import Foundation

/// 키 없이 화면 07~13을 완성하기 위한 Mock.
///
/// **반환값이 계약으로 고정돼 있다** (계약 2절). Android Mock과 값이 다르면
/// 같은 사진으로 서로 다른 화면이 떠서 흐름 검증 자체가 무의미해진다.
///
/// | 입력 | 반환 |
/// |---|---|
/// | 기본 | `0.82` / `0.11` / `0.04` → 화면 09 확정 경로 |
/// | 파일명에 `low` | `0.55` / `0.30` / `0.12` → 후보 3개 동일 크기 경로 |
/// | 파일명에 `fail` | 빈 배열 → 화면 12 판별 실패 |
///
/// 카메라로 찍은 사진에는 파일명이 없다. 그래서 `scenario`를 직접 주입할 수 있게 해
/// 디버그 메뉴에서 세 경로를 다 밟아볼 수 있게 한다.
struct MockFlowerRecognizer: FlowerRecognizer {

    enum Scenario: String, CaseIterable, Sendable {
        /// 1순위 확정 경로.
        case confident
        /// 임계값 미달 — 후보 3개를 같은 크기로 (화면 09 변형).
        case low
        /// 판별 실패 (화면 12).
        case fail

        /// 계약 표의 점수. **이 숫자를 바꾸면 Android도 바꿔야 한다.**
        var scores: [Double] {
            switch self {
            case .confident: return [0.82, 0.11, 0.04]
            case .low: return [0.55, 0.30, 0.12]
            case .fail: return []
            }
        }

        /// 파일명에서 시나리오를 고른다. 계약이 정한 판정 방식이다.
        static func from(fileName: String?) -> Scenario {
            guard let name = fileName?.lowercased() else { return .confident }
            if name.contains("fail") { return .fail }
            if name.contains("low") { return .low }
            return .confident
        }
    }

    /// 명시 지정. `nil`이면 `fileName`으로 판정한다.
    var scenario: Scenario?
    /// 파일명 기반 판정용. 앨범/테스트 픽스처 경로가 들어온다.
    var fileName: String?
    /// 화면 08은 `5초 정도 걸려요`라고 말한다. 0으로 두면 로딩 화면을 검증할 수 없다.
    var delay: Duration = .milliseconds(1500)

    func identify(
        imageData: Data,
        candidates: [Int]
    ) async throws -> [RecognitionCandidate] {
        try? await Task.sleep(for: delay)

        let scores = (scenario ?? Scenario.from(fileName: fileName)).scores
        guard !scores.isEmpty else { return [] }

        // 서버가 개화월 필터로 좁혀 준 후보에서 앞쪽을 집는다.
        // 후보가 3개보다 적으면 **있는 만큼만** 반환한다 (계약: "3개 미만이면 있는 만큼").
        return zip(candidates.prefix(GamePolicy.candidateCount), scores).map {
            RecognitionCandidate(flowerID: $0, score: $1)
        }
    }
}
