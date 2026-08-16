package com.catchflower.app.ui.ranking

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.SeasonClock
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.RankingService
import com.catchflower.app.data.RankingSource
import com.catchflower.app.data.model.RankedEntry
import com.catchflower.app.data.model.RankingRules
import kotlinx.coroutines.launch

/** 화면 17·18의 탭. 기본 진입은 `우리 동네` (와이어프레임 17 주석 ①). */
enum class RankingTab(val label: String) {
    REGION("우리 동네"),
    FRIENDS("친구"),
}

/**
 * 화면 17·18 상태.
 *
 * ⚠️ **`@JvmOverloads`가 없으면 화면을 여는 순간 앱이 죽는다.** 화면 07에서 이미
 *    당했다 — `AndroidViewModelFactory`는 리플렉션으로 `(Application)` 단일 인자
 *    생성자를 찾는데, 코틀린 기본값은 그 생성자를 만들어 주지 않는다.
 *    **컴파일은 통과하고 실행만 죽는다.**
 *
 * 🔴 **더미를 읽지 않는다.** 랭킹은 남의 발견 기록을 세는 것이라 기기에서 계산할 수
 *    없다 — 서버 함수가 센 결과를 그대로 그린다([RankingService]).
 *    그래서 **"랭킹이 없는 상태"가 생겼다.** 더미에는 없던 상태이고, 한 가지가
 *    아니라 네 가지다 — [RegionRankingUi]가 그것을 가른다.
 */
class RankingViewModel @JvmOverloads constructor(
    app: Application,
    /** 테스트가 시각을 고정할 수 있게 주입받는다. 시즌 배너가 달마다 달라지기 때문이다. */
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    /**
     * 랭킹 조회. null이면 서버 기능이 꺼진 빌드다.
     *
     * ⚠️ **[DiscoveryRepository]가 든 것과 같은 `AuthService`를 쓴다.** 새로 만들면
     *    토큰 갱신이 서로 다른 prefs 인스턴스를 통해 일어나 회전된 `refresh_token`을
     *    덮어써서 **며칠 뒤 조용히 로그인이 끊긴다.**
     */
    private val ranking: RankingSource? =
        DiscoveryRepository.get(app).auth?.let { RankingService(it) },
) : AndroidViewModel(app) {

    private val repository = FlowerRepository.get(app)

    var tab by mutableStateOf(RankingTab.REGION)
        private set

    fun selectTab(next: RankingTab) {
        tab = next
    }

    /** 화면 17 시즌 배너. */
    val season: SeasonClock.Status get() = SeasonClock.status(nowProvider())

    val deadlineLabel: String get() = SeasonClock.deadlineLabel(season)

    /** 화면 17. **처음은 [RegionRankingUi.Loading]이다** — 빈 랭킹이 아니다. */
    var region by mutableStateOf<RegionRankingUi>(RegionRankingUi.Loading)
        private set

    /** 화면 18. */
    var friends by mutableStateOf<FriendRankingUi>(FriendRankingUi.Loading)
        private set

    /**
     * 화면 20 프로필·활동 지역.
     *
     * ⚠️ **[region]과 같은 `myRegion` 응답에서 나온다.** 화면 20이 따로 물으면
     *    두 화면이 다른 동네를 말할 수 있다.
     */
    var profile by mutableStateOf(RankingUiMapper.ProfileUi())
        private set

    /**
     * 화면 18 `친구 {N}명과 겨루는 중` · 화면 20 `친구 관리 {N}명`.
     *
     * 🔴 **`null`은 "모른다"이고 화면은 숫자를 안 쓴다.** 0으로 바꾸지 마라 —
     *    이유는 [RankingUiMapper.friendCount]에 있다. 랭킹 행 수로 대신하는 것도
     *    안 된다(그게 (18)에서 7 vs 8이 났던 방식이다).
     *
     * ⚠️ 화면 18·20이 **같은 값을 읽는다.** 두 화면이 각자 세면 반드시 갈라진다.
     *
     * 🔴 **선언을 [init] 아래로 내리지 마라.** 아래 있으면 `refresh()`가 돌 때
     *    이 프로퍼티의 `MutableState`가 아직 null이어서 **탭을 여는 순간 앱이 죽는다**
     *    (`NullPointerException … MutableState.setValue`). 코틀린은 선언 순서대로
     *    초기화하고 `init`은 그 사이에 끼어 있다. **컴파일은 통과하고 JVM 테스트도
     *    전부 초록이었다** — `AndroidViewModel`이라 테스트가 이 클래스를 못 만든다.
     *    실제로 이 순서 때문에 죽었고, 에뮬레이터에서 열어 봐야 보였다.
     */
    var friendCount by mutableStateOf<Int?>(null)
        private set

    init {
        refresh()
    }

    /** A 문서 3절 `조회 실패 → 다시 시도` 버튼이 부른다. */
    fun refresh() {
        loadRegion()
        loadFriends()
    }

    /**
     * 화면 17.
     *
     * 🔴 **[RankingSource.myRegion]을 같이 물어야 한다.** 서버는 "지역을 안 정했다"와
     *    "지역은 정했지만 아무도 안 찍었다"에 **똑같이 빈 응답**을 준다(실측:
     *    `region_none.json` == `region_with_dong.json` == `[]`). 안 물어보면 화면이
     *    두 상태를 구분할 수 없고, 앞쪽은 **사용자가 할 일이 있는데 안 알려주는** 상태다.
     */
    private fun loadRegion() {
        val source = ranking ?: run {
            region = RegionRankingUi.NotConfigured
            // 🔴 키 없는 빌드에서 더미 프로필을 남기지 않는다 — `꽃보다효산 · 연남동`이
            //    실제 값처럼 보인다.
            profile = RankingUiMapper.ProfileUi()
            return
        }
        region = RegionRankingUi.Loading
        viewModelScope.launch {
            // 판정은 [RankingUiMapper]가 한다 — 여기서 하면 JVM 테스트가 못 읽는다.
            val mine = source.myRegion()
            region = RankingUiMapper.region(ranking = source.regionRanking(), mine = mine)
            // 화면 20이 같은 응답을 쓴다 — 두 화면이 다른 동네를 말할 수 없다.
            profile = RankingUiMapper.profile(mine)
        }
    }

    /**
     * 화면 18.
     *
     * ⚠️ **친구 수를 따로 묻는다.** 랭킹 응답의 행 수는 친구 수가 아니다 —
     *    이유는 [RankingUiMapper.friendCount]에 있다.
     */
    private fun loadFriends() {
        val source = ranking ?: run {
            friends = FriendRankingUi.NotConfigured
            friendCount = null
            return
        }
        friends = FriendRankingUi.Loading
        // 🔴 이전 화면의 친구 수를 남겨두지 않는다. 새로 고치는 동안 옛 숫자가
        //    새 목록 옆에 붙으면 어느 쪽이 지금 데이터인지 알 수 없다.
        friendCount = null
        viewModelScope.launch {
            friends = RankingUiMapper.friends(source.friendRanking())
            friendCount = RankingUiMapper.friendCount(source.friendCount())
        }
    }

    /** 화면 17 목록. 화면이 상태로 분기하므로 여기선 행만 꺼낸다. */
    val regionTop: List<RankedEntry>
        get() = (region as? RegionRankingUi.Loaded)?.rows.orEmpty()

    val friendRanking: List<RankedEntry>
        get() = (friends as? FriendRankingUi.Loaded)?.rows.orEmpty()

    /** 화면 18 시상대 1~3위. */
    val podium: List<RankedEntry> get() = friendRanking.take(3)

    /** 화면 18 4위 이하. 내 순위는 리스트 상단에 고정되지 않고 **강조 테두리**만 준다. */
    val friendRest: List<RankedEntry> get() = friendRanking.drop(3)

    /** 화면 18 초대 유도 분기. 기준과 이유는 [RankingUiMapper.showInvite]에 있다. */
    val showInviteInsteadOfRanking: Boolean
        get() = RankingUiMapper.showInvite(friends, friendCount)

    /** 화면 18 `{1위 닉네임}까지 25종 남음`. 내가 1위면 null → 문장을 쓰지 않는다. */
    val behindLeader: Pair<String, Int>? get() = RankingRules.speciesBehindLeader(friendRanking)

    val me: RankedEntry? get() = friendRanking.firstOrNull { it.entry.isMe }

    /**
     * 화면 17 내 순위 카드.
     *
     * ⚠️ 지역 랭킹은 **상위 N명만** 받으므로 내가 그 밖이면 여기 없다. 그때 null이고
     *    화면은 순위 칸을 비운다 — **0위나 1위로 채우면 안 된다.**
     *    (내 순위만 주는 서버 함수를 따로 두는 것이 다음 단계다 — §9)
     */
    val myRegionRank: RankedEntry? get() = regionTop.firstOrNull { it.entry.isMe }

    /** 꽃 이름·일러스트를 위해 도감을 참조한다. 행의 `대표 꽃 · 장미`. */
    // 🔴 **대표종으로 접어서 본다**(B-4 · 계약 1-6). 서버 행에는 접힌 종의 번호가
    //    남아 있을 수 있다 — 앱은 자기 행을 UPDATE하지 않고 iOS는 아직 B-4가 없다.
    //    `byId`로 읽으면 랭킹에 `서양민들레`가 뜨는데 도감에는 그 칸이 없다.
    fun flowerName(flowerId: Int): String = repository.representativeOf(flowerId)?.name.orEmpty()

    fun flower(flowerId: Int) = repository.representativeOf(flowerId)

    // 🔴 **`forceNoFriends`·`toggleNoFriends`·`effectiveShowInvite`를 지웠다**(2026-08-09).
    //    더미 시절에는 친구가 항상 8명이라 "친구 0명 분기"(와이어프레임 18 주석 ④)를
    //    **코드로만 존재하는 화면**이었고, 그래서 눌러서 보는 토글이 필요했다.
    //    더미를 떼면서 그 분기가 **실제 기본 화면**이 됐다 — 토글은 목적을 잃었고,
    //    `[개발] …` 라벨을 붙인 버튼이 사용자 화면에 남아 있었다(실측: 화면 18·19).
    //
    //    ⚠️ 다시 만들 때는 **[showInviteInsteadOfRanking]에 손대지 않는 방법**으로 한다.
    //       분기 조건에 `||`를 하나 더 붙이면 그 조건이 테스트가 재는 것과 달라진다
    //       ([RankingUiMapper.showInvite]는 테스트가 있는데 `effectiveShowInvite`는 없었다).
}
