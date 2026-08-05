import Foundation
import UIKit

/// 촬영 사진을 기기에 저장한다.
///
/// **왜 필요한가.** 지금까지는 판별에 쓴 JPEG를 그냥 버렸다. 그래서 화면 10·11의
/// `지금까지 만난 {꽃}` 스트립과 화면 05 `내 발견 기록`이 전부 회색 아이콘이었다.
///
/// **왜 사진을 DB에 안 넣는가.** `Discovery.photo_url`에는 **파일명만** 넣는다.
/// 서버(A-2)가 붙으면 같은 파일명이 스토리지 키가 되므로 레코드 모양이 안 바뀐다.
///
/// 저장 위치는 `Documents/Photos/`다. `Caches/`는 iOS가 임의로 비워서
/// 도감 사진이 소리 없이 사라진다 — 도감은 사용자의 자산이라 지워지면 안 된다.
struct PhotoStore: Sendable {

    /// 테스트가 실제 사진 디렉터리를 오염시키지 않게 루트를 주입받는다.
    let root: URL

    init(root: URL? = nil) {
        self.root = root ?? Self.defaultRoot
    }

    static var defaultRoot: URL {
        let documents = FileManager.default.urls(
            for: .documentDirectory,
            in: .userDomainMask
        )[0]
        return documents.appendingPathComponent("Photos", isDirectory: true)
    }

    /// 저장하고 **파일명**을 돌려준다. 경로 전체를 저장하면 안 된다 —
    /// iOS는 앱 컨테이너 경로가 재설치·복원 때 바뀌어서 다음 실행에 못 찾는다.
    @discardableResult
    func save(_ data: Data, fileName: String = "\(UUID().uuidString).jpg") throws -> String {
        try FileManager.default.createDirectory(
            at: root,
            withIntermediateDirectories: true
        )
        try data.write(to: root.appendingPathComponent(fileName), options: .atomic)
        return fileName
    }

    func url(for fileName: String) -> URL {
        root.appendingPathComponent(fileName)
    }

    func data(for fileName: String) -> Data? {
        try? Data(contentsOf: url(for: fileName))
    }

    func image(for fileName: String) -> UIImage? {
        guard let data = data(for: fileName) else { return nil }
        return UIImage(data: data)
    }

    func exists(_ fileName: String) -> Bool {
        FileManager.default.fileExists(atPath: url(for: fileName).path)
    }

    func delete(_ fileName: String) {
        try? FileManager.default.removeItem(at: url(for: fileName))
    }

    /// 시뮬레이터 픽스처는 사진 데이터가 비어 있다. 빈 파일을 만들어 두면
    /// `exists`는 true인데 `image`는 nil이어서 판단이 꼬인다 — 아예 저장하지 않는다.
    func saveIfNotEmpty(_ data: Data) -> String? {
        guard !data.isEmpty else { return nil }
        return try? save(data)
    }
}
