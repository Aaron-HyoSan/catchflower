package com.catchflower.app.core

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 상대 시각 표기. 화면 04(`오늘`·`3일 전`)와 화면 15·16(`2시간 전`·`40분 전`)이 쓴다.
 *
 * ## 왜 순수 객체인가
 *
 * 🔴 **밀리초를 24시간으로 나누면 안 된다.** 어제 23시에 찍은 것을 오늘 1시에 보면
 *    두 시간 차이라서 `오늘`이 된다 — 날짜가 바뀌었는데 어제라고 말하지 않는다.
 *    그래서 **날짜 경계**로 센다([daysBetween]). 이 계산은 **화면에서 예쁘게 틀린다** —
 *    `어제`라고 쓰여 있으면 아무도 의심하지 않는다. 그래서 여기 있고 JVM 테스트가 잰다
 *    (`RelativeTimeTest`).
 *
 * ## 문구는 새로 쓴 것이 아니다
 *
 * A 문서가 쓴 모양만 만든다:
 * - 화면 16 작성자 줄 `꽃보다효산 / 연남동 · 2시간 전` (2절 16번 표)
 * - 와이어프레임 16 댓글 `1시간 전` · `40분 전` · `20분 전`
 * - 화면 04 최근 발견 `오늘` · `어제` · `3일 전` · `M월 D일` (와이어프레임 04)
 *
 * ⚠️ **`방금`을 만들지 않았다.** 1분 미만에 쓸 문구가 A 문서에 없고, 지어내면
 *    톤 검토를 안 지난 말이 화면에 남는다(A 문서 3절 `탈퇴한 사용자예요`가 그렇게
 *    들어왔고 지금도 오너 검토 대상이다). 대신 [MIN_MINUTES]로 올려 `1분 전`이라고
 *    말한다 — 55초를 반올림하는 것이라 승인된 모양 안에 있다.
 *
 * ⚠️ **미래 시각을 음수로 계산하지 않는다.** 서버 `created_at`은 서버 시계이고 화면은
 *    기기 시계로 잰다 — 기기가 몇 초 느리면 방금 쓴 댓글이 **`-1분 전`**이 된다
 *    (`ReactionService.now` 주석의 그 문제와 같은 뿌리다). 0으로 깎는다.
 */
object RelativeTime {

    /** 1분 미만도 이 값으로 말한다. 위 주석의 이유. */
    private const val MIN_MINUTES = 1L

    /**
     * 화면 15·16. **시·분까지 말한다.**
     *
     * `1분 전` → `{N}분 전`(60분 미만) → `{N}시간 전`(24시간 미만) → `어제` →
     * `{N}일 전`(7일 미만) → `M월 D일`.
     *
     * ⚠️ 24시간 미만을 `오늘`로 말하지 않는다 — A 문서 16번 표가 `2시간 전`이다.
     *    같은 값을 화면 04는 `오늘`이라고 쓰는데(그 화면은 하루 단위 목록이다)
     *    **그건 [day]다.** 두 모양을 한 함수로 합치면 한쪽 화면의 문구가 조용히 바뀐다.
     */
    fun detailed(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = (now - timestamp).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        if (minutes < 60L) return "${minutes.coerceAtLeast(MIN_MINUTES)}분 전"
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        if (hours < 24L) return "${hours}시간 전"
        return day(timestamp, now)
    }

    /**
     * 화면 04. **하루 단위**로만 말한다.
     *
     * ⚠️ `오늘`이 나올 수 있다 — [detailed]에서 여기로 넘어오는 경로는 24시간이
     *    지난 뒤라서 `오늘`이 나오지 않지만, 화면 04는 직접 이 함수를 부른다.
     */
    fun day(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysBetween(timestamp, now)
        return when {
            days <= 0L -> "오늘"
            days == 1L -> "어제"
            days < 7L -> "${days}일 전"
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                "${cal.get(Calendar.MONTH) + 1}월 ${cal.get(Calendar.DAY_OF_MONTH)}일"
            }
        }
    }

    /**
     * **날짜 경계**로 센 일수. 시각 차이가 아니다.
     *
     * ⚠️ `TimeUnit.MILLISECONDS.toDays(to - from)`로는 안 된다 — 위 클래스 주석.
     */
    internal fun daysBetween(from: Long, to: Long): Long =
        TimeUnit.MILLISECONDS.toDays(startOfDay(to) - startOfDay(from))

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
