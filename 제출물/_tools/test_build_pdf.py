#!/usr/bin/env python3
"""build_pdf.py 마크다운 변환 검사.

**왜 있나:** 이 스크립트가 조용히 틀리는 방식이 두 가지다 —
① 표 칸이 밀리는데(이스케이프된 `|`) PDF는 예쁘게 나온다,
② 인라인 코드 안의 `*`가 기울임으로 먹혀 **코드가 다른 문자열로 인쇄된다.**
둘 다 PDF를 열어 봐도 "그럴듯해" 보이므로 눈으로는 못 잡는다.

    python3 제출물/_tools/test_build_pdf.py
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from build_pdf import inline, md_to_html, split_row  # noqa: E402

fails: list[str] = []


def check(label: str, got: object, want: object) -> None:
    if got != want:
        fails.append(f"{label}\n  기대: {want!r}\n  실제: {got!r}")


# ── 표 칸 나누기 ──────────────────────────────────────────────────────
check("표 3칸", split_row("| a | b | c |"), ["a", "b", "c"])
check("이스케이프된 파이프는 칸이 아니다", split_row(r"| a \| b | c |"), ["a | b", "c"])
check("빈 칸 유지", split_row("| a |  | c |"), ["a", "", "c"])

# ── 인라인 ────────────────────────────────────────────────────────────
check("굵게", inline("**진짜**"), "<strong>진짜</strong>")
check("인라인 코드", inline("`x=1`"), "<code>x=1</code>")
check(
    "코드 안의 * 는 기울임이 아니다",
    inline("`*args*`"),
    "<code>*args*</code>",
)
check(
    "코드 안의 링크 문법도 살아남는다",
    inline("`[a](b)`"),
    "<code>[a](b)</code>",
)
check("HTML 이스케이프", inline("a < b & c"), "a &lt; b &amp; c")
check(
    "코드 안 HTML도 이스케이프",
    inline("`<script>`"),
    "<code>&lt;script&gt;</code>",
)
check(
    "링크",
    inline("[여기](https://x.com)"),
    '<a href="https://x.com">여기</a>',
)
check("곱셈 별표는 기울임이 아니다", inline("2 * 3 * 4"), "2 * 3 * 4")

# ── 블록 ──────────────────────────────────────────────────────────────
h = md_to_html("| 머리 | 값 |\n|---|---|\n| a | b |")
check("표 생성", "<table>" in h and "<th>머리</th>" in h and "<td>b</td>" in h, True)

h = md_to_html("| 파이프 | 아님 |\n|---|---|\n| a `|` b | c |")
check("표 안 인라인 코드", "<code>|</code>" in h, True)

h = md_to_html("| 한 줄 |\n\n이건 표가 아니다")
check("구분선 없으면 표가 아니다", "<table>" not in h, True)

h = md_to_html("```\n| a | b |\n# 제목\n```")
check("코드 블록 안은 파싱하지 않는다", "<table>" not in h and "<h1>" not in h, True)
check("코드 블록은 pre", "<pre><code>" in h, True)

h = md_to_html("# 제목1\n## 제목2\n###### 제목6")
check("제목 레벨", "<h1>제목1</h1>" in h and "<h6>제목6</h6>" in h, True)

h = md_to_html("- 하나\n- 둘\n\n본문")
check("목록 닫힘", h.count("<ul>") == 1 and h.count("</ul>") == 1, True)

h = md_to_html("1. 하나\n2. 둘")
check("번호 목록", "<ol>" in h and h.count("<li>") == 2, True)

h = md_to_html("- 글머리\n\n1. 번호")
check("목록 종류가 바뀌면 닫고 새로 연다", "</ul>" in h and "<ol>" in h, True)

h = md_to_html("> 경고다\n> 두 줄")
check("인용", "<blockquote>" in h and "경고다 두 줄" in h.replace("\n", " "), True)

h = md_to_html("첫 줄\n이어지는 줄\n\n다음 단락")
check("빈 줄까지 한 단락", h.count("<p>") == 2, True)

h = md_to_html("---")
check("수평선", "<hr>" in h, True)

h = md_to_html("| | |\n|---|---|\n| a | b |")
check("빈 머리글은 thead를 만들지 않는다", "<thead>" not in h and "<td>a</td>" in h, True)

h = md_to_html("| 값 | |\n|---|---|\n| a | b |")
check("한 칸만 차 있으면 thead를 만든다", "<thead>" in h, True)

check("이미지", inline("![캡션](a.png)"), '<img src="a.png" alt="캡션">')
check(
    "이미지가 링크보다 먼저다",
    inline("![](x.png)"),
    '<img src="x.png" alt="">',
)
h = md_to_html("| 하나 |\n|---|\n| ![](a.png) |")
check("이미지 표는 shots 판", 'class="shots"' in h, True)
h = md_to_html("| 하나 |\n|---|\n| 글자 |")
check("글자만 있는 표는 shots가 아니다", 'class="shots"' not in h, True)

h = md_to_html("본문\n| a | b |\n|---|---|\n| 1 | 2 |")
check("단락 뒤 표가 단락에 먹히지 않는다", "<table>" in h and "<p>본문</p>" in h, True)

# ── 실제 제출 문서로 왕복 ─────────────────────────────────────────────
# ⚠️ **가장 중요한 검사다.** 위 단위 검사는 전부 통과하면서도 실제 문서에서
#    표가 하나도 안 만들어지는 경우가 있었다(구분선 정규식). 그래서 결과물의
#    표 개수를 원본의 구분선 개수와 맞춰 본다.
docs = Path(__file__).resolve().parents[1]
for md in sorted(docs.glob("*.md")):
    text = md.read_text(encoding="utf-8")
    body = md_to_html(text)
    lines = text.split("\n")
    # 코드 블록 밖의 표 구분선만 센다
    in_code, expected = False, 0
    for idx, ln in enumerate(lines):
        if ln.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code:
            continue
        s = ln.strip()
        if s.startswith("|") and "-" in s and set(s) <= set("|-: "):
            prev = lines[idx - 1].strip() if idx else ""
            if prev.startswith("|"):
                expected += 1
    # `<table>`이 아니라 `<table`로 센다 — 스크린샷 표는 `<table class="shots">`다.
    check(f"{md.name}: 표 개수", body.count("<table"), expected)
    check(f"{md.name}: 미처리 마크다운 굵게 남음", "**" in body, False)

if fails:
    print(f"❌ {len(fails)}개 실패\n")
    print("\n\n".join(fails))
    sys.exit(1)
print("✅ 전부 통과")
