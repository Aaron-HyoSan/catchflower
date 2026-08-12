-- ════════════════════════════════════════════════════════════════
--  0009 — 댓글 삭제 403의 **진짜 원인**. soft delete가 자기 SELECT 정책을 넘지 못했다
-- ════════════════════════════════════════════════════════════════
--
--  ✅ **오너가 적용했다 (2026-08-12). 진단 6행 전부 ✅.** 이 파일은 그때까지
--     `프로젝트 맥락/제안/0009_제안_댓글삭제_RPC.sql`에 있었다 — 계약 6절이 스키마를
--     "한쪽만 만든다 · 진행.md에 먼저 쓰고 알린다"라서 승인 전엔 `migrations/`에
--     둘 수 없었다(여기 두면 `build_합본.py`가 집어가 **승인 없이 서버로 간다**).
--
--     🔴 **적용된 지금은 반대로 여기 없는 것이 위험하다.** 합본(`01_스키마_전체.sql`)은
--        `migrations/`만 훑으므로, 이 파일이 밖에 있으면 **새 환경에 합본을 붙여넣을 때
--        이 고침만 조용히 빠진다.** 그리고 그 환경에서는 댓글 삭제가 다시 403이 되는데
--        진단 8행은 전부 ✅라서 **원인을 처음부터 다시 찾게 된다**(그게 (65)다).
--        → 그래서 승격했다. `제안/`의 원본은 이력으로 남긴다.
--
--  🔴 **0008은 원인을 못 찾았다.** 그 파일 머리말에 이렇게 썼다:
--
--     > **원인을 파일에서 찾지 못했다.** `0007`의 원문은 정확하다(바이트까지 확인했다).
--     > `deleted_at is not null`이 참인데 거부된다면 **서버에 붙은 식이 이 파일과 다르다.**
--
--     그 추측이 **틀렸다.** 오너가 0008을 적용하고 진단 8행이 **전부 기대와 일치**했는데
--     (2026-08-12) 실기기에서 삭제는 여전히 `Failed(code=403, pgCode=42501)`였다.
--     정책 원문이 파일과 같다는 것이 확인됐으니 남은 것은 하나다 — **우리 정책이 아니라
--     Postgres의 규칙이다.**
--
--  🔴 **원인: `update`의 새 행에는 `select` 정책도 적용된다.**
--
--     `comments_read` 는 `deleted_at is null` 을 요구한다(0007 3절). soft delete는
--     바로 그 칸을 채우는 update다. 그래서 **바뀐 뒤의 행이 자기 SELECT 정책에서
--     사라지고**, Postgres는 그것을 `42501 new row violates row-level security policy`
--     로 거부한다. 즉 두 정책은 **각각 옳은데 서로를 막았다.**
--
--     ⚠️ 이 조합은 **정책 원문을 아무리 읽어도 안 보인다.** 0008의 진단 8행이 전부 ✅인
--        이유가 그것이다 — 진단은 각 정책이 옳은지를 봤고, 실제로 각각 옳았다.
--        틀린 것은 **둘의 상호작용**이고 그건 `pg_policies`에 안 적혀 있다.
--
--  ## 실측 (로컬 Postgres 16.2 · `set role authenticated`로 RLS를 실제로 태웠다)
--
--  0001~0008을 그대로 올리고 A의 공개 기록에 A가 댓글을 달아 지워 봤다:
--
--      A가 자기 댓글 삭제                      → 42501   ← 실기기와 같은 실패를 재현했다
--      사진 주인 A가 B의 댓글 삭제               → 42501   (실기기 미측정이던 경로도 같다)
--      제3자 B가 삭제 (막혀야 정상)              → 0행     ← 여기만 갈린다(정상)
--      `comments_read` 정책만 떼고 다시          → **통과**  ← SELECT 정책이 원인이다
--
--  조건을 하나만 바꿔 확인했다. `comments_read`에서 `deleted_at is null`만 빼면
--  삭제가 통과한다 — 나머지는 하나도 안 건드렸다.
--
--  ## 규칙을 표 하나로 못 박았다 (우리 스키마를 안 쓰는 최소 재현)
--
--      create table t (id int primary key, gone boolean default false, note text);
--      정책: for update using(true) with check(true) · for select using (gone = false)
--
--      update t set gone = true where id = 2      → 42501
--      update t set note = 'x'  where id = 2      → 1행    (새 행이 여전히 보인다)
--      update t set gone = true where note is null → 42501
--      update t set gone = true where id > 0       → 42501
--      update t set gone = true                    → **3행**  ← WHERE가 없으면 통과한다
--      update t set gone = true returning id       → 42501
--      SELECT 정책을 using(true)로 바꾸면            → 1행
--
--  🔵 **`WHERE`가 없을 때만 통과한다** — 그리고 `returning`을 붙이면 다시 42501이다.
--     즉 갈림선은 "WHERE가 있나"가 아니라 **행을 읽어야 하나**다. WHERE도 RETURNING도
--     없는 update만 읽기가 필요 없어서 SELECT 정책을 안 탄다. `where true`도 통과한다
--     (계획에서 지워진다). 실행 계획을 바꿔도(`enable_indexscan=off`) 결과는 같다 —
--     ⚠️ **계획 탓이 아니다.** 이걸 확인해 두지 않으면 "우리 서버에서만 그렇다"로 읽는다.
--
--     → PostgREST의 PATCH는 항상 `?id=eq.…`를 WHERE로 만든다. **우회할 방법이 없다.**
--
--  ## 그래서 무엇을 고치나 — 두 후보를 **둘 다 실측했다**
--
--  ① `comments_read`에 `or user_id = auth.uid() or is_discovery_owner(...)`를 더한다
--     → 삭제 3경로 전부 통과. **그런데 지운 댓글이 목록에 다시 나타난다**(실측:
--       작성자에게 3행 중 2행이 지워진 것 · 제3자에게도 1행). [ReactionService.comments]는
--       `deleted_at` 필터를 **안 보낸다**(0007 3절이 "정책에 넣는다"고 정한 그 지점이다).
--       화면 16이 지운 댓글을 그리고, `댓글 3`은 2를 말한다 — **오류 없이 어긋난다.**
--     ❌ 탈락. 삭제를 고치려고 읽기를 깼다.
--
--  ② 삭제를 `security definer` 함수로 뺀다 → 3경로 전부 옳고, 지운 댓글은 계속 안 보인다.
--     ✅ 채택. 이 저장소가 이미 두 번 쓴 방법이다(`can_see_discovery` ·
--        `is_discovery_owner` — 0007 머리말의 "정책 안의 서브쿼리도 RLS를 탄다").
--
--  ⚠️ **`comments_soft_delete` 정책을 지우지 않는다.** 이제 그 정책으로는 **아무도
--     성공할 수 없다**(위 규칙 때문에). 그게 의도다 — 클라이언트의 직접 PATCH가
--     전부 막힌 상태가 되고, 삭제는 아래 함수 **한 길로만** 지나간다. 0008의 진단
--     1번(`update 정책 개수 = 1`)도 그대로 ✅다.
--
--  ⚠️ **트리거는 여전히 필요하다.** 트리거는 RLS와 무관하게 돈다 — `security definer`
--     함수의 update도 `comments_only_soft_delete`를 지나간다(아래 4절이 그걸 실측한다).
--     즉 C-9 "수정 불가"의 자물쇠는 0008이 만든 그 트리거이고, 이 파일은 그것을 안 건드린다.
--
--  ✅ 두 번 돌려도 안전하다 (`create or replace`).
--  ⚠️ 이 파일도 **마지막 문장이 진단 SELECT**다 (SQL Editor는 마지막 결과만 보여준다).


-- ─────────────────────────────────────────────────────────────
-- 1. 삭제를 함수로 뺀다 — 권한 판정은 **함수 안에서** 한다
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 **`security definer`는 RLS를 통째로 지나간다.** 그러니 "누가 지울 수 있나"를
--    이 안에서 직접 세지 않으면 **아무나 남의 댓글을 지울 수 있다.** 0007이 정책에
--    적어 둔 조건(`본인 or 사진 주인`)을 여기로 옮겨 오는 것이고, 옮겨 온 조건이
--    실제로 막는지는 아래 4절 진단이 아니라 **로컬 Postgres 실측**이 확인했다
--    (제3자 → false · 지운 댓글이 목록에 안 보임 · 집계 `0 · 1 · false`).
--
-- ⚠️ **`raise exception`이 아니라 `boolean`을 돌려준다.** 예외로 만들면 PostgREST가
--    HTTP 400/500으로 감싸고, 그러면 **"권한 없음"과 "서버 오류"가 화면에서 같아진다**
--    (`카카오맵 401은 두 원인`과 같은 모양이다). false는 "안 지웠다"만 말한다.
--    🔴 그래서 **클라이언트는 본문을 반드시 읽어야 한다** — `204`만 보고 성공으로
--       접으면 안 지워진 댓글이 지워진 것처럼 사라진다(낙관적 갱신). 0008 머리말의
--       "남의 댓글 삭제도 204 + 0행"과 정확히 같은 함정이다.
--
-- ⚠️ **이미 지운 댓글은 `true`다**(멱등). 두 번 눌린 것을 실패로 보이면 사용자는
--    이미 지워진 댓글을 못 지웠다고 읽는다 — [ReactionService.report]가 중복 신고를
--    성공으로 접는 것과 같은 이유다. 단, **권한이 없으면 이미 지워졌어도 false**다
--    (남의 댓글이 지워졌는지 여부를 알려 주지 않는다).
create or replace function public.delete_comment(c_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  c_user_id      uuid;
  c_discovery_id uuid;
  c_deleted_at   timestamptz;
begin
  -- 🔴 **로그인 안 한 호출을 맨 먼저 끊는다.** 아래 판정보다 앞에 두는 이유는
  --    이 파일이 실제로 뚫렸기 때문이다 — 실측 [6]번에서 **anon이 남의 댓글을
  --    지웠다**(`delete_comment → true`).
  --
  --    원인: `c_user_id = auth.uid()`는 `auth.uid()`가 null이면 **false가 아니라
  --    null**이다. `null or false` = null이고, plpgsql의 `if not null then`은
  --    **참이 아니므로 그 분기를 건너뛴다.** 즉 `return false`에 도달하지 못하고
  --    삭제까지 흘러간다. 🔴 **막는 코드가 있는데 통과한다** — 3항 논리의 함정이고
  --    `if`로 쓰면 `where`와 달리 "거짓"과 "모름"이 같은 길로 간다.
  --
  --    ⚠️ 내가 이 파일 첫 판에 바로 위 줄에 **"그래서 anon은 여기서 걸린다"**고
  --       주석까지 달아 두었다. 주석이 틀렸고 실측이 그것을 잡았다 — 이 저장소에서
  --       **내 주석이 검사를 승인한** 사고와 같은 층이다(그때는 세 번이었다).
  --       그래서 아래 4절 진단이 이 가드를 **글자로** 센다.
  if auth.uid() is null then
    return false;
  end if;

  -- 🔴 `security definer`라 이 select는 RLS를 안 탄다 — 즉 **지워진 댓글도 읽힌다.**
  --    그게 필요하다(멱등 판정을 하려면 지워진 것도 봐야 한다).
  select user_id, discovery_id, deleted_at
    into c_user_id, c_discovery_id, c_deleted_at
    from public.comments where id = c_id;

  -- 없는 id. 있는지 없는지를 굳이 구분해 주지 않는다.
  if not found then
    return false;
  end if;

  -- C-9: 작성자 또는 사진 소유자.
  -- ⚠️ `coalesce`로 감싼다. 위에서 `auth.uid()` null을 이미 끊었으므로 지금은
  --    남을 수 없는 null이지만, **두 겹으로 둔다** — 위 가드를 누가 지우면 여기서
  --    다시 걸린다. 위 가드 하나에만 의존하면 그것을 지운 것이 아무 증상도 안 낸다.
  if not coalesce(
       c_user_id = auth.uid() or public.is_discovery_owner(c_discovery_id),
       false
     ) then
    return false;
  end if;

  if c_deleted_at is not null then
    return true;   -- 멱등
  end if;

  -- 🔴 이 update는 `comments_only_soft_delete` 트리거를 **지나간다**(0008 2절).
  --    트리거는 RLS와 무관하게 돌기 때문이고, 그래서 C-9의 자물쇠가 안 풀린다.
  update public.comments set deleted_at = now() where id = c_id;
  return true;
end;
$$;

-- ⚠️ Supabase에서 함수는 기본적으로 `PUBLIC`에게 실행 권한이 있다. 그래서 이 grant는
--    권한을 **주는** 것이 아니라 명시하는 것이다 — 실제 자물쇠는 위 함수 안의 판정이다.
--    (여기에 `revoke ... from public`을 넣지 않는다: 0001~0008의 다른 함수들과 방식이
--     갈리면 다음 사람이 어느 쪽이 규칙인지 모른다.)
grant execute on function public.delete_comment(uuid) to authenticated;


-- ─────────────────────────────────────────────────────────────
-- 2. 진단 — **이 파일이 서버에 갔는가**
-- ─────────────────────────────────────────────────────────────
--
-- ⚠️ **문장이 하나다.** 문장을 추가하면 SQL Editor가 마지막 것만 보여준다.
--
-- 🔴 "함수가 있다"와 "그 함수가 권한을 센다"는 다르다 — 3번이 그것을 본다.
--    조건을 지운 함수도 1·2번은 그대로 ✅다.
with 진단 as (

  select 1 as 순서, 'delete_comment 함수가 있다' as 항목, 'true' as 기대,
         (select count(*) > 0 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'delete_comment')::text as 실제

  union all
  -- RLS를 지나가야 한다. `security invoker`면 이 파일은 아무것도 안 고친 것이다.
  select 2, 'security definer로 붙었다', 'true',
         (select prosecdef from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'delete_comment' limit 1)::text

  union all
  -- 🔴 권한 판정 두 갈래가 **함수 본문에 남아 있는가.** definer 함수에서 이게 빠지면
  --    아무나 남의 댓글을 지운다 — 그리고 **아무 오류도 안 난다.**
  select 3, '본문이 권한을 센다 (본인 · 사진주인)', '2',
         (select (
            (prosrc like '%c_user_id = auth.uid()%')::int +
            (prosrc like '%is_discovery_owner(c_discovery_id)%')::int
          )::text
            from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'delete_comment' limit 1)

  union all
  -- 🔴 **anon 가드 두 겹이 남아 있는가.** 이 파일 첫 판이 정확히 여기서 뚫렸다 —
  --    `auth.uid()`가 null일 때 `= null`이 null이 되어 `if not null`이 분기를
  --    건너뛰고 **anon이 남의 댓글을 지웠다**(로컬 실측). 두 겹 중 하나만 남아도
  --    지금은 막히지만, **하나가 사라진 것을 아무도 못 본다** — 그래서 2를 센다.
  select 4, 'anon 가드가 두 겹이다 (uid null · coalesce)', '2',
         (select (
            (prosrc like '%auth.uid() is null%')::int +
            (prosrc like '%coalesce(%')::int
          )::text
            from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'delete_comment' limit 1)

  union all
  -- 0008의 트리거가 아직 붙어 있는가. 이 파일은 그걸 전제로 만들어졌다.
  select 5, '0008 트리거가 그대로 있다', 'comments_only_soft_delete',
         (select coalesce(string_agg(tgname, ', ' order by tgname), '(없음)')
            from pg_trigger where tgrelid = 'public.comments'::regclass and not tgisinternal)

  union all
  -- 원인 그 자체. 이 조건이 살아 있어야 지운 댓글이 안 보인다 —
  -- 🔴 그리고 이것 때문에 직접 PATCH는 계속 42501이다(그게 의도다).
  select 6, 'comments_read가 아직 deleted_at을 가린다', 'true',
         (select (qual like '%deleted_at IS NULL%')::text from pg_policies
           where schemaname = 'public' and tablename = 'comments'
             and policyname = 'comments_read' limit 1)
)
select
  항목,
  coalesce(실제, '(없음)') as 실제,
  기대,
  case when 실제 = 기대 then '✅' else '❌' end as 판정
from 진단
order by 순서;
