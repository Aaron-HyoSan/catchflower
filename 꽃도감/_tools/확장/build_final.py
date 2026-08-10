# -*- coding: utf-8 -*-
"""한국 꽃 목록 최종 조립 — 3차.

1·2차에서 밟은 함정을 전부 고친다.

  ① 기존 200종의 학명이 낡아 63종이 신규 목록에서 빠졌다.
     → 국명(KPNI 정명)으로 역매칭해 살리고, 낡은 학명은 별도 열에 남긴다.
  ② 원예·재배종(튤립·팬지·페튜니아 등)은 GBIF 야생 관찰기록이 없다.
     → 제외하지 않고 `자생구분` 축으로 분리한다. 사용자는 화단에서 이것도 찍는다.
  ③ 🔴 학명을 속+종소명 2단어(binomial)로 자르면 원종과 변종·품종이 한 칸에 들어간다.
     KPNI 정명만 세어도 **한 binomial에 정명 국명이 2개 이상인 것이 955건**이다.
     예) `Viola albida`     → 원종 정명 '태백제비꽃' · 변종 정명 '남산제비꽃'
         `Lythrum salicaria` → 원종 '털부처꽃'      · 변종 '부처꽃'
         `Syringa oblata`    → 원종 '참수수꽃다리'   · 변종 '수수꽃다리'
     2차에서 이 셋을 "학명 복구"로 오판해 **도감의 부처꽃을 털부처꽃으로 바꿔 놨다.**
     → binomial을 국명의 근거로 쓰지 않는다. `국명 → 정명 학명(전체)`이 원본이고,
       비교·기록은 저자명만 뗀 학명 전체(`var.`·`subsp.` 포함)로 한다.
  ④ 🔴 GBIF canonicalName은 사실상 전부 binomial(2,778개 중 3단어는 1개)이다.
     그래서 GBIF 종 ↔ KPNI 변종 정명은 **원리상 1:1로 붙지 않는다.**
     관찰수·개화월은 binomial 단위로만 유효하므로, 변종 정명을 쓸 때는
     `관찰기준학명` 열에 실제로 어떤 학명으로 집계했는지 적는다.
"""
import json, re, csv, io, collections, bisect

kp = json.load(open('kpni_raw.json'))
cand = json.load(open('candidates_occ.json'))
mc = json.load(open('month_counts_obs.json'))
full = json.load(open('kr_checklist.json'))
live = [x for x in cand if x['occ'] > 0]

RANK = ('var.', 'subsp.', 'ssp.', 'f.', 'forma', 'cv.')
RANK_RE = re.compile(r"\b(var|subsp|ssp|f|cv|forma)\.")
# 🔴 품종은 `'Variegatum'` 처럼 **따옴표로 감싼 한 덩어리**다. 그냥 아포스트로피를
#    찾으면 **저자명**이 걸린다 — `Caragana sinica (Buc'hoz) Rehder` ·
#    `Oenothera rosea L'Hér.` · `Arabidopsis lyrata (L.) O'Kane`.
#    이 5종이 원종인데 품종으로 오판돼 목록에서 배제돼 있었다.
CULTIVAR_RE = re.compile(r"['‘’][^'‘’]*['’]")


def is_species_level(sci):
    """원종(종 수준)인가 — 변종·아종·품종이 아닌가."""
    return not (RANK_RE.search(sci) or CULTIVAR_RE.search(sci))


def canon(s):
    """속+종소명 2단어. GBIF와 맞추는 **집계 키**로만 쓴다(국명 근거로 쓰면 ③)."""
    out = []
    for w in s.replace('×', ' ').split():
        if len(out) >= 2:
            break
        if re.match(r'^[A-Za-z\-]+$', w):
            out.append(w)
    return ' '.join(out) if len(out) == 2 else None


def sciname(s):
    """저자명을 떼고 학명만 남긴다 — `var.`·`subsp.`·`f.`·품종명은 **남긴다**.

    이게 종의 신원이다. canon()으로 자르면 원종과 변종이 같아진다(③).
    품종명(`'Variegatum'`)까지 잘라 버리면 품종이 원종으로 승격된다.
    """
    cul = CULTIVAR_RE.search(s)
    toks = s.replace('×', ' ').split()
    keep = []
    for i, w in enumerate(toks):
        lw = w.lower()
        if i < 2:
            keep.append(w)
        elif lw in RANK:
            keep.append(lw)
        elif keep and keep[-1] in RANK:
            keep.append(w)
        else:
            break
    out = ' '.join(keep)
    return f'{out} {cul.group(0)}' if cul else out


# ── KPNI 인덱스
#   ko2acc : 국명 → [(학명전체, binomial, 종분류, 원종여부)]   정명만
#   syn2ko : 이명 binomial → (국명, 종분류)
#   bi2sp  : binomial → 원종 정명 국명 (유일할 때만. 2개 이상은 187건 → 안 쓴다)
ko2acc = collections.defaultdict(list)
syn2ko = {}
bi_sp = collections.defaultdict(list)     # binomial → [(국명, 종분류, 잡종여부)]
bi_infra = collections.defaultdict(list)  # binomial → [(학명, 국명, 종분류)]
for g, v in kp.items():
    for kind, status, sci, ko, *_ in v['rows']:
        c = canon(sci)
        if not c:
            continue
        issp = is_species_level(sci)
        if status == '정명':
            ko2acc[ko].append((sciname(sci), c, kind, issp))
            if issp:
                bi_sp[c].append((ko, kind, '×' in sci))
            else:
                bi_infra[c].append((sciname(sci), ko, kind))
        elif issp:
            syn2ko.setdefault(c, (ko, kind))


def _pick_sp(l):
    """binomial의 원종 정명 국명 — 유일할 때만.

    🔴 잡종이 원종 자리를 모호하게 만든다. `Prunus salicina Lindl. L. × P. armeniaca`
       ("플럼코트")가 canon()에서 `Prunus salicina`가 되어 '자두나무'와 2개가 됐고,
       그래서 모호로 빠지면서 그 자리를 아래 변종 fallback이 **품종
       "자두나무 '슈페리어'"** 로 채웠다. 잡종을 뺀 뒤 유일하면 그것을 쓴다.
    """
    kos = {x[0] for x in l}
    if len(kos) == 1:
        return next(iter(kos))
    pure = {x[0] for x in l if not x[2]}
    return next(iter(pure)) if len(pure) == 1 else None


bi2sp = {c: k for c, l in bi_sp.items() if (k := _pick_sp(l))}

# ko2acc[ko][0]을 쓰므로 원종 → 변종·아종 → 품종 순으로 세운다.
# (같은 국명에 여러 정명이 붙는 경우 197건. 국명이 품종까지 포함해 다르므로
#  실제로 순서가 뒤집히는 국명은 지금 데이터에 0건이지만, 조립이 첫 행에
#  의존하므로 순서를 데이터 크롤 순서에 맡기지 않는다.)
for _ko, _l in ko2acc.items():
    _l.sort(key=lambda r: (0 if r[3] else (2 if CULTIVAR_RE.search(r[0]) else 1), len(r[0])))

occ_by_sci = {x['canonicalName']: x for x in cand}
occs = sorted(x['occ'] for x in live)
nub_by_sci = {x['canonicalName']: x.get('nubKey') for x in cand}


def rarity(v):
    if v == 0:
        return ''
    p = bisect.bisect_left(occs, v) / len(occs)
    return '흔함' if p >= 0.66 else ('보통' if p >= 0.25 else '귀함')


THRESH, MIN_OBS = 0.35, 30
SEASON = {1: '겨울', 2: '겨울', 3: '봄', 4: '봄', 5: '봄', 6: '여름',
          7: '여름', 8: '여름', 9: '가을', 10: '가을', 11: '가을', 12: '겨울'}


def bloom(nub):
    if not nub:
        return None, 0, ''
    p = {int(m): per[str(nub)] for m, per in mc.items() if per.get(str(nub))}
    if not p:
        return None, 0, ''
    tot = sum(p.values())
    if tot < MIN_OBS:
        return None, tot, '/'.join(f'{k}:{p.get(k,0)}' for k in range(1, 13))
    mx = max(p.values())
    keep = {m for m in range(1, 13) if p.get(m, 0) >= mx * THRESH}
    peak = max(p, key=lambda m: p[m])
    run = [peak]
    m = peak
    for _ in range(11):
        m = 12 if m == 1 else m - 1
        if m in keep:
            run.insert(0, m)
        else:
            break
    m = peak
    for _ in range(11):
        m = 1 if m == 12 else m + 1
        if m in keep:
            run.append(m)
        else:
            break
    prof = '/'.join(f'{k}:{p.get(k,0)}' for k in range(1, 13))
    # 🔴 6개월 초과 = 꽃이 아닌 관찰(열매·단풍·잎)이 섞인 것. 개화월로 쓸 수 없다.
    if len(run) > 6:
        return None, tot, prof
    return run, tot, prof


def fmt(run):
    return '' if not run else (f'{run[0]}월' if len(run) == 1 else f'{run[0]}~{run[-1]}월')


fam_by_sci = {}
for x in full:
    fam_by_sci.setdefault(x['canonicalName'], x.get('family', ''))

old = list(csv.DictReader(io.open(
    '/Users/kimhyosan/game-project/꽃도감/꽃목록_200종.csv', encoding='utf-8-sig')))

rows = {}        # key: 학명 전체(sciname)
# 🔴 list에 append하면 **같은 정정이 두 경로에서 두 번** 들어간다(마타리: GBIF 이명
#    해소 + 기존 200종 경로). 문서에 "34건"으로 적히면 실제보다 부풀려진다.
#    (국명, 낡은학명) 키로 모아 유일하게 만든다.
stale_map = {}   # (국명, 도감/GBIF 학명) → (도감 학명, KPNI 정명 학명, 국명, 사유)


def note_stale(oldn, new, ko, why):
    stale_map.setdefault((ko, oldn), (oldn, new, ko, why))


def add(sci, bino, ko, kind, src, old_row=None, old_sci=''):
    """sci = 학명 전체(변종 포함, 종의 신원) · bino = GBIF 집계에 쓸 binomial."""
    if sci in rows:
        if old_row and not rows[sci]['기존200종']:
            rows[sci].update(_from_old(old_row, old_sci))
        return
    x = occ_by_sci.get(bino)
    occ = x['occ'] if x else 0
    run, tot, prof = bloom(nub_by_sci.get(bino))
    peak = run[len(run) // 2] if run else None
    rows[sci] = {
        '도감번호': '', '이름': ko, 'KPNI국명': ko, '학명': sci,
        # 변종 정명은 GBIF에 없어서 원종 binomial로 집계했다(④).
        # 학명과 같으면 빈칸 — 다르면 관찰수·개화월의 실제 집계 대상이다.
        '관찰기준학명': '' if bino == sci else bino,
        '과': (x.get('family') if x else fam_by_sci.get(bino, '')) or '',
        '자생구분': kind or '', '관찰건수': occ,
        '개화기_초안': fmt(run), '계절_초안': SEASON.get(peak, '') if peak else '',
        '희귀도_초안': rarity(occ), '관찰합계': tot, '관찰월분포': prof,
        '출처': src, '기존200종': '', '기존학명_낡음': '',
        '대표색': '', '주요서식지': '', 'AI난이도': '', '비슷한꽃': '',
    }
    if old_row:
        rows[sci].update(_from_old(old_row, old_sci))


def _from_old(o, old_sci):
    d = {'도감번호': o['도감번호'], '이름': o['이름'], '기존200종': 'Y',
         '대표색': o['대표색'], '주요서식지': o['주요서식지'],
         'AI난이도': o['AI난이도'], '비슷한꽃': o['비슷한꽃']}
    if old_sci:
        d['기존학명_낡음'] = old_sci
    return d


# ── 1) 관찰기록 있는 야생종 (GBIF는 binomial 단위다)
for x in sorted(live, key=lambda v: -v['occ']):
    bino = x['canonicalName']
    if bino in bi2sp:                             # 원종 정명이 유일 → 확정
        ko = bi2sp[bino]
        rec = [r for r in ko2acc[ko] if r[1] == bino and r[3]][0]
        add(rec[0], bino, ko, rec[2], 'GBIF관찰+KPNI정명')
    elif bino in syn2ko:                          # 이명 → 정명 국명으로 해소
        ko, kind = syn2ko[bino]
        accs = ko2acc.get(ko, [])
        if accs:
            add(accs[0][0], accs[0][1], ko, accs[0][2], 'GBIF관찰+KPNI이명해소')
            if accs[0][0] != bino:
                note_stale(bino, accs[0][0], ko, 'GBIF학명이 이명')
    elif len(bi_infra.get(bino, ())) == 1:        # 원종 정명이 없고 변종만 정명(30종)
        sci2, ko, kind = bi_infra[bino][0]
        add(sci2, bino, ko, kind, 'GBIF관찰+KPNI변종정명')

# ── 2) 기존 200종 전량 보존
#   🔴 순서가 결함의 원인이었다. **도감 이름(국명)이 KPNI 정명이면 그것이 먼저다.**
#   학명 binomial을 먼저 보면 `Viola albida`가 남산제비꽃을 태백제비꽃으로 바꾼다(③).
recovered = 0
for o in old:
    c = canon(o['학명'])
    mine = sciname(o['학명'])
    ko = o['이름']
    target = bino = kind = None
    if ko in ko2acc:                              # ① 국명이 KPNI 정명이다 — 최우선
        target, bino, kind, _ = ko2acc[ko][0]
        if target != mine:
            recovered += 1
            note_stale(mine, target, ko,
                       '도감 학명이 원종(정명은 변종)' if c == bino else '도감 학명이 낡음')
    syn_ko = None
    if not target and c and c in syn2ko:          # ② 학명이 이명 → 정명 국명 경유
        k2 = syn2ko[c][0]
        if k2 in ko2acc:
            target, bino, kind, _ = ko2acc[k2][0]
            syn_ko = k2                            # 이 경로의 KPNI 국명은 도감 이름이 아니다
            # 🔴 "정정"은 **학명이 실제로 바뀐 것**만이다. 도감 이름(부추꽃)이 KPNI에
            #    없어서 이 경로로 왔을 뿐, 학명 `Allium tuberosum`은 이미 정명이었다.
            #    경로를 근거로 세면 고칠 게 없는 종이 정정 목록에 들어간다.
            if target != mine:
                recovered += 1
                note_stale(mine, target, k2, '도감 학명이 이명')
    kpni_ko = syn_ko or (ko if target else None)   # KPNI 국명 — 없으면 빈칸으로 둔다
    if not target and c and c in bi2sp:            # ③ 학명 binomial의 원종 정명
        kpni_ko = bi2sp[c]
        rec = [r for r in ko2acc[kpni_ko] if r[1] == c and r[3]][0]
        target, bino, kind = rec[0], c, rec[2]
    if not target:                                 # ④ KPNI에 없다(원예 속칭 등)
        # 🔴 `KPNI국명`에 도감 이름을 넣으면 **출처가 KPNI인 것처럼 보인다.**
        #    벚꽃·복사꽃·안개꽃·버베나가 그랬다. KPNI에 없으면 비운다.
        target, bino, kind, kpni_ko = mine or ko, c or ko, '미확인', ''
    if target in rows:
        rows[target].update(_from_old(o, o['학명'] if mine != target else ''))
    else:
        add(target, bino, kpni_ko, kind, '기존200종(관찰기록없음)', o,
            o['학명'] if mine != target else '')

out = list(rows.values())

# ── 3) 🔴 원종과 변종이 **같은 관찰수를 나눠 갖지 않게** 한다.
#   GBIF는 binomial 단위라 `Viola albida` 7,180건이 태백제비꽃(원종)과
#   남산제비꽃(변종) 두 행에 그대로 들어갔다. 8건이 그랬다.
#   합계가 부풀 뿐 아니라 **희귀도가 분위값이라 두 종 다 '흔함'이 된다** —
#   남산제비꽃 단독 관찰수는 모른다. 원종에 남기고 변종 쪽은 비운다.
shared = collections.defaultdict(list)
for r in out:
    shared[r['관찰기준학명'] or r['학명']].append(r)
dedup = 0
for bino, l in shared.items():
    if len(l) < 2:
        continue
    for r in l:
        if r['관찰기준학명']:                 # 변종 쪽 — 원종의 수치를 쓰고 있었다
            r['관찰건수'] = 0
            r['희귀도_초안'] = ''
            # 개화기·관찰월은 남긴다(같은 종의 개화기는 실질적으로 같다).
            # 다만 근거가 원종임을 관찰기준학명이 이미 밝히고 있다.
            r['출처'] += '·관찰수는원종중복이라제외'
            dedup += 1

out.sort(key=lambda r: (-int(r['관찰건수'] or 0), r['학명']))

OUT = '/Users/kimhyosan/game-project/꽃도감/꽃목록_후보_2000종.csv'
with io.open(OUT, 'w', encoding='utf-8-sig', newline='') as f:
    w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
    w.writeheader()
    w.writerows(out)

print(f'출력 {len(out):,}종 → {OUT}')
print(f'기존 200종 보존 {sum(1 for r in out if r["기존200종"]=="Y")}/200  (학명 정정 {recovered}종)')
print()
print('자생구분 ', dict(collections.Counter(r['자생구분'] for r in out)))
print('희귀도   ', dict(collections.Counter(r['희귀도_초안'] for r in out)))
print('계절     ', dict(collections.Counter(r['계절_초안'] for r in out)))
print('개화기초안', sum(1 for r in out if r['개화기_초안']))
print('변종정명(관찰은 원종으로 집계)', sum(1 for r in out if r['관찰기준학명']))
print(f'원종과 관찰수 중복이라 관찰수 비운 변종 {dedup}종')
stale = sorted(stale_map.values(), key=lambda x: (x[3], x[2]))
json.dump(stale, open('stale_names.json', 'w'), ensure_ascii=False, indent=1)
print(f'\n학명 불일치 {len(stale)}건 → stale_names.json')
