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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.DummyRanking
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.CfStat
import com.catchflower.app.ui.component.CfTextButton
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
    friendCount: Int,
    onOpenFriends: () -> Unit,
    onOpenLastSeason: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badges = remember { DummyRanking.badges() }

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
                CfTextButton(text = "설정", onClick = { /* TODO(화면 22): 설정 */ })
            }
        }

        item { ProfileBlock() }

        item {
            // 지표 3칸 — 종수(경쟁 축) / 발견 횟수(활동량) / 공유 수(기여도).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CfDimen.ScreenPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CfStat(label = "모은 꽃", value = "${DummyRanking.TOTAL_SPECIES}종")
                CfStat(label = "총 발견", value = "${DummyRanking.TOTAL_DISCOVERIES}회")
                CfStat(label = "공유", value = "${DummyRanking.TOTAL_SHARES}개")
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        item {
            SectionHeader(
                title = "내 배지",
                // `3개 · 전체 보기` — 개수를 버튼 안에 넣는다 (A 문서 표기).
                actionLabel = "${badges.size}개 · 전체 보기",
                onAction = { /* TODO(B-9 확정 후): 배지 전체 목록 */ },
            )
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CfDimen.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
            ) {
                items(badges, key = { it.name }) { badge -> BadgeCard(badge) }
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        item { RegionBlock() }

        item {
            // 메뉴 — 친구 관리·지난 시즌만 실제로 연결되고 나머지는 아직 화면이 없다.
            MenuRow(
                label = "친구 관리",
                value = "${friendCount}명",
                onClick = onOpenFriends,
            )
            MenuRow(
                label = "내가 공유한 꽃",
                value = "${DummyRanking.TOTAL_SHARES}개",
                onClick = { /* TODO(화면 15·16 이후): 내 공유 목록 */ },
            )
            MenuRow(label = "지난 시즌 기록", value = null, onClick = onOpenLastSeason)
            MenuRow(label = "알림 설정", value = null, onClick = { /* TODO(화면 22) */ })
            MenuRow(label = "고객문의", value = null, onClick = { /* TODO(화면 22) */ })
        }
    }
}

/**
 * 프로필 — 닉네임 / 대표 칭호 / `연남동 · 2026년 3월부터 함께`.
 *
 * 칭호는 프로필 아이덴티티라 **상시 노출**한다(주석 ①). 랭킹 행에도 같이 나간다.
 */
@Composable
private fun ProfileBlock() {
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
                Text(
                    DummyRanking.MY_NICKNAME.take(1),
                    style = CfText.Hero,
                    color = CfColor.Primary,
                )
            }
            Spacer(Modifier.width(CfDimen.Gap))
            Column(Modifier.weight(1f)) {
                Text(
                    DummyRanking.MY_NICKNAME,
                    style = CfText.Section,
                    color = CfColor.TextPrimary,
                )
                Spacer(Modifier.height(CfDimen.GapTiny))
                // 칭호는 배경을 줘서 닉네임과 구분한다 — 둘 다 텍스트만이면 한 덩어리로 읽힌다.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(CfDimen.RadiusChip))
                        .background(CfColor.PrimaryLight)
                        .padding(horizontal = CfDimen.GapMedium, vertical = CfDimen.GapTiny),
                ) {
                    Text(DummyRanking.MY_TITLE, style = CfText.Caption, color = CfColor.Primary)
                }
                Spacer(Modifier.height(CfDimen.GapTiny))
                Text(
                    "${DummyRanking.MY_DONG} · ${DummyRanking.JOINED_LABEL}",
                    style = CfText.Tiny,
                    color = CfColor.TextTertiary,
                )
            }
        }
        Spacer(Modifier.height(CfDimen.Gap))
        CfSecondaryButton(text = "프로필 수정", onClick = { /* TODO(화면 22 이후) */ })
        Spacer(Modifier.height(CfDimen.GapLarge))
    }
}

/**
 * 활동 지역 — `변경 불가` 뒤에 **가능 날짜를 항상 병기한다**(주석 ④).
 *
 * ⚠️ 주석이 "'변경 불가'만 쓰면 문의가 몰린다"고 이유까지 적었다. 날짜를 빼지 않는다.
 *    가능해지면 이 행이 `변경하기` 버튼으로 바뀐다 — 그래서 [changeable] 분기를 남겨 둔다.
 */
@Composable
private fun RegionBlock(changeable: Boolean = false) {
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
                    DummyRanking.MY_REGION_FULL,
                    style = CfText.BodyBold,
                    color = CfColor.TextPrimary,
                )
                if (!changeable) {
                    Spacer(Modifier.height(CfDimen.GapTiny))
                    Text(
                        "변경 불가 · ${DummyRanking.REGION_CHANGE_AVAILABLE}",
                        style = CfText.Tiny,
                        color = CfColor.TextTertiary,
                    )
                }
            }
            if (changeable) {
                com.catchflower.app.ui.component.CfSmallButton(
                    text = "변경하기",
                    onClick = { /* TODO(화면 02 재사용) */ },
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
 * 배지 카드.
 *
 * 배지 아트가 없다. 와이어프레임의 `배지` 플레이스홀더 자리에 **꽃 모양 대신 원**을
 * 두고 이름을 밑에 적는다 — B-9에서 배지 종류가 확정되면 아트로 바꾼다.
 */
@Composable
private fun BadgeCard(badge: DummyRanking.Badge) {
    Column(
        Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(vertical = CfDimen.GapMedium, horizontal = CfDimen.GapSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(CfColor.PrimaryLight),
            contentAlignment = Alignment.Center,
        ) {
            Text("배지", style = CfText.Tiny, color = CfColor.Primary)
        }
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            badge.name,
            style = CfText.Caption,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(badge.periodLabel, style = CfText.Tiny, color = CfColor.TextTertiary)
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
 */
@Composable
fun SeasonResultScreen(
    onStartNewSeason: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = DummyRanking.lastSeason

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
                "2026 ${result.seasonLabel}이 끝났어요",
                style = CfText.Hero,
                color = CfColor.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${DummyRanking.MY_DONG} ${result.rank}위",
                    style = CfText.HeroNumber,
                    color = CfColor.Primary,
                )
                Text(
                    "이웃 ${KoreanText.thousands(result.neighborCount)}명 중",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )
            }
            Spacer(Modifier.height(CfDimen.GapLarge))
        }

        // 배지·칭호 수여. 4위는 순위형 미지급이라 **달성형만** 나온다(주석 ②).
        result.awardedBadge?.let { badge ->
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
                Text(
                    "${result.seasonLabel} 기록",
                    style = CfText.Section,
                    color = CfColor.TextPrimary,
                )
                Spacer(Modifier.height(CfDimen.GapMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CfStat(label = "모은 꽃", value = "${result.speciesCount}종")
                    CfStat(label = "발견 횟수", value = "${result.discoveryCount}회")
                }
                Spacer(Modifier.height(CfDimen.GapMedium))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CfStat(label = "지도 공유", value = "${result.shareCount}개")
                    CfStat(label = "최고 순위", value = "${result.bestRank}위")
                }
            }
            Spacer(Modifier.height(CfDimen.Gap))
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
                    "모은 꽃 ${DummyRanking.TOTAL_SPECIES}종과 사진은 초기화되지 않아요.",
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
                text = "시즌 2 시작하기",
                onClick = onStartNewSeason,
            )
            Spacer(Modifier.height(CfDimen.GapSmall))
            com.catchflower.app.ui.component.CfGhostButton(
                text = "결과 공유하기",
                onClick = { /* TODO(주석 ④): 이미지 카드 저장·외부 공유 */ },
            )
        }
    }
}
