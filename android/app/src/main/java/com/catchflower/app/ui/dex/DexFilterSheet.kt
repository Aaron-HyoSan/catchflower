package com.catchflower.app.ui.dex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.CollectState
import com.catchflower.app.data.DexFilter
import com.catchflower.app.ui.component.CfChip
import com.catchflower.app.ui.component.CfChipGroup
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 06 도감 필터 (바텀시트).
 *
 * 와이어프레임 주석 ①: **전체 화면 전환이 아니라 시트다** — 배경에 도감이 보여야
 * 맥락이 끊기지 않는다. 그래서 ModalBottomSheet를 쓴다.
 *
 * 주석 ④: **결과 개수를 실시간으로 갱신한다.** 시트를 닫아야 몇 종인지 알게 되면
 * 조건을 계속 다시 열게 된다. 그래서 필터 상태를 ViewModel에 즉시 반영하고
 * (닫기 전 임시 상태를 따로 두지 않는다) CTA 라벨이 같은 상태에서 개수를 읽는다.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DexFilterSheet(
    vm: DexViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filter = vm.filter
    val matchCount = vm.visibleFlowers.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CfColor.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CfDimen.ScreenPadding)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "필터",
                    style = CfText.ScreenTitle,
                    color = CfColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                CfTextButton(text = "초기화", onClick = vm::resetFilter)
            }

            Spacer(Modifier.height(CfDimen.GapMedium))

            // 그룹 1 — 수집 여부 (단일 선택)
            FilterGroup(title = "수집 여부") {
                CollectState.entries.forEach { state ->
                    CfChip(
                        label = state.label,
                        selected = filter.collectState == state,
                        onClick = { vm.setCollectState(state) },
                    )
                }
            }

            // 그룹 2 — 계절 (복수 선택)
            FilterGroup(title = "계절") {
                Season.entries.forEach { season ->
                    CfChip(
                        label = season.label,
                        selected = season in filter.seasons,
                        onClick = { vm.toggleSeason(season) },
                    )
                }
            }

            // 그룹 3 — 색상 (복수 선택). 칩 목록은 A 문서가 확정한 6개다.
            FilterGroup(title = "색상") {
                DexFilter.COLOR_CHIPS.forEach { color ->
                    CfChip(
                        label = color,
                        selected = color in filter.colors,
                        onClick = { vm.toggleColor(color) },
                    )
                }
            }

            // 그룹 4 — 보기 쉬움 (복수 선택)
            // A 문서 라벨이 `보기 쉬움`이고 값이 `흔함/보통/귀함`이다.
            // 데이터 컬럼은 rarity지만 사용자에게는 희귀도라는 말을 쓰지 않는다.
            FilterGroup(title = "보기 쉬움") {
                Rarity.entries.forEach { rarity ->
                    CfChip(
                        label = rarity.label,
                        selected = rarity in filter.rarities,
                        onClick = { vm.toggleRarity(rarity) },
                    )
                }
            }

            Spacer(Modifier.height(CfDimen.GapLarge))

            // CTA — 0종이면 Disabled + 문구 교체 (A 문서 화면 06)
            CfPrimaryButton(
                text = if (matchCount == 0) "조건에 맞는 꽃이 없어요" else "${matchCount}종 보기",
                onClick = onDismiss,
                enabled = matchCount > 0,
            )
            Spacer(Modifier.height(CfDimen.Gap))
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(bottom = CfDimen.GapSmall),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = title, style = CfText.Section, color = CfColor.TextPrimary)
        CfChipGroup(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
