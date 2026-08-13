package com.catchflower.app.ui.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.catchflower.app.core.SettingsRules
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.ExternalOpen
import com.catchflower.app.ui.component.rememberToaster

/**
 * 화면 20-2 `설정`. 문구는 **A 문서 3절 ⑤가 전부다.**
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸며 생겼다 — 화면 20 헤더의 `설정`이
 * 그때까지 `아직 준비 중이에요`였다.
 *
 * ⚠️ **와이어프레임에 없는 화면이다.** 그래도 만드는 이유는 A 문서 3절 ⑤가 이 네 행을
 *    적어 뒀기 때문이고, 헤더 버튼을 지우는 쪽을 고르지 않은 것은 오너 결정
 *    (`죽어있는 버튼 없도록 전부 구현해다오`)이다.
 *
 * 🔴 **네 행 중 셋만 나올 수 있다.** `고객문의`는 창구 주소가 있는 빌드에만 나온다 —
 *    판단은 [SettingsRules.rows]에 있고, 지금 빌드에서는 **안 나온다**(4절 17번).
 */
@Composable
fun SettingsScreen(
    /** `앱 버전` 값 행에 그대로 쓴다. 부르는 쪽이 `BuildConfig.VERSION_NAME`을 넘긴다. */
    version: String,
    /** 화면 02를 연다. 화면 20 활동 지역 칸과 **같은 자리**를 연다. */
    onPickRegion: () -> Unit,
    onBack: () -> Unit,
    /**
     * 창구 메일 주소. 기본값은 빌드에 주입된 값이다.
     *
     * ⚠️ 인자로 받는 이유는 **테스트가 아니라 미리보기**다 — 기본값을 그대로 쓰면
     *    `AppSecrets`가 `BuildConfig`를 읽어서 JVM에서 못 만든다.
     */
    contactEmail: String = com.catchflower.app.core.AppSecrets.contactEmail,
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
                // 🔴 **값 행에는 `{}`도 주지 않는다** — 누를 수 있는데 아무 일도 안 하는
                //    줄이 정확히 우리가 지우려는 그 버튼이다([MenuRow] 주석).
                onClick = if (!row.clickable) null else {
                    when (row) {
                        SettingsRules.Row.REGION -> onPickRegion
                        SettingsRules.Row.NOTIFICATION -> openNotifications
                        SettingsRules.Row.CONTACT -> openContact
                        // 위 `!row.clickable`이 이미 걸렀다. `when`을 다 채우는 것은
                        // 행이 늘어날 때 **여기서 컴파일이 깨지게** 하기 위한 것이다 —
                        // 안 그러면 새 행이 조용히 값 행으로 나온다.
                        SettingsRules.Row.VERSION -> null
                    }
                },
            )
        }
    }
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
