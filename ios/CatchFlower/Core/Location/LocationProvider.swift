import CoreLocation
import Foundation

/// 촬영 위치를 잡는다.
///
/// **왜 필요한가.** 지금까지 `CoreLocation`을 아예 안 썼다. 그래서 실기기 촬영은
/// 좌표가 `nil`로 저장됐고, **B-5(같은 종+같은 장소 하루 1회)가 장소를 구분할 수 없었다.**
///
/// **권한이 없어도 촬영은 된다.** 권한 문구가 약속한 그대로다 —
/// `도감 등록은 위치 없이도 할 수 있어요`. 그래서 실패를 던지지 않고 `nil`을 돌려준다.
@MainActor
@Observable
final class LocationProvider {

    private let manager = CLLocationManager()
    private var pending: [CheckedContinuation<CLLocation?, Never>] = []
    /// 권한 응답을 기다리는 쪽(화면 03). 위치와 별도로 관리한다 —
    /// 권한을 허용해도 좌표는 몇 초 뒤에 온다.
    private var pendingAuthorization: [CheckedContinuation<Void, Never>] = []
    /// 델리게이트를 별도 객체로 뺀다. `CLLocationManagerDelegate`는 `@MainActor`가
    /// 아니어서, 이 클래스가 직접 채택하면 Swift 6 엄격 동시성에서 컴파일되지 않는다.
    private let delegate = LocationDelegate()

    private(set) var authorization: CLAuthorizationStatus

    /// 권한을 물어본 적이 없는 상태. 화면 03(권한 안내)을 띄울지 판단하는 값이다.
    var isUndetermined: Bool { authorization == .notDetermined }

    /// 거부됐는가. A 문서 3절의 권한 재요청 시트를 띄울 조건이다.
    var isDenied: Bool {
        authorization == .denied || authorization == .restricted
    }

    init() {
        authorization = manager.authorizationStatus
        // 꽃 사진의 장소다. 미터 단위 정확도가 필요 없고, 배터리를 덜 쓴다.
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.delegate = delegate
        delegate.onLocation = { [weak self] location in
            Task { @MainActor in self?.resume(with: location) }
        }
        delegate.onAuthorizationChange = { [weak self] status in
            Task { @MainActor in
                self?.authorization = status
                // 거부로 바뀌었으면 기다리던 요청을 풀어 준다 — 안 하면 촬영이 멈춘다.
                if self?.isDenied == true { self?.resume(with: nil) }
                // 결정이 났으면(허용이든 거부든) 화면 03을 다음 권한으로 넘긴다.
                if status != .notDetermined { self?.resumeAuthorization() }
            }
        }
    }

    func requestAuthorization() {
        manager.requestWhenInUseAuthorization()
    }

    /// 권한 대화상자를 띄우고 **사용자가 답할 때까지 기다린다.**
    ///
    /// 화면 03은 권한 3개를 **차례로** 물어야 한다. `requestAuthorization()`은 즉시
    /// 반환해서, 그대로 연달아 호출하면 카메라 대화상자 위로 위치 대화상자를 밀어 넣는다
    /// (iOS는 두 번째를 조용히 버린다 — 위치 권한을 아예 못 물어보게 된다).
    ///
    /// `CLLocationManager`에는 async 요청 API가 없어서 델리게이트 콜백을 기다린다.
    func requestAuthorizationAndWait() async {
        guard authorization == .notDetermined else { return }
        await withCheckedContinuation { continuation in
            pendingAuthorization.append(continuation)
            manager.requestWhenInUseAuthorization()
        }
    }

    /// 현재 위치. 권한이 없거나 실패하면 `nil`이다 — **호출자는 계속 진행해야 한다.**
    func currentLocation() async -> CLLocation? {
        guard !isDenied else { return nil }
        if authorization == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        // 최근 값이 있으면 그대로 쓴다. 산책 중이라 몇십 미터는 문제가 안 된다.
        if let cached = manager.location,
           cached.timestamp.timeIntervalSinceNow > -60 {
            return cached
        }
        return await withCheckedContinuation { continuation in
            pending.append(continuation)
            manager.requestLocation()
        }
    }

    private func resume(with location: CLLocation?) {
        let waiting = pending
        pending = []
        for continuation in waiting { continuation.resume(returning: location) }
    }

    private func resumeAuthorization() {
        let waiting = pendingAuthorization
        pendingAuthorization = []
        for continuation in waiting { continuation.resume() }
    }
}

/// `CLLocationManagerDelegate`를 받는 전용 객체.
///
/// **왜 분리했나.** 이 프로토콜은 `@MainActor`가 아니라서 `LocationProvider`가
/// 직접 채택하면 Swift 6 엄격 동시성이 막는다("crosses into main actor-isolated code").
/// 콜백만 넘기고 상태는 `LocationProvider`가 메인 액터에서 갖는다.
private final class LocationDelegate: NSObject, CLLocationManagerDelegate {

    /// `@MainActor`로 표시해 두면 클로저 안에서 메인 액터 상태를 만질 수 있다.
    var onLocation: (@Sendable (CLLocation?) -> Void)?
    var onAuthorizationChange: (@Sendable (CLAuthorizationStatus) -> Void)?

    func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        onLocation?(locations.last)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // 위치를 못 잡아도 촬영은 계속된다.
        onLocation?(nil)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        onAuthorizationChange?(manager.authorizationStatus)
    }
}
