# -*- coding: utf-8 -*-
"""꽃 일러스트 부품 라이브러리 — 파라메트릭 SVG.

`꽃도감_일러스트/`의 기존 200장을 **실측해서** 뽑은 스타일을 재현한다.
추측한 값은 없다. 재측정 명령은 `measure_style.py`.

## 실측한 스타일 (200장 전수)

| 항목 | 측정값 | 이 파일에서 |
|---|---|---|
| 알파 bbox 중앙값 | 좌 94 · 상 147 · 우 418 · 하 502 | `BBOX_*` |
| 잎·줄기 초록 | `#489018` `#78C048` `#60A830` `#487818` `#306000` (화면의 37%) | `GREEN_*` |
| 장당 고유색 | 중앙값 3,531색 | 모든 부품에 **그라디언트**를 쓴다 |
| 외곽선 | 없음 (순수 fill) | stroke를 쓰지 않는다 |

🔴 **고유색이 3,531이라는 건 평면 벡터가 아니라는 뜻이다.** 단색 fill로 그리면
   숫자로는 규격을 통과하는데(512·RGBA·알파) 기존 200장 옆에 놓으면 확연히 다르다.
   그래서 부품마다 명암 그라디언트를 넣는다.

🔴 **네 변에 그림이 닿으면 안 된다** (`verify_illust.py`의 '네 변에 여백 있음').
   좌표를 직접 쓰지 말고 `BBOX_*` 안에서만 그려라. 도감 그리드에서 잘려 보인다.
"""
import math

CANVAS = 512
CX = CANVAS // 2          # 256 — 좌우 대칭축

# 실측 bbox 중앙값. 이 안에서만 그린다.
BBOX_TOP = 147
BBOX_BOTTOM = 502
BBOX_LEFT = 94
BBOX_RIGHT = 418

GROUND_Y = BBOX_BOTTOM    # 줄기 밑끝
MAX_R = 132               # 꽃 반지름 상한 (CX ± MAX_R < 512 여백 보장)

# ── 실측 초록 ────────────────────────────────────────────────
GREEN_LIGHT = '#78C048'
GREEN_MID = '#60A830'
GREEN_BASE = '#489018'
GREEN_DARK = '#487818'
GREEN_DEEP = '#306000'

BROWN_LIGHT = '#8A6038'    # 목본 줄기 (개나리·매화에서 실측된 #604020 계열)
BROWN_DARK = '#4A3018'


# ── 색 유틸 ──────────────────────────────────────────────────
def _rgb(c):
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def _hex(t):
    return '#%02X%02X%02X' % tuple(max(0, min(255, int(round(v)))) for v in t)


def lighten(c, f=0.3):
    r, g, b = _rgb(c)
    return _hex((r + (255 - r) * f, g + (255 - g) * f, b + (255 - b) * f))


def darken(c, f=0.3):
    r, g, b = _rgb(c)
    return _hex((r * (1 - f), g * (1 - f), b * (1 - f)))


def mix(c1, c2, t=0.5):
    a, b = _rgb(c1), _rgb(c2)
    return _hex(tuple(a[i] + (b[i] - a[i]) * t for i in range(3)))


class Art:
    """SVG 문서 하나를 조립한다. 그라디언트 정의(defs)를 모아 둔다.

    🔴 그라디언트 id는 문서 안에서 유일해야 한다. 두 꽃이 같은 id를 쓰면
       뒤에 정의된 쪽이 앞의 것을 덮어 **색이 조용히 바뀐다**. `_n`으로 센다.

    🔴 `body`는 **그리는 순서 = z축**이다. 줄기를 꽃보다 뒤에 그리면
       줄기가 꽃 위로 지나간다. 순서를 바꿔야 할 때는 `insert_at`을 쓴다 —
       꽃을 먼저 그려 크기를 알아낸 뒤 줄기를 그 아래 레이어로 끼워 넣는다.
    """

    def __init__(self):
        self.defs = []
        self.body = []
        self._n = 0

    def mark(self):
        """지금 위치를 기억한다. 나중에 여기에 끼워 넣는다."""
        return len(self.body)

    def insert_at(self, i, s):
        self.body.insert(i, s)

    def _id(self, p):
        self._n += 1
        return f'{p}{self._n}'

    # ── 그라디언트 ───────────────────────────────────────────
    def lin(self, c_light, c_dark, x1=0, y1=0, x2=1, y2=1):
        i = self._id('lg')
        self.defs.append(
            f'<linearGradient id="{i}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">'
            f'<stop offset="0" stop-color="{c_light}"/>'
            f'<stop offset="1" stop-color="{c_dark}"/></linearGradient>')
        return f'url(#{i})'

    def rad(self, c_light, c_dark, fx=0.35, fy=0.3, r=0.75):
        i = self._id('rg')
        self.defs.append(
            f'<radialGradient id="{i}" cx="0.5" cy="0.5" r="{r}" fx="{fx}" fy="{fy}">'
            f'<stop offset="0" stop-color="{c_light}"/>'
            f'<stop offset="1" stop-color="{c_dark}"/></radialGradient>')
        return f'url(#{i})'

    def add(self, s):
        self.body.append(s)

    def render(self, node_name=''):
        d = '\n'.join(self.defs)
        b = '\n'.join(self.body)
        title = f'<title>{node_name}</title>' if node_name else ''
        return (f'<svg xmlns="http://www.w3.org/2000/svg" '
                f'width="{CANVAS}" height="{CANVAS}" '
                f'viewBox="0 0 {CANVAS} {CANVAS}">'
                f'{title}<defs>{d}</defs>{b}</svg>')


# ── 꽃잎 모양 (원점에서 위로 뻗는다. 뾰족한 끝이 (0,-L)) ──────
def petal_teardrop(L, W):
    return (f'M0 0C{-W} {-L*0.32} {-W*0.86} {-L*0.86} 0 {-L}'
            f'C{W*0.86} {-L*0.86} {W} {-L*0.32} 0 0Z')


def petal_round(L, W):
    """끝이 둥근 꽃잎 — 장미과·미나리아재비과."""
    return (f'M0 0C{-W} {-L*0.30} {-W*1.02} {-L*0.78} 0 {-L}'
            f'C{W*1.02} {-L*0.78} {W} {-L*0.30} 0 0Z')


def petal_pointed(L, W):
    """끝이 뾰족한 꽃잎 — 백합과 화피·도라지."""
    return (f'M0 0C{-W} {-L*0.38} {-W*0.55} {-L*0.80} 0 {-L}'
            f'C{W*0.55} {-L*0.80} {W} {-L*0.38} 0 0Z')


def petal_notched(L, W):
    """끝이 갈라진 꽃잎 — 석죽과(패랭이·별꽃). 이 갈라짐이 과의 식별점이다."""
    n = W * 0.32
    return (f'M0 0C{-W} {-L*0.38} {-W*0.92} {-L*0.90} {-n} {-L}'
            f'L0 {-L*0.80}L{n} {-L}'
            f'C{W*0.92} {-L*0.90} {W} {-L*0.38} 0 0Z')


def petal_strap(L, W):
    """혀꽃(설상화) — 국화과의 바깥쪽 꽃잎."""
    return (f'M{-W} 0C{-W} {-L*0.55} {-W*0.9} {-L} 0 {-L}'
            f'C{W*0.9} {-L} {W} {-L*0.55} {W} 0Z')


# ── 잎 ──────────────────────────────────────────────────────
def leaf_path(L, W, teeth=0, tip=0.85, base=0.85):
    """(0,0)에서 +x 방향으로 L만큼 뻗는 잎. 회전은 호출자가 한다.

    teeth: 톱니 개수. 0이면 매끈한 잎(전연). 민들레·장미과는 톱니가 있다.
    """
    n = 30
    up, dn = [], []
    for i in range(n + 1):
        t = i / n
        w = W * (math.sin(math.pi * (t ** base)) ** tip)
        x = L * t
        up.append((x, -w))
        dn.append((x, w))
    if teeth:
        # 🔴 진폭을 W에 비례시키면 안 된다. 잎을 넓히자 톱니가 같이 커져
        #    호랑가시나무 같은 **가시 잎**이 됐다(파일럿 24장 중 9장).
        #    실측 200장의 톱니는 잎 폭과 무관하게 얕다 — 고정 픽셀로 둔다.
        amp = min(6.0, W * 0.13)
        for arr, sgn in ((up, -1), (dn, 1)):
            for i in range(1, n):
                if (i * teeth // n) != ((i - 1) * teeth // n):
                    x, y = arr[i]
                    arr[i] = (x, y + sgn * amp)
    pts = up + list(reversed(dn))
    d = 'M' + ' L'.join(f'{x:.1f} {y:.1f}' for x, y in pts) + 'Z'
    return d


def leaf_lobed(L, W, lobes=5):
    """단풍잎처럼 갈라진 잎 — 단풍나무과·포도과."""
    pts = []
    for i in range(lobes * 2 + 1):
        t = i / (lobes * 2)
        ang = math.pi * (t - 0.5) * 0.95
        r = L if i % 2 == 0 else L * 0.52
        pts.append((r * math.cos(ang), r * math.sin(ang) * (W / L) * 1.9))
    return 'M0 0 L' + ' L'.join(f'{x:.1f} {y:.1f}' for x, y in pts) + ' Z'


def leaf_heart(L, W):
    """심장형 잎 — 나팔꽃·마과."""
    return (f'M0 0C{L*0.15} {-W} {L*0.75} {-W*1.05} {L} 0'
            f'C{L*0.75} {W*1.05} {L*0.15} {W} 0 0Z')


class Flower:
    """꽃 한 장을 그린다.

    호출 순서가 곧 그리는 순서(z축)다: 잎 → 줄기 → 꽃.
    잎을 먼저 그려야 줄기가 잎 위로 지나간다 (기존 200장의 배치다).
    """

    def __init__(self, art, rnd, color, green=GREEN_MID):
        self.a = art
        self.r = rnd
        self.c = color
        self.g = green
        # 🔴 꽃이 줄기에 붙는 y. 각 f_* 함수가 **반드시** 설정한다.
        #    설정을 잊으면 줄기가 꽃을 뚫고 위로 튀어나온다 —
        #    파일럿 24장에서 실제로 8장이 그랬다. 예외는 안 난다.
        self.attach_y = None
        # 꽃 부품 치수의 일괄 배율. 잎에는 적용되지 않는다(잎은 L·W를 직접 받는다).
        self.scale = 1.0

    def _j(self, v, f=0.12):
        """같은 과라도 장마다 조금씩 다르게 — 500장이 붕어빵이 되지 않게.

        🔴 `scale`이 여기에 곱해진다. 꽃 부품 치수는 **전부 `_j`를 거쳐야**
           일괄 조정이 먹는다. 상수를 직접 쓴 곳은 `scale`을 무시한다.
        """
        return v * self.scale * (1 + self.r.uniform(-f, f))

    # ── 줄기 ─────────────────────────────────────────────────
    def stem_svg(self, top_y, x=CX, w=13, woody=False):
        """줄기를 **문자열로** 돌려준다 — 호출자가 z축 위치를 정한다.

        🔴 `add`하지 않는다. 꽃을 먼저 그려 `attach_y`를 알아낸 뒤
           잎과 꽃 **사이**에 끼워 넣어야 하기 때문이다.
        """
        c1, c2 = (BROWN_LIGHT, BROWN_DARK) if woody else (
            lighten(self.g, 0.22), darken(self.g, 0.28))
        f = self.a.lin(c1, c2, 0, 0, 1, 0)
        h = GROUND_Y - top_y
        if h <= 0:
            return ''
        return (f'<rect x="{x-w/2:.1f}" y="{top_y:.1f}" width="{w}" '
                f'height="{h:.1f}" rx="{w/2:.1f}" fill="{f}"/>')

    def stem(self, top_y, x=CX, w=13, woody=False):
        self.a.add(self.stem_svg(top_y, x, w, woody))

    def stem_curved(self, top_y, x=CX, w=12, bend=26, woody=False):
        """휘어진 줄기 — 덩굴·처지는 꽃차례."""
        c1, c2 = (BROWN_LIGHT, BROWN_DARK) if woody else (
            lighten(self.g, 0.22), darken(self.g, 0.28))
        f = self.a.lin(c1, c2, 0, 0, 1, 0)
        my = (top_y + GROUND_Y) / 2
        self.a.add(
            f'<path d="M{x} {GROUND_Y}Q{x+bend} {my} {x} {top_y}" fill="none" '
            f'stroke="{f}" stroke-width="{w}" stroke-linecap="round"/>')

    # ── 잎 ───────────────────────────────────────────────────
    def leaves(self, y, kind='oval', n=2, L=158, W=46, angle=34, teeth=0):
        """줄기 밑에 잎을 좌우로 붙인다. 실측: 잎은 아래쪽 1/3에 모여 있다."""
        for i in range(n):
            side = -1 if i % 2 == 0 else 1
            tier = i // 2
            yy = y + tier * 34
            ang = side * self._j(angle, 0.22) + (18 if tier else 0) * side
            LL, WW = self._j(L), self._j(W)
            # 🔴 아래 변을 뚫지 않게 잎 시작 y를 끌어올린다.
            #    잎을 길게 만들자(L 96→192) 끝이 512를 넘어 500장 중 170장이
            #    `verify_illust.py`의 '네 변에 여백 있음'에서 걸렸다.
            #    각도·길이가 장마다 달라 고정값으로는 못 막는다 — 계산해서 자른다.
            reach = LL * math.sin(math.radians(abs(ang))) + WW
            yy = min(yy, BBOX_BOTTOM - reach)
            if kind == 'lobed':
                d = leaf_lobed(LL, WW)
            elif kind == 'heart':
                d = leaf_heart(LL, WW)
            elif kind == 'blade':
                d = leaf_path(LL * 1.25, WW * 0.42, tip=1.5, base=1.4)
            elif kind == 'lance':
                d = leaf_path(LL, WW * 0.62, teeth=teeth, tip=1.2)
            else:
                d = leaf_path(LL, WW, teeth=teeth)
            f = self.a.lin(lighten(self.g, 0.26), darken(self.g, 0.34),
                           0, 0, 0.4, 1)
            sx = -1 if side < 0 else 1
            self.a.add(
                f'<g transform="translate({CX} {yy:.1f}) rotate({ang:.1f}) '
                f'scale({sx} 1)"><path d="{d}" fill="{f}"/>'
                f'<path d="M4 0L{LL*0.86:.1f} 0" stroke="{darken(self.g,0.42)}" '
                f'stroke-width="2.2" opacity="0.55" fill="none"/></g>')

    def leaves_compound(self, y, n=2, L=152, leaflets=3):
        """겹잎 — 콩과. 작은 잎 여러 장이 한 자루에 붙는다."""
        for i in range(n):
            side = -1 if i % 2 == 0 else 1
            ang = side * self._j(24, 0.2)
            # 🔴 `leaves`와 같은 보정이 필요하다. 여기만 빼먹어 겹잎(콩과)
            #    18장이 아래 변을 뚫었다 — 한쪽만 고치면 나머지가 남는다.
            #    자루 끝의 작은 잎이 자루보다 더 아래로 처지므로 L*0.13을 더한다.
            yy = y + (i // 2) * 32
            reach = L * 0.9 * math.sin(math.radians(abs(ang))) + L * 0.34
            yy = min(yy, BBOX_BOTTOM - reach)
            f = self.a.lin(lighten(self.g, 0.26), darken(self.g, 0.34), 0, 0, 0.4, 1)
            g = [f'<g transform="translate({CX} {yy:.1f}) '
                 f'rotate({ang:.1f}) scale({-1 if side<0 else 1} 1)">']
            g.append(f'<path d="M0 0L{L*0.9:.1f} 0" stroke="{darken(self.g,0.3)}" '
                     f'stroke-width="3" fill="none"/>')
            for k in range(leaflets):
                t = 0.32 + 0.68 * (k / max(1, leaflets - 1))
                lx = L * t * 0.9
                ll, lw = L * 0.34, L * 0.13
                for s in (-1, 1):
                    d = leaf_path(ll, lw)
                    g.append(f'<g transform="translate({lx:.1f} 0) '
                             f'rotate({s*62})"><path d="{d}" fill="{f}"/></g>')
            g.append('</g>')
            self.a.add(''.join(g))

    # ── 꽃 ───────────────────────────────────────────────────
    def _petals(self, cx, cy, n, L, W, shape, rot0=0.0, scale=1.0):
        c1, c2 = lighten(self.c, 0.38), darken(self.c, 0.22)
        for i in range(n):
            ang = rot0 + 360.0 * i / n
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = shape(L * scale, W * scale)
            self.a.add(f'<g transform="translate({cx:.1f} {cy:.1f}) '
                       f'rotate({ang:.1f})"><path d="{d}" fill="{f}"/></g>')

    def _center(self, cx, cy, r, c=None):
        c = c or '#F0C048'
        f = self.a.rad(lighten(c, 0.45), darken(c, 0.25))
        self.a.add(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{r:.1f}" fill="{f}"/>')

    def _stamens(self, cx, cy, n=6, L=20):
        c = '#E8C24A'
        for i in range(n):
            ang = math.radians(-90 + (i - (n - 1) / 2) * 15)
            x2, y2 = cx + L * math.cos(ang), cy + L * math.sin(ang)
            self.a.add(f'<path d="M{cx:.1f} {cy:.1f}L{x2:.1f} {y2:.1f}" '
                       f'stroke="{c}" stroke-width="2.6" fill="none" '
                       f'stroke-linecap="round"/>')
            self.a.add(f'<circle cx="{x2:.1f}" cy="{y2:.1f}" r="3.4" '
                       f'fill="{darken(c,0.18)}"/>')

    # ══ 꽃형태별 그리기 ═════════════════════════════════════
    # 각 함수는 꽃 맨 위 y를 돌려준다 (줄기 길이를 정하려고).

    def f_ray_disc(self, cy=250):
        """설상화 + 통상화 — 국화과. 500종 중 63종이 이 과다."""
        n = self.r.choice([13, 16, 18, 21])
        L, W = self._j(140), self._j(21)
        self._petals(CX, cy, n, L, W, petal_strap)
        self._center(CX, cy, self._j(46), '#E8B430')
        self.attach_y = cy + 18
        return cy - L

    def f_ray_disc_big(self, cy=254):
        """해바라기형 — 통상화가 크다."""
        n = self.r.choice([20, 24, 28])
        L, W = self._j(108), self._j(18)
        self._petals(CX, cy, n, L, W, petal_pointed)
        self._center(CX, cy, self._j(74), '#6B4A20')
        self.attach_y = cy + 30
        return cy - L

    def f_petal5(self, cy=252, shape=petal_round):
        """5장 — 장미과·미나리아재비과."""
        L, W = self._j(124), self._j(56)
        self._petals(CX, cy, 5, L, W, shape, rot0=self.r.uniform(-12, 12))
        self._center(CX, cy, self._j(27))
        self.attach_y = cy + 20
        return cy - L

    def f_petal5_notched(self, cy=250):
        """5장 + 끝 갈라짐 — 석죽과."""
        L, W = self._j(116), self._j(50)
        self._petals(CX, cy, 5, L, W, petal_notched)
        self._center(CX, cy, self._j(20), '#F0E0A0')
        self.attach_y = cy + 18
        return cy - L

    def f_cross4(self, cy=250):
        """십자 4장 — 십자화과(냉이·유채). 4장이 과의 식별점이다."""
        L, W = self._j(104), self._j(51)
        # 봉오리 — 십자화과는 총상꽃차례다.
        # 🔴 꽃잎을 45° 돌렸으므로 **위쪽 정중앙은 꽃잎 사이 틈**이다.
        #    그 축에 봉오리를 두면 아무것에도 닿지 않고 프레임에 점이 뜬다
        #    (500장 중 27장). 꽃 **뒤에** 겹쳐 그려 꽃과 이어 붙인다.
        for k, t in enumerate((0.72, 1.02)):
            f = self.a.lin(lighten(self.c, 0.3), darken(self.c, 0.2))
            self.a.add(f'<ellipse cx="{CX}" cy="{cy - L*t:.1f}" rx="{15-k*4}" '
                       f'ry="{20-k*5}" fill="{f}"/>')
        self._petals(CX, cy, 4, L, W, petal_round, rot0=45)
        self._center(CX, cy, self._j(19), '#E8C24A')
        self.attach_y = cy + 16
        return cy - L * 1.02 - 20

    def f_tepal6(self, cy=252):
        """화피 6장 — 백합과. 500종 중 30종."""
        L, W = self._j(132), self._j(44)
        self._petals(CX, cy, 6, L, W, petal_pointed, rot0=self.r.uniform(0, 30))
        self._stamens(CX, cy, 6, self._j(46))
        self.attach_y = cy + 22
        return cy - L

    def f_pea(self, cy=254):
        """접형화 — 콩과. 기꽃잎 1 + 날개꽃잎 2 + 용골꽃잎. 500종 중 36종."""
        c1, c2 = lighten(self.c, 0.40), darken(self.c, 0.24)
        for x, sc in ((-42, 1.0), (42, 1.0)):
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = petal_round(self._j(64) * sc, self._j(31) * sc)
            self.a.add(f'<g transform="translate({CX+x} {cy}) '
                       f'rotate({-24 if x<0 else 24})"><path d="{d}" fill="{f}"/></g>')
        f = self.a.lin(lighten(self.c, 0.5), self.c, 0.5, 0, 0.5, 1)
        self.a.add(f'<path d="M{CX-58} {cy-40}C{CX-64} {cy-116} {CX+64} {cy-116} '
                   f'{CX+58} {cy-40}C{CX+28} {cy-18} {CX-28} {cy-18} '
                   f'{CX-58} {cy-40}Z" fill="{f}"/>')
        f2 = self.a.lin(self.c, darken(self.c, 0.34), 0.5, 0, 0.5, 1)
        self.a.add(f'<path d="M{CX-28} {cy-10}C{CX-20} {cy+36} {CX+20} {cy+36} '
                   f'{CX+28} {cy-10}Z" fill="{f2}"/>')
        self.attach_y = cy + 26
        return cy - 86

    def f_lipped_spike(self, cy=228):
        """입술꽃 이삭 — 꿀풀과. 층층이 돌려난다. 500종 중 18종."""
        tiers = self.r.choice([4, 5, 6])
        top = cy
        for i in range(tiers):
            yy = cy + i * 30
            rr = 20 + i * 7
            for s in (-1, 1):
                f = self.a.lin(lighten(self.c, 0.42), darken(self.c, 0.22), 0, 0, 0.4, 1)
                self.a.add(
                    f'<path d="M{CX} {yy}C{CX+s*rr*0.5} {yy-16} '
                    f'{CX+s*rr} {yy-12} {CX+s*rr} {yy+2}'
                    f'C{CX+s*rr} {yy+14} {CX+s*rr*0.4} {yy+13} {CX} {yy+7}Z" '
                    f'fill="{f}"/>')
            top = min(top, yy - 16)
        self.attach_y = cy + tiers * 30
        return top

    def f_tiny_spike(self, cy=212):
        """작은 꽃 이삭 — 마디풀과(여뀌). 500종 중 17종.

        🔴 알갱이만 세로로 늘어놓으면 32px에서 실선 한 줄이 된다.
           이삭 **덩어리 실루엣**을 먼저 깔아 형태를 남긴다.
        """
        # 🔴 세로:가로가 4.7:1이면 **깃털/잎**으로 보인다(북범꼬리 등).
        #    3:1까지 굵혀야 이삭으로 읽힌다.
        H, W = self._j(132), self._j(44)
        f0 = self.a.lin(lighten(self.c, 0.40), darken(self.c, 0.08), 0.2, 0, 0.8, 1)
        self.a.add(f'<path d="M{CX} {cy - 12:.1f}'
                   f'C{CX + W:.1f} {cy + H*0.20:.1f} {CX + W:.1f} '
                   f'{cy + H*0.78:.1f} {CX} {cy + H:.1f}'
                   f'C{CX - W:.1f} {cy + H*0.78:.1f} {CX - W:.1f} '
                   f'{cy + H*0.20:.1f} {CX} {cy - 12:.1f}Z" fill="{f0}"/>')
        n = self.r.choice([20, 25, 30])
        for i in range(n):
            t = i / n
            yy = cy + t * H
            xx = CX + math.sin(t * 11) * (W * 0.40)
            f = self.a.rad(lighten(self.c, 0.55), darken(self.c, 0.12))
            self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" '
                       f'r="{9 - t*2.5:.1f}" fill="{f}"/>')
        self.attach_y = cy + H
        return cy - 14

    def f_umbel(self, cy=238):
        """산형꽃차례 — 산형과(개발나물·참반디). 500종 중 19종.

        🔴 우산살 끝에 지름 22px 원만 찍으면 **살만 보이고 꽃이 안 보인다**
           (500장 전수에서 45장이 흰 부챗살로 나왔다 — 파일럿 24장에는
           umbel이 없어 안 걸렸다). 산형과의 실제 모습은 살 끝마다
           **작은 우산이 또 달린 겹산형**이다. 덩어리로 그린다.
        """
        spokes = self.r.choice([7, 9, 11])
        R = self._j(126)
        base_y = cy + 34
        for i in range(spokes):
            ang = math.radians(-180 * i / (spokes - 1))
            ex, ey = CX + R * math.cos(ang), cy + R * math.sin(ang) * 0.58
            self.a.add(f'<path d="M{CX} {base_y:.1f}L{ex:.1f} {ey:.1f}" '
                       f'stroke="{darken(self.g,0.2)}" stroke-width="3" fill="none"/>')
            # 살 끝의 작은 우산 — 이 덩어리가 있어야 꽃으로 읽힌다
            sr = self.r.uniform(24, 31)
            f0 = self.a.rad(lighten(self.c, 0.42), darken(self.c, 0.14))
            self.a.add(f'<ellipse cx="{ex:.1f}" cy="{ey:.1f}" rx="{sr:.1f}" '
                       f'ry="{sr*0.66:.1f}" fill="{f0}"/>')
            for k in range(5):
                a2 = self.r.uniform(0, math.tau)
                rr = sr * 0.62 * math.sqrt(self.r.random())
                f = self.a.rad(lighten(self.c, 0.55), darken(self.c, 0.22))
                self.a.add(f'<circle cx="{ex + rr*math.cos(a2):.1f}" '
                           f'cy="{ey + rr*math.sin(a2)*0.66:.1f}" '
                           f'r="{self.r.uniform(7,10):.1f}" fill="{f}"/>')
        self.attach_y = base_y
        return cy - R * 0.58 - 22

    def f_violet(self, cy=252):
        """제비꽃형 — 아래 꽃잎이 크고 좌우비대칭. 500종 중 13종."""
        c1, c2 = lighten(self.c, 0.40), darken(self.c, 0.26)
        for ang, L, W in ((-52, 76, 34), (52, 76, 34), (-116, 65, 28), (116, 65, 28)):
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = petal_round(self._j(L), self._j(W))
            self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang})">'
                       f'<path d="{d}" fill="{f}"/></g>')
        f = self.a.lin(lighten(self.c, 0.2), darken(self.c, 0.34), 0.5, 0, 0.5, 1)
        d = petal_round(self._j(73), self._j(42))
        self.a.add(f'<g transform="translate({CX} {cy}) rotate(180)">'
                   f'<path d="{d}" fill="{f}"/></g>')
        self._center(CX, cy, 9, '#F0D060')
        self.attach_y = cy + 30
        return cy - 56

    def f_bell(self, cy=248):
        """종모양 — 초롱꽃과·용담과·진달래과. 아래를 향해 매달린다.

        🔴 위아래 폭이 비슷하면 **모서리 둥근 상자**가 된다(파일럿에서 실제로
           마시멜로처럼 나왔다). 종은 **위가 좁고 입구가 벌어진** 형태다.
           위폭/아래폭 비를 0.34로 못박아 둔다 — 이 비가 종모양의 전부다.
        """
        w, h = self._j(120), self._j(132)
        top, bot = cy - h * 0.44, cy + h * 0.40
        tw = w * 0.34                      # 🔴 위폭. 아래폭의 1/3이어야 종으로 읽힌다.
        d = [f'M{CX - tw/2:.1f} {top:.1f}',
             # 왼쪽 벽 — 좁은 목에서 부풀며 입구로 벌어진다
             f'C{CX - w*0.42:.1f} {cy - h*0.10:.1f} '
             f'{CX - w*0.52:.1f} {cy + h*0.20:.1f} '
             f'{CX - w*0.50:.1f} {bot:.1f}']
        lobes = self.r.choice([5, 5, 6])
        for i in range(lobes):             # 입구의 갈래 — 통꽃 끝이 갈라진 표시
            x0 = CX - w * 0.50 + w * i / lobes
            x1 = CX - w * 0.50 + w * (i + 1) / lobes
            d.append(f'C{x0 + (x1-x0)*0.28:.1f} {bot + h*0.14:.1f} '
                     f'{x1 - (x1-x0)*0.28:.1f} {bot + h*0.14:.1f} '
                     f'{x1:.1f} {bot:.1f}')
        d.append(f'C{CX + w*0.52:.1f} {cy + h*0.20:.1f} '
                 f'{CX + w*0.42:.1f} {cy - h*0.10:.1f} {CX + tw/2:.1f} {top:.1f}')
        d.append(f'L{CX - tw/2:.1f} {top:.1f}Z')
        f = self.a.lin(lighten(self.c, 0.44), darken(self.c, 0.30), 0.12, 0, 0.88, 1)
        self.a.add(f'<path d="{"".join(d)}" fill="{f}"/>')
        # 입구 안쪽 그늘 — 열린 통이라는 것을 알려준다. 없으면 덩어리로 보인다.
        f2 = self.a.rad(darken(self.c, 0.36), darken(self.c, 0.12), 0.5, 0.25, 0.8)
        self.a.add(f'<ellipse cx="{CX}" cy="{bot + h*0.03:.1f}" '
                   f'rx="{w*0.42:.1f}" ry="{h*0.11:.1f}" fill="{f2}" opacity="0.8"/>')
        # 꽃받침 — 좁은 목을 초록으로 받쳐 줄기와 이어 준다
        self.a.add(f'<ellipse cx="{CX}" cy="{top + 4:.1f}" rx="{tw*0.62:.1f}" '
                   f'ry="9" fill="{darken(self.g, 0.10)}"/>')
        self.attach_y = top
        return top - 6

    def f_funnel(self, cy=250):
        """나팔형 — 메꽃과(나팔꽃). 500종 중 7종."""
        R = self._j(116)
        f = self.a.rad(lighten(self.c, 0.5), darken(self.c, 0.2), 0.5, 0.62, 0.7)
        self.a.add(f'<ellipse cx="{CX}" cy="{cy}" rx="{R:.1f}" ry="{R*0.86:.1f}" '
                   f'fill="{f}"/>')
        for i in range(5):
            ang = math.radians(-90 + 72 * i)
            self.a.add(f'<path d="M{CX} {cy}L{CX+R*math.cos(ang):.1f} '
                       f'{cy+R*0.86*math.sin(ang):.1f}" stroke="{lighten(self.c,0.55)}" '
                       f'stroke-width="4" opacity="0.7" fill="none"/>')
        self._center(CX, cy, 12, '#FFF0C0')
        self.attach_y = cy + R * 0.5
        return cy - R * 0.86

    def f_two_lip(self, cy=250):
        """두 갈래 입술꽃 — 현삼과."""
        f = self.a.lin(lighten(self.c, 0.42), darken(self.c, 0.24), 0.3, 0, 0.7, 1)
        w = self._j(94)
        self.a.add(f'<path d="M{CX-w:.1f} {cy}C{CX-w*1.1:.1f} {cy-52} '
                   f'{CX+w*1.1:.1f} {cy-52} {CX+w:.1f} {cy}'
                   f'C{CX+w*0.7:.1f} {cy+34} {CX-w*0.7:.1f} {cy+34} '
                   f'{CX-w:.1f} {cy}Z" fill="{f}"/>')
        self.a.add(f'<path d="M{CX-w*0.5:.1f} {cy+6}Q{CX} {cy+30} '
                   f'{CX+w*0.5:.1f} {cy+6}" stroke="{darken(self.c,0.4)}" '
                   f'stroke-width="3" fill="none" opacity="0.55"/>')
        self._center(CX, cy - 4, 8, '#F5E080')
        self.attach_y = cy + 24
        return cy - 54

    def f_panicle_small(self, cy=208):
        """작은 꽃 원추꽃차례 — 범의귀과·꿀풀과 일부. 500종 중 13종.

        🔴 작은 꽃을 **점으로 흩뿌리면 32px에서 사라진다.** 규격이
           '형태와 색의 식별성'을 기준으로 삼는데(`일러스트_납품_규격.md`),
           흩뿌린 점은 축소하면 회색 얼룩이 된다.
           그래서 **삼각형 덩어리 실루엣을 먼저 깔고** 그 위에 알갱이를 얹는다.
        """
        H, W = self._j(132), self._j(104)
        # 덩어리 실루엣 — 축소해도 이 삼각형은 남는다
        f0 = self.a.lin(lighten(self.c, 0.42), darken(self.c, 0.06), 0.3, 0, 0.7, 1)
        self.a.add(f'<path d="M{CX} {cy - 14:.1f}'
                   f'C{CX + W*0.30:.1f} {cy + H*0.22:.1f} '
                   f'{CX + W*0.50:.1f} {cy + H*0.66:.1f} '
                   f'{CX + W*0.30:.1f} {cy + H:.1f}'
                   f'C{CX + W*0.10:.1f} {cy + H*1.10:.1f} '
                   f'{CX - W*0.10:.1f} {cy + H*1.10:.1f} '
                   f'{CX - W*0.30:.1f} {cy + H:.1f}'
                   f'C{CX - W*0.50:.1f} {cy + H*0.66:.1f} '
                   f'{CX - W*0.30:.1f} {cy + H*0.22:.1f} '
                   f'{CX} {cy - 14:.1f}Z" fill="{f0}"/>')
        for i in range(self.r.choice([26, 32, 38])):
            t = self.r.random()
            yy = cy + t * H
            spread = W * 0.16 + t * W * 0.34
            xx = CX + self.r.uniform(-spread, spread)
            f = self.a.rad(lighten(self.c, 0.55), darken(self.c, 0.14))
            self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" '
                       f'r="{self.r.uniform(7,11):.1f}" fill="{f}"/>')
        self.attach_y = cy + H
        return cy - 16

    def f_ball_cluster(self, cy=242):
        """공 모양 꽃뭉치 — 두릅나무과·수국형."""
        R = self._j(108)
        for i in range(46):
            ang = self.r.uniform(0, math.tau)
            rr = R * math.sqrt(self.r.random())
            xx, yy = CX + rr * math.cos(ang), cy + rr * math.sin(ang) * 0.9
            f = self.a.rad(lighten(self.c, 0.48), darken(self.c, 0.12))
            self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" r="10" fill="{f}"/>')
        self.attach_y = cy + R * 0.6
        return cy - R * 0.9 - 8

    def f_tiny_cluster(self, cy=244):
        """작고 눈에 안 띄는 꽃 다발 — 나무 종류(단풍·화살나무·포도).

        🔴 이 과들은 실제로 꽃이 작고 초록빛이다. 크고 화려하게 그리면
           사용자가 실물과 못 잇는다.
        🔴 그런데 **점으로 흩뿌리면 32px에서 사라진다.** 그래서 작은 꽃을
           유지하면서 **평평한 우산꼴 덩어리**로 묶는다 — 축소하면
           '나무의 작은 꽃뭉치'라는 형태가 남는다.
        """
        R = self._j(94)
        # 꽃자루 — 우산꼴이라는 것을 알려주는 선. 실루엣만으로는 접시로 읽힌다.
        for i in range(7):
            t = (i / 6) * 2 - 1
            ex, ey = CX + R * 0.80 * t, cy - R * 0.10 + abs(t) * R * 0.16
            self.a.add(f'<path d="M{CX} {cy + R*0.34:.1f}L{ex:.1f} {ey:.1f}" '
                       f'stroke="{darken(self.g,0.18)}" stroke-width="3" fill="none"/>')
        # 알갱이가 스스로 윤곽을 만든다 — 매끈한 타원을 깔면 버섯처럼 보인다
        n = self.r.choice([22, 27, 32])
        for i in range(n):
            ang = self.r.uniform(0, math.tau)
            rr = R * 0.94 * (self.r.random() ** 0.62)
            xx, yy = CX + rr * math.cos(ang), cy + rr * math.sin(ang) * 0.44
            f = self.a.rad(lighten(self.c, 0.50), darken(self.c, 0.20))
            rad = self.r.uniform(11, 16)
            self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" '
                       f'r="{rad:.1f}" fill="{f}"/>')
            self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" r="3.4" '
                       f'fill="{darken(self.c,0.36)}" opacity="0.6"/>')
        self.attach_y = cy + R * 0.34
        return cy - R * 0.46

    def f_catkin(self, cy=204):
        """꼬리꽃차례 — 버드나무과. 늘어진 이삭."""
        for s in (-1, 1):
            for k in range(2):
                x0 = CX + s * (18 + k * 20)
                y0 = cy + k * 22
                self.a.add(f'<path d="M{x0} {y0}Q{x0+s*14} {y0+52} '
                           f'{x0+s*6} {y0+96}" stroke="{darken(self.g,0.2)}" '
                           f'stroke-width="3" fill="none"/>')
                for i in range(11):
                    t = i / 10
                    xx = x0 + s * (14 * 2 * t * (1 - t) + 6 * t * t)
                    yy = y0 + 96 * t
                    f = self.a.rad(lighten(self.c, 0.44), self.c)
                    self.a.add(f'<circle cx="{xx:.1f}" cy="{yy:.1f}" r="7.5" '
                               f'fill="{f}"/>')
        self.attach_y = cy + 30
        return cy - 10

    def f_iris(self, cy=250):
        """붓꽃형 — 위로 선 꽃잎 3 + 아래로 처진 꽃잎 3."""
        c1, c2 = lighten(self.c, 0.40), darken(self.c, 0.26)
        for ang in (-142, 142, 180):
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = petal_round(self._j(86), self._j(39))
            self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang})">'
                       f'<path d="{d}" fill="{f}"/></g>')
        for ang in (-16, 16, 0):
            f = self.a.lin(lighten(self.c, 0.24), darken(self.c, 0.34), 0.5, 0, 0.5, 1)
            d = petal_teardrop(self._j(81), self._j(29))
            self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang})">'
                       f'<path d="{d}" fill="{f}"/></g>')
        self.a.add(f'<path d="M{CX-14} {cy-6}L{CX} {cy+14}L{CX+14} {cy-6}" '
                   f'stroke="#E8C24A" stroke-width="5" fill="none" '
                   f'stroke-linecap="round"/>')
        self.attach_y = cy + 40
        return cy - 60

    def f_orchid(self, cy=252):
        """난초형 — 좌우 2장 + 아래 입술꽃잎."""
        c1, c2 = lighten(self.c, 0.42), darken(self.c, 0.24)
        for ang in (-58, 58, -118, 118, 0):
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = petal_pointed(self._j(70), self._j(24))
            self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang})">'
                       f'<path d="{d}" fill="{f}"/></g>')
        f = self.a.lin(lighten(self.c, 0.16), darken(self.c, 0.36), 0.5, 0, 0.5, 1)
        self.a.add(f'<path d="M{CX-26} {cy+6}C{CX-34} {cy+52} {CX+34} {cy+52} '
                   f'{CX+26} {cy+6}C{CX+10} {cy+18} {CX-10} {cy+18} '
                   f'{CX-26} {cy+6}Z" fill="{f}"/>')
        self.attach_y = cy + 40
        return cy - 52

    def f_spurred(self, cy=228):
        """꽃뿔 달린 처지는 꽃 — 현호색과. 500종 중 7종."""
        for i in range(self.r.choice([4, 5, 6])):
            s = -1 if i % 2 == 0 else 1
            xx = CX + s * (20 + (i // 2) * 8)
            yy = cy + (i // 2) * 30
            f = self.a.lin(lighten(self.c, 0.44), darken(self.c, 0.24), 0, 0, 1, 1)
            self.a.add(f'<path d="M{xx:.1f} {yy:.1f}'
                       f'C{xx+s*30:.1f} {yy+6:.1f} {xx+s*34:.1f} {yy+30:.1f} '
                       f'{xx+s*14:.1f} {yy+38:.1f}'
                       f'C{xx+s*2:.1f} {yy+32:.1f} {xx-s*4:.1f} {yy+14:.1f} '
                       f'{xx:.1f} {yy:.1f}Z" fill="{f}"/>')
        self.attach_y = cy + 60
        return cy - 12

    def f_magnolia(self, cy=254):
        """목련형 — 크고 두꺼운 꽃잎 6~9장."""
        n = self.r.choice([6, 9])
        L, W = self._j(134), self._j(58)
        self._petals(CX, cy, n, L, W, petal_round, rot0=self.r.uniform(0, 20))
        self._center(CX, cy, 14, '#E0D060')
        self.attach_y = cy + 26
        return cy - L

    def f_camellia(self, cy=252):
        """겹꽃 — 차나무과(동백). 꽃잎이 여러 겹이다."""
        for ring, (n, L, W) in enumerate(((6, 92, 44), (6, 64, 36), (5, 39, 25))):
            c1 = lighten(self.c, 0.30 + ring * 0.08)
            c2 = darken(self.c, 0.28 - ring * 0.06)
            for i in range(n):
                ang = 360.0 * i / n + ring * 26
                f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
                d = petal_round(self._j(L), self._j(W))
                self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang:.1f})">'
                           f'<path d="{d}" fill="{f}"/></g>')
        self._center(CX, cy, 13, '#F0D050')
        self.attach_y = cy + 30
        return cy - 68

    def f_commelina(self, cy=250):
        """닭의장풀형 — 파란 꽃잎 2장 + 노란 수술."""
        c1, c2 = lighten(self.c, 0.34), darken(self.c, 0.28)
        for ang in (-46, 46):
            f = self.a.lin(c1, c2, 0.5, 0, 0.5, 1)
            d = petal_round(self._j(78), self._j(47))
            self.a.add(f'<g transform="translate({CX} {cy}) rotate({ang})">'
                       f'<path d="{d}" fill="{f}"/></g>')
        self._stamens(CX, cy + 6, 5, 24)
        self.attach_y = cy + 24
        return cy - 58

    def f_arum(self, cy=250):
        """육수꽃차례 + 불염포 — 천남성과. 잎처럼 생긴 덮개가 특징이다."""
        f = self.a.lin(lighten(self.g, 0.3), darken(self.g, 0.3), 0.2, 0, 0.8, 1)
        self.a.add(f'<path d="M{CX-40} {cy+40}C{CX-52} {cy-40} {CX-16} {cy-72} '
                   f'{CX+6} {cy-66}C{CX+40} {cy-58} {CX+44} {cy+6} '
                   f'{CX+32} {cy+40}C{CX+10} {cy+52} {CX-18} {cy+52} '
                   f'{CX-40} {cy+40}Z" fill="{f}"/>')
        f2 = self.a.lin(lighten(self.c, 0.4), darken(self.c, 0.2), 0, 0, 1, 1)
        self.a.add(f'<rect x="{CX-7}" y="{cy-46}" width="14" height="72" rx="7" '
                   f'fill="{f2}"/>')
        self.attach_y = cy + 40
        return cy - 72

    def f_poppy(self, cy=252):
        """양귀비형 — 얇고 넓은 꽃잎 4장."""
        L, W = self._j(124), self._j(74)
        self._petals(CX, cy, 4, L, W, petal_round, rot0=45)
        self._center(CX, cy, 15, '#5A4A20')
        self.attach_y = cy + 24
        return cy - L

    def f_star5(self, cy=250):
        """납작한 별 5장 — 돌나물과·앵초과·괭이밥과."""
        L, W = self._j(110), self._j(40)
        self._petals(CX, cy, 5, L, W, petal_pointed)
        self._center(CX, cy, 11, '#F0D45A')
        self.attach_y = cy + 18
        return cy - L


# ── 과(科) → 꽃형태·잎 원형 ─────────────────────────────────
# 🔴 이 표는 **식물학적 근거**로 정한다. 임의로 예쁜 것을 고르면
#    사용자가 실물과 그림을 못 잇는다 — 도감의 목적이 무너진다.
FAMILY_FORM = {
    'Asteraceae':      ('ray_disc', 'lance', 8),
    'Fabaceae':        ('pea', 'compound', 0),
    'Liliaceae':       ('tepal6', 'blade', 0),
    'Rosaceae':        ('petal5', 'oval', 9),
    'Ranunculaceae':   ('petal5', 'lobed', 0),
    'Lamiaceae':       ('lipped_spike', 'oval', 7),
    'Polygonaceae':    ('tiny_spike', 'lance', 0),
    'Apiaceae':        ('umbel', 'compound', 0),
    'Brassicaceae':    ('cross4', 'lance', 6),
    'Violaceae':       ('violet', 'heart', 6),
    'Saxifragaceae':   ('panicle_small', 'lobed', 7),
    'Caryophyllaceae': ('petal5_notched', 'lance', 0),
    'Caprifoliaceae':  ('two_lip', 'oval', 0),
    'Celastraceae':    ('tiny_cluster', 'oval', 6),
    'Aceraceae':       ('tiny_cluster', 'lobed', 0),
    'Scrophulariaceae': ('two_lip', 'lance', 5),
    'Orchidaceae':     ('orchid', 'blade', 0),
    'Fumariaceae':     ('spurred', 'compound', 0),
    'Convolvulaceae':  ('funnel', 'heart', 0),
    'Rutaceae':        ('petal5', 'compound', 0),
    'Verbenaceae':     ('panicle_small', 'oval', 6),
    'Vitaceae':        ('tiny_cluster', 'lobed', 0),
    'Solanaceae':      ('star5', 'oval', 0),
    'Salicaceae':      ('catkin', 'lance', 5),
    'Rhamnaceae':      ('tiny_cluster', 'oval', 5),
    'Lauraceae':       ('tiny_cluster', 'oval', 0),
    'Rubiaceae':       ('star5', 'oval', 0),
    'Oleaceae':        ('cross4', 'oval', 0),
    'Campanulaceae':   ('bell', 'lance', 6),
    'Araliaceae':      ('ball_cluster', 'lobed', 0),
    'Dioscoreaceae':   ('tiny_spike', 'heart', 0),
    'Crassulaceae':    ('star5', 'oval', 0),
    'Oxalidaceae':     ('star5', 'heart', 0),
    'Anacardiaceae':   ('panicle_small', 'compound', 0),
    'Araceae':         ('arum', 'lobed', 0),
    'Cornaceae':       ('cross4', 'oval', 0),
    'Actinidiaceae':   ('petal5', 'oval', 6),
    'Tiliaceae':       ('umbel', 'heart', 7),
    'Clusiaceae':      ('star5', 'oval', 0),
    'Boraginaceae':    ('star5', 'lance', 0),
    'Cucurbitaceae':   ('star5', 'lobed', 0),
    'Gentianaceae':    ('bell', 'lance', 0),
    'Aquifoliaceae':   ('tiny_cluster', 'oval', 8),
    'Primulaceae':     ('star5', 'oval', 6),
    'Onagraceae':      ('cross4', 'lance', 0),
    'Symplocaceae':    ('panicle_small', 'oval', 6),
    # 🔴 종모양이 아니다. 때죽나무·쪽동백나무는 **매달린 흰 5장 꽃**이다.
    #    bell로 그렸더니 마시멜로가 나왔다 — 규격 검사는 통과했다.
    'Styracaceae':     ('petal5', 'oval', 0),
    'Commelinaceae':   ('commelina', 'blade', 0),
    'Menispermaceae':  ('tiny_cluster', 'heart', 0),
    'Lardizabalaceae': ('tiny_cluster', 'compound', 0),
    'Pyrolaceae':      ('bell', 'oval', 0),
    'Elaeagnaceae':    ('bell', 'lance', 0),
    'Theaceae':        ('camellia', 'oval', 6),
    'Iridaceae':       ('iris', 'blade', 0),
    'Magnoliaceae':    ('magnolia', 'oval', 0),
    'Ericaceae':       ('bell', 'oval', 0),
    'Valerianaceae':   ('panicle_small', 'compound', 0),
    'Simaroubaceae':   ('tiny_cluster', 'compound', 0),
    'Ebenaceae':       ('bell', 'oval', 0),
    'Schisandraceae':  ('petal5', 'oval', 5),
    'Alismataceae':    ('petal5', 'lance', 0),
    'Berberidaceae':   ('star5', 'lobed', 6),
    'Lythraceae':      ('petal5', 'lance', 0),
    'Staphyleaceae':   ('panicle_small', 'compound', 0),
    'Phytolaccaceae':  ('tiny_spike', 'oval', 0),
    'Alangiaceae':     ('tiny_cluster', 'oval', 0),
    'Apocynaceae':     ('star5', 'lance', 0),
    'Papaveraceae':    ('poppy', 'lobed', 0),
    'Trapaceae':       ('cross4', 'lobed', 6),
    'Myrsinaceae':     ('star5', 'oval', 0),
    'Portulacaceae':   ('star5', 'oval', 0),
    'Pittosporaceae':  ('star5', 'oval', 0),
    'Sabiaceae':       ('panicle_small', 'oval', 0),
    'Geraniaceae':     ('petal5', 'lobed', 0),
    'Polygalaceae':    ('pea', 'lance', 0),
    'Meliaceae':       ('panicle_small', 'compound', 0),
    'Daphniphyllaceae': ('tiny_cluster', 'oval', 0),
    'Plumbaginaceae':  ('star5', 'lance', 0),
    'Aizoaceae':       ('ray_disc', 'blade', 0),
    'Menyanthaceae':   ('star5', 'oval', 0),
    'Malvaceae':       ('funnel', 'lobed', 6),
    'Chloranthaceae':  ('tiny_spike', 'oval', 7),
    'Adoxaceae':       ('panicle_small', 'lobed', 6),
}

# 과별 기본 꽃색. 종별 색을 모를 때의 최후 수단이다.
# 🔴 '몰라서 이 색을 썼다'는 것이 `색_근거` 열에 남아야 한다 —
#    안 남기면 검수자가 어느 장을 봐야 할지 모른다.
FAMILY_COLOR = {
    'Asteraceae': '#F0C048', 'Fabaceae': '#C888C0', 'Liliaceae': '#F0E0C0',
    'Rosaceae': '#F5F0EA', 'Ranunculaceae': '#F0C848', 'Lamiaceae': '#A888C8',
    'Polygonaceae': '#F0D8DC', 'Apiaceae': '#F5F2EC', 'Brassicaceae': '#F5F5F0',
    'Violaceae': '#9888C8', 'Saxifragaceae': '#F5F0E8', 'Caryophyllaceae': '#F8F5F0',
    'Caprifoliaceae': '#F5EFE6', 'Celastraceae': '#C8D0A0', 'Aceraceae': '#C8CC88',
    'Scrophulariaceae': '#B090C8', 'Orchidaceae': '#E8C0D0', 'Fumariaceae': '#C0A0D0',
    'Convolvulaceae': '#B0A0D8', 'Rutaceae': '#F0F0E4', 'Verbenaceae': '#B49AD0',
    'Vitaceae': '#BCC890', 'Solanaceae': '#F0F0E8', 'Salicaceae': '#D8D8B0',
    'Rhamnaceae': '#C8D098', 'Lauraceae': '#D8D890', 'Rubiaceae': '#F2F2EA',
    'Oleaceae': '#F0F0E0', 'Campanulaceae': '#A090C8', 'Araliaceae': '#E0E4C8',
    'Dioscoreaceae': '#D0D4A0', 'Crassulaceae': '#F0D850', 'Oxalidaceae': '#F0D060',
    'Anacardiaceae': '#E0E0B0', 'Araceae': '#C0C888', 'Cornaceae': '#F0F0E4',
    'Actinidiaceae': '#F5F2E8', 'Tiliaceae': '#F0E8B8', 'Clusiaceae': '#F0D050',
    'Boraginaceae': '#98A8D8', 'Cucurbitaceae': '#F0D858', 'Gentianaceae': '#7888C8',
    'Aquifoliaceae': '#F0F0E8', 'Primulaceae': '#F0D058', 'Onagraceae': '#F0D060',
    'Symplocaceae': '#F5F2EC', 'Styracaceae': '#F8F5F0', 'Commelinaceae': '#7898D8',
    'Menispermaceae': '#D8DCA8', 'Lardizabalaceae': '#C8A0C0', 'Pyrolaceae': '#F2F0E8',
    'Elaeagnaceae': '#F0E8C0', 'Theaceae': '#F5F0E8', 'Iridaceae': '#8878C0',
    'Magnoliaceae': '#F8F4EC', 'Ericaceae': '#F0DCE0', 'Valerianaceae': '#F0E8C0',
    'Simaroubaceae': '#D8DCA0', 'Ebenaceae': '#E8E4C0', 'Schisandraceae': '#F0E8D8',
    'Alismataceae': '#F8F5F0', 'Berberidaceae': '#F0D860', 'Lythraceae': '#D890B0',
    'Staphyleaceae': '#F2EEE4', 'Phytolaccaceae': '#F0E8E8', 'Alangiaceae': '#F0EEE0',
    'Apocynaceae': '#F0EEE4', 'Papaveraceae': '#E07050', 'Trapaceae': '#F8F5F0',
    'Myrsinaceae': '#F0EEE8', 'Portulacaceae': '#E890A8', 'Pittosporaceae': '#F5F2E8',
    'Sabiaceae': '#E8E8C0', 'Geraniaceae': '#D898B8', 'Polygalaceae': '#A890C8',
    'Meliaceae': '#F0EEE0', 'Daphniphyllaceae': '#D8DCA8', 'Plumbaginaceae': '#D8A0C0',
    'Aizoaceae': '#E888A8', 'Menyanthaceae': '#F5F2EC', 'Malvaceae': '#E8A0B8',
    'Chloranthaceae': '#E8E8D0', 'Adoxaceae': '#F2EEE4',
}

FORM_FN = {
    'ray_disc': 'f_ray_disc', 'ray_disc_big': 'f_ray_disc_big',
    'petal5': 'f_petal5', 'petal5_notched': 'f_petal5_notched',
    'cross4': 'f_cross4', 'tepal6': 'f_tepal6', 'pea': 'f_pea',
    'lipped_spike': 'f_lipped_spike', 'tiny_spike': 'f_tiny_spike',
    'umbel': 'f_umbel', 'violet': 'f_violet', 'bell': 'f_bell',
    'funnel': 'f_funnel', 'two_lip': 'f_two_lip',
    'panicle_small': 'f_panicle_small', 'ball_cluster': 'f_ball_cluster',
    'tiny_cluster': 'f_tiny_cluster', 'catkin': 'f_catkin', 'iris': 'f_iris',
    'orchid': 'f_orchid', 'spurred': 'f_spurred', 'magnolia': 'f_magnolia',
    'camellia': 'f_camellia', 'commelina': 'f_commelina', 'arum': 'f_arum',
    'poppy': 'f_poppy', 'star5': 'f_star5',
}
