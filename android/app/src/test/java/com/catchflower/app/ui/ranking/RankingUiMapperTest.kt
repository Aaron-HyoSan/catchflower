package com.catchflower.app.ui.ranking

import com.catchflower.app.data.FriendRanking
import com.catchflower.app.data.MyRegion
import com.catchflower.app.data.RankingResult
import com.catchflower.app.data.RegionRanking
import com.catchflower.app.data.model.RankEntry
import com.catchflower.app.data.model.RankedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 17·18의 **상태 판정**을 고정한다.
 *
 * 🔴 **여기가 조용히 틀리는 자리다.** 판정이 틀려도 화면은 **멀쩡한 빈 상태**로
 *    보인다 — 오류도 안 나고, 목록이 비어 있는 것 자체는 정상이기도 하다.
 *    사람이 화면을 봐서는 "아무도 안 찍어서 빈 것"과 "동네를 안 골라서 빈 것"과
 *    "네트워크가 죽어서 빈 것"이 구분되지 않는다. 그래서 테스트로 가른다.
 *
 * ⚠️ **아래 응답 조합은 실측에 근거한다.** `region_ranking`은 지역 미설정과
 *    발견 0건에 **똑같이 `[]`** 를 준다(`region_none.json` == `region_with_dong.json`).
 *    그래서 `myRegion`을 같이 넘기고, 이 테스트는 그 조합을 하나씩 짚는다.
 *    실패 응답 코드도 실측값이다(본인 확인 거부 = `400 P0001`).
 */
class RankingUiMapperTest {

    // ── 실측 기반 픽스처 ────────────────────────────────────────────

    private fun row(rank: Int, name: String, species: Int, isMe: Boolean = false) = RankedEntry(
        rank = rank,
        entry = RankEntry(
            userId = "u$rank",
            nickname = name,
            speciesCount = species,
            signatureFlowerId = 1,
            isMe = isMe,
        ),
    )

    /**
     * 지역을 정한 사용자. `region_name`은 실측대로 **세 어절 전체**다.
     *
     * 🔴 **`createdAt`을 반드시 넣는다.** 비워 두면
     *    `변경_시각을_모르면_가능일을_만들지_않는다`가 **거짓 초록이 된다** —
     *    `regionChangedAt ?: createdAt`으로 대체 계산하는 결함을 심어도 둘 다
     *    null이라 결과가 같아서 테스트가 통과한다(돌연변이 판정에서 실제로 새어 나갔다).
     */
    private fun mineSet(name: String? = "서울특별시 마포구 연남동") = RankingResult.Loaded(
        MyRegion(
            regionName = name,
            dongCode = "1144012400",
            guCode = "11440",
            // 실측: PATCH가 성공해도 서버가 **안 채운다**. 여기 값이 있다고 가정하면 안 된다.
            regionChangedAt = null,
            nickname = "꽃친구5bf7",
            createdAt = "2026-03-14T09:45:53.504149+00:00",
        ),
    )

    /** 아직 동네를 안 고른 사용자. 갓 설치한 상태다. */
    private fun mineUnset() = RankingResult.Loaded(
        MyRegion(regionName = null, dongCode = null, guCode = null, regionChangedAt = null),
    )

    /** 실측: 본인 확인 거부는 `403 42501`이 아니라 **`400 P0001`** 이다. */
    private fun mineFailed() = RankingResult.Failed(code = 400, pgCode = "P0001")

    private fun loadedRanking(
        scope: String? = "dong",
        memberCount: Int = 2,
        rows: List<RankedEntry> = emptyList(),
    ) = RankingResult.Loaded(
        RegionRanking(scope = scope, regionCode = "1144012400", memberCount = memberCount, rows = rows),
    )

    /** 실측 응답 그대로: 지역 미설정도, 발견 0건도 **`[]`** 다 → scope를 알 수 없다. */
    private fun emptyRanking() = loadedRanking(scope = null, memberCount = 0, rows = emptyList())

    // ── 빈 응답의 이유를 가른다 (이 파일의 핵심) ──────────────────────

    @Test
    fun 빈_랭킹과_지역_미설정을_가른다() {
        // 응답은 `[]` 하나뿐이고, 가르는 근거는 **myRegion밖에 없다.**
        assertEquals(
            RegionRankingUi.NoRegion,
            RankingUiMapper.region(emptyRanking(), mineUnset()),
        )
        assertEquals(
            RegionRankingUi.Empty,
            RankingUiMapper.region(emptyRanking(), mineSet()),
        )
    }

    @Test
    fun 지역_조회_실패를_미설정으로_읽지_않는다() {
        // 🔴 `Failed`를 `false`(미설정)로 뭉개면 네트워크가 죽었을 때 화면이
        //    `동네 선택하기`를 내밀고, **이미 고른 동네를 다시 고르게 만든다**
        //    (기획서 9장 6개월 규칙에 걸리는 행동이다).
        assertEquals(
            RegionRankingUi.Failed,
            RankingUiMapper.region(emptyRanking(), mineFailed()),
        )
    }

    @Test
    fun 랭킹_조회_실패는_빈_랭킹이_아니다() {
        // 지역 설정 여부를 알더라도, 랭킹을 못 불렀으면 빈 랭킹으로 그리지 않는다.
        assertEquals(
            RegionRankingUi.Failed,
            RankingUiMapper.region(RankingResult.Failed(500), mineSet()),
        )
    }

    @Test
    fun 키_없는_빌드는_실패와_다르다() {
        // 오류 문구도 `다시 시도` 버튼도 띄우면 안 된다 — 눌러도 안 되고 사용자 탓이 아니다.
        assertEquals(
            RegionRankingUi.NotConfigured,
            RankingUiMapper.region(RankingResult.NotConfigured, RankingResult.NotConfigured),
        )
    }

    @Test
    fun 지역을_못_읽어도_랭킹이_있으면_보여준다() {
        // 랭킹 행이 있으면 목록은 그린다. 지역명만 비운다 — 순위를 감출 이유가 없다.
        val ui = RankingUiMapper.region(
            loadedRanking(rows = listOf(row(1, "꽃보다효산", 13))),
            mineFailed(),
        ) as RegionRankingUi.Loaded
        assertEquals(1, ui.rows.size)
        assertEquals("", ui.regionLabel)
    }

    // ── 순위·개수를 만들어내지 않는다 ────────────────────────────────

    @Test
    fun 서버가_준_순위를_그대로_쓴다() {
        // 상위 5명만 받는 화면이라 6위만 담긴 페이지가 올 수 있다. 인덱스로 다시 세면
        // **6위가 1위로 보이고, 화면은 아무 문제 없어 보인다.**
        val ui = RankingUiMapper.region(
            loadedRanking(rows = listOf(row(6, "이웃", 9))),
            mineSet(),
        ) as RegionRankingUi.Loaded
        assertEquals(6, ui.rows.single().rank)
    }

    @Test
    fun 이웃_수는_행_개수가_아니다() {
        // 실측: `member_count`는 응답 행 수와 무관하게 따로 온다. 행으로 세면
        // 상위 5명만 받는 화면에서 `이웃 5명`이 된다.
        val ui = RankingUiMapper.region(
            loadedRanking(memberCount = 1284, rows = listOf(row(1, "가", 20), row(2, "나", 19))),
            mineSet(),
        ) as RegionRankingUi.Loaded
        assertEquals(1284, ui.memberCount)
    }

    @Test
    fun 행_순서를_바꾸지_않는다() {
        // 동점자 순서는 **서버가 정한 것**이 계약이다(먼저 도달한 쪽이 앞).
        // 클라이언트가 다시 정렬하면 서버와 다른 순서가 나오는데, 화면은 똑같이 예쁘다.
        val rows = listOf(row(1, "먼저", 13), row(2, "나중", 13))
        val ui = RankingUiMapper.region(loadedRanking(rows = rows), mineSet()) as RegionRankingUi.Loaded
        assertEquals(listOf("먼저", "나중"), ui.rows.map { it.entry.nickname })
    }

    @Test
    fun 서버가_주지_않는_순위_변동을_만들지_않는다() {
        // 실측: 서버는 `delta`를 안 준다. 0을 넣으면 화면이 `변동 없음`을 표시하는데,
        // 실제로는 **모른다**. 0과 null은 다르다.
        val ui = RankingUiMapper.region(
            loadedRanking(rows = listOf(row(1, "가", 20))),
            mineSet(),
        ) as RegionRankingUi.Loaded
        assertEquals(null, ui.rows.single().delta)
    }

    // ── B-6이 구로 넓힌 경우 ─────────────────────────────────────────

    @Test
    fun 구로_넓어지면_구명을_쓴다() {
        // 🔴 동명을 그대로 두면 `연남동 이웃 1,284명`이라고 써 놓고 **마포구 전체 순위**다.
        //    서버가 `scope`를 돌려주는 이유가 이것이다(0002 SQL 2절 주석).
        val ui = RankingUiMapper.region(
            loadedRanking(scope = "gu", rows = listOf(row(1, "가", 20))),
            mineSet(),
        ) as RegionRankingUi.Loaded
        assertTrue(ui.scopeIsGu)
        assertEquals("마포구", ui.regionLabel)
    }

    @Test
    fun 동_단위면_동명을_쓴다() {
        val ui = RankingUiMapper.region(
            loadedRanking(scope = "dong", rows = listOf(row(1, "가", 20))),
            mineSet(),
        ) as RegionRankingUi.Loaded
        assertFalse(ui.scopeIsGu)
        assertEquals("연남동", ui.regionLabel)
    }

    @Test
    fun 구명을_못_찾으면_비운다() {
        // `구`로 끝나는 어절이 없는 지역(세종·군·시)도 있다. 그때 아무 어절이나
        // 집으면 **`서울특별시 이웃 1,284명`** 같은 거짓말이 된다.
        val ui = RankingUiMapper.region(
            loadedRanking(scope = "gu", rows = listOf(row(1, "가", 20))),
            mineSet("세종특별자치시 조치원읍"),
        ) as RegionRankingUi.Loaded
        assertEquals("", ui.regionLabel)
    }

    @Test
    fun 지역명이_없으면_기본값을_넣지_않는다() {
        // `연남동` 같은 기본값을 넣으면 **남의 동네 이름을 내 화면에 박는다.**
        val ui = RankingUiMapper.region(
            loadedRanking(rows = listOf(row(1, "가", 20))),
            mineSet(name = null),
        ) as RegionRankingUi.Loaded
        assertEquals("", ui.regionLabel)
    }

    // ── 화면 18 친구 ─────────────────────────────────────────────────

    @Test
    fun 순위에_오른_친구_수는_친구_수와_다르다() {
        // 실측: 이번 시즌 발견이 0건인 친구는 **행이 아예 없다.** 나는 행에 있지만
        // 친구로 세지 않는다 — (18)에서 7 vs 8로 겪은 사고다.
        val ui = RankingUiMapper.friends(
            RankingResult.Loaded(
                FriendRanking(
                    rows = listOf(row(1, "친구", 20), row(2, "나", 13, isMe = true)),
                ),
            ),
        ) as FriendRankingUi.Loaded
        assertEquals(1, ui.rankedFriendCount)
        assertEquals(2, ui.rows.size)
    }

    @Test
    fun 친구_랭킹_실패는_빈_랭킹이_아니다() {
        assertEquals(FriendRankingUi.Failed, RankingUiMapper.friends(RankingResult.Failed(500)))
        assertEquals(
            FriendRankingUi.NotConfigured,
            RankingUiMapper.friends(RankingResult.NotConfigured),
        )
    }

    @Test
    fun 응답이_오기_전에는_초대_화면을_띄우지_않는다() {
        // 🔴 로딩 중에 띄우면 친구가 8명인 사용자도 화면을 열 때마다
        //    `아직 겨룰 친구가 없어요`를 먼저 본다.
        assertFalse(RankingUiMapper.showInvite(FriendRankingUi.Loading, 0))
        assertFalse(RankingUiMapper.showInvite(FriendRankingUi.Failed, 0))
        assertFalse(RankingUiMapper.showInvite(FriendRankingUi.NotConfigured, 0))
    }

    @Test
    fun 친구가_적으면_초대_화면을_띄운다() {
        // 와이어프레임 18 주석 ④ `친구 0~2명일 때`. **2명은 포함이고 3명은 아니다.**
        assertTrue(RankingUiMapper.showInvite(loadedFriends(), friendCount = 0))
        assertTrue(RankingUiMapper.showInvite(loadedFriends(), friendCount = 2))
        assertFalse(RankingUiMapper.showInvite(loadedFriends(), friendCount = 3))
    }

    // ── 친구 수 (화면 18·20이 같은 값을 읽는다) ──────────────────────

    /**
     * 🔴 **랭킹에 오른 친구 수로 초대 화면을 띄우면 안 된다.**
     *
     * 친구 8명이 전부 이번 시즌에 아직 안 찍었으면 랭킹 행에는 나만 있다
     * (실측: 발견 0건인 친구는 행이 없다). 그 값으로 세면 `아직 겨룰 친구가
     * 없어요` + `초대 링크 보내기`가 뜬다 — **이미 친구인 8명을 다시 초대하라는
     * 화면**이다.
     *
     * 빨개지는 경우: `showInvite`가 `rankedFriendCount`를 보면.
     */
    @Test
    fun 아무도_안_찍었어도_친구가_있으면_초대_화면이_아니다() {
        // 랭킹에는 나 혼자 (rankedFriendCount = 0), 실제 친구는 8명.
        val onlyMe = RankingUiMapper.friends(
            RankingResult.Loaded(FriendRanking(rows = listOf(row(1, "나", 3, isMe = true)))),
        ) as FriendRankingUi.Loaded
        assertEquals("랭킹에 오른 친구", 0, onlyMe.rankedFriendCount)
        assertFalse(
            "친구 8명에게 `초대 링크 보내기`를 내밀었다",
            RankingUiMapper.showInvite(onlyMe, friendCount = 8),
        )
    }

    /**
     * 🔴 **친구 수를 못 세면 초대 화면을 띄우지 않는다.**
     *
     * 헤더가 안 왔거나 조회가 실패한 경우다. 띄우면 친구가 있는 사용자가
     * **받아온 랭킹 대신** 초대 화면을 본다 — 랭킹은 실제로 있는 데이터인데
     * 숫자 하나를 못 받은 이유로 감추는 것이 된다.
     */
    @Test
    fun 친구_수를_모르면_초대_화면을_띄우지_않는다() {
        assertFalse(RankingUiMapper.showInvite(loadedFriends(), friendCount = null))
    }

    /** 모르는 것은 **`null`이고 0이 아니다.** 0은 "친구가 없다"는 사실 주장이다. */
    @Test
    fun 친구_수를_모르면_0으로_만들지_않는다() {
        assertNull(RankingUiMapper.friendCount(RankingResult.Failed(500)))
        assertNull(RankingUiMapper.friendCount(RankingResult.NotConfigured))
        assertNull("파싱 실패도 모르는 것이다", RankingUiMapper.friendCount(
            RankingResult.Failed(200, "PARSE"),
        ))
    }

    @Test
    fun 서버가_센_친구_수를_그대로_쓴다() {
        assertEquals(8, RankingUiMapper.friendCount(RankingResult.Loaded(8)))
        // 0은 **아는 값**이다 — 진짜 친구가 없는 것이라 초대 화면이 맞다.
        assertEquals(0, RankingUiMapper.friendCount(RankingResult.Loaded(0)))
        assertTrue(RankingUiMapper.showInvite(loadedFriends(), friendCount = 0))
    }

    private fun loadedFriends() =
        FriendRankingUi.Loaded(rows = emptyList(), rankedFriendCount = 0)

    // ── 화면 20 프로필 ───────────────────────────────────────────────
    //
    // 여기 값들은 **틀려도 화면이 완벽하게 정상으로 보인다.** 더미를 읽던 때는
    // `꽃보다효산 · 연남동 · 2026년 3월부터 함께`가 항상 예쁘게 있었다.

    @Test
    fun 서버가_준_프로필을_그대로_쓴다() {
        val p = RankingUiMapper.profile(mineSet())
        assertEquals("서울특별시 마포구 연남동", p.regionFull)
        assertEquals("연남동", p.dongName)
    }

    /**
     * 🔴 **못 받은 프로필에 기본값을 넣지 않는다.**
     *
     * `꽃보다효산`·`연남동`은 더미 상수다. 넣으면 **남의 닉네임과 동네가 내
     * 프로필에 박히고**, 화면은 정상으로 보인다.
     */
    @Test
    fun 프로필을_못_받으면_기본값을_넣지_않는다() {
        val failed = RankingUiMapper.profile(RankingResult.Failed(500))
        assertNull(failed.nickname)
        assertNull(failed.regionFull)
        assertNull(failed.dongName)
        assertNull(failed.joinedLabel)
        // 키 없는 빌드도 같다.
        assertNull(RankingUiMapper.profile(RankingResult.NotConfigured).nickname)
    }

    /** 지역만 안 고른 상태가 **기본값**이다(`handle_new_user`는 닉네임만 넣는다). */
    @Test
    fun 지역만_없는_프로필도_닉네임은_보여준다() {
        val mine = RankingResult.Loaded(
            MyRegion(
                regionName = null, dongCode = null, guCode = null, regionChangedAt = null,
                nickname = "꽃친구5bf7", createdAt = "2026-03-14T09:45:53.504149+00:00",
            ),
        )
        val p = RankingUiMapper.profile(mine)
        assertEquals("꽃친구5bf7", p.nickname)
        assertNull("지역을 안 골랐는데 지역명을 만들어 냈다", p.regionFull)
        assertEquals("2026년 3월부터 함께", p.joinedLabel)
    }

    /** 실측한 `created_at` 형태 그대로. 마이크로초 6자리 + `+00:00`이다. */
    @Test
    fun 가입월을_실측_형식에서_읽는다() {
        assertEquals(
            "2026년 8월부터 함께",
            RankingUiMapper.joinedLabel("2026-08-08T09:45:53.504149+00:00"),
        )
        // 🔴 `03` → `3`. 앞의 0을 남기면 `2026년 03월부터 함께`가 된다.
        assertEquals(
            "2026년 3월부터 함께",
            RankingUiMapper.joinedLabel("2026-03-01T00:00:00+00:00"),
        )
    }

    /**
     * 🔴 **가입일을 모르면 오늘 날짜를 넣지 않는다.**
     *
     * 3월에 가입한 사용자에게 `2026년 8월부터 함께`라고 말하는 것이고,
     * **화면은 완벽하게 정상으로 보인다.**
     */
    @Test
    fun 가입일을_모르면_문구를_만들지_않는다() {
        assertNull(RankingUiMapper.joinedLabel(null))
        assertNull(RankingUiMapper.joinedLabel(""))
        // 형식이 바뀌었다. 억지로 읽으면 엉뚱한 달이 나온다.
        assertNull(RankingUiMapper.joinedLabel("08/08/2026"))
        assertNull("`2026-13`을 13월로 읽었다", RankingUiMapper.joinedLabel("2026-13-01T00:00:00Z"))
    }

    // ── 화면 20 활동 지역 변경 가능일 ────────────────────────────────

    /**
     * 🔴 **서버가 `region_changed_at`을 안 채우면 이 줄을 쓰지 않는다.**
     *
     * 실측: `users` PATCH가 성공해도 이 컬럼은 null이다(채우는 트리거가 없다).
     * 그런데 날짜를 만들어 내면 `2027. 2. 4. 부터 가능`을 못 박아 놓고
     * **근거가 없는 상태**가 된다. 사용자가 그날 와서 안 되면 우리가 거짓말한 것이다.
     *
     * 빨개지는 경우: `?: 가입일 + 6개월` 같은 대체 계산을 넣으면.
     */
    @Test
    fun 변경_시각을_모르면_가능일을_만들지_않는다() {
        assertNull(RankingUiMapper.regionChangeLabel(null))
        assertNull(RankingUiMapper.regionChangeLabel(""))
        // 🔴 **가입일이 있어도 마찬가지다 — 가입은 "바꾼 것"이 아니다.**
        //    [mineSet]은 `createdAt`이 있고 `regionChangedAt`은 null이다. 여기서
        //    `?: createdAt`으로 대신 계산하면 `2026. 9. 14. 부터 가능`이 나오는데,
        //    **한 번도 안 바꾼 사용자는 지금 바꿀 수 있어야 한다** — 갓 가입한
        //    사용자가 6개월간 동네를 못 고르게 막힌다.
        val p = RankingUiMapper.profile(mineSet())
        assertEquals("픽스처 전제", "2026년 3월부터 함께", p.joinedLabel)
        assertNull("가입일로 변경 가능일을 계산했다", p.regionChangeLabel)
    }

    @Test
    fun 마지막_변경일에_6개월을_더한다() {
        assertEquals(
            "2027. 2. 4. 부터 가능",
            RankingUiMapper.regionChangeLabel("2026-08-04T09:45:53.504149+00:00"),
        )
    }

    /** 🔴 12를 넘기면 **해가 넘어간다.** `13월`이 나오면 안 된다. */
    @Test
    fun 여섯달을_더해_해가_넘어가도_13월이_되지_않는다() {
        assertEquals(
            "2027. 1. 15. 부터 가능",
            RankingUiMapper.regionChangeLabel("2026-07-15T00:00:00+00:00"),
        )
        // 12월 + 6 = 다음 해 6월.
        assertEquals(
            "2027. 6. 1. 부터 가능",
            RankingUiMapper.regionChangeLabel("2026-12-01T00:00:00+00:00"),
        )
    }

    @Test
    fun 변경일_형식이_바뀌면_문구를_만들지_않는다() {
        assertNull(RankingUiMapper.regionChangeLabel("2026-08"))
        assertNull(RankingUiMapper.regionChangeLabel("2026/08/04"))
        assertNull(RankingUiMapper.regionChangeLabel("2026-00-04T00:00:00Z"))
    }
}
