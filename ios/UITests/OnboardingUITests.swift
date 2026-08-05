import XCTest

/// 화면 03 권한 안내.
///
/// **권한 대화상자는 여기서 검증하지 않는다.** 시스템 대화상자는 앱 밖(springboard)이고,
/// 시뮬레이터에서는 카메라 권한 대화상자가 아예 뜨지 않는다(장치가 없다).
/// 이 테스트가 보는 것은 **화면 03이 뜨는가 · 문구가 스펙대로인가 · 지나면 도감으로 가는가**다.
final class OnboardingUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// **`-uiTestForceOnboarding`을 쓴다.** 통과 여부가 `UserDefaults`에 남아서
    /// 두 번째 실행부터는 온보딩이 안 뜬다 — 상태를 지우는 대신 무시하게 만든다.
    private func launchOnboarding() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            LaunchArgument.temporaryStorage,
            LaunchArgument.storageID, uiTestStorageID,
            LaunchArgument.reset,
            LaunchArgument.forceOnboarding,
        ]
        app.launch()
        return app
    }

    func test_권한안내_문구가_스펙대로다() {
        let app = launchOnboarding()

        XCTAssertTrue(
            app.staticTexts["이 세 가지만 허용하면 준비 끝!"].waitForExistence(timeout: 10),
            "화면 03이 뜨지 않았다"
        )
        XCTAssertTrue(app.staticTexts["권한 안내"].exists)
        XCTAssertTrue(app.staticTexts["2/2"].exists)
        XCTAssertTrue(
            app.staticTexts["허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요."].exists
        )

        // 권한 3종. 목적과 제약을 **둘 다** 보여줘야 한다 —
        // `앨범 사진은 등록할 수 없어요`는 게임 규칙이라 여기서 미리 알려야 한다.
        for line in [
            "꽃을 직접 촬영해 도감에 등록합니다.",
            "앨범 사진은 등록할 수 없어요.",
            "꽃을 발견한 장소를 지도에 남깁니다.",
            "끄면 지도 공유를 쓸 수 없어요.",
            "이미 가입한 지인을 친구로 연결합니다.",
            "번호는 암호화해 보관하며 저장하지 않아요.",
        ] {
            XCTAssertTrue(app.staticTexts[line].exists, "권한 설명 누락: \(line)")
        }

        // 안심 박스 — '자동 공개가 아니다'를 가입 단계에서 못 박는다.
        XCTAssertTrue(app.staticTexts["촬영한 사진은 내 도감에만 저장됩니다."].exists)
        XCTAssertTrue(app.staticTexts["지도 공유는 매번 직접 선택해요."].exists)

        XCTAssertTrue(app.buttons["허용하고 시작하기"].exists)
        XCTAssertTrue(app.staticTexts["나중에 설정에서 바꿀 수 있어요"].exists)
    }

    /// 필수/선택을 **글자로도** 구분한다. 색이나 토글만으로 구분하면
    /// 색각 이상·저시력 사용자에게 정보가 사라진다.
    func test_필수와_선택이_글자로_구분된다() {
        let app = launchOnboarding()
        XCTAssertTrue(app.staticTexts["카메라"].waitForExistence(timeout: 10))

        XCTAssertTrue(app.staticTexts["필수"].exists, "카메라 `필수` 배지가 없다")
        // 위치·연락처 둘이 `선택`이다.
        XCTAssertEqual(
            app.staticTexts.matching(identifier: "선택").count, 2,
            "`선택` 배지가 2개가 아니다"
        )
    }

    /// **권한을 거부해도 도감으로 넘어간다.** 시뮬레이터에서는 권한 대화상자가
    /// 뜨지 않거나 자동으로 처리되는데, 어느 쪽이든 **막혀서는 안 된다** —
    /// `허용하지 않아도 도감은 쓸 수 있지만…`이 약속이다.
    func test_허용하고_시작하면_도감으로_간다() {
        let app = launchOnboarding()
        XCTAssertTrue(app.buttons["허용하고 시작하기"].waitForExistence(timeout: 10))

        // 시스템 대화상자가 뜨면 눌러서 치운다. 안 뜨는 게 정상인 권한도 있어서
        // 존재를 단정하지 않는다.
        addUIInterruptionMonitor(withDescription: "권한 대화상자") { alert in
            for label in ["허용", "App을 사용하는 동안 허용", "OK", "Allow"] {
                let button = alert.buttons[label]
                if button.exists {
                    button.tap()
                    return true
                }
            }
            return false
        }

        app.buttons["허용하고 시작하기"].tap()
        // 대화상자 처리를 유발하려면 앱을 한 번 건드려야 한다 (XCUITest 특성).
        app.tap()

        // **여유를 넉넉히 준다.** 권한 대화상자 처리는 시뮬레이터 부하에 따라 느리다.
        // 다만 앱 쪽에도 상한이 있어서(60초) 대화상자가 안 돌아와도 화면은 넘어간다 —
        // 실제로 연락처 요청이 응답 없이 멈춰서 CTA가 영구 비활성이 됐고, 그래서 넣었다.
        XCTAssertTrue(
            app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 90),
            "권한 절차가 끝났는데 도감으로 넘어가지 않았다"
        )
    }
}
