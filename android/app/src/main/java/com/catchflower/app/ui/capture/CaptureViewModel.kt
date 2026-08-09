package com.catchflower.app.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.Coordinate
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.DiscoveryRules
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.LocationSource
import com.catchflower.app.data.NoLocationSource
import com.catchflower.app.data.PlaceInfo
import com.catchflower.app.data.PlaceService
import com.catchflower.app.data.PlatformLocationSource
import com.catchflower.app.data.KakaoPlaceService
import com.catchflower.app.data.NoPlaceService
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.recognizer.FlowerPreFilter
import com.catchflower.app.recognizer.FlowerRecognizer
import com.catchflower.app.recognizer.IdentifyFlow
import com.catchflower.app.recognizer.IdentifyOutcome
import com.catchflower.app.recognizer.MlKitFlowerPreFilter
import com.catchflower.app.recognizer.MockFlowerRecognizer
import com.catchflower.app.recognizer.PlantNetRecognizer
import com.catchflower.app.recognizer.QaBypassPreFilter
import com.catchflower.app.recognizer.QaPreFilterSwitch
import com.catchflower.app.recognizer.RankedCandidate
import com.catchflower.app.recognizer.RecognitionError
import com.catchflower.app.recognizer.ScientificNameIndex
import com.catchflower.app.ui.component.CfToast
import java.util.Calendar
import java.util.UUID
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

    /**
     * 화면 10 — 신규 등록.
     *
     * @param discoveryId 방금 저장한 기록. 화면 13(지도 공유)이 이걸 수정한다 —
     *   없으면 "공유하기"가 어느 기록의 공개 범위를 바꿀지 모른다.
     */
    data class NewFlower(
        val flowerId: Int,
        val dexOrder: Int,
        val collectedCount: Int,
        val seasonCount: Int,
        val discoveryId: String,
    ) : CaptureState

    /** 화면 11 — 재발견. */
    data class Rediscovered(
        val flowerId: Int,
        val count: Int,
        val discoveryId: String,
    ) : CaptureState

    /**
     * B-5 — 같은 종·같은 장소를 하루에 두 번.
     *
     * 문구는 iOS 세션이 A 문서에 추가한 `B-5 하루 중복 안내` 표를 쓴다.
     * **오너 검토 대상 문구다.**
     */
    data class DailyDuplicate(val flowerId: Int) : CaptureState

    /**
     * 화면 13 — 지도 공유 설정. 10·11의 `지도에 공유하기`가 여기로 온다.
     *
     * **왜 촬영 흐름 안에 두는가.** 이 화면이 고치는 것은 방금 등록한 기록이고,
     * 그 id는 10·11 상태에만 있다. 셸(`MainActivity`)에서 따로 띄우면 id를 넘기는
     * 경로를 하나 더 만들어야 하고, 흐름의 다음 목적지(지도·도감 상세)를 화면 두
     * 곳이 나눠 알게 된다 — [CaptureFlow] KDoc이 경계한 상태다.
     *
     * @param discoveryCount 이 꽃의 누적 발견 횟수. 카드의 `{서수} 발견`이 쓴다.
     */
    data class ShareSettings(
        val discoveryId: String,
        val flowerId: Int,
        val discoveryCount: Int,
    ) : CaptureState
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
     *
     * ⚠️ [QaBypassPreFilter]로 감싸 두지만 **평소에는 아무 일도 하지 않는다** —
     *    `/data/local/tmp/cf_qa_allow_any_photo`가 있을 때만 열린다
     *    ([QaPreFilterSwitch]). `AlwaysPassPreFilter`로 갈아끼우지 않는 이유는
     *    그 파일 주석에 있다.
     */
    private val preFilter: FlowerPreFilter = QaBypassPreFilter(MlKitFlowerPreFilter()),
    /**
     * 위치. 기본은 플랫폼 구현이고 **권한이 없으면 조용히 null**이다 —
     * `도감 등록은 위치 없이도 할 수 있어요`(화면 03)가 약속이다.
     */
    private val locationSource: LocationSource = PlatformLocationSource(app),
    /** 장소명·행정구역. 카카오 키가 없으면 [NoPlaceService]. */
    private val places: PlaceService =
        if (AppSecrets.hasKakaoKey) KakaoPlaceService() else NoPlaceService,
) : AndroidViewModel(app) {

    private val repository = FlowerRepository.get(app)
    private val flow = IdentifyFlow(repository)
    private val discoveries = DiscoveryRepository.get(app)

    init {
        // 저장된 기록을 읽어 둔다. **B-5와 `12번째 꽃`이 이걸 봐야 한다** —
        // 안 읽으면 앱을 켠 직후 촬영에서 모든 꽃이 "신규"가 되고 중복 제한도 안 걸린다.
        viewModelScope.launch { discoveries.load() }
    }

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

    /**
     * 지금 1차 필터가 어떤 것인가. [recognizerName]과 같은 이유로 있다 —
     * 검증이 `QaPreFilterSwitch.enabled`를 두 번 확인하는 동어반복이 되지 않게.
     */
    val preFilterName: String get() = preFilter::class.simpleName ?: "?"

    init {
        android.util.Log.i("CatchFlower", "인식기: $recognizerName · 1차필터: $preFilterName")
        if (QaPreFilterSwitch.enabled) {
            // 🔴 **한 줄로 크게 남긴다.** QA 우회를 켠 채 잊으면 그 뒤의 모든 촬영이
            //    유료 API로 가는데 **화면에는 아무 증상이 없다** — 화면 12가
            //    "필터가 막았다"와 "호출했는데 실패했다"를 똑같이 보여주기 때문이다((39)).
            android.util.Log.w(
                "CatchFlower",
                "🔴 QA 우회가 켜져 있다 (${QaPreFilterSwitch.PATH}). " +
                    "꽃이 아닌 사진도 유료 API로 간다. QA 끝나면 adb shell rm 으로 지운다",
            )
        }
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

    /**
     * 지금 흐름에 있는 사진 1장의 부수 정보.
     *
     * ⚠️ **셔터를 누른 시각을 여기 잡아 둔다.** 확정 시점에 `System.currentTimeMillis()`를
     *    다시 부르면 `captured_at`이 등록 시각이 되어 계약 C-8(촬영↔등록 시차 검증)이
     *    **항상 0초로 통과한다** — 검증이 있는데 아무것도 검증하지 않는 상태가 된다.
     */
    private var pending: Pending? = null

    private data class Pending(
        val jpeg: ByteArray,
        val capturedAt: Long,
        var coordinate: Coordinate? = null,
        var place: PlaceInfo? = null,
    )

    /** 위치·장소 조회. 분석과 **병렬로** 돌린다. */
    private var locationJob: Job? = null

    /** 화면 07에서 셔터를 눌렀다. */
    fun onPhotoTaken(jpeg: ByteArray) {
        analysisJob?.cancel()
        locationJob?.cancel()
        val shot = Pending(jpeg = jpeg, capturedAt = System.currentTimeMillis())
        pending = shot
        state = CaptureState.Analyzing(jpeg)
        // 위치는 판별과 **동시에** 받는다. 순차로 하면 촬영 후 대기가 GPS 대기만큼 늘어난다.
        // 판별이 먼저 끝나도 사용자가 화면 09에서 버튼을 누르는 동안 이쪽이 채워진다.
        locationJob = viewModelScope.launch { fillPlace(shot) }
        analysisJob = viewModelScope.launch { analyze(jpeg) }
    }

    /**
     * 좌표와 장소를 채운다. **실패해도 촬영은 계속된다** —
     * `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`(화면 03)가 약속이다.
     */
    private suspend fun fillPlace(shot: Pending) {
        val coordinate = locationSource.current() ?: return
        shot.coordinate = coordinate
        shot.place = places.place(coordinate.lat, coordinate.lng)
    }

    private suspend fun analyze(jpeg: ByteArray) {
        // ① 온디바이스 1차 필터 — 꽃이 아니면 **유료 API를 부르지 않는다**.
        //    비용 문서 4절 절감 장치 ②.
        val pre = preFilter.check(jpeg)
        // 🔴 **지우지 않는다**((39)). 화면 12는 **1차 필터가 막은 것**과 **유료 호출이
        //    404를 돌려준 것**을 똑같이 보여준다 — 로그가 없으면 "이 촬영이 과금됐는가"를
        //    화면으로는 **판단할 수 없다.** 실제로 이 줄 덕분에 에뮬레이터 촬영이
        //    `top=Screenshot`으로 걸러졌다는 것(= 호출 0건)을 확인했다.
        android.util.Log.i(
            "CatchFlower",
            "1차필터: 꽃=${pre.isLikelyFlower} top=${pre.topLabel} " +
                "matched=${pre.matchedLabel} conf=${pre.confidence} ${pre.elapsedMillis}ms",
        )
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
        // 🔴 **지우지 않는다**((39)). 후보가 0개면 [PlantNetRecognizer]가 **네트워크를
        //    타지 않는다**(유료 호출 없음). 반대로 이 줄이 `후보=N개(N>0)`로 찍히면
        //    **그 촬영은 과금된 것**이다. 과금 여부를 사후에 확인할 수 있는 유일한 흔적이다.
        android.util.Log.i(
            "CatchFlower",
            "판별 호출 직전: $recognizerName 후보=${candidates.size}개 (${month}월)",
        )

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
        // 위치 조회도 끊는다. 안 끊으면 취소한 사진의 좌표를 받으려고 GPS가 계속 돌고,
        // 다음 촬영의 `pending`에 **앞 사진의 장소가 덮인다.**
        locationJob?.cancel()
        locationJob = null
        pending = null
        state = CaptureState.Camera
    }

    /** 화면 12 `나중에 할게요` — 도감으로 나간다. 연속 실패도 끊는다. */
    fun giveUp() {
        analysisJob?.cancel()
        locationJob?.cancel()
        pending = null
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
     *    판정은 [DiscoveryRules.isDuplicateToday]가 한다 — 좌표 반올림 기준을
     *    카카오 캐시와 공유해야 해서 여기서 다시 구현하지 않는다.
     *
     * ⚠️ **저장이 끝난 뒤에 화면을 바꾼다.** 먼저 화면을 넘기면 `12번째 꽃`이
     *    저장 전 숫자로 그려지고, 저장이 실패해도 성공 화면이 뜬다.
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
        viewModelScope.launch { record(candidate) }
    }

    private suspend fun record(candidate: RankedCandidate) {
        val flowerId = candidate.flower.id
        // 위치 조회가 아직 안 끝났으면 기다린다. **타임아웃은 안에 있다**
        // ([PlatformLocationSource.REQUEST_TIMEOUT_MS]) — 여기서 무한정 기다리지 않는다.
        locationJob?.join()
        val shot = pending
        val records = discoveries.discoveries.value
        val now = System.currentTimeMillis()

        // B-5 — 같은 종 + 같은 장소를 오늘 이미 기록했나.
        if (DiscoveryRules.isDuplicateToday(
                discoveries = records,
                flowerId = flowerId,
                lat = shot?.coordinate?.lat,
                lng = shot?.coordinate?.lng,
                now = now,
            )
        ) {
            state = CaptureState.DailyDuplicate(flowerId)
            return
        }

        val isFirst = flowerId !in DiscoveryRules.collectedIds(records)
        // 사진을 먼저 저장한다. 실패하면 파일명 없이 기록만 남는다 —
        // **등록 자체를 막지 않는다.** 도감 칸은 채워지고 썸네일만 실루엣이 된다.
        val photoName = shot?.jpeg?.let { discoveries.photos.save(it) }
        val discovery = Discovery(
            id = UUID.randomUUID().toString(),
            userId = discoveries.userId,
            flowerId = flowerId,
            photoUrl = null, // 서버 업로드 전이다. 로컬 파일명은 아래 필드에 있다.
            localPhotoPath = photoName,
            lat = shot?.coordinate?.lat,
            lng = shot?.coordinate?.lng,
            placeName = shot?.place?.placeName ?: shot?.place?.dongName,
            dongCode = shot?.place?.dongCode,
            guCode = shot?.place?.guCode,
            // 기본은 비공개다. 공개는 화면 13에서 **사용자가 명시적으로** 고른다 —
            // 기본 공개로 두면 위치가 붙은 사진이 동의 없이 지도에 올라간다.
            visibility = Visibility.PRIVATE,
            aiConfidence = candidate.score,
            aiPickedRank = candidate.rank,
            isFirstDiscovery = isFirst,
            createdAt = now,
            capturedAt = shot?.capturedAt ?: now,
        )

        val updated = discoveries.add(discovery)
        pending = null

        state = if (isFirst) {
            CaptureState.NewFlower(
                flowerId = flowerId,
                dexOrder = DiscoveryRules.collectedIds(updated).size,
                collectedCount = DiscoveryRules.collectedIds(updated).size,
                seasonCount = DiscoveryRules.seasonCollectedCount(
                    updated,
                    Calendar.getInstance().get(Calendar.MONTH) + 1,
                ),
                discoveryId = discovery.id,
            )
        } else {
            CaptureState.Rediscovered(
                flowerId = flowerId,
                count = DiscoveryRules.countFor(updated, flowerId),
                discoveryId = discovery.id,
            )
        }
    }

    // --- 화면 13 지도 공유 설정 ---

    /** 화면 10·11의 `지도에 공유하기`. */
    fun openShareSettings(discoveryId: String, flowerId: Int) {
        val records = discoveries.discoveries.value
        state = CaptureState.ShareSettings(
            discoveryId = discoveryId,
            flowerId = flowerId,
            // `{서수} 발견`. 신규 등록이면 1이라 `첫 번째`가 된다.
            discoveryCount = DiscoveryRules.countFor(records, flowerId),
        )
    }

    /** 화면 13이 그릴 기록. 이미 저장돼 있으므로 메모리에서 찾는다. */
    fun discovery(id: String): Discovery? =
        discoveries.discoveries.value.firstOrNull { it.id == id }

    /**
     * 화면 13 확인 카드의 사진. 없으면 null (일러스트로 대체한다).
     *
     * ⚠️ **파일 존재를 확인한다.** 파일명만 보고 넘기면 사진 저장이 실패했거나
     *    정리([DiscoveryRepository.prunePhotos])에 걸린 기록에서 빈 칸이 나온다.
     */
    fun photoFile(discovery: Discovery): java.io.File? =
        discovery.localPhotoPath
            ?.takeIf { discoveries.photos.exists(it) }
            ?.let { discoveries.photos.file(it) }

    /**
     * 화면 13 `공유하기`. 공개 범위와 한 줄을 저장하고 **서버에 다시 올린다**
     * ([DiscoveryRepository.update]).
     *
     * ⚠️ **저장을 기다리지 않고 상태를 먼저 되돌린다.** 지하철에서 눌러도 화면이
     *    넘어가야 하고, 공개 범위는 이미 정해진 일이다([DiscoveryRepository.add]와 같은 판단).
     *
     * 🔴 **상태를 [CaptureState.Camera]로 되돌리는 것이 핵심이다.** 안 되돌리면
     *    촬영 탭을 다시 열 때 **방금 공유한 기록의 화면 13이 또 나온다** — 이미 공유한
     *    꽃을 다시 공유하라고 묻는 화면이고, 그 기록은 이번엔 이미 `public`이다.
     *    호출부가 곧바로 다른 탭으로 옮기므로 카메라가 실제로 열리지는 않는다.
     *
     * @param note [ShareRules.toStored]를 지난 값. **빈 문자열을 넘기지 않는다** —
     *   서버 행에 `note = ''`가 박힌다(그 이유는 [ShareRules.toStored]에 있다).
     */
    fun share(discoveryId: String, visibility: Visibility, note: String?) {
        val target = discovery(discoveryId) ?: return
        state = CaptureState.Camera
        viewModelScope.launch {
            discoveries.update(target.copy(visibility = visibility, note = note))
        }
    }

    /**
     * 화면 13 `공유하지 않기`.
     *
     * ⚠️ **아무것도 저장하지 않는다.** 기록은 이미 `private`으로 저장돼 있어서
     *    고칠 것이 없다 — 여기서 `update`를 부르면 서버에 같은 값을 한 번 더 보낸다.
     *    그래서 호출부가 토스트(`도감에는 저장됐어요`)를 띄워야 한다. 안 띄우면
     *    **아무 일도 안 일어난 것처럼 보인다.**
     */
    fun skipShare() {
        state = CaptureState.Camera
    }

    fun flowerName(flowerId: Int): String = repository.byId(flowerId)?.name.orEmpty()

    fun flower(flowerId: Int) = repository.byId(flowerId)
}
