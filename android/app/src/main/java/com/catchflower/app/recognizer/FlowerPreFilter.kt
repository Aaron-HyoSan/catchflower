package com.catchflower.app.recognizer

/**
 * 온디바이스 '꽃 여부' 1차 필터.
 *
 * **비용 문서 4절의 절감 장치 ②다.** 신발·하늘·사람 사진에 유료 API를 부르지 않는다.
 * iOS는 Vision 프레임워크를 쓰고, Android는 ML Kit Image Labeling을 쓴다 —
 * **둘 다 온디바이스·무료**여야 이 절감이 성립한다.
 *
 * ⚠️ 이 필터가 틀리는 두 방향은 **값이 전혀 다르다.**
 * - 꽃을 아니라고 하면(FN) → 사용자가 못 찍는다. **기능이 망가진다.**
 * - 꽃 아닌 걸 맞다고 하면(FP) → API를 한 번 헛부른다. **돈만 조금 쓴다.**
 *
 * 그래서 임계값은 **FN을 최소화하는 쪽**으로 잡는다. 절감률을 몇 %p 얻으려고
 * 꽃 사진을 막는 건 남는 장사가 아니다.
 */
interface FlowerPreFilter {

    /**
     * @param jpeg 촬영 사진 (JPEG 바이트)
     * @return 판정 결과. [PreFilterResult.isLikelyFlower]가 false면 유료 API를 부르지 않는다.
     */
    suspend fun check(jpeg: ByteArray): PreFilterResult
}

/**
 * @param isLikelyFlower 유료 API를 부를지 여부
 * @param topLabel 가장 점수가 높은 라벨 (디버깅·측정용)
 * @param matchedLabel 꽃으로 판정한 근거 라벨. 판정이 false면 null
 * @param confidence 근거 라벨의 점수
 * @param elapsedMillis 추론 소요 시간. 화면 08의 "5초 정도 걸려요"에 이 시간이 더해진다
 */
data class PreFilterResult(
    val isLikelyFlower: Boolean,
    val topLabel: String?,
    val matchedLabel: String?,
    val confidence: Float,
    val elapsedMillis: Long,
)

/**
 * 항상 통과시키는 필터.
 *
 * 필터를 못 쓰게 되거나(기기 미지원 등) 측정 전에는 이걸 쓴다.
 * **막는 쪽으로 기본값을 두지 않는다** — 필터가 고장 나면 앱이 꽃을 못 받는 게 아니라
 * API 비용이 좀 더 드는 쪽으로 실패해야 한다.
 */
object AlwaysPassPreFilter : FlowerPreFilter {
    override suspend fun check(jpeg: ByteArray) = PreFilterResult(
        isLikelyFlower = true,
        topLabel = null,
        matchedLabel = null,
        confidence = 1f,
        elapsedMillis = 0L,
    )
}
