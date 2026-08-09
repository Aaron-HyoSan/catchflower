package com.catchflower.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면에 뜨는 **버튼 라벨이 전부 A 문서에 있는가.**
 *
 * ## 왜 이 테스트가 있나
 *
 * 두 번 같은 결함을 배포했다:
 * - 랭킹에 `[개발] 친구 없는 화면` 버튼이 **사용자 화면에 떠 있었다**(2026-08-09 · (35)).
 * - 도감 헤더에 `0종 보기` 개발 토글이 **3종을 모은 화면에 떠 있었다**((38)).
 *   주석에 `출시 전에 뺀다`라고 적혀 있었는데도 남았다.
 *
 * 🔴 **개발용 버튼은 잘 동작하기 때문에 아무 증상이 없다.** 크래시도, 틀린 숫자도,
 * 이상한 배치도 없다 — 화면을 봐도 "이건 내가 넣은 것"이라는 걸 알아야만 보인다.
 * 스크린샷 검토로는 못 잡고, 값을 재는 테스트로도 못 잡는다. **소스를 읽어야 한다**
 * ([com.catchflower.app.ui.onboarding.PermissionCopyTest]와 같은 층).
 *
 * ## 왜 `[개발]` 같은 낱말 목록으로 막지 않나
 *
 * `0종 보기`에는 개발이라는 표시가 **한 글자도 없다.** 금지어 목록은 다음 토글이
 * 어떤 이름을 달고 올지 모르므로 **원리상 못 막는다.** 대신 반대로 잰다 —
 * **A 문서에 있는 문구만 버튼이 될 수 있다.** 프로젝트 규칙(`UI 문구를 새로 쓰지
 * 않는다`)을 그대로 검사로 만든 것이고, 개발용 토글은 A 문서에 있을 수 없으니
 * 자동으로 걸린다.
 *
 * ## 문구를 추가해야 하면
 *
 * **A 문서를 먼저 고친다.** 이 테스트가 빨개지면 순서를 어긴 것이다
 * (2026-08-09에 `나중에 하기`·`도감 보기`·`뒤로` 3개가 이 검사로 발견돼 3절에 추가됐다).
 */
class ButtonLabelSourceTest {

    /**
     * 이 파일이 보는 버튼 컴포저블.
     *
     * ⚠️ **새 버튼 컴포넌트를 만들면 여기 추가한다.** 안 하면 그 버튼은 검사에서
     *    빠지는데 결과는 초록이다 — 아래 [버튼_컴포넌트_목록이_실제와_같다]가 그걸 막는다.
     */
    private val buttonComposables = listOf(
        "CfPrimaryButton",
        "CfSecondaryButton",
        "CfGhostButton",
        "CfTextButton",
        // ⚠️ 처음 이 목록을 손으로 적을 때 이걸 빠뜨렸다. 아래
        //    [버튼_컴포넌트_목록이_실제와_같다]가 그 자리에서 잡았다 — 목록을 손으로
        //    관리하는 검사는 **목록 자체를 검사**해야 의미가 있다.
        "CfSmallButton",
    )

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "디자이너_업무").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError(
            "프로젝트 루트를 못 찾았다. 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 버튼 검증이 조용히 사라진다",
        )
    }

    private val aDoc: String by lazy {
        val f = File(projectRoot, "디자이너_업무/A_문구·버튼_스펙.md")
        if (!f.exists()) throw AssertionError("A 문서를 못 찾았다: ${f.path}")
        f.readText()
    }

    /**
     * A 문서가 **승인한 문구** 집합.
     *
     * 🔴 **`aDoc.contains(label)`로 검사하면 안 된다.** 처음 그렇게 썼는데
     *    돌연변이(`0종 보기` 버튼을 되붙이기)가 **살아남았다** — 그 버튼을 지운 경위를
     *    설명하는 산문이 A 문서 3절에 있어서, 지운 버튼의 이름이 **문서 안에 글자로
     *    존재했기 때문**이다. 즉 문서에 "이건 지웠다"라고 적으면 그게 승인이 된다.
     *    검사가 스스로를 무력화하는 모양이다.
     *
     * 그래서 **표 셀만** 승인으로 본다(2절·3절 문구표의 `| 위치 | 문구 |` 형식).
     * 산문·주의문·`왜 없었나` 설명은 승인이 아니다.
     *
     * 셀 안에서 꺼내는 것: 셀 전체 · `/`로 나눈 조각(`설정으로 이동` / `나중에`) ·
     * 백틱 안 · 굵게 표시 안. A 문서가 실제로 쓰는 네 가지 표기다.
     */
    private val approvedCopy: Set<String> by lazy {
        val out = mutableSetOf<String>()
        for (raw in aDoc.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("|") || line.startsWith("|---")) continue
            for (cell in line.trim('|').split("|")) {
                val c = cell.trim()
                val candidates = buildList {
                    add(c)
                    addAll(c.split("/"))
                    Regex("`([^`]+)`").findAll(c).forEach { add(it.groupValues[1]) }
                    Regex("""\*\*([^*]+)\*\*""").findAll(c).forEach { add(it.groupValues[1]) }
                }
                candidates.map { it.replace("**", "").trim().trim('`').trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { out.add(it) }
            }
        }
        out
    }

    private val mainSources: List<File> by lazy {
        val root = File(projectRoot, "android/app/src/main/java")
        if (!root.isDirectory) throw AssertionError("main 소스 폴더를 못 찾았다: ${root.path}")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * 픽스처가 비어 있지 않은가.
     *
     * ⚠️ **이게 없으면 파일 0개를 읽고 초록이다.** 소스를 읽는 테스트의 기본 실패
     *    모양이고((22)에서 겪었다), 경로가 바뀌는 순간 조용히 일어난다.
     */
    @Test
    fun 소스를_실제로_읽었다() {
        assertTrue("main 소스를 한 개도 못 읽었다", mainSources.size > 30)
        assertTrue("A 문서가 너무 짧다 — 다른 파일을 읽었다", aDoc.length > 10_000)
        // 표를 실제로 파싱했는가. 0개를 승인 집합으로 두면 아래 검사가 **전부** 빨개져서
        // 오히려 눈에 띄지만, 반대로 파싱이 너무 느슨해지는 쪽은 조용하다.
        assertTrue("A 문서 표에서 문구를 못 꺼냈다 (${approvedCopy.size}개)", approvedCopy.size > 200)
        assertTrue("`내 꽃 도감`이 승인 집합에 없다 — 표 파싱이 틀렸다", "내 꽃 도감" in approvedCopy)
        assertTrue("버튼 라벨을 한 개도 못 찾았다", labels().isNotEmpty())
    }

    /**
     * 🔴 **산문에 적힌 문구는 승인이 아니다.**
     *
     * `0종 보기`는 A 문서 3절 산문에 **글자로 존재한다**(지운 경위를 적어 뒀다).
     * 그런데 표 셀에는 없으므로 승인이 아니어야 한다.
     *
     * **왜 이 단정을 따로 두는가.** 표 셀 제한을 풀어 보는 돌연변이는 **혼자서는
     * 살아남는다** — 지금 그 버튼이 코드에 없으니 잘못 승인할 대상이 없어서
     * 아무것도 안 터진다(실제로 돌연변이가 살아남았다). 즉 [모든_버튼_문구가_A문서에_있다]는
     * **미래에 누군가 그 버튼을 되붙일 때만** 이 결함을 드러낸다. 여기서 파싱 규칙
     * 자체를 직접 고정한다.
     */
    @Test
    fun 산문에_적힌_문구는_승인이_아니다() {
        assertTrue(
            "전제가 깨졌다 — A 문서 산문에서 `0종 보기` 언급이 사라졌다. " +
                "이 테스트는 그 언급을 표본으로 쓴다(3절 `코드에 있는데 2절 표에 없던 버튼 3개`)",
            aDoc.contains("0종 보기"),
        )
        assertTrue(
            "산문에 적힌 `0종 보기`가 승인 문구로 읽혔다 — 표 셀만 봐야 한다. " +
                "이대로면 **버튼을 지운 경위를 문서에 적는 것이 그 버튼의 승인**이 된다",
            "0종 보기" !in approvedCopy,
        )
    }

    /**
     * 소스에서 `text = "…"`인 버튼 라벨을 모은다.
     *
     * 문자열 템플릿(`"${'$'}{n}종 보기"`)은 뺀다 — 값이 런타임에 정해져서 문서와
     * 글자로 비교할 수 없다. 그건 [com.catchflower.app.data.DexFilterTest]처럼
     * 값을 만드는 함수 쪽에서 잰다.
     *
     * @return 라벨 → 그 라벨이 나온 파일 이름들
     */
    private fun labels(): Map<String, Set<String>> {
        val found = mutableMapOf<String, MutableSet<String>>()
        for (file in mainSources) {
            // 주석은 뺀다. 지운 버튼을 주석에 기록해 두는 관행이 있어서
            // (`여기 있던 [개발] 버튼을 지웠다`) 안 빼면 지운 것이 다시 걸린다.
            val body = file.readText().lineSequence()
                .map { it.substringBefore("//") }
                .joinToString("\n")
            for (composable in buttonComposables) {
                Regex(Regex.escape(composable) + """\s*\(""").findAll(body).forEach { m ->
                    val segment = body.substring(m.range.last, minOf(body.length, m.range.last + 300))
                    Regex("""text\s*=\s*"([^"$]*)"""").find(segment)?.let {
                        found.getOrPut(it.groupValues[1]) { mutableSetOf() }.add(file.name)
                    }
                }
            }
        }
        return found
    }

    /**
     * 🔴 **A 문서에 없는 버튼 문구가 없다.**
     *
     * 빨개지는 경우: 개발용 토글을 넣거나, 문구를 코드에서 새로 지어내면.
     * 실제로 `0종 보기`(개발 토글)와 문구 3개를 이 검사로 찾았다.
     */
    @Test
    fun 모든_버튼_문구가_A문서에_있다() {
        val missing = labels().filterKeys { it !in approvedCopy }
        assertEquals(
            "A 문서에 없는 버튼 문구다. **A 문서를 먼저 고친다** — 개발용 버튼이면 지운다: " +
                missing.entries.joinToString { "${it.key} (${it.value.joinToString()})" },
            emptyMap<String, Set<String>>(),
            missing,
        )
    }

    /**
     * 버튼 컴포넌트 목록이 실제 코드와 같은가.
     *
     * ⚠️ **[buttonComposables]가 낡으면 위 검사가 조용히 좁아진다.** 새 버튼
     *    컴포넌트를 만들고 여기 추가를 잊으면 그 버튼들은 검사 밖인데 결과는 초록이다 —
     *    "검사가 있다"는 믿음만 남는다. 그래서 목록 자체를 소스에서 다시 센다.
     */
    @Test
    fun 버튼_컴포넌트_목록이_실제와_같다() {
        val declared = mainSources
            .filter { it.name == "Buttons.kt" || it.name == "Chips.kt" }
            .flatMap { f ->
                Regex("""fun (Cf\w*Button)\(""").findAll(f.readText()).map { it.groupValues[1] }
            }
            .toSet()
        assertTrue("버튼 컴포넌트를 한 개도 못 찾았다 — 파일이 옮겨졌다", declared.isNotEmpty())
        assertEquals(
            "버튼 컴포넌트가 새로 생겼는데 buttonComposables에 없다 — 그 버튼들은 검사 밖이다",
            emptySet<String>(),
            declared - buttonComposables.toSet(),
        )
    }

    /**
     * 개발용 토글이 ViewModel에 남아 있지 않은가.
     *
     * 위 검사는 **버튼**을 본다. 토글을 부르는 함수만 남으면 버튼이 없으니 통과하는데,
     * 그 상태는 다음 사람이 버튼을 다시 붙이기 쉬운 상태다. `forceEmptyState`는
     * 실제로 그렇게 만들어졌다.
     */
    @Test
    fun 개발용_상태_토글이_남아있지_않다() {
        val offenders = mainSources.filter { f ->
            val body = f.readText().lineSequence()
                .map { it.substringBefore("//") }
                .joinToString("\n")
            Regex("""\b(force[A-Z]\w*|fake[A-Z]\w*|debug[A-Z]\w*)\s*(by|=)""").containsMatchIn(body)
        }.map { it.name }
        assertEquals(
            "개발용 상태 플래그가 프로덕션 소스에 남아 있다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }
}
