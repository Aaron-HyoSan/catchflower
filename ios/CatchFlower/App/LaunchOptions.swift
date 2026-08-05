import Foundation

/// 실행 인자로 앱 상태를 지정한다. **UI 테스트 전용이다.**
///
/// **왜 필요해졌나.** 저장을 붙이자마자 UI 테스트 6개가 깨졌다 —
/// 앞 테스트가 등록한 꽃이 다음 테스트에 그대로 남아서
/// "빈 도감" 검증이 실패하고, **B-5가 같은 꽃 재촬영을 정상적으로 막았다.**
/// 테스트가 잘못된 게 아니라 **격리가 없던 것**이다.
///
/// 저장을 끄는 게 아니라 **테스트마다 새 저장소를 준다** —
/// 저장을 끄면 "저장이 되는가"를 UI에서 검증할 수 없다.
struct LaunchOptions: Sendable {

    /// 기록·사진을 임시 폴더에 둔다. 테스트 간 상태가 새지 않는다.
    var usesTemporaryStorage = false
    /// 시작 시 저장된 기록을 지운다.
    var resetsStorage = false
    /// 임시 폴더 이름. **테스트가 정한다.**
    ///
    /// 프로세스 ID로 만들면 안 된다 — `app.terminate()` 후 다시 켜면 PID가 바뀌어
    /// 저장 위치가 옮겨간다. 그러면 "껐다 켜도 남아있는가"를 검증할 수 없다
    /// (저장이 잘 돼도 빈 폴더를 보게 된다).
    var storageID = "default"

    /// 판별을 느리게 만든다. **분석 중 취소(화면 08)를 검증하려면 필요하다.**
    ///
    /// Mock 기본 지연은 1.5초다. 그 안에 `취소`를 찾아 누르는 건 시뮬레이터가
    /// 한가할 때만 되고, 테스트를 여럿 같이 돌리면 판별이 먼저 끝나서 **화면이
    /// 넘어가 버린다.** 실제로 단독 실행은 통과, 전체 실행은 실패했다 —
    /// 타이밍에 기대는 테스트를 타이밍을 정해주는 테스트로 바꾼다.
    var slowsIdentification = false

    /// 화면 03(권한 안내)을 건너뛴다.
    ///
    /// **UI 테스트 대부분은 도감부터 시작해야 한다.** 온보딩 게이트를 붙인 순간
    /// 23개가 전부 권한 화면에서 멈춘다 — 테스트마다 권한 화면을 통과하는 코드를
    /// 붙이면 실제로 검증하려는 것과 무관한 절차가 23번 복제된다.
    ///
    /// **기본값을 "건너뛴다"로 두지 않았다.** 그러면 화면 03이 테스트에서
    /// 영원히 안 보인다. 테스트가 명시적으로 켜고 끈다.
    var skipsOnboarding = false

    /// 화면 03을 **무조건** 띄운다. `skipsOnboarding`보다 우선한다.
    ///
    /// 온보딩 통과 여부는 `UserDefaults`에 남아서 앱을 지우기 전까지 유지된다.
    /// 그래서 "온보딩이 뜨는가"를 검증하는 테스트가 **앞선 테스트의 실행 여부에
    /// 좌우된다** — 단독으로 돌리면 통과하고 전체로 돌리면 실패하는 종류다.
    /// 상태를 지우는 대신 **상태를 무시하게** 만든다.
    var forcesOnboarding = false

    static let temporaryStorageFlag = "-uiTestTemporaryStorage"
    static let resetFlag = "-uiTestReset"
    static let storageIDFlag = "-uiTestStorageID"
    static let slowIdentifyFlag = "-uiTestSlowIdentify"
    static let skipOnboardingFlag = "-uiTestSkipOnboarding"
    static let forceOnboardingFlag = "-uiTestForceOnboarding"

    init(arguments: [String] = []) {
        usesTemporaryStorage = arguments.contains(Self.temporaryStorageFlag)
        resetsStorage = arguments.contains(Self.resetFlag)
        slowsIdentification = arguments.contains(Self.slowIdentifyFlag)
        skipsOnboarding = arguments.contains(Self.skipOnboardingFlag)
        forcesOnboarding = arguments.contains(Self.forceOnboardingFlag)
        if let index = arguments.firstIndex(of: Self.storageIDFlag),
           index + 1 < arguments.count {
            storageID = arguments[index + 1]
        }
    }

    /// 실행 인자로 지정된 저장 위치. 기본값이면 nil이다.
    var storageRoot: URL? {
        guard usesTemporaryStorage else { return nil }
        return FileManager.default.temporaryDirectory
            .appendingPathComponent("uitest-\(storageID)")
    }
}
