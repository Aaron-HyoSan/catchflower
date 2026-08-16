package com.catchflower.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catchflower.app.data.model.Flower
import com.catchflower.app.ui.theme.CfColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 꽃 일러스트 — **실제 납품 아트를 그린다** (2026-08-08).
 *
 * 원본은 `꽃도감/꽃도감_일러스트_전수_webp/{번호}.webp` **2,057장**(전 종)이고, 빌드가
 * `assets/flower_illust/`로 복사한다. 읽는 것은 [FlowerIllustLoader].
 *
 * ⚠️ **발주서는 SVG였지만 앱이 쓰는 것은 512×512 래스터다.** 안드로이드는 SVG를 직접
 *    렌더하지 못한다(빌드 시점에 `VectorDrawable`로 변환해야 한다).
 *    다만 벡터가 아니라서 **원본보다 크게 그리면 뭉갠다** — 지금 최대 사용처는 132dp이고
 *    3x 기기에서 396px이라 512px 안이다. 더 크게 쓰는 화면이 생기면 원본을 다시 받아야 한다.
 *
 * 🔴 **2026-08-12: PNG 200장 → WebP 2,057장.** 이유는 용량이다 — 전수를 PNG로 넣으면
 *    130MB로 Play 업로드 상한(AAB 150MB · APK 직접 100MB)을 넘고, 그때 **빌드는 성공한다.**
 *    실측: WebP로 바꾼 뒤 디버그 APK 142.3MB · **AAB 94.8MB**(제출은 이쪽).
 *    실측표는 `꽃도감/_tools/pack_illust_webp.py` 주석에 있다.
 *
 * ⚠️ **화풍이 섞이지 않게 전량을 교체했다.** 옛 200장은 납작한 채색 픽토그램이고 새
 *    2,057장은 선 위주 스케치다 — 200종만 옛 그림으로 두면 도감 격자에 **두 화풍이
 *    섞여 보인다.** 옛 그림은 `꽃도감/꽃도감_일러스트/`에 그대로 남겨 두었다
 *    (오너가 픽토그램 쪽을 고르면 되돌릴 수 있다 · 오너 미결 항목).
 *
 * ⚠️ **파일을 못 찾으면 플레이스홀더로 되돌린다.** 예외를 던지지 않는다 — 일러스트 한 장이
 *    없다고 도감을 못 열게 만들 이유가 없다. 대신 **빈 칸이 조용히 생기는 것**이
 *    이 방식의 유일한 위험이라, 전량이 다 있는지는 테스트가 센다
 *    ([com.catchflower.app.ui.component.FlowerIllustAssetTest]).
 */
@Composable
fun FlowerIllust(
    flower: Flower,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reqPx = with(density) { size.roundToPx() }

    // 디코딩은 파일 I/O다. `remember`로 묶지 않으면 리컴포지션마다 다시 읽는다
    // (그리드 스크롤에서 바로 티가 난다). 키에 크기를 넣는 이유는 같은 꽃을
    // 셀과 상세에서 다른 크기로 쓰기 때문이다.
    val bitmap = remember(flower.id, reqPx) {
        FlowerIllustLoader.load(context, flower, reqPx)
    }

    if (bitmap == null) {
        // 납품 누락 · 복사 실패. 옛 플레이스홀더로 되돌린다.
        FlowerIllustPlaceholder(flower = flower, size = size, modifier = modifier)
        return
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        // 이름은 셀 아래 텍스트가 말한다. 여기서 또 읽으면 중복 낭독이 된다.
        contentDescription = null,
        // ⚠️ **`Crop`이 아니라 `Fit`이다.** 일러스트는 아트보드를 거의 꽉 채우고 있다.
        //    옛 200장: 여백 중앙값 10px · 194장이 32px 미만.
        //    새 전수 2,057장(121장 표본 재실측 2026-08-12): 중앙값 **8px** ·
        //    최소 7 · 최대 10 · 테두리에 닿는 장 **0장** · 전량이 32px 미만.
        //    → 전수로 바뀌어도 이 판단은 그대로다. `Crop`으로 두면 꽃잎 끝이 잘린다.
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/**
 * 아트가 없을 때만 쓰는 옛 플레이스홀더 — 꽃 색·꽃잎 수로 그린다.
 *
 * ⚠️ **지우지 않는다.** 일러스트 배치 3(65종)은 출시 후 납품이고
 *    (`C_꽃일러스트_발주서.md`), 그때 빈 칸이 생기면 이 그림이 대신 나와야 한다.
 *    실제 아트와 구분되게 두는 것이 목적이라 "예쁘게" 만들 필요는 없다.
 */
@Composable
fun FlowerIllustPlaceholder(
    flower: Flower,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val petalColor = flowerColor(flower.color)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f

            // 꽃잎 수를 id로 흔든다 (5~8장). 실제 종의 꽃잎 수가 아니다 — 구분용이다.
            val petals = 5 + (flower.id % 4)
            val petalRadius = radius * 0.34f
            val orbit = radius * 0.50f
            val spin = (flower.id * 37f) % 360f

            rotate(degrees = spin, pivot = center) {
                repeat(petals) { index ->
                    val angle = (2.0 * Math.PI * index / petals).toFloat()
                    val cx = center.x + orbit * kotlin.math.cos(angle)
                    val cy = center.y + orbit * kotlin.math.sin(angle)
                    drawCircle(
                        color = petalColor.copy(alpha = 0.85f),
                        radius = petalRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
            // 꽃심
            drawCircle(
                color = centerColor(flower.color),
                radius = radius * 0.24f,
                center = center,
            )
        }
    }
}

/**
 * 미발견 셀 (화면 04). **회색 단색 실루엣** — 색도 모양도 보여주지 않는다.
 *
 * ## 🔵 오너가 (b)로 정했다 (2026-08-16)
 *
 * B 문서 76행이 미발견 처리를 **2안 시안(a: 일러스트 회색 반투명 / b: 단색 실루엣)** 으로
 * 물었고, 오너가 **`미발견 셀 회색 처리해`** 로 답했다(= 권고였던 b).
 * (a)를 고르면 **꽃 모양이 그대로 드러난다** — 이름만 가리고 그림을 보여주면
 * "무슨 꽃인지 모른다"는 정보가 새고, 2,044칸 대부분이 미발견이라 도감 첫인상이
 * "이미 다 아는 꽃 목록"이 된다. 그래서 실제 일러스트를 쓰지 않고 추상 도형을 그린다.
 *
 * ## 🔴 결정을 받고 **채우기로 바꿨다** — 그전에는 1.5px 테두리였다
 *
 * `Stroke(width = 1.5f)`였다. 그 값은 **dp가 아니라 픽셀**이라(Canvas 안이다)
 * 3x 기기에서 **0.5dp**로 그려진다 — `#D9D9D9`를 흰 배경에 0.5dp로 그리면
 * 사실상 안 보이고, 셀은 **빈 칸**으로 읽힌다. "회색 처리"의 반대였다.
 * ⚠️ 그래서 여기는 **면으로 채운다.** 채우면 도형이 겹쳐도 이음선이 안 생긴다
 *    (같은 불투명 색이라). 반투명으로 바꾸면 겹친 자리가 진해져 **꽃잎 수가 세진다.**
 *
 * ⚠️ 대비를 더 올리지 않는다. 발견한 셀(실제 일러스트)보다 눈에 띄면
 *    **모은 것이 덜 보인다** — 이 화면의 목적이 뒤집힌다.
 */
@Composable
fun FlowerSilhouette(
    flower: Flower,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f
            val petals = 5 + (flower.id % 4)
            val orbit = radius * 0.50f
            val spin = (flower.id * 37f) % 360f
            rotate(degrees = spin, pivot = center) {
                repeat(petals) { index ->
                    val angle = (2.0 * Math.PI * index / petals).toFloat()
                    drawCircle(
                        color = SilhouetteGray,
                        radius = radius * 0.34f,
                        center = Offset(
                            center.x + orbit * kotlin.math.cos(angle),
                            center.y + orbit * kotlin.math.sin(angle),
                        ),
                    )
                }
            }
            // 꽃심. 꽃잎보다 조금 진하게 — **단색 한 덩어리**로 보이지 않게 하는 최소한이다.
            drawCircle(
                color = SilhouetteGrayCenter,
                radius = radius * 0.24f,
                center = center,
            )
        }
    }
}

/** 미발견 실루엣의 꽃잎 색. 흰 배경 대비 1.3:1 — **읽는 글자가 아니라 도형**이다. */
private val SilhouetteGray = Color(0xFFD9D9D9)

/** 미발견 실루엣의 꽃심 색. 꽃잎보다 한 단 진하다. */
private val SilhouetteGrayCenter = Color(0xFFC4C4C4)

/**
 * **내가 찍은 사진 한 장.** 없으면 도감 일러스트로 되돌린다.
 *
 * 화면 05 `내 발견 기록`과 화면 13 공유 카드가 **같은 이 함수**를 쓴다.
 * 🔴 **따로 만들면 안 된다** — 화면 13에만 `SharePhoto`가 있던 동안 화면 05는
 * 회색 `사진` 박스였다(아래). 두 화면이 같은 데이터를 다르게 그리면
 * **어느 쪽이 맞는지 화면만 봐서는 판단할 수 없다.**
 *
 * ⚠️ **사진이 없을 때 빈 회색 칸을 두지 않는다.** 사진 저장은 실패해도 등록을 막지
 *    않으므로([com.catchflower.app.ui.capture.CaptureViewModel] `record`) 실제로 없을 수
 *    있고, 옛 기록에는 아예 없다(사진 저장은 나중에 붙었다). 회색 칸은 **앱이 고장난
 *    것처럼** 보이는데, 일러스트를 그리면 "무슨 꽃인지"는 그대로 전달된다.
 *
 * ⚠️ **`remember`로 붙든다.** 리컴포지션마다 디코딩하면 목록을 스크롤하는 동안
 *    프레임마다 JPEG를 다시 읽는다. 키는 **파일 경로와 크기**다 —
 *    [Flower]를 키로 쓰면 같은 종의 다른 기록이 **첫 사진을 재사용한다.**
 *
 * 🔴 **디코딩을 메인 스레드에서 하지 않는다((39) 측정 후 고쳤다).** 처음엔
 *    `remember { PhotoLoader.load(...) }`로 **동기 디코딩**을 했고, 기기가
 *    **18~94ms/장**을 보고했다 — 60fps 한 프레임(16.7ms)의 1~6배다.
 *    ⚠️ **이 결함은 "좀 끊기네"로만 보인다** — 크래시도 로그도 없고, 에뮬레이터는
 *    원래 스크롤이 끊기므로 **기기에 시간을 재는 로그를 넣어야** 원인이 드러났다.
 *    (그 로그는 측정용이라 지웠다 — 상시로 두면 스크롤마다 로그가 쏟아진다.)
 *
 * 🔴 **고쳤다는 증거는 프레임 수가 아니다((40)).** 고친 뒤 `Choreographer: Skipped`가
 *    오히려 **71 → 173프레임**으로 커져 보였는데, 그 큰 값들은 **탭하기 전 콜드 스타트**
 *    구간이거나 **앱이 아닌 pid**(919·1055)의 것이었다. 스크롤 구간만 떼서 재도
 *    이 에뮬레이터는 SwiftShader라 **사진을 안 읽는 화면이 더 janky**하게 나온다
 *    ([PhotoLoader.load]의 대조 실측). 실제 증거는 **41장 전부 `main=false`,
 *    되돌려 스크롤해도 디코딩 41회 유지(캐시 적중), FATAL·OOM 0건**이다.
 *
 * ⚠️ **첫 프레임은 [PhotoLoader.cached]로 그린다.** 캐시에 있는 것까지 비동기로
 *    돌리면 스크롤을 되돌릴 때마다 **일러스트가 한 번 번쩍인다.**
 *    캐시에 없을 때만 일러스트를 잠깐 보여주고, 읽히면 사진으로 바뀐다 —
 *    **회색 칸을 두지 않는 이유와 같다**(빈 칸은 고장으로 보인다).
 */
@Composable
fun DiscoveryPhoto(
    photo: java.io.File?,
    flower: Flower,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val density = LocalDensity.current
    val reqPx = with(density) { size.roundToPx() }
    val path = photo?.path
    // 캐시 조회는 메모리 연산이라 동기로 해도 된다(디스크를 안 건드린다).
    var bitmap by remember(path, reqPx) {
        mutableStateOf(photo?.let { PhotoLoader.cached(it, reqPx) })
    }
    LaunchedEffect(path, reqPx) {
        if (bitmap != null || photo == null) return@LaunchedEffect
        // ⚠️ `exists()`도 디스크 접근이라 여기서 한다. 메인에서 부르면
        //    사진 없는 기록 41개가 각각 `stat`을 한 번씩 한다.
        val loaded = withContext(Dispatchers.IO) {
            photo.takeIf { it.exists() }?.let { PhotoLoader.load(it, reqPx) }
        }
        if (loaded != null) bitmap = loaded
    }

    val shown = bitmap
    if (shown == null) {
        FlowerIllust(flower = flower, size = size, modifier = modifier)
        return
    }
    Image(
        bitmap = shown.asImageBitmap(),
        contentDescription = contentDescription,
        // ⚠️ 여기는 **`Crop`이다** — 일러스트(`Fit`)와 반대다. 사진은 정사각으로
        //    저장되므로 잘릴 것이 없고, 만약 옛 기록이 정사각이 아니면 `Fit`은
        //    **칸 안에 빈 띠**를 남긴다. 목록에서 그 띠가 칸마다 다르게 생긴다.
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size),
    )
}

/**
 * 사진 자리. **사진이 있을 수 없는 자리**에만 쓴다.
 *
 * 🔴 **발견 기록에는 쓰지 않는다.** 화면 05가 이걸 쓰고 있었는데, 사진은 (28)부터
 *    실제로 저장되고 있었다 — **파일은 기기에 있는데 회색 박스를 그리고 있었다**
 *    ((39)에서 [DiscoveryPhoto]로 바꿨다). 사진이 없는 것과 **안 보여주는 것**은
 *    화면에서 똑같이 보인다.
 */
@Composable
fun PhotoPlaceholder(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawRect(
            color = Color(0xFFE4E4E4),
            size = Size(this.size.width, this.size.height),
        )
    }
}

/**
 * 꽃 색 한글값 → 실제 색.
 *
 * ⚠️ 데이터에는 **8종**의 색이 있다: 흰색 46 / 분홍 43 / 노랑 37 / 보라 34 /
 *    붉은색 13 / 기타 13 / 파랑 8 / 주황 6.
 *    A 문서 화면 06의 색상 칩은 **6개**(흰색·노랑·분홍·붉은색·보라·파랑)뿐이라
 *    `주황`·`기타` 19종은 색상 필터로 못 찾는다. 칩을 임의로 늘리지 않고
 *    (문구 신설 금지) 오너 확인 항목으로 남긴다.
 */
private fun flowerColor(korean: String): Color = when (korean) {
    "흰색" -> Color(0xFFF2F2EE)
    "노랑" -> Color(0xFFF2C230)
    "분홍" -> Color(0xFFEF9FB6)
    "붉은색" -> Color(0xFFD64545)
    "보라" -> Color(0xFF9A6FBF)
    "파랑" -> Color(0xFF5B8FD6)
    "주황" -> Color(0xFFE8863C)
    else -> Color(0xFFB6C4A8) // 기타 — 연한 잎색. 흰색과 구분되어야 한다
}

private fun centerColor(korean: String): Color = when (korean) {
    "노랑" -> Color(0xFFC98A12)
    "흰색" -> Color(0xFFE8C34A)
    else -> Color(0xFFF5D97A)
}

/** 셀 배경 원의 지름 대비 일러스트 비율. 와이어프레임의 점선 원과 맞춘다. */
val IllustInsetPadding = 6.dp
