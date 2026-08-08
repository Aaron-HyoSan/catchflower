package com.catchflower.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 동네 검색 응답 해석 — 화면 02가 목록에 올리는 값.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가
 *
 * 여기서 뽑은 `dong_code`는 **6개월간 바꿀 수 없다**(0004 트리거). 그리고 잘못 뽑아도
 * **아무것도 실패하지 않는다** — 저장은 200, 화면 20은 `연남동`을 잘 보여주고,
 * 랭킹만 조용히 빈다. 눈으로 검산할 방법이 없는 결함이다.
 *
 * ⚠️ **이 함정은 이미 실현돼 있었다.** 실측으로 내 테스트 계정 3개에 법정동 코드
 *    `1144012400`이 들어 있었다([KakaoRegionSearchService] 주석 ①).
 *
 * ## ⚠️ 응답 본문은 **전부 실제 카카오가 준 것**이다
 *
 * `app/src/test/resources/kakao/` 아래 json — `save_kakao_fixtures.py`로 받아서 **한 글자도
 * 고치지 않고** 넣었다. 내가 상상한 형식으로 쓰면 **테스트만 통과하고 앱은 안 된다**
 * ((22)에서 실제로 그랬다).
 *
 * 🔴 **픽스처를 손으로 줄이지 마라.** `meta`·`road_address`·좌표까지 그대로 있다.
 *    줄이면 "우리 코드가 안 보는 칸"을 내가 판단한 것이 되고, 그 판단이 틀리면
 *    픽스처가 실제 응답과 달라진다 — 그 순간 이 파일은 아무것도 증명하지 않는다.
 */
class RegionSearchServiceTest {

    /** 실측 응답을 정해 두고, 무엇을 어떤 순서로 물었는지 기록한다. */
    private class FakeTransport(
        private val responses: MutableList<Pair<Int, String>>,
    ) : KakaoRegionSearchService.Transport {
        val urls = mutableListOf<String>()
        val auths = mutableListOf<String>()

        override suspend fun get(url: String, authorization: String): Pair<Int, String> {
            urls += url
            auths += authorization
            return if (responses.isEmpty()) 500 to "" else responses.removeAt(0)
        }
    }

    private val logs = mutableListOf<String>()

    /**
     * ⚠️ `apiKey`를 반드시 넣는다 — 기본값이 전역 `AppSecrets`라 **그 맥에
     *    `local.properties`가 있느냐로 통과 여부가 갈린다.**
     * ⚠️ `log`도 갈아 끼운다 — `android.util.Log`는 JVM에서 던진다.
     */
    private fun service(
        vararg responses: Pair<Int, String>,
    ): Pair<FakeTransport, KakaoRegionSearchService> {
        val transport = FakeTransport(responses.toMutableList())
        return transport to KakaoRegionSearchService(
            apiKey = "test-key",
            transport = transport,
            log = { logs += it },
        )
    }

    /** 실측 응답 본문. **여기서 문자열을 만들지 않는다** — 파일에서 읽는다. */
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/kakao/$name.json")) {
            "픽스처 $name.json이 없다 — save_kakao_fixtures.py로 다시 받아라"
        }.bufferedReader().use { it.readText() }

    private fun ok(name: String): Pair<Int, String> = 200 to fixture(name)

    private fun loaded(result: RegionSearchResult): List<RegionCandidate> =
        (result as RegionSearchResult.Loaded).candidates

    // ── ①번 함정: 법정동이 섞여 온다 ────────────────────────────────

    /**
     * `연남동`은 행정동 1개가 깔끔하게 온다. **정상 경로의 값 전부를 못 박는다** —
     * 코드·구 코드·이름 형식이 하나라도 어긋나면 랭킹이 빈다.
     */
    @Test
    fun 연남동은_행정동_코드를_준다() = runBlocking {
        val (transport, svc) = service(ok("search_연남동"))
        val list = loaded(svc.search("연남동"))

        assertEquals(1, list.size)
        // 🔴 실측 `h_code`. `b_code`(1144012400)를 쓰면 **같은 연남동인데 다른 동네**가 된다.
        assertEquals("1144071000", list[0].dongCode)
        assertEquals("11440", list[0].guCode)
        // 되짚기 형식이다 — 검색 응답은 `서울 마포구 연남동`이었다.
        assertEquals("서울특별시 마포구 연남동", list[0].regionName)
        assertEquals("연남동", list[0].dongName)

        // 되짚기를 부르지 않았다 — 행정동을 이미 찾았으므로 쿼터를 더 쓰면 안 된다.
        assertEquals(1, transport.urls.size)
        assertTrue(transport.urls[0].startsWith("https://dapi.kakao.com/v2/local/search/address.json"))
        assertEquals("KakaoAK test-key", transport.auths[0])
    }

    /**
     * 🔴 **`역삼`은 법정동과 행정동이 **섞여서** 온다**(실측 3건:
     *    법정동 `역삼동` + 행정동 `역삼1동`·`역삼2동`).
     *
     *    법정동을 안 걸러내면 목록 첫 줄이 `역삼동`(`b_code 1168010100`)이 되고,
     *    사용자는 **당연히 그걸 누른다** — 가장 자기 동네 이름 같으니까.
     *    그리고 6개월간 아무 랭킹에도 안 나온다.
     */
    @Test
    fun 역삼은_법정동을_걸러내고_행정동만_남긴다() = runBlocking {
        val (transport, svc) = service(ok("search_역삼"))
        val list = loaded(svc.search("역삼"))

        assertEquals(listOf("1168064000", "1168065000"), list.map { it.dongCode })
        assertEquals(
            listOf("서울특별시 강남구 역삼1동", "서울특별시 강남구 역삼2동"),
            list.map { it.regionName },
        )
        // 법정동 `역삼동`의 `b_code`가 어디에도 없다.
        assertTrue(list.none { it.dongCode == "1168010100" })
        // 행정동이 있었으므로 되짚기는 한 번도 안 불렀다.
        assertEquals(1, transport.urls.size)
    }

    /**
     * 🔴 **두 이름 칸이 실제로 갈리는 행이 있다.** 실측 `봉천동 862-1`:
     *    `region_3depth_name`(법정동)은 `봉천동`, `region_3depth_h_name`(행정동)은
     *    **`청룡동`**이다. 그리고 이 행은 `h_code`·`b_code`를 **둘 다** 갖는다.
     *
     *    법정동 칸을 쓰면 코드는 청룡동(`1162059500`)인데 이름은 `봉천동`이 되어
     *    **화면 20이 랭킹과 다른 동네를 말한다.** 코드가 맞으니 랭킹은 정상으로
     *    보이고, 이름만 조용히 틀린다.
     *
     * ⚠️ **이 테스트는 돌연변이 하네스가 찾아낸 구멍이다.** 그전 픽스처들은 행정동
     *    행에서 두 칸이 같거나 법정동 칸이 비어 있어서, 칸을 바꿔치기해도
     *    **44개 중 3개가 초록으로 통과했다.** 초록 테스트가 증거가 아니라는 그 얘기다.
     */
    @Test
    fun 법정동명과_행정동명이_다르면_행정동명을_쓴다() = runBlocking {
        val (_, svc) = service(ok("search_봉천동862"))
        val list = loaded(svc.search("봉천동 862-1"))

        assertEquals(1, list.size)
        // 이름과 코드가 **같은 동네**를 가리켜야 한다.
        assertEquals("서울특별시 관악구 청룡동", list[0].regionName)
        assertEquals("1162059500", list[0].dongCode)
        assertEquals("청룡동", list[0].dongName)
        // 법정동 이름·코드가 어디에도 없다.
        assertTrue(list.none { it.regionName.contains("봉천동") })
        assertTrue(list.none { it.dongCode == "1162010100" })
    }

    // ── ③번 함정: 구·시는 되짚어서도 안 된다 ──────────────────────

    /**
     * 🔴 **`마포구`는 `h_code`가 **있다**(`1144000000`).** `h_code`만 보고 통과시키면
     *    구 코드가 `dong_code`로 저장되고, 랭킹은 오류 없이 빈다.
     *
     * 🔴 **그리고 되짚어서도 안 된다.** 구 대표 좌표는 우연히 어느 동에 걸리므로
     *    (실측: `마포구` → `성산2동`) 되짚으면 **고른 적 없는 동네가 6개월간 배정된다.**
     *    그래서 결과는 빈 목록이고 화면은 `검색 결과가 없어요`를 띄운다 —
     *    사용자가 동 이름으로 다시 치게 하는 것이 맞는 대응이다.
     */
    @Test
    fun 구를_검색하면_되짚지_않고_빈_목록이다() = runBlocking {
        val (transport, svc) = service(ok("search_마포구"))
        val list = loaded(svc.search("마포구"))

        assertTrue("구 코드가 후보로 올라왔다: $list", list.isEmpty())
        // 🔴 **호출이 1번이어야 한다.** 2번이면 되짚기를 했다는 뜻이고,
        //    그러면 `성산2동`이 후보로 올라온다.
        assertEquals(1, transport.urls.size)
    }

    // ── ②번 함정: 걸러내면 자기 동네가 사라진다 ────────────────────

    /**
     * 🔴 **`봉천동`은 법정동 1개만 온다** — 걸러내면 결과가 0개다. 그런데 그게
     *    사용자가 자기 동네를 부르는 이름이다. `검색 결과가 없어요`를 띄우면
     *    **자기 동네가 없는 앱**이 된다.
     *
     *    실측: 봉천동 법정동 좌표를 되짚으면 `서울특별시 관악구 중앙동`이 나온다.
     *    이름이 바뀌므로 **자동 확정하지 않고 화면이 그대로 보여준다**
     *    (`RegionPickerScreen` 주석) — 여기서는 후보로 올라오는 것까지만 잰다.
     */
    @Test
    fun 봉천동은_좌표로_되짚어_행정동을_찾는다() = runBlocking {
        val (transport, svc) = service(ok("search_봉천동"), ok("coord_봉천동"))
        val list = loaded(svc.search("봉천동"))

        assertEquals(1, list.size)
        // 실측값이다. 친 이름(`봉천동`)과 **다르다** — 그게 이 경로의 사실이다.
        assertEquals("서울특별시 관악구 중앙동", list[0].regionName)
        assertEquals("1162061500", list[0].dongCode)
        assertEquals("11620", list[0].guCode)
        // 🔴 법정동 코드(`1162010100`)가 아니다. 되짚기의 `H` 행을 골랐다는 증거.
        assertTrue(list.none { it.dongCode == "1162010100" })

        assertEquals(2, transport.urls.size)
        // ⚠️ 되짚기 좌표는 **`x=경도&y=위도`**다. 뒤집으면 오류 없이 **다른 동네**가 온다.
        //    실측 봉천동 좌표: x 126.95…, y 37.48…
        assertTrue(
            "되짚기 URL이 x=경도,y=위도가 아니다: ${transport.urls[1]}",
            transport.urls[1].contains("x=126.95") && transport.urls[1].contains("y=37.48"),
        )
    }

    /** `성수동1가`도 같은 경로다(실측 → `성수1가1동`). 받침 없는 동명이 여기서 나온다. */
    @Test
    fun 성수동1가도_되짚어야_찾는다() = runBlocking {
        val (_, svc) = service(ok("search_성수동1가"), ok("coord_성수동1가"))
        val list = loaded(svc.search("성수동1가"))

        assertEquals("서울특별시 성동구 성수1가1동", list[0].regionName)
        assertEquals("1120065000", list[0].dongCode)
    }

    /**
     * ⚠️ **되짚기가 실패하면 그 행만 버리고, 검색 자체는 실패로 만들지 않는다.**
     *    되짚기는 부가 호출이라 [RegionSearchResult.Failed]로 올리면 다른 후보까지
     *    사라진다. 여기서는 후보가 하나뿐이라 빈 목록이 되고, 화면은
     *    `검색 결과가 없어요`를 띄운다.
     */
    @Test
    fun 되짚기가_실패하면_그_행만_버린다() = runBlocking {
        val (_, svc) = service(ok("search_봉천동"), 500 to "")
        val result = svc.search("봉천동")
        assertTrue(result is RegionSearchResult.Loaded)
        assertTrue(loaded(result).isEmpty())
    }

    // ── 결과 0개와 실패 ──────────────────────────────────────────────

    /**
     * 🔴 **결과 0개는 [RegionSearchResult.Loaded]다.** [RegionSearchResult.Failed]로
     *    만들면 오타를 친 사용자에게 `연결이 불안정해요`라고 말한다.
     */
    @Test
    fun 결과가_없으면_실패가_아니라_빈_목록이다() = runBlocking {
        val (_, svc) = service(ok("search_없는동네"))
        val result = svc.search("가나다라마바사")
        assertTrue("실패로 올렸다: $result", result is RegionSearchResult.Loaded)
        assertTrue(loaded(result).isEmpty())
    }

    /**
     * 🔴 **HTTP 실패는 빈 목록이 아니다.** 뭉치면 와이파이가 끊긴 사용자가
     *    **자기 동네 이름을 의심한다.**
     */
    @Test
    fun HTTP_실패는_빈_목록과_다른_결과다() = runBlocking {
        val (_, svc) = service(403 to """{"errorType":"AccessDeniedError","message":"disabled OPEN_MAP_AND_LOCAL service"}""")
        assertEquals(RegionSearchResult.Failed, svc.search("연남동"))
        // 403은 콘솔 토글이 꺼진 것이다 — 키가 틀린 것과 구분되게 본문이 남아야 한다.
        assertTrue(logs.any { it.contains("403") && it.contains("OPEN_MAP_AND_LOCAL") })
    }

    /**
     * ⚠️ **응답 형식이 바뀌면 실패로 남아야 한다.** 빈 목록으로 만들면 카카오가
     *    스키마를 바꾼 날 전국 사용자가 `검색 결과가 없어요`를 보고, 우리는
     *    "검색어 문제"로 읽는다.
     */
    @Test
    fun 형식이_깨지면_실패다() = runBlocking {
        val (_, svc) = service(200 to """{"documents":"이건 배열이 아니다"}""")
        assertEquals(RegionSearchResult.Failed, svc.search("연남동"))
    }

    @Test
    fun 본문이_비면_실패다() = runBlocking {
        val (_, svc) = service(200 to "")
        assertEquals(RegionSearchResult.Failed, svc.search("연남동"))
    }

    // ── 부르기 전에 막는 것들 ────────────────────────────────────────

    /**
     * 실측: `동` 한 글자는 0개다. **부르지 않는다** — 화면이 타이핑마다 쿼터를 태운다.
     * ⚠️ 그래도 [RegionSearchResult.Failed]가 아니다. 지우는 중인 검색창에
     *    오류를 띄우면 안 된다.
     */
    @Test
    fun 한_글자면_부르지_않는다() = runBlocking {
        val (transport, svc) = service(ok("search_연남동"))
        val result = svc.search("동")
        assertTrue(loaded(result).isEmpty())
        assertEquals(0, transport.urls.size)
    }

    /** 키 없는 빌드는 **오류가 아니다.** 호출도 하지 않는다. */
    @Test
    fun 키가_없으면_NotConfigured다() = runBlocking {
        val transport = FakeTransport(mutableListOf())
        val svc = KakaoRegionSearchService(apiKey = "", transport = transport, log = { logs += it })
        assertEquals(RegionSearchResult.NotConfigured, svc.search("연남동"))
        assertEquals(RegionSearchResult.NotConfigured, svc.byCoordinate(37.5, 127.0))
        assertEquals(0, transport.urls.size)
    }

    // ── 현재 위치로 찾기 ────────────────────────────────────────────

    /**
     * `현재 위치로 우리 동네 찾기`. **검색 경로와 같은 `H` 선택 규칙**이어야 한다 —
     * 갈리면 검색으로 정한 사람과 위치로 정한 사람이 같은 동네에서 다른 코드를 갖는다.
     */
    @Test
    fun 좌표로_찾으면_행정동_하나다() = runBlocking {
        val (transport, svc) = service(ok("coord_봉천동"))
        val list = loaded(svc.byCoordinate(lat = 37.4825384917268, lng = 126.952165181187))

        assertEquals(1, list.size)
        assertEquals("1162061500", list[0].dongCode)
        assertEquals("서울특별시 관악구 중앙동", list[0].regionName)
        // 🔴 x가 경도, y가 위도다. 뒤집으면 **오류 없이 다른 동네**가 온다.
        assertTrue(transport.urls[0].contains("x=126.952165181187"))
        assertTrue(transport.urls[0].contains("y=37.4825384917268"))
    }

    /**
     * ⚠️ `H` 행이 없는 좌표가 있다(바다·해외). 그때 `B` 행으로 대체하면
     *    **법정동 코드가 저장된다** — 그게 ①번 함정이다.
     */
    @Test
    fun 행정동_행이_없으면_실패다() = runBlocking {
        val onlyLegal = """
            {"meta":{"total_count":1},"documents":[
              {"region_type":"B","address_name":"서울특별시 관악구 봉천동",
               "region_1depth_name":"서울특별시","region_2depth_name":"관악구",
               "region_3depth_name":"봉천동","code":"1162010100","x":126.9,"y":37.4}
            ]}
        """.trimIndent()
        val (_, svc) = service(200 to onlyLegal)
        assertEquals(RegionSearchResult.Failed, svc.byCoordinate(37.4, 126.9))
    }

    // ── 이름 조립 ────────────────────────────────────────────────────

    /**
     * 🔴 **검색 응답은 시도명을 축약한다**(실측: 검색 `서울`, 되짚기 `서울특별시`).
     *    그대로 쓰면 화면 20이 A 문서와 다른 이름을 보여주고, 검색으로 정한 사용자와
     *    위치로 정한 사용자의 `region_name`이 갈린다.
     */
    @Test
    fun 축약된_시도명을_정식_이름으로_펼친다() {
        assertEquals("서울특별시", KakaoRegionSearchService.fullSidoName("서울"))
        assertEquals("경기도", KakaoRegionSearchService.fullSidoName("경기"))
        assertEquals("강원특별자치도", KakaoRegionSearchService.fullSidoName("강원"))
    }

    /**
     * ⚠️ **모르는 이름은 그대로 돌려준다.** 실측에서 검색이 이미 정식 이름을 주는
     *    시도가 있었다(`제주특별자치도`) — 표에 없어도 통과해야 한다.
     *    빈 문자열이나 예외로 만들면 **그 지역 사람은 동네를 고를 수 없다.**
     */
    @Test
    fun 모르는_시도명은_그대로_둔다() {
        assertEquals("제주특별자치도", KakaoRegionSearchService.fullSidoName("제주특별자치도"))
        assertEquals("없는시도", KakaoRegionSearchService.fullSidoName("없는시도"))
    }

    // ── 구 코드 ──────────────────────────────────────────────────────

    /** B-6 구 확장의 묶음 단위다. [KakaoPlaceService]와 **같은 규칙**이어야 한다. */
    @Test
    fun 구_코드는_앞_다섯_자리다() {
        assertEquals("11440", KakaoRegionSearchService.guOf("1144071000"))
    }

    /**
     * ⚠️ 짧은 코드는 null이다. `substring`으로 자르면 **예외로 앱이 죽는다** —
     *    응답 형식이 바뀌었을 때 화면 02가 열리지 않는다.
     */
    @Test
    fun 짧은_코드는_구를_못_만든다() {
        assertNull(KakaoRegionSearchService.guOf("1144"))
        assertNotNull(KakaoRegionSearchService.guOf("11440"))
    }
}
