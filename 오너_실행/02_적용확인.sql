-- ════════════════════════════════════════════════════════════════
--  적용 확인 — 01_스키마_전체.sql 을 돌린 뒤 이걸 붙여넣고 Run
-- ════════════════════════════════════════════════════════════════
--
--  ⚠️ SQL Editor의 "Success. No rows returned" 는 **성공했다는 뜻이 아니다.**
--     아무것도 만들지 않고 끝났을 때도 똑같이 나온다. 그래서 세어서 확인한다.
--
--  ⚠️ **이 파일은 SQL 문장이 딱 하나다.** Supabase SQL Editor는 여러 문장을 돌리면
--     **마지막 결과만 보여준다** — 앞 문장 결과가 조용히 가려진다(실제로 겪었다).
--     그래서 확인 항목을 전부 한 문장에 넣었다. 문장을 추가하지 않는다.
--
--  결과가 표로 나온다. **`판정` 칸이 7줄 전부 ✅ 여야 한다.**
--  하나라도 ❌ 면 그 줄을 그대로 개발자(클로드)에게 보여주면 된다.

with 검사 as (

  select 1 as 순서, '표(table) 6개' as 항목, '6' as 기대,
         (select count(*) from pg_tables
           where schemaname = 'public'
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports')
         )::text as 실제

  union all
  select 2, '꽃 도감 200종', '200',
         (select count(*) from public.flowers)::text

  union all
  -- 개수만 세면 **엉뚱한 데이터가 200개 들어가도 통과한다.** 내용도 한 줄 본다.
  select 3, '도감 1번이 개나리 · 3~4월', '개나리 {3,4}',
         (select name || ' ' || bloom_months::text from public.flowers where id = 1)

  union all
  select 4, '함수(function) 10개', '10',
         (select count(*) from pg_proc p
            join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public'
             and p.proname in ('season_bounds','region_ranking','friend_ranking',
                               'my_season_summary','assert_self','are_friends',
                               'is_blocked_between','is_report_hidden',
                               'handle_new_user','set_captured_date')
         )::text

  union all
  -- `flowers`까지 6개다. 도감 마스터도 RLS를 켜고 "누구나 읽기" 정책을 따로 준다 —
  -- 켜지 않으면 anon 키로 **쓰기까지** 열린다.
  select 5, 'RLS 켜진 표 6개', '6',
         (select count(*) from pg_tables
           where schemaname = 'public' and rowsecurity = true
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports')
         )::text

  union all
  select 6, '접근정책(policy) 17개', '17',
         (select count(*) from pg_policies where schemaname = 'public')::text

  union all
  -- 랭킹 함수가 **불릴 수 있는가.** 만들어졌다는 것과 도는 것은 다르다
  -- (인자 개수가 어긋나면 앱에서만 404가 난다).
  select 7, '시즌 계산 함수가 실제로 돈다', 'ok',
         (select case when (select count(*) from public.season_bounds()) >= 1
                      then 'ok' else '결과 없음' end)

)
select
  항목,
  coalesce(실제, '(없음)') || '  ⟵ 기대: ' || 기대 as "실제 ⟵ 기대",
  case when 실제 = 기대 then '✅' else '❌' end as 판정
from 검사
order by 순서;
