#!/usr/bin/env python3
"""제출물 마크다운 → PDF.

제출 요건이 PDF 형식이라 마크다운 원본을 그대로 낼 수 없다. 그런데 **PDF를 손으로
고치면 원본과 어긋난다** — 이 프로젝트는 생성물을 직접 수정하지 않는 규칙이라
변환을 스크립트로 고정한다.

    python3 제출물/_tools/build_pdf.py            # 전부
    python3 제출물/_tools/build_pdf.py 게임_소개   # 하나만

⚠️ **pandoc·wkhtmltopdf를 쓰지 않는다.** 이 맥에 없고 `brew install --cask`가
   막혀 있다(sudo 없음). 대신 **이미 있는 Chrome의 headless 인쇄**를 쓴다.

⚠️ 마크다운 파서를 직접 쓴다. 필요한 문법이 제목·표·목록·코드·인용·강조뿐이라
   의존성을 추가할 이유가 없다. **표 안의 `|` 이스케이프와 인라인 코드 안의
   `*`는 실제로 걸렸던 케이스**라 테스트가 있다(`test_build_pdf.py`).
"""

from __future__ import annotations

import html
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "제출물"

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

CSS = """
@page { size: A4; margin: 18mm 16mm 20mm; }
* { box-sizing: border-box; }
body {
  margin: 0; color: #16211b; background: #fff;
  font: 10.5pt/1.72 -apple-system, "Apple SD Gothic Neo", "Malgun Gothic", "Noto Sans KR", sans-serif;
  -webkit-print-color-adjust: exact; print-color-adjust: exact;
}
h1 {
  font-size: 21pt; margin: 0 0 4mm; padding-bottom: 3mm; letter-spacing: -.01em;
  border-bottom: 2.5pt solid #1f5c3d; color: #1f5c3d;
}
h2 {
  font-size: 14pt; margin: 9mm 0 3mm; padding-left: 3mm; letter-spacing: -.01em;
  border-left: 3.5pt solid #1f5c3d; page-break-after: avoid;
}
h3 { font-size: 11.5pt; margin: 6mm 0 2mm; color: #1f5c3d; page-break-after: avoid; }
h4 { font-size: 10.5pt; margin: 4mm 0 1.5mm; page-break-after: avoid; }
p { margin: 0 0 2.6mm; }
strong { font-weight: 700; }
a { color: #1f5c3d; text-decoration: none; word-break: break-all; }

table {
  width: 100%; border-collapse: collapse; margin: 3mm 0 4.5mm;
  font-size: 9.3pt; page-break-inside: avoid;
}
th, td {
  border: .4pt solid #cfd8d3; padding: 1.9mm 2.6mm; text-align: left; vertical-align: top;
}
th { background: #eaf2ed; font-weight: 700; color: #1f5c3d; }
tr:nth-child(even) td { background: #fafbfa; }

ul, ol { margin: 0 0 3mm; padding-left: 6mm; }
li { margin-bottom: 1.1mm; }

pre {
  background: #16211b; color: #dfe9e3; padding: 3mm 3.5mm; border-radius: 1.6mm;
  font: 8.6pt/1.55 "SF Mono", Menlo, monospace; margin: 2.5mm 0 4mm;
  white-space: pre-wrap; word-break: break-word; page-break-inside: avoid;
}
code {
  background: #eef2ef; padding: .3mm 1.1mm; border-radius: .8mm;
  font: 9pt/1.4 "SF Mono", Menlo, monospace;
  /* 🔴 **공백을 보존한다.** HTML은 연속 공백을 한 칸으로 접는다. 그래서 양식 문서의
     빈 칸(`   ` 로 적은 기입란)이 **1~2mm 조각으로 뭉개져 칸인지 알 수 없게 된다**
     — 실제로 `팀원_롤_기술서`가 그렇게 나왔다. 값이 사라지는 게 아니라 **폭만
     줄어들어서** PDF를 봐도 "왜 이렇게 좁지" 정도로만 보인다. */
  white-space: pre-wrap;
}
pre code { background: none; padding: 0; color: inherit; font-size: inherit; }

blockquote {
  margin: 2.5mm 0 4mm; padding: 2.6mm 3.5mm;
  background: #fff7ef; border-left: 2.5pt solid #b4550f; font-size: 9.8pt;
}
blockquote p:last-child { margin-bottom: 0; }

hr { border: 0; border-top: .5pt solid #dde4e0; margin: 6mm 0; }

/* 화면 스크린샷 — 표 한 칸에 하나씩 넣어 4장을 한 줄로 늘어놓는다.
   ⚠️ 폭을 100%로 두면 A4에서 한 장이 반 페이지를 먹는다. */
table.shots { border: 0; }
table.shots th, table.shots td { border: 0; background: none !important; text-align: center; }
img { max-width: 100%; }
table.shots img {
  width: 100%; max-width: 38mm; border: .4pt solid #cfd8d3; border-radius: 1.2mm;
}
table.shots td { font-size: 8.2pt; line-height: 1.45; color: #4a544e; padding: 1mm 1.4mm 3mm; }
table.shots td b { display: block; color: #16211b; font-size: 8.8pt; margin: 1.2mm 0 .5mm; }
"""


# ── 인라인 ────────────────────────────────────────────────────────────
#: 문서에서 그대로 쓰도록 허용한 인라인 HTML. 표 한 칸 안에서 줄을 바꾸는 방법이
#: 마크다운에 없어서 필요하다. ⚠️ **화이트리스트다** — 이스케이프를 통째로 끄면
#: 문서에 적은 `<script>`가 실행된다.
ALLOWED_TAGS = ("b", "br", "small")


def inline(text: str) -> str:
    """굵게·기울임·인라인 코드·링크·이미지. **코드 안은 다른 문법을 적용하지 않는다.**"""
    # 인라인 코드를 먼저 떼어 자리표시자로 바꾼다. 이걸 안 하면 `*args*` 같은 코드가
    # 기울임으로 먹히고, 코드 안의 `[a](b)`가 링크가 된다.
    codes: list[str] = []

    def stash(m: re.Match[str]) -> str:
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", stash, text)
    text = html.escape(text, quote=False)
    for tag in ALLOWED_TAGS:
        text = text.replace(f"&lt;{tag}&gt;", f"<{tag}>").replace(f"&lt;/{tag}&gt;", f"</{tag}>")
    # 이미지가 링크보다 먼저다 — `![a](b)`를 링크 규칙이 먹으면 `!<a>`가 된다.
    text = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", r'<img src="\2" alt="\1">', text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    # ⚠️ 안쪽이 공백으로 시작·끝나면 기울임이 아니다. 이걸 안 막으면
    #    `2 * 3 * 4`(곱셈)가 `2 <em> 3 </em> 4`가 된다 — 숫자 문서에서 실제로 걸렸다.
    text = re.sub(r"(?<![\w*])\*(?!\s)([^*\n]*[^\s*])\*(?![\w*])", r"<em>\1</em>", text)
    return re.sub(
        r"\x00(\d+)\x00",
        lambda m: f"<code>{html.escape(codes[int(m.group(1))], quote=False)}</code>",
        text,
    )


def split_row(line: str) -> list[str]:
    """표 한 줄 → 칸.

    칸 구분이 **아닌** `|` 두 가지를 지킨다:
    - `\\|` (이스케이프)
    - 백틱 안의 `|` — 표에서 `` `|` `` 를 설명하려면 필요하다
    """
    body = line.strip()
    if body.startswith("|"):
        body = body[1:]
    if body.endswith("|") and not body.endswith("\\|"):
        body = body[:-1]
    cells, cur = [], ""
    in_code = False
    i = 0
    while i < len(body):
        ch = body[i]
        if ch == "\\" and i + 1 < len(body) and body[i + 1] == "|":
            cur += "|"
            i += 2
            continue
        if ch == "`":
            in_code = not in_code
            cur += ch
            i += 1
            continue
        if ch == "|" and not in_code:
            cells.append(cur.strip())
            cur = ""
            i += 1
            continue
        cur += ch
        i += 1
    cells.append(cur.strip())
    return cells


def is_sep(line: str) -> bool:
    return bool(re.fullmatch(r"\|?[\s:\-|]+\|?", line.strip())) and "-" in line


# ── 블록 ──────────────────────────────────────────────────────────────
def md_to_html(md: str) -> str:
    lines = md.replace("\r\n", "\n").split("\n")
    out: list[str] = []
    i, n = 0, len(lines)
    list_stack: list[str] = []

    def close_lists() -> None:
        while list_stack:
            out.append(f"</{list_stack.pop()}>")

    while i < n:
        line = lines[i]
        stripped = line.strip()

        # 코드 블록
        if stripped.startswith("```"):
            close_lists()
            i += 1
            buf: list[str] = []
            while i < n and not lines[i].strip().startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            body = html.escape("\n".join(buf), quote=False)
            out.append(f"<pre><code>{body}</code></pre>")
            continue

        # 표: 헤더 + 구분선이 붙어 있을 때만
        if stripped.startswith("|") and i + 1 < n and is_sep(lines[i + 1]):
            close_lists()
            head = split_row(stripped)
            i += 2
            rows: list[list[str]] = []
            while i < n and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i].strip()))
                i += 1
            # 이미지가 든 표는 스크린샷 판으로 그린다(테두리·배경 없이).
            has_img = any("![" in c for r in rows + [head] for c in r)
            out.append('<table class="shots">' if has_img else "<table>")
            # 머리글이 전부 비어 있으면 **머리 줄을 만들지 않는다.** `| | |` 형태를
            # 라벨-값 표로 쓰는 곳이 많은데, 빈 초록 띠가 한 줄 남으면 표가 잘려 보인다.
            if any(c for c in head):
                out.append("<thead><tr>")
                out += [f"<th>{inline(c)}</th>" for c in head]
                out.append("</tr></thead>")
            out.append("<tbody>")
            for r in rows:
                r = (r + [""] * len(head))[: len(head)]
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            out.append("</tbody></table>")
            continue

        if not stripped:
            close_lists()
            i += 1
            continue

        if re.fullmatch(r"-{3,}|_{3,}|\*{3,}", stripped):
            close_lists()
            out.append("<hr>")
            i += 1
            continue

        m = re.match(r"(#{1,6})\s+(.*)", stripped)
        if m:
            close_lists()
            lv = len(m.group(1))
            out.append(f"<h{lv}>{inline(m.group(2))}</h{lv}>")
            i += 1
            continue

        if stripped.startswith(">"):
            close_lists()
            buf = []
            while i < n and lines[i].strip().startswith(">"):
                buf.append(lines[i].strip()[1:].strip())
                i += 1
            out.append("<blockquote>")
            for para in "\n".join(buf).split("\n\n"):
                if para.strip():
                    out.append(f"<p>{inline(para.strip())}</p>")
            out.append("</blockquote>")
            continue

        m = re.match(r"([-*+])\s+(.*)", stripped)
        if m:
            if not list_stack or list_stack[-1] != "ul":
                close_lists()
                out.append("<ul>")
                list_stack.append("ul")
            out.append(f"<li>{inline(m.group(2))}</li>")
            i += 1
            continue

        m = re.match(r"(\d+)[.)]\s+(.*)", stripped)
        if m:
            if not list_stack or list_stack[-1] != "ol":
                close_lists()
                out.append("<ol>")
                list_stack.append("ol")
            out.append(f"<li>{inline(m.group(2))}</li>")
            i += 1
            continue

        # 본문 — 빈 줄까지 이어 한 단락으로 (줄바꿈은 공백)
        #
        # 🔴 **첫 줄은 조건 없이 삼킨다.** 여기 오는 줄이 아래 while 조건에 걸리는
        #    경우가 있다 — 구분선 없는 `| a |` 는 표 분기를 못 타고 여기로 내려오는데
        #    `\|`가 조건에 있어서 buf가 비고 **i가 안 늘어 무한 루프가 된다**
        #    (실제로 테스트가 걸려서 죽였다). 진행이 조건에 의존하면 안 된다.
        close_lists()
        buf = [line.strip()]
        i += 1
        while i < n and lines[i].strip() and not re.match(
            r"(#{1,6}\s|```|>|\||[-*+]\s|\d+[.)]\s)", lines[i].strip()
        ):
            buf.append(lines[i].strip())
            i += 1
        out.append(f"<p>{inline(' '.join(buf))}</p>")

    close_lists()
    return "\n".join(out)


def build(name: str) -> Path:
    src = OUT_DIR / f"{name}.md"
    if not src.exists():
        raise SystemExit(f"없는 파일: {src}")

    md = src.read_text(encoding="utf-8")

    # 🔴 **이미지 경로를 먼저 확인한다.** 경로가 틀려도 Chrome은 PDF를 정상 생성하고
    #    그 칸만 빈다 — "빠진 것"은 결과물을 봐도 눈에 안 띈다.
    missing = [p for p in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", md)
               if not p.startswith(("http://", "https://")) and not (OUT_DIR / p).exists()]
    if missing:
        raise SystemExit(f"이미지가 없다({len(missing)}개): {missing}")

    title = re.sub(r"[_·]", " ", name)
    page = (
        "<!DOCTYPE html><html lang='ko'><head><meta charset='utf-8'>"
        f"<title>{html.escape(title)}</title><style>{CSS}</style></head><body>"
        f"{md_to_html(md)}</body></html>"
    )

    # 🔴 **중간 HTML을 마크다운과 같은 폴더에 둔다.** 임시 폴더에 두면
    #    `![](화면/dex.png)` 같은 상대 경로가 전부 깨지는데, **PDF는 정상적으로
    #    생성되고 이미지 자리만 조용히 빈다**(오류도 안 난다). 실제로 한 번 그렇게 나왔다.
    tmp_html = OUT_DIR / f".{name}.build.html"
    tmp_html.write_text(page, encoding="utf-8")

    pdf = OUT_DIR / f"{name}.pdf"
    if not Path(CHROME).exists():
        raise SystemExit(f"Chrome이 없다: {CHROME}")

    # ⚠️ `--print-to-pdf`는 성공해도 종료 코드가 0이 아닐 수 있고, 실패해도
    #    조용할 수 있다. **파일 존재와 크기로 판정한다.**
    before = pdf.stat().st_size if pdf.exists() else -1
    subprocess.run(
        [
            CHROME, "--headless", "--disable-gpu", "--no-pdf-header-footer",
            f"--print-to-pdf={pdf}", tmp_html.as_uri(),
        ],
        capture_output=True,
        timeout=120,
    )
    if not pdf.exists() or pdf.stat().st_size < 5_000:
        raise SystemExit(f"PDF 생성 실패: {pdf} (before={before})")
    tmp_html.unlink(missing_ok=True)
    print(f"✅ {pdf.relative_to(ROOT)}  {pdf.stat().st_size:,} bytes")
    return pdf


#: 인자 없이 돌리면 만드는 목록. 앞의 3개가 제출물이고 `플레이영상_콘티`는 오너 작업지시서다
#: — 여기 넣어 두는 이유는 **빼 두면 원본을 고쳐도 PDF가 조용히 낡기** 때문이다.
DOCS = ["게임_소개", "AI_활용_기술문서", "팀원_롤_기술서", "플레이영상_콘티"]

if __name__ == "__main__":
    targets = sys.argv[1:] or DOCS
    for t in targets:
        build(t)
