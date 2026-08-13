package com.catchflower.app.data

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 도감 숫자와 B-5 판정 테스트.
 *
 * **이 계산들은 틀려도 화면에 예쁘게 나온다.** `모은 꽃 37 / 200종`, `발견 횟수 4회`,
 * `12번째 꽃` — 전부 그럴듯한 숫자가 뜨고 사용자는 맞는 줄 안다.
 * 랭킹에서 이미 같은 함정을 밟았다(진행 (17): 정렬이 틀려도 화면은 정상).
 *
 * ⚠️ **각 테스트가 "어떤 경우에 빨개지나"를 주석에 적는다.** 적을 수 없으면
 *    그 테스트는 아무것도 검증하지 않는 것이다 (진행 (22) 교훈).
 */
class DiscoveryRulesTest {

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    /** 2026-08-06 14:00 KST. 시즌 1(3~8월) 안이다. */
    private val aug6 = at(2026, 8, 6, 14)

    private fun at(y: Int, m: Int, d: Int, h: Int): Long =
        Calendar.getInstance(seoul).apply {
            clear()
            set(y, m - 1, d, h, 0, 0)
        }.timeInMillis

    private fun d(
        id: String,
        flowerId: Int,
        capturedAt: Long,
        lat: Double? = null,
        lng: Double? = null,
        visibility: Visibility = Visibility.PRIVATE,
    ) = Discovery(
        id = id,
        userId = "u",
        flowerId = flowerId,
        photoUrl = null,
        localPhotoPath = null,
        lat = lat,
        lng = lng,
        placeName = null,
        dongCode = null,
        guCode = null,
        visibility = visibility,
        aiConfidence = 0.8f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = capturedAt,
        capturedAt = capturedAt,
    )

    // ── 도감 숫자 ────────────────────────────────────────────────────

    /** 같은 종을 3번 찍으면 도감은 1종이다. 빨개지는 경우: 기록 수를 종수로 세면. */
    @Test
    fun 같은_종을_여러_번_찍어도_한_종이다() {
        val records = listOf(
            d("a", 1, aug6),
            d("b", 1, aug6 - 86_400_000),
            d("c", 2, aug6),
        )
        assertEquals(setOf(1, 2), DiscoveryRules.collectedIds(records))
        assertEquals(2, DiscoveryRules.collectedIds(records).size)
        assertEquals(2, DiscoveryRules.countFor(records, 1))
    }

    /**
     * 화면 04 `최근 발견한 꽃`은 **종이 겹치지 않아야** 한다.
     *
     * 빨개지는 경우: 중복 제거를 빼면. 그러면 오늘 장미를 세 번 찍은 사람의
     * 최근 목록이 장미 3칸이 된다 — 목록이 아니라 한 종의 로그가 된다.
     */
    @Test
    fun 최근_발견은_종별로_한_칸씩만_차지한다() {
        val records = listOf(
            d("a", 1, aug6),
            d("b", 1, aug6 - 1000),
            d("c", 2, aug6 - 2000),
        )
        assertEquals(listOf(1, 2), DiscoveryRules.recentFlowerIds(records, limit = 6))
    }

    /** 최근순 정렬. 빨개지는 경우: 오름차순으로 정렬하거나 정렬을 빼면. */
    @Test
    fun 최근_발견은_최신이_먼저다() {
        val old = d("old", 1, aug6 - 10 * 86_400_000L)
        val fresh = d("new", 2, aug6)
        assertEquals(
            listOf(2, 1),
            DiscoveryRules.recentFlowerIds(listOf(old, fresh), limit = 6),
        )
    }

    /** 화면 05는 최신이 위다. 빨개지는 경우: `sortedBy`(오름차순)로 쓰면. */
    @Test
    fun 종별_기록도_최신이_먼저다() {
        val records = listOf(
            d("old", 5, aug6 - 5 * 86_400_000L),
            d("mid", 5, aug6 - 86_400_000L),
            d("new", 5, aug6),
        )
        assertEquals(
            listOf("new", "mid", "old"),
            DiscoveryRules.forFlower(records, 5).map { it.id },
        )
    }

    /** 다른 종의 기록이 섞이면 안 된다. 빨개지는 경우: filter 조건이 빠지면. */
    @Test
    fun 종별_기록은_그_종만_센다() {
        val records = listOf(d("a", 1, aug6), d("b", 2, aug6))
        assertEquals(1, DiscoveryRules.forFlower(records, 1).size)
        assertEquals(0, DiscoveryRules.countFor(records, 99))
    }

    // ── 시즌 ────────────────────────────────────────────────────────

    /**
     * 8월은 시즌 1(3~8월)이다. 5월에 찍은 것도 같은 시즌이라 함께 센다.
     *
     * 빨개지는 경우: "이번 달"로만 세면 5월 기록이 빠진다.
     * 실제로 그렇게 되어 있었다 — 옛 `DummyDiscoveries.thisSeasonIds`는
     * **그냥 앞 12개**였다(계산이 아니라 상수).
     */
    @Test
    fun 이번_시즌은_같은_시즌_창_전체를_센다() {
        val records = listOf(
            d("may", 1, at(2026, 5, 1, 12)),   // 시즌 1
            d("aug", 2, aug6),                  // 시즌 1
            d("oct", 3, at(2026, 10, 1, 12)),  // 시즌 2
        )
        assertEquals(
            2,
            DiscoveryRules.seasonCollectedCount(records, month = 8, timeZone = seoul),
        )
    }

    /**
     * 휴지기(12~2월)에는 시즌이 없다 → 0종.
     *
     * 빨개지는 경우: 휴지기를 다음 시즌으로 접으면. 그러면 3월 1일 시즌 시작 전에
     * 이미 채워진 랭킹이 된다.
     */
    @Test
    fun 휴지기에는_시즌_종수가_0이다() {
        val records = listOf(d("jan", 1, at(2026, 1, 15, 12)))
        assertEquals(
            0,
            DiscoveryRules.seasonCollectedCount(records, month = 1, timeZone = seoul),
        )
        assertEquals(null, DiscoveryRules.seasonIndexOf(1))
    }

    /** 시즌 경계는 GamePolicy가 원본이다. 빨개지는 경우: 여기 월을 하드코딩하면. */
    @Test
    fun 시즌_경계는_정책_파일을_따른다() {
        for (window in GamePolicy.seasonWindows) {
            assertEquals(window.index, DiscoveryRules.seasonIndexOf(window.startMonth))
            assertEquals(window.index, DiscoveryRules.seasonIndexOf(window.endMonth))
        }
        for (month in GamePolicy.dormantMonths) {
            assertEquals(null, DiscoveryRules.seasonIndexOf(month))
        }
    }

    // ── B-5 중복 ────────────────────────────────────────────────────

    /** 첫 등록은 중복이 아니다. 빨개지는 경우: 빈 목록에서 limit 비교가 뒤집히면. */
    @Test
    fun 첫_등록은_중복이_아니다() {
        assertFalse(
            DiscoveryRules.isDuplicateToday(emptyList(), 1, 37.5, 127.0, aug6, seoul),
        )
    }

    /** 같은 종·같은 자리·같은 날은 막는다. 빨개지는 경우: 좌표 비교를 빼면. */
    @Test
    fun 같은_종_같은_자리_같은_날은_중복이다() {
        val records = listOf(d("a", 1, aug6, lat = 37.5445, lng = 127.0374))
        assertTrue(
            DiscoveryRules.isDuplicateToday(records, 1, 37.5445, 127.0374, aug6, seoul),
        )
    }

    /**
     * **장소를 옮기면 인정한다.**
     *
     * 빨개지는 경우: 같은 날 같은 종을 무조건 막으면. 그러면 다른 동네에서 만난
     * 같은 꽃이 등록되지 않는다 — B-5는 도배 방지고 이동 금지가 아니다.
     */
    @Test
    fun 장소를_옮기면_같은_날에도_등록된다() {
        val records = listOf(d("a", 1, aug6, lat = 37.5445, lng = 127.0374)) // 성수동
        assertFalse(
            DiscoveryRules.isDuplicateToday(records, 1, 37.5600, 126.9250, aug6, seoul), // 연남동
        )
    }

    /**
     * GPS가 몇 미터 흔들려도 같은 자리다.
     *
     * `GEOCODE_CACHE_COORD_DECIMALS = 4`면 소수 5번째 자리 차이는 같은 키가 된다.
     * 빨개지는 경우: 원좌표를 그대로 비교하면 — 같은 벤치에서 두 번 찍어도
     * 매번 "새 장소"가 되어 B-5가 사실상 꺼진다.
     */
    @Test
    fun 좌표가_미세하게_흔들려도_같은_자리로_본다() {
        val records = listOf(d("a", 1, aug6, lat = 37.54450, lng = 127.03740))
        assertTrue(
            DiscoveryRules.isDuplicateToday(records, 1, 37.544501, 127.037401, aug6, seoul),
        )
    }

    /**
     * ⚠️ **위치가 없는 기록은 같은 장소로 본다.**
     *
     * 빨개지는 경우: 위치 없음을 "다른 장소"로 처리하면. 그러면 위치 권한을 끈
     * 사용자에게 B-5가 아예 안 걸려서 **권한 거부가 무제한 등록 우회로**가 된다.
     */
    @Test
    fun 위치_없는_기록은_같은_장소로_본다() {
        val records = listOf(d("a", 1, aug6))
        assertTrue(DiscoveryRules.isDuplicateToday(records, 1, null, null, aug6, seoul))
        // 이번엔 좌표가 있어도 — 앞 기록에 좌표가 없으니 다른 자리라고 단정할 수 없다.
        assertTrue(DiscoveryRules.isDuplicateToday(records, 1, 37.5, 127.0, aug6, seoul))
    }

    /** 어제 기록은 오늘을 막지 않는다. 빨개지는 경우: 날짜 비교를 빼면. */
    @Test
    fun 어제_기록은_오늘_등록을_막지_않는다() {
        val yesterday = at(2026, 8, 5, 23)
        val records = listOf(d("a", 1, yesterday, lat = 37.5445, lng = 127.0374))
        assertFalse(
            DiscoveryRules.isDuplicateToday(records, 1, 37.5445, 127.0374, aug6, seoul),
        )
    }

    /** 다른 종은 서로를 막지 않는다. 빨개지는 경우: flowerId 조건이 빠지면. */
    @Test
    fun 다른_종은_서로를_막지_않는다() {
        val records = listOf(d("a", 1, aug6, lat = 37.5445, lng = 127.0374))
        assertFalse(
            DiscoveryRules.isDuplicateToday(records, 2, 37.5445, 127.0374, aug6, seoul),
        )
    }

    /**
     * ⚠️ **`capturedAt`으로 센다, `createdAt`이 아니다.**
     *
     * 자정 직전에 찍고 자정 직후에 등록한 사진: 촬영은 어제, 등록은 오늘이다.
     * B-5는 "그 날 그 자리에서 찍었나"를 묻는 규칙이므로 촬영 시각을 본다.
     * 빨개지는 경우: `createdAt`으로 바꾸면 — 이 기록은 오늘 것으로 취급되어
     * 오늘 같은 꽃을 찍은 사람이 등록을 못 한다.
     */
    @Test
    fun 날짜는_촬영_시각으로_센다() {
        val capturedYesterday = at(2026, 8, 5, 23)
        val registeredToday = at(2026, 8, 6, 0)
        val record = d("a", 1, capturedYesterday, lat = 37.5445, lng = 127.0374)
            .copy(createdAt = registeredToday)
        assertFalse(
            DiscoveryRules.isDuplicateToday(
                listOf(record), 1, 37.5445, 127.0374, aug6, seoul,
            ),
        )
    }

    // ── 도감 순번 ───────────────────────────────────────────────────

    /**
     * 화면 10 `12번째 꽃`.
     *
     * 빨개지는 경우: 재발견에도 +1을 하면. 그러면 이미 모은 꽃을 다시 찍을 때마다
     * 도감 순번이 올라가서 200종을 넘는 숫자가 화면에 뜬다.
     */
    @Test
    fun 도감_순번은_신규일_때만_올라간다() {
        val records = listOf(d("a", 1, aug6), d("b", 2, aug6))
        assertEquals(3, DiscoveryRules.dexOrderAfterAdding(records, flowerId = 3))
        assertEquals(2, DiscoveryRules.dexOrderAfterAdding(records, flowerId = 1))
    }

    // ── 좌표 키 ─────────────────────────────────────────────────────

    /**
     * ⚠️ **카카오 캐시와 B-5가 같은 키 함수를 써야 한다.**
     *
     * 빨개지는 경우: `KakaoPlaceService`가 자기 반올림을 다시 구현하면 —
     * 캐시는 "같은 자리"인데 B-5는 "다른 장소"로 보는 어긋남이 생긴다.
     * 그건 화면에 아무 증상도 남기지 않는다.
     */
    @Test
    fun 카카오_캐시_키와_B5_키가_같다() {
        val service = KakaoPlaceService(apiKey = "", transport = FailTransport)
        assertEquals(PlaceKey.of(37.5445, 127.0374), service.cacheKey(37.5445, 127.0374))
    }

    /** 반올림 자릿수는 정책에서 온다. 빨개지는 경우: 키 함수에 4를 박으면. */
    @Test
    fun 좌표_키는_정책_자릿수를_쓴다() {
        val digits = GamePolicy.GEOCODE_CACHE_COORD_DECIMALS
        val key = PlaceKey.of(37.123456789, 127.987654321)
        // "37.1235,127.9877" — 소수부 길이가 정책 자릿수와 같아야 한다.
        val decimals = key.substringBefore(',').substringAfter('.').length
        assertEquals(digits, decimals)
    }

    // ── 화면 20 지표 3칸 ────────────────────────────────────────────
    //
    // 🔴 **세 숫자 다 틀려도 화면이 완벽하게 정상이다.** 더미를 읽던 때는
    //    `37종 / 112회 / 26개`가 항상 예쁘게 있었다. 하나가 틀려도 사용자는
    //    자기 기록을 안 세어 봤으니 알 수 없다.

    /**
     * 종수와 발견 횟수는 **다른 숫자**다.
     *
     * 빨개지는 경우: `speciesCount`에 `discoveries.size`를 쓰거나(같은 종 3번이
     * 3종이 된다), `discoveryCount`에 `collectedIds().size`를 쓰면(112회가 37회로
     * 줄어든다 — **활동량이 3분의 1로 보인다**).
     */
    @Test
    fun 같은_종을_여러_번_찍으면_종수는_하나_발견은_여러_번이다() {
        val records = listOf(
            d("a", 1, aug6),
            d("b", 1, aug6 - 86_400_000),
            d("c", 1, aug6 - 172_800_000),
            d("d", 2, aug6),
        )
        val stats = DiscoveryRules.profileStats(records)
        assertEquals("모은 꽃", 2, stats.speciesCount)
        assertEquals("총 발견", 4, stats.discoveryCount)
    }

    /**
     * 🔴 **`공유`는 공개로 올린 것만이다.**
     *
     * 빨개지는 경우: `discoveries.size`를 쓰거나 `PRIVATE`가 아닌 것을 세면.
     * 그러면 `나만 보기`로 저장한 기록이 `공유 26개`에 들어가서 **사용자가
     * 자기 사진이 공개된 줄로 읽는다.** 화면 13에서 매번 고르는 값이라 실제로 섞인다.
     */
    @Test
    fun 공유는_공개로_올린_것만_센다() {
        val records = listOf(
            d("a", 1, aug6, visibility = Visibility.PUBLIC),
            d("b", 2, aug6, visibility = Visibility.PUBLIC),
            d("c", 3, aug6, visibility = Visibility.FRIENDS),
            d("d", 4, aug6, visibility = Visibility.PRIVATE),
        )
        val stats = DiscoveryRules.profileStats(records)
        assertEquals("공유", 2, stats.shareCount)
        // 나머지 두 칸은 공개 여부와 무관하게 전부 센다 — 내 도감은 내 것이다.
        assertEquals(4, stats.speciesCount)
        assertEquals(4, stats.discoveryCount)
    }

    /** `친구에게만`은 공유가 아니다. 위 테스트에서 가장 흔히 새는 값이라 따로 못 박는다. */
    @Test
    fun 친구에게만_공개는_공유가_아니다() {
        val onlyFriends = listOf(d("a", 1, aug6, visibility = Visibility.FRIENDS))
        assertEquals(0, DiscoveryRules.profileStats(onlyFriends).shareCount)
    }

    /** 기록이 없으면 세 칸 다 0이다. **여기서만 0이 맞는 값이다.** */
    @Test
    fun 기록이_없으면_전부_0이다() {
        val stats = DiscoveryRules.profileStats(emptyList())
        assertEquals(0, stats.speciesCount)
        assertEquals(0, stats.discoveryCount)
        assertEquals(0, stats.shareCount)
    }

    /**
     * 지표 3칸은 **시즌과 무관한 누적치**다(와이어프레임 20 주석 ②).
     *
     * 빨개지는 경우: 시즌 필터([DiscoveryRules.seasonCollectedCount])를 끼워 넣으면.
     * 그러면 시즌이 바뀌는 순간 `모은 꽃 37종`이 `0종`으로 떨어지고,
     * **사용자는 도감이 초기화된 줄로 읽는다** — 화면 21이 "안 지워진다"고
     * 약속한 바로 그 숫자다.
     */
    @Test
    fun 지표는_시즌으로_자르지_않는다() {
        val records = listOf(
            // 시즌 1(3~8월) 안.
            d("a", 1, aug6),
            // 지난 시즌 — 2025년 10월. 시즌으로 자르면 이게 빠진다.
            d("b", 2, at(2025, 10, 1, 12)),
            // 휴지기(1월)에 찍은 것. 어느 시즌에도 안 들어간다.
            d("c", 3, at(2026, 1, 15, 12)),
        )
        val stats = DiscoveryRules.profileStats(records)
        assertEquals("누적 종수", 3, stats.speciesCount)
        assertEquals("누적 발견", 3, stats.discoveryCount)
    }

    // ── 화면 23 목록([DiscoveryRules.discoveryList]) ──────────────────

    /**
     * 🔴 **`공유 N개`와 `내가 공유한 꽃` 목록의 줄 수가 같아야 한다.**
     *
     * 빨개지는 경우: 두 곳이 다른 기준으로 공개를 판정하면(예: 목록만 `FRIENDS`를
     * 포함하면). 그러면 `공유 2개`를 누른 사용자에게 3줄이 나오는데 **양쪽 다
     * 그럴듯해서 화면으로는 아무 이상이 없다.**
     */
    @Test
    fun 공유_지표와_공유_목록의_수가_같다() {
        val records = listOf(
            d("a", 1, aug6, visibility = Visibility.PUBLIC),
            d("b", 2, aug6, visibility = Visibility.PUBLIC),
            d("c", 3, aug6, visibility = Visibility.FRIENDS),
            d("d", 4, aug6, visibility = Visibility.PRIVATE),
        )
        assertEquals(
            DiscoveryRules.profileStats(records).shareCount,
            DiscoveryRules.discoveryList(records, sharedOnly = true).size,
        )
        assertEquals(2, DiscoveryRules.discoveryList(records, sharedOnly = true).size)
        // 제목이 `발견 기록`일 때는 안 거른다 — 내 도감은 공개 여부와 무관하다.
        assertEquals(4, DiscoveryRules.discoveryList(records, sharedOnly = false).size)
    }

    /** `친구에게만`은 공유가 아니다([isShared]의 유일한 참 조건은 `PUBLIC`이다). */
    @Test
    fun 공개만_공유로_본다() {
        assertTrue(DiscoveryRules.isShared(d("a", 1, aug6, visibility = Visibility.PUBLIC)))
        assertFalse(DiscoveryRules.isShared(d("b", 1, aug6, visibility = Visibility.FRIENDS)))
        assertFalse(DiscoveryRules.isShared(d("c", 1, aug6, visibility = Visibility.PRIVATE)))
    }

    /**
     * 🔴 **한 줄 = 기록 한 건이다(종이 아니다).**
     *
     * 빨개지는 경우: [DiscoveryRules.recentDiscoveries]를 재사용하면. 그건
     * `distinctBy { flowerId }`로 종을 합치므로 같은 꽃을 세 번 찍은 사용자의
     * 목록에서 **기록 두 건이 조용히 사라진다** — 지표는 `발견 횟수 3회`라고
     * 말하는데 목록은 1줄이다.
     */
    @Test
    fun 같은_종을_두_번_찍으면_두_줄이다() {
        val records = listOf(
            d("a", 1, aug6),
            d("b", 1, aug6 - 86_400_000),
            d("c", 2, aug6),
        )
        val rows = DiscoveryRules.discoveryList(records, sharedOnly = false)
        assertEquals(3, rows.size)
        // 같은 종의 두 건이 각각 남아 있다.
        assertEquals(2, rows.count { it.flowerId == 1 })
    }

    /**
     * 최신이 위다. **`capturedAt` 기준**이다 — 화면도 그 값을 그린다.
     *
     * 빨개지는 경우: 정렬을 빼거나 `createdAt`으로 정렬하면. 오프라인에서 찍고
     * 며칠 뒤 등록한 기록이 실제로 갈리고, 그때 목록은 **찍은 날짜를 보여주면서
     * 등록 순서로 늘어선다** — 순서가 뒤죽박죽인 것처럼 보인다.
     */
    @Test
    fun 최근_촬영이_위에_온다() {
        val records = listOf(
            d("old", 1, aug6 - 172_800_000),
            d("new", 2, aug6),
            d("mid", 3, aug6 - 86_400_000),
        )
        val rows = DiscoveryRules.discoveryList(records, sharedOnly = false)
        assertEquals(listOf("new", "mid", "old"), rows.map { it.id })
    }

    /** 공유가 0건이면 빈 목록이다 — 화면이 `아직 지도에 공유한 꽃이 없어요`를 그린다. */
    @Test
    fun 공유가_없으면_목록도_비어_있다() {
        val records = listOf(d("a", 1, aug6, visibility = Visibility.PRIVATE))
        assertTrue(DiscoveryRules.discoveryList(records, sharedOnly = true).isEmpty())
        assertEquals(1, DiscoveryRules.discoveryList(records, sharedOnly = false).size)
    }

    private object FailTransport : KakaoPlaceService.Transport {
        override suspend fun get(url: String, authorization: String): Pair<Int, String> =
            error("테스트에서 네트워크를 부르면 안 된다")
    }
}
