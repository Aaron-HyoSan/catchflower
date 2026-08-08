-- ════════════════════════════════════════════════════════════════
--  적용 확인 — 01_스키마_전체.sql 을 돌린 뒤 이걸 붙여넣고 Run
-- ════════════════════════════════════════════════════════════════
--
--  ⚠️ SQL Editor의 "Success. No rows returned" 는 **성공했다는 뜻이 아니다.**
--     아무것도 만들지 않고 끝났을 때도 똑같이 나온다. 그래서 세어서 확인한다.
--
--  결과가 표로 나온다. **`판정` 칸이 전부 ✅ 여야 한다.**
--  하나라도 ❌ 면 그 줄을 그대로 개발자(클로드)에게 보여주면 된다.

select
  항목,
  실제 || ' / ' || 기대 as "실제 / 기대",
  case when 실제 = 기대 then '✅' else '❌' end as 판정
from (
  select '표(table) 6개'            as 항목, 6 as 기대,
         (select count(*) from pg_tables
           where schemaname = 'public'
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports')
         )::int as 실제
  union all
  select '꽃 도감 200종', 200,
         (select count(*) from public.flowers)::int
  union all
  select '함수(function) 10개', 10,
         (select count(*) from pg_proc p
            join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public'
             and p.proname in ('season_bounds','region_ranking','friend_ranking',
                               'my_season_summary','assert_self','are_friends',
                               'is_blocked_between','is_report_hidden',
                               'handle_new_user','set_captured_date')
         )::int
  union all
  -- `flowers`까지 6개다. 도감 마스터도 RLS를 켜고 "누구나 읽기" 정책을 따로 준다 —
  -- 켜지 않으면 anon 키로 **쓰기까지** 열린다.
  select 'RLS 켜진 표 6개', 6,
         (select count(*) from pg_tables
           where schemaname = 'public' and rowsecurity = true
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports')
         )::int
  union all
  select '접근정책(policy) 17개', 17,
         (select count(*) from pg_policies where schemaname = 'public')::int
) t
order by 판정, 항목;


-- ── 추가 확인: 도감 데이터가 실제로 읽히는가 ──────────────────────
-- 아래는 3줄이 나와야 한다 (1 개나리 / 2 진달래 / 3 벚꽃 ... 순서는 다를 수 있다).
select id, name, bloom_months, season
from public.flowers
order by id
limit 3;
