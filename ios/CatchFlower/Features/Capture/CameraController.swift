import AVFoundation
import SwiftUI
import UIKit

/// 카메라 세션. 화면 07 전용.
///
/// **압축은 여기서 한다.** 장변 `GamePolicy.photoLongEdgePixels`(1600px)로 줄여
/// 1장만 넘긴다 (A-4). 원본을 들고 다니면 업로드 비용과 메모리가 같이 튄다.
@MainActor
@Observable
final class CameraController {

    private let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private var input: AVCaptureDeviceInput?
    private var position: AVCaptureDevice.Position = .back

    var isFlashOn = false
    var isRunning = false
    /// 권한 거부. 화면 07에서 안내로 바꿔야 한다 (A 문서 3절 권한 문구).
    var isDenied = false

    var captureSession: AVCaptureSession { session }

    func start() async {
        guard !isRunning else { return }

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            break
        case .notDetermined:
            guard await AVCaptureDevice.requestAccess(for: .video) else {
                isDenied = true
                return
            }
        default:
            isDenied = true
            return
        }

        configure()
        // `startRunning()`은 몇백 ms 메인 스레드를 막는다. 별 스레드로 보낸다.
        // `AVCaptureSession`은 Sendable이 아니라서 박스로 감싸 넘긴다 — 실제로는
        // 이 객체를 만든 곳이 여기뿐이고 세션 API 자체가 스레드 안전하다.
        let box = UncheckedBox(session)
        await Task.detached { box.value.startRunning() }.value
        isRunning = true
    }

    func stop() {
        guard isRunning else { return }
        let box = UncheckedBox(session)
        Task.detached { box.value.stopRunning() }
        isRunning = false
    }

    func switchPosition() {
        position = position == .back ? .front : .back
        session.beginConfiguration()
        if let input { session.removeInput(input) }
        attachInput()
        session.commitConfiguration()
    }

    private func configure() {
        session.beginConfiguration()
        session.sessionPreset = .photo
        attachInput()
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
    }

    private func attachInput() {
        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: position
        ), let newInput = try? AVCaptureDeviceInput(device: device) else { return }

        if session.canAddInput(newInput) {
            session.addInput(newInput)
            input = newInput
        }
    }

    /// JPEG 데이터를 돌려준다. 실패하면 `nil` — 호출부가 화면을 넘기지 않는다.
    func capturePhoto() async -> Data? {
        var settings = AVCapturePhotoSettings()
        if output.availablePhotoCodecTypes.contains(.jpeg) {
            settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
        }
        if isFlashOn && output.supportedFlashModes.contains(.on) {
            settings.flashMode = .on
        }

        let raw = await withCheckedContinuation { continuation in
            let delegate = PhotoCaptureDelegate { data in
                continuation.resume(returning: data)
            }
            // 델리게이트는 output이 약하게 잡으므로 스스로 살아 있어야 한다.
            delegate.retainSelf()
            output.capturePhoto(with: settings, delegate: delegate)
        }

        guard let raw, let image = UIImage(data: raw) else { return nil }
        return image.downscaledJPEG(longEdge: CGFloat(GamePolicy.photoLongEdgePixels))
    }
}

/// Sendable이 아닌 값을 스레드 경계 밖으로 옮기기 위한 최소 상자.
/// **남용하면 strict concurrency를 끈 것과 같다** — 여기서는 스레드 안전이
/// 문서로 보장된 `AVCaptureSession`에만 쓴다.
private struct UncheckedBox<T>: @unchecked Sendable {
    let value: T
    init(_ value: T) { self.value = value }
}

/// `AVCapturePhotoCaptureDelegate`는 클래스여야 하고, output이 약참조로 들고 있다.
private final class PhotoCaptureDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let completion: (Data?) -> Void
    private var selfReference: PhotoCaptureDelegate?

    init(completion: @escaping (Data?) -> Void) {
        self.completion = completion
    }

    func retainSelf() { selfReference = self }

    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        completion(error == nil ? photo.fileDataRepresentation() : nil)
        selfReference = nil
    }
}

extension UIImage {
    /// 장변을 `longEdge`로 맞춰 줄이고 JPEG로 만든다. 이미 작으면 그대로 인코딩만 한다.
    func downscaledJPEG(longEdge: CGFloat, quality: CGFloat = 0.8) -> Data? {
        let maxSide = max(size.width, size.height)
        guard maxSide > longEdge else { return jpegData(compressionQuality: quality) }

        let scale = longEdge / maxSide
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let resized = renderer.image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: quality)
    }
}

/// 미리보기 레이어.
struct CameraPreview: UIViewRepresentable {
    let controller: CameraController

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.videoPreviewLayer.session = controller.captureSession
        view.videoPreviewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var videoPreviewLayer: AVCaptureVideoPreviewLayer {
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}
