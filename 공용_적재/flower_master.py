# -*- coding: utf-8 -*-
"""도감 마스터 2,057종을 만든다 — **iOS·Android 공용 단일 구현**.

## 이 파일이 존재하는 이유

적재 스크립트가 **두 개**다. `꽃도감/_tools/build_app_data.py`(Android가 읽는
`꽃도감/flowers.json`)와 `공용_적재/build_flowers_json.py`(iOS가 읽는
`공용_적재/flowers.json`). 200종일 때는 둘 다 CSV를 그대로 옮기기만 해서
갈라져도 티가 안 났다.

🔴 **확장은 CSV를 옮기는 게 아니라 값을 만든다.** 개화월 cascade·라틴→한글 과·
   계절 규칙을 양쪽에 각자 쓰면 **두 앱이 다른 후보 집합을 쓰게 된다** —
   그게 공유계약 1-2가 금지한 바로 그것이다(개화월 하드 필터의 입력이다).
   그래서 **만드는 로직은 여기 한 번만** 있고 두 스크립트가 이걸 import 한다.

## 계약 근거

`프로젝트 맥락/공유계약_iOS_AOS.md` 1-1 / 1-1-a / 1-1-b / 1-1-c / 1-2-b / 1-2-c / 1-5.
숫자를 여기서 바꾸지 않는다 — 계약을 먼저 고치고 `진행.md`에 적는다.

## 🔴 실측으로 정한 것 (전부 캐시 재생 · API 호출 0건)

| 결정 | 근거 |
|---|---|
| id 1~200은 사람 값 유지 | 초안으로 덮으면 Top-1 77.0% → **50.0%** (튤립 38/40 → **0/40**) |
| 개화월 cascade 5단 | 최고 Top-1(81.5%) 구성은 **1,077종을 영구히 못 잡는다**. 채택안은 1,985/2,057 |
| 저관찰(1~29건)에 구간 추정 금지 | 그 구간 cover **42.9%** — 좁고 그럴듯하고 틀린 창이 종을 지운다 |
| 계절 경계 8월=가을 | 사람 200종에서 재적합 **83.5% → 90.5%** |
| 신규종 난이도 `상` | `하`로 두면 **모르는 종을 3장 자동 확정**한다(오등록). `상`은 0장 |
| 희귀도는 관찰수로 못 정한다 | 재적합해도 **64.7%**(최빈 추측 48%) — 그래서 신규종은 `보통` 고정 |
| 색·서식지는 값을 만들지 않는다 | 이름 형태소 색은 **58.8%**(17종 중 7종 오답), 서식지는 원천이 없다 |
"""

import csv
import os
import re
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CSV_200 = os.path.join(ROOT, "꽃도감", "꽃목록_200종.csv")
CSV_2057 = os.path.join(ROOT, "꽃도감", "꽃목록_확장_2057종.csv")

TOTAL_COUNT = 2057
HUMAN_COUNT = 200

# ── 공유계약 1-4의 enum. 한글 → 계약 문자열 ────────────────────────────
SEASON = {"봄": "spring", "여름": "summer", "가을": "autumn", "겨울": "winter"}
RARITY = {"흔함": "common", "보통": "normal", "귀함": "rare"}
DIFFICULTY = {"하": "low", "중": "mid", "상": "high"}

# 계약 1-2-b의 `bloom_source`. **화면에 내보내지 않는다** — 보일 문구가 A 문서에 없다.
BLOOM_HUMAN = "human"
BLOOM_DRAFT = "draft"
BLOOM_OBSERVED = "observed"
BLOOM_PEAK = "peak_window"
BLOOM_UNKNOWN = "unknown"

# 🔴 `bloom_label`을 낼 수 없는 출처. 계약 1-2-c가 "문구를 만들지 않고 절을 뺀다"고
#    정한 대상이다. 빈 문자열을 내리면 화면에 `국화과 · 에 피는 꽃`이 나온다.
#
# ⚠️ **출처만으로 판정되지 않는다.** ③ `observed`인데도 표기를 못 내는 종이 9개 있다 —
#    관찰 기록이 12달 전부에 있는 상록수(개산초·광나무·…)다. `1~12월`이라고 쓰면
#    "일 년 내내 핀다"는 **단정**이 되는데 실제 뜻은 "모든 달에 관찰 기록이 있다"다.
#    그래서 판정 함수는 [bloom_label_for]이고, 이 상수는 그 안에서만 쓴다.
BLOOM_SOURCES_WITHOUT_LABEL = (BLOOM_PEAK, BLOOM_UNKNOWN)


def bloom_label_for(months, bloom_source):
    """화면에 낼 개화기 표기. 낼 수 없으면 `""`.

    🔴 **표기를 낼 수 있는지의 유일한 판정.** 처음엔 계약 1-2-c대로 `bloom_source`만
       봤는데, 그러면 12달 전부가 개화월인 `observed` 9종에 `1~12월`이 붙는다.
       즉 **판정 근거가 출처가 아니라 "월 배열로 정직한 문장을 만들 수 있는가"** 다.
    """
    if bloom_source in BLOOM_SOURCES_WITHOUT_LABEL:
        return ""
    return _label_from_months(months) or ""

# ── 계절 경계 — **사람이 정한 200종에서 재적합했다** ──────────────────
# 🔴 처음에 `9월부터 가을`로 쓰니 사람 값과 83.5%만 맞았고 오분류가 한 방향으로
#    쏠렸다(5월 시작을 사람은 여름으로 본다). 첫 달별 최빈 계절로 다시 맞추니
#    **90.5%(181/200)**가 됐다. 즉 규칙이 아니라 내가 쓴 경계가 틀렸던 것이다.
#    확장 CSV의 `계절_초안`은 낡은 경계로 만들어졌으므로 **쓰지 않는다**
#    (그 컬럼은 사람 값과 73.6%만 맞고, 자기 `개화기_초안`과도 382/803 모순이다).
SEASON_OF_FIRST_MONTH = {
    1: "겨울", 2: "겨울", 3: "봄", 4: "봄", 5: "봄", 6: "여름",
    7: "여름", 8: "가을", 9: "가을", 10: "가을", 11: "겨울", 12: "겨울",
}

# 🔴 **대표월의 종류가 다르면 경계표도 달라야 한다.**
#    위 표는 "개화 첫 달"에 맞춘 것이고(90.5%), ④ peak_window의 대표월은
#    "관찰 최다월"이라 성질이 다르다 — 개화 시작보다 늦다. 위 표를 그대로 쓰면
#    **68.6%**로 떨어지는데, 최다월로 따로 적합하면 **76.4%**다.
#    같은 이름의 '월'이라고 같은 표를 쓰면 조용히 나빠진다.
SEASON_OF_PEAK_MONTH = {
    1: "겨울", 2: "겨울", 3: "봄", 4: "봄", 5: "봄", 6: "여름",
    7: "여름", 8: "여름", 9: "여름", 10: "가을", 11: "겨울", 12: "겨울",
}

# 신규종 기본값 — **근거가 없을 때 무엇으로 두는가**.
#
# 🔴 난이도는 화면에 안 보이지만 **분기를 바꾼다**. `IdentifyFlow.decide`가
#    `confidenceThreshold[난이도]`(하 0.60·중 0.70·상 0.85)를 넘으면 화면 10으로
#    **자동 확정**한다. 캐시 200장 실측(1순위가 신규종인 41장):
#      기본 `하` → 자동확정 정답 46 · **오등록 3** / `중` → 46 · **1** / `상` → 46 · **0**
#    정답 자동확정은 셋 다 46으로 같고 오등록만 줄어든다. 그래서 `상`이다.
#    "모르는 종을 자동으로 확정하지 않는다"가 이 값의 뜻이다.
DEFAULT_DIFFICULTY_NEW = "상"

# 🔴 희귀도는 관찰수로 정할 수 없다. `희귀도_초안`은 사람 값과 **52.6%**만 맞고
#    (사람 '보통'을 '흔함'으로 40건 — 한 방향으로 쏠렸다), 경계를 격자탐색으로
#    다시 맞춰도 **64.7%**다(최빈값만 찍어도 48%다). GBIF 관찰수는 사람이 보는
#    "도시에서 만나기 쉬움"과 다른 것을 재고 있다 — 채집·업로드 편향이 섞인다.
#    그래서 **초안을 쓰지 않고 `보통`으로 둔다.** 틀린 세 갈래보다 균일한 한 갈래가
#    낫다 — 희귀도는 화면 06 필터 축이고 신규 발견 연출 강도를 정한다.
DEFAULT_RARITY_NEW = "보통"


def nfc(s):
    """⚠️ macOS 파일명·일부 CSV는 NFD. 이름을 키로 쓰면 **전량 불일치**한다."""
    return unicodedata.normalize("NFC", (s or "").strip())


# ─────────────────────────────────────────────────────────────────────
# 개화기 파싱 — 공유계약 1-2. **여기서 단 한 번만 한다**
# ─────────────────────────────────────────────────────────────────────

def parse_bloom_months(text):
    """`"3~4월"` → `[3, 4]` · `"12~4월"` → `[12, 1, 2, 3, 4]` (랩어라운드).

    빈 문자열은 **거부한다** — 개화월이 비면 하드 필터에서 영구 제외이고,
    그걸 조용히 통과시키면 도감에 보이는데 평생 못 잡는 종이 된다.
    """
    s = (text or "").strip().replace(" ", "")
    if not s:
        raise ValueError("개화기가 비어 있다")

    m = re.fullmatch(r"(\d{1,2})~(\d{1,2})월", s)
    if m:
        start, end = int(m.group(1)), int(m.group(2))
        _check_month(start, text)
        _check_month(end, text)
        if start <= end:
            return list(range(start, end + 1))
        return list(range(start, 13)) + list(range(1, end + 1))

    m = re.fullmatch(r"(\d{1,2})월", s)
    if m:
        month = int(m.group(1))
        _check_month(month, text)
        return [month]

    raise ValueError("개화기 표기를 해석할 수 없다: %r" % (text,))


def _check_month(month, original):
    if not 1 <= month <= 12:
        raise ValueError("월 범위를 벗어났다: %d (원문 %r)" % (month, original))


def parse_month_counts(text):
    """`관찰월분포` `"1:5/2:0/..."` → `{월: 건수}`. 0건인 달은 버린다."""
    out = {}
    for part in (text or "").split("/"):
        if ":" not in part:
            continue
        k, v = part.split(":", 1)
        try:
            month, count = int(k), int(v)
        except ValueError:
            continue
        if 1 <= month <= 12 and count > 0:
            out[month] = count
    return out


def pad_months(months, k=1):
    """앞뒤로 k개월 넓힌다(원형). 순서를 보존한다."""
    out = list(months)
    for _ in range(k):
        head = 12 if out[0] == 1 else out[0] - 1
        tail = 1 if out[-1] == 12 else out[-1] + 1
        out = [head] + out + [tail]
    seen, uniq = set(), []
    for m in out:
        if m not in seen:
            seen.add(m)
            uniq.append(m)
    return uniq


def observed_run(counts, share=0.05):
    """관찰 최다월에서 원형으로 확장. 최다월의 `share` 이상인 연속 구간.

    🔴 **12개월을 다 덮으면 거기서 멈춘다.** 양방향으로 각각 11칸을 걷기 때문에
       12달 전부가 `keep`이면 **자기를 지나쳐 계속 걷고 `23개월`이 나온다.**
       상록수 9종(개산초·광나무·굴거리나무·꽝꽝나무·멀구슬나무·왕백량금·자금우·
       조록나무·팔손이)이 실제로 그랬다 — 전 달에 관찰 기록이 있는 종들이다.

       예외는 안 났다. 개화월 필터는 `month in months`라서 **중복이 있어도 옳게 돈다.**
       드러난 자리는 `bloom_label`이었다: `_label_from_months`가 `months[0]`과
       `months[-1]`을 읽어 **`6~4월`**을 만들고, 그게 화면 09에 `6~4월에 피는 꽃`으로
       나간다. 개수를 세어 봐서 잡았다(23개월인 종 9개).
    """
    if not counts:
        return []
    top = max(counts.values())
    keep = {m for m in range(1, 13) if counts.get(m, 0) >= top * share}
    peak = max(counts, key=lambda m: counts[m])

    run, m = [peak], peak
    for _ in range(11):
        m = 12 if m == 1 else m - 1
        if m not in keep or m in run:
            break
        run.insert(0, m)
    m = peak
    for _ in range(11):
        m = 1 if m == 12 else m + 1
        if m not in keep or m in run:
            break
        run.append(m)
    return run


def around_peak(counts, k=4):
    """최다월 ±k (원형). **구간을 추정하지 않는다** — 계약 1-2-b ④."""
    peak = max(counts, key=lambda m: counts[m])
    return sorted({((peak - 1 + off) % 12) + 1 for off in range(-k, k + 1)})


def bloom_for_new_species(row):
    """신규종 1종의 `(개화월, bloom_source, 대표월)`. 계약 1-2-b의 cascade ②~⑤.

    🔴 순서를 바꾸면 안 된다. 그리고 **1~29건에 구간 추정을 넣지 않는다** —
       그 구간의 실측 cover가 42.9%다(관찰 5건인 깨꽃에 실제 `6~10월` 대신
       `9~10월`을 준다). 빠진 달에 찍은 사용자는 그 꽃을 영원히 못 잡는데
       **어떤 지표도 움직이지 않는다.**

    🔴 **대표월을 따로 돌려주는 이유** — 계절을 `months[0]`으로 뽑으면 안 된다.
       ④ `around_peak`은 **정렬된** 배열을 준다: 최다월 4월이면
       `[1,2,3,4,5,6,7,8,12]`이고 첫 원소가 1월이다. 처음에 `months[0]`을 썼더니
       **신규종 계절이 겨울 647종**으로 나왔다(전체의 3분의 1). 예외도 안 나고
       화면에도 "겨울" 칩이 그냥 붙는다 — 개수를 세어 봐서 잡았다.
       `[1,...,12]`처럼 랩어라운드·전월 배열이 섞이면 **월 배열의 첫 원소는
       개화 시작이 아니다.**
    """
    draft = (row["개화기_초안"] or "").strip()
    if draft:
        # ② 초안 + 앞뒤 1개월. 초안 규칙 그대로는 cover 64.3%, 1개월 넓히면 93.0%.
        #    대표월은 **넓히기 전 초안의 첫 달**이다 — 넓힌 값의 첫 달은 한 달 이르다.
        months = parse_bloom_months(draft)
        return pad_months(months, 1), BLOOM_DRAFT, months[0]

    counts = parse_month_counts(row["관찰월분포"])
    total = sum(counts.values())
    if total >= 30:
        # ③ cover 98.5%. `observed_run`은 최다월에서 확장하므로 첫 원소가 개화 시작이다.
        run = observed_run(counts)
        return run, BLOOM_OBSERVED, run[0]
    if counts:
        # ④ cover 97.9%. 대표월은 **최다월**이다(정렬된 배열의 첫 원소가 아니다).
        peak = max(counts, key=lambda m: counts[m])
        return around_peak(counts, 4), BLOOM_PEAK, peak
    # ⑤ 근거가 없다 — 막지 않는다. 대표월도 없으므로 계절을 정하지 않는다.
    return list(range(1, 13)), BLOOM_UNKNOWN, None


# ─────────────────────────────────────────────────────────────────────
# 과(family) — 계약 1-1-b. **한글이 원본이다**
# ─────────────────────────────────────────────────────────────────────

def build_family_map(rows_200, rows_ext):
    """학명 속(genus)이 겹치는 종에서 라틴→한글 과 대응을 뽑는다.

    🔴 **기존 200종의 한글 값이 먼저 이긴다.** 라틴 1개가 한글 2개인 경우가 있고
       (`Ranunculaceae` → 미나리아재비과·작약과, `Saxifragaceae` → 범의귀과·수국과)
       그건 **최근 분류에서 갈라진 과**라 라틴 쪽이 낡았다. 1:1 표로 만들면
       한쪽을 조용히 덮는다. 그래서 **속 단위로 먼저 맞추고**, 속이 없을 때만
       과 단위 다수결로 내린다.
    """
    ext_by_id = {int(r["도감번호"]): r for r in rows_ext}
    by_genus = {}
    latin_votes = {}

    for r in rows_200:
        fid = int(r["도감번호"])
        korean = nfc(r["과"])
        latin = nfc(ext_by_id[fid]["과"]) if fid in ext_by_id else ""
        genus = _genus(r["학명"])
        if korean and genus:
            by_genus.setdefault(genus, korean)
        if korean and latin:
            latin_votes.setdefault(latin, {})
            latin_votes[latin][korean] = latin_votes[latin].get(korean, 0) + 1

    # 과 단위는 다수결. 동수면 이름이 짧은 쪽(상위 과)을 쓴다 — 임의성을 고정한다.
    by_latin = {
        latin: sorted(votes.items(), key=lambda kv: (-kv[1], len(kv[0])))[0][0]
        for latin, votes in latin_votes.items()
    }
    return by_genus, by_latin


def build_latin_family_by_genus(rows_ext):
    """확장 CSV 안에서 `속 → 라틴 과`를 모은다. **과가 아예 빈 4종을 메우는 데 쓴다.**

    🔴 4종(애기중의무릇·왕자귀나무·일월비비추·참두메부추)은 확장 CSV의 `과`가
       비어 있다 — KPNI 이명 해소로 들어온 행이라 원천에 과가 없었다.
       이름을 코드에 박지 않고 **같은 속의 다른 종에서 가져온다**: 넷 다 같은 속
       동속종이 CSV 안에 있고 투표가 만장일치다
       (`Gagea`→Liliaceae 1 · `Albizia`→Fabaceae 1 · `Hosta`→Liliaceae 8 ·
       `Allium`→Liliaceae 20). 박아 두면 다음에 CSV가 바뀔 때 조용히 낡는다.
    """
    votes = {}
    for r in rows_ext:
        latin = nfc(r["과"])
        genus = _genus(r["학명"])
        if latin and genus:
            votes.setdefault(genus, {})
            votes[genus][latin] = votes[genus].get(latin, 0) + 1
    # 만장일치인 속만 쓴다. 갈리면 추측이므로 채우지 않는다(그러면 적재가 멈춘다).
    return {g: list(v)[0] for g, v in votes.items() if len(v) == 1}


def _genus(scientific_name):
    parts = (scientific_name or "").strip().split()
    return parts[0].lower() if parts else ""


def korean_family(row, by_genus, by_latin, latin_by_genus):
    """신규종의 과를 한글로 바꾼다. 못 바꾸면 **라틴명을 그대로 내린다**.

    빈칸으로 내리지 않는다 — 화면 05가 `Rosa hybrida · 장미과`로 그리는 자리라
    빈칸이면 `Rosa hybrida · `가 된다(계약 1-1-c).
    """
    genus = _genus(row["학명"])
    if genus in by_genus:
        return by_genus[genus]
    latin = nfc(row["과"])
    if not latin:
        # 확장 CSV의 `과`가 빈 4종. 같은 속의 다른 종에서 라틴 과를 가져온다.
        latin = latin_by_genus.get(genus, "")
    if latin in by_latin:
        return by_latin[latin]
    return latin  # 미지의 라틴 과 — 통과시킨다. 빈칸보다 낫다


# ─────────────────────────────────────────────────────────────────────
# 마스터 조립
# ─────────────────────────────────────────────────────────────────────

def _read(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def _split_similar(text):
    t = (text or "").strip()
    if not t or t == "-":
        return []
    return [x.strip() for x in t.split(",") if x.strip() and x.strip() != "-"]


def _enum(table, value, column, flower_id):
    key = (value or "").strip()
    if key not in table:
        raise ValueError(
            "도감번호 %s: %s 값 %r 이 공유계약 1-4의 enum에 없다" % (flower_id, column, key)
        )
    return table[key]


def build_master():
    """2,057종 마스터를 만든다. `(flowers, stats, dangling)`.

    🔴 `stats`를 함께 돌려주는 이유: 출처별 종수를 **세어서 보고**해야
       "2,057종을 등록했다"가 "2,057종을 다 안다"로 읽히지 않는다.
       `peak_window`+`unknown`이 사람이 채워야 하는 목록이다(계약 1-2-b).
    """
    rows_200 = _read(CSV_200)
    rows_ext = _read(CSV_2057)

    if len(rows_200) != HUMAN_COUNT:
        raise ValueError("기존 CSV가 %d종이어야 한다. 실제 %d종" % (HUMAN_COUNT, len(rows_200)))
    if len(rows_ext) != TOTAL_COUNT:
        raise ValueError("확장 CSV가 %d종이어야 한다. 실제 %d종" % (TOTAL_COUNT, len(rows_ext)))

    human = {int(r["도감번호"]): r for r in rows_200}
    by_genus, by_latin = build_family_map(rows_200, rows_ext)
    latin_by_genus = build_latin_family_by_genus(rows_ext)

    # 이름 → id. `비슷한꽃`(이름 문자열)을 id로 바꾸는 데 쓴다.
    # ⚠️ NFC로 정규화해서 넣는다 — 안 하면 이름이 같아 보이는데 안 맞는다.
    name_to_id = {}
    for r in rows_ext:
        name = nfc(r["이름"])
        fid = int(r["도감번호"])
        if name in name_to_id:
            raise ValueError("이름이 중복된다: %s (%d, %d)" % (name, name_to_id[name], fid))
        name_to_id[name] = fid

    flowers = []
    stats = {}
    dangling = []

    for index, row in enumerate(rows_ext, start=1):
        fid = int(row["도감번호"])
        if fid != index:
            raise ValueError("도감번호가 1~%d 연속이 아니다: %d번째가 %d" % (TOTAL_COUNT, index, fid))

        src = human.get(fid)
        if src is not None:
            # ① 사람이 정한 값. **확장 CSV의 같은 행을 쓰지 않는다** (계약 1-1-a).
            #    덮으면 Top-1 77.0% → 50.0%로 떨어진다(실측).
            months = parse_bloom_months(src["개화기"])
            bloom_source = BLOOM_HUMAN
            bloom_label = src["개화기"].strip()
            season = _enum(SEASON, src["계절"], "계절", fid)
            color = src["대표색"].strip()
            rarity = _enum(RARITY, src["희귀도"], "희귀도", fid)
            habitat = src["주요서식지"].strip()
            difficulty = _enum(DIFFICULTY, src["AI난이도"], "AI난이도", fid)
            similar_names = _split_similar(src["비슷한꽃"])
            name = nfc(src["이름"])
            scientific = src["학명"].strip()
            family = nfc(src["과"])
            illust_batch = int(src["일러스트배치"] or 0)
        else:
            months, bloom_source, ref_month = bloom_for_new_species(row)
            # 🔴 개화기를 모르면 `bloom_label`을 **만들지 않는다.** 빈 문자열이고,
            #    화면은 계약 1-2-c대로 그 절을 뺀다. `한 해 내내 피어요`로 채우면
            #    "모른다"를 "일 년 내내 핀다"로 바꿔 말하는 것이다.
            bloom_label = bloom_label_for(months, bloom_source)
            # 대표월의 **종류에 맞는 경계표**를 쓴다. 섞으면 76.4% → 68.6%로 떨어진다.
            season = _season_from(ref_month, bloom_source)
            color = ""
            rarity = RARITY[DEFAULT_RARITY_NEW]
            habitat = ""
            difficulty = DIFFICULTY[DEFAULT_DIFFICULTY_NEW]
            similar_names = []
            name = nfc(row["이름"])
            scientific = row["학명"].strip()
            family = korean_family(row, by_genus, by_latin, latin_by_genus)
            illust_batch = 0

        if not scientific:
            raise ValueError("도감번호 %d: 학명이 비어 있다" % fid)
        if not months:
            # 여기 오면 cascade에 구멍이 있다는 뜻이다. 조용히 넘기면 영구 제외다.
            raise ValueError("도감번호 %d: 개화월이 비었다 (출처 %s)" % (fid, bloom_source))
        if not family:
            raise ValueError("도감번호 %d: 과가 비어 있다" % fid)

        similar_ids = []
        for nm in similar_names:
            target = name_to_id.get(nfc(nm))
            if target is None:
                dangling.append((fid, nm))
                continue
            similar_ids.append(target)

        stats[bloom_source] = stats.get(bloom_source, 0) + 1
        flowers.append({
            "id": fid,
            "name": name,
            "scientific_name": scientific,
            "family": family,
            "bloom_months": months,
            "bloom_label": bloom_label,
            "bloom_source": bloom_source,
            "season": season,
            "color": color,
            "rarity": rarity,
            "habitat": habitat,
            "ai_difficulty": difficulty,
            "similar_flower_ids": similar_ids,
            "similar_flower_names": similar_names,
            "illust_batch": illust_batch,
        })

    _verify(flowers)
    return flowers, stats, dangling


def _season_from(ref_month, bloom_source):
    """대표월 → 계절 enum. **출처에 맞는 경계표를 골라 쓴다.**

    ⑤ `unknown`은 대표월이 없다 → **`None`(JSON `null`)** 을 준다. 계약 1-1-d.

    🔴 **빈 문자열이 아니라 `null`이다.** 계절은 양쪽에서 enum이라
       `""`를 내리면 iOS는 디코딩에서 죽고 Android는
       `error("알 수 없는 season: ")`로 죽는다. `null`은 "모른다"를 뜻하고
       옵셔널로 받는다 — 그러면 **화면 06 필터에 걸리지 않는다**(그게 맞다.
       근거 없는 계절로 필터에 넣으면 봄 목록에 여름 꽃이 섞인다).
    """
    if ref_month is None:
        return None
    table = SEASON_OF_PEAK_MONTH if bloom_source == BLOOM_PEAK else SEASON_OF_FIRST_MONTH
    return SEASON[table[ref_month]]


def _label_from_months(months):
    """개화월 배열 → 표시 문자열. `[5,6]` → `5~6월`, `[9]` → `9월`.

    ⚠️ 연속 구간일 때만 부른다. cascade ②③이 만드는 값은 원형 연속 구간이다.

    🔴 **12개월이면 표기를 내지 않는다**(`None`). `1~12월`이라고 쓰면 화면에
       `1~12월에 피는 꽃`이 되는데, 그건 "일 년 내내 핀다"는 **단정**이다.
       실제 뜻은 "관찰 기록이 모든 달에 있다"이고 상록수에서 흔하다 —
       그 둘은 다르다. 계약 1-2-c의 원칙(모르는 것을 아는 것처럼 말하지 않는다)을
       그대로 적용해 **절을 뺀다.**
    """
    if len(months) >= 12:
        return None
    if len(months) == 1:
        return "%d월" % months[0]
    return "%d~%d월" % (months[0], months[-1])


def _verify(flowers):
    """🔴 조용히 틀리는 것만 검사한다. 여기서 안 잡으면 화면에서 보인다."""
    problems = []
    if len(flowers) != TOTAL_COUNT:
        problems.append("종수가 %d이어야 한다. 실제 %d" % (TOTAL_COUNT, len(flowers)))

    ids = [f["id"] for f in flowers]
    if ids != list(range(1, len(flowers) + 1)):
        problems.append("도감번호가 1~%d 연속이 아니다" % len(flowers))

    for f in flowers:
        # 계약 1-1-c: 사람이 읽는 컬럼에 빈 문자열을 내리면 **깨진 문장**이 나온다.
        # `color`·`habitat`·`bloom_label`은 값이 없을 수 있고(근거가 없다),
        # 그 경우 **화면이 절을 빼도록** 되어 있다. 그러니 빈 문자열 자체는 허용하되
        # **빈 문자열이 화면에 그대로 보간되지 않는지**는 Kotlin/Swift 쪽 검사가 센다.
        # 여기서 막는 것은 **논리에 쓰이는 값**의 빈칸이다.
        if not f["bloom_months"]:
            problems.append("%d %s 개화월이 비었다" % (f["id"], f["name"]))
        if not f["scientific_name"]:
            problems.append("%d %s 학명이 비었다" % (f["id"], f["name"]))
        if not f["family"]:
            problems.append("%d %s 과가 비었다" % (f["id"], f["name"]))
        if f["rarity"] not in set(RARITY.values()):
            problems.append("%d %s rarity가 계약 밖이다: %r" % (f["id"], f["name"], f["rarity"]))
        if f["ai_difficulty"] not in set(DIFFICULTY.values()):
            problems.append("%d %s ai_difficulty가 계약 밖이다: %r"
                            % (f["id"], f["name"], f["ai_difficulty"]))
        # `season`은 null이거나 계약 enum이다. **빈 문자열은 허용하지 않는다** —
        # 그걸 내리면 양쪽 클라이언트가 디코딩에서 죽는다(계약 1-1-d).
        if f["season"] is not None and f["season"] not in set(SEASON.values()):
            problems.append("%d %s season이 계약 밖이다: %r" % (f["id"], f["name"], f["season"]))
        # 🔴 `bloom_label`이 있는데 개화월을 모르는 출처면 모순이다.
        if f["bloom_source"] in BLOOM_SOURCES_WITHOUT_LABEL and f["bloom_label"]:
            problems.append("%d %s bloom_source=%s인데 bloom_label이 있다"
                            % (f["id"], f["name"], f["bloom_source"]))
        # 🔴 **개화월에 중복이 있으면 안 된다.** 필터는 `month in months`라서
        #    중복이 있어도 옳게 돌지만, `bloom_label`이 `months[0]`~`months[-1]`을
        #    읽어 **`6~4월`** 같은 표기를 만든다. 실제로 9종이 23개월이었다.
        if len(f["bloom_months"]) != len(set(f["bloom_months"])):
            problems.append("%d %s 개화월에 중복이 있다: %s"
                            % (f["id"], f["name"], f["bloom_months"]))
        if len(f["bloom_months"]) > 12:
            problems.append("%d %s 개화월이 %d개월이다 (12를 넘을 수 없다)"
                            % (f["id"], f["name"], len(f["bloom_months"])))
        # 🔴 **표기가 12개월을 뜻하지 않는지 센다.** `1~12월`은 "일 년 내내 핀다"는
        #    단정인데 근거는 "모든 달에 관찰 기록이 있다"뿐이다(계약 1-2-c와 같은 원칙).
        if f["bloom_label"] and len(f["bloom_months"]) >= 12:
            problems.append("%d %s 개화월 12개월인데 표기가 있다: %r"
                            % (f["id"], f["name"], f["bloom_label"]))
        # 표기를 낼 수 있는데 비어 있으면 화면이 이유 없이 절을 뺀다.
        expected_label = bloom_label_for(f["bloom_months"], f["bloom_source"])
        if f["bloom_source"] != BLOOM_HUMAN and f["bloom_label"] != expected_label:
            problems.append("%d %s bloom_label이 %r인데 규칙은 %r을 준다"
                            % (f["id"], f["name"], f["bloom_label"], expected_label))
        # 사람이 정한 200종은 **전부** 채워져 있어야 한다. 하나라도 비면 초안이 섞인 것이다.
        if f["bloom_source"] == BLOOM_HUMAN and not (f["color"] and f["habitat"]):
            problems.append("%d %s 사람이 정한 종인데 색·서식지가 비었다" % (f["id"], f["name"]))

    if problems:
        raise ValueError("마스터 무결성 위반 %d건:\n  - %s"
                         % (len(problems), "\n  - ".join(problems[:20])))


def format_stats(stats, flowers):
    """출처별 종수를 사람이 읽을 한 줄로. **세어서 보고한다** — 계약 1-2-b.

    🔴 "개화기를 모르는 종"은 **출처에서 계산하지 않고 라벨을 직접 센다.**
       원래 `peak_window + unknown`으로 더했는데(1,017), 그건 **틀린 수였다**:
       12개월 관찰된 상록수 9종은 `observed`인데도 라벨을 낼 수 없다
       (`1~12월`은 "일 년 내내 핀다"는 단정이라 못 쓴다 — 계약 1-2-c).
       실제로 절이 빠지는 종은 **1,026종**이다.
       파생값으로 보고하면 규칙이 바뀔 때 **보고만 조용히 낡는다.**
    """
    order = [BLOOM_HUMAN, BLOOM_DRAFT, BLOOM_OBSERVED, BLOOM_PEAK, BLOOM_UNKNOWN]
    parts = ["%s %d" % (k, stats.get(k, 0)) for k in order if stats.get(k, 0)]
    todo = sum(1 for f in flowers if not f["bloom_label"])
    return "출처 {%s} · 🔴 개화기 표기가 없는 종 %d (화면에서 절이 빠진다 · 사람이 채울 목록)" % (
        ", ".join(parts), todo)
