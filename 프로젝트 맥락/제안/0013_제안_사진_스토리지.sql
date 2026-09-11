-- ════════════════════════════════════════════════════════════════
--  0013 — **사진을 서버에 올린다** (A-4 · 보관 3개월)
-- ════════════════════════════════════════════════════════════════
--
--  **오너 결정(2026-08-27):** `도감에 등록되는 사진은 다운로드 가능하게 하고
--  보관기간은 3개월로 한다.` → `오너_결정사항.md` **A-4**.
--
--  지금까지 사진은 **기기에만** 있었다(`PhotoStore` · `filesDir/photos/`).
--  그래서 화면 16(남의 기록 상세)에는 **사진 칸이 아예 없었다** — 와이어프레임
--  맨 위가 `사용자 촬영 사진`인데 그릴 값이 없었기 때문이다.
--  `discoveries.photo_url` 컬럼은 `0001`에 **처음부터 있었고 항상 null이었다.**
--
--  ── 이 파일이 만드는 것 ──────────────────────────────────────────
--   1절  버킷 `discovery-photos` (비공개 · image/jpeg만 · 2MB 상한)
--   2절  경로에서 발견 id를 꺼내는 함수 `photo_discovery_id`
--   3절  `storage.objects` 정책 4개 (읽기 · 넣기 · 덮기 · 지우기)
--   4절  적용 확인 (여기서 초록이 나와야 끝난다)
--   5절  만료·고아 사진 목록 (오너가 지울 때 쓰는 조회)
--
--  ── 실측 ────────────────────────────────────────────────────────
--  `python3 supabase/_tools/measure_0013_photo_storage.py`
--  (pgserver + psycopg 필요 · `~/.cache/cf-sqlvenv2/bin/python`으로 돌렸다)
--
--  🔴 **대시보드로는 이 정책이 도는지 못 본다.** SQL Editor는 소유자 롤이라 RLS를
--     건너뛴다 — 소유자로 눌러 보면 남의 사진도 다 보인다. 그래서 계측기는
--     `set role authenticated` + `request.jwt.claim.sub`로 **사용자로서** 누른다
--     (0012와 같은 이유 · `catchflower-rls-policy-is-not-enforcement`).
--
--  ── 경로 규칙 (여기가 원본이다) ─────────────────────────────────
--
--      discovery-photos/{user_id}/{discovery_id}.jpg
--
--  `discoveries.photo_url`에는 **이 경로**가 들어간다(URL이 아니다 · `0001` 주석과
--  공유계약 1-7). 왜 이 모양인가:
--   · 첫 칸이 `user_id`라서 **정책이 남의 폴더 쓰기를 한 줄로 막는다.**
--   · 둘째 칸이 `discovery_id`라서 **한 기록에 사진이 정확히 한 장**이다
--     (경로가 곧 키다 — 같은 사람이 스토리지를 파일 창고로 쓸 수 없다).
--   · 확장자를 `.jpg`로 못 박는다. 압축은 장변 1600px · JPEG 85
--     (`GamePolicy.PHOTO_LONG_EDGE_PX` · `PHOTO_JPEG_QUALITY`).
--
--  ⚠️ **`storage.foldername()`을 쓰지 않는다.** 실제 Supabase에는 있지만 계측기가
--     재현해야 하는 함수라서, 재현이 조금이라도 다르면 **초록인 채로 틀린다.**
--     `split_part(name, '/', 1)`은 표준 SQL이고 두 곳에서 같은 뜻이다.
--
--  ── 🔴 이 파일에서 가장 조용히 틀릴 수 있는 것 ─────────────────
--  3절 읽기 정책의 `exists (select 1 from public.discoveries ...)` 는
--  **`security definer` 함수가 아니다. 일부러 그렇다.**
--  그 서브쿼리가 **호출자의 RLS를 타야** `discoveries_read`(본인 / public /
--  상호 수락 친구 · 신고 숨김 · 차단 제외)가 사진에도 **그대로** 적용된다.
--  🔴 여기를 `security definer`로 "고치면" 규칙이 사라지고 **남의 비공개 사진이
--     열린다.** 계측기 ⑨가 그 변형을 일부러 만들어서 열리는 것을 보여 준다.
--  (0001의 `is_report_hidden`·`are_friends`는 반대 이유로 definer다 — 거기는
--   **세는** 일이라 RLS가 좁히면 답이 틀렸다. 여기는 **판정 자체를 빌려 온다.**)
--
--  멱등하다. 두 번 돌려도 안전하다 (`on conflict` · `drop policy if exists`).
--  ════════════════════════════════════════════════════════════════


-- ─────────────────────────────────────────────────────────────────
-- 1절. 버킷 — **비공개**로 만든다
-- ─────────────────────────────────────────────────────────────────
--
-- 🔴 `public = true`로 만들면 **경로만 알면 누구나** 사진을 본다(키도 필요 없다).
--    공개 범위(`visibility`)를 지키는 앱이 사진 원본을 열어 두면 앞뒤가 안 맞고,
--    `friends`·`private` 기록의 사진이 링크 하나로 새어 나간다.
--    ⚠️ 화면에는 증상이 없다 — 우리 앱은 어느 쪽이든 잘 그린다.
--
-- `allowed_mime_types`·`file_size_limit`은 **정책이 아니라 storage-api가** 본다.
-- 정책만으로는 "jpeg인가"를 알 수 없다(바이트를 SQL이 안 본다).
--   · 2MB: 장변 1600px JPEG 85가 실측 150~400KB다. 5배 여유를 둔다.
--   · image/jpeg 하나만: png·mp4를 올려 창고로 쓰는 길을 막는다.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'discovery-photos',
  'discovery-photos',
  false,
  2097152,                      -- 2 MiB
  array['image/jpeg']
)
on conflict (id) do update set
  public             = false,
  file_size_limit    = 2097152,
  allowed_mime_types = array['image/jpeg'];


-- ─────────────────────────────────────────────────────────────────
-- 2절. 경로 → 발견 id
-- ─────────────────────────────────────────────────────────────────
--
-- 정책 세 개가 같은 파싱을 한다. 세 곳에 베껴 쓰면 **한 곳만 고친 날 조용히
-- 갈린다** — 읽기는 막고 쓰기는 허용하는 상태가 되고, 그건 화면에 안 보인다.
--
-- ⚠️ **모양이 틀리면 예외가 아니라 null을 준다.** 정책 안에서 예외가 나면
--    요청이 500이 되고, 그러면 "정책이 막았다"와 "함수가 터졌다"를 앱이 구별할
--    수 없다(둘 다 사진이 안 뜬다). null이면 `exists`가 거짓이라 **깔끔히 막힌다.**
--
-- `immutable`이다 — 같은 문자열이면 항상 같은 답이고, 정책 평가가 캐시된다.

create or replace function public.photo_discovery_id(object_name text)
returns uuid
language plpgsql
immutable
as $$
declare
  parts text[];
  tail  text;
begin
  if object_name is null then
    return null;
  end if;
  parts := string_to_array(object_name, '/');
  -- 정확히 두 칸이어야 한다. `a/b/c.jpg`도, `c.jpg`도 우리 경로가 아니다.
  if array_length(parts, 1) is distinct from 2 then
    return null;
  end if;
  -- uuid 36자 + `.jpg`. 대소문자를 둘 다 받는다(uuid 표기는 소문자지만
  -- 클라이언트가 대문자로 만드는 날 사진만 조용히 안 올라가는 것을 막는다).
  tail := substring(parts[2] from '^([0-9a-fA-F-]{36})\.jpg$');
  if tail is null then
    return null;
  end if;
  return tail::uuid;
exception
  when others then
    return null;
end;
$$;

comment on function public.photo_discovery_id(text) is
  'discovery-photos 경로 {user_id}/{discovery_id}.jpg 에서 발견 id를 꺼낸다. 모양이 다르면 null (제안 0013)';

grant execute on function public.photo_discovery_id(text) to anon, authenticated;


-- ─────────────────────────────────────────────────────────────────
-- 3절. storage.objects 정책 4개
-- ─────────────────────────────────────────────────────────────────
--
-- ⚠️ `storage.objects`에는 Supabase가 이미 RLS를 켜 두었다. 정책이 **하나도
--    없으면 아무도 못 읽고 못 쓴다** — 그래서 "업로드가 403"이 이 절을 안 돌린
--    상태의 정상 증상이다.

-- ── 3-1. 읽기 ────────────────────────────────────────────────────
--
-- 두 갈래다:
--   ⓐ **내 사진은 나이와 무관하게 보인다.**
--      🔴 이 갈래가 없으면 **3개월 지난 내 사진을 아무도 목록에서 볼 수 없고,
--         볼 수 없으면 지울 수도 없다** — 앱의 자동 정리가 원리상 못 돈다
--         (`list`도 이 select 정책을 탄다). 그러면 바이트가 영구히 남는데
--         화면에는 아무 증상이 없다. **접근 차단과 삭제는 다른 일이다.**
--      ⚠️ 내 사진은 어차피 내 기기에 원본이 있다. 여는 것이 아니라 **남겨 두는 것**이다.
--   ⓑ 남의 사진은 **90일 안 + 그 기록을 볼 수 있을 때만.**
--      기간은 `storage.objects.created_at`(서버가 넣는 값)으로 센다 —
--      `discoveries.captured_at`·`created_at`은 **클라이언트가 보내는 값**이라
--      미래 날짜를 보내면 보관기간이 늘어난다.
--
-- 🔴 `exists`의 `security definer` 금지 — 파일 머리말 참조.

drop policy if exists discovery_photos_read on storage.objects;
create policy discovery_photos_read on storage.objects
  for select to authenticated
  using (
    bucket_id = 'discovery-photos'
    and (
      -- ⓐ 내 폴더
      split_part(name, '/', 1) = auth.uid()::text
      -- ⓑ 남의 사진: 보관기간 안 + 그 기록이 내게 보이는가
      or (
        created_at > now() - interval '90 days'
        and exists (
          select 1 from public.discoveries d
          where d.id = public.photo_discovery_id(name)
        )
      )
    )
  );

-- ── 3-2. 넣기 ────────────────────────────────────────────────────
--
-- 조건이 셋이다:
--   ① 내 폴더인가 (`{user_id}/`)
--   ② 그 발견 기록이 **존재하고 내 것인가**
--      🔴 이게 없으면 사용자가 자기 폴더에 **아무 이름으로나** 파일을 쌓을 수 있다
--         (스토리지를 무료 창고로 쓰는 길 · 요금은 오너가 낸다).
--         `d.user_id = auth.uid()`는 `discoveries` RLS와 겹치지만 **명시한다** —
--         RLS가 바뀌는 날 이 정책이 조용히 넓어지지 않게.
--   ③ 경로 모양이 맞는가 (`photo_discovery_id`가 null이 아님 = ②에 포함)
--
-- ⚠️ **기록이 먼저 올라가야 사진이 올라간다.** 앱이 그 순서로 보낸다
--    (`DiscoveryRepository.push` → `PhotoSync`). 순서를 뒤집으면 **첫 업로드가
--    항상 403**이고, 다음 실행에 재시도해서 결국 성공하므로 **증상이 늦게 온다.**

drop policy if exists discovery_photos_insert on storage.objects;
create policy discovery_photos_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'discovery-photos'
    and split_part(name, '/', 1) = auth.uid()::text
    and exists (
      select 1 from public.discoveries d
      where d.id = public.photo_discovery_id(name)
        and d.user_id = auth.uid()
    )
  );

-- ── 3-3. 덮기 ────────────────────────────────────────────────────
--
-- 앱은 `x-upsert: true`로 올린다. **재시도가 정상 경로이기 때문이다** —
-- 응답을 못 받았지만 서버에는 들어간 경우(터널 진입)에 덮기가 막혀 있으면
-- 그 사진은 **영원히 409**다(`DiscoveryUploader`가 행에서 겪은 것과 같은 함정).
--
-- ⚠️ `using`과 `with check`를 **둘 다** 쓴다. `with check`를 생략하면 `using`이
--    새 행 검사로도 쓰이는데(0012 ④에서 확인했다), 여기서는 두 식이 달라야 한다:
--    지울 수 있는 행(`using`)과 넣을 수 있는 값(`with check`)이 같은 조건이지만
--    **명시하지 않으면 다음에 한쪽을 고칠 때 나머지가 따라오지 않는다.**

drop policy if exists discovery_photos_update on storage.objects;
create policy discovery_photos_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'discovery-photos'
    and split_part(name, '/', 1) = auth.uid()::text
  )
  with check (
    bucket_id = 'discovery-photos'
    and split_part(name, '/', 1) = auth.uid()::text
    and exists (
      select 1 from public.discoveries d
      where d.id = public.photo_discovery_id(name)
        and d.user_id = auth.uid()
    )
  );

-- ── 3-4. 지우기 ──────────────────────────────────────────────────
--
-- 🔴 **발견 기록이 있는지 보지 않는다. 일부러 그렇다.**
--    지우는 순서가 `기록 삭제 → 사진 삭제`이고(회원 탈퇴는 기록이 cascade로
--    먼저 사라진다), 기록 존재를 요구하면 **그 순간부터 사진을 지울 수 없다.**
--    지울 수 없는 사진은 3개월 뒤에도 남고, 그게 처리방침과 어긋난다.
-- ⚠️ 그래서 이 정책은 `내 폴더인가` 하나만 본다 — 남의 사진은 못 지운다.

drop policy if exists discovery_photos_delete on storage.objects;
create policy discovery_photos_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'discovery-photos'
    and split_part(name, '/', 1) = auth.uid()::text
  );


-- ─────────────────────────────────────────────────────────────────
-- 4절. 적용 확인 — **여기서 초록이 나와야 끝난다**
-- ─────────────────────────────────────────────────────────────────
--
-- ⚠️ SQL Editor는 **마지막 문장 결과만** 보여준다(`catchflower-supabase`).
--    그래서 한 문장으로 합쳐 놓았다 — 아래를 **따로 실행**해서 6행을 읽는다.

select
  '① 버킷'                as 항목,
  case when exists (
    select 1 from storage.buckets
    where id = 'discovery-photos' and public = false
      and file_size_limit = 2097152
      and allowed_mime_types = array['image/jpeg']
  ) then '🔵 OK' else '🔴 없다/설정이 다르다' end as 결과
union all
select '② 파싱 함수',
  case when public.photo_discovery_id(
         '11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.jpg'
       ) = '22222222-2222-2222-2222-222222222222'::uuid
  then '🔵 OK' else '🔴 파싱이 틀렸다' end
union all
select '③ 이상한 경로는 null',
  case when public.photo_discovery_id('a/b/c.jpg') is null
        and public.photo_discovery_id('nope.jpg') is null
        and public.photo_discovery_id('11111111-1111-1111-1111-111111111111/x.png') is null
  then '🔵 OK' else '🔴 이상한 경로가 통과한다' end
union all
select '④ 정책 4개',
  case when (
    select count(*) from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname in ('discovery_photos_read','discovery_photos_insert',
                         'discovery_photos_update','discovery_photos_delete')
  ) = 4 then '🔵 OK' else '🔴 4개가 아니다' end
union all
-- 🔴 **2026-09-11 실측: 이 줄이 틀려서 정상인 정책에 빨강이 떴다.**
--    원래 `qual like '%public.discoveries%'` 였는데, Postgres는 정책을 저장할 때
--    표현식을 **자기 방식으로 다시 써서** 넣는다 — `public` 이 search_path 에 있으면
--    스키마 이름을 **떼고** 적는다. 실제로 저장돼 있던 본문은 이랬다:
--      … AND (EXISTS ( SELECT 1 FROM discoveries d
--                      WHERE (d.id = photo_discovery_id(objects.name))))
--    즉 `public.` 이 없다. 정책은 완벽한데 검사가 원리상 절대 통과할 수 없었다.
--    ⚠️ **정책 본문을 `like` 로 재려면 스키마 접두사를 넣지 않는다.**
--       그리고 "그 이름이 나온다"가 아니라 **규칙을 이루는 조각을 하나씩** 센다.
select '⑤ 읽기 정책이 공개 범위를 본다',
  case when (
    select count(*) from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname = 'discovery_photos_read'
      and qual like '%discoveries%'
      and qual like '%photo_discovery_id%'
      and qual like '%90 days%'
      and qual like '%auth.uid()%'
  ) = 1 then '🔵 OK' else '🔴 discoveries·90일·auth.uid() 중 빠진 것이 있다' end
union all
select '⑥ 버킷이 공개가 아니다',
  case when not exists (
    select 1 from storage.buckets where id = 'discovery-photos' and public
  ) then '🔵 OK' else '🔴 공개 버킷이다 — 경로만 알면 누구나 본다' end;


-- ─────────────────────────────────────────────────────────────────
-- 5절. 만료·고아 사진 — **오너가 지울 때 쓰는 조회**
-- ─────────────────────────────────────────────────────────────────
--
-- 🔴 **`delete from storage.objects`로 지우면 안 된다.** 그건 **메타데이터 행만**
--    지운다 — 실제 바이트는 스토리지에 남고, 목록에서 사라져서 **지운 것처럼
--    보인다.** 반드시 Storage API(대시보드 Storage 화면 · `storage.remove()`)로
--    지운다. 아래 조회는 **무엇을 지울지 고르는 데만** 쓴다.
--
-- 정리는 두 층이다:
--   ⓐ **앱이 자기 사진을 지운다** — 하루 한 번, 90일 넘은 내 사진을 목록에서
--      찾아 `DELETE /storage/v1/object/...`로 지운다(`PhotoRetention`).
--      키가 필요 없고(사용자 토큰) 활동 중인 사용자에게는 이걸로 끝난다.
--   ⓑ **오너가 나머지를 지운다** — 앱을 다시 열지 않는 사용자와, 탈퇴로 기록이
--      cascade 삭제돼 **주인 없는 사진**이 된 것들. 아래 조회가 그 목록이다.
--      ⏸ 자동화하려면 service_role 키가 필요하다(Edge Function 또는
--         pg_cron + Vault). MVP에서는 **오너가 대시보드에서 지운다.**

-- 5-1. 90일이 지난 사진 (보관기간 초과)
--
--   select * from public.expired_photo_objects order by created_at limit 200;
create or replace view public.expired_photo_objects as
  select
    o.name                                        as object_path,
    o.created_at,
    (now() - o.created_at)                        as age,
    public.photo_discovery_id(o.name)             as discovery_id,
    (o.metadata ->> 'size')::bigint               as size_bytes
  from storage.objects o
  where o.bucket_id = 'discovery-photos'
    and o.created_at <= now() - interval '90 days';

comment on view public.expired_photo_objects is
  '보관기간(3개월)이 지난 사진. Storage API로 지운다 — delete from storage.objects 는 바이트를 안 지운다 (제안 0013)';

-- 5-2. 기록이 사라진 사진 (탈퇴·기록 삭제 후 남은 것)
create or replace view public.orphan_photo_objects as
  select
    o.name                            as object_path,
    o.created_at,
    (o.metadata ->> 'size')::bigint   as size_bytes
  from storage.objects o
  where o.bucket_id = 'discovery-photos'
    and not exists (
      select 1 from public.discoveries d
      where d.id = public.photo_discovery_id(o.name)
    );

comment on view public.orphan_photo_objects is
  '발견 기록이 없는 사진(탈퇴·기록 삭제). 같은 방식으로 지운다 (제안 0013)';

-- ⚠️ **두 뷰를 사용자에게 열지 않는다.** 이름 목록 자체가 "누가 몇 장 올렸는가"다.
--    `security_invoker`를 켜지 않으므로 뷰 소유자(postgres) 권한으로 돌아간다 —
--    그래서 **grant 를 주지 않는 것이 유일한 잠금이다.**
revoke all on public.expired_photo_objects  from anon, authenticated;
revoke all on public.orphan_photo_objects   from anon, authenticated;

-- 5-3. 지금 얼마나 쌓였나 (비용 감각)
--
--   1,000명 × 90일 × 하루 1장 × 150KB ≈ 13.5GB → Pro 100GB 안(오너_결정사항 A-4).
--   ⚠️ **용량과 전송량(egress)은 다른 축이다.** 남의 사진을 보는 만큼 나간다.
select
  count(*)                                              as 사진_수,
  pg_size_pretty(sum((metadata ->> 'size')::bigint))    as 총_용량,
  min(created_at)                                       as 가장_오래된,
  count(*) filter (where created_at <= now() - interval '90 days') as 만료된_수
from storage.objects
where bucket_id = 'discovery-photos';
