package com.catchflower.app.data

import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower

/**
 * 도감 필터 (화면 06).
 *
 * 그룹 4개는 A 문서가 확정한 것이다:
 * - 수집 여부 — 전체 / 모은 꽃 / 미발견
 * - 계절 — 봄 / 여름 / 가을 / 겨울
 * - 색상 — 흰색 / 노랑 / 분홍 / 붉은색 / 보라 / 파랑
 * - 보기 쉬움 — 흔함 / 보통 / 귀함
 *
 * 그룹 **안**은 OR, 그룹 **끼리는** AND다. 와이어프레임에서 계절 `봄` + 색상 `노랑`이
 * 동시에 선택돼 `42종 보기`가 나오는데, 그건 교집합일 때만 42가 나온다.
 */
enum class CollectState(val label: String) {
    ALL("전체"),
    COLLECTED("모은 꽃"),
    NOT_COLLECTED("미발견"),
}

data class DexFilter(
    val collectState: CollectState = CollectState.ALL,
    val seasons: Set<Season> = emptySet(),
    val colors: Set<String> = emptySet(),
    val rarities: Set<Rarity> = emptySet(),
) {
    val isEmpty: Boolean
        get() = collectState == CollectState.ALL &&
            seasons.isEmpty() && colors.isEmpty() && rarities.isEmpty()

    /** 화면 04 상단 칩 줄에 보여줄 요약. 조건이 없으면 `전체`. */
    fun toggleSeason(season: Season) =
        copy(seasons = seasons.toggle(season))

    fun toggleColor(color: String) = copy(colors = colors.toggle(color))

    fun toggleRarity(rarity: Rarity) = copy(rarities = rarities.toggle(rarity))

    /**
     * 조건 적용.
     *
     * @param collectedIds 발견한 flower id. 서버가 붙기 전에는 더미다.
     */
    fun apply(flowers: List<Flower>, collectedIds: Set<Int>): List<Flower> =
        flowers.filter { flower ->
            val collectOk = when (collectState) {
                CollectState.ALL -> true
                CollectState.COLLECTED -> flower.id in collectedIds
                CollectState.NOT_COLLECTED -> flower.id !in collectedIds
            }
            // 🔴 `season`이 null인 278종은 **어떤 계절 칩에도 걸리지 않는다** (계약 1-1-d).
            //    그게 맞다 — 근거 없는 계절을 찍어 넣으면 `봄 꽃 보기`에 여름 꽃이 섞인다.
            //    `flower.season in seasons`로도 같은 답이 나오지만, null이 의도인지
            //    실수인지 읽는 사람이 알 수 없어서 명시한다.
            val seasonOk = seasons.isEmpty() || flower.season?.let { it in seasons } == true
            collectOk && seasonOk &&
                (colors.isEmpty() || flower.color in colors) &&
                (rarities.isEmpty() || flower.rarity in rarities)
        }

    companion object {
        /**
         * 색상 칩 6개. **A 문서 화면 06이 확정한 목록 그대로다.**
         *
         * ⚠️ 마스터 데이터에는 색이 8종 있다 — `주황` 6종, `기타` 13종이 더 있다.
         *    즉 **19종은 색상 필터로 도달할 수 없다.** 칩을 임의로 늘리는 건
         *    문구 신설이라 금지되어 있으므로(A 문서) 오너 확인 항목으로 남긴다.
         *    선택지는 (a) 칩 2개 추가 (b) 19종을 6색으로 재분류 (c) 그대로 둔다.
         */
        val COLOR_CHIPS = listOf("흰색", "노랑", "분홍", "붉은색", "보라", "파랑")

        /** 화면 04 상단 빠른 필터 칩 — `전체 / 모은 꽃 / 봄 / 여름 / 가을`. */
        val QUICK_CHIPS = listOf("전체", "모은 꽃", "봄", "여름", "가을")
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value
