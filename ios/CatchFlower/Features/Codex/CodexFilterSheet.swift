import SwiftUI

/// 화면 06 도감 필터. 문구는 A 문서 06번.
///
/// **CTA가 `{n}종 보기`다** — 즉 적용 전에 결과 개수를 먼저 보여줘야 한다.
/// 그래서 시트 안에서 임시 필터를 들고 실시간으로 세고, `보기`를 눌러야 반영한다.
struct CodexFilterSheet: View {
    @Binding var filter: CodexFilter
    let entries: [CodexEntry]

    @Environment(\.dismiss) private var dismiss
    @State private var draft: CodexFilter

    init(filter: Binding<CodexFilter>, entries: [CodexEntry]) {
        self._filter = filter
        self.entries = entries
        self._draft = State(initialValue: filter.wrappedValue)
    }

    private var matchCount: Int { draft.apply(to: entries).count }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    group("수집 여부") {
                        ForEach(CodexFilter.Collected.allCases, id: \.self) { option in
                            FilterChip(
                                title: option.displayName,
                                isSelected: draft.collected == option,
                                scope: "filterSheet"
                            ) {
                                draft.collected = option
                            }
                        }
                    }

                    group("계절") {
                        ForEach(FlowerSeason.allCases, id: \.self) { season in
                            FilterChip(
                                title: season.displayName,
                                isSelected: draft.seasons.contains(season),
                                scope: "filterSheet"
                            ) {
                                draft.seasons.toggleMember(season)
                            }
                        }
                    }

                    group("색상") {
                        ForEach(CodexFilter.filterColors, id: \.self) { color in
                            FilterChip(
                                title: color,
                                isSelected: draft.colors.contains(color),
                                scope: "filterSheet"
                            ) {
                                draft.colors.toggleMember(color)
                            }
                        }
                    }

                    group("보기 쉬움") {
                        ForEach(Rarity.allCases, id: \.self) { rarity in
                            FilterChip(
                                title: rarity.displayName,
                                isSelected: draft.rarities.contains(rarity),
                                scope: "filterSheet"
                            ) {
                                draft.rarities.toggleMember(rarity)
                            }
                        }
                    }
                }
                .padding(Theme.Metric.screenPadding)
            }
            .background(Theme.Palette.background)
            .navigationTitle("필터")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    TextOnlyButton(title: "초기화") { draft = CodexFilter() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                // 0종이면 문구를 안내로 교체하고 비활성 (A 문서 06번).
                PrimaryButton(
                    title: "\(matchCount)종 보기",
                    disabledTitle: "조건에 맞는 꽃이 없어요",
                    isEnabled: matchCount > 0
                ) {
                    filter = draft
                    dismiss()
                }
                .padding(Theme.Metric.screenPadding)
                .background(Theme.Palette.surface.ignoresSafeArea(edges: .bottom))
            }
        }
    }

    /// 칩이 한 줄을 넘기면 다음 줄로 흐른다. `Grid`는 열 수를 고정해서 안 맞는다.
    private func group<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)
            FlowLayout(spacing: 8) { content() }
        }
    }
}

private extension Set {
    mutating func toggleMember(_ member: Element) {
        if contains(member) { remove(member) } else { insert(member) }
    }
}

/// 칩을 줄바꿈해 배치한다. `LazyVGrid`는 칩 너비가 글자마다 달라서 못 쓴다.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = arrange(subviews: subviews, in: width)
        let height = rows.reduce(0) { $0 + $1.height } +
            spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = arrange(subviews: subviews, in: bounds.width)
        var y = bounds.minY
        for row in rows {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(size)
                )
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(subviews: Subviews, in width: CGFloat) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width && !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            if current.indices.isEmpty {
                current.width = size.width
            } else {
                current.width += spacing + size.width
            }
            current.indices.append(index)
            current.height = max(current.height, size.height)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}
