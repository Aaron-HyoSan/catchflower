import Foundation

/// 인식 후보 1개. `score`는 **0.0~1.0**이다 — 퍼센트로 곱하지 않는다 (계약 2절).
struct RecognitionCandidate: Identifiable, Hashable, Sendable {
    let flowerID: Int
    let score: Double

    var id: Int { flowerID }

    /// 화면 09 표시용 `82%`. 곱셈은 **표시 직전에 한 번만** 한다.
    var percentText: String { "\(Int((score * 100).rounded()))%" }
}

enum RecognitionError: Error, Sendable {
    /// 네트워크·서버 오류. 화면 12와 다르다 — 이건 "다시 시도"이고 화면 12는 "다시 찍기"다.
    case unavailable
    /// 일일 상한(`GamePolicy.dailyAPICallLimitMultiplier`) 초과.
    case quotaExceeded
}

/// 꽃 인식 인터페이스. **벤더(PlantNet)를 이 뒤에 숨긴다** (계약 2절).
///
/// 이 장치 덕분에 A-1(엔진 선택)이 블로커에서 빠졌다. 키가 없어도
/// `MockFlowerRecognizer`로 화면 07~13 전체 흐름을 완성할 수 있다.
///
/// **개화월 하드 필터는 서버가 적용한 뒤 내려준다.** 그래서 `candidates`로
/// 후보 집합을 받는다 — 클라이언트가 응답을 다시 필터하지 않는다.
/// (Mock·온디바이스 검증 단계에서만 `FlowerRepository.flowersBlooming(inMonth:)`를 쓴다.)
protocol FlowerRecognizer: Sendable {
    /// - Parameters:
    ///   - imageData: 촬영 이미지 (장변 `GamePolicy.photoLongEdgePixels`로 축소된 것)
    ///   - candidates: 서버가 개화월 필터를 적용해 좁힌 후보 `flower_id` 목록
    /// - Returns: 점수 내림차순 후보 **최대 3개**. 빈 배열이면 판별 실패(화면 12).
    func identify(imageData: Data, candidates: [Int]) async throws -> [RecognitionCandidate]
}
