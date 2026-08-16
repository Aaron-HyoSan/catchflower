package com.catchflower.app.recognizer

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 어떤 판별 결과가 **비로그인 2회를 깎는가** (오너 결정 2026-08-14 · 공유계약 3절).
 *
 * ## 🔴 왜 이 파일이 있어야 하는가
 *
 * 오너 원문은 `여기 판별은 성공이라고 하면 될듯하다` 한 줄이고, 그 "성공"의 정의가
 * 한도 2회의 **절반**이다. 정의가 흔들리면:
 *
 * - 실패(화면 12)까지 세면 → **꽃을 한 송이도 못 모은 사람이 로그인 화면을 본다.**
 *   PlantNet floor는 0.05라 실패가 드물지 않다.
 * - 성공을 안 세면 → **무료 판별이 무한이다.**
 *
 * 🔴 **둘 다 화면에 증상이 없다.** 판별은 정상으로 돌고, 시트가 뜨는 시점만 다르다.
 *
 * ## ⚠️ 네트워크 실패·일일 한도는 여기에 오지 않는다
 *
 * 그 경로는 [IdentifyOutcome]을 **만들지 않는다**(토스트로 끝난다). 즉 "안 센다"가
 * 코드 구조로 지켜지고 있어서 **이 파일이 잴 수 있는 대상이 아니다.** 여기서
 * `assertFalse`로 흉내를 내면 지키지 않는 것을 지킨다고 적는 셈이라 두지 않았다.
 * 🔴 그 경로가 나중에 `Failed`로 뭉개져 이 함수를 지나게 되면 아래 검사가 그때
 *    **여전히 옳다**(❌) — 안전한 방향이다.
 */
class IdentifySuccessCountTest {

    // flowers.json 실제 값. 지어낸 종으로 재면 "우리 데이터에서" 확인한 것이 아니다.
    private val 민들레 = Flower(
        id = 31,
        name = "민들레",
        scientificName = "Taraxacum platycarpum",
        scientificAliases = emptyList(),
        family = "국화과",
        bloomMonths = listOf(3, 4, 5),
        bloomLabel = "3~5월",
        bloomSource = BloomSource.HUMAN,
        season = Season.SPRING,
        color = "노랑",
        rarity = Rarity.COMMON,
        habitat = "길가",
        aiDifficulty = AiDifficulty.LOW,
        similarFlowerIds = emptyList(),
        similarFlowerNames = emptyList(),
        // 31 민들레는 **대표종**이다(계약 1-6). 자기 id를 넣는다.
        collectGroupId = 31,
        illustBatch = 1,
    )

    private fun candidate(rank: Int, score: Float) =
        RankedCandidate(rank = rank, flower = 민들레, score = score)

    /** 화면 09 — 1순위를 확실하게 줬다. 앱이 할 일을 다 했다. */
    @Test
    fun 확실한_결과는_횟수를_깎는다() {
        val outcome = IdentifyOutcome.Confident(
            top = candidate(1, 0.71f),
            alternatives = listOf(candidate(2, 0.12f)),
        )
        assertTrue("화면 09까지 갔는데 횟수가 안 줄어든다 — 무료 판별이 무한이 된다", outcome.countsAsSuccess)
    }

    /**
     * 화면 09 변형 — 후보 3개를 줬다.
     *
     * ⚠️ **여기가 `false`이면 사실상 게이트가 없다.** PlantNet 점수는 4,932종에 퍼진
     *    확률이라 정답의 흔한 값이 0.1 근처이고(`plantnet-score-is-not-confidence`),
     *    그래서 `Ambiguous`가 **드문 경로가 아니라 흔한 경로**다.
     */
    @Test
    fun 후보_여러_개도_횟수를_깎는다() {
        val outcome = IdentifyOutcome.Ambiguous(
            candidates = listOf(candidate(1, 0.18f), candidate(2, 0.14f), candidate(3, 0.09f)),
        )
        assertTrue("후보를 보여줬는데 횟수가 안 줄어든다", outcome.countsAsSuccess)
    }

    /**
     * 화면 12 — floor 미달.
     *
     * 🔴 **우리가 못 맞힌 것이다.** 이걸 세면 꽃을 한 송이도 못 모은 사람이 3회째에
     *    로그인 시트를 본다 — 준 것이 없는데 대가를 요구하는 화면이 된다.
     */
    @Test
    fun 판별_실패는_횟수를_깎지_않는다() {
        assertFalse("화면 12(못 맞힘)가 횟수를 깎는다", IdentifyOutcome.Failed.countsAsSuccess)
    }

    /**
     * ⚠️ **결과 타입이 세 개인지** 고정한다.
     *
     * `countsAsSuccess`의 `when`은 `else`가 없어서 새 타입이 생기면 컴파일이 깨진다 —
     * 컴파일을 통과시키려고 새 타입을 `true`나 `false` 아무 쪽에 붙일 수 있는데,
     * 이 검사가 그때 **"둘 중 어디에 속하는지 결정하라"**고 다시 세운다.
     *
     * ⚠️ **`sealedSubclasses`(코틀린 리플렉션)를 쓰지 않는다.** 이 모듈에는
     *    `kotlin-reflect.jar`가 없어서 `KotlinReflectionNotSupportedError`로 죽는다
     *    (실측했다). 자바 `declaredClasses`는 중첩 클래스를 그대로 준다 —
     *    `IdentifyOutcome`의 세 타입이 전부 중첩이라 같은 것을 센다.
     */
    @Test
    fun 판별_결과는_세_종류다() {
        val known = setOf("Confident", "Ambiguous", "Failed")
        val actual = IdentifyOutcome::class.java.declaredClasses.map { it.simpleName }.toSet()
        assertEquals(
            "새 결과 타입이 생겼다 — countsAsSuccess에서 세는지 안 세는지 결정하고 " +
                "공유계약 3절 `판별 성공` 정의도 같이 고친다",
            known,
            actual,
        )
    }
}
