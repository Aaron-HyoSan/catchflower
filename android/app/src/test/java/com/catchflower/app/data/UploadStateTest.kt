package com.catchflower.app.data

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 어떤 기록이 올라갔는가를 기억하는 파일.
 *
 * **왜 테스트하는가.** 이 파일이 틀리면 증상이 **한 방향으로만** 나온다:
 * 기록이 조용히 안 올라간다. 도감은 로컬 파일로 그려지니 화면은 정상이고,
 * 드러나는 곳은 랭킹뿐이다. 기기에서 확인할 수 없다.
 */
class UploadStateTest {

    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var state: UploadState

    private val a = "aaaaaaaa-1111-4111-8111-111111111111"
    private val b = "bbbbbbbb-2222-4222-8222-222222222222"
    private val c = "cccccccc-3333-4333-8333-333333333333"

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "cf-upload-state-${System.nanoTime()}")
        dir.mkdirs()
        file = File(dir, "upload_state.json")
        state = newState()
    }

    /**
     * ⚠️ `log`를 갈아 끼운다 — 기본값은 `android.util.Log`라 JVM에서 던진다.
     *    깨진 파일 복구가 그 로그를 지나가므로, 안 끼우면 **가장 조용히 틀리는 경로를
     *    검증할 수 없다**(예외를 허용하는 것으로 끝난다).
     */
    private fun newState() = UploadState(file, log = { m, _ -> logs += m })

    private val logs = mutableListOf<String>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** 아무것도 안 올렸으면 전부 대기다. 파일이 아직 없는 첫 실행이다. */
    @Test
    fun 파일이_없으면_전부_미전송이다() = runBlocking {
        assertEquals(listOf(a, b), state.pending(listOf(a, b)))
    }

    @Test
    fun 올린_것은_대기에서_빠진다() = runBlocking {
        state.markUploaded(a)
        assertEquals(listOf(b), state.pending(listOf(a, b)))
    }

    /**
     * 거절된 것도 대기에서 빠진다.
     *
     * 빨개지는 경우: `pending`이 `rejected`를 안 걸러내면. 그러면 B-5 위반 기록을
     * **매 실행마다 다시 보내고 매번 409를 맞는다.**
     */
    @Test
    fun 거절된_것도_다시_보내지_않는다() = runBlocking {
        state.markRejected(a)
        assertEquals(listOf(b), state.pending(listOf(a, b)))
    }

    /** 재시작해도 기억한다 — 새 인스턴스가 같은 파일을 읽는다. */
    @Test
    fun 앱을_다시_켜도_기억한다() = runBlocking {
        state.markUploaded(a)
        state.markRejected(b)
        assertEquals(listOf(c), newState().pending(listOf(a, b, c)))
    }

    /**
     * 🔴 **`pending`은 인자로 받은 목록만 본다.**
     *
     * 빨개지는 경우: 구현이 파일에서 전체 목록을 다시 읽으면. 메모리(화면이 들고 있는
     * 진실)와 파일이 어긋난 순간 **다른 답이 나오고**, 방금 등록한 기록이
     * 대기 목록에서 빠질 수 있다.
     */
    @Test
    fun 주어진_목록에_없는_id는_돌려주지_않는다() = runBlocking {
        state.markUploaded(a)
        assertEquals("모르는 id를 만들어냈다", emptyList<String>(), state.pending(emptyList()))
        assertEquals(listOf(c), state.pending(listOf(c)))
    }

    /**
     * 🔴 **id가 바뀌면 "올라갔다"를 전부 취소한다.**
     *
     * 빨개지는 경우: [UploadState.clearUploaded]가 안 지우면. 이관 후 서버에 있는 행은
     * **옛 계정 것**이라 새 계정에는 한 건도 없는데 앱은 다 올렸다고 믿는다 —
     * 랭킹이 0종이고 도감은 정상으로 보인다.
     */
    @Test
    fun 계정이_바뀌면_다시_올린다() = runBlocking {
        state.markUploaded(a)
        state.markUploaded(b)
        state.clearUploaded()
        assertEquals(listOf(a, b), state.pending(listOf(a, b)))
    }

    /**
     * 🔴 **`clearUploaded`는 `rejected`를 지우지 않는다.**
     *
     * 빨개지는 경우: 둘 다 지우면. 거절 이유(B-5 위반 등)는 **계정과 무관**하므로
     * 매 이관마다 같은 400을 다시 맞는다.
     */
    @Test
    fun 계정이_바뀌어도_거절은_유지한다() = runBlocking {
        state.markUploaded(a)
        state.markRejected(b)
        state.clearUploaded()
        assertEquals("거절이 초기화됐다 — 같은 400을 또 맞는다", listOf(a), state.pending(listOf(a, b)))
    }

    /**
     * 🔴 **파일이 깨졌으면 "전부 미전송"으로 시작한다. 던지지 않는다.**
     *
     * 빨개지는 경우: 깨진 파일에 "전부 올라갔다"고 가정하거나 예외를 올리면.
     * 앞쪽은 **기록이 영구히 안 올라가고**, 뒤쪽은 앱 시작이 깨진다.
     * 반대 방향의 최악은 이미 올라간 것을 다시 올리는 것이고, upsert라 그건 무해하다.
     */
    @Test
    fun 깨진_파일은_전부_미전송으로_본다() = runBlocking {
        file.writeText("{ 이건 JSON이 아니다")
        assertEquals("깨진 파일에서 '올라갔다'를 만들어냈다", listOf(a, b), state.pending(listOf(a, b)))
        assertTrue("조용히 넘어갔다 — 복구했다는 흔적이 없다", logs.isNotEmpty())

        // 깨진 파일을 덮어쓸 수 있어야 한다. 못 쓰면 매 실행마다 전부 다시 올린다.
        state.markUploaded(a)
        assertEquals(listOf(b), newState().pending(listOf(a, b)))
    }

    /** 빈 파일도 같은 취급이다 — 원자적 쓰기가 실패한 흔적일 수 있다. */
    @Test
    fun 빈_파일은_전부_미전송으로_본다() = runBlocking {
        file.writeText("")
        assertEquals(listOf(a, b), state.pending(listOf(a, b)))
    }

    /**
     * JSON이지만 형태가 다른 파일도 죽지 않는다.
     *
     * 빨개지는 경우: `JSONObject`를 기대하는 코드에 배열이 오면 `JSONException`이 아니라
     * 다른 예외가 날 수 있다 — 그러면 위 복구 경로를 안 타고 앱 시작이 깨진다.
     */
    @Test
    fun 형태가_다른_JSON도_미전송으로_본다() = runBlocking {
        file.writeText("""["$a"]""")
        assertEquals(listOf(a, b), state.pending(listOf(a, b)))
    }

    /** 같은 id를 두 번 표시해도 결과가 같다 — 재시도 경로에서 실제로 일어난다. */
    @Test
    fun 두_번_표시해도_같다() = runBlocking {
        state.markUploaded(a)
        state.markUploaded(a)
        assertEquals(setOf(a), state.uploaded())
    }

    /**
     * 반쪽 파일을 남기지 않는다.
     *
     * 빨개지는 경우: 원자적 쓰기(tmp → rename)를 안 하면. 쓰는 중에 앱이 죽으면
     * 다음 실행이 깨진 파일을 읽고 **전부 다시 올린다**(무해하지만 낭비다).
     */
    @Test
    fun 임시_파일을_남기지_않는다() = runBlocking {
        state.markUploaded(a)
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue("임시 파일이 남았다: $leftovers", leftovers.isEmpty())
    }
}
