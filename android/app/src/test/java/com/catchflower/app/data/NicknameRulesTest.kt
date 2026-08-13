package com.catchflower.app.data

import com.catchflower.app.ui.my.ProfileEditViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 닉네임 판정.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가 — 세 가지가 조용히 틀린다
 *
 * 1. **공백만 넣은 닉네임.** 서버 `users.nickname text not null`은 `"   "`를 막지 않는다.
 *    저장되면 랭킹 목록에 **이름이 없는 줄**이 생기고, 그 줄이 자기 줄이어도 못 알아본다.
 * 2. **길이를 `String.length`로 세는 것.** 이모지는 UTF-16 두 칸이라
 *    `🌸🌸🌸🌸🌸🌸`(6자)가 12로 세어져 거부된다 — **한글로만 재면 절대 안 보인다**
 *    ([FriendRulesTest]가 같은 함정을 이미 겪었다).
 * 3. **닉네임을 못 받은 상태에서 편집 화면을 여는 것.** 빈 칸이 뜨고, 거기서 저장하면
 *    **닉네임을 지운다.** 이건 화면에서만 드러나므로 [ProfileEditViewModel.openable]로
 *    끌어내려 여기서 잰다.
 */
class NicknameRulesTest {

    // ── 빈 값 ────────────────────────────────────────────────────────

    @Test
    fun 빈_문자열은_Empty다() {
        assertEquals(NicknameRules.Verdict.Empty, NicknameRules.validate(""))
    }

    /** 🔴 위 ①. 서버 `not null`이 안 막는 자리다. */
    @Test
    fun 공백만_넣으면_Empty다() {
        assertEquals(NicknameRules.Verdict.Empty, NicknameRules.validate("   "))
        assertEquals(NicknameRules.Verdict.Empty, NicknameRules.validate("\t\n "))
    }

    // ── 길이 ─────────────────────────────────────────────────────────

    @Test
    fun 한_글자도_쓸_수_있다() {
        assertEquals(NicknameRules.Verdict.Ok("김"), NicknameRules.validate("김"))
    }

    @Test
    fun 열_글자는_되고_열한_글자는_안_된다() {
        val ten = "가나다라마바사아자차"
        assertEquals(10, ten.length)
        assertEquals(NicknameRules.Verdict.Ok(ten), NicknameRules.validate(ten))
        assertEquals(NicknameRules.Verdict.TooLong, NicknameRules.validate(ten + "카"))
    }

    /**
     * 🔴 위 ②. **이 테스트만 `String.length` 구현에서 빨개진다.**
     *
     * 이모지 6개는 코드 포인트로 6, UTF-16으로 12다. 위의 `열_글자는…` 테스트는
     * 둘 중 어느 구현에서도 초록이다 — 한글은 두 셈법이 같기 때문이다.
     */
    @Test
    fun 이모지_여섯_개는_여섯_글자다() {
        val six = "🌸🌸🌸🌸🌸🌸"
        assertEquals("UTF-16으로는 12칸이다 — 이게 이 테스트의 전부다", 12, six.length)
        assertEquals(NicknameRules.Verdict.Ok(six), NicknameRules.validate(six))
    }

    @Test
    fun 이모지도_열한_개면_길다() {
        assertEquals(NicknameRules.Verdict.TooLong, NicknameRules.validate("🌸".repeat(11)))
    }

    /** 코드 포인트 셈법이라도 **한글+이모지 섞인 경계**를 한 번 더 못 박는다. */
    @Test
    fun 한글과_이모지를_섞어도_열_글자까지다() {
        assertEquals(NicknameRules.Verdict.Ok("꽃🌸꽃🌸꽃🌸꽃🌸꽃🌸"), NicknameRules.validate("꽃🌸꽃🌸꽃🌸꽃🌸꽃🌸"))
        assertEquals(NicknameRules.Verdict.TooLong, NicknameRules.validate("꽃🌸꽃🌸꽃🌸꽃🌸꽃🌸꽃"))
    }

    // ── 앞뒤 공백 ────────────────────────────────────────────────────

    @Test
    fun 앞뒤_공백을_떼고_보낸다() {
        assertEquals(NicknameRules.Verdict.Ok("꽃친구"), NicknameRules.validate("  꽃친구 "))
    }

    /**
     * 공백을 떼고 세므로 `열 글자 + 공백`은 통과한다. 안 그러면 사용자가
     * **보이지 않는 글자 때문에** `10자까지 쓸 수 있어요`를 듣는다.
     */
    @Test
    fun 공백을_떼고_길이를_센다() {
        assertEquals(
            NicknameRules.Verdict.Ok("가나다라마바사아자차"),
            NicknameRules.validate("  가나다라마바사아자차  "),
        )
    }

    // ── 바뀐 것이 있는가 ─────────────────────────────────────────────

    @Test
    fun 같은_값이면_안_바뀐_것이다() {
        assertFalse(NicknameRules.changed("꽃친구", "꽃친구"))
    }

    /** ⚠️ 앞뒤 공백만 지운 편집도 **안 바뀐 것**이다 — 서버를 왕복하지 않는다. */
    @Test
    fun 앞뒤_공백만_지운_것은_안_바뀐_것이다() {
        assertFalse(NicknameRules.changed(" 꽃친구 ", "꽃친구"))
    }

    @Test
    fun 다른_값이면_바뀐_것이다() {
        assertTrue(NicknameRules.changed("꽃친구", "꽃박사"))
    }

    /** 닉네임을 몰랐다면(null) 무엇을 넣어도 바뀐 것이다. */
    @Test
    fun 원래_값을_모르면_바뀐_것이다() {
        assertTrue(NicknameRules.changed(null, "꽃친구"))
    }

    // ── 화면을 열 수 있는가 ──────────────────────────────────────────

    /**
     * 🔴 위 ③. 이 네 줄이 "빈 칸을 저장해 닉네임을 지우는" 경로를 막는다.
     */
    @Test
    fun 닉네임을_모르면_수정_화면을_열지_않는다() {
        assertFalse(ProfileEditViewModel.openable(null, savable = true))
        assertFalse(ProfileEditViewModel.openable("", savable = true))
        assertFalse("공백만 있는 닉네임도 못 받은 것이다", ProfileEditViewModel.openable("   ", savable = true))
        assertTrue(ProfileEditViewModel.openable("꽃친구", savable = true))
    }

    /** 키 없는 빌드에서는 저장할 곳이 없다 — 열면 그 버튼이 다시 죽은 버튼이 된다. */
    @Test
    fun 저장할_수_없는_빌드에서는_열지_않는다() {
        assertFalse(ProfileEditViewModel.openable("꽃친구", savable = false))
    }
}
