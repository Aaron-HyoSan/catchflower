# -*- coding: utf-8 -*-
"""한국 관속식물 4,083종 → '사용자가 꽃으로 인식해 찍는 종'만 남긴다.

제외 근거는 "꽃이 없는 것"이 아니라 **꽃이 사용자 눈에 꽃으로 안 보이는 것**이다.
벼과·사초과는 꽃이 있지만 풍매화라 화피가 없어 초록 이삭으로 보인다.
"""
import json, collections, csv, sys

d = json.load(open('kr_checklist.json'))

# ── 1) 속씨식물만 (양치·나자식물 제외: 꽃이 없다)
ang = [x for x in d if x.get('phylum') == 'Angiospermae']

# ── 2) 풍매화·화피 없음 = 화면에서 꽃으로 안 보이는 과
WIND_POLLINATED = {
    'Poaceae',        # 벼과 307 — 이삭
    'Cyperaceae',     # 사초과 304 — 이삭
    'Juncaceae',      # 골풀과 24
    'Typhaceae',      # 부들과
    'Sparganiaceae',  # 흑삼릉과
    'Fagaceae',       # 참나무과 21 — 꼬리모양 수꽃
    'Betulaceae',     # 자작나무과 17
    'Ulmaceae',       # 느릅나무과 18
    'Juglandaceae',   # 가래나무과
    'Moraceae',       # 뽕나무과
    'Cannabaceae',
    'Urticaceae',     # 쐐기풀과 34
    'Chenopodiaceae', # 명아주과 32
    'Amaranthaceae',  # 비름과 16
    'Plantaginaceae', # 질경이과 — 이삭형
    'Ceratophyllaceae', 'Callitrichaceae', 'Zosteraceae', 'Potamogetonaceae',
    'Najadaceae', 'Ruppiaceae', 'Zannichelliaceae', 'Hydrocharitaceae',
    'Lemnaceae', 'Salviniaceae', 'Azollaceae',   # 수중·부유
    'Santalaceae', 'Loranthaceae', 'Viscaceae',  # 기생·꽃 미세
    'Aristolochiaceae',
}

# ── 3) 꽃이 1~2mm 수준이라 촬영 대상이 못 되는 과
TOO_SMALL = {
    'Euphorbiaceae',   # 대극과 30 — 배상꽃차례
    'Cuscutaceae', 'Elatinaceae', 'Callitrichaceae',
    'Haloragaceae', 'Haloragidaceae', 'Hippuridaceae',
}

excluded_family = WIND_POLLINATED | TOO_SMALL

kept, dropped = [], []
for x in ang:
    fam = x.get('family')
    if fam in excluded_family:
        dropped.append((x, fam))
    else:
        kept.append(x)

print(f'속씨식물      {len(ang):5d}종')
print(f'제외(풍매·미소) {len(dropped):5d}종  ({len(excluded_family)}과 지정)')
print(f'남은 후보     {len(kept):5d}종 / {len(set(x.get("family") for x in kept))}과')
print()
dc = collections.Counter(f for _, f in dropped)
print('제외 상위:', dc.most_common(10))

json.dump(kept, open('candidates.json', 'w'), ensure_ascii=False)
