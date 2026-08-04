# -*- coding: utf-8 -*-
"""화면 24 : 실제 화면 축소판으로 만든 흐름도.

23번 흐름도는 사각형 박스라 '무엇이 어디로 가는지'는 보이지만 '어떤 화면인지'는
안 보인다. 이 화면은 01~22의 실제 SVG 본문을 축소해 배치하고 그 사이를 화살표로
잇는다. 박스 대신 진짜 화면을 보며 흐름을 읽는 용도.

구현 방식
- 각 화면 함수는 svg() 로 완결된 문서를 반환하므로 그대로는 못 합친다.
  래퍼를 벗겨 내부 콘텐츠만 꺼내고(strip_svg), 프레임 영역만 <clipPath> 로 잘라
  <g transform=scale> 로 축소한다. 주석열과 페이지 헤더는 클립 밖이라 자동 제외된다.
- 주석 마커는 축소하면 판독이 안 되므로 wf_lib._OPTS["markers"] 로 끈다 (build.py 담당).
- 화면을 가로지르는 긴 화살표 대신 연결 기호(A)를 쓴다. 1500px 선이 다른 화면을
  통과하면 흐름이 오히려 안 읽힌다.
"""
import re

from wf_lib import (DARK, FH, FILL, FW, FX, FY, HINT, INK, LINE, SUB,
                    circ, line, rect, svg, t)
from screens_a import SCREENS_A
from screens_b import SCREENS_B

# 흐름도에 넣을 화면 (23번 흐름도 자신은 제외)
ALL = {name.split("_")[0]: fn for name, fn in SCREENS_A + SCREENS_B}

SCALE = 0.30                      # 축소 배율
TW = FW * SCALE                   # 축소 폭   112.5
TH = FH * SCALE                   # 축소 높이 243.6

W, H = 2260, 1860
Y1, Y2, Y3 = 150, 700, 1400       # 행 기준선


def strip_svg(s):
    """svg() 래퍼를 벗기고 내부 콘텐츠만 반환."""
    s = s[s.index(">", s.index("<svg")) + 1:]
    s = s[:s.rindex("</svg>")]
    s = re.sub(r"<title>.*?</title>", "", s, flags=re.S)
    s = re.sub(r'<rect width="\d+" height="\d+" fill="#ffffff"/>', "", s, count=1)
    return s


_uid = [0]


def thumb(sid, x, y, label=None, strong=False, shift=0):
    """화면 sid 의 프레임 영역만 잘라 (x, y) 에 축소 배치한다.

    shift : 프레임 좌표계 기준으로 콘텐츠를 위로 끌어올리는 양. 06 도감 필터처럼
            바텀시트가 화면 아래쪽에만 있는 경우 상단 딤 영역이 회색 덩어리로만
            보이므로, 시트를 끌어올려 실제 내용이 보이게 한다.
    """
    _uid[0] += 1
    cid = f"c{_uid[0]}"
    body = strip_svg(ALL[sid]())
    tx, ty = x - FX * SCALE, y - (FY + shift) * SCALE
    h = (FH - shift) * SCALE          # 끌어올린 만큼 높이도 줄여 빈 흰 칸을 없앤다
    o = [f'<clipPath id="{cid}">'
         f'<rect x="{x}" y="{y}" width="{TW:.1f}" height="{h:.1f}" rx="9"/></clipPath>',
         f'<g clip-path="url(#{cid})">',
         f'<rect x="{x}" y="{y}" width="{TW:.1f}" height="{h:.1f}" fill="#ffffff"/>',
         f'<g transform="translate({tx:.2f},{ty:.2f}) scale({SCALE})">', body, '</g></g>',
         rect(x, y, TW, h, r=9, fill="none",
              stroke=DARK if strong else "#b4b4b4", sw=2 if strong else 1),
         circ(x + 1, y - 1, 11, fill=DARK, stroke="#ffffff", sw=2),
         t(x + 1, y + 2.5, sid, 10, 700, "#ffffff", "middle")]
    if label:
        for i, ln in enumerate(label.split("\n")):
            o.append(t(x + TW / 2, y + TH + 15 + i * 13, ln,
                       10.5 if i == 0 else 9.5, 700 if i == 0 else 400,
                       INK if i == 0 else HINT, "middle"))
    return "".join(o)


def arrow(x1, y1, x2, y2, dash=None, label=None, lx=None, ly=None, bend=None):
    """직선 또는 직각 꺾임 화살표. bend='h' 수평 먼저 / 'v' 수직 먼저."""
    import math
    col = SUB if not dash else HINT
    o = []
    if bend == "h":
        o += [line(x1, y1, x2, y1, stroke=col, sw=1.3, dash=dash),
              line(x2, y1, x2, y2, stroke=col, sw=1.3, dash=dash)]
        a = math.pi / 2 if y2 > y1 else -math.pi / 2
    elif bend == "v":
        o += [line(x1, y1, x1, y2, stroke=col, sw=1.3, dash=dash),
              line(x1, y2, x2, y2, stroke=col, sw=1.3, dash=dash)]
        a = 0 if x2 > x1 else math.pi
    else:
        o.append(line(x1, y1, x2, y2, stroke=col, sw=1.3, dash=dash))
        a = math.atan2(y2 - y1, x2 - x1)
    for sg in (1, -1):
        o.append(line(x2, y2, x2 - 7 * math.cos(a - sg * 0.45),
                      y2 - 7 * math.sin(a - sg * 0.45), stroke=col, sw=1.3))
    if label:
        px = lx if lx is not None else (x1 + x2) / 2
        py = ly if ly is not None else (y1 + y2) / 2 - 5
        wd = len(label) * 5.6 + 12
        o += [rect(px - wd / 2, py - 9, wd, 14, r=7, fill="#ffffff", stroke="none"),
              t(px, py + 1.5, label, 9, 400, HINT, "middle")]
    return "".join(o)


def band(x, y, w, h, title, note=None):
    """단계 묶음 배경. 제목·부연은 y+20 / y+35 에 놓이고 콘텐츠는 y+64 부터."""
    o = [rect(x, y, w, h, r=14, fill="#fbfbfa", stroke="#e8e8e6", dash="5 4"),
         t(x + 14, y + 20, title, 11.5, 700, SUB, ls=1.2)]
    if note:
        o.append(t(x + 14, y + 35, note, 9.5, 400, HINT))
    return "".join(o)


def cmark(letter, x, y, note=None, anchor="start"):
    """연결 기호. 멀리 떨어진 화면을 긴 선 없이 잇는다.

    anchor : 'start' 오른쪽에 설명 / 'end' 왼쪽에 설명 / 'above' 기호 위에 설명.
    """
    o = [circ(x, y, 13, fill="#ffffff", stroke=DARK, sw=1.6),
         t(x, y + 4.5, letter, 12, 700, DARK, "middle")]
    if note:
        if anchor == "above":
            o.append(t(x, y - 20, note, 10, 400, SUB, "middle"))
        else:
            dx = 20 if anchor == "start" else -20
            o.append(t(x + dx, y + 4, note, 10, 400, SUB, anchor))
    return "".join(o)


def s24():
    o = [t(60, 54, "캐치플라워 · 화면 흐름 지도", 24, 700, INK),
         t(60, 78, "실제 와이어프레임 축소판으로 본 전체 동선 · 실선 = 주 동선 / 점선 = 조건 분기 · 실패",
           12.5, 400, SUB),
         t(W - 60, 54, "v0.1", 11, 700, HINT, "end"),
         t(W - 60, 74, "화면 1장 = 와이어프레임 1장 (30% 축소) · 번호는 파일명과 같다",
           10, 400, HINT, "end")]

    # ══════════════════════════════════ STEP 1 · 온보딩
    o.append(band(60, Y1 - 64, 860, TH + 148, "STEP 1 · 온보딩",
                  "최초 1회. 활동 지역은 6개월 잠금이라 여기가 가장 큰 이탈 지점이다"))
    ox = 96
    for i, (sid, lb) in enumerate([
            ("01", "로그인\n카카오 · 애플 · 번호"),
            ("02", "활동 지역 선택\n동 단위 · 6개월 잠금"),
            ("03", "권한 안내\n카메라만 필수"),
            ("22", "빈 상태 도감\n0 / 200종 · 제철 추천")]):
        o.append(thumb(sid, ox + i * 200, Y1, lb))
    for i in range(3):
        o.append(arrow(ox + TW + i * 200 + 6, Y1 + TH / 2,
                       ox + 200 + i * 200 - 6, Y1 + TH / 2))

    # ══════════════════════════════════ STEP 2 · 메인 5탭
    o.append(band(960, Y1 - 64, 1010, TH + 148, "STEP 2 · 메인 5탭",
                  "하단 내비게이션. 도감이 첫 화면이다 (기획서 12장)"))
    mx = 996
    for i, (sid, lb, st) in enumerate([
            ("04", "도감 홈  ← 첫 화면\n모은 꽃 · 200종 그리드", True),
            ("14", "지도\n장소 핀 · 필터", False),
            ("17", "랭킹 · 우리 동네\n시즌 · 내 순위", False),
            ("18", "랭킹 · 친구\n시상대 · 격차 종수", False),
            ("20", "마이페이지\n프로필 · 배지 · 설정", False)]):
        o.append(thumb(sid, mx + i * 200, Y1, lb, strong=st))
    o.append(arrow(ox + 600 + TW + 6, Y1 + TH / 2, mx - 6, Y1 + TH / 2,
                   dash="4 3", label="첫 꽃을 등록하면",
                   lx=(ox + 600 + TW + mx) / 2, ly=Y1 + TH / 2 - 7))
    # 탭 자유 이동 표시
    bar = Y1 + TH + 52
    o.append(line(mx + TW / 2, bar, mx + 800 + TW / 2, bar, stroke=LINE, sw=1.3))
    for i in range(5):
        cx = mx + i * 200 + TW / 2
        o.append(line(cx, bar - 10, cx, bar, stroke=LINE, sw=1.3))
        o.append(circ(cx, bar, 2.6, fill=SUB, stroke="none"))
    o.append(t(mx + 452, bar + 18, "하단 내비게이션 · 어느 탭에서든 자유 이동",
               10, 400, HINT, "middle"))

    # 시즌 마감 → STEP 5 연결 기호
    o.append(arrow(mx + 600 + TW / 2, Y1 + TH + 30, mx + 600 + TW / 2, bar + 32))
    o.append(cmark("A", mx + 600 + TW / 2, bar + 46, "6개월 시즌 마감 시"))

    # ══════════════════════════════════ STEP 3 · 핵심 루프
    o.append(band(60, 524, 1340, 750, "STEP 3 · 핵심 루프 · 촬영과 AI 인증",
                  "어느 탭에서든 중앙 버튼으로 진입. 사용자가 가장 자주 보는 흐름이다"))
    c = 96
    o.append(thumb("07", c, Y2, "카메라\n앨범 업로드 불가", strong=True))
    o.append(thumb("08", c + 200, Y2, "AI 분석 중\n약 5초"))
    o.append(thumb("09", c + 400, Y2, "판별 결과 확인\n이 꽃은 OO인가요?", strong=True))
    o.append(thumb("10", c + 640, Y2 - 120, "신규 꽃 등록\n시즌 종수 +1", strong=True))
    o.append(thumb("11", c + 640, Y2 + 140, "기존 꽃 재발견\n시즌 종수 유지"))
    o.append(thumb("12", c + 200, Y2 + 290, "판별 실패\n재촬영 팁 4개"))
    o.append(thumb("13", c + 1120, Y2, "지도 공유 설정\n장소 · 공개범위 · 한 줄", strong=True))

    # 진입 (메인 → 07)
    o.append(arrow(mx + 400 + TW / 2, 484, c + TW / 2, Y2 - 6, bend="h",
                   dash="4 3", label="중앙 '꽃 촬영' 버튼",
                   lx=760, ly=477))
    # 07 → 08 → 09
    for i in range(2):
        o.append(arrow(c + TW + i * 200 + 6, Y2 + TH / 2,
                       c + 200 + i * 200 - 6, Y2 + TH / 2))
    # 09 분기
    o.append(arrow(c + 400 + TW + 6, Y2 + 70, c + 640 - 6, Y2 - 120 + TH / 2,
                   label="처음 발견한 꽃", lx=672, ly=676))
    o.append(arrow(c + 400 + TW + 6, Y2 + 180, c + 640 - 6, Y2 + 140 + TH / 2,
                   label="이미 도감에 있는 꽃", lx=672, ly=968))
    # 10 · 11 → 13
    o.append(arrow(c + 640 + TW + 6, Y2 - 120 + TH / 2, c + 1120 - 6, Y2 + 80,
                   label="지도에 공유하기", lx=1030, ly=694))
    o.append(arrow(c + 640 + TW + 6, Y2 + 140 + TH / 2, c + 1120 - 6, Y2 + 190,
                   label="지도에 공유하기", lx=1030, ly=956))
    # 09 → 12 (실패)
    o.append(arrow(c + 400 + TW / 2, Y2 + TH + 8, c + 200 + TW + 8, Y2 + 412,
                   bend="v", dash="4 3", label="어떤 꽃인지 알 수 없음",
                   lx=580, ly=1088))
    # 12 → 07 (재촬영)
    o.append(arrow(c + 200 - 8, Y2 + 412, c + TW / 2, Y2 + TH + 8,
                   bend="h", dash="4 3", label="다시 찍기", lx=196, ly=1136))

    # ══════════════════════════════════ 범례
    lx0, ly0 = 1440, 524
    o.append(rect(lx0, ly0, 760, 750, r=14, fill=FILL, stroke="none"))
    o.append(t(lx0 + 28, ly0 + 34, "읽는 법", 14, 700, INK))
    ly = ly0 + 66
    for kind, desc in [
            ("solid", "주 동선 · 대부분의 사용자가 지나간다"),
            ("dash", "조건 분기 · 실패 · 이탈 경로"),
            ("box", "핵심 화면 · 여기서 이탈하면 루프가 끊긴다"),
            ("num", "화면 번호 · 와이어프레임 파일명과 같다"),
            ("mark", "연결 기호 · 같은 글자끼리 이어진다")]:
        if kind == "solid":
            o.append(line(lx0 + 28, ly, lx0 + 76, ly, stroke=SUB, sw=1.3))
        elif kind == "dash":
            o.append(line(lx0 + 28, ly, lx0 + 76, ly, stroke=HINT, sw=1.3, dash="4 3"))
        elif kind == "box":
            o.append(rect(lx0 + 28, ly - 8, 48, 16, r=3, fill="none", stroke=DARK, sw=2))
        elif kind == "num":
            o.append(circ(lx0 + 40, ly, 10, fill=DARK, stroke="none"))
            o.append(t(lx0 + 40, ly + 3.5, "04", 9, 700, "#ffffff", "middle"))
        else:
            o.append(circ(lx0 + 40, ly, 11, fill="#ffffff", stroke=DARK, sw=1.5))
            o.append(t(lx0 + 40, ly + 4, "A", 10.5, 700, DARK, "middle"))
        o.append(t(lx0 + 96, ly + 4, desc, 11, 400, SUB))
        ly += 30

    ly += 16
    o.append(line(lx0 + 28, ly, lx0 + 732, ly, stroke="#e2e2e2"))
    ly += 30
    o.append(t(lx0 + 28, ly, "핵심 루프 · 이 한 바퀴가 앱의 전부다", 13, 700, INK))
    ly += 22
    loop = [("꽃 발견", "산책 중"), ("직접 촬영", "07"), ("AI 인증", "08 · 09"),
            ("도감 등록", "10 · 11"), ("지도 공유", "13 · 14"), ("수집 경쟁", "17 · 18")]
    for i, (st, ref) in enumerate(loop):
        col, row = i % 3, i // 3
        bx, by = lx0 + 28 + col * 240, ly + row * 56
        o.append(rect(bx, by, 216, 42, r=10, fill="#ffffff", stroke=LINE))
        o.append(t(bx + 14, by + 19, f"{i+1}. {st}", 11.5, 700, INK))
        o.append(t(bx + 14, by + 33, ref, 9.5, 400, HINT))
        if col < 2 and i < len(loop) - 1:
            o.append(arrow(bx + 220, by + 21, bx + 236, by + 21))
    ly += 130
    o.append(t(lx0 + 28, ly, "07 → 08 → 09 → 10 → 13 → 14 → 04 · 화면 7장으로 한 바퀴가 돈다",
               10.5, 400, HINT))
    ly += 18
    o.append(t(lx0 + 28, ly, "재발견(11)은 시즌 종수를 늘리지 않는다. "
               "이 규칙을 사용자가 학습하는 지점이 10과 11의 위계 차이다",
               10.5, 400, HINT))

    # ══════════════════════════════════ STEP 4 · 결과 반영
    o.append(band(60, Y3 - 64, 940, TH + 120, "STEP 4 · 결과 반영",
                  "공유 여부와 무관하게 도감에는 남는다 (기획서 8장)"))
    r0 = 96
    for i, (sid, lb, src) in enumerate([
            ("05", "도감 상세\n발견 기록 누적", "04 도감 셀 선택"),
            ("15", "장소 상세\n꽃 목록 · 길찾기", "13 공유 완료 → 14 지도"),
            ("16", "기록 상세\n좋아요 · 댓글", None),
            ("06", "도감 필터\n계절 · 색 · 희귀도 (바텀시트)", "04 필터")]):
        x = r0 + i * 200
        # 06 은 바텀시트라 위쪽 딤 영역을 잘라내야 내용이 보인다
        o.append(thumb(sid, x, Y3, lb, shift=240 if sid == "06" else 0))
        if src:
            o.append(arrow(x + TW / 2, Y3 - 78, x + TW / 2, Y3 - 6,
                           dash="4 3" if sid == "06" else None,
                           label=src, lx=x + TW / 2, ly=Y3 - 86))
    o.append(arrow(r0 + 200 + TW + 6, Y3 + TH / 2, r0 + 400 - 6, Y3 + TH / 2,
                   label="기록 선택", lx=r0 + 356, ly=Y3 + TH / 2 - 7))

    # ══════════════════════════════════ STEP 5 · 시즌 · 친구
    o.append(band(1040, Y3 - 64, 700, TH + 120, "STEP 5 · 시즌 · 친구",
                  "6개월 주기. 도감은 유지되고 순위 점수만 초기화된다"))
    sx = 1076
    o.append(thumb("19", sx, Y3, "친구 관리 · 초대\n연락처 매칭"))
    o.append(thumb("21", sx + 200, Y3, "시즌 종료 결과\n칭호 지급 · 점수 리셋", strong=True))
    o.append(arrow(sx + TW + 6, Y3 + TH / 2, sx + 200 - 6, Y3 + TH / 2,
                   dash="4 3", label="시즌 마감", lx=sx + 156, ly=Y3 + TH / 2 - 7))
    o.append(cmark("A", sx + 200 + TW / 2, Y3 - 96, "17 · 18 랭킹 마감", "above"))
    o.append(arrow(sx + 200 + TW / 2, Y3 - 80, sx + 200 + TW / 2, Y3 - 6, dash="4 3"))
    # 시즌 재시작
    o.append(arrow(sx + 200 + TW + 6, Y3 + TH / 2, sx + 200 + TW + 60, Y3 + TH / 2))
    o.append(t(sx + 200 + TW + 70, Y3 + TH / 2 - 4, "시즌 N+1 시작", 11, 700, INK))
    o.append(t(sx + 200 + TW + 70, Y3 + TH / 2 + 12, "→ 04 도감 홈으로 복귀", 10, 400, SUB))
    o.append(t(sx + 200 + TW + 70, Y3 + TH / 2 + 27, "도감은 그대로 · 순위 점수만 초기화",
               10, 400, HINT))

    # ══════════════════════════════════ 결정 필요
    o.append(rect(60, 1730, 2140, 96, r=14, fill="#fdf6f6", stroke="#f0dede"))
    o.append(t(84, 1760, "이 흐름도에서 아직 정의되지 않은 분기 (개발 착수 전 결정 필요)",
               13, 700, "#8a3a3a"))
    todo = [
        "① 09에서 3회 연속 판별 실패 시 어디로 보내는가",
        "② 200종에 없는 꽃을 찍었을 때 09가 무엇을 보여주는가",
        "③ 05에서 공개 범위를 사후에 바꿀 수 있는가",
        "④ 16의 친구 추가가 단방향인가 상호 수락인가",
        "⑤ 17 · 18 랭킹 동점 시 순위 기준",
        "⑥ 계정 지역과 촬영 지역이 다를 때 13의 기본 공개범위",
    ]
    for i, ln in enumerate(todo):
        o.append(t(84 + (i % 3) * 712, 1786 + (i // 3) * 22, ln, 11, 400, "#7a4a4a"))

    return svg("".join(o), W, H, "24 화면 흐름 지도")


SCREENS_FLOWMAP = [("24_화면흐름지도", s24)]
