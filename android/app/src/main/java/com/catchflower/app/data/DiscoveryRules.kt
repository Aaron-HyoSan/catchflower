package com.catchflower.app.data

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.model.Discovery
import java.util.Calendar
import java.util.TimeZone

/**
 * 발견 기록에서 화면 숫자와 B-5 판정을 뽑는다. **안드로이드 의존이 없다.**
 *
 * **왜 ViewModel에 두지 않는가.** 이 계산들은 **틀려도 화면에는 예쁘게 나온다** —
 * `모은 꽃 37 / 200종`, `발견 횟수 4회`, `12번째 꽃`. [SeasonClock]과 같은 이유로
 * 순수 함수로 떼어내 JVM 테스트로 고정한다. 에뮬레이터에서 눈으로 볼 수 없는 종류다.
 *
 * ⚠️ 실제로 랭킹에서 **정렬이 틀려도 화면은 정상으로 보였다**(진행 (17)).
 *    같은 함정을 도감 숫자에서 반복하지 않으려고 만든 파일이다.
 */
object DiscoveryRules {

    /** 도감에 채워진 종 id. 같은 종을 여러 번 찍어도 1종이다. */
    fun collectedIds(discoveries: List<Discovery>): Set<Int> =
        discoveries.mapTo(HashSet()) { it.flowerId }

    /** 화면 05 `발견 횟수 4회` — 한 종을 몇 번 찍었나. */
    fun countFor(discoveries: List<Discovery>, flowerId: Int): Int =
        discoveries.count { it.flowerId == flowerId }

    /** 화면 05 `내 발견 기록` — 최근 것이 위로. */
    fun forFlower(discoveries: List<Discovery>, flowerId: Int): List<Discovery> =
        discoveries.filter { it.flowerId == flowerId }.sortedByDescending { it.capturedAt }

    /**
     * 화면 04 `최근 발견한 꽃`.
     *
     * ⚠️ **종 단위로 중복을 제거한다.** 안 하면 오늘 장미를 세 번 찍은 사람의
     *    `최근 발견한 꽃`이 장미 3칸으로 채워진다 — 목록이 아니라 한 종의 로그가 된다.
     */
    fun recentFlowerIds(discoveries: List<Discovery>, limit: Int): List<Int> =
        discoveries
            .sortedByDescending { it.capturedAt }
            .map { it.flowerId }
            .distinct()
            .take(limit)

    /** 화면 04 `최근 발견한 꽃` 카드가 쓰는 기록(종별 최신 1건). */
    fun recentDiscoveries(discoveries: List<Discovery>, limit: Int): List<Discovery> =
        discoveries
            .sortedByDescending { it.capturedAt }
            .distinctBy { it.flowerId }
            .take(limit)

    /**
     * 화면 04 `이번 시즌 12종`.
     *
     * 시즌 경계는 [GamePolicy.seasonWindows]가 원본이다 — 여기서 월을 다시 적지 않는다
     * (B-1 오너 미확정이라 답이 오면 한 곳만 고쳐야 한다).
     *
     * ⚠️ 휴지기(12~2월)에 찍은 기록은 **어느 시즌도 아니다.** 0종이 맞다 —
     *    억지로 다음 시즌에 넣으면 시즌 시작 전에 이미 채워진 랭킹이 된다.
     */
    fun seasonCollectedCount(
        discoveries: List<Discovery>,
        month: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val season = seasonIndexOf(month) ?: return 0
        return discoveries
            .filter { seasonIndexOf(monthOf(it.capturedAt, timeZone)) == season }
            .mapTo(HashSet()) { it.flowerId }
            .size
    }

    /** 그 달이 속한 시즌 번호. 휴지기면 null. */
    fun seasonIndexOf(month: Int): Int? =
        GamePolicy.seasonWindows.firstOrNull { it.contains(month) }?.index

    /**
     * B-5 — 같은 종 + **같은 장소**를 하루에 [GamePolicy.SAME_FLOWER_SAME_PLACE_DAILY_LIMIT]회.
     *
     * **장소를 옮기면 인정한다.** 좌표는 [PlaceKey]로 반올림해 비교한다 —
     * GPS가 몇 미터씩 흔들려서 원좌표로 비교하면 같은 자리가 늘 다른 장소가 된다.
     *
     * ⚠️ **위치가 없는 기록은 같은 장소로 본다.** 반대로 두면(다른 장소로 보면)
     *    위치 권한을 끈 사용자에게 B-5가 아예 걸리지 않는다 — 권한 거부가
     *    무제한 등록 우회로가 된다.
     *
     * ⚠️ 날짜 비교는 `capturedAt`으로 한다. `createdAt`(등록 시각)으로 세면
     *    자정 직전에 찍고 자정 직후에 등록한 사진이 어제도 오늘도 아닌 것이 된다.
     */
    fun isDuplicateToday(
        discoveries: List<Discovery>,
        flowerId: Int,
        lat: Double?,
        lng: Double?,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val sameDay = discoveries.filter {
            it.flowerId == flowerId && isSameDay(it.capturedAt, now, timeZone)
        }
        if (sameDay.isEmpty()) return false
        val limit = GamePolicy.SAME_FLOWER_SAME_PLACE_DAILY_LIMIT
        if (lat == null || lng == null) return sameDay.size >= limit
        val key = PlaceKey.of(lat, lng)
        val samePlace = sameDay.count { d ->
            val l = d.lat
            val g = d.lng
            if (l == null || g == null) true else PlaceKey.of(l, g) == key
        }
        return samePlace >= limit
    }

    /** 화면 10 `12번째 꽃` — 이 종이 새로 들어간 뒤의 도감 순번. */
    fun dexOrderAfterAdding(discoveries: List<Discovery>, flowerId: Int): Int {
        val ids = collectedIds(discoveries)
        return if (flowerId in ids) ids.size else ids.size + 1
    }

    fun isSameDay(a: Long, b: Long, timeZone: TimeZone = TimeZone.getDefault()): Boolean {
        val ca = calendarAt(a, timeZone)
        val cb = calendarAt(b, timeZone)
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun monthOf(millis: Long, timeZone: TimeZone): Int =
        calendarAt(millis, timeZone).get(Calendar.MONTH) + 1

    private fun calendarAt(millis: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }
}

/**
 * 좌표를 "같은 장소" 단위로 반올림한 키.
 *
 * ⚠️ **B-5 판정과 카카오 캐시가 이 함수를 공유해야 한다.** 두 곳이 각자 반올림하면
 *    캐시는 "같은 자리"라고 하는데 B-5는 "다른 장소"라고 판단하는 어긋남이 생긴다.
 *    iOS도 같은 이유로 `AppSession.placeKey`를 `KakaoPlaceService`와 공유한다.
 *
 * 문자열 형식 자체는 계약이 아니다(저장·전송하지 않는다). **계약은 반올림 자릿수**
 * [GamePolicy.GEOCODE_CACHE_COORD_DECIMALS]다.
 */
object PlaceKey {
    fun of(lat: Double, lng: Double): String {
        val digits = GamePolicy.GEOCODE_CACHE_COORD_DECIMALS
        // ⚠️ 로케일을 주지 않으면 독일어 등에서 소수점이 `,`가 된다. 그러면 키가 갈려
        //    캐시가 안 먹고(쿼터 두 배) B-5도 같은 자리를 다른 자리로 본다.
        return "%.${digits}f,%.${digits}f".format(java.util.Locale.US, lat, lng)
    }
}
