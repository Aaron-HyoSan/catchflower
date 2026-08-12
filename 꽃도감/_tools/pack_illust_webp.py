#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""전수 일러스트 2,057장을 **앱이 담을 수 있는 크기**로 바꾼다 (PNG → WebP).

    python3 꽃도감/_tools/pack_illust_webp.py             # 전수 변환
    python3 꽃도감/_tools/pack_illust_webp.py --limit 40   # 앞 40장만 (빠른 확인)
    python3 꽃도감/_tools/pack_illust_webp.py --measure    # 변환 안 하고 손실만 잰다

산출물
    꽃도감/꽃도감_일러스트_전수_webp/NNNN.webp   ← 앱이 쓴다. **번호만 남긴다**

🔴 **생성물이다.** 원본은 `꽃도감_일러스트_전수/`(생성물이고, 그 원본은
   `_tools/확장/gen_sketch_all.py`)다. 이 폴더를 직접 고치지 마라.

## 🔴 왜 필요한가 — **PNG로는 APK에 안 들어간다**

| | 값 |
|---|---|
| 전수 PNG 2,057장 | **130.0 MB** (실측 평균 64.7KB) |
| 일러스트 전 디버그 APK | 105 MB (일러스트 200장 = 9.5MB 포함) |
| **Play 업로드 상한 — AAB** | **150 MB** (기기 1대가 받는 양 기준) |
| **Play 업로드 상한 — APK 직접** | **100 MB** |

즉 PNG를 그대로 넣으면 **225MB가 되어 어느 쪽으로도 올라가지 않는다.** 그리고 이 문제는
**빌드가 성공한다** — 용량은 경고도 예외도 아니다. 커밋하고 나서 스토어에서 막힌다.

WebP로 바꾼 뒤 실측(2026-08-12):

```
디버그 APK  142.3 MB   ← ABI 2개 + R8 미적용 dex 37MB가 들어 있다. **APK 직접 업로드는 못 한다**
디버그 AAB   94.8 MB   ← 제출은 이쪽이다. 기기 1대는 ABI 하나만 받으므로 더 줄어든다
  구성: 일러스트 46.4 · dex 37.0 · lib(arm64) 28.6 · lib(v7a) 22.5 · 기타 11.0
```

🔴 **제출은 AAB로 한다.** 릴리스는 R8이 dex를 줄이고 ABI가 분리되므로 더 작아지지만,
   ⚠️ 릴리스 서명 APK는 **지도가 조용히 죽는다**(카카오 키해시가 이 맥의 debug 키스토어
   SHA-1로 등록돼 있다) — 그래서 이 저장소는 `assembleDebug`만 쓴다.

## 왜 WebP인가 (실측 51장 표본, 2026-08-12)

| 방식 | 2,057장 크기 | 원본 대비 | 512px 평균 오차 | 32px 평균 오차 |
|---|---|---|---|---|
| PNG (원본) | 130.0 MB | 100% | 0 | 0 |
| WebP 무손실 | 71 MB | 55% | 0 | 0 |
| **WebP q80 + alpha_q 100 ← 채택** | **44.6 MB** | **36%** | **2.97 / 255** | **0.08 / 255** |
| WebP q70 | 41.1 MB | 32% | 3.51 | 0.09 |

q70과 q80의 차이는 3.5MB(8%)뿐이라 **더 안전한 쪽(q80)을 쓴다.**

## 🔴 첫 판 검사는 **화질 5에서도 PASS**였다 — 지표가 배경에 희석됐다

돌연변이로 `QUALITY = 5`를 넣고 돌렸더니 그대로 `PASS`가 나왔다. 원인:

```
그림 한 장의 89%가 투명 배경이다 (잉크 픽셀 10.8% — 실측, 진달래)
→ 전 화면 평균을 내면 손상이 1/9로 희석된다
   q5 전체평균 0.70  vs  q80 전체평균 0.32   ← 상한 3.0을 둘 다 통과
   q5 잉크평균 6.46  vs  q80 잉크평균 2.97   ← 이건 갈린다
```

🔴 **"평균을 어디서 내는가"가 지표의 정의다.** 값을 재는 방법이 맞아도 **범위**가
   틀리면 결함이 상한 아래로 숨는다. `verify_32px.py`가 같은 실수를 한 번 했다
   (거기서는 창을 측정 대상에서 뽑아 결함이 창을 같이 옮겼다).

→ 그래서 **잉크 영역**(원본 알파 > 8)에서만 평균을 낸다. ⚠️ 마스크를 **원본에서**
   뽑는다 — 압축 결과에서 뽑으면 알파가 뭉개진 만큼 창이 같이 움직여서 그 손상이
   측정에서 빠진다. 아래 절이 그 경우를 실측했다.

## 🔴 두 번째 판: 상한을 **앞 24장으로 정해서** 틀렸다

`MAX_INK_ERR = 3.3`은 앞 24장 실측(3.26)에 맞춰 정한 값이다. 전수 2,057장을 돌리니
**3.87이 나와 채택한 화질이 자기 검사에 FAIL**했다. 즉 상한이 화질을 거른 게 아니라
**표본이 모집단과 달랐던 것**이다(이 저장소가 이미 아는 실패 —
"파일럿 24장으로 정한 상수는 500장에서 틀린다").

그래서 412장 모집단으로 다시 쟀다 (2026-08-12):

```
q80 잉크오차 · 412장: 평균 3.93 · 표준편차 0.74 · 최악 6.39
  표본 40장의 표본평균: 표준편차 0.110 · 상위 0.1% 4.31   ← 이 위로 새면 **거짓 FAIL**
  표본 120장           : 표준편차 0.058 · 상위 0.1% 4.13
화질별 모집단 평균(120장 스윕): q95 2.71 · q90 3.09 · q85 3.53 · q80 3.93 · q70 4.61 · q60 4.92
```

🔴 상한은 **채택값(3.93)과 그 아래 한 칸(q70 = 4.61) 사이**에 둔다 → `4.35`.
   그리고 표본을 40 → **120장으로 올린다.** 40장이면 흔들림 상한(4.31)이 4.35에
   거의 닿아서 **아무 문제가 없는데도 가끔 빨개진다** — 그런 검사는 곧 무시된다.
   120장이면 흔들림 상한 4.13이라 여유가 0.22 남고, q70은 항상 잡힌다.

⚠️ **상한을 그냥 4.0으로 올리지 않았다.** 그러면 q80(3.93)과의 여유가 0.07뿐이라
   표본만 바뀌어도 빨개지고, 반대로 실제 화질 저하는 q70(4.61)까지 못 잡는다.

## 🔴 세 번째 판: "마스크는 원본에서" 라는 주석이 **한동안 증명되지 않았다**

돌연변이로 마스크를 압축 결과에서 뽑게 바꿨더니 **값이 안 변했다**(3.26 → 3.26).
주석이 지키는 것이 없는데 지킨다고 말하고 있었다. 갈리는 경우를 찾아 실측했다
(`0460_금방망이`, 원본 잉크 60,925화소):

```
결과물                        원본마스크   결과마스크   결과쪽 잉크화소
cwebp alpha_q 100               5.80       5.80       60,925
cwebp alpha_q  20               7.78       7.75       60,090
cwebp alpha_q   0              10.97       9.73       58,430
알파를 전부 잃은 결과(그림 소실)  121.62       0.00            0   🔴
```

즉 **cwebp가 알파를 조금 뭉개는 정도로는 두 방식이 안 갈린다** — 그래서 그 돌연변이가
초록이었던 것이고, 내 주석은 틀리지 않았지만 **약한 경우로 시험했던 것**이다.
결정적인 경우는 알파가 **크게** 사라질 때다: 그림이 통째로 없어진 산출물이
결과마스크로는 **0.00 = 만점**을 받는다(잴 화소가 0개니까). 원본마스크는 121.62로
잡는다. ⚠️ 이건 가정이 아니다 — 변환 파이프라인이 배경을 합성해 버리거나
알파 없는 산출물이 섞이면 실제로 나오는 모양이고, 그때 화면 증상은
**"그 종만 빈 칸"** 이라 "아직 안 온 그림"과 구별되지 않는다.

🔴 **`-alpha_q 100`이 필수다.** 알파를 손실 압축하면 투명 배경 경계에 반투명 띠가
   남고, 도감 셀의 원형 배경 위에서 **꽃 주위에 옅은 사각 테두리**로 보인다.
   ⚠️ 이건 512px 원본을 눈으로 봐도 거의 안 보인다 — 셀 크기로 줄여야 드러난다.

## 🔴 minSdk 26이라 WebP를 쓸 수 있다

투명 WebP는 **Android 4.3(API 18)** 부터, 무손실/알파는 4.2.1+다. 이 앱은
`minSdk = 26`이므로 안전하다. ⚠️ 이걸 확인하지 않고 WebP로 갈면 **오래된 기기에서만
빈 칸**이 되고, 이 맥의 에뮬레이터(API 36)로는 영원히 안 보인다.

## 이 스크립트가 세는 것 (조용히 틀리는 자리)

| 자리 | 안 세면 | 증상 |
|---|---|---|
| 변환 실패 1장 | 조용히 빠진다 | 그 종만 플레이스홀더 — "아직 안 온 그림"과 구별 불가 |
| 번호 중복 | 하나가 이긴다 | 어느 그림이 들어갔는지 알 수 없다 |
| 알파 손실 | 없음 | 셀에서 꽃 주위에 옅은 테두리 |
| 크기 512 아님 | 없음 | `inSampleSize` 가정이 깨져 캐시 키와 실제 크기가 어긋난다 |
| **디코딩 자체 실패** | 파일은 있다 | 앱에서 그 칸이 빈다. 그래서 **다시 열어 본다** |
| **화질 저하** | 배경에 희석돼 안 보인다 | 위 절 — 잉크 영역에서만 잰다 |

🔴 **파일 크기만 세지 않는다.** "변환됐다"와 "읽을 수 있다"는 다르다 —
   `cwebp`가 0바이트 파일을 남기고 성공 코드를 줄 수 있고, 그러면 검사는
   `2057장 생성`이라고 말한다. 그래서 변환 직후 **Pillow로 다시 열어** 크기·알파를
   확인하고, 표본은 **원본과 화소를 대조**한다.
"""
# ⚠️ 이 맥의 시스템 파이썬은 **3.9**다. `str | None` 같은 표기는 3.10부터
#    실행 시점에 평가되므로 이것 없이 쓰면 `TypeError`로 죽는다(실제로 죽었다).
from __future__ import annotations

import argparse
import concurrent.futures as futures
import os
import pathlib
import re
import shutil
import subprocess
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit('Pillow가 필요하다: python3 -m pip install Pillow')

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / '꽃도감_일러스트_전수'
OUT = ROOT / '꽃도감_일러스트_전수_webp'

# 🔴 빌드(`SyncSharedAssets`)의 정규식과 **자릿수가 같아야 한다.**
NAME_RE = re.compile(r'^(\d{3,4})_.*\.png$')

CWEBP = '/opt/homebrew/bin/cwebp'
QUALITY = 80
# 🔴 알파는 무손실이다. 위 주석의 이유 — 줄여도 용량이 거의 안 줄고 테두리가 생긴다.
ALPHA_QUALITY = 100
SIZE = (512, 512)

# 화소 대조 표본 수. 전수를 대조하면 2,057번 디코딩·비교라 오래 걸린다.
# 🔴 40이었다. 40장 표본평균의 흔들림(표준편차 0.110 · 상위 0.1% 4.31)이 상한 4.35에
#    거의 닿아서 **정상인데도 가끔 빨개진다.** 120장이면 흔들림 상한이 4.13이다.
SAMPLE = 120

# 🔴 **잉크 영역** 평균 오차 상한. 전 화면 평균이 아니다 — 위 절의 그 이유.
#    실측 모집단 평균(412장): q95 2.71 · q90 3.09 · q85 3.53 · **q80 3.93** · q70 4.61.
#    채택값 3.93과 그 아래 한 칸 4.61 사이에 둔다. ⚠️ 앞 24장으로 3.3을 정했다가
#    전수에서 3.87이 나와 자기 검사에 FAIL했다 — 위 "두 번째 판" 절.
MAX_INK_ERR = 4.35
# 32px(도감 셀)에서 허용하는 평균 오차. 실측 q80 = 0.08 · q5 = 0.30.
# ⚠️ 32px는 축소가 오차를 다시 평균 내므로 **혼자서는 화질을 못 가른다** —
#    잉크 상한이 주 검사이고 이쪽은 "축소해도 남는 손상"을 잡는 보조다.
MAX_MEAN_ERR_32 = 0.2
# 잉크 마스크 경계. 이보다 진한 원본 화소만 잰다.
INK_ALPHA = 8
# 종이색 — 합성해서 비교할 때 쓴다. 투명 위 비교는 알파 0 영역의 RGB가
# 인코더마다 달라서 **의미 없는 큰 차이**가 나온다(WebP는 그 자리를 자유롭게 채운다).
PAPER = (242, 240, 236)


def encode(src: pathlib.Path, dst: pathlib.Path) -> str | None:
    """한 장 변환. 실패 이유 문자열 또는 None(성공)."""
    r = subprocess.run(
        [CWEBP, '-quiet', '-q', str(QUALITY), '-alpha_q', str(ALPHA_QUALITY),
         str(src), '-o', str(dst)],
        capture_output=True, text=True,
    )
    if r.returncode != 0:
        return f'cwebp {r.returncode}: {r.stderr.strip()[:80]}'
    if not dst.exists() or dst.stat().st_size == 0:
        return '0바이트 — cwebp가 성공을 말했지만 파일이 비었다'
    # 🔴 다시 열어 본다. "만들어졌다"와 "읽힌다"는 다르다.
    try:
        im = Image.open(dst)
        im.load()
    except Exception as e:  # noqa: BLE001 — 무슨 예외든 그 장은 못 쓴다
        return f'다시 열 수 없다: {type(e).__name__}'
    if im.size != SIZE:
        return f'크기가 {im.size[0]}×{im.size[1]}다'
    if im.mode not in ('RGBA', 'LA'):
        return f'알파가 없다({im.mode}) — 셀 배경 위에 흰 사각형이 된다'
    return None


def composite(im: Image.Image) -> Image.Image:
    """종이색 위에 얹는다. 알파 0 영역의 RGB는 인코더 자유이므로 비교에서 빼야 한다."""
    rgba = im.convert('RGBA')
    bg = Image.new('RGB', rgba.size, PAPER)
    bg.paste(rgba, (0, 0), rgba)
    return bg


def pixel_error(a_path: pathlib.Path, b_path: pathlib.Path) -> tuple:
    """(잉크영역 평균오차, 32px 평균오차, 잉크 비율). 0~255 스케일.

    🔴 **잉크 마스크는 [a_path](원본)에서 뽑는다.** 압축 결과에서 뽑으면
       알파가 뭉개진 만큼 마스크가 같이 움직여서 **그 손상이 측정에서 빠진다** —
       결함이 자기를 재는 창을 옮기는 종류다(`verify_32px.py`가 그렇게 틀렸다).
    """
    orig = Image.open(a_path)
    a = composite(orig)
    b = composite(Image.open(b_path))
    ba, bb = a.tobytes(), b.tobytes()

    alpha = orig.convert('RGBA').getchannel('A').getdata()
    total = 0
    count = 0
    for i, av in enumerate(alpha):
        if av > INK_ALPHA:
            j = i * 3
            total += (abs(ba[j] - bb[j]) + abs(ba[j + 1] - bb[j + 1])
                      + abs(ba[j + 2] - bb[j + 2]))
            count += 3
    # 잉크가 아예 없는 그림은 `verify_illust.py`가 먼저 잡는다(알파 검사).
    ink = total / count if count else 0.0

    a32 = a.resize((32, 32), Image.LANCZOS).tobytes()
    b32 = b.resize((32, 32), Image.LANCZOS).tobytes()
    mean32 = sum(abs(x - y) for x, y in zip(a32, b32)) / len(a32)
    return ink, mean32, count / 3 / len(alpha)


def self_check() -> str | None:
    """🔴 **검사기가 도는지 검사한다.** 실패 이유 또는 None.

    잉크 마스크를 압축 결과에서 뽑는 실수는 **cwebp의 정상 손실로는 안 드러난다**
    (실측: alpha_q 20에서 7.78 vs 7.75). 갈리는 것은 알파가 크게 사라질 때이고,
    그때 결과마스크는 **0.00 = 만점**을 준다 — 그림이 통째로 없어진 파일이 PASS한다.

    그래서 매번 **그 경우를 합성해서** 실제로 잡히는지 본다. 이게 없으면
    [pixel_error]를 잘못 고쳐도 전수 변환이 그냥 PASS로 끝난다.
    """
    blank = OUT / '_selfcheck.webp'
    try:
        Image.new('RGBA', SIZE, PAPER + (0,)).save(blank)   # 완전 투명 = 그림 소실
        ref = Image.new('RGBA', SIZE, (0, 0, 0, 255))       # 전면 잉크
        ref_path = OUT / '_selfcheck_src.png'
        ref.save(ref_path)
        ink, _, ratio = pixel_error(ref_path, blank)
        if ratio < 0.99:
            return f'대조군 잉크 비율이 {ratio:.2f}다 — 마스크가 원본을 안 읽는다'
        if ink <= MAX_INK_ERR:
            return (f'그림이 사라진 파일이 오차 {ink:.2f}로 통과한다 '
                    f'(상한 {MAX_INK_ERR}) — 마스크를 압축 결과에서 뽑고 있다')
    finally:
        for p in (blank, OUT / '_selfcheck_src.png'):
            if p.exists():
                p.unlink()
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument('--limit', type=int, default=0, help='앞 N장만')
    ap.add_argument('--measure', action='store_true',
                    help='이미 변환된 결과의 손실만 잰다(변환하지 않는다)')
    args = ap.parse_args()

    if not SRC.is_dir():
        sys.exit(f'원본 폴더가 없다: {SRC}\n'
                 f'  → python3 꽃도감/_tools/확장/gen_sketch_all.py 를 먼저 돌린다')
    if not os.path.exists(CWEBP):
        sys.exit(f'cwebp가 없다: {CWEBP}\n  → brew install webp')

    src_files = sorted(f for f in SRC.iterdir()
                       if f.is_file() and NAME_RE.match(f.name))
    if args.limit:
        src_files = src_files[:args.limit]
    if not src_files:
        sys.exit(f'{SRC}에 `번호_이름.png` 파일이 없다')

    # 번호 중복은 여기서 세운다 — 조용히 하나가 이기면 어느 그림인지 알 수 없다.
    seen: dict[int, str] = {}
    dup = []
    for f in src_files:
        no = int(NAME_RE.match(f.name).group(1))
        if no in seen:
            dup.append(f'{no}: {seen[no]} / {f.name}')
        seen[no] = f.name
    if dup:
        print('🔴 도감번호 중복 — 어느 그림이 앱에 들어갈지 알 수 없다:')
        for d in dup[:10]:
            print(f'    {d}')
        return 1

    plan = [(f, OUT / ('%04d.webp' % int(NAME_RE.match(f.name).group(1))))
            for f in src_files]

    if not args.measure:
        # 🔴 폴더를 **비우고** 시작한다. 원본에서 사라진 종의 옛 webp가 남으면
        #    "2,057장 있다"가 맞는데 그중 한 장은 지워진 종의 그림이다.
        if OUT.exists():
            shutil.rmtree(OUT)
        OUT.mkdir(parents=True)
        print(f'변환 {len(plan)}장 → {OUT.name}/  (q{QUALITY} · alpha_q {ALPHA_QUALITY})')
        errors = []
        with futures.ThreadPoolExecutor(max_workers=8) as ex:
            for (src, dst), err in zip(plan, ex.map(lambda p: encode(*p), plan)):
                if err:
                    errors.append(f'{src.name}: {err}')
        if errors:
            print(f'\n🔴 변환 실패 {len(errors)}장 — 그 종은 앱에서 빈 칸이 된다:')
            for e in errors[:10]:
                print(f'    {e}')
            return 1
    else:
        missing = [d.name for _, d in plan if not d.exists()]
        if missing:
            print(f'🔴 --measure인데 결과가 {len(missing)}장 없다: {missing[:5]}')
            return 1

    # ── 세기 ────────────────────────────────────────────────
    png_bytes = sum(s.stat().st_size for s, _ in plan)
    webp_bytes = sum(d.stat().st_size for _, d in plan)
    n = len(plan)
    print(f'\n  PNG  {png_bytes / 1048576:6.1f} MB  (평균 {png_bytes / n / 1024:.1f} KB)')
    print(f'  WebP {webp_bytes / 1048576:6.1f} MB  (평균 {webp_bytes / n / 1024:.1f} KB)'
          f'  ← 원본의 {webp_bytes / png_bytes * 100:.0f}%')

    # ── 검사기 자체 검사 ────────────────────────────────────
    # 🔴 화소를 재기 **전에** 잰다. 검사기가 고장 난 채로 2,057장을 통과시키면
    #    그 결과물이 APK에 들어가고, 화면에서는 빈 칸으로만 보인다.
    broken = self_check()
    if broken:
        print(f'\n🔴 검사기가 고장 났다 — {broken}')
        print('   → pixel_error()의 마스크 출처를 확인한다(원본이어야 한다).')
        return 1

    # ── 화소 대조 (표본) ────────────────────────────────────
    # 🔴 **크기만 보고 끝내지 않는다.** 손실 압축이 얼마나 망가뜨렸는지는
    #    파일 크기에 안 나온다. 32px는 도감 셀이 실제로 쓰는 크기다.
    step = max(1, n // SAMPLE)
    sample = plan[::step][:SAMPLE]
    worst = []
    with futures.ThreadPoolExecutor(max_workers=8) as ex:
        for (src, _), (ink, m32, ratio) in zip(sample,
                                               ex.map(lambda p: pixel_error(*p), sample)):
            worst.append((ink, m32, ratio, src.name))
    worst.sort(reverse=True)
    ink_all = sum(w[0] for w in worst) / len(worst)
    mean32_all = sum(w[1] for w in worst) / len(worst)
    ratio_all = sum(w[2] for w in worst) / len(worst)
    print(f'\n  화소 대조 표본 {len(worst)}장 (종이색 #F2F0EC 위에 합성해서 비교)')
    print(f'    잉크영역 평균 오차 {ink_all:5.2f} / 255   ← 주 검사'
          f'  (잉크가 화면의 {ratio_all * 100:.1f}%뿐이라 전 화면 평균은 희석된다)')
    print(f'     32px  평균 오차 {mean32_all:5.2f} / 255   ← 도감 셀이 쓰는 크기')
    print('    가장 나쁜 3장: ' +
          ', '.join(f'{nm}({ink:.2f})' for ink, _, _, nm in worst[:3]))

    fail = []
    if ink_all > MAX_INK_ERR:
        fail.append(f'잉크영역 {ink_all:.2f} > {MAX_INK_ERR}')
    if mean32_all > MAX_MEAN_ERR_32:
        fail.append(f'32px {mean32_all:.2f} > {MAX_MEAN_ERR_32}')
    print()
    if fail:
        print(f'🔴 FAIL — {" · ".join(fail)}')
        print(f'   QUALITY({QUALITY})를 올리거나, 원본이 바뀐 것인지 본다.')
        return 1
    print(f'PASS — {n}장 · {webp_bytes / 1048576:.1f} MB · '
          f'잉크영역 오차 {ink_all:.2f}/255 · 32px {mean32_all:.2f}/255')
    return 0


if __name__ == '__main__':
    sys.exit(main())
