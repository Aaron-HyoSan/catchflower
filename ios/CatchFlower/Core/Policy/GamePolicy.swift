import Foundation

/// 게임 정책 숫자를 **한 곳에** 모은 파일.
///
/// 공유계약 3절: 여기 있는 값 대부분이 **오너 미확정(권고안)**이다.
/// 그래서 화면·로직 어디에도 숫자를 직접 쓰지 않는다. 답변이 오면 이 파일 한 줄만 고친다.
/// 흩어서 하드코딩하면 답변 한 번에 수십 군데를 고쳐야 한다.
///
/// **Android의 `GamePolicy` object와 값이 같아야 한다.** 혼자 바꾸지 않는다.
enum GamePolicy {

    // MARK: - 확정 (오너 결정 2026-08-04)

    /// 도감 총 종수. 출시 후 도감번호는 재배치하지 않는다.
    static let codexTotalCount = 200

    /// A-1 = PlantNet. A-3 = 카카오맵.
    /// 인식 엔진은 `FlowerRecognizer` 뒤에 숨는다 (공유계약 2절).

    // MARK: - 권고 · 오너 미확정 (★ ● ○)

    /// B-5 ★ 같은 종을 하루에 몇 번까지 인정하는가.
    /// 권고 ③ — 같은 종 + **같은 장소**는 하루 1회. 장소를 옮기면 인정한다.
    /// 이 값은 지도 도배 방지가 목적이었지만 API 호출을 35% 줄이는 효과가 더 크다.
    static let sameFlowerSamePlacePerDayLimit = 1

    /// B-5 판정에서 "같은 장소"로 볼 좌표 반올림 자릿수.
    /// 소수 4자리 ≈ 11m. 카카오 장소명 조회 캐시 키에도 같은 값을 쓴다.
    static let placeCoordinateRoundingDigits = 4

    /// B-6 ★ 지역 랭킹 최소 인원. 미만이면 구 단위로 확장한다.
    /// 그래서 DB에 동 코드와 구 코드를 **함께** 저장한다 (계약 1-3).
    static let regionRankingMinimumMembers = 10

    /// B-3 ★ AI 신뢰도 임계값 — 난이도별 차등. 권고 ② + ③ 병용.
    /// 임계값은 "확정 여부"가 아니라 **후보를 어떻게 보여줄지**를 가른다.
    /// - 이상: 1순위를 크게 + 2·3순위를 작게 (화면 09)
    /// - 미달: 후보 3개를 같은 크기로 "어느 꽃인가요?" (화면 09 변형)
    static func confidenceThreshold(for difficulty: AIDifficulty) -> Double {
        switch difficulty {
        case .low: return 0.60
        case .mid: return 0.70
        case .high: return 0.85
        }
    }

    /// B-3-a ✅ 1순위조차 이 값 미달이면 판별 실패(화면 12)로 보낸다.
    ///
    /// **2026-08-09 오너 승인: 0.30 → 0.05** (진행 (44) · 공유계약 3절).
    ///
    /// 0.30은 이 점수를 "30% 확신"으로 읽고 정한 값인데 그게 오해였다 — PlantNet 점수는
    /// **후보 종 전체에 퍼진 분포**라 정답 사진의 중위값이 0.354, 25% 분위가 0.157이다.
    /// AOS 실기기 실호출에서 0.30이 버린 4장이 **전부 정답**이었다
    /// (장미 0.110 · 서양민들레 0.112·0.185·0.019). 캐시 200장 기준 1순위 정답률
    /// **50% → 80%**이고, 유일한 오답 `해바라기 0.002`는 0.05에서도 막힌다.
    ///
    /// - Warning: AOS `MIN_CONFIDENCE_FOR_ANY_CANDIDATE`와 **같은 값이다.** 한쪽만 고치면
    ///   같은 사진에 두 앱이 다른 답을 준다 — 내린 쪽은 후보를 보여주고 안 내린 쪽은
    ///   화면 12로 보낸다. **혼자 바꾸지 않는다.**
    /// - Note: 낮은 점수를 통과시켜도 오등록이 되지 않는다. 이 값은 "확정" 관문이 아니라
    ///   **"후보를 보여줄 자격"**이고, 0.110은 화면 09 변형으로 가서 사용자가 고른다.
    static let identifyFailureFloor = 0.05

    /// B-3 ★ 후보는 항상 3개 제시한다 (공유계약 2절).
    static let candidateCount = 3

    /// B-3 어뷰징 가드 ① — 희귀종을 낮은 순위에서 고르면 즉시 확정하지 않고
    /// 사진을 한 장 더 받는다. `rarity == .rare` 이고 순위가 이 값 이상일 때.
    static let rareFlowerExtraPhotoRankThreshold = 2

    /// B-3 어뷰징 가드 ① 판정.
    ///
    /// **왜 여기 두는가.** 화면에서 `rarity == .rare && rank >= 2`를 직접 쓰면
    /// 규칙이 뷰 안에 숨는다. 오너 규칙은 정책 파일에 모아서 **테스트가 직접 부를 수
    /// 있게** 한다 — `rareFlowerExtraPhotoRankThreshold`는 상수만 있고 부르는 곳이
    /// 없어서 오랫동안 규칙이 아니라 장식이었다.
    ///
    /// **1순위는 통과시킨다.** AI가 가장 그럴 법하다고 본 답을 사용자가 그대로
    /// 받아들인 건 어뷰징의 모양이 아니다. 여기까지 막으면 진짜로 귀한 꽃을
    /// 만난 사람에게 매번 두 장을 요구하게 된다.
    ///
    /// **점수는 보지 않는다.** 희귀종은 표본이 적어 점수가 원래 낮게 나온다 —
    /// 점수까지 조건에 넣으면 정직한 발견을 더 자주 막는다.
    /// 낮은 순위를 **직접 고른 행위**가 신호다.
    static func needsExtraPhoto(rarity: Rarity, pickedRank: Int) -> Bool {
        rarity == .rare && pickedRank >= rareFlowerExtraPhotoRankThreshold
    }

    /// B-11 ● 판별 실패를 몇 번 연속하면 안내를 바꾸는가.
    /// 이 횟수에 도달하면 화면 12 제목이 `꽃이 아닐 수도 있어요`로 바뀐다.
    static let identifyFailureStreakForGuideChange = 3

    /// C-1 ★ 신고 누적 자동 숨김 횟수. UGC 앱은 신고 수단이 없으면 심사에서 반려된다.
    static let reportCountForAutoHide = 3

    /// C-4 ★ 가입 최소 연령. 만 14세 미만은 법정대리인 동의 개발이 붙는다.
    static let minimumAge = 14

    /// C-9 ○ 댓글 최대 길이. 수정 불가.
    static let commentMaxLength = 200

    /// 화면 13 한 줄 남기기 최대 길이 (문구 스펙 `0 / 40`).
    static let mapShareNoteMaxLength = 40

    /// A-4 ● 업로드 사진 장변 크기. 압축 1장만 보관한다.
    static let photoLongEdgePixels = 1600

    /// 서버 일일 API 호출 상한 배수 — 예상치의 3배에서 차단한다.
    static let dailyAPICallLimitMultiplier = 3

    // MARK: - 시즌 (B-1 ★ 권고 ②)

    /// B-1 권고 ② — 3~8월 / 9~11월 + **12~2월 휴지기**.
    /// 6개월로 단순히 자르면 11월 4종·12월 2종·1월 1종이라 한 시즌 후반이 죽는다.
    /// 없는 꽃으로 경쟁시키면 사용자가 앱을 지운다.
    enum Season: Sendable {
        case first          // 3~8월
        case second         // 9~11월
        case dormant        // 12~2월 · 경쟁을 돌리지 않는다

        /// 랭킹 화면을 띄우는가. 휴지기에는 "다음 시즌 D-day"만 보여준다.
        var runsRanking: Bool { self != .dormant }
    }

    static func season(forMonth month: Int) -> Season {
        switch month {
        case 3...8: return .first
        case 9...11: return .second
        default: return .dormant     // 12 · 1 · 2
        }
    }

    /// 계정 활동 지역 변경 가능 주기 (기획서 9장 — 6개월).
    static let regionChangeIntervalMonths = 6

    // MARK: - 표시 규칙

    /// 도감 그리드 열 수. 320pt(SE)에서도 3열을 유지한다 (B-8 8-1).
    static let codexGridColumns = 3

    /// 화면 11 서수 표기 — 이 횟수까지는 한글, 넘으면 `12번째`.
    static let koreanOrdinalMaxCount = 11
}
