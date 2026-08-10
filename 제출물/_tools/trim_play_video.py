#!/usr/bin/env python3
"""157초 원본 녹화를 제출용 51초로 줄이고, **줄인 결과를 눈으로 확인한다.**

제출 요건이 **30~60초**인데 `record_play_video.py`가 만드는 원본은 157초다. 컷 사이 대기와
에뮬레이터 걸음(매크로 10초)이 들어가 있어서 그렇다 — 원본도 커밋한다(재현·검증용).

🔴 **구간을 눈으로 정하지 않으면 조용히 틀린 영상이 나온다.** 첫 시도는 이렇게 망했다:
  · **검은 프리뷰 2초**가 앞에 붙어 카메라가 고장난 것처럼 보였다(42초 지점)
  · **분석중 로딩 컷이 아예 빠졌다** — 콘티 5-1절이 "배속·삭제 금지"로 못 박은 컷이다.
    3~5초 로딩은 "AI가 실제로 서버에 물어보고 있다"는 **유일한 시각적 증거**다.
    빠지면 판별 결과가 미리 정해진 것처럼 보인다.
  · 대신 후보 목록이 6초 중복이었다.
**43초라는 숫자와 "passthrough 성공"은 둘 다 초록이었다.** 그래서 아래 CUTS는
`sample_frames.swift`로 프레임을 뽑아 **화면을 읽고** 정한 값이고, 이 스크립트는 자른 뒤에도
다시 뽑아 확인한다.

⚠️ **재인코딩하지 않는다**(`AVAssetExportPresetPassthrough`). 화질 손실이 없고
   **판별 결과를 가공하지 않는다** — 심사 대상 영상이다.

사용:
    python3 제출물/_tools/trim_play_video.py            # 자르고 프레임 확인
    python3 제출물/_tools/trim_play_video.py --frames-only   # 자르지 않고 확인만
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOOLS = ROOT / "제출물/_tools"
OUT_DIR = ROOT / "제출물/영상"
SRC = OUT_DIR / "catchflower_play.mp4"
DST = OUT_DIR / "catchflower_play_51s.mp4"

#: 아래 `CUTS`를 정할 때 쓴 원본의 재생시간(초). **파일이 없어도 범위를 잴 수 있게 적어 둔다.**
#: 🔴 파일 유무로만 검사하면 **파일이 없을 때 검사가 스스로 꺼진다** — 실제로
#: `test_trim_play_video.py`가 그 구멍을 밟았다(범위를 벗어난 컷인데 **실패 0개**였다).
#: 다시 녹화했으면 이 값도 같이 고친다(테스트가 실제 파일과 이 값의 일치도 잰다).
SRC_SECONDS = 157.0

#: 원본 157초에서 쓸 구간 (시작초, 끝초, 무엇인가).
#: 🔴 **원본을 다시 녹화하면 이 값이 전부 틀어진다.** 대기 시간이 조금만 달라도 화면 전환
#: 시각이 밀린다. 새로 찍었으면 `--frames-only`로 프레임을 먼저 뽑아 **경계를 다시 읽는다.**
#: 아래 초는 2026-08-10 테이크(157.0초 · 3,592,575 bytes) 기준이다.
CUTS = [
    (3, 5, "스플래시 — 앱 아이콘"),
    (17, 22, "활동 지역 선택 → 현재 위치로 동네 찾기(삼성2동)"),
    (26, 29, "권한 안내 → 허용하고 시작하기"),
    (34, 37, "빈 도감 0/200종 · 아직 모은 꽃이 없어요"),
    (77, 82, "촬영 화면 — 꽃을 네모에 채운다"),
    (82, 86, "분석중 — 🔴 **이 컷을 빼거나 배속하지 않는다**(콘티 5-1)"),
    (86, 92, "화면 09 후보 목록 — 사람이 고른다"),
    (98, 103, "신규 등록 — 새로운 꽃을 발견했어요! · 1/200종"),
    (110, 116, "화면 13 지도 공유 — 공개 범위 고르고 공유하기"),
    (129, 133, "화면 14 지도 — 핀이 찍혔다"),
    (138, 141, "도감 1/200종 · 최근 발견한 꽃"),
    (146, 151, "랭킹 — 강남구 시즌 순위 · 22일 남음"),
]

#: 자른 뒤 확인할 시각(초). 컷 경계를 걸치게 골라 **이어붙인 자리가 엉뚱하지 않은지** 본다.
CHECK_AT = [1, 3, 5, 8, 11, 14, 17, 20, 23, 25, 28, 30, 33, 35, 38, 41, 44, 47, 50]


def sh(*args):
    r = subprocess.run(args, capture_output=True, text=True, timeout=900)
    out = (r.stdout or "") + (r.stderr or "")
    if r.returncode != 0:
        raise SystemExit(f"실패({r.returncode}): {' '.join(map(str, args))}\n{out}")
    # swift가 deprecated 경고를 쏟는다(그대로 돈다 — .swift 주석 참조).
    # ⚠️ 경고 **본문**(`24 | let asset = ...` 같은 소스 인용)까지 같이 나와서, 단순히
    # "warning: 없는 줄"로 거르면 수십 줄이 새어 나온다. 우리 출력만 화이트리스트로 남긴다.
    # 🔴 화이트리스트만으로는 부족하다 — 경고가 인용하는 **소스 줄에 우리 문구가 들어 있다**
    #    (`print("❌ 실패: ...")`). 인용 줄은 `NN | ` 꼴이라 그것을 먼저 버린다.
    keep = ("붙였다", "ok", "실패", "✅", "❌", "길이", "시트", "한 장도")
    return "\n".join(l for l in out.splitlines()
                     if not re.match(r"^\s*\d+\s*\|", l) and any(k in l for k in keep))


def frames(mp4, tag, times):
    """프레임을 뽑고 콘택트 시트로 묶는다. **경로를 돌려준다 — 사람이 열어 봐야 한다.**"""
    d = OUT_DIR / f"_frames_{tag}"
    print(sh("swift", str(TOOLS / "sample_frames.swift"), str(mp4), str(d),
             ",".join(str(t) for t in times)))
    sheet = sh(sys.executable, str(TOOLS / "contact_sheet.py"), str(d))
    print(sheet)
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frames-only", action="store_true",
                    help="자르지 않고 원본 프레임만 뽑는다 (구간을 다시 읽을 때)")
    a = ap.parse_args()

    if not SRC.exists():
        raise SystemExit(f"원본이 없다: {SRC}\n  → python3 제출물/_tools/record_play_video.py")

    if a.frames_only:
        # 원본 전체를 4초 간격으로 훑는다. 화면 전환 시각을 여기서 읽어 CUTS를 고친다.
        frames(SRC, "src", list(range(0, 157, 4)))
        print("\n→ 시트를 열어 화면 전환 시각을 읽고 CUTS를 고친다")
        return

    total = sum(e - s for s, e, _ in CUTS)
    print(f"컷 {len(CUTS)}개 · 합계 {total}초")
    for s, e, what in CUTS:
        print(f"  {s:3d}~{e:3d}s  {what}")
    # 🔴 요건을 스크립트가 잰다. 30초 미만이면 흐름이 안 보이고 60초를 넘으면 요건 위반이다.
    if not 30 <= total <= 60:
        raise SystemExit(f"제출 요건은 30~60초인데 {total}초다 — CUTS를 고친다")

    print()
    print(sh("swift", str(TOOLS / "trim_play_video.swift"), str(SRC), str(DST),
             ",".join(f"{s}-{e}" for s, e, _ in CUTS)))

    # 🔴 **자른 것을 다시 확인한다.** passthrough는 키프레임 경계에서 앞뒤가 붙을 수 있고,
    #    "성공" 출력과 재생시간만으로는 **무슨 화면이 들어갔는지 알 수 없다.**
    print("\n자른 결과를 확인한다")
    d = frames(DST, "51s", CHECK_AT)
    print(f"\n✅ {DST}")
    print(f"   확인용 시트: {d / 'sheet.png'} — **열어서 컷 순서를 눈으로 읽는다**")


if __name__ == "__main__":
    sys.exit(main())
