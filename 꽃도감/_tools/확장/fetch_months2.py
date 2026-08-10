# -*- coding: utf-8 -*-
"""월분포 재수집 — basisOfRecord=HUMAN_OBSERVATION 만.

🔴 표본(PRESERVED_SPECIMEN 89.9만건)을 섞으면 개화월이 무너진다.
   동백(12~4월)이 2·4·8월로 퍼져 보였던 원인. 표본 채집일은 개화기와 무관하다.
"""
import json, urllib.request, time, os

UA = {'User-Agent': 'catchflower-dex/1.0'}
OUT = 'month_counts_obs.json'
res = json.load(open(OUT)) if os.path.exists(OUT) else {}

for mo in range(1, 13):
    if str(mo) in res:
        continue
    per = {}
    for off in range(0, 5000, 1000):
        u = ('https://api.gbif.org/v1/occurrence/search?country=KR&taxonKey=7707728'
             f'&month={mo}&basisOfRecord=HUMAN_OBSERVATION'
             f'&limit=0&facet=speciesKey&facetLimit=1000&facetOffset={off}')
        d = None
        for attempt in range(3):
            try:
                d = json.load(urllib.request.urlopen(
                    urllib.request.Request(u, headers=UA), timeout=120))
                break
            except Exception as e:
                if attempt == 2:
                    print(f'  FAIL m={mo} off={off}: {e}', flush=True)
                else:
                    time.sleep(5)
        if not d:
            continue
        f = d['facets'][0]['counts'] if d['facets'] else []
        if not f:
            break
        for c in f:
            per[c['name']] = c['count']
    res[str(mo)] = per
    json.dump(res, open(OUT, 'w'))
    print(f'{mo:2d}월  종 {len(per)}', flush=True)
print('완료', sorted(int(k) for k in res))
