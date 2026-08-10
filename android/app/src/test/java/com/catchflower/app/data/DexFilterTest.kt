package com.catchflower.app.data

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 필터 조합 규칙 테스트.
 *
 * **왜 테스트가 필요한가**: 화면 06의 CTA는 `42종 보기`처럼 개수를 보여준다.
 * 그룹끼리 AND여야 할 걸 OR로 짜면 개수가 커지기만 하고 **화면상으로는 그럴듯해 보인다**.
 * 조용히 틀리는 종류라 눈으로는 못 잡는다.
 */
class DexFilterTest {

    private fun flower(
        id: Int,
        season: Season = Season.SPRING,
        color: String = "노랑",
        rarity: Rarity = Rarity.COMMON,
        bloomMonths: List<Int> = listOf(3, 4),
    ) = Flower(
        id = id,
        name = "꽃$id",
        scientificName = "Test $id",
        family = "테스트과",
        bloomMonths = bloomMonths,
        bloomLabel = "3~4월",
        bloomSource = BloomSource.HUMAN,
        season = season,
        color = color,
        rarity = rarity,
        habitat = "테스트",
        aiDifficulty = AiDifficulty.LOW,
        similarFlowerIds = emptyList(),
        similarFlowerNames = emptyList(),
        illustBatch = 1,
    )

    private val sample = listOf(
        flower(1, season = Season.SPRING, color = "노랑", rarity = Rarity.COMMON),
        flower(2, season = Season.SPRING, color = "분홍", rarity = Rarity.NORMAL),
        flower(3, season = Season.SUMMER, color = "노랑", rarity = Rarity.RARE),
        flower(4, season = Season.WINTER, color = "파랑", rarity = Rarity.COMMON),
    )

    private val collected = setOf(1, 3)

    @Test
    fun `조건이 없으면 전부 통과한다`() {
        val result = DexFilter().apply(sample, collected)
        assertEquals(4, result.size)
    }

    @Test
    fun `그룹 안은 OR다`() {
        // 봄 OR 여름 → 1, 2, 3
        val filter = DexFilter(seasons = setOf(Season.SPRING, Season.SUMMER))
        assertEquals(listOf(1, 2, 3), filter.apply(sample, collected).map { it.id })
    }

    @Test
    fun `그룹끼리는 AND다`() {
        // (봄 OR 여름) AND 노랑 → 1, 3
        // ⚠️ OR로 짜면 4개가 나온다. 그게 이 테스트가 잡으려는 실수다.
        val filter = DexFilter(
            seasons = setOf(Season.SPRING, Season.SUMMER),
            colors = setOf("노랑"),
        )
        assertEquals(listOf(1, 3), filter.apply(sample, collected).map { it.id })
    }

    @Test
    fun `수집 여부는 다른 그룹과도 AND다`() {
        // 모은 꽃 AND 봄 → 1만 (3은 모았지만 여름)
        val filter = DexFilter(
            collectState = CollectState.COLLECTED,
            seasons = setOf(Season.SPRING),
        )
        assertEquals(listOf(1), filter.apply(sample, collected).map { it.id })
    }

    @Test
    fun `미발견은 수집 목록의 여집합이다`() {
        val filter = DexFilter(collectState = CollectState.NOT_COLLECTED)
        assertEquals(listOf(2, 4), filter.apply(sample, collected).map { it.id })
    }

    @Test
    fun `조합이 공집합이면 빈 목록이다`() {
        // 겨울 AND 노랑 → 없음. 화면 06의 Disabled CTA 경로다.
        val filter = DexFilter(seasons = setOf(Season.WINTER), colors = setOf("노랑"))
        assertTrue(filter.apply(sample, collected).isEmpty())
    }

    @Test
    fun `칩 토글은 두 번 누르면 해제된다`() {
        val once = DexFilter().toggleSeason(Season.SPRING)
        assertEquals(setOf(Season.SPRING), once.seasons)
        val twice = once.toggleSeason(Season.SPRING)
        assertTrue(twice.seasons.isEmpty())
        assertTrue(twice.isEmpty)
    }

    @Test
    fun `수집여부만 전체가 아니어도 isEmpty가 아니다`() {
        assertFalse(DexFilter(collectState = CollectState.COLLECTED).isEmpty)
    }

    /**
     * 색상 칩이 마스터 데이터의 색을 전부 덮지 못한다는 **알려진 격차**를 고정한다.
     * 이 테스트가 깨지면 (a) 칩이 늘었거나 (b) 데이터 색이 바뀐 것이고,
     * 둘 다 오너 결정이 내려졌다는 뜻이므로 이 주석과 함께 갱신해야 한다.
     */
    @Test
    fun `색상 칩은 6개이고 주황·기타는 포함하지 않는다`() {
        assertEquals(6, DexFilter.COLOR_CHIPS.size)
        assertFalse("주황" in DexFilter.COLOR_CHIPS)
        assertFalse("기타" in DexFilter.COLOR_CHIPS)
    }
}
