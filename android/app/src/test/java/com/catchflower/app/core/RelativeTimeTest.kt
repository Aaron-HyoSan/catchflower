package com.catchflower.app.core

import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RelativeTime]이 **날짜 경계로 세는가.**
 *
 * ## 왜 이 파일이 필요했나
 *
 * 🔴 [RelativeTime]의 KDoc이 처음부터 이 테스트 이름을 적어 뒀는데 **파일이 없었다.**
 *    주석이 "테스트가 잰다"고 말하고 실제로는 아무도 안 재는 상태 — `초록 테스트도
 *    증거가 아니다`의 ⑨·⑪(내 주석이 금지어를 승인했다)과 같은 종류다.
 *    주석이 증거로 읽히는 것이 더 나쁘다.
 *
 * ## 무엇이 틀려도 예쁘게 나오나
 *
 * `어제`라고 쓰여 있으면 아무도 의심하지 않는다. 밀리초를 24시간으로 나누는 구현은
 * **대부분의 시각에 맞는 답을 준다** — 자정을 갓 넘긴 몇 시간에만 틀리고, 그때
 * 화면은 완벽히 정상으로 보인다. 그래서 시각을 고정해 그 창을 직접 겨냥한다.
 */
class RelativeTimeTest {

    /** 2026-08-12(수) 01:00 로컬. 자정을 갓 넘긴 시각을 기준으로 쓴다. */
    private val wedAt1am: Long = at(2026, 8, 12, 1, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * 🔴 **이 화면의 그 결함이다.** 어제 23시에 찍은 것을 오늘 1시에 보면 두 시간
     *    차이라서 `2시간 전`이 아니라 `어제`여야 하는가?
     *
     * → [RelativeTime.detailed]는 **`2시간 전`이 맞다**(A 문서 16번이 시·분을 쓴다).
     *   [RelativeTime.day]는 **`어제`가 맞다**(화면 04는 하루 단위 목록이다).
     *   두 함수가 갈리는 지점이라 둘을 같이 잰다 — 한 함수로 합치면 한쪽 화면의
     *   문구가 조용히 바뀐다.
     */
    @Test
    fun 자정을_넘긴_두_시간은_detailed에서_시간이고_day에서_어제다() {
        val yesterday23 = at(2026, 8, 11, 23, 0)
        assertEquals("2시간 전", RelativeTime.detailed(yesterday23, wedAt1am))
        assertEquals("어제", RelativeTime.day(yesterday23, wedAt1am))
    }

    /**
     * 🔴 **밀리초를 24시간으로 나누면 `오늘`이 된다.** 어제 23시와 오늘 1시는 2시간
     *    차이라서 `toDays()`가 0을 준다 — 날짜는 바뀌었는데 어제라고 말하지 않는다.
     *    이 단정이 그 구현을 직접 죽인다.
     */
    @Test
    fun 날짜_경계로_센다_시각차이가_아니다() {
        val yesterday23 = at(2026, 8, 11, 23, 0)
        val elapsed = wedAt1am - yesterday23
        // 전제: 실제 경과 시간은 하루가 안 된다. 그래서 나눗셈 구현은 0일을 준다.
        assertEquals(
            "전제가 깨졌다 — 표본 두 시각의 간격이 24시간을 넘는다",
            0L,
            TimeUnit.MILLISECONDS.toDays(elapsed),
        )
        assertEquals(
            "날짜 경계로 세지 않았다 — `어제`가 `오늘`로 나온다",
            1L,
            RelativeTime.daysBetween(yesterday23, wedAt1am),
        )
    }

    /**
     * `1분 전` → `{N}분 전` → `{N}시간 전` → `어제` → `{N}일 전` → `M월 D일`.
     *
     * ⚠️ 경계를 하나씩 짚는다. 59분·60분·23시간·24시간에서 문구가 바뀌는 지점이
     *    어긋나면 `60분 전`이나 `24시간 전`처럼 **틀리지 않았는데 이상한 말**이 나온다.
     */
    @Test
    fun detailed의_단계가_경계에서_바뀐다() {
        val now = wedAt1am
        fun minutesAgo(n: Long) = now - TimeUnit.MINUTES.toMillis(n)
        assertEquals("40분 전", RelativeTime.detailed(minutesAgo(40), now))
        assertEquals("59분 전", RelativeTime.detailed(minutesAgo(59), now))
        assertEquals("1시간 전", RelativeTime.detailed(minutesAgo(60), now))
        assertEquals("23시간 전", RelativeTime.detailed(minutesAgo(23 * 60), now))
        // 24시간을 지나면 하루 단위로 넘어간다. 8/11 01:00은 하루 전이라 `어제`다.
        assertEquals("어제", RelativeTime.detailed(minutesAgo(24 * 60), now))
    }

    /**
     * 🔴 **`방금`을 만들지 않았다.** 1분 미만에 쓸 문구가 A 문서에 없어서
     *    [RelativeTime]은 `1분 전`으로 올린다.
     *
     * 이 단정이 지키는 것: 누군가 `방금`을 지어내면 **톤 검토를 안 지난 말**이
     * 화면에 남는다(A 문서 3절 `탈퇴한 사용자예요`가 그렇게 들어왔고 지금도 검토 대상이다).
     */
    @Test
    fun 일분_미만도_승인된_문구로_말한다() {
        val fiveSecondsAgo = wedAt1am - TimeUnit.SECONDS.toMillis(5)
        assertEquals("1분 전", RelativeTime.detailed(fiveSecondsAgo, wedAt1am))
        assertEquals("1분 전", RelativeTime.detailed(wedAt1am, wedAt1am))
    }

    /**
     * 🔴 **미래 시각이 `-1분 전`이 되지 않는다.**
     *
     * 서버 `created_at`은 서버 시계이고 화면은 기기 시계로 잰다 — 기기가 몇 초 느리면
     * **방금 쓴 댓글이 미래**다. 그때 음수를 그리면 화면에 `-1분 전`이 뜬다.
     */
    @Test
    fun 미래_시각을_음수로_말하지_않는다() {
        val future = wedAt1am + TimeUnit.MINUTES.toMillis(3)
        val text = RelativeTime.detailed(future, wedAt1am)
        assertEquals("1분 전", text)
        assertTrue("음수가 화면 문구에 들어갔다: $text", !text.contains("-"))
    }

    /** 7일이 지나면 날짜로 말한다. 상대 시각이 계속 커지면 `340일 전`이 된다. */
    @Test
    fun 일주일이_지나면_날짜로_말한다() {
        assertEquals("3일 전", RelativeTime.day(at(2026, 8, 9, 12, 0), wedAt1am))
        assertEquals("6일 전", RelativeTime.day(at(2026, 8, 6, 12, 0), wedAt1am))
        assertEquals("8월 5일", RelativeTime.day(at(2026, 8, 5, 12, 0), wedAt1am))
        assertEquals("1월 3일", RelativeTime.day(at(2026, 1, 3, 12, 0), wedAt1am))
    }
}
