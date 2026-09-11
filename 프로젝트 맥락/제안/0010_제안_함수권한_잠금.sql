-- ════════════════════════════════════════════════════════════════
--  0010 — `revoke execute … from anon`이 **아무것도 막지 않았다**
--        ⚠️ **이 제목은 절반만 맞았다 — 아래 「2026-09-11 정정」을 먼저 읽는다.**
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **실측(2026-08-16 · 실서버 · APK에 들어 있는 publishable 키만 씀 · 세션 없음).**
--     로그인하지 않은 상태에서 랭킹 함수들이 **200을 준다**:
--
--         POST /rest/v1/rpc/dong_member_count   {"target_dong":"1144071000"}  → 200  0
--         POST /rest/v1/rpc/region_ranking      {"target_user":"000…0"}       → 200  []
--         POST /rest/v1/rpc/friend_ranking      {"target_user":<uuid>}        → 200  []
--         POST /rest/v1/rpc/my_season_summary   {"target_user":<uuid>}        → 200  [{…}]
--
--     🔵 **`[]`가 아니라 `200`이 증거다.** 권한이 막혔으면 `403 / 42501`이 온다.
--        200은 **함수가 실제로 돌았다**는 뜻이고, 그 뒤에 나오는 값이 비어 있는지는
--        그 계정에 기록이 있느냐의 문제일 뿐이다 —
--        `my_season_summary`는 **행을 채워서 돌려줬다**(`species_count` …).
--
--  ## 왜 열려 있나 — 우리 SQL은 틀리지 않았다. **한 줄이 빠졌다**
--
--  `0002`·`0004`가 이렇게 쓴다:
--
--      grant  execute on function public.region_ranking(uuid, timestamptz, int) to authenticated;
--      revoke execute on function public.region_ranking(uuid, timestamptz, int) from anon;
--
--  🔴 **함수를 만들면 Postgres가 `PUBLIC`에게 EXECUTE를 자동으로 준다.**
--     `PUBLIC`은 "모든 롤"이라서 `anon`도 거기에 포함된다. 그래서
--     **`anon`에게서 직접 준 적 없는 권한을 회수해도** PUBLIC 경로가 그대로 남는다.
--     `revoke … from anon`은 **오류도 경고도 없이 성공하고, 아무것도 바꾸지 않는다.**
--
--  ## 🔴 2026-09-11 정정 — **위 문장은 절반만 맞았다. 실서버에서 반증됐다**
--
--  이 파일 2절(`from public`만 회수)을 실서버에 올렸는데 3절에서 **7개 중 4개만
--  ✅였다.** 5·6·7번(`assert_self` · `discovery_reactions` · `delete_comment`)은
--  `anon이_실행가능 = true`로 남아 있었다.
--
--  원인은 `pg_proc.proacl`을 읽어서 나왔다(`받는사람=권한/준사람` 형식):
--
--      region_ranking      → {postgres=X/postgres,authenticated=X/postgres}      ← 잠겼다
--      assert_self         → {postgres=X/postgres,anon=X/postgres,authenticated=…} ← 남아 있다
--                                                 ^^^^^^^^^^^^^^^
--
--  🔴 **`anon`에게 직접 준 항목이 따로 있었다.** `PUBLIC` 경로를 끊어도 이 직접
--     항목은 그대로 남는다. 즉 두 회수가 **서로를 대신하지 못한다** —
--     어느 쪽이 필요한지는 **함수마다 다르고, 파일을 봐서는 알 수 없다.**
--     (원래 문장은 `from anon`이 **언제나** 무의미하다고 읽히게 써 있었다.)
--
--  🔵 **고침 = 둘 다 쓴다.** 위 3개에 아래를 더 돌려서 13/13 ✅가 됐다:
--
--      revoke execute on function public.assert_self(uuid)         from anon;
--      revoke execute on function public.discovery_reactions(uuid) from anon;
--      revoke execute on function public.delete_comment(uuid)      from anon;
--      grant  execute on function public.assert_self(uuid)         to authenticated, service_role;
--      grant  execute on function public.discovery_reactions(uuid) to authenticated, service_role;
--      grant  execute on function public.delete_comment(uuid)      to authenticated, service_role;
--
--  ⚠️ **왜 로컬 실측(아래 measure_0010_grants.py)이 이걸 못 잡았나.** 그 스크립트는
--     `0002`·`0004`가 쓰는 방식으로 함수를 새로 만들어 재현했다 — 거기서는 `anon`
--     직접 항목이 **생기지 않는다.** 실서버의 그 3개는 다른 마이그레이션(0007·0009)이
--     `grant … to anon`을 실제로 썼기 때문에 생긴 것이다.
--     🔴 **재현이 원본과 같은 경로로 만들어졌는지 세지 않으면, 재현은 대조군만 잡는다.**
--
--  🔵 **그래서 3절이 이 파일을 구했다.** "성공했다"만 봤으면 4/7로 끝났고, 열린
--     3개 중 하나가 **남의 댓글을 지우는 `delete_comment`**였다.
--     → 진단이 필요하면 3절 뒤의 **3-b절(proacl 덤프)** 을 쓴다.
--
--  ⚠️ **그래서 이건 "정책이 붙었는지"를 보는 검사로는 절대 안 잡힌다.** `02_적용확인.sql`은
--     함수가 **있는지**를 세고 있고, 있는 건 맞다. 권한은 `pg_policies`에도, 함수 본문에도
--     안 적혀 있다 — `has_function_privilege('anon', …)`을 **직접 물어야** 나온다(1절).
--
--  ## 이게 왜 위험한가 — `public_profiles`가 uuid를 준다
--
--  `0001` 7-2절이 `public_profiles`(id · nickname)를 **anon에게 일부러 열어 뒀다**
--  (같은 동네 사람 목록이 필요해서). 그 자체는 결정된 사항이다. 그런데 위 구멍과
--  붙으면 **APK에 든 키만으로 남의 uuid를 얻어 그 사람의 시즌 요약·친구 랭킹을 읽는**
--  경로가 된다. 두 개 각각은 의도된 값이고, **위험한 것은 조합이다.**
--
--  🔵 **앱은 이걸로 안 죽는다.** 익명 로그인 세션의 JWT는 롤이 `authenticated`다
--     (`anon`이 아니다). 앱은 항상 세션 토큰으로 부른다(`RankingService`는 토큰이
--     없으면 `Failed(401)`을 내고 호출조차 하지 않는다). 즉 **비로그인 열람은
--     그대로 돌아간다** — 잠그는 것은 "세션이 아예 없는 호출"이다.
--
--  ## 🔵 이 파일이 실제로 막는지 **로컬에서 확인했다**
--
--  실서버 실측은 "열려 있다"까지만 말해 준다. 원인과 고침은 로컬 Postgres에 같은 순서를
--  다시 올려 확인했다 — `python3 supabase/_tools/measure_0010_grants.py`:
--
--      ① grant authenticated + revoke anon → 카탈로그: anon=True  authenticated=True
--         anon이 실제로 호출 → 🔴 돌았다
--      ② + revoke from public              → 카탈로그: anon=False authenticated=True
--         anon          → 막혔다: permission denied for function …
--         authenticated → 돌았다 · service_role → 돌았다
--      판정: PASS — 재현했고(anon이 실제로 돌았다) 0010이 막는다
--
--  🔴 **카탈로그(`has_function_privilege`)만 보지 않고 `set role`로 실제 호출까지 했다.**
--     권한은 실행 시점에 걸리므로 카탈로그만 읽으면 "막혔다고 적혀 있다"와 "막힌다"를
--     구분할 수 없다 — 이 저장소에서 정책을 그렇게 믿었다가 세 곳이 뚫려 있었다.
--
--  ## 못 잰 것 (모르는 채로 둔다)
--
--  `delete_comment` · `discovery_reactions` · `assert_self`는 **같은 방식으로 열려 있을
--  것이 거의 확실하지만 재지 않았다** — 실서버 프로덕션을 더 찔러 보는 것이 막혔다.
--  🔴 `delete_comment`가 그중 유일하게 **쓰는** 함수다. 첫 판에서 **anon이 남의 댓글을
--  지운 적이 있고**((72) 21행이 그때 생겼다), 지금 그것을 막고 있는 것은 함수 본문의
--  가드 3개뿐이다. 이 파일은 그 앞에 자물쇠를 하나 더 건다 — **가드가 유일한 방어인
--  상태를 유지하지 않는다.**
--
--  ## 안 고치는 것
--
--  `season_bounds` · `is_report_hidden` · `is_blocked_between` · `are_friends` ·
--  `can_see_discovery` · `is_discovery_owner`는 **anon에게 일부러 열었다**(0001·0007).
--  이 파일은 손대지 않고, 3절에서 **대조군으로 같이 잰다** — 전부 `false`로 나오면
--  그건 성공이 아니라 **내가 너무 많이 잠근 것**이다.
--
-- ════════════════════════════════════════════════════════════════


-- ────────────────────────────────────────────────────────────────
--  1절 — 지금 상태. **아무것도 바꾸지 않는다.**
-- ────────────────────────────────────────────────────────────────
--  `anon이_실행가능`이 잠글대상 7줄에서 **true**로 나오는 것이 이 파일의 근거다.
--  🔵 대조군 6줄은 **true가 정상**이다(일부러 열어 둔 함수).

with 대상(순서, 함수, 잠글것인가) as (
  values
    ( 1, 'public.region_ranking(uuid, timestamptz, int)',  true),
    ( 2, 'public.friend_ranking(uuid, timestamptz)',       true),
    ( 3, 'public.my_season_summary(uuid, timestamptz)',    true),
    ( 4, 'public.dong_member_count(text, timestamptz)',    true),
    ( 5, 'public.assert_self(uuid)',                       true),
    ( 6, 'public.discovery_reactions(uuid)',               true),
    ( 7, 'public.delete_comment(uuid)',                    true),
    ( 8, 'public.season_bounds(timestamptz)',              false),
    ( 9, 'public.is_report_hidden(uuid)',                  false),
    (10, 'public.is_blocked_between(uuid, uuid)',          false),
    (11, 'public.are_friends(uuid, uuid)',                 false),
    (12, 'public.can_see_discovery(uuid)',                 false),
    (13, 'public.is_discovery_owner(uuid)',                false)
)
select
  순서,
  case when 잠글것인가 then '🔒 잠글 대상' else '🔵 대조군(열어 둔 것)' end as 구분,
  함수,
  has_function_privilege('anon',          함수, 'execute') as "anon이_실행가능",
  has_function_privilege('authenticated', 함수, 'execute') as "로그인이_실행가능"
from 대상
order by 잠글것인가 desc, 순서;


-- ────────────────────────────────────────────────────────────────
--  2절 — 잠근다. 🔴 **1절 표를 확인한 뒤에** 아래 `/*` `*/` 두 줄을 지운다.
-- ────────────────────────────────────────────────────────────────
--  🔴 ~~`from anon`이 아니라 **`from public`**이다. 그것이 이 파일의 전부다.~~
--     **2026-09-11 정정: 틀렸다. 둘 다 해야 한다.** `from public`만 돌리면 실서버에서
--     7개 중 3개가 안 잠긴다(`assert_self` · `discovery_reactions` · `delete_comment`
--     에는 `anon`에게 **직접 준 항목**이 따로 있다). 머리말의 정정 절 참조.
--     🔵 그래서 아래 목록은 **두 벌**이다. 어느 쪽이 필요한지 함수마다 다르므로
--        **전부에 둘 다 돌린다** — 필요 없는 쪽은 아무 일도 안 하고 성공한다.
--  ⚠️ `authenticated`·`service_role`에게는 **다시 명시적으로 준다.** PUBLIC을 걷으면
--     명시 grant만 남으므로, 원래 그 롤로 돌던 호출이 조용히 죽는 일이 없게 한다.
--  ✅ **두 번 돌려도 안전하다**(revoke·grant는 멱등이다).

/*
revoke execute on function public.region_ranking(uuid, timestamptz, int) from public;
revoke execute on function public.friend_ranking(uuid, timestamptz)      from public;
revoke execute on function public.my_season_summary(uuid, timestamptz)   from public;
revoke execute on function public.dong_member_count(text, timestamptz)   from public;
revoke execute on function public.assert_self(uuid)                      from public;
revoke execute on function public.discovery_reactions(uuid)              from public;
revoke execute on function public.delete_comment(uuid)                   from public;

-- 🔴 여기부터가 2026-09-11에 추가된 줄이다. **이게 없으면 3개가 안 잠긴다.**
revoke execute on function public.region_ranking(uuid, timestamptz, int) from anon;
revoke execute on function public.friend_ranking(uuid, timestamptz)      from anon;
revoke execute on function public.my_season_summary(uuid, timestamptz)   from anon;
revoke execute on function public.dong_member_count(text, timestamptz)   from anon;
revoke execute on function public.assert_self(uuid)                      from anon;
revoke execute on function public.discovery_reactions(uuid)              from anon;
revoke execute on function public.delete_comment(uuid)                   from anon;

grant execute on function public.region_ranking(uuid, timestamptz, int) to authenticated, service_role;
grant execute on function public.friend_ranking(uuid, timestamptz)      to authenticated, service_role;
grant execute on function public.my_season_summary(uuid, timestamptz)   to authenticated, service_role;
grant execute on function public.dong_member_count(text, timestamptz)   to authenticated, service_role;
grant execute on function public.assert_self(uuid)                      to authenticated, service_role;
grant execute on function public.discovery_reactions(uuid)              to authenticated, service_role;
grant execute on function public.delete_comment(uuid)                   to authenticated, service_role;
*/


-- ────────────────────────────────────────────────────────────────
--  3절 — 다시 잰다. 2절을 돌린 **뒤에** 이 절만 따로 돌린다.
-- ────────────────────────────────────────────────────────────────
--  🔴 **"성공했다"는 메시지는 증거가 아니다.** revoke는 아무것도 안 바꿔도 성공한다 —
--     그게 이 파일이 존재하는 이유다. 아래 `판정`이 **13줄 전부 ✅**여야 한다.
--  ⚠️ SQL Editor는 **마지막 문장 결과만** 보여준다. 그래서 이 절을 따로 돌린다.

/*
with 대상(순서, 함수, 잠글것인가) as (
  values
    ( 1, 'public.region_ranking(uuid, timestamptz, int)',  true),
    ( 2, 'public.friend_ranking(uuid, timestamptz)',       true),
    ( 3, 'public.my_season_summary(uuid, timestamptz)',    true),
    ( 4, 'public.dong_member_count(text, timestamptz)',    true),
    ( 5, 'public.assert_self(uuid)',                       true),
    ( 6, 'public.discovery_reactions(uuid)',               true),
    ( 7, 'public.delete_comment(uuid)',                    true),
    ( 8, 'public.season_bounds(timestamptz)',              false),
    ( 9, 'public.is_report_hidden(uuid)',                  false),
    (10, 'public.is_blocked_between(uuid, uuid)',          false),
    (11, 'public.are_friends(uuid, uuid)',                 false),
    (12, 'public.can_see_discovery(uuid)',                 false),
    (13, 'public.is_discovery_owner(uuid)',                false)
)
select
  순서,
  함수,
  has_function_privilege('anon',          함수, 'execute') as "anon이_실행가능",
  has_function_privilege('authenticated', 함수, 'execute') as "로그인이_실행가능",
  case
    when 잠글것인가
     and not has_function_privilege('anon', 함수, 'execute')
     and     has_function_privilege('authenticated', 함수, 'execute') then '✅ 잠겼다'
    when not 잠글것인가
     and     has_function_privilege('anon', 함수, 'execute')          then '✅ 그대로 열려 있다'
    when 잠글것인가
     and not has_function_privilege('authenticated', 함수, 'execute') then '🔴 로그인도 못 부른다 — 너무 많이 잠갔다'
    else '❌'
  end as 판정
from 대상
order by 잠글것인가 desc, 순서;
*/


-- ────────────────────────────────────────────────────────────────
--  3-b절 — 3절에서 ❌가 남았을 때만. **왜 안 잠겼는지**를 본다.
-- ────────────────────────────────────────────────────────────────
--  🔴 `has_function_privilege`는 "열려 있다"까지만 말한다. **어느 경로로** 열려
--     있는지는 `pg_proc.proacl`에만 적혀 있다 — 그걸 모르면 어떤 revoke를 더
--     써야 하는지 알 수 없고, 추측으로 SQL을 더 만들게 된다.
--
--  읽는 법: 항목은 `받는사람=권한/준사람`이다. `받는사람`이 비면 그게 `PUBLIC`이다.
--    · `anon=X/postgres`  가 보이면 → `revoke … from anon`   이 필요하다
--    · `=X/postgres`      가 보이면 → `revoke … from public` 이 필요하다
--    · 둘 다 보이면 → **둘 다** 필요하다(하나만 하면 조용히 안 막힌다)
--
--  ⚠️ **이미 잠긴 함수 2개를 대조군으로 같이 뽑는다.** 안 그러면 "이 모양이 원래
--     그런 것"인지 "이 함수만 다른 것"인지 구분할 수 없다.

/*
with 대상(순서, 이름, 인수, 비고) as (
  values
    ( 1, 'region_ranking',      'uuid, timestamptz, int', '대조군 — 이미 잠겼어야 한다'),
    ( 2, 'my_season_summary',   'uuid, timestamptz',      '대조군 — 이미 잠겼어야 한다'),
    ( 3, 'assert_self',         'uuid',                   '3절에서 ❌였다'),
    ( 4, 'discovery_reactions', 'uuid',                   '3절에서 ❌였다'),
    ( 5, 'delete_comment',      'uuid',                   '3절에서 ❌였다')
)
select
  t.순서,
  t.이름,
  t.비고,
  has_function_privilege('anon', 'public.' || t.이름 || '(' || t.인수 || ')', 'execute')
    as "anon이_실행가능",
  coalesce(p.proacl::text, '(null = PUBLIC에게 열린 기본 상태)') as 권한항목
from 대상 t
left join pg_proc p
       on p.oid = to_regprocedure('public.' || t.이름 || '(' || t.인수 || ')')
order by t.순서;
*/

--  🔵 2026-09-11 실측 결과 — 3·4·5번에 `anon=X/postgres`가 있었고 1·2번에는 없었다.
--     대조군이 없었으면 "proacl에 anon이 있는 게 정상"으로 읽고 넘겼을 것이다.
