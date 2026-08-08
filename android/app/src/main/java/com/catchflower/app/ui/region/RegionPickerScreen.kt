package com.catchflower.app.ui.region

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchflower.app.data.PlatformLocationSource
import com.catchflower.app.data.RegionCandidate
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import kotlinx.coroutines.launch

/**
 * 화면 02 활동 지역 선택 — `02_지역선택.svg`. **문구는 A 문서 02번 표와
 * 3절 `화면 02에서 이웃 수를 모를 때`가 전부다.**
 *
 * ## 🔴 이 화면이 없으면 랭킹이 영구히 빈다
 *
 * 서버 `region_ranking`은 `users.dong_code`가 null이면 `[]`를 준다(실측).
 * 화면 17·20·21이 전부 그 값에 걸려 있어서, 지역을 정하는 화면이 없으면
 * **서버 랭킹 전체가 아무에게도 안 보인다.**
 *
 * ## 🔴 여기서 고른 값은 6개월간 못 바꾼다
 *
 * 기획서 9장 · 0004 트리거가 서버에서 막는다. 그래서 이 화면은 **앱이 대신 고르지
 * 않는다** — 후보가 하나뿐일 때만 미리 선택해 두고(`현재 위치로 찾기`),
 * 검색 결과가 여럿이면 사용자가 누르게 한다.
 *
 * ⚠️ **되짚어 찾은 동네도 그대로 보여주고 누르게 한다.** `봉천동`을 치면 실측으로
 *    `관악구 중앙동`이 나온다([KakaoRegionSearchService] ②번 함정) — 자동 확정하면
 *    친 이름과 다른 동네 주민이 되고 **6개월간 못 바꾼다.**
 *
 * ## ⚠️ 건너뛸 수 있다
 *
 * 헤더에 `나중에 하기`를 둔다. 오너 결정이 "누구나 바로 플레이"이고
 * (`진행.md` 로그인 항목), 지역은 **랭킹에만** 필요하다 — 도감·촬영은 지역 없이 된다.
 * 여기서 막아 세우면 검색이 실패한 사용자가 앱에 들어오지도 못한다.
 * 화면 17의 `동네 선택하기`가 이 화면을 다시 연다.
 */
@Composable
fun RegionPickerScreen(
    /** 저장까지 끝났거나 사용자가 건너뛰었다. 부르는 쪽이 화면을 닫는다. */
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegionPickerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // `현재 위치로 우리 동네 찾기`가 좌표를 못 얻었다. **검색 실패와 다른 문구다** —
    // 사용자는 검색어를 의심하는 게 아니라 위치를 켜야 한다.
    var locationFailed by remember { mutableStateOf(false) }

    /**
     * ⚠️ **권한 요청은 화면이 한다** ([com.catchflower.app.data.PlatformLocationSource]
     *    주석: 그 클래스는 권한이 없으면 조용히 null을 준다). 여기서 안 물으면
     *    `현재 위치로 찾기`가 **눌러도 아무 일 없는 버튼**이 된다.
     */
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // 거부해도 화면은 살아 있다 — 검색으로 고르면 된다.
        if (granted.values.none { it }) {
            locationFailed = true
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val coord = PlatformLocationSource(context).current()
            if (coord == null) {
                locationFailed = true
            } else {
                locationFailed = false
                viewModel.onCoordinate(coord.lat, coord.lng)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .statusBarsPadding(),
    ) {
        Header(onSkip = onDone)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = CfDimen.ScreenPadding),
        ) {
            Spacer(Modifier.height(CfDimen.GapLarge))
            Text("어느 동네에서 활동하세요?", style = CfText.Hero, color = CfColor.TextPrimary)
            Spacer(Modifier.height(CfDimen.GapMedium))
            Text(
                "같은 동네 이웃들과 꽃 수집 순위를 겨루게 됩니다.",
                style = CfText.Body,
                color = CfColor.TextSecondary,
            )
            Spacer(Modifier.height(CfDimen.GapTiny))
            // A 문서 `설명 2 (강조)`. **강조가 스펙에 있는 이유**는 이 화면의 선택이
            // 되돌릴 수 없기 때문이다 — 색과 굵기로 그 무게를 준다.
            Text(
                "가입 후에는 6개월에 한 번만 변경할 수 있어요.",
                style = CfText.BodyBold,
                color = CfColor.Warning,
            )

            Spacer(Modifier.height(CfDimen.GapLarge))
            SearchField(
                value = viewModel.query,
                onValueChange = {
                    locationFailed = false
                    viewModel.onQueryChange(it)
                },
            )
            Spacer(Modifier.height(CfDimen.GapMedium))
            CfSecondaryButton(
                text = "현재 위치로 우리 동네 찾기",
                onClick = {
                    locationPermission.launch(
                        // COARSE·FINE을 **함께** 요청한다. API 31+에서 FINE만 요청하면
                        // `대략적 위치` 선택지가 안 나온다 — 동 단위면 대략도 충분하다
                        // (화면 03과 같은 이유).
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    )
                },
            )

            if (locationFailed) {
                Spacer(Modifier.height(CfDimen.GapSmall))
                // 3절 권한 재요청 시트의 위치 문구를 재사용한다. **새로 쓰지 않았다.**
                Text(
                    "지도에 남기려면 위치 권한이 필요해요",
                    style = CfText.Caption,
                    color = CfColor.Error,
                )
            }

            Spacer(Modifier.height(CfDimen.GapLarge))
            ResultSection(
                state = viewModel.list,
                selected = viewModel.selected,
                memberCounts = viewModel.memberCounts,
                onSelect = viewModel::onSelect,
            )
        }

        Footer(
            selected = viewModel.selected,
            save = viewModel.save,
            onConfirm = { viewModel.onConfirm(onDone) },
            onRetry = { viewModel.retrySave(onDone) },
            onSkip = onDone,
        )
    }
}

@Composable
private fun Header(onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CfColor.Surface)
            .padding(horizontal = CfDimen.ScreenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "활동 지역 선택",
            style = CfText.ScreenTitle,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        // 온보딩 2단계 중 첫 번째. 화면 03이 `2/2`로 이미 고정돼 있다.
        Text(text = "1/2", style = CfText.Body, color = CfColor.TextTertiary)
        Spacer(Modifier.size(CfDimen.GapMedium))
        // ⚠️ **건너뛰기가 헤더에 있다.** 하단은 Primary 하나(`{동명}으로 시작하기`)이고
        //    A 문서 1절이 Primary를 화면당 1개로 못 박았다. 아래에 두면 두 버튼이
        //    같은 무게로 보여서 **아무나 건너뛰게 된다** — 지역을 정하는 게 기본이다.
        com.catchflower.app.ui.component.CfTextButton(
            text = "나중에 하기",
            onClick = onSkip,
            color = CfColor.TextSecondary,
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        // A 문서 `검색창` 칸 그대로. 라벨이 아니라 placeholder다 — 라벨로 두면
        // 입력 중에 위로 올라가 `예: 연남동`이라는 예시가 사라진다.
        placeholder = {
            Text("동 이름으로 검색 (예: 연남동)", style = CfText.Body, color = CfColor.TextTertiary)
        },
        singleLine = true,
        shape = RoundedCornerShape(CfDimen.RadiusCard),
        // ⚠️ `ImeAction.Search`가 아니라 `Done`이다. 검색은 타이핑 중에 자동으로
        //    돌아가므로(디바운스) 눌러야 하는 버튼을 만들면 **안 눌러 본 사용자는
        //    결과가 안 나온다고 생각한다.**
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CfColor.Background,
            unfocusedContainerColor = CfColor.Background,
            focusedIndicatorColor = CfColor.Primary,
            unfocusedIndicatorColor = CfColor.Border,
            cursorColor = CfColor.Primary,
        ),
    )
}

/**
 * A 문서 `목록 라벨 = 검색 결과` + 목록.
 *
 * 🔴 **상태 다섯 개가 서로 다른 화면이다.** 뭉치면 각각 틀린 말이 된다 —
 *    [RegionPickerUi] 주석 참고.
 */
@Composable
private fun ResultSection(
    state: RegionPickerUi,
    selected: RegionCandidate?,
    memberCounts: Map<String, Int>,
    onSelect: (RegionCandidate) -> Unit,
) {
    when (state) {
        // 아직 안 검색했다. **여기에 `검색 결과가 없어요`를 그리지 않는다** —
        // 화면을 열자마자 자기 동네가 없는 앱으로 읽힌다.
        RegionPickerUi.Idle -> Unit

        RegionPickerUi.Searching -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = CfDimen.GapLarge),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = CfColor.Primary, modifier = Modifier.size(28.dp))
        }

        RegionPickerUi.NoResult -> Message(
            // A 문서 3절 `필터 결과 0`과 같은 구조(무엇이 없다 + 무엇을 해 보라).
            title = "검색 결과가 없어요",
            body = "동 이름으로 다시 검색해 보세요 (예: 연남동)",
        )

        // 카카오를 못 불렀다. **`검색 결과가 없어요`와 다른 화면이다** —
        // 3절 토스트의 네트워크 문구를 재사용한다.
        RegionPickerUi.Failed -> Message(
            title = "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
            body = null,
        )

        // 키 없는 빌드. **오류를 띄우지 않는다** — 사용자 탓이 아니다.
        // `나중에 하기`로 나갈 수 있으므로 막힌 화면이 되지 않는다.
        RegionPickerUi.NotConfigured -> Unit

        is RegionPickerUi.Loaded -> Column(Modifier.fillMaxWidth()) {
            Text("검색 결과", style = CfText.Section, color = CfColor.TextSecondary)
            Spacer(Modifier.height(CfDimen.GapSmall))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
                // 목록이 길어도 하단 CTA를 밀어내지 않는다.
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(state.candidates, key = { it.dongCode }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        isSelected = candidate.dongCode == selected?.dongCode,
                        // 🔴 **`memberCounts[코드]`가 null이면 줄을 안 그린다.**
                        //    0으로 바꾸지 마라 — `아직 이웃이 적어요`는 "세어 봤다"는
                        //    뜻이고, 이웃 1,284명인 동네를 비었다고 말하게 된다.
                        //    A 문서 3절 `화면 02에서 이웃 수를 모를 때`.
                        memberLabel = RegionPickerLogic.memberLabel(memberCounts[candidate.dongCode]),
                        onClick = { onSelect(candidate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: RegionCandidate,
    isSelected: Boolean,
    memberLabel: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(if (isSelected) CfColor.PrimaryLight else CfColor.Surface)
            .border(
                width = if (isSelected) CfDimen.BorderButton else CfDimen.BorderThin,
                color = if (isSelected) CfColor.Primary else CfColor.Border,
                shape = RoundedCornerShape(CfDimen.RadiusCard),
            )
            // `selectable`이라 스크린리더가 "선택됨"을 읽는다. `clickable`이면
            // 고른 항목과 안 고른 항목이 **똑같이 읽힌다.**
            .selectable(selected = isSelected, onClick = onClick)
            .padding(CfDimen.Gap)
            // 두 줄을 한 덩어리로 읽어준다 — 따로 읽으면 어느 동네의 이웃 수인지 모른다.
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(candidate.regionName, memberLabel)
                    .joinToString(". ")
            },
    ) {
        Text(
            text = candidate.regionName,
            style = CfText.BodyBold,
            color = CfColor.TextPrimary,
            // A 문서 4절 2번: `서울특별시 성동구 성수동1가`(13자)가 최장이라 잘리면
            // 어느 동네인지 알 수 없다. **줄여 쓰지 않고 두 줄까지 허용한다.**
            maxLines = 2,
        )
        if (memberLabel != null) {
            Spacer(Modifier.height(CfDimen.GapTiny))
            Text(text = memberLabel, style = CfText.Caption, color = CfColor.TextTertiary)
        }
    }
}

@Composable
private fun Message(title: String, body: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = CfDimen.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = CfText.BodyBold, color = CfColor.TextSecondary)
        if (body != null) {
            Spacer(Modifier.height(CfDimen.GapTiny))
            Text(body, style = CfText.Caption, color = CfColor.TextTertiary)
        }
    }
}

/**
 * 하단 CTA + A 문서 `하단 고지`.
 *
 * 🔴 **`선택한 동네는 {날짜}부터 변경할 수 있어요.`의 날짜를 여기서 만들지 않는다.**
 *    저장 **전**이라 서버가 정할 `region_changed_at`을 앱이 알 수 없다.
 *    날짜 없이 `변경할 수 있어요`만 쓰면 문장이 성립하지 않고, 앱이 계산한 날짜를
 *    쓰면 서버가 정한 날과 어긋난다(화면 20 주석 ④가 금지한 바로 그 화면).
 *    그래서 **위 설명 2(`6개월에 한 번만 변경할 수 있어요`)가 이 정보를 대신 전한다** —
 *    같은 사실을 날짜 없이 말하는 문장이고 이미 A 문서에 있다.
 *    날짜가 있는 줄은 화면 20이 `region_changed_at`을 받아서 그린다
 *    ([com.catchflower.app.ui.ranking.RankingUiMapper.regionChangeLabel]).
 */
@Composable
private fun Footer(
    selected: RegionCandidate?,
    save: RegionSaveUi,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CfColor.Background)
            .padding(CfDimen.ScreenPadding)
            .navigationBarsPadding(),
    ) {
        when (save) {
            // 🔴 서버가 규칙으로 거절했다. **`다시 시도`를 내밀지 않는다** —
            //    6개월 규칙은 눌러서 풀리지 않는다. 나갈 길만 준다.
            RegionSaveUi.RuleRejected -> {
                Text(
                    "활동 지역은 6개월에 한 번만 변경할 수 있어요.",
                    style = CfText.Body,
                    color = CfColor.Error,
                )
                Spacer(Modifier.height(CfDimen.GapSmall))
                CfSecondaryButton(text = "나중에 하기", onClick = onSkip)
            }

            RegionSaveUi.SaveFailed -> {
                // 3절 토스트의 네트워크 문구 재사용.
                Text(
                    "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                    style = CfText.Body,
                    color = CfColor.Error,
                )
                Spacer(Modifier.height(CfDimen.GapSmall))
                CfPrimaryButton(text = "다시 시도", onClick = onRetry)
            }

            else -> CfPrimaryButton(
                text = RegionPickerLogic.ctaLabel(selected),
                onClick = onConfirm,
                // 저장 중에도 막는다 — 연달아 누르면 요청이 겹친다.
                enabled = selected != null && save !is RegionSaveUi.Saving,
            )
        }
    }
}
