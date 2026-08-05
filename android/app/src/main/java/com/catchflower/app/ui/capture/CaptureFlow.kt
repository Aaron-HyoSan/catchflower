package com.catchflower.app.ui.capture

import android.widget.Toast
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchflower.app.data.FlowerRepository
import androidx.compose.ui.platform.LocalContext

/**
 * 화면 07~12를 잇는 호스트.
 *
 * **왜 한 곳에서 분기하는가**: 이 흐름은 뒤로 가기가 단순하지 않다.
 * 09에서 `아니에요`는 07로, 12에서 `나중에`는 도감으로, 10·11의 버튼은 지도(13)로 간다.
 * 화면마다 다음 목적지를 알게 하면 흐름이 흩어진다.
 *
 * @param onExit 흐름을 완전히 벗어난다 (도감으로). 하단 내비를 다시 보여줘야 한다.
 * @param onShare 화면 13 지도 공유 설정으로. **아직 없다** — 다음 단계.
 */
@Composable
fun CaptureFlow(
    onExit: () -> Unit,
    onShare: (flowerId: Int) -> Unit,
    modifier: Modifier = Modifier,
    vm: CaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val repository = FlowerRepository.get(context)

    // 네트워크 오류 안내 (A 문서 3절 토스트). **화면 12로 보내지 않는다** —
    // `CaptureViewModel.toast` 주석 참조.
    //
    // ⚠️ 플랫폼 [Toast]를 쓴다. Compose `Snackbar`는 `Scaffold`가 있어야 자리가 잡히는데
    //    이 흐름은 하단 내비 바깥이라 `Scaffold`가 없다. 여기서 스낵바 호스트를 새로 세우면
    //    촬영 화면 전체 레이아웃을 건드려야 한다.
    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast.message, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }

    // ⚠️ 이 흐름은 **하단 내비 바깥**이라 셸(`MainActivity`)의 시스템 여백을 물려받지 못한다.
    //    빼면 화면 12의 제목이 시계와 맞붙고, 하단 버튼이 제스처 바에 닿는다.
    //    화면 07은 프리뷰가 화면을 꽉 채워야 하므로 **제외한다** — 대신 헤더가 직접 여백을 갖는다
    //    (`CameraScreen` 주석 참조). 여기서 07까지 밀면 프리뷰에 검은 띠가 생긴다.
    val insetModifier = modifier.statusBarsPadding().navigationBarsPadding()

    when (val state = vm.state) {
        CaptureState.Camera -> CameraScreen(
            onPhotoTaken = vm::onPhotoTaken,
            onClose = onExit,
            // 도움말은 화면이 따로 없다 (기획서에 없음). 촬영 팁은 화면 12가 갖고 있으므로
            // 지금은 닫기와 같게 두지 않고 아무것도 하지 않는다 —
            // TODO(다음 단계): 촬영 팁 시트. 지금 onExit에 붙이면 "도움말을 눌렀는데 나가진다".
            onHelp = {},
            modifier = modifier,
        )

        is CaptureState.Analyzing -> AnalyzingScreen(
            jpeg = state.jpeg,
            overdue = state.overdue,
            onOverdue = vm::markOverdue,
            onCancel = vm::backToCamera,
            modifier = insetModifier,
        )

        is CaptureState.Confirm -> ConfirmScreen(
            jpeg = state.jpeg,
            outcome = state.outcome,
            similarNamesOf = { it.similarFlowerNames },
            onConfirm = vm::confirm,
            onRetake = vm::backToCamera,
            modifier = insetModifier,
        )

        is CaptureState.Failed -> IdentifyFailedScreen(
            streak = state.streak,
            onRetake = vm::backToCamera,
            onGiveUp = {
                vm.giveUp()
                onExit()
            },
            modifier = insetModifier,
        )

        is CaptureState.NewFlower -> {
            val flower = repository.byId(state.flowerId)
            if (flower == null) {
                onExit()
            } else {
                NewFlowerScreen(
                    flower = flower,
                    dexOrder = state.dexOrder,
                    // TODO: 저장이 붙으면 실제 수집 수로 바꾼다 (지금은 더미).
                    collectedCount = state.dexOrder,
                    seasonCount = SEASON_COUNT_PLACEHOLDER,
                    onShare = { onShare(state.flowerId) },
                    onKeepPrivate = {
                        vm.backToCamera()
                        onExit()
                    },
                    modifier = insetModifier,
                )
            }
        }

        is CaptureState.Rediscovered -> {
            val flower = repository.byId(state.flowerId)
            if (flower == null) {
                onExit()
            } else {
                RediscoveredScreen(
                    flower = flower,
                    count = state.count,
                    onShare = { onShare(state.flowerId) },
                    onKeepPrivate = {
                        vm.backToCamera()
                        onExit()
                    },
                    modifier = insetModifier,
                )
            }
        }

        is CaptureState.DailyDuplicate -> {
            val flower = repository.byId(state.flowerId)
            if (flower == null) {
                onExit()
            } else {
                DailyDuplicateScreen(
                    flower = flower,
                    onOpenDex = {
                        vm.backToCamera()
                        onExit()
                    },
                    onCaptureAnother = vm::backToCamera,
                    modifier = insetModifier,
                )
            }
        }
    }
}

/** 시즌 종수는 저장이 붙어야 세진다. 더미 값이라는 걸 이름으로 남긴다. */
private const val SEASON_COUNT_PLACEHOLDER = 13
