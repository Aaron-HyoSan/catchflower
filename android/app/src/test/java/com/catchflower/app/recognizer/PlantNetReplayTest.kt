package com.catchflower.app.recognizer

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.FlowerRepository
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

    private fun dex(key: String): List<Flower> {
        val arr = fixture.getJSONArray(key)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val months = o.getJSONArray("bloom_months")
            Flower(
                id = o.getInt("id"),
                name = o.getString("name"),
                scientificName = o.getString("scientific_name"),
                family = "",
                bloomMonths = (0 until months.length()).map { months.getInt(it) },
                bloomLabel = "",
                // 판별 자체는 `bloomSource`를 안 본다(개화월과 학명만 쓴다). 그런데
                // **개화월 필터가 도는지 재려면 이 축이 필요하다** — 아래
                // `개화월 필터가 근거 있는 종을 실제로 좁힌다`가 이걸로 표본을 가른다.
                bloomSource = BloomSource.fromWire(o.getString("bloom_source")),
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

    /**
     * **대조군 200종.** JVM 테스트는 assets를 못 읽어서 픽스처가 함께 들고 있다.
     *
     * 🔴 **여기를 2,057종으로 바꾸지 않는다.** 아래 기대값(Top-1 77.0% · 클래스별
     *    95/95/90/62.5/42.5 · 화면12 66/42/92)은 **이 200종에서 나온 숫자**이고
     *    iOS 실측과 비교하는 근거다. 확장 도감을 넣으면 숫자가 전부 움직이는데,
     *    그러면 "확장 때문인가 파이프라인이 깨진 건가"를 가릴 수 없다.
     *    확장 쪽은 [flowersFull]로 **따로** 잰다.
     */
    private val flowers: List<Flower> by lazy { dex("flowers") }

    /** 앱이 실제로 싣는 도감 2,057종. 확장이 판별을 어떻게 바꾸는지 잰다. */
    private val flowersFull: List<Flower> by lazy { dex("flowers_full") }

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
        // 🔴 **대조군은 200종으로 못 박는다** — `TOTAL_FLOWER_COUNT`를 쓰면 안 된다.
        //    확장할 때 그 상수가 2057이 되면서 이 단정이 **저절로 따라 움직였다**.
        //    그러면 대조군이 통째로 갈려도 아무것도 빨개지지 않는다.
        assertEquals("대조군이 200종이 아니다", 200, flowers.size)
        assertEquals("전체 도감이 ${GamePolicy.TOTAL_FLOWER_COUNT}종이 아니다",
            GamePolicy.TOTAL_FLOWER_COUNT, flowersFull.size)
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
     * **이 검사가 B-3-a 승인((44))을 이끌어냈다.** 통과/실패를 판정하지 않는다 —
     * **값을 바꿨을 때 무엇이 달라지는지 보이게 하는 것**이 목적이었다.
     *
     * | floor | 버리는 정답 | 막는 오답 |
     * |---|---|---|
     * | 0.30 (옛값) | **66장 / 154** | 9장 |
     * | **0.05 (현재)** | **18장 / 154** | 6장 |
     *
     * ⚠️ **0.05에서도 순효과는 여전히 음수다**(정답 18장 > 오답 6장). 즉 floor를
     *    0으로 없애는 것이 실측상 더 낫다 — 그런데 그러면 오답이 10장 전부 통과한다.
     *    6장을 막는 대가로 18장을 버리는 것을 남겨 둔 이유는 **오등록이 도감·랭킹에
     *    영구히 남기 때문**이다(되돌리는 화면이 없다). 이 판단이 뒤집히면
     *    아래 단정이 알려준다.
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
                "(B-3-a ✅ 0.05 확정 · (44))",
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
     * 🔴 **사용자가 실제로 보는 실패율을 기록한다** — `identify()`가 아니라
     * [IdentifyFlow.decide]까지 태운다.
     *
     * **왜 이 검사가 따로 있어야 하는가.** 위의 `실측 200장 Top1이 iOS와 같다`는
     * **1순위가 맞았는가**만 본다. 그런데 화면 12로 갈지는 `decide()`가 정하고,
     * 거기엔 `identify()`에 없는 관문이 하나 더 있다 —
     * [GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE]. 그래서 **Top-1 77%가 초록인 채로
     * 사용자는 대부분 실패를 본다**는 상태가 성립했고, 실제로 성립해 있었다:
     * 실기기에서 꽃을 찍었는데 `꽃이 아닐 수도 있어요`가 떴다((42)).
     *
     * ✅ **floor 0.30 → 0.05 승인((44))의 효과가 이 표다** (8월 후보풀 98종 · 캐시 200장):
     *
     * | 결과 | floor 0.30 (옛값) | **floor 0.05 (현재)** |
     * |---|---|---|
     * | 후보 0개 → 화면 12 | 66장 (33%) | 66장 — **안 움직인다** |
     * | 1순위 < floor → 화면 12 | 77장 (38%) | **42장 (21%)** |
     * | 화면 09/09변형 | 57장 (28%) | **92장 (46%)** |
     * | 실패율 | 72% | **54%** |
     * | 3연속(`꽃이 아닐 수도 있어요`) | **37%** | **16%** |
     *
     * 🔴 **후보 0개 66장은 floor로 줄일 수 없다** — 여기가 **남은 실패의 절반**이고
     *    성질이 다르다. PlantNet 답이 ④단계에서 걸러진 것인데, 원인은 개화월 필터가
     *    아니라 **도감 200종이 좁다**는 것이다(도감에 있는 속 143개 · 이 데이터셋의
     *    daisy는 `Leucanthemum`이 도감에 아예 없어서 12장이 여기로 온다).
     *    **B-4 유사종 통합과 도감 커버리지에 걸린 몫이다.**
     *
     * ⚠️ **통과선을 걸지 않는다.** 지금 이 숫자를 "합격"으로 못 박으면 B-3-b(난이도
     *    임계값)가 결정될 때 무엇이 좋아졌는지 알 수 없다. 대신 **값이 움직이면 알도록**
     *    현재값을 고정한다 — 임계값을 고치는 순간 이 테스트가 빨개져서
     *    "얼마나 좋아졌는지"를 숫자로 들고 오게 된다.
     */
    @Test
    fun `화면 12로 가는 비율을 기록한다`() = runBlocking {
        // 8월로 고정한다 — 오너가 실기기로 찍은 달이고, 제철 달로 재면
        // "필터가 떨군 것"이 클래스별로 흩어져 **한 사람이 겪는 실패율**이 안 보인다.
        val month = 8
        val candidates = candidatesFor(month)
        val flow = IdentifyFlow(FlowerRepository.forTest(flowers))

        var noCandidate = 0
        var belowFloor = 0
        var shown = 0

        for (photo in photos) {
            val result = recognizer(photo.body).identify(ByteArray(1), candidates)
            when {
                result.isEmpty() -> noCandidate++
                flow.decide(result) == IdentifyOutcome.Failed -> belowFloor++
                else -> shown++
            }
        }

        val failed = noCandidate + belowFloor
        println(
            "${month}월 화면 12 비율 — 후보0개 $noCandidate · floor미달 $belowFloor · " +
                "통과 $shown / ${photos.size} (실패 ${failed * 100 / photos.size}%)",
        )

        // 현재값 고정. 🔴 **이 숫자를 그냥 갱신하지 않는다** — 움직였다는 건
        //    판별 흐름이 달라진 것이고, (42)의 진단을 다시 해야 한다는 뜻이다.
        assertEquals("후보 0개 장수가 달라졌다 — 개화월 필터나 색인이 바뀌었다", 66, noCandidate)
        assertEquals(
            "floor 미달 장수가 달라졌다 — MIN_CONFIDENCE_FOR_ANY_CANDIDATE를 만졌는가 " +
                "(0.30이면 77 · **0.05면 42** · 0.10이면 47)",
            42, belowFloor,
        )
        assertEquals("화면 09로 가는 장수가 달라졌다", 92, shown)
    }

    /**
     * 🔴 **2,057종 확장이 판별을 어떻게 바꾸는가** — 앱이 실제로 싣는 도감으로 잰다.
     *
     * **왜 대조군과 따로 재는가.** 위 테스트들은 200종에서 iOS와의 일치를 본다.
     * 그런데 사용자가 쓰는 건 2,057종이고, 그쪽 숫자를 **아무도 재지 않으면**
     * "등록했다"가 "동작한다"로 읽힌다. 계약 1-2-b에 적은 값이 이 표다:
     *
     * | | Top-1 | Top-3 | 화면12 | 후보풀 |
     * |---|---|---|---|---|
     * | 대조군 200종 | 77.0% | 77.5% | 60 | 70 |
     * | **확장 2,057종** | **75.0%** | **83.0%** | **42** | 1,590 |
     *
     * **Top-1이 내려가는 것이 정상이다.** 후보가 70종에서 1,590종으로 늘어 신규종이
     * 기존종의 1순위 자리를 뺏는다. 대신 Top-3가 오르고 화면 12가 줄어든다 —
     * **사용자가 보는 결과는 나아진다.** 그래서 Top-1 하나로 판정하지 않는다.
     *
     * ⚠️ 여기서 재는 것은 **캐시 200장(흔한 5종)뿐**이다. 신규 1,857종의 판별 정확도는
     *    **측정되지 않았다** — 이 표본에 그 종의 사진이 아예 없다. 이 테스트가 초록인
     *    것은 "확장이 기존종을 해치지 않았다"까지만 뜻한다.
     */
    @Test
    fun `확장 2057종이 기존종 판별을 해치지 않는다`() = runBlocking {
        val fullIndex = ScientificNameIndex(flowersFull)
        fun candidates(month: Int) = flowersFull.filter { month in it.bloomMonths }.map { it.id }
        // 정답 인정 집합도 **확장 도감 기준으로 다시 만든다** — 같은 속의 신규종이
        // 1순위가 되는 경우가 있고, 그건 오답이 아니다(속 단위 인정은 iOS와 같은 규칙).
        val genera = fixture.getJSONObject("class_to_genera")
        val fullTruth = genera.keys().asSequence().associateWith { cls ->
            val list = genera.getJSONArray(cls)
            val names = (0 until list.length()).map { list.getString(it) }
            flowersFull.filter { it.scientificName.substringBefore(' ') in names }
                .map { it.id }.toSet()
        }

        var top1 = 0
        var top3 = 0
        for (photo in photos) {
            val month = peakMonth.getValue(photo.cls)
            val result = PlantNetRecognizer(
                index = fullIndex,
                apiKey = "replay",
                transport = object : PlantNetRecognizer.Transport {
                    override suspend fun post(url: String, contentType: String, b: ByteArray) =
                        200 to photo.body
                },
            ).identify(ByteArray(1), candidates(month))
            val ok = fullTruth.getValue(photo.cls)
            if (result.firstOrNull()?.flowerId in ok) top1++
            if (result.any { it.flowerId in ok }) top3++
        }

        val t1 = top1 * 100.0 / photos.size
        val t3 = top3 * 100.0 / photos.size
        println("확장 2,057종 — Top-1 ${"%.1f".format(t1)}% · Top-3 ${"%.1f".format(t3)}%")

        // 계약 1-2-b에 적은 값을 고정한다. 움직이면 cascade나 색인이 바뀐 것이다.
        assertEquals("확장 Top-1이 계약에 적은 75.0%와 다르다", 75.0, t1, 0.01)
        assertEquals("확장 Top-3이 계약에 적은 83.0%와 다르다", 83.0, t3, 0.01)
        // 🔴 **관계를 단정한다.** 숫자만 고정하면 "왜 이 값을 받아들였는가"가 사라진다.
        //    확장을 받아들인 근거는 Top-1 손실보다 Top-3 이득이 크다는 것이다.
        assertTrue("확장으로 Top-3이 대조군(77.5%)보다 나아지지 않았다면 확장 근거가 사라진다",
            t3 > 77.5)
    }

    /**
     * 🔴 **개화월 배열이 성립하는가** — 중복도 13개월도 없어야 한다.
     *
     * **이 검사가 실제 결함 9건을 잡았다.** `observed_run`이 양방향으로 각각 11칸을
     * 걸어서, 12달 전부에 관찰 기록이 있는 상록수(개산초·광나무·굴거리나무·꽝꽝나무·
     * 멀구슬나무·왕백량금·자금우·조록나무·팔손이)에 **23개월**을 줬다.
     *
     * ⚠️ **판별은 멀쩡했다.** 필터가 `month in bloomMonths`라서 중복이 있어도 옳게 돈다.
     *    드러난 자리는 문구였다 — `bloom_label`이 `months[0]`~`months[-1]`을 읽어
     *    **`6~4월`**을 만들고 화면 09에 `6~4월에 피는 꽃`으로 나갔다.
     *    즉 **판별 지표로는 절대 안 보이는 결함**이고, 개수를 세어서 잡았다.
     */
    @Test
    fun `개화월 배열에 중복도 13개월도 없다`() {
        val dup = flowersFull.filter { it.bloomMonths.size != it.bloomMonths.toSet().size }
        val over = flowersFull.filter { it.bloomMonths.size > 12 }
        assertTrue("개화월에 중복이 있는 종 ${dup.size}개: " +
            dup.take(5).joinToString { "${it.id} ${it.name} ${it.bloomMonths}" }, dup.isEmpty())
        assertTrue("개화월이 12개월을 넘는 종 ${over.size}개", over.isEmpty())
    }

    /**
     * 🔴 **개화월 하드 필터가 실제로 좁히는가** — 단, **근거 있는 종에서만 잰다.**
     *
     * 처음엔 "최대 후보풀이 전체의 절반 미만"으로 단정했는데 **빨개졌다**(6월 1,728종 =
     * 84%). 그런데 **그건 결함이 아니었다** — 근거 없는 1,017종을 전월 허용으로 둔 것이
     * 계약 1-2-b의 결정 자체다(좁히면 종을 지운다). 즉 **내가 잰 축이 틀렸다.**
     * 그 종들을 섞어 놓고 재면 필터가 도는지 안 도는지 **원리상 알 수 없다.**
     *
     * 그래서 근거 있는 1,040종(`human`·`draft`·`observed`)만 본다. 실측:
     *
     * | | 1월 | 6월 | 12월 |
     * |---|---|---|---|
     * | 근거 있는 1,040종 | 27 (2%) | 748 (**71%**) | 30 (3%) |
     * | 근거 약한 1,017종 | 759 (75%) | 980 (96%) | 656 (65%) |
     *
     * **근거 있는 쪽은 달에 따라 2%~71%로 움직인다** — 필터가 도는 증거다.
     * cascade가 망가져 대다수가 전월 허용이 되면 이 폭이 사라진다.
     */
    @Test
    fun `개화월 필터가 근거 있는 종을 실제로 좁힌다`() {
        // ⚠️ **표본을 `bloomSource`로 가른다.** 처음엔 "개화월 12개월 미만"으로 갈랐는데
        //    그러면 peak_window(9개월)가 근거 있는 쪽에 섞여 1,040종이어야 하는 표본이
        //    **1,770종**이 됐고, 폭이 3.6배로 줄어 단정이 빨개졌다 —
        //    코드가 아니라 **내가 다른 것을 재고 있었다.**
        val evidenced = flowersFull.filter {
            it.bloomSource in setOf(BloomSource.HUMAN, BloomSource.DRAFT, BloomSource.OBSERVED)
        }
        val pool = (1..12).map { m -> evidenced.count { m in it.bloomMonths } }
        val always = flowersFull.count { it.bloomMonths.size == 12 }
        println("근거 있는 ${evidenced.size}종 월별 후보풀 $pool · 전월 허용 $always 종")

        assertEquals("근거 있는 종이 1,040종(human 200 + draft 803 + observed 37)이 아니다",
            1040, evidenced.size)
        // 전월 허용 = ⑤ unknown 278 + 12달 전부에 관찰이 있는 상록수 9 = 287.
        assertEquals("전월 허용 종수가 달라졌다 — cascade ⑤나 observed_run이 바뀌었다",
            287, always)
        // 🔴 **폭을 단정한다.** 개수 하나를 고정하면 "필터가 도는가"를 못 재고,
        //    비율만 보면 우연히 통과한다. 겨울과 초여름의 차이가 필터의 본체다.
        //    실측: 1월 27종(2%) ~ 6월 748종(71%) = 27배.
        assertTrue("가장 좁은 달(${pool.min()}종)과 가장 넓은 달(${pool.max()}종)의 차이가 " +
            "10배 미만이다 — 개화월 필터가 사실상 꺼진 것이다", pool.max() > pool.min() * 10)
        assertTrue("겨울(1월 ${pool[0]}종)이 근거 있는 종의 10%를 넘는다", pool[0] < evidenced.size / 10)
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
