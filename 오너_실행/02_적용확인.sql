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
--  결과가 표로 나온다. **`판정` 칸이 10줄 전부 ✅ 여야 한다.**
--  하나라도 ❌ 면 그 줄을 그대로 개발자(클로드)에게 보여주면 된다.
--
--  ⚠️ **표가 아니라 빨간 오류가 나면 그것도 답이다.** 7·10번은 함수를 실제로
--     불러 보는 줄이라, 그 함수가 없으면 표가 아니라 오류로 끝난다
--     (`function public.dong_member_count(...) does not exist`처럼 **없는 것의
--     이름이 오류에 나온다**). 그 문장을 그대로 보여주면 된다.

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
  -- 0004에서 둘 늘었다(`enforce_region_change` · `dong_member_count`).
  select 4, '함수(function) 12개', '12',
         (select count(*) from pg_proc p
            join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public'
             and p.proname in ('season_bounds','region_ranking','friend_ranking',
                               'my_season_summary','assert_self','are_friends',
                               'is_blocked_between','is_report_hidden',
                               'handle_new_user','set_captured_date',
                               'enforce_region_change','dong_member_count')
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

  -- ── 여기부터 0004(활동 지역) ──

  union all
  -- 🔴 **트리거가 "있는가"가 아니라 "붙었는가"다.** 함수만 만들어지고 트리거가
  --    안 붙어도 4번은 통과한다 — 그러면 6개월 규칙이 **아무것도 막지 않는데
  --    앱은 막힌다고 믿는다.** 0001 7-3절의 "정책이 붙었다 ≠ 규칙이 돈다"와 같다.
  select 8, '지역 변경 트리거가 users에 붙었다', '1',
         (select count(*) from pg_trigger
           where tgrelid = 'public.users'::regclass
             and tgname = 'users_enforce_region_change'
             and not tgisinternal
         )::text

  union all
  -- 0004의 check 제약 4개. `not valid`로 붙였어도 여기엔 나온다.
  select 9, '지역 코드 검사 4개', '4',
         (select count(*) from pg_constraint
           where conrelid = 'public.users'::regclass
             and contype = 'c'
             and conname in ('users_region_all_or_none','users_dong_code_format',
                             'users_gu_code_format','users_gu_code_matches_dong')
         )::text

  union all
  -- 화면 02의 `이웃 N명 활동 중`. 없으면 그 줄이 화면에서 조용히 사라진다.
  -- 아무도 안 사는 코드로 불러 본다 — **0이 정상 결과**이고, 여기서 보는 것은
  -- "함수가 도는가"다. 오류로 끝나면 그게 답이다(위 머리말 참고).
  select 10, '이웃 수 세는 함수가 실제로 돈다', 'ok',
         (select case when public.dong_member_count('0000000000') >= 0
                      then 'ok' else '결과 없음' end)

)
select
  항목,
  coalesce(실제, '(없음)') || '  ⟵ 기대: ' || 기대 as "실제 ⟵ 기대",
  case when 실제 = 기대 then '✅' else '❌' end as 판정
from 검사
order by 순서;
