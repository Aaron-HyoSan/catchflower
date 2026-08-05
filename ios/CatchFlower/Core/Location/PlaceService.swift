import Foundation

/// 좌표 → 장소 이름과 행정구역. 카카오 로컬 API를 쓴다 (A-3 확정).
///
/// **왜 캐시가 필수인가.** 카카오는 일일 쿼터가 있다. 맛집지도에서 겪은 것과 같다.
/// 촬영마다 조회하면 같은 자리를 반복 조회해서 한도를 태운다.
/// 그래서 좌표를 `GamePolicy.placeCoordinateRoundingDigits`자리로 반올림해 캐시한다 —
/// B-5의 "같은 장소" 판정과 **같은 반올림 기준**을 쓴다.
///
/// **행정구역은 동 코드와 구 코드를 둘 다 저장한다.** B-6("동 단위 10명 미만이면
/// 구 단위로 확장")이 여기 의존하고, 런타임에 동에서 구를 유도할 수 없다.
protocol PlaceService: Sendable {
    func place(lat: Double, lng: Double) async -> PlaceInfo?
}

/// 좌표를 사람이 읽는 이름으로 바꾼 결과.
struct PlaceInfo: Codable, Hashable, Sendable {
    /// 로컬 검색 결과. 예 `서울숲`. 주변에 이름난 장소가 없으면 nil이다.
    var placeName: String?
    /// 행정동 이름. 예 `연남동`.
    var dongName: String?
    /// 행정동 코드.
    var dongCode: String?
    /// 시군구 코드. **B-6이 여기 의존한다.**
    var guCode: String?
}

/// 카카오 로컬 구현체.
///
/// 키는 소스에 없다 — `Config/Secrets.xcconfig` → `Info.plist` → 여기로 온다.
actor KakaoPlaceService: PlaceService {

    private let apiKey: String
    private let session: URLSession
    /// 반올림한 좌표 → 결과. 같은 자리 재조회를 막는다.
    private var cache: [String: PlaceInfo] = [:]

    init(apiKey: String? = nil, session: URLSession = .shared) {
        self.apiKey = apiKey ?? AppSecrets.kakaoRESTAPIKey
        self.session = session
    }

    func place(lat: Double, lng: Double) async -> PlaceInfo? {
        let key = AppSession.placeKey(lat: lat, lng: lng)
        if let hit = cache[key] { return hit }
        guard !apiKey.isEmpty else { return nil }

        // 행정구역이 먼저다 — 랭킹(B-6)이 여기 의존해서 장소명보다 중요하다.
        async let region = regionCode(lat: lat, lng: lng)
        async let name = nearbyPlaceName(lat: lat, lng: lng)

        var info = await region ?? PlaceInfo()
        info.placeName = await name
        guard info.dongCode != nil || info.placeName != nil else { return nil }

        cache[key] = info
        return info
    }

    // MARK: - 좌표 → 행정구역

    private func regionCode(lat: Double, lng: Double) async -> PlaceInfo? {
        var components = URLComponents(
            string: "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"
        )!
        components.queryItems = [
            URLQueryItem(name: "x", value: String(lng)),
            URLQueryItem(name: "y", value: String(lat)),
        ]
        guard let data = try? await get(components.url!),
              let decoded = try? JSONDecoder().decode(RegionResponse.self, from: data)
        else { return nil }

        // `H`는 행정동, `B`는 법정동이다. 사용자에게 익숙한 쪽은 행정동이다.
        let admin = decoded.documents.first { $0.regionType == "H" }
            ?? decoded.documents.first
        guard let admin else { return nil }

        return PlaceInfo(
            placeName: nil,
            dongName: admin.region3DepthName,
            dongCode: admin.code,
            // 구 코드는 행정동 코드 앞 5자리다.
            guCode: admin.code.count >= 5 ? String(admin.code.prefix(5)) : nil
        )
    }

    // MARK: - 좌표 → 주변 장소명

    private func nearbyPlaceName(lat: Double, lng: Double) async -> String? {
        var components = URLComponents(
            string: "https://dapi.kakao.com/v2/local/search/category.json"
        )!
        components.queryItems = [
            // AT4 관광명소 · 공원 등이 여기 걸린다. 꽃을 찍는 장소와 맞다.
            URLQueryItem(name: "category_group_code", value: "AT4"),
            URLQueryItem(name: "x", value: String(lng)),
            URLQueryItem(name: "y", value: String(lat)),
            URLQueryItem(name: "radius", value: "500"),
            URLQueryItem(name: "sort", value: "distance"),
            URLQueryItem(name: "size", value: "1"),
        ]
        guard let data = try? await get(components.url!),
              let decoded = try? JSONDecoder().decode(SearchResponse.self, from: data)
        else { return nil }
        return decoded.documents.first?.placeName
    }

    private func get(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.setValue("KakaoAK \(apiKey)", forHTTPHeaderField: "Authorization")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw PlaceError.requestFailed(
                (response as? HTTPURLResponse)?.statusCode ?? -1
            )
        }
        return data
    }

    enum PlaceError: Error {
        case requestFailed(Int)
    }

    // MARK: - 응답 모양

    private struct RegionResponse: Decodable {
        let documents: [Document]
        struct Document: Decodable {
            let regionType: String
            let code: String
            let region3DepthName: String?
            enum CodingKeys: String, CodingKey {
                case regionType = "region_type"
                case code
                case region3DepthName = "region_3depth_name"
            }
        }
    }

    private struct SearchResponse: Decodable {
        let documents: [Document]
        struct Document: Decodable {
            let placeName: String
            enum CodingKeys: String, CodingKey {
                case placeName = "place_name"
            }
        }
    }
}

/// 위치 기능이 꺼진 환경(시뮬레이터·테스트)에서 쓴다.
struct NoPlaceService: PlaceService {
    func place(lat: Double, lng: Double) async -> PlaceInfo? { nil }
}
