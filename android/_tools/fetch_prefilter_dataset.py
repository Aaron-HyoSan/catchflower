#!/usr/bin/env python3
"""1차 필터 실측용 사진 데이터셋을 만든다.

`PreFilterBenchmark`가 읽는 `app/src/androidTest/assets/{flower,notflower}/`를 채운다.

**왜 스크립트인가** — 사진 1,000장이 45MB다. 저장소에 커밋하면 클론하는 사람마다
45MB를 받는데, 실제로 쓰는 건 1차 필터를 다시 측정할 때뿐이다.
그래서 자산은 `.gitignore`로 빼고 **재생성 가능하게** 남긴다.

**왜 고정 시드인가** — 재현율/차단율 숫자를 코드 주석(`MlKitFlowerPreFilter`)에
박아 뒀다. 다른 사진으로 재면 숫자가 달라져 비교가 안 된다. seed 42로 고정한다.

⚠️ `flower/`에는 **오라벨 사진이 섞여 있다** (Flickr 태그 기반 데이터셋이라
   `roses` 태그에 'Roses' 간판·퍼레이드 사진이 들어온다).
   벤치마크의 `mislabeledNonFlowers`가 육안 감사로 걸러낸 목록이다.
   데이터셋을 바꾸면 **그 목록도 다시 감사해야 한다.**

실행:
    python3 android/_tools/fetch_prefilter_dataset.py
"""

from __future__ import annotations

import random
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

SEED = 42
PER_FLOWER_CLASS = 100  # 5클래스 × 100 = 꽃 500장
PER_NEG_CLASS = 50  # 10클래스 × 50 = 꽃아님 500장

FLOWERS_URL = "https://storage.googleapis.com/download.tensorflow.org/example_images/flower_photos.tgz"
IMAGENETTE_URL = "https://s3.amazonaws.com/fast-ai-imageclas/imagenette2-320.tgz"

FLOWER_CLASSES = ["daisy", "dandelion", "roses", "sunflowers", "tulips"]

# Imagenette 10클래스 — 꽃이 아닌 일상 사진이 필요하다.
# 사용자가 실수로 찍을 만한 것(사람·하늘·신발)과 완전한 오브젝트를 섞는다.
NEG_CLASSES = {
    "n01440764": "물고기",
    "n02102040": "개",
    "n02979186": "카세트",
    "n03000684": "기계톱",
    "n03028079": "교회",
    "n03394916": "프렌치호른",
    "n03417042": "쓰레기차",
    "n03425413": "주유펌프",
    "n03445777": "골프공",
    "n03888257": "낙하산",
}

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/androidTest/assets"
CACHE = Path("/tmp/cf_prefilter_cache")


def download(url: str, dest: Path) -> Path:
    if dest.exists():
        print(f"캐시 사용 · {dest.name}")
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"다운로드 · {url}")
    urllib.request.urlretrieve(url, dest)
    return dest


def extract(archive: Path, dest: Path) -> Path:
    if dest.exists():
        print(f"압축 해제 건너뜀 · {dest.name}")
        return dest
    print(f"압축 해제 · {archive.name}")
    with tarfile.open(archive) as tar:
        # filter='data'는 Python 3.12+ 기본값이지만 명시한다 (경로 탈출 방지).
        tar.extractall(dest.parent, filter="data")
    return dest


def pick(src_dir: Path, count: int, rng: random.Random) -> list[Path]:
    files = sorted(p for p in src_dir.iterdir() if p.suffix.lower() in {".jpg", ".jpeg"})
    if len(files) < count:
        sys.exit(f"사진이 부족하다: {src_dir} ({len(files)} < {count})")
    return rng.sample(files, count)


def main() -> None:
    rng = random.Random(SEED)

    flowers_root = extract(
        download(FLOWERS_URL, CACHE / "flower_photos.tgz"), CACHE / "flower_photos"
    )
    imagenette_root = extract(
        download(IMAGENETTE_URL, CACHE / "imagenette2-320.tgz"), CACHE / "imagenette2-320"
    )

    for name in ("flower", "notflower"):
        target = ASSETS / name
        if target.exists():
            shutil.rmtree(target)
        target.mkdir(parents=True)

    total = 0
    for cls in FLOWER_CLASSES:
        for src in pick(flowers_root / cls, PER_FLOWER_CLASS, rng):
            shutil.copy(src, ASSETS / "flower" / f"{cls}_{src.name}")
            total += 1
    print(f"flower/ {total}장")

    total = 0
    for wnid in NEG_CLASSES:
        for src in pick(imagenette_root / "val" / wnid, PER_NEG_CLASS, rng):
            shutil.copy(src, ASSETS / "notflower" / f"{wnid}_{src.name}")
            total += 1
    print(f"notflower/ {total}장")

    print(
        "\n완료. 측정:\n"
        "  ./gradlew :app:connectedDebugAndroidTest "
        "-Pandroid.testInstrumentationRunnerArguments.class="
        "com.catchflower.app.recognizer.PreFilterBenchmark\n"
        "  adb logcat -d -s CfBench"
    )


if __name__ == "__main__":
    main()
