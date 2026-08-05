import AVFoundation
import SwiftUI

/// 화면 03 권한 안내. 문구는 A 문서 03번 표가 전부다.
///
/// **왜 별도 화면인가.** iOS 권한 대화상자는 **한 번 거부하면 다시 뜨지 않는다.**
/// 아무 설명 없이 시스템 대화상자를 먼저 띄우면 사용자는 뭘 묻는지 모르는 채로 거부하고,
/// 그 뒤로는 설정 앱까지 들어가야 복구된다. 먼저 왜 필요한지 보여주고 묻는다.
///
/// 와이어프레임 주석: `카메라 = 필수, 토글 고정 ON`. 토글을 그리지 않은 이유는
/// **끌 수 없는 스위치는 스위치가 아니기 때문이다** — 눌러도 안 움직이면 고장으로 읽힌다.
/// `필수`/`선택` 배지로 같은 정보를 준다.
struct PermissionIntroView: View {

    /// 세 권한을 다 물어본 뒤 호출된다. **거부해도 호출된다** —
    /// `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`가 약속이다.
    let onFinish: () -> Void

    @Environment(AppSession.self) private var session
    /// 권한 요청 중에는 CTA를 막는다. 연달아 누르면 대화상자가 겹친다.
    @State private var isRequesting = false

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("이 세 가지만 허용하면 준비 끝!")
                        .font(Theme.Typo.hero)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요.")
                        .font(Theme.Typo.body)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(spacing: 12) {
                        ForEach(PermissionIntroItem.all) { item in
                            PermissionIntroRow(item: item)
                        }
                    }

                    assuranceBox
                }
                .padding(.horizontal, Theme.Metric.screenPadding)
                .padding(.top, 20)
                .padding(.bottom, 24)
            }

            footer
        }
        .background(Theme.Palette.background)
    }

    // MARK: - 헤더 `권한 안내` + `2/2`

    private var header: some View {
        HStack {
            Text("권한 안내")
                .font(Theme.Typo.screenTitle)
                .foregroundStyle(Theme.Palette.textPrimary)
            Spacer()
            // 온보딩 2단계 중 2번째. 화면 02(지역선택)는 카카오맵 대기라
            // 지금은 여기가 첫 화면이지만 **표기는 스펙대로 둔다** —
            // 02가 붙을 때 이 숫자를 다시 손대지 않게.
            Text("2/2")
                .font(Theme.Typo.body)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .padding(.horizontal, Theme.Metric.screenPadding)
        .padding(.vertical, 14)
        .background(Theme.Palette.surface)
        .overlay(Theme.Palette.border.frame(height: 0.5), alignment: .bottom)
    }

    // MARK: - 안심 박스

    /// 와이어프레임 주석: `'자동 공개가 아니다'를 가입 단계에서 못 박는다.`
    /// 지도에 꽃 위치가 올라가는 앱이라, 이 약속이 없으면 촬영 자체를 망설인다.
    private var assuranceBox: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(
                [
                    "촬영한 사진은 내 도감에만 저장됩니다.",
                    "지도 공유는 매번 직접 선택해요.",
                ],
                id: \.self
            ) { line in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Palette.success)
                        .accessibilityHidden(true)
                    Text(line)
                        .font(Theme.Typo.body)
                        .foregroundStyle(Theme.Palette.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Palette.surfaceAlt)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
    }

    // MARK: - CTA

    private var footer: some View {
        VStack(spacing: 10) {
            PrimaryButton(
                title: "허용하고 시작하기",
                isEnabled: !isRequesting
            ) {
                Task { await requestAll() }
            }
            Text("나중에 설정에서 바꿀 수 있어요")
                .font(Theme.Typo.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
        }
        .padding(.horizontal, Theme.Metric.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(
            Theme.Palette.surface
                .overlay(Theme.Palette.border.frame(height: 0.5), alignment: .top)
        )
    }

    /// 권한을 **차례로** 묻는다.
    ///
    /// **동시에 부르면 안 된다.** iOS는 권한 대화상자를 하나만 띄우고, 겹친 요청은
    /// 조용히 버린다 — 카메라만 묻고 위치는 아예 못 물어보게 된다.
    /// 그래서 각 요청이 답을 받을 때까지 기다린다.
    ///
    /// **거부해도 다음으로 넘어간다.** 필수는 카메라지만, 여기서 막아 세우면
    /// 도감(04)조차 못 보게 된다. 촬영 진입 시점에 권한 시트로 다시 안내한다.
    ///
    /// **연락처는 여기서 요청하지 않는다** — 화면에는 설명이 남아 있다(스펙 그대로).
    /// 이유는 아래 `requestContactsWhenFriendsExist` 주석에 적었다.
    private func requestAll() async {
        isRequesting = true
        // **전체에 상한을 둔다.** 시스템 권한 호출이 안 돌아오면 사용자는
        // 비활성 버튼만 남은 화면에 갇힌다. 실제로 연락처 요청에서 그랬다.
        // 상한이 지나면 그냥 도감으로 보낸다 — 대화상자는 위에 그대로 떠 있고,
        // 늦게 답해도 권한은 정상 반영된다.
        await Self.withTimeLimit(.seconds(60)) {
            // 카메라 — 게임 규칙의 근간(기획서 5장).
            _ = await AVCaptureDevice.requestAccess(for: .video)
            // 위치 — 없어도 도감 등록은 된다.
            await session.location.requestAuthorizationAndWait()
        }
        isRequesting = false
        onFinish()
    }

    /// **연락처를 지금 묻지 않는 이유.**
    ///
    /// 1. 쓰는 코드가 없다. 친구 연결(화면 19)이 아직 없어서 권한을 받아도 할 일이 없다.
    ///    쓰지 않는 권한을 미리 받는 앱은 심사에서도 지적된다.
    /// 2. **실제로 여기서 멈췄다.** 시뮬레이터에서 `CNContactStore.requestAccess`가
    ///    대화상자도 없이 돌아오지 않았고(요소 계층에 아무것도 없었다),
    ///    CTA가 비활성인 채로 온보딩이 끝나지 않았다. 실기기에서 같은지는 모른다 —
    ///    **모르는 위험을 필수 경로에 둘 이유가 없다.**
    ///
    /// 화면 19를 만들 때 **그 화면에서** 묻는다. 그때가 사용자에게도 더 이해되는 시점이다
    /// (`지인을 찾아볼까요?` → 대화상자). 화면 03의 연락처 설명은 스펙대로 남겨 둔다 —
    /// 앞으로 무엇에 쓰는지 미리 알리는 것이 그 칸의 목적이다.
    static let requestContactsWhenFriendsExist = "화면 19에서 요청"

    /// 주어진 시간 안에 안 끝나면 그냥 반환한다.
    ///
    /// 시간이 지나도 **작업을 죽이지는 못한다** — 권한 요청은 취소에 반응하지 않는다.
    /// 목적은 작업 중단이 아니라 **화면을 붙잡아 두지 않는 것**이다.
    @MainActor
    private static func withTimeLimit(
        _ limit: Duration,
        _ operation: @escaping @MainActor () async -> Void
    ) async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await operation() }
            group.addTask { try? await Task.sleep(for: limit) }
            await group.next()
            group.cancelAll()
        }
    }
}

// MARK: - 권한 3종 행

/// A 문서 03번 표의 세 줄. **`String`을 받지 않는다** — 문구가 창작되는 걸 막는다.
struct PermissionIntroItem: Identifiable {
    let id: String
    let iconName: String
    let title: String
    let isRequired: Bool
    let purpose: String
    let caveat: String

    static let all: [PermissionIntroItem] = [
        PermissionIntroItem(
            id: "camera",
            iconName: "camera.fill",
            title: "카메라",
            isRequired: true,
            purpose: "꽃을 직접 촬영해 도감에 등록합니다.",
            caveat: "앨범 사진은 등록할 수 없어요."
        ),
        PermissionIntroItem(
            id: "location",
            iconName: "location.fill",
            title: "위치",
            isRequired: false,
            purpose: "꽃을 발견한 장소를 지도에 남깁니다.",
            caveat: "끄면 지도 공유를 쓸 수 없어요."
        ),
        PermissionIntroItem(
            id: "contacts",
            iconName: "person.2.fill",
            title: "연락처",
            isRequired: false,
            purpose: "이미 가입한 지인을 친구로 연결합니다.",
            caveat: "번호는 암호화해 보관하며 저장하지 않아요."
        ),
    ]
}

private struct PermissionIntroRow: View {
    let item: PermissionIntroItem

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.iconName)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Theme.Palette.primary)
                .frame(width: 28, height: 28)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(item.title)
                        .font(Theme.Typo.sectionTitle)
                        .foregroundStyle(Theme.Palette.textPrimary)
                    RequirementBadge(isRequired: item.isRequired)
                }
                Text(item.purpose)
                    .font(Theme.Typo.body)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(item.caveat)
                    .font(Theme.Typo.caption)
                    .foregroundStyle(Theme.Palette.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Theme.Palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Metric.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Metric.cardRadius)
                .stroke(Theme.Palette.border, lineWidth: 1)
        )
        // 세 줄을 한 덩어리로 읽어준다 — 따로 읽으면 어느 권한 설명인지 알 수 없다.
        .accessibilityElement(children: .combine)
    }
}

/// `필수` / `선택` 배지. **색만으로 구분하지 않는다** — 글자가 같은 말을 한다.
private struct RequirementBadge: View {
    let isRequired: Bool

    var body: some View {
        Text(isRequired ? "필수" : "선택")
            .font(Theme.Typo.minimum)
            .foregroundStyle(isRequired ? .white : Theme.Palette.textSecondary)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(
                isRequired ? Theme.Palette.primary : Theme.Palette.surfaceAlt,
                in: Capsule()
            )
    }
}

#Preview("03 권한 안내") {
    PermissionIntroPreview()
}
