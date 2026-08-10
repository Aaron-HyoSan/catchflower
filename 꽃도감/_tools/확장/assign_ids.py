# -*- coding: utf-8 -*-
"""신규 1,857종에 도감번호를 부여한다 → `꽃목록_확장_2057종.csv`

    python3 꽃도감/_tools/확장/assign_ids.py

🔴 **기존 200종의 번호는 절대 바뀌지 않는다.** `discoveries.flower_id`가
   `flowers(id)`를 외래키로 참조한다(`오너_실행/01_스키마_전체.sql:152`).
   번호가 밀리면 **사용자가 이미 모은 꽃이 다른 꽃으로 바뀐다** — 앱은 정상 동작하고,
   도감에는 그 사람이 찍지도 않은 꽃이 등록돼 있다. 증상이 "이상한 꽃이 있네"뿐이다.

번호 부여 규칙 (결정론적이어야 한다 — 돌릴 때마다 달라지면 파일명이 무효가 된다):
  1~200      기존 도감. `꽃목록_200종.csv`의 번호를 그대로 쓴다.
  201~        신규. **국명 가나다순**. 관찰수 순이 아니다 —
              관찰수는 GBIF를 다시 받으면 바뀌므로 **번호가 흔들린다**.
              이름은 KPNI 정명이라 안정적이다.

🔴 이름이 같은 두 종은 없다(verify_list.py의 '표시명 중복 0'이 지킨다). 그래서
   가나다순이 유일한 순서를 만든다. 동명이 생기면 학명으로 2차 정렬한다.
"""
import csv
import io
import sys

BASE = '/Users/kimhyosan/game-project/꽃도감/'
SRC = BASE + '꽃목록_후보_2000종.csv'
OLD = BASE + '꽃목록_200종.csv'
OUT = BASE + '꽃목록_확장_2057종.csv'


def main():
    rows = list(csv.DictReader(io.open(SRC, encoding='utf-8-sig')))
    old = list(csv.DictReader(io.open(OLD, encoding='utf-8-sig')))

    # 기존 번호를 원본에서 다시 읽는다. 후보 CSV의 값을 믿지 않는다(이중 확인).
    old_no = {o['이름']: int(o['도감번호']) for o in old}
    if sorted(old_no.values()) != list(range(1, 201)):
        sys.exit('🔴 기존 200종의 번호가 1~200 연속이 아니다')

    kept, new = [], []
    for r in rows:
        if r['기존200종'] == 'Y':
            n = old_no.get(r['이름'])
            if n is None:
                sys.exit(f'🔴 기존종인데 원본에 이름이 없다: {r["이름"]}')
            if int(r['도감번호']) != n:
                sys.exit(f'🔴 번호 불일치: {r["이름"]} {r["도감번호"]} != {n}')
            r['도감번호'] = str(n)
            kept.append(r)
        else:
            if r['도감번호'].strip():
                sys.exit(f'🔴 신규종에 번호가 이미 있다: {r["이름"]}')
            new.append(r)

    if len(kept) != 200:
        sys.exit(f'🔴 기존종이 200이 아니다: {len(kept)}')

    new.sort(key=lambda r: (r['이름'], r['학명']))
    for i, r in enumerate(new, start=201):
        r['도감번호'] = str(i)

    out = sorted(kept + new, key=lambda r: int(r['도감번호']))

    # ── 부여 결과 불변식. 여기서 막지 못하면 파일명 규칙이 무너진다.
    nos = [int(r['도감번호']) for r in out]
    if nos != list(range(1, len(out) + 1)):
        sys.exit('🔴 번호가 1~N 연속이 아니다')
    if len(set(nos)) != len(nos):
        sys.exit('🔴 번호 중복')
    for r in out:
        if int(r['도감번호']) <= 200 and r['기존200종'] != 'Y':
            sys.exit(f'🔴 1~200 구간에 신규종이 들어갔다: {r["이름"]}')

    with io.open(OUT, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(out[0].keys()))
        w.writeheader()
        w.writerows(out)

    print(f'출력 {len(out):,}종 → {OUT}')
    print(f'  1~200      기존 도감 (번호 그대로)')
    print(f'  201~{len(out)}  신규 {len(new):,}종 (국명 가나다순)')
    print(f'\n번호 자릿수: {len(str(len(out)))}자리 → 파일명은 '
          f'`{{:0{len(str(len(out)))}d}}_이름.png` 꼴이다')
    print('\n예시:')
    for r in out[199:204]:
        mark = '기존' if r['기존200종'] == 'Y' else '신규'
        print(f"  {int(r['도감번호']):04d}_{r['이름']}.png   ({mark})")
    print('  …')
    for r in out[-2:]:
        print(f"  {int(r['도감번호']):04d}_{r['이름']}.png   (신규)")


if __name__ == '__main__':
    main()
