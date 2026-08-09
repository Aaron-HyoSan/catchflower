package com.catchflower.app.recognizer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.FlowerRepository
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 🔴 **실기기 판별 실패 진단**((42)) — 정답을 아는 사진으로 **파이프라인 전체**를 태운다.
 *
 * **왜 이게 꽃을 찍는 것보다 낫나.** 오너가 실기기에서 꽃을 찍었을 때
 * `꽃이 아닐 수도 있어요`가 떴다. 그런데 손으로 찍어서 확인하면 **원인이 섞인다** —
 * 그 꽃이 도감 200종에 없었는지, 8월에 안 피는 종이었는지, 초점이 나갔는지를
 * 사후에 알 수 없다. 여기서는 **답을 아는 사진 500장**(1차 필터 실측 데이터셋)을 쓰므로
 * 실패했을 때 그게 결함인지 정상 동작인지 **가릴 수 있다.**
 *
 * **[PlantNetLiveTest]와 무엇이 다른가.** 그건 `identify()`가 서버에 받아들여지는지만
 * 본다(요청 성립). 이건 **`MlKitFlowerPreFilter` → 개화월 → PlantNet →
 * [IdentifyFlow.decide]** 까지, 즉 **사용자가 보는 화면이 정해지는 지점까지** 태운다.
 * (42)에서 확인한 구멍이 정확히 거기였다 — 오프라인 재현은 `decide()`가 8월 촬영의
 * 72%를 화면 12로 보낸다고 말하는데, **그게 실기기에서도 같은지는 재 본 적이 없다.**
 *
 * ⚠️ **유료 호출이다.** 스위치 파일이 있어야 돈다 — 무료 한도 500/일 중 아래 장수만 쓴다:
 *
 *      adb shell touch /data/local/tmp/cf_run_network_tests
 *      ./gradlew :app:connectedDebugAndroidTest --tests '*RealDeviceDiagnosisTest*'
 *      adb shell rm /data/local/tmp/cf_run_network_tests
 *
 * ⚠️ **에뮬레이터에서 돌려도 의미가 있다** — 여기서는 카메라를 안 쓰고 에셋 사진을
 *    쓰기 때문이다. 다만 오너 증상은 실기기에서 나왔으므로 실기기에서 재는 것이 목적이다.
 */
@RunWith(AndroidJUnit4::class)
class RealDeviceDiagnosisTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private companion object {
        const val SWITCH = "/data/local/tmp/cf_run_network_tests"
        const val TAG = "CatchFlowerDiag"

        /**
         * 클래스별로 몇 장 태울지. **유료 호출 수 = 이 합계**(1차 필터가 막은 것은 제외).
         *
         * `sunflowers`를 많이 쓴다 — **8월이 제철인 유일한 클래스**라서
         * "8월에 실패하는 것이 정상인 종"과 섞이지 않는다. 나머지는 대조군이다:
         * 8월에 안 피는 종이 **의도대로** 떨어지는지 확인한다.
         */
        val SAMPLE = mapOf(
            "sunflowers" to 6,
            "roses" to 3,
            "dandelion" to 3,
            "tulips" to 2,
        )
    }

    /** 한 장이 어느 갈래로 갔는가 — (42)의 원인 ①②③④에 그대로 대응한다. */
    private enum class Path {
        /** ① ML Kit가 막았다. 과금 0건. */
        PRE_FILTER_BLOCKED,

        /** ② 개화월 후보가 0개. 과금 0건. */
        NO_CANDIDATES,

        /** ③ PlantNet이 답했지만 우리 200종·이번 달에 없다. **과금됨.** */
        NOT_IN_DEX,

        /** ④ 1순위 < floor. **과금됨.** */
        BELOW_FLOOR,

        /** 화면 09/09변형 — 사용자가 등록할 수 있다. */
        SHOWN,
    }

    private data class Outcome(val path: Path, val detail: String)

    @Test
    fun 실사진으로_파이프라인_전체를_태운다() = runBlocking {
        assumeTrue(
            "유료 호출이라 건너뛴다. 켜려면: adb shell touch $SWITCH",
            java.io.File(SWITCH).exists(),
        )
        assumeTrue("PLANTNET_API_KEY가 없다", AppSecrets.hasPlantNetKey)

        val repository = FlowerRepository.get(context)
        val flow = IdentifyFlow(repository)
        val preFilter = MlKitFlowerPreFilter()
        val recognizer = PlantNetRecognizer(
            index = ScientificNameIndex(repository.flowers),
            apiKey = AppSecrets.plantNetApiKey,
        )

        // ⚠️ **달을 고정하지 않는다.** 오너가 겪은 것과 같은 조건이어야 한다 —
        //    테스트를 위해 4월로 박으면 "8월에 무엇이 일어나는가"를 못 본다((19) 7절).
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        val candidates = flow.candidatesForMonth(month)
        Log.i(TAG, "═══ ${month}월 · 개화 후보 ${candidates.size}종 · floor ${GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE} ═══")

        val results = LinkedHashMap<String, MutableList<Outcome>>()
        var paidCalls = 0

        for ((cls, count) in SAMPLE) {
            val files = assets.list("flower").orEmpty()
                // ⚠️ `"$cls_"`로 쓰면 **`cls_`라는 변수**를 찾는다(컴파일 에러).
                .filter { it.startsWith("${cls}_") }
                .sorted()
                .take(count)
            val bucket = results.getOrPut(cls) { mutableListOf() }

            for (file in files) {
                val jpeg = assets.open("flower/$file").use { it.readBytes() }

                // ① 1차 필터
                val pre = preFilter.check(jpeg)
                if (!pre.isLikelyFlower) {
                    bucket += Outcome(Path.PRE_FILTER_BLOCKED, "top=${pre.topLabel}")
                    continue
                }
                // ② 개화월
                if (candidates.isEmpty()) {
                    bucket += Outcome(Path.NO_CANDIDATES, "${month}월 후보 0")
                    continue
                }

                // ③④ 실제 유료 호출. 던지면 그대로 실패시킨다 —
                //     잡아서 문자열로 바꾸면 **어떤 HTTP 오류였는지가 사라진다.**
                paidCalls++
                val identified = recognizer.identify(jpeg, candidates)
                if (identified.isEmpty()) {
                    bucket += Outcome(Path.NOT_IN_DEX, "후보 0개로 번역됨")
                    continue
                }
                val top = identified.first()
                val name = repository.byId(top.flowerId)?.name ?: "?"
                val detail = "$name %.3f".format(top.score)

                bucket += when (flow.decide(identified)) {
                    IdentifyOutcome.Failed -> Outcome(Path.BELOW_FLOOR, detail)
                    else -> Outcome(Path.SHOWN, detail)
                }
            }
        }

        // ── 보고 ────────────────────────────────────────────────────────
        Log.i(TAG, "─── 사진별 ───")
        results.forEach { (cls, list) ->
            list.forEachIndexed { i, o -> Log.i(TAG, "  %-11s #%d %-18s %s".format(cls, i + 1, o.path, o.detail)) }
        }
        Log.i(TAG, "─── 클래스별 ───")
        results.forEach { (cls, list) ->
            val shown = list.count { it.path == Path.SHOWN }
            Log.i(
                TAG,
                "  %-11s 통과 %d/%d · %s".format(
                    cls, shown, list.size,
                    list.groupingBy { it.path }.eachCount().entries
                        .sortedBy { it.key.ordinal }
                        .joinToString(" ") { "${it.key}=${it.value}" },
                ),
            )
        }
        val total = results.values.sumOf { it.size }
        val shownTotal = results.values.sumOf { l -> l.count { it.path == Path.SHOWN } }
        Log.i(TAG, "═══ 통과 $shownTotal/$total · 유료 호출 ${paidCalls}건 (무료 한도 500/일) ═══")

        // 🔴 **판정은 sunflowers로만 한다.** 8월에 튤립·데이지가 떨어지는 것은
        //    개화월 필터가 **옳게** 도는 것이라 결함이 아니다 — 그걸 실패로 세면
        //    정상 동작을 고치려 들게 된다((19) 7절 1번과 같은 함정).
        //
        //    오프라인 재현((42))은 8월 해바라기 40장 중 36장(90%)이 통과한다고 말한다.
        //    실기기에서 **절반도 통과하지 못하면** 오프라인에 없던 것이 실기기에 있다는 뜻이다
        //    (요청 구성·이미지 인코딩·네트워크). 그게 이 테스트가 잡으려는 것이다.
        val sun = results["sunflowers"].orEmpty()
        val sunShown = sun.count { it.path == Path.SHOWN }
        assertTrue(
            "8월 제철인 해바라기가 ${sun.size}장 중 ${sunShown}장만 통과했다. " +
                "오프라인 재현은 90%가 통과한다고 말한다 — 실기기 경로에만 있는 문제다. " +
                "갈래: ${sun.joinToString(" · ") { "${it.path}(${it.detail})" }}",
            sunShown * 2 >= sun.size,
        )
    }
}
