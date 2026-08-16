#!/usr/bin/env python3
"""`collect_groups.self_check`가 **실제로 빨개지는지** 잰다.

🔴 왜 이 파일이 따로 있는가. 그룹 표는 **손으로 적은 목록**이고, 손으로 적은 목록을
   검사하는 코드는 **아무것도 안 잡으면서 초록**일 수 있다. 이 저장소가 그 함정을
   여러 번 밟았다(`catchflower-green-tests-are-not-evidence` ⑩·⑯).
   그래서 검사를 믿는 대신 **일부러 틀린 표를 넣어 걸리는지** 본다.
   "이게 어떤 경우에 빨개지나"에 답이 있는 검사만 남긴다.

⚠️ 옳은 표가 통과하는 것도 같이 단정한다 — 검사가 **모든 것을 거부하는** 상태여도
   위의 red들은 전부 초록으로 보인다(그럼 아무 표도 못 넣는다).

실행:
    python3 공용_적재/test_collect_groups.py
"""

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import collect_groups as cg  # noqa: E402
import flower_master as fm  # noqa: E402

# 검사용 가짜 도감 — 이름이 id와 짝지어져 있다는 것만 쓴다.
FAKE_NAMES = {1: "가", 2: "나", 3: "다", 4: "라", 5: "마"}


def _group(rep, members, reason="이유"):
    return {"rep": rep, "members": members, "reason": reason}


# (설명, 표, 오류 메시지에 들어 있어야 할 조각)
BAD_TABLES = [
    ("도감에 없는 번호",
     [_group((1, "가"), [(99, "없는꽃")])],
     "도감에 없는 번호"),
    ("id는 있는데 이름이 다르다 (CSV가 밀린 경우)",
     [_group((1, "가"), [(2, "라")])],
     "CSV가 바뀌었다"),
    ("한 종이 두 그룹에 있다",
     [_group((1, "가"), [(3, "다")]), _group((2, "나"), [(3, "다")])],
     "두 그룹에 있다"),
    ("대표종이 다른 그룹의 멤버다 (사슬)",
     [_group((1, "가"), [(2, "나")]), _group((2, "나"), [(3, "다")])],
     "사슬"),
    ("이유가 비었다",
     [_group((1, "가"), [(2, "나")], reason="   ")],
     "reason"),
    ("멤버가 없다",
     [_group((1, "가"), [])],
     "멤버가 없다"),
    ("대표가 자기 자신을 멤버로 넣었다",
     [_group((1, "가"), [(1, "가")])],
     "두 그룹에 있다"),
]

GOOD_TABLE = [_group((1, "가"), [(2, "나")]), _group((3, "다"), [(4, "라")])]


def main() -> int:
    failures = []

    # ① 틀린 표는 반드시 던진다. **무엇 때문에 던졌는지도** 본다 —
    #    다른 이유로 던지면 그 검사는 여전히 안 도는 것이다.
    for label, table, needle in BAD_TABLES:
        try:
            cg.self_check(FAKE_NAMES, groups=table)
        except ValueError as exc:
            if needle not in str(exc):
                failures.append(f"{label}: 던졌지만 이유가 다르다 — {exc}")
        else:
            failures.append(f"{label}: 통과했다 (검사가 안 돈다)")

    # ② 옳은 표는 통과한다.
    try:
        stats = cg.self_check(FAKE_NAMES, groups=GOOD_TABLE)
    except ValueError as exc:
        failures.append(f"옳은 표를 거부했다 — {exc}")
    else:
        if stats != {"groups": 2, "members": 2, "slots": 3}:
            failures.append(f"옳은 표의 집계가 틀렸다: {stats}")

    # ③ 실제 표 — 실제 도감으로 돌린다. 여기가 빨개지면 CSV가 바뀐 것이다.
    flowers, _stats, _dangling = fm.build_master()
    name_by_id = {f["id"]: f["name"] for f in flowers}
    try:
        real = cg.self_check(name_by_id)
    except ValueError as exc:
        failures.append(f"실제 그룹 표가 도감과 어긋난다 — {exc}")
        real = None
    if real is not None and real != {"groups": 8, "members": 13, "slots": 2044}:
        failures.append(f"실제 집계가 문서(8그룹·13멤버·2044칸)와 다르다: {real}")
    if cg.SLOT_COUNT != 2044:
        failures.append(f"SLOT_COUNT가 2044가 아니다: {cg.SLOT_COUNT}")

    # ④ `group_id_of`의 기본값은 **자기 자신**이다. 이게 `None`이 되면 읽는 쪽이
    #    "그룹 없음" 분기를 타야 하고, 그 분기를 빠뜨리면 도감 칸이 조용히 두 개가 된다.
    mapping = cg.member_to_rep()
    if cg.group_id_of(1, mapping) != 1:
        failures.append("그룹에 없는 종의 group_id가 자기 자신이 아니다")
    if cg.group_id_of(32, mapping) != 31:
        failures.append("32 서양민들레의 group_id가 31이 아니다")
    if 31 in mapping:
        failures.append("대표종 31이 mapping에 들어 있다 — 접기가 두 번 돈다")

    # ⑤ **그룹 밖에 남은 같은 속**을 세는 보고가 0을 돌려주고 통과하지 않는지.
    #    이 숫자가 B-4가 얼마나 남았는지의 유일한 근거다(오너 보고용).
    outside = cg.outside_report(flowers)
    if outside["clusters"] < 100:
        failures.append(f"그룹 밖 클러스터가 {outside['clusters']}개뿐이다 — 세는 코드를 의심한다")
    for genus, count, names in outside["top"]:
        if count < len(names):
            failures.append(f"{genus}: 종수 {count} < 표본 {len(names)} — 세는 방식이 틀렸다")

    if failures:
        print(f"실패 {len(failures)}건")
        for line in failures:
            print(f"  ✗ {line}")
        return 1

    print(f"통과 — 틀린 표 {len(BAD_TABLES)}종류가 전부 빨개진다 · 실제 표 {real} "
          f"· 그룹 밖 같은 속 클러스터 {outside['clusters']}개 / {outside['species']}종")
    return 0


if __name__ == "__main__":
    sys.exit(main())
