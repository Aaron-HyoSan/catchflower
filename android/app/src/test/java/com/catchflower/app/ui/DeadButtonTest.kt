package com.catchflower.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **눌러도 아무 일이 없는 버튼이 없는가.**
 *
 * ## 무엇을 잡는가
 *
 * 2026-08-09 실기 점검에서 마이 탭 헤더의 `설정`을 눌렀는데 **아무 일도 일어나지
 * 않았다.** 코드에는 클릭 핸들러 자리에 `TODO(화면 22)` 블록 주석만 있었다 —
 * 개발자에게는 "아직 안 만들었다"는 표시지만, **누른 사람에게는 그냥 고장난 앱**이다.
 * 세어 보니 같은 버튼이 **10개**였다: 설정 · 프로필 수정 · 알림 설정 · 고객문의 ·
 * 검색 · 초대 링크 보내기 · N위부터 더 보기 · 전체 보기 · 내가 공유한 꽃 · 결과 공유하기.
 *
 * 🔴 **시연에서 눌리는 것이 대부분 이 버튼들이다.** 심사위원은 만든 사람이 의도한
 * 경로로 걷지 않고 **눈에 보이는 것을 누른다.**
 *
 * ## 왜 못 봤나
 *
 * 빈 람다는 **컴파일도 되고 크래시도 안 나고 화면도 정상**이다. 스크린샷에는
 * 정상으로 찍히고, 값을 재는 테스트는 통과하고, 심지어 **누른 사람도 자기가 잘못
 * 눌렀다고 생각한다.** [ButtonLabelSourceTest]가 개발용 버튼을 잡는 것과 같은 층 —
 * **소스를 읽어야만** 보인다.
 *
 * ## 어떻게 고쳤나 — **두 번 고쳤다**
 *
 * 1. **2026-08-09**: 버튼을 지우지 않고 `아직 준비 중이에요` 토스트를 붙였다
 *    ([com.catchflower.app.ui.component.CfToast.NOT_READY] · A 문서 3절에 문구를 먼저
 *    추가했다). 지우면 와이어프레임과 화면이 달라지고 나중에 붙일 자리도 사라진다.
 *    ⚠️ 그때 적어 둔 대로 **토스트가 뜨는 것은 기능이 아니다.**
 * 2. **2026-08-13**: 오너가 `죽어있는 버튼 없도록 전부 구현해다오`라고 결정해서
 *    그 14자리가 **전부 실제 동작**이 됐다. 그래서 아래
 *    [준비중_토스트를_쓰는_곳이_없다]는 **2026-08-09판의 반대**를 단정한다 —
 *    옛 단정([준비중_토스트가_실제로_붙어있다])은 "토스트가 4개 파일 이상에 붙어
 *    있어야 한다"였고, 지금 그건 **거짓이어야 맞다.**
 *
 * 🔴 **낡은 단정을 지우지 않고 뒤집은 이유.** 지우면 "토스트로 뭉갠 상태로 되돌아가는
 *    것"을 막는 검사가 아무것도 안 남는다. 다음 사람이 급할 때 가장 쉬운 선택이
 *    정확히 그것이다(빈 람다보다 토스트가 성실해 보인다).
 */
class DeadButtonTest {

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "디자이너_업무").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError("프로젝트 루트를 못 찾았다 — 건너뛰게 만들면 검증이 사라진다")
    }

    private val mainSources: List<File> by lazy {
        val root = File(projectRoot, "android/app/src/main/java")
        if (!root.isDirectory) throw AssertionError("main 소스 폴더를 못 찾았다: ${root.path}")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * 주석을 뺀 본문.
     *
     * 지운 결함을 주석에 적어 두는 관행이 있어서(`여기 있던 [개발] 버튼을 지웠다`)
     * 안 빼면 **지운 것이 다시 걸린다.** 줄 주석과 블록 주석을 **둘 다** 뺀다 —
     * 이 파일이 잡으려는 결함의 원래 모양이 `onClick = { 블록주석 }`이라서
     * 블록 주석을 안 빼면 [눌러도_아무_일_없는_버튼이_없다]가 그걸 못 본다.
     */
    private fun bodyOf(file: File): String =
        file.readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .lineSequence().map { it.substringBefore("//") }.joinToString("\n")

    @Test
    fun 소스를_실제로_읽었다() {
        assertTrue("main 소스를 한 개도 못 읽었다", mainSources.size > 30)
        // 검사 대상이 실제로 있는가. 버튼이 0개면 아래 검사는 **아무것도 안 보고** 통과한다.
        val clicks = mainSources.sumOf { Regex("""onClick\s*=""").findAll(bodyOf(it)).count() }
        assertTrue("onClick을 한 개도 못 찾았다 — 정규식이나 경로가 틀렸다", clicks > 20)
    }

    /**
     * 🔴 **눌러도 아무 일이 없는 버튼이 없다.**
     *
     * 빨개지는 경우: 버튼의 `onClick`을 빈 람다로 두거나, 안에 주석만 적어 두면
     * ([bodyOf]가 주석을 먼저 떼므로 `{ TODO 주석 }`도 빈 람다로 보인다).
     * 고치는 방법은 둘이다 — 기능을 붙이거나,
     * [com.catchflower.app.ui.component.CfToast.NOT_READY]를 띄운다.
     *
     * ⚠️ **버튼을 아예 안 그리는 자리는 대상이 아니다.**
     *    `SectionHeader(title = "내 배지", actionLabel = null, onAction = {})`이 그렇다
     *    (`MyScreen.kt`) — `actionLabel`이 `null`이면 `CfTextButton`을 안 그리므로
     *    **누를 것이 없다.** 그래서 이 검사는 `onAction`을 받는 래퍼가 아니라
     *    **버튼 컴포저블 호출에 직접 붙은 `onClick`**만 본다.
     */
    @Test
    fun 눌러도_아무_일_없는_버튼이_없다() {
        val dead = mutableListOf<String>()
        for (file in mainSources) {
            val body = bodyOf(file)
            for (name in BUTTONS) {
                Regex(Regex.escape(name) + """\s*\(""").findAll(body).forEach { m ->
                    val segment = body.substring(m.range.last, minOf(body.length, m.range.last + 300))
                    if (Regex("""onClick\s*=\s*\{\s*\}""").containsMatchIn(segment)) {
                        val label = Regex("""text\s*=\s*"([^"]*)"""").find(segment)?.groupValues?.get(1)
                        dead += "${file.name}: ${label ?: name}"
                    }
                }
            }
        }
        assertEquals(
            "눌러도 아무 일이 없는 버튼이다. 기능을 붙이거나 CfToast.NOT_READY를 띄운다: $dead",
            emptyList<String>(),
            dead,
        )
    }

    /**
     * 클릭 핸들러 자리에 `TODO` 주석만 있는 곳이 없다.
     *
     * 위 검사는 **버튼 컴포저블**만 본다. `Modifier.clickable`·`IconButton`처럼
     * 버튼이 아닌 자리도 같은 결함이 나는데, 그건 라벨이 없어 위 검사가 못 짚는다.
     * 여기서는 **원문 그대로** 읽어서 핸들러 자리의 `TODO`를 문법적으로 찾는다.
     *
     * 🔴 `TODO`라는 낱말이 붙어 있으면 개발자는 "안 만든 걸 알고 있다"고 안심한다 —
     *    **그 안심이 이 결함 10개를 실기기까지 가져갔다.** 남겨 둘 이유가 있으면
     *    주석이 아니라 [com.catchflower.app.ui.component.CfToast.NOT_READY]로 남긴다.
     */
    @Test
    fun TODO만_들어있는_클릭핸들러가_없다() {
        val offenders = mutableListOf<String>()
        for (file in mainSources) {
            Regex("""on(?:Click|Action)\s*=\s*\{\s*/\*[\s\S]*?\*/\s*\}""")
                .findAll(file.readText())
                .forEach { offenders += "${file.name}: ${it.value.take(70)}" }
        }
        assertEquals(
            "클릭하면 주석만 실행되는 버튼이다 — 사용자에게는 고장난 앱이다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * 🔴 **빈 람다를 `on…` 인자로 넘기는 자리가 승인된 한 곳뿐이다.**
     *
     * ## 왜 위 두 검사로 안 되나 — **실제로 두 개를 놓쳤다**(2026-08-13)
     *
     * `죽어있는 버튼 없도록 전부 구현해다오`를 다 끝냈다고 적은 뒤에
     * 문서를 고치려고 `TODO`를 세어 보니 **눌러도 아무 일이 없는 자리가 둘 더** 있었다:
     *
     * 1. **화면 07 `도움말`** — 버튼 쪽은 `onClick = onHelp`라서 빈 람다가 아니었고,
     *    빈 람다는 **부르는 쪽**(`CaptureFlow`)의 `onHelp = {}`에 있었다.
     *    [눌러도_아무_일_없는_버튼이_없다]는 버튼 호출 뒤 300자만 보므로 원리상 못 본다.
     * 2. **랭킹 행 탭** — `Modifier.clickable(onClickLabel = "…") { 줄주석 }`.
     *    [TODO만_들어있는_클릭핸들러가_없다]는 **블록 주석**만 보고, 이름도
     *    `onClick`·`onAction`만 본다.
     *
     * 즉 두 검사는 **버튼 컴포저블 자리**를 지키고 있었고, 결함은 그 밖으로 나갔다.
     * 여기서는 이름을 정하지 않고 `on…= {}` **전부**를 센다.
     *
     * ## 🔴 금지가 아니라 **허용 목록**이다
     *
     * 빈 람다가 정당한 경우가 있다 — `actionLabel = null`이면 버튼을 안 그리므로
     * `onAction`을 부를 자리가 없다. 그래서 "빈 람다 금지"로 쓰면 이 검사는
     * 곧 지워진다. 대신 **승인된 자리 목록과 같은지**를 본다(`금지어 목록은 원리상
     * 못 막는다 → 승인된 것만 허용으로 뒤집는다`). 새 자리가 생기면 여기서 걸리고,
     * 정당하면 이유를 적어 목록에 넣는다.
     */
    @Test
    fun 빈_람다를_넘기는_자리는_승인된_곳뿐이다() {
        val found = mutableListOf<String>()
        for (file in mainSources) {
            val body = bodyOf(file)
            // 선언의 기본값(`onX: () -> Unit = {}`)은 이름 바로 뒤에 `:`가 오므로 안 걸린다.
            // 여기서 잡는 것은 **부르는 쪽이 넘기는** `onX = {}`다.
            Regex("""\bon[A-Z]\w*\s*=\s*\{\s*\}""").findAll(body).forEach { m ->
                val name = m.value.substringBefore("=").trim()
                found += "${file.name}: $name"
            }
        }
        assertEquals(
            "빈 람다를 넘기는 자리가 생겼다 — 눌러도 아무 일이 없는 버튼이거나, " +
                "정당하면 이유와 함께 이 목록에 넣어라: $found",
            EMPTY_HANDLERS_OK,
            found.sorted(),
        )
    }

    /**
     * 🔴 **눌러도 아무 일이 없는 `clickable`이 없다.**
     *
     * 빨개지는 경우: `Modifier.clickable { }` 또는 안에 주석만 둘 때
     * ([bodyOf]가 줄 주석과 블록 주석을 **둘 다** 뗀다).
     *
     * 🔴 **버튼보다 더 나쁘다.** 랭킹 행이 `onClickLabel = "{닉네임} 도감 보기"`를 달고
     *    빈 람다를 갖고 있었다 — **스크린리더는 "도감을 여는 버튼"이라고 읽어 준다.**
     *    눈으로 보는 사람에게는 물결만 뜨고, 화면 읽기로 쓰는 사람에게는 **없는 화면을
     *    약속한다.** 어느 쪽도 화면 캡처로는 보이지 않는다.
     *
     * ⚠️ 붙일 화면이 없으면 `clickable`을 **지운다.** 화면 20-2의 `앱 버전`이 값 행인
     *    것과 같은 판단이다 — 누를 수 없으면 아무것도 약속하지 않는다.
     */
    @Test
    fun 눌러도_아무_일_없는_clickable이_없다() {
        val dead = mutableListOf<String>()
        var callSites = 0
        var trailingLambdas = 0
        for (file in mainSources) {
            val body = bodyOf(file)
            callSites += Regex("""\.clickable\s*[({]""").findAll(body).count()
            Regex("""\.clickable\s*(\([^)]*\))?\s*\{""").findAll(body).forEach { m ->
                trailingLambdas++
                // 여는 중괄호 뒤가 곧바로 닫는 중괄호면 몸통이 비었다(주석은 이미 떼였다).
                if (Regex("""^\s*\}""").containsMatchIn(body.substring(m.range.last + 1))) {
                    dead += "${file.name}:${body.take(m.range.first).count { it == '\n' } + 1}"
                }
            }
        }
        // ⚠️ 대상이 0개면 위 루프는 **아무것도 안 보고** 통과한다. 두 숫자를 따로 본다 —
        //    ① `.clickable`을 아예 못 찾으면 경로·주석 제거가 깨진 것이고(실측 14곳),
        //    ② 후행 람다 형태를 못 찾으면 **이 검사가 볼 대상이 없다**(실측 4곳).
        //    🔴 ②를 안 두면 `clickable(onClick = …)` 형태만 남았을 때 조용히 통과한다
        //       (그 형태의 빈 람다는 위 `빈_람다를_넘기는_자리는…`이 잡는다).
        assertTrue("`.clickable` 호출을 한 개도 못 찾았다 — 정규식이나 경로가 틀렸다", callSites > 10)
        assertTrue(
            "후행 람다 형태(`clickable { … }`)가 하나도 없다 — 이 검사가 아무것도 안 보고 있다",
            trailingLambdas > 0,
        )
        assertEquals(
            "누를 수 있는데 아무 일도 안 하는 자리다. 기능을 붙이거나 clickable을 지운다: $dead",
            emptyList<String>(),
            dead,
        )
    }

    /**
     * 🔴 **`아직 준비 중이에요`를 띄우는 자리가 하나도 없다.**
     *
     * 빨개지는 경우: 새 버튼을 만들면서 기능 대신
     * [com.catchflower.app.ui.component.CfToast.NOT_READY]를 붙일 때.
     *
     * ⚠️ **위 두 검사로는 그걸 못 잡는다.** 토스트를 띄우는 람다는 빈 람다가 아니고
     *    `TODO` 주석도 없다 — 즉 "죽은 버튼"의 정의를 피해 간다. 그런데 사용자에게는
     *    **누를 수 있고, 눌리고, 아무 것도 안 되는 버튼**이다. 오너 결정
     *    (`죽어있는 버튼 없도록 전부 구현해다오`)이 금지한 것이 정확히 그 상태다.
     *
     * ⚠️ [bodyOf]가 주석을 먼저 뗀다 — 그래서 `CfToast.kt`의 상수 선언 자체와
     *    "다시 쓰면 이 테스트가 빨개진다"고 적어 둔 **주석은 걸리지 않는다.**
     *    상수를 남겨 둔 이유는 A 문서 왕복을 다시 하지 않기 위한 것이다.
     */
    @Test
    fun 준비중_토스트를_쓰는_곳이_없다() {
        val users = mainSources
            .filter { bodyOf(it).contains("CfToast.NOT_READY") }
            .map { it.name }
        assertEquals(
            "`아직 준비 중이에요`로 뭉갠 버튼이 생겼다 — 기능을 붙여라(오너 결정 2026-08-13): $users",
            emptyList<String>(),
            users,
        )
    }

    /**
     * ⚠️ **위 검사가 스스로 꺼지지 않는가.** 상수 이름을 바꾸거나 없애면
     *    `contains("CfToast.NOT_READY")`가 영원히 false가 되고, 그러면 위 단정은
     *    **아무것도 안 보고 통과한다**(`파일 유무를 검사 조건으로 쓰면 스스로 꺼진다`와
     *    같은 함정). 그래서 상수가 **선언되어 있다는 것**을 따로 확인한다.
     */
    @Test
    fun 준비중_상수는_아직_선언되어_있다() {
        val toastFile = mainSources.single { it.name == "CfToast.kt" }
        assertTrue(
            "CfToast.NOT_READY 선언이 없다 — 위 `준비중_토스트를_쓰는_곳이_없다`가 이제 아무것도 검사하지 않는다",
            bodyOf(toastFile).contains("NOT_READY("),
        )
    }

    private companion object {
        /**
         * `on… = {}`가 **정당한** 자리. 목록 밖이면
         * [빈_람다를_넘기는_자리는_승인된_곳뿐이다]가 빨개진다.
         *
         * - `MyScreen.kt: onAction` —
         *   `SectionHeader(title = "내 배지", actionLabel = null, onAction = {})`.
         *   `actionLabel`이 `null`이면 [com.catchflower.app.ui.component.CfTextButton]을
         *   **안 그린다** → 누를 것이 없다. 배지 전체목록은 B-9 오너 미확정이라
         *   버튼 자체를 두지 않았다.
         */
        val EMPTY_HANDLERS_OK = listOf("MyScreen.kt: onAction")

        /**
         * ⚠️ [ButtonLabelSourceTest.버튼_컴포넌트_목록이_실제와_같다]가 이 목록이
         *    실제 코드와 같은지 검사한다. 새 버튼 컴포넌트를 만들면 **양쪽에** 넣는다.
         */
        val BUTTONS = listOf(
            "CfPrimaryButton",
            "CfSecondaryButton",
            "CfGhostButton",
            "CfTextButton",
            "CfSmallButton",
        )
    }
}
