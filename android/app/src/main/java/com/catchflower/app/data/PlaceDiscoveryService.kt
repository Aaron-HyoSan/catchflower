package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 화면 15(장소 상세)가 읽는 **남의 공개 기록**. 지도 핀을 눌러 `자세히 보기`로 들어온다.
 *
 * ## 🔵 새 서버 함수를 만들지 않았다 — **필요하다고 적어 둔 내 판단이 틀렸다**
 *
 * `ReactionService` 주석과 A 문서 화면 16 절에 *"남의 발견 기록을 주는 서버 함수가
 * 없어서 그릴 대상 자체가 없다"* 고 적어 두었고, 오너에게도 `0009`가 필요하다고
 * 보고했다. **둘 다 틀렸다.** 실측(2026-08-12 · 익명 계정 2개):
 *
 * ```
 * B 계정으로 GET /rest/v1/discoveries?visibility=eq.public&select=... → 200 · 7행
 *   전부 B가 쓰지 않은 기록이었다(작성자 7명)
 * (대조군) 태평양 bbox → 200 · 0행                      ← 필터가 실제로 돈다
 * (대조군) 필터 없이 전체 조회 → visibility 분포 {public: 15}
 *          ← **private이 한 건도 안 새어 나온다**
 * ```
 *
 * 즉 `0001`의 `discoveries_read` 정책이 이미 로그인한 사용자에게
 * `visibility <> 'private'`인 남의 기록을 준다. **필요한 것은 함수가 아니라 쿼리였다.**
 * 내가 "함수가 없다"고 단정한 것은 **정책을 안 읽고 지도 코드만 본 결과**다 —
 * 지도가 내 기록만 그리는 것은 권한 문제가 아니라 `MapPins`가 로컬 저장소를 읽기
 * 때문이었다. 안 필요한 마이그레이션을 만들면 **오너가 붙여넣을 것이 하나 더** 늘고,
 * 그건 되돌리기 어렵다.
 *
 * ## 🔴 그런데 REST가 **못 해 주는 것**이 하나 있다 — 그래서 집계를 앱에서 한다
 *
 * 화면 15의 지표 3칸(`꽃 종류 5종 · 기록 12개 · 이번 주 3개`)은 group by가 필요한데,
 * PostgREST는 그것을 거부한다(실측):
 *
 * ```
 * GET ...&select=place_name,count → 400 · 42803
 *   column "discoveries.place_name" must appear in the GROUP BY clause
 * ```
 *
 * → 그래서 **행을 받아 앱에서 센다.** 상자가 좁아서(반경 수백 m) 행 수가 적다.
 *
 * ⚠️ **그 대신 "센 값이 틀릴 수 있다"는 문제가 생긴다.** 페이지 상한보다 많으면
 *    받은 행만 세게 되고, 화면에는 **작은 숫자가 정상처럼** 뜬다. 그래서
 *    [PlaceDiscoveries.truncated]로 실어 보낸다 — 화면은 그때 숫자를 쓰지 않는다.
 *    총수는 `Prefer: count=exact`의 `Content-Range`로 따로 받는다(실측: `0-1/15`).
 */
interface PlaceDiscoverySource {

    /**
     * 좌표 주변의 **남의 공개 기록 + 내 기록**을 최신순으로 받는다.
     *
     * @param radiusMeters 상자의 반변 길이. 지도 핀 묶음(약 11m)보다 넓게 준다 —
     *   핀은 표시용 반올림이고 여기는 "이 장소"의 범위다.
     */
    suspend fun near(lat: Double, lng: Double, radiusMeters: Int): PlaceResult<PlaceDiscoveries>
}

/**
 * 조회 결과.
 *
 * ⚠️ [ReactionResult]를 재사용하지 않는다 — 그 타입에는 `Rejected(EMPTY_BODY·TOO_LONG)`가
 *    있고, 장소 조회에 "본문이 비었다"는 실패는 존재하지 않는다. 있을 수 없는 상태를
 *    표현할 수 있게 두면 `when`이 그 가지를 다뤄야 하고, 읽는 사람은 **댓글 코드에서
 *    왜 이게 나오는지** 찾는다(`ReactionResult` 주석의 그 이유).
 */
sealed interface PlaceResult<out T> {
    data class Loaded<T>(val value: T) : PlaceResult<T>

    /** 못 불렀다. 🔴 화면은 **`꽃 0종`이 아니라 오류**를 말한다(A 문서 화면 20·21 규칙). */
    data class Failed(val code: Int, val pgCode: String = "") : PlaceResult<Nothing>

    /** 키 없는 빌드. 문구도 띄우지 않는다 — 사용자 탓이 아니다. */
    data object NotConfigured : PlaceResult<Nothing>
}

/**
 * 한 장소의 기록 묶음. 화면 15의 지표 3칸과 두 목록이 여기서 나온다.
 *
 * ⚠️ **`speciesCount`를 필드로 두지 않고 계산한다.** [MapPin]과 같은 이유다 —
 *    필드로 두면 목록과 숫자가 어긋난 상태를 만들 수 있고, 그건 화면에서 안 보인다.
 */
data class PlaceDiscoveries(
    /** 최신순. 내 기록과 남의 기록이 섞여 있다. */
    val rows: List<Discovery>,
    /**
     * 🔴 **서버가 말한 전체 개수.** [rows]보다 클 수 있다.
     *
     * `Content-Range: 0-1/15`의 뒤 숫자다(실측). ⚠️ 헤더를 못 읽었으면 `null`이고,
     * 그때는 [recordCount]가 **받은 행 수**라서 실제보다 작을 수 있다.
     */
    val totalOnServer: Int?,
    /**
     * 🔴 **받은 행이 전부가 아닌가.** true면 아래 숫자들이 **실제보다 작다.**
     *
     * ⚠️ 화면은 이때 지표 3칸을 그리지 않는다. `꽃 3종`이라고 쓰면 5종인 장소가
     *    3종으로 보이고, **화면은 완벽히 정상으로 보인다** — A 문서가 `좋아요 0`을
     *    금지한 것과 같은 종류의 거짓말이다.
     */
    val truncated: Boolean,
) {
    /** `꽃 종류 5종`. 같은 종을 여러 번 찍어도 1이다. */
    val speciesCount: Int get() = rows.mapTo(HashSet()) { it.flowerId }.size

    /**
     * `기록 12개`.
     *
     * ⚠️ [totalOnServer]가 있으면 그것을 쓴다 — 받은 행이 잘렸을 때
     *    **서버가 아는 값이 더 정확하다.**
     */
    val recordCount: Int get() = totalOnServer ?: rows.size

    /** 최신순 꽃 id, 중복 없이. 화면 15 `이곳에서 발견된 꽃` 순서다. */
    val flowerIdsRecentFirst: List<Int>
        get() = rows.sortedByDescending { it.createdAt }.map { it.flowerId }.distinct()

    /**
     * `이번 주 3개`.
     *
     * 🔴 **잘린 목록에서는 이 값을 쓰지 않는다.** 최신순으로 잘리므로 이번 주 기록이
     *    먼저 오고, 그러면 이 숫자만은 우연히 맞을 수 있다 — 하지만 **"우연히 맞는
     *    값"을 화면에 쓰면 언제 틀리는지 아무도 모른다.** [truncated]면 화면이 판단한다.
     *
     * @param sinceMillis 이 시각 이후. 주 시작 계산은 화면 층의 일이다 —
     *   여기서 하면 시간대·주 시작 요일이 데이터 층에 숨는다.
     */
    fun countSince(sinceMillis: Long): Int = rows.count { it.createdAt >= sinceMillis }

    /**
     * 남이 쓴 기록만. 화면 15 `사람들의 기록` 목록이고, **화면 16의 입구**다.
     *
     * 🔴 **내 기록을 여기 섞지 않는다.** 좋아요·댓글·신고는 남의 기록에만 붙는
     *    기능인데(`likes_insert_self`는 되지만 자기 기록에 좋아요는 말이 안 된다),
     *    섞으면 사용자가 **자기 기록을 신고하는** 화면을 보게 된다.
     */
    fun othersRecords(myUserId: String?): List<Discovery> =
        rows.filter { myUserId == null || it.userId != myUserId }
}

/**
 * 좌표 상자 계산. **[PlaceDiscoveryService]에서 분리해 둔다** — 여기가 틀리면
 * 화면에는 "기록이 없는 장소"로 보이고, 그건 정상 화면과 구별이 안 된다.
 */
internal object BoundingBox {

    /** 위도 1도의 길이(m). 어디서나 거의 같다. */
    private const val METERS_PER_LAT_DEGREE = 111_320.0

    /**
     * 🔴 **경도 1도는 위도 1도보다 짧고, 그 차이가 위도마다 다르다.**
     *
     * 실측 계산(2026-08-12):
     * ```
     * 위도 33.5°(제주)   → 1° 경도 = 92.8km  (위도 대비 0.834)
     * 위도 37.5°(서울)   → 1° 경도 = 88.3km  (0.793)
     * 위도 38.6°(최북단) → 1° 경도 = 87.0km  (0.782)
     * ```
     * 즉 위도·경도에 **같은 delta**를 쓰면 상자가 정사각형이 아니고, 제주와 최북단에서
     * **실제 폭이 4% 다르다.** 그래서 경도 delta를 `cos(위도)`로 나눈다.
     *
     * ⚠️ 이걸 안 하면 증상이 **"제주에서만 옆 화단이 안 걸린다"** 인데, 화면은
     *    기록이 없는 장소와 똑같이 보인다. 눈으로 못 잡는 종류다.
     */
    fun of(lat: Double, lng: Double, radiusMeters: Int): Box {
        val dLat = radiusMeters / METERS_PER_LAT_DEGREE
        // ⚠️ 극지에서 cos가 0에 수렴해 폭이 무한이 된다. 한국에서는 안 나오지만,
        //    0으로 나누기를 코드에 남기지 않는다(좌표가 조작돼 들어올 수 있다).
        val shrink = max(cos(Math.toRadians(lat)), MIN_COS)
        val dLng = radiusMeters / (METERS_PER_LAT_DEGREE * shrink)
        return Box(
            minLat = lat - dLat,
            maxLat = lat + dLat,
            minLng = lng - dLng,
            maxLng = lng + dLng,
        )
    }

    /** 위도 ±85° 밖에서 폭이 폭발하는 것을 막는다. */
    private const val MIN_COS = 0.0871

    /**
     * ⚠️ **`lngToLatSpanRatio` 같은 편의 속성을 두지 않는다.** 처음에 넣었는데
     *    **읽는 사람이 0명**이었다 — 이 저장소가 결함으로 세는 종류다(`symptomless-ui-defects`).
     *    위도 보정이 실제로 도는지는 [PlaceDiscoveryServiceTest]가 네 칸에서 직접
     *    계산해 확인한다. 계산을 여기 두면 "테스트만 부르는 코드"가 본문에 남는다.
     */
    data class Box(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double,
    )
}

class PlaceDiscoveryService(
    private val auth: TokenSource,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ 주입한다 — `android.util.Log`는 JVM에서 던진다([ReactionService]와 같은 이유). */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : PlaceDiscoverySource {

    interface Transport {
        /**
         * @return 본문 + **`Content-Range` 헤더.** 🔴 헤더를 안 돌려주면 총 개수를 알
         *   방법이 없고, 그러면 잘린 목록을 전부로 착각한다.
         */
        suspend fun get(url: String, apiKey: String, bearer: String): Response

        data class Response(val code: Int, val body: String, val contentRange: String?)
    }

    override suspend fun near(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
    ): PlaceResult<PlaceDiscoveries> {
        if (baseUrl.isEmpty() || anonKey.isEmpty()) return PlaceResult.NotConfigured
        val token = auth.accessToken() ?: return PlaceResult.Failed(401)

        val url = query(baseUrl, BoundingBox.of(lat, lng, radiusMeters))
        val first = send(url, token)
        val res = if (first.code == 401) {
            val fresh = auth.refresh() ?: return PlaceResult.Failed(401)
            send(url, fresh)
        } else {
            first
        }

        if (res.code !in 200..299) {
            // `42803` = group by 위반(집계를 서버에 맡기려 한 것) · `42501` = RLS 거절
            val pg = runCatching { org.json.JSONObject(res.body).optString("code") }
                .getOrDefault("")
            log("장소 기록 조회 실패 · HTTP ${res.code}${if (pg.isEmpty()) "" else " · $pg"}")
            return PlaceResult.Failed(res.code, pg)
        }

        val rows = runCatching { parse(res.body) }.getOrElse { e ->
            // 🔴 형식이 바뀐 것을 **빈 목록으로 만들지 않는다.** 그러면 화면이
            //    "이 장소에 기록이 없다"가 되고 서버가 바뀐 사실은 아무도 모른다.
            log("장소 기록 응답을 읽을 수 없다 · ${e.javaClass.simpleName}")
            return PlaceResult.Failed(res.code, PARSE_FAILED)
        }

        val total = totalFrom(res.contentRange)
        return PlaceResult.Loaded(
            PlaceDiscoveries(
                rows = rows,
                totalOnServer = total,
                // 🔴 두 가지를 다 본다. 페이지가 꽉 찼으면 잘렸을 수 있고,
                //    총수가 받은 행보다 크면 **확실히** 잘렸다.
                truncated = rows.size >= PAGE_SIZE || (total != null && total > rows.size),
            ),
        )
    }

    private suspend fun send(url: String, token: String): Transport.Response =
        runCatching { transport.get(url, anonKey, token) }.getOrElse { e ->
            if (e is CancellationException) throw e
            Transport.Response(0, "", null)
        }

    companion object {
        /** 응답을 읽을 수 없을 때. HTTP 코드로는 구분이 안 된다. */
        const val PARSE_FAILED = "PARSE"

        /**
         * 한 번에 받는 최대 행 수.
         *
         * ⚠️ **이 값을 올려서 `truncated`를 없애려 하지 않는다.** 상한이 얼마든
         *    넘는 장소는 존재하고, 그때 조용히 틀린 숫자를 쓰는 것이 진짜 문제다.
         *    상한은 트래픽을 위한 것이고, 정확함은 [PlaceDiscoveries.truncated]가 지킨다.
         */
        internal const val PAGE_SIZE = 200

        private const val TIMEOUT_MS = 15_000

        /**
         * 화면 15의 조회 주소.
         *
         * 🔴 **`visibility=neq.private`를 빼면 안 된다.** RLS가 막아 주므로 결과는
         *    같지만(실측: 필터 없이 조회해도 `{public: 15}`뿐이었다), **내 비공개
         *    기록이 목록에 섞인다** — 화면 15는 "사람들의 기록"이고 거기에 내가
         *    남에게 안 보이려고 숨긴 기록이 뜨면 공개된 줄 오해한다.
         *
         * ⚠️ **`select`에 `user_id`를 반드시 넣는다.** 없으면
         *    [PlaceDiscoveries.othersRecords]가 내 것을 걸러낼 수 없고, 그러면
         *    사용자가 **자기 기록에 신고 버튼이 있는 화면**을 본다.
         */
        internal fun query(baseUrl: String, box: BoundingBox.Box): String =
            "$baseUrl/rest/v1/discoveries" +
                "?select=id,user_id,flower_id,photo_url,lat,lng,place_name," +
                "dong_code,gu_code,visibility,ai_confidence,ai_picked_rank," +
                "is_first_discovery,note,created_at,captured_at" +
                "&visibility=neq.private" +
                "&lat=gte.${box.minLat}&lat=lte.${box.maxLat}" +
                "&lng=gte.${box.minLng}&lng=lte.${box.maxLng}" +
                "&order=created_at.desc" +
                "&limit=$PAGE_SIZE"

        /**
         * `Content-Range: 0-1/15` → `15`. 실측한 형식이다.
         *
         * ⚠️ 총수를 모를 때는 슬래시 뒤가 `*` 하나로 온다(실측 — `Prefer` 헤더를 안
         *    보내면 그렇다). `*`를 0으로 읽으면 **기록이 있는 장소가 `기록 0개`로
         *    뜬다.** null을 준다.
         *
         * ⚠️ **주석에 `0-1/`와 별표를 붙여 쓰지 않는다.** 코틀린 블록 주석은
         *    **중첩**되므로 KDoc 안의 슬래시+별표가 주석을 한 겹 더 열고, 그러면
         *    이 companion 전체가 주석으로 먹혀 `Unresolved reference`가 9개 났다
         *    (실제로 그렇게 컴파일이 깨졌다 — 원인이 279줄 아래에서 보고됐다).
         */
        internal fun totalFrom(header: String?): Int? {
            val slash = header?.substringAfterLast('/', "") ?: return null
            return slash.toIntOrNull()
        }

        internal fun parse(body: String): List<Discovery> {
            val arr = JSONArray(body)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Discovery(
                    id = o.getString("id"),
                    userId = o.getString("user_id"),
                    flowerId = o.getInt("flower_id"),
                    // ⚠️ `stringOrNull`이다. `optString`은 기기에서 JSON null을
                    //    **`"null"` 문자열**로 준다(`JsonNull` 주석의 그 사고).
                    photoUrl = o.stringOrNull("photo_url"),
                    lat = if (o.isNull("lat")) null else o.getDouble("lat"),
                    lng = if (o.isNull("lng")) null else o.getDouble("lng"),
                    placeName = o.stringOrNull("place_name"),
                    dongCode = o.stringOrNull("dong_code"),
                    guCode = o.stringOrNull("gu_code"),
                    visibility = Visibility.fromWire(o.getString("visibility")),
                    aiConfidence = o.getDouble("ai_confidence").toFloat(),
                    aiPickedRank = o.getInt("ai_picked_rank"),
                    isFirstDiscovery = o.getBoolean("is_first_discovery"),
                    note = o.stringOrNull("note"),
                    createdAt = DiscoveryStore.decodeTime(o.getString("created_at")),
                    capturedAt = DiscoveryStore.decodeTime(o.getString("captured_at")),
                )
            }
        }
    }

    internal object HttpTransport : Transport {
        override suspend fun get(
            url: String,
            apiKey: String,
            bearer: String,
        ): Transport.Response = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                // 🔴 **이 헤더가 총 개수를 만든다.** 없으면 `Content-Range`의 총수 자리가
                //    별표로 오고, 잘린 목록을 전부로 착각한다(실측으로 둘을 비교했다).
                setRequestProperty("Prefer", "count=exact")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                Transport.Response(
                    code = code,
                    body = stream?.bufferedReader()?.use { it.readText() } ?: "",
                    contentRange = conn.getHeaderField("Content-Range"),
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
