#!/usr/bin/env python3
"""trim_play_video.py 검사 — **컷 목록이 조용히 틀리는 것을 잡는다.**

**왜 있나.** 이 도구가 틀리는 방식은 "실패"로 안 나온다.

| 조용히 틀리는 방식 | 어떻게 보이나 |
|---|---|
| `CUTS`가 원본보다 뒤를 가리킨다(다시 녹화해서 길이가 줄었다) | `insertTimeRange`가 **있는 만큼만 붙여** 짧은 영상이 성공으로 나온다 |
| 컷 하나를 빠뜨렸다 | `passthrough 성공` + 그럴듯한 초 수. **실제로 분석중 컷이 빠진 영상을 만들었다** |
| 구간이 겹치거나 순서가 뒤바뀌었다 | 같은 화면이 두 번 나오는데 길이는 정상 |
| 합계가 요건을 벗어났다 | 심사 요건 위반인데 파일은 완벽하다 |

**그래서 여기서 재는 것은 산술과 목록이다.** "무슨 화면이 들어갔나"는 산술로 못 잰다 —
그건 `trim_play_video.py`가 만드는 프레임 시트를 **사람이 읽어야** 한다(그 검사도 아래에서
"시트를 만드는 단계가 빠지지 않았나"로만 잰다).

    python3 제출물/_tools/test_trim_play_video.py
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

import trim_play_video as T  # noqa: E402

fails: list[str] = []


def check(label: str, ok: bool, detail: str = "") -> None:
    if not ok:
        fails.append(f"{label}{chr(10) + '  ' + detail if detail else ''}")


# ── 컷 목록 자체 ──────────────────────────────────────────────────────
check("컷이 있다", len(T.CUTS) > 0)
for s, e, what in T.CUTS:
    check(f"{s}~{e} 는 앞뒤가 바르다", e > s, f"끝({e})이 시작({s})보다 뒤여야 한다")
    check(f"{s}~{e} 에 설명이 있다", bool(what.strip()))

starts = [s for s, _, _ in T.CUTS]
check("컷이 시간 순서다", starts == sorted(starts), f"{starts}")

# 🔴 **겹치면 같은 화면이 두 번 나온다** — 길이는 정상이라 눈치채기 어렵다.
for (s1, e1, _), (s2, e2, _) in zip(T.CUTS, T.CUTS[1:]):
    check(f"{s1}~{e1} 와 {s2}~{e2} 가 겹치지 않는다", s2 >= e1,
          "다음 컷이 앞 컷 안에서 시작한다 — 같은 화면이 두 번 나온다")

# ── 제출 요건 ─────────────────────────────────────────────────────────
total = sum(e - s for s, e, _ in T.CUTS)
check(f"합계 {total}초가 요건 30~60초 안이다", 30 <= total <= 60)

# ── 원본 길이 안에 들어가는가 ─────────────────────────────────────────
# 🔴 **이것이 이 파일의 핵심이다.** 다시 녹화하면 원본 길이·화면 전환 시각이 전부 밀리는데,
#    범위를 넘긴 컷은 **오류가 아니라 "있는 만큼만" 붙는다** — 짧은 영상이 성공으로 나온다.
#
# 🔴 **처음에는 이 검사를 `if T.SRC.exists():` 로 감쌌고, 그게 구멍이었다.**
#    돌연변이 테스트에서 마지막 컷을 원본 밖(200~205초)으로 옮겼는데 **실패 0개**가 나왔다 —
#    복사본 디렉터리에서 돌리니 파일이 없어 **검사가 스스로 꺼진 것이다.** 파일 유무를
#    조건으로 쓰면 검사는 "없을 때 통과"가 된다. 그래서 상수(`SRC_SECONDS`)로 먼저 잰다.
last = max(e for _, e, _ in T.CUTS)
check(f"마지막 컷 {last}초가 원본 {T.SRC_SECONDS:.0f}초(SRC_SECONDS) 안이다",
      last <= T.SRC_SECONDS,
      "다시 녹화했으면 --frames-only 로 경계를 다시 읽고 CUTS·SRC_SECONDS를 고친다")

# 파일이 있으면 **상수가 실제와 맞는지**까지 잰다. 상수만 믿으면 다시 녹화한 뒤
# 상수를 안 고쳐도 통과한다 — 그러면 위 검사가 낡은 값을 재게 된다.
if T.SRC.exists():
    import importlib.util

    spec = importlib.util.spec_from_file_location(
        "rec", pathlib.Path(__file__).parent / "record_play_video.py")
    rec = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rec)
    dur = rec.mp4_duration(T.SRC)
    check(f"SRC_SECONDS({T.SRC_SECONDS:.0f})가 실제 원본({dur:.1f}초)과 맞다",
          abs(dur - T.SRC_SECONDS) < 1.0,
          "다시 녹화했다 — CUTS 초가 전부 밀렸을 것이다. --frames-only 로 다시 읽는다")
else:
    # 🔴 건너뛰지 않는다. **파일이 없다는 것 자체를 보고한다** — 조용한 skip이 위 구멍이었다.
    print(f"⚠️ 원본 파일이 없다: {T.SRC}")
    print("   상수(SRC_SECONDS)로 범위는 쟀지만 **실제 파일과의 일치는 재지 못했다.**")
    print("   (→ python3 제출물/_tools/record_play_video.py)")

# ── 확인 시각이 결과물 안에 있는가 ────────────────────────────────────
# 시트에 찍을 시각이 영상 끝을 넘으면 그 칸이 비는데, **시트는 정상으로 보인다.**
check(f"확인 시각 최대 {max(T.CHECK_AT)}초가 합계 {total}초 안이다", max(T.CHECK_AT) < total)
check("확인 시각이 순서대로다", T.CHECK_AT == sorted(T.CHECK_AT))

# ── 빠지면 안 되는 컷 ─────────────────────────────────────────────────
# 🔴 **분석중 로딩은 콘티 5-1절이 "빼거나 배속하지 말 것"으로 못 박은 컷이다.**
#    3~5초 로딩이 "AI가 실제로 서버에 물어보고 있다"는 유일한 시각적 증거다.
#    첫 편집본이 이걸 빠뜨렸고 **43.0초 · 성공이 초록으로 떴다.**
descs = " / ".join(w for _, _, w in T.CUTS)
for must in ("분석중", "지도", "랭킹", "도감"):
    check(f"'{must}' 컷이 있다", must in descs, f"컷 설명: {descs}")

# ── 확인 단계가 빠지지 않았나 ─────────────────────────────────────────
# 🔴 자른 뒤 프레임을 다시 뽑는 단계를 지우면 **이 파일의 검사가 전부 통과한 채로**
#    "무슨 화면이 들어갔는지 아무도 안 본" 영상이 제출된다.
src = (pathlib.Path(__file__).parent / "trim_play_video.py").read_text()
check("자른 뒤 프레임을 다시 뽑는다", re.search(r'frames\(DST', src) is not None,
      "trim_play_video.py 가 DST 로 frames() 를 부르지 않는다 — 확인 단계가 사라졌다")
check("요건 범위를 스크립트가 잰다", "30 <= total <= 60" in src)
check("재인코딩하지 않는다",
      "Passthrough" in (pathlib.Path(__file__).parent / "trim_play_video.swift").read_text(),
      "passthrough가 아니면 화질이 손실되고 판별 결과를 가공한 것이 된다")

# ── 결과 ──────────────────────────────────────────────────────────────
if fails:
    print(f"❌ {len(fails)}개 실패\n")
    for f in fails:
        print(f"  · {f}")
    sys.exit(1)
print(f"✅ 통과 · 컷 {len(T.CUTS)}개 · 합계 {total}초 · 확인 시각 {len(T.CHECK_AT)}곳")
