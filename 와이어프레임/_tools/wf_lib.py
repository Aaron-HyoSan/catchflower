# -*- coding: utf-8 -*-
"""캐치플라워 와이어프레임 SVG 생성용 공용 컴포넌트.

와이어프레임 규칙
- 회색조만 사용 (색은 디자이너 몫)
- 실선 = 확정 요소 / 점선 = 이미지·사진 placeholder
- 화면 안 모든 문구는 실제 카피 초안 (더미 텍스트 금지)
- 오른쪽 주석열에 번호 달아 스펙 설명
"""

W_CANVAS = 1000
H_CANVAS = 1080

# 기기 프레임 (iPhone 논리 해상도 기준)
FX, FY = 56, 116          # 프레임 좌상단
FW, FH = 375, 812         # 프레임 크기
AX = FX + FW + 60         # 주석열 x

FONT = "'Apple SD Gothic Neo','Noto Sans KR','Malgun Gothic',sans-serif"

INK = "#1a1a1a"
SUB = "#6b6b6b"
HINT = "#9a9a9a"
LINE = "#c8c8c8"
FILL = "#f4f4f4"
DARK = "#333333"

NAV_H = 64
HEADER_H = 52
STATUS_H = 44


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def t(x, y, s, size=14, weight=400, fill=INK, anchor="start", op=1.0, ls=0):
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" '
            f'font-weight="{weight}" fill="{fill}" text-anchor="{anchor}" '
            f'opacity="{op}" letter-spacing="{ls}">{esc(s)}</text>')


def rect(x, y, w, h, r=0, fill="none", stroke=LINE, sw=1, dash=None, op=1.0):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{d} opacity="{op}"/>')


def line(x1, y1, x2, y2, stroke=LINE, sw=1, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" '
            f'stroke-width="{sw}"{d}/>')


def circ(cx, cy, r, fill="none", stroke=LINE, sw=1, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{sw}"{d}/>')


def path(d, fill="none", stroke=LINE, sw=1, dash=None):
    ds = f' stroke-dasharray="{dash}"' if dash else ""
    return f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{ds}/>'


# ---------------------------------------------------------------- placeholder
def imgph(x, y, w, h, label="사진", r=8, note=None):
    """점선 박스 + 대각선 X = 이미지 자리."""
    o = [rect(x, y, w, h, r=r, fill="#fafafa", stroke=HINT, dash="4 3"),
         line(x, y, x + w, y + h, stroke="#e0e0e0"),
         line(x + w, y, x, y + h, stroke="#e0e0e0")]
    o.append(t(x + w / 2, y + h / 2 + 4, label, 11, 400, HINT, "middle"))
    if note:
        o.append(t(x + w / 2, y + h / 2 + 19, note, 9, 400, HINT, "middle"))
    return "".join(o)


def flowerph(cx, cy, r, label=None, dash=True):
    """꽃 일러스트 자리 (원형 + 꽃 아이콘 실루엣)."""
    o = [circ(cx, cy, r, fill="#fafafa", stroke=HINT, dash="4 3" if dash else None)]
    p = r * 0.30
    for i in range(5):
        import math
        a = math.radians(-90 + i * 72)
        o.append(circ(cx + math.cos(a) * p * 1.25, cy + math.sin(a) * p * 1.25, p,
                      fill="none", stroke="#dcdcdc"))
    o.append(circ(cx, cy, p * 0.62, fill="none", stroke="#dcdcdc"))
    if label:
        o.append(t(cx, cy + r + 15, label, 10, 400, SUB, "middle"))
    return "".join(o)


# ---------------------------------------------------------------- 버튼
def btn(x, y, w, h, label, kind="primary", size=15, sub=None):
    """kind: primary(채움) / secondary(테두리) / ghost(연한) / disabled"""
    if kind == "primary":
        o = [rect(x, y, w, h, r=h / 2, fill=DARK, stroke=DARK)]
        o.append(t(x + w / 2, y + h / 2 + size * 0.36, label, size, 700, "#ffffff", "middle"))
    elif kind == "secondary":
        o = [rect(x, y, w, h, r=h / 2, fill="#ffffff", stroke=DARK, sw=1.5)]
        o.append(t(x + w / 2, y + h / 2 + size * 0.36, label, size, 700, DARK, "middle"))
    elif kind == "disabled":
        o = [rect(x, y, w, h, r=h / 2, fill="#ededed", stroke="#ededed")]
        o.append(t(x + w / 2, y + h / 2 + size * 0.36, label, size, 700, HINT, "middle"))
    else:  # ghost
        o = [rect(x, y, w, h, r=h / 2, fill=FILL, stroke=LINE)]
        o.append(t(x + w / 2, y + h / 2 + size * 0.36, label, size, 500, SUB, "middle"))
    if sub:
        o.append(t(x + w / 2, y + h + 15, sub, 10, 400, HINT, "middle"))
    return "".join(o)


def chip(x, y, label, active=False, size=12, padx=12, h=28):
    w = len(label) * (size * 0.92 if any(ord(c) > 127 for c in label) else size * 0.58) + padx * 2
    if active:
        o = [rect(x, y, w, h, r=h / 2, fill=DARK, stroke=DARK),
             t(x + w / 2, y + h / 2 + 4, label, size, 700, "#ffffff", "middle")]
    else:
        o = [rect(x, y, w, h, r=h / 2, fill="#ffffff", stroke=LINE),
             t(x + w / 2, y + h / 2 + 4, label, size, 400, SUB, "middle")]
    return "".join(o), w


def toggle(x, y, on=True, label=None, w=44, h=26):
    o = [rect(x, y, w, h, r=h / 2, fill=DARK if on else "#e4e4e4",
              stroke=DARK if on else LINE)]
    o.append(circ(x + (w - h / 2) if on else x + h / 2, y + h / 2, h / 2 - 3,
                  fill="#ffffff", stroke="none"))
    if label:
        o.append(t(x - 10, y + h / 2 + 4, label, 13, 400, INK, "end"))
    return "".join(o)


def radio(x, y, label, on=False, size=13):
    o = [circ(x + 9, y + 9, 9, fill="#ffffff", stroke=DARK if on else LINE, sw=1.5)]
    if on:
        o.append(circ(x + 9, y + 9, 4.5, fill=DARK, stroke="none"))
    o.append(t(x + 27, y + 14, label, size, 700 if on else 400, INK if on else SUB))
    return "".join(o)


def field(x, y, w, label, placeholder, h=48, value=None):
    o = [t(x, y - 8, label, 12, 700, SUB)]
    o.append(rect(x, y, w, h, r=10, fill="#ffffff", stroke=LINE))
    o.append(t(x + 14, y + h / 2 + 5, value or placeholder, 14, 400,
               INK if value else HINT))
    return "".join(o)


# ---------------------------------------------------------------- 프레임 파츠
def statusbar(x=FX, y=FY, w=FW):
    o = [t(x + 26, y + 27, "9:41", 13, 700, INK)]
    # 신호/와이파이/배터리 약식
    for i in range(4):
        o.append(rect(x + w - 78 + i * 5, y + 22 - i * 2.2, 3, 6 + i * 2.2, r=1,
                      fill=INK, stroke="none"))
    o.append(path(f"M {x+w-52} {y+22} a 7 7 0 0 1 11 0", stroke=INK, sw=1.6))
    o.append(rect(x + w - 34, y + 15, 20, 10, r=2.5, fill="none", stroke=INK))
    o.append(rect(x + w - 32, y + 17, 14, 6, r=1, fill=INK, stroke="none"))
    return "".join(o)


def header(title, y=None, back=False, right=None, x=FX, w=FW, sub=None):
    y = y if y is not None else FY + STATUS_H
    o = [rect(x, y, w, HEADER_H, fill="#ffffff", stroke="none"),
         line(x, y + HEADER_H, x + w, y + HEADER_H, stroke="#ededed")]
    if back:
        o.append(path(f"M {x+27} {y+18} L {x+18} {y+26} L {x+27} {y+34}",
                      stroke=INK, sw=1.8))
        o.append(t(x + w / 2, y + 31, title, 16, 700, INK, "middle"))
    else:
        o.append(t(x + 20, y + 32, title, 19, 700, INK))
    if right:
        o.append(t(x + w - 20, y + 31, right, 13, 500, SUB, "end"))
    if sub:
        o.append(t(x + 20, y + 47, sub, 11, 400, HINT))
    return "".join(o)


NAV_ITEMS = ["도감", "지도", "꽃 촬영", "랭킹", "마이"]


def bottomnav(active="도감", x=FX, y=None, w=FW):
    """하단 내비 5개. 중앙 '꽃 촬영'은 크게 튀어나온 원형 버튼."""
    y = y if y is not None else FY + FH - NAV_H - 24
    o = [rect(x, y, w, NAV_H + 24, fill="#ffffff", stroke="none"),
         line(x, y, x + w, y, stroke="#e4e4e4")]
    slot = w / 5
    icons = {"도감": "book", "지도": "map", "랭킹": "trophy", "마이": "person"}
    for i, name in enumerate(NAV_ITEMS):
        cx = x + slot * i + slot / 2
        if name == "꽃 촬영":
            o.append(circ(cx, y + 12, 34, fill=DARK, stroke="#ffffff", sw=4))
            # 카메라 픽토그램
            o.append(rect(cx - 13, y + 3, 26, 19, r=4, fill="none", stroke="#ffffff", sw=1.8))
            o.append(circ(cx, y + 12.5, 6, fill="none", stroke="#ffffff", sw=1.8))
            o.append(rect(cx - 5, y - 1, 10, 4, r=1.5, fill="#ffffff", stroke="none"))
            o.append(t(cx, y + 60, "꽃 촬영", 11, 700, DARK, "middle"))
            continue
        on = (name == active)
        col = INK if on else HINT
        k = icons[name]
        iy = y + 22
        if k == "book":
            o.append(rect(cx - 10, iy - 9, 20, 18, r=2, fill="none", stroke=col, sw=1.6))
            o.append(line(cx, iy - 9, cx, iy + 9, stroke=col, sw=1.6))
        elif k == "map":
            o.append(path(f"M {cx-11} {iy-8} L {cx-3} {iy-10} L {cx+3} {iy+8} "
                          f"L {cx+11} {iy+6} L {cx+11} {iy-6} L {cx+3} {iy-8} "
                          f"L {cx-3} {iy+10} L {cx-11} {iy+8} Z", stroke=col, sw=1.6))
        elif k == "trophy":
            o.append(path(f"M {cx-8} {iy-9} L {cx+8} {iy-9} L {cx+7} {iy} "
                          f"a 7 7 0 0 1 -14 0 Z", stroke=col, sw=1.6))
            o.append(line(cx, iy + 7, cx, iy + 10, stroke=col, sw=1.6))
            o.append(line(cx - 6, iy + 10, cx + 6, iy + 10, stroke=col, sw=1.6))
        else:
            o.append(circ(cx, iy - 4, 5.5, fill="none", stroke=col, sw=1.6))
            o.append(path(f"M {cx-9} {iy+10} a 9 8 0 0 1 18 0", stroke=col, sw=1.6))
        o.append(t(cx, y + 48, name, 11, 700 if on else 400, col, "middle"))
    # 홈 인디케이터
    o.append(rect(x + w / 2 - 60, y + NAV_H + 14, 120, 4, r=2, fill="#d8d8d8", stroke="none"))
    return "".join(o)


def frame_shell(x=FX, y=FY, w=FW, h=FH):
    return (rect(x - 1, y - 1, w + 2, h + 2, r=30, fill="#ffffff", stroke="#8f8f8f", sw=1.5) +
            rect(x + w / 2 - 40, y + 6, 80, 5, r=3, fill="#ececec", stroke="none"))


# ---------------------------------------------------------------- 주석
def annots(items, x=AX, y=FY + 8, w=400, screen_id="", title="", flow=None):
    """오른쪽 주석열. items = [(번호, 제목, 설명)]"""
    o = []
    o.append(t(x, y, f"{screen_id}", 12, 700, HINT, ls=1.5))
    o.append(t(x, y + 26, title, 22, 700, INK))
    cy = y + 52
    if flow:
        o.append(t(x, cy, "흐름", 11, 700, SUB, ls=1))
        cy += 17
        for ln in flow:
            o.append(t(x, cy, ln, 11.5, 400, SUB))
            cy += 16
        cy += 8
    o.append(line(x, cy, x + w, cy, stroke="#e8e8e8"))
    cy += 22
    for num, head, desc in items:
        o.append(circ(x + 9, cy - 5, 9.5, fill=DARK, stroke="none"))
        o.append(t(x + 9, cy - 1, str(num), 10.5, 700, "#ffffff", "middle"))
        o.append(t(x + 26, cy, head, 12.5, 700, INK))
        cy += 16
        for ln in wrap(desc, 46):
            o.append(t(x + 26, cy, ln, 11.5, 400, SUB))
            cy += 15
        cy += 11
    return "".join(o)


# 렌더 옵션. 흐름도(24번)처럼 화면을 축소해 쓸 때 주석 마커를 끈다.
# screens_a/b 는 `from wf_lib import *` 로 함수만 가져가므로,
# 플래그는 호출 시점에 이 딕셔너리를 읽어야 외부에서 바꿀 수 있다.
_OPTS = {"markers": True}


def marker(num, x, y, r=10):
    """프레임 위 번호 마커."""
    if not _OPTS["markers"]:
        return ""
    return (circ(x, y, r, fill="#ffffff", stroke=DARK, sw=1.5) +
            t(x, y + 4, str(num), 11, 700, DARK, "middle"))


def wrap(s, n):
    """한글 폭 고려한 단순 줄바꿈 (한글=2, 영문=1 기준)."""
    out, cur, wid = [], "", 0
    for ch in s:
        if ch == "\n":
            out.append(cur); cur, wid = "", 0; continue
        cw = 2 if ord(ch) > 127 else 1
        if wid + cw > n * 1.35 and ch == " ":
            out.append(cur); cur, wid = "", 0; continue
        cur += ch; wid += cw
        if wid > n * 1.35:
            out.append(cur); cur, wid = "", 0
    if cur:
        out.append(cur)
    return out


def svg(body, w=W_CANVAS, h=H_CANVAS, title=""):
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">\n'
            f'<title>{esc(title)}</title>\n'
            f'<rect width="{w}" height="{h}" fill="#ffffff"/>\n'
            f'{body}\n</svg>\n')


def page_header(name, group, x=FX - 4, y=64):
    return (t(x, y, "캐치플라워 · 와이어프레임 v0.1", 11, 700, HINT, ls=1) +
            t(x, y + 22, name, 17, 700, INK) +
            t(x + W_CANVAS - FX - 40, y + 22, group, 11, 400, HINT, "end"))


def clip_cover(x=FX, y=FY, w=FW, h=FH, pad=26, tail=340):
    """프레임 밖으로 넘친 콘텐츠를 흰색으로 덮어 잘라낸다 (스크롤 영역 표현).
    evenodd 규칙: 바깥 사각형 - 둥근 프레임 = 테두리 밖 영역만 칠함.
    markers/annots 보다 먼저 그려야 한다."""
    ox, oy = x - pad, y - pad
    ow, oh = w + pad * 2, h + pad + tail
    r = 30
    outer = f"M {ox} {oy} H {ox+ow} V {oy+oh} H {ox} Z"
    inner = (f"M {x+r} {y} H {x+w-r} A {r} {r} 0 0 1 {x+w} {y+r} "
             f"V {y+h-r} A {r} {r} 0 0 1 {x+w-r} {y+h} "
             f"H {x+r} A {r} {r} 0 0 1 {x} {y+h-r} "
             f"V {y+r} A {r} {r} 0 0 1 {x+r} {y} Z")
    return (f'<path d="{outer} {inner}" fill="#ffffff" fill-rule="evenodd" '
            f'stroke="none"/>'
            + rect(x - 1, y - 1, w + 2, h + 2, r=30, fill="none", stroke="#8f8f8f", sw=1.5))


def scroll_hint(x=FX, y=None, w=FW, label="스크롤"):
    y = y if y is not None else FY + FH - NAV_H - 60
    return (rect(x + w - 6, y, 3, 60, r=1.5, fill="#dcdcdc", stroke="none"))
