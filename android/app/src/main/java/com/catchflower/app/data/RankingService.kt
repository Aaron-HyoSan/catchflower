package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.FriendState
import com.catchflower.app.data.model.RankEntry
import com.catchflower.app.data.model.RankedEntry
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 랭킹을 **서버에서 받아온다.** 화면 17·18·20·21이 쓴다.
 *
 * 🔴 **클라이언트가 순위를 세지 않는다** (계약 3절 · `진행.md` (23) 5절).
 *    세면 숫자를 조작할 수 있고, 무엇보다 **남의 발견 기록이 기기에 없으므로
 *    셀 수가 없다.** 서버 함수 3개가 센 결과를 그대로 그린다.
 *
 * ⚠️ [com.catchflower.app.data.model.RankingRules]는 **지우지 않는다.** 서버가 순위를
 *    주므로 화면은 더 이상 그걸 부르지 않지만, 그 규칙 표(동점 = ① 먼저 도달 ② 총
 *    발견 횟수)가 **서버 SQL과 같아야 한다는 계약**이고 테스트 16개가 그걸 고정한다.
 *    실측으로 서버도 같은 순서였다(아래 `region_tie`).
 */
interface RankingSource {
    suspend fun regionRanking(minMembers: Int? = null): RankingResult<RegionRanking>
    suspend fun friendRanking(): RankingResult<FriendRanking>
    suspend fun seasonSummary(): RankingResult<SeasonSummary>

    /**
     * 내 활동 지역. **랭킹과 따로 물어야 한다** — 이유는 [RegionRanking] 주석에 있다.
     */
    suspend fun myRegion(): RankingResult<MyRegion>

    /**
     * 화면 18 `친구 {N}명과 겨루는 중` · 화면 20 `친구 관리 {N}명`의 N.
     *
     * 🔴 **[friendRanking]의 행 수로 세면 안 된다.** 이번 시즌 발견이 0건인 친구는
     *    랭킹 응답에 행이 아예 없다(실측). (18)에서 7 vs 8로 겪은 사고다 —
     *    두 화면이 같은 것을 다른 숫자로 말하면 어느 쪽이 맞는지 화면만 봐선 모른다.
     */
    suspend fun friendCount(): RankingResult<Int>

    /**
     * 화면 02 목록의 `이웃 {N}명 활동 중` · `아직 이웃이 적어요`.
     *
     * 🔴 **이게 왜 랭킹 쪽에 있는가.** 화면 02는 지역 **선택** 화면인데 이 숫자는
     *    화면 17의 `{동명} 이웃 1,284명`과 **같은 뜻이어야 한다.** 서버
     *    `dong_member_count`가 `region_ranking`의 `dong_members` CTE와 글자 그대로
     *    같은 집합을 센다(0004). 두 정의가 갈리면 화면 02에서 `이웃 12명`을 보고
     *    고른 사용자가 화면 17에서 `이웃 3명`을 본다 — (18)에서 친구 수 7 vs 8로
     *    겪은 사고이고, 이번에는 **6개월간 못 바꾸는 선택**의 근거가 된다.
     *
     * ⚠️ **`public_profiles`를 세는 것으로 바꾸지 마라.** anon 키로도 되고 더 쉽지만
     *    (실측: `Content-Range: 0-0/11`) 그건 **가입자 수**다. 위 사고가 그것이다.
     *
     * ⚠️ 실패는 [RankingResult.Failed]다 — **0으로 만들지 않는다.** 0은
     *    `아직 이웃이 적어요`가 되고, 그건 "세어 봤다"는 뜻이다(A 문서 3절
     *    `화면 02에서 이웃 수를 모를 때`). 모를 때 화면은 줄을 **뺀다.**
     *
     * @param dongCode **행정동** 코드. 법정동을 넣으면 오류 없이 0이 나온다.
     */
    suspend fun dongMemberCount(dongCode: String): RankingResult<Int>
}

/**
 * 랭킹 조회 결과.
 *
 * ⚠️ **`null`이나 빈 목록으로 실패를 표현하지 않는다.** 화면 17·18의 빈 목록은
 *    **정상 상태이기도 하다**(아무도 아직 안 찍었다). 실패와 같은 값으로 만들면
 *    화면이 "이웃이 없어요"라고 말하는데 실제로는 **네트워크가 죽은 것**이 된다.
 *    사용자는 앱을 신뢰할 수 없고, 우리도 로그 없이는 구분할 수 없다.
 */
sealed interface RankingResult<out T> {
    data class Loaded<T>(val value: T) : RankingResult<T>

    /** 서버를 못 불렀다. 화면은 **빈 랭킹이 아니라 오류**를 말해야 한다. */
    data class Failed(val code: Int, val pgCode: String = "") : RankingResult<Nothing>

    /** 서버 기능이 꺼진 빌드(키 없음). 오류 문구도 띄우지 않는다 — 사용자 탓이 아니다. */
    data object NotConfigured : RankingResult<Nothing>
}

/**
 * 화면 17 지역 랭킹.
 *
 * 🔴 **[scope]가 `null`이면 "지역 랭킹이 없다"이고, 그건 실측으로 두 가지다:**
 *    ① 활동 지역(`users.dong_code`)을 아직 안 정했다 ② 정했지만 그 지역에
 *    **이번 시즌 발견이 한 건도 없다.**
 *
 *    실측한 응답이 **둘 다 똑같이 `[]`** 다(`region_none.json` = `region_with_dong.json`
 *    = `[]`). `scope`는 **행 안에** 실려 오므로 행이 없으면 알 수 없다.
 *    그래서 [RankingSource.myRegion]을 **따로 부른다** — 안 부르면 화면이
 *    "아직 이웃이 없어요"와 "동네를 안 골랐어요"를 구분할 수 없고,
 *    후자는 사용자가 **할 일이 있는데 안 알려주는** 상태다.
 */
data class RegionRanking(
    /** `dong` · `gu` · null(행이 없어 알 수 없음). B-6이 `gu`로 넓힌 결과가 온다. */
    val scope: String?,
    val regionCode: String?,
    /**
     * 화면 17 `{동명} 이웃 1,284명`.
     *
     * ⚠️ **가입자 수가 아니라 "이번 시즌 발견이 있는 사람" 수다**(0002 주석: 가입만 한
     *    사람을 세면 10명을 넘겨도 랭킹이 텅 빈다). 실측: 2계정 · 2건 → `member_count 2`.
     */
    val memberCount: Int,
    val rows: List<RankedEntry>,
)

/** 화면 18 친구 랭킹. **나를 포함한다.** */
data class FriendRanking(
    val rows: List<RankedEntry>,
) {
    /**
     * 🔴 **이 값을 "친구 수"로 쓰면 안 된다.** 서버 `scored` CTE가 `discoveries`를
     *    조인하므로 **이번 시즌 발견이 0건인 친구는 행이 아예 없다**(실측: 내 발견이
     *    0건이면 응답이 `[]`, 나조차 안 나온다 — `friend_empty.json`).
     *
     *    그래서 A 문서 `친구 8명과 겨루는 중` / 화면 20 `친구 관리 8명`을 이걸로
     *    만들면 **아직 안 찍은 친구가 빠져서 두 화면이 서로 다른 숫자를 말한다.**
     *    (18)에서 이미 같은 사고가 있었다 — 7 vs 8. 친구 수는 `friendships`를 센다.
     */
    val rankedFriendCount: Int get() = rows.count { !it.entry.isMe }

    val me: RankedEntry? get() = rows.firstOrNull { it.entry.isMe }
}

/** 화면 20·21 내 시즌 요약. 실측: 발견이 0건이어도 **행 1개**가 온다. */
data class SeasonSummary(
    val seasonStart: String,
    val seasonEnd: String,
    val isDormant: Boolean,
    val speciesCount: Int,
    val discoveryCount: Int,
    val placeCount: Int,
    val rareCount: Int,
)

/**
 * 내 `users` 행. 화면 20 프로필·활동 지역 · 화면 17 배너의 `{동명}`.
 *
 * ⚠️ **닉네임을 여기서 받는다.** 화면 20이 로컬에 저장된 닉네임을 쓰면 다른 기기에서
 *    바꾼 뒤 두 화면이 다른 이름을 말한다. 랭킹 행의 `is_me` 이름은 서버가 준
 *    `users.nickname`이므로 **같은 곳에서 받아야 한 사람으로 보인다.**
 */
data class MyRegion(
    val regionName: String?,
    val dongCode: String?,
    val guCode: String?,
    /** 기획서 9장 6개월 규칙의 기준 시각. **실측으로 서버가 안 채운다** — 아래 주석. */
    val regionChangedAt: String?,
    /** 화면 20 프로필. `handle_new_user`가 `꽃친구{앞4자리}`로 만든다(실측). */
    val nickname: String? = null,
    /** 화면 20 `2026년 3월부터 함께`의 기준. `2026-08-08T09:45:53.504149+00:00` 형태. */
    val createdAt: String? = null,
) {
    val isSet: Boolean get() = !dongCode.isNullOrEmpty()

    /**
     * 화면 17 배너·목록 라벨의 `{동명}`.
     *
     * `region_name`은 `서울특별시 마포구 연남동` 전체다(실측). 화면 17은 동만 쓰고
     * 화면 20은 전체를 쓴다. **마지막 어절**을 동으로 본다.
     */
    val dongName: String? get() = regionName?.trim()?.split(' ')?.lastOrNull()?.ifEmpty { null }
}

/**
 * Supabase RPC 구현체.
 *
 * ⚠️ 이음새는 [DiscoveryUploader]와 같은 이유로 같은 모양이다 — `TokenSource`(생성자가
 *    `Context`를 요구하는 [AuthService]를 JVM에서 못 만든다) · `baseUrl`/`anonKey`
 *    주입(전역을 보면 **그 맥에 `local.properties`가 있느냐로 테스트가 갈린다**) ·
 *    `log` 주입(`android.util.Log`가 JVM에서 던지고 **실패 경로가 전부 로그를 지난다**).
 */
class RankingService(
    // ⚠️ `TokenSource`가 아니라 [AuthAccount]다 — **내 uuid가 필요하다**
    //    ([regionRanking]의 🔴: 서버가 지역 랭킹에 `is_me`를 안 준다).
    private val auth: AuthAccount,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : RankingSource {

    interface Transport {
        /**
         * `body`가 null이면 GET이다.
         *
         * @param countOnly 개수만 필요하다. `Prefer: count=exact` + `Range: 0-0`을 붙여
         *   **행을 받지 않고** 총 개수를 [Response.contentRange]로 받는다.
         */
        suspend fun send(
            url: String,
            apiKey: String,
            bearer: String,
            body: String?,
            countOnly: Boolean,
        ): Response

        /**
         * 응답.
         *
         * ⚠️ **[contentRange]가 필요해서 `Pair<Int, String>`을 버렸다.** 개수 조회는
         *    본문이 아니라 헤더로 온다(실측: `Content-Range: 0-0/1`). PostgREST는
         *    `select=count()`를 **거부한다** — `PGRST123 Use of aggregate functions is
         *    not allowed`(실측). 그래서 본문 행을 세는 방법밖에 없는데, 그건
         *    **서버가 행을 자르면 조용히 적게 센다.**
         */
        data class Response(
            val code: Int,
            val body: String,
            /** `0-0/1` 형태. 개수 조회가 아니면 빈 문자열이다. */
            val contentRange: String = "",
        )
    }

    /**
     * 화면 17.
     *
     * 🔴 **서버 `region_ranking`은 `is_me`를 주지 않는다** — `friend_ranking`만 준다
     *    (0002 · `returns table`에 그 칸이 없다). 그래서 `is_me`만 믿으면 지역 랭킹의
     *    **내 행이 영원히 표시되지 않는다**: 화면 17 `내 순위`가 **1위인 사람에게도 `-`**로
     *    나오고, 목록에서 내 줄의 강조 테두리도 안 그려진다.
     *    실측(2026-08-17 · 릴리스 빌드): 내가 1종인데 `내 순위 -`, 같은 1종인 이웃은 5위로
     *    보였다. `-`는 "상위 목록 밖"이라는 정상 상태와 **글자가 똑같아서** 화면으로는
     *    구분이 안 된다(근거: `프로젝트 맥락/진행.md` (82)).
     *
     * 🔴 **서버를 고치지 않고 여기서 표시한다.** 0002는 iOS 터미널 소유고(계약 §6),
     *    `is_me`를 추가하면 마이그레이션 + 오너 실행이 필요하다. 반면 내 uuid는 앱이
     *    이미 알고 있다 — **순위를 다시 세는 것이 아니라 서버가 준 행에 표를 붙이는
     *    것뿐**이라 계약 3절(순위는 서버가 센다)에 걸리지 않는다.
     */
    override suspend fun regionRanking(minMembers: Int?): RankingResult<RegionRanking> {
        // ⚠️ 세션이 있으면 저장된 uuid를 그대로 준다(네트워크를 타지 않는다). 실패하면
        //    null이고, 그때는 예전처럼 표가 안 붙는다 — **랭킹 자체를 막지는 않는다.**
        val myId = runCatching { auth.userId() }.getOrNull()
        return rpc("region_ranking", JSONObject().apply { minMembers?.let { put("min_members", it) } }) {
            val rows = it.map { o -> rankedRow(o, myId) }
            RegionRanking(
                // ⚠️ 행이 없으면 scope를 **모른다**(null). "none"으로 채우지 않는다 —
                //    실측으로 `[]`는 지역 미설정과 발견 0건 **양쪽 다**이기 때문이다.
                // `scope`도 [stringOrNull]이다 — `"null"`이 들어오면 `SCOPE_GU` 비교가
                // 조용히 false가 되어 **구 랭킹에 동 이름**이 붙는다.
                scope = it.firstOrNull()?.stringOrNull("scope"),
                regionCode = it.firstOrNull()?.stringOrNull("region_code"),
                memberCount = it.firstOrNull()?.optInt("member_count") ?: 0,
                rows = rows,
            )
        }
    }

    override suspend fun friendRanking(): RankingResult<FriendRanking> =
        rpc("friend_ranking", JSONObject()) { FriendRanking(it.map { o -> rankedRow(o) }) }

    override suspend fun seasonSummary(): RankingResult<SeasonSummary> =
        rpc("my_season_summary", JSONObject()) { rows ->
            // 실측: 발견 0건이어도 행 1개가 온다. 없으면 그건 형식이 바뀐 것이다.
            val o = rows.firstOrNull() ?: return@rpc null
            SeasonSummary(
                seasonStart = o.getString("season_start"),
                seasonEnd = o.getString("season_end"),
                isDormant = o.getBoolean("is_dormant"),
                speciesCount = o.getInt("species_count"),
                discoveryCount = o.getInt("discovery_count"),
                placeCount = o.getInt("place_count"),
                rareCount = o.getInt("rare_count"),
            )
        }

    /**
     * 내 `users` 행. **RPC가 아니라 테이블 조회**다 — RLS `users_read_self`가 본인만 준다.
     */
    override suspend fun myRegion(): RankingResult<MyRegion> {
        if (!configured) return RankingResult.NotConfigured
        val token = auth.accessToken() ?: return RankingResult.Failed(401)
        val url = "$baseUrl/rest/v1/users" +
            "?select=nickname,region_name,dong_code,gu_code,region_changed_at,created_at"
        return request(url, null, token) { body ->
            val arr = JSONArray(body)
            // 본인 행이 없다 = `handle_new_user` 트리거가 안 돌았다. 지역 미설정과
            // 같은 화면으로 보내되 로그를 남긴다 — 이건 서버 쪽 사고다.
            if (arr.length() == 0) {
                log("users에 내 행이 없다 — handle_new_user 트리거를 확인해야 한다")
                MyRegion(null, null, null, null)
            } else {
                val o = arr.getJSONObject(0)
                // 🔴 **[stringOrNull]을 `optString(…).ifEmpty { null }`로 되돌리지 마라.**
                //    이 여섯 칸은 **전부 서버가 JSON `null`로 준다**(지역을 안 고른
                //    계정이 기본값이고, `region_changed_at`은 서버가 아예 안 채운다).
                //    안드로이드 `org.json`은 그걸 **문자열 `"null"`** 로 주므로
                //    `ifEmpty`가 안 걸리고, 화면 20에 `null · 2026년 8월부터 함께`와
                //    `null`이라는 동네가 그대로 떴다. **JVM 테스트는 전부 초록이었다** —
                //    이유는 [stringOrNull] 주석의 표에 있다.
                MyRegion(
                    regionName = o.stringOrNull("region_name"),
                    dongCode = o.stringOrNull("dong_code"),
                    guCode = o.stringOrNull("gu_code"),
                    regionChangedAt = o.stringOrNull("region_changed_at"),
                    nickname = o.stringOrNull("nickname"),
                    createdAt = o.stringOrNull("created_at"),
                )
            }
        }
    }

    /**
     * 수락된 친구 관계 수. **RPC가 아니라 테이블 조회**다 —
     * RLS `friendships_read_involved`가 당사자 둘에게만 준다(실측: anon은 `200 []`).
     *
     * 🔴 **`state=eq.accepted`를 빼면 안 된다.** 실측으로 필터 없이 조회하면
     *    `pending`(아직 수락 안 한 요청)까지 온다 — 그러면 화면 18이 **내가 요청만
     *    보낸 사람을 "겨루는 중인 친구"로 센다.** C-2가 상호 수락인 이유가 이것이다.
     *
     * ⚠️ **한 관계가 한 행이고 양쪽에 똑같이 보인다**(실측: A·B가 같은 1행을 받는다).
     *    그래서 `requester_id = 나 or addressee_id = 나`를 따로 합칠 필요가 없고,
     *    **합치면 오히려 두 번 센다.** 방향 조건도 걸지 않는다 — RLS가 이미 내 것만 준다.
     *
     * ⚠️ 행을 받아서 세지 않고 **`count=exact` 헤더**로 개수만 받는다
     *    (실측: `Content-Range: 0-0/1`). 친구가 많아도 본문이 한 행이라
     *    닉네임·uuid를 필요 없이 내려받지 않는다.
     */
    override suspend fun friendCount(): RankingResult<Int> {
        if (!configured) return RankingResult.NotConfigured
        val token = auth.accessToken() ?: return RankingResult.Failed(401)
        val url = "$baseUrl/rest/v1/friendships?select=requester_id&state=eq.$ACCEPTED"
        return requestCount(url, token)
    }

    /**
     * 🔴 **응답이 배열이 아니라 스칼라다.** `returns int` 함수라 PostgREST가
     *    `12`를 그대로 준다 — `[{"count":12}]`가 아니다. [rpc]를 쓰면
     *    `JSONArray("12")`가 던지고 [PARSE_FAILED]가 되므로 직접 읽는다.
     *
     * ⚠️ **`toIntOrNull()`이다.** 형식이 바뀌면 실패로 남겨야 한다 —
     *    `toInt()`는 던지고, 0으로 두면 위 인터페이스 주석의 사고가 된다.
     */
    override suspend fun dongMemberCount(dongCode: String): RankingResult<Int> {
        if (!configured) return RankingResult.NotConfigured
        val token = auth.accessToken() ?: return RankingResult.Failed(401)
        val args = JSONObject().put("target_dong", dongCode)
        return request("$baseUrl/rest/v1/rpc/dong_member_count", args.toString(), token) { body ->
            body.trim().toIntOrNull()
        }
    }

    /**
     * 🔴 **서버가 준 `rank`를 그대로 쓴다. 다시 세지 않는다.**
     *
     * 다시 세면 두 가지가 깨진다: ① 상위 5명만 받아 오는 페이지에서는 **내 순위를
     * 알 수 없다**(6위인 사람이 1위가 된다) ② 동점 타이브레이크가 서버 SQL과
     * 어긋나면 **같은 데이터에 두 순위**가 생긴다.
     *
     * ⚠️ `reachedAt`·`totalDiscoveries`는 **서버가 주지 않는다**(집계에만 쓰고
     *    내보내지 않는다 — 0002 5절 "내용은 한 줄도 내보내지 않는다").
     *    기본값 0으로 남는데, **그 값으로 정렬하면 안 되는 이유가 여기 있다.**
     */
    private fun rankedRow(o: JSONObject, myId: String? = null): RankedEntry = RankedEntry(
        rank = o.getInt("rank"),
        entry = RankEntry(
            userId = o.getString("user_id"),
            nickname = o.getString("nickname"),
            speciesCount = o.getInt("species_count"),
            // 대표 꽃이 없을 수 있다(발견이 있으면 항상 있지만 left join이다).
            signatureFlowerId = if (o.isNull("top_flower_id")) 0 else o.getInt("top_flower_id"),
            // 🔴 서버가 준 `is_me`가 **먼저**다(친구 랭킹). 없으면 uuid로 맞춘다
            //    (지역 랭킹 — [regionRanking]의 🔴). 대소문자를 무시한다:
            //    uuid는 같은 값이 대문자로 와도 같은 사람인데, 그때 `==`는 조용히 false다.
            isMe = o.optBoolean("is_me", false) ||
                (myId != null && o.getString("user_id").equals(myId, ignoreCase = true)),
        ),
        // 지난 시즌 대비 변동은 **서버가 주지 않는다.** null이면 화면이 `▲`를 안 그린다.
        delta = null,
    )

    private suspend fun <T> rpc(
        name: String,
        args: JSONObject,
        parse: (List<JSONObject>) -> T?,
    ): RankingResult<T> {
        if (!configured) return RankingResult.NotConfigured
        val token = auth.accessToken() ?: return RankingResult.Failed(401)
        return request("$baseUrl/rest/v1/rpc/$name", args.toString(), token) { body ->
            val arr = JSONArray(body)
            parse((0 until arr.length()).map { arr.getJSONObject(it) })
        }
    }

    /**
     * 한 번 보내고, 401이면 갱신해서 한 번 더. [DiscoveryUploader]와 같은 이유다.
     */
    private suspend fun <T> request(
        url: String,
        body: String?,
        token: String,
        countOnly: Boolean = false,
        parse: (String) -> T?,
    ): RankingResult<T> {
        val first = send(url, body, token, countOnly)
        val res = if (first.code == 401) {
            val fresh = auth.refresh() ?: return RankingResult.Failed(401)
            send(url, body, fresh, countOnly)
        } else {
            first
        }
        val code = res.code
        val text = if (countOnly) res.contentRange else res.body
        if (code !in 200..299) {
            val pgCode = runCatching { JSONObject(res.body).optString("code") }.getOrDefault("")
            // 실측한 실패 형태를 로그로 구분한다:
            //   `P0001` = assert_self (남의 uuid나 anon 키로 불렀다 · HTTP 400)
            //   `PGRST202` = 함수·인자 이름이 틀렸다 (HTTP 404 · 배포 안 됨)
            //   `PGRST301` = JWT가 깨졌다 (HTTP 401)
            log("랭킹 조회 실패 · HTTP $code${if (pgCode.isEmpty()) "" else " · $pgCode"}")
            return RankingResult.Failed(code, pgCode)
        }
        val parsed = runCatching { parse(text) }.getOrElse { e ->
            // 🔴 **형식이 바뀐 것을 빈 랭킹으로 보여주면 안 된다.** 화면이 조용히
            //    "이웃이 없어요"가 되고, 서버 함수가 바뀐 사실은 아무도 모른다.
            log("랭킹 응답을 읽을 수 없다 · ${e.javaClass.simpleName}")
            null
        } ?: return RankingResult.Failed(code, PARSE_FAILED)
        return RankingResult.Loaded(parsed)
    }

    private suspend fun send(
        url: String,
        body: String?,
        token: String,
        countOnly: Boolean = false,
    ): Transport.Response =
        runCatching { transport.send(url, anonKey, token, body, countOnly) }.getOrElse { e ->
            if (e is CancellationException) throw e
            Transport.Response(0, "")
        }

    /**
     * `Content-Range: 0-0/1`의 **`/` 뒤 숫자**를 읽는다.
     *
     * 🔴 **본문 행을 세지 않는다.** `Range: 0-0`을 붙였으므로 본문에는 행이 최대
     *    한 개뿐이다 — 세면 친구가 8명이어도 **1명**이 된다. 그리고 아무 오류도 안 난다.
     *
     * ⚠️ 헤더가 없거나 `*`이면(총계를 안 준 것) **개수를 0으로 만들지 않는다.**
     *    0은 "친구가 없다"는 뜻이고 화면 18은 그걸로 초대 화면을 띄운다 —
     *    친구가 있는 사용자에게 `아직 겨룰 친구가 없어요`를 보여주는 사고가 된다.
     */
    private fun parseTotal(contentRange: String): Int? =
        contentRange.substringAfter('/', "").trim().toIntOrNull()

    private suspend fun requestCount(url: String, token: String): RankingResult<Int> =
        request(url, null, token, countOnly = true) { range -> parseTotal(range) }

    /** 전역이 아니라 주입된 값으로 판단한다 — [DiscoveryUploader]와 같은 이유. */
    private val configured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    internal object HttpTransport : Transport {
        override suspend fun send(
            url: String,
            apiKey: String,
            bearer: String,
            body: String?,
            countOnly: Boolean,
        ): Transport.Response = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = if (body == null) "GET" else "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                // ⚠️ anon 키가 아니라 사용자 토큰이다. anon으로 부르면 `assert_self`가
                //    **HTTP 400 `P0001`로 거부한다**(실측) — RLS 42501이 아니다.
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                if (countOnly) {
                    // 실측: 이 둘을 같이 줘야 `Content-Range: 0-0/1`이 온다.
                    setRequestProperty("Prefer", "count=exact")
                    setRequestProperty("Range", "0-0")
                }
                if (body != null) doOutput = true
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Transport.Response(
                    code = code,
                    body = stream?.bufferedReader()?.use { it.readText() } ?: "",
                    contentRange = conn.getHeaderField("Content-Range").orEmpty(),
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        /** 응답을 읽을 수 없을 때의 표시. HTTP 코드로는 구분할 수 없다. */
        const val PARSE_FAILED = "PARSE"
        private const val TIMEOUT_MS = 15_000

        /**
         * `friend_state` enum의 값. **공유계약 enum이라 여기서 문자열을 새로 쓰지 않는다** —
         * [com.catchflower.app.core.FriendState]가 원본이다.
         */
        private val ACCEPTED = FriendState.ACCEPTED.wire
    }
}
