#!/usr/bin/env python3
"""build_app_data.py 파서 테스트.

공유계약 1-2가 **랩어라운드 케이스 테스트를 넣으라고 명시**했다.
이 파싱이 틀리면 개화월 하드 필터가 틀리고, 그건 판별이 틀리는 것이다.

실행:
    python3 꽃도감/_tools/test_build_app_data.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_app_data import build, parse_bloom_months  # noqa: E402

CASES = [
    ("4월", [4]),
    ("3~4월", [3, 4]),
    ("7~9월", [7, 8, 9]),
    ("4~5월", [4, 5]),  # 23종 · 최다
    ("3~10월", [3, 4, 5, 6, 7, 8, 9, 10]),
    # 랩어라운드 — 계약이 지정한 케이스
    ("12~4월", [12, 1, 2, 3, 4]),
    ("11~2월", [11, 12, 1, 2]),  # 계약 문서의 예시
    ("12~1월", [12, 1]),
    # 공백 허용
    (" 5~6월 ", [5, 6]),
]

BAD_CASES = ["봄", "", "13~2월", "0~3월", "5~6", "3월~4월"]


def main() -> int:
    failures = []

    for text, expected in CASES:
        try:
            got = parse_bloom_months(text)
        except Exception as exc:  # noqa: BLE001
            failures.append(f"{text!r}: 예외 {exc}")
            continue
        if got != expected:
            failures.append(f"{text!r}: {got} != {expected}")

    for text in BAD_CASES:
        try:
            parse_bloom_months(text)
        except ValueError:
            pass
        else:
            failures.append(f"{text!r}: 실패해야 하는데 통과했다")

    # CSV 전체가 계약의 분포와 맞는지 — 문서 숫자와 실제가 갈리는 걸 막는다
    data = build()
    flowers = data["flowers"]
    from collections import Counter

    checks = {
        "종수": (len(flowers), 200),
        "봄": (sum(f["season"] == "spring" for f in flowers), 72),
        "여름": (sum(f["season"] == "summer" for f in flowers), 92),
        "가을": (sum(f["season"] == "autumn" for f in flowers), 30),
        "겨울": (sum(f["season"] == "winter" for f in flowers), 6),
        "흔함": (sum(f["rarity"] == "common" for f in flowers), 88),
        "보통": (sum(f["rarity"] == "normal" for f in flowers), 102),
        "귀함": (sum(f["rarity"] == "rare" for f in flowers), 10),
        "난이도 하": (sum(f["ai_difficulty"] == "low" for f in flowers), 33),
        "난이도 중": (sum(f["ai_difficulty"] == "mid" for f in flowers), 116),
        "난이도 상": (sum(f["ai_difficulty"] == "high" for f in flowers), 51),
    }
    for label, (got, expected) in checks.items():
        if got != expected:
            failures.append(f"분포 {label}: {got} != 계약 {expected}")

    # 공유계약 1-2의 월별 개화 종수 표와 일치해야 한다
    per_month = Counter()
    for f in flowers:
        for month in f["bloom_months"]:
            per_month[month] += 1
    contract_months = {
        1: 1, 2: 4, 3: 24, 4: 52, 5: 71, 6: 78,
        7: 100, 8: 98, 9: 67, 10: 36, 11: 4, 12: 2,
    }
    for month, expected in contract_months.items():
        got = per_month.get(month, 0)
        if got != expected:
            failures.append(f"{month}월 개화 종수: {got} != 계약 {expected}")

    # 참조 무결성 — id가 도감 범위 안이고 자기 자신을 가리키지 않는다
    for f in flowers:
        for target in f["similar_flower_ids"]:
            if not 1 <= target <= 200:
                failures.append(f"{f['id']}: 비슷한꽃 id {target}가 범위 밖")
            if target == f["id"]:
                failures.append(f"{f['id']}: 자기 자신을 비슷한꽃으로 가리킨다")

    if failures:
        print(f"실패 {len(failures)}건")
        for line in failures:
            print(f"  ✗ {line}")
        return 1

    print(f"통과 — 파서 {len(CASES) + len(BAD_CASES)}케이스 + 분포·월별·참조 무결성")
    return 0


if __name__ == "__main__":
    sys.exit(main())
