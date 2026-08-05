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
     * 서버 기능(계정·랭킹·친구)을 켤 수 있는가.
     * **키만으로는 부족하다** — 프로젝트 URL이 있어야 접속된다.
     */
    val hasSupabase: Boolean get() = supabaseUrl.isNotEmpty() && supabaseAnonKey.isNotEmpty()

    /** 실제 꽃 인식을 켤 수 있는가. 없으면 Mock으로 돈다. */
    val hasPlantNetKey: Boolean get() = plantNetApiKey.isNotEmpty()

    /** 지도·장소 기능을 켤 수 있는가. */
    val hasKakaoKey: Boolean get() = kakaoRestApiKey.isNotEmpty()

    /** 디버그 화면에서 무엇이 빠졌는지 보여준다. */
    val missingKeys: List<String>
        get() = buildList {
            if (supabaseUrl.isEmpty()) add("SUPABASE_URL")
            if (supabaseAnonKey.isEmpty()) add("SUPABASE_ANON_KEY")
            if (plantNetApiKey.isEmpty()) add("PLANTNET_API_KEY")
            if (kakaoRestApiKey.isEmpty()) add("KAKAO_REST_API_KEY")
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
