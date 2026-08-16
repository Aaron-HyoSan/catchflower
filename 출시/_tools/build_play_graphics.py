#!/usr/bin/env python3
"""Play 스토어 그래픽 2종을 **납품 아트에서** 굽는다 (아이콘 512 · 그래픽 이미지 1024×500).

    python3 출시/_tools/build_play_graphics.py

출력은 `출시/스토어_그래픽/`이고 **생성물이다** — 손으로 고치면 다음 실행에 사라진다.

무엇을 어디서 가져오나 (두 벌을 만들지 않는 것이 이 파일의 전부다)
──────────────────────────────────────────────────────────────
· 그림 → `디자이너_업무/아이콘/…/앱아이콘_시안E_1024.png` (납품 아트)
· 꽃 분리·배지 초록 → `build_android_launcher.py`의 `extract_flower` · `badge_green`을
  **불러서 쓴다.** 🔴 여기 다시 구현하면 런처 아이콘과 스토어 아이콘이 갈라지는데,
  둘을 나란히 볼 사람이 없어서 아무도 모른다.
· 글자 → `출시/스토어_등록정보.md`의 `그래픽 문구` · `그래픽 보조 문구`.
  🔴 문구를 여기 적지 않는다 — 스토어 글과 그림이 다른 말을 하게 된다.

크기를 어떻게 정했나
────────────────────
· 아이콘은 **정사각 풀블리드**다. 런처에서 이미 `초록 배경 + 꽃 전경`으로 나뉘어 있고
  (`build_android_launcher.py` 독스트링의 이중 마스킹), 스토어도 같은 얼굴이어야 한다.
  Play가 표시할 때 모서리를 스스로 둥글게 깎으므로 **여기서 깎지 않는다.**
· 꽃 크기 `FLOWER_RATIO`는 **눈으로 정한 값이다**(측정값이 아니다). 런처의 꽃은
  108dp 캔버스에 66dp이고 원형 마스크의 보이는 지름은 72dp라 **보이는 영역의 92%**를
  채운다. 정사각에서 92%면 가장자리에 닿아 답답하므로 74%로 줄였다.
· 그래픽 이미지는 Play가 배치에 따라 **가장자리를 자를 수 있다.** 그래서 글자와 꽃을
  `MARGIN` 안쪽에만 그린다.

🔴 두부(tofu) 함정 — **글꼴이 한글을 못 그려도 스크립트는 성공한다.** 빠진 글자는 빈
   칸이나 네모로 그려지고 PIL은 오류를 내지 않는다. 그래서 `assert_hangul`이 글자마다
   잉크를 재고, **여러 글자가 똑같은 모양으로 나오면**(= 네모) 실패로 본다.
   ⚠️ 그래도 마지막 확인은 사람이 그림을 보는 것이다 — 이 저장소의 "영상의 '성공'도
   증거가 아니다"와 같은 이유다.
"""

import importlib.util
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "출시" / "스토어_그래픽"
LAUNCHER = ROOT / "디자이너_업무/아이콘/_tools/build_android_launcher.py"

ICON_SIDE = 512
FEATURE = (1024, 500)
MARGIN = 64
#: 위 독스트링 "크기를 어떻게 정했나" — 측정값이 아니라 결정이다.
FLOWER_RATIO = 0.74

#: Play의 파일 크기 상한(바이트). 넘으면 콘솔이 업로드를 거부한다.
LIMITS = {"icon_512.png": 1024 * 1024, "feature_1024x500.png": 15 * 1024 * 1024}

#: 한글이 있는 글꼴. 앱은 **플랫폼 기본 글꼴**을 쓰므로(res/font가 없다) 스토어 그래픽과
#: 앱 화면의 글꼴은 원래 다르다 — 맞추는 것이 목표가 아니다.
FONT_CANDIDATES = [
    ("/System/Library/Fonts/AppleSDGothicNeo.ttc", 4),  # Bold
    ("/System/Library/Fonts/AppleSDGothicNeo.ttc", 0),
    ("/System/Library/Fonts/Supplemental/AppleGothic.ttf", 0),
]


def module(path: pathlib.Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def font(size: int) -> ImageFont.FreeTypeFont:
    for path, index in FONT_CANDIDATES:
        if pathlib.Path(path).is_file():
            return ImageFont.truetype(path, size, index=index)
    sys.exit(f"🔴 한글 글꼴을 못 찾았다: {[p for p, _ in FONT_CANDIDATES]}")


def assert_hangul(f: ImageFont.FreeTypeFont, text: str) -> None:
    """글꼴이 이 문장의 한글을 **정말 그리는지** 본다 (위 독스트링의 두부 함정)."""
    hangul = sorted({c for c in text if "가" <= c <= "힣"})
    if not hangul:
        return
    signatures = {}
    for ch in hangul:
        mask = f.getmask(ch, mode="L")
        ink = sum(1 for v in mask if v > 0)
        if ink == 0:
            sys.exit(f"🔴 글꼴이 `{ch}`를 빈 칸으로 그린다 — 글자가 안 보이는 그림이 나온다")
        signatures.setdefault((mask.size, ink), []).append(ch)
    worst = max(signatures.values(), key=len)
    if len(worst) >= 3:
        sys.exit(
            f"🔴 서로 다른 글자 {len(worst)}개가 똑같은 모양이다({' '.join(worst)}) — "
            "빠진 글자를 네모로 그리는 중이다(두부)"
        )


def mix(a, b, t: float):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def build_icon(flower: Image.Image, green) -> pathlib.Path:
    canvas = Image.new("RGBA", (ICON_SIDE, ICON_SIDE), (*green, 255))
    f = flower.copy()
    inner = int(round(ICON_SIDE * FLOWER_RATIO))
    f.thumbnail((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(f, ((ICON_SIDE - f.width) // 2, (ICON_SIDE - f.height) // 2))
    # 🔴 투명 픽셀이 남으면 Play가 거부한다. 배경을 꽉 칠했으니 0이어야 한다 — 재서 확인한다.
    clear = sum(1 for _, _, _, a in canvas.getdata() if a < 255)
    if clear:
        sys.exit(f"🔴 아이콘에 반투명·투명 픽셀이 {clear}개 남았다")
    path = OUT / "icon_512.png"
    canvas.save(path)
    print(f"  icon_512.png {ICON_SIDE}×{ICON_SIDE} (꽃 {f.width}px · 배경 {green})")
    return path


def build_feature(flower: Image.Image, green, lines: tuple[str, str]) -> pathlib.Path:
    w, h = FEATURE
    top, bottom = mix(green, (255, 255, 255), 0.30), mix(green, (0, 0, 0), 0.18)
    canvas = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(canvas)
    # 위→아래 그라데이션. 색은 배지 초록에서 뽑은 것이라 손으로 박은 색이 없다.
    for y in range(h):
        d.line([(0, y), (w, y)], fill=mix(top, bottom, y / (h - 1)))

    # ── 왼쪽: 꽃 ────────────────────────────────────────────────
    side = h - MARGIN * 2
    f = flower.copy().convert("RGBA")
    f.thumbnail((side, side), Image.LANCZOS)
    fx = MARGIN + (side - f.width) // 2
    canvas.paste(f, (fx, (h - f.height) // 2), f)

    # ── 오른쪽: 글자 ────────────────────────────────────────────
    x = MARGIN + side + 56
    name_f, main_f, sub_f = font(92), font(46), font(34)
    for f_ in (name_f, main_f, sub_f):
        assert_hangul(f_, "캐치플라워" + lines[0] + lines[1])
    blocks = [
        ("캐치플라워", name_f, (255, 255, 255), 18),
        (lines[0], main_f, (255, 255, 255), 12),
        (lines[1], sub_f, mix(green, (255, 255, 255), 0.78), 0),
    ]
    heights = [d.textbbox((0, 0), t, font=f_)[3] + gap for t, f_, _, gap in blocks]
    y = (h - sum(heights)) // 2
    for (text, f_, color, gap), height in zip(blocks, heights):
        d.text((x, y), text, font=f_, fill=color)
        right = x + d.textbbox((0, 0), text, font=f_)[2]
        if right > w - MARGIN:
            sys.exit(f"🔴 `{text}`가 오른쪽 여백을 {right - (w - MARGIN)}px 넘는다 — 잘릴 수 있다")
        y += height

    path = OUT / "feature_1024x500.png"
    canvas.save(path)
    print(f"  feature_1024x500.png {w}×{h} (글자 3줄 · 꽃 {f.width}px)")
    return path


def main() -> int:
    launcher = module(LAUNCHER, "build_android_launcher")
    listing = module(ROOT / "출시/_tools/store_listing.py", "store_listing")

    src_path = pathlib.Path(launcher.SRC)
    if not src_path.is_file():
        sys.exit(f"🔴 납품 아트가 없다: {src_path}")
    src = Image.open(src_path).convert("RGBA")
    flower = launcher.extract_flower(src)
    green = launcher.badge_green(src)

    lines = (listing.get("그래픽_문구"), listing.get("그래픽_보조_문구"))
    print(f"문구 (등록정보 문서에서 읽었다): {lines[0]} / {lines[1]}")

    OUT.mkdir(parents=True, exist_ok=True)
    made = [build_icon(flower, green), build_feature(flower, green, lines)]

    print()
    for p in made:
        size = p.stat().st_size
        limit = LIMITS[p.name]
        mark = "🔵" if size <= limit else "🔴"
        print(f"   {mark} {p.relative_to(ROOT)} {size:,}바이트 (상한 {limit:,})")
        if size > limit:
            return 1
    print()
    print("⚠️ **그림은 눈으로 봐야 확인이 끝난다.** 글자가 잘리거나 꽃이 배경에 묻혔는지는")
    print("   위 검사가 못 잡는다. 두 파일을 열어 보고 `출시/스토어_등록정보.md` 7절에 적는다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
