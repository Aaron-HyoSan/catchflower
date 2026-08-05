package com.catchflower.app.recognizer

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Image Labeling 기반 1차 필터 (Android 전용).
 *
 * iOS의 Vision 프레임워크에 대응하는 자리다. 조건:
 * - **온디바이스·무료** (번들 모델. 네트워크·과금 없음)
 * - minSdk 26에서 동작
 * - APK 증가분이 감당 가능
 *
 * ⚠️ ML Kit 기본 모델은 **약 400개 일반 라벨**만 안다. 종을 식별하지 못한다 —
 *    `Flower`, `Plant` 같은 범주 라벨만 준다. 그게 이 자리에 필요한 전부다.
 *    종 식별은 PlantNet이 한다.
 *
 * **설정값은 실측으로 정했다** (`PreFilterBenchmark`, 사진 1,000장, Pixel 8 API 36).
 * 처음엔 `Plant`·`Leaf`·`Garden`을 넣어 뒀는데 — "꽃 근접 촬영에서 Flower보다 Plant가
 * 높게 나올 것"이라는 **추측**이었다 — 측정 결과 정반대였다:
 *
 * | 라벨셋 (th=0.30) | 재현율 | 차단율 |
 * |---|---|---|
 * | Flower·Petal·Blossom | **99.2%** | **96.6%** |
 * | + Plant | 98.8% | 62.8% |
 *
 * `Plant`는 꽃아님 사진 **489/500장**에도 붙는다 (물고기·개·기계톱 배경의 초목).
 * 넣으면 차단율이 34%p 무너지는데 재현율은 오히려 안 오른다.
 * 되찾아 오는 사진들은 전부 데이터셋 오라벨(행진 악대·찻잔)이었다.
 * `Petal`·`Blossom`은 이 데이터셋에서 **차이가 없었다** — 근접 촬영 보험으로 남겨 둔다
 * (차단율 손실 0.2%p 이하).
 */
class MlKitFlowerPreFilter(
    /**
     * 근거 라벨의 최소 점수.
     *
     * 낮게 잡는다. 이 필터의 목적은 "꽃을 정확히 맞히기"가 아니라
     * **"확실히 꽃이 아닌 것만 걷어내기"** 다.
     */
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val labeler: ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            // ML Kit 기본값 0.5보다 낮춰서 받는다. 걸러내는 판단은 우리가 한다.
            .setConfidenceThreshold(0.1f)
            .build()
    ),
) : FlowerPreFilter {

    override suspend fun check(jpeg: ByteArray): PreFilterResult {
        val started = System.nanoTime()
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: return PreFilterResult(
                // 디코딩 실패는 필터의 판단 대상이 아니다. 막지 않는다.
                isLikelyFlower = true,
                topLabel = null,
                matchedLabel = null,
                confidence = 0f,
                elapsedMillis = 0L,
            )

        val labels = suspendCancellableCoroutine { cont ->
            labeler.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000

        val matched = labels
            .filter { it.text in FLOWER_LABELS && it.confidence >= threshold }
            .maxByOrNull { it.confidence }

        return PreFilterResult(
            isLikelyFlower = matched != null,
            topLabel = labels.maxByOrNull { it.confidence }?.text,
            matchedLabel = matched?.text,
            confidence = matched?.confidence ?: 0f,
            elapsedMillis = elapsed,
        )
    }

    companion object {
        /**
         * 실측으로 정한 임계값.
         *
         * | th | 재현율 | 차단율 | 비꽃 20% 가정 시 절감 |
         * |---|---|---|---|
         * | **0.20** | **100.0%** | **92.4%** | **18.5%** |
         * | 0.30 | 99.2% | 96.6% | 19.3% |
         * | 0.50 | 98.3% | 99.2% | 19.8% |
         * | 0.70 | 95.5% | 99.8% | 20.0% |
         *
         * **0.20을 고른 이유** — DAU 1,000명(월 31,338 호출) 기준으로 환산하면:
         * - th=0.20 → 헛호출 476건/월, 막힌 정상 촬영 **0건**
         * - th=0.30 → 헛호출 213건/월, 막힌 정상 촬영 **201건**
         *
         * 263건 아끼려고 201명에게 "꽃이 아닐 수도 있어요"(화면 12, 3회 연속 실패 문구)를
         * 보여주는 거래다. 손에 진짜 꽃을 들고 있는 사람에게. 둘 다 돈으로는 몇백 원인데
         * 한쪽은 기능이 망가진다.
         *
         * 0.30 이상에서 더 막히는 사진을 전부 열어봤다 — 역광 민들레 홀씨, 흑백 접사,
         * `Flower 0.28`로 간신히 못 넘긴 근접 촬영. **우리 사용자가 실제로 찍을 사진들이다.**
         */
        const val DEFAULT_THRESHOLD = 0.20f

        /**
         * 꽃으로 인정할 라벨.
         *
         * ML Kit 기본 모델의 라벨 이름은 영어로 고정이다 (기기 언어와 무관).
         * **`Plant`를 넣지 않는다** — 이유는 클래스 주석의 표를 본다.
         */
        val FLOWER_LABELS = setOf("Flower", "Petal", "Blossom")
    }
}
