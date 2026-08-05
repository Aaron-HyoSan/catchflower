package com.catchflower.app.recognizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.FlowerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **실제 꽃 사진**으로 판별 흐름 전체를 태운다 (1차 필터 → 개화월 → 인식기 → 화면 분기).
 *
 * **왜 필요한가**: 에뮬레이터 카메라는 합성 도형만 비춘다. 그래서 화면 07에서 셔터를
 * 눌러 확인할 수 있는 경로는 **화면 12(1차 필터 거부)뿐이다.** 실제 꽃이 들어왔을 때
 * 09/09변형으로 갈라지는 경로는 손으로 확인할 방법이 없다.
 *
 * 사진은 1차 필터 실측용 데이터셋을 그대로 쓴다
 * (재생성: `python3 android/_tools/fetch_prefilter_dataset.py`).
 */
@RunWith(AndroidJUnit4::class)
class IdentifyPipelineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun photo(dir: String, name: String): ByteArray =
        assets.open("$dir/$name").use { it.readBytes() }

    private fun names(dir: String): List<String> =
        assets.list(dir)?.sorted().orEmpty()

    /**
     * 데이터셋이 실제로 붙어 있는지 먼저 본다.
     *
     * ⚠️ 에셋이 비면 아래 테스트들이 **0장을 돌고 통과한다.** 실측 단계에서
     *    한 번 당한 함정이라 여기서 막는다.
     */
    @Test
    fun 데이터셋이_붙어_있다() {
        assertEquals(
            "flower/ 500장이 아니다. fetch_prefilter_dataset.py를 돌린다",
            500,
            names("flower").size,
        )
    }

    /** 실제 꽃 사진이 1차 필터를 통과해야 유료 API까지 간다. */
    @Test
    fun 실제_꽃_사진은_1차_필터를_통과한다() = runBlocking {
        val preFilter = MlKitFlowerPreFilter()
        // 500장 전수는 실측 벤치마크가 이미 한다. 여기서는 흐름 검증이라 표본만 본다.
        val sample = names("flower").filterIndexed { i, _ -> i % 25 == 0 }
        var passed = 0
        sample.forEach { name ->
            if (preFilter.check(photo("flower", name)).isLikelyFlower) passed++
        }
        assertTrue(
            "표본 ${sample.size}장 중 ${passed}장만 통과했다. 정상 촬영이 막힌다",
            passed == sample.size,
        )
    }

    /**
     * 꽃 사진 → 화면 09(확정) 경로.
     *
     * Mock 기본 반환은 0.82/0.11/0.04이므로 [IdentifyOutcome.Confident]가 나와야 한다.
     */
    @Test
    fun 꽃_사진은_확정_화면으로_간다() = runBlocking {
        val outcome = runPipeline(photo("flower", names("flower").first()), debugLabel = null)
        assertTrue("화면 09가 아니라 $outcome 이 나왔다", outcome is IdentifyOutcome.Confident)

        val confident = outcome as IdentifyOutcome.Confident
        // 후보는 항상 3개다 (오너 결정 B-3). 1순위 + 대안 2개.
        assertEquals(
            "후보가 3개가 아니다 — B-3 '항상 후보 3개' 위반",
            GamePolicy.CANDIDATE_COUNT,
            1 + confident.alternatives.size,
        )
        assertEquals(1, confident.top.rank)
    }

    /** `low` 라벨 → 화면 09 변형(후보 3개 동일 크기). */
    @Test
    fun 점수가_낮으면_후보_3개를_보여준다() = runBlocking {
        val outcome = runPipeline(photo("flower", names("flower").first()), debugLabel = "low")
        assertTrue("화면 09 변형이 아니라 $outcome 이 나왔다", outcome is IdentifyOutcome.Ambiguous)
        assertEquals(
            GamePolicy.CANDIDATE_COUNT,
            (outcome as IdentifyOutcome.Ambiguous).candidates.size,
        )
    }

    /** `fail` 라벨 → 화면 12. */
    @Test
    fun 인식_실패는_화면_12로_간다() = runBlocking {
        val outcome = runPipeline(photo("flower", names("flower").first()), debugLabel = "fail")
        assertEquals(IdentifyOutcome.Failed, outcome)
    }

    /**
     * 비꽃 사진은 **유료 API를 부르지 않고** 막힌다.
     *
     * 이게 비용 문서 4절 절감 장치 ②의 핵심이다. 인식기가 호출되면 실패다.
     */
    @Test
    fun 비꽃_사진은_인식기를_부르지_않는다() = runBlocking {
        val preFilter = MlKitFlowerPreFilter()
        var recognizerCalls = 0
        val counting = object : FlowerRecognizer {
            override suspend fun identify(
                image: ByteArray,
                candidates: List<Int>,
                debugLabel: String?,
            ): List<Candidate> {
                recognizerCalls++
                return emptyList()
            }
        }

        val sample = names("notflower").filterIndexed { i, _ -> i % 25 == 0 }
        var blocked = 0
        sample.forEach { name ->
            val jpeg = photo("notflower", name)
            if (preFilter.check(jpeg).isLikelyFlower) {
                counting.identify(jpeg, listOf(1), null)
            } else {
                blocked++
            }
        }

        // 차단율 92.4% 실측이므로 표본에서도 대부분 막혀야 한다.
        assertTrue(
            "표본 ${sample.size}장 중 ${blocked}장만 막혔다 (유료 호출 ${recognizerCalls}건). " +
                "차단율 설계가 무너졌다",
            blocked >= sample.size * 0.85,
        )
    }

    /**
     * 개화월 하드 필터가 실제로 후보를 줄인다 (A-1 필수 구현).
     *
     * ⚠️ 이게 비면 11월에 벚꽃이 후보로 온다.
     */
    @Test
    fun 개화월_후보는_전체보다_적다() {
        val flow = IdentifyFlow(FlowerRepository.get(context))
        val total = FlowerRepository.get(context).flowers.size
        (1..12).forEach { month ->
            val candidates = flow.candidatesForMonth(month)
            assertTrue("${month}월 후보가 비었다", candidates.isNotEmpty())
            assertTrue(
                "${month}월 후보가 전체(${total})와 같다 — 개화월 필터가 동작하지 않는다",
                candidates.size < total,
            )
        }
    }

    /** 1차 필터 → 개화월 → 인식기 → 분기. `CaptureViewModel.analyze`와 같은 순서다. */
    private suspend fun runPipeline(jpeg: ByteArray, debugLabel: String?): IdentifyOutcome {
        val repository = FlowerRepository.get(context)
        val flow = IdentifyFlow(repository)

        if (!MlKitFlowerPreFilter().check(jpeg).isLikelyFlower) return IdentifyOutcome.Failed

        // 월에 따라 후보가 바뀌면 테스트가 계절마다 다르게 돈다. 봄(4월)으로 고정한다.
        val candidates = flow.candidatesForMonth(4)
        val result = MockFlowerRecognizer(delayMillis = 0L)
            .identify(jpeg, candidates, debugLabel)
        return flow.decide(result)
    }
}
