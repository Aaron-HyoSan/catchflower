package com.catchflower.app.ui.dex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.CollectState
import com.catchflower.app.data.DexFilter
import com.catchflower.app.data.DummyDiscoveries
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/**
 * 도감 화면 상태 (화면 04·05·06·22 공용).
 *
 * 서버가 없으므로 발견 기록은 [DummyDiscoveries]에서 온다.
 * 서버가 붙으면 그 부분만 갈아 끼운다 — 화면은 이 ViewModel만 본다.
 */
class DexViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FlowerRepository.get(app)

    /** 지금. 상대 날짜(`오늘`, `3일 전`) 계산의 기준이다. */
    private val now: Long = System.currentTimeMillis()

    /** 현재 월. 개화월 하드 필터와 화면 22 제철 추천의 입력이다. */
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    val allFlowers: List<Flower> = repository.flowers

    var filter by mutableStateOf(DexFilter())
        private set

    /**
     * 빈 상태(화면 22) 확인용 토글.
     * 0종 화면은 가입 직후에만 보이므로, 없으면 개발 중에 한 번도 못 본다.
     */
    var forceEmptyState by mutableStateOf(false)
        private set

    val collectedIds: Set<Int>
        get() = if (forceEmptyState) emptySet() else DummyDiscoveries.collectedIds

    val collectedCount: Int get() = collectedIds.size

    val thisSeasonCount: Int
        get() = if (forceEmptyState) 0 else DummyDiscoveries.thisSeasonIds.size

    /** `도감 18% 완성` — 소수점 버린 정수 퍼센트. */
    val completionPercent: Int
        get() = collectedCount * 100 / GamePolicy.TOTAL_FLOWER_COUNT

    val progress: Float
        get() = collectedCount.toFloat() / GamePolicy.TOTAL_FLOWER_COUNT

    /**
     * `다음 배지까지 3종`.
     *
     * ⚠️ 배지 단계는 아직 오너 미확정이다. 10종 단위로 가정해 둔다 —
     *    확정되면 이 값도 GamePolicy로 옮긴다.
     */
    val toNextBadge: Int
        get() = BADGE_STEP - (collectedCount % BADGE_STEP)

    val recentDiscoveries: List<Discovery>
        get() = if (forceEmptyState) emptyList() else DummyDiscoveries.recent(now)

    /** 필터가 적용된 그리드. 화면 04의 3열 그리드가 이걸 그린다. */
    val visibleFlowers: List<Flower>
        get() = filter.apply(allFlowers, collectedIds)

    /**
     * 화면 22 `처음이라면 이 꽃부터` 4종.
     *
     * 와이어프레임 주석의 조건: **현재 월 + 흔함(common)**.
     * 지역 기준은 아직 데이터가 없어 못 넣는다 (동네별 출현 데이터는 미수집).
     */
    val starterFlowers: List<Flower>
        get() = repository.bloomingIn(currentMonth)
            .filter { it.rarity == Rarity.COMMON }
            .take(4)
            .ifEmpty {
                // 12·1·2월은 흔함이 4종 안 될 수 있다 (겨울 개화 6종뿐).
                // 빈 섹션을 보여주는 것보다 흔함 전체에서 채우는 게 낫다.
                repository.byRarity(Rarity.COMMON).take(4)
            }

    fun discoveriesFor(flowerId: Int): List<Discovery> =
        if (forceEmptyState) emptyList()
        else DummyDiscoveries.forFlower(flowerId, now).sortedByDescending { it.createdAt }

    fun flower(id: Int): Flower? = repository.byId(id)

    fun similarTo(flower: Flower): List<Flower> = repository.similarTo(flower)

    // --- 필터 조작 ---

    fun setCollectState(state: CollectState) {
        filter = filter.copy(collectState = state)
    }

    fun toggleSeason(season: Season) {
        filter = filter.toggleSeason(season)
    }

    fun toggleColor(color: String) {
        filter = filter.toggleColor(color)
    }

    fun toggleRarity(rarity: Rarity) {
        filter = filter.toggleRarity(rarity)
    }

    /** 화면 06 `초기화`. */
    fun resetFilter() {
        filter = DexFilter()
    }

    fun applyQuickChip(chip: String) {
        filter = when (chip) {
            "전체" -> DexFilter()
            "모은 꽃" -> DexFilter(collectState = CollectState.COLLECTED)
            "봄" -> DexFilter(seasons = setOf(Season.SPRING))
            "여름" -> DexFilter(seasons = setOf(Season.SUMMER))
            "가을" -> DexFilter(seasons = setOf(Season.AUTUMN))
            else -> filter
        }
    }

    /** 화면 04 상단 칩 줄에서 어느 칩이 눌린 상태인지. */
    fun activeQuickChip(): String = when {
        filter.isEmpty -> "전체"
        filter.collectState == CollectState.COLLECTED &&
            filter.seasons.isEmpty() && filter.colors.isEmpty() && filter.rarities.isEmpty() -> "모은 꽃"
        filter.seasons == setOf(Season.SPRING) &&
            filter.collectState == CollectState.ALL &&
            filter.colors.isEmpty() && filter.rarities.isEmpty() -> "봄"
        filter.seasons == setOf(Season.SUMMER) &&
            filter.collectState == CollectState.ALL &&
            filter.colors.isEmpty() && filter.rarities.isEmpty() -> "여름"
        filter.seasons == setOf(Season.AUTUMN) &&
            filter.collectState == CollectState.ALL &&
            filter.colors.isEmpty() && filter.rarities.isEmpty() -> "가을"
        else -> "" // 시트에서 복합 조건을 걸면 어떤 칩도 선택되지 않는다
    }

    fun toggleEmptyStatePreview() {
        forceEmptyState = !forceEmptyState
    }

    private companion object {
        const val BADGE_STEP = 10
    }
}
