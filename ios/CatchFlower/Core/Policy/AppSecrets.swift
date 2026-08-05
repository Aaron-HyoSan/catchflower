import Foundation

/// 빌드 시점에 주입된 키를 읽는다.
///
/// **키는 소스에 없다.** `Config/Secrets.xcconfig`(gitignore) → `Info.plist` → 여기.
/// 새 맥에서는 `Config/Secrets_TEMPLATE.xcconfig`를 복사해 값을 채운다.
///
/// **비어 있어도 앱은 죽지 않는다.** 키가 없으면 그 기능만 꺼진다 —
/// 키 하나 빠졌다고 도감을 못 보게 만들 이유가 없다. 대신 `missingKeys`로 알린다.
enum AppSecrets {

    static var kakaoRESTAPIKey: String { value("KakaoRESTAPIKey") }
    static var kakaoNativeAppKey: String { value("KakaoNativeAppKey") }
    static var plantNetAPIKey: String { value("PlantNetAPIKey") }
    static var supabaseURL: String { value("SupabaseURL") }
    static var supabaseAnonKey: String { value("SupabaseAnonKey") }

    /// 지도·장소 기능을 켤 수 있는가.
    static var hasKakaoKey: Bool { !kakaoRESTAPIKey.isEmpty }
    /// 실제 꽃 인식을 켤 수 있는가. 없으면 Mock으로 돈다.
    static var hasPlantNetKey: Bool { !plantNetAPIKey.isEmpty }
    /// 서버 기능(계정·랭킹·친구)을 켤 수 있는가.
    /// **키만으로는 부족하다** — 프로젝트 URL이 있어야 접속된다.
    static var hasSupabase: Bool { !supabaseURL.isEmpty && !supabaseAnonKey.isEmpty }

    /// 디버그 화면에서 무엇이 빠졌는지 보여준다.
    static var missingKeys: [String] {
        var missing: [String] = []
        if kakaoRESTAPIKey.isEmpty { missing.append("KAKAO_REST_API_KEY") }
        if plantNetAPIKey.isEmpty { missing.append("PLANTNET_API_KEY") }
        if supabaseURL.isEmpty { missing.append("SUPABASE_URL") }
        if supabaseAnonKey.isEmpty { missing.append("SUPABASE_ANON_KEY") }
        return missing
    }

    /// `Info.plist`에서 읽는다. xcconfig 변수가 안 채워지면 `$(NAME)`이 그대로 남으므로
    /// 그것도 빈 값으로 본다 — 안 그러면 `$(KAKAO_REST_API_KEY)`를 키로 보내게 된다.
    private static func value(_ key: String) -> String {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return ""
        }
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        return trimmed.hasPrefix("$(") ? "" : trimmed
    }
}
