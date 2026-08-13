package com.catchflower.app.core

import com.catchflower.app.BuildConfig

/**
 * 빌드 시점에 주입된 키를 읽는다.
 *
 * **키는 소스에 없다.** `local.properties`(gitignore) → `buildConfigField` → 여기.
 * 새 맥에서는 `android/local.properties.template`을 복사해 값을 채운다.
 * iOS 대응물은 `AppSecrets.swift`(`Secrets.xcconfig` → `Info.plist` → 읽기)다.
 *
 * **비어 있어도 앱은 죽지 않는다.** 키가 없으면 그 기능만 꺼진다 —
 * 키 하나 빠졌다고 도감을 못 보게 만들 이유가 없다. 대신 [missingKeys]로 알린다.
 */
object AppSecrets {

    /**
     * Supabase 프로젝트 루트 URL.
     *
     * ⚠️ 오너가 준 값은 `.../rest/v1/`까지 붙어 있었다. 그걸 그대로 두면
     *    클라이언트가 경로를 또 붙여 `/rest/v1/rest/v1/...`로 404가 난다.
     *    **여기서 한 번 정규화한다** — 설정 파일에 무엇이 들어와도 루트로 만든다.
     *    404는 "URL이 틀렸다"가 아니라 "테이블이 없다"로 읽히기 때문에 특히 위험하다.
     */
    val supabaseUrl: String
        get() = normalizeSupabaseUrl(BuildConfig.SUPABASE_URL)

    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()
    val plantNetApiKey: String get() = BuildConfig.PLANTNET_API_KEY.trim()
    val kakaoRestApiKey: String get() = BuildConfig.KAKAO_REST_API_KEY.trim()
    val kakaoNativeAppKey: String get() = BuildConfig.KAKAO_NATIVE_APP_KEY.trim()

    /**
     * 화면 20-2 `고객문의`가 열 메일 주소. **비어 있으면 그 행을 그리지 않는다**
     * ([SettingsRules.rows]).
     *
     * 🔴 **[missingKeys]에 넣지 않는다.** 키가 아니라 창구 주소이고, 지금 빌드에는
     *    없는 것이 정상이다(A 문서 4절 17번 — 오너 미확정). 여기 넣으면 디버그
     *    화면이 매번 "키가 빠졌다"고 말해서 **진짜 빠진 키가 묻힌다.**
     */
    val contactEmail: String get() = BuildConfig.CONTACT_EMAIL.trim()

    /**
     * 서버 기능(계정·랭킹·친구)을 켤 수 있는가.
     * **키만으로는 부족하다** — 프로젝트 URL이 있어야 접속된다.
     */
    val hasSupabase: Boolean get() = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()

    /** 실제 꽃 인식을 켤 수 있는가. 없으면 Mock으로 돈다. */
    val hasPlantNetKey: Boolean get() = plantNetApiKey.isNotEmpty()

    /**
     * 주소↔좌표 되짚기(화면 02·촬영 장소명)를 켤 수 있는가.
     *
     * 🔴 **지도 타일과 다른 키다.** REST 키는 카카오 로컬 API용이고, 지도 SDK는
     *    [hasKakaoMapKey]의 **네이티브 앱 키**로 초기화한다. 하나로 뭉쳐서 보면
     *    REST 키만 넣은 빌드가 "지도도 된다"고 말하고, **회색 화면**이 나온다.
     */
    val hasKakaoKey: Boolean get() = kakaoRestApiKey.isNotEmpty()

    /**
     * 지도(화면 14~16)를 켤 수 있는가.
     *
     * ⚠️ 키가 있어도 **콘솔에 키 해시·패키지명이 등록돼야** 타일이 내려온다.
     *    등록 전에는 초기화가 성공하고 화면만 회색이다 — 그래서 지도 실패는
     *    **로그로만 보인다**([MapView] 콜백을 반드시 남긴다).
     *
     * 🔴 **`MapAuthException(401)`을 인증 실패로만 읽지 마라.** SDK가 **DNS 실패도
     *    401로 감싼다**(2026-08-09 실측). 원인은 인증 엔드포인트 응답 **본문**에
     *    문장으로 있다 — `e.message`에는 없다((35)).
     */
    val hasKakaoMapKey: Boolean get() = kakaoNativeAppKey.isNotEmpty()

    /** 디버그 화면에서 무엇이 빠졌는지 보여준다. */
    val missingKeys: List<String>
        get() = buildList {
            if (supabaseUrl.isEmpty()) add("SUPABASE_URL")
            if (supabaseAnonKey.isEmpty()) add("SUPABASE_ANON_KEY")
            if (plantNetApiKey.isEmpty()) add("PLANTNET_API_KEY")
            if (kakaoRestApiKey.isEmpty()) add("KAKAO_REST_API_KEY")
            // ⚠️ 지도 키를 여기 빼 두면 "키 다 들어왔다"는 로그가 뜨는데 지도만
            //    회색이다. 위 [hasKakaoKey] 주석의 사고가 그것이다.
            if (kakaoNativeAppKey.isEmpty()) add("KAKAO_NATIVE_APP_KEY")
        }

    /**
     * `https://xxx.supabase.co/rest/v1/` → `https://xxx.supabase.co`
     *
     * 끝의 `/`와 알려진 API 경로 접미를 벗긴다. 테스트가 이 동작을 고정한다.
     */
    internal fun normalizeSupabaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        // `/rest/v1`(PostgREST) · `/auth/v1`(GoTrue) — 클라이언트가 직접 붙이는 경로들.
        for (suffix in listOf("/rest/v1", "/auth/v1", "/storage/v1")) {
            if (url.endsWith(suffix)) url = url.removeSuffix(suffix)
        }
        return url.trimEnd('/')
    }
}
