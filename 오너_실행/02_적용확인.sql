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
--  결과가 표로 나온다. **`판정` 칸이 17줄 전부 ✅ 여야 한다.**
--  하나라도 ❌ 면 그 줄을 그대로 개발자(클로드)에게 보여주면 된다.
--
--  ⚠️ **표가 아니라 빨간 오류가 나면 그것도 답이다.** 8·11·14·17번은 함수를 실제로
--     불러 보는 줄이라, 그 함수가 없으면 표가 아니라 오류로 끝난다
--     (`function public.dong_member_count(...) does not exist`처럼 **없는 것의
--     이름이 오류에 나온다**). 그 문장을 그대로 보여주면 된다.

with 검사 as (

  -- 0007에서 둘 늘었다(`likes` · `comments`).
  select 1 as 순서, '표(table) 8개' as 항목, '8' as 기대,
         (select count(*) from pg_tables
           where schemaname = 'public'
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports',
                               'likes','comments')
         )::text as 실제

  union all
  -- 0006에서 1,857종 늘었다. **`= 2057`이 아니라 `>= 2057`로 보지 않는 이유:**
  -- 여기는 확인이지 멱등성이 아니다 — 2,057보다 많으면 도감에 없는 번호가
  -- 들어간 것이고 그건 앱 그리드에서 **빈 칸**으로만 보인다(0005 1절).
  select 2, '꽃 도감 2,057종', '2057',
         (select count(*) from public.flowers)::text

  union all
  -- 개수만 세면 **엉뚱한 데이터가 2,057개 들어가도 통과한다.** 내용도 한 줄 본다.
  select 3, '도감 1번이 개나리 · 3~4월', '개나리 {3,4}',
         (select name || ' ' || bloom_months::text from public.flowers where id = 1)

  union all
  -- 🔴 **확장분이 사람이 정한 200종을 덮지 않았는가.** 이게 가장 위험한 실패다 —
  --    덮여도 종수는 2,057 그대로라 2번은 ✅고, 화면도 정상으로 보이는데
  --    판별 Top-1이 77% → 50%로 떨어진다(계약 1-1-a 실측). 200종은 네 칸이
  --    **전부 채워져 있어야** 한다. 하나라도 비면 초안이 덮은 것이다.
  select 4, '사람이 정한 200종이 안 덮였다', '0',
         (select count(*) from public.flowers
           where id <= 200
             and (season is null or color is null or habitat is null
                  or bloom_label is null or bloom_label = ''))::text

  union all
  -- 0005에서 하나 늘었다(`bloom_months_sane`). 0004에서 둘
  -- (`enforce_region_change` · `dong_member_count`). 0007에서 셋.
  select 5, '함수(function) 16개', '16',
         (select count(*) from pg_proc p
            join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public'
             and p.proname in ('season_bounds','region_ranking','friend_ranking',
                               'my_season_summary','assert_self','are_friends',
                               'is_blocked_between','is_report_hidden',
                               'handle_new_user','set_captured_date',
                               'enforce_region_change','dong_member_count',
                               'bloom_months_sane',
                               -- 0007
                               'can_see_discovery','is_discovery_owner','discovery_reactions')
         )::text

  union all
  -- `flowers`까지 6개다. 도감 마스터도 RLS를 켜고 "누구나 읽기" 정책을 따로 준다 —
  -- 켜지 않으면 anon 키로 **쓰기까지** 열린다.
  select 6, 'RLS 켜진 표 8개', '8',
         (select count(*) from pg_tables
           where schemaname = 'public' and rowsecurity = true
             and tablename in ('flowers','users','discoveries','friendships','blocks','reports',
                               -- 🔴 0007. 여기 RLS를 안 켜면 **비공개 기록의 댓글이
                               --    anon 키로 전부 읽힌다** — 앱 화면에는 증상이 없다.
                               'likes','comments')
         )::text

  union all
  select 7, '접근정책(policy) 23개', '23',
         (select count(*) from pg_policies where schemaname = 'public')::text

  union all
  -- 랭킹 함수가 **불릴 수 있는가.** 만들어졌다는 것과 도는 것은 다르다
  -- (인자 개수가 어긋나면 앱에서만 404가 난다).
  select 8, '시즌 계산 함수가 실제로 돈다', 'ok',
         (select case when (select count(*) from public.season_bounds()) >= 1
                      then 'ok' else '결과 없음' end)

  -- ── 여기부터 0004(활동 지역) ──

  union all
  -- 🔴 **트리거가 "있는가"가 아니라 "붙었는가"다.** 함수만 만들어지고 트리거가
  --    안 붙어도 4번은 통과한다 — 그러면 6개월 규칙이 **아무것도 막지 않는데
  --    앱은 막힌다고 믿는다.** 0001 7-3절의 "정책이 붙었다 ≠ 규칙이 돈다"와 같다.
  select 9, '지역 변경 트리거가 users에 붙었다', '1',
         (select count(*) from pg_trigger
           where tgrelid = 'public.users'::regclass
             and tgname = 'users_enforce_region_change'
             and not tgisinternal
         )::text

  union all
  -- 0004의 check 제약 4개. `not valid`로 붙였어도 여기엔 나온다.
  select 10, '지역 코드 검사 4개', '4',
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
  select 11, '이웃 수 세는 함수가 실제로 돈다', 'ok',
         (select case when public.dong_member_count('0000000000') >= 0
                      then 'ok' else '결과 없음' end)

  -- ── 여기부터 0005·0006(도감 2,057종 확장) ──

  union all
  -- 🔴 **기본값 `'human'`이 새 행을 조용히 삼키지 않았는가**(0005 2절).
  --    `bloom_source`를 안 적은 적재가 있으면 이 수가 200을 넘고, 그러면
  --    "사람이 확인한 값"이 부풀어 **채워야 할 목록이 줄어 보인다.**
  select 12, '개화기 근거가 human인 종 200', '200',
         (select count(*) from public.flowers where bloom_source = 'human')::text

  union all
  -- 🔴 **`1~12월에 피는 꽃`이 화면에 뜨지 않는가**(계약 1-2-c).
  --    상록수 9종은 "12개월 모두 관찰됐다"까지만 근거가 있는데, 라벨을 붙이면
  --    "일 년 내내 핀다"고 **단정하는 문장**이 된다. 0이어야 한다.
  select 13, '12개월인데 개화기 표기가 붙은 종 0', '0',
         (select count(*) from public.flowers
           where array_length(bloom_months, 1) >= 12
             and bloom_label is not null and bloom_label <> '')::text

  union all
  -- 🔴 **개화월 무결성 제약이 실제로 붙었는가.** 이게 없으면 23개월짜리 행이
  --    들어가도 판별 정확도는 **하나도 안 움직이고** 화면에 `6~4월에 피는 꽃`만
  --    뜬다 — 즉 지표로는 원리상 못 잡는다(0005 4절). 함수를 직접 불러 본다:
  --    없으면 표가 아니라 오류로 끝나고, 그게 답이다(위 머리말 참고).
  select 14, '개화월 검사 함수가 23개월을 막는다', 'ok',
         (select case when public.bloom_months_sane('{1,2,3}'::int[])
                       and not public.bloom_months_sane('{6,6,7}'::int[])
                       and not public.bloom_months_sane('{0,13}'::int[])
                      then 'ok' else '막지 않는다' end)

  -- ── 여기부터 0007(좋아요 · 댓글) ──

  union all
  -- 🔴 **`can_see_discovery`가 비공개 기록을 막는가.** 이게 뚫리면 **비공개 기록의
  --    댓글이 읽힌다** — 기록 본문은 안 보이는데 댓글은 보이는 상태고, 우리 앱은
  --    기록을 먼저 읽으므로 **화면에 아무 증상이 없다.** API로만 새어 나간다.
  --    지금 로그인 없이(anon) 부르면 `auth.uid()`가 null이라 **무엇도 보여선 안 된다.**
  --    없는 uuid로 부른다 — false가 정상이고, 여기서 보는 것은 "함수가 돌고 막는가"다.
  select 15, '기록 열람 판정이 anon에게 아무것도 안 준다', 'ok',
         (select case when public.can_see_discovery('00000000-0000-0000-0000-000000000000') = false
                      then 'ok' else '막지 않는다' end)

  union all
  -- C-9 200자. 🔴 **제약이 붙었는가**를 본다 — 클라이언트만 막으면 API로 우회된다.
  select 16, '댓글 200자 제한이 붙었다', '1',
         (select count(*) from pg_constraint
           where conrelid = 'public.comments'::regclass
             and contype = 'c' and conname = 'comments_body_len')::text

  union all
  -- 화면 16 `좋아요 12 · 댓글 3`. 함수가 **불릴 수 있는가.**
  -- 없는 기록으로 부르면 0·0·false가 나온다 — 그게 정상이다.
  select 17, '반응 집계 함수가 실제로 돈다', 'ok',
         (select case when (select count(*) from
                     public.discovery_reactions('00000000-0000-0000-0000-000000000000')) = 1
                      then 'ok' else '결과 없음' end)

)
select
  항목,
  coalesce(실제, '(없음)') || '  ⟵ 기대: ' || 기대 as "실제 ⟵ 기대",
  case when 실제 = 기대 then '✅' else '❌' end as 판정
from 검사
order by 순서;
