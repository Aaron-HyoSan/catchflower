package com.catchflower.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 하단 내비 5탭 (A 문서: `도감 · 지도 · **꽃 촬영** · 랭킹 · 마이`).
 *
 * 중앙 `꽃 촬영`은 지름 68의 원형 셔터로 내비 위로 솟는다 (B 문서 B-1-3).
 * 라벨은 항상 보인다 — 아이콘 단독 금지 규칙 때문이고, 그래서 아이콘이
 * 완벽히 정확할 필요는 없다 (의미는 텍스트가 나른다).
 *
 * ⚠️ 카메라 아이콘은 직접 그린다. `material-icons-extended`에만 PhotoCamera가 있고
 *    그 의존성은 APK를 크게 늘린다 — 아이콘 1개 때문에 넣을 값이 아니다.
 */
enum class NavTab(val label: String) {
    DEX("도감"),
    MAP("지도"),
    CAPTURE("꽃 촬영"),
    RANKING("랭킹"),
    MY("마이"),
}

@Composable
fun CfBottomNav(
    current: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 상단 구분선 없이는 스크롤된 도감 셀이 내비 뒤로 비쳐 보인다.
                .drawTopBorder()
                .background(CfColor.Background)
                .navigationBarsPadding() // B 문서의 "세이프에어리어 24"를 실기기 값으로 받는다
                .height(CfDimen.BottomNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                if (tab == NavTab.CAPTURE) {
                    // 셔터 자리를 비워둔다. 실제 버튼은 아래에서 겹쳐 그린다.
                    Box(modifier = Modifier.weight(1f))
                } else {
                    NavItem(
                        tab = tab,
                        selected = tab == current,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 중앙 셔터. 내비 위로 솟으므로 Row 밖에서 그린다.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(CfDimen.ShutterDiameter)
                    .clip(CircleShape)
                    .background(CfColor.Primary)
                    .clickable(role = Role.Button) { onSelect(NavTab.CAPTURE) },
                contentAlignment = Alignment.Center,
            ) {
                CameraGlyph(size = 30.dp, color = Color.White, holeColor = CfColor.Primary)
            }
            Text(
                text = NavTab.CAPTURE.label,
                style = CfText.NavLabel,
                color = CfColor.Primary,
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) CfColor.Primary else CfColor.TextTertiary
    Column(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = CfDimen.GapSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val icon: ImageVector? = when (tab) {
            NavTab.DEX -> Icons.AutoMirrored.Outlined.List
            NavTab.MAP -> Icons.Outlined.Place
            NavTab.RANKING -> Icons.Outlined.Star
            NavTab.MY -> Icons.Outlined.Person
            NavTab.CAPTURE -> null // 여기로 오지 않는다
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null, // 라벨이 이름을 말한다. 중복 낭독을 막는다
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(text = tab.label, style = CfText.NavLabel, color = tint)
    }
}

/**
 * 카메라 글리프 — 본체(라운드 사각) + 렌즈(원) + 상단 돌출부.
 *
 * ⚠️ [holeColor]는 **렌즈를 파낸 자리에 칠하는 배경색**이다. 처음에는 이 값을
 *    `CfColor.Primary`로 박아 뒀는데, 그러면 이 글리프는 **초록 셔터 위에서만**
 *    맞다. 화면 03처럼 `PrimaryLight`(연한 초록) 원 안에 놓으면 렌즈 구멍만
 *    진한 초록으로 칠해져서 "렌즈에 초록 점이 박힌" 그림이 된다.
 *    호출처가 자기 배경색을 넘긴다.
 */
@Composable
fun CameraGlyph(
    size: Dp,
    color: Color,
    holeColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f

        // 상단 돌출부 (뷰파인더 혹)
        drawPath(
            path = Path().apply {
                moveTo(w * 0.34f, h * 0.24f)
                lineTo(w * 0.41f, h * 0.13f)
                lineTo(w * 0.59f, h * 0.13f)
                lineTo(w * 0.66f, h * 0.24f)
                close()
            },
            color = color,
        )
        // 본체
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, h * 0.22f),
            size = Size(w, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.14f),
        )
        // 렌즈 — 본체에서 파낸 것처럼 보이게 배경색 원 + 링
        drawCircle(
            color = holeColor,
            radius = w * 0.19f,
            center = Offset(w / 2f, h * 0.53f),
        )
        drawCircle(
            color = color,
            radius = w * 0.19f,
            center = Offset(w / 2f, h * 0.53f),
            style = Stroke(width = stroke),
        )
    }
}

/** 상단 1px 구분선. Divider 컴포저블을 쓰면 높이 계산에 끼어들어 셔터 위치가 밀린다. */
private fun Modifier.drawTopBorder(): Modifier = drawBehind {
    drawLine(
        color = CfColor.Border,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1f,
    )
}

/** 진행 바 (화면 04·22 현황 카드). */
@Composable
fun CfProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CfDimen.ProgressBarHeight)
            .clip(RoundedCornerShape(CfDimen.ProgressBarHeight / 2))
            .background(CfColor.Border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(CfDimen.ProgressBarHeight)
                .clip(RoundedCornerShape(CfDimen.ProgressBarHeight / 2))
                .background(CfColor.Primary),
        )
    }
}
