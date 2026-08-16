package com.catchflower.app.ui.ranking

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.model.RankedEntry
import com.catchflower.app.data.model.RankingRules
import com.catchflower.app.ui.component.CfSmallButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.FlowerIllust
import com.catchflower.app.ui.component.PhotoPlaceholder
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 17 랭킹·우리 동네 / 화면 18 랭킹·친구 — `17_랭킹_지역.svg` · `18_랭킹_친구.svg`.
 *
 * **한 파일에 둔 이유**: 탭 하나로 전환되는 같은 화면이고(주석 ①) 행 구성이 같다.
 * 문구는 A 문서 2절 17·18을 그대로 쓴다.
 */
@Composable
fun RankingScreen(
    vm: RankingViewModel,
    onOpenFriends: () -> Unit,
    onOpenLastSeason: () -> Unit,
    /**
     * 화면 02(활동 지역 선택)로 보낸다. 🔴 **이 화면이 아직 없다.**
     * 지역을 안 정하면 서버 랭킹은 영원히 비므로, 화면 17만으로는 사용자가
     * 빠져나갈 방법이 없다 — §9에 올렸다.
     */
    onPickRegion: () -> Unit,
    /** 화면 07(카메라)로 보낸다 — 빈 동네의 `꽃 찍어보기`. */
    onCapture: () -> Unit,
    /**
     * 내가 **이번 시즌 모은 종수**(기기에서 센 값 · 도감 헤더와 같은 출처).
     * 서버 상위 목록에 내 행이 없을 때 이 값으로 카드를 채운다 — 자세한 이유는
     * [MyRankCard]의 🔴. 아직 읽는 중이면 `null`이고, 그때는 아무 숫자도 그리지 않는다.
     */
    mySeasonSpeciesCount: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        // 헤더 우측 버튼이 탭마다 다르다 — 17은 `지난 시즌`, 18은 `친구 관리`.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CfDimen.ScreenPadding, vertical = CfDimen.GapMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("랭킹", style = CfText.ScreenTitle, color = CfColor.TextPrimary)
            Spacer(Modifier.weight(1f))
            when (vm.tab) {
                RankingTab.REGION -> CfTextButton(
                    text = "지난 시즌",
                    onClick = onOpenLastSeason,
                    iconRes = R.drawable.ic_season,
                )
                RankingTab.FRIENDS -> CfTextButton(text = "친구 관리", onClick = onOpenFriends)
            }
        }

        RankingTabs(current = vm.tab, onSelect = vm::selectTab)

        when (vm.tab) {
            RankingTab.REGION -> RegionRanking(
                vm,
                onPickRegion = onPickRegion,
                onCapture = onCapture,
                mySeasonSpeciesCount = mySeasonSpeciesCount,
            )
            RankingTab.FRIENDS -> FriendRanking(vm, onInvite = onOpenFriends)
        }
    }
}

/**
 * 탭 2개 — `우리 동네` / `친구`.
 *
 * ⚠️ 선택 상태를 **밑줄만으로** 표시하지 않는다. 색만·밑줄만으로 구분하면
 *    저시력 사용자에게 두 탭이 같아 보인다. 굵기 + 색 + 밑줄 세 가지를 함께 준다.
 */
@Composable
private fun RankingTabs(current: RankingTab, onSelect: (RankingTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CfColor.Background),
    ) {
        RankingTab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = tab.label) { onSelect(tab) }
                    .padding(vertical = CfDimen.GapMedium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.label,
                    style = if (selected) CfText.BodyBold else CfText.Body,
                    color = if (selected) CfColor.Primary else CfColor.TextSecondary,
                )
                Spacer(Modifier.height(CfDimen.GapSmall))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (selected) CfColor.Primary else CfColor.Border),
                )
            }
        }
    }
}

// ── 화면 17 우리 동네 ────────────────────────────────────────────────

/**
 * 화면 17.
 *
 * 🔴 **랭킹이 없는 상태가 네 가지다.** 더미를 읽던 때는 랭킹이 **항상 있었다** —
 *    서버를 붙이면서 생긴 분기이고, 하나로 뭉개면 다음이 섞인다:
 *    "동네를 안 골랐다"(사용자가 할 일이 있다) · "아무도 안 찍었다"(정상) ·
 *    "네트워크가 죽었다"(다시 시도) · "키 없는 빌드"(할 수 있는 게 없다).
 *    문구는 A 문서 3절 `화면 17 지역 랭킹의 빈·실패 상태`를 쓴다.
 */
@Composable
private fun RegionRanking(
    vm: RankingViewModel,
    onPickRegion: () -> Unit,
    onCapture: () -> Unit,
    /** 기기에서 센 내 이번 시즌 종수. 이유는 [MyRankCard]의 🔴. */
    mySeasonSpeciesCount: Int?,
) {
    val state = vm.region
    /**
     * `더 보기`를 눌렀나(2026-08-13 · 오너 `죽어있는 버튼 없도록 전부 구현해다오`).
     *
     * 🔴 **서버를 다시 부르지 않는다.** `region_ranking`에는 `limit`·`offset`이 없어서
     *    **이미 동네 전체가 내려와 있다**(0002 · `order by species_count desc` 뒤에
     *    자르는 절이 없다). 그동안 이 버튼이 죽어 있던 이유가 "더 못 읽어서"가 아니라
     *    **화면이 안 그려서**였다 — 필요한 것은 offset이 아니라 이 `Boolean`이다.
     *
     * ⚠️ 탭을 옮기면 접힌다. 그건 의도다 — 접힌 상태가 이 화면의 첫 모습이고,
     *    펼친 상태를 기억하면 `더 보기`가 없는 화면을 처음 보는 사람이 생긴다.
     */
    var expanded by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CfDimen.ScreenPadding,
            end = CfDimen.ScreenPadding,
            top = CfDimen.Gap,
            // 하단 내비에 가리지 않게 띄운다.
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        // 시즌 배너는 랭킹과 무관하게 항상 맞는 정보다(마감까지 남은 일수).
        item { SeasonBanner(vm, state) }

        when (state) {
            // A 문서: 로딩은 **문구를 두지 않는다.** `아직 없어요`를 깜빡이면
            // 랭킹이 있는 사용자에게도 없다고 말하는 순간이 생긴다.
            RegionRankingUi.Loading -> item { RankingLoading() }

            RegionRankingUi.NoRegion -> item {
                RegionEmptyState(
                    title = "활동 지역을 정하면 순위를 볼 수 있어요",
                    // 화면 02 `설명 1`과 **같은 문장**이다 — 같은 것을 설명하는 두
                    // 화면이 다른 말을 하면 사용자가 다른 기능으로 읽는다.
                    body = "같은 동네 이웃들과 꽃 수집 순위를 겨루게 됩니다.",
                    action = "동네 선택하기",
                    onAction = onPickRegion,
                )
            }

            RegionRankingUi.Empty -> item {
                RegionEmptyState(
                    title = "아직 이 동네에 모은 꽃이 없어요",
                    body = "첫 번째로 남겨보세요",
                    action = "꽃 찍어보기",
                    onAction = onCapture,
                )
            }

            RegionRankingUi.Failed -> item {
                RegionEmptyState(
                    // 3절 토스트의 네트워크 오류 문구를 재사용한다.
                    title = "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                    body = null,
                    action = "다시 시도",
                    onAction = vm::refresh,
                    secondary = true,
                )
            }

            // 키 없는 빌드 — 오류도 버튼도 띄우지 않는다. 눌러도 안 되고 사용자 탓이 아니다.
            RegionRankingUi.NotConfigured -> Unit

            is RegionRankingUi.Loaded -> {
                item { MyRankCard(vm, state, mySeasonSpeciesCount) }
                item {
                    Text(
                        // `연남동 이웃 1,284명`. 🔴 B-6이 구로 넓히면 **구명**이 온다 —
                        // 동명을 박아 두면 마포구 전체 순위를 연남동이라고 말한다.
                        "${state.regionLabel} 이웃 ${KoreanText.thousands(state.memberCount)}명",
                        style = CfText.Section,
                        color = CfColor.TextPrimary,
                    )
                }
                // 처음에는 [RankingRules.VISIBLE_ROWS]줄만 보여준다. 동네 전체가 이미
                // 손에 있으므로(위 [expanded] 주석) 자르는 판단은 여기서 한다.
                val visible = if (expanded) state.rows else state.rows.take(RankingRules.VISIBLE_ROWS)
                items(visible) { ranked -> RankRow(ranked, vm) }

                // 🔴 **더 보여줄 줄이 없으면 버튼을 그리지 않는다.** 이웃이 3명인 동네에서
                //    `4위부터 더 보기`가 뜨면 **없는 순위를 가리키는 버튼**이 되고, 눌러도
                //    아무 줄도 늘지 않아 고장으로 읽힌다((38)에서 지운 그 모양이다).
                val nextRank = RankingRules.nextPageRank(state.rows, visible.size)
                if (nextRank != null) {
                    item {
                        // `6위부터 더 보기`(A 문서 2절 17번).
                        CfTextButton(
                            text = "${nextRank}위부터 더 보기",
                            onClick = { expanded = true },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 랭킹을 불러오는 중.
 *
 * ⚠️ **`0종`·`-위` 같은 빈 값을 그리지 않는다.** 그러면 41종을 모은 사용자가
 *    화면을 열 때마다 0종을 먼저 본다 — 도감 `loaded` 플래그를 둔 것과 같은 이유다.
 */
@Composable
private fun RankingLoading() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = CfDimen.GapLarge),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = CfColor.Primary)
    }
}

/**
 * 랭킹이 없을 때. 문구는 **전부 A 문서 3절**에서 온다.
 *
 * ⚠️ 상태마다 **행동 버튼이 다르다** — 같은 "비었어요" 화면을 돌려 쓰면
 *    동네를 안 고른 사람에게 `꽃 찍어보기`를 내밀게 되고, 그 사람은 찍어도
 *    순위가 안 생긴다.
 */
@Composable
private fun RegionEmptyState(
    title: String,
    body: String?,
    action: String,
    onAction: () -> Unit,
    secondary: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = CfDimen.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(
                body,
                style = CfText.Body,
                color = CfColor.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(CfDimen.Gap))
        if (secondary) {
            com.catchflower.app.ui.component.CfSecondaryButton(text = action, onClick = onAction)
        } else {
            com.catchflower.app.ui.component.CfPrimaryButton(text = action, onClick = onAction)
        }
    }
}

/**
 * 시즌 배너 — `2026 시즌 2` / `{동명} 꽃 수집 순위` / `9월 30일 마감 · 47일 남음`.
 *
 * 남은 일수를 **크게** 보여준다 (와이어프레임 주석 ②: 마감 압박).
 * ⚠️ 휴지기(12~2월)에는 `마감`이 아니라 다음 시즌 `시작`을 센다 —
 *    [com.catchflower.app.core.SeasonClock]이 문장까지 만든다.
 */
@Composable
private fun SeasonBanner(vm: RankingViewModel, state: RegionRankingUi) {
    val season = vm.season
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.PrimaryLight)
            .padding(CfDimen.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(season.title, style = CfText.BodyBold, color = CfColor.Primary)
            Spacer(Modifier.height(CfDimen.GapTiny))
            // `{동명} 꽃 수집 순위`.
            // ⚠️ **지역명을 모를 때 이 줄을 아예 쓰지 않는다.** 더미 시절에는 항상
            //    `연남동`이 있었지만, 지역을 안 정했거나 조회에 실패하면 이름이 없다.
            //    빈칸을 두면 ` 꽃 수집 순위`가 되고, 기본값을 넣으면 **남의 동네
            //    이름을 내 화면에 박는다.**
            (state as? RegionRankingUi.Loaded)?.regionLabel?.takeIf { it.isNotEmpty() }?.let {
                Text("$it 꽃 수집 순위", style = CfText.Body, color = CfColor.TextPrimary)
                Spacer(Modifier.height(CfDimen.GapTiny))
            }
            Text(vm.deadlineLabel, style = CfText.Caption, color = CfColor.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${season.daysLeft}", style = CfText.HeroNumber, color = CfColor.Primary)
            Text("일 남음", style = CfText.Caption, color = CfColor.TextSecondary)
        }
    }
}

/**
 * 내 순위 고정 카드 — 리스트를 스크롤해 자기를 찾게 하지 않는다 (주석 ③).
 *
 * `3종만 더 모으면 15위권!`은 **행동으로 이어지는 문장**이라 순위보다 중요하다.
 *
 * 🔴 **더미가 만들어 주던 값 두 개가 서버에는 없다.**
 *    ① `▲ 3`(지난 시즌 대비 변동) — 서버가 안 보낸다. 0을 넣으면 `-`가 그려지고
 *      사용자는 **"순위가 안 변했다"는 정보로 읽는다.** 우리는 모르는 것이다.
 *    ② 내가 상위 목록 밖이면(6위 이하) 내 행이 아예 없다. `0위`로 채우면
 *      **1등보다 위에 있는 순위**를 그린다.
 *    둘 다 **문장을 지운다.** 없는 정보를 그리는 것보다 안 그리는 게 정직하다.
 *
 * 🔴 **그런데 종수는 모르는 값이 아니었다 — `?: 0`이 아는 값을 틀리게 그렸다.**
 *    실측(2026-08-16 · 스토어 스크린샷): 해바라기를 등록한 직후 도감은 `이번 시즌 1종`,
 *    같은 순간 이 카드는 **`이번 시즌 모은 꽃 0종`**이었다. 상위 목록에 내 행이 없어서
 *    (`myRegionRank == null`) 기본값 0이 그려진 것이다. 순위는 `-`로 비웠는데 종수는
 *    **0이라고 단정**했다 — 사용자에게는 "방금 모은 것이 사라졌다"로 읽힌다.
 *    그래서 서버 행이 없으면 **기기에서 센 값**([mySeasonSpeciesCount] · 도감 헤더와
 *    같은 출처)을 쓰고, 그것도 읽는 중이면 `-`를 그린다. **0은 사실 주장이다.**
 *    ⚠️ 서버 행이 있으면 그쪽을 그대로 쓴다 — 아래 목록의 내 행과 같은 숫자여야 한다.
 */
@Composable
private fun MyRankCard(
    vm: RankingViewModel,
    state: RegionRankingUi.Loaded,
    mySeasonSpeciesCount: Int?,
) {
    val mine = vm.myRegionRank
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .border(CfDimen.BorderButton, CfColor.Primary, RoundedCornerShape(CfDimen.RadiusCard))
            .padding(CfDimen.Gap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 순위", style = CfText.Body, color = CfColor.TextSecondary)
            Spacer(Modifier.width(CfDimen.GapSmall))
            if (mine != null) {
                Text("${mine.rank}위", style = CfText.Section, color = CfColor.TextPrimary)
                Spacer(Modifier.width(CfDimen.GapSmall))
                // 서버가 변동을 안 주므로 null이고, 그때 RankDelta는 아무것도 안 그린다.
                RankDelta(mine.delta)
            } else {
                // 상위 목록 밖이다. `-`는 "변동 없음"과 헷갈리지 않는 자리다.
                Text("-", style = CfText.Section, color = CfColor.TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("이번 시즌 모은 꽃", style = CfText.Tiny, color = CfColor.TextTertiary)
                // ⚠️ 지역 랭킹의 내 행에서 읽는다. 친구 랭킹(vm.me)에서 읽으면
                //    **친구가 없으면 0종이 되고**, 같은 사용자의 종수가 화면마다 달라진다.
                //    내 행이 없을 때 기기 값으로 내려가는 이유는 위 독스트링 🔴.
                val myCount = RankingRules.mySeasonSpeciesCount(
                    server = mine?.entry?.speciesCount,
                    local = mySeasonSpeciesCount,
                )
                Text(
                    if (myCount != null) "${myCount}종" else "-",
                    style = CfText.BodyBold,
                    color = if (myCount != null) CfColor.Primary else CfColor.TextTertiary,
                )
            }
        }
        // `{N}종만 더 모으면 {M}위권!` — 목표 순위 안에 들려면 몇 종이 더 필요한지를
        // **받은 목록에서 계산한다.** 더미의 3·15는 그냥 박아 둔 숫자였다.
        // 이미 목표 안이거나 내 행이 없으면 [RankingRules]가 null을 주고, 그때
        // **문장을 쓰지 않는다** — `0종만 더 모으면`은 말이 안 된다.
        val target = mine?.let {
            RankingRules.speciesToReach(state.rows, myRank = it.rank, targetRank = TARGET_RANK)
        }
        if (target != null) {
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(
                "${target}종만 더 모으면 ${TARGET_RANK}위권!",
                style = CfText.Body,
                color = CfColor.Primary,
            )
        }
    }
}

/**
 * `{N}종만 더 모으면 {M}위권!`의 목표 순위.
 *
 * ⚠️ 와이어프레임 예시가 `15위권`이라 그 숫자를 쓴다. **게임 규칙이 아니라 화면
 *    문구의 기준**이라 [com.catchflower.app.core.GamePolicy]에 넣지 않았다
 *    (랭킹 최소 인원과 다른 성격이다).
 */
private const val TARGET_RANK = 15

/**
 * 순위 변동 `▲ 3`.
 *
 * ⚠️ **화살표만 쓰지 않는다.** 빨강/파랑 화살표로만 방향을 구분하면 색각 이상
 *    사용자에게는 숫자만 남는다. 기호(▲▼)와 숫자를 함께 쓴다.
 */
@Composable
private fun RankDelta(delta: Int?) {
    // 🔴 **모를 때(null)는 아무것도 그리지 않는다.** `-`를 그리면 "변동 없음"으로
    //    읽히는데, 서버는 지난 시즌 순위를 아예 보내지 않는다. 0과 null은 다르다.
    if (delta == null) return
    if (delta == 0) {
        Text("-", style = CfText.Caption, color = CfColor.TextTertiary)
        return
    }
    val up = delta > 0
    Text(
        text = if (up) "▲ $delta" else "▼ ${-delta}",
        style = CfText.Caption,
        // 올랐을 때 초록, 내렸을 때 주황. 기호가 방향을 이미 말하므로 색은 보조다.
        color = if (up) CfColor.Success else CfColor.Warning,
    )
}

/**
 * 랭킹 행 — 순위 / 대표 꽃 썸네일 / 닉네임 / 종수 (주석 ④).
 *
 * 1위에만 왕관을 두던 와이어프레임 표기(`王`)는 **글자가 아니라 순위 배지**로 만든다.
 * `王`은 한자라 스크린리더가 "왕"으로 읽는다 — 순위를 읽어야 한다.
 */
@Composable
private fun RankRow(ranked: RankedEntry, vm: RankingViewModel) {
    val entry = ranked.entry
    val flowerName = vm.flowerName(entry.signatureFlowerId)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .then(
                // 내 행은 강조 테두리 (주석 ③).
                if (entry.isMe) {
                    Modifier.border(
                        CfDimen.BorderButton,
                        CfColor.Primary,
                        RoundedCornerShape(CfDimen.RadiusCard),
                    )
                } else {
                    Modifier.background(CfColor.Surface)
                }
            )
            // 🔴 **`clickable`을 지웠다 (2026-08-13).** `onClickLabel = "{닉네임} 도감 보기"`를
            //    달고 **빈 람다**를 갖고 있었다 — 스크린리더에게는 "도감을 여는 버튼"이라고
            //    말하면서 누르면 아무 일도 안 일어난다. 눈으로 보는 사람에게는 물결만 뜬다.
            //    ⚠️ `DeadButtonTest`가 **못 잡던 두 번째 모양**이다: 검사는 `onClick = {}`와
            //       `onClick = { 블록주석 }`을 보는데 이건 **줄 주석이 든 후행 람다**였다.
            //    상대 도감 요약(주석 ④)을 붙이려면 문구가 먼저 필요하다 — A 문서 4절 18번에
            //    올렸다. 그때까지는 **누를 수 없게 두는 것이 정직하다**(화면 20-2 `앱 버전`과
            //    같은 판단이고, 화면 01을 아직 안 만드는 이유도 같다).
            .padding(horizontal = CfDimen.GapMedium, vertical = CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(ranked.rank)
        Spacer(Modifier.width(CfDimen.GapMedium))
        SignatureFlower(vm, entry.signatureFlowerId, 40.dp)
        Spacer(Modifier.width(CfDimen.GapMedium))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (entry.isMe) "나 (${entry.nickname})" else entry.nickname,
                style = CfText.BodyBold,
                color = CfColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // `대표 꽃 · 장미`
                text = "대표 꽃 · $flowerName",
                style = CfText.Tiny,
                color = CfColor.TextTertiary,
                maxLines = 1,
            )
        }
        Text(
            "${entry.speciesCount}종",
            style = CfText.Section,
            color = if (entry.isMe) CfColor.Primary else CfColor.TextPrimary,
        )
    }
}

/**
 * 대표 꽃 썸네일.
 *
 * ⚠️ [FlowerIllust]는 **[com.catchflower.app.data.model.Flower]를 받는다** — id가 아니다.
 *    도감에서 못 찾은 id(잘못된 대표 꽃, 서버가 200종 밖의 값을 준 경우)에는
 *    빈칸을 두지 않고 [PhotoPlaceholder]를 둔다. 자리가 사라지면 행 정렬이 어긋나서
 *    "이 사람만 썸네일이 없다"가 아니라 "레이아웃이 깨졌다"로 보인다.
 */
@Composable
private fun SignatureFlower(vm: RankingViewModel, flowerId: Int, size: androidx.compose.ui.unit.Dp) {
    val flower = vm.flower(flowerId)
    if (flower != null) FlowerIllust(flower = flower, size = size) else PhotoPlaceholder(size = size)
}

/** 순위 숫자. 1~3위는 색으로도 구분하지만 **숫자를 지우지 않는다.** */
@Composable
private fun RankBadge(rank: Int) {
    val background = when (rank) {
        1 -> CfColor.Primary
        2, 3 -> CfColor.PrimaryLight
        else -> CfColor.Background
    }
    val textColor = when (rank) {
        1 -> CfColor.TextOnDark
        2, 3 -> CfColor.Primary
        else -> CfColor.TextSecondary
    }
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text("$rank", style = CfText.BodyBold, color = textColor)
    }
}

// ── 화면 18 친구 ────────────────────────────────────────────────────

@Composable
private fun FriendRanking(vm: RankingViewModel, onInvite: () -> Unit) {
    // 🔴 **응답이 오기 전에 초대 화면을 띄우면 안 된다.** 친구가 8명인 사용자도
    //    탭을 열 때마다 `아직 겨룰 친구가 없어요`를 먼저 보게 된다 —
    //    더미를 읽던 때는 목록이 항상 있었으니 없던 문제다.
    when (vm.friends) {
        FriendRankingUi.Loading -> {
            RankingLoading()
            return
        }
        FriendRankingUi.Failed -> {
            // 실패를 `친구가 없다`로 보여주면 사용자는 **친구가 사라졌다고 읽는다.**
            RegionEmptyState(
                title = "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                body = null,
                action = "다시 시도",
                onAction = vm::refresh,
                secondary = true,
            )
            return
        }
        // 키 없는 빌드에서는 친구 기능 자체가 없다 — 초대 화면을 보여준다(할 일이 있다).
        FriendRankingUi.NotConfigured -> Unit
        is FriendRankingUi.Loaded -> Unit
    }

    // 친구 0~2명이면 랭킹 자체가 무의미하므로 초대 화면으로 **전체 대체** (주석 ④).
    //
    // ⚠️ 조건이 [RankingUiMapper.showInvite] **그대로**여야 한다. 개발 토글을 위해
    //    `|| forceNoFriends`를 붙여 뒀던 자리인데, 그러면 테스트가 재는 조건과
    //    화면이 쓰는 조건이 갈린다(2026-08-09에 떼면서 남긴 주석 참고).
    if (vm.showInviteInsteadOfRanking || vm.friends is FriendRankingUi.NotConfigured) {
        NoFriendsInvite(onInvite = onInvite)
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CfDimen.ScreenPadding,
            end = CfDimen.ScreenPadding,
            top = CfDimen.Gap,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A 문서 `친구 수를 모를 때의 문구` — 못 세면 **숫자만 뺀다.**
                // 🔴 `0명`을 넣으면 틀린 말인 데다 초대 화면 조건과 겹친다.
                Text(
                    vm.friendCount?.let { "친구 ${it}명과 겨루는 중" } ?: "친구와 겨루는 중",
                    style = CfText.Section,
                    color = CfColor.TextPrimary,
                )
                // 🔴 여기 있던 `[개발] 친구 없는 화면` 버튼을 지웠다(2026-08-09).
                //    개발용 라벨을 붙인 버튼이 **사용자 화면에 그대로 떠 있었다.**
            }
        }
        item { Podium(vm) }
        item {
            vm.behindLeader?.let { (leader, gap) ->
                // `{1위 닉네임}까지 25종 남음` — 격차를 종수로 환산 (주석 ②).
                Text(
                    "${leader}까지 ${gap}종 남음",
                    style = CfText.Body,
                    color = CfColor.Primary,
                )
            }
        }
        items(vm.friendRest) { ranked -> RankRow(ranked, vm) }
        item { InvitePrompt(onInvite) }
    }
}

/**
 * 시상대 1~3위 (주석 ①).
 *
 * 지역 랭킹(수백~수천 명)과 달리 친구는 소수라 리스트보다 시상대가 '우리끼리'의
 * 감각을 만든다. 가운데가 1위, 왼쪽 2위, 오른쪽 3위 — 와이어프레임 배치를 따른다.
 */
@Composable
private fun Podium(vm: RankingViewModel) {
    val podium = vm.podium
    if (podium.isEmpty()) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        // 2위 · 1위 · 3위 순서. 없으면 자리를 비운다 (친구 3명 미만).
        listOf(1, 0, 2).forEach { index ->
            podium.getOrNull(index)?.let { PodiumSlot(it, vm, isWinner = index == 0) }
        }
    }
}

@Composable
private fun PodiumSlot(ranked: RankedEntry, vm: RankingViewModel, isWinner: Boolean) {
    val entry = ranked.entry
    Column(
        Modifier
            .width(if (isWinner) 112.dp else 96.dp)
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .then(
                if (entry.isMe) {
                    Modifier.border(
                        CfDimen.BorderButton,
                        CfColor.Primary,
                        RoundedCornerShape(CfDimen.RadiusCard),
                    )
                } else {
                    Modifier.background(CfColor.Surface)
                }
            )
            .padding(vertical = CfDimen.GapMedium, horizontal = CfDimen.GapSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RankBadge(ranked.rank)
        Spacer(Modifier.height(CfDimen.GapSmall))
        SignatureFlower(vm, entry.signatureFlowerId, if (isWinner) 64.dp else 52.dp)
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            text = if (entry.isMe) "나 (${entry.nickname})" else entry.nickname,
            style = CfText.BodyBold,
            color = CfColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text("${entry.speciesCount}종", style = CfText.Body, color = CfColor.Primary)
    }
}

/** 초대 유도 — `친구가 많을수록 재미있어요`. 리스트 아래에 둔다. */
@Composable
private fun InvitePrompt(onInvite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("친구가 많을수록 재미있어요", style = CfText.BodyBold, color = CfColor.TextPrimary)
            Spacer(Modifier.height(CfDimen.GapTiny))
            Text(
                "연락처에 저장된 지인을 초대해 보세요",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
        }
        Spacer(Modifier.width(CfDimen.GapMedium))
        CfSmallButton(text = "초대", onClick = onInvite)
    }
}

/**
 * 친구 0~2명 — 랭킹 대신 초대 CTA가 **화면의 주인공**이 된다 (주석 ④).
 *
 * 문구는 A 문서 3절 빈 상태 `친구 없음` 항목을 쓴다:
 * `아직 겨룰 친구가 없어요` / `연락처에서 지인을 찾아보세요`.
 */
@Composable
private fun NoFriendsInvite(onInvite: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("아직 겨룰 친구가 없어요", style = CfText.Section, color = CfColor.TextPrimary)
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            "연락처에서 지인을 찾아보세요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapLarge))
        com.catchflower.app.ui.component.CfPrimaryButton(
            text = "초대 링크 보내기",
            onClick = onInvite,
        )
        // 🔴 여기 있던 `[개발] 랭킹으로 돌아가기` 버튼을 지웠다(2026-08-09).
        //    친구 0명이 실제 상태가 된 지금, 되돌릴 랭킹 자체가 없다.
    }
}
