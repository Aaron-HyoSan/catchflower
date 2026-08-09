package com.catchflower.app.ui.my

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.DiscoveryRules
import com.catchflower.app.data.model.SeasonResult
import com.catchflower.app.ui.ranking.RankingUiMapper
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.CfStat
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 20 마이페이지 — `20_마이페이지.svg`.
 *
 * 문구는 A 문서 2절 20을 그대로 쓴다. 새로 쓴 문장은 없다.
 *
 * ⚠️ 지표 3칸은 **시즌 초기화와 무관한 누적 수치**다(주석 ②). 시즌 랭킹의
 *    `13종`과 여기 `37종`이 다른 것은 버그가 아니다 — 랭킹은 시즌 내 종수,
 *    여기는 영구 종수다. 두 값을 같게 만들려는 '수정'을 하지 않는다.
 */
@Composable
fun MyScreen(
    /**
     * 서버가 준 내 프로필. **칸마다 따로 `null`일 수 있다** —
     * 이유는 [RankingUiMapper.profile]에 있다.
     */
    profile: RankingUiMapper.ProfileUi,
    /**
     * 기기 기록으로 센 누적 지표. **읽는 중이면 `null`이다**(0이 아니다) —
     * 이유는 [com.catchflower.app.ui.dex.DexViewModel.profileStats]에 있다.
     */
    stats: DiscoveryRules.ProfileStats?,
    /** `null`이면 **모른다** — 값 칸을 비운다(A 문서 `친구 수를 모를 때의 문구`). */
    friendCount: Int?,
    onOpenFriends: () -> Unit,
    onOpenLastSeason: () -> Unit,
    /** 활동 지역을 아직 안 골랐을 때 여는 화면 02. */
    onPickRegion: () -> Unit,
    /** 프로필 조회가 실패했을 때의 `다시 시도`. [RankingUiMapper.profile]이 실패를 가른다. */
    onRetryProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 예선 범위 밖 버튼들이 쓴다. **아무 일도 안 하는 버튼을 남기지 않는다**((38)) —
    // 코드에 `TODO`만 달려 있으면 누른 사람에게는 앱이 고장난 것으로 보인다.
    val toast = rememberToaster()
    val notReady: () -> Unit = { toast(CfToast.NOT_READY) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CfDimen.ScreenPadding, vertical = CfDimen.GapMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("마이", style = CfText.ScreenTitle, color = CfColor.TextPrimary)
                Spacer(Modifier.weight(1f))
                CfTextButton(text = "설정", onClick = { notReady() })
            }
        }

        item { ProfileBlock(profile = profile, onRetry = onRetryProfile, notReady = notReady) }

        // 지표 3칸 — 종수(경쟁 축) / 발견 횟수(활동량) / 공유 수(기여도).
        //
        // 🔴 **아직 못 셌으면 칸을 아예 그리지 않는다.** `0종`을 그리면 200종을 모은
        //    사용자가 마이 탭을 열 때마다 **도감이 비었다고 말하는 화면**을 한 프레임
        //    본다. 도감 홈이 [com.catchflower.app.ui.dex.DexViewModel.loading]으로
        //    화면 22를 막는 것과 같은 이유다.
        stats?.let { s ->
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CfDimen.ScreenPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CfStat(label = "모은 꽃", value = "${s.speciesCount}종")
                    CfStat(label = "총 발견", value = "${s.discoveryCount}회")
                    // `공유`는 **공개로 올린 것**만이다 — 이유는 [DiscoveryRules.profileStats].
                    CfStat(label = "공유", value = "${s.shareCount}개")
                }
                Spacer(Modifier.height(CfDimen.GapLarge))
            }
        }

        item {
            // 🔴 **더미 배지 3개를 지웠다.** 서버에 배지가 없다(01 스키마에 테이블이
            //    없고, 배지 종류는 B-9 오너 미확정이다). 그런데 `봄꽃 수집가`를 그리면
            //    **아무 것도 안 한 사용자에게 배지 3개를 받았다고 말한다** — 게임의
            //    보상을 없는 것으로 만드는 거짓말이고, 화면은 완벽하게 정상으로 보인다.
            //    A 문서 3절 `배지 없음` 빈 상태를 그대로 쓴다.
            SectionHeader(title = "내 배지", actionLabel = null, onAction = {})
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CfDimen.ScreenPadding),
            ) {
                Text("아직 받은 배지가 없어요", style = CfText.Body, color = CfColor.TextSecondary)
                Text(
                    "시즌이 끝나면 받을 수 있어요",
                    style = CfText.Tiny,
                    color = CfColor.TextTertiary,
                )
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        item {
            RegionBlock(
                regionFull = profile.regionFull,
                changeAvailableLabel = profile.regionChangeLabel,
                onPickRegion = onPickRegion,
            )
        }

        item {
            // 메뉴 — 친구 관리·지난 시즌만 실제로 연결되고 나머지는 아직 화면이 없다.
            MenuRow(
                label = "친구 관리",
                // 🔴 못 세면 `0명`이 아니라 **빈 칸**이다. 화면 18과 같은 값을 읽으므로
                //    두 화면이 서로 다른 친구 수를 말할 수 없다.
                value = friendCount?.let { "${it}명" },
                onClick = onOpenFriends,
            )
            MenuRow(
                label = "내가 공유한 꽃",
                // 위 지표 3칸의 `공유`와 **같은 값을 읽는다.** 각자 세면 갈라진다.
                value = stats?.let { "${it.shareCount}개" },
                onClick = notReady,
            )
            MenuRow(label = "지난 시즌 기록", value = null, onClick = onOpenLastSeason)
            MenuRow(label = "알림 설정", value = null, onClick = notReady)
            MenuRow(label = "고객문의", value = null, onClick = notReady)
        }
    }
}

/**
 * 프로필 — 닉네임 / `{동명} · {가입}`.
 *
 * 🔴 **닉네임을 못 받으면 `다시 시도`를 내민다.** 여기가 화면 20에서 유일하게
 *    "실패했다"를 말할 수 있는 자리다 — 지표 3칸은 기기 기록이라 오프라인에서도
 *    나오고, 활동 지역 칸은 "안 골랐다"와 "못 물었다"를 스스로 구분할 수 없다.
 *    아무 표시도 안 하면 사용자는 **자기 프로필이 지워진 것으로 읽는다.**
 *
 * ⚠️ **대표 칭호를 지웠다.** 주석 ①이 상시 노출을 요구하지만, 서버에 칭호가 없다
 *    (01 스키마에 테이블이 없고 [com.catchflower.app.data.model.RankEntry.title]도
 *    항상 null이다 — 서버가 안 준다). `우리 동네 꽃박사`를 그리면 **1위를 한 적 없는
 *    사용자에게 1위 칭호를 붙인다.** 값이 오면 이 자리에 다시 넣는다 — §9에 올렸다.
 */
@Composable
private fun ProfileBlock(
    profile: RankingUiMapper.ProfileUi,
    onRetry: () -> Unit,
    /** 예선 범위 밖 `프로필 수정`이 쓴다. */
    notReady: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CfDimen.ScreenPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(CfColor.PrimaryLight),
                contentAlignment = Alignment.Center,
            ) {
                // 닉네임을 모르면 첫 글자도 모른다. `꽃`을 박으면 **아바타만 남의 것**이 된다.
                Text(
                    profile.nickname?.take(1).orEmpty(),
                    style = CfText.Hero,
                    color = CfColor.Primary,
                )
            }
            Spacer(Modifier.width(CfDimen.Gap))
            Column(Modifier.weight(1f)) {
                if (profile.nickname != null) {
                    Text(profile.nickname, style = CfText.Section, color = CfColor.TextPrimary)
                } else {
                    // A 문서 3절 네트워크 오류 문구를 그대로 쓴다. 새 문장이 아니다.
                    Text(
                        "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                    )
                }
                // `{동명} · {가입}`. 🔴 **둘 중 하나만 있으면 있는 것만 쓴다.**
                //    `null · 2026년 3월부터 함께`나 앞에 붙은 ` · `가 남으면
                //    "정보가 사라졌다"로 읽힌다.
                val subtitle = listOfNotNull(profile.dongName, profile.joinedLabel)
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(CfDimen.GapTiny))
                    Text(
                        subtitle.joinToString(" · "),
                        style = CfText.Tiny,
                        color = CfColor.TextTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.height(CfDimen.Gap))
        if (profile.nickname != null) {
            CfSecondaryButton(text = "프로필 수정", onClick = notReady)
        } else {
            // 못 받은 프로필을 고칠 수는 없다. `프로필 수정`을 열면 빈 칸을 저장해
            // **닉네임을 지운다.**
            CfSecondaryButton(text = "다시 시도", onClick = onRetry)
        }
        Spacer(Modifier.height(CfDimen.GapLarge))
    }
}

/**
 * 활동 지역 — `변경 불가` 뒤에 **가능 날짜를 항상 병기한다**(주석 ④).
 *
 * ⚠️ 주석이 "'변경 불가'만 쓰면 문의가 몰린다"고 이유까지 적었다. 날짜를 빼지 않는다 —
 *    그래서 [changeAvailableLabel]이 `null`이면 **`변경 불가`도 안 쓴다.** 날짜 없이
 *    `변경 불가`만 남기는 것이 주석이 금지한 바로 그 화면이다.
 *
 * 🔴 **지금은 [changeAvailableLabel]이 항상 `null`이다** — 실측으로 서버가
 *    `region_changed_at`을 안 채운다([RankingUiMapper.regionChangeLabel]).
 *    그래서 이 칸은 지역명만 나오고 **변경 버튼도 안 나온다.** 버튼을 내밀면
 *    6개월 규칙을 검사할 근거가 없는 채로 변경을 허용하게 된다 — §9에 올렸다.
 *
 * ⚠️ 지역을 아직 안 골랐으면(`regionFull == null`) **A 문서 화면 17 `지역 미설정`
 *    문구를 그대로 재사용한다.** 같은 것을 설명하는 두 화면이 다른 말을 하면 사용자가
 *    다른 기능으로 읽는다(A 문서가 화면 02·17에 대해 적어 둔 이유와 같다).
 */
@Composable
private fun RegionBlock(
    regionFull: String?,
    changeAvailableLabel: String?,
    onPickRegion: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CfDimen.ScreenPadding),
    ) {
        Text("활동 지역", style = CfText.Section, color = CfColor.TextPrimary)
        Spacer(Modifier.height(CfDimen.GapSmall))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CfDimen.RadiusCard))
                .background(CfColor.Surface)
                .padding(CfDimen.Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    // 🔴 `서울특별시 마포구 연남동`을 기본값으로 박지 않는다 —
                    //    **남의 동네를 내 프로필에 붙인다.**
                    regionFull ?: "활동 지역을 정하면 순위를 볼 수 있어요",
                    style = if (regionFull != null) CfText.BodyBold else CfText.Body,
                    color = if (regionFull != null) CfColor.TextPrimary else CfColor.TextSecondary,
                )
                if (regionFull != null && changeAvailableLabel != null) {
                    Spacer(Modifier.height(CfDimen.GapTiny))
                    Text(
                        "변경 불가 · $changeAvailableLabel",
                        style = CfText.Tiny,
                        color = CfColor.TextTertiary,
                    )
                }
            }
            if (regionFull == null) {
                com.catchflower.app.ui.component.CfSmallButton(
                    text = "동네 선택하기",
                    onClick = onPickRegion,
                )
            }
        }
        Spacer(Modifier.height(CfDimen.GapLarge))
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CfDimen.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = CfText.Section, color = CfColor.TextPrimary)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) CfTextButton(text = actionLabel, onClick = onAction)
    }
}

/**
 * 메뉴 행.
 *
 * ⚠️ 오른쪽 `>` 화살표만 두지 않는다 — 아이콘 단독 금지 규칙(A 문서)에 걸린다.
 *    값(`8명`)이 있으면 값이 어포던스를 대신하고, 없으면 `보기`를 적는다.
 */
@Composable
private fun MenuRow(label: String, value: String?, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = label, onClick = onClick)
                .heightIn(min = CfDimen.MinTouch)
                .padding(horizontal = CfDimen.ScreenPadding, vertical = CfDimen.GapMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = CfText.Body, color = CfColor.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                value ?: "보기",
                style = if (value != null) CfText.BodyBold else CfText.Body,
                color = if (value != null) CfColor.TextPrimary else CfColor.TextSecondary,
            )
        }
        HorizontalDivider(color = CfColor.Border, thickness = CfDimen.BorderThin)
    }
}

/**
 * 화면 21 시즌 종료 결과 — `21_시즌결과.svg`.
 *
 * ⚠️ **시즌 종료 후 첫 실행에 1회 강제 노출되는 전면 화면**이다(주석 ①).
 *    지금은 화면 20 `지난 시즌 기록`에서 열어 확인만 한다 — 강제 노출은 시즌 종료
 *    감지와 "본 적 있음" 저장이 필요하고, 저장 계층이 아직 없다.
 *
 * ⚠️ **초기화 범위 안내를 지우지 않는다**(주석 ③). "여기서 명시하지 않으면 '내 꽃이
 *    사라졌다'는 오해가 시즌 전환 시점에 집중 발생한다"고 주석이 이유를 적었다.
 *
 * 🔴 **지난 시즌 기록은 서버에도 기기에도 없다.** 서버는 `my_season_summary`로
 *    **이번 시즌**만 세고(실측), 지난 시즌 순위·최고 순위·수여 배지를 보관하는
 *    테이블이 01 스키마에 없다. 기기 기록으로도 못 만든다 — 순위는 남의 기록을
 *    세는 것이다. 그래서 이 화면은 **아직 값을 못 채운다**(§9).
 *
 *    그런데 [result]에 기본값을 두면 안 된다. `연남동 4위 / 이웃 1,284명 중`은
 *    **한 번도 랭킹에 든 적 없는 사용자에게 4위를 받았다고 말하는 화면**이고,
 *    시즌 종료 후 강제 노출되는 전면 화면이라 **본인이 안 눌러도 뜬다.**
 *    그래서 `null`이면 결과 블록을 그리지 않고 안내만 남긴다.
 */
@Composable
fun SeasonResultScreen(
    /**
     * 지난 시즌 결과. **`null`이면 아직 기록이 없다**(첫 시즌이거나 서버가 안 준다).
     * 기본값을 주지 않는 이유는 위 KDoc에 있다.
     */
    result: SeasonResult?,
    /** 도감 누적 지표. 초기화 안내의 `모은 꽃 {N}종`에 쓴다. `null`이면 숫자를 뺀다. */
    stats: DiscoveryRules.ProfileStats?,
    /**
     * 새로 시작하는 시즌 이름(`시즌 2`). 버튼 문구에 들어간다.
     *
     * ⚠️ **`시즌 2`를 박아 두지 않는다.** 시즌은 해마다 1·2·3이 돌아오므로
     *    2026 시즌 3이 끝난 뒤에도 `시즌 2 시작하기`가 나온다.
     *    [com.catchflower.app.core.SeasonClock]이 원본이다.
     */
    newSeasonLabel: String,
    onStartNewSeason: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = rememberToaster()
    val notReady: () -> Unit = { toast(CfToast.NOT_READY) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CfDimen.ScreenPadding,
            end = CfDimen.ScreenPadding,
            bottom = CfDimen.GapLarge,
        ),
    ) {
        item {
            // 전면 화면이지만 확인용으로 열 때 나갈 길은 있어야 한다.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                CfTextButton(text = "닫기", onClick = onClose)
            }
        }

        item {
            Spacer(Modifier.height(CfDimen.Gap))
            Text(
                // `2026 시즌 1이 끝났어요` — 조사가 붙는다. `시즌 1`은 숫자로 끝나
                // 받침 판정을 못 하므로 문장을 그대로 쓴다 (A 문서 표기).
                //
                // 🔴 **기록이 없으면 어느 시즌이 끝났는지도 모른다.** 지금 시즌 번호를
                //    넣으면 `2026 시즌 2가 끝났어요`라고 써 놓고 시즌 2는 진행 중이다.
                //    A 문서 `화면 20·21에서 값이 없는 칸`의 대체 문구를 쓴다.
                result?.let { "${it.year} ${it.seasonLabel}이 끝났어요" }
                    ?: "첫 시즌을 함께 했어요",
                style = CfText.Hero,
                color = CfColor.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        if (result == null) {
            item {
                Text(
                    "다음 시즌에는 순위를 볼 수 있어요",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(CfDimen.GapLarge))
            }
        }

        result?.let { r ->
        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // 동명은 결과와 같이 온다 — 지금 프로필의 동네를 붙이면
                    // 이사한 사용자의 지난 시즌 순위에 **새 동네 이름**이 붙는다.
                    "${r.dongName} ${r.rank}위",
                    style = CfText.HeroNumber,
                    color = CfColor.Primary,
                )
                Text(
                    "이웃 ${KoreanText.thousands(r.neighborCount)}명 중",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        // 배지·칭호 수여. 4위는 순위형 미지급이라 **달성형만** 나온다(주석 ②).
        r.awardedBadge?.let { badge ->
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CfDimen.RadiusCard))
                        .background(CfColor.PrimaryLight)
                        .padding(CfDimen.Gap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(CfColor.Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("배지", style = CfText.Caption, color = CfColor.Primary)
                    }
                    Spacer(Modifier.height(CfDimen.GapSmall))
                    Text(badge, style = CfText.Section, color = CfColor.TextPrimary)
                    Text("칭호를 받았어요", style = CfText.Body, color = CfColor.TextSecondary)
                }
                Spacer(Modifier.height(CfDimen.Gap))
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CfDimen.RadiusCard))
                    .background(CfColor.Surface)
                    .padding(CfDimen.Gap),
            ) {
                Text("${r.seasonLabel} 기록", style = CfText.Section, color = CfColor.TextPrimary)
                Spacer(Modifier.height(CfDimen.GapMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CfStat(label = "모은 꽃", value = "${r.speciesCount}종")
                    CfStat(label = "발견 횟수", value = "${r.discoveryCount}회")
                }
                Spacer(Modifier.height(CfDimen.GapMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CfStat(label = "지도 공유", value = "${r.shareCount}개")
                    CfStat(label = "최고 순위", value = "${r.bestRank}위")
                }
            }
            Spacer(Modifier.height(CfDimen.Gap))
        }
        }

        item {
            // 초기화 범위 안내 (주석 ③ — 중요). 문구는 A 문서 그대로다.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CfDimen.RadiusCard))
                    .background(CfColor.PrimaryLight)
                    .padding(CfDimen.Gap),
            ) {
                Text("도감은 그대로 남아요", style = CfText.BodyBold, color = CfColor.Primary)
                Spacer(Modifier.height(CfDimen.GapTiny))
                Text(
                    // 못 셌으면 **숫자만 뺀다**(A 문서). `모은 꽃 0종과 사진은
                    // 초기화되지 않아요`는 안심시키려는 문장이 **다 잃었다는 말**이 된다.
                    stats?.let { "모은 꽃 ${it.speciesCount}종과 사진은 초기화되지 않아요." }
                        ?: "모은 꽃과 사진은 초기화되지 않아요.",
                    style = CfText.Body,
                    color = CfColor.TextPrimary,
                )
                Text(
                    "순위 점수만 새 시즌으로 리셋됩니다.",
                    style = CfText.Body,
                    color = CfColor.TextPrimary,
                )
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        item {
            com.catchflower.app.ui.component.CfPrimaryButton(
                text = "$newSeasonLabel 시작하기",
                onClick = onStartNewSeason,
            )
            Spacer(Modifier.height(CfDimen.GapSmall))
            com.catchflower.app.ui.component.CfGhostButton(
                text = "결과 공유하기",
                onClick = notReady,
            )
        }
    }
}
