import XCTest

/// **실제 PlantNet을 부르는 테스트.** 다른 UI 테스트와 성격이 완전히 다르다.
///
/// 왜 따로 두는가:
/// - **네트워크와 외부 서비스에 의존한다.** 응답이 바뀌면 실패하는데 그건 우리 코드
///   문제가 아니다. 같은 스킴에 섞으면 CI가 남의 서버 상태에 흔들린다.
/// - **일일 쿼터(무료 500회)를 쓴다.** 테스트를 돌릴 때마다 줄어든다.
/// - 그래서 **기본 테스트 실행에서 빠져 있다** (`project.yml`의 scheme에 없다).
///
/// 돌리는 방법:
/// ```
/// xcodebuild test -project CatchFlower.xcodeproj -scheme CatchFlower \
///   -destination 'platform=iOS Simulator,name=iPhone 17' \
///   -only-testing:CatchFlowerUITests/RealRecognitionUITests
/// ```
///
/// **무엇을 검증하는가.** "`PlantNetRecognizer`가 앱 안에서 실제로 실행되는가"다.
/// 200장 실측(`ios/Tools/plantnet_measure.py`)은 스크립트가 한 것이라
/// **앱 코드를 한 줄도 거치지 않았다.** 이 테스트가 그 간극을 메운다 —
/// 응답 파싱·개화월 필터·학명 색인·화면 분기가 실제 응답으로 도는지 본다.
///
/// ⚠️ **점수를 단정하지 않는다.** 실측값(0.99/0.57/0.11)은 참고이고,
/// PlantNet이 모델을 갱신하면 바뀐다. 여기서는 **경로가 도는가**만 본다.
final class RealRecognitionUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// 실호출은 30초 타임아웃이 걸려 있다(`PlantNetRecognizer`). 넉넉히 준다.
    private let networkTimeout: TimeInterval = 45

    /// 네트워크 테스트 스위치. **파일이 있으면 켜진다.**
    ///
    /// 왜 파일인가 — 앞의 두 방법이 실제로 안 됐다:
    /// - `CF_RUN_NETWORK_TESTS=1 xcodebuild ...` → XCUITest 러너는 별도 프로세스라
    ///   `xcodebuild`의 환경을 물려받지 않는다 (skip 그대로였다)
    /// - `TEST_RUNNER_CF_...=1` → 이 배치에서 닿지 않았다
    /// - `-- -cfRunNetworkTests` → `xcodebuild: error: Unknown build action`
    ///
    /// 파일 존재 확인은 **프로세스 경계를 넘는다.** 스킴을 고치지 않아도 되고
    /// 실수로 CI에서 켜질 일도 없다(파일을 만들어야 켜진다).
    ///
    /// ⚠️ `NSTemporaryDirectory()`를 쓰면 안 된다 — 그건 **앱 샌드박스 안**이라
    /// 맥 터미널에서 `touch`할 수 없다. `SIMULATOR_SHARED_RESOURCES_DIRECTORY`가
    /// 시뮬레이터와 호스트가 **같이 보는** 경로다.
    private static var switchFile: URL? {
        guard let shared = ProcessInfo.processInfo
            .environment["SIMULATOR_SHARED_RESOURCES_DIRECTORY"] else { return nil }
        return URL(fileURLWithPath: shared)
            .appendingPathComponent("tmp/cf_run_network_tests")
    }

    private func skipUnlessNetworkTestsEnabled() throws {
        let path = Self.switchFile?.path
        let on = path.map { FileManager.default.fileExists(atPath: $0) } ?? false
        try XCTSkipUnless(on, """
            네트워크와 일일 쿼터(500)를 쓰는 테스트다. 켜는 방법:
              touch "\(path ?? "<시뮬레이터가 아니다>")"
            """)
    }

    private func openCameraWithRealFixtures() -> XCUIApplication {
        let app = launchIsolatedApp()
        let tab = app.buttons["꽃 촬영"]
        XCTAssertTrue(tab.waitForExistence(timeout: 10), "탭 바에 꽃 촬영이 없다")
        tab.tap()
        XCTAssertTrue(
            app.staticTexts["꽃 한 송이를 네모 안에 꽉 채워 주세요"].waitForExistence(timeout: 5),
            "화면 07이 뜨지 않았다"
        )
        return app
    }

    /// 실호출 버튼이 화면에 있는지부터 본다. **번들에 사진이 없으면 여기서 걸린다** —
    /// `project.yml`에 RealFixtures를 등록하고 `xcodegen generate`를 안 하면
    /// 버튼은 있는데 빈 데이터를 들고 Mock으로 조용히 되돌아간다.
    func test_실호출_픽스처_버튼_세개가_있다() {
        let app = openCameraWithRealFixtures()
        XCTAssertTrue(app.buttons["실호출 · 해바라기 (0.99)"].exists)
        XCTAssertTrue(app.buttons["실호출 · 해바라기 (0.57)"].exists)
        XCTAssertTrue(app.buttons["실호출 · 민들레 (0.11)"].exists)
    }

    /// **이 테스트가 이 파일의 이유다.** 해바라기 실사진을 PlantNet에 보내고
    /// 화면 09(확정 또는 애매)에 **해바라기가 뜨는지** 본다.
    ///
    /// 확정/애매 중 어느 쪽인지는 단정하지 않는다 — 점수가 임계값 0.60 근처면
    /// 갈릴 수 있고, 그건 우리가 통제하는 값이 아니다.
    /// **꽃 이름이 맞는가**가 검증 대상이다.
    func test_해바라기_실사진이_해바라기로_판별된다() throws {
        try skipUnlessNetworkTestsEnabled()
        let app = openCameraWithRealFixtures()
        app.buttons["실호출 · 해바라기 (0.99)"].tap()

        // 화면 08(분석 중)을 지나 09 또는 12로 간다.
        //
        // ⚠️ **`"이 꽃은 해바라기인가요?"`로 찾으면 안 된다.** 화면 09는 꽃 이름을
        // 강조하려고 문구를 세 조각으로 나눠 렌더링한다 — 접근성 트리에도
        // `"이 꽃은"` · `"해바라기"` · `"인가요?"`로 따로 올라온다.
        // 실제로 이 테스트가 그것 때문에 45초를 기다리고 실패했다(판별은 성공했는데).
        let confident = app.staticTexts["이 꽃은"]
        let ambiguous = app.staticTexts["어느 꽃인가요?"]
        // A 문서 12번 제목 그대로. 문구가 바뀌면 이 테스트가 깨져야 한다.
        let failed = app.staticTexts["어떤 꽃인지 알 수 없었어요"]

        let deadline = Date().addingTimeInterval(networkTimeout)
        while Date() < deadline {
            if confident.exists || ambiguous.exists || failed.exists { break }
            usleep(300_000)
        }

        // 어느 화면에도 못 갔으면 **무엇이 떠 있는지** 남긴다.
        // 실호출은 화면만 보고 원인을 알 수 없어서 진단 정보가 필요하다.
        if !(confident.exists || ambiguous.exists || failed.exists) {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            shot.name = "실호출_타임아웃_화면"
            add(shot)
            // `map(\.label)`을 쓰면 안 된다 — `label`이 main actor 격리라
            // 키패스를 만들 수 없다(Swift 6). 클로저로 읽는다.
            let visible = app.staticTexts.allElementsBoundByIndex
                .prefix(15).map { $0.label }.filter { !$0.isEmpty }
            XCTFail("""
                \(Int(networkTimeout))초 안에 09/12 어디에도 도달하지 못했다.
                화면에 보이는 문구: \(visible)
                토스트가 떠 있으면 네트워크 오류 경로다(키 주입 또는 시뮬레이터 연결).
                """)
            return
        }

        if failed.exists {
            XCTFail("""
                실사진이 판별 실패로 갔다. 원인 후보:
                ① PlantNet 키가 안 들어갔다(Secrets.xcconfig)
                ② 지금 달에 해바라기가 안 핀다(개화월 7~9월) — 그러면 이건 정상 동작이고
                   픽스처를 제철 꽃으로 바꿔야 한다
                ③ identifyFailureFloor(0.30)가 정답을 버렸다 — B-3 미결 항목
                """)
            return
        }
        XCTAssertTrue(confident.exists || ambiguous.exists, "화면 09에 도달하지 못했다")

        // 확정이든 애매든 후보 어딘가에 해바라기가 있어야 한다.
        let hasSunflower = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", "해바라기"))
            .count > 0
        XCTAssertTrue(hasSunflower, "실제 응답에 해바라기가 없다 — 색인 또는 개화월 필터를 본다")
    }

    /// **저득점 사진이 판별 실패(화면 12)로 가는 것을 고정한다.**
    ///
    /// 민들레 정답 사진(실측 0.112)을 쓴다. 실패로 가는 이유가 **달마다 다르다**:
    /// - 3~5월: `identifyFailureFloor = 0.30`이 정답(0.112)을 버린다 — **B-3 미결 항목**
    /// - 6월 이후: 개화월 하드 필터가 민들레를 후보에서 뺀다 — **A-1이 옳게 도는 것**
    ///
    /// 결과는 같고 원인이 다르다. 그래서 여기서는 **경로만** 고정하고
    /// 원인은 단정하지 않는다 — 원인별 판정은 `plantnet_measure.py`가 재는 일이다.
    ///
    /// ⚠️ 오너가 B-3-a에 답해 floor가 낮아지면 **3~5월에만 이 테스트가 깨진다.**
    /// 깨지는 게 신호다.
    func test_저득점_정답사진은_현재_판별실패로_간다() throws {
        try skipUnlessNetworkTestsEnabled()
        let app = openCameraWithRealFixtures()
        app.buttons["실호출 · 민들레 (0.11)"].tap()

        // A 문서 12번 제목 그대로. 문구가 바뀌면 이 테스트가 깨져야 한다.
        let failed = app.staticTexts["어떤 꽃인지 알 수 없었어요"]
        let reached = failed.waitForExistence(timeout: networkTimeout)
        if !reached {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            add(shot)
            let visible = app.staticTexts.allElementsBoundByIndex
                .prefix(15).map { $0.label }.filter { !$0.isEmpty }
            XCTFail("""
                판별 실패로 가지 않았다. 화면 문구: \(visible)
                ① floor가 낮아졌다면 이 테스트의 기대값을 바꾼다 (오너_결정사항.md B-3-a)
                ② 다른 꽃으로 판별됐다면 개화월 필터가 민들레를 걸러낸 것이다 —
                   민들레는 3~5월이라 지금 달이 6월 이후면 후보에 없다
                """)
        }
    }
}
