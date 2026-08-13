package com.catchflower.app.data.model

/**
 * 랭킹 한 행. 화면 17(지역)·18(친구)이 같은 타입을 쓴다.
 *
 * **왜 하나로 두는가**: 두 화면의 행 구성이 같다 —
 * 순위 / 대표 꽃 / 닉네임 / 종수 (와이어프레임 17 주석 ④가 지역 랭킹에도
 * 같은 구성을 적용하라고 명시했다). 타입을 나누면 정렬·동점 처리가 두 벌 생긴다.
 */
data class RankEntry(
    val userId: String,
    val nickname: String,
    /** 이번 시즌 모은 종수. **순위의 유일한 기준**이다. */
    val speciesCount: Int,
    /** 대표 꽃 도감번호. 화면 17 행 `대표 꽃 · 장미`. */
    val signatureFlowerId: Int,
    /** 획득한 대표 칭호. 화면 20 주석 ①이 "랭킹 리스트에도 같이 나간다"고 했다. */
    val title: String? = null,
    /** 나인가. 리스트에서 강조 테두리를 준다 (와이어프레임 18 주석 ③). */
    val isMe: Boolean = false,
    /** 동점 처리용 — 총 발견 횟수. [RankingRules] 참조. */
    val totalDiscoveries: Int = 0,
    /** 동점 처리용 — 현재 종수에 도달한 시각(ms). 빠른 쪽이 앞선다. */
    val reachedAt: Long = 0L,
)

/** 순위가 매겨진 행. `rank`는 계산 결과이므로 [RankEntry]에 두지 않는다. */
data class RankedEntry(
    val rank: Int,
    val entry: RankEntry,
    /** 지난 시즌 대비 순위 변동. 화면 17 `▲ 3`. null이면 비교 대상이 없다(신규). */
    val delta: Int? = null,
)

/**
 * 순위 계산 규칙.
 *
 * ⚠️ **동점 처리는 와이어프레임 17 주석 ⑤에서 "미정 → 확정 필요"로 표시된 항목이다.**
 *    기획서에 없다. 그래서 여기서 **정하지 않고** 주석이 제시한 순서를 그대로 구현하고,
 *    바꿀 곳을 한 군데로 모아 둔다: ① 도달 시각이 빠른 사람 ② 총 발견 횟수.
 *    오너 답이 오면 [compareTieBreak]만 고친다.
 *
 * **정렬을 안정적으로 만드는 이유**: 동점자 순서가 실행마다 흔들리면
 * "어제는 4위였는데 오늘 5위"가 되고, 그건 데이터가 바뀐 게 아니라 **버그**다.
 * 마지막 비교에 `userId`를 넣어 완전순서를 만든다.
 */
object RankingRules {

    /**
     * 종수 내림차순 → 동점 처리 → 순위 부여.
     *
     * **동점자는 같은 순위를 받는다** (공동 3위 둘이면 다음은 5위).
     * 순위를 1씩 올리면 같은 종수인데 다른 순위가 되어 "왜 내가 아래냐"가 된다.
     */
    fun rank(entries: List<RankEntry>): List<RankedEntry> {
        val sorted = entries.sortedWith(comparator)
        var currentRank = 0
        var lastCount = Int.MIN_VALUE
        return sorted.mapIndexed { index, entry ->
            if (entry.speciesCount != lastCount) {
                currentRank = index + 1
                lastCount = entry.speciesCount
            }
            RankedEntry(rank = currentRank, entry = entry)
        }
    }

    private val comparator: Comparator<RankEntry> = Comparator { a, b ->
        compareValuesBy(b, a) { it.speciesCount }
            .takeIf { it != 0 }
            ?: compareTieBreak(a, b)
    }

    /** ① 도달 시각이 빠른 사람 ② 총 발견 횟수 많은 사람 ③ id (안정 정렬용). */
    private fun compareTieBreak(a: RankEntry, b: RankEntry): Int =
        compareValuesBy(a, b) { it.reachedAt }
            .takeIf { it != 0 }
            ?: compareValuesBy(b, a) { it.totalDiscoveries }
                .takeIf { it != 0 }
            ?: a.userId.compareTo(b.userId)

    /**
     * 화면 17 `3종만 더 모으면 15위권!`
     *
     * @return 필요한 종수. 이미 그 안이거나 위가 없으면 `null` —
     *   그때 이 문장을 **쓰지 않는다.** `0종만 더 모으면`은 말이 안 된다.
     */
    fun speciesToReach(ranked: List<RankedEntry>, myRank: Int, targetRank: Int): Int? {
        if (myRank <= targetRank) return null
        val me = ranked.firstOrNull { it.rank == myRank } ?: return null
        // 목표 순위에 있는 사람보다 1종 많으면 그 자리에 든다.
        val target = ranked.filter { it.rank <= targetRank }.minByOrNull { it.entry.speciesCount }
            ?: return null
        val gap = target.entry.speciesCount - me.entry.speciesCount + 1
        return gap.takeIf { it > 0 }
    }

    /**
     * 화면 18 `{1위 닉네임}까지 25종 남음`.
     *
     * @return 1위와의 차이. 내가 1위면 `null`(문장을 쓰지 않는다).
     */
    fun speciesBehindLeader(ranked: List<RankedEntry>): Pair<String, Int>? {
        val leader = ranked.firstOrNull() ?: return null
        val me = ranked.firstOrNull { it.entry.isMe } ?: return null
        if (me.entry.isMe && leader.entry.isMe) return null
        val gap = leader.entry.speciesCount - me.entry.speciesCount
        return if (gap > 0) leader.entry.nickname to gap else null
    }

    /**
     * 화면 17이 처음 보여주는 줄 수. A 문서 2절 17번의 `6위부터 더 보기`가 이 값을 전제한다.
     *
     * ⚠️ 이 숫자를 바꾸면 **A 문서의 예시 문구도 같이 바뀐다**(6이 아니게 된다).
     */
    const val VISIBLE_ROWS = 5

    /**
     * `더 보기` 버튼에 쓸 순위. **더 보여줄 줄이 없으면 `null`** — 화면은 버튼을 뺀다.
     *
     * 🔴 **`보인 줄 수 + 1`도 `마지막 줄의 순위 + 1`도 안 된다.** 동점자가 있으면
     *    `rank()`가 순위를 건너뛴다(공동 4위 둘이면 다음은 6위) — 그러면 `5위부터 더 보기`가
     *    **없는 순위를 가리킨다.** 반대로 공동 4위가 셋이라 6번째 줄도 4위면
     *    `+1`은 이미 지나간 자리를 가리킨다.
     *    그래서 **숨은 첫 줄의 순위를 그대로 쓴다** — 목록이 실제로 이어지는 자리다.
     *    (공동 순위라서 이미 보인 숫자가 다시 나오는 경우가 있는데, 그건 참이다:
     *    그 순위에 아직 못 보여준 사람이 남아 있다.)
     *
     * 🔴 **`region_ranking`에는 `limit`이 없다**(0002) — 이 함수가 자르는 것은
     *    **화면**이지 조회가 아니다. `offset`을 기다릴 이유가 없었다.
     *
     * @param rows 동네 전체 순위(내려온 그대로).
     * @param shownCount 지금 화면에 그린 줄 수.
     */
    fun nextPageRank(rows: List<RankedEntry>, shownCount: Int): Int? =
        rows.getOrNull(shownCount)?.rank
}
