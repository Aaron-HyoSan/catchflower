# -*- coding: utf-8 -*-
"""flowers.json → 적재 SQL 2개 (iOS·Android 공용)

**왜 SQL을 생성하는가.** 도감은 `flowers` 테이블에 들어가야 랭킹·발견 기록의
외래키가 성립한다. 그런데 anon(publishable) 키로는 대량 적재를 돌리기 번거롭고
(RLS가 flowers 쓰기를 막는다 — 그게 맞다), 오너가 대시보드에 붙여넣는 게 가장 확실하다.

**손으로 SQL을 쓰지 않는 이유:** 2,057줄을 사람이 옮기면 조용히 틀린다.
그리고 `bloom_months` 파싱은 `build_flowers_json.py`가 이미 한 번 했다 —
공유계약 1-2가 "파싱은 단 한 번"이라고 못 박았으므로 **여기서 다시 파싱하지 않고
flowers.json을 읽는다.** CSV를 직접 읽으면 파싱이 두 곳이 된다.

🔴 **파일을 두 개로 나눈다. 하나로 합칠 수 없다.**

    0003_seed_flowers.sql     id 1~200      (스키마 0001 상태에서 돈다)
    0006_seed_flowers_2057.sql id 201~2057  (스키마 **0005 이후**에만 돈다)

0001은 `check (id between 1 and 200)`이고 `season`·`bloom_label`·`color`·`habitat`이
`not null`이다. 신규 1,857종은 그 네 칸이 비어 있고 id가 201부터라 **0005가 먼저
돌아야 들어간다.** 합본(`오너_실행/build_합본.py`)은 번호순으로 이어 붙이므로
확장 적재가 0005 **뒤 번호**여야 한다 — 0003을 2,057종으로 바꾸면 오너가 합본을
붙여넣는 순간 **1,857종이 제약 위반으로 전부 거부된다.**

입력  flowers.json                        (생성물. 원본은 build_flowers_json.py)
출력  ../supabase/migrations/0003_seed_flowers.sql
      ../supabase/migrations/0006_seed_flowers_2057.sql

실행  python3 공용_적재/seed_flowers_sql.py
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
IN_PATH = os.path.join(HERE, "flowers.json")
MIGRATIONS = os.path.join(HERE, "..", "supabase", "migrations")
OUT_PATH = os.path.join(MIGRATIONS, "0003_seed_flowers.sql")
OUT_PATH_EXT = os.path.join(MIGRATIONS, "0006_seed_flowers_2057.sql")

HUMAN_COUNT = 200
TOTAL_COUNT = 2057

# 공유계약 1-4절의 enum 문자열. **여기 없는 값이 오면 멈춘다** —
# DB enum에 없는 문자열은 적재 시점에 터지고, 그때는 원인이 멀어진다.
SEASONS = {"spring", "summer", "autumn", "winter"}
RARITIES = {"common", "normal", "rare"}
DIFFICULTIES = {"low", "mid", "high"}
BLOOM_SOURCES = {"human", "draft", "observed", "peak_window", "unknown"}


def q(s):
    """SQL 문자열 리터럴. 홑따옴표를 두 개로 늘린다.

    꽃 이름에 따옴표가 없더라도 `주요서식지`에는 들어올 수 있다.
    이스케이프를 생략하면 문법 오류가 아니라 **값이 조용히 잘린다.**
    """
    return "'" + str(s).replace("'", "''") + "'"


def q_or_null(s):
    """빈 값은 **`null`로 내린다. `''`가 아니다.**

    🔴 `''`는 "값이 있고 그것이 빈 문자열"이다. 그러면 화면 쪽이 각자 `isEmpty`를
       확인해야 하고, 한 화면이 빠뜨리면 `국화과 · 에 피는 꽃`이 나오는데
       **아무 검사도 빨개지지 않는다**(계약 1-1-c). null이면 잊었을 때 터진다.
    """
    return "null" if s is None or s == "" else q(s)


def int_array(xs):
    return "'{" + ",".join(str(int(x)) for x in xs) + "}'"


HEADER_200 = """-- 도감 마스터 — 사람이 정한 200종 (id 1~200)
--
-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`이고
-- 그 입력은 `공용_적재/flowers.json`(← `꽃도감/꽃목록_200종.csv`)이다.
-- 꽃 데이터를 바꾸려면 `꽃도감/_tools/flowers.py`부터 고친다.
--
-- `bloom_months`는 **이미 파싱된 값**이다 (공유계약 1-2: 파싱은 단 한 번).
-- 적용: 0001_init.sql 다음에 SQL Editor에서 Run. 여러 번 돌려도 안전하다.
--
-- ⚠️ **여기에 신규 1,857종은 없다.** 그건 `0006_seed_flowers_2057.sql`이고,
--    0001의 제약(`id between 1 and 200` · `season not null` 등) 때문에
--    **0005가 먼저 돌아야** 들어간다. 파일이 두 개인 이유가 그것이다.
-- ⚠️ **`bloom_source` 컬럼을 여기서 적지 않는다.** 이 파일은 그 컬럼이 생기기 전
--    스키마에서도 돌아야 한다(합본은 번호순이다). 0005가 기본값 `'human'`을 주고,
--    id 1~200은 실제로 사람이 정한 값이라 그 기본값이 사실과 맞다."""

HEADER_EXT = """-- 도감 확장 — 신규 1,857종 (id 201~2057)
--
-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`.
--
-- 🔴 **0005보다 먼저 돌면 전부 거부된다.** 0001은 `id between 1 and 200`이고
--    `season`·`bloom_label`·`color`·`habitat`이 `not null`이다. 신규종은 id가
--    201부터이고 그 네 칸이 비어 있다 — 채울 근거가 없어서 **null로 내린다**
--    (계약 1-1-c·1-2-c: 없는 문구를 만들지 않고 화면이 절을 뺀다).
--
-- ⚠️ **빈 문자열이 아니라 null이다.** `''`는 "값이 있고 그것이 빈 문자열"이라
--    `is null` 검사에 안 걸리고, 화면 쪽이 각자 `isEmpty`를 봐야 한다.
--    한 화면이 빠뜨리면 `국화과 · 에 피는 꽃`이 나오는데 아무 검사도 안 빨개진다.
--
-- ⚠️ **`bloom_source`를 함께 넣는다.** 이 컬럼이 없으면 "1,857종을 등록했다"가
--    "1,857종을 다 안다"로 읽힌다. `peak_window` 739 + `unknown` 278은
--    **사람이 채워야 하는 목록**이고, 그걸 세는 방법이 데이터 안에 있어야 한다."""


def write_seed(path, flowers, header, with_source, expect):
    """`flowers`를 upsert하는 마이그레이션 한 개를 쓴다.

    @param with_source `bloom_source` 컬럼을 함께 넣는가 (0005 이후 파일만 True)
    @param expect      이 파일까지 돌았을 때 테이블에 있어야 하는 **최소** 종수
    """
    cols = ["id", "name", "scientific_name", "family", "bloom_months", "bloom_label",
            "season", "color", "rarity", "habitat", "ai_difficulty",
            "similar_flower_ids", "illust_batch"]
    if with_source:
        cols.append("bloom_source")

    lines = [header, "",
             "insert into public.flowers",
             "  (" + ", ".join(cols) + ")",
             "values"]

    rows = []
    for fl in flowers:
        values = [
            str(fl["id"]),
            q(fl["name"]),
            q(fl["scientific_name"]),
            q(fl["family"]),
            int_array(fl["bloom_months"]),
            q_or_null(fl["bloom_label"]),
            q_or_null(fl["season"]),
            q_or_null(fl["color"]),
            q(fl["rarity"]),
            q_or_null(fl["habitat"]),
            q(fl["ai_difficulty"]),
            int_array(fl.get("similar_flower_ids", [])),
            str(fl.get("illust_batch", 0)),
        ]
        if with_source:
            values.append(q(fl["bloom_source"]))
        rows.append("  (" + ", ".join(values) + ")")
    lines.append(",\n".join(rows))

    # 재적재를 허용한다. 도감 데이터는 오너 결정(B-4 통합 등)으로 바뀔 수 있고,
    # 그때 테이블을 비우면 **discoveries의 외래키가 깨진다.** upsert가 맞다.
    updates = [c for c in cols if c != "id"]
    lines.append("on conflict (id) do update set")
    lines.append(",\n".join("  %-18s = excluded.%s" % (c, c) for c in updates) + ";")

    # 🔴 **`<>`가 아니라 `<`로 센다.** 원래 0003이 `if n <> 200`이었는데, 확장분이
    #    들어간 DB에서 그 줄은 **2057을 보고 실패한다** — 즉 합본을 다시 돌릴 수
    #    없어진다("두 번 돌려도 안전하다"가 이 폴더의 약속이다).
    #    빠진 것을 잡는 게 목적이므로 **하한**만 본다.
    lines += ["",
              "-- 적재 검증. %d종보다 적으면 뭔가 빠진 것이다." % expect,
              "-- (더 많은 것은 실패가 아니다 — 뒤 파일이 이미 돌았을 수 있다.)",
              "do $$",
              "declare n int;",
              "begin",
              "  select count(*) into n from public.flowers;",
              "  if n < %d then" % expect,
              "    raise exception '도감 종수가 %%개다. %d종 이상이어야 한다', n;" % expect,
              "  end if;",
              "end $$;",
              ""]

    if with_source:
        # 🔴 기본값 `'human'`이 조용히 새 행을 삼키는 것을 여기서 막는다(0005 2절).
        #    사람이 정한 종은 정확히 200이어야 한다 — 더 많으면 어떤 적재가
        #    `bloom_source`를 안 적은 것이고, 그러면 "사람이 확인한 값"의 수가
        #    부풀어 **채워야 할 목록이 줄어 보인다.**
        lines += ["-- 🔴 `bloom_source = 'human'`은 정확히 200종이어야 한다.",
                  "-- 더 많으면 기본값이 새 행을 삼킨 것이다 (0005 2절).",
                  "do $$",
                  "declare n int;",
                  "begin",
                  "  select count(*) into n from public.flowers where bloom_source = 'human';",
                  "  if n <> %d then" % HUMAN_COUNT,
                  "    raise exception '사람이 정한 종이 %%개다. %d이어야 한다"
                  " (bloom_source를 빼먹은 적재가 있다)', n;" % HUMAN_COUNT,
                  "  end if;",
                  "end $$;",
                  ""]

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


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
        if not (1 <= fid <= TOTAL_COUNT):
            problems.append(f"도감번호 범위 밖: {fid}")
        months = fl["bloom_months"]
        if not months:
            # 개화월이 비면 개화월 하드 필터에서 **영구히 후보에 안 오른다.**
            problems.append(f"개화월 없음: {fid} {fl['name']}")
        # 🔴 상록수 9종에 **23개월**이 들어간 적이 있다(랩어라운드 버그).
        #    정확도 지표는 하나도 안 움직였고 화면에 `6~4월에 피는 꽃`만 떴다.
        #    0005의 check와 **같은 것**을 여기서도 본다 — 대시보드로 손으로 넣는
        #    경로가 있어서 한 층만 두면 못 막는다.
        if len(set(months)) != len(months):
            problems.append(f"개화월에 중복: {fid} {fl['name']} {months}")
        if any(not 1 <= m <= 12 for m in months):
            problems.append(f"개화월이 1~12 밖: {fid} {fl['name']} {months}")
        if len(months) >= 12 and fl["bloom_label"]:
            # `1~12월`은 "일 년 내내 핀다"는 단정이다. 근거는 그렇게 말하지 않는다.
            problems.append(f"12개월인데 표기가 있다: {fid} {fl['name']} {fl['bloom_label']!r}")
        # season은 **null이 정상이다**(계약 1-1-d · 278종). 값이 있으면 enum이어야 한다.
        if fl["season"] is not None and fl["season"] not in SEASONS:
            problems.append(f"season 값 이상: {fid} {fl['season']}")
        if fl["rarity"] not in RARITIES:
            problems.append(f"rarity 값 이상: {fid} {fl['rarity']}")
        if fl["ai_difficulty"] not in DIFFICULTIES:
            problems.append(f"ai_difficulty 값 이상: {fid} {fl['ai_difficulty']}")
        if fl.get("bloom_source") not in BLOOM_SOURCES:
            problems.append(f"bloom_source 값 이상: {fid} {fl.get('bloom_source')!r}")
        for sid in fl.get("similar_flower_ids", []):
            if sid == fid:
                problems.append(f"자기 자신을 비슷한 꽃으로 가리킨다: {fid}")

    # `similar_flower_ids`가 없는 id를 가리키면 적재 후 도감 상세에서 빈 칸이 된다.
    # 외래키를 걸지 않은 배열 컬럼이라 **DB가 못 잡는다** → 여기서 잡는다.
    for fl in flowers:
        for sid in fl.get("similar_flower_ids", []):
            if sid not in seen_ids:
                problems.append(f"비슷한 꽃 id가 도감에 없다: {fl['id']} → {sid}")

    missing = sorted(set(range(1, TOTAL_COUNT + 1)) - seen_ids)
    if missing:
        problems.append(f"빈 도감번호 {len(missing)}개: {missing[:10]}")

    # 🔴 사람이 정한 200종은 네 칸이 **전부 채워져 있어야 한다.** 여기가 비면
    #    확장 초안이 id 1~200을 덮은 것이고, 그러면 Top-1이 77.0% → 50.0%로
    #    떨어진다(계약 1-1-a 실측). 화면에는 아무 이상이 없어 보인다.
    for fl in flowers:
        if fl["id"] > HUMAN_COUNT:
            continue
        for key in ("season", "color", "habitat", "bloom_label"):
            if not fl[key]:
                problems.append(f"사람이 정한 종인데 {key}가 비었다: {fl['id']} {fl['name']}")

    if problems:
        print("❌ 적재 중단 — 데이터 문제 %d건" % len(problems))
        for p in problems[:20]:
            print("   ", p)
        return 1

    human = [f for f in flowers if f["id"] <= HUMAN_COUNT]
    new = [f for f in flowers if f["id"] > HUMAN_COUNT]
    if len(human) != HUMAN_COUNT:
        print("❌ 사람이 정한 종이 %d개다. %d개여야 한다" % (len(human), HUMAN_COUNT))
        return 1

    write_seed(OUT_PATH, human, HEADER_200, with_source=False, expect=HUMAN_COUNT)
    write_seed(OUT_PATH_EXT, new, HEADER_EXT, with_source=True, expect=TOTAL_COUNT)

    # 분포를 같이 찍는다. 공유계약 1-1의 값과 다르면 CSV가 바뀐 것이다.
    from collections import Counter
    rel = lambda p: os.path.relpath(p, os.path.join(HERE, ".."))
    print("✅ %s — %d종 (id 1~%d)" % (rel(OUT_PATH), len(human), HUMAN_COUNT))
    print("✅ %s — %d종 (id %d~%d)" % (rel(OUT_PATH_EXT), len(new),
                                       HUMAN_COUNT + 1, TOTAL_COUNT))
    def count(key):
        return dict(Counter(f[key] if f[key] is not None else "(null)" for f in flowers))
    print("   합계 %d종 · 계절 %s" % (len(flowers), count("season")))
    print("   희귀도 %s" % count("rarity"))
    print("   AI난이도 %s" % count("ai_difficulty"))
    print("   출처 %s" % count("bloom_source"))
    print("   개화기 표기 없는 종 %d · 대표색 없는 종 %d · 서식지 없는 종 %d" % (
        sum(1 for f in flowers if not f["bloom_label"]),
        sum(1 for f in flowers if not f["color"]),
        sum(1 for f in flowers if not f["habitat"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
