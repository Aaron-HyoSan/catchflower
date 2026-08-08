package com.catchflower.app.data

import com.catchflower.app.data.model.RankEntry

/**
 * 랭킹·친구·프로필 더미. 와이어프레임 17~21의 숫자를 그대로 만든다.
 *
 * ⚠️ **화면 검증용이고 게임 규칙이 아니다.** 서버(A-2 Supabase)가 붙으면 통째로 지운다.
 *    정책 판단은 여기 넣지 않는다.
 *    (발견 기록 더미 `DummyDiscoveries`는 실제 저장([DiscoveryRepository])이 붙어서
 *     지웠다. 랭킹은 다른 사용자의 데이터라 서버 없이는 대체할 수 없어 남아 있다.)
 *
 * ⚠️ 순위를 **미리 매겨 두지 않는다.** 와이어프레임의 `1위 41종`을 그대로 박으면
 *    정렬·동점 처리가 틀려도 화면이 맞게 보인다. 종수만 주고
 *    [com.catchflower.app.data.model.RankingRules]가 순위를 계산하게 한다.
 */
object DummyRanking {

    /** 화면 17·20의 `연남동`. 로그인(화면 02 지역 선택)이 붙으면 사용자 값이다. */
    const val MY_DONG = "연남동"

    /** 화면 20 `서울특별시 마포구 연남동`. */
    const val MY_REGION_FULL = "서울특별시 마포구 연남동"

    /** 화면 18 `나 (꽃보다효산)`. */
    const val MY_NICKNAME = "꽃보다효산"

    /** 화면 17 목록 라벨 `연남동 이웃 1,284명`. */
    const val NEIGHBOR_COUNT = 1_284

    /** 화면 17 `내 순위 21위` — 상위 5명만 더미로 두므로 내 순위는 값으로 준다. */
    const val MY_REGION_RANK = 21

    /** 화면 17 `▲ 3`. */
    const val MY_REGION_RANK_DELTA = 3

    /** 화면 17 `3종만 더 모으면 15위권!`의 목표 순위. */
    const val REGION_TARGET_RANK = 15

    /** 화면 17 `3종만 더` — 상위 5명 데이터로는 15위권 종수를 알 수 없어 값으로 둔다. */
    const val REGION_SPECIES_TO_TARGET = 3

    /** 화면 20 지표 3칸. 시즌 초기화와 무관한 **영구 수치**(주석 ②). */
    const val TOTAL_SPECIES = 37
    const val TOTAL_DISCOVERIES = 112
    const val TOTAL_SHARES = 26

    /** 화면 20 `2026년 3월부터 함께`. */
    const val JOINED_LABEL = "2026년 3월부터 함께"

    /** 화면 20 활동 지역 변경 가능일. 6개월 제한(기획서 9장). */
    const val REGION_CHANGE_AVAILABLE = "2027. 2. 4. 부터 가능"

    /** 화면 20·21 대표 칭호. */
    const val MY_TITLE = "우리 동네 꽃박사"

    /**
     * 화면 17 상위 5명.
     *
     * 대표 꽃은 와이어프레임이 전부 `장미`로 그렸지만(썸네일 반복) 여기서는 **다르게** 준다 —
     * 전부 같으면 대표 꽃이 행마다 제대로 연결되는지 화면으로 확인할 수 없다.
     */
    fun regionTop(): List<RankEntry> = listOf(
        RankEntry("u1", "연남동꽃선생", speciesCount = 41, signatureFlowerId = 81, title = "꽃길 개척자", totalDiscoveries = 210, reachedAt = 1L),
        RankEntry("u2", "산책하는날", speciesCount = 38, signatureFlowerId = 2, totalDiscoveries = 180, reachedAt = 2L),
        RankEntry("u3", "봄이오면", speciesCount = 35, signatureFlowerId = 13, totalDiscoveries = 150, reachedAt = 3L),
        RankEntry("u4", "효산맘", speciesCount = 31, signatureFlowerId = 44, totalDiscoveries = 120, reachedAt = 4L),
        RankEntry("u5", "성미산둘레", speciesCount = 29, signatureFlowerId = 5, totalDiscoveries = 100, reachedAt = 5L),
    )

    /**
     * 화면 18 친구 랭킹. **나를 포함한다** — 친구 8명 + 나 = 9행.
     *
     * 와이어프레임은 1위 연남댁 38종, 나 13종(4위)이다.
     * 종수만 주고 순위는 규칙이 계산한다.
     *
     * ⚠️ 친구 수는 **8명**이어야 한다. A 문서가 화면 18 `친구 8명과 겨루는 중`,
     *    화면 20 `친구 관리 8명`으로 두 화면에 같은 숫자를 쓴다. 7명으로 두면
     *    두 화면이 조용히 어긋나고, 화면만 봐서는 어느 쪽이 맞는지 알 수 없다.
     *    [FRIEND_COUNT]가 이 숫자를 고정한다.
     */
    fun friends(): List<RankEntry> = listOf(
        RankEntry("f1", "연남댁", speciesCount = 38, signatureFlowerId = 81, totalDiscoveries = 190, reachedAt = 1L),
        RankEntry("f2", "효산맘", speciesCount = 31, signatureFlowerId = 44, totalDiscoveries = 130, reachedAt = 2L),
        RankEntry("f3", "성수산책", speciesCount = 29, signatureFlowerId = 2, totalDiscoveries = 110, reachedAt = 3L),
        RankEntry("me", MY_NICKNAME, speciesCount = 13, signatureFlowerId = 1, title = MY_TITLE, isMe = true, totalDiscoveries = TOTAL_DISCOVERIES, reachedAt = 4L),
        RankEntry("f4", "봄이오면", speciesCount = 11, signatureFlowerId = 13, totalDiscoveries = 60, reachedAt = 5L),
        RankEntry("f5", "산책하는날", speciesCount = 9, signatureFlowerId = 5, totalDiscoveries = 40, reachedAt = 6L),
        RankEntry("f6", "우동꽃길", speciesCount = 6, signatureFlowerId = 26, totalDiscoveries = 25, reachedAt = 7L),
        RankEntry("f7", "제주댁", speciesCount = 4, signatureFlowerId = 34, totalDiscoveries = 15, reachedAt = 8L),
        RankEntry("f8", "동네한바퀴", speciesCount = 2, signatureFlowerId = 60, totalDiscoveries = 8, reachedAt = 9L),
    )

    /** A 문서가 화면 18·20에 같이 쓰는 `8명`. [friends]와 어긋나면 테스트가 깨진다. */
    const val FRIEND_COUNT = 8

    /** 화면 19 `연락처에서 찾은 친구` — 이미 가입한 사람. 즉시 추가할 수 있다. */
    fun contactsOnService(): List<Contact> = listOf(
        Contact("김영희", "01021234567"),
        Contact("박순자", "01095678901"),
        Contact("이정미", "01039012345"),
    )

    /** 화면 19 `아직 가입하지 않은 지인` — 초대 대상. */
    fun contactsToInvite(): List<Contact> = listOf(
        Contact("최말순", "01044443333"),
        Contact("정해경", "01055552222"),
        Contact("윤보라", "01066661111"),
    )

    /**
     * 화면 20 `내 배지` 3개.
     *
     * MVP 배지 종류는 업무목록 B-9에서 확정한다(와이어프레임 20 주석 ③) —
     * 여기 3개는 와이어프레임에 그려진 것뿐이고 **배지 체계가 아니다.**
     */
    fun badges(): List<Badge> = listOf(
        Badge("봄꽃 수집가", "2026 S1"),
        Badge("우리 동네 꽃박사", "2026 S1"),
        Badge("첫 발견", "2026.3"),
    )

    data class Contact(val name: String, val phone: String)

    data class Badge(val name: String, val periodLabel: String)
}
