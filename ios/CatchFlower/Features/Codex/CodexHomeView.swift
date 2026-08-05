import SwiftUI

/// 화면 04 도감 홈. 문구는 A 문서 04번이 전부다.
struct CodexHomeView: View {
    @Environment(AppSession.self) private var session
    /// 화면 22의 `꽃 찍어보기`가 촬영 모달을 여는 통로. 탭 바가 소유자다.
    var onStartCapture: (() -> Void)?
    @State private var filter = CodexFilter()
    @State private var showsFilterSheet = false
    @State private var selected: Flower?

    private var entries: [CodexEntry] { session.codexEntries }
    private var filtered: [CodexEntry] { filter.apply(to: entries) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    statusCard

                    if session.collectedCount == 0 {
                        // 화면 22 — 도감이 비어 있을 때는 그리드를 보여줘도 의미가 없다.
                        EmptyCodexView(onStartCapture: onStartCapture)
                    } else {
                        recentSection
                    }

                    allSection
                }
                .padding(.horizontal, Theme.Metric.screenPadding)
                .padding(.vertical, 16)
            }
            .background(Theme.Palette.background)
            .navigationTitle("내 꽃 도감")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showsFilterSheet) {
                CodexFilterSheet(filter: $filter, entries: entries)
            }
            .navigationDestination(item: $selected) { flower in
                CodexDetailView(flower: flower)
            }
        }
    }

    // MARK: - 현황 카드 · 진행 문구

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text("모은 꽃")
                    .font(Theme.Typo.sectionTitle)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Text("\(session.collectedCount)")
                    .font(Theme.Typo.heroSmall)
                    .foregroundStyle(Theme.Palette.primary)
                Text("/ \(GamePolicy.codexTotalCount)종")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Text("이번 시즌 \(session.seasonCollectedCount)종")
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(Theme.Palette.textPrimary)
            }

            ProgressView(
                value: Double(session.collectedCount),
                total: Double(GamePolicy.codexTotalCount)
            )
            .tint(Theme.Palette.primary)

            Text("도감 \(session.completionPercent)% 완성")
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .padding(16)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
    }

    // MARK: - 섹션 1 최근 발견한 꽃

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "최근 발견한 꽃", trailing: "전체 보기") {
                filter.collected = .collected
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(Array(session.recentlyDiscovered().enumerated()), id: \.element) {
                        index, flower in
                        Button { selected = flower } label: {
                            VStack(spacing: 6) {
                                FlowerSymbol(flower: flower)
                                    .frame(width: 72, height: 72)
                                Text(flower.name)
                                    .font(Theme.Typo.caption)
                                    .foregroundStyle(Theme.Palette.textPrimary)
                            }
                        }
                        // 어느 꽃이 뽑히는지는 계절에 따라 달라서 테스트가 이름을
                        // 고정할 수 없다. 자리로 지목할 수 있게 식별자를 준다.
                        .accessibilityIdentifier("recent.\(index)")
                    }
                }
            }
        }
    }

    // MARK: - 섹션 2 전체 200종

    private var allSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(
                title: "전체 \(GamePolicy.codexTotalCount)종",
                trailing: "필터"
            ) {
                showsFilterSheet = true
            }

            quickChips

            if filtered.isEmpty {
                // 화면 06 CTA와 같은 문구를 쓴다.
                Text("조건에 맞는 꽃이 없어요")
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            } else {
                LazyVGrid(
                    columns: Array(
                        repeating: GridItem(spacing: 12),
                        count: GamePolicy.codexGridColumns
                    ),
                    spacing: 16
                ) {
                    ForEach(filtered) { entry in
                        Button { selected = entry.flower } label: {
                            CodexGridCell(entry: entry)
                        }
                    }
                }
            }
        }
    }

    /// 화면 04 상단 칩 — `전체 / 모은 꽃 / 봄 / 여름 / 가을`.
    /// 겨울이 없는 건 오타가 아니라 문구 스펙 그대로다 (겨울 6종은 06 필터에서 고른다).
    private var quickChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(title: "전체", isSelected: filter.isEmpty) {
                    filter = CodexFilter()
                }
                FilterChip(
                    title: "모은 꽃",
                    isSelected: filter.collected == .collected && filter.seasons.isEmpty
                ) {
                    filter = CodexFilter(collected: .collected)
                }
                ForEach([FlowerSeason.spring, .summer, .autumn], id: \.self) { season in
                    FilterChip(
                        title: season.displayName,
                        isSelected: filter.seasons == [season]
                    ) {
                        filter = CodexFilter(seasons: [season])
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }
}

// MARK: - 그리드 셀

struct CodexGridCell: View {
    let entry: CodexEntry

    var body: some View {
        VStack(spacing: 6) {
            ZStack(alignment: .topTrailing) {
                FlowerSymbol(flower: entry.flower, isDiscovered: entry.isDiscovered)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1, contentMode: .fit)
                    .padding(10)
                    .background(Theme.Palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                            .stroke(Theme.Palette.border, lineWidth: 1)
                    )

                // 2회 이상 발견은 횟수를 보여준다 (기획서 6장 — 기록이 누적된다).
                if entry.discoveryCount > 1 {
                    Text("\(entry.discoveryCount)회")
                        .font(Theme.Typo.minimum)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Theme.Palette.primary, in: Capsule())
                        .padding(6)
                }
            }

            Text(entry.isDiscovered ? entry.flower.name : "미발견")
                .font(entry.isDiscovered ? Theme.Typo.bodyBold : Theme.Typo.body)
                .foregroundStyle(
                    entry.isDiscovered
                        ? Theme.Palette.textPrimary
                        : Theme.Palette.textSecondary
                )
                .lineLimit(1)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            entry.isDiscovered
                ? "\(entry.flower.name), \(entry.discoveryCount)회 발견"
                : "미발견 꽃"
        )
    }
}

// MARK: - 섹션 헤더

struct SectionHeader: View {
    let title: String
    var trailing: String?
    var action: (() -> Void)?

    var body: some View {
        HStack {
            Text(title)
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)
            Spacer()
            if let trailing, let action {
                TextOnlyButton(title: trailing, action: action)
            }
        }
    }
}

#Preview("도감 홈 · 비어 있음") {
    RootTabPreview(discoveryCount: 0)
}

#Preview("도감 홈 · 모은 꽃 있음") {
    RootTabPreview(discoveryCount: 12)
}
