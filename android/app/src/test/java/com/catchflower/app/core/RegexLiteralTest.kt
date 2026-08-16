package com.catchflower.app.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱 소스의 **정규식 리터럴이 안드로이드 엔진에서도 컴파일되는가** — 중괄호만 본다.
 *
 * ## 🔴 왜 이 검사가 생겼나
 *
 * `LegalDocs`의 `Regex("\\{\\{[^}]+}}")`가 **데스크톱 JVM에서는 되고 기기에서는
 * 죽었다**(2026-08-17 · 화면 20-3을 여는 순간 프로세스 종료). 안드로이드의
 * `java.util.regex`는 ICU 엔진이라 **짝 없는 `}`를 문법 오류로 본다.**
 * OpenJDK는 그것을 리터럴 `}`로 받아 주므로 **JVM 테스트로는 원리상 못 잡는다.**
 *
 * ## ⚠️ 이 검사가 무엇이고 무엇이 아닌가
 *
 * 이것은 **선별기(pre-filter)** 다. 재는 것은 중괄호 한 가지뿐이고, "이 패턴이 ICU에서
 * 컴파일된다"를 증명하지 않는다. **증명하는 층은 기기다** —
 * `androidTest`의 `LegalDocsDeviceTest`가 실제로 컴파일해서 잰다.
 * 그래도 이 검사를 두는 이유: 새 정규식을 쓰는 사람이 계측 테스트를 돌리지 않아도
 * `./gradlew test`에서 걸린다(그리고 이 사고는 그렇게 놓쳤다).
 *
 * ## 🔴 계측기를 먼저 잰다
 *
 * 소스를 읽어서 판단하는 검사는 **찾지 못했을 때도 초록**이다(정규식이 아무것도
 * 안 잡으면 "위반 없음"과 구별되지 않는다). 그래서 [계측기_대조군]이
 * ① 알려진 나쁜 패턴을 **반드시 잡고** ② 알려진 좋은 패턴을 **통과시키고**
 * ③ 실제로 읽은 소스 파일이 **0개가 아닌지** 를 먼저 확인한다.
 */
class RegexLiteralTest {

    /** 저장소 안에서 앱 소스 폴더를 찾는다(테스트 작업 디렉터리는 `android/app`이다). */
    private val mainSources: List<File> by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "법무").isDirectory) dir = dir.parentFile
        val root = requireNotNull(dir) { "저장소 루트를 못 찾았다(법무/ 폴더 기준)" }
        File(root, "android/app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    /**
     * `Regex("…")` · `Regex("""…""")` · `"…".toRegex()` 안의 패턴 문자열.
     *
     * 🔴 **정규식으로 뽑지 않는다.** `Regex("((?:[^"\\]|\\.)*)")` 꼴로 썼더니
     *    긴 소스에서 **역추적이 폭발해 `StackOverflowError`**가 났다(실측). 검사기가
     *    스스로 죽는 것은 초록보다 낫지만(빨개진다), 손으로 훑는 쪽이 예측 가능하다.
     */
    private fun patternsIn(source: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3)
                    if (end < 0) return out
                    if (source.substring(0, i).endsWith("Regex(")) out += source.substring(i + 3, end)
                    if (source.startsWith(".toRegex()", end + 3)) out += source.substring(i + 3, end)
                    i = end + 3
                }
                source[i] == '"' -> {
                    var j = i + 1
                    while (j < source.length && source[j] != '"' && source[j] != '\n') {
                        j += if (source[j] == '\\') 2 else 1
                    }
                    if (j >= source.length || source[j] != '"') { i++; continue }
                    val body = source.substring(i + 1, j)
                    if (source.substring(0, i).endsWith("Regex(") ||
                        source.startsWith(".toRegex()", j + 1)
                    ) {
                        out += unescapeKotlin(body)
                    }
                    i = j + 1
                }
                else -> i++
            }
        }
        return out
    }

    /** 코틀린 단일 인용 문자열의 `\\` → 실제 정규식이 받는 `\`. 그 외 escape는 그대로 둔다. */
    private fun unescapeKotlin(literal: String): String = literal.replace("\\\\", "\\")

    /**
     * 문자 클래스 **밖에** 짝 없는 중괄호가 있으면 그 위치를 돌려준다(없으면 null).
     *
     * 허용하는 것: escape된 `\{` `\}` · 수량자 `{3}` `{0,14}` `{2,}` · 클래스 안의 아무 것.
     */
    private fun unpairedBrace(pattern: String): String? {
        var i = 0
        var inClass = false
        val quantifier = Regex("""^\{\d+(,\d*)?}""")
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++  // 다음 글자는 escape된 것이다
                inClass && c == ']' -> inClass = false
                inClass -> Unit
                c == '[' -> inClass = true
                c == '{' -> {
                    val q = quantifier.find(pattern.substring(i))
                        ?: return "짝 없는 `{` (위치 $i)"
                    i += q.value.length - 1
                }
                c == '}' -> return "짝 없는 `}` (위치 $i) — `\\}`로 escape한다"
            }
            i++
        }
        return null
    }

    @Test
    fun `계측기 대조군`() {
        // ① 사고를 낸 그 패턴을 잡아야 한다.
        assertTrue(
            "실제로 앱을 죽인 패턴을 못 잡는다면 이 검사는 의미가 없다",
            unpairedBrace("""\{\{[^}]+}}""") != null,
        )
        // ② 정상 패턴을 잡으면 안 된다(거짓 빨강은 다음 사람이 검사를 지우게 만든다).
        for (good in listOf(
            """\{\{[^}]+\}\}""",
            """\d{4}-\d{2}""",
            """^(제\d+조|\d+\.|\(\d+\)|\d+\)|[가-힣]\.|[-*•]|\*)\s""",
            """^[^:\s][^:]{0,14}:\s""",
            """[\n\r\t]""",
            """a{2,}""",
        )) {
            assertEquals("정상 패턴을 잡았다: $good", null, unpairedBrace(good))
        }
        // ③ 소스를 정말 읽었는가. 0개면 위 검사는 아무것도 재지 않는다.
        assertTrue("앱 소스를 못 읽었다", mainSources.size > 50)
        // ④ 🔴 **개수가 아니라 내용으로 확인한다.** 추출기가 인용부호를 잘못 세면
        //    조용히 적게 찾는데(= 검사가 꺼진다), 개수 기준은 그것을 "몇 개는 찾았다"로
        //    통과시킨다. 그래서 **소스에 실제로 있는 패턴 두 개를 이름으로 요구한다.**
        //    ⚠️ 여기 쓸 패턴은 **이 검사가 고칠 대상이 아닌 것**을 고른다. 검사 대상인
        //    `LegalDocs`의 자리표시자 패턴을 쓰면, 그 줄을 되돌려 보는 대조 실험에서
        //    **계측기 대조군까지 같이 빨개져** 어느 쪽이 걸린 것인지 흐려진다(실측).
        //    아래 둘은 **뽑는 경로가 다르다**: 앞은 단일 인용("…"), 뒤는 삼중 인용.
        val found = mainSources.flatMap { patternsIn(it.readText()) }
        for (known in listOf("[\\n\\r\\t]", """\d{4}-\d{2}""")) {
            assertTrue(
                "소스에 있는 정규식 `$known`을 못 찾았다 — 추출기가 고장났다(찾은 것 ${found.size}개)",
                known in found,
            )
        }
    }

    @Test
    fun `앱 소스의 정규식에 짝 없는 중괄호가 없다`() {
        val bad = mainSources.flatMap { f ->
            patternsIn(f.readText()).mapNotNull { p ->
                unpairedBrace(p)?.let { "${f.name}: `$p` → $it" }
            }
        }
        assertEquals(
            "안드로이드(ICU) 정규식은 짝 없는 중괄호를 문법 오류로 본다 — " +
                "기기에서만 `PatternSyntaxException`으로 죽는다:\n" + bad.joinToString("\n"),
            emptyList<String>(),
            bad,
        )
    }
}
