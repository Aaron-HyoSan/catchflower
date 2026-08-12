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
 * ## ✅ 서버가 열렸다 (2026-08-12 실측) — 그래서 [canLinkToExistingAccount]가 true다
 *
 * 오너가 Manual Linking을 켜고 저장한 뒤 익명 계정으로 다시 태웠다:
 *
 * ```
 * kakao                → 302  https://kauth.kakao.com/oauth/authorize?client_id=…
 * google               → 400  validation_failed · Unsupported provider: provider is not enabled
 * nonexistent_prov_xyz → 400  validation_failed · Provider … could not be found
 * ```
 *
 * 🔵 **대조군이 갈렸다는 것이 이 측정의 핵심이다.** 켜기 전에는 셋 다 똑같이
 *    `404 manual_linking_disabled`였다 — 그때는 provider를 보기도 전에 막혔다는 뜻이고,
 *    지금은 provider마다 답이 다르다. 즉 요청이 **provider 판정까지 도달한다.**
 *    셋이 여전히 같았다면 "켰다"는 말과 무관하게 안 켜진 것이다.
 *
 * ⚠️ **`kakao = true`는 `/auth/v1/settings`로도 확인된다** — provider 토글은 조회로
 *    알 수 있다(전에 "settings는 아무것도 안 알려준다"고 적었던 것은 과했다).
 *    다만 **manual linking 항목은 그 응답에 여전히 없다.** 그래서 이 상수는
 *    조회가 아니라 **실제 호출**로만 검증된다.
 *
 * ## 카카오 콘솔 ①②도 같이 확인됐다 (밖에서 잴 수 있었다)
 *
 * 위 302의 `Location`을 **한 번** 따라가니 `accounts.kakao.com/login`이 200으로 떴고
 * **`KOE###`가 하나도 없었다.** 카카오는 설정이 틀리면 거기서 오류코드를 준다:
 * `KOE006`(Redirect URI 미등록 = ②) · `KOE101`(로그인 활성화 OFF·앱키 불일치 = ①).
 * 즉 ①②가 둘 다 맞다. Supabase가 만든 요청은 이랬다:
 *
 * ```
 * redirect_uri  https://ngfkkazyvbbhrcznqkar.supabase.co/auth/v1/callback
 * scope         account_email profile_image profile_nickname
 * ```
 *
 * ✅ `profile_nickname`이 scope에 있다 — 랭킹에 쓸 이름이 온다(콘솔 ① 요구사항).
 *
 * ⚠️ **`client_id`가 네이티브 앱키와 다르다**(REST `52c37dc…` / 네이티브 `3bf6d46…`).
 *    **정상이다** — 같은 카카오 앱의 다른 키다(REST API 키 vs 네이티브 앱키).
 *    🔴 앞자리가 다른 것을 보고 "다른 앱을 등록했다"로 읽지 않는다. 지도 SDK는
 *    네이티브 앱키를, 로그인은 REST 키를 쓴다.
 *
 * ## 🔴 그래도 아직 **눌릴 화면이 없다** — 그리고 이게 지금 유일한 구멍이다
 *
 * 화면 01이 없다. 이 층은 **아무도 부르지 않는다**([KakaoLoginTest]가 그 사실을
 * 고정한다). 즉 상수를 true로 바꾼 것은 **버튼을 연 것이 아니라 자물쇠를 푼 것**이다.
 *
 * ⚠️ **화면을 붙이는 날 반드시 같이 해야 하는 것:**
 *    1. `AndroidManifest.xml`에 `catchflower://auth-callback` **intent-filter**.
 *       없으면 카카오 인증이 성공하고 브라우저가 그 주소를 열지만 **받는 앱이 없다** —
 *       사용자는 로그인이 끝났는데 앱은 그대로다(증상: "눌러도 안 돼요").
 *    2. `redirect_to`가 Supabase 콘솔 **Redirect URLs 허용목록**에 있어야 한다.
 *       🔴 **없으면 서버가 오류 대신 Site URL로 조용히 보낸다.**
 *       ⚠️ 이건 **아직 못 쟀다.** 재려고 `state`를 열어 봤는데 GoTrue의 `state`는
 *       JWT가 아니라 **불투명한 uuid**여서, 우리 값과 대조군(`bogusscheme://nope`)이
 *       **똑같은 답을 줬다** — 즉 그 계측은 아무것도 안 쟀다. 실제 콜백을 한 번
 *       완주해야 알 수 있다. **"확인했다"로 적지 않는다.**
 *
 * ## 왜 이 상수를 계속 남겨 두나 (지금은 true인데)
 *
 * 서버 설정은 **다시 꺼질 수 있다**(프로젝트 복제·무료플랜 초기화·오너의 다른 조작).
 * 그때 [linkFailureReason]이 `manual_linking_disabled`를 다시 받으면
 * [LinkFailure.SERVER_FEATURE_OFF]로 갈라 문구를 말한다 — 상수만 지우면 그 경로가
 * "모르는 실패"로 뭉개진다.
 */
object KakaoLogin {

    /**
     * Supabase가 **수동 계정 연결(manual linking)** 을 허용하는가.
     *
     * ✅ **2026-08-12: 오너가 켰고, 실측으로 확인한 뒤 `true`로 바꿨다.**
     *    `/auth/v1/user/identities/authorize?provider=kakao` → **302 → kauth.kakao.com**
     *    (그전에는 `404 manual_linking_disabled`였다.)
     *
     * 🔴 **서버 설정이다. 앱이 켤 수 없다.** 오너가 Supabase 콘솔에서
     *    `Authentication → Sign In / Providers → Manual Linking`을 켠 것이다.
     *
     * 🔴 **"켰다"는 말만 듣고 이 값을 바꾸지 않는다.** 실제로 그런 일이 있었다 —
     *    오너가 "열었다"고 알린 뒤 쟀을 때 서버는 **아직 꺼져 있었다**(`Save` 미클릭).
     *    말과 서버가 갈렸고, 그때 값을 바꿨으면 버튼이 열린 채로 기록을 잃었다.
     *    **두 번째 측정에서 302가 나온 뒤에** 바꿨다.
     *
     * ⚠️ **끌 때도 같다.** 서버가 다시 꺼지면 이 값을 false로 돌려야 하고, 그
     *    판단 근거는 실제 호출뿐이다 — `GET /auth/v1/settings`에는 **linking 항목이
     *    없다**(provider 토글은 있다). [linkFailureReason]이 런타임에 그 코드를 잡는다.
     */
    const val canLinkToExistingAccount: Boolean = true

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
