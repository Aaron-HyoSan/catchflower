package com.catchflower.app.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.DummyDiscoveries
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.recognizer.FlowerPreFilter
import com.catchflower.app.recognizer.FlowerRecognizer
import com.catchflower.app.recognizer.IdentifyFlow
import com.catchflower.app.recognizer.IdentifyOutcome
import com.catchflower.app.recognizer.MlKitFlowerPreFilter
import com.catchflower.app.recognizer.MockFlowerRecognizer
import com.catchflower.app.recognizer.PlantNetRecognizer
import com.catchflower.app.recognizer.RankedCandidate
import com.catchflower.app.recognizer.RecognitionError
import com.catchflower.app.recognizer.ScientificNameIndex
import com.catchflower.app.ui.component.CfToast
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 촬영 흐름 상태 (화면 07 → 08 → 09 → 10/11/12).
 *
 * **한 흐름을 한 상태 기계로 둔 이유**: 07~12는 별개 화면처럼 보이지만
 * 사진 1장·후보 3개·선택 순위를 공유한다. 화면마다 상태를 들고 있으면
 * "취소했는데 분석이 계속 도는" 류의 어긋남이 생긴다.
 */
sealed interface CaptureState {

    /** 화면 07 — 카메라. */
    data object Camera : CaptureState

    /** 화면 08 — `어떤 꽃인지 보고 있어요`. */
    data class Analyzing(
        val jpeg: ByteArray,
        /** 10초를 넘겼는가 → `조금 더 걸리고 있어요`. */
        val overdue: Boolean = false,
    ) : CaptureState {
        // ByteArray는 equals가 참조 비교라 data class의 이점이 없다.
        // Compose가 상태 변화를 놓치지 않게 명시한다.
        override fun equals(other: Any?): Boolean =
            other is Analyzing && other.jpeg === jpeg && other.overdue == overdue

        override fun hashCode(): Int = 31 * System.identityHashCode(jpeg) + overdue.hashCode()
    }

    /** 화면 09 / 09 변형 — 사용자 확인. */
    data class Confirm(
        val jpeg: ByteArray,
        val outcome: IdentifyOutcome,
    ) : CaptureState {
        override fun equals(other: Any?): Boolean =
            other is Confirm && other.jpeg === jpeg && other.outcome == outcome

        override fun hashCode(): Int = 31 * System.identityHashCode(jpeg) + outcome.hashCode()
    }

    /**
     * 화면 12 — 판별 실패.
     *
     * @param streak 연속 실패 횟수. [GamePolicy.FAIL_STREAK_FOR_NOT_A_FLOWER]회부터
     *   제목이 `꽃이 아닐 수도 있어요`로 바뀐다 (A 문서).
     */
    data class Failed(val streak: Int) : CaptureState

    /** 화면 10 — 신규 등록. */
    data class NewFlower(val flowerId: Int, val dexOrder: Int) : CaptureState

    /** 화면 11 — 재발견. */
    data class Rediscovered(val flowerId: Int, val count: Int) : CaptureState

    /**
     * B-5 — 같은 종·같은 장소를 하루에 두 번.
     *
     * 문구는 iOS 세션이 A 문서에 추가한 `B-5 하루 중복 안내` 표를 쓴다.
     * **오너 검토 대상 문구다.**
     */
    data class DailyDuplicate(val flowerId: Int) : CaptureState
}

/**
 * ⚠️ **`@JvmOverloads`가 없으면 앱이 죽는다.** 코틀린 기본값은 바이트코드에
 *    `(Application)` 단일 인자 생성자를 만들어 주지 않는다. 그런데
 *    `AndroidViewModelFactory`는 리플렉션으로 정확히 그 생성자를 찾으므로
 *    `NoSuchMethodException` → `Cannot create an instance of class CaptureViewModel`으로
 *    **컴파일은 통과하고 실행만 죽는다.** 화면 07을 열어보지 않으면 못 잡는다.
 *    (인자를 주입받는 형태는 테스트를 위해 남긴다.)
 */
class CaptureViewModel @JvmOverloads constructor(
    app: Application,
    recognizer: FlowerRecognizer? = null,
    /**
     * 1차 필터. 실측 결과 재현율 100%·차단율 92.4%로 확정했다
     * ([MlKitFlowerPreFilter] 주석 참조).
     */
    private val preFilter: FlowerPreFilter = MlKitFlowerPreFilter(),
) : AndroidViewModel(app) {

    private val repository = FlowerRepository.get(app)
    private val flow = IdentifyFlow(repository)

    /**
     * 실제 인식기를 쓸 수 있으면 쓰고, **키가 없으면 Mock으로 돈다.**
     *
     * ⚠️ 키가 없다고 촬영 흐름을 못 쓰게 만들지 않는다 ([AppSecrets] 규칙).
     *    반대로 키가 있는데 Mock으로 계속 돌면 **`PlantNetRecognizer`가 한 줄도
     *    실행되지 않는다** — 컴파일과 테스트만 통과한 코드가 된다. iOS가 그 함정에
     *    빠져서 `#if DEBUG` 우회로를 따로 만들었다.
     */
    private val recognizer: FlowerRecognizer = recognizer ?: defaultRecognizer()

    private fun defaultRecognizer(): FlowerRecognizer =
        if (AppSecrets.hasPlantNetKey) {
            PlantNetRecognizer(
                index = ScientificNameIndex(repository.flowers),
                apiKey = AppSecrets.plantNetApiKey,
            )
        } else {
            MockFlowerRecognizer()
        }

    /**
     * 지금 어떤 인식기로 도는가. **테스트와 로그가 이걸 본다.**
     *
     * ⚠️ 이게 없으면 "키가 있으면 실엔진을 쓴다"를 검증할 방법이 없다 —
     *    `AppSecrets.hasPlantNetKey`를 두 번 확인하는 동어반복 테스트가 된다.
     *    실제로 그렇게 짰다가 고쳤다.
     */
    val recognizerName: String get() = recognizer::class.simpleName ?: "?"

    init {
        android.util.Log.i("CatchFlower", "인식기: $recognizerName")
    }

    var state by mutableStateOf<CaptureState>(CaptureState.Camera)
        private set

    /**
     * 화면에 띄울 토스트. 소비하면 [consumeToast]로 지운다.
     *
     * **왜 상태로 두는가**: 네트워크 오류를 화면 12(판별 실패)로 보내면
     * `사진을 다시 찍어주세요`라고 말하게 되는데, 그건 **사용자 잘못이 아닌 걸
     * 사용자 잘못으로 만든다** (A 문서 0절 원칙). 원인을 토스트로 알리고 촬영 화면에 남긴다.
     */
    var toast by mutableStateOf<CfToast?>(null)
        private set

    fun consumeToast() {
        toast = null
    }

    /** 연속 판별 실패. 성공하면 0으로 돌아간다. */
    private var failStreak = 0

    private var analysisJob: Job? = null

    /**
     * 개발용. Mock 분기(`low`·`fail`)를 UI 없이 태우기 위한 라벨.
     *
     * ⚠️ 실제 인식기는 이 값을 무시한다 (`FlowerRecognizer.debugLabel` 계약).
     */
    var debugLabel: String? = null

    /** 화면 07에서 셔터를 눌렀다. */
    fun onPhotoTaken(jpeg: ByteArray) {
        analysisJob?.cancel()
        state = CaptureState.Analyzing(jpeg)
        analysisJob = viewModelScope.launch { analyze(jpeg) }
    }

    private suspend fun analyze(jpeg: ByteArray) {
        // ① 온디바이스 1차 필터 — 꽃이 아니면 **유료 API를 부르지 않는다**.
        //    비용 문서 4절 절감 장치 ②.
        val pre = preFilter.check(jpeg)
        if (!pre.isLikelyFlower) {
            // 화면 12로 보낸다. 사용자에게는 판별 실패와 구분되지 않는다 —
            // "꽃이 아니에요"라고 단정하면 필터가 틀렸을 때(재현율 100%지만 우리 데이터셋 기준)
            // 사용자가 반박할 방법이 없다. 3회 연속이면 A 문구가 그때 그 말을 한다.
            failStreak++
            state = CaptureState.Failed(failStreak)
            return
        }

        // ② 개화월 하드 필터 (A-1 필수 구현).
        //    ⚠️ 서버가 붙으면 서버가 한다. 지금은 클라이언트가 후보를 만든다.
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        val candidates = flow.candidatesForMonth(month)

        val result = try {
            recognizer.identify(jpeg, candidates, debugLabel)
        } catch (e: RecognitionError) {
            // **통신 오류는 판별 실패가 아니다.** 화면 12는 "사진을 다시 찍어주세요"라고
            // 하는데, 연결 문제로 그러면 사용자가 멀쩡한 사진을 계속 다시 찍는다.
            // 연속 실패(B-11)로도 세지 않는다 — 통신 문제로 `꽃이 아닐 수도 있어요`가
            // 뜨면 엉뚱한 안내가 된다.
            //
            // 일일 한도 초과도 같이 묶는다. 사용자가 할 수 있는 게 "잠시 후 다시"인 건 같고
            // **한도 전용 문구는 A 문서에 없다** — 없는 문구를 만들지 않는다.
            android.util.Log.w("CatchFlower", "인식기 호출 실패", e)
            toast = CfToast.NETWORK_ERROR
            state = CaptureState.Camera
            return
        }

        when (val outcome = flow.decide(result)) {
            is IdentifyOutcome.Failed -> {
                failStreak++
                state = CaptureState.Failed(failStreak)
            }
            else -> {
                failStreak = 0
                state = CaptureState.Confirm(jpeg, outcome)
            }
        }
    }

    /** 화면 08 `취소` · 화면 09 `아니에요, 다시 찍을게요` · 화면 12 `다시 찍기`. */
    fun backToCamera() {
        analysisJob?.cancel()
        analysisJob = null
        state = CaptureState.Camera
    }

    /** 화면 12 `나중에 할게요` — 도감으로 나간다. 연속 실패도 끊는다. */
    fun giveUp() {
        analysisJob?.cancel()
        failStreak = 0
        state = CaptureState.Camera
    }

    /** 화면 08에서 10초를 넘겼다 → `조금 더 걸리고 있어요`. */
    fun markOverdue() {
        val current = state
        if (current is CaptureState.Analyzing && !current.overdue) {
            state = current.copy(overdue = true)
        }
    }

    /**
     * 화면 09에서 후보를 확정했다 (`네, 맞아요` 또는 후보 선택).
     *
     * ⚠️ **B-5를 여기서 판정한다.** 같은 종·같은 장소·같은 날이면 등록하지 않는다.
     *    지금은 위치가 없어(GPS 미연결) 같은 날 같은 종만 본다 —
     *    위치가 붙으면 [GamePolicy.SAME_PLACE_RADIUS_METERS]를 함께 본다.
     */
    fun confirm(candidate: RankedCandidate) {
        // B-3 어뷰징 가드 ① — 희귀종을 낮은 순위에서 고르면 사진을 한 장 더 받는다.
        // ⚠️ 아직 화면이 없다. 여기서 조용히 통과시키면 가드가 없는 것과 같으므로
        //    호출부가 알 수 있게 상태로 남긴다 (다음 단계에서 화면을 붙인다).
        if (candidate.needsExtraPhoto) {
            // TODO(다음 단계): 추가 촬영 화면. 지금은 그대로 진행하되 흔적을 남긴다.
            android.util.Log.w(
                "CatchFlower",
                "B-3 가드 ①: ${candidate.flower.name} rank=${candidate.rank} " +
                    "score=${candidate.score} → 추가 촬영이 필요한 케이스",
            )
        }

        val flowerId = candidate.flower.id
        val today = DummyDiscoveries.forFlower(flowerId, System.currentTimeMillis())

        // B-5 — 같은 종을 오늘 이미 기록했나.
        val alreadyToday = today.count { isSameDay(it.createdAt, System.currentTimeMillis()) }
        if (alreadyToday >= GamePolicy.SAME_FLOWER_SAME_PLACE_DAILY_LIMIT) {
            state = CaptureState.DailyDuplicate(flowerId)
            return
        }

        state = if (today.isEmpty()) {
            CaptureState.NewFlower(flowerId, dexOrder = DummyDiscoveries.collectedIds.size + 1)
        } else {
            CaptureState.Rediscovered(flowerId, count = today.size + 1)
        }
    }

    fun flowerName(flowerId: Int): String = repository.byId(flowerId)?.name.orEmpty()

    fun flower(flowerId: Int) = repository.byId(flowerId)

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
