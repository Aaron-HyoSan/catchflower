package com.catchflower.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 버튼 6종 (A 문서 1절). **여기 밖에서 Button을 직접 쓰지 않는다** —
 * 높이·라운드·굵기가 화면마다 어긋나는 걸 막는 유일한 방법이다.
 *
 * 공통 규칙:
 * - 최소 터치 영역 44×44
 * - **아이콘 단독 버튼은 만들지 않는다.** 아이콘을 받는 오버로드는 반드시 text도 받는다
 */

/** Primary — 화면의 주 행동. **화면당 1개.** 높이 56, 라운드 완전, 채움, 굵게. */
@Composable
fun CfPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(CfDimen.ButtonPrimary),
        shape = RoundedCornerShape(CfDimen.RadiusFull),
        colors = ButtonDefaults.buttonColors(
            containerColor = CfColor.Primary,
            contentColor = Color.White,
            // Disabled — 회색 배경 + 회색 글자. 문구는 호출자가 안내로 교체한다
            // (예: `조건에 맞는 꽃이 없어요`). A 문서 1절.
            disabledContainerColor = CfColor.DisabledBackground,
            disabledContentColor = CfColor.TextDisabled,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text = text, style = CfText.Button, textAlign = TextAlign.Center)
    }
}

/** Secondary — 대안 행동. 테두리 1.5px, 흰 배경. */
@Composable
fun CfSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(CfDimen.ButtonSecondary),
        shape = RoundedCornerShape(CfDimen.RadiusFull),
        border = BorderStroke(CfDimen.BorderButton, CfColor.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CfColor.Background,
            contentColor = CfColor.TextPrimary,
            disabledContentColor = CfColor.TextDisabled,
        ),
    ) {
        Text(text = text, style = CfText.Button, textAlign = TextAlign.Center)
    }
}

/** Ghost — 회피·보조 행동 (`나만 보기`, `나중에 할게요`). 연한 회색 배경. */
@Composable
fun CfGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(CfDimen.ButtonGhost),
        shape = RoundedCornerShape(CfDimen.RadiusFull),
        colors = ButtonDefaults.buttonColors(
            containerColor = CfColor.GhostBackground,
            contentColor = CfColor.TextPrimary,
            disabledContainerColor = CfColor.DisabledBackground,
            disabledContentColor = CfColor.TextDisabled,
        ),
    ) {
        Text(text = text, style = CfText.ButtonGhost)
    }
}

/**
 * Small — 카드 내부 (`길찾기`, `추가`, `초대하기`).
 * 높이 38이라 44에 못 미치므로 [Modifier.sizeIn]으로 터치 영역만 44를 확보한다.
 */
@Composable
fun CfSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(CfDimen.ButtonSmall / 2)
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .sizeIn(minHeight = CfDimen.MinTouch)
                .height(CfDimen.ButtonSmall),
            shape = shape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CfColor.Primary,
                contentColor = Color.White,
            ),
        ) {
            Text(text = text, style = CfText.ButtonSmall)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .sizeIn(minHeight = CfDimen.MinTouch)
                .height(CfDimen.ButtonSmall),
            shape = shape,
            border = BorderStroke(CfDimen.BorderThin, CfColor.Border),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CfColor.TextPrimary),
        ) {
            Text(text = text, style = CfText.ButtonSmall)
        }
    }
}

/**
 * Text — 최소 강조 (`전체 보기`, `필터`, `초기화`). 밑줄 없음.
 *
 * [iconRes]를 줘도 텍스트는 필수다 — 아이콘 단독 버튼 금지 규칙(A 문서 1절 44번)을
 * 시그니처 수준에서 못 박는다.
 *
 * 🔴 **`ImageVector`를 받지 않는다.** 이전 시그니처는 `icon: ImageVector?`였고
 *    [Icon]으로 그려 **`tint` 한 색으로 덮었다.** 납품 아이콘은 컬러(분홍 꽃잎·노란
 *    꽃심)라서 그렇게 그리면 **초록 실루엣**이 된다([CfIcon]에 실측이 적혀 있다).
 *    게다가 그 파라미터는 **호출하는 곳이 한 곳도 없었다** — 죽은 파라미터였다.
 *    납품 PNG를 받으려면 `@DrawableRes Int`여야 한다.
 */
@Composable
fun CfTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    color: Color = CfColor.TextSecondary,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = CfDimen.MinTouch),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
        ) {
            if (iconRes != null) {
                // 텍스트가 옆에 있으므로 contentDescription은 주지 않는다(중복 낭독).
                // 크기는 라벨과 같은 눈높이로 18dp — 24dp는 텍스트보다 커서 라벨이 딸려 보인다.
                CfIcon(id = iconRes, size = 18.dp)
            }
            Text(text = text, style = CfText.ButtonText, color = color)
        }
    }
}
