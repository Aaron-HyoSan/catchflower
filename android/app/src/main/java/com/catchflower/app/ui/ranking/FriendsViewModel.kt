package com.catchflower.app.ui.ranking

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.LoginGate
import com.catchflower.app.data.AnonymousUsage
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.FriendResult
import com.catchflower.app.data.FriendRules
import com.catchflower.app.data.FriendService
import com.catchflower.app.data.FriendSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 화면 19 닉네임 검색의 상태. **다섯 개가 서로 다른 화면이다** — 뭉치면 각각 틀린 말이 된다. */
sealed interface FriendSearchUi {
    /** 검색창이 비어 있다. 아무 안내도 그리지 않는다. */
    data object Idle : FriendSearchUi

    /**
     * 한 글자만 넣었다.
     *
     * 🔴 **[NoResult]가 아니다.** 지우는 중인 검색창에 `그 닉네임을 가진 사람이 없어요`를
     *    띄우면 **찾아봤다는 뜻**이 된다([com.catchflower.app.ui.region.RegionPickerUi]와
     *    같은 판단).
     */
    data object TooShort : FriendSearchUi

    data object Searching : FriendSearchUi
    data class Loaded(val rows: List<FriendRules.Found>) : FriendSearchUi
    data object NoResult : FriendSearchUi

    /** 조회 실패. 🔴 [NoResult]와 갈라 둔다 — 못 물어본 것을 "없다"고 말하지 않는다. */
    data class Failed(val code: Int) : FriendSearchUi
}

/**
 * 화면 19 `내 친구` 탭 맨 위 **받은 요청** 섹션의 상태(2026-08-27 · 항목 1).
 *
 * 🔴 **[Empty]와 [Failed]를 갈라 둔다.** 뭉치면 **못 물어본 것을 "받은 요청이 없다"고
 *    말한다** — 그런데 이 섹션은 0건일 때 **아무것도 안 그리므로**, 실패를 0건으로
 *    뭉개면 화면에 흔적이 전혀 남지 않는다. 요청이 와 있는데 영원히 못 보게 된다.
 *    ([FriendSearchUi]에서 한 판단과 같지만 여기가 더 조용하다.)
 */
sealed interface IncomingUi {
    /** 아직 안 물어봤다. 로그인 전이거나 키 없는 빌드도 여기다. */
    data object Idle : IncomingUi

    data object Loading : IncomingUi

    /** 받은 요청이 0건이다. 화면은 섹션을 **안 그린다**(A 문서 3절). */
    data object Empty : IncomingUi

    data class Loaded(val rows: List<FriendRules.Incoming>) : IncomingUi

    /** 못 읽었다. 화면은 `받은 요청을 불러오지 못했어요` + `다시 시도`를 그린다. */
    data class Failed(val code: Int) : IncomingUi
}

/**
 * 화면 19 `검색`(닉네임으로 친구 찾기) · `친구 추가`.
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸며 생겼다(A 문서 3절 ③).
 *
 * 🔴 **`@JvmOverloads`가 없으면 검색을 여는 순간 죽는다.** `AndroidViewModelFactory`는
 *    리플렉션으로 `(Application)` 단일 인자 생성자를 찾는다 — 화면 02·07·15·17에서
 *    네 번 당했고 **컴파일은 통과한다.**
 *
 * 🔴 **모든 `mutableStateOf` 선언이 어떤 `init`보다 위에 있어야 한다**([PlaceViewModel] 주석).
 */
class FriendsViewModel @JvmOverloads constructor(
    app: Application,
    /**
     * null이면 서버 기능이 꺼진 빌드다 — 화면이 **`검색` 버튼을 아예 안 그린다**([searchable]).
     *
     * ⚠️ **[DiscoveryRepository]가 든 것과 같은 `AuthService`를 쓴다.** 새로 만들면
     *    토큰 갱신이 서로 다른 prefs 인스턴스를 지나 회전된 `refresh_token`을 덮어써서
     *    **며칠 뒤 조용히 로그인이 끊긴다**([PlaceViewModel]·[RankingService]와 같은 이유).
     */
    private val source: FriendSource? = DiscoveryRepository.get(app).auth?.let { auth ->
        FriendService(auth, myUserId = { DiscoveryRepository.get(app).userId })
    },
    /** 로그인 게이트의 입력. 친구 요청은 **비로그인 0회**다(오너 결정 2026-08-16). */
    private val usage: AnonymousUsage = AnonymousUsage.get(app),
) : AndroidViewModel(app) {

    var query by mutableStateOf("")
        private set

    var list by mutableStateOf<FriendSearchUi>(FriendSearchUi.Idle)
        private set

    /**
     * 방금 요청을 보낸 사람들.
     *
     * 🔴 **이게 없으면 버튼이 계속 `친구 추가`로 남는다.** 목록을 다시 받지 않으므로
     *    서버 상태(`pending`)와 화면이 어긋나고, 사용자는 요청이 안 갔다고 보고 다시
     *    누른다 — 서버는 409를 성공으로 삼키니 **아무 표시 없이 같은 일이 반복된다.**
     */
    var justRequested by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 로그인 시트를 띄워야 하는 행동. null이면 안 띄운다
     * ([com.catchflower.app.ui.component.LoginGateSheet]).
     *
     * 🔴 **이 값을 그리는 화면이 둘이다** — 화면 19([FriendsScreen])와 화면 16
     *    ([com.catchflower.app.ui.place.RecordDetailScreen]). 둘이 **같은 인스턴스**를
     *    쓰기 때문이다([justRequested] 주석). 한 곳에만 시트를 두면 다른 화면에서는
     *    `친구 추가`가 **눌리지만 아무 일도 안 하는 버튼**이 된다.
     */
    var loginRequired by mutableStateOf<LoginGate.GatedAction?>(null)
        private set

    fun dismissLoginRequired() {
        loginRequired = null
    }

    /** 서버 기능이 있는 빌드인가. 없으면 `검색` 버튼을 그리지 않는다. */
    val searchable: Boolean get() = source != null

    private var searchJob: Job? = null

    /**
     * 타이핑마다 서버를 부르지 않는다(디바운스).
     *
     * ⚠️ **자동 검색이다.** 화면 02가 같은 방식이라 `Done` 키만 두고 검색 버튼을
     *    만들지 않는다 — 눌러야 하는 버튼을 만들면 **안 눌러 본 사용자는 결과가 없다고
     *    생각한다**(A 문서 3절 ③).
     */
    fun onQueryChange(next: String) {
        query = next
        searchJob?.cancel()
        val trimmed = next.trim()
        if (trimmed.isEmpty()) {
            list = FriendSearchUi.Idle
            return
        }
        if (FriendRules.tooShort(trimmed)) {
            list = FriendSearchUi.TooShort
            return
        }
        searchJob = viewModelScope.launch {
            // 디바운스를 넘긴 뒤에 `Searching`으로 바꾼다 — 한 글자마다 번쩍이지 않게.
            delay(DEBOUNCE_MS)
            list = FriendSearchUi.Searching
            list = toUi(source?.search(trimmed))
        }
    }

    /** `다시 시도`. 검색어를 다시 받지 않는다 — 그 사이에 글자가 바뀌었으면 그건 새 검색이다. */
    fun retry() {
        onQueryChange(query)
    }

    /**
     * 친구 요청. **성공은 요청이 저장됐다는 뜻이고 친구가 됐다는 뜻이 아니다**
     * (C-2 상호 수락 · A 문서 3절 ③).
     *
     * ⚠️ **문구를 여기서 고르지 않는다.** 화면이 [com.catchflower.app.ui.component.CfToast]를
     *    고른다 — ViewModel이 문구를 들면 A 문서와의 대응이 화면에서 안 보인다
     *    (`RecordViewModel.deleteComment`와 같은 모양).
     *
     * 🔴 **2026-08-16: 로그인 게이트가 여기 붙었다**(오너 결정 · 비로그인 0회).
     *    막혔을 때 [onDone]을 **부르지 않는다** — `false`로 부르면 화면이
     *    `연결이 불안정해요`를 띄워서 **로그인이 필요한 것을 네트워크 문제로 말한다.**
     *    설명은 [loginRequired]가 띄우는 시트가 한다.
     *
     * @param onDone `true`면 요청이 저장됐다. `false`면 실패다.
     *   **로그인이 필요해 막힌 경우에는 아예 안 부른다.**
     */
    fun add(userId: String, onDone: (Boolean) -> Unit) {
        if (gateBlocks()) return
        val src = source ?: run {
            onDone(false)
            return
        }
        viewModelScope.launch {
            when (val res = src.request(userId)) {
                is FriendResult.Loaded -> {
                    // 🔴 **성공한 뒤에 버튼 자리를 바꾼다.** 누른 즉시 바꾸면 실패한
                    //    요청이 `요청 보냄`으로 남는다(`댓글을 지웠어요`를 낙관적으로
                    //    띄우지 않은 것과 같은 판단).
                    justRequested = justRequested + userId
                    onDone(true)
                }

                is FriendResult.Failed -> onDone(false)
                FriendResult.TooShort, FriendResult.NotConfigured -> onDone(false)
            }
        }
    }

    // ── 받은 요청 · 수락 · 거절 (2026-08-27 · 항목 1) ─────────────────

    var incoming by mutableStateOf<IncomingUi>(IncomingUi.Idle)
        private set

    private var incomingJob: Job? = null

    /**
     * 받은 요청을 읽는다. 화면이 열릴 때·수락·거절 뒤에 부른다.
     *
     * ⚠️ **이미 돌고 있으면 다시 시작한다**(`cancel` 후 재발). 수락 직후에 앞의 조회가
     *    늦게 도착하면 **방금 수락한 사람이 목록에 다시 나타난다.**
     *
     * 🔴 **[IncomingUi.Loading]으로 먼저 바꾸지 않는다** — 이미 목록이 그려져 있을 때
     *    로딩으로 되돌리면 섹션이 사라졌다 나타난다(수락할 때마다 화면이 튄다).
     *    처음 조회에서만 [IncomingUi.Loading]을 쓴다.
     */
    fun loadIncoming() {
        val src = source ?: return
        incomingJob?.cancel()
        if (incoming is IncomingUi.Idle) incoming = IncomingUi.Loading
        incomingJob = viewModelScope.launch {
            incoming = when (val res = src.incoming()) {
                is FriendResult.Loaded ->
                    if (res.value.isEmpty()) IncomingUi.Empty else IncomingUi.Loaded(res.value)

                is FriendResult.Failed -> IncomingUi.Failed(res.code)
                // 여기까지 올 수 없다(검색어가 없는 호출이다). 그래도 뭉개지 않는다.
                FriendResult.TooShort, FriendResult.NotConfigured -> IncomingUi.Failed(0)
            }
        }
    }

    /**
     * 받은 요청을 수락한다. **여기서 실제로 친구가 된다**(C-2).
     *
     * 🔴 **로그인 게이트를 [LoginGate.GatedAction.FRIEND_REQUEST]로 재사용한다.**
     *    새 enum 값을 만들지 않았다 — 그 목록은 공유계약 3절이고 **혼자 못 바꾼다**
     *    (iOS도 같은 값을 가져야 한다). 수락은 친구 관계를 만드는 같은 종류의 행동이라
     *    같은 게이트가 맞다.
     *
     * ⚠️ 성공 뒤 [onDone]이 `true`로 불리면 화면은 **친구 랭킹까지 다시 읽어야 한다** —
     *    안 읽으면 `내 친구 {n}`이 그대로여서 **수락이 안 된 것처럼 보인다.**
     *
     * @param onDone `true`면 친구가 됐다. **로그인이 막았을 때는 안 부른다**([add]와 같다).
     */
    fun accept(requesterId: String, onDone: (Boolean) -> Unit) {
        if (gateBlocks()) return
        val src = source ?: run {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = src.accept(requesterId) is FriendResult.Loaded
            // 🔴 **성공이든 실패든 다시 읽는다.** 실패 원인 대부분이 "그 요청이 이미
            //    없다"(취소·탈퇴)이므로, 안 읽으면 사라진 요청이 목록에 계속 남아
            //    누를 때마다 실패한다.
            loadIncoming()
            onDone(ok)
        }
    }

    /**
     * 받은 요청을 거절한다 = 행을 지운다. **차단이 아니다** — 상대는 다시 보낼 수 있다.
     *
     * ⚠️ 0행 삭제도 성공이다([FriendService.decline]) — 이미 없는 요청을 거절한 것이다.
     */
    fun decline(requesterId: String, onDone: (Boolean) -> Unit) {
        if (gateBlocks()) return
        val src = source ?: run {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = src.decline(requesterId) is FriendResult.Loaded
            loadIncoming()
            onDone(ok)
        }
    }

    /** [add]·[accept]·[decline]이 같은 판정을 쓴다 — 세 곳에 각자 쓰면 한 곳이 안 막힌다. */
    private fun gateBlocks(): Boolean {
        val blocked = LoginGate.requiresLogin(
            action = LoginGate.GatedAction.FRIEND_REQUEST,
            kakaoLinked = usage.kakaoLinked(),
            identifyCount = usage.identifyCount(),
        )
        if (blocked) loginRequired = LoginGate.GatedAction.FRIEND_REQUEST
        return blocked
    }

    /**
     * 검색 결과에 [justRequested]를 덮어 그린 목록.
     *
     * ⚠️ 화면이 두 값을 맞춰 보지 않게 여기서 합친다 — 화면에서 `if`로 나누면
     *    그 판단이 어느 층에서도 검증되지 않는다.
     */
    fun rowsWithLocalState(rows: List<FriendRules.Found>): List<FriendRules.Found> =
        rows.map {
            if (it.state == FriendRules.State.NONE && it.id in justRequested) {
                it.copy(state = FriendRules.State.REQUESTED)
            } else {
                it
            }
        }

    private fun toUi(res: FriendResult<List<FriendRules.Found>>?): FriendSearchUi = when (res) {
        null -> FriendSearchUi.Failed(0)
        is FriendResult.Loaded ->
            if (res.value.isEmpty()) FriendSearchUi.NoResult else FriendSearchUi.Loaded(res.value)

        is FriendResult.Failed -> FriendSearchUi.Failed(res.code)
        FriendResult.TooShort -> FriendSearchUi.TooShort
        // 여기까지 올 수 없다 — 키가 없으면 `검색` 버튼 자체가 없다. 그래도
        // `Failed`로 뭉개지 않는다: 원인이 다르면 다음 사람이 다른 곳을 본다.
        FriendResult.NotConfigured -> FriendSearchUi.Failed(0)
    }

    private companion object {
        /** 화면 02와 같은 값. 두 검색창의 체감이 다르면 한쪽이 고장으로 읽힌다. */
        const val DEBOUNCE_MS = 300L
    }
}
