import SwiftUI
import UIKit

/// 화면 09 AI 판별 결과. 문구는 A 문서 09번.
///
/// **경로가 두 개다** (B-3 권고 ②+③ 병용, `GamePolicy.confidenceThreshold`):
/// - 1순위 점수 ≥ 임계값 → `이 꽃은 / {꽃이름} / 인가요?` + 2·3순위는 작게
/// - 미달 → 후보 3개를 **같은 크기로** 놓고 고르게 한다
///
/// 임계값은 꽃의 `ai_difficulty`에 따라 다르다 (하 60 · 중 70 · 상 85).
/// 인식이 어려운 꽃에 같은 기준을 쓰면 오등록이 늘고, 그게 랭킹 신뢰를 깬다.
struct IdentifyConfirmView: View {
    @Environment(AppSession.self) private var session

    let photo: CapturedPhoto
    let candidates: [RecognitionCandidate]
    /// 고른 후보와 **그 순위**(1·2·3)를 함께 넘긴다 — B-3 어뷰징 가드 ②.
    let onConfirm: (RecognitionCandidate, Int) -> Void
    let onRetake: () -> Void

    private var top: RecognitionCandidate? { candidates.first }

    private var topFlower: Flower? {
        top.flatMap { session.repository[$0.flowerID] }
    }

    /// 1순위가 임계값을 넘었는가. 난이도는 1순위 꽃 기준으로 본다.
    private var isConfident: Bool {
        guard let top, let flower = topFlower else { return false }
        return top.score >= GamePolicy.confidenceThreshold(for: flower.aiDifficulty)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                photoThumbnail

                if isConfident {
                    confidentBody
                } else {
                    ambiguousBody
                }
            }
            .padding(.horizontal, Theme.Metric.screenPadding)
            .padding(.vertical, 20)
        }
        .background(Theme.Palette.background)
    }

    private var photoThumbnail: some View {
        RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
            .fill(Theme.Palette.surfaceAlt)
            .frame(height: 180)
            .overlay {
                if let image = UIImage(data: photo.data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
                } else {
                    // 시뮬레이터 픽스처는 이미지 데이터가 없다.
                    Image(systemName: "camera.macro")
                        .font(.system(size: 40))
                        .foregroundStyle(Theme.Palette.textTertiary)
                }
            }
    }

    // MARK: - 확정 경로

    @ViewBuilder
    private var confidentBody: some View {
        if let top, let flower = topFlower {
            VStack(spacing: 20) {
                // 질문 3줄 — `이 꽃은 / {꽃이름} / 인가요?`
                VStack(spacing: 6) {
                    Text("이 꽃은")
                        .font(Theme.Typo.body)
                        .foregroundStyle(Theme.Palette.textSecondary)
                    Text(flower.name)
                        .font(Theme.Typo.hero)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("인가요?")
                        .font(Theme.Typo.body)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }

                FlowerSymbol(flower: flower)
                    .frame(width: 120, height: 120)

                // 부연 — `장미과 · 5~6월에 피는 꽃`
                Text(flower.identifyDetailLine)
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)

                PrimaryButton(title: "네, 맞아요") { onConfirm(top, 1) }
                SecondaryButton(title: "아니에요, 다시 찍을게요", action: onRetake)

                hintSection(for: flower)

                // 2·3순위는 작게. 여기서 고르면 순위가 그대로 기록된다.
                if candidates.count > 1 {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("혹시 이 꽃인가요?")
                            .font(Theme.Typo.sectionTitle)
                            .foregroundStyle(Theme.Palette.textSecondary)
                        ForEach(Array(candidates.dropFirst().enumerated()), id: \.element.id) {
                            index, candidate in
                            if let other = session.repository[candidate.flowerID] {
                                AlternateCandidateRow(flower: other) {
                                    onConfirm(candidate, index + 2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - 애매한 경로 (후보 3개 동일 크기)

    private var ambiguousBody: some View {
        VStack(spacing: 20) {
            VStack(spacing: 6) {
                Text("어느 꽃인가요?")
                    .font(Theme.Typo.heroSmall)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text("가장 비슷한 꽃을 골라 주세요")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }

            ForEach(Array(candidates.enumerated()), id: \.element.id) { index, candidate in
                if let flower = session.repository[candidate.flowerID] {
                    CandidateCard(flower: flower, rank: index + 1) {
                        onConfirm(candidate, index + 1)
                    }
                }
            }

            SecondaryButton(title: "아니에요, 다시 찍을게요", action: onRetake)
        }
    }

    /// 힌트 — `비슷한 꽃 · 해당화, 찔레꽃 / 다르면 다시 찍어 주세요`
    @ViewBuilder
    private func hintSection(for flower: Flower) -> some View {
        let similar = session.repository.flowers(ids: flower.similarFlowerIDs)
        if !similar.isEmpty {
            VStack(spacing: 4) {
                Text("비슷한 꽃 · \(similar.map(\.name).joined(separator: ", "))")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Text("다르면 다시 찍어 주세요")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
        }
    }
}

/// 후보 카드. 세 개가 **같은 크기**여야 한다 — 크기가 다르면 그게 곧 유도다.
private struct CandidateCard: View {
    let flower: Flower
    let rank: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                FlowerSymbol(flower: flower)
                    .frame(width: 60, height: 60)

                VStack(alignment: .leading, spacing: 4) {
                    Text(flower.name)
                        .font(Theme.Typo.bodyBold)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text(flower.identifyDetailLine)
                        .font(Theme.Typo.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundStyle(Theme.Palette.textTertiary)
            }
            .padding(14)
            .background(Theme.Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                    .stroke(Theme.Palette.border, lineWidth: 1)
            )
        }
        // **점수를 보여주지 않는다.** `55%`를 띄우면 사용자가 확률을 신뢰도로 읽고,
        // 낮은 숫자를 보면 맞는 답도 안 고른다. A 문서에도 점수 문구가 없다.
        .accessibilityLabel("\(rank)번 후보, \(flower.name)")
    }
}

private struct AlternateCandidateRow: View {
    let flower: Flower
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                FlowerSymbol(flower: flower)
                    .frame(width: 36, height: 36)
                Text(flower.name)
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Spacer()
                Text("이 꽃이에요")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.primary)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: Theme.Metric.minTouchTarget)
            .background(Theme.Palette.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}
