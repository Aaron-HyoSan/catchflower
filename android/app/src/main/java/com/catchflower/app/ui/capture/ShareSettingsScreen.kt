package com.catchflower.app.ui.capture

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.KoreanText
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.DiscoveryPhoto
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import java.io.File
import java.util.Calendar

/**
 * 화면 13 지도 공유 설정 — `13_지도공유설정.svg` · A 문서 13번 표.
 *
 * 흐름은 `10/11 지도에 공유하기 → 13 → 14 지도`이고, `공유하지 않기`는
 * **05 도감 상세**로 간다(와이어프레임 주석 ⑤). 기록은 이미 저장돼 있다 —
 * 이 화면이 정하는 것은 **공개 범위와 한 줄**뿐이다.
 *
 * ⚠️ **판단은 [ShareRules]에 있다.** 40자 자르기·빈 문자열 처리·좌표 유무를 여기서
 *    다시 구현하지 않는다. `adb shell input text`가 한글을 못 보내서 **이 화면을
 *    기기로 눌러도 입력 규칙은 검증되지 않는다**(8차에 겪었다).
 *
 * ⚠️ **`변경`(장소 재선택)을 넣지 않았다.** A 문서와 와이어프레임에 있지만
 *    화면 15(장소 상세)와 카카오 장소 검색이 예선 범위 밖이라, 넣으면 **눌러도
 *    아무 일도 안 나는 버튼**이 된다 — 이 저장소가 반복해서 지적한 결함이다
 *    (`MapUnavailable`의 `다시 시도`, `CameraScreen`의 `onHelp = {}`,
 *    온보딩의 연락처 카드). 장소는 지금 촬영 시점 좌표로만 정해진다.
 *
 * 🔴 **좌표가 없으면 `공유하기`를 막는다.** 위치 권한 없이 찍으면 좌표가 null이고
 *    그게 정상 경로다(화면 03의 약속). 그 기록을 공개로 저장하면
 *    [com.catchflower.app.ui.map.MapPins.from]이 좌표 없는 기록을 버리므로
 *    **동의만 받고 어느 지도에도 못 올린다.** 안내 문구는 A 문서 권한 재요청 시트의
 *    위치 항목을 그대로 쓴다 — `RegionPickerScreen`이 같은 이유로 이미 쓰고 있다.
 *
 * @param discoveryCount 이 꽃의 누적 발견 횟수. 카드의 `{서수} 발견`이 쓴다.
 * @param photo 저장된 사진 파일. 없으면 도감 일러스트로 대체한다.
 * @param onShare (공개 범위, 한 줄). 한 줄은 **안 썼으면 null이다** — `""`가 아니다.
 */
@Composable
fun ShareSettingsScreen(
    flower: Flower,
    discovery: Discovery,
    discoveryCount: Int,
    photo: File?,
    onShare: (Visibility, String?) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 기본값은 `모두에게 공개`다 (와이어프레임 주석 ③).
    //
    // ⚠️ 저장된 기록의 값(`private`)을 초기값으로 쓰지 않는다. [CaptureViewModel.record]가
    //    일부러 비공개로 저장하기 때문에, 그 값을 따르면 이 화면이 **매번 아무것도
    //    선택되지 않은 것처럼** 보인다.
    var visibility by remember { mutableStateOf(Visibility.PUBLIC) }
    var note by remember { mutableStateOf("") }

    val canShare = ShareRules.canPlaceOnMap(discovery.lat, discovery.lng)

    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            // 키보드가 올라오면 그만큼 밀어 올린다. 안 주면 한 줄을 쓰는 동안
            // `공유하기`가 키보드 밑에 깔린다.
            .imePadding(),
    ) {
        // `뒤로`를 두지 않는다 — 되돌아갈 화면 10/11은 `{N}번째 꽃`·시즌 종수를 들고
        // 있어서 상태를 다시 만들어야 하고, A 문서 13번 표에도 없다.
        // 나가는 길은 `공유하기`와 `공유하지 않기` 둘이다.
        CfHeader(title = "지도에 공유하기")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CfDimen.ScreenPadding),
        ) {
            Text(
                "이 꽃을 지도에 공유할까요?",
                style = CfText.ScreenTitle,
                color = CfColor.TextPrimary,
            )
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(
                "공유하면 다른 사람이 이 장소에서 꽃을 찾아볼 수 있어요.",
                style = CfText.Body,
                color = CfColor.TextSecondary,
            )

            Spacer(Modifier.height(CfDimen.GapLarge))
            // 주석 ①: 공유 대상 확인 카드 — 사진·이름·날짜를 다시 보여준다.
            TargetCard(
                flower = flower,
                photo = photo,
                dateLine = "${shareDate(discovery.createdAt)} · " +
                    "${KoreanText.ordinal(discoveryCount)} 발견",
            )

            Spacer(Modifier.height(CfDimen.GapSection))
            PlaceSection(placeName = discovery.placeName, canShare = canShare)

            Spacer(Modifier.height(CfDimen.GapSection))
            Text("누구에게 보여줄까요?", style = CfText.Section, color = CfColor.TextPrimary)
            Spacer(Modifier.height(CfDimen.GapMedium))
            // 주석 ③: **2택이다.** `나만 보기`를 넣지 않는다 — `공유하지 않기`와 결과가
            // 같아서(둘 다 `private`으로 남는다) 셋을 두면 사용자가 차이를 고민한다.
            // iOS가 같은 이유로 2개만 둔다.
            VISIBILITY_OPTIONS.forEach { option ->
                VisibilityRow(
                    option = option,
                    isSelected = visibility == option,
                    onSelect = { visibility = option },
                )
                Spacer(Modifier.height(CfDimen.GapSmall))
            }

            Spacer(Modifier.height(CfDimen.GapLarge))
            NoteSection(
                note = note,
                // 🔴 **입력마다 [ShareRules.sanitize]를 지난다.** 붙여넣기로 40자를
                //    넘겨도 여기서 잘린다. `singleLine`은 붙여넣기를 막지 못한다.
                onNoteChange = { note = ShareRules.sanitize(it) },
            )

            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CfDimen.ScreenPadding)
                .padding(bottom = CfDimen.GapLarge),
        ) {
            CfPrimaryButton(
                text = "공유하기",
                onClick = { onShare(visibility, ShareRules.toStored(note)) },
                enabled = canShare,
            )
            Spacer(Modifier.height(CfDimen.GapSmall))
            CfTextButton(text = "공유하지 않기", onClick = onSkip)
        }
    }
}

/** A 문서 13번 표의 옵션 1·2. 순서도 표를 따른다. */
private val VISIBILITY_OPTIONS = listOf(Visibility.PUBLIC, Visibility.FRIENDS)

/**
 * 공개 범위 부연 (A 문서 13번 표 옵션 1·2의 뒷문장).
 *
 * ⚠️ [Visibility]에 두지 않았다. 그 enum은 서버 계약(`wire`)을 들고 있는 core 타입이고,
 *    화면 문구를 넣으면 A 문서와 계약 문서 두 곳이 같은 필드를 고치게 된다.
 */
private fun detailOf(visibility: Visibility): String = when (visibility) {
    Visibility.PUBLIC -> "지도를 보는 누구나 볼 수 있어요"
    Visibility.FRIENDS -> "연락처로 연결된 친구만 볼 수 있어요"
    // 화면에 없는 선택지다 (위 [VISIBILITY_OPTIONS] 주석). when을 완전하게 두기 위해서만 있다.
    Visibility.PRIVATE -> ""
}

/**
 * 공개 범위 한 줄.
 *
 * ⚠️ **[selectable]이다 — `clickable`이 아니다.** 스크린리더가 "선택됨"을 읽어야
 *    고른 항목과 안 고른 항목이 구분된다. `CfChip`은 `Role.Checkbox`라 2택 라디오에
 *    맞지 않는다. `RegionPickerScreen.CandidateRow`와 같은 판단이다.
 */
@Composable
private fun VisibilityRow(
    option: Visibility,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val detail = detailOf(option)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(if (isSelected) CfColor.PrimaryLight else CfColor.Surface)
            .border(
                width = if (isSelected) CfDimen.BorderButton else CfDimen.BorderThin,
                color = if (isSelected) CfColor.Primary else CfColor.Border,
                shape = RoundedCornerShape(CfDimen.RadiusCard),
            )
            .selectable(selected = isSelected, onClick = onSelect)
            .padding(CfDimen.Gap)
            // 두 줄을 한 덩어리로 읽는다. 안 묶으면 라벨과 부연을 따로 읽어서
            // 무엇을 고르는 항목인지 흐려진다.
            .clearAndSetSemantics { contentDescription = "${option.label}. $detail" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        // 아이콘은 **장식이다** — 선택 여부는 배경·테두리·라벨 색이 이미 나른다
        // (여기서 아이콘을 회색조로 돌리면 두 행이 "둘 다 안 골라진 것"처럼 보인다).
        // 위 `clearAndSetSemantics`가 행 전체를 한 덩어리로 읽으므로 낭독에도 안 낀다.
        iconOf(option)?.let { com.catchflower.app.ui.component.CfIcon(id = it, size = 22.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny)) {
            Text(
                option.label,
                style = CfText.BodyBold,
                color = if (isSelected) CfColor.Primary else CfColor.TextPrimary,
            )
            Text(detail, style = CfText.Caption, color = CfColor.TextSecondary)
        }
    }
}

/**
 * 공개 범위 아이콘.
 *
 * ⚠️ [Visibility.PRIVATE]은 **`null`이다.** 이 화면의 선택지는
 * [VISIBILITY_OPTIONS](공개·친구만) 둘뿐이고 `비공개`는 `공유하지 않기` 버튼으로
 * 처리된다 — 납품 `비공개.png`(자물쇠)를 여기 끼우면 **없는 선택지 하나를 그린다.**
 *
 * ⚠️ `else`로 닫지 않는다. 범위가 하나 늘면 여기서 컴파일이 깨져야 한다
 * ([com.catchflower.app.ui.component.iconRes]와 같은 이유).
 */
@androidx.annotation.DrawableRes
private fun iconOf(option: Visibility): Int? = when (option) {
    Visibility.PUBLIC -> com.catchflower.app.R.drawable.ic_public
    Visibility.FRIENDS -> com.catchflower.app.R.drawable.ic_friends_only
    Visibility.PRIVATE -> null
}

/**
 * 주석 ①의 확인 카드.
 *
 * ⚠️ 사진이 없으면 **빈 회색 칸을 두지 않고** 도감 일러스트를 그린다. 사진 저장은
 *    실패해도 등록을 막지 않으므로([CaptureViewModel.record]) 실제로 없을 수 있고,
 *    "무엇을 공유하는지 다시 보여주는" 카드가 비면 카드의 목적이 사라진다.
 */
@Composable
private fun TargetCard(flower: Flower, photo: File?, dateLine: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SharePhoto(photo = photo, flower = flower, size = 72.dp)
        Spacer(Modifier.size(CfDimen.GapMedium))
        Column(verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny)) {
            Text(flower.name, style = CfText.Section, color = CfColor.TextPrimary)
            Text(dateLine, style = CfText.Caption, color = CfColor.TextSecondary)
        }
    }
}

/**
 * 저장된 사진 한 장.
 *
 * 🔴 **직접 디코딩하지 않는다.** (39)까지 이 함수는 `BitmapFactory.decodeFile`을
 *    옵션 없이 불렀다 — 72dp 칸에 **1600px 원본(약 10MB)**을 그대로 올린 것이고,
 *    화면으로는 완벽하게 정상이었다. 지금은 화면 05와 **같은 [DiscoveryPhoto]**를 쓴다.
 *    두 화면이 사진을 각각 그리던 동안 **화면 05는 회색 박스였다** —
 *    같은 데이터를 두 곳에서 그리면 한쪽이 틀려도 아무도 모른다.
 */
@Composable
private fun SharePhoto(photo: File?, flower: Flower, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(CfDimen.GapSmall))
            .background(CfColor.Background),
        contentAlignment = Alignment.Center,
    ) {
        DiscoveryPhoto(
            photo = photo,
            flower = flower,
            size = size,
            // 여기는 **낭독이 필요하다** — 화면 05 목록과 달리 이 카드는
            // "무엇을 공유하는지"를 확인시키는 것이 목적이고, 옆 줄은 꽃 이름과
            // 날짜만 읽는다(사진이 그 확인의 절반이다).
            contentDescription = "방금 찍은 사진",
        )
    }
}

/**
 * 주석 ②: 장소는 **이름 단위로만** 보여주고, `정확한 위치는 공개되지 않아요`를 상시 노출한다.
 *
 * ⚠️ 장소명이 없으면 **줄을 뺀다.** 좌표를 대신 쓰면 `37.5601, 126.9251`이 장소 이름이
 *    된다(`MapScreen.PinPreviewCard`와 같은 판단).
 */
@Composable
private fun PlaceSection(placeName: String?, canShare: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall)) {
        Text("발견 장소", style = CfText.Section, color = CfColor.TextPrimary)

        if (!canShare) {
            // A 문서 권한 재요청 시트의 `위치` 항목 두 줄을 그대로 쓴다.
            // `RegionPickerScreen`이 같은 문구를 같은 이유로 이미 쓴다 —
            // **새 문구를 만들지 않는다**(`장소를 알 수 없어요` 류는 A 문서에 없다).
            Text(
                "지도에 남기려면 위치 권한이 필요해요",
                style = CfText.Body,
                color = CfColor.Error,
            )
            Text(
                "도감 등록은 그대로 할 수 있어요",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
            return@Column
        }

        if (placeName != null) {
            Text(placeName, style = CfText.BodyBold, color = CfColor.TextPrimary)
        }
        Text(
            "정확한 위치는 공개되지 않아요",
            style = CfText.Caption,
            color = CfColor.TextSecondary,
        )
    }
}

/** 주석 ④: 한 줄은 **선택**이고 40자다. */
@Composable
private fun NoteSection(note: String, onNoteChange: (String) -> Unit) {
    Column {
        Text(
            "한 줄 남기기 (안 써도 돼요)",
            style = CfText.Section,
            color = CfColor.TextPrimary,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "예: 숲길 끝 벤치 옆에 활짝 피었어요",
                    style = CfText.Body,
                    color = CfColor.TextTertiary,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(CfDimen.RadiusCard),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CfColor.Background,
                unfocusedContainerColor = CfColor.Background,
                focusedIndicatorColor = CfColor.Primary,
                unfocusedIndicatorColor = CfColor.Border,
                cursorColor = CfColor.Primary,
            ),
        )
        Spacer(Modifier.height(CfDimen.GapTiny))
        Text(
            // 🔴 카운터도 [ShareRules]가 센다. `note.length`로 세면 이모지 한 개가 2로
            //    보여서 **서버가 세는 수(`char_length`)와 다른 숫자**를 띄운다.
            text = ShareRules.counter(note),
            style = CfText.Caption,
            color = if (ShareRules.isAtLimit(note)) CfColor.Warning else CfColor.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 와이어프레임 13의 `2026. 6. 14.` 형식.
 *
 * ⚠️ `DexDetailScreen`의 `fullDate`를 쓰지 않는다 — 그쪽은 `private`이고 **요일을
 *    덧붙인다**(`2026. 6. 14. 토`). 와이어프레임 13에는 요일이 없다.
 */
private fun shareDate(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "${cal.get(Calendar.YEAR)}. ${cal.get(Calendar.MONTH) + 1}. " +
        "${cal.get(Calendar.DAY_OF_MONTH)}."
}
