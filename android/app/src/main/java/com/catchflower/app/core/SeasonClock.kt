package com.catchflower.app.core

import java.util.Calendar
import java.util.TimeZone

/**
 * 시즌 경계와 남은 일수. 화면 17 시즌 배너 · 화면 21 시즌 결과의 근거다.
 *
 * **왜 따로 두는가**: 화면 17이 크게 보여주는 `47일 남음`은 **틀려도 화면에는
 * 정상으로 보인다.** 마감 압박을 만드는 숫자(와이어프레임 주석 ②)라서
 * 틀리면 사용자를 재촉하거나 반대로 안심시킨다. 눈으로는 못 잡는다.
 *
 * ⚠️ **밀리초 나눗셈으로 세지 않는다.** 도감의 `오늘/어제`에서 이미 밟은 함정이다 —
 *    9월 30일 23시에 남은 일수를 물으면 나눗셈으로는 `0일`이지만 마감은 아직 오늘이다.
 *    **날짜 경계**로 센다.
 *
 * ⚠️ 시즌 경계 숫자는 [GamePolicy.seasonWindows]가 원본이다. 여기서 복사하지 않는다 —
 *    오너 미확정(B-1 ★)이라 답이 오면 한 곳만 고쳐야 한다.
 */
object SeasonClock {

    /**
     * 지금이 어느 시즌인가.
     *
     * @param dormant 휴지기(12~2월)면 `true`. 이때 랭킹 대신 "다음 시즌"을 보여준다 —
     *   겨울 개화종이 6종뿐이라 순위를 매기면 사실상 아무 일도 일어나지 않는다.
     */
    data class Status(
        val year: Int,
        val seasonIndex: Int,
        val dormant: Boolean,
        /** 마감일(포함). 휴지기면 다음 시즌 **시작일**이다. */
        val endMillis: Long,
        val endMonth: Int,
        val endDay: Int,
        /** 오늘부터 마감일까지. 마감일 당일은 `0`이 아니라 `1`이다 (아직 하루 남았다). */
        val daysLeft: Int,
    ) {
        /** 화면 17 배너 `2026 시즌 2`. */
        val title: String get() = "$year 시즌 $seasonIndex"
    }

    /**
     * @param now 밀리초. 테스트가 시각을 고정할 수 있어야 하므로 인자로 받는다 —
     *   `Calendar.getInstance()`를 안에서 부르면 8월에만 통과하는 테스트가 된다.
     */
    fun status(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Status {
        val cal = calendarAt(now, timeZone)
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        val current = GamePolicy.seasonWindows.firstOrNull { it.contains(month) }
        if (current != null) {
            // 마감일 = 종료월의 말일. 9~11월 시즌이면 11월 30일이다.
            val end = lastDayOf(year, current.endMonth, timeZone)
            return Status(
                year = year,
                seasonIndex = current.index,
                dormant = false,
                endMillis = end.timeInMillis,
                endMonth = current.endMonth,
                endDay = end.get(Calendar.DAY_OF_MONTH),
                daysLeft = daysBetween(now, end.timeInMillis, timeZone) + 1,
            )
        }

        // 휴지기 — 다음 시즌 시작일까지 센다.
        // ⚠️ 12월이면 **다음 해** 3월이다. 해를 안 넘기면 `-9일 남음`이 나온다.
        val next = GamePolicy.seasonWindows.first()
        val nextYear = if (month >= 12) year + 1 else year
        val start = firstDayOf(nextYear, next.startMonth, timeZone)
        return Status(
            year = nextYear,
            seasonIndex = next.index,
            dormant = true,
            endMillis = start.timeInMillis,
            endMonth = next.startMonth,
            endDay = 1,
            daysLeft = daysBetween(now, start.timeInMillis, timeZone),
        )
    }

    /** 화면 17 `9월 30일 마감 · 47일 남음`. */
    fun deadlineLabel(status: Status): String =
        if (status.dormant) {
            "${status.endMonth}월 ${status.endDay}일 시작 · ${status.daysLeft}일 남음"
        } else {
            "${status.endMonth}월 ${status.endDay}일 마감 · ${status.daysLeft}일 남음"
        }

    /**
     * 두 시각 사이의 **날짜 수**. 시:분:초를 버리고 자정 기준으로 센다.
     *
     * 서머타임·윤년 때문에 `(b - a) / 86_400_000`은 하루씩 틀린다.
     * 자정으로 내린 뒤 `DAY_OF_YEAR`를 하루씩 올려 세는 대신 밀리초 차를 쓰되,
     * **양쪽을 먼저 자정으로 맞춰** 오차가 12시간 미만이 되게 하고 반올림한다.
     */
    private fun daysBetween(from: Long, to: Long, timeZone: TimeZone): Int {
        val a = startOfDay(from, timeZone)
        val b = startOfDay(to, timeZone)
        val diff = (b - a).toDouble() / 86_400_000.0
        return Math.round(diff).toInt()
    }

    private fun startOfDay(millis: Long, timeZone: TimeZone): Long =
        calendarAt(millis, timeZone).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calendarAt(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }

    private fun lastDayOf(year: Int, month: Int, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

    private fun firstDayOf(year: Int, month: Int, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, 1)
        }
}
