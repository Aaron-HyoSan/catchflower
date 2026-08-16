# -*- coding: utf-8 -*-
"""`05_B4_수집그룹_이관.sql`을 만든다. **결과 파일을 직접 고치지 않는다.**

🔴 **왜 손으로 안 쓰나.** 이 SQL에는 도감번호 13쌍이 박힌다. 손으로 적으면
`공용_적재/collect_groups.py`(원본)와 갈리는데, **틀려도 SQL은 성공한다** —
없는 번호를 UPDATE하면 `0 rows`이고 그건 "고칠 게 없었다"와 구분되지 않는다.
그래서 표에서 생성한다. 그룹을 늘리면 이 스크립트를 다시 돌린다.

⚠️ **`supabase/migrations/`에 넣지 않는다.** 스키마 변경이 아니라 **한 번 돌리는
데이터 수정**이고, `build_합본.py`가 `migrations/`를 훑어 오너 붙여넣기 파일에
자동으로 넣기 때문에 거기 두면 **다음에 스키마를 세우는 사람이 이것까지 돌린다.**
(계약 6절: `migrations/`는 iOS가 만든다 · AOS 제안은 `프로젝트 맥락/제안/`.)

    python3 오너_실행/build_05_b4_이관.py
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "공용_적재"))

import collect_groups  # noqa: E402

OUT = ROOT / "오너_실행" / "05_B4_수집그룹_이관.sql"

HEAD = """\
-- ════════════════════════════════════════════════════════════════
--  05 — B-4 수집 그룹 이관 (2026-08-16 · AOS 세션 · 진행.md (78))
--  🔴 **생성물이다.** 원본은 `오너_실행/build_05_b4_이관.py` +
--     `공용_적재/collect_groups.py`. 이 파일을 직접 고치면 다음 생성에 지워진다.
-- ════════════════════════════════════════════════════════════════
--
--  ## 무엇을 하나
--
--  사진으로 못 가르는 종 **{members}종**을 도감 한 칸(**대표종**)으로 접었다(계약 1-6).
--  앱은 **자기 로컬 파일을 실행 시 자동으로 이관한다**(멱등). 그런데 **서버에 이미
--  올라간 행은 앱이 못 고친다** — 업로드는 INSERT뿐이고 앱은 남의 행도 자기 행도
--  UPDATE하지 않는다(계약 1-3). 그 몫이 이 파일이다.
--
--  ## 🔴 안 고치면 어떻게 되나 — **오류가 아니라 두 숫자가 갈린다**
--
--  도감 그리드는 대표종 칸으로 세고(`모은 꽃 37종`), 서버 랭킹은 멤버 번호를
--  다른 종으로 세서 **38종**을 준다. **둘 다 그럴듯한 숫자라 화면으로는 어느 쪽이
--  맞는지 알 수 없다.** 예외도 빈 화면도 안 난다.
--
--  ## ✅ 통째로 붙여넣으면 된다 — **1절(미리보기)만 나온다**
--
--  2절(수정)과 3절(다시 세기)은 **둘 다 `/* */`로 잠겨 있다.**
--  🔴 SQL Editor는 여러 문장을 돌리면 **마지막 결과만** 보여준다 — `04`에서
--     그것 때문에 미리보기가 조용히 가려진 사고가 있었다. 그래서 열어 두지 않는다.
--
--  **순서:** 1절을 돌려 결과를 나에게 붙여 준다 → 내가 확인한다 → 2절을 풀어 돌린다.
--
--  ⚠️ **멱등이다.** 두 번 돌아도 대표종은 자기 자신이라 아무것도 안 바뀐다.
--  ⚠️ **`flowers` 표는 건드리지 않는다** — 종을 지우지도, 번호를 재배치하지도
--     않는다(`discoveries.flower_id`가 외래키다 · 계약 1-5). 바꾸는 것은
--     **발견 기록이 가리키는 번호**뿐이다.
--
--  ## 표 (대표 ⟵ 멤버)
--
{table}
--
-- ────────────────────────────────────────────────────────────────
--  1절 — 미리보기. **아무것도 바꾸지 않는다.**
-- ────────────────────────────────────────────────────────────────
--  🔵 `고칠행 0`이 나오면 **그것도 정상이다** — 그 멤버로 등록한 사람이 없었다는
--     뜻이다. 0이 나쁜 신호인 것은 `flowers`에 그 번호가 없을 때뿐인데,
--     그건 아래 `종이름`이 비는 것으로 갈린다(비면 나에게 알려 주세요).

with 그룹(멤버, 대표) as (
  values
{values}
)
select
  g.멤버                                     as 멤버번호,
  fm.name                                   as 멤버이름,
  g.대표                                     as 대표번호,
  fr.name                                   as 대표이름,
  count(d.id)                               as 고칠행,
  count(distinct d.user_id)                 as 해당계정
from 그룹 g
  left join flowers    fm on fm.id = g.멤버
  left join flowers    fr on fr.id = g.대표
  left join discoveries d  on d.flower_id = g.멤버
group by g.멤버, fm.name, g.대표, fr.name
order by g.멤버;

-- ────────────────────────────────────────────────────────────────
--  2절 — 실제 수정. 🔴 **1절 결과를 확인한 뒤에** 아래 `/*` `*/` 두 줄을 지운다.
-- ────────────────────────────────────────────────────────────────
/*
with 그룹(멤버, 대표) as (
  values
{values}
)
update discoveries d
   set flower_id = g.대표
  from 그룹 g
 where d.flower_id = g.멤버
returning d.id, g.멤버 as 이전, g.대표 as 이후;
*/

-- ────────────────────────────────────────────────────────────────
--  3절 — 다시 세기. 2절을 돌린 **뒤에** 이 절만 따로 돌린다.
-- ────────────────────────────────────────────────────────────────
--  🔴 **`남은멤버행`이 0이어야 한다.** "수정했다"는 메시지는 성공을 증명하지
--     않는다 — 세어서 0인 것이 증거다.
/*
with 그룹(멤버, 대표) as (
  values
{values}
)
select
  (select count(*) from discoveries d join 그룹 g on g.멤버 = d.flower_id) as 남은멤버행,
  (select count(distinct flower_id) from discoveries)                      as 서버가세는종수,
  (select count(*) from flowers)                                           as 도감행,
  {slots}                                                                  as 도감칸_기대값;
*/
"""


def _check_values_blocks(text, member_count):
    """생성한 SQL의 `values` 블록을 **주석을 떼고** 검사한다.

    🔴 **쌍의 개수를 세는 것으로는 부족하다.** 쉼표가 주석 안으로 들어가도 쌍의
       수는 맞고, 그러면 문법 오류인 SQL이 "생성 성공"으로 나온다(실제로 한 번
       그랬다). 그래서 **`--` 뒤를 버린 코드 쪽**을 본다.
    """
    blocks = []
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.strip() != "values":
            continue
        rows = []
        for row in lines[i + 1:]:
            if not row.startswith("    ("):
                break
            rows.append(row)
        blocks.append(rows)

    assert len(blocks) == 3, "`values` 블록이 3개여야 한다(1·2·3절). 실제 %d개" % len(blocks)
    for n, rows in enumerate(blocks, start=1):
        assert len(rows) == member_count, (
            "%d번째 블록이 %d쌍이어야 한다. 실제 %d쌍" % (n, member_count, len(rows)))
        for j, row in enumerate(rows):
            code = row.split("--")[0].rstrip()
            m = re.fullmatch(r"\s*\(\d+, \d+\)(,?)", code)
            assert m, "%d번째 블록 %d행이 값 쌍이 아니다: %r" % (n, j + 1, code)
            last = j == len(rows) - 1
            assert bool(m.group(1)) != last, (
                "%d번째 블록 %d행의 쉼표가 틀렸다(마지막=%s): %r" % (n, j + 1, last, row))


def _check_parses(text):
    """진짜 PostgreSQL 파서로 문법을 확인한다 (`pglast` = libpg_query).

    🔴 **파일 전체를 한 번 넣는 것으로는 2·3절을 재지 못한다.** 그 두 절은
       `/* */` 안에 있어서 **파서가 주석으로 건너뛴다** — 즉 잠긴 절이 아무리
       망가져 있어도 전체 파싱은 통과한다. 잠금을 벗기는 순간 오너 앞에서 터진다.
       그래서 **블록을 따로 꺼내 각각 파싱한다.**

    ⚠️ `pglast`가 없으면 **조용히 통과시키지 않는다.** 검사가 스스로 꺼지는 것이
       가장 나쁘다 — 없다고 말하고 실패한다(`pip3 install pglast`).

    ⚠️ **문법만 본다.** 돌연변이로 확인하다가 알았다 — `g.멤버 == d.flower_id`는
       **문법 오류가 아니다**(Postgres는 사용자 정의 연산자를 허용해서 `==`를
       연산자 토큰으로 받는다). 없는 컬럼·없는 표·틀린 번호도 여기서는 안 걸린다.
       그건 1절 미리보기를 오너가 돌려서 **표로** 확인하는 몫이다.
    """
    import pglast  # 없으면 여기서 ImportError로 죽는 게 맞다

    open_part = pglast.parse_sql(text)
    assert len(open_part) == 1, (
        "열린 절이 1문장이어야 한다(1절 미리보기). 실제 %d문장 — 2·3절 잠금이 풀렸나?"
        % len(open_part))

    # ⚠️ **줄머리의 `/*`만 잠금으로 센다.** 머리말이 `` `/* */` `` 를 설명으로
    #    인용하고 있어서, 그냥 찾으면 그 인용까지 블록으로 세어 4개가 된다.
    blocks = re.findall(r"^/\*$(.*?)^\*/$", text, re.S | re.M)
    assert len(blocks) == 2, "잠긴 절이 2개여야 한다(2절·3절). 실제 %d개" % len(blocks)
    for n, block in enumerate(blocks, start=2):
        parsed = pglast.parse_sql(block)
        assert len(parsed) == 1, "%d절이 1문장이어야 한다. 실제 %d문장" % (n, len(parsed))
    return 1 + len(blocks)


def main():
    mapping = collect_groups.member_to_rep()
    name = {}
    for g in collect_groups.GROUPS:
        name[g["rep"][0]] = g["rep"][1]
        for mid, mname in g["members"]:
            name[mid] = mname

    # 🔴 **쉼표를 주석 앞에 붙인다.** `(32, 31)   -- 서양민들레,` 로 만들면 쉼표가
    #    주석 안으로 들어가서 두 번째 값부터 **문법 오류**다 — 실제로 처음에 그렇게
    #    만들었고, "생성 성공"이 떴다. 그 SQL은 **오너 앞에서 처음 터진다.**
    rows = sorted(mapping.items())
    values = "\n".join(
        "    (%d, %d)%s   -- %s → %s"
        % (m, r, "," if i < len(rows) - 1 else "", name[m], name[r])
        for i, (m, r) in enumerate(rows)
    )
    table = "\n".join(
        "--  | %d %s ⟵ %s" % (
            g["rep"][0], g["rep"][1],
            " · ".join("%d %s" % (mid, mname) for mid, mname in g["members"]),
        )
        for g in collect_groups.GROUPS
    )

    OUT.write_text(
        HEAD.format(
            members=collect_groups.MEMBER_COUNT,
            slots=collect_groups.SLOT_COUNT,
            table=table,
            values=values,
        ),
        encoding="utf-8",
    )

    # 🔴 생성한 것을 **다시 읽어서** 센다. 포맷 문자열이 조용히 비면
    #    `values` 자리가 사라진 채로 "생성 성공"이 되고, 그 SQL은 문법 오류로
    #    오너 앞에서 처음 터진다.
    text = OUT.read_text(encoding="utf-8")
    _check_values_blocks(text, collect_groups.MEMBER_COUNT)
    statements = _check_parses(text)
    for placeholder in ("{values}", "{table}", "{members}", "{slots}"):
        assert placeholder not in text, "%s 가 안 채워졌다" % placeholder
    print(
        "%s — 멤버 %d쌍 · 도감 칸 기대값 %d · **문법 검사 %d문장 통과**(pglast)"
        % (OUT.relative_to(ROOT), collect_groups.MEMBER_COUNT,
           collect_groups.SLOT_COUNT, statements)
    )


if __name__ == "__main__":
    main()
