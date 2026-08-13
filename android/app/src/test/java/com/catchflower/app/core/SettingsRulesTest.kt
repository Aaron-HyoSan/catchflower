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

    /** A 문서 3절 ⑤ `행` 칸의 순서를 그대로 못 박는다. */
    @Test
    fun 주소가_있으면_네_행이다() {
        assertEquals(
            listOf("활동 지역", "알림 설정", "고객문의", "앱 버전"),
            labels("help@example.com"),
        )
    }

    /** 🔴 위 ①. **지금 빌드가 이 경우다.** */
    @Test
    fun 주소가_없으면_고객문의를_그리지_않는다() {
        assertEquals(listOf("활동 지역", "알림 설정", "앱 버전"), labels(""))
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

    @Test
    fun 나머지_세_행은_누를_수_있다() {
        assertTrue(SettingsRules.Row.REGION.clickable)
        assertTrue(SettingsRules.Row.NOTIFICATION.clickable)
        assertTrue(SettingsRules.Row.CONTACT.clickable)
    }

    /**
     * ⚠️ **행이 늘어나면 여기서 걸린다.** 새 행을 넣고 [SettingsRules.Row.clickable]과
     *    화면의 `when`을 안 고치면 **아무 일도 안 하는 줄**이 하나 생기는데, 화면은
     *    완벽하게 정상으로 보인다. 숫자를 박아 두는 이유가 그것이다.
     */
    @Test
    fun 행은_넷뿐이다() {
        assertEquals(
            "행을 늘렸다면 화면 20-2의 when과 A 문서 3절 ⑤ 표도 같이 고쳐야 한다",
            4,
            SettingsRules.Row.entries.size,
        )
    }
}
