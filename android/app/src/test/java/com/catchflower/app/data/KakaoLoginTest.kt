package com.catchflower.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카카오 로그인 층. **여기서 지키는 것은 문구가 아니라 계정 데이터다.**
 *
 * 🔴 이 파일의 검사 대부분은 "누를 수 없게 되어 있는가"를 잰다. 실패하면 증상이
 *    **로그인 오류가 아니라 도감 소실**이다 — 그리고 도감 화면은 그대로 보인다
 *    (사진이 기기 로컬 파일이므로). 눈으로 확인할 수 없는 종류라 테스트로 고정한다.
 *
 * ⚠️ [SupabaseAuthUrls]의 URL 조립은 `android.net.Uri`를 쓰므로 **JVM에서 못 돈다**
 *    (android.jar 껍데기 → `RuntimeException: Stub!`). 그래서 이 파일은 URL을
 *    **조립하지 않고**, 상수와 판단 함수만 잰다. URL 조립은 계측 테스트의 일이다.
 *    ⚠️ 여기서 `Uri`를 부르는 검사를 추가하면 **다른 검사까지 같이 죽는다.**
 */
class KakaoLoginTest {

    /**
     * 🔴 빨개지는 경우: 누군가 [KakaoLogin.canLinkToExistingAccount]를 `true`로
     *    바꿨는데 **서버는 아직 꺼져 있을** 때. 그러면 카카오 버튼이 눌리고,
     *    누른 사람의 익명 계정 기록이 주인 없는 데이터가 된다.
     *
     * ⚠️ **이 검사는 "false여야 옳다"고 말하는 것이 아니다.** 서버가 켜지면 이 값을
     *    true로 바꾸는 것이 맞고, 그때 이 테스트가 빨개진다 — 그게 의도다.
     *    빨개지면 **실서버로 다시 재고**(`/auth/v1/user/identities/authorize`가
     *    `manual_linking_disabled`를 더 안 주는지) 이 테스트와 A 문서 3절
     *    `화면 01에서 로그인 버튼을 누를 수 없을 때`를 같이 고친다.
     */
    @Test
    fun 계정_연결이_불가한_동안_카카오_버튼은_눌리지_않는다() {
        assertFalse(
            "Manual Linking이 서버에서 켜졌다면 실측으로 확인하고 이 테스트와 " +
                "A 문서 3절 화면 01 표를 같이 고친다 — 값만 바꾸면 도감이 사라지는 경로가 열린다",
            KakaoLogin.canLinkToExistingAccount,
        )
        assertFalse(
            "서버 설정이 꺼져 있는데 버튼이 눌린다",
            KakaoLogin.buttonEnabled(serverConfigured = true),
        )
    }

    /**
     * 빨개지는 경우: 키가 없는 빌드에서 버튼이 눌리게 됐을 때.
     * 그러면 누른 사람은 브라우저가 열리고 **`https:///auth/v1/authorize`** 같은
     * 주소로 가서 아무 설명 없는 오류 화면을 본다.
     */
    @Test
    fun 서버_키가_없으면_연결이_허용돼도_눌리지_않는다() {
        assertFalse(
            KakaoLogin.buttonEnabled(serverConfigured = false, linkingAllowed = true),
        )
        // 대조군 — 둘 다 갖춰지면 켜진다. 이게 없으면 위 검사는
        // "항상 false를 돌려주는 함수"여도 통과한다.
        assertTrue(
            KakaoLogin.buttonEnabled(serverConfigured = true, linkingAllowed = true),
        )
    }

    /**
     * 🔴 빨개지는 경우: 모르는 오류를 **성공으로 취급**하게 바뀌었을 때.
     *
     * GoTrue의 `404`는 "없는 주소"와 "꺼진 기능"이 겹친다(실측:
     * `manual_linking_disabled`가 404로 온다). 코드로 판단하면 갈릴 수 없다.
     */
    @Test
    fun 서버_오류코드를_원인별로_가른다() {
        assertEquals(KakaoLogin.LinkFailure.NONE, KakaoLogin.linkFailureReason(null))
        assertEquals(
            "실측된 코드다. 이 매핑이 빠지면 '연결이 불안정해요'로 뭉개진다",
            KakaoLogin.LinkFailure.SERVER_FEATURE_OFF,
            KakaoLogin.linkFailureReason("manual_linking_disabled"),
        )
        assertEquals(
            KakaoLogin.LinkFailure.PROVIDER_OFF,
            KakaoLogin.linkFailureReason("provider_disabled"),
        )
        assertEquals(
            KakaoLogin.LinkFailure.ALREADY_LINKED_ELSEWHERE,
            KakaoLogin.linkFailureReason("identity_already_exists"),
        )
        // 🔴 모르는 값은 UNKNOWN이다 — **NONE이 아니다.** NONE으로 떨어지면
        //    "실패가 아니다"가 되어 새 계정을 만드는 경로로 이어진다.
        assertEquals(
            "모르는 실패가 성공으로 취급된다 — 그게 기록을 잃는 경로다",
            KakaoLogin.LinkFailure.UNKNOWN,
            KakaoLogin.linkFailureReason("something_new_from_gotrue"),
        )
    }

    /**
     * 🔴 빨개지는 경우: 연결용 엔드포인트와 새 로그인용 엔드포인트가 **같은 경로**로
     *    바뀌었을 때.
     *
     * 둘은 결과가 정반대다:
     * - `/auth/v1/authorize` → **새 세션**. 익명 uuid를 버린다 (도감이 끊긴다)
     * - `/auth/v1/user/identities/authorize` → **uuid 유지**. 이게 우리가 원하는 것
     *
     * ⚠️ 문자열이라서 컴파일은 통하고, 실행하면 **로그인이 성공한다** — 성공하면서
     *    계정이 갈린다. 그래서 증상이 "오류"가 아니라 "도감이 비었다"다.
     *
     * ⚠️ **`Uri`를 부르지 않고 상수 문자열로 잰다** (클래스 주석 참고).
     */
    @Test
    fun 계정_연결_경로와_새_로그인_경로를_섞지_않는다() {
        val src = source("main", "data/KakaoLogin.kt")
        assertTrue(
            "새 로그인 경로(/auth/v1/authorize)가 없어졌다",
            src.contains("\"\$baseUrl/auth/v1/authorize\""),
        )
        assertTrue(
            "계정 연결 경로가 없어졌다 — 이 경로만이 uuid를 유지한다",
            src.contains("\"\$baseUrl/auth/v1/user/identities/authorize\""),
        )
        // 연결 함수가 새 로그인 경로를 쓰고 있지 않은가. 두 함수의 본문을 갈라서 본다.
        val link = src.substringAfter("fun linkIdentity(").substringBefore("fun idTokenGrant(")
        assertTrue(
            "linkIdentity가 identities 경로를 쓰지 않는다 — uuid가 갈린다",
            link.contains("user/identities/authorize"),
        )
    }

    /**
     * 빨개지는 경우: 리다이렉트 스킴이 `applicationId`와 같아졌을 때.
     * 스킴이 겹치면 안드로이드가 **앱 선택 대화상자**를 띄우고, 사용자는 무엇을
     * 고르는지 모른다 — 잘못 고르면 로그인이 끝나지 않는다.
     */
    @Test
    fun 리다이렉트_스킴은_앱_고유값이다() {
        val redirect = SupabaseAuthUrls.APP_REDIRECT
        assertTrue("스킴이 //를 포함해야 한다", redirect.contains("://"))
        assertFalse(
            "applicationId를 스킴으로 쓰면 다른 앱이 가로챌 수 있다",
            redirect.startsWith("com.catchflower.app:"),
        )
        assertEquals("kakao", SupabaseAuthUrls.PROVIDER_KAKAO)
    }

    /**
     * 🔴 빨개지는 경우: **화면이 이 층을 부르기 시작했을 때.**
     *
     * 지금 화면 01은 없다 — A 문서 3절이 그 이유를 적었다(버튼 3개가 전부 눌릴 수
     * 없어서 만들면 죽은 버튼 화면이 된다). 그런데 **부르는 곳이 없는 코드는
     * "됐다"고 착각하게 만드는 대표적인 자리**다([ReactionContractTest]와 같은 검사다).
     *
     * 화면이 붙는 날 이 테스트가 빨개지고, 그때 A 문서 3절 표의 문구를 쓰게 된다.
     */
    @Test
    fun 아직_어느_화면도_카카오_로그인을_부르지_않는다() {
        val ui = File("src/main/java/com/catchflower/app/ui")
        assertTrue(ui.isDirectory)
        val callers = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("KakaoLogin") || t.contains("SupabaseAuthUrls")
            }
            .map { it.name }
            .toList()
        assertEquals(
            "화면이 카카오 로그인을 부르기 시작했다. 좋은 일이다 — 이 테스트와 " +
                "구현현황_AOS.md·KakaoLogin의 '아직 안 부른다' 주석, 그리고 A 문서 3절 " +
                "화면 01 표를 같이 고친다: $callers",
            emptyList<String>(),
            callers,
        )
    }

    /**
     * 🔴 빨개지는 경우: 위 [아직_어느_화면도_카카오_로그인을_부르지_않는다]가
     *    막고 있는 사실이 **A 문서에서 사라졌을** 때.
     *
     * 코드에만 적어 두면 화면을 만드는 사람이 문구를 **새로 지어낸다**(규칙 위반).
     * 3절에 표가 살아 있어야 그날 지어낼 필요가 없다.
     */
    @Test
    fun 화면_01의_눌리지_않는_상태_문구가_A문서에_있다() {
        val a = File(projectRoot, "디자이너_업무/A_문구·버튼_스펙.md").readText()
        assertTrue(
            "A 문서 3절에 화면 01 Disabled 표가 없다 — 화면을 만드는 날 문구를 지어내게 된다",
            a.contains("화면 01에서 **로그인 버튼을 누를 수 없을 때**"),
        )
        for (copy in listOf("카카오 로그인 준비 중이에요", "지금은 로그인 없이 바로 쓸 수 있어요")) {
            assertTrue("A 문서에 `$copy`가 없다", a.contains(copy))
        }
        // 🔴 원인 문구를 `연결이 불안정해요`로 쓰지 말라는 근거가 남아 있어야 한다.
        //    서버 설정 문제라 재시도해도 영원히 같은 결과다.
        assertTrue(
            "재시도 문구를 쓰지 말라는 근거가 사라졌다",
            a.contains("manual_linking_disabled"),
        )
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError(
            "프로젝트 루트를 못 찾았다. 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 검증이 조용히 사라진다",
        )
    }

    private fun source(set: String, path: String): String {
        val f = File("src/$set/java/com/catchflower/app/$path")
        assertTrue("$path 를 못 찾았다", f.isFile)
        return f.readText()
    }
}
