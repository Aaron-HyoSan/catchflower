-- ════════════════════════════════════════════════════════════════
--  캐치플라워 DB 스키마 — 이 파일 전체를 복사해 SQL Editor에 붙여넣고 Run
-- ════════════════════════════════════════════════════════════════
--
--  이 파일은 자동 합본이다. 원본은 supabase/migrations/ 이고
--  고칠 일이 있으면 원본을 고친 뒤 `python3 오너_실행/build_합본.py`로 다시 만든다.
--  **이 파일을 직접 고치면 원본과 어긋나고, 어긋난 걸 알 방법이 없다.**
--
--  ✅ 두 번 돌려도 안전하다 (if not exists · drop policy if exists ·
--     create or replace · on conflict do update 로 작성했다).
--  ✅ 중간에 실패해도 이미 만들어진 것은 남는다 — 고친 뒤 그대로 다시 Run 하면 된다.
--
--  ⚠️ 다 돌린 뒤 `오너_실행/02_적용확인.sql` 을 붙여넣어 실제로 들어갔는지 확인한다.
--     "Success. No rows returned" 는 **성공했다는 뜻이 아니다** — 아무 것도 안 만들고
--     끝났을 때도 같은 메시지가 나온다.
--


-- ══════════════════════════════════════════════════════════════
-- ▼ 0001_init.sql
-- ══════════════════════════════════════════════════════════════

-- 캐치플라워 초기 스키마
--
-- 원본은 `프로젝트 맥락/공유계약_iOS_AOS.md` 1절이다. 컬럼명·enum 문자열은
-- 거기 있는 것을 그대로 옮긴 것이고, **이 파일에서 새로 만든 이름은 없다.**
-- 계약에 없는 컬럼을 여기서 추가하면 Android가 모르는 컬럼이 생긴다.
--
-- 공유계약 6절: **DB 스키마는 먼저 도달한 세션이 만들고 `진행.md`에 기록한다.**
-- 이 파일이 그 결과다 (2026-08-06 · iOS 세션).
--
-- 적용 방법: Supabase 대시보드 → SQL Editor에 붙여넣고 Run.
--   anon(publishable) 키로는 DDL을 못 돌린다 — 확인했다. 대시보드나
--   service_role 키가 필요하고, service_role 키는 클라이언트에 두면 안 되므로
--   **이 파일을 사람이 한 번 실행하는 방식으로 남긴다.**
--
-- 멱등하게 썼다 (`if not exists` · `drop policy if exists`). 두 번 돌려도 안전하다.

-- ─────────────────────────────────────────────────────────────
-- 0. enum — 공유계약 1-4절
--
-- 숫자가 아니라 문자열을 쓴다. 값이 추가돼도 순서가 깨지지 않는다.
-- Swift `ShareVisibility`·Kotlin enum의 raw value와 **철자까지 같아야 한다.**
-- ─────────────────────────────────────────────────────────────

do $$ begin
  create type flower_season   as enum ('spring', 'summer', 'autumn', 'winter');
exception when duplicate_object then null; end $$;

do $$ begin
  create type rarity          as enum ('common', 'normal', 'rare');
exception when duplicate_object then null; end $$;

do $$ begin
  create type ai_difficulty   as enum ('low', 'mid', 'high');
exception when duplicate_object then null; end $$;

do $$ begin
  create type visibility      as enum ('public', 'friends', 'private');
exception when duplicate_object then null; end $$;

do $$ begin
  create type friend_state    as enum ('pending', 'accepted', 'blocked');
exception when duplicate_object then null; end $$;

do $$ begin
  create type report_state    as enum ('open', 'hidden', 'resolved');
exception when duplicate_object then null; end $$;


-- ─────────────────────────────────────────────────────────────
-- 1. flowers — 도감 마스터 200종 (공유계약 1-1)
--
-- 원본은 `꽃도감/꽃목록_200종.csv`이고 이 테이블은 그걸 적재한 결과다.
-- 적재는 `공용_적재/build_flowers_json.py`가 만든 flowers.json으로 한다
-- (개화기 문자열 파싱을 여기서 다시 하지 않는다 — 계약 1-2).
-- ─────────────────────────────────────────────────────────────

create table if not exists public.flowers (
  -- 도감번호 1~200. **출시 후 재배치 금지** — 사용자 기록이 여기 걸린다.
  id                  int primary key,
  name                text          not null,
  scientific_name     text          not null,
  family              text          not null,
  -- ⚠️ 이미 파싱된 월 배열이다. `"3~4월"` 같은 문자열을 넣지 않는다 (계약 1-2).
  -- 랩어라운드도 여기서 풀려 있다: `12~4월` → {12,1,2,3,4}
  bloom_months        int[]         not null,
  -- 원문 표기. 화면 09 부연 `장미과 · 5~6월에 피는 꽃`에 쓴다.
  bloom_label         text          not null,
  season              flower_season not null,
  color               text          not null,
  rarity              rarity        not null,
  habitat             text          not null,
  ai_difficulty       ai_difficulty not null,
  -- CSV의 `비슷한꽃`은 이름 문자열이고, 적재 시 id로 변환된 결과가 들어간다.
  similar_flower_ids  int[]         not null default '{}',
  illust_batch        int           not null default 0,

  constraint flowers_id_range check (id between 1 and 200),
  -- 개화월이 비면 **개화월 하드 필터에서 그 종이 영구히 후보에 안 오른다.**
  -- 조용히 사라지므로 DB가 막는다.
  constraint flowers_bloom_months_not_empty check (array_length(bloom_months, 1) >= 1)
);

-- 개화월 하드 필터(A-1 필수 구현)가 매 판별마다 이 조건으로 조회한다.
create index if not exists flowers_bloom_months_idx on public.flowers using gin (bloom_months);
create index if not exists flowers_season_idx       on public.flowers (season);


-- ─────────────────────────────────────────────────────────────
-- 2. users — 계정 (auth.users의 부속 프로필)
--
-- **비밀번호·이메일을 우리가 갖지 않는다.** Supabase Auth(`auth.users`)가
-- 그걸 갖고, 이 테이블은 게임 데이터만 붙인다. `id`가 `auth.users.id`다.
-- ─────────────────────────────────────────────────────────────

create table if not exists public.users (
  id                  uuid primary key references auth.users(id) on delete cascade,
  nickname            text not null,
  -- 화면 02 활동 지역. B-6 지역 랭킹의 기준이다.
  -- **동 코드와 구 코드를 둘 다 둔다** — 런타임에 동에서 구를 유도할 수 없다.
  region_name         text,
  dong_code           text,
  gu_code             text,
  -- 기획서 9장: 활동 지역은 6개월에 한 번만 바꿀 수 있다.
  -- 마지막 변경 시각이 없으면 그 규칙을 서버에서 강제할 수 없다.
  region_changed_at   timestamptz,
  -- C-4: 만 14세 이상만 가입한다. 생년월일을 저장하지 않고
  -- **"확인했다"는 사실만** 남긴다 (안 쓰는 개인정보는 받지 않는다).
  age_confirmed_at    timestamptz,
  -- C-3: 연락처 친구 매칭용. **전화번호 원본이 아니라 SHA-256 해시다.**
  -- 화면 03 문구가 "저장하지 않아요"라고 약속했으므로 원본 컬럼을 만들지 않는다.
  phone_hash          text,
  created_at          timestamptz not null default now(),
  deleted_at          timestamptz
);

-- C-3 친구 매칭은 이 해시를 대조한다. 중복 계정도 막는다.
create unique index if not exists users_phone_hash_key on public.users (phone_hash)
  where phone_hash is not null;
create index if not exists users_dong_code_idx on public.users (dong_code);
create index if not exists users_gu_code_idx   on public.users (gu_code);


-- ─────────────────────────────────────────────────────────────
-- 3. discoveries — 발견 기록 (공유계약 1-3)
-- ─────────────────────────────────────────────────────────────

create table if not exists public.discoveries (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid       not null references public.users(id) on delete cascade,
  flower_id           int        not null references public.flowers(id),
  -- 장변 1600px 압축 1장 (A-4 권고). Storage 오브젝트 경로가 들어간다.
  photo_url           text,
  lat                 double precision,
  lng                 double precision,
  place_name          text,
  -- ⚠️ B-6이 여기 의존한다 — 동 코드와 구 코드를 **둘 다** 저장한다.
  dong_code           text,
  gu_code             text,
  visibility          visibility not null default 'private',
  -- 0.0~1.0. PlantNet 점수를 그대로 넣는다 (퍼센트로 곱하지 않는다).
  ai_confidence       double precision not null,
  -- **사용자가 몇 순위를 골랐는가.** B-3 어뷰징 가드 ②:
  -- 계속 하위 순위만 고르는 계정이 신호다. 이 컬럼이 없으면 그 신호가 사라진다.
  ai_picked_rank      int        not null default 1,
  is_first_discovery  boolean    not null default false,
  created_at          timestamptz not null default now(),
  -- C-8: 촬영 시각과 등록 시각의 차이를 검증한다.
  captured_at         timestamptz not null,
  -- 화면 13 한 줄 남기기. 길이 상한은 GamePolicy.mapShareNoteMaxLength와 같은 값.
  note                text,
  -- B-5 판정용 **한국 날짜**. 아래 트리거가 채운다 — 클라이언트가 넣지 않는다.
  --
  -- ⚠️ 왜 컬럼을 따로 두는가. 두 가지를 다 못 피했다.
  -- ① `captured_at::date`를 인덱스 식에 쓰면 `functions in index expression must be
  --    marked IMMUTABLE`로 **인덱스 자체가 안 만들어진다** (시간대에 의존해서 STABLE이다).
  -- ② 그렇다고 UTC 날짜로 세면 **오전 9시 이전 사진이 전날로 잡힌다.**
  --    07-01 08:00 KST = 06-30 23:00 UTC다. 아침 산책이 어제 기록이 되고,
  --    같은 아침에 두 번 찍으면 하루 1회 제한이 엉뚱하게 걸린다.
  -- `generated always as`도 못 쓴다 — 거기도 IMMUTABLE만 허용한다.
  captured_date       date,

  constraint discoveries_confidence_range check (ai_confidence between 0 and 1),
  constraint discoveries_picked_rank_range check (ai_picked_rank between 1 and 3),
  constraint discoveries_note_length       check (note is null or char_length(note) <= 40),
  -- 좌표는 둘 다 있거나 둘 다 없다. 한쪽만 있으면 지도에 못 찍는다.
  constraint discoveries_latlng_together   check ((lat is null) = (lng is null))
);

create index if not exists discoveries_user_idx      on public.discoveries (user_id, captured_at desc);
create index if not exists discoveries_flower_idx    on public.discoveries (flower_id);
create index if not exists discoveries_dong_idx      on public.discoveries (dong_code);
create index if not exists discoveries_gu_idx        on public.discoveries (gu_code);
-- 화면 14 지도는 공개 기록만 그린다.
create index if not exists discoveries_public_idx    on public.discoveries (visibility, captured_at desc)
  where visibility <> 'private';

-- B-5 ★ 같은 종 + 같은 장소는 하루 1회.
--
-- **이걸 클라이언트에만 두면 규칙이 아니다.** iOS `CaptureFlow`가 이미 막고
-- 있지만, 앱을 우회한 요청이 오면 그대로 들어간다. 그래서 **DB가 같은 규칙을
-- 한 번 더 강제한다.**
--
-- "같은 장소"는 좌표 소수 4자리(≈11m) 반올림으로 본다
-- (`GamePolicy.placeCoordinateRoundingDigits` = 4와 같은 값이다).
-- 좌표가 없는 기록은 이 제약에서 빠진다 — 실내·GPS 실패를 막으면 안 된다.
--
-- 날짜는 `captured_date`(한국 날짜)를 쓴다. 이유는 그 컬럼 주석에 있다.
create or replace function public.set_captured_date()
returns trigger
language plpgsql
as $$
begin
  -- **클라이언트가 준 값을 쓰지 않는다.** 날짜를 직접 넣게 하면
  -- 어제 날짜를 보내서 하루 1회 제한을 우회할 수 있다.
  new.captured_date := (new.captured_at at time zone 'Asia/Seoul')::date;
  return new;
end;
$$;

drop trigger if exists discoveries_set_captured_date on public.discoveries;
create trigger discoveries_set_captured_date
  before insert or update of captured_at on public.discoveries
  for each row execute function public.set_captured_date();

create unique index if not exists discoveries_same_flower_place_per_day
  on public.discoveries (
    user_id,
    flower_id,
    captured_date,
    (round(lat::numeric, 4)),
    (round(lng::numeric, 4))
  )
  where lat is not null and lng is not null;


-- ─────────────────────────────────────────────────────────────
-- 4. friendships — C-2 상호 수락
--
-- **한 관계를 한 행으로 둔다.** 두 행(A→B, B→A)으로 두면 수락 상태가
-- 갈라져서 한쪽만 accepted인 상태가 생긴다.
-- ─────────────────────────────────────────────────────────────

create table if not exists public.friendships (
  -- 요청한 쪽. 화면 19 '요청 보냄' 목록의 기준이다.
  requester_id  uuid         not null references public.users(id) on delete cascade,
  addressee_id  uuid         not null references public.users(id) on delete cascade,
  state         friend_state not null default 'pending',
  created_at    timestamptz  not null default now(),
  responded_at  timestamptz,

  primary key (requester_id, addressee_id),
  -- 자기 자신과 친구가 되면 친구 랭킹에 자기가 두 번 나온다.
  constraint friendships_no_self check (requester_id <> addressee_id)
);

-- 친구 랭킹(화면 18)은 "내가 요청한 것"과 "나에게 온 것"을 둘 다 봐야 한다.
create index if not exists friendships_addressee_idx on public.friendships (addressee_id, state);


-- ─────────────────────────────────────────────────────────────
-- 5. reports — C-1 신고 (3회 누적 자동 숨김)
--
-- **UGC 앱은 신고 수단이 없으면 앱스토어 심사에서 반려된다.** MVP 필수다.
-- ─────────────────────────────────────────────────────────────

create table if not exists public.reports (
  id             uuid         primary key default gen_random_uuid(),
  discovery_id   uuid         not null references public.discoveries(id) on delete cascade,
  reporter_id    uuid         not null references public.users(id) on delete cascade,
  reason         text,
  state          report_state not null default 'open',
  created_at     timestamptz  not null default now(),

  -- 같은 사람이 같은 기록을 3번 신고해 자동 숨김을 혼자 발동시킬 수 있다.
  -- 신고 3회는 **서로 다른 3명**이어야 의미가 있다.
  unique (discovery_id, reporter_id)
);

create index if not exists reports_discovery_idx on public.reports (discovery_id, state);


-- ─────────────────────────────────────────────────────────────
-- 6. blocks — C-1 차단
--
-- 차단하면 **지도에서도 안 보이게** 한다 (권고). 목록에서만 지우면
-- 차단한 사람의 핀을 지도에서 계속 보게 된다.
-- ─────────────────────────────────────────────────────────────

create table if not exists public.blocks (
  blocker_id  uuid        not null references public.users(id) on delete cascade,
  blocked_id  uuid        not null references public.users(id) on delete cascade,
  created_at  timestamptz not null default now(),

  primary key (blocker_id, blocked_id),
  constraint blocks_no_self check (blocker_id <> blocked_id)
);


-- ─────────────────────────────────────────────────────────────
-- 7. RLS — **여기가 이 파일에서 가장 중요하다**
--
-- 클라이언트가 anon(publishable) 키로 직접 REST를 부르는 구조다. 즉
-- **RLS가 유일한 접근 통제**다. 켜지 않으면 아무나 남의 기록을 지울 수 있다.
-- Supabase는 RLS를 안 켜도 테이블이 만들어지므로 조용히 뚫린 채로 남는다.
--
-- 정책은 **작업(select/insert/update/delete)별로 따로** 쓴다. `for all`로
-- 묶으면 "읽기는 공개, 쓰기는 본인만"을 표현할 수 없다.
-- ─────────────────────────────────────────────────────────────

alter table public.flowers     enable row level security;
alter table public.users       enable row level security;
alter table public.discoveries enable row level security;
alter table public.friendships enable row level security;
alter table public.reports     enable row level security;
alter table public.blocks      enable row level security;

-- 7-1. flowers — 도감 마스터는 **누구나 읽고 아무도 못 쓴다.**
-- 로그인 전 화면 04(도감 홈)가 보여야 하므로 anon도 읽는다.
-- 쓰기 정책을 아예 만들지 않는 게 곧 "쓰기 금지"다.
drop policy if exists flowers_read_all on public.flowers;
create policy flowers_read_all on public.flowers
  for select using (true);

-- 7-2. users — 프로필은 공개(랭킹·친구 목록에 닉네임이 나온다), 수정은 본인만.
--
-- ⚠️ `phone_hash`가 이 테이블에 있다. select 정책이 `true`면 **남의 해시를
-- 읽을 수 있다.** 해시라도 전화번호는 자리수가 정해져 있어 역산이 가능하다
-- (10자리는 전수 대입이 현실적이다). 그래서 컬럼 단위로 가린 뷰를 따로 둔다.
drop policy if exists users_read_self on public.users;
create policy users_read_self on public.users
  for select using (auth.uid() = id);

drop policy if exists users_insert_self on public.users;
create policy users_insert_self on public.users
  for insert with check (auth.uid() = id);

drop policy if exists users_update_self on public.users;
create policy users_update_self on public.users
  for update using (auth.uid() = id);

-- 남의 프로필은 **이 뷰로만** 본다. `phone_hash`가 없다.
-- `security_invoker`를 켜지 않는다 — 뷰가 RLS를 우회해서 공개 컬럼만 내보내는 게
-- 여기서 의도한 동작이다.
create or replace view public.public_profiles as
  select id, nickname, region_name, dong_code, gu_code
  from public.users
  where deleted_at is null;

grant select on public.public_profiles to anon, authenticated;

-- 7-3. discoveries — 규칙이 셋으로 갈린다.
--   ① 본인 것은 전부 보인다 (도감·마이페이지)
--   ② `public`은 로그인한 누구나 (화면 14 지도) — 단 숨김·차단 제외
--   ③ `friends`는 상호 수락된 친구만 (C-2)
--
-- 🔴 **정책 안의 서브쿼리도 RLS를 탄다 — 여기서 실제로 두 번 뚫렸다.**
--
-- 처음엔 정책 본문에서 `public.reports`와 `public.blocks`를 직접 세었다.
-- 그런데 그 두 테이블에도 RLS가 걸려 있어서 **서브쿼리가 호출자에게 보이는 행만
-- 센다.** 결과:
--   · 신고 3건이 쌓여도 내가 넣은 1건만 보여서 `count < 3`이 참 → **자동 숨김이 안 돈다**
--   · 차단은 `blocks_read_self` 때문에 내가 건 것만 보여서 → **차단당한 쪽에 상대 핀이 계속 보인다**
-- 둘 다 정책은 "있는데" 동작하지 않았다. **`create policy`가 붙었다는 것과
-- 규칙이 돈다는 것은 다른 말이다** — 테스트가 이걸 잡았다.
--
-- 그래서 판정을 `security definer` 함수로 뺀다. 함수는 RLS를 지나서 **세기만** 하고,
-- 밖으로는 boolean 하나만 나간다 (누가 신고했는지·누가 차단했는지는 안 나간다).

-- C-1: 서로 다른 3명이 신고했으면 숨긴다. 클라이언트가 세지 않는다.
create or replace function public.is_report_hidden(d_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select count(*) >= 3 from public.reports r
  where r.discovery_id = d_id and r.state <> 'resolved';
$$;

-- 차단은 **양방향**으로 막는다. 한쪽만 막으면 차단한 쪽 핀이 상대에게 계속 보인다.
create or replace function public.is_blocked_between(x uuid, y uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.blocks b
    where (b.blocker_id = x and b.blocked_id = y)
       or (b.blocker_id = y and b.blocked_id = x)
  );
$$;

-- C-2 상호 수락된 친구인가. `friendships`도 RLS가 있어 같은 이유로 함수로 뺀다.
create or replace function public.are_friends(x uuid, y uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.friendships f
    where f.state = 'accepted'
      and ((f.requester_id = x and f.addressee_id = y)
        or (f.requester_id = y and f.addressee_id = x))
  );
$$;

grant execute on function public.is_report_hidden(uuid)        to anon, authenticated;
grant execute on function public.is_blocked_between(uuid,uuid) to anon, authenticated;
grant execute on function public.are_friends(uuid,uuid)        to anon, authenticated;

drop policy if exists discoveries_read on public.discoveries;
create policy discoveries_read on public.discoveries
  for select using (
    user_id = auth.uid()
    or (
      -- **로그인해야 남의 기록을 본다.** 지도(화면 14)는 로그인 뒤 탭이고,
      -- anon에게 열어 두면 키만 있으면 누구나 전국 좌표를 긁을 수 있다.
      -- C-6(여행지 기본 하향)까지 해 둔 앱이 그 앞단을 열어 두면 앞뒤가 안 맞는다.
      auth.uid() is not null
      and visibility <> 'private'
      and not public.is_report_hidden(id)
      and not public.is_blocked_between(auth.uid(), user_id)
      and (visibility = 'public' or public.are_friends(auth.uid(), user_id))
    )
  );

drop policy if exists discoveries_insert_self on public.discoveries;
create policy discoveries_insert_self on public.discoveries
  for insert with check (user_id = auth.uid());

-- C-5: 공개 범위를 사후에 바꿀 수 있게 한다. 되돌릴 수 없으면 공유 자체를 꺼린다.
drop policy if exists discoveries_update_self on public.discoveries;
create policy discoveries_update_self on public.discoveries
  for update using (user_id = auth.uid());

drop policy if exists discoveries_delete_self on public.discoveries;
create policy discoveries_delete_self on public.discoveries
  for delete using (user_id = auth.uid());

-- 7-4. friendships — 당사자 둘만 본다.
drop policy if exists friendships_read_involved on public.friendships;
create policy friendships_read_involved on public.friendships
  for select using (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists friendships_insert_requester on public.friendships;
create policy friendships_insert_requester on public.friendships
  for insert with check (requester_id = auth.uid());

-- **수락은 받은 쪽만 한다.** 요청한 쪽이 스스로 accepted로 바꾸면
-- C-2 상호 수락이 단방향 팔로우가 된다.
drop policy if exists friendships_update_addressee on public.friendships;
create policy friendships_update_addressee on public.friendships
  for update using (addressee_id = auth.uid());

-- 취소·삭제는 양쪽 다 할 수 있다 (요청 취소 / 친구 끊기).
drop policy if exists friendships_delete_involved on public.friendships;
create policy friendships_delete_involved on public.friendships
  for delete using (requester_id = auth.uid() or addressee_id = auth.uid());

-- 7-5. reports — 넣을 수만 있고 못 읽는다.
-- 남의 신고를 읽게 하면 누가 신고했는지 알 수 있어 보복이 생긴다.
drop policy if exists reports_insert_self on public.reports;
create policy reports_insert_self on public.reports
  for insert with check (reporter_id = auth.uid());

drop policy if exists reports_read_own on public.reports;
create policy reports_read_own on public.reports
  for select using (reporter_id = auth.uid());

-- 7-6. blocks — 본인 것만 전부.
drop policy if exists blocks_read_self on public.blocks;
create policy blocks_read_self on public.blocks
  for select using (blocker_id = auth.uid());

drop policy if exists blocks_insert_self on public.blocks;
create policy blocks_insert_self on public.blocks
  for insert with check (blocker_id = auth.uid());

drop policy if exists blocks_delete_self on public.blocks;
create policy blocks_delete_self on public.blocks
  for delete using (blocker_id = auth.uid());


-- ─────────────────────────────────────────────────────────────
-- 8. 가입 시 프로필 자동 생성
--
-- 없으면 카카오·애플 로그인 직후 `auth.users`에는 행이 있는데
-- `public.users`에는 없어서 **첫 발견 저장이 외래키 위반으로 실패한다.**
-- 클라이언트가 두 단계로 처리하게 두면 iOS·Android가 각자 구현하게 된다.
-- ─────────────────────────────────────────────────────────────

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.users (id, nickname)
  values (
    new.id,
    -- 소셜 로그인이 주는 이름을 쓰고, 없으면 임시 닉네임을 만든다.
    -- **빈 문자열을 두지 않는다** — 랭킹에 이름 없는 줄이 생긴다.
    coalesce(
      nullif(new.raw_user_meta_data->>'name', ''),
      nullif(new.raw_user_meta_data->>'full_name', ''),
      '꽃친구' || substr(new.id::text, 1, 4)
    )
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();


-- ══════════════════════════════════════════════════════════════
-- ▼ 0002_ranking.sql
-- ══════════════════════════════════════════════════════════════

-- 랭킹 — B-6 · B-7 · B-8 (화면 17 · 18 · 21)
--
-- **왜 함수로 만드는가.** 랭킹은 "동 단위 인원이 10명 미만이면 구 단위로
-- 확장"(B-6)이라 **조회 결과가 조건에 따라 다른 집합**이 된다. 이걸 클라이언트가
-- 하면 iOS·Android가 각자 세고, 한쪽이 10명을 9명으로 세면 다른 랭킹이 나온다.
-- 공유계약 1-2와 같은 원리다 — **세는 일은 서버에서 한 번만 한다.**
--
-- 적용: 0001_init.sql 다음에 SQL Editor에서 Run.

-- ─────────────────────────────────────────────────────────────
-- 1. 시즌 경계 — B-1 권고 ② (3~8월 / 9~11월 / 12~2월 휴지기)
--
-- 12~2월은 개화종이 1·4·2종뿐이라 경쟁을 돌리지 않는다.
-- **없는 꽃으로 경쟁시키면 사용자가 앱을 지운다.**
-- ─────────────────────────────────────────────────────────────

-- 주어진 시각이 속한 시즌의 시작·끝을 준다. 휴지기면 `is_dormant`가 true다.
create or replace function public.season_bounds(ts          timestamptz default now())
returns table (season_start timestamptz, season_end timestamptz, is_dormant boolean)
language sql
stable
as $$
  with m as (select extract(month from ts at time zone 'Asia/Seoul')::int as mm,
                    extract(year  from ts at time zone 'Asia/Seoul')::int as yy)
  select
    case when mm between 3 and 8  then make_timestamptz(yy, 3, 1, 0,0,0, 'Asia/Seoul')
         when mm between 9 and 11 then make_timestamptz(yy, 9, 1, 0,0,0, 'Asia/Seoul')
         -- 휴지기(12·1·2)는 앞 시즌이 끝난 12/1부터로 잡는다. 1·2월은 전년 12월이다.
         when mm = 12              then make_timestamptz(yy,     12, 1, 0,0,0, 'Asia/Seoul')
         else                           make_timestamptz(yy - 1, 12, 1, 0,0,0, 'Asia/Seoul')
    end,
    case when mm between 3 and 8  then make_timestamptz(yy, 9, 1, 0,0,0, 'Asia/Seoul')
         when mm between 9 and 11 then make_timestamptz(yy, 12, 1, 0,0,0, 'Asia/Seoul')
         when mm = 12              then make_timestamptz(yy + 1, 3, 1, 0,0,0, 'Asia/Seoul')
         else                           make_timestamptz(yy,     3, 1, 0,0,0, 'Asia/Seoul')
    end,
    (mm = 12 or mm <= 2)
  from m;
$$;

-- ─────────────────────────────────────────────────────────────
-- 2. 지역 랭킹 — B-6 · B-7 · B-8
--
-- 반환에 `scope`를 넣는다. 화면 17 제목이 "우리 동네"인데 실제로는 구 단위로
-- 확장됐을 수 있어서, **클라이언트가 어느 범위인지 알아야 제목을 못 속인다.**
-- ─────────────────────────────────────────────────────────────

create or replace function public.region_ranking(
  target_user uuid default auth.uid(),
  ts          timestamptz default now(),
  -- B-6 ★ 최소 인원. 기본값을 공유계약 3절과 같은 10으로 둔다.
  -- **인자로 뺀 이유:** 오너 미확정이라 값이 바뀔 수 있고, 그때 이 함수를
  -- 다시 배포하지 않아도 되게 한다. 다만 기본값이 계약과 같아야 한다.
  min_members int default 10
)
returns table (
  scope         text,     -- 'dong' | 'gu' | 'none'
  region_code   text,
  member_count  int,
  rank          int,
  user_id       uuid,
  nickname      text,
  species_count int,
  -- B-8 권고 ② — 대표 꽃은 **가장 희귀도가 높은 종**. 자랑거리가 되어야 동기가 생긴다.
  top_flower_id int,
  top_flower    text
)
-- ⚠️ `language plpgsql`이다. `language sql` + `with guard as (...)`로 쓰면
-- **플래너가 참조되지 않은 CTE를 지워서 검사가 아예 안 돈다.** 검사는
-- 질의 밖에서 무조건 실행돼야 한다.
language plpgsql
stable
as $$
#variable_conflict use_column
begin
  -- 5절 참고: `security definer`라 인자를 믿을 수 없다. 남의 uuid면 여기서 끊긴다.
  perform public.assert_self(target_user);
  return query
  with b as (select * from public.season_bounds(ts)),
  me as (select dong_code, gu_code from public.users where id = target_user),
  -- 동 단위 인원을 먼저 센다. **발견이 있는 사람만** 센다 —
  -- 가입만 한 사람을 세면 10명을 넘겨도 랭킹이 텅 빈다.
  dong_members as (
    select count(distinct d.user_id) as n
    from public.discoveries d
    join public.users u on u.id = d.user_id
    join b on d.captured_at >= b.season_start and d.captured_at < b.season_end
    where u.dong_code = (select dong_code from me) and u.deleted_at is null
  ),
  chosen as (
    select case
             when (select dong_code from me) is null then 'none'
             when (select n from dong_members) >= min_members then 'dong'
             when (select gu_code from me) is not null then 'gu'
             else 'none'
           end as scope
  ),
  scoped as (
    select u.id, u.nickname,
           case when (select scope from chosen) = 'dong' then u.dong_code else u.gu_code end as code
    from public.users u
    where u.deleted_at is null
      and (select scope from chosen) <> 'none'
      and case when (select scope from chosen) = 'dong'
               then u.dong_code = (select dong_code from me)
               else u.gu_code   = (select gu_code   from me)
          end
  ),
  -- 시즌 내 발견만 센다. **종 수**이므로 distinct flower_id다 —
  -- 재발견을 세면 같은 꽃을 반복 촬영한 사람이 1위가 된다.
  scored as (
    select s.id, s.nickname, s.code,
           count(distinct d.flower_id)::int as species_count,
           -- B-7 권고 ① 동점은 **먼저 도달한 사람이 위**. 노력 순서가 반영된다.
           min(d.captured_at) as first_at
    from scoped s
    join public.discoveries d on d.user_id = s.id
    join b on d.captured_at >= b.season_start and d.captured_at < b.season_end
    group by s.id, s.nickname, s.code
  ),
  -- B-8: 대표 꽃 = 시즌 내 발견 중 희귀도가 가장 높은 종.
  -- 동일 희귀도면 도감번호가 작은 쪽(먼저 나온 종)으로 고정해 결과를 안정시킨다.
  top as (
    select distinct on (d.user_id)
           d.user_id, f.id as fid, f.name as fname
    from public.discoveries d
    join public.flowers f on f.id = d.flower_id
    join b on d.captured_at >= b.season_start and d.captured_at < b.season_end
    order by d.user_id,
             case f.rarity when 'rare' then 0 when 'normal' then 1 else 2 end,
             f.id
  )
  select (select scope from chosen),
         sc.code,
         (select count(*)::int from scored),
         (rank() over (order by sc.species_count desc, sc.first_at asc))::int,
         sc.id, sc.nickname, sc.species_count,
         t.fid, t.fname
  from scored sc
  left join top t on t.user_id = sc.id
  order by sc.species_count desc, sc.first_at asc;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- 3. 친구 랭킹 — 화면 18
--
-- 지역 랭킹과 달리 **최소 인원 규칙이 없다.** 친구가 2명이면 2명으로 보여준다 —
-- 아는 사이라 인원이 적어도 의미가 있다.
-- 본인을 포함한다. 시상대에 내가 없으면 내 순위를 알 수 없다.
-- ─────────────────────────────────────────────────────────────

create or replace function public.friend_ranking(
  target_user uuid default auth.uid(),
  ts          timestamptz default now()
)
returns table (
  rank          int,
  user_id       uuid,
  nickname      text,
  species_count int,
  top_flower_id int,
  top_flower    text,
  is_me         boolean
)
language plpgsql
stable
as $$
-- ⚠️ 반환 컬럼명(`rank`·`user_id`·`nickname`…)이 질의 안의 컬럼명과 겹친다.
-- plpgsql은 기본적으로 **변수를 우선**해서 `rank() over(...)`나 `u.nickname`이
-- 조용히 OUT 변수로 해석될 수 있다. 컬럼 쪽으로 못 박는다.
#variable_conflict use_column
begin
  perform public.assert_self(target_user);
  return query
  with b as (select * from public.season_bounds(ts)),
  circle as (
    select target_user as id
    union
    select case when f.requester_id = target_user then f.addressee_id else f.requester_id end
    from public.friendships f
    where f.state = 'accepted'
      and (f.requester_id = target_user or f.addressee_id = target_user)
  ),
  scored as (
    select u.id, u.nickname,
           count(distinct d.flower_id)::int as species_count,
           min(d.captured_at) as first_at
    from circle c
    join public.users u on u.id = c.id and u.deleted_at is null
    join public.discoveries d on d.user_id = u.id
    join b on d.captured_at >= b.season_start and d.captured_at < b.season_end
    group by u.id, u.nickname
  ),
  top as (
    select distinct on (d.user_id) d.user_id, f.id as fid, f.name as fname
    from public.discoveries d
    join public.flowers f on f.id = d.flower_id
    join b on d.captured_at >= b.season_start and d.captured_at < b.season_end
    order by d.user_id,
             case f.rarity when 'rare' then 0 when 'normal' then 1 else 2 end,
             f.id
  )
  select (rank() over (order by sc.species_count desc, sc.first_at asc))::int,
         sc.id, sc.nickname, sc.species_count, t.fid, t.fname,
         (sc.id = target_user)
  from scored sc
  left join top t on t.user_id = sc.id
  order by sc.species_count desc, sc.first_at asc;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- 4. 내 시즌 요약 — 화면 20 마이페이지 · 21 시즌 종료
-- ─────────────────────────────────────────────────────────────

create or replace function public.my_season_summary(
  target_user uuid default auth.uid(),
  ts          timestamptz default now()
)
returns table (
  season_start   timestamptz,
  season_end     timestamptz,
  is_dormant     boolean,
  species_count  int,
  -- 종 수와 별개로 센다. 화면 05가 "발견 횟수 4회"를 따로 보여준다.
  discovery_count int,
  place_count    int,
  rare_count     int
)
language plpgsql
stable
as $$
-- ⚠️ 반환 컬럼명(`rank`·`user_id`·`nickname`…)이 질의 안의 컬럼명과 겹친다.
-- plpgsql은 기본적으로 **변수를 우선**해서 `rank() over(...)`나 `u.nickname`이
-- 조용히 OUT 변수로 해석될 수 있다. 컬럼 쪽으로 못 박는다.
#variable_conflict use_column
begin
  perform public.assert_self(target_user);
  return query
  with b as (select * from public.season_bounds(ts))
  select b.season_start, b.season_end, b.is_dormant,
         count(distinct d.flower_id)::int,
         count(d.id)::int,
         -- 좌표가 아니라 **장소명** 기준으로 센다 (iOS `CodexEntry.placeCount`와 같은 규칙).
         count(distinct d.place_name)::int,
         count(distinct d.flower_id) filter (where f.rarity = 'rare')::int
  from b
  left join public.discoveries d
    on d.user_id = target_user
   and d.captured_at >= b.season_start and d.captured_at < b.season_end
  left join public.flowers f on f.id = d.flower_id
  group by b.season_start, b.season_end, b.is_dormant;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- 5. ⚠️ 왜 랭킹 함수가 `security definer`인가 — 기록해 둔다
--
-- 처음엔 안 붙였다. 그러면 함수가 호출자의 RLS를 그대로 타서
-- **`private` 기록이 랭킹에서 빠진다.** 그게 왜 틀렸는가:
--
-- 기획서 8장은 **도감 등록과 지도 공유가 별개**라고 못 박았다. iOS 화면 13에서
-- "나만 보기"를 고르면 토스트가 *"도감에는 이미 등록됐어요"*라고 말한다.
-- 그런데 RLS를 타면 그 기록이 **시즌 종수에서 빠진다** — 지도에 공유를 안 했다는
-- 이유로 도감 점수가 깎인다. 사용자는 이유를 알 수 없고, 결국
-- **경쟁하려면 위치를 전체 공개해야 하는 앱**이 된다. C-6(여행지 기본 하향)과 정면으로 어긋난다.
--
-- 그래서 집계만 RLS를 우회한다. **대신 내용은 한 줄도 내보내지 않는다** —
-- 반환에 `lat`·`lng`·`photo_url`·`place_name`·`note`가 없다. 나가는 것은
-- 닉네임·종 수·대표 꽃 이름뿐이고, 이 셋은 원래 화면 17·18에 보이는 값이다.
-- **"어디서 찍었는지"는 여전히 못 본다.**
--
-- ⚠️ `security definer` 함수는 RLS를 지나므로 **인자를 그대로 믿으면 안 된다.**
-- `target_user`를 남의 uuid로 넣으면 남의 친구 목록·요약을 볼 수 있다.
-- 그래서 아래 세 함수는 **호출자 본인인지 먼저 확인하고 아니면 거부한다.**
-- ─────────────────────────────────────────────────────────────

create or replace function public.assert_self(target_user uuid)
returns void
language plpgsql
stable
as $$
begin
  if target_user is null or target_user <> auth.uid() then
    -- 조용히 빈 결과를 주지 않는다. 빈 랭킹은 "친구가 없다"로 읽혀서
    -- 버그와 구분이 안 된다.
    raise exception '본인 것만 조회할 수 있다';
  end if;
end;
$$;

alter function public.region_ranking(uuid, timestamptz, int)  security definer set search_path = public;
alter function public.friend_ranking(uuid, timestamptz)       security definer set search_path = public;
alter function public.my_season_summary(uuid, timestamptz)    security definer set search_path = public;

grant execute on function public.region_ranking(uuid, timestamptz, int) to authenticated;
grant execute on function public.friend_ranking(uuid, timestamptz)      to authenticated;
grant execute on function public.my_season_summary(uuid, timestamptz)   to authenticated;
grant execute on function public.season_bounds(timestamptz)             to anon, authenticated;
grant execute on function public.assert_self(uuid)                      to authenticated;

-- anon(로그인 전)은 랭킹을 못 부른다. `auth.uid()`가 null이라 `assert_self`가 막는다.
revoke execute on function public.region_ranking(uuid, timestamptz, int) from anon;
revoke execute on function public.friend_ranking(uuid, timestamptz)      from anon;
revoke execute on function public.my_season_summary(uuid, timestamptz)   from anon;


-- ══════════════════════════════════════════════════════════════
-- ▼ 0003_seed_flowers.sql
-- ══════════════════════════════════════════════════════════════

-- 도감 마스터 200종 적재
--
-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`이고
-- 그 입력은 `공용_적재/flowers.json`(← `꽃도감/꽃목록_200종.csv`)이다.
-- 꽃 데이터를 바꾸려면 `꽃도감/_tools/flowers.py`부터 고친다.
--
-- `bloom_months`는 **이미 파싱된 값**이다 (공유계약 1-2: 파싱은 단 한 번).
-- 적용: 0001_init.sql 다음에 SQL Editor에서 Run. 여러 번 돌려도 안전하다.

insert into public.flowers
  (id, name, scientific_name, family, bloom_months, bloom_label,
   season, color, rarity, habitat, ai_difficulty, similar_flower_ids, illust_batch)
values
  (1, '개나리', 'Forsythia koreana', '물푸레나무과', '{3,4}', '3~4월', 'spring', '노랑', 'common', '담장·공원 울타리', 'low', '{13}', 1),
  (2, '진달래', 'Rhododendron mucronulatum', '진달래과', '{3,4}', '3~4월', 'spring', '분홍', 'common', '산지·공원 사면', 'mid', '{3,4}', 1),
  (3, '철쭉', 'Rhododendron schlippenbachii', '진달래과', '{4,5}', '4~5월', 'spring', '분홍', 'common', '산지·공원', 'mid', '{2,4}', 1),
  (4, '영산홍', 'Rhododendron indicum', '진달래과', '{4,5}', '4~5월', 'spring', '붉은색', 'common', '화단·가로 조경', 'mid', '{3,2}', 1),
  (5, '벚꽃', 'Prunus × yedoensis', '장미과', '{4}', '4월', 'spring', '분홍', 'common', '가로수·공원', 'high', '{6,7,9}', 1),
  (6, '매화', 'Prunus mume', '장미과', '{2,3,4}', '2~4월', 'winter', '흰색', 'normal', '공원·정원', 'high', '{5,7}', 1),
  (7, '살구꽃', 'Prunus armeniaca', '장미과', '{4}', '4월', 'spring', '분홍', 'normal', '주택가·농가', 'high', '{6,5,8}', 2),
  (8, '복사꽃', 'Prunus persica', '장미과', '{4,5}', '4~5월', 'spring', '분홍', 'normal', '과수원·주택가', 'high', '{7,5}', 2),
  (9, '자두꽃', 'Prunus salicina', '장미과', '{4}', '4월', 'spring', '흰색', 'normal', '과수원·주택가', 'high', '{6,5}', 3),
  (10, '백목련', 'Magnolia denudata', '목련과', '{3,4}', '3~4월', 'spring', '흰색', 'common', '공원·학교', 'mid', '{11,12}', 1),
  (11, '목련', 'Magnolia kobus', '목련과', '{3,4}', '3~4월', 'spring', '흰색', 'normal', '공원·산지', 'mid', '{10}', 2),
  (12, '자목련', 'Magnolia liliiflora', '목련과', '{4,5}', '4~5월', 'spring', '보라', 'normal', '공원·정원', 'low', '{10}', 2),
  (13, '산수유', 'Cornus officinalis', '층층나무과', '{3,4}', '3~4월', 'spring', '노랑', 'common', '공원·가로수', 'mid', '{1,14}', 1),
  (14, '생강나무', 'Lindera obtusiloba', '녹나무과', '{3}', '3월', 'spring', '노랑', 'normal', '산지 등산로', 'high', '{13}', 3),
  (15, '동백꽃', 'Camellia japonica', '차나무과', '{12,1,2,3,4}', '12~4월', 'winter', '붉은색', 'normal', '남부 해안·공원', 'low', '{16}', 1),
  (16, '애기동백', 'Camellia sasanqua', '차나무과', '{10,11,12}', '10~12월', 'winter', '분홍', 'normal', '공원·정원', 'mid', '{15}', 2),
  (17, '명자나무꽃', 'Chaenomeles speciosa', '장미과', '{4,5}', '4~5월', 'spring', '붉은색', 'normal', '정원·울타리', 'mid', '{15}', 2),
  (18, '조팝나무', 'Spiraea prunifolia', '장미과', '{4}', '4월', 'spring', '흰색', 'common', '공원·도로변', 'mid', '{26}', 1),
  (19, '수수꽃다리', 'Syringa dilatata', '물푸레나무과', '{4,5}', '4~5월', 'spring', '보라', 'common', '공원·학교', 'mid', '{}', 1),
  (20, '이팝나무', 'Chionanthus retusus', '물푸레나무과', '{5,6}', '5~6월', 'spring', '흰색', 'common', '가로수', 'mid', '{26}', 2),
  (21, '아까시나무', 'Robinia pseudoacacia', '콩과', '{5,6}', '5~6월', 'spring', '흰색', 'common', '산지·도로변', 'mid', '{91}', 1),
  (22, '등나무', 'Wisteria floribunda', '콩과', '{5}', '5월', 'spring', '보라', 'common', '공원 그늘막·정자', 'low', '{93}', 1),
  (23, '병꽃나무', 'Weigela subsessilis', '인동과', '{5}', '5월', 'spring', '붉은색', 'normal', '산지·공원', 'mid', '{}', 3),
  (24, '황매화', 'Kerria japonica', '장미과', '{4,5}', '4~5월', 'spring', '노랑', 'normal', '공원·화단', 'mid', '{}', 2),
  (25, '산딸나무', 'Cornus kousa', '층층나무과', '{5,6}', '5~6월', 'spring', '흰색', 'normal', '공원·가로수', 'low', '{}', 2),
  (26, '쥐똥나무', 'Ligustrum obtusifolium', '물푸레나무과', '{5,6}', '5~6월', 'spring', '흰색', 'common', '울타리·도로변', 'mid', '{20,18}', 2),
  (27, '모란', 'Paeonia suffruticosa', '작약과', '{5}', '5월', 'spring', '붉은색', 'normal', '정원·고궁', 'mid', '{28}', 2),
  (28, '작약', 'Paeonia lactiflora', '작약과', '{5,6}', '5~6월', 'spring', '분홍', 'normal', '화단·농가', 'mid', '{27}', 2),
  (29, '찔레꽃', 'Rosa multiflora', '장미과', '{5,6}', '5~6월', 'spring', '흰색', 'common', '들·산기슭', 'mid', '{81,82}', 1),
  (30, '마가목', 'Sorbus commixta', '장미과', '{5,6}', '5~6월', 'spring', '흰색', 'normal', '공원·산지', 'high', '{}', 3),
  (31, '민들레', 'Taraxacum platycarpum', '국화과', '{3,4,5}', '3~5월', 'spring', '노랑', 'common', '길가·공터', 'mid', '{32,45}', 1),
  (32, '서양민들레', 'Taraxacum officinale', '국화과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', '노랑', 'common', '길가·공터', 'high', '{31}', 1),
  (33, '제비꽃', 'Viola mandshurica', '제비꽃과', '{4,5}', '4~5월', 'spring', '보라', 'common', '길가·잔디밭', 'mid', '{34,73}', 1),
  (34, '남산제비꽃', 'Viola albida', '제비꽃과', '{4,5}', '4~5월', 'spring', '흰색', 'normal', '산지', 'high', '{33}', 3),
  (35, '꽃마리', 'Trigonotis peduncularis', '지치과', '{4,5}', '4~5월', 'spring', '파랑', 'common', '길가·잔디밭', 'high', '{36}', 2),
  (36, '큰개불알풀', 'Veronica persica', '현삼과', '{3,4,5}', '3~5월', 'spring', '파랑', 'common', '길가·밭', 'mid', '{35}', 1),
  (37, '봄맞이꽃', 'Androsace umbellata', '앵초과', '{4,5}', '4~5월', 'spring', '흰색', 'normal', '길가·논둑', 'high', '{38}', 3),
  (38, '별꽃', 'Stellaria media', '석죽과', '{3,4,5}', '3~5월', 'spring', '흰색', 'common', '길가·밭', 'high', '{39,40}', 2),
  (39, '쇠별꽃', 'Stellaria aquatica', '석죽과', '{4,5,6}', '4~6월', 'spring', '흰색', 'common', '습한 길가', 'high', '{38}', 3),
  (40, '개별꽃', 'Pseudostellaria heterophylla', '석죽과', '{4,5}', '4~5월', 'spring', '흰색', 'normal', '산지 숲', 'high', '{38}', 3),
  (41, '광대나물', 'Lamium amplexicaule', '꿀풀과', '{3,4,5}', '3~5월', 'spring', '분홍', 'common', '길가·밭', 'mid', '{42}', 2),
  (42, '자주광대나물', 'Lamium purpureum', '꿀풀과', '{3,4,5}', '3~5월', 'spring', '보라', 'normal', '길가·공터', 'high', '{41}', 3),
  (43, '냉이', 'Capsella bursa-pastoris', '십자화과', '{3,4,5}', '3~5월', 'spring', '흰색', 'common', '길가·밭', 'mid', '{}', 2),
  (44, '애기똥풀', 'Chelidonium majus', '양귀비과', '{5,6,7,8}', '5~8월', 'spring', '노랑', 'common', '길가·담장 밑', 'mid', '{45}', 1),
  (45, '씀바귀', 'Ixeris dentata', '국화과', '{5,6,7}', '5~7월', 'spring', '노랑', 'common', '길가·들', 'high', '{46,31}', 2),
  (46, '고들빼기', 'Crepidiastrum sonchifolium', '국화과', '{5,6,7,8}', '5~8월', 'spring', '노랑', 'common', '길가·공터', 'high', '{45,47}', 2),
  (47, '뽀리뱅이', 'Youngia japonica', '국화과', '{5,6}', '5~6월', 'spring', '노랑', 'common', '길가·화단 틈', 'high', '{46}', 3),
  (48, '지칭개', 'Hemistepta lyrata', '국화과', '{5,6,7}', '5~7월', 'spring', '보라', 'common', '길가·공터', 'mid', '{49}', 2),
  (49, '엉겅퀴', 'Cirsium japonicum', '국화과', '{6,7,8}', '6~8월', 'autumn', '보라', 'normal', '들·산기슭', 'mid', '{48}', 2),
  (50, '유채꽃', 'Brassica napus', '십자화과', '{3,4,5}', '3~5월', 'spring', '노랑', 'common', '강변·경관 밭', 'mid', '{}', 1),
  (51, '토끼풀', 'Trifolium repens', '콩과', '{5,6,7,8}', '5~8월', 'spring', '흰색', 'common', '잔디밭·공원', 'low', '{52}', 1),
  (52, '붉은토끼풀', 'Trifolium pratense', '콩과', '{5,6,7,8}', '5~8월', 'spring', '분홍', 'common', '길가·잔디밭', 'low', '{51}', 2),
  (53, '살갈퀴', 'Vicia angustifolia', '콩과', '{4,5}', '4~5월', 'spring', '보라', 'common', '길가·논둑', 'high', '{}', 3),
  (54, '자운영', 'Astragalus sinicus', '콩과', '{4,5}', '4~5월', 'spring', '분홍', 'normal', '논·밭 (남부)', 'mid', '{52}', 3),
  (55, '벌노랑이', 'Lotus corniculatus', '콩과', '{5,6,7,8}', '5~8월', 'spring', '노랑', 'normal', '길가·잔디밭', 'mid', '{}', 3),
  (56, '금낭화', 'Lamprocapnos spectabilis', '현호색과', '{5,6}', '5~6월', 'spring', '분홍', 'normal', '화단·산지', 'low', '{}', 2),
  (57, '현호색', 'Corydalis remota', '현호색과', '{4,5}', '4~5월', 'spring', '보라', 'normal', '산지 숲', 'high', '{}', 3),
  (58, '할미꽃', 'Pulsatilla koreana', '미나리아재비과', '{4,5}', '4~5월', 'spring', '보라', 'rare', '산소·풀밭', 'low', '{}', 3),
  (59, '노루귀', 'Hepatica asiatica', '미나리아재비과', '{3,4}', '3~4월', 'winter', '분홍', 'rare', '산지 숲', 'mid', '{}', 3),
  (60, '복수초', 'Adonis amurensis', '미나리아재비과', '{2,3,4}', '2~4월', 'winter', '노랑', 'rare', '산지 숲', 'mid', '{}', 3),
  (61, '변산바람꽃', 'Eranthis byunsanensis', '미나리아재비과', '{2,3}', '2~3월', 'winter', '흰색', 'rare', '남부 산지', 'mid', '{}', 3),
  (62, '앵초', 'Primula sieboldii', '앵초과', '{4,5}', '4~5월', 'spring', '분홍', 'rare', '산지 습지', 'mid', '{}', 3),
  (63, '은방울꽃', 'Convallaria keiskei', '백합과', '{5,6}', '5~6월', 'spring', '흰색', 'rare', '산지 숲', 'low', '{}', 3),
  (64, '붓꽃', 'Iris sanguinea', '붓꽃과', '{5,6}', '5~6월', 'spring', '보라', 'normal', '공원·화단', 'mid', '{65,66}', 2),
  (65, '꽃창포', 'Iris ensata', '붓꽃과', '{6,7}', '6~7월', 'summer', '보라', 'normal', '물가·습지', 'high', '{64}', 2),
  (66, '노랑꽃창포', 'Iris pseudacorus', '붓꽃과', '{5,6}', '5~6월', 'spring', '노랑', 'normal', '물가·수변공원', 'mid', '{65}', 2),
  (67, '등심붓꽃', 'Sisyrinchium rosulatum', '붓꽃과', '{5,6}', '5~6월', 'spring', '보라', 'normal', '잔디밭 (남부)', 'high', '{}', 3),
  (68, '튤립', 'Tulipa gesneriana', '백합과', '{4,5}', '4~5월', 'spring', '기타', 'common', '화단·공원', 'low', '{}', 1),
  (69, '수선화', 'Narcissus tazetta', '수선화과', '{3,4}', '3~4월', 'spring', '노랑', 'common', '화단·공원', 'low', '{}', 1),
  (70, '히아신스', 'Hyacinthus orientalis', '아스파라거스과', '{4}', '4월', 'spring', '보라', 'normal', '화단·화분', 'low', '{71}', 2),
  (71, '무스카리', 'Muscari armeniacum', '아스파라거스과', '{4,5}', '4~5월', 'spring', '파랑', 'normal', '화단', 'low', '{70}', 2),
  (72, '크로커스', 'Crocus vernus', '붓꽃과', '{3,4}', '3~4월', 'spring', '보라', 'normal', '화단', 'mid', '{}', 3),
  (73, '팬지', 'Viola × wittrockiana', '제비꽃과', '{3,4,5}', '3~5월', 'spring', '기타', 'common', '화단·화분', 'mid', '{74,33}', 1),
  (74, '비올라', 'Viola cornuta', '제비꽃과', '{3,4,5}', '3~5월', 'spring', '기타', 'common', '화단·화분', 'high', '{73}', 2),
  (75, '데이지', 'Bellis perennis', '국화과', '{4,5}', '4~5월', 'spring', '흰색', 'common', '화단', 'mid', '{}', 2),
  (76, '프리지어', 'Freesia refracta', '붓꽃과', '{3,4}', '3~4월', 'spring', '노랑', 'normal', '화단·화분', 'mid', '{}', 3),
  (77, '라넌큘러스', 'Ranunculus asiaticus', '미나리아재비과', '{4,5}', '4~5월', 'spring', '기타', 'normal', '화단·화분', 'mid', '{}', 3),
  (78, '아네모네', 'Anemone coronaria', '미나리아재비과', '{4,5}', '4~5월', 'spring', '기타', 'normal', '화단', 'mid', '{}', 3),
  (79, '꽃잔디', 'Phlox subulata', '꽃고비과', '{4,5}', '4~5월', 'spring', '분홍', 'common', '화단·경사면', 'low', '{}', 1),
  (80, '물망초', 'Myosotis scorpioides', '지치과', '{5,6}', '5~6월', 'spring', '파랑', 'normal', '화단·물가', 'high', '{35}', 3),
  (81, '장미', 'Rosa hybrida', '장미과', '{5,6,7,8,9,10}', '5~10월', 'summer', '붉은색', 'common', '화단·담장', 'mid', '{29,82}', 1),
  (82, '해당화', 'Rosa rugosa', '장미과', '{5,6,7}', '5~7월', 'summer', '분홍', 'normal', '해안·공원', 'mid', '{81,29}', 2),
  (83, '수국', 'Hydrangea macrophylla', '수국과', '{6,7}', '6~7월', 'summer', '파랑', 'common', '공원·정원', 'low', '{84,85}', 1),
  (84, '산수국', 'Hydrangea serrata', '수국과', '{6,7,8}', '6~8월', 'summer', '파랑', 'normal', '산지 계곡', 'mid', '{83}', 2),
  (85, '나무수국', 'Hydrangea paniculata', '수국과', '{7,8,9}', '7~9월', 'summer', '흰색', 'normal', '공원·정원', 'mid', '{83}', 2),
  (86, '능소화', 'Campsis grandiflora', '능소화과', '{7,8}', '7~8월', 'summer', '주황', 'common', '담장·아파트 화단', 'low', '{}', 1),
  (87, '배롱나무', 'Lagerstroemia indica', '부처꽃과', '{7,8,9}', '7~9월', 'summer', '분홍', 'common', '공원·가로수', 'mid', '{}', 1),
  (88, '무궁화', 'Hibiscus syriacus', '아욱과', '{7,8,9}', '7~9월', 'summer', '분홍', 'common', '공원·학교·관공서', 'mid', '{89,117}', 1),
  (89, '부용', 'Hibiscus mutabilis', '아욱과', '{8,9,10}', '8~10월', 'autumn', '분홍', 'normal', '공원·정원', 'high', '{88,117}', 2),
  (90, '자귀나무', 'Albizia julibrissin', '콩과', '{6,7}', '6~7월', 'summer', '분홍', 'common', '공원·도로변', 'low', '{}', 1),
  (91, '회화나무', 'Styphnolobium japonicum', '콩과', '{7,8}', '7~8월', 'summer', '흰색', 'normal', '가로수·고궁', 'high', '{21}', 3),
  (92, '싸리', 'Lespedeza bicolor', '콩과', '{7,8}', '7~8월', 'summer', '보라', 'common', '산기슭·도로 사면', 'mid', '{93}', 2),
  (93, '칡꽃', 'Pueraria lobata', '콩과', '{7,8}', '7~8월', 'summer', '보라', 'common', '산기슭·공터', 'mid', '{92,22}', 2),
  (94, '인동덩굴', 'Lonicera japonica', '인동과', '{5,6,7}', '5~7월', 'summer', '흰색', 'common', '울타리·산기슭', 'mid', '{}', 2),
  (95, '치자나무', 'Gardenia jasminoides', '꼭두서니과', '{6,7}', '6~7월', 'summer', '흰색', 'normal', '정원 (남부)', 'mid', '{}', 3),
  (96, '모감주나무', 'Koelreuteria paniculata', '무환자나무과', '{6,7}', '6~7월', 'summer', '노랑', 'normal', '공원·해안', 'mid', '{}', 3),
  (97, '개오동', 'Catalpa ovata', '능소화과', '{6,7}', '6~7월', 'summer', '흰색', 'normal', '공원·학교', 'mid', '{}', 3),
  (98, '큰꽃으아리', 'Clematis patens', '미나리아재비과', '{5,6}', '5~6월', 'summer', '흰색', 'normal', '화단·울타리', 'mid', '{100}', 2),
  (99, '사위질빵', 'Clematis apiifolia', '미나리아재비과', '{7,8}', '7~8월', 'summer', '흰색', 'common', '산기슭·울타리', 'high', '{100}', 3),
  (100, '으아리', 'Clematis terniflora', '미나리아재비과', '{7,8}', '7~8월', 'summer', '흰색', 'normal', '산기슭', 'high', '{99}', 3),
  (101, '개망초', 'Erigeron annuus', '국화과', '{6,7,8}', '6~8월', 'summer', '흰색', 'common', '공터·길가', 'mid', '{102,103}', 1),
  (102, '망초', 'Conyza canadensis', '국화과', '{7,8,9}', '7~9월', 'summer', '흰색', 'common', '공터·길가', 'high', '{101}', 2),
  (103, '실망초', 'Conyza bonariensis', '국화과', '{7,8,9}', '7~9월', 'summer', '흰색', 'normal', '공터', 'high', '{102}', 3),
  (104, '큰금계국', 'Coreopsis lanceolata', '국화과', '{6,7,8}', '6~8월', 'summer', '노랑', 'common', '도로변·하천', 'mid', '{105,106}', 1),
  (105, '금계국', 'Coreopsis basalis', '국화과', '{6,7,8}', '6~8월', 'summer', '노랑', 'normal', '화단·도로변', 'high', '{104}', 1),
  (106, '기생초', 'Coreopsis tinctoria', '국화과', '{7,8,9}', '7~9월', 'summer', '노랑', 'normal', '도로변·화단', 'mid', '{105}', 2),
  (107, '원추천인국', 'Rudbeckia hirta', '국화과', '{7,8,9}', '7~9월', 'summer', '노랑', 'common', '화단·도로변', 'mid', '{108,109}', 1),
  (108, '삼잎국화', 'Rudbeckia laciniata', '국화과', '{7,8,9}', '7~9월', 'summer', '노랑', 'normal', '하천변·공터', 'mid', '{107}', 2),
  (109, '해바라기', 'Helianthus annuus', '국화과', '{7,8,9}', '7~9월', 'summer', '노랑', 'common', '밭·경관 화단', 'low', '{}', 1),
  (110, '코스모스', 'Cosmos bipinnatus', '국화과', '{8,9,10}', '8~10월', 'autumn', '분홍', 'common', '하천변·길가', 'low', '{111}', 1),
  (111, '노랑코스모스', 'Cosmos sulphureus', '국화과', '{7,8,9,10}', '7~10월', 'summer', '주황', 'common', '도로변·하천', 'mid', '{110,105}', 1),
  (112, '나팔꽃', 'Ipomoea nil', '메꽃과', '{7,8,9}', '7~9월', 'summer', '보라', 'common', '담장·울타리', 'mid', '{113,114}', 1),
  (113, '메꽃', 'Calystegia pubescens', '메꽃과', '{6,7,8}', '6~8월', 'summer', '분홍', 'common', '길가·논둑', 'mid', '{112}', 2),
  (114, '애기나팔꽃', 'Ipomoea lacunosa', '메꽃과', '{7,8,9}', '7~9월', 'summer', '흰색', 'normal', '공터·하천', 'high', '{112}', 3),
  (115, '달맞이꽃', 'Oenothera biennis', '바늘꽃과', '{6,7,8,9}', '6~9월', 'summer', '노랑', 'common', '하천변·공터', 'mid', '{116}', 1),
  (116, '낮달맞이꽃', 'Oenothera speciosa', '바늘꽃과', '{5,6,7,8,9}', '5~9월', 'summer', '분홍', 'common', '화단·도로변', 'low', '{115}', 1),
  (117, '접시꽃', 'Alcea rosea', '아욱과', '{6,7,8}', '6~8월', 'summer', '분홍', 'normal', '화단·담장 밑', 'mid', '{88,89}', 1),
  (118, '패랭이꽃', 'Dianthus chinensis', '석죽과', '{6,7,8}', '6~8월', 'summer', '분홍', 'normal', '화단·길가', 'mid', '{119,120}', 2),
  (119, '수염패랭이꽃', 'Dianthus barbatus', '석죽과', '{6,7}', '6~7월', 'summer', '붉은색', 'normal', '화단', 'mid', '{118}', 3),
  (120, '술패랭이꽃', 'Dianthus longicalyx', '석죽과', '{7,8}', '7~8월', 'summer', '분홍', 'rare', '산지·풀밭', 'mid', '{118}', 3),
  (121, '끈끈이대나물', 'Silene armeria', '석죽과', '{6,7,8}', '6~8월', 'summer', '분홍', 'normal', '길가·공터', 'mid', '{}', 3),
  (122, '꿀풀', 'Prunella vulgaris', '꿀풀과', '{5,6,7}', '5~7월', 'summer', '보라', 'common', '풀밭·산기슭', 'mid', '{123}', 2),
  (123, '배초향', 'Agastache rugosa', '꿀풀과', '{7,8,9}', '7~9월', 'autumn', '보라', 'normal', '산기슭·밭', 'mid', '{122,125}', 2),
  (124, '익모초', 'Leonurus japonicus', '꿀풀과', '{7,8}', '7~8월', 'summer', '분홍', 'normal', '길가·공터', 'high', '{}', 3),
  (125, '층층이꽃', 'Clinopodium chinense', '꿀풀과', '{7,8}', '7~8월', 'summer', '분홍', 'normal', '산기슭·풀밭', 'high', '{123}', 3),
  (126, '박하', 'Mentha canadensis', '꿀풀과', '{7,8,9}', '7~9월', 'summer', '보라', 'normal', '물가·밭', 'high', '{}', 3),
  (127, '라벤더', 'Lavandula angustifolia', '꿀풀과', '{6,7}', '6~7월', 'summer', '보라', 'normal', '화단·허브 농원', 'low', '{}', 2),
  (128, '부처꽃', 'Lythrum anceps', '부처꽃과', '{7,8}', '7~8월', 'summer', '보라', 'normal', '습지·물가', 'mid', '{}', 3),
  (129, '물봉선', 'Impatiens textori', '봉선화과', '{8,9}', '8~9월', 'autumn', '분홍', 'normal', '산지 계곡', 'mid', '{130}', 2),
  (130, '봉선화', 'Impatiens balsamina', '봉선화과', '{7,8,9}', '7~9월', 'summer', '분홍', 'normal', '화단·주택가', 'mid', '{129,166}', 2),
  (131, '노루오줌', 'Astilbe rubra', '범의귀과', '{7,8}', '7~8월', 'summer', '분홍', 'normal', '산지 계곡', 'mid', '{}', 3),
  (132, '돌나물', 'Sedum sarmentosum', '돌나물과', '{5,6}', '5~6월', 'summer', '노랑', 'common', '담장·바위 틈', 'mid', '{133}', 2),
  (133, '기린초', 'Phedimus kamtschaticus', '돌나물과', '{6,7}', '6~7월', 'summer', '노랑', 'normal', '바위·화단', 'mid', '{132}', 3),
  (134, '큰까치수염', 'Lysimachia clethroides', '앵초과', '{6,7,8}', '6~8월', 'summer', '흰색', 'normal', '산기슭·풀밭', 'mid', '{135}', 2),
  (135, '까치수염', 'Lysimachia barystachys', '앵초과', '{6,7,8}', '6~8월', 'summer', '흰색', 'normal', '풀밭', 'high', '{134}', 3),
  (136, '좁쌀풀', 'Lysimachia vulgaris', '앵초과', '{6,7,8}', '6~8월', 'summer', '노랑', 'normal', '습지·풀밭', 'mid', '{}', 3),
  (137, '짚신나물', 'Agrimonia pilosa', '장미과', '{6,7,8}', '6~8월', 'summer', '노랑', 'normal', '산기슭·길가', 'high', '{}', 3),
  (138, '이질풀', 'Geranium thunbergii', '쥐손이풀과', '{8,9}', '8~9월', 'autumn', '분홍', 'normal', '산기슭·길가', 'mid', '{}', 3),
  (139, '여뀌', 'Persicaria hydropiper', '마디풀과', '{6,7,8,9}', '6~9월', 'summer', '분홍', 'common', '물가·논둑', 'high', '{140,141}', 2),
  (140, '개여뀌', 'Persicaria longiseta', '마디풀과', '{6,7,8,9}', '6~9월', 'summer', '분홍', 'common', '길가·물가', 'high', '{139}', 2),
  (141, '고마리', 'Persicaria thunbergii', '마디풀과', '{8,9}', '8~9월', 'autumn', '분홍', 'common', '물가·논둑', 'mid', '{139}', 2),
  (142, '이삭여뀌', 'Persicaria filiformis', '마디풀과', '{7,8}', '7~8월', 'summer', '붉은색', 'normal', '산지 숲', 'high', '{139}', 3),
  (143, '도라지', 'Platycodon grandiflorus', '초롱꽃과', '{7,8}', '7~8월', 'summer', '보라', 'normal', '밭·화단', 'low', '{}', 1),
  (144, '초롱꽃', 'Campanula punctata', '초롱꽃과', '{6,7}', '6~7월', 'summer', '흰색', 'normal', '산기슭·화단', 'mid', '{145}', 2),
  (145, '섬초롱꽃', 'Campanula takesimana', '초롱꽃과', '{6,7,8}', '6~8월', 'summer', '분홍', 'rare', '울릉도·화단', 'mid', '{144}', 3),
  (146, '잔대', 'Adenophora triphylla', '초롱꽃과', '{7,8,9}', '7~9월', 'summer', '보라', 'normal', '산지·풀밭', 'mid', '{}', 3),
  (147, '참나리', 'Lilium lancifolium', '백합과', '{7,8}', '7~8월', 'summer', '주황', 'normal', '산기슭·화단', 'low', '{148}', 1),
  (148, '백합', 'Lilium longiflorum', '백합과', '{6,7}', '6~7월', 'summer', '흰색', 'normal', '화단·화분', 'mid', '{147}', 2),
  (149, '원추리', 'Hemerocallis fulva', '백합과', '{6,7,8}', '6~8월', 'summer', '주황', 'common', '도로변·공원', 'mid', '{150,147}', 1),
  (150, '노랑원추리', 'Hemerocallis thunbergii', '백합과', '{7,8}', '7~8월', 'summer', '노랑', 'normal', '공원·화단', 'mid', '{149}', 2),
  (151, '옥잠화', 'Hosta plantaginea', '백합과', '{8,9}', '8~9월', 'autumn', '흰색', 'normal', '화단·공원', 'mid', '{152}', 2),
  (152, '비비추', 'Hosta longipes', '백합과', '{7,8}', '7~8월', 'summer', '보라', 'common', '화단·공원 그늘', 'mid', '{151}', 1),
  (153, '상사화', 'Lycoris squamigera', '수선화과', '{7,8}', '7~8월', 'summer', '분홍', 'normal', '공원·사찰', 'low', '{188}', 2),
  (154, '연꽃', 'Nelumbo nucifera', '연꽃과', '{7,8}', '7~8월', 'summer', '분홍', 'normal', '연못·수생공원', 'low', '{155}', 1),
  (155, '수련', 'Nymphaea tetragona', '수련과', '{6,7,8}', '6~8월', 'summer', '흰색', 'normal', '연못', 'low', '{154}', 2),
  (156, '부추꽃', 'Allium tuberosum', '백합과', '{8,9}', '8~9월', 'summer', '흰색', 'common', '밭·화단', 'mid', '{198}', 2),
  (157, '백일홍', 'Zinnia elegans', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', '기타', 'common', '화단', 'mid', '{173}', 1),
  (158, '채송화', 'Portulaca grandiflora', '쇠비름과', '{6,7,8,9}', '6~9월', 'summer', '기타', 'common', '화단·화분', 'mid', '{159}', 1),
  (159, '송엽국', 'Lampranthus spectabilis', '석류풀과', '{4,5,6}', '4~6월', 'summer', '분홍', 'common', '화단·경사면', 'mid', '{158}', 1),
  (160, '페튜니아', 'Petunia hybrida', '가지과', '{5,6,7,8,9,10}', '5~10월', 'summer', '기타', 'common', '화단·걸이 화분', 'mid', '{}', 1),
  (161, '메리골드', 'Tagetes erecta', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', '주황', 'common', '화단', 'mid', '{162}', 1),
  (162, '만수국', 'Tagetes patula', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', '노랑', 'common', '화단', 'high', '{161}', 2),
  (163, '깨꽃', 'Salvia splendens', '꿀풀과', '{6,7,8,9,10}', '6~10월', 'summer', '붉은색', 'common', '화단', 'low', '{}', 1),
  (164, '맨드라미', 'Celosia cristata', '비름과', '{7,8,9}', '7~9월', 'summer', '붉은색', 'normal', '화단', 'low', '{}', 2),
  (165, '제라늄', 'Pelargonium inquinans', '쥐손이풀과', '{5,6,7,8,9,10}', '5~10월', 'summer', '붉은색', 'common', '화분·베란다', 'mid', '{}', 2),
  (166, '임파첸스', 'Impatiens walleriana', '봉선화과', '{6,7,8,9,10}', '6~10월', 'summer', '분홍', 'common', '화단 그늘', 'high', '{130,167}', 2),
  (167, '베고니아', 'Begonia semperflorens', '베고니아과', '{5,6,7,8,9,10}', '5~10월', 'summer', '분홍', 'common', '화단·화분', 'mid', '{166}', 2),
  (168, '버들마편초', 'Verbena bonariensis', '마편초과', '{7,8,9}', '7~9월', 'summer', '보라', 'common', '화단·경관 초지', 'mid', '{169}', 2),
  (169, '버베나', 'Verbena hybrida', '마편초과', '{5,6,7,8,9}', '5~9월', 'summer', '보라', 'normal', '화단·화분', 'mid', '{168}', 3),
  (170, '수레국화', 'Centaurea cyanus', '국화과', '{6,7}', '6~7월', 'summer', '파랑', 'normal', '화단·경관 초지', 'low', '{}', 2),
  (171, '안개꽃', 'Gypsophila elegans', '석죽과', '{6,7}', '6~7월', 'summer', '흰색', 'normal', '화단·꽃다발', 'mid', '{}', 3),
  (172, '가자니아', 'Gazania rigens', '국화과', '{6,7,8,9}', '6~9월', 'summer', '노랑', 'normal', '화단', 'mid', '{}', 3),
  (173, '다알리아', 'Dahlia pinnata', '국화과', '{7,8,9,10}', '7~10월', 'summer', '기타', 'normal', '화단', 'mid', '{157}', 2),
  (174, '글라디올러스', 'Gladiolus hybridus', '붓꽃과', '{7,8}', '7~8월', 'summer', '기타', 'normal', '화단·밭', 'mid', '{}', 3),
  (175, '칸나', 'Canna generalis', '홍초과', '{7,8,9}', '7~9월', 'summer', '붉은색', 'normal', '화단·공원', 'low', '{}', 2),
  (176, '한련화', 'Tropaeolum majus', '한련과', '{6,7,8,9}', '6~9월', 'summer', '주황', 'normal', '화단·화분', 'mid', '{}', 3),
  (177, '천일홍', 'Gomphrena globosa', '비름과', '{7,8,9,10}', '7~10월', 'summer', '분홍', 'normal', '화단', 'low', '{}', 2),
  (178, '아게라텀', 'Ageratum houstonianum', '국화과', '{6,7,8,9}', '6~9월', 'summer', '보라', 'normal', '화단', 'mid', '{}', 3),
  (179, '국화', 'Chrysanthemum morifolium', '국화과', '{10,11}', '10~11월', 'autumn', '기타', 'common', '화단·전시·화분', 'mid', '{180,181}', 1),
  (180, '산국', 'Chrysanthemum boreale', '국화과', '{9,10,11}', '9~11월', 'autumn', '노랑', 'normal', '산기슭·풀밭', 'high', '{181,179}', 2),
  (181, '감국', 'Chrysanthemum indicum', '국화과', '{10,11}', '10~11월', 'autumn', '노랑', 'normal', '산기슭·해안', 'high', '{180}', 3),
  (182, '구절초', 'Dendranthema zawadskii', '국화과', '{9,10}', '9~10월', 'autumn', '흰색', 'normal', '산지·화단', 'mid', '{184,183}', 1),
  (183, '벌개미취', 'Aster koraiensis', '국화과', '{6,7,8,9,10}', '6~10월', 'autumn', '보라', 'common', '도로변·공원', 'high', '{184,185}', 1),
  (184, '쑥부쟁이', 'Aster yomena', '국화과', '{8,9,10}', '8~10월', 'autumn', '보라', 'common', '길가·풀밭', 'high', '{183,186}', 1),
  (185, '개미취', 'Aster tataricus', '국화과', '{8,9,10}', '8~10월', 'autumn', '보라', 'normal', '산지·화단', 'high', '{183}', 2),
  (186, '미국쑥부쟁이', 'Symphyotrichum pilosum', '국화과', '{9,10}', '9~10월', 'autumn', '흰색', 'common', '공터·도로변', 'high', '{184,101}', 2),
  (187, '참취', 'Aster scaber', '국화과', '{8,9,10}', '8~10월', 'autumn', '흰색', 'normal', '산지 숲', 'high', '{186}', 3),
  (188, '꽃무릇', 'Lycoris radiata', '수선화과', '{9,10}', '9~10월', 'autumn', '붉은색', 'normal', '사찰·공원', 'low', '{153}', 1),
  (189, '용담', 'Gentiana scabra', '용담과', '{9,10}', '9~10월', 'autumn', '파랑', 'rare', '산지 풀밭', 'mid', '{}', 3),
  (190, '투구꽃', 'Aconitum jaluense', '미나리아재비과', '{9,10}', '9~10월', 'autumn', '보라', 'rare', '산지 숲', 'mid', '{}', 3),
  (191, '마타리', 'Patrinia scabiosifolia', '마타리과', '{8,9,10}', '8~10월', 'autumn', '노랑', 'normal', '산기슭·풀밭', 'mid', '{192}', 2),
  (192, '뚝갈', 'Patrinia villosa', '마타리과', '{8,9}', '8~9월', 'autumn', '흰색', 'normal', '산기슭', 'high', '{191}', 3),
  (193, '며느리밥풀꽃', 'Melampyrum roseum', '현삼과', '{8,9}', '8~9월', 'autumn', '분홍', 'normal', '산지 숲', 'mid', '{}', 3),
  (194, '왕고들빼기', 'Lactuca indica', '국화과', '{8,9,10}', '8~10월', 'autumn', '노랑', 'common', '공터·길가', 'mid', '{46}', 2),
  (195, '방가지똥', 'Sonchus oleraceus', '국화과', '{5,6,7,8,9,10}', '5~10월', 'autumn', '노랑', 'common', '길가·화단 틈', 'high', '{45}', 3),
  (196, '미국가막사리', 'Bidens frondosa', '국화과', '{8,9,10}', '8~10월', 'autumn', '노랑', 'common', '물가·공터', 'mid', '{}', 3),
  (197, '서양등골나물', 'Ageratina altissima', '국화과', '{9,10}', '9~10월', 'autumn', '흰색', 'common', '산기슭·도심 숲', 'mid', '{}', 2),
  (198, '산부추', 'Allium thunbergii', '백합과', '{9,10}', '9~10월', 'autumn', '보라', 'normal', '산지·바위', 'mid', '{156}', 3),
  (199, '억새', 'Miscanthus sinensis', '벼과', '{9,10}', '9~10월', 'autumn', '기타', 'common', '산지·하천·억새밭', 'mid', '{200}', 1),
  (200, '수크령', 'Pennisetum alopecuroides', '벼과', '{8,9,10}', '8~10월', 'autumn', '기타', 'common', '길가·도로변', 'mid', '{199}', 2)
on conflict (id) do update set
  name               = excluded.name,
  scientific_name    = excluded.scientific_name,
  family             = excluded.family,
  bloom_months       = excluded.bloom_months,
  bloom_label        = excluded.bloom_label,
  season             = excluded.season,
  color              = excluded.color,
  rarity             = excluded.rarity,
  habitat            = excluded.habitat,
  ai_difficulty      = excluded.ai_difficulty,
  similar_flower_ids = excluded.similar_flower_ids,
  illust_batch       = excluded.illust_batch;

-- 적재 검증. 200이 아니면 뭔가 빠진 것이다.
do $$
declare n int;
begin
  select count(*) into n from public.flowers;
  if n <> 200 then
    raise exception '도감 종수가 %개다. 200이어야 한다', n;
  end if;
end $$;


-- ══════════════════════════════════════════════════════════════
-- ▼ 0004_region_change.sql
-- ══════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- 0004. 활동 지역 변경 — `region_changed_at`을 서버가 채우고, 6개월 규칙을 강제한다
--
-- 화면 02(활동 지역 선택)가 붙으면서 필요해졌다. 지금까지 이 칸은 **아무도 채우지
-- 않았다** — `handle_new_user`는 `(id, nickname)`만 넣고(0001 8절), 앱의
-- `users` PATCH는 지역 세 칸만 보낸다. 그래서 화면 20의
-- `2027. 2. 4. 부터 가능` 줄이 **항상 안 뜨는 상태**였다(`구현현황_AOS.md` 8절).
--
-- ## 🔴 왜 클라이언트에 두면 안 되나
--
-- 기획서 9장 "활동 지역은 6개월에 한 번만 변경". 이걸 앱에서만 막으면
-- **`users` PATCH를 직접 부르는 요청은 그대로 들어간다.** RLS `users_update_self`는
-- "본인 행인가"만 보고 "언제 바꿨나"는 안 본다. B-5 하루 1회 제한을 DB에서 한 번 더
-- 막은 것과 **같은 이유**다(0001 3절 주석: "이걸 클라이언트에만 두면 규칙이 아니다").
--
-- 지역을 마음대로 바꿀 수 있으면 **랭킹 1위를 골라 다니는 것**이 가능하다 —
-- 이웃이 적은 동네로 옮겨 1위를 받고 다시 옮긴다. 6개월 규칙이 그걸 막는 장치다.
--
-- ## ⚠️ 시각을 클라이언트가 주지 않는다
--
-- `set_captured_date`와 같은 이유다(0001 3절). 앱이 `region_changed_at`을 보내게 하면
-- **7개월 전 날짜를 보내서 규칙을 우회한다.** 그래서 트리거가 `now()`로 덮는다.
-- 앱이 무엇을 보내든 무시된다.
--
-- ## ⚠️ 처음 정하는 것은 "변경"이 아니다
--
-- `region_changed_at`이 null이면 **한 번도 안 바꾼 것**이므로 통과시킨다.
-- 가입일 기준으로 계산하면 **갓 가입한 사용자가 6개월간 동네를 못 고른다** —
-- 화면 02는 온보딩 화면이라 그러면 아무도 랭킹을 볼 수 없다.
-- (같은 판단이 `RankingUiMapper.regionChangeLabel` 주석에 있다.)
-- ─────────────────────────────────────────────────────────────

-- 기획서 9장. **이 숫자를 두 곳에 쓰지 않는다** — 아래 함수만 본다.
-- 클라이언트 쪽 짝은 `RankingUiMapper.REGION_CHANGE_MONTHS`(= 6)이고,
-- 어긋나면 앱은 "바꿀 수 있다"고 말하는데 서버가 거절한다.
create or replace function public.enforce_region_change()
returns trigger
language plpgsql
as $$
begin
  -- 지역이 안 바뀐 업데이트(닉네임 변경 등)는 손대지 않는다.
  --
  -- 🔴 **`is distinct from`이다.** `<>`로 쓰면 **null이 섞인 비교가 null**이 되어
  --    조건이 거짓이 되고, 지역을 **처음 정하는 경우**(old가 null)가 통째로
  --    이 검사를 빠져나간다 — 그러면 `region_changed_at`이 여전히 안 채워진다.
  if new.dong_code is not distinct from old.dong_code then
    return new;
  end if;

  -- 한 번도 안 바꿨으면 통과. 위 주석의 이유.
  if old.region_changed_at is not null
     and old.region_changed_at > now() - interval '6 months' then
    -- `P0001`이다. 앱은 이 코드를 보고 "규칙으로 거절됐다"를 안다 —
    -- 네트워크 실패(재시도해야 한다)와 반대로 **재시도하면 안 되는** 경우다.
    raise exception
      '활동 지역은 6개월에 한 번만 변경할 수 있습니다 (다음 변경 가능: %)',
      to_char(old.region_changed_at + interval '6 months', 'YYYY-MM-DD')
      using errcode = 'P0001';
  end if;

  -- **클라이언트가 준 값을 쓰지 않는다.** 위 주석의 이유.
  new.region_changed_at := now();
  return new;
end;
$$;

drop trigger if exists users_enforce_region_change on public.users;
create trigger users_enforce_region_change
  before update of dong_code on public.users
  for each row execute function public.enforce_region_change();

-- ⚠️ `before update of dong_code`다. `before update`로 넓히면 닉네임만 바꾼
--    업데이트도 트리거를 지나가고, 함수 첫 줄이 걸러 주긴 하지만 **모든 프로필
--    수정이 이 함수를 타게** 된다. 좁혀 두면 의도가 이름에 남는다.

-- ─────────────────────────────────────────────────────────────
-- 지역 세 칸은 **같이** 바뀌어야 한다
--
-- 🔴 `dong_code`만 바꾸고 `gu_code`를 안 바꾸면 **B-6 구 확장이 옛 구로 묶인다.**
--    이웃이 10명 미만일 때 서버가 구 랭킹을 주는데(0002), 그 구가 지금 사는 구가
--    아니다. 오류는 안 난다 — **랭킹에 남의 동네 사람이 섞여 나온다.**
--
-- `region_name`도 같이 본다. 없으면 화면 20이 이름 없는 지역을 보여준다.
-- ⚠️ **`not valid`로 붙인다.** 이미 있는 행 중 세 칸이 다 null인 계정이 정상이고
--    (아직 지역을 안 정한 사용자), 기존 행 검사에서 걸리면 마이그레이션이 실패한다.
-- ─────────────────────────────────────────────────────────────
alter table public.users drop constraint if exists users_region_all_or_none;
alter table public.users add constraint users_region_all_or_none check (
  (dong_code is null and gu_code is null and region_name is null)
  or (dong_code is not null and gu_code is not null and region_name is not null)
) not valid;

-- ─────────────────────────────────────────────────────────────
-- 코드 형식을 못 박는다
--
-- 🔴 **실측으로 확인한 사고 두 개를 여기서 막는다:**
--    ① 카카오 주소 검색은 **법정동 코드(`b_code`)를 섞어서 준다.** 그걸 넣으면
--       `discoveries.dong_code`(행정동)와 체계가 달라 랭킹이 **영구히 빈다.**
--       두 코드가 자리수가 같아서(둘 다 10자리) 형식으로는 못 가른다 —
--       그래서 자리수·숫자만 막고, 행정동 판정은 앱이 `h_code`로 한다
--       (`RegionSearchService.isAdminDong`).
--    ② 구·시를 검색하면 `1144000000`(마포구) 같은 **구 코드**가 온다.
--       뒤 5자리가 `00000`이면 동이 아니다 — 그건 여기서 막을 수 있다.
--
--    ③ 안드로이드 `org.json`이 JSON null을 문자열 `"null"`로 주는 함정이 있어서
--       (`JsonNull.kt`) `"null"`이 코드로 올라올 수 있었다. 숫자 검사가 그걸 막는다.
--
-- 🔴 **①은 이미 일어났다. 지금 DB에 잘못된 행이 3개 있다** (2026-08-08 실측 ·
--    `public_profiles` 조회):
--
--      꽃친구2cdf · 꽃친구5bf7 · 꽃친구3070  →  dong_code = 1144012400
--
--    `1144012400`은 연남동 **법정동** 코드다. 행정동은 `1144071000`이다.
--    (한 개 더: 꽃친구b92a는 `region_name = '성동구 성수동1가'` — 시도가 빠져 있다.)
--    화면 02가 없던 동안 손으로 PATCH해 넣은 테스트 값이고, **전부 내가 만든
--    익명 테스트 계정이라 지워도 된다**(`구현현황_AOS.md` 9절 뒷정리 목록).
--
--    ⚠️ 그래서 아래 제약을 **`not valid`로 붙인다.** `validate`하면 이 3행 때문에
--       마이그레이션이 실패한다. 형식 검사는 자리수만 보므로 법정동을 못 가른다 —
--       **이 3행은 제약을 통과한다.** 가려내는 것은 앱의
--       `RegionSearchService.isAdminDong`이고, 여기 제약은 구 코드·`"null"`·
--       세 칸 불일치를 막는 2차 방어다.
-- ─────────────────────────────────────────────────────────────
alter table public.users drop constraint if exists users_dong_code_format;
alter table public.users add constraint users_dong_code_format check (
  dong_code is null
  or (dong_code ~ '^[0-9]{10}$' and right(dong_code, 5) <> '00000')
) not valid;

alter table public.users drop constraint if exists users_gu_code_format;
alter table public.users add constraint users_gu_code_format check (
  gu_code is null or gu_code ~ '^[0-9]{5}$'
) not valid;

-- ⚠️ **`gu_code`가 `dong_code`의 앞 5자리인지도 검사한다.** 앱이 두 값을 따로
--    보내므로 어긋난 조합을 보낼 수 있고, 어긋나면 B-6이 남의 구로 묶는다.
--    `KakaoPlaceService`·`RegionSearchService`가 같은 규칙(앞 5자리)을 쓴다.
alter table public.users drop constraint if exists users_gu_code_matches_dong;
alter table public.users add constraint users_gu_code_matches_dong check (
  dong_code is null or gu_code is null or gu_code = left(dong_code, 5)
) not valid;

-- ─────────────────────────────────────────────────────────────
-- 화면 02 목록의 `이웃 1,284명 활동 중`
--
-- ## 🔴 왜 `public_profiles`를 세지 않는가 — 두 화면이 다른 숫자를 말하게 된다
--
-- 실측으로 `public_profiles`는 anon 키로도 `dong_code`별 개수를 준다
-- (206 · `Content-Range: 0-0/11`). 그래서 앱에서 바로 셀 수 있다. **그런데
-- 그 숫자는 가입자 수다.** 화면 17의 `{동명} 이웃 1,284명`은 위 `region_ranking`이
-- 주는 `member_count`이고, 그건 **"이번 시즌 발견이 있는 사람" 수**다
-- (위 2절 주석: "가입만 한 사람을 세면 10명을 넘겨도 랭킹이 텅 빈다").
--
-- 두 정의를 섞으면 화면 02에서 `이웃 12명 활동 중`을 보고 동네를 고른 사용자가
-- 화면 17에서 `연남동 이웃 3명`을 본다. **어느 쪽이 맞는지 화면만 봐선 모른다** —
-- (18)에서 친구 수 7 vs 8로 이미 겪은 사고다(A 문서 3절 `친구 수를 모를 때`).
--
-- 그래서 **`region_ranking`의 `dong_members` CTE와 글자 그대로 같은 집합**을 센다.
-- 앱에서 세지 않는 이유도 같다 — 남의 발견 기록은 기기에 없다.
--
-- ## ⚠️ 이 함수만 `assert_self`를 안 쓴다
--
-- 다른 랭킹 함수와 다르다. **아직 내 동네가 아닌 동네를 세는 것이 목적**이라
-- 본인 확인이 성립하지 않는다(화면 02는 동네를 고르기 **전** 화면이다).
-- 대신 내보내는 것이 **정수 하나**다 — 누가 있는지·닉네임·발견 내용은 나가지 않는다.
-- `public_profiles`가 이미 같은 동네 사람 목록을 anon에게까지 주고 있으므로
-- (0001 7-2절) 인원수는 그보다 좁은 정보다.
--
-- ⚠️ **`authenticated`만 준다.** anon에게 열면 로그인 없이 전국 동별 활동량을
--    긁을 수 있다. 화면 02는 익명 로그인 뒤에 뜨므로 이걸로 충분하다.
-- ─────────────────────────────────────────────────────────────
create or replace function public.dong_member_count(
  target_dong text,
  ts          timestamptz default now()
)
returns int
language sql
stable
as $$
  -- `region_ranking`의 `dong_members`와 **같은 집합**이다. 한쪽만 고치면
  -- 화면 02와 17이 다른 숫자를 말한다 — 고칠 때 둘 다 고친다.
  select count(distinct d.user_id)::int
  from public.discoveries d
  join public.users u on u.id = d.user_id
  join public.season_bounds(ts) b
    on d.captured_at >= b.season_start and d.captured_at < b.season_end
  where u.dong_code = target_dong and u.deleted_at is null;
$$;

-- `security definer`다. 그러지 않으면 `discoveries` RLS 때문에 **호출자에게 보이는
-- 기록만 세어** 남의 동네가 항상 0명으로 나온다 — 0001 7-3절에서 정책 안 서브쿼리가
-- RLS를 타서 두 번 뚫린 것과 **같은 함정**이다. 오류는 안 나고 숫자만 조용히 0이 된다.
alter function public.dong_member_count(text, timestamptz)
  security definer set search_path = public;

grant  execute on function public.dong_member_count(text, timestamptz) to authenticated;
revoke execute on function public.dong_member_count(text, timestamptz) from anon;
