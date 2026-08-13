package com.catchflower.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공유 시트로 **앱 밖으로 나가는 문장**([ShareText]).
 *
 * ## 왜 이 층에서 재는가
 *
 * 🔴 **이 세 문장은 우리 화면에 한 번도 안 그려진다.** 카카오톡·문자에 붙어서
 *    사용자 이름으로 남에게 전달된다 — 스크린샷 검토로도, `Text("…")`를 읽는
 *    [com.catchflower.app.ui.CopySourceTest]로도 볼 수 없다. **값을 만드는 함수가
 *    유일한 검증 지점이다.**
 *
 * ## 어떤 경우에 빨개지나
 *
 * - 조사를 `을`로 박아 두면 (`개나리을 모았어요`)
 * - 이름·종수가 없을 때 null 대신 문장을 만들면 (`캐치플라워에서  모았어요` ·
 *   `꽃 0종을 모았어요`가 남의 대화창에 남는다)
 * - 시즌 결과에 순위를 넣으면 — 이 화면은 지난 시즌 순위를 **영구히 모른다**(4절 9번).
 *   넣는 순간 **없는 값을 남에게 자랑하는 문장**이 된다.
 */
class ShareTextTest {

    @Test
    fun 꽃이름_조사가_받침을_따라간다() {
        // 받침 있음 → `을`, 없음 → `를`. 하나를 박아 두면 절반이 틀린다.
        assertEquals("캐치플라워에서 금계국을 모았어요", ShareText.flower("금계국"))
        assertEquals("캐치플라워에서 개나리를 모았어요", ShareText.flower("개나리"))
    }

    @Test
    fun 이름이_없으면_문장을_안_만든다() {
        assertNull("이름이 null인데 문장을 만들었다", ShareText.flower(null))
        assertNull("빈 이름으로 문장을 만들었다", ShareText.flower(""))
        // ⚠️ 공백만 있는 이름도 빈 이름이다. `trim` 없이 `isEmpty`만 보면
        //    `캐치플라워에서  를 모았어요`가 나간다.
        assertNull("공백만 있는 이름으로 문장을 만들었다", ShareText.flower("   "))
    }

    @Test
    fun 시즌_결과는_종수만_말한다() {
        val text = ShareText.seasonResult(37)
        assertEquals("이번 시즌에 꽃 37종을 모았어요 · 캐치플라워", text)
        // 🔴 순위를 말하지 않는다. `위`·`순위`가 들어가면 4절 9번을 어긴 것이다.
        assertTrue("공유 문장이 순위를 말한다: $text", text!!.none { it == '위' })
        assertTrue("공유 문장에 `순위`가 들어갔다: $text", "순위" !in text)
    }

    @Test
    fun 종수를_모르거나_0이면_문장이_없다() {
        assertNull("종수를 못 셌는데 문장을 만들었다", ShareText.seasonResult(null))
        assertNull("`꽃 0종을 모았어요`를 만들었다", ShareText.seasonResult(0))
        // 있을 수 없는 값이지만, 음수가 오면 `꽃 -3종`이 남에게 간다.
        assertNull("음수 종수로 문장을 만들었다", ShareText.seasonResult(-3))
    }

    @Test
    fun 초대_문장에_링크가_들어있다() {
        val url = AppLinks.playStore("com.catchflower.app")
        val text = ShareText.invite(url)
        assertTrue("초대 문장에 링크가 없다 — 초대장에 초대할 곳이 없다: $text", url in text)
        assertTrue("초대 문장이 A 문서 ① 문구가 아니다: $text", text.startsWith("같이 동네 꽃을 모아요 · 캐치플라워"))
    }

    /**
     * 스토어 링크가 **`applicationId`에서 나온다.**
     *
     * ⚠️ 여기에 `com.catchflower.app`을 글자로 박으면 이 검사는 그 사실을 못 잰다 —
     *    그래서 **다른 id를 넣어** 링크가 따라 움직이는지 본다. 안 따라가면
     *    출시 후 초대 링크가 남의 앱을 가리킨다.
     */
    @Test
    fun 스토어_링크가_패키지명을_따라간다() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.other",
            AppLinks.playStore("com.example.other"),
        )
        assertTrue(
            "스토어 링크가 https가 아니다 — 안드로이드 9+ 기본 설정에서 열리지 않는다",
            AppLinks.playStore("com.catchflower.app").startsWith("https://"),
        )
    }
}
