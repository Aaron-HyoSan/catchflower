package com.catchflower.app.core

/**
 * 한국어 조사·서수 처리.
 *
 * A 문서가 **직접 요구한 것**이다:
 * > 조사 처리 주의: `{꽃이름}가/이` — 받침 유무에 따라 분기해야 한다. 개발 시 조사 자동 처리 함수 필요.
 * > 서수 표기: 두 번째 ~ 열한 번째 까지 한글, 12회 이상은 `12번째`
 *
 * 꽃 이름 200종에는 `억새`(받침 없음) · `개망초`(없음) · `민들레`(없음) ·
 * `할미꽃`(있음) · `봄맞이꽃`(있음)이 섞여 있다. 하드코딩으로는 못 막는다.
 *
 * ⚠️ **iOS `Core/Text/KoreanText.swift`와 같은 규칙이어야 한다.** 같은 꽃 이름에
 *    두 플랫폼이 다른 문장을 만들면 안 된다. 함수 이름·기본값 처리를 일부러 맞췄다.
 */
object KoreanText {

    /**
     * 마지막 글자에 받침(종성)이 있는가.
     *
     * 한글 음절은 `0xAC00 + (초성×21 + 중성)×28 + 종성` 이므로
     * `(코드 - 0xAC00) % 28 != 0` 이면 받침이 있다.
     *
     * @return 한글이 아니면 `null` — 판단하지 않는다 (학명·숫자 등).
     */
    fun hasFinalConsonant(word: String): Boolean? {
        val last = word.lastOrNull() ?: return null
        val code = last.code
        if (code !in 0xAC00..0xD7A3) return null
        return (code - 0xAC00) % 28 != 0
    }

    /**
     * 받침에 따라 조사를 고른다.
     *
     * @param withFinal 받침 **있을 때** 쓰는 조사 (`이` `을` `은` `과` `으로`)
     * @param withoutFinal 받침 **없을 때** 쓰는 조사 (`가` `를` `는` `와` `로`)
     *
     * 한글이 아니면 [withoutFinal]을 쓴다 — 어느 쪽이든 틀리지만
     * `장미(이)` 같은 괄호 표기는 A 문서의 어조("사람이 말하듯")에 어긋난다.
     */
    fun particle(word: String, withFinal: String, withoutFinal: String): String =
        if (hasFinalConsonant(word) == true) withFinal else withoutFinal

    /** `억새가` · `할미꽃이` — 화면 10 `{꽃이름}가 도감에 등록되었습니다.` */
    fun subject(word: String): String = word + particle(word, "이", "가")

    /** `억새를` · `할미꽃을` — 화면 11 `{꽃이름}를 다시 발견했어요!` */
    fun objectOf(word: String): String = word + particle(word, "을", "를")

    /** `억새는` · `할미꽃은` */
    fun topic(word: String): String = word + particle(word, "은", "는")

    /** `연남동으로` · `성수동2가로` */
    fun direction(word: String): String = word + particle(word, "으로", "로")

    private val koreanOrdinals = mapOf(
        2 to "두 번째", 3 to "세 번째", 4 to "네 번째", 5 to "다섯 번째",
        6 to "여섯 번째", 7 to "일곱 번째", 8 to "여덟 번째", 9 to "아홉 번째",
        10 to "열 번째", 11 to "열한 번째",
    )

    /**
     * 화면 11 — `이번이 **{서수}** 발견입니다.`
     *
     * 2~11회는 한글, 12회 이상은 `12번째` (A 문서 규정).
     * 1회는 신규 등록(화면 10)이라 여기 오지 않지만, 방어적으로 `첫 번째`를 돌려준다.
     */
    fun ordinal(count: Int): String = when {
        count <= 1 -> "첫 번째"
        else -> koreanOrdinals[count] ?: "${count}번째"
    }

    /**
     * 화면 17 `연남동 이웃 **1,284명**` — 천 단위 구분.
     *
     * `String.format("%,d")`를 쓰지 않는다. 로케일에 따라 `1.284`(마침표)가 되는데
     * 그건 한국어 화면에서 **1.284명으로 읽힌다.** 구분자를 직접 넣는다.
     */
    fun thousands(value: Int): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        return digits.reversed().chunked(3).joinToString(",").reversed()
    }

    /**
     * 화면 19 전화번호 마스킹 — `010-2••••-1234`.
     *
     * ⚠️ **A 문서가 "마스킹 필수"로 명시한 항목이다.** 연락처는 해시로만 대조하고
     *    원문을 저장하지 않는다(화면 03 고지). 화면에 다 띄우면 그 약속과 어긋난다.
     *
     * 규칙: 가운데 4자리 중 **앞 1자리만 남기고** 나머지를 `•`로 덮는다.
     * 형식을 못 알아보면(자릿수 부족·해외번호) **전부 덮는다** — 어중간히 노출하는 것보다 안전하다.
     *
     * ⚠️ 와이어프레임 표기는 `010-2••••-1234`로 점이 **4개**지만, 11자리 번호의 가운데는
     *    4자리라 앞 1자리를 남기면 점은 **3개**가 맞다(`010-2•••-1234`).
     *    점 개수를 맞추려고 자리를 하나 더 덮으면 마스킹 규칙이 번호 길이와 어긋난다 —
     *    와이어프레임 쪽이 시각 표기고, 여기서는 **자릿수를 따른다.**
     */
    fun maskPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 10..11) return "•".repeat(maxOf(digits.length, 4))
        val head = digits.take(3)
        val tail = digits.takeLast(4)
        val middle = digits.substring(3, digits.length - 4)
        // 가운데가 3자리(구형 번호)면 앞 1자리 + `••`, 4자리면 앞 1자리 + `•••`.
        val maskedMiddle = middle.take(1) + "•".repeat(middle.length - 1)
        return "$head-$maskedMiddle-$tail"
    }
}
