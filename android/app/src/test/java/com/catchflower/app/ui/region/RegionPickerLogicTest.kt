package com.catchflower.app.ui.region

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.RegionCandidate
import com.catchflower.app.data.RegionSearchResult
import com.catchflower.app.data.RegionUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 02의 판단.
 *
 * **왜 [RegionPickerLogic]에 몰아 놓고 여기서 재는가.** [RegionPickerViewModel]은
 * `AndroidViewModel`이라 JVM에서 **생성 자체가 안 된다**(Robolectric을 안 쓴다).
 * ViewModel 안에 분기를 두면 그 분기는 **어떤 테스트도 실행하지 않는 코드**가 된다 —
 * 화면 17에서 그래서 초록인 상태로 앱이 죽었다.
 *
 * ⚠️ 그러니 여기 초록인 것이 "화면 02가 동작한다"는 뜻은 아니다. 조립·코루틴·
 *    디바운스는 **에뮬레이터에서 열어 봐야** 확인된다.
 */
class RegionPickerLogicTest {

    private fun candidate(
        name: String = "서울특별시 마포구 연남동",
        code: String = "1144071000",
    ) = RegionCandidate(regionName = name, dongCode = code, guCode = code.take(5))

    // ── 미리 골라 두기 ──────────────────────────────────────────
    //
    // 🔴 이 규칙은 **에뮬레이터로 확인할 수 없었다.** `adb shell input text`가 한글을
    //    못 보내서(`NullPointerException: Attempt to get length of null array`)
    //    `역삼`처럼 결과가 여럿인 검색어를 기기에서 칠 방법이 없다.
    //    그래서 판단을 [RegionPickerLogic.autoSelect]로 빼고 여기서 잰다.

    /**
     * 🔴 **후보가 여럿이면 아무것도 고르지 않는다.**
     *
     * 골라 두면 사용자가 `역삼`을 검색해 목록 첫 줄(`역삼1동`)이 눌린 채로 CTA를
     * 누를 수 있고, 정작 살던 곳은 `역삼2동`이다. **6개월간 못 바꾼다.**
     * 오류는 한 건도 안 나고 화면은 고른 동네 이름을 정확히 보여준다.
     */
    @Test
    fun 후보가_여럿이면_고르지_않는다() {
        val many = listOf(
            candidate("서울특별시 강남구 역삼1동", "1168064000"),
            candidate("서울특별시 강남구 역삼2동", "1168065000"),
        )
        assertNull(
            "앱이 대신 고르면 6개월간 못 바꾸는 선택이 사용자 뜻과 달라진다",
            RegionPickerLogic.autoSelect(many),
        )
    }

    /** 하나뿐이면 골라 둔다 — `현재 위치로 찾기`가 그 경우다(되짚기는 하나를 준다). */
    @Test
    fun 후보가_하나면_골라_둔다() {
        val one = candidate()
        assertEquals(one, RegionPickerLogic.autoSelect(listOf(one)))
    }

    /** 빈 목록에서 고르면 안 된다 — 그 화면은 `검색 결과가 없어요`다. */
    @Test
    fun 후보가_없으면_고르지_않는다() {
        assertNull(RegionPickerLogic.autoSelect(emptyList()))
    }

    // ── 검색을 부를지 ────────────────────────────────────────────

    @Test
    fun 한_글자면_검색하지_않는다() {
        // 실측: `동` 한 글자는 결과가 0개다. 부르면 쿼터만 태운다.
        assertTrue(!RegionPickerLogic.shouldSearch("동"))
        assertTrue(!RegionPickerLogic.shouldSearch(""))
    }

    @Test
    fun 공백은_글자로_세지_않는다() {
        // `연 `를 두 글자로 세면 한 글자 검색이 나간다.
        assertTrue(!RegionPickerLogic.shouldSearch("연 "))
        assertTrue(!RegionPickerLogic.shouldSearch("  "))
        assertTrue(RegionPickerLogic.shouldSearch(" 연남 "))
    }

    @Test
    fun 두_글자면_검색한다() {
        assertTrue(RegionPickerLogic.shouldSearch("연남"))
    }

    // ── 목록 상태 ────────────────────────────────────────────────

    /**
     * 🔴 **이 테스트가 이 파일의 이유다.** 빈 목록과 실패를 같은 화면으로 만들면,
     *    와이파이가 끊긴 사용자에게 `검색 결과가 없어요`라고 말하게 된다 —
     *    사용자는 **자기 동네 이름을 의심한다.**
     */
    @Test
    fun 빈_결과와_실패가_다른_상태다() {
        assertEquals(
            RegionPickerUi.NoResult,
            RegionPickerLogic.toUi(RegionSearchResult.Loaded(emptyList())),
        )
        assertEquals(RegionPickerUi.Failed, RegionPickerLogic.toUi(RegionSearchResult.Failed))
        assertEquals(
            RegionPickerUi.NotConfigured,
            RegionPickerLogic.toUi(RegionSearchResult.NotConfigured),
        )
    }

    @Test
    fun 후보가_있으면_그대로_실린다() {
        val list = listOf(candidate(), candidate("서울특별시 강남구 역삼1동", "1168064000"))
        val ui = RegionPickerLogic.toUi(RegionSearchResult.Loaded(list))
        assertEquals(RegionPickerUi.Loaded(list), ui)
    }

    // ── 저장 결과 ────────────────────────────────────────────────

    /**
     * 🔴 **규칙 거절과 일시 실패를 뭉치면 안 된다.** 6개월 규칙은 **눌러서 풀리지
     *    않는다** — 뭉치면 화면이 `다시 시도`를 내밀고 사용자는 눌러도 안 되는
     *    버튼을 계속 누른다.
     */
    @Test
    fun 규칙_거절과_일시_실패가_다른_상태다() {
        assertEquals(
            RegionSaveUi.RuleRejected,
            RegionPickerLogic.toSaveUi(RegionUpdateResult.Rejected(400, "P0001")),
        )
        assertEquals(
            RegionSaveUi.SaveFailed,
            RegionPickerLogic.toSaveUi(RegionUpdateResult.Failed(500)),
        )
    }

    /**
     * ⚠️ **check 제약 위반(`23514`)도 사용자에게는 "다시 눌러도 안 된다"다.**
     *    앱이 잘못된 코드를 보낸 것이므로 재시도해도 같은 결과다
     *    (원인은 로그로 남는다 — `RegionUpdateService`).
     */
    @Test
    fun 제약_위반도_규칙_거절이다() {
        assertEquals(
            RegionSaveUi.RuleRejected,
            RegionPickerLogic.toSaveUi(RegionUpdateResult.Rejected(400, "23514")),
        )
    }

    /** 키 없는 빌드는 **오류가 아니다.** 막으면 서버 없는 빌드로 앱을 못 쓴다. */
    @Test
    fun 키_없는_빌드는_통과시킨다() {
        assertEquals(
            RegionSaveUi.Saved,
            RegionPickerLogic.toSaveUi(RegionUpdateResult.NotConfigured),
        )
    }

    // ── 이웃 수 라벨 ─────────────────────────────────────────────

    /**
     * 🔴 **모를 때는 줄을 뺀다.** `0명`이나 `아직 이웃이 적어요`를 쓰면
     *    이웃 1,284명인 동네를 **비어 있다고 말하는 것**이고, 사용자는 그걸 보고
     *    **6개월간 못 바꾸는 선택**을 한다. A 문서 3절
     *    `화면 02에서 이웃 수를 모를 때`가 이 판단을 적어 둔 곳이다.
     */
    @Test
    fun 이웃_수를_모르면_줄을_그리지_않는다() {
        assertNull(RegionPickerLogic.memberLabel(null))
    }

    @Test
    fun 이웃이_적으면_숫자를_쓰지_않는다() {
        assertEquals("아직 이웃이 적어요", RegionPickerLogic.memberLabel(0))
        assertEquals("아직 이웃이 적어요", RegionPickerLogic.memberLabel(9))
    }

    /**
     * ⚠️ **경계가 B-6 최소 인원과 같은 값이어야 한다.** 갈리면 화면 02가
     *    "이웃이 충분하다"고 말한 동네에서 서버가 구 단위로 넓힌다(0002).
     *    상수를 직접 읽어서 비교한다 — 10을 손으로 적으면 정책이 바뀌어도 안 잡힌다.
     */
    @Test
    fun 경계가_B6_최소인원과_같다() {
        val min = GamePolicy.REGION_RANKING_MIN_MEMBERS
        assertEquals("아직 이웃이 적어요", RegionPickerLogic.memberLabel(min - 1))
        assertEquals("이웃 ${min}명 활동 중", RegionPickerLogic.memberLabel(min))
    }

    /** 천 단위 구분자는 [com.catchflower.app.core.KoreanText]가 넣는다. */
    @Test
    fun 천_단위_구분자가_붙는다() {
        assertEquals("이웃 1,284명 활동 중", RegionPickerLogic.memberLabel(1284))
    }

    // ── CTA ──────────────────────────────────────────────────────

    @Test
    fun 미선택이면_안내_문구다() {
        assertEquals("동네를 선택해 주세요", RegionPickerLogic.ctaLabel(null))
    }

    @Test
    fun 받침_있는_동명은_으로다() {
        assertEquals("연남동으로 시작하기", RegionPickerLogic.ctaLabel(candidate()))
    }

    /**
     * 🔴 **받침 없는 동명이 실제로 있다.** `"${dongName}으로"`로 쓰면
     *    `성수동2가으로 시작하기`가 된다 — **컴파일도 되고 화면도 그려진다.**
     *    A 문서 0절이 조사 자동 처리를 요구하는 이유다.
     *
     *    실측으로 나온 이름들이다: `성수동1가`(A 문서 4절 2번이 최장으로 지목),
     *    `제주시 연동`(구·시 검색 되짚기 결과).
     */
    @Test
    fun 받침_없는_동명은_로다() {
        assertEquals(
            "성수동1가로 시작하기",
            RegionPickerLogic.ctaLabel(candidate("서울특별시 성동구 성수동1가", "1120068000")),
        )
    }

    /**
     * ⚠️ **마지막 어절만 쓴다.** 전체 이름을 넣으면
     *    `서울특별시 마포구 연남동으로 시작하기`가 되어 버튼을 넘친다
     *    (A 문서 4절 2번이 잘림을 확인 요청 항목으로 올린 자리다).
     */
    @Test
    fun 전체_이름이_아니라_동명만_쓴다() {
        assertTrue(!RegionPickerLogic.ctaLabel(candidate()).contains("마포구"))
    }
}
