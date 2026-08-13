package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 화면 19 `검색` · 화면 16 `친구 추가`가 쓰는 서버 호출.
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸면서 생겼다.
 *
 * ## 🔴 마이그레이션이 필요 없다
 *
 * 서버가 이미 열려 있다 — **새 SQL을 만들지 않았다**(공유계약 6절: DB 스키마는 한쪽만 만든다).
 * - 닉네임 검색: `public.public_profiles` 뷰 + `grant select … to authenticated`(0001 7-2).
 *   그 뷰는 `security_invoker`가 **일부러 꺼져 있어** RLS를 우회하고 공개 컬럼만 낸다.
 * - 친구 요청: `friendships_insert_requester for insert with check (requester_id = auth.uid())`.
 * - 내 관계 조회: `friendships_read_involved`.
 *
 * ## 🔴 `추가`를 눌러도 친구가 되지 않는다
 *
 * `friendships.state`의 기본값은 `pending`이고 `accepted`로 바꾸는 정책은
 * **받은 쪽만**이다(`friendships_update_addressee` · C-2 상호 수락).
 * 그래서 성공 문구가 `친구 요청을 보냈어요`다 — `{이름}님과 친구가 되었어요`를 쓰면
 * **친구 목록에 그 사람이 없는 이유**를 사용자가 알 수 없다(A 문서 3절 ③).
 */
interface FriendSource {

    /**
     * 닉네임으로 찾는다. 결과에는 **버튼 자리 상태가 이미 붙어 있다**
     * ([FriendRules.merge]) — 화면이 두 응답을 맞춰 보지 않게 한다.
     */
    suspend fun search(query: String): FriendResult<List<FriendRules.Found>>

    /** 친구 요청을 보낸다. 성공은 **요청이 저장됐다**는 뜻이고 친구가 됐다는 뜻이 아니다. */
    suspend fun request(userId: String): FriendResult<Unit>
}

/** [FriendSource]의 결과. [ReactionResult]와 같은 모양이다 — 실패를 뭉개지 않는다. */
sealed interface FriendResult<out T> {
    data class Loaded<T>(val value: T) : FriendResult<T>

    /** @param pgCode PostgREST가 준 `code`. 빈 문자열이면 못 읽었다. */
    data class Failed(val code: Int, val pgCode: String = "") : FriendResult<Nothing>

    /**
     * 검색어가 [FriendRules.MIN_QUERY]보다 짧다.
     *
     * ⚠️ **[Failed]로 만들지 않는다.** 네트워크 실패와 같은 값으로 두면 화면이
     *    `연결이 불안정해요`를 띄우고, 사용자는 **글자를 더 넣으면 된다는 것을 모른다.**
     */
    data object TooShort : FriendResult<Nothing>

    /** 키 없는 빌드. 화면은 검색창을 그리지 않는다. */
    data object NotConfigured : FriendResult<Nothing>
}

class FriendService(
    private val auth: TokenSource,
    /**
     * 내 uuid. **`friendships.requester_id`에 넣어야 한다** — 서버가 `auth.uid()`로
     * 채워 주지 않는다([ReactionService]와 같은 이유).
     */
    private val myUserId: suspend () -> String?,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ 주입한다 — `android.util.Log`는 JVM에서 던진다. */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : FriendSource {

    interface Transport {
        suspend fun send(
            url: String,
            method: String,
            apiKey: String,
            bearer: String,
            body: String?,
        ): Response

        data class Response(val code: Int, val body: String)
    }

    /**
     * 왕복 **두 번**이다: 공개 프로필을 찾고, 내 친구 관계를 읽는다.
     *
     * 🔴 **한 번에 못 받는다.** `public_profiles`에는 관계가 없고, `friendships`를
     *    임베드하면 RLS가 걸린 테이블을 뷰를 통해 조인하는 모양이 되어
     *    **우리 프로젝트에서 되는지 못 재 봤다**([ReactionService.comments]와 같은 판단).
     *
     * ⚠️ 관계 조회가 실패하면 **검색 결과도 안 보여준다.** 관계 없이 그리면 모든 줄이
     *    `추가`가 되고, 이미 친구인 사람에게 요청을 보내 **409로 실패하는 버튼**이 된다.
     */
    override suspend fun search(query: String): FriendResult<List<FriendRules.Found>> {
        if (FriendRules.tooShort(query)) return FriendResult.TooShort
        val me = myUserId() ?: return FriendResult.Failed(401)
        val pattern = FriendRules.sanitize(query)
        if (FriendRules.tooShort(pattern)) return FriendResult.TooShort
        val encoded = URLEncoder.encode("*$pattern*", "UTF-8")
        val profilesUrl = "$baseUrl/rest/v1/$VIEW_PROFILES" +
            "?select=$COL_ID,$COL_NICKNAME&$COL_NICKNAME=ilike.$encoded&limit=${FriendRules.SEARCH_LIMIT}"

        val profiles = request(profilesUrl, "GET", null) { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.stringOrNull(COL_ID) ?: return@mapNotNull null
                // 🔴 닉네임이 null인 행은 **뺀다.** 빈 이름 줄에 `추가` 버튼만 뜨면
                //    누구를 추가하는지 알 수 없다.
                val nickname = o.stringOrNull(COL_NICKNAME) ?: return@mapNotNull null
                id to nickname
            }
        }
        // ⚠️ `as FriendResult<Nothing>`로 뭉개지 않는다. 실패 갈래를 그대로 올려야
        //    화면이 `연결이 불안정해요`와 `두 글자 이상`을 구분할 수 있다.
        val found = when (profiles) {
            is FriendResult.Loaded -> profiles.value
            is FriendResult.Failed -> return profiles
            FriendResult.TooShort -> return FriendResult.TooShort
            FriendResult.NotConfigured -> return FriendResult.NotConfigured
        }
        if (found.isEmpty()) return FriendResult.Loaded(emptyList())

        val edges = request(
            "$baseUrl/rest/v1/$TBL_FRIENDSHIPS?select=$COL_REQUESTER,$COL_ADDRESSEE,$COL_STATE",
            "GET",
            null,
        ) { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                FriendRules.Edge(
                    requesterId = o.stringOrNull(COL_REQUESTER) ?: return@mapNotNull null,
                    addresseeId = o.stringOrNull(COL_ADDRESSEE) ?: return@mapNotNull null,
                    state = o.stringOrNull(COL_STATE) ?: return@mapNotNull null,
                )
            }
        }
        return when (edges) {
            is FriendResult.Loaded ->
                FriendResult.Loaded(FriendRules.merge(found, edges.value, me))

            is FriendResult.Failed -> edges
            FriendResult.TooShort -> FriendResult.TooShort
            FriendResult.NotConfigured -> FriendResult.NotConfigured
        }
    }

    /**
     * 🔴 **`state`를 보내지 않는다.** 컬럼 기본값이 `pending`이므로 서버가 채운다 —
     *    여기서 `"pending"`을 적으면 enum 값이 두 곳에 생기고, 서버가 기본값을 바꾸는 날
     *    **클라이언트가 옛 값을 우겨서** 상호 수락 규칙이 깨진다.
     *
     * ⚠️ **이미 보낸 요청(409)을 성공으로 본다.** 키가 `(requester_id, addressee_id)`라
     *    두 번째 삽입은 유일성 위반인데, 원하는 상태(요청이 가 있다)는 이미 이뤄져 있다
     *    ([ReactionService.like]와 같은 판단). 화면이 목록을 갱신하기 전에 두 번 누르는
     *    경우가 실제로 생긴다.
     */
    override suspend fun request(userId: String): FriendResult<Unit> {
        val me = myUserId() ?: return FriendResult.Failed(401)
        if (me == userId) {
            // 서버 `friendships_no_self`가 막는다. 여기서 먼저 막는 이유는 화면이
            // 나를 목록에서 빼는 것과 같다 — 실패할 요청을 보내지 않는다.
            log("자기 자신에게 친구 요청을 보내려 했다 — 화면이 나를 목록에서 안 뺐다")
            return FriendResult.Failed(400, SELF_REQUEST)
        }
        val body = JSONObject()
            .put(COL_REQUESTER, me)
            .put(COL_ADDRESSEE, userId)
        return request(
            "$baseUrl/rest/v1/$TBL_FRIENDSHIPS",
            "POST",
            body.toString(),
            idempotentOn409 = true,
        ) { Unit }
    }

    private suspend fun <T> request(
        url: String,
        method: String,
        body: String?,
        idempotentOn409: Boolean = false,
        parse: (String) -> T?,
    ): FriendResult<T> {
        if (!configured) return FriendResult.NotConfigured
        val token = auth.accessToken() ?: return FriendResult.Failed(401)
        val first = send(url, method, body, token)
        val res = if (first.code == 401) {
            val fresh = auth.refresh() ?: return FriendResult.Failed(401)
            send(url, method, body, fresh)
        } else {
            first
        }
        val code = res.code
        if (code !in 200..299) {
            val pgCode = runCatching { JSONObject(res.body).optString("code") }.getOrDefault("")
            if (idempotentOn409 && (code == CONFLICT || pgCode == DUP_KEY)) {
                log("이미 보낸 친구 요청이다 · HTTP $code — 성공으로 본다")
                val value = runCatching { parse("") }.getOrNull()
                    ?: return FriendResult.Failed(code, pgCode)
                return FriendResult.Loaded(value)
            }
            // `42501` = RLS 거절(`requester_id`가 내 uuid가 아니다)
            // `PGRST205` = 뷰·테이블이 스키마 캐시에 없다 → 0001이 적용 안 된 상태
            log("친구 요청/검색 실패 · $method · HTTP $code${if (pgCode.isEmpty()) "" else " · $pgCode"}")
            return FriendResult.Failed(code, pgCode)
        }
        val parsed = runCatching { parse(res.body) }.getOrElse { e ->
            log("친구 응답을 읽을 수 없다 · ${e.javaClass.simpleName}")
            null
        } ?: return FriendResult.Failed(code, PARSE_FAILED)
        return FriendResult.Loaded(parsed)
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

    private val configured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

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
        const val PARSE_FAILED = "PARSE"

        /** 화면이 나를 목록에서 빼지 않았다는 표시. 서버까지 가지 않는다. */
        const val SELF_REQUEST = "SELF"

        const val DUP_KEY = "23505"
        const val CONFLICT = 409
        private const val TIMEOUT_MS = 15_000

        // ── 0001의 이름들. 혼자 바꾸지 않는다(공유계약 6절). ──
        const val VIEW_PROFILES = "public_profiles"
        const val TBL_FRIENDSHIPS = "friendships"
        const val COL_ID = "id"
        const val COL_NICKNAME = "nickname"
        const val COL_REQUESTER = "requester_id"
        const val COL_ADDRESSEE = "addressee_id"
        const val COL_STATE = "state"
    }
}
