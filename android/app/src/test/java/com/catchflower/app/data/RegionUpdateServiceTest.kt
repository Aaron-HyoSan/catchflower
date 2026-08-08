package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 활동 지역 저장.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가 — 200이 "저장됐다"가 아니다
 *
 * **실측**: 없는 uuid로 PATCH하면 PostgREST가 **HTTP 200 + 본문 `[]`** 를 준다
 * (`app/src/test/resources/supabase/patch_zero_rows.json`).
 *
 * 그리고 그 상황은 실제로 일어난다: 익명 로그인이 실패하면 [AuthAccount.userId]가
 * **기기 로컬 uuid**를 준다(`LocalUser.id` — 서버에 없는 값). 그러면 화면 02는
 * `동네 선택 완료`로 넘어가고, **랭킹은 영원히 빈다.** 사용자는 동네를 골랐다고
 * 믿는데 서버에는 없고, 6개월 규칙 때문에 다시 고를 화면도 안 열린다.
 * **오류는 한 건도 안 난다** — 이 테스트가 없으면 아무도 모른다.
 *
 * ## ⚠️ 응답 본문은 **실제 Supabase가 준 것**이다
 *
 * `probe_region_patch.py`·`probe_region_errors.py`·`probe_23514.py`로 익명 계정을
 * 만들어 실측했다(진행.md (32)). 봉투 형식을 상상해서 쓰면 **테스트만 통과하고
 * 앱은 안 된다** — (22)에서 실제로 그랬다.
 *
 * 🔴 **`P0001`(6개월 트리거)만은 실측하지 못했다.** 측정 시점에 0004가 아직 서버에
 *    적용돼 있지 않아서(법정동 코드 `1144012400`을 보냈는데 **HTTP 200으로 통과**했다 —
 *    `patch_check_violation.json`) 트리거를 발동시킬 방법이 없었다. 그래서 `P0001`
 *    본문은 **같은 계열로 실측된 `23514` 봉투**(`insert_check_violation.json`:
 *    `{"code":…,"details":null,"hint":null,"message":…}`)에 code만 바꿔 만들었다.
 *    ⚠️ 오너가 0004를 적용한 뒤 `probe_region_patch.py` ⑦번을 다시 돌려
 *    **실측 본문으로 교체해야 한다.** 지금 재고 있는 것은 "그 봉투에서 code를 읽는가"까지다.
 */
class RegionUpdateServiceTest {

    private val myId = "42f5e652-de06-4aab-ad93-482c89cf6081"

    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<Pair<Int, String>>,
    ) : RegionUpdateService.Transport {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val bearers = mutableListOf<String>()
        val apiKeys = mutableListOf<String>()
        var throwIo = false

        override suspend fun patch(
            url: String,
            apiKey: String,
            bearer: String,
            body: String,
        ): Pair<Int, String> {
            urls += url
            bodies += body
            bearers += bearer
            apiKeys += apiKey
            if (throwIo) throw java.io.IOException("네트워크 끊김")
            return if (responses.isEmpty()) 500 to "" else responses.removeAt(0)
        }
    }

    private class StubAuth(
        private val id: String,
        private val token: String? = TOKEN,
        private val freshToken: String? = FRESH,
    ) : AuthAccount {
        var refreshCalls = 0
        override suspend fun userId(): String = id
        override fun needsReauth(): Boolean = false
        override fun reset() = Unit
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

    private val candidate = RegionCandidate(
        regionName = "서울특별시 마포구 연남동",
        dongCode = "1144071000",
        guCode = "11440",
    )

    /**
     * ⚠️ `baseUrl`·`anonKey`를 반드시 넣는다 — 기본값은 전역 `AppSecrets`라
     *    **그 맥에 `local.properties`가 있느냐로 통과 여부가 갈린다.**
     * ⚠️ `log`도 갈아 끼운다 — `android.util.Log`는 JVM에서 던진다.
     */
    private fun service(
        vararg responses: Pair<Int, String>,
        auth: StubAuth = StubAuth(myId),
    ): Pair<FakeTransport, RegionUpdateService> {
        val transport = FakeTransport(responses.toMutableList())
        return transport to RegionUpdateService(
            auth = auth,
            baseUrl = "https://example.supabase.co",
            anonKey = "test-anon",
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
        assertEquals(RegionUpdateResult.Saved, svc.save(candidate))

        // 🔴 **`?id=eq.{내 uuid}` 필터가 반드시 있어야 한다.** 없으면 PostgREST가
        //    테이블 전체 수정으로 해석한다 — RLS 정책 한 줄에 의존하게 된다.
        assertEquals("https://example.supabase.co/rest/v1/users?id=eq.$myId", transport.urls[0])
        assertEquals("test-anon", transport.apiKeys[0])
        assertEquals(StubAuth.TOKEN, transport.bearers[0])
    }

    /**
     * ⚠️ **세 칸을 항상 같이 보낸다.** `dong_code`만 바꾸고 `gu_code`를 안 바꾸면
     *    B-6 구 확장이 **옛 구로 묶인다** — 오류 없이 랭킹에 남의 동네 사람이 섞인다.
     */
    @Test
    fun 세_칸을_함께_보낸다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.save(candidate)

        val sent = JSONObject(transport.bodies[0])
        assertEquals("서울특별시 마포구 연남동", sent.getString("region_name"))
        assertEquals("1144071000", sent.getString("dong_code"))
        assertEquals("11440", sent.getString("gu_code"))
    }

    /**
     * 🔴 **`region_changed_at`을 보내지 않는다.** 0004 트리거가 `now()`로 채운다 —
     *    앱이 보내면 **7개월 전 날짜를 보내 6개월 규칙을 우회할 수 있다.**
     *    트리거가 덮으므로 보내도 무시되지만, 보내는 코드를 두면 다음 사람이
     *    "앱이 정하는 값"이라고 읽는다.
     */
    @Test
    fun 변경_시각을_보내지_않는다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        svc.save(candidate)

        val sent = JSONObject(transport.bodies[0])
        assertTrue(
            "region_changed_at을 보냈다: ${transport.bodies[0]}",
            !sent.has("region_changed_at"),
        )
        // 계약에 있는 세 칸만 보낸다. 다른 칸을 얹으면 PGRST204로 저장 자체가 죽는다.
        assertEquals(setOf("region_name", "dong_code", "gu_code"), sent.keys().asSequence().toSet())
    }

    // ── 🔴 0행 검사 ─────────────────────────────────────────────────

    /**
     * 🔴 **이 테스트가 이 파일의 이유다.** 실측 `patch_zero_rows.json`은
     *    **HTTP 200 + `[]`**다. 이걸 성공으로 읽으면 화면 02는 완료로 넘어가고
     *    랭킹은 영원히 빈다 — 그리고 6개월간 다시 고를 수 없다.
     */
    @Test
    fun 이백이지만_영행이면_실패다() = runBlocking {
        val (_, svc) = service(200 to fixture("patch_zero_rows"))
        assertEquals(RegionUpdateResult.Failed(200), svc.save(candidate))
        // 원인을 알 수 있어야 한다 — "저장이 안 되네"만 남으면 아무도 못 찾는다.
        assertTrue(logs.any { it.contains("0행") })
    }

    /**
     * ⚠️ **본문이 비면 통과시킨다.** 실측 `patch_no_prefer.json`은 `Prefer` 헤더가
     *    없을 때 **204 + 빈 본문**이다. 그걸 "저장 안 됨"으로 단정하면 실제로 저장된
     *    사용자에게 오류를 띄우고 **다시 저장하게** 되는데, 그건 6개월 규칙을 소모한다.
     *    0행 검사의 목적은 **0행을 확실히 아는 경우만** 잡는 것이다.
     */
    @Test
    fun 본문이_없으면_저장으로_본다() = runBlocking {
        val (_, svc) = service(204 to fixture("patch_no_prefer"))
        assertEquals(RegionUpdateResult.Saved, svc.save(candidate))
    }

    /** 형식을 읽을 수 없어도 같은 이유로 통과시킨다 — 위 주석 참고. */
    @Test
    fun 형식을_모르면_저장으로_본다() = runBlocking {
        val (_, svc) = service(200 to """{"이건":"배열이 아니다"}""")
        assertEquals(RegionUpdateResult.Saved, svc.save(candidate))
    }

    // ── 규칙 거절 vs 일시 실패 ──────────────────────────────────────

    /**
     * 🔴 **`23514`는 앱 버그다.** 0004 check 제약에 걸린 것이므로 법정동 코드나
     *    어긋난 `gu_code`를 보냈다는 뜻이다 — 재시도해도 같은 결과이므로
     *    [RegionUpdateResult.Rejected]다. 그리고 **원인이 로그에 남아야 한다**:
     *    조용히 넘기면 "저장이 안 되네"만 보이고 왜인지 알 수 없다.
     *
     * ⚠️ 봉투는 실측이다(`insert_check_violation.json` — `discoveries_note_length`로
     *    같은 `23514`를 실제로 냈다).
     */
    @Test
    fun 실측_23514는_규칙_거절이다() = runBlocking {
        val (_, svc) = service(400 to fixture("insert_check_violation"))
        val result = svc.save(candidate)
        assertEquals(RegionUpdateResult.Rejected(400, "23514"), result)
        assertTrue(
            "제약 위반 원인이 로그에 없다: $logs",
            logs.any { it.contains("행정동 코드가 아닐 수 있다") },
        )
    }

    /**
     * ⚠️ `PGRST204`는 컬럼 이름이 계약과 다르다는 뜻이다 — 실측 봉투다
     *    (`patch_unknown_column.json`). 재시도해도 같으므로 [RegionUpdateResult.Rejected]다.
     */
    @Test
    fun 실측_PGRST204는_규칙_거절이다() = runBlocking {
        val (_, svc) = service(400 to fixture("patch_unknown_column"))
        assertEquals(RegionUpdateResult.Rejected(400, "PGRST204"), svc.save(candidate))
    }

    /**
     * 6개월 규칙(0004 트리거).
     *
     * 🔴 **봉투는 실측 `23514`에서 code만 바꿨다** — 측정 시점에 0004가 서버에
     *    없었다(클래스 주석). 오너 적용 후 실측으로 교체할 자리다.
     *    지금 고정되는 것은 "이 봉투에서 code를 읽고 Rejected로 보낸다"까지다.
     */
    @Test
    fun P0001은_규칙_거절이다() = runBlocking {
        val body = """{"code":"P0001","details":null,"hint":null,""" +
            """"message":"활동 지역은 6개월에 한 번만 변경할 수 있습니다"}"""
        assertEquals(RegionUpdateResult.Rejected(400, "P0001"), service(400 to body).second.save(candidate))
    }

    /**
     * 🔴 **모르는 pgCode는 [RegionUpdateResult.Failed]다.** `Rejected`로 만들면
     *    RLS·FK 문제인데 화면이 "6개월 규칙에 걸렸다"고 말하고, 사용자는
     *    **고칠 수 있는 문제를 영구적인 것으로 받아들인다.**
     *
     * ⚠️ 실측 봉투 두 개로 잰다: `42501`(RLS 위반 · 403)과 `23502`(NOT NULL · 400).
     */
    @Test
    fun 모르는_pgCode는_일시_실패다() = runBlocking {
        assertEquals(
            RegionUpdateResult.Failed(403),
            service(403 to fixture("insert_rls_violation")).second.save(candidate),
        )
        assertEquals(
            RegionUpdateResult.Failed(400),
            service(400 to fixture("insert_fk_violation")).second.save(candidate),
        )
    }

    @Test
    fun 오백은_일시_실패다() = runBlocking {
        val (_, svc) = service(500 to "")
        assertEquals(RegionUpdateResult.Failed(500), svc.save(candidate))
    }

    /** 봉투가 JSON이 아니어도 죽지 않는다(게이트웨이 HTML 등). */
    @Test
    fun JSON이_아닌_오류_본문에도_죽지_않는다() = runBlocking {
        val (_, svc) = service(502 to "<html>Bad Gateway</html>")
        assertEquals(RegionUpdateResult.Failed(502), svc.save(candidate))
    }

    // ── 토큰 ────────────────────────────────────────────────────────

    /**
     * 401은 **한 번 갱신하고 다시 보낸다.** 실측 봉투(`patch_bad_token.json` —
     * `PGRST301`)로 잰다.
     *
     * ⚠️ **같은 본문·같은 URL로 다시 보내야 한다.** 재시도에서 본문을 다시 만들면
     *    그 사이 사용자가 다른 동네를 고를 수 있다.
     */
    @Test
    fun 토큰이_만료되면_갱신하고_한_번_다시_보낸다() = runBlocking {
        val auth = StubAuth(myId)
        val (transport, svc) = service(
            401 to fixture("patch_bad_token"),
            200 to fixture("patch_saved"),
            auth = auth,
        )
        assertEquals(RegionUpdateResult.Saved, svc.save(candidate))
        assertEquals(1, auth.refreshCalls)
        assertEquals(2, transport.urls.size)
        assertEquals(transport.urls[0], transport.urls[1])
        assertEquals(transport.bodies[0], transport.bodies[1])
        assertEquals(StubAuth.FRESH, transport.bearers[1])
    }

    /** ⚠️ **두 번 갱신하지 않는다** — 실패하는 토큰으로 무한히 돌면 안 된다. */
    @Test
    fun 갱신_후에도_401이면_거기서_멈춘다() = runBlocking {
        val auth = StubAuth(myId)
        val (transport, svc) = service(
            401 to fixture("patch_bad_token"),
            401 to fixture("patch_bad_token"),
            auth = auth,
        )
        assertEquals(RegionUpdateResult.Failed(401), svc.save(candidate))
        assertEquals(1, auth.refreshCalls)
        assertEquals(2, transport.urls.size)
    }

    @Test
    fun 토큰이_아예_없으면_보내지_않는다() = runBlocking {
        val (transport, svc) = service(auth = StubAuth(myId, token = null))
        assertEquals(RegionUpdateResult.Failed(401), svc.save(candidate))
        assertEquals(0, transport.urls.size)
    }

    /**
     * 🔴 **uuid가 비면 보내지 않는다.** 필터가 `?id=eq.`로 끝나면 PostgREST가
     *    그걸 어떻게 읽든 우리가 의도한 요청이 아니다 — 보내기 전에 멈춘다.
     */
    @Test
    fun uuid를_모르면_보내지_않는다() = runBlocking {
        val (transport, svc) = service(auth = StubAuth(id = ""))
        assertEquals(RegionUpdateResult.Failed(401), svc.save(candidate))
        assertEquals(0, transport.urls.size)
        assertTrue(logs.any { it.contains("uuid") })
    }

    // ── 키 없는 빌드 · 네트워크 ─────────────────────────────────────

    /** 키 없는 빌드는 **오류가 아니다.** 호출도 하지 않는다. */
    @Test
    fun 키가_없으면_NotConfigured다() = runBlocking {
        val transport = FakeTransport(mutableListOf())
        val svc = RegionUpdateService(
            auth = StubAuth(myId),
            baseUrl = "",
            anonKey = "",
            transport = transport,
            log = { logs += it },
        )
        assertEquals(RegionUpdateResult.NotConfigured, svc.save(candidate))
        assertEquals(0, transport.urls.size)
    }

    /**
     * ⚠️ **IOException이 밖으로 나가면 안 된다.** 화면 02가 코루틴에서 부르므로
     *    던지면 **앱이 죽는다** — 비행기 모드에서 `시작하기`를 누른 사람이 그 경우다.
     */
    @Test
    fun 네트워크가_끊기면_실패로_돌려준다() = runBlocking {
        val (transport, svc) = service(200 to fixture("patch_saved"))
        transport.throwIo = true
        assertEquals(RegionUpdateResult.Failed(0), svc.save(candidate))
    }
}
