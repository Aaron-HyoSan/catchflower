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
