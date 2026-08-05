import Foundation
import Observation

/// 앱 전역 상태.
///
/// **저장은 아직 메모리다.** 백엔드(A-2)가 미확정이라 `Supabase`를 붙이지 않았다.
/// 대신 이 클래스가 유일한 쓰기 창구여서, 나중에 `DiscoveryStore` 프로토콜로
/// 갈아 끼울 때 화면 코드는 건드리지 않는다.
@MainActor
@Observable
final class AppSession {

    let repository: FlowerRepository

    /// 발견 기록. **기기에 저장된다** (`DiscoveryStore`).
    /// 서버 동기화는 A-2가 정해지면 이 배열을 올리는 방식으로 붙는다.
    private(set) var discoveries: [Discovery] = []

    let photos: PhotoStore
    private let store: DiscoveryStore

    let location = LocationProvider()
    let places: any PlaceService
    private let launchOptions: LaunchOptions

    /// B-11 — 판별 실패 연속 횟수. `GamePolicy.identifyFailureStreakForGuideChange`에
    /// 도달하면 화면 12 제목이 `꽃이 아닐 수도 있어요`로 바뀐다.
    private(set) var identifyFailureStreak = 0

    /// 로그인 전이라 임시 사용자다 (화면 01은 이번 범위 밖).
    let userID = UUID()

    /// 활동 지역. 화면 02에서 고르는 값. 지금은 문구 스펙의 예시값을 쓴다.
    var regionName = "연남동"

    /// 지금 달. 개화월 하드 필터의 입력이다.
    /// 테스트에서 12월·1월을 재현할 수 있어야 해서 주입 가능하게 둔다.
    var currentMonth: Int

    var recognizer: any FlowerRecognizer

    init(
        repository: FlowerRepository = FlowerRepository(),
        recognizer: (any FlowerRecognizer)? = nil,
        currentMonth: Int? = nil,
        store: DiscoveryStore? = nil,
        photos: PhotoStore? = nil,
        places: (any PlaceService)? = nil,
        launchOptions: LaunchOptions = LaunchOptions()
    ) {
        self.repository = repository
        // **키가 있으면 실제 PlantNet, 없으면 Mock.** 시뮬레이터는 카메라가 없어
        // 픽스처(빈 데이터)를 쓰므로 실제 엔진에 보낼 사진이 없다 — Mock을 유지한다.
        if let recognizer {
            self.recognizer = recognizer
        } else {
            #if targetEnvironment(simulator)
            var mock = MockFlowerRecognizer()
            // 분석 중 취소를 검증하는 테스트가 판별을 붙잡아 둔다.
            if launchOptions.slowsIdentification { mock.delay = .seconds(30) }
            self.recognizer = mock
            #else
            self.recognizer = AppSecrets.hasPlantNetKey
                ? PlantNetRecognizer(
                    scientificIndex: ScientificNameIndex(flowers: repository.flowers)
                )
                : MockFlowerRecognizer()
            #endif
        }
        self.currentMonth = currentMonth
            ?? Calendar.current.component(.month, from: Date())
        // UI 테스트는 임시 폴더를 쓴다 — 안 그러면 앞 테스트가 남긴 꽃 때문에
        // "빈 도감" 검증이 깨지고 B-5가 재촬영을 막는다.
        let root = launchOptions.storageRoot
        self.store = store ?? DiscoveryStore(
            fileURL: root?.appendingPathComponent("discoveries.json")
        )
        self.photos = photos ?? PhotoStore(
            root: root?.appendingPathComponent("Photos")
        )
        self.launchOptions = launchOptions
        // 키가 없으면 장소 조회를 아예 하지 않는다 — `$(KAKAO_REST_API_KEY)`를
        // 키로 보내 403을 받는 것보다 조용히 꺼두는 게 낫다.
        self.places = places
            ?? (AppSecrets.hasKakaoKey ? KakaoPlaceService() : NoPlaceService())
    }

    /// 촬영 위치를 채운다. **권한이 없거나 실패해도 촬영은 계속된다** —
    /// 권한 문구가 `도감 등록은 위치 없이도 할 수 있어요`라고 약속했다.
    func attachLocation(to photo: CapturedPhoto) async -> CapturedPhoto {
        var filled = photo
        guard let coordinate = await location.currentLocation() else { return filled }
        filled.lat = coordinate.coordinate.latitude
        filled.lng = coordinate.coordinate.longitude
        if let info = await places.place(lat: filled.lat!, lng: filled.lng!) {
            filled.placeName = info.placeName
            filled.dongCode = info.dongCode
            filled.guCode = info.guCode
        }
        return filled
    }

    /// 저장된 기록을 읽어 온다. 앱 시작 시 한 번 부른다.
    ///
    /// **`init`에서 하지 않는 이유.** `init`은 동기라 `actor`를 못 기다린다.
    /// 여기서 억지로 기다리면 앱 시작이 파일 I/O만큼 멈춘다.
    func loadPersisted() async {
        if launchOptions.resetsStorage {
            discoveries = []
            persist()
            return
        }
        let loaded = await store.load()
        guard !loaded.isEmpty else { return }
        discoveries = loaded
    }

    /// 디스크에 반영한다. 실패해도 화면을 막지 않는다 —
    /// 메모리에는 이미 들어가 있어서 이번 세션 동작은 정상이다.
    ///
    /// **다만 조용히 넘기지는 않는다.** `try?`로 삼켰다가 상위 폴더가 없어
    /// 저장이 통째로 안 되는 걸 못 봤다. 앱을 껐다 켜야 드러나는 종류의 버그라
    /// 로그가 유일한 단서다.
    private func persist() {
        let snapshot = discoveries
        Task { [store] in
            do {
                try await store.save(snapshot)
            } catch {
                assertionFailure("발견 기록 저장 실패: \(error)")
            }
        }
    }

    // MARK: - 도감

    /// 200종 전체 + 각 종의 발견 기록. 화면 04 그리드가 이걸 그린다.
    var codexEntries: [CodexEntry] {
        let grouped = Dictionary(grouping: discoveries, by: \.flowerID)
        return repository.flowers.map { flower in
            CodexEntry(flower: flower, discoveries: grouped[flower.id] ?? [])
        }
    }

    func entry(for flower: Flower) -> CodexEntry {
        CodexEntry(flower: flower, discoveries: discoveries.filter { $0.flowerID == flower.id })
    }

    /// 화면 04 현황 카드 — `모은 꽃 37 / 200종`.
    var collectedCount: Int { Set(discoveries.map(\.flowerID)).count }

    /// 화면 04 진행 문구 — `도감 18% 완성`. 소수점을 버린다.
    var completionPercent: Int {
        collectedCount * 100 / GamePolicy.codexTotalCount
    }

    /// 이번 시즌에 모은 종수. 시즌 경계는 `GamePolicy.season(forMonth:)`가 정한다.
    var seasonCollectedCount: Int {
        let season = GamePolicy.season(forMonth: currentMonth)
        let calendar = Calendar.current
        let ids = discoveries.filter {
            GamePolicy.season(forMonth: calendar.component(.month, from: $0.capturedAt)) == season
        }.map(\.flowerID)
        return Set(ids).count
    }

    /// 화면 04 섹션 1 — `최근 발견한 꽃`.
    func recentlyDiscovered(limit: Int = 6) -> [Flower] {
        var seen = Set<Int>()
        return discoveries
            .sorted { $0.capturedAt > $1.capturedAt }
            .compactMap { discovery -> Flower? in
                guard seen.insert(discovery.flowerID).inserted else { return nil }
                return repository[discovery.flowerID]
            }
            .prefix(limit)
            .map { $0 }
    }

    // MARK: - 판별 후보 집합

    /// **개화월 하드 필터** — 인식 요청에 넣을 후보 `flower_id` 목록.
    ///
    /// 계약상 서버가 적용해 내려주는 것이 원칙이고, 지금은 서버가 없어서
    /// 여기서 만든다. 서버가 붙으면 이 메서드는 서버 응답으로 대체된다.
    /// 없으면 11월에 "벚꽃"이 후보로 올라온다 (A-1 필수 구현).
    var candidateIDsForCurrentMonth: [Int] {
        repository.flowersBlooming(inMonth: currentMonth).map(\.id)
    }

    // MARK: - 쓰기

    /// B-5 — 같은 종 + 같은 장소는 하루 `sameFlowerSamePlacePerDayLimit`회.
    /// 장소를 옮기면 인정한다. 좌표는 `placeCoordinateRoundingDigits`자리로 반올림해 비교한다.
    func isDuplicateToday(flowerID: Int, lat: Double?, lng: Double?) -> Bool {
        let calendar = Calendar.current
        let sameDay = discoveries.filter {
            $0.flowerID == flowerID && calendar.isDateInToday($0.capturedAt)
        }
        guard !sameDay.isEmpty else { return false }
        guard let lat, let lng else {
            // 위치 없이 찍은 건 장소를 구분할 수 없다. 같은 장소로 본다.
            return sameDay.count >= GamePolicy.sameFlowerSamePlacePerDayLimit
        }
        let key = Self.placeKey(lat: lat, lng: lng)
        let atSamePlace = sameDay.filter {
            guard let l = $0.lat, let g = $0.lng else { return true }
            return Self.placeKey(lat: l, lng: g) == key
        }
        return atSamePlace.count >= GamePolicy.sameFlowerSamePlacePerDayLimit
    }

    /// `nonisolated` — 순수 계산이고, **장소 캐시(`KakaoPlaceService`)가 같은 기준으로
    /// 반올림해야** 한다. 두 곳이 다른 반올림을 쓰면 캐시는 맞았다고 하는데
    /// B-5는 다른 장소라고 판단하는 일이 생긴다.
    nonisolated static func placeKey(lat: Double, lng: Double) -> String {
        let scale = pow(10.0, Double(GamePolicy.placeCoordinateRoundingDigits))
        return "\((lat * scale).rounded())_\((lng * scale).rounded())"
    }

    /// 발견 기록을 추가하고, 신규였는지 돌려준다 (화면 10 vs 11 분기).
    @discardableResult
    func record(_ discovery: Discovery) -> Bool {
        discoveries.append(discovery)
        identifyFailureStreak = 0
        persist()
        return discovery.isFirstDiscovery
    }

    /// 화면 13에서 공개 범위를 바꾼다.
    func updateVisibility(discoveryID: UUID, to visibility: ShareVisibility, note: String?) {
        guard let index = discoveries.firstIndex(where: { $0.id == discoveryID }) else { return }
        discoveries[index].visibility = visibility
        discoveries[index].note = note
        persist()
    }

    /// 기록을 지운다. 사진 파일도 같이 지운다 —
    /// 안 지우면 참조 없는 사진이 기기에 계속 쌓인다.
    func delete(discoveryID: UUID) {
        guard let index = discoveries.firstIndex(where: { $0.id == discoveryID }) else { return }
        if let fileName = discoveries[index].photoURL {
            photos.delete(fileName)
        }
        discoveries.remove(at: index)
        persist()
    }

    func hasDiscovered(flowerID: Int) -> Bool {
        discoveries.contains { $0.flowerID == flowerID }
    }

    func discoveryCount(flowerID: Int) -> Int {
        discoveries.filter { $0.flowerID == flowerID }.count
    }

    func noteIdentifyFailure() {
        identifyFailureStreak += 1
    }

    /// 화면 12 — 실패가 누적됐으면 제목을 바꾼다 (B-11).
    var showsNotAFlowerGuide: Bool {
        identifyFailureStreak >= GamePolicy.identifyFailureStreakForGuideChange
    }
}
