package com.catchflower.app.ui.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.catchflower.app.core.AccountDeletionRules.Phase
import com.catchflower.app.core.LegalDoc
import com.catchflower.app.core.SettingsRules
import com.catchflower.app.ui.component.AppRestart
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.ExternalOpen
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 20-2 `설정`. 문구는 **A 문서 3절 ⑤·⑨·⑪이 전부다.**
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸며 생겼다 — 화면 20 헤더의 `설정`이
 * 그때까지 `아직 준비 중이에요`였다.
 *
 * ⚠️ **와이어프레임에 없는 화면이다.** 그래도 만드는 이유는 A 문서 3절 ⑤가 이 행들을
 *    적어 뒀기 때문이고, 헤더 버튼을 지우는 쪽을 고르지 않은 것은 오너 결정
 *    (`죽어있는 버튼 없도록 전부 구현해다오`)이다.
 *
 * 🔴 **행이 8개인데 7개만 나올 수 있다.** `고객문의`는 창구 주소가 있는 빌드에만
 *    나온다 — 판단은 [SettingsRules.rows]에 있고, 지금 빌드에서는 **안 나온다**(4절 17번).
 *
 * 🔴 **2026-08-16에 4행이 늘었다**(개인정보 처리방침 · 이용약관 · 위치기반서비스
 *    이용약관 · 회원 탈퇴). 앞 셋과 탈퇴는 **Play 심사 통과 조건**이고, 없을 때
 *    증상이 나오는 곳은 화면이 아니라 심사다.
 */
@Composable
fun SettingsScreen(
    /** `앱 버전` 값 행에 그대로 쓴다. 부르는 쪽이 `BuildConfig.VERSION_NAME`을 넘긴다. */
    version: String,
    /** 화면 02를 연다. 화면 20 활동 지역 칸과 **같은 자리**를 연다. */
    onPickRegion: () -> Unit,
    /** 화면 20-3을 연다. 어떤 문서인지는 [SettingsRules.legalDoc]이 정한다. */
    onOpenLegal: (LegalDoc) -> Unit,
    onBack: () -> Unit,
    /**
     * 창구 메일 주소. 기본값은 빌드에 주입된 값이다.
     *
     * ⚠️ 인자로 받는 이유는 **테스트가 아니라 미리보기**다 — 기본값을 그대로 쓰면
     *    `AppSecrets`가 `BuildConfig`를 읽어서 JVM에서 못 만든다.
     */
    contactEmail: String = com.catchflower.app.core.AppSecrets.contactEmail,
    /**
     * 탈퇴 상태. **기본값을 주지 않는다** — 기본으로 `viewModel()`을 부르면
     * 미리보기에서 죽고, null로 두면 8행이 **눌러도 아무 일 없는 줄**이 된다.
     */
    deletion: AccountDeletionViewModel,
    modifier: Modifier = Modifier,
) {
    val openNotifications = rememberNotificationSettingsOpener()
    val openContact = rememberContactOpener(contactEmail)

    Column(modifier.fillMaxSize()) {
        CfHeader(title = "설정", onBack = onBack)

        SettingsRules.rows(contactEmail).forEach { row ->
            MenuRow(
                label = row.label,
                // `앱 버전`만 값이 있다. 나머지는 눌러서 여는 줄이라 `보기`가 붙는다.
                value = if (row == SettingsRules.Row.VERSION) version else null,
                // 🔴 되돌릴 수 없는 행만 색이 다르다. 판정은 화면이 아니라
                //    [SettingsRules.Row.destructive]에 있다.
                labelColor = if (row.destructive) CfColor.Error else CfColor.TextPrimary,
                // 🔴 **값 행에는 `{}`도 주지 않는다** — 누를 수 있는데 아무 일도 안 하는
                //    줄이 정확히 우리가 지우려는 그 버튼이다([MenuRow] 주석).
                onClick = if (!row.clickable) null else {
                    when (row) {
                        SettingsRules.Row.REGION -> onPickRegion
                        SettingsRules.Row.NOTIFICATION -> openNotifications
                        SettingsRules.Row.CONTACT -> openContact
                        // 세 문서 행은 **같은 화면**을 서로 다른 문서로 연다.
                        // 짝은 [SettingsRules.legalDoc]에 있다 — 여기서 `when`을 또
                        // 쓰면 두 곳이 되고, 새 문서가 한쪽에만 붙는다.
                        SettingsRules.Row.PRIVACY,
                        SettingsRules.Row.TERMS,
                        SettingsRules.Row.LOCATION_TERMS,
                        // 🔴 짝이 없으면(`legalDoc`가 null) **누를 수 없는 줄**이 된다.
                        //    `{}`를 주면 눌리는데 아무 일도 안 한다(죽은 버튼).
                        -> SettingsRules.legalDoc(row)?.let { doc -> { onOpenLegal(doc) } }

                        SettingsRules.Row.DELETE_ACCOUNT -> deletion::requestDelete
                        // 위 `!row.clickable`이 이미 걸렀다. `when`을 다 채우는 것은
                        // 행이 늘어날 때 **여기서 컴파일이 깨지게** 하기 위한 것이다 —
                        // 안 그러면 새 행이 조용히 값 행으로 나온다.
                        SettingsRules.Row.VERSION -> null
                    }
                },
            )
        }
    }

    AccountDeletionDialog(deletion)
}

/**
 * 회원 탈퇴 다이얼로그. **네 가지 얼굴이 하나의 컴포저블이다**(A 문서 3절 ⑪).
 *
 * 🔴 **[Phase.Done]에 `취소`가 없다.** 그 상태에서 닫을 수 있게 두면 계정이 없는 앱을
 *    계속 쓰게 되고, 화면은 빈 도감과 실패한 조회를 그린다 — 사용자는 그것을
 *    "탈퇴가 잘못됐다"로 읽는다. 나가는 문은 `확인` 하나이고, 그것이 재시작이다.
 *
 * ⚠️ **[Phase.Running]에는 버튼이 없다.** 확인 버튼을 남기면 요청이 두 벌 나가고,
 *    취소를 남기면 "취소했는데 서버는 계속 지운다"가 된다(취소할 방법이 없다).
 *
 * ⚠️ 컴포넌트로 빼지 않고 material3 [AlertDialog]를 직접 쓴다 — 두 번째 다이얼로그지만
 *    (첫 번째는 화면 16 댓글 삭제) **모양이 다르다**(상태 4개 · 버튼이 없는 상태).
 *    지금 공통화하면 두 곳 다 안 맞는 추상이 된다.
 */
@Composable
private fun AccountDeletionDialog(vm: AccountDeletionViewModel) {
    val phase = vm.phase ?: return
    val context = LocalContext.current

    val title = when (phase) {
        Phase.Confirm -> "정말 탈퇴할까요?"
        Phase.Running -> "탈퇴를 처리하고 있어요"
        Phase.Done -> "탈퇴가 끝났어요"
        is Phase.Failed -> "탈퇴를 마치지 못했어요"
    }
    val body = when (phase) {
        Phase.Confirm -> "모은 꽃과 발견 기록, 기기의 사진이 모두 지워져요. 되돌릴 수 없어요."
        Phase.Running -> null
        Phase.Done -> "그동안 함께해 주셔서 고맙습니다."
        // 🔴 `아직 아무것도 지워지지 않았어요`가 아니다 — 서버 삭제는 요청 5개이고
        //    중간에 끊기면 앞의 것은 이미 지워졌다(A 문서 3절 ⑪).
        is Phase.Failed -> "다시 시도하면 남은 것부터 이어서 지워요."
    }

    AlertDialog(
        // 진행 중·완료에서는 바깥을 눌러도 닫히지 않는다(판정은 [vm.dismiss]가 한다).
        onDismissRequest = vm::dismiss,
        title = { Text(title, style = CfText.BodyBold, color = CfColor.TextPrimary) },
        text = body?.let { { Text(it, style = CfText.Body, color = CfColor.TextSecondary) } },
        confirmButton = {
            when (phase) {
                Phase.Confirm ->
                    CfTextButton(text = "탈퇴하기", onClick = vm::confirm, color = CfColor.Error)

                Phase.Running -> Unit

                Phase.Done -> CfTextButton(
                    text = "확인",
                    // 🔴 여기서만 프로세스를 다시 띄운다([AppRestart] 주석) —
                    //    화면만 되돌리면 캐시가 지워진 도감을 그대로 그린다.
                    onClick = { AppRestart.toFirstRun(context) },
                )

                is Phase.Failed ->
                    CfTextButton(text = "다시 시도", onClick = vm::confirm)
            }
        },
        dismissButton = {
            // 취소가 있는 상태는 둘뿐이다(확인 · 실패).
            if (phase is Phase.Confirm || phase is Phase.Failed) {
                CfTextButton(text = "취소", onClick = vm::dismiss)
            }
        },
        containerColor = CfColor.Background,
    )
}

/**
 * `알림 설정`을 누르면 할 일. **화면 20 메뉴와 화면 20-2가 같은 것을 쓴다.**
 *
 * ⚠️ A 문서 3절 ⑤가 "설정 화면과 메뉴가 같은 자리를 두 번 연다(둘 다 같은 동작)"고
 *    적었다. 각자 쓰면 한쪽만 실패 문구를 띄우거나, 한쪽이 우리 토글을 만들게 된다.
 */
@Composable
internal fun rememberNotificationSettingsOpener(): () -> Unit {
    val context = LocalContext.current
    val toast = rememberToaster()
    return {
        // 실패는 **두 번 실패한 뒤**다(알림 설정 → 앱 정보). [CfToast.SETTINGS_NO_APP] 주석.
        if (!ExternalOpen.notificationSettings(context)) toast(CfToast.SETTINGS_NO_APP)
    }
}

/**
 * `고객문의`를 누르면 할 일.
 *
 * 🔴 **주소가 없으면 이 함수를 부를 자리 자체가 없어야 한다**
 *    ([SettingsRules.contactVisible]). 그래도 여기서 한 번 더 막는다 — 부르는 곳이
 *    둘이고, 한쪽이 조건을 빼면 **받는 사람이 빈 메일 앱**이 열린다(4절 17번).
 *    막지 못하고 열렸을 때의 증상은 "보냈다고 믿는 문의가 아무 데도 안 간다"다.
 */
@Composable
internal fun rememberContactOpener(address: String): () -> Unit {
    val context = LocalContext.current
    val toast = rememberToaster()
    return {
        if (!ExternalOpen.email(context, address)) toast(CfToast.MAIL_NO_APP)
    }
}
