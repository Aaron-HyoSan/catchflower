package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import com.catchflower.app.data.model.Discovery
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * [DiscoveryRepository]가 보는 면. **한 건 올린다**가 전부다.
 *
 * ⚠️ 저장소 테스트가 이걸 갈아 끼운다 — 없으면 "일시 실패에 멈추는가"를
 *    HTTP 응답을 흉내내서만 검증할 수 있고, 그건 저장소가 아니라 분류를 재는 것이다.
 *
 * ⚠️ **최상위에 둔다** — [TokenSource]와 같은 이유(상위 타입 순환)다.
 */
interface UploadSink {
    suspend fun upload(discovery: Discovery): DiscoveryUploader.Result
}

/**
 * 발견 기록을 Supabase에 올린다. 계약 1-3의 `discoveries` 테이블이 목적지다.
 *
 * **왜 별 층인가.** [DiscoveryStore]는 파일만, 여기는 네트워크만 본다.
 * 섞으면 "저장은 됐는데 업로드가 안 된 상태"를 표현할 곳이 없어진다 —
 * 그 상태가 **정상 상태**다(비행기 모드·지하철).
 *
 * ⚠️ **업로드 실패가 등록을 막지 않는다.** 기록은 이미 기기에 있고 도감 칸도 채워졌다.
 *    여기서 예외를 던져 올리면 **꽃을 찍었는데 등록이 취소된다** — 사용자 입장에서는
 *    네트워크가 나쁜 게 아니라 앱이 고장 난 것이다. 그래서 결과를 [Result]로 돌려주고
 *    호출부가 "나중에 다시"를 판단한다.
 *
 * ⚠️ **랭킹을 여기서 계산하지 않는다.** 올리기만 하고, 순위는 서버 함수
 *    (`region_ranking`·`friend_ranking`)가 센다 — 계약 3절·`진행.md` (23) 5절.
 *    클라이언트가 세면 숫자를 조작할 수 있다.
 */
class DiscoveryUploader(
    /**
     * ⚠️ **[AuthService] 자체가 아니라 [TokenSource]를 받는다.**
     *    `AuthService`는 생성자가 `Context`를 요구해서 JVM 테스트에서 만들 수 없다.
     *    401 갱신·RLS 거절은 **기기에서 재현할 방법이 사실상 없는** 경우라서
     *    이 층은 JVM으로 고정해야 한다.
     */
    private val auth: TokenSource,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /**
     * ⚠️ **`android.util.Log`를 직접 부르면 JVM 테스트에서 던진다**(스텁이다).
     *    거절 경로는 **전부 로그를 지나가므로**, 직접 부르면 이 클래스에서
     *    검증하고 싶은 분류가 하나도 테스트되지 않는다.
     */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : UploadSink {

    /** HTTP를 갈아 끼울 수 있게 둔다 — JVM 테스트가 네트워크를 부르면 안 된다. */
    interface Transport {
        suspend fun post(
            url: String,
            apiKey: String,
            bearer: String,
            prefer: String,
            body: String,
        ): Pair<Int, String>
    }

    /**
     * 업로드 한 건의 결과.
     *
     * **[Rejected]와 [Failed]를 나누는 이유가 핵심이다.** 둘 다 "안 올라갔다"인데
     * 대응이 반대다 — [Failed]는 **다시 시도해야** 하고, [Rejected]는
     * **다시 시도하면 안 된다.** 하나로 뭉치면 서버가 규칙으로 거절한 기록을
     * 앱이 영원히 재시도한다(매 실행마다 400을 맞으면서).
     */
    sealed interface Result {
        /** 올라갔다. 이미 있던 것을 덮은 경우도 포함한다(upsert). */
        data object Uploaded : Result

        /**
         * 서버가 **규칙으로** 거절했다. 재시도해도 결과가 같다.
         *
         * 예: B-5 하루 1회 위반(`23505`) · 계약에 없는 컬럼(`PGRST204`) ·
         * 제약 위반(`23502`·`23514`). **코드에 버그가 있다는 신호이므로 로그에 남긴다.**
         */
        data class Rejected(val code: Int, val pgCode: String) : Result

        /** 일시적 실패(네트워크·5xx·401 갱신 실패). **다시 시도한다.** */
        data class Failed(val code: Int) : Result
    }

    /**
     * 한 건 올린다.
     *
     * **`Prefer: resolution=merge-duplicates`(upsert)를 쓴다.** 이유:
     * 🔴 같은 `id`를 그냥 POST하면 **409 `23505 discoveries_pkey`**가 난다(실측).
     *    업로드는 재시도되는 경로다 — 응답을 못 받았지만 서버에는 들어간 경우가
     *    정상적으로 생긴다(지하철에서 터널 진입). 그때 재시도가 409면 그 기록은
     *    **영원히 "실패"로 남고 앱은 매번 다시 보낸다.** upsert면 200이라 멱등하다.
     *
     * ⚠️ **`id`를 클라이언트가 만든다는 전제가 여기 걸려 있다.** 서버가
     *    `gen_random_uuid()`로 만들게 두면 재시도마다 새 행이 생겨 **중복 기록**이 된다.
     *    지금은 `CaptureViewModel`이 `UUID.randomUUID()`로 만든다.
     */
    override suspend fun upload(discovery: Discovery): Result {
        // ⚠️ **전역([AppSecrets])이 아니라 주입된 값을 본다.** 전역을 보면 주입이
        //    무의미해지고, **테스트 결과가 그 맥에 `local.properties`가 있느냐로 갈린다.**
        if (baseUrl.isEmpty() || anonKey.isEmpty()) return Result.Failed(0)
        val body = DiscoveryStore.toWireJson(discovery).toString()
        val token = auth.accessToken() ?: return Result.Failed(401)

        val first = send(body, token)
        // 🔴 **401을 한 번은 갱신하고 다시 시도한다.** 선제 갱신([AuthService.accessToken])만
        //    믿으면, 기기 시계가 바뀌었거나 서버가 세션을 폐기한 경우에 **그 실행 내내
        //    모든 업로드가 401**이 된다. 화면에는 증상이 없다.
        if (first.first == 401) {
            val fresh = auth.refresh() ?: return Result.Failed(401)
            return classify(send(body, fresh))
        }
        return classify(first)
    }

    private suspend fun send(body: String, token: String): Pair<Int, String> = runCatching {
        transport.post(
            url = "$baseUrl/rest/v1/discoveries",
            apiKey = anonKey,
            bearer = token,
            prefer = "resolution=merge-duplicates",
            body = body,
        )
    }.getOrElse { e ->
        if (e is CancellationException) throw e
        // 네트워크 예외는 **일시 실패다.** 여기서 던지면 등록 흐름이 깨진다.
        0 to ""
    }

    private fun classify(response: Pair<Int, String>): Result {
        val (code, body) = response
        if (code in 200..299) return Result.Uploaded

        val pgCode = runCatching { JSONObject(body).optString("code") }.getOrDefault("")

        // ⚠️ **상태 코드만으로 가르면 안 된다.** 409는 두 가지다:
        //    `23505 discoveries_pkey`(이미 올라간 것 = 사실상 성공) vs
        //    `23505 discoveries_same_flower_place_per_day`(B-5 위반 = 거절).
        //    upsert를 쓰므로 pkey 충돌은 안 나야 정상이지만, 나면 **그 기록은 이미
        //    서버에 있다** — 실패로 세면 영원히 재시도한다.
        if (pgCode == PG_UNIQUE_VIOLATION && body.contains(PKEY_CONSTRAINT)) {
            return Result.Uploaded
        }

        return when {
            // 4xx는 요청이 틀린 것이다. 몇 번 보내도 같다.
            // 단 **401·408·429는 제외** — 인증 만료·타임아웃·요청 과다는 시간이 풀어 준다.
            code == 401 || code == 408 || code == 429 -> Result.Failed(code)
            // 🔴 **403 `42501`(RLS 위반)을 영구 거절로 세면 안 된다.** 실측으로
            //    이 코드가 나오는 경우는 **`user_id`가 로그인 계정과 다를 때**다 —
            //    즉 로그인이 아직 안 됐거나 이관 전에 올린 것이다. 둘 다 **다음 실행에
            //    풀린다.** 영구 거절로 표시하면 그 기록은 **다시는 올라가지 않고**,
            //    도감에는 그대로 보여서 아무도 모른다. 랭킹에서만 조용히 빠진다.
            code == 403 && pgCode == PG_RLS_VIOLATION -> {
                log("RLS가 거부했다 — user_id가 로그인 계정과 다르다. 다음 실행에 재시도한다")
                Result.Failed(code)
            }
            code in 400..499 -> {
                log("서버가 기록을 거절했다 · HTTP $code${if (pgCode.isEmpty()) "" else " · $pgCode"}")
                Result.Rejected(code, pgCode)
            }
            // 5xx·0(네트워크)은 일시적이다.
            else -> Result.Failed(code)
        }
    }

    /** 실물 HTTP. 계측 테스트가 실호출에 쓸 수 있게 `internal`이다. */
    internal object HttpTransport : Transport {
        override suspend fun post(
            url: String,
            apiKey: String,
            bearer: String,
            prefer: String,
            body: String,
        ): Pair<Int, String> = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                // ⚠️ **anon 키가 아니라 사용자 토큰이다.** anon 키를 여기 넣으면
                //    `auth.uid()`가 null이라 RLS(`user_id = auth.uid()`)가
                //    **42501로 전부 거부한다**(실측).
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", prefer)
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                // ⚠️ 2xx가 아니면 `inputStream`은 던진다 — 그러면 모든 상태 코드가
                //    "네트워크 실패"로 뭉개져서 `Rejected`와 `Failed`를 못 가른다.
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                conn.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val PG_UNIQUE_VIOLATION = "23505"
        const val PG_RLS_VIOLATION = "42501"
        const val PKEY_CONSTRAINT = "discoveries_pkey"
    }
}
