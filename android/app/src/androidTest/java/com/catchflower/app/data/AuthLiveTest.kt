package com.catchflower.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 익명 로그인이 **실제로** 되는가.
 *
 * ⚠️ **유료 API가 아니다.** Supabase 인증은 무료 티어에 포함된다 (PlantNet·Google과
 *    다르다). 그래서 `PlantNetLiveTest`처럼 `/data/local/tmp` 파일 스위치로 잠그지 않는다.
 *    다만 **돌릴 때마다 익명 계정이 하나 생긴다** — Supabase는 30일 미접속 익명 계정을
 *    정리하므로 쌓여도 무해하지만, 반복 실행할 이유는 없다.
 *
 * ⚠️ **토큰·uuid 값을 로그에 찍지 않는다.** 길이와 형식만 본다.
 *
 * ⚠️ **앱의 실제 계정 저장을 건드리지 않는다.** [AuthService]에 테스트 전용 prefs 이름을
 *    넣는다 — 기본값(`catchflower`)을 쓰면 실기기에서 돌릴 때 **사용자 계정이 테스트
 *    계정으로 덮이고** 그 기기의 도감이 다른 계정 것이 된다. 되돌릴 방법이 없다.
 *
 * **왜 계측 테스트인가.** JVM에는 실물 `SharedPreferences`도 네트워크 스택도 없고,
 * `AuthMigrationTest`는 이관 규칙만 고정한다. **"로그인이 진짜 되는가"는 JVM이 답할 수
 * 없는 질문이다** — 그리고 로그인 실패는 앱이 기기 로컬 uuid로 조용히 계속 돌기 때문에
 * 화면에 증상이 없다. 서버 업로드를 붙이는 날 RLS에 전부 막혀서야 드러난다.
 */
@RunWith(AndroidJUnit4::class)
class AuthLiveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 실제 앱 prefs(`catchflower`)가 아니다. 테스트마다 새로 만든다. */
    private lateinit var testPrefs: String
    private lateinit var root: File

    @Before
    fun setUp() {
        val tag = UUID.randomUUID().toString()
        testPrefs = "authtest-$tag"
        root = File(context.filesDir, "authtest-$tag").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        context.getSharedPreferences(testPrefs, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun service(transport: AuthService.Transport = AuthService.HttpTransport) =
        AuthService(context = context, transport = transport, prefsName = testPrefs)

    /** 네트워크를 탔는지 세면서 실물로 넘긴다. 저장된 id를 돌려받은 경우와 구분하려면 필요하다. */
    private class CountingTransport : AuthService.Transport {
        var calls = 0
        var lastUrl: String? = null

        override suspend fun post(url: String, apiKey: String, body: String): Pair<Int, String> {
            calls++
            lastUrl = url
            return AuthService.HttpTransport.post(url, apiKey, body)
        }
    }

    private fun sample(userId: String, id: String = UUID.randomUUID().toString()) = Discovery(
        id = id,
        userId = userId,
        flowerId = 42,
        photoUrl = null,
        localPhotoPath = null,
        lat = 37.5445,
        lng = 127.0374,
        placeName = "서울숲",
        dongCode = "1120065000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = System.currentTimeMillis(),
        capturedAt = System.currentTimeMillis(),
    )

    /**
     * 계정이 실제로 만들어지고 **uuid를 돌려준다.**
     *
     * 빨개지는 경우: 익명 provider가 꺼졌거나(`anonymous_provider_disabled` 422),
     * 엔드포인트·본문 형식이 틀렸거나, 응답에서 `user.id`를 못 꺼내면.
     */
    @Test
    fun 익명_로그인이_uuid를_돌려준다() = runBlocking<Unit> {
        assumeTrue("Supabase 키가 없다", AppSecrets.hasSupabase)

        val transport = CountingTransport()
        val id = service(transport).userId()

        // ⚠️ **네트워크를 탔는지 먼저 본다.** 안 탔다면 `LocalUser`의 기기 로컬 uuid를
        //    돌려받은 것이고, 그 값도 uuid라서 **아래 형식 검사는 그냥 통과한다.**
        //    그러면 이 테스트는 아무것도 검증하지 않는다 — (22)에서 겪은 거짓 초록이다.
        assertEquals("실호출이 일어나지 않았다 — 로그인을 검증하지 못했다", 1, transport.calls)
        assertTrue(
            "엔드포인트가 /auth/v1/signup이 아니다: ${transport.lastUrl}",
            transport.lastUrl!!.endsWith("/auth/v1/signup"),
        )

        // 로그인 성공이면 **저장에 남는다.** 실패하면 `userId()`는 LocalUser 값을
        // 돌려주고 저장은 비어 있다 — 그 차이가 성공/실패의 유일한 관측점이다.
        val stored = context.getSharedPreferences(testPrefs, Context.MODE_PRIVATE)
            .getString("auth_user_id", null)
        assertEquals(
            "익명 로그인이 실패했다 (provider가 꺼졌거나 응답 형식이 다르다). " +
                "앱은 기기 로컬 id로 조용히 계속 돈다 — 그래서 이 검사가 필요하다",
            id,
            stored,
        )

        // 계약 1-3의 `user_id`는 uuid다.
        assertEquals("uuid 형식이 아니다 (길이 ${id.length})", 36, id.length)
        UUID.fromString(id) // 형식이 틀리면 던진다
        android.util.Log.i("CatchFlowerLive", "익명 로그인 성공 · uuid 길이 ${id.length}")
    }

    /**
     * 두 번째 실행은 **네트워크를 타지 않는다.**
     *
     * 빨개지는 경우: 저장된 id를 안 보고 매번 signup을 부르면. 그러면 **앱을 켤 때마다
     * 새 계정이 생기고** 이관이 매번 돌아 도감이 계정 사이를 떠돈다 — 화면에는
     * 정상으로 보이지만 서버에는 사용자 한 명이 수십 개 계정으로 쌓인다.
     */
    @Test
    fun 두_번째부터는_저장된_id를_쓴다() = runBlocking<Unit> {
        assumeTrue("Supabase 키가 없다", AppSecrets.hasSupabase)

        val first = service().userId()

        val guard = CountingTransport()
        val second = service(guard).userId()

        assertEquals("같은 기기에서 사용자 id가 바뀌었다", first, second)
        assertEquals("두 번째 실행에서 또 계정을 만들었다", 0, guard.calls)
    }

    /**
     * 로그인하면 **그동안의 기록이 새 id로 옮겨진다.**
     *
     * 빨개지는 경우:
     * - `DiscoveryRepository.load()`가 `signIn`을 안 부르면 → id가 그대로다
     * - 이관 후 메모리를 안 맞추면 → 파일만 보면 옮겨진 것처럼 보이고,
     *   **한 건 더 등록하는 순간 전부 옛 id로 되돌아간다**
     */
    @Test
    fun 로그인하면_기존_기록이_새_id로_옮겨진다() = runBlocking<Unit> {
        assumeTrue("Supabase 키가 없다", AppSecrets.hasSupabase)

        val store = DiscoveryStore(File(root, "discoveries.json"))
        val oldId = UUID.randomUUID().toString()
        val recordId = UUID.randomUUID().toString()
        store.append(sample(userId = oldId, id = recordId))

        val repo = DiscoveryRepository(
            store = store,
            photos = PhotoStore(File(root, "photos")),
            userId = oldId,
            auth = service(),
        )
        repo.load()

        assertNotEquals("로그인 id로 갈아타지 않았다", oldId, repo.userId)
        assertTrue("파일이 옛 id로 남았다", store.load().all { it.userId == repo.userId })
        assertTrue(
            "메모리가 파일과 어긋난다 — 다음 저장에서 옛 id를 되돌려 쓴다",
            repo.discoveries.value.all { it.userId == repo.userId },
        )
        // 이관은 **id만** 바꾼다. 기록이 사라지거나 다른 필드가 초기화되면 안 된다.
        assertEquals("이관 중에 기록 수가 변했다", 1, store.load().size)
        assertEquals("기록 자체가 바뀌었다", recordId, store.load().single().id)
        assertEquals("신뢰도가 사라졌다", 0.82f, store.load().single().aiConfidence!!, 0.0001f)

        // 메모리가 어긋났을 때의 **실제 증상**을 재현한다.
        //
        // ⚠️ `add`로는 안 잡힌다 — `store.append`가 파일을 다시 읽어서 쓰기 때문에
        //    메모리가 옛 id를 들고 있어도 파일은 멀쩡하다. 되돌려 쓰는 건
        //    **메모리를 그대로 저장하는 경로**(`update`·`delete`)다. 화면 13에서
        //    공개 범위를 한 번 바꾸면 도감 전체가 옛 id로 돌아간다.
        repo.update(repo.discoveries.value.single().copy(visibility = Visibility.PUBLIC))
        assertFalse(
            "공개 범위를 한 번 바꾸자 옛 id가 되살아났다",
            store.load().any { it.userId == oldId },
        )
        android.util.Log.i("CatchFlowerLive", "이관 확인: 1건 → 로그인 id, 등록 후에도 유지")
    }
}
