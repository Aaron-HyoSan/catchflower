#!/usr/bin/env python3
"""마이그레이션 SQL이 **postgres가 실제로 받는 문법인가**를 검사한다.

🔴 **왜 필요한가.** 이 SQL은 오너가 대시보드에 붙여넣어 딱 한 번 돈다. 문법이 틀리면
   그때 처음 알게 되고, 우리는 그 자리에 없다. 그리고 "눈으로 읽어서 맞아 보인다"는
   증거가 아니다 — 실제로 `check` 제약 안에 서브쿼리를 썼다가 postgres가 거절하는
   코드를 이 검사로 처음 잡았다(`cannot use subquery in check constraint`).

**진짜 postgres 파서로 검사한다.** `pglast`는 postgres의 `gram.y`를 그대로 쓴 바인딩이라
정규식 검사와 다르다. plpgsql 본문도 `parse_plpgsql`로 따로 본다 — 그게 없으면
`do` 블록과 함수 본문 안의 문법 오류가 **전부 통과한다**(대조군으로 확인했다).

🔴 **본문을 `do $$ … $$`로 감싸서 넣으면 안 된다 — 내가 처음 그렇게 썼고 red 5개가
   났는데 전부 검사기 잘못이었다.** `do` 블록에는 `NEW` 레코드도 없고 반환 타입도
   없어서, 트리거 함수의 `new.captured_date`가 "모르는 변수"가 되고 `returns setof`
   함수의 `return query`가 "SETOF가 아닌 함수"가 된다. **`create function` 문장을
   통째로** 넘겨야 문맥이 산다. 그게 여전히 진짜 오류를 잡는지는 돌연변이로 확인했다
   (`if` 뒤를 비우면 거부 · `end if;`를 지우면 거부).

⚠️ **이 검사가 잡지 못하는 것**(초록을 과신하지 않기 위해 적는다):
  - 컬럼·함수·변수가 **존재하는지**는 못 본다. 문법만 본다
    (돌연변이 대조군에서 `new.존재하지않는칸`은 **통과했다** — 실행 시점에 터진다).
  - 서브쿼리 금지처럼 **파서가 아니라 실행기가 막는 규칙**은 못 본다
    (`check (… (select …))`도 파싱은 된다 — 그 규칙만 아래에서 따로 본다).
  - 값이 맞는지, 제약이 실제로 데이터를 막는지는 못 본다.
  - **주석 안의 문자열을 코드로 읽은 적이 있다** — `statement_language` 주석 참고.
    문법 판정을 `"language sql" in stmt` 같은 부분문자열로 하면 안 된다.

사전 준비 (선택 도구다 — 없으면 이 검사만 못 돈다):

    python3 -m pip install --target /경로/pglast_pkg pglast
    PYTHONPATH=/경로/pglast_pkg python3 supabase/_tools/check_migrations.py

⚠️ 시스템 파이썬에 바로 깔면 이 맥에서 `externally-managed-environment`로 막힌다.
   그래서 `--target`으로 따로 두고 `PYTHONPATH`로 준다. 전역 설치가 되는 환경이면

    python3 supabase/_tools/check_migrations.py
"""
import pathlib
import re
import sys

try:
    import pglast
except ImportError:
    sys.exit("pglast가 없다:  python3 -m pip install pglast")

ROOT = pathlib.Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "supabase" / "migrations"


def statement_language(stmt):
    """문장의 `language …` 절을 돌려준다. 없으면 `None`.

    🔴 **`"language sql" in stmt`로 판정하면 안 된다.** 0002의 `region_ranking`은
       `language plpgsql`인데 주석에 "`language sql`로 쓰면 안 된다"고 적혀 있어서
       그 문자열이 걸렸다 — 검사가 **주석을 코드로 읽고** 빨개졌다.
       (이 저장소에서 두 번째다: 금지어 목록이 원리상 못 막는 것과 같은 형태.)

    그래서 ① `$$ … $$` 본문을 지우고(본문 안 주석·문자열을 오해하지 않게)
    ② 줄 주석을 지운 다음 ③ `language` 절을 찾는다.
    """
    masked = re.sub(r"\$\$.*?\$\$", "$$$$", stmt, flags=re.S)
    masked = re.sub(r"--[^\n]*", "", masked)
    m = re.search(r"\blanguage\s+([a-z_]+)", masked, flags=re.I)
    return m.group(1).lower() if m else None


def plpgsql_statements(sql):
    """plpgsql 본문을 가진 문장을 **문장 단위로** 돌려준다.

    ⚠️ 본문만 떼어 `do $$ … $$`로 감싸면 안 된다 — 머리말 참고.

    🔴 `^\\s*do\\s+\\$`로 찾았다가 0003·0006의 `do $$` 블록을 **0개로 세면서 PASS**했다.
       `pglast.split`이 앞 주석을 문장에 붙여 주기 때문에 `do`가 줄 맨 앞이 아니다.
       **개수가 0인데 초록인 것**이 유일한 증상이었다 — 그래서 아래 `main`이
       "블록이 하나도 없다"를 따로 경고한다. 검사가 아무것도 안 하고 통과하는 게
       이 저장소에서 반복해 당한 실패다.
    """
    out = []
    for stmt in pglast.split(sql):
        # ⚠️ 여기도 `"language plpgsql" in stmt`로 보면 주석을 코드로 읽는다
        #    (`statement_language` 주석 참고). 이쪽은 **더 많이 검사하는** 방향의
        #    오류라서 조용하지만, 그래도 잘못된 문장을 파서에 넣으면 가짜 red가 난다.
        if statement_language(stmt) == "plpgsql" or re.search(r"(^|\n)\s*do\s+\$", stmt.lower()):
            out.append(stmt)
    return out


def check_sql_function_bodies(sql, name, problems):
    """🔴 `language sql` 함수 **본문**을 파싱한다.

    `parse_sql`은 함수 본문을 **문자열로만** 본다 — 안이 `select from where`여도
    통과한다(돌연변이로 확인했다). `language plpgsql`은 `parse_plpgsql`이 봐 주는데
    `language sql`은 봐 주는 게 없어서 **검사에 구멍이 생긴다.**
    0005의 `bloom_months_sane`이 그 함수라서 직접 뜯어 본다.

    반환값: 검사한 본문 개수 (0이면 위 카운트 경고에 걸린다).
    """
    checked = 0
    for stmt in pglast.split(sql):
        if statement_language(stmt) != "sql" or "create" not in stmt.lower():
            continue
        m = re.search(r"\$\$(.*?)\$\$", stmt, flags=re.S)
        if not m:
            continue
        body = m.group(1).strip()
        checked += 1
        try:
            pglast.parse_sql(body if body.endswith(";") else body + ";")
        except Exception as e:
            problems.append(f"{name}: language sql 함수 본문 — {str(e).splitlines()[0]}")
    return checked


def check_no_subquery_in_check(sql, name, problems):
    """🔴 `check (…)` 안의 서브쿼리 — **파서는 통과시키고 실행기가 거절한다.**

    `ERROR: cannot use subquery in check constraint`. 실제로 이걸로 한 번 막혔다.
    괄호 균형으로 `check ( … )` 범위를 잘라 내고 그 안의 `select`를 본다
    (정규식만으로 자르면 중첩 괄호에서 틀린다).
    """
    for m in re.finditer(r"\bcheck\s*\(", sql, flags=re.I):
        depth, i = 0, m.end() - 1
        while i < len(sql):
            if sql[i] == "(":
                depth += 1
            elif sql[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = sql[m.end():i]
        if re.search(r"\bselect\b", body, flags=re.I):
            line = sql[:m.start()].count("\n") + 1
            problems.append(
                f"{name}:{line} check 제약 안에 서브쿼리가 있다 — postgres가 거절한다"
                f" (immutable 함수로 감싼다): {body.strip()[:60]}…")


def main():
    files = sorted(MIGRATIONS.glob("[0-9][0-9][0-9][0-9]_*.sql"))
    if not files:
        sys.exit(f"마이그레이션이 없다: {MIGRATIONS}")

    problems = []
    total_blocks = 0
    for path in files:
        sql = path.read_text(encoding="utf-8")
        name = path.name
        try:
            stmts = pglast.parse_sql(sql)
        except Exception as e:
            problems.append(f"{name}: SQL 문법 — {str(e).splitlines()[0]}")
            continue

        blocks = 0
        for stmt in plpgsql_statements(sql):
            blocks += 1
            try:
                pglast.parse_plpgsql(stmt)
            except Exception as e:
                head = next((l for l in stmt.splitlines() if l.strip()
                             and not l.strip().startswith("--")), "")
                problems.append(f"{name}: plpgsql — {str(e).splitlines()[0]}"
                                f"  ({head.strip()[:50]})")

        sql_bodies = check_sql_function_bodies(sql, name, problems)
        check_no_subquery_in_check(sql, name, problems)

        # 🔴 **검사 대상을 세어서 보고한다.** 파일에 `$$` 블록이 있는데 위에서
        #    0개를 셌다면 그건 PASS가 아니라 **검사가 그 파일을 안 본 것**이다.
        #    실제로 그렇게 통과한 적이 있다(`plpgsql_statements` 주석 참고).
        if "$$" in sql and blocks + sql_bodies == 0:
            problems.append(f"{name}: `$$` 블록이 있는데 본문 검사가 0개를 셌다 —"
                            " 검사가 이 파일을 안 봤다")
        total_blocks += blocks + sql_bodies
        print(f"  OK   {name}  ({len(stmts)}문장 · plpgsql {blocks} · sql함수 {sql_bodies})")

    if problems:
        print(f"\n🔴 문제 {len(problems)}건")
        for p in problems:
            print("   -", p)
        return 1
    print(f"\n판정: PASS — {len(files)}개 파일 · plpgsql {total_blocks}블록")
    return 0


if __name__ == "__main__":
    sys.exit(main())
