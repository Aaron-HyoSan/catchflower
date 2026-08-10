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
