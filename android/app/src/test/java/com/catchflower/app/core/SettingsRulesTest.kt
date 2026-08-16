package com.catchflower.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 20-2 설정에 무엇이 나오는가.
 *
 * ## 🔴 왜 이 파일이 있어야 하는가
 *
 * 두 규칙 다 **어긋나도 화면이 정상으로 보인다**:
 *
 * 1. **주소 없는 빌드의 `고객문의`.** 눌러서 메일 앱이 열리고 받는 사람이 빈 칸이다 —
 *    보낸 사용자는 접수됐다고 믿고, 우리는 문의가 오지 않는 것을 "문의가 없다"로 읽는다.
 *    **지금 빌드가 그 상태다**(`CONTACT_EMAIL`이 비어 있다 · A 문서 4절 17번) — 즉
 *    이 규칙은 가정이 아니라 **지금 동작 중인 코드**다.
 * 2. **`앱 버전`을 누를 수 있게 두는 것.** 눌러도 아무 일이 없는 줄 = 우리가 2026-08-13에
 *    지운 그 죽은 버튼이다.
 */
class SettingsRulesTest {

    private fun labels(contactEmail: String) =
        SettingsRules.rows(contactEmail).map { it.label }

    // ── 행 목록 ──────────────────────────────────────────────────────

    /**
     * A 문서 3절 ⑤·⑨ `행` 칸의 순서를 그대로 못 박는다.
     *
     * 🔴 **`회원 탈퇴`가 마지막이라는 것도 이 목록이 지킨다.** 순서를 안 세면
     *    `활동 지역` 옆에 붙어도 아무 검사가 안 깨지는데, 그게 오누름의 자리다.
     */
    @Test
    fun 주소가_있으면_여덟_행이다() {
        assertEquals(
            listOf(
                "활동 지역", "알림 설정", "고객문의",
                "개인정보 처리방침", "이용약관", "위치기반서비스 이용약관",
                "앱 버전", "회원 탈퇴",
            ),
            labels("help@example.com"),
        )
    }

    /** 🔴 위 ①. **지금 빌드가 이 경우다.** */
    @Test
    fun 주소가_없으면_고객문의를_그리지_않는다() {
        assertEquals(
            listOf(
                "활동 지역", "알림 설정",
                "개인정보 처리방침", "이용약관", "위치기반서비스 이용약관",
                "앱 버전", "회원 탈퇴",
            ),
            labels(""),
        )
    }

    /**
     * 🔴 **주소가 없어도 법적 문서와 탈퇴는 나온다.** 이 셋이 `고객문의`처럼 조건부가 되면
     * **Play 심사에서 내려가는 빌드**가 만들어지는데, 화면은 정상으로 보인다.
     * 지금 빌드가 주소 없는 빌드라 이 경우가 곧 출시 빌드다.
     */
    @Test
    fun 주소가_없어도_문서와_탈퇴는_나온다() {
        val rows = SettingsRules.rows("")
        assertTrue(rows.contains(SettingsRules.Row.PRIVACY))
        assertTrue(rows.contains(SettingsRules.Row.TERMS))
        assertTrue(rows.contains(SettingsRules.Row.LOCATION_TERMS))
        assertTrue(rows.contains(SettingsRules.Row.DELETE_ACCOUNT))
    }

    /**
     * ⚠️ 공백만 들어온 것도 없는 것이다. `CONTACT_EMAIL= ` 처럼 값을 지우다 남긴
     * 한 칸이 **행을 되살린다** — 그러면 받는 사람이 `" "`인 메일이 열린다.
     */
    @Test
    fun 공백만_있는_주소도_없는_것이다() {
        assertFalse(SettingsRules.contactVisible("   "))
        assertEquals(labels(""), labels("   "))
    }

    /** 다른 세 행은 주소와 무관하다 — 주소가 없어서 설정 화면이 비면 안 된다. */
    @Test
    fun 주소가_없어도_나머지_행은_남는다() {
        val rows = SettingsRules.rows("")
        assertTrue(rows.contains(SettingsRules.Row.REGION))
        assertTrue(rows.contains(SettingsRules.Row.NOTIFICATION))
        assertTrue(rows.contains(SettingsRules.Row.VERSION))
    }

    // ── 누를 수 있는가 ───────────────────────────────────────────────

    /** 🔴 위 ②. `앱 버전`은 값 행이다. */
    @Test
    fun 앱_버전은_누를_수_없다() {
        assertFalse(SettingsRules.Row.VERSION.clickable)
    }

    /** `앱 버전`만 값 행이다 — 나머지 일곱은 전부 무언가를 연다. */
    @Test
    fun 앱_버전만_빼고_전부_누를_수_있다() {
        val notClickable = SettingsRules.Row.entries.filterNot { it.clickable }
        assertEquals(listOf(SettingsRules.Row.VERSION), notClickable)
    }

    /**
     * ⚠️ **행이 늘어나면 여기서 걸린다.** 새 행을 넣고 [SettingsRules.Row.clickable]과
     *    화면의 `when`을 안 고치면 **아무 일도 안 하는 줄**이 하나 생기는데, 화면은
     *    완벽하게 정상으로 보인다. 숫자를 박아 두는 이유가 그것이다.
     */
    @Test
    fun 행은_여덟뿐이다() {
        assertEquals(
            "행을 늘렸다면 화면 20-2의 when과 A 문서 3절 ⑤·⑨ 표도 같이 고쳐야 한다",
            8,
            SettingsRules.Row.entries.size,
        )
    }

    // ── 되돌릴 수 없는 행 ────────────────────────────────────────────

    /**
     * 🔴 **`회원 탈퇴` 하나만 빨간 글자다.** 이게 어긋나는 두 방향 다 화면은 정상이다 —
     * 탈퇴가 평범한 줄로 보이거나(오누름), 평범한 줄이 빨개져서(겁먹고 안 누른다).
     */
    @Test
    fun 되돌릴_수_없는_행은_탈퇴_하나다() {
        assertEquals(
            listOf(SettingsRules.Row.DELETE_ACCOUNT),
            SettingsRules.Row.entries.filter { it.destructive },
        )
    }

    /** 🔴 **맨 아래여야 한다**(위 순서 검사와 짝 · 오누름 방지). */
    @Test
    fun 탈퇴는_맨_아래다() {
        assertEquals(SettingsRules.Row.DELETE_ACCOUNT, SettingsRules.Row.entries.last())
        assertEquals(SettingsRules.Row.DELETE_ACCOUNT, SettingsRules.rows("").last())
        assertEquals(SettingsRules.Row.DELETE_ACCOUNT, SettingsRules.rows("a@b.c").last())
    }

    // ── 법적 문서 짝 ─────────────────────────────────────────────────

    /**
     * 🔴 **문서 3개가 서로 다른 문서를 열어야 한다.** 짝이 겹치면(복사-붙여넣기 사고)
     * `이용약관`을 눌렀는데 처방침이 나오는데, **화면은 완벽하게 정상으로 보인다** —
     * 제목까지 그 행의 이름이 아니라 문서 제목이라 맞게 보인다.
     */
    @Test
    fun 문서_행마다_다른_문서를_연다() {
        val docs = SettingsRules.Row.entries.mapNotNull { SettingsRules.legalDoc(it) }
        assertEquals(LegalDoc.entries.size, docs.size)
        assertEquals(LegalDoc.entries.toSet(), docs.toSet())
    }

    /** 문서 행이 아닌 행은 문서를 열지 않는다 — 열면 그 줄이 딴 화면으로 간다. */
    @Test
    fun 문서_행이_아니면_null이다() {
        val notDoc = SettingsRules.Row.entries.filter { SettingsRules.legalDoc(it) == null }
        assertEquals(
            listOf(
                SettingsRules.Row.REGION,
                SettingsRules.Row.NOTIFICATION,
                SettingsRules.Row.CONTACT,
                SettingsRules.Row.VERSION,
                SettingsRules.Row.DELETE_ACCOUNT,
            ),
            notDoc,
        )
    }

    /** 문서 행의 이름 = 그 문서의 제목. 어긋나면 누른 줄과 다음 화면 제목이 달라진다. */
    @Test
    fun 문서_행_이름은_문서_제목과_같다() {
        SettingsRules.Row.entries.forEach { row ->
            SettingsRules.legalDoc(row)?.let { assertEquals(row.label, it.title) }
        }
    }
}
