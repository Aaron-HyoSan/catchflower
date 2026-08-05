import XCTest

/// 화면 07~13 흐름 검증.
///
/// **왜 UI 테스트인가.** 시뮬레이터에는 카메라가 없고, 이 흐름은 화면 6개를 지나며
/// 분기한다(확정/애매/실패 × 신규/재발견). 손으로 밟으면 매번 3분이고 한 경로만 본다.
///
/// 문구를 **A 문서 그대로** 찾는다 — 문구가 바뀌면 이 테스트가 깨져야 한다.
/// 그게 "문구를 창작하지 않는다"를 지키는 장치다.
final class CaptureFlowUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    /// **테스트마다 다른 저장소를 준다.** 저장을 붙이자마자 6개가 깨졌는데,
    /// 원인은 앞 테스트가 등록한 꽃이 남아 B-5가 재촬영을 막은 것이었다 —
    /// 테스트가 아니라 격리가 문제였다. (`launchIsolatedApp` 참고)
    private func launch(reset: Bool = true) -> XCUIApplication {
        launchIsolatedApp(reset: reset)
    }

    /// 촬영 모달을 연다. 화면 22의 `꽃 찍어보기`와 탭 바 `꽃 촬영` 둘 다 통로다.
    private func openCamera(_ app: XCUIApplication) {
        let tabButton = app.buttons["꽃 촬영"]
        XCTAssertTrue(tabButton.waitForExistence(timeout: 10), "탭 바에 꽃 촬영이 없다")
        tabButton.tap()
        XCTAssertTrue(
            app.staticTexts["꽃 한 송이를 네모 안에 꽉 채워 주세요"].waitForExistence(timeout: 5),
            "화면 07이 뜨지 않았다"
        )
    }

    // MARK: - 07 카메라

    func test_카메라화면_문구와_보조버튼이_있다() {
        let app = launch()
        openCamera(app)

        XCTAssertTrue(app.staticTexts["너무 멀면 잘 못 알아봐요"].exists)
        // 앨범 금지 고지는 반드시 보여야 한다 (권한 문구와의 약속).
        XCTAssertTrue(app.staticTexts["앨범 사진은 등록할 수 없어요. 직접 찍어 주세요."].exists)
        // 아이콘 단독 버튼 금지 — 라벨이 실제로 붙어 있는지 본다.
        XCTAssertTrue(app.buttons["플래시"].exists)
        XCTAssertTrue(app.buttons["전환"].exists)
        XCTAssertTrue(app.buttons["닫기"].exists)
        XCTAssertTrue(app.buttons["도움말"].exists)
    }

    func test_닫기를_누르면_도감으로_돌아온다() {
        let app = launch()
        openCamera(app)
        app.buttons["닫기"].tap()
        XCTAssertTrue(app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 5))
    }

    // MARK: - 08 → 09 → 10 확정 경로

    func test_확정경로_신규등록까지_간다() {
        let app = launch()
        openCamera(app)

        app.buttons["찍기 (확정 · 0.82)"].tap()

        // 화면 08
        XCTAssertTrue(
            app.staticTexts["어떤 꽃인지 보고 있어요"].waitForExistence(timeout: 5),
            "화면 08이 뜨지 않았다"
        )

        // 화면 09 — 질문 3줄
        XCTAssertTrue(
            app.staticTexts["이 꽃은"].waitForExistence(timeout: 10),
            "화면 09가 뜨지 않았다"
        )
        XCTAssertTrue(app.staticTexts["인가요?"].exists)
        XCTAssertTrue(app.buttons["네, 맞아요"].exists)
        XCTAssertTrue(app.buttons["아니에요, 다시 찍을게요"].exists)

        app.buttons["네, 맞아요"].tap()

        // 화면 10 — 신규 등록
        XCTAssertTrue(
            app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5),
            "화면 10이 뜨지 않았다"
        )
        XCTAssertTrue(app.buttons["지도에 공유하기"].exists)
        XCTAssertTrue(app.buttons["나만 보기"].exists)
    }

    // MARK: - 13 지도 공유 설정

    func test_공유설정_문구와_공개범위_두개가_있다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.buttons["지도에 공유하기"].waitForExistence(timeout: 5))
        app.buttons["지도에 공유하기"].tap()

        XCTAssertTrue(
            app.staticTexts["이 꽃을 지도에 공유할까요?"].waitForExistence(timeout: 5),
            "화면 13이 뜨지 않았다"
        )
        XCTAssertTrue(app.staticTexts["모두에게 공개"].exists)
        XCTAssertTrue(app.staticTexts["친구에게만 공개"].exists)
        // `나만 보기`는 여기 없어야 한다 — `공유하지 않기`와 결과가 같아 중복이다.
        XCTAssertFalse(app.staticTexts["나만 보기"].exists)
        XCTAssertTrue(app.buttons["공유하기"].exists)
        XCTAssertTrue(app.buttons["공유하지 않기"].exists)
        // 글자 수 카운터 (0 / 40)
        XCTAssertTrue(app.staticTexts["0 / 40"].exists)
    }

    func test_공유하기를_누르면_도감으로_돌아오고_기록이_남는다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        app.buttons["지도에 공유하기"].tap()
        XCTAssertTrue(app.buttons["공유하기"].waitForExistence(timeout: 5))
        app.buttons["공유하기"].tap()

        XCTAssertTrue(app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 5))
        // 화면 22가 사라지고 현황이 1종으로 바뀌어야 한다.
        XCTAssertFalse(app.staticTexts["아직 모은 꽃이 없어요"].exists)
        XCTAssertTrue(app.staticTexts["최근 발견한 꽃"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["도감 0% 완성"].exists, "1종은 0%가 맞다 (200종 중 1종)")
    }

    // MARK: - 09 애매 경로

    func test_애매경로_후보3개를_고르게_한다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (애매 · 0.55)"].tap()

        XCTAssertTrue(
            app.staticTexts["어느 꽃인가요?"].waitForExistence(timeout: 10),
            "임계값 미달인데 확정 경로로 갔다"
        )
        XCTAssertTrue(app.staticTexts["가장 비슷한 꽃을 골라 주세요"].exists)
        // 확정 경로 문구가 섞여 있으면 안 된다.
        XCTAssertFalse(app.buttons["네, 맞아요"].exists)

        // 후보 3개가 놓여 있어야 한다.
        let candidates = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "번 후보")
        )
        XCTAssertEqual(candidates.count, 3, "후보가 3개가 아니다")
    }

    func test_애매경로에서_2순위를_골라도_등록된다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (애매 · 0.55)"].tap()
        XCTAssertTrue(app.staticTexts["어느 꽃인가요?"].waitForExistence(timeout: 10))

        let second = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "2번 후보")
        ).firstMatch
        XCTAssertTrue(second.exists)
        second.tap()

        XCTAssertTrue(app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5))
    }

    // MARK: - 12 판별 실패

    func test_실패경로_팁4개와_버튼이_있다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (실패)"].tap()

        XCTAssertTrue(
            app.staticTexts["어떤 꽃인지 알 수 없었어요"].waitForExistence(timeout: 10),
            "화면 12가 뜨지 않았다"
        )
        XCTAssertTrue(app.staticTexts["다시 한 번 찍어 주시겠어요?"].exists)
        XCTAssertTrue(app.staticTexts["이 사진은 저장되지 않았어요"].exists)
        for tip in [
            "꽃 한 송이가 화면에 꽉 차게",
            "그림자 없는 밝은 곳에서",
            "정면이나 살짝 위에서",
            "흔들리지 않게 잠시 멈춰서",
        ] {
            XCTAssertTrue(app.staticTexts[tip].exists, "팁 누락: \(tip)")
        }
        XCTAssertTrue(app.buttons["다시 찍기"].exists)
        XCTAssertTrue(app.buttons["나중에 할게요"].exists)
    }

    /// B-11 — 3회 연속 실패하면 제목이 바뀌고 도감 복귀를 유도한다.
    func test_3회_연속_실패하면_제목이_바뀐다() {
        let app = launch()
        openCamera(app)

        for attempt in 1...3 {
            app.buttons["찍기 (실패)"].tap()
            if attempt < 3 {
                XCTAssertTrue(
                    app.staticTexts["어떤 꽃인지 알 수 없었어요"].waitForExistence(timeout: 10),
                    "\(attempt)회차에서 제목이 이미 바뀌었다"
                )
                app.buttons["다시 찍기"].tap()
                XCTAssertTrue(
                    app.buttons["찍기 (실패)"].waitForExistence(timeout: 5),
                    "다시 찍기가 화면 07로 돌아가지 않았다"
                )
            }
        }

        XCTAssertTrue(
            app.staticTexts["꽃이 아닐 수도 있어요"].waitForExistence(timeout: 10),
            "3회 연속 실패인데 제목이 그대로다"
        )
        XCTAssertTrue(app.buttons["도감으로 돌아가기"].exists)
    }

    // MARK: - 11 재발견

    /// 같은 꽃을 두 번 찍으면 화면 10이 아니라 11로 가야 한다.
    /// Mock은 후보 목록의 앞쪽을 집으므로 같은 꽃이 두 번 나온다.
    ///
    /// **2회차는 장소를 옮겨야 한다.** B-5(같은 종+같은 장소 하루 1회)를 흐름에 붙인 뒤로
    /// 같은 자리 재촬영은 정상적으로 막힌다 — 그게 규칙이다.
    func test_같은꽃을_다른장소에서_다시_찍으면_재발견화면이_뜬다() {
        let app = launch()

        // 1회차 — 신규 등록 (서울숲)
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5))
        app.buttons["나만 보기"].tap()

        // 2회차 — 다른 장소라 인정된다 (서울광장)
        openCamera(app)
        app.buttons["찍기 (확정 · 다른 장소)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()

        XCTAssertTrue(
            app.staticTexts["이번이 두 번째 발견입니다."].waitForExistence(timeout: 5),
            "재발견 서수가 한글 `두 번째`가 아니다"
        )
        XCTAssertTrue(app.staticTexts["이번 시즌 종수는 늘지 않아요"].exists)
        XCTAssertTrue(app.staticTexts["같은 꽃은 한 종으로 계산해요. 사진은 도감에 쌓여요."].exists)
        XCTAssertTrue(app.staticTexts["이번 발견 기록"].exists)
        // 신규 등록 문구가 섞이면 안 된다.
        XCTAssertFalse(app.staticTexts["새로운 꽃을 발견했어요!"].exists)
    }

    /// B-5 — 같은 종 + 같은 장소는 하루 1회. **오너 확정 규칙이다.**
    /// 상수와 판정 함수는 있었지만 흐름이 부르지 않아서 무한 등록이 됐다.
    func test_같은장소에서_같은꽃을_다시_찍으면_막힌다() {
        let app = launch()

        // 1회차 — 등록
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5))
        app.buttons["나만 보기"].tap()

        // 2회차 — 같은 장소라 막혀야 한다
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()

        // A 문서 3절에 추가한 문구 (2026-08-05)
        XCTAssertTrue(
            app.staticTexts["오늘 여기서 만난 꽃이에요"].waitForExistence(timeout: 5),
            "B-5가 같은 장소 재촬영을 막지 않았다"
        )
        XCTAssertTrue(app.staticTexts["같은 자리에서 같은 꽃은 하루에 한 번 기록해요."].exists)
        XCTAssertTrue(app.staticTexts["장소를 옮기면 다시 기록할 수 있어요."].exists)
        XCTAssertTrue(app.buttons["도감에서 보기"].exists)
        XCTAssertTrue(app.buttons["다른 꽃 찍기"].exists)
        // 등록 화면 문구가 섞이면 안 된다.
        XCTAssertFalse(app.staticTexts["새로운 꽃을 발견했어요!"].exists)
    }

    /// 저장이 실제로 되는가 — **앱을 껐다 켜서** 확인한다.
    /// 이게 이번 작업의 핵심이라 UI에서 직접 본다.
    func test_앱을_다시_켜도_도감이_남아있다() {
        // 1회차는 **초기화하고** 시작한다. 임시 폴더는 앱 컨테이너 안이라
        // 테스트를 두 번째로 돌릴 때도 남아 있다 — 안 지우면 지난 실행의 꽃 때문에
        // B-5가 첫 촬영을 막는다.
        let app = launch()

        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5))
        app.buttons["나만 보기"].tap()
        XCTAssertTrue(app.staticTexts["최근 발견한 꽃"].waitForExistence(timeout: 5))

        // 껐다 켠다. **초기화 인자를 떼고** 켜야 한다 — 안 떼면 방금 저장한 걸
        // 앱이 스스로 지우고, 테스트는 저장 버그와 구분할 수 없게 된다.
        app.terminate()
        app.launchArguments.removeAll { $0 == LaunchArgument.reset }
        app.launch()

        XCTAssertTrue(
            app.staticTexts["최근 발견한 꽃"].waitForExistence(timeout: 10),
            "앱을 다시 켰더니 도감이 비었다 — 저장이 안 된다"
        )
        XCTAssertFalse(
            app.staticTexts["아직 모은 꽃이 없어요"].exists,
            "빈 상태 화면(22)이 떴다 — 기록이 사라졌다"
        )
    }

    // MARK: - 08 취소

    /// **판별을 느리게 만들고 검증한다.** Mock 기본 지연 1.5초 안에 `취소`를 누르는 건
    /// 시뮬레이터가 한가할 때만 된다 — 단독 실행은 통과했는데 전체 실행에서
    /// 판별이 먼저 끝나 화면이 넘어갔다. 타이밍을 테스트가 정한다.
    func test_분석중_취소하면_카메라로_돌아온다() {
        let app = XCUIApplication()
        app.launchArguments += [
            LaunchArgument.temporaryStorage,
            LaunchArgument.storageID, uiTestStorageID,
            LaunchArgument.reset,
            LaunchArgument.slowIdentify,
            LaunchArgument.skipOnboarding,
        ]
        app.launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.staticTexts["어떤 꽃인지 보고 있어요"].waitForExistence(timeout: 5))
        app.buttons["취소"].tap()
        XCTAssertTrue(
            app.staticTexts["꽃 한 송이를 네모 안에 꽉 채워 주세요"].waitForExistence(timeout: 5)
        )
    }

    // MARK: - 공통 UI (A 문서 3절)

    /// 등록 후 **성공 피드백**이 있어야 한다. 이게 없어서 화면만 닫혔고,
    /// 사용자는 "된 건가?" 하고 같은 동작을 다시 한다.
    func test_나만_보기를_누르면_토스트가_뜬다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.buttons["나만 보기"].waitForExistence(timeout: 5))
        app.buttons["나만 보기"].tap()

        XCTAssertTrue(
            app.staticTexts["도감에는 저장됐어요"].waitForExistence(timeout: 3),
            "등록 토스트가 뜨지 않았다"
        )
    }

    /// 지도 공유 완료 토스트. **모달이 닫힌 뒤 도감 위에** 떠야 한다 —
    /// 토스트를 촬영 모달 안에 두면 닫히면서 같이 사라진다.
    func test_공유하면_공유_토스트가_뜬다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        app.buttons["지도에 공유하기"].tap()
        XCTAssertTrue(app.buttons["공유하기"].waitForExistence(timeout: 5))
        app.buttons["공유하기"].tap()

        XCTAssertTrue(app.staticTexts["지도에 공유했어요"].waitForExistence(timeout: 3))
        // 도감으로 돌아온 상태여야 한다 (모달 안이 아니다).
        XCTAssertTrue(app.navigationBars["내 꽃 도감"].exists)
    }

    /// 찍은 사진이 있는데 나가려 하면 **확인을 받는다.**
    /// 그냥 닫히면 판별 중이던 사진이 소리 없이 사라진다.
    func test_사진을_찍은_뒤_나가려면_확인을_받는다() {
        let app = XCUIApplication()
        app.launchArguments += [
            LaunchArgument.temporaryStorage,
            LaunchArgument.storageID, uiTestStorageID,
            LaunchArgument.reset,
            LaunchArgument.slowIdentify,
            LaunchArgument.skipOnboarding,
        ]
        app.launch()
        openCamera(app)
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.staticTexts["어떤 꽃인지 보고 있어요"].waitForExistence(timeout: 5))
        // 화면 07로 되돌린 뒤 닫기 — 이때는 찍은 사진이 있는 상태다.
        app.buttons["취소"].tap()
        XCTAssertTrue(app.buttons["닫기"].waitForExistence(timeout: 5))
        app.buttons["닫기"].tap()

        // 취소 시에는 사진을 버리므로 확인 없이 닫혀야 한다.
        XCTAssertTrue(
            app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 5),
            "사진을 버린 뒤인데 확인을 물었다"
        )
    }

    // MARK: - B-3 어뷰징 가드 ① 희귀종 추가 사진

    /// 애매 경로에서 **2순위 희귀종**을 고르면 즉시 등록되지 않는다.
    /// 오너 확정 규칙인데 상수만 있고 부르는 곳이 없어서 그냥 등록됐던 자리다.
    private func pickRareSecondCandidate(_ app: XCUIApplication) {
        app.buttons["찍기 (애매 · 2순위 희귀종)"].tap()
        XCTAssertTrue(
            app.staticTexts["어느 꽃인가요?"].waitForExistence(timeout: 10),
            "애매 경로로 가지 않았다"
        )
        let second = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "2번 후보")
        ).firstMatch
        XCTAssertTrue(second.waitForExistence(timeout: 5), "2번 후보가 없다")
        second.tap()
    }

    func test_희귀종을_2순위에서_고르면_사진을_한장_더_받는다() {
        let app = launch()
        openCamera(app)
        pickRareSecondCandidate(app)

        // A 문서 3절에 추가한 문구 (2026-08-05)
        XCTAssertTrue(
            app.staticTexts["귀한 꽃이네요!"].waitForExistence(timeout: 5),
            "가드 ①이 걸리지 않았다 — 2순위 희귀종이 그냥 등록됐다"
        )
        XCTAssertTrue(app.staticTexts["조금 다른 각도에서 찍어 주시면 좋아요."].exists)
        XCTAssertTrue(app.buttons["한 장 더 찍기"].exists)
        XCTAssertTrue(app.buttons["다시 고르기"].exists)
        // 등록이 끝난 것처럼 읽히면 안 된다.
        XCTAssertFalse(app.staticTexts["새로운 꽃을 발견했어요!"].exists)
    }

    /// 한 장 더 찍으면 등록된다. **두 번 묻지 않는다** —
    /// 두 번째 사진도 판별을 새로 거치므로 같은 희귀종이 또 걸리면 영원히 등록할 수 없다.
    func test_한장_더_찍으면_등록된다() {
        let app = launch()
        openCamera(app)
        pickRareSecondCandidate(app)
        XCTAssertTrue(app.buttons["한 장 더 찍기"].waitForExistence(timeout: 5))
        app.buttons["한 장 더 찍기"].tap()

        // 화면 07로 돌아간다.
        XCTAssertTrue(
            app.staticTexts["꽃 한 송이를 네모 안에 꽉 채워 주세요"].waitForExistence(timeout: 5),
            "한 장 더 찍기가 촬영 화면으로 돌아가지 않았다"
        )
        pickRareSecondCandidate(app)

        XCTAssertTrue(
            app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5),
            "두 번째 사진인데 또 가드가 걸렸다 — 등록할 방법이 없다"
        )
    }

    /// `다시 고르기`는 후보 선택으로 돌아간다. **면제를 주지 않는다** —
    /// 되돌린 뒤 같은 희귀종을 다시 고르면 가드는 그대로 걸려야 한다.
    func test_다시_고르기는_면제를_주지_않는다() {
        let app = launch()
        openCamera(app)
        pickRareSecondCandidate(app)
        XCTAssertTrue(app.buttons["다시 고르기"].waitForExistence(timeout: 5))
        app.buttons["다시 고르기"].tap()

        XCTAssertTrue(
            app.staticTexts["어느 꽃인가요?"].waitForExistence(timeout: 5),
            "다시 고르기가 후보 선택으로 돌아가지 않았다"
        )
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "2번 후보")
        ).firstMatch.tap()

        XCTAssertTrue(
            app.staticTexts["귀한 꽃이네요!"].waitForExistence(timeout: 5),
            "다시 고르기로 가드를 우회할 수 있다"
        )
    }

    /// 1순위는 통과시킨다. AI가 가장 그럴 법하다고 본 답을 받아들인 건
    /// 어뷰징의 모양이 아니다 — 여기까지 막으면 정직한 발견을 벌주게 된다.
    func test_1순위는_추가_사진을_요구하지_않는다() {
        let app = launch()
        openCamera(app)
        app.buttons["찍기 (애매 · 2순위 희귀종)"].tap()
        XCTAssertTrue(app.staticTexts["어느 꽃인가요?"].waitForExistence(timeout: 10))
        app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "1번 후보")
        ).firstMatch.tap()

        XCTAssertTrue(
            app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5),
            "1순위인데 가드가 걸렸다"
        )
    }

    /// 아직 아무것도 안 찍었으면 **확인을 묻지 않는다.** 잃을 게 없다.
    func test_아무것도_안_찍었으면_바로_닫힌다() {
        let app = launch()
        openCamera(app)
        app.buttons["닫기"].tap()

        XCTAssertTrue(app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 5))
        XCTAssertFalse(
            app.staticTexts["촬영을 그만할까요?"].exists,
            "찍은 사진이 없는데 확인 다이얼로그가 떴다"
        )
    }
}

/// 화면 04·05·06 — 도감 쪽.
final class CodexUITests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    func test_빈도감은_화면22를_보여준다() {
        let app = launchIsolatedApp()

        XCTAssertTrue(app.staticTexts["아직 모은 꽃이 없어요"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["산책길에 만난 꽃을 찍어 첫 칸을 채워보세요"].exists)
        XCTAssertTrue(app.buttons["꽃 찍어보기"].exists)
        XCTAssertTrue(app.staticTexts["처음이라면 이 꽃부터"].exists)
        XCTAssertTrue(app.staticTexts["지금 이 계절, 동네에서 흔히 보이는 꽃이에요"].exists)
    }

    func test_필터시트_그룹4개와_CTA가_있다() {
        let app = launchIsolatedApp()

        XCTAssertTrue(app.buttons["필터"].waitForExistence(timeout: 10))
        app.buttons["필터"].tap()

        XCTAssertTrue(app.navigationBars["필터"].waitForExistence(timeout: 5))
        for group in ["수집 여부", "계절", "색상", "보기 쉬움"] {
            XCTAssertTrue(app.staticTexts[group].exists, "그룹 누락: \(group)")
        }
        XCTAssertTrue(app.buttons["초기화"].exists)
        // 아무것도 안 골랐으면 200종이 다 통과한다.
        XCTAssertTrue(app.buttons["200종 보기"].exists)
    }

    /// 화면 06 CTA — 0종이면 문구를 안내로 바꾸고 비활성화한다.
    func test_결과가_0종이면_CTA가_비활성이다() {
        let app = launchIsolatedApp()
        XCTAssertTrue(app.buttons["필터"].waitForExistence(timeout: 10))
        app.buttons["필터"].tap()
        XCTAssertTrue(app.navigationBars["필터"].waitForExistence(timeout: 5))

        // 아무것도 안 모은 상태에서 `모은 꽃`을 고르면 0종이 된다.
        // 뒤에 깔린 화면 04에도 같은 라벨의 칩이 있다. SwiftUI `.sheet`는 이 런타임에서
        // `app.sheets`로 안 잡히므로, 칩에 붙인 `scope` 식별자로 시트 쪽을 지목한다.
        app.buttons["filterSheet.모은 꽃"].tap()

        let disabled = app.buttons["조건에 맞는 꽃이 없어요"]
        XCTAssertTrue(disabled.waitForExistence(timeout: 3), "0종 안내 문구가 안 뜬다")
        XCTAssertFalse(disabled.isEnabled, "0종인데 버튼이 눌린다")
    }

    func test_필터를_적용하면_종수가_줄어든다() {
        let app = launchIsolatedApp()
        XCTAssertTrue(app.buttons["필터"].waitForExistence(timeout: 10))
        app.buttons["필터"].tap()
        XCTAssertTrue(app.navigationBars["필터"].waitForExistence(timeout: 5))

        app.buttons["filterSheet.겨울"].tap()
        // 겨울은 6종이다 (CSV 실측값).
        XCTAssertTrue(app.buttons["6종 보기"].waitForExistence(timeout: 3), "겨울 종수가 6이 아니다")
        app.buttons["6종 보기"].tap()

        XCTAssertTrue(app.navigationBars["내 꽃 도감"].waitForExistence(timeout: 5))
    }

    /// 기록 삭제는 **확인을 받는다** (A 문서 3절). 사진 파일까지 지워서 되돌릴 수 없다.
    /// 스와이프가 아니라 **눌리는 버튼**이어야 한다 — 제스처 전용 기능 금지(타깃 제약).
    func test_기록을_지우려면_확인을_받는다() {
        let app = launchIsolatedApp()

        // 먼저 한 건 등록한다.
        XCTAssertTrue(app.buttons["꽃 촬영"].waitForExistence(timeout: 10))
        app.buttons["꽃 촬영"].tap()
        XCTAssertTrue(app.buttons["찍기 (확정 · 0.82)"].waitForExistence(timeout: 5))
        app.buttons["찍기 (확정 · 0.82)"].tap()
        XCTAssertTrue(app.buttons["네, 맞아요"].waitForExistence(timeout: 10))
        app.buttons["네, 맞아요"].tap()
        XCTAssertTrue(app.staticTexts["새로운 꽃을 발견했어요!"].waitForExistence(timeout: 5))
        app.buttons["나만 보기"].tap()

        // 방금 등록한 꽃의 상세로 들어간다.
        let recent = app.staticTexts["최근 발견한 꽃"]
        XCTAssertTrue(recent.waitForExistence(timeout: 5))
        // **`최근 발견한 꽃` 스트립을 쓴다.** 그리드 셀도 상세로 가지만
        // 발견한 꽃은 200칸 어딘가에 있어서 화면 밖이다(전부 `미발견 꽃`만 보인다).
        // 스트립은 방금 등록한 꽃을 맨 앞에 놓으므로 항상 보인다.
        //
        // 어떤 꽃이 뽑히는지는 계절에 따라 달라서 이름을 고정할 수 없다 —
        // `recent.0` 식별자로 첫 칸을 지목한다.
        let stripFlower = app.buttons["recent.0"]
        XCTAssertTrue(stripFlower.waitForExistence(timeout: 5), "최근 발견 스트립이 비었다")
        stripFlower.tap()

        // 상세 화면인지는 꽃 이름 대신 **고정 문구**로 확인한다
        // (꽃 이름은 계절에 따라 달라진다).
        XCTAssertTrue(
            app.staticTexts["내 발견 기록"].waitForExistence(timeout: 5),
            "도감 상세로 못 들어갔다"
        )

        let deleteButton = app.buttons["이 기록 지우기"]
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 5), "지우기 버튼이 없다")
        deleteButton.tap()

        XCTAssertTrue(
            app.staticTexts["이 발견 기록을 지울까요?"].waitForExistence(timeout: 3),
            "삭제 확인 다이얼로그가 안 뜬다"
        )
        XCTAssertTrue(app.staticTexts["지우면 되돌릴 수 없어요"].exists)

        // **취소 버튼이 실제로 눌리는지 본다.** 처음엔 `confirmationDialog`을 썼는데
        // 계층을 찍어보니 `지우기` 하나만 있었다 — 나가는 길이 바깥 탭뿐이었다.
        // 그건 제스처 전용이라 타깃 제약 위반이고, 여기서 걸러야 한다.
        let dialog = app.alerts.firstMatch
        XCTAssertTrue(dialog.waitForExistence(timeout: 3), "삭제 확인 다이얼로그가 없다")
        let cancelButton = dialog.buttons["취소"]
        XCTAssertTrue(
            cancelButton.waitForExistence(timeout: 3),
            "취소 버튼이 없다 — 바깥을 탭하는 것 말고 나갈 길이 없다"
        )

        // 취소하면 기록이 남아야 한다.
        cancelButton.tap()
        XCTAssertTrue(deleteButton.waitForExistence(timeout: 3), "취소했는데 기록이 사라졌다")

        // 다시 눌러 실제로 지운다.
        deleteButton.tap()
        let confirmDialog = app.alerts.firstMatch
        XCTAssertTrue(confirmDialog.buttons["지우기"].waitForExistence(timeout: 3))
        confirmDialog.buttons["지우기"].tap()
        XCTAssertTrue(
            app.staticTexts["아직 이 꽃을 만나지 못했어요"].waitForExistence(timeout: 5),
            "지웠는데 기록이 남아 있다"
        )
    }

    func test_도감상세로_들어간다() {
        let app = launchIsolatedApp()

        // 추천 꽃(화면 22)이 아니라 그리드 셀을 눌러야 상세로 간다.
        let cell = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "미발견 꽃")
        ).firstMatch
        XCTAssertTrue(cell.waitForExistence(timeout: 10), "미발견 셀이 없다")
        cell.tap()

        XCTAssertTrue(app.staticTexts["꽃 이야기"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["내 발견 기록"].exists)
        XCTAssertTrue(app.staticTexts["아직 이 꽃을 만나지 못했어요"].exists)
    }
}
