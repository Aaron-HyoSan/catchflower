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


def photos_from_cache():
    """`/private/tmp/plantnet_cache`에서 응답 200건을 읽는다. 캐시가 없으면 `None`."""
    if not CACHE.exists() or not DATASET.exists():
        return None

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
    if missing:
        print(f"⚠️ 캐시 없는 사진 {missing}장은 건너뜀 (iOS 실측 표본과 다르다)")
    return photos or None


def main():
    photos = photos_from_cache()
    reused = False
    if photos is None:
        # 🔴 **`/private/tmp`는 지워진다.** 실제로 지워졌다 — 캐시 0건.
        #    그러면 이 스크립트가 예전엔 `sys.exit`으로 멈췄는데, 그건
        #    **도감을 갱신할 방법이 없어진다**는 뜻이다(확장 때 필요해졌다).
        #    사진 응답은 이미 커밋된 픽스처 안에 있으므로 **그걸 재사용한다.**
        #
        #    ⚠️ 재사용은 **응답에 한정한다.** 도감 배열은 지금 `flowers.json`에서
        #    다시 만든다 — 그게 이 스크립트를 다시 돌리는 이유다.
        if not OUT.exists():
            sys.exit(f"캐시({CACHE})도 없고 기존 픽스처({OUT})도 없다.\n"
                     "  iOS 실측(ios/Tools/plantnet_measure.py)이 캐시를 만든다.")
        photos = json.loads(OUT.read_text(encoding="utf-8"))["photos"]
        reused = True
        print(f"⚠️ 캐시가 없어 기존 픽스처의 응답 {len(photos)}건을 그대로 재사용한다 "
              "(도감 배열만 다시 만든다). API 호출 0건.")

    # 🔴 **장수를 단정한다.** 여기가 비거나 줄면 아래 테스트들이 **0장을 돌고 통과한다** —
    #    실측 단계에서 한 번 당한 함정이고, 그때는 초록이라 눈치채지 못했다.
    if len(photos) != LIMIT * len(CLASS_TO_GENERA):
        sys.exit(f"사진이 {LIMIT * len(CLASS_TO_GENERA)}장이어야 한다: {len(photos)}장")

    # 도감도 같이 담는다 — JVM 테스트는 `assets`를 못 읽는다(그건 계측 테스트다).
    # **필요한 4필드만** 넣는다: 색인은 학명, 개화월 필터는 bloom_months, 채점은 id를 쓴다.
    flowers = json.load(open(FLOWERS_JSON, encoding="utf-8"))["flowers"]
    if len(flowers) != 2057:
        sys.exit(f"도감이 2,057종이 아니다: {len(flowers)}종\n"
                 "  python3 꽃도감/_tools/build_app_data.py 를 먼저 돌린다.")

    # 🔴 **배열을 두 개 담는다.** `flowers`는 **대조군(옛 200종)** 이고
    #    `flowers_full`이 앱이 실제로 싣는 2,057종이다.
    #
    #    확장하면서 이 픽스처를 2,057종으로 **갈아치우고 싶었는데, 그러면
    #    iOS 실측 77.0%와 비교할 대상이 사라진다.** 숫자가 바뀌었을 때
    #    "확장 때문인가 파이프라인이 깨진 건가"를 가릴 방법이 없어진다 —
    #    이 저장소에서 반복해 당한 실패다(대조군이 없으면 새 숫자를 믿을 수 없다).
    #    그래서 대조군을 남기고 확장분을 **추가**한다.
    control = [f for f in flowers if f["id"] <= 200]
    if len(control) != 200:
        sys.exit(f"대조군 200종을 못 뽑았다: {len(control)}종")

    def slim(fs):
        # `bloom_source`도 담는다 — **개화월 필터가 도는지 재려면 이 축이 필요하다.**
        # 근거 있는 종(human·draft·observed)과 근거 없는 종(peak_window·unknown)을
        # 섞어 놓고 후보풀을 세면 **필터가 도는지 원리상 알 수 없다**:
        # 후자는 의도적으로 전 달에 오르기 때문이다(계약 1-2-b ⑤).
        # 개월 수로 대신 가르려 했더니 peak_window(9개월)가 근거 있는 쪽에 섞여
        # 1,040종이어야 하는 표본이 1,770종이 됐다 — **다른 것을 재고 있었다.**
        return [
            {"id": f["id"], "name": f["name"],
             "scientific_name": f["scientific_name"], "bloom_months": f["bloom_months"],
             "bloom_source": f["bloom_source"]}
            for f in fs
        ]

    # 🔴 **대조군이 조용히 바뀌면 대조군이 아니다.** 확장 CSV의 초안으로 id 1~200을
    #    덮으면 개화월이 167종 바뀌고 Top-1이 77.0% → 50.0%가 된다(계약 1-1-a 실측).
    #    그런데 그 상태로 픽스처를 다시 만들면 **테스트의 기대값도 같이 바뀌어**
    #    아무것도 빨개지지 않는다 — 검사가 자기 정답을 다시 쓰는 것이다.
    #    그래서 기존 픽스처와 **한 종씩 비교**하고, 다르면 멈춘다.
    if OUT.exists():
        prev = {f["id"]: f for f in json.loads(OUT.read_text(encoding="utf-8"))["flowers"]}
        changed = [
            f["id"] for f in slim(control)
            if f["id"] in prev and (
                f["bloom_months"] != prev[f["id"]]["bloom_months"]
                or f["scientific_name"] != prev[f["id"]]["scientific_name"])
        ]
        if changed:
            sys.exit(
                f"🔴 대조군 200종의 값이 {len(changed)}종 바뀌었다: {changed[:10]}\n"
                "  개화월·학명이 바뀌면 iOS 실측 77.0%와 비교할 수 없다(계약 1-1-a).\n"
                "  의도한 변경이면 진행.md에 근거를 적고 이 검사를 통과시킨 뒤\n"
                "  PlantNetReplayTest의 기대값도 **재측정한 숫자로** 고친다.")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "note": "iOS 실측(진행 (19)) 캐시에서 생성. 재생성: "
                "python3 android/_tools/build_plantnet_replay_fixture.py",
        "class_to_genera": CLASS_TO_GENERA,
        "class_peak_month": CLASS_PEAK_MONTH,
        # 대조군. **이 배열의 값은 확장 전과 같아야 한다** — 사람이 정한 200종을
        # 그대로 쓰는 것이 계약 1-1-a이고, 값이 움직이면 77.0%도 움직인다.
        "flowers": slim(control),
        # 앱이 실제로 싣는 도감. 확장이 판별을 어떻게 바꾸는지 재는 데 쓴다.
        "flowers_full": slim(flowers),
        "photos": photos,
    }, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"{len(photos)}장{'(재사용)' if reused else ''} · 대조군 {len(control)}종 · "
          f"전체 {len(flowers)}종 → {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    main()
