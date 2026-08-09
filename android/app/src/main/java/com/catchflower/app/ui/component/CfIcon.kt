package com.catchflower.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.catchflower.app.R

/**
 * 납품 아이콘 한 개. **[androidx.compose.material3.Icon]을 쓰지 않는다.**
 *
 * ## 왜 `Icon`이 아닌가
 *
 * 🔴 [androidx.compose.material3.Icon]은 그림을 **`tint` 한 색으로 덮어 그린다.**
 *    Material 아이콘은 단색 벡터라 그게 맞지만, 납품 아이콘은 **컬러**다 —
 *    분홍 꽃잎·노란 꽃심·초록 잎이 있는 그림이고 `tint`를 주면 **초록 실루엣**이 된다.
 *    (`Icon`에 `tint = Color.Unspecified`로 끌 수는 있지만, 그러면 "왜 Icon을 쓰면서
 *    tint를 끄는가"가 호출부마다 반복 설명거리가 된다.)
 *
 * ⚠️ **B 문서는 "라인 아이콘 1.6~1.8px"을 요청했는데 온 것은 컬러 면 아이콘이다.**
 *    발주와 납품이 다르지만 납품 쪽을 쓴다 — 44종이 서로 통일돼 있고, 앱아이콘과도
 *    같은 계열이다. 이 불일치는 `B_화면_UI디자인_업무목록.md` 1-7행에 적혀 있다.
 *
 * ## 선택/비선택을 어떻게 구분하나
 *
 * 🔴 **색을 바꿀 수 없으므로 [desaturate]로 구분한다.** 컬러 아이콘에서 선택 상태를
 *    나타내는 흔한 방법 세 가지를 실제로 그려 보고 골랐다(2026-08-09):
 *
 * | 방법 | 결과 |
 * |---|---|
 * | 컬러 그대로 | 네 탭이 **전부 똑같이 보인다** — 어느 탭에 있는지 알 수 없다 |
 * | 알파 0.55 | 노란 트로피(`랭킹`)가 흰 배경에서 **거의 사라진다** |
 * | 회색조 | 넷 다 형태가 남고 선택 탭만 색을 갖는다 ← **채택** |
 *
 * ⚠️ 알파를 낮추는 방식은 **아이콘 색에 따라 결과가 다르다** — 진한 초록 `도감`은
 *    멀쩡하고 노란 `랭킹`만 사라진다. 한 아이콘으로 확인하면 통과한다.
 *
 * @param desaturate 회색조로 그린다(비선택 상태).
 */
@Composable
fun CfIcon(
    @DrawableRes id: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    desaturate: Boolean = false,
) {
    Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        // 채도 0 = 회색조. 밝기는 유지되므로 형태가 남는다(알파를 낮추면 형태가 죽는다).
        colorFilter = if (desaturate) GrayscaleFilter else null,
        modifier = modifier.size(size),
    )
}

/**
 * 채도 0 필터. **한 번만 만든다** — [ColorMatrix]는 `FloatArray`를 들고 있고,
 * 컴포저블 안에서 만들면 리컴포지션마다 20칸 배열이 새로 생긴다(하단 내비는
 * 탭을 옮길 때마다 4개가 다시 그려진다).
 */
private val GrayscaleFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * 하단 내비 탭 아이콘 리소스. **[NavTab]과 짝이 맞는지 컴파일러가 보게 한다.**
 *
 * ⚠️ `when`을 `else`로 닫지 않는다. 탭이 하나 늘면 **여기서 컴파일이 깨져야 한다** —
 *    `else -> ic_tab_dex` 같은 기본값을 두면 새 탭이 도감 아이콘을 달고 조용히 나온다.
 */
@get:DrawableRes
val NavTab.iconRes: Int
    get() = when (this) {
        NavTab.DEX -> R.drawable.ic_tab_dex
        NavTab.MAP -> R.drawable.ic_tab_map
        NavTab.RANKING -> R.drawable.ic_tab_ranking
        NavTab.MY -> R.drawable.ic_tab_my
        // 셔터는 초록 원 안에 그리고 크기도 다르다. 이 표로 가져오지 않는다.
        NavTab.CAPTURE -> R.drawable.ic_capture
    }
