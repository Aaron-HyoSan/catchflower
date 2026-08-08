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
