import Foundation
import XCTest

/// 앱에 넘기는 실행 인자. **원본은 앱 쪽 `LaunchOptions`다.**
///
/// UI 테스트 타깃은 앱 모듈을 import하지 않으므로(호스트 앱은 별도 프로세스다)
/// 문자열을 한 번 더 적는 수밖에 없다. 그래도 테스트 파일마다 흩뿌리지 않고
/// 여기 모아둔다 — 오타 하나로 격리가 조용히 꺼지면 원인 찾기가 매우 어렵다.
/// (`-uiTestReset` 대신 `-uiTestRest`를 적어도 앱은 아무 말 없이 잘 뜬다.)
enum LaunchArgument {
    static let temporaryStorage = "-uiTestTemporaryStorage"
    static let reset = "-uiTestReset"
    static let storageID = "-uiTestStorageID"
    static let slowIdentify = "-uiTestSlowIdentify"
}

extension XCTestCase {
    /// 테스트 이름에서 만든 저장소 ID.
    /// `-[CodexUITests test_빈도감은_화면22를_보여준다]` → `CodexUITests-test-빈도감은...`
    var uiTestStorageID: String {
        name.components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "-")
    }

    /// 격리된 저장소로 앱을 띄운다.
    func launchIsolatedApp(reset: Bool = true) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            LaunchArgument.temporaryStorage,
            LaunchArgument.storageID, uiTestStorageID,
        ]
        if reset { app.launchArguments.append(LaunchArgument.reset) }
        app.launch()
        return app
    }
}
