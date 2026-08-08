package com.catchflower.app.ui.region

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.KakaoRegionSearchService
import com.catchflower.app.data.RankingResult
import com.catchflower.app.data.RankingService
import com.catchflower.app.data.RankingSource
import com.catchflower.app.data.RegionCandidate
import com.catchflower.app.data.RegionSearchResult
import com.catchflower.app.data.RegionSearchSource
import com.catchflower.app.data.RegionUpdateResult
import com.catchflower.app.data.RegionUpdateService
import com.catchflower.app.data.RegionUpdateSink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 화면 02 활동 지역 선택.
 *
 * 🔴 **`@JvmOverloads`가 없으면 화면을 여는 순간 앱이 죽는다.** 화면 07·17에서
 *    이미 두 번 당했다 — `AndroidViewModelFactory`는 리플렉션으로 `(Application)`
 *    단일 인자 생성자를 찾는데 코틀린 기본값은 그 생성자를 만들지 않는다.
 *    **컴파일은 통과하고 실행만 죽는다.**
 *
 * 🔴 **프로퍼티 선언을 [init] 아래로 내리지 마라** — 화면 17에서 겪은 그 사고다
 *    (`NullPointerException … MutableState.setValue`). 그래서 이 클래스에는
 *    **`init`이 없다.** 화면 02는 검색어를 받기 전에 할 일이 없으므로 처음부터
 *    아무것도 안 불러도 된다.
 *
 * ## ⚠️ JVM 테스트가 이 클래스를 **만들 수 없다**
 *
 * `AndroidViewModel`이라 `Application`이 필요하고 Robolectric을 안 쓴다.
 * 그래서 여기 로직은 테스트로 고정되지 않는다 — **판단이 필요한 부분은 전부
 * [RegionPickerLogic]으로 뺐다.** 이 클래스에는 조립과 코루틴만 남긴다.
 * 화면 17의 교훈이다: "ViewModel을 건드리면 에뮬레이터에서 그 탭을 열어야 한다."
 */
class RegionPickerViewModel @JvmOverloads constructor(
    app: Application,
    private val search: RegionSearchSource = KakaoRegionSearchService(),
    /**
     * ⚠️ **[DiscoveryRepository]가 든 것과 같은 `AuthService`를 쓴다.** 새로 만들면
     *    토큰 갱신이 서로 다른 prefs 인스턴스를 지나 회전된 `refresh_token`을
     *    덮어써서 **며칠 뒤 조용히 로그인이 끊긴다**(화면 17과 같은 이유).
     */
    private val sink: RegionUpdateSink? =
        DiscoveryRepository.get(app).auth?.let { RegionUpdateService(it) },
    private val ranking: RankingSource? =
        DiscoveryRepository.get(app).auth?.let { RankingService(it) },
) : AndroidViewModel(app) {

    var query by mutableStateOf("")
        private set

    var list by mutableStateOf<RegionPickerUi>(RegionPickerUi.Idle)
        private set

    /**
     * 고른 동네. null이면 CTA가 `동네를 선택해 주세요`(Disabled)다.
     *
     * ⚠️ **`list`가 바뀌면 비운다.** 안 비우면 `연남동`을 골라 둔 채 `역삼`을
     *    검색한 사용자가 **화면에 없는 동네로 시작하기**를 누른다.
     */
    var selected by mutableStateOf<RegionCandidate?>(null)
        private set

    var save by mutableStateOf<RegionSaveUi>(RegionSaveUi.Idle)
        private set

    /**
     * 동네별 이웃 수. **없는 키는 "아직 모른다"이고 화면은 줄을 뺀다.**
     *
     * 🔴 **[Int]가 아니라 `Int?`를 담지 않는다** — 키가 없는 것과 값이 null인 것을
     *    구분할 이유가 없고, 두 가지로 두면 화면이 한쪽을 잊는다. 실패한 동네는
     *    **키를 넣지 않는다**(A 문서 3절 `화면 02에서 이웃 수를 모를 때`).
     */
    var memberCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** 타이핑마다 호출을 태우지 않기 위한 디바운스. */
    private var searchJob: Job? = null

    fun onQueryChange(next: String) {
        query = next
        selected = null
        searchJob?.cancel()
        if (!RegionPickerLogic.shouldSearch(next)) {
            // 🔴 **[RegionPickerUi.NoResult]가 아니라 [RegionPickerUi.Idle]이다.**
            //    지우는 중인 검색창에 `검색 결과가 없어요`를 띄우면 자기 동네가
            //    없는 앱으로 읽힌다.
            list = RegionPickerUi.Idle
            return
        }
        searchJob = viewModelScope.launch {
            // ⚠️ 여기서 [RegionPickerUi.Searching]으로 **바로 바꾸지 않는다.**
            //    한 글자 칠 때마다 스피너가 번쩍인다. 디바운스를 넘긴 뒤에 바꾼다.
            delay(DEBOUNCE_MS)
            list = RegionPickerUi.Searching
            apply(search.search(next.trim()))
        }
    }

    /** A 문서 02번 `현재 위치로 우리 동네 찾기`. 좌표는 화면이 준다(권한이 화면에 있다). */
    fun onCoordinate(lat: Double, lng: Double) {
        searchJob?.cancel()
        selected = null
        searchJob = viewModelScope.launch {
            list = RegionPickerUi.Searching
            apply(search.byCoordinate(lat, lng))
        }
    }

    private suspend fun apply(result: RegionSearchResult) {
        list = RegionPickerLogic.toUi(result)
        val loaded = list as? RegionPickerUi.Loaded ?: return
        selected = RegionPickerLogic.autoSelect(loaded.candidates)
        loadMemberCounts(loaded.candidates)
    }

    /**
     * 목록에 올린 동네들의 이웃 수를 채운다.
     *
     * ⚠️ **실패한 동네는 지도에 넣지 않는다.** 0을 넣으면 화면이
     *    `아직 이웃이 적어요`를 그리는데, 그건 **세어 봤다는 뜻**이다.
     *
     * ⚠️ 목록이 바뀌면 이전 값은 남겨 둔다 — 같은 동네를 다시 검색할 때 다시 묻지
     *    않는다. 이웃 수는 초 단위로 바뀌는 값이 아니다.
     */
    private suspend fun loadMemberCounts(candidates: List<RegionCandidate>) {
        val source = ranking ?: return
        for (c in candidates) {
            if (memberCounts.containsKey(c.dongCode)) continue
            val res = source.dongMemberCount(c.dongCode)
            if (res is RankingResult.Loaded) {
                memberCounts = memberCounts + (c.dongCode to res.value)
            }
        }
    }

    fun onSelect(candidate: RegionCandidate) {
        selected = candidate
    }

    /** A 문서 02번 CTA `{동명}으로 시작하기`. */
    fun onConfirm(onDone: () -> Unit) {
        val target = selected ?: return
        if (save is RegionSaveUi.Saving) return // 연달아 누르면 요청이 겹친다
        save = RegionSaveUi.Saving
        viewModelScope.launch {
            val sink = sink
            if (sink == null) {
                // 키 없는 빌드. **오류를 띄우지 않고 넘긴다** — 사용자 탓이 아니고,
                // 여기서 막으면 서버 없는 빌드로는 앱을 아예 못 쓴다.
                save = RegionSaveUi.Saved
                onDone()
                return@launch
            }
            val result = sink.save(target)
            save = RegionPickerLogic.toSaveUi(result)
            // 🔴 **[RegionUpdateResult.NotConfigured]도 넘긴다.** 위와 같은 이유다.
            if (result is RegionUpdateResult.Saved || result is RegionUpdateResult.NotConfigured) {
                onDone()
            }
        }
    }

    /** A 문서 3절 `다시 시도`. **[RegionSaveUi.RuleRejected]에는 이 버튼이 없다.** */
    fun retrySave(onDone: () -> Unit) {
        save = RegionSaveUi.Idle
        onConfirm(onDone)
    }

    private companion object {
        /**
         * 실측: 카카오 주소 검색은 200~400ms다. 300ms면 한 글자씩 치는 동안
         * 호출이 안 나가고, 멈추면 바로 뜬다.
         */
        const val DEBOUNCE_MS = 300L
    }
}

/**
 * 화면 02의 **판단만** 모은 곳. [RegionPickerViewModel]이 JVM에서 만들어지지 않기
 * 때문에 존재한다 — 여기 있는 것은 테스트로 고정되고, ViewModel에 남은 것은
 * 에뮬레이터로만 확인된다.
 *
 * 🔴 **분기를 ViewModel로 다시 옮기지 마라.** 그러면 그 분기는 **어떤 테스트도
 *    실행하지 않는 코드**가 된다.
 */
object RegionPickerLogic {

    /** 두 글자 미만이면 부르지 않는다 — 실측으로 `동` 한 글자는 0개다. */
    fun shouldSearch(query: String): Boolean =
        query.trim().length >= KakaoRegionSearchService.MIN_QUERY_LENGTH

    /**
     * 🔴 **빈 목록을 [RegionPickerUi.NoResult]로 바꾸는 곳이 여기 한 군데여야 한다.**
     *    [RegionSearchResult.Loaded]는 빈 목록일 수 있고(오타를 쳤다),
     *    [RegionSearchResult.Failed]는 그것과 **다른 화면**이다.
     */
    fun toUi(result: RegionSearchResult): RegionPickerUi = when (result) {
        is RegionSearchResult.Loaded ->
            if (result.candidates.isEmpty()) RegionPickerUi.NoResult
            else RegionPickerUi.Loaded(result.candidates)
        RegionSearchResult.Failed -> RegionPickerUi.Failed
        RegionSearchResult.NotConfigured -> RegionPickerUi.NotConfigured
    }

    /**
     * ⚠️ **[RegionUpdateResult.Rejected]가 [RegionSaveUi.RuleRejected]로 간다.**
     *    [RegionSaveUi.SaveFailed]로 뭉치면 화면이 `다시 시도`를 내밀고
     *    사용자는 눌러도 안 되는 버튼을 계속 누른다.
     */
    fun toSaveUi(result: RegionUpdateResult): RegionSaveUi = when (result) {
        RegionUpdateResult.Saved -> RegionSaveUi.Saved
        // 키 없는 빌드는 **오류가 아니다.** 화면은 그대로 넘어간다.
        RegionUpdateResult.NotConfigured -> RegionSaveUi.Saved
        is RegionUpdateResult.Rejected -> RegionSaveUi.RuleRejected
        is RegionUpdateResult.Failed -> RegionSaveUi.SaveFailed
    }

    /**
     * 검색 결과를 받은 직후 **미리 골라 둘 동네.** null이면 아무것도 안 고른다.
     *
     * 🔴 **후보가 여럿이면 고르지 않는다.** 이 선택은 **6개월간 못 바꾼다** —
     *    앱이 대신 고르면 사용자가 `역삼`을 검색해 목록 첫 줄(`역삼1동`)이 눌린 채로
     *    CTA를 누를 수 있고, 정작 살던 곳은 `역삼2동`이다. **오류는 한 건도 안 나고**
     *    화면은 고른 동네 이름을 정확히 보여준다.
     *
     * 하나뿐이면 골라 둔다 — `현재 위치로 우리 동네 찾기`가 그 경우다. 좌표 되짚기는
     * 행정동을 **하나** 주므로 사용자가 다시 누를 것이 없다.
     *
     * ⚠️ **이 판단을 [RegionPickerViewModel]로 되돌리지 마라.** 되돌리면 어떤 테스트도
     *    실행하지 않는 분기가 된다(위 클래스 주석). 실제로 한동안 그 안에 있었고,
     *    그래서 `역삼`처럼 결과가 여럿인 검색어로는 **확인된 적이 없었다** —
     *    `adb shell input text`는 한글을 아예 못 보낸다(실측:
     *    `NullPointerException: Attempt to get length of null array`).
     */
    fun autoSelect(candidates: List<RegionCandidate>): RegionCandidate? =
        candidates.singleOrNull()

    /**
     * 목록 항목 둘째 줄. **A 문서 02번 표와 3절 `이웃 수를 모를 때`가 근거다.**
     *
     * @return null이면 **줄을 그리지 않는다.** 0으로 바꾸지 마라 —
     *   `아직 이웃이 적어요`는 "세어 봤다"는 뜻이고, 이웃 1,284명인 동네를
     *   비어 있다고 말하게 된다. 사용자는 그걸 보고 6개월간 못 바꾸는 선택을 한다.
     */
    fun memberLabel(count: Int?): String? = when {
        count == null -> null
        // B-6 최소 인원과 **같은 값**이다. 여기 숫자를 직접 쓰지 않는다 —
        // 갈리면 화면 02가 "이웃이 충분하다"고 말한 동네에서 랭킹이 구로 넓어진다.
        count < GamePolicy.REGION_RANKING_MIN_MEMBERS -> "아직 이웃이 적어요"
        else -> "이웃 ${KoreanText.thousands(count)}명 활동 중"
    }

    /**
     * CTA `{동명}으로 시작하기`. 미선택이면 A 문서의 Disabled 문구다.
     *
     * 🔴 **`"${dongName}으로"`로 쓰면 안 된다.** 받침 없는 동명이 많다 —
     *    `성수동2가`·`제주시 연동`·`역삼1동`은 `…가으로`·`…동으로`가 아니라
     *    **`가로`·`동으로`**다. A 문서 0절이 조사 자동 처리를 요구하고
     *    [KoreanText.direction]이 그 함수다. **컴파일도 되고 화면도 그려진다** —
     *    틀린 한국어만 남는다.
     */
    fun ctaLabel(selected: RegionCandidate?): String =
        if (selected == null) "동네를 선택해 주세요"
        else "${KoreanText.direction(selected.dongName)} 시작하기"
}
