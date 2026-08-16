package com.catchflower.app.ui.my

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.core.AccountDeletionRules
import com.catchflower.app.core.AccountDeletionRules.Phase
import com.catchflower.app.data.AccountDeletionResult
import com.catchflower.app.data.AccountDeletionService
import com.catchflower.app.data.AccountDeletionSource
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.LocalDataWiper
import kotlinx.coroutines.launch

/**
 * 화면 20-2 8행 `회원 탈퇴`의 상태(A 문서 3절 ⑪).
 *
 * 2026-08-16 출시 준비. 순서·판정은 [AccountDeletionRules]가 원본이다.
 *
 * 🔴 **`@JvmOverloads`가 없으면 화면을 여는 순간 죽고 컴파일은 통과한다**
 *    (다섯 번 당했다 · [ProfileEditViewModel] 주석).
 *
 * ## 🔴 기기를 지우는 조건이 이 파일에서 가장 중요한 한 줄이다
 *
 * [AccountDeletionRules.mayWipeDevice]가 true일 때만 [LocalDataWiper]를 부른다.
 * 반대로 하면(실패해도 지우면) 사용자는 탈퇴됐다고 믿고, 서버에는 계정과 발견 기록이
 * 남고, **토큰이 없어져서 다시 시도할 수도 없다.** 화면은 성공과 똑같다.
 */
class AccountDeletionViewModel @JvmOverloads constructor(
    app: Application,
    /**
     * null이면 서버를 부를 수 없는 빌드다. **그래도 탈퇴는 된다** —
     * 기기 데이터만 지우고, 서버에는 만들어진 계정이 없다
     * ([AccountDeletionResult.NothingOnServer]).
     */
    private val source: AccountDeletionSource? = DiscoveryRepository.get(app).auth?.let { auth ->
        AccountDeletionService(auth, myUserId = { DiscoveryRepository.get(app).userId })
    },
    /** ⚠️ 주입한다 — 기기를 실제로 지우는 일을 테스트가 하면 안 된다. */
    private val wipe: () -> Int = { LocalDataWiper.wipe(app) },
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : AndroidViewModel(app) {

    /** null이면 다이얼로그를 안 그린다. 그 외에는 A 문서 3절 ⑪의 네 가지 얼굴이다. */
    var phase by mutableStateOf<Phase?>(null)
        private set

    /** 설정 8행을 눌렀다. */
    fun requestDelete() {
        // ⚠️ 진행 중에 다시 누르면 무시한다 — 요청을 두 벌 보내면 실패 판정이 섞인다.
        if (phase is Phase.Running) return
        phase = Phase.Confirm
    }

    /**
     * `취소`.
     *
     * ⚠️ [Phase.Done]에서는 닫지 않는다 — 그 상태에서 닫으면 **이미 계정이 없는 앱**을
     *    계속 쓰게 되고, 화면은 지워진 도감을 그린다. 닫는 대신 [restart]를 부른다.
     */
    fun dismiss() {
        if (phase is Phase.Done || phase is Phase.Running) return
        phase = null
    }

    /** `탈퇴하기` / `다시 시도`. 둘 다 **처음부터** 돌린다([AccountDeletionRules]). */
    fun confirm() {
        if (phase is Phase.Running) return
        phase = Phase.Running
        viewModelScope.launch {
            val result = source?.deleteServerData() ?: AccountDeletionResult.NothingOnServer
            val failedAt = (result as? AccountDeletionResult.Failed)?.step
            if (!AccountDeletionRules.mayWipeDevice(failedAt)) {
                log("탈퇴 실패 · $result — 기기 데이터를 지우지 않았다(다시 시도할 수 있다)")
                phase = Phase.Failed(failedAt)
                return@launch
            }
            // 🔴 지우지 못한 파일이 있어도 성공이다. 여기서 실패로 되돌리면
            //    **서버 계정은 이미 없는데** 사용자는 `다시 시도`만 반복한다.
            val left = wipe()
            if (left > 0) log("탈퇴: 기기 파일 ${left}개를 지우지 못했다 — 앱 삭제로 사라진다")
            phase = Phase.Done
        }
    }
}
