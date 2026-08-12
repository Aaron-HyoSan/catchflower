package com.catchflower.app.ui.place

import java.util.Calendar

/**
 * 화면 15의 판단. **화면 밖에 둔다.**
 *
 * 🔴 **`AndroidViewModel`은 JVM 테스트가 만들 수 없고**(화면 02·07·17에서 세 번 겪었다)
 *    Composable은 에뮬레이터가 필요하다. 그래서 이 화면에서 **틀려도 예쁘게 나오는**
 *    계산을 여기 모은다:
 *    - `이번 주 3개`의 주 시작 — 하루만 어긋나도 숫자가 바뀌는데 화면으로는 못 안다
 *    - `!` 배지 — 내 도감과 대조하는 것이라 **뒤집혀도** 화면이 정상으로 보인다
 *      (와이어프레임 15 주석 ③: 이 배지가 지도→촬영 전환의 지점이다)
 */
object PlaceRules {

    /**
     * `이번 주`의 시작 시각. **월요일 0시**다.
     *
     * ⚠️ **`Calendar`의 기본 첫 요일을 쓰지 않는다.** 한국 로케일에서
     *    [Calendar.getFirstDayOfWeek]는 **일요일**이고, 그러면 일요일에 이 화면을 열면
     *    `이번 주`가 그날 하루가 된다 — 토요일에 12개였던 숫자가 일요일에 0개가 되고
     *    사용자는 **기록이 사라졌다**고 읽는다. 주 경계를 어디로 두든 하나는 어색하지만,
     *    "주말에 산책하며 찍는다"가 이 앱의 사용 시각이라 **주말을 한 주로 묶는** 쪽을
     *    고른다.
     *
     * ⚠️ 밀리초를 7일로 나누지 않는다 — [com.catchflower.app.core.RelativeTime]과 같은 이유다.
     */
    fun weekStart(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        // 월요일까지 되돌린다.
        //
        // ⚠️ **여기서 실제로 일하는 줄은 위의 `firstDayOfWeek`다.** 돌연변이 실측
        //    (2026-08-12): 이 두 줄을 `set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)`로
        //    바꿔도 **테스트가 전부 통과했다** — `firstDayOfWeek`를 월요일로 정해 두면
        //    `set(DAY_OF_WEEK)`도 같은 주 안에서 움직이기 때문이다.
        //    빨개진 것은 `firstDayOfWeek`를 지웠을 때였다(일요일에 주 시작이 **미래**가 됐다).
        //    → 즉 이 형태는 방어가 하나 더 있는 것이고, **`firstDayOfWeek`를 지우면
        //      안 된다**는 것이 실측으로 고정된 사실이다.
        val back = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -back)
    }.timeInMillis

    /**
     * `이곳에서 발견된 꽃` 칩 (A 문서 15번 · 와이어프레임 `장미 개망초 금계국 접시꽃! 나팔꽃!`).
     *
     * @param flowerIdsRecentFirst 최근 찍은 순. [com.catchflower.app.data.PlaceDiscoveries]가 준다.
     * @param nameOf 도감 이름. **못 찾으면 null을 주면 된다** — 그 칩을 뺀다.
     *   ⚠️ 빈 이름으로 칩을 그리면 **누를 수도 없는 빈 칩**이 줄에 남는다.
     * @param collectedIds 내 도감. 여기 없는 종에 `!`가 붙는다(주석 ③).
     *
     * ⚠️ **`collectedIds`가 빈 집합인 것과 "아직 안 읽었다"는 다르다.** 도감을 읽기 전에
     *    그리면 **전부 `!`**가 되고, 그건 200종을 모은 사용자에게 거짓이다. 판단은
     *    호출처가 한다([PlaceScreen]이 `dexLoaded`를 본다) — 여기서는 시킨 대로 붙인다.
     */
    fun chips(
        flowerIdsRecentFirst: List<Int>,
        nameOf: (Int) -> String?,
        collectedIds: Set<Int>,
        markMissing: Boolean = true,
    ): List<Chip> = flowerIdsRecentFirst.mapNotNull { id ->
        val name = nameOf(id)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Chip(
            flowerId = id,
            name = name,
            missingFromMyDex = markMissing && id !in collectedIds,
        )
    }

    /**
     * 칩 하나.
     *
     * ⚠️ **`!`를 [name]에 이어 붙이지 않는다.** 문자열로 만들면 화면이 그 글자를
     *    본문과 같은 색·굵기로 그리게 되고, 그러면 **`접시꽃!`이 꽃 이름처럼 보인다.**
     *    표시 방법은 화면이 정한다(A 문서 15번 범례가 그 기호를 설명한다).
     */
    data class Chip(
        val flowerId: Int,
        val name: String,
        val missingFromMyDex: Boolean,
    )
}
