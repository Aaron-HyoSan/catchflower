# -*- coding: utf-8 -*-
"""앱내아이콘 → `android/.../res/drawable-*/` PNG.

    python3 디자이너_업무/아이콘/_tools/build_android_ui_icons.py

## 왜 `res/`에 직접 넣는가 (일러스트는 `assets/`인데)

일러스트 200장은 **도감번호로 찾는 데이터**다 — 어느 꽃이 필요할지는 실행 중에
정해지므로 `assets/`에서 이름으로 연다. 아이콘은 반대다: **코드에 박힌 고정
7개**이고, 리소스로 두면 밀도별 자동 선택(`drawable-xxhdpi`)을 안드로이드가
해 준다. `assets/`에 두면 그 선택을 손으로 짜야 한다.

🔴 **원본 파일명이 한글이라 그대로는 리소스가 될 수 없다** — 안드로이드 리소스
이름은 `[a-z0-9_]`만 받는다. 그래서 이 스크립트가 **영문 이름으로 바꿔 복사한다.**

⚠️ **이름 표를 여기 두는 것이 위험 지점이다.** macOS 파일명은 NFD(자모 분리),
   이 파일의 문자열 리터럴은 NFC라서 `==`로 비교하면 **전부 불일치한다**(같은
   글자로 보인다). `unicodedata.normalize`로 맞춰서 찾고, 못 찾으면 **세운다** —
   조용히 건너뛰면 아이콘 없는 빈 칸이 앱에 남는다.
"""

import os
import unicodedata

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ICON_DIR = os.path.dirname(HERE)
SRC_DIR = os.path.join(ICON_DIR, "앱아이콘 + 앱내아이콘")
RES = os.path.abspath(os.path.join(ICON_DIR, "..", "..", "android", "app", "src", "main", "res"))

# 한글 원본 → 리소스 이름. **여기 있는 것만 앱에 들어간다.**
#
# 🔴 납품은 44종이지만 7종만 쓴다. "언젠가 쓸 것"을 미리 넣지 않는다 —
#    아이콘이 있으면 그것을 놓을 자리를 만들게 되고, 그 자리는 눌러도
#    아무 일이 없는 버튼이 된다(이 저장소가 반복해 지적한 결함).
#    쓰는 화면이 생길 때 이 표에 한 줄을 더한다.
MAPPING = {
    "도감": "ic_tab_dex",        # 하단 내비 1번 탭
    "지도": "ic_tab_map",        # 하단 내비 2번 탭
    "랭킹": "ic_tab_ranking",    # 하단 내비 3번 탭
    "마이": "ic_tab_my",         # 하단 내비 4번 탭
    "꽃 촬영": "ic_capture",     # 하단 내비 가운데 셔터
    "위치": "ic_place",          # 화면 03 권한 카드(위치)
    "친구 추가": "ic_person_add",  # 화면 03 권한 카드(연락처)
}

# 아이콘은 24dp(탭)와 20dp(권한 카드)로 쓴다. 가장 큰 사용처 24dp × 4배(xxxhdpi)
# = 96px이므로 원본 512px을 그대로 넣을 이유가 없다 — 밀도별로 필요한 크기만 만든다.
# ⚠️ 24dp를 기준으로 만든 다음 20dp 자리에 쓰면 **살짝 줄여 그린다**(흐려지지 않는다).
BASE_DP = 24
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def sources():
    """원본 폴더를 NFC 정규화한 이름으로 색인한다."""
    out = {}
    for name in os.listdir(SRC_DIR):
        if not name.endswith(".png"):
            continue
        out[unicodedata.normalize("NFC", name)] = os.path.join(SRC_DIR, name)
    return out


def main():
    index = sources()
    print(f"원본 {len(index)}개 중 {len(MAPPING)}개를 쓴다")
    for korean, res_name in MAPPING.items():
        key = unicodedata.normalize("NFC", korean + ".png")
        # 조용히 건너뛰지 않는다 — 빈 칸은 화면에서 "아직 안 만든 것"으로 보인다.
        assert key in index, f"원본을 못 찾았다: {korean}.png (NFC로 찾았다)"
        im = Image.open(index[key]).convert("RGBA")
        im = im.crop(im.getbbox())  # 아트보드 여백 제거 — 남기면 실제 아이콘이 작게 보인다
        for suffix, scale in DENSITIES.items():
            side = int(round(BASE_DP * scale))
            canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
            t = im.copy()
            t.thumbnail((side, side), Image.LANCZOS)
            canvas.alpha_composite(t, ((side - t.width) // 2, (side - t.height) // 2))
            d = os.path.join(RES, f"drawable-{suffix}")
            os.makedirs(d, exist_ok=True)
            canvas.save(os.path.join(d, f"{res_name}.png"))
        print(f"  {korean} → {res_name}.png ({im.size[0]}×{im.size[1]} 원본)")


if __name__ == "__main__":
    main()
