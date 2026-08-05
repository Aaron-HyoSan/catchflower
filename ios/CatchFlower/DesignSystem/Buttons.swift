import SwiftUI

/// 버튼 6종. A 문서 1절 '버튼' 표가 유일한 근거다.
///
/// **아이콘만 있는 버튼은 만들지 않는다** — 반드시 텍스트를 병기한다 (`좋아요 12`).

// MARK: - Primary · 화면의 주 행동. 화면당 1개

struct PrimaryButton: View {
    let title: String
    /// 조건 미충족 시 문구를 **안내로 교체**한다 (`동네를 선택해 주세요`).
    var disabledTitle: String?
    var isEnabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(isEnabled ? title : (disabledTitle ?? title))
                .font(Theme.Typo.button)
                .foregroundStyle(isEnabled ? .white : Theme.Palette.textTertiary)
                .frame(maxWidth: .infinity)
                .frame(height: Theme.Metric.primaryButtonHeight)
                .background(isEnabled ? Theme.Palette.primary : Theme.Palette.surfaceAlt)
                .clipShape(Capsule())
        }
        .disabled(!isEnabled)
    }
}

// MARK: - Secondary · 대안 행동

struct SecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.Typo.button)
                .foregroundStyle(Theme.Palette.textPrimary)
                .frame(maxWidth: .infinity)
                .frame(height: Theme.Metric.secondaryButtonHeight)
                .background(Theme.Palette.surface)
                .overlay(Capsule().stroke(Theme.Palette.border, lineWidth: 1.5))
                .clipShape(Capsule())
        }
    }
}

// MARK: - Ghost · 회피·보조 (`나만 보기` `나중에 할게요`)

struct GhostButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Theme.Palette.textSecondary)
                .frame(maxWidth: .infinity)
                .frame(height: Theme.Metric.ghostButtonHeight)
                .background(Theme.Palette.surfaceAlt)
                .clipShape(Capsule())
        }
    }
}

// MARK: - Small · 카드 내부 (`길찾기` `추가` `초대하기`)

struct SmallButton: View {
    let title: String
    var filled: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(Theme.Typo.buttonSmall)
                .foregroundStyle(filled ? .white : Theme.Palette.textPrimary)
                .padding(.horizontal, 14)
                .frame(height: Theme.Metric.smallButtonHeight)
                .background(filled ? Theme.Palette.primary : Theme.Palette.surfaceAlt)
                .clipShape(Capsule())
        }
        // 최소 터치 영역 44 확보 — 버튼 높이가 36이라 세로 여백으로 메운다.
        .frame(minHeight: Theme.Metric.minTouchTarget)
    }
}

// MARK: - Text · 최소 강조 (`공유하지 않기` `전체 보기`)

struct TextOnlyButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.Palette.textSecondary)
                .frame(minHeight: Theme.Metric.minTouchTarget)
        }
    }
}

// MARK: - 칩 (필터)

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    /// 화면 04와 화면 06 시트에 **같은 라벨의 칩이 동시에** 존재한다(전체·모은 꽃·계절).
    /// 라벨만으로는 UI 테스트가 어느 쪽을 누르는지 정할 수 없어서 출처를 구분한다.
    var scope: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(isSelected ? Theme.Typo.bodyBold : Theme.Typo.body)
                .foregroundStyle(isSelected ? .white : Theme.Palette.textSecondary)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(isSelected ? Theme.Palette.primary : Theme.Palette.surface)
                .overlay(
                    Capsule().stroke(
                        isSelected ? Color.clear : Theme.Palette.border,
                        lineWidth: 1
                    )
                )
                .clipShape(Capsule())
        }
        .frame(minHeight: Theme.Metric.minTouchTarget)
        .accessibilityIdentifier(scope.map { "\($0).\(title)" } ?? title)
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 12) {
            PrimaryButton(title: "네, 맞아요") {}
            PrimaryButton(
                title: "연남동으로 시작하기",
                disabledTitle: "동네를 선택해 주세요",
                isEnabled: false
            ) {}
            SecondaryButton(title: "아니에요, 다시 찍을게요") {}
            GhostButton(title: "나만 보기") {}
            HStack {
                SmallButton(title: "길찾기") {}
                SmallButton(title: "추가", filled: true) {}
            }
            TextOnlyButton(title: "공유하지 않기") {}
            HStack {
                FilterChip(title: "전체", isSelected: true) {}
                FilterChip(title: "모은 꽃", isSelected: false) {}
                FilterChip(title: "봄", isSelected: false) {}
            }
        }
        .padding(Theme.Metric.screenPadding)
    }
    .background(Theme.Palette.background)
}
