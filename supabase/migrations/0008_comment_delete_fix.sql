-- ════════════════════════════════════════════════════════════════
--  0008 — 댓글 삭제가 서버에서 막히던 것을 고치고, **서버가 자기 상태를 말하게** 한다
-- ════════════════════════════════════════════════════════════════
--
--  🔴 무엇이 고장났나 (2026-08-12 실측 · 익명 계정 2개로 실서버에 태웠다)
--
--     작성자가 자기 댓글 삭제        → 403 42501
--     사진 주인이 남의 댓글 삭제      → 403 42501
--     남의 댓글 삭제 (막혀야 정상)     → 204 + 0행  ← 여기만 갈린다
--     아무 칸도 없는 빈 PATCH        → 204
--
--     `using`은 통과한다(남의 댓글은 대상 0행으로 갈리므로). 즉 **`with check`가
--     항상 거짓**이다 — `deleted_at`을 ISO로 줘도, 공백 형식으로 줘도, null로 줘도,
--     심지어 `created_at`만 바꿔도 403이고, **칸을 하나도 안 바꿀 때만** 204다.
--
--  ⚠️ **원인을 파일에서 찾지 못했다.** `0007`의 원문은 정확하다(바이트까지 확인했다):
--
--        for update using (user_id = auth.uid() or public.is_discovery_owner(discovery_id))
--        with check (deleted_at is not null)
--
--     `deleted_at is not null`이 참인데 거부된다면 **서버에 붙은 식이 이 파일과 다르다.**
--     그리고 anon 키로는 `pg_policies`를 읽을 수 없어서(`PGRST205`) 내가 확인할 방법이
--     없다. 🔴 **그래서 이 파일의 절반은 "고치는 것"이 아니라 "서버가 말하게 하는 것"이다.**
--     추측으로 고치면 고쳐진 척하고 넘어간다 — 화면 16을 만드는 날 다시 터진다.
--
--  ✅ 두 번 돌려도 안전하다 (`drop policy if exists` · `create or replace`).
--  ⚠️ 이 파일은 **마지막 문장이 진단 SELECT**다. SQL Editor는 마지막 결과만 보여주므로
--     그렇게 배치했다. 오너는 그 표를 그대로 보여주면 된다.


-- ─────────────────────────────────────────────────────────────
-- 1. 정책을 다시 붙인다 — 다만 **`with check`를 다르게 쓴다**
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 `with check (deleted_at is not null)`은 **의도가 절반만 담긴 식이었다.**
--    이 식은 "삭제 표시가 채워졌다"만 본다. 그런데 update의 `with check`는 **바뀐 뒤의
--    행 전체**에 적용되므로, 본문·작성자·기록 id가 함께 바뀌어도 `deleted_at`만
--    채워져 있으면 **전부 통과한다.** 즉 C-9 "수정 불가"를 이 식이 지키고 있던 게
--    아니라 **PostgREST가 body를 안 보내 준 덕에** 지켜지고 있었다.
--
--    실측이 그것을 보여준다: `deleted_at`과 `body`를 **같이** 보낸 PATCH도 403이었지만,
--    그건 이 식이 막은 게 아니다(그 식은 참이었다).
--
--    → 그래서 이번엔 **바뀌어서는 안 되는 칸들을 이름으로 못 박는다.** 서버가 스스로
--      지키게 되고, 클라이언트가 무엇을 보내든 상관이 없어진다.
--
-- ⚠️ **`old`를 정책에서 볼 수 없다.** `using`은 이전 행, `with check`는 이후 행을
--    각각 보지만 **한 식 안에서 둘을 비교할 수는 없다** — 그건 트리거의 일이다.
--    그래서 역할을 나눈다:
--      · 정책     = 누가 손댈 수 있나 (`using`) + 결과가 삭제 상태인가 (`with check`)
--      · 트리거   = **바뀌면 안 되는 칸이 그대로인가** (아래 2절)
--    한쪽만 두면 반쪽이 된다. 정책만 두면 본문 수정이 열리고(위), 트리거만 두면
--    남이 내 댓글에 손댈 수 있다.
drop policy if exists comments_soft_delete on public.comments;
create policy comments_soft_delete on public.comments
  for update using (
    -- 본인, 또는 사진 소유자 (C-9 권고)
    user_id = auth.uid()
    or public.is_discovery_owner(discovery_id)
  )
  with check (
    -- 결과는 반드시 "삭제된 상태"여야 한다. 되살리기(`deleted_at = null`)는 없다 —
    -- C-9에 그 기능이 없고, 열어 두면 지운 댓글이 다시 나타난다.
    deleted_at is not null
  );


-- ─────────────────────────────────────────────────────────────
-- 2. 🔴 트리거 — **본문·작성자·기록은 바뀔 수 없다** (C-9 수정 불가의 실제 자물쇠)
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 **왜 정책으로 안 되나.** 위에 적었듯 정책은 이전 행과 이후 행을 **비교할 수
--    없다.** `with check`에 `body = body`처럼 쓰면 그건 "이후 행의 body가 이후 행의
--    body와 같다"라서 **항상 참**이다 — 검사처럼 보이는데 아무것도 안 잰다.
--    (이 저장소에서 같은 모양에 두 번 속았다: 지표가 스스로 따라 움직여
--     아무것도 빨개지지 않는 자리.)
--
-- ⚠️ **`before update of deleted_at`으로 좁히지 않는다.** 좁히면 `body`만 바꾸는
--    update가 이 트리거를 **지나가지 않는다** — 정확히 막고 싶은 것이 그것이다.
--    0004의 `users_enforce_region_change`는 `of dong_code`로 좁혔지만 그건 반대
--    상황이다(그 칸이 바뀔 때만 검사할 게 있었다).
create or replace function public.comments_only_soft_delete()
returns trigger
language plpgsql
as $$
begin
  -- 이 셋이 바뀌면 그건 삭제가 아니라 **수정**이다. C-9은 수정을 금지했다.
  if new.body is distinct from old.body then
    raise exception '댓글 본문은 수정할 수 없다 (C-9)';
  end if;
  if new.user_id is distinct from old.user_id then
    raise exception '댓글 작성자는 바꿀 수 없다';
  end if;
  if new.discovery_id is distinct from old.discovery_id then
    raise exception '댓글이 달린 기록은 바꿀 수 없다';
  end if;
  -- 🔴 `created_at`도 막는다. 화면 16은 **최신순**으로 그리므로, 이 값을 바꿀 수
  --    있으면 남의 댓글을 맨 위로 올릴 수 있다 — 내용은 그대로라 **아무 증상이 없다.**
  if new.created_at is distinct from old.created_at then
    raise exception '댓글 작성 시각은 바꿀 수 없다';
  end if;
  -- 되살리기 금지. 위 정책의 `with check`와 겹치지만, 정책은 나중에 누가 고칠 수
  -- 있고 이쪽은 남는다. 두 겹으로 둔다.
  if new.deleted_at is null then
    raise exception '지운 댓글을 되살릴 수 없다';
  end if;
  return new;
end;
$$;

drop trigger if exists comments_only_soft_delete on public.comments;
create trigger comments_only_soft_delete
  before update on public.comments
  for each row execute function public.comments_only_soft_delete();


-- ─────────────────────────────────────────────────────────────
-- 3. `discovery_reactions`에 `deleted_at is null`을 **글자로** 적는다
-- ─────────────────────────────────────────────────────────────
--
-- 🔵 **처음 여기에 "지운 댓글을 세고 있었다 — 진짜 결함이다"라고 썼다. 틀렸다.**
--    쓰기 전에 실서버로 쟀고, 내 주장이 깨졌다(2026-08-12):
--
--      A의 **비공개** 기록에 A가 댓글 2개 → B가 집계를 부르면 `comment_count = 0`
--      같은 기록을 A가 부르면 2 · 공개 기록은 A·B 모두 1
--
--    B가 0을 받는다는 것은 **count에도 `comments_read` 정책이 걸린다**는 뜻이다.
--    그 정책은 `deleted_at is null`을 요구하므로 **지운 댓글은 이미 안 세어진다.**
--    (같은 실측이 하나 더 확인해 줬다: 비공개 기록의 반응 수가 남에게 **새지 않는다.**)
--
-- ⚠️ **그래도 조건을 적는다. 다만 "결함 수정"이 아니라 "의존을 끊는 것"이다.**
--    지금 이 함수가 옳게 도는 이유는 함수 본문이 아니라 **RLS 덕분**이다.
--    누군가 이 함수에 `security definer`를 붙이는 순간(위 `can_see_discovery`처럼
--    붙이고 싶어지는 자리다) RLS가 빠지고 **지운 댓글이 조용히 다시 세어진다** —
--    `댓글 3`을 쓰고 2개를 그리게 되는데, 화면에는 아무 오류가 없다.
--    조건을 글자로 두면 그 변경이 집계를 깨지 않는다.
--
-- ⚠️ **이건 어떤 테스트도 잡지 못하는 종류다.** JVM 테스트의 응답 본문은 내가
--    만든 것이고, 계약 테스트는 인자명·칸 이름만 대조한다 — **`where` 절은 아무도
--    안 읽는다.** 그래서 아래 4절이 함수 원문을 읽어서 잰다.
create or replace function public.discovery_reactions(d_id uuid)
returns table (like_count int, comment_count int, liked_by_me boolean)
language sql
stable
set search_path = public
as $$
  select
    (select count(*)::int from public.likes    l where l.discovery_id = d_id),
    -- 🔴 `deleted_at is null`. 이게 없으면 `댓글 3`을 쓰고 2개를 그린다.
    (select count(*)::int from public.comments c
      where c.discovery_id = d_id and c.deleted_at is null),
    exists (select 1 from public.likes l
             where l.discovery_id = d_id and l.user_id = auth.uid());
$$;

grant execute on function public.discovery_reactions(uuid) to authenticated;


-- ─────────────────────────────────────────────────────────────
-- 4. 진단 — **서버가 자기 상태를 말한다.** 이 표를 그대로 보여주면 된다
-- ─────────────────────────────────────────────────────────────
--
-- ⚠️ **문장이 하나다.** SQL Editor는 여러 문장을 돌리면 마지막 결과만 보여준다.
--    문장을 추가하지 않는다.
--
-- 🔴 위 1~3절이 돌았어도 **403이 계속될 수 있다.** 그러면 원인은 우리 정책이 아니라
--    서버에 따로 붙은 무엇이다 — 그것을 아래 `정책 원문`·`추가 트리거` 칸이 말한다.
--    ⚠️ **`판정` 칸이 ✅인 것만 보지 말고, `정책 원문` 줄의 값을 그대로 옮겨 준다.**
--    그 문장이 이 파일과 다르면 그게 답이다.
with 진단 as (

  select 1 as 순서, 'comments의 update 정책 개수' as 항목, '1' as 기대,
         (select count(*)::text from pg_policies
           where schemaname = 'public' and tablename = 'comments' and cmd = 'UPDATE') as 실제

  union all
  -- 🔴 여기가 이 파일의 핵심이다. **서버가 가진 식을 글자로 받는다.**
  --    내가 anon 키로 못 읽던 그 값이다.
  select 2, 'update 정책의 with check 원문', 'deleted_at IS NOT NULL',
         (select coalesce(with_check, '(없음)') from pg_policies
           where schemaname = 'public' and tablename = 'comments' and cmd = 'UPDATE'
           limit 1)

  union all
  select 3, 'update 정책의 using 원문', '(본인 or 사진주인)',
         (select coalesce(qual, '(없음)') from pg_policies
           where schemaname = 'public' and tablename = 'comments' and cmd = 'UPDATE'
           limit 1)

  union all
  -- 🔴 **restrictive 정책이 하나라도 있으면 AND로 합쳐진다** — permissive 정책이
  --    전부 참이어도 거부된다. 403의 유력한 후보이고, 대시보드 화면으로는 안 보인다.
  select 4, 'comments의 restrictive 정책 0개', '0',
         (select count(*)::text from pg_policies
           where schemaname = 'public' and tablename = 'comments'
             and permissive = 'RESTRICTIVE')

  union all
  -- 🔴 우리가 만든 것 말고 **다른 트리거가 붙어 있나.** 붙어 있으면 그것이 값을
  --    되돌리거나 예외를 던져 403처럼 보일 수 있다.
  select 5, 'comments의 트리거 (우리 것 1개뿐)', 'comments_only_soft_delete',
         (select coalesce(string_agg(tgname, ', ' order by tgname), '(없음)')
            from pg_trigger
           where tgrelid = 'public.comments'::regclass and not tgisinternal)

  union all
  -- authenticated에게 update 권한이 있는가. 없으면 메시지가 달랐겠지만 세어 둔다.
  select 6, 'authenticated의 comments UPDATE 권한', 'true',
         has_table_privilege('authenticated', 'public.comments', 'UPDATE')::text

  union all
  -- 🔴 3절이 실제로 반영됐는가 — **함수 원문에 `deleted_at is null`이 있는가.**
  --    "함수가 있다"와 "지운 댓글을 안 센다"는 다르다.
  select 7, '반응 집계가 지운 댓글을 뺀다', 'true',
         (select (prosrc like '%deleted_at is null%')::text
            from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'discovery_reactions'
           limit 1)

  union all
  -- 🔴 트리거 **본문**이 네 칸을 다 막는가. "트리거가 붙었다"(5번)와 "그 트리거가
  --    무엇을 막는가"는 다르다 — 조건 하나를 빼도 5번은 그대로 ✅다.
  select 8, '트리거가 막는 칸 4개', '4',
         (select (
            (prosrc like '%new.body is distinct from old.body%')::int +
            (prosrc like '%new.user_id is distinct from old.user_id%')::int +
            (prosrc like '%new.discovery_id is distinct from old.discovery_id%')::int +
            (prosrc like '%new.created_at is distinct from old.created_at%')::int
          )::text
            from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public' and p.proname = 'comments_only_soft_delete'
           limit 1)

  -- ⚠️ **여기서 실제 update를 태워 보지 않는다.** 태우려면 임시 사용자·기록·댓글을
  --    만들어야 하고, `discoveries`에는 B-5 하루중복 유니크 제약이 걸려 있어서
  --    (실측: `discoveries_same_flower_place_per_day` 23505) 오너가 두 번 돌리면
  --    **두 번째에 실패한다.** 이 파일의 "두 번 돌려도 안전하다"가 깨진다.
  --    → 트리거가 실제로 막는지는 **앱 쪽 실측**으로 잰다(내가 익명 계정 2개로
  --      태우는 그 스크립트다). 여기서는 원문만 본다.
)
select
  항목,
  coalesce(실제, '(없음)') as 실제,
  기대,
  case
    when 순서 in (2, 3) then '👀 값을 그대로 보여줄 것'
    when 실제 = 기대 then '✅'
    else '❌'
  end as 판정
from 진단
order by 순서;
