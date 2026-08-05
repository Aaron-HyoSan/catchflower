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
    /// 모양이 틀린 URL은 켜지 않는다 (`supabaseURLProblem` 참고).
    static var hasSupabase: Bool {
        !supabaseURL.isEmpty && !supabaseAnonKey.isEmpty && supabaseURLProblem == nil
    }

    /// Supabase URL의 **모양**이 틀렸으면 이유를 준다. 정상이면 nil.
    ///
    /// **두 실수가 실제로 났기 때문에 있는 검사다.**
    /// ① 오너가 준 값이 `https://<id>.supabase.co/rest/v1/`였다 — 그건 REST
    ///    엔드포인트고 Project URL이 아니다. 그대로 두면 클라이언트가 경로를
    ///    한 번 더 붙여 `/rest/v1/rest/v1/...`을 만든다.
    /// ② xcconfig에서 `//`는 주석이라 `https://...`를 적으면 **`https:`까지만 남는다.**
    ///    잘린 값도 `isEmpty`가 아니라서 "키가 있다"로 통과해 버린다.
    ///
    /// 둘 다 조용히 틀리고, 증상은 런타임 네트워크 오류로만 나타난다 —
    /// 그러면 원인을 서버·RLS·네트워크에서 찾게 된다. 여기서 잡는다.
    static var supabaseURLProblem: String? { problem(inSupabaseURL: supabaseURL) }

    /// 위 검사의 순수 함수 형태. **주입값이 아니라 인자를 본다.**
    ///
    /// 나눠 둔 이유: `supabaseURLProblem`은 `Info.plist`를 읽어서
    /// **테스트가 실패 사례를 만들 수 없다.** 잘린 URL·경로 붙은 URL을
    /// 실제로 넣어 봐야 이 검사가 도는지 알 수 있다.
    static func problem(inSupabaseURL url: String) -> String? {
        guard !url.isEmpty else { return nil }   // 안 넣은 것은 이 검사의 대상이 아니다
        guard let parsed = URL(string: url), let host = parsed.host, !host.isEmpty else {
            // `https:`만 남은 경우가 여기로 온다 — xcconfig의 `//` 주석에 잘린 것이다.
            return "호스트가 없다(`\(url)`). xcconfig에서 `//`가 주석이라 잘렸을 수 있다 — "
                 + "`https:$(SLASH)$(SLASH)...` 형태로 쓴다."
        }
        guard parsed.scheme == "https" else {
            return "https가 아니다(`\(parsed.scheme ?? "없음")`)."
        }
        // 경로가 붙어 있으면 잘라 쓰지 않고 **거부한다.** 조용히 고쳐 주면
        // 설정 파일과 실제 동작이 어긋난 채로 남는다.
        let path = parsed.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard path.isEmpty else {
            return "경로가 붙어 있다(`/\(path)`). Project URL은 호스트까지다 — "
                 + "`/rest/v1` 같은 경로는 클라이언트가 붙인다."
        }
        return nil
    }

    /// 디버그 화면에서 무엇이 빠졌는지 보여준다.
    static var missingKeys: [String] {
        var missing: [String] = []
        if kakaoRESTAPIKey.isEmpty { missing.append("KAKAO_REST_API_KEY") }
        if plantNetAPIKey.isEmpty { missing.append("PLANTNET_API_KEY") }
        if supabaseURL.isEmpty { missing.append("SUPABASE_URL") }
        if supabaseAnonKey.isEmpty { missing.append("SUPABASE_ANON_KEY") }
        // **값이 있어도 모양이 틀리면 없는 것과 같다.** 빈 값만 세면
        // "키 다 들어왔는데 왜 안 되지"로 시간을 쓴다.
        if let problem = supabaseURLProblem { missing.append("SUPABASE_URL(\(problem))") }
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
