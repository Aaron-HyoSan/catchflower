package com.catchflower.app.ui.component

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 하단 내비 5탭 (A 문서: `도감 · 지도 · **꽃 촬영** · 랭킹 · 마이`).
 *
 * 중앙 `꽃 촬영`은 지름 68의 원형 셔터로 내비 위로 솟는다 (B 문서 B-1-3).
 * 라벨은 항상 보인다 — 아이콘 단독 금지 규칙(A 문서 1절 44번) 때문이고,
 * 게다가 컬러 아이콘이라 **선택 표시의 절반을 라벨 색이 나른다**([CfIcon]).
 *
 * 아이콘 5종은 납품 아트다([NavTab.iconRes]) — 예전에는 카메라만 직접 그렸는데
 * (`material-icons-extended`가 APK를 크게 늘려서), 이제 납품 세트가 다 채운다.
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
                // 납품 `꽃 촬영` 아이콘(분홍 카메라 + 흰 꽃). 초록 원 위에서 대비가 크다.
                //
                // ⚠️ **`찍기` 아이콘이 아니다.** `찍기`는 카메라 셔터 링(흰 테두리 + 분홍 원)
                //    그림이라 촬영 화면(08)의 셔터 자리 물건이다. 실제로 둘을 초록 원에
                //    올려 비교했고, 하단 내비에서는 "카메라"가 무엇을 하는 버튼인지
                //    더 분명했다(내비에는 셔터라는 문맥이 없다).
                CfIcon(id = NavTab.CAPTURE.iconRes, size = 30.dp)
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
        // 납품 아이콘 ([NavTab.iconRes]). **`tint`를 주지 않는다** — 컬러 아이콘이라
        // 덮어 칠하면 초록 실루엣이 된다([CfIcon]).
        //
        // 🔴 그래서 **선택 상태를 색으로 말할 수 없다.** 아이콘은 회색조로 구분하고,
        //    색(초록/회색)은 **라벨 텍스트가** 계속 나른다 — 라벨이 없으면
        //    선택 표시가 회색조 하나에만 걸린다(색약 사용자에게 약하다).
        CfIcon(
            id = tab.iconRes,
            size = 24.dp,
            contentDescription = null, // 라벨이 이름을 말한다. 중복 낭독을 막는다
            desaturate = !selected,
        )
        Text(text = tab.label, style = CfText.NavLabel, color = tint)
    }
}

// 🔴 **직접 그린 `CameraGlyph`를 지웠다**(2026-08-09). 납품 `꽃 촬영` 아이콘이
//    셔터와 화면 03 카드 두 곳을 다 채워서 **호출처가 0이 됐다.** 남겨 두면
//    "카메라를 그리는 방법이 두 가지"가 되고, 다음 사람이 어느 쪽을 써야 하는지
//    알 수 없다 — 이 저장소가 반복해서 지적한 결함(쓰는 사람이 0명인 코드)이다.
//    필요해지면 git 이력에 있다(`ceda1b5` 이전).

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
