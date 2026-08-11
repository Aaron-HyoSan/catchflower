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


def check_function_defined_before_use(files, problems):
    """🔴 정책이 **아직 없는 함수**를 부르는가 — 붙여넣기가 그 자리에서 멈춘다.

    `create policy`는 본문의 함수를 **그 시점에** 찾는다. 뒤에서 만들면
    `ERROR: function public.xxx(uuid) does not exist`로 끝난다. 그런데 문법은
    완벽하고, `pglast`는 존재 여부를 안 보므로 **문법 검사는 PASS다**
    (머리말의 "못 잡는 것" 1번 — 0007을 쓸 때 실제로 이 순서로 썼다).

    ⚠️ **파일 하나만 보면 안 된다.** 오너는 합본을 번호순으로 한 번에 돌리므로,
       0007의 정책이 0001의 함수를 부르는 것은 **정상**이다. 그래서 전체 파일을
       번호순으로 이어 붙인 위치를 기준으로 앞뒤를 판정한다.

    ⚠️ 이 검사는 `public.` 접두사가 붙은 호출만 본다. 스키마 없이 부르면 못 잡지만,
       이 저장소는 `search_path`를 못 믿어서 전부 `public.`을 붙이는 규칙이다.

    🔴 **이 검사의 첫 red는 검사 잘못이었다** — 0001을 잡았는데 0001은 실서버에서
       이미 정상으로 돌았다. 원인은 `create\\s+policy` 정규식이 **주석 안의
       "create policy`가 붙었다는 것과…"** 를 코드로 읽은 것이다. 이 저장소에서
       주석을 코드로 읽은 게 **세 번째**다(`statement_language` · 금지어 목록).
       그래서 문장 쪼개기는 `pglast.split`에 맡기고, 주석은 먼저 지운다.

    🔴 **그걸 고친 뒤 PASS는 우연이었다.** 순서를 실제로 뒤집는 돌연변이를 넣었는데
       **초록이 나왔다.** 원인: 정의 위치는 `strip_comments(sql)`의 오프셋으로,
       호출 위치는 **원문** 오프셋으로 재고 있었다 — 주석을 지운 쪽이 훨씬 짧으니
       **두 좌표계를 비교한 값에는 아무 의미가 없었다**(정의가 항상 앞처럼 보였다).
       그래서 오프셋을 버리고 **문장 번호 하나**로만 판정한다. 파일을 가로질러
       번호가 이어지므로 합본 순서와 같다. (돌연변이 대조 완료 — 아래 red 확인.)
    """
    def strip_comments(s):
        # `$$ … $$` 본문 안의 `--`는 주석이 아닐 수 있으나, 여기서는 정책만 보므로
        # 본문을 먼저 비우고 줄 주석을 지운다(`statement_language`와 같은 순서).
        s = re.sub(r"\$\$.*?\$\$", "$$$$", s, flags=re.S)
        return re.sub(r"--[^\n]*", "", s)

    defined = {}          # 이름 → 정의된 문장 번호
    used = []             # (문장 번호, 파일, 줄, 이름)
    seq = 0
    for path in files:
        sql = path.read_text(encoding="utf-8")
        for stmt in pglast.split(sql):
            seq += 1
            clean = strip_comments(stmt)

            m = re.search(
                r"create\s+(?:or\s+replace\s+)?function\s+public\.([a-z0-9_]+)",
                clean, flags=re.I,
            )
            if m:
                defined.setdefault(m.group(1).lower(), seq)
                continue

            if not re.match(r"\s*create\s+policy\b", clean, flags=re.I):
                continue
            # 줄 번호는 메시지용이다 — 판정에는 쓰지 않는다(위 🔴 참고).
            head = stmt.strip().splitlines()[0][:40]
            at = sql.find(head)
            line = sql[:at].count("\n") + 1 if at >= 0 else 0
            for fm in re.finditer(r"public\.([a-z0-9_]+)\s*\(", clean, flags=re.I):
                used.append((seq, path.name, line, fm.group(1).lower()))

    for seq_used, fname, line, fn in used:
        if fn not in defined:
            problems.append(
                f"{fname}:{line} 정책이 `public.{fn}()`을 부르는데 **정의가 어디에도 없다** —"
                " 오너의 붙여넣기가 여기서 멈춘다")
        elif defined[fn] > seq_used:
            problems.append(
                f"{fname}:{line} 정책이 `public.{fn}()`을 부르는데 **그 함수를 뒤에서 만든다** —"
                " create policy는 그 시점에 함수를 찾으므로 오너의 붙여넣기가 여기서 멈춘다"
                " (함수를 정책보다 앞으로 옮긴다)")
    return len(used)


def check_owner_docs_not_stale(files, problems) -> int:
    """오너가 실제로 붙여넣는 파일이 **마이그레이션과 어긋났는가.**

    🔴 **이 저장소가 같은 실수를 두 번 했다.** 마이그레이션을 새로 쓰고 커밋했는데
       `오너_실행/01_스키마_전체.sql`(합본)을 다시 만들지 않았다. 오너는 합본만
       붙여넣으니 **새 파일이 조용히 빠진다** — 그리고 앱은 그 표가 있다고 믿는다.

       두 번째는 더 나빴다: `오너_실행/README.md`가 기대값 표(`함수 13개`)를 **베껴
       들고 있었고**, 0007이 들어오면서 실제는 16개가 됐다. 그러면 오너는 **정상인
       서버를 ❌로 읽는다.** 그래서 README에서 그 표를 없애고 이 검사를 넣었다.

    ⚠️ 이 검사는 **파일 목록과 개수만** 본다. 내용이 맞는지는 못 본다.
    """
    combined = ROOT / "오너_실행" / "01_스키마_전체.sql"
    verify = ROOT / "오너_실행" / "02_적용확인.sql"
    readme = ROOT / "오너_실행" / "README.md"
    checked = 0

    if combined.is_file():
        text = combined.read_text(encoding="utf-8")
        listed = re.findall(r"^-- ▼ (\S+\.sql)$", text, flags=re.M)
        checked += 1
        # 🔴 0개면 PASS가 아니라 **정규식이 안 맞은 것**이다. 합본 머리말 형식이
        #    바뀌면 이 검사는 조용히 아무것도 안 본다.
        if not listed:
            problems.append(
                "01_스키마_전체.sql: `-- ▼ 파일명` 줄을 하나도 못 찾았다 —"
                " 합본 형식이 바뀌었다면 이 검사를 고친다(그냥 두면 검사가 안 본다)")
        else:
            missing = [f.name for f in files if f.name not in listed]
            extra = [n for n in listed if n not in {f.name for f in files}]
            if missing:
                problems.append(
                    f"01_스키마_전체.sql에 {missing}가 **빠져 있다** — 오너는 합본만"
                    " 붙여넣으므로 그 마이그레이션은 서버에 영원히 안 간다."
                    " `python3 오너_실행/build_합본.py`로 다시 만든다")
            if extra:
                problems.append(
                    f"01_스키마_전체.sql에 {extra}가 있는데 마이그레이션에는 없다 —"
                    " 파일을 지웠다면 합본도 다시 만든다")
    else:
        problems.append("01_스키마_전체.sql이 없다 — 오너가 붙여넣을 파일이다")

    # README가 기대값을 **베껴 들고 있지 않은가.** 원본은 02_적용확인.sql 한 곳이다.
    if readme.is_file() and verify.is_file():
        checked += 1
        rtext = readme.read_text(encoding="utf-8")
        # `02_적용확인.sql`이 스스로 들고 있는 항목 문구를 그대로 README에서 찾는다.
        labels = re.findall(
            r"select\s+\d+(?:\s+as\s+순서)?\s*,\s*'([^']+)'(?:\s+as\s+항목)?\s*,",
            verify.read_text(encoding="utf-8"))
        if not labels:
            problems.append(
                "02_적용확인.sql에서 항목 문구를 못 뽑았다 — 이 검사가 아무것도 안 본다")
        else:
            # 🔵 **처음엔 문구 전체를 README에서 찾았다. 그건 거의 아무것도 못 잡는다.**
            #    그렇게 해서 걸린 것은 `꽃 도감 2,057종` 하나였고 **그 숫자는 맞았다.**
            #    정작 낡아 있던 `표 6개`(실제 8) · `접근정책 17개`(실제 23)는
            #    문구가 `표(table) 8개` · `접근정책(policy) 23개`라서 **안 걸렸다.**
            #    즉 red가 떴지만 **틀린 이유로** 떴다.
            #
            # → 그래서 문구가 아니라 **`명사 + 개수`** 꼴을 README에서 직접 찾아,
            #   같은 명사에 대해 02 파일이 말하는 수와 **다르면** 잡는다.
            #   ⚠️ 이래야 "베껴 뒀는데 값이 낡은" 상태를 잡는다. 문구 일치는
            #      베낀 사실만 보고 **낡았는지는 안 본다.**
            expected = {}   # 명사 → 기대 개수
            for label in labels:
                m = re.match(r"([가-힣]+)(?:\([a-z]+\))?\s*(\d[\d,]*)개$", label)
                if m:
                    expected[m.group(1)] = int(m.group(2).replace(",", ""))
            if not expected:
                problems.append(
                    "02_적용확인.sql에서 `명사 N개` 꼴을 하나도 못 뽑았다 —"
                    " 항목 문구 형식이 바뀌었으면 이 검사를 고친다")
            # 🔵 **두 번째 red도 틀린 이유로 떴다.** 위 정규식은 낡은 수를 잘 찾았지만,
            #    찾은 6곳 중 **4곳이 "예전엔 이랬다"는 서술**이었다 — 이 문서가 스스로
            #    낡았던 일을 기록한 문장과, 내가 방금 쓴 경고문이다. 그걸 고치라고 하면
            #    **역사를 지우게 된다.**
            #
            #    가르는 기준: 이 문서에서 **백틱 안의 값은 인용**이고(`표 6개`처럼 남의
            #    말·옛 값을 옮긴 것), **백틱 밖의 값은 주장**이다(표 칸·불릿의 `표 6개:`).
            #    실제로 낡아서 오너를 속였던 4곳은 **전부 백틱 밖**이었다(표 칸과 불릿).
            #
            # ⚠️ 그래서 백틱 span을 **길이를 유지한 채** 지운다 — 행 번호가 밀리면
            #    보고가 엉뚱한 줄을 가리킨다.
            # ⚠️ **줄바꿈을 넘는 인용을 놓쳤다.** 처음 `[^`\n]*`로 썼는데, 마크다운
            #    인라인 코드는 **줄바꿈을 넘을 수 있다** — 긴 인용이 줄 끝에서 접히면
            #    면제가 안 되고 red가 뜬다(그 red를 문서 탓으로 읽고 문장을 고치려
            #    했다). 그래서 줄바꿈을 허용하되 **비탐욕 + 길이 상한**을 둔다:
            #    짝이 안 맞는 백틱 하나가 문서 절반을 삼켜 **면제가 너무 넓어지는**
            #    것을 막는다(그러면 진짜 낡은 수도 같이 면제된다).
            span = re.compile(r"`[^`]{0,200}?`", re.S)
            scan = span.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), rtext)
            quoted = len(span.findall(rtext))
            stale = []
            for noun, want in expected.items():
                for m in re.finditer(
                        rf"{re.escape(noun)}(?:\([a-z]+\))?\s*(\d[\d,]*)개", scan):
                    got = int(m.group(1).replace(",", ""))
                    if got != want:
                        line = scan[:m.start()].count("\n") + 1
                        stale.append(f"{line}행 `{noun} {m.group(1)}개`(실제 {want})")
            # 🔴 백틱 span이 0개면 위 면제가 **아무 일도 안 한 것**이다. 그때는 면제가
            #    있다고 착각하지 않도록 찍어 둔다(0이면 형식이 바뀐 것이다).
            if quoted == 0:
                problems.append(
                    "README.md에서 백틱 인용을 하나도 못 찾았다 — 인용/주장을 가르는"
                    " 면제가 안 돌고 있다. 형식이 바뀌었으면 이 검사를 고친다")
            if stale:
                problems.append(
                    f"README.md의 개수가 낡았다: {', '.join(stale)} —"
                    " 오너는 이걸 보고 **정상인 서버를 ❌로 읽는다**(두 번 있었던 일이다)."
                    " 개수를 지우고 `02_적용확인.sql`이 말하게 둔다")
    return checked


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

    # 파일을 가로질러 본다 — 합본은 번호순으로 한 번에 도므로 앞뒤가 파일 경계를 넘는다.
    policy_calls = check_function_defined_before_use(files, problems)

    # 🔴 오너가 **실제로 붙여넣는 파일**이 이 폴더와 어긋났는가. 문법이 다 맞아도
    #    합본에서 빠지면 서버에 안 간다 — 그건 문법 검사로 원리상 못 잡는다.
    doc_checks = check_owner_docs_not_stale(files, problems)

    if problems:
        print(f"\n🔴 문제 {len(problems)}건")
        for p in problems:
            print("   -", p)
        return 1
    # 🔴 **센 개수를 찍는다.** 정책 안 함수 호출이 0개면 그건 PASS가 아니라
    #    정규식이 안 맞아서 **아무것도 안 본 것**이다(이 저장소가 반복해 당한 실패).
    print(f"\n판정: PASS — {len(files)}개 파일 · plpgsql {total_blocks}블록 ·"
          f" 정책이 부르는 함수 {policy_calls}곳(전부 앞에서 정의됨) ·"
          f" 오너 문서 {doc_checks}개 대조됨")
    return 0


if __name__ == "__main__":
    sys.exit(main())
