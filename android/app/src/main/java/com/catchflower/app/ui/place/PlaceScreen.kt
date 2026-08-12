package com.catchflower.app.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.ui.component.CfChipGroup
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.CfSmallButton
import com.catchflower.app.ui.component.CfStat
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 15 장소 상세 — `15_장소상세.svg`.
 *
 * 지도 핀의 프리뷰 카드에서 `자세히 보기`로 들어온다(와이어프레임 15 흐름:
 * `14 핀/프리뷰 → 장소 상세 → 16 기록 상세`).
 *
 * ## 이 화면이 지도와 다른 것
 *
 * 🔴 **지도는 내 기록만 그리고(로컬 저장소) 이 화면은 남의 공개 기록을 서버에서
 *    읽는다.** 그래서 **핀에 있던 숫자와 여기 숫자가 다를 수 있다** —
 *    핀 `기록 3개`(내 것)인데 여기는 `기록 12개`(모두의 것)다. 같아야 하는 값이
 *    아니므로 맞추려 하지 않는다.
 *
 * ⚠️ 반대로 **내 비공개 기록만 있는 자리**에서는 여기가 [PlaceUi.Empty]다
 *    (조회가 `visibility=neq.private`이다). 조회 실패와 구분해서 그린다.
 *
 * ## 예선 범위에서 뺀 것
 *
 * - **`1.2km`(A 문서 15번 장소명 줄).** 현재 위치를 추적하지 않아서 계산할 수 없다.
 *   넣으면 **가만히 있는데도 틀린 거리**가 보인다([com.catchflower.app.ui.map.MapScreen]의
 *   프리뷰 카드가 같은 이유로 뺐다).
 * - **`최신순 ▾` 정렬 변경.** 서버가 이미 최신순으로 주고(`order=created_at.desc`)
 *   다른 정렬이 없다. 라벨은 **정렬 상태 표시로만** 둔다 — 누르면 아무 일이 없는
 *   버튼으로 만들지 않는다(버튼이 아니라 텍스트다).
 */
@Composable
fun PlaceScreen(
    vm: PlaceViewModel,
    /**
     * 내 도감. `!` 배지가 이걸로 판정된다.
     *
     * 🔴 **[dexLoaded]와 함께 받는다.** 도감을 읽기 전의 빈 집합으로 그리면
     *    **모든 칩에 `!`** 가 붙고, 그건 200종을 모은 사용자에게 거짓이다
     *    ([PlaceRules.chips] 주석).
     */
    collectedIds: Set<Int>,
    dexLoaded: Boolean,
    onBack: () -> Unit,
    onOpenRecord: (Discovery) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `길찾기`는 카카오맵 앱으로 넘기는 기능이다(와이어프레임 15 주석 ①). 예선 범위
    // 밖이라 토스트를 띄운다 — 빈 람다로 두면 눌러도 아무 일이 없어서 고장으로 읽힌다.
    val toast = rememberToaster()
    val notReady: () -> Unit = { toast(CfToast.NOT_READY) }

    Column(modifier.fillMaxSize()) {
        CfHeader(
            // 장소명이 없으면 **좌표를 쓰지 않는다.** `37.5601, 126.9251`이 제목이 되면
            // 그게 장소 이름으로 읽힌다(프리뷰 카드와 같은 판단).
            title = vm.placeName ?: "내 발견",
            onBack = onBack,
            // 주석 ①: `길찾기 = 최상단 우측 고정`. 카드 내부 버튼이라 Small이다
            // (A 문서 1절 Small 예시가 `길찾기`다).
            trailing = { CfSmallButton(text = "길찾기", onClick = notReady) },
        )

        when (val state = vm.ui) {
            PlaceUi.Loading -> PlaceMessage(title = null)

            // A 문서 3절 빈 상태 `지도에 기록 없음`을 재사용한다. **새 문구를 쓰지 않는다.**
            PlaceUi.Empty -> PlaceMessage(
                title = "아직 이 근처에 공유된 꽃이 없어요",
                body = "첫 번째로 남겨보세요",
            )

            is PlaceUi.Failed -> PlaceMessage(
                // 3절 토스트의 네트워크 오류 문구다(화면 17·14가 같은 문장을 쓴다).
                title = "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                action = "다시 시도",
                onAction = vm::retry,
            )

            // 키 없는 빌드 — 문구도 버튼도 없다. 눌러도 안 되고 사용자 탓이 아니다.
            PlaceUi.NotConfigured -> Unit

            is PlaceUi.Loaded -> PlaceBody(
                state = state,
                vm = vm,
                collectedIds = collectedIds,
                dexLoaded = dexLoaded,
                onOpenRecord = onOpenRecord,
            )
        }
    }
}

@Composable
private fun PlaceBody(
    state: PlaceUi.Loaded,
    vm: PlaceViewModel,
    collectedIds: Set<Int>,
    dexLoaded: Boolean,
    onOpenRecord: (Discovery) -> Unit,
) {
    val data = state.data
    val others = data.othersRecords(vm.myUserId)
    val chips = PlaceRules.chips(
        flowerIdsRecentFirst = data.flowerIdsRecentFirst,
        nameOf = vm::flowerName,
        collectedIds = collectedIds,
        markMissing = dexLoaded,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CfDimen.ScreenPadding,
            end = CfDimen.ScreenPadding,
            // 하단 내비(64) + 촬영 FAB을 피한다.
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        // 🔴 **잘린 목록에서는 지표 3칸을 그리지 않는다.** 200행 상한에 걸린 장소에서
        //    `기록 200개`·`꽃 3종`은 **실제보다 작은 값**이고, 화면은 완벽히 정상으로
        //    보인다(`PlaceDiscoveries.truncated` 주석 · A 문서가 `좋아요 0`을 금지한
        //    것과 같은 종류의 거짓말이다).
        if (!data.truncated) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CfDimen.RadiusCard))
                        .background(CfColor.Surface)
                        .padding(vertical = CfDimen.Gap),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // A 문서 15번 `꽃 종류 5종 · 기록 12개 · 이번 주 3개`.
                    CfStat(label = "꽃 종류", value = "${data.speciesCount}종")
                    CfStat(label = "기록", value = "${data.recordCount}개")
                    CfStat(label = "이번 주", value = "${state.thisWeek}개")
                }
            }
        }

        if (chips.isNotEmpty()) {
            item {
                PlaceSectionRow(title = "이곳에서 발견된 꽃", note = "내 도감 기준")
            }
            item { FlowerChipFlow(chips) }
            // 주석 ④: `아이콘만으로는 중장년 타깃에 전달되지 않으므로 문장 범례를 항상 노출`.
            //
            // ⚠️ **`!`가 하나도 없어도 그린다.** 조건부로 그리면 도감을 다 모은
            //    사용자에게만 사라지는데, 그때 `!`가 무엇이었는지 설명이 없어진다.
            //    도감을 읽기 전([dexLoaded]가 false)에는 배지 자체를 안 붙이므로
            //    그 상태에서만 범례를 뺀다 — 없는 기호를 설명하게 된다.
            if (dexLoaded) {
                item {
                    Text(
                        "! 표시는 내 도감에 없는 꽃이에요",
                        style = CfText.Caption,
                        color = CfColor.TextSecondary,
                    )
                }
            }
        }

        item {
            PlaceSectionRow(
                title = "사람들의 기록",
                // ⚠️ **버튼이 아니다.** 서버가 최신순으로만 주고 다른 정렬이 없어서
                //    누를 것이 없다 — `CfTextButton`으로 두면 눌러도 아무 일이 없는
                //    버튼이 된다(`DeadButtonTest`가 잡는 그 결함).
                note = "최신순 ▾",
            )
        }

        if (others.isEmpty()) {
            item {
                // 🔴 **`Empty` 상태와 다른 경우다.** 이 자리에 온 것은 조회는 됐고
                //    **이 장소의 공개 기록이 전부 내 것**이라는 뜻이다.
                //    같은 문구를 쓰면 자기가 올린 기록이 안 보인다고 읽는다.
                Text(
                    "아직 이 근처에 공유된 꽃이 없어요",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                    modifier = Modifier.padding(vertical = CfDimen.GapMedium),
                )
            }
        } else {
            items(others, key = { it.id }) { discovery ->
                RecordCard(
                    discovery = discovery,
                    flowerName = vm.flowerName(discovery.flowerId),
                    onClick = { onOpenRecord(discovery) },
                )
            }
        }
    }
}

/**
 * 섹션 제목 + 우측 보조 문구.
 *
 * ⚠️ **공용 `SectionHeader`가 없다.** `MyScreen`·`DexHomeScreen`이 각자 private으로
 *    갖고 있고 둘 다 우측이 **버튼**이다. 여기 우측(`내 도감 기준`·`최신순 ▾`)은
 *    버튼이 아니라 설명이라 그 둘을 쓰면 **누를 수 있는 것처럼 보인다.**
 */
@Composable
private fun PlaceSectionRow(title: String, note: String) {
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
        Text(text = note, style = CfText.Caption, color = CfColor.TextTertiary)
    }
}

/**
 * `장미 개망초 금계국 접시꽃! 나팔꽃!` (와이어프레임 15).
 *
 * ⚠️ **가로 스크롤이 아니라 줄바꿈이다.** 한 장소에 20종이 찍히면 가로 스크롤에서는
 *    뒤쪽 `!` 칩이 화면 밖에 있고, 이 화면의 목적(주석 ③ `여기 가면 새 꽃 2종`)이
 *    스크롤해 봐야 성립한다.
 */
@Composable
private fun FlowerChipFlow(chips: List<PlaceRules.Chip>) {
    CfChipGroup(modifier = Modifier.fillMaxWidth()) {
        chips.forEach { chip -> MissingAwareChip(chip) }
    }
}

/**
 * 칩 하나. `!`는 **본문과 다른 색·굵기**로 그린다.
 *
 * 🔴 이름에 이어 붙이면 `접시꽃!`이 **꽃 이름처럼 보인다**([PlaceRules.Chip] 주석).
 *    그래서 [Text]를 두 개 두고 뒤쪽만 [CfColor.Warning]으로 그린다.
 *
 * ⚠️ **누를 수 없는 칩이다.** 도감 상세로 보내고 싶지만 이 화면은 지도 탭 안이고
 *    도감 탭으로 건너가면 **`뒤로`가 지도로 돌아오지 않는다**(내비가 `remember`
 *    상태라서 스택이 없다). 눌리게 해 두고 아무 데도 안 가는 것보다 낫다.
 *    → 그래서 [com.catchflower.app.ui.component.CfAttributeChip]과 같은 읽기 전용 모양이다.
 */
@Composable
private fun MissingAwareChip(chip: PlaceRules.Chip) {
    Row(
        modifier = Modifier
            .padding(vertical = CfDimen.GapTiny)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (chip.missingFromMyDex) CfColor.Surface else CfColor.Background)
            .border(
                width = CfDimen.BorderThin,
                color = if (chip.missingFromMyDex) CfColor.Warning else CfColor.Border,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chip.name,
            style = CfText.Chip,
            color = if (chip.missingFromMyDex) CfColor.TextPrimary else CfColor.TextSecondary,
        )
        if (chip.missingFromMyDex) {
            Text(
                text = " !",
                style = CfText.Chip,
                color = CfColor.Warning,
            )
        }
    }
}

/**
 * `사람들의 기록` 카드 (와이어프레임 15 주석 ⑤). 누르면 화면 16이다.
 *
 * ## 🔴 사진을 그리지 않는다
 *
 * 와이어프레임의 `사진` 칸은 남이 찍은 사진인데 **우리는 사진을 서버에 올리지 않는다**
 * (기기 로컬 파일이다). 그래서 그릴 것이 없다 —
 * [com.catchflower.app.ui.component.PhotoPlaceholder]로 채우면 회색 사각형이
 * **"사진을 못 불러왔다"로 읽히고**, 실제로는 존재하지 않는 사진이다
 * (그 컴포넌트 주석이 `발견 기록에는 쓰지 않는다`라고 못 박아 뒀다).
 * 대신 주석 ⑤의 다른 절(`한 줄 설명이 없으면 그 줄을 비우고`)을 지켜 **글자만** 그린다.
 *
 * ⚠️ 작성자 이름을 여기서 그리지 않는다. 닉네임 조회는 왕복이 하나 더 필요하고
 *    (목록 20개면 20명), 실패하면 **`탈퇴한 사용자예요`가 목록 전체에 깔린다.**
 *    이름은 화면 16에서 한 명만 묻는다.
 */
@Composable
private fun RecordCard(
    discovery: Discovery,
    flowerName: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .border(CfDimen.BorderThin, CfColor.Border, RoundedCornerShape(CfDimen.RadiusCard))
            .clickable(onClick = onClick)
            .padding(CfDimen.GapMedium),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 꽃 이름을 못 찾으면 **줄을 비우지 않고 시각만** 남긴다 — 도감에 없는
            // id는 새 시드가 안 깔린 기기에서 나오고, 그때도 카드는 눌릴 수 있어야 한다.
            if (flowerName != null) {
                Text(
                    text = flowerName,
                    style = CfText.BodyBold,
                    color = CfColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                // 🔴 `2시간 전`. **밀리초를 24시간으로 나누지 않는다**
                //    ([RelativeTime] 주석 — 날짜 경계로 센다).
                text = RelativeTime.detailed(discovery.createdAt),
                style = CfText.Tiny,
                color = CfColor.TextTertiary,
            )
        }
        // 주석 ⑤: `한 줄 설명이 없으면 그 줄을 비우고`. 빈 문자열로 그리면
        // 빈 줄이 남아 카드 높이만 들쭉날쭉해진다(화면 22 개화기 줄과 같은 처리).
        discovery.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = CfText.Body,
                color = CfColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 로딩·빈·실패 공통 안내.
 *
 * @param title `null`이면 **아무것도 그리지 않는다** — 로딩이다.
 *   🔴 `0종·0개`로 깜빡이면 기록이 있는 장소가 빈 장소로 보인다.
 */
@Composable
private fun PlaceMessage(
    title: String?,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (title == null) return@Box
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = CfText.Section,
                color = CfColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(CfDimen.GapSmall))
                Text(
                    text = body,
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.height(CfDimen.Gap))
                CfSecondaryButton(text = action, onClick = onAction)
            }
        }
    }
}
