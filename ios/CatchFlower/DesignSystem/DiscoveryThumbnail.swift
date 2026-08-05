import SwiftUI

/// 발견 기록 사진 1장. 화면 05·10·11이 같은 걸 쓴다.
///
/// **사진이 없을 수 있다.** 시뮬레이터 픽스처(데이터 없음), 위치·사진 없이 저장된 옛 기록,
/// 파일이 지워진 경우다. 그때 빈 칸을 두면 "사진이 안 뜨는 버그"로 보이므로
/// 회색 자리표시를 그린다 — **의도된 빈 칸임을 눈에 보이게** 한다.
struct DiscoveryThumbnail: View {
    let discovery: Discovery
    var side: CGFloat = 72

    @Environment(AppSession.self) private var session

    var body: some View {
        Group {
            if let fileName = discovery.photoURL,
               let image = session.photos.image(for: fileName) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Theme.Palette.surfaceAlt
                    .overlay(
                        Image(systemName: "photo")
                            .foregroundStyle(Theme.Palette.textTertiary)
                    )
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
