package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 화면 02가 목록에 올리는 동네 하나.
 *
 * 🔴 **[dongCode]는 반드시 행정동 코드다. 섞이면 같은 동네가 두 동네가 된다.**
 *
 *    서버 `region_ranking`은 **사용자끼리 코드를 문자열로 맞춰 본다**
 *    (0002 `scoped` CTE: `u.dong_code = (select dong_code from me)`) — 코드가 어떤
 *    체계인지는 보지 않는다. 그래서 한 사람이 검색으로 법정동 `1144012400`을,
 *    다른 사람이 `현재 위치로 찾기`로 행정동 `1144071000`을 저장하면 **둘 다 연남동인데
 *    서로의 랭킹에 절대 안 나온다.** 양쪽 다 저장은 200이고, 화면 20은 `연남동`을
 *    잘 보여준다 — 그리고 **6개월간 바꿀 수 없다.**
 *
 *    `현재 위치로 찾기`([RegionSearchSource.byCoordinate])와 촬영 기록
 *    ([KakaoPlaceService.regionCode])은 **둘 다 `region_type == "H"`**, 즉 행정동이다.
 *    그래서 검색 경로도 행정동으로 맞춘다 — **다수가 아니라 기존 값에 맞추는 것이다.**
 */
data class RegionCandidate(
    /** 화면 20·17이 쓰는 전체 이름. 예 `서울특별시 마포구 연남동`. */
    val regionName: String,
    /** 행정동 코드 10자리. 예 `1144071000`. */
    val dongCode: String,
    /** 시군구 코드 5자리. **B-6이 여기 의존한다.** */
    val guCode: String,
) {
    /** 목록 항목·CTA `{동명}으로 시작하기`에 쓰는 마지막 어절. */
    val dongName: String get() = regionName.trim().substringAfterLast(' ')
}

/**
 * 화면 02 동네 검색 결과.
 *
 * ⚠️ **빈 목록으로 실패를 표현하지 않는다.** [RankingResult]와 같은 이유다 —
 *    화면 02의 `검색 결과가 없어요`는 **정상 상태이기도 하다**(오타를 쳤다).
 *    네트워크 실패를 같은 값으로 만들면 사용자는 자기 동네 이름을 의심하고,
 *    실제로는 와이파이가 끊긴 것이다.
 */
sealed interface RegionSearchResult {
    data class Loaded(val candidates: List<RegionCandidate>) : RegionSearchResult

    /** 카카오를 못 불렀다. **`검색 결과가 없어요`로 보여주면 안 된다.** */
    data object Failed : RegionSearchResult

    /** 키 없는 빌드. 오류 문구도 띄우지 않는다 — 사용자 탓이 아니다. */
    data object NotConfigured : RegionSearchResult
}

/** 동 이름 → 행정동 후보. 화면 02가 쓴다. */
interface RegionSearchSource {
    suspend fun search(query: String): RegionSearchResult

    /** 화면 02 `현재 위치로 우리 동네 찾기`. 좌표 → 행정동 하나. */
    suspend fun byCoordinate(lat: Double, lng: Double): RegionSearchResult
}

/**
 * 카카오 주소 검색 구현체.
 *
 * ## 🔴 실측으로 드러난 함정 세 개. 전부 조용히 틀린다.
 *
 * ### ① 주소 검색은 **법정동을 섞어서 준다.** 그걸 그대로 저장하면 랭킹이 죽는다.
 *
 * | 검색어 | 응답에 들어오는 것 |
 * |---|---|
 * | `연남동` | 행정동 1개 (`h_code 1144071000`) |
 * | `봉천동` | **법정동 1개뿐** (`h_code`가 **빈 문자열**) |
 * | `성수동1가` | **법정동 1개뿐** |
 * | `역삼` | 법정동 `역삼동` + 행정동 `역삼1동`·`역삼2동` **섞여서** |
 *
 * `h_code`가 없는 행의 `b_code`를 `dong_code`로 쓰면 **체계가 다른 코드**가 들어간다.
 * 서버는 문자열만 맞춰 보므로 같은 동네가 두 동네로 갈린다 — 오류 0건.
 * 그래서 [isAdminDong]으로 **행정동만** 남긴다.
 *
 * ⚠️ **이 함정은 이미 실현돼 있었다.** 실측으로 `public_profiles`를 조회해 보니 내가
 *    만든 테스트 계정 3개에 `dong_code = 1144012400`(연남동 **법정동**)이 들어 있고,
 *    한 개는 `1120052000`에 `region_name = '성동구 성수동1가'`(시도가 빠진 이름)였다.
 *    화면 02 없이 손으로 PATCH해서 넣은 값들이다 — **화면이 없는 동안에도 잘못된
 *    형식이 들어갈 수 있다는 증거**이고, 그래서 0004에 서버 제약을 같이 넣었다.
 *
 * ### ② 걸러내면 **자기 동네가 사라지는 검색어가 있다.**
 *
 * `봉천동`·`성수동1가`는 걸러내면 결과가 **0개**가 된다. 그런데 그게 사용자가 자기
 * 동네를 부르는 이름이다. 여기서 `검색 결과가 없어요`를 띄우면 **자기 동네가 없는 앱**이다.
 * 그래서 0개가 되면 법정동 좌표를 [KakaoPlaceService]와 **같은 엔드포인트**로 되짚어
 * 행정동을 찾는다(실측: `봉천동` → `관악구 중앙동`, `성수동1가` → `성수1가1동`).
 *
 * ⚠️ **이름이 바뀌는 것을 숨기지 않는다.** 되짚은 결과는 사용자가 친 이름과 다르다.
 *    화면 02는 이 항목도 **전체 이름을 그대로 보여주고 사용자가 눌러서 고르게** 한다 —
 *    자동으로 확정하면 `봉천동`을 치고 `중앙동` 주민이 되는데, **6개월간 못 바꾼다.**
 *
 * ### ③ 구·시를 검색하면 **고른 적 없는 동네가 배정된다.**
 *
 * 실측: `마포구` 좌표 되짚기 → `성산2동`. `제주` → `제주시 연동`. `서울` → 어딘가의 동.
 * 구 대표 좌표가 우연히 걸친 동이다. **이건 되짚기를 하면 안 되는 경우**이므로
 * [isAdminDong]이 먼저 걸러내고(구·시는 `h_code`는 있지만 `region_3depth_h_name`이
 * 비어 있다 — 실측), 되짚기는 **법정동 행에만** 한다.
 *
 * ## 이름은 검색 응답이 아니라 되짚기 값을 쓴다
 *
 * 실측: 검색은 `서울 마포구 연남동`, 되짚기는 `서울특별시 마포구 연남동`.
 * A 문서 화면 02·20이 **후자** 형식이다(`서울특별시 마포구 연남동`). 검색 값을 쓰면
 * 화면 20이 A 문서와 다른 이름을 보여준다. `1depth_name`만 다르므로 조립한다 —
 * **되짚기를 한 번 더 부르지 않는다**(쿼터).
 *
 * ⚠️ 코드는 검색 값을 믿어도 된다. **검색 `h_code`와 되짚은 `H.code`가 16/16 일치**했다.
 */
class KakaoRegionSearchService(
    private val apiKey: String = AppSecrets.kakaoRestApiKey,
    private val transport: Transport = HttpTransport,
    /**
     * ⚠️ **`android.util.Log`를 직접 부르면 실패 경로를 JVM에서 잴 수 없다.**
     *    android.jar의 `Log`는 껍데기라 테스트에서 던지고, 그걸 덮는
     *    `unitTests.isReturnDefaultValues = true`는 이 프로젝트가 거부한 설정이다
     *    (`app/build.gradle.kts` 주석). 그런데 이 클래스에서 **로그를 지나는 경로가
     *    곧 함정 경로다** — 403(콘솔 토글 꺼짐)·응답 형식 변경·IO 실패.
     *    그래서 [RankingService]·[RegionUpdateService]와 같은 모양으로 뺀다.
     */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : RegionSearchSource {

    /** [KakaoPlaceService.Transport]와 같은 모양. 테스트가 네트워크를 부르면 안 된다. */
    interface Transport {
        /** @return HTTP 상태 코드와 본문 */
        suspend fun get(url: String, authorization: String): Pair<Int, String>
    }

    override suspend fun search(query: String): RegionSearchResult {
        if (apiKey.isEmpty()) return RegionSearchResult.NotConfigured
        val q = query.trim()
        // 한 글자로는 못 찾는다(실측: `동` → 0개). 부르지 않고 빈 결과를 준다 —
        // 화면이 타이핑 중에 쿼터를 태우는 것을 막는다.
        if (q.length < MIN_QUERY_LENGTH) return RegionSearchResult.Loaded(emptyList())

        val body = request(
            "$ADDRESS_URL?query=${URLEncoder.encode(q, "UTF-8")}&size=$PAGE_SIZE"
        ) ?: return RegionSearchResult.Failed

        val docs = documents(body) ?: return RegionSearchResult.Failed
        val admin = docs.mapNotNull { candidate(it) }
        if (admin.isNotEmpty()) return RegionSearchResult.Loaded(admin.distinctBy { it.dongCode })

        // ②번 함정: 행정동이 0개다. 법정동 좌표를 되짚어 본다.
        //
        // ⚠️ **법정동 행만 되짚는다.** 구·시 행을 되짚으면 ③번 함정이 된다.
        //    `size`를 제한한다 — 되짚기는 행마다 한 번씩 호출이고 쿼터가 있다.
        val fallback = docs
            .filter { isLegalDong(it) }
            .take(FALLBACK_LOOKUP_LIMIT)
            .mapNotNull { byCoordinateOf(it) }
        return RegionSearchResult.Loaded(fallback.distinctBy { it.dongCode })
    }

    override suspend fun byCoordinate(lat: Double, lng: Double): RegionSearchResult {
        if (apiKey.isEmpty()) return RegionSearchResult.NotConfigured
        val found = regionByCoordinate(lat, lng) ?: return RegionSearchResult.Failed
        return RegionSearchResult.Loaded(listOf(found))
    }

    /** 법정동 행 하나의 좌표를 되짚는다. 실패하면 그 행만 버린다. */
    private suspend fun byCoordinateOf(doc: JSONObject): RegionCandidate? {
        val x = doc.stringOrNull("x")?.toDoubleOrNull() ?: return null
        val y = doc.stringOrNull("y")?.toDoubleOrNull() ?: return null
        return regionByCoordinate(lat = y, lng = x)
    }

    /**
     * 좌표 → 행정동.
     *
     * ⚠️ **[KakaoPlaceService.regionCode]와 같은 엔드포인트·같은 `H` 선택 규칙이다.**
     *    여기서 규칙이 갈리면 사용자가 고른 동네와 찍은 기록의 동네가 서로 다른
     *    체계가 된다. 합치지 않은 이유는 그 클래스가 **캐시와 장소명**까지 안고 있어서,
     *    화면 02가 쓰면 촬영용 캐시에 검색 좌표가 섞인다(B-5 판정과 같은 캐시다).
     */
    private suspend fun regionByCoordinate(lat: Double, lng: Double): RegionCandidate? {
        val body = request("$COORD_URL?x=$lng&y=$lat") ?: return null
        val docs = documents(body) ?: return null
        // `H`는 행정동, `B`는 법정동. **행정동 코드가 랭킹 집계 단위다.**
        val h = docs.firstOrNull { it.stringOrNull("region_type") == TYPE_ADMIN } ?: return null
        val code = h.stringOrNull("code") ?: return null
        val name = h.stringOrNull("address_name") ?: return null
        return RegionCandidate(regionName = name, dongCode = code, guCode = guOf(code) ?: return null)
    }

    /**
     * 주소 검색 행 하나 → 후보. **행정동이 아니면 null이다.**
     *
     * 이름은 되짚기 형식(`서울특별시 …`)으로 조립한다 — 위 클래스 주석 참고.
     */
    private fun candidate(doc: JSONObject): RegionCandidate? {
        if (!isAdminDong(doc)) return null
        val a = doc.optJSONObject("address") ?: return null
        val code = a.stringOrNull("h_code") ?: return null
        val gu = guOf(code) ?: return null
        // 🔴 `region_3depth_h_name`이다. `region_3depth_name`은 **법정동 이름**이고
        //    실측으로 행정동 행에서는 비어 있다(`역삼1동` 행의 3depth는 `""`).
        val dong = a.stringOrNull("region_3depth_h_name") ?: return null
        val depth1 = a.stringOrNull("region_1depth_name") ?: return null
        val depth2 = a.stringOrNull("region_2depth_name").orEmpty()
        val full = listOf(fullSidoName(depth1), depth2, dong)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return RegionCandidate(regionName = full, dongCode = code, guCode = gu)
    }

    private suspend fun request(url: String): String? {
        val (status, body) = try {
            transport.get(url, "KakaoAK $apiKey")
        } catch (e: CancellationException) {
            throw e // 삼키면 검색 취소가 안 먹는다
        } catch (e: IOException) {
            log("동네 검색 호출 실패 · ${e.javaClass.simpleName}")
            return null
        }
        if (status != 200) {
            // 403 `disabled OPEN_MAP_AND_LOCAL service`는 콘솔 토글이 꺼진 것이다.
            // 키가 틀린 것과 구분되게 본문을 남긴다 — [KakaoPlaceService]와 같은 이유.
            log("동네 검색 HTTP $status · $body")
            return null
        }
        return body
    }

    private fun documents(body: String): List<JSONObject>? = try {
        val arr = JSONObject(body).optJSONArray("documents") ?: return null
        (0 until arr.length()).map { arr.getJSONObject(it) }
    } catch (e: JSONException) {
        log("동네 검색 응답을 읽을 수 없다 · ${e.message}")
        null
    }

    internal companion object {
        private const val ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json"
        private const val COORD_URL =
            "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"

        /** 행정동. [KakaoPlaceService]와 같은 값을 본다. */
        private const val TYPE_ADMIN = "H"

        /** 실측: `동` 한 글자는 0개다. 타이핑 중 호출을 막는다. */
        const val MIN_QUERY_LENGTH = 2

        /** `역삼`처럼 동이 여럿인 검색어가 있다(실측 3개). 넉넉히 받고 걸러낸다. */
        private const val PAGE_SIZE = 15

        /**
         * ②번 되짚기 호출 상한.
         *
         * ⚠️ **행마다 한 번씩 호출이다.** 상한이 없으면 검색어 하나가 15번을 태운다.
         *    법정동만 나오는 검색어는 실측에서 결과가 1~2개였으므로 3이면 충분하다.
         */
        private const val FALLBACK_LOOKUP_LIMIT = 3

        /**
         * **행정동으로 확정된 행인가.**
         *
         * 실측표(위 클래스 주석 ①·③):
         *
         * | 행 | `h_code` | `region_3depth_h_name` | 판정 |
         * |---|---|---|---|
         * | `서울 마포구 연남동` | 있음 | `연남동` | ✅ 행정동 |
         * | `서울 강남구 역삼1동` | 있음 | `역삼1동` | ✅ 행정동 |
         * | `서울 관악구 봉천동` | **빈 문자열** | 빈 문자열 | ⛔ 법정동 |
         * | `서울 마포구`(구) | 있음 | **빈 문자열** | ⛔ 구 |
         * | `제주특별자치도`(도) | 있음 | 빈 문자열 | ⛔ 도 |
         *
         * 🔴 **`h_code`만 보면 구·도가 통과한다** — 구도 `h_code`를 갖는다
         *    (`마포구` = `1144000000`). 그러면 `마포구`를 검색한 사용자에게
         *    구 코드가 `dong_code`로 저장되고, 랭킹은 조용히 빈다.
         *    `region_3depth_h_name`을 같이 봐야 **동까지 내려온 행**만 남는다.
         */
        fun isAdminDong(doc: JSONObject): Boolean {
            val a = doc.optJSONObject("address") ?: return false
            return a.stringOrNull("h_code") != null &&
                a.stringOrNull("region_3depth_h_name") != null
        }

        /** 법정동 행인가. 되짚기 대상은 **이것만**이다(③번 함정). */
        fun isLegalDong(doc: JSONObject): Boolean {
            val a = doc.optJSONObject("address") ?: return false
            return a.stringOrNull("h_code") == null &&
                a.stringOrNull("b_code") != null &&
                a.stringOrNull("region_3depth_name") != null
        }

        /**
         * 구 코드는 행정동 코드 **앞 5자리**다(예 `1144071000` → `11440`).
         *
         * ⚠️ [KakaoPlaceService]가 같은 규칙을 쓴다. 갈리면 B-6 구 확장이
         *    사용자마다 다른 단위로 묶인다.
         */
        fun guOf(dongCode: String): String? =
            dongCode.takeIf { it.length >= GU_CODE_LENGTH }?.substring(0, GU_CODE_LENGTH)

        private const val GU_CODE_LENGTH = 5

        /**
         * 검색 응답의 축약 시도명을 **되짚기와 같은 정식 이름**으로 펼친다.
         *
         * 실측: 검색은 `서울`, 되짚기는 `서울특별시`. A 문서 화면 02·20이 정식 이름
         * 형식(`서울특별시 마포구 연남동`)이라 검색 값을 그대로 쓰면 두 화면이 다른
         * 이름을 말한다.
         *
         * ⚠️ **모르는 이름은 그대로 돌려준다.** 표를 못 맞히는 것보다 `경남 양산시 …`가
         *    낫다 — 빈 문자열이나 예외로 만들면 **동네를 고를 수 없게** 된다.
         *    실측에서 검색이 이미 정식 이름을 주는 시도도 있었다(`제주특별자치도`·
         *    `전북특별자치도`) — 그건 표에 없으므로 그대로 통과한다.
         */
        fun fullSidoName(short: String): String = SIDO_FULL_NAMES[short] ?: short

        /** 실측으로 검색 응답이 축약해서 주는 시도 이름들. */
        private val SIDO_FULL_NAMES = mapOf(
            "서울" to "서울특별시",
            "부산" to "부산광역시",
            "대구" to "대구광역시",
            "인천" to "인천광역시",
            "광주" to "광주광역시",
            "대전" to "대전광역시",
            "울산" to "울산광역시",
            "세종" to "세종특별자치시",
            "경기" to "경기도",
            "충북" to "충청북도",
            "충남" to "충청남도",
            "경북" to "경상북도",
            "경남" to "경상남도",
            "강원" to "강원특별자치도",
            "전북" to "전북특별자치도",
            "전남" to "전라남도",
            "제주" to "제주특별자치도",
        )
    }

    private object HttpTransport : Transport {
        override suspend fun get(url: String, authorization: String): Pair<Int, String> =
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", authorization)
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                try {
                    val status = conn.responseCode
                    // ⚠️ 비2xx에서 `inputStream`을 읽으면 던진다 — 그러면 상태 코드
                    //    분기가 사라지고 전부 "실패"가 된다.
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    status to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
                } finally {
                    conn.disconnect()
                }
            }
    }
}
