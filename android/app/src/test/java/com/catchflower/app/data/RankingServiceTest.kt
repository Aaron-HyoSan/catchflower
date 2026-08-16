package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버 랭킹 응답 해석.
 *
 * **왜 테스트가 필요한가.** 랭킹이 틀리면 **예쁘게 틀린다.** (18)에서 이미 겪었다 —
 * 순위를 인덱스에서 뽑아서 정렬이 깨져도 화면은 `1 2 3 4 5`로 나왔다. 서버로 옮긴
 * 지금은 더 나쁘다: **남의 데이터라 눈으로 검산할 방법이 아예 없다.**
 * `3위 32종`이 맞는지 틀리는지 기기 앞에서는 알 수 없다.
 *
 * ⚠️ **아래 응답 본문은 전부 실제 Supabase가 준 것을 그대로 옮긴 것이다**(진행.md (31)).
 *    익명 계정 3개로 실측했다. 내가 상상한 형식으로 쓰면 **테스트만 통과하고
 *    앱은 안 된다** — (22)에서 실제로 그랬다.
 */
class RankingServiceTest {

    private val meId = "5bf714f9-b172-4636-a878-bf8efbc26fb7"
    private val otherId = "3070caaa-0d53-4b2b-a71f-e5e230c6cda7"

    private val logs = mutableListOf<String>()

    /** 실측 응답을 정해 두고, 무엇을 어떻게 물었는지 기록한다. */
    private class FakeTransport(
        private val responses: MutableList<RankingService.Transport.Response>,
    ) : RankingService.Transport {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String?>()
        val bearers = mutableListOf<String>()
        val apiKeys = mutableListOf<String>()

        /** `countOnly`로 부른 호출을 기록한다 — 개수 조회가 헤더 방식인지 고정한다. */
        val countOnlyFlags = mutableListOf<Boolean>()

        override suspend fun send(
            url: String,
            apiKey: String,
            bearer: String,
            body: String?,
            countOnly: Boolean,
        ): RankingService.Transport.Response {
            urls += url
            bodies += body
            bearers += bearer
            apiKeys += apiKey
            countOnlyFlags += countOnly
            return if (responses.isEmpty()) {
                RankingService.Transport.Response(500, "")
            } else {
                responses.removeAt(0)
            }
        }
    }

    private class StubAuth(
        private val token: String? = TOKEN,
        private val refreshResult: String? = FRESH,
        /** 내 uuid. 지역 랭킹의 `is_me`를 앱이 붙이므로 필요하다(`RankingService.regionRanking`). */
        private val id: String = "5bf714f9-b172-4636-a878-bf8efbc26fb7",
        /** uuid를 못 얻는 경우(가입 실패·비설정)를 재현한다. */
        private val idThrows: Boolean = false,
    ) : AuthAccount {
        var refreshCalls = 0
        var userIdCalls = 0
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): String? {
            refreshCalls++
            return refreshResult
        }

        override suspend fun userId(): String {
            userIdCalls++
            if (idThrows) throw IllegalStateException("uuid를 모른다")
            return id
        }

        override fun needsReauth(): Boolean = false
        override fun reset() = Unit

        companion object {
            const val TOKEN = "stub-token"
            const val FRESH = "stub-fresh-token"
        }
    }

    /**
     * ⚠️ `baseUrl`·`anonKey`를 반드시 넣는다 — 기본값은 전역 `AppSecrets`라
     *    **그 맥에 `local.properties`가 있느냐로 통과 여부가 갈린다.**
     * ⚠️ `log`도 갈아 끼운다 — `android.util.Log`는 JVM에서 던진다.
     */
    private fun service(
        vararg responses: Pair<Int, String>,
        auth: StubAuth = StubAuth(),
    ): Pair<FakeTransport, RankingService> =
        build(responses.map { (code, body) -> res(code, body) }, auth)

    /**
     * 개수 조회용. 본문이 아니라 **`Content-Range` 헤더**를 정해 준다.
     *
     * ⚠️ 위 [service]와 나눈 이유: 개수 조회는 본문에 답이 없다(실측:
     *    `Range: 0-0`이라 본문은 최대 한 행). `Pair<Int, String>`으로는
     *    **헤더가 없는 응답과 있는 응답을 구분해 줄 수 없다.**
     */
    private fun countingService(
        vararg responses: RankingService.Transport.Response,
        auth: StubAuth = StubAuth(),
    ): Pair<FakeTransport, RankingService> = build(responses.toList(), auth)

    /** 실측 헤더 형태 그대로. `total`이 null이면 헤더를 아예 안 준 경우다. */
    private fun res(code: Int, body: String = "", range: String = "") =
        RankingService.Transport.Response(code = code, body = body, contentRange = range)

    private fun build(
        responses: List<RankingService.Transport.Response>,
        auth: StubAuth,
    ): Pair<FakeTransport, RankingService> {
        val t = FakeTransport(responses.toMutableList())
        return t to RankingService(
            auth = auth,
            baseUrl = "https://test.supabase.co",
            anonKey = "anon-key",
            transport = t,
            log = { logs += it },
        )
    }

    // ────────────────────────────────────────────────────────────────
    // 실측 응답 (진행.md (31))
    // ────────────────────────────────────────────────────────────────

    /** 두 계정 · A 1종 · B 2종 → B가 1위. `min_members: 1`로 동 단위. */
    private val regionTwoUsers = """
        [{"scope":"dong","region_code":"1144012400","member_count":2,"rank":1,
          "user_id":"$otherId","nickname":"꽃친구3070","species_count":2,
          "top_flower_id":2,"top_flower":"진달래"},
         {"scope":"dong","region_code":"1144012400","member_count":2,"rank":2,
          "user_id":"$meId","nickname":"꽃친구5bf7","species_count":1,
          "top_flower_id":1,"top_flower":"개나리"}]
    """.trimIndent()

    /** 동점(둘 다 2종). A가 먼저 도달했으므로 **A(나)가 1위**로 왔다. */
    private val regionTie = """
        [{"scope":"dong","region_code":"1144012400","member_count":2,"rank":1,
          "user_id":"$meId","nickname":"꽃친구5bf7","species_count":2,
          "top_flower_id":1,"top_flower":"개나리"},
         {"scope":"dong","region_code":"1144012400","member_count":2,"rank":2,
          "user_id":"$otherId","nickname":"꽃친구3070","species_count":2,
          "top_flower_id":2,"top_flower":"진달래"}]
    """.trimIndent()

    /** B-6: 기본 `min_members` 10에서 2명이면 **구 단위로 넓어진다.** */
    private val regionGuScope = """
        [{"scope":"gu","region_code":"11440","member_count":2,"rank":1,
          "user_id":"$otherId","nickname":"꽃친구3070","species_count":2,
          "top_flower_id":2,"top_flower":"진달래"}]
    """.trimIndent()

    private val friendMe = """
        [{"rank":1,"user_id":"$meId","nickname":"꽃친구5bf7","species_count":2,
          "top_flower_id":1,"top_flower":"개나리","is_me":true}]
    """.trimIndent()

    private val summaryEmpty = """
        [{"season_start":"2026-02-28T15:00:00+00:00","season_end":"2026-08-31T15:00:00+00:00",
          "is_dormant":false,"species_count":0,"discovery_count":0,
          "place_count":0,"rare_count":0}]
    """.trimIndent()

    /** `assert_self` 거부. **HTTP 400 · `P0001`이다** — RLS의 42501이 아니다. */
    private val assertSelfBody =
        """{"code":"P0001","details":null,"hint":null,"message":"본인 것만 조회할 수 있다"}"""

    /** 함수가 없거나 인자 이름이 틀렸을 때. **HTTP 404 · `PGRST202`.** */
    private val notDeployedBody =
        """{"code":"PGRST202","message":"Could not find the function public.region_ranking(nope) in the schema cache"}"""

    // ────────────────────────────────────────────────────────────────
    // 🔴 순위는 서버가 준 값이다
    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **서버가 준 `rank`를 그대로 쓴다.**
     *
     * 빨개지는 경우: 인덱스로 순위를 다시 매기면. 지금은 순서가 같아서 통과하지만
     * **상위 5명만 받는 페이지에서는 6위인 사람이 1위가 된다.** (18)에서 당한 결함과
     * 같은 것이고, 그때는 화면상 `1 2 3 4 5`로 예쁘게 나왔다.
     */
    @Test
    fun 순위는_서버가_준_값을_쓴다() = runBlocking<Unit> {
        // 1위 행을 빼고 2위 행만 준다 — 페이지 뒤쪽을 받은 상황이다.
        val secondPageOnly = """
            [{"scope":"dong","region_code":"1144012400","member_count":9,"rank":6,
              "user_id":"$meId","nickname":"꽃친구5bf7","species_count":3,
              "top_flower_id":1,"top_flower":"개나리"}]
        """.trimIndent()
        val (_, s) = service(200 to secondPageOnly)
        val r = s.regionRanking() as RankingResult.Loaded
        assertEquals(
            "순위를 다시 세면 6위가 1위가 된다 — 화면은 예쁘게 나온다",
            6,
            r.value.rows.single().rank,
        )
    }

    /**
     * 동점 순서를 서버가 정한 대로 쓴다.
     *
     * 빨개지는 경우: 클라이언트가 `RankingRules`로 다시 정렬하면. 서버는
     * **먼저 도달한 사람**을 위로 두는데(실측 확인) 클라이언트에는 `reachedAt`이
     * **오지 않아서 전부 0**이다 — 다시 정렬하면 `userId` 문자열 순서가 되어
     * **같은 데이터에 두 순위**가 생긴다.
     */
    @Test
    fun 동점_순서를_바꾸지_않는다() = runBlocking<Unit> {
        val (_, s) = service(200 to regionTie)
        val rows = (s.regionRanking() as RankingResult.Loaded).value.rows
        assertEquals("서버가 준 순서가 뒤집혔다", listOf(meId, otherId), rows.map { it.entry.userId })
        assertEquals(listOf(1, 2), rows.map { it.rank })
        // 동점인데 다른 순위를 준 게 아니라, 서버가 정한 순서를 그대로 옮긴 것이다.
        assertEquals(listOf(2, 2), rows.map { it.entry.speciesCount })
    }

    /**
     * 🔴 **서버가 주지 않는 값을 만들어내지 않는다.**
     *
     * 빨개지는 경우: `delta`(지난 시즌 대비 `▲ 3`)에 0이나 아무 값을 넣으면.
     * 화면 17이 `▲ 0`을 그리고, 사용자는 **순위가 안 변했다는 정보로 읽는다** —
     * 실제로는 우리가 모르는 것이다. `reachedAt`·`totalDiscoveries`도 서버가
     * 안 보낸다(0002 5절: "내용은 한 줄도 내보내지 않는다").
     */
    @Test
    fun 서버가_주지_않는_값을_만들지_않는다() = runBlocking<Unit> {
        val (_, s) = service(200 to regionTwoUsers)
        val row = (s.regionRanking() as RankingResult.Loaded).value.rows.first()
        assertNull("모르는 순위 변동을 만들어냈다 — 화면이 ▲0을 그린다", row.delta)
        assertEquals("서버가 안 주는 도달시각을 만들어냈다", 0L, row.entry.reachedAt)
        assertEquals(0, row.entry.totalDiscoveries)
    }

    // ────────────────────────────────────────────────────────────────
    // 🔴 빈 목록과 실패를 가른다 — 이 층의 핵심이다
    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **`[]`는 성공이다. 실패가 아니다.**
     *
     * 빨개지는 경우: 빈 배열을 오류로 만들면. 아무도 아직 안 찍은 동네에서
     * **정상인데 오류 화면**이 뜬다.
     */
    @Test
    fun 빈_랭킹은_성공이다() = runBlocking<Unit> {
        val (_, s) = service(200 to "[]")
        val r = s.regionRanking()
        assertTrue("빈 랭킹을 실패로 봤다: $r", r is RankingResult.Loaded)
        assertEquals(0, (r as RankingResult.Loaded).value.rows.size)
    }

    /**
     * 🔴 **서버를 못 부른 것을 빈 랭킹으로 보여주면 안 된다.**
     *
     * 빨개지는 경우: 실패에 빈 목록을 돌려주면. 화면이 `아직 겨룰 친구가 없어요`를
     * 띄우는데 실제로는 **네트워크가 죽은 것**이다. 사용자는 친구가 사라졌다고 읽고,
     * 우리는 로그 없이 구분할 수 없다.
     */
    @Test
    fun 조회_실패는_빈_랭킹과_다르다() = runBlocking<Unit> {
        for (code in listOf(500, 503, 0)) {
            val (_, s) = service(code to "")
            val r = s.friendRanking()
            assertTrue("HTTP $code 를 빈 랭킹으로 만들었다: $r", r is RankingResult.Failed)
        }
    }

    /**
     * 🔴 **응답 형식이 바뀐 것도 실패다.**
     *
     * 빨개지는 경우: 파싱 실패에 빈 목록을 돌려주면. 서버 함수의 반환 컬럼이 바뀌면
     * 화면이 조용히 "이웃이 없어요"가 되고 **아무도 배포 사고를 모른다.**
     */
    @Test
    fun 형식이_바뀌면_실패다() = runBlocking<Unit> {
        // `rank`가 없는 행 — 컬럼 이름이 바뀐 상황이다.
        val (_, s) = service(200 to """[{"user_id":"$meId","nickname":"x","species_count":1}]""")
        val r = s.regionRanking()
        assertTrue("형식 변경을 빈 랭킹으로 삼켰다: $r", r is RankingResult.Failed)
        assertEquals(RankingService.PARSE_FAILED, (r as RankingResult.Failed).pgCode)
        assertTrue("조용히 넘어갔다 — 흔적이 없다", logs.isNotEmpty())
    }

    /** JSON이 아예 아닌 응답(프록시 HTML 등)도 같은 취급이다. */
    @Test
    fun JSON이_아니면_실패다() = runBlocking<Unit> {
        val (_, s) = service(200 to "<html>502 Bad Gateway</html>")
        assertTrue(s.friendRanking() is RankingResult.Failed)
    }

    // ────────────────────────────────────────────────────────────────
    // 🔴 지역 미설정과 "이웃이 없다"를 가른다
    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **행이 없으면 `scope`를 모른다 — `'none'`으로 단정하지 않는다.**
     *
     * 실측: `dong_code`가 없을 때와, 있지만 발견이 0건일 때의 응답이
     * **둘 다 똑같이 `[]`** 였다(`region_none.json` == `region_with_dong.json`).
     * `scope`는 행 안에 실려 오므로 행이 없으면 알 수 없다.
     *
     * 빨개지는 경우: 빈 응답에 `scope = "none"`을 채우면. 화면이 "동네를 안 골랐어요"로
     * 단정하는데 실제로는 **골랐고 아직 아무도 안 찍은 것**이거나 그 반대다.
     * 후자는 사용자가 **할 일이 있는데 안 알려주는** 상태다.
     */
    @Test
    fun 행이_없으면_지역_범위를_모른다() = runBlocking<Unit> {
        val (_, s) = service(200 to "[]")
        val r = (s.regionRanking() as RankingResult.Loaded).value
        assertNull("빈 응답에서 scope를 만들어냈다 — 지역 미설정과 구분할 수 없다", r.scope)
        assertNull(r.regionCode)
        assertEquals(0, r.memberCount)
    }

    /** 행이 있으면 그 행의 `scope`를 읽는다. B-6이 구로 넓힌 결과가 그대로 온다. */
    @Test
    fun 구_단위로_넓어진_것을_읽는다() = runBlocking<Unit> {
        val (_, s) = service(200 to regionGuScope)
        val r = (s.regionRanking() as RankingResult.Loaded).value
        assertEquals("B-6 구 확장을 못 읽었다", "gu", r.scope)
        assertEquals("11440", r.regionCode)
    }

    /**
     * `member_count`는 **가입자 수가 아니라 이번 시즌 발견이 있는 사람 수**다.
     *
     * 빨개지는 경우: 행 개수로 세면. 실측에서 2계정·2행에 `member_count 2`라
     * 지금은 같아 보이지만, **상위 5명만 받으면 이웃 1,284명이 5명이 된다.**
     */
    @Test
    fun 이웃_수는_행_개수가_아니다() = runBlocking<Unit> {
        val onlyTopRow = """
            [{"scope":"dong","region_code":"1144012400","member_count":1284,"rank":1,
              "user_id":"$otherId","nickname":"꽃친구3070","species_count":41,
              "top_flower_id":81,"top_flower":"장미"}]
        """.trimIndent()
        val (_, s) = service(200 to onlyTopRow)
        val r = (s.regionRanking() as RankingResult.Loaded).value
        assertEquals("행 개수로 이웃 수를 셌다 — 1,284명이 1명이 된다", 1284, r.memberCount)
        assertEquals(1, r.rows.size)
    }

    /** `min_members`(B-6)를 안 넘기면 payload에 넣지 않는다 — 서버 기본값 10을 쓴다. */
    @Test
    fun 최소_인원을_안_주면_서버_기본값에_맡긴다() = runBlocking<Unit> {
        val (t, s) = service(200 to "[]")
        s.regionRanking()
        assertFalse(
            "안 정한 값을 보냈다 — 서버 기본값(계약 3절 10명)을 덮는다",
            JSONObject(t.bodies.single()!!).has("min_members"),
        )
    }

    @Test
    fun 최소_인원을_주면_그대로_보낸다() = runBlocking<Unit> {
        val (t, s) = service(200 to "[]")
        s.regionRanking(minMembers = 1)
        assertEquals(1, JSONObject(t.bodies.single()!!).getInt("min_members"))
    }

    // ────────────────────────────────────────────────────────────────
    // 친구 랭킹
    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **친구 랭킹의 행 수는 "친구 수"가 아니다.**
     *
     * 실측: 발견이 0건이면 응답이 `[]`다 — **나조차 안 나온다.** 서버 `scored` CTE가
     * `discoveries`를 조인하기 때문이다.
     *
     * 빨개지는 경우: 이걸로 A 문서의 `친구 8명과 겨루는 중`을 만들면. 아직 안 찍은
     * 친구가 빠져서 **화면 18과 화면 20의 `친구 관리 8명`이 서로 다른 숫자**가 된다 —
     * (18)에서 이미 7 vs 8로 당한 사고다.
     */
    @Test
    fun 순위에_오른_친구_수는_친구_수와_다르다() = runBlocking<Unit> {
        val (_, s) = service(200 to friendMe)
        val r = (s.friendRanking() as RankingResult.Loaded).value
        assertEquals("나만 있는데 친구가 있다고 셌다", 0, r.rankedFriendCount)
        assertEquals(meId, r.me?.entry?.userId)
    }

    /** 발견이 0건이면 나조차 없다 — 그때 `me`는 null이고, 그건 정상이다. */
    @Test
    fun 발견이_없으면_내_행도_없다() = runBlocking<Unit> {
        val (_, s) = service(200 to "[]")
        val r = (s.friendRanking() as RankingResult.Loaded).value
        assertNull("없는 내 행을 만들어냈다", r.me)
    }

    @Test
    fun 나를_표시한다() = runBlocking<Unit> {
        val (_, s) = service(200 to friendMe)
        val r = (s.friendRanking() as RankingResult.Loaded).value
        assertTrue("is_me를 못 읽었다 — 리스트에서 내 행 강조가 사라진다", r.rows.single().entry.isMe)
    }

    /**
     * 어떤 경우에 빨개지나: 지역 랭킹에서 **`is_me`만 믿으면.**
     *
     * 🔴 위 `regionTwoUsers`는 **실측 응답 그대로**다 — `is_me` 칸이 아예 없다.
     *    서버 `region_ranking`(0002)의 `returns table`에 그 칸이 없기 때문이다
     *    (`friend_ranking`에만 있다). 그래서 예전 판은 지역 랭킹의 내 행을 **한 번도**
     *    표시하지 못했고, 화면 17 `내 순위`가 **1위인 사람에게도 `-`**였다.
     *    실측(2026-08-17 · 릴리스 빌드): 내가 1종인데 `내 순위 -`, 같은 1종 이웃은 5위.
     *    `-`는 "상위 목록 밖"과 글자가 같아서 **화면으로는 절대 구분이 안 된다.**
     */
    @Test
    fun 지역_랭킹은_서버가_is_me를_안_줘도_내_행을_찾는다() = runBlocking<Unit> {
        val (_, s) = service(200 to regionTwoUsers)
        val r = (s.regionRanking(minMembers = 1) as RankingResult.Loaded).value
        assertFalse("실측 응답에 `is_me`가 있으면 이 테스트는 아무것도 안 잰다",
            regionTwoUsers.contains("is_me"))
        val mine = r.rows.single { it.entry.isMe }
        assertEquals(meId, mine.entry.userId)
        assertEquals("내 순위를 서버가 준 값 그대로 써야 한다", 2, mine.rank)
        assertEquals("내가 아닌 행을 나로 표시했다", 1, r.rows.count { it.entry.isMe })
    }

    /** uuid 대소문자가 달라도 같은 사람이다. `==`로 비교하면 조용히 `-`가 된다. */
    @Test
    fun 대문자_uuid도_내_행이다() = runBlocking<Unit> {
        val (_, s) = build(
            listOf(res(200, regionTwoUsers.replace(meId, meId.uppercase()))),
            StubAuth(),
        )
        val r = (s.regionRanking(minMembers = 1) as RankingResult.Loaded).value
        assertTrue("대문자 uuid를 남으로 봤다", r.rows.any { it.entry.isMe })
    }

    /**
     * 어떤 경우에 빨개지나: uuid를 못 얻었을 때 **랭킹을 아예 실패로 만들면.**
     *
     * 내 표시가 없는 것보다 목록 자체가 사라지는 것이 나쁘다 — 이웃 순위는 남의 값이라
     * 내 uuid 없이도 다 그릴 수 있다.
     */
    @Test
    fun uuid를_못_얻어도_지역_랭킹은_나온다() = runBlocking<Unit> {
        val (_, s) = build(listOf(res(200, regionTwoUsers)), StubAuth(idThrows = true))
        val r = (s.regionRanking(minMembers = 1) as RankingResult.Loaded).value
        assertEquals(2, r.rows.size)
        assertFalse("모르는데 나라고 표시했다", r.rows.any { it.entry.isMe })
    }

    // ────────────────────────────────────────────────────────────────
    // 시즌 요약
    // ────────────────────────────────────────────────────────────────

    /**
     * 실측: 발견이 0건이어도 **행 1개**가 온다(시즌 경계는 항상 있다).
     *
     * 빨개지는 경우: 빈 요약을 실패로 보면. 갓 시작한 사용자의 마이페이지가 오류가 된다.
     */
    @Test
    fun 기록이_없어도_요약은_온다() = runBlocking<Unit> {
        val (_, s) = service(200 to summaryEmpty)
        val v = (s.seasonSummary() as RankingResult.Loaded).value
        assertEquals(0, v.speciesCount)
        assertEquals(0, v.discoveryCount)
        assertFalse(v.isDormant)
        assertEquals("2026-02-28T15:00:00+00:00", v.seasonStart)
    }

    /** 요약에 행이 하나도 없으면 그건 형식이 바뀐 것이다 — 0으로 채우지 않는다. */
    @Test
    fun 요약_행이_없으면_실패다() = runBlocking<Unit> {
        val (_, s) = service(200 to "[]")
        val r = s.seasonSummary()
        assertTrue("없는 요약을 0으로 만들어냈다: $r", r is RankingResult.Failed)
    }

    // ────────────────────────────────────────────────────────────────
    // 인증 · 실패 형태 (전부 실측)
    // ────────────────────────────────────────────────────────────────

    /**
     * **anon 키가 아니라 사용자 토큰으로 부른다.**
     *
     * 빨개지는 경우: `Authorization`에 anon 키를 넣으면. `assert_self`가
     * **HTTP 400 `P0001`로 거부한다**(실측) — 랭킹이 통째로 안 나온다.
     */
    @Test
    fun 사용자_토큰으로_부른다() = runBlocking<Unit> {
        val (t, s) = service(200 to "[]")
        s.regionRanking()
        assertEquals("anon 키로 부르면 assert_self가 400 P0001로 막는다", StubAuth.TOKEN, t.bearers.single())
        assertEquals("apikey 헤더는 anon 키다", "anon-key", t.apiKeys.single())
    }

    /** 401은 갱신해서 한 번 더. [DiscoveryUploader]와 같은 이유다. */
    @Test
    fun 토큰이_만료되면_갱신해서_다시_부른다() = runBlocking<Unit> {
        val auth = StubAuth()
        val (t, s) = service(401 to """{"code":"PGRST301"}""", 200 to friendMe, auth = auth)
        val r = s.friendRanking()
        assertTrue("$r", r is RankingResult.Loaded)
        assertEquals("두 번 부르지 않았다", 2, t.urls.size)
        assertEquals(1, auth.refreshCalls)
        assertEquals("재시도에 갱신된 토큰을 쓰지 않았다", StubAuth.FRESH, t.bearers[1])
    }

    @Test
    fun 갱신이_실패하면_실패로_남긴다() = runBlocking<Unit> {
        val (_, s) = service(401 to "", auth = StubAuth(refreshResult = null))
        val r = s.friendRanking()
        assertTrue("$r", r is RankingResult.Failed)
        assertEquals(401, (r as RankingResult.Failed).code)
    }

    /** 토큰이 아예 없으면 부르지 않는다 — anon으로 부르면 400을 맞을 뿐이다. */
    @Test
    fun 토큰이_없으면_부르지_않는다() = runBlocking<Unit> {
        val (t, s) = service(200 to "[]", auth = StubAuth(token = null, refreshResult = null))
        val r = s.regionRanking()
        assertTrue("$r", r is RankingResult.Failed)
        assertEquals("토큰 없이 서버를 불렀다", 0, t.urls.size)
    }

    /**
     * `assert_self` 거부를 **그대로 알아볼 수 있게 남긴다**(실측 HTTP 400 · `P0001`).
     *
     * 빨개지는 경우: pg 코드를 버리면. 로그에 `HTTP 400`만 남아서
     * **"anon 키로 불렀다"와 "인자를 잘못 넣었다"를 구분할 수 없다.**
     */
    @Test
    fun 본인_확인_거부를_알아본다() = runBlocking<Unit> {
        val (_, s) = service(400 to assertSelfBody)
        val r = s.regionRanking() as RankingResult.Failed
        assertEquals(400, r.code)
        assertEquals("P0001", r.pgCode)
    }

    /**
     * 함수가 배포되지 않았거나 인자 이름이 틀렸을 때 — 실측 **HTTP 404 · `PGRST202`**.
     *
     * 빨개지는 경우: 404를 "빈 랭킹"으로 보면. 서버 함수를 배포하지 않은 상태가
     * **정상 화면과 구별되지 않는다.**
     */
    @Test
    fun 함수가_없으면_실패로_알아본다() = runBlocking<Unit> {
        val (_, s) = service(404 to notDeployedBody)
        val r = s.regionRanking()
        assertTrue("404를 빈 랭킹으로 삼켰다: $r", r is RankingResult.Failed)
        assertEquals("PGRST202", (r as RankingResult.Failed).pgCode)
    }

    /** 엔드포인트를 실제로 RPC로 부른다 — 테이블 조회로 잘못 부르면 404다. */
    @Test
    fun RPC_엔드포인트로_부른다() = runBlocking<Unit> {
        val (t, s) = service(200 to "[]")
        s.regionRanking()
        assertTrue("엔드포인트가 틀렸다: ${t.urls.single()}", t.urls.single().endsWith("/rest/v1/rpc/region_ranking"))
    }

    /** 키가 없는 빌드에서는 서버를 부르지 않고 오류도 아니다. */
    @Test
    fun 키가_없으면_부르지_않는다() = runBlocking<Unit> {
        val t = FakeTransport(mutableListOf())
        val s = RankingService(StubAuth(), baseUrl = "", anonKey = "", transport = t, log = { })
        assertEquals(RankingResult.NotConfigured, s.regionRanking())
        assertEquals("키가 없는데 서버를 불렀다", 0, t.urls.size)
    }

    // ────────────────────────────────────────────────────────────────
    // 내 활동 지역
    // ────────────────────────────────────────────────────────────────

    /** 실측한 `users` PATCH 응답 형태와 같다. */
    private val usersRow = """
        [{"id":"$meId","nickname":"꽃친구5bf7","region_name":"서울특별시 마포구 연남동",
          "dong_code":"1144012400","gu_code":"11440","region_changed_at":null,
          "created_at":"2026-08-08T09:45:53.504149+00:00","deleted_at":null}]
    """.trimIndent()

    @Test
    fun 활동_지역을_읽는다() = runBlocking<Unit> {
        val (t, s) = service(200 to usersRow)
        val r = (s.myRegion() as RankingResult.Loaded).value
        assertTrue(r.isSet)
        assertEquals("1144012400", r.dongCode)
        assertEquals("서울특별시 마포구 연남동", r.regionName)
        // 화면 17은 동만 쓰고 화면 20은 전체를 쓴다.
        assertEquals("연남동", r.dongName)
        // 본인 행만 오면 되므로 RLS에 맡기고 필터를 걸지 않는다. GET이다.
        assertNull("지역 조회를 POST로 보냈다", t.bodies.single())
    }

    /**
     * 🔴 **지역을 안 정한 상태를 알아낼 수 있어야 한다.**
     *
     * 실측: `handle_new_user` 트리거는 `(id, nickname)`만 넣으므로 **모든 계정이
     * 이 상태로 시작한다.** 이게 화면 17이 비는 진짜 이유다.
     *
     * 빨개지는 경우: null `dong_code`를 "설정됨"으로 보면. 화면이 이웃 랭킹을
     * 기다리는데 서버는 영원히 `[]`를 준다.
     */
    @Test
    fun 지역_미설정을_알아본다() = runBlocking<Unit> {
        val fresh = """[{"id":"$meId","nickname":"꽃친구5bf7","region_name":null,
                        "dong_code":null,"gu_code":null,"region_changed_at":null}]""".trimIndent()
        val (_, s) = service(200 to fresh)
        val r = (s.myRegion() as RankingResult.Loaded).value
        assertFalse("지역을 안 정했는데 정했다고 봤다", r.isSet)
        assertNull(r.dongName)
    }

    /** `users` 행 자체가 없으면 트리거 사고다 — 같은 화면으로 보내되 로그를 남긴다. */
    @Test
    fun 내_행이_없으면_기록을_남긴다() = runBlocking<Unit> {
        val (_, s) = service(200 to "[]")
        val r = (s.myRegion() as RankingResult.Loaded).value
        assertFalse(r.isSet)
        assertTrue("조용히 넘어갔다 — handle_new_user 사고를 아무도 모른다", logs.isNotEmpty())
    }

    // ────────────────────────────────────────────────────────────────
    // 친구 수 (화면 18 `친구 {N}명과 겨루는 중` · 화면 20 `친구 관리 {N}명`)
    //
    // 실측 근거는 `friendships_*.json` 9개. 여기 테스트는 전부
    // **틀려도 화면이 멀쩡한** 사고를 막는다 — 숫자가 하나 틀린 것뿐이라
    // 앱을 봐서는 맞는지 알 수 없다.
    // ────────────────────────────────────────────────────────────────

    /**
     * 실측: `Prefer: count=exact` + `Range: 0-0` → `Content-Range: 0-0/1`.
     * `/` 뒤가 총 개수다.
     */
    @Test
    fun 친구_수를_헤더에서_읽는다() = runBlocking<Unit> {
        // 본문에는 행이 **한 개뿐**이다(Range: 0-0). 그런데 총계는 8이다.
        val body = """[{"requester_id":"$meId"}]"""
        val (t, s) = countingService(res(200, body, range = "0-0/8"))
        assertEquals(RankingResult.Loaded(8), s.friendCount())
        // 개수 조회로 물었는지 — 안 그러면 헤더가 아예 안 온다.
        assertTrue("count=exact로 묻지 않았다", t.countOnlyFlags.single())
        assertNull("개수 조회를 POST로 보냈다", t.bodies.single())
    }

    /**
     * 🔴 **본문 행을 세면 안 된다.**
     *
     * 빨개지는 경우: `parseTotal` 대신 `JSONArray(body).length()`를 쓰면.
     * `Range: 0-0` 때문에 본문은 한 행이라 **친구가 8명이어도 1명**이 되고,
     * 오류는 안 난다. 화면 18이 `친구 1명과 겨루는 중`을 태연히 그린다.
     */
    @Test
    fun 본문_행_수를_친구_수로_쓰지_않는다() = runBlocking<Unit> {
        val body = """[{"requester_id":"$meId"}]"""
        val (_, s) = countingService(res(200, body, range = "0-0/8"))
        assertEquals("본문 행을 셌다", RankingResult.Loaded(8), s.friendCount())
    }

    /**
     * 🔴 **`state=eq.accepted`를 URL에서 빼면 안 된다.**
     *
     * 실측: 필터가 없으면 `pending`(내가 요청만 보낸 사이)까지 온다
     * (`friendships_pending.json` 1행 vs `friendships_accepted_filter_pending.json` 0행).
     * 빼면 화면 18이 **아직 수락도 안 한 사람을 "겨루는 중"으로 센다.**
     */
    @Test
    fun 수락된_관계만_센다() = runBlocking<Unit> {
        val (t, s) = countingService(res(200, "[]", range = "0-0/0"))
        s.friendCount()
        val url = t.urls.single()
        assertTrue("pending까지 세는 URL이다: $url", url.contains("state=eq.accepted"))
        assertTrue("friendships가 아니다: $url", url.contains("/rest/v1/friendships"))
    }

    /**
     * ⚠️ **관계 하나는 행 하나다.** 실측으로 A·B가 **같은 1행**을 받는다
     * (`friendships_accepted_a.json` == `friendships_accepted_b.json`).
     * 그래서 `requester_id=eq.나`나 `or=(...)`를 붙이면 안 된다 —
     * 방향별로 합치면 **한 친구를 두 번 센다.**
     */
    @Test
    fun 방향별로_두_번_세지_않는다() = runBlocking<Unit> {
        val (t, s) = countingService(res(200, "[]", range = "0-0/1"))
        assertEquals(RankingResult.Loaded(1), s.friendCount())
        val url = t.urls.single()
        assertFalse("방향 조건을 걸었다 — RLS가 이미 내 것만 준다: $url", url.contains("or="))
        assertFalse("요청자 필터를 걸었다: $url", url.contains("requester_id=eq"))
        assertFalse("수신자 필터를 걸었다: $url", url.contains("addressee_id=eq"))
        assertEquals("한 관계를 두 번 물었다", 1, t.urls.size)
    }

    /** 친구가 정말 0명일 때. 실측: `friendships_none.json` = `[]` · 총계 0. */
    @Test
    fun 친구가_없으면_0이다() = runBlocking<Unit> {
        val (_, s) = countingService(res(200, "[]", range = "0-0/0"))
        assertEquals(RankingResult.Loaded(0), s.friendCount())
    }

    /**
     * 🔴 **헤더가 없으면 0이 아니라 실패다.**
     *
     * 0은 "친구가 없다"는 뜻이고 화면 18은 그걸로 **초대 화면을 전체로 띄운다.**
     * 친구 8명인 사용자가 `아직 겨룰 친구가 없어요`를 보는 사고다.
     *
     * 빨개지는 경우: `parseTotal`이 `?: 0`을 하면.
     */
    @Test
    fun 개수_헤더가_없으면_0으로_읽지_않는다() = runBlocking<Unit> {
        val (_, s) = countingService(res(200, "[]", range = ""))
        val r = s.friendCount()
        assertTrue("헤더가 없는데 개수를 만들어 냈다: $r", r is RankingResult.Failed)
        assertEquals(RankingService.PARSE_FAILED, (r as RankingResult.Failed).pgCode)
    }

    /** `*`은 "총계를 안 셌다"는 뜻이다. 이것도 0이 아니다. */
    @Test
    fun 총계를_안_준_헤더도_0이_아니다() = runBlocking<Unit> {
        val (_, s) = countingService(res(200, "[]", range = "0-0/*"))
        assertTrue("`*`을 0으로 읽었다", s.friendCount() is RankingResult.Failed)
    }

    /**
     * 실측: anon 키로 조회하면 RLS가 막아 **`200 []`**이 온다(오류가 아니다).
     * 우리 코드는 사용자 토큰으로 부르므로 이 경우가 나오면 안 되지만,
     * 나오더라도 **0명이 아니라는 걸 구분할 수단은 없다** — 그래서 토큰을
     * 쓰는지를 여기서 고정한다.
     */
    @Test
    fun 사용자_토큰으로_묻는다() = runBlocking<Unit> {
        val (t, s) = countingService(res(200, "[]", range = "0-0/0"))
        s.friendCount()
        assertEquals("anon 키로 물었다 — RLS가 막아 조용히 0명이 된다",
            StubAuth.TOKEN, t.bearers.single())
        assertEquals("anon-key", t.apiKeys.single())
    }

    /** 토큰이 만료되면 갱신해서 한 번 더. 다른 조회와 같은 규칙이다. */
    @Test
    fun 개수_조회도_토큰을_갱신한다() = runBlocking<Unit> {
        val auth = StubAuth()
        val (t, s) = countingService(
            res(401, """{"code":"PGRST301"}"""),
            res(200, "[]", range = "0-0/3"),
            auth = auth,
        )
        assertEquals(RankingResult.Loaded(3), s.friendCount())
        assertEquals(1, auth.refreshCalls)
        assertEquals(StubAuth.FRESH, t.bearers.last())
        // 두 번째 호출도 개수 조회여야 한다 — 아니면 헤더가 안 와서 실패한다.
        assertTrue("갱신 후 호출이 개수 조회가 아니다", t.countOnlyFlags.all { it })
    }

    /** 서버가 죽으면 0명이 아니다. */
    @Test
    fun 개수_조회_실패는_0명이_아니다() = runBlocking<Unit> {
        val (_, s) = countingService(res(500, ""))
        assertTrue(s.friendCount() is RankingResult.Failed)
    }

    /** 키 없는 빌드에서는 묻지 않는다 — 화면 20이 `친구 관리`를 아예 안 그린다. */
    @Test
    fun 키가_없으면_친구_수를_묻지_않는다() = runBlocking<Unit> {
        val t = FakeTransport(mutableListOf())
        val s = RankingService(StubAuth(), baseUrl = "", anonKey = "", transport = t, log = { })
        assertEquals(RankingResult.NotConfigured, s.friendCount())
        assertEquals(0, t.urls.size)
    }

    /**
     * 🔴 **랭킹 행 수와 친구 수는 다른 숫자다.**
     *
     * 실측: `friend_ranking`은 이번 시즌 발견이 0건인 친구를 **행으로 주지 않는다.**
     * 같은 응답에서 두 숫자가 갈리는 것을 한 테스트로 못박는다 —
     * 여기가 (18)에서 7 vs 8 사고가 났던 자리다.
     */
    @Test
    fun 랭킹_행_수와_친구_수는_다른_숫자다() = runBlocking<Unit> {
        // 친구는 8명인데 이번 시즌 찍은 친구는 1명(+나)뿐인 상황.
        val onePosted = """
            [{"rank":1,"user_id":"$otherId","nickname":"꽃친구3070","species_count":2,
              "top_flower_id":2,"top_flower":"진달래","is_me":false},
             {"rank":2,"user_id":"$meId","nickname":"꽃친구5bf7","species_count":1,
              "top_flower_id":1,"top_flower":"개나리","is_me":true}]
        """.trimIndent()
        val (_, ranked) = service(200 to onePosted)
        val onRanking = (ranked.friendRanking() as RankingResult.Loaded).value.rankedFriendCount
        val (_, counted) = countingService(res(200, "[]", range = "0-0/8"))
        val real = (counted.friendCount() as RankingResult.Loaded).value
        assertEquals("랭킹에 오른 친구", 1, onRanking)
        assertEquals("실제 친구", 8, real)
    }
}
