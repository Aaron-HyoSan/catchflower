package com.catchflower.app.data

import com.catchflower.app.core.AccountDeletionRules
import com.catchflower.app.core.AccountDeletionRules.Step
import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 회원 탈퇴의 **서버 쪽**. 기기 쪽은 [LocalDataWiper]가 한다.
 *
 * 2026-08-16 출시 준비. 순서와 판정은 [AccountDeletionRules]가 원본이다.
 *
 * ## 🔴 마이그레이션이 필요 없다 — 이미 있는 정책만으로 지운다
 *
 * | 지우는 것 | 쓰는 정책 | 어디 |
 * |---|---|---|
 * | `discoveries` | `discoveries_delete_self` | 0001 |
 * | `likes` | `likes_delete_self` | 0007 |
 * | `friendships` | `friendships_delete_involved` | 0001 |
 * | `blocks` | `blocks_delete_self` | 0001 |
 * | `users` (soft) | `users_update_self` | 0001 |
 *
 * 공유계약 6절: 스키마는 iOS 세션이 만든다. **여기서 SQL을 만들지 않았다.**
 *
 * ## 🔴 지우지 못하는 것이 둘 있다 — 그래서 문서에 그렇게 적었다
 *
 * 1. **남의 사진에 남긴 내 댓글.** `comments`에는 delete 정책이 없고 soft delete만
 *    있는데(0008 · `comments_only_soft_delete` 트리거), 그러면 대화가 구멍 난다.
 *    게다가 `comments_read`가 `can_see_discovery`를 요구해서 **내 댓글 전체를 셀 수도
 *    없다**(차단당한 뒤·비공개로 바뀐 뒤에는 안 보인다). 그래서 남기고,
 *    작성자 이름만 사라진다 — `public_profiles`가 `deleted_at is null`로 걸러서
 *    화면 16은 [com.catchflower.app.ui.place.RecordRules.authorName] 규칙에 따라
 *    `탈퇴한 사용자예요`를 그린다. **이 동작은 이미 있었다**(새로 만든 것이 아니다).
 * 2. **`reports`(신고 기록).** insert·select 정책만 있다. 신고 대응을 위해 남기는 것이
 *    맞고, 개인정보 처리방침 6항 나에 기간(최대 1년)을 적었다.
 *
 * ⚠️ **auth 레코드(로그인 계정 자체)도 앱에서 못 지운다.** 지우려면 `service_role`
 *    키가 필요하고, 그 키를 앱에 넣으면 앱을 뜯은 사람이 **모든 사용자를 지울 수
 *    있다.** 오너가 서버에서 정리한다(`프로젝트 맥락/제안/0011`).
 */
interface AccountDeletionSource {

    /**
     * 서버에서 지운다. **성공은 "5단계가 전부 끝났다"는 뜻이다.**
     *
     * 부분 성공을 [AccountDeletionResult.Deleted]로 만들지 않는다 — 그러면 기기를
     * 지워도 된다는 판정이 되고([AccountDeletionRules.mayWipeDevice]), 서버에 남은
     * 데이터를 **다시 시도할 방법이 사라진다.**
     */
    suspend fun deleteServerData(): AccountDeletionResult
}

/** [AccountDeletionSource]의 결과. */
sealed interface AccountDeletionResult {

    /** 5단계 전부 끝났다. 이제 기기를 지워도 된다. */
    data object Deleted : AccountDeletionResult

    /**
     * 서버에 지울 것이 없다. **키 없는 빌드이거나 아직 계정을 못 만든 상태다.**
     *
     * ⚠️ 실패가 아니다 — 기기 데이터는 지워야 한다. 실패로 처리하면 오프라인 첫
     *    실행 뒤에 **탈퇴할 수 없는 앱**이 된다.
     */
    data object NothingOnServer : AccountDeletionResult

    /**
     * @param step 어디서 끊겼는지. 화면에는 쓰지 않는다(사용자가 할 일이 같다) —
     *   로그와 재시도 판정에만 쓴다.
     */
    data class Failed(
        val step: Step,
        val code: Int,
        val pgCode: String = "",
    ) : AccountDeletionResult
}

class AccountDeletionService(
    private val auth: TokenSource,
    private val myUserId: suspend () -> String?,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ 주입한다 — `android.util.Log`는 JVM에서 던진다. */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : AccountDeletionSource {

    interface Transport {
        suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            prefer: String,
        ): Response

        data class Response(val code: Int, val body: String)
    }

    override suspend fun deleteServerData(): AccountDeletionResult {
        if (baseUrl.isEmpty() || anonKey.isEmpty()) {
            log("탈퇴: 키 없는 빌드다 — 서버에 계정이 없으므로 기기만 지운다")
            return AccountDeletionResult.NothingOnServer
        }
        val me = myUserId()
        if (me.isNullOrEmpty()) {
            log("탈퇴: 계정 uuid가 없다 — 서버에 만들어진 적이 없으므로 기기만 지운다")
            return AccountDeletionResult.NothingOnServer
        }

        // 🔴 **순서를 여기서 정하지 않는다.** `Step.entries`가 곧 순서이고 그 이유는
        //    `AccountDeletionRules.Step` 주석에 있다. 화면·서비스가 각자 순서를 갖고
        //    있으면 한쪽만 고쳐지고, 어긋난 결과는 화면에 안 보인다.
        for (step in Step.entries) {
            val failure = runStep(step, me)
            if (failure != null) {
                log("탈퇴 ${step.name} 실패 · HTTP ${failure.code} ${failure.pgCode}".trim())
                return failure
            }
        }
        return AccountDeletionResult.Deleted
    }

    /** @return 실패면 [AccountDeletionResult.Failed], 성공이면 null. */
    private suspend fun runStep(step: Step, me: String): AccountDeletionResult.Failed? =
        when (step) {
            // 🔴 필터를 반드시 붙인다. PostgREST의 DELETE는 필터가 없으면 **보이는 행
            //    전부**를 지운다(`ReactionService.unlike` 주석과 같은 규칙). RLS가
            //    막아 주긴 하지만, 그 방어가 정책 하나에만 걸려 있게 두지 않는다.
            Step.DISCOVERIES -> delete(step, "discoveries?user_id=eq.$me")
            Step.LIKES -> delete(step, "likes?user_id=eq.$me")
            // `or=(a.eq.x,b.eq.y)` — 요청한 쪽·받은 쪽 어디에 있든 지운다.
            Step.FRIENDSHIPS ->
                delete(step, "friendships?or=(requester_id.eq.$me,addressee_id.eq.$me)")
            Step.BLOCKS -> delete(step, "blocks?blocker_id=eq.$me")
            Step.PROFILE -> softDeleteProfile(me)
        }

    /**
     * `DELETE /rest/v1/<path>`.
     *
     * ⚠️ **0행 삭제를 실패로 보지 않는다.** [ProfileService]의 PATCH와 반대다 —
     *    지울 것이 없는 것(발견 기록 0개·좋아요 0개)이 **정상**이고, 그걸 실패로 세면
     *    새 계정은 탈퇴가 영구히 실패한다. 실패 판정은 HTTP 코드로만 한다.
     */
    private suspend fun delete(step: Step, path: String): AccountDeletionResult.Failed? {
        val res = sendWithRefresh("$baseUrl/rest/v1/$path", "DELETE", null, prefer = "")
        if (res.code in 200..299) return null
        return AccountDeletionResult.Failed(step, res.code, pgCode(res.body))
    }

    /**
     * `PATCH /rest/v1/users?id=eq.me` — `deleted_at`을 찍고 **본인 정보를 비운다.**
     *
     * 🔴 `deleted_at`만 찍으면 닉네임·활동 지역이 **행에 그대로 남는다.** 뷰가 가려
     *    주니 화면에는 안 보이지만, 개인정보 처리방침 6항 가는 "닉네임과 활동 지역이
     *    지워진다"고 약속했다 — 보이지 않는 것과 지워진 것은 다르다.
     *
     * 🔴 `Prefer: return=representation`이 필요하다. PostgREST의 PATCH는 **아무 행도
     *    안 맞아도 204**라서, RLS가 막았거나 uuid가 틀린 경우가 성공으로 온다
     *    (`ProfileService.updateNickname` 주석의 그 사고 그대로다). 여기서 그걸 놓치면
     *    **기기를 지워도 된다는 판정**이 나오고, 사용자는 탈퇴됐다고 믿는다.
     *
     * ⚠️ soft delete가 `users_read_self`를 깨지 않는다 — 그 정책은 `auth.uid() = id`
     *    뿐이고 `deleted_at`을 안 본다. 0008의 댓글 사고(`update`의 새 행도 select
     *    정책을 탄다 → 42501)는 **여기서는 일어나지 않는다.** 확인하고 적었다.
     */
    private suspend fun softDeleteProfile(me: String): AccountDeletionResult.Failed? {
        val body = JSONObject()
            // 🔴 `"now"`다 — `"now()"`가 아니다. Postgres는 `'now'`를 timestamptz의
            //    특수 입력으로 받지만 `'now()'`는 **입력 문법 오류**다(함수 호출은
            //    JSON 값으로 보낼 수 없다). 그러면 400이 오고 탈퇴가 영구히 실패한다.
            // ⚠️ 기기 시계로 만든 ISO 문자열을 보내지 않는다 — 시계가 틀린 기기가
            //    `deleted_at`을 미래로 찍으면 오너의 정리 작업(제안 0011)이 그 계정을
            //    영원히 건너뛴다. 시각은 **서버 시계**가 정한다.
            .put("deleted_at", "now")
            // 🔴 `nickname`은 `not null`이다(0001) — NULL을 보내면 23502로 막히고
            //    탈퇴가 끝까지 실패한다. 그래서 **빈 문자열**로 비운다.
            //    유니크 제약이 없어서 탈퇴 계정이 여러 개여도 충돌하지 않는다(확인했다).
            .put("nickname", "")
            .put("region_name", JSONObject.NULL)
            .put("dong_code", JSONObject.NULL)
            .put("gu_code", JSONObject.NULL)
            .toString()
        val url = "$baseUrl/rest/v1/users?id=eq.$me&select=id"
        val res = sendWithRefresh(url, "PATCH", body, PREFER_REPRESENTATION)
        if (res.code !in 200..299) {
            return AccountDeletionResult.Failed(Step.PROFILE, res.code, pgCode(res.body))
        }
        val rows = runCatching { JSONArray(res.body).length() }.getOrElse { -1 }
        if (rows <= 0) {
            // 여기가 위 주석의 그 자리다. **성공 코드인데 바뀐 행이 없다.**
            return AccountDeletionResult.Failed(Step.PROFILE, res.code, ProfileService.NO_ROW)
        }
        return null
    }

    /** 401이면 토큰을 갱신해 **한 번만** 다시 보낸다([ProfileService]와 같은 규칙). */
    private suspend fun sendWithRefresh(
        url: String,
        method: String,
        body: String?,
        prefer: String,
    ): Transport.Response {
        val token = auth.accessToken() ?: return Transport.Response(401, "")
        val first = send(url, method, body, prefer, token)
        if (first.code != 401) return first
        val fresh = auth.refresh() ?: return first
        return send(url, method, body, prefer, fresh)
    }

    private suspend fun send(
        url: String,
        method: String,
        body: String?,
        prefer: String,
        token: String,
    ): Transport.Response = runCatching {
        transport.send(url, method, anonKey, token, body, prefer)
    }.getOrElse { e ->
        if (e is CancellationException) throw e
        // 코드 0 = 못 보냈다(네트워크). 실패로 센다 — 다시 시도할 수 있다.
        Transport.Response(0, "")
    }

    private fun pgCode(body: String): String =
        runCatching { JSONObject(body).optString("code") }.getOrDefault("")

    internal object HttpTransport : Transport {
        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            prefer: String,
        ): Transport.Response = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                // 🔴 PATCH는 **`HttpURLConnection`이 거부할 수 있다.** 그때 POST로
                //    되돌아가면 PostgREST가 insert로 처리해 403 `42501`이 오고, 그 응답만
                //    보면 RLS 문제로 읽힌다 — 이미 그 오진에 시간을 썼다. 판단은 한 곳
                //    ([RegionUpdateService.HttpTransport.forcePatchMethod])에만 둔다.
                if (method == "PATCH") {
                    RegionUpdateService.HttpTransport.forcePatchMethod(this)
                } else {
                    requestMethod = method
                }
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
        const val PREFER_REPRESENTATION = ProfileService.PREFER_REPRESENTATION
        private const val TIMEOUT_MS = 15_000
    }
}
