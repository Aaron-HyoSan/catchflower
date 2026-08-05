import AVFoundation
import SwiftUI
import UIKit

/// 화면 07 카메라. 어두운 화면. 문구는 A 문서 07번.
///
/// **앨범 선택은 만들지 않는다.** 기획서·권한 문구가 `앨범 사진은 등록할 수 없어요`라고
/// 약속했다. 앨범을 열면 남의 사진으로 도감을 채울 수 있고, 그게 이 게임의 전제를 깬다.
///
/// **시뮬레이터에는 카메라가 없다.** 그래서 `#if targetEnvironment(simulator)`로
/// 픽스처 버튼을 대신 띄운다 — 없으면 화면 08~13을 아예 검증할 수 없다.
struct CameraView: View {
    let onCapture: (CapturedPhoto) -> Void
    let onClose: () -> Void

    @State private var camera = CameraController()
    @State private var showsHelp = false

    /// 권한 시트는 **카메라 상태에서 파생된다.** 별도 `Bool`을 두면
    /// 상태가 둘로 갈려서 어긋난다(거부됐는데 시트가 닫혀 있는 상태).
    private var showsCameraPermission: Binding<Bool> {
        Binding(get: { camera.isDenied }, set: { _ in })
    }

    var body: some View {
        ZStack {
            Theme.Palette.cameraBackground.ignoresSafeArea()

            #if targetEnvironment(simulator)
            simulatorFixtures
            #else
            CameraPreview(controller: camera)
                .ignoresSafeArea()
            #endif

            VStack(spacing: 0) {
                topBar
                Spacer()
                guideFrame
                Spacer()
                bottomBar
            }
        }
        .task {
            #if !targetEnvironment(simulator)
            await camera.start()
            #endif
        }
        .onDisappear { camera.stop() }
        // **권한이 거부되면 안내한다.** `camera.isDenied`는 원래도 있었는데
        // 아무도 안 봤다 — 그래서 권한을 거부하면 **검은 화면**만 남았다.
        // iOS 권한 대화상자는 한 번 거부하면 다시 뜨지 않으므로
        // 설정 앱으로 보내는 게 유일한 복구 경로다.
        .permissionSheet(.camera, isPresented: showsCameraPermission) {
            // `나중에`를 누르면 촬영 화면에 머물 이유가 없다. 도감으로 되돌린다.
            onClose()
        }
        .alert("이렇게 찍으면 잘 알아봐요", isPresented: $showsHelp) {
            Button("확인") {}
        } message: {
            // A 문서 12번의 팁 4개를 그대로 쓴다.
            Text(
                """
                꽃 한 송이가 화면에 꽉 차게
                그림자 없는 밝은 곳에서
                정면이나 살짝 위에서
                흔들리지 않게 잠시 멈춰서
                """
            )
        }
    }

    private var topBar: some View {
        HStack {
            Button(action: onClose) {
                Text("닫기")
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(.white)
                    .frame(minWidth: Theme.Metric.minTouchTarget,
                           minHeight: Theme.Metric.minTouchTarget)
            }
            Spacer()
            Text("꽃 촬영")
                .font(Theme.Typo.screenTitle)
                .foregroundStyle(.white)
            Spacer()
            Button { showsHelp = true } label: {
                Text("도움말")
                    .font(Theme.Typo.bodyBold)
                    .foregroundStyle(.white)
                    .frame(minWidth: Theme.Metric.minTouchTarget,
                           minHeight: Theme.Metric.minTouchTarget)
            }
        }
        .padding(.horizontal, 12)
    }

    private var guideFrame: some View {
        VStack(spacing: 12) {
            Text("꽃 한 송이를 네모 안에 꽉 채워 주세요")
                .font(Theme.Typo.bodyBold)
                .foregroundStyle(.white)

            RoundedRectangle(cornerRadius: 20)
                .stroke(.white.opacity(0.9), lineWidth: 3)
                .frame(width: 260, height: 260)

            Text("너무 멀면 잘 못 알아봐요")
                .font(Theme.Typo.body)
                .foregroundStyle(.white.opacity(0.85))
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 14) {
            Text("앨범 사진은 등록할 수 없어요. 직접 찍어 주세요.")
                .font(Theme.Typo.caption)
                .foregroundStyle(.white.opacity(0.8))

            HStack {
                // 보조 버튼도 텍스트를 병기한다 (아이콘 단독 금지).
                CameraSideButton(
                    title: "플래시",
                    systemImage: camera.isFlashOn ? "bolt.fill" : "bolt.slash"
                ) {
                    camera.isFlashOn.toggle()
                }

                Spacer()

                Button { capture() } label: {
                    VStack(spacing: 4) {
                        Circle()
                            .fill(.white)
                            .frame(width: 72, height: 72)
                            .overlay(Circle().stroke(.white.opacity(0.5), lineWidth: 4).padding(-6))
                        Text("찍기")
                            .font(Theme.Typo.bodyBold)
                            .foregroundStyle(.white)
                    }
                }
                .accessibilityLabel("찍기")

                Spacer()

                CameraSideButton(title: "전환", systemImage: "arrow.triangle.2.circlepath") {
                    camera.switchPosition()
                }
            }
            .padding(.horizontal, 24)
        }
        .padding(.bottom, 24)
    }

    private func capture() {
        #if targetEnvironment(simulator)
        onCapture(FixtureShot.confident.photo())
        #else
        Task {
            guard let data = await camera.capturePhoto() else { return }
            onCapture(
                CapturedPhoto(
                    data: data,
                    fileName: nil,
                    capturedAt: Date(),
                    lat: nil,
                    lng: nil,
                    placeName: nil
                )
            )
        }
        #endif
    }

    /// 시뮬레이터 전용. Mock 3경로를 손으로 밟게 해준다 (계약 2절 표).
    private var simulatorFixtures: some View {
        VStack(spacing: 10) {
            Text("시뮬레이터 · 카메라 없음")
                .font(Theme.Typo.caption)
                .foregroundStyle(.white.opacity(0.6))
            ForEach(FixtureShot.allCases, id: \.self) { fixture in
                Button {
                    onCapture(fixture.photo())
                } label: {
                    Text(fixture.label)
                        .font(Theme.Typo.buttonSmall)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.white.opacity(0.18), in: Capsule())
                }
            }
        }
        .offset(y: -40)
    }
}

/// Mock 판정용 픽스처. 파일명이 시나리오를 가른다 — 계약이 정한 방식 그대로다.
///
/// **`confident`가 난이도 `low`인 꽃을 요구하는 이유.** Mock 1순위 점수는 0.82로 고정인데
/// 임계값은 꽃 난이도로 갈린다(하 60·중 70·상 85). 후보 1순위가 `high`인 꽃이면
/// 0.82로도 애매 경로가 된다. 지금이 8월이면 첫 후보가 서양민들레(`high`)라서
/// "확정 경로" 버튼이 애매 화면으로 갔다 — 난이도를 지정해야 두 경로를 다 밟을 수 있다.
enum FixtureShot: String, CaseIterable, Sendable {
    case confident = "fixture_confident.jpg"
    case low = "fixture_low.jpg"
    case fail = "fixture_fail.jpg"
    /// 확정 경로 + **다른 장소**.
    ///
    /// **B-5를 붙이고 나서 필요해졌다.** 같은 종을 같은 장소에서 같은 날 두 번 찍으면
    /// 이제 정상적으로 막힌다. 그래서 **재발견 화면(11)은 장소를 옮겨야만 볼 수 있다** —
    /// 규칙이 맞게 동작하는 것이고, 이 픽스처가 그 경로를 밟게 해준다.
    case confidentElsewhere = "fixture_confident_elsewhere.jpg"
    /// 애매 경로 + **2순위가 희귀종**. B-3 가드 ①을 밟는 유일한 통로다.
    ///
    /// 파일명에 `low`가 들어가야 Mock이 애매 시나리오를 고른다(계약 표) —
    /// 후보 3개가 같은 크기로 놓여야 2순위를 **직접 고르는 행위**를 재현할 수 있다.
    case rareSecondLow = "fixture_low_rare2.jpg"

    var label: String {
        switch self {
        case .confident: return "찍기 (확정 · 0.82)"
        case .low: return "찍기 (애매 · 0.55)"
        case .fail: return "찍기 (실패)"
        case .confidentElsewhere: return "찍기 (확정 · 다른 장소)"
        case .rareSecondLow: return "찍기 (애매 · 2순위 희귀종)"
        }
    }

    /// 확정 경로를 밟으려면 임계값이 낮은 꽃(`하` 60)이 1순위여야 한다.
    private var preferredDifficulty: AIDifficulty? {
        switch self {
        case .confident, .confidentElsewhere: return .low
        case .low, .fail, .rareSecondLow: return nil
        }
    }

    /// B-3 가드 ① 경로. 희귀종을 2순위 자리에 놓는다.
    private var placesRareCandidateSecond: Bool { self == .rareSecondLow }

    /// `confidentElsewhere`만 다른 좌표를 쓴다. B-5가 "같은 장소"로 보지 않을 만큼
    /// 떨어져 있어야 한다(반올림 4자리 ≈ 11m 기준).
    private var coordinate: (lat: Double, lng: Double, place: String) {
        switch self {
        case .confidentElsewhere:
            return (37.5665, 126.9780, "서울광장")
        default:
            // 문구 스펙 13번의 예시 장소. 카카오 로컬(A-3)이 붙으면 실제 값으로 바뀐다.
            return (37.5443, 127.0557, "서울숲")
        }
    }

    func photo(at date: Date = Date()) -> CapturedPhoto {
        CapturedPhoto(
            data: Data(),
            fileName: rawValue,
            capturedAt: date,
            lat: coordinate.lat,
            lng: coordinate.lng,
            placeName: coordinate.place,
            preferredDifficulty: preferredDifficulty,
            placesRareCandidateSecond: placesRareCandidateSecond
        )
    }
}

private struct CameraSideButton: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: systemImage)
                    .font(.system(size: 20, weight: .medium))
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(.white)
            .frame(minWidth: 56, minHeight: Theme.Metric.minTouchTarget)
        }
        .accessibilityLabel(title)
    }
}
