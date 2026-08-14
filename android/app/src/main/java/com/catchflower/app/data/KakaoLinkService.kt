package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 익명 계정에 **카카오를 연결**한다(화면 01 대체 · 로그인 시트).
 *
 * ## 🔴 브라우저를 그냥 열면 안 된다
 *
 * [SupabaseAuthUrls.linkIdentity]는 **Authorization 헤더가 필요한 엔드포인트**다.
 * 그 주소를 `ACTION_VIEW`로 브라우저에 던지면 헤더가 없어서 `401`이 되고, 사용자는
 * 브라우저에 뜬 영문 오류를 본다. 그래서 순서가 이렇다:
 *
 * ```
 * ① 앱이 GET (Authorization: Bearer …, skip_http_redirect=true)  ← 이 클래스
 * ② 응답 JSON { "url": "https://kauth.kakao.com/oauth/authorize?…" }
 * ③ 그 url을 브라우저로 연다                                      ← 호출부
 * ④ 카카오 로그인 → Supabase /callback → catchflower://auth-callback
 * ⑤ 앱이 인텐트를 받아 [callbackSucceeded]로 성공/실패를 가른다
 * ```
 *
 * ⚠️ **`skip_http_redirect=true`가 핵심이다.** 없으면 서버가 302를 주는데,
 *    `HttpURLConnection`이 그걸 따라가서 **카카오 로그인 페이지 HTML을 받아온다** —
 *    코드 200이라 성공으로 읽히고, 우리는 열 주소를 못 얻는다.
 *
 * ## 🔴 [SupabaseAuthUrls.oauthAuthorize]를 쓰지 않는 이유
 *
 * 그쪽은 **새 세션을 만들고 익명 uuid를 버린다** — 그 순간 지금까지 모은 도감이
 * 다른 계정의 것이 되고, RLS 때문에 **보이지도 지우지도 못한다.** 두 엔드포인트는
 * 이름이 비슷하고 둘 다 "로그인이 된다"는 점에서 증상이 같아서, 섞으면
 * **성공 화면과 함께 도감이 사라진다.** 여기서는 linking만 쓴다.
 *
 * ## ⚠️ 아직 완주하지 못한 것 (2026-08-14)
 *
 * 오너가 Supabase 콘솔 → Authentication → URL Configuration → **Redirect URLs**에
 * `catchflower://auth-callback`을 넣어야 ④가 앱으로 돌아온다. 없으면 서버가
 * **오류 대신 Site URL로 조용히 보낸다** — 로그에도 안 남는다. 밖에서는 잴 수 없다
 * (GoTrue `state`가 불투명한 uuid라 대조군과 답이 같았다 · [KakaoLogin] 주석).
 * **실제 콜백을 한 번 완주해야 확인된다. "확인했다"로 적지 않는다.**
 */
class KakaoLinkService(
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    private val log: (String) -> Unit = {},
) {

    /** GET 전용 전송로. **[AuthService.Transport]를 고치지 않는다** — 가짜 전부를 손봐야 한다. */
    interface Transport {
        suspend fun get(url: String, apiKey: String, bearer: String): Response
        data class Response(val code: Int, val body: String)
    }

    sealed interface Start {
        /** 이 주소를 브라우저로 연다. */
        data class OpenBrowser(val url: String) : Start

        /**
         * 시작하지 못했다.
         *
         * @param reason [KakaoLogin.linkFailureReason]이 가른 원인. 문구를 여기서 만들지
         *   않는다 — 호출부가 A 문서 문구를 고른다.
         */
        data class Failed(val reason: KakaoLogin.LinkFailure, val httpCode: Int) : Start
    }

    /**
     * ①②를 한다. 성공하면 [Start.OpenBrowser].
     *
     * @param accessToken 익명 세션의 액세스 토큰([AuthService.accessToken]).
     *   🔴 **비어 있으면 부르지 않는다** — 토큰 없는 요청은 401이 되고, 그 401은
     *   "Manual Linking이 꺼짐"과 화면에서 구분되지 않는다.
     */
    suspend fun start(accessToken: String): Start {
        if (baseUrl.isEmpty() || anonKey.isEmpty() || accessToken.isEmpty()) {
            log("카카오 연결 시작 못 함 · 서버 설정 또는 토큰이 없다")
            return Start.Failed(KakaoLogin.LinkFailure.UNKNOWN, 0)
        }
        val url = SupabaseAuthUrls.linkIdentity(
            baseUrl = baseUrl,
            provider = SupabaseAuthUrls.PROVIDER_KAKAO,
            redirectTo = SupabaseAuthUrls.APP_REDIRECT,
        ) + "&$SKIP_REDIRECT"

        val res = runCatching { transport.get(url, anonKey, accessToken) }.getOrElse { e ->
            if (e is CancellationException) throw e
            log("카카오 연결 요청 실패 · ${e.javaClass.simpleName}")
            return Start.Failed(KakaoLogin.LinkFailure.UNKNOWN, 0)
        }

        if (res.code !in 200..299) {
            // 🔴 **코드가 아니라 본문의 `error_code`로 판단한다.** GoTrue는 기능이 꺼져
            //    있을 때도 404를 준다([KakaoLogin.linkFailureReason]).
            val code = runCatching { JSONObject(res.body).stringOrNull("error_code") }.getOrNull()
            val reason = KakaoLogin.linkFailureReason(code)
            log("카카오 연결 거절 · HTTP ${res.code} · ${code ?: "(코드 없음)"} → $reason")
            return Start.Failed(reason, res.code)
        }

        val target = runCatching { JSONObject(res.body).stringOrNull("url") }.getOrNull()
        if (target.isNullOrEmpty()) {
            // 성공 코드인데 열 주소가 없다. **성공으로 넘기지 않는다** —
            // 넘기면 아무 일도 안 일어나고 사용자는 버튼이 죽은 것으로 읽는다.
            log("카카오 연결 응답에 url이 없다 · HTTP ${res.code}")
            return Start.Failed(KakaoLogin.LinkFailure.UNKNOWN, res.code)
        }
        return Start.OpenBrowser(target)
    }

    internal object HttpTransport : Transport {
        override suspend fun get(
            url: String,
            apiKey: String,
            bearer: String,
        ): Transport.Response = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                // ⚠️ **리다이렉트를 따라가지 않는다.** `skip_http_redirect=true`가 있으면
                //    200 JSON이 오지만, 서버 동작이 바뀌어 302가 오면 따라가서
                //    카카오 HTML을 200으로 받는다 — 그게 "성공했는데 url이 없다"가 된다.
                instanceFollowRedirects = false
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Transport.Response(
                    code = code,
                    // ⚠️ **본문을 로그에 찍지 않는다** — 콜백 응답에는 토큰이 섞인다.
                    body = stream?.bufferedReader()?.use { it.readText() } ?: "",
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val SKIP_REDIRECT = "skip_http_redirect=true"

        /**
         * ⑤ 돌아온 주소가 **성공인가.**
         *
         * ⚠️ **`android.net.Uri`를 쓰지 않는다.** JVM 단위 테스트에서 `Uri.parse`는
         *    `not mocked`로 죽는다 — 그러면 이 판정을 검사할 방법이 사라진다.
         *    그래서 문자열로 직접 본다(`SupabaseAuthUrls`는 앱에서만 도므로 Uri를 쓴다).
         *
         * 🔴 **`error`가 있으면 실패다.** GoTrue는 실패도 **같은 주소로 돌려보낸다**
         *    (`…auth-callback#error=…&error_description=…`) — 즉 **인텐트가 왔다는 것은
         *    성공의 증거가 아니다.** 여기를 틀리면 연결이 안 됐는데 게이트가 열리고,
         *    그 기기의 기록은 익명 uuid에 남아 앱 데이터를 지우면 사라진다.
         *
         * 🔴 **토큰이 없어도 성공이다.** identity linking은 **세션을 새로 주지 않을 수
         *    있다**(이미 로그인한 사용자에 identity만 붙는다). `access_token`이 있는지로
         *    판정하면 **연결 성공이 실패로 읽힌다.**
         */
        fun callbackSucceeded(uri: String?): Boolean {
            if (uri.isNullOrEmpty()) return false
            if (!uri.startsWith(SupabaseAuthUrls.APP_REDIRECT)) return false
            // `?error=` · `#error=` · `&error=` 모두 실패다. `error_description`만 있고
            // `error`가 없는 응답은 본 적 없지만, 어느 쪽이든 있으면 실패로 본다.
            val tail = uri.removePrefix(SupabaseAuthUrls.APP_REDIRECT)
            return !tail.contains("error")
        }
    }
}
