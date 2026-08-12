package com.catchflower.app.data

import com.catchflower.app.core.GamePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 16 반응(좋아요·댓글·신고) 층.
 *
 * 🔴 **여기 응답 본문은 실측이 아니다.** `0007_likes_comments.sql`이 아직 적용되지 않아
 *    한 번도 못 불렀다 — `RankingServiceTest`의 본문들은 전부 실제 Supabase가 준 것이지만
 *    이 파일은 아니다. 그래서 **이 테스트가 재는 것은 "서버가 이렇게 주면 우리가 이렇게
 *    읽는다"가 아니라 "우리가 무엇을 어떻게 요청하는가"** 쪽에 무게가 있다(URL·메서드·
 *    필터·본문). 형식 해석은 0007 적용 후 실측으로 다시 고정한다.
 *
 * ⚠️ **"이게 어떤 경우에 빨개지나"를 각 테스트에 적었다.** 안 적으면 통과하는데 아무것도
 *    안 재는 테스트가 남는다 — 이 저장소에서 12번 그랬다.
 */
class ReactionServiceTest {

    private val meId = "5bf714f9-b172-4636-a878-bf8efbc26fb7"
    private val otherId = "3070caaa-0d53-4b2b-a71f-e5e230c6cda7"
    private val discoveryId = "8a1e0c22-9b4d-4d1e-9d2a-2f0b8c7e5a11"

    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<ReactionService.Transport.Response>,
    ) : ReactionService.Transport {
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
        ): ReactionService.Transport.Response {
            urls += url
            methods += method
            bodies += body
            bearers += bearer
            apiKeys += apiKey
            return if (responses.isEmpty()) {
                ReactionService.Transport.Response(500, "")
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
     *    **그 맥에 `local.properties`가 있느냐로 테스트 결과가 갈린다.**
     */
    private fun service(
        transport: FakeTransport,
        auth: TokenSource = StubAuth(),
        userId: String? = meId,
        // 🔵 **`now: Long`을 지웠다 (2026-08-12).** PATCH 시절 `deleted_at`에 넣을
        //    기기 시계였고, 0009의 RPC는 서버 `now()`가 채운다. 안 쓰는 인자를 남기면
        //    다음 사람이 "이 층은 시각을 주입한다"고 읽는다(`읽는 사람이 0명인 데이터`).
    ) = ReactionService(
        auth = auth,
        myUserId = { userId },
        baseUrl = BASE,
        anonKey = ANON,
        transport = transport,
        log = { logs += it },
    )

    private fun ok(body: String) = ReactionService.Transport.Response(200, body)
    private fun created() = ReactionService.Transport.Response(201, "")

    // ── 반응 개수 ────────────────────────────────────────────────────

    /**
     * 빨개지는 경우: `returns table`을 스칼라로 읽으려 하거나, 인자 이름을 `discovery_id`로
     * 되돌리거나(→ 실서버에서 `PGRST202`), 함수명을 바꿨을 때.
     */
    @Test
    fun 반응_세_칸을_한_번에_받는다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("""[{"like_count":12,"comment_count":3,"liked_by_me":true}]""")))
        val res = service(t).reactions(discoveryId)

        val v = (res as ReactionResult.Loaded).value
        assertEquals(12, v.likeCount)
        assertEquals(3, v.commentCount)
        assertTrue(v.likedByMe)

        // 왕복 한 번. 두 번이 되면 "한 번에 받는다"가 깨진 것이다.
        assertEquals(1, t.urls.size)
        assertEquals("$BASE/rest/v1/rpc/discovery_reactions", t.urls[0])
        assertEquals("POST", t.methods[0])
        // 🔴 인자 이름은 서버 시그니처가 정한다(`d_id`).
        assertTrue(t.bodies[0]!!.contains(""""d_id":"$discoveryId""""))
        // anon 키가 아니라 사용자 토큰으로 부른다 — RLS 전부가 여기 달려 있다.
        assertEquals(StubAuth.TOKEN, t.bearers[0])
        assertEquals(ANON, t.apiKeys[0])
    }

    /**
     * 빨개지는 경우: 파싱 실패를 `Reactions(0,0,false)`로 삼키게 바꿨을 때.
     * **0을 돌려주면 화면이 "아무도 안 눌렀다"가 되고 서버가 바뀐 걸 아무도 모른다.**
     */
    @Test
    fun 형식이_바뀌면_0이_아니라_실패다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("""[{"likes":12}]""")))
        val res = service(t).reactions(discoveryId)

        val f = res as ReactionResult.Failed
        assertEquals(200, f.code)
        assertEquals(ReactionService.PARSE_FAILED, f.pgCode)
        assertTrue(logs.any { it.contains("읽을 수 없다") })
    }

    /**
     * 🔴 **칸 하나만 이름이 바뀌어도 실패여야 한다.**
     *
     * 위 [형식이_바뀌면_0이_아니라_실패다]는 **세 칸이 전부 없는** 응답이라
     * `comment_count`의 `getInt`가 던져서 통과했다 — 즉 `like_count`를 `optInt`로
     * 느슨하게 읽어도 그 테스트는 **초록으로 남는다**(돌연변이로 실측했다).
     * 그러면 서버가 칸 하나만 개명했을 때 화면이 `좋아요 0 · 댓글 3`이 되고,
     * **댓글 수는 맞으니까 조회는 성공한 것처럼 보인다.** 한 칸씩 빼서 다 확인한다.
     */
    @Test
    fun 칸_하나만_없어도_실패다() = runBlocking {
        val full = mapOf(
            "like_count" to "12",
            "comment_count" to "3",
            "liked_by_me" to "true",
        )
        for (missing in full.keys) {
            val body = full.filterKeys { it != missing }
                .entries.joinToString(",") { (k, v) -> """"$k":$v""" }
            val t = FakeTransport(mutableListOf(ok("[{$body}]")))
            val res = service(t).reactions(discoveryId)
            assertTrue(
                "$missing 칸이 없는데 실패가 아니다 — 화면이 조용히 0을 보여준다",
                res is ReactionResult.Failed,
            )
        }
    }

    /** 빨개지는 경우: 빈 배열에 기본값을 만들어 넣게 바꿨을 때. */
    @Test
    fun 행이_없으면_실패다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("[]")))
        val res = service(t).reactions(discoveryId)
        assertEquals(ReactionService.PARSE_FAILED, (res as ReactionResult.Failed).pgCode)
    }

    /**
     * 빨개지는 경우: 0007이 아직 없는 서버에 대고 실패를 성공/0으로 바꿨을 때.
     *
     * ⚠️ **이 응답이 지금 실서버의 상태다** — `0007`이 적용되지 않았으므로
     *    `PGRST202`가 온다. 그게 "반응 0"으로 보이면 화면이 다 붙은 것처럼 보인다.
     */
    @Test
    fun 배포되지_않은_서버는_실패로_남는다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ReactionService.Transport.Response(
                    404,
                    """{"code":"PGRST202","message":"Could not find the function public.discovery_reactions"}""",
                ),
            ),
        )
        val res = service(t).reactions(discoveryId)
        val f = res as ReactionResult.Failed
        assertEquals(404, f.code)
        assertEquals("PGRST202", f.pgCode)
        assertTrue(logs.any { it.contains("PGRST202") })
    }

    // ── 좋아요 ──────────────────────────────────────────────────────

    /**
     * 빨개지는 경우: `user_id`를 본문에서 뺐을 때. 서버가 `auth.uid()`로 채워 주지
     * 않으므로 **`23502 not null` 위반**이 된다.
     */
    @Test
    fun 좋아요는_내_uuid를_본문에_넣는다() = runBlocking {
        val t = FakeTransport(mutableListOf(created()))
        val res = service(t).like(discoveryId)

        assertTrue(res is ReactionResult.Loaded)
        assertEquals("$BASE/rest/v1/likes", t.urls[0])
        assertEquals("POST", t.methods[0])
        assertTrue(t.bodies[0]!!.contains(""""user_id":"$meId""""))
        assertTrue(t.bodies[0]!!.contains(""""discovery_id":"$discoveryId""""))
    }

    /**
     * 빨개지는 경우: 중복 삽입을 실패로 되돌렸을 때.
     * **다른 기기에서 이미 누른 사용자가 `연결이 불안정해요`를 읽게 된다.**
     */
    @Test
    fun 이미_누른_좋아요는_성공으로_본다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ReactionService.Transport.Response(
                    409,
                    """{"code":"23505","message":"duplicate key value violates unique constraint \"likes_pkey\""}""",
                ),
            ),
        )
        val res = service(t).like(discoveryId)
        assertTrue(res is ReactionResult.Loaded)
    }

    /**
     * 🔴 빨개지는 경우: 삼키는 범위를 "4xx 전부"로 넓혔을 때.
     *    `42501`(RLS 거절)을 성공으로 만들면 **비공개 기록에 좋아요가 눌린 것처럼
     *    보이고** 다음 조회에서 숫자가 안 늘어난다 — 증상이 없는 결함이다.
     */
    @Test
    fun RLS_거절은_좋아요_성공이_아니다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ReactionService.Transport.Response(
                    403,
                    """{"code":"42501","message":"new row violates row-level security policy for table \"likes\""}""",
                ),
            ),
        )
        val res = service(t).like(discoveryId)
        val f = res as ReactionResult.Failed
        assertEquals(403, f.code)
        assertEquals("42501", f.pgCode)
    }

    /**
     * 🔴 빨개지는 경우: DELETE 필터를 하나라도 뺐을 때. PostgREST의 DELETE는 필터가
     *    없으면 **보이는 행 전부**를 지운다 — RLS가 남의 것은 막아 주지만
     *    **내가 눌러 둔 모든 좋아요가 사라지고**, 화면에서는 방금 누른 하트만 꺼진다.
     */
    @Test
    fun 좋아요_취소는_기록과_사람_둘_다로_거른다() = runBlocking {
        val t = FakeTransport(mutableListOf(ReactionService.Transport.Response(204, "")))
        val res = service(t).unlike(discoveryId)

        assertTrue(res is ReactionResult.Loaded)
        assertEquals("DELETE", t.methods[0])
        assertNull(t.bodies[0])
        assertTrue(t.urls[0].contains("discovery_id=eq.$discoveryId"))
        assertTrue(t.urls[0].contains("user_id=eq.$meId"))
    }

    // ── 댓글 ────────────────────────────────────────────────────────

    /**
     * 빨개지는 경우: `order=created_at.desc`를 빼거나(A 문서 `최신순 ▾`),
     * 닉네임을 `users`에서 받게 바꿨을 때(→ **남의 이름이 조용히 빈다**).
     */
    @Test
    fun 댓글은_최신순이고_이름은_공개뷰에서_받는다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok(
                    """[
                      {"id":"c2","user_id":"$otherId","body":"저도 봤어요","created_at":"2026-08-11T02:00:00+00:00"},
                      {"id":"c1","user_id":"$meId","body":"활짝 피었네요","created_at":"2026-08-10T02:00:00+00:00"}
                    ]""",
                ),
                ok("""[{"id":"$otherId","nickname":"꽃보다효산"},{"id":"$meId","nickname":"꽃친구5bf7"}]"""),
            ),
        )
        val res = service(t).comments(discoveryId)
        val list = (res as ReactionResult.Loaded).value
        val rows = list.rows

        assertEquals(listOf("c2", "c1"), rows.map { it.id })
        assertEquals("꽃보다효산", rows[0].nickname)
        assertEquals("저도 봤어요", rows[0].body)
        assertTrue(list.namesLoaded)

        assertTrue(t.urls[0].contains("order=created_at.desc"))
        // 🔴 닉네임은 `public_profiles`다. `users(nickname)` 임베드로 바꾸면
        //    RLS `users_read_self` 때문에 남의 이름이 빈다.
        assertTrue(t.urls[1].startsWith("$BASE/rest/v1/public_profiles"))
        assertTrue(t.urls[1].contains("id=in.("))
        // ⚠️ `deleted_at`은 정책이 거른다 — 쿼리에 넣지 않는다(0007 3절).
        assertFalse(t.urls[0].contains("deleted_at"))
    }

    /**
     * 🔴 빨개지는 경우: 닉네임 조회 실패를 목록 전체의 실패로 만들었을 때.
     *    **본문은 이미 받았는데 댓글이 사라진다** — 네트워크는 좋은데 화면이 빈다.
     *
     * 🔴 그리고 `namesLoaded`를 늘 true로 두거나 아예 없앴을 때. 그러면 화면이 이
     *    `null`을 **탈퇴로 오해해서 살아 있는 사람에게 `탈퇴한 사용자예요`를 붙인다**
     *    (A 문서 3절 `화면 16에서 값이 없거나 실패한 칸`).
     */
    @Test
    fun 닉네임을_못_받아도_댓글은_보이고_그것이_탈퇴는_아니다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok("""[{"id":"c1","user_id":"$otherId","body":"안녕","created_at":"2026-08-11T02:00:00+00:00"}]"""),
                ReactionService.Transport.Response(500, ""),
            ),
        )
        val list = (service(t).comments(discoveryId) as ReactionResult.Loaded).value
        assertEquals(1, list.rows.size)
        assertNull(list.rows[0].nickname)
        assertEquals("안녕", list.rows[0].body)
        // 이름을 **모르는** 것이다 — 탈퇴가 아니다.
        assertFalse(list.namesLoaded)
    }

    /**
     * 🔴 빨개지는 경우: `stringOrNull`을 `optString`으로 되돌렸을 때 —
     *    는 **JVM에서는 안 빨개진다**(`JsonNull` 주석의 표: 기기는 `"null"`,
     *    JVM 테스트는 `""`). 그래서 이 테스트가 재는 것은 값이 **null로 오는 경로가
     *    존재한다**는 사실뿐이고, 되돌림 방지는 `JsonNullTest`가 소스를 읽어서 한다.
     */
    @Test
    fun 탈퇴한_사람의_댓글은_이름이_없다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok("""[{"id":"c1","user_id":"$otherId","body":"안녕","created_at":"2026-08-11T02:00:00+00:00"}]"""),
                // 탈퇴자는 `public_profiles`(deleted_at is null)에 아예 없다 — 행이 0개다.
                ok("[]"),
            ),
        )
        val list = (service(t).comments(discoveryId) as ReactionResult.Loaded).value
        assertNull(list.rows[0].nickname)
        // 🔴 **조회는 성공했다.** 그래서 이 null은 탈퇴다 — 위 테스트의 null과
        //    값은 같고 뜻이 다르며, 화면 문구도 다르다(A 문서 3절).
        assertTrue(list.namesLoaded)
    }

    /** 빨개지는 경우: 조회 실패를 빈 목록으로 바꿨을 때. */
    @Test
    fun 댓글_조회_실패는_빈_목록이_아니다() = runBlocking {
        val t = FakeTransport(mutableListOf(ReactionService.Transport.Response(500, "")))
        val res = service(t).comments(discoveryId)
        assertEquals(500, (res as ReactionResult.Failed).code)
    }

    /** 빨개지는 경우: 댓글이 0건일 때 닉네임 조회를 그래도 부르게 됐을 때(불필요한 왕복). */
    @Test
    fun 댓글이_없으면_이름을_묻지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("[]")))
        val list = (service(t).comments(discoveryId) as ReactionResult.Loaded).value
        assertTrue(list.isEmpty)
        assertEquals(1, t.urls.size)
        // 물어볼 이름이 없었으므로 **모르는 것도 없다** — false면 화면이
        // 있지도 않은 실패를 알린다.
        assertTrue(list.namesLoaded)
    }

    /**
     * 빨개지는 경우: 빈 댓글을 서버로 보내게 됐을 때. 서버 `comments_body_len`이
     * 거절하지만 사용자는 **자기 입력 문제를 네트워크 오류로 읽는다.**
     */
    @Test
    fun 빈_댓글은_보내지_않고_거절한다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val res = service(t).postComment(discoveryId, "   \n ")
        assertEquals(
            ReactionResult.Rejected.Reason.EMPTY_BODY,
            (res as ReactionResult.Rejected).reason,
        )
        // 네트워크를 부르지 않았다.
        assertTrue(t.urls.isEmpty())
    }

    /** 빨개지는 경우: 상한을 서버 `check`와 다른 값으로 바꿨을 때. */
    @Test
    fun 상한을_넘는_댓글은_보내지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf(created()))
        val svc = service(t)

        val exactly = "가".repeat(GamePolicy.COMMENT_MAX_LENGTH)
        assertTrue(svc.postComment(discoveryId, exactly) is ReactionResult.Loaded)

        val tooLong = FakeTransport(mutableListOf())
        val over = "가".repeat(GamePolicy.COMMENT_MAX_LENGTH + 1)
        val res = service(tooLong).postComment(discoveryId, over)
        assertEquals(
            ReactionResult.Rejected.Reason.TOO_LONG,
            (res as ReactionResult.Rejected).reason,
        )
        assertTrue(tooLong.urls.isEmpty())
    }

    /**
     * 🔴 빨개지는 경우: 글자 수를 `String.length`로 세게 됐을 때.
     *    코틀린은 UTF-16 단위라 이모지가 **2**, Postgres `char_length`는 **1**이다.
     *    그러면 서버 기준 200자인 댓글을 **클라이언트가 거절한다.**
     *    ⚠️ **한글로만 테스트하면 이 결함이 안 보인다** — 한글은 양쪽 다 1이다.
     */
    @Test
    fun 이모지를_두_글자로_세지_않는다() = runBlocking {
        // 🌸는 UTF-16 서로게이트 쌍이라 `String.length`로는 2다.
        val emoji = "🌸".repeat(GamePolicy.COMMENT_MAX_LENGTH)
        assertEquals(GamePolicy.COMMENT_MAX_LENGTH * 2, emoji.length)
        assertEquals(GamePolicy.COMMENT_MAX_LENGTH, ReactionService.charLength(emoji))

        val t = FakeTransport(mutableListOf(created()))
        // 서버가 받을 수 있는 길이이므로 보내야 한다.
        assertTrue(service(t).postComment(discoveryId, emoji) is ReactionResult.Loaded)
        assertEquals(1, t.urls.size)
    }

    /**
     * 🔵 **PATCH가 아니라 RPC를 부른다** (2026-08-12에 뒤집었다).
     *
     * 원래 이 테스트는 `댓글_삭제는_deleted_at만_채운다`였고 `PATCH
     * /rest/v1/comments?id=eq.c1` + 본문 `deleted_at`을 단정했다. **그 경로는 원리상
     * 불가능하다** — `update`의 새 행에도 `select` 정책이 적용되고 `comments_read`가
     * `deleted_at is null`을 요구하므로 soft delete가 **자기 SELECT 정책에서 사라져**
     * 42501이 된다(0009 머리말 · 로컬 Postgres 16.2 실측). PostgREST는 `?id=eq.…`를
     * 항상 WHERE로 만들어서 클라이언트가 우회할 방법이 없다.
     *
     * 🔴 **옛 테스트는 그 사실을 못 잡았다** — 픽스처가 204를 주도록 내가 짜 놨으니
     *    **초록이었고 실기기에서는 403이었다.** 여기서 잴 수 있는 것은 "무엇을 어떻게
     *    부르는가"뿐이고, 그것을 0009의 시그니처와 대조하는 것은
     *    [ReactionContractTest]다.
     *
     * 🔴 빨개지는 경우: 다시 PATCH로 되돌리거나, 인자 이름을 `comment_id`로 쓰거나
     *    (→ 실서버에서 `PGRST202`, 그건 "0009 미적용"과 **같은 코드**다), 함수명을 바꿨을 때.
     */
    @Test
    fun 댓글_삭제는_0009_RPC를_부른다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("true")))
        val res = service(t).deleteComment("c1")

        assertTrue("본문 `true`는 성공이다: $res", res is ReactionResult.Loaded)
        assertEquals("POST", t.methods[0])
        assertEquals("$BASE/rest/v1/rpc/delete_comment", t.urls[0])
        val body = t.bodies[0]!!
        assertTrue("RPC 인자가 `c_id`가 아니다: $body", body.contains(""""c_id":"c1""""))
        // 🔴 본문에 `body`·`deleted_at`을 실으면 안 된다 — 삭제 시각은 서버 `now()`가
        //    채우고(기기 시계가 안 들어간다), 본문 수정은 C-9가 금지한다.
        assertFalse("삭제 요청에 body가 실려 있다 — C-9 수정 불가가 우회된다", body.contains("\"body\""))
        assertFalse("기기 시계로 deleted_at을 만들고 있다", body.contains("deleted_at"))
    }

    /**
     * 🔴 **`false`를 성공으로 접지 않는다.**
     *
     * `delete_comment`는 `security definer` 함수이고 **실패도 HTTP 200**으로 온다 —
     * 본문 `false`만이 "안 지웠다"를 말한다(0009 1절). [ReactionService.sendUnit]처럼
     * 본문을 안 읽는 경로로 부르면 **모든 거절이 성공**이 되고, 화면은 서버에 그대로
     * 남아 있는 댓글을 지워 버린다(0008 머리말 `남의 댓글 삭제도 204 + 0행`과 같은 함정).
     *
     * 🔴 빨개지는 경우: `sendUnit`으로 되돌리거나, `body.toBoolean()`으로 읽게 됐을 때
     *    (그 함수는 **아는 값이 아닌 것을 전부 false로** 만들어 형식 변경을 거절로 바꾼다).
     */
    @Test
    fun 삭제_거절은_HTTP_200인데도_실패다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("false")))
        val res = service(t).deleteComment("c1")

        assertTrue("HTTP 200 + 본문 false가 성공으로 읽혔다: $res", res is ReactionResult.Failed)
        assertEquals(200, (res as ReactionResult.Failed).code)
        assertEquals(
            "거절을 다른 실패와 같은 값으로 뭉갰다 — 화면 문구가 갈라지지 않는다",
            ReactionService.DENIED,
            res.pgCode,
        )
        // 조용히 실패하지 않는다 — 이 경로는 화면에 오류 문구가 하나뿐이라
        // 로그가 없으면 원인을 나중에 못 찾는다.
        assertTrue("거절이 로그를 안 지났다: $logs", logs.any { it.contains("거절") })
    }

    /**
     * 🔴 **"서버가 거절했다"와 "서버가 형식을 바꿨다"를 갈라 둔다.**
     *
     * 처음 이 층을 RPC로 바꿀 때 `parse`에서 `false`에 곧바로 `null`을 돌려줬다.
     * 그러면 [ReactionService.request]가 [ReactionService.PARSE_FAILED]로 표시하는데,
     * **정상 동작(거절)과 사고(형식 변경)가 같은 값이 된다** — API가 바뀐 날 화면은
     * `댓글을 지우지 못했어요`를 띄우고 아무도 원인을 모른다.
     *
     * 🔴 빨개지는 경우: 그 구현으로 되돌아가거나, 두 상수를 하나로 합쳤을 때.
     */
    @Test
    fun 형식이_바뀐_응답은_거절과_다른_값이다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("""{"ok":true}""")))
        val res = service(t).deleteComment("c1")

        assertTrue(res is ReactionResult.Failed)
        assertEquals(
            "형식 변경이 거절(DENIED)로 읽혔다 — 정상 동작과 사고가 같은 값이 된다",
            ReactionService.PARSE_FAILED,
            (res as ReactionResult.Failed).pgCode,
        )
        assertTrue(
            "두 상수가 같은 값이다 — 갈라 두는 의미가 사라졌다",
            ReactionService.PARSE_FAILED != ReactionService.DENIED,
        )
    }

    // ── 신고 ────────────────────────────────────────────────────────

    /**
     * 빨개지는 경우: `reporter_id`를 `user_id`로 쓰거나(0001 컬럼명), 두 번째 신고를
     * 실패로 보이게 했을 때 — 사용자가 **다시 누른다.**
     */
    @Test
    fun 신고는_두_번_해도_접수로_본다() = runBlocking {
        val first = FakeTransport(mutableListOf(created()))
        assertTrue(service(first).report(discoveryId, "부적절한 사진") is ReactionResult.Loaded)
        assertTrue(first.bodies[0]!!.contains(""""reporter_id":"$meId""""))
        assertTrue(first.bodies[0]!!.contains("부적절한 사진"))

        val again = FakeTransport(
            mutableListOf(
                ReactionService.Transport.Response(
                    409,
                    """{"code":"23505","message":"duplicate key value violates unique constraint \"reports_discovery_id_reporter_id_key\""}""",
                ),
            ),
        )
        assertTrue(service(again).report(discoveryId, null) is ReactionResult.Loaded)
    }

    /**
     * 빨개지는 경우: 이유가 비었을 때 `JSONObject.NULL`이나 `""`을 넣게 됐을 때.
     * `reason`은 nullable 컬럼이고, 키를 빼는 것과 명시적 null은 PostgREST에서 다르다.
     */
    @Test
    fun 이유가_비면_reason_키를_넣지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf(created()))
        service(t).report(discoveryId, "  ")
        assertFalse(t.bodies[0]!!.contains("reason"))
    }

    // ── 공통 이음새 ──────────────────────────────────────────────────

    /** 빨개지는 경우: 401에 재시도를 안 하거나, 두 번 이상 하게 됐을 때. */
    @Test
    fun 토큰이_만료되면_한_번_갱신해서_다시_보낸다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ReactionService.Transport.Response(401, """{"code":"PGRST301"}"""),
                ok("""[{"like_count":1,"comment_count":0,"liked_by_me":false}]"""),
            ),
        )
        val auth = StubAuth()
        val res = service(t, auth = auth).reactions(discoveryId)

        assertEquals(1, (res as ReactionResult.Loaded).value.likeCount)
        assertEquals(1, auth.refreshCalls)
        assertEquals(2, t.bearers.size)
        assertEquals(StubAuth.FRESH, t.bearers[1])
    }

    /** 빨개지는 경우: 갱신도 실패했는데 계속 보내려 할 때. */
    @Test
    fun 갱신도_실패하면_401로_끝낸다() = runBlocking {
        val t = FakeTransport(mutableListOf(ReactionService.Transport.Response(401, "")))
        val auth = StubAuth(refreshResult = null)
        val res = service(t, auth = auth).reactions(discoveryId)
        assertEquals(401, (res as ReactionResult.Failed).code)
        assertEquals(1, t.urls.size)
    }

    /**
     * 빨개지는 경우: `configured` 판정을 전역 `AppSecrets`로 되돌렸을 때 —
     * **그 맥에 `local.properties`가 있느냐로 테스트가 갈린다.**
     */
    @Test
    fun 키가_없는_빌드는_네트워크를_부르지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val svc = ReactionService(
            auth = StubAuth(),
            myUserId = { meId },
            baseUrl = "",
            anonKey = "",
            transport = t,
            log = { logs += it },
        )
        assertTrue(svc.reactions(discoveryId) is ReactionResult.NotConfigured)
        assertTrue(svc.like(discoveryId) is ReactionResult.NotConfigured)
        assertTrue(svc.comments(discoveryId) is ReactionResult.NotConfigured)
        assertTrue(t.urls.isEmpty())
    }

    /**
     * 🔴 빨개지는 경우: uuid가 없을 때 `"null"`이나 빈 문자열을 본문에 넣게 됐을 때.
     *    uuid 컬럼에 들어가지 못해 `22P02`가 되고, 그건 **영구 거절**이라
     *    그 좋아요는 다시는 안 올라간다.
     */
    @Test
    fun 내_uuid를_모르면_보내지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val svc = service(t, userId = null)
        assertEquals(401, (svc.like(discoveryId) as ReactionResult.Failed).code)
        assertEquals(401, (svc.report(discoveryId, null) as ReactionResult.Failed).code)
        assertEquals(
            401,
            (svc.postComment(discoveryId, "안녕") as ReactionResult.Failed).code,
        )
        assertTrue(t.urls.isEmpty())
    }

    /**
     * 빨개지는 경우: 네트워크 예외를 그대로 던지게 됐을 때. 화면 16에서 던지면
     * 앱이 죽는다 — 다른 서비스들과 같은 규칙(코드 0)이어야 한다.
     */
    @Test
    fun 네트워크_예외는_코드_0의_실패다() = runBlocking {
        val throwing = object : ReactionService.Transport {
            override suspend fun send(
                url: String,
                method: String,
                apiKey: String,
                bearer: String,
                body: String?,
            ): ReactionService.Transport.Response = throw java.io.IOException("no route")
        }
        val svc = ReactionService(
            auth = StubAuth(),
            myUserId = { meId },
            baseUrl = BASE,
            anonKey = ANON,
            transport = throwing,
            log = { logs += it },
        )
        assertEquals(0, (svc.reactions(discoveryId) as ReactionResult.Failed).code)
    }

    private companion object {
        const val BASE = "https://ngfkkazyvbbhrcznqkar.supabase.co"
        const val ANON = "stub-anon-key"
    }
}
