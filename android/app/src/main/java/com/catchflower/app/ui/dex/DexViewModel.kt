package com.catchflower.app.ui.dex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.CollectState
import com.catchflower.app.data.DexFilter
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.DiscoveryRules
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * 도감 화면 상태 (화면 04·05·06·22 공용).
 *
 * **발견 기록은 [DiscoveryRepository]에서 온다** — 기기에 저장된 실제 기록이다.
 * 촬영 흐름도 같은 저장소에 쓰므로, 등록한 꽃이 바로 도감에 나타난다.
 *
 * ⚠️ 숫자 계산은 [DiscoveryRules]가 한다. 여기서 다시 세지 않는다 —
 *    `모은 꽃 37 / 200종`은 **틀려도 화면에는 예쁘게 나오는** 종류라서
 *    JVM 테스트가 있는 순수 함수에 둔다.
 */
class DexViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = FlowerRepository.get(app)
    private val discoveries = DiscoveryRepository.get(app)

    /** 지금. 상대 날짜(`오늘`, `3일 전`) 계산의 기준이다. */
    private val now: Long = System.currentTimeMillis()

    /** 현재 월. 개화월 하드 필터와 화면 22 제철 추천의 입력이다. */
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    val allFlowers: List<Flower> = repository.flowers

    var filter by mutableStateOf(DexFilter())
        private set

    /**
     * 저장된 기록. Compose가 다시 그리도록 상태로 미러한다.
     *
     * ⚠️ **`StateFlow`를 그냥 읽으면 화면이 갱신되지 않는다.** `collectAsState`를
     *    화면마다 부르게 하면 ViewModel의 파생 숫자(`collectedCount` 등)는 여전히
     *    옛 값을 본다 — 화면 04의 그리드는 새 꽃이 채워지는데 현황 카드는 37에 멈춘다.
     */
    private var records by mutableStateOf<List<Discovery>>(emptyList())

    /**
     * 파일을 아직 읽는 중인가.
     *
     * ⚠️ **화면 22(빈 상태)와 구분해야 한다.** 로딩 중을 0종으로 그리면
     *    도감을 채운 사용자에게 `처음이라면 이 꽃부터`가 한 프레임 깜빡인다.
     */
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            discoveries.load()
            loading = false
            // 참조 없는 사진을 정리한다.
            //
            // **왜 여기인가.** `prunePhotos`는 기록을 다 읽은 뒤에만 안전하다
            // (안 읽은 상태에서는 모든 사진이 "참조 없음"이다). `load()` 직후가
            // 그 조건이 보장되는 유일한 자리다. `Application.onCreate`에 두면
            // 로드 완료를 기다려야 하고, 기다리는 코드를 잊으면 **사진이 전부 날아간다.**
            //
            // 남는 경우: 등록 중에 앱이 죽어 사진은 저장됐고 기록은 안 된 때.
            // 그대로 두면 기기에 계속 쌓이는데, 사용자에게는 아무 증상이 없어서
            // 저장공간 문제로만 뒤늦게 드러난다.
            val removed = discoveries.prunePhotos()
            if (removed > 0) {
                android.util.Log.i("CatchFlower", "참조 없는 사진 ${removed}장 정리")
            }
        }
        viewModelScope.launch {
            discoveries.discoveries.collect { records = it }
        }
    }

    // 🔴 여기 있던 `forceEmptyState` 토글을 지웠다(2026-08-09 · (38)).
    //    빈 상태(화면 22)를 개발 중에 보기 위한 것이었는데, 헤더에 `0종 보기` 버튼이
    //    딸려 있어서 **3종을 모은 사용자 화면에 그대로 떠 있었다** — 랭킹에서 지운
    //    `[개발] 친구 없는 화면`과 **같은 결함**이다(`RankingScreens.kt:570`).
    //    화면 22는 토글 없이도 볼 수 있다: `adb shell pm clear com.catchflower.app`.
    //    그게 실제 신규 사용자가 보는 경로이므로 미리보기보다 정확하다.

    val collectedIds: Set<Int>
        get() = DiscoveryRules.collectedIds(records)

    val collectedCount: Int get() = collectedIds.size

    val thisSeasonCount: Int
        get() = DiscoveryRules.seasonCollectedCount(records, currentMonth)

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
        get() = DiscoveryRules.recentDiscoveries(records, RECENT_LIMIT)

    /**
     * 화면 20 지표 3칸 `모은 꽃 37종 / 총 발견 112회 / 공유 26개`.
     *
     * 🔴 **읽는 중에는 `null`이다. 0으로 그리지 않는다.** [loading]이 있는 이유와
     *    같다 — 200종을 모은 사용자가 마이 탭을 열 때마다 `모은 꽃 0종`을 한 프레임
     *    보게 된다. 파일을 읽기 전이라 **모르는 것**이고, 0은 사실 주장이다.
     *
     * ⚠️ **화면 20이 [DiscoveryRepository]를 다시 열지 않고 여기서 받아 간다.**
     *    같은 저장소를 두 ViewModel이 각자 읽으면 한쪽이 옛 목록을 들고 있을 수 있다
     *    (등록 직후가 그렇다) — 그러면 도감은 38종인데 마이는 37종이 된다.
     */
    val profileStats: DiscoveryRules.ProfileStats?
        get() = if (loading) null else DiscoveryRules.profileStats(records)

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
        DiscoveryRules.forFlower(records, flowerId)

    /**
     * 화면 05 썸네일. 파일명 → 실제 파일. 없으면 null이고,
     * [com.catchflower.app.ui.component.DiscoveryPhoto]가 **도감 일러스트**로 되돌린다
     * (실루엣이 아니다 — 실루엣은 미발견 종의 표현이고, 이 기록은 이미 발견한 것이다).
     *
     * ⚠️ **기록마다 부른다.** 꽃 단위로 한 번 구해서 돌려쓰면 같은 종의 모든 기록이
     *    첫 사진 하나를 보여주는데, 화면으로는 완벽하게 정상으로 보인다.
     */
    fun photoFile(discovery: Discovery): java.io.File? =
        discovery.localPhotoPath
            ?.takeIf { discoveries.photos.exists(it) }
            ?.let { discoveries.photos.file(it) }

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

    private companion object {
        const val BADGE_STEP = 10

        /** 화면 04 `최근 발견한 꽃` 가로 스트립 칸 수. */
        const val RECENT_LIMIT = 6
    }
}
