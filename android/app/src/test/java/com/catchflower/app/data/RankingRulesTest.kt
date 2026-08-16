package com.catchflower.app.data

import com.catchflower.app.data.model.RankEntry
import com.catchflower.app.data.model.RankingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 순위 계산 테스트.
 *
 * **왜 필요한가**: 화면에 순위가 `1 2 3 4 5`로 보이면 정렬이 맞는 것처럼 보인다.
 * 그런데 순위 번호는 인덱스에서 나오므로 **정렬이 틀려도 번호는 항상 예쁘게 나온다.**
 * 눈으로는 절대 못 잡는다 — 그래서 더미에 순위를 박지 않고 여기서 검증한다.
 */
class RankingRulesTest {

    private fun entry(
        id: String,
        species: Int,
        reachedAt: Long = 0L,
        discoveries: Int = 0,
        isMe: Boolean = false,
    ) = RankEntry(
        userId = id,
        nickname = id,
        speciesCount = species,
        signatureFlowerId = 1,
        isMe = isMe,
        totalDiscoveries = discoveries,
        reachedAt = reachedAt,
    )

    @Test
    fun `종수 내림차순으로 정렬한다`() {
        val ranked = RankingRules.rank(
            // 일부러 뒤섞어 넣는다. 입력 순서를 그대로 쓰면 여기서 걸린다.
            listOf(entry("c", 29), entry("a", 41), entry("b", 35)),
        )
        assertEquals(listOf("a", "b", "c"), ranked.map { it.entry.userId })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun `동점자는 같은 순위를 받고 다음은 건너뛴다`() {
        val ranked = RankingRules.rank(
            listOf(
                entry("a", 40),
                entry("b", 30, reachedAt = 1L),
                entry("c", 30, reachedAt = 2L),
                entry("d", 20),
            ),
        )
        // 공동 2위 둘 → 다음은 4위. 3위를 주면 "같은 종수인데 왜 내가 아래냐"가 된다.
        assertEquals(listOf(1, 2, 2, 4), ranked.map { it.rank })
        assertEquals(listOf("a", "b", "c", "d"), ranked.map { it.entry.userId })
    }

    @Test
    fun `동점이면 먼저 도달한 사람이 앞선다`() {
        val ranked = RankingRules.rank(
            listOf(entry("late", 30, reachedAt = 200L), entry("early", 30, reachedAt = 100L)),
        )
        // 와이어프레임 17 주석 ⑤가 제시한 1순위 기준.
        assertEquals("early", ranked.first().entry.userId)
    }

    @Test
    fun `도달 시각이 같으면 총 발견 횟수가 많은 사람이 앞선다`() {
        val ranked = RankingRules.rank(
            listOf(
                entry("few", 30, reachedAt = 100L, discoveries = 50),
                entry("many", 30, reachedAt = 100L, discoveries = 90),
            ),
        )
        assertEquals("many", ranked.first().entry.userId)
    }

    @Test
    fun `모든 기준이 같으면 순서가 실행마다 흔들리지 않는다`() {
        // ⚠️ 이게 흔들리면 "어제는 4위였는데 오늘 5위"가 되고, 사용자에게는 **버그**로 보인다.
        val entries = listOf(entry("z", 30), entry("a", 30), entry("m", 30))
        val first = RankingRules.rank(entries).map { it.entry.userId }
        val shuffled = RankingRules.rank(entries.reversed()).map { it.entry.userId }
        assertEquals(first, shuffled)
        // 완전순서를 만드는 마지막 비교는 userId다.
        assertEquals(listOf("a", "m", "z"), first)
    }

    @Test
    fun `빈 목록은 빈 결과다`() {
        assertEquals(emptyList<Int>(), RankingRules.rank(emptyList()).map { it.rank })
    }

    @Test
    fun `1위와의 격차를 종수로 알려준다`() {
        val ranked = RankingRules.rank(
            listOf(entry("leader", 38), entry("me", 13, isMe = true)),
        )
        val (nickname, gap) = RankingRules.speciesBehindLeader(ranked)!!
        assertEquals("leader", nickname)
        assertEquals(25, gap) // 화면 18 `{1위}까지 25종 남음`
    }

    @Test
    fun `내가 1위면 격차 문장을 쓰지 않는다`() {
        val ranked = RankingRules.rank(
            listOf(entry("me", 38, isMe = true), entry("other", 13)),
        )
        // `나까지 0종 남음`은 말이 안 된다.
        assertNull(RankingRules.speciesBehindLeader(ranked))
    }

    @Test
    fun `1위와 동점이면 격차 문장을 쓰지 않는다`() {
        val ranked = RankingRules.rank(
            listOf(entry("other", 30, reachedAt = 1L), entry("me", 30, reachedAt = 2L, isMe = true)),
        )
        // 종수가 같으면 gap이 0이다 — 문장을 쓰면 `0종 남음`이 된다.
        assertNull(RankingRules.speciesBehindLeader(ranked))
    }

    @Test
    fun `내가 목록에 없으면 격차를 계산하지 않는다`() {
        val ranked = RankingRules.rank(listOf(entry("a", 30), entry("b", 20)))
        assertNull(RankingRules.speciesBehindLeader(ranked))
    }

    @Test
    fun `목표 순위 안에 이미 있으면 목표 문장을 쓰지 않는다`() {
        val ranked = RankingRules.rank(listOf(entry("me", 40, isMe = true)))
        // `0종만 더 모으면 15위권!`은 말이 안 된다.
        assertNull(RankingRules.speciesToReach(ranked, myRank = 1, targetRank = 15))
        assertNull(RankingRules.speciesToReach(ranked, myRank = 15, targetRank = 15))
    }

    // ── 내 순위 카드의 종수 (2026-08-17) ─────────────────────────────

    /**
     * 어떤 경우에 빨개지나: 서버 상위 목록에 내 행이 없을 때 **`0종`으로 채우면.**
     *
     * 🔴 이건 실측으로 나온 결함이다. 스토어 스크린샷을 찍다가 해바라기를 등록한 직후
     *    도감은 `이번 시즌 1종`인데 랭킹 카드는 **`이번 시즌 모은 꽃 0종`**이었다
     *    (`?: 0` · `RankingScreens.MyRankCard`). 순위는 `-`로 비우면서 종수는 0이라고
     *    **단정**한 것이고, 사용자에게는 "방금 모은 것이 사라졌다"로 읽힌다.
     *    화면으로는 아무 오류도 안 보인다 — 숫자가 예쁘게 그려진다.
     */
    @Test
    fun `상위 목록 밖이면 기기에서 센 종수를 쓴다`() {
        assertEquals(1, RankingRules.mySeasonSpeciesCount(server = null, local = 1))
        // 서버 행이 있으면 그쪽이 이긴다 — 아래 목록의 내 행과 같은 숫자여야 한다.
        assertEquals(7, RankingRules.mySeasonSpeciesCount(server = 7, local = 1))
        // 서버가 0을 **실제로** 준 경우는 0이 사실이다(그 동네에서 이번 시즌 0종).
        assertEquals(0, RankingRules.mySeasonSpeciesCount(server = 0, local = 3))
    }

    /**
     * 어떤 경우에 빨개지나: 둘 다 모르는데 **0을 그리면.**
     *
     * 도감을 읽기 전(`loading`)이라 기기 값도 없다. 0은 모르는 값이 아니라 사실 주장이고,
     * 200종을 모은 사용자가 랭킹 탭을 열 때마다 `0종`을 한 프레임 본다.
     */
    @Test
    fun `둘 다 모르면 숫자를 그리지 않는다`() {
        assertNull(RankingRules.mySeasonSpeciesCount(server = null, local = null))
    }

    // 🔴 **더미 랭킹을 검사하던 테스트 5개를 지웠다**(2026-08-13, `DummyRanking.kt`와 함께).
    //    `더미 지역 랭킹의 순위가 와이어프레임과 같다`·`친구 수는 A 문서의 8명과 같다`·
    //    `더미 친구 랭킹에서 나는 4위다`·`대표 꽃이 서로 다르다`·`도감 200종 안에 있다`.
    //
    //    지운 이유는 "안 쓰니까"가 아니다. 그 5개는 **읽는 사람이 0명인 데이터**를
    //    성실하게 검증하고 있었다 — 화면 17·18·19·20은 이미 전부 `region_ranking`·
    //    `friend_ranking`·`users`에서 값을 받는데((31)·(38)), 초록불 5개가 계속 켜져 있어서
    //    **더미가 아직 화면에 연결되어 있는 것처럼 보였다.** 실제로 `구현현황_AOS.md`가
    //    `친구 목록은 더미`라고 적어 둔 채 남아 있었다.
    //
    //    ⚠️ 순위 계산 자체는 여기 위쪽 `종수 내림차순으로 정렬한다`·`동점자는 같은 순위를
    //    받고 다음은 건너뛴다`가 **입력을 일부러 뒤섞어** 검증한다. 지운 5개는 그 규칙을
    //    고정된 5·9행에 한 번 더 통과시킨 것뿐이라 빠지는 커버리지가 없다.
    //
    //    ⚠️ 더미를 다시 만들지 않는다. `가짜 사람을 보여주는 것이 미구현보다 나쁘다`
    //    (`FriendsScreen` 주석)는 판단이 그대로 유효하다.

    // ── `더 보기` (2026-08-13) ────────────────────────────────────────

    /**
     * 어떤 경우에 빨개지나: 더 보여줄 줄이 없는데 버튼을 그리면.
     *
     * 🔴 이웃이 3명인 동네에서 `4위부터 더 보기`가 뜨면 **없는 순위를 가리키는 버튼**이고
     *    눌러도 줄이 안 늘어난다 — (38)에서 지운 죽은 버튼의 그 모양이다.
     */
    @Test
    fun `남은 줄이 없으면 더 보기 순위가 없다`() {
        val ranked = RankingRules.rank(listOf(entry("a", 41), entry("b", 35), entry("c", 29)))
        assertNull(RankingRules.nextPageRank(ranked, ranked.size))
        assertNull(RankingRules.nextPageRank(ranked, RankingRules.VISIBLE_ROWS))
        assertNull(RankingRules.nextPageRank(emptyList(), 0))
    }

    @Test
    fun `더 보기는 숨은 첫 줄의 순위를 가리킨다`() {
        val ranked = RankingRules.rank((1..8).map { entry("u$it", 50 - it) })
        assertEquals(6, RankingRules.nextPageRank(ranked, 5))
        assertEquals(2, RankingRules.nextPageRank(ranked, 1))
    }

    /**
     * 어떤 경우에 빨개지나: `마지막 줄의 순위 + 1`로 세면.
     *
     * 🔴 공동 4위가 둘이면 `rank()`가 5위를 **건너뛴다** — `+1`은 `5위부터 더 보기`가
     *    되어 **존재하지 않는 순위**를 말한다. 화면에는 그냥 숫자 하나로 보여서
     *    아무도 못 본다.
     */
    @Test
    fun `동점으로 순위가 건너뛰어도 없는 순위를 말하지 않는다`() {
        // 41 / 35 / 30 / 29 / 29 / 20 → 순위 1 2 3 4 4 6
        val ranked = RankingRules.rank(
            listOf(
                entry("a", 41), entry("b", 35), entry("c", 30),
                entry("d", 29, reachedAt = 1), entry("e", 29, reachedAt = 2),
                entry("f", 20),
            ),
        )
        assertEquals(listOf(1, 2, 3, 4, 4, 6), ranked.map { it.rank })
        // 5줄을 보여줬다. 마지막 줄은 4위이므로 `+1`은 5 — 그런 순위는 없다.
        assertEquals(6, RankingRules.nextPageRank(ranked, 5))
    }

    /**
     * ⚠️ 공동 순위가 걸쳐 있으면 **이미 보인 숫자가 다시 나온다.** 그건 참이다 —
     *    그 순위에 아직 못 보여준 사람이 남아 있다.
     */
    @Test
    fun `공동 순위가 잘리면 그 순위를 다시 가리킨다`() {
        // 41 / 35 / 30 / 29 / 29 / 29 → 순위 1 2 3 4 4 4
        val ranked = RankingRules.rank(
            listOf(
                entry("a", 41), entry("b", 35), entry("c", 30),
                entry("d", 29, reachedAt = 1), entry("e", 29, reachedAt = 2),
                entry("f", 29, reachedAt = 3),
            ),
        )
        assertEquals(4, RankingRules.nextPageRank(ranked, 5))
    }

    /** A 문서 2절 17번의 예시 문구(`6위부터 더 보기`)가 이 값을 전제한다. */
    @Test
    fun `처음 보여주는 줄 수가 A문서 예시와 맞는다`() {
        assertEquals(5, RankingRules.VISIBLE_ROWS)
        val ranked = RankingRules.rank((1..10).map { entry("u$it", 50 - it) })
        assertEquals(6, RankingRules.nextPageRank(ranked, RankingRules.VISIBLE_ROWS))
    }

    // ─────────────────────────────────────────────────────────────
    // 랭킹 다시 읽기 판정 (2026-08-17)
    //
    // 🔴 **이 검사가 없어서 화면이 낡은 값을 그렸다.** 실측: 꽃을 등록한 직후 화면 17이
    //    `내 순위 -`였고, 같은 세션에서 탭을 다시 눌러도 `-`였고, **앱을 다시 켜니 `10위`**
    //    였다(에뮬레이터 · 계정 `꽃친구769c` · `삼성2동 이웃 10명`). 서버는 맞았다.
    //    `-`는 "상위 목록 밖"과 글자가 같아서 **증상으로는 정상과 구분되지 않는다.**
    // ─────────────────────────────────────────────────────────────

    /** 기본값. 아래 각 테스트는 **한 가지만** 바꾼다. */
    private fun stale(
        loading: Boolean = false,
        lastLoadAtMs: Long? = 1_000L,
        lastLoadFailed: Boolean = false,
        countAtLastLoad: Int? = 1,
        countNow: Int? = 1,
        nowMs: Long = 1_000L,
    ) = RankingRules.shouldRefreshRanking(
        loading = loading,
        lastLoadAtMs = lastLoadAtMs,
        lastLoadFailed = lastLoadFailed,
        countAtLastLoad = countAtLastLoad,
        countNow = countNow,
        nowMs = nowMs,
    )

    @Test
    fun `한 번도 안 읽었으면 읽는다`() {
        assertTrue(stale(lastLoadAtMs = null))
    }

    /**
     * 🔴 **이 앱이 실제로 틀렸던 자리다.** 앱 시작 시점에 0종으로 읽어 둔 랭킹을
     *    등록 뒤에도 그대로 쓰면 내 행이 없다. 시간은 1초도 안 지났다.
     */
    @Test
    fun `등록해서 종수가 늘면 시간이 안 지났어도 읽는다`() {
        assertTrue(stale(countAtLastLoad = 0, countNow = 1, nowMs = 1_000L))
    }

    /** 도감을 아직 읽는 중이라 몰랐다가 알게 된 것도 **변화다.** */
    @Test
    fun `종수를 몰랐다가 알게 되면 읽는다`() {
        assertTrue(stale(countAtLastLoad = null, countNow = 1))
    }

    /** 같은 종을 다시 찍으면 종수가 그대로다 — 랭킹도 그대로이므로 왕복하지 않는다. */
    @Test
    fun `종수가 같고 시간이 안 지났으면 읽지 않는다`() {
        assertFalse(stale(countAtLastLoad = 2, countNow = 2, nowMs = 1_000L + 59_999L))
    }

    @Test
    fun `낡음 경계를 넘기면 읽는다`() {
        assertEquals(60_000L, RankingRules.RANKING_STALE_AFTER_MS)
        val edge = 1_000L + RankingRules.RANKING_STALE_AFTER_MS
        assertFalse("경계 1ms 전에는 읽지 않는다", stale(nowMs = edge - 1))
        assertTrue("경계에서는 읽는다", stale(nowMs = edge))
    }

    /**
     * ⚠️ **읽는 중이면 다른 조건이 전부 참이어도 부르지 않는다.** 부르면 같은 왕복이
     *    두 벌 돌고, 늦게 온 응답이 먼저 온 응답을 덮어쓴다.
     */
    @Test
    fun `읽는 중이면 부르지 않는다`() {
        assertTrue("대조군 — 읽는 중이 아니면 읽는다", stale(lastLoadAtMs = null, countNow = 9))
        assertFalse(stale(loading = true, lastLoadAtMs = null, countNow = 9, lastLoadFailed = true))
    }

    /** 실패한 채로 탭에 들어오면 다시 시도한다 — 사용자가 버튼을 찾아 누르기 전에. */
    @Test
    fun `지난 조회가 실패했으면 읽는다`() {
        assertTrue(stale(lastLoadFailed = true))
    }
}
