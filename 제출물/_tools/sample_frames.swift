// mp4에서 지정한 초의 프레임을 jpg로 뽑는다. **영상을 눈으로 확인하는 유일한 방법이다.**
//
// 🔴 왜 필요한가. `screenrecord`는 컷이 남아 있어도 `--time-limit`에 맞춰 끝내고,
//    스크립트는 남은 탭을 계속 눌러 "완주했다"를 찍는다. **로그는 전부 통과인데 mp4에는
//    마지막 컷이 없다.** 실측으로 랭킹 컷이 9.8초 잘린 적이 있다 — 크기도 정상이었다.
//    그래서 재생시간(record_play_video.py `mp4_duration`)과 **이 프레임 추출** 둘 다 본다.
//
// 이 맥에는 ffmpeg/ffprobe가 없다. AVFoundation `AVAssetImageGenerator`로 한다.
//
// 사용: swift sample_frames.swift <mp4> <출력폴더> "3,17,26,34"
//       콘택트 시트로 묶어 보려면 그 폴더에서 contact_sheet.py 를 돌린다.
import AVFoundation
import AppKit
import Foundation

guard CommandLine.arguments.count == 4 else {
    print("사용: swift sample_frames.swift <mp4> <출력폴더> \"초,초,초\"")
    exit(2)
}
let src = URL(fileURLWithPath: CommandLine.arguments[1])
let outDir = URL(fileURLWithPath: CommandLine.arguments[2])
let times = CommandLine.arguments[3].split(separator: ",").compactMap { Double($0) }
try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

let asset = AVURLAsset(url: src)
guard let vTrack = asset.tracks(withMediaType: .video).first else { fatalError("비디오 트랙이 없다") }
print(String(format: "길이 %.1f초 · %.0fx%.0f",
             CMTimeGetSeconds(asset.duration),
             vTrack.naturalSize.width, vTrack.naturalSize.height))
let gen = AVAssetImageGenerator(asset: asset)
gen.appliesPreferredTrackTransform = true
// 🔴 **허용 오차를 0으로 두지 않는다.** 정확히 그 시각에 프레임이 없으면 실패로 떨어지는데,
//    screenrecord는 정지 화면에서 프레임을 거의 안 넣는다(대기 구간이 그렇다).
//    0.5초 앞뒤를 허용하면 "화면이 안 바뀐 구간"도 뽑힌다.
gen.requestedTimeToleranceBefore = CMTime(seconds: 0.5, preferredTimescale: 600)
gen.requestedTimeToleranceAfter = CMTime(seconds: 0.5, preferredTimescale: 600)

var failed = 0
for t in times {
    let time = CMTime(seconds: t, preferredTimescale: 600)
    do {
        let cg = try gen.copyCGImage(at: time, actualTime: nil)
        let rep = NSBitmapImageRep(cgImage: cg)
        guard let jpg = rep.representation(using: .jpeg, properties: [.compressionFactor: 0.8])
        else { throw NSError(domain: "jpeg", code: 1) }
        let name = String(format: "g%06.1f.jpg", t)
        try jpg.write(to: outDir.appendingPathComponent(name))
        print(String(format: "  %5.1fs ok", t))
    } catch {
        failed += 1
        print(String(format: "  %5.1fs 실패 — %@", t, error.localizedDescription))
    }
}
// 실패가 전부라면 영상이 깨진 것이다. 일부 실패는 그 시각에 프레임이 없는 것(정지 구간).
if failed == times.count { print("❌ 한 장도 못 뽑았다 — mp4가 깨졌을 것이다"); exit(1) }
