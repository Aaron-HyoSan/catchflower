#!/usr/bin/env python3
"""iOS가 캐시해 둔 PlantNet 원본 응답 200개를 JVM 테스트용 픽스처로 압축한다.

**왜 필요한가.** AOS `PlantNetRecognizer`는 컴파일도 되고 단위 테스트도 통과한다.
그런데 그건 **내가 만든 응답으로만** 검증한 것이다 — 진짜 PlantNet 응답의 모양
(속만 맞는 학명, `sect.` 표기, 후보 10개 중 정답이 5순위)에서 같은 결과가 나오는지는
모른다. iOS는 실측 200장으로 Top-1 77%를 얻었다. **AOS가 같은 숫자를 내야 한다** —
안 그러면 두 앱이 같은 사진에 다른 답을 준다(공유계약 3절).

**호출은 0건이다.** iOS 실측이 이미 캐시한 원본 응답(`/private/tmp/plantnet_cache`)만 읽는다.
오너 규칙("원본 응답을 캐시해야 재과금 없이 재채점", "실측 보고 전 유료 호출 금지")대로다.

사용:
    python3 android/_tools/build_plantnet_replay_fixture.py
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
# 도감 원본. `꽃도감/flowers.json`이 앱이 실제로 번들하는 파일이다
# (`공용_적재/flowers.json`은 similar_flower_names가 없는 옛 산출물 — 값은 같다).
FLOWERS_JSON = ROOT / "꽃도감" / "flowers.json"
DATASET = pathlib.Path("/private/tmp/flower_photos")
CACHE = pathlib.Path("/private/tmp/plantnet_cache")
OUT = ROOT / "android/app/src/test/resources/plantnet_replay.json"

# iOS `plantnet_measure.py`의 CLASS_TO_GENERA·CLASS_PEAK_MONTH와 **같은 값**이어야 한다.
# 여기서 흩어지면 두 플랫폼의 측정이 서로를 설명하지 못한다.
CLASS_TO_GENERA = {
    "dandelion": ["Taraxacum"],
    "roses": ["Rosa"],
    "sunflowers": ["Helianthus"],
    "tulips": ["Tulipa"],
    "daisy": ["Bellis", "Leucanthemum", "Chrysanthemum", "Argyranthemum", "Aster"],
}
CLASS_PEAK_MONTH = {
    "dandelion": 4,   # 민들레 3~5월
    "roses": 6,       # 장미 5~10 · 찔레꽃 5~6 · 해당화 5~7
    "sunflowers": 8,  # 해바라기 7~9월
    "tulips": 4,      # 튤립 4~5월
    "daisy": 5,       # 데이지 4~5월
}
LIMIT = 40  # 클래스당. iOS 실측과 같은 표본이어야 숫자를 비교할 수 있다.


def main():
    if not CACHE.exists():
        sys.exit(f"캐시가 없다: {CACHE}\n  iOS 실측(ios/Tools/plantnet_measure.py)이 만든다.")
    if not DATASET.exists():
        sys.exit(f"데이터셋이 없다: {DATASET}\n"
                 "  python3 android/_tools/fetch_prefilter_dataset.py 로 내린다.")

    photos, missing = [], 0
    for cls in sorted(CLASS_TO_GENERA):
        for p in sorted((DATASET / cls).glob("*.jpg"))[:LIMIT]:
            digest = hashlib.sha1(p.read_bytes()).hexdigest()[:16]
            cached = CACHE / f"{digest}.json"
            if not cached.exists():
                missing += 1
                continue
            payload = json.loads(cached.read_text())
            # **응답 전체를 담지 않는다.** 우리가 파싱하는 두 필드만 남긴다
            # (score·scientificNameWithoutAuthor). gbif/powo/commonNames까지 넣으면
            # 픽스처가 몇 MB가 되는데 검증에 쓰이지 않는다.
            photos.append({
                "cls": cls,
                "file": p.name,
                "results": [
                    {"score": r["score"],
                     "name": r["species"]["scientificNameWithoutAuthor"]}
                    for r in payload.get("results", [])
                ],
            })

    # 도감 200종도 같이 담는다 — JVM 테스트는 `assets`를 못 읽는다(그건 계측 테스트다).
    # **필요한 3필드만** 넣는다: 색인은 학명, 개화월 필터는 bloom_months, 채점은 id를 쓴다.
    flowers = json.load(open(FLOWERS_JSON, encoding="utf-8"))["flowers"]
    if len(flowers) != 200:
        sys.exit(f"도감이 200종이 아니다: {len(flowers)}종")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "note": "iOS 실측(진행 (19)) 캐시에서 생성. 재생성: "
                "python3 android/_tools/build_plantnet_replay_fixture.py",
        "class_to_genera": CLASS_TO_GENERA,
        "class_peak_month": CLASS_PEAK_MONTH,
        "flowers": [
            {"id": f["id"], "name": f["name"],
             "scientific_name": f["scientific_name"], "bloom_months": f["bloom_months"]}
            for f in flowers
        ],
        "photos": photos,
    }, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"{len(photos)}장 → {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024}KB)")
    if missing:
        print(f"⚠️ 캐시 없는 사진 {missing}장은 건너뜀 (iOS 실측 표본과 다르다)")


if __name__ == "__main__":
    main()
