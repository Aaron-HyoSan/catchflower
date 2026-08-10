#!/usr/bin/env python3
"""`sample_frames.swift`가 뽑은 프레임을 한 장으로 묶는다 — **영상을 눈으로 읽기 위해서다.**

프레임을 한 장씩 열어 보면 순서를 놓친다. 시트로 묶고 각 칸에 초를 찍어야
"몇 초에 무슨 화면인지"가 한눈에 보이고, 잘린 컷·중복 컷·검은 화면이 드러난다.

⚠️ **이 시트로 확인하지 않으면 잘린 영상을 제출한다.** 실제로 마지막 컷이 9.8초 잘린
   테이크가 있었는데 크기·재생시간·스크립트 로그가 모두 정상이었다.

사용: python3 제출물/_tools/contact_sheet.py <프레임폴더> [열수]
"""
import pathlib
import sys

from PIL import Image, ImageDraw


def build(d: pathlib.Path, cols: int = 7):
    files = sorted(d.glob("g*.jpg"))
    if not files:
        raise SystemExit(f"프레임이 없다: {d}/g*.jpg")
    tw, th = 170, 378  # 1080x2400 세로 비율
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * th), "black")
    draw = ImageDraw.Draw(sheet)
    for i, f in enumerate(files):
        x, y = (i % cols) * tw, (i // cols) * th
        sheet.paste(Image.open(f).resize((tw, th)), (x, y))
        # 초를 검은 띠 위에 노랑으로 찍는다. 흰 배경 화면에서는 글자가 안 보인다.
        label = f.stem[1:].lstrip("0") or "0"
        draw.rectangle([x, y, x + 52, y + 18], fill="black")
        draw.text((x + 3, y + 3), f"{label}s", fill="yellow")
    out = d / "sheet.png"
    sheet.save(out)
    return out, len(files)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 7
    out, n = build(pathlib.Path(sys.argv[1]), cols)
    print(f"시트 {n}장 → {out}")
