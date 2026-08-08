package com.catchflower.app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * 어떤 기록이 서버에 올라갔는가. **기록 파일과 따로 둔다.**
 *
 * ⚠️ **`discoveries.json`에 `uploaded` 필드를 넣지 않는다.** 그 파일은 계약 1-3의
 *    컬럼명과 1:1이고 **그대로 서버로 올라가는 형태**다. 계약에 없는 키가 섞이면
 *    PostgREST가 요청 전체를 거부한다 — `_local_photo_path` 하나로 400을 맞았다
 *    ([DiscoveryStore.toWireJson] 주석). 같은 함정을 또 만들지 않는다.
 *
 * **왜 "올라간 id 목록"이고 "못 올린 id 목록"이 아닌가.** 기록은 이미
 * `discoveries.json`에 다 있다. 못 올린 것 = 전체 − 올라간 것으로 유도된다.
 * 반대로 두면 **두 파일이 어긋났을 때 기록이 조용히 사라진다** —
 * 큐에서 빠진 기록은 다시는 올라가지 않고, 화면에는 정상으로 보인다.
 *
 * **거절된 것도 따로 센다.** 서버가 규칙으로 거절한 기록(B-5 위반 등)을
 * "안 올라감"에 두면 **매 실행마다 다시 보내고 매번 400을 맞는다.**
 */
class UploadState(
    private val file: File,
    /**
     * ⚠️ **`android.util.Log`를 직접 부르면 JVM 테스트에서 던진다**(스텁이다).
     *    깨진 파일 복구가 이 로그를 지나가므로, 직접 부르면 **"깨졌을 때 전부 미전송으로
     *    되는가"를 검증할 수 없다** — 그게 이 클래스에서 가장 조용히 틀리는 경로다.
     */
    private val log: (String, Exception) -> Unit = { m, e ->
        android.util.Log.w("CatchFlower", m, e)
    },
) {

    private val lock = Mutex()

    /** 올라간 기록 id. */
    suspend fun uploaded(): Set<String> = withContext(Dispatchers.IO) {
        lock.withLock { read().first }
    }

    /**
     * 아직 안 올라갔고 거절되지도 않은 id를 준다.
     *
     * ⚠️ **인자가 전체 목록이다.** 여기서 파일을 다시 읽지 않는다 —
     *    호출부(메모리)와 파일이 어긋난 순간 다른 답이 나온다.
     */
    suspend fun pending(allIds: List<String>): List<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            val (up, rejected) = read()
            allIds.filterNot { it in up || it in rejected }
        }
    }

    suspend fun markUploaded(id: String) = mark(id, uploaded = true)

    /**
     * 재시도해도 소용없는 기록으로 표시한다.
     *
     * ⚠️ **`Failed`에는 부르지 않는다.** 네트워크 실패를 여기 넣으면
     *    **지하철에서 찍은 꽃이 영구히 서버에 안 올라간다** — 도감에는 있으니
     *    아무도 모르고, 랭킹에서만 조용히 빠진다.
     */
    suspend fun markRejected(id: String) = mark(id, uploaded = false)

    /**
     * "올라갔다"를 전부 취소한다. **사용자 id가 바뀔 때 부른다.**
     *
     * ⚠️ `rejected`는 지우지 않는다. 거절 이유(B-5 위반 등)는 계정과 무관하고,
     *    지우면 매 이관마다 같은 400을 다시 맞는다.
     */
    suspend fun clearUploaded() = withContext(Dispatchers.IO) {
        lock.withLock {
            val (_, rejected) = read()
            write(emptySet(), rejected)
        }
    }

    private suspend fun mark(id: String, uploaded: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            val (up, rejected) = read()
            val newUp = if (uploaded) up + id else up
            val newRejected = if (uploaded) rejected - id else rejected + id
            write(newUp, newRejected)
        }
    }

    private fun read(): Pair<Set<String>, Set<String>> {
        if (!file.exists()) return emptySet<String>() to emptySet()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptySet<String>() to emptySet()
        return try {
            val o = org.json.JSONObject(text)
            o.optJSONArray(K_UPLOADED).toSet() to o.optJSONArray(K_REJECTED).toSet()
        } catch (e: JSONException) {
            // ⚠️ **깨졌으면 빈 집합으로 시작한다.** 최악은 이미 올라간 걸 다시 올리는 것이고,
            //    upsert라 그건 무해하다([DiscoveryUploader.upload]).
            //    반대로 "다 올라갔다"고 가정하면 **기록이 영구히 안 올라간다.**
            log("업로드 상태를 읽을 수 없다 — 전부 미전송으로 본다", e)
            emptySet<String>() to emptySet()
        }
    }

    private fun write(uploaded: Set<String>, rejected: Set<String>) {
        val o = org.json.JSONObject()
            .put(K_UPLOADED, JSONArray(uploaded.toList()))
            .put(K_REJECTED, JSONArray(rejected.toList()))
        file.parentFile?.mkdirs()
        // 원자적으로 쓴다 — 반쪽 JSON이 남으면 위 `read`가 전부 미전송으로 되돌린다.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(o.toString())
            tmp.delete()
        }
    }

    private fun JSONArray?.toSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNullTo(HashSet()) { optString(it).ifEmpty { null } }
    }

    companion object {
        private const val FILE_NAME = "upload_state.json"
        private const val K_UPLOADED = "uploaded"
        private const val K_REJECTED = "rejected"

        fun default(context: Context): UploadState =
            UploadState(File(context.filesDir, FILE_NAME))
    }
}
