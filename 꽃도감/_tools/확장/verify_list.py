# -*- coding: utf-8 -*-
"""꽃목록 CSV 불변식 검사.

각 검사에 **어떤 경우에 빨개지는가**를 적는다. 못 적는 검사는 빼는 게 낫다.
"""
import csv, io, json, re, collections, sys

CSV = '/Users/kimhyosan/game-project/꽃도감/꽃목록_후보_2000종.csv'
r = list(csv.DictReader(io.open(CSV, encoding='utf-8-sig')))
old = list(csv.DictReader(io.open(
    '/Users/kimhyosan/game-project/꽃도감/꽃목록_200종.csv', encoding='utf-8-sig')))
raw = json.load(io.open('/Users/kimhyosan/game-project/꽃도감/_tools/확장/kpni_raw.json',
                        encoding='utf-8'))
fail = []


def chk(label, ok, redwhen, detail=''):
    print(('  OK   ' if ok else '  FAIL ') + label)
    if not ok:
        fail.append((label, redwhen, detail))
        if detail:
            print('         ' + detail[:400])


# ── KPNI 정명 인덱스를 검사 쪽에서 **독립적으로** 다시 만든다.
#    build_final.py의 함수를 import하면 같은 버그를 같이 물려받는다.
# 검사는 조립 코드와 **독립적으로** 판정해야 한다. 품종은 따옴표로 감싼 덩어리이고
# 그냥 아포스트로피를 찾으면 저자명(`L'Hér.` · `O'Kane`)이 걸린다.
CUL = re.compile(r"['‘’][^'‘’]*['’]")
RANKQ = re.compile(r"\b(var|subsp|ssp|f|cv|forma)\.")
acc_ko = collections.defaultdict(set)     # 국명 → {학명(저자 제외)}
acc_sci = {}                              # 학명 → 국명
syn_sci = collections.defaultdict(set)
RANK = ('var.', 'subsp.', 'ssp.', 'f.', 'forma', 'cv.')


def sciname(s):
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
    return ' '.join(keep)


for g, v in raw.items():
    for kind, status, sci, ko, *_ in v['rows']:
        n = sciname(sci)
        if status == '정명':
            acc_ko[ko].add(n)
            acc_sci.setdefault(n, ko)
        else:
            syn_sci[n].add(ko)

print(f'대상 {len(r)}행 · KPNI 정명 국명 {len(acc_ko)}개\n')

# 1 ─ 학명 중복. 빨개지는 경우: 같은 종이 두 줄로 들어가 도감 번호가 갈린다.
d = [k for k, v in collections.Counter(x['학명'] for x in r).items() if v > 1]
chk('학명 중복 0', not d, '같은 학명이 두 행', str(d[:5]))

# 2 ─ 국명 중복. 빨개지는 경우: 사용자에게 같은 이름이 두 칸으로 보인다.
# 빈칸은 KPNI에 없는 원예 속칭(벚꽃·복사꽃 등) — 중복 판정 대상이 아니다.
d = [k for k, v in collections.Counter(x['KPNI국명'] for x in r).items() if v > 1 and k]
chk('KPNI국명 중복 0', not d, '같은 국명이 두 행', str(d[:5]))

# 2b ─ 표시명(이름) 중복. 빨개지는 경우: 사용자 화면에 같은 이름이 두 칸.
d = [k for k, v in collections.Counter(x['이름'] for x in r).items() if v > 1]
chk('표시명 중복 0', not d, '화면에 같은 이름 두 칸', str(d[:5]))

# 3 ─ 기존 200종 전량 보존 + 번호 유지.
#     빨개지는 경우: 조립 규칙을 바꿔 기존 종이 빠진다(1차에서 63종이 빠졌다).
keep = {x['도감번호'] for x in r if x['기존200종'] == 'Y'}
want = {o['도감번호'] for o in old}
chk('기존 200종 보존 200/200', keep == want, '기존 종이 누락/번호 변경',
    f'누락 {sorted(want-keep)[:10]}')

# 4 ─ 도감 표시명 보존. 빨개지는 경우: KPNI 국명이 도감 이름을 덮어써
#     '남산제비꽃'이 '태백제비꽃'으로 바뀐다(2차에서 실제로 일어났다).
by_no = {x['도감번호']: x for x in r if x['기존200종'] == 'Y'}
bad = [(o['이름'], by_no[o['도감번호']]['이름']) for o in old
       if o['도감번호'] in by_no and by_no[o['도감번호']]['이름'] != o['이름']]
chk('도감 표시명 200종 그대로', not bad, '도감 이름이 바뀜', str(bad[:5]))

# 5 ─ 🔴 국명↔학명이 KPNI 정명 쌍인가. 이게 2·3차 결함을 잡는 검사다.
#     빨개지는 경우: binomial로 잘라 원종/변종을 뒤섞으면 쌍이 깨진다.
bad = [f"{x['KPNI국명']}={x['학명']}" for x in r
       if x['자생구분'] != '미확인' and x['학명'] not in acc_ko.get(x['KPNI국명'], ())]
chk('국명↔학명이 KPNI 정명 쌍 (미확인 제외)', not bad,
    '원종/변종을 섞어 다른 종의 학명을 붙임', f'{len(bad)}건 {bad[:6]}')

# 5b ─ 🔴 KPNI에 없는 종은 KPNI국명이 **비어 있어야** 한다.
#      빨개지는 경우: 도감 속칭(벚꽃·복사꽃)을 KPNI국명 칸에 넣어 출처를 위장한다.
#      검사 5가 '미확인'을 제외하므로 이 검사가 없으면 그 결함이 통과한다(MUT187).
bad = [f"{x['이름']}={x['KPNI국명']}" for x in r
       if x['자생구분'] == '미확인' and x['KPNI국명']]
chk('미확인 종의 KPNI국명 빈칸', not bad,
    'KPNI에 없는 이름을 KPNI 출처로 표기', f'{len(bad)}건 {bad[:6]}')

# 6 ─ 재배품종(따옴표)이 종 자리에 없는가.
#     빨개지는 경우: ko2acc 정렬이 빠져 "자두나무 '슈페리어'"가 들어온다.
bad = [f"{x['KPNI국명']}={x['학명']}" for x in r
       if CUL.search(x['KPNI국명']) or CUL.search(x['학명'])]
chk('품종명 잔존 0', not bad, '품종이 원종 자리를 차지', str(bad[:5]))

# 7 ─ 개화기 형식. 빨개지는 경우: fmt()를 고쳐 파싱 불가 문자열이 나온다.
bad = [x['개화기_초안'] for x in r
       if x['개화기_초안'] and not re.match(r'^\d{1,2}(~\d{1,2})?월$', x['개화기_초안'])]
chk('개화기 형식 이상 0', not bad, '형식이 깨짐', str(bad[:5]))


def span(s):
    s = s.replace('월', '')
    if '~' not in s:
        return 1
    a, b = map(int, s.split('~'))
    return ((b - a) % 12) + 1


# 8 ─ 6개월 초과 없음. 빨개지는 경우: >6개월 블랭킹을 빼면 열매·잎 관찰이 섞여 들어온다.
bad = [(x['이름'], x['개화기_초안']) for x in r
       if x['개화기_초안'] and span(x['개화기_초안']) > 6]
chk('개화 구간 6개월 초과 0', not bad, '열매·잎 관찰이 개화월로 들어감', str(bad[:5]))

# 9 ─ 관찰기준학명은 학명의 binomial이어야 한다.
#     빨개지는 경우: 관찰수를 엉뚱한 종에서 가져온다(④의 실수).
bad = [f"{x['학명']} ← {x['관찰기준학명']}" for x in r
       if x['관찰기준학명'] and not x['학명'].startswith(x['관찰기준학명'])]
chk('관찰기준학명이 학명의 원종', not bad, '다른 종의 관찰수를 집계', str(bad[:5]))

# 9b ─ 🔴 변종 정명 행에는 관찰기준학명이 **반드시** 있어야 한다.
#      검사 9는 "있으면 원종인가"만 보므로, 칸을 아예 비워 버리면 통과한다(MUT191).
#      그러면 `Prunella vulgaris subsp. asiatica`의 관찰수가 원종 것인데
#      화면상 그 변종의 실측처럼 보인다 — 출처를 알 수 없게 된다.
bad = [x['학명'] for x in r
       if (RANKQ.search(x['학명']) or CUL.search(x['학명']))
       and int(x['관찰건수'] or 0) > 0 and not x['관찰기준학명']]
chk('변종·품종 행에 관찰기준학명 명시', not bad,
    '원종의 관찰수를 변종 실측으로 표기', f'{len(bad)}건 {bad[:6]}')

# 9c ─ 🔴 같은 binomial의 관찰수를 두 행이 나눠 갖지 않는가.
#      빨개지는 경우: 원종 7,180건이 변종에도 복사돼 합계가 부풀고 둘 다 '흔함'이 된다.
#      검사 9는 "관찰기준학명이 원종인가"만 보므로 이 중복을 못 잡는다.
grp = collections.defaultdict(list)
for x in r:
    if int(x['관찰건수'] or 0) > 0:
        grp[x['관찰기준학명'] or x['학명']].append(x['이름'])
bad = [(b, l) for b, l in grp.items() if len(l) > 1]
chk('관찰수 이중계산 0', not bad, '원종 관찰수가 변종에도 복사됨', f'{len(bad)}건 {bad[:4]}')

# 10 ─ 관찰건수 0인 행에는 개화기·희귀도가 없어야 한다.
#      빨개지는 경우: 근거 없는 값이 채워진다.
# 관찰기준학명이 있는 행은 예외 — 관찰수는 중복이라 비웠지만 개화기는 원종 근거가
# 있고, 그 근거가 어느 학명인지 관찰기준학명에 적혀 있다.
bad = [x['이름'] for x in r
       if x['관찰건수'] in ('', '0') and not x['관찰기준학명']
       and (x['희귀도_초안'] or x['개화기_초안'])]
chk('관찰 0건에 근거없는 값 0', not bad, '데이터 없이 값을 채움', str(bad[:5]))

# 10b ─ 희귀도는 관찰수가 있을 때만. 빨개지는 경우: 중복 제거 후에도 희귀도가 남아
#       원종의 분위값이 변종의 희귀도로 보인다(둘 다 '흔함'이 되던 결함).
bad = [x['이름'] for x in r if x['관찰건수'] in ('', '0') and x['희귀도_초안']]
chk('관찰수 없는 행에 희귀도 0', not bad, '원종 분위값이 변종 희귀도로 남음', str(bad[:5]))

# 11 ─ 사람이 정할 칸은 기존 200종에만 있어야 한다(신규는 비어 있다).
#      빨개지는 경우: 추측으로 대표색·AI난이도를 채운다.
bad = [x['이름'] for x in r if x['기존200종'] != 'Y'
       and (x['대표색'] or x['AI난이도'] or x['주요서식지'] or x['비슷한꽃'])]
chk('신규종의 사람칸 빈칸', not bad, '추측으로 채움', str(bad[:5]))

# 12 ─ 학명 정정 기록이 실제로 정정된 값인가.
stale = json.load(io.open(
    '/Users/kimhyosan/game-project/꽃도감/_tools/확장/stale_names.json', encoding='utf-8'))
by_ko = {x['KPNI국명']: x for x in r}
bad = [f'{ko}: 기대 {new} 실제 {by_ko[ko]["학명"]}' for oldn, new, ko, why in stale
       if ko in by_ko and by_ko[ko]['학명'] != new]
chk('stale 기록 = CSV 실제값', not bad, '문서에 적은 정정이 CSV에 없음(2차의 오보고)',
    f'{len(bad)}건 {bad[:5]}')

# 14b ─ stale 기록에 중복·무변화가 없어야 한다.
#       빨개지는 경우: 경로를 근거로 세어 학명이 안 바뀐 종(부추)이 정정 목록에 들어간다.
bad = [s0 for s0 in stale if s0[0] == s0[1]]
chk('stale에 무변화 기록 0', not bad, '고칠 게 없는 종을 정정으로 보고', str(bad[:5]))
seen = collections.Counter((s0[2], s0[1]) for s0 in stale)
bad = [k for k, v in seen.items() if v > 1]
chk('stale 중복 0', not bad, '같은 정정을 두 번 보고', str(bad[:5]))

# 15 ─ 🔴 종 수 하한선. 빨개지는 경우: 조립 경로를 하나 빼면 종이 **조용히** 사라진다.
#      이명 해소 경로를 끄면 2,057 → 2,052로 5종이 줄었는데 다른 14개 검사가 전부
#      통과했다(MUT192). 목록 작업에서 가장 위험한 실패는 "틀린 값"이 아니라 "없어진 행"이다.
#      2,050 같은 느슨한 하한선은 무의미하다 — 2,052도 통과해서 MUT192가 살아남았다.
#      **정확한 기대값**을 박는다. 조립 규칙을 의도적으로 바꿀 때 이 숫자도 같이 고친다.
EXPECT = 2057
chk(f'종 수 == {EXPECT} (현재 {len(r)})', len(r) == EXPECT,
    '조립 경로가 빠져 종이 사라짐 / 규칙 변경 후 기대값 미갱신', f'{len(r)}행')

print()
if fail:
    print(f'판정: FAIL {len(fail)}건')
    for l, w, _ in fail:
        print(f'  · {l}  ← {w}')
    sys.exit(1)
print('판정: PASS  (20개 검사)')
