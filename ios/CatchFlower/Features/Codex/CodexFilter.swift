import Foundation

/// 화면 06 필터 상태. 4개 그룹 (A 문서 06번).
///
/// 계절·색상·희귀도는 **여러 개 선택**이다. 비어 있으면 "전체"로 본다 —
/// `nil`과 "아무것도 안 고름"을 따로 두면 상태가 두 벌이 된다.
struct CodexFilter: Equatable, Sendable {

    /// 그룹 1 — 수집 여부.
    enum Collected: String, CaseIterable, Sendable {
        case all, collected, notCollected

        var displayName: String {
            switch self {
            case .all: return "전체"
            case .collected: return "모은 꽃"
            case .notCollected: return "미발견"
            }
        }
    }

    var collected: Collected = .all
    var seasons: Set<FlowerSeason> = []
    /// CSV `대표색` 원문 문자열. A 문서 06번이 6색만 노출한다.
    var colors: Set<String> = []
    var rarities: Set<Rarity> = []

    /// 화면 06 색상 그룹에 띄우는 6색. CSV에는 `주황`·`기타`도 있지만
    /// 문구 스펙이 정한 목록을 따른다 (임의로 늘리지 않는다).
    static let filterColors = ["흰색", "노랑", "분홍", "붉은색", "보라", "파랑"]

    var isEmpty: Bool {
        collected == .all && seasons.isEmpty && colors.isEmpty && rarities.isEmpty
    }

    /// 화면 04 상단 칩(`전체 / 모은 꽃 / 봄 / 여름 / 가을`)에서 온 단순 필터도
    /// 같은 구조를 쓴다. 필터가 두 벌이면 화면 04와 06이 어긋난다.
    func matches(_ entry: CodexEntry) -> Bool {
        switch collected {
        case .all: break
        case .collected: if !entry.isDiscovered { return false }
        case .notCollected: if entry.isDiscovered { return false }
        }
        if !seasons.isEmpty && !seasons.contains(entry.flower.season) { return false }
        if !colors.isEmpty && !colors.contains(entry.flower.color) { return false }
        if !rarities.isEmpty && !rarities.contains(entry.flower.rarity) { return false }
        return true
    }

    func apply(to entries: [CodexEntry]) -> [CodexEntry] {
        entries.filter(matches)
    }
}
