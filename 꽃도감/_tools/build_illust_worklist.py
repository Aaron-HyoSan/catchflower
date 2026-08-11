#!/usr/bin/env python3
"""`꽃도감/일러스트_파일명_목록.csv` 를 만든다 — **디자이너에게 주는 발주 목록**이다.

종 2,057개마다 "이 번호로 이 파일명을 주면 된다"를 한 줄씩 적는다.

🔴 **왜 이 스크립트가 뒤늦게 생겼나.** 이 CSV는 원래 생성기 없이 손으로 만들어져
   있었고, 그래서 **`상태` 칸이 낡아 있었다** — 200장은 이미 4자리로 개명했는데
   `납품됨(4자리로 개명 필요)`이라고 적혀 있었다. 목록은 **납품 폴더의 현재
   상태**를 말하는 문서라서, 폴더가 바뀌면 목록도 다시 나와야 한다.
   (`캐치플라워 산출물은 생성물` 규칙 — 결과 파일을 직접 고치지 않는다.)

🔴 **번호의 원본은 `꽃목록_확장_2057종.csv` 다.** `꽃목록_후보_2000종.csv` 를
   쓰면 안 된다 — 그 파일은 행이 2,057개지만 `도감번호`가 채워진 행이 **200개뿐**
   이고 신규 1,857종은 **빈칸**이다(세어서 확인). 그걸 보고 발주하면 신규종
   번호를 아예 만들 수 없다.

⚠️ **납품 여부는 번호로만 판정한다.** 파일명의 한글은 macOS에서 NFD로 저장되고
   CSV의 이름은 NFC라, 이름으로 맞추면 **200종 전부 불일치하는데 눈으로는
   똑같이 보인다**(규격 문서 2절 실측).

사용법:
    python3 꽃도감/_tools/build_illust_worklist.py            # 다시 만든다
    python3 꽃도감/_tools/build_illust_worklist.py --check     # 안 쓰고 비교만
"""

import csv
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEX = os.path.dirname(HERE)

MASTER = os.path.join(DEX, "꽃목록_확장_2057종.csv")
DELIVERED_DIR = os.path.join(DEX, "꽃도감_일러스트")
OUT = os.path.join(DEX, "일러스트_파일명_목록.csv")

HEADER = ["파일명", "도감번호", "이름", "학명", "과", "개화기_초안", "희귀도_초안", "상태"]

# 빌드(`SyncSharedAssets`)가 받는 형식과 **같은 정규식이어야 한다.**
# 3자리로 와도 빌드가 4자리로 정규화해 복사하므로 여기서도 3자리를 납품으로 센다.
DELIVERED_RE = re.compile(r"^(\d{3,4})_.*\.png$", re.IGNORECASE)


def delivered_numbers(folder):
    """납품 폴더에 실제로 들어 있는 도감번호 집합."""
    if not os.path.isdir(folder):
        return set()
    found = set()
    for name in os.listdir(folder):
        m = DELIVERED_RE.match(name)
        if m:
            found.add(int(m.group(1)))
    return found


def build_rows():
    with open(MASTER, encoding="utf-8-sig", newline="") as f:
        master = list(csv.DictReader(f))

    # 🔴 원본을 `꽃목록_후보_2000종.csv` 로 잘못 짚으면 여기서 걸린다.
    #    그 파일은 행이 2,057개인데 `도감번호`는 200개만 채워져 있어서, 안 막으면
    #    `int('')`가 터지고 **원인이 "빈칸"이라는 게 메시지에 안 나온다.**
    blank = sum(1 for r in master if not r["도감번호"].strip())
    assert blank == 0, (
        f"{os.path.basename(MASTER)} 에 도감번호가 빈 행이 {blank}개다 — 원본 파일을 잘못 짚었다.\n"
        "  번호가 2,057행 전부 채워진 파일을 써야 한다. 후보 목록 쪽은 200개만 채워져 있다.\n"
        f"  확인: python3 -c \"import csv;rows=list(csv.DictReader(open('{MASTER}',encoding='utf-8-sig')));"
        "print(sum(1 for r in rows if r['도감번호'].strip()),'/',len(rows))\""
    )

    have = delivered_numbers(DELIVERED_DIR)
    rows = []
    for r in master:
        num = int(r["도감번호"])
        rows.append({
            "파일명": f"{num:04d}_{r['이름']}.png",
            "도감번호": str(num),
            "이름": r["이름"],
            "학명": r["학명"],
            "과": r["과"],
            "개화기_초안": r["개화기_초안"],
            "희귀도_초안": r["희귀도_초안"],
            "상태": "납품됨" if num in have else "미납품",
        })
    rows.sort(key=lambda r: int(r["도감번호"]))

    # 🔴 번호가 곧 자산 키다. 겹치거나 빠지면 **다른 꽃의 그림이 들어가는데
    #    화면에서는 "아트가 안 온 종"과 구별되지 않는다.**
    nums = [int(r["도감번호"]) for r in rows]
    assert len(set(nums)) == len(nums), "도감번호가 중복이다"
    assert nums == list(range(1, len(nums) + 1)), f"도감번호가 1~{len(nums)} 연속이 아니다"
    for r in rows:
        m = re.match(r"^(\d{4})_", r["파일명"])
        assert m and int(m.group(1)) == int(r["도감번호"]), f"파일명 번호가 도감번호와 다르다: {r['파일명']}"
    return rows


def render(rows):
    import io
    buf = io.StringIO(newline="")
    w = csv.DictWriter(buf, fieldnames=HEADER, lineterminator="\r\n")
    w.writeheader()
    w.writerows(rows)
    return buf.getvalue()


def main():
    rows = build_rows()
    text = render(rows)
    delivered = sum(1 for r in rows if r["상태"] == "납품됨")

    if "--check" in sys.argv:
        old = open(OUT, encoding="utf-8-sig", newline="").read() if os.path.exists(OUT) else ""
        same = old == text
        print(f"{'✅ 같다' if same else '🔴 다르다 — 다시 만들어야 한다'}  ({OUT})")
        print(f"   {len(rows)}종 · 납품됨 {delivered} · 미납품 {len(rows) - delivered}")
        return 0 if same else 1

    with open(OUT, "w", encoding="utf-8-sig", newline="") as f:
        f.write(text)
    print(f"✅ {OUT}")
    print(f"   {len(rows)}종 · 납품됨 {delivered} · 미납품 {len(rows) - delivered}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
