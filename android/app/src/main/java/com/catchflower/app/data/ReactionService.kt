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
 * 🔴 **이 층은 아직 어느 화면도 부르지 않는다. 일부러 그렇다.**
 *    화면 16을 지금 그리면 **눌러도 아무 일이 없는 화면**이 하나 더 생긴다 — 세 가지가 없다:
 *    ① `0007`이 **아직 적용되지 않았다**(서버에 `likes`·`comments` 테이블이 없다.
 *       적용된 것은 0001~0003이다) ② **남의 발견 기록을 주는 서버 함수가 없다** —
 *       지금 지도는 `MapPins`가 말하듯 **내 기록만** 그린다 ③ 그래서 화면 16으로
 *       들어가는 입구(`자세히 보기`)도 없다.
 *    좋아요·댓글은 **남의 기록에만** 붙는 기능이라 ②가 풀리기 전에는 그릴 대상이 없다.
 *
 *    **그럼 왜 지금 쓰는가.** 이 층은 오너 조치를 기다리지 않고 만들 수 있고,
 *    JVM 테스트로 고정할 수 있는 것이 여기 전부 모여 있다(중복 좋아요·삭제 필터·
 *    글자 수 세는 방법·닉네임을 어디서 받는가). 0007이 붙는 날 화면만 얹으면 된다.
 *
 * ⚠️ **아래 응답 형태는 실측이 아니다.** `RankingService`의 주석에 붙은 표들은 전부
 *    실제 Supabase 응답이지만, 여기 것은 **0007이 적용되지 않아 한 번도 못 불렀다.**
 *    그래서 "실측"이라고 쓴 곳이 하나도 없다 — 상상한 형식을 실측처럼 적어 두면
 *    (22)에서처럼 **테스트만 통과하고 앱은 안 된다.** 적용 후 다시 재고 이 주석을 고친다.
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

    /** uuid → 닉네임. 없는 사람(탈퇴)은 키가 아예 없다. */
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
