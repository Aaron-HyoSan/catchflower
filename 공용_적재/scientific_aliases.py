#!/usr/bin/env python3
"""학명 **별칭** — PlantNet이 돌려주는 다른 이름을 우리 도감 종으로 잇는다.

계약 1-1-e. **`scientific_name`은 한 자도 고치지 않는다.** 별칭만 더한다.

─────────────────────────────────────────────────────────────
🔴 **왜 "고치기"가 아니라 "더하기"인가 — 실측이 두 번 내 판단을 뒤집었다.**

**첫 판단(틀렸다):** `꽃도감/_tools/확장/stale_names.json`에 33건이 있는 것을 보고
"낡은 학명 29건이 판별 정확도를 깎는다 → 새 이름으로 바꾸면 된다"고 적었다.
실제 PlantNet 응답 200장(iOS 실측 캐시)으로 대조하니 **바꾸면 3건이 깨진다**:
PlantNet이 **옛 이름을** 돌려주고 있었다(애기해바라기 0.397 · 민들레 0.008 ·
원추천인국 0.034). 특히 원추천인국은 새 이름(`Rudbeckia bicolor`)이 200장 응답에
**한 번도 안 나온다** — 바꾸면 지금 맞던 것이 그냥 사라진다.
→ 그래서 **덮지 않고 더하는** 방향으로 뒤집었다. 양쪽 이름이 다 맞으므로
  **어느 쪽이 옳은지 몰라도 안전하다.**

**두 번째 판단(이것도 틀렸다):** 그 다음 `stale_names.json`의 33건을
"속이 다른 4건 + 종이 다른 19건 + 정규화하면 같은 10건"으로 분류해 25건을 넣었다.
**적재가 red를 냈다** — 5건이 "자기 학명과 같다"였다. 원인을 파 보니:

🔴 **`stale_names.json`의 이름 변경은 확장 CSV에 이미 반영돼 있었다.**
   `꽃목록_확장_2057종.csv`의 `학명` 칸은 **33건 전부 새 이름**이다.
   그런데 계약 1-1-a가 **id 1~200은 사람이 정한 `꽃목록_200종.csv`를 쓴다**고
   정해 놨다(초안으로 덮으면 Top-1이 77%→50%로 떨어진다는 실측 때문이다).
   그래서 앱이 실제로 싣는 `flowers.json`은:

   | | 앱이 쓰는 학명 | 별칭이 필요한가 | 건수 |
   |---|---|---|---|
   | id ≤ 200 | **옛 이름**(200 CSV가 이긴다) | 🔴 **필요하다** | **16** |
   | id ≤ 200 | 옛 이름이지만 `var.`/`subsp.`만 차이 | 불필요(정규화가 같다) | 10 |
   | id ≤ 200 | 🔴 **넣으면 다른 종을 빼앗는다** | **금지** | **2** |
   | id > 200 | **새 이름**(확장 CSV) | 불필요(이미 새 이름) | 5 |

   즉 **별칭이 필요한 것은 33건이 아니라 16건이고, 전부 id ≤ 200이다.**
   내가 처음 만든 "속이 다른 4건 / 종이 다른 19건" 분류는 **확장 CSV의 새 이름을
   도감 학명으로 착각**하고 센 것이었다. 방향이 거꾸로였다.
   ⚠️ 그리고 이건 **낡은 학명 문제가 아니다** — 사람이 정한 값을 지키는 대가로
      생긴 것이고, 계약 1-1-a는 그대로 두는 것이 맞다(그쪽 이득이 훨씬 크다).

**세 번째로 틀린 것(이건 더 나빴다):** 위 18건(그때는 18이었다)을 넣고 나서 "별칭이 도감의 **다른**
종의 학명과 겹치는가"를 세 봤다 — **2건이 겹쳤다.** `Lythrum salicaria`는
**털부처꽃(1909)의 학명 그 자체**이고 `Phedimus aizoon`은 **가는기린초(206)**다.
`take(2)`가 아종·품종 표기를 지우기 때문에 생긴다. 그대로 넣으면 PlantNet이
털부처꽃을 **정확히 맞혀도 부처꽃으로 번역된다** — 별칭이 맞던 답을 빼앗는다.
→ 뺐다(`DO_NOT_ALIAS_AMBIGUOUS`). **지표로는 넣어도 빼도 0 변화다**(그 두 학명은
  실측 200장에 아예 안 나온다) — 근거가 지표가 아니라 **구조**인 판단이다.

─────────────────────────────────────────────────────────────
🔴 **이 별칭이 실제로 무엇을 바꾸는가 — 실측한 대로만 적는다.**

`Top-1`·`Top-3`·화면12 분해는 **한 자리도 안 움직인다**(200종·2,057종 양쪽 확인).
그 이유를 먼저 알아야 이 파일의 값을 오해하지 않는다:

**① 실측 정답 판정이 속(genus) 단위다.** `PlantNetReplayTest`의 `class_to_genera`는
   `Taraxacum` 속의 **6종 중 아무거나** 맞으면 정답으로 센다(daisy는 17종).
   별칭은 "같은 속 안에서 어느 종인가"를 고치는 것이라 **원리상 이 지표에
   나타날 수 없다.** 지표가 안 움직인 것은 "효과가 없다"가 아니라
   **"이 지표는 그걸 재지 않는다"**다.
   ⚠️ **그래서 지표를 근거로 이 파일을 지우면 안 된다.**

**② 이 200장은 흔한 5종(민들레·장미·해바라기·튤립·데이지)뿐이다.**
   별칭 16건 중 **측정되는 속에 든 것은 4건**이고 나머지는 그 종의 사진이
   표본에 아예 없다 — **측정 불가**다.

**그래서 종 단위로 다시 쟀다**(응답 학명 211개 × 12달 = 2,532조합).
번역 결과가 달라지는 것은 **22건**이고, 전부 한 갈래다:

    Erigeron bonariensis    8월   개망초 → 실망초   ← 이게 정답이다
    Erigeron bonariensis    9월   민망초 → 실망초
    Erigeron bonariensis  11·12월   없음 → 실망초

실측 응답에서 이 학명은 **20장/200장에 등장**한다. 지금은 속 fallback이
`개망초`·`민망초`로 보내고 있었다 — **화면에는 꽃 이름이 예쁘게 나오므로 증상이
없다.** 지표로도 화면으로도 안 보이는 결함이고, 이게 이 파일의 유일한 실측 이득이다.
작지만 진짜다. 나머지 15건은 **원리는 같고 표본에 없다.**

⚠️ **`exact` 색인에만 넣는다. 속 색인은 건드리지 않는다.**
   속 색인에도 넣어 본 결과 **노리지 않은 종이 함께 움직였다** — 9월
   `Erigeron annuus`(개망초)와 `Erigeron strigosus`가 `민망초` → `망초`로 바뀌었다.
   별칭이 속의 후보 순서를 바꿔 버린 것이다. exact에만 넣으면 사라진다(24건 → 22건).

🔴 **교배종 `×` 표기는 별칭 대상이 아니다 — 고칠 것이 없었다.**
   응답에 `Chrysanthemum × morifolium` 등 4건이 온다. `normalize`가 `×`를 안 지우니
   앞 두 낱말이 `chrysanthemum ×`가 되어 exact가 실패한다. 나는 이걸 "속조차 안
   맞는다"고 적었는데 **틀렸다** — 속은 첫 낱말이라 정상 추출되고 **속 fallback이
   받는다.** `×`를 지우는 안을 만들어 재 봤더니 **제철 달에서는 4건 전부 변화 없음**
   (국화 10·11월, 모란 5월, 무궁화 7~9월, 붓꽃 5·6월 — 현행도 전부 정답).
   달라지는 17건은 **그 꽃이 안 피는 달**뿐이고, 개화월 필터가 옳게 떨어뜨리는 자리다.
   → **순이득 0이라 넣지 않는다.** `Hibiscus × rosa-sinensis`·`Iris × germanica`는
     `×`를 지워도 exact가 실패한다(도감 학명이 종부터 다르다) — 애초에 못 고친다.

자기검사:
    python3 공용_적재/scientific_aliases.py
"""

import csv
import json
import pathlib
import re
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
STALE_JSON = ROOT / "꽃도감" / "_tools" / "확장" / "stale_names.json"
DEX_200 = ROOT / "꽃도감" / "꽃목록_200종.csv"
DEX_EXT = ROOT / "꽃도감" / "꽃목록_확장_2057종.csv"
REPLAY = (ROOT / "android" / "app" / "src" / "test" / "resources" /
          "plantnet_replay.json")
KOTLIN_INDEX = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" /
                "catchflower" / "app" / "recognizer" / "ScientificNameIndex.kt")


# ─────────────────────────────────────────────────────────────
# 별칭 16건: PlantNet이 줄 수 있는 이름 → 도감의 한글 이름
# ─────────────────────────────────────────────────────────────
#
# **키는 학명, 값은 한글 이름이다.** id로 적지 않는다 — 도감번호는 생성물이고,
# 여기 숫자를 박으면 번호가 바뀔 때 **조용히 다른 꽃을 가리킨다**
# (계약 1-5의 `discoveries.flower_id` 함정과 같은 형태).
#
# 🔴 **오타 난 한글 이름은 예외가 아니라 "별칭이 없는 것"이 된다.** 적재가 이름으로
#    도감을 찾다가 못 찾으면 그 줄을 조용히 건너뛴다 — 파일에는 16줄이 그대로
#    있어서 **넣은 줄 수와 동작하는 줄 수가 갈린다.** 아래 자기검사 ⑤가 막는다.
#    ⚠️ 이 주석을 처음 쓸 때 "자기검사가 대조하므로 red가 난다"고 적었는데
#       **그 검사가 없었다**(돌연변이가 초록으로 통과했다). 이 저장소에서
#       **내 주석이 없는 안전장치를 승인한 세 번째**다. 그래서 검사를 추가했다.
#
# ⚠️ **16건 전부 id ≤ 200이다.** 우연이 아니다 — 계약 1-1-a가 사람이 정한 200종의
#    학명을 지키기 때문에 그쪽만 옛 이름으로 남는다(머리말 표 참고).
#    확장 CSV(id>200)는 이미 새 이름이라 별칭이 필요 없다.
ALIASES = {
    # ── 속(genus)이 달라서 **속 fallback조차 못 타는** 4건 ──
    #    도감 학명의 속이 PlantNet이 주는 속과 달라 완전히 실패한다.
    "Erigeron bonariensis": "실망초",              # id 103 · 도감 Conyza bonariensis
    #  ⬆ **이 한 건만 실측으로 확인됐다** — 200장 중 20장에 등장하고, 지금은
    #    속 fallback이 `개망초`(8월)·`민망초`(9월)로 보낸다. 머리말 표 참고.
    "Erigeron canadensis": "망초",                 # id 102 · 도감 Conyza canadensis
    "Chrysanthemum naktongensis": "구절초",        # id 182 · 도감 Dendranthema zawadskii
    "Ixeridium dentatum": "씀바귀",                # id  45 · 도감 Ixeris dentata

    # ── 속은 같고 종이 다른 12건 ──
    #    속 fallback이 건지지만 그건 "속만 맞다"는 약한 매칭이라, 같은 속의 다른
    #    종이 개화 중이면 **그쪽이 뽑힌다.** 정확히 맞게 한다.
    #    ⚠️ **전부 미측정이다** — 이 200장에 해당 종의 사진이 없다.
    "Taraxacum mongolicum": "민들레",              # id  31 · 도감 Taraxacum platycarpum
    "Vicia sativa": "살갈퀴",                      # id  53 · 도감 Vicia angustifolia
    "Clematis mandshurica": "으아리",              # id 100 · 도감 Clematis terniflora
    "Rudbeckia bicolor": "원추천인국",               # id 107 · 도감 Rudbeckia hirta
    "Oenothera tetragona": "낮달맞이꽃",             # id 116 · 도감 Oenothera speciosa
    "Impatiens textorii": "물봉선",                # id 129 · 도감 Impatiens textori
    "Astilbe chinensis": "노루오줌",                # id 131 · 도감 Astilbe rubra
    "Chrysanthemum seticuspe": "산국",             # id 180 · 도감 Chrysanthemum boreale
    "Aster indicus": "쑥부쟁이",                    # id 184 · 도감 Aster yomena
    "Patrinia serratulifolia": "마타리",            # id 191 · 도감 Patrinia scabiosifolia
    #    아래 2건은 새 이름이 `var.`/`subsp.` 형태다 — **정규화하면 종까지 달라진다**
    #    (`syringa oblata` ≠ `syringa dilatata`). 그래서 필요하다.
    "Syringa oblata var. dilatata": "수수꽃다리",     # id  19 · 도감 Syringa dilatata
    "Mentha arvensis var. piperascens": "박하",     # id 126 · 도감 Mentha canadensis
}

# 🔴 **넣으면 안 되는 별칭 2건 — 넣었더니 "맞던 종"을 빼앗아 갔다.**
#
# 둘 다 `var.`/`subsp.`를 뗀 앞 두 낱말이 **도감의 다른 종의 학명과 정확히 같다.**
#
#   `Lythrum salicaria subsp. anceps` → `lythrum salicaria` = **털부처꽃(1909)의 학명**
#   `Phedimus aizoon var. floribundus` → `phedimus aizoon`  = **가는기린초(206)의 학명**
#
# 즉 별칭을 넣으면 PlantNet이 털부처꽃을 정확히 맞혀도 **부처꽃으로 번역된다.**
# 지금 맞던 것을 빼앗는 쪽이라 순손실이다. `take(2)`가 아종·품종 구분을 지우기
# 때문에 생기는 문제고, `take(3)`으로 늘리는 것은 **훨씬 큰 변경**이라 하지 않는다
# (도감 2,057종 전부의 매칭이 바뀐다 — 이 두 건을 위해 감당할 위험이 아니다).
#
# ⚠️ 실측 응답 200장에 `lythrum salicaria`·`phedimus aizoon`은 **0장** 등장한다.
#    그래서 지표로는 넣어도 빼도 똑같다 — **이 판단의 근거는 지표가 아니라
#    "같은 이름이 두 종을 가리킨다"는 구조**다. 아래 자기검사 ②가 지킨다.
DO_NOT_ALIAS_AMBIGUOUS = {
    "Lythrum salicaria subsp. anceps": ("부처꽃", "털부처꽃"),      # 128 vs 1909
    "Phedimus aizoon var. floribundus": ("기린초", "가는기린초"),   # 133 vs 206
}

# 넣지 **않은** 것과 이유. 목록으로 남겨야 "빠뜨린 것"과 구별된다.
#
# 🔴 **①** `ScientificNameIndex.normalize`가 `var.`/`subsp.`/`f.`를 지우고 앞 두
#    낱말만 쓰기 때문에 **이미 같은 값으로 정규화되는** 10건. 넣어도 동작이 1비트도
#    안 바뀐다 — 넣으면 "이만큼 고쳤다"는 착각만 생긴다.
NO_ALIAS_NEEDED_SAME_NORM = {
    "Spiraea prunifolia f. simpliciflora": "조팝나무",          # id  18
    "Viola albida var. chaerophylloides": "남산제비꽃",          # id  34
    "Cirsium japonicum var. maackii": "엉겅퀴",                # id  49
    "Lotus corniculatus var. japonica": "벌노랑이",             # id  55
    "Dianthus barbatus var. asiaticus": "수염패랭이꽃",           # id 119
    "Prunella vulgaris subsp. asiatica": "꿀풀",              # id 122
    "Clinopodium chinense var. parviflorum": "층층이꽃",        # id 125
    "Lysimachia vulgaris var. davurica": "좁쌀풀",             # id 136
    "Adenophora triphylla var. japonica": "잔대",             # id 146
    "Hepatica asiatica": "노루귀",                             # id  59 · 완전 동일
}

# 🔴 **②** 앱이 **이미 새 이름을 싣고 있는** 5건(id > 200). 확장 CSV가 원본이라
#    처음부터 새 이름이다 — 별칭을 넣으면 자기 학명과 같아진다(적재가 red를 낸다).
NO_ALIAS_NEEDED_ALREADY_NEW = {
    "Gagea terracianoana": "애기중의무릇",                        # id 1409
    "Helianthus debilis subsp. cucumerifolius": "애기해바라기",   # id 1413
    "Albizia macrophylla": "왕자귀나무",                         # id 1483
    "Hosta nakaiana": "일월비비추",                              # id 1575
    "Allium alatoscapum": "참두메부추",                          # id 1756
}

# 🔴 **바꾸지 말라고 실측이 말한 3건.** 누가 나중에 "낡은 학명을 정리하자"며
#    `꽃목록_200종.csv`의 학명을 덮으려 할 때 이 표가 근거다.
#    ⚠️ 세 건 다 위 `ALIASES`/`NO_ALIAS_NEEDED_*`에도 들어 있다 — **모순이 아니다.**
#       별칭으로 새 이름을 **더하는** 것은 안전하고, 옛 이름을 **지우는** 것이 위험하다.
DO_NOT_REPLACE = {
    # 도감 학명(지금 맞는 것): (GBIF가 권하는 이름, 200장 최고점, 새 이름도 응답에 있나)
    "Helianthus debilis": ("Helianthus debilis subsp. cucumerifolius", 0.39665, True),
    "Taraxacum platycarpum": ("Taraxacum mongolicum", 0.00792, True),
    # ⬇ 이게 가장 위험하다: 새 이름이 200장 응답에 **한 번도 안 나온다.**
    #   바꾸면 지금 맞던 0.034가 그냥 사라진다.
    "Rudbeckia hirta": ("Rudbeckia bicolor", 0.03427, False),
}


def nfc(s):
    """한글 이름 비교는 **NFC로** 한다 — macOS는 NFD로 주고 JSON은 NFC다."""
    return unicodedata.normalize("NFC", s).strip()


def normalize(name):
    """`ScientificNameIndex.normalize`와 **같은 결과를 내야 한다.**

    ⚠️ 한쪽만 바뀌면 **별칭이 조용히 안 먹는다** — 앱은 다른 꽃 이름을 예쁘게
       내놓고, 그건 "PlantNet이 그렇게 준 것"과 화면에서 구별되지 않는다.
       그래서 아래 자기검사 ④가 Kotlin 규칙을 재현해 **결과로** 대조한다.

    🔴 **`var.`·`subsp.` 치환은 실은 아무 일도 하지 않는다** (실측).
       `take(2)`가 앞 두 낱말만 남기는데 식물 학명에서 `var.`/`subsp.`는
       **항상 세 번째 낱말 이후**에 온다. 도감·응답·stale을 합친 **학명 2,187개에서
       치환을 아예 빼도 결과가 한 건도 안 바뀐다.** 진짜 일하는 것은 `take(2)`다.
       → Kotlin을 건드리지 않는다(동작이 같고, 방어적으로 남길 값이다).
    """
    n = nfc(name).lower().replace("var.", " ").replace("subsp.", " ")
    return " ".join(n.split()[:2])


def alias_pairs():
    """`(정규화된 학명, 한글 이름)` 목록. 적재 스크립트가 쓴다."""
    return [(normalize(k), v) for k, v in ALIASES.items()]


def _read_dex():
    """앱이 **실제로 싣는** 학명을 계약 1-1-a 규칙대로 재현한다.

    🔴 확장 CSV만 읽으면 안 된다 — 그러면 id ≤ 200이 새 이름으로 보여서
       **별칭 16건이 전부 "불필요"로 판정된다**(내가 처음 그렇게 세어서 틀렸다).
    """
    with DEX_EXT.open(encoding="utf-8-sig", newline="") as fh:
        ext = {int(r["도감번호"]): (nfc(r["이름"]), nfc(r["학명"]))
               for r in csv.DictReader(fh) if r.get("도감번호")}
    with DEX_200.open(encoding="utf-8-sig", newline="") as fh:
        for r in csv.DictReader(fh):
            fid = int(r["도감번호"])
            # 계약 1-1-a: id 1~200은 사람이 정한 값이 이긴다.
            ext[fid] = (nfc(r["이름"]), nfc(r["학명"]))
    return ext


def self_check():
    problems = []
    counts = {}
    dex = _read_dex()
    by_name = {name: (fid, sci) for fid, (name, sci) in dex.items()}
    counts["도감"] = len(dex)

    # ① 두 별칭이 정규화 후 같아지면 어느 꽃인지 알 수 없다.
    seen = {}
    for raw, kr in ALIASES.items():
        n = normalize(raw)
        if n in seen and seen[n] != kr:
            problems.append(f"같은 학명 `{n}`이 두 꽃을 가리킨다: {seen[n]} · {kr}")
        seen[n] = kr

    # ② 🔴 **별칭이 자기 학명과 같으면 무의미하다.** 넣은 줄 수가 부풀어
    #    "이만큼 고쳤다"는 착각이 생긴다(적재도 같은 검사를 한다).
    for raw, kr in ALIASES.items():
        hit = by_name.get(nfc(kr))
        if hit and normalize(hit[1]) == normalize(raw):
            problems.append(
                f"ALIASES의 `{raw}`({kr})이 도감 학명 `{hit[1]}`과 정규화 후 같다 —"
                " 아무 일도 하지 않는다(NO_ALIAS_NEEDED_*로 옮긴다)")

    # ②-b 🔴 **별칭이 도감의 다른 종의 학명을 빼앗는가.** 이게 가장 위험한 실패다 —
    #     **지금 맞던 종이 틀리게 된다.** 그리고 화면에는 다른 꽃 이름이 예쁘게
    #     나오므로 증상이 없다(`Lythrum salicaria`가 실제로 그랬다: 털부처꽃을
    #     정확히 맞혀도 부처꽃으로 번역됐다).
    #     ⚠️ **`ALIASES`에 넣지 않은 것도 함께 검사한다** — `DO_NOT_ALIAS_AMBIGUOUS`에
    #        적어 둔 이유가 참인지 봐야, 도감이 바뀌어 이유가 사라졌을 때 알 수 있다.
    by_sci = {}
    for fid, (name, sci) in dex.items():
        by_sci.setdefault(normalize(sci), []).append((fid, name))
    for raw, kr in ALIASES.items():
        owners = [(fid, nm) for fid, nm in by_sci.get(normalize(raw), [])
                  if nm != nfc(kr)]
        if owners:
            desc = " · ".join(f"{fid} {nm}" for fid, nm in owners)
            problems.append(
                f"ALIASES의 `{raw}` → `{kr}`이 **다른 종의 학명을 빼앗는다**: {desc} —"
                " 그 종을 정확히 맞혀도 이 별칭이 가로챈다(DO_NOT_ALIAS_AMBIGUOUS로 옮긴다)")
    for raw, (kr, taken) in DO_NOT_ALIAS_AMBIGUOUS.items():
        owners = {nm for _, nm in by_sci.get(normalize(raw), [])}
        if nfc(taken) not in owners:
            problems.append(
                f"DO_NOT_ALIAS_AMBIGUOUS의 `{raw}`가 `{taken}`을 빼앗는다고 적혀 있는데"
                f" 도감에서 그 학명을 쓰는 종은 {sorted(owners) or '없다'} —"
                " 이유가 낡았다(별칭으로 옮길 수 있는지 다시 본다)")

    # ③ 🔴 **`NO_ALIAS_NEEDED_*`의 주장을 검사한다.** "불필요"가 참이어야 한다 —
    #    아니면 그건 빠뜨린 것이고, 그 종은 지금 판별에 실패하고 있다.
    stale = json.loads(STALE_JSON.read_text(encoding="utf-8"))
    counts["stale"] = len(stale)
    for label, source in (("SAME_NORM", NO_ALIAS_NEEDED_SAME_NORM),
                          ("ALREADY_NEW", NO_ALIAS_NEEDED_ALREADY_NEW)):
        for new_name, kr in source.items():
            hit = by_name.get(nfc(kr))
            if hit is None:
                problems.append(f"NO_ALIAS_NEEDED_{label}의 `{kr}`이 도감에 없다")
            elif normalize(hit[1]) != normalize(new_name):
                problems.append(
                    f"NO_ALIAS_NEEDED_{label}에 `{new_name}`({kr})이 있는데 도감 학명은 "
                    f"`{hit[1]}`이다 — 정규화가 다르므로 **별칭이 필요하다**")

    # ④ 🔴 **stale 33건을 전부 덮는가.** 빠지면 "다 처리했다"가 거짓이 된다.
    covered = ({normalize(k) for k in ALIASES}
               | {normalize(k) for k in NO_ALIAS_NEEDED_SAME_NORM}
               | {normalize(k) for k in NO_ALIAS_NEEDED_ALREADY_NEW}
               | {normalize(k) for k in DO_NOT_ALIAS_AMBIGUOUS})
    for old, new, kr, why in stale:
        if normalize(new) not in covered:
            problems.append(f"stale {kr}({new})이 어디에도 없다 — 판단을 안 적었다")
    total = (len(ALIASES) + len(NO_ALIAS_NEEDED_SAME_NORM)
             + len(NO_ALIAS_NEEDED_ALREADY_NEW) + len(DO_NOT_ALIAS_AMBIGUOUS))
    if total != len(stale):
        problems.append(f"네 목록 합이 {total}인데 stale은 {len(stale)}건이다 —"
                        " 중복이거나 stale에 없는 것을 넣었다")

    # ⑤ 🔴 **한글 이름이 도감에 실제로 있는가.** 없으면 적재가 그 별칭을 조용히
    #    건너뛴다 — 파일에는 남아 있고 화면에는 증상이 없다.
    ambiguous_kr = {k: v[0] for k, v in DO_NOT_ALIAS_AMBIGUOUS.items()}
    for label, source in (("ALIASES", ALIASES),
                          ("NO_ALIAS_NEEDED_SAME_NORM", NO_ALIAS_NEEDED_SAME_NORM),
                          ("NO_ALIAS_NEEDED_ALREADY_NEW", NO_ALIAS_NEEDED_ALREADY_NEW),
                          ("DO_NOT_ALIAS_AMBIGUOUS", ambiguous_kr)):
        for raw, kr in source.items():
            if nfc(kr) not in by_name:
                problems.append(
                    f"{label}의 `{raw}` → **`{kr}`이 도감에 없다** —"
                    " 적재가 이 별칭을 조용히 건너뛴다(오타인가)")

    # ⑥ 🔴 **정규화가 Kotlin과 갈렸는가 — 목록이 아니라 `결과`로 본다.**
    #
    #    ⚠️ 처음엔 Kotlin의 `.replace("…")` **목록을 집합 비교**했다. 그리고
    #       **첫 red가 검사 잘못이었다**: 파이썬에만 있던 `" f. "` 때문에 빨개졌는데
    #       학명 2,187개로 재 보니 **결과가 다른 것이 0건**이었다. 무해한 표현
    #       차이에 빨개지는 검사는 **고칠 곳이 없는 red**라 결국 꺼지게 된다.
    #    ⚠️ 주석을 먼저 지운다 — 문자열이 "있는지"로 보면 KDoc의 예시도 코드로
    #       읽는다(이 저장소에서 세 번 당했다).
    if not KOTLIN_INDEX.exists():
        problems.append(f"Kotlin 색인을 찾을 수 없다: {KOTLIN_INDEX}")
    else:
        kt_src = KOTLIN_INDEX.read_text(encoding="utf-8")
        kt_src = re.sub(r"/\*.*?\*/", "", kt_src, flags=re.S)   # KDoc·블록 주석
        kt_src = re.sub(r"//[^\n]*", "", kt_src)                # 줄 주석
        kt_repl = re.findall(r'\.replace\("([^"]*)",\s*"([^"]*)"\)', kt_src)
        kt_take = re.search(r"\.take\((\d+)\)", kt_src)

        if not kt_take:
            problems.append("Kotlin에서 `.take(n)`을 못 찾았다 — 색인 구현이 바뀌었다")
        elif ".lowercase()" not in kt_src:
            problems.append("Kotlin에 `.lowercase()`가 없다 — 대소문자 규칙이 갈렸다")
        else:
            take_n = int(kt_take.group(1))

            def kotlin_normalize(name):
                n = nfc(name).lower()
                for a, b in kt_repl:
                    n = n.replace(a, b)
                return " ".join(n.split()[:take_n])

            # 대조 입력: 도감 전체 + 실측 응답 + stale 양쪽 이름 + 별칭 키.
            # 🔴 별칭 목록만 넣으면 18개는 통과하는데 도감 2,057종에서 갈리는
            #    규칙을 놓친다(그게 더 나쁘다).
            probe = set(ALIASES) | set(NO_ALIAS_NEEDED_SAME_NORM)
            probe |= set(NO_ALIAS_NEEDED_ALREADY_NEW) | set(DO_NOT_REPLACE)
            probe |= set(DO_NOT_ALIAS_AMBIGUOUS)
            probe |= {sci for _, sci in dex.values()}
            for old, new, kr, why in stale:
                probe |= {old, new}
            if REPLAY.exists():
                fx = json.loads(REPLAY.read_text(encoding="utf-8"))
                probe |= {r["name"] for ph in fx["photos"] for r in ph["results"]}
            else:
                problems.append(f"재생 픽스처가 없다: {REPLAY} — 응답 학명을 못 대본다")

            mismatch = [n for n in sorted(probe)
                        if kotlin_normalize(n) != normalize(n)]
            if mismatch:
                problems.append(
                    f"정규화 결과가 Kotlin과 다르다 ({len(mismatch)}/{len(probe)}건) —"
                    " **별칭이 조용히 안 먹는다**. 예: " + ", ".join(
                        f"`{n}` → Kotlin `{kotlin_normalize(n)}` vs 여기 `{normalize(n)}`"
                        for n in mismatch[:3]))
            counts["정규화 대조"] = len(probe)

    return problems, counts


def main():
    problems, counts = self_check()
    print(f"별칭 {len(ALIASES)}개 · 불필요 {len(NO_ALIAS_NEEDED_SAME_NORM)}"
          f"+{len(NO_ALIAS_NEEDED_ALREADY_NEW)}개"
          f" · 모호해서 금지 {len(DO_NOT_ALIAS_AMBIGUOUS)}개"
          f" · 교체금지 {len(DO_NOT_REPLACE)}개")
    print("  대조: " + " · ".join(f"{k} {v}개" for k, v in counts.items()))

    # 🔴 **센 개수를 확인한다.** 대조 입력이 비면 위 검사들이 아무것도 안 보고
    #    통과한다(이 저장소가 반복해 당한 실패).
    if counts.get("도감", 0) < 2000:
        problems.append(f"도감을 {counts.get('도감')}종만 읽었다 — CSV 열 이름이 바뀌었나")
    if counts.get("정규화 대조", 0) < 2000:
        problems.append(f"정규화 대조 입력이 {counts.get('정규화 대조')}개뿐이다")

    if problems:
        print(f"\n🔴 문제 {len(problems)}건")
        for p in problems:
            print("   -", p)
        return 1
    print("판정: PASS — stale 33건 전부에 판단이 있고, 정규화가 Kotlin과 같다")
    return 0


if __name__ == "__main__":
    sys.exit(main())
