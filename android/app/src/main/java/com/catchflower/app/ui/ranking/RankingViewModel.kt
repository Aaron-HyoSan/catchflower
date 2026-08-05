package com.catchflower.app.ui.ranking

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.SeasonClock
import com.catchflower.app.data.DummyRanking
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.model.RankEntry
import com.catchflower.app.data.model.RankedEntry
import com.catchflower.app.data.model.RankingRules

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
 */
class RankingViewModel @JvmOverloads constructor(
    app: Application,
    /** 테스트가 시각을 고정할 수 있게 주입받는다. 시즌 배너가 달마다 달라지기 때문이다. */
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
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

    /** 화면 17 상위 5명. 순위는 **여기서 계산한다** (더미에 박아 두지 않는다). */
    val regionTop: List<RankedEntry> = RankingRules.rank(DummyRanking.regionTop())

    /** 화면 18 친구 랭킹 — 나를 포함해 순위를 매긴다. */
    val friendRanking: List<RankedEntry> = RankingRules.rank(DummyRanking.friends())

    /** 화면 18 시상대 1~3위. */
    val podium: List<RankedEntry> get() = friendRanking.take(3)

    /** 화면 18 4위 이하. 내 순위는 리스트 상단에 고정되지 않고 **강조 테두리**만 준다. */
    val friendRest: List<RankedEntry> get() = friendRanking.drop(3)

    val friendCount: Int get() = friendRanking.count { !it.entry.isMe }

    /**
     * 친구가 [GamePolicy]가 아니라 와이어프레임 18 주석 ④가 정한 **2명 이하**면
     * 랭킹 대신 초대 유도를 화면 전체로 띄운다.
     *
     * ⚠️ 이 숫자는 정책이 아니라 **화면 분기 기준**이라 GamePolicy에 넣지 않았다.
     *    (게임 규칙 숫자만 GamePolicy에 둔다 — 공유계약 3절)
     */
    val showInviteInsteadOfRanking: Boolean get() = friendCount <= FRIENDS_MIN_FOR_RANKING

    /** 화면 18 `{1위 닉네임}까지 25종 남음`. 내가 1위면 null → 문장을 쓰지 않는다. */
    val behindLeader: Pair<String, Int>? get() = RankingRules.speciesBehindLeader(friendRanking)

    val me: RankedEntry? get() = friendRanking.firstOrNull { it.entry.isMe }

    /** 꽃 이름·일러스트를 위해 도감을 참조한다. 행의 `대표 꽃 · 장미`. */
    fun flowerName(flowerId: Int): String = repository.byId(flowerId)?.name.orEmpty()

    fun flower(flowerId: Int) = repository.byId(flowerId)

    /** 개발용 — 친구 0명 분기(와이어프레임 18 주석 ④)를 화면에서 볼 수 있게 한다. */
    var forceNoFriends by mutableStateOf(false)
        private set

    fun toggleNoFriends() {
        forceNoFriends = !forceNoFriends
    }

    val effectiveShowInvite: Boolean get() = forceNoFriends || showInviteInsteadOfRanking

    private companion object {
        /** 와이어프레임 18 주석 ④ `친구 0~2명일 때`. */
        const val FRIENDS_MIN_FOR_RANKING = 2
    }
}
