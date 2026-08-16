package com.catchflower.app.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 비로그인으로 무엇을 할 수 있는가 (오너 결정 2026-08-14 · 공유계약 3절).
 *
 * ## 🔴 왜 이 파일이 있어야 하는가
 *
 * 이 판정은 **어느 쪽으로 틀려도 화면에 증상이 없다.**
 *
 * - 너무 느슨하면 게이트가 **아예 없는 것과 같다** — 앱은 완벽하게 동작하고, 로그인한
 *   사용자가 0명인 것으로만 나중에 드러난다.
 * - 너무 빡세면 **첫 촬영부터 막힌다** — 사용자는 앱이 고장난 줄 안다.
 *
 * 둘 다 빌드도 실행도 정상이다. 그래서 숫자와 6개 행동의 조합을 여기서 못 박는다.
 *
 * ## ⚠️ 이 파일이 재는 것과 못 재는 것
 *
 * 재는 것: **판정**. 못 재는 것: 그 판정을 **부르는지**. `onPhotoTaken`에서 게이트
 * 호출을 지워도 이 파일은 전부 초록이다(`CaptureViewModel`은 `Application`이 필요해
 * JVM에서 못 만든다). 그 층은 [com.catchflower.app.ui.DeadButtonTest] 계열이 아니라
 * **실기기 확인**이 필요하다 — `구현현황_AOS.md`에 그렇게 적었다.
 *
 * ## 🔴 이 파일을 돌연변이로 검증할 때 (실측 2026-08-14)
 *
 * `2`를 `3`으로 바꿔 red를 확인하려면 **`--rerun-tasks --no-build-cache`가 필요하다.**
 * 그냥 돌리면 두 가지가 각각 조용히 검증을 무력화한다:
 *
 * 1. **Gradle이 `.kt` 변경을 못 본다.** `2`→`3`은 **파일 크기가 같아서**, 되돌릴 때
 *    mtime까지 원래대로 돌아가면(`cp`/`mv`로 백업·복원하면 그렇게 된다) VFS가
 *    `compileDebugKotlin UP-TO-DATE`로 넘긴다 — **빌드가 아예 안 돈다.**
 * 2. **빌드 캐시가 앞선 라운드의 결과를 되돌려 준다**(`FROM-CACHE`). 즉 red여야 하는
 *    실행이 **초록으로 끝난다.**
 *
 * 🔴 둘 다 화면에 `BUILD SUCCESSFUL`로 보인다 — 즉 **"돌연변이가 안 잡혔다"와
 *    "돌연변이를 안 재 봤다"가 구분되지 않는다.** 실제로 처음 이 검증을 돌렸을 때
 *    1번에 걸려서 `아무 검사도 안 잡는다`로 읽었다.
 */
class LoginGateTest {

    /**
     * 이 프로젝트 루트. `디자이너_업무` 폴더를 찾아 올라간다
     * ([com.catchflower.app.ui.DeadButtonTest]와 같은 방식 · 건너뛰지 않는다).
     */
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

    /** 주석을 뺀 본문. **KDoc의 `[…GatedAction.X]` 참조가 호출처로 세지 않도록** 뗀다. */
    private fun bodyOf(file: File): String =
        file.readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .lineSequence().map { it.substringBefore("//") }.joinToString("\n")

    private fun anon(action: LoginGate.GatedAction, count: Int) =
        LoginGate.requiresLogin(action, kakaoLinked = false, identifyCount = count)

    private fun linked(action: LoginGate.GatedAction, count: Int) =
        LoginGate.requiresLogin(action, kakaoLinked = true, identifyCount = count)

    /**
     * 오너가 준 숫자. 🔴 바꾸려면 공유계약 3절부터 고친다(iOS가 같은 값을 쓴다).
     *
     * ## 🔴 `GamePolicy.ANONYMOUS_IDENTIFY_LIMIT`을 직접 읽지 않는다
     *
     * `const val`은 **읽는 쪽에 값이 박힌다**(컴파일 시 인라인). 즉 소스에
     * `assertEquals(2, GamePolicy.ANONYMOUS_IDENTIFY_LIMIT)`이라고 써 두면 컴파일된
     * 바이트코드는 **`2 == 2`** 이고, 상수를 3으로 바꿔도 이 테스트가 다시 컴파일되지
     * 않는 한 **초록이다.**
     *
     * ⚠️ 가정이 아니라 **실측이다.** 돌연변이(2→3)로 잰 직후 상수를 되돌렸는데
     *    `compileDebugUnitTestKotlin`이 up-to-date로 넘어가서, 상수가 2인 소스에서
     *    이 검사가 **`3`을 보고 빨개진 채로 남아 있었다.** 방향만 반대인 같은 일이
     *    "상수를 바꿨는데 초록"이다.
     *
     * → 그래서 **리플렉션으로 필드를 읽는다.** 리플렉션은 인라인을 타지 않아
     *   실행 시점의 진짜 값을 본다.
     */
    @Test
    fun 한도는_2회다() {
        val field = GamePolicy::class.java.getField("ANONYMOUS_IDENTIFY_LIMIT")
        assertEquals(
            "오너 결정은 `비로그인은 판별 디바이스당 2회`다. 이 값을 고치기 전에 " +
                "프로젝트 맥락/공유계약_iOS_AOS.md 3절과 iOS anonymousIdentifyLimit을 같이 고친다",
            2,
            field.getInt(null),
        )
    }

    // ── 판별·등록·공유: 2회까지 ────────────────────────────────────────

    /**
     * 🔴 **0·1회에서 막히면 첫 사용자가 아무것도 못 한다.**
     *
     * `>=`를 `>`로 잘못 쓰면 3회가 되고, `<`로 뒤집으면 첫 촬영부터 막힌다.
     * 두 실수 다 컴파일된다.
     */
    @Test
    fun 판별은_두_번까지_비로그인으로_된다() {
        assertFalse("첫 촬영이 막혔다", anon(LoginGate.GatedAction.IDENTIFY, 0))
        assertFalse("두 번째 촬영이 막혔다", anon(LoginGate.GatedAction.IDENTIFY, 1))
    }

    /** 🔴 여기서 막히지 않으면 무료 판별이 **무한**이다. */
    @Test
    fun 판별은_세_번째부터_로그인을_요구한다() {
        assertTrue("2회를 다 썼는데 또 된다", anon(LoginGate.GatedAction.IDENTIFY, 2))
        assertTrue("3회를 넘겼는데 또 된다", anon(LoginGate.GatedAction.IDENTIFY, 3))
    }

    /**
     * 등록·공유는 **판별과 같은 규칙**이다.
     *
     * ⚠️ 여기서 끊으면 "찍고 확정까지 했는데 마지막에 막힌다"가 된다 —
     *    사용자에게는 앱이 고장난 것으로 보인다([LoginGate.GatedAction.SHARE] 주석).
     */
    @Test
    fun 등록과_공유는_판별과_같은_규칙이다() {
        for (action in listOf(LoginGate.GatedAction.REGISTER, LoginGate.GatedAction.SHARE)) {
            assertFalse("$action 이 1회째에 막혔다", anon(action, 1))
            assertTrue("$action 이 한도 뒤에도 열려 있다", anon(action, 2))
        }
    }

    // ── 댓글·좋아요·신고: 0회 ─────────────────────────────────────────

    /**
     * 🔴 **횟수와 무관하게 막힌다.** 남의 기록에 관여하는 행위다.
     *
     * 이 세 개를 판별과 같은 가지에 넣으면 **비로그인이 남의 기록에 댓글을 달 수 있다** —
     * 그리고 서버는 익명 세션에도 `auth.uid()`가 있으므로 **성공한다.** 즉 화면에
     * 아무 오류도 안 뜨고, 지울 수 없는 익명 댓글이 남는다.
     */
    @Test
    fun 댓글_좋아요_신고는_횟수가_없다() {
        val zeroTimes = listOf(
            LoginGate.GatedAction.COMMENT,
            LoginGate.GatedAction.LIKE,
            LoginGate.GatedAction.REPORT,
        )
        for (action in zeroTimes) {
            for (count in 0..3) {
                assertTrue("$action 이 $count 회에서 열렸다", anon(action, count))
            }
        }
    }

    /**
     * 🔴 **친구 요청·닉네임 변경도 0회다** (오너 결정 2026-08-16).
     *
     * ## 왜 위 검사가 이걸 못 잡았나
     *
     * 위 [댓글_좋아요_신고는_횟수가_없다]는 **목록에 적은 세 개**만 본다. 2026-08-16까지
     * 친구 요청과 닉네임 변경은 [LoginGate.GatedAction]에 **아예 없었고**, 없는 것은
     * 어떤 검사도 못 본다 — 오너가 실기기에서 `마이프로필이나 설정을 바꿀때면 로그인이
     * 없어도되네?`로 발견했다.
     *
     * 그래서 [게이트가_걸리는_행동은_여덟_개다]가 **목록 자체**를 고정하고, 이 검사가
     * **어느 무리에 넣었는지**를 고정한다. 둘 중 하나만 있으면 다시 같은 일이 난다.
     */
    @Test
    fun 친구요청과_닉네임변경도_횟수가_없다() {
        val zeroTimes = listOf(
            LoginGate.GatedAction.FRIEND_REQUEST,
            LoginGate.GatedAction.PROFILE_EDIT,
        )
        for (action in zeroTimes) {
            for (count in 0..3) {
                assertTrue(
                    "$action 이 $count 회에서 열렸다 — 판별 무리에 넣으면 비로그인이 " +
                        "남의 알림함에 남거나 랭킹에 보이는 이름을 바꾼다",
                    anon(action, count),
                )
            }
        }
    }

    // ── 로그인하면 전부 열린다 ────────────────────────────────────────

    /**
     * 🔴 **연결된 계정에는 횟수가 없다.**
     *
     * 여기가 틀리면 로그인한 사용자가 3회째에 **다시 로그인 시트를 본다** —
     * `아까 로그인했는데 또?`가 되고, 시트에서 로그인을 눌러도 이미 연결돼 있어서
     * 서버가 거절한다(그 실패는 `연결이 불안정해요`로 보인다). 원인이 화면에 없다.
     */
    @Test
    fun 로그인하면_모든_행동이_열린다() {
        for (action in LoginGate.GatedAction.entries) {
            for (count in listOf(0, 2, 99)) {
                assertFalse("로그인했는데 $action 이 $count 회에서 막혔다", linked(action, count))
            }
        }
    }

    /**
     * ⚠️ **8개가 전부인지** 고정한다. 새 행동이 늘면 이 검사가 빨개지고, 그때
     *    위 두 무리 중 어디에 속하는지 **결정**하게 만든다 — `requiresLogin`의 `when`은
     *    컴파일로 잡지만, 여기 목록은 "판별 무리에 잘못 넣었다"를 잡는다.
     *
     * 🔴 **이 검사가 잡지 못하는 방향이 하나 있다: 목록에 넣지 않는 것.**
     *    2026-08-16에 실제로 그랬다 — 친구 요청과 닉네임 변경은 enum에 없어서
     *    게이트를 아예 지나지 않았고, `when`의 exhaustive 검사도 이 목록 검사도
     *    **둘 다 초록이었다.** 그 방향은 코드로 못 막는다(없는 것은 컴파일러도
     *    grep도 못 찾는다) → **서버에 쓰는 새 자리를 만들 때마다 여기 표를 본다.**
     */
    @Test
    fun 게이트가_걸리는_행동은_여덟_개다() {
        assertEquals(
            listOf(
                "IDENTIFY", "REGISTER", "SHARE",
                "COMMENT", "LIKE", "REPORT", "FRIEND_REQUEST", "PROFILE_EDIT",
            ),
            LoginGate.GatedAction.entries.map { it.name },
        )
    }

    // ── 남은 횟수 ────────────────────────────────────────────────────

    /**
     * 🔴 **음수가 나오면 안 된다.** 소급 적용을 안 하기로 했으므로 이미 3회 이상
     *    등록한 기기가 존재한다(테스터 12명) — `2 - 5 = -3`이 화면에 그려지면
     *    그 기기에서만 이상한 숫자가 뜨고 재현이 안 된다.
     *
     * ⚠️ **지금 이 함수를 부르는 화면이 없다**(A 문서 4절 21번). 그래도 검사를 두는
     *    이유는 문구가 왔을 때 붙이는 사람이 판정을 새로 쓰지 않게 하려는 것이다.
     */
    @Test
    fun 남은_횟수는_음수가_되지_않는다() {
        assertEquals(2, LoginGate.remainingAnonymousIdentifies(kakaoLinked = false, identifyCount = 0))
        assertEquals(1, LoginGate.remainingAnonymousIdentifies(kakaoLinked = false, identifyCount = 1))
        assertEquals(0, LoginGate.remainingAnonymousIdentifies(kakaoLinked = false, identifyCount = 2))
        assertEquals(0, LoginGate.remainingAnonymousIdentifies(kakaoLinked = false, identifyCount = 7))
    }

    // ── 판정을 **부르는 곳이 있는가** (소스 검사) ──────────────────────

    /**
     * 🔴 **선언만 있고 아무 데서도 판정하지 않는 행동이 없다.**
     *
     * ## 왜 이 검사가 필요한가 (2026-08-16)
     *
     * 위 검사들은 전부 **판정 함수**만 본다. 그래서 `GatedAction`에 값을 넣고
     * **부르는 자리를 안 만들어도 전부 초록이다** — `CfToast`에서 같은 일이
     * 났고([com.catchflower.app.ui.DeadButtonTest] 마지막 검사), 그 결함은
     * **화면에도 증상이 없다**(막혀야 할 것이 그냥 잘 된다).
     *
     * ## ⚠️ 승인 목록이 있다 — `REGISTER`·`SHARE`는 **부르는 곳이 없는 게 맞다**
     *
     * 게이트는 **셔터 한 곳**에만 있다([LoginGate.GatedAction.SHARE] 주석).
     * 판별에 실패한 사람은 등록·공유 화면에 도달하지 못하고, 반대로 2회째 판별에
     * 성공한 사람(`identifyCount == 2`)이 등록에서 다시 판정을 받으면
     * **찍고 확정까지 한 뒤에 막힌다** — 오너가 금지한 그 모양이다. 두 상수는
     * `요금표`로 남는다(정책을 한 표에서 읽게 하는 값).
     *
     * 🔴 **금지가 아니라 승인 목록으로 쓴다** — [com.catchflower.app.ui.DeadButtonTest]의
     *    `빈_람다를_넘기는_자리는_승인된_곳뿐이다`와 같은 이유다. "부르는 곳이 없어도
     *    된다"를 규칙으로 두면 이 검사는 곧 아무것도 안 잡는다.
     *
     * ⚠️ **주석은 호출처로 세지 않는다.** `[…GatedAction.FRIEND_REQUEST]`처럼 KDoc에
     *    이름을 적어 두는 것이 통과 사유가 되면, 문서가 구현을 승인하게 된다.
     */
    @Test
    fun 선언된_행동은_전부_부르는_곳이_있다() {
        val sources = mainSources.filter { it.name != "LoginGate.kt" }
        assertTrue("main 소스를 한 개도 못 읽었다", sources.size > 30)

        val bodies = sources.map { bodyOf(it) }
        // 이 검사가 **실제로 무언가를 보고 있는지** 먼저 센다. 호출 형태가 바뀌면
        // (예: `GatedAction`을 import해서 접두사 없이 쓰면) 전부 0곳이 되어
        // "모두 빠졌다"로 빨개져야 하고, 조용히 통과해서는 안 된다.
        val called = LoginGate.GatedAction.entries.filter { action ->
            bodies.any { it.contains("GatedAction.${action.name}") }
        }
        assertTrue("게이트를 부르는 자리를 하나도 못 찾았다 — 이 검사가 비어 있다", called.isNotEmpty())

        val orphans = LoginGate.GatedAction.entries
            .map { it.name }
            .filterNot { it in CALLSITE_OPTIONAL }
            .filterNot { name -> bodies.any { it.contains("GatedAction.$name") } }
        assertEquals(
            "선언만 하고 아무 데서도 판정하지 않는 행동이다 — 부르는 자리를 붙이거나, " +
                "부를 필요가 없는 이유를 적어 CALLSITE_OPTIONAL에 넣어라: $orphans",
            emptyList<String>(),
            orphans,
        )
    }

    /** 로그인한 사용자에게는 남은 횟수라는 개념이 없다. 0을 주면 "다 썼다"로 읽힌다. */
    @Test
    fun 로그인하면_남은_횟수를_세지_않는다() {
        assertEquals(
            Int.MAX_VALUE,
            LoginGate.remainingAnonymousIdentifies(kakaoLinked = true, identifyCount = 99),
        )
    }

    private companion object {
        /**
         * **부르는 곳이 없어도 되는** 행동. 목록 밖이면
         * [선언된_행동은_전부_부르는_곳이_있다]가 빨개진다.
         *
         * - `REGISTER`·`SHARE` — 게이트는 **셔터 한 곳**이다. 등록·공유 화면은 판별에
         *   성공해야 도달하므로 거기서 다시 판정하면 **2회째를 찍고 확정한 사람이
         *   마지막에 막힌다.** 두 상수는 정책을 한 표에서 읽게 하는 값으로 남는다.
         *   ⚠️ 촬영 흐름 밖에서 공유를 여는 자리가 생기면(예: 저장된 기록을 나중에
         *   지도에 올리기) **그때는 `SHARE`를 여기서 빼고 그 자리에서 판정해야 한다.**
         */
        val CALLSITE_OPTIONAL = setOf("REGISTER", "SHARE")
    }

    /**
     * 🔴 **두 함수가 같은 곳에서 갈린다.** `remaining == 0`인 순간이
     *    `requiresLogin == true`인 순간과 같아야 한다.
     *
     * 갈리면 화면이 `1회 남았어요`라고 말한 뒤 그 1회에서 시트가 뜬다 — 그때 증상은
     * "안내가 거짓말을 한다"이고, 두 함수를 따로 보면 둘 다 맞아 보인다.
     */
    @Test
    fun 남은_횟수_0과_게이트가_같은_지점에서_바뀐다() {
        for (count in 0..5) {
            val blocked = anon(LoginGate.GatedAction.IDENTIFY, count)
            val remaining = LoginGate.remainingAnonymousIdentifies(false, count)
            assertEquals(
                "count=$count 에서 남은 횟수($remaining)와 게이트($blocked)가 어긋난다",
                blocked,
                remaining == 0,
            )
        }
    }
}
