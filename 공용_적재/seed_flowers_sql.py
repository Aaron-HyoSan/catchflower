# -*- coding: utf-8 -*-
"""flowers.json → 0003_seed_flowers.sql (iOS·Android 공용)

**왜 SQL을 생성하는가.** 도감 200종은 `flowers` 테이블에 들어가야 랭킹·발견 기록의
외래키가 성립한다. 그런데 anon(publishable) 키로는 대량 적재를 돌리기 번거롭고
(RLS가 flowers 쓰기를 막는다 — 그게 맞다), 오너가 대시보드에 붙여넣는 게 가장 확실하다.

**손으로 SQL을 쓰지 않는 이유:** 200줄을 사람이 옮기면 조용히 틀린다.
그리고 `bloom_months` 파싱은 `build_flowers_json.py`가 이미 한 번 했다 —
공유계약 1-2가 "파싱은 단 한 번"이라고 못 박았으므로 **여기서 다시 파싱하지 않고
flowers.json을 읽는다.** CSV를 직접 읽으면 파싱이 두 곳이 된다.

입력  flowers.json                        (생성물. 원본은 build_flowers_json.py)
출력  ../supabase/migrations/0003_seed_flowers.sql

실행  python3 공용_적재/seed_flowers_sql.py
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
IN_PATH = os.path.join(HERE, "flowers.json")
OUT_PATH = os.path.join(HERE, "..", "supabase", "migrations", "0003_seed_flowers.sql")

# 공유계약 1-4절의 enum 문자열. **여기 없는 값이 오면 멈춘다** —
# DB enum에 없는 문자열은 적재 시점에 터지고, 그때는 원인이 멀어진다.
SEASONS = {"spring", "summer", "autumn", "winter"}
RARITIES = {"common", "normal", "rare"}
DIFFICULTIES = {"low", "mid", "high"}


def q(s):
    """SQL 문자열 리터럴. 홑따옴표를 두 개로 늘린다.

    꽃 이름에 따옴표가 없더라도 `주요서식지`에는 들어올 수 있다.
    이스케이프를 생략하면 문법 오류가 아니라 **값이 조용히 잘린다.**
    """
    return "'" + str(s).replace("'", "''") + "'"


def int_array(xs):
    return "'{" + ",".join(str(int(x)) for x in xs) + "}'"


def main():
    with open(IN_PATH, encoding="utf-8") as f:
        data = json.load(f)

    flowers = data["flowers"]
    problems = []

    seen_ids = set()
    for fl in flowers:
        fid = fl["id"]
        if fid in seen_ids:
            problems.append(f"도감번호 중복: {fid}")
        seen_ids.add(fid)
        if not (1 <= fid <= 200):
            problems.append(f"도감번호 범위 밖: {fid}")
        if not fl["bloom_months"]:
            # 개화월이 비면 개화월 하드 필터에서 **영구히 후보에 안 오른다.**
            problems.append(f"개화월 없음: {fid} {fl['name']}")
        if fl["season"] not in SEASONS:
            problems.append(f"season 값 이상: {fid} {fl['season']}")
        if fl["rarity"] not in RARITIES:
            problems.append(f"rarity 값 이상: {fid} {fl['rarity']}")
        if fl["ai_difficulty"] not in DIFFICULTIES:
            problems.append(f"ai_difficulty 값 이상: {fid} {fl['ai_difficulty']}")
        for sid in fl.get("similar_flower_ids", []):
            if sid == fid:
                problems.append(f"자기 자신을 비슷한 꽃으로 가리킨다: {fid}")

    # `similar_flower_ids`가 없는 id를 가리키면 적재 후 도감 상세에서 빈 칸이 된다.
    # 외래키를 걸지 않은 배열 컬럼이라 **DB가 못 잡는다** → 여기서 잡는다.
    for fl in flowers:
        for sid in fl.get("similar_flower_ids", []):
            if sid not in seen_ids:
                problems.append(f"비슷한 꽃 id가 도감에 없다: {fl['id']} → {sid}")

    missing = sorted(set(range(1, 201)) - seen_ids)
    if missing:
        problems.append(f"빈 도감번호 {len(missing)}개: {missing[:10]}")

    if problems:
        print("❌ 적재 중단 — 데이터 문제 %d건" % len(problems))
        for p in problems[:20]:
            print("   ", p)
        return 1

    lines = [
        "-- 도감 마스터 200종 적재",
        "--",
        "-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`이고",
        "-- 그 입력은 `공용_적재/flowers.json`(← `꽃도감/꽃목록_200종.csv`)이다.",
        "-- 꽃 데이터를 바꾸려면 `꽃도감/_tools/flowers.py`부터 고친다.",
        "--",
        "-- `bloom_months`는 **이미 파싱된 값**이다 (공유계약 1-2: 파싱은 단 한 번).",
        "-- 적용: 0001_init.sql 다음에 SQL Editor에서 Run. 여러 번 돌려도 안전하다.",
        "",
        "insert into public.flowers",
        "  (id, name, scientific_name, family, bloom_months, bloom_label,",
        "   season, color, rarity, habitat, ai_difficulty, similar_flower_ids, illust_batch)",
        "values",
    ]

    rows = []
    for fl in flowers:
        rows.append(
            "  ({id}, {name}, {sci}, {fam}, {bm}, {bl}, {season}, {color}, "
            "{rarity}, {hab}, {diff}, {sim}, {batch})".format(
                id=fl["id"],
                name=q(fl["name"]),
                sci=q(fl["scientific_name"]),
                fam=q(fl["family"]),
                bm=int_array(fl["bloom_months"]),
                bl=q(fl["bloom_label"]),
                season=q(fl["season"]),
                color=q(fl["color"]),
                rarity=q(fl["rarity"]),
                hab=q(fl["habitat"]),
                diff=q(fl["ai_difficulty"]),
                sim=int_array(fl.get("similar_flower_ids", [])),
                batch=fl.get("illust_batch", 0),
            )
        )
    lines.append(",\n".join(rows))

    # 재적재를 허용한다. 도감 데이터는 오너 결정(B-4 통합 등)으로 바뀔 수 있고,
    # 그때 테이블을 비우면 **discoveries의 외래키가 깨진다.** upsert가 맞다.
    lines += [
        "on conflict (id) do update set",
        "  name               = excluded.name,",
        "  scientific_name    = excluded.scientific_name,",
        "  family             = excluded.family,",
        "  bloom_months       = excluded.bloom_months,",
        "  bloom_label        = excluded.bloom_label,",
        "  season             = excluded.season,",
        "  color              = excluded.color,",
        "  rarity             = excluded.rarity,",
        "  habitat            = excluded.habitat,",
        "  ai_difficulty      = excluded.ai_difficulty,",
        "  similar_flower_ids = excluded.similar_flower_ids,",
        "  illust_batch       = excluded.illust_batch;",
        "",
        "-- 적재 검증. 200이 아니면 뭔가 빠진 것이다.",
        "do $$",
        "declare n int;",
        "begin",
        "  select count(*) into n from public.flowers;",
        "  if n <> 200 then",
        "    raise exception '도감 종수가 %개다. 200이어야 한다', n;",
        "  end if;",
        "end $$;",
        "",
    ]

    with open(OUT_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    # 분포를 같이 찍는다. 공유계약 1-1의 값과 다르면 CSV가 바뀐 것이다.
    from collections import Counter
    print("✅ %s" % os.path.relpath(OUT_PATH, os.path.join(HERE, "..")))
    print("   %d종 · 계절 %s" % (len(flowers), dict(Counter(f["season"] for f in flowers))))
    print("   희귀도 %s" % dict(Counter(f["rarity"] for f in flowers)))
    print("   AI난이도 %s" % dict(Counter(f["ai_difficulty"] for f in flowers)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
