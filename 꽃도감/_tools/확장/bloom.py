# -*- coding: utf-8 -*-
"""관찰 월분포 → 개화월 구간 추정.

규칙
  1. HUMAN_OBSERVATION 만 쓴다 (표본은 채집일이 개화기와 무관).
  2. 최다 월을 중심으로, 최댓값의 THRESH 이상인 연속 구간을 개화월로 본다.
  3. 12월↔1월을 잇는다 (동백 12~4월).
  4. 관찰 30건 미만이면 추정하지 않는다 (None) — 적은 표본은 한 달에 몰린다.
"""
import json

THRESH = 0.35
MIN_OBS = 30


def profile(month_counts, nub_key):
    """→ {월: 건수} 또는 None"""
    p = {}
    for mo, per in month_counts.items():
        v = per.get(str(nub_key))
        if v:
            p[int(mo)] = v
    return p or None


def estimate(p):
    """→ (개화월 리스트, 총관찰, 신뢰) """
    if not p:
        return None, 0, 'none'
    tot = sum(p.values())
    if tot < MIN_OBS:
        return None, tot, 'low'
    mx = max(p.values())
    keep = {m for m in range(1, 13) if p.get(m, 0) >= mx * THRESH}
    if not keep:
        return None, tot, 'low'
    # 최다월에서 원형으로 확장해 연속 구간만 남긴다
    peak = max(p, key=lambda m: p[m])
    run = [peak]
    m = peak
    for _ in range(11):                      # 뒤로
        m = 12 if m == 1 else m - 1
        if m in keep:
            run.insert(0, m)
        else:
            break
    m = peak
    for _ in range(11):                      # 앞으로
        m = 1 if m == 12 else m + 1
        if m in keep:
            run.append(m)
        else:
            break
    conf = 'high' if tot >= 200 else 'mid'
    return run, tot, conf


def fmt(run):
    if not run:
        return ''
    if len(run) == 1:
        return f'{run[0]}월'
    return f'{run[0]}~{run[-1]}월'


if __name__ == '__main__':
    mc = json.load(open('month_counts_obs.json'))
    cand = json.load(open('candidates_occ.json'))
    byname = {x['canonicalName']: x for x in cand}
    KNOWN = {
        'Forsythia koreana': '3~4월', 'Rhododendron mucronulatum': '3~4월',
        'Prunus mume': '2~4월', 'Camellia japonica': '12~4월',
        'Convallaria keiskei': '5월', 'Lycoris radiata': '9월',
        'Cosmos bipinnatus': '9~10월', 'Hepatica asiatica': '3~4월',
        'Taraxacum officinale': '3~5월', 'Magnolia kobus': '3~4월',
        'Rosa multiflora': '5월', 'Hibiscus syriacus': '7~9월',
        'Chelidonium majus': '5~8월', 'Trifolium repens': '6~7월',
    }
    ok = 0
    print(f"{'학명':28s} {'실제':8s} {'추정':10s} {'관찰':>6s} 신뢰  판정")
    for sci, exp in KNOWN.items():
        x = byname.get(sci)
        if not x:
            print(f'{sci:28s} {exp:8s} — 후보에 없음')
            continue
        p = profile(mc, x.get('nubKey'))
        run, tot, conf = estimate(p)
        got = fmt(run)
        # 겹치면 성공으로 본다 (구간 추정이므로)
        def months(s):
            s = s.replace('월', '')
            if '~' in s:
                a, b = map(int, s.split('~'))
                return {(a + i - 1) % 12 + 1 for i in range(((b - a) % 12) + 1)}
            return {int(s)}
        hit = bool(run) and bool(months(exp) & set(run))
        ok += hit
        print(f'{sci:28s} {exp:8s} {got:10s} {tot:6d} {conf:5s} {"○" if hit else "✕"}')
    print(f'\n겹침 성공 {ok}/{len(KNOWN)}')
