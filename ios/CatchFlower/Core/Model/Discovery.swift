import Foundation

/// 발견 기록 1건. 공유계약 1-3절의 `discoveries` 테이블과 필드가 1:1이다.
struct Discovery: Identifiable, Codable, Hashable, Sendable {
    let id: UUID
    let userID: UUID
    /// `Flower.id`.
    let flowerID: Int
    /// 장변 1600px 압축 1장 (A-4 권고). 로컬 단계에서는 파일명이 들어간다.
    var photoURL: String?
    var lat: Double?
    var lng: Double?
    /// 카카오 로컬 검색 결과. 예 `서울숲`.
    var placeName: String?
    /// 행정동 코드.
    var dongCode: String?
    /// 시군구 코드. **⚠️ B-6이 여기 의존한다 — 동 코드와 둘 다 저장한다.**
    /// "동 단위 10명 미만이면 구 단위로 확장"이라 런타임에 동에서 구를 유도할 수 없다.
    var guCode: String?
    var visibility: ShareVisibility
    /// 0.0~1.0. PlantNet은 보정된 확률이라 그대로 저장한다 (퍼센트로 곱하지 않는다).
    let aiConfidence: Double
    /// **사용자가 몇 순위를 골랐는가 (1·2·3).** B-3 어뷰징 가드 ②.
    /// 계속 하위 순위만 고르는 계정은 신호다.
    let aiPickedRank: Int
    /// 신규(화면 10) vs 재발견(화면 11).
    let isFirstDiscovery: Bool
    let createdAt: Date
    /// C-8: 촬영 시각과 등록 시각의 차이를 검증하는 데 쓴다.
    let capturedAt: Date
    /// 화면 13 한 줄 남기기 (최대 `GamePolicy.mapShareNoteMaxLength`자).
    var note: String?

    enum CodingKeys: String, CodingKey {
        case id, lat, lng, visibility, note
        case userID = "user_id"
        case flowerID = "flower_id"
        case photoURL = "photo_url"
        case placeName = "place_name"
        case dongCode = "dong_code"
        case guCode = "gu_code"
        case aiConfidence = "ai_confidence"
        case aiPickedRank = "ai_picked_rank"
        case isFirstDiscovery = "is_first_discovery"
        case createdAt = "created_at"
        case capturedAt = "captured_at"
    }

    /// 지도에 공유됐는가. 도감 등록과 지도 공유는 별개다 (기획서 8장).
    var isSharedToMap: Bool { visibility != .private }
}

/// 한 종에 대한 사용자의 누적 상태. 화면 04 그리드·05 상세가 이걸 본다.
///
/// 기획서 6장: 같은 꽃을 여러 번 찍어도 **도감 항목은 하나**고 그 안에 기록이 누적된다.
struct CodexEntry: Identifiable, Hashable, Sendable {
    let flower: Flower
    var discoveries: [Discovery]

    var id: Int { flower.id }

    /// 발견한 적이 있는가. 없으면 화면 04에서 `미발견` 셀로 그린다.
    var isDiscovered: Bool { !discoveries.isEmpty }

    /// 화면 05 지표 — 발견 횟수 `4회`.
    var discoveryCount: Int { discoveries.count }

    /// 화면 05 지표 — 첫 발견 `5월 2일`.
    var firstDiscoveredAt: Date? { discoveries.map(\.capturedAt).min() }

    var lastDiscoveredAt: Date? { discoveries.map(\.capturedAt).max() }

    /// 화면 05 지표 — 장소 `3곳`. 좌표가 아니라 장소명 기준으로 센다.
    var placeCount: Int {
        Set(discoveries.compactMap(\.placeName)).count
    }
}
