package com.catchflower.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catchflower.app.data.LocationPermissionPrompt
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import kotlinx.coroutines.launch

/**
 * 화면 07 카메라 — `07_카메라.svg`.
 *
 * **어두운 화면이다** (A 문서 표기). 앱 전체는 라이트인데 이 화면만 검다 —
 * 프리뷰가 화면을 채우므로 UI가 밝으면 사진이 안 보인다.
 * 그래서 [CfColor]를 쓰지 않고 이 화면 안에서 색을 정한다.
 *
 * ⚠️ **앨범 버튼을 넣지 않는다.** 기획서 5장 규칙이고, 와이어프레임 주석 ③이
 *    "앨범 버튼을 아예 두지 않고, 왜 없는지를 문구로 설명해야 '기능 고장'으로
 *    오해하지 않는다"고 못 박았다. 그래서 고지 문구가 자리를 차지한다.
 */
@Composable
fun CameraScreen(
    onPhotoTaken: (ByteArray) -> Unit,
    onClose: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // --- 위치 (선택) ---
    // ⚠️ **매니페스트 선언만으로는 아무 일도 안 일어난다.** 요청을 아무도 하지 않으면
    //    `checkSelfPermission`이 영구히 DENIED고, `PlatformLocationSource`는 늘 null을
    //    돌려준다 — 등록은 정상으로 보이고 `place_name`·`dong_code`만 조용히 빈다.
    //    그러면 B-6 랭킹이 통째로 죽는데 화면에는 증상이 없다.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 거부해도 등록은 그대로 된다 — 결과로 흐름을 바꾸지 않는다 */ }

    LaunchedEffect(Unit) {
        // ⚠️ **한 번만 묻는다.** 촬영마다 다시 물으면 화면 03의 약속
        //    (`허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`)이
        //    안내가 아니라 강요가 된다. 거부한 사람에게 재요청은 설정 화면에서 한다.
        if (LocationPermissionPrompt.shouldAsk(context)) {
            LocationPermissionPrompt.markAsked(context)
            // ⚠️ COARSE와 FINE을 **함께** 요청한다. API 31+에서 FINE만 요청하면
            //    `대략적 위치` 선택지가 나오지 않는다. 동 단위·100m면 대략도 충분하다.
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var useFront by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    // 셔터 연타 방지. 두 번 찍히면 화면 08이 두 번 뜬다.
    var capturing by remember { mutableStateOf(false) }

    // 가이드 프레임의 실제 위치. **이 값으로 크롭 영역을 정한다** —
    // 화면에 보이는 네모와 전송 영역을 같게 만드는 유일한 방법이다 (주석 ②).
    var guide by remember { mutableStateOf<GuideRect?>(null) }

    Box(modifier.fillMaxSize().background(CfColor.SurfaceDark)) {
        if (granted) {
            val previewView = remember {
                PreviewView(context).apply {
                    // FILL_CENTER — 가이드 프레임 안이 곧 전송 영역이 되게 한다.
                    // FIT_CENTER면 프리뷰에 여백이 생겨 크롭 영역과 어긋난다.
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            LaunchedEffect(useFront, flashOn) {
                when (
                    val result = bindCamera(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = previewView.surfaceProvider,
                        useFrontCamera = useFront,
                        flashMode = if (flashOn) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        },
                    )
                ) {
                    is CameraBindResult.Ready -> {
                        capture = result.capture
                        bindError = null
                    }
                    is CameraBindResult.Failed -> {
                        capture = null
                        bindError = result.cause.message ?: "카메라를 열 수 없어요"
                    }
                }
            }
        }

        CameraOverlay(
            onClose = onClose,
            onHelp = onHelp,
            flashOn = flashOn,
            onToggleFlash = { flashOn = !flashOn },
            onSwitch = { useFront = !useFront },
            shutterEnabled = granted && capture != null && !capturing,
            onGuideMeasured = { guide = it },
            onShutter = {
                val target = capture ?: return@CameraOverlay
                capturing = true
                scope.launch {
                    try {
                        onPhotoTaken(target.takeSquareJpeg(context, guide))
                    } catch (e: Exception) {
                        bindError = "사진을 찍지 못했어요"
                    } finally {
                        capturing = false
                    }
                }
            },
            notice = when {
                !granted -> "카메라를 쓸 수 있게 허용해 주세요"
                bindError != null -> bindError
                else -> null
            },
        )
    }
}

/**
 * 프리뷰 위에 얹는 UI 전체.
 *
 * 프리뷰와 분리한 이유는 **미리보기(@Preview)와 테스트에서 카메라 없이 볼 수 있게** 하려는 것이다.
 * 에뮬레이터 카메라가 안 붙는 상황에서도 배치를 확인할 수 있다.
 */
@Composable
private fun CameraOverlay(
    onClose: () -> Unit,
    onHelp: () -> Unit,
    flashOn: Boolean,
    onToggleFlash: () -> Unit,
    onSwitch: () -> Unit,
    shutterEnabled: Boolean,
    onShutter: () -> Unit,
    notice: String?,
    onGuideMeasured: (GuideRect) -> Unit = {},
) {
    // 프리뷰는 이 Column 전체 뒤에 깔려 있으므로 뷰포트는 화면 전체다.
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it },
    ) {

        // --- 헤더: 닫기 / 꽃 촬영 / 도움말 ---
        // ⚠️ `statusBarsPadding`은 헤더가 **직접** 가져야 한다. 촬영 흐름은 하단 내비
        //    바깥에서 그려지므로 셸의 여백을 물려받지 못한다. 빼면 `닫기`가 시계와 겹친다.
        Row(
            Modifier
                .fillMaxWidth()
                .background(CameraChrome)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CfTextButton(text = "닫기", onClick = onClose, color = Color.White)
            Text(
                text = "꽃 촬영",
                style = CfText.ScreenTitle,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CfTextButton(text = "도움말", onClick = onHelp, color = Color.White)
        }

        // --- 가이드 영역 ---
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                // ⚠️ 프리뷰 위의 흰 글씨는 **밝은 피사체에서 사라진다.**
                //    와이어프레임은 프리뷰를 `#3f3f3f` 회색 판으로 그려서 이 문제가
                //    설계 단계에서는 보이지 않았다. 실제로 흰 벽·하늘·밝은 꽃을 비추면
                //    안내 문구를 읽을 수 없다 — 중장년 타깃 명도차 4.5:1 요건 위반이다.
                //    그래서 문구 뒤에 반투명 판을 깐다 (색은 유지, 배경만 보장).
                Text(
                    "꽃 한 송이를 네모 안에 꽉 채워 주세요",
                    style = CfText.BodyBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(TextScrim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(16.dp))

                // 정사각 가이드 — 이 안이 **실제로 AI에 전송되는 영역**이다
                // (와이어프레임 주석 ②). 장식이 아니다.
                //
                // ⚠️ 위치를 실제로 재서 올려보낸다. 프레임은 헤더·안내 문구 아래에 있어
                //    **화면 중앙이 아니다.** 재지 않고 "이미지 중앙"을 자르면 네모 안과
                //    다른 영역이 전송된다 — 사진은 정상으로 보이고 인식률만 떨어진다.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .onGloballyPositioned { coordinates ->
                            if (viewport == IntSize.Zero) return@onGloballyPositioned
                            val origin = coordinates.positionInRoot()
                            onGuideMeasured(
                                GuideRect(
                                    viewportWidth = viewport.width.toFloat(),
                                    viewportHeight = viewport.height.toFloat(),
                                    left = origin.x,
                                    top = origin.y,
                                    size = coordinates.size.width.toFloat(),
                                )
                            )
                        }
                        .drawGuideCorners(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "너무 멀면 잘 못 알아봐요",
                    style = CfText.Body,
                    color = GuideText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(TextScrim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            notice?.let {
                Text(
                    text = it,
                    style = CfText.BodyBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp)
                        .background(NoticeBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        // --- 앨범 차단 고지 ---
        // 버튼이 없는 이유를 말해 주는 자리다. 지우면 "기능 고장"으로 읽힌다.
        Text(
            text = "앨범 사진은 등록할 수 없어요. 직접 찍어 주세요.",
            style = CfText.Body,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(CameraChrome)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
                .background(NoticeBg, RoundedCornerShape(10.dp))
                .padding(vertical = 12.dp),
        )

        // --- 셔터 줄 ---
        Row(
            Modifier
                .fillMaxWidth()
                .background(CameraChrome)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CameraSideButton(
                // 플래시는 상태가 보여야 한다. 아이콘만 두면 켜졌는지 알 수 없다.
                label = if (flashOn) "플래시 끄기" else "플래시",
                onClick = onToggleFlash,
            )

            // 셔터 지름 80px (와이어프레임 주석 ④). 라벨 `찍기`를 안에 넣는다 —
            // 아이콘만 있는 버튼을 만들지 않는다는 원칙이 여기도 적용된다.
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (shutterEnabled) Color.White else ShutterDisabled)
                    // Disabled일 때도 자리를 지킨다 — 사라지면 "버튼이 없어졌다"로 읽힌다.
                    .clickable(enabled = shutterEnabled, onClickLabel = "찍기", onClick = onShutter),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "찍기",
                    style = CfText.ButtonSmall,
                    color = if (shutterEnabled) CfColor.SurfaceDark else Color.White,
                )
            }

            CameraSideButton(label = "전환", onClick = onSwitch)
        }
    }
}

/** 좌우 보조 버튼. 최소 터치 영역 44px (와이어프레임 주석 ④). */
@Composable
private fun CameraSideButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(CfDimen.MinTouch + 16.dp)
            .clip(CircleShape)
            .background(SideButtonBg)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CfText.Tiny, color = Color.White, textAlign = TextAlign.Center)
    }
}

/**
 * 네 귀퉁이 ㄱ자 브래킷.
 *
 * 사각형 전체를 그리지 않는 이유: 실선 테두리는 꽃과 겹쳐 **피사체를 가린다.**
 * 와이어프레임도 귀퉁이만 그렸다.
 */
private fun Modifier.drawGuideCorners(): Modifier = drawBehind {
    val len = size.minDimension * 0.14f
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Square)
    val corners = listOf(
        Offset(0f, 0f) to listOf(Offset(len, 0f), Offset(0f, len)),
        Offset(size.width, 0f) to listOf(Offset(size.width - len, 0f), Offset(size.width, len)),
        Offset(0f, size.height) to
            listOf(Offset(len, size.height), Offset(0f, size.height - len)),
        Offset(size.width, size.height) to listOf(
            Offset(size.width - len, size.height),
            Offset(size.width, size.height - len),
        ),
    )
    // ⚠️ 흰 선만 그으면 **밝은 피사체 위에서 프레임이 사라진다** (흰 벽·하늘·연한 꽃).
    //    프레임은 곧 전송 영역이라 안 보이면 사용자가 꽃을 어디에 맞출지 알 수 없다.
    //    사각형을 채우거나 두껍게 하면 피사체를 가리므로, 흰 선 뒤에 검은 선을
    //    한 겹 더 깔아 어느 배경에서도 경계가 남게 한다.
    corners.forEach { (origin, ends) ->
        ends.forEach { end ->
            drawLine(
                color = Color.Black.copy(alpha = 0.55f),
                start = origin,
                end = end,
                strokeWidth = stroke.width * 2.2f,
                cap = StrokeCap.Square,
            )
            drawLine(
                color = Color.White,
                start = origin,
                end = end,
                strokeWidth = stroke.width,
                cap = StrokeCap.Square,
            )
        }
    }
}


private val CameraChrome = Color(0xFF262626)

/** 프리뷰 위 문구 배경. 밝은 피사체에서도 흰 글씨가 읽히게 하는 최소한의 판. */
private val TextScrim = Color(0xCC1A1A1A)
private val GuideText = Color(0xFFCCCCCC)
private val NoticeBg = Color(0xFF3A3A3A)
private val SideButtonBg = Color(0xFF3A3A3A)
private val ShutterDisabled = Color(0xFF6B6B6B)
