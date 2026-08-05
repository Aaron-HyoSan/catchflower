import SwiftUI

/// 토스트 문구. **A 문서 3절 표 7종이 전부다.**
///
/// **왜 `String`을 안 받는가.** 호출부가 문자열을 넘기게 두면 문구가 코드 곳곳에서
/// 창작된다("저장 완료!", "저장했습니다" …). 문구는 A 문서가 원본이라는 규칙이
/// 지켜지는지 컴파일러가 확인해주는 쪽이 낫다. **새 문구가 필요하면
/// A 문서에 먼저 추가하고 여기 case를 늘린다.**
enum ToastMessage: Equatable, Sendable {
    /// 도감 등록 후 공유 안 함.
    case savedToCodex
    /// 지도 공유 완료.
    case sharedToMap
    /// 친구 추가 완료. `{이름}`이 들어간다.
    case becameFriends(name: String)
    case invitationSent
    case likeCancelled
    case reportReceived
    case networkError

    var text: String {
        switch self {
        case .savedToCodex: return "도감에는 저장됐어요"
        case .sharedToMap: return "지도에 공유했어요"
        case .becameFriends(let name): return "\(name)님과 친구가 되었어요"
        case .invitationSent: return "초대를 보냈어요"
        case .likeCancelled: return "좋아요를 취소했어요"
        case .reportReceived: return "신고를 접수했어요. 확인 후 처리됩니다."
        case .networkError: return "연결이 불안정해요. 잠시 후 다시 시도해 주세요."
        }
    }

    /// 실패 토스트는 색과 아이콘을 달리한다.
    /// **색만으로 구분하지 않는다** — 아이콘을 같이 바꾼다(색각 이상 대응).
    var isError: Bool {
        self == .networkError
    }

    var systemImage: String {
        isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill"
    }

    /// 표시 시간. 타깃(40~50대)은 읽는 속도를 여유 있게 잡는다 —
    /// iOS 기본 감각(2초)보다 길게 둔다. 긴 문구는 더 준다.
    var duration: Duration {
        text.count > 20 ? .seconds(4) : .seconds(2.6)
    }
}

/// 토스트를 화면 위에 겹쳐 보여준다.
///
/// **왜 필요했나.** 등록·공유가 성공해도 화면만 닫혀서 **성공 피드백이 없었다.**
/// 타깃 사용자는 "된 건가?" 하고 같은 동작을 다시 한다.
@MainActor
@Observable
final class ToastCenter {

    private(set) var current: ToastMessage?

    /// 표시 중인 토스트를 지우는 작업. 새 토스트가 오면 취소한다 —
    /// 안 그러면 먼저 뜬 토스트의 타이머가 **나중 토스트를 일찍 지운다.**
    private var dismissTask: Task<Void, Never>?

    func show(_ message: ToastMessage) {
        dismissTask?.cancel()
        current = message
        dismissTask = Task { [duration = message.duration] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            current = nil
        }
    }

    func dismiss() {
        dismissTask?.cancel()
        current = nil
    }
}

/// 토스트 한 장. 탭 바를 가리지 않게 위쪽에 띄운다.
struct ToastView: View {
    let message: ToastMessage

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: message.systemImage)
                .font(.system(size: 15, weight: .semibold))
            Text(message.text)
                // 본문 최소 굵기 Regular 이상. 토스트는 짧게 보이므로 semibold.
                .font(Theme.Typo.bodyBold)
                // 긴 문구(`연결이 불안정해요…`)가 두 줄이 될 수 있다. 자르지 않는다.
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .background(
            (message.isError ? Theme.Palette.error : Theme.Palette.textPrimary)
                .opacity(0.95),
            in: RoundedRectangle(cornerRadius: 12)
        )
        .padding(.horizontal, Theme.Metric.screenPadding)
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        // 스크린 리더는 자동으로 읽어야 한다 — 토스트는 사라지는 UI다.
        .accessibilityAddTraits(.isStaticText)
    }
}

extension View {
    /// 화면 최상단에 토스트를 얹는다. `RootTabView`에서 한 번만 붙인다.
    func toastOverlay(_ center: ToastCenter) -> some View {
        overlay(alignment: .top) {
            if let message = center.current {
                ToastView(message: message)
                    // 위에서 내려오게. 감속만 쓰고 튕기지 않는다.
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .padding(.top, 8)
                    // 토스트를 눌러서 바로 지울 수 있다 — 가려서 답답한 걸 막는다.
                    .onTapGesture { center.dismiss() }
            }
        }
        .animation(.easeOut(duration: 0.22), value: center.current)
    }
}
