package com.catchflower.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 조사·서수 테스트.
 *
 * **왜 필요한가**: 조사가 틀리면 화면 10·11에서 **모든 사용자가 매번 본다**
 * (`장미을 다시 발견했어요`). 그런데 개발 중엔 더미 데이터 몇 종만 보게 되고,
 * 하필 그게 받침 없는 이름이면 끝까지 안 걸린다.
 */
class KoreanTextTest {

    @Test
    fun `받침 있는 이름은 이·을·은을 쓴다`() {
        assertEquals("할미꽃이", KoreanText.subject("할미꽃"))
        assertEquals("할미꽃을", KoreanText.objectOf("할미꽃"))
        assertEquals("할미꽃은", KoreanText.topic("할미꽃"))
    }

    @Test
    fun `받침 없는 이름은 가·를·는을 쓴다`() {
        assertEquals("민들레가", KoreanText.subject("민들레"))
        assertEquals("민들레를", KoreanText.objectOf("민들레"))
        assertEquals("민들레는", KoreanText.topic("민들레"))
    }

    /**
     * `ㄹ` 받침은 `으로`가 아니라 `로`다 (`서울로`, 아니 `서울으로`가 아니다).
     * ⚠️ 지금 구현은 이걸 구분하지 않는다 — 받침이 있으면 `으로`를 쓴다.
     * 장소 이름에 쓰는 [KoreanText.direction]에서만 문제가 되고, 화면 10·11의
     * 꽃 이름에는 쓰이지 않아 지금은 넘긴다. **쓰기 전에 고쳐야 한다.**
     */
    @Test
    fun `ㄹ 받침 예외는 아직 구현되지 않았다`() {
        assertEquals("성수동으로", KoreanText.direction("성수동"))
        // 알려진 한계를 고정한다. 고치면 이 테스트가 깨지고, 그때 기대값을 "서울로"로 바꾼다.
        assertEquals("서울으로", KoreanText.direction("서울"))
    }

    @Test
    fun `한글이 아니면 판단하지 않고 기본형을 쓴다`() {
        assertNull(KoreanText.hasFinalConsonant("Rosa"))
        assertNull(KoreanText.hasFinalConsonant(""))
        // 괄호 표기(`장미(이)`)를 만들지 않는다 — A 문서의 어조에 어긋난다.
        assertEquals("Rosa가", KoreanText.subject("Rosa"))
    }

    @Test
    fun `서수는 11까지 한글이고 12부터 숫자다`() {
        assertEquals("두 번째", KoreanText.ordinal(2))
        assertEquals("열한 번째", KoreanText.ordinal(11))
        assertEquals("12번째", KoreanText.ordinal(12))
        assertEquals("37번째", KoreanText.ordinal(37))
    }

    @Test
    fun `1회 이하는 방어적으로 첫 번째다`() {
        // 1회는 화면 10(신규 등록)이라 화면 11에 오지 않는다.
        assertEquals("첫 번째", KoreanText.ordinal(1))
        assertEquals("첫 번째", KoreanText.ordinal(0))
    }

    @Test
    fun `천 단위 구분자는 쉼표다`() {
        // ⚠️ `String.format("%,d")`는 로케일에 따라 `1.284`가 되고, 그건 한국어
        //    화면에서 **1.284명**으로 읽힌다. 그래서 직접 넣는다.
        assertEquals("1,284", KoreanText.thousands(1_284))
        assertEquals("999", KoreanText.thousands(999))
        assertEquals("1,000", KoreanText.thousands(1_000))
        assertEquals("12,345", KoreanText.thousands(12_345))
        assertEquals("123,456", KoreanText.thousands(123_456))
        assertEquals("1,234,567", KoreanText.thousands(1_234_567))
        assertEquals("0", KoreanText.thousands(0))
    }

    @Test
    fun `전화번호는 가운데를 덮는다`() {
        // 11자리: 가운데 4자리 중 앞 1자리만 남는다 → 점 3개.
        assertEquals("010-2•••-1234", KoreanText.maskPhone("01023451234"))
        assertEquals("010-2•••-4567", KoreanText.maskPhone("010-2123-4567"))
        // 10자리(구형): 가운데가 3자리 → 점 2개.
        assertEquals("011-2••-4567", KoreanText.maskPhone("0112234567"))
    }

    @Test
    fun `형식을 못 알아보면 전부 덮는다`() {
        // 어중간히 노출하는 것보다 안전하다. 해외번호·자릿수 부족·빈 문자열.
        assertEquals("••••", KoreanText.maskPhone(""))
        assertEquals("••••", KoreanText.maskPhone("1234"))
        // 9자리 — 국내 형식이 아니다.
        assertEquals("•••••••••", KoreanText.maskPhone("012345678"))
        // ⚠️ 자릿수만 보고 판단하기 때문에 **해외번호 중 11자리는 국내처럼 마스킹된다**
        //    (`+1 415 555 0199` → 숫자 11개). 덜 덮는 게 아니라 자릿수 위치가 다를 뿐이고,
        //    연락처가 국내 서비스 대조용이라 여기서 국가코드를 따로 다루지 않는다.
        assertEquals("141-5•••-0199", KoreanText.maskPhone("+1 415 555 0199"))
    }

    @Test
    fun `마스킹 결과에 가운데 숫자가 남지 않는다`() {
        // 규칙을 눈으로 확인하는 대신 **원문 조각이 새는지**를 본다.
        val masked = KoreanText.maskPhone("01098765432")
        assertEquals("010-9•••-5432", masked)
        // 가운데 4자리 `9876` 중 앞 1자리만 허용 — `876`이 그대로 보이면 실패다.
        org.junit.Assert.assertFalse(masked.contains("876"))
    }
}
