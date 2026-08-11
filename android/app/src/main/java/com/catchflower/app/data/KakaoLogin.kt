package com.catchflower.app.data

import android.net.Uri

/**
 * 카카오 로그인(화면 01). **웹 OAuth 방식**이다 — 카카오 SDK를 넣지 않는다.
 *
 * ## 왜 웹 OAuth인가 (2026-08-12 실측으로 정했다)
 *
 * 두 가지 길이 있다:
 *
 * | 방식 | 흐름 | 필요한 것 |
 * |---|---|---|
 * | 웹 OAuth (이것) | 앱 → 브라우저 → 카카오 → Supabase `/callback` → 앱 | **없다** (콘솔 설정만) |
 * | 네이티브 SDK | 카카오톡 앱 → `id_token` → Supabase `token?grant_type=id_token` | 카카오 SDK 의존성 + 키해시 재등록 |
 *
 * 🔵 **처음에 "네이티브는 불가능하다(Supabase가 카카오 `id_token`을 거부한다)"고
 *    적었다. 틀렸다.** 그 판단의 근거가 `400 provider_disabled` 하나였는데,
 *    **그때 카카오 provider가 꺼져 있었다** — 즉 "지원 안 함"과 "꺼져 있음"을
 *    가릴 수 없는 응답으로 판단했다. `PGRST202`에 세 번 속은 것과 같은 모양이다.
 *
 *    대조군을 만들어 다시 쟀다:
 *
 *    ```
 *    kakao                → 400 provider_disabled  · Provider (issuer "https://kauth.kakao.com") is not enabled
 *    google               → 400 provider_disabled  · Provider (issuer "https://accounts.google.com") is not enabled
 *    nonexistent_prov_xyz → 400 validation_failed  · Custom OIDC provider "..." not allowed
 *    ```
 *
 *    **카카오는 구글과 같은 문장을 준다.** 구글 네이티브 `id_token` 로그인은 되는
 *    것이 알려져 있으므로, 카카오도 provider를 켜면 네이티브가 **된다.**
 *    ("안 된다"가 아니라 "아직 못 쟀다"였다.)
 *
 * ⚠️ **그래도 웹 OAuth로 간다.** 이유는 불가능해서가 아니다:
 *    1. 네이티브는 카카오 SDK(`com.kakao.sdk:v2-user`)를 더 넣어야 하고, 그 SDK는
 *       **키해시 등록**을 다시 요구한다 — 지도 SDK가 이미 그것으로 회색 화면을
 *       만들었고, 이 맥의 **디버그** 키스토어에만 등록돼 있다((35)·(36)).
 *       릴리스 서명 APK에서 조용히 죽는 경로를 하나 더 만드는 셈이다.
 *    2. 웹 OAuth는 **추가 키가 0개**다. 오너 콘솔 설정만으로 켜진다.
 *    3. 예선 범위 밖 기능이라(제출물/게임_소개.md) 의존성을 늘릴 이유가 약하다.
 *
 *    네이티브로 갈아탈 날에는 [SupabaseAuthUrls.idTokenGrant]가 이미 있다 —
 *    위 실측을 근거로 남겨 둔 자리다.
 *
 * ## 🔴 이 층이 **아직 로그인을 완성시키지 못한다** (서버가 막고 있다)
 *
 * 실측(2026-08-12 · 익명 계정으로 `/auth/v1/user/identities/authorize` 호출):
 *
 * ```
 * kakao                → 404 manual_linking_disabled · Manual linking is disabled
 * google               → 404 manual_linking_disabled
 * nonexistent_prov_xyz → 404 manual_linking_disabled   ← 대조군도 같다
 * ```
 *
 * 대조군까지 같은 응답이라는 건 **provider와 무관하게 기능 자체가 꺼져 있다**는 뜻이다.
 * 이게 왜 치명적인가: 지금 모든 사용자는 **익명 계정**이고 도감·업로드가 그 uuid에
 * 묶여 있다. 연결(linking) 없이 카카오로 로그인하면 **다른 uuid의 새 계정**이 생기고,
 * 그 순간 그동안의 기록은 [AuthService.reset] 주석이 말한 **주인 없는 데이터**가 된다 —
 * RLS(`auth.uid() = user_id`) 때문에 새 계정으로는 보이지도, 지우지도 못한다.
 * 그리고 **화면에는 도감이 그대로 보인다**(로컬 파일이므로). 증상이 없다.
 *
 * ⚠️ 그래서 [canLinkToExistingAccount]가 false인 동안 **카카오 버튼을 누를 수 없게**
 *    둔다. 눌리게 해 두고 "나중에 고치자"로 두면, 누른 사람의 기록이 사라진다.
 *    A 문서 1절의 `Disabled` 규칙대로 **왜 못 누르는지를 문구로** 말한다.
 */
object KakaoLogin {

    /**
     * Supabase가 **수동 계정 연결(manual linking)** 을 허용하는가.
     *
     * 🔴 **서버 설정이다. 앱이 켤 수 없다.** 오너가 Supabase 콘솔에서
     *    `Authentication → Sign In / Providers → Manual Linking`을 켜야 한다.
     *
     * ⚠️ **기본값을 true로 두지 않는다.** true로 두고 서버가 꺼져 있으면 사용자가
     *    버튼을 누르고 → 404를 받고 → 그 사이에 새 계정이 생기는 경로가 열린다.
     *    모르면 **막는 쪽**이 안전하다.
     *
     * ⚠️ 이 값을 상수로 박아 두는 것은 **임시**다. 켜진 뒤에는
     *    `GET /auth/v1/settings`가 알려주지 **않으므로**(그 응답에 linking 항목이
     *    없다 — 실측) 실제 호출의 `manual_linking_disabled` 유무로만 알 수 있다.
     *    그래서 [linkFailureReason]이 그 코드를 문구로 번역한다.
     */
    const val canLinkToExistingAccount: Boolean = false

    /**
     * 서버가 준 오류 코드를 **원인별로** 가른다.
     *
     * 🔴 **`404`를 "없는 주소"로 읽으면 안 된다.** GoTrue는 기능이 꺼져 있을 때도
     *    404를 준다 — 위 실측이 그것이다. 코드가 아니라 **본문의 `error_code`**로
     *    판단해야 한다. 카카오맵 401이 DNS 실패까지 감쌌던 것과 같은 종류다((34)).
     *
     * @param errorCode 응답 본문의 `error_code`. 없으면 null
     * @return 원인. 모르면 [LinkFailure.UNKNOWN] — **성공으로 취급하지 않는다**
     */
    fun linkFailureReason(errorCode: String?): LinkFailure = when (errorCode) {
        null -> LinkFailure.NONE
        "manual_linking_disabled" -> LinkFailure.SERVER_FEATURE_OFF
        "provider_disabled" -> LinkFailure.PROVIDER_OFF
        // 이미 그 카카오 계정이 다른 uuid에 붙어 있다. **새 계정을 만들면 안 된다** —
        // 그쪽이 진짜 주인이고, 이쪽 익명 기록을 옮길 방법이 없다.
        "identity_already_exists" -> LinkFailure.ALREADY_LINKED_ELSEWHERE
        else -> LinkFailure.UNKNOWN
    }

    enum class LinkFailure {
        /** 실패가 아니다. */
        NONE,

        /** 🔴 오너가 Supabase 콘솔에서 Manual Linking을 켜야 한다. */
        SERVER_FEATURE_OFF,

        /** 🔴 오너가 Supabase 콘솔에서 Kakao provider를 켜야 한다. */
        PROVIDER_OFF,

        /** 그 카카오 계정은 이미 다른 계정에 연결돼 있다. */
        ALREADY_LINKED_ELSEWHERE,

        /**
         * 모르는 실패.
         *
         * ⚠️ **낙관적으로 처리하지 않는다.** 모르는 실패에 "그냥 로그인시키자"로
         *    두면 그게 정확히 기록을 잃는 경로다.
         */
        UNKNOWN,
    }

    /**
     * 카카오 버튼을 **누를 수 있는가.**
     *
     * @param serverConfigured Supabase URL·anon 키가 있는가 ([AppSecrets.hasSupabase])
     */
    fun buttonEnabled(
        serverConfigured: Boolean,
        linkingAllowed: Boolean = canLinkToExistingAccount,
    ): Boolean = serverConfigured && linkingAllowed
}

/**
 * GoTrue 엔드포인트 조립. **문자열을 화면·서비스에 흩뿌리지 않는다.**
 *
 * ⚠️ `baseUrl`은 [com.catchflower.app.core.AppSecrets.normalizeSupabaseUrl]을 지난
 *    값이어야 한다. 그러지 않으면 `/auth/v1/auth/v1/...`이 되고, **404가 "그런
 *    provider 없음"으로 읽힌다.**
 */
object SupabaseAuthUrls {

    /**
     * 웹 OAuth 시작 주소. 브라우저로 이 주소를 연다.
     *
     * ⚠️ **`redirect_to`를 반드시 넣는다.** 없으면 카카오 인증 후 브라우저가
     *    Supabase의 **Site URL**로 가고 앱으로 돌아오지 않는다 — 사용자는 로그인이
     *    성공했는데 앱은 아무 일도 없는 상태로 남는다(증상: "눌러도 안 돼요").
     *
     * ⚠️ 이 값은 Supabase 콘솔의 **Redirect URLs 허용목록**에 있어야 한다.
     *    없으면 서버가 조용히 Site URL로 보낸다 — 오류가 아니라서 로그에도 안 남는다.
     */
    fun oauthAuthorize(baseUrl: String, provider: String, redirectTo: String): String =
        Uri.parse("$baseUrl/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_to", redirectTo)
            .build()
            .toString()

    /**
     * 기존(익명) 계정에 provider를 **연결**한다.
     *
     * 🔴 [oauthAuthorize]와 **다른 엔드포인트다.** 이쪽은 로그인한 사용자의
     *    액세스 토큰이 필요하고, 성공하면 **uuid가 유지된다.** 저쪽은 새 세션을
     *    만들고 익명 uuid를 버린다. 둘을 섞으면 도감이 사라진다.
     */
    fun linkIdentity(baseUrl: String, provider: String, redirectTo: String): String =
        Uri.parse("$baseUrl/auth/v1/user/identities/authorize").buildUpon()
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_to", redirectTo)
            .build()
            .toString()

    /**
     * 네이티브 SDK로 갈아탈 때 쓸 자리. **지금은 아무도 부르지 않는다.**
     *
     * 실측으로 이 경로가 살아 있다는 것만 확인해 뒀다(위 [KakaoLogin] 주석의 대조군).
     * 부르는 곳이 없는 코드는 "됐다"는 착각을 만들므로, 테스트가 이 사실을 고정한다.
     */
    fun idTokenGrant(baseUrl: String): String = "$baseUrl/auth/v1/token?grant_type=id_token"

    /**
     * 앱으로 돌아올 주소.
     *
     * ⚠️ **`applicationId`와 같은 값을 쓰지 않는다.** 스킴이 겹치면 다른 앱이
     *    가로챌 수 있고, 안드로이드는 그때 **선택 대화상자**를 띄운다(사용자가
     *    무엇을 고르는지 모른다). 앱 고유 스킴으로 둔다.
     */
    const val APP_REDIRECT = "catchflower://auth-callback"

    /** 카카오 provider 이름. GoTrue가 쓰는 문자열이다. */
    const val PROVIDER_KAKAO = "kakao"
}
