package com.catchflower.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면에 뜨는 **모든 문구**가 A 문서에 있는가 — 버튼만이 아니라 `Text()` 전부.
 *
 * ## 왜 이 파일이 필요했나
 *
 * 🔴 **화면 16을 만들면서 `댓글`이라는 섹션 제목을 지어냈다**(2026-08-12). 2절 16번
 *    표에도 와이어프레임 16에도 없는 말인데 **아무것도 빨개지지 않았다.**
 *    [ButtonLabelSourceTest]는 **버튼만** 본다.
 *
 * 그래서 소스의 `Text("…")` 리터럴 전량을 대조했더니 **한글 79개 중 9개가 표에 없었다.**
 * 2026-08-09에 버튼 3개를 찾은 것과 같은 사건이고, 그때 만든 검사가 **한 종류만
 * 막았기 때문에** 나머지가 그대로 남아 있었다:
 *
 * ```
 * 어느 꽃인가요? · 가장 비슷한 꽃을 골라 주세요 · 혹시 이 꽃인가요?   (화면 09 변형)
 * 이 꽃이에요 · 이거예요                                        (화면 09 후보 행)
 * 꽃 정보를 찾을 수 없어요                                       (화면 05)
 * 초대 링크로도 친구가 될 수 있어요 · 링크를 받은 지인이 …             (화면 19)
 * 활동 지역은 6개월에 한 번만 변경할 수 있어요.                       (화면 02)
 * ```
 *
 * 전부 3절 `코드에 있는데 표에 없던 버튼 아닌 문구 9개`에 적었다(4절 12·13번이
 * 톤 검토 문의다). `댓글`은 문서에 넣지 않고 **코드에서 지웠다.**
 *
 * ## 왜 버튼 검사에 합치지 않나
 *
 * [ButtonLabelSourceTest]는 **누를 수 있는 것**만 본다 — 그래서 `개발용 상태 토글`·
 * `죽은 버튼` 같은 버튼 고유의 단정을 함께 들고 있다. 여기서 재는 것은 다른 위험이다:
 * **문구를 지어내는 것.** 대상 집합이 다르고(전자 27개 · 후자 79개) 실패 메시지가
 * 가리키는 곳도 다르다. 합치면 한쪽 실패가 다른 쪽 이유로 읽힌다.
 *
 * ## 이 검사가 못 잡는 것 (적어 두지 않으면 "다 막았다"로 읽힌다)
 *
 * 🔴 **`Text(변수)`는 사정거리 밖이다.** 문구를 `val x = "…"`에 담아 두거나
 *    `buildAnnotatedString`으로 조립하면 **여전히 초록**이다.
 * 🔴 **`contentDescription`도 밖이다** — 그건 화면에 안 보이지만 스크린리더가 읽는다.
 * ⚠️ 그래서 이 검사는 "문구를 다 막았다"가 아니라 **"리터럴로 지어내는 가장 흔한
 *    경로를 막았다"** 다. 아래 [사정거리를_문서에_적어_두었다]가 그 한계를 문서와 묶는다.
 */
class CopySourceTest {

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "디자이너_업무").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError(
            "프로젝트 루트를 못 찾았다. 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 문구 검증이 조용히 사라진다",
        )
    }

    private val aDoc: String by lazy {
        val f = File(projectRoot, "디자이너_업무/A_문구·버튼_스펙.md")
        if (!f.exists()) throw AssertionError("A 문서를 못 찾았다: ${f.path}")
        f.readText()
    }

    /**
     * A 문서가 **승인한 문구** 집합. 표 셀만 본다.
     *
     * 🔴 **산문은 승인이 아니다.** [ButtonLabelSourceTest]에서 그 함정에 이미 걸렸다 —
     *    "이 버튼은 지웠다"라고 문서에 적으면 그게 승인이 되었다.
     *
     * 셀에서 꺼내는 표기 — A 문서가 실제로 쓰는 형태다:
     * 셀 전체 · `/`와 ` · `로 나눈 조각 · 백틱 안 · 굵게 안 ·
     * **뒤 괄호(주석)를 떼어 낸 나머지**(`이곳에서 발견된 꽃 (우측 \`내 도감 기준\`)` →
     * `이곳에서 발견된 꽃`).
     *
     * ⚠️ 마지막 하나가 없으면 **정상 문구 3개가 위반으로 잡힌다** — 처음 그렇게 썼고
     *    `사람들의 기록`·`이곳에서 발견된 꽃`·`발견 장소`가 걸렸다. `IconAssetTest`에서
     *    두 번 겪은 것과 같다: **검사기는 대조군으로 먼저 잰다.**
     *
     * ## 🔴 여기서 **하지 않는** 것 두 가지 (돌연변이로 정해졌다)
     *
     * ① **셀 안의 백틱을 떼어 낸 나머지를 승인하지 않는다.** 처음에 그렇게 썼더니
     *    ``| 반응 줄 | 좋아요 `12` · 댓글 `3` |``이 ` · `로 갈리고 백틱이 떨어져
     *    **`댓글`이 승인됐다** — 즉 이 검사를 만든 이유였던 그 문구가 통과했다
     *    (돌연변이 실측: `BUILD SUCCESSFUL`).
     *    ⚠️ 그런데 **떼지 않으면** ``| 시즌 배너 | … / `47` 일 남음 |``에서
     *    `일 남음`을 못 찾아 정상 문구가 걸린다. **두 셀의 글자 모양이 같다**
     *    (`<라벨> \`<값>\``) — 어느 규칙도 한쪽만 고를 수 없다.
     *    → 그래서 반쪽 3개는 [labelHalves]에 **손으로 적는다.** 모양으로 추측하지 않는다.
     *
     * ② **취소선 셀(`~~…~~`)은 건너뛴다.** 3절 9개 표의 마지막 줄이
     *    ``~~섹션 제목 `댓글`~~ | **없앴다**``인데, 백틱 추출이 **지운 문구를 되살려
     *    승인했다.** 지운 것을 문서에 적으면 그 기록이 승인이 되는 사고를 **세 번째**
     *    겪었다(`ButtonLabelSourceTest` 산문 · `IconAssetTest` 주석).
     */
    private val approvedCopy: Set<String> by lazy {
        val out = mutableSetOf<String>()
        for (raw in aDoc.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("|") || line.startsWith("|---")) continue
            for (cell in line.trim('|').split("|")) {
                val c = cell.trim()
                // 🔴 ② 지운 기록은 승인이 아니다.
                if ("~~" in c) continue
                val pieces = buildList {
                    add(c)
                    addAll(c.split("/"))
                    addAll(c.split(" · "))
                }
                for (p in pieces) {
                    val forms = buildList {
                        add(p)
                        Regex("`([^`]+)`").findAll(p).forEach { add(it.groupValues[1]) }
                        Regex("""\*\*([^*]+)\*\*""").findAll(p).forEach { add(it.groupValues[1]) }
                        // 뒤 괄호(주석)만 뗀다. 🔴 ① 백틱은 떼지 않는다.
                        add(p.replace(Regex("""\s*\([^)]*\)\s*$"""), ""))
                        add(p.replace(Regex("""\{[^}]*\}"""), ""))
                    }
                    forms.map { it.replace("**", "").replace(Regex("\\s+"), " ").trim().trim('`').trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { out.add(it) }
                }
            }
        }
        out + labelHalves
    }

    /**
     * 표의 한 줄을 화면이 **두 조각으로 그리는** 경우의 라벨 반쪽.
     *
     * 🔴 **규칙으로 자동 인식할 수 없다** — [approvedCopy] ①의 이유다. 이 셋은 실제로
     *    A 문서 표에 있는 줄이고(04 현황 카드 · 20 시즌 배너 · 20 내 순위 카드),
     *    화면이 숫자만 큰 글씨로 키우려고 문장을 쪼갠 것이다. A 문서 3절
     *    `값과 따로 그리는 라벨 반쪽 3개`가 같은 표를 들고 있다.
     *
     * ⚠️ **여기에 문구를 추가하는 것으로 새 문구를 통과시키지 않는다.** 그건 검사를
     *    끄는 것이다. 조건은 **"A 문서 표의 한 줄을 화면이 쪼개서 그린다"** 뿐이고,
     *    [라벨_반쪽은_A문서_줄의_일부다]가 그것을 기계로 확인한다.
     */
    private val labelHalves = setOf("이번 시즌", "일 남음", "이번 시즌 모은 꽃")

    private val uiSources: List<File> by lazy {
        val root = File(projectRoot, "android/app/src/main/java/com/catchflower/app/ui")
        if (!root.isDirectory) throw AssertionError("ui 소스 폴더를 못 찾았다: ${root.path}")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * `Text("…")` / `Text(text = "…")`의 **한글이 들어간 리터럴**.
     *
     * 문자열 템플릿(`"${'$'}{n}종"`)은 정규식이 `$`를 배제해 자동으로 빠진다 —
     * 값이 런타임에 정해져 문서와 글자로 비교할 수 없다.
     *
     * @return 문구 → 나온 파일 이름들
     */
    private fun drawnCopy(): Map<String, Set<String>> {
        val found = mutableMapOf<String, MutableSet<String>>()
        val pattern = Regex("""\bText\(\s*(?:text\s*=\s*)?"([^"$\n]*)"""")
        for (file in uiSources) {
            // 주석은 뺀다 — 지운 문구를 주석에 적어 두는 관행이 있어서
            // (`여기 있던 섹션 제목을 지웠다`) 안 빼면 지운 것이 다시 걸린다.
            val body = file.readText().lineSequence()
                .map { it.substringBefore("//") }
                .joinToString("\n")
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            for (m in pattern.findAll(body)) {
                val text = m.groupValues[1]
                if (text.any { it in '가'..'힣' }) {
                    found.getOrPut(text) { mutableSetOf() }.add(file.name)
                }
            }
        }
        return found
    }

    /**
     * 픽스처가 비어 있지 않은가.
     *
     * ⚠️ **이게 없으면 파일 0개를 읽고 초록이다.** 소스를 읽는 테스트의 기본 실패
     *    모양이고((22)에서 겪었다), 경로가 바뀌는 순간 조용히 일어난다.
     */
    @Test
    fun 소스를_실제로_읽었다() {
        assertTrue("ui 소스를 한 개도 못 읽었다 (${uiSources.size}개)", uiSources.size > 20)
        assertTrue("A 문서가 너무 짧다 — 다른 파일을 읽었다", aDoc.length > 10_000)
        assertTrue("A 문서 표에서 문구를 못 꺼냈다 (${approvedCopy.size}개)", approvedCopy.size > 300)
        assertTrue(
            "화면에 그리는 한글 문구를 한 개도 못 찾았다 — 정규식이 죽었다",
            drawnCopy().size > 50,
        )
    }

    /**
     * 🔴 **파싱이 너무 느슨해지지 않았는가** (대조군).
     *
     * 위 [소스를_실제로_읽었다]는 승인 집합이 **너무 작을 때**만 잡는다. 반대쪽 —
     * 승인 집합이 문서 전체를 삼켜 **뭐든 통과시키는** 상태는 조용하다.
     * 산문에만 있는 문구 두 개로 그 방향을 직접 고정한다.
     */
    @Test
    fun 산문에_적힌_문구는_승인이_아니다() {
        assertTrue(
            "전제가 깨졌다 — A 문서 산문에서 `0종 보기` 언급이 사라졌다",
            aDoc.contains("0종 보기"),
        )
        assertTrue(
            "산문의 `0종 보기`가 승인 문구로 읽혔다 — 표 셀만 봐야 한다",
            "0종 보기" !in approvedCopy,
        )
        // 3절 산문에 있는 긴 설명문. 표 셀이 아니므로 승인이 아니다.
        assertTrue(
            "산문 문장이 승인 집합에 들어왔다 — 셀 파싱이 문서 전체를 삼켰다",
            "왜 추가했나" !in approvedCopy,
        )
    }

    /**
     * 🔴 **지운 문구가 되살아나지 않는가** (대조군).
     *
     * [approvedCopy]의 `"~~" in c` 한 줄이 실제로 일한다는 것을 고정한다. 그 줄을
     * 지우면 3절 ``~~섹션 제목 `댓글`~~`` 셀에서 백틱 추출이 `댓글`을 꺼내
     * **지어낸 섹션 제목이 다시 통과한다** — 돌연변이 M6로 확인했고, 이 단정이
     * 없을 때는 **아무것도 빨개지지 않았다**(가드를 지운 것 자체를 아무도 안 본다).
     *
     * ⚠️ 이 단정이 없으면 가드는 "있어도 되고 없어도 되는 줄"로 보이고,
     *    다음 사람이 파싱을 정리하다가 지운다. `PlaceRules.weekStart`의
     *    `firstDayOfWeek`와 같은 종류의 줄이다 — **실측으로만 일하는 게 보인다.**
     */
    @Test
    fun 취소선으로_지운_문구는_승인이_아니다() {
        assertTrue(
            "전제가 깨졌다 — A 문서 3절의 `~~섹션 제목 \\`댓글\\`~~` 줄이 사라졌다. " +
                "그 줄이 이 검사의 대조군이다",
            aDoc.contains("~~섹션 제목 `댓글`~~"),
        )
        assertTrue(
            "취소선으로 지운 `댓글`이 승인 문구로 읽혔다 — `\"~~\" in c` 가드가 죽었다. " +
                "지운 것을 문서에 적으면 그 기록이 승인이 되는 사고를 이미 세 번 겪었다",
            "댓글" !in approvedCopy,
        )
    }

    /**
     * 🔴 **A 문서에 없는 문구를 화면에 그리지 않는다.**
     *
     * 빨개지는 경우: 문구를 코드에서 지어내면. 실제로 `댓글`(내가 지어낸 섹션 제목)과
     * 9개를 이 검사로 찾았다.
     *
     * **A 문서를 먼저 고친다.** 이 테스트가 빨개지면 순서를 어긴 것이다.
     */
    @Test
    fun 화면에_그리는_모든_문구가_A문서에_있다() {
        val missing = drawnCopy().filterKeys { it !in approvedCopy }
        assertEquals(
            "A 문서 표에 없는 문구를 화면에 그린다. **A 문서를 먼저 고친다** " +
                "(3절에 표를 추가하고 4절에 톤 검토 문의를 남긴다): " +
                missing.entries.joinToString { "${it.key} (${it.value.joinToString()})" },
            emptyMap<String, Set<String>>(),
            missing,
        )
    }

    /**
     * 🔴 **[labelHalves]가 검사를 끄는 뒷문이 되지 않는가** (대조군).
     *
     * 손으로 적는 목록은 **아무 문구나 넣으면 통과하는 구멍**이다 — 지어낸 문구를
     * 여기 적어 초록으로 만들 수 있다면 위 [화면에_그리는_모든_문구가_A문서에_있다]는
     * 있으나 마나다.
     *
     * 그래서 조건을 기계로 확인한다: **각 반쪽은 A 문서 표의 어떤 셀에 실제로 들어
     * 있는 글자여야 한다**(취소선 셀은 제외). 화면이 문장을 쪼개서 그린 것이므로
     * 반쪽은 반드시 원래 줄의 일부다. 지어낸 문구는 어느 셀에도 없어서 여기서 걸린다.
     *
     * ⚠️ 이건 완전한 방어가 아니다 — 문서의 아무 셀에나 그 글자를 끼워 넣으면 통과한다.
     *    다만 그때는 **A 문서를 고치는 일**이 되고, 그게 이 규칙이 원래 요구하는 순서다.
     */
    @Test
    fun 라벨_반쪽은_A문서_줄의_일부다() {
        val liveCells = aDoc.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") && !it.startsWith("|---") }
            .flatMap { it.trim('|').split("|").asSequence() }
            .map { it.trim() }
            .filter { "~~" !in it }
            .toList()
        val orphans = labelHalves.filter { half -> liveCells.none { half in it } }
        assertEquals(
            "손으로 적은 라벨 반쪽이 A 문서 표의 어느 셀에도 없다 — 목록에 넣어서 " +
                "검사를 끈 것이다. **A 문서를 먼저 고친다**: " + orphans.joinToString(),
            emptyList<String>(),
            orphans,
        )
        assertTrue(
            "A 문서 3절에 라벨 반쪽 표가 없다 — 목록과 문서가 연결되지 않았다",
            aDoc.contains("값과 따로 그리는"),
        )
    }

    /**
     * 이 검사의 **사정거리가 문서에 적혀 있는가.**
     *
     * 🔴 **한계를 적지 않으면 "문구를 다 막았다"로 읽힌다.** `Text(변수)`·
     *    `buildAnnotatedString`·`contentDescription`은 여기서 안 보인다 —
     *    그 경로로 지어낸 문구는 **여전히 초록**이다.
     *
     * `IconAssetTest`의 `미사용_검사가_실제로_잡는다`와 같은 층이다: 검사가 무엇을
     * **못** 하는지를 소스가 아니라 **문서**에 묶어 둔다. 다음 사람이 이 파일을 읽지
     * 않고 A 문서만 읽어도 알아야 한다.
     */
    @Test
    fun 사정거리를_문서에_적어_두었다() {
        assertTrue(
            "A 문서 3절에 이 검사의 한계(`Text(someVar)`는 사정거리 밖)가 적혀 있지 않다 — " +
                "적지 않으면 다음 사람이 이 검사를 '문구 전량 검증'으로 읽는다",
            aDoc.contains("Text(someVar)") && aDoc.contains("contentDescription"),
        )
        assertTrue(
            "A 문서에 `CopySourceTest` 이름이 없다 — 검사와 문서가 연결되지 않았다",
            aDoc.contains("CopySourceTest"),
        )
    }
}
