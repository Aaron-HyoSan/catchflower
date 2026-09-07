#!/usr/bin/env python3
"""`출시/스토어_등록정보.md`의 **붙여넣을 문장**을 읽고 검사한다.

왜 기계가 세나
──────────────
Play Console은 글자 수를 넘기면 **저장 단계에서 잘라 내거나 거부한다.** 그런데 한글은
눈으로 세기 어렵고(짧은 설명 80자는 한 줄 반쯤이다), 문구를 스토어 그래픽에도 그리기
때문에 **같은 문장이 두 곳에 있으면 반드시 갈라진다.** 그래서 문서 하나를 원본으로 두고
여기서 읽는다 — 그래픽 스크립트도 이 모듈을 불러서 같은 문장을 쓴다.

무엇을 보나
───────────
① 길이 — 제목 줄 `### 이름 · 최대 N자`의 N과 코드블록 본문을 대조한다.
② 앱 이름의 **허용 문자** — 🔴 금지어 목록을 만들지 않았다. 이 저장소가 "금지어 목록은
   원리상 못 막는다, **승인된 문구만 허용**으로 뒤집어라"를 이미 배웠다. 이름은
   한글·영문·숫자·공백과 `- · : ( )`만 통과시킨다(Play는 이모지·특수문자를 거부한다).
③ 빈 칸(`{{…}}`) — 코드블록 안에 있으면 **그대로 스토어에 붙는다**(즉시 실패).
   설명 표에 있는 것은 오너 대기 항목이고, 그것도 남아 있으면 실패로 본다
   (`--allow-placeholders`로 넘길 수 있다 — 길이만 보고 싶을 때 쓴다).
④ **종 수** — 🔴 처음 이 문서를 쓸 때 `200종`이라고 적었고, **앱은 `0 / 2044종`을
   보여 주고 있었다**(에뮬레이터 화면). 기획서의 옛 범위(`꽃목록_200종.csv`)를 그대로
   베낀 것이다. 스토어에 200이라고 적으면 **심사자가 앱을 열자마자 다른 숫자를 본다.**
   그래서 문구에 나오는 `N종`은 전부 `GamePolicy.DEX_SLOT_COUNT`(사람이 보는 분모)와
   같아야 한다 — 이 저장소의 "제출 문서 수치는 틀린다"를 기계로 막는 자리다.
⑤ URL 어긋남 — 처방침·계정삭제 URL은 `법무/build_웹.py`가 굽는 자리다.
   🔴 두 파일에 각각 적혀 있으므로 **여기서 대조한다.** 안 하면 한쪽만 고쳐도 아무도
   모르고, 스토어에 적힌 주소가 404가 된다(그건 반려 사유다).

    python3 출시/_tools/store_listing.py
    python3 출시/_tools/store_listing.py --print 그래픽_문구
    python3 출시/_tools/store_listing.py --allow-placeholders
"""

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOC = ROOT / "출시" / "스토어_등록정보.md"
WEB_BUILDER = ROOT / "법무" / "build_웹.py"
GAME_POLICY = (
    ROOT / "android/app/src/main/java/com/catchflower/app/core/GamePolicy.kt"
)
#: 문구 안의 종 수. `2,044종` · `2044 종` 둘 다 잡는다.
SPECIES = re.compile(r"([\d][\d,]*)\s*종")

HEADING = re.compile(r"^###\s+(?P<name>.+?)\s+·\s+최대\s+(?P<max>\d+)자\s*$")
FENCE = re.compile(r"^```")
PLACEHOLDER = re.compile(r"\{\{[^}]+}}")

#: 앱 이름에 **허용하는** 문자. 위 ②의 뒤집기.
NAME_OK = re.compile(r"^[0-9A-Za-z가-힣ㄱ-ㅎㅏ-ㅣ \-·:()]+$")


class Field:
    def __init__(self, name: str, limit: int, text: str, line: int):
        self.name = name
        self.limit = limit
        self.text = text
        self.line = line

    @property
    def key(self) -> str:
        return self.name.replace(" ", "_")

    def __len__(self) -> int:  # Play가 세는 것과 같은 기준: 줄바꿈도 한 글자다
        return len(self.text)


def fields(doc: pathlib.Path = DOC) -> dict[str, Field]:
    """제목 줄과 **바로 다음 코드블록**을 짝짓는다.

    🔴 짝을 못 찾으면 건너뛰지 않고 죽는다. 건너뛰게 만들면 문구 하나가 조용히 사라지고,
       그래픽은 빈 글자를 그리고 검사는 초록으로 끝난다.
    """
    if not doc.is_file():
        sys.exit(f"🔴 원본 문서가 없다: {doc}")
    lines = doc.read_text(encoding="utf-8").splitlines()
    out: dict[str, Field] = {}
    i = 0
    while i < len(lines):
        m = HEADING.match(lines[i])
        if not m:
            i += 1
            continue
        name, limit = m.group("name").strip(), int(m.group("max"))
        # 다음 코드블록을 찾는다. 그 사이에 다른 제목이 나오면 짝이 없는 것이다.
        j = i + 1
        while j < len(lines) and not FENCE.match(lines[j]):
            if lines[j].startswith("#"):
                sys.exit(f"🔴 {doc.name}:{i + 1} `{name}` 아래에 코드블록이 없다")
            j += 1
        if j >= len(lines):
            sys.exit(f"🔴 {doc.name}:{i + 1} `{name}` 아래에 코드블록이 없다")
        k = j + 1
        body: list[str] = []
        while k < len(lines) and not FENCE.match(lines[k]):
            body.append(lines[k])
            k += 1
        if k >= len(lines):
            sys.exit(f"🔴 {doc.name}:{j + 1} 코드블록이 닫히지 않았다")
        out[name] = Field(name, limit, "\n".join(body).strip("\n"), j + 2)
        i = k + 1
    if not out:
        sys.exit(f"🔴 {doc.name}에서 문구를 하나도 못 읽었다 — 제목 줄 형식이 바뀌었으면 이 파일을 고친다")
    return out


def get(name: str) -> str:
    """다른 스크립트가 문구 하나만 가져갈 때 쓴다(그래픽 생성기)."""
    fs = fields()
    key = name.replace("_", " ")
    if key not in fs:
        sys.exit(f"🔴 `{key}` 문구가 문서에 없다. 있는 것: {' / '.join(fs)}")
    return fs[key].text


def dex_slot_count() -> int:
    """앱이 화면에 쓰는 **분모**를 코틀린 소스에서 읽는다.

    🔴 `TOTAL_FLOWER_COUNT`(2057)가 아니라 `DEX_SLOT_COUNT`다. 사람이 보는 숫자가
       후자이고(유사종을 한 칸으로 묶는다), 스토어 문구는 사람이 보는 쪽을 말해야 한다.
    """
    if not GAME_POLICY.is_file():
        sys.exit(f"🔴 {GAME_POLICY}가 없다 — 종 수를 대조할 원본이 사라졌다")
    m = re.search(r"DEX_SLOT_COUNT\s*=\s*(\d+)", GAME_POLICY.read_text(encoding="utf-8"))
    if not m:
        sys.exit("🔴 `DEX_SLOT_COUNT`를 못 찾았다 — 상수 이름이 바뀌었으면 이 파일을 고친다")
    return int(m.group(1))


def web_urls() -> list[str]:
    """`법무/build_웹.py`가 안내하는 **공개 URL**을 그 파일에서 긁어 온다."""
    if not WEB_BUILDER.is_file():
        sys.exit(f"🔴 {WEB_BUILDER}가 없다 — URL을 대조할 원본이 사라졌다")
    text = WEB_BUILDER.read_text(encoding="utf-8")
    return sorted(set(re.findall(r"https://[^\s\"'`)]+/legal/[a-z\-]+\.html", text)))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--print", dest="want", help="문구 하나만 출력한다 (예: 짧은_설명)")
    ap.add_argument("--allow-placeholders", action="store_true")
    args = ap.parse_args()

    if args.want:
        print(get(args.want))
        return 0

    fs = fields()
    bad: list[str] = []

    print(f"문구 {len(fs)}개 — {DOC.relative_to(ROOT)}")
    for f in fs.values():
        over = len(f) > f.limit
        room = f.limit - len(f)
        mark = "🔴" if over else "🔵"
        head = f.text.splitlines()[0] if f.text else ""
        print(f"   {mark} {f.name}: {len(f)}/{f.limit}자 (남은 칸 {room}) · {head[:34]}…")
        if over:
            bad.append(f"{f.name}이 {len(f) - f.limit}자 넘는다 ({DOC.name}:{f.line})")
        if not f.text.strip():
            bad.append(f"{f.name}이 비어 있다")
        ph = sorted({m.group(0) for m in PLACEHOLDER.finditer(f.text)})
        if ph:
            bad.append(f"{f.name}에 빈 칸이 남았다: {' '.join(ph)} — 그대로 스토어에 붙는다")

    # ── 앱 이름: 허용 문자만 ──────────────────────────────────────
    name = fs.get("앱 이름")
    if name:
        if not NAME_OK.match(name.text):
            odd = sorted({c for c in name.text if not NAME_OK.match(c)})
            bad.append(f"앱 이름에 Play가 거부하는 문자가 있다: {' '.join(odd)}")
        if "\n" in name.text:
            bad.append("앱 이름이 두 줄이다")
    short = fs.get("짧은 설명")
    if short and "\n" in short.text:
        bad.append("짧은 설명은 한 줄이어야 한다")

    # ── 종 수 대조 (여기 ↔ GamePolicy.DEX_SLOT_COUNT) ──────────────
    slots = dex_slot_count()
    print()
    print(f"종 수 대조 — 앱이 화면에 쓰는 분모는 `DEX_SLOT_COUNT = {slots}`다")
    found = False
    for f in fs.values():
        for m in SPECIES.finditer(f.text):
            found = True
            n = int(m.group(1).replace(",", ""))
            ok = n == slots
            print(f"   {'🔵' if ok else '🔴'} {f.name}: {m.group(0)}")
            if not ok:
                bad.append(
                    f"{f.name}의 `{m.group(0)}`이 앱 화면의 `{slots}종`과 다르다 "
                    f"({DOC.name}:{f.line})"
                )
    if not found:
        print("   ⚠️ 문구에 종 수가 하나도 없다 — 이 대조는 아무것도 못 막았다")

    # ── URL 대조 (여기 ↔ 법무/build_웹.py) ─────────────────────────
    doc_text = DOC.read_text(encoding="utf-8")
    print()
    print("법적 문서 URL 대조 — `법무/build_웹.py`가 굽는 주소가 이 문서에 있나")
    for url in web_urls():
        here = url in doc_text
        print(f"   {'🔵' if here else '🔴'} {url}")
        if not here:
            bad.append(f"{url}이 등록정보 문서에 없다 — 스토어에 적을 주소가 빠졌다")

    # ── 오너 대기 빈 칸 (문서 전체) ────────────────────────────────
    # 🔴 **모든 치환자가 "빈 칸"인 것은 아니다.** `{{문의_이메일}}`은 원본이
    #    `local.properties`의 `CONTACT_EMAIL` **하나**이고 앱(`LegalDocs.render`)과
    #    웹(`build_웹.py`)이 그 값을 바꿔 넣는다. 이 문서에 값을 베껴 두면 사본이 둘이 되고,
    #    한쪽만 고친 날 **앱과 스토어가 다른 주소를 안내한다** — 그래서 여기서 읽어
    #    채워 보여 준다. 이것을 빈 칸으로 세면 채울 방법이 없어 **원리상 영원히 FAIL이다.**
    #    ⚠️ 못 읽었을 때는 다시 빈 칸으로 센다("주입돼 있다"와 "읽지 못했다"는 다른 말이다).
    resolved: dict[str, str] = {}
    lp = ROOT / "android" / "local.properties"
    if lp.is_file():
        for line in lp.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("CONTACT_EMAIL="):
                value = line.split("=", 1)[1].strip()
                if value:
                    resolved["{{문의_이메일}}"] = value

    seen = {m.group(0) for m in PLACEHOLDER.finditer(doc_text)}
    if resolved:
        print()
        print("🔵 `local.properties`가 채우는 칸 — 이 문서에 값을 베껴 두지 않는다:")
        for key, value in sorted(resolved.items()):
            자리 = "· 이 문서에는 없다" if key not in seen else ""
            print(f"   {key} = {value} {자리}".rstrip())

    left = sorted(seen - resolved.keys())
    if left:
        print()
        print(f"⏸ 아직 채우지 않은 칸 {len(left)}개 — 오너에게 받는다(8절):")
        print(f"   {' '.join(left)}")
        if not args.allow_placeholders:
            bad.append(f"빈 칸 {len(left)}개가 남아 있어 아직 제출할 수 없다")

    print()
    if bad:
        for b in bad:
            print(f"   🔴 {b}")
        print()
        print("판정: FAIL")
        return 1
    if left:
        # 🔴 여기서 그냥 PASS라고 쓰면 안 된다 — 빈 칸이 남은 채로 "붙여넣어도 된다"로
        #    읽힌다. `--allow-placeholders`는 길이만 보려고 켜는 스위치다.
        print(f"판정: 길이·대조는 통과. **아직 제출용이 아니다** — 빈 칸 {len(left)}개")
        return 0
    print("판정: PASS — 이 문서를 그대로 Console에 붙여넣을 수 있다")
    return 0


if __name__ == "__main__":
    sys.exit(main())
