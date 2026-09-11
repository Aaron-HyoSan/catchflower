-- ════════════════════════════════════════════════════════════════
--  0012 — **동의 없이 친구가 되는 세 가지 길**을 막는다
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **지금 서버는 상대의 동의 없이 친구가 되는 것을 막지 않는다.**
--     `0001`의 두 정책이 `state`를 아예 안 본다:
--
--         friendships_insert_requester : with check (requester_id = auth.uid())
--         friendships_update_addressee : for update using (addressee_id = auth.uid())
--
--     그래서 앱이 아니라 **키만 든 사람**(anon key는 APK 안에 있다)이 이렇게 할 수 있다:
--
--         POST /rest/v1/friendships
--         {"requester_id":"<나>","addressee_id":"<아무나>","state":"accepted"}
--
--     한 번의 요청으로 `are_friends(나, 아무나)`가 **true**가 된다. C-2 상호 수락이
--     서버에서는 존재하지 않는 규칙이었던 셈이다.
--
--  🔴 **이게 왜 지금 급한가.** 2026-08-27에 `친구에게만` 공개(항목 3)와 친구 수락
--     화면(항목 1)이 붙었다. `discoveries_read_visible`(0001)이
--     `visibility = 'public' or public.are_friends(auth.uid(), user_id)`라서,
--     위조된 한 행은 곧 **남이 친구에게만 공개한 사진·좌표·시각을 읽는 권한**이다.
--     즉 이 구멍은 "친구 목록이 이상해진다"가 아니라 **개인정보 열람**이다.
--
--  ## 실측 — `python3 supabase/_tools/measure_0012_friendship_forge.py`
--
--  0001의 `friendships`·`discoveries`·`are_friends`·정책 4개를 그대로 재현하고
--  `set role authenticated` + `request.jwt.claim.sub`로 **사용자로서** 눌러 봤다.
--  (대시보드로는 안 보인다 — 소유자 롤은 RLS를 건너뛴다.)
--
--      ▮ 0012 적용 **전**
--      ① insert (나→피해자, 'accepted')            🔴 1행 들어갔다 · are_friends=True
--         → 피해자의 `friends` 기록이 **보였다**(1건)
--      ② update 로 requester_id 갈아치우기          🔴 1행 · (제3자→나, accepted)가 됐다
--         → 요청한 적 없는 제3자와 친구 · are_friends=True
--      ③ 상대가 나를 차단한 행을 accepted로         🔴 1행 · 차단이 친구로 뒤집혔다
--      ④ (대조군) addressee_id 를 남으로            🔵 막힌다 — `with check`가 생략되면
--         `using` 식이 새 행 검사로도 쓰인다(그래서 ①②③만 열려 있었다)
--
--      ▮ 0012 적용 **후**
--      ① `new row violates row-level security policy` (정책이 막았다)
--      ② `친구 관계의 당사자는 바꿀 수 없습니다` (트리거가 막았다)
--      ③ **오류가 아니라 `0행`이다** — `using`이 그 행을 아예 안 고른다. 조용하다.
--         🔵 그래서 앱이 `Prefer: return=representation`으로 **행 수를 센다**
--            (`FriendService.accept` → `NO_ROW`). 안 세면 이 갈래가 성공으로 보인다.
--      ④ 그대로 막힌다
--      🔵 **앱이 실제로 보내는 수락 PATCH는 그대로 된다** — 1행 · state=accepted ·
--         responded_at 서버 시각으로 채워짐
--      🔵 pending 행 만들기(정상 요청) · 차단 행 만들기 · 거절(DELETE) 그대로 된다
--      판정: PASS
--
--  ## 0절 — 무엇을 바꾸나
--
--  | 바꾸는 것 | 왜 |
--  |---|---|
--  | insert 에 `state <> 'accepted'` | **`accepted`로 태어나는 행을 막는다.** 요청은 늘 `pending`으로 시작한다(앱도 `state`를 안 보낸다). `blocked`는 남겨 둔다 — 차단은 상대 동의가 필요한 일이 아니다 |
--  | update 의 `using`에 `state = 'pending'` | **답할 것이 있는 행만 답한다.** 이미 `accepted`·`blocked`인 행은 update 대상이 아니다(③을 막는다) |
--  | update 의 `with check`에 `state <> 'pending'` | 수락/차단으로 **나아가는** 변경만 허용한다 |
--  | `before update` 트리거 | **당사자 두 칸과 `created_at`을 얼린다.** `with check`는 `old` 행을 볼 수 없어서 ②를 정책만으로는 못 막는다 |
--  | 트리거가 `responded_at := now()` | 지금은 **기기 시각**이 들어온다(`FriendService.accept`). 서버 시각으로 덮는다 — 읽는 사람이 없는 칸이라 시계가 틀려도 아무도 모른다 |
--
--  ⚠️ **앱 코드는 안 바꾼다.** 클라이언트는 이미 `state=eq.pending`을 걸고
--     `state`를 안 보낸다(`FriendServiceTest.요청은_두_uuid만_보내고_상태는_서버가_채운다`).
--     0012는 **앱이 이미 지키던 규칙을 서버에 옮겨 적는 것**이다.
--
--  ⚠️ **공유계약.** `friendships`는 iOS/AOS 공용이고 0001은 iOS 담당이다.
--     그래서 이 파일은 `제안/`에 둔다 — 오너가 SQL Editor에서 돌리고, iOS 세션에
--     `진행.md`로 알린다. **enum·컬럼·정책 이름은 하나도 안 바꿨다**(정책 두 개의
--     조건만 좁힌다) — iOS 클라이언트가 고칠 것은 없다.


-- ════════════════════════════════════════════════════════════════
--  1절 — 먼저 확인한다 (읽기만 한다 · 아무것도 바꾸지 않는다)
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **여기서 `accepted`가 이미 많이 보이면 알려 주세요.** 위조된 행이 있을 수 있다.
--     (지금은 실사용자가 오너 계정뿐이라 0~1행이 정상이다.)

select state, count(*) as 행수
  from public.friendships
 group by state
 order by state;

-- 정책이 지금 어떤 식을 갖고 있나. `qual`·`with_check`를 눈으로 확인한다.
select policyname, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public' and tablename = 'friendships'
 order by policyname;


-- ════════════════════════════════════════════════════════════════
--  2절 — 적용 (SQL Editor에 이 절만 붙여넣어 실행)
-- ════════════════════════════════════════════════════════════════

-- 2-1. 요청은 `accepted`로 태어날 수 없다.
--
-- 🔴 `state = 'pending'`으로 못 박지 않는 이유: 차단(`blocked`)은 관계가 없던 상대에게도
--    할 수 있어야 하고, 그건 상대 동의가 필요한 일이 아니다. 막아야 하는 것은
--    **동의를 건너뛴 `accepted`** 하나다.
drop policy if exists friendships_insert_requester on public.friendships;
create policy friendships_insert_requester on public.friendships
  for insert with check (requester_id = auth.uid() and state <> 'accepted');

-- 2-2. 받은 쪽이 답하는 것은 **아직 답 안 한 행**뿐이다.
--
-- 🔴 `using`에 `state = 'pending'`이 없으면, 상대가 나를 차단한 행(내가 addressee다)을
--    내가 `accepted`로 뒤집을 수 있다 — 차단한 사람의 기록이 나에게 열린다.
-- ⚠️ `with check`를 **적어 둔다.** 생략하면 `using` 식이 새 행 검사로도 쓰여서
--    `state = 'pending'`이 새 행에도 걸리고, 그러면 **수락 자체가 0행이 된다.**
drop policy if exists friendships_update_addressee on public.friendships;
create policy friendships_update_addressee on public.friendships
  for update
  using      (addressee_id = auth.uid() and state = 'pending')
  with check (addressee_id = auth.uid() and state <> 'pending');

-- 2-3. 당사자 두 칸은 얼린다 — 정책으로는 못 막는 자리다.
--
-- 🔴 `with check`는 `old` 행을 볼 수 없다. 그래서 "받은 요청의 `requester_id`를
--    제3자로 갈아치우고 `accepted`로 만든다"는 위 두 정책을 **둘 다 통과한다**
--    (새 행도 여전히 `addressee_id = auth.uid()`이므로).
create or replace function public.friendships_freeze_pair()
returns trigger
language plpgsql
as $$
begin
  if new.requester_id <> old.requester_id or new.addressee_id <> old.addressee_id then
    -- 42501 = insufficient_privilege. PostgREST가 403으로 내보내고 앱은
    -- `친구 요청을 처리하지 못했어요`를 띄운다(재시도해도 같은 결과인 갈래다).
    raise exception '친구 관계의 당사자는 바꿀 수 없습니다' using errcode = '42501';
  end if;
  -- 요청 시각도 얼린다 — 목록 순서(`created_at.desc`)를 뒤에서 바꿀 수 있으면
  -- 남의 요청을 내 목록 맨 위로 올릴 수 있다.
  new.created_at   := old.created_at;
  -- 답한 시각은 **서버가 찍는다.** 지금은 기기 시각이 올라온다.
  new.responded_at := now();
  return new;
end
$$;

drop trigger if exists friendships_freeze_pair on public.friendships;
create trigger friendships_freeze_pair
  before update on public.friendships
  for each row execute function public.friendships_freeze_pair();


-- ════════════════════════════════════════════════════════════════
--  3절 — 적용 확인 (2절을 올린 뒤 붙여넣기)
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **세 줄 다 `true`여야 한다.** 하나라도 false면 2절이 덜 올라갔다.

select 'insert 가 accepted 를 막나' as 확인,
       (select with_check from pg_policies
         where schemaname='public' and tablename='friendships'
           and policyname='friendships_insert_requester') like '%accepted%' as 결과
union all
select 'update 가 pending 행만 보나',
       (select qual from pg_policies
         where schemaname='public' and tablename='friendships'
           and policyname='friendships_update_addressee') like '%pending%'
union all
select '당사자를 얼리는 트리거가 붙었나',
       exists (select 1 from pg_trigger
                where tgrelid = 'public.friendships'::regclass
                  and tgname = 'friendships_freeze_pair'
                  and not tgisinternal);


-- ════════════════════════════════════════════════════════════════
--  4절 — 못 잰 것 / 알고도 안 고친 것
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **이미 위조된 행이 있는지는 여기서 못 판정한다.** `accepted` 행이 정상 수락으로
--     생긴 것인지 위조인지 구별할 표시가 없다(`responded_at`이 null인 `accepted` 행은
--     의심스럽지만, 0001에 트리거가 없어서 **정상 수락도 null일 수 있었다** —
--     2026-08-27 전에는 앱이 그 칸을 아예 안 보냈다). 1절이 그래서 개수만 센다.
--     지금은 실사용자가 오너 계정뿐이라 실질 위험이 없다.
--
--  ⚠️ **친구를 끊는 것은 여전히 양쪽 다 할 수 있다**(`friendships_delete_involved`).
--     그건 정상이다 — 동의가 필요한 것은 맺는 쪽이다.
--
--  ⚠️ **`blocked` 행을 상대가 지울 수 있다**(같은 delete 정책). 내가 차단했는데
--     상대가 그 행을 지우면 차단이 풀린다. 0012 범위 밖이고, 차단 UI가 아직 없어서
--     앱에서는 도달할 수 없다 — 차단 기능을 붙일 때 delete 정책도 같이 좁혀야 한다.
--     (`A_문구·버튼_스펙.md` 4절에 항목으로 남기지 않았다. 서버 규칙이라 문구가 없다.)
--
--  ⚠️ **`are_friends`는 security definer라 RLS를 건너뛴다.** 그래서 위조 행 하나가
--     곧바로 열람으로 이어졌다. 이 함수를 좁히는 것으로는 못 고친다 —
--     친구 판정은 원래 RLS 밖에서 해야 한다(0001 주석). 고칠 자리는 **행이 생기는 곳**이고
--     그게 2절이다.
