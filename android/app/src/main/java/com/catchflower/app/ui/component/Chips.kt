package com.catchflower.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 선택 칩. 화면 04(필터 칩 줄)·06(필터 시트) 공용.
 *
 * 와이어프레임에서 선택 상태는 **채운 어두운 배경 + 흰 글자**다.
 * 테두리 색만 바꾸는 방식은 40~50대 타깃에서 선택 여부가 안 보인다.
 */
@Composable
fun CfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            // 칩 높이는 38이지만 터치 영역은 44를 지킨다 (A 문서).
            .defaultMinSize(minHeight = CfDimen.MinTouch)
            .padding(vertical = (CfDimen.MinTouch - CfDimen.ChipHeight) / 2)
            .height(CfDimen.ChipHeight)
            .clip(RoundedCornerShape(CfDimen.RadiusChip))
            .background(
                when {
                    !enabled -> CfColor.DisabledBackground
                    selected -> CfColor.Primary
                    else -> CfColor.Background
                }
            )
            .border(
                BorderStroke(
                    CfDimen.BorderThin,
                    if (selected) CfColor.Primary else CfColor.Border,
                ),
                RoundedCornerShape(CfDimen.RadiusChip),
            )
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CfText.Chip,
            color = when {
                !enabled -> CfColor.TextDisabled
                selected -> Color.White
                else -> CfColor.TextPrimary
            },
        )
    }
}

/**
 * 읽기 전용 속성 칩 (화면 05 `여름 / 흔함 / 붉은색`).
 * 누를 수 없으므로 터치 영역 규칙 대상이 아니다.
 */
@Composable
fun CfAttributeChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(CfDimen.BorderThin, CfColor.Border), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = CfText.Chip, color = CfColor.TextSecondary)
    }
}

/** 공개/비공개 배지 (화면 05 발견 기록 행). */
@Composable
fun CfVisibilityBadge(label: String, isPublic: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (isPublic) CfColor.PrimaryLight else CfColor.Surface)
            .border(
                BorderStroke(
                    CfDimen.BorderThin,
                    if (isPublic) CfColor.Primary else CfColor.Border,
                ),
                RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CfText.Tiny,
            color = if (isPublic) CfColor.Primary else CfColor.TextSecondary,
        )
    }
}

/** 가로 스크롤 칩 줄 (화면 04). 5개가 375폭에 안 들어가므로 스크롤이 필요하다. */
@Composable
fun <T> CfChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CfDimen.ScreenPadding,
        ),
    ) {
        items(options) { option ->
            CfChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** 줄바꿈 칩 그룹 (화면 06 시트). 색상 6개는 한 줄에 안 들어가 2줄이 된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CfChipGroup(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
        verticalArrangement = Arrangement.spacedBy(0.dp), // 칩 자체가 세로 패딩을 갖는다
        content = content,
    )
}

/** 라벨 + 값 한 줄 (화면 05 지표 3칸에서 씀). */
@Composable
fun CfStat(label: String, value: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
    ) {
        Text(text = label, style = CfText.Tiny, color = CfColor.TextTertiary)
        Text(text = value, style = CfText.BodyBold, color = CfColor.TextPrimary)
    }
}

/** 헤더 (뒤로/제목/우측 액션). 우측 액션은 텍스트 버튼으로만 둔다. */
@Composable
fun CfHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = CfDimen.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            // 아이콘 단독 금지 규칙: 뒤로가기는 `뒤로` 텍스트를 병기한다.
            CfTextButton(
                text = "뒤로",
                onClick = onBack,
                iconRes = R.drawable.ic_back,
                color = CfColor.TextPrimary,
            )
        } else {
            Box(Modifier.padding(start = CfDimen.GapMedium))
        }
        Text(
            text = title,
            style = CfText.ScreenTitle,
            color = CfColor.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = CfDimen.GapSmall),
            textAlign = if (onBack != null) androidx.compose.ui.text.style.TextAlign.Center
            else androidx.compose.ui.text.style.TextAlign.Start,
            maxLines = 1,
        )
        if (trailing != null) trailing() else Box(Modifier.padding(end = CfDimen.GapLarge))
    }
}
