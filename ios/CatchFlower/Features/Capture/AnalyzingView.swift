import SwiftUI

/// 화면 08 AI 분석 중. 어두운 화면. 문구는 A 문서 08번.
///
/// **10초를 넘기면 문구를 바꾼다** — `조금 더 걸리고 있어요`.
/// 스피너만 계속 도는 화면은 사용자가 앱이 죽었다고 판단하는 지점이다.
struct AnalyzingView: View {
    let onCancel: () -> Void

    @State private var elapsed = 0

    private var isSlow: Bool { elapsed >= 10 }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: 16) {
                ProgressView()
                    .progressViewStyle(.circular)
                    .controlSize(.large)
                    .tint(.white)

                Text("어떤 꽃인지 보고 있어요")
                    .font(Theme.Typo.heroSmall)
                    .foregroundStyle(.white)

                Text(isSlow ? "조금 더 걸리고 있어요" : "5초 정도 걸려요")
                    .font(Theme.Typo.body)
                    .foregroundStyle(.white.opacity(0.85))
            }

            Spacer()

            Button(action: onCancel) {
                Text("취소")
                    .font(Theme.Typo.button)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: Theme.Metric.ghostButtonHeight)
                    .background(.white.opacity(0.15), in: Capsule())
            }
            .padding(.horizontal, Theme.Metric.screenPadding)
            .padding(.bottom, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.Palette.cameraBackground.ignoresSafeArea())
        .task {
            // 1초마다 센다. 판별이 끝나면 이 뷰가 사라져 Task도 취소된다.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                elapsed += 1
            }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    AnalyzingView(onCancel: {})
}
