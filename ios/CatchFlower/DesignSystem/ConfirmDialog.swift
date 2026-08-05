import SwiftUI

/// 확인 다이얼로그. **A 문서 3절 표 3종이 전부다.**
///
/// 문구를 `case`로 못 박는 이유는 `ToastMessage`와 같다 — 호출부가 문자열을 넘기면
/// 문구가 창작된다. 새로 필요하면 **A 문서에 먼저 추가한다.**
enum ConfirmDialogKind: Equatable, Sendable {
    /// 촬영 중 이탈.
    case leaveCapture
    /// 기록 삭제.
    case deleteDiscovery
    /// 공유 취소(지도에서 내리기).
    case unshare

    var title: String {
        switch self {
        case .leaveCapture: return "촬영을 그만할까요?"
        case .deleteDiscovery: return "이 발견 기록을 지울까요?"
        case .unshare: return "지도에서 내릴까요?"
        }
    }

    var message: String {
        switch self {
        case .leaveCapture: return "찍은 사진은 저장되지 않아요"
        case .deleteDiscovery: return "지우면 되돌릴 수 없어요"
        case .unshare: return "도감 기록은 그대로 남아요"
        }
    }

    /// 진행 버튼.
    var confirmTitle: String {
        switch self {
        case .leaveCapture: return "그만하기"
        case .deleteDiscovery: return "지우기"
        case .unshare: return "내리기"
        }
    }

    /// 취소 버튼. **다이얼로그마다 문구가 다르다** —
    /// 촬영 중 이탈은 `취소`가 아니라 `계속 찍기`다(무엇이 취소되는지 헷갈리지 않게).
    var cancelTitle: String {
        switch self {
        case .leaveCapture: return "계속 찍기"
        case .deleteDiscovery, .unshare: return "취소"
        }
    }

    /// 되돌릴 수 없는 동작만 빨간 글씨를 쓴다.
    /// 전부 빨갛게 하면 경고가 경고로 안 읽힌다.
    var isDestructive: Bool {
        self == .deleteDiscovery
    }
}

extension View {
    /// 확인 다이얼로그를 붙인다.
    ///
    /// **`confirmationDialog`이 아니라 `alert`을 쓴다.** 처음엔 아래에서 올라오는
    /// 시트가 엄지에 가까워 낫다고 보고 `confirmationDialog`을 썼는데, 실제로 띄워 보니
    /// **취소 버튼이 아예 렌더링되지 않았다**(요소 계층에 `지우기` 하나만 있었다).
    /// 나가는 길이 "바깥을 탭"뿐이면 **제스처 전용 기능**이 되고, 그건 타깃 제약 위반이다 —
    /// 스와이프·바깥탭을 모르는 사용자는 파괴적 동작 앞에서 갇힌다.
    ///
    /// `alert`은 두 버튼을 항상 그린다. 취소는 `.cancel` 역할이라
    /// **바깥 탭·Esc로도 취소로 처리**되고, 버튼으로도 나갈 수 있다.
    func confirmDialog(
        _ kind: ConfirmDialogKind,
        isPresented: Binding<Bool>,
        onConfirm: @escaping () -> Void
    ) -> some View {
        alert(kind.title, isPresented: isPresented) {
            Button(
                kind.confirmTitle,
                role: kind.isDestructive ? .destructive : nil,
                action: onConfirm
            )
            Button(kind.cancelTitle, role: .cancel) {}
        } message: {
            Text(kind.message)
        }
    }
}
