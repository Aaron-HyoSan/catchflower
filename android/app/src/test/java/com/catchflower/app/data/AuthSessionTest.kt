package com.catchflower.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 세션 응답 해석. **토큰 수명 계산이 여기 있다.**
 *
 * **왜 테스트하는가.** 토큰 만료는 **1시간 뒤에 나타나는 버그**다. 앱을 켜서 확인하는
 * 동안에는 항상 정상으로 보이고, 하루 뒤 첫 업로드부터 401이 된다 — 그리고 화면에는
 * 아무 증상이 없다(도감은 로컬이다). 실기기로 잡을 수 없는 종류다.
 *
 * ⚠️ 아래 응답은 **실제 GoTrue가 준 형식이다**(진행.md (30)). 내가 상상한 형식으로
 *    쓰면 테스트만 통과한다.
 */
class AuthSessionTest {

    private val userId = "b92a22d2-2fe7-41ff-956e-a251731171af"

    private fun response(
        expiresIn: Long? = 3600,
        accessToken: String? = "eyJhbGciOi.access",
        refreshToken: String? = "rt-original",
        expiresAt: Long? = 1786003600,
    ) = JSONObject().apply {
        accessToken?.let { put("access_token", it) }
        put("token_type", "bearer")
        expiresIn?.let { put("expires_in", it) }
        expiresAt?.let { put("expires_at", it) }
        refreshToken?.let { put("refresh_token", it) }
        put("user", JSONObject().put("id", userId).put("is_anonymous", true))
    }

    @Test
    fun 사용자_id를_읽는다() {
        assertEquals(userId, AuthService.parseSession(response()).userId)
    }

    /**
     * 🔴 **만료는 `expires_in`으로 계산한다 — 서버의 `expires_at`을 쓰지 않는다.**
     *
     * 빨개지는 경우: `expires_at`(서버 시계 기준 절대 초)을 그대로 쓰면.
     * 아래 응답의 `expires_at`은 2026년이고 `now`는 0이다 — 서버 값을 쓰면
     * 결과가 `1786003600000`이 된다. 기기 시계가 어긋난 사용자는
     * **만료된 토큰을 "아직 남았다"로 읽고 401을 맞는다.**
     */
    @Test
    fun 만료는_받은_순간부터_센다() {
        val s = AuthService.parseSession(response(expiresIn = 3600, expiresAt = 1786003600), now = 0L)
        assertEquals(
            "서버의 절대 시각을 그대로 썼다 — 기기 시계가 어긋나면 401을 맞는다",
            3_600_000L,
            s.expiresAt,
        )
    }

    /** 기기 시계 기준이므로 `now`가 움직이면 같이 움직인다. */
    @Test
    fun 기기_시계를_기준으로_삼는다() {
        val now = 1_700_000_000_000L
        assertEquals(now + 3_600_000L, AuthService.parseSession(response(), now = now).expiresAt)
    }

    /**
     * 🔴 **`expires_in`이 없으면 0(= 즉시 갱신 대상)이다.**
     *
     * 빨개지는 경우: 없을 때 먼 미래나 기본 수명을 넣으면. 그러면 앱이 죽은 토큰을
     * 유효하다고 믿고 **그 실행 내내 401**이며, 갱신을 시도하지 않는다.
     * 모르는 것은 **만료됐다로 취급**해야 안전하다.
     */
    @Test
    fun 수명을_모르면_만료로_본다() {
        assertEquals(
            "수명 없는 응답에 유효 기간을 만들어냈다",
            0L,
            AuthService.parseSession(response(expiresIn = null), now = 1_700_000_000_000L).expiresAt,
        )
    }

    /**
     * 🔴 **회전된 `refresh_token`을 반드시 읽는다.**
     *
     * 빨개지는 경우: `refresh_token`을 안 읽으면(null이 되면). `save`가 빈 값을 쓰고
     * **다음 갱신부터 영구 실패한다.** 옛 값도 잠깐 통하는 재사용 창이 있어서
     * **바로는 증상이 안 나오고 며칠 뒤에 조용히 로그인이 끊긴다.**
     */
    @Test
    fun 갱신_토큰을_읽는다() {
        val s = AuthService.parseSession(response(refreshToken = "rt-rotated-2"))
        assertEquals("갱신 토큰을 버리면 며칠 뒤 조용히 로그인이 끊긴다", "rt-rotated-2", s.refreshToken)
        assertNotNull(s.accessToken)
    }

    /** 빈 문자열은 없는 것과 같다. `optString`은 없을 때 ""를 준다. */
    @Test
    fun 빈_토큰은_없는_것으로_본다() {
        val s = AuthService.parseSession(response(accessToken = null, refreshToken = null))
        assertNull(s.accessToken)
        assertNull("빈 문자열을 갱신 토큰으로 저장하면 갱신이 조용히 실패한다", s.refreshToken)
    }

    /** `user.id`가 없는 응답은 세션이 아니다 — 던져서 호출부가 null로 처리한다. */
    @Test
    fun 사용자가_없으면_세션이_아니다() {
        val broken = JSONObject().put("access_token", "x").put("expires_in", 3600)
        val result = runCatching { AuthService.parseSession(broken) }
        assertTrue("id 없는 응답을 세션으로 받아들였다", result.isFailure)
    }

    /**
     * `expires_in`이 초 단위라는 것을 고정한다.
     *
     * 빨개지는 경우: ms로 착각해 `* 1000`을 빼면. 3600ms = 3.6초 만료라
     * **모든 업로드가 매번 갱신을 먼저 한다**(그리고 여유 5분 때문에 항상 만료로 읽힌다).
     */
    @Test
    fun 수명은_초_단위다() {
        assertEquals(60_000L, AuthService.parseSession(response(expiresIn = 60), now = 0L).expiresAt)
    }
}
