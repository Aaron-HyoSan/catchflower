import Foundation
import Testing
@testable import CatchFlower

/// 계약과 데이터가 갈리는 지점만 테스트한다.
/// **화면 레이아웃은 테스트하지 않는다** — 프리뷰로 보는 게 빠르고 정확하다.

// MARK: - 도감 데이터 (계약 1-1)

@Suite("도감 마스터 200종")
struct FlowerCatalogTests {

    let repo = FlowerRepository()

    @Test("200종이 전부 있고 도감번호가 1~200으로 연속이다")
    func catalogCount() {
        #expect(repo.flowers.count == GamePolicy.codexTotalCount)
        #expect(Set(repo.flowers.map(\.id)) == Set(1...200))
    }

    @Test("개화월이 비어 있는 종이 없다")
    func everyFlowerHasBloomMonths() {
        let empty = repo.flowers.filter { $0.bloomMonths.isEmpty }
        #expect(empty.isEmpty, "개화월 없는 종: \(empty.map(\.name))")
    }

    @Test("개화월이 1~12 범위를 벗어나지 않는다")
    func bloomMonthsInRange() {
        for flower in repo.flowers {
            for month in flower.bloomMonths {
                #expect((1...12).contains(month), "\(flower.name)의 개화월 \(month)")
            }
        }
    }

    /// **연말 랩어라운드.** 동백꽃 `12~4월`은 `[12,1,2,3,4]`여야 한다.
    /// 여기가 틀리면 12월에 동백꽃을 못 찍는다 — 겨울에 찍을 수 있는 몇 종 중 하나다.
    @Test("연말을 넘는 개화기가 올바르게 펼쳐졌다")
    func wrapAroundBloomMonths() throws {
        let camellia = try #require(repo.flower(named: "동백꽃"))
        #expect(camellia.bloomMonths == [12, 1, 2, 3, 4])
        #expect(camellia.blooms(inMonth: 1))
        #expect(camellia.blooms(inMonth: 12))
        #expect(!camellia.blooms(inMonth: 6))
    }

    @Test("비슷한 꽃 참조가 모두 실재하는 도감번호다")
    func similarFlowerReferencesResolve() {
        let ids = Set(repo.flowers.map(\.id))
        for flower in repo.flowers {
            for similar in flower.similarFlowerIDs {
                #expect(ids.contains(similar), "\(flower.name) → \(similar)")
            }
        }
    }

    /// 개화월 하드 필터가 실제로 좁히는지. 아무 달이나 200종이 다 나오면 필터가 죽은 것이다.
    @Test("달마다 후보 수가 200보다 작다", arguments: 1...12)
    func bloomFilterNarrows(month: Int) {
        let blooming = repo.flowersBlooming(inMonth: month)
        #expect(blooming.count < GamePolicy.codexTotalCount)
        #expect(blooming.allSatisfy { $0.bloomMonths.contains(month) })
    }

    /// 겨울(12·1·2월)은 후보가 거의 없다. **이게 시즌 휴지기의 근거다** (B-1 권고 ②).
    @Test("1월 후보는 손에 꼽을 정도다")
    func januaryIsSparse() {
        #expect(repo.flowersBlooming(inMonth: 1).count <= 5)
    }
}

// MARK: - Mock 인식기 (계약 2절)

@Suite("Mock FlowerRecognizer — 계약 고정값")
struct MockRecognizerTests {

    private var allCandidates: [Int] { Array(1...50) }

    @Test("기본은 0.82 / 0.11 / 0.04")
    func defaultScenario() async throws {
        let result = try await MockFlowerRecognizer(delay: .zero)
            .identify(imageData: Data(), candidates: allCandidates)
        #expect(result.map(\.score) == [0.82, 0.11, 0.04])
        #expect(result.count == GamePolicy.candidateCount)
    }

    @Test("파일명에 low가 있으면 0.55 / 0.30 / 0.12")
    func lowScenario() async throws {
        let result = try await MockFlowerRecognizer(
            fileName: "fixture_low.jpg",
            delay: .zero
        ).identify(imageData: Data(), candidates: allCandidates)
        #expect(result.map(\.score) == [0.55, 0.30, 0.12])
    }

    @Test("파일명에 fail이 있으면 빈 배열")
    func failScenario() async throws {
        let result = try await MockFlowerRecognizer(
            fileName: "fixture_fail.jpg",
            delay: .zero
        ).identify(imageData: Data(), candidates: allCandidates)
        #expect(result.isEmpty)
    }

    /// 계약: "3개 미만이면 있는 만큼". 12월 후보가 2종뿐일 때 크래시하면 안 된다.
    @Test("후보가 3개보다 적으면 있는 만큼만 돌려준다")
    func fewerCandidates() async throws {
        let result = try await MockFlowerRecognizer(delay: .zero)
            .identify(imageData: Data(), candidates: [7, 8])
        #expect(result.count == 2)
        #expect(result.map(\.flowerID) == [7, 8])
    }

    /// **한 번 실제로 깨진 자리다.** `CaptureFlow`가 Mock에 `fileName`을 넘기지 않아
    /// 픽스처가 뭐든 전부 `confident`로 판정됐고, 실패 경로(화면 12)를 밟을 수 없었다.
    /// 픽스처 파일명과 시나리오가 실제로 이어지는지를 여기서 못 박는다.
    @Test(
        "픽스처 파일명이 시나리오를 가른다",
        arguments: [
            (FixtureShot.confident, [0.82, 0.11, 0.04]),
            (FixtureShot.low, [0.55, 0.30, 0.12]),
            (FixtureShot.fail, []),
        ]
    )
    func fixtureRouting(fixture: FixtureShot, expected: [Double]) async throws {
        let photo = fixture.photo()
        let fileName = try #require(photo.fileName, "픽스처는 파일명을 들고 있어야 한다")
        let result = try await MockFlowerRecognizer(fileName: fileName, delay: .zero)
            .identify(imageData: photo.data, candidates: allCandidates)
        #expect(result.map(\.score) == expected)
    }

    @Test("점수가 내림차순이다")
    func scoresDescend() async throws {
        for scenario in [MockFlowerRecognizer.Scenario.confident, .low] {
            let result = try await MockFlowerRecognizer(scenario: scenario, delay: .zero)
                .identify(imageData: Data(), candidates: allCandidates)
            #expect(result.map(\.score) == result.map(\.score).sorted(by: >))
        }
    }
}

// MARK: - 한국어 문구 (A 문서가 명시한 요구)

@Suite("조사·서수 처리")
struct KoreanTextTests {

    @Test("받침 유무를 가린다", arguments: [
        ("장미", false), ("개망초", false), ("민들레", false), ("억새", false),
        ("할미꽃", true), ("봄맞이꽃", true), ("동백꽃", true), ("진달래", false),
    ])
    func finalConsonant(word: String, expected: Bool) {
        #expect(KoreanText.hasFinalConsonant(word) == expected)
    }

    @Test("주격 조사 가/이")
    func subjectParticle() {
        #expect(KoreanText.subject("장미") == "장미가")
        #expect(KoreanText.subject("할미꽃") == "할미꽃이")
    }

    @Test("목적격 조사 를/을")
    func objectParticle() {
        #expect(KoreanText.object("장미") == "장미를")
        #expect(KoreanText.object("할미꽃") == "할미꽃을")
    }

    @Test("한글이 아니면 받침 없는 형태를 쓴다")
    func nonKorean() {
        #expect(KoreanText.hasFinalConsonant("Rosa") == nil)
        #expect(KoreanText.subject("Rosa") == "Rosa가")
    }

    /// A 문서: `두 번째 ~ 열한 번째 까지 한글, 12회 이상은 12번째`
    @Test("서수 표기", arguments: [
        (2, "두 번째"), (5, "다섯 번째"), (10, "열 번째"),
        (11, "열한 번째"), (12, "12번째"), (37, "37번째"),
    ])
    func ordinals(count: Int, expected: String) {
        #expect(KoreanText.ordinal(count) == expected)
    }

    @Test("도감 200종 전부 조사가 붙는다")
    func allFlowerNamesGetParticles() {
        for flower in FlowerRepository().flowers {
            let sentence = KoreanText.subject(flower.name)
            #expect(sentence.hasPrefix(flower.name))
            #expect(sentence.count == flower.name.count + 1)
        }
    }
}

// MARK: - 정책 (계약 3절)

@Suite("GamePolicy")
struct GamePolicyTests {

    @Test("난이도별 임계값 — 하 60 · 중 70 · 상 85")
    func thresholds() {
        #expect(GamePolicy.confidenceThreshold(for: .low) == 0.60)
        #expect(GamePolicy.confidenceThreshold(for: .mid) == 0.70)
        #expect(GamePolicy.confidenceThreshold(for: .high) == 0.85)
    }

    /// Mock 기본값 0.82는 **`상` 난이도에서는 애매 경로로 가야 한다**.
    /// 이게 화면 09 두 경로를 실제로 다 밟게 해주는 조건이다.
    @Test("Mock 기본값 0.82의 경로가 난이도에 따라 갈린다")
    func mockDefaultRoutesDifferByDifficulty() {
        #expect(0.82 >= GamePolicy.confidenceThreshold(for: .low))
        #expect(0.82 >= GamePolicy.confidenceThreshold(for: .mid))
        #expect(0.82 < GamePolicy.confidenceThreshold(for: .high))
    }

    @Test("시즌 경계 — 3~8월 / 9~11월 / 12~2월 휴지기", arguments: [
        (3, GamePolicy.Season.first), (8, .first),
        (9, .second), (11, .second),
        (12, .dormant), (1, .dormant), (2, .dormant),
    ])
    func seasonBoundaries(month: Int, expected: GamePolicy.Season) {
        #expect(GamePolicy.season(forMonth: month) == expected)
    }

    @Test("휴지기에는 랭킹을 돌리지 않는다")
    func dormantDoesNotRunRanking() {
        #expect(!GamePolicy.Season.dormant.runsRanking)
        #expect(GamePolicy.Season.first.runsRanking)
    }

    /// B-3 어뷰징 가드 ① — 막는 건 **희귀종 × 하위 순위** 조합 하나뿐이다.
    /// 나머지를 다 통과시켜야 한다는 게 이 표의 요점이다 —
    /// 넓게 막으면 진짜로 귀한 꽃을 만난 사람이 매번 두 장을 찍어야 한다.
    @Test("가드 ① — 희귀종을 2·3순위에서 고를 때만 추가 사진", arguments: [
        (Rarity.rare, 1, false),      // AI 1순위를 받아들인 것 = 어뷰징 모양이 아니다
        (Rarity.rare, 2, true),
        (Rarity.rare, 3, true),
        (Rarity.common, 3, false),    // 흔한 꽃은 하위 순위여도 이득이 없다
        (Rarity.normal, 3, false),
        (Rarity.common, 1, false),
    ])
    func rareFlowerExtraPhotoGuard(rarity: Rarity, rank: Int, expected: Bool) {
        #expect(GamePolicy.needsExtraPhoto(rarity: rarity, pickedRank: rank) == expected)
    }

    /// 임계값 상수와 판정이 **같은 규칙**을 말하는지 본다.
    /// 상수만 있고 부르는 곳이 없어서 규칙이 아니라 장식이던 기간이 있었다.
    @Test("가드 ① 임계값은 2순위부터다")
    func rareGuardThresholdIsSecondRank() {
        #expect(GamePolicy.rareFlowerExtraPhotoRankThreshold == 2)
        #expect(!GamePolicy.needsExtraPhoto(
            rarity: .rare,
            pickedRank: GamePolicy.rareFlowerExtraPhotoRankThreshold - 1
        ))
        #expect(GamePolicy.needsExtraPhoto(
            rarity: .rare,
            pickedRank: GamePolicy.rareFlowerExtraPhotoRankThreshold
        ))
    }
}

// MARK: - 세션 로직

@MainActor
@Suite("AppSession")
struct AppSessionTests {

    private func makeDiscovery(
        session: AppSession,
        flowerID: Int,
        lat: Double? = 37.5443,
        lng: Double? = 127.0557,
        capturedAt: Date = .now,
        isFirst: Bool = true
    ) -> Discovery {
        Discovery(
            id: UUID(),
            userID: session.userID,
            flowerID: flowerID,
            photoURL: nil,
            lat: lat,
            lng: lng,
            placeName: "서울숲",
            dongCode: nil,
            guCode: nil,
            visibility: .private,
            aiConfidence: 0.82,
            aiPickedRank: 1,
            isFirstDiscovery: isFirst,
            createdAt: capturedAt,
            capturedAt: capturedAt,
            note: nil
        )
    }

    @Test("같은 종을 두 번 찍어도 모은 종수는 1이다")
    func sameFlowerCountsOnce() {
        let session = AppSession(currentMonth: 5)
        session.record(makeDiscovery(session: session, flowerID: 1))
        session.record(makeDiscovery(session: session, flowerID: 1, isFirst: false))
        #expect(session.collectedCount == 1)
        #expect(session.discoveryCount(flowerID: 1) == 2)
    }

    /// B-5 — 같은 종 + **같은 장소**는 하루 1회. 장소를 옮기면 인정한다.
    @Test("같은 장소 중복은 막고 다른 장소는 인정한다")
    func duplicateRuleIsPlaceAware() {
        let session = AppSession(currentMonth: 5)
        session.record(makeDiscovery(session: session, flowerID: 1))

        #expect(session.isDuplicateToday(flowerID: 1, lat: 37.5443, lng: 127.0557))
        // 11m 안쪽(소수 4자리)이면 같은 장소로 본다.
        #expect(session.isDuplicateToday(flowerID: 1, lat: 37.54431, lng: 127.05571))
        // 충분히 떨어지면 다른 장소다.
        #expect(!session.isDuplicateToday(flowerID: 1, lat: 37.5600, lng: 127.0900))
        // 다른 종은 애초에 무관하다.
        #expect(!session.isDuplicateToday(flowerID: 2, lat: 37.5443, lng: 127.0557))
    }

    @Test("판별 실패 3회 연속이면 안내를 바꾼다")
    func failureStreakChangesGuide() {
        let session = AppSession(currentMonth: 5)
        #expect(!session.showsNotAFlowerGuide)
        for _ in 1...GamePolicy.identifyFailureStreakForGuideChange {
            session.noteIdentifyFailure()
        }
        #expect(session.showsNotAFlowerGuide)
    }

    @Test("등록에 성공하면 실패 연속이 초기화된다")
    func recordResetsFailureStreak() {
        let session = AppSession(currentMonth: 5)
        session.noteIdentifyFailure()
        session.noteIdentifyFailure()
        session.record(makeDiscovery(session: session, flowerID: 1))
        #expect(!session.showsNotAFlowerGuide)
    }

    @Test("도감 항목은 항상 200개다 — 미발견도 셀이 있어야 한다")
    func codexAlwaysHasEveryFlower() {
        let session = AppSession(currentMonth: 5)
        session.record(makeDiscovery(session: session, flowerID: 1))
        #expect(session.codexEntries.count == GamePolicy.codexTotalCount)
        #expect(session.codexEntries.filter(\.isDiscovered).count == 1)
    }

    /// 후보 집합에 지금 안 피는 꽃이 섞이면 11월에 벚꽃이 뜬다 (A-1 필수 구현).
    @Test("후보 집합은 지금 피는 꽃만 담는다", arguments: [4, 7, 11])
    func candidatesRespectBloomMonth(month: Int) {
        let session = AppSession(currentMonth: month)
        let ids = Set(session.candidateIDsForCurrentMonth)
        #expect(!ids.isEmpty)
        for id in ids {
            #expect(session.repository[id]?.blooms(inMonth: month) == true)
        }
    }

    @Test("화면 22 추천은 지금 피는 흔한 꽃만 고른다")
    func beginnerRecommendationsAreInSeason() {
        let session = AppSession(currentMonth: 5)
        let recommended = session.repository.recommendedForBeginners(month: 5)
        #expect(!recommended.isEmpty)
        for flower in recommended {
            #expect(flower.blooms(inMonth: 5))
            #expect(flower.rarity == .common)
        }
    }

    @Test("공개 범위 변경이 반영된다")
    func visibilityUpdate() {
        let session = AppSession(currentMonth: 5)
        let discovery = makeDiscovery(session: session, flowerID: 1)
        session.record(discovery)
        session.updateVisibility(discoveryID: discovery.id, to: .public, note: "벤치 옆")

        let stored = session.discoveries.first { $0.id == discovery.id }
        #expect(stored?.visibility == .public)
        #expect(stored?.note == "벤치 옆")
        #expect(stored?.isSharedToMap == true)
    }
}

// MARK: - 도감 필터 (화면 06)

@Suite("CodexFilter")
struct CodexFilterTests {

    private var entries: [CodexEntry] {
        FlowerRepository().flowers.map { CodexEntry(flower: $0, discoveries: []) }
    }

    @Test("빈 필터는 200종을 그대로 통과시킨다")
    func emptyFilterPassesAll() {
        #expect(CodexFilter().apply(to: entries).count == 200)
    }

    @Test("계절을 여러 개 고르면 합집합이다")
    func multipleSeasonsUnion() {
        let spring = CodexFilter(seasons: [.spring]).apply(to: entries).count
        let summer = CodexFilter(seasons: [.summer]).apply(to: entries).count
        let both = CodexFilter(seasons: [.spring, .summer]).apply(to: entries).count
        #expect(both == spring + summer)
    }

    @Test("그룹이 다르면 교집합이다")
    func differentGroupsIntersect() {
        let filter = CodexFilter(seasons: [.spring], rarities: [.rare])
        for entry in filter.apply(to: entries) {
            #expect(entry.flower.season == .spring)
            #expect(entry.flower.rarity == .rare)
        }
    }

    /// 화면 06 CTA가 `{n}종 보기` / 0종이면 비활성이다. 0이 되는 조합이 실제로 있어야
    /// 그 분기를 검증할 수 있다.
    @Test("결과가 0종이 되는 조합이 존재한다")
    func zeroResultIsReachable() {
        let filter = CodexFilter(collected: .collected, seasons: [.winter])
        #expect(filter.apply(to: entries).isEmpty)
    }

    @Test("미발견 필터는 발견한 종을 제외한다")
    func notCollectedExcludesDiscovered() {
        let repo = FlowerRepository()
        let first = repo.flowers[0]
        var list = entries
        list[0] = CodexEntry(
            flower: first,
            discoveries: [
                Discovery(
                    id: UUID(), userID: UUID(), flowerID: first.id, photoURL: nil,
                    lat: nil, lng: nil, placeName: nil, dongCode: nil, guCode: nil,
                    visibility: .private, aiConfidence: 0.82, aiPickedRank: 1,
                    isFirstDiscovery: true, createdAt: .now, capturedAt: .now, note: nil
                )
            ]
        )
        let notCollected = CodexFilter(collected: .notCollected).apply(to: list)
        #expect(notCollected.count == 199)
        #expect(!notCollected.contains { $0.flower.id == first.id })
    }
}

// MARK: - 저장 (2026-08-05 추가)

@Suite("저장 — 앱을 꺼도 도감이 남는가")
struct PersistenceTests {

    /// 테스트가 실제 사진·기록 파일을 건드리지 않게 임시 폴더를 쓴다.
    private func tempURL(_ name: String) -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("\(UUID().uuidString)-\(name)")
    }

    private func makeDiscovery(flowerID: Int, photo: String? = nil) -> Discovery {
        Discovery(
            id: UUID(), userID: UUID(), flowerID: flowerID, photoURL: photo,
            lat: 37.5443, lng: 127.0557, placeName: "서울숲",
            dongCode: "1144012700", guCode: "11440",
            visibility: .private, aiConfidence: 0.82, aiPickedRank: 1,
            isFirstDiscovery: true, createdAt: .now, capturedAt: .now, note: nil
        )
    }

    @Test("저장한 기록을 그대로 읽는다")
    func roundTrip() async throws {
        let store = DiscoveryStore(fileURL: tempURL("discoveries.json"))
        let records = [makeDiscovery(flowerID: 1), makeDiscovery(flowerID: 7)]
        try await store.save(records)

        let loaded = await store.load()
        #expect(loaded.count == 2)
        #expect(loaded.map(\.flowerID) == [1, 7])
        // **B-6이 여기 의존한다** — 구 코드가 살아 돌아와야 한다.
        #expect(loaded[0].guCode == "11440")
        #expect(loaded[0].dongCode == "1144012700")
    }

    /// **한 번 실제로 깨진 자리다.** 상위 폴더가 없으면 쓰기가 실패하는데
    /// `AppSession.persist()`가 `try?`로 삼켜서 저장이 조용히 안 됐다.
    /// 앱을 껐다 켜야 드러나는 버그라 UI 테스트가 먼저 잡았다.
    ///
    /// 위의 `roundTrip`이 이걸 못 잡은 이유: `temporaryDirectory`에 바로 써서
    /// 상위 폴더가 이미 있었다. 실제 주입 경로는 **한 단계 더 깊다.**
    @Test("상위 폴더가 없어도 저장된다")
    func createsParentDirectory() async throws {
        let nested = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("Records", isDirectory: true)
            .appendingPathComponent("discoveries.json")
        #expect(!FileManager.default.fileExists(atPath: nested.path))

        let store = DiscoveryStore(fileURL: nested)
        try await store.save([makeDiscovery(flowerID: 3)])

        #expect(await store.load().map(\.flowerID) == [3])
    }

    /// 사진도 같은 이유로 상위 폴더를 만들어야 한다.
    @Test("사진 저장도 상위 폴더를 만든다")
    func photoCreatesParentDirectory() throws {
        let nested = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
            .appendingPathComponent("Photos", isDirectory: true)
        let photos = PhotoStore(root: nested)
        let fileName = try photos.save(Data([0xFF, 0xD8, 0xFF]))
        #expect(photos.exists(fileName))
        #expect(photos.data(for: fileName)?.count == 3)
    }

    @Test("저장 파일이 없으면 빈 배열이다 (첫 실행)")
    func firstRun() async {
        let loaded = await DiscoveryStore(fileURL: tempURL("none.json")).load()
        #expect(loaded.isEmpty)
    }

    /// 깨진 파일에 빈 배열만 돌려주면 사용자는 도감이 사라진 걸로 본다.
    /// 원본을 `.corrupt`로 치워 둬서 최소한 복구가 가능해야 한다.
    @Test("깨진 파일은 옆으로 치워 두고 빈 상태로 시작한다")
    func corruptFileIsPreserved() async throws {
        let url = tempURL("broken.json")
        try Data("{ 이건 JSON이 아니다".utf8).write(to: url)

        let loaded = await DiscoveryStore(fileURL: url).load()
        #expect(loaded.isEmpty)
        #expect(FileManager.default.fileExists(atPath: url.appendingPathExtension("corrupt").path))
    }

    @Test("전송 형식이 계약의 snake_case다")
    func wireFormat() async throws {
        let url = tempURL("wire.json")
        try await DiscoveryStore(fileURL: url).save([makeDiscovery(flowerID: 3)])
        let json = try #require(String(data: Data(contentsOf: url), encoding: .utf8))
        // 서버(A-2)가 붙으면 이 문자열이 그대로 올라간다.
        for key in ["flower_id", "user_id", "gu_code", "dong_code", "ai_picked_rank", "captured_at"] {
            #expect(json.contains(key), "계약 필드 누락: \(key)")
        }
        #expect(!json.contains("flowerID"), "camelCase가 새어 나갔다")
    }

    @Test("사진을 저장하고 파일명으로 되찾는다")
    func photoRoundTrip() throws {
        let store = PhotoStore(root: tempURL("Photos"))
        let bytes = Data([0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3])
        let name = try store.save(bytes)

        #expect(store.exists(name))
        #expect(store.data(for: name) == bytes)
        store.delete(name)
        #expect(!store.exists(name))
    }

    /// 시뮬레이터 픽스처는 데이터가 비어 있다. 빈 파일을 만들면
    /// `exists`는 true인데 `image`는 nil이라 판단이 꼬인다.
    @Test("빈 데이터는 저장하지 않는다")
    func emptyPhotoIsNotSaved() {
        let store = PhotoStore(root: tempURL("Photos"))
        #expect(store.saveIfNotEmpty(Data()) == nil)
    }
}

// MARK: - PlantNet 학명 색인 (2026-08-05 추가)

@Suite("PlantNet 학명 색인")
struct ScientificNameIndexTests {

    private let index = ScientificNameIndex(flowers: FlowerRepository().flowers)
    private let repo = FlowerRepository()

    @Test("200종 학명이 전부 자기 자신을 찾는다")
    func allExactMatches() throws {
        for flower in repo.flowers where !flower.scientificName.isEmpty {
            let found = index.flowerID(for: flower.scientificName)
            #expect(found != nil, "\(flower.name)(\(flower.scientificName))을 못 찾는다")
        }
    }

    @Test("대소문자와 여분 공백을 무시한다")
    func caseInsensitive() throws {
        let first = try #require(repo.flowers.first { !$0.scientificName.isEmpty })
        #expect(index.flowerID(for: first.scientificName.uppercased()) != nil)
        #expect(index.flowerID(for: "  \(first.scientificName)  ") != nil)
    }

    /// PlantNet이 같은 속의 다른 종을 줄 수 있다. 그때 버리면
    /// **맞는 답을 판별 실패로 만든다** — 속까지 맞으면 받아들인다.
    @Test("속까지만 맞아도 찾는다")
    func genusFallback() throws {
        let first = try #require(repo.flowers.first { $0.scientificName.contains(" ") })
        let genus = try #require(first.scientificName.split(separator: " ").first)
        #expect(index.flowerID(for: "\(genus) nonexistentspecies") != nil)
    }

    @Test("전혀 다른 속은 못 찾는다")
    func unknownGenus() {
        #expect(index.flowerID(for: "Quercus robur") == nil)
        #expect(index.flowerID(for: "") == nil)
    }

    /// 품종 표기가 붙어 와도 같은 종으로 봐야 한다.
    @Test("var.·subsp. 표기를 무시한다")
    func varietyNotation() throws {
        let first = try #require(repo.flowers.first { $0.scientificName.contains(" ") })
        #expect(index.flowerID(for: "\(first.scientificName) var. alba") != nil)
    }

    // MARK: 후보 집합 우선 (A-1 실측 2026-08-05로 드러난 결함)

    /// **실측이 잡은 것.** 8월 장미 사진에서 PlantNet은 `Rosa chinensis`를
    /// 0.606으로 맞혔는데, 색인이 `Rosa` 대표를 도감번호 최솟값인
    /// **찔레꽃(29, 5~6월)**로 번역해서 개화월 필터에 탈락했다.
    /// 그 자리를 `Begonia grandis` 0.003이 차지했다 — 정답 0.606이 오답 0.003에게 진 것이다.
    @Test("속에 여러 종이 있으면 후보 집합에 든 종을 고른다")
    func genusPrefersCandidateSet() throws {
        let rosa = repo.flowers.filter { $0.scientificName.hasPrefix("Rosa ") }
        try #require(rosa.count > 1, "이 테스트는 Rosa가 2종 이상이어야 의미가 있다")
        let august = Set(repo.flowers.filter { $0.bloomMonths.contains(8) }.map(\.id))
        let hit = try #require(index.flowerID(for: "Rosa chinensis", preferring: august))
        #expect(august.contains(hit), "8월에 안 피는 종을 골랐다: \(repo.flowers.first { $0.id == hit }?.name ?? "?")")
    }

    /// **정확히 맞혔더라도 그 종이 이번 달에 안 피면 쓸 수 없다.**
    /// `Bellis perennis`(데이지 75, 4~5월)를 8월에 받으면 exact가 걸려도 탈락한다 —
    /// 그때 같은 속을 훑어야 한다. exact를 무조건 반환하면 이 경로가 죽는다.
    @Test("exact가 후보 밖이면 같은 속에서 다시 찾는다")
    func exactOutOfSeasonFallsBackToGenus() throws {
        let daisy = try #require(repo.flower(named: "데이지"))
        try #require(!daisy.bloomMonths.contains(8))
        // 8월 후보에 데이지가 없으니 exact 히트를 그대로 주면 안 된다.
        let august = Set(repo.flowers.filter { $0.bloomMonths.contains(8) }.map(\.id))
        let hit = index.flowerID(for: daisy.scientificName, preferring: august)
        // 같은 속(Bellis)에 8월 종이 없으면 nil이 아니라 exact로 돌아온다 —
        // 그건 호출처의 `allowed.contains` 가드가 버린다. 여기서 확인할 건
        // **후보에 든 종이 있을 때 그것을 고르는가**다.
        if let hit, august.contains(hit) {
            #expect(hit != daisy.id)
        } else {
            #expect(hit == daisy.id, "후보 밖이면 exact로 돌려주고 호출처가 버린다")
        }
    }

    /// `preferring`을 안 주면 예전 동작이어야 한다 — 다른 호출처를 깨지 않는다.
    @Test("후보 집합 없이 부르면 도감번호가 작은 종을 준다")
    func withoutPreferringKeepsLowestID() {
        #expect(index.flowerID(for: "Rosa nonexistentspecies")
                == repo.flowers.filter { $0.scientificName.hasPrefix("Rosa ") }.map(\.id).min())
    }

    /// **실측이 잡은 버그의 회귀 테스트.** 이게 이 수정의 요점이다.
    ///
    /// 속 대표를 도감번호 최솟값으로 고정하면, **그 대표의 개화기가 끝나는 달부터
    /// 그 속 전체가 판별 불가**가 된다. 라벨 200장 실측에서:
    /// - 장미: 7~10월 Top-1 **0%** → 62% (대표 `찔레꽃`이 6월에 끝난다)
    /// - 민들레: 6~9월 Top-1 **0%** → 85% (대표 `민들레`가 5월에 끝난다)
    ///
    /// 앱을 켜서는 못 본다 — 8월에 장미를 찍으면 `베고니아`가 뜨는데
    /// 그게 그냥 "AI가 틀렸네"로 보인다. 달을 바꿔 재야 드러난다.
    @Test("속 대표의 개화기가 끝난 달에도 같은 속을 찾는다", arguments: [
        ("Rosa chinensis", "장미", 8),        // 대표 찔레꽃 5~6월 → 8월에 죽었다
        ("Rosa chinensis", "장미", 10),
        ("Taraxacum officinale", "서양민들레", 8),  // 대표 민들레 3~5월 → 8월에 죽었다
        ("Taraxacum officinale", "서양민들레", 6),
    ])
    func genusSurvivesRepresentativeGoingOutOfSeason(
        plantNetName: String, expectedKoreanName: String, month: Int
    ) throws {
        let expected = try #require(repo.flower(named: expectedKoreanName))
        try #require(expected.bloomMonths.contains(month), "테스트 전제가 깨졌다: \(expectedKoreanName)")
        let season = Set(repo.flowers.filter { $0.bloomMonths.contains(month) }.map(\.id))
        #expect(index.flowerID(for: plantNetName, preferring: season) == expected.id)
    }

    /// 후보 집합이 그 속을 하나도 안 담고 있으면 **없는 종을 억지로 만들지 않는다.**
    @Test("후보 집합이 그 속을 비우면 후보 밖 id를 주고 호출처가 버린다")
    func emptyIntersectionDoesNotInvent() throws {
        let rosaIDs = Set(repo.flowers.filter { $0.scientificName.hasPrefix("Rosa ") }.map(\.id))
        let allowed = Set(repo.flowers.map(\.id)).subtracting(rosaIDs)
        let hit = index.flowerID(for: "Rosa chinensis", preferring: allowed)
        // Rosa chinensis는 우리 도감에 없으니 exact도 없다 → nil이어야 한다.
        #expect(hit == nil || !allowed.contains(hit!))
    }
}

// MARK: - 키 주입 (2026-08-05 추가)

@Suite("키 주입")
struct AppSecretsTests {

    /// xcconfig 변수가 안 채워지면 `$(NAME)`이 그대로 남는다.
    /// 그걸 키로 보내면 403을 받는다 — 빈 값으로 봐야 한다.
    @Test("치환 안 된 xcconfig 변수는 빈 값으로 본다")
    func unsubstitutedPlaceholder() {
        // 실제 주입값은 환경마다 달라서 값 자체를 단정하지 않는다.
        // `$(`로 시작하는 문자열이 새어 나오지 않는 것만 본다.
        for key in [
            AppSecrets.kakaoRESTAPIKey,
            AppSecrets.plantNetAPIKey,
            AppSecrets.supabaseURL,
            AppSecrets.supabaseAnonKey,
        ] {
            #expect(!key.hasPrefix("$("), "치환되지 않은 변수가 키로 쓰인다: \(key)")
        }
    }

    /// Supabase는 키만으로 접속이 안 된다. URL이 있어야 한다.
    @Test("Supabase는 URL과 키가 둘 다 있어야 켜진다")
    func supabaseNeedsBoth() {
        if AppSecrets.supabaseURL.isEmpty {
            #expect(!AppSecrets.hasSupabase, "URL이 없는데 켜졌다고 한다")
        }
    }
}
