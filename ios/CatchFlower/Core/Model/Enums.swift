import Foundation

/// 공유계약 1-4절의 enum 6종.
///
/// **저장·전송은 여기 있는 원시 문자열을 그대로 쓴다.** 숫자로 바꾸지 않는다 —
/// 값이 추가돼도 순서가 깨지지 않게 하기 위함이고, Android와 같은 문자열을 써야 한다.
/// 화면에 보이는 한글은 `displayName`으로만 얻는다 (DB에 한글이 들어가면 안 된다).

// MARK: - season

enum FlowerSeason: String, Codable, CaseIterable, Sendable {
    case spring, summer, autumn, winter

    var displayName: String {
        switch self {
        case .spring: return "봄"
        case .summer: return "여름"
        case .autumn: return "가을"
        case .winter: return "겨울"
        }
    }
}

// MARK: - rarity

enum Rarity: String, Codable, CaseIterable, Sendable {
    case common, normal, rare

    /// 화면 06 필터 그룹 4는 '보기 쉬움'이라는 이름으로 이 값을 쓴다 (문구 스펙).
    var displayName: String {
        switch self {
        case .common: return "흔함"
        case .normal: return "보통"
        case .rare: return "귀함"
        }
    }

    /// 화면 10 축하 연출 강도. B-4 추가 요청 — 희귀종은 가장 강하게.
    var celebrationLevel: Int {
        switch self {
        case .common, .normal: return 1
        case .rare: return 3
        }
    }
}

// MARK: - ai_difficulty

enum AIDifficulty: String, Codable, CaseIterable, Sendable {
    case low, mid, high

    var displayName: String {
        switch self {
        case .low: return "하"
        case .mid: return "중"
        case .high: return "상"
        }
    }
}

// MARK: - visibility

/// ⚠️ **SwiftUI에 이미 `Visibility`가 있다** (`.visible`/`.hidden`). 같은 이름을 쓰면
/// `.toolbarBackground(.hidden)` 같은 곳에서 어느 타입인지 모호해진다. 그래서
/// Swift 타입 이름만 `ShareVisibility`로 두고, **저장·전송 문자열은 계약 그대로** 쓴다
/// (`public` / `friends` / `private`). DB 컬럼명도 `visibility`로 유지된다.
enum ShareVisibility: String, Codable, CaseIterable, Sendable {
    case `public`, friends, `private`

    /// 화면 13 옵션 문구. A 문서 2절 13번을 그대로 쓴다.
    var displayName: String {
        switch self {
        case .public: return "모두에게 공개"
        case .friends: return "친구에게만 공개"
        case .private: return "나만 보기"
        }
    }

    var detailText: String {
        switch self {
        case .public: return "지도를 보는 누구나 볼 수 있어요"
        case .friends: return "연락처로 연결된 친구만 볼 수 있어요"
        case .private: return "도감에만 저장돼요"
        }
    }

    /// 화면 05 기록 배지 (`공개` / `비공개`).
    var badgeText: String { self == .public ? "공개" : "비공개" }
}

// MARK: - friend_state (C-2 상호 수락)

enum FriendState: String, Codable, Sendable {
    case pending, accepted, blocked
}

// MARK: - report_state (C-1 3회 누적 자동 숨김)

enum ReportState: String, Codable, Sendable {
    case open, hidden, resolved
}
