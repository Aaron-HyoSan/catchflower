package com.catchflower.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

/**
 * 화면 14 지도 홈 — `14_지도홈.svg`.
 *
 * ## 예선 범위
 *
 * 검색창·필터 칩(`전체 / 친구 / 이번 주 / 못 모은 꽃`)·`길찾기`·`목록`은 **넣지 않는다.**
 * 남의 기록을 읽는 서버 함수가 없어서 `친구` 필터는 항상 빈 결과이고,
 * 나머지 칩도 필터할 대상이 내 기록뿐이라 **누를 이유가 없는 버튼**이 된다.
 * 지금 목표는 "내 발견이 핀으로 찍히고, 눌러서 무엇을 찍었는지 보인다"까지다.
 *
 * 🔴 **회색 화면은 오류로 보이지 않는다.** 카카오맵은 인증이 막혀도 **예외를 던지지 않고**
 *    타일만 안 내려준다. 그래서 [MapLifeCycleCallback.onMapError]를 **반드시** 로그로
 *    남기고, 화면에도 안내를 띄운다 — 안 그러면 "지도가 안 나온다"의 원인을
 *    코드에서 찾게 된다(원인은 앱 밖일 수 있다).
 *
 * 🔴 **실패는 되돌릴 수 있어야 한다.** 2026-08-09 실측에서 에뮬레이터 DNS가 3초간 죽어
 *    `MapAuthException(401)`이 났다 — 연결은 곧 돌아왔는데 **화면이 오류 상태에
 *    갇혔다.** 앱을 완전히 죽여야 지도가 나왔다. 지하철·엘리베이터에서 지도를 여는 것이
 *    그 조건이고, 사용자에게는 **"지도 기능이 영구히 고장난 앱"**으로 보인다.
 *    그래서 [retryKey]로 `MapView`를 통째로 다시 만든다 — 자세한 이유는 아래 주석.
 *
 *    ⚠️ 오류를 처음 진단할 때 **콘솔 설정을 먼저 의심했는데 아니었다.** 키 해시
 *       (`origin/…`)와 패키지명이 요청 헤더에 그대로 실려 나가고 있었다.
 *       로그 한 줄(`Unable to resolve host "dapi.kakao.com"`)이 답이었다 —
 *       **401을 인증 실패로만 읽으면 원인을 앱 밖에서 찾는다.**
 */
@Composable
fun MapScreen(
    vm: MapViewModel,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 키 없는 빌드에서는 SDK를 초기화조차 하지 않는다. `KakaoMapSdk.init`에 빈 키를
    // 넘기면 회색 화면이 나오고, 그건 **키가 없는 것과 신청이 안 된 것을 구분할 수
    // 없게** 만든다.
    if (!AppSecrets.hasKakaoMapKey) {
        // ⚠️ **문구도 버튼도 띄우지 않는다** (A 문서 3절 `지도 키가 없는 빌드`).
        //    화면 17의 `NotConfigured`와 같은 판단이다 — 눌러도 안 되고
        //    **사용자 탓이 아니다.** 오류처럼 보이게 하면 안 된다.
        Box(modifier.fillMaxSize())
        return
    }

    var failed by remember { mutableStateOf(false) }

    // 🔴 **`MapView`를 다시 만드는 유일한 방법이 이 키다.** 카카오맵 SDK에는
    //    "인증만 다시 해 봐" API가 없다 — `MapView.start()`는 한 번 실패하면
    //    그 인스턴스로 되돌릴 수 없다. `remember(retryKey)`의 키를 바꿔서
    //    **뷰째로 새로 만드는 것**이 재시도다.
    //
    //    ⚠️ `failed`를 false로 돌리는 것만으로는 부족하다. 그러면 죽은 `MapView`가
    //       그대로 남아 **회색 화면이 보이고**, 그건 오류 안내보다 나쁘다
    //       ("이 동네에 기록이 없다"로 읽힌다).
    var retryKey by remember { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize()) {
        KakaoMapCanvas(
            retryKey = retryKey,
            pins = vm.pins,
            onPinClick = vm::onPinClick,
            onMapClick = vm::onMapClick,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            failed -> MapUnavailable(
                onRetry = {
                    failed = false
                    retryKey++
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(CfColor.Background),
            )

            // A 문서 3절 빈 상태 `지도에 기록 없음`. **문구를 새로 쓰지 않는다.**
            //
            // ⚠️ `loading`이 끝난 뒤에만 띄운다. 읽는 중에 띄우면 기록이 있는
            //    사용자에게도 한 프레임 깜빡인다.
            !vm.loading && vm.pins.isEmpty() -> MapEmptyOverlay(
                onCapture = onCapture,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> vm.selected?.let { pin ->
                PinPreviewCard(
                    pin = pin,
                    flowerSummary = vm.flowerSummary(pin),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 하단 내비(약 72dp)와 촬영 FAB을 피한다.
                        .padding(CfDimen.ScreenPadding)
                        .padding(bottom = 88.dp),
                )
            }
        }
    }
}

/**
 * 카카오맵 [MapView]를 Compose에 끼운다.
 *
 * 🔴 **`AndroidView`의 `factory`에서 `start()`를 부르면 안 된다** — recomposition마다
 *    새 `MapView`가 만들어지는 것은 아니지만, `factory`는 **한 번만** 도는 대신
 *    `update`가 여러 번 돈다. `start()`를 `update`에 두면 지도가 매번 다시 시작한다.
 *    그래서 `remember`로 뷰를 붙들고 `factory`에서 한 번만 시작한다.
 *
 * ⚠️ **`finish()`를 `DisposableEffect`에서 부른다.** 안 부르면 지도 탭을 몇 번 왕복하는
 *    동안 렌더 스레드가 쌓여서 앱이 느려지다 죽는다 — 화면에는 아무 증상이 없다.
 *
 * @param retryKey 값이 바뀌면 **`MapView`를 버리고 새로 만든다.** SDK에 재인증 API가
 *   없어서 이것이 유일한 재시도 경로다. 🔴 이 값을 `remember`의 키에서 빼면
 *   `다시 시도`가 **아무 일도 하지 않는 버튼**이 된다 — 화면은 오류를 지우고
 *   회색 지도를 보여준다.
 */
@Composable
private fun KakaoMapCanvas(
    retryKey: Int,
    pins: List<MapPin>,
    onPinClick: (MapPin) -> Unit,
    onMapClick: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ⚠️ **한 번만 초기화한다.** `init`을 두 번 부르면 두 번째가 예외를 던진다.
    //    `Application.onCreate`에 두지 않은 이유: 지도를 한 번도 안 여는 사용자에게
    //    네이티브 라이브러리 로드 비용을 물리지 않는다(15MB AAR이다).
    //
    // ⚠️ **여기에 `retryKey`를 주지 않는다.** SDK 초기화는 프로세스에 한 번이고,
    //    `isInitialized()` 가드가 있어도 재시도마다 부를 이유가 없다.
    //    다시 만들어야 하는 것은 [MapView]다.
    remember {
        if (!KakaoMapSdk.isInitialized()) {
            runCatching { KakaoMapSdk.init(context, AppSecrets.kakaoNativeAppKey) }
                .onFailure { onError() }
        }
        true
    }

    // 지도가 준비된 뒤에만 핀을 그릴 수 있다. 준비 콜백이 오기 전에 온 핀 목록은
    // 여기 담아 두고, 준비되면 [drawPins]가 한 번에 그린다.
    //
    // ⚠️ **`retryKey`로 같이 비운다.** 안 비우면 죽은 지도의 `KakaoMap`이 남아
    //    [LaunchedEffect]가 **없어진 뷰에 핀을 그린다.**
    var map by remember(retryKey) { mutableStateOf<KakaoMap?>(null) }

    val mapView = remember(retryKey) {
        MapView(context).also { view ->
            view.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {
                        android.util.Log.i("CatchFlower", "지도 종료")
                    }

                    override fun onMapError(e: Exception) {
                        // 🔴 **이 로그가 지도 문제의 유일한 단서다.** 키·해시·패키지명이
                        //    콘솔과 어긋나거나 네트워크가 없으면 여기로 온다.
                        //    **값은 찍지 않는다** — 메시지만 남긴다.
                        //
                        // ⚠️ **메시지만으로는 원인을 못 가린다.** 아래 401 주석 참고 —
                        //    이유는 인증 엔드포인트 **응답 본문**에만 문장으로 있다.
                        //
                        // ⚠️ **`MapAuthException(401)`을 인증 실패로만 읽지 마라.**
                        //    2026-08-09 실측에서 이 401의 원인은 **DNS 실패**였다
                        //    (`Unable to resolve host "dapi.kakao.com"`). SDK가
                        //    호스트를 못 찾은 것을 401로 감싼다 — 키·해시·콘솔 설정을
                        //    뒤지게 만드는 메시지다. 같은 시각 다른 서버 호출도
                        //    (`랭킹 조회 실패 · HTTP 0`) 같이 실패했는지 보면 갈린다.
                        android.util.Log.e("CatchFlower", "지도 오류: ${e.message}")
                        onError()
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(kakaoMap: KakaoMap) {
                        android.util.Log.i("CatchFlower", "지도 준비됨")
                        map = kakaoMap
                    }
                },
            )
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            // 실패해도 화면을 나가는 것을 막지 않는다.
            runCatching { mapView.finish() }
        }
    }

    // ⚠️ **`map`과 `pins` 둘 다 키로 준다.** `pins`만 주면 지도가 준비되기 전에 온
    //    첫 목록이 그려지지 않고, `map`만 주면 새로 등록한 꽃의 핀이 안 붙는다.
    LaunchedEffect(map, pins) {
        val ready = map ?: return@LaunchedEffect
        drawPins(ready, pins, onPinClick, onMapClick)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 핀을 다시 그린다.
 *
 * 🔴 **매번 [com.kakao.vectormap.label.LabelLayer.removeAll]로 비우고 다시 넣는다.**
 *    증분으로 넣으면 같은 기록이 두 번 그려져 **핀이 정확히 겹치고 위의 것만 눌린다** —
 *    아래 핀은 지도에서 영구히 못 여는 상태가 된다. 핀은 많아야 수백 개라 전부
 *    다시 넣어도 체감 차이가 없다.
 */
private fun drawPins(
    map: KakaoMap,
    pins: List<MapPin>,
    onPinClick: (MapPin) -> Unit,
    onMapClick: () -> Unit,
) {
    val labelManager = map.labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()

    if (pins.isEmpty()) return

    val style = labelManager.addLabelStyles(
        LabelStyles.from("cf_pin", LabelStyle.from(pinBitmap())),
    )

    for (pin in pins) {
        layer.addLabel(
            LabelOptions.from(pin.id, LatLng.from(pin.lat, pin.lng))
                .setStyles(style)
                // ⚠️ **`tag`에 핀을 담는다.** 클릭 콜백은 `Label`만 주고 좌표는
                //    반올림돼 돌아오므로, 좌표로 되찾으면 못 찾는 핀이 생긴다.
                .setClickable(true)
                .apply { tag = pin },
        )
    }

    map.setOnLabelClickListener { _, _, label ->
        (label.tag as? MapPin)?.let(onPinClick)
        true // 이벤트를 먹는다 — 안 먹으면 지도 클릭까지 같이 발생해 카드가 바로 닫힌다
    }
    map.setOnMapClickListener { _, _, _, _ -> onMapClick() }

    // 처음 열 때 최근 핀으로 옮긴다.
    //
    // 🔴 **핀이 없으면 움직이지 않는다**([MapPins.center]가 null을 준다).
    //    기본 좌표(서울시청 등)로 채우면 부산 사용자가 "기록이 사라졌다"고 읽는다.
    MapPins.center(pins)?.let { (lat, lng) ->
        map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lng), DEFAULT_ZOOM))
    }
}

/** 동 단위가 한 화면에 들어오는 배율. 15는 약 500m 폭이다. */
private const val DEFAULT_ZOOM = 15

/**
 * 핀 아이콘.
 *
 * ⚠️ **드로어블 리소스를 만들지 않고 비트맵을 그린다.** `res/`에 아이콘이 하나도
 *    없는 프로젝트라(테마·문자열뿐) 벡터 드로어블을 넣으면 그것만 홀로 남고,
 *    디자이너 SVG가 들어올 때 두 곳을 고쳐야 한다. 핀은 **원 하나**면 충분하다.
 */
private fun pinBitmap(): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = size / 2f
    // 흰 테두리를 먼저 깔아야 초록 지도 위에서도 핀이 보인다.
    canvas.drawCircle(r, r, r - 2f, Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true })
    canvas.drawCircle(r, r, r - 6f, Paint().apply { color = 0xFF2E6B4F.toInt(); isAntiAlias = true })
    return bitmap
}

/**
 * 핀을 눌렀을 때의 프리뷰 카드 (A 문서 14번 `프리뷰 카드`).
 *
 * ⚠️ **`1.2km`(거리)를 쓰지 않는다.** 내 현재 위치를 계속 받지 않으므로 계산할 수
 *    없고, 넣으면 **가만히 있는데도 틀린 거리**가 보인다. A 문서의 그 칸은
 *    현재 위치 추적이 붙을 때 채운다.
 */
@Composable
private fun PinPreviewCard(
    pin: MapPin,
    flowerSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Background)
            .padding(CfDimen.GapMedium),
    ) {
        // 장소명이 없으면 **줄을 뺀다.** 좌표를 쓰면 `37.5601, 126.9251`이 제목이 된다.
        Text(
            pin.placeName ?: "내 발견",
            style = CfText.Section,
            color = CfColor.TextPrimary,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            "꽃 ${pin.speciesCount}종 · 기록 ${pin.recordCount}개",
            style = CfText.Body,
            color = CfColor.TextSecondary,
        )
        if (flowerSummary.isNotEmpty()) {
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(flowerSummary, style = CfText.Body, color = CfColor.TextTertiary)
        }
    }
}

/** A 문서 3절 빈 상태 `지도에 기록 없음`. **문구를 새로 쓰지 않는다.** */
@Composable
private fun MapEmptyOverlay(onCapture: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(CfDimen.ScreenPadding)
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Background)
            .padding(CfDimen.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "아직 이 근처에 공유된 꽃이 없어요",
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            "첫 번째로 남겨보세요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapLarge))
        CfPrimaryButton(text = "꽃 찍어보기", onClick = onCapture)
    }
}

/**
 * 지도 인증·연결 실패 (A 문서 3절 `화면 14에서 지도를 못 띄울 때`).
 *
 * ⚠️ **빈 지도를 보여주지 않는다.** 회색 화면은 "이 동네에 기록이 없다"로 읽히고,
 *    사용자는 꽃을 찍어도 안 나온다고 생각한다.
 *
 * 🔴 **`다시 시도`가 반드시 있어야 한다.** 문구가 "잠시 후 다시 시도해 주세요"인데
 *    다시 할 방법이 없으면 **말과 화면이 어긋난다.** 실제 원인이 3초짜리 연결 끊김일
 *    수 있고(2026-08-09 실측), 그때 앱을 죽여야 지도가 돌아오는 것은 사용자에게
 *    **"지도가 영구히 고장난 앱"**이다.
 *
 * ⚠️ **문구를 새로 쓰지 않았다.** 화면 17 `조회 실패`와 **같은 문장**이다
 *    (3절 토스트 `네트워크 오류`). 같은 상황을 두 화면이 다른 말로 말할 이유가 없다.
 */
@Composable
private fun MapUnavailable(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            // 도감은 그대로 쓸 수 있다는 것을 알려야 한다 — 지도가 안 되는 것이
            // 앱이 고장난 것으로 읽히지 않게 (A 문서: 위치 권한 문구와 같은 구조).
            "도감과 촬영은 그대로 쓸 수 있어요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapLarge))
        // Secondary — A 문서가 화면 17 `조회 실패`에 지정한 것과 같은 위계다.
        CfSecondaryButton(text = "다시 시도", onClick = onRetry)
    }
}
