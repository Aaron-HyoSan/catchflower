package com.catchflower.app.data

import com.catchflower.app.data.AuthService.Companion.TokenStep
import com.catchflower.app.data.AuthService.Companion.nextTokenStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 토큰이 없을 때 무엇을 할지 정하는 규칙.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가 — 이 결함은 **에뮬레이터에서 잡혔다**
 *
 * 화면 02에서 `연남동으로 시작하기`를 눌렀더니 `연결이 불안정해요`가 떴다.
 * 그런데 **로그가 한 줄도 없었다.** 서버는 정상이었다 — 같은 순간 호스트에서
 * `POST /auth/v1/signup`을 보내니 **HTTP 200 + 토큰**이 왔다.
 *
 * 기기 `shared_prefs`를 열어 보니 `local_user_id` **하나뿐**이었다.
 * `auth_user_id`·`auth_access_token`·`auth_refresh_token`이 전부 없었다:
 * 첫 실행에서 익명 로그인이 한 번 실패한 상태였다.
 *
 * 그 상태에서 [AuthService.accessToken]은 "토큰 없음 → 갱신 → `refresh_token`도
 * 없음 → null"로 끝났고, **가입을 다시 시도하는 [AuthService.userId]에는 아무도
 * 닿지 못했다** — 서버를 쓰는 다섯 곳이 전부 `accessToken()`을 **먼저** 부른다
 * (`RegionUpdateService`·`RankingService`×4·`DiscoveryUploader`).
 * 즉 **한 번의 실패가 서버 기능 전체를 영구히 끈다.** 다음 실행도 같은 자리다.
 *
 * ⚠️ **초록 테스트 310개가 이걸 못 잡았다.** 각 서비스의 401 처리는 전부 테스트돼
 *    있었다 — 잘못된 것이 없었다. 빠진 것은 **누가 가입을 재시도하는가**였고,
 *    그건 클래스 하나 안이 아니라 **호출 순서에 있었다.**
 *
 * ⚠️ 그래서 판단을 [AuthService.nextTokenStep]으로 뺐다. [AuthService]는 `Context`를
 *    받고 이 프로젝트에 Robolectric이 없으므로, 분기를 그 안에 두면
 *    **어떤 테스트도 실행하지 않는 코드**가 된다.
 */
class AuthTokenStepTest {

    private val now = 1_780_000_000_000L
    private val token = "eyJhbGciOi.access"

    private fun step(
        token: String? = this.token,
        expiresAt: Long = now + 3_600_000L,
        hasRefreshToken: Boolean = true,
        hasAccount: Boolean = true,
        configured: Boolean = true,
    ) = nextTokenStep(
        token = token,
        expiresAt = expiresAt,
        now = now,
        hasRefreshToken = hasRefreshToken,
        hasAccount = hasAccount,
        configured = configured,
    )

    // ── 🔴 실측으로 잡힌 결함 ────────────────────────────────────────

    /**
     * 🔴 **이 테스트가 이 파일의 이유다.** 실측된 기기 상태 그대로다:
     *    토큰 없음 · `refresh_token` 없음 · **계정 없음**(로컬 uuid만) · 키는 있음.
     *
     *    [TokenStep.GIVE_UP]이면 화면 02는 `연결이 불안정해요`를 띄우고
     *    **다음 실행에서도 똑같다** — 서버·키·네트워크가 전부 정상인데
     *    랭킹·업로드·지역 저장이 영구히 죽는다.
     */
    @Test
    fun 계정이_없으면_가입을_다시_시도한다() {
        assertEquals(
            "익명 로그인이 한 번 실패한 기기에서 서버 기능이 영구히 죽는다",
            TokenStep.SIGN_UP,
            step(token = null, hasRefreshToken = false, hasAccount = false),
        )
    }

    /**
     * ⚠️ **계정이 있으면 가입하지 않는다.** 가면 토큰이 만료됐을 뿐인 사용자에게
     *    **새 계정**이 생기고, 그동안 올린 기록은 RLS 때문에 보이지도 지워지지도
     *    않는 주인 없는 데이터가 된다([AuthService.reset] 주석과 같은 사고다).
     */
    @Test
    fun 계정이_있으면_가입하지_않는다() {
        assertEquals(
            "토큰만 잃은 사용자에게 새 계정을 만들었다 — 기존 기록이 주인을 잃는다",
            TokenStep.GIVE_UP,
            step(token = null, hasRefreshToken = false, hasAccount = true),
        )
    }

    /**
     * ⚠️ 키 없는 빌드에서는 부르지 않는다. `configured`가 false면 `baseUrl`이 비어 있어
     *    가입 요청이 `"/auth/v1/signup"`으로 나간다.
     */
    @Test
    fun 키가_없으면_가입도_하지_않는다() {
        assertEquals(
            TokenStep.GIVE_UP,
            step(token = null, hasRefreshToken = false, hasAccount = false, configured = false),
        )
    }

    // ── 갱신이 먼저다 ───────────────────────────────────────────────

    /**
     * 🔴 **`refresh_token`이 있으면 가입보다 갱신이 먼저다.** 순서를 뒤집으면
     *    잠깐 만료된 사용자마다 새 계정이 생긴다 — 위와 같은 사고이면서
     *    **계정이 무한히 늘어난다.**
     */
    @Test
    fun 갱신이_가입보다_먼저다() {
        assertEquals(TokenStep.REFRESH, step(token = null, hasRefreshToken = true, hasAccount = false))
    }

    /** 토큰이 만료됐고 `refresh_token`이 있으면 갱신이다. */
    @Test
    fun 만료되면_갱신한다() {
        assertEquals(TokenStep.REFRESH, step(expiresAt = now - 1))
    }

    // ── 보관한 토큰 ─────────────────────────────────────────────────

    @Test
    fun 살아_있는_토큰은_그대로_쓴다() {
        assertEquals(TokenStep.USE_STORED, step())
    }

    /**
     * 🔴 **만료 5분 전부터는 갱신한다.** 여유가 없으면 만료 직전에 보낸 요청이
     *    서버에 닿을 때 이미 죽어 있다 — 401이 되고 사용자는 이유를 모른다.
     */
    @Test
    fun 만료_오분_전에는_미리_갱신한다() {
        // 4분 남음 → 여유(5분) 안이므로 갱신
        assertEquals(TokenStep.REFRESH, step(expiresAt = now + 4 * 60 * 1000L))
        // 6분 남음 → 그대로 쓴다
        assertEquals(TokenStep.USE_STORED, step(expiresAt = now + 6 * 60 * 1000L))
    }

    /**
     * ⚠️ **`expiresAt`이 0이면(수명을 모르는 응답) 만료로 본다.** `AuthSessionTest`의
     *    `수명을_모르면_만료로_본다`와 짝이다 — 그쪽이 0을 만들고 여기가 0을 해석한다.
     */
    @Test
    fun 수명을_모르는_토큰은_갱신_대상이다() {
        assertEquals(TokenStep.REFRESH, step(expiresAt = 0L))
    }

    /**
     * ⚠️ 토큰이 없는데 `expiresAt`이 미래인 어긋난 상태에서도 **토큰을 쓰지 않는다.**
     *    `USE_STORED`가 되면 호출부가 null을 bearer로 보낸다.
     */
    @Test
    fun 토큰이_없으면_유효기간이_남아도_쓰지_않는다() {
        assertEquals(TokenStep.REFRESH, step(token = null, expiresAt = now + 3_600_000L))
    }
}
