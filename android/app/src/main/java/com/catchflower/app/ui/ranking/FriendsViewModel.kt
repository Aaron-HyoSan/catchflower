package com.catchflower.app.ui.ranking

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
     * @param onDone `true`면 요청이 저장됐다. `false`면 실패다.
     */
    fun add(userId: String, onDone: (Boolean) -> Unit) {
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
