// 원본 녹화에서 필요한 구간만 잘라 이어붙인다. 제출 요건이 30~60초인데 원본은 157초다.
//
// 🔴 이 맥에는 ffmpeg이 없어서 AVFoundation으로 한다. **재인코딩 없이 컷만 이어붙인다** —
//    `AVAssetExportPresetPassthrough`라 화질 손실이 없고, **판별 결과를 가공하지 않는다.**
//    (심사 대상 영상이다. 배속·합성·리터칭은 하지 않는다 — 콘티 5-1절.)
//
// 사용: swift trim_play_video.swift <원본.mp4> <출력.mp4> "3-5,17-22,..."
//       보통은 직접 부르지 않고 trim_play_video.py 가 부른다(구간이 거기 적혀 있다).
//
// ⚠️ deprecated 경고 6개가 나오지만 그대로 둔다 — 대체 API(`load(.tracks)`·`export(to:as:)`)는
//    async라 스크립트 최상단을 async로 바꿔야 하고, 동작에는 차이가 없다. **경고는 뜨고 돈다.**
import AVFoundation
import Foundation

guard CommandLine.arguments.count == 4 else {
    print("사용: swift trim_play_video.swift <원본.mp4> <출력.mp4> \"시작-끝,시작-끝,...\"")
    exit(2)
}
let src = URL(fileURLWithPath: CommandLine.arguments[1])
let dst = URL(fileURLWithPath: CommandLine.arguments[2])
let ranges: [(Double, Double)] = CommandLine.arguments[3].split(separator: ",").map {
    let p = $0.split(separator: "-").compactMap { Double($0) }
    guard p.count == 2, p[1] > p[0] else { fatalError("구간 형식이 틀렸다: \($0)") }
    return (p[0], p[1])
}
let asset = AVURLAsset(url: src)
// 🔴 **비디오 트랙을 골라야 한다.** screenrecord mp4에는 `mett`(메타데이터) 트랙이 같이
//    들어 있어서 첫 트랙을 그냥 쓰면 해상도 0x0 · 프레임 1개가 나온다(한 번 그렇게 읽었다).
guard let vTrack = asset.tracks(withMediaType: .video).first else { fatalError("비디오 트랙이 없다") }
let comp = AVMutableComposition()
guard let cv = comp.addMutableTrack(withMediaType: .video,
                                   preferredTrackID: kCMPersistentTrackID_Invalid)
else { fatalError("트랙 생성 실패") }
cv.preferredTransform = vTrack.preferredTransform  // 세로 영상이 눕지 않게
var cursor = CMTime.zero
for (s, e) in ranges {
    let range = CMTimeRange(start: CMTime(seconds: s, preferredTimescale: 600),
                            duration: CMTime(seconds: e - s, preferredTimescale: 600))
    try cv.insertTimeRange(range, of: vTrack, at: cursor)
    cursor = cursor + range.duration
    print(String(format: "  %5.1f~%5.1f (%.1f초) 붙였다 → 누적 %.1f초",
                 s, e, e - s, CMTimeGetSeconds(cursor)))
}
try? FileManager.default.removeItem(at: dst)
guard let ex = AVAssetExportSession(asset: comp, presetName: AVAssetExportPresetPassthrough)
else { fatalError("export 세션 실패") }
ex.outputURL = dst
ex.outputFileType = .mp4
let sem = DispatchSemaphore(value: 0)
ex.exportAsynchronously { sem.signal() }
sem.wait()
guard ex.status == .completed else {
    print("❌ 실패: \(ex.error?.localizedDescription ?? "?")  status=\(ex.status.rawValue)")
    exit(1)
}
let sz = (try! FileManager.default.attributesOfItem(atPath: dst.path)[.size] as! NSNumber).intValue
print("✅ \(dst.path)  \(sz) bytes · \(String(format: "%.1f", CMTimeGetSeconds(cursor)))초")
