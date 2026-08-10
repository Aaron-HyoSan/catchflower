# -*- coding: utf-8 -*-
"""돌연변이 검증 — 검사(verify_list.py)가 정말 빨개지는지 확인한다.

    python3 mutate_check.py 188      # 하나만
    python3 mutate_check.py all      # 전부

build_final.py에 결함을 하나 심고 재조립 → verify_list.py를 돌려 **FAIL이 나는지** 본다.
끝나면 build_final.py를 원상 복구한다(예외가 나도 복구한다).

🔴 **CSV가 안 바뀌면 그 돌연변이는 무효다.** 검사가 통과한 게 아니라 결함을 안 심은 것이다.
   189가 그렇다 — 최종 목록에 품종 행이 0개라 그 코드 경로를 아무도 안 탄다.
🔴 각 항목의 '기대' 는 **내가 실제로 돌려서 본 결과**다. 여기 적은 검사 이름이 안 나오면
   검사가 약해졌거나 조립 규칙이 바뀐 것이다.
"""
import io, os, sys, subprocess, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BF = os.path.join(HERE, 'build_final.py')
VF = os.path.join(HERE, 'verify_list.py')
CSV = '/Users/kimhyosan/game-project/꽃도감/꽃목록_후보_2000종.csv'

# 번호: (심을 곳, 바꿀 값, 무슨 결함인가, 기대 FAIL)
MUTS = {
    '183': ("    if ko in ko2acc:                              # ① 국명이 KPNI 정명이다",
            "    if False and ko in ko2acc:                    # ① 국명이 KPNI 정명이다",
            '국명 우선 경로 제거 — 2차 결함(부처꽃→털부처꽃) 재현',
            '도감 표시명 200종 그대로'),
    '184': ("        elif lw in RANK:\n            keep.append(lw)",
            "        elif lw in RANK:\n            break",
            'sciname()이 var.·subsp.를 버린다 — 원종과 변종이 같아진다',
            '국명↔학명이 KPNI 정명 쌍'),
    '186': ("    if len(run) > 6:\n        return None, tot, prof",
            "    if False:\n        return None, tot, prof",
            '개화 6개월 초과 블랭킹 제거 — 열매·잎 관찰이 개화월로 들어온다',
            '개화 구간 6개월 초과 0'),
    '187': ("target, bino, kind, kpni_ko = mine or ko, c or ko, '미확인', ''",
            "target, bino, kind, kpni_ko = mine or ko, c or ko, '미확인', ko",
            'KPNI에 없는 도감 이름을 KPNI국명 칸에 넣는다 — 출처를 위조한다',
            '미확인 종의 KPNI국명 빈칸'),
    '188': (r'''CULTIVAR_RE = re.compile(r"['‘’][^'‘’]*['’]")''',
            r'''CULTIVAR_RE = re.compile(r"['‘’]")''',
            '품종 판정을 아포스트로피 하나로 — 저자명(Buc\'hoz)이 품종으로 걸린다',
            '국명↔학명이 KPNI 정명 쌍'),
    '189': ("    return f'{out} {cul.group(0)}' if cul else out",
            "    return out",
            '품종명을 잘라 버린다 — 품종이 원종으로 승격된다',
            '(무효: 최종 목록에 품종 행이 0개라 CSV가 안 바뀐다)'),
    '190': ("    return next(iter(pure)) if len(pure) == 1 else None",
            "    return None",
            '잡종 제외를 되돌린다 — 플럼코트가 자두나무 자리를 모호하게 만든다',
            '품종명 잔존 0'),
    '191': ("'관찰기준학명': '' if bino == sci else bino,",
            "'관찰기준학명': '',",
            '집계에 쓴 학명을 안 적는다 — 원종 관찰수가 변종 실측으로 보인다',
            '변종·품종 행에 관찰기준학명 명시'),
    '192': ("        elif issp:\n            syn2ko.setdefault(c, (ko, kind))",
            "        elif False:\n            syn2ko.setdefault(c, (ko, kind))",
            '이명 해소 경로 제거 — 종이 5개 조용히 사라진다',
            '종 수 == 2057'),
    'dedup': ("    if len(l) < 2:\n        continue",
              "    if True:\n        continue",
              '원종·변종 관찰수 중복 제거를 되돌린다 — 두 종이 같은 수치로 흔함이 된다',
              '관찰수 이중계산 0'),
}


def run(name, base_csv):
    a, b, what, expect = MUTS[name]
    orig = io.open(BF, encoding='utf-8').read()
    if a not in orig:
        print(f'MUT{name}: 🔴 심을 패턴이 없다 — 조립 코드가 바뀌었다')
        return
    try:
        io.open(BF, 'w', encoding='utf-8').write(orig.replace(a, b, 1))
        r = subprocess.run(['python3', BF], capture_output=True, text=True, cwd=HERE)
        head = (r.stdout.splitlines() or [r.stderr.strip()[-120:]])[0]
        now = io.open(CSV, encoding='utf-8-sig').read()
        print(f'\nMUT{name}  {what}')
        print(f'  {head}')
        if now == base_csv:
            print('  ⚠ CSV 무변화 → 돌연변이 무효(검사가 통과한 게 아니다)')
        v = subprocess.run(['python3', VF], capture_output=True, text=True, cwd=HERE)
        fails = [l.strip() for l in v.stdout.splitlines() if 'FAIL' in l]
        for l in fails:
            print('  ' + l)
        print(f'  기대: {expect}')
    finally:
        io.open(BF, 'w', encoding='utf-8').write(orig)


if __name__ == '__main__':
    base = io.open(CSV, encoding='utf-8-sig').read()   # 정상 CSV를 먼저 잡아 둔다
    names = list(MUTS) if sys.argv[1:2] == ['all'] else [sys.argv[1]]
    try:
        for n in names:
            run(n, base)
    finally:
        subprocess.run(['python3', BF], capture_output=True, cwd=HERE)   # 정상 CSV 복원
        print('\nbuild_final.py · CSV 원상 복구 완료')
