package com.catchflower.app.data

import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.util.concurrent.TimeUnit

/**
 * 발견 기록 더미.
 *
 * **왜 필요한가**: 화면 04는 `모은 꽃 37 / 200종`, 화면 05는 `발견 횟수 4회`를
 * 보여준다. 서버(A-2 Supabase)도 로그인(화면 01)도 아직 없으므로, 이 숫자가
 * 없으면 도감 화면을 실제 모습으로 볼 수 없다.
 *
 * ⚠️ 이건 화면 검증용이고 **게임 규칙이 아니다.** 서버가 붙으면 통째로 지운다.
 *    그래서 어떤 정책 판단도 여기 넣지 않는다 — 정책 숫자는 GamePolicy에만 있다.
 * ⚠️ 시각은 "지금"에서 상대적으로 만든다. 고정 날짜를 박으면 며칠 뒤에
 *    `오늘`이 `12일 전`으로 보여서 상대 날짜 표기 버그처럼 읽힌다.
 */
object DummyDiscoveries {

    /** 와이어프레임 04와 같은 37종. 계절이 섞이도록 고른다. */
    private val COLLECTED_IDS = listOf(
        1, 2, 3, 5, 8, 11, 13, 17, 19, 22, 26, 29, 31, 34, 37, 41, 44, 47,
        52, 55, 58, 63, 66, 70, 74, 79, 83, 88, 92, 97, 103, 111, 120, 134,
        150, 168, 185,
    )

    val collectedIds: Set<Int> = COLLECTED_IDS.toSet()

    /** 화면 04 `이번 시즌 12종` — 최근에 발견한 12종. */
    val thisSeasonIds: Set<Int> = COLLECTED_IDS.take(12).toSet()

    /**
     * 화면 04 `최근 발견한 꽃` 3개.
     * 와이어프레임의 `오늘 / 어제 / 3일 전`을 그대로 만든다.
     */
    fun recent(now: Long): List<Discovery> = listOf(
        discovery(id = "d1", flowerId = COLLECTED_IDS[0], daysAgo = 0, now = now, place = "연남동 경의선숲길"),
        discovery(id = "d2", flowerId = COLLECTED_IDS[1], daysAgo = 1, now = now, place = "서울숲"),
        discovery(id = "d3", flowerId = COLLECTED_IDS[2], daysAgo = 3, now = now, place = "홍제천 산책로"),
    )

    /**
     * 화면 05 `내 발견 기록`. 종마다 개수를 다르게 만든다 —
     * 전부 4회면 `발견 횟수`·`장소` 지표가 제대로 계산되는지 알 수 없다.
     */
    fun forFlower(flowerId: Int, now: Long): List<Discovery> {
        if (flowerId !in collectedIds) return emptyList()
        val count = 1 + (flowerId % 4) // 1~4회
        val places = listOf("서울숲", "연남동 경의선숲길", "홍제천 산책로", "낙성대공원")
        return List(count) { index ->
            discovery(
                id = "d-$flowerId-$index",
                flowerId = flowerId,
                // 최근 것이 위로 오게 만든다 (내림차순 정렬은 화면이 한다)
                daysAgo = 14L * index + (flowerId % 7),
                now = now,
                place = places[(flowerId + index) % places.size],
                visibility = if (index % 2 == 0) Visibility.PUBLIC else Visibility.PRIVATE,
                isFirst = index == count - 1,
            )
        }
    }

    private fun discovery(
        id: String,
        flowerId: Int,
        daysAgo: Long,
        now: Long,
        place: String,
        visibility: Visibility = Visibility.PUBLIC,
        isFirst: Boolean = false,
    ): Discovery {
        val at = now - TimeUnit.DAYS.toMillis(daysAgo)
        return Discovery(
            id = id,
            userId = "dummy-user",
            flowerId = flowerId,
            photoUrl = null,
            localPhotoPath = null,
            lat = null,
            lng = null,
            placeName = place,
            dongCode = null,
            guCode = null,
            visibility = visibility,
            aiConfidence = 0.82f,
            aiPickedRank = 1,
            isFirstDiscovery = isFirst,
            createdAt = at,
            capturedAt = at,
        )
    }
}
