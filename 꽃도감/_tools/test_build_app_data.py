#!/usr/bin/env python3
"""적재 파이프라인 테스트 — `공용_적재/flower_master.py` → `꽃도감/flowers.json`.

공유계약 1-2가 **랩어라운드 케이스 테스트를 넣으라고 명시**했다.
이 파싱이 틀리면 개화월 하드 필터가 틀리고, 그건 판별이 틀리는 것이다.

🔴 **이 파일은 2026-08-16까지 한 줄도 안 돌고 있었다.** 2,057종 확장 때 파싱·적재가
   `flower_master`로 옮겨졌는데 여기는 `from build_app_data import build`를 그대로 두어
   **`ImportError`로 죽었다.** 그리고 아무도 안 돌리니 **아무 증상이 없었다** —
   `python3 … | tail`의 종료코드가 0이라 "통과"로도 보였다.
   → 그래서 지금은 **모듈이 임포트되는지부터** 단정한다(0케이스를 돌고 통과하지 않게).

실행:
    python3 꽃도감/_tools/test_build_app_data.py
"""

import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "공용_적재"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import collect_groups as cg  # noqa: E402
import flower_master as fm  # noqa: E402
from build_app_data import OUT_JSON, report  # noqa: E402

parse_bloom_months = fm.parse_bloom_months

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

# 공유계약 1-2의 월별 개화 종수 표. **사람이 정한 200종에 대한 값이다** —
# 확장 1,857종은 이 표에 없다. 그래서 200종 부분집합으로 잰다.
CONTRACT_MONTHS_200 = {
    1: 1, 2: 4, 3: 24, 4: 52, 5: 71, 6: 78,
    7: 100, 8: 98, 9: 67, 10: 36, 11: 4, 12: 2,
}
CONTRACT_200 = {
    "종수": 200,
    "봄": 72, "여름": 92, "가을": 30, "겨울": 6,
    "흔함": 88, "보통": 102, "귀함": 10,
    "난이도 하": 33, "난이도 중": 116, "난이도 상": 51,
}
# 전체 2,057종의 값. 계약 1-5·구현현황 2-16의 숫자와 같아야 한다.
# 🔴 이 값들은 **드리프트 감지용**이다 — CSV나 cascade가 바뀌면 여기가 빨개지고,
#    그때 문서 숫자를 같이 고친다(문서만 낡는 것을 막는다).
TOTAL_2057 = {
    "종수": 2057,
    "난이도 하": 33, "난이도 중": 116, "난이도 상": 1908,
    "계절 없음": 278,
    "개화기 표기 없음": 1026,
    "별칭": 16,
    "도감 칸": 2044,
}


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

    flowers, stats, dangling = fm.build_master()
    human = [f for f in flowers if f["id"] <= 200]

    # ── 사람이 정한 200종 = 계약 표 ──────────────────────────────────
    got_200 = {
        "종수": len(human),
        "봄": sum(f["season"] == "spring" for f in human),
        "여름": sum(f["season"] == "summer" for f in human),
        "가을": sum(f["season"] == "autumn" for f in human),
        "겨울": sum(f["season"] == "winter" for f in human),
        "흔함": sum(f["rarity"] == "common" for f in human),
        "보통": sum(f["rarity"] == "normal" for f in human),
        "귀함": sum(f["rarity"] == "rare" for f in human),
        "난이도 하": sum(f["ai_difficulty"] == "low" for f in human),
        "난이도 중": sum(f["ai_difficulty"] == "mid" for f in human),
        "난이도 상": sum(f["ai_difficulty"] == "high" for f in human),
    }
    for label, expected in CONTRACT_200.items():
        if got_200[label] != expected:
            failures.append(f"200종 {label}: {got_200[label]} != 계약 {expected}")

    per_month = Counter()
    for f in human:
        for month in f["bloom_months"]:
            per_month[month] += 1
    for month, expected in CONTRACT_MONTHS_200.items():
        got = per_month.get(month, 0)
        if got != expected:
            failures.append(f"200종 {month}월 개화 종수: {got} != 계약 {expected}")

    # ── 전체 2,057종 드리프트 ────────────────────────────────────────
    got_all = {
        "종수": len(flowers),
        "난이도 하": sum(f["ai_difficulty"] == "low" for f in flowers),
        "난이도 중": sum(f["ai_difficulty"] == "mid" for f in flowers),
        "난이도 상": sum(f["ai_difficulty"] == "high" for f in flowers),
        "계절 없음": sum(1 for f in flowers if f["season"] is None),
        "개화기 표기 없음": sum(1 for f in flowers if not f["bloom_label"]),
        "별칭": sum(len(f["scientific_aliases"]) for f in flowers),
        "도감 칸": len(set(f["collect_group_id"] for f in flowers)),
    }
    for label, expected in TOTAL_2057.items():
        if got_all[label] != expected:
            failures.append(f"2057종 {label}: {got_all[label]} != 문서 {expected}")

    # ── 참조 무결성 ─────────────────────────────────────────────────
    ids = set(f["id"] for f in flowers)
    for f in flowers:
        for target in f["similar_flower_ids"]:
            if target not in ids:
                failures.append(f"{f['id']}: 비슷한꽃 id {target}가 범위 밖")
            if target == f["id"]:
                failures.append(f"{f['id']}: 자기 자신을 비슷한꽃으로 가리킨다")

    # ── B-4 수집 그룹 (계약 1-6) ────────────────────────────────────
    # 🔴 **행이 지워지지 않았는지**가 이 층의 핵심이다. 멤버를 지우면 그 학명이
    #    도감에서 사라져 **인식이 줄어든다** — 그런데 도감 화면은 더 깔끔해 보인다.
    mapping = cg.member_to_rep()
    if len(mapping) != 13:
        failures.append(f"접히는 멤버가 13종이어야 한다: {len(mapping)}")
    for member_id, rep_id in mapping.items():
        member = flowers[member_id - 1]
        rep = flowers[rep_id - 1]
        if member["id"] != member_id:
            failures.append(f"멤버 {member_id}의 행이 밀렸다")
        if member["collect_group_id"] != rep_id:
            failures.append(
                f"{member_id} {member['name']}의 collect_group_id가 {member['collect_group_id']}"
                f" — {rep_id}여야 한다")
        if rep["collect_group_id"] != rep_id:
            failures.append(f"대표 {rep_id} {rep['name']}이 자기 그룹이 아니다 — 사슬이다")
        if not member["bloom_months"]:
            failures.append(f"{member_id} {member['name']} 개화월이 비었다 — 후보에서 사라진다")
        if not member["scientific_name"]:
            failures.append(f"{member_id} {member['name']} 학명이 비었다 — 매칭이 죽는다")

    # 🔴 **이 한 줄이 B-4에서 가장 조용한 결함을 막는다.** 대표 민들레는 3~5월인데
    #    서양민들레는 3~10월이다. 멤버의 개화월을 대표로 갈아 끼우거나 멤버 행을
    #    지우면 **8월에 민들레류가 후보에서 사라진다** — 화면에는 `어떤 꽃인지 알 수
    #    없었어요`만 뜨고, 도감·통계는 전부 정상으로 보인다.
    if 8 not in flowers[31]["bloom_months"]:
        failures.append("32 서양민들레가 8월에 안 핀다고 적혀 있다 — 8월 민들레류 인식이 죽는다")
    if 8 in flowers[30]["bloom_months"]:
        failures.append("31 민들레(대표)에 8월이 들어갔다 — 멤버 개화월을 대표에 합치지 않는다")

    # 접힌 멤버로 가는 링크가 남으면 화면 05에서 **칸이 없는 종**으로 간다.
    for f in flowers:
        for target in f["similar_flower_ids"]:
            if target in mapping:
                failures.append(f"{f['id']} {f['name']}의 비슷한꽃 {target}이 접힌 멤버다")

    # ── 산출물이 실제로 갱신됐는지 ──────────────────────────────────
    # 🔴 이걸 안 세면 **Python은 옳은데 앱이 낡은 JSON을 싣는다** — 빌드는 성공하고
    #    화면도 정상이다. `flowers.json`은 커밋되는 생성물이라 손으로 다시 만들어야 한다.
    if not OUT_JSON.exists():
        failures.append(f"{OUT_JSON}가 없다 — build_app_data.py를 돌린다")
    else:
        import json
        on_disk = json.loads(OUT_JSON.read_text(encoding="utf-8"))["flowers"]
        if on_disk != flowers:
            failures.append(
                f"{OUT_JSON.name}이 지금 코드의 결과와 다르다 "
                f"(디스크 {len(on_disk)}종) — `python3 꽃도감/_tools/build_app_data.py`를 다시 돌린다")

    if failures:
        print(f"실패 {len(failures)}건")
        for line in failures:
            print(f"  ✗ {line}")
        return 1

    checks = (len(CASES) + len(BAD_CASES) + len(CONTRACT_200) + len(CONTRACT_MONTHS_200)
              + len(TOTAL_2057))
    print(f"통과 — 파서 {len(CASES) + len(BAD_CASES)}케이스 · 단정 {checks}개 "
          f"· 종 {len(flowers)} · 도감 칸 {got_all['도감 칸']} · dangling {len(dangling)}")
    report(flowers, stats, dangling)
    return 0


if __name__ == "__main__":
    sys.exit(main())
