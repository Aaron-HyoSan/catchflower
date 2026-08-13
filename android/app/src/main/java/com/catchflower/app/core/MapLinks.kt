package com.catchflower.app.core

import java.net.URLEncoder
import java.util.Locale

/**
 * 화면 15 `길찾기`가 여는 주소들. **순서가 있는 목록**이고 앞에서부터 시도한다.
 *
 * ## 왜 두 개인가
 *
 * 첫째는 카카오맵 앱을 바로 열고, 둘째는 같은 길찾기를 **웹**으로 연다(카카오맵이
 * 설치돼 있으면 앱 링크로 다시 앱이 열리고, 없으면 브라우저에 뜬다).
 * 둘 다 실패하면 [com.catchflower.app.ui.component.CfToast.MAP_NO_APP]다.
 *
 * 🔴 **`geo:`를 넣지 않았다.** `geo:`는 그 지점을 **보여 주기만** 하고 길을 알려 주지
 *    않는다 — 버튼이 `길찾기`라고 적혀 있는데 핀만 뜨면, 누른 사람은 앱이 잘못 열린
 *    줄 안다. 죽은 버튼을 없애면서 **비슷하게 생긴 다른 동작**을 채워 넣는 것은
 *    `배지 3개` 더미를 지운 이유와 같은 종류의 거짓이다.
 *
 * ## 왜 좌표를 [Locale.US]로 찍는가
 *
 * ⚠️ `String.format("%.6f", …)`는 **기기 지역 설정을 따른다.** 소수점이 쉼표인
 *    지역(독일·프랑스 등)에서는 `37,566` 이 되고, 카카오 링크는 좌표를 쉼표로
 *    나누므로 **주소가 통째로 어긋난다.** 한국 기기만 보면 영원히 안 보이는 결함이다.
 */
object MapLinks {

    /**
     * 카카오맵 앱 길찾기. `sp`(출발지)를 안 주면 **현재 위치**에서 시작한다.
     *
     * `by=FOOT` — 동네 꽃까지 걸어가는 거리라 자동차 경로가 아니다.
     */
    fun kakaoApp(lat: Double, lng: Double): String =
        "kakaomap://route?ep=${coord(lat)},${coord(lng)}&by=FOOT"

    /**
     * 카카오맵 웹 길찾기. 형식은 `…/link/to/{이름},{위도},{경도}`.
     *
     * ⚠️ **이름에서 쉼표를 뺀다.** 경로를 쉼표로 나누는 형식이라 이름에 쉼표가 있으면
     *    좌표 자리가 밀린다. 그리고 한글 이름은 URL 인코딩해야 한다 —
     *    안 하면 일부 브라우저가 주소를 잘라서 **엉뚱한 좌표로 길을 안내한다.**
     */
    fun kakaoWeb(lat: Double, lng: Double, label: String): String {
        val name = label.replace(",", " ").replace("/", " ").trim().ifEmpty { FALLBACK_LABEL }
        // ⚠️ `URLEncoder`는 폼 인코딩이라 **공백을 `+`로 바꾼다.** 경로(path)에서는
        //    `+`가 공백이 아니라 글자 `+`라서 목적지 이름이 `연남동+경의선숲길`로 뜬다.
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "https://map.kakao.com/link/to/$encoded,${coord(lat)},${coord(lng)}"
    }

    /**
     * 시도할 순서. 부르는 쪽은 **앞에서부터 하나씩 열어 보고 처음 성공한 것에서 멈춘다.**
     *
     * @param label 목적지 이름. 화면 15가 **제목에 쓰는 그 값**을 그대로 넘긴다 —
     *   여기서 이름을 새로 지으면 사용자가 보는 화면 제목과 지도 앱의 목적지 이름이
     *   달라진다.
     */
    fun routeChain(lat: Double, lng: Double, label: String): List<String> =
        listOf(kakaoApp(lat, lng), kakaoWeb(lat, lng, label))

    /**
     * 좌표 6자리 ≈ 0.11m. 지도 핀이 4자리(약 11m)로 묶이므로([com.catchflower.app.ui.map.MapPins])
     * 이보다 정밀할 필요가 없고, 더 짧게 자르면 길찾기 목적지가 옆 건물로 간다.
     */
    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)

    /** 장소명을 못 받았을 때 웹 링크에 넣을 이름. 빈 이름은 주소 형식을 깨뜨린다. */
    private const val FALLBACK_LABEL = "내 발견"
}
