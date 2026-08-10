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
# 🔴 납품은 44종이지만 전부 넣지 않는다. "언젠가 쓸 것"을 미리 넣지 않는다 —
#    아이콘이 있으면 그것을 놓을 자리를 만들게 되고, 그 자리는 눌러도
#    아무 일이 없는 버튼이 된다(이 저장소가 반복해 지적한 결함).
#    쓰는 화면이 생길 때 이 표에 한 줄을 더한다.
#
# 🔴 **`notReady`(죽은 버튼) 자리에는 붙이지 않는다.** 납품 아이콘 중 `검색`·`공유`·
#    `설정`·`프로필 수정`·`고객문의`·`초대`는 **호출부가 `notReady`** 다 — 아이콘을
#    붙이면 **동작하는 기능처럼 보이는 죽은 버튼**이 된다. 기능이 붙을 때 함께 넣는다.
#    (미적용 목록과 이유는 `디자이너_업무/아이콘/적용현황.md`)
MAPPING = {
    # ── 하단 내비 (전부 동작)
    "도감": "ic_tab_dex",        # 하단 내비 1번 탭
    "지도": "ic_tab_map",        # 하단 내비 2번 탭
    "랭킹": "ic_tab_ranking",    # 하단 내비 3번 탭
    "마이": "ic_tab_my",         # 하단 내비 4번 탭
    "꽃 촬영": "ic_capture",     # 하단 내비 가운데 셔터
    # ── 화면 03 권한 카드
    "위치": "ic_place",          # 화면 03 권한 카드(위치)
    "친구 추가": "ic_person_add",  # 화면 03 권한 카드(연락처)
    # ── 여기부터 이번에 추가. **호출부가 실제로 동작하는 것만** 넣었다.
    "뒤로": "ic_back",           # 공통 헤더 `뒤로` (화면 05·15·16·19·21)
    "닫기": "ic_close",          # 화면 07 카메라 `닫기` · 화면 22 시트 `닫기`
    "취소": "ic_cancel",         # 화면 08 분석중 `취소`
    "필터": "ic_filter",         # 화면 04 도감 헤더 `필터` (시트가 실제로 열린다)
    "도움말": "ic_help",         # 화면 07 카메라 `도움말` (시트가 열린다)
    "시즌": "ic_season",         # 화면 15 랭킹 `지난 시즌` (화면 16으로 이동한다)
    # ── 화면 07 카메라 측면 버튼. 어두운 chrome(0xFF3A3A3A) 위 대비를 재고 넣었다
    #    (플래시 7.5:1 · 전환 4.2:1 · 3:1 미달 픽셀 0~3%).
    "플래시": "ic_flash",        # 화면 07 `플래시 / 플래시 끄기` (상태가 실제로 바뀐다)
    "전환": "ic_switch_camera",  # 화면 07 `전환` (전/후면이 실제로 바뀐다)
    # ── 화면 13 공개 범위 2택. 라디오 행이라 텍스트가 이미 두 줄 붙어 있다.
    "모두 공개": "ic_public",    # 화면 13 `모두에게 공개`
    "친구만 공개": "ic_friends_only",  # 화면 13 `친구만 공개`
}

# 🔴 **`ic_retry`·`ic_region`을 뺐다** — 만들어 두고 안 쓰면 위 원칙("쓸 것만 넣는다")을
#    스스로 깨는 것이라 표에서 지웠다. 이유는 붙일 자리를 실측한 결과다:
#
#    | 아이콘 | 붙일 자리 | 왜 뺐나 |
#    |---|---|---|
#    | `다시 찍기` | 화면 09·12 `다시 찍기` | 그 자리는 [CfPrimaryButton](초록 채움)이다. 아이콘의 카메라 몸통이 **초록이라 배경에 녹는다**(중앙 대비 2.17:1, 3:1 미달 84.6%) — 분홍 화살표만 떠서 무엇인지 알 수 없다 |
#    | `활동 지역` | 화면 17·20 `동네 선택하기` | 그림에 **자물쇠가 그려져 있다.** 6개월 잠금(화면 20)을 뜻하는 그림인데, 붙일 자리는 **처음 고르는 버튼**이라 "잠겨 있다"로 읽힌다 — 뜻이 반대다 |
#
#    둘 다 기능은 살아 있다(죽은 버튼이 아니다). **아트가 그 자리에 안 맞는 것**이므로
#    디자이너에게 재납품을 요청할 항목이다(`적용현황.md` §3).

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

    sweep(set(MAPPING.values()))


def sweep(expected):
    """표에서 지운 아이콘의 **PNG가 남아 있으면 세운다.**

    🔴 이 스크립트는 **쓰기만 한다** — 표에서 한 줄을 지워도 지난번에 만든 PNG는
       `res/`에 그대로 남는다. 그래서 `ic_retry`·`ic_region`이 **다섯 밀도에 다
       있는데 코드에서 아무도 안 쓰는 상태**로 실제로 남아 있었다(실측).

    ⚠️ **증상이 없다.** APK에 몇 KB 더 들어갈 뿐 화면은 정상이고 빌드도 통과한다.
       다음 사람은 "리소스에 있으니 쓰라고 만든 것"으로 읽고 **자리를 만들어 붙인다**
       — 그게 이 저장소가 반복해 지적한 죽은 버튼이 되는 경로다.

    ⚠️ 지우지 않고 **세우기만 한다.** 여기서 `os.remove`를 하면 이 스크립트가
       모르는 아이콘(런처·스플래시는 다른 스크립트가 만든다)을 지울 수 있다.
    """
    stale = set()
    for suffix in DENSITIES:
        d = os.path.join(RES, f"drawable-{suffix}")
        for name in os.listdir(d):
            if not name.endswith(".png"):
                continue
            if name[:-4] not in expected:
                stale.add(name[:-4])
    if stale:
        names = " ".join(sorted(stale))
        raise SystemExit(
            f"🔴 표에 없는 아이콘이 res/에 남아 있다: {names}\n"
            f"   표에서 지웠다면 PNG도 지운다:\n"
            f"     for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do\n"
            f"       for n in {names}; do rm -f "
            f"android/app/src/main/res/drawable-$d/$n.png; done; done"
        )
    print(f"정리 확인: drawable-*/ 에 표({len(expected)}개) 밖의 아이콘 없음")


if __name__ == "__main__":
    main()
