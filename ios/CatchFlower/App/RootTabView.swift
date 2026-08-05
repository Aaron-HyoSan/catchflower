import SwiftUI

/// 하단 내비 5칸 — `도감 · 지도 · **꽃 촬영** · 랭킹 · 마이` (A 문서 04번).
///
/// 중앙 `꽃 촬영`은 탭이 아니라 **모달**이다. 촬영 흐름(07~13)은 되돌아올 곳이
/// 도감이라, 탭으로 두면 촬영 중에 다른 탭으로 새는 길이 생긴다.
///
/// **아이콘 단독 금지**라서 모든 칸에 텍스트 라벨을 붙인다 (타깃 제약).
struct RootTabView: View {
    @Environment(AppSession.self) private var session
    @Environment(ToastCenter.self) private var toasts
    @State private var tab: Tab = .codex
    @State private var isCapturing = false

    enum Tab: Hashable {
        case codex, map, ranking, mine
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            content
                // 탭 바에 가려지지 않게 아래를 비운다.
                .safeAreaInset(edge: .bottom) {
                    Color.clear.frame(height: Theme.Metric.tabBarHeight)
                }

            tabBar
        }
        .background(Theme.Palette.background)
        .fullScreenCover(isPresented: $isCapturing) {
            CaptureFlow()
        }
        // **토스트는 촬영 모달 바깥에 얹는다.** 등록 완료 토스트는 모달이 닫힌 뒤
        // 도감 위에 떠야 한다 — 모달 안에 두면 닫히면서 같이 사라진다.
        .toastOverlay(toasts)
    }

    @ViewBuilder
    private var content: some View {
        switch tab {
        case .codex: CodexHomeView { isCapturing = true }
        case .map: NotBuiltYetView(title: "지도", screenNumber: 14)
        case .ranking: NotBuiltYetView(title: "랭킹", screenNumber: 17)
        case .mine: NotBuiltYetView(title: "마이", screenNumber: 20)
        }
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            TabItem(title: "도감", systemImage: "book.closed", isSelected: tab == .codex) {
                tab = .codex
            }
            TabItem(title: "지도", systemImage: "map", isSelected: tab == .map) {
                tab = .map
            }

            // 중앙 돌출 촬영 버튼 (B-1 1-6, 지름 68).
            CaptureTabButton { isCapturing = true }

            TabItem(title: "랭킹", systemImage: "chart.bar", isSelected: tab == .ranking) {
                tab = .ranking
            }
            TabItem(title: "마이", systemImage: "person", isSelected: tab == .mine) {
                tab = .mine
            }
        }
        .frame(height: Theme.Metric.tabBarHeight)
        .background(
            Theme.Palette.surface
                .overlay(Theme.Palette.border.frame(height: 0.5), alignment: .top)
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

private struct TabItem: View {
    let title: String
    let systemImage: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: isSelected ? "\(systemImage).fill" : systemImage)
                    .font(.system(size: 20, weight: .medium))
                Text(title)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
            }
            .foregroundStyle(isSelected ? Theme.Palette.primary : Theme.Palette.textSecondary)
            .frame(maxWidth: .infinity, minHeight: Theme.Metric.minTouchTarget)
        }
        .accessibilityLabel(title)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

/// 중앙 촬영 버튼. 라벨을 버튼 안에 넣으면 68pt 원이 좁아서
/// `꽃 촬영` 두 단어가 안 들어간다 — 원 아래에 붙인다.
private struct CaptureTabButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Circle()
                    .fill(Theme.Palette.primary)
                    .frame(
                        width: Theme.Metric.captureButtonSize,
                        height: Theme.Metric.captureButtonSize
                    )
                    .overlay(
                        Image(systemName: "camera.fill")
                            .font(.system(size: 26, weight: .semibold))
                            .foregroundStyle(.white)
                    )
                    .overlay(Circle().stroke(Theme.Palette.surface, lineWidth: 4))
                Text("꽃 촬영")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Palette.primary)
            }
            .frame(maxWidth: .infinity)
            // 원이 탭 바 위로 솟는다.
            .offset(y: -18)
        }
        .accessibilityLabel("꽃 촬영")
    }
}

/// 이번 범위(1~6단계) 밖 화면의 자리. **빈 화면으로 두면 버그와 구별이 안 된다.**
struct NotBuiltYetView: View {
    let title: String
    let screenNumber: Int

    var body: some View {
        VStack(spacing: 10) {
            Text(title)
                .font(Theme.Typo.screenTitle)
            Text("화면 \(String(format: "%02d", screenNumber))은 아직 만들지 않았어요")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.background)
    }
}
