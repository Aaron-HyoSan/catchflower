import Foundation

/// 도감 마스터 200종을 번들에서 읽어 제공한다.
///
/// `flowers.json`은 **생성물**이다. 원본은 `공용_적재/build_flowers_json.py`이고
/// 그 원본의 원본은 `꽃도감/_tools/flowers.py`다. JSON을 직접 고치지 않는다.
final class FlowerRepository: Sendable {

    let flowers: [Flower]
    private let byID: [Int: Flower]
    private let byName: [String: Flower]

    /// 번들에서 적재한다. 데이터가 없거나 깨졌으면 **즉시 죽인다** —
    /// 도감 데이터 없이 뜬 앱은 빈 화면만 보여주고 원인을 숨긴다.
    init(bundle: Bundle = .main) {
        guard let url = bundle.url(forResource: "flowers", withExtension: "json") else {
            fatalError("flowers.json이 번들에 없다. project.yml의 resources 설정을 확인한다.")
        }
        do {
            let data = try Data(contentsOf: url)
            let catalog = try JSONDecoder().decode(FlowerCatalog.self, from: data)
            precondition(
                catalog.flowers.count == catalog.count,
                "flowers.json의 count(\(catalog.count))와 실제 종수(\(catalog.flowers.count))가 다르다"
            )
            self.flowers = catalog.flowers
        } catch {
            fatalError("flowers.json 적재 실패: \(error)")
        }
        self.byID = Dictionary(uniqueKeysWithValues: flowers.map { ($0.id, $0) })
        self.byName = Dictionary(uniqueKeysWithValues: flowers.map { ($0.name, $0) })
    }

    /// 테스트·프리뷰용.
    init(flowers: [Flower]) {
        self.flowers = flowers
        self.byID = Dictionary(uniqueKeysWithValues: flowers.map { ($0.id, $0) })
        self.byName = Dictionary(uniqueKeysWithValues: flowers.map { ($0.name, $0) })
    }

    subscript(id: Int) -> Flower? { byID[id] }

    func flower(named name: String) -> Flower? { byName[name] }

    func flowers(ids: [Int]) -> [Flower] { ids.compactMap { byID[$0] } }

    /// **개화월 하드 필터** (A-1 필수 구현).
    ///
    /// PlantNet은 79,047종에서 고르므로 11월에 "벚꽃"이 올 수 있다.
    /// 인식 요청의 후보 집합을 만들 때, 그리고 응답 후보를 걸러낼 때 둘 다 이걸 쓴다.
    ///
    /// 계약상 서버가 적용한 뒤 내려주는 것이 원칙이고, 이 메서드는
    /// **Mock 단계와 온디바이스 검증용**이다. 서버가 붙으면 서버 결과를 신뢰한다.
    func flowersBlooming(inMonth month: Int) -> [Flower] {
        flowers.filter { $0.blooms(inMonth: month) }
    }

    /// 화면 22 — `처음이라면 이 꽃부터`. 지금 이 계절에 흔히 보이는 꽃.
    func recommendedForBeginners(month: Int, limit: Int = 4) -> [Flower] {
        flowersBlooming(inMonth: month)
            .filter { $0.rarity == .common }
            .sorted { $0.id < $1.id }
            .prefix(limit)
            .map { $0 }
    }
}
