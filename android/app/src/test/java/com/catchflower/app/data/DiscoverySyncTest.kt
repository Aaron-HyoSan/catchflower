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
 * 못 올린 기록을 언제 다시 보내는가 ([DiscoveryRepository.syncPending]).
 *
 * **왜 테스트하는가.** 이 함수가 틀리는 방향이 두 개인데 **둘 다 화면에 안 보인다**:
 * 1. 너무 안 보낸다 → 기록이 영구히 서버에 없다(랭킹에서만 조용히 빠진다).
 * 2. 너무 보낸다 → 비행기 모드에서 수백 번 왕복한다(배터리·데이터).
 *
 * ⚠️ 네트워크를 부르지 않는다. [UploadSink]와 [AuthAccount]를 갈아 끼운다.
 */
class DiscoverySyncTest {

    private lateinit var dir: File
    private lateinit var store: DiscoveryStore
    private lateinit var state: UploadState

    private val localId = "11111111-1111-4111-8111-111111111111"
    private val serverId = "22222222-2222-4222-8222-222222222222"

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cf-sync").toFile()
        store = DiscoveryStore(File(dir, "discoveries.json"))
        state = UploadState(File(dir, "upload_state.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun d(id: String, userId: String = localId) = Discovery(
        id = id,
        userId = userId,
        flowerId = 1,
        photoUrl = null,
        localPhotoPath = "$id.jpg",
        lat = 37.5445,
        lng = 127.0557,
        placeName = "서울숲",
        dongCode = "1120052000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = 1_786_000_000_000L,
        capturedAt = 1_786_000_000_000L,
    )

    /** id별 결과를 미리 정해 두고 **누가 몇 번 불렸는지** 센다. */
    private class FakeSink(
        private val results: Map<String, DiscoveryUploader.Result> = emptyMap(),
        private val default: DiscoveryUploader.Result = DiscoveryUploader.Result.Uploaded,
    ) : UploadSink {
        val calls = mutableListOf<String>()

        override suspend fun upload(discovery: Discovery): DiscoveryUploader.Result {
            calls += discovery.id
            return results[discovery.id] ?: default
        }
    }

    private class FakeAccount(
        private val id: String,
        private val reauth: Boolean = false,
    ) : AuthAccount {
        var resetCalls = 0
        override suspend fun userId(): String = id
        override suspend fun accessToken(): String? = "token"
        override suspend fun refresh(): String? = "token"
        override fun needsReauth(): Boolean = reauth
        override fun reset() { resetCalls++ }
    }

    private fun repo(
        sink: UploadSink?,
        auth: AuthAccount? = null,
        userId: String = localId,
    ) = DiscoveryRepository(
        store = store,
        photos = PhotoStore(File(dir, "photos")),
        userId = userId,
        auth = auth,
        uploader = sink,
        uploadState = state,
        // ⚠️ 기본값은 `android.util.Log`라 JVM에서 던진다 — 보류 경로가 로그를 지난다.
        log = { },
    )

    private fun id(n: Int) = "0000000$n-0000-4000-8000-00000000000$n"

    // ────────────────────────────────────────────────────────────────

    /** 등록하면 바로 올린다 — 다음 실행까지 기다리지 않는다. */
    @Test
    fun 등록하면_바로_올린다() = runBlocking {
        val sink = FakeSink()
        repo(sink).add(d(id(1)))
        assertEquals(listOf(id(1)), sink.calls)
        assertEquals(setOf(id(1)), state.uploaded())
    }

    /**
     * 🔴 **업로드가 실패해도 등록은 살아 있다.**
     *
     * 빨개지는 경우: `push`가 예외를 올리거나 저장을 되돌리면. 그러면 **비행기 모드에서
     * 찍은 꽃이 도감에 안 들어간다** — 사용자에게는 앱이 고장 난 것으로 보인다.
     */
    @Test
    fun 업로드가_실패해도_기록은_남는다() = runBlocking {
        val sink = FakeSink(default = DiscoveryUploader.Result.Failed(0))
        val r = repo(sink)
        r.add(d(id(1)))

        assertEquals("기록이 사라졌다", 1, store.load().size)
        assertEquals("실패인데 올라갔다고 표시했다", emptySet<String>(), state.uploaded())
        // 다음 실행에서 다시 대상이 되어야 한다.
        assertEquals(listOf(id(1)), state.pending(listOf(id(1))))
    }

    /** 이미 올린 것은 다시 보내지 않는다. */
    @Test
    fun 이미_올린_것은_다시_보내지_않는다() = runBlocking {
        store.save(listOf(d(id(1)), d(id(2))))
        state.markUploaded(id(1))

        val sink = FakeSink()
        val r = repo(sink)
        r.load()

        assertEquals("올라간 기록을 또 보냈다", listOf(id(2)), sink.calls)
    }

    /** 거절된 것도 다시 보내지 않는다 — 매 실행 400을 맞을 이유가 없다. */
    @Test
    fun 거절된_것은_다시_보내지_않는다() = runBlocking {
        store.save(listOf(d(id(1))))
        state.markRejected(id(1))

        val sink = FakeSink()
        repo(sink).load()
        assertEquals("거절된 기록을 또 보냈다", emptyList<String>(), sink.calls)
    }

    /**
     * 🔴 **일시 실패를 만나면 그 자리에서 멈춘다.**
     *
     * 빨개지는 경우: `Failed`에 `continue`를 쓰면. 네트워크가 끊긴 상태에서 밀린 기록이
     * 200건이면 **200번 왕복을 시도한다.** 한 건이 네트워크로 실패했으면 나머지도 실패한다.
     */
    @Test
    fun 일시_실패를_만나면_멈춘다() = runBlocking {
        store.save(listOf(d(id(1)), d(id(2)), d(id(3))))
        val sink = FakeSink(
            results = mapOf(id(2) to DiscoveryUploader.Result.Failed(0)),
        )
        val sent = repo(sink).load().let { state }

        assertEquals("2번에서 멈추지 않고 계속 보냈다", listOf(id(1), id(2)), sink.calls)
        assertEquals(setOf(id(1)), sent.uploaded())
        // 3번은 손대지 않았다 — 다음 실행에 온다.
        assertEquals(listOf(id(2), id(3)), state.pending(listOf(id(1), id(2), id(3))))
    }

    /**
     * 규칙 거절은 **건너뛰고 계속한다.**
     *
     * 빨개지는 경우: `Rejected`에서도 멈추면. B-5 위반 **한 건이 그날 나머지 기록 전부를
     * 막는다** — 배열 삽입을 포기한 이유가 바로 그거였다.
     */
    @Test
    fun 규칙_거절은_건너뛰고_계속한다() = runBlocking {
        store.save(listOf(d(id(1)), d(id(2)), d(id(3))))
        val sink = FakeSink(
            results = mapOf(id(2) to DiscoveryUploader.Result.Rejected(409, "23505")),
        )
        repo(sink).load()

        assertEquals("거절 한 건이 나머지를 막았다", listOf(id(1), id(2), id(3)), sink.calls)
        assertEquals(setOf(id(1), id(3)), state.uploaded())
        assertEquals("거절이 대기로 남았다", emptyList<String>(), state.pending(listOf(id(1), id(2), id(3))))
    }

    /**
     * 🔴 **로그인보다 업로드가 먼저 오면 안 된다.**
     *
     * 빨개지는 경우: [DiscoveryRepository.load]가 `syncPending`을 `signIn` 앞에 두면.
     * 그러면 기기 로컬 id로 보내서 서버가 **42501로 거부하고**, 그 기록들이 실패로 쌓인다.
     * 여기서는 **보낸 payload의 `user_id`가 로그인 id인지**로 순서를 잰다 —
     * 함수 호출 순서를 세는 것보다 이게 실제 증상에 가깝다.
     */
    @Test
    fun 로그인_뒤에_올린다() = runBlocking {
        store.save(listOf(d(id(1), userId = localId)))
        var seenUserIds = listOf<String>()
        val sink = object : UploadSink {
            override suspend fun upload(discovery: Discovery): DiscoveryUploader.Result {
                seenUserIds = seenUserIds + discovery.userId
                return DiscoveryUploader.Result.Uploaded
            }
        }
        repo(sink, auth = FakeAccount(serverId)).load()

        assertEquals(
            "기기 로컬 id로 올렸다 — 서버는 42501로 거부한다",
            listOf(serverId),
            seenUserIds,
        )
    }

    /**
     * 🔴 **계정이 바뀌면 이미 올린 것도 다시 올린다.**
     *
     * 빨개지는 경우: 이관 후 [UploadState.clearUploaded]를 안 부르면.
     * 서버에 있는 행은 **옛 계정 것**이라 새 계정에는 한 건도 없는데 앱은 다 올렸다고
     * 믿는다 — 랭킹이 0종이고 도감은 정상으로 보인다.
     */
    @Test
    fun 계정이_바뀌면_다시_올린다() = runBlocking {
        store.save(listOf(d(id(1), userId = localId)))
        state.markUploaded(id(1))

        val sink = FakeSink()
        repo(sink, auth = FakeAccount(serverId)).load()

        assertEquals("이관했는데 다시 올리지 않았다", listOf(id(1)), sink.calls)
        assertEquals(serverId, store.load().single().userId)
    }

    /** 이관할 게 없으면 이미 올린 것을 다시 올리지 않는다(clearUploaded 남용 방지). */
    @Test
    fun 이관이_없으면_다시_올리지_않는다() = runBlocking {
        store.save(listOf(d(id(1), userId = serverId)))
        state.markUploaded(id(1))

        val sink = FakeSink()
        repo(sink, auth = FakeAccount(serverId), userId = serverId).load()
        assertEquals("이관도 없는데 전부 다시 올렸다", emptyList<String>(), sink.calls)
    }

    /**
     * 🔴 **갱신 수단 없는 계정은 버리고 새로 받는다 — 단, 아직 아무것도 안 올렸을 때만.**
     *
     * 빨개지는 경우: 조건 없이 [AuthAccount.reset]을 부르면. 이미 서버에 올라간 기록이
     * **주인 없는 데이터가 된다**(새 계정으로는 RLS 때문에 보이지도 지우지도 못한다).
     */
    @Test
    fun 올린_게_있으면_계정을_버리지_않는다() = runBlocking {
        store.save(listOf(d(id(1))))
        state.markUploaded(id(1))

        val auth = FakeAccount(serverId, reauth = true)
        repo(FakeSink(), auth = auth).load()
        assertEquals("올린 게 있는데 계정을 버렸다 — 그 데이터는 주인을 잃는다", 0, auth.resetCalls)
    }

    @Test
    fun 아무것도_안_올렸으면_계정을_버린다() = runBlocking {
        store.save(listOf(d(id(1))))
        val auth = FakeAccount(serverId, reauth = true)
        repo(FakeSink(), auth = auth).load()
        assertEquals("영구 401 계정을 그대로 뒀다", 1, auth.resetCalls)
    }

    /**
     * 업로더가 없으면(오프라인 빌드·테스트) 아무 일도 없다.
     *
     * 빨개지는 경우: null 검사를 빼면 도감 화면이 켜지자마자 죽는다.
     */
    @Test
    fun 업로더가_없으면_아무것도_하지_않는다() = runBlocking {
        store.save(listOf(d(id(1))))
        val r = repo(sink = null)
        r.load()
        assertEquals(0, r.syncPending())
        assertEquals(1, r.discoveries.value.size)
    }

    /**
     * 대기가 없으면 네트워크를 아예 만지지 않는다.
     *
     * 빨개지는 경우: 빈 목록에도 요청을 보내면. 앱을 켤 때마다 불필요한 왕복이 생긴다.
     */
    @Test
    fun 대기가_없으면_부르지_않는다() = runBlocking {
        val sink = FakeSink()
        val r = repo(sink)
        r.load()
        assertTrue("대기 없는데 요청을 보냈다: ${sink.calls}", sink.calls.isEmpty())
    }
}
