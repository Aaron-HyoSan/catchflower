package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카카오 연결이 **성공했는가**를 무엇으로 판단하는가.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가
 *
 * 판정이 틀리면 증상이 **로그인 오류가 아니다.**
 *
 * - 실패를 성공으로 읽으면(`#error=`를 못 보면) → 연결이 안 됐는데 게이트가 열린다.
 *   그 기기의 기록은 익명 uuid에 남고, 앱 데이터를 지우면 **도감이 사라진다.**
 * - 성공을 실패로 읽으면(토큰이 있는지로 판단하면) → 로그인했는데 시트가 계속 뜬다.
 *   다시 누르면 서버가 이미 연결됐다고 거절하고, 그 거절은 `연결이 불안정해요`로 보인다.
 *
 * ## ⚠️ [KakaoLinkService.start]의 성공 경로는 JVM에서 못 잰다
 *
 * [SupabaseAuthUrls.linkIdentity]가 `android.net.Uri`를 쓴다 — android.jar 껍데기라
 * `RuntimeException: Stub!`으로 죽는다([KakaoLoginTest] 주석과 같은 벽이다).
 * 그래서 여기서 재는 것은 **URL을 만들기 전에 끝나는 경로**(설정·토큰 없음)와
 * 콜백 판정뿐이다. 응답 파싱(`url` 없는 2xx, `error_code` 읽기)은 **계측 테스트나
 * 실기기의 일**이다 — 안 잰 것을 잰 것으로 적지 않는다.
 */
class KakaoLinkServiceTest {

    /** 부르면 안 되는 전송로. 불리면 그 자체가 실패다. */
    private object NeverCalled : KakaoLinkService.Transport {
        override suspend fun get(
            url: String,
            apiKey: String,
            bearer: String,
        ): KakaoLinkService.Transport.Response =
            throw AssertionError("설정이 없는데 서버를 불렀다")
    }

    // ── ①② 시작 전 방어 ──────────────────────────────────────────────

    /**
     * 🔴 **토큰이 비었으면 요청을 보내지 않는다.**
     *
     * 보내면 서버가 401을 주는데, 그 401은 `Manual Linking이 꺼짐`과 화면에서
     * 구분되지 않는다 — 원인을 잘못 짚게 만드는 요청이라 아예 보내지 않는다.
     */
    @Test
    fun 토큰이_없으면_서버를_부르지_않는다() = runBlocking {
        val service = KakaoLinkService(
            baseUrl = "https://example.supabase.co",
            anonKey = "anon",
            transport = NeverCalled,
        )
        val result = service.start(accessToken = "")
        assertTrue("토큰 없이도 시작했다: $result", result is KakaoLinkService.Start.Failed)
        assertEquals(
            "HTTP를 안 보냈으므로 코드는 0이어야 한다 — 0이 아니면 서버 응답인 척하는 값이다",
            0,
            (result as KakaoLinkService.Start.Failed).httpCode,
        )
    }

    /** 키 없는 빌드에서도 같다. **서버 설정이 없으면 시작 자체가 없다.** */
    @Test
    fun 서버_설정이_없으면_서버를_부르지_않는다() = runBlocking {
        val service = KakaoLinkService(baseUrl = "", anonKey = "", transport = NeverCalled)
        assertTrue(service.start(accessToken = "token") is KakaoLinkService.Start.Failed)
    }

    // ── ⑤ 콜백 판정 ─────────────────────────────────────────────────

    /** 정상 복귀. 🔴 **토큰이 없어도 성공이다** — linking은 세션을 새로 주지 않을 수 있다. */
    @Test
    fun 오류가_없는_복귀는_성공이다() {
        assertTrue(KakaoLinkService.callbackSucceeded("catchflower://auth-callback"))
        assertTrue(KakaoLinkService.callbackSucceeded("catchflower://auth-callback#access_token=abc&expires_in=3600"))
    }

    /**
     * 🔴 **GoTrue는 실패도 같은 주소로 돌려보낸다.** 인텐트가 왔다는 것은 증거가 아니다.
     *
     * ⚠️ 이 검사가 없으면 위 `오류가_없는_복귀는_성공이다`만으로도 통과하는 구현이
     *    있다 — `fun callbackSucceeded(uri) = uri != null`. 그 구현에서 앱은
     *    **취소한 사용자에게도 게이트를 열어 준다.**
     */
    @Test
    fun 오류가_붙어_오면_실패다() {
        val failures = listOf(
            "catchflower://auth-callback#error=access_denied&error_description=User+denied",
            "catchflower://auth-callback?error=server_error",
            "catchflower://auth-callback#error_code=422&error=invalid_request",
        )
        for (uri in failures) {
            assertFalse("실패 복귀를 성공으로 읽었다: $uri", KakaoLinkService.callbackSucceeded(uri))
        }
    }

    /** 아무것도 안 왔으면 실패다. `noHistory` 액티비티가 데이터 없이 뜰 수 있다. */
    @Test
    fun 주소가_없으면_실패다() {
        assertFalse(KakaoLinkService.callbackSucceeded(null))
        assertFalse(KakaoLinkService.callbackSucceeded(""))
    }

    /**
     * ⚠️ **대조군.** 다른 스킴은 실패다.
     *
     * intent-filter가 이 주소만 받으므로 현실에서는 오지 않지만, 이 검사가 없으면
     * `!uri.contains("error")`만 남은 구현도 통과한다 — 그 구현은 **어떤 주소든**
     * 성공으로 읽는다.
     */
    @Test
    fun 다른_주소는_성공이_아니다() {
        assertFalse(KakaoLinkService.callbackSucceeded("bogusscheme://nope"))
        assertFalse(
            "Site URL로 조용히 보내진 경우다 — 이게 성공이면 Redirect URLs 누락이 숨는다",
            KakaoLinkService.callbackSucceeded("https://catchflower.example.com/"),
        )
    }

    /** 앱 복귀 주소는 **공유계약이 아니라 매니페스트와 짝**이다. 둘이 갈리면 인텐트가 안 온다. */
    @Test
    fun 복귀_주소가_매니페스트_필터와_같다() {
        assertEquals(
            "AndroidManifest.xml의 scheme·host와 같아야 한다 — 갈리면 브라우저가 받는 앱을 " +
                "못 찾고, 증상은 `눌러도 안 돼요`이며 로그에도 아무것도 안 남는다",
            "catchflower://auth-callback",
            SupabaseAuthUrls.APP_REDIRECT,
        )
    }
}
