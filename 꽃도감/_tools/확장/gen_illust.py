#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""우선순위 500종의 꽃 일러스트를 생성한다 (SVG + PNG).

    python3 꽃도감/_tools/확장/gen_illust.py            # 500종
    python3 꽃도감/_tools/확장/gen_illust.py --limit 30  # 앞 30종만

산출물
    꽃도감/꽃도감_일러스트_확장/NNNN_이름.svg   ← 원본(벡터). Figma에 올라간다
    꽃도감/꽃도감_일러스트_확장/NNNN_이름.png   ← 512×512 RGBA. 앱이 쓴다
    꽃도감/꽃도감_일러스트_확장/_스펙.csv        ← 종별 색·형태와 **그 근거**

🔴 **생성물이다. 결과 파일을 직접 수정하지 마라** — 다시 돌리면 덮인다.
   고칠 것은 `flower_art.py`(형태)와 `species_color.py`(색)다.

🔴 **번호는 `꽃목록_확장_2057종.csv`의 `도감번호`다.** 순위가 아니다.
   관찰건수 순으로 500종을 고르지만, 파일명 번호는 도감번호를 쓴다 —
   `assign_ids.py`가 그 번호를 `discoveries.flower_id`와 맞춰 놨다.

## 결정론

같은 입력이면 같은 그림이 나와야 한다 (`Math.random` 함정과 같은 이유 —
돌릴 때마다 달라지면 검수한 그림과 납품한 그림이 다르다).
그래서 종별 난수 시드를 **도감번호로 고정**한다. 시계·전역 난수를 쓰지 않는다.
"""
import argparse
import csv
import inspect
import io
import os
import random
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import flower_art as FA
from flower_art import Art, Flower, FAMILY_FORM, FORM_FN
from species_color import resolve

BASE = '/Users/kimhyosan/game-project/꽃도감/'
SRC = BASE + '꽃목록_확장_2057종.csv'
OUT = BASE + '꽃도감_일러스트_확장/'
# 🔴 SVG를 PNG와 같은 폴더에 두면 `verify_illust.py`의 'png만 있다'가 FAIL한다.
#    벡터 원본은 Figma에 올릴 것이라 버릴 수 없다 — 하위 폴더로 가른다.
SVG_OUT = OUT + '_svg/'
SPEC = OUT + '_스펙.csv'

TOP_N = 500

# 🔴 SVG→PNG 렌더러. cairosvg(파이썬)는 시스템 python에서 SIP가 `DYLD_*`를
#    지워 libcairo를 못 찾는다. rsvg-convert는 바이너리라 그 문제가 없다.
#    실측: 512×512 RGBA + 배경 알파 0을 낸다(`t.png` 확인).
RSVG = '/opt/homebrew/bin/rsvg-convert'

# 잎 종류 → Flower.leaves의 kind
LEAF_KIND = {'oval': 'oval', 'lance': 'lance', 'blade': 'blade',
             'lobed': 'lobed', 'heart': 'heart', 'compound': 'compound'}

# 목본(나무)으로 그릴 과 — 줄기가 갈색이다.
# 🔴 초본을 갈색 줄기로 그리면 실물과 안 맞는다. 과로 일괄 판정하되
#    `이름에 '나무'/'덩굴'`도 함께 본다 (같은 과에 나무와 풀이 섞여 있다).
WOODY_FAMILIES = {
    'Aceraceae', 'Celastraceae', 'Salicaceae', 'Rhamnaceae', 'Lauraceae',
    'Oleaceae', 'Vitaceae', 'Rutaceae', 'Anacardiaceae', 'Aquifoliaceae',
    'Theaceae', 'Magnoliaceae', 'Ericaceae', 'Ebenaceae', 'Simaroubaceae',
    'Symplocaceae', 'Styracaceae', 'Elaeagnaceae', 'Tiliaceae', 'Cornaceae',
    'Actinidiaceae', 'Schisandraceae', 'Menispermaceae', 'Lardizabalaceae',
    'Berberidaceae', 'Staphyleaceae', 'Alangiaceae', 'Meliaceae',
    'Daphniphyllaceae', 'Sabiaceae', 'Pittosporaceae', 'Myrsinaceae',
    'Caprifoliaceae', 'Araliaceae', 'Verbenaceae', 'Adoxaceae',
}
WOODY_WORDS = ('나무', '버들', '조희풀')
# 🔴 '덩굴'은 목본 단어가 아니다 — 댕댕이덩굴은 목본이지만
#    메꽃과 덩굴은 초본이다. 과로 판정하게 넘긴다.
# 🔴 이름 우선이면 안 된다: '괭이밥'·'멍석딸기'는 초본인데
#    파일럿에서 갈색 줄기로 나왔다. 과를 먼저 본다.
HERB_WORDS = ('풀', '꽃', '나물', '냉이', '취', '밥', '딸기', '쑥', '미나리',
              '제비', '민들레', '뱀', '고사리')


def is_woody(name, family):
    """목본(갈색 줄기)인지. 판정 순서가 결과를 바꾼다.

    🔴 초본을 갈색 줄기로 그리면 실물과 안 맞는다. 규격 검사는 통과한다 —
       그림을 봐야 보인다. 파일럿에서 괭이밥·멍석딸기가 그랬다.
    """
    if any(w in name for w in HERB_WORDS):
        return False
    if any(w in name for w in WOODY_WORDS):
        return True
    return family in WOODY_FAMILIES


def _edge_touch(path, thr=10):
    """네 변 중 그림이 닿은 변. 빈 문자열이면 통과."""
    from PIL import Image
    im = Image.open(path).convert('RGBA')
    a = im.getchannel('A')
    w, h = im.size
    px = a.load()
    hit = [s for s, y in (
        ('위', any(px[x, 0] > thr for x in range(w))),
        ('아래', any(px[x, h - 1] > thr for x in range(w))),
        ('왼', any(px[0, y] > thr for y in range(h))),
        ('오른', any(px[w - 1, y] > thr for y in range(h))),
    ) if y]
    return '·'.join(hit)


def pick(rows):
    """관찰건수 상위 TOP_N. 동수는 이름순 — 순서가 흔들리지 않게."""
    new = [r for r in rows if r['기존200종'] != 'Y']
    new.sort(key=lambda r: (-int(r['관찰건수'] or 0), r['이름']))
    return new[:TOP_N]


# 🔴 이 두 상수는 **파일럿 24장이 아니라 500장 전수 실측**으로 정했다.
#    파일럿에서 정한 LIFT=32는 500장에서 꽃상단 120(기존 150)으로 과했다 —
#    파일럿이 작은 꽃(tiny_cluster 5/24)에 치우친 표본이었다.
#    바꿀 때는 500장을 다시 재라. 24장으로 재면 또 틀린다.
LIFT = 0        # 꽃을 위로 올리는 픽셀. 실측 결과 보정이 필요 없다.
FLOWER_SCALE = 0.88   # 꽃 크기. 실측 꽃폭 187 → 기존 162에 맞춘다.


def _default_cy(fn):
    """형태 함수의 `cy` 기본값. 없으면 예외 — 조용히 0이 되면 꽃이 사라진다."""
    d = inspect.signature(fn).parameters['cy'].default
    if d is inspect.Parameter.empty:
        raise AssertionError(f'{fn.__name__}에 cy 기본값이 없다')
    return d


def draw(no, name, family, color):
    """꽃 한 장. 시드는 도감번호 — 같은 종은 항상 같은 그림."""
    rnd = random.Random(no)
    art = Art()
    form, leaf, teeth = FAMILY_FORM.get(family, ('petal5', 'oval', 0))
    woody = is_woody(name, family)

    # 초록도 장마다 조금씩 다르게 — 실측된 5색 안에서만 고른다
    green = rnd.choice([FA.GREEN_MID, FA.GREEN_BASE, FA.GREEN_DARK,
                        FA.GREEN_LIGHT])
    fl = Flower(art, rnd, color, green)

    # 🔴 순서가 z축이다: 잎 → 줄기 → 꽃. 뒤바꾸면 줄기가 잎 아래로 숨는다.
    # 🔴 L·W를 여기서 넘기면 `leaves()`의 기본값은 **안 쓰인다**.
    #    라이브러리 기본값만 키우고 여기를 그대로 두면 아무것도 안 넓어진다
    #    (실제로 그랬다 — 잎 bbox 좌 157이 안 움직였다).
    #    실측 목표: 알파 bbox 좌 94 = CX에서 가로로 162px. 각도 30°면 L≈188.
    # 🔴 잎 **폭**을 줄인다. bbox 폭(좌93 vs 94)은 맞았는데 면적이 안 맞았다:
    #    기존 꽃/잎 면적비 1.04(꽃과 잎이 같은 면적) vs 신규 0.55(잎이 두 배).
    #    bbox만 보면 통과한다 — 나란히 놓고 봐야 "잎이 먼저 보인다"가 드러난다.
    #    길이는 유지한다(bbox 폭을 맞춘 값이다). 폭과 개수만 줄인다.
    leaf_y = rnd.choice([344, 356, 368])
    if leaf == 'compound':
        fl.leaves_compound(leaf_y, n=2, L=rnd.choice([156, 170, 182]))
    else:
        fl.leaves(leaf_y, kind=LEAF_KIND[leaf], n=2,
                  L=rnd.choice([168, 180, 192]), W=rnd.choice([26, 30, 34]),
                  angle=rnd.choice([26, 30, 34]), teeth=teeth)

    # 🔴 꽃을 **먼저** 그린다. 그래야 `attach_y`(꽃이 줄기에 붙는 높이)를 알 수 있다.
    #    줄기 길이를 고정값으로 박으면 줄기가 꽃을 뚫고 위로 튀어나온다
    #    (파일럿 24장 중 8장에서 실제로 그랬다 — 예외가 안 나서 그림을 봐야 보인다).
    #    꽃을 먼저 body에 넣되, 줄기는 잎 뒤·꽃 앞 위치에 끼워 넣는다.
    slot = art.mark()
    fn = getattr(fl, FORM_FN[form])
    # 🔴 꽃 높이를 형태별 기본값에서 LIFT만큼 올린다. 실측이 근거다:
    #    기존200 꽃상단 y=150, 신규 y=187 → 꽃이 아래로 처져 있었다.
    #    형태마다 cy 기본값이 달라 상수를 박을 수 없다 — 기본값을 읽어서 뺀다.
    #    attach_y도 같이 올라가므로 줄기는 알아서 길어진다.
    fl.scale = FLOWER_SCALE
    fn(cy=_default_cy(fn) - LIFT)
    if fl.attach_y is None:
        raise AssertionError(f'{form}이 attach_y를 설정하지 않았다 — '
                             f'줄기가 꽃을 뚫는다')

    stem_svg = fl.stem_svg(fl.attach_y, w=rnd.choice([12, 13, 15]),
                           woody=woody)
    art.insert_at(slot, stem_svg)

    return art.render(f'{no:04d}_{name}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--limit', type=int, default=0)
    ap.add_argument('--png', action='store_true', default=True)
    a = ap.parse_args()

    rows = list(csv.DictReader(io.open(SRC, encoding='utf-8-sig')))
    sel = pick(rows)
    if a.limit:
        sel = sel[:a.limit]

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(SVG_OUT, exist_ok=True)

    rsvg = RSVG if os.path.exists(RSVG) else shutil.which('rsvg-convert')
    if not rsvg:
        sys.exit('rsvg-convert가 필요하다: brew install librsvg')

    spec = []
    edges = []
    basis = {'종': 0, '이름': 0, '과': 0}
    forms = {}
    for r in sel:
        no = int(r['도감번호'])
        name = r['이름']
        fam = r['과'] or ''
        color, why = resolve(name, fam)
        basis[why] += 1
        form = FAMILY_FORM.get(fam, ('petal5', 'oval', 0))[0]
        forms[form] = forms.get(form, 0) + 1

        svg = draw(no, name, fam, color)
        stem = f'{no:04d}_{name}'
        with io.open(SVG_OUT + stem + '.svg', 'w', encoding='utf-8') as f:
            f.write(svg)
        # 🔴 check=True. 렌더가 실패해도 SVG는 남으므로 조용히 PNG만
        #    빠지면 '납품 500장'인데 앱에서는 그 칸이 빈다.
        subprocess.run([rsvg, '-w', '512', '-h', '512',
                        SVG_OUT + stem + '.svg', '-o', OUT + stem + '.png'],
                       check=True, capture_output=True)
        # 🔴 렌더 직후 여백을 센다. 나중에 `verify_illust.py`로만 잡으면
        #    어느 부품이 뚫었는지 모른 채 500장을 다시 돌려야 한다.
        edge = _edge_touch(OUT + stem + '.png')
        if edge:
            edges.append(f'{stem}: {edge}')

        spec.append({'도감번호': no, '이름': name, '과': fam,
                     '꽃색': color, '색_근거': why, '꽃형태': form,
                     '목본': 'Y' if is_woody(name, fam) else '',
                     '관찰건수': r['관찰건수']})

    with io.open(SPEC, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(spec[0].keys()))
        w.writeheader()
        w.writerows(spec)

    n = len(spec)
    print(f'생성 {n}종 → {OUT}')
    print(f'  SVG {n}장 (벡터 원본) · PNG {n}장 (512×512)')
    print(f'\n색 근거:')
    for k in ('종', '이름', '과'):
        print(f'  {k:2s} {basis[k]:4d}장  ({basis[k]/n*100:.0f}%)')
    print(f'\n🔴 `과` 등급 {basis["과"]}장은 그 종의 색이 아니라 '
          f'**그 과에서 흔한 색**이다 — 검수 1순위다.')
    print(f'\n꽃형태 분포 (상위 10):')
    for k, v in sorted(forms.items(), key=lambda x: -x[1])[:10]:
        print(f'  {k:16s} {v:4d}')
    if edges:
        print(f'\n🔴 네 변에 닿은 장 {len(edges)}건 — 도감 그리드에서 잘린다:')
        for e in edges[:10]:
            print(f'  {e}')
        if len(edges) > 10:
            print(f'  … 외 {len(edges)-10}건')
    else:
        print(f'\n여백: {n}장 전부 네 변에 여백 있음')
    print(f'\n스펙 → {SPEC}')


if __name__ == '__main__':
    main()
