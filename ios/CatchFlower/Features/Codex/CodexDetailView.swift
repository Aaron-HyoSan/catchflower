import SwiftUI

/// 화면 05 도감 상세. 문구는 A 문서 05번.
struct CodexDetailView: View {
    @Environment(AppSession.self) private var session
    let flower: Flower

    /// 삭제 확인 중인 기록. nil이면 다이얼로그가 닫혀 있다.
    @State private var pendingDeletion: Discovery?

    private var entry: CodexEntry { session.entry(for: flower) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                metrics
                storySection
                similarSection
                recordSection
            }
            .padding(.horizontal, Theme.Metric.screenPadding)
            .padding(.bottom, 32)
        }
        .background(Theme.Palette.background)
        .navigationTitle(flower.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        VStack(spacing: 12) {
            FlowerSymbol(flower: flower, isDiscovered: entry.isDiscovered)
                .frame(width: 160, height: 160)
                .padding(20)
                .frame(maxWidth: .infinity)
                .background(Theme.Palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))

            // 학명 줄 — `Rosa hybrida · 장미과`
            Text(flower.scientificLine)
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)

            // 속성 칩 — `여름 / 흔함 / 붉은색`
            HStack(spacing: 8) {
                AttributeChip(text: flower.season.displayName)
                AttributeChip(text: flower.rarity.displayName)
                AttributeChip(text: flower.color)
            }
        }
    }

    /// 지표 3칸 — `발견 횟수 4회 · 첫 발견 5월 2일 · 장소 3곳`
    private var metrics: some View {
        HStack(spacing: 0) {
            MetricCell(label: "발견 횟수", value: "\(entry.discoveryCount)회")
            Divider().frame(height: 32)
            MetricCell(label: "첫 발견", value: firstDiscoveredText)
            Divider().frame(height: 32)
            MetricCell(label: "장소", value: "\(entry.placeCount)곳")
        }
        .padding(.vertical, 14)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
    }

    private var firstDiscoveredText: String {
        guard let date = entry.firstDiscoveredAt else { return "-" }
        return date.formatted(.dateTime.month(.defaultDigits).day())
    }

    /// 꽃 이야기. **문구를 창작하지 않는다** — CSV의 `서식지`·개화기로 사실만 조립한다.
    /// 200종 설명문은 A 문서에 없다. 필요하면 A에 추가하고 기록해야 한다.
    private var storySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "꽃 이야기")
            Text("\(flower.bloomLabel)에 피어요. \(flower.habitat)에서 볼 수 있어요.")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textPrimary)
                .lineSpacing(4)
        }
    }

    /// 비슷한 꽃 — `비슷한 꽃 · 해당화, 찔레꽃`
    @ViewBuilder
    private var similarSection: some View {
        let similar = session.repository.flowers(ids: flower.similarFlowerIDs)
        if !similar.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "비슷한 꽃")
                Text(similar.map(\.name).joined(separator: ", "))
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
        }
    }

    /// 내 발견 기록 (우측 `4회`)
    @ViewBuilder
    private var recordSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "내 발견 기록", trailing: "\(entry.discoveryCount)회")

            if entry.discoveries.isEmpty {
                Text("아직 이 꽃을 만나지 못했어요")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 20)
            } else {
                ForEach(entry.discoveries.sorted { $0.capturedAt > $1.capturedAt }) { d in
                    DiscoveryRow(discovery: d) { pendingDeletion = d }
                }
            }
        }
        // **삭제는 확인을 받는다.** A 문서 3절 `이 발견 기록을 지울까요?`.
        // 사진 파일까지 지우므로 되돌릴 수 없다.
        .confirmDialog(.deleteDiscovery, isPresented: isConfirmingDelete) {
            if let pendingDeletion {
                session.delete(discoveryID: pendingDeletion.id)
            }
            pendingDeletion = nil
        }
    }

    /// 다이얼로그 표시 여부를 **대상 기록의 유무로** 만든다.
    /// `Bool` 하나를 따로 두면 "띄웠는데 대상이 nil"인 상태가 생긴다.
    private var isConfirmingDelete: Binding<Bool> {
        Binding(
            get: { pendingDeletion != nil },
            // 취소·바깥 탭으로 닫힐 때 대상도 같이 비운다.
            set: { if !$0 { pendingDeletion = nil } }
        )
    }
}

private struct AttributeChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(Theme.Typo.caption)
            .foregroundStyle(Theme.Palette.textSecondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Theme.Palette.surfaceAlt, in: Capsule())
    }
}

private struct MetricCell: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(Theme.Typo.bodyBold)
                .foregroundStyle(Theme.Palette.textPrimary)
            Text(label)
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

/// 기록 1줄. 배지는 `공개` / `비공개` (A 문서 05번).
struct DiscoveryRow: View {
    let discovery: Discovery
    /// 지우기 버튼. nil이면 버튼을 그리지 않는다 (다른 화면에서 재사용한다).
    var onDelete: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            DiscoveryThumbnail(discovery: discovery, side: 48)

            VStack(alignment: .leading, spacing: 3) {
                Text(discovery.capturedAt.formatted(.dateTime.month().day()))
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text(discovery.placeName ?? "장소 없음")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }

            Spacer()

            Text(discovery.visibility.badgeText)
                .font(Theme.Typo.minimum)
                .foregroundStyle(Theme.Palette.textSecondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Theme.Palette.surfaceAlt, in: Capsule())

            if let onDelete {
                // **스와이프 삭제를 쓰지 않는다.** 제스처 전용 기능 금지(타깃 제약) —
                // 스와이프를 모르면 지울 방법이 아예 없다. 눌리는 버튼을 둔다.
                Button(action: onDelete) {
                    // 아이콘 단독 금지라 라벨을 병기한다.
                    Text("지우기")
                        .font(Theme.Typo.minimum)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .frame(minWidth: Theme.Metric.minTouchTarget,
                               minHeight: Theme.Metric.minTouchTarget)
                }
                .accessibilityLabel("이 기록 지우기")
            }
        }
        .padding(12)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
    }
}
