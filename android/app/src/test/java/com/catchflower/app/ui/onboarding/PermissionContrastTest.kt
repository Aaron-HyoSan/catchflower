package com.catchflower.app.ui.onboarding

import androidx.compose.ui.graphics.Color
import com.catchflower.app.ui.theme.CfColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 03의 글자·배경 조합이 **명도 대비 4.5:1을 넘는가.**
 *
 * B 문서 25행: `저채도 회색 글자(#999 이하)를 본문에 쓰지 않는다. 명도 대비 4.5:1 이상 확보.`
 * 타깃이 40~50대 여성이라 이건 취향이 아니라 스펙이다.
 *
 * ⚠️ **이 테스트가 잡아낸 실제 결함.** 단서 줄(`앨범 사진은 등록할 수 없어요.`)에
 *    [CfColor.TextTertiary]를 썼는데, 그 색 주석에 적힌 `대비 4.5:1`은 **흰 배경 기준**이다.
 *    카드 배경은 [CfColor.Surface](#F7F7F5)라서 실제 대비는 **4.23:1**로 미달이었다.
 *    화면을 봐도, 문구 테스트를 돌려도 알 수 없다 — 글자는 또렷해 보이고 문구는 맞다.
 *
 * ⚠️ **이 테스트도 한 번 거짓 초록이었다.** 처음에는 여기에 쌍을 직접 적었다
 *    (`assertReadable(TextSecondary, Surface)`). 그 상태로 화면의 색을 `TextTertiary`로
 *    되돌리는 돌연변이를 심었더니 **그대로 통과했다** — 검사한 게 화면이 아니라
 *    테스트가 스스로 적어 둔 상수였기 때문이다. 그래서 쌍을
 *    [PermissionIntroPalette.pairs] **한 곳에만** 두고 화면과 테스트가 같은 값을 읽는다.
 */
class PermissionContrastTest {

    /** WCAG 상대 휘도. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /**
     * 계산식 자체가 맞는지 먼저 고정한다.
     *
     * ⚠️ 이게 없으면 [luminance]가 통째로 틀려도 (예: 감마 보정을 빼먹어도)
     *    모든 조합이 여유롭게 통과하면서 **초록이 된다.** 검사 도구를 검사한다.
     */
    @Test
    fun 대비_계산이_맞다() {
        // 흑백은 정의상 21:1, 같은 색끼리는 1:1이다.
        assertTrue(ratio(Color.Black, Color.White) > 20.9)
        assertTrue(ratio(Color.White, Color.White) < 1.01)

        // ⚠️ **이 줄이 이 파일의 근거다.** 같은 `TextTertiary`가 배경에 따라
        //    합격(흰 배경)과 불합격(카드 배경)으로 갈린다 — 대비는 색 하나의
        //    속성이 아니라 **쌍**의 속성이라는 뜻이다.
        val onWhite = ratio(CfColor.TextTertiary, Color.White)
        val onCard = ratio(CfColor.TextTertiary, CfColor.Surface)
        assertTrue("TextTertiary on 흰배경 = ${"%.2f".format(onWhite)} (4.5 이상이어야)", onWhite >= 4.5)
        assertTrue("TextTertiary on 카드 = ${"%.2f".format(onCard)} (4.5 미달이어야)", onCard < 4.5)
    }

    /**
     * **화면이 실제로 쓰는 쌍 전부**를 [PermissionIntroPalette.pairs]에서 읽어 검사한다.
     * 쌍을 여기에 다시 적지 않는 것이 핵심이다 (클래스 주석의 거짓 초록 참고).
     */
    @Test
    fun 화면_03의_모든_색_조합이_4대5_대비를_넘는다() {
        val failures = PermissionIntroPalette.pairs
            .map { it to ratio(it.foreground, it.background) }
            .filter { (_, r) -> r < 4.5 }
            .map { (pair, r) -> "${pair.label} ${"%.2f".format(r)}:1" }

        assertTrue(
            "B 문서 하한 4.5:1 미달: $failures",
            failures.isEmpty(),
        )
    }

    /**
     * 팔레트에 선언만 해 두고 [PermissionIntroPalette.pairs]에 넣지 않으면
     * **그 색은 검사를 받지 않는다.** 목록이 조용히 비는 것을 막는다.
     *
     * ⚠️ 새 요소를 추가하면 이 개수도 함께 올린다. 개수를 세는 이유는,
     *    `pairs`를 빈 리스트로 만들어도 위 테스트가 "미달 없음"으로 통과하기 때문이다.
     */
    @Test
    fun 검사_대상이_비어_있지_않다() {
        assertEquals(
            "화면 03의 색 조합 수가 달라졌다 — 요소를 추가했으면 pairs에도 넣었는지 확인한다",
            12,
            PermissionIntroPalette.pairs.size,
        )
        // 라벨이 겹치면 서로 다른 자리를 같은 것으로 착각해 하나를 빠뜨린다.
        assertEquals(
            PermissionIntroPalette.pairs.size,
            PermissionIntroPalette.pairs.map { it.label }.distinct().size,
        )
    }
}
