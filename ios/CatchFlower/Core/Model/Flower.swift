import Foundation

/// 도감 마스터 1종. 공유계약 1-1절의 `flowers` 테이블과 필드가 1:1이다.
///
/// **`bloomMonths`를 클라이언트에서 파싱하지 않는다.** CSV의 `"3~4월"` 문자열은
/// `공용_적재/build_flowers_json.py`가 단 한 번 파싱해 `[3, 4]`로 넣어 준다.
/// 여기서 다시 파싱하면 Android와 다른 값이 나올 수 있고, 그러면
/// **개화월 하드 필터가 갈려 판별 결과 자체가 달라진다** (계약 1-2).
struct Flower: Identifiable, Codable, Hashable, Sendable {
    /// 도감번호 1~200. **고정 ID.** 출시 후 변경 금지 — 사용자 기록이 여기 연결된다.
    let id: Int
    let name: String
    let scientificName: String
    let family: String
    /// 개화월. 이미 파싱된 `int[]`. `12~4월`은 `[12, 1, 2, 3, 4]`로 온다.
    let bloomMonths: [Int]
    /// 원문 표기 (`"5~6월"`). 화면 09 부연 `장미과 · 5~6월에 피는 꽃`에 쓴다.
    let bloomLabel: String
    let season: FlowerSeason
    let color: String
    let rarity: Rarity
    let habitat: String
    let aiDifficulty: AIDifficulty
    /// 계약 1-1: CSV의 `비슷한꽃`은 이름 문자열이고, 적재 시 id로 변환된 결과다.
    let similarFlowerIDs: [Int]
    let illustBatch: Int

    enum CodingKeys: String, CodingKey {
        case id, name, family, color, rarity, habitat
        case scientificName = "scientific_name"
        case bloomMonths = "bloom_months"
        case bloomLabel = "bloom_label"
        case season
        case aiDifficulty = "ai_difficulty"
        case similarFlowerIDs = "similar_flower_ids"
        case illustBatch = "illust_batch"
    }

    /// 이 달에 피는가. **개화월 하드 필터의 판정 근거**다 (A-1 필수 구현).
    /// PlantNet은 79,047종에서 고르므로 11월에 "벚꽃"이 올 수 있다.
    func blooms(inMonth month: Int) -> Bool {
        bloomMonths.contains(month)
    }

    /// 화면 05 학명 줄 — `Rosa hybrida · 장미과`.
    var scientificLine: String { "\(scientificName) · \(family)" }

    /// 화면 09 부연 — `장미과 · 5~6월에 피는 꽃`.
    var identifyDetailLine: String { "\(family) · \(bloomLabel)에 피는 꽃" }
}

/// 번들에서 읽어 들이는 `flowers.json` 전체 구조.
struct FlowerCatalog: Codable, Sendable {
    let schemaVersion: Int
    let count: Int
    let flowers: [Flower]

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case count, flowers
    }
}
