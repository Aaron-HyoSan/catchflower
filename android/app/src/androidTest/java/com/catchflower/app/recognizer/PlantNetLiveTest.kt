package com.catchflower.app.recognizer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.ui.capture.CaptureViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **실제 PlantNet 호출** — 앱 코드로 진짜 요청을 보낸다.
 *
 * **왜 이것까지 필요한가.** [PlantNetReplayTest]는 캐시된 응답 200개로 파싱·색인·필터를
 * 전부 검증했다. 그런데 **요청을 만드는 쪽은 하나도 검증하지 못한다** —
 * multipart 경계 문자열, `organs=flower` 파트, 쿼리 파라미터 이름, INTERNET 권한.
 * 여기가 틀리면 응답이 400으로 오는데, 가짜 Transport로는 영원히 알 수 없다.
 * iOS도 같은 이유로 실호출 3경로를 따로 검증했다(진행 (19) 6절).
 *
 * ⚠️ **유료 호출이다.** 그래서 기본 실행에서 빠진다 — 무료 쿼터는 하루 500건이고
 *    오너 규칙은 "실측 보고 전 유료 호출 금지"다. 켜는 방법:
 *
 *      adb shell touch /data/local/tmp/cf_run_network_tests
 *      ./gradlew :app:connectedDebugAndroidTest --tests '*PlantNetLiveTest*'
 *      adb shell rm /data/local/tmp/cf_run_network_tests
 *
 *    **사진 3장만 보낸다.** 정확도 측정이 아니라 "요청이 서버에 받아들여지는가"를
 *    보는 것이므로 장수를 늘릴 이유가 없다.
 *
 * ⚠️ 스위치를 환경변수로 만들지 않았다. iOS 세션이 그걸로 세 번 실패했다 —
 *    테스트 러너는 별도 프로세스라 env를 물려받지 않는다. **파일 존재**가 확실하다.
 */
@RunWith(AndroidJUnit4::class)
class PlantNetLiveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private companion object {
        const val SWITCH = "/data/local/tmp/cf_run_network_tests"
        const val TAG = "CatchFlowerLive"

        /**
         * 파일명 → iOS 실측 캐시의 1순위 점수 (`Helianthus annuus` = 해바라기).
         *
         * 캐시에서 뽑았다: `/private/tmp/plantnet_cache`의 해바라기 1순위 상위 3장.
         * **답이 확실한 사진만 쓴다** — 이 테스트는 정확도가 아니라 요청 성립을 본다.
         */
        val HIGH_CONFIDENCE_SUNFLOWERS = listOf(
            "sunflowers_14121915990_4b76718077_m.jpg" to 0.838f,
            "sunflowers_10386540696_0a95ee53a8_n.jpg" to 0.729f,
            "sunflowers_10386702973_e74a34c806_n.jpg" to 0.704f,
        )
    }

    private fun requireSwitch() {
        assumeTrue(
            "유료 호출이라 건너뛴다. 켜려면: adb shell touch $SWITCH",
            java.io.File(SWITCH).exists(),
        )
        assumeTrue("PLANTNET_API_KEY가 없다", AppSecrets.hasPlantNetKey)
    }

    private fun recognizer() = PlantNetRecognizer(
        index = ScientificNameIndex(FlowerRepository.get(context).flowers),
        apiKey = AppSecrets.plantNetApiKey,
    )

    /**
     * 실사진 3장이 서버에 받아들여지고, **캐시된 실측과 같은 답이 온다.**
     *
     * ⚠️ **사진을 아무거나 고르면 안 된다.** 처음엔 `assets.list("flower").take(3)`으로
     *    했는데 알파벳 순이라 전부 `daisy_`였다 — 5클래스 중 정확도가 가장 낮은 클래스다(42.5%).
     *    실제 결과가 `후보 0개 · 감국 0.002 · 후보 0개`로 왔는데
     *    "빈 결과가 아니면 통과"라는 느슨한 조건 때문에 **0.002 하나로 초록이 됐다.**
     *    요청이 깨져 있어도 통과할 수 있는 검사였다.
     *
     * 그래서 **iOS 실측 캐시에 답이 확실히 들어 있는 사진**을 고정으로 쓴다.
     * 같은 사진에 같은 종이 오면 요청·응답 경로 전체가 맞다는 뜻이다.
     *
     * **후보 집합을 넓게 준다** (전체 200종). 여기서 재는 건 정확도가 아니라
     * 요청이 성립하는지다 — 개화월 필터로 좁히면 "필터가 떨군 것"과
     * "요청이 틀린 것"이 섞인다(진행 (19) 7절 1번과 같은 함정).
     */
    @Test
    fun 실제_호출이_캐시된_실측과_같은_답을_준다() = runBlocking {
        requireSwitch()
        val repository = FlowerRepository.get(context)
        val all = repository.flowers.map { it.id }
        // 학명으로 찾는다. 도감번호를 박으면 도감이 바뀔 때 조용히 틀린다.
        val sunflowerId = repository.flowers
            .first { it.scientificName.startsWith("Helianthus") }.id

        for ((name, cachedScore) in HIGH_CONFIDENCE_SUNFLOWERS) {
            val jpeg = assets.open("flower/$name").use { it.readBytes() }
            // 실패하면 RecognitionError가 던져진다 — 그게 곧 테스트 실패다.
            // 잡아서 assert로 바꾸면 **어떤 HTTP 오류였는지가 사라진다.**
            val result = recognizer().identify(jpeg, all)
            val named = result.joinToString(" · ") {
                "${repository.byId(it.flowerId)?.name} %.3f".format(it.score)
            }
            Log.i(TAG, "$name → 후보 ${result.size}개: $named (캐시 1순위 $cachedScore)")

            assertEquals(
                "$name 의 1순위가 해바라기가 아니다. 캐시에는 $cachedScore 로 들어 있다 — " +
                    "요청 구성(multipart·organs·project)을 의심한다. 받은 것: [$named]",
                sunflowerId,
                result.firstOrNull()?.flowerId,
            )
            // 점수가 딱 맞을 필요는 없다(모델이 갱신될 수 있다). 다만 **자릿수가 달라지면**
            // 같은 사진에 다른 응답이 오는 것이므로 알아야 한다.
            assertTrue(
                "$name 점수가 캐시($cachedScore)와 크게 다르다: ${result.first().score}",
                result.first().score > cachedScore / 3f,
            )
        }
    }

    /**
     * 잘못된 키에 **정상 응답이 오지 않는다.**
     *
     * 이게 없으면 "호출 성공"이 무의미하다 — 키를 안 보내도 200이 오는 경로가 있다면
     * 우리가 무엇을 검증한 건지 알 수 없다. 호출 1건.
     */
    @Test
    fun 잘못된_키는_거부된다() = runBlocking {
        requireSwitch()
        val bad = PlantNetRecognizer(
            index = ScientificNameIndex(FlowerRepository.get(context).flowers),
            apiKey = "definitely-not-a-real-key",
        )
        val jpeg = assets.open("flower/${assets.list("flower")!!.sorted().first()}")
            .use { it.readBytes() }

        val error = runCatching { bad.identify(jpeg, listOf(81)) }.exceptionOrNull()
        Log.i(TAG, "잘못된 키 결과: $error")
        assertTrue(
            "잘못된 키인데 오류가 아니었다 (실제: $error)",
            error is RecognitionError.Unavailable,
        )
    }

    /**
     * 후보가 비면 **호출하지 않는다** — 12~2월 휴지기에 돈을 쓰지 않는다.
     * 호출 0건이라 스위치 없이도 돈다.
     */
    @Test
    fun 후보가_비면_호출하지_않는다() = runBlocking {
        assumeTrue("PLANTNET_API_KEY가 없다", AppSecrets.hasPlantNetKey)
        val jpeg = assets.open("flower/${assets.list("flower")!!.sorted().first()}")
            .use { it.readBytes() }
        val result = recognizer().identify(jpeg, emptyList())
        assertTrue(result.isEmpty())
    }

    /**
     * 앱이 **실제로 어떤 인식기를 쓰는지** 확인한다.
     *
     * ⚠️ iOS가 여기서 걸렸다 — `PlantNetRecognizer`를 다 만들어 두고
     *    **한 번도 실행하지 않은 채** 커밋까지 갔다. 키가 있으면 실엔진이어야 한다.
     *    호출 0건이라 스위치 없이 돈다.
     *
     * ⚠️ 처음엔 `assertFalse(!AppSecrets.hasPlantNetKey)`로 짰다 — `assumeTrue`가 이미
     *    보장한 것을 한 번 더 확인하는 **동어반복이라 항상 통과한다.**
     *    ViewModel이 Mock을 고르도록 바뀌어도 초록이었다. 그래서 실제 선택 결과
     *    ([CaptureViewModel.recognizerName])를 본다.
     */
    @Test
    fun 키가_있으면_실엔진을_쓴다() {
        assumeTrue("PLANTNET_API_KEY가 없다", AppSecrets.hasPlantNetKey)
        // ViewModel 생성은 메인 스레드에서 해야 한다 (AndroidViewModel → Application 접근).
        val names = arrayOfNulls<String>(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            names[0] = CaptureViewModel(
                context.applicationContext as android.app.Application,
            ).recognizerName
        }
        Log.i(TAG, "앱이 고른 인식기: ${names[0]}")
        assertEquals(
            "키가 있는데 앱이 ${names[0]}으로 돈다 — 실인식이 영원히 실행되지 않는다",
            PlantNetRecognizer::class.simpleName,
            names[0],
        )
    }
}
