package com.catchflower.app.recognizer

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.stringOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 판별이 실패한 이유. **빈 목록(판별 실패)과 구분한다** — 사용자에게 보여줄 문구가 다르고,
 * 재시도해도 되는지가 다르다.
 */
sealed class RecognitionError(message: String) : Exception(message) {
    /** 키가 없거나 네트워크·서버 문제. 재시도 가치가 있다. */
    class Unavailable(detail: String) : RecognitionError("인식기를 쓸 수 없다: $detail")

    /** 일일 한도 초과(429). **재시도하면 안 된다** — 비용 문서 4절. */
    data object QuotaExceeded : RecognitionError("PlantNet 일일 한도를 넘겼다")
}

/**
 * PlantNet 실제 인식기 (A-1 확정). iOS `PlantNetRecognizer.swift`와 같은 규칙을 쓴다.
 *
 * **개화월 하드 필터가 여기서 완성된다.** PlantNet은 자기 프로젝트 안의 수천 종에서 고르므로
 * 11월에 "벚꽃"을 1순위로 줄 수 있다. [identify]의 `candidates`에 없는 종은 버린다 —
 * A-1의 필수 구현 조건이다.
 *
 * ⚠️ 네트워크를 새 의존성 없이 쓴다([HttpURLConnection]). OkHttp를 넣으면 APK가 커지고
 *    이 한 번의 호출을 위해 유지할 이유가 없다. 대신 [transport]로 갈아끼워
 *    **유료 호출 없이** 응답 파싱을 테스트한다 (오너 규칙: 실측 보고 전 유료 호출 금지).
 */
class PlantNetRecognizer(
    private val index: ScientificNameIndex,
    private val apiKey: String,
    /**
     * `k-eastern-asia`(4,932종) — iOS가 실측으로 존재를 확인했다 (2026-08-05).
     * `k-world-flora`(84,513종)보다 좁아서 오답이 줄어든다.
     */
    private val project: String = "k-eastern-asia",
    private val transport: Transport = HttpTransport(),
    /**
     * 응답 요약을 어디로 보낼지. **기본은 아무것도 안 한다.**
     *
     * ⚠️ **여기서 `android.util.Log`를 직접 부르면 JVM 테스트가 죽는다**
     *    (`Method i in android.util.Log not mocked`). 피하는 설정
     *    `unitTests.isReturnDefaultValues = true`는 **모든 안드로이드 API를 조용히
     *    0/null로 만드는** 거부된 항목이다. 그래서 실제 로그는 호출처
     *    ([com.catchflower.app.ui.capture.CaptureViewModel])가 주입한다.
     */
    private val log: (String) -> Unit = {},
    /**
     * 도감번호 → **수집 그룹 대표 번호** (B-4 · 계약 1-6). 기본값은 항등이다.
     *
     * 🔴 **왜 인식기가 그룹을 알아야 하는가 — 여기가 B-4의 실제 지점이다.**
     *    [parse]는 후보를 **3개에서 끊는다**([GamePolicy.CANDIDATE_COUNT]). 그 3개가
     *    **종 단위**로 세어진 것이면, `민들레 / 서양민들레 / 별꽃`처럼 앞의 둘이 한 칸으로
     *    접히는 경우 **화면에 남는 후보가 2개**가 된다 — 즉 접기를 뒤에서만 하면
     *    B-4가 후보를 **늘리는 게 아니라 줄인다.** (실제로 처음에 그렇게 짰다:
     *    [com.catchflower.app.recognizer.IdentifyFlow.decide]에서 접고 잘랐는데,
     *    거기 오는 목록은 **이미 3개로 잘려 있어서** 4순위가 존재하지 않았다.
     *    ⚠️ 그 상태로도 **테스트는 전부 초록이었다** — Top-1·화면12 비율은 1순위만
     *    보므로 후보 개수가 줄어도 **원리상 안 움직인다.**)
     *
     *    그래서 **끊는 단위를 그룹으로 바꾼다.** `민들레 / 서양민들레 / 별꽃 / 팬지`면
     *    `민들레 / 별꽃 / 팬지` 3칸이 남는다 — 접힌 자리만큼 **뒤 후보가 올라온다.**
     *
     * ⚠️ 후보로 담는 `flowerId`는 **접기 전의 종**이다(점수가 제일 높은 멤버).
     *    표시·등록은 [IdentifyFlow]가 대표종으로 접는다 — 여기서 미리 대표로 바꾸면
     *    같은 그룹의 두 번째 멤버가 **더 높은 점수로 덮어쓰는지**를 알 수 없다.
     */
    private val groupOf: (Int) -> Int = { it },
) : FlowerRecognizer {

    /** HTTP 한 번. 테스트에서 갈아끼우는 자리다. */
    interface Transport {
        /** @return (상태코드, 본문). 던지지 않고 상태코드로 알린다. */
        suspend fun post(url: String, contentType: String, body: ByteArray): Pair<Int, String>
    }

    override suspend fun identify(
        image: ByteArray,
        candidates: List<Int>,
        debugLabel: String?,
    ): List<Candidate> {
        // debugLabel은 Mock 전용이다 (계약). 실구현은 무시한다.
        if (apiKey.isEmpty()) throw RecognitionError.Unavailable("PLANTNET_API_KEY가 비어 있다")

        // 후보가 없으면 **부르지 않는다.** 12~2월 휴지기에 유료 호출을 태울 이유가 없다.
        if (candidates.isEmpty()) return emptyList()

        val boundary = "catchflower-${UUID.randomUUID()}"
        val (status, body) = try {
            transport.post(
                url = "https://my-api.plantnet.org/v2/identify/$project" +
                    "?api-key=$apiKey&include-related-images=false&nb-results=10",
                contentType = "multipart/form-data; boundary=$boundary",
                body = multipartBody(image, boundary),
            )
        } catch (e: CancellationException) {
            // ⚠️ 취소는 통과시킨다. 여기서 삼키면 화면 08에서 `취소`를 눌러도
            //    판별이 계속 돌다가 화면 09로 끌려간다.
            throw e
        } catch (e: IOException) {
            // 비행기 모드·DNS 실패·타임아웃. **호출부가 RecognitionError만 잡으므로
            // 그대로 새어 나가면 앱이 죽는다.**
            throw RecognitionError.Unavailable(e.toString())
        }

        when (status) {
            200 -> Unit
            429 -> throw RecognitionError.QuotaExceeded
            // 404 = 인식 결과 없음. 오류가 아니라 **판별 실패(화면 12)** 다.
            404 -> return emptyList()
            else -> throw RecognitionError.Unavailable("HTTP $status")
        }

        return try {
            parse(body, candidates).also { out ->
                // 🔴 **지우지 않는다.** 이게 없으면 `판별 호출 직전` 뒤가 비어서
                //    화면 12의 원인 **세 가지를 구분할 수 없다**((45) QA에서 실제로 막혔다):
                //      ⓐ PlantNet이 아무것도 못 알아봤다 (`받은 후보=0`)
                //      ⓑ 알아봤지만 도감·개화월에서 전부 걸렸다 (`받은 후보=N · 통과=0`)
                //      ⓒ 통과했지만 floor 미달이다 (`통과=N`인데 화면 12)
                //    셋 다 화면에 똑같이 `어떤 꽃인지 알 수 없었어요`로 보인다.
                //    ⚠️ **학명을 찍는다.** 도감 id만 찍으면 ⓑ에서 "무엇이 걸렸는지" 모른다.
                val raw = JSONObject(body).optJSONArray("results")
                val names = (0 until (raw?.length() ?: 0)).mapNotNull { i ->
                    val r = raw!!.optJSONObject(i) ?: return@mapNotNull null
                    val n = r.optJSONObject("species")?.stringOrNull("scientificNameWithoutAuthor")
                    n?.let { "$it ${"%.3f".format(r.optDouble("score", 0.0))}" }
                }
                log(
                    "판별 응답: 받은 후보=${names.size} [${names.joinToString(" · ")}] → " +
                        "통과=${out.size} ${out.map { "${it.flowerId}:${"%.3f".format(it.score)}" }} " +
                        "(floor=${GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE})",
                )
            }
        } catch (e: JSONException) {
            // 200인데 JSON이 아닌 경우(프록시가 끼어든 HTML 등). 크래시로 만들지 않는다.
            throw RecognitionError.Unavailable("응답을 읽을 수 없다: $e")
        }
    }

    /** 응답 → 후보. **개화월 하드 필터를 여기서 적용한다.** */
    internal fun parse(body: String, candidates: List<Int>): List<Candidate> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val allowed = candidates.toSet()
        // 🔴 **종이 아니라 수집 그룹으로 센다** (B-4 · [groupOf] 주석). 종으로 세면
        //    접히는 두 종이 3칸 중 2칸을 먹고 화면에 후보가 2개만 남는다.
        val seen = HashSet<Int>()
        val out = ArrayList<Candidate>(GamePolicy.CANDIDATE_COUNT)

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            // [stringOrNull]이다 — 학명이 JSON `null`이면 안드로이드에서 `"null"`이
            // 되고, 그 문자열을 색인에서 찾다가 못 찾아 **후보가 조용히 하나 빠진다.**
            val name = item.optJSONObject("species")
                ?.stringOrNull("scientificNameWithoutAuthor") ?: continue

            // **후보 집합을 색인에 넘긴다.** 안 넘기면 속 대표가 도감번호 최솟값으로 정해져서
            // 8월에 `Rosa chinensis 0.606`(정답)이 `찔레꽃`(5~6월)으로 번역되고 탈락한다.
            // iOS가 실측 200장으로 밟은 지뢰다 — [ScientificNameIndex] 주석 참조.
            val flowerId = index.flowerId(name, preferring = allowed) ?: continue
            if (flowerId !in allowed) continue
            // ⚠️ `flowerId`가 아니라 `groupOf(flowerId)`다. 응답은 점수 내림차순이므로
            //    같은 그룹에서 **먼저 온 종(= 점수가 높은 쪽)** 이 그 칸을 가진다.
            if (!seen.add(groupOf(flowerId))) continue

            // score는 PlantNet의 보정된 확률이라 **그대로 쓴다** (퍼센트로 곱하지 않는다).
            // 범위를 벗어난 값이 오면 Candidate의 require가 앱을 죽인다 — 서버 값이 우리
            // 계약을 깨는 경우를 크래시로 만들 이유가 없어서 여기서 자른다.
            val score = item.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f)
            out.add(Candidate(flowerId = flowerId, score = score))
            if (out.size == GamePolicy.CANDIDATE_COUNT) break
        }
        return out
    }

    private fun multipartBody(image: ByteArray, boundary: String): ByteArray {
        val out = ByteArrayOutputStream(image.size + 512)
        fun write(s: String) = out.write(s.toByteArray())

        write("--$boundary\r\n")
        // `organs=flower` — 우리가 받는 건 항상 꽃 사진이다. 기관을 알려주면 정확도가 오른다.
        write("Content-Disposition: form-data; name=\"organs\"\r\n\r\n")
        write("flower\r\n")
        write("--$boundary\r\n")
        write("Content-Disposition: form-data; name=\"images\"; filename=\"flower.jpg\"\r\n")
        write("Content-Type: image/jpeg\r\n\r\n")
        out.write(image)
        write("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    /** 실제 네트워크. */
    private class HttpTransport : Transport {
        override suspend fun post(
            url: String,
            contentType: String,
            body: ByteArray,
        ): Pair<Int, String> = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                // 화면 08이 `5초 정도 걸려요`라고 말한다. 무한정 기다리게 두지 않는다.
                connectTimeout = 15_000
                readTimeout = 30_000
                setFixedLengthStreamingMode(body.size)
            }
            try {
                conn.outputStream.use { it.write(body) }
                val status = conn.responseCode
                // ⚠️ 4xx·5xx에서 `inputStream`은 던진다. `errorStream`으로 읽어야
                //    상태코드별 분기가 IOException에 먹히지 않는다.
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                status to text
            } finally {
                conn.disconnect()
            }
        }
    }
}
