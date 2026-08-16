package com.catchflower.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.catchflower.app.data.KakaoLinkService
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import kotlinx.coroutines.launch

/**
 * 로그인 요구 시트. 막힌 액션을 누르면 뜬다(`LoginGate.requiresLogin` == true).
 *
 * ⚠️ **`판별 2회를 다 쓴 뒤`만이 아니다**(2026-08-16에 이 첫 줄을 고쳤다). 댓글·좋아요·
 *    신고·친구 요청·닉네임 변경은 **0회**라 첫 번째 누름에서 바로 뜬다 —
 *    `LoginGate.GatedAction` 표가 원본이다. 시트는 어느 행동인지 모른 채 뜬다
 *    (제목이 하나뿐이라 구분할 필요가 없다 · 아래 `설명 줄이 없다` 참조).
 *
 * ## 문구 (A 문서 3절 `로그인 없이 쓸 수 있는 범위 — 판별 2회` 표)
 *
 * | 칸 | 문구 |
 * |---|---|
 * | 제목 | `로그인하시고 더 많은 꽃을 만나보세요` |
 * | 확인 | `카카오로 3초 만에 시작하기` |
 * | 취소 | `나중에 할게요` |
 *
 * 🔴 **여기서 문구를 만들지 않는다.** 제목은 오너가 준 문장이고(맞춤법만 고쳤다),
 *    아래 두 개는 A 문서 2절 화면 01 / 1절 Ghost 예시에서 **그대로 가져온 것**이다.
 *    [com.catchflower.app.ui.CopySourceTest]는 A 문서 **표 칸**만 승인으로 인정한다 —
 *    이 주석은 승인이 아니다. 실제로 내 주석이 금지어를 승인해 버린 사고가 두 번 있었다.
 *
 * ## ⚠️ 설명 줄이 없다
 *
 * A 문서 1절은 "왜 못 하는지 화면에 적는다"를 요구하는데, **`2회를 다 썼어요`류의
 * 문구가 A 문서에 없다.** 지어내지 않았다(A 문서 4절 19번 · 오너 미답).
 * 그래서 지금 시트는 제목 한 줄 + 버튼 두 개다.
 *
 * 🔴 이건 "안내가 충분하다"는 뜻이 **아니다.** 3번째 촬영에서 갑자기 시트가 뜨는데
 *    이유가 안 적혀 있으면 고장으로 읽힐 수 있다 — 문구가 오면 제목 아래에 한 줄을
 *    넣는다(파라미터를 미리 뚫어 두지 않았다. 안 쓰는 파라미터는
 *    "안내가 있다"는 착각을 만든다).
 *
 * ## ⚠️ `3초 만에`는 지금 참이 아닐 수 있다
 *
 * 로그인은 **웹 OAuth**라 브라우저가 뜬다(`KakaoLogin` 주석). 카카오톡 간편로그인이
 * 아니어서 3초가 아닐 수 있다. A 문서 4절 20번에 올려 뒀고, 오너 답이 오기 전까지는
 * **문구를 바꾸지 않는다** — 개발자가 고칠 문장이 아니다.
 *
 * 🔴 연결은 **`/auth/v1/user/identities/authorize`**로 한다 —
 *    `/auth/v1/authorize`는 새 세션을 만들고 익명 uuid를 버려서 **도감이 사라진다**
 *    (`SupabaseAuthUrls.linkIdentity` 주석). 그 선택은 [KakaoLinkService]가 한다.
 *
 * 🔴 **닫은 뒤 원래 행동을 이어서 하지 않는다** — `나중에 할게요`를 눌렀는데 판별이
 *    시작되면 게이트가 없는 것과 같다.
 *
 * ## 시트 + 카카오 연결 시작을 **한 함수로** 묶었다
 *
 * 🔴 **시트만 그리는 공개 함수를 따로 두지 않았다.** 두 개가 있으면 한쪽이
 *    `KakaoLinkLauncher`를 안 부르는 채로 쓰이고, 그때 `카카오로 3초 만에
 *    시작하기`는 **눌리지만 아무 일도 하지 않는다** — 죽은 버튼 14개가 그 모양이었다.
 *
 * ⚠️ **네 곳에서 쓴다**(촬영 흐름 · 화면 16 · 화면 19 · 화면 20 = `프로필 수정`).
 *    2026-08-16에 둘에서 넷이 됐다 — 화면 16은 **시트를 두 개** 그린다(`RecordViewModel`과
 *    `FriendsViewModel`이 각자 판정 주인이다). 각자 [KakaoLinkLauncher]를 부르게 두면
 *    실패 처리가 갈리고, 한쪽만 고쳐진다 — 그때 증상은 "어떤 화면에서는 눌러도
 *    아무 일이 없다"이고 원인이 화면에 안 보인다.
 *
 * @param visible false면 아무것도 그리지 않는다. 판정은 ViewModel이 이미 했다
 *   (`LoginGate.requiresLogin`) — 여기서 횟수를 다시 세지 않는다.
 */
@Composable
fun LoginGateSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LoginRequiredSheetBody(
        onLogin = {
            scope.launch {
                when (val result = KakaoLinkLauncher.start(context)) {
                    // 브라우저로 나갔다. 마무리는 `AuthCallbackActivity`가 한다.
                    KakaoLinkLauncher.Result.Opened -> onDismiss()
                    // ⚠️ **시트를 닫지 않는다** — 닫으면 아무 일도 안 일어난 것처럼 보인다.
                    //    이유를 말하고 다시 누를 수 있게 둔다.
                    KakaoLinkLauncher.Result.NoBrowser,
                    is KakaoLinkLauncher.Result.Failed,
                    -> {
                        android.util.Log.w("CatchFlower", "카카오 연결 시작 실패: $result")
                        android.widget.Toast.makeText(
                            context,
                            // ⚠️ **전용 문구가 A 문서에 없다**(4절 19번). `잠시 후 다시`가
                            //    `manual_linking_disabled`에는 틀린 안내지만, 지어낸
                            //    문구보다 낫다 — 원인은 위 로그에 남는다.
                            CfToast.NETWORK_ERROR.message,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        },
        onDismiss = onDismiss,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LoginRequiredSheetBody(
    onLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CfColor.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CfDimen.ScreenPadding)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "로그인하시고 더 많은 꽃을 만나보세요",
                style = CfText.ScreenTitle,
                color = CfColor.TextPrimary,
            )

            Spacer(Modifier.height(CfDimen.GapLarge))

            CfPrimaryButton(text = "카카오로 3초 만에 시작하기", onClick = onLogin)

            Spacer(Modifier.height(CfDimen.GapSmall))

            // Ghost — 회피 행동. A 문서 1절이 `나중에 할게요`를 Ghost 예시로 든다.
            CfGhostButton(text = "나중에 할게요", onClick = onDismiss)

            Spacer(Modifier.height(CfDimen.Gap))
        }
    }
}
