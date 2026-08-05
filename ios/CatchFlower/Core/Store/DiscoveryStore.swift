import Foundation

/// 발견 기록을 기기에 저장한다.
///
/// **왜 SwiftData가 아닌가.** 최소 iOS를 17로 올린 명분이 SwiftData였는데,
/// 여기서는 **JSON 파일**을 쓴다. 이유는 셋이다.
///
/// 1. `Discovery`는 이미 `Codable`이고 **공유계약의 snake_case와 1:1**이다.
///    `@Model` 클래스로 바꾸면 계약 필드명을 다시 손으로 맞춰야 하고,
///    양쪽(iOS/AOS)이 어긋날 자리가 하나 더 생긴다.
/// 2. 서버(A-2 Supabase)가 붙으면 이 레코드는 **그대로 업로드된다.**
///    SwiftData 모델은 한 번 더 변환해야 한다.
/// 3. 발견 기록은 한 사용자당 수천 건 규모다. 쿼리·관계·마이그레이션이 필요한 양이 아니다.
///
/// SwiftData가 필요해지는 시점은 **오프라인 동기화 큐**를 만들 때다. 그때 다시 판단한다.
/// (iOS 17 결정 자체는 `@Observable`만으로도 값을 한다.)
///
/// **파일 하나에 전부 쓴다.** 건당 파일로 쪼개면 200건에 200번 읽기가 된다.
actor DiscoveryStore {

    private let fileURL: URL

    init(fileURL: URL? = nil) {
        self.fileURL = fileURL ?? Self.defaultURL
    }

    static var defaultURL: URL {
        FileManager.default
            .urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("discoveries.json")
    }

    /// 계약 1-3절의 전송 형식과 같게 맞춘다. 서버가 붙으면 그대로 쓴다.
    private static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    func load() -> [Discovery] {
        guard let data = try? Data(contentsOf: fileURL) else { return [] }
        do {
            return try Self.makeDecoder().decode([Discovery].self, from: data)
        } catch {
            // **여기서 조용히 []를 돌려주면 사용자 도감이 사라진 것처럼 보인다.**
            // 원본을 옆으로 치워 두고 빈 상태로 시작한다 — 최소한 복구는 가능하다.
            let backup = fileURL.appendingPathExtension("corrupt")
            try? FileManager.default.removeItem(at: backup)
            try? FileManager.default.moveItem(at: fileURL, to: backup)
            return []
        }
    }

    func save(_ discoveries: [Discovery]) throws {
        let data = try Self.makeEncoder().encode(discoveries)
        // **상위 폴더를 먼저 만든다.** `Documents/`는 항상 있지만 주입받은 경로는
        // 없을 수 있다(UI 테스트의 임시 폴더). 이걸 빼먹으면 쓰기가 실패하고,
        // `persist()`가 `try?`로 삼켜서 **조용히 저장이 안 된다** — 실제로 그랬다.
        try FileManager.default.createDirectory(
            at: fileURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        // `.atomic` — 저장 중 앱이 죽어도 반쪽 파일이 남지 않는다.
        try data.write(to: fileURL, options: .atomic)
    }
}
