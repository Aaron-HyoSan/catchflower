import SwiftUI

/// 디자인 시스템 최소 세트.
///
/// 디자이너 산출물(B-1 Figma)이 오기 전의 **개발용 기준값**이다.
/// 값의 근거는 `디자이너_업무/A_문구·버튼_스펙.md` 1절과
/// `B_화면_UI디자인_업무목록.md` 공통 전제다. Figma가 오면 이 파일만 갈아 끼운다.
///
/// **타깃(40~50대 여성) 제약은 취향이 아니라 요구사항이다** (B 문서):
/// - Light/Thin 굵기 금지. 본문은 Regular 이상
/// - `#999` 이하 저채도 회색을 본문에 쓰지 않는다. 명도 대비 4.5:1 이상
/// - 아이콘 단독 버튼 금지. 텍스트 라벨 병기
/// - 제스처 전용 기능 금지
enum Theme {

    // MARK: - 색

    enum Palette {
        /// Primary. 일러스트 200종이 다채로우므로 UI는 저채도로 둔다 (B-1 1-1).
        static let primary = Color(red: 0.36, green: 0.55, blue: 0.42)
        static let primaryPressed = Color(red: 0.29, green: 0.46, blue: 0.35)

        static let background = Color(red: 0.98, green: 0.98, blue: 0.97)
        static let surface = Color.white
        static let surfaceAlt = Color(red: 0.95, green: 0.95, blue: 0.94)
        static let border = Color(red: 0.85, green: 0.85, blue: 0.84)

        /// 텍스트 3단계. 가장 연한 것도 대비 4.5:1을 넘긴다.
        static let textPrimary = Color(red: 0.13, green: 0.13, blue: 0.12)
        static let textSecondary = Color(red: 0.36, green: 0.36, blue: 0.35)
        static let textTertiary = Color(red: 0.47, green: 0.47, blue: 0.46)

        static let success = Color(red: 0.20, green: 0.55, blue: 0.33)
        static let warning = Color(red: 0.80, green: 0.53, blue: 0.13)
        static let error = Color(red: 0.75, green: 0.25, blue: 0.22)

        /// 화면 07·08은 다크 화면이다 (문구 스펙 표기).
        static let cameraBackground = Color(red: 0.09, green: 0.09, blue: 0.10)

        /// 대표색 필터·플레이스홀더에 쓰는 꽃 색. CSV `대표색` 값이 키다.
        static func flowerColor(_ name: String) -> Color {
            switch name {
            case "흰색": return Color(red: 0.97, green: 0.97, blue: 0.95)
            case "노랑": return Color(red: 0.98, green: 0.82, blue: 0.28)
            case "분홍": return Color(red: 0.96, green: 0.68, blue: 0.75)
            case "붉은색": return Color(red: 0.85, green: 0.29, blue: 0.29)
            case "보라": return Color(red: 0.64, green: 0.51, blue: 0.80)
            case "파랑": return Color(red: 0.42, green: 0.62, blue: 0.86)
            case "주황": return Color(red: 0.96, green: 0.62, blue: 0.28)
            default: return Color(red: 0.72, green: 0.76, blue: 0.68)   // 기타
            }
        }
    }

    // MARK: - 타이포 (A 문서 1절 7단계)

    enum Typo {
        /// 화면 제목 19~21pt Bold — 헤더 `내 꽃 도감`.
        static let screenTitle = Font.system(size: 20, weight: .bold)
        /// 축하·질문 대상 24~32pt Bold — `장미`, `연남동 4위`.
        static let hero = Font.system(size: 28, weight: .bold)
        static let heroSmall = Font.system(size: 24, weight: .bold)
        /// 섹션 제목 13.5~14.5pt Bold.
        static let sectionTitle = Font.system(size: 14, weight: .bold)
        /// 본문 12~13.5pt Regular.
        static let body = Font.system(size: 13, weight: .regular)
        static let bodyBold = Font.system(size: 13, weight: .semibold)
        /// 보조 설명 10.5~11.5pt.
        static let caption = Font.system(size: 11, weight: .regular)
        /// **최소 크기 10.5pt. 이보다 작게 쓰지 않는다.**
        static let minimum = Font.system(size: 10.5, weight: .regular)
        /// 버튼 라벨 15~16pt Bold.
        static let button = Font.system(size: 16, weight: .bold)
        static let buttonSmall = Font.system(size: 13, weight: .semibold)
    }

    // MARK: - 치수 (B 문서 공통 전제)

    enum Metric {
        static let screenPadding: CGFloat = 20
        static let cardRadius: CGFloat = 14
        static let chipRadius: CGFloat = 16

        /// 최소 터치 영역 44×44. 아이콘 단독 버튼도 이 값을 지킨다.
        static let minTouchTarget: CGFloat = 44

        static let primaryButtonHeight: CGFloat = 56
        static let secondaryButtonHeight: CGFloat = 56
        static let ghostButtonHeight: CGFloat = 50
        static let smallButtonHeight: CGFloat = 36

        /// 하단 내비 높이 64 + 홈 인디케이터 24.
        static let tabBarHeight: CGFloat = 64
        /// 중앙 돌출 촬영 버튼 지름 68 (B-1 1-6).
        static let captureButtonSize: CGFloat = 68
    }
}
