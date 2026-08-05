import SwiftUI

@main
struct CatchFlowerApp: App {
    /// 도감 200종은 앱 전체가 공유한다. 번들 적재라 실패하면 즉시 죽는다.
    @State private var session = AppSession(
        launchOptions: LaunchOptions(arguments: ProcessInfo.processInfo.arguments)
    )
    /// 토스트는 화면 전환과 무관하게 살아 있어야 한다 —
    /// 촬영 모달이 닫힌 **뒤에** 도감 위에 떠야 하므로 최상단에서 소유한다.
    @State private var toasts = ToastCenter()

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .environment(session)
                .environment(toasts)
                // E-3: 다크 모드 미대응. 시스템 설정과 무관하게 라이트로 고정한다.
                .preferredColorScheme(.light)
                // 저장된 도감을 읽어 온다. 이게 없으면 앱을 끌 때마다 도감이 비워진다.
                .task { await session.loadPersisted() }
        }
    }
}
