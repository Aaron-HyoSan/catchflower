# -*- coding: utf-8 -*-
"""B-4 **수집 그룹** — 사진으로 못 가르는 종을 도감 한 칸으로 묶는다.

## 왜 필요한가 (실측)

PlantNet이 민들레 사진 40장 중 **29장에 `Taraxacum sect. Taraxacum`** 을 준다.
속(genus)보다 위 계급을 내는 것은 **"종을 못 가르겠다"는 대답**이고, 실제로
민들레/서양민들레는 **씨앗(총포 뒤집힘)으로 갈라서 꽃 사진으로는 불가능**하다.
그러면 도감에 **원리상 채울 수 없는 칸**이 생긴다 — 사용자는 자기가 못 찍는 줄 안다.

그리고 우리 파이프라인은 그 답을 **임의로** 한 종에 배정하고 있었다. 캐시 200장을
재생해 1순위의 출처를 세어 봤다(유료 호출 0건):

| 1순위가 정해진 방식 | 사진 수 |
|---|---|
| 학명이 도감에 정확히 있었다 (유일) | 123 |
| 속 폴백 — 제철 후보가 1종뿐 | 6 |
| **속 폴백 — 제철 후보가 2종 이상이라 `도감번호 최솟값`으로 골랐다** | **56 (30%)** |

즉 **10장 중 3장은 화면에 뜬 종 이름이 "정해진 것"이 아니었다.**

## 🔴 그래서 무엇을 하고 무엇을 안 하는가

| | |
|---|---|
| 한다 | 도감 **칸**을 묶는다. 등록·도감·후보 중복제거가 **대표종**으로 간다 |
| **안 한다** | **종을 지우지 않는다.** 2,057행은 그대로고 `id`도 **재배치하지 않는다** |
| **안 한다** | **학명 색인을 줄이지 않는다.** 멤버 학명도 계속 맞는다 — 안 그러면 인식이 **줄어든다** |
| **안 한다** | 멤버의 `bloom_months`를 대표종에 합치지 않는다 (아래 ⚠️) |

🔴 **`id`를 재배치하면 이미 등록된 기록이 다른 꽃이 된다** — `discoveries.flower_id`가
   FK다(공유계약 1-5). 그래서 도감 칸 수는 **2,057 - 멤버 13 = 2,044**로 줄지만
   번호는 1~2,057이 그대로 남는다. `2044`를 세는 곳은 [SLOT_COUNT]다.

⚠️ **멤버의 개화월을 대표종에 union 하지 않는 이유.** 후보 집합은 여전히
   **종 단위**(`bloomingIn(month)`)로 만든다 — 8월에 서양민들레(3~10월)는 후보에
   들어가고, 매칭된 다음에 대표종 31로 접힌다. 대표종의 3~5월을 8월까지 늘리면
   **후보 집합만 넓어지고**(다른 종의 자리를 먹는다) 얻는 것이 없다.
   🔴 반대로 **접기를 후보 만들기 앞에 두면** 서양민들레가 8월 후보에서 사라져
   **인식이 조용히 죽는다.** 순서가 설계다: `후보(종) → 매칭(종) → 접기(그룹)`.
   ⚠️ 남는 부작용 하나: 화면 05가 대표종의 개화기(`3~5월`)를 그대로 보여준다.
      8월에 등록되면 어긋나 보인다 — **문구·데이터 결정이라 오너 확인 대기**로 적어 둔다.

## 그룹을 늘릴 때

**이 표에만** 추가하고 `python3 꽃도감/_tools/build_app_data.py`를 다시 돌린다.
🔴 **속(genus)으로 자동 묶지 않는다.** 재 봤더니 같은 속 2종 이상이 **1,635종
(79%)** 이라 자동 규칙은 도감의 5분의 4를 삼킨다 — 제비꽃 41종이 팬지 한 칸이 된다.
오너가 `유지`라고 명시한 것들(고마리·개망초)도 같이 삼킨다.
그래서 **손으로 적고, 손으로 적은 목록을 검사한다**([self_check]).
"""

import unicodedata


def _nfc(s):
    return unicodedata.normalize("NFC", s)


# ── 그룹 표 ────────────────────────────────────────────────────────────
# `rep`  = 도감에 남는 칸(대표종). 사용자가 보는 이름·일러스트·개화기가 이것이다.
# `members` = 대표종 칸으로 접히는 종. 행은 남고 학명 매칭도 계속 된다.
#
# ⚠️ **이름을 같이 적는다.** id만 적으면 CSV가 한 줄 밀렸을 때 **엉뚱한 꽃이
#    조용히 합쳐진다** — [self_check]가 이름을 도감과 대조해서 막는다.
GROUPS = [
    {
        "rep": (31, "민들레"),
        "members": [(32, "서양민들레")],
        "reason": "PlantNet이 40장 중 29장에 `Taraxacum sect. Taraxacum`(종보다 위 계급)을 준다. "
                  "총포가 뒤집혔는지로 가르는 것이라 꽃 사진으로는 원리상 불가능하다.",
    },
    {
        "rep": (38, "별꽃"),
        "members": [(39, "쇠별꽃"), (40, "개별꽃")],
        "reason": "흰 별 모양 5장(끝이 갈라져 10장처럼 보인다)이 같다. "
                  "암술대 3개/5개로 가르는 것이라 접사가 아니면 안 보인다.",
    },
    {
        "rep": (73, "팬지"),
        "members": [(74, "비올라")],
        "reason": "같은 원예 교배종 계열이고 **크기로만** 구분한다 — 사진에 기준 물체가 없으면 못 잰다. "
                  "⚠️ 자생 제비꽃 41종은 **묶지 않는다**(꽃 모양이 실제로 다르다).",
    },
    {
        "rep": (102, "망초"),
        "members": [(103, "실망초")],
        "reason": "혀꽃이 거의 없는 같은 얼굴이고 총포 털로 가른다. "
                  "⚠️ 개망초(혀꽃이 흰 국화 모양)는 눈으로 갈라지므로 **묶지 않는다**(오너 `유지`).",
    },
    {
        "rep": (105, "금계국"),
        "members": [(104, "큰금계국"), (106, "기생초")],
        "reason": "노란 8장 혀꽃이 같고, 잎 모양·꽃 지름·가운데 붉은 무늬 유무로 가른다 — "
                  "군락 사진에서는 셋이 섞여 자란다.",
    },
    {
        "rep": (139, "여뀌"),
        "members": [(140, "개여뀌"), (142, "이삭여뀌")],
        "reason": "가늘고 긴 이삭 꽃차례가 같다. "
                  "⚠️ 고마리(삼각형 잎·연분홍 뭉치)는 **묶지 않는다**(오너 `유지`).",
    },
    {
        "rep": (161, "메리골드"),
        "members": [(162, "만수국")],
        "reason": "국내에서 둘을 같은 이름으로 판다(아프리칸/프렌치). 사진으로 꽃 지름이 안 나오면 못 가른다.",
    },
    {
        "rep": (184, "쑥부쟁이"),
        "members": [(185, "개미취"), (186, "미국쑥부쟁이"), (187, "참취")],
        "reason": "연자주~흰 혀꽃 + 노란 통꽃의 같은 얼굴이다. 잎·총포로 가른다. "
                  "🔴 미국쑥부쟁이는 **속이 다르다**(`Symphyotrichum`) — 그래서 속 자동 묶기로는 "
                  "만들 수 없는 그룹이고, 손으로 적어야 하는 이유다. "
                  "⚠️ 벌개미취(꽃이 크고 잎이 길다)는 **묶지 않는다**.",
    },
]

# 도감 칸 수 = 2,057 - 접히는 멤버 수. `GamePolicy.DEX_SLOT_COUNT`·계약 1-5와 같아야 한다.
MEMBER_COUNT = sum(len(g["members"]) for g in GROUPS)
SLOT_COUNT = 2057 - MEMBER_COUNT


def member_to_rep(groups=None):
    """멤버 id → 대표 id. **대표종은 들어 있지 않다**(자기 자신은 호출부 기본값)."""
    out = {}
    for g in (GROUPS if groups is None else groups):
        rep_id = g["rep"][0]
        for member_id, _ in g["members"]:
            out[member_id] = rep_id
    return out


def group_id_of(flower_id, mapping=None):
    """`collect_group_id` — 멤버는 대표 id, 나머지는 **자기 id**."""
    m = mapping if mapping is not None else member_to_rep()
    return m.get(flower_id, flower_id)


def self_check(name_by_id, groups=None):
    """🔴 조용히 틀리는 것만 검사한다. 문제가 있으면 `ValueError`.

    `name_by_id`: 도감번호 → 이름(NFC). 호출부가 CSV에서 만든 것을 넘긴다.
    `groups`: 검사할 표. **테스트가 일부러 틀린 표를 넣어 이 검사가 빨개지는지 본다** —
    안 그러면 "검사가 있다"는 믿음만 남는다(`test_collect_groups.py`).

    막는 것:
      ① id가 도감에 없다 (오타·범위 밖)
      ② **id와 이름이 안 맞는다** ← CSV가 밀리면 여기서만 잡힌다
      ③ 한 종이 두 그룹에 들어 있다 (등록이 어디로 갈지 정해지지 않는다)
      ④ 대표종이 다른 그룹의 멤버다 (사슬 — 한 번 접기로 안 끝난다)
      ⑤ 이유가 비었다 (다음 사람이 왜 합쳤는지 모르면 되돌린다)
      ⑥ [SLOT_COUNT]가 실제 멤버 수와 안 맞는다
    """
    table = GROUPS if groups is None else groups
    problems = []
    seen = {}          # id → 어느 그룹에서 봤나
    rep_ids = set()

    for g in table:
        entries = [("대표", g["rep"])] + [("멤버", m) for m in g["members"]]
        rep_name = g["rep"][1]
        rep_ids.add(g["rep"][0])

        if not str(g.get("reason", "")).strip():
            problems.append("%s 그룹: `reason`이 비었다 — 왜 합쳤는지 적는다" % rep_name)
        if not g["members"]:
            problems.append("%s 그룹: 멤버가 없다 — 그룹이 아니다" % rep_name)

        for kind, (fid, name) in entries:
            actual = name_by_id.get(fid)
            if actual is None:
                problems.append("%s 그룹 %s %d(%s): 도감에 없는 번호다" % (rep_name, kind, fid, name))
                continue
            if _nfc(actual) != _nfc(name):
                # ② 여기가 이 검사의 핵심이다.
                problems.append(
                    "%s 그룹 %s %d: 표에는 `%s`인데 도감은 `%s`다 — CSV가 바뀌었다"
                    % (rep_name, kind, fid, name, actual))
            if fid in seen:
                problems.append("%d(%s)이 두 그룹에 있다: %s / %s"
                                % (fid, name, seen[fid], rep_name))
            else:
                seen[fid] = rep_name

    mapping = member_to_rep(table)
    for rep_id in sorted(rep_ids):
        if rep_id in mapping:
            problems.append("대표종 %d이 다른 그룹의 멤버다 — 접기가 사슬이 된다" % rep_id)

    members = sum(len(g["members"]) for g in table)
    # ⑥ 이 모듈의 상수와 실제가 갈리는지. **표를 주입한 테스트에서는 재지 않는다** —
    #    일부러 틀린 표를 넣었으니 여기까지 걸리면 무엇이 잡혔는지 구분이 안 된다.
    if groups is None and SLOT_COUNT != len(name_by_id) - members:
        problems.append("SLOT_COUNT %d이 실제(%d - 멤버 %d = %d)와 다르다"
                        % (SLOT_COUNT, len(name_by_id), members,
                           len(name_by_id) - members))

    if problems:
        raise ValueError("수집 그룹 표가 틀렸다:\n- " + "\n- ".join(problems))

    return {
        "groups": len(table),
        "members": members,
        "slots": len(name_by_id) - members,
    }


def outside_report(flowers, limit=12):
    """**그룹 밖에 남은 같은 속 2종 이상**을 세어 돌려준다 — 실패시키지 않는다.

    🔴 이 함수가 있는 이유: 그룹 표는 손으로 적은 목록이라 **빠뜨려도 증상이 없다.**
       "묶었다"는 만족감만 남고 도감 대부분은 그대로다. 그래서 **얼마나 남았는지**를
       숫자로 뽑아 보고한다(오너가 `난이도 상 나머지`를 판단할 재료다).

    돌려주는 것: `{"clusters": n, "species": n, "top": [(속, 종수, [이름 몇 개…]), …]}`

    ⚠️ `top`의 이름 목록은 **잘려 있다.** 그래서 종수를 **따로** 돌려준다 —
       `len(names)`로 세면 6에서 멈춰 "6종뿐"으로 읽힌다(내가 한 번 그렇게 찍었다).
    """
    mapping = member_to_rep()
    by_genus = {}
    for f in flowers:
        if f["id"] in mapping:
            continue                      # 이미 접힌 종은 세지 않는다
        genus = f["scientific_name"].strip().split(" ")[0]
        if not genus:
            continue
        by_genus.setdefault(genus, []).append(f["name"])

    clusters = {g: names for g, names in by_genus.items() if len(names) >= 2}
    ordered = sorted(clusters.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    return {
        "clusters": len(clusters),
        "species": sum(len(v) for v in clusters.values()),
        "top": [(g, len(names), sorted(names)[:6]) for g, names in ordered[:limit]],
    }
