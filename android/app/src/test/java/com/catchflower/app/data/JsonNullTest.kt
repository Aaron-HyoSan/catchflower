package com.catchflower.app.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON `null` 읽기.
 *
 * 🔴 **이 파일의 절반은 값을 재지 않고 소스를 읽는다. 그게 이상해 보이는 것이 정상이고,
 *    이유가 있다 — 이 결함은 값으로 잴 수 없다.**
 *
 *    안드로이드 `org.json`과 테스트 클래스패스의 `org.json:json`은 **JSON `null`에
 *    다르게 답한다**(실측):
 *
 *    | 입력 `{"region_name":null}` | `optString` | `isNull` |
 *    |---|---|---|
 *    | 안드로이드 기기            | `"null"`    | `true`   |
 *    | JVM 테스트 (json:20250517) | `""`        | `true`   |
 *
 *    그래서 `optString(…).ifEmpty { null }`로 되돌려 놓아도 **JVM에서는 통과한다.**
 *    실제로 `"region_name":null`을 그대로 넣은 픽스처가 있었는데
 *    (`RankingServiceTest.지역_미설정을_알아본다`) **249개가 전부 초록인 채로**
 *    화면 20에 `null · 2026년 8월부터 함께`가 떴다.
 *
 *    돌연변이로도 못 잡는다 — 돌연변이 판정은 JVM 테스트를 돌리는 것이므로
 *    **이 결함을 심으면 여전히 초록이다.** 남은 방법이 소스를 읽는 것뿐이다.
 *
 * ⚠️ 이 테스트가 빨개지는 경우: 아래 목록의 파일에서 `optString(...).ifEmpty { null }`
 *    꼴을 다시 쓰면. 그때는 [stringOrNull]로 바꾼다.
 */
class JsonNullTest {

    // ── 판정 자체 (JVM에서도 옳게 나오는 부분만 잰다) ────────────────

    @Test
    fun 없는_키와_JSON_null과_빈_문자열이_모두_null이다() {
        val o = JSONObject("""{"a":null,"c":"","d":"연남동"}""")
        assertNull("JSON null을 문자열로 남겼다", o.stringOrNull("a"))
        assertNull("없는 키를 문자열로 만들었다", o.stringOrNull("absent"))
        assertNull("빈 문자열을 값으로 봤다", o.stringOrNull("c"))
        assertEquals("연남동", o.stringOrNull("d"))
    }

    /**
     * 🔴 **문자열 `"null"`은 지우지 않는다.**
     *
     * `"null"`을 걸러내는 방식으로 고치면 닉네임을 `null`로 지은 사용자의
     * **진짜 이름이 사라진다** — 서버가 준 값을 클라이언트가 삭제하는 것이고,
     * 화면에는 조회 실패와 똑같이 보여서 원인을 알 수 없다.
     * 그래서 [stringOrNull]은 `isNull`을 본다(두 구현이 일치하는 유일한 접근자).
     */
    @Test
    fun 닉네임이_진짜_null이라는_문자열이면_지우지_않는다() {
        val o = JSONObject("""{"nickname":"null"}""")
        assertEquals("사용자 닉네임을 지웠다", "null", o.stringOrNull("nickname"))
    }

    @Test
    fun 배열_원소도_같은_판정이다() {
        val arr = JSONArray("""[null,"","abc"]""")
        assertNull(arr.stringOrNull(0))
        assertNull(arr.stringOrNull(1))
        assertEquals("abc", arr.stringOrNull(2))
    }

    /**
     * 위 표의 두 번째 줄을 **테스트가 직접 확인한다.**
     *
     * 이 단정이 빨개지면 테스트 라이브러리가 안드로이드와 같아진 것이고, 그때는
     * 아래 소스 검사를 값 검사로 바꿔도 된다. 그때까지는 소스를 읽어야 한다.
     */
    @Test
    fun 테스트_라이브러리는_기기와_다르게_동작한다() {
        val o = JSONObject("""{"region_name":null}""")
        assertEquals(
            "JVM org.json이 기기처럼 \"null\"을 주기 시작했다 — 이 파일의 소스 검사를 재검토한다",
            "",
            o.optString("region_name"),
        )
        assertTrue("isNull은 두 구현이 일치해야 한다", o.isNull("region_name"))
    }

    // ── 되돌아가는 것을 소스로 막는다 ────────────────────────────────

    /**
     * `optString(...).ifEmpty { null }` 꼴이 **다시 생기지 않게** 한다.
     *
     * ⚠️ 서버·외부 API 응답을 읽는 파일만 본다. 우리가 쓴 파일(`UploadState`의 저장
     *    형식처럼)도 포함한다 — 파일이 손상되면 같은 일이 난다.
     */
    @Test
    fun 서버_응답을_optString_ifEmpty로_읽지_않는다() {
        val offenders = scanFor(OPT_STRING_IF_EMPTY)
        assertEquals(
            "JSON null이 문자열 \"null\"로 화면까지 올라간다 — stringOrNull을 쓴다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * `getString`으로 바로 읽는 것도 같은 문제다 — JSON `null`이면 안드로이드에서
     * `"null"`을 준다(`getString`은 `JSONException`을 던지지 않는다).
     *
     * ⚠️ **없으면 던져야 하는 필수 칸은 예외다.** `season_start`처럼 서버가 항상
     *    주는 값은 없을 때 던지는 것이 맞다([RankingService.seasonSummary] 주석:
     *    "없으면 그건 형식이 바뀐 것이다"). 그래서 이 검사는 **`?:`나 `.ifEmpty`로
     *    기본값을 만들어 붙인 경우만** 잡는다 — 그게 조용히 틀리는 꼴이다.
     */
    @Test
    fun getString에_기본값을_붙여_읽지_않는다() {
        val offenders = scanFor(GET_STRING_IF_EMPTY)
        assertEquals(
            "getString에 기본값을 붙였다 — JSON null이 \"null\"로 남는다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * 소스에서 [pattern]에 걸리는 줄을 찾는다. **주석 줄은 뺀다.**
     *
     * 🔴 **주석을 빼는 것이 이 검사의 약점이자 필요한 일이다.** 왜 이 꼴을 쓰면 안
     *    되는지 설명하는 주석에 그 꼴이 그대로 적혀 있어서(이 파일과 [stringOrNull],
     *    `RankingService.myRegion`이 그렇다) 안 빼면 **설명을 쓰는 것 자체가 위반**이
     *    된다. 그러면 다음 사람은 검사를 지우거나 설명을 지운다 — 둘 다 나쁘다.
     *
     * ⚠️ **`SharedPreferences.getString`은 안 잡는다.** 그건 JSON이 아니라 안드로이드
     *    prefs이고, 없을 때 진짜 `null`을 준다(`AuthService`가 그렇게 읽는다).
     *    거기서 `.ifEmpty { null }`은 옳다 — 빈 문자열로 저장된 옛 값을 걸러낸다.
     *    그래서 수신자가 `prefs()`/`p`인 호출은 대상이 아니다.
     */
    private fun scanFor(pattern: Regex): List<String> {
        val offenders = mutableListOf<String>()
        for (file in mainSources()) {
            // 🔴 **[stringOrNull] 자신은 예외다 — 그 안에서는 `optString`이 옳다.**
            //    `isNull`을 **먼저** 보고 통과한 뒤에 부르므로 JSON null이 이미 걸러졌다.
            //    이 한 곳이 유일하게 `optString`을 직접 부를 자리이고, 그래서
            //    `stringOrNull_은_isNull을_먼저_본다`가 그 순서를 따로 고정한다.
            if (file.name == "JsonNull.kt") continue
            file.readLines().forEachIndexed { i, raw ->
                val line = raw.trim()
                // 주석 줄은 건너뛴다 — 위 주석의 이유.
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                if (!pattern.containsMatchIn(raw)) return@forEachIndexed
                // prefs 읽기는 JSON이 아니다.
                if (Regex("""\b(prefs\(\)|p)\.getString""").containsMatchIn(raw)) return@forEachIndexed
                offenders += "${file.name}:${i + 1}"
            }
        }
        return offenders
    }

    /**
     * 🔴 **검사가 파일을 하나도 못 읽으면 위 두 테스트는 거짓 초록이다.**
     *
     * 경로가 바뀌거나 작업 디렉터리가 달라지면 `mainSources()`가 빈 목록을 주고,
     * `emptyList == emptyList`로 **통과한다.** 그게 이 프로젝트에서 이미 겪은
     * 거짓 초록의 모양이다. 그래서 세는 것을 따로 고정한다.
     */
    @Test
    fun 검사가_실제로_소스를_읽었다() {
        val files = mainSources()
        assertTrue("소스를 한 개도 못 읽었다 — 위 검사 두 개가 거짓 초록이다", files.size >= 20)
        assertTrue(
            "RankingService를 못 읽었다 — 결함이 실제로 있던 파일이다",
            files.any { it.name == "RankingService.kt" },
        )
        // 검사가 정말 이 꼴을 잡는지 확인한다. 잡는 능력이 없으면 위는 무의미하다.
        assertTrue(
            "검사 정규식이 결함 꼴을 못 잡는다",
            OPT_STRING_IF_EMPTY.containsMatchIn("""val x = o.optString("k").ifEmpty { null }"""),
        )
        assertTrue(
            "물음표가 끼면 못 잡는다 — `?.ifEmpty`가 실제로 있던 꼴이다",
            OPT_STRING_IF_EMPTY.containsMatchIn("""o?.optString("k")?.ifEmpty { null }"""),
        )
        assertTrue(
            GET_STRING_IF_EMPTY.containsMatchIn("""getString(K).ifEmpty { null }"""),
        )
    }

    /**
     * [stringOrNull]이 **`isNull`을 먼저 보는지** 소스로 고정한다.
     *
     * 🔴 **위 검사에서 `JsonNull.kt`를 제외했으므로 이 파일만은 따로 지켜야 한다.**
     *    안을 `optString(key).ifEmpty { null }`로 바꿔 놓으면 **모든 호출부가 조용히
     *    결함으로 되돌아가는데** 스캐너는 그 파일을 안 보고, 값 검사는 JVM에서
     *    통과한다(위 표). 그러면 아무 것도 빨개지지 않는다.
     */
    @Test
    fun stringOrNull은_isNull을_먼저_본다() {
        val src = mainSources().firstOrNull { it.name == "JsonNull.kt" }
        assertTrue("JsonNull.kt를 못 읽었다 — 이 테스트가 거짓 초록이다", src != null)
        val body = src!!.readLines()
            .filterNot { val t = it.trim(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
            .joinToString("\n")
        assertTrue(
            "isNull(key) 검사가 사라졌다 — JSON null이 문자열 \"null\"로 새어 나간다",
            Regex("""isNull\s*\(\s*key\s*\)""").containsMatchIn(body),
        )
        assertTrue(
            "배열 쪽 isNull(index) 검사가 사라졌다",
            Regex("""isNull\s*\(\s*index\s*\)""").containsMatchIn(body),
        )
    }

    private companion object {
        /** `optString(...)`에 `.ifEmpty`를 붙인 꼴. `?.` 형태도 잡는다. */
        val OPT_STRING_IF_EMPTY = Regex("""optString\s*\([^)]*\)\s*\??\.ifEmpty""")
        val GET_STRING_IF_EMPTY = Regex("""getString\s*\([^)]*\)\s*\??\.ifEmpty""")
    }

    /**
     * `app/src/main/java` 아래 `.kt` 전부.
     *
     * ⚠️ 테스트 작업 디렉터리는 `app/`이다(gradle 기본). 못 찾으면 상위로 한 번 올라가
     *    본다 — IDE에서 저장소 루트로 도는 경우가 있다.
     */
    private fun mainSources(): List<File> {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("android/app/src/main/java"),
        )
        val root = candidates.firstOrNull { it.isDirectory } ?: return emptyList()
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
