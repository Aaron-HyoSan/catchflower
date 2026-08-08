package com.catchflower.app.ui.ranking

import com.catchflower.app.data.model.RankedEntry

/**
 * 화면 17 지역 랭킹의 상태.
 *
 * 🔴 **네 상태를 하나로 합치지 않는다.** 더미를 쓸 때는 랭킹이 **항상 있었다** —
 *    서버를 붙이면 없을 수 있고, 없는 이유가 네 가지다. 목록이 비었다는 사실만
 *    보고 한 문장으로 처리하면 다음이 섞인다:
 *
 *    | 상태 | 사용자가 할 일 |
 *    |---|---|
 *    | [Loading] | 기다린다 |
 *    | [NoRegion] | **동네를 고른다** ← 할 일이 있는데 안 알려주면 영원히 빈 화면이다 |
 *    | [Empty] | 꽃을 찍는다 |
 *    | [Failed] | 다시 시도한다 |
 *
 *    실측으로 [NoRegion]과 [Empty]는 **서버 응답이 똑같다**(둘 다 `[]`). 그래서
 *    [RankingViewModel]이 `myRegion`을 따로 물어서 가른다. 안 가르면 갓 설치한
 *    사용자에게 "아직 이 동네에 모은 꽃이 없어요"라고 말하는데, 실제로는
 *    **동네를 안 골랐고 그 화면을 아무도 안 보여준 것**이다.
 *
 * 문구는 A 문서 3절 `화면 17 지역 랭킹의 빈·실패 상태`에 있다. **여기서 만들지 않는다.**
 */
sealed interface RegionRankingUi {
    /** 아직 서버 응답이 없다. **빈 목록과 다르다** — 0종으로 깜빡이면 안 된다. */
    data object Loading : RegionRankingUi

    /** 활동 지역(`users.dong_code`)을 안 정했다. 화면 02로 보내야 한다. */
    data object NoRegion : RegionRankingUi

    /** 지역은 정했지만 그 지역에 이번 시즌 발견이 0건이다. */
    data object Empty : RegionRankingUi

    /** 서버를 못 불렀다. **빈 랭킹으로 보여주면 안 된다.** */
    data object Failed : RegionRankingUi

    /**
     * 서버 기능이 꺼진 빌드(키 없음).
     *
     * ⚠️ **[Failed]와 다르게 다룬다.** 오류 문구도 `다시 시도` 버튼도 띄우지 않는다 —
     *    사용자가 아무리 눌러도 되지 않고, 사용자 탓도 아니다.
     */
    data object NotConfigured : RegionRankingUi

    /**
     * 랭킹이 있다.
     *
     * @property scopeIsGu B-6이 구 단위로 넓힌 결과다. 🔴 이때 제목에 동명을 쓰면
     *   **거짓말이 된다** — `연남동 이웃 1,284명`이라고 써 놓고 마포구 전체 순위다.
     *   서버가 `scope`를 돌려주는 이유가 이것이다(0002 SQL 2절 주석).
     * @property regionLabel 제목·목록 라벨에 넣을 지역명. [scopeIsGu]면 구명이다.
     * @property memberCount **가입자 수가 아니라 이번 시즌 발견이 있는 사람 수**다.
     *   행 개수로 세면 상위 5명만 받는 화면에서 이웃이 5명이 된다.
     */
    data class Loaded(
        val rows: List<RankedEntry>,
        val regionLabel: String,
        val scopeIsGu: Boolean,
        val memberCount: Int,
    ) : RegionRankingUi
}

/**
 * 화면 18 친구 랭킹의 상태.
 *
 * 지역과 달리 `NoRegion`이 없다 — 친구 랭킹에는 지역 조건이 없다(0002 SQL 3절:
 * "최소 인원 규칙이 없다").
 */
sealed interface FriendRankingUi {
    data object Loading : FriendRankingUi

    data object Failed : FriendRankingUi

    /** 키 없는 빌드. [RegionRankingUi.NotConfigured]와 같은 이유로 따로 둔다. */
    data object NotConfigured : FriendRankingUi

    /**
     * @property rows 나를 포함한다. 시상대에 내가 없으면 내 순위를 알 수 없다.
     * @property rankedFriendCount 🔴 **"친구 수"가 아니다.** 이번 시즌 발견이 0건인
     *   친구는 서버 응답에 행이 아예 없다(실측). A 문서 `친구 8명과 겨루는 중`을
     *   이 값으로 만들면 화면 20의 `친구 관리 8명`과 **다른 숫자**가 된다 —
     *   (18)에서 이미 7 vs 8로 겪은 사고다. 친구 수는 `friendships`를 세야 한다.
     */
    data class Loaded(
        val rows: List<RankedEntry>,
        val rankedFriendCount: Int,
    ) : FriendRankingUi
}
