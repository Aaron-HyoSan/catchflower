package com.catchflower.app.ui.map

import com.catchflower.app.data.model.Discovery

/**
 * 지도에 찍을 핀 하나. 화면 14의 프리뷰 카드가 읽는 값도 여기 있다.
 *
 * ⚠️ **[Discovery]를 그대로 지도에 넘기지 않는다.** 좌표가 `null`인 기록이 섞여 있고
 *    (위치 권한 없이 찍으면 그렇다), 같은 장소에서 여러 번 찍은 기록은 핀 하나로
 *    합쳐야 한다. 그 판단이 [MapPins]에 있고 **JVM 테스트가 그것을 잰다** —
 *    지도 화면 안에 두면 에뮬레이터로만 확인되는 코드가 된다.
 */
data class MapPin(
    /** 라벨 태그로 넣는 값. 같은 좌표 묶음의 대표 기록 id다. */
    val id: String,
    val lat: Double,
    val lng: Double,
    /** `서울숲`. 장소명이 안 붙은 기록은 null이고 화면은 좌표 대신 **줄을 뺀다.** */
    val placeName: String?,
    /** 이 핀에 묶인 기록들. **비어 있을 수 없다** — 묶는 쪽이 보장한다. */
    val discoveries: List<Discovery>,
) {
    /** 프리뷰 카드 `꽃 5종 · 기록 12개`의 앞 숫자. 같은 종을 여러 번 찍어도 1이다. */
    val speciesCount: Int get() = discoveries.mapTo(HashSet()) { it.flowerId }.size

    /** 같은 카드의 뒤 숫자. */
    val recordCount: Int get() = discoveries.size

    /**
     * 카드에 이름을 쓸 순서. **최근에 찍은 것부터**다.
     *
     * ⚠️ `flowerId` 순서로 두면 매번 같은 꽃이 앞에 오고, 방금 등록한 꽃이
     *    `외 2종`에 숨는다 — 사용자는 **등록이 안 된 줄 안다.**
     */
    val flowerIdsRecentFirst: List<Int>
        get() = discoveries.sortedByDescending { it.createdAt }
            .map { it.flowerId }
            .distinct()
}

/**
 * 발견 기록 → 지도 핀.
 *
 * 🔴 **이 판단들을 [MapViewModel]이나 Composable로 옮기지 마라.** `AndroidViewModel`은
 *    JVM에서 **생성조차 안 되고**(화면 02·17에서 겪었다) Composable은 에뮬레이터가
 *    필요하다. 핀은 **틀려도 지도에 예쁘게 찍힌다** — 좌표가 조금 어긋나거나 기록이
 *    하나 빠진 것을 눈으로는 알 수 없다. 그래서 여기 있어야 한다.
 */
object MapPins {

    /**
     * 같은 장소로 묶는 반올림 자릿수.
     *
     * 소수 4자리 = 약 11m다. [com.catchflower.app.core.GamePolicy.SAME_PLACE_RADIUS_METERS]
     * (100m)와 **일부러 다르다** — 그건 "같은 꽃을 같은 장소에서 하루 한 번"이라는
     * 어뷰징 규칙이고, 이건 화면에 핀이 겹쳐 보이지 않게 하는 표시 문제다.
     *
     * ⚠️ 100m로 묶으면 산책로 양쪽 끝이 한 핀이 되어 **`자세히 보기`가 다른 장소를
     *    보여준다.** 반대로 6자리(약 0.1m)면 같은 화단을 두 번 찍어도 핀이 두 개다.
     */
    const val GROUP_DECIMALS = 4

    /**
     * 좌표가 있는 기록만 핀으로 만든다.
     *
     * 🔴 **좌표 없는 기록을 (0, 0)으로 채우면 안 된다.** 기니 만 앞바다에 핀이
     *    찍히고, 지도를 `fitMapPoints`로 맞추면 **한국이 화면에서 사라진다.**
     *    위치 권한 없이 찍은 기록은 실제로 좌표가 null이다(그게 정상 경로다).
     *
     * ⚠️ **`visibility`로 걸러내지 않는다.** 이건 **내 기록만** 그리는 지도다
     *    (예선 범위: 남의 기록을 읽는 서버 함수가 없다). 내가 `비공개`로 저장한 꽃도
     *    내 지도에는 보여야 한다 — 안 보이면 "등록이 안 됐다"로 읽힌다.
     *    다른 사람 기록이 들어오는 순간 **그때 필터를 넣는다.**
     *
     * @return 최근 기록이 있는 핀이 앞에 온다. 화면 14의 목록 순서가 그것이다.
     */
    fun from(discoveries: List<Discovery>): List<MapPin> {
        val located = discoveries.filter { it.lat != null && it.lng != null }
        if (located.isEmpty()) return emptyList()

        val groups = located.groupBy { key(it.lat!!, it.lng!!) }

        return groups.values.map { group ->
            // 대표는 **가장 최근** 기록이다. 장소명·좌표를 여기서 가져온다 —
            // 처음 찍었을 때는 장소명이 안 붙었는데 나중에 붙은 경우가 있다.
            val newest = group.maxByOrNull { it.createdAt }!!
            MapPin(
                id = newest.id,
                lat = newest.lat!!,
                lng = newest.lng!!,
                // ⚠️ **대표 기록의 장소명이 null이면 묶음 안에서 찾는다.** 같은 자리인데
                //    이름이 있는 기록과 없는 기록이 섞이면, 최근 것이 없는 쪽일 때
                //    이름이 사라진다.
                placeName = group.asSequence()
                    .sortedByDescending { it.createdAt }
                    .mapNotNull { it.placeName?.takeIf(String::isNotBlank) }
                    .firstOrNull(),
                discoveries = group.sortedByDescending { it.createdAt },
            )
        }.sortedByDescending { pin -> pin.discoveries.first().createdAt }
    }

    /**
     * 지도를 처음 열 때 맞출 중심.
     *
     * @return 핀이 없으면 null — 화면은 **카메라를 움직이지 않는다.**
     *   🔴 서울시청 같은 기본 좌표로 채우면, 부산에서 꽃을 찍은 사용자가
     *      "내 기록이 사라졌다"고 읽는다(지도는 서울을 보여주고 핀은 화면 밖에 있다).
     */
    fun center(pins: List<MapPin>): Pair<Double, Double>? {
        if (pins.isEmpty()) return null
        // 평균이 아니라 **가장 최근 핀**이다. 평균은 서울과 부산에 기록이 있으면
        // 아무것도 없는 대전 위를 보여준다.
        val newest = pins.first()
        return newest.lat to newest.lng
    }

    /**
     * 프리뷰 카드 둘째 줄 `장미, 개망초, 금계국 외 2종` (A 문서 14번).
     *
     * @param names 꽃 이름. 도감에서 찾지 못한 id는 **호출처가 빼고 넘긴다** —
     *   여기서 빈 문자열을 걸러 주지만, 이름 없는 꽃을 `외 N종`으로 세면 숫자가 틀린다.
     */
    fun flowerSummary(names: List<String>, maxShown: Int = 3): String {
        val clean = names.filter { it.isNotBlank() }
        if (clean.isEmpty()) return ""
        if (clean.size <= maxShown) return clean.joinToString(", ")
        val shown = clean.take(maxShown).joinToString(", ")
        return "$shown 외 ${clean.size - maxShown}종"
    }

    /**
     * ⚠️ **문자열로 만든다.** `Pair<Double, Double>`를 키로 쓰면 반올림 오차가 남은
     *    `Double`이 서로 다른 키가 되어 같은 자리가 두 핀으로 갈린다
     *    (`0.1 + 0.2 != 0.3`이 좌표에서도 똑같이 일어난다).
     */
    private fun key(lat: Double, lng: Double): String {
        val f = "%.${GROUP_DECIMALS}f"
        return String.format(f, lat) + "," + String.format(f, lng)
    }
}
