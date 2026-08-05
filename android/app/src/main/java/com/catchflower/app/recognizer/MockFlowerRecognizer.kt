package com.catchflower.app.recognizer

import kotlinx.coroutines.delay

/**
 * Mock 인식기. **PlantNet 키 없이 화면 07~13 전 흐름을 검증하는 장치다.**
 *
 * ⚠️ 반환값은 공유계약 2절이 **못 박아 둔 것**이다. iOS와 Android가 다른 Mock을
 *    만들면 흐름 검증이 안 된다. 이 표를 혼자 바꾸지 않는다.
 *
 * | Mock 입력 | 반환 | 검증되는 경로 |
 * |---|---|---|
 * | 기본 | 0.82 / 0.11 / 0.04 | 화면 09 확정 |
 * | 파일명에 `low` | 0.55 / 0.30 / 0.12 | 후보 3개 동일 크기 |
 * | 파일명에 `fail` | 빈 배열 | 화면 12 판별 실패 |
 */
class MockFlowerRecognizer(
    /** 화면 08의 "5초 정도 걸려요"를 실제로 겪어보기 위한 지연. */
    private val delayMillis: Long = 1_500L,
) : FlowerRecognizer {

    override suspend fun identify(
        image: ByteArray,
        candidates: List<Int>,
        debugLabel: String?,
    ): List<Candidate> {
        delay(delayMillis)

        val label = debugLabel?.lowercase().orEmpty()

        // 화면 12 — 판별 실패
        if (label.contains("fail")) return emptyList()

        if (candidates.isEmpty()) return emptyList()

        // 화면 09 변형 — 후보 3개를 같은 크기로 ("어느 꽃인가요?")
        val scores = if (label.contains("low")) {
            listOf(0.55f, 0.30f, 0.12f)
        } else {
            // 화면 09 확정 경로
            listOf(0.82f, 0.11f, 0.04f)
        }

        // 후보 집합에서 앞에서부터 고른다. 후보가 3개 미만이면 있는 만큼만 (계약).
        return candidates.take(scores.size).mapIndexed { index, flowerId ->
            Candidate(flowerId = flowerId, score = scores[index])
        }
    }
}
