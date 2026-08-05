package com.catchflower.app.recognizer

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **실측 재현** — iOS가 캐시해 둔 PlantNet 원본 응답 200개를 AOS 파이프라인에 그대로 태운다.
 *
 * **왜 이게 필요한가.** [PlantNetRecognizerTest]는 내가 만든 응답으로만 검증한다.
 * 진짜 응답의 모양 — 속만 맞는 학명, `Taraxacum sect. Taraxacum` 같은 계급 표기,
 * 후보 10개 중 정답이 5순위 — 에서 같은 결과가 나오는지는 **모른다.**
 * iOS는 이 200장으로 Top-1 77%를 얻었다. **AOS가 같은 숫자를 내야 한다** —
 * 안 그러면 두 앱이 같은 사진에 다른 답을 준다(공유계약 3절).
 *
 * **API 호출은 0건이다.** 픽스처는 iOS 실측이 남긴 캐시에서 만든다
 * (`android/_tools/build_plantnet_replay_fixture.py`). 오너 규칙 그대로다 —
 * "원본 응답을 캐시해야 재과금 없이 재채점", "실측 보고 전 유료 호출 금지".
 *
 * ⚠️ 클래스마다 **제철 달**로 잰다. 8월 하나로 전부 재면 튤립·데이지가 0%로 나오는데
 *    그건 결함이 아니라 개화월 필터가 옳게 도는 것이다 — 한 달로 고정하면
 *    "필터가 떨군 것"과 "인식이 틀린 것"이 섞여 원인을 못 가린다(진행 (19) 7절 1번).
 */
class PlantNetReplayTest {

    // ── 픽스처 ──────────────────────────────────────────────────────────

    private val fixture: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("plantnet_replay.json")
        requireNotNull(stream) {
            "plantnet_replay.json이 없다. " +
                "python3 android/_tools/build_plantnet_replay_fixture.py 를 돌린다"
        }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    /** 도감 200종. JVM 테스트는 assets를 못 읽어서 픽스처가 함께 들고 있다. */
    private val flowers: List<Flower> by lazy {
        val arr = fixture.getJSONArray("flowers")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val months = o.getJSONArray("bloom_months")
            Flower(
                id = o.getInt("id"),
                name = o.getString("name"),
                scientificName = o.getString("scientific_name"),
                family = "",
                bloomMonths = (0 until months.length()).map { months.getInt(it) },
                bloomLabel = "",
                season = Season.SPRING,
                color = "",
                rarity = Rarity.COMMON,
                habitat = "",
                aiDifficulty = AiDifficulty.LOW,
                similarFlowerIds = emptyList(),
                similarFlowerNames = emptyList(),
                illustBatch = 1,
            )
        }
    }

    private val index by lazy { ScientificNameIndex(flowers) }

    private data class Photo(val cls: String, val file: String, val body: String)

    /** 캐시 응답을 우리가 파싱하는 모양으로 되돌린다. */
    private val photos: List<Photo> by lazy {
        val arr = fixture.getJSONArray("photos")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val results = o.getJSONArray("results")
            val items = (0 until results.length()).joinToString(",") { j ->
                val r = results.getJSONObject(j)
                """{"score":${r.getDouble("score")},""" +
                    """"species":{"scientificNameWithoutAuthor":"${r.getString("name")}"}}"""
            }
            Photo(o.getString("cls"), o.getString("file"), """{"results":[$items]}""")
        }
    }

    /** 클래스 → 정답으로 인정할 도감 id 집합. **속 단위로 인정한다** (iOS와 같은 규칙). */
    private val truth: Map<String, Set<Int>> by lazy {
        val genera = fixture.getJSONObject("class_to_genera")
        genera.keys().asSequence().associateWith { cls ->
            val list = genera.getJSONArray(cls)
            val names = (0 until list.length()).map { list.getString(it) }
            flowers.filter { it.scientificName.substringBefore(' ') in names }
                .map { it.id }.toSet()
        }
    }

    private val peakMonth: Map<String, Int> by lazy {
        val o = fixture.getJSONObject("class_peak_month")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }

    private fun candidatesFor(month: Int): List<Int> =
        flowers.filter { month in it.bloomMonths }.map { it.id }

    private fun recognizer(body: String) = PlantNetRecognizer(
        index = index,
        apiKey = "replay",
        transport = object : PlantNetRecognizer.Transport {
            override suspend fun post(url: String, contentType: String, b: ByteArray) = 200 to body
        },
    )

    // ── 검증 ────────────────────────────────────────────────────────────

    @Test
    fun `픽스처가 붙어 있다`() {
        // ⚠️ 비면 아래 테스트들이 **0장을 돌고 통과한다.** 실측 단계에서 한 번 당한 함정이다.
        assertEquals("사진 200장이 아니다", 200, photos.size)
        assertEquals("도감 200종이 아니다", GamePolicy.TOTAL_FLOWER_COUNT, flowers.size)
        assertEquals(5, truth.size)
    }

    /**
     * **iOS 실측과 같은 Top-1을 낸다.**
     *
     * iOS 결과(진행 (19) 2절): sunflowers 95 · tulips 95 · dandelion 90 · roses 62.5 ·
     * daisy 42.5 · **합계 77.0%**. 합격선은 Top-1 70%다.
     */
    @Test
    fun `실측 200장 Top1이 iOS와 같다`() = runBlocking {
        val expected = mapOf(
            "sunflowers" to 95.0, "tulips" to 95.0, "dandelion" to 90.0,
            "roses" to 62.5, "daisy" to 42.5,
        )
        var top1 = 0
        val perClass = HashMap<String, Pair<Int, Int>>() // cls → (맞음, 전체)

        for (photo in photos) {
            val month = peakMonth.getValue(photo.cls)
            val result = recognizer(photo.body).identify(ByteArray(1), candidatesFor(month))
            val hit = result.firstOrNull()?.flowerId in truth.getValue(photo.cls)
            if (hit) top1++
            val (h, n) = perClass[photo.cls] ?: (0 to 0)
            perClass[photo.cls] = (if (hit) h + 1 else h) to (n + 1)
        }

        val report = perClass.entries.sortedBy { it.key }.joinToString("\n") { (cls, v) ->
            "  %-11s %5.1f%% (%d/%d)".format(cls, v.first * 100.0 / v.second, v.first, v.second)
        }
        println("실측 재현 Top-1 ${"%.1f".format(top1 * 100.0 / photos.size)}%\n$report")

        perClass.forEach { (cls, v) ->
            assertEquals(
                "$cls Top-1이 iOS 실측과 다르다. AOS 파이프라인이 iOS와 갈렸다\n$report",
                expected.getValue(cls),
                v.first * 100.0 / v.second,
                0.01,
            )
        }
        assertEquals("합계 Top-1이 iOS 실측(77.0%)과 다르다\n$report", 77.0, top1 * 100.0 / photos.size, 0.01)
    }

    /** 합격선 확인 — A-1 채택 근거다. 여기가 깨지면 벤더 선택 자체를 다시 봐야 한다. */
    @Test
    fun `합격선 Top1 70퍼센트를 넘는다`() = runBlocking {
        val top1 = photos.count { photo ->
            val month = peakMonth.getValue(photo.cls)
            recognizer(photo.body).identify(ByteArray(1), candidatesFor(month))
                .firstOrNull()?.flowerId in truth.getValue(photo.cls)
        }
        val rate = top1 * 100.0 / photos.size
        assertTrue("Top-1 ${"%.1f".format(rate)}% < 합격선 70%", rate >= 70.0)
    }

    /**
     * **genus fallback 수정이 실제로 무엇을 되찾는지** 같은 캐시로 재현한다.
     *
     * 수정 전(속 대표 = 도감번호 최솟값)은 `preferring`을 안 넘긴 것과 같다.
     * iOS 실측에서 이 차이가 **장미 6월 5% → 62%**로 나타났다(진행 (19) 3절).
     */
    @Test
    fun `속 대표의 개화기가 끝난 달에 정답이 사라진다`() {
        // ⚠️ **달을 6월로 잡으면 차이가 안 나온다** (25/40 vs 25/40 — 처음에 그렇게 짰다).
        //    6월엔 속 대표 찔레꽃(29, 5~6월)이 아직 피어서 필터를 통과하고,
        //    찔레꽃도 Rosa라서 정답으로 인정된다. **버그가 있어도 숫자가 같다.**
        //    8월이면 찔레꽃이 지고 장미(81, 5~10월)만 남는다 — iOS 실측 표의
        //    `장미 8월 0→62`가 이 지점이다(진행 (19) 3절).
        val month = 8
        val inSeason = candidatesFor(month).toSet()
        val rosePhotos = photos.filter { it.cls == "roses" }

        fun topFlowerId(photo: Photo, preferring: Set<Int>?): Int? {
            val results = JSONObject(photo.body).getJSONArray("results")
            for (i in 0 until results.length()) {
                val name = results.getJSONObject(i).getJSONObject("species")
                    .getString("scientificNameWithoutAuthor")
                val id = index.flowerId(name, preferring) ?: continue
                if (id !in inSeason) continue
                return id
            }
            return null
        }

        val fixed = rosePhotos.count { topFlowerId(it, inSeason) in truth.getValue("roses") }
        val legacy = rosePhotos.count { topFlowerId(it, null) in truth.getValue("roses") }

        println("장미 ${month}월 — 수정 후 $fixed/${rosePhotos.size} · 수정 전 $legacy/${rosePhotos.size}")
        assertTrue(
            "후보 집합을 넘기는 쪽이 더 많이 맞혀야 한다 (수정 후 $fixed, 수정 전 $legacy). " +
                "같으면 genus fallback 수정이 동작하지 않는 것이다",
            fixed > legacy,
        )
        // 수정 전에는 **0에 가까워야** 한다. 속 대표가 죽으면 그 속 전체가 못 맞힌다.
        assertEquals("수정 전 동작이 iOS 실측(8월 0%)과 다르다", 0, legacy)
    }

    /**
     * ⚠️ **`MIN_CONFIDENCE_FOR_ANY_CANDIDATE`가 정답을 얼마나 버리는지 숫자로 남긴다.**
     *
     * B-3-a ★ 오너 미결 항목의 근거다. 통과/실패를 판정하지 않는다 —
     * **값을 바꿨을 때 무엇이 달라지는지 보이게 하는 것**이 목적이다.
     * iOS 실측: floor 0.30이 정답 66장(43%)을 버리고 오답 9장을 막았다.
     */
    @Test
    fun `floor가 버리는 정답 수를 기록한다`() = runBlocking {
        var correctBelowFloor = 0
        var wrongBelowFloor = 0
        var correctTotal = 0

        for (photo in photos) {
            val month = peakMonth.getValue(photo.cls)
            val top = recognizer(photo.body).identify(ByteArray(1), candidatesFor(month))
                .firstOrNull() ?: continue
            val correct = top.flowerId in truth.getValue(photo.cls)
            if (correct) correctTotal++
            if (top.score < GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE) {
                if (correct) correctBelowFloor++ else wrongBelowFloor++
            }
        }

        println(
            "floor ${GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE} — " +
                "버리는 정답 $correctBelowFloor/$correctTotal · 막는 오답 $wrongBelowFloor " +
                "(B-3-a ★ 오너 미결)",
        )
        // 실측에서 손해가 이득보다 크다는 사실 자체를 고정한다. 이 관계가 뒤집히면
        // floor를 유지할 근거가 생긴 것이므로 **테스트가 알려줘야 한다.**
        assertTrue(
            "floor가 오답보다 정답을 더 많이 버리지 않게 되었다 " +
                "(정답 $correctBelowFloor · 오답 $wrongBelowFloor). B-3-a 근거를 다시 계산한다",
            correctBelowFloor > wrongBelowFloor,
        )
    }

    /**
     * **`Taraxacum sect. Taraxacum`이 민들레로 번역되는가** (B-4 근거).
     *
     * iOS 실측에서 민들레 1순위 학명 40장 중 **29장**이 이 표기였다.
     * `sect.`는 종이 아니라 속과 종 사이 계급이다 — 정규화가 `taraxacum sect`로
     * 잘라 버리면 **속조차 못 맞히고 민들레 전체가 판별 실패가 된다.**
     */
    @Test
    fun `sect 표기도 속으로 번역한다`() {
        val april = candidatesFor(4).toSet()
        val id = index.flowerId("Taraxacum sect. Taraxacum", preferring = april)
        assertTrue(
            "sect. 표기가 민들레로 번역되지 않았다 (id=$id)",
            id in truth.getValue("dandelion"),
        )
    }
}
