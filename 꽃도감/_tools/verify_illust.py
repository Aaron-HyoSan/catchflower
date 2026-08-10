#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""납품된 일러스트가 규격을 지키는지 검사한다 — `꽃도감/일러스트_납품_규격.md`가 원본.

    python3 꽃도감/_tools/verify_illust.py

🔴 **이 검사가 없으면 규격을 어긋난 장이 그냥 화면에 나온다.** 예외가 안 난다:
   - 512가 아닌 장 → `inSampleSize = 512 / bucket`이 가정을 깨서 **크기가 어긋난 채** 그려진다
   - 배경이 흰 장  → 투명 PNG들 사이에서 **흰 사각형**으로 보인다
   - 이름으로 매칭 → macOS NFD 때문에 **전량 못 찾는데 눈으로는 같은 글자**다

각 검사에 **어떤 경우 빨개지는지** 적는다. 못 적는 검사는 넣지 않는다.
"""
import collections
import pathlib
import re
import sys
import unicodedata

try:
    from PIL import Image
except ImportError:
    sys.exit('Pillow가 필요하다: python3 -m pip install Pillow')

ROOT = pathlib.Path(__file__).resolve().parents[1]
# 폴더를 인자로 받는다. 기본은 기존 200장 폴더.
#   python3 꽃도감/_tools/verify_illust.py 꽃도감_일러스트_확장
ILLUST = ROOT / (sys.argv[1] if len(sys.argv) > 1 else '꽃도감_일러스트')
if not ILLUST.is_absolute():
    ILLUST = pathlib.Path(sys.argv[1]) if not ILLUST.is_dir() else ILLUST
# 🔴 대조 목록은 **번호를 부여한 확장 CSV**다. 후보 CSV에는 신규종 번호가 비어 있어
#    '목록에 없는 번호'가 500건 전부 FAIL로 뜬다 — 검사가 아니라 대조 대상이 틀린 것이다.
CSV = ROOT / '꽃목록_확장_2057종.csv'
if not CSV.exists():
    CSV = ROOT / '꽃목록_후보_2000종.csv'

SIZE = (512, 512)
# 🔴 빌드(`SyncSharedAssets`)의 정규식과 **같아야 한다**. 여기만 넓히면
#    검사는 통과하는데 빌드가 그 파일을 조용히 건너뛴다.
#    3자리·4자리를 함께 받는다 — 200종 시절 파일이 섞여 있을 수 있다.
NAME_RE = re.compile(r'^(\d{3,4})_.*\.png$')
EDGE_ALPHA = 10   # 이보다 진하면 '그림이 변에 닿았다'로 본다


def main():
    if not ILLUST.is_dir():
        sys.exit(f'폴더가 없다: {ILLUST}')

    # `_`로 시작하는 파일·폴더는 납품물이 아니다(`_스펙.csv`, `_svg/`).
    # 🔴 그래도 `.psd`·`.ai` 같은 원본이 섞이면 여전히 빨개진다 — 그게 이 검사의 목적이다.
    #    `_`를 붙이면 검사를 피할 수 있으니, 붙이는 것은 **메타 파일에만** 한다.
    files = sorted(f for f in ILLUST.iterdir()
                   if f.is_file() and not f.name.startswith('_'))
    png = [f for f in files if f.suffix.lower() == '.png']
    fails = []

    def chk(name, bad, why):
        """bad가 비어 있으면 OK. 비어 있지 않으면 그 목록이 실패 근거다."""
        if bad:
            fails.append(name)
            print(f'  FAIL {name} — {len(bad)}건')
            for x in list(bad)[:8]:
                print(f'         {x}')
            if len(bad) > 8:
                print(f'         … 외 {len(bad)-8}건')
            print(f'         → {why}')
        else:
            print(f'  OK   {name}')

    print(f'검사 대상 {len(png)}장  ({ILLUST})\n')

    # ── 파일명 ──────────────────────────────────────────────
    # red: 번호 접두사가 없거나 자릿수가 틀린 파일. 빌드가 **조용히 건너뛴다**.
    chk('파일명이 `번호_이름.png`',
        [f.name for f in png if not NAME_RE.match(f.name)],
        '빌드 정규식이 안 잡아 그 종의 칸이 빈다(예외 없음)')

    ok = [f for f in png if NAME_RE.match(f.name)]
    ids = collections.defaultdict(list)
    for f in ok:
        ids[int(NAME_RE.match(f.name).group(1))].append(f.name)

    # red: 같은 번호가 둘. 빌드는 check()로 세우지만 여기서 먼저 알린다.
    chk('도감번호 중복 0',
        [f'{i}: {sorted(v)}' for i, v in sorted(ids.items()) if len(v) > 1],
        '어느 쪽이 앱에 들어갔는지 알 수 없다 — 빌드가 실패한다')

    # red: png가 아닌 원본(.psd/.ai)이 섞였다.
    chk('png만 있다',
        [f.name for f in files if f.suffix.lower() != '.png'],
        '빌드는 무시하지만 저장소가 커진다')

    # ── 이미지 실체 ─────────────────────────────────────────
    wrong_size, wrong_mode, no_alpha, edge, broken = [], [], [], [], []
    for f in ok:
        try:
            im = Image.open(f)
            im.load()
        except Exception as e:
            broken.append(f'{f.name}: {type(e).__name__}')
            continue
        if im.size != SIZE:
            wrong_size.append(f'{f.name}: {im.size[0]}×{im.size[1]}')
        if im.mode not in ('RGBA', 'LA'):
            wrong_mode.append(f'{f.name}: {im.mode}')
            continue
        a = im.getchannel('A')
        if a.getextrema()[0] == 255:
            no_alpha.append(f.name)
            continue
        w, h = im.size
        px = a.load()
        touch = [s for s, hit in (
            ('위', any(px[x, 0] > EDGE_ALPHA for x in range(w))),
            ('아래', any(px[x, h - 1] > EDGE_ALPHA for x in range(w))),
            ('왼', any(px[0, y] > EDGE_ALPHA for y in range(h))),
            ('오른', any(px[w - 1, y] > EDGE_ALPHA for y in range(h))),
        ) if hit]
        if touch:
            edge.append(f'{f.name}: {"·".join(touch)} 변에 닿음')

    # red: 읽히지 않는 파일(전송 중 잘림 등). 앱에서는 그 칸만 빈다.
    chk('열린다', broken, '앱은 IOException을 삼키고 그 칸만 비운다 — 증상이 약하다')

    # red: 512가 아닌 장. `inSampleSize = 512 / bucket` 가정이 깨진다.
    chk(f'크기 {SIZE[0]}×{SIZE[1]}', wrong_size,
        '디코딩 축소 비율이 어긋나 크기가 틀린 채 그려진다(예외 없음)')

    # red: 알파 채널이 없는 모드(RGB/P). 배경이 불투명해진다.
    chk('알파 채널 있음(RGBA)', wrong_mode, '배경이 흰/검은 사각형으로 보인다')

    # red: 알파는 있으나 전부 255 = 사실상 배경이 꽉 찬 그림.
    chk('투명 배경을 실제로 쓴다', no_alpha,
        '알파는 있는데 전부 불투명하다 — 그리드에서 사각형으로 보인다')

    # red: 그림이 캔버스 변에 닿음 → 셀에서 잘려 보인다.
    chk('네 변에 여백 있음', edge, '도감 그리드에서 잘려 보인다')

    # ── 목록과의 대조 ───────────────────────────────────────
    if CSV.exists():
        import csv as _csv
        import io as _io
        rows = list(_csv.DictReader(_io.open(CSV, encoding='utf-8-sig')))
        want = {int(r['도감번호']) for r in rows if r['도감번호']}
        extra = sorted(set(ids) - want)
        # red: 목록에 없는 번호의 그림. 앱이 안 읽으니 **아무 증상이 없다**.
        chk('목록에 없는 번호 0', [str(i) for i in extra],
            '앱이 읽지 않아 증상이 없다 — 번호를 잘못 붙인 것이다')
        missing = sorted(want - set(ids))
        print(f'\n  참고: 목록 {len(want)}종 중 그림 {len(ids)}장 · '
              f'미납품 {len(missing)}종'
              + (f' (예: {missing[:6]}…)' if missing else ''))
        # 미납품은 FAIL이 아니다 — 배치 납품이 정상이다. 대신 개수를 반드시 찍는다.

    nfd = sum(1 for f in png if unicodedata.normalize('NFC', f.name) != f.name)
    print(f'  참고: 파일명이 NFD(자모분리)인 것 {nfd}/{len(png)}장 — '
          f'번호를 키로 쓰므로 문제되지 않는다')

    if png:
        tot = sum(f.stat().st_size for f in png)
        print(f'  참고: 평균 {tot/len(png)/1024:.1f} KB · 총 {tot/1048576:.1f} MB')

    print(f'\n판정: {"FAIL " + str(len(fails)) + "건" if fails else "PASS"}')
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
