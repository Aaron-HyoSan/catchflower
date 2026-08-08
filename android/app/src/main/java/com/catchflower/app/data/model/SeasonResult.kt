package com.catchflower.app.data.model

/**
 * 화면 21이 그리는 **지난 시즌** 결과 한 벌.
 *
 * 🔴 **지금 이 값을 만들 수 있는 곳이 없다.** 서버 `my_season_summary`는 **이번
 *    시즌**만 세고(실측), 지난 시즌 순위·최고 순위·수여 배지를 보관하는 테이블이
 *    01 스키마에 없다. 기기 기록으로도 못 만든다 — 순위는 **남의 기록을 세는 것**이라
 *    이 기기에 없다. 시즌이 끝나면 서버의 이번 시즌 값도 사라지므로,
 *    **시즌 종료 시점에 스냅샷을 남기는 서버 작업**이 필요하다(§9 · A 문서 4절 9번).
 *
 *    그래서 화면 21은 `null`을 받는다. 예시 값을 기본값으로 두면 안 된다 —
 *    **시즌 종료 후 강제로 뜨는 전면 화면**이라(와이어프레임 21 주석 ①) 한 번도
 *    랭킹에 든 적 없는 사용자가 `연남동 4위`를 보게 되고, 눌러서 들어간 것도 아니다.
 *
 * ⚠️ [dongName]을 여기 담는 이유: 지금 프로필의 동네를 붙이면 **이사한 사용자의
 *    지난 시즌 순위에 새 동네 이름**이 붙는다. 순위와 동네는 한 벌로 와야 한다.
 */
data class SeasonResult(
    /** `2026`. 제목 `2026 시즌 1이 끝났어요`에 쓴다. */
    val year: Int,
    /** `시즌 1`. */
    val seasonLabel: String,
    /** 그 시즌 그 동네에서의 최종 순위. */
    val rank: Int,
    val dongName: String,
    /** 이번 시즌 발견이 있던 이웃 수. **가입자 수가 아니다**(0002 SQL `member_count`). */
    val neighborCount: Int,
    val speciesCount: Int,
    val discoveryCount: Int,
    val shareCount: Int,
    /** 시즌 중 최고 순위. 최종 순위와 다를 수 있다. */
    val bestRank: Int,
    /**
     * 받은 배지. 화면 21 주석 ②: 순위형(1~3위)과 달성형 2계열이고
     * **4위는 순위형 미지급 → 달성형만** 표시한다. 없으면 `null`이고 블록을 안 그린다.
     */
    val awardedBadge: String?,
)
