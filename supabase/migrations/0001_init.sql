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
