package com.catchflower.app.data

import android.content.Context

/**
 * 비로그인 사용량과 **카카오 연결 여부**를 기억한다.
 *
 * `LoginGate`가 판정에 쓰는 두 값([identifyCount]·[kakaoLinked])의 저장소다.
 * 판정은 [com.catchflower.app.core.LoginGate]에, 저장은 여기에 — 그래서 판정은
 * `Context` 없이 JVM에서 테스트되고, 여기는 값을 넣고 꺼내는 일만 한다.
 *
 * ## 🔴 `kakaoLinked`가 왜 로컬 플래그인가
 *
 * 서버에 물어보려면 `GET /auth/v1/user`의 `identities`를 봐야 하는데,
 * [AuthService.Transport]에는 **`post`밖에 없다** — `get`을 추가하면 그 인터페이스를
 * 구현한 **모든 테스트 가짜**를 같이 고쳐야 한다. 그 비용을 지금 낼 이유가 약하다:
 *
 * ⚠️ 로컬 플래그가 서버와 어긋나면 어떻게 되나.
 *    - **플래그가 false인데 서버는 연결됨**(앱 데이터 삭제 후 같은 계정 복구 등):
 *      게이트가 다시 걸린다 → 사용자가 카카오 로그인을 한 번 더 누른다 →
 *      `identity_already_exists`가 아니라 **자기 계정이라 그냥 통과**한다.
 *      즉 **불편할 뿐, 기록은 잃지 않는다.**
 *    - **플래그가 true인데 서버는 미연결**: 게이트가 안 걸린다 → 판별을 계속 쓴다.
 *      **쿼터가 새는 것**이고 데이터 손실은 아니다.
 *    두 방향 다 손실이 없어서 로컬로 둔다. 서버 판정이 필요해지는 것은 쿼터를
 *    지켜야 할 때이고, 그때는 **서버에서 세야 한다**(로컬 카운터로는 원리상 못 막는다).
 *
 * ## ⚠️ `AuthService`와 **같은 prefs 파일**을 쓴다
 *
 * 파일 이름이 `catchflower`로 같고 키만 다르다([K_IDENTIFY_COUNT]·[K_KAKAO_LINKED]).
 * 🔴 **`AuthService.reset()`이 이 키들을 지우지 않는다** — 일부러다. reset은
 * "세션을 버리고 새 계정을 만든다"는 복구 경로인데, 그때 판별 횟수까지 0이 되면
 * **복구를 반복해서 무료 판별을 무한으로 늘릴 수 있다.**
 */
interface AnonymousUsage {

    /** 지금까지 **성공한** 판별 누계. 성공의 정의는 `IdentifyOutcome.countsAsSuccess`. */
    fun identifyCount(): Int

    /**
     * 판별 성공 1건을 더한다.
     *
     * ⚠️ **성공했을 때만 부른다.** 화면 12(floor 미달)·네트워크 실패에서 부르면
     *    우리 쪽 사정으로 남의 횟수를 깎는 것이 된다(공유계약 3절).
     */
    fun recordIdentify()

    /** 카카오 계정이 이 익명 uuid에 연결됐는가. */
    fun kakaoLinked(): Boolean

    /**
     * 연결 성공을 기록한다.
     *
     * 🔴 **연결이 실제로 성공한 뒤에만 부른다.** `KakaoLogin.LinkFailure.UNKNOWN`에서
     *    부르면 **게이트만 열리고 계정은 안 붙는다** — 그 기기의 기록은 익명 uuid에
     *    남고, 앱 데이터를 지우면 사라진다. 화면에는 아무 증상이 없다.
     */
    fun markKakaoLinked()

    companion object {
        /** 실제 저장소. 테스트는 이걸 부르지 않고 가짜를 넣는다. */
        fun get(context: Context): AnonymousUsage = PrefsAnonymousUsage(context)
    }
}

/**
 * [AnonymousUsage]의 SharedPreferences 구현.
 *
 * ⚠️ **`apply()`를 쓴다**(`commit()`이 아니다). 촬영 직후 메인 스레드에서 불리는
 *    경로라 디스크 쓰기를 기다리면 프레임이 밀린다. 즉시 읽어도 같은 값이 나온다
 *    (같은 프로세스에서는 메모리 캐시가 먼저 갱신된다).
 */
internal class PrefsAnonymousUsage(private val context: Context) : AnonymousUsage {

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun identifyCount(): Int = prefs().getInt(K_IDENTIFY_COUNT, 0)

    override fun recordIdentify() {
        val p = prefs()
        // ⚠️ 상한을 두지 않는다. `>= 한도`로만 판정하므로 커져도 판정은 같고,
        //    "몇 번 썼나"는 나중에 문구(`n회 남았어요`)나 진단에 쓸 수 있다.
        p.edit().putInt(K_IDENTIFY_COUNT, p.getInt(K_IDENTIFY_COUNT, 0) + 1).apply()
    }

    override fun kakaoLinked(): Boolean = prefs().getBoolean(K_KAKAO_LINKED, false)

    override fun markKakaoLinked() {
        // 🔴 카운터를 **0으로 되돌리지 않는다.** 되돌리면 연결을 끊었을 때
        //    (또는 플래그만 false가 됐을 때) 무료 2회가 되살아난다.
        prefs().edit().putBoolean(K_KAKAO_LINKED, true).apply()
    }

    companion object {
        /** [AuthService]와 같은 파일이다. 클래스 주석 참조. */
        private const val PREFS = "catchflower"
        private const val K_IDENTIFY_COUNT = "anon_identify_count"
        private const val K_KAKAO_LINKED = "auth_kakao_linked"
    }
}
