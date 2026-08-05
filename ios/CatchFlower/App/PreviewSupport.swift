import SwiftUI

/// 프리뷰용 세션 조립기.
///
/// 화면 대부분이 `AppSession`을 `@Environment`로 받는다. 프리뷰마다 손으로
/// 세션을 만들면 `discoveries`를 채우는 코드가 열 군데로 복제된다.
struct RootTabPreview: View {
    var discoveryCount: Int = 12
    var month: Int?

    var body: some View {
        let session = AppSession.preview(discoveryCount: discoveryCount, month: month)
        RootTabView()
            .environment(session)
            .preferredColorScheme(.light)
    }
}

/// 화면 03 프리뷰. 권한 요청은 프리뷰에서 실제로 뜨지 않는다 — 배치만 본다.
struct PermissionIntroPreview: View {
    var body: some View {
        PermissionIntroView {}
            .environment(AppSession.preview(discoveryCount: 0))
            .preferredColorScheme(.light)
    }
}

extension AppSession {
    /// 지금 달에 피는 꽃 앞쪽부터 `count`종을 발견한 상태로 만든다.
    ///
    /// **개화월에 맞는 꽃으로 채운다.** 아무 꽃이나 넣으면 1월 프리뷰에 벚꽃이 뜨고,
    /// 그러면 화면이 잘못됐는지 데이터가 잘못됐는지 구분할 수 없다.
    static func preview(discoveryCount: Int = 12, month: Int? = nil) -> AppSession {
        let session = AppSession(currentMonth: month)
        let pool = session.repository.flowersBlooming(inMonth: session.currentMonth)
        let source = pool.isEmpty ? session.repository.flowers : pool

        let places = ["서울숲", "연남동 경의선숲길", "남산 산책로"]
        for (index, flower) in source.prefix(discoveryCount).enumerated() {
            // 같은 종을 두 번 찍은 경우도 섞는다 — 재발견 표시(`2회`)를 검증해야 한다.
            let repeats = index % 4 == 0 ? 2 : 1
            for r in 0..<repeats {
                session.record(
                    Discovery(
                        id: UUID(),
                        userID: session.userID,
                        flowerID: flower.id,
                        photoURL: nil,
                        lat: 37.5443,
                        lng: 127.0557 + Double(index) * 0.01,
                        placeName: places[(index + r) % places.count],
                        dongCode: nil,
                        guCode: nil,
                        visibility: index % 3 == 0 ? .private : .public,
                        aiConfidence: 0.82,
                        aiPickedRank: 1,
                        isFirstDiscovery: r == 0,
                        createdAt: .now,
                        capturedAt: .now.addingTimeInterval(-Double(index) * 86_400),
                        note: nil
                    )
                )
            }
        }
        return session
    }
}
