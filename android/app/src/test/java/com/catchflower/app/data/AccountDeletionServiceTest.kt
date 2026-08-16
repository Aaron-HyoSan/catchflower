package com.catchflower.app.data

import com.catchflower.app.core.AccountDeletionRules.Step
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회원 탈퇴의 서버 쪽.
 *
 * ## 🔴 이 검사가 어떤 경우에 빨개지나
 *
 * | 사고 | 화면 증상 | 잡는 검사 |
 * |---|---|---|
 * | 0행 PATCH를 성공으로 읽는다 | **성공 화면.** 서버에 계정이 그대로 남는다 | [프로필_0행은_실패다] |
 * | 0행 DELETE를 실패로 읽는다 | `탈퇴를 마치지 못했어요` — 새 계정은 **영구히** 탈퇴 못 한다 | [지울_것이_없어도_성공이다] |
 * | DELETE에 필터를 빼먹는다 | 없다(RLS가 막는다). 정책이 하나 풀리는 날 남의 행이 지워진다 | [모든_DELETE에_내_id_필터가_붙는다] |
 * | 한 단계 실패인데 계속 진행한다 | 없다 | [실패하면_그_자리에서_멈춘다] |
 * | `deleted_at`을 함수 호출로 보낸다 | 400 — **탈퇴가 영구히 실패한다** | [deleted_at은_now_문자열이다] |
 * | 닉네임을 NULL로 비운다 | 23502 — 같음 | [닉네임은_빈_문자열로_비운다] |
 *
 * ## ⚠️ 응답 봉투는 **실측한 것만** 쓴다
 *
 * `patch_zero_rows`·`patch_saved`·`patch_bad_token`은 익명 계정으로 실제 Supabase에서
 * 받아 둔 봉투다([ProfileServiceTest] 주석). 상상해서 쓰면 **테스트만 통과하고 앱은
 * 안 된다** — 이 저장소가 이미 (22)에서 그랬다.
 *
 * 🔴 **`patch_saved`는 `select=*` 응답이고 우리 PATCH는 `select=id`다.** 그래도 쓰는
 *    이유는 이 코드가 보는 것이 **행 수**뿐이기 때문이다(1행 = 성공). 봉투 모양이
 *    아니라 그 판정만 재고 있다는 것을 적어 둔다.
 *
 * ## ⚠️ 여기서 못 재는 것
 *
 * 🔴 **RLS가 실제로 무엇을 허용하는지 모른다.** 정책은 서버에 있고 이 검사는 요청만
 *    본다 — `discoveries_delete_self`가 없어져도 초록이다(0011 확인 목록).
 * 🔴 **auth 레코드는 애초에 안 지운다**(`service_role`이 필요하다). 그건 오너 작업이다.
 */
class AccountDeletionServiceTest {

    private val myId = "42f5e652-de06-4aab-ad93-482c89cf6081"
    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<Pair<Int, String>>,
    ) : AccountDeletionService.Transport {
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String?>()
        val bearers = mutableListOf<String>()
        val prefers = mutableListOf<String>()
        var throwIo = false

        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            prefer: String,
        ): AccountDeletionService.Transport.Response {
            urls += url
            methods += method
            bodies += body
            bearers += bearer
            prefers += prefer
            if (throwIo) throw java.io.IOException("네트워크 끊김")
            // ⚠️ 응답을 다 쓰면 500을 준다 — 무한히 성공을 주면 요청 수를 세는 검사가
            //    "덜 보냈다"를 못 잡는다.
            val (code, text) = if (responses.isEmpty()) 500 to "" else responses.removeAt(0)
            return AccountDeletionService.Transport.Response(code, text)
        }
    }

    private class StubAuth(
        private val token: String? = "stub-token",
        private val freshToken: String? = "stub-fresh-token",
    ) : TokenSource {
        var refreshCalls = 0
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): String? {
            refreshCalls++
            return freshToken
        }
    }

    /**
     * ⚠️ `baseUrl`·`anonKey`를 반드시 넣는다 — 기본값은 `AppSecrets`라 **그 맥에
     *    `local.properties`가 있느냐로 통과 여부가 갈린다.**
     */
    private fun service(
        vararg responses: Pair<Int, String>,
        auth: StubAuth = StubAuth(),
        userId: String? = "42f5e652-de06-4aab-ad93-482c89cf6081",
        baseUrl: String = "https://example.supabase.co",
        anonKey: String = "test-anon",
    ): Pair<FakeTransport, AccountDeletionService> {
        val transport = FakeTransport(responses.toMutableList())
        return transport to AccountDeletionService(
            auth = auth,
            myUserId = { userId },
            baseUrl = baseUrl,
            anonKey = anonKey,
            transport = transport,
            log = { logs += it },
        )
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/supabase/$name.json")) {
            "픽스처 $name.json이 없다"
        }.bufferedReader().use { it.readText() }

    /** 네 번의 DELETE(204) + PATCH(200 · 1행). */
    private fun 전부_성공하는_응답() = arrayOf(
        204 to "", 204 to "", 204 to "", 204 to "",
        200 to fixture("patch_saved"),
    )

    // ── 정상 ─────────────────────────────────────────────────────────

    @Test
    fun 다섯_단계를_순서대로_보낸다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        assertEquals(AccountDeletionResult.Deleted, s.deleteServerData())

        assertEquals(listOf("DELETE", "DELETE", "DELETE", "DELETE", "PATCH"), t.methods)
        // 🔴 순서를 표로 못 박는다. 표(`Step.entries`)와 실제 요청이 어긋나면
        //    부분 삭제가 남는데 화면은 성공이다.
        assertTrue(t.urls[0], t.urls[0].startsWith("https://example.supabase.co/rest/v1/discoveries?"))
        assertTrue(t.urls[1], t.urls[1].startsWith("https://example.supabase.co/rest/v1/likes?"))
        assertTrue(t.urls[2], t.urls[2].startsWith("https://example.supabase.co/rest/v1/friendships?"))
        assertTrue(t.urls[3], t.urls[3].startsWith("https://example.supabase.co/rest/v1/blocks?"))
        assertTrue(t.urls[4], t.urls[4].startsWith("https://example.supabase.co/rest/v1/users?"))
    }

    /**
     * 🔴 **필터 없는 DELETE는 보이는 행 전부를 지운다**(PostgREST). 지금은 RLS가 막지만
     * 방어를 정책 하나에만 걸어 두지 않는다 — 그리고 필터가 빠져도 **화면 증상이 없다.**
     */
    @Test
    fun 모든_DELETE에_내_id_필터가_붙는다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        t.urls.zip(t.methods).filter { it.second == "DELETE" }.forEach { (url, _) ->
            assertTrue("필터가 없는 DELETE다: $url", "?" in url)
            assertTrue("내 id가 안 붙은 DELETE다: $url", myId in url)
        }
    }

    /** 친구 관계는 **양쪽**을 지운다 — 한쪽만 지우면 상대 목록에 내가 남는다. */
    @Test
    fun 친구_관계는_요청한_쪽과_받은_쪽_모두_지운다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        assertEquals(
            "friendships?or=(requester_id.eq.$myId,addressee_id.eq.$myId)",
            t.urls[2].substringAfter("/rest/v1/"),
        )
    }

    /**
     * 🔴 **PATCH만 `Prefer: return=representation`이 필요하다.** 없으면 PostgREST가
     * 0행에도 204를 줘서 RLS에 막힌 것이 성공으로 온다 — 그때 기기를 지운다.
     */
    @Test
    fun 프로필_PATCH만_대표행을_요구한다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        assertEquals(listOf("", "", "", "", AccountDeletionService.PREFER_REPRESENTATION), t.prefers)
        assertTrue("select 없이 PATCH하면 본문이 비어 행 수를 셀 수 없다", "select=id" in t.urls[4])
    }

    // ── 프로필 PATCH 본문 ────────────────────────────────────────────

    /**
     * 🔴 `"now()"`는 timestamptz 입력 문법 오류다(함수 호출을 JSON 값으로 보낼 수 없다).
     * 그러면 400이 오고 **탈퇴가 영구히 실패한다.** `'now'`는 Postgres의 특수 입력이다.
     *
     * ⚠️ 기기 시계로 만든 ISO 문자열도 안 된다 — 시계가 틀린 기기가 미래로 찍으면
     *    오너의 정리 작업(제안 0011)이 그 계정을 **영원히 건너뛴다.**
     */
    @Test
    fun deleted_at은_now_문자열이다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        val body = JSONObject(t.bodies[4]!!)
        assertEquals("now", body.getString("deleted_at"))
    }

    /** 🔴 `nickname`은 `not null`이다(0001) — NULL이면 23502로 탈퇴가 끝까지 실패한다. */
    @Test
    fun 닉네임은_빈_문자열로_비운다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        val body = JSONObject(t.bodies[4]!!)
        assertFalse("NULL을 보내면 23502다", body.isNull("nickname"))
        assertEquals("", body.getString("nickname"))
    }

    /**
     * 🔴 활동 지역은 **NULL로** 비운다. `deleted_at`만 찍으면 뷰가 가려 주니 화면에는
     * 안 보이지만 행에는 남는다 — 개인정보 처리방침 6항 가가 "지워진다"고 약속했다.
     */
    @Test
    fun 활동_지역은_NULL로_비운다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        val body = JSONObject(t.bodies[4]!!)
        listOf("region_name", "dong_code", "gu_code").forEach {
            assertTrue("$it 를 비우지 않았다", body.isNull(it))
        }
    }

    /** DELETE에는 본문이 없다 — 붙이면 PostgREST가 400을 준다. */
    @Test
    fun DELETE에는_본문이_없다() = runBlocking {
        val (t, s) = service(*전부_성공하는_응답())
        s.deleteServerData()
        assertEquals(listOf(null, null, null, null), t.bodies.take(4))
    }

    // ── 0행 ──────────────────────────────────────────────────────────

    /**
     * 🔴 **실측 봉투다**(`patch_zero_rows` = HTTP 200 + `[]`). RLS가 막았거나 uuid가
     * 틀렸을 때의 얼굴이고, 성공과 코드가 같다. 이걸 성공으로 읽으면 사용자는
     * 탈퇴됐다고 믿고 서버 계정은 그대로다.
     */
    @Test
    fun 프로필_0행은_실패다() = runBlocking {
        val (_, s) = service(
            204 to "", 204 to "", 204 to "", 204 to "",
            200 to fixture("patch_zero_rows"),
        )
        val result = s.deleteServerData()
        assertEquals(
            AccountDeletionResult.Failed(Step.PROFILE, 200, ProfileService.NO_ROW),
            result,
        )
    }

    /**
     * 🔴 **DELETE는 반대다.** 지울 것이 없는 것(발견 기록 0개)이 정상이고, 실패로 세면
     * 첫 실행 직후 탈퇴하는 사용자는 **영구히** 탈퇴할 수 없다.
     *
     * ⚠️ 두 판단이 반대라는 것이 이 파일의 핵심이다 — '통일'하면 한쪽이 반드시 틀린다.
     */
    @Test
    fun 지울_것이_없어도_성공이다() = runBlocking {
        // PostgREST는 0행 DELETE에도 204를 준다(본문 없음).
        val (_, s) = service(
            204 to "", 204 to "", 204 to "", 204 to "",
            200 to fixture("patch_saved"),
        )
        assertEquals(AccountDeletionResult.Deleted, s.deleteServerData())
    }

    // ── 실패 ─────────────────────────────────────────────────────────

    /**
     * 🔴 **첫 실패에서 멈춘다.** 계속 보내면 뒤 단계가 성공해서 결과가 섞이고,
     * "어디까지 지워졌는지"를 아무도 모른다.
     */
    @Test
    fun 실패하면_그_자리에서_멈춘다() = runBlocking {
        val (t, s) = service(204 to "", 403 to fixture("insert_rls_violation"))
        val result = s.deleteServerData()
        assertTrue(result is AccountDeletionResult.Failed)
        assertEquals(Step.LIKES, (result as AccountDeletionResult.Failed).step)
        assertEquals(403, result.code)
        // 🔴 요청이 **딱 둘**이다. 셋이면 실패한 뒤에도 계속 보낸 것이다.
        assertEquals(2, t.urls.size)
    }

    /** 네트워크가 아예 안 되면 코드 0이다 — 실패로 세고, 다시 시도할 수 있다. */
    @Test
    fun 네트워크가_끊기면_실패다() = runBlocking {
        val (t, s) = service(204 to "")
        t.throwIo = true
        val result = s.deleteServerData()
        assertEquals(AccountDeletionResult.Failed(Step.DISCOVERIES, 0), result)
    }

    /** 실패 로그에는 단계와 HTTP 코드가 남는다 — 화면에는 안 쓰므로 여기가 유일한 단서다. */
    @Test
    fun 실패는_로그에_단계와_코드를_남긴다() = runBlocking {
        logs.clear()
        val (_, s) = service(204 to "", 204 to "", 500 to "")
        s.deleteServerData()
        assertTrue(logs.toString(), logs.any { "FRIENDSHIPS" in it && "500" in it })
    }

    // ── 토큰 ─────────────────────────────────────────────────────────

    /**
     * 401이면 갱신해서 **한 번만** 다시 보낸다. 무한 재시도는 계정이 죽은 경우에
     * 앱을 매달리게 만든다.
     */
    @Test
    fun 만료된_토큰은_한_번_갱신하고_다시_보낸다() = runBlocking {
        val auth = StubAuth()
        val (t, s) = service(
            401 to fixture("patch_bad_token"),
            204 to "", 204 to "", 204 to "", 204 to "",
            200 to fixture("patch_saved"),
            auth = auth,
        )
        assertEquals(AccountDeletionResult.Deleted, s.deleteServerData())
        assertEquals(1, auth.refreshCalls)
        assertEquals(6, t.urls.size)
        assertEquals("stub-fresh-token", t.bearers[1])
    }

    /** 갱신 수단이 없으면 첫 응답(401)이 그대로 실패다 — 여기서 성공으로 넘기면 안 된다. */
    @Test
    fun 갱신할_수_없으면_401이_실패다() = runBlocking {
        val (_, s) = service(
            401 to fixture("patch_bad_token"),
            auth = StubAuth(freshToken = null),
        )
        val result = s.deleteServerData()
        assertEquals(Step.DISCOVERIES, (result as AccountDeletionResult.Failed).step)
        assertEquals(401, result.code)
    }

    @Test
    fun 토큰이_아예_없으면_실패다() = runBlocking {
        val (t, s) = service(204 to "", auth = StubAuth(token = null))
        val result = s.deleteServerData()
        assertEquals(401, (result as AccountDeletionResult.Failed).code)
        assertEquals("보내지 않았어야 한다", 0, t.urls.size)
    }

    // ── 서버에 계정이 없는 경우 ──────────────────────────────────────

    /**
     * 🔴 **실패가 아니다.** 키 없는 빌드·계정 uuid가 없는 상태에서 실패로 처리하면
     * **탈퇴할 수 없는 앱**이 된다(그리고 기기 데이터는 계속 남는다).
     */
    @Test
    fun 키가_없으면_서버에_지울_것이_없다() = runBlocking {
        val (t, s) = service(baseUrl = "", anonKey = "")
        assertEquals(AccountDeletionResult.NothingOnServer, s.deleteServerData())
        assertEquals(0, t.urls.size)
    }

    @Test
    fun uuid가_없으면_서버에_지울_것이_없다() = runBlocking {
        val (t, s) = service(userId = null)
        assertEquals(AccountDeletionResult.NothingOnServer, s.deleteServerData())
        assertEquals(0, t.urls.size)
    }

    // ── 재시도 ───────────────────────────────────────────────────────

    /**
     * 🔴 **다시 시도하면 처음부터 다시 돈다**(`RETRY_FROM_START`). 이어서 하려면
     * "어디까지 했는지"를 기기에 저장해야 하고, 그 상태로 앱을 쓰면 화면이 거짓말을 한다.
     * 요청 수로 잰다 — 2번 + 5번 = 7번.
     */
    @Test
    fun 다시_시도하면_처음부터_보낸다() = runBlocking {
        val (t, s) = service(
            204 to "", 403 to fixture("insert_rls_violation"),
            204 to "", 204 to "", 204 to "", 204 to "",
            200 to fixture("patch_saved"),
        )
        assertTrue(s.deleteServerData() is AccountDeletionResult.Failed)
        assertEquals(AccountDeletionResult.Deleted, s.deleteServerData())
        assertEquals(7, t.urls.size)
        assertTrue(t.urls[2].contains("discoveries"))
    }
}
