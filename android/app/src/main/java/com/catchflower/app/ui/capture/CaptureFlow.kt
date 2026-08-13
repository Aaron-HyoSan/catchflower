package com.catchflower.app.ui.capture

import android.widget.Toast
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.ui.component.CfToast
import androidx.compose.ui.platform.LocalContext

/**
 * 화면 07~12를 잇는 호스트.
 *
 * **왜 한 곳에서 분기하는가**: 이 흐름은 뒤로 가기가 단순하지 않다.
 * 09에서 `아니에요`는 07로, 12에서 `나중에`는 도감으로, 10·11의 버튼은 지도(13)로 간다.
 * 화면마다 다음 목적지를 알게 하면 흐름이 흩어진다.
 *
 * @param onExit 흐름을 완전히 벗어난다 (도감으로). 하단 내비를 다시 보여줘야 한다.
 * @param onShared 화면 13에서 공유를 마쳤다 → **지도(화면 14)로 간다**(와이어프레임 13 흐름).
 *   토스트(`지도에 공유했어요`)는 여기서 띄운다.
 * @param onSkipShare 화면 13 `공유하지 않기` → **도감 상세(화면 05)로 간다**
 *   (와이어프레임 13 주석 ⑤). 토스트(`도감에는 저장됐어요`)도 여기서 띄운다.
 */
@Composable
fun CaptureFlow(
    onExit: () -> Unit,
    onShared: () -> Unit,
    onSkipShare: (flowerId: Int) -> Unit,
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
            // 🔴 `onHelp = {}`를 넘기고 있었다 — **`도움말`이 눌러도 아무 일이 없었다**
            //    (2026-08-13에 고쳤다). 화면 07이 팁 시트를 **직접** 띄우므로 인자가
            //    없어졌다. `onExit`에 붙이지 않은 판단은 그대로 유효하다 —
            //    "도움말을 눌렀는데 화면이 나가진다"가 된다.
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
                    collectedCount = state.collectedCount,
                    seasonCount = state.seasonCount,
                    onShare = { vm.openShareSettings(state.discoveryId, state.flowerId) },
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
                    onShare = { vm.openShareSettings(state.discoveryId, state.flowerId) },
                    onKeepPrivate = {
                        vm.backToCamera()
                        onExit()
                    },
                    modifier = insetModifier,
                )
            }
        }

        is CaptureState.ShareSettings -> {
            val flower = repository.byId(state.flowerId)
            val discovery = vm.discovery(state.discoveryId)
            // ⚠️ **둘 중 하나가 없으면 나간다.** 기록을 못 찾는 것은 저장이 어긋난
            //    경우뿐이고, 없는 기록의 공개 범위를 묻는 화면은 무엇을 눌러도
            //    아무 데도 가지 않는다.
            if (flower == null || discovery == null) {
                vm.skipShare()
                onExit()
            } else {
                ShareSettingsScreen(
                    flower = flower,
                    discovery = discovery,
                    discoveryCount = state.discoveryCount,
                    photo = vm.photoFile(discovery),
                    onShare = { visibility, note ->
                        vm.share(state.discoveryId, visibility, note)
                        Toast.makeText(
                            context,
                            CfToast.MAP_SHARED.message,
                            Toast.LENGTH_LONG,
                        ).show()
                        onShared()
                    },
                    onSkip = {
                        vm.skipShare()
                        // 🔴 **토스트가 반드시 있어야 한다.** `공유하지 않기`는 아무것도
                        //    바꾸지 않으므로(기록은 이미 비공개다), 안내가 없으면
                        //    "방금 찍은 꽃이 어디로 갔는지" 알 수 없다.
                        Toast.makeText(
                            context,
                            CfToast.DEX_SAVED_ONLY.message,
                            Toast.LENGTH_LONG,
                        ).show()
                        onSkipShare(state.flowerId)
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
