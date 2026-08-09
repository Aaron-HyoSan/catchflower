package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 화면 13이 고친 공개 범위·한 줄이 **서버까지 가는가** ([DiscoveryRepository.update]).
 *
 * 🔴 **이 경로는 화면·파일·서버 셋 중 둘만 맞아도 아무 증상이 없다.**
 * 앱은 고친 값을 보여주고(메모리), 껐다 켜도 유지되고(파일), **서버 행만 옛 값이다.**
 * 지도는 내 기록을 로컬에서 그리므로 내 화면에는 핀이 보이고,
 * 다른 사람에게만 안 보인다 — 예선 심사 중에 알 방법이 없는 결함이다.
 */
class ShareUpdateTest {

    private lateinit var dir: File
    private lateinit var store: DiscoveryStore
    private lateinit var state: UploadState

    private val userId = "11111111-1111-4111-8111-111111111111"
    private val recordId = "33333333-3333-4333-8333-333333333333"

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cf-share").toFile()
        store = DiscoveryStore(File(dir, "discoveries.json"))
        state = UploadState(File(dir, "upload_state.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** 화면 13 진입 시점의 기록 — [CaptureViewModel]이 비공개로 저장해 둔 상태다. */
    private fun saved() = Discovery(
        id = recordId,
        userId = userId,
        flowerId = 1,
        photoUrl = null,
        localPhotoPath = "$recordId.jpg",
        lat = 37.5445,
        lng = 127.0557,
        placeName = "서울숲",
        dongCode = "1120052000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        note = null,
        createdAt = 1_786_000_000_000L,
        capturedAt = 1_786_000_000_000L,
    )

    private class RecordingSink : UploadSink {
        val sent = mutableListOf<Discovery>()
        override suspend fun upload(discovery: Discovery): DiscoveryUploader.Result {
            sent += discovery
            return DiscoveryUploader.Result.Uploaded
        }
    }

    private fun repo(sink: UploadSink?) = DiscoveryRepository(
        store = store,
        photos = PhotoStore(File(dir, "photos")),
        userId = userId,
        auth = null,
        uploader = sink,
        uploadState = state,
        // ⚠️ 기본값은 `android.util.Log`라 JVM에서 던진다.
        log = { },
    )

    // ────────────────────────────────────────────────────────────────

    /**
     * 🔴 **이미 올라간 기록을 고치면 다시 올린다.**
     *
     * 빨개지는 경우: [DiscoveryRepository.update]가 `push`를 안 부르면.
     * [UploadState.pending]은 **아직 안 올린 id만** 주므로, 이미 `uploaded`인 기록은
     * 다음 실행의 [DiscoveryRepository.syncPending]에도 **영원히 안 걸린다** —
     * 서버 행은 계속 `private`·`note = null`이고 앱은 `모두에게 공개`를 보여준다.
     */
    @Test
    fun 공유_설정을_고치면_서버에_다시_올린다() = runBlocking {
        val sink = RecordingSink()
        val r = repo(sink)
        r.add(saved())
        assertEquals("등록이 안 올라갔다", 1, sink.sent.size)
        assertTrue("등록이 uploaded로 안 남았다", recordId in state.uploaded())

        r.update(saved().copy(visibility = Visibility.PUBLIC, note = "숲길 끝 벤치 옆"))

        assertEquals("고친 값을 서버에 안 보냈다", 2, sink.sent.size)
        val last = sink.sent.last()
        assertEquals(Visibility.PUBLIC, last.visibility)
        assertEquals("숲길 끝 벤치 옆", last.note)
    }

    /**
     * 🔴 **한 건 고치는데 전 기록을 다시 올리지 않는다.**
     *
     * 빨개지는 경우: [UploadState.clearUploaded]로 대기 상태를 비우면.
     * 기록 200건인 사용자가 꽃 하나를 공유할 때마다 **200번 왕복**한다 —
     * 화면은 정상이고 데이터·배터리만 조용히 사라진다.
     */
    @Test
    fun 한_건만_다시_올린다() = runBlocking {
        val other = saved().copy(id = "44444444-4444-4444-8444-444444444444")
        val sink = RecordingSink()
        val r = repo(sink)
        r.add(other)
        r.add(saved())
        sink.sent.clear()

        r.update(saved().copy(visibility = Visibility.PUBLIC))

        assertEquals(listOf(recordId), sink.sent.map { it.id })
        assertEquals(
            "다른 기록이 대기로 돌아갔다 — 다음 실행에 전부 다시 올라간다",
            setOf(other.id, recordId),
            state.uploaded(),
        )
    }

    /**
     * 고친 값이 **파일에도** 남는다.
     *
     * 빨개지는 경우: 메모리만 갱신하면. 앱을 껐다 켜면 공개로 바꾼 꽃이
     * 도감에서 다시 `비공개` 배지를 달고, 서버에는 공개로 올라가 있다.
     */
    @Test
    fun 고친_값이_파일에_남는다() = runBlocking {
        val r = repo(null)
        r.add(saved())
        r.update(saved().copy(visibility = Visibility.PUBLIC, note = "한 줄"))

        val reloaded = store.load().single()
        assertEquals(Visibility.PUBLIC, reloaded.visibility)
        assertEquals("한 줄", reloaded.note)
        assertEquals("기록이 늘거나 줄었다", 1, store.load().size)
    }

    /** 고친 값이 메모리 흐름에도 반영된다 — 지도([MapPins])가 이걸 구독한다. */
    @Test
    fun 고친_값이_메모리에_반영된다() = runBlocking {
        val r = repo(null)
        r.add(saved())
        r.update(saved().copy(visibility = Visibility.PUBLIC))
        assertEquals(Visibility.PUBLIC, r.discoveries.value.single().visibility)
    }

    /**
     * 업로더가 없으면(오프라인 빌드) 조용히 저장만 한다.
     *
     * 빨개지는 경우: null 검사를 빼면 공유하기가 화면을 죽인다.
     */
    @Test
    fun 업로더가_없어도_저장은_된다() = runBlocking {
        val r = repo(null)
        r.add(saved())
        r.update(saved().copy(visibility = Visibility.PUBLIC))
        assertEquals(Visibility.PUBLIC, store.load().single().visibility)
    }

    /**
     * 🔴 **한 줄을 안 썼을 때 `""`를 넣으면 서버 행에 빈 문자열이 박힌다.**
     *
     * 이게 `ShareRules.toStored`가 null을 돌려주는 이유다. 여기서 재는 것은
     * **전송 JSON**이다 — 로컬 왕복으로는 안 잡힌다(`fromJson`의 `stringOrNull`이
     * 빈 문자열도 null로 읽어서 **저장했다 읽으면 똑같이 null이 나온다**).
     *
     * 빨개지는 경우: `toJson`이 `putOpt` 대신 `put`을 쓰거나, 화면이 `""`를 넘기면.
     */
    @Test
    fun 빈_한줄은_전송_JSON에서_키가_빠져야_한다() {
        val none = DiscoveryStore.toWireJson(saved().copy(note = null))
        assertFalse("한 줄을 안 썼는데 note 키가 나갔다", none.has("note"))

        val written = DiscoveryStore.toWireJson(saved().copy(note = "숲길 끝"))
        assertEquals("숲길 끝", written.getString("note"))

        // 🔴 `""`가 그대로 나가는 것을 **기록해 둔다** — 화면이 `""`를 넘기면
        //    "안 쓴 것"과 "빈 줄을 쓴 것"이 서버에서 구분되지 않는다.
        val empty = DiscoveryStore.toWireJson(saved().copy(note = ""))
        assertTrue(
            "빈 문자열이 키에서 빠지고 있다 — 그러면 이 방어가 필요 없다는 뜻이니 " +
                "ShareRules.toStored의 주석을 고쳐야 한다",
            empty.has("note"),
        )
        assertEquals("", empty.getString("note"))
    }

    /** 공개 범위는 계약 문자열로 나간다 (`public`/`friends`/`private`). */
    @Test
    fun 공개_범위는_계약_문자열로_나간다() {
        assertEquals(
            "public",
            DiscoveryStore.toWireJson(saved().copy(visibility = Visibility.PUBLIC))
                .getString("visibility"),
        )
        assertEquals(
            "friends",
            DiscoveryStore.toWireJson(saved().copy(visibility = Visibility.FRIENDS))
                .getString("visibility"),
        )
    }
}
