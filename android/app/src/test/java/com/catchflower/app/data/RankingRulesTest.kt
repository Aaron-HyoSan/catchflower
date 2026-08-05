package com.catchflower.app.data

import com.catchflower.app.data.model.RankEntry
import com.catchflower.app.data.model.RankingRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `더미 지역 랭킹의 순위가 와이어프레임과 같다`() {
        // 더미에는 순위를 박아 두지 않았다. 규칙이 계산한 결과가 와이어프레임(41·38·35·31·29)과
        // 같은지 **여기서** 확인한다 — 화면에서는 확인할 수 없는 항목이다.
        val ranked = RankingRules.rank(DummyRanking.regionTop())
        assertEquals(listOf(1, 2, 3, 4, 5), ranked.map { it.rank })
        assertEquals(listOf(41, 38, 35, 31, 29), ranked.map { it.entry.speciesCount })
        assertEquals("연남동꽃선생", ranked.first().entry.nickname)
    }

    @Test
    fun `친구 수는 A 문서의 8명과 같다`() {
        // ⚠️ A 문서가 화면 18 `친구 8명과 겨루는 중`과 화면 20 `친구 관리 8명`에
        //    같은 숫자를 쓴다. 더미가 7명이면 두 화면이 조용히 어긋난다 —
        //    화면만 봐서는 어느 쪽이 맞는지 알 수 없어서 실제로 놓쳤다.
        val friendsOnly = DummyRanking.friends().filterNot { it.isMe }
        assertEquals(DummyRanking.FRIEND_COUNT, friendsOnly.size)
        assertEquals(8, DummyRanking.FRIEND_COUNT)
        // 나는 정확히 한 명이다. 둘이면 `나 (...)` 행이 두 개 나온다.
        assertEquals(1, DummyRanking.friends().count { it.isMe })
    }

    @Test
    fun `더미 친구 랭킹에서 나는 4위다`() {
        // 와이어프레임 18: 1위 연남댁 38종, 나 13종.
        val ranked = RankingRules.rank(DummyRanking.friends())
        val me = ranked.first { it.entry.isMe }
        assertEquals(4, me.rank)
        assertEquals(13, me.entry.speciesCount)
        assertEquals("연남댁", ranked.first().entry.nickname)
        assertEquals(38, ranked.first().entry.speciesCount)
    }

    @Test
    fun `더미 친구 랭킹의 대표 꽃이 서로 다르다`() {
        // 와이어프레임은 전부 장미로 그렸다. 전부 같으면 썸네일 연결이 깨져도
        // 화면으로 확인할 수 없어서 일부러 다르게 뒀다 — 그 의도를 고정한다.
        val ids = DummyRanking.friends().map { it.signatureFlowerId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `더미 대표 꽃은 모두 도감 200종 안에 있다`() {
        // 범위를 벗어나면 화면에서 썸네일 자리가 플레이스홀더로 바뀐다 —
        // 그건 "데이터가 이상하다"가 아니라 "디자인이 그렇다"로 오해된다.
        val ids = (DummyRanking.regionTop() + DummyRanking.friends()).map { it.signatureFlowerId }
        ids.forEach { id ->
            assertEquals("도감번호 $id 가 1..200 밖이다", true, id in 1..200)
        }
    }
}
