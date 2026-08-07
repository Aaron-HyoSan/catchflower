package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 좌표를 사람이 읽는 이름과 행정구역으로 바꾼 결과. iOS `PlaceInfo`와 같은 모양이다.
 */
data class PlaceInfo(
    /** 로컬 검색 결과. 예 `서울숲`. 주변에 이름난 장소가 없으면 null. */
    val placeName: String? = null,
    /** 행정동 이름. 예 `연남동`. 화면 표시용. */
    val dongName: String? = null,
    /** 행정동 코드. */
    val dongCode: String? = null,
    /** 시군구 코드. **⚠️ B-6이 여기 의존한다.** */
    val guCode: String? = null,
)

/**
 * 좌표 → 장소 이름과 행정구역. 카카오 로컬 API를 쓴다 (A-3 확정).
 *
 * 인터페이스로 둔 이유는 [FlowerRecognizer]와 같다 — 벤더를 뒤에 숨기고,
 * 키가 없는 환경(테스트·위치 거부)에서 [NoPlaceService]로 갈아 끼운다.
 */
interface PlaceService {
    suspend fun place(lat: Double, lng: Double): PlaceInfo?
}

/** 위치 기능이 꺼진 환경에서 쓴다. 키가 없어도 앱이 죽지 않게 하는 쪽이다. */
object NoPlaceService : PlaceService {
    override suspend fun place(lat: Double, lng: Double): PlaceInfo? = null
}

/**
 * 카카오 로컬 구현체. iOS `KakaoPlaceService`와 같은 두 엔드포인트를 쓴다.
 *
 * **왜 캐시가 필수인가.** 카카오는 일일 쿼터가 있다(무료 키). 촬영마다 조회하면
 * 같은 자리를 반복 조회해서 한도를 태운다 — 맛집지도에서 겪은 것과 같다.
 * 좌표를 [PlaceKey]로 반올림해 캐시한다 — **B-5의 "같은 장소" 판정과 같은 함수다.**
 * 기준이 갈리면 "같은 장소인데 장소명이 다른" 기록이 생긴다.
 *
 * ⚠️ `GamePolicy.GEOCODE_CACHE_COORD_DECIMALS`는 지금까지 **호출처가 0건이던 상수다**
 *    (`구현현황_AOS.md` 4절). 정책 파일에 숫자만 있는 것은 구현이 아니다.
 *
 * **행정구역과 장소명을 병렬로 부른다.** 순차로 하면 촬영 후 대기가 두 배가 된다.
 * 행정구역이 더 중요하다 — 랭킹(B-6)이 여기 의존하고 장소명은 표시용이다.
 */
class KakaoPlaceService(
    private val apiKey: String = AppSecrets.kakaoRestApiKey,
    private val transport: Transport = HttpTransport(),
) : PlaceService {

    /** 테스트가 실제 호출 없이 응답을 넣을 수 있게 뚫어 둔다. */
    interface Transport {
        /** @return HTTP 상태 코드와 본문 */
        suspend fun get(url: String, authorization: String): Pair<Int, String>
    }

    private val cache = HashMap<String, PlaceInfo>()
    private val cacheLock = Mutex()

    override suspend fun place(lat: Double, lng: Double): PlaceInfo? {
        if (apiKey.isEmpty()) return null
        val key = cacheKey(lat, lng)
        cacheLock.withLock { cache[key] }?.let { return it }

        val info = coroutineScope {
            val region = async { regionCode(lat, lng) }
            val name = async { nearbyPlaceName(lat, lng) }
            val base = region.await() ?: PlaceInfo()
            base.copy(placeName = name.await())
        }

        // **둘 다 실패했으면 캐시하지 않는다.** 일시적 네트워크 오류를 캐시하면
        // 그 자리는 앱을 다시 켤 때까지 영구히 "장소 없음"이 된다.
        if (info.dongCode == null && info.placeName == null) return null
        cacheLock.withLock { cache[key] = info }
        return info
    }

    /** 좌표 → 행정동·시군구 코드. */
    private suspend fun regionCode(lat: Double, lng: Double): PlaceInfo? {
        val body = request(
            "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=$lng&y=$lat"
        ) ?: return null
        return try {
            val docs = JSONObject(body).optJSONArray("documents") ?: return null
            // `H`는 행정동, `B`는 법정동이다. 사용자에게 익숙한 쪽은 행정동이고,
            // **행정동 코드가 랭킹 집계 단위다.**
            var chosen: JSONObject? = null
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                if (d.optString("region_type") == "H") { chosen = d; break }
                if (chosen == null) chosen = d
            }
            val admin = chosen ?: return null
            val code = admin.optString("code").ifEmpty { null }
            PlaceInfo(
                dongName = admin.optString("region_3depth_name").ifEmpty { null },
                dongCode = code,
                // 구 코드는 행정동 코드 앞 5자리다 (예 1120065000 → 11200).
                guCode = code?.takeIf { it.length >= 5 }?.substring(0, 5),
            )
        } catch (e: JSONException) {
            android.util.Log.w("CatchFlower", "행정구역 응답을 읽을 수 없다", e)
            null
        }
    }

    /** 좌표 → 주변 장소명. */
    private suspend fun nearbyPlaceName(lat: Double, lng: Double): String? {
        // AT4는 관광명소다 — 공원·수목원·산책로가 여기 걸려서 꽃 찍는 장소와 맞다.
        // 실측: 성수동 좌표에서 `성동올레길 28m`.
        val body = request(
            "https://dapi.kakao.com/v2/local/search/category.json" +
                "?category_group_code=AT4&x=$lng&y=$lat&radius=$SEARCH_RADIUS_M" +
                "&sort=distance&size=1"
        ) ?: return null
        return try {
            val docs = JSONObject(body).optJSONArray("documents") ?: return null
            if (docs.length() == 0) return null
            docs.getJSONObject(0).optString("place_name").ifEmpty { null }
        } catch (e: JSONException) {
            android.util.Log.w("CatchFlower", "장소 검색 응답을 읽을 수 없다", e)
            null
        }
    }

    /**
     * @return 200이면 본문, 아니면 null.
     *
     * ⚠️ **오류를 던지지 않고 null로 돌려준다.** 장소명은 없어도 등록이 되어야 한다 —
     *    카카오가 죽었다고 사용자가 찍은 꽃을 도감에 못 넣게 만들 이유가 없다.
     *    (인식기는 반대다. 거기서는 결과 없이 진행할 수 없어 오류를 던진다.)
     */
    private suspend fun request(url: String): String? {
        val (status, body) = try {
            transport.get(url, "KakaoAK $apiKey")
        } catch (e: CancellationException) {
            throw e // 삼키면 촬영 취소가 안 먹는다
        } catch (e: IOException) {
            android.util.Log.w("CatchFlower", "카카오 호출 실패", e)
            return null
        }
        if (status != 200) {
            // 403 `disabled OPEN_MAP_AND_LOCAL service`는 **콘솔 토글이 꺼진 것**이다.
            // 키가 틀린 것과 구분되게 본문을 남긴다 — 이걸로 하루를 아낀다.
            android.util.Log.w("CatchFlower", "카카오 HTTP $status · $body")
            return null
        }
        return body
    }

    /**
     * 반올림한 좌표를 캐시 키로 쓴다.
     *
     * ⚠️ **[PlaceKey]를 부른다 — 여기서 반올림을 다시 구현하지 않는다.**
     *    B-5의 "같은 장소" 판정이 같은 함수를 쓴다. 각자 반올림하면 캐시는
     *    "같은 자리"인데 B-5는 "다른 장소"라고 보는 어긋남이 생긴다.
     */
    internal fun cacheKey(lat: Double, lng: Double): String = PlaceKey.of(lat, lng)

    private class HttpTransport : Transport {
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
                    //    분기가 사라지고 전부 "실패"가 된다. errorStream을 봐야 한다.
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    status to body
                } finally {
                    conn.disconnect()
                }
            }
    }

    private companion object {
        /** 500m. 이보다 넓히면 "옆 동네 공원"이 장소명으로 붙는다. */
        const val SEARCH_RADIUS_M = 500
    }
}
