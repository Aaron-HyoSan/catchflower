#!/usr/bin/env python3
"""`revoke execute … from anon`이 아무것도 막지 않는다는 것을 **로컬에서 재현**하고,
`제안/0010_제안_함수권한_잠금.sql`이 실제로 막는지 **검증**한다. (진행 (79))

왜 이 파일이 있나
─────────────────
실서버 실측은 "열려 있다"까지만 말해 준다(공개 키 · 세션 없이 랭킹 함수 4개가 200).
🔴 **거기서 멈추면 원인이 추측이고, 고침은 검증되지 않은 채로 오너에게 간다.**
그래서 같은 순서를 로컬 Postgres에 다시 올려 두 가지를 따로 본다:

  ① 우리가 쓴 순서(`create` → `grant authenticated` → `revoke anon`)로 만들면
     anon이 **실제로 함수를 호출할 수 있다** — `revoke`는 성공했는데 아무 일도 없다.
  ② `revoke … from public`을 더하면 anon만 막히고 **authenticated는 그대로 돈다.**

🔴 **`has_function_privilege`만 보지 않고 `set role`로 실제 호출까지 한다.**
   권한 검사는 실행 시점에 걸리므로, 카탈로그만 읽으면 "막혔다고 적혀 있다"와
   "막힌다"를 구분하지 못한다 — 이 저장소에서 정책을 그렇게 믿었다가 세 곳이
   뚫려 있었다(0001 7-3절).

⚠️ **프로덕션에 대고는 재지 않는다.** 남의 계정을 훑어 얼마나 새는지 세는 것은
   (정당하게) 막혔다. 여기서 재는 것은 **기전**이고, 그건 빈 DB로 충분하다.

⚠️ 실제 함수를 복사해 오지 않고 **같은 모양의 최소 함수**를 쓴다. 재는 것은 함수
   본문이 아니라 `grant`/`revoke`의 동작이므로, 본문을 베끼면 함수가 바뀔 때마다
   이 파일이 낡는다.

    python3 supabase/_tools/measure_0010_grants.py
"""

import sys
import tempfile

SIG = "public.demo_ranking(uuid, timestamptz)"


def main() -> int:
    try:
        import pgserver
        import psycopg
    except ImportError as e:
        print(f"🔴 {e} — 이 검사는 pgserver + psycopg가 있어야 돈다")
        return 2

    server = pgserver.get_server(tempfile.mkdtemp(prefix="pg0010_"))
    uri = server.get_uri()

    def as_role(role: str) -> tuple[bool, str]:
        """`role`로 실제 호출해 본다. (돌았는가, 메시지)"""
        with psycopg.connect(uri, autocommit=True) as c, c.cursor() as q:
            q.execute(f"set role {role}")
            try:
                q.execute("select public.demo_ranking(gen_random_uuid())")
                return True, str(q.fetchone()[0])
            except psycopg.Error as e:
                return False, str(e).splitlines()[0]

    with psycopg.connect(uri, autocommit=True) as c, c.cursor() as q:
        q.execute("create role anon; create role authenticated; create role service_role;")
        q.execute(
            """
            create or replace function public.demo_ranking(
              target_user uuid, ts timestamptz default now()
            ) returns int language sql stable as $$ select 1 $$;
            """
        )

        # ── ① 우리 SQL이 쓴 그대로 ─────────────────────────────────
        q.execute(f"grant  execute on function {SIG} to authenticated;")
        q.execute(f"revoke execute on function {SIG} from anon;")

        def catalog() -> tuple[bool, bool]:
            q.execute(
                f"""select has_function_privilege('anon','{SIG}','execute'),
                           has_function_privilege('authenticated','{SIG}','execute')"""
            )
            return q.fetchone()

        before = catalog()
        anon_ran, anon_msg = as_role("anon")
        print(f"① grant authenticated + revoke anon → 카탈로그: anon={before[0]} authenticated={before[1]}")
        print(f"   anon이 실제로 호출 → {'🔴 돌았다 (' + anon_msg + ')' if anon_ran else '막혔다: ' + anon_msg}")

        # ── ② 0010 제안 ───────────────────────────────────────────
        q.execute(f"revoke execute on function {SIG} from public;")
        q.execute(f"grant  execute on function {SIG} to authenticated, service_role;")

        after = catalog()
        print(f"② + revoke from public         → 카탈로그: anon={after[0]} authenticated={after[1]}")
        for role in ("anon", "authenticated", "service_role"):
            ran, msg = as_role(role)
            print(f"   {role:14s} → {'돌았다' if ran else '막혔다'}: {msg}")

    ok = before[0] and before[1] and anon_ran and not after[0] and after[1]
    print(
        "판정: "
        + (
            "PASS — 재현했고(anon이 실제로 돌았다) 0010이 막는다"
            if ok
            else "🔴 FAIL — 예상과 다르다. 결론을 다시 쓴다"
        )
    )
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
