package com.catchflower.app.ui.my

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.NicknameRules
import com.catchflower.app.data.ProfileResult
import com.catchflower.app.data.ProfileService
import com.catchflower.app.data.ProfileSource
import com.catchflower.app.ui.component.CfToast
import kotlinx.coroutines.launch

/**
 * 화면 20 `프로필 수정`의 상태.
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸며 생겼다(A 문서 3절 ④).
 *
 * 🔴 **`@JvmOverloads`가 없으면 화면을 여는 순간 죽는다** — 화면 02·07·15·17·19에서
 *    다섯 번 당했고 **컴파일은 통과한다**([FriendsViewModel] 주석).
 *
 * 🔴 **모든 `mutableStateOf` 선언이 어떤 `init`보다 위에 있어야 한다**([RankingViewModel]
 *    `friendCount` 주석: 아래 두면 탭을 여는 순간 NPE로 죽고 **JVM 테스트는 전부 초록이다**).
 */
class ProfileEditViewModel @JvmOverloads constructor(
    app: Application,
    /** null이면 키 없는 빌드다 — 화면 20이 `프로필 수정` 버튼을 아예 안 그린다([savable]). */
    private val source: ProfileSource? = DiscoveryRepository.get(app).auth?.let { auth ->
        ProfileService(auth, myUserId = { DiscoveryRepository.get(app).userId })
    },
) : AndroidViewModel(app) {

    var input by mutableStateOf("")
        private set

    /** 저장 중. 버튼을 두 번 눌러 **PATCH를 두 번 보내는** 것을 막는다. */
    var saving by mutableStateOf(false)
        private set

    /**
     * 화면을 열 때의 닉네임. [NicknameRules.changed]가 이 값과 비교한다.
     *
     * ⚠️ **화면이 아니라 여기 둔다.** 화면에 `remember`로 두면 회전할 때 사라지고,
     *    그러면 아무것도 안 바꾼 저장이 서버까지 나간다.
     */
    private var original: String? = null

    /** 서버 기능이 있는 빌드인가. */
    val savable: Boolean get() = source != null

    /**
     * 화면을 열 때 부른다.
     *
     * 🔴 **닉네임을 못 받았으면(null) 열지 않는다** — 판단은 [openable]이 한다.
     */
    fun open(current: String?) {
        original = current
        input = current.orEmpty()
        saving = false
    }

    fun onInputChange(next: String) {
        input = next
    }

    /**
     * `저장`.
     *
     * 🔴 **문구를 띄우는 것과 화면을 닫는 것을 따로 받는다.** 하나로 묶으면
     *    `닉네임은 10자까지 쓸 수 있어요`를 띄우면서 **화면이 닫힌다** — 사용자는
     *    고칠 자리를 잃고, 고치라는 말만 듣는다. 실패(네트워크)도 같은 이유로 안 닫는다:
     *    닫아 버리면 방금 쓴 이름이 사라져서 **처음부터 다시 쓰게** 된다.
     *
     * @param onToast 띄울 문구.
     * @param onClose 화면을 닫는다. **저장됐거나 바꾼 게 없을 때만** 부른다.
     * @param onSaved 저장이 실제로 됐을 때만 부른다 — 화면 20이 서버를 다시 읽는다.
     */
    fun save(onToast: (CfToast) -> Unit, onClose: () -> Unit, onSaved: () -> Unit) {
        if (saving) return
        // 🔴 **문구 선택을 여기서 한다.** 세 갈래(빈 값·10자 초과·정상)가 서로 다른
        //    문구이고, 화면에서 `if`로 나누면 그 판단이 **어느 층에서도 검증되지 않는다**
        //    ([FriendsViewModel.add]와 반대로 하는 이유: 여기는 고를 문구가 셋이고
        //    그 기준이 [NicknameRules]에 있다).
        when (val verdict = NicknameRules.validate(input)) {
            // 두 갈래 모두 **화면을 닫지 않는다**(위 주석).
            NicknameRules.Verdict.Empty -> onToast(CfToast.NICKNAME_EMPTY)
            NicknameRules.Verdict.TooLong -> onToast(CfToast.NICKNAME_TOO_LONG)
            is NicknameRules.Verdict.Ok -> {
                if (!NicknameRules.changed(original, verdict.value)) {
                    // ⚠️ 서버를 안 부르고 **닫기만 한다.** `프로필을 저장했어요`도 안 띄운다 —
                    //    아무것도 안 바뀐 저장에 성공 문구를 띄우면 그 표시를 믿을 수 없게 된다.
                    onClose()
                    return
                }
                val src = source ?: run {
                    onToast(CfToast.NETWORK_ERROR)
                    return
                }
                saving = true
                viewModelScope.launch {
                    when (src.updateNickname(verdict.value)) {
                        is ProfileResult.Saved -> {
                            saving = false
                            // 🔴 **순서가 중요하다.** 먼저 서버를 다시 읽게 하고 나서
                            //    닫는다 — 반대로 하면 화면 20이 **옛 닉네임을 그린 채로**
                            //    돌아오고, 사용자는 저장이 안 된 것으로 읽는다.
                            onSaved()
                            onToast(CfToast.PROFILE_SAVED)
                            onClose()
                        }

                        ProfileResult.Invalid -> {
                            saving = false
                            // 여기까지 올 수 없다(위에서 이미 검증했다). 그래도
                            // 네트워크 문구로 뭉개지 않는다 — 원인이 다르면 볼 곳도 다르다.
                            onToast(CfToast.NICKNAME_EMPTY)
                        }

                        is ProfileResult.Failed, ProfileResult.NotConfigured -> {
                            saving = false
                            onToast(CfToast.NETWORK_ERROR)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * 이 화면을 열 수 있는가.
         *
         * 🔴 **닉네임을 못 받은 상태에서 열면 안 된다**(A 문서 3절 ④). 빈 칸이 뜨고,
         *    거기서 저장하면 **닉네임을 지운다** — 화면 20이 그 자리에 `다시 시도`를
         *    두는 것과 같은 이유다. 키 없는 빌드에서도 열지 않는다(저장할 곳이 없다).
         *
         * ⚠️ 화면이 아니라 여기 둔다 — JVM에서 재려면 Composable 밖이어야 한다.
         */
        fun openable(nickname: String?, savable: Boolean): Boolean =
            savable && !nickname.isNullOrBlank()
    }
}
