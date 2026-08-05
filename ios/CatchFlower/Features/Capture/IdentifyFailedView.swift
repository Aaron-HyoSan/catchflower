import SwiftUI

/// 화면 12 AI 판별 실패. 문구는 A 문서 12번.
///
/// **3회 연속 실패하면 제목이 바뀐다** (`꽃이 아닐 수도 있어요`) — B-11.
/// 같은 실패 문구를 반복하면 사용자가 "앱이 고장났다"로 읽는다. 원인을 다르게 짚어 준다.
struct IdentifyFailedView: View {
    let showsNotAFlowerGuide: Bool
    let onRetake: () -> Void
    let onLater: () -> Void

    /// A 문서 12번의 팁 4개. **순서까지 그대로** 쓴다.
    private let tips = [
        "꽃 한 송이가 화면에 꽉 차게",
        "그림자 없는 밝은 곳에서",
        "정면이나 살짝 위에서",
        "흔들리지 않게 잠시 멈춰서",
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                VStack(spacing: 8) {
                    Text(showsNotAFlowerGuide ? "꽃이 아닐 수도 있어요" : "어떤 꽃인지 알 수 없었어요")
                        .font(Theme.Typo.heroSmall)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .multilineTextAlignment(.center)
                    Text("다시 한 번 찍어 주시겠어요?")
                        .font(Theme.Typo.body)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
                .padding(.top, 12)

                // 사진 하단 — `이 사진은 저장되지 않았어요`
                VStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                        .fill(Theme.Palette.surfaceAlt)
                        .frame(height: 140)
                        .overlay(
                            Image(systemName: "camera.macro")
                                .font(.system(size: 34))
                                .foregroundStyle(Theme.Palette.textTertiary)
                        )
                    Text("이 사진은 저장되지 않았어요")
                        .font(Theme.Typo.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("이렇게 찍으면 잘 알아봐요")
                        .font(Theme.Typo.sectionTitle)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    ForEach(tips, id: \.self) { tip in
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Theme.Palette.primary)
                            Text(tip)
                                .font(Theme.Typo.body)
                                .foregroundStyle(Theme.Palette.textPrimary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Theme.Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                        .stroke(Theme.Palette.border, lineWidth: 1)
                )

                VStack(spacing: 10) {
                    PrimaryButton(title: "다시 찍기", action: onRetake)
                    // 3회 연속이면 도감 홈 복귀를 유도한다 (B-11).
                    GhostButton(
                        title: showsNotAFlowerGuide ? "도감으로 돌아가기" : "나중에 할게요",
                        action: onLater
                    )
                }
            }
            .padding(.horizontal, Theme.Metric.screenPadding)
            .padding(.bottom, 28)
        }
        .background(Theme.Palette.background)
    }
}

#Preview("실패 1회") {
    IdentifyFailedView(showsNotAFlowerGuide: false, onRetake: {}, onLater: {})
}

#Preview("실패 3회 연속") {
    IdentifyFailedView(showsNotAFlowerGuide: true, onRetake: {}, onLater: {})
}
