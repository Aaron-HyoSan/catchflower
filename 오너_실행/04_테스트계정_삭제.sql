-- ════════════════════════════════════════════════════════════════
--  04 — 내 테스트 쓰레기 계정만 지운다 (2026-08-12)
-- ════════════════════════════════════════════════════════════════
--
--  🔴 **이 파일은 지운다. 앞의 세 파일과 다르다.**
--     `01`은 만들고 `02`는 세고 `03`은 잠겨 있었다. 이건 실제로 삭제한다.
--     그래서 **2절을 돌리기 전에 1절을 먼저 돌려 목록을 눈으로 본다.**
--
--  ## 왜 `03`의 2절을 안 쓰고 새 파일인가
--
--  `03` 2절은 "익명 계정을 전부 지운다"였다. 그걸 지금 풀면 **오너의 실물 꽃 QA
--  기록이 같이 날아간다** — 특히 `9451db26…`(42건)이 익명 계정이다. 그래서 `03`
--  2절은 계속 `/* */`로 잠근 채 두고, **지울 것을 지문으로 좁힌 문장**을 새로 쓴다.
--
--  ## 지문 — 무엇을 "내 쓰레기"로 보는가 (2026-08-12 실측)
--
--  오너가 돌린 분류 조회의 24줄을 받아서, 서버의 좌표 **값**을 직접 읽어 확인했다.
--  분류 조회는 `서로다른좌표 1`처럼 **개수만** 보여주고 값은 안 보여주기 때문이다.
--
--      ⚪️ 6개  → 전부 (37.5665, 126.9780)      = `ReactionLiveTest`의 씨앗 좌표
--      🔴 11개 → (37.5, 127.0) 과 (38.1, 127.0) = 내 진단 스크립트 좌표
--      🟢 7개  → (37.4976,127.0296) (37.5077,127.0458)
--                (37.5146,127.0496) (37.5445,127.0557)  ← 실제로 찍은 자리들
--
--  ✅ **세 무리의 좌표가 하나도 겹치지 않는다.** 그래서 좌표로 가를 수 있다.
--     겹쳤다면 이 파일을 쓰지 않고 오너에게 되물었을 것이다.
--
--  🔴 **`🔴 애매하다` 11개는 내 것이었다.** 분류 조회가 그걸 못 갈랐던 이유는
--     `⚪️` 조건에 좌표 한 점(37.5665/126.9780)만 적어 뒀기 때문이다. 내 진단
--     스크립트들은 **다른 점**을 쓴다 — 그래서 조회가 "모른다"고 답했다.
--     즉 11개는 새로운 종류가 아니라 **내가 조회에 안 적은 좌표**였다.
--
--  ⚠️ **11개 중 3개는 내가 확인하지 못했다**(`f8027842` `6bd3c4e5` `6c2250f7`).
--     그 계정 기록이 `private`라서 앱 권한으로는 안 보인다. 날짜(08-11)와 다른
--     8개의 모양으로 보아 같은 스크립트의 것이 거의 확실하지만 **"거의"다.**
--     → 그래서 이 파일은 **내 목록을 믿지 않는다.** 아래 2절은 uuid를 나열하는
--       대신 **모든 기록이 지문에 맞는 계정만** 지운다. 내 추측이 틀렸다면
--       그 계정은 지문에 안 맞아서 **그냥 안 지워진다.**
--
--  ## 안전장치 4개
--
--   ① `a31bcf09…`(보존 지시받은 계정)를 이름으로 제외한다
--   ② 기록이 **하나라도** 지문을 벗어나면 그 계정은 통째로 건너뛴다
--   ③ 사진·장소가 하나라도 있으면 건너뛴다 (실물 QA의 표식)
--   ④ 가입 계정(`is_anonymous`가 아닌 것)은 애초에 후보에 안 든다
--
--  ⚠️ **기록이 0개인 계정 92개는 이 파일이 건드리지 않는다.** 랭킹에 안 나오니
--     급하지 않고, 지문으로 가릴 근거도 없다(기록이 없으면 지문도 없다).
--
--  ✅ 두 번 돌려도 안전하다. 두 번째는 지울 것이 없어서 0줄이 나온다.


-- ─────────────────────────────────────────────────────────────
-- 1. 먼저 이것만 돌린다 — **무엇이 지워질지 보여준다** (아무것도 안 지운다)
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 2절과 **똑같은 조건**을 쓴다. 조건을 두 벌로 적으면 한쪽만 고쳐져서
--    "본 것과 지워진 것이 다른" 사고가 난다. 그래서 조건은 `삭제대상` 하나뿐이고
--    1절과 2절이 그것을 같이 쓴다.
with 지문 as (
  -- 내 스크립트가 쓰는 좌표. 소수 4자리로 반올림해 비교한다
  -- (`double precision`은 그대로 `=`로 비교하면 안 된다).
  select * from (values
    (37.5665, 126.9780),   -- ReactionLiveTest.seedOwnDiscovery
    (37.5000, 127.0000),   -- diag2~6 · diag_softdelete · e2e_reactions
    (38.1000, 127.0000),   -- diag7 (공개 기록 쪽)
    (37.5624, 126.9256)    -- probe_ranking · probe_ranking2
  ) as t(lat, lng)
),
후보 as (
  select
    d.user_id,
    count(*)                                                   as 기록수,
    count(d.photo_url)                                         as 사진있음,
    count(d.place_name)                                        as 장소있음,
    -- 지문 밖 좌표가 **하나라도** 있으면 이 계정은 건드리지 않는다
    count(*) filter (
      where not exists (
        select 1 from 지문 f
         where round(d.lat::numeric, 4) = f.lat
           and round(d.lng::numeric, 4) = f.lng
      )
    )                                                          as 지문밖,
    -- 좌표가 비어 있는 기록도 "모르는 것"이다 → 지문밖과 같이 취급한다
    count(*) filter (where d.lat is null or d.lng is null)      as 좌표없음,
    string_agg(distinct round(d.lat::numeric,4)::text || ',' ||
                        round(d.lng::numeric,4)::text, ' | ')   as 좌표들,
    min(d.created_at)::date                                     as 처음,
    max(d.created_at)::date                                     as 마지막
  from public.discoveries d
  join auth.users u on u.id = d.user_id
  where u.is_anonymous is true
    and u.id::text not like 'a31bcf09%'        -- ① 보존 지시받은 계정
  group by d.user_id
),
삭제대상 as (
  select * from 후보
   where 지문밖 = 0            -- ②
     and 좌표없음 = 0          -- ②
     and 사진있음 = 0          -- ③
     and 장소있음 = 0          -- ③
)
select
  user_id, 기록수, 좌표들, 처음, 마지막,
  '🗑 지운다' as 판정
from 삭제대상
union all
select
  user_id, 기록수, 좌표들, 처음, 마지막,
  case
    when 사진있음 > 0 or 장소있음 > 0 then '🟢 남긴다 (사진·장소가 있다)'
    when 좌표없음 > 0                then '🟡 남긴다 (좌표가 비어 있다 — 모르는 것)'
    else                                  '🟡 남긴다 (지문 밖 좌표가 있다)'
  end
from 후보
 where user_id not in (select user_id from 삭제대상)
order by 판정, 기록수 desc;

-- ⚠️ **기대: `🗑 지운다`가 17줄, `🟢/🟡 남긴다`가 7줄이다.**
--    🔴 다르면 2절을 돌리지 말고 이 표를 그대로 보여준다. 특히
--    **`🗑` 줄에 기록수가 42인 계정이 있으면 절대 돌리지 않는다** — 그건
--    `9451db26…`(오너의 실물 QA)이고, 그게 후보에 들었다면 내 지문이 틀린 것이다.


-- ─────────────────────────────────────────────────────────────
-- 2. 위 표를 확인한 **뒤에** 이 아래 `/* */`를 벗기고 돌린다
-- ─────────────────────────────────────────────────────────────
--
-- 🔴 삭제 순서가 중요하다. `discoveries`를 먼저 지우고 계정을 지운다 —
--    좋아요·댓글·랭킹 캐시가 기록을 참조하기 때문이다.
--    (`auth.users` 삭제만으로 정리되는지는 이 저장소가 확인한 적이 없다.
--     확인 안 된 것에 기대지 않고 명시적으로 지운다.)
--
-- ⚠️ 여기도 조건을 **다시 적지 않는다.** 1절과 같은 `with`를 그대로 쓴다.

/*
with 지문 as (
  select * from (values
    (37.5665, 126.9780),
    (37.5000, 127.0000),
    (38.1000, 127.0000),
    (37.5624, 126.9256)
  ) as t(lat, lng)
),
후보 as (
  select
    d.user_id,
    count(d.photo_url) as 사진있음,
    count(d.place_name) as 장소있음,
    count(*) filter (
      where not exists (
        select 1 from 지문 f
         where round(d.lat::numeric, 4) = f.lat
           and round(d.lng::numeric, 4) = f.lng
      )
    ) as 지문밖,
    count(*) filter (where d.lat is null or d.lng is null) as 좌표없음
  from public.discoveries d
  join auth.users u on u.id = d.user_id
  where u.is_anonymous is true
    and u.id::text not like 'a31bcf09%'
  group by d.user_id
),
삭제대상 as (
  select user_id from 후보
   where 지문밖 = 0 and 좌표없음 = 0 and 사진있음 = 0 and 장소있음 = 0
),
지운기록 as (
  delete from public.discoveries
   where user_id in (select user_id from 삭제대상)
  returning user_id
),
지운계정 as (
  delete from auth.users
   where id in (select user_id from 삭제대상)
  returning id
)
select
  (select count(*) from 지운기록) as 지운기록수,
  (select count(*) from 지운계정) as 지운계정수;
*/

-- ⚠️ **기대: 지운기록수 19 · 지운계정수 17.**
--    기록이 계정보다 많은 이유는 두 계정이 기록을 2개씩 갖고 있어서다
--    (`c1394d87` `51450132` — diag7이 공개·비공개를 한 계정에 하나씩 넣는다).


-- ─────────────────────────────────────────────────────────────
-- 3. 지운 뒤에 다시 센다
-- ─────────────────────────────────────────────────────────────
--
-- ⚠️ **문장이 하나다** — SQL Editor는 여러 문장을 돌리면 마지막 결과만 보여준다.
--    2절과 3절을 같이 돌리면 3절만 보이고 **몇 개가 지워졌는지가 가려진다.**
--    그래서 2절과 3절은 **따로** 돌린다.
select
  (select count(*) from auth.users where is_anonymous is true)        as 남은익명계정,
  (select count(distinct user_id) from public.discoveries)            as 기록있는계정,
  (select count(*) from public.discoveries)                           as 남은기록,
  (select count(*) from auth.users where id::text like 'a31bcf09%')   as 보존계정살아있나,
  (select count(*) from public.discoveries d join auth.users u
     on u.id = d.user_id where u.id::text like '9451db26%')           as 실물QA_42건;

-- ⚠️ **기대: 보존계정살아있나 = 1 · 실물QA_42건 = 42.**
--    🔴 이 두 줄이 이 파일의 진짜 검사다. "몇 개 지웠다"는 성공을 증명하지 않는다 —
--    **지우면 안 되는 것이 남아 있는가**가 증명한다.
