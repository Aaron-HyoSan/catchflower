import SwiftUI

/// 화면 13 지도 공유 설정. 문구는 A 문서 13번.
///
/// **도감 등록과 지도 공유는 별개다** (기획서 8장). 여기서 `공유하지 않기`를 눌러도
/// 이미 등록은 끝났다 — 그래서 토스트가 `도감에는 저장됐어요`다.
///
/// 공개 범위 옵션은 `모두에게 공개` / `친구에게만 공개` 둘만 노출한다.
/// `나만 보기`는 `공유하지 않기`와 같은 결과라, 세 개를 다 두면 사용자가 차이를 고민한다.
struct MapShareSettingsView: View {
    let flower: Flower
    let discovery: Discovery
    let onDone: (ShareVisibility, String?) -> Void
    let onSkip: () -> Void

    @State private var visibility: ShareVisibility = .public
    @State private var note = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("이 꽃을 지도에 공유할까요?")
                            .font(Theme.Typo.heroSmall)
                            .foregroundStyle(Theme.Palette.textPrimary)
                        Text("공유하면 다른 사람이 이 장소에서 꽃을 찾아볼 수 있어요.")
                            .font(Theme.Typo.body)
                            .foregroundStyle(Theme.Palette.textSecondary)
                    }

                    placeSection
                    visibilitySection
                    noteSection
                }
                .padding(Theme.Metric.screenPadding)
            }
            .background(Theme.Palette.background)
            .navigationTitle("지도에 공유하기")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 6) {
                    PrimaryButton(title: "공유하기") {
                        onDone(visibility, note.isEmpty ? nil : note)
                    }
                    TextOnlyButton(title: "공유하지 않기", action: onSkip)
                }
                .padding(.horizontal, Theme.Metric.screenPadding)
                .padding(.bottom, 8)
                .background(Theme.Palette.surface.ignoresSafeArea(edges: .bottom))
            }
        }
    }

    /// 발견 장소 (우측 `변경`). 부연 — `성동구 성수동1가 · 정확한 위치는 공개되지 않아요`
    private var placeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "발견 장소", trailing: "변경") {
                // 장소 변경은 화면 15(장소 상세)·카카오맵(A-3)에 걸려 있다. 이번 범위 밖.
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(discovery.placeName ?? "장소를 확인할 수 없어요")
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text("정확한 위치는 공개되지 않아요")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Theme.Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                    .stroke(Theme.Palette.border, lineWidth: 1)
            )
        }
    }

    private var visibilitySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("누구에게 보여줄까요?")
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)

            ForEach([ShareVisibility.public, .friends], id: \.self) { option in
                Button { visibility = option } label: {
                    HStack(spacing: 12) {
                        Image(systemName: visibility == option
                            ? "largecircle.fill.circle" : "circle")
                            .font(.system(size: 22))
                            .foregroundStyle(
                                visibility == option
                                    ? Theme.Palette.primary
                                    : Theme.Palette.border
                            )
                        VStack(alignment: .leading, spacing: 3) {
                            Text(option.displayName)
                                .font(Theme.Typo.bodyBold)
                                .foregroundStyle(Theme.Palette.textPrimary)
                            Text(option.detailText)
                                .font(Theme.Typo.caption)
                                .foregroundStyle(Theme.Palette.textSecondary)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .frame(minHeight: Theme.Metric.minTouchTarget)
                    .background(Theme.Palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                            .stroke(
                                visibility == option
                                    ? Theme.Palette.primary
                                    : Theme.Palette.border,
                                lineWidth: visibility == option ? 2 : 1
                            )
                    )
                }
            }
        }
    }

    /// 한 줄 남기기 (안 써도 돼요) · `0 / 40`
    private var noteSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("한 줄 남기기")
                    .font(Theme.Typo.sectionTitle)
                    .foregroundStyle(Theme.Palette.textPrimary)
                Text("(안 써도 돼요)")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                Spacer()
                Text("\(note.count) / \(GamePolicy.mapShareNoteMaxLength)")
                    .font(Theme.Typo.caption)
                    .foregroundStyle(
                        note.count >= GamePolicy.mapShareNoteMaxLength
                            ? Theme.Palette.warning
                            : Theme.Palette.textSecondary
                    )
            }

            TextField(
                "예: 숲길 끝 벤치 옆에 활짝 피었어요",
                text: $note,
                axis: .vertical
            )
            .font(Theme.Typo.body)
            .lineLimit(2, reservesSpace: true)
            .padding(12)
            .background(Theme.Palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Theme.Palette.border, lineWidth: 1)
            )
            // 40자에서 자른다. 자를 때 붙여넣기도 막아야 하므로 값 변경마다 검사한다.
            .onChange(of: note) { _, newValue in
                if newValue.count > GamePolicy.mapShareNoteMaxLength {
                    note = String(newValue.prefix(GamePolicy.mapShareNoteMaxLength))
                }
            }
        }
    }
}
