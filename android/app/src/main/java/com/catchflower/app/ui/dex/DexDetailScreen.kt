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
import com.catchflower.app.ui.component.DiscoveryPhoto
import com.catchflower.app.ui.component.FlowerIllust
import com.catchflower.app.ui.component.FlowerSilhouette
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
                // 속성 칩 — 계절 / 희귀도 / 대표 색상 (와이어프레임 주석 ②).
                // ⚠️ **3개 고정이 아니다.** 신규 1,857종은 대표색이 없고 278종은 계절도
                //    없다 — 고정으로 그리면 **테두리만 있는 빈 칩**이 나온다(계약 1-1-c).
                //    희귀도는 전 종에 있으므로 줄이 비지는 않는다.
                Row(horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall)) {
                    flower.attributeChips.forEach { CfAttributeChip(label = it) }
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

        // 🔴 **낼 문장이 하나도 없으면 섹션을 그리지 않는다.** 신규 1,857종은 서식지가
        //    없고 그중 개화기까지 모르는 종은 `꽃 이야기` 아래가 **완전히 빈다** —
        //    제목만 남은 섹션은 정보가 아니라 고장으로 보인다. 문구를 만들어 채우는 것은
        //    금지되어 있으므로(A 문서) **자리를 없앤다** (계약 1-2-c와 같은 원칙).
        val story = flower.storyText
        if (story != null || similar.isNotEmpty()) item {
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
                if (story != null) {
                    Text(
                        text = story,
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                    )
                }
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
                    flower = flower,
                    // 🔴 사진은 **기록마다 다르다.** `vm.photoFile(discovery)`를 여기서
                    //    부른다 — 꽃 단위로 한 번 구해서 돌려쓰면 같은 종의 모든 기록이
                    //    **첫 사진 하나**를 보여주는데, 화면으로는 완벽하게 정상이다.
                    photo = vm.photoFile(discovery),
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

/**
 * 발견 기록 한 줄.
 *
 * 🔴 **여기가 회색 `사진` 박스였다**((39)에서 고쳤다). 사진은 (28)부터 실제로
 *    기기에 저장되고 있었는데 **아무도 읽지 않았다** — `DexViewModel.photoFile`의
 *    호출자가 0개였다. 도감을 채운 사용자에게 **자기가 찍은 사진이 한 장도 안 보였다.**
 *    "사진이 없다"와 "사진을 안 보여준다"는 화면에서 똑같이 보이고,
 *    코드에는 `실제 사진이 붙기 전까지 자리만 잡는다`는 주석이 남아 있어서
 *    **이미 붙었다는 사실을 가렸다.**
 */
@Composable
private fun DiscoveryRow(
    discovery: Discovery,
    flower: Flower,
    photo: java.io.File?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .border(CfDimen.BorderThin, CfColor.Border, RoundedCornerShape(CfDimen.RadiusCard))
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(CfDimen.GapSmall)),
            contentAlignment = Alignment.Center,
        ) {
            DiscoveryPhoto(
                photo = photo,
                flower = flower,
                size = 52.dp,
                // 날짜·장소는 옆 줄이 읽어 준다. 사진 자체가 새 정보는 아니라서
                // 낭독을 늘리지 않는다(일러스트 `contentDescription = null`과 같은 판단).
                contentDescription = null,
            )
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

// 꽃 이야기 본문 조립은 `Flower.storyText`로 옮겼다.
//
// ⚠️ 와이어프레임 주석 ④는 "2~3문장 고정 분량"이라고 정했지만 **실제 이야기 텍스트는
//    아직 없다**(마스터 데이터에 컬럼이 없다). 가진 데이터(개화기·서식지)로 조립한다.
//
// 🔴 여기 있던 것을 모델로 올린 이유: **2,057종에서는 절이 빠지는 종이 생겼고**
//    같은 조립을 화면 09·22도 한다. 화면마다 따로 분기하면 한 군데를 빠뜨려도
//    아무 검사도 빨개지지 않는다 — 그래서 판정을 한 곳에 둔다.

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
