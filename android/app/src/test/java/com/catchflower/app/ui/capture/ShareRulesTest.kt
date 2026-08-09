package com.catchflower.app.ui.capture

import com.catchflower.app.core.GamePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 13 한 줄·좌표 규칙 ([ShareRules]).
 *
 * **왜 여기서 재는가.** 이 규칙은 **다른 어느 층에서도 검증되지 않는다**:
 * - `adb shell input text`가 한글을 못 보내서 **기기로 타이핑해도 못 잰다**(8차 실측).
 * - Composable 안에 두면 JVM 테스트가 아예 못 들어간다.
 * - 서버는 `char_length(note) <= 40`으로 거절만 하고, 그 거절은 **화면에 안 보인다**
 *   (업로드는 백그라운드다 · `DiscoveryRepository.push`).
 *
 * 그래서 여기가 이 규칙의 **유일한 검증층**이다.
 */
class ShareRulesTest {

    /** 서버 제약과 같은 수인가. 여기가 갈리면 카운터가 서버와 다른 말을 한다. */
    @Test
    fun 한도는_정책_상수를_따른다() {
        assertEquals(GamePolicy.SHARE_NOTE_MAX_LENGTH, ShareRules.NOTE_MAX)
        assertEquals(40, ShareRules.NOTE_MAX)
    }

    @Test
    fun 카운터는_A문서_형식이다() {
        // A 문서 13번 표: `글자 수 | 0 / 40`
        assertEquals("0 / 40", ShareRules.counter(""))
        assertEquals("4 / 40", ShareRules.counter("숲길 끝"))
    }

    /**
     * 🔴 **길이를 코드포인트로 센다.**
     *
     * 빨개지는 경우: [ShareRules.length]가 `String.length`를 쓰면. 이모지 하나가 2로
     * 세어져서 **화면 카운터가 서버(`char_length`)와 다른 숫자를 보여준다** —
     * 사용자는 20자를 썼는데 40이 찍힌다.
     */
    @Test
    fun 이모지는_한_글자로_센다() {
        // U+1F337 TULIP — 서로게이트 쌍이라 `length`로는 2다.
        assertEquals(2, "🌷".length)
        assertEquals(1, ShareRules.length("🌷"))
        assertEquals(3, ShareRules.length("꽃🌷!"))
    }

    /**
     * 🔴 **자를 때 서로게이트 쌍을 쪼개지 않는다.**
     *
     * 빨개지는 경우: `take(40)`을 쓰면. 40번째가 이모지 앞짝이면 **반토막 난 글자
     * 한 개**가 남고, 그게 JSON에 그대로 실려 서버로 나간다.
     * 화면에서는 깨진 사각형 하나라 "이모지가 안 나오네" 정도로 보인다.
     */
    @Test
    fun 자를_때_서로게이트_쌍을_쪼개지_않는다() {
        // 39자 + 이모지 1자 = 40자. 여기까지는 그대로 남아야 한다.
        val exact = "가".repeat(39) + "🌷"
        assertEquals(40, ShareRules.length(exact))
        assertEquals(exact, ShareRules.sanitize(exact))

        // ⚠️ **이 표본이 중요하다.** `가`40개 + 이모지로 재면 `take(40)`도 같은 답을
        //    내서(이모지가 40번째 뒤에 있다) **틀린 구현이 통과한다** — 처음에 그렇게
        //    짰다가 돌연변이 검증에서 살아남았다. 자르는 자리가 **쌍의 가운데**에
        //    오도록 이모지를 앞에 깔아야 한다.
        //    `가` 1개 + 이모지 40개 = 41자이고, UTF-16으로는 1 + 80 = 81단위다.
        //    → 코드포인트 40번째는 81단위 중 79번째, `take(40)`은 40번째 단위 =
        //      19번째 이모지의 **앞짝**에서 끊긴다.
        val over = "가" + "🌷".repeat(40)
        assertEquals(41, ShareRules.length(over))
        val cut = ShareRules.sanitize(over)

        assertEquals("잘랐는데 글자 수가 한도와 다르다", 40, ShareRules.length(cut))
        assertEquals("가" + "🌷".repeat(39), cut)
        // 🔴 이 단정이 `take(40)`을 잡는다. 반쪽 서로게이트는 화면에서 깨진 사각형
        //    하나로만 보이고, 그대로 JSON에 실려 서버로 나간다.
        assertFalse(
            "반토막 난 서로게이트가 남았다 — 코드포인트가 아니라 UTF-16으로 잘랐다",
            cut.last().isSurrogate() && !cut.last().isLowSurrogate(),
        )
        assertEquals("쌍이 홀수로 남았다", 0, cut.count { it.isHighSurrogate() } - cut.count { it.isLowSurrogate() })
    }

    @Test
    fun 넘치면_한도까지_자른다() {
        val cut = ShareRules.sanitize("가".repeat(60))
        assertEquals(40, ShareRules.length(cut))
    }

    /**
     * 🔴 **입력 중에는 공백을 압축하지 않는다.**
     *
     * 빨개지는 경우: [ShareRules.sanitize]가 `trim`이나 공백 압축을 하면.
     * `숲길 `을 타이핑하는 중에 뒤 공백이 지워져서 **다음 글자를 이어 쓸 수 없다** —
     * 입력이 되지 않는 필드가 되는데, 코드에는 "정리한다"고만 적혀 있다.
     */
    @Test
    fun 입력중_뒤공백을_지우지_않는다() {
        assertEquals("숲길 ", ShareRules.sanitize("숲길 "))
        assertEquals("숲길  끝", ShareRules.sanitize("숲길  끝"))
    }

    /**
     * 붙여넣기로 들어온 줄바꿈·탭은 공백으로 바꾼다.
     *
     * 빨개지는 경우: 그냥 통과시키면. `singleLine`은 **키보드 입력만** 막고
     * 붙여넣기는 못 막아서, 한 줄 필드에 여러 줄이 들어간다.
     */
    @Test
    fun 붙여넣은_줄바꿈은_공백이_된다() {
        assertEquals("숲길 끝", ShareRules.sanitize("숲길\n끝"))
        assertEquals("숲길 끝", ShareRules.sanitize("숲길\t끝"))
        assertEquals("숲길 끝", ShareRules.sanitize("숲길\r끝"))
    }

    /**
     * 🔴 **안 쓴 한 줄은 `null`이다. `""`가 아니다.**
     *
     * 빨개지는 경우: `""`를 돌려주면. `DiscoveryStore.toJson`이 `putOpt`를 쓰는데
     * **`putOpt`는 null 키만 빼고 `""`는 넣는다** — 서버 행에 `note = ''`가 박힌다.
     * 그리고 로컬에서는 `fromJson`의 `stringOrNull`이 빈 문자열도 null로 읽어서
     * **왕복 테스트로는 안 잡힌다.** 서버에서만 진짜인 결함이다.
     */
    @Test
    fun 안_쓴_한줄은_null이다() {
        assertNull(ShareRules.toStored(""))
        assertNull("공백만 쓴 것도 안 쓴 것이다", ShareRules.toStored("   "))
        assertNull(ShareRules.toStored("\n"))
    }

    /** 저장 시점에는 앞뒤 공백을 정리한다 (입력 중과 다르다). */
    @Test
    fun 저장할_때_앞뒤_공백을_정리한다() {
        assertEquals("숲길 끝", ShareRules.toStored("  숲길 끝  "))
        // 가운데 공백은 사용자가 쓴 것이다. 건드리지 않는다.
        assertEquals("숲길  끝", ShareRules.toStored("숲길  끝"))
    }

    /**
     * 저장 시점에도 한도를 지킨다.
     *
     * 빨개지는 경우: [ShareRules.toStored]가 자르지 않고 [ShareRules.sanitize]만
     * 믿으면. `sanitize`를 안 지나는 경로(초기값·복원)가 생기면 서버가 400을 준다.
     */
    @Test
    fun 저장할_때도_한도를_지킨다() {
        val stored = ShareRules.toStored(" " + "가".repeat(50) + " ")
        assertEquals(40, ShareRules.length(stored!!))
    }

    @Test
    fun 한도에_닿으면_알린다() {
        assertFalse(ShareRules.isAtLimit("가".repeat(39)))
        assertTrue(ShareRules.isAtLimit("가".repeat(40)))
        // 이모지 40개는 `length`로 80이지만 한도에는 딱 닿는다.
        assertTrue(ShareRules.isAtLimit("🌷".repeat(40)))
        assertFalse("이모지를 2로 세어 한도를 앞당겼다", ShareRules.isAtLimit("🌷".repeat(20)))
    }

    /**
     * 🔴 **좌표가 없으면 지도에 올릴 수 없다.**
     *
     * 빨개지는 경우: `true`를 돌려주면. `MapPins.from`이 좌표 없는 기록을 버리므로
     * **`public`으로 저장되는데 어느 지도에도 안 보인다** — 동의만 받고 아무것도
     * 못 보여주는 상태이고, 사용자는 공유가 됐다고 믿는다.
     */
    @Test
    fun 좌표가_없으면_공유할_수_없다() {
        assertTrue(ShareRules.canPlaceOnMap(37.5445, 127.0557))
        assertFalse(ShareRules.canPlaceOnMap(null, null))
        // 한쪽만 있는 것도 못 쓴다 — 반쪽 좌표로는 핀을 찍을 수 없다.
        assertFalse(ShareRules.canPlaceOnMap(37.5445, null))
        assertFalse(ShareRules.canPlaceOnMap(null, 127.0557))
    }

    /**
     * 0,0은 **유효한 좌표로 본다.**
     *
     * 빨개지는 경우: `lat != 0.0`류의 검사를 넣으면. 기니 만 앞바다가 실제 촬영지일
     * 가능성은 없지만, "값이 0이면 없는 것"이라는 규칙은 적도·본초자오선 위의
     * 정상 좌표를 버린다. 없는 것은 **null로만** 표현한다.
     */
    @Test
    fun 영_좌표도_좌표다() {
        assertTrue(ShareRules.canPlaceOnMap(0.0, 0.0))
    }
}
