package com.catchflower.app.recognizer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Image Labeling 1차 필터 실측.
 *
 * **AOS 전용 숙제** (세션시작_AOS.md): ML Kit이 iOS Vision을 대체해
 * 무료 온디바이스 '꽃 여부' 필터로 쓸 수 있는지 확인한다.
 * 실패하면 비용 문서의 **절감률 48% / "DAU 135명까지 0원"** 계산이
 * Android에서 깨진다.
 *
 * ⚠️ **문서를 읽고 판단하지 않는다.** 실제 사진으로 돌린다.
 *
 * 데이터셋 — `app/src/androidTest/assets/` (테스트 APK에 들어간다):
 * - `flower/` 500장 — TensorFlow flowers (daisy·dandelion·roses·sunflowers·tulips 각 100)
 * - `notflower/` 500장 — Imagenette val 10클래스 각 50 (물고기·개·카세트·기계톱·교회·
 *   프렌치호른·쓰레기차·주유펌프·골프공·낙하산)
 *
 * ⚠️ 사진 1,000장 때문에 테스트 APK가 45MB 늘어난다. **운영 APK에는 영향 없다**
 *    (androidTest 소스셋). 자산은 **커밋하지 않는다** (.gitignore).
 *    없으면 먼저 만든다:
 *
 *      python3 android/_tools/fetch_prefilter_dataset.py
 *
 *    시드가 고정이라 같은 사진 1,000장이 나온다 — 그래서 아래 숫자와 비교가 된다.
 *
 * 실행:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.catchflower.app.recognizer.PreFilterBenchmark
 *   adb logcat -d | grep CfBench
 */
@RunWith(AndroidJUnit4::class)
class PreFilterBenchmark {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.05f).build()
    )

    /**
     * **육안 감사 결과.** `flower/` 500장 중 실제로 꽃이 아닌 사진들이다
     * (TF flowers는 Flickr 태그로 모은 데이터셋이라 라벨 노이즈가 있다).
     *
     * 1차 실행에서 "놓친 꽃 20장"을 전부 열어보니 행진 악대·`Roses` 간판·찻잔·
     * Jeff Koons 풍선·벽돌 건물이었다. **ML Kit이 맞고 데이터셋이 틀렸다.**
     *
     * ⚠️ 이걸 빼지 않으면 재현율이 실제보다 낮게 나오고, 그걸 보고 임계값을
     *    낮추게 된다 → 차단율을 버려 비용 절감이 무너진다.
     *    즉 **데이터셋 노이즈가 잘못된 설정을 유도한다.**
     */
    private val mislabeledNonFlowers = setOf(
        "roses_11694025703_9a906fedc1_n.jpg",   // 퍼레이드 꽃수레
        "roses_15750320284_22ef21c682.jpg",     // 행진 악대 (배경 현수막에 꽃 그림)
        "roses_16424992340_c1d9eb72b4.jpg",     // 바다·보트
        "roses_2408236801_f43c6bcff2.jpg",      // 매달린 찻잔
        "roses_2863863372_605e29c03e_m.jpg",    // 벽돌 건물
        "roses_5148639829_781eb7d346.jpg",      // 빛바랜 담벼락
        "roses_8590442797_07fa2141c0_n.jpg",    // `Roses` 상점 간판
        "sunflowers_7270375648_79f0caef42_n.jpg", // 야간 조명 오브제
        "tulips_13910131718_731353d84c_n.jpg",  // 흑백 인물
        "tulips_14097328354_4f1469a170.jpg",    // 새
        "tulips_17202535346_ab828e779b.jpg",    // 흑백 나무
        "tulips_3433265727_0b8022e091.jpg",     // 금속 풍선 조형물
        "tulips_4522153453_06437ca3af_m.jpg",   // 야외에서 그림 그리는 사람
        "tulips_7266196114_c2a736a15a_m.jpg",   // 공원 잔디밭
        // 아래 둘은 '꽃 그림/디지털 아트'다. 실사가 아니라 실사용 입력으로 보기 어렵다.
        "sunflowers_7012366081_019c8a17a4_m.jpg",
        "tulips_5433747333_869a2a172d_m.jpg",
    )

    private val assets get() =
        InstrumentationRegistry.getInstrumentation().context.assets

    private suspend fun labelsOf(path: String): List<ImageLabel> {
        val bitmap = assets.open(path).use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return emptyList()
        return suspendCancellableCoroutine { cont ->
            labeler.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    @Test
    fun measure() = runBlocking {
        // ⚠️ 사진을 sdcard에서 읽으려는 시도는 전부 막혔다 (기록으로 남긴다):
        //    - `/sdcard/cf_bench` → Scoped storage. exists()는 true인데 listFiles()가
        //      **빈 목록**을 준다. 조용히 0장으로 돌아 "측정 완료"처럼 보인다
        //    - 앱 전용 외부 디렉터리 → `connectedAndroidTest`가 앱을 지울 때 같이 지워진다
        //    - run-as + 내부 저장소 → 에뮬레이터에서 files/ 접근이 Permission denied
        //    그래서 **테스트 APK의 assets에 넣는다.** 권한이 필요 없고, APK가 곧
        //    데이터셋이라 다른 기기에서도 같은 입력으로 재현된다.
        val positives = assets.list("flower")?.sorted().orEmpty().map { "flower/$it" }
        val negatives = assets.list("notflower")?.sorted().orEmpty().map { "notflower/$it" }
        check(positives.isNotEmpty() && negatives.isNotEmpty()) {
            "데이터셋이 없다. app/src/androidTest/assets/{flower,notflower}/ 를 채운다."
        }
        log("데이터셋 · 꽃 ${positives.size}장 / 꽃아님 ${negatives.size}장")

        // 1회 워밍업 — 첫 추론은 모델 로딩이 섞여 있어 지연 통계를 오염시킨다.
        positives.firstOrNull()?.let { labelsOf(it) }

        // 사진마다 라벨 전체를 한 번만 받아 두고, 임계값·라벨셋은 그 위에서 계산한다.
        // 임계값별로 다시 추론하면 20배 오래 걸린다.
        val posLabelsRaw = positives.map { it to labelsOf(it) }
        val negLabels = negatives.map { it to labelsOf(it) }

        // 오라벨 사진을 뺀 뒤 재현율을 센다 (근거는 [mislabeledNonFlowers] 주석).
        val posLabels = posLabelsRaw.filterNot { (name, _) ->
            name.removePrefix("flower/") in mislabeledNonFlowers
        }
        log("꽃 사진 ${posLabelsRaw.size}장 중 오라벨 ${posLabelsRaw.size - posLabels.size}장 제외 → ${posLabels.size}장으로 재현율 계산")

        // --- 지연 ---
        val timings = ArrayList<Long>(120)
        positives.take(100).forEach {
            val t = System.nanoTime()
            labelsOf(it)
            timings += (System.nanoTime() - t) / 1_000_000
        }
        val sorted = timings.sorted()
        log(
            "지연(100장) · 중앙값 ${sorted[sorted.size / 2]}ms · " +
                "p90 ${sorted[(sorted.size * 0.9).toInt()]}ms · 최대 ${sorted.last()}ms"
        )

        // --- 어떤 라벨이 실제로 나오는가 ---
        // FLOWER_LABELS를 문서 추정으로 정하면 안 된다. 실제 분포를 본다.
        val posTop = posLabels.flatMap { (_, ls) -> ls.map { it.text } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(15)
        log("꽃 사진에서 나온 라벨 상위 15: " + posTop.joinToString { "${it.key}=${it.value}" })

        val negTop = negLabels.flatMap { (_, ls) -> ls.map { it.text } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(15)
        log("꽃아님 사진에서 나온 라벨 상위 15: " + negTop.joinToString { "${it.key}=${it.value}" })

        // --- 라벨셋 · 임계값 스윕 ---
        // Plant가 꽃아님 사진에도 대량으로 나온다는 걸 1차 실행에서 확인했다.
        // 그래서 "Plant를 넣을지"가 이 측정의 핵심 질문이다 — 후보를 여러 개 같이 잰다.
        val labelSets = linkedMapOf(
            "Flower만" to setOf("Flower"),
            "Flower+Petal+Blossom" to setOf("Flower", "Petal", "Blossom"),
            "Flower+Plant" to setOf("Flower", "Plant"),
            "운영설정" to MlKitFlowerPreFilter.FLOWER_LABELS,
        )
        val thresholds = listOf(0.10f, 0.20f, 0.30f, 0.40f, 0.50f, 0.70f)

        log("--- 라벨셋 × 임계값 (재현율=꽃을 통과시킨 비율, 차단율=꽃아님을 막은 비율) ---")
        for ((setName, set) in labelSets) {
            for (th in thresholds) {
                val recall = posLabels.count { (_, ls) -> ls.any { it.text in set && it.confidence >= th } }
                val blocked = negLabels.count { (_, ls) -> ls.none { it.text in set && it.confidence >= th } }
                val recallPct = recall * 100.0 / posLabels.size
                val blockPct = blocked * 100.0 / negLabels.size
                log(
                    "$setName th=%.2f · 재현율 %.1f%% (놓친 꽃 %d장) · 차단율 %.1f%%"
                        .format(th, recallPct, posLabels.size - recall, blockPct)
                )
            }
        }

        // --- 2단 규칙: Flower는 낮은 임계값, Plant는 높은 임계값 ---
        // Plant는 꽃아님에도 흔하지만 **점수가 낮다면** 꽃아님일 가능성이 높다.
        // 근접 촬영(Plant가 Flower보다 높게 나오는 경우)을 살리면서 차단율을 지키는지 본다.
        log("--- 2단 규칙 (Flower th=0.10 OR Plant th=X) ---")
        for (plantTh in listOf(0.50f, 0.70f, 0.80f, 0.90f)) {
            fun pass(ls: List<ImageLabel>) = ls.any {
                (it.text == "Flower" && it.confidence >= 0.10f) ||
                    (it.text == "Plant" && it.confidence >= plantTh)
            }
            val recall = posLabels.count { (_, ls) -> pass(ls) }
            val blocked = negLabels.count { (_, ls) -> !pass(ls) }
            log(
                "Plant th=%.2f · 재현율 %.1f%% (놓친 꽃 %d장) · 차단율 %.1f%%".format(
                    plantTh,
                    recall * 100.0 / posLabels.size,
                    posLabels.size - recall,
                    blocked * 100.0 / negLabels.size,
                )
            )
        }

        // --- 비용 문서 가정 검증 ---
        // 문서는 "꽃 아닌 사진이 20% 들어온다"고 가정하고 그만큼 절약된다고 봤다.
        // 실제 절감률 = 차단율 × 0.20 (꽃 사진은 어차피 호출해야 한다)
        val opSet = MlKitFlowerPreFilter.FLOWER_LABELS
        val th = MlKitFlowerPreFilter.DEFAULT_THRESHOLD
        val recallOp = posLabels.count { (_, ls) -> ls.any { it.text in opSet && it.confidence >= th } }
        val blockedOp = negLabels.count { (_, ls) -> ls.none { it.text in opSet && it.confidence >= th } }
        val fnCount = posLabels.size - recallOp
        // ⚠️ format 문자열에 리터럴 %를 쓸 때는 %%로 이스케이프해야 한다.
        //    안 하면 UnknownFormatConversionException으로 **측정 마지막 단계만** 죽는다.
        val recallPctOp = recallOp * 100.0 / posLabels.size
        val blockPctOp = blockedOp * 100.0 / negLabels.size
        log("--- 운영 설정 (%s · th=%.2f) ---".format(opSet.sorted().joinToString("+"), th))
        log("재현율 %.1f%% · 놓친 꽃 %d장 (기능 손실)".format(recallPctOp, fnCount))
        log(
            "차단율 %.1f%% · 비꽃 입력 20%% 가정 시 호출 절감 %.1f%%"
                .format(blockPctOp, blockPctOp * 0.20)
        )

        // 놓친 꽃 사진의 라벨을 남긴다 — 라벨셋을 넓혀 고칠 수 있는지 판단하려면 필요하다.
        posLabels.filterNot { (_, ls) -> ls.any { it.text in opSet && it.confidence >= th } }
            .take(15)
            .forEach { (name, ls) ->
                log("놓침: $name → " + ls.take(4).joinToString { "%s %.2f".format(it.text, it.confidence) })
            }

        // --- 후보 임계값별 놓친 파일 전체 목록 ---
        // ⚠️ 재현율만 보고 임계값을 정하면 안 된다. 1차 실행에서 "놓친 꽃"을 눈으로 열어보니
        //    행진 악대·찻잔 사진이 섞여 있었다 — **데이터셋 라벨 노이즈**이고 필터는 옳았다.
        //    그래서 파일명을 전부 남겨 육안 검증을 한다. 놓침이 진짜 꽃일 때만 FN으로 센다.
        for (t in listOf(0.30f, 0.50f, 0.70f)) {
            val missed = posLabels
                .filterNot { (_, ls) -> ls.any { it.text == "Flower" && it.confidence >= t } }
                .map { (name, _) -> name.removePrefix("flower/") }
            log("Flower만 th=%.2f 놓침 %d장: %s".format(t, missed.size, missed.joinToString(",")))
        }

        // 꽃아님인데 통과한 사진 (FP) — 어떤 종류가 새는지 본다. 돈만 조금 쓰는 오류다.
        for (t in listOf(0.50f, 0.70f)) {
            val leaked = negLabels
                .filter { (_, ls) -> ls.any { it.text == "Flower" && it.confidence >= t } }
                .map { (name, _) -> name.removePrefix("notflower/") }
            log("Flower만 th=%.2f 통과(FP) %d장: %s".format(t, leaked.size, leaked.joinToString(",")))
        }

        // --- 회귀 가드 ---
        // 여기까지는 출력만 하는 측정이었다. 출력만 하면 나중에 라벨셋·임계값을 건드려도
        // 테스트는 계속 통과한다. **운영 설정이 합격선을 지키는지 단정한다.**
        // 합격선 근거: FN은 기능 손실(사용자가 꽃을 못 찍는다), FP는 돈 조금.
        // → 재현율을 먼저 지키고, 차단율은 그 다음이다.
        assertTrue(
            "운영 설정 재현율 %.1f%% — 99%% 미만이면 정상 촬영이 막힌다".format(recallPctOp),
            recallPctOp >= 99.0,
        )
        assertTrue(
            "운영 설정 차단율 %.1f%% — 90%% 미만이면 비용 절감 설계가 성립하지 않는다".format(blockPctOp),
            blockPctOp >= 90.0,
        )
        assertTrue(
            "1차 필터 지연 중앙값 ${sorted[sorted.size / 2]}ms — 200ms를 넘으면 화면 08 체감에 붙는다",
            sorted[sorted.size / 2] <= 200,
        )
    }

    private fun log(message: String) {
        // Log는 1줄 길이 제한이 있어 잘린다. 나눠서 찍는다.
        message.chunked(3800).forEach { Log.i(TAG, it) }
    }

    private companion object {
        const val TAG = "CfBench"
    }
}
