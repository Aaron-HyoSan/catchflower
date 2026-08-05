# -*- coding: utf-8 -*-
"""꽃목록_200종.csv → flowers.json 적재 스크립트 (iOS·Android 공용).

**이 파일이 존재하는 이유는 하나다.**
공유계약 1-2절: 개화기 `"3~4월"` 문자열 파싱은 **여기서 단 한 번만** 한다.
클라이언트는 `bloom_months`를 `int[]`로만 받는다. 양쪽이 각자 파싱하면
iOS와 Android가 다른 후보 집합을 쓰게 되고, 그러면 개화월 하드 필터가 갈려
**판별 결과 자체가 달라진다.**

입력  ../꽃도감/꽃목록_200종.csv   (생성물. 원본은 꽃도감/_tools/flowers.py)
출력  flowers.json                 (iOS 번들 · Android assets 공용)

실행  python3 공용_적재/build_flowers_json.py
      python3 공용_적재/build_flowers_json.py --test   ← 파싱 케이스 테스트만
"""

import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(HERE, "..", "꽃도감", "꽃목록_200종.csv")
OUT_PATH = os.path.join(HERE, "flowers.json")

# 공유계약 1-4절. 문자열을 못 박는다 — 숫자로 바꾸지 않는다.
SEASON = {"봄": "spring", "여름": "summer", "가을": "autumn", "겨울": "winter"}
RARITY = {"흔함": "common", "보통": "normal", "귀함": "rare"}
AI_DIFF = {"하": "low", "중": "mid", "상": "high"}


def parse_bloom_months(s):
    """개화기 문자열 → 월 배열.

    `"4월"` → [4] · `"3~4월"` → [3, 4] · `"7~9월"` → [7, 8, 9]
    `"12~4월"` → [12, 1, 2, 3, 4]   ← 연말 랩어라운드 (동백꽃)

    랩어라운드는 실제로 CSV에 있다. 이걸 놓치면 동백꽃이 겨울에 안 잡힌다.
    """
    t = s.strip().replace("월", "").strip()
    if not t:
        raise ValueError("개화기가 비어 있다")

    if "~" in t:
        a, b = t.split("~", 1)
        start, end = int(a), int(b)
    else:
        start = end = int(t)

    for m in (start, end):
        if not 1 <= m <= 12:
            raise ValueError("월이 1~12 범위를 벗어난다: %r" % s)

    months = []
    m = start
    while True:
        months.append(m)
        if m == end:
            break
        m = 1 if m == 12 else m + 1
        if len(months) > 12:
            raise ValueError("월 순환이 끝나지 않는다: %r" % s)
    return months


def split_similar(s):
    """비슷한꽃 컬럼 → 이름 목록. `-` 와 빈 값은 없음을 뜻한다."""
    t = (s or "").strip()
    if not t or t == "-":
        return []
    return [x.strip() for x in t.split(",") if x.strip() and x.strip() != "-"]


def test_parse():
    """공유계약 1-2절이 요구한 케이스 테스트. 랩어라운드가 핵심이다."""
    cases = [
        ("3~4월", [3, 4]),
        ("4~5월", [4, 5]),
        ("7~9월", [7, 8, 9]),
        ("4월", [4]),
        ("3월", [3]),
        ("5~10월", [5, 6, 7, 8, 9, 10]),
        ("12~4월", [12, 1, 2, 3, 4]),          # 동백꽃 — 실제 데이터
        ("10~12월", [10, 11, 12]),             # 애기동백 — 실제 데이터
        ("11~2월", [11, 12, 1, 2]),            # 계약 문서에 명시된 가상 케이스
        ("12월", [12]),
        ("1~12월", [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]),
    ]
    ok = True
    for src, want in cases:
        got = parse_bloom_months(src)
        mark = "✓" if got == want else "✗"
        if got != want:
            ok = False
        print("  %s %-8s → %s" % (mark, src, got))

    for bad in ("", "13~4월", "0월", "월"):
        try:
            parse_bloom_months(bad)
            print("  ✗ %r 는 거부해야 한다" % bad)
            ok = False
        except ValueError:
            print("  ✓ %r 거부됨" % bad)

    print("파싱 테스트: %s" % ("전부 통과" if ok else "실패 있음"))
    return ok


def build():
    with open(CSV_PATH, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    # 이름 → 도감번호. 유사종 이름을 id로 바꾸는 데 쓴다 (계약 1-1).
    by_name = {r["이름"].strip(): int(r["도감번호"]) for r in rows}

    flowers = []
    problems = []
    dangling = []

    for r in rows:
        fid = int(r["도감번호"])
        name = r["이름"].strip()

        try:
            months = parse_bloom_months(r["개화기"])
        except ValueError as e:
            problems.append("%d %s 개화기: %s" % (fid, name, e))
            months = []

        for key, table, label in (
            ("계절", SEASON, "season"),
            ("희귀도", RARITY, "rarity"),
            ("AI난이도", AI_DIFF, "ai_difficulty"),
        ):
            if r[key].strip() not in table:
                problems.append("%d %s %s 값이 계약에 없다: %r" % (fid, name, label, r[key]))

        similar_ids = []
        for nm in split_similar(r["비슷한꽃"]):
            if nm in by_name:
                similar_ids.append(by_name[nm])
            else:
                # 도감에 없는 종을 가리키는 참조. 눈으로는 못 찾는다.
                dangling.append("%d %s → %s" % (fid, name, nm))

        flowers.append({
            "id": fid,
            "name": name,
            "scientific_name": r["학명"].strip(),
            "family": r["과"].strip(),
            "bloom_months": months,
            "bloom_label": r["개화기"].strip(),   # 화면 09 부연 문구용 원문
            "season": SEASON.get(r["계절"].strip(), ""),
            "color": r["대표색"].strip(),
            "rarity": RARITY.get(r["희귀도"].strip(), ""),
            "habitat": r["주요서식지"].strip(),
            "ai_difficulty": AI_DIFF.get(r["AI난이도"].strip(), ""),
            "similar_flower_ids": similar_ids,
            "illust_batch": int(r["일러스트배치"]),
        })

    flowers.sort(key=lambda x: x["id"])

    # 도감번호는 고정 ID다. 1~200 연속·중복 없음을 여기서 확인한다.
    ids = [f["id"] for f in flowers]
    if ids != list(range(1, len(flowers) + 1)):
        problems.append("도감번호가 1~%d 연속이 아니다" % len(flowers))
    if len(set(ids)) != len(ids):
        problems.append("도감번호 중복이 있다")
    for f in flowers:
        if not f["scientific_name"]:
            problems.append("%d %s 학명이 비어 있다" % (f["id"], f["name"]))
        if not f["bloom_months"]:
            problems.append("%d %s 개화월이 비어 있다" % (f["id"], f["name"]))

    return flowers, problems, dangling


def report(flowers):
    """계약에 적힌 분포와 실제가 맞는지 센다. 숫자를 손으로 쓰면 틀린다."""
    def count(key):
        c = {}
        for f in flowers:
            c[f[key]] = c.get(f[key], 0) + 1
        return c

    print("총 %d종" % len(flowers))
    print("  계절     %s" % count("season"))
    print("  희귀도   %s" % count("rarity"))
    print("  AI난이도 %s" % count("ai_difficulty"))
    print("  대표색   %s" % count("color"))

    per_month = {m: 0 for m in range(1, 13)}
    for f in flowers:
        for m in f["bloom_months"]:
            per_month[m] += 1
    print("  월별 개화 종수 %s" % [per_month[m] for m in range(1, 13)])
    return per_month


def main():
    if "--test" in sys.argv:
        sys.exit(0 if test_parse() else 1)

    print("[1/3] 개화기 파싱 케이스 테스트")
    if not test_parse():
        print("파싱이 틀렸다. 적재를 중단한다.")
        sys.exit(1)

    print("\n[2/3] CSV 적재 · 검증")
    flowers, problems, dangling = build()

    if dangling:
        # 진행.md (1)회차에서 12건을 잡아낸 그 검사다. 재발을 여기서 막는다.
        print("  ⚠️ 도감에 없는 종을 가리키는 '비슷한꽃' 참조 %d건" % len(dangling))
        for d in dangling:
            print("     - %s" % d)

    if problems:
        print("  ✗ 무결성 위반 %d건 — 적재를 중단한다" % len(problems))
        for p in problems:
            print("     - %s" % p)
        sys.exit(1)
    print("  ✓ 무결성 통과 (도감번호 연속 · 학명·개화월 빈칸 없음 · enum 값 전부 계약 범위)")

    per_month = report(flowers)

    print("\n[3/3] 출력")
    payload = {
        "schema_version": 1,
        "source": "꽃도감/꽃목록_200종.csv",
        "note": "생성물이다. 직접 고치지 않는다. 원본은 공용_적재/build_flowers_json.py",
        "count": len(flowers),
        "bloom_months_by_month": {str(m): per_month[m] for m in range(1, 13)},
        "flowers": flowers,
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("  → %s (%.1f KB)" % (OUT_PATH, os.path.getsize(OUT_PATH) / 1024))


if __name__ == "__main__":
    main()
