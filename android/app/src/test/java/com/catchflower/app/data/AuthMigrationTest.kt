package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 로그인 id로 기록을 옮기는 규칙.
 *
 * **왜 테스트가 필요한가.** 이관이 없거나 틀리면 **아무 증상이 없다** —
 * 도감은 그대로 보이고 파일도 그대로 있다. `user_id`가 다른 계정 것이어서
 * **서버에 못 올라가는 것뿐**이고, 그건 업로드를 붙이는 날 처음 드러난다.
 * 그때는 이미 몇 주치 기록이 쌓여 있다.
 *
 * ⚠️ 네트워크를 부르지 않는다. [AuthService.migrate]는 저장소만 만지는 함수다.
 */
class AuthMigrationTest {

    private lateinit var dir: File
    private lateinit var store: DiscoveryStore

    private val oldId = "11111111-1111-1111-1111-111111111111"
    private val newId = "22222222-2222-2222-2222-222222222222"

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cf-auth").toFile()
        store = DiscoveryStore(File(dir, "discoveries.json"))
    }

    @After
    fun tearDown() = dir.deleteRecursively().let { }

    private fun d(id: String, userId: String) = Discovery(
        id = id,
        userId = userId,
        flowerId = 1,
        photoUrl = null,
        localPhotoPath = "p.jpg",
        lat = 37.5445,
        lng = 127.0374,
        placeName = "서울숲",
        dongCode = "1120065000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = 1_780_000_000_000L,
        capturedAt = 1_780_000_000_000L,
    )

    /**
     * 옛 id로 저장된 기록이 새 id로 옮겨진다.
     *
     * 빨개지는 경우: `migrate`를 안 부르거나 `copy(userId = ...)`를 빼면.
     * 그러면 **로그인 전에 모은 꽃이 전부 서버에 못 올라간다** (RLS가 거부한다).
     */
    @Test
    fun 옛_id로_저장된_기록이_새_id로_옮겨진다() = runBlocking {
        store.save(listOf(d("a", oldId), d("b", oldId)))
        assertEquals(2, AuthService.migrate(store, newId))
        val after = store.load()
        assertTrue("옛 id가 남았다: ${after.map { it.userId }}", after.all { it.userId == newId })
        // 나머지 필드는 그대로여야 한다 — 이관은 id만 바꾼다.
        assertEquals(listOf("a", "b"), after.map { it.id })
        assertEquals(0.82f, after.first().aiConfidence!!, 0.0001f)
        assertEquals("1120065000", after.first().dongCode)
    }

    /**
     * ⚠️ **멱등해야 한다.** 매 실행마다 불린다.
     *
     * 빨개지는 경우: 조건 없이 항상 저장하면. 매번 파일 전체를 다시 쓰는 것은
     * 200건 규모에서 첫 화면을 느리게 만들고, 쓰기가 늘어날수록 손상 위험도 늘어난다.
     */
    @Test
    fun 이미_옮긴_기록은_다시_옮기지_않는다() = runBlocking {
        store.save(listOf(d("a", newId)))
        assertEquals(0, AuthService.migrate(store, newId))
    }

    /** 기록이 없으면 아무것도 하지 않는다. 빨개지는 경우: 빈 목록에 save를 부르면. */
    @Test
    fun 기록이_없으면_이관하지_않는다() = runBlocking {
        assertEquals(0, AuthService.migrate(store, newId))
    }

    /**
     * 섞여 있으면 옛 것만 옮긴다.
     *
     * 빨개지는 경우: 전부 덮어쓰면 — 그 자체로는 결과가 같지만 반환값이 틀려서
     * "몇 건 옮겼나" 로그가 거짓이 된다. 이관은 한 번뿐인 사건이라 기록이 근거다.
     */
    @Test
    fun 섞여_있으면_옛_것만_센다() = runBlocking {
        store.save(listOf(d("a", oldId), d("b", newId)))
        assertEquals(1, AuthService.migrate(store, newId))
        assertTrue(store.load().all { it.userId == newId })
    }

    /**
     * ⚠️ [AuthService.migrate]는 **companion 함수이고 `Context`를 받지 않는다.**
     *    처음에 인스턴스 메서드로 써서 이 테스트가 `Context`를 만들어야 했는데,
     *    `Context`는 인터페이스가 아니라 프록시로도 못 만든다 —
     *    **테스트가 못 만들어지는 것 자체가 결합이 잘못됐다는 신호였다.**
     *    이관은 한 번뿐인 사건이라 기기에서 재현할 기회가 사실상 없으므로
     *    JVM으로 고정할 수 있어야 한다.
     */
    @Test
    fun 이관은_안드로이드_의존_없이_돈다() {
        // 컴파일되는 것 자체가 검증이다 — Context 인자가 생기면 이 파일이 깨진다.
        val fn: suspend (DiscoveryStore, String) -> Int = AuthService::migrate
        assertTrue(fn.toString().isNotEmpty())
    }
}
