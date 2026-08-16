package com.catchflower.app.recognizer

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 학명 색인 + 응답 파싱 테스트.
 *
 * **왜 필요한가**: 이 층이 틀리면 화면에는 **다른 꽃 이름이 예쁘게 나온다.** 오류도, 빈 화면도
 * 아니다 — 사용자는 자기가 찍은 꽃이 그 꽃인 줄 안다. 눈으로 잡을 방법이 없다.
 *
 * 아래 `Rosa` 케이스는 iOS가 사진 200장 실측에서 실제로 밟은 것을 그대로 재현한다:
 * 8월 장미 사진에서 정답 `Rosa chinensis 0.606`이 탈락하고 오답 `Begonia grandis 0.003`이
 * 1순위가 되었다. **유료 호출 없이** 재현하려고 [PlantNetRecognizer.Transport]를 갈아끼운다
 * (오너 규칙: 실측 보고 전 유료 API 호출 금지).
 *
 * ⚠️ 학명·개화월은 `꽃도감/flowers.json`의 실제 값이다. 손으로 지어낸 값으로 테스트하면
 *    "우리 데이터에서" 이 버그가 나는지를 확인하지 못한다.
 */
class PlantNetRecognizerTest {

    private fun flower(
        id: Int,
        name: String,
        scientificName: String,
        bloomMonths: List<Int>,
        aliases: List<String> = emptyList(),
        /** 계약 1-6. 기본값은 **자기 id**(그룹에 없는 종) — 접기는 여기서 안 잰다. */
        collectGroupId: Int = id,
    ) = Flower(
        id = id,
        name = name,
        scientificName = scientificName,
        scientificAliases = aliases,
        family = "테스트과",
        bloomMonths = bloomMonths,
        bloomLabel = "테스트",
        bloomSource = BloomSource.HUMAN,
        season = Season.SUMMER,
        color = "빨강",
        rarity = Rarity.COMMON,
        habitat = "테스트",
        aiDifficulty = AiDifficulty.LOW,
        similarFlowerIds = emptyList(),
        similarFlowerNames = emptyList(),
        collectGroupId = collectGroupId,
        illustBatch = 1,
    )

    // flowers.json 실제 값 (id·학명·개화월).
    private val 찔레꽃 = flower(29, "찔레꽃", "Rosa multiflora", listOf(5, 6))
    private val 장미 = flower(81, "장미", "Rosa hybrida", listOf(5, 6, 7, 8, 9, 10))
    private val 해당화 = flower(82, "해당화", "Rosa rugosa", listOf(5, 6, 7))
    private val 베고니아 = flower(167, "베고니아", "Begonia semperflorens", listOf(5, 6, 7, 8, 9, 10))
    private val 민들레 = flower(31, "민들레", "Taraxacum platycarpum", listOf(3, 4, 5))

    private val flowers = listOf(찔레꽃, 장미, 해당화, 베고니아, 민들레)
    private val index = ScientificNameIndex(flowers)

    /** 8월 후보 집합 — 개화월 하드 필터가 이미 좁힌 결과. 찔레꽃(5~6월)·해당화(5~7월)는 없다. */
    private val august = listOf(장미.id, 베고니아.id)

    // ── 색인 ────────────────────────────────────────────────────────────

    @Test
    fun `학명이 정확히 맞으면 그 종이다`() {
        assertEquals(장미.id, index.flowerId("Rosa hybrida"))
        assertEquals(민들레.id, index.flowerId("Taraxacum platycarpum"))
    }

    @Test
    fun `속만 맞아도 받아들인다`() {
        // PlantNet이 `Taraxacum officinale`(우리 도감엔 없는 종)를 줘도 민들레는 맞다.
        assertEquals(민들레.id, index.flowerId("Taraxacum officinale"))
    }

    @Test
    fun `속 대표를 도감번호로 고정하지 않는다`() {
        // ⚠️ 이게 iOS가 밟은 지뢰다. 후보를 넘기면 8월엔 장미(81)를 골라야 한다 —
        //    도감번호가 더 작은 찔레꽃(29)을 고르면 그 뒤 필터에서 정답이 사라진다.
        assertEquals(장미.id, index.flowerId("Rosa chinensis", preferring = august.toSet()))
        // 후보를 안 넘기는 경로에서만 최솟값을 쓴다.
        assertEquals(찔레꽃.id, index.flowerId("Rosa chinensis"))
    }

    @Test
    fun `정확히 맞은 종이 후보 밖이면 같은 속의 개화 중인 종으로 내린다`() {
        // `Rosa rugosa`(해당화, 5~7월)를 정확히 맞혔어도 8월엔 쓸 수 없다.
        assertEquals(장미.id, index.flowerId("Rosa rugosa", preferring = august.toSet()))
    }

    @Test
    fun `품종 표기와 대소문자를 무시한다`() {
        assertEquals(장미.id, index.flowerId("ROSA HYBRIDA"))
        assertEquals(민들레.id, index.flowerId("Taraxacum platycarpum var. koreanum"))
    }

    @Test
    fun `도감에 없는 속은 없다고 답한다`() {
        // 도감 200종 밖 → 판별 실패(화면 12)로 이어져야 한다. 억지로 끼워 맞추지 않는다.
        assertEquals(null, index.flowerId("Quercus mongolica"))
    }

    // ── 응답 파싱 ────────────────────────────────────────────────────────

    private fun recognizer(status: Int, responseBody: String) = PlantNetRecognizer(
        index = index,
        apiKey = "test-key",
        transport = object : PlantNetRecognizer.Transport {
            override suspend fun post(url: String, contentType: String, body: ByteArray) =
                status to responseBody
        },
    )

    private fun response(vararg pairs: Pair<String, Double>): String {
        val results = pairs.joinToString(",") { (name, score) ->
            """{"score":$score,"species":{"scientificNameWithoutAuthor":"$name"}}"""
        }
        return """{"results":[$results]}"""
    }

    @Test
    fun `iOS가 실측에서 밟은 8월 장미 케이스를 재현한다`() = runBlocking {
        // 실측 응답 그대로: 정답이 0.606, 오답이 0.003.
        val body = response(
            "Rosa chinensis" to 0.606,
            "Begonia grandis" to 0.003,
        )
        val result = recognizer(200, body).identify(ByteArray(1), august)

        // **1순위는 장미여야 한다.** 색인이 찔레꽃으로 번역하면 여기서 베고니아가 1순위가 된다.
        assertEquals(장미.id, result.first().flowerId)
        assertEquals(0.606f, result.first().score, 0.0001f)
    }

    @Test
    fun `후보 밖의 종은 버린다`() = runBlocking {
        // 11월에 벚꽃이 오는 상황의 축소판 — 후보에 없으면 결과에 남지 않는다.
        val result = recognizer(200, response("Quercus mongolica" to 0.9)).identify(ByteArray(1), august)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `같은 종으로 번역되는 응답은 한 번만 담는다`() = runBlocking {
        // 같은 속의 여러 종이 오면 전부 장미로 번역된다. 후보 3개가 같은 꽃이면
        // 화면 09 변형이 **같은 이름 3개**를 보여준다.
        val body = response(
            "Rosa chinensis" to 0.5,
            "Rosa gallica" to 0.3,
            "Begonia semperflorens" to 0.1,
        )
        val result = recognizer(200, body).identify(ByteArray(1), august)
        assertEquals(listOf(장미.id, 베고니아.id), result.map { it.flowerId })
    }

    @Test
    fun `후보는 최대 3개다`() = runBlocking {
        val many = listOf(장미.id, 베고니아.id, 민들레.id, 해당화.id, 찔레꽃.id)
        val body = response(
            "Rosa hybrida" to 0.5,
            "Begonia semperflorens" to 0.2,
            "Taraxacum platycarpum" to 0.1,
            "Rosa rugosa" to 0.05,
        )
        val result = recognizer(200, body).identify(ByteArray(1), many)
        assertEquals(3, result.size)
    }

    @Test
    fun `404는 오류가 아니라 판별 실패다`() = runBlocking {
        // PlantNet은 "인식 결과 없음"에 404를 준다. 예외로 만들면 화면 12 대신
        // "일시적인 문제"를 보여주게 된다 — 사용자가 계속 재시도한다.
        assertTrue(recognizer(404, "").identify(ByteArray(1), august).isEmpty())
    }

    @Test
    fun `429는 한도 초과로 구분한다`() = runBlocking {
        val error = runCatching { recognizer(429, "").identify(ByteArray(1), august) }
            .exceptionOrNull()
        assertTrue("한도 초과는 재시도하면 안 된다", error is RecognitionError.QuotaExceeded)
    }

    @Test
    fun `500은 재시도 가치가 있는 오류다`() = runBlocking {
        val error = runCatching { recognizer(500, "").identify(ByteArray(1), august) }
            .exceptionOrNull()
        assertTrue(error is RecognitionError.Unavailable)
    }

    @Test
    fun `후보가 비면 유료 호출을 하지 않는다`() = runBlocking {
        // 12~2월 휴지기. 부르면 돈만 쓰고 결과는 전부 탈락한다.
        var called = false
        val r = PlantNetRecognizer(
            index = index,
            apiKey = "test-key",
            transport = object : PlantNetRecognizer.Transport {
                override suspend fun post(url: String, contentType: String, body: ByteArray) =
                    502 to "".also { called = true }
            },
        )
        assertTrue(r.identify(ByteArray(1), emptyList()).isEmpty())
        assertTrue("후보가 없는데 API를 불렀다", !called)
    }

    @Test
    fun `키가 없으면 사용 불가로 던진다`() = runBlocking {
        // 호출부가 이걸 잡아 Mock으로 폴백한다. 조용히 빈 목록을 주면
        // "판별 실패"로 보여서 키가 없다는 걸 아무도 모른다.
        val r = PlantNetRecognizer(index = index, apiKey = "")
        val error = runCatching { r.identify(ByteArray(1), august) }.exceptionOrNull()
        assertTrue(error is RecognitionError.Unavailable)
    }

    @Test
    fun `통신 예외는 RecognitionError로 바꿔서 던진다`() = runBlocking {
        // 비행기 모드·타임아웃. IOException이 그대로 새어 나가면 호출부(RecognitionError만
        // 잡는다)를 지나쳐 **앱이 죽는다.**
        val r = PlantNetRecognizer(
            index = index,
            apiKey = "test-key",
            transport = object : PlantNetRecognizer.Transport {
                override suspend fun post(url: String, contentType: String, body: ByteArray):
                    Pair<Int, String> = throw java.net.UnknownHostException("my-api.plantnet.org")
            },
        )
        val error = runCatching { r.identify(ByteArray(1), august) }.exceptionOrNull()
        assertTrue("실제로 던진 것: $error", error is RecognitionError.Unavailable)
    }

    @Test
    fun `200인데 JSON이 아니면 사용 불가로 바꾼다`() = runBlocking {
        // 공용 와이파이 로그인 페이지가 HTML을 200으로 주는 상황. 크래시로 만들지 않는다.
        val error = runCatching { recognizer(200, "<html>Login</html>").identify(ByteArray(1), august) }
            .exceptionOrNull()
        assertTrue("실제로 던진 것: $error", error is RecognitionError.Unavailable)
    }

    @Test
    fun `취소는 삼키지 않는다`() = runBlocking {
        // 삼키면 화면 08에서 `취소`를 눌러도 판별이 계속 돌다가 화면 09로 끌려간다.
        val r = PlantNetRecognizer(
            index = index,
            apiKey = "test-key",
            transport = object : PlantNetRecognizer.Transport {
                override suspend fun post(url: String, contentType: String, body: ByteArray):
                    Pair<Int, String> = throw kotlinx.coroutines.CancellationException("취소됨")
            },
        )
        val error = runCatching { r.identify(ByteArray(1), august) }.exceptionOrNull()
        assertTrue(
            "취소가 RecognitionError로 바뀌면 안 된다. 실제: $error",
            error is kotlinx.coroutines.CancellationException,
        )
    }

    @Test
    fun `범위를 벗어난 점수는 잘라낸다`() = runBlocking {
        // 서버가 계약을 깨는 값을 줘도 앱이 죽지 않아야 한다 (Candidate의 require).
        val result = recognizer(200, response("Rosa hybrida" to 1.4)).identify(ByteArray(1), august)
        assertEquals(1f, result.first().score, 0.0001f)
    }

    /**
     * 🔴 **응답 요약 로그가 세 원인을 구분할 수 있는가.**
     *
     * **왜 로그에 테스트가 붙는가.** 이 줄이 없던 동안 오너 QA가 막혔다((45)) —
     * `판별 호출 직전` 뒤가 비어서 `어떤 꽃인지 알 수 없었어요`의 원인이
     * ⓐ 못 알아봤다 / ⓑ 도감·개화월에서 걸렸다 / ⓒ floor 미달 중 무엇인지
     * **알 수 없었다.** 화면에는 셋이 똑같이 보인다. 그리고 로그는 **조용해져도
     * 증상이 없다** — 지워지거나 값이 빠져도 아무도 모른다. 그래서 고정한다.
     *
     * ⚠️ **학명이 들어 있어야 한다.** 도감 id만 찍으면 ⓑ에서 "무엇이 걸렸는지" 모른다.
     */
    @Test
    fun `응답 요약 로그가 원인을 구분할 수 있게 남는다`() = runBlocking {
        val lines = mutableListOf<String>()
        // 실기기 실측(21:09)의 모양을 그대로 쓴다 — **1순위를 맞혔는데 점수가 0.018이라
        // floor에 걸린** 사진이다. ⓑ와 ⓒ가 한 줄에 같이 보여야 한다.
        // ⚠️ 실측의 `Taraxacum`을 그대로 쓰면 이 픽스처의 민들레가 3~5월이라 8월 후보에
        //    없어서 `통과=0`이 되고, **ⓒ(floor 미달)를 확인할 수 없다.** 그래서 8월에 피는
        //    속으로 바꿔 같은 점수를 쓴다.
        val body = response(
            "Rosa chinensis" to 0.018,
            "Quercus mongolica" to 0.9, // 도감 200종 밖 → 통과 목록에는 없어야 한다
            "Begonia grandis" to 0.007,
        )
        PlantNetRecognizer(
            index = index,
            apiKey = "test-key",
            transport = object : PlantNetRecognizer.Transport {
                override suspend fun post(url: String, contentType: String, body2: ByteArray) =
                    200 to body
            },
            log = { lines += it },
        ).identify(ByteArray(1), august)

        assertEquals("로그가 한 줄 남아야 한다. 실제: $lines", 1, lines.size)
        val line = lines.single()

        // ⓐ vs ⓑ를 가르는 값: PlantNet이 **몇 개를 줬는가**
        assertTrue("받은 후보 수가 없다: $line", "받은 후보=3" in line)
        // ⓑ를 읽으려면 **학명**이 있어야 한다
        assertTrue("학명이 없다 — 무엇이 걸렸는지 알 수 없다: $line", "Rosa chinensis" in line)
        assertTrue("도감 밖 학명도 남아야 한다(왜 탈락했는지): $line", "Quercus mongolica" in line)
        // ⓒ를 읽으려면 통과 목록과 점수, 그리고 비교 대상인 floor가 있어야 한다
        assertTrue("통과 수가 없다: $line", "통과=2" in line)
        assertTrue("1순위 점수가 없다: $line", "0.018" in line)
        assertTrue(
            "floor가 없다 — 점수만 보고 미달인지 판단할 수 없다: $line",
            "floor=${GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE}" in line,
        )
    }

    /** 로거를 안 넘기면 조용하다 — 테스트·JVM에서 `android.util.Log`를 부르지 않는 근거다. */
    @Test
    fun `로거를 주지 않으면 아무것도 하지 않는다`() = runBlocking {
        // 여기서 죽으면 인식기가 android.util.Log를 직접 부르는 것이다
        // (`Method i in android.util.Log not mocked`).
        val result = recognizer(200, response("Rosa chinensis" to 0.606))
            .identify(ByteArray(1), august)
        assertEquals(1, result.size)
    }
}
