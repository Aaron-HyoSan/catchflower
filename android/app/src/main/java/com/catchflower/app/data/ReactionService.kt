package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.GamePolicy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 화면 16(꽃 기록 상세)의 좋아요·댓글·신고. 마이그레이션 `0007_likes_comments.sql`이 서버 쪽이다.
 *
 * 🔵 **2026-08-12 정정 — 위에 내가 적어 둔 "막힌 이유 세 가지"가 전부 틀렸다.**
 *
 * 원래 이 자리에 이렇게 적어 두었다: ① `0007`이 아직 적용되지 않았다 ② 남의 발견
 * 기록을 주는 서버 함수가 없다 ③ 그래서 화면 16 입구가 없다. **실측으로 다시 재니
 * ①②가 사실이 아니었다**(익명 계정 2개로 실제 서버를 태웠다):
 *
 * ```
 * GET /rest/v1/likes    → 200 · 행이 있다        ← 0007은 적용돼 있었다
 * GET /rest/v1/comments → 200 · 행이 있다
 * POST /rpc/discovery_reactions → 200 {"like_count":0,...}
 *   (대조군) POST /rpc/nonexistent_fn_xyz → 404 PGRST202  ← 없으면 이렇게 나온다
 *
 * B 계정으로 GET /rest/v1/discoveries?visibility=eq.public → 200 · 남의 기록 7행
 *   (대조군) 태평양 bbox → 200 · 0행 · 전체 조회 visibility 분포 {public:15}
 * ```
 *
 * 🔴 **①이 왜 틀렸나.** "적용된 것은 0001~0003"이라고 쓴 것은 **오너가 그렇게 보고한
 *    시점의 기억**이고, 그 뒤 0007까지 적용됐는데 나는 **다시 재지 않고 주석을
 *    유지했다.** 서버 상태를 코드 주석에 사실로 적으면 그 주석이 낡는다 —
 *    `제출 문서 수치는 틀린다`와 같은 종류다.
 *
 * 🔴 **②가 왜 틀렸나.** 새 서버 함수가 필요하다고 단정했는데, 필요한 것은 함수가
 *    아니라 **쿼리**였다. `discoveries_read` 정책(0001)이 이미
 *    `visibility <> 'private'`인 남의 기록을 **로그인한 사용자에게 준다.**
 *    내가 "함수가 없다"고 말한 것은 **정책을 안 읽고 지도 코드만 본 결과**다
 *    (`MapPins`가 내 기록만 그리는 것은 정책 문제가 아니라 지도가 로컬 저장소를
 *    읽기 때문이다). → 그래서 `0009`를 쓰지 않았다. 안 필요한 마이그레이션을
 *    만들면 오너에게 붙여넣게 하는 일이 하나 더 생긴다.
 *
 * ✅ **③도 이제 해결됐다**(2026-08-12). 화면 15(장소 상세)가 입구가 되고,
 *    화면 16([com.catchflower.app.ui.place.RecordDetailScreen])이 이 층을 **실제로
 *    부른다.** 남의 기록을 모으는 쪽은 [PlaceDiscoveryService]다(서버 함수가 아니라
 *    bbox 조회다).
 *
 *    ⚠️ **아직 실기기에서 좋아요·댓글을 쓰지 않았다.** `reactions`·`comments`(읽기)는
 *       위 실측에 있지만 `postComment`·`like`·`report`의 **성공 응답은 못 쟀다** —
 *       `구현현황_AOS.md`에 그 상태로 적는다. 화면이 붙은 것과 동작을 확인한 것은
 *       다른 일이다(`빌드 성공은 증거가 아니다`).
 *
 * ⚠️ **아래 응답 형태 중 실측인 것과 아닌 것을 갈라 둔다.** `reactions`의 배열 형태와
 *    `public_profiles` 조회는 위에서 실제로 태웠다. `postComment`·`deleteComment`·
 *    `report`의 **성공 응답 형태는 아직 못 쟀다**(남의 기록에 실제로 쓰지 않았다) —
 *    상상한 형식을 실측처럼 적어 두면 (22)처럼 **테스트만 통과하고 앱은 안 된다.**
 *
 * ⚠️ 이음새는 [RankingService]와 같은 이유로 같은 모양이다 — `TokenSource` 주입 ·
 *    `baseUrl`/`anonKey` 주입 · `log` 주입. 이유는 그쪽 주석에 있다.
 */
interface ReactionSource {

    /**
     * 화면 16 `좋아요 12 · 댓글 3` + 내가 눌렀는지. **한 번의 왕복**이다(0007 4절).
     *
     * ⚠️ 볼 수 없는 기록에는 **오류가 아니라 0**이 온다(`discovery_reactions`가
     *    `security definer`가 아니라서 호출자의 RLS를 탄다 — 0007 4절).
     *    즉 `Loaded(0,0,false)`는 "반응이 없다"와 "그 기록을 볼 권한이 없다"를 **구분하지
     *    못한다.** 화면 16은 남의 공개 기록에서만 열리므로 지금은 문제가 아니지만,
     *    비공개로 바뀐 기록을 열어 두고 있으면 조용히 0이 된다.
     */
    suspend fun reactions(discoveryId: String): ReactionResult<Reactions>

    /** 좋아요. **이미 눌러 둔 상태여도 성공**이다 — 이유는 [ReactionService.like]. */
    suspend fun like(discoveryId: String): ReactionResult<Unit>

    /** 좋아요 취소. A 문서 3절 `좋아요를 취소했어요`. */
    suspend fun unlike(discoveryId: String): ReactionResult<Unit>

    /** 한 기록의 댓글, **최신순**(A 문서 화면 15 `최신순 ▾`과 같은 순서). */
    suspend fun comments(discoveryId: String): ReactionResult<CommentList>

    /**
     * uuid → 닉네임. 화면 16 **작성자 줄**(`꽃보다효산`)이 쓴다.
     *
     * 🔴 **[comments]가 안에서 쓰던 것을 밖으로 낸 것이다**(2026-08-12). 발견 기록의
     *    작성자 이름은 댓글 목록에 안 들어 있는데(작성자가 자기 기록에 댓글을 안 달면
     *    한 번도 안 나온다) 화면 16의 첫 줄이 그 이름이다.
     *
     * ⚠️ **키가 없는 것과 값이 null인 것이 다르다.** 키가 없으면 `public_profiles`에
     *    그 사람이 없다는 뜻이고(탈퇴 · 0001 7-2), 값이 null이면 닉네임을 안 정한
     *    사람이다. 화면은 [CommentList.namesLoaded]와 같은 판정을 해야 한다 —
     *    **조회 자체가 실패한 것**([ReactionResult.Failed])을 탈퇴로 읽으면
     *    살아 있는 사람이 `탈퇴한 사용자예요`로 굳는다(A 문서 3절).
     */
    suspend fun profiles(ids: Set<String>): ReactionResult<Map<String, String?>>

    /**
     * 댓글 등록. 화면 16 `댓글을 남겨보세요` + `등록`.
     *
     * 빈 본문·[GamePolicy.COMMENT_MAX_LENGTH] 초과는 **부르지 않고** [ReactionResult.Rejected]다.
     */
    suspend fun postComment(discoveryId: String, body: String): ReactionResult<Unit>

    /**
     * 댓글 삭제. C-9 **수정 불가 · 본인과 사진 소유자가 삭제**라서 `deleted_at`만 채운다.
     */
    suspend fun deleteComment(commentId: String): ReactionResult<Unit>

    /**
     * 신고. 화면 16 헤더 우측 `신고`. A 문서 3절 `신고를 접수했어요. 확인 후 처리됩니다.`
     *
     * ⚠️ **같은 기록을 두 번 신고해도 성공으로 본다** — 이유는 [ReactionService.report].
     */
    suspend fun report(discoveryId: String, reason: String?): ReactionResult<Unit>
}

/**
 * 반응 조회·전송 결과.
 *
 * ⚠️ **[RankingResult]와 모양이 같은데 왜 따로 두는가.** 이름이 `RankingResult`인
 *    타입을 댓글 등록이 돌려주면 읽는 사람이 랭킹 코드를 찾아간다. 이 저장소는
 *    서비스마다 결과 타입을 따로 둔다(`RegionUpdateResult`·`RegionSearchResult`).
 *    다만 **[Failed]에 0/빈 목록을 쓰지 않는다는 규칙은 같다** — 그쪽 주석이 원본이다.
 */
sealed interface ReactionResult<out T> {
    data class Loaded<T>(val value: T) : ReactionResult<T>

    /** 서버를 못 불렀다. 화면은 **반응 0이 아니라 오류**를 말해야 한다. */
    data class Failed(val code: Int, val pgCode: String = "") : ReactionResult<Nothing>

    /**
     * 보내기 전에 우리가 막았다. **네트워크를 부르지 않았다.**
     *
     * 🔴 서버 `comments_body_len`(1~200)과 같은 판정을 **먼저** 한다. 서버만 믿으면
     *    빈 댓글에 왕복 한 번을 쓰고 사용자는 `연결이 불안정해요`를 읽는다 —
     *    원인이 자기 입력인데 네트워크 탓으로 보인다.
     */
    data class Rejected(val reason: Reason) : ReactionResult<Nothing> {
        enum class Reason { EMPTY_BODY, TOO_LONG }
    }

    /** 서버 기능이 꺼진 빌드(키 없음). 오류 문구도 띄우지 않는다. */
    data object NotConfigured : ReactionResult<Nothing>
}

/** 화면 16 반응 줄. `discovery_reactions`가 준 세 칸 그대로다. */
data class Reactions(
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
)

/**
 * 댓글 목록 + **이름을 받아 왔는지**.
 *
 * 🔴 **[CommentRow.nickname]이 `null`인 이유가 두 가지고, 화면 문구가 서로 다르다**
 *    (A 문서 3절 `화면 16에서 값이 없거나 실패한 칸`, 2026-08-11):
 *    ① **탈퇴한 사람의 댓글** — `public_profiles`는 `deleted_at is null`인 사용자만
 *       담으므로(0001 7-2) 이름을 **영구히** 못 받는다 → `탈퇴한 사용자예요`.
 *    ② **이름 조회가 실패했다**(네트워크) → 이름 칸을 **비우고** 본문은 그린다.
 *       다시 시도하면 오기 때문에, 여기에 "탈퇴"라고 쓰면 **살아 있는 사람을
 *       탈퇴로 표시하고 그대로 굳는다.**
 *
 *    `nickname`만 보면 두 경우가 **같은 값**이라 화면이 구분할 수 없다. 그래서
 *    [namesLoaded]를 함께 준다 — 이 값이 false면 화면은 이름 칸을 비운다.
 */
data class CommentList(
    val rows: List<CommentRow>,
    /**
     * 닉네임 조회가 성공했나. `false`면 [CommentRow.nickname]의 `null`은
     * **탈퇴가 아니라 "모른다"** 다.
     *
     * 댓글이 0건이면 이름을 물을 일이 없으므로 `true`다(모르는 것이 없다).
     */
    val namesLoaded: Boolean,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** 댓글 한 줄. [nickname]의 `null` 판정은 [CommentList] 주석을 반드시 읽어야 한다. */
data class CommentRow(
    val id: String,
    val userId: String,
    val nickname: String?,
    val body: String,
    val createdAt: String,
)

class ReactionService(
    private val auth: TokenSource,
    /**
     * 내 uuid. **`likes.user_id`·`reports.reporter_id`가 `not null`이고 기본값이 없다** —
     * 서버가 `auth.uid()`로 채워 주지 않으므로 본문에 넣어야 한다.
     *
     * ⚠️ [AuthAccount]를 그대로 받지 않는다. 그 타입은 `needsReauth`·`reset`까지 요구해서
     *    JVM 테스트가 쓰지도 않을 두 함수를 구현하게 된다.
     */
    private val myUserId: suspend () -> String?,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ 주입한다 — `android.util.Log`는 JVM에서 던지고 **실패 경로가 전부 로그를 지난다.** */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
    /**
     * `deleted_at`에 넣을 시각.
     *
     * 🔴 **서버 `now()`를 쓸 수가 없다.** PostgREST에 보내는 것은 JSON 값이라 SQL 함수를
     *    넣을 수 없고, 정책 `comments_soft_delete`는 `deleted_at is not null`만 본다.
     *    그래서 **기기 시계**가 들어간다 — 기기 시계가 틀리면 삭제 시각도 틀린다.
     *    화면에 안 보이는 값이라 증상이 없다. 정렬·통계에 쓰려면 그때 서버 함수로 옮긴다.
     *    (테스트가 시각을 고정할 수 있게 주입한다.)
     */
    private val now: () -> Long = { System.currentTimeMillis() },
) : ReactionSource {

    interface Transport {
        /**
         * @param method `GET`·`POST`·`PATCH`·`DELETE`.
         *   ⚠️ [RankingService.Transport]는 본문 유무로 GET/POST를 갈랐다. 여기는
         *   **본문 없는 DELETE**와 **본문 있는 PATCH**가 둘 다 있어서 그 규칙이 안 통한다.
         */
        suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
        ): Response

        data class Response(val code: Int, val body: String)
    }

    override suspend fun reactions(discoveryId: String): ReactionResult<Reactions> {
        val args = JSONObject().put(ARG_DISCOVERY, discoveryId)
        return request("$baseUrl/rest/v1/rpc/$FN_REACTIONS", "POST", args.toString()) { body ->
            // `returns table (...)`이라 PostgREST가 **배열**을 준다 — 스칼라가 아니다.
            // 행이 없으면 형식이 바뀐 것이다(`select`가 항상 한 행을 만든다).
            val arr = JSONArray(body)
            val o = arr.optJSONObject(0) ?: return@request null
            Reactions(
                likeCount = o.getInt("like_count"),
                commentCount = o.getInt("comment_count"),
                likedByMe = o.getBoolean("liked_by_me"),
            )
        }
    }

    /**
     * 🔴 **중복 좋아요를 실패로 만들지 않는다.** `likes`의 키는 `(discovery_id, user_id)`
     *    뿐이라(0007 2절) 두 번째 삽입은 유일성 위반이다. 그걸 오류로 올리면 **다른 기기에서
     *    이미 누른 사용자가 `연결이 불안정해요`를 읽는다** — 원하는 상태(좋아요가 눌려 있다)는
     *    이미 이뤄져 있는데 앱이 고장난 것처럼 보인다.
     *
     * ⚠️ 그래서 [CONFLICT]와 pgcode [DUP_KEY]를 성공으로 삼킨다. **다른 4xx는 삼키지 않는다** —
     *    `42501`(RLS 거절)을 성공으로 만들면 **비공개 기록에 좋아요가 눌린 것처럼 보이고**
     *    다음 조회에서 숫자가 안 늘어난다.
     */
    override suspend fun like(discoveryId: String): ReactionResult<Unit> {
        val me = myUserId() ?: return ReactionResult.Failed(401)
        val body = JSONObject()
            .put(COL_DISCOVERY_ID, discoveryId)
            .put(COL_USER_ID, me)
        return sendUnit("$baseUrl/rest/v1/likes", "POST", body.toString(), idempotentOn409 = true)
    }

    /**
     * 🔴 **필터를 반드시 두 개 다 붙인다.** PostgREST의 DELETE는 필터가 없으면
     *    **보이는 행 전부를 지운다.** RLS `likes_delete_self`가 내 것으로 한정하니
     *    필터를 빠뜨려도 남의 좋아요는 안 지워지지만, **내가 눌러 둔 모든 좋아요가
     *    사라진다** — 화면에는 방금 누른 하트만 꺼지므로 알아챌 방법이 없다.
     *
     * ⚠️ `user_id` 필터는 RLS와 겹쳐서 없어도 되지만 남긴다. 정책은 나중에 바뀔 수 있고,
     *    **정책이 무엇을 하든 이 요청 자체가 한 행을 뜻해야** 읽는 사람이 안심한다.
     */
    override suspend fun unlike(discoveryId: String): ReactionResult<Unit> {
        val me = myUserId() ?: return ReactionResult.Failed(401)
        val url = "$baseUrl/rest/v1/likes" +
            "?$COL_DISCOVERY_ID=eq.$discoveryId&$COL_USER_ID=eq.$me"
        return sendUnit(url, "DELETE", null)
    }

    /**
     * 댓글 목록. **왕복이 두 번이다** — 댓글을 받고, 그 작성자들의 닉네임을 받는다.
     *
     * 🔴 **`select=...,users(nickname)`으로 한 번에 받으면 안 된다.** `comments.user_id`는
     *    `public.users`를 가리키고 그 테이블의 RLS는 `users_read_self`(본인 행만)다 —
     *    **남의 닉네임이 조용히 비어 온다.** 그리고 화면 16의 댓글은 대부분 남의 것이다.
     *    남의 이름은 `public_profiles` 뷰로만 본다(0001 7-2).
     *
     * ⚠️ `public_profiles`를 임베드(`public_profiles(nickname)`)하는 방법은 **쓰지 않는다.**
     *    뷰를 통한 관계 추론은 PostgREST 버전에 따라 다르고, 0007이 적용되지 않아
     *    **우리 프로젝트에서 되는지 못 재 봤다.** 되는 걸 확인하면 왕복을 하나 줄인다.
     *
     * ⚠️ **`deleted_at is null`을 쿼리에 넣지 않는다.** 정책 `comments_read`가 이미 걸고,
     *    양쪽에 두면 "어느 쪽이 진짜 규칙인가"가 흐려진다(0007 3절이 정책에 둔 이유).
     */
    override suspend fun comments(discoveryId: String): ReactionResult<CommentList> {
        val url = "$baseUrl/rest/v1/comments" +
            "?$COL_DISCOVERY_ID=eq.$discoveryId" +
            "&select=id,$COL_USER_ID,body,created_at" +
            "&order=created_at.desc"
        val raw = when (val rows = request(url, "GET", null) { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }) {
            is ReactionResult.Loaded -> rows.value
            // 실패·미설정은 **그대로 올린다.** 빈 목록으로 바꾸면 화면이
            // "댓글이 없어요"가 되고, 그건 조회 실패와 다른 말이다.
            is ReactionResult.Failed -> return ReactionResult.Failed(rows.code, rows.pgCode)
            is ReactionResult.Rejected -> return ReactionResult.Rejected(rows.reason)
            ReactionResult.NotConfigured -> return ReactionResult.NotConfigured
        }
        // 댓글이 0건이면 이름을 물을 일이 없다 — 왕복을 아끼는 것이 아니라
        // **모르는 것이 없으므로** `namesLoaded = true`다.
        if (raw.isEmpty()) return ReactionResult.Loaded(CommentList(emptyList(), true))

        val names = nicknames(raw.map { it.getString(COL_USER_ID) }.toSet())
        // 🔴 **닉네임 조회가 실패하면 목록 전체를 실패로 만들지 않는다.** 댓글 본문은
        //    이미 받았고, 실패하면 **댓글이 사라진다** — 네트워크는 이미 잘 되고 있다.
        //    대신 실패했다는 사실을 [CommentList.namesLoaded]로 실어 보낸다:
        //    그게 없으면 화면이 이 null을 **탈퇴로 오해**한다(A 문서 3절).
        val byId = if (names is ReactionResult.Loaded) names.value else emptyMap()
        return ReactionResult.Loaded(
            CommentList(
                rows = raw.map { o ->
                    val uid = o.getString(COL_USER_ID)
                    CommentRow(
                        id = o.getString("id"),
                        userId = uid,
                        // 값 자체는 `stringOrNull`로 읽었다 — 뷰가 JSON `null`을 주면
                        // 기기에서는 `"null"`이 되어 **작성자 이름이 `null`로 뜬다**
                        // (`JsonNull` 주석의 그 사고).
                        nickname = byId[uid],
                        body = o.getString("body"),
                        createdAt = o.getString("created_at"),
                    )
                },
                namesLoaded = names is ReactionResult.Loaded,
            ),
        )
    }

    /**
     * uuid → 닉네임. 없는 사람(탈퇴)은 키가 아예 없다.
     *
     * ⚠️ [profiles]로 밖에서도 부른다 — 화면 16의 **작성자 이름**이 여기서 온다.
     *    두 벌로 만들지 않는다(왕복 규칙·오류 처리가 갈리면 한쪽만 고쳐진다).
     */
    override suspend fun profiles(ids: Set<String>): ReactionResult<Map<String, String?>> =
        nicknames(ids)

    private suspend fun nicknames(ids: Set<String>): ReactionResult<Map<String, String?>> {
        if (ids.isEmpty()) return ReactionResult.Loaded(emptyMap())
        val url = "$baseUrl/rest/v1/public_profiles" +
            "?id=in.(${ids.joinToString(",")})&select=id,nickname"
        return request(url, "GET", null) { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                o.getString("id") to o.stringOrNull("nickname")
            }
        }
    }

    override suspend fun postComment(discoveryId: String, body: String): ReactionResult<Unit> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ReactionResult.Rejected(EMPTY)
        if (charLength(trimmed) > GamePolicy.COMMENT_MAX_LENGTH) {
            return ReactionResult.Rejected(TOO_LONG)
        }
        val me = myUserId() ?: return ReactionResult.Failed(401)
        val payload = JSONObject()
            .put(COL_DISCOVERY_ID, discoveryId)
            .put(COL_USER_ID, me)
            .put("body", trimmed)
        return sendUnit("$baseUrl/rest/v1/comments", "POST", payload.toString())
    }

    /**
     * C-9 soft delete. **본문을 함께 보내지 않는다** — 정책 `comments_soft_delete`의
     * `with check (deleted_at is not null)`은 본문 변경을 막지 않고, `body`를 같이
     * PATCH하면 **"수정 불가"가 우회된다.**
     */
    override suspend fun deleteComment(commentId: String): ReactionResult<Unit> {
        val url = "$baseUrl/rest/v1/comments?id=eq.$commentId"
        val payload = JSONObject().put("deleted_at", DiscoveryStore.encodeTime(now()))
        return sendUnit(url, "PATCH", payload.toString())
    }

    /**
     * 🔴 **두 번째 신고를 실패로 보이지 않게 한다.** `reports`는
     *    `unique (discovery_id, reporter_id)`다(0001 5절 — 한 사람이 3회 신고로
     *    자동 숨김을 혼자 발동시키지 못하게 한 것). 그러니 두 번째는 유일성 위반인데,
     *    그걸 오류로 띄우면 **"신고했는데 실패했다"** 가 되어 다시 누르게 만든다.
     *    A 문서의 응답은 `신고를 접수했어요. 확인 후 처리됩니다.` 하나뿐이고,
     *    이미 접수된 것도 그 문장이 사실이다.
     *
     * ⚠️ **신고 결과를 화면에서 확인할 방법이 없다** — `reports_read_own`이 본인 것만 주고,
     *    자동 숨김은 3명이 모여야 도는 서버 판정이다(`is_hidden`). 그래서 이 호출이
     *    조용히 실패하면 아무도 모른다. 실패는 반드시 [log]를 지난다.
     */
    override suspend fun report(discoveryId: String, reason: String?): ReactionResult<Unit> {
        val me = myUserId() ?: return ReactionResult.Failed(401)
        val payload = JSONObject()
            .put(COL_DISCOVERY_ID, discoveryId)
            .put("reporter_id", me)
            // ⚠️ `putOpt`다 — `reason`은 nullable 컬럼이고, `JSONObject.NULL`을 넣는 것과
            //    키를 빼는 것은 PostgREST에서 같지 않다(`DiscoveryStore.toJson` 주석).
            .apply { putOpt("reason", reason?.trim()?.ifEmpty { null }) }
        return sendUnit("$baseUrl/rest/v1/reports", "POST", payload.toString(), idempotentOn409 = true)
    }

    /** 본문을 안 읽는 호출. 성공이면 [Unit]. */
    private suspend fun sendUnit(
        url: String,
        method: String,
        body: String?,
        idempotentOn409: Boolean = false,
    ): ReactionResult<Unit> = request(url, method, body, idempotentOn409) { Unit }

    /**
     * 한 번 보내고, 401이면 갱신해서 한 번 더. [RankingService]와 같은 이유다.
     *
     * @param idempotentOn409 유일성 위반을 성공으로 본다. [like]·[report]만 쓴다.
     */
    private suspend fun <T> request(
        url: String,
        method: String,
        body: String?,
        idempotentOn409: Boolean = false,
        parse: (String) -> T?,
    ): ReactionResult<T> {
        if (!configured) return ReactionResult.NotConfigured
        val token = auth.accessToken() ?: return ReactionResult.Failed(401)
        val first = send(url, method, body, token)
        val res = if (first.code == 401) {
            val fresh = auth.refresh() ?: return ReactionResult.Failed(401)
            send(url, method, body, fresh)
        } else {
            first
        }
        val code = res.code
        if (code !in 200..299) {
            val pgCode = runCatching { JSONObject(res.body).optString("code") }.getOrDefault("")
            if (idempotentOn409 && (code == CONFLICT || pgCode == DUP_KEY)) {
                // 이미 있는 것이므로 원하는 상태다. 로그는 남긴다 —
                // 이게 자주 보이면 화면이 이미 누른 상태를 못 읽고 있다는 뜻이다.
                log("이미 있는 행이다 · HTTP $code${pgCode.dash()} — 성공으로 본다")
                // ⚠️ 본문 없이 [parse]를 부른다. **[sendUnit]만 이 경로를 쓰고** 그
                //    `parse`는 본문을 안 본다. 본문을 읽는 parse가 들어오면 여기서
                //    null이 되어 실패로 남는다 — 캐스트로 억지로 성공을 만들지 않는다.
                val value = runCatching { parse("") }.getOrNull()
                    ?: return ReactionResult.Failed(code, pgCode)
                return ReactionResult.Loaded(value)
            }
            // 실패 형태를 구분해 남긴다:
            //   `42501` = RLS 거절 (볼 수 없는 기록 · 남의 uuid를 넣었다)
            //   `23514` = comments_body_len 위반 (클라이언트 검사를 지나쳤다)
            //   `PGRST202` = 함수·인자 이름이 틀렸다 → **0007이 적용 안 된 상태가 이것이다**
            //   `PGRST205` = 테이블이 스키마 캐시에 없다 → 같은 원인
            log("반응 요청 실패 · $method · HTTP $code${pgCode.dash()}")
            return ReactionResult.Failed(code, pgCode)
        }
        val parsed = runCatching { parse(res.body) }.getOrElse { e ->
            // 🔴 형식이 바뀐 것을 `좋아요 0`으로 보여주면 안 된다 — 화면이 조용히
            //    "아무도 안 눌렀다"가 되고 서버가 바뀐 사실은 아무도 모른다.
            log("반응 응답을 읽을 수 없다 · ${e.javaClass.simpleName}")
            null
        } ?: return ReactionResult.Failed(code, PARSE_FAILED)
        return ReactionResult.Loaded(parsed)
    }

    private suspend fun send(
        url: String,
        method: String,
        body: String?,
        token: String,
    ): Transport.Response =
        runCatching { transport.send(url, method, anonKey, token, body) }.getOrElse { e ->
            if (e is CancellationException) throw e
            Transport.Response(0, "")
        }

    /** 전역이 아니라 주입된 값으로 판단한다 — [RankingService]와 같은 이유. */
    private val configured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    private fun String.dash(): String = if (isEmpty()) "" else " · $this"

    internal object HttpTransport : Transport {
        override suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
        ): Transport.Response = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                // ⚠️ anon 키가 아니라 사용자 토큰이다. RLS `user_id = auth.uid()`가
                //    전부 여기에 달려 있다.
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                if (body != null) doOutput = true
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Transport.Response(
                    code = code,
                    body = stream?.bufferedReader()?.use { it.readText() } ?: "",
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        /** 응답을 읽을 수 없을 때의 표시. HTTP 코드로는 구분할 수 없다. */
        const val PARSE_FAILED = "PARSE"

        /** Postgres 유일성 위반. PostgREST가 HTTP 409로 감싼다. */
        const val DUP_KEY = "23505"
        const val CONFLICT = 409

        private const val TIMEOUT_MS = 15_000

        /**
         * 🔴 **RPC 인자 이름은 서버가 정한다.** `0007`의 함수 시그니처가
         *    `discovery_reactions(d_id uuid)`이므로 `discovery_id`로 보내면
         *    **`PGRST202`(함수를 못 찾았다)** 가 되고, 그건 "아직 배포 안 됐다"와
         *    **같은 코드**라서 원인을 헷갈린다.
         */
        private const val ARG_DISCOVERY = "d_id"
        private const val FN_REACTIONS = "discovery_reactions"

        // ── 0007·0001의 컬럼명. 혼자 바꾸지 않는다. ──
        private const val COL_DISCOVERY_ID = "discovery_id"
        private const val COL_USER_ID = "user_id"

        private val EMPTY = ReactionResult.Rejected.Reason.EMPTY_BODY
        private val TOO_LONG = ReactionResult.Rejected.Reason.TOO_LONG

        /**
         * 서버 `char_length`와 **같은 방식**으로 센다.
         *
         * 🔴 **`String.length`를 쓰면 안 된다.** 코틀린은 UTF-16 단위를 세므로 이모지
         *    하나가 **2**다. Postgres `char_length`는 코드 포인트를 세므로 **1**이다.
         *    그러면 이모지 100개짜리(서버 기준 100자) 댓글을 클라이언트가 **200자
         *    초과로 거절한다** — 사용자는 화면의 글자 수가 자기가 쓴 것과 다르다고 본다.
         *    한글은 둘 다 1이라 **한국어로만 테스트하면 안 보인다.**
         *
         * ⚠️ 반대 방향은 없다(코틀린 길이 ≥ 코드 포인트 수). 즉 서버가 거절할 것을
         *    통과시키는 실수는 이 방향에서 안 나온다.
         */
        internal fun charLength(text: String): Int = text.codePointCount(0, text.length)
    }
}
