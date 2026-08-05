package com.catchflower.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Supabase URL 정규화 테스트.
 *
 * **왜 필요한가**: 오너가 준 URL은 `https://xxx.supabase.co/rest/v1/`였다.
 * 그대로 쓰면 클라이언트가 경로를 또 붙여 `/rest/v1/rest/v1/flowers`가 되고,
 * 서버는 **404**를 준다. 404는 "URL이 틀렸다"가 아니라 "테이블이 없다"로 읽히기 때문에
 * 스키마를 몇 번이고 다시 만들면서 원인을 못 찾는다 — 화면에는 "불러올 수 없어요"만 뜬다.
 *
 * 이건 화면으로 확인할 수 없다. 설정 파일에 무엇이 들어와도 루트가 나오는지 여기서 고정한다.
 */
class AppSecretsTest {

    private fun norm(raw: String) = AppSecrets.normalizeSupabaseUrl(raw)

    private val root = "https://ngfkkazyvbbhrcznqkar.supabase.co"

    @Test
    fun `루트 URL은 그대로 둔다`() {
        assertEquals(root, norm(root))
    }

    @Test
    fun `오너가 준 형태인 rest v1 슬래시를 벗긴다`() {
        assertEquals(root, norm("$root/rest/v1/"))
        assertEquals(root, norm("$root/rest/v1"))
    }

    @Test
    fun `다른 서비스 경로도 벗긴다`() {
        assertEquals(root, norm("$root/auth/v1/"))
        assertEquals(root, norm("$root/storage/v1"))
    }

    @Test
    fun `끝의 슬래시와 공백을 벗긴다`() {
        // local.properties에 붙여 넣을 때 흔한 형태다.
        assertEquals(root, norm("  $root/  "))
        assertEquals(root, norm("$root///"))
    }

    @Test
    fun `빈 값은 빈 값으로 남는다`() {
        // 키가 없을 때 `hasSupabase`가 false가 되어야 한다 — 여기서 "/"라도 만들면
        // 빈 문자열이 아니게 되어 **서버 기능이 켜졌다고 착각한다.**
        assertEquals("", norm(""))
        assertEquals("", norm("   "))
        assertEquals("", norm("/"))
    }
}
