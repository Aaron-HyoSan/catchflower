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

-- 도감 마스터 — 사람이 정한 200종 (id 1~200)
--
-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`이고
-- 그 입력은 `공용_적재/flowers.json`(← `꽃도감/꽃목록_200종.csv`)이다.
-- 꽃 데이터를 바꾸려면 `꽃도감/_tools/flowers.py`부터 고친다.
--
-- `bloom_months`는 **이미 파싱된 값**이다 (공유계약 1-2: 파싱은 단 한 번).
-- 적용: 0001_init.sql 다음에 SQL Editor에서 Run. 여러 번 돌려도 안전하다.
--
-- ⚠️ **여기에 신규 1,857종은 없다.** 그건 `0006_seed_flowers_2057.sql`이고,
--    0001의 제약(`id between 1 and 200` · `season not null` 등) 때문에
--    **0005가 먼저 돌아야** 들어간다. 파일이 두 개인 이유가 그것이다.
-- ⚠️ **`bloom_source` 컬럼을 여기서 적지 않는다.** 이 파일은 그 컬럼이 생기기 전
--    스키마에서도 돌아야 한다(합본은 번호순이다). 0005가 기본값 `'human'`을 주고,
--    id 1~200은 실제로 사람이 정한 값이라 그 기본값이 사실과 맞다.

insert into public.flowers
  (id, name, scientific_name, family, bloom_months, bloom_label, season, color, rarity, habitat, ai_difficulty, similar_flower_ids, illust_batch)
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

-- 적재 검증. 200종보다 적으면 뭔가 빠진 것이다.
-- (더 많은 것은 실패가 아니다 — 뒤 파일이 이미 돌았을 수 있다.)
do $$
declare n int;
begin
  select count(*) into n from public.flowers;
  if n < 200 then
    raise exception '도감 종수가 %개다. 200종 이상이어야 한다', n;
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


-- ══════════════════════════════════════════════════════════════
-- ▼ 0005_flowers_expand_2057.sql
-- ══════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- 0005. 도감 확장 200종 → 2,057종 — 스키마가 신규종을 **받을 수 있게** 만든다
--
-- 0001은 도감을 200종으로 못 박아 놨다. 확장분을 그대로 넣으면 **네 군데서 막힌다**:
--
--   ① `flowers_id_range check (id between 1 and 200)` → 201번부터 전부 거부
--   ② `season flower_season not null`      → 근거 없는 278종은 null이어야 한다
--   ③ `bloom_label text not null`          → 표기를 낼 수 없는 1,026종이 있다
--   ④ `color`·`habitat` not null           → 신규 1,857종은 채울 근거가 없다
--
-- ②③④를 "빈 문자열을 넣어서" 통과시키면 안 된다 — 그게 계약 1-1-c가 막는 것이고,
-- 화면에 `국화과 · 에 피는 꽃`·테두리만 있는 칩이 나온다. **null로 내리고 화면이
-- 절을 뺀다**(1-2-c). 클라이언트는 이미 그렇게 되어 있다(AOS `Flower.bloomText` 외 3개).
--
-- 그리고 근거를 **함께 저장한다**: `bloom_source`. 이게 없으면
-- "2,057종을 등록했다"가 "2,057종을 다 안다"로 읽히고, 사람이 채울 목록을
-- 세는 방법이 데이터 안에 없다(계약 1-2-b).
--
-- ⚠️ **`id`를 재배치하지 않는다.** `discoveries.flower_id`가 외래키라 번호를 바꾸면
--    **사용자 발견 기록이 다른 꽃을 가리킨다.** 신규종은 201번부터 붙였다.
--
-- 멱등하다. 두 번 돌려도 안전하다.
-- ─────────────────────────────────────────────────────────────


-- ─────────────────────────────────────────────────────────────
-- 1. id 범위를 1~2057로 넓힌다
--
-- ⚠️ **상한을 없애지 않고 늘린다.** `check (id >= 1)`로 열어 두면 오타 한 번에
--    `20570`번 꽃이 들어가고, 그건 앱의 도감 그리드에서 **번호가 빈 칸**으로만
--    보인다(일러스트도 안 나오는데 그건 정상 상태와 구별이 안 된다).
--    종수는 코드에도 상수로 있다(`GamePolicy.TOTAL_FLOWER_COUNT`) — 양쪽이 같아야 한다.
-- ─────────────────────────────────────────────────────────────

alter table public.flowers drop constraint if exists flowers_id_range;
alter table public.flowers add  constraint flowers_id_range check (id between 1 and 2057);


-- ─────────────────────────────────────────────────────────────
-- 2. `bloom_source` — 어떤 근거로 개화월을 정했는가 (계약 1-2-b)
--
-- 값: human | draft | observed | peak_window | unknown
--
-- 🔴 **enum이 아니라 text + check로 둔다.** 다른 enum들(0001 0절)과 다른 선택이라
--    이유를 적는다: 이 컬럼은 **채워 나가는 작업 목록**이라 값이 늘 수 있고
--    (예: `expert`), postgres enum에 값을 추가하는 것은 트랜잭션 안에서 쓸 수 없는
--    제약이 붙는다. 화면에 안 나가는 내부 축이라 정렬도 필요 없다.
--
-- ⚠️ **화면에 내보내지 않는다** — 사용자에게 보일 문구가 A 문서에 없다.
--
-- 🔴 **기본값 `'human'`을 둔다. 두고 싶지 않았는데 둬야 한다** — 이유를 적는다.
--
-- 기본값이 있으면 적재가 이 컬럼을 **빼먹어도 통과**하고, 그러면 "1,857종을
-- 등록했다"가 "다 안다"로 읽히는 상태가 조용히 만들어진다. 그래서 처음엔 안 뒀다.
-- 그런데 **합본은 번호순으로 돈다**: `0003_seed_flowers.sql`(200종 insert)이
-- 이 파일보다 **먼저** 있고, 0003은 이 컬럼이 생기기 전에 쓴 파일이라
-- 컬럼 이름을 적지 않는다. 기본값이 없으면 **합본을 두 번 돌릴 때 0003의 insert가
-- not null 위반으로 터진다**(postgres는 `on conflict` 판정보다 먼저 not null을 본다 —
-- 즉 행이 이미 있어도 터진다). 합본은 "두 번 돌려도 안전하다"가 약속이다.
--
-- 그래서 기본값을 두고, **대신 세어서 막는다**: 0006 끝에서
-- `bloom_source = 'human'`인 종이 정확히 200이어야 한다고 단정한다.
-- 무언가 기본값으로 조용히 들어오면 그 수가 200을 넘어 거기서 걸린다.
-- (0003이 넣는 id 1~200은 실제로 사람이 정한 값이라 기본값이 사실과 맞다.)
-- ─────────────────────────────────────────────────────────────

alter table public.flowers add column if not exists bloom_source text default 'human';

do $$ begin
  alter table public.flowers
    add constraint flowers_bloom_source_enum
    check (bloom_source in ('human','draft','observed','peak_window','unknown'));
exception when duplicate_object then null; end $$;

-- 이 컬럼이 생기기 전에 들어간 200종은 사람이 정한 값이다.
-- null로 남으면 아래 not null이 실패하므로 먼저 채운다.
update public.flowers set bloom_source = 'human' where bloom_source is null;

alter table public.flowers alter column bloom_source set not null;

-- 사람이 채울 목록을 **세는** 인덱스. `where bloom_source <> 'human'`으로
-- 부분 인덱스를 만들 이유는 없다 — 200종도 같이 세는 질의가 정상이다.
create index if not exists flowers_bloom_source_idx on public.flowers (bloom_source);


-- ─────────────────────────────────────────────────────────────
-- 3. 근거 없는 칸을 **null로 받는다** — 빈 문자열이 아니라 null이다
--
-- 🔴 왜 빈 문자열이 아닌가. `''`는 "값이 있고 그것이 빈 문자열"이다. 그러면
--    `coalesce`도 `is null` 검사도 안 걸리고, 화면 쪽에서 **각자 `isEmpty`를
--    확인해야 한다** — 한 화면이 빠뜨리면 `에 피어요.`가 나오고 아무 검사도 안 빨개진다.
--    null이면 잊었을 때 터진다(그게 낫다).
--
-- ⚠️ 그런데 **지금 클라이언트는 빈 문자열도 받는다**(AOS는 `optString`+`isNull`을
--    구분해 읽는다). 이 마이그레이션은 `''`를 null로 **정규화하지 않는다** —
--    0003이 넣은 200종에는 이 세 칸이 전부 채워져 있어서 바꿀 것이 없고,
--    확장 적재는 처음부터 null을 보낸다.
-- ─────────────────────────────────────────────────────────────

alter table public.flowers alter column season      drop not null;
alter table public.flowers alter column bloom_label drop not null;
alter table public.flowers alter column color       drop not null;
alter table public.flowers alter column habitat     drop not null;


-- ─────────────────────────────────────────────────────────────
-- 4. 🔴 개화월 무결성 — DB가 막아야 하는 두 가지
--
-- 0001은 `array_length >= 1`만 봤다. 그것으로는 **실제로 겪은 결함이 통과한다**:
-- 상록수 9종에 `observed_run`의 랩어라운드 버그로 **23개월**이 들어갔고
-- (`[6,7,…,4]` — 6월이 두 번), 그 상태로도
--   · 개화월 하드 필터는 `month = any(bloom_months)`라 **정답이 그대로 나온다**
--   · 정확도 지표는 **하나도 안 움직인다**
-- 유일한 증상은 `bloom_label`이 `6~4월`이 되어 화면 09에 `6~4월에 피는 꽃`이
-- 뜨는 것이었다. 즉 **지표로는 원리상 못 잡는다** → 데이터 층에서 막는다.
--
-- 적재 스크립트(`flower_master._verify`)도 같은 것을 본다. 두 곳에 두는 이유:
-- 적재는 우리가 고칠 수 있지만 **대시보드에서 손으로 넣는 경로**도 있다.
--
-- ⚠️ **check 안에 서브쿼리를 쓸 수 없다.** postgres가 거절한다
--    (`cannot use subquery in check constraint`). `select count(distinct m) from
--    unnest(...)`로 쓰려다 막혔다 — 그래서 함수로 감싼다. 함수 호출은 허용된다.
--    ⚠️ 함수를 쓰면 **정의를 바꿔도 기존 행을 다시 검사하지 않는다**(postgres는
--    immutable을 믿는다). 규칙을 바꿀 때는 `validate constraint`를 다시 돌린다.
-- ─────────────────────────────────────────────────────────────

create or replace function public.bloom_months_sane(months int[])
returns boolean
language sql
immutable
-- `strict`를 쓰지 않는다. null 입력에 null을 돌려주면 check가 통과해 버린다
-- (postgres check는 null을 위반으로 보지 않는다). null은 명시적으로 false다.
as $$
  select months is not null
     and array_length(months, 1) between 1 and 12
     -- 중복 없음. `array_length`만 보면 **23개월이 통과한다.**
     and array_length(months, 1) = (select count(distinct m) from unnest(months) m)
     -- 1~12 밖의 값. `0월`·`13월`은 하드 필터에서 **영구히 안 걸리는 달**이다.
     and not exists (select 1 from unnest(months) m where m is null or m < 1 or m > 12);
$$;

do $$ begin
  alter table public.flowers
    add constraint flowers_bloom_months_sane
    check (public.bloom_months_sane(bloom_months));
exception when duplicate_object then null; end $$;


-- ─────────────────────────────────────────────────────────────
-- 5. 🔴 `1~12월`을 라벨로 쓰지 않는다 — 12개월이면 표기가 없어야 한다
--
-- "12개월 모두에서 관찰됐다"는 근거는 "일 년 내내 핀다"를 뜻하지 **않는다.**
-- 상록수가 그렇다. 라벨은 화면 문구에 그대로 보간되므로 `1~12월에 피는 꽃`은
-- **없는 사실을 단정하는 문장**이 된다(계약 1-2-c와 같은 원리).
--
-- 이건 `bloom_source`로 판정할 수 없다 — 그 9종의 출처는 `observed`다.
-- 그래서 **개화월 개수로** 막는다.
-- ─────────────────────────────────────────────────────────────

do $$ begin
  alter table public.flowers
    add constraint flowers_no_all_year_label
    check (array_length(bloom_months, 1) < 12 or bloom_label is null or bloom_label = '');
exception when duplicate_object then null; end $$;


-- ─────────────────────────────────────────────────────────────
-- 6. 적용 확인 — **"에러 없음"은 확인이 아니다**
--
-- 위 `alter`들은 조건이 이미 맞으면 조용히 통과한다. 그래서 **바뀐 상태를
-- 직접 되읽는다.** 특히 `drop not null`은 이미 nullable이어도 성공하므로
-- 확인 없이는 "돌았다"와 "원래 그랬다"를 구별할 수 없다.
-- ─────────────────────────────────────────────────────────────

do $$
declare
  n_nullable int;
  has_source bool;
  id_max int;
begin
  select count(*) into n_nullable
    from information_schema.columns
   where table_schema = 'public' and table_name = 'flowers'
     and column_name in ('season','bloom_label','color','habitat')
     and is_nullable = 'YES';
  if n_nullable <> 4 then
    raise exception 'season·bloom_label·color·habitat 중 %개만 null 허용이다. 4개여야 한다', n_nullable;
  end if;

  select exists (
    select 1 from information_schema.columns
     where table_schema = 'public' and table_name = 'flowers' and column_name = 'bloom_source'
  ) into has_source;
  if not has_source then
    raise exception 'bloom_source 컬럼이 없다';
  end if;

  -- id 상한이 실제로 넓어졌는가. 제약 정의 문자열을 본다 —
  -- 2057번을 시험 삽입하면 `discoveries`에 쓰레기가 남을 수 있어서 하지 않는다.
  select 1 into id_max from pg_constraint
   where conrelid = 'public.flowers'::regclass
     and conname = 'flowers_id_range'
     and pg_get_constraintdef(oid) like '%2057%';
  if not found then
    raise exception 'flowers_id_range가 아직 2057까지 아니다';
  end if;

  raise notice '0005 적용됨: id 1~2057 · bloom_source 있음 · 4칸 null 허용';
end $$;


-- ══════════════════════════════════════════════════════════════
-- ▼ 0006_seed_flowers_2057.sql
-- ══════════════════════════════════════════════════════════════

-- 도감 확장 — 신규 1,857종 (id 201~2057)
--
-- **생성물이다. 직접 고치지 않는다.** 원본은 `공용_적재/seed_flowers_sql.py`.
--
-- 🔴 **0005보다 먼저 돌면 전부 거부된다.** 0001은 `id between 1 and 200`이고
--    `season`·`bloom_label`·`color`·`habitat`이 `not null`이다. 신규종은 id가
--    201부터이고 그 네 칸이 비어 있다 — 채울 근거가 없어서 **null로 내린다**
--    (계약 1-1-c·1-2-c: 없는 문구를 만들지 않고 화면이 절을 뺀다).
--
-- ⚠️ **빈 문자열이 아니라 null이다.** `''`는 "값이 있고 그것이 빈 문자열"이라
--    `is null` 검사에 안 걸리고, 화면 쪽이 각자 `isEmpty`를 봐야 한다.
--    한 화면이 빠뜨리면 `국화과 · 에 피는 꽃`이 나오는데 아무 검사도 안 빨개진다.
--
-- ⚠️ **`bloom_source`를 함께 넣는다.** 이 컬럼이 없으면 "1,857종을 등록했다"가
--    "1,857종을 다 안다"로 읽힌다. `peak_window` 739 + `unknown` 278은
--    **사람이 채워야 하는 목록**이고, 그걸 세는 방법이 데이터 안에 있어야 한다.

insert into public.flowers
  (id, name, scientific_name, family, bloom_months, bloom_label, season, color, rarity, habitat, ai_difficulty, similar_flower_ids, illust_batch, bloom_source)
values
  (201, '가거양지꽃', 'Potentilla gageodoensis', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (202, '가는개여뀌', 'Persicaria trigonocarpa', '마디풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (203, '가는괴불주머니', 'Corydalis raddeana', '현호색과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (204, '가는금불초', 'Inula linariifolia', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (205, '가는기름나물', 'Kitagawia komarovii', 'Apiaceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (206, '가는기린초', 'Phedimus aizoon', '돌나물과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (207, '가는끈끈이장구채', 'Silene antirrhina', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (208, '가는네잎갈퀴', 'Galium trifidum', '꼭두서니과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (209, '가는다리장구채', 'Silene jeniseensis', '석죽과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (210, '가는대나물', 'Gypsophila pacifica', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (211, '가는돌쩌귀', 'Aconitum villosum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (212, '가는등갈퀴', 'Vicia tenuifolia', '콩과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (213, '가는마디꽃', 'Rotala mexicana', '부처꽃과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (214, '가는바디', 'Ostericum maximowiczii', 'Apiaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (215, '가는산부추', 'Allium splendens', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (216, '가는쑥부지깽이', 'Erysimum macilentum', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (217, '가는잎가시상추', 'Lactuca saligna', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (218, '가는잎개별꽃', 'Pseudostellaria sylvatica', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (219, '가는잎금방망이', 'Senecio inaequidens', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (220, '가는잎미선콩', 'Lupinus angustifolius', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (221, '가는잎산들깨', 'Mosla chinensis', '꿀풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (222, '가는잎소리쟁이', 'Rumex stenophyllus', '마디풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (223, '가는잎쑥', 'Artemisia subulata', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (224, '가는잎조팝나무', 'Spiraea thunbergii', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (225, '가는잎털냉이', 'Sisymbrium altissimum', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (226, '가는잎푸른딱지꽃', 'Potentilla tanacetifolia', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (227, '가는잎한련초', 'Eclipta alba', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (228, '가는잎할미꽃', 'Pulsatilla cernua', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (229, '가는잎향유', 'Elsholtzia angustifolia', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (230, '가는장구채', 'Silene yanoei', '석죽과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (231, '가는장대', 'Dontostemon dentatus', '십자화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (232, '가는줄돌쩌귀', 'Aconitum volubile', '미나리아재비과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (233, '가막사리', 'Bidens tripartita', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (234, '가막살나무', 'Viburnum dilatatum', '인동과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (235, '가새잎개갓냉이', 'Rorippa sylvestris', '십자화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (236, '가솔송', 'Phyllodoce caerulea', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (237, '가시까치밥나무', 'Ribes diacantha', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (238, '가시꽈리', 'Physaliastrum echinatum', 'Solanaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (239, '가시박', 'Sicyos angulatus', 'Cucurbitaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (240, '가시상추', 'Lactuca serriola', '국화과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (241, '가시여뀌', 'Persicaria dissitiflora', '마디풀과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (242, '가시연꽃', 'Euryale ferox', '수련과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (243, '가시오갈피', 'Eleutherococcus senticosus', 'Araliaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (244, '가죽나무', 'Ailanthus altissima', 'Simaroubaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (245, '가지', 'Solanum melongena', 'Solanaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (246, '가지곡정초', 'Eriocaulon setaceum', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (247, '가지괭이눈', 'Chrysosplenium ramosum', '수국과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (248, '가지꼭두서니', 'Rubia hexaphylla', '꼭두서니과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (249, '가지더부살이', 'Phacellanthus tubiflorus', 'Orobanchaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (250, '가지털괭이눈', 'Chrysosplenium ramosissimum', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (251, '가회톱', 'Ampelopsis japonica', 'Vitaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (252, '각시둥굴레', 'Polygonatum humile', '백합과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (253, '각시마', 'Dioscorea tenuipes', 'Dioscoreaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (254, '각시붓꽃', 'Iris rossii', '붓꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (255, '각시서덜취', 'Saussurea macrolepis', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (256, '각시원추리', 'Hemerocallis dumortieri', '백합과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (257, '각시제비꽃', 'Viola boissieuana', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (258, '각시취', 'Saussurea pulchella', '국화과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (259, '각시투구꽃', 'Aconitum monanthum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (260, '갈고리네잎갈퀴', 'Galium pseudoasprellum', '꼭두서니과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (261, '갈기기름나물', 'Peucedanum chujaense', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (262, '갈기조팝나무', 'Spiraea trichocarpa', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (263, '갈래꿀풀', 'Prunella intermedia', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (264, '갈래잎어수리', 'Heracleum dissectum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (265, '갈래조팝나무', 'Spiraea trilobata', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (266, '갈매기난초', 'Platanthera japonica', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (267, '갈매나무', 'Rhamnus davurica', 'Rhamnaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (268, '갈퀴꼭두서니', 'Rubia cordifolia', '꼭두서니과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (269, '갈퀴나물', 'Vicia amoena', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (270, '갈퀴아재비', 'Asperula lasiantha', '꼭두서니과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (271, '갈퀴지치', 'Asperugo procumbens', '지치과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (272, '갈퀴현호색', 'Corydalis grandicalyx', '현호색과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (273, '감나무', 'Diospyros kaki', 'Ebenaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (274, '감자', 'Solanum tuberosum', 'Solanaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (275, '감자개발나물', 'Sium ninsi', 'Apiaceae', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (276, '감자난초', 'Oreorchis patens', 'Orchidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (277, '감절대', 'Reynoutria forbesii', '마디풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (278, '감초', 'Glycyrrhiza uralensis', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (279, '감탕나무', 'Ilex integra', 'Aquifoliaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (280, '감태나무', 'Lindera glauca', '녹나무과', '{1,2,3,4,5,6,7,8,9,10,11}', '1~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (281, '갓', 'Brassica juncea', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (282, '강계버들', 'Salix kangensis', 'Salicaceae', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (283, '강화황기', 'Astragalus sikokianus', '콩과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (284, '개갈퀴', 'Galium maximowiczii', '꼭두서니과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (285, '개갓냉이', 'Rorippa indica', '십자화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (286, '개곽향', 'Teucrium japonicum', '꿀풀과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (287, '개구리갓', 'Ranunculus ternatus', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (288, '개구리미나리', 'Ranunculus tachiroei', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (289, '개구리발톱', 'Semiaquilegia adoxoides', '미나리아재비과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (290, '개구리자리', 'Ranunculus sceleratus', '미나리아재비과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (291, '개꽃아재비', 'Anthemis cotula', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (292, '개느삼', 'Sophora koreensis', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (293, '개다래', 'Actinidia polygama', 'Actinidiaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (294, '개당주나무', 'Ribes fasciculatum', '수국과', '{2,3,4,5,6,7}', '2~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (295, '개대황', 'Rumex longifolius', '마디풀과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (296, '개도둑놈의갈고리', 'Hylodesmum podocarpum', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (297, '개똥쑥', 'Artemisia annua', '국화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (298, '개마디풀', 'Polygonum equisetiforme', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (299, '개말나리', 'Lilium medeoloides', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (300, '개맥문동', 'Liriope spicata', '백합과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (301, '개미자리', 'Sagina japonica', '석죽과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (302, '개박하', 'Nepeta cataria', '꿀풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (303, '개발나물', 'Sium suave', 'Apiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (304, '개버무리', 'Clematis serratifolia', '미나리아재비과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (305, '개벚지나무', 'Prunus glandulifolia', '장미과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (306, '개벼룩', 'Moehringia lateriflora', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (307, '개병풍', 'Astilboides tabularis', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (308, '개보리뺑이', 'Lapsanastrum apogonoides', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (309, '개복수초', 'Adonis pseudoamurensis', '미나리아재비과', '{1,2,3,4,5}', '1~5월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (310, '개불알풀', 'Veronica polita', '현삼과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (311, '개사상자', 'Torilis scabra', 'Apiaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (312, '개사철쑥', 'Artemisia caruifolia', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (313, '개산초', 'Zanthoxylum armatum', 'Rutaceae', '{6,7,8,9,10,11,12,1,2,3,4,5}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (314, '개살구나무', 'Prunus mandshurica', '장미과', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (315, '개선갈퀴', 'Galium trifloriforme', '꼭두서니과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (316, '개수염', 'Eriocaulon miquelianum', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (317, '개승마', 'Actaea biternata', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (318, '개시호', 'Bupleurum longeradiatum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (319, '개싸리', 'Lespedeza tomentosa', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (320, '개쑥갓', 'Senecio vulgaris', '국화과', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (321, '개아마', 'Linum stelleroides', 'Linaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (322, '개양귀비', 'Papaver rhoeas', '현호색과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (323, '개엉겅퀴', 'Cirsium japonicum', '국화과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (324, '개연꽃', 'Nuphar japonica', '수련과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (325, '개옻나무', 'Toxicodendron trichocarpum', 'Anacardiaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (326, '개자리', 'Medicago polymorpha', '콩과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (327, '개잠자리난초', 'Habenaria cruciformis', 'Orchidaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (328, '개제비란', 'Dactylorhiza viridis', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (329, '개종용', 'Lathraea japonica', 'Orobanchaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (330, '개차즈기', 'Amethystea caerulea', '꿀풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (331, '개키버들', 'Salix integra', 'Salicaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (332, '개탑꽃', 'Clinopodium fauriei', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (333, '개통발', 'Utricularia intermedia', 'Lentibulariaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (334, '개현삼', 'Scrophularia alata', '현삼과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (335, '개황기', 'Astragalus uliginosus', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (336, '갯강활', 'Angelica japonica', 'Apiaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (337, '갯개미자리', 'Spergularia marina', '석죽과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (338, '갯고들빼기', 'Crepidiastrum lanceolatum', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (339, '갯괴불주머니', 'Corydalis platycarpa', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (340, '갯기름나물', 'Peucedanum japonicum', 'Apiaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (341, '갯까치수염', 'Lysimachia mauritiana', '앵초과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (342, '갯당근', 'Daucus littoralis', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (343, '갯대추나무', 'Paliurus ramosissimus', 'Rhamnaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (344, '갯마디풀', 'Polygonum arenastrum', '마디풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (345, '갯메꽃', 'Calystegia soldanella', '메꽃과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (346, '갯바위패랭이꽃', 'Dianthus koreanus', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (347, '갯방풍', 'Glehnia littoralis', 'Apiaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (348, '갯버들', 'Salix gracilistyla', 'Salicaceae', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (349, '갯부추', 'Allium pseudojaponicum', '백합과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (350, '갯사상자', 'Cnidium japonicum', 'Apiaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (351, '갯실새삼', 'Cuscuta chinensis', '메꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (352, '갯씀바귀', 'Ixeris repens', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (353, '갯완두', 'Lathyrus japonicus', '콩과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (354, '갯장대', 'Arabis stelleri', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (355, '갯제비쑥', 'Artemisia littoricola', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (356, '갯질경', 'Limonium tetragonum', 'Plumbaginaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (357, '갯패랭이꽃', 'Dianthus japonicus', '석죽과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (358, '거문도닥나무', 'Wikstroemia ganpi', 'Thymelaeaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (359, '거문딸기', 'Rubus trifidus', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (360, '거센털꽃마리', 'Trigonotis radicans', '지치과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (361, '거제딸기', 'Rubus tozawae', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (362, '거지딸기', 'Rubus sumatranus', '장미과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (363, '검나무싸리', 'Lespedeza melanantha', '콩과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (364, '검노린재나무', 'Symplocos tanakana', 'Symplocaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (365, '검양옻나무', 'Toxicodendron succedaneum', 'Anacardiaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (366, '검은개선갈퀴', 'Galium japonicum', '꼭두서니과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (367, '검은개수염', 'Eriocaulon parvum', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (368, '검은곡정초', 'Eriocaulon atrum', 'Eriocaulaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (369, '검은딸기', 'Rubus croceacanthus', '장미과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (370, '검종덩굴', 'Clematis fusca', '미나리아재비과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (371, '게박쥐나물', 'Parasenecio adenostyloides', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (372, '겨울딸기', 'Rubus buergeri', '장미과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (373, '겨이삭여뀌', 'Persicaria taquetii', '마디풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (374, '겨자무', 'Armoracia rusticana', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (375, '결명자', 'Senna tora', '콩과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (376, '계수나무', 'Cercidiphyllum japonicum', 'Cercidiphyllaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (377, '계요등', 'Paederia foetida', '꼭두서니과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (378, '고광나무', 'Philadelphus schrenkii', '수국과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (379, '고깔닭의장풀', 'Commelina benghalensis', 'Commelinaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (380, '고깔제비꽃', 'Viola rossii', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (381, '고려엉겅퀴', 'Cirsium setidens', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (382, '고로보이짚신나물', 'Agrimonia gorovoii', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (383, '고산물망초', 'Myosotis alpestris', '지치과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (384, '고삼', 'Sophora flavescens', '콩과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (385, '고수', 'Coriandrum sativum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (386, '고슴도치풀', 'Triumfetta japonica', 'Tiliaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (387, '고욤나무', 'Diospyros lotus', 'Ebenaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (388, '고추', 'Capsicum annuum', 'Solanaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (389, '고추나무', 'Staphylea bumalda', 'Staphyleaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (390, '고추나물', 'Hypericum erectum', 'Clusiaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (391, '고추냉이', 'Eutrema japonicum', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (392, '곡정초', 'Eriocaulon cinereum', 'Eriocaulaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (393, '곤달비', 'Ligularia stenocephala', '국화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (394, '곤약', 'Amorphophallus konjac', 'Araceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (395, '골담초', 'Caragana sinica', '콩과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (396, '골등골나물', 'Eupatorium lindleyanum', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (397, '골무꽃', 'Scutellaria indica', '꿀풀과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (398, '골병꽃나무', 'Weigela hortensis', '인동과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (399, '골잎원추리', 'Hemerocallis coreana', '백합과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (400, '곰딸기', 'Rubus phoenicolasius', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (401, '곰의말채나무', 'Cornus macrophylla', '층층나무과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (402, '곰취', 'Ligularia fischeri', '국화과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (403, '공단풀', 'Sida spinosa', '아욱과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (404, '공조팝나무', 'Spiraea cantoniensis', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (405, '과꽃', 'Callistephus chinensis', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (406, '곽향', 'Teucrium veronicoides', '꿀풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (407, '관동', 'Tussilago farfara', '국화과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (408, '관모개미자리', 'Eremogone capillaris', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (409, '광나무', 'Ligustrum japonicum', '물푸레나무과', '{7,8,9,10,11,12,1,2,3,4,5,6}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (410, '광릉골무꽃', 'Scutellaria insignis', '꿀풀과', '{5,6,7}', '5~7월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (411, '광릉요강꽃', 'Cypripedium japonicum', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (412, '괭이눈', 'Chrysosplenium grayanum', '수국과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (413, '괭이밥', 'Oxalis corniculata', 'Oxalidaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (414, '괭이싸리', 'Lespedeza pilosa', '콩과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (415, '괴불나무', 'Lonicera maackii', '인동과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (416, '괴불주머니', 'Corydalis pallida', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (417, '구골나무', 'Osmanthus heterophyllus', '물푸레나무과', '{9,10,11,12}', '9~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (418, '구기자나무', 'Lycium chinense', 'Solanaceae', '{4,5,6,7,8,9,10,11,12,1}', '4~1월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (419, '구름범의귀', 'Micranthes laciniata', '수국과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (420, '구름병아리난초', 'Hemipilia cucullata', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (421, '구름송이풀', 'Pedicularis verticillata', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (422, '구름제비꽃', 'Viola crassa', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (423, '구름제비란', 'Platanthera ophrydioides', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (424, '구릿대', 'Angelica dahurica', 'Apiaceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (425, '구슬갓냉이', 'Rorippa globosa', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (426, '구슬골무꽃', 'Scutellaria moniliorhiza', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (427, '구슬꽃나무', 'Adina rubella', '꼭두서니과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (428, '구슬다닥냉이', 'Neslia paniculata', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (429, '구슬붕이', 'Gentiana squarrosa', '용담과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (430, '구와말', 'Limnophila sessiliflora', '현삼과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (431, '구와취', 'Saussurea ussuriensis', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (432, '구주갈퀴덩굴', 'Vicia sepium', '콩과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (433, '국화마', 'Dioscorea septemloba', 'Dioscoreaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (434, '국화바람꽃', 'Anemone pseudoaltaica', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (435, '국화방망이', 'Tephroseris koreana', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (436, '국화잎가막사리', 'Bidens maximowicziana', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (437, '국화잎다닥냉이', 'Lepidium bonariense', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (438, '국화잎아욱', 'Modiola caroliniana', '아욱과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (439, '국화쥐손이', 'Erodium stephanianum', '쥐손이풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (440, '굴거리나무', 'Daphniphyllum macropodum', 'Daphniphyllaceae', '{5,6,7,8,9,10,11,12,1,2,3,4}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (441, '궁궁이', 'Angelica polymorpha', 'Apiaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (442, '귀룽나무', 'Prunus padus', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (443, '귀박쥐나물', 'Parasenecio auriculatus', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (444, '그늘꿩의다리', 'Thalictrum osmorhizoides', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (445, '그늘별꽃', 'Stellaria sessiliflora', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (446, '그늘보리뺑이', 'Lapsanastrum humile', '국화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (447, '그늘쑥', 'Artemisia sylvatica', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (448, '극동산마늘', 'Allium ochotense', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (449, '금강봄맞이', 'Androsace cortusifolia', '앵초과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (450, '금강분취', 'Saussurea diamantiaca', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (451, '금강애기나리', 'Streptopus ovalis', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (452, '금강인가목', 'Pentactina rupicola', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (453, '금강제비꽃', 'Viola diamantiaca', '제비꽃과', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (454, '금강초롱꽃', 'Hanabusaya asiatica', '초롱꽃과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (455, '금꿩의다리', 'Thalictrum rochebruneanum', '미나리아재비과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (456, '금난초', 'Cephalanthera falcata', 'Orchidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (457, '금떡쑥', 'Pseudognaphalium hypoleucum', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (458, '금마타리', 'Patrinia saniculifolia', '마타리과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (459, '금매화', 'Trollius ledebourii', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (460, '금방망이', 'Senecio nemorensis', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (461, '금불초', 'Inula japonica', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (462, '금붓꽃', 'Iris minutoaurea', '붓꽃과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (463, '금소리쟁이', 'Rumex maritimus', '마디풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (464, '금어초', 'Antirrhinum majus', '현삼과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (465, '금영화', 'Eschscholzia californica', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (466, '금잔화', 'Calendula arvensis', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (467, '금창초', 'Ajuga decumbens', '꿀풀과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (468, '기는괭이눈', 'Chrysosplenium epigealum', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (469, '기는미나리아재비', 'Ranunculus repens', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (470, '긴갓냉이', 'Sisymbrium orientale', '십자화과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (471, '긴개별꽃', 'Pseudostellaria japonica', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (472, '긴꼬리제비꽃', 'Viola inconspicua', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (473, '긴꽃며느리밥풀', 'Melampyrum koreanum', '현삼과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (474, '긴담배풀', 'Carpesium divaricatum', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (475, '긴뚝갈', 'Patrinia monandra', '마타리과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (476, '긴미꾸리낚시', 'Persicaria hastatosagittata', '마디풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (477, '긴병꽃풀', 'Glechoma longituba', '꿀풀과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (478, '긴사상자', 'Osmorhiza aristata', 'Apiaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (479, '긴잎갈퀴', 'Galium boreale', '꼭두서니과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (480, '긴잎곰취', 'Ligularia jaluensis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (481, '긴잎단풍딸기', 'Rubus palmatus', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (482, '긴잎달맞이꽃', 'Oenothera stricta', '바늘꽃과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (483, '긴잎별꽃', 'Stellaria longifolia', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (484, '긴잎여로', 'Veratrum maackii', '백합과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (485, '긴잎제비꽃', 'Viola ovato-oblonga', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (486, '긴잎조팝나무', 'Spiraea media', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (487, '긴털댕강나무', 'Zabelia densipila', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (488, '긴포마편초', 'Verbena bracteata', '마편초과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (489, '긴화살여뀌', 'Persicaria breviochreata', '마디풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (490, '길다닥냉이', 'Lepidium densiflorum', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (491, '길뚝개꽃', 'Anthemis arvensis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (492, '길마가지나무', 'Lonicera harae', '인동과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (493, '김의난초', 'Cephalanthera longifolia', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (494, '깃털장대', 'Sisymbrium irio', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (495, '까마귀머루', 'Vitis ficifolia', 'Vitaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (496, '까마귀베개', 'Rhamnella franguloides', 'Rhamnaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (497, '까마중', 'Solanum nigrum', 'Solanaceae', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (498, '까실쑥부쟁이', 'Aster ageratoides', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (499, '까실천인국', 'Gaillardia aristata', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (500, '까치고들빼기', 'Crepidiastrum chelidoniifolium', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (501, '까치발', 'Bidens parviflora', '국화과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (502, '까치밥나무', 'Ribes mandshuricum', '수국과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (503, '깔끔좁쌀풀', 'Euphrasia coreana', '현삼과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (504, '께묵', 'Hololeion maximowiczii', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (505, '꼬리까치밥나무', 'Ribes komarovii', '수국과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (506, '꼬리말발도리', 'Deutzia paniculata', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (507, '꼬리조팝나무', 'Spiraea salicifolia', '장미과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (508, '꼬리진달래', 'Rhododendron micranthum', '진달래과', '{2,3,4,5,6,7,8,9}', '2~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (509, '꼬마은난초', 'Cephalanthera subaphylla', 'Orchidaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (510, '꼭지연잎꿩의다리', 'Thalictrum ichangense', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (511, '꽃갈퀴덩굴', 'Sherardia arvensis', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (512, '꽃개오동', 'Catalpa bignonioides', '능소화과', '{1,2,3,4,5,6,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (513, '꽃꿩의다리', 'Thalictrum petaloideum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (514, '꽃냉이', 'Cardamine pratensis', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (515, '꽃다지', 'Draba nemorosa', '십자화과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (516, '꽃단풍', 'Acer pycnanthum', 'Aceraceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (517, '꽃대', 'Chloranthus serratus', 'Chloranthaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (518, '꽃받이', 'Bothriospermum zeylanicum', '지치과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (519, '꽃버들', 'Salix udensis', 'Salicaceae', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (520, '꽃벚나무', 'Prunus serrulata', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (521, '꽃상치', 'Cichorium endivia', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (522, '꽃생강', 'Hedychium coronarium', 'Zingiberaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (523, '꽃싸리', 'Campylotropis macrocarpa', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (524, '꽃아까시나무', 'Robinia hispida', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (525, '꽃쥐손이', 'Geranium platyanthum', '쥐손이풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (526, '꽃패랭이꽃', 'Dianthus superbus', '석죽과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (527, '꽃향유', 'Elsholtzia splendens', '꿀풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (528, '꽝꽝나무', 'Ilex crenata', 'Aquifoliaceae', '{7,8,9,10,11,12,1,2,3,4,5,6}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (529, '꿩의다리아재비', 'Caulophyllum robustum', 'Berberidaceae', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (530, '꿩의바람꽃', 'Anemone raddeana', '미나리아재비과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (531, '꿩의비름', 'Hylotelephium erythrostictum', '돌나물과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (532, '끈끈이여뀌', 'Persicaria viscofera', '마디풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (533, '끈끈이장구채', 'Silene koreana', '석죽과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (534, '끈끈이주걱', 'Drosera rotundifolia', 'Droseraceae', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (535, '끈적털갯개미자리', 'Spergularia bocconei', '석죽과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (536, '나나벌이난초', 'Liparis krameri', 'Orchidaceae', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (537, '나도갈퀴덩굴', 'Galium aparine', '꼭두서니과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (538, '나도공단풀', 'Sida rhombifolia', '아욱과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (539, '나도국수나무', 'Neillia uekii', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (540, '나도냉이', 'Barbarea orthoceras', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (541, '나도닭의덩굴', 'Fallopia convolvulus', '마디풀과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (542, '나도독미나리', 'Conium maculatum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (543, '나도민들레', 'Crepis tectorum', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (544, '나도바람꽃', 'Enemion raddeanum', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (545, '나도밤나무', 'Meliosma myriantha', 'Sabiaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (546, '나도생강', 'Pollia japonica', 'Commelinaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (547, '나도송이풀', 'Phtheirospermum japonicum', '현삼과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (548, '나도수영', 'Oxyria digyna', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (549, '나도수정초', 'Monotropastrum humile', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (550, '나도씨눈란', 'Herminium monorchis', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (551, '나도어저귀', 'Anoda cristata', '아욱과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (552, '나도여로', 'Anticlea sibirica', '백합과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (553, '나도옥잠화', 'Clintonia udensis', '백합과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (554, '나도잠자리란', 'Platanthera ussuriensis', 'Orchidaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (555, '나도재쑥', 'Descurainia pinnata', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (556, '나도제비란', 'Galearis cyclochila', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (557, '나도풍란', 'Phalaenopsis japonica', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (558, '나도하수오', 'Reynoutria ciliinervis', '마디풀과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (559, '나래가막사리', 'Verbesina alternifolia', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (560, '나래완두', 'Vicia anguste-pinnata', '콩과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (561, '나래쪽동백', 'Pterostyrax hispidus', 'Styracaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (562, '나래회나무', 'Euonymus macropterus', 'Celastraceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (563, '나리난초', 'Liparis makinoana', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (564, '나리잔대', 'Adenophora liliifolia', '초롱꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (565, '나비나물', 'Vicia unijuga', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (566, '나비잎유홍초', 'Ipomoea cristulata', '메꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (567, '나제승마', 'Actaea austrokoreana', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (568, '낙상홍', 'Ilex serrata', 'Aquifoliaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (569, '낙지다리', 'Penthorum chinense', 'Penthoraceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (570, '낚시돌풀', 'Leptopetalum coreanum', '꼭두서니과', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (571, '낚시제비꽃', 'Viola grypoceras', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (572, '난장이현호색', 'Corydalis humilis', '현호색과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (573, '난쟁이바위솔', 'Meterostachys sikokianus', '돌나물과', '{7,8,9}', '7~9월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (574, '난쟁이아욱', 'Malva neglecta', '아욱과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (575, '날개하늘나리', 'Lilium pensylvanicum', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (576, '남가새', 'Tribulus terrestris', 'Zygophyllaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (577, '남방향유', 'Elsholtzia griffithii', '꿀풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (578, '남오미자', 'Kadsura japonica', 'Schisandraceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (579, '남천', 'Nandina domestica', 'Berberidaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (580, '남포분취', 'Saussurea chinnampoensis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (581, '내버들', 'Salix gilgiana', 'Salicaceae', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (582, '냇씀바귀', 'Ixeris tamagawaensis', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (583, '냉초', 'Veronicastrum sibiricum', '현삼과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (584, '너도바람꽃', 'Eranthis stellata', '미나리아재비과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (585, '너도양지꽃', 'Sibbaldia procumbens', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (586, '너도제비란', 'Hemipilia joo-iokiana', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (587, '넌출월귤', 'Vaccinium oxycoccos', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (588, '넓은잎갈퀴', 'Vicia japonica', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (589, '넓은잎개수염', 'Eriocaulon alpestre', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (590, '넓은잎까치밥나무', 'Ribes latifolium', '수국과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (591, '넓은잎미꾸리낚시', 'Persicaria muricata', '마디풀과', '{5,6,7}', '5~7월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (592, '넓은잎범꼬리', 'Bistorta officinalis', '마디풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (593, '넓은잎외잎쑥', 'Artemisia stolonifera', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (594, '넓은잎잠자리란', 'Platanthera fuscescens', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (595, '넓은잎제비꽃', 'Viola mirabilis', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (596, '넓은잎쥐오줌풀', 'Valeriana dageletiana', '마타리과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (597, '넓은잎초오', 'Aconitum sczukinii', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (598, '넓은잎큰조롱', 'Cynanchum boudieri', 'Apocynaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (599, '넓은잔대', 'Adenophora divaricata', '초롱꽃과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (600, '네귀쓴풀', 'Swertia tetrapetala', '용담과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (601, '네마름', 'Trapa natans', 'Trapaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (602, '네잎갈퀴나물', 'Vicia nipponica', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (603, '노란꽃땅꽈리', 'Physalis angulata', 'Solanaceae', '{9,10,11}', '9~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (604, '노란장대', 'Sisymbrium luteum', '십자화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (605, '노란해당화', 'Rosa xanthina', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (606, '노랑갈퀴', 'Vicia chosenensis', '콩과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (607, '노랑개아마', 'Linum virginianum', 'Linaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (608, '노랑개자리', 'Medicago ruthenica', '콩과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (609, '노랑까마중', 'Solanum villosum', 'Solanaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (610, '노랑꽃누운땅꽈리', 'Physalis lagascae', 'Solanaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (611, '노랑도깨비바늘', 'Bidens polylepis', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (612, '노랑만병초', 'Rhododendron aureum', '진달래과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (613, '노랑무늬붓꽃', 'Iris odaesanensis', '붓꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (614, '노랑물봉선', 'Impatiens noli-tangere', '봉선화과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (615, '노랑복주머니란', 'Cypripedium calceolus', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (616, '노랑부추', 'Allium condensatum', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (617, '노랑붓꽃', 'Iris koreana', '붓꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (618, '노랑어리연꽃', 'Nymphoides peltata', 'Menyanthaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (619, '노랑제비꽃', 'Viola orientalis', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (620, '노랑제주무엽란', 'Lecanorchis suginoana', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (621, '노랑토끼풀', 'Trifolium campestre', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (622, '노랑투구꽃', 'Aconitum barbatum', '미나리아재비과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (623, '노루발', 'Pyrola japonica', 'Pyrolaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (624, '노루삼', 'Actaea asiatica', '미나리아재비과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (625, '노린재나무', 'Symplocos sawafutagi', 'Symplocaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (626, '노박덩굴', 'Celastrus orbiculatus', 'Celastraceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (627, '녹양박하', 'Mentha spicata', '꿀풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (628, '논냉이', 'Cardamine lyrata', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (629, '놋젓가락나물', 'Aconitum ciliare', '미나리아재비과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (630, '뇌성목', 'Lindera angustifolia', '녹나무과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (631, '누른괭이눈', 'Chrysosplenium flaviflorum', '수국과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (632, '누리장나무', 'Clerodendrum trichotomum', '마편초과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (633, '누린내풀', 'Tripora divaricata', '마편초과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (634, '누운닭의장풀', 'Commelina caroliniana', 'Commelinaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (635, '누운주름잎', 'Mazus miquelii', '현삼과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (636, '눈개불알풀', 'Veronica hederifolia', '현삼과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (637, '눈개승마', 'Aruncus dioicus', '장미과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (638, '눈갯쑥부쟁이', 'Aster hayatae', '국화과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (639, '눈괴불주머니', 'Corydalis ochotensis', '현호색과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (640, '눈까치밥나무', 'Ribes triste', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (641, '눈범꼬리', 'Bistorta suffulta', '마디풀과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (642, '눈빛승마', 'Actaea dahurica', '미나리아재비과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (643, '눈여뀌바늘', 'Ludwigia ovalis', '바늘꽃과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (644, '눈해변싸리', 'Lespedeza macrovirgata', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (645, '느러진장대', 'Catolobus pendulus', '십자화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (646, '는쟁이냉이', 'Cardamine komarovii', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (647, '능금나무', 'Malus asiatica', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (648, '늦둥굴레', 'Polygonatum infundiflorum', '백합과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (649, '다닥냉이', 'Lepidium apetalum', '십자화과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (650, '다도해비비추', 'Hosta jonesii', '백합과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (651, '다도해산들깨', 'Mosla dadoensis', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (652, '다래', 'Actinidia arguta', 'Actinidiaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (653, '다릅나무', 'Maackia amurensis', '콩과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (654, '다발골무꽃', 'Scutellaria asperiflora', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (655, '다북개미자리', 'Scleranthus annuus', '석죽과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (656, '다북떡쑥', 'Anaphalis sinica', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (657, '닥장버들', 'Salix brachypoda', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (658, '단풍나무', 'Acer palmatum', 'Aceraceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (659, '단풍마', 'Dioscorea quinquelobata', 'Dioscoreaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (660, '단풍박쥐나무', 'Alangium platanifolium', 'Alangiaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (661, '단풍잎돼지풀', 'Ambrosia trifida', '국화과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (662, '단풍잎복분자', 'Rubus chingii', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (663, '단풍취', 'Ainsliaea acerifolia', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (664, '달구지풀', 'Trifolium lupinaster', '콩과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (665, '달래', 'Allium monanthum', '백합과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (666, '닭의난초', 'Epipactis thunbergii', 'Orchidaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (667, '닭의덩굴', 'Fallopia dumetorum', '마디풀과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (668, '닭의장풀', 'Commelina communis', 'Commelinaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (669, '담배', 'Nicotiana tabacum', 'Solanaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (670, '담배풀', 'Carpesium abrotanoides', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (671, '담쟁이덩굴', 'Parthenocissus tricuspidata', 'Vitaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (672, '당개지치', 'Brachybotrys paridiformis', '지치과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (673, '당광나무', 'Ligustrum lucidum', '물푸레나무과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (674, '당남천죽', 'Mahonia fortunei', 'Berberidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (675, '당단풍나무', 'Acer pseudosieboldianum', 'Aceraceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (676, '당매자나무', 'Berberis chinensis', 'Berberidaceae', '{1,2,3,4,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (677, '당버들', 'Populus simonii', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (678, '당분취', 'Saussurea tanakae', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (679, '당삽주', 'Atractylodes koreana', '국화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (680, '당아욱', 'Malva sylvestris', '아욱과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (681, '당양지꽃', 'Potentilla ancistrifolia', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (682, '당잔대', 'Adenophora stricta', '초롱꽃과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (683, '당조팝나무', 'Spiraea chinensis', '장미과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (684, '당키버들', 'Salix miyabeana', 'Salicaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (685, '대구돌나물', 'Crassula aquatica', '돌나물과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (686, '대나물', 'Gypsophila oldhamiana', '석죽과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (687, '대마참나물', 'Tilingia tsusimensis', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (688, '대만뿔남천', 'Mahonia japonica', 'Berberidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (689, '대반하', 'Pinellia tripartita', 'Araceae', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (690, '대부도냉이', 'Lepidium perfoliatum', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (691, '대상화', 'Eriocapitella hupehensis', '미나리아재비과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (692, '대암개발나물', 'Sium tenue', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (693, '대청', 'Isatis tinctoria', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (694, '대청부채', 'Iris dichotoma', '붓꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (695, '대청지치', 'Thyrocarpus glochidiatus', '지치과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (696, '대추나무', 'Ziziphus jujuba', 'Rhamnaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (697, '대팻집나무', 'Ilex macropoda', 'Aquifoliaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (698, '대황', 'Rheum rhabarbarum', '마디풀과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (699, '대흥란', 'Cymbidium macrorhizon', 'Orchidaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (700, '댕강나무', 'Zabelia tyaihyonii', '인동과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (701, '댕댕이나무', 'Lonicera caerulea', '인동과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (702, '댕댕이덩굴', 'Cocculus orbiculatus', 'Menispermaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (703, '더덕', 'Codonopsis lanceolata', '초롱꽃과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (704, '덕우기름나물', 'Sillaphyton podagraria', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (705, '덜꿩나무', 'Viburnum erosum', '인동과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (706, '덤불꼭두서니', 'Rubia sylvatica', '꼭두서니과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (707, '덤불쑥', 'Artemisia rubripes', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (708, '덤불조팝나무', 'Spiraea miyabei', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (709, '덤불취', 'Saussurea manshurica', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (710, '덩굴개별꽃', 'Pseudostellaria davidii', '석죽과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (711, '덩굴꽃마리', 'Trigonotis icumae', '지치과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (712, '덩굴닭의장풀', 'Streptolirion volubile', 'Commelinaceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (713, '덩굴모밀', 'Persicaria chinensis', '마디풀과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (714, '덩굴민백미꽃', 'Vincetoxicum japonicum', 'Apocynaceae', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (715, '덩굴박주가리', 'Vincetoxicum nipponicum', 'Apocynaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (716, '덩굴옻나무', 'Toxicodendron orientale', 'Anacardiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (717, '덩굴조희풀', 'Clematis pseudotubulosa', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (718, '덩굴해란초', 'Cymbalaria muralis', '현삼과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (719, '덩이괭이밥', 'Oxalis articulata', 'Oxalidaceae', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (720, '도깨비가지', 'Solanum carolinense', 'Solanaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (721, '도깨비바늘', 'Bidens bipinnata', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (722, '도깨비부채', 'Rodgersia podophylla', '수국과', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (723, '도깨비엉겅퀴', 'Cirsium schantarense', '국화과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (724, '도꼬로마', 'Dioscorea tokoro', 'Dioscoreaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (725, '도꼬마리', 'Xanthium strumarium', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (726, '도라지모시대', 'Adenophora grandiflora', '초롱꽃과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (727, '독말풀', 'Datura stramonium', 'Solanaceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (728, '독미나리', 'Cicuta virosa', 'Apiaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (729, '돈나무', 'Pittosporum tobira', 'Pittosporaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (730, '돌갈매나무', 'Rhamnus parvifolia', 'Rhamnaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (731, '돌단풍', 'Mukdenia rossii', '수국과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (732, '돌방풍', 'Carlesia sinensis', 'Apiaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (733, '돌부채', 'Bergenia crassifolia', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (734, '돌부추', 'Allium koreanum', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (735, '돌소리쟁이', 'Rumex obtusifolius', '마디풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (736, '돌앵초', 'Primula saxatilis', '앵초과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (737, '돌양지꽃', 'Potentilla dickinsii', '장미과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (738, '돌외', 'Gynostemma pentaphyllum', 'Cucurbitaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (739, '돌채송화', 'Sedum japonicum', '돌나물과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (740, '동강할미꽃', 'Pulsatilla tongkangensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (741, '동의나물', 'Caltha palustris', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (742, '돼지풀', 'Ambrosia artemisiifolia', '국화과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (743, '돼지풀아재비', 'Parthenium hysterophorus', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (744, '된장풀', 'Ohwia caudata', '콩과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (745, '두루미꽃', 'Maianthemum bifolium', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (746, '두루미천남성', 'Arisaema heterophyllum', 'Araceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (747, '두릅나무', 'Aralia elata', 'Araliaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (748, '두메고들빼기', 'Lactuca triangulata', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (749, '두메꿀풀', 'Prunella vulgaris', '꿀풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (750, '두메냉이', 'Cardamine changbaiana', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (751, '두메담배풀', 'Carpesium triste', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (752, '두메부추', 'Allium dumebuchum', '백합과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (753, '두메분취', 'Saussurea tomentosa', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (754, '두메애기풀', 'Polygala sibirica', 'Polygalaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (755, '두메양귀비', 'Papaver coreanum', '현호색과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (756, '두메오이풀', 'Sanguisorba obtusa', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (757, '두메자운', 'Oxytropis anertii', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (758, '두메잔대', 'Adenophora lamarckii', '초롱꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (759, '두메취', 'Saussurea triangulata', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (760, '두메층층이', 'Clinopodium micranthum', '꿀풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (761, '두잎약난초', 'Cremastra unguiculata', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (762, '두충', 'Eucommia ulmoides', 'Eucommiaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (763, '둥근마', 'Dioscorea bulbifera', 'Dioscoreaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (764, '둥근말발도리', 'Deutzia scabra', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (765, '둥근매듭풀', 'Kummerowia stipulacea', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (766, '둥근바위솔', 'Orostachys malacophylla', '돌나물과', '{9,10,11,12}', '9~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (767, '둥근배암차즈기', 'Salvia japonica', '꿀풀과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (768, '둥근빗살괴불주머니', 'Fumaria officinalis', '현호색과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (769, '둥근이질풀', 'Geranium koreanum', '쥐손이풀과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (770, '둥근인가목', 'Rosa spinosissima', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (771, '둥근잎개야광', 'Cotoneaster integerrimus', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (772, '둥근잎꿩의비름', 'Hylotelephium ussuriense', '돌나물과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (773, '둥근잎나팔꽃', 'Ipomoea purpurea', '메꽃과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (774, '둥근잎비름', 'Sedum makinoi', '돌나물과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (775, '둥근잎아욱', 'Malva pusilla', '아욱과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (776, '둥근잎유홍초', 'Ipomoea coccinea', '메꽃과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (777, '둥근잎천남성', 'Arisaema amurense', 'Araceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (778, '둥근잎택사', 'Caldesia parnassifolia', 'Alismataceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (779, '둥근털제비꽃', 'Viola collina', '제비꽃과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (780, '드람불꽃', 'Phlox drummondii', '꽃고비과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (781, '들갓', 'Sinapis arvensis', '십자화과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (782, '들개미자리', 'Spergula arvensis', '석죽과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (783, '들괭이밥', 'Oxalis dillenii', 'Oxalidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (784, '들깨', 'Perilla frutescens', '꿀풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (785, '들깨풀', 'Mosla scabra', '꿀풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (786, '들다닥냉이', 'Lepidium campestre', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (787, '들떡쑥', 'Leontopodium leontopodioides', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (788, '들메나무', 'Fraxinus mandshurica', '물푸레나무과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (789, '들바람꽃', 'Anemone amurensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (790, '들버들', 'Salix subopposita', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (791, '들벌노랑이', 'Lotus pedunculatus', '콩과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (792, '들별꽃', 'Stellaria ruderalis', '석죽과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (793, '들사마귀풀', 'Murdannia nudiflora', 'Commelinaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (794, '들완두', 'Vicia bungei', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (795, '들정향나무', 'Syringa reticulata', '물푸레나무과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (796, '들쭉나무', 'Vaccinium uliginosum', '진달래과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (797, '들통발', 'Utricularia aurea', 'Lentibulariaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (798, '들현호색', 'Corydalis ternata', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (799, '등갈퀴나물', 'Vicia cracca', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (800, '등골나물아재비', 'Ageratum conyzoides', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (801, '등대꽃나무', 'Enkianthus campanulatus', '진달래과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (802, '등대시호', 'Bupleurum euphorbioides', 'Apiaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (803, '등수국', 'Hydrangea petiolaris', '수국과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (804, '등에풀', 'Dopatrium junceum', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (805, '등포풀', 'Limosella aquatica', '현삼과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (806, '딱지꽃', 'Potentilla chinensis', '장미과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (807, '딱총나무', 'Sambucus williamsii', '인동과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (808, '땃두릅나무', 'Oplopanax elatus', 'Araliaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (809, '땅귀개', 'Utricularia bifida', 'Lentibulariaceae', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (810, '땅꽈리', 'Physalis pubescens', 'Solanaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (811, '땅나리', 'Lilium callosum', '백합과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (812, '땅두릅', 'Aralia cordata', 'Araliaceae', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (813, '땅비수리', 'Lespedeza juncea', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (814, '땅비싸리', 'Indigofera kirilowii', '콩과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (815, '때죽나무', 'Styrax japonicus', 'Styracaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (816, '떡쑥', 'Pseudognaphalium affine', '국화과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (817, '뚜껑덩굴', 'Actinostemma lobatum', 'Cucurbitaceae', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (818, '뚱딴지', 'Helianthus tuberosus', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (819, '뜰보리수', 'Elaeagnus multiflora', 'Elaeagnaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (820, '라일락', 'Syringa vulgaris', '물푸레나무과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (821, '라즈베리', 'Rubus idaeus', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (822, '로젯사철란', 'Goodyera brachystegia', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (823, '린네풀', 'Linnaea borealis', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (824, '마', 'Dioscorea polystachya', 'Dioscoreaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (825, '마가렛트', 'Argyranthemum frutescens', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (826, '마늘', 'Allium sativum', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (827, '마늘냉이', 'Alliaria petiolata', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (828, '마디꽃', 'Rotala indica', '부처꽃과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (829, '마디풀', 'Polygonum aviculare', '마디풀과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (830, '마삭줄', 'Trachelospermum asiaticum', 'Apocynaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (831, '마편초', 'Verbena officinalis', '마편초과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (832, '만년청', 'Rohdea japonica', 'Commelinaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (833, '만리화', 'Forsythia ovata', '물푸레나무과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (834, '만병초', 'Rhododendron brachycarpum', '진달래과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (835, '만삼', 'Codonopsis pilosula', '초롱꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (836, '만수국아재비', 'Tagetes minuta', '국화과', '{9,10,11,12}', '9~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (837, '만주겨이삭여뀌', 'Persicaria foliosa', '마디풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (838, '만주고로쇠', 'Acer truncatum', 'Aceraceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (839, '만주미나리아재비', 'Ranunculus grandis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (840, '만주바람꽃', 'Isopyrum manshuricum', '미나리아재비과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (841, '만주붓꽃', 'Iris mandshurica', '붓꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (842, '만주송이풀', 'Pedicularis mandshurica', '현삼과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (843, '만주잔대', 'Adenophora pereskiifolia', '초롱꽃과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (844, '만첩조팝나무', 'Spiraea prunifolia', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (845, '말나리', 'Lilium distichum', '백합과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (846, '말냉이', 'Thlaspi arvense', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (847, '말냉이장구채', 'Silene noctiflora', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (848, '말똥비름', 'Sedum bulbiferum', '돌나물과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (849, '말발도리', 'Deutzia parviflora', '수국과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (850, '말뱅이나물', 'Gypsophila vaccaria', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (851, '말채나무', 'Cornus walteri', '층층나무과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (852, '맑은대쑥', 'Artemisia keiskeana', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (853, '망개나무', 'Berchemiella berchemiifolia', 'Rhamnaceae', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (854, '망적천문동', 'Asparagus dauricus', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (855, '매듭풀', 'Kummerowia striata', '콩과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (856, '매발톱나무', 'Berberis amurensis', 'Berberidaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (857, '매자나무', 'Berberis koreana', 'Berberidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (858, '매자잎버들', 'Salix berberifolia', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (859, '매화노루발', 'Chimaphila japonica', 'Pyrolaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (860, '매화마름', 'Ranunculus kadzusensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (861, '매화말발도리', 'Deutzia uniflora', '수국과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (862, '매화오리나무', 'Clethra barbinervis', 'Clethraceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (863, '맥문동', 'Liriope muscari', '백합과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (864, '맥문아재비', 'Ophiopogon jaburan', '백합과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (865, '머귀나무', 'Zanthoxylum ailanthoides', 'Rutaceae', '{1,2,3,4,5,6,7,8,9,10,11}', '1~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (866, '머루', 'Vitis coignetiae', 'Vitaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (867, '머위', 'Petasites japonicus', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (868, '먹넌출', 'Berchemia floribunda', 'Rhamnaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (869, '먼나무', 'Ilex rotunda', 'Aquifoliaceae', '{1,2,3,4,5,6,7,8,9,10,11}', '1~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (870, '멀구슬나무', 'Melia azedarach', 'Meliaceae', '{6,7,8,9,10,11,12,1,2,3,4,5}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (871, '멀꿀', 'Stauntonia hexaphylla', 'Lardizabalaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (872, '멍석딸기', 'Rubus parvifolius', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (873, '메밀', 'Fagopyrum esculentum', '마디풀과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (874, '메밀여뀌', 'Persicaria capitata', '마디풀과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (875, '멕시코돌나물', 'Sedum mexicanum', '돌나물과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (876, '멕시코백령풀', 'Richardia brasiliensis', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (877, '며느리밑씻개', 'Persicaria senticosa', '마디풀과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (878, '며느리배꼽', 'Persicaria perfoliata', '마디풀과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (879, '멸가치', 'Adenocaulon himalaicum', '국화과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (880, '명자순', 'Ribes maximoviczianum', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (881, '모래냉이', 'Diplotaxis muralis', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (882, '모새나무', 'Vaccinium bracteatum', '진달래과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (883, '모시대', 'Adenophora remotiflora', '초롱꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (884, '목서', 'Osmanthus fragrans', '물푸레나무과', '{9,10,11,12}', '9~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (885, '목포용둥굴레', 'Polygonatum cryptanthum', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (886, '목향', 'Inula helenium', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (887, '뫼제비꽃', 'Viola selkirkii', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (888, '묏꿩의다리', 'Thalictrum sachalinense', '미나리아재비과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (889, '묏미나리', 'Ostericum sieboldii', 'Apiaceae', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (890, '묏장대', 'Arabidopsis lyrata', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (891, '묏황기', 'Hedysarum alpinum', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (892, '무늬천남성', 'Arisaema thunbergii', 'Araceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (893, '무산상자', 'Sphallerocarpus gracilis', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (894, '무엽란', 'Lecanorchis japonica', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (895, '무환자나무', 'Sapindus mukorossi', '무환자나무과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (896, '묵밭소리쟁이', 'Rumex conglomeratus', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (897, '문모초', 'Veronica peregrina', '현삼과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (898, '물고추나물', 'Triadenum japonicum', 'Clusiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (899, '물까치수염', 'Lysimachia leucantha', '앵초과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (900, '물꼬리풀', 'Pogostemon stellatus', '꿀풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (901, '물꽈리아재비', 'Erythranthe nepalensis', '현삼과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (902, '물들메나무', 'Fraxinus chiisanensis', '물푸레나무과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (903, '물레나물', 'Hypericum ascyron', 'Clusiaceae', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (904, '물매화', 'Parnassia palustris', '수국과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (905, '물머위', 'Adenostemma lavenia', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (906, '물상추', 'Pistia stratiotes', 'Araceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (907, '물솜방망이', 'Tephroseris pseudosonchus', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (908, '물싸리', 'Dasiphora fruticosa', '장미과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (909, '물쑥', 'Artemisia selengensis', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (910, '물앵도나무', 'Lonicera ruprechtiana', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (911, '물양귀비', 'Hydrocleys nymphoides', 'Alismataceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (912, '물양지꽃', 'Potentilla cryptotaeniae', '장미과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (913, '물엉겅퀴', 'Cirsium nipponicum', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (914, '물지채', 'Triglochin palustris', 'Juncaginaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (915, '물참대', 'Deutzia glabrata', '수국과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (916, '물칭개나물', 'Veronica undulata', '현삼과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (917, '미국갯마디풀', 'Polygonum ramosissimum', '마디풀과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (918, '미국까마중', 'Solanum americanum', 'Solanaceae', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (919, '미국꽃말이', 'Amsinckia lycopsoides', '지치과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (920, '미국나팔꽃', 'Ipomoea hederacea', '메꽃과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (921, '미국낙상홍', 'Ilex verticillata', 'Aquifoliaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (922, '미국담쟁이덩굴', 'Parthenocissus quinquefolia', 'Vitaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (923, '미국마편초', 'Verbena hastata', '마편초과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (924, '미국물칭개', 'Veronica americana', '현삼과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (925, '미국미역취', 'Solidago gigantea', '국화과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (926, '미국산사', 'Crataegus scabrida', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (927, '미국수국', 'Hydrangea arborescens', '수국과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (928, '미국수련', 'Nymphaea odorata', '수련과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (929, '미국실새삼', 'Cuscuta campestris', '메꽃과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (930, '미국외풀', 'Lindernia dubia', '현삼과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (931, '미국자리공', 'Phytolacca americana', 'Phytolaccaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (932, '미국잔디갈고리', 'Desmodium paniculatum', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (933, '미국좀부처꽃', 'Ammannia coccinea', '부처꽃과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (934, '미국쥐손이', 'Geranium carolinianum', '쥐손이풀과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (935, '미국큰고추풀', 'Gratiola neglecta', '현삼과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (936, '미국풀솜나물', 'Gamochaeta pensylvanica', '국화과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (937, '미꾸리낚시', 'Persicaria sagittata', '마디풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (938, '미나리', 'Oenanthe javanica', 'Apiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (939, '미나리냉이', 'Cardamine leucantha', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (940, '미나리아재비', 'Ranunculus japonicus', '미나리아재비과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (941, '미모사', 'Mimosa pudica', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (942, '미선나무', 'Abeliophyllum distichum', '물푸레나무과', '{2,3,4}', '2~4월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (943, '미역취아재비', 'Euthamia graminifolia', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (944, '민개미자리', 'Sagina procumbens', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (945, '민둥갈퀴', 'Galium kinuta', '꼭두서니과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (946, '민둥갈퀴덩굴', 'Galium tricornutum', '꼭두서니과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (947, '민말똥비름', 'Sedum alfredii', '돌나물과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (948, '민망초', 'Erigeron acris', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (949, '민생열귀나무', 'Rosa silenidiflora', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (950, '민솜대', 'Maianthemum dahuricum', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (951, '민쑥부쟁이', 'Aster mongolicus', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (952, '밀나물', 'Smilax riparia', '백합과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (953, '바늘꽃', 'Epilobium pyrricholophum', '바늘꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (954, '바늘엉겅퀴', 'Cirsium rhinoceros', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (955, '바보여뀌', 'Persicaria pubescens', '마디풀과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (956, '바위괭이눈', 'Chrysosplenium macrostemon', '수국과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (957, '바위댕강나무', 'Zabelia integrifolia', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (958, '바위돌꽃', 'Rhodiola rosea', '돌나물과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (959, '바위미나리아재비', 'Ranunculus crucilobus', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (960, '바위솔', 'Orostachys japonica', '돌나물과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (961, '바위솜나물', 'Tephroseris phaeantha', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (962, '바위장대', 'Arabis serrata', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (963, '바위채송화', 'Sedum polytrichoides', '돌나물과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (964, '바위취', 'Saxifraga stolonifera', '수국과', '{2,3,4,5,6,7}', '2~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (965, '바이칼꿩의다리', 'Thalictrum baicalense', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (966, '박', 'Lagenaria siceraria', 'Cucurbitaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (967, '박달목서', 'Osmanthus insularis', '물푸레나무과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (968, '박새', 'Veratrum oxysepalum', '백합과', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (969, '박태기나무', 'Cercis chinensis', '콩과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (970, '반디미나리', 'Pternopetalum tanakae', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (971, '반짝버들', 'Salix pseudopentandra', 'Salicaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (972, '발톱꿩의다리', 'Thalictrum sparsiflorum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (973, '방기', 'Sinomenium acutum', 'Menispermaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (974, '방울꽃', 'Strobilanthes oligantha', 'Acanthaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (975, '방울비짜루', 'Asparagus oligoclonos', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (976, '방울새란', 'Pogonia minor', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (977, '방울제비꽃', 'Viola breviflora', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (978, '방풍', 'Saposhnikovia divaricata', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (979, '배암나무', 'Viburnum koreanum', '인동과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (980, '배암차즈기', 'Salvia plebeia', '꿀풀과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (981, '배풍등', 'Solanum lyratum', 'Solanaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (982, '백두산떡쑥', 'Antennaria dioica', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (983, '백량금', 'Ardisia crispa', 'Myrsinaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (984, '백리향', 'Thymus quinquecostatus', '꿀풀과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (985, '백미꽃', 'Vincetoxicum atratum', 'Apocynaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (986, '백부자', 'Aconitum coreanum', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (987, '백서향', 'Daphne kiusiana', 'Thymelaeaceae', '{12,1,2,3,4,5,6}', '12~6월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (988, '백선', 'Dictamnus dasycarpus', 'Rutaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (989, '백약이참나물', 'Pimpinella saxifraga', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (990, '백운란', 'Odontochilus nakaianus', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (991, '백운산원추리', 'Hemerocallis hakuunensis', '백합과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (992, '백운취', 'Saussurea insularis', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (993, '백합나무', 'Liriodendron tulipifera', '목련과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (994, '뱀무', 'Geum japonicum', '장미과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (995, '버드나무', 'Salix pierotii', 'Salicaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (996, '버들까치수염', 'Lysimachia thyrsiflora', '앵초과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (997, '버들바늘꽃', 'Epilobium palustre', '바늘꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (998, '버들분취', 'Saussurea maximowiczii', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (999, '버들잎엉겅퀴', 'Cirsium lineare', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1000, '버들쥐똥나무', 'Ligustrum salicinum', '물푸레나무과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1001, '버들취', 'Saussurea amurensis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1002, '버어먼초', 'Burmannia cryptopetala', 'Burmanniaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1003, '버즘나무', 'Platanus orientalis', 'Platanaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1004, '번행초', 'Tetragonia tetragonoides', '석류풀과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1005, '벋음씀바귀', 'Ixeris japonica', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1006, '벌깨덩굴', 'Meehania urticifolia', '꿀풀과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1007, '벌깨풀', 'Dracocephalum rupestre', '꿀풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1008, '벌등골나물', 'Eupatorium japonicum', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1009, '벌사상자', 'Cnidium monnieri', 'Apiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1010, '벌씀바귀', 'Ixeris polycephala', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1011, '벌완두', 'Vicia amurensis', '콩과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1012, '범부채', 'Iris domestica', '붓꽃과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1013, '벗풀', 'Sagittaria trifolia', 'Alismataceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1014, '벳지', 'Vicia villosa', '콩과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1015, '벼룩나물', 'Stellaria alsine', '석죽과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1016, '벼룩이울타리', 'Eremogone juncea', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1017, '벼룩이자리', 'Arenaria serpyllifolia', '석죽과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1018, '벽오동나무', 'Firmiana simplex', 'Sterculiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1019, '별꽃아재비', 'Galinsoga parviflora', '국화과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1020, '별꽃풀', 'Swertia veratroides', '용담과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1021, '별나팔꽃', 'Ipomoea triloba', '메꽃과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1022, '병아리꽃나무', 'Rhodotypos scandens', '장미과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1023, '병아리난초', 'Hemipilia gracilis', 'Orchidaceae', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1024, '병아리다리', 'Salomonia ciliata', 'Polygalaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1025, '병아리풀', 'Polygala tatarinowii', 'Polygalaceae', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1026, '병풀', 'Centella asiatica', 'Apiaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1027, '병풀아재비', 'Bowlesia incana', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1028, '보리밥나무', 'Elaeagnus macrophylla', 'Elaeagnaceae', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1029, '보리수나무', 'Elaeagnus umbellata', 'Elaeagnaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1030, '보리장나무', 'Elaeagnus glabra', 'Elaeagnaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1031, '보춘화', 'Cymbidium goeringii', 'Orchidaceae', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1032, '보풀', 'Sagittaria aginashi', 'Alismataceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1033, '복분자딸기', 'Rubus coreanus', '장미과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1034, '복자기', 'Acer triflorum', 'Aceraceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1035, '복장나무', 'Acer mandshuricum', 'Aceraceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1036, '복주머니란', 'Cypripedium macranthos', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1037, '봄구슬붕이', 'Gentiana thunbergii', '용담과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1038, '봄나도냉이', 'Barbarea verna', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1039, '봄망초', 'Erigeron philadelphicus', '국화과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1040, '봄맞이냉이', 'Cardamine hirsuta', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1041, '봄여뀌', 'Persicaria maculosa', '마디풀과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1042, '봉화현호색', 'Corydalis bonghwaensis', '현호색과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1043, '부게꽃나무', 'Acer ukurunduense', 'Aceraceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1044, '부들레야 다비디', 'Buddleja davidii', 'Loganiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1045, '부령소리쟁이', 'Rumex patientia', '마디풀과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1046, '부산마디풀', 'Polygonum humifusum', '마디풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1047, '부전바디', 'Angelica nakaiana', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1048, '부지깽이나물', 'Erysimum amurense', '십자화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1049, '부채마', 'Dioscorea nipponica', 'Dioscoreaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1050, '부채붓꽃', 'Iris setosa', '붓꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1051, '북금매화', 'Trollius chinensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1052, '북만리화', 'Forsythia mandschurica', '물푸레나무과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1053, '북방산비장이', 'Serratula coronata', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1054, '북범꼬리', 'Bistorta manshuriensis', '마디풀과', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1055, '북분취', 'Saussurea mongolica', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1056, '북점나도나물', 'Cerastium holosteoides', '석죽과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1057, '북투구꽃', 'Aconitum kirinense', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1058, '분꽃', 'Mirabilis jalapa', 'Nyctaginaceae', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1059, '분꽃나무', 'Viburnum carlesii', '인동과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1060, '분단나무', 'Viburnum furcatum', '인동과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1061, '분버들', 'Salix rorida', 'Salicaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1062, '분취', 'Saussurea seoulensis', '국화과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1063, '분홍꽃조개나물', 'Ajuga nipponensis', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1064, '분홍낮달맞이꽃', 'Oenothera speciosa', '바늘꽃과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1065, '분홍싸리', 'Lespedeza floribunda', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1066, '분홍장구채', 'Silene capitata', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1067, '분홍쥐손이', 'Geranium maximowiczii', '쥐손이풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1068, '분홍할미꽃', 'Pulsatilla dahurica', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1069, '불란서국화', 'Leucanthemum vulgare', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1070, '불암초', 'Melochia corchorifolia', 'Sterculiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1071, '붉나무', 'Rhus chinensis', 'Anacardiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1072, '붉은노루삼', 'Actaea rubra', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1073, '붉은병꽃나무', 'Weigela florida', '인동과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1074, '붉은사철란', 'Goodyera biflora', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1075, '붉은서나물', 'Erechtites hieraciifolius', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1076, '붉은정향나무', 'Syringa villosa', '물푸레나무과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1077, '붉은참반디', 'Sanicula rubriflora', 'Apiaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1078, '붉은털이슬', 'Circaea erubescens', '바늘꽃과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1079, '붉은하늘타리', 'Trichosanthes cucumeroides', 'Cucurbitaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1080, '붓순나무', 'Illicium anisatum', 'Illiciaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1081, '브라질마편초', 'Verbena brasiliensis', '마편초과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1082, '비너스도라지', 'Triodanis perfoliata', '초롱꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1083, '비누풀', 'Saponaria officinalis', '석죽과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1084, '비단분취', 'Saussurea komaroviana', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1085, '비로용담', 'Gentiana jamesii', '용담과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1086, '비목나무', 'Lindera erythrocarpa', '녹나무과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1087, '비비추난초', 'Tipularia japonica', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1088, '비수리', 'Lespedeza cuneata', '콩과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1089, '비쑥', 'Artemisia scoparia', '국화과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1090, '비진도콩', 'Dumasia truncata', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1091, '비짜루', 'Asparagus schoberioides', '백합과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1092, '비짜루국화', 'Symphyotrichum subulatum', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1093, '비쭈기나무', 'Cleyera japonica', '차나무과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1094, '빈도리', 'Deutzia crenata', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1095, '빈추나무', 'Prinsepia sinensis', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1096, '빈카', 'Vinca minor', '꼭두서니과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1097, '뺑쑥', 'Artemisia lancea', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1098, '뻐꾹나리', 'Tricyrtis macropoda', '백합과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1099, '뻐꾹채', 'Leuzea uniflora', '국화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1100, '뿔냉이', 'Chorispora tenella', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1101, '사국이질풀', 'Geranium shikokianum', '쥐손이풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1102, '사데풀', 'Sonchus brachyotus', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1103, '사리풀', 'Hyoscyamus niger', 'Solanaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1104, '사마귀풀', 'Murdannia keisak', 'Commelinaceae', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1105, '사막갓', 'Brassica tournefortii', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1106, '사상자', 'Torilis japonica', 'Apiaceae', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1107, '사스레피나무', 'Eurya japonica', '차나무과', '{1,2,3,4,5,6,7,8,9,10,11}', '1~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1108, '사창분취', 'Saussurea calcicola', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1109, '사철나무', 'Euonymus japonicus', 'Celastraceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1110, '사철쑥', 'Artemisia capillaris', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1111, '사향엉겅퀴', 'Carduus nutans', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1112, '사향제비꽃', 'Viola obtusa', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1113, '산가막살나무', 'Viburnum wrightii', '인동과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1114, '산각시취', 'Saussurea umbrosa', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1115, '산개갈퀴', 'Galium platygalium', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1116, '산개벚지나무', 'Prunus maximowiczii', '장미과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1117, '산검양옻나무', 'Toxicodendron sylvestre', 'Anacardiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1118, '산겨릅나무', 'Acer tegmentosum', 'Aceraceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1119, '산골취', 'Saussurea neoserrata', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1120, '산괭이눈', 'Chrysosplenium japonicum', '수국과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1121, '산괴불주머니', 'Corydalis speciosa', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1122, '산국수나무', 'Physocarpus amurensis', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1123, '산궁궁이', 'Conioselinum smithii', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1124, '산꽃다지', 'Draba glabella', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1125, '산꿩의다리', 'Thalictrum tuberiferum', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1126, '산닥나무', 'Wikstroemia trichotoma', 'Thymelaeaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1127, '산달래', 'Allium macrostemon', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1128, '산당근', 'Daucus carota', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1129, '산돌배', 'Pyrus ussuriensis', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1130, '산동쥐똥나무', 'Ligustrum leucanthum', '물푸레나무과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1131, '산들깨', 'Mosla japonica', '꿀풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1132, '산딸기', 'Rubus crataegifolius', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1133, '산떡쑥', 'Anaphalis margaritacea', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1134, '산마늘', 'Allium microdictyon', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1135, '산매자나무', 'Vaccinium japonicum', '진달래과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1136, '산물머위', 'Adenostemma madurense', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1137, '산민들레', 'Taraxacum ussuriense', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1138, '산박하', 'Isodon inflexus', '꿀풀과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1139, '산방백운풀', 'Oldenlandia corymbosa', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1140, '산버들', 'Salix taraikensis', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1141, '산벚나무', 'Prunus sargentii', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1142, '산복사나무', 'Prunus davidiana', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1143, '산부채', 'Calla palustris', 'Araceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1144, '산분꽃나무', 'Viburnum burejaeticum', '인동과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1145, '산사나무', 'Crataegus pinnatifida', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1146, '산새콩', 'Lathyrus vaniotii', '콩과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1147, '산솜방망이', 'Tephroseris flammea', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1148, '산쉽싸리', 'Lycopus charkeviczii', '꿀풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1149, '산쑥', 'Artemisia montana', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1150, '산쑥부쟁이', 'Aster lautureanus', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1151, '산씀바귀', 'Lactuca raddeana', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1152, '산여뀌', 'Persicaria nepalensis', '마디풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1153, '산오이풀', 'Sanguisorba hakusanensis', '장미과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1154, '산옥매', 'Prunus glandulosa', '장미과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1155, '산외', 'Schizopepon bryoniifolius', 'Cucurbitaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1156, '산용담', 'Gentiana algida', '용담과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1157, '산이질풀', 'Geranium nepalense', '쥐손이풀과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1158, '산작약', 'Paeonia obovata', '작약과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1159, '산조팝나무', 'Spiraea blumei', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1160, '산쥐손이', 'Geranium dahuricum', '쥐손이풀과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1161, '산지치', 'Eritrichium sichotense', '지치과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1162, '산진달래', 'Rhododendron dauricum', '진달래과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1163, '산짚신나물', 'Agrimonia coreana', '장미과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1164, '산초나무', 'Zanthoxylum schinifolium', 'Rutaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1165, '산토끼꽃', 'Dipsacus japonicus', 'Dipsacaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1166, '산파', 'Allium maximowiczii', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1167, '산할미꽃', 'Pulsatilla nivalis', '미나리아재비과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1168, '산형나도별꽃', 'Holosteum umbellatum', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1169, '산호수', 'Ardisia pusilla', 'Myrsinaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1170, '산흰쑥', 'Artemisia sieversiana', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1171, '삼도하수오', 'Fallopia koreana', '마디풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1172, '삼백초', 'Saururus chinensis', 'Saururaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1173, '삼색제비꽃', 'Viola tricolor', '제비꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1174, '삼수구릿대', 'Angelica anomala', 'Apiaceae', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1175, '삼쥐손이', 'Geranium soboliferum', '쥐손이풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1176, '삼지구엽초', 'Epimedium koreanum', 'Berberidaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1177, '삼지닥나무', 'Edgeworthia chrysantha', 'Thymelaeaceae', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1178, '삿갓나물', 'Paris verticillata', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1179, '상동나무', 'Sageretia thea', 'Rhamnaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1180, '상동잎쥐똥나무', 'Ligustrum quihoui', '물푸레나무과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1181, '상산', 'Orixa japonica', 'Rutaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1182, '상치아재비', 'Valerianella locusta', '마타리과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1183, '새끼꿩의비름', 'Hylotelephium viviparum', '돌나물과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1184, '새끼노루발', 'Orthilia secunda', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1185, '새덕이', 'Neolitsea aciculata', '녹나무과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1186, '새등골나물', 'Eupatorium fortunei', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1187, '새머루', 'Vitis flexuosa', 'Vitaceae', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1188, '새모래덩굴', 'Menispermum dauricum', 'Menispermaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1189, '새비나무', 'Callicarpa mollis', '마편초과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1190, '새삼', 'Cuscuta japonica', '메꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1191, '새완두', 'Vicia hirsuta', '콩과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1192, '새우난초', 'Calanthe discolor', 'Orchidaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1193, '새이삭여뀌', 'Persicaria neofiliformis', '마디풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1194, '생달나무', 'Cinnamomum chekiangense', '녹나무과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1195, '생열귀나무', 'Rosa davurica', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1196, '서덜취', 'Saussurea grandifolia', '국화과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1197, '서양가시엉겅퀴', 'Cirsium vulgare', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1198, '서양개보리뺑이', 'Lapsana communis', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1199, '서양갯냉이', 'Cakile edentula', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1200, '서양고추나물', 'Hypericum perforatum', 'Clusiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1201, '서양금혼초', 'Hypochaeris radicata', '국화과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1202, '서양딱총나무', 'Sambucus nigra', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1203, '서양말냉이', 'Iberis amara', '십자화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1204, '서양메꽃', 'Convolvulus arvensis', '메꽃과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1205, '서양무아재비', 'Raphanus raphanistrum', '십자화과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1206, '서양벌노랑이', 'Lotus corniculatus', '콩과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1207, '서양오엽딸기', 'Rubus fruticosus', '장미과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1208, '서양전동싸리', 'Melilotus dentatus', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1209, '서양톱풀', 'Achillea millefolium', '국화과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1210, '서울개발나물', 'Pterygopleurum neurophyllum', 'Apiaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1211, '서울제비꽃', 'Viola seoulensis', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1212, '서향', 'Daphne odora', 'Thymelaeaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1213, '석결명', 'Senna occidentalis', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1214, '석곡', 'Dendrobium moniliforme', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1215, '석류풀', 'Trigastrotheca stricta', 'Molluginaceae', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1216, '석창포', 'Acorus gramineus', 'Araceae', '{7,8,9}', '7~9월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1217, '선갈퀴', 'Galium odoratum', '꼭두서니과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1218, '선개미자리', 'Sagina micropetala', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1219, '선개불알풀', 'Veronica arvensis', '현삼과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1220, '선갯장대', 'Arabis erecta', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1221, '선괭이밥', 'Oxalis stricta', 'Oxalidaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1222, '선괴불주머니', 'Corydalis pauciovulata', '현호색과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1223, '선나팔꽃', 'Jacquemontia tamnifolia', '메꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1224, '선둥굴레', 'Polygonatum grandicaule', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1225, '선밀나물', 'Smilax nipponica', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1226, '선백미꽃', 'Vincetoxicum inamoenum', 'Apocynaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1227, '선연리초', 'Lathyrus komarovii', '콩과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1228, '선옹초', 'Agrostemma githago', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1229, '선이질풀', 'Geranium krameri', '쥐손이풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1230, '선인장', 'Opuntia ficus-indica', 'Cactaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1231, '선제비꽃', 'Viola raddeana', '제비꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1232, '선주름잎', 'Mazus stachydifolius', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1233, '선줄바꽃', 'Aconitum raddeanum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1234, '선토끼풀', 'Trifolium hybridum', '콩과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1235, '선투구꽃', 'Aconitum umbrosum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1236, '선풀솜나물', 'Gamochaeta calviceps', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1237, '선현호색', 'Corydalis ohii', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1238, '설령쥐오줌풀', 'Valeriana amurensis', '마타리과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1239, '섬강개갓냉이', 'Rorippa apetala', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1240, '섬개벚나무', 'Prunus buergeriana', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1241, '섬곽향', 'Teucrium viscidum', '꿀풀과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1242, '섬국수나무', 'Spiraea insularis', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1243, '섬까치수염', 'Lysimachia acroadenia', '앵초과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1244, '섬노루귀', 'Hepatica maxima', '미나리아재비과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1245, '섬노린재나무', 'Symplocos coreana', 'Symplocaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1246, '섬다래', 'Actinidia rufa', 'Actinidiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1247, '섬딸기', 'Rubus ribisoideus', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1248, '섬말나리', 'Lilium hansonii', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1249, '섬벚나무', 'Prunus takesimensis', '장미과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1250, '섬사철란', 'Goodyera henryi', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1251, '섬시호', 'Bupleurum latissimum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1252, '섬쑥부쟁이', 'Aster pseudoglehnii', '국화과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1253, '섬쥐똥나무', 'Ligustrum foliosum', '물푸레나무과', '{7,8,9}', '7~9월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1254, '섬현호색', 'Corydalis filistipes', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1255, '성널수국', 'Hydrangea luteovenosa', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1256, '성주풀', 'Centranthera cochinchinensis', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1257, '세바람꽃', 'Anemone stolonifera', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1258, '세복수초', 'Adonis multiflora', '미나리아재비과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1259, '세뿔여뀌', 'Persicaria debilis', '마디풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1260, '세뿔투구꽃', 'Aconitum austrokoreense', '미나리아재비과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1261, '세손이', 'Lindera triloba', '녹나무과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1262, '세수염마름', 'Trapella sinensis', 'Pedaliaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1263, '세열미국쥐손이', 'Geranium dissectum', '쥐손이풀과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1264, '세열유럽쥐손이', 'Erodium cicutarium', '쥐손이풀과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1265, '세이지', 'Salvia officinalis', '꿀풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1266, '세잎꿩의비름', 'Hylotelephium verticillatum', '돌나물과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1267, '세잎솜대', 'Maianthemum trifolium', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1268, '세잎승마', 'Actaea bifida', '미나리아재비과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1269, '세잎양지꽃', 'Potentilla freyniana', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1270, '세잎종덩굴', 'Clematis koreana', '미나리아재비과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1271, '세잎쥐손이', 'Geranium wilfordii', '쥐손이풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1272, '세포큰조롱', 'Vincetoxicum volubile', 'Apocynaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1273, '소경불알', 'Codonopsis ussuriensis', '초롱꽃과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1274, '소래풀', 'Orychophragmus violaceus', '십자화과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1275, '소리쟁이', 'Rumex crispus', '마디풀과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1276, '소엽맥문동', 'Ophiopogon japonicus', '백합과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1277, '소엽풀', 'Limnophila aromatica', '현삼과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1278, '소태나무', 'Picrasma quassioides', 'Simaroubaceae', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1279, '속단아재비', 'Paraphlomis koreana', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1280, '속속이풀', 'Rorippa palustris', '십자화과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1281, '손바닥난초', 'Gymnadenia conopsea', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1282, '솔나리', 'Lilium cernuum', '백합과', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1283, '솔붓꽃', 'Iris ruthenica', '붓꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1284, '솔인진', 'Ajania pallasiana', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1285, '솔잎잔대', 'Adenophora gmelinii', '초롱꽃과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1286, '솔잎해란초', 'Nuttallanthus canadensis', '현삼과', '{1,2,3,4,5,6,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1287, '솔체꽃', 'Scabiosa comosa', 'Dipsacaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1288, '솜다리', 'Leontopodium coreanum', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1289, '솜방망이', 'Tephroseris kirilowii', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1290, '솜분취', 'Saussurea eriophylla', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1291, '솜쑥방망이', 'Tephroseris pierotii', '국화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1292, '솜아마존', 'Vincetoxicum amplexicaule', 'Apocynaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1293, '솜양지꽃', 'Potentilla discolor', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1294, '송악', 'Hedera rhombea', 'Araliaceae', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1295, '송양나무', 'Ehretia acuminata', '지치과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1296, '송이풀', 'Pedicularis resupinata', '현삼과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1297, '송장풀', 'Leonurus macranthus', '꿀풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1298, '쇠물푸레나무', 'Fraxinus sieboldiana', '물푸레나무과', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1299, '쇠분취', 'Saussurea sinuata', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1300, '쇠비름', 'Portulaca oleracea', '쇠비름과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1301, '쇠채', 'Scorzonera albicaulis', '국화과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1302, '쇠채아재비', 'Tragopogon dubius', '국화과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1303, '쇠털이슬', 'Circaea cordata', '바늘꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1304, '수궁초', 'Apocynum cannabinum', 'Apocynaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1305, '수리딸기', 'Rubus corchorifolius', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1306, '수리취', 'Synurus deltoides', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1307, '수박', 'Citrullus lanatus', 'Cucurbitaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1308, '수박풀', 'Hibiscus trionum', '아욱과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1309, '수양버들', 'Salix babylonica', 'Salicaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1310, '수염가래꽃', 'Lobelia chinensis', '초롱꽃과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1311, '수염용담', 'Gentianopsis barbata', '용담과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1312, '수염현호색', 'Corydalis caudata', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1313, '수영', 'Rumex acetosa', '마디풀과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1314, '수원잔대', 'Adenophora polyantha', '초롱꽃과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1315, '수잔루드베키아', 'Rudbeckia hirta', '국화과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1316, '수정난풀', 'Monotropa uniflora', 'Pyrolaceae', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1317, '수정목', 'Damnacanthus major', '꼭두서니과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1318, '수호초', 'Pachysandra terminalis', 'Buxaceae', '{1,2,3,4,5,6}', '1~6월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1319, '숙은꽃장포', 'Tofieldia coccinea', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1320, '순비기나무', 'Vitex rotundifolia', '마편초과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1321, '순채', 'Brasenia schreberi', 'Cabombaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1322, '숫잔대', 'Lobelia sessilifolia', '초롱꽃과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1323, '숲바람꽃', 'Anemone umbrosa', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1324, '쉬나무', 'Tetradium daniellii', 'Rutaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1325, '쉽싸리', 'Lycopus lucidus', '꿀풀과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1326, '승마', 'Actaea heracleifolia', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1327, '시계꽃', 'Passiflora caerulea', 'Passifloraceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1328, '시닥나무', 'Acer komarovii', 'Aceraceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1329, '시루산돔부', 'Oxytropis strobilacea', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1330, '시베리아살구', 'Prunus sibirica', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1331, '시베리아여뀌', 'Knorringia sibirica', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1332, '시호', 'Bupleurum komarovianum', 'Apiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1333, '식나무', 'Aucuba japonica', '층층나무과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1334, '신감채', 'Ostericum grosseserratum', 'Apiaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1335, '신안새우난초', 'Calanthe aristulifera', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1336, '실갈퀴', 'Galium linearifolium', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1337, '실꽃풀', 'Chamaelirium japonicum', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1338, '실별꽃', 'Stellaria filicaulis', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1339, '실부추', 'Allium anisopodium', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1340, '실비단분취', 'Saussurea salicifolia', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1341, '실새삼', 'Cuscuta australis', '메꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1342, '실쑥', 'Filifolium sibiricum', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1343, '실유카', 'Yucca filamentosa', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1344, '실제비쑥', 'Artemisia angustissima', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1345, '실통발', 'Utricularia minor', 'Lentibulariaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1346, '싸리냉이', 'Cardamine impatiens', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1347, '쌍구슬풀', 'Bifora radians', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1348, '쌍동바람꽃', 'Anemone rossii', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1349, '쌍둥제비란', 'Platanthera densa', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1350, '쌍실버들', 'Salix divaricata', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1351, '쑥', 'Artemisia indica', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1352, '쑥국화', 'Tanacetum vulgare', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1353, '쑥부지깽이', 'Erysimum cheiranthoides', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1354, '쓴메밀', 'Fagopyrum tataricum', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1355, '쓴풀', 'Swertia japonica', '용담과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1356, '씨범꼬리', 'Bistorta vivipara', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1357, '아광나무', 'Crataegus maximowiczii', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1358, '아구장나무', 'Spiraea ouensanensis', '장미과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1359, '아그배나무', 'Malus toringo', '장미과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1360, '아마', 'Linum usitatissimum', 'Linaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1361, '아마풀', 'Diarthron linifolium', 'Thymelaeaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1362, '아스파라거스', 'Asparagus officinalis', '백합과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1363, '아욱', 'Malva verticillata', '아욱과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1364, '아욱메풀', 'Dichondra micrantha', '메꽃과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1365, '아욱제비꽃', 'Viola hondoensis', '제비꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1366, '앉은좁쌀풀', 'Euphrasia maximowiczii', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1367, '알꽈리', 'Tubocapsicum anomalum', 'Solanaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1368, '알록제비꽃', 'Viola variegata', '제비꽃과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1369, '애기개별꽃', 'Pseudostellaria baekdusanensis', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1370, '애기고광나무', 'Philadelphus pekinensis', '수국과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1371, '애기고추나물', 'Hypericum japonicum', 'Clusiaceae', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1372, '애기골무꽃', 'Scutellaria dependens', '꿀풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1373, '애기괭이눈', 'Chrysosplenium flagelliferum', '수국과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1374, '애기괭이밥', 'Oxalis acetosella', 'Oxalidaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1375, '애기금강제비꽃', 'Viola yazawana', '제비꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1376, '애기기린초', 'Phedimus middendorffianus', '돌나물과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1377, '애기나리', 'Disporum smilacinum', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1378, '애기노랑토끼풀', 'Trifolium dubium', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1379, '애기달맞이꽃', 'Oenothera laciniata', '바늘꽃과', '{10,11,12}', '10~12월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1380, '애기담배풀', 'Carpesium rosulatum', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1381, '애기도라지', 'Wahlenbergia marginata', '초롱꽃과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1382, '애기등', 'Wisteriopsis japonica', '콩과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1383, '애기마디풀', 'Polygonum plebeium', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1384, '애기마름', 'Trapa incisa', 'Trapaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1385, '애기메꽃', 'Calystegia hederacea', '메꽃과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1386, '애기며느리밥풀', 'Melampyrum setaceum', '현삼과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1387, '애기무엽란', 'Neottia acuminata', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1388, '애기물매화', 'Parnassia alpicola', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1389, '애기병꽃', 'Diervilla sessilifolia', '인동과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1390, '애기봄맞이', 'Androsace filiformis', '앵초과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1391, '애기분홍낮달맞이꽃', 'Oenothera rosea', '바늘꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1392, '애기비쑥', 'Artemisia fauriei', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1393, '애기사철란', 'Goodyera repens', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1394, '애기석남', 'Andromeda polifolia', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1395, '애기석잠풀', 'Stachys agraria', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1396, '애기송이풀', 'Pedicularis ishidoyana', '현삼과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1397, '애기수영', 'Rumex acetosella', '마디풀과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1398, '애기실부추', 'Allium tenuissimum', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1399, '애기아욱', 'Malva parviflora', '아욱과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1400, '애기앉은부채', 'Symplocarpus nipponicus', 'Araceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1401, '애기우산나물', 'Syneilesis aconitifolia', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1402, '애기원추리', 'Hemerocallis minor', '백합과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1403, '애기자운', 'Gueldenstaedtia verna', '콩과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1404, '애기장구채', 'Silene aprica', '석죽과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1405, '애기장대', 'Arabidopsis thaliana', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1406, '애기점나도나물', 'Cerastium pumilum', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1407, '애기제비란', 'Platanthera maximowicziana', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1408, '애기좁쌀풀', 'Euphrasia coreanalpina', '현삼과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1409, '애기중의무릇', 'Gagea terracianoana', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1410, '애기참반디', 'Sanicula tuberculata', 'Apiaceae', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1411, '애기탑꽃', 'Clinopodium gracile', '꿀풀과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1412, '애기풀', 'Polygala japonica', 'Polygalaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1413, '애기해바라기', 'Helianthus debilis subsp. cucumerifolius', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1414, '애기현호색', 'Corydalis fumariifolia', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1415, '앵도나무', 'Prunus tomentosa', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1416, '야고', 'Aeginetia indica', 'Orobanchaceae', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1417, '야광나무', 'Malus baccata', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1418, '야생팬지', 'Viola arvensis', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1419, '약모밀', 'Houttuynia cordata', 'Saururaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1420, '얇은잎고광나무', 'Philadelphus tenuifolius', '수국과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1421, '양구슬냉이', 'Camelina sativa', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1422, '양귀비', 'Papaver somniferum', '현호색과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1423, '양미역취', 'Solidago altissima', '국화과', '{9,10,11}', '9~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1424, '양반풀', 'Cynanchum thesioides', 'Apocynaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1425, '양버들', 'Populus nigra', 'Salicaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1426, '양버즘나무', 'Platanus occidentalis', 'Platanaceae', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1427, '양장구채', 'Silene gallica', '석죽과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1428, '양재금방망이', 'Senecio scandens', '국화과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1429, '양파', 'Allium cepa', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1430, '양하', 'Zingiber mioga', 'Zingiberaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1431, '어리연꽃', 'Nymphoides indica', 'Menyanthaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1432, '어수리', 'Heracleum moellendorffii', 'Apiaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1433, '어수리아재비', 'Tordylium maximum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1434, '어저귀', 'Abutilon theophrasti', '아욱과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1435, '어항마름', 'Cabomba caroliniana', 'Cabombaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1436, '얼레지', 'Erythronium japonicum', '백합과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1437, '얼룩닭의장풀', 'Tradescantia fluminensis', 'Commelinaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1438, '얼치기완두', 'Vicia tetrasperma', '콩과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1439, '여뀌바늘', 'Ludwigia epilobioides', '바늘꽃과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1440, '여우꼬리풀', 'Aletris glabra', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1441, '여우오줌', 'Carpesium macrocephalum', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1442, '여우콩', 'Rhynchosia volubilis', '콩과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1443, '여우팥', 'Dunbaria villosa', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1444, '연등심붓꽃', 'Sisyrinchium micranthum', '붓꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1445, '연리갈퀴', 'Vicia venosa', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1446, '연리초', 'Lathyrus quinquenervius', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1447, '연미붓꽃', 'Iris tectorum', '붓꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1448, '연복초', 'Adoxa moschatellina', 'Adoxaceae', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1449, '연영초', 'Trillium camschatcense', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1450, '연잎꿩의다리', 'Thalictrum coreanum', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1451, '연자주쥐손이', 'Geranium purpureum', '쥐손이풀과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1452, '염주괴불주머니', 'Corydalis heterocarpa', '현호색과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1453, '염주장구채', 'Silene conoidea', '석죽과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1454, '영아리난초', 'Nervilia nipponica', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1455, '영아자', 'Asyneuma japonicum', '초롱꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1456, '영암풀', 'Exallage chrysotricha', '꼭두서니과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1457, '영주치자', 'Gardneria nutans', 'Loganiaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1458, '영춘화', 'Jasminum nudiflorum', '물푸레나무과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1459, '오가나무', 'Eleutherococcus sieboldianus', 'Araliaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1460, '오갈피나무', 'Eleutherococcus sessiliflorus', 'Araliaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1461, '오랑캐장구채', 'Silene repens', '석죽과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1462, '오리방풀', 'Isodon excisus', '꿀풀과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1463, '오미자', 'Schisandra chinensis', 'Schisandraceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1464, '오이풀', 'Sanguisorba officinalis', '장미과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1465, '옥녀꽃대', 'Chloranthus fortunei', 'Chloranthaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1466, '옥잠난초', 'Liparis kumokiri', 'Orchidaceae', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1467, '옥천앵두', 'Solanum pseudocapsicum', 'Solanaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1468, '올괴불나무', 'Lonicera praeflorens', '인동과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1469, '올리브나무', 'Olea europaea', '물푸레나무과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1470, '올미', 'Sagittaria pygmaea', 'Alismataceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1471, '옻나무', 'Toxicodendron vernicifluum', 'Anacardiaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1472, '왕과', 'Thladiantha dubia', 'Cucurbitaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1473, '왕괴불나무', 'Lonicera vidalii', '인동과', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1474, '왕달맞이꽃', 'Oenothera macrocarpa', '바늘꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1475, '왕닭의장풀', 'Commelina diffusa', 'Commelinaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1476, '왕도깨비가지', 'Solanum viarum', 'Solanaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1477, '왕도깨비바늘', 'Bidens subalternans', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1478, '왕둥굴레', 'Polygonatum robustum', '백합과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1479, '왕머루', 'Vitis amurensis', 'Vitaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1480, '왕백량금', 'Ardisia crenata', 'Myrsinaceae', '{4,5,6,7,8,9,10,11,12,1,2,3}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1481, '왕버들', 'Salix chaenomeloides', 'Salicaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1482, '왕별꽃', 'Stellaria radians', '석죽과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1483, '왕자귀나무', 'Albizia macrophylla', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1484, '왕제비꽃', 'Viola websteri', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1485, '왕죽대아재비', 'Streptopus koreanus', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1486, '왕쥐똥나무', 'Ligustrum ovalifolium', '물푸레나무과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1487, '왕찔레나무', 'Rosa laevigata', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1488, '왕초피나무', 'Zanthoxylum simulans', 'Rutaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1489, '왕호장근', 'Reynoutria sachalinensis', '마디풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1490, '왜갓냉이', 'Cardamine yezoensis', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1491, '왜개연꽃', 'Nuphar pumila', '수련과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1492, '왜광대수염', 'Lamium album', '꿀풀과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1493, '왜당귀', 'Angelica acutiloba', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1494, '왜떡쑥', 'Gnaphalium uliginosum', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1495, '왜미나리아재비', 'Ranunculus franchetii', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1496, '왜방풍', 'Aegopodium alpestre', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1497, '왜승마', 'Actaea japonica', '미나리아재비과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1498, '왜우산풀', 'Pleurospermum uralense', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1499, '왜젓가락나물', 'Ranunculus silerifolius', '미나리아재비과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1500, '왜제비꽃', 'Viola japonica', '제비꽃과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1501, '왜졸방제비꽃', 'Viola sacchalinensis', '제비꽃과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1502, '왜지치', 'Myosotis sylvatica', '지치과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1503, '왜천궁', 'Angelica genuflexa', 'Apiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1504, '왜현호색', 'Corydalis ambigua', '현호색과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1505, '외대바람꽃', 'Anemone nikoensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1506, '외대으아리', 'Clematis brachyura', '미나리아재비과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1507, '외잎승마', 'Astilbe simplicifolia', '범의귀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1508, '외잎쑥', 'Artemisia viridissima', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1509, '용가시나무', 'Rosa maximowicziana', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1510, '용둥굴레', 'Polygonatum involucratum', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1511, '용머리', 'Dracocephalum argunense', '꿀풀과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1512, '우단담배풀', 'Verbascum thapsus', '현삼과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1513, '우단쥐손이', 'Geranium wlassovianum', '쥐손이풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1514, '우묵사스레피', 'Eurya emarginata', '차나무과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1515, '우산나물', 'Syneilesis palmata', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1516, '우산제비꽃', 'Viola woosanensis', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1517, '우선국', 'Symphyotrichum novi-belgii', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1518, '우엉', 'Arctium lappa', '국화과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1519, '우영사마귀풀', 'Murdannia loriformis', 'Commelinaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1520, '울릉산마늘', 'Allium ulleungense', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1521, '울릉장구채', 'Silene takeshimensis', '석죽과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1522, '울릉제비꽃', 'Viola ulleungdoensis', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1523, '울산도깨비바늘', 'Bidens pilosa', '국화과', '{8,9,10,11,12}', '8~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1524, '울진노루오줌', 'Astilbe uljinensis', '범의귀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1525, '원지', 'Polygala tenuifolia', 'Polygalaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1526, '월계수', 'Laurus nobilis', '녹나무과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1527, '월귤', 'Vaccinium vitis-idaea', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1528, '위령선', 'Clematis florida', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1529, '위성류', 'Tamarix chinensis', 'Tamaricaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1530, '유럽개미자리', 'Spergularia rubra', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1531, '유럽나도냉이', 'Barbarea vulgaris', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1532, '유럽단추쑥', 'Cotula australis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1533, '유럽미나리아재비', 'Ranunculus muricatus', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1534, '유럽수련', 'Nymphaea alba', '수련과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1535, '유럽장대', 'Sisymbrium officinale', '십자화과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1536, '유럽전호', 'Anthriscus caucalis', 'Apiaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1537, '유럽점나도나물', 'Cerastium glomeratum', '석죽과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1538, '유럽큰고추풀', 'Gratiola officinalis', '현삼과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1539, '유럽패랭이꽃', 'Dianthus armeria', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1540, '유홍초', 'Ipomoea quamoclit', '메꽃과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1541, '육계나무', 'Cinnamomum loureiroi', '녹나무과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1542, '육박나무', 'Litsea coreana', '녹나무과', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1543, '육지꽃버들', 'Salix schwerinii', 'Salicaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1544, '윤노리나무', 'Pourthiaea villosa', '장미과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1545, '윤판나물', 'Disporum uniflorum', '백합과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1546, '윤판나물아재비', 'Disporum sessile', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1547, '율무쑥', 'Artemisia koidzumii', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1548, '으름난초', 'Cyrtosia septentrionalis', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1549, '으름덩굴', 'Akebia quinata', 'Lardizabalaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1550, '은난초', 'Cephalanthera erecta', 'Orchidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1551, '은단풍', 'Acer saccharinum', 'Aceraceae', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1552, '은대난초', 'Cephalanthera longibracteata', 'Orchidaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1553, '은백양', 'Populus alba', 'Salicaceae', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1554, '은분취', 'Saussurea gracilis', '국화과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1555, '은빛까마중', 'Solanum elaeagnifolium', 'Solanaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1556, '은양지꽃', 'Potentilla nivea', '장미과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1557, '음나무', 'Kalopanax septemlobus', 'Araliaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1558, '의성개나리', 'Forsythia viridissima', '물푸레나무과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1559, '이고들빼기', 'Crepidiastrum denticulatum', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1560, '이나무', 'Idesia polycarpa', 'Flacourtiaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1561, '이노리나무', 'Malus komarovii', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1562, '이란미나리', 'Lisaea heterocarpa', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1563, '이른범꼬리', 'Bistorta tenuicaulis', '마디풀과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1564, '이삭귀개', 'Utricularia caerulea', 'Lentibulariaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1565, '이삭단엽란', 'Malaxis monophyllos', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1566, '이삭바꽃', 'Aconitum kusnezoffii', '미나리아재비과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1567, '이삭송이풀', 'Pedicularis spicata', '현삼과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1568, '이태리포플라', 'Populus canadensis', 'Salicaceae', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1569, '인가목', 'Rosa acicularis', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1570, '인가목조팝나무', 'Spiraea chamaedryfolia', '장미과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1571, '인디언천인국', 'Gaillardia pulchella', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1572, '인삼', 'Panax ginseng', 'Araliaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1573, '일본매자나무', 'Berberis thunbergii', 'Berberidaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1574, '일본목련', 'Magnolia obovata', '목련과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1575, '일월비비추', 'Hosta nakaiana', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1576, '잇꽃', 'Carthamus tinctorius', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1577, '잎꽃돌나물', 'Sedum kiangnanense', '돌나물과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1578, '잎새바위솔', 'Orostachys spinosa', '돌나물과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1579, '자귀풀', 'Aeschynomene indica', '콩과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1580, '자금우', 'Ardisia japonica', 'Myrsinaceae', '{5,6,7,8,9,10,11,12,1,2,3,4}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1581, '자란초', 'Ajuga spectabilis', '꿀풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1582, '자리공', 'Phytolacca acinosa', 'Phytolaccaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1583, '자병취', 'Saussurea chabyoungsanica', '국화과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1584, '자주개자리', 'Medicago sativa', '콩과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1585, '자주개황기', 'Astragalus laxmannii', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1586, '자주괴불주머니', 'Corydalis incisa', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1587, '자주꿩의다리', 'Thalictrum uchiyamae', '미나리아재비과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1588, '자주꿩의비름', 'Hylotelephium telephium', '돌나물과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1589, '자주달개비', 'Tradescantia ohiensis', 'Commelinaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1590, '자주땅귀개', 'Utricularia uliginosa', 'Lentibulariaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1591, '자주방가지똥', 'Lactuca sibirica', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1592, '자주방아풀', 'Isodon serra', '꿀풀과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1593, '자주비수리', 'Lespedeza lichiyuniae', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1594, '자주솜대', 'Maianthemum bicolor', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1595, '자주쓴풀', 'Swertia pseudochinensis', '용담과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1596, '자주알록제비꽃', 'Viola tenuicornis', '제비꽃과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1597, '자주잎제비꽃', 'Viola violacea', '제비꽃과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1598, '자주천인국', 'Echinacea purpurea', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1599, '자주풀솜나물', 'Gamochaeta purpurea', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1600, '작살나무', 'Callicarpa japonica', '마편초과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1601, '잔개자리', 'Medicago lupulina', '콩과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1602, '잔나비나물', 'Vicia bifolia', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1603, '잔잎바디', 'Angelica czernaevia', 'Apiaceae', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1604, '잔잎양지꽃', 'Potentilla heynei', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1605, '잔털제비꽃', 'Viola keiskei', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1606, '잠자리난초', 'Habenaria linearifolia', 'Orchidaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1607, '장구밥나무', 'Grewia biloba', 'Tiliaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1608, '장구채', 'Silene firma', '석죽과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1609, '장대나물', 'Turritis glabra', '십자화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1610, '장대여뀌', 'Persicaria posumbu', '마디풀과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1611, '장딸기', 'Rubus hirsutus', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1612, '장백제비꽃', 'Viola biflora', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1613, '장백패랭이꽃', 'Dianthus repens', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1614, '장수만리화', 'Forsythia nakaii', '물푸레나무과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1615, '장흥곡정초', 'Eriocaulon buergerianum', 'Eriocaulaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1616, '재쑥', 'Descurainia sophia', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1617, '전동싸리', 'Melilotus suaveolens', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1618, '전주물꼬리풀', 'Pogostemon yatabeanus', '꿀풀과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1619, '전호', 'Anthriscus sylvestris', 'Apiaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1620, '전호아재비', 'Chaerophyllum tainturieri', 'Apiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1621, '절국대', 'Siphonostegia chinensis', '현삼과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1622, '절굿대', 'Echinops setifer', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1623, '점박이천남성', 'Arisaema serratum', 'Araceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1624, '점현호색', 'Corydalis maculata', '현호색과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1625, '젓가락나물', 'Ranunculus chinensis', '미나리아재비과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1626, '정금나무', 'Vaccinium oldhamii', '진달래과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1627, '정향풀', 'Amsonia elliptica', 'Apocynaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1628, '제비고깔', 'Delphinium grandiflorum', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1629, '제비붓꽃', 'Iris laevigata', '붓꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1630, '제비쑥', 'Artemisia japonica', '국화과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1631, '제주무엽란', 'Lecanorchis kiusiana', 'Orchidaceae', '{1,2,3,4,5,6,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1632, '제주산딸기', 'Rubus nishimuranus', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1633, '제주산버들', 'Salix blinii', 'Salicaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1634, '제주진득찰', 'Sigesbeckia orientalis', '국화과', '{1,2,3,7,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1635, '제주큰옥매듭풀', 'Polygonum fusco-ochreatum', '마디풀과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1636, '제주피막이', 'Hydrocotyle yabei', 'Apiaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1637, '제충국', 'Tanacetum cinerariifolium', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1638, '조개나물', 'Ajuga multiflora', '꿀풀과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1639, '조록나무', 'Distylium racemosum', 'Hamamelidaceae', '{5,6,7,8,9,10,11,12,1,2,3,4}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1640, '조록싸리', 'Lespedeza maximowiczii', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1641, '조름나물', 'Menyanthes trifoliata', 'Menyanthaceae', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1642, '조밥나물', 'Hieracium umbellatum', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1643, '조선현호색', 'Corydalis turtschaninovii', '현호색과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1644, '족제비싸리', 'Amorpha fruticosa', '콩과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1645, '졸방제비꽃', 'Viola acuminata', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1646, '좀가지풀', 'Lysimachia japonica', '앵초과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1647, '좀갈매나무', 'Rhamnus taquetii', 'Rhamnaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1648, '좀개갓냉이', 'Rorippa cantoniensis', '십자화과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1649, '좀개미취', 'Aster maackii', '국화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1650, '좀개불알풀', 'Veronica serpyllifolia', '현삼과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1651, '좀개수염', 'Eriocaulon decemflorum', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1652, '좀개자리', 'Medicago minima', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1653, '좀골담초', 'Caragana microphylla', '콩과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1654, '좀다닥냉이', 'Lepidium ruderale', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1655, '좀담배풀', 'Carpesium cernuum', '국화과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1656, '좀댕강나무', 'Diabelia serrata', '인동과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1657, '좀딱취', 'Ainsliaea apiculata', '국화과', '{3,4,5,6,7,8,9,10,11}', '3~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1658, '좀딸기', 'Potentilla centigrana', '장미과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1659, '좀땅비싸리', 'Indigofera koreana', '콩과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1660, '좀맥문동', 'Liriope minor', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1661, '좀머귀나무', 'Zanthoxylum fauriei', 'Rutaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1662, '좀민들레', 'Taraxacum hallaisanense', '국화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1663, '좀부추', 'Allium minus', '백합과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1664, '좀비비추', 'Hosta minor', '백합과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1665, '좀사위질빵', 'Clematis brevicaudata', '미나리아재비과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1666, '좀사철나무', 'Euonymus fortunei', 'Celastraceae', '{1,2,3,4,5,6,7,8,9,10,11}', '1~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1667, '좀소리쟁이', 'Rumex dentatus', '마디풀과', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1668, '좀쉬땅나무', 'Sorbaria kirilowii', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1669, '좀싸리', 'Lespedeza virgata', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1670, '좀씀바귀', 'Ixeris stolonifera', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1671, '좀아마냉이', 'Camelina microcarpa', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1672, '좀양귀비', 'Papaver dubium', '현호색과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1673, '좀양지꽃', 'Potentilla matsumurae', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1674, '좀어리연꽃', 'Nymphoides coreana', 'Menyanthaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1675, '좀작살나무', 'Callicarpa dichotoma', '마편초과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1676, '좀전동싸리', 'Melilotus indicus', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1677, '좀쥐손이', 'Geranium tripartitum', '쥐손이풀과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1678, '좀짚신나물', 'Agrimonia nipponica', '장미과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1679, '좀쪽동백나무', 'Styrax shiraianus', 'Styracaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1680, '좀참꽃', 'Rhododendron redowskianum', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1681, '좀향유', 'Elsholtzia minima', '꿀풀과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1682, '좀현호색', 'Corydalis decumbens', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1683, '좀회양목', 'Buxus microphylla', 'Buxaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1684, '좁쌀냉이', 'Cardamine fallax', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1685, '좁은잎가막사리', 'Bidens cernua', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1686, '좁은잎덩굴용담', 'Pterygocalyx volubilis', '용담과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1687, '좁은잎미꾸리낚시', 'Persicaria praetermissa', '마디풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1688, '좁은잎벌노랑이', 'Lotus tenuis', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1689, '좁은잎사위질빵', 'Clematis hexapetala', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1690, '좁은잎해란초', 'Linaria vulgaris', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1691, '종둥굴레', 'Polygonatum acuminatifolium', '백합과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1692, '종려나무', 'Trachycarpus fortunei', 'Arecaceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1693, '종지나물', 'Viola sororia', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1694, '주걱개망초', 'Erigeron strigosus', '국화과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1695, '주걱끈끈이주걱', 'Drosera spatulata', 'Droseraceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1696, '주걱노루발', 'Pyrola minor', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1697, '주걱댕강나무', 'Diabelia spathulata', '인동과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1698, '주걱비름', 'Sedum tosaense', '돌나물과', '{1,2,3,4,8,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1699, '주걱비비추', 'Hosta clausa', '백합과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1700, '주름구슬냉이', 'Rapistrum rugosum', '십자화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1701, '주름잎', 'Mazus pumilus', '현삼과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1702, '주름전동싸리', 'Melilotus officinalis', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1703, '주름제비란', 'Galearis camtschatica', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1704, '주엽나무', 'Gleditsia japonica', '콩과', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1705, '주홍서나물', 'Crassocephalum crepidioides', '국화과', '{6,7,8,9,10,11,12}', '6~12월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1706, '죽대', 'Polygonatum lasianthum', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1707, '죽대아재비', 'Streptopus amplexifolius', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1708, '죽백란', 'Cymbidium lancifolium', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1709, '죽절초', 'Sarcandra glabra', 'Chloranthaceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1710, '줄꽃주머니', 'Adlumia asiatica', '현호색과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1711, '줄딸기', 'Rubus pungens', '장미과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1712, '줄바꽃', 'Aconitum alboviolaceum', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1713, '줄바늘꽃', 'Epilobium ciliatum', '바늘꽃과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1714, '줄현호색', 'Corydalis bungeana', '현호색과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1715, '중국단풍', 'Acer buergerianum', 'Aceraceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1716, '중국할미꽃', 'Pulsatilla chinensis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1717, '중대가리풀', 'Centipeda minima', '국화과', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1718, '중의무릇', 'Gagea nakaiana', '백합과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1719, '쥐깨풀', 'Mosla dianthera', '꿀풀과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1720, '쥐꼬리풀', 'Aletris spicata', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1721, '쥐다래', 'Actinidia kolomikta', 'Actinidiaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1722, '쥐손이풀', 'Geranium sibiricum', '쥐손이풀과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1723, '쥐오줌풀', 'Valeriana fauriei', '마타리과', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1724, '쥐털이슬', 'Circaea alpina', '바늘꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1725, '지네발란', 'Pelatantheria scolopendrifolia', 'Orchidaceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1726, '지느러미엉겅퀴', 'Carduus crispus', '국화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1727, '지리바꽃', 'Aconitum chiisanense', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1728, '지면패랭이꽃', 'Dianthus deltoides', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1729, '지모', 'Anemarrhena asphodeloides', '백합과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1730, '지채', 'Triglochin maritima', 'Juncaginaceae', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1731, '진득찰', 'Sigesbeckia glabrescens', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1732, '진땅고추풀', 'Deinostema violacea', '현삼과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1733, '진범', 'Aconitum pseudolaeve', '미나리아재비과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1734, '진주고추나물', 'Hypericum oliganthum', 'Clusiaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1735, '진퍼리까치수염', 'Lysimachia fortunei', '앵초과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1736, '진퍼리꽃나무', 'Chamaedaphne calyculata', '진달래과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1737, '진퍼리버들', 'Salix myrtilloides', 'Salicaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1738, '진퍼리잔대', 'Adenophora palustris', '초롱꽃과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1739, '진홍토끼풀', 'Trifolium incarnatum', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1740, '진황정', 'Polygonatum falcatum', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1741, '진흙풀', 'Microcarpaea minima', '현삼과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1742, '짝자래나무', 'Rhamnus yoshinoi', 'Rhamnaceae', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1743, '쪽동백나무', 'Styrax obassia', 'Styracaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1744, '차나무', 'Camellia sinensis', '차나무과', '{6,7,8,9,10,11,12}', '6~12월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1745, '차풀', 'Chamaecrista nomame', '콩과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1746, '찰피나무', 'Tilia mandshurica', 'Tiliaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1747, '참갈매나무', 'Rhamnus ussuriensis', 'Rhamnaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1748, '참개연꽃', 'Nuphar subintegerrima', '수련과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1749, '참골무꽃', 'Scutellaria strigillosa', '꿀풀과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1750, '참깨', 'Sesamum indicum', 'Pedaliaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1751, '참꽃나무', 'Rhododendron weyrichii', '진달래과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1752, '참꽃받이', 'Bothriospermum secundum', '지치과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1753, '참당귀', 'Angelica gigas', 'Apiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1754, '참닻꽃', 'Halenia coreana', '용담과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1755, '참돌매화나무', 'Diapensia lapponica', 'Diapensiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1756, '참두메부추', 'Allium alatoscapum', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1757, '참마', 'Dioscorea japonica', 'Dioscoreaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1758, '참바위취', 'Micranthes oblongifolia', '수국과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1759, '참반디', 'Sanicula chinensis', 'Apiaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1760, '참배암차즈기', 'Salvia chanryoenica', '꿀풀과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1761, '참빗살나무', 'Euonymus hamiltonianus', 'Celastraceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1762, '참소리쟁이', 'Rumex japonicus', '마디풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1763, '참시호', 'Bupleurum scorzonerifolium', 'Apiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1764, '참식나무', 'Neolitsea sericea', '녹나무과', '{2,3,4,5,6,7,8}', '2~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1765, '참싸리', 'Lespedeza cyrtobotrya', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1766, '참오동나무', 'Paulownia tomentosa', '현삼과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1767, '참외', 'Cucumis melo', 'Cucurbitaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1768, '참으아리', 'Clematis terniflora', '미나리아재비과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1769, '참제비고깔', 'Delphinium ajacis', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1770, '참조팝나무', 'Spiraea fritschiana', '장미과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1771, '참죽나무', 'Toona sinensis', 'Meliaceae', '{1,2,3,4,5,6,7,8}', '1~8월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1772, '참줄바꽃', 'Aconitum neotortuosum', '미나리아재비과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1773, '참통발', 'Utricularia tenuicaulis', 'Lentibulariaceae', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1774, '참회나무', 'Euonymus oxyphyllus', 'Celastraceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1775, '창골무꽃', 'Scutellaria barbata', '꿀풀과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1776, '창원제비꽃', 'Viola palmata', '제비꽃과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1777, '창포', 'Acorus calamus', 'Araceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1778, '채고추나물', 'Hypericum attenuatum', 'Clusiaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1779, '채진목', 'Amelanchier asiatica', '장미과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1780, '처녀바디', 'Angelica cartilaginomarginata', 'Apiaceae', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1781, '처진물봉선', 'Impatiens furcillata', '봉선화과', '{7,8,9}', '7~9월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1782, '천마', 'Gastrodia elata', 'Orchidaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1783, '천문동', 'Asparagus cochinchinensis', '백합과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1784, '천수국아재비', 'Dyssodia papposa', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1785, '천일담배풀', 'Carpesium glossophyllum', '국화과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1786, '천지괭이눈', 'Chrysosplenium macrospermum', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1787, '청가시덩굴', 'Smilax sieboldii', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1788, '청괴불나무', 'Lonicera subsessilis', '인동과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1789, '청닭의난초', 'Epipactis papillosa', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1790, '청미래덩굴', 'Smilax china', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1791, '청비수리', 'Lespedeza inschanica', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1792, '청시닥나무', 'Acer barbinerve', 'Aceraceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1793, '초령목', 'Magnolia compressa', '목련과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1794, '초록별꽃', 'Stellaria neglecta', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1795, '초석잠풀', 'Stachys affinis', '꿀풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1796, '초종용', 'Orobanche coerulescens', 'Orobanchaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1797, '초피나무', 'Zanthoxylum piperitum', 'Rutaceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1798, '추분취', 'Rhynchospermum verticillatum', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1799, '층꽃나무', 'Caryopteris incana', '마편초과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1800, '층층갈고리둥굴레', 'Polygonatum sibiricum', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1801, '층층나무', 'Cornus controversa', '층층나무과', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1802, '층층둥굴레', 'Polygonatum stenophyllum', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1803, '층층잔대', 'Adenophora triphylla', '초롱꽃과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1804, '층층장구채', 'Silene macrostyla', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1805, '치자풀', 'Monochasma sheareri', '현삼과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1806, '칠보치마', 'Metanarthecium luteoviride', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1807, '칠엽수', 'Aesculus turbinata', 'Hippocastanaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1808, '카나다엉겅퀴', 'Cirsium arvense', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1809, '카네이션', 'Dianthus caryophyllus', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1810, '카밀레', 'Matricaria chamomilla', '국화과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1811, '캐나다딱총나무', 'Sambucus canadensis', '인동과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1812, '컴프리', 'Symphytum officinale', '지치과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1813, '콩다닥냉이', 'Lepidium virginicum', '십자화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1814, '콩팥노루발', 'Pyrola renifolia', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1815, '큰각시취', 'Saussurea japonica', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1816, '큰개구리발톱', 'Semiaquilegia quelpaertensis', '미나리아재비과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1817, '큰개미자리', 'Sagina maxima', '석죽과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1818, '큰개별꽃', 'Pseudostellaria palibiniana', '석죽과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1819, '큰개수염', 'Eriocaulon taquetii', 'Eriocaulaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1820, '큰개현삼', 'Scrophularia kakudensis', '현삼과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1821, '큰고추풀', 'Gratiola japonica', '현삼과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1822, '큰괭이밥', 'Oxalis obtriangulata', 'Oxalidaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1823, '큰괴불주머니', 'Corydalis gigantea', '현호색과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1824, '큰구슬붕이', 'Gentiana zollingeri', '용담과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1825, '큰꼭두서니', 'Rubia chinensis', '꼭두서니과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1826, '큰꽃땅비싸리', 'Indigofera grandiflora', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1827, '큰꽃장대', 'Dontostemon hispidus', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1828, '큰꿩의비름', 'Hylotelephium spectabile', '돌나물과', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1829, '큰낭아초', 'Indigofera bungeana', '콩과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1830, '큰네잎갈퀴', 'Vicia ramuliflora', '콩과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1831, '큰다닥냉이', 'Lepidium sativum', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1832, '큰달맞이꽃', 'Oenothera glazioviana', '바늘꽃과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1833, '큰닭의덩굴', 'Fallopia dentatoalata', '마디풀과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1834, '큰도꼬마리', 'Xanthium orientale', '국화과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1835, '큰도둑놈의갈고리', 'Hylodesmum oldhamii', '콩과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1836, '큰두루미꽃', 'Maianthemum dilatatum', '백합과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1837, '큰등갈퀴', 'Vicia pseudo-orobus', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1838, '큰망초', 'Erigeron sumatrensis', '국화과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1839, '큰매화노루발', 'Chimaphila umbellata', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1840, '큰메꽃', 'Calystegia sepium', '메꽃과', '{4,5,6,7,8,9}', '4~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1841, '큰물칭개나물', 'Veronica anagallis-aquatica', '현삼과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1842, '큰바늘꽃', 'Epilobium hirsutum', '바늘꽃과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1843, '큰방가지똥', 'Sonchus asper', '국화과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1844, '큰방울새란', 'Pogonia japonica', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1845, '큰백령풀', 'Diodia virginiana', '꼭두서니과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1846, '큰뱀무', 'Geum aleppicum', '장미과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1847, '큰벼룩아재비', 'Mitrasacme pygmaea', 'Loganiaceae', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1848, '큰별꽃', 'Stellaria bungeana', '석죽과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1849, '큰비비추', 'Hosta sieboldiana', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1850, '큰비쑥', 'Artemisia fukudo', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1851, '큰비짜루국화', 'Symphyotrichum expansum', '국화과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1852, '큰산꿩의다리', 'Thalictrum filamentosum', '미나리아재비과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1853, '큰산좁쌀풀', 'Euphrasia hirtella', '현삼과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1854, '큰석류풀', 'Mollugo verticillata', 'Molluginaceae', '{6,7,8,9,10,11}', '6~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1855, '큰수리취', 'Synurus excelsus', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1856, '큰애기나리', 'Disporum viridescens', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1857, '큰앵초', 'Primula jesoana', '앵초과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1858, '큰엉겅퀴', 'Cirsium pendulum', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1859, '큰여우콩', 'Rhynchosia acuminatifolia', '콩과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1860, '큰연영초', 'Trillium tschonoskii', '백합과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1861, '큰오이풀', 'Sanguisorba stipulata', '장미과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1862, '큰옥매듭풀', 'Polygonum bellardii', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1863, '큰원추리', 'Hemerocallis middendorffii', '백합과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1864, '큰잎갈퀴', 'Galium dahuricum', '꼭두서니과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1865, '큰잎냉이', 'Erucastrum gallicum', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1866, '큰잎다닥냉이', 'Lepidium draba', '십자화과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1867, '큰잎싸리', 'Lespedeza davidii', '콩과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1868, '큰장대', 'Clausia trichosepala', '십자화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1869, '큰점나도나물', 'Cerastium fischerianum', '석죽과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1870, '큰제비고깔', 'Delphinium maackianum', '미나리아재비과', '{7,8,9}', '7~9월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1871, '큰제비란', 'Platanthera sachalinensis', 'Orchidaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1872, '큰조롱', 'Cynanchum wilfordii', 'Apocynaceae', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1873, '큰졸방제비꽃', 'Viola kusanoana', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1874, '큰천남성', 'Arisaema ringens', 'Araceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1875, '큰키다닥냉이', 'Lepidium latifolium', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1876, '큰피막이', 'Hydrocotyle ramiflora', 'Apiaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1877, '큰피막이풀', 'Hydrocotyle javanica', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1878, '큰황새냉이', 'Cardamine scutata', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1879, '키버들', 'Salix koriyanagi', 'Salicaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1880, '키큰꿩의비름', 'Hylotelephium pallescens', '돌나물과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1881, '키큰산국', 'Leucanthemella linearis', '국화과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1882, '타래난초', 'Spiranthes sinensis', 'Orchidaceae', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1883, '탐라바위취', 'Saxifraga cortusifolia', '수국과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1884, '탐라현호색', 'Corydalis hallaisanensis', '현호색과', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1885, '탑꽃', 'Clinopodium multicaule', '꿀풀과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1886, '태백바람꽃', 'Anemone pendulisepala', '미나리아재비과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1887, '태백제비꽃', 'Viola albida', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1888, '태산목', 'Magnolia grandiflora', '목련과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1889, '택사', 'Alisma canaliculatum', 'Alismataceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1890, '터리풀', 'Filipendula glaberrima', '장미과', '{3,4,5,6,7,8,9}', '3~9월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1891, '털갈매나무', 'Rhamnus koraiensis', 'Rhamnaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1892, '털개구리미나리', 'Ranunculus cantoniensis', '미나리아재비과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1893, '털개구리자리', 'Ranunculus sardous', '미나리아재비과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1894, '털개머루', 'Ampelopsis glandulosa', 'Vitaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1895, '털고로쇠나무', 'Acer pictum', 'Aceraceae', '{3,4,5,6,7,8,9,10}', '3~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1896, '털괭이눈', 'Chrysosplenium pilosum', '수국과', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1897, '털괴불나무', 'Lonicera subhispida', '인동과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1898, '털까마중', 'Solanum sarrachoides', 'Solanaceae', '{9,10,11}', '9~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1899, '털노박덩굴', 'Celastrus stephanotiifolius', 'Celastraceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1900, '털다닥냉이', 'Lepidium pinnatifidum', '십자화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1901, '털댕강나무', 'Zabelia biflora', '인동과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1902, '털도깨비바늘', 'Bidens biternata', '국화과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1903, '털독말풀', 'Datura innoxia', 'Solanaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1904, '털마삭줄', 'Trachelospermum jasminoides', 'Apocynaceae', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1905, '털머위', 'Farfugium japonicum', '국화과', '{8,9,10,11,12}', '8~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1906, '털박쥐나물', 'Parasenecio hastatus', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1907, '털별꽃아재비', 'Galinsoga quadriradiata', '국화과', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1908, '털복주머니란', 'Cypripedium guttatum', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1909, '털부처꽃', 'Lythrum salicaria', '부처꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1910, '털쉽싸리', 'Lycopus uniflorus', '꿀풀과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1911, '털양지꽃', 'Potentilla squamosa', '장미과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1912, '털여뀌', 'Persicaria orientalis', '마디풀과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1913, '털오갈피나무', 'Eleutherococcus divaricatus', 'Araliaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1914, '털이슬', 'Circaea mollis', '바늘꽃과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1915, '털장대', 'Arabis hirsuta', '십자화과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1916, '털점나도나물', 'Cerastium pauciflorum', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1917, '털제비꽃', 'Viola phalacrocarpa', '제비꽃과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1918, '털조장나무', 'Lindera sericea', '녹나무과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1919, '털족제비싸리', 'Amorpha canescens', '콩과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1920, '털중나리', 'Lilium amabile', '백합과', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1921, '털향유', 'Galeopsis bifida', '꿀풀과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1922, '토대황', 'Rumex aquaticus', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1923, '토란', 'Colocasia esculenta', 'Araceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1924, '토마토', 'Solanum lycopersicum', 'Solanaceae', '{9,10,11}', '9~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1925, '톱바위취', 'Micranthes nelsoniana', '수국과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1926, '톱풀', 'Achillea alpina', '국화과', '{5,6,7,8,9,10}', '5~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1927, '통달목', 'Tetrapanax papyrifer', 'Araliaceae', '{6,7,8}', '6~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1928, '통발', 'Utricularia japonica', 'Lentibulariaceae', '{12,1,2}', '12~2월', 'winter', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1929, '통영볼레나무', 'Elaeagnus pungens', 'Elaeagnaceae', '{1,2,3,4,5,6,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1930, '퉁둥굴레', 'Polygonatum inflatum', '백합과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1931, '파', 'Allium fistulosum', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1932, '파드득나물', 'Cryptotaenia japonica', 'Apiaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1933, '파슬리', 'Petroselinum crispum', 'Apiaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1934, '팔손이', 'Fatsia japonica', 'Araliaceae', '{5,6,7,8,9,10,11,12,1,2,3,4}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (1935, '팥', 'Vigna angularis', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1936, '팥꽃나무', 'Wikstroemia genkwa', 'Thymelaeaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1937, '패랭이아재비', 'Petrorhagia nanteuilii', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1938, '패모', 'Fritillaria usuriensis', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1939, '페루꽈리', 'Nicandra physalodes', 'Solanaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1940, '푸른가막살', 'Viburnum japonicum', '인동과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1941, '푸른마', 'Dioscorea coreana', 'Dioscoreaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1942, '푸른박새', 'Veratrum dolichopetalum', '백합과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1943, '푼지나무', 'Celastrus flagellaris', 'Celastraceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1944, '풀명자', 'Chaenomeles japonica', '장미과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1945, '풀산딸나무', 'Cornus canadensis', '층층나무과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1946, '풀솜나물', 'Euchiton japonicus', '국화과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1947, '풀솜대', 'Maianthemum japonicum', '백합과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1948, '풀싸리', 'Lespedeza thunbergii', '콩과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1949, '풀협죽도', 'Phlox paniculata', '꽃고비과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1950, '풍년화', 'Hamamelis japonica', 'Hamamelidaceae', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1951, '풍도둥굴레', 'Polygonatum odoratum', '백합과', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1952, '풍선덩굴', 'Cardiospermum halicacabum', '무환자나무과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1953, '피나무', 'Tilia amurensis', 'Tiliaceae', '{4,5,6,7,8,9,10,11}', '4~11월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1954, '피나물', 'Hylomecon vernalis', '현호색과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1955, '피막이', 'Hydrocotyle sibthorpioides', 'Apiaceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1956, '피뿌리풀', 'Stellera chamaejasme', 'Thymelaeaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1957, '하늘말나리', 'Lilium tsingtauense', '백합과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1958, '하늘바라기', 'Heliopsis helianthoides', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1959, '하늘산제비란', 'Platanthera neglecta', 'Orchidaceae', '{4,5,6}', '4~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1960, '하늘타리', 'Trichosanthes kirilowii', 'Cucurbitaceae', '{5,6,7,8,9,10,11}', '5~11월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1961, '하수오', 'Reynoutria multiflora', '마디풀과', '{7,8,9,10,11}', '7~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1962, '한계령풀', 'Gymnospermium microrrhynchum', 'Berberidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1963, '한국앉은부채', 'Symplocarpus koreanus', 'Araceae', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1964, '한라고들빼기', 'Crepidiastrum hallaisanense', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1965, '한라부추', 'Allium taquetii', '백합과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1966, '한라비비추', 'Hosta venusta', '백합과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1967, '한라새둥지란', 'Neottia kiusiana', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1968, '한라새우난초', 'Calanthe striata', 'Orchidaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1969, '한라옥잠난초', 'Liparis auriculata', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1970, '한라잠자리란', 'Platanthera minor', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1971, '한라천마', 'Gastrodia pubilabiata', 'Orchidaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1972, '한라투구꽃', 'Aconitum quelpaertense', '미나리아재비과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1973, '한란', 'Cymbidium kanran', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1974, '한련초', 'Eclipta thermalis', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1975, '할미밀망', 'Clematis trichotoma', '미나리아재비과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1976, '함경나비나물', 'Vicia ohwiana', '콩과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1977, '함경딸기', 'Rubus arcticus', '장미과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1978, '함박꽃나무', 'Magnolia sieboldii', '목련과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1979, '함박이', 'Stephania japonica', 'Menispermaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1980, '해국', 'Aster spathulifolius', '국화과', '{7,8,9,10,11,12}', '7~12월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1981, '해녀콩', 'Canavalia lineata', '콩과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1982, '해란초', 'Linaria japonica', '현삼과', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1983, '해란초아재비', 'Kickxia elatine', '현삼과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1984, '해변노박덩굴', 'Celastrus punctatus', 'Celastraceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1985, '해변싸리', 'Lespedeza maritima', '콩과', '{8,9,10}', '8~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1986, '해안선인장', 'Opuntia stricta', 'Cactaceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1987, '향유', 'Elsholtzia ciliata', '꿀풀과', '{8,9,10,11}', '8~11월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1988, '헐떡이풀', 'Tiarella polyphylla', '수국과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1989, '헛개나무', 'Hovenia dulcis', 'Rhamnaceae', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1990, '현삼', 'Scrophularia buergeriana', '현삼과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1991, '협죽도', 'Nerium oleander', 'Apocynaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (1992, '형개', 'Nepeta tenuifolia', '꿀풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1993, '호노루발', 'Pyrola dahurica', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1994, '호대황', 'Rumex gmelinii', '마디풀과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (1995, '호랑가시나무', 'Ilex cornuta', 'Aquifoliaceae', '{2,3,4,5,6}', '2~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1996, '호랑버들', 'Salix caprea', 'Salicaceae', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1997, '호바늘꽃', 'Epilobium amurense', '바늘꽃과', '{6,7,8,9,10}', '6~10월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1998, '호비수리', 'Lespedeza davurica', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (1999, '호산장구채', 'Silene foliosa', '석죽과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (2000, '호자나무', 'Damnacanthus indicus', '꼭두서니과', '{2,3,4,5,6,7,8,9,10,11}', '2~11월', 'winter', null, 'normal', null, 'high', '{}', 0, 'observed'),
  (2001, '호자덩굴', 'Mitchella undulata', '꼭두서니과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2002, '호장근', 'Reynoutria japonica', '마디풀과', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2003, '호제비꽃', 'Viola philippica', '제비꽃과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2004, '혹난초', 'Bulbophyllum inconspicuum', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (2005, '홀꽃노루발', 'Moneses uniflora', 'Pyrolaceae', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (2006, '홀아비꽃대', 'Chloranthus quadrifolius', 'Chloranthaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2007, '홀아비바람꽃', 'Anemone koraiensis', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2008, '홍괴불나무', 'Lonicera maximowiczii', '인동과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2009, '홍노도라지', 'Peracarpa carnosa', '초롱꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2010, '홍도까치수염', 'Lysimachia pentapetala', '앵초과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2011, '홍도서덜취', 'Saussurea polylepis', '국화과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2012, '화살곰취', 'Ligularia jamesii', '국화과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2013, '화살나무', 'Euonymus alatus', 'Celastraceae', '{3,4,5,6,7}', '3~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2014, '화엄제비꽃', 'Viola ibukiana', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2015, '활나물', 'Crotalaria sessiliflora', '콩과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2016, '활량나물', 'Lathyrus davidii', '콩과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2017, '황금', 'Scutellaria baicalensis', '꿀풀과', '{4,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2018, '황단나무', 'Dalbergia hupeana', '콩과', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2019, '황벽나무', 'Phellodendron amurense', 'Rutaceae', '{4,5,6,7,8,9,10}', '4~10월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2020, '황새냉이', 'Cardamine flexuosa', '십자화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2021, '황새승마', 'Actaea cimicifuga', '미나리아재비과', '{1,2,3,4,5,9,10,11,12}', null, 'winter', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2022, '황종용', 'Orobanche pycnostachya', 'Orobanchaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2023, '황해쑥', 'Artemisia argyi', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2024, '회나무', 'Euonymus sachalinensis', 'Celastraceae', '{4,5,6,7,8}', '4~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2025, '회령바늘꽃', 'Epilobium fastigiato-ramosum', '바늘꽃과', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2026, '회리바람꽃', 'Anemone reflexa', '미나리아재비과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2027, '회향', 'Foeniculum vulgare', 'Apiaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2028, '후미푸사선인장', 'Opuntia humifusa', 'Cactaceae', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2029, '후박나무', 'Machilus thunbergii', '녹나무과', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2030, '후추등', 'Piper kadsura', 'Piperaceae', '{3,4,5,6,7,8}', '3~8월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2031, '흑난초', 'Liparis nervosa', 'Orchidaceae', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2032, '흑산도비비추', 'Hosta yingeri', '백합과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2033, '흑오미자', 'Schisandra repanda', 'Schisandraceae', '{1,2,3,4,5,6,7,11,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2034, '흰갈퀴', 'Galium tokyoense', '꼭두서니과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2035, '흰괴불나무', 'Lonicera tatarinowii', '인동과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2036, '흰꽃나도사프란', 'Zephyranthes candida', '수선화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2037, '흰꽃물고추나물', 'Triadenum breviflorum', 'Clusiaceae', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2038, '흰독말풀', 'Datura wrightii', 'Solanaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2039, '흰말채나무', 'Cornus alba', '층층나무과', '{4,5,6,7}', '4~7월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2040, '흰민들레', 'Taraxacum coreanum', '국화과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2041, '흰바위취', 'Micranthes manchuriensis', '수국과', '{5,6,7,8,9}', '5~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2042, '흰범꼬리', 'Bistorta incana', '마디풀과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2043, '흰뿌리제비꽃', 'Viola prionantha', '제비꽃과', '{1,2,3,4,5,6,7,8,12}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2044, '흰쑥', 'Artemisia stelleriana', '국화과', '{1,5,6,7,8,9,10,11,12}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2045, '흰여뀌', 'Persicaria lapathifolia', '마디풀과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2046, '흰여로', 'Veratrum versicolor', '백합과', '{6,7,8,9}', '6~9월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2047, '흰인가목', 'Rosa koreana', '장미과', '{2,3,4,5,6,7,8,9,10}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2048, '흰잎엉겅퀴', 'Cirsium vlassovianum', '국화과', '{1,2,3,4,5,6,7,8,9}', null, 'spring', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2049, '흰전동싸리', 'Melilotus albus', '콩과', '{5,6,7,8}', '5~8월', 'summer', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2050, '흰젖제비꽃', 'Viola lactiflora', '제비꽃과', '{3,4,5}', '3~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2051, '흰제비꽃', 'Viola patrinii', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2052, '흰제비란', 'Platanthera hologlottis', 'Orchidaceae', '{3,4,5,6,7,8,9,10,11}', null, 'summer', null, 'normal', null, 'high', '{}', 0, 'peak_window'),
  (2053, '흰진범', 'Aconitum longecassidatum', '미나리아재비과', '{7,8,9,10}', '7~10월', 'autumn', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2054, '흰털민들레', 'Taraxacum platypecidum', '국화과', '{1,2,3,4,5,6,7,8,9,10,11,12}', null, null, null, 'normal', null, 'high', '{}', 0, 'unknown'),
  (2055, '흰털제비꽃', 'Viola hirtipes', '제비꽃과', '{3,4,5,6}', '3~6월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2056, '흰현호색', 'Corydalis albipetala', '현호색과', '{2,3,4,5}', '2~5월', 'spring', null, 'normal', null, 'high', '{}', 0, 'draft'),
  (2057, '히어리', 'Corylopsis coreana', 'Hamamelidaceae', '{1,2,6,7,8,9,10,11,12}', null, 'autumn', null, 'normal', null, 'high', '{}', 0, 'peak_window')
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
  illust_batch       = excluded.illust_batch,
  bloom_source       = excluded.bloom_source;

-- 적재 검증. 2057종보다 적으면 뭔가 빠진 것이다.
-- (더 많은 것은 실패가 아니다 — 뒤 파일이 이미 돌았을 수 있다.)
do $$
declare n int;
begin
  select count(*) into n from public.flowers;
  if n < 2057 then
    raise exception '도감 종수가 %개다. 2057종 이상이어야 한다', n;
  end if;
end $$;

-- 🔴 `bloom_source = 'human'`은 정확히 200종이어야 한다.
-- 더 많으면 기본값이 새 행을 삼킨 것이다 (0005 2절).
do $$
declare n int;
begin
  select count(*) into n from public.flowers where bloom_source = 'human';
  if n <> 200 then
    raise exception '사람이 정한 종이 %개다. 200이어야 한다 (bloom_source를 빼먹은 적재가 있다)', n;
  end if;
end $$;
