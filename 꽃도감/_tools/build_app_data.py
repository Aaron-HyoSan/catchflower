#!/usr/bin/env python3
"""꽃목록_확장_2057종.csv → 꽃도감/flowers.json (**Android**가 번들로 싣는 데이터)

왜 이 스크립트가 있는가
-----------------------
공유계약 1-2가 못 박은 것 때문이다. CSV의 개화기는 `"3~4월"` 같은 **문자열**인데
이 값이 A-1 개화월 하드 필터(필수 구현)의 입력이다.
**iOS와 Android가 각자 파싱하면 후보 집합이 갈리고 판별이 갈린다.**

그래서 파싱은 여기서 단 한 번 하고, 클라이언트는 `int[]`만 받는다.
`비슷한꽃`의 이름 → id 변환도 같은 이유로 여기서 한다.

🔴 **2,057종 확장부터는 "만드는 값"이 생겼다** — 개화월 cascade·라틴→한글 과·
   계절 규칙이다(계약 1-2-b). 그건 이 파일에 두지 않고
   `공용_적재/flower_master.py` **한 곳에만** 둔다. iOS 쪽 적재 스크립트
   (`공용_적재/build_flowers_json.py`)도 같은 모듈을 import 한다 —
   여기 베껴 두면 두 앱이 다른 개화월을 쓰게 되고, 그게 계약 1-2가 막으려는 것이다.

산출물 `꽃도감/flowers.json`은 **Android 빌드가 assets로 복사하는 파일**이다
(`android/app/build.gradle.kts`의 `SyncSharedAssets`). 형식을 혼자 바꾸지 않는다.

사용:
    python3 꽃도감/_tools/build_app_data.py
"""

import json
import os
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_JSON = ROOT / "꽃도감" / "flowers.json"

# 만드는 로직은 공용 모듈에 있다. **여기서 다시 구현하지 않는다.**
sys.path.insert(0, str(ROOT / "공용_적재"))
import flower_master as fm  # noqa: E402


def report(flowers, stats, dangling):
    print(f"종수: {len(flowers)}")
    print(f"  {fm.format_stats(stats, flowers)}")
    print("계절:", dict(Counter(f["season"] or "(없음)" for f in flowers)))
    print("희귀도:", dict(Counter(f["rarity"] for f in flowers)))
    print("AI난이도:", dict(Counter(f["ai_difficulty"] for f in flowers)))

    per_month = Counter()
    for f in flowers:
        for month in f["bloom_months"]:
            per_month[month] += 1
    print("월별 개화 종수:", {m: per_month.get(m, 0) for m in range(1, 13)})

    # 🔴 사람이 읽는 값이 빈 종수를 **세어서 보고한다.** 계약 1-1-c·1-2-c대로
    #    화면은 절을 빼지만, 몇 종이 그 상태인지는 숫자로 남아야 한다.
    #    안 세면 "2,057종 등록"이 "2,057종을 안다"로 읽힌다.
    for key, label in (("bloom_label", "개화기 표기"), ("color", "대표색"),
                       ("habitat", "주요서식지"), ("season", "계절")):
        blank = sum(1 for f in flowers if not f[key])
        if blank:
            print(f"  ⚠️ {label} 없는 종 {blank}개 — 화면은 해당 절을 빼서 그린다")

    if dangling:
        print(f"\n⚠️ 도감에 없는 '비슷한꽃' 참조 {len(dangling)}건 (표시명으로만 남긴다):")
        for flower_id, name in dangling:
            print(f"   {flower_id:>4} → {name}")


def main() -> int:
    flowers, stats, dangling = fm.build_master()
    OUT_JSON.write_text(
        json.dumps({"flowers": flowers}, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    report(flowers, stats, dangling)
    print(f"\n생성: {OUT_JSON.relative_to(ROOT)} ({OUT_JSON.stat().st_size / 1024:.0f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
