import SwiftUI

/// 화면 10 신규 등록 · 화면 11 재발견. 문구는 A 문서 10·11번.
///
/// **한 파일에 둔 이유.** 두 화면은 구조가 같고 문구·강조만 다르다. 나누면
/// `지도에 공유하기` 이후 흐름이 두 곳에 복제된다.
///
/// 조사(`{꽃이름}가/이`)와 서수(`두 번째`)는 `KoreanText`가 처리한다 — A 문서가 명시한 요구다.
struct DiscoveryResultView: View {
    @Environment(AppSession.self) private var session

    let flower: Flower
    let discovery: Discovery
    /// `true`면 화면 10, `false`면 화면 11.
    let isFirst: Bool
    let onShare: () -> Void
    let onKeepPrivate: () -> Void

    private var totalCount: Int { session.discoveryCount(flowerID: flower.id) }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                celebration

                FlowerSymbol(flower: flower)
                    .frame(width: 140, height: 140)
                    // B-4: 희귀종은 축하를 가장 강하게. 지금은 테두리 강조로만 구분한다.
                    .padding(flower.rarity.celebrationLevel > 1 ? 12 : 0)
                    .background(
                        Circle()
                            .fill(Theme.Palette.surface)
                            .shadow(
                                color: .black.opacity(
                                    flower.rarity.celebrationLevel > 1 ? 0.12 : 0.05
                                ),
                                radius: 12
                            )
                    )

                if isFirst {
                    newFlowerBadge
                    codexMetrics
                    rankChange
                } else {
                    rediscoveryCard
                    photoStrip
                    seasonNotice
                }

                VStack(spacing: 10) {
                    PrimaryButton(title: "지도에 공유하기", action: onShare)
                    GhostButton(title: "나만 보기", action: onKeepPrivate)
                }
            }
            .padding(.horizontal, Theme.Metric.screenPadding)
            .padding(.vertical, 28)
        }
        .background(Theme.Palette.background)
    }

    // MARK: - 축하 2줄

    private var celebration: some View {
        VStack(spacing: 8) {
            if isFirst {
                Text("새로운 꽃을 발견했어요!")
                    .font(Theme.Typo.hero)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .multilineTextAlignment(.center)
                // `{꽃이름}가/이` — 받침에 따라 갈린다.
                Text("\(KoreanText.subject(flower.name)) 도감에 등록되었습니다.")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
            } else {
                // `{꽃이름}를/을 다시 발견했어요!`
                Text("\(KoreanText.object(flower.name)) 다시 발견했어요!")
                    .font(Theme.Typo.heroSmall)
                    .foregroundStyle(Theme.Palette.textPrimary)
                    .multilineTextAlignment(.center)
                // `이번이 {서수} 발견입니다.`
                Text("이번이 \(KoreanText.ordinal(totalCount)) 발견입니다.")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
    }

    /// 배지 — `{n}번째 꽃`
    private var newFlowerBadge: some View {
        Text("\(session.collectedCount)번째 꽃")
            .font(Theme.Typo.bodyBold)
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(Theme.Palette.primary, in: Capsule())
    }

    /// 지표 — `도감 37 / 200종 · 이번 시즌 13종 (+1)`
    private var codexMetrics: some View {
        HStack(spacing: 0) {
            VStack(spacing: 4) {
                Text("\(session.collectedCount) / \(GamePolicy.codexTotalCount)종")
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text("도감")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
            .frame(maxWidth: .infinity)

            Divider().frame(height: 32)

            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    Text("\(session.seasonCollectedCount)종")
                        .font(Theme.Typo.bodyBold)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    Text("(+1)")
                        .font(Theme.Typo.caption)
                        .foregroundStyle(Theme.Palette.success)
                }
                Text("이번 시즌")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.vertical, 14)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
    }

    /// 순위 변동 — `{동명} 순위 24위 → 21위`.
    ///
    /// **순위 계산은 서버 일이다** (B-6, 최소 10명). 백엔드(A-2) 전에는 값이 없다.
    /// 그래서 가짜 숫자를 만들지 않고 줄 자체를 감춘다 — 가짜 순위는 검증을 오염시킨다.
    @ViewBuilder
    private var rankChange: some View {
        EmptyView()
    }

    /// 기록 카드 — `이번 발견 기록 — 날짜 / 장소 / 총 발견`
    private var rediscoveryCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("이번 발견 기록")
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)

            HStack(spacing: 0) {
                labeled("날짜", discovery.capturedAt.formatted(.dateTime.month().day()))
                Divider().frame(height: 28)
                labeled("장소", discovery.placeName ?? "-")
                Divider().frame(height: 28)
                labeled("총 발견", "\(totalCount)회")
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
    }

    private func labeled(_ label: String, _ value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(Theme.Typo.bodyBold)
                .foregroundStyle(Theme.Palette.textPrimary)
                .lineLimit(1)
            Text(label)
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    /// 사진 스트립 — `지금까지 만난 {꽃이름}` (최신 사진에 `NEW`).
    ///
    /// 사진 실물은 아직 저장하지 않는다 (백엔드 A-2 미정). 자리와 `NEW` 배지 위치만
    /// 잡아 둔다 — 개수는 실제 기록 수와 맞으므로 흐름 검증에는 충분하다.
    private var photoStrip: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("지금까지 만난 \(flower.name)")
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    let sorted = session.entry(for: flower).discoveries
                        .sorted { $0.capturedAt > $1.capturedAt }
                    ForEach(Array(sorted.enumerated()), id: \.element.id) { index, item in
                        ZStack(alignment: .topLeading) {
                            DiscoveryThumbnail(discovery: item)
                            if index == 0 {
                                Text("NEW")
                                    .font(Theme.Typo.minimum)
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(Theme.Palette.primary, in: Capsule())
                                    .padding(4)
                            }
                        }
                        .accessibilityLabel(
                            item.capturedAt.formatted(.dateTime.month().day())
                        )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 안내 박스 — 규칙을 이득으로 설명한다 (A 문서 어조 규칙).
    private var seasonNotice: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("이번 시즌 종수는 늘지 않아요")
                .font(Theme.Typo.bodyBold)
                .foregroundStyle(Theme.Palette.textPrimary)
            Text("같은 꽃은 한 종으로 계산해요. 사진은 도감에 쌓여요.")
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Theme.Palette.surfaceAlt)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
