package com.catchflower.app.ui.map

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지도 핀 만들기 (화면 14).
 *
 * **왜 [MapPins]에 몰아 놓고 여기서 재는가.** 핀은 **틀려도 지도에 예쁘게 찍힌다** —
 * 좌표가 조금 어긋나거나 기록 하나가 빠진 것을 화면으로는 알 수 없다. `MapViewModel`은
 * `AndroidViewModel`이라 JVM에서 생성이 안 되고, Composable은 에뮬레이터가 필요하다.
 *
 * ⚠️ 여기 초록인 것이 "지도가 나온다"는 뜻은 아니다. SDK 초기화·타일 인증·라이프사이클은
 *    **에뮬레이터에서 열어 봐야** 확인된다.
 */
class MapPinsTest {

    private var seq = 0

    private fun rec(
        lat: Double? = 37.5601,
        lng: Double? = 126.9251,
        flowerId: Int = 1,
        place: String? = "연남동 경의선숲길",
        createdAt: Long = 1_000L,
        id: String = "d${seq++}",
    ) = Discovery(
        id = id,
        userId = "u1",
        flowerId = flowerId,
        photoUrl = null,
        localPhotoPath = "$id.jpg",
        lat = lat,
        lng = lng,
        placeName = place,
        dongCode = "1144071000",
        guCode = "11440",
        visibility = Visibility.PUBLIC,
        aiConfidence = 0.9f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        note = null,
        createdAt = createdAt,
        capturedAt = createdAt,
    )

    // ── 좌표 없는 기록 ───────────────────────────────────────────

    /**
     * 🔴 **이 테스트가 이 파일의 이유다.** 좌표 없는 기록을 (0, 0)으로 채우면
     *    기니 만 앞바다에 핀이 찍히고, `fitMapPoints`로 지도를 맞추면
     *    **한국이 화면에서 사라진다.** 위치 권한 없이 찍으면 실제로 null이다.
     *
     * 빨개지는 경우: `from`이 `lat ?: 0.0` 같은 기본값을 쓰면.
     */
    @Test
    fun 좌표_없는_기록은_핀이_되지_않는다() {
        val pins = MapPins.from(listOf(rec(lat = null, lng = null)))
        assertTrue("좌표 없는 기록에 핀을 만들면 0,0에 찍힌다", pins.isEmpty())
    }

    /** 한쪽만 없는 경우도 버린다 — 위도만 있는 좌표는 좌표가 아니다. */
    @Test
    fun 위도만_있으면_핀이_되지_않는다() {
        assertTrue(MapPins.from(listOf(rec(lng = null))).isEmpty())
        assertTrue(MapPins.from(listOf(rec(lat = null))).isEmpty())
    }

    /** 좌표 있는 것과 없는 것이 섞여 있으면 **있는 것만** 남는다. */
    @Test
    fun 섞여_있으면_좌표_있는_것만_남는다() {
        val pins = MapPins.from(
            listOf(
                rec(lat = null, lng = null, flowerId = 1),
                rec(lat = 37.5601, lng = 126.9251, flowerId = 2),
            ),
        )
        assertEquals(1, pins.size)
        assertEquals(listOf(2), pins.single().discoveries.map { it.flowerId })
    }

    // ── 같은 장소 묶기 ───────────────────────────────────────────

    /**
     * 같은 자리를 두 번 찍으면 핀은 **하나**다.
     *
     * ⚠️ 안 묶으면 핀이 정확히 겹쳐서 **위의 것만 눌린다** — 아래 기록은
     *    지도에서 영구히 접근 불가다(있는데 못 여는 상태).
     */
    @Test
    fun 같은_자리_두_기록이_핀_하나가_된다() {
        val pins = MapPins.from(
            listOf(
                rec(flowerId = 1, createdAt = 100),
                rec(flowerId = 2, createdAt = 200),
            ),
        )
        assertEquals(1, pins.size)
        assertEquals(2, pins.single().recordCount)
        assertEquals(2, pins.single().speciesCount)
    }

    /**
     * ⚠️ **같은 종을 두 번 찍으면 기록은 2개, 종수는 1이다.** 둘을 같은 숫자로 세면
     *    프리뷰 카드가 `꽃 2종 · 기록 2개`라고 말한다 — 도감은 1종이라고 말하는데.
     */
    @Test
    fun 같은_종을_두_번_찍으면_종수는_하나다() {
        val pin = MapPins.from(
            listOf(rec(flowerId = 7, createdAt = 100), rec(flowerId = 7, createdAt = 200)),
        ).single()
        assertEquals(2, pin.recordCount)
        assertEquals(1, pin.speciesCount)
    }

    /**
     * 🔴 **11m 안쪽은 같은 핀, 100m는 다른 핀이다.**
     *
     * 100m로 묶으면 산책로 양쪽 끝이 한 핀이 되어 `자세히 보기`가 **다른 장소**를
     * 보여준다. 반대로 안 묶으면 같은 화단이 핀 두 개다.
     *
     * 실측 기준: 위도 0.0001° ≈ 11m, 0.001° ≈ 111m.
     */
    @Test
    fun 십여미터는_묶고_백미터는_나눈다() {
        val near = MapPins.from(
            listOf(rec(lat = 37.56010), rec(lat = 37.56012)), // 약 2m
        )
        assertEquals("2m 떨어진 두 기록은 같은 핀이어야 한다", 1, near.size)

        val far = MapPins.from(
            listOf(rec(lat = 37.5601), rec(lat = 37.5611)), // 약 111m
        )
        assertEquals("111m 떨어진 두 기록은 다른 핀이어야 한다", 2, far.size)
    }

    /**
     * 🔴 **좌표를 `Double`째로 키에 쓰면 같은 자리가 두 핀으로 갈린다.**
     *
     * GPS는 같은 자리에 서 있어도 **비트가 같은 `Double`을 두 번 주지 않는다.**
     * 아래 두 값은 약 0.1mm 차이(`1e-9°`)인데 `Double`로는 서로 다르다 —
     * `Pair<Double, Double>`를 키로 쓰면 **핀이 정확히 겹쳐 찍히고 위의 것만 눌린다.**
     * 아래 기록은 지도에서 영구히 못 여는 상태가 된다.
     *
     * 빨개지는 경우: `key()`가 반올림을 빼거나 `Pair`를 키로 쓰면.
     *
     * ⚠️ 처음에 `37.56 + 0.0001 + 0.0002` vs `37.56 + 0.0003`으로 썼는데
     *    **그 둘은 같은 `Double`이라 이 테스트가 재려던 것을 아예 재지 않았다**
     *    (다만 그때는 빨갰다 — 두 기록이 한 핀이 되는 게 맞으니까).
     *    "전제가 실제로 성립하나"를 먼저 확인해야 한다는 교훈이 또 나왔다 —
     *    그래서 `assertTrue`로 **전제를 같이 잰다.**
     */
    @Test
    fun 부동소수_오차로_핀이_갈리지_않는다() {
        val a = 37.5601
        val b = 37.5601 + 1e-9 // 약 0.1mm — 사람에게는 같은 자리다
        assertTrue("전제: 두 값이 Double로는 서로 다르다", a != b)
        assertEquals(1, MapPins.from(listOf(rec(lat = a), rec(lat = b))).size)
    }

    // ── 장소명 ───────────────────────────────────────────────────

    /**
     * 🔴 **묶음 안에 이름 있는 기록이 있으면 그것을 쓴다.** 대표(최근) 기록만 보면,
     *    처음엔 장소명이 붙었는데 나중 기록에 안 붙은 경우 **이름이 사라진다** —
     *    사용자에게는 핀이 이름을 잃은 것으로 보인다.
     */
    @Test
    fun 최근_기록에_장소명이_없으면_묶음에서_찾는다() {
        val pin = MapPins.from(
            listOf(
                rec(place = "연남동 경의선숲길", createdAt = 100),
                rec(place = null, createdAt = 200), // 최근인데 이름이 없다
            ),
        ).single()
        assertEquals("연남동 경의선숲길", pin.placeName)
    }

    /** 아무 기록에도 이름이 없으면 null — 화면은 좌표를 쓰지 않고 **줄을 뺀다.** */
    @Test
    fun 장소명이_아무_기록에도_없으면_널이다() {
        val pin = MapPins.from(listOf(rec(place = null), rec(place = null))).single()
        assertNull(pin.placeName)
    }

    /** 빈 문자열은 이름이 아니다 — 카카오가 결과 없이 응답하면 `""`가 온다. */
    @Test
    fun 빈_문자열은_장소명이_아니다() {
        val pin = MapPins.from(listOf(rec(place = "   "))).single()
        assertNull(pin.placeName)
    }

    // ── 순서 ─────────────────────────────────────────────────────

    /**
     * ⚠️ **최근 기록이 있는 핀이 앞이다.** 방금 등록한 꽃의 핀이 목록 아래로 밀리면
     *    사용자는 **등록이 안 된 줄 안다.**
     */
    @Test
    fun 최근_기록이_있는_핀이_앞에_온다() {
        val pins = MapPins.from(
            listOf(
                rec(lat = 37.5601, createdAt = 100, place = "옛날"),
                rec(lat = 37.6601, createdAt = 999, place = "방금"),
            ),
        )
        assertEquals(listOf("방금", "옛날"), pins.map { it.placeName })
    }

    /**
     * ⚠️ **핀 안의 이름 순서도 최근 순이다.** `flowerId` 순으로 두면 매번 같은 꽃이
     *    앞에 오고, 방금 찍은 꽃이 `외 N종`에 숨는다.
     */
    @Test
    fun 핀_안의_꽃도_최근_순이다() {
        val pin = MapPins.from(
            listOf(
                rec(flowerId = 5, createdAt = 100),
                rec(flowerId = 1, createdAt = 200),
            ),
        ).single()
        assertEquals(listOf(1, 5), pin.flowerIdsRecentFirst)
    }

    /** 같은 종을 여러 번 찍어도 이름은 한 번만 나온다. */
    @Test
    fun 핀_안의_꽃_목록에_중복이_없다() {
        val pin = MapPins.from(
            listOf(
                rec(flowerId = 5, createdAt = 100),
                rec(flowerId = 5, createdAt = 300),
                rec(flowerId = 1, createdAt = 200),
            ),
        ).single()
        assertEquals(listOf(5, 1), pin.flowerIdsRecentFirst)
    }

    // ── 지도 중심 ────────────────────────────────────────────────

    /**
     * 🔴 **핀이 없으면 null이고 화면은 카메라를 움직이지 않는다.** 서울시청 같은
     *    기본 좌표를 넣으면, 부산에서 찍은 사용자가 서울을 보며
     *    "내 기록이 사라졌다"고 읽는다.
     */
    @Test
    fun 핀이_없으면_중심이_없다() {
        assertNull(MapPins.center(emptyList()))
    }

    /**
     * 🔴 **평균이 아니라 가장 최근 핀이다.** 서울과 부산에 기록이 있으면 평균은
     *    **아무것도 없는 대전 위**를 보여준다.
     */
    @Test
    fun 중심은_평균이_아니라_최근_핀이다() {
        val pins = MapPins.from(
            listOf(
                rec(lat = 37.5601, lng = 126.9251, createdAt = 100), // 서울
                rec(lat = 35.1796, lng = 129.0756, createdAt = 999), // 부산
            ),
        )
        val center = MapPins.center(pins)!!
        assertEquals(35.1796, center.first, 1e-6)
        assertEquals(129.0756, center.second, 1e-6)
    }

    // ── 프리뷰 카드 문구 ─────────────────────────────────────────

    @Test
    fun 세_종_이하면_그대로_나열한다() {
        assertEquals("장미, 개망초", MapPins.flowerSummary(listOf("장미", "개망초")))
        assertEquals(
            "장미, 개망초, 금계국",
            MapPins.flowerSummary(listOf("장미", "개망초", "금계국")),
        )
    }

    /** A 문서 14번 `장미, 개망초, 금계국 외 2종`. */
    @Test
    fun 네_종_이상이면_외_N종이_붙는다() {
        assertEquals(
            "장미, 개망초, 금계국 외 2종",
            MapPins.flowerSummary(listOf("장미", "개망초", "금계국", "토끼풀", "민들레")),
        )
    }

    /**
     * ⚠️ **이름을 못 찾은 꽃은 세지 않는다.** 도감에 없는 id가 섞이면 빈 문자열이
     *    오는데, 그걸 세면 `외 1종`이 붙었는데 이름이 3개뿐인 카드가 된다.
     */
    @Test
    fun 이름_없는_꽃은_세지_않는다() {
        assertEquals("장미, 개망초", MapPins.flowerSummary(listOf("장미", "", "개망초", "  ")))
    }

    @Test
    fun 이름이_하나도_없으면_빈_문자열이다() {
        assertEquals("", MapPins.flowerSummary(emptyList()))
        assertEquals("", MapPins.flowerSummary(listOf("", " ")))
    }

    // ── 픽스처 자체 검사 ─────────────────────────────────────────

    /**
     * ⚠️ **빈 입력을 돌고 통과하지 않는지 센다.** 위 테스트들이 `from`에 실제로
     *    기록을 넘기고 있는지 — `emptyList == emptyList`로 초록이 되는 함정은
     *    이 프로젝트에서 이미 나왔다(진행 (22) ④).
     */
    @Test
    fun 픽스처가_실제로_핀을_만든다() {
        val pins = MapPins.from(listOf(rec()))
        assertEquals(1, pins.size)
        assertEquals(1, pins.single().recordCount)
    }
}
