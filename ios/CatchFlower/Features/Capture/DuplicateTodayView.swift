import SwiftUI

/// B-5 — 같은 종 + 같은 장소를 하루에 두 번 찍었을 때.
///
/// **오너가 확정한 규칙인데 문구가 없었다.** A 문서 3절에 추가하고 여기서 쓴다
/// (2026-08-05, 오너 검토 대상). 창작이 아니라 **문서에 먼저 넣고 가져오는** 순서를 지켰다.
///
/// 막는 화면이 아니라 **이미 기록됐다고 알리는** 화면이다. 그래서 제목이 부정형이 아니고,
/// 대안(장소를 옮기면 된다)을 같이 준다.
struct DuplicateTodayView: View {
    let flower: Flower
    let onGoToCodex: () -> Void
    let onRetake: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            FlowerSymbol(flower: flower)
                .frame(width: 116, height: 116)

            Text("오늘 여기서 만난 꽃이에요")
                .font(Theme.Typo.screenTitle)
                .foregroundStyle(Theme.Palette.textPrimary)
                .padding(.top, 24)

            Text("같은 자리에서 같은 꽃은 하루에 한 번 기록해요.")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, 12)

            Text("장소를 옮기면 다시 기록할 수 있어요.")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, 4)

            Spacer()

            VStack(spacing: 10) {
                PrimaryButton(title: "도감에서 보기", action: onGoToCodex)
                GhostButton(title: "다른 꽃 찍기", action: onRetake)
            }
        }
        .padding(.horizontal, Theme.Metric.screenPadding)
        .padding(.vertical, 28)
        .background(Theme.Palette.background)
    }
}
