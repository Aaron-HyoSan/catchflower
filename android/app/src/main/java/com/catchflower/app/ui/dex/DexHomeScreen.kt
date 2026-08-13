package com.catchflower.app.ui.dex

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.core.RelativeTime
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import com.catchflower.app.ui.component.CfChip
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfProgressBar
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.FlowerIllust
import com.catchflower.app.ui.component.FlowerSilhouette
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 04 도감 홈 / 화면 22 빈 상태.
 *
 * **한 화면이다.** 와이어프레임 22는 04의 0종 변형이고, 현황 카드가 그대로 남는다
 * (22 주석 ①: "0종에서도 분모 노출 — 앞으로의 규모를 인지시킨다").
 * 그래서 컴포저블을 나누지 않고 [DexViewModel.collectedCount] 0 분기로 처리한다.
 *
 * 문구는 A 문서 2절 화면 04·22를 그대로 쓴다. 새로 쓰지 않는다.
 */
@Composable
fun DexHomeScreen(
    vm: DexViewModel,
    onFlowerClick: (Int) -> Unit,
    onOpenFilter: () -> Unit,
    onCapture: () -> Unit,
    /**
     * `전체 보기` — 화면 23을 [com.catchflower.app.ui.dex.DiscoveryListMode.ALL]로 연다.
     *
     * ✅ 2026-08-13까지 `준비 중` 토스트였다. 이제 화면이 있다.
     */
    onOpenAllDiscoveries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = vm.collectedCount == 0

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        // 그리드 셀만 3열이고 카드·섹션은 전폭이다. LazyVerticalGrid에 span으로 섞는다 —
        // Column + 내부 그리드로 만들면 200칸이 전부 즉시 measure되어 스크롤이 무거워진다.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        fullWidth { DexHeader() }

        fullWidth {
            StatusCard(
                collected = vm.collectedCount,
                total = vm.allFlowers.size,
                thisSeason = vm.thisSeasonCount,
                percent = vm.completionPercent,
                toNextBadge = vm.toNextBadge,
                progress = vm.progress,
                showProgressText = !isEmpty,
            )
        }

        if (isEmpty) {
            // --- 화면 22 ---
            fullWidth {
                EmptyStateBlock(
                    starters = vm.starterFlowers,
                    onCapture = onCapture,
                    onFlowerClick = onFlowerClick,
                )
            }
        } else {
            // --- 화면 04 ---
            fullWidth {
                SectionRow(
                    title = "최근 발견한 꽃",
                    actionText = "전체 보기",
                    onAction = onOpenAllDiscoveries,
                )
            }
            fullWidth {
                RecentRow(
                    discoveries = vm.recentDiscoveries,
                    flowerOf = vm::flower,
                    onClick = onFlowerClick,
                )
            }
            fullWidth {
                Spacer(Modifier.height(CfDimen.GapSmall))
                SectionRow(
                    title = "전체 ${vm.allFlowers.size}종",
                    actionText = "필터",
                    onAction = onOpenFilter,
                    actionIcon = R.drawable.ic_filter,
                )
            }
            fullWidth(padded = false) {
                QuickFilterChips(
                    active = vm.activeQuickChip(),
                    onSelect = vm::applyQuickChip,
                )
            }

            val visible = vm.visibleFlowers
            if (visible.isEmpty()) {
                fullWidth {
                    // 필터 결과 0종. A 문서 화면 06의 Disabled 문구를 재사용한다.
                    Text(
                        text = "조건에 맞는 꽃이 없어요",
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CfDimen.GapSection),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(visible, key = { it.id }) { flower ->
                    DexCell(
                        flower = flower,
                        collected = flower.id in vm.collectedIds,
                        onClick = { onFlowerClick(flower.id) },
                    )
                }
            }
        }
    }
}

/** 전폭 아이템 헬퍼. 3열 그리드에 섹션을 섞기 위해 span을 전부 채운다. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
        Box(
            modifier = if (padded) Modifier.padding(horizontal = CfDimen.ScreenPadding)
            else Modifier,
        ) {
            Column { content() }
        }
    }
}

/**
 * 헤더. **A 문서 화면 04 표에는 `내 꽃 도감` 한 줄뿐이다** — 버튼이 없다.
 *
 * 🔴 여기 `0종 보기` 개발 토글이 있었다(2026-08-09에 지웠다 · (38)).
 *    "출시 전에 뺀다"고 주석까지 달려 있었는데 **3종을 모은 화면에 그대로 떠 있었다.**
 *    랭킹의 `[개발] 친구 없는 화면`과 같은 결함이고, 같은 이유로 못 봤다 —
 *    **개발용 버튼은 잘 동작하기 때문에** 아무 증상이 없다.
 *    `ButtonLabelSourceTest`가 이제 이 자리를 지킨다 — A 문서 표에 있는 문구만
 *    버튼이 될 수 있으므로, 개발용 토글은 이름을 어떻게 짓든 걸린다.
 */
@Composable
private fun DexHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CfDimen.GapLarge, bottom = CfDimen.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "내 꽃 도감",
            style = CfText.ScreenTitle,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 현황 카드. 화면 04는 `모은 꽃 37 / 200종` + `이번 시즌 12종`,
 * 화면 22는 같은 카드에 `0 / 200종`과 빈 진행 바.
 */
@Composable
private fun StatusCard(
    collected: Int,
    total: Int,
    thisSeason: Int,
    percent: Int,
    toNextBadge: Int,
    progress: Float,
    showProgressText: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.Gap),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "모은 꽃", style = CfText.Caption, color = CfColor.TextSecondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$collected",
                        style = CfText.HeroNumber,
                        color = CfColor.TextPrimary,
                    )
                    Text(
                        text = " / ${total}종",
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (thisSeason > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "이번 시즌",
                        style = CfText.Caption,
                        color = CfColor.TextSecondary,
                    )
                    Text(
                        text = "${thisSeason}종",
                        style = CfText.Section,
                        color = CfColor.Primary,
                    )
                }
            }
        }
        CfProgressBar(progress = progress)
        if (showProgressText) {
            Text(
                // A 문서: `도감 18% 완성 · 다음 배지까지 3종`
                text = "도감 ${percent}% 완성 · 다음 배지까지 ${toNextBadge}종",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    actionText: String,
    onAction: () -> Unit,
    @DrawableRes actionIcon: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CfText.Section,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        CfTextButton(text = actionText, onClick = onAction, iconRes = actionIcon)
    }
}

@Composable
private fun RecentRow(
    discoveries: List<Discovery>,
    flowerOf: (Int) -> Flower?,
    onClick: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        items(discoveries, key = { it.id }) { discovery ->
            val flower = flowerOf(discovery.flowerId) ?: return@items
            Column(
                modifier = Modifier
                    .width(CfDimen.RecentCard)
                    .clickable { onClick(flower.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
            ) {
                Box(
                    modifier = Modifier
                        .size(CfDimen.RecentCard)
                        .clip(RoundedCornerShape(CfDimen.RadiusCard))
                        .background(CfColor.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    FlowerIllust(flower = flower, size = CfDimen.RecentCard - 16.dp)
                }
                Text(
                    text = flower.name,
                    style = CfText.BodyBold,
                    color = CfColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeDay(discovery.createdAt),
                    style = CfText.Tiny,
                    color = CfColor.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun QuickFilterChips(active: String, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CfDimen.ScreenPadding,
        ),
    ) {
        items(com.catchflower.app.data.DexFilter.QUICK_CHIPS) { chip ->
            CfChip(
                label = chip,
                selected = chip == active,
                onClick = { onSelect(chip) },
            )
        }
    }
}

/**
 * 도감 셀. 미발견은 **회색 실루엣 + `미발견`** (와이어프레임 04).
 * 이름을 보여주지 않는 게 핵심이다 — 이름이 보이면 모으는 동기가 사라진다.
 */
@Composable
private fun DexCell(flower: Flower, collected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = CfDimen.GapTiny),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
    ) {
        Box(
            modifier = Modifier
                .size(CfDimen.DexCellIllust)
                .clip(CircleShape)
                .background(if (collected) CfColor.Surface else CfColor.Background)
                .border(
                    width = CfDimen.BorderThin,
                    color = if (collected) CfColor.Border else CfColor.Border,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (collected) {
                FlowerIllust(flower = flower, size = CfDimen.DexCellIllust - 18.dp)
            } else {
                FlowerSilhouette(flower = flower, size = CfDimen.DexCellIllust - 18.dp)
            }
        }
        Text(
            text = if (collected) flower.name else "미발견",
            style = if (collected) CfText.Caption else CfText.Tiny,
            color = if (collected) CfColor.TextPrimary else CfColor.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 화면 22 본문 — 큰 CTA + 제철 추천 4종. */
@Composable
private fun EmptyStateBlock(
    starters: List<Flower>,
    onCapture: () -> Unit,
    onFlowerClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        Spacer(Modifier.height(CfDimen.GapLarge))
        starters.firstOrNull()?.let {
            // 대표 일러스트 자리. 특정 종을 크게 보여주면 "그 꽃을 찍어야 한다"로
            // 읽히므로 실루엣으로 둔다.
            //
            // ⚠️ **실제 아트가 왔어도 여기는 실루엣이다.** 132dp로 크게 그리면 아래 문구
            //    "아직 모은 꽃이 없어요"와 붙어 **그 꽃을 찍으라는 지시로 읽힌다** —
            //    `starters.first()`는 그냥 제철 목록의 첫 항목일 뿐이다.
            //    이름도 안 붙어 있어서 오해를 정정할 방법이 없다.
            //    아래 추천 4종은 반대로 실제 일러스트를 쓴다(이름이 붙어 있다).
            FlowerSilhouette(flower = it, size = CfDimen.DetailIllust)
        }
        Text(
            text = "아직 모은 꽃이 없어요",
            style = CfText.Section,
            color = CfColor.TextPrimary,
        )
        Text(
            text = "산책길에 만난 꽃을 찍어 첫 칸을 채워보세요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        CfPrimaryButton(
            text = "꽃 찍어보기",
            onClick = onCapture,
            modifier = Modifier.padding(horizontal = CfDimen.GapLarge),
        )
        Spacer(Modifier.height(CfDimen.GapSmall))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CfDimen.RadiusCard))
                .border(CfDimen.BorderThin, CfColor.Border, RoundedCornerShape(CfDimen.RadiusCard))
                .padding(CfDimen.Gap),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
        ) {
            Text(text = "처음이라면 이 꽃부터", style = CfText.Section, color = CfColor.TextPrimary)
            Text(
                text = "지금 이 계절, 동네에서 흔히 보이는 꽃이에요",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall)) {
                starters.forEach { flower ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onFlowerClick(flower.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // ⚠️ 여기는 **실루엣이 아니라 실제 일러스트다.** 위 395행과 다르다.
                        //    이 섹션은 이름을 이미 다 적어 놓고 "동네에서 흔히 보이는
                        //    꽃이에요"라며 **찾아보라고 시키는 자리**다. 그림을 가리면
                        //    감추는 정보가 없는데(이름이 있다) **뭘 찾으라는 건지 알 수 없다.**
                        //    미발견 셀(366행)과는 목적이 반대다.
                        FlowerIllust(flower = flower, size = 56.dp)
                        Text(
                            text = flower.name,
                            style = CfText.Caption,
                            color = CfColor.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 개화기를 모르는 종은 **줄을 그리지 않는다** (계약 1-2-c).
                        // 빈 문자열을 넣으면 빈 줄이 생겨 셀 높이만 들쭉날쭉해진다.
                        flower.bloomText?.let {
                            Text(
                                text = it,
                                style = CfText.Tiny,
                                color = CfColor.TextTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 상대 날짜. 와이어프레임 04는 `오늘 / 어제 / 3일 전`이다.
 *
 * 🔴 **계산이 여기 있었다.** 화면 15·16이 같은 표기를 필요로 하면서
 *    [com.catchflower.app.core.RelativeTime]으로 옮겼다 — `private`이라 다른 화면이
 *    못 썼고, 그대로 두면 **같은 규칙이 세 화면에 세 벌** 생긴다. 날짜 경계 계산은
 *    틀려도 화면에 예쁘게 나오는 종류라(`어제`라고 쓰여 있으면 의심하지 않는다)
 *    한 곳에 두고 JVM 테스트로 잰다.
 *
 * ⚠️ 화면 15·16은 [com.catchflower.app.core.RelativeTime.detailed]다(`2시간 전`).
 *    두 모양을 한 함수로 합치지 않은 이유는 그쪽 주석에 있다.
 */
private fun relativeDay(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    RelativeTime.day(timestamp, now)
