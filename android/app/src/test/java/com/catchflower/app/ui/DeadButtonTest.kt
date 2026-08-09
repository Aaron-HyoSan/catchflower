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
 * ## 어떻게 고쳤나
 *
 * 버튼을 지우지 않고 `아직 준비 중이에요` 토스트를 붙였다
 * ([com.catchflower.app.ui.component.CfToast.NOT_READY] · A 문서 3절에 문구를 먼저 추가했다).
 * 지우면 와이어프레임과 화면이 달라지고 나중에 붙일 자리도 사라진다.
 *
 * ⚠️ **토스트가 뜨는 것은 기능이 아니다.** `구현현황_AOS.md`에 ✅를 주지 않는다.
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
     * `NOT_READY` 토스트가 실제로 붙어 있는가.
     *
     * ⚠️ **위 두 검사는 "빈 람다가 없다"만 본다.** 예선 범위 밖 버튼을 **전부 지워도**
     *    통과하는데, 그건 와이어프레임과 화면이 달라지는 것이라 우리가 고른 답이 아니다.
     *    이 단정이 고친 **방식 자체**를 고정한다.
     */
    @Test
    fun 준비중_토스트가_실제로_붙어있다() {
        val users = mainSources.filter { bodyOf(it).contains("CfToast.NOT_READY") }.map { it.name }
        assertTrue(
            "CfToast.NOT_READY를 쓰는 화면이 없다 — 예선 범위 밖 버튼을 지웠거나 토스트가 빠졌다: $users",
            users.size >= 4,
        )
    }

    private companion object {
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
