import SwiftUI

/// 촬영 → 판별 → 등록 흐름(화면 07~13)의 단일 조정자.
///
/// **왜 한 곳에 모으는가.** 이 흐름은 분기가 많다:
/// 판별 확정(09) / 임계값 미달(09 변형) / 실패(12) / 신규(10) / 재발견(11) / 공유(13).
/// 화면마다 다음 화면을 직접 열게 하면 되돌아올 곳이 뒤엉킨다.
/// 여기서 `step`만 바꾸고, 각 화면은 자기 그리기와 콜백만 안다.
struct CaptureFlow: View {
    @Environment(AppSession.self) private var session
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    @State private var step: Step = .camera
    @State private var shot: CapturedPhoto?
    @State private var candidates: [RecognitionCandidate] = []
    /// 사용자가 고른 후보의 순위(1·2·3). B-3 어뷰징 가드 ②로 저장한다.
    @State private var pickedRank = 1
    @State private var recorded: Discovery?
    /// B-5로 막힌 종. 화면에 꽃 이름·그림을 보여주려면 알아야 한다.
    @State private var duplicateFlowerID: Int?
    /// 촬영 중 이탈 확인 (A 문서 3절). **찍은 사진이 사라지는 걸 미리 알려야 한다.**
    @State private var isConfirmingLeave = false

    enum Step: Equatable {
        case camera             // 07
        case analyzing          // 08
        case confirm            // 09
        case registered         // 10 신규
        case rediscovered       // 11 재발견
        case failed             // 12
        case share              // 13
        case duplicate          // B-5 하루 중복 (와이어프레임에 없다 — A 문서 3절)
    }

    var body: some View {
        content
            .background(backgroundColor)
            .animation(.easeInOut(duration: 0.2), value: step)
            .confirmDialog(.leaveCapture, isPresented: $isConfirmingLeave) {
                dismiss()
            }
    }

    /// 촬영 화면에서 나가려 할 때. **찍은 사진이 있을 때만 확인을 묻는다.**
    ///
    /// 화면 07에서 아직 아무것도 안 찍었으면 잃을 게 없다 — 거기서 다이얼로그를 띄우면
    /// 그냥 방해다(A 문서 문구도 `찍은 사진은 저장되지 않아요`라서 말이 안 맞는다).
    private func requestClose() {
        if shot == nil {
            dismiss()
        } else {
            isConfirmingLeave = true
        }
    }

    /// 화면 07·08만 어두운 화면이다 (문구 스펙 표기).
    private var backgroundColor: Color {
        switch step {
        case .camera, .analyzing: return Theme.Palette.cameraBackground
        default: return Theme.Palette.background
        }
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case .camera:
            CameraView(
                onCapture: { photo in
                    shot = photo
                    step = .analyzing
                    // 위치는 판별과 **동시에** 잡는다. 순서대로 하면 촬영 후
                    // GPS를 기다리는 만큼 사용자가 멈춰 서 있게 된다.
                    Task {
                        let filled = await session.attachLocation(to: photo)
                        // 판별이 이미 끝나 등록까지 갔으면 늦은 값이다 — 버린다.
                        guard step == .analyzing || step == .confirm else { return }
                        shot = filled
                    }
                },
                onClose: requestClose
            )

        case .analyzing:
            AnalyzingView(onCancel: {
                // 취소하면 그 사진은 버린다. 남겨두면 화면 07에서 `닫기`를 눌렀을 때
                // 이미 없는 사진을 두고 "저장되지 않아요"라고 묻게 된다.
                shot = nil
                step = .camera
            })
            .task { await identify() }

        case .confirm:
            // `if let shot`으로 풀면 이름이 가려져서 콜백 안에서 상태를 못 지운다.
            if let taken = shot {
                IdentifyConfirmView(
                    photo: taken,
                    candidates: candidates,
                    onConfirm: { candidate, rank in
                        pickedRank = rank
                        register(candidate: candidate, rank: rank)
                    },
                    onRetake: {
                        shot = nil
                        step = .camera
                    }
                )
            }

        case .registered, .rediscovered:
            if let recorded, let flower = session.repository[recorded.flowerID] {
                DiscoveryResultView(
                    flower: flower,
                    discovery: recorded,
                    isFirst: step == .registered,
                    onShare: { step = .share },
                    onKeepPrivate: {
                        // A 문서 3절 — `도감 등록 후 공유 안 함`.
                        // 이게 없으면 화면만 닫혀서 등록됐는지 알 수 없다.
                        toasts.show(.savedToCodex)
                        dismiss()
                    }
                )
            }

        case .duplicate:
            if let duplicateFlowerID, let flower = session.repository[duplicateFlowerID] {
                DuplicateTodayView(
                    flower: flower,
                    onGoToCodex: { dismiss() },
                    onRetake: { step = .camera }
                )
            }

        case .failed:
            IdentifyFailedView(
                showsNotAFlowerGuide: session.showsNotAFlowerGuide,
                onRetake: { step = .camera },
                onLater: { dismiss() }
            )

        case .share:
            if let recorded, let flower = session.repository[recorded.flowerID] {
                MapShareSettingsView(
                    flower: flower,
                    discovery: recorded,
                    onDone: { visibility, note in
                        session.updateVisibility(
                            discoveryID: recorded.id,
                            to: visibility,
                            note: note
                        )
                        toasts.show(.sharedToMap)
                        dismiss()
                    },
                    // `공유하지 않기`도 **도감에는 이미 등록됐다.**
                    // 여기서 토스트를 안 띄우면 "공유 안 했으니 기록도 없나?"로 읽힌다.
                    onSkip: {
                        toasts.show(.savedToCodex)
                        dismiss()
                    }
                )
            }
        }
    }

    // MARK: - 판별

    private func identify() async {
        // 이름을 바꿔 받는다 — `let shot`으로 가리면 콜백에서 상태를 못 지운다.
        guard let taken = shot else { return }

        // **개화월 하드 필터.** 이 목록을 좁혀서 보내는 게 A-1의 필수 조건이다.
        // 서버가 붙으면 서버가 좁힌 목록으로 대체된다.
        var candidateIDs = session.candidateIDsForCurrentMonth

        #if DEBUG
        // 픽스처가 난이도를 지정했으면 그 난이도의 꽃을 앞으로 당긴다.
        // 실인식에서는 후보 목록의 **순서에 의미가 없다**(엔진이 점수를 매긴다).
        // 그래서 이 재배치는 프로덕션 동작을 바꾸지 않고, Mock만 결과가 달라진다.
        if let wanted = taken.preferredDifficulty {
            candidateIDs.sort { lhs, rhs in
                let l = session.repository[lhs]?.aiDifficulty == wanted
                let r = session.repository[rhs]?.aiDifficulty == wanted
                return l && !r
            }
        }
        #endif

        // **Mock에 파일명을 넘겨준다.** 계약이 정한 Mock 판정 근거는 파일명인데,
        // 인식 프로토콜은 이미지 바이트만 받는다(실제 엔진은 파일명을 안 쓴다).
        // 이걸 빼먹으면 픽스처가 뭐든 전부 `confident`로 판정돼서
        // 실패 경로(화면 12)를 아예 밟을 수 없다 — 실제로 그랬다.
        var engine = session.recognizer
        #if DEBUG
        if var mock = engine as? MockFlowerRecognizer, let fileName = taken.fileName {
            mock.fileName = fileName
            engine = mock
        }
        #endif

        do {
            let result = try await engine.identify(
                imageData: taken.data,
                candidates: candidateIDs
            )

            // **취소를 눌렀으면 여기서 멈춘다.** 안 그러면 사용자가 화면 07로 돌아간 뒤
            // 판별이 뒤늦게 끝나면서 화면 09로 강제로 끌려간다.
            guard !Task.isCancelled, step == .analyzing else { return }

            // 빈 배열 = 판별 실패. 1순위가 하한 미달인 것도 실패로 본다 (B-3).
            guard let top = result.first,
                  top.score >= GamePolicy.identifyFailureFloor else {
                session.noteIdentifyFailure()
                step = .failed
                return
            }
            candidates = result
            step = .confirm
        } catch {
            guard !Task.isCancelled, step == .analyzing else { return }
            // **네트워크 오류는 판별 실패가 아니다.** 화면 12는 "사진을 다시 찍어주세요"라고
            // 하는데, 연결 문제로 그러면 **사용자 잘못이 아닌 걸 사용자 잘못으로 만든다**
            // (A 문서 0절 원칙). 토스트로 원인을 알리고 촬영 화면으로 되돌린다.
            //
            // 연속 실패(B-11)로도 세지 않는다. 통신 문제로 `꽃이 아닐 수도 있어요`가
            // 뜨면 엉뚱한 안내가 된다.
            //
            // 일일 상한 초과(`quotaExceeded`)도 같이 묶는다. 사용자가 할 수 있는 게
            // "잠시 후 다시"인 건 같고, 상한 전용 문구는 A 문서에 없다 —
            // **없는 문구를 만들지 않는다.** 필요해지면 A 문서에 추가하고 case를 늘린다.
            switch error {
            case is URLError, RecognitionError.unavailable, RecognitionError.quotaExceeded:
                toasts.show(.networkError)
                shot = nil
                step = .camera
            default:
                session.noteIdentifyFailure()
                step = .failed
            }
        }
    }

    private func register(candidate: RecognitionCandidate, rank: Int) {
        guard let shot else { return }

        // **B-5 — 오너 확정 규칙.** 같은 종 + 같은 장소는 하루 1회다.
        // 여기서 부르지 않으면 상수와 판정 함수가 있어도 같은 꽃을 무한히 등록할 수 있다
        // (실제로 그랬다 — 단위 테스트만 이 함수를 부르고 있었다).
        if session.isDuplicateToday(
            flowerID: candidate.flowerID,
            lat: shot.lat,
            lng: shot.lng
        ) {
            duplicateFlowerID = candidate.flowerID
            step = .duplicate
            return
        }

        let isFirst = !session.hasDiscovered(flowerID: candidate.flowerID)

        // **사진을 실제로 저장한다.** 안 하면 도감 사진이 회색 아이콘으로 남는다.
        // 시뮬레이터 픽스처는 데이터가 비어 있어서 저장하지 않고 nil이 된다.
        let savedFileName = session.photos.saveIfNotEmpty(shot.data)

        let discovery = Discovery(
            id: UUID(),
            userID: session.userID,
            flowerID: candidate.flowerID,
            photoURL: savedFileName,
            lat: shot.lat,
            lng: shot.lng,
            placeName: shot.placeName,
            dongCode: shot.dongCode,
            guCode: shot.guCode,
            // 도감 등록과 지도 공유는 별개다. 등록 시점에는 비공개다 (기획서 8장).
            visibility: .private,
            aiConfidence: candidate.score,
            aiPickedRank: rank,
            isFirstDiscovery: isFirst,
            createdAt: shot.capturedAt,
            capturedAt: shot.capturedAt,
            note: nil
        )
        session.record(discovery)
        recorded = discovery
        step = isFirst ? .registered : .rediscovered
    }
}

/// 촬영 결과. 카메라·픽스처 어느 쪽에서 왔는지와 무관하게 같은 모양이다.
struct CapturedPhoto: Equatable, Sendable {
    let data: Data
    /// Mock이 시나리오를 고르는 근거 (계약: 파일명에 `low`/`fail`).
    let fileName: String?
    let capturedAt: Date
    var lat: Double?
    var lng: Double?
    var placeName: String?
    /// 행정동 코드. 카카오 좌표→행정구역 결과.
    var dongCode: String?
    /// 시군구 코드. **B-6(랭킹 구 단위 확장)이 여기 의존한다.**
    var guCode: String?
    /// **시뮬레이터 픽스처 전용.** 화면 09의 두 경로를 손으로 밟기 위한 것이다.
    ///
    /// 임계값은 꽃의 난이도로 갈리는데(하 60·중 70·상 85) Mock 점수는 0.82로 고정이라,
    /// 후보 1순위가 `high`면 0.82로도 애매 경로가 된다 — 즉 **어느 꽃이 뽑히느냐로
    /// 경로가 바뀐다**. 실기기에서는 이 필드가 항상 nil이다.
    var preferredDifficulty: AIDifficulty?
}
