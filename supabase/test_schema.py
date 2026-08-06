# -*- coding: utf-8 -*-
"""스키마 실행 테스트 — **실제 Postgres에 돌려 본다.**

**이 파일이 있는 이유.** SQL은 문법이 맞아도 규칙을 안 지킬 수 있다.
`create policy`가 붙었다는 것과 **남의 기록이 실제로 안 보인다**는 것은 다른 말이고,
`unique index`가 있다는 것과 **B-5가 실제로 막힌다**는 것도 다른 말이다.
캐치플라워에서 이미 겪었다 — `GamePolicy`에 숫자만 있고 부르는 곳이 0건이었다.
**정의는 구현이 아니다.**

Supabase 대시보드에 붙여넣기 전에 여기서 돌린다. Supabase 자체는 없으므로
`auth.uid()`·`auth.users`·`anon`/`authenticated` 역할만 흉내 낸다 (스키마가 의존하는 전부다).

⚠️ **`psql` 문자열 출력으로 판정하지 않는다.** 처음에 그렇게 썼다가 38개가
가짜로 실패했다 — `psql`은 오류에도 예외를 안 내고, `SET` 같은 출력이 값에 섞인다.
**드라이버로 붙어서 예외와 행을 직접 본다.**

실행  python3 supabase/test_schema.py
필요  pip3 install pgserver "psycopg[binary]"
"""

import os
import sys
import tempfile
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
MIGRATIONS = os.path.join(HERE, "migrations")

# Supabase가 주는 것 중 **스키마가 실제로 의존하는 것만** 흉내 낸다.
# 전체를 재현하려 들면 테스트가 Supabase 버전에 묶인다.
BOOTSTRAP = """
create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key default gen_random_uuid(),
  raw_user_meta_data jsonb default '{}'::jsonb
);
-- 실제 Supabase는 JWT에서 꺼낸다. 테스트는 `set request.jwt.claim.sub`로 사용자를 바꾼다.
create or replace function auth.uid() returns uuid language sql stable as
  $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
do $$ begin create role anon;          exception when duplicate_object then null; end $$;
do $$ begin create role authenticated; exception when duplicate_object then null; end $$;
grant usage on schema public to anon, authenticated;
grant usage on schema auth to anon, authenticated;
-- Supabase는 이 권한을 기본으로 준다. RLS가 그 위에서 행을 가른다 —
-- **grant가 없으면 RLS를 시험하는 게 아니라 권한 부족을 보는 것**이 된다.
grant select, insert, update, delete on all tables in schema public to authenticated;
grant select on all tables in schema public to anon;
"""

PASS, FAIL = [], []


class DB:
    """테스트용 연결. **역할을 바꿔 RLS를 실제로 태운다.**

    `set role authenticated`가 핵심이다 — 슈퍼유저는 RLS를 통째로 우회하므로
    역할을 안 바꾸면 정책을 시험하는 게 아니라 무시하는 것이 된다.
    """

    def __init__(self, uri):
        import psycopg
        self.psycopg = psycopg
        self.uri = uri

    def _conn(self, role=None, uid=None):
        c = self.psycopg.connect(self.uri, autocommit=True)
        if uid is not None:
            # set_config는 리터럴이 아니라 파라미터를 받는다 — uuid를 문자열로 붙이지 않는다.
            c.execute("select set_config('request.jwt.claim.sub', %s, false)", (str(uid),))
        if role:
            c.execute("set role " + role)
        return c

    def run(self, sql, role=None, uid=None, params=None):
        """실행하고 (행 리스트, 영향 행 수)를 준다. 오류는 예외로 올라온다."""
        with self._conn(role, uid) as c:
            cur = c.execute(sql, params)
            rows = cur.fetchall() if cur.description else []
            return rows, cur.rowcount

    def one(self, sql, role=None, uid=None, params=None):
        rows, _ = self.run(sql, role, uid, params)
        return rows[0][0] if rows and rows[0] else None

    def rows(self, sql, role=None, uid=None, params=None):
        return self.run(sql, role, uid, params)[0]

    def blocked(self, sql, role="authenticated", uid=None, params=None):
        """**막혔는가.** 예외로 막히거나, RLS가 0행으로 막는다.

        이 구분이 중요하다 — `update ... where`가 RLS에 걸리면 `UPDATE 0`이 온다.
        성공처럼 보이지만 아무것도 안 바뀐 것이다. 놓치면 정책이 없어도 통과한다.
        """
        try:
            rows, n = self.run(sql, role, uid, params)
        except Exception:
            return True
        if rows:
            return False
        return n <= 0

    def rejects(self, sql, params=None):
        """제약이 이 값을 **거부해야** 한다 (superuser로 넣어 RLS와 분리한다)."""
        try:
            self.run(sql, params=params)
            return False
        except Exception:
            return True

    def accepts(self, sql, params=None):
        try:
            self.run(sql, params=params)
            return True
        except Exception as e:
            print("      (거부됨: %s)" % str(e).strip().split("\n")[0][:160])
            return False


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  ✅ " if cond else "  ❌ ") + name + (("  — %r" % (detail,)) if detail and not cond else ""))


def main():
    try:
        import pgserver  # noqa
        import psycopg   # noqa
    except ImportError as e:
        print("의존성이 없다: pip3 install pgserver \"psycopg[binary]\"  (%s)" % e)
        return 2

    import pgserver
    d = tempfile.mkdtemp(prefix="catchflower_pg_")
    server = pgserver.get_server(d)
    db = DB(server.get_uri())

    print("\n[0] 부트스트랩 · 마이그레이션 실행")
    db.run(BOOTSTRAP)
    for f in sorted(os.listdir(MIGRATIONS)):
        if not f.endswith(".sql"):
            continue
        with open(os.path.join(MIGRATIONS, f), encoding="utf-8") as fh:
            db.run(fh.read())
        print("  ✅ %s" % f)
    # 0001이 만든 테이블에 대한 grant를 다시 준다 (부트스트랩 시점엔 없었다).
    db.run("grant select, insert, update, delete on all tables in schema public to authenticated;"
           "grant select on all tables in schema public to anon;")

    # ── 1. 도감 마스터 ───────────────────────────────────────
    print("\n[1] 도감 마스터 200종")
    check("200종이 들어갔다", db.one("select count(*) from public.flowers") == 200)
    dist = db.rows("select ai_difficulty::text, count(*) from public.flowers group by 1")
    check("난이도 분포가 계약(하33·중116·상51)과 같다",
          dict(dist) == {"low": 33, "mid": 116, "high": 51}, dist)
    check("개화월이 빈 종이 없다",
          db.one("select count(*) from public.flowers "
                 "where array_length(bloom_months,1) is null") == 0)
    check("연말 랩어라운드가 풀려 있다 (1월에 피는 종이 있다)",
          db.one("select count(*) from public.flowers where 1 = any(bloom_months)") >= 1)
    check("7월 개화 100종 (계약 1-2 표와 일치)",
          db.one("select count(*) from public.flowers where 7 = any(bloom_months)") == 100)
    check("도감 밖 id를 넣으면 거부된다", db.rejects("""
        insert into public.flowers (id,name,scientific_name,family,bloom_months,bloom_label,
          season,color,rarity,habitat,ai_difficulty)
        values (201,'x','X x','과','{5}','5월','spring','노랑','common','들','low')"""))
    check("개화월이 빈 종을 넣으면 거부된다", db.rejects("""
        insert into public.flowers (id,name,scientific_name,family,bloom_months,bloom_label,
          season,color,rarity,habitat,ai_difficulty)
        values (150,'x','X x','과','{}','5월','spring','노랑','common','들','low')"""))

    # ── 2. 가입 트리거 ───────────────────────────────────────
    print("\n[2] 가입하면 프로필이 자동으로 생긴다")
    a, b, c = (str(uuid.uuid4()) for _ in range(3))
    db.run("insert into auth.users (id, raw_user_meta_data) values "
           "(%s,'{\"name\":\"가영\"}'::jsonb), (%s,'{}'::jsonb), (%s,'{\"name\":\"다온\"}'::jsonb)",
           params=(a, b, c))
    check("auth.users 3명 → public.users 3명", db.one("select count(*) from public.users") == 3)
    check("소셜 이름을 쓴다",
          db.one("select nickname from public.users where id=%s", params=(a,)) == "가영")
    check("이름이 없으면 빈 닉네임을 두지 않는다",
          (db.one("select nickname from public.users where id=%s", params=(b,)) or "").startswith("꽃친구"))

    db.run("update public.users set nickname='나린', dong_code='1144012600', gu_code='11440' where id=%s",
           params=(b,))
    db.run("update public.users set dong_code='1144012600', gu_code='11440' where id=%s", params=(a,))
    db.run("update public.users set dong_code='1168010100', gu_code='11680' where id=%s", params=(c,))

    # ── 3. RLS 프로필 ────────────────────────────────────────
    print("\n[3] RLS — 프로필")
    check("본인 프로필은 보인다",
          db.one("select nickname from public.users where id=%s", "authenticated", a, (a,)) == "가영")
    check("남의 프로필 행은 안 보인다",
          db.one("select count(*) from public.users where id=%s", "authenticated", a, (b,)) == 0)
    check("남의 닉네임은 public_profiles로 본다",
          db.one("select nickname from public.public_profiles where id=%s",
                 "authenticated", a, (b,)) == "나린")
    cols = [r[0] for r in db.rows("select column_name from information_schema.columns "
                                  "where table_name='public_profiles'")]
    check("public_profiles에 phone_hash가 없다", "phone_hash" not in cols, cols)
    check("남의 프로필을 수정할 수 없다",
          db.blocked("update public.users set nickname='해킹' where id=%s",
                     "authenticated", a, (b,)))

    # ── 4. 발견 기록 · B-5 ───────────────────────────────────
    print("\n[4] 발견 기록 · B-5 하루 중복 제한")

    def disc(uid, flower, day, lat=37.5665, lng=126.9780, vis="public"):
        return ("insert into public.discoveries "
                "(user_id, flower_id, lat, lng, place_name, dong_code, gu_code, visibility, "
                " ai_confidence, ai_picked_rank, is_first_discovery, captured_at) values "
                "('%s', %d, %s, %s, '서울숲', '1144012600', '11440', '%s', 0.8, 1, true, '%s')"
                % (uid, flower, lat, lng, vis, day))

    db.run(disc(a, 1, "2026-07-01 10:00+09"))
    check("발견 1건이 들어갔다", db.one("select count(*) from public.discoveries") == 1)
    check("같은 종·같은 자리·같은 날은 두 번 안 들어간다 (B-5)",
          db.rejects(disc(a, 1, "2026-07-01 18:00+09")))
    check("장소를 옮기면 같은 날도 들어간다 (B-5 권고 ③)",
          db.accepts(disc(a, 1, "2026-07-01 19:00+09", lat=37.6000, lng=127.0500)))
    check("다음 날은 같은 자리도 들어간다", db.accepts(disc(a, 1, "2026-07-02 10:00+09")))
    check("신뢰도가 1을 넘으면 거부된다 (퍼센트로 넣는 실수)",
          db.rejects("insert into public.discoveries (user_id,flower_id,ai_confidence,captured_at) "
                     "values ('%s', 5, 80, now())" % a))
    check("순위 4를 넣으면 거부된다 (후보는 3개다)",
          db.rejects("insert into public.discoveries "
                     "(user_id,flower_id,ai_confidence,ai_picked_rank,captured_at) "
                     "values ('%s', 6, 0.5, 4, now())" % a))
    check("좌표 한쪽만 있으면 거부된다",
          db.rejects("insert into public.discoveries (user_id,flower_id,lat,ai_confidence,captured_at) "
                     "values ('%s', 7, 37.5, 0.5, now())" % a))
    check("도감에 없는 flower_id는 거부된다",
          db.rejects("insert into public.discoveries (user_id,flower_id,ai_confidence,captured_at) "
                     "values ('%s', 999, 0.5, now())" % a))
    check("한 줄 남기기 40자를 넘으면 거부된다",
          db.rejects("insert into public.discoveries "
                     "(user_id,flower_id,ai_confidence,captured_at,note) "
                     "values ('%s', 8, 0.5, now(), repeat('가',41))" % a))
    check("좌표 없는 기록은 하루에 여러 번 된다 (실내·GPS 실패를 막지 않는다)",
          db.accepts("insert into public.discoveries (user_id,flower_id,ai_confidence,captured_at) "
                     "values ('%s', 9, 0.5, '2026-07-03 10:00+09'), "
                     "('%s', 9, 0.5, '2026-07-03 11:00+09')" % (a, a)))

    # ── 4-2. 하루 경계가 한국 날짜인가 ───────────────────────
    #
    # **여기가 조용히 틀리는 자리였다.** UTC로 세면 07-01 08:00 KST가
    # 06-30 23:00 UTC라 **아침 산책이 어제 기록**이 된다. 같은 아침에 두 번
    # 찍으면 하루 1회 제한이 엉뚱하게 걸리고, 사용자는 이유를 알 수 없다.
    print("\n[4-2] B-5의 '하루'는 한국 날짜다 (UTC로 세면 아침이 전날이 된다)")
    check("오전 8시 KST가 그날로 잡힌다 (UTC로는 전날 23시)",
          db.accepts(disc(c, 30, "2026-07-10 08:00+09", lat=35.1, lng=129.1)) and
          db.one("select captured_date::text from public.discoveries "
                 "where user_id='%s' and flower_id=30" % c) == "2026-07-10")
    check("같은 아침 오전 8시·9시는 같은 날로 막힌다",
          db.rejects(disc(c, 30, "2026-07-10 09:00+09", lat=35.1, lng=129.1)))
    check("밤 11시(KST)는 다음 날이 아니다",
          db.accepts(disc(c, 31, "2026-07-10 23:00+09", lat=35.1, lng=129.1)) and
          db.one("select captured_date::text from public.discoveries "
                 "where user_id='%s' and flower_id=31" % c) == "2026-07-10")
    # 클라이언트가 날짜를 직접 넣어 제한을 우회할 수 없어야 한다.
    db.run("insert into public.discoveries "
           "(user_id,flower_id,lat,lng,ai_confidence,captured_at,captured_date) "
           "values ('%s', 32, 35.2, 129.2, 0.5, '2026-07-11 10:00+09', '1999-01-01')" % c)
    check("클라이언트가 보낸 captured_date는 무시된다 (우회 방지)",
          db.one("select captured_date::text from public.discoveries "
                 "where user_id='%s' and flower_id=32" % c) == "2026-07-11")

    # ── 5. 공개 범위 ─────────────────────────────────────────
    print("\n[5] RLS — 공개 범위 (private / friends / public)")
    db.run(disc(b, 20, "2026-07-05 10:00+09", lat=37.55, lng=126.95, vis="private"))
    db.run(disc(b, 21, "2026-07-05 10:00+09", lat=37.56, lng=126.96, vis="friends"))
    db.run(disc(b, 22, "2026-07-05 10:00+09", lat=37.57, lng=126.97, vis="public"))

    def seen(uid, flower):
        return db.one("select count(*) from public.discoveries where flower_id=%s",
                      "authenticated", uid, (flower,))

    check("남의 private은 안 보인다", seen(a, 20) == 0)
    check("친구가 아니면 friends도 안 보인다", seen(a, 21) == 0)
    check("남의 public은 보인다", seen(a, 22) == 1)
    check("본인 private은 본인에게 보인다", seen(b, 20) == 1)

    # ── 6. C-2 상호 수락 ─────────────────────────────────────
    print("\n[6] C-2 상호 수락 — 수락 전에는 friends가 안 보인다")
    db.run("insert into public.friendships (requester_id, addressee_id) values (%s,%s)",
           "authenticated", a, (a, b))
    check("요청이 들어갔다", db.one("select state::text from public.friendships") == "pending")
    check("pending 상태에서는 friends 기록이 안 보인다", seen(a, 21) == 0)
    check("요청한 쪽이 스스로 수락할 수 없다 (단방향 팔로우 방지)",
          db.blocked("update public.friendships set state='accepted' where requester_id=%s",
                     "authenticated", a, (a,)))
    db.run("update public.friendships set state='accepted', responded_at=now() "
           "where requester_id=%s and addressee_id=%s", "authenticated", b, (a, b))
    check("받은 쪽이 수락하면 friends 기록이 보인다", seen(a, 21) == 1)
    check("수락해도 private은 여전히 안 보인다", seen(a, 20) == 0)
    check("자기 자신과 친구가 될 수 없다",
          db.rejects("insert into public.friendships (requester_id, addressee_id) "
                     "values ('%s','%s')" % (a, a)))

    # ── 7. C-1 신고 ──────────────────────────────────────────
    print("\n[7] C-1 신고 3회 누적 자동 숨김")
    did = db.one("select id from public.discoveries where flower_id=22")
    for r in (a, c):
        db.run("insert into public.reports (discovery_id, reporter_id) values (%s,%s)",
               params=(did, r))
    check("2회 신고로는 아직 보인다", seen(a, 22) == 1)
    check("같은 사람이 같은 기록을 두 번 신고할 수 없다 (혼자 3회 발동 방지)",
          db.rejects("insert into public.reports (discovery_id, reporter_id) "
                     "values ('%s','%s')" % (did, a)))
    db.run("insert into public.reports (discovery_id, reporter_id) values (%s,%s)", params=(did, b))
    check("3명이 신고하면 숨는다", seen(a, 22) == 0)
    check("신고돼도 본인에게는 보인다 (내 도감이 사라지면 안 된다)", seen(b, 22) == 1)
    db.run("delete from public.reports")

    # ── 8. 차단 ──────────────────────────────────────────────
    print("\n[8] C-1 차단 — 지도에서도 안 보인다")
    db.run("insert into public.blocks (blocker_id, blocked_id) values (%s,%s)",
           "authenticated", a, (a, b))
    check("차단하면 그 사람 public 기록이 안 보인다", seen(a, 22) == 0)
    check("차단당한 쪽도 상대 기록을 못 본다 (양방향)",
          db.one("select count(*) from public.discoveries where user_id=%s",
                 "authenticated", b, (a,)) == 0)
    db.run("delete from public.blocks")

    # ── 9. 시즌 경계 ─────────────────────────────────────────
    print("\n[9] 시즌 경계 — B-1 권고 ② (3~8 / 9~11 / 12~2 휴지기)")

    def bounds(when):
        r = db.rows("select season_start::date::text, season_end::date::text, is_dormant "
                    "from public.season_bounds(%s)", params=(when,))[0]
        return r

    check("7월은 3~8월 시즌", bounds("2026-07-15 12:00+09")[:2] == ("2026-03-01", "2026-09-01"),
          bounds("2026-07-15 12:00+09"))
    check("10월은 9~11월 시즌", bounds("2026-10-15 12:00+09")[:2] == ("2026-09-01", "2026-12-01"),
          bounds("2026-10-15 12:00+09"))
    check("1월은 휴지기이고 전년 12월부터다 (해를 넘긴다)",
          bounds("2027-01-15 12:00+09") == ("2026-12-01", "2027-03-01", True),
          bounds("2027-01-15 12:00+09"))
    check("12월도 휴지기다", bounds("2026-12-15 12:00+09")[2] is True)
    check("8월은 휴지기가 아니다", bounds("2026-08-15 12:00+09")[2] is False)

    # ── 10. B-6 ──────────────────────────────────────────────
    print("\n[10] B-6 지역 랭킹 — 10명 미만이면 구 단위로 확장")
    r = db.rows("select scope, member_count from public.region_ranking(%s, %s)",
                "authenticated", a, (a, "2026-07-15 12:00+09"))
    check("동에 2명뿐이라 구 단위로 확장됐다", r and r[0][0] == "gu", r)
    r1 = db.rows("select distinct scope from public.region_ranking(%s, %s, 1)",
                 "authenticated", a, (a, "2026-07-15 12:00+09"))
    check("최소 인원을 1로 낮추면 동 단위가 된다", r1 and r1[0][0] == "dong", r1)

    # ── 11. 랭킹 정확성 ──────────────────────────────────────
    print("\n[11] 랭킹 정확성 — 종 수로 세고, 비공개도 센다")
    got = dict(db.rows("select nickname, species_count from public.region_ranking(%s, %s, 1)",
                       "authenticated", a, (a, "2026-07-15 12:00+09")))
    check("재발견을 세지 않는다 — 가영은 4건이지만 2종", got.get("가영") == 2, got)
    # 나린은 private/friends/public 각 1건 = 3종. **private도 세야 한다** —
    # 기획서 8장: 도감 등록과 지도 공유는 별개다.
    check("private 기록도 랭킹에 센다 (도감과 지도 공유는 별개)", got.get("나린") == 3, got)
    top = dict(db.rows("select nickname, top_flower from public.region_ranking(%s, %s, 1)",
                       "authenticated", a, (a, "2026-07-15 12:00+09")))
    check("대표 꽃이 채워진다 (B-8)", all(v for v in top.values()), top)

    # ── 12. 유출 ─────────────────────────────────────────────
    print("\n[12] 랭킹이 위치·사진을 내보내지 않는다")
    names = db.one("select array_to_string(proargnames, ',') from pg_proc "
                   "where proname='region_ranking'")
    for leak in ("lat", "lng", "photo_url", "place_name", "note"):
        check("반환에 %s가 없다" % leak, leak not in (names or "").split(","), names)

    # ── 13. security definer 방어 ────────────────────────────
    print("\n[13] security definer 함수가 인자를 믿지 않는다")
    check("남의 uuid로 지역 랭킹을 못 부른다",
          db.blocked("select * from public.region_ranking(%s)", "authenticated", a, (b,)))
    check("남의 uuid로 친구 랭킹을 못 부른다",
          db.blocked("select * from public.friend_ranking(%s)", "authenticated", a, (b,)))
    check("남의 uuid로 시즌 요약을 못 부른다",
          db.blocked("select * from public.my_season_summary(%s)", "authenticated", a, (b,)))

    # ── 14. 친구 랭킹 · 요약 ─────────────────────────────────
    print("\n[14] 친구 랭킹 · 시즌 요약")
    fr = db.rows("select nickname, is_me from public.friend_ranking(%s, %s)",
                 "authenticated", a, (a, "2026-07-15 12:00+09"))
    d = dict(fr)
    check("친구 랭킹에 본인이 포함된다 (내 순위를 알아야 한다)", d.get("가영") is True, fr)
    check("친구 랭킹에 친구가 포함된다", "나린" in d, fr)
    check("친구가 아닌 사람은 안 들어온다", "다온" not in d, fr)
    s = db.rows("select species_count, discovery_count, place_count "
                "from public.my_season_summary(%s, %s)",
                "authenticated", a, (a, "2026-07-15 12:00+09"))[0]
    # 가영: flower 1을 3건(좌표 있음) + flower 9를 2건(좌표 없음) = 5건 · 2종
    check("시즌 요약 — 종 수와 발견 횟수를 따로 센다", s[0] == 2 and s[1] == 5, s)

    # ── 15. anon ─────────────────────────────────────────────
    print("\n[15] anon (로그인 전)")
    check("로그인 전에도 도감 200종은 보인다 (화면 04)",
          db.one("select count(*) from public.flowers", "anon") == 200)
    check("로그인 전에는 도감 마스터를 못 고친다",
          db.blocked("update public.flowers set name='해킹' where id=1", "anon"))
    check("로그인 전에는 남의 발견 기록을 못 본다",
          db.one("select count(*) from public.discoveries", "anon") == 0)
    check("로그인 전에는 랭킹을 못 부른다",
          db.blocked("select * from public.region_ranking()", "anon"))

    print("\n" + "=" * 60)
    print("통과 %d · 실패 %d" % (len(PASS), len(FAIL)))
    if FAIL:
        print("\n실패 목록:")
        for f in FAIL:
            print("  -", f)
    return 1 if FAIL else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("\n💥 테스트 자체가 죽었다:\n%s" % e)
        sys.exit(2)
