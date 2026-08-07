package com.catchflower.app.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **저장이 실제 기기에서 도는가.**
 *
 * JVM 테스트(`DiscoveryStoreTest`)는 임시 폴더에 쓴다. 그건 저장 규칙을 고정하지만
 * **앱이 진짜로 다음 실행에서 도감을 되찾는지는 증명하지 않는다.** 여기서 확인하는 것:
 *
 * 1. `filesDir`에 실제로 파일이 생기는가 (권한·경로 문제는 기기에서만 난다)
 * 2. **저장소 객체를 새로 만들면 읽히는가** — 앱 재시작과 같은 상황이다
 * 3. 사진 바이트가 살아 있는가 (`cacheDir`가 아니라서 안 지워진다)
 * 4. `org.json`이 **실물**인가 — 계약 컬럼명이 기기에서도 그대로 나가는가
 *
 * ⚠️ 네트워크·유료 API를 쓰지 않는다. 카카오도 PlantNet도 부르지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 앱 진짜 파일을 건드리지 않는다 — 실기기에서 돌리면 사용자 도감이 날아간다. */
    private lateinit var root: File

    private companion object {
        const val TAG = "CatchFlowerLive"
    }

    @Before
    fun setUp() {
        root = File(context.filesDir, "test-${UUID.randomUUID()}")
        root.mkdirs()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun discovery(flowerId: Int, photo: String?, capturedAt: Long) = Discovery(
        id = UUID.randomUUID().toString(),
        userId = UUID.randomUUID().toString(),
        flowerId = flowerId,
        photoUrl = null,
        localPhotoPath = photo,
        lat = 37.5445,
        lng = 127.0374,
        placeName = "서울숲",
        dongCode = "1120065000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = capturedAt + 3_000,
        capturedAt = capturedAt,
    )

    /**
     * **앱을 껐다 켠 것과 같은 상황.** 저장소 객체를 버리고 새로 만들어 읽는다.
     *
     * ⚠️ 이 테스트가 없으면 "저장했다"의 근거가 메모리 상태뿐이다 —
     *    화면에는 등록된 꽃이 그대로 보여서 저장이 되는 줄 안다.
     */
    @Test
    fun 앱을_다시_켠_것처럼_읽어도_기록이_남아있다() = runBlocking<Unit> {
        val file = File(root, "discoveries.json")
        val photos = PhotoStore(File(root, "photos"))

        // ① 사진을 저장한다. 실제 JPEG 헤더로 시작하는 바이트를 쓴다.
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(512) { 7 }
        val photoName = photos.save(jpeg)
        assertNotNull("기기에서 사진 저장이 실패했다", photoName)
        Log.i(TAG, "사진 저장: $photoName (${jpeg.size}바이트)")

        // ② 기록을 저장한다.
        val first = DiscoveryStore(file)
        val record = discovery(flowerId = 42, photo = photoName, capturedAt = System.currentTimeMillis())
        first.append(record)

        assertTrue("filesDir에 파일이 생기지 않았다: ${file.absolutePath}", file.exists())
        Log.i(TAG, "기록 파일: ${file.absolutePath} (${file.length()}바이트)")

        // ③ **새 저장소 객체** — 앱 재시작과 같다.
        val reopened = DiscoveryStore(file).load()
        assertEquals("재시작 후 기록이 사라졌다", 1, reopened.size)
        assertEquals(record, reopened.single())

        // ④ 사진 바이트도 살아 있어야 한다. 파일명만 저장하고 경로는 다시 만든다.
        val name = reopened.single().localPhotoPath!!
        assertTrue("사진 파일이 없다: $name", photos.exists(name))
        assertEquals(jpeg.size, photos.bytes(name)!!.size)
        Log.i(TAG, "재시작 후 복원: 꽃 ${reopened.single().flowerId}번 · 사진 ${name}")
    }

    /**
     * 계약 컬럼명이 **기기에서도** 그대로 나가는가.
     *
     * ⚠️ JVM 테스트는 `org.json:json` 실물을 쓰고 기기는 android.jar 구현을 쓴다.
     *    **다른 구현체다.** 직렬화 결과가 같다는 보장이 없어서 양쪽에서 확인한다.
     */
    @Test
    fun 기기에서도_계약_컬럼명으로_저장된다() = runBlocking<Unit> {
        val file = File(root, "contract.json")
        DiscoveryStore(file).append(discovery(1, "p.jpg", 1_780_000_000_000L))
        val o = org.json.JSONArray(file.readText()).getJSONObject(0)

        for (key in listOf(
            "id", "user_id", "flower_id", "lat", "lng", "place_name",
            "dong_code", "gu_code", "visibility", "ai_confidence",
            "ai_picked_rank", "is_first_discovery", "created_at", "captured_at",
        )) {
            assertTrue("계약 컬럼 `$key`가 없다", o.has(key))
        }
        // 시각은 ISO-8601 문자열이어야 한다 (iOS와 같은 형식).
        val captured = o.get("captured_at")
        assertTrue("captured_at이 $captured — 문자열이어야 한다", captured is String)
        assertEquals(
            1_780_000_000_000L,
            java.time.Instant.parse(captured as String).toEpochMilli(),
        )
        // 로컬 파일명이 계약 필드를 오염시키지 않는다.
        assertFalse("photo_url에 로컬 파일명이 들어갔다", o.has("photo_url"))
        Log.i(TAG, "기기 직렬화: $o")
    }

    /**
     * 사진은 `filesDir` 아래다 — **`cacheDir`가 아니다.**
     *
     * ⚠️ `cacheDir`에 두면 안드로이드가 저장공간이 부족할 때 임의로 비워서
     *    **도감 사진이 소리 없이 사라진다.** 코드로만 보면 구분이 안 되는 실수라
     *    실제 경로를 확인한다.
     */
    @Test
    fun 사진은_캐시가_아닌_영구_저장소에_있다() = runBlocking<Unit> {
        val photos = PhotoStore.default(context)
        val name = photos.save(ByteArray(64) { 3 })
        assertNotNull(name)
        val path = photos.file(name!!).absolutePath
        try {
            assertTrue(
                "사진이 filesDir 밖에 있다: $path",
                path.startsWith(context.filesDir.absolutePath),
            )
            assertFalse(
                "사진이 cacheDir에 있다 — 안드로이드가 임의로 지운다: $path",
                path.startsWith(context.cacheDir.absolutePath),
            )
            Log.i(TAG, "사진 경로: $path")
        } finally {
            photos.delete(name)
        }
    }

    /**
     * `pruneExcept`가 참조 없는 사진만 지운다.
     *
     * ⚠️ **빈 집합이면 아무것도 지우지 않아야 한다.** 기록을 아직 못 읽은 상태에서
     *    부르면 전부 날아가기 때문이다. 그건 복구가 불가능한 사고다.
     */
    @Test
    fun 참조없는_사진만_지운다() = runBlocking<Unit> {
        val photos = PhotoStore(File(root, "prune"))
        val keep = photos.save(ByteArray(32) { 1 })!!
        val orphan = photos.save(ByteArray(32) { 2 })!!

        // 빈 집합 — 아무것도 지우면 안 된다.
        assertEquals(0, photos.pruneExcept(emptySet()))
        assertTrue(photos.exists(keep))
        assertTrue(photos.exists(orphan))

        assertEquals(1, photos.pruneExcept(setOf(keep)))
        assertTrue("참조된 사진이 지워졌다", photos.exists(keep))
        assertFalse("참조 없는 사진이 남았다", photos.exists(orphan))
    }

    /**
     * 손상된 파일은 격리되고 앱은 살아 있는다.
     *
     * ⚠️ 조용히 빈 목록만 돌려주면 **사용자에게는 도감이 사라진 것으로 보인다.**
     *    원본을 `.corrupt`로 남겨야 최소한 복구를 시도할 수 있다.
     *    (JVM 테스트에서는 `android.util.Log`가 스텁이라 이 경로를 못 탄다 —
     *     기기에서만 확인 가능하다.)
     */
    @Test
    fun 손상된_파일은_격리되고_앱은_살아있는다() = runBlocking<Unit> {
        val file = File(root, "broken.json")
        file.writeText("""[{"id":"a","user_i""")
        val loaded = DiscoveryStore(file).load()
        assertTrue("깨진 파일이 읽혔다: $loaded", loaded.isEmpty())
        assertTrue("원본이 격리되지 않았다", File(root, "broken.json.corrupt").exists())
        assertFalse("원본이 그대로 남아 다음 실행에도 같은 오류가 난다", file.exists())
        Log.i(TAG, "손상 파일 격리 확인: broken.json.corrupt")
    }

    /**
     * [DiscoveryRepository]가 두 화면에 **같은 데이터**를 준다.
     *
     * ⚠️ 이게 깨지면 촬영으로 등록한 꽃이 도감에 안 나타난다 — 앱을 껐다 켜야 보인다.
     *    "저장은 되는데 화면이 안 바뀐다"로 보여서 저장 버그로 읽히지도 않는다.
     */
    @Test
    fun 저장소는_촬영과_도감에_같은_데이터를_준다() = runBlocking<Unit> {
        val repo = DiscoveryRepository(
            store = DiscoveryStore(File(root, "shared.json")),
            photos = PhotoStore(File(root, "shared-photos")),
            userId = UUID.randomUUID().toString(),
        )
        repo.load()
        assertTrue(repo.loaded.value)
        assertEquals(0, repo.discoveries.value.size)

        // 촬영 쪽이 등록한다.
        val added = repo.add(discovery(99, null, System.currentTimeMillis()))
        // 도감 쪽이 읽는다 — 같은 인스턴스의 StateFlow다.
        assertEquals(1, repo.discoveries.value.size)
        assertEquals(added, repo.discoveries.value)
        assertEquals(setOf(99), DiscoveryRules.collectedIds(repo.discoveries.value))

        // 지우면 양쪽에서 사라진다.
        repo.delete(added.single().id)
        assertEquals(0, repo.discoveries.value.size)
        Log.i(TAG, "저장소 공유 확인: 등록 → 도감 반영 → 삭제 반영")
    }
}
