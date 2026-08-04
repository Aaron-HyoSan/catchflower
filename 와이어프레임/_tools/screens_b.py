# -*- coding: utf-8 -*-
"""화면 14~23 : 지도 / 랭킹 / 친구 / 마이페이지 + 전체 플로우"""
from wf_lib import *

L = FX + 20
R = FX + FW - 20
CW = FW - 40
BODY_TOP = FY + STATUS_H + HEADER_H


def mapbg(x=FX, y=None, w=FW, h=None):
    """지도 배경 약식 (도로 + 녹지)."""
    y = y if y is not None else FY + STATUS_H
    h = h if h is not None else FH - STATUS_H - 60
    o = [rect(x, y, w, h, fill="#f2f2f0", stroke="none")]
    o.append(path(f"M {x} {y+120} L {x+w} {y+92}", stroke="#e0e0dc", sw=9))
    o.append(path(f"M {x} {y+330} L {x+w} {y+368}", stroke="#e0e0dc", sw=9))
    o.append(path(f"M {x+112} {y} L {x+150} {y+h}", stroke="#e0e0dc", sw=7))
    o.append(path(f"M {x+276} {y} L {x+250} {y+h}", stroke="#e0e0dc", sw=7))
    o.append(path(f"M {x+30} {y+180} q 70 -40 150 10 t 140 -20", stroke="#e6e6e2", sw=5))
    o.append(rect(x + 36, y + 200, 96, 74, r=10, fill="#e8ece6", stroke="none"))
    o.append(rect(x + 200, y + 400, 120, 92, r=10, fill="#e8ece6", stroke="none"))
    o.append(t(x + 84, y + 242, "공원", 10, 400, "#a8b0a4", "middle"))
    o.append(t(x + w - 14, y + h - 10, "카카오맵", 8.5, 400, "#b0b0b0", "end"))
    return "".join(o)


def placepin(cx, cy, count, label, active=False, w=None):
    """장소 핀 = 꽃 개수 배지 + 장소명."""
    lb = f"{label} {count}"
    w = w or (len(label) * 11 + 46)
    col = DARK if active else "#ffffff"
    txt = "#ffffff" if active else INK
    o = [rect(cx - w / 2, cy - 32, w, 30, r=15, fill=col, stroke=DARK, sw=1.5)]
    o.append(path(f"M {cx-6} {cy-3} L {cx} {cy+5} L {cx+6} {cy-3} Z", fill=col, stroke=DARK, sw=1.5))
    o.append(circ(cx - w / 2 + 17, cy - 17, 10, fill=FILL if not active else "#5a5a5a", stroke="none"))
    o.append(t(cx - w / 2 + 17, cy - 13, "꽃", 8.5, 400, txt if active else SUB, "middle"))
    o.append(t(cx - w / 2 + 32, cy - 12, lb, 12, 700, txt))
    return "".join(o)


# ══════════════════════════════════════════════ 14 지도 홈
def s14():
    o = [frame_shell(), statusbar()]
    o.append(mapbg(y=FY + STATUS_H, h=FH - STATUS_H - 88))
    # 상단 검색 + 필터
    sy = FY + STATUS_H + 12
    o.append(rect(L, sy, CW, 46, r=23, fill="#ffffff", stroke=LINE))
    o.append(circ(L + 24, sy + 23, 7, stroke=SUB, sw=1.6))
    o.append(line(L + 29, sy + 28, L + 34, sy + 33, stroke=SUB, sw=1.6))
    o.append(t(L + 44, sy + 28, "공원, 산책로 이름으로 찾기", 13, 400, HINT))
    cx = L
    for lb, on in [("전체", True), ("친구", False), ("이번 주", False), ("못 모은 꽃", False)]:
        s, w = chip(cx, sy + 58, lb, on, 12, 12, 30)
        o.append(s); cx += w + 7

    # 핀
    o.append(placepin(FX + 118, FY + 300, "5", "서울숲", True))
    o.append(placepin(FX + 262, FY + 236, "2", "성수동길"))
    o.append(placepin(FX + 96, FY + 470, "8", "경의선숲길"))
    o.append(placepin(FX + 268, FY + 560, "3", "응봉산"))
    # 내 위치
    o.append(circ(FX + 190, FY + 400, 22, fill="#dcdcdc", stroke="none", dash="3 3"))
    o.append(circ(FX + 190, FY + 400, 7, fill=DARK, stroke="#ffffff", sw=2.5))

    # 우측 플로팅
    o.append(circ(R - 4, FY + 520, 22, fill="#ffffff", stroke=LINE))
    o.append(t(R - 4, FY + 524, "내위치", 8, 400, SUB, "middle"))
    o.append(circ(R - 4, FY + 574, 22, fill="#ffffff", stroke=LINE))
    o.append(t(R - 4, FY + 578, "목록", 8.5, 400, SUB, "middle"))

    # 하단 프리뷰 카드
    py = FY + FH - 220
    o.append(rect(L, py, CW, 128, r=14, fill="#ffffff", stroke=LINE))
    o.append(imgph(L + 12, py + 12, 78, 78, "사진", 10))
    o.append(t(L + 102, py + 32, "서울숲", 15.5, 700, INK))
    o.append(t(L + 102, py + 52, "꽃 5종 · 기록 12개", 11.5, 400, SUB))
    o.append(t(L + 102, py + 70, "장미, 개망초, 금계국 외 2종", 11, 400, HINT))
    o.append(btn(L + 102, py + 82, 112, 32, "길찾기", "secondary", 12))
    o.append(t(L + CW - 16, py + 100, "자세히 보기", 11.5, 700, SUB, "end"))
    o.append(t(L + CW - 18, py + 32, "1.2km", 11.5, 700, SUB, "end"))

    o.append(bottomnav("지도"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, sy + 23), (2, R + 4, sy + 73),
                      (3, FX + 60, FY + 292), (4, FX + 148, FY + 400),
                      (5, FX - 4, py + 40)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "장소 검색",
         "핀을 찾아 돌아다니지 않고 목적지로 직행하는 경로. 카카오맵 장소 검색 API 사용."),
        (2, "지도 필터",
         "'못 모은 꽃'이 핵심 — 내 도감에 없는 꽃이 있는 장소만 표시. 탐험 동기와 "
         "수집 목표를 직결시키는 장치(기획서에 미정의 → 신규 제안)."),
        (3, "장소 단위 핀 + 꽃 개수",
         "기획서 7장: 꽃마다 핀을 찍지 않고 장소로 묶는다. 배지 숫자는 종수가 아니라 "
         "기록 수. 줌 아웃 시 핀끼리 다시 묶어 구·동 단위로 집계."),
        (4, "내 위치",
         "회색 원 = GPS 정확도 반경. 지도 진입 시 내 위치 중심, 반경 3km를 기본 노출."),
        (5, "하단 프리뷰 카드",
         "핀 탭 시 지도를 가리지 않는 높이(128px)로 등장. 거리·꽃 종수·길찾기까지 "
         "여기서 끝낼 수 있게 한다."),
    ], screen_id="SCREEN 14", title="지도 홈",
        flow=["하단 지도 탭 → 핀 탭 → 프리뷰 → 15 장소 상세"]))
    return svg("".join(o) + page_header("14 지도 홈", "지도"), title="14 지도 홈")


# ══════════════════════════════════════════════ 15 장소 상세
def s15():
    o = [frame_shell(), statusbar()]
    o.append(mapbg(y=FY + STATUS_H, h=210))
    o.append(placepin(FX + FW / 2, FY + 190, "5", "서울숲", True))
    o.append(circ(FX + 32, FY + STATUS_H + 24, 17, fill="#ffffff", stroke=LINE))
    o.append(path(f"M {FX+36} {FY+STATUS_H+16} L {FX+28} {FY+STATUS_H+24} "
                  f"L {FX+36} {FY+STATUS_H+32}", stroke=INK, sw=1.8))

    sy = FY + STATUS_H + 196
    o.append(rect(FX, sy, FW, FH - (sy - FY), r=22, fill="#ffffff", stroke="none"))
    o.append(rect(FX + FW / 2 - 22, sy + 10, 44, 4, r=2, fill="#dcdcdc", stroke="none"))
    y = sy + 32
    o.append(t(L, y + 14, "서울숲", 21, 700, INK))
    o.append(t(L, y + 38, "성동구 성수동1가 · 1.2km", 12, 400, SUB))
    o.append(btn(R - 96, y - 2, 96, 40, "길찾기", "primary", 13.5))

    y += 62
    o.append(rect(L, y, CW, 56, r=12, fill=FILL, stroke="none"))
    for i, (k, v) in enumerate([("꽃 종류", "5종"), ("기록", "12개"), ("이번 주", "3개")]):
        px = L + 18 + i * ((CW - 36) / 3)
        o.append(t(px, y + 24, k, 10.5, 400, SUB))
        o.append(t(px, y + 44, v, 14, 700, INK))

    y += 76
    o.append(t(L, y, "이곳에서 발견된 꽃", 14, 700, INK))
    o.append(t(R, y, "내 도감 기준", 11, 400, HINT, "end"))
    y += 14
    for i, (nm, mine) in enumerate([("장미", True), ("개망초", True), ("금계국", False),
                                    ("접시꽃", False), ("나팔꽃", False)]):
        cx = L + 34 + i * 68
        o.append(flowerph(cx, y + 34, 26, dash=True))
        o.append(t(cx, y + 74, nm, 10.5, 700 if mine else 400, INK if mine else SUB, "middle"))
        if not mine:
            o.append(circ(cx + 20, y + 16, 8, fill=DARK, stroke="#ffffff", sw=1.5))
            o.append(t(cx + 20, y + 19.5, "!", 9, 700, "#ffffff", "middle"))

    y += 96
    o.append(rect(L, y, CW, 40, r=10, fill=FILL, stroke="none"))
    o.append(t(L + 16, y + 25, "! 표시는 내 도감에 없는 꽃이에요", 11.5, 700, INK))

    y += 56
    o.append(t(L, y, "사람들의 기록", 14, 700, INK))
    o.append(t(R, y, "최신순 ▾", 11.5, 700, SUB, "end"))
    y += 14
    for nick, when, txt, likes, cmts in [
            ("꽃보다효산", "2시간 전", "숲길 끝 벤치 옆에 활짝 피었어요", "12", "3"),
            ("연남댁", "어제", "", "8", "1")]:
        o.append(rect(L, y, CW, 116, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(imgph(L + 12, y + 12, 92, 92, "사진", 8))
        o.append(circ(L + 122, y + 26, 11, fill=FILL, stroke=LINE))
        o.append(t(L + 140, y + 30, nick, 12.5, 700, INK))
        o.append(t(L + CW - 14, y + 30, when, 10.5, 400, HINT, "end"))
        o.append(t(L + 122, y + 54, "금계국", 12, 700, INK))
        if txt:
            o.append(t(L + 122, y + 74, txt, 11, 400, SUB))
        o.append(path(f"M {L+128} {y+96} a 5 5 0 0 1 8 -3 a 5 5 0 0 1 8 3 "
                      f"q 0 6 -8 11 q -8 -5 -8 -11 Z", stroke=SUB, sw=1.4))
        o.append(t(L + 150, y + 100, likes, 11, 400, SUB))
        o.append(rect(L + 176, y + 89, 14, 12, r=3, fill="none", stroke=SUB, sw=1.4))
        o.append(t(L + 196, y + 100, cmts, 11, 400, SUB))
        y += 124

    o.append(clip_cover())
    for n, mx, my in [(1, R - 88, sy + 44), (2, R + 4, sy + 130),
                      (3, R + 4, sy + 226), (4, R + 4, sy + 300), (5, R + 4, sy + 400)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "길찾기 = 최상단 우측 고정",
         "기획서 7장 카카오맵 길찾기. 앱 내 표시 → 실제 안내는 카카오맵 앱으로 넘긴다."),
        (2, "장소 통계 3칸",
         "'이번 주' 수치가 있으면 지금 가면 볼 수 있다는 신선도 판단이 가능."),
        (3, "발견된 꽃 · 내 도감 대조",
         "장소별 꽃 목록(기획서 7장)에 '내가 안 가진 꽃' 표시를 겹쳐 '여기 가면 새 꽃 "
         "2종'이라는 목표가 즉시 성립. 지도 → 촬영 전환율을 끌어올리는 지점."),
        (4, "! 배지 범례",
         "아이콘만으로는 중장년 타깃에 전달되지 않으므로 문장 범례를 항상 노출."),
        (5, "기록 카드 (좋아요·댓글)",
         "기획서 8장: 별도 SNS 피드 없이 장소 안에서만 반응. 카드 탭 → 16 상세. "
         "한 줄 설명이 없으면 그 줄을 비우고 사진을 크게."),
    ], screen_id="SCREEN 15", title="장소 상세 (핀 선택)",
        flow=["14 핀/프리뷰 → 장소 상세 → 16 기록 상세 · 길찾기"]))
    return svg("".join(o) + page_header("15 장소 상세", "지도"), title="15 장소 상세")


# ══════════════════════════════════════════════ 16 기록 상세 (좋아요·댓글)
def s16():
    o = [frame_shell(), statusbar(), header("꽃 기록", back=True, right="신고")]
    y = BODY_TOP
    o.append(imgph(FX, y, FW, 300, "사용자 촬영 사진", 0))
    y += 316
    o.append(circ(L + 16, y + 4, 16, fill=FILL, stroke=LINE))
    o.append(t(L + 40, y + 2, "꽃보다효산", 13.5, 700, INK))
    o.append(t(L + 40, y + 20, "연남동 · 2시간 전", 11, 400, HINT))
    o.append(btn(R - 78, y - 12, 78, 32, "친구 추가", "secondary", 11.5))

    y += 46
    o.append(rect(L, y, CW, 56, r=12, fill=FILL, stroke="none"))
    o.append(flowerph(L + 40, y + 28, 20, dash=True))
    o.append(t(L + 74, y + 26, "금계국", 14, 700, INK))
    o.append(t(L + 74, y + 44, "국화과 · 6~8월", 11, 400, SUB))
    o.append(t(L + CW - 16, y + 34, "도감에서 보기", 11.5, 700, SUB, "end"))

    y += 74
    o.append(t(L, y, "숲길 끝 벤치 옆에 활짝 피었어요", 13.5, 400, INK))
    y += 26
    o.append(t(L, y, "서울숲 · 성동구 성수동1가", 11.5, 700, SUB))

    y += 24
    o.append(line(L, y, R, y, stroke="#ededed"))
    y += 30
    o.append(path(f"M {L+8} {y-4} a 7 7 0 0 1 11 -4 a 7 7 0 0 1 11 4 "
                  f"q 0 8 -11 15 q -11 -7 -11 -15 Z", stroke=DARK, sw=1.6))
    o.append(t(L + 40, y + 5, "좋아요 12", 13, 700, INK))
    o.append(rect(L + 130, y - 10, 18, 15, r=4, fill="none", stroke=SUB, sw=1.6))
    o.append(t(L + 156, y + 5, "댓글 3", 13, 400, SUB))

    y += 30
    for nick, txt, when in [("연남댁", "여기 아직 피어 있나요?", "1시간 전"),
                            ("꽃보다효산", "네 어제도 그대로였어요", "40분 전"),
                            ("성수산책", "주말에 가봐야겠네요", "20분 전")]:
        o.append(circ(L + 13, y + 10, 13, fill=FILL, stroke=LINE))
        o.append(t(L + 34, y + 8, nick, 12, 700, INK))
        o.append(t(L + 34, y + 26, txt, 12, 400, SUB))
        o.append(t(R, y + 8, when, 10, 400, HINT, "end"))
        y += 48

    o.append(rect(FX, FY + FH - 88, FW, 88, fill="#ffffff", stroke="none"))
    o.append(line(FX, FY + FH - 88, FX + FW, FY + FH - 88, stroke="#ededed"))
    o.append(rect(L, FY + FH - 74, CW - 62, 44, r=22, fill=FILL, stroke=LINE))
    o.append(t(L + 18, FY + FH - 46, "댓글을 남겨보세요", 12.5, 400, HINT))
    o.append(btn(R - 54, FY + FH - 74, 54, 44, "등록", "primary", 12.5))

    o.append(clip_cover())
    for n, mx, my in [(1, R - 70, BODY_TOP + 304), (2, R + 4, BODY_TOP + 390),
                      (3, R + 4, BODY_TOP + 470), (4, R + 4, FY + FH - 52)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "친구 추가 진입점",
         "연락처 기반 연결(기획서 10장) 외에 지도에서 만난 사람도 친구가 될 수 있어야 "
         "지역 커뮤니티가 형성된다. 상호 수락 방식."),
        (2, "꽃 정보 → 도감 링크",
         "남의 기록에서 본 꽃을 내 도감(미발견 상태)으로 연결. 수집 목표 전환 동선."),
        (3, "좋아요 · 댓글",
         "기획서 8장. 아이콘 단독 대신 '좋아요 12' 텍스트 병기 — 타깃 인지율 우선."),
        (4, "댓글 입력 · 신고",
         "MVP 운영 최소 요건: 신고(헤더 우측) + 차단. 댓글 신고 정책은 별도 문서 필요."),
    ], screen_id="SCREEN 16", title="꽃 기록 상세",
        flow=["15 기록 카드 탭 → 상세 → 댓글·좋아요"]))
    return svg("".join(o) + page_header("16 꽃 기록 상세", "지도"), title="16 기록 상세")


# ══════════════════════════════════════════════ 17 랭킹 (지역)
def s17():
    o = [frame_shell(), statusbar()]
    hy = FY + STATUS_H
    o.append(rect(FX, hy, FW, HEADER_H, fill="#ffffff", stroke="none"))
    o.append(t(L, hy + 33, "랭킹", 20, 700, INK))
    o.append(t(R, hy + 31, "지난 시즌", 11.5, 700, SUB, "end"))
    # 탭
    ty = hy + HEADER_H
    o.append(line(FX, ty + 44, FX + FW, ty + 44, stroke="#ededed"))
    for i, (lb, on) in enumerate([("우리 동네", True), ("친구", False)]):
        cx = FX + FW / 4 + i * FW / 2
        o.append(t(cx, ty + 28, lb, 15, 700 if on else 400, INK if on else HINT, "middle"))
        if on:
            o.append(rect(cx - 52, ty + 42, 104, 3, r=1.5, fill=DARK, stroke="none"))

    # 시즌 배너
    y = ty + 60
    o.append(rect(L, y, CW, 86, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 18, y + 26, "2026 시즌 2", 12, 700, SUB))
    o.append(t(L + 18, y + 52, "연남동 꽃 수집 순위", 16, 700, INK))
    o.append(t(L + 18, y + 72, "9월 30일 마감 · 47일 남음", 11.5, 400, SUB))
    o.append(rect(L + CW - 82, y + 18, 64, 50, r=10, fill="#ffffff", stroke=LINE))
    o.append(t(L + CW - 50, y + 38, "47", 19, 700, INK, "middle"))
    o.append(t(L + CW - 50, y + 55, "일 남음", 9, 400, HINT, "middle"))

    # 내 순위 카드
    y += 102
    o.append(rect(L, y, CW, 84, r=14, fill="#ffffff", stroke=DARK, sw=1.5))
    o.append(t(L + 18, y + 26, "내 순위", 11, 700, SUB))
    o.append(t(L + 18, y + 58, "21위", 24, 700, INK))
    o.append(t(L + 76, y + 58, "▲ 3", 12, 700, SUB))
    o.append(t(L + CW - 18, y + 26, "이번 시즌 모은 꽃", 11, 400, SUB, "end"))
    o.append(t(L + CW - 18, y + 56, "13종", 20, 700, INK, "end"))
    o.append(t(L + 18, y + 76, "3종만 더 모으면 15위권!", 11, 700, INK))

    # 랭킹 리스트
    y += 100
    o.append(t(L, y, "연남동 이웃 1,284명", 13.5, 700, INK))
    y += 12
    rows = [(1, "연남동꽃선생", "41종", True), (2, "산책하는날", "38종", False),
            (3, "봄이오면", "35종", False), (4, "효산맘", "31종", False),
            (5, "성미산둘레", "29종", False)]
    for rk, nick, cnt, crown in rows:
        o.append(rect(L, y, CW, 62, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(t(L + 26, y + 38, str(rk), 16, 700, INK, "middle"))
        o.append(circ(L + 62, y + 31, 17, fill=FILL, stroke=LINE))
        o.append(flowerph(L + 62, y + 31, 12, dash=True))
        o.append(t(L + 90, y + 28, nick, 13.5, 700, INK))
        o.append(t(L + 90, y + 46, "대표 꽃 · 장미", 10.5, 400, HINT))
        o.append(t(L + CW - 16, y + 38, cnt, 14.5, 700, INK, "end"))
        if crown:
            o.append(t(L + 26, y + 16, "王", 9, 700, SUB, "middle"))
        y += 70
    o.append(rect(L, y, CW, 44, r=22, fill=FILL, stroke="none"))
    o.append(t(FX + FW / 2, y + 28, "6위부터 더 보기", 12.5, 700, SUB, "middle"))

    o.append(bottomnav("랭킹"))
    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 62, ty + 28), (2, R + 4, ty + 100),
                      (3, R + 4, ty + 200), (4, R + 4, ty + 300), (5, R + 4, ty + 470)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "탭 2개 = 우리 동네 / 친구",
         "기획서 11장 랭킹 종류 2종. 기본 진입은 '우리 동네'."),
        (2, "시즌 정보 + 남은 기간",
         "기획서 12장 랭킹 화면 필수 항목. 연 2회 · 약 6개월 시즌이므로 '남은 일수'를 "
         "크게 보여줘야 마감 압박이 생긴다."),
        (3, "내 순위 고정 카드",
         "리스트를 스크롤해 자기를 찾게 하지 않는다. 순위 변동(▲3)과 다음 목표까지 "
         "필요한 종수를 함께 제시 — 행동으로 이어지는 문장."),
        (4, "랭킹 행 구성",
         "순위 / 대표 꽃 썸네일 / 닉네임 / 종수. 기획서 10장 '친구의 대표 꽃 또는 도감 "
         "정보'를 지역 랭킹에도 동일 적용. 행 탭 → 상대 도감 요약(공개 범위 내)."),
        (5, "동점 처리 규칙 (미정 → 확정 필요)",
         "같은 종수일 때 순위 기준: ① 해당 종수 도달 시각이 빠른 사람 ② 총 발견 횟수. "
         "기획서에 없어 개발 전 결정 필요."),
    ], screen_id="SCREEN 17", title="랭킹 · 우리 동네",
        flow=["하단 랭킹 탭 → 우리 동네 / 친구 전환"]))
    return svg("".join(o) + page_header("17 랭킹 · 우리 동네", "랭킹"), title="17 지역 랭킹")


# ══════════════════════════════════════════════ 18 랭킹 (친구)
def s18():
    o = [frame_shell(), statusbar()]
    hy = FY + STATUS_H
    o.append(rect(FX, hy, FW, HEADER_H, fill="#ffffff", stroke="none"))
    o.append(t(L, hy + 33, "랭킹", 20, 700, INK))
    o.append(t(R, hy + 31, "친구 관리", 11.5, 700, SUB, "end"))
    ty = hy + HEADER_H
    o.append(line(FX, ty + 44, FX + FW, ty + 44, stroke="#ededed"))
    for i, (lb, on) in enumerate([("우리 동네", False), ("친구", True)]):
        cx = FX + FW / 4 + i * FW / 2
        o.append(t(cx, ty + 28, lb, 15, 700 if on else 400, INK if on else HINT, "middle"))
        if on:
            o.append(rect(cx - 52, ty + 42, 104, 3, r=1.5, fill=DARK, stroke="none"))

    # 시상대
    y = ty + 70
    o.append(t(L, y, "친구 8명과 겨루는 중", 13.5, 700, INK))
    py = y + 130
    for rk, nick, cnt, h, dx in [(2, "효산맘", "31종", 62, -104), (1, "연남댁", "38종", 90, 0),
                                 (3, "성수산책", "29종", 46, 104)]:
        cx = FX + FW / 2 + dx
        o.append(flowerph(cx, py - h - 34, 24, dash=True))
        o.append(rect(cx - 42, py - h, 84, h, r=8, fill=FILL, stroke="none"))
        o.append(t(cx, py - h + 26, str(rk), 20, 700, INK, "middle"))
        o.append(t(cx, py + 20, nick, 12, 700, INK, "middle"))
        o.append(t(cx, py + 37, cnt, 11, 400, SUB, "middle"))

    # 내 순위
    y = py + 62
    o.append(rect(L, y, CW, 76, r=14, fill="#ffffff", stroke=DARK, sw=1.5))
    o.append(t(L + 26, y + 44, "4", 20, 700, INK, "middle"))
    o.append(circ(L + 64, y + 38, 18, fill=FILL, stroke=LINE))
    o.append(t(L + 92, y + 32, "나 (꽃보다효산)", 13.5, 700, INK))
    o.append(t(L + 92, y + 52, "연남댁까지 25종 남음", 10.5, 400, SUB))
    o.append(t(L + CW - 16, y + 44, "13종", 15, 700, INK, "end"))

    y += 92
    rows = [(5, "봄이오면", "11종"), (6, "산책하는날", "9종"),
            (7, "우동꽃길", "6종"), (8, "제주댁", "4종")]
    for rk, nick, cnt in rows:
        o.append(rect(L, y, CW, 58, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(t(L + 26, y + 36, str(rk), 15, 700, SUB, "middle"))
        o.append(circ(L + 60, y + 29, 16, fill=FILL, stroke=LINE))
        o.append(t(L + 86, y + 34, nick, 13, 400, INK))
        o.append(t(L + CW - 16, y + 34, cnt, 13.5, 700, INK, "end"))
        y += 66

    y += 4
    o.append(rect(L, y, CW, 74, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 16, y + 28, "친구가 많을수록 재미있어요", 12.5, 700, INK))
    o.append(t(L + 16, y + 48, "연락처에 저장된 지인을 초대해 보세요", 11, 400, SUB))
    o.append(btn(L + CW - 92, y + 20, 80, 34, "초대", "secondary", 12))

    o.append(bottomnav("랭킹"))
    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, py - 60), (2, R + 4, py + 90),
                      (3, R + 4, py + 200), (4, R + 4, FY + FH - 130)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "친구는 시상대 형태",
         "지역 랭킹(수백~수천 명)과 달리 친구는 소수. 리스트보다 1~3위 시상대가 "
         "'우리끼리'의 감각을 만든다."),
        (2, "격차를 종수로 환산",
         "'25종 남음'처럼 따라잡을 거리를 숫자로. 순위만 보여주면 행동으로 이어지지 않음."),
        (3, "4위 이하 리스트",
         "내 순위는 항상 강조 테두리로 리스트 상단 고정."),
        (4, "친구 0~2명일 때 (분기)",
         "랭킹 대신 초대 유도 화면 전체를 표시: '아직 겨룰 친구가 없어요'. 친구 수가 "
         "적으면 랭킹 자체가 무의미하므로 초대 CTA를 화면 주인공으로."),
    ], screen_id="SCREEN 18", title="랭킹 · 친구",
        flow=["17 친구 탭 → 친구 랭킹 → 19 친구 관리"]))
    return svg("".join(o) + page_header("18 랭킹 · 친구", "랭킹"), title="18 친구 랭킹")


# ══════════════════════════════════════════════ 19 친구 관리 · 초대
def s19():
    o = [frame_shell(), statusbar(), header("친구", back=True, right="검색")]
    ty = BODY_TOP
    o.append(line(FX, ty + 44, FX + FW, ty + 44, stroke="#ededed"))
    for i, (lb, on) in enumerate([("내 친구 8", True), ("초대하기", False)]):
        cx = FX + FW / 4 + i * FW / 2
        o.append(t(cx, ty + 28, lb, 14, 700 if on else 400, INK if on else HINT, "middle"))
        if on:
            o.append(rect(cx - 52, ty + 42, 104, 3, r=1.5, fill=DARK, stroke="none"))

    y = ty + 62
    o.append(rect(L, y, CW, 62, r=12, fill=FILL, stroke="none"))
    o.append(t(L + 16, y + 26, "연락처에 캐치플라워 이웃 4명이 있어요", 12, 700, INK))
    o.append(t(L + 16, y + 46, "친구로 추가하면 순위를 함께 볼 수 있어요", 10.5, 400, SUB))

    y += 80
    o.append(t(L, y, "연락처에서 찾은 친구", 13, 700, INK))
    y += 12
    for nick, phone in [("김영희", "010-2••••-1234"), ("박순자", "010-9••••-5678"),
                        ("이정미", "010-3••••-9012")]:
        o.append(rect(L, y, CW, 62, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(circ(L + 34, y + 31, 18, fill=FILL, stroke=LINE))
        o.append(t(L + 62, y + 28, nick, 13.5, 700, INK))
        o.append(t(L + 62, y + 46, phone, 10.5, 400, HINT))
        o.append(btn(L + CW - 84, y + 15, 72, 32, "추가", "primary", 12))
        y += 70

    y += 10
    o.append(t(L, y, "아직 가입하지 않은 지인", 13, 700, INK))
    y += 12
    for nick in ["최말순", "정해경", "윤보라"]:
        o.append(rect(L, y, CW, 58, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(circ(L + 32, y + 29, 16, fill=FILL, stroke=LINE, dash="3 3"))
        o.append(t(L + 58, y + 34, nick, 13, 400, SUB))
        o.append(btn(L + CW - 88, y + 13, 76, 32, "초대하기", "secondary", 11.5))
        y += 66

    o.append(rect(FX, FY + FH - 96, FW, 96, fill="#ffffff", stroke="none"))
    o.append(line(FX, FY + FH - 96, FX + FW, FY + FH - 96, stroke="#ededed"))
    o.append(btn(L, FY + FH - 82, CW, 52, "초대 링크 보내기", "primary", 15))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, ty + 92), (2, R + 4, ty + 170),
                      (3, R + 4, ty + 400), (4, R + 4, FY + FH - 56)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "연락처 매칭 결과 요약",
         "기획서 10장. 전화번호는 해시로 서버 대조하고 원문 저장 안 함(화면 03 고지와 일치)."),
        (2, "가입자 = 즉시 추가",
         "번호 마스킹 표기 필수. 상호 수락 없이 단방향 추가 시 랭킹 노출 범위를 "
         "'서로 친구'로 제한할지 결정 필요."),
        (3, "미가입자 = 초대",
         "기획서 10장 전화번호 초대. SMS/카카오톡 공유 시트 호출. 초대 성공 시 "
         "양쪽에 보상(배지)을 주는 안 검토."),
        (4, "연락처 권한 거부 시 (분기)",
         "이 화면 전체를 '초대 링크 보내기' 단독 화면으로 대체. 권한 재요청은 강요하지 않는다."),
    ], screen_id="SCREEN 19", title="친구 관리 · 초대",
        flow=["18 친구 관리 → 연락처 매칭 → 추가/초대"]))
    return svg("".join(o) + page_header("19 친구 관리 · 초대", "친구"), title="19 친구 관리")


# ══════════════════════════════════════════════ 20 마이페이지
def s20():
    o = [frame_shell(), statusbar()]
    hy = FY + STATUS_H
    o.append(rect(FX, hy, FW, HEADER_H, fill="#ffffff", stroke="none"))
    o.append(t(L, hy + 33, "마이", 20, 700, INK))
    o.append(t(R, hy + 31, "설정", 11.5, 700, SUB, "end"))

    y = hy + HEADER_H + 12
    o.append(circ(FX + FW / 2, y + 46, 40, fill=FILL, stroke=LINE))
    o.append(flowerph(FX + FW / 2, y + 46, 28, dash=True))
    o.append(t(FX + FW / 2, y + 112, "꽃보다효산", 18, 700, INK, "middle"))
    s, w = chip(FX + FW / 2 - 46, y + 124, "우리 동네 꽃박사", False, 11, 12, 26)
    o.append(s)
    o.append(t(FX + FW / 2, y + 172, "연남동 · 2026년 3월부터 함께", 11.5, 400, SUB, "middle"))
    o.append(btn(FX + FW / 2 - 56, y + 186, 112, 34, "프로필 수정", "secondary", 12))

    y += 240
    o.append(rect(L, y, CW, 72, r=14, fill=FILL, stroke="none"))
    for i, (k, v) in enumerate([("모은 꽃", "37종"), ("총 발견", "112회"), ("공유", "26개")]):
        px = L + 18 + i * ((CW - 36) / 3)
        o.append(t(px, y + 28, k, 10.5, 400, SUB))
        o.append(t(px, y + 52, v, 16, 700, INK))

    y += 92
    o.append(t(L, y, "내 배지", 14, 700, INK))
    o.append(t(R, y, "3개 · 전체 보기", 11.5, 700, SUB, "end"))
    y += 14
    for i, (nm, sub) in enumerate([("봄꽃 수집가", "2026 S1"), ("우리 동네 꽃박사", "2026 S1"),
                                   ("첫 발견", "2026.3")]):
        cx = L + 48 + i * 104
        o.append(circ(cx, y + 40, 30, fill="#ffffff", stroke=LINE, dash="4 3"))
        o.append(t(cx, y + 45, "배지", 10, 400, HINT, "middle"))
        o.append(t(cx, y + 88, nm, 10.5, 700, INK, "middle"))
        o.append(t(cx, y + 103, sub, 9.5, 400, HINT, "middle"))

    y += 128
    o.append(rect(L, y, CW, 66, r=12, fill="#ffffff", stroke="#ececec"))
    o.append(t(L + 16, y + 26, "활동 지역", 12, 700, INK))
    o.append(t(L + 16, y + 46, "서울특별시 마포구 연남동", 12.5, 400, SUB))
    o.append(t(L + CW - 16, y + 26, "변경 불가", 10.5, 700, HINT, "end"))
    o.append(t(L + CW - 16, y + 46, "2027. 2. 4. 부터 가능", 10, 400, HINT, "end"))

    y += 80
    for lb, right in [("친구 관리", "8명"), ("내가 공유한 꽃", "26개"),
                      ("지난 시즌 기록", ""), ("알림 설정", ""), ("고객문의", "")]:
        o.append(line(L, y + 46, R, y + 46, stroke="#f0f0f0"))
        o.append(t(L, y + 28, lb, 13.5, 400, INK))
        o.append(t(R - 16, y + 28, right, 11.5, 400, HINT, "end"))
        o.append(path(f"M {R-8} {y+21} L {R-2} {y+27} L {R-8} {y+33}", stroke=HINT, sw=1.4))
        y += 46

    o.append(bottomnav("마이"))
    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 76, hy + HEADER_H + 136),
                      (2, R + 4, hy + HEADER_H + 290), (3, R + 4, hy + HEADER_H + 400),
                      (4, R + 4, hy + HEADER_H + 500)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "칭호는 프로필 아이덴티티",
         "기획서 11장 배지·칭호는 시즌 후에도 유지. 획득한 칭호 중 하나를 대표로 선택해 "
         "닉네임 아래 상시 노출 — 랭킹 리스트에도 같이 나간다."),
        (2, "누적 3지표",
         "종수(경쟁 축) / 발견 횟수(활동량) / 공유 수(기여도). 시즌 초기화와 무관한 영구 수치."),
        (3, "배지 진열장",
         "200종 도감 + 시즌 2회 구조에서 배지가 장기 리텐션 축. MVP 배지 종류는 "
         "업무목록 B-9에서 확정."),
        (4, "지역 변경 가능일 명시",
         "기획서 9장 6개월 제한. '변경 불가'만 쓰면 문의가 몰리므로 가능 날짜를 항상 병기. "
         "가능해지면 이 행이 '변경하기' 버튼으로 바뀐다."),
    ], screen_id="SCREEN 20", title="마이페이지",
        flow=["하단 마이 탭 → 프로필 · 배지 · 설정"]))
    return svg("".join(o) + page_header("20 마이페이지", "마이페이지"), title="20 마이페이지")


# ══════════════════════════════════════════════ 21 시즌 종료 결과
def s21():
    o = [frame_shell(), statusbar()]
    o.append(rect(FX, FY, FW, FH, r=30, fill=FILL, stroke="none"))
    y = FY + 96
    o.append(t(FX + FW / 2, y, "2026 시즌 1이 끝났어요", 13, 700, SUB, "middle"))
    o.append(t(FX + FW / 2, y + 36, "연남동 4위", 30, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 64, "이웃 1,284명 중", 12, 400, SUB, "middle"))
    o.append(circ(FX + FW / 2, y + 148, 54, fill="#ffffff", stroke=LINE, dash="4 3"))
    o.append(t(FX + FW / 2, y + 152, "배지", 12, 400, HINT, "middle"))
    o.append(t(FX + FW / 2, y + 228, "봄꽃 수집가", 20, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 252, "칭호를 받았어요", 12, 400, SUB, "middle"))

    y += 292
    o.append(rect(L, y, CW, 150, r=14, fill="#ffffff", stroke="none"))
    o.append(t(L + 18, y + 30, "시즌 1 기록", 13, 700, INK))
    for i, (k, v) in enumerate([("모은 꽃", "24종"), ("발견 횟수", "68회"),
                                ("지도 공유", "19개"), ("최고 순위", "3위")]):
        o.append(t(L + 18, y + 58 + i * 24, k, 11.5, 400, SUB))
        o.append(t(L + CW - 18, y + 58 + i * 24, v, 12.5, 700, INK, "end"))

    y += 168
    o.append(rect(L, y, CW, 78, r=14, fill="#ffffff", stroke="none"))
    o.append(t(L + 18, y + 28, "도감은 그대로 남아요", 12.5, 700, INK))
    o.append(t(L + 18, y + 48, "모은 꽃 37종과 사진은 초기화되지 않아요.", 11, 400, SUB))
    o.append(t(L + 18, y + 65, "순위 점수만 새 시즌으로 리셋됩니다.", 11, 400, SUB))

    by = FY + FH - 160
    o.append(btn(L, by, CW, 56, "시즌 2 시작하기", "primary", 16))
    o.append(btn(L, by + 68, CW, 50, "결과 공유하기", "ghost", 14.5))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 108, FY + 132), (2, R + 4, FY + 340),
                      (3, R + 4, FY + 470), (4, R + 4, by + 94)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "시즌 결과는 전면 화면",
         "시즌 종료 후 첫 실행 시 1회 강제 노출. 기획서 11장 보상 지급 시점."),
        (2, "칭호 · 배지 수여",
         "'2026 시즌 1 연남동 꽃수집 1위' 같은 순위형과 '봄꽃 수집가' 같은 달성형 2계열. "
         "4위는 순위형 미지급 → 달성형만 표시."),
        (3, "초기화 범위 안내 (중요)",
         "기획서 6·11장의 핵심 규칙. 여기서 명시하지 않으면 '내 꽃이 사라졌다'는 오해가 "
         "시즌 전환 시점에 집중 발생한다."),
        (4, "결과 공유",
         "이미지 카드로 저장/외부 공유. 신규 유입 경로이자 초대 유인."),
    ], screen_id="SCREEN 21", title="시즌 종료 결과",
        flow=["시즌 종료 후 최초 실행 → 결과 → 새 시즌"]))
    return svg("".join(o) + page_header("21 시즌 종료 결과", "랭킹"), title="21 시즌 결과")


# ══════════════════════════════════════════════ 22 빈 상태 (도감 0종)
def s22():
    o = [frame_shell(), statusbar()]
    hy = FY + STATUS_H
    o.append(rect(FX, hy, FW, HEADER_H, fill="#ffffff", stroke="none"))
    o.append(t(L, hy + 33, "내 꽃 도감", 20, 700, INK))

    y = hy + HEADER_H + 12
    o.append(rect(L, y, CW, 100, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 18, y + 30, "모은 꽃", 11.5, 400, SUB))
    o.append(t(L + 18, y + 62, "0", 30, 700, HINT))
    o.append(t(L + 44, y + 62, "/ 200종", 13, 400, SUB))
    o.append(rect(L + 18, y + 78, CW - 36, 8, r=4, fill="#e2e2e2", stroke="none"))

    y += 130
    o.append(flowerph(FX + FW / 2, y + 60, 52, dash=True))
    o.append(t(FX + FW / 2, y + 152, "아직 모은 꽃이 없어요", 19, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 178, "산책길에 만난 꽃을 찍어 첫 칸을 채워보세요",
               12.5, 400, SUB, "middle"))
    o.append(btn(FX + FW / 2 - 88, y + 200, 176, 52, "꽃 찍어보기", "primary", 15))

    y += 282
    o.append(rect(L, y, CW, 138, r=14, fill="#ffffff", stroke="#ececec"))
    o.append(t(L + 18, y + 30, "처음이라면 이 꽃부터", 13, 700, INK))
    o.append(t(L + 18, y + 48, "지금 이 계절, 동네에서 흔히 보이는 꽃이에요", 10.5, 400, SUB))
    for i, (nm, mon) in enumerate([("개망초", "6~8월"), ("금계국", "6~8월"),
                                   ("나팔꽃", "7~9월"), ("접시꽃", "6~8월")]):
        cx = L + 44 + i * 74
        o.append(flowerph(cx, y + 86, 22, dash=True))
        o.append(t(cx, y + 118, nm, 10.5, 700, INK, "middle"))
        o.append(t(cx, y + 131, mon, 9, 400, HINT, "middle"))

    o.append(bottomnav("도감"))
    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, hy + HEADER_H + 62), (2, FX + FW / 2 + 128, hy + 320),
                      (3, R + 4, hy + HEADER_H + 470)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "0종에서도 분모 노출",
         "'0 / 200종'을 보여줘 앞으로의 규모를 인지시킨다. 진행 바는 빈 상태 유지."),
        (2, "빈 상태 CTA",
         "하단 촬영 버튼이 있어도 빈 화면에서는 큰 CTA를 한 번 더. 첫 촬영 전환이 "
         "온보딩 최종 관문."),
        (3, "제철 추천 꽃 4종",
         "'무엇을 찍어야 하나'를 해결하는 장치. 현재 월 + 계정 지역 기준으로 "
         "꽃 마스터 데이터에서 흔함(rarity=흔함) 종을 자동 추출. 200종 데이터의 "
         "season/rarity 컬럼이 이 기능의 전제."),
    ], screen_id="SCREEN 22", title="빈 상태 · 도감 0종",
        flow=["가입 직후 첫 진입 → 첫 촬영 유도"]))
    return svg("".join(o) + page_header("22 빈 상태 (도감 0종)", "도감"), title="22 빈 상태")


# ══════════════════════════════════════════════ 23 전체 화면 흐름도
def s23():
    W, H = 1500, 1000
    o = [t(60, 56, "캐치플라워 · 전체 화면 흐름도 v0.1", 22, 700, INK),
         t(60, 80, "실선 = 주 동선 / 점선 = 조건 분기 · 이탈 경로", 12, 400, SUB)]

    def box(x, y, w, h, num, title, sub=None, strong=False):
        r = [rect(x, y, w, h, r=10, fill="#ffffff" if not strong else FILL,
                  stroke=DARK if strong else LINE, sw=1.8 if strong else 1.2)]
        r.append(circ(x + 15, y + 15, 10, fill=DARK, stroke="none"))
        r.append(t(x + 15, y + 19, num, 9.5, 700, "#ffffff", "middle"))
        r.append(t(x + 32, y + 20, title, 12.5, 700, INK))
        if sub:
            r.append(t(x + 12, y + 40, sub, 10, 400, SUB))
        return "".join(r)

    def arr(x1, y1, x2, y2, dash=None, label=None, lx=None, ly=None):
        r = [line(x1, y1, x2, y2, stroke=SUB if not dash else HINT, sw=1.4, dash=dash)]
        import math
        a = math.atan2(y2 - y1, x2 - x1)
        for sg in (0.5, -0.5):
            r.append(line(x2, y2, x2 - 8 * math.cos(a - sg * 0.5),
                          y2 - 8 * math.sin(a - sg * 0.5), stroke=SUB if not dash else HINT, sw=1.4))
        if label:
            r.append(t(lx or (x1 + x2) / 2, ly or (y1 + y2) / 2 - 6, label, 9.5, 400, HINT, "middle"))
        return "".join(r)

    BW, BH = 168, 54
    # 온보딩 열
    o.append(t(64, 124, "온보딩", 11, 700, HINT, ls=1.5))
    o.append(box(60, 136, BW, BH, "01", "로그인", "카카오 · 애플 · 번호"))
    o.append(box(60, 216, BW, BH, "02", "활동 지역 선택", "동 단위 · 6개월 제한"))
    o.append(box(60, 296, BW, BH, "03", "권한 안내", "카메라 필수 · 위치·연락처 선택"))
    o.append(box(60, 376, BW, BH, "22", "빈 상태 도감", "0 / 200종 · 제철 추천"))
    for y in (190, 270, 350):
        o.append(arr(144, y, 144, y + 26))

    # 메인 탭 열
    o.append(t(300, 124, "메인 5탭", 11, 700, HINT, ls=1.5))
    o.append(box(296, 136, BW, BH, "04", "도감 홈", "첫 화면 · 현황 카드", True))
    o.append(box(296, 216, BW, BH, "14", "지도", "장소 핀 · 필터"))
    o.append(box(296, 296, BW, BH, "17", "랭킹 · 우리 동네", "시즌 · 내 순위"))
    o.append(box(296, 376, BW, BH, "20", "마이페이지", "프로필 · 배지 · 설정"))
    o.append(box(296, 456, BW, BH, "07", "꽃 촬영", "어느 탭에서든 진입", True))
    o.append(arr(228, 403, 292, 170, dash="4 3"))
    o.append(rect(286, 128, 188, 396, r=14, fill="none", stroke="#e4e4e4", dash="5 4"))
    o.append(t(380, 540, "하단 내비게이션", 10, 400, HINT, "middle"))

    # 도감 갈래
    o.append(t(546, 124, "도감", 11, 700, HINT, ls=1.5))
    o.append(box(542, 136, BW, BH, "05", "도감 상세", "일러스트 · 발견 기록"))
    o.append(box(542, 216, BW, BH, "06", "필터 시트", "계절 · 색 · 희귀도"))
    o.append(arr(464, 158, 538, 158))
    o.append(arr(464, 172, 538, 232, dash="4 3"))

    # 지도 갈래
    o.append(box(542, 300, BW, BH, "15", "장소 상세", "꽃 목록 · 기록 · 길찾기"))
    o.append(box(542, 380, BW, BH, "16", "기록 상세", "좋아요 · 댓글 · 친구추가"))
    o.append(arr(464, 240, 538, 318))
    o.append(arr(626, 354, 626, 376))

    # 촬영 플로우
    o.append(t(546, 456, "촬영 · AI 인증", 11, 700, HINT, ls=1.5))
    o.append(box(542, 468, BW, BH, "08", "AI 분석 중", "약 5초 · 취소 가능"))
    o.append(arr(464, 490, 538, 490))
    o.append(box(788, 468, BW, BH, "09", "판별 결과 확인", "이 꽃은 OO인가요?", True))
    o.append(arr(710, 490, 784, 490))
    o.append(box(788, 578, BW, BH, "12", "판별 실패", "재촬영 팁 4개"))
    o.append(arr(710, 500, 784, 592, dash="4 3", label="인식 불가", lx=752, ly=560))
    o.append(arr(872, 578, 872, 528, dash="4 3", label="다시 찍기", lx=940, ly=556))
    o.append(arr(788, 502, 626, 522, dash="4 3", label="아니에요", lx=700, ly=534))

    o.append(box(1034, 408, BW, BH, "10", "신규 꽃 등록", "새로운 꽃 발견! · 시즌 +1"))
    o.append(box(1034, 496, BW, BH, "11", "기존 꽃 재발견", "N번째 발견 · 시즌 유지"))
    o.append(arr(956, 480, 1030, 440, label="첫 발견", lx=990, ly=444))
    o.append(arr(956, 500, 1030, 520, label="보유 중", lx=996, ly=528))

    o.append(box(1034, 620, BW, BH, "13", "지도 공유 설정", "장소 · 공개범위 · 한 줄", True))
    o.append(arr(1118, 462, 1118, 490, dash="4 3"))
    o.append(arr(1118, 550, 1118, 614))
    o.append(arr(1202, 647, 1290, 647))
    o.append(box(1290, 620, 168, BH, "14", "지도 반영", "장소 핀에 기록 추가"))
    o.append(arr(1034, 640, 700, 400, dash="4 3", label="공유하지 않기 → 도감 상세",
                 lx=880, ly=520))

    # 시즌
    o.append(t(1038, 124, "시즌", 11, 700, HINT, ls=1.5))
    o.append(box(1034, 136, BW, BH, "18", "랭킹 · 친구", "시상대 · 격차 종수"))
    o.append(box(1034, 216, BW, BH, "19", "친구 관리 · 초대", "연락처 매칭"))
    o.append(box(1034, 296, BW, BH, "21", "시즌 종료 결과", "칭호 지급 · 점수 리셋"))
    o.append(arr(464, 318, 1030, 163, dash="4 3"))
    o.append(arr(1118, 190, 1118, 212))
    o.append(arr(1118, 270, 1118, 292))

    # 루프 표시
    o.append(rect(60, 700, 1398, 130, r=14, fill=FILL, stroke="none"))
    o.append(t(84, 736, "핵심 루프", 14, 700, INK))
    loop = ["꽃 발견", "직접 촬영", "AI 인증", "도감 등록", "지도 공유", "수집 경쟁", "새로운 꽃"]
    for i, st in enumerate(loop):
        cx = 150 + i * 190
        o.append(rect(cx - 62, 768, 124, 40, r=20, fill="#ffffff", stroke=LINE))
        o.append(t(cx, 793, st, 12, 700, INK, "middle"))
        if i < len(loop) - 1:
            o.append(arr(cx + 64, 788, cx + 124, 788))
    o.append(t(84, 812, "", 10, 400, HINT))

    # 미결 사항
    o.append(t(60, 880, "개발 전 결정이 필요한 항목 (기획서 미정의)", 14, 700, INK))
    todo = [
        "① 랭킹 동점 시 순위 기준 (도달 시각 vs 총 발견 횟수)",
        "② 친구 추가 = 단방향 / 상호 수락 중 무엇인가",
        "③ 사후에 공개 범위를 바꿀 수 있는가 (도감 상세 05)",
        "④ AI 3회 연속 실패 시 처리 (도감 홈 복귀 · 문구 교체)",
        "⑤ 계정 지역과 촬영 지역이 다를 때 기본 공개범위 하향 여부",
        "⑥ 200종 외 꽃을 찍었을 때 처리 (미등록 종 안내 문구)",
    ]
    for i, ln in enumerate(todo):
        o.append(t(60 + (i % 2) * 720, 908 + (i // 2) * 24, ln, 11.5, 400, SUB))

    return svg("".join(o), W, H, "23 전체 흐름도")


SCREENS_B = [("14_지도홈", s14), ("15_장소상세", s15), ("16_기록상세", s16),
             ("17_랭킹_지역", s17), ("18_랭킹_친구", s18), ("19_친구관리", s19),
             ("20_마이페이지", s20), ("21_시즌결과", s21), ("22_빈상태", s22),
             ("23_전체흐름도", s23)]
