import SwiftUI

/// 화면 22 빈 상태. 문구는 A 문서 22번.
///
/// **추천 섹션이 핵심이다.** `지금 이 계절, 동네에서 흔히 보이는 꽃`이라고 약속했으니
/// 개화월 필터 + `rarity == .common`으로 실제로 지금 피는 흔한 꽃을 보여줘야 한다.
/// 200종에서 아무거나 뽑아 놓으면 1월에 벚꽃을 추천하게 된다.
struct EmptyCodexView: View {
    @Environment(AppSession.self) private var session
    var onStartCapture: (() -> Void)?

    private var recommended: [Flower] {
        session.repository.recommendedForBeginners(month: session.currentMonth)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(spacing: 8) {
                Text("아직 모은 꽃이 없어요")
                    .font(Theme.Typo.heroSmall)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text("산책길에 만난 꽃을 찍어 첫 칸을 채워보세요")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 12)

            if let onStartCapture {
                PrimaryButton(title: "꽃 찍어보기", action: onStartCapture)
            }

            if recommended.isEmpty {
                // 12·1·2월은 피는 꽃이 거의 없다 (1월 1종). 추천이 빌 수 있다 —
                // 빈 섹션을 그리는 대신 휴지기임을 알린다 (B-1 권고 ②의 근거와 같다).
                Text("지금은 피는 꽃이 드문 철이에요")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Text("처음이라면 이 꽃부터")
                        .font(Theme.Typo.sectionTitle)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("지금 이 계절, 동네에서 흔히 보이는 꽃이에요")
                        .font(Theme.Typo.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }

                HStack(spacing: 12) {
                    ForEach(recommended) { flower in
                        VStack(spacing: 6) {
                            FlowerSymbol(flower: flower)
                                .frame(maxWidth: .infinity)
                                .aspectRatio(1, contentMode: .fit)
                            Text(flower.name)
                                .font(Theme.Typo.caption)
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
        .padding(20)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
    }
}
