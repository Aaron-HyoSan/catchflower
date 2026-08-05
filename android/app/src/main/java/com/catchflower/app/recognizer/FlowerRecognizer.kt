package com.catchflower.app.recognizer

/**
 * 꽃 인식기. **이게 A-1을 블로커에서 빼준 장치다** — 벤더(PlantNet)를 이 뒤에 숨긴다.
 *
 * 공유계약 2절이 형태를 고정했다:
 *     identify(image, candidates: [FlowerId]) -> [Candidate(flowerId, score)]
 *
 * | 규칙 | 내용 |
 * |---|---|
 * | 후보는 항상 3개 | B-3 권고 ③. 3개 미만이면 있는 만큼 |
 * | score는 0.0~1.0 | PlantNet 보정 확률. 퍼센트로 곱하지 않는다 |
 * | **개화월 하드 필터는 서버가 적용한 뒤 내려준다** | 클라이언트가 필터하지 않는다 |
 *
 * 마지막 줄이 중요하다. [candidates]는 "이 중에서 골라라"가 아니라
 * **서버가 이미 개화월로 좁힌 후보 집합**이다. 클라이언트가 다시 필터하면
 * iOS와 Android가 다른 결과를 낼 수 있다 (공유계약 1-2와 같은 이유).
 */
interface FlowerRecognizer {

    /**
     * @param image JPEG 바이트. 장변 [com.catchflower.app.core.GamePolicy.PHOTO_LONG_EDGE_PX]로 압축된 것.
     * @param candidates 서버가 개화월 하드 필터를 적용한 후보 flower id 집합.
     * @param debugLabel Mock의 분기용 파일명. 실구현은 무시한다.
     * @return 점수 내림차순 후보. **빈 목록이면 판별 실패(화면 12)** 다.
     */
    suspend fun identify(
        image: ByteArray,
        candidates: List<Int>,
        debugLabel: String? = null,
    ): List<Candidate>
}

/**
 * 인식 후보 1개.
 *
 * @param flowerId flowers.id
 * @param score 0.0~1.0. PlantNet의 보정된 확률이라 그대로 쓴다.
 */
data class Candidate(val flowerId: Int, val score: Float) {
    init {
        require(score in 0f..1f) { "score는 0.0~1.0이다. 받은 값 $score" }
    }
}
