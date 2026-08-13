package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 19 `검색` · 화면 16 `친구 추가`의 서버 호출([FriendService]).
 *
 * 🔴 **여기 응답 본문은 실측이 아니다.** `public_profiles`·`friendships`는 0001에
 *    이미 있지만 이 두 경로를 **한 번도 실제로 부른 적이 없다**(오너 승인 없이
 *    부르지 않는다). 그래서 이 테스트가 재는 무게는 형식 해석보다
 *    **"우리가 무엇을 어떻게 요청하는가"**(URL·필터·본문·실패 갈래)에 있다 —
 *    `ReactionServiceTest`와 같은 처지다.
 *
 * ⚠️ 각 테스트에 **어떤 경우에 빨개지나**를 적었다. 안 적으면 통과하는데 아무것도
 *    안 재는 테스트가 남는다(이 저장소에서 12번 그랬다).
 */
class FriendServiceTest {

    private val meId = "5bf714f9-b172-4636-a878-bf8efbc26fb7"
    private val otherId = "3070caaa-0d53-4b2b-a71f-e5e230c6cda7"

    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<FriendService.Transport.Response>,
    ) : FriendService.Transport {
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String?>()
        val bearers = mutableListOf<String>()
        val apiKeys = mutableListOf<String>()

        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
        ): FriendService.Transport.Response {
            urls += url
            methods += method
            bodies += body
            bearers += bearer
            apiKeys += apiKey
            return if (responses.isEmpty()) {
                FriendService.Transport.Response(500, "")
            } else {
                responses.removeAt(0)
            }
        }
    }

    private class StubAuth(
        private val token: String? = TOKEN,
        private val refreshResult: String? = FRESH,
    ) : TokenSource {
        var refreshCalls = 0
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): String? {
            refreshCalls++
            return refreshResult
        }

        companion object {
            const val TOKEN = "stub-token"
            const val FRESH = "stub-fresh-token"
        }
    }

    /**
     * ⚠️ `baseUrl`·`anonKey`를 반드시 넣는다 — 기본값은 전역 `AppSecrets`라
     *    **그 맥에 `local.properties`가 있느냐로 결과가 갈린다.**
     */
    private fun service(
        responses: MutableList<FriendService.Transport.Response>,
        auth: TokenSource = StubAuth(),
        myUserId: String? = "5bf714f9-b172-4636-a878-bf8efbc26fb7",
        transport: FakeTransport = FakeTransport(responses),
    ): Pair<FriendService, FakeTransport> =
        FriendService(
            auth = auth,
            myUserId = { myUserId },
            baseUrl = BASE,
            anonKey = KEY,
            transport = transport,
            log = { logs += it },
        ) to transport

    private fun ok(body: String) = FriendService.Transport.Response(200, body)

    // ── 검색 ─────────────────────────────────────────────────────────

    /**
     * 어떤 경우에 빨개지나: 뷰 이름·컬럼·`ilike`·`limit`이 바뀌면. 특히
     * **`limit`이 빠지면** 한 번에 전체 사용자가 내려온다([FriendRules.MIN_QUERY] 주석).
     */
    @Test
    fun 공개_프로필_뷰에_ilike로_묻고_인원을_제한한다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")))
        svc.search("효산")

        val url = t.urls.single()
        assertTrue(url, url.startsWith("$BASE/rest/v1/public_profiles?"))
        assertTrue(url, "select=id,nickname" in url)
        assertTrue(url, "limit=20" in url)
        assertTrue(url, "nickname=ilike." in url)
        assertEquals("GET", t.methods.single())
        assertEquals(KEY, t.apiKeys.single())
        assertEquals(StubAuth.TOKEN, t.bearers.single())
    }

    /**
     * 어떤 경우에 빨개지나: `*`를 URL에 그대로 붙이거나 인코딩을 빼면.
     *
     * 🔴 앞뒤 `*`가 없으면 **정확히 일치하는 닉네임만** 찾는다 — 검색이 아니라 조회가 된다.
     */
    @Test
    fun 앞뒤_와일드카드를_붙여_인코딩한다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")))
        svc.search("효산")

        val url = t.urls.single()
        // `*효산*`을 UTF-8로 인코딩한 값. 한글이 raw로 남으면 이 검사가 빨개진다.
        //
        // ⚠️ **`*`는 인코딩되지 않는다.** `URLEncoder`는 `. - * _`를 안전한 문자로 보고
        //    그대로 둔다 — 우리에게는 그게 맞다. PostgREST는 값 안의 `*`를 `%`로 읽으므로
        //    `%2A`로 보내면 **와일드카드가 아니라 별표 한 글자를 찾는다**(즉 결과가 0건).
        //    사용자가 넣은 `*`는 [FriendRules.sanitize]가 이미 지웠으니 여기 남은 둘은
        //    우리가 붙인 것뿐이다.
        assertTrue(url, "nickname=ilike.*%ED%9A%A8%EC%82%B0*" in url)
    }

    /**
     * 어떤 경우에 빨개지나: 서비스가 [FriendRules.sanitize] 결과를 **다시 안 세면**.
     *
     * 🔴 `*가*`는 세 글자로 통과하지만 지우면 한 글자가 된다 — 그대로 보내면
     *    `ilike.*가*`가 되어 **한 글자 검색이 그대로 돈다.**
     */
    @Test
    fun 지운_뒤에_다시_세서_짧으면_안_보낸다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")))
        assertEquals(FriendResult.TooShort, svc.search("*가*"))
        assertTrue("서버를 부르지 않았어야 한다", t.urls.isEmpty())
    }

    @Test
    fun 한_글자는_서버를_부르지_않는다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")))
        assertEquals(FriendResult.TooShort, svc.search("가"))
        assertTrue(t.urls.isEmpty())
    }

    /**
     * 어떤 경우에 빨개지나: 결과가 있는데 관계를 안 물으면(모든 줄이 `친구 추가`가 되고
     * 이미 친구인 사람에게 409로 실패하는 버튼이 생긴다).
     */
    @Test
    fun 결과가_있으면_내_친구_관계를_한_번_더_묻는다() = runBlocking {
        val (svc, t) = service(
            mutableListOf(
                ok("""[{"id":"$otherId","nickname":"꽃보다효산"}]"""),
                ok("""[{"requester_id":"$meId","addressee_id":"$otherId","state":"accepted"}]"""),
            ),
        )
        val res = svc.search("효산")

        assertEquals(2, t.urls.size)
        assertTrue(t.urls[1], t.urls[1].startsWith("$BASE/rest/v1/friendships?"))
        assertTrue(t.urls[1], "select=requester_id,addressee_id,state" in t.urls[1])
        val rows = (res as FriendResult.Loaded).value
        assertEquals(FriendRules.State.FRIEND, rows.single().state)
    }

    /** ⚠️ 결과가 비면 두 번째 왕복을 **안 한다** — 합칠 것이 없다. */
    @Test
    fun 결과가_없으면_관계를_묻지_않는다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")))
        val res = svc.search("효산")
        assertEquals(1, t.urls.size)
        assertTrue((res as FriendResult.Loaded).value.isEmpty())
    }

    /**
     * 어떤 경우에 빨개지나: 관계 조회 실패를 무시하고 결과를 그리면.
     *
     * 🔴 관계 없이 그리면 이미 친구인 사람에게도 `친구 추가`가 뜬다 — 그게 이 저장소가
     *    반복해 만든 `누를 수 있는데 실패하는 버튼`이다.
     */
    @Test
    fun 관계_조회가_실패하면_검색도_실패다() = runBlocking {
        val (svc, _) = service(
            mutableListOf(
                ok("""[{"id":"$otherId","nickname":"꽃보다효산"}]"""),
                FriendService.Transport.Response(500, ""),
            ),
        )
        val res = svc.search("효산")
        assertTrue(res.toString(), res is FriendResult.Failed)
        assertEquals(500, (res as FriendResult.Failed).code)
    }

    /** ⚠️ 닉네임이 null인 행은 뺀다 — 빈 이름 줄에 버튼만 남으면 누구를 추가하는지 모른다. */
    @Test
    fun 닉네임이_없는_행은_뺀다() = runBlocking {
        val (svc, _) = service(
            mutableListOf(
                ok("""[{"id":"$otherId","nickname":null}]"""),
                ok("[]"),
            ),
        )
        val res = svc.search("효산")
        assertTrue((res as FriendResult.Loaded).value.isEmpty())
    }

    /** 어떤 경우에 빨개지나: 401에 갱신 없이 포기하면(며칠 쓰면 검색이 통째로 죽는다). */
    @Test
    fun 토큰이_만료되면_한_번_갱신해_다시_묻는다() = runBlocking {
        val auth = StubAuth()
        val (svc, t) = service(
            mutableListOf(FriendService.Transport.Response(401, ""), ok("[]")),
            auth = auth,
        )
        svc.search("효산")
        assertEquals(1, auth.refreshCalls)
        assertEquals(2, t.bearers.size)
        assertEquals(StubAuth.FRESH, t.bearers[1])
    }

    @Test
    fun 키가_없으면_서버를_부르지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val svc = FriendService(
            auth = StubAuth(),
            myUserId = { meId },
            baseUrl = "",
            anonKey = "",
            transport = t,
            log = { logs += it },
        )
        assertEquals(FriendResult.NotConfigured, svc.search("효산"))
        assertTrue(t.urls.isEmpty())
    }

    // ── 친구 요청 ────────────────────────────────────────────────────

    /**
     * 어떤 경우에 빨개지나: `state`를 클라이언트가 보내면.
     *
     * 🔴 `pending`을 여기서 적으면 enum 값이 두 곳에 생기고, 서버가 기본값을 바꾸는 날
     *    **클라이언트가 옛 값을 우겨서** 상호 수락 규칙이 깨진다.
     */
    @Test
    fun 요청은_두_uuid만_보내고_상태는_서버가_채운다() = runBlocking {
        val (svc, t) = service(mutableListOf(FriendService.Transport.Response(201, "")))
        val res = svc.request(otherId)

        assertTrue(res.toString(), res is FriendResult.Loaded)
        assertEquals("POST", t.methods.single())
        assertEquals("$BASE/rest/v1/friendships", t.urls.single())
        val body = t.bodies.single()!!
        assertTrue(body, "\"requester_id\":\"$meId\"" in body)
        assertTrue(body, "\"addressee_id\":\"$otherId\"" in body)
        assertFalse(body, "state" in body)
        assertFalse(body, "pending" in body)
    }

    /**
     * 어떤 경우에 빨개지나: 이미 보낸 요청(409)을 실패로 보면 — 목록이 갱신되기 전에
     * 두 번 누른 사용자에게 `연결이 불안정해요`가 뜬다. 원하는 상태는 이미 이뤄졌다.
     */
    @Test
    fun 이미_보낸_요청은_성공으로_본다() = runBlocking {
        val (svc, _) = service(
            mutableListOf(FriendService.Transport.Response(409, """{"code":"23505"}""")),
        )
        assertTrue(svc.request(otherId) is FriendResult.Loaded)
    }

    /**
     * 어떤 경우에 빨개지나: 자기 자신에게 보내는 것을 막지 않으면. 서버
     * `friendships_no_self`가 막으므로 **눌러도 실패하는 버튼**이 된다.
     */
    @Test
    fun 자기_자신에게는_보내지_않는다() = runBlocking {
        val (svc, t) = service(mutableListOf(FriendService.Transport.Response(201, "")))
        val res = svc.request(meId)
        assertEquals(FriendService.SELF_REQUEST, (res as FriendResult.Failed).pgCode)
        assertTrue("서버를 부르지 않았어야 한다", t.urls.isEmpty())
        assertTrue(logs.any { "자기 자신" in it })
    }

    /** ⚠️ RLS 거절(42501)을 삼키지 않는다 — 요청이 저장 안 됐는데 보냈다고 말하게 된다. */
    @Test
    fun 정책_거절은_실패로_올린다() = runBlocking {
        val (svc, _) = service(
            mutableListOf(FriendService.Transport.Response(403, """{"code":"42501"}""")),
        )
        val res = svc.request(otherId) as FriendResult.Failed
        assertEquals(403, res.code)
        assertEquals("42501", res.pgCode)
    }

    /** 로그인 전(uuid 없음)에는 401이다 — 빈 `requester_id`를 보내지 않는다. */
    @Test
    fun 내_uuid를_모르면_보내지_않는다() = runBlocking {
        val (svc, t) = service(mutableListOf(ok("[]")), myUserId = null)
        assertEquals(401, (svc.request(otherId) as FriendResult.Failed).code)
        assertEquals(401, (svc.search("효산") as FriendResult.Failed).code)
        assertTrue(t.urls.isEmpty())
    }

    private companion object {
        const val BASE = "https://stub.supabase.co"
        const val KEY = "stub-anon-key"
    }
}
