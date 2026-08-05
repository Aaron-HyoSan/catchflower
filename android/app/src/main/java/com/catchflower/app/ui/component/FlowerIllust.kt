package com.catchflower.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catchflower.app.data.model.Flower
import com.catchflower.app.ui.theme.CfColor

/**
 * 꽃 일러스트.
 *
 * ⚠️ **200종 SVG는 아직 없다.** `디자이너_업무/C_꽃일러스트_발주서.md`는 발주서이고,
 *    산출물(`flower_{3자리}_{이름}.svg`)은 아직 납품 전이다.
 *
 * 그래서 지금은 꽃 색·꽃잎 수로 그리는 **플레이스홀더**를 쓴다.
 * 목적은 "그림처럼 보이게"가 아니라 **레이아웃과 색 배치를 실제 크기로 검증**하는 것이다.
 * - 종마다 다르게 보인다 (id로 꽃잎 수·회전을 흔든다) → 그리드에서 200칸이 구분된다
 * - 실제 색을 쓴다 → B-1-1의 "UI 저채도 / 일러스트 다채로움" 충돌 여부를 지금 볼 수 있다
 *
 * SVG가 오면 [FlowerIllust] 내부만 교체한다. 호출부는 [Flower.illustAssetName]을
 * 이미 알고 있으므로 바뀌지 않는다.
 */
@Composable
fun FlowerIllust(
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
 * 미발견 셀 (화면 04). 와이어프레임은 **회색 실루엣**이다 —
 * 색까지 보여주면 "무슨 꽃인지 모른다"는 정보가 새 버린다.
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
                        color = Color(0xFFD9D9D9),
                        radius = radius * 0.34f,
                        center = Offset(
                            center.x + orbit * kotlin.math.cos(angle),
                            center.y + orbit * kotlin.math.sin(angle),
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                    )
                }
            }
            drawCircle(
                color = Color(0xFFD9D9D9),
                radius = radius * 0.24f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
            )
        }
    }
}

/**
 * 사진 자리. 발견 기록 목록(화면 05)에서 실제 사진이 붙기 전까지 쓴다.
 * 와이어프레임의 점선 `사진` 박스와 같은 자리다.
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
