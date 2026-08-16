# -*- coding: utf-8 -*-
"""앱아이콘_시안E → 안드로이드 런처 아이콘 · 스플래시 아이콘.

    python3 디자이너_업무/아이콘/_tools/build_android_launcher.py

`android/app/src/main/res/` 아래 PNG를 **덮어쓴다**. 결과 PNG는 생성물이므로
손으로 고치지 않는다 — 시안이 바뀌면 이 스크립트를 다시 돌린다.

## 왜 배지를 통째로 넣지 않는가

납품된 앱아이콘은 **이미 원형 초록 배지**다. 어댑티브 아이콘의 전경(foreground)에
그대로 넣으면 런처가 **한 번 더 원형으로 깎는다** — 배지 테두리가 잘려 나가고
꽃도 같이 작아진다(이중 마스킹). 그래서 전경은 **꽃만**, 배경은 배지의 초록
**단색**으로 나눈다.

## 꽃을 어떻게 떼어내는가

색상키(초록 픽셀 제거)로 하면 **꽃심의 흰 하이라이트가 같이 지워진다** —
`g-r`이 음수가 아니라 0 근처라서 초록으로 오판된다. 실제로 그렇게 만들어 봤고
꽃 가운데에 초록 구멍이 뚫렸다. 그래서 **바깥에서 배경을 채워 들어간다**
(flood fill): 이미지 경계에서 시작해 초록·투명 픽셀만 타고 번지고, **끝까지
못 닿은 픽셀은 꽃 내부**로 본다. 하이라이트는 꽃잎에 둘러싸여 있어 살아남는다.
"""

import collections
import os
import unicodedata

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ICON_DIR = os.path.dirname(HERE)
SRC = os.path.join(ICON_DIR, "앱아이콘 + 앱내아이콘", "앱아이콘_시안E_1024.png")
RES = os.path.abspath(os.path.join(ICON_DIR, "..", "..", "android", "app", "src", "main", "res"))

# 어댑티브 아이콘: 108dp 캔버스 중 **가운데 66dp만** 항상 보인다(나머지는 런처가
# 모양대로 깎는다). 꽃을 그 안에 넣는다 — 넘기면 꽃잎 끝이 원형 런처에서 잘린다.
ADAPTIVE_SAFE = 66.0 / 108.0
# 안드로이드 12+ 스플래시 아이콘 안의 꽃 크기.
#
# 🔴 **문서의 안전영역(288 중 192)을 그대로 쓰면 안 된다 — 실측으로 확인했다.**
#    처음 `192/288`(=0.667)로 만들었더니 기기에서 **꽃이 초록 원을 정확히 꽉 채워**
#    원이 1px 테두리로만 보였다(실측: 원 지름 417px, 꽃 지름 419px · 비율 1.005).
#    `windowSplashScreenIconBackgroundColor`를 주면 시스템이 아이콘 배경 원을
#    직접 그리고 **그 원의 지름이 곧 192dp 영역**이라, 문서가 말한 "안전영역"이
#    여기서는 **여백이 아니라 경계**다.
#    ⚠️ 이건 화면을 봐야만 드러난다 — 빌드·설치·실행 전부 정상이고,
#       "아이콘이 크게 나온다"는 것 말고는 어떤 로그도 없다.
SPLASH_SAFE = 0.45

# dp → px 배율. mdpi(=1x)에서 어댑티브 캔버스는 108px이다.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# 스플래시 배지(원+꽃 한 장)의 캔버스 dp. **화면에 나오는 크기가 곧 이 값이다.**
#
# 🔴 **108이었다가 160으로 고쳤다(2026-08-16) — 실측 때문이다.**
#    `windowSplashScreenIconBackgroundColor`를 주면 안드로이드 12+ 시스템은
#    아이콘 배경 원을 **지름 160dp**로 그린다(240dp 캔버스 중 160dp). 위
#    `SPLASH_SAFE` 주석의 실측 `원 지름 417px`도 같은 값이다(420dpi에서 159dp).
#    그런데 이 배지는 108dp로 만들어져 있었다 — 즉
#    ① API 31+ 시스템 스플래시(160dp) → ② 화면 00 로딩 화면(배지 원본 크기)로
#    넘어갈 때 로고가 **1.48배 줄어들며 깜빡였다**(에뮬레이터 실측:
#    시스템 420px → 화면 00 284px). API 31 미만 경로도 이 PNG를 원본 픽셀로
#    그리므로(`drawable/splash_background.xml`) 세 곳이 이 한 값으로 맞는다.
#
# ⚠️ **여기를 고치면 코틀린 쪽도 같이 고쳐야 한다** —
#    `AppLoadingScreen.BADGE_DP`. 두 값이 어긋나면 화면에서 다시 깜빡이는데
#    빌드·테스트는 통과한다. 그래서 `AppLoadingGateTest`가 **이 파일의 숫자와
#    코틀린 상수와 실제 PNG 크기를 셋 다 비교**한다.
#
# ⚠️ 상한은 시안 크기다 — xxxhdpi가 `BADGE_DP * 4`px이므로 1024/4 = **256dp까지**
#    확대 없이 만들 수 있다. 넘기면 흐려진다(그때는 시안을 다시 받는다).
BADGE_DP = 160


def extract_flower(src: Image.Image) -> Image.Image:
    """배지에서 꽃만 잘라낸다. 위 독스트링의 flood fill."""
    w, h = src.size
    px = src.load()

    def is_bg(x, y):
        r, g, b, a = px[x, y]
        # 초록(g가 r보다 확실히 큰 픽셀)과 완전 투명. 경계의 섞인 픽셀은 꽃으로 남긴다 —
        # 배경색을 배지와 같은 초록으로 두므로 티가 나지 않는다.
        return a < 8 or (g - r) >= 12

    reached = [[False] * w for _ in range(h)]
    q = collections.deque()
    for x in range(w):
        for y in (0, h - 1):
            if is_bg(x, y) and not reached[y][x]:
                reached[y][x] = True
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if is_bg(x, y) and not reached[y][x]:
                reached[y][x] = True
                q.append((x, y))
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not reached[ny][nx] and is_bg(nx, ny):
                reached[ny][nx] = True
                q.append((nx, ny))

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    kept = 0
    for y in range(h):
        for x in range(w):
            if reached[y][x]:
                continue
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            op[x, y] = (r, g, b, 255)
            kept += 1
    # 꽃이 배지 면적의 절반쯤은 되어야 한다. 색 판정이 어긋나면 여기서 걸린다.
    total = w * h
    assert 0.2 * total < kept < 0.8 * total, f"꽃 픽셀이 {kept}/{total}다 — 분리가 틀렸다"
    box = out.getbbox()
    return out.crop(box)


def badge_green(src: Image.Image):
    """배지에서 **꽃 바로 뒤 초록**을 읽는다. 색을 손으로 박지 않는다."""
    w, h = src.size
    px = src.load()
    # 꽃은 가운데에 있으므로 링 안쪽·꽃 바깥인 지점을 세로로 훑어 초록을 모은다.
    greens = []
    for y in range(int(h * 0.12), int(h * 0.88)):
        for x in (int(w * 0.11), int(w * 0.89)):
            r, g, b, a = px[x, y]
            if a > 250 and (g - r) >= 24:
                greens.append((r, g, b))
    assert greens, "배지 초록을 못 찾았다"
    greens.sort(key=lambda c: c[1])
    return greens[len(greens) // 2]


def write_scaled(flower, name, canvas_dp, safe_ratio):
    for suffix, scale in DENSITIES.items():
        side = int(round(canvas_dp * scale))
        canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        inner = int(round(side * safe_ratio))
        f = flower.copy()
        f.thumbnail((inner, inner), Image.LANCZOS)
        canvas.alpha_composite(f, ((side - f.width) // 2, (side - f.height) // 2))
        d = os.path.join(RES, f"mipmap-{suffix}")
        os.makedirs(d, exist_ok=True)
        canvas.save(os.path.join(d, f"{name}.png"))
        print(f"  mipmap-{suffix}/{name}.png {side}×{side} (꽃 {f.width}px)")


def main():
    assert os.path.exists(SRC), f"시안이 없다: {SRC}"
    # macOS 파일명은 NFD다 — 경로를 문자열로 조립해 비교하지 않고 존재만 확인한다.
    src = Image.open(SRC).convert("RGBA")
    assert src.size == (1024, 1024), f"시안 크기가 {src.size}다"
    # 배지를 **확대**해서 만들지 못하게 막는다 — 흐려진 PNG는 화면에서만 보이고
    # 빌드·테스트는 전부 통과한다(BADGE_DP 주석의 상한 256dp를 여기서 센다).
    biggest = BADGE_DP * max(DENSITIES.values())
    assert biggest <= src.size[0], f"BADGE_DP={BADGE_DP}는 시안({src.size[0]}px)을 {biggest}px로 확대한다"

    flower = extract_flower(src)
    r, g, b = badge_green(src)
    print(f"배지 초록 #{r:02X}{g:02X}{b:02X} · 꽃 {flower.size}")

    print("런처 전경(어댑티브 108dp 캔버스):")
    write_scaled(flower, "ic_launcher_foreground", 108, ADAPTIVE_SAFE)
    print("스플래시 아이콘(288dp 캔버스):")
    write_scaled(flower, "ic_launcher_splash", 288, SPLASH_SAFE)

    # 스플래시 배지(API 31 **미만** 경로). 배지를 통째로 쓴다 — 여기는 시스템이
    # 아이콘 배경 원을 그려 주지 않으므로 초록 원까지 그림에 들어 있어야 한다.
    #
    # ⚠️ **크기가 곧 화면에 나오는 크기다.** `layer-list`의 `<bitmap>`은 원본
    #    픽셀 그대로 그리므로, 288dp 캔버스로 만들면 스플래시에서 화면을 거의
    #    다 덮는다. [BADGE_DP] 캔버스로 만들어 안드로이드 12가 그리는 원 지름과
    #    맞춘다 — 그 값이 왜 160인지는 [BADGE_DP] 주석에 실측으로 적어 뒀다.
    print(f"스플래시 배지(레거시 · {BADGE_DP}dp 캔버스):")
    for suffix, scale in DENSITIES.items():
        side = int(round(BADGE_DP * scale))
        img = src.resize((side, side), Image.LANCZOS)
        d = os.path.join(RES, f"mipmap-{suffix}")
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, "ic_splash_badge.png"))
        print(f"  mipmap-{suffix}/ic_splash_badge.png {side}×{side}")

    # 레거시 런처 아이콘 — minSdk 26이라 어댑티브만으로 충분하지만,
    # 일부 런처·설정 화면·공유 시트가 **레거시를 먼저 찾는다.** 여기는 배지를
    # 통째로 쓴다(이미 원형이라 깎을 것이 없다).
    print("레거시 런처 아이콘(48dp 캔버스):")
    for suffix, scale in DENSITIES.items():
        side = int(round(48 * scale))
        img = src.resize((side, side), Image.LANCZOS)
        d = os.path.join(RES, f"mipmap-{suffix}")
        os.makedirs(d, exist_ok=True)
        img.save(os.path.join(d, "ic_launcher.png"))
        print(f"  mipmap-{suffix}/ic_launcher.png {side}×{side}")

    # 배경색은 XML에 박지 않고 여기서 써 준다 — 시안을 바꾸면 색도 따라 바뀐다.
    color_xml = os.path.join(RES, "values", "ic_launcher_background.xml")
    with open(color_xml, "w", encoding="utf-8") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- 🔴 생성물이다. 손으로 고치지 않는다 —\n"
            "     `python3 디자이너_업무/아이콘/_tools/build_android_launcher.py`가 쓴다.\n"
            "     값은 앱아이콘_시안E의 **꽃 바로 뒤 초록**을 실측한 것이다. -->\n"
            "<resources>\n"
            f'    <color name="ic_launcher_background">#FF{r:02X}{g:02X}{b:02X}</color>\n'
            "</resources>\n"
        )
    print(f"values/ic_launcher_background.xml = #{r:02X}{g:02X}{b:02X}")


if __name__ == "__main__":
    main()
