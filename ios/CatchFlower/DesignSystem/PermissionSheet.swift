import SwiftUI
import UIKit

/// 권한 재요청 시트. **A 문서 3절 표 3종.**
///
/// **왜 필요한가.** iOS 권한 대화상자는 **한 번 거부하면 다시 뜨지 않는다.**
/// 그 상태에서 우리가 아무 안내도 안 하면 `CameraView`는 그냥 검은 화면이고,
/// 사용자는 앱이 고장 난 줄 안다. 설정 앱으로 보내주는 게 유일한 복구 경로다.
enum PermissionKind: Equatable, Sendable {
    case camera
    case location
    case contacts

    var title: String {
        switch self {
        case .camera: return "꽃을 촬영하려면 카메라 권한이 필요해요"
        case .location: return "지도에 남기려면 위치 권한이 필요해요"
        case .contacts: return "지인을 찾으려면 연락처 권한이 필요해요"
        }
    }

    var message: String {
        switch self {
        case .camera: return "설정에서 카메라를 켜주세요"
        case .location: return "도감 등록은 그대로 할 수 있어요"
        case .contacts: return "번호는 저장하지 않아요"
        }
    }

    /// **카메라만 필수다.** 위치·연락처 없이도 도감은 채울 수 있고,
    /// A 문서 문구가 그렇게 약속했다(`도감 등록은 그대로 할 수 있어요`).
    /// 필수가 아니면 시트를 닫고 계속 쓸 수 있어야 한다.
    var isRequired: Bool {
        self == .camera
    }

    /// 보조 버튼. A 문서는 카메라에만 버튼을 적어뒀다(`설정으로 이동` / `나중에`).
    /// 나머지 둘은 필수가 아니라서 **거부한 채로 계속 쓰는 선택**을 줘야 한다.
    var dismissTitle: String {
        switch self {
        case .camera: return "나중에"
        case .location: return "위치 없이 계속하기"
        case .contacts: return "나중에"
        }
    }
}

/// 권한 안내 시트 본문.
struct PermissionSheet: View {
    let kind: PermissionKind
    let onDismiss: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)

            Image(systemName: iconName)
                .font(.system(size: 44, weight: .regular))
                .foregroundStyle(Theme.Palette.primary)
                .padding(.bottom, 20)
                // 아이콘은 장식이다 — 제목이 같은 말을 한다.
                .accessibilityHidden(true)

            Text(kind.title)
                .font(Theme.Typo.sectionTitle)
                .foregroundStyle(Theme.Palette.textPrimary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 8)

            Text(kind.message)
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 28)

            PrimaryButton(title: "설정으로 이동") {
                // **설정 앱의 이 앱 화면으로 직접 보낸다.** 설정 최상단으로 보내면
                // 타깃 사용자가 앱을 찾아 들어가야 한다 — 거기서 대부분 포기한다.
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    openURL(url)
                }
            }
            .padding(.bottom, 10)

            GhostButton(title: kind.dismissTitle, action: onDismiss)
        }
        .padding(.horizontal, Theme.Metric.screenPadding)
        .padding(.bottom, 20)
        .background(Theme.Palette.background)
    }

    private var iconName: String {
        switch kind {
        case .camera: return "camera.fill"
        case .location: return "location.fill"
        case .contacts: return "person.2.fill"
        }
    }
}

extension View {
    /// 권한 시트를 붙인다.
    ///
    /// 필수 권한(카메라)은 **스와이프로 못 닫게 한다** — 닫아도 할 수 있는 게 없는
    /// 화면으로 돌아가서 또 막힌다. 대신 `나중에` 버튼으로 명시적으로 나가게 한다.
    func permissionSheet(
        _ kind: PermissionKind,
        isPresented: Binding<Bool>,
        onDismiss: @escaping () -> Void = {}
    ) -> some View {
        sheet(isPresented: isPresented) {
            PermissionSheet(kind: kind) {
                isPresented.wrappedValue = false
                onDismiss()
            }
            .presentationDetents([.height(340)])
            .interactiveDismissDisabled(kind.isRequired)
        }
    }
}
