package com.catchflower.app.ui.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.RelativeTime
import com.catchflower.app.data.DiscoveryRules
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.DiscoveryPhoto
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 23 — 발견 기록 전체 목록. **화면 04 `전체 보기`와 화면 20 `내가 공유한 꽃`이
 * 여는 같은 화면**이다(A 문서 3절 ⑥).
 *
 * 2026-08-13에 죽은 버튼 두 개를 실제 동작으로 바꾸면서 생겼다.
 * 와이어프레임에 없는 화면이라 **새 문구 3개를 A 문서 3절 ⑥에 먼저 등재**하고 만들었다.
 *
 * ## 🔴 제목이 두 개인 이유
 *
 * 목록은 같은 기기 기록이고 [DiscoveryListMode.SHARED]만 공개 범위로 걸러진다. **제목이 같으면
 * 걸러진 목록을 전체로 읽는다** — `총 발견 112회`를 누른 사용자가 26줄을 보고
 * 기록이 사라졌다고 생각한다.
 *
 * ## 🔴 서버에 묻지 않는다
 *
 * 기기 기록([DexViewModel.discoveryList])이다. 화면 20 지표 3칸과 **같은 출처**여야 한다 —
 * 서버에 물으면 업로드가 밀린 기록이 목록에서만 사라지고, 그건 `공유 26개`와 26줄이
 * 어긋나는 것으로 보인다([DiscoveryRules.isShared] 주석).
 */
@Composable
fun DiscoveryListScreen(
    vm: DexViewModel,
    mode: DiscoveryListMode,
    onBack: () -> Unit,
    /** 줄을 누르면 도감 상세로 간다. 기록 상세(화면 16)는 **남의 기록용**이다. */
    onFlowerClick: (Int) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = vm.discoveryList(sharedOnly = mode == DiscoveryListMode.SHARED)

    Column(modifier.fillMaxSize()) {
        // A 문서 3절 ⑥의 두 제목. 한쪽만 쓰면 걸러진 목록을 전체로 읽는다.
        CfHeader(title = mode.title, onBack = onBack)

        // ⚠️ **읽는 중에는 빈 상태를 그리지 않는다.** 기록이 41건인 사용자가 화면을
        //    열 때마다 `아직 지도에 공유한 꽃이 없어요`를 한 프레임 본다
        //    ([DexViewModel.profileStats]가 `null`을 두는 것과 같은 이유).
        if (vm.loading) {
            Spacer(Modifier.fillMaxSize())
            return@Column
        }

        if (rows.isEmpty()) {
            EmptyList(mode = mode, onCapture = onCapture)
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CfDimen.ScreenPadding,
                end = CfDimen.ScreenPadding,
                top = CfDimen.GapMedium,
                // 하단 내비에 가리지 않게 띄운다(화면 17과 같은 값).
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
        ) {
            // ⚠️ `key`는 **기록 id**다. `flowerId`로 두면 같은 종을 두 번 찍은 사람의
            //    목록에서 Compose가 줄을 재사용해 **사진이 서로 바뀐다.**
            items(rows, key = { it.id }) { discovery ->
                DiscoveryRow(
                    discovery = discovery,
                    vm = vm,
                    // 공유 목록에서는 전부 공개이므로 배지를 붙이지 않는다 —
                    // 모든 줄에 같은 배지가 붙으면 아무 정보도 아니다.
                    showSharedBadge = mode == DiscoveryListMode.ALL,
                    onClick = { onFlowerClick(discovery.flowerId) },
                )
            }
        }
    }
}

/** 이 화면을 여는 두 경로. [title]은 A 문서 3절 ⑥ 표에서 왔다. */
enum class DiscoveryListMode(val title: String) {
    /** 화면 04 `전체 보기`. 기기 기록 전부. */
    ALL("발견 기록"),

    /** 화면 20 `내가 공유한 꽃`. [DiscoveryRules.isShared]만. */
    SHARED("내가 공유한 꽃"),
}

@Composable
private fun EmptyList(mode: DiscoveryListMode, onCapture: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        Spacer(Modifier.size(CfDimen.GapSection))
        Text(
            text = when (mode) {
                // 🔴 **여기에 화면 22의 `아직 모은 꽃이 없어요`를 쓰지 않는다**(A 문서
                //    3절 ⑥). 공유가 0건인 것과 도감이 빈 것은 다른 상태다 — 37종을
                //    모은 사용자에게 "모은 꽃이 없다"고 말하게 된다.
                DiscoveryListMode.SHARED -> "아직 지도에 공유한 꽃이 없어요"

                // ⚠️ [DiscoveryListMode.ALL]에서는 **그 문구가 맞다** — 목록이 비었다는
                //    것이 곧 도감이 비었다는 뜻이다. 그리고 이 갈래는 사실상 닫혀 있다:
                //    `전체 보기`는 `최근 발견한 꽃` 섹션 안에 있고 그 섹션은 도감이 비면
                //    아예 안 그려진다(화면 22가 대신 뜬다). 그래도 빈 화면으로 두지
                //    않는다 — 도달할 수 없다고 적어 둔 자리에 실제로 도달하는 일이
                //    이 저장소에서 있었고, 그때 보이는 것이 **아무것도 없는 화면**이면
                //    앱이 죽은 것으로 읽힌다.
                DiscoveryListMode.ALL -> "아직 모은 꽃이 없어요"
            },
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        // 화면 22와 같은 라벨. 할 수 있는 일을 한 개 준다.
        CfPrimaryButton(text = "꽃 찍어보기", onClick = onCapture)
    }
}

/**
 * 한 줄 = 기록 **한 건**이다(종이 아니다).
 *
 * ⚠️ [DexViewModel.photoFile]을 줄마다 부른다. 종 단위로 한 번 구해 돌려쓰면 같은
 *    종의 모든 줄이 첫 사진을 보여주는데, **화면으로는 완벽하게 정상으로 보인다**
 *    (그 함수 주석에 적힌 함정이다).
 */
@Composable
private fun DiscoveryRow(
    discovery: Discovery,
    vm: DexViewModel,
    showSharedBadge: Boolean,
    onClick: () -> Unit,
) {
    // 도감에서 못 찾는 종이면 줄을 그리지 않는다 — 이름 없는 사진에 날짜만 남는다.
    val flower = vm.flower(discovery.flowerId) ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .clickable(onClick = onClick)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(THUMB)
                .clip(RoundedCornerShape(CfDimen.RadiusCard))
                .background(CfColor.Background),
            contentAlignment = Alignment.Center,
        ) {
            DiscoveryPhoto(
                photo = vm.photoFile(discovery),
                flower = flower,
                size = THUMB,
                contentDescription = flower.name,
            )
        }
        Spacer(Modifier.width(CfDimen.GapMedium))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
        ) {
            Text(
                text = flower.name,
                style = CfText.BodyBold,
                color = CfColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // ⚠️ **`capturedAt`이다**(`createdAt`이 아니다). 목록도 그 값으로 정렬하므로
            //    다른 값을 그리면 **순서가 뒤죽박죽인 목록**으로 보인다. 오프라인에서
            //    찍고 며칠 뒤 등록한 기록이 실제로 갈린다.
            Text(
                text = RelativeTime.day(discovery.capturedAt),
                style = CfText.Caption,
                color = CfColor.TextTertiary,
                maxLines = 1,
            )
            // 장소는 있을 때만. 없는 줄에 빈 칸을 남기면 줄 높이가 달라져
            // **목록이 들쭉날쭉해 보인다.**
            discovery.placeName?.takeIf { it.isNotBlank() }?.let { place ->
                Text(
                    text = place,
                    style = CfText.Caption,
                    color = CfColor.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showSharedBadge && DiscoveryRules.isShared(discovery)) {
            // 2절 13번의 `모두에게 공개` 대신 짧게 쓰지 않는다 — `Visibility.label`이
            // 원본이다. 새 축약어를 만들면 같은 상태를 두 이름으로 부르게 된다.
            Text(
                text = discovery.visibility.label,
                style = CfText.Tiny,
                color = CfColor.Primary,
                maxLines = 1,
            )
        }
    }
}

private val THUMB = 64.dp
