package com.catchflower.app.ui.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import com.catchflower.app.ui.component.CfAttributeChip
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfStat
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.component.CfVisibilityBadge
import com.catchflower.app.ui.component.FlowerIllust
import com.catchflower.app.ui.component.FlowerSilhouette
import com.catchflower.app.ui.component.PhotoPlaceholder
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import java.util.Calendar

/**
 * 화면 05 도감 상세.
 *
 * 와이어프레임 주석 ①이 못 박은 것: **대표 이미지는 사용자 사진이 아니라 공식 일러스트다.**
 * 사진을 대표로 쓰면 200칸의 톤이 제각각이 되고, 미발견 종은 대표 이미지가 아예 없어진다.
 *
 * 미발견 종도 이 화면에 들어올 수 있다 (그리드에서 미발견 셀을 눌렀을 때).
 * 그때는 지표·발견 기록이 없고 실루엣으로 보여준다.
 */
@Composable
fun DexDetailScreen(
    vm: DexViewModel,
    flowerId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flower = vm.flower(flowerId)
    if (flower == null) {
        // 있을 수 없는 경로지만 조용히 빈 화면을 띄우지 않는다.
        Column(modifier = modifier.fillMaxSize()) {
            CfHeader(title = "도감", onBack = onBack)
            Text(
                text = "꽃 정보를 찾을 수 없어요",
                style = CfText.Body,
                color = CfColor.TextSecondary,
                modifier = Modifier.padding(CfDimen.ScreenPadding),
            )
        }
        return
    }

    val collected = flower.id in vm.collectedIds
    // 예선 범위 밖 `공유`(외부 공유 시트). 빈 람다였다((38)).
    val toast = rememberToaster()
    val notReady: () -> Unit = { toast(CfToast.NOT_READY) }
    val discoveries = vm.discoveriesFor(flower.id)
    val similar = vm.similarTo(flower)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    ) {
        item {
            CfHeader(
                title = flower.name,
                onBack = onBack,
                trailing = {
                    // 미발견 종은 공유할 기록이 없다.
                    if (collected) CfTextButton(text = "공유", onClick = notReady)
                    else Spacer(Modifier.size(CfDimen.GapLarge))
                },
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CfDimen.GapLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
            ) {
                Box(
                    modifier = Modifier
                        .size(CfDimen.DetailIllust + 24.dp)
                        .clip(CircleShape)
                        .background(if (collected) CfColor.Surface else CfColor.Background)
                        .border(CfDimen.BorderThin, CfColor.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (collected) FlowerIllust(flower = flower, size = CfDimen.DetailIllust)
                    else FlowerSilhouette(flower = flower, size = CfDimen.DetailIllust)
                }
                Text(text = flower.name, style = CfText.Hero, color = CfColor.TextPrimary)
                // A 문서: `Rosa hybrida · 장미과`
                Text(
                    text = "${flower.scientificName} · ${flower.family}",
                    style = CfText.Caption,
                    color = CfColor.TextSecondary,
                )
                Spacer(Modifier.height(CfDimen.GapTiny))
                // 속성 칩 3종 — 계절 / 희귀도 / 대표 색상 (와이어프레임 주석 ②)
                Row(horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall)) {
                    CfAttributeChip(label = flower.season.label)
                    CfAttributeChip(label = flower.rarity.label)
                    CfAttributeChip(label = flower.color)
                }
            }
        }

        // 누적 지표 3칸. 미발견이면 숨긴다 — `0회 / - / 0곳`을 보여주는 건 정보가 아니다.
        if (collected) {
            item {
                MetricsCard(
                    discoveries = discoveries,
                    modifier = Modifier.padding(horizontal = CfDimen.ScreenPadding),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CfDimen.ScreenPadding,
                        end = CfDimen.ScreenPadding,
                        top = CfDimen.GapLarge,
                    ),
                verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
            ) {
                Text(text = "꽃 이야기", style = CfText.Section, color = CfColor.TextPrimary)
                Text(
                    text = flowerStory(flower),
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )
                if (similar.isNotEmpty()) {
                    // A 문서: `비슷한 꽃 · 해당화, 찔레꽃`
                    Text(
                        text = "비슷한 꽃 · ${similar.joinToString(", ") { it.name }}",
                        style = CfText.Caption,
                        color = CfColor.TextSecondary,
                    )
                }
            }
        }

        if (collected) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = CfDimen.ScreenPadding,
                            end = CfDimen.ScreenPadding,
                            top = CfDimen.GapLarge,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "내 발견 기록",
                        style = CfText.Section,
                        color = CfColor.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${discoveries.size}회",
                        style = CfText.Caption,
                        color = CfColor.TextTertiary,
                    )
                }
            }
            items(discoveries, key = { it.id }) { discovery ->
                DiscoveryRow(
                    discovery = discovery,
                    modifier = Modifier.padding(
                        horizontal = CfDimen.ScreenPadding,
                        vertical = CfDimen.GapSmall,
                    ),
                )
            }
        } else {
            item {
                Text(
                    text = "아직 모은 꽃이 없어요",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CfDimen.GapSection),
                )
            }
        }
    }
}

/** 지표 3칸 — `발견 횟수 4회 · 첫 발견 5월 2일 · 장소 3곳`. */
@Composable
private fun MetricsCard(discoveries: List<Discovery>, modifier: Modifier = Modifier) {
    val first = discoveries.minByOrNull { it.createdAt }
    // 같은 장소를 여러 번 찍으면 1곳이다 (와이어프레임 주석 ③ "같은 꽃을 다시 찍어도 하나").
    val placeCount = discoveries.mapNotNull { it.placeName }.distinct().size

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(vertical = CfDimen.Gap),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CfStat(label = "발견 횟수", value = "${discoveries.size}회")
        CfStat(
            label = "첫 발견",
            value = first?.let { monthDay(it.createdAt) } ?: "-",
        )
        CfStat(label = "장소", value = "${placeCount}곳")
    }
}

@Composable
private fun DiscoveryRow(discovery: Discovery, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .border(CfDimen.BorderThin, CfColor.Border, RoundedCornerShape(CfDimen.RadiusCard))
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        // 실제 사진이 붙기 전까지 자리만 잡는다.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(CfDimen.GapSmall)),
            contentAlignment = Alignment.Center,
        ) {
            PhotoPlaceholder(size = 52.dp)
            Text(text = "사진", style = CfText.Tiny, color = CfColor.TextTertiary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = fullDate(discovery.createdAt),
                style = CfText.BodyBold,
                color = CfColor.TextPrimary,
            )
            Text(
                text = discovery.placeName ?: "장소 없음",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
        }
        // A 문서 화면 05: 기록 배지 `공개 / 비공개`
        val isPublic = discovery.visibility == Visibility.PUBLIC
        CfVisibilityBadge(
            label = if (isPublic) "공개" else "비공개",
            isPublic = isPublic,
        )
    }
}

/**
 * 꽃 이야기 본문.
 *
 * ⚠️ 와이어프레임 주석 ④는 "2~3문장 고정 분량"이라고 정했지만
 *    **200종의 실제 이야기 텍스트는 아직 없다** (마스터 데이터에 컬럼이 없다).
 *    지금은 가진 데이터(개화기·서식지·희귀도)로 조립한다.
 *    없는 문장을 지어내는 것보다 이게 낫다 — 나중에 컬럼이 추가되면 그걸 쓴다.
 *    문장 형식은 A 문서 어미 규칙(`~어요/~아요`)을 따른다.
 */
private fun flowerStory(flower: Flower): String {
    val bloom = "${flower.bloomLabel}에 피어요."
    val where = "${flower.habitat}에서 흔히 만납니다."
    return "$bloom $where"
}

private fun monthDay(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "${cal.get(Calendar.MONTH) + 1}월 ${cal.get(Calendar.DAY_OF_MONTH)}일"
}

/** 와이어프레임 05의 `2026. 6. 14. 토` 형식. */
private fun fullDate(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val weekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "일"
        Calendar.MONDAY -> "월"
        Calendar.TUESDAY -> "화"
        Calendar.WEDNESDAY -> "수"
        Calendar.THURSDAY -> "목"
        Calendar.FRIDAY -> "금"
        else -> "토"
    }
    return "${cal.get(Calendar.YEAR)}. ${cal.get(Calendar.MONTH) + 1}. " +
        "${cal.get(Calendar.DAY_OF_MONTH)}. $weekday"
}
