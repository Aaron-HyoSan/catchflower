package com.catchflower.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 치수. B 문서 B-1-2·B-1-3의 수치를 그대로 쓴다.
 *
 * 기준 해상도 375×812 (B 문서). Compose dp는 375pt 기준 논리 픽셀과 1:1이므로
 * 와이어프레임의 px 값을 그대로 dp로 옮긴다.
 */
object CfDimen {
    /** 화면 좌우 여백. 와이어프레임 375 기준 20. */
    val ScreenPadding = 20.dp

    val GapTiny = 4.dp
    val GapSmall = 8.dp
    val GapMedium = 12.dp
    val Gap = 16.dp
    val GapLarge = 24.dp
    val GapSection = 28.dp

    /** 버튼 높이 — A 문서 1절. */
    val ButtonPrimary = 56.dp
    val ButtonSecondary = 56.dp
    val ButtonGhost = 50.dp
    val ButtonSmall = 38.dp

    /** 최소 터치 영역 44×44 (A 문서). 아이콘 단독 버튼도 지킨다. */
    val MinTouch = 44.dp

    /** 라운드. Primary는 "라운드 완전(28px)" = 높이의 절반. */
    val RadiusFull = 28.dp
    val RadiusCard = 14.dp
    val RadiusChip = 18.dp
    val RadiusSheet = 20.dp

    val BorderThin = 1.dp
    /** Secondary 버튼 테두리 1.5px (A 문서). */
    val BorderButton = 1.5.dp

    /** 하단 내비 — B 문서: 높이 64 + 세이프에어리어 24. */
    val BottomNavHeight = 64.dp
    /** 중앙 셔터 지름 68 (B 문서). 내비 위로 솟는다. */
    val ShutterDiameter = 68.dp

    /** 도감 그리드 — 3열. 셀 일러스트 렌더 크기는 C 발주서의 100 계열. */
    val DexCellIllust = 84.dp
    /** 최근 발견 가로 스크롤 카드. */
    val RecentCard = 96.dp
    /** 도감 상세 대표 일러스트 — C 발주서 132 계열. */
    val DetailIllust = 132.dp
    /** 칩 높이. 터치 44에 못 미치므로 세로 패딩으로 보완한다. */
    val ChipHeight = 38.dp

    val ProgressBarHeight = 8.dp
}
