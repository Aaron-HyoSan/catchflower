package com.catchflower.app.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 15 `길찾기`가 여는 주소([MapLinks]).
 *
 * ## 어떤 경우에 빨개지나
 *
 * - 좌표를 기기 지역 설정으로 찍으면 (`37,566535` → 카카오 링크가 통째로 어긋난다).
 *   🔴 **한국 기기만 보면 영원히 안 보이는 결함이라** 여기서 기본 지역을 독일로
 *   바꿔 두고 잰다. 이 검사가 없으면 `Locale.US`를 지워도 초록이다.
 * - 앱 링크가 아니라 웹 링크를 먼저 열면 (카카오맵이 깔려 있어도 브라우저가 뜬다).
 * - 목적지 이름을 URL 인코딩하지 않거나 쉼표를 남기면 (좌표 자리가 밀려서
 *   **엉뚱한 곳으로 길을 안내한다** — 주소는 열리므로 아무도 실패로 안 본다).
 */
class MapLinksTest {

    private val lat = 37.566535
    private val lng = 126.977969

    /**
     * ⚠️ **기본 지역을 바꿔서 잰다.** `String.format("%.6f", …)`은 지역을 따라
     *    소수점을 쉼표로 찍는다. 한국·미국 기기에서는 절대 안 나타난다.
     */
    private fun <T> inGermany(block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            return block()
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun 앱_링크는_현재위치에서_걸어가는_경로다() {
        val url = inGermany { MapLinks.kakaoApp(lat, lng) }
        assertEquals("kakaomap://route?ep=37.566535,126.977969&by=FOOT", url)
    }

    @Test
    fun 소수점을_지역설정으로_찍지_않는다() {
        val app = inGermany { MapLinks.kakaoApp(lat, lng) }
        val web = inGermany { MapLinks.kakaoWeb(lat, lng, "연남동 경의선숲길") }
        // 쉼표는 좌표를 나누는 구분자다 — 소수점이 쉼표가 되면 좌표가 4개로 읽힌다.
        assertEquals("앱 링크 좌표에 쉼표가 2개 이상이다: $app", 1, app.count { it == ',' })
        assertTrue("웹 링크에 `37,566535`가 들어갔다: $web", "37,566" !in web)
    }

    @Test
    fun 웹_링크는_이름을_인코딩하고_좌표를_뒤에_붙인다() {
        val url = MapLinks.kakaoWeb(lat, lng, "연남동 경의선숲길")
        assertTrue("카카오 길찾기 웹 형식이 아니다: $url", url.startsWith("https://map.kakao.com/link/to/"))
        // 한글·공백이 그대로 있으면 일부 브라우저가 주소를 공백에서 자른다.
        assertTrue("이름을 URL 인코딩하지 않았다: $url", "연남동" !in url && " " !in url)
        assertTrue("좌표가 주소 끝에 없다: $url", url.endsWith(",37.566535,126.977969"))
    }

    @Test
    fun 이름의_쉼표와_슬래시를_뺀다() {
        // `서울, 연남동`처럼 쉼표가 있으면 좌표 자리가 밀린다. 주소는 열리고
        // **다른 좌표로 길을 안내한다** — 실패로 안 보이는 종류의 결함이다.
        val url = MapLinks.kakaoWeb(lat, lng, "서울, 연남동/경의선")
        assertEquals("좌표 앞에 쉼표가 더 있다: $url", 2, url.substringAfter("/link/to/").count { it == ',' })
        assertTrue("이름의 슬래시가 경로를 늘렸다: $url", url.substringAfter("/link/to/").none { it == '/' })
    }

    @Test
    fun 이름이_비면_대체_이름을_쓴다() {
        // 빈 이름은 `…/link/to/,37.5,126.9`가 되어 형식이 깨진다.
        val url = MapLinks.kakaoWeb(lat, lng, "   ")
        assertTrue("빈 이름이 그대로 들어갔다: $url", !url.contains("/to/,"))
    }

    @Test
    fun 앱_링크를_먼저_시도한다() {
        val chain = MapLinks.routeChain(lat, lng, "연남동")
        assertEquals("시도할 주소가 2개가 아니다: $chain", 2, chain.size)
        assertTrue(
            "웹 링크가 먼저다 — 카카오맵이 깔려 있어도 브라우저가 열린다: $chain",
            chain[0].startsWith("kakaomap://"),
        )
        assertTrue("두 번째가 웹 길찾기가 아니다: $chain", chain[1].startsWith("https://map.kakao.com/"))
    }
}
