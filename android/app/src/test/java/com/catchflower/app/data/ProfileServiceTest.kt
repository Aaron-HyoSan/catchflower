package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 닉네임 저장.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가 — 200이 "저장됐다"가 아니다
 *
 * **실측**: 없는 uuid로 PATCH하면 PostgREST가 **HTTP 200 + 본문 `[]`** 를 준다
 * (`patch_zero_rows.json` — [RegionUpdateServiceTest]가 받아 둔 실측 본문이다).
 * RLS `users_update_self`가 남의 행을 막은 경우도 같은 얼굴이다.
 *
 * 그 응답을 성공으로 읽으면 화면은 `프로필을 저장했어요`를 띄운 뒤 **옛 닉네임**을
 * 다시 그린다. 사용자에게는 "저장했다면서 안 바뀐다"로 보이고, **오류는 한 건도 안 난다.**
 *
 * ## ⚠️ [RegionUpdateService]와 판단이 **반대**다
 *
 * 지역 저장은 읽을 수 없는 응답을 "저장됐다"로 본다 — 거짓 실패로 재시도하면
 * **6개월에 한 번뿐인 지역 변경**이 날아가기 때문이다. 닉네임에는 그 대가가 없다.
 * 그래서 여기서는 0행·못 읽음을 **실패**로 둔다. 두 서비스가 다른 쪽으로 기울어야
 * 맞는 자리이므로, 한쪽을 다른 쪽에 맞추는 '통일'을 하지 않는다.
 *
 * ## ⚠️ 응답 본문은 **실제 Supabase가 준 것**이다
 *
 * `patch_saved`·`patch_zero_rows`·`patch_bad_token`은 익명 계정으로 실측한 봉투다
 * (진행.md (32)). 봉투를 상상해서 쓰면 **테스트만 통과하고 앱은 안 된다** — (22)에서
 * 실제로 그랬다.
 */
class ProfileServiceTest {

    private val myId = "42f5e652-de06-4aab-ad93-482c89cf6081"

    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<Pair<Int, String>>,
    ) : ProfileService.Transport {
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()
        val bodies = mutableListOf<String?>()
        val bearers = mutableListOf<String>()
        val apiKeys = mutableListOf<String>()
        val prefers = mutableListOf<String>()
        var throwIo = false

        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
            prefer: String,
        ): ProfileService.Transport.Response {
            urls += url
            methods += method
            bodies += body
            bearers += bearer
            apiKeys += apiKey
            prefers += prefer
            if (throwIo) throw java.io.IOException("네트워크 끊김")
            val (code, text) = if (responses.isEmpty()) 500 to "" else responses.removeAt(0)
            return ProfileService.Transport.Response(code, text)
        }
    }

    private class StubAuth(
        private val token: String? = TOKEN,
        private val freshToken: String? = FRESH,
    ) : TokenSource {
        var refreshCalls = 0
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): String? {
            refreshCalls++
            return freshToken
        }

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
        userId: String? = "42f5e652-de06-4aab-ad93-482c89cf6081",
        baseUrl: String = "https://example.supabase.co",
        anonKey: String = "test-anon",
    ): Pair<FakeTransport, ProfileService> {
        val transport = FakeTransport(responses.toMutableList())
        return transport to ProfileService(
            auth = auth,
            myUserId = { userId },
            baseUrl = baseUrl,
            anonKey = anonKey,
            transport = transport,
            log = { logs += it },
        )
    }

    /** 실측 응답 본문. **여기서 문자열을 만들지 않는다** — 파일에서 읽는다. */
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/supabase/$name.json")) {
            "픽스처 $name.json이 없다 — probe_region_patch.py로 다시 받아라"
        }.bufferedReader().use { it.readText() }

    // ── 정상 저장 ────────────────────────────────────────────────────

    @Test
    fun 실측_응답으로_저장이_성공한다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))

        // 🔴 **우리가 보낸 값이 아니라 서버가 준 값이다.** 픽스처의 닉네임은
        //    `꽃친구42f5`이고, 우리가 보낸 것은 `꽃박사`다 — 두 값이 다르므로
        //    구현이 요청값을 그대로 성공값으로 쓰면 이 줄에서 빨개진다.
        assertEquals(ProfileResult.Saved("꽃친구42f5"), svc.updateNickname("꽃박사"))

        // 🔴 **`?id=eq.{내 uuid}` 필터가 반드시 있어야 한다.** 없으면 PostgREST가
        //    테이블 전체 수정으로 해석한다 — RLS 정책 한 줄에 의존하게 된다.
        assertEquals(
            "https://example.supabase.co/rest/v1/users?id=eq.$myId&select=nickname",
            transport.urls[0],
        )
        assertEquals("test-anon", transport.apiKeys[0])
        assertEquals(StubAuth.TOKEN, transport.bearers[0])
    }

    /**
     * 🔴 **PATCH여야 한다.** POST로 나가면 PostgREST가 insert로 처리하고,
     *    이미 있는 내 행이라 403 `42501`이 온다 — 그 응답만 보면 **RLS 사고로 읽힌다**
     *    ([RegionUpdateVerbTest]가 그 사건의 기록이다).
     */
    @Test
    fun PATCH로_보낸다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.updateNickname("꽃박사")
        assertEquals("PATCH", transport.methods[0])
    }

    /**
     * 🔴 **`Prefer: return=representation`이 없으면 위 `0행` 검사가 원리상 불가능하다.**
     *    PATCH 기본 응답은 `204 No Content`이고 본문이 비어 있어(`patch_no_prefer.json`
     *    — 실측: 빈 파일) 몇 행이 바뀌었는지 **알 방법이 없다.**
     */
    @Test
    fun return_representation을_요구한다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.updateNickname("꽃박사")
        assertEquals("return=representation", transport.prefers[0])
    }

    /**
     * ⚠️ **닉네임 한 칸만 보낸다.** 프로필 화면이 가진 다른 값(지역·가입일)을 같이
     *    보내면 6개월 규칙이 걸린 지역까지 건드리게 되고, 그건 트리거 `P0001`로
     *    거절되면서 **닉네임 변경까지 같이 실패한다.**
     */
    @Test
    fun 닉네임_한_칸만_보낸다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.updateNickname("꽃박사")
        val sent = JSONObject(checkNotNull(transport.bodies[0]))
        assertEquals(1, sent.length())
        assertEquals("꽃박사", sent.getString("nickname"))
    }

    /** 앞뒤 공백은 [NicknameRules]가 뗀 값으로 나간다 — `" 꽃박사 "`가 저장되면 안 된다. */
    @Test
    fun 앞뒤_공백을_뗀_값을_보낸다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.updateNickname("  꽃박사  ")
        assertEquals("꽃박사", JSONObject(checkNotNull(transport.bodies[0])).getString("nickname"))
    }

    // ── 🔴 성공 코드인데 저장이 안 된 경우 ───────────────────────────

    /**
     * 🔴 **이 파일이 존재하는 이유.** HTTP 200인데 바뀐 행이 없다.
     */
    @Test
    fun 실측_0행_응답은_실패다() = runBlocking {
        val (_, svc) = service(200 to fixture("patch_zero_rows"))
        assertEquals(
            ProfileResult.Failed(200, ProfileService.NO_ROW),
            svc.updateNickname("꽃박사"),
        )
        assertTrue(
            "0행을 로그로 남겨야 한다 — 안 남기면 기기에서 원인을 못 찾는다",
            logs.any { it.contains("0행") },
        )
    }

    /** 204 + 빈 본문(실측 `patch_no_prefer`)도 같은 자리다. **성공으로 읽으면 안 된다.** */
    @Test
    fun 빈_본문은_실패다() = runBlocking {
        val (_, svc) = service(204 to fixture("patch_no_prefer"))
        val result = svc.updateNickname("꽃박사")
        assertTrue("빈 본문을 성공으로 읽었다 — 실제로는 몇 행이 바뀌었는지 모른다", result is ProfileResult.Failed)
    }

    /** JSON이 아닌 본문은 [ProfileService.PARSE_FAILED]로 갈린다 — 0행과 원인이 다르다. */
    @Test
    fun 읽을_수_없는_본문은_PARSE로_갈린다() = runBlocking {
        val (_, svc) = service(200 to "<html>502 Bad Gateway</html>")
        assertEquals(
            ProfileResult.Failed(200, ProfileService.PARSE_FAILED),
            svc.updateNickname("꽃박사"),
        )
    }

    // ── 실패를 뭉개지 않는다 ─────────────────────────────────────────

    /**
     * ⚠️ 봉투는 `discoveries`에서 실측한 것이다(`insert_rls_violation.json`) —
     *    **읽는 것은 `code` 한 칸**이고 PostgREST는 테이블과 무관하게 같은 봉투를 준다.
     */
    @Test
    fun RLS_거절은_42501을_그대로_올린다() = runBlocking {
        val (_, svc) = service(403 to fixture("insert_rls_violation"))
        assertEquals(ProfileResult.Failed(403, "42501"), svc.updateNickname("꽃박사"))
    }

    @Test
    fun 전송이_던지면_실패다() = runBlocking {
        val (transport, svc) = service()
        transport.throwIo = true
        assertEquals(ProfileResult.Failed(0), svc.updateNickname("꽃박사"))
    }

    // ── 토큰 ─────────────────────────────────────────────────────────

    /**
     * 401(`patch_bad_token` 실측: `PGRST301`)이면 **한 번만** 갱신하고 다시 보낸다.
     */
    @Test
    fun 토큰이_만료되면_한_번_갱신하고_다시_보낸다() = runBlocking {
        val auth = StubAuth()
        val (transport, svc) = service(
            401 to fixture("patch_bad_token"),
            200 to fixture("patch_saved"),
            auth = auth,
        )
        assertEquals(ProfileResult.Saved("꽃친구42f5"), svc.updateNickname("꽃박사"))
        assertEquals(1, auth.refreshCalls)
        assertEquals(listOf(StubAuth.TOKEN, StubAuth.FRESH), transport.bearers)
    }

    @Test
    fun 갱신할_수_없으면_401이다() = runBlocking {
        val auth = StubAuth(freshToken = null)
        val (transport, svc) = service(401 to fixture("patch_bad_token"), auth = auth)
        assertEquals(ProfileResult.Failed(401), svc.updateNickname("꽃박사"))
        // 두 번째 요청을 보내지 않는다 — 보낼 토큰이 없다.
        assertEquals(1, transport.urls.size)
    }

    @Test
    fun 토큰이_없으면_서버를_부르지_않는다() = runBlocking {
        val (transport, svc) = service(auth = StubAuth(token = null))
        assertEquals(ProfileResult.Failed(401), svc.updateNickname("꽃박사"))
        assertTrue(transport.urls.isEmpty())
    }

    /** 내 uuid를 모르면 필터를 만들 수 없다 — **필터 없는 PATCH를 보내지 않는다.** */
    @Test
    fun 내_uuid를_모르면_서버를_부르지_않는다() = runBlocking {
        val (transport, svc) = service(userId = null)
        assertEquals(ProfileResult.Failed(401), svc.updateNickname("꽃박사"))
        assertTrue(transport.urls.isEmpty())
    }

    // ── 화면이 막았어야 하는 값 ──────────────────────────────────────

    /**
     * 🔴 **빈 닉네임은 서버까지 가지 않는다.** 화면이 이미 막지만, 그 판단이 화면에만
     *    있으면 다음 사람이 다른 화면에서 이 함수를 부를 때 닉네임이 지워진다.
     *
     * ⚠️ [ProfileResult.Failed]가 아니라 [ProfileResult.Invalid]다 — 실패로 보이면
     *    `잠시 후 다시 시도해 주세요`가 뜨는데, 다시 시도해도 결과는 같다.
     */
    @Test
    fun 빈_닉네임은_서버를_부르지_않는다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        assertEquals(ProfileResult.Invalid, svc.updateNickname("   "))
        assertTrue(transport.urls.isEmpty())
    }

    @Test
    fun 열한_글자는_서버를_부르지_않는다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        assertEquals(ProfileResult.Invalid, svc.updateNickname("가나다라마바사아자차카"))
        assertTrue(transport.urls.isEmpty())
    }

    // ── 키 없는 빌드 ─────────────────────────────────────────────────

    /**
     * ⚠️ **[ProfileResult.Invalid] 판정보다 뒤에 온다.** 키가 없어도 잘못된 입력은
     *    잘못된 입력이므로, 순서가 바뀌면 키 없는 빌드에서 빈 닉네임이
     *    `잠시 후 다시 시도해 주세요`로 보고된다.
     */
    @Test
    fun 키가_없으면_NotConfigured다() = runBlocking {
        val (transport, svc) = service(anonKey = "")
        assertEquals(ProfileResult.NotConfigured, svc.updateNickname("꽃박사"))
        assertTrue(transport.urls.isEmpty())
    }

    @Test
    fun 키가_없어도_빈_닉네임은_Invalid다() = runBlocking {
        val (_, svc) = service(baseUrl = "", anonKey = "")
        assertEquals(ProfileResult.Invalid, svc.updateNickname(""))
    }
}
