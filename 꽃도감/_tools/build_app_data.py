#!/usr/bin/env python3
"""꽃목록_200종.csv → flowers.json (앱이 번들로 싣는 공유 데이터)

왜 이 스크립트가 있는가
-----------------------
공유계약 1-2가 못 박은 것 때문이다. CSV의 개화기는 `"3~4월"` 같은 **문자열**인데
이 값이 A-1 개화월 하드 필터(필수 구현)의 입력이다.
**iOS와 Android가 각자 파싱하면 후보 집합이 갈리고 판별이 갈린다.**

그래서 파싱은 여기서 단 한 번 하고, 클라이언트는 `int[]`만 받는다.
`비슷한꽃`의 이름 → id 변환도 같은 이유로 여기서 한다.

산출물 `꽃도감/flowers.json`은 **iOS·Android가 함께 쓰는 공유 자산**이다.
한쪽이 혼자 형식을 바꾸지 않는다 (공유계약 6절).

사용:
    python3 꽃도감/_tools/build_app_data.py
"""

import csv
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "꽃도감" / "꽃목록_200종.csv"
OUT_JSON = ROOT / "꽃도감" / "flowers.json"

# 클라이언트에 내려가는 enum 문자열은 공유계약 1-4가 원본이다.
# 한글 표기 → 계약 문자열. 여기 없는 값이 나오면 즉시 실패한다 (조용히 틀리는 것보다 낫다).
SEASON = {"봄": "spring", "여름": "summer", "가을": "autumn", "겨울": "winter"}
RARITY = {"흔함": "common", "보통": "normal", "귀함": "rare"}
DIFFICULTY = {"하": "low", "중": "mid", "상": "high"}


def parse_bloom_months(text: str) -> list[int]:
    """개화기 문자열 → 월 정수 배열.

    `4월`      → [4]
    `3~4월`    → [3, 4]
    `7~9월`    → [7, 8, 9]
    `12~4월`   → [12, 1, 2, 3, 4]   ← 연말 랩어라운드. CSV에 실제로 있다(동백꽃).

    공유계약 1-2가 랩어라운드 테스트를 넣으라고 명시한 케이스다.
    """
    s = text.strip().replace(" ", "")
    m = re.fullmatch(r"(\d{1,2})~(\d{1,2})월", s)
    if m:
        start, end = int(m.group(1)), int(m.group(2))
        _check_month(start, text)
        _check_month(end, text)
        if start <= end:
            return list(range(start, end + 1))
        # 랩어라운드: 12~4월 → 12,1,2,3,4
        return list(range(start, 13)) + list(range(1, end + 1))

    m = re.fullmatch(r"(\d{1,2})월", s)
    if m:
        month = int(m.group(1))
        _check_month(month, text)
        return [month]

    raise ValueError(f"개화기 표기를 해석할 수 없다: {text!r}")


def _check_month(month: int, original: str) -> None:
    if not 1 <= month <= 12:
        raise ValueError(f"월 범위를 벗어났다: {month} (원문 {original!r})")


def _split_similar(text: str) -> list[str]:
    """`비슷한꽃` 컬럼 파싱. `-` 는 "비슷한 꽃 없음"이라 빈 목록이 된다."""
    if not text or not text.strip():
        return []
    names = [name.strip() for name in text.split(",") if name.strip()]
    return [name for name in names if name != "-"]


def _enum(table: dict[str, str], value: str, column: str, flower_id: int) -> str:
    key = value.strip()
    if key not in table:
        raise ValueError(
            f"도감번호 {flower_id}: {column} 값 {key!r}이 공유계약 1-4의 enum에 없다"
        )
    return table[key]


def build() -> dict:
    with CSV_PATH.open(encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))

    if len(rows) != 200:
        raise ValueError(f"200종이어야 한다. 실제 {len(rows)}종")

    name_to_id: dict[str, int] = {}
    for row in rows:
        name = row["이름"].strip()
        flower_id = int(row["도감번호"])
        if name in name_to_id:
            raise ValueError(f"이름이 중복된다: {name}")
        name_to_id[name] = flower_id

    flowers = []
    dangling: list[tuple[int, str]] = []

    for index, row in enumerate(rows, start=1):
        flower_id = int(row["도감번호"])
        if flower_id != index:
            raise ValueError(f"도감번호가 1~200 연속이 아니다: {index}번째가 {flower_id}")

        similar_ids = []
        for similar_name in _split_similar(row["비슷한꽃"]):
            target = name_to_id.get(similar_name)
            if target is None:
                # 도감에 없는 종을 가리키는 참조. 화면 05·09의 '비슷한 꽃'에
                # 도감에 없는 이름을 링크로 띄우면 안 되므로 id 변환에서 떨어뜨리고,
                # 이름은 표시용으로 남긴다.
                dangling.append((flower_id, similar_name))
                continue
            similar_ids.append(target)

        if not row["학명"].strip():
            raise ValueError(f"도감번호 {flower_id}: 학명이 비어 있다")

        flowers.append(
            {
                "id": flower_id,
                "name": row["이름"].strip(),
                "scientific_name": row["학명"].strip(),
                "family": row["과"].strip(),
                "bloom_months": parse_bloom_months(row["개화기"]),
                "bloom_label": row["개화기"].strip(),  # 화면 09 부연 "5~6월에 피는 꽃"
                "season": _enum(SEASON, row["계절"], "계절", flower_id),
                "color": row["대표색"].strip(),
                "rarity": _enum(RARITY, row["희귀도"], "희귀도", flower_id),
                "habitat": row["주요서식지"].strip(),
                "ai_difficulty": _enum(DIFFICULTY, row["AI난이도"], "AI난이도", flower_id),
                "similar_flower_ids": similar_ids,
                "similar_flower_names": _split_similar(row["비슷한꽃"]),
                "illust_batch": int(row["일러스트배치"]),
            }
        )

    return {"flowers": flowers, "_dangling": dangling}


def report(flowers: list[dict], dangling: list[tuple[int, str]]) -> None:
    from collections import Counter

    print(f"종수: {len(flowers)}")
    print("계절:", dict(Counter(f["season"] for f in flowers)))
    print("희귀도:", dict(Counter(f["rarity"] for f in flowers)))
    print("AI난이도:", dict(Counter(f["ai_difficulty"] for f in flowers)))

    per_month = Counter()
    for f in flowers:
        for month in f["bloom_months"]:
            per_month[month] += 1
    print("월별 개화 종수:", {m: per_month.get(m, 0) for m in range(1, 13)})

    wrap = [f["name"] for f in flowers if _is_wraparound(f["bloom_months"])]
    print("연말 랩어라운드 종:", wrap or "없음")

    if dangling:
        print(f"\n⚠️ 도감에 없는 '비슷한꽃' 참조 {len(dangling)}건 (표시명으로만 남긴다):")
        for flower_id, name in dangling:
            print(f"   {flower_id:>3} → {name}")


def _is_wraparound(months: list[int]) -> bool:
    return len(months) > 1 and months[-1] < months[0]


def main() -> int:
    data = build()
    dangling = data.pop("_dangling")
    OUT_JSON.write_text(
        json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    report(data["flowers"], dangling)
    print(f"\n생성: {OUT_JSON.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
