import Foundation

/// PlantNet 실제 인식기 (A-1 확정).
///
/// **개화월 하드 필터가 여기서 완성된다.** PlantNet은 79,047종(World flora 84,513종)에서
/// 고르므로 11월에 "벚꽃"을 1순위로 줄 수 있다. `candidates`로 받은 **이번 달 개화 종만**
/// 남기고 나머지는 버린다 — A-1의 필수 구현 조건이다.
///
/// **학명으로 맞춘다.** PlantNet은 한국 이름을 모른다. `flowers.json`의 `scientific_name`이
/// 유일한 연결 고리다. 속(genus)까지만 맞는 경우도 받아들인다 —
/// PlantNet이 `Taraxacum officinale`를 주고 우리가 `Taraxacum coreanum`을 들고 있으면
/// **민들레라는 건 맞다.** 종을 못 가리는 건 B-4 유사종 통합이 풀 문제다.
struct PlantNetRecognizer: FlowerRecognizer {

    /// 학명 → `Flower.id`. 앱 시작 시 한 번 만든다.
    let scientificIndex: ScientificNameIndex
    let apiKey: String
    let session: URLSession
    /// `k-eastern-asia`(4,932종) — 실측으로 존재를 확인했다 (2026-08-05).
    /// `k-world-flora`(84,513종)보다 좁아서 오답이 줄어든다.
    var project: String = "k-eastern-asia"

    init(
        scientificIndex: ScientificNameIndex,
        apiKey: String? = nil,
        session: URLSession = .shared
    ) {
        self.scientificIndex = scientificIndex
        self.apiKey = apiKey ?? AppSecrets.plantNetAPIKey
        self.session = session
    }

    func identify(
        imageData: Data,
        candidates: [Int]
    ) async throws -> [RecognitionCandidate] {
        guard !apiKey.isEmpty else { throw RecognitionError.unavailable }

        let request = try makeRequest(imageData: imageData)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw RecognitionError.unavailable
        }
        switch http.statusCode {
        case 200: break
        case 429: throw RecognitionError.quotaExceeded
        // 404 = 인식 결과 없음. 오류가 아니라 **판별 실패(화면 12)**다.
        case 404: return []
        default: throw RecognitionError.unavailable
        }

        let decoded = try JSONDecoder().decode(PlantNetResponse.self, from: data)

        // **개화월 하드 필터.** 이번 달 후보에 없는 종은 버린다.
        let allowed = Set(candidates)
        var seen = Set<Int>()
        var results: [RecognitionCandidate] = []

        for item in decoded.results {
            // **후보 집합 안에서 고르게 한다.** 색인에 `allowed`를 넘기는 이유는 실측이다 —
            // 넘기지 않으면 속 대표를 도감번호 최솟값으로 정해 버려서,
            // 8월에 `Rosa chinensis 0.606`(장미, 정답)이 `찔레꽃`(5~6월)으로 번역되고
            // 개화월 필터에 탈락한다. 그 자리를 `Begonia grandis 0.003`이 차지했다.
            // 200종 중 94종이 다종 속에 있고 그중 30속은 개화월이 서로 다르다 —
            // 이건 예외가 아니라 절반의 문제였다.
            guard let flowerID = scientificIndex.flowerID(
                      for: item.species.scientificNameWithoutAuthor,
                      preferring: allowed
                  ),
                  allowed.contains(flowerID),
                  seen.insert(flowerID).inserted
            else { continue }
            results.append(RecognitionCandidate(flowerID: flowerID, score: item.score))
            if results.count == GamePolicy.candidateCount { break }
        }
        return results
    }

    private func makeRequest(imageData: Data) throws -> URLRequest {
        var components = URLComponents(
            string: "https://my-api.plantnet.org/v2/identify/\(project)"
        )!
        components.queryItems = [
            URLQueryItem(name: "api-key", value: apiKey),
            // `flower` — 우리가 받는 건 항상 꽃 사진이다. 기관(organ)을 알려주면 정확도가 오른다.
            URLQueryItem(name: "include-related-images", value: "false"),
            URLQueryItem(name: "nb-results", value: "10"),
        ]
        guard let url = components.url else { throw RecognitionError.unavailable }

        let boundary = "catchflower-\(UUID().uuidString)"
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(
            "multipart/form-data; boundary=\(boundary)",
            forHTTPHeaderField: "Content-Type"
        )
        request.httpBody = multipartBody(imageData: imageData, boundary: boundary)
        // 화면 08이 `5초 정도 걸려요`라고 말한다. 무한정 기다리게 두지 않는다.
        request.timeoutInterval = 30
        return request
    }

    private func multipartBody(imageData: Data, boundary: String) -> Data {
        var body = Data()
        func append(_ string: String) {
            body.append(Data(string.utf8))
        }
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"organs\"\r\n\r\n")
        append("flower\r\n")
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"images\"; filename=\"flower.jpg\"\r\n")
        append("Content-Type: image/jpeg\r\n\r\n")
        body.append(imageData)
        append("\r\n--\(boundary)--\r\n")
        return body
    }

    // MARK: - 응답 모양

    private struct PlantNetResponse: Decodable {
        let results: [Result]
        struct Result: Decodable {
            /// 0.0~1.0. **보정된 확률이라 그대로 쓴다** (퍼센트로 곱하지 않는다).
            let score: Double
            let species: Species
        }
        struct Species: Decodable {
            let scientificNameWithoutAuthor: String
        }
    }
}

/// 학명 → `Flower.id` 색인.
///
/// **속(genus)까지만 맞는 경우를 받아들인다.** PlantNet이 주는 종과 우리 CSV의 종이
/// 다를 수 있는데(같은 속의 다른 종), 사용자에게는 "민들레"로 같다.
/// 종을 정확히 못 가리는 건 B-4 유사종 통합이 풀 문제고, 여기서 버리면
/// **맞는 답을 판별 실패로 만든다.**
struct ScientificNameIndex: Sendable {

    private let exact: [String: Int]
    /// 속 → 그 속의 **모든** 종 id (도감번호 순).
    ///
    /// **하나만 담지 않는다.** 속 대표를 하나로 고정하면 개화월 필터와 싸운다 —
    /// `Rosa`의 대표가 `찔레꽃`(5~6월)이면 8월 장미 사진은 전부 탈락한다.
    /// 어느 종을 고를지는 색인이 아니라 **호출 시점의 후보 집합**이 정한다.
    private let byGenus: [String: [Int]]

    init(flowers: [Flower]) {
        var exact: [String: Int] = [:]
        var byGenus: [String: [Int]] = [:]
        for flower in flowers {
            let name = Self.normalize(flower.scientificName)
            guard !name.isEmpty else { continue }
            exact[name] = flower.id
            if let genus = name.split(separator: " ").first {
                byGenus[String(genus), default: []].append(flower.id)
            }
        }
        self.exact = exact
        // CSV는 id 순이지만 의존하지 않고 정렬한다 — 동점일 때 결과가 결정론적이어야 한다.
        self.byGenus = byGenus.mapValues { $0.sorted() }
    }

    /// - Parameter preferring: 이번 달 개화 종 등 **살아남을 수 있는 id 집합**.
    ///   속에 여러 종이 있으면 이 집합에 든 종을 먼저 고른다.
    ///   `nil`이면 예전처럼 도감번호가 가장 작은 종을 준다(테스트·비필터 경로용).
    func flowerID(for scientificName: String, preferring: Set<Int>? = nil) -> Int? {
        let name = Self.normalize(scientificName)
        // exact가 후보 밖이면 속으로 내려간다 — `Bellis perennis`(4~5월)를 정확히
        // 맞혔더라도 8월엔 쓸 수 없고, 같은 속의 다른 종이 개화 중일 수 있다.
        if let hit = exact[name], preferring?.contains(hit) ?? true { return hit }
        guard let genus = name.split(separator: " ").first,
              let sameGenus = byGenus[String(genus)]
        else { return exact[name] }
        if let preferring {
            return sameGenus.first(where: preferring.contains) ?? exact[name]
        }
        return sameGenus.first
    }

    /// 대소문자·여분 공백·품종 표기(`var.` 등)를 지운다.
    private static func normalize(_ name: String) -> String {
        name
            .lowercased()
            .replacingOccurrences(of: "var.", with: " ")
            .replacingOccurrences(of: "subsp.", with: " ")
            .split(separator: " ", omittingEmptySubsequences: true)
            .prefix(2)
            .joined(separator: " ")
    }
}
