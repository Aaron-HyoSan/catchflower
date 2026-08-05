package com.catchflower.app.core

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시즌 경계·남은 일수 테스트.
 *
 * **왜 필요한가**: 화면 17이 `47`을 크게 띄운다. 그 숫자가 하루 틀리거나 음수가 되어도
 * 화면은 정상으로 보인다 — 배너 레이아웃은 그대로고 숫자만 거짓말을 한다.
 * 마감 압박을 만드는 숫자(와이어프레임 17 주석 ②)라서 **틀리면 사용자를 속인다.**
 *
 * ⚠️ 모든 케이스에 시각을 **직접 준다.** `System.currentTimeMillis()`를 쓰면
 *    8월에만 통과하는 테스트가 되고, 그건 12월 휴지기 버그를 못 잡는다.
 */
class SeasonClockTest {

    private val kst = TimeZone.getTimeZone("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance(kst).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun status(year: Int, month: Int, day: Int, hour: Int = 12) =
        SeasonClock.status(at(year, month, day, hour), kst)

    @Test
    fun `3월부터 8월까지는 시즌 1이다`() {
        for (month in 3..8) {
            val s = status(2026, month, 15)
            assertEquals("${month}월", 1, s.seasonIndex)
            assertFalse("${month}월", s.dormant)
            // 시즌 1의 마감은 8월 31일이다.
            assertEquals("${month}월", 8, s.endMonth)
            assertEquals("${month}월", 31, s.endDay)
        }
    }

    @Test
    fun `9월부터 11월까지는 시즌 2다`() {
        for (month in 9..11) {
            val s = status(2026, month, 15)
            assertEquals("${month}월", 2, s.seasonIndex)
            assertFalse("${month}월", s.dormant)
            // 11월 30일 마감. 30일인 달을 31일로 세면 하루가 늘어난다.
            assertEquals("${month}월", 11, s.endMonth)
            assertEquals("${month}월", 30, s.endDay)
        }
    }

    @Test
    fun `12월과 1·2월은 휴지기이고 다음 시즌 시작을 센다`() {
        for (month in listOf(12, 1, 2)) {
            val s = status(2026, month, 15)
            assertTrue("${month}월", s.dormant)
            assertEquals("${month}월", 3, s.endMonth)
            assertEquals("${month}월", 1, s.endDay)
            // ⚠️ 남은 일수가 **양수**여야 한다. 12월에 해를 안 넘기면 여기서 음수가 난다.
            assertTrue("${month}월 남은 일수=${s.daysLeft}", s.daysLeft > 0)
        }
    }

    @Test
    fun `12월 휴지기는 다음 해 3월을 가리킨다`() {
        val s = status(2026, 12, 15)
        // ⚠️ 이 한 줄이 `-9일 남음` 버그를 잡는다. 2026-12-15 → 2027-03-01.
        assertEquals(2027, s.year)
        assertEquals("2027 시즌 1", s.title)
        // 12/15 → 3/1: 12월 16일 + 1월 31 + 2월 28 + 3월 1일 = 76일.
        assertEquals(76, s.daysLeft)
    }

    @Test
    fun `1월 휴지기는 같은 해 3월을 가리킨다`() {
        val s = status(2027, 1, 15)
        assertEquals(2027, s.year)
        // 1/15 → 3/1: 연중일수로 60 - 15 = 45일.
        // (1월 남은 16 + 2월 28 = 44로 세면 3월 1일 자체를 빠뜨린다.)
        assertEquals(45, s.daysLeft)
    }

    @Test
    fun `마감일 당일은 1일 남음이다`() {
        // ⚠️ 밀리초 나눗셈이면 0이 나온다. 마감이 오늘이라도 **아직 하루 남았다.**
        assertEquals(1, status(2026, 8, 31).daysLeft)
        assertEquals(1, status(2026, 11, 30).daysLeft)
    }

    @Test
    fun `마감일 밤 11시에도 1일 남음이다`() {
        // 시:분을 버리고 날짜 경계로 세는지 확인한다. 23시에 0이 되면
        // "마감일 저녁에 이미 끝난 것처럼" 보인다.
        assertEquals(1, status(2026, 8, 31, hour = 23).daysLeft)
        assertEquals(1, status(2026, 8, 31, hour = 0).daysLeft)
    }

    @Test
    fun `마감 하루 전은 2일 남음이다`() {
        assertEquals(2, status(2026, 8, 30).daysLeft)
    }

    @Test
    fun `A 문서의 47일 남음이 나오는 날짜가 있다`() {
        // A 문서 화면 17 표기 `9월 30일 마감 · 47일 남음`.
        // ⚠️ 우리 시즌 경계는 **11월 30일 마감**이라(오너 확정 B-1) 9월 30일이 아니다.
        //    A 문서 숫자는 시즌 경계 확정 **전**에 쓰인 예시다. 여기서는 `47`이라는
        //    숫자가 계산으로 재현되는지만 확인한다 — 11/30에서 46일 뺀 10/15.
        assertEquals(47, status(2026, 10, 15).daysLeft)
    }

    @Test
    fun `윤년 2월도 하루씩 밀리지 않는다`() {
        // 2028은 윤년(2월 29일). 2/15 → 3/1은 15일이다(윤년이 아니면 14일).
        assertEquals(15, SeasonClock.status(at(2028, 2, 15), kst).daysLeft)
        assertEquals(14, SeasonClock.status(at(2027, 2, 15), kst).daysLeft)
    }

    @Test
    fun `서머타임이 있는 지역에서도 하루가 사라지지 않는다`() {
        // ⚠️ 밀리초 나눗셈의 고전적 실패 — DST 전환일이 끼면 23시간·25시간 하루가 생긴다.
        //    한국은 DST가 없지만 코드가 TimeZone을 받으므로 검증해 둔다.
        val ny = TimeZone.getTimeZone("America/New_York")
        val mar1 = Calendar.getInstance(ny).apply { clear(); set(2026, 2, 1, 12, 0, 0) }
        val s = SeasonClock.status(mar1.timeInMillis, ny)
        // 3/1 → 8/31. 3월 DST 시작(3/8)이 구간 안에 있다.
        // 3월 30 + 4월 30 + 5월 31 + 6월 30 + 7월 31 + 8월 31 = 183, +1(당일 포함) = 184.
        assertEquals(184, s.daysLeft)
    }

    @Test
    fun `배너 문장은 시즌 중과 휴지기가 다르다`() {
        val inSeason = status(2026, 10, 15)
        assertEquals("11월 30일 마감 · 47일 남음", SeasonClock.deadlineLabel(inSeason))

        val dormant = status(2027, 1, 15)
        // 휴지기에 `마감`이라고 쓰면 "이미 하는 중"으로 읽힌다.
        assertEquals("3월 1일 시작 · 45일 남음", SeasonClock.deadlineLabel(dormant))
    }

    @Test
    fun `시즌 경계는 GamePolicy가 원본이다`() {
        // 여기 숫자를 복사해 두지 않았음을 고정한다. 정책이 바뀌면 이 테스트가 먼저 깨진다.
        assertEquals(2, GamePolicy.seasonWindows.size)
        assertEquals(setOf(12, 1, 2), GamePolicy.dormantMonths)
        // 정책의 모든 달이 시즌 또는 휴지기 중 정확히 한 쪽에 속한다 — 빈 달이 없다.
        for (month in 1..12) {
            val inSeason = GamePolicy.seasonWindows.count { it.contains(month) }
            val isDormant = month in GamePolicy.dormantMonths
            assertEquals(
                "${month}월이 어느 쪽에도 없거나 양쪽에 있다",
                1,
                inSeason + if (isDormant) 1 else 0,
            )
        }
    }
}
