package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업로드 결과 분류와 전송 형식.
 *
 * **왜 테스트가 필요한가.** 업로드 실패는 **화면에 증상이 없다.** 도감은 로컬 파일로
 * 그려지므로 한 건도 안 올라가도 사용자에게는 정상으로 보인다. 드러나는 곳은
 * **랭킹뿐이고, 랭킹은 지금 더미라서 그것도 안 보인다.** 실기기로 확인할 수 없는 층이다.
 *
 * ⚠️ **네트워크를 부르지 않는다.** [DiscoveryUploader.Transport]를 갈아 끼운다.
 *    아래 응답 본문은 **실제 Supabase가 준 것을 그대로 옮긴 것이다**(진행.md (30) 표).
 *    내가 상상한 형식으로 쓰면 테스트만 통과하고 실제로는 안 된다.
 */
class DiscoveryUploaderTest {

    private val myId = "b92a22d2-2fe7-41ff-956e-a251731171af"

    private fun sample(
        id: String = "11111111-1111-4111-8111-111111111111",
        userId: String = myId,
        lat: Double? = 37.5445,
        lng: Double? = 127.0557,
        localPhoto: String? = "photo-1.jpg",
    ) = Discovery(
        id = id,
        userId = userId,
        flowerId = 1,
        photoUrl = null,
        localPhotoPath = localPhoto,
        lat = lat,
        lng = lng,
        placeName = "서울숲",
        dongCode = "1120052000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = 1786000000000L,
        capturedAt = 1786000000000L,
    )

    /** 응답을 미리 정해 두고, 무엇을 어떻게 보냈는지 기록한다. */
    private class FakeTransport(
        private val responses: MutableList<Pair<Int, String>>,
    ) : DiscoveryUploader.Transport {
        val bodies = mutableListOf<String>()
        val bearers = mutableListOf<String>()
        val prefers = mutableListOf<String>()
        val urls = mutableListOf<String>()

        override suspend fun post(
            url: String,
            apiKey: String,
            bearer: String,
            prefer: String,
            body: String,
        ): Pair<Int, String> {
            urls += url
            bodies += body
            bearers += bearer
            prefers += prefer
            return if (responses.isEmpty()) 500 to "" else responses.removeAt(0)
        }
    }

    private fun uploader(
        vararg responses: Pair<Int, String>,
        transport: FakeTransport = FakeTransport(responses.toMutableList()),
    ) = transport to uploaderWith(transport, StubAuth())

    /**
     * ⚠️ `baseUrl`·`anonKey`를 **반드시 넣는다.** 기본값은 `AppSecrets`(전역)이고,
     *    그러면 이 테스트가 **그 맥에 `local.properties`가 있느냐에 따라 통과 여부가 갈린다.**
     * ⚠️ `log`도 갈아 끼운다 — 기본값은 `android.util.Log`라 JVM에서 던진다.
     */
    private fun uploaderWith(transport: FakeTransport, auth: StubAuth) = DiscoveryUploader(
        auth = auth,
        baseUrl = "https://test.supabase.co",
        anonKey = "anon-key",
        transport = transport,
        log = { logs += it },
    )

    private val logs = mutableListOf<String>()

    // ────────────────────────────────────────────────────────────────
    // 전송 형식
    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **로컬 전용 필드가 전송에 섞이면 안 된다.**
     *
     * 빨개지는 경우: [DiscoveryStore.toWireJson]이 `_local_photo_path`를 안 지우면.
     * 그러면 실제로는 **PostgREST가 요청 전체를 400으로 거부한다**
     * (`PGRST204 Could not find the '_local_photo_path' column`). 실측했다.
     * 한 건도 안 올라가고 이유는 로그에만 남는다.
     */
    @Test
    fun 로컬_전용_필드는_서버로_보내지_않는다() = runBlocking<Unit> {
        val (t, up) = uploader(201 to "")
        up.upload(sample(localPhoto = "photo-1.jpg"))

        val sent = JSONObject(t.bodies.single())
        assertFalse(
            "로컬 사진 경로가 전송에 섞였다 — 서버는 PGRST204로 요청 전체를 거부한다",
            sent.has("_local_photo_path"),
        )
        // 계약 컬럼은 그대로 있어야 한다. 위 검사는 "다 지워도" 통과하기 때문이다.
        assertEquals("계약 컬럼이 사라졌다", myId, sent.getString("user_id"))
        assertEquals(1, sent.getInt("flower_id"))
        assertTrue("좌표가 사라졌다", sent.has("lat") && sent.has("lng"))
    }

    /**
     * 서버가 채우는 컬럼을 클라이언트가 보내지 않는다.
     *
     * 빨개지는 경우: `captured_date`를 payload에 넣으면. **B-5 하루 1회를 우회할 수 있다** —
     * 어제 날짜를 보내면 같은 꽃을 같은 장소에서 하루에 여러 번 등록할 수 있다.
     */
    @Test
    fun 서버가_채우는_날짜를_보내지_않는다() = runBlocking<Unit> {
        val (t, up) = uploader(201 to "")
        up.upload(sample())
        assertFalse(
            "captured_date를 클라이언트가 보내면 B-5 하루 1회를 우회할 수 있다",
            JSONObject(t.bodies.single()).has("captured_date"),
        )
    }

    /**
     * 🔴 **`not null` 컬럼을 반드시 보낸다.**
     *
     * 빨개지는 경우: `ai_confidence`·`ai_picked_rank`를 `putOpt`로 되돌리거나
     * 모델을 다시 nullable로 만들면. DB가 `not null`이라 키가 빠진 요청은
     * **`400 23502`로 거부되고, 400은 영구 거절**이라 그 기록은 다시는 올라가지 않는다
     * (도감에는 보이므로 아무도 모른다).
     *
     * 좌표는 반대다 — 없을 수 있고 서버도 null을 허용한다. **둘을 같이 검사해서
     * "그냥 다 넣는다"로 통과하지 못하게** 한다.
     */
    @Test
    fun not_null_컬럼은_빠지지_않는다() = runBlocking<Unit> {
        val (t, up) = uploader(201 to "")
        up.upload(sample(lat = null, lng = null))

        val sent = JSONObject(t.bodies.single())
        assertTrue("ai_confidence가 빠졌다 — 서버는 23502로 영구 거절한다", sent.has("ai_confidence"))
        assertTrue("ai_picked_rank가 빠졌다", sent.has("ai_picked_rank"))
        assertEquals(0.82, sent.getDouble("ai_confidence"), 0.0001)
        // 좌표는 없을 수 있다. 없는 키를 억지로 넣으면 서버가 명시적 null로 받아 덮어쓴다.
        assertFalse("좌표 없는 기록에 lat을 만들어 넣었다", sent.has("lat"))
    }

    /**
     * 🔴 **재전송이 안전해야 한다 — upsert 헤더를 붙인다.**
     *
     * 빨개지는 경우: `Prefer: resolution=merge-duplicates`가 빠지면.
     * 실측: 같은 id를 그냥 POST하면 **409 `23505 discoveries_pkey`**다.
     * 업로드는 재시도되는 경로라서(응답을 못 받았지만 서버에는 들어간 경우)
     * 그 기록이 **영원히 실패로 남고 앱은 매번 다시 보낸다.**
     */
    @Test
    fun 재전송이_안전하도록_upsert로_보낸다() = runBlocking<Unit> {
        val (t, up) = uploader(200 to "")
        up.upload(sample())
        assertEquals("upsert가 아니면 재시도가 409로 영구 실패한다", "resolution=merge-duplicates", t.prefers.single())
        assertTrue("엔드포인트가 틀렸다: ${t.urls.single()}", t.urls.single().endsWith("/rest/v1/discoveries"))
    }

    /**
     * **anon 키가 아니라 사용자 토큰으로 보낸다.**
     *
     * 빨개지는 경우: `Authorization`에 anon 키를 넣으면. 그러면 `auth.uid()`가 null이라
     * RLS(`user_id = auth.uid()`)가 **42501로 전부 거부한다**(실측).
     */
    @Test
    fun 사용자_토큰으로_보낸다() = runBlocking<Unit> {
        val (t, up) = uploader(201 to "")
        up.upload(sample())
        assertEquals("anon 키로 보내면 RLS가 42501로 전부 막는다", StubAuth.TOKEN, t.bearers.single())
    }

    // ────────────────────────────────────────────────────────────────
    // 결과 분류 — Rejected와 Failed를 가르는 것이 이 클래스의 핵심이다
    // ────────────────────────────────────────────────────────────────

    @Test
    fun 성공은_Uploaded다() = runBlocking<Unit> {
        val (_, up) = uploader(201 to "")
        assertEquals(DiscoveryUploader.Result.Uploaded, up.upload(sample()))
    }

    /**
     * 🔴 **RLS 위반(403 · 42501)은 재시도 대상이다.**
     *
     * 빨개지는 경우: 403을 `Rejected`로 분류하면. 실측으로 이 코드가 나오는 경우는
     * **`user_id`가 로그인 계정과 다를 때**다 — 로그인 전에 올렸거나 이관 전이다.
     * 둘 다 **다음 실행에 풀린다.** 영구 거절로 표시하면 그 기록은 **다시는 올라가지
     * 않고**, 도감에는 보이므로 아무도 모른다. 랭킹에서만 조용히 빠진다.
     */
    @Test
    fun RLS_위반은_영구_거절이_아니다() = runBlocking<Unit> {
        val body = """{"code":"42501","message":"new row violates row-level security policy for table \"discoveries\""}"""
        val (_, up) = uploader(403 to body)
        val result = up.upload(sample())
        assertTrue(
            "403을 영구 거절로 세면 그 기록은 다시는 올라가지 않는다: $result",
            result is DiscoveryUploader.Result.Failed,
        )
    }

    /**
     * **B-5 위반(409 · 하루 1회 제약)은 재시도해도 같다.**
     *
     * 빨개지는 경우: 이걸 `Failed`로 두면 앱이 매 실행마다 다시 보내고 매번 409를 맞는다.
     */
    @Test
    fun B5_하루중복은_영구_거절이다() = runBlocking<Unit> {
        val body = """{"code":"23505","message":"duplicate key value violates unique constraint \"discoveries_same_flower_place_per_day\""}"""
        val (_, up) = uploader(409 to body)
        val result = up.upload(sample())
        assertTrue("B-5 위반은 재시도해도 같다: $result", result is DiscoveryUploader.Result.Rejected)
        assertEquals("23505", (result as DiscoveryUploader.Result.Rejected).pgCode)
    }

    /**
     * 🔴 **같은 409·같은 23505인데 pkey 충돌은 "이미 올라갔다"다.**
     *
     * 위 테스트와 **상태 코드도 pg 코드도 같다** — 제약 이름만 다르다.
     * 빨개지는 경우: 상태 코드나 `23505`만 보고 가르면. 그러면 이미 서버에 있는 기록을
     * 실패로 세고 **영원히 재시도한다.**
     */
    @Test
    fun 이미_올라간_기록은_성공으로_센다() = runBlocking<Unit> {
        val body = """{"code":"23505","message":"duplicate key value violates unique constraint \"discoveries_pkey\""}"""
        val (_, up) = uploader(409 to body)
        assertEquals(
            "pkey 충돌은 이미 서버에 있다는 뜻이다 — 실패로 세면 영원히 재시도한다",
            DiscoveryUploader.Result.Uploaded,
            up.upload(sample()),
        )
    }

    /**
     * 계약에 없는 컬럼(`PGRST204`)은 **코드 버그**라서 영구 거절이다.
     *
     * 빨개지는 경우: 400을 재시도로 두면 잘못된 payload를 무한히 다시 보낸다.
     */
    @Test
    fun 모르는_컬럼은_영구_거절이다() = runBlocking<Unit> {
        val body = """{"code":"PGRST204","message":"Could not find the 'foo' column of 'discoveries' in the schema cache"}"""
        val (_, up) = uploader(400 to body)
        val result = up.upload(sample())
        assertTrue("$result", result is DiscoveryUploader.Result.Rejected)
        assertEquals("PGRST204", (result as DiscoveryUploader.Result.Rejected).pgCode)
    }

    /**
     * 5xx·타임아웃·요청과다는 **시간이 풀어 준다.**
     *
     * 빨개지는 경우: 이 셋 중 하나라도 `Rejected`가 되면 서버가 잠깐 아픈 동안 찍은
     * 꽃이 **영구히 안 올라간다.**
     */
    @Test
    fun 일시적_실패는_재시도_대상이다() = runBlocking<Unit> {
        for (code in listOf(500, 502, 503, 408, 429)) {
            val (_, up) = uploader(code to "")
            val result = up.upload(sample())
            assertTrue("HTTP $code 를 영구 거절로 세면 안 된다: $result", result is DiscoveryUploader.Result.Failed)
        }
    }

    /**
     * 🔴 **401은 토큰을 갱신해서 한 번 더 시도한다.**
     *
     * 빨개지는 경우: 401에 바로 포기하면. 기기 시계가 바뀌었거나 서버가 세션을 폐기하면
     * **그 실행 내내 모든 업로드가 401**이 되고, 화면에는 증상이 없다.
     */
    @Test
    fun 토큰이_만료되면_갱신해서_다시_보낸다() = runBlocking<Unit> {
        val transport = FakeTransport(mutableListOf(401 to """{"code":"PGRST301"}""", 201 to ""))
        val auth = StubAuth()
        val up = uploaderWith(transport, auth)

        assertEquals(DiscoveryUploader.Result.Uploaded, up.upload(sample()))
        assertEquals("두 번 보내지 않았다 — 갱신 후 재시도가 없다", 2, transport.bodies.size)
        assertEquals("갱신을 부르지 않았다", 1, auth.refreshCalls)
        assertEquals(
            "재시도에 갱신된 토큰을 쓰지 않았다 — 같은 토큰으로 또 401을 맞는다",
            StubAuth.FRESH_TOKEN,
            transport.bearers[1],
        )
    }

    /** 갱신도 실패하면 재시도 대상으로 남긴다 (영구 거절이 아니다). */
    @Test
    fun 갱신이_실패하면_보류한다() = runBlocking<Unit> {
        val transport = FakeTransport(mutableListOf(401 to ""))
        val auth = StubAuth(refreshResult = null)
        val up = uploaderWith(transport, auth)

        val result = up.upload(sample())
        assertTrue("$result", result is DiscoveryUploader.Result.Failed)
        assertEquals(401, (result as DiscoveryUploader.Result.Failed).code)
    }

    /**
     * ⚠️ [AuthService]를 상속으로 흉내내지 않는다 — 생성자가 `Context`를 요구한다.
     *    업로더가 실제로 쓰는 것은 **토큰 두 함수뿐**이라 그것만 인터페이스로 본다.
     */
    private class StubAuth(
        private val token: String? = TOKEN,
        private val refreshResult: String? = FRESH_TOKEN,
    ) : TokenSource {
        var refreshCalls = 0
        override suspend fun accessToken(): String? = token
        override suspend fun refresh(): String? {
            refreshCalls++
            return refreshResult
        }

        companion object {
            const val TOKEN = "stub-access-token"
            const val FRESH_TOKEN = "stub-fresh-token"
        }
    }
}
