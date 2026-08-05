import SwiftUI

/// B-3 어뷰징 가드 ① — 희귀종을 **낮은 순위에서** 골랐을 때 사진을 한 장 더 받는다.
///
/// **무엇을 막는가.** 후보 3개를 보여주는 방식(B-3 ③)에는 구멍이 있다 —
/// 3순위에 `귀함` 종이 있으면 그걸 고르는 사람이 생긴다. 점수가 낮은데도
/// 희귀종이 등록되면 랭킹이 무의미해진다.
///
/// **문구가 가장 어려운 화면이다.** 실제로 귀한 꽃을 만난 사람이 대다수이고
/// 어뷰징하는 쪽은 소수다. 의심하는 말투를 쓰면 **정직한 다수를 범인 취급**한다.
/// 그래서 `확인이 필요합니다`가 아니라 `귀한 꽃이네요!`로 시작한다 —
/// 검증이 아니라 **대접**으로 읽히게. (A 문서 3절에 추가, 오너 검토 대상)
struct RareFlowerExtraPhotoView: View {
    let flower: Flower
    /// 한 장 더 찍으러 간다 (화면 07).
    let onRetake: () -> Void
    /// 후보 선택(화면 09)으로 돌아간다. **잘못 골랐을 수도 있다.**
    let onReselect: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            FlowerSymbol(flower: flower)
                .frame(width: 116, height: 116)

            Text("귀한 꽃이네요!")
                .font(Theme.Typo.hero)
                .foregroundStyle(Theme.Palette.textPrimary)
                .padding(.top, 24)

            Text("\(flower.name)은 보기 드문 꽃이라 사진을 한 장 더 받아요.")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 12)

            Text("조금 다른 각도에서 찍어 주시면 좋아요.")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)

            Spacer()

            VStack(spacing: 10) {
                PrimaryButton(title: "한 장 더 찍기", action: onRetake)
                // **`나중에`를 두지 않았다.** 여기서 빠져나가면 등록이 안 된 채로
                // 끝나는데, 그건 사용자가 원한 결과가 아니다.
                GhostButton(title: "다시 고르기", action: onReselect)
            }
        }
        .padding(.horizontal, Theme.Metric.screenPadding)
        .padding(.vertical, 28)
        .background(Theme.Palette.background)
    }
}

#Preview("B-3 가드 ① 희귀종 추가 확인") {
    let session = AppSession.preview(discoveryCount: 0)
    let rare = session.repository.flowers.first { $0.rarity == .rare }
        ?? session.repository.flowers[0]
    return RareFlowerExtraPhotoView(flower: rare, onRetake: {}, onReselect: {})
}
