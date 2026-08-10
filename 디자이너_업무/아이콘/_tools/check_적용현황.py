# -*- coding: utf-8 -*-
"""`적용현황.md`가 실제 상태와 맞는지 센다.

    python3 디자이너_업무/아이콘/_tools/check_적용현황.py

## 왜 필요한가

🔴 **손으로 센 숫자는 낡을 뿐 아니라 처음부터 틀린다.** 이 저장소에서 이미
   겪었다(제출 문서의 계측 47 vs 실제 45, 꽃목록 26 vs 27). 이 문서도 §1~§4의
   개수 합이 납품 44장과 맞아야 하는데, **처음 쓸 때 3개를 빠뜨렸다**
   (`내 위치`·`앨범`·`판별 실패`) — 표는 그럴듯해 보였고 아무것도 안 걸렸다.

⚠️ **`적용현황.md`를 파싱하지 않는다.** 마크다운 표를 정규식으로 읽으면
   표 서식을 바꿀 때마다 이 스크립트가 조용히 0개를 세고 통과한다.
   대신 **분류를 여기 코드로 들고** 있고, 문서에는 그 결과를 옮겨 적는다.
   (문서와 이 파일이 갈라지면 그건 사람이 봐야 하는 문제다 — §5에 적혀 있다.)

무엇을 세나:
  ① 4개 분류의 합 == 납품 앱내아이콘 수 (누락·중복 없음)
  ② §1 적용 목록 == `build_android_ui_icons.py`의 `MAPPING` 키
  ③ §1의 리소스가 5밀도에 다 있다
  ④ §1의 리소스를 코드가 실제로 참조한다
"""

import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ICON_DIR = os.path.dirname(HERE)
SRC_DIR = os.path.join(ICON_DIR, "앱아이콘 + 앱내아이콘")
ROOT = os.path.abspath(os.path.join(ICON_DIR, "..", ".."))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
SRC_KT = os.path.join(ROOT, "android", "app", "src", "main", "java")

# 앱아이콘은 앱내아이콘이 아니다 — 런처·스플래시는 다른 스크립트가 쓴다.
NOT_UI_ICON = {"앱아이콘_시안E_1024"}

# ── 분류. `적용현황.md` §1~§4와 같아야 한다.
APPLIED = [
    "도감", "지도", "랭킹", "마이", "꽃 촬영", "위치", "친구 추가", "뒤로", "닫기",
    "도움말", "플래시", "전환", "취소", "모두 공개", "친구만 공개", "필터", "시즌",
]
DEAD_BUTTON = [  # §2 기능이 없는 자리(notReady)
    "검색", "초대", "설정", "프로필 수정", "알림", "고객문의", "공유", "목록", "순위 상승",
]
ART_MISMATCH = ["다시 찍기", "활동 지역"]  # §3 재납품 요청
NO_PLACE = [  # §4 놓을 자리가 없다
    "NEW", "길찾기", "내 위치", "댓글", "더보기", "등록", "미발견", "배지", "분석중",
    "비공개", "빈 상태", "성공", "앨범", "좋아요", "찍기", "판별 실패",
]

BUCKETS = {
    "§1 적용": APPLIED,
    "§2 죽은 버튼": DEAD_BUTTON,
    "§3 아트 불일치": ART_MISMATCH,
    "§4 자리 없음": NO_PLACE,
}
DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]


def nfc(s):
    """⚠️ macOS 파일명은 NFD, 이 파일의 리터럴은 NFC. 안 맞추면 **전량 불일치**한다."""
    return unicodedata.normalize("NFC", s)


def delivered():
    names = {nfc(n[:-4]) for n in os.listdir(SRC_DIR) if n.endswith(".png")}
    return names - NOT_UI_ICON


def mapping_keys():
    """생성 스크립트의 MAPPING을 **읽어서** 가져온다(베끼지 않는다)."""
    path = os.path.join(HERE, "build_android_ui_icons.py")
    text = open(path, encoding="utf-8").read()
    block = text.split("MAPPING = {", 1)[1].split("\n}", 1)[0]
    # 주석 줄을 먼저 버린다 — 주석에도 한글 이름이 들어 있다.
    lines = [ln.split("#")[0] for ln in block.splitlines()]
    return dict(re.findall(r'"([^"]+)"\s*:\s*"([^"]+)"', "\n".join(lines)))


def kotlin_sources():
    out = []
    for root, _, files in os.walk(SRC_KT):
        for f in files:
            if f.endswith(".kt"):
                out.append(os.path.join(root, f))
    return out


def main():
    fails = []
    names = delivered()

    # ① 분류의 합
    flat = [x for v in BUCKETS.values() for x in v]
    dupes = {x for x in flat if flat.count(x) > 1}
    if dupes:
        fails.append(f"두 분류에 실린 아이콘: {sorted(dupes)}")
    missing = sorted(names - set(flat))
    if missing:
        fails.append(f"어느 분류에도 없는 납품 아이콘: {missing}")
    ghost = sorted(set(flat) - names)
    if ghost:
        fails.append(f"납품에 없는 문서 항목(이름 오타?): {ghost}")

    for k, v in BUCKETS.items():
        print(f"{k}: {len(v)}개")
    print(f"합계 {len(flat)}개 / 납품 앱내아이콘 {len(names)}개")

    # ② §1 == MAPPING
    mapping = mapping_keys()
    if set(mapping) != set(APPLIED):
        only_map = sorted(set(mapping) - set(APPLIED))
        only_doc = sorted(set(APPLIED) - set(mapping))
        fails.append(
            f"§1과 MAPPING이 다르다 — MAPPING에만: {only_map} / 문서에만: {only_doc}"
        )

    # ③ 5밀도
    for korean in APPLIED:
        res_name = mapping.get(korean)
        if not res_name:
            continue  # ②가 이미 세운다
        for d in DENSITIES:
            p = os.path.join(RES, f"drawable-{d}", f"{res_name}.png")
            if not os.path.exists(p):
                fails.append(f"drawable-{d}/{res_name}.png 이 없다")

    # ④ 코드 참조
    body = "\n".join(open(f, encoding="utf-8").read() for f in kotlin_sources())
    for korean in APPLIED:
        res_name = mapping.get(korean)
        if res_name and f"R.drawable.{res_name}" not in body:
            fails.append(f"{res_name}: 리소스는 있는데 코드에서 아무도 안 쓴다")

    if fails:
        print()
        for f in fails:
            print(f"🔴 {f}")
        sys.exit(1)
    print(f"판정: PASS (적용 {len(APPLIED)}개 · 5밀도 · 코드 참조 확인)")


if __name__ == "__main__":
    main()
