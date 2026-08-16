#!/usr/bin/env python3
"""탈퇴 계정 정리(`제안/0011`)가 **무엇을 지우고 무엇을 남기는지** 로컬에서 잰다.

왜 이 파일이 있나
─────────────────
`AccountDeletionService`는 탈퇴할 때 `public.users`를 **soft delete** 한다
(`deleted_at`을 찍고 닉네임·지역을 비운다). 그러면 서버에 두 가지가 남는다:

  ① `auth.identities` — **카카오 계정과 우리 uuid를 잇는 줄.** 이게 남아 있으면
     "탈퇴했다"고 말하면서 카카오 계정과의 연결은 그대로 있는 셈이다.
  ② `auth.users` · `public.users` 껍데기 — uuid와 `deleted_at`만 남은 묘비.

개인정보 처리방침 6항은 ①을 "정리 작업 시 파기"한다고 적었고, 같은 항에서
**다른 사람 사진에 남긴 댓글 본문은 남는다**고도 적었다. 이 둘은 서로 잡아당긴다 —
`0001`이 `public.users.id → auth.users(id) on delete cascade`이고
`0007`이 `comments.user_id → public.users(id) on delete cascade`라서
**`auth.users` 한 줄을 지우면 댓글이 같이 사라진다.**

🔴 그래서 여기서 재는 것은 세 가지다. 전부 "믿는다"가 아니라 **돌려 본다**:

  A. `delete from auth.users` 하면 **남의 사진에 달린 내 댓글과 신고 기록이 사라진다**
     (= 처방침 6항 나를 어긴다). 그리고 오류도 경고도 없다.
  B. 정리 전에는 **같은 카카오 계정으로 다시 시작할 수 없다** —
     `auth.identities`의 `(provider, provider_id)` 유일 제약에 걸린다.
     (앱에서는 로그인 실패로 보이고, 원인은 화면에 안 나온다.)
  C. `0011`의 순서(identities·sessions만 지우고 묘비는 남긴다)로 하면
     **댓글·신고는 남고 카카오 재연결은 된다.**

🔴 **C는 제안 파일의 2·3절을 그대로 읽어서 돌린다.** 여기에 SQL을 베껴 쓰면 파일이
   바뀔 때 이 검사가 낡은 것을 재게 되고, 그건 초록인 채로 틀린다 — 이 저장소가
   "오너가 붙여넣는 문서는 어긋난다"로 이미 겪었다.

⚠️ **여기 만드는 `auth` 스키마는 Supabase의 진짜 스키마가 아니다.** 재는 것은
   *FK 연쇄와 유일 제약의 동작*이므로 그 세 컬럼만 같은 모양으로 만든다. 진짜
   서버에서 `postgres` 롤이 `auth.identities`를 지울 권한이 있는지는 **여기서 못
   잰다** — 그건 0011 1절이 오너 쪽에서 먼저 확인하게 되어 있다.

    python3 supabase/_tools/measure_0011_purge.py
"""

import sys
import tempfile

# 실제 스키마에서 베껴 온 것은 **FK와 유일 제약뿐이다**(위 주석).
SCHEMA = """
create schema if not exists auth;

create table auth.users (
  id           uuid primary key,
  created_at   timestamptz not null default now(),
  is_anonymous boolean     not null default true
);

-- Supabase auth의 실제 제약: (provider, provider_id)가 유일하다.
create table auth.identities (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  provider    text not null,
  provider_id text not null,
  unique (provider, provider_id)
);

create table auth.sessions (
  id      uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade
);

-- ⚠️ 실제 GoTrue처럼 `user_id`를 **문자열**로 둔다. 0011이 여기서 uuid를 그대로
--    비교하면 조용히 0행을 지우므로, 그 분기를 실제로 지나가게 만든다.
create table auth.refresh_tokens (
  id         bigserial primary key,
  user_id    varchar(255),
  session_id uuid references auth.sessions(id) on delete cascade
);

-- 아래는 0001 · 0007에서 그대로 가져온 FK 규칙이다.
create table public.users (
  id                uuid primary key references auth.users(id) on delete cascade,
  nickname          text not null,
  region_name       text,
  dong_code         text,
  gu_code           text,
  region_changed_at timestamptz,
  phone_hash        text,
  age_confirmed_at  timestamptz,
  deleted_at        timestamptz
);

create table public.discoveries (
  id      uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade
);

create table public.comments (
  id           uuid primary key default gen_random_uuid(),
  discovery_id uuid not null references public.discoveries(id) on delete cascade,
  user_id      uuid not null references public.users(id) on delete cascade,
  body         text not null,
  deleted_at   timestamptz
);

create table public.reports (
  id           uuid primary key default gen_random_uuid(),
  discovery_id uuid not null references public.discoveries(id) on delete cascade,
  reporter_id  uuid not null references public.users(id) on delete cascade,
  reason       text not null
);

-- 3절 확인 쿼리가 읽는다.
create table public.friendships (
  id           uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.users(id) on delete cascade,
  addressee_id uuid not null references public.users(id) on delete cascade
);
"""

# 탈퇴자(me)와 남는 사람(other). me가 other의 사진에 댓글과 신고를 남겼다.
SEED = """
insert into auth.users (id) values
  ('11111111-1111-1111-1111-111111111111'),
  ('22222222-2222-2222-2222-222222222222');
insert into auth.identities (user_id, provider, provider_id) values
  ('11111111-1111-1111-1111-111111111111', 'kakao', 'kakao-4242');
insert into auth.sessions (user_id) values
  ('11111111-1111-1111-1111-111111111111');
-- ⚠️ **세션에 매달리지 않은** 리프레시 토큰이다(session_id가 null). 연쇄로는 안 지워지므로
--    0011의 `user_id::text` 분기를 지나가야만 사라진다.
insert into auth.refresh_tokens (user_id, session_id) values
  ('11111111-1111-1111-1111-111111111111', null);
insert into public.users (id, nickname, region_name, phone_hash, age_confirmed_at) values
  ('11111111-1111-1111-1111-111111111111', '꽃친구a1b2', '중동', 'sha256:abc', now()),
  ('22222222-2222-2222-2222-222222222222', '꽃친구c3d4', '중동', null, now());
insert into public.discoveries (id, user_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222');
insert into public.comments (discovery_id, user_id, body) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
   '이 꽃 우리 동네에도 있어요');
insert into public.reports (discovery_id, reporter_id, reason) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'spam');
"""

# 앱이 실제로 보내는 탈퇴 PATCH(= AccountDeletionService 5단계 중 마지막).
SOFT_DELETE = """
update public.users
   set deleted_at = 'now', nickname = '', region_name = null
 where id = '11111111-1111-1111-1111-111111111111';
"""

ME = "11111111-1111-1111-1111-111111111111"


PROPOSAL = "프로젝트 맥락/제안/0011_제안_탈퇴계정_정리.sql"


def sections() -> tuple[str, str]:
    """제안 파일에서 **2절(적용)** 과 **3절(확인)** 을 잘라 온다.

    🔴 파일에서 읽는다 — 여기 SQL을 베껴 쓰면 파일이 바뀔 때 낡은 것을 재게 되고
       그건 초록인 채로 틀린다.
    """
    import pathlib

    root = pathlib.Path(__file__).resolve().parents[2]
    text = (root / PROPOSAL).read_text(encoding="utf-8")
    marks = ["--  2절", "--  3절", "--  4절"]
    at = []
    for m in marks:
        i = text.find(m)
        if i < 0:
            raise SystemExit(f"🔴 제안 파일에서 `{m}` 표시를 못 찾았다 — 절 제목이 바뀌었으면 이 파일을 고친다")
        at.append(i)
    apply_sql, verify_sql = text[at[0] : at[1]], text[at[1] : at[2]]
    for name, sql in (("2절", apply_sql), ("3절", verify_sql)):
        body = "\n".join(l for l in sql.splitlines() if not l.strip().startswith("--"))
        if len(body.strip()) < 100:
            raise SystemExit(f"🔴 {name}에서 SQL을 못 꺼냈다({len(body.strip())}자) — 주석만 읽었다")
    return apply_sql, verify_sql


def counts(q) -> dict:
    out = {}
    for name, sql in [
        ("comments", "select count(*) from public.comments"),
        ("reports", "select count(*) from public.reports"),
        ("public.users", "select count(*) from public.users"),
        ("auth.users", "select count(*) from auth.users"),
        ("auth.identities", "select count(*) from auth.identities"),
        ("auth.sessions", "select count(*) from auth.sessions"),
        ("auth.refresh_tokens", "select count(*) from auth.refresh_tokens"),
    ]:
        q.execute(sql)
        out[name] = q.fetchone()[0]
    return out


def relink_possible(q) -> tuple[bool, str]:
    """새 uuid가 **같은 카카오 계정**을 연결할 수 있는가 (= 다시 시작할 수 있는가)."""
    # ⚠️ **재고 나서 되돌린다**(`begin` … `rollback`). 안 되돌리면 ③에서 재연결이
    #    이미 되어 있는 상태를 재게 되어 검사가 스스로 통과한다.
    q.execute("begin")
    try:
        q.execute(
            "insert into auth.users (id) values ('33333333-3333-3333-3333-333333333333') "
            "on conflict do nothing"
        )
        q.execute(
            "insert into auth.identities (user_id, provider, provider_id) "
            "values ('33333333-3333-3333-3333-333333333333', 'kakao', 'kakao-4242')"
        )
        return True, "연결됐다"
    except Exception as e:  # psycopg.Error
        return False, str(e).splitlines()[0]
    finally:
        q.execute("rollback")


def main() -> int:
    try:
        import psycopg  # noqa: F401
        import pgserver
    except ImportError as e:
        print(f"🔴 {e} — 이 검사는 pgserver + psycopg가 있어야 돈다")
        return 2
    import psycopg

    server = pgserver.get_server(tempfile.mkdtemp(prefix="pg0011_"))
    uri = server.get_uri()
    verdicts = []

    def fresh(c):
        with c.cursor() as q:
            q.execute("drop schema if exists auth cascade")
            q.execute(
                "drop table if exists public.reports, public.comments, public.friendships, "
                "public.discoveries, public.users cascade"
            )
            q.execute("drop function if exists public.purge_deleted_accounts(interval)")
            for role in ("anon", "authenticated", "service_role"):
                q.execute(f"drop role if exists {role}")
            q.execute(SCHEMA)
            q.execute(SEED)
            q.execute(SOFT_DELETE)

    with psycopg.connect(uri, autocommit=True) as c:
        # ── A. auth.users를 지우면 무엇이 같이 사라지나 ──────────────
        fresh(c)
        with c.cursor() as q:
            before = counts(q)
            ok, why = relink_possible(q)
            q.execute(f"delete from auth.users where id = '{ME}'")
            after = counts(q)
        print("① `delete from auth.users` — 오류 없이 성공한다")
        print(f"   탈퇴(soft delete) 직후: {before}")
        print(f"   auth.users 삭제 후    : {after}")
        lost = [k for k in before if after[k] < before[k]]
        print(f"   줄어든 것: {', '.join(lost)}")
        a_ok = after["comments"] == 0 and after["reports"] == 0
        print(
            "   🔴 남의 사진에 달린 댓글과 신고 기록이 **연쇄로 사라졌다**"
            if a_ok
            else "   ⚠️ 연쇄가 일어나지 않았다 — FK 규칙이 바뀌었으면 이 파일을 고친다"
        )
        verdicts.append(("A 연쇄 삭제를 재현했다", a_ok))

        # ── B. 정리 전에는 같은 카카오 계정으로 다시 시작할 수 없다 ──
        print()
        print("② 정리하기 전에 같은 카카오 계정으로 다시 시작해 본다")
        print(f"   soft delete 직후: {'된다' if ok else '막힌다'} — {why}")
        verdicts.append(("B 정리 전 재연결이 막힌다", not ok))

        # ── C. 제안 파일의 2절·3절을 **그대로** 돌린다 ───────────────
        fresh(c)
        apply_sql, verify_sql = sections()
        with c.cursor() as q:
            q.execute("create role anon; create role authenticated; create role service_role;")
            q.execute(apply_sql)   # 2절 — 함수 생성 + 권한
            q.execute(verify_sql)  # 3절 — 확인 쿼리 + 실제 정리 실행
            after = counts(q)
            ok2, why2 = relink_possible(q)
            q.execute(f"select nickname, region_name, phone_hash from public.users where id = '{ME}'")
            tomb = q.fetchone()
            q.execute(
                "select has_function_privilege('anon', "
                "'public.purge_deleted_accounts(interval)', 'execute'), "
                "has_function_privilege('service_role', "
                "'public.purge_deleted_accounts(interval)', 'execute')"
            )
            anon_can, service_can = q.fetchone()
        print()
        print("③ 제안 파일의 2절·3절을 그대로 실행했다 (오류 없이 끝났다)")
        print(f"   정리 후: {after}")
        print(f"   함수 권한 — anon: {anon_can} (기대 False) · service_role: {service_can} (기대 True)")
        print(f"   묘비 행(nickname, region_name, phone_hash): {tomb}")
        print(f"   같은 카카오 계정으로 다시 시작: {'된다' if ok2 else '막힌다'} — {why2}")
        c_ok = (
            after["comments"] == 1
            and after["reports"] == 1
            and after["auth.identities"] == 0
            and after["auth.sessions"] == 0
            and after["auth.refresh_tokens"] == 0
            and after["public.users"] == 2
            and ok2
            and tomb == ("", None, None)
        )
        verdicts.append(("C 댓글·신고는 남고 카카오 재연결은 된다", c_ok))
        verdicts.append(("C-2 anon은 정리 함수를 못 부른다", not anon_can and service_can))

    print()
    for name, ok in verdicts:
        print(f"   {'🔵' if ok else '🔴'} {name}")
    passed = all(ok for _, ok in verdicts)
    print()
    print(f"판정: {'PASS' if passed else 'FAIL'}")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
