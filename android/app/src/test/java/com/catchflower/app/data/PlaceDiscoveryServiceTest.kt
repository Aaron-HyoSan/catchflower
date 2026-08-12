package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 15(장소 상세)가 읽는 층.
 *
 * 🔴 **여기 응답 본문 형식은 실측이다** — `Content-Range: 0-1/15`와 `42803`은 실제
 *    Supabase가 준 것이다([PlaceDiscoveryService] 주석의 그 측정). 다만 **행 내용은
 *    내가 만든 것**이라, 이 파일이 재는 것은 "무엇을 요청하고 받은 것을 어떻게 세는가"다.
 *
 * ⚠️ **각 테스트에 "빨개지는 경우"를 적었다.** 안 적으면 통과하는데 아무것도 안 재는
 *    테스트가 남는다 — 이 저장소에서 12번 그랬다.
 */
class PlaceDiscoveryServiceTest {

    private val meId = "5bf714f9-b172-4636-a878-bf8efbc26fb7"
    private val otherId = "3070caaa-0d53-4b2b-a71f-e5e230c6cda7"

    private val logs = mutableListOf<String>()

    private class FakeTransport(
        private val responses: MutableList<PlaceDiscoveryService.Transport.Response>,
    ) : PlaceDiscoveryService.Transport {
        val urls = mutableListOf<String>()
        val bearers = mutableListOf<String>()
        val apiKeys = mutableListOf<String>()

        override suspend fun get(
            url: String,
            apiKey: String,
            bearer: String,
        ): PlaceDiscoveryService.Transport.Response {
            urls += url
            bearers += bearer
            apiKeys += apiKey
            return if (responses.isEmpty()) {
                PlaceDiscoveryService.Transport.Response(500, "", null)
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
     *    **그 맥에 `local.properties`가 있느냐로 테스트 결과가 갈린다**
     *    ([ReactionServiceTest]와 같은 이유).
     */
    private fun service(
        transport: FakeTransport,
        auth: TokenSource = StubAuth(),
    ) = PlaceDiscoveryService(
        auth = auth,
        baseUrl = BASE,
        anonKey = ANON,
        transport = transport,
        log = { logs += it },
    )

    private fun ok(body: String, contentRange: String? = null) =
        PlaceDiscoveryService.Transport.Response(200, body, contentRange)

    /** 한 행. `created_at`은 ISO-8601이다 — 계약 1-3이 `timestamptz`다. */
    private fun row(
        id: String,
        userId: String = otherId,
        flowerId: Int = 12,
        createdAt: String = "2026-08-11T02:00:00+00:00",
        placeName: String? = "서울숲",
        visibility: String = "public",
    ): String {
        val place = placeName?.let { "\"$it\"" } ?: "null"
        return """
            {"id":"$id","user_id":"$userId","flower_id":$flowerId,"photo_url":null,
             "lat":37.5445,"lng":127.0374,"place_name":$place,
             "dong_code":"1120052000","gu_code":"11200","visibility":"$visibility",
             "ai_confidence":0.4123,"ai_picked_rank":1,"is_first_discovery":true,
             "note":null,"created_at":"$createdAt","captured_at":"$createdAt"}
        """.trimIndent()
    }

    private fun rows(vararg r: String) = "[${r.joinToString(",")}]"

    // ── 무엇을 요청하는가 ────────────────────────────────────────────

    /**
     * 🔴 빨개지는 경우: `visibility=neq.private`를 빼거나 `select`에서 `user_id`를
     *    뺐을 때. 둘 다 **결과가 그럴듯해 보이는** 결함이다 —
     *    앞은 내 비공개 기록을 `사람들의 기록`에 섞고, 뒤는 사용자가 **자기 기록에
     *    신고 버튼이 있는 화면**을 본다.
     */
    @Test
    fun 비공개를_빼고_작성자를_받아_온다() {
        val url = PlaceDiscoveryService.query(BASE, BoundingBox.of(37.5445, 127.0374, 300))

        assertTrue("주소가 discoveries 표가 아니다: $url", url.startsWith("$BASE/rest/v1/discoveries?"))
        assertTrue(
            "visibility=neq.private가 없다 — 내 비공개 기록이 '사람들의 기록'에 섞인다",
            url.contains("visibility=neq.private"),
        )
        assertTrue(
            "select에 user_id가 없다 — 내 기록을 걸러낼 수 없어 자기 기록에 신고 버튼이 붙는다",
            url.contains("user_id"),
        )
        // 최신순 · 상한. A 문서 화면 15의 `최신순 ▾`가 기본값이다.
        assertTrue(url.contains("order=created_at.desc"))
        assertTrue(url.contains("limit=${PlaceDiscoveryService.PAGE_SIZE}"))
        // 상자는 네 칸 전부 건다. 하나라도 빠지면 전국이 걸린다.
        listOf("lat=gte.", "lat=lte.", "lng=gte.", "lng=lte.").forEach {
            assertTrue("$it 가 없다 — 상자가 한쪽으로 열려 있다", url.contains(it))
        }
    }

    /**
     * 🔴 **위도 보정이 실제로 도는가.** 빨개지는 경우: `dLng`를 `cos` 없이
     *    `dLat`과 같게 두었을 때(= 상자가 정사각형이 아니게 되는 것을 못 잡게 됨).
     *
     * ⚠️ 증상이 **"제주에서만 옆 화단이 안 걸린다"** 이고, 화면은 기록이 없는 장소와
     *    똑같이 보인다. 눈으로 못 잡으니 여기서 숫자로 잡는다.
     */
    @Test
    fun 경도_폭은_위도_폭보다_넓고_북쪽에서_더_넓다() {
        fun ratio(lat: Double): Double {
            val b = BoundingBox.of(lat, 127.0, 300)
            return abs(b.maxLng - b.minLng) / abs(b.maxLat - b.minLat)
        }

        val jeju = ratio(33.5)
        val seoul = ratio(37.5)
        val north = ratio(38.6)

        // 경도 1도가 더 짧으니 같은 거리를 담으려면 경도 delta가 더 커야 한다.
        assertTrue("경도 폭이 위도 폭보다 넓지 않다 — cos 보정이 빠졌다 (서울 $seoul)", seoul > 1.0)
        // 북으로 갈수록 경도 1도가 더 짧아진다 → delta는 더 커진다.
        assertTrue("위도가 높은데 경도 폭이 안 넓어졌다 ($jeju → $seoul)", seoul > jeju)
        assertTrue("최북단이 서울보다 안 넓다 ($seoul → $north)", north > seoul)
        // 실측 계산값(1/cos)과 맞나. 0.834 → 1.199 · 0.793 → 1.261
        assertEquals(1.199, jeju, 0.005)
        assertEquals(1.261, seoul, 0.005)
    }

    /**
     * 빨개지는 경우: 극지 방어(`MIN_COS`)를 지웠을 때 — `cos(90°)`이 0에 수렴해
     * 폭이 무한이 되고, 그러면 상자가 지구 전체가 된다.
     */
    @Test
    fun 극지_좌표가_들어와도_폭이_폭발하지_않는다() {
        val b = BoundingBox.of(89.999, 127.0, 300)
        val span = abs(b.maxLng - b.minLng)
        assertTrue("경도 폭이 폭발했다($span) — 0으로 나누기 방어가 사라졌다", span < 1.0)
    }

    /** 사용자 토큰으로 부른다 — RLS 전부가 여기 달려 있다. anon 키로 부르면 남의 기록이 안 온다. */
    @Test
    fun 사용자_토큰으로_부른다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1")), "0-0/1")))
        service(t).near(37.5445, 127.0374, 300)

        assertEquals(StubAuth.TOKEN, t.bearers[0])
        assertEquals(ANON, t.apiKeys[0])
    }

    /** 빨개지는 경우: 401 재시도를 빼거나 두 번 이상 돌게 만들었을 때(무한 재시도). */
    @Test
    fun 토큰이_만료되면_한_번만_갱신한다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                PlaceDiscoveryService.Transport.Response(401, """{"code":"PGRST301"}""", null),
                ok(rows(row("d1")), "0-0/1"),
            ),
        )
        val auth = StubAuth()
        val res = service(t, auth).near(37.5445, 127.0374, 300)

        assertTrue(res is PlaceResult.Loaded)
        assertEquals("갱신을 한 번만 한다", 1, auth.refreshCalls)
        assertEquals(2, t.urls.size)
        assertEquals("갱신한 토큰으로 다시 불러야 한다", StubAuth.FRESH, t.bearers[1])
    }

    // ── 총 개수 ──────────────────────────────────────────────────────

    /**
     * 🔴 빨개지는 경우: `*`를 0으로 읽게 바꿨을 때. **기록이 있는 장소가 `기록 0개`로
     *    뜨고 화면은 완벽히 정상으로 보인다.**
     */
    @Test
    fun 총수를_모를_때는_0이_아니라_null이다() {
        assertEquals(15, PlaceDiscoveryService.totalFrom("0-1/15"))
        assertNull("`*`를 숫자로 읽었다", PlaceDiscoveryService.totalFrom("0-1/*"))
        assertNull("헤더가 없는데 값을 만들었다", PlaceDiscoveryService.totalFrom(null))
        assertNull("형식이 깨졌는데 값을 만들었다", PlaceDiscoveryService.totalFrom("garbage"))
    }

    /**
     * 🔴 빨개지는 경우: `truncated`를 `rows.size >= PAGE_SIZE`만으로 판정하게 바꿨을 때.
     *    상한보다 적게 받았는데 서버 총수가 더 크면 **그때도 잘린 것**이다
     *    (다른 필터가 걸렸거나 페이지가 접혔을 때).
     */
    @Test
    fun 서버_총수가_받은_행보다_크면_잘린_것이다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1"), row("d2")), "0-1/15")))
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertEquals(2, v.rows.size)
        assertEquals(15, v.totalOnServer)
        assertTrue("총수 15 > 받은 2인데 잘렸다고 안 했다", v.truncated)
        // `기록 12개`는 서버가 아는 값을 쓴다 — 받은 행을 세면 2개가 되어 조용히 틀린다.
        assertEquals(15, v.recordCount)
    }

    /** 다 받았으면 잘리지 않았다. (이게 없으면 위 테스트는 항상 true로도 통과한다.) */
    @Test
    fun 전부_받았으면_잘리지_않았다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1"), row("d2")), "0-1/2")))
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertFalse("전부 받았는데 잘렸다고 했다 — 화면이 지표를 못 그린다", v.truncated)
        assertEquals(2, v.recordCount)
    }

    /** 헤더를 못 읽으면 받은 행을 센다. ⚠️ 그때도 `truncated`는 꺼져 있지 않다면 안 된다. */
    @Test
    fun 헤더가_없으면_받은_행을_세지만_총수는_모른다고_한다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1"), row("d2")), null)))
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertNull(v.totalOnServer)
        assertEquals(2, v.recordCount)
    }

    // ── 세는 규칙 ────────────────────────────────────────────────────

    /**
     * `꽃 종류 5종 · 기록 12개`. 빨개지는 경우: `speciesCount`가 중복을 안 지울 때
     * (같은 꽃을 세 번 찍은 장소가 `꽃 3종`으로 뜬다).
     */
    @Test
    fun 같은_종을_여러_번_찍어도_한_종이다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok(
                    rows(
                        row("d1", flowerId = 12),
                        row("d2", flowerId = 12),
                        row("d3", flowerId = 88),
                    ),
                    "0-2/3",
                ),
            ),
        )
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertEquals(2, v.speciesCount)
        assertEquals(3, v.recordCount)
    }

    /** `이곳에서 발견된 꽃` 순서 = 최신순, 중복 없이. 빨개지는 경우: 정렬을 지웠을 때. */
    @Test
    fun 발견된_꽃은_최신순이고_중복이_없다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok(
                    rows(
                        row("d1", flowerId = 12, createdAt = "2026-08-01T00:00:00+00:00"),
                        row("d2", flowerId = 88, createdAt = "2026-08-11T00:00:00+00:00"),
                        row("d3", flowerId = 12, createdAt = "2026-08-05T00:00:00+00:00"),
                    ),
                    "0-2/3",
                ),
            ),
        )
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertEquals(listOf(88, 12), v.flowerIdsRecentFirst)
    }

    /** `이번 주 3개`. 경계는 **포함**이다 — 주 시작 자정에 찍은 기록은 이번 주다. */
    @Test
    fun 이번_주_개수는_경계를_포함한다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok(
                    rows(
                        row("d1", createdAt = "2026-08-10T00:00:00Z"),
                        row("d2", createdAt = "2026-08-11T05:00:00Z"),
                        row("d3", createdAt = "2026-08-09T23:59:59Z"),
                    ),
                    "0-2/3",
                ),
            ),
        )
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        val weekStart = DiscoveryStore.decodeTime("2026-08-10T00:00:00Z")
        assertEquals("주 시작 자정 기록이 빠졌다", 2, v.countSince(weekStart))
    }

    /**
     * 🔴 `사람들의 기록`에 내 기록을 섞지 않는다. 빨개지는 경우: 필터를 지웠을 때 —
     *    사용자가 **자기 기록을 신고하는** 화면을 본다.
     */
    @Test
    fun 남의_기록만_고른다() = runBlocking {
        val t = FakeTransport(
            mutableListOf(
                ok(rows(row("d1", userId = meId), row("d2", userId = otherId)), "0-1/2"),
            ),
        )
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        val others = v.othersRecords(meId)
        assertEquals(1, others.size)
        assertEquals("d2", others[0].id)
        // 로그인 정보가 없으면(있을 수 없지만) 지우지 않는다 — 지우면 목록이 빈다.
        assertEquals(2, v.othersRecords(null).size)
    }

    // ── 읽기 ────────────────────────────────────────────────────────

    /**
     * 🔴 빨개지는 경우: `place_name`을 `optString`으로 읽게 바꿨을 때.
     *
     * ⚠️ **이 단정은 JVM에서 약하다** — 테스트 라이브러리는 JSON null에 `""`를 주고
     *    기기는 `"null"`을 준다([JsonNullTest]의 그 표). 그래서 `null`인지만 잰다.
     *    소스 검사는 [JsonNullTest]가 이 파일까지 훑는다.
     */
    @Test
    fun 장소명이_JSON_null이면_코틀린_null이다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1", placeName = null)), "0-0/1")))
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        assertNull("장소명에 문자열이 들어갔다: ${v.rows[0].placeName}", v.rows[0].placeName)
    }

    @Test
    fun 행을_모델로_읽는다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1")), "0-0/1")))
        val v = (service(t).near(37.5445, 127.0374, 300) as PlaceResult.Loaded).value

        val d = v.rows[0]
        assertEquals(otherId, d.userId)
        assertEquals(12, d.flowerId)
        assertEquals(Visibility.PUBLIC, d.visibility)
        assertEquals(0.4123f, d.aiConfidence, 0.0001f)
        assertEquals(1, d.aiPickedRank)
        assertTrue(d.isFirstDiscovery)
        assertEquals(DiscoveryStore.decodeTime("2026-08-11T02:00:00+00:00"), d.createdAt)
    }

    // ── 실패 ────────────────────────────────────────────────────────

    /**
     * 🔴 빨개지는 경우: 파싱 실패를 빈 목록으로 삼키게 바꿨을 때.
     *    **그러면 화면이 "이 장소에 기록이 없다"가 되고 서버가 바뀐 사실을 아무도 모른다.**
     */
    @Test
    fun 형식이_바뀌면_빈_목록이_아니라_실패다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok("""[{"id":"d1"}]""", "0-0/1")))
        val res = service(t).near(37.5445, 127.0374, 300)

        val f = res as PlaceResult.Failed
        assertEquals(200, f.code)
        assertEquals(PlaceDiscoveryService.PARSE_FAILED, f.pgCode)
        assertTrue(logs.any { it.contains("읽을 수 없다") })
    }

    /**
     * 🔴 `visibility`에 모르는 값이 오면 [Visibility.fromWire]가 **던진다.**
     *    그것도 빈 목록이 아니라 실패다 — 던지는 것을 감싸지 않으면 앱이 죽는다.
     */
    @Test
    fun 모르는_공개범위도_실패로_받는다() = runBlocking {
        val t = FakeTransport(mutableListOf(ok(rows(row("d1", visibility = "team")), "0-0/1")))
        val res = service(t).near(37.5445, 127.0374, 300)

        assertEquals(PlaceDiscoveryService.PARSE_FAILED, (res as PlaceResult.Failed).pgCode)
    }

    /**
     * 집계를 서버에 맡기려 하면 `400 42803`이 온다(실측). 그 코드가 화면까지 실려야
     * **"왜 실패했는지"** 를 다시 찾지 않는다.
     */
    @Test
    fun 실패는_HTTP코드와_pg코드를_같이_싣는다() = runBlocking {
        val body = """{"code":"42803","message":"column must appear in the GROUP BY clause"}"""
        val t = FakeTransport(mutableListOf(PlaceDiscoveryService.Transport.Response(400, body, null)))
        val res = service(t).near(37.5445, 127.0374, 300)

        val f = res as PlaceResult.Failed
        assertEquals(400, f.code)
        assertEquals("42803", f.pgCode)
    }

    /** 네트워크가 죽어도 예외를 위로 던지지 않는다. 화면은 `연결이 불안정해요…`를 띄운다. */
    @Test
    fun 전송이_실패하면_실패를_돌려준다() = runBlocking {
        val t = object : PlaceDiscoveryService.Transport {
            override suspend fun get(url: String, apiKey: String, bearer: String) =
                throw java.io.IOException("no route to host")
        }
        val res = PlaceDiscoveryService(
            auth = StubAuth(),
            baseUrl = BASE,
            anonKey = ANON,
            transport = t,
            log = { logs += it },
        ).near(37.5445, 127.0374, 300)

        assertEquals(0, (res as PlaceResult.Failed).code)
    }

    /** 키 없는 빌드에서는 문구도 띄우지 않는다 — 사용자 탓이 아니다. */
    @Test
    fun 키가_없으면_NotConfigured다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val res = PlaceDiscoveryService(
            auth = StubAuth(),
            baseUrl = "",
            anonKey = "",
            transport = t,
            log = { logs += it },
        ).near(37.5445, 127.0374, 300)

        assertEquals(PlaceResult.NotConfigured, res)
        assertTrue("키가 없는데 서버를 불렀다", t.urls.isEmpty())
    }

    /** 로그인 전이면 부르지 않는다 — RLS가 익명에게는 아무 행도 안 준다. */
    @Test
    fun 토큰이_없으면_부르지_않는다() = runBlocking {
        val t = FakeTransport(mutableListOf())
        val res = service(t, StubAuth(token = null, refreshResult = null))
            .near(37.5445, 127.0374, 300)

        assertEquals(401, (res as PlaceResult.Failed).code)
        assertTrue(t.urls.isEmpty())
    }

    // ── 값으로 잴 수 없는 것 ─────────────────────────────────────────

    /**
     * 🔴 **`Prefer: count=exact`는 값으로 잴 수 없다.** 그 헤더를 붙이는 곳은
     *    `HttpTransport`(실제 `HttpURLConnection`)이고, 테스트는 가짜 Transport를
     *    쓰므로 **헤더를 지워도 위의 모든 테스트가 초록이다** — 가짜가 `contentRange`를
     *    직접 주기 때문이다.
     *
     *    그런데 헤더가 없으면 서버는 총수 자리에 별표를 주고, 그러면 [PlaceDiscoveries.recordCount]가
     *    **받은 행 수로 떨어지면서 `truncated`도 못 켠다**(총수를 모르니 비교할 게 없다).
     *    즉 **잘린 목록이 전부처럼 보인다.** 그래서 소스를 읽어서 고정한다
     *    ([JsonNullTest]가 소스를 읽는 것과 같은 이유).
     */
    @Test
    fun 총수를_요청하는_헤더가_소스에_있다() {
        val src = java.io.File("src/main/java/com/catchflower/app/data/PlaceDiscoveryService.kt")
        assertTrue("소스를 못 읽었다 — 이 테스트가 거짓 초록이다", src.isFile)
        val body = src.readLines()
            .filterNot { val l = it.trim(); l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") }
            .joinToString("\n")

        assertTrue(
            "Prefer: count=exact가 사라졌다 — Content-Range 총수가 별표가 되고 잘린 목록이 전부처럼 보인다",
            body.contains("\"Prefer\"") && body.contains("count=exact"),
        )
        assertTrue(
            "Content-Range를 안 읽는다 — 총수를 알 방법이 없어진다",
            body.contains("Content-Range"),
        )
    }

    private companion object {
        const val BASE = "https://ngfkkazyvbbhrcznqkar.supabase.co"
        const val ANON = "stub-anon-key"
    }
}
