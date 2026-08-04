# -*- coding: utf-8 -*-
"""화면 01~13 : 온보딩 / 도감 / 촬영·AI인증 플로우"""
from wf_lib import *

L = FX + 20          # 콘텐츠 좌측
R = FX + FW - 20     # 콘텐츠 우측
CW = FW - 40         # 콘텐츠 폭
BODY_TOP = FY + STATUS_H + HEADER_H


# ══════════════════════════════════════════════ 01 스플래시 · 로그인
def s01():
    o = [frame_shell(), statusbar()]
    y = FY + 150
    o.append(flowerph(FX + FW / 2, y, 52, dash=True))
    o.append(t(FX + FW / 2, y + 96, "캐치플라워", 30, 700, INK, "middle", ls=-0.5))
    o.append(t(FX + FW / 2, y + 126, "산책길에 만난 꽃, 사진으로 모으는 나만의 꽃 도감",
               12.5, 400, SUB, "middle"))

    by = FY + 500
    o.append(btn(L, by, CW, 54, "카카오로 3초 만에 시작하기", "primary", 15.5))
    o.append(btn(L, by + 66, CW, 54, "애플로 시작하기", "secondary", 15.5))
    o.append(btn(L, by + 132, CW, 54, "휴대폰 번호로 시작하기", "secondary", 15.5))

    ty = FY + 720
    o.append(t(FX + FW / 2, ty, "시작하면 이용약관과 개인정보 처리방침에", 10.5, 400, HINT, "middle"))
    o.append(t(FX + FW / 2, ty + 15, "동의한 것으로 봅니다.", 10.5, 400, HINT, "middle"))
    o.append(t(FX + FW / 2, ty + 36, "이용약관   ·   개인정보 처리방침", 10.5, 700, SUB, "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 78, y - 26), (2, R + 4, by + 27),
                      (3, R + 4, by + 159), (4, FX + FW / 2 + 108, ty + 36)]:
        o.append(marker(n, mx, my))

    o.append(annots([
        (1, "로고 · 태그라인",
         "태그라인은 '게임'보다 '기록'을 앞세운다. 40~50대에게 게임이라는 단어는 진입 장벽. "
         "로고는 꽃 심볼+워드마크 조합, 세로형."),
        (2, "1순위 로그인 = 카카오",
         "타깃 연령대 가입 이탈이 가장 적은 경로. 버튼 높이 54px, 폰트 15.5pt 이상 고정."),
        (3, "휴대폰 번호 로그인 필수",
         "연락처 기반 친구 연결(기획서 10장)이 전화번호를 쓰므로, 소셜 로그인만 두면 "
         "번호 미보유 계정이 생겨 친구 매칭이 깨진다. 가입 후 번호 인증을 반드시 받는다."),
        (4, "약관 동의 방식",
         "체크박스를 두지 않고 '시작하면 동의' 방식. 단, 연락처·위치·카메라는 각 기능 "
         "최초 진입 시 별도 동의(화면 03)."),
    ], screen_id="SCREEN 01", title="스플래시 · 로그인",
        flow=["앱 최초 실행 → 로그인 → 02 지역 선택"]))
    return svg("".join(o) + page_header("01 스플래시 · 로그인", "온보딩"), title="01 로그인")


# ══════════════════════════════════════════════ 02 활동 지역 선택
def s02():
    o = [frame_shell(), statusbar(), header("활동 지역 선택", back=True, right="1/2")]
    y = BODY_TOP + 24
    o.append(t(L, y + 6, "어느 동네에서 활동하세요?", 21, 700, INK))
    o.append(t(L, y + 32, "같은 동네 이웃들과 꽃 수집 순위를 겨루게 됩니다.", 12.5, 400, SUB))
    o.append(t(L, y + 50, "가입 후에는 6개월에 한 번만 변경할 수 있어요.", 12.5, 700, INK))

    y += 84
    o.append(rect(L, y, CW, 48, r=10, fill=FILL, stroke=LINE))
    o.append(circ(L + 24, y + 24, 7, stroke=SUB, sw=1.6))
    o.append(line(L + 29, y + 29, L + 34, y + 34, stroke=SUB, sw=1.6))
    o.append(t(L + 44, y + 29, "동 이름으로 검색 (예: 연남동)", 13.5, 400, HINT))

    y += 64
    o.append(btn(L, y, CW, 46, "현재 위치로 우리 동네 찾기", "secondary", 14))

    y += 70
    o.append(t(L, y, "검색 결과", 12, 700, SUB, ls=1))
    y += 14
    rows = [("서울특별시 마포구 연남동", "이웃 1,284명 활동 중", True),
            ("서울특별시 마포구 연희동", "이웃 942명 활동 중", False),
            ("서울특별시 서대문구 연희동", "이웃 771명 활동 중", False),
            ("서울특별시 성동구 성수동1가", "이웃 1,530명 활동 중", False),
            ("부산광역시 해운대구 우동", "이웃 1,102명 활동 중", False)]
    for nm, cnt, on in rows:
        o.append(rect(L, y, CW, 58, r=10, fill="#ffffff",
                      stroke=DARK if on else "#ececec", sw=1.5 if on else 1))
        o.append(radio(L + 12, y + 20, "", on))
        o.append(t(L + 46, y + 26, nm, 13.5, 700 if on else 400, INK))
        o.append(t(L + 46, y + 44, cnt, 11, 400, HINT))
        y += 66

    o.append(rect(FX, FY + FH - 108, FW, 108, fill="#ffffff", stroke="none"))
    o.append(line(FX, FY + FH - 108, FX + FW, FY + FH - 108, stroke="#ededed"))
    o.append(btn(L, FY + FH - 90, CW, 54, "연남동으로 시작하기", "primary", 15.5))
    o.append(t(FX + FW / 2, FY + FH - 22, "선택한 동네는 2027년 2월 4일부터 변경할 수 있어요.",
               10.5, 400, HINT, "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, BODY_TOP + 134), (2, R + 4, BODY_TOP + 172),
                      (3, R + 4, BODY_TOP + 268), (4, R + 4, FY + FH - 63),
                      (5, FX + FW / 2 + 148, FY + FH - 22)]:
        o.append(marker(n, mx, my))

    o.append(annots([
        (1, "동 단위 검색",
         "행정동 기준. '연희동'처럼 동명이 중복되는 케이스가 실제로 많아 시·구를 항상 "
         "함께 표기해야 오등록을 막는다."),
        (2, "현재 위치로 찾기",
         "직접 타이핑이 어려운 사용자용 보조 경로. 위치 권한 1회성 요청."),
        (3, "이웃 활동 인원 표시",
         "빈 동네를 고르면 랭킹이 무의미해지므로 인원을 미리 보여준다. 인원 0~9명이면 "
         "'아직 이웃이 적어요' 안내 문구로 대체."),
        (4, "CTA에 선택 지역명 삽입",
         "선택 전에는 disabled 상태 + 문구 '동네를 선택해 주세요'."),
        (5, "변경 제한 사전 고지",
         "6개월 제한(기획서 9장)은 가입 시점에 날짜로 명시해야 CS 문의가 줄어든다. "
         "가입일 + 6개월을 실제 날짜로 계산해 표기."),
    ], screen_id="SCREEN 02", title="활동 지역 선택",
        flow=["로그인 완료 → 지역 선택 → 03 권한 안내"]))
    return svg("".join(o) + page_header("02 활동 지역 선택", "온보딩"), title="02 지역 선택")


# ══════════════════════════════════════════════ 03 권한 안내
def s03():
    o = [frame_shell(), statusbar(), header("권한 안내", back=True, right="2/2")]
    y = BODY_TOP + 24
    o.append(t(L, y + 6, "이 세 가지만 허용하면 준비 끝!", 21, 700, INK))
    o.append(t(L, y + 32, "허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요.",
               12.5, 400, SUB))

    y += 66
    perms = [
        ("카메라", "필수", "꽃을 직접 촬영해 도감에 등록합니다.",
         "앨범 사진은 등록할 수 없어요."),
        ("위치", "선택", "꽃을 발견한 장소를 지도에 남깁니다.",
         "끄면 지도 공유를 쓸 수 없어요."),
        ("연락처", "선택", "이미 가입한 지인을 친구로 연결합니다.",
         "번호는 암호화해 보관하며 저장하지 않아요."),
    ]
    for name, tag, desc, sub in perms:
        o.append(rect(L, y, CW, 104, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(rect(L + 16, y + 18, 42, 42, r=10, fill=FILL, stroke=LINE))
        o.append(t(L + 37, y + 44, "icon", 9, 400, HINT, "middle"))
        o.append(t(L + 70, y + 32, name, 15, 700, INK))
        tw, w2 = chip(L + 70 + len(name) * 15 + 8, y + 18, tag, tag == "필수", 10, 8, 20)
        o.append(tw)
        o.append(t(L + 70, y + 54, desc, 11.5, 400, SUB))
        o.append(t(L + 70, y + 72, sub, 11, 400, HINT))
        o.append(toggle(L + CW - 60, y + 20, on=True))
        y += 114

    o.append(rect(L, y, CW, 66, r=12, fill=FILL, stroke="none"))
    o.append(t(L + 16, y + 26, "촬영한 사진은 내 도감에만 저장됩니다.", 12, 700, INK))
    o.append(t(L + 16, y + 46, "지도 공유는 매번 직접 선택해요.", 11.5, 400, SUB))

    o.append(btn(L, FY + FH - 108, CW, 54, "허용하고 시작하기", "primary", 15.5))
    o.append(t(FX + FW / 2, FY + FH - 34, "나중에 설정에서 바꿀 수 있어요", 11, 700, SUB, "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, BODY_TOP + 100), (2, R + 4, BODY_TOP + 214),
                      (3, R + 4, BODY_TOP + 328), (4, R + 4, BODY_TOP + 424)]:
        o.append(marker(n, mx, my))

    o.append(annots([
        (1, "카메라 = 필수, 토글 고정 ON",
         "앱 카메라 직접 촬영이 게임 규칙의 근간(기획서 5장). 거부 시 촬영 진입마다 "
         "설정 유도 시트를 띄운다."),
        (2, "위치 = 선택",
         "거부해도 도감 등록은 가능하되 지도 공유 옵션이 사라진다. 이 경우 인증 완료 "
         "화면에서 공유 영역을 숨기고 '위치를 켜면 지도에 남길 수 있어요' 배너."),
        (3, "연락처 = 선택 + 보안 문구",
         "기획서 15장 보안 검토 항목. 원문 저장 없이 해시 매칭한다는 사실을 이 화면에서 "
         "명시해야 중장년 타깃 신뢰 확보에 유리."),
        (4, "프라이버시 안심 박스",
         "'자동 공개가 아니다'를 가입 단계에서 못 박는다. 실제 공유는 화면 13에서 매번 선택."),
    ], screen_id="SCREEN 03", title="권한 안내",
        flow=["지역 선택 → 권한 → 04 도감 홈"]))
    return svg("".join(o) + page_header("03 권한 안내", "온보딩"), title="03 권한 안내")


# ══════════════════════════════════════════════ 04 도감 홈 (첫 화면)
def s04():
    o = [frame_shell(), statusbar()]
    hy = FY + STATUS_H
    o.append(rect(FX, hy, FW, HEADER_H, fill="#ffffff", stroke="none"))
    o.append(t(L, hy + 33, "내 꽃 도감", 20, 700, INK))
    o.append(circ(R - 46, hy + 26, 13, stroke=SUB, sw=1.5))
    o.append(t(R - 46, hy + 30, "?", 12, 700, SUB, "middle"))
    o.append(circ(R - 12, hy + 26, 13, stroke=SUB, sw=1.5))
    o.append(t(R - 12, hy + 30, "설정", 7.5, 400, SUB, "middle"))

    # 요약 카드
    y = hy + HEADER_H + 12
    o.append(rect(L, y, CW, 118, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 18, y + 28, "모은 꽃", 11.5, 400, SUB))
    o.append(t(L + 18, y + 58, "37", 30, 700, INK))
    o.append(t(L + 18 + 40, y + 58, "/ 200종", 13, 400, SUB))
    o.append(t(L + CW - 18, y + 28, "이번 시즌", 11.5, 400, SUB, "end"))
    o.append(t(L + CW - 18, y + 56, "12종", 19, 700, INK, "end"))
    # 진행 바
    o.append(rect(L + 18, y + 76, CW - 36, 8, r=4, fill="#e2e2e2", stroke="none"))
    o.append(rect(L + 18, y + 76, (CW - 36) * 0.185, 8, r=4, fill=DARK, stroke="none"))
    o.append(t(L + 18, y + 102, "도감 18% 완성 · 다음 배지까지 3종", 11.5, 400, SUB))

    # 최근 발견
    y += 134
    o.append(t(L, y, "최근 발견한 꽃", 14.5, 700, INK))
    o.append(t(R, y, "전체 보기", 11.5, 700, SUB, "end"))
    y += 14
    for i, (nm, when) in enumerate([("개망초", "오늘"), ("금계국", "어제"), ("접시꽃", "3일 전")]):
        cx = L + 44 + i * 100
        o.append(flowerph(cx, y + 44, 34, dash=True))
        o.append(t(cx, y + 92, nm, 12, 700, INK, "middle"))
        o.append(t(cx, y + 107, when, 10, 400, HINT, "middle"))

    # 필터
    y += 130
    o.append(t(L, y, "전체 200종", 14.5, 700, INK))
    o.append(t(R, y, "필터", 11.5, 700, SUB, "end"))
    y += 14
    cx = L
    for lb, on in [("전체", True), ("모은 꽃", False), ("봄", False), ("여름", False), ("가을", False)]:
        s, w = chip(cx, y, lb, on)
        o.append(s); cx += w + 7

    # 그리드 3열
    y += 44
    cell = (CW - 2 * 10) / 3
    got = [True, True, False, True, False, False, True, False, False]
    names = ["장미", "개망초", "?", "금계국", "?", "?", "접시꽃", "?", "?"]
    for i in range(9):
        gx = L + (i % 3) * (cell + 10)
        gy = y + (i // 3) * (cell + 30)
        if got[i]:
            o.append(rect(gx, gy, cell, cell, r=12, fill="#ffffff", stroke=LINE))
            o.append(flowerph(gx + cell / 2, gy + cell / 2, cell * 0.31, dash=True))
            o.append(t(gx + cell / 2, gy + cell + 15, names[i], 11.5, 700, INK, "middle"))
        else:
            o.append(rect(gx, gy, cell, cell, r=12, fill="#f7f7f7", stroke="#eaeaea"))
            o.append(flowerph(gx + cell / 2, gy + cell / 2, cell * 0.31, dash=True))
            o.append(rect(gx, gy, cell, cell, r=12, fill="#ffffff", stroke="none", op=0.55))
            o.append(t(gx + cell / 2, gy + cell + 15, "미발견", 11.5, 400, HINT, "middle"))
    o.append(bottomnav("도감"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, hy + HEADER_H + 70), (2, R + 4, hy + HEADER_H + 200),
                      (3, R + 4, hy + HEADER_H + 320), (4, FX - 4, y + 60),
                      (5, FX + FW / 2, FY + FH - 96)]:
        o.append(marker(n, mx, my))

    o.append(annots([
        (1, "수집 현황 카드",
         "기획서 12장 첫 화면 필수 4요소를 한 카드에 압축: 누적 종수 / 전체 진행률 / "
         "이번 시즌 종수 / 다음 목표. 분모 200은 확정 도감 규모."),
        (2, "최근 발견 3종",
         "가로 스크롤. 사진이 아니라 '꽃 일러스트'를 대표 이미지로 쓴다 — 사용자 사진 "
         "품질이 들쭉날쭉해도 도감 화면 톤이 유지됨."),
        (3, "필터 칩",
         "MVP는 전체 / 모은 꽃 / 계절 4개만. 색상·희귀도 필터는 확장 단계."),
        (4, "미발견 = 실루엣",
         "회색 실루엣 + '미발견'. 이름을 감춰 수집 동기를 만든다. 단 탭하면 힌트 시트"
         "(계절·서식지)는 공개 — 완전 봉인은 중장년 타깃에 답답함으로 작동."),
        (5, "중앙 촬영 버튼",
         "어느 탭에서든 노출되는 지름 68px 원형. 기획서 12장 '크고 명확하게' 요구 반영."),
    ], screen_id="SCREEN 04", title="도감 홈 (앱 첫 화면)",
        flow=["앱 실행 시 기본 화면 → 셀 탭 → 05 도감 상세"]))
    return svg("".join(o) + page_header("04 도감 홈 (첫 화면)", "도감"), title="04 도감 홈")


# ══════════════════════════════════════════════ 05 도감 상세
def s05():
    o = [frame_shell(), statusbar(), header("장미", back=True, right="공유")]
    y = BODY_TOP + 16
    o.append(flowerph(FX + FW / 2, y + 82, 66, dash=True))
    o.append(t(FX + FW / 2, y + 176, "장미", 24, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 198, "Rosa hybrida · 장미과", 11.5, 400, HINT, "middle"))
    cx = FX + FW / 2 - 66
    for lb in ["여름", "흔함"]:
        s, w = chip(cx, y + 212, lb, False, 11, 10, 24)
        o.append(s); cx += w + 6
    s, w = chip(cx, y + 212, "붉은색", False, 11, 10, 24)
    o.append(s)

    y += 258
    o.append(rect(L, y, CW, 62, r=12, fill=FILL, stroke="none"))
    for i, (k, v) in enumerate([("발견 횟수", "4회"), ("첫 발견", "5월 2일"), ("장소", "3곳")]):
        px = L + 18 + i * ((CW - 36) / 3)
        o.append(t(px, y + 26, k, 10.5, 400, SUB))
        o.append(t(px, y + 48, v, 15, 700, INK))

    y += 80
    o.append(t(L, y, "꽃 이야기", 14, 700, INK))
    o.append(t(L, y + 22, "5~6월에 가장 화려하게 피어요. 겹겹이 포개진 꽃잎과", 12, 400, SUB))
    o.append(t(L, y + 40, "짙은 향이 특징이며, 공원 화단과 담장에서 흔히 만납니다.", 12, 400, SUB))
    o.append(t(L, y + 62, "비슷한 꽃 · 해당화, 찔레꽃", 11.5, 700, SUB))

    y += 92
    o.append(t(L, y, "내 발견 기록", 14, 700, INK))
    o.append(t(R, y, "4회", 11.5, 400, HINT, "end"))
    y += 14
    logs = [("2026. 6. 14. 토", "서울숲", "공개"),
            ("2026. 5. 30. 토", "연남동 경의선숲길", "비공개"),
            ("2026. 5. 2. 토", "올림픽공원", "공개")]
    for d, place, vis in logs:
        o.append(rect(L, y, CW, 74, r=12, fill="#ffffff", stroke="#ececec"))
        o.append(imgph(L + 12, y + 12, 50, 50, "사진", 8))
        o.append(t(L + 74, y + 30, d, 12.5, 700, INK))
        o.append(t(L + 74, y + 50, place, 11.5, 400, SUB))
        s, w = chip(L + CW - 68, y + 24, vis, False, 10, 8, 22)
        o.append(s)
        y += 82

    o.append(bottomnav("도감"))
    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 92, BODY_TOP + 76), (2, R + 4, BODY_TOP + 228),
                      (3, R + 4, BODY_TOP + 305), (4, R + 4, BODY_TOP + 400),
                      (5, R + 4, BODY_TOP + 505)]:
        o.append(marker(n, mx, my))

    o.append(annots([
        (1, "대표 이미지 = 공식 일러스트",
         "사용자 사진이 아닌 도감 일러스트를 히어로로. 200종 × 1컷이 디자이너 최대 "
         "물량 작업(업무목록 C 참조)."),
        (2, "속성 칩 3종",
         "계절 / 희귀도 / 대표 색상. 꽃 마스터 데이터의 컬럼과 1:1 대응하므로 데이터가 "
         "채워지면 자동 렌더."),
        (3, "누적 지표 3칸",
         "기획서 6장. 같은 꽃을 다시 찍어도 항목은 하나, 횟수·날짜·장소만 누적."),
        (4, "꽃 이야기 + 비슷한 꽃",
         "2~3문장 고정 분량. '비슷한 꽃'은 AI 오인식이 잦은 종을 서로 링크해 "
         "사용자가 스스로 판단할 여지를 준다."),
        (5, "발견 기록 리스트",
         "각 행에 공개/비공개 배지. 여기서 사후에 공개 범위를 바꿀 수 있어야 한다 "
         "(기획서에 미정의 → 추가 필요)."),
    ], screen_id="SCREEN 05", title="도감 상세",
        flow=["04 그리드 셀 탭 → 상세 → 기록 행 탭 → 사진 뷰어"]))
    return svg("".join(o) + page_header("05 도감 상세", "도감"), title="05 도감 상세")


# ══════════════════════════════════════════════ 06 도감 필터 시트
def s06():
    o = [frame_shell(), statusbar()]
    o.append(rect(FX, FY, FW, FH, r=30, fill="#ffffff", stroke="none"))
    o.append(rect(FX, FY, FW, FH, r=30, fill="#3a3a3a", stroke="none", op=0.35))
    sy = FY + 250
    o.append(rect(FX, sy, FW, FH - (sy - FY), r=22, fill="#ffffff", stroke=LINE))
    o.append(rect(FX + FW / 2 - 22, sy + 10, 44, 4, r=2, fill="#dcdcdc", stroke="none"))
    o.append(t(L, sy + 46, "필터", 19, 700, INK))
    o.append(t(R, sy + 44, "초기화", 12, 700, SUB, "end"))

    y = sy + 78
    groups = [("수집 여부", [("전체", True), ("모은 꽃", False), ("미발견", False)]),
              ("계절", [("봄", True), ("여름", False), ("가을", False), ("겨울", False)]),
              ("색상", [("흰색", False), ("노랑", True), ("분홍", False), ("붉은색", False),
                      ("보라", False), ("파랑", False)]),
              ("보기 쉬움", [("흔함", False), ("보통", False), ("귀함", False)])]
    for gname, items in groups:
        o.append(t(L, y, gname, 12.5, 700, INK))
        y += 12
        cx = L
        for lb, on in items:
            s, w = chip(cx, y, lb, on, 12, 13, 32)
            if cx + w > R:
                cx = L; y += 40
                s, w = chip(cx, y, lb, on, 12, 13, 32)
            o.append(s); cx += w + 8
        y += 58

    o.append(rect(FX, FY + FH - 96, FW, 96, fill="#ffffff", stroke="none"))
    o.append(btn(L, FY + FH - 82, CW, 54, "42종 보기", "primary", 15.5))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, sy + 44), (2, R + 4, sy + 96),
                      (3, R + 4, sy + 212), (4, R + 4, FY + FH - 55)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "바텀시트 · 초기화",
         "전체 화면 전환 대신 시트. 배경 도감이 보여 맥락이 끊기지 않는다."),
        (2, "수집 여부",
         "'미발견'만 보기가 실사용 빈도 최상위 — 남은 꽃을 확인하고 찾아나서는 동선."),
        (3, "색상 · 보기 쉬움",
         "꽃 마스터 데이터의 color / rarity 컬럼 기반. 기획서 14장에서 확장 기능으로 "
         "분류했으나, 200종 규모에서는 필터 없이 탐색이 불가해 MVP 편입 권고."),
        (4, "결과 개수 즉시 표기",
         "선택할 때마다 버튼 숫자가 실시간 갱신. 0종이면 disabled + '조건에 맞는 꽃이 "
         "없어요'."),
    ], screen_id="SCREEN 06", title="도감 필터 (바텀시트)",
        flow=["04 필터 탭 → 조건 선택 → 도감 그리드 갱신"]))
    return svg("".join(o) + page_header("06 도감 필터", "도감"), title="06 필터")


# ══════════════════════════════════════════════ 07 카메라 촬영
def s07():
    o = [frame_shell()]
    o.append(rect(FX, FY, FW, FH, r=30, fill="#2b2b2b", stroke="none"))
    o.append(t(FX + 26, FY + 27, "9:41", 13, 700, "#ffffff"))
    o.append(t(FX + FW / 2, FY + 74, "꽃 촬영", 16, 700, "#ffffff", "middle"))
    o.append(t(FX + 24, FY + 79, "닫기", 13, 400, "#ffffff"))
    o.append(t(FX + FW - 24, FY + 79, "도움말", 13, 400, "#ffffff", "end"))

    # 뷰파인더
    vy = FY + 110
    o.append(rect(FX, vy, FW, 520, fill="#3f3f3f", stroke="none"))
    o.append(t(FX + FW / 2, vy + 260, "카메라 프리뷰", 12, 400, "#8f8f8f", "middle"))
    # 가이드 프레임
    gx, gy, gw, gh = FX + 58, vy + 118, FW - 116, FW - 116
    for x1, y1, x2, y2, x3, y3 in [
        (gx, gy + 30, gx, gy, gx + 30, gy),
        (gx + gw - 30, gy, gx + gw, gy, gx + gw, gy + 30),
        (gx + gw, gy + gh - 30, gx + gw, gy + gh, gx + gw - 30, gy + gh),
        (gx + 30, gy + gh, gx, gy + gh, gx, gy + gh - 30)]:
        o.append(path(f"M {x1} {y1} L {x2} {y2} L {x3} {y3}", stroke="#ffffff", sw=2.5))
    o.append(t(FX + FW / 2, gy - 22, "꽃 한 송이를 네모 안에 꽉 채워 주세요", 13, 700,
               "#ffffff", "middle"))
    o.append(t(FX + FW / 2, gy + gh + 34, "너무 멀면 잘 못 알아봐요", 11.5, 400, "#c8c8c8", "middle"))

    # 하단 컨트롤
    cy = FY + 660
    o.append(rect(FX, cy, FW, FH - (cy - FY), fill="#1f1f1f", stroke="none"))
    o.append(rect(L, cy + 16, CW, 40, r=10, fill="#333333", stroke="none"))
    o.append(t(FX + FW / 2, cy + 41, "앨범 사진은 등록할 수 없어요. 직접 찍어 주세요.",
               11.5, 700, "#e0e0e0", "middle"))
    o.append(circ(FX + FW / 2, cy + 116, 40, fill="#ffffff", stroke="#7a7a7a", sw=2))
    o.append(circ(FX + FW / 2, cy + 116, 32, fill="#ffffff", stroke="#bdbdbd"))
    o.append(t(FX + FW / 2, cy + 176, "찍기", 12, 700, "#ffffff", "middle"))
    o.append(circ(FX + 70, cy + 116, 22, fill="#333333", stroke="none"))
    o.append(t(FX + 70, cy + 120, "플래시", 8.5, 400, "#e0e0e0", "middle"))
    o.append(circ(FX + FW - 70, cy + 116, 22, fill="#333333", stroke="none"))
    o.append(t(FX + FW - 70, cy + 120, "전환", 9, 400, "#e0e0e0", "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 128, gy - 22), (2, R + 4, gy + gh / 2),
                      (3, FX - 4, cy + 36), (4, FX + FW / 2 + 62, cy + 116)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "촬영 가이드 문구",
         "AI 인식률은 피사체 크기에 직결된다. '꽃 한 송이를 네모 안에 꽉 채워'처럼 "
         "동작을 지시하는 문장으로 쓴다."),
        (2, "정사각 가이드 프레임",
         "1:1 크롭 영역만 AI에 전송. 배경 노이즈를 줄여 오인식을 낮춘다."),
        (3, "앨범 차단 사전 고지",
         "기획서 5장 규칙. 앨범 버튼을 아예 두지 않고, 왜 없는지를 문구로 설명해야 "
         "'기능 고장'으로 오해하지 않는다."),
        (4, "셔터 지름 80px",
         "중장년 타깃 기준 최소 터치 영역. 좌우 보조 버튼은 44px."),
    ], screen_id="SCREEN 07", title="카메라 촬영",
        flow=["하단 중앙 촬영 버튼 → 카메라 → 08 분석 중"]))
    return svg("".join(o) + page_header("07 카메라 촬영", "촬영·AI인증"), title="07 카메라")


# ══════════════════════════════════════════════ 08 AI 분석 중
def s08():
    o = [frame_shell()]
    o.append(rect(FX, FY, FW, FH, r=30, fill="#2b2b2b", stroke="none"))
    o.append(t(FX + 26, FY + 27, "9:41", 13, 700, "#ffffff"))
    o.append(imgph(FX + 38, FY + 150, FW - 76, FW - 76, "촬영한 사진", 16))
    y = FY + 500
    o.append(circ(FX + FW / 2, y, 26, fill="none", stroke="#5a5a5a", sw=3))
    o.append(path(f"M {FX+FW/2} {y-26} a 26 26 0 0 1 26 26", stroke="#ffffff", sw=3))
    o.append(t(FX + FW / 2, y + 66, "어떤 꽃인지 보고 있어요", 18, 700, "#ffffff", "middle"))
    o.append(t(FX + FW / 2, y + 92, "5초 정도 걸려요", 12.5, 400, "#b0b0b0", "middle"))
    o.append(rect(FX + 90, y + 118, FW - 180, 6, r=3, fill="#454545", stroke="none"))
    o.append(rect(FX + 90, y + 118, (FW - 180) * 0.6, 6, r=3, fill="#ffffff", stroke="none"))
    o.append(t(FX + FW / 2, FY + FH - 60, "취소", 14, 700, "#c0c0c0", "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, FY + 150 + (FW - 76) / 2), (2, FX + FW / 2 + 118, y + 66),
                      (3, FX + FW / 2, FY + FH - 60)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "촬영 사진 즉시 노출",
         "업로드 대기 중에도 자기가 찍은 사진이 보이면 체감 대기 시간이 짧아진다."),
        (2, "대기 문구는 소요 시간 명시",
         "'분석 중...' 대신 '5초 정도 걸려요'. 무응답 오해로 인한 이탈 방지. "
         "10초 초과 시 '조금 더 걸리고 있어요'로 교체."),
        (3, "취소 가능",
         "네트워크 지연 시 탈출구. 취소 시 사진은 서버에 저장하지 않는다."),
    ], screen_id="SCREEN 08", title="AI 분석 중",
        flow=["07 촬영 → 분석 → 09 확인 / 12 실패"]))
    return svg("".join(o) + page_header("08 AI 분석 중", "촬영·AI인증"), title="08 분석 중")


# ══════════════════════════════════════════════ 09 AI 판별 결과 확인
def s09():
    o = [frame_shell(), statusbar()]
    o.append(imgph(FX, FY + STATUS_H, FW, 300, "촬영한 사진", 0))
    y = FY + STATUS_H + 300
    o.append(rect(FX, y - 20, FW, FH - (y - FY) + 20, r=22, fill="#ffffff", stroke="none"))
    y += 18
    o.append(t(FX + FW / 2, y + 8, "이 꽃은", 14, 400, SUB, "middle"))
    o.append(t(FX + FW / 2, y + 48, "장미", 32, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 76, "인가요?", 14, 400, SUB, "middle"))
    o.append(flowerph(FX + FW / 2, y + 140, 42, dash=True))
    o.append(t(FX + FW / 2, y + 202, "장미과 · 5~6월에 피는 꽃", 12, 400, SUB, "middle"))

    by = FY + FH - 224
    o.append(btn(L, by, CW, 56, "네, 맞아요", "primary", 16))
    o.append(btn(L, by + 68, CW, 56, "아니에요, 다시 찍을게요", "secondary", 16))
    o.append(t(FX + FW / 2, by + 152, "비슷한 꽃 · 해당화, 찔레꽃", 12, 700, SUB, "middle"))
    o.append(t(FX + FW / 2, by + 172, "다르면 다시 찍어 주세요", 11, 400, HINT, "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, R - 20, FY + STATUS_H + 30), (2, FX + FW / 2 + 68, y + 44),
                      (3, R + 4, by + 28), (4, R + 4, by + 96),
                      (5, FX + FW / 2 + 92, by + 152)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "촬영 사진 상단 고정",
         "판정 대상과 결과를 한 화면에서 대조할 수 있어야 사용자가 판단 가능."),
        (2, "꽃 이름을 최대 크기로",
         "기획서 5장 문안 '이 꽃은 장미인가요?'를 3줄로 쪼개 이름만 32pt. 노안 고려."),
        (3, "긍정 버튼 = 네, 맞아요",
         "기획서 원안 '맞아요'보다 응답형 '네, 맞아요'가 질문-대답 쌍으로 읽혀 오탭이 적다."),
        (4, "부정 버튼 = 재촬영 직행",
         "기획서 5장: 후보 목록은 제공하지 않는다. 탭 시 07 카메라로 즉시 복귀."),
        (5, "비슷한 꽃 힌트",
         "오인식 빈발 종을 미리 보여 사용자가 스스로 걸러내게 한다. 오등록 데이터가 "
         "도감에 쌓이는 것을 막는 저비용 장치."),
    ], screen_id="SCREEN 09", title="AI 판별 결과 확인",
        flow=["08 분석 → 확인 → 10 신규 / 11 재발견"]))
    return svg("".join(o) + page_header("09 AI 판별 결과 확인", "촬영·AI인증"), title="09 판별 확인")


# ══════════════════════════════════════════════ 10 신규 꽃 등록 완료
def s10():
    o = [frame_shell(), statusbar()]
    y = FY + 110
    o.append(t(FX + FW / 2, y, "새로운 꽃을 발견했어요!", 22, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 30, "장미가 도감에 등록되었습니다.", 14, 400, SUB, "middle"))
    # 반짝임
    for dx, dy, r in [(-96, 46, 3), (96, 52, 3), (-72, 150, 2.5), (84, 158, 2.5),
                      (-104, 100, 2), (104, 104, 2)]:
        o.append(circ(FX + FW / 2 + dx, y + dy, r, fill="#c8c8c8", stroke="none"))
    o.append(flowerph(FX + FW / 2, y + 148, 74, dash=True))
    o.append(t(FX + FW / 2, y + 254, "장미", 26, 700, INK, "middle"))
    s, w = chip(FX + FW / 2 - 30, y + 268, "37번째 꽃", False, 11, 12, 26)
    o.append(s)

    y += 320
    o.append(rect(L, y, CW, 96, r=14, fill=FILL, stroke="none"))
    for i, (k, v) in enumerate([("도감", "37 / 200종"), ("이번 시즌", "13종 (+1)")]):
        o.append(t(L + 18, y + 32 + i * 40, k, 12, 400, SUB))
        o.append(t(L + CW - 18, y + 34 + i * 40, v, 14, 700, INK, "end"))
    o.append(rect(L, y + 112, CW, 52, r=12, fill="#ffffff", stroke="#ececec"))
    o.append(t(L + 16, y + 143, "연남동 순위 24위 → 21위", 12.5, 700, INK))
    o.append(t(L + CW - 16, y + 143, "보기", 11.5, 700, SUB, "end"))

    by = FY + FH - 160
    o.append(btn(L, by, CW, 56, "지도에 공유하기", "primary", 16))
    o.append(btn(L, by + 68, CW, 50, "나만 보기", "ghost", 15))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 140, y - 320), (2, R + 4, y - 66),
                      (3, R + 4, y + 46), (4, R + 4, y + 138), (5, R + 4, by + 28)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "축하 문구 = 기획서 원문 유지",
         "'새로운 꽃을 발견했어요! / 장미가 도감에 등록되었습니다.' 2줄 구조 확정. "
         "반짝임 파티클은 과하지 않게(0.6초, 1회)."),
        (2, "몇 번째 꽃인지 배지",
         "숫자가 늘어나는 감각이 수집형의 핵심 보상. 10·50·100번째는 특별 연출."),
        (3, "도감 · 시즌 동시 갱신 표시",
         "신규 발견만 시즌 종수가 +1 된다는 규칙(기획서 11장)을 이 화면에서 학습시킨다."),
        (4, "순위 변동 알림",
         "지역 랭킹 상승을 즉시 보여주는 것이 재방문 동기. 순위 변동이 없으면 이 행은 숨김."),
        (5, "공유는 항상 선택",
         "기획서 8장. '나만 보기'를 회색 보조 버튼으로 두되 동등하게 누를 수 있게. "
         "탭하면 도감 상세(05)로 이동."),
    ], screen_id="SCREEN 10", title="신규 꽃 등록 완료",
        flow=["09 네, 맞아요 → (첫 발견) → 13 공유 설정 또는 05 상세"]))
    return svg("".join(o) + page_header("10 신규 꽃 등록 완료", "촬영·AI인증"), title="10 신규 등록")


# ══════════════════════════════════════════════ 11 기존 꽃 재발견
def s11():
    o = [frame_shell(), statusbar()]
    y = FY + 110
    o.append(t(FX + FW / 2, y, "장미를 다시 발견했어요!", 22, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 30, "이번이 네 번째 발견입니다.", 14, 400, SUB, "middle"))
    o.append(flowerph(FX + FW / 2, y + 140, 66, dash=True))
    o.append(t(FX + FW / 2, y + 236, "장미", 24, 700, INK, "middle"))

    y += 272
    o.append(rect(L, y, CW, 118, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 18, y + 28, "이번 발견 기록", 12, 700, INK))
    for i, (k, v) in enumerate([("날짜", "2026. 6. 14. 토"),
                                ("장소", "서울숲"), ("총 발견", "4회")]):
        o.append(t(L + 18, y + 54 + i * 22, k, 11.5, 400, SUB))
        o.append(t(L + CW - 18, y + 54 + i * 22, v, 12, 700, INK, "end"))

    y += 136
    o.append(rect(L, y, CW, 60, r=12, fill="#ffffff", stroke="#ececec"))
    o.append(t(L + 16, y + 26, "이번 시즌 종수는 늘지 않아요", 12.5, 700, INK))
    o.append(t(L + 16, y + 45, "같은 꽃은 한 종으로 계산해요. 사진은 도감에 쌓여요.",
               11, 400, SUB))

    y += 76
    o.append(t(L, y, "지금까지 만난 장미", 13.5, 700, INK))
    for i in range(4):
        o.append(imgph(L + i * ((CW - 24) / 4 + 8), y + 12, (CW - 24) / 4, (CW - 24) / 4,
                       "" if i else "NEW", 8))

    by = FY + FH - 160
    o.append(btn(L, by, CW, 56, "지도에 공유하기", "primary", 16))
    o.append(btn(L, by + 68, CW, 50, "나만 보기", "ghost", 15))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 132, FY + 140), (2, R + 4, FY + 440),
                      (3, R + 4, FY + 520), (4, R + 4, FY + 600)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "재발견 문구 = 기획서 원문",
         "'장미를 다시 발견했어요! / 이번이 네 번째 발견입니다.' 서수(네 번째)로 표기 — "
         "'4회째'보다 타깃 가독성이 높다. 11번째 이상은 '열한 번째'까지 한글, 이후 '12번째'."),
        (2, "이번 발견 기록 요약",
         "날짜·장소·누적 횟수(기획서 5장 누적 항목). 위치 권한 거부 시 장소 행은 숨김."),
        (3, "시즌 점수 미증가 안내",
         "여기서 설명하지 않으면 '점수가 안 올랐다'는 CS로 직결. 규칙(기획서 11장)을 "
         "실패가 아니라 사진이 쌓이는 이득으로 프레이밍한다."),
        (4, "누적 사진 스트립",
         "같은 꽃을 다시 찍을 이유를 시각화. 최신 사진에 NEW 표시, 탭하면 전체 뷰어."),
    ], screen_id="SCREEN 11", title="기존 꽃 재발견",
        flow=["09 네, 맞아요 → (이미 보유) → 13 공유 설정"]))
    return svg("".join(o) + page_header("11 기존 꽃 재발견", "촬영·AI인증"), title="11 재발견")


# ══════════════════════════════════════════════ 12 판별 실패
def s12():
    o = [frame_shell(), statusbar(), header("", back=True)]
    y = FY + 150
    o.append(circ(FX + FW / 2, y + 40, 44, fill=FILL, stroke=LINE, dash="5 4"))
    o.append(t(FX + FW / 2, y + 46, "?", 34, 700, HINT, "middle"))
    o.append(t(FX + FW / 2, y + 128, "어떤 꽃인지 알 수 없었어요", 21, 700, INK, "middle"))
    o.append(t(FX + FW / 2, y + 156, "다시 한 번 찍어 주시겠어요?", 14, 400, SUB, "middle"))

    y += 196
    o.append(rect(L, y, CW, 168, r=14, fill=FILL, stroke="none"))
    o.append(t(L + 18, y + 30, "이렇게 찍으면 잘 알아봐요", 13, 700, INK))
    tips = ["꽃 한 송이가 화면에 꽉 차게",
            "그림자 없는 밝은 곳에서",
            "정면이나 살짝 위에서",
            "흔들리지 않게 잠시 멈춰서"]
    for i, tp in enumerate(tips):
        o.append(circ(L + 26, y + 56 + i * 26, 3, fill=SUB, stroke="none"))
        o.append(t(L + 38, y + 60 + i * 26, tp, 12, 400, SUB))

    y += 190
    o.append(imgph(L, y, CW, 120, "내가 찍은 사진", 12, "이 사진은 저장되지 않았어요"))

    by = FY + FH - 160
    o.append(btn(L, by, CW, 56, "다시 찍기", "primary", 16))
    o.append(btn(L, by + 68, CW, 50, "나중에 할게요", "ghost", 15))

    o.append(clip_cover())
    for n, mx, my in [(1, FX + FW / 2 + 138, FY + 278), (2, R + 4, FY + 420),
                      (3, R + 4, FY + 596), (4, R + 4, by + 28)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "실패는 사용자 잘못이 아니게",
         "'인식 실패' 같은 시스템 어투 금지. '알 수 없었어요' + 부탁 형태로 재시도를 유도."),
        (2, "재촬영 팁 4개",
         "실패 화면의 실질 기능. 기획서 5장은 '다시 촬영하도록 안내'만 규정 → 구체 팁을 "
         "이 화면에 추가 정의."),
        (3, "실패 사진 미저장 명시",
         "촬영물이 어딘가 남는다는 불안을 차단."),
        (4, "3회 연속 실패 시 (분기)",
         "'꽃이 아닐 수도 있어요' 문구로 교체 + 도감 홈 복귀 유도. 무한 재시도 루프 방지."),
    ], screen_id="SCREEN 12", title="AI 판별 실패",
        flow=["08 분석 실패 → 재촬영(07) 또는 이탈"]))
    return svg("".join(o) + page_header("12 AI 판별 실패", "촬영·AI인증"), title="12 판별 실패")


# ══════════════════════════════════════════════ 13 지도 공유 설정
def s13():
    o = [frame_shell(), statusbar(), header("지도에 공유하기", back=True)]
    y = BODY_TOP + 18
    o.append(t(L, y + 8, "이 꽃을 지도에 공유할까요?", 19, 700, INK))
    o.append(t(L, y + 32, "공유하면 다른 사람이 이 장소에서 꽃을 찾아볼 수 있어요.",
               12, 400, SUB))

    y += 56
    o.append(rect(L, y, CW, 88, r=12, fill="#ffffff", stroke="#ececec"))
    o.append(imgph(L + 12, y + 12, 64, 64, "사진", 8))
    o.append(t(L + 88, y + 34, "장미", 15, 700, INK))
    o.append(t(L + 88, y + 56, "2026. 6. 14. · 4번째 발견", 11.5, 400, SUB))

    y += 108
    o.append(t(L, y, "발견 장소", 13, 700, INK))
    y += 12
    o.append(rect(L, y, CW, 66, r=12, fill="#ffffff", stroke=DARK, sw=1.5))
    o.append(path(f"M {L+26} {y+22} a 9 9 0 1 1 0.1 0 L {L+26} {y+46} Z",
                  stroke=DARK, sw=1.6))
    o.append(t(L + 48, y + 30, "서울숲", 14.5, 700, INK))
    o.append(t(L + 48, y + 50, "성동구 성수동1가 · 정확한 위치는 공개되지 않아요",
               10.5, 400, SUB))
    o.append(t(L + CW - 16, y + 40, "변경", 11.5, 700, SUB, "end"))

    y += 88
    o.append(t(L, y, "누구에게 보여줄까요?", 13, 700, INK))
    y += 14
    for lb, sub, on in [("모두에게 공개", "지도를 보는 누구나 볼 수 있어요", True),
                        ("친구에게만 공개", "연락처로 연결된 친구만 볼 수 있어요", False)]:
        o.append(rect(L, y, CW, 62, r=12, fill="#ffffff",
                      stroke=DARK if on else "#ececec", sw=1.5 if on else 1))
        o.append(radio(L + 14, y + 22, "", on))
        o.append(t(L + 48, y + 28, lb, 13.5, 700 if on else 400, INK))
        o.append(t(L + 48, y + 47, sub, 10.5, 400, HINT))
        y += 70

    y += 6
    o.append(t(L, y, "한 줄 남기기 (안 써도 돼요)", 13, 700, INK))
    o.append(rect(L, y + 12, CW, 62, r=12, fill="#ffffff", stroke=LINE))
    o.append(t(L + 14, y + 38, "예: 숲길 끝 벤치 옆에 활짝 피었어요", 12.5, 400, HINT))
    o.append(t(L + CW - 14, y + 62, "0 / 40", 10, 400, HINT, "end"))

    o.append(rect(FX, FY + FH - 118, FW, 118, fill="#ffffff", stroke="none"))
    o.append(line(FX, FY + FH - 118, FX + FW, FY + FH - 118, stroke="#ededed"))
    o.append(btn(L, FY + FH - 100, CW, 56, "공유하기", "primary", 16))
    o.append(t(FX + FW / 2, FY + FH - 26, "공유하지 않기", 13, 700, SUB, "middle"))

    o.append(clip_cover())
    for n, mx, my in [(1, R + 4, BODY_TOP + 118), (2, R + 4, BODY_TOP + 210),
                      (3, R + 4, BODY_TOP + 310), (4, R + 4, BODY_TOP + 458),
                      (5, FX + FW / 2 + 62, FY + FH - 26)]:
        o.append(marker(n, mx, my))
    o.append(annots([
        (1, "공유 대상 확인 카드",
         "무엇을 공유하는지 사진·이름·날짜로 재확인. 오공유 취소 문의를 줄인다."),
        (2, "장소는 이름 단위로만",
         "기획서 7장: 좌표 대신 장소명. '정확한 위치는 공개되지 않아요'를 상시 노출해 "
         "집 근처 촬영 시 거부감을 없앤다. '변경'으로 인근 장소 후보 재선택."),
        (3, "공개 범위 2택",
         "기본값 '모두에게 공개'. 단, 계정 지역과 촬영 위치가 다른 시·군·구면 기본값을 "
         "'친구에게만'으로 낮추는 안을 검토(집 위치 노출 방지)."),
        (4, "한 줄 설명은 선택",
         "기획서 8장. 라벨 자체에 '안 써도 돼요'를 넣어 필수로 오해하지 않게. 40자 제한."),
        (5, "공유하지 않기 = 텍스트 버튼",
         "도감 등록은 이미 끝난 상태(기획서 8장). 탭하면 05 도감 상세로 이동하며 "
         "'도감에는 저장됐어요' 토스트."),
    ], screen_id="SCREEN 13", title="지도 공유 설정",
        flow=["10/11 지도에 공유하기 → 설정 → 14 지도"]))
    return svg("".join(o) + page_header("13 지도 공유 설정", "촬영·AI인증"), title="13 공유 설정")


SCREENS_A = [("01_로그인", s01), ("02_지역선택", s02), ("03_권한안내", s03),
             ("04_도감홈", s04), ("05_도감상세", s05), ("06_도감필터", s06),
             ("07_카메라", s07), ("08_AI분석중", s08), ("09_판별확인", s09),
             ("10_신규등록", s10), ("11_재발견", s11), ("12_판별실패", s12),
             ("13_지도공유설정", s13)]
