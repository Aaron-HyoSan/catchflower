package com.catchflower.app.ui.place

import java.util.Calendar
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 15의 판단 — **틀려도 예쁘게 나오는 두 계산.**
 *
 * ① `이번 주 3개`의 주 시작: 하루만 어긋나도 숫자가 바뀌는데 **화면으로는 못 안다.**
 * ② `!` 배지: 내 도감과 대조하는 것이라 **뒤집혀도** 화면이 정상으로 보인다.
 *    와이어프레임 15 주석 ③이 이 배지를 "지도→촬영 전환의 지점"이라고 적었다 —
 *    뒤집히면 이미 모은 꽃을 찍으러 가게 만든다.
 */
class PlaceRulesTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayOfWeek(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    private fun startOfDayEquals(a: Long, expectedY: Int, expectedM: Int, expectedD: Int) {
        val c = Calendar.getInstance().apply { timeInMillis = a }
        assertEquals(expectedY, c.get(Calendar.YEAR))
        assertEquals(expectedM, c.get(Calendar.MONTH) + 1)
        assertEquals(expectedD, c.get(Calendar.DAY_OF_MONTH))
        assertEquals("자정이 아니다", 0, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals(0, c.get(Calendar.MILLISECOND))
    }

    /** 2026-08-12는 수요일이고, 그 주의 월요일은 8월 10일이다. */
    @Test
    fun 주는_월요일_0시에_시작한다() {
        val wed = at(2026, 8, 12, 14)
        assertEquals("표본이 수요일이 아니다", Calendar.WEDNESDAY, dayOfWeek(wed))
        startOfDayEquals(PlaceRules.weekStart(wed), 2026, 8, 10)
    }

    /**
     * 🔴 **일요일이 이 계산의 함정이다.**
     *
     * 한국 로케일에서 `Calendar.getFirstDayOfWeek()`는 **일요일**이다. 그 기본값으로
     * 계산하면 일요일에 화면을 열었을 때 `이번 주`가 **그날 하루**가 된다 —
     * 토요일에 12개였던 숫자가 일요일에 0~1개가 되고, 사용자는 **기록이 사라졌다**고 읽는다.
     * "주말에 산책하며 찍는다"가 이 앱의 사용 시각이라 주말을 한 주로 묶는다.
     *
     * 2026-08-16은 일요일이고, 그 주의 시작은 **6일 전**인 8월 10일(월)이어야 한다.
     */
    @Test
    fun 일요일에는_주_시작이_엿새_전_월요일이다() {
        val sun = at(2026, 8, 16, 10)
        assertEquals("표본이 일요일이 아니다", Calendar.SUNDAY, dayOfWeek(sun))
        val start = PlaceRules.weekStart(sun)
        startOfDayEquals(start, 2026, 8, 10)
        assertTrue("주 시작이 미래다 — 로케일 기본 첫 요일로 계산했다", start <= sun)
        assertEquals(
            "일요일의 주 시작이 6일 전이 아니다",
            6L,
            TimeUnit.MILLISECONDS.toDays(sun - start - TimeUnit.HOURS.toMillis(10)),
        )
    }

    /** 월요일 0시 정각에 열면 그 시각 자체가 주 시작이다(미래로 밀리지 않는다). */
    @Test
    fun 월요일_자정은_그_자신이_주_시작이다() {
        val mon = at(2026, 8, 10, 0)
        assertEquals("표본이 월요일이 아니다", Calendar.MONDAY, dayOfWeek(mon))
        assertEquals(mon, PlaceRules.weekStart(mon))
    }

    /** 주 시작은 **절대 미래가 아니다.** 요일 7개를 모두 돌려 확인한다. */
    @Test
    fun 어느_요일에도_주_시작이_미래가_아니다() {
        for (day in 10..16) {
            val now = at(2026, 8, day, 9)
            val start = PlaceRules.weekStart(now)
            assertTrue("8월 ${day}일의 주 시작이 미래다", start <= now)
            assertTrue(
                "8월 ${day}일의 주 시작이 7일보다 전이다 — 주가 두 주치가 됐다",
                now - start < TimeUnit.DAYS.toMillis(7),
            )
        }
    }

    // ── 칩 ────────────────────────────────────────────────────────────

    private val names = mapOf(1 to "장미", 2 to "개망초", 3 to "금계국", 4 to "접시꽃")

    /** 와이어프레임 15: `장미 개망초 금계국 접시꽃!` — 내 도감에 없는 것에만 `!`. */
    @Test
    fun 내_도감에_없는_종에만_배지가_붙는다() {
        val chips = PlaceRules.chips(
            flowerIdsRecentFirst = listOf(1, 2, 3, 4),
            nameOf = names::get,
            collectedIds = setOf(1, 2, 3),
        )
        assertEquals(listOf("장미", "개망초", "금계국", "접시꽃"), chips.map { it.name })
        assertEquals(
            listOf(false, false, false, true),
            chips.map { it.missingFromMyDex },
        )
    }

    /**
     * 🔴 **`!`를 이름에 이어 붙이지 않았다.**
     *
     * 문자열로 만들면 화면이 그 글자를 본문과 같은 색·굵기로 그리게 되고,
     * `접시꽃!`이 **꽃 이름처럼 보인다.** 표시 방법은 화면이 정해야 한다.
     */
    @Test
    fun 배지가_이름_문자열에_섞이지_않는다() {
        val chips = PlaceRules.chips(listOf(4), names::get, collectedIds = emptySet())
        assertEquals("접시꽃", chips.single().name)
        assertTrue("이름에 `!`가 섞였다", !chips.single().name.contains("!"))
    }

    /**
     * 🔴 **도감을 읽기 전에는 배지를 붙이지 않는다.**
     *
     * `collectedIds`가 빈 집합인 것과 "아직 안 읽었다"는 다르다. 읽기 전에 그리면
     * **전부 `!`** 가 되고, 그건 200종을 모은 사용자에게 거짓이다.
     * 판단은 호출처가 `markMissing`으로 내린다([PlaceScreen]이 `dexLoaded`를 본다).
     */
    @Test
    fun 도감을_읽기_전에는_전부_배지가_붙지_않는다() {
        val chips = PlaceRules.chips(
            flowerIdsRecentFirst = listOf(1, 2, 3, 4),
            nameOf = names::get,
            // 읽기 전 상태 — 빈 집합이지만 그렸다고 200종 미보유라고 말하면 안 된다.
            collectedIds = emptySet(),
            markMissing = false,
        )
        assertEquals(4, chips.size)
        assertTrue(
            "도감을 읽기 전인데 배지가 붙었다 — 화면에 `!`가 전부 깔린다",
            chips.none { it.missingFromMyDex },
        )
    }

    /**
     * ⚠️ **이름 없는 칩은 뺀다.** 도감에 없는 `flowerId`(새 시드가 안 깔린 기기)는
     *    이름이 null이고, 빈 이름으로 그리면 **누를 수도 없는 빈 칩**이 줄에 남는다.
     */
    @Test
    fun 이름을_못_찾은_종은_칩이_되지_않는다() {
        val chips = PlaceRules.chips(
            flowerIdsRecentFirst = listOf(1, 9999, 3),
            nameOf = names::get,
            collectedIds = emptySet(),
        )
        assertEquals(listOf("장미", "금계국"), chips.map { it.name })
    }

    /** 빈 이름(공백만)도 같은 취급이다 — 화면에서는 빈 칩과 구분되지 않는다. */
    @Test
    fun 공백만_있는_이름도_칩이_되지_않는다() {
        val chips = PlaceRules.chips(
            flowerIdsRecentFirst = listOf(1, 2),
            nameOf = { id -> if (id == 2) "   " else names[id] },
            collectedIds = emptySet(),
        )
        assertEquals(listOf("장미"), chips.map { it.name })
    }

    /** 순서는 **최근 찍은 것부터**다. 뒤집히면 방금 등록한 꽃이 뒤에 숨는다. */
    @Test
    fun 받은_순서를_그대로_유지한다() {
        val chips = PlaceRules.chips(listOf(3, 1, 4, 2), names::get, emptySet())
        assertEquals(listOf(3, 1, 4, 2), chips.map { it.flowerId })
    }
}
