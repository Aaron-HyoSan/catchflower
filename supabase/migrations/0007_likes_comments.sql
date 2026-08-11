-- ════════════════════════════════════════════════════════════════
--  0007. 좋아요 · 댓글 (화면 16 꽃 기록 상세)
-- ════════════════════════════════════════════════════════════════
--
--  A 문서 16번 표가 요구하는 것: `좋아요 12 · 댓글 3` · `댓글을 남겨보세요` · 버튼 `등록`.
--  지금까지 이 두 기능은 **테이블이 아예 없었다** — 화면도, 저장할 곳도 없었다.
--
--  🔴 **이 파일의 핵심은 테이블이 아니라 "누가 볼 수 있나"다.**
--     좋아요·댓글은 **남의 기록에 붙는다.** discoveries는 공개 범위·차단·신고 숨김이
--     걸린 테이블인데, 거기 붙는 자식 테이블에 그 규칙을 다시 안 걸면
--     **비공개 기록의 댓글이 읽힌다.** 기록 본문은 안 보이는데 댓글은 보이는 상태다 —
--     화면에는 "댓글만 뜨는 이상한 칸"이 아니라 **아무 증상도 없다**(우리 앱은
--     기록을 먼저 읽어서 그 화면을 안 열기 때문이다). API를 직접 부르면 새어 나간다.
--
--  🔴 **정책 안의 서브쿼리도 RLS를 탄다** (0001 7-3절에서 두 번 뚫린 것과 같은 함정).
--     `exists (select 1 from discoveries where id = discovery_id)` 로 쓰면
--     그 서브쿼리가 **호출자에게 보이는 discoveries만** 본다. 그래서 판정을
--     `security definer` 함수 `can_see_discovery(uuid)`로 뺀다.
--     함수는 RLS를 지나 판정만 하고 **boolean 하나만** 내보낸다.


-- ─────────────────────────────────────────────────────────────
-- 1. 이 기록을 내가 볼 수 있는가 — 좋아요·댓글 정책이 전부 이걸 쓴다
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 **`discoveries_read` 정책과 같은 조건을 여기 한 번 더 쓴다.** 규칙이 두 곳에
--    생기는 건 위험하지만, 대안이 더 나쁘다: 정책 본문에서 discoveries를 세면
--    위 머리말대로 RLS에 걸려 **비공개 기록이 "안 보이니까 통과"**가 된다.
--    두 곳이 어긋나는 것은 아래 3절 회귀 검사(`0007` 확인 SQL)가 잡는다.
create or replace function public.can_see_discovery(d_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.discoveries d
    where d.id = d_id
      and (
        d.user_id = auth.uid()
        or (
          auth.uid() is not null
          and d.visibility <> 'private'
          and not public.is_report_hidden(d.id)
          and not public.is_blocked_between(auth.uid(), d.user_id)
          and (d.visibility = 'public' or public.are_friends(auth.uid(), d.user_id))
        )
      )
  );
$$;

grant execute on function public.can_see_discovery(uuid) to anon, authenticated;


-- ─────────────────────────────────────────────────────────────
-- 2. 좋아요
-- ─────────────────────────────────────────────────────────────
--
-- ⚠️ **id 컬럼을 두지 않는다.** (discovery_id, user_id) 자체가 키다 —
--    별도 id를 두면 **같은 사람이 같은 기록에 좋아요를 두 번** 넣을 수 있고,
--    그러면 `좋아요 12`가 12명이 아니라 12번이 된다. 화면에서 구별이 안 된다.
create table if not exists public.likes (
  discovery_id  uuid        not null references public.discoveries(id) on delete cascade,
  user_id       uuid        not null references public.users(id)       on delete cascade,
  created_at    timestamptz not null default now(),

  primary key (discovery_id, user_id)
);

-- 개수를 세는 게 유일한 읽기 패턴이다(화면 16 `좋아요 12`).
create index if not exists likes_discovery_idx on public.likes (discovery_id);
-- 마이페이지에서 "내가 좋아요한 기록"을 나중에 쓸 때를 위한 역방향.
create index if not exists likes_user_idx on public.likes (user_id);

alter table public.likes enable row level security;

-- 볼 수 있는 기록의 좋아요만 읽는다.
drop policy if exists likes_read on public.likes;
create policy likes_read on public.likes
  for select using (public.can_see_discovery(discovery_id));

-- 🔴 **`with check`에 두 조건이 다 필요하다.**
--    `user_id = auth.uid()`만 두면 **비공개 기록에도 좋아요를 넣을 수 있다** —
--    행이 남고, 그 기록 주인의 화면에서 `좋아요 1`이 된다(누가 눌렀는지는 안 보인다).
drop policy if exists likes_insert_self on public.likes;
create policy likes_insert_self on public.likes
  for insert with check (
    user_id = auth.uid()
    and public.can_see_discovery(discovery_id)
  );

-- 취소는 본인 것만. (A 문서 318줄 `좋아요를 취소했어요`)
--
-- ⚠️ 취소에는 `can_see_discovery`를 걸지 않는다. 눌러 둔 뒤 상대가 비공개로 바꾸면
--    조건이 거짓이 되어 **자기가 누른 좋아요를 영구히 못 지운다.**
drop policy if exists likes_delete_self on public.likes;
create policy likes_delete_self on public.likes
  for delete using (user_id = auth.uid());

-- 수정할 것이 없다 — `update` 정책을 일부러 만들지 않는다(정책이 없으면 거부된다).


-- ─────────────────────────────────────────────────────────────
-- 3. 댓글
-- ─────────────────────────────────────────────────────────────
--
-- 정책 C-9 (권고 · 오너 미답): **200자 · 수정 불가 · 본인과 사진 소유자가 삭제 가능.**
-- 🔴 길이 상한은 **서버에도 둔다.** 클라이언트만 막으면 API로 우회되고, 그때
--    화면 16의 댓글 칸이 끝없이 늘어난다(레이아웃이 깨지는 게 아니라 스크롤이 늘어난다 —
--    증상이 약해서 발견이 늦다).
create table if not exists public.comments (
  id            uuid        primary key default gen_random_uuid(),
  discovery_id  uuid        not null references public.discoveries(id) on delete cascade,
  user_id       uuid        not null references public.users(id)       on delete cascade,
  body          text        not null,
  created_at    timestamptz not null default now(),
  deleted_at    timestamptz,

  -- C-9 200자. 빈 댓글도 막는다(`등록`이 눌리면 안 되지만 API로는 들어온다).
  constraint comments_body_len check (char_length(body) between 1 and 200)
);

-- 화면 16은 한 기록의 댓글을 최신순으로 읽는다(A 문서 `최신순 ▾`).
create index if not exists comments_discovery_idx
  on public.comments (discovery_id, created_at desc);

alter table public.comments enable row level security;

-- 🔴 **삭제된 댓글을 읽히지 않게 하는 것을 정책에 넣는다.** 앱 쿼리에
--    `deleted_at is null`을 넣는 것으로 대신하면, 한 군데서 빼먹는 순간 새어 나가고
--    **그 화면만 조용히 다르게 보인다.**
drop policy if exists comments_read on public.comments;
create policy comments_read on public.comments
  for select using (
    deleted_at is null
    and public.can_see_discovery(discovery_id)
  );

drop policy if exists comments_insert_self on public.comments;
create policy comments_insert_self on public.comments
  for insert with check (
    user_id = auth.uid()
    and public.can_see_discovery(discovery_id)
    and deleted_at is null
  );

-- 사진 소유자 판정. 🔴 **이것도 `security definer`여야 한다** — 정책 안에서
--    discoveries를 직접 보면 비공개 기록이 "안 보이니 소유자도 아님"이 되어,
--    자기 기록에 달린 댓글을 못 지운다.
--
-- ⚠️ **정책보다 먼저 만든다.** `create policy`는 본문의 함수를 그 자리에서 찾으므로,
--    아래 `comments_soft_delete`보다 뒤에 두면 `function … does not exist`로
--    **오너의 붙여넣기가 거기서 멈춘다.** (처음 이 순서로 썼다.)
create or replace function public.is_discovery_owner(d_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.discoveries d
    where d.id = d_id and d.user_id = auth.uid()
  );
$$;

grant execute on function public.is_discovery_owner(uuid) to anon, authenticated;

-- C-9 **수정 불가.** `update` 정책을 만들지 않으면 수정이 거부된다.
--
-- 🔴 그런데 삭제도 `update`(soft delete)로 해야 한다 — 하드 삭제하면 화면 16에서
--    댓글 3개가 갑자기 2개가 되고, 대댓글이 붙는 구조로 갈 때 되돌릴 수 없다.
--    그래서 **`deleted_at`을 채우는 것만** 허용하는 정책을 둔다.
--    `using`은 "누가 손댈 수 있나", `with check`는 "결과가 무엇이어야 하나"다 —
--    ⚠️ `with check`를 빼면 **본문을 바꾸는 수정이 열린다**(C-9 위반).
drop policy if exists comments_soft_delete on public.comments;
create policy comments_soft_delete on public.comments
  for update using (
    -- 본인, 또는 사진 소유자 (C-9 권고)
    user_id = auth.uid()
    or public.is_discovery_owner(discovery_id)
  )
  with check (
    deleted_at is not null
  );


-- ─────────────────────────────────────────────────────────────
-- 4. 개수 세기 — 화면 16 `좋아요 12 · 댓글 3`
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 **왜 함수인가.** 클라이언트가 `select count(*)`로 세면 좋아요 행 전체를
--    받아야 하거나(트래픽) `Prefer: count=exact` 헤더에 의존한다. 그리고
--    "내가 눌렀는가"를 따로 한 번 더 물어야 해서 **왕복이 3번**이 된다.
--    화면 16은 이 셋을 한 번에 받는다.
--
-- ⚠️ `security definer`가 **아니다.** 여기는 판정이 아니라 집계라서, 호출자의
--    RLS를 그대로 타야 맞다 — 위 `likes_read`·`comments_read`가 걸리므로
--    **볼 수 없는 기록에는 0이 나온다**(오류가 아니라 0이다).
create or replace function public.discovery_reactions(d_id uuid)
returns table (like_count int, comment_count int, liked_by_me boolean)
language sql
stable
set search_path = public
as $$
  select
    (select count(*)::int from public.likes    l where l.discovery_id = d_id),
    (select count(*)::int from public.comments c where c.discovery_id = d_id),
    exists (select 1 from public.likes l
             where l.discovery_id = d_id and l.user_id = auth.uid());
$$;

grant execute on function public.discovery_reactions(uuid) to authenticated;
