package com.catchflower.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 디자인 시스템 (B 문서 B-1).
 *
 * 타깃(40~50대 여성) 제약은 **취향이 아니라 요구사항**이다 (B 문서 공통 전제):
 * - 얇은 폰트 굵기 금지. 본문은 Regular 이상
 * - #999 이하 회색을 본문에 쓰지 않는다. 명도 대비 **4.5:1** 이상
 * - 아이콘 단독 버튼 금지. 텍스트 병기
 * - E-3: 다크 모드 미대응 — 시스템 다크에서도 라이트 고정
 */
object CfColor {
    /**
     * Primary.
     * B-1-1이 지적한 것: 200종 일러스트가 다채로우므로 **UI는 저채도**여야 충돌하지 않는다.
     * 그래서 초록 계열 저채도를 쓴다 — 꽃 색(빨강·노랑·분홍)과 겹치지 않는 유일한 계열이다.
     */
    val Primary = Color(0xFF2E6B4F)
    val PrimaryPressed = Color(0xFF24543E)
    val PrimaryLight = Color(0xFFE8F1EC)

    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF7F7F5)
    val SurfaceDark = Color(0xFF1A1A1A) // 화면 07·08 (다크 화면)

    val Border = Color(0xFFDDDDDD)
    val BorderStrong = Color(0xFF333333)

    // 텍스트 3단계. Secondary까지 본문에 쓸 수 있고, Tertiary는 보조 정보만.
    val TextPrimary = Color(0xFF1A1A1A)   // 대비 15.9:1
    val TextSecondary = Color(0xFF555555) // 대비 7.5:1 — 본문 하한
    val TextTertiary = Color(0xFF767676)  // 대비 4.5:1 — 날짜·개수만
    val TextOnDark = Color(0xFFFFFFFF)
    val TextDisabled = Color(0xFF8E8E8E)

    val Success = Color(0xFF2E7D32)
    val Warning = Color(0xFFB25E00)
    val Error = Color(0xFFC62828)

    val DisabledBackground = Color(0xFFE8E8E8)
    val GhostBackground = Color(0xFFF0F0F0)

    /** 희귀도 강조 — 화면 10 축하 연출 3단 중 '귀함'. */
    val Rare = Color(0xFF7B3F9E)
}

@Composable
fun CatchFlowerTheme(content: @Composable () -> Unit) {
    // 다크 스킴을 주지 않는다 (E-3). 시스템 다크에서도 이 스킴이 쓰인다.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CfColor.Primary,
            onPrimary = Color.White,
            primaryContainer = CfColor.PrimaryLight,
            onPrimaryContainer = CfColor.Primary,
            background = CfColor.Background,
            onBackground = CfColor.TextPrimary,
            surface = CfColor.Background,
            onSurface = CfColor.TextPrimary,
            surfaceVariant = CfColor.Surface,
            onSurfaceVariant = CfColor.TextSecondary,
            outline = CfColor.Border,
            error = CfColor.Error,
        ),
        typography = CfTypography,
        content = content,
    )
}
