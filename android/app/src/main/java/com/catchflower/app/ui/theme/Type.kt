package com.catchflower.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 텍스트 스케일. A 문서 1절 표를 그대로 옮긴 것이다.
 *
 * | 역할 | 크기 | 굵기 |
 * |---|---|---|
 * | 화면 제목 | 19~21pt | Bold |
 * | 축하/질문 대상 | 24~32pt | Bold |
 * | 섹션 제목 | 13.5~14.5pt | Bold |
 * | 본문 | 12~13.5pt | Regular |
 * | 보조 설명 | 10.5~11.5pt | Regular |
 * | 최소 크기 | **10.5pt** | |
 *
 * ⚠️ pt 값을 sp로 그대로 쓴다. Android sp는 시스템 글꼴 배율을 따르므로
 *    사용자가 글씨를 키우면 같이 커진다 — 노안 타깃에서는 그게 맞는 동작이다.
 * ⚠️ Light·Thin 굵기는 쓰지 않는다 (A 문서 공통 전제). Normal 이상만 둔다.
 */
object CfText {

    /** 헤더 제목 — `내 꽃 도감`, `도감 필터`. */
    val ScreenTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)

    /** 축하·질문 대상 — `장미`, `연남동 4위`. 화면당 1개. */
    val Hero = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)

    /** 현황 카드의 큰 숫자 — `37`, `0`. */
    val HeroNumber = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)

    /** 섹션 제목 — `최근 발견한 꽃`, `전체 200종`. */
    val Section = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)

    /** 본문. 하한 12sp를 지킨다. */
    val Body = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)

    /** 본문 강조 — 굵게가 필요한 본문. */
    val BodyBold = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)

    /** 보조 설명 (회색 부연). */
    val Caption = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp)

    /**
     * 최소 크기. **날짜·개수 등 보조 정보에만** 쓴다 (A 문서 주석).
     * 설명문에 쓰면 안 된다.
     */
    val Tiny = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp)

    /** 버튼 라벨 — Primary/Secondary 공통 15~16pt Bold. */
    val Button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)

    /** Ghost 버튼 — 15pt Medium. */
    val ButtonGhost = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)

    /** Small 버튼 — 카드 내부 12~13.5pt. */
    val ButtonSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)

    /** Text 버튼 — `전체 보기`, `필터`. 밑줄 없음. */
    val ButtonText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)

    /** 칩 라벨. */
    val Chip = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)

    /** 하단 내비 라벨. 아이콘 단독 금지이므로 항상 함께 나온다. */
    val NavLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
}

/**
 * Material3 Typography 매핑.
 * M3 컴포넌트(Text 기본값 등)가 집어가는 슬롯도 우리 스케일로 덮는다 —
 * 안 덮으면 M3 기본 bodyLarge(16sp)·labelSmall(11sp Light 느낌)이 섞여 나온다.
 */
internal val CfTypography = Typography(
    headlineLarge = CfText.Hero,
    headlineMedium = CfText.Hero,
    titleLarge = CfText.ScreenTitle,
    titleMedium = CfText.Section,
    titleSmall = CfText.Section,
    bodyLarge = CfText.Body,
    bodyMedium = CfText.Body,
    bodySmall = CfText.Caption,
    labelLarge = CfText.Button,
    labelMedium = CfText.Chip,
    labelSmall = CfText.Tiny,
)

/** 한글은 기본 trim이 들어가면 줄간이 좁아 보인다. 필요할 때 붙인다. */
internal val KoreanLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
