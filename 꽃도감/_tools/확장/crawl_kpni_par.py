# -*- coding: utf-8 -*-
"""KPNI 병렬 수집 — 스레드 6개. 결과는 락으로 보호해 매 속 저장."""
import json, os, sys, time, threading
from concurrent.futures import ThreadPoolExecutor
import kpni

cand = json.load(open('candidates_occ.json'))
live = [x for x in cand if x['occ'] > 0]
genera = sorted({x['canonicalName'].split()[0] for x in live})

OUT = 'kpni_raw.json'
lock = threading.Lock()
done = json.load(open(OUT)) if os.path.exists(OUT) else {}
todo = [g for g in genera if g not in done]
print(f'남은 속 {len(todo)}', flush=True)
fails = []
n = [0]

def work(g):
    for attempt in range(3):
        try:
            h = kpni.fetch(g, timeout=90)
            tot = kpni.total(h)
            rows = kpni.parse(h)
            page = 2
            while tot and len(rows) < tot and page <= 12:
                more = kpni.parse(kpni.fetch(g, page=page, timeout=90))
                if not more:
                    break
                rows += more
                page += 1
            with lock:
                done[g] = {'total': tot, 'rows': rows}
                n[0] += 1
                if n[0] % 20 == 0:
                    json.dump(done, open(OUT, 'w'), ensure_ascii=False)
                    print(f'  {n[0]}/{len(todo)}  누적 {len(done)}', flush=True)
            return
        except Exception as e:
            if attempt == 2:
                with lock:
                    fails.append(g)
                    print(f'  FAIL {g}: {type(e).__name__}', flush=True)
            else:
                time.sleep(4)

with ThreadPoolExecutor(max_workers=6) as ex:
    list(ex.map(work, todo))

json.dump(done, open(OUT, 'w'), ensure_ascii=False)
if fails:
    json.dump(fails, open('kpni_fails.json', 'w'), ensure_ascii=False)
print(f'\n완료 속 {len(done)} / 레코드 {sum(len(v["rows"]) for v in done.values()):,} / 실패 {len(fails)}')
