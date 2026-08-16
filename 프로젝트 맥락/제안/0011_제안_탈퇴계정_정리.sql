-- ════════════════════════════════════════════════════════════════
--  0011 — 탈퇴한 계정의 **카카오 연결과 로그인 세션**을 파기한다
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **이 파일이 없으면 두 가지가 동시에 깨진다.**
--
--     ① 개인정보 처리방침 6항이 "정리 작업에서 파기한다"고 약속한 **카카오 계정과
--        우리 uuid를 잇는 줄(`auth.identities`)이 서버에 영구히 남는다.**
--        탈퇴했다고 말하면서 카카오 계정과의 연결은 그대로 있는 셈이다.
--     ② **같은 카카오 계정으로 다시 시작할 수 없다.** 처방침 6항 다가
--        "빈 도감으로 새로 시작합니다"라고 적었는데, 실제로는 로그인이 실패한다.
--
--  ## 실측 — 세 가지를 로컬에서 돌려 봤다
--
--  `python3 supabase/_tools/measure_0011_purge.py` (0001·0007의 FK 규칙을 그대로 옮긴
--  최소 스키마 · 앱이 보내는 탈퇴 PATCH까지 그대로):
--
--      ① delete from auth.users  → comments 1→0 · reports 1→0 · public.users 2→1
--         🔴 **남의 사진에 달린 내 댓글과 신고 기록이 연쇄로 사라졌다.** 오류도 경고도 없다.
--         🔴 그러면서 **refresh_tokens는 1로 남았다** — `auth.refresh_tokens.user_id`는
--            uuid가 아니라 문자열이라 FK가 없다. 즉 가장 거친 방법이 **지울 것은 남기고
--            남길 것을 지운다.**
--      ② soft delete 직후 같은 카카오 계정 재연결
--         🔴 막힌다: duplicate key value violates unique constraint
--                    "identities_provider_provider_id_key"
--      ③ **이 파일의 2절·3절을 그대로 읽어서 실행**(베껴 쓰지 않는다)
--         🔵 comments 1 · reports 1 그대로 · identities 0 · sessions 0 · refresh_tokens 0
--         🔵 묘비 행은 남았다: nickname `''` · region_name null · phone_hash null
--         🔵 같은 카카오 계정 재연결 **된다**
--         🔵 함수 권한 — anon False · service_role True
--      판정: PASS
--
--  ## 🔴 그래서 **`delete from auth.users`를 하지 않는다**
--
--  가장 그럴듯한 한 줄이 가장 나쁘다. `0001`이
--  `public.users.id → auth.users(id) on delete cascade`이고
--  `0007`이 `comments.user_id → public.users(id) on delete cascade`라서,
--  auth 한 줄을 지우면 **처방침 6항 나가 "남는다"고 약속한 댓글 본문과
--  "최대 1년 보관"이라고 약속한 신고 기록이 같이 사라진다.**
--  → 이 파일은 **묘비(uuid + `deleted_at`)를 남긴다.** 화면의 `탈퇴한 사용자예요`가
--    그 행을 읽는다 — 지우면 그 표시도 못 만든다.
--
--  ## 0절 — 무엇을 지우고 무엇을 남기나
--
--  | 대상 | 이 파일 | 이유 |
--  |---|---|---|
--  | `auth.identities` (카카오 연결) | **지운다** | 사람과 계정을 잇는 유일한 값. 6항 나의 약속 |
--  | `auth.sessions` | **지운다** | 남으면 죽은 계정의 토큰이 계속 산다 |
--  | `auth.refresh_tokens` | **지운다**(따로) | `user_id`가 문자열이라 FK가 없다 — 연쇄로 안 사라진다(실측 ①) |
--  | `public.users.phone_hash` · `age_confirmed_at` | **지운다** | 앱의 탈퇴 PATCH가 안 비운다(아래 5절 ②) |
--  | `auth.users` 한 줄 | **남긴다** | 지우면 위 연쇄로 댓글·신고가 사라진다 |
--  | `public.users` 묘비 | **남긴다** | `탈퇴한 사용자예요` 표시가 이 행을 읽는다 |
--  | 다른 사람 사진의 댓글 본문 | **남긴다** | 6항 나 · 대화 맥락 |
--  | 신고 기록 | **남긴다** | 6항 나 · 처리 완료 후 최대 1년 |
--
--  ⚠️ 그래서 **처방침 6항 나의 문장을 같이 고쳤다**(`법무/개인정보_처리방침.txt`).
--     "인증 레코드를 파기한다"는 말은 uuid까지 지운다는 뜻으로 읽히는데, 위 표대로
--     uuid는 남는다 — **문서가 코드보다 많이 약속하고 있으면 그게 곧 거짓말이다.**
--
-- ════════════════════════════════════════════════════════════════
--  1절 — 먼저 확인한다 (읽기만 한다 · 아무것도 바꾸지 않는다)
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **여기서 `false`가 하나라도 나오면 2절을 올리지 말고 알려 주세요.**
--     `auth` 스키마는 Supabase가 관리하고, 프로젝트에 따라 `postgres` 롤의 권한이
--     다를 수 있다. 그건 우리 저장소에서 잴 수 없다(위 실측은 우리 FK만 재현한 것이다).
--     권한이 없으면 함수가 **런타임에** 실패한다 — 즉 만들 때는 아무 오류도 안 난다.

select 'auth.identities 를 지울 수 있나' as 확인,
       has_table_privilege(current_user, 'auth.identities', 'delete') as 결과
union all
select 'auth.sessions 를 지울 수 있나',
       has_table_privilege(current_user, 'auth.sessions', 'delete')
union all
select 'auth.users 를 읽을 수 있나',
       has_table_privilege(current_user, 'auth.users', 'select')
union all
select '지금 로그인한 롤', current_user::text is not null;

-- 지금 몇 개가 정리를 기다리고 있나 (0이어도 정상 — 아직 탈퇴한 사람이 없다는 뜻)
select count(*) as 정리대기_계정,
       count(*) filter (where i.user_id is not null) as 카카오연결_남은_계정
  from public.users u
  left join auth.identities i on i.user_id = u.id
 where u.deleted_at is not null;

-- ════════════════════════════════════════════════════════════════
--  2절 — 적용 (SQL Editor에 이 절만 붙여넣어 실행)
-- ════════════════════════════════════════════════════════════════

create or replace function public.purge_deleted_accounts(
  grace interval default interval '0'
)
returns table (
  purged_user        uuid,
  identities_removed int,
  sessions_removed   int
)
language plpgsql
security definer
-- ⚠️ `search_path`를 고정한다. 안 하면 호출자가 만든 스키마의 같은 이름 테이블을
--    definer 권한으로 건드리게 만들 수 있다.
set search_path = public, auth, pg_temp
as $$
declare
  target uuid;
  n_id   int;
  n_se   int;
begin
  for target in
    select u.id
      from public.users u
     where u.deleted_at is not null
       and u.deleted_at <= now() - grace
  loop
    -- 세션을 먼저 지운다. refresh_tokens 는 session_id 연쇄로 같이 사라진다.
    delete from auth.sessions where auth.sessions.user_id = target;
    get diagnostics n_se = row_count;

    -- 🔴 스키마가 다를 경우를 대비해 방어적으로 한 번 더 지운다. `auth.refresh_tokens`의
    --    `user_id`는 uuid가 아니라 문자열이라서 **양쪽을 text로 맞춘다** — 타입이
    --    다르면 조용히 0행을 지우고 성공한다.
    if to_regclass('auth.refresh_tokens') is not null then
      execute 'delete from auth.refresh_tokens where user_id::text = $1' using target::text;
    end if;

    -- 카카오 연결. 이 한 줄이 이 파일의 목적이다.
    delete from auth.identities where auth.identities.user_id = target;
    get diagnostics n_id = row_count;

    -- 묘비에 남은 개인정보를 비운다. **행 자체는 남긴다**(0절).
    update public.users
       set nickname          = '',
           region_name       = null,
           dong_code         = null,
           gu_code           = null,
           region_changed_at = null,
           phone_hash        = null,
           age_confirmed_at  = null
     where public.users.id = target;

    purged_user        := target;
    identities_removed := n_id;
    sessions_removed   := n_se;
    return next;
  end loop;
end;
$$;

-- ─────────────────────────────────────────────────────────────
--  권한 — 🔴 `revoke … from public`을 **반드시** 같이 한다
-- ─────────────────────────────────────────────────────────────
--
--  0010에서 배운 것: 함수를 만들면 Postgres가 `PUBLIC`에게 EXECUTE를 자동으로 준다.
--  `revoke … from anon`만 쓰면 **오류도 경고도 없이 성공하고 아무것도 막지 않는다.**
--  이 함수는 `security definer`라서 그 구멍이 곧 "아무나 남의 auth 줄을 지우는 길"이다.

revoke execute on function public.purge_deleted_accounts(interval) from public;
revoke execute on function public.purge_deleted_accounts(interval) from anon;
revoke execute on function public.purge_deleted_accounts(interval) from authenticated;
grant  execute on function public.purge_deleted_accounts(interval) to   service_role;

-- ════════════════════════════════════════════════════════════════
--  3절 — 적용 확인 (2절을 올린 뒤 붙여넣기)
-- ════════════════════════════════════════════════════════════════
--
--  ⚠️ **대조군이 섞여 있다.** 전부 `true`가 나와야 하는 것이 아니라, 마지막 두 줄은
--     "너무 많이 지우지 않았는가"를 본다 — 그 둘이 `true`로 바뀌면 실패다.

select 1 as 번호, '함수가 있다' as 확인,
       (to_regprocedure('public.purge_deleted_accounts(interval)') is not null)::text as 결과,
       'true' as 기대
union all
select 2, 'anon 은 못 부른다',
       (not has_function_privilege('anon', 'public.purge_deleted_accounts(interval)', 'execute'))::text,
       'true'
union all
select 3, 'authenticated 도 못 부른다',
       (not has_function_privilege('authenticated', 'public.purge_deleted_accounts(interval)', 'execute'))::text,
       'true'
union all
select 4, 'service_role 은 부를 수 있다 (대조군 — false면 너무 많이 잠갔다)',
       has_function_privilege('service_role', 'public.purge_deleted_accounts(interval)', 'execute')::text,
       'true';

-- ── 실제로 돌린다 ────────────────────────────────────────────────
--  결과는 정리한 계정 목록이다. 0행이면 "정리할 게 없었다"는 뜻이다(1절 대기 수와 비교).
select * from public.purge_deleted_accounts();

-- ── 돌린 뒤 ─────────────────────────────────────────────────────
select 5 as 번호, '탈퇴 계정에 카카오 연결이 남지 않았다' as 확인,
       (count(*) = 0)::text as 결과, 'true' as 기대
  from public.users u join auth.identities i on i.user_id = u.id
 where u.deleted_at is not null
union all
select 6, '탈퇴 계정에 세션이 남지 않았다',
       (count(*) = 0)::text, 'true'
  from public.users u join auth.sessions s on s.user_id = u.id
 where u.deleted_at is not null;

-- 🔴 **대조군.** 탈퇴자가 남긴 댓글·신고는 **남아 있어야 한다**(처방침 6항 나).
--    0이 나오면 성공이 아니라 **연쇄 삭제가 일어난 것이다** — 그때는 알려 주세요.
select '탈퇴자가 남긴 댓글(남아야 한다)' as 확인, count(*) as 개수
  from public.comments c join public.users u on u.id = c.user_id
 where u.deleted_at is not null
union all
select '탈퇴자가 낸 신고(남아야 한다)', count(*)
  from public.reports r join public.users u on u.id = r.reporter_id
 where u.deleted_at is not null;

-- 🔴 **여기가 0이 아니면 앱의 탈퇴가 중간에 끊긴 계정이 있다.** 이 함수는 그것을
--    조용히 치우지 않는다 — 치우면 "탈퇴가 실패했다"는 사실이 사라진다.
select '탈퇴했는데 남아 있는 발견 기록' as 확인, count(*) as 개수
  from public.discoveries d join public.users u on u.id = d.user_id
 where u.deleted_at is not null
union all
select '탈퇴했는데 남아 있는 친구 관계', count(*)
  from public.friendships f join public.users u
    on u.id in (f.requester_id, f.addressee_id)
 where u.deleted_at is not null;

-- ════════════════════════════════════════════════════════════════
--  4절 — 언제 돌리나 (오너 결정 · 지금은 손으로 돌린다)
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **손으로 돌리는 동안은 "탈퇴 후 같은 카카오 계정으로 다시 시작"이 막혀 있다.**
--     그 사이에 재시작을 시도한 사람은 로그인 실패만 본다(앱은 원인을 모른다).
--     그래서 처방침 6항 다에 "정리 작업이 끝난 뒤"를 적었다.
--
--  자동으로 돌리려면 `pg_cron`을 켜고 아래를 실행한다. ⚠️ **이 저장소에서 확인하지
--  못했다** — 프로젝트에서 확장이 켜지는지, `cron` 스키마 권한이 있는지 봐야 한다.
--
--      create extension if not exists pg_cron;
--      select cron.schedule(
--        'purge-deleted-accounts', '17 3 * * *',   -- 매일 03:17 (UTC)
--        $$select public.purge_deleted_accounts()$$
--      );
--
--  확인:   select jobname, schedule, active from cron.job;
--  기록:   select status, return_message, start_time from cron.job_run_details
--            where jobname = 'purge-deleted-accounts' order by start_time desc limit 5;
--  취소:   select cron.unschedule('purge-deleted-accounts');
--
--  ⚠️ **`cron.job`에 줄이 생긴 것은 "돌았다"가 아니다.** `job_run_details`의 `status`를
--     봐야 한다 — 권한이 없으면 매일 조용히 실패한다(이 저장소가 "정책이 붙은 것 ≠
--     규칙이 도는 것"으로 이미 겪었다).
--
-- ════════════════════════════════════════════════════════════════
--  5절 — 못 잰 것 / 알고도 안 고친 것
-- ════════════════════════════════════════════════════════════════
--
--  ① **진짜 `auth` 스키마에서 이 함수가 도는지 모른다.** 위 실측은 우리 FK 규칙을
--     재현한 것이고, `postgres` 롤이 `auth.identities`를 지울 수 있는지는 1절이
--     오너 쪽에서 확인한다. 권한이 없으면 GoTrue Admin API의
--     `DELETE /auth/v1/admin/users/{id}/identities/{identity_id}`가 대안이다
--     (⚠️ 그건 `service_role` 비밀 키가 필요하다 — 우리는 그 키를 갖지 않는다).
--
--  ② **`phone_hash`·`age_confirmed_at`은 앱의 탈퇴 PATCH가 안 비운다.** 지금 앱은
--     연락처를 아예 안 받으므로 `phone_hash`는 항상 null이고, 그래서 앱을 고치는
--     대신 여기서 비운다. 🔴 연락처 친구 매칭(C-3)을 켜는 날 **앱 쪽 PATCH에도
--     넣어야 한다** — 안 넣으면 탈퇴한 사람의 전화번호 해시가 유일 인덱스에 남아
--     그 번호로는 새 계정을 만들 수 없다.
--
--  ③ **`auth.mfa_factors`·`auth.audit_log_entries` 등은 손대지 않았다.** 이 앱은 MFA를
--     쓰지 않고, 감사 로그는 Supabase가 관리한다. 다른 서비스에서 이 파일을 베낄 때는
--     그쪽을 다시 세야 한다.
--
--  ④ **되돌릴 수 없다.** 카카오 연결을 지우면 그 계정으로 다시 로그인할 방법이 없다.
--     처방침 6항 다가 "탈퇴는 되돌릴 수 없습니다"라고 적은 그 지점이다.
