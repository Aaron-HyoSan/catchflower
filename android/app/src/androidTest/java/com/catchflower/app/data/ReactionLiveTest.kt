package com.catchflower.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.ui.place.RecordRules
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 화면 16의 **쓰기 3개**(댓글·좋아요·신고)가 실서버에서 되는가.
 *
 * ## 🔴 왜 이제서야 만들 수 있나
 *
 * [ReactionSource] KDoc에 이렇게 적어 두었다:
 *
 * > `postComment`·`deleteComment`·`report`의 **성공 응답 형태는 아직 못 쟀다**
 * > (남의 기록에 실제로 쓰지 않았다) — 상상한 형식을 실측처럼 적어 두면 (22)처럼
 * > **테스트만 통과하고 앱은 안 된다.**
 *
 * 막고 있던 것은 **자기 댓글 삭제가 403 42501로 거부되던 것**이었다(0008 머리말의
 * 실측 4줄). 0008이 그것을 고쳤고 오너가 적용해 **진단 8행이 전부 기대와 일치**했다
 * (2026-08-12). 즉 **이 파일은 0008 적용을 전제로만 의미가 있다** — 안 붙은 서버에
 * 대고 돌리면 `댓글을_달고_읽고_지운다`가 삭제 단계에서 빨개진다(그게 맞는 동작이다).
 *
 * ## ⚠️ 왜 계측 테스트인가 — JVM으로는 이걸 못 잰다
 *
 * [ReactionServiceTest](JVM 25개)는 **픽스처**를 읽는다. 픽스처는 (22)에서 배운 대로
 * **함께 틀리고 함께 초록이다.** [ReactionContractTest]는 마이그레이션 **원문**을 읽지만
 * 그건 파일이지 서버가 아니다. 🔴 **0007·0008이 서버에 붙어 있는지 자체를 파일로는
 * 알 수 없다** — `PGRST202`가 "아직 배포 안 됨"과 "이름을 틀림"에 **똑같이** 온다.
 *
 * ## ⚠️ 유료 API가 아니다 (오너 규칙 확인)
 *
 * Supabase는 무료 티어다(PlantNet·Google과 다르다). 그래서 `PlantNetLiveTest`처럼
 * `/data/local/tmp/cf_run_network_tests` 스위치로 잠그지 않는다 — **오너의 "실측 보고 전
 * 유료 호출 금지"는 과금되는 호출에 대한 것**이고 이 파일은 한 푼도 쓰지 않는다.
 *
 * 🔴 **다만 돌릴 때마다 익명 계정이 하나 생긴다**([AuthLiveTest]와 같다). **서버에
 * 익명 계정 110개가 쌓인 이유가 정확히 이것이다**(오너 실측 2026-08-12). 그래서
 * 이 파일은 **클래스 전체가 계정 하나**를 쓴다 — 테스트 3개가 각자 만들면 3배로 쌓인다.
 *
 * ## 🔴 이 테스트가 **재는 것과 못 재는 것**
 *
 * 잰다: 성공 응답의 코드·모양 · 등록한 댓글이 **다시 읽히는가**(쓰기와 읽기의 RLS는
 * 따로다) · 집계가 **따라 움직이는가** · 삭제가 **집계에서 빠지는가**(0008의 핵심) ·
 * `created_at` ISO 문자열이 **화면이 쓰는 형식인가**.
 *
 * 🔴 **못 잰다: 남의 기록에 쓰는 것.** 방금 만든 익명 계정은 **남의 공개 기록 id를
 *    모른다** — 그걸 주는 서버 함수가 아직 없다(그래서 화면 15가 경계 상자로 긁어 온다).
 *    그래서 **자기 기록에 자기가** 쓴다. ⚠️ 이건 실사용 경로가 아니다:
 *    `can_see_discovery`의 **남의 공개 기록** 분기와 `is_discovery_owner`(사진 주인이
 *    남의 댓글을 지우는 경로)는 **여전히 미측정**이다. 이 사실을 `구현현황_AOS.md`에
 *    남긴다 — 여기서만 알고 있으면 다음 세션이 "쓰기 실측 완료"로 읽는다.
 */
@RunWith(AndroidJUnit4::class)
class ReactionLiveTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testPrefs: String

    /** 이 클래스가 만든 **유일한** 계정. 세 테스트가 나눠 쓴다. */
    private lateinit var auth: AuthService

    @Before
    fun setUp() {
        // ⚠️ 앱 계정(`catchflower`)을 쓰면 **실기기 사용자의 도감이 테스트 계정 것이 된다.**
        //    되돌릴 방법이 없다([AuthService.prefsName] 주석).
        testPrefs = "reactionlive-${UUID.randomUUID()}"
        auth = AuthService(context = context, prefsName = testPrefs)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(testPrefs, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /**
     * 키 없는 빌드에서는 **건너뛴다.**
     *
     * ⚠️ `assumeTrue`는 실패가 아니라 **건너뜀**으로 보고된다. 실패로 만들면 키 없는
     *    CI가 빨개지고, 무조건 통과로 만들면 **한 줄도 안 태우고 초록**이 된다.
     */
    private fun requireServer() {
        assumeTrue("Supabase 키가 없는 빌드다 — 건너뛴다", AppSecrets.hasSupabase)
    }

    private fun service() = ReactionService(auth, myUserId = { auth.userId() })

    /**
     * 🔴 **댓글 등록 → 다시 읽기 → 집계 → 삭제 → 집계에서 빠짐.**
     *
     * 한 테스트에 묶는 이유: 각 단계가 **앞 단계의 산출물**(댓글 id)을 쓴다. 나누면
     * 각각 기록을 새로 만들어야 하고 서버에 쓰레기가 3배로 남는다.
     *
     * ⚠️ **`204`를 성공으로 읽지 않는다.** 0008 진단에서 배운 것이 정확히 그것이다 —
     *    **남의 댓글 삭제도 `204 + 0행`**이었다(머리말 3행). 그래서 코드가 아니라
     *    **집계가 실제로 줄어드는지**로 판정한다.
     */
    @Test
    fun 댓글을_달고_읽고_지운다() {
        requireServer()
        runBlocking {
            val svc = service()
            val id = seedOwnDiscovery() ?: return@runBlocking
            val base = loaded(svc.reactions(id), "반응 조회")

            val body = "실측 댓글 ${UUID.randomUUID()}"
            // 🔴 **한 번만 부른다.** 처음에 `assertTrue("…${'$'}{svc.postComment(…)}", svc.postComment(…) is …)`
            //    로 썼더니 **메시지 인자가 먼저 평가되어 댓글이 두 개 달렸다** —
            //    `expected:<1> but was:<2>`로 실기기에서 걸렸다. 실패 메시지에 호출을
            //    넣는 습관이 **부수효과가 있는 함수에서는 호출을 두 배로 만든다.**
            //    ⚠️ JVM 픽스처로는 절대 못 보는 종류다(픽스처는 몇 번 불려도 같은 값을 준다).
            val posted = svc.postComment(id, body)
            assertTrue("댓글 등록이 실패했다: $posted", posted is ReactionResult.Loaded)

            // 🔴 등록 성공이 **읽힌다는 뜻이 아니다** — 쓰기와 읽기의 RLS가 따로다.
            val rows = loaded(svc.comments(id), "댓글 목록").rows
            val mine = rows.firstOrNull { it.body == body }
            assertNotNull(
                "등록은 성공인데 목록에 없다 — insert 정책은 통과하고 select 정책이 막았다",
                mine,
            )

            // ⚠️ `createdAt`은 ISO 문자열이다(Long이 아니다). **화면이 파싱하므로**
            //    서버가 주는 실제 형식이 그 파서를 통과하는지를 여기서 잰다 —
            //    JVM 픽스처로는 내가 상상한 형식만 확인된다.
            assertNotNull(
                "서버의 created_at을 상대시각으로 못 바꿨다 — 화면 16에서 시각 칸이 " +
                    "조용히 사라진다. 받은 값의 길이=${mine!!.createdAt.length}",
                RecordRules.commentTime(mine.createdAt, System.currentTimeMillis()),
            )

            assertEquals(
                "댓글 개수가 안 올라갔다 — 집계가 댓글을 안 센다",
                base.commentCount + 1,
                loaded(svc.reactions(id), "등록 뒤 반응").commentCount,
            )

            // 🔴 **여기는 0009를 오너가 적용할 때까지 빨갛다. 그게 맞는 상태다.**
            //
            // 원인을 찾았다(2026-08-12 · 로컬 Postgres 16.2 실측). 0008이 아니다 —
            // 0008은 서버에 정상 적용됐고 진단 8행이 전부 ✅였다. 진짜 원인은
            // **`update`의 새 행에도 `select` 정책이 적용된다**는 Postgres 규칙이다.
            // `comments_read`가 `deleted_at is null`을 요구하므로(0007 3절) soft
            // delete는 자기 행을 그 정책에서 사라지게 만들고, 그것이 42501이 된다.
            // → 고침 제안은 `프로젝트 맥락/제안/0009_제안_댓글삭제_RPC.sql`
            //   (`delete_comment(uuid)` RPC). **오너가 적용해야 초록이 된다.**
            //
            // ⚠️ 처음 이 메시지에 **"0008이 서버에 적용되지 않았을 수 있다"**고 썼다.
            //    그게 틀렸고, 틀린 진단을 실패 메시지에 박아 두면 다음 사람이
            //    **이미 적용된 것을 다시 적용하며 원인을 찾는다.**
            val deleted = svc.deleteComment(mine.id)   // ⚠️ 한 번만 (위 주석 참고)
            assertTrue(
                // 🔴 **이유는 응답 본문에만 있다.** `Failed(403, 42501)`만으로는
                //    어느 정책이 막았는지 구분되지 않는다 — 그래서 실패할 때
                //    **같은 PATCH를 날것으로 한 번 더 보내 본문을 읽는다.**
                //    ⚠️ 계측 테스트는 끝나면 **앱을 지워서 logcat이 밀린다**(adb 함정 4가지)
                //       → 로그가 아니라 **단정 메시지**에 담아야 남는다.
                "자기 댓글 삭제가 실패했다: $deleted\n" +
                    "0009(delete_comment RPC)가 아직 서버에 없으면 이게 정상이다 — " +
                    "원인은 `update`의 새 행에 `select` 정책이 걸리는 것이고 " +
                    "0008과 무관하다.\n서버가 말한 이유 = ${rawPatchReason(mine.id)}",
                deleted is ReactionResult.Loaded,
            )

            // 🔴 `204`를 믿지 않고 **집계로** 확인한다.
            assertEquals(
                "삭제가 성공을 줬는데 집계가 안 줄었다 — 0행 삭제였다(남의 댓글 삭제와 " +
                    "**같은 응답 모양**이다 · 0008 진단 3행)",
                base.commentCount,
                loaded(svc.reactions(id), "삭제 뒤 반응").commentCount,
            )

            assertNull(
                "지운 댓글이 목록에 그대로 있다 — deleted_at을 select 필터에 안 넣었다",
                loaded(svc.comments(id), "삭제 뒤 목록").rows.firstOrNull { it.body == body },
            )
        }
    }

    /**
     * 좋아요 → 집계 → **두 번 눌러도 1** → 취소 → 집계.
     *
     * ⚠️ **`likedByMe`를 같이 잰다.** 개수만 보면 "내가 눌렀다"와 "남이 눌렀다"가
     *    구분되지 않는다 — 화면은 그 값으로 버튼을 채운다.
     */
    @Test
    fun 좋아요를_누르고_취소한다() {
        requireServer()
        runBlocking {
            val svc = service()
            val id = seedOwnDiscovery() ?: return@runBlocking

            // ⚠️ 한 번만 부른다 — `댓글을_달고_읽고_지운다`의 주석 참고. 좋아요는
            //    멱등이라 두 번 불려도 개수가 안 틀리는데, **그래서 더 위험하다**
            //    (같은 실수가 여기서는 증상이 없다).
            val liked = svc.like(id)
            assertTrue("좋아요가 실패했다: $liked", liked is ReactionResult.Loaded)
            val on = loaded(svc.reactions(id), "좋아요 뒤 반응")
            assertEquals("좋아요 개수가 1이 아니다", 1, on.likeCount)
            assertTrue("내가 눌렀는데 likedByMe가 false다", on.likedByMe)

            // 🔴 같은 계정이 두 번 눌러도 **개수는 1이어야** 한다 — 0007이 `likes`에
            //    `id`를 두지 않은 이유다(있으면 한 사람이 두 번 좋아요가 된다).
            assertTrue(svc.like(id) is ReactionResult.Loaded)
            assertEquals(
                "같은 사람이 두 번 눌러 개수가 2가 됐다 — `likes`의 기본키가 갈렸다",
                1,
                loaded(svc.reactions(id), "두 번 뒤 반응").likeCount,
            )

            assertTrue(svc.unlike(id) is ReactionResult.Loaded)
            val off = loaded(svc.reactions(id), "취소 뒤 반응")
            assertEquals("취소했는데 개수가 안 줄었다", 0, off.likeCount)
            assertFalse("취소했는데 likedByMe가 true다", off.likedByMe)
        }
    }

    /**
     * 신고. **성공 응답 모양만** 잰다.
     *
     * ⚠️ 신고는 **결과를 화면에서 확인할 방법이 없다**(`RecordDetailScreen` 주석 —
     *    접수됐는지 안 됐는지 화면이 안 바뀐다). 그래서 **여기가 유일한 확인 지점이다.**
     */
    @Test
    fun 신고가_접수된다() {
        requireServer()
        runBlocking {
            val svc = service()
            val id = seedOwnDiscovery() ?: return@runBlocking

            val first = svc.report(id, "실측 신고")   // ⚠️ 한 번만 (위 주석 참고)
            assertTrue("신고가 실패했다: $first", first is ReactionResult.Loaded)
            // 같은 기록을 두 번 신고해도 성공으로 본다([ReactionService.report] 주석) —
            // 중복 제약을 실패로 올리면 사용자는 이미 접수된 신고를 실패로 본다.
            val second = svc.report(id, "실측 신고 2")
            assertTrue(
                "두 번째 신고가 실패했다 — 중복 제약을 성공으로 접지 않았다: $second",
                second is ReactionResult.Loaded,
            )
        }
    }

    /**
     * 🔴 **서버가 거절한 이유를 글자로 받아 온다.** 실패했을 때만 부른다.
     *
     * `ReactionResult.Failed(403, 42501)`은 **어느 정책이 막았는지 말하지 않는다** —
     * `카카오맵 401은 두 원인`과 같은 모양이다: 같은 코드에 원인이 여러 개고
     * **이유는 응답 본문에만 있다.** 0008 진단이 정책 원문을 보여줬으므로
     * 여기서 필요한 것은 **서버가 이 PATCH에 대해 하는 말**이다.
     *
     * ⚠️ 이 호출은 **진단 전용**이다. 성공 경로에서는 부르지 않는다(부수효과가 있다).
     */
    private fun rawPatchReason(commentId: String): String {
        val token = runBlocking { auth.accessToken() } ?: return "토큰 없음"
        val conn = (URL("${AppSecrets.supabaseUrl}/rest/v1/comments?id=eq.$commentId")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("apikey", AppSecrets.supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return runCatching {
            conn.outputStream.use {
                it.write("""{"deleted_at":"${java.time.Instant.now()}"}""".toByteArray())
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            "HTTP $code · $text"
        }.getOrElse { "읽기 실패: ${it.javaClass.simpleName}" }.also { conn.disconnect() }
    }

    /** [ReactionResult.Loaded]만 통과시키고 값을 꺼낸다. 실패 이유를 메시지에 남긴다. */
    private fun <T> loaded(result: ReactionResult<T>, what: String): T {
        assertTrue("$what 이(가) 실패했다: $result", result is ReactionResult.Loaded)
        return (result as ReactionResult.Loaded).value
    }

    /**
     * 이 계정 소유의 발견 기록 하나를 서버에 만들고 **id를 돌려준다.**
     *
     * 🔴 **실패하면 `assumeTrue`로 건너뛴다** — 여기서 빨개지면 재는 대상이 반응이 아니라
     *    **업로드**가 된다. 원인을 섞지 않는다(업로드는 `DiscoveryUploaderTest`가 잰다).
     *
     * ⚠️ **`ai_confidence`를 빼먹으면 안 된다.** 그것 때문에 죽은 스크립트가 있고,
     *    그때도 **계정은 이미 만든 뒤**에 죽어서 익명 계정만 남았다(03 SQL 머리말).
     */
    private suspend fun seedOwnDiscovery(): String? {
        val token = auth.accessToken()
        val userId = auth.userId()
        if (token.isNullOrEmpty()) {
            assumeTrue("익명 로그인이 안 됐다 — 이 아래는 전부 의미가 없다", false)
            return null
        }
        val payload = buildString {
            append("""{"user_id":"$userId","flower_id":1,""")
            append(""""lat":37.5665,"lng":126.9780,"visibility":"public",""")
            append(""""ai_confidence":0.9,"captured_at":"${java.time.Instant.now()}"}""")
        }
        val conn = (URL("${AppSecrets.supabaseUrl}/rest/v1/discoveries")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("apikey", AppSecrets.supabaseAnonKey)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=representation")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val id = runCatching {
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            // ⚠️ **값이 아니라 모양만** 남긴다 — uuid·토큰을 로그에 찍지 않는다.
            android.util.Log.i("ReactionLive", "기록 생성: code=$code len=${text.length}")
            if (code !in 200..299) null else JSONArray(text).optJSONObject(0)?.stringOrNull("id")
        }.getOrNull()
        conn.disconnect()
        assumeTrue(
            "발견 기록을 만들지 못했다 — 반응이 아니라 업로드가 막힌 것이므로 건너뛴다",
            id != null,
        )
        return id
    }
}
