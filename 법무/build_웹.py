#!/usr/bin/env python3
"""`법무/*.txt`를 **웹 페이지로 굽는다**. 출력은 `docs/legal/` — 그대로 공개된다.

왜 필요한가 — Play 심사가 **URL 두 개**를 요구한다
──────────────────────────────────────────────
① **개인정보처리방침 URL** — 앱 안에 있는 것으로는 안 된다. 심사자가 브라우저로
   열어 보고, 스토어 등록정보에도 그 주소를 적는다.
② **계정 삭제 URL** — 계정을 만드는 앱은 **앱을 설치하지 않고도** 삭제를 요청할 수
   있는 웹 페이지를 내야 한다(Play `데이터 삭제` 정책). 그래서 `delete-account.html`을
   같이 굽는다.

🔴 **문서 본문을 여기 베껴 쓰지 않는다.** 원본은 `법무/*.txt` 하나이고 앱은 빌드가
   복사해서(`SyncSharedAssets`) 읽는다. 웹도 같은 파일에서 굽는다 — 세 벌이 되면
   갈라지고, 갈라진 쪽은 아무도 모른다(앱 화면 20-3 · 웹 · 심사자가 보는 것).

🔴 **줄바꿈을 다시 계산하지 않는다.** 앱은 `LegalDocs.reflow`가 접힌 줄을 붙이는데,
   그 규칙을 파이썬으로 한 번 더 구현하면 **두 벌이 되고 반드시 갈라진다.**
   웹은 대신 `white-space: pre-wrap` + 본문 폭 제한으로 **원본의 줄을 그대로 살린다.**
   ⚠️ 그래서 좁은 화면에서는 한 줄이 두 줄로 접힌다 — 문서가 깨진 것이 아니다.

⚠️ **`docs/legal/`은 생성물이다. 직접 고치지 마라.** 고치면 다음 실행에서 사라지고,
   그 사이에 웹만 다른 문장을 보여 준다.

어디로 나가나 — **`docs/`가 곧 공개 사이트다**
──────────────────────────────────────────
GitHub Pages가 `main` 브랜치의 `/docs`를 그대로 서비스한다(실측:
`gh api repos/Aaron-HyoSan/catchflower/pages` → `source: {branch: main, path: /docs}`).
그래서 **여기 굽고 커밋·푸시하면 그게 심사에 내는 주소다.** 따로 복사하는 단계를
두지 않았다 — 복사는 두 벌을 만들고, 낡은 쪽이 공개된 쪽이 된다.

    https://aaron-hyosan.github.io/catchflower/legal/privacy.html        ← 처방침 URL
    https://aaron-hyosan.github.io/catchflower/legal/delete-account.html ← 계정 삭제 URL

🔴 **초안은 `docs/`로 나가지 않는다.** `--allow-placeholders`는 `법무/웹_초안/`에
   쓴다. 같은 자리에 쓰게 두면 `{{운영자}}`가 박힌 페이지를 **커밋 한 번으로 공개**해
   버리고, 그건 심사 반려 사유다(A 문서 4절 24번). 모양만 보려면 초안 폴더를 브라우저로 연다.

    python3 법무/build_웹.py                      # 빈 칸이 있으면 **멈춘다** → docs/legal/
    python3 법무/build_웹.py --allow-placeholders  # 초안(빨간 띠) → 법무/웹_초안/
    python3 법무/build_웹.py --contact help@example.com
"""

import argparse
import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LEGAL = ROOT / "법무"
#: 완성본. 여기 있는 것이 **공개된 것이다**(위 주석의 Pages 설정).
OUT = ROOT / "docs" / "legal"
#: 초안. 🔴 `docs/` 밖이어야 한다 — 안에 두면 커밋이 곧 공개다.
DRAFT_OUT = LEGAL / "웹_초안"

CONTACT_PLACEHOLDER = "{{문의_이메일}}"
PLACEHOLDER = re.compile(r"\{\{[^}]+}}")

# (원본, 출력, 제목) — 🔴 `LegalDoc` enum · `build.gradle.kts`의 legalPairs와 **같은 짝이다.**
#     세 곳이 어긋나면 앱과 웹이 다른 문서를 보여 준다. 아래 [검사]가 그것을 본다.
DOCS = [
    ("개인정보_처리방침.txt", "privacy.html", "개인정보 처리방침"),
    ("서비스_이용약관.txt", "terms.html", "이용약관"),
    ("위치기반서비스_이용약관.txt", "location.html", "위치기반서비스 이용약관"),
]

CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body {
  margin: 0; padding: 24px 20px 64px;
  font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo",
               "Malgun Gothic", "Noto Sans KR", sans-serif;
  line-height: 1.7; color: #1b1b1b; background: #fff;
}
main { max-width: 40em; margin: 0 auto; }
h1 { font-size: 1.35rem; margin: 0 0 4px; }
.sub { color: #6b6b6b; font-size: .85rem; margin: 0 0 24px; }
/* 🔴 원본의 손 줄바꿈을 그대로 살린다(위 주석). 좁은 화면에서는 한 번 더 접힌다. */
.doc { white-space: pre-wrap; word-break: break-word; font-size: .95rem; }
nav ul { padding-left: 1.2em; }
a { color: #1c6b3a; }
.draft {
  background: #b3261e; color: #fff; padding: 12px 16px; border-radius: 8px;
  margin: 0 auto 24px; max-width: 40em; font-weight: 700;
}
.steps { background: #f2f6f3; border-radius: 8px; padding: 16px 20px; }
footer { max-width: 40em; margin: 40px auto 0; color: #6b6b6b; font-size: .8rem; }
@media (prefers-color-scheme: dark) {
  body { color: #ececec; background: #121212; }
  .sub, footer { color: #a5a5a5; }
  a { color: #7fd6a2; }
  .steps { background: #1e2620; }
}
"""

DRAFT_BANNER = (
    '<div class="draft">초안입니다 — 아직 채우지 않은 항목이 있어 공개용이 아닙니다. '
    "(빈 칸: {left})</div>"
)


def page(title: str, body: str, *, draft: str = "", nav: bool = True) -> str:
    banner = DRAFT_BANNER.format(left=html.escape(draft)) if draft else ""
    home = (
        '<footer><a href="./">캐치플라워 문서 목록</a></footer>' if nav else "<footer></footer>"
    )
    return f"""<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} · 캐치플라워</title>
<style>{CSS}</style>
</head>
<body>
{banner}
<main>
{body}
</main>
{home}
</body>
</html>
"""


def section_six(privacy_raw: str) -> str:
    """처방침 **6항(보유 기간과 삭제)** 을 그대로 잘라 온다.

    🔴 삭제 안내 페이지에 같은 내용을 다시 쓰지 않는다. 다시 쓰면 처방침을 고친 날
       이 페이지가 낡고, **웹 두 페이지가 서로 다른 말을 한다.**
    """
    start = privacy_raw.find("6. 보유 기간과 삭제")
    end = privacy_raw.find("7. 이용자의 권리")
    if start < 0 or end <= start:
        sys.exit(
            "🔴 처방침에서 6항을 못 찾았다. 항 번호나 제목이 바뀌었으면 이 스크립트를 고친다 — "
            "건너뛰게 만들면 삭제 안내가 조용히 빈 페이지가 된다."
        )
    return privacy_raw[start:end].rstrip()


def build(contact: str, allow: bool) -> int:
    raws = {}
    for src, _, _ in DOCS:
        f = LEGAL / src
        if not f.is_file():
            sys.exit(f"🔴 원본이 없다: {f}")
        raws[src] = f.read_text(encoding="utf-8")

    # 🔴 **먼저 다 만들고, 만든 것에서 빈 칸을 센 다음에 쓴다.** 원본만 세면
    #    이 스크립트가 직접 넣는 칸(삭제 페이지의 `{{운영자}}`)을 못 본다 —
    #    처음에 그렇게 썼고, 원본을 다 채워도 페이지에 `{{운영자}}`가 남았다.
    pages: dict[str, tuple[str, str]] = {}  # 파일명 → (제목, 본문)

    for src, out_name, title in DOCS:
        raw = raws[src]
        if contact:
            raw = raw.replace(CONTACT_PLACEHOLDER, contact)
        pages[out_name] = (
            title,
            f"<h1>{html.escape(title)}</h1>\n"
            f'<p class="sub">캐치플라워 (Catchflower)</p>\n'
            f'<div class="doc">{html.escape(raw)}</div>',
        )

    # ── 계정 삭제 안내 (Play 데이터 삭제 URL) ─────────────────────────
    six = section_six(raws["개인정보_처리방침.txt"])
    if contact:
        six = six.replace(CONTACT_PLACEHOLDER, contact)
    mail = (
        f'<a href="mailto:{html.escape(contact)}">{html.escape(contact)}</a>'
        if contact
        else f"<b>{html.escape(CONTACT_PLACEHOLDER)}</b>"
    )
    delete_body = f"""<h1>계정 및 데이터 삭제</h1>
<p class="sub">캐치플라워 (Catchflower) · 개발자: {{{{운영자}}}}</p>

<h2>앱에서 바로 삭제하기</h2>
<div class="steps">
<ol>
<li>캐치플라워 앱을 엽니다.</li>
<li>아래 탭에서 <b>마이</b> → 오른쪽 위 <b>설정</b>을 누릅니다.</li>
<li>맨 아래 <b>회원 탈퇴</b>를 누릅니다.</li>
<li>안내를 확인하고 한 번 더 누르면 삭제가 끝납니다. (되돌릴 수 없습니다.)</li>
</ol>
</div>

<h2>앱 없이 요청하기</h2>
<p>앱을 이미 지웠거나 앱을 쓸 수 없는 경우, {mail} 으로
<b>제목에 "계정 삭제 요청"</b>이라고 적어 보내 주세요. 계정을 확인할 수 있는 정보
(앱에서 쓰던 닉네임, 가입에 사용한 카카오 계정)를 함께 알려 주시면
본인 확인 후 처리합니다. 접수 후 <b>영업일 기준 7일 이내</b>에 처리하고 회신합니다.</p>

<h2>무엇이 지워지고 무엇이 남나</h2>
<p>아래는 개인정보 처리방침 6항과 <b>같은 내용</b>입니다.</p>
<div class="doc">{html.escape(six)}</div>

<footer style="margin-top:32px">
<a href="./privacy.html">개인정보 처리방침</a> ·
<a href="./terms.html">이용약관</a>
</footer>"""
    if contact:
        delete_body = delete_body.replace(CONTACT_PLACEHOLDER, contact)
    pages["delete-account.html"] = ("계정 및 데이터 삭제", delete_body)

    # ── 목록 ────────────────────────────────────────────────────────
    links = "\n".join(
        f'<li><a href="./{out_name}">{html.escape(title)}</a></li>'
        for _, out_name, title in DOCS
    )
    pages["index.html"] = (
        "문서",
        f"""<h1>캐치플라워 문서</h1>
<p class="sub">꽃을 찍어 모으는 도감 앱</p>
<nav><ul>
{links}
<li><a href="./delete-account.html">계정 및 데이터 삭제</a></li>
</ul></nav>""",
    )

    # ── 빈 칸 검사 (릴리스 빌드의 `copyLegalDocs`와 같은 판정) ──────────
    left: dict[str, list[str]] = {}
    for name, (_, body) in pages.items():
        found = sorted({m.group(0) for m in PLACEHOLDER.finditer(body)})
        if found:
            left[name] = found
    if left and not allow:
        print("🔴 채우지 않은 칸이 남아서 웹 페이지를 굽지 않았다(아무 파일도 안 썼다):")
        for name, ph in left.items():
            print(f"   {name}: {' '.join(ph)}")
        print()
        print("   오너에게 받을 값이다(A 문서 4절 24번). 모양만 보려면 --allow-placeholders")
        return 1

    draft = " ".join(sorted({p for ph in left.values() for p in ph}))

    # 🔴 초안은 공개 폴더로 나가지 않는다. 여기서 갈라 놓는 것이 유일한 방어다 —
    #    같은 폴더에 쓰면 `git add docs` 한 번으로 `{{운영자}}`가 박힌 페이지가 공개된다.
    out = DRAFT_OUT if draft else OUT
    if draft and (ROOT / "docs") in out.parents:
        sys.exit(f"🔴 초안 출력 폴더가 docs/ 안이다({out}) — 공개돼 버린다. DRAFT_OUT을 고쳐라")

    out.mkdir(parents=True, exist_ok=True)
    written = []
    for name, (title, body) in pages.items():
        (out / name).write_text(
            page(title, body, draft=draft, nav=(name != "index.html")), encoding="utf-8"
        )
        written.append(name)

    print(f"🔵 {len(written)}개 파일을 구웠다 → {out.relative_to(ROOT)}/")
    for w in written:
        print(f"   {w}  ({(out / w).stat().st_size:,}바이트)")
    if draft:
        print()
        print(f"⚠️ **초안이다.** 빨간 띠가 붙어 있다 — 채울 칸: {draft}")
        print("   🔴 이건 공개 폴더가 아니다. 값을 채우고 플래그 없이 다시 구워야")
        print("      `docs/legal/`에 들어가고, 커밋·푸시하면 그때 공개된다.")
    else:
        print()
        print("   공개 주소(커밋·푸시 뒤):")
        print("   https://aaron-hyosan.github.io/catchflower/legal/privacy.html")
        print("   https://aaron-hyosan.github.io/catchflower/legal/delete-account.html")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--contact",
        default="",
        help="문의 이메일. 비우면 android/local.properties의 CONTACT_EMAIL을 읽는다",
    )
    ap.add_argument("--allow-placeholders", action="store_true")
    args = ap.parse_args()

    contact = args.contact.strip()
    if not contact:
        # 🔴 주소의 원본은 `local.properties` 하나다(앱의 고객문의가 그 값을 쓴다).
        #    여기 기본값을 적으면 두 곳이 되고, 바꾼 날 한쪽이 낡는다.
        lp = ROOT / "android" / "local.properties"
        if lp.is_file():
            for line in lp.read_text(encoding="utf-8").splitlines():
                if line.strip().startswith("CONTACT_EMAIL="):
                    contact = line.split("=", 1)[1].strip()
    return build(contact, args.allow_placeholders)


if __name__ == "__main__":
    sys.exit(main())
