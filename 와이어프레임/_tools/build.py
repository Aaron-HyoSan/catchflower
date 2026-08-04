# -*- coding: utf-8 -*-
"""와이어프레임 SVG 일괄 생성 + 인덱스 HTML."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.dirname(HERE)

import wf_lib                             # noqa: E402
from screens_a import SCREENS_A          # noqa: E402
from screens_b import SCREENS_B          # noqa: E402
from screens_flowmap import SCREENS_FLOWMAP  # noqa: E402

ALL = SCREENS_A + SCREENS_B
# 24번 흐름 지도는 화면을 30%로 축소해 넣으므로 주석 마커를 끈 상태로 그린다.
NO_MARKER = {name for name, _ in SCREENS_FLOWMAP}


def main():
    made = []
    for name, fn in ALL + SCREENS_FLOWMAP:
        wf_lib._OPTS["markers"] = name not in NO_MARKER
        svg = fn()
        wf_lib._OPTS["markers"] = True
        p = os.path.join(OUT, f"{name}.svg")
        with open(p, "w", encoding="utf-8") as f:
            f.write(svg)
        made.append((name, len(svg)))
        print(f"  {name}.svg  ({len(svg):,} bytes)")

    # 인덱스 HTML (한눈에 보기)
    cards = "\n".join(
        f'<figure><img src="{n}.svg" alt="{n}"><figcaption>{n.replace("_", " ")}</figcaption></figure>'
        for n, _ in made)
    html = f"""<!doctype html><html lang="ko"><meta charset="utf-8">
<title>캐치플라워 와이어프레임 v0.1</title>
<style>
 body{{margin:0;padding:40px;background:#f6f6f4;
   font-family:'Apple SD Gothic Neo','Noto Sans KR',sans-serif;color:#1a1a1a}}
 h1{{font-size:24px;margin:0 0 6px}}
 p.lead{{color:#6b6b6b;font-size:13px;margin:0 0 32px}}
 figure{{margin:0 0 40px;background:#fff;border:1px solid #e4e4e4;border-radius:14px;
   overflow:hidden}}
 img{{display:block;width:100%;height:auto}}
 figcaption{{padding:12px 18px;font-size:13px;font-weight:700;border-top:1px solid #f0f0f0}}
</style>
<h1>캐치플라워 와이어프레임 v0.1</h1>
<p class="lead">전체 {len(made)}장 · 회색조 와이어프레임 · 오른쪽 열은 화면별 스펙 주석</p>
{cards}
</html>"""
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)
    print(f"\n총 {len(made)}장 생성 + index.html")


if __name__ == "__main__":
    main()
