package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 화면 20 `프로필 수정`이 쓰는 서버 호출.
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸면서 생겼다.
 *
 * ## 🔴 마이그레이션이 필요 없다
 *
 * `users_update_self for update using (id = auth.uid())`가 0001에 이미 있다 —
 * **새 SQL을 만들지 않았다**(공유계약 6절: DB 스키마는 한쪽만 만든다).
 *
 * ## 🔴 닉네임을 여기서만 바꾼다
 *
 * 기기에 따로 저장하지 않는다. 화면 20·17·18의 이름은 전부 `users.nickname`에서
 * 오므로([RankingSource.myRegion]·랭킹 행의 `is_me`), 로컬에 복사해 두면
 * **다른 기기에서 바꾼 뒤 두 화면이 다른 이름을 말한다.**
 */
interface ProfileSource {

    /**
     * 닉네임을 바꾼다. **성공은 "서버 행이 실제로 바뀌었다"는 뜻이다** —
     * 이유는 [ProfileService.updateNickname] 주석에 있다.
     */
    suspend fun updateNickname(nickname: String): ProfileResult
}

/** [ProfileSource]의 결과. [FriendResult]와 같은 모양이다 — 실패를 뭉개지 않는다. */
sealed interface ProfileResult {
    /** @param nickname **서버가 되돌려 준 값**이다. 우리가 보낸 값이 아니다. */
    data class Saved(val nickname: String) : ProfileResult

    /** @param pgCode PostgREST가 준 `code`. 빈 문자열이면 못 읽었다. */
    data class Failed(val code: Int, val pgCode: String = "") : ProfileResult

    /**
     * 화면이 막았어야 하는 값이 여기까지 왔다(빈 닉네임·10자 초과).
     *
     * ⚠️ **[Failed]로 만들지 않는다.** 네트워크 실패로 보이면 `잠시 후 다시 시도해
     *    주세요`가 뜨는데, 다시 시도해도 결과는 같다 — 사용자가 할 일은 글자를 고치는 것이다.
     */
    data object Invalid : ProfileResult

    /** 키 없는 빌드. 화면은 `프로필 수정` 버튼을 그리지 않는다. */
    data object NotConfigured : ProfileResult
}

class ProfileService(
    private val auth: TokenSource,
    /**
     * 내 uuid. **URL 필터에 넣어야 한다** — PostgREST의 PATCH는 필터가 없으면
     * 테이블 전체를 대상으로 삼고, 그건 RLS가 막아 주긴 하지만 **0행 갱신**으로 끝난다
     * (아래 `return=representation` 주석).
     */
    private val myUserId: suspend () -> String?,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ 주입한다 — `android.util.Log`는 JVM에서 던진다. */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : ProfileSource {

    interface Transport {
        suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            /** `return=representation` 같은 PostgREST 헤더. 비면 안 붙인다. */
            prefer: String,
        ): Response

        data class Response(val code: Int, val body: String)
    }

    /**
     * `PATCH /rest/v1/users?id=eq.{내 uuid}`.
     *
     * 🔴 **`Prefer: return=representation`이 없으면 실패가 성공으로 온다.**
     *    PostgREST의 PATCH는 기본이 `204 No Content`이고, **아무 행도 안 맞아도 204**다
     *    (0008에서 `남의 댓글 삭제도 204 + 0행`으로 겪은 얼굴 그대로다).
     *    RLS `users_update_self`가 막은 경우·`id`가 틀린 경우가 전부 성공으로 보이고,
     *    화면은 `프로필을 저장했어요`를 띄운 뒤 **되돌아온 옛 닉네임**을 그린다 —
     *    사용자에게는 "저장했는데 안 바뀐다"로 보인다.
     *    → 갱신된 행을 **되받아서** 0행이면 실패([NO_ROW])로 본다.
     *
     * 🔴 **되받은 닉네임을 그대로 올린다.** 우리가 보낸 값을 성공값으로 쓰면 서버가
     *    트리거로 다듬는 날(공백 정리·금칙어 치환) **화면과 서버가 갈린다.**
     *
     * ⚠️ 판정을 [NicknameRules]로 한 번 더 한다. 화면이 이미 막지만, 그 판단이 화면에만
     *    있으면 다음 사람이 다른 화면에서 이 함수를 부를 때 빈 닉네임이 저장된다.
     */
    override suspend fun updateNickname(nickname: String): ProfileResult {
        val verdict = NicknameRules.validate(nickname)
        if (verdict !is NicknameRules.Verdict.Ok) {
            log("화면이 막지 못한 닉네임이 서버 호출까지 왔다 — $verdict")
            return ProfileResult.Invalid
        }
        if (!configured) return ProfileResult.NotConfigured
        val me = myUserId() ?: return ProfileResult.Failed(401)
        val token = auth.accessToken() ?: return ProfileResult.Failed(401)
        val url = "$baseUrl/rest/v1/$TBL_USERS?$COL_ID=eq.$me&select=$COL_NICKNAME"
        val body = JSONObject().put(COL_NICKNAME, verdict.value).toString()

        val first = send(url, body, token)
        val res = if (first.code == 401) {
            val fresh = auth.refresh() ?: return ProfileResult.Failed(401)
            send(url, body, fresh)
        } else {
            first
        }
        if (res.code !in 200..299) {
            val pgCode = runCatching { JSONObject(res.body).optString("code") }.getOrDefault("")
            // `42501` = RLS 거절(남의 행을 고치려 했다)
            // `PGRST116` = 필터가 여러 행을 가리켰다
            log("닉네임 저장 실패 · HTTP ${res.code}${if (pgCode.isEmpty()) "" else " · $pgCode"}")
            return ProfileResult.Failed(res.code, pgCode)
        }
        val saved = runCatching {
            val arr = JSONArray(res.body)
            if (arr.length() == 0) null else arr.getJSONObject(0).stringOrNull(COL_NICKNAME)
        }.getOrElse { e ->
            log("닉네임 응답을 읽을 수 없다 · ${e.javaClass.simpleName}")
            return ProfileResult.Failed(res.code, PARSE_FAILED)
        } ?: run {
            // ⚠️ **[RegionUpdateService.changedAnyRow]와 판단이 반대다.** 거기서는 읽을
            //    수 없는 응답을 "저장됐다"로 본다 — 실패로 단정하면 사용자가 다시
            //    저장하고, 그건 **6개월에 한 번뿐인 지역 변경**을 소모한다.
            //    닉네임에는 그 대가가 없다. 반대로 거짓 성공은 `프로필을 저장했어요`를
            //    띄운 뒤 **옛 이름이 그대로 있는** 화면을 만든다 → 여기서는 실패로 둔다.
            // 🔴 여기가 위 주석의 그 자리다. **성공 코드인데 바뀐 행이 없다.**
            log("닉네임 저장이 0행을 갱신했다 · HTTP ${res.code} — RLS나 id를 확인해야 한다")
            return ProfileResult.Failed(res.code, NO_ROW)
        }
        return ProfileResult.Saved(saved)
    }

    private suspend fun send(url: String, body: String, token: String): Transport.Response =
        runCatching {
            transport.send(url, "PATCH", anonKey, token, body, PREFER_REPRESENTATION)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            Transport.Response(0, "")
        }

    private val configured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    internal object HttpTransport : Transport {
        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            prefer: String,
        ): Transport.Response = withContext(Dispatchers.IO) {
            // 아래 `forcePatchMethod`가 메서드를 고정하므로, 다른 메서드를 받으면
            // **말과 행동이 달라진다.** 조용히 PATCH로 바꾸지 않고 여기서 멈춘다.
            require(method == "PATCH") { "이 전송로는 PATCH만 보낸다 — 받은 것은 $method" }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                // 🔴 **`X-HTTP-Method-Override`를 쓰지 않는다 — PostgREST가 그 헤더를
                //    무시한다(실측 · 진행.md (33)).** POST로 나가면 서버는 insert로
                //    처리하고, 이미 있는 내 행이라 `users_insert_self`의 WITH CHECK에 걸려
                //    **403 `42501`**이 온다 — 그 응답만 보면 **RLS 문제로 읽힌다.**
                //
                // ⚠️ 그 실측은 [RegionUpdateService]가 이미 했다(같은 테이블·같은 메서드).
                //    **여기서 다시 판단하지 않고 그 함수를 부른다** — 두 곳에 각자 쓰면
                //    한쪽만 고쳐지고, 안 고쳐진 쪽은 위의 403을 **RLS 사고로 보고**한다.
                //    막히면 던진다(POST로 되돌아가지 않는다).
                RegionUpdateService.HttpTransport.forcePatchMethod(this)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                if (prefer.isNotEmpty()) setRequestProperty("Prefer", prefer)
                if (body != null) doOutput = true
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Transport.Response(
                    code = code,
                    body = stream?.bufferedReader()?.use { it.readText() } ?: "",
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        const val PARSE_FAILED = "PARSE"

        /** 성공 코드인데 갱신된 행이 없다. **HTTP 코드로는 구분할 수 없다.** */
        const val NO_ROW = "NOROW"

        const val PREFER_REPRESENTATION = "return=representation"

        // ── 0001의 이름들. 혼자 바꾸지 않는다(공유계약 6절). ──
        const val TBL_USERS = "users"
        const val COL_ID = "id"
        const val COL_NICKNAME = "nickname"

        private const val TIMEOUT_MS = 15_000
    }
}
