# -*- coding: utf-8 -*-
"""꽃목록_확장_2057종.csv → flowers.json 적재 스크립트 (**iOS 번들**).

**이 파일이 존재하는 이유는 하나다.**
공유계약 1-2절: 개화기 `"3~4월"` 문자열 파싱은 **여기서 단 한 번만** 한다.
클라이언트는 `bloom_months`를 `int[]`로만 받는다. 양쪽이 각자 파싱하면
iOS와 Android가 다른 후보 집합을 쓰게 되고, 그러면 개화월 하드 필터가 갈려
**판별 결과 자체가 달라진다.**

🔴 **2,057종 확장부터 만드는 값이 생겼다** — 개화월 cascade·라틴→한글 과·계절
   규칙(계약 1-2-b). 그건 `flower_master.py` **한 곳에만** 있고
   Android 쪽 `꽃도감/_tools/build_app_data.py`도 같은 모듈을 import 한다.
   여기 베껴 쓰면 두 앱이 다른 개화월을 쓰게 된다 — 계약 1-2가 막으려는 바로 그것.

입력  ../꽃도감/꽃목록_확장_2057종.csv + ../꽃도감/꽃목록_200종.csv
출력  flowers.json                 (iOS 번들. `ios/project.yml`이 이 경로를 싣는다)

실행  python3 공용_적재/build_flowers_json.py
      python3 공용_적재/build_flowers_json.py --test   ← 파싱 케이스 테스트만
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_PATH = os.path.join(HERE, "flowers.json")

sys.path.insert(0, HERE)
import flower_master as fm  # noqa: E402

parse_bloom_months = fm.parse_bloom_months


def test_parse():
    """공유계약 1-2절이 요구한 케이스 테스트. 랩어라운드가 핵심이다."""
    cases = [
        ("3~4월", [3, 4]),
        ("4~5월", [4, 5]),
        ("7~9월", [7, 8, 9]),
        ("4월", [4]),
        ("3월", [3]),
        ("5~10월", [5, 6, 7, 8, 9, 10]),
        ("12~4월", [12, 1, 2, 3, 4]),          # 동백꽃 — 실제 데이터
        ("10~12월", [10, 11, 12]),             # 애기동백 — 실제 데이터
        ("11~2월", [11, 12, 1, 2]),            # 계약 문서에 명시된 가상 케이스
        ("12월", [12]),
        ("1~12월", [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]),
    ]
    ok = True
    for src, want in cases:
        got = parse_bloom_months(src)
        mark = "✓" if got == want else "✗"
        if got != want:
            ok = False
        print("  %s %-8s → %s" % (mark, src, got))

    for bad in ("", "13~4월", "0월", "월"):
        try:
            parse_bloom_months(bad)
            print("  ✗ %r 는 거부해야 한다" % bad)
            ok = False
        except ValueError:
            print("  ✓ %r 거부됨" % bad)

    # 🔴 cascade 보조 함수도 함께 잰다. 여기가 틀리면 **종이 조용히 사라진다.**
    checks = [
        ("pad 앞뒤 1개월", fm.pad_months([5, 6], 1), [4, 5, 6, 7]),
        ("pad 랩어라운드", fm.pad_months([12], 1), [11, 12, 1]),
        # 최다월 4월 ±4 → 12~8월. **정렬된 배열이라 첫 원소가 12월이 아니다** —
        # 이 성질 때문에 계절을 `months[0]`으로 뽑으면 안 된다(계약 1-1-d).
        ("최다월 4월 ±4", fm.around_peak({4: 10}, 4), [1, 2, 3, 4, 5, 6, 7, 8, 12]),
        ("관찰추정 단일월", fm.observed_run({7: 100}), [7]),
    ]
    for label, got, want in checks:
        mark = "✓" if got == want else "✗"
        if got != want:
            ok = False
        print("  %s %-16s → %s" % (mark, label, got))

    print("파싱 테스트: %s" % ("전부 통과" if ok else "실패 있음"))
    return ok


def report(flowers, stats):
    """계약에 적힌 분포와 실제가 맞는지 센다. 숫자를 손으로 쓰면 틀린다."""
    def count(key):
        c = {}
        for f in flowers:
            k = f[key] if f[key] is not None else "(null)"
            c[k] = c.get(k, 0) + 1
        return c

    print("총 %d종" % len(flowers))
    print("  %s" % fm.format_stats(stats, flowers))
    print("  계절     %s" % count("season"))
    print("  희귀도   %s" % count("rarity"))
    print("  AI난이도 %s" % count("ai_difficulty"))

    per_month = {m: 0 for m in range(1, 13)}
    for f in flowers:
        for m in f["bloom_months"]:
            per_month[m] += 1
    print("  월별 개화 종수 %s" % [per_month[m] for m in range(1, 13)])
    return per_month


def main():
    if "--test" in sys.argv:
        sys.exit(0 if test_parse() else 1)

    print("[1/3] 개화기 파싱 · cascade 보조함수 케이스 테스트")
    if not test_parse():
        print("파싱이 틀렸다. 적재를 중단한다.")
        sys.exit(1)

    print("\n[2/3] CSV 적재 · 검증 (무결성 위반이 있으면 여기서 멈춘다)")
    flowers, stats, dangling = fm.build_master()

    if dangling:
        # 진행.md (1)회차에서 12건을 잡아낸 그 검사다. 재발을 여기서 막는다.
        print("  ⚠️ 도감에 없는 종을 가리키는 '비슷한꽃' 참조 %d건" % len(dangling))
        for fid, nm in dangling:
            print("     - %d → %s" % (fid, nm))

    print("  ✓ 무결성 통과 (도감번호 1~%d 연속 · 학명·과·개화월 빈칸 없음 · "
          "enum 전부 계약 범위 · bloom_source와 bloom_label이 어긋나지 않음)"
          % fm.TOTAL_COUNT)

    per_month = report(flowers, stats)

    print("\n[3/3] 출력")
    payload = {
        # 🔴 스키마가 바뀌었다 — `bloom_source`가 추가되고 `season`이 nullable이 됐다.
        #    옛 번들을 쓰는 앱이 조용히 오작동하지 않도록 버전을 올린다.
        "schema_version": 2,
        "source": "꽃도감/꽃목록_확장_2057종.csv + 꽃목록_200종.csv(id 1~200)",
        "note": "생성물이다. 직접 고치지 않는다. 원본은 공용_적재/flower_master.py",
        "count": len(flowers),
        "bloom_source_counts": stats,
        "bloom_months_by_month": {str(m): per_month[m] for m in range(1, 13)},
        "flowers": flowers,
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("  → %s (%.1f KB)" % (OUT_PATH, os.path.getsize(OUT_PATH) / 1024))


if __name__ == "__main__":
    main()
