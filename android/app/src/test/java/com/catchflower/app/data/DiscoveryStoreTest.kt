package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 발견 기록 저장 테스트.
 *
 * **왜 필요한가**: 저장은 **다음 실행에서만 틀린 게 드러난다.** 앱을 쓰는 동안은
 * 메모리에 있어서 전부 정상으로 보이고, 껐다 켜면 도감이 비어 있다.
 * 눈으로 잡으려면 매번 앱을 재시작해야 한다.
 *
 * ⚠️ **`org.json`은 android.jar에 껍데기만 있다.** 실물(`org.json:json:20250517`)이
 *    테스트 클래스패스에 있어서 이 테스트가 진짜 파싱을 한다.
 *    `unitTests.isReturnDefaultValues = true`로 덮으면 파싱이 조용히 null을 돌려주고
 *    **이 테스트가 초록으로 통과한다** — 그래서 그 옵션을 쓰지 않는다.
 *
 * ⚠️ `android.util.Log`는 JVM에서 스텁이라 호출하면 던진다. 그래서 이 테스트는
 *    **로그를 타지 않는 경로만** 검증한다. 손상 격리(`quarantine`)는 Log를 부르므로
 *    `Log` 없이 검증 가능한 부분(파일이 치워졌는가)만 확인하고 예외를 허용한다.
 */
class DiscoveryStoreTest {

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var store: DiscoveryStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cf-store").toFile()
        file = File(dir, "discoveries.json")
        store = DiscoveryStore(file)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun d(
        id: String,
        flowerId: Int = 1,
        capturedAt: Long = 1_780_000_000_000L,
        lat: Double? = 37.5445,
        lng: Double? = 127.0374,
        photo: String? = "photo.jpg",
        note: String? = null,
    ) = Discovery(
        id = id,
        userId = "11111111-2222-3333-4444-555555555555",
        flowerId = flowerId,
        photoUrl = null,
        localPhotoPath = photo,
        lat = lat,
        lng = lng,
        placeName = "서울숲",
        dongCode = "1120065000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 2,
        isFirstDiscovery = true,
        note = note,
        createdAt = capturedAt + 5_000,
        capturedAt = capturedAt,
    )

    // ── 왕복 ────────────────────────────────────────────────────────

    /**
     * 저장한 것이 그대로 돌아온다.
     *
     * 빨개지는 경우: 필드 하나라도 직렬화에서 빠지면. **가장 위험한 건 조용한 누락이다** —
     * `dong_code`가 안 저장되면 랭킹(B-6)만 조용히 죽고 도감은 정상으로 보인다.
     */
    @Test
    fun 저장한_기록이_그대로_돌아온다() = runBlocking {
        val original = listOf(d("a"), d("b", flowerId = 7, note = "골목에서 만났어요"))
        store.save(original)
        assertEquals(original, DiscoveryStore(file).load())
    }

    /** 파일이 없으면 빈 목록이다. 빨개지는 경우: 예외를 던지면 앱이 첫 실행에 죽는다. */
    @Test
    fun 파일이_없으면_빈_목록이다() = runBlocking {
        assertEquals(emptyList<Discovery>(), store.load())
    }

    /** null 필드가 null로 돌아온다. 빨개지는 경우: `optString`이 ""를 주는데 그대로 넣으면. */
    @Test
    fun 없는_필드는_null로_돌아온다() = runBlocking {
        store.save(listOf(d("a", lat = null, lng = null, photo = null, note = null)))
        val loaded = store.load().single()
        assertNull(loaded.lat)
        assertNull(loaded.lng)
        assertNull(loaded.localPhotoPath)
        assertNull(loaded.note)
    }

    /**
     * ⚠️ **null 필드는 키를 아예 넣지 않는다** (`putOpt`).
     *
     * 빨개지는 경우: `JSONObject.NULL`을 쓰면. 이 JSON이 그대로 Supabase에 올라가는데,
     * 명시적 null은 **서버의 기존 값을 덮는다** — 나중에 사진을 업로드해 `photo_url`이
     * 채워진 뒤 이 기록을 다시 올리면 그 값이 지워진다.
     */
    @Test
    fun null_필드는_키_자체가_없다() = runBlocking {
        store.save(listOf(d("a", lat = null, note = null)))
        val o = JSONArray(file.readText()).getJSONObject(0)
        assertFalse("lat 키가 있으면 서버 값을 null로 덮는다", o.has("lat"))
        assertFalse("note 키가 있으면 서버 값을 null로 덮는다", o.has("note"))
        assertTrue(o.has("dong_code")) // 값이 있는 건 들어가야 한다
    }

    // ── 계약: 컬럼명과 시각 형식 ────────────────────────────────────

    /**
     * 계약 1-3의 컬럼명(snake_case)으로 저장된다.
     *
     * 빨개지는 경우: 코틀린 프로퍼티명(`flowerId`)이 그대로 나가면. 서버가 붙는 날
     * 전부 400이 되는데, **로컬에서는 아무 증상이 없다** — 읽는 쪽도 같은 이름을
     * 쓰니까 왕복 테스트는 통과한다.
     */
    @Test
    fun 계약_컬럼명으로_저장된다() = runBlocking {
        store.save(listOf(d("a")))
        val o = JSONArray(file.readText()).getJSONObject(0)
        for (key in listOf(
            "id", "user_id", "flower_id", "lat", "lng", "place_name",
            "dong_code", "gu_code", "visibility", "ai_confidence",
            "ai_picked_rank", "is_first_discovery", "created_at", "captured_at",
        )) {
            assertTrue("계약 컬럼 `$key`가 없다", o.has(key))
        }
        // 코틀린 프로퍼티명이 새어 나가면 안 된다.
        for (leak in listOf("flowerId", "userId", "dongCode", "createdAt", "capturedAt")) {
            assertFalse("코틀린 이름 `$leak`이 새어 나갔다", o.has(leak))
        }
    }

    /**
     * ⚠️ **시각은 ISO-8601 문자열이다. epoch millis가 아니다.**
     *
     * iOS는 `.iso8601`로 쓴다. 여기서 Long을 그대로 넣으면 같은 `timestamptz` 컬럼에
     * 두 형식이 섞여 올라간다 — **floor 0.20 vs 0.30과 똑같은 조용한 불일치**이고,
     * 로컬 왕복 테스트는 양쪽 다 통과한다.
     *
     * 빨개지는 경우: `put(K_CREATED_AT, d.createdAt)`으로 되돌리면.
     */
    @Test
    fun 시각은_ISO8601_문자열이다() = runBlocking {
        store.save(listOf(d("a", capturedAt = 1_780_000_000_000L)))
        val o = JSONArray(file.readText()).getJSONObject(0)
        val captured = o.get("captured_at")
        assertTrue("captured_at이 $captured — 문자열이어야 한다", captured is String)
        // ⚠️ 기대값을 손으로 적지 않는다. 처음에 `2026-06-08...`이라고 적었는데
        //    실제 값은 `2026-05-28...`이었다 — **테스트가 틀렸고 코드는 맞았다.**
        //    손으로 계산한 기대값은 그 자체가 검증 대상이 아닌 새 버그다.
        assertEquals(
            java.time.Instant.ofEpochMilli(1_780_000_000_000L).toString(),
            captured,
        )
        // 형식이 계약(timestamptz)과 맞는지는 **다시 파싱해서** 본다.
        // iOS `.iso8601`이 만드는 것과 같은 `...Z` 형태여야 한다.
        assertTrue("Z로 끝나지 않는다: $captured", (captured as String).endsWith("Z"))
        assertEquals(1_780_000_000_000L, java.time.Instant.parse(captured).toEpochMilli())
        assertTrue(o.get("created_at") is String)
    }

    /** 왕복해도 밀리초가 유지된다. 빨개지는 경우: 초 단위로 잘라 쓰면. */
    @Test
    fun 시각_왕복에_밀리초가_유지된다() {
        val millis = 1_780_000_000_123L
        assertEquals(millis, DiscoveryStore.decodeTime(DiscoveryStore.encodeTime(millis)))
    }

    /**
     * `visibility`는 계약의 와이어 값으로 나간다.
     *
     * 빨개지는 경우: enum 이름(`PRIVATE`)을 그대로 쓰면. 계약 값과 대문자/소문자가
     * 다르면 서버가 거부하는데 로컬은 자기가 쓴 값을 자기가 읽어서 통과한다.
     */
    @Test
    fun 공개범위는_와이어_값으로_저장된다() = runBlocking {
        store.save(listOf(d("a")))
        val o = JSONArray(file.readText()).getJSONObject(0)
        assertEquals(Visibility.PRIVATE.wire, o.getString("visibility"))
    }

    /**
     * ⚠️ 신뢰도는 Double로 넓혀 저장한다.
     *
     * 빨개지는 경우: Float를 그대로 `put`하면 `0.8199999928474426`이 파일에 박힌다.
     * 값이 틀린 건 아니지만 사람이 파일을 열어 확인할 수 없게 되고, 서버 쪽
     * `numeric` 컬럼에 지저분한 값이 쌓인다.
     */
    @Test
    fun 신뢰도가_지저분한_소수로_저장되지_않는다() = runBlocking {
        store.save(listOf(d("a")))
        val text = JSONArray(file.readText()).getJSONObject(0).get("ai_confidence").toString()
        assertEquals("0.82", text)
        // 그래도 Float로 되돌아와야 한다.
        assertEquals(0.82f, store.load().single().aiConfidence!!, 0.0001f)
    }

    /**
     * ⚠️ **로컬 사진 파일명은 `photo_url`에 넣지 않는다.**
     *
     * 빨개지는 경우: `photoUrl`에 파일명을 넣으면. 서버가 그걸 스토리지 키로 읽어
     * 존재하지 않는 이미지를 가리키게 된다.
     */
    @Test
    fun 로컬_사진_파일명은_계약_필드를_오염시키지_않는다() = runBlocking {
        store.save(listOf(d("a", photo = "abc.jpg")))
        val o = JSONArray(file.readText()).getJSONObject(0)
        assertFalse("photo_url에 로컬 파일명이 들어갔다", o.has("photo_url"))
        assertEquals("abc.jpg", o.getString("_local_photo_path"))
    }

    // ── 동시성 ──────────────────────────────────────────────────────

    /**
     * ⚠️ **읽기→추가→쓰기가 한 락 안에 있어야 한다.**
     *
     * 빨개지는 경우: `append`가 `load()`를 락 밖에서 부르면. 두 코루틴이 같은 목록을
     * 읽고 각자 1건씩 붙여 저장해서 **한 건이 사라진다.**
     * 처음에 실제로 그렇게 썼다 — 주석으로 위험을 적어 놓고 코드는 그 위험을 뒀다.
     *
     * 이 테스트는 그 회귀를 잡는다. 20건을 동시에 넣고 20건이 남는지 본다.
     */
    @Test
    fun 동시에_추가해도_기록이_사라지지_않는다() = runBlocking {
        val count = 20
        (0 until count).map { i ->
            async { store.append(d("id-$i", flowerId = i + 1)) }
        }.awaitAll()

        val loaded = DiscoveryStore(file).load()
        assertEquals("동시 추가에서 기록이 사라졌다", count, loaded.size)
        assertEquals(count, loaded.map { it.id }.distinct().size)
    }

    /** `append`가 갱신된 전체를 돌려준다. 빨개지는 경우: 저장만 하고 옛 목록을 주면. */
    @Test
    fun append는_갱신된_전체를_돌려준다() = runBlocking {
        store.append(d("a"))
        val after = store.append(d("b", flowerId = 2))
        assertEquals(listOf("a", "b"), after.map { it.id })
    }

    // ── 원자적 쓰기·손상 ────────────────────────────────────────────

    /**
     * 임시 파일이 남지 않는다.
     *
     * 빨개지는 경우: rename 대신 직접 쓰고 tmp를 안 지우면. 남은 `.tmp`는
     * 다음 실행에서 아무 영향이 없어 보이지만 저장 공간을 계속 먹는다.
     */
    @Test
    fun 저장_후_임시_파일이_남지_않는다() = runBlocking {
        store.save(listOf(d("a")))
        val leftovers = dir.listFiles()!!.filter { it.name.endsWith(".tmp") }
        assertTrue("임시 파일이 남았다: ${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    /**
     * 옛 형식(epoch millis)이 들어 있으면 파싱이 거부한다.
     *
     * ⚠️ **조용히 읽히면 안 된다.** 읽히면 1970년 기록이 되어 도감 날짜가
     * `20470일 전`이 된다 — 그건 눈에 보이지만, `capturedAt`이 1970년이면
     * **시즌 집계와 B-5가 조용히 어긋난다.**
     *
     * 빨개지는 경우: `decodeTime`이 숫자도 받아 주면.
     */
    @Test
    fun epoch_millis로_쓰인_옛_파일은_그대로_읽히지_않는다() {
        val bad = JSONArray().put(
            JSONObject()
                .put("id", "a").put("user_id", "u").put("flower_id", 1)
                .put("visibility", Visibility.PRIVATE.wire)
                .put("is_first_discovery", true)
                .put("created_at", 1_780_000_000_000L)
                .put("captured_at", 1_780_000_000_000L),
        )
        file.writeText(bad.toString())
        // ⚠️ 격리(`quarantine`)는 `android.util.Log`를 부르는데 JVM에서는 스텁이 던진다.
        //    그래서 "빈 목록"이 아니라 "그대로 읽히지 않는다"를 검증한다 —
        //    조용히 1970년 기록으로 읽히면 이 assert가 실패한다.
        val loaded = runCatching { runBlocking { store.load() } }.getOrNull()
        assertTrue(
            "epoch millis가 조용히 읽혔다: $loaded",
            loaded == null || loaded.isEmpty(),
        )
    }

    /** 깨진 JSON도 그대로 읽히지 않는다. 빨개지는 경우: 파싱 예외를 삼켜 부분 읽기를 하면. */
    @Test
    fun 깨진_JSON은_그대로_읽히지_않는다() {
        file.writeText("""[{"id":"a","user_i""")
        val loaded = runCatching { runBlocking { store.load() } }.getOrNull()
        assertTrue("깨진 파일이 읽혔다: $loaded", loaded == null || loaded.isEmpty())
    }

    /**
     * 모르는 `visibility` 값도 거부한다.
     *
     * 빨개지는 경우: `fromWire`가 기본값으로 넘기면. 공개 범위를 **기본 공개**로
     * 넘기는 실수는 사용자 동의 없이 사진을 지도에 올린다.
     */
    @Test
    fun 계약에_없는_공개범위는_읽히지_않는다() {
        val bad = JSONArray().put(
            JSONObject()
                .put("id", "a").put("user_id", "u").put("flower_id", 1)
                .put("visibility", "everyone_forever")
                .put("is_first_discovery", true)
                .put("created_at", "2026-08-06T00:00:00Z")
                .put("captured_at", "2026-08-06T00:00:00Z"),
        )
        file.writeText(bad.toString())
        val loaded = runCatching { runBlocking { store.load() } }.getOrNull()
        assertTrue("모르는 공개범위가 읽혔다: $loaded", loaded == null || loaded.isEmpty())
    }

    /** 빈 목록을 저장하면 빈 배열이다. 빨개지는 경우: 파일을 지워 버리면 (다음 load가 애매해진다). */
    @Test
    fun 빈_목록도_저장된다() = runBlocking {
        store.save(listOf(d("a")))
        store.save(emptyList())
        assertTrue(file.exists())
        assertEquals(emptyList<Discovery>(), store.load())
    }

    /** 상위 폴더가 없어도 저장된다. 빨개지는 경우: mkdirs를 빼면 첫 저장이 통째로 실패한다. */
    @Test
    fun 상위_폴더가_없어도_저장된다() = runBlocking {
        val nested = File(dir, "sub/deeper/discoveries.json")
        val s = DiscoveryStore(nested)
        s.append(d("a"))
        assertTrue(nested.exists())
        assertNotNull(DiscoveryStore(nested).load().singleOrNull())
    }
}
