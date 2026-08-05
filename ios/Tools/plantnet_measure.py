#!/usr/bin/env python3
"""A-1 실측 — PlantNet이 실제 꽃 사진을 맞히는지 라벨된 데이터셋으로 잰다.

**왜 스크립트인가.** 시뮬레이터로도 카메라가 없어서 사진을 못 넣는다.
그런데 재려는 건 카메라가 아니라 **네트워크 응답의 정확도**다 —
`PlantNetRecognizer`가 하는 계산(개화월 하드 필터 + 학명 색인)을 여기서
같은 규칙으로 재현하면 실기기 없이 합격선을 판정할 수 있다.

⚠️ **원본 응답을 전부 캐시한다** (오너 규칙: 재과금 없이 재채점).
   두 번째 실행은 API를 부르지 않고 캐시만 읽는다. 채점 규칙을 고쳐도 쿼터가 안 준다.

⚠️ **무료 쿼터 500/일.** --limit 으로 클래스당 장수를 제한한다.
   호출 전 남은 쿼터를 응답 헤더(X-Remaining-Requests)로 기록한다.

사용:
    python3 plantnet_measure.py --limit 40            # 호출 + 채점
    python3 plantnet_measure.py --limit 40 --score-only   # 캐시로만 재채점
"""
import argparse
import hashlib
import json
import os
import pathlib
import sys
import urllib.request
import urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[2]
FLOWERS_JSON = ROOT / "공용_적재" / "flowers.json"
SECRETS = ROOT / "ios" / "Config" / "Secrets.xcconfig"
DATASET = pathlib.Path("/private/tmp/flower_photos")
CACHE = pathlib.Path("/private/tmp/plantnet_cache")

# GamePolicy.swift 와 같은 값이어야 한다. 여기서 흩어지면 측정이 앱을 설명하지 못한다.
IDENTIFY_FAILURE_FLOOR = 0.30
CANDIDATE_COUNT = 3
CONFIDENCE_THRESHOLD = {"low": 0.60, "mid": 0.70, "high": 0.85}

# TF-flowers 5클래스 → 우리 도감에서 "정답으로 인정할 id 집합".
# **속 단위로 인정한다** — PlantNetRecognizer 주석과 같은 근거다:
# 사용자에게 `Taraxacum platycarpum`과 `Taraxacum officinale`는 둘 다 "민들레"다.
# daisy는 라벨이 느슨하다(TF-flowers의 daisy에는 Bellis 외에 Leucanthemum·
# Argyranthemum 등이 섞여 있다) — 국화과 흰꽃 여러 속을 인정 후보로 둔다.
CLASS_TO_GENERA = {
    "dandelion": ["Taraxacum"],
    "roses": ["Rosa"],
    "sunflowers": ["Helianthus"],
    "tulips": ["Tulipa"],
    "daisy": ["Bellis", "Leucanthemum", "Chrysanthemum", "Argyranthemum", "Aster"],
}


def read_api_key():
    for line in SECRETS.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("PLANTNET_API_KEY"):
            return line.split("=", 1)[1].strip()
    sys.exit("PLANTNET_API_KEY 없음")


def normalize(name):
    """ScientificNameIndex.normalize() 를 그대로 옮긴 것."""
    n = name.lower().replace("var.", " ").replace("subsp.", " ")
    return " ".join(n.split()[:2])


class Index:
    """ScientificNameIndex 를 그대로 옮긴 것. 스위프트 쪽을 고치면 여기도 고친다.

    `preferring`(=이번 달 개화 후보)을 받아서 속 안에서 살아남을 종을 고른다.
    `legacy=True`로 부르면 수정 전 동작(속 대표=도감번호 최솟값)을 재현한다 —
    수정이 실제로 얼마를 되찾았는지 같은 캐시로 비교하기 위한 것이다.
    """

    def __init__(self, flowers):
        self.exact, self.by_genus = {}, {}
        for f in flowers:
            n = normalize(f["scientific_name"])
            if not n:
                continue
            self.exact[n] = f["id"]
            self.by_genus.setdefault(n.split()[0], []).append(f["id"])
        self.by_genus = {k: sorted(v) for k, v in self.by_genus.items()}

    def flower_id(self, scientific_name, preferring=None, legacy=False):
        n = normalize(scientific_name)
        if legacy:
            preferring = None
        if n in self.exact and (preferring is None or self.exact[n] in preferring):
            return self.exact[n], "exact"
        g = n.split()[0] if n.split() else ""
        same = self.by_genus.get(g)
        if same is None:
            return (self.exact.get(n), "exact") if n in self.exact else (None, "miss")
        if preferring is not None:
            hit = next((i for i in same if i in preferring), None)
            if hit is not None:
                return hit, "genus"
            return (self.exact[n], "exact") if n in self.exact else (None, "miss")
        return same[0], "genus"


def call_plantnet(path, api_key):
    """캐시가 있으면 API를 부르지 않는다. 반환: (응답dict, 남은쿼터 or None)"""
    digest = hashlib.sha1(path.read_bytes()).hexdigest()[:16]
    cached = CACHE / f"{digest}.json"
    if cached.exists():
        return json.loads(cached.read_text()), None

    boundary = "catchflower-measure"
    body = b""
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="organs"\r\n\r\nflower\r\n'
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="images"; filename="f.jpg"\r\n'
    body += b"Content-Type: image/jpeg\r\n\r\n"
    body += path.read_bytes()
    body += f"\r\n--{boundary}--\r\n".encode()

    url = (
        "https://my-api.plantnet.org/v2/identify/k-eastern-asia"
        f"?api-key={api_key}&include-related-images=false&nb-results=10"
    )
    req = urllib.request.Request(
        url, data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = json.loads(resp.read())
            remaining = resp.headers.get("X-Remaining-Requests")
    except urllib.error.HTTPError as e:
        # 404 = 인식 결과 없음. 앱에서도 오류가 아니라 판별 실패다 — 캐시해서 센다.
        if e.code == 404:
            payload, remaining = {"results": []}, e.headers.get("X-Remaining-Requests")
        elif e.code == 429:
            raise SystemExit("429 쿼터 초과 — 여기까지 캐시됨. 내일 --limit 낮춰 재시도")
        else:
            raise SystemExit(f"HTTP {e.code}: {e.read()[:300]}")
    CACHE.mkdir(parents=True, exist_ok=True)
    cached.write_text(json.dumps(payload, ensure_ascii=False))
    return payload, remaining


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=40, help="클래스당 사진 수")
    ap.add_argument("--month", type=int, default=8, help="개화월 필터를 고정할 달")
    ap.add_argument("--month-fixed", action="store_true",
                    help="모든 클래스를 --month 한 달로 잰다 (제철 아닌 꽃이 떨어지는 걸 확인할 때)")
    ap.add_argument("--score-only", action="store_true", help="캐시만 쓰고 API 호출 금지")
    args = ap.parse_args()

    flowers = json.load(open(FLOWERS_JSON, encoding="utf-8"))["flowers"]
    by_id = {f["id"]: f for f in flowers}
    index = Index(flowers)
    api_key = read_api_key()

    # 개화월 하드 필터 — PlantNetRecognizer가 `candidates`로 받는 집합.
    #
    # **클래스마다 제철 달로 재는 게 맞다.** 8월 하나로 전부 재면 튤립·데이지가
    # 0%로 나오는데 그건 결함이 아니라 **필터가 옳게 동작한 것**이다 —
    # 8월에 튤립을 찍으면 앱은 판별 실패라고 말해야 한다.
    # 한 달로 고정해 재면 "필터 때문에 떨어진 것"과 "인식이 틀린 것"이 섞인다.
    def season_set(month):
        return {f["id"] for f in flowers if month in f["bloom_months"]}

    CLASS_PEAK_MONTH = {          # 우리 도감의 해당 속 개화월에서 고른 대표 달
        "dandelion": 4,           # 민들레 3~5월
        "roses": 6,               # 장미 5~10 · 찔레꽃 5~6 · 해당화 5~7
        "sunflowers": 8,          # 해바라기 7~9월
        "tulips": 4,              # 튤립 4~5월
        "daisy": 5,               # 데이지 4~5월
    }
    season_cache = {}

    # 클래스별 정답 id 집합 (속 단위 인정)
    truth = {}
    for cls, genera in CLASS_TO_GENERA.items():
        truth[cls] = {
            f["id"] for f in flowers
            if f["scientific_name"].split()[0] in genera
        }

    rows = []
    for cls in sorted(CLASS_TO_GENERA):
        photos = sorted((DATASET / cls).glob("*.jpg"))[: args.limit]
        for p in photos:
            digest = hashlib.sha1(p.read_bytes()).hexdigest()[:16]
            if args.score_only and not (CACHE / f"{digest}.json").exists():
                continue
            payload, remaining = call_plantnet(p, api_key)
            raw = [
                (r["score"], r["species"]["scientificNameWithoutAuthor"])
                for r in payload.get("results", [])
            ]
            rows.append({"cls": cls, "file": p.name, "raw": raw})
            if remaining:
                print(f"  [{len(rows):4d}] 남은 쿼터 {remaining}", file=sys.stderr)

    # ---- 채점 ----
    print(f"\n{'='*78}\nA-1 실측 — PlantNet k-eastern-asia")
    if args.month_fixed:
        print(f"개화월 필터: **{args.month}월 고정** — 제철 아닌 클래스는 떨어지는 게 정상이다")
    else:
        print("개화월 필터: 클래스별 제철 달 " +
              ", ".join(f"{c}={m}월" for c, m in sorted(CLASS_PEAK_MONTH.items())))
    print(f"사진 {len(rows)}장 · 합격선 Top-1 70%\n{'='*78}\n")

    stats = {}
    for row in rows:
        cls = row["cls"]
        # 이 클래스의 제철 달로 후보 집합을 만든다 (--month 를 주면 그 달로 고정)
        month = args.month if args.month_fixed else CLASS_PEAK_MONTH[cls]
        if month not in season_cache:
            season_cache[month] = season_set(month)
        in_season = season_cache[month]
        s = stats.setdefault(cls, {
            "n": 0, "raw_top1": 0, "raw_top3": 0, "empty": 0,
            "pipe_top1": 0, "pipe_top3": 0, "pipe_empty": 0, "below_floor": 0,
            "legacy_pipe_top1": 0, "legacy_pipe_top3": 0,
            "legacy_pipe_empty": 0, "legacy_below_floor": 0,
            "genus_hits": 0, "wrong_genus_redirect": 0, "scores": [],
        })
        s["n"] += 1
        if not row["raw"]:
            s["empty"] += 1

        # 1층: 원시 인식 — PlantNet이 속을 맞혔나 (필터·색인 무관)
        if row["raw"]:
            top_name = row["raw"][0][1]
            if top_name.split()[0] in CLASS_TO_GENERA[cls]:
                s["raw_top1"] += 1
            if any(n.split()[0] in CLASS_TO_GENERA[cls] for _, n in row["raw"][:3]):
                s["raw_top3"] += 1
            s["scores"].append(row["raw"][0][0])

        # 2층: 앱 파이프라인 — 색인 + 개화월 필터 + candidateCount.
        # legacy(수정 전)와 fixed(수정 후)를 **같은 캐시로** 나란히 잰다.
        def run_pipeline(legacy):
            picked, seen = [], set()
            for score, name in row["raw"]:
                fid, how = index.flower_id(
                    name, preferring=None if legacy else in_season, legacy=legacy
                )
                if fid is None:
                    continue
                if (not legacy) and how == "genus" and fid not in truth[cls] \
                        and name.split()[0] in CLASS_TO_GENERA[cls]:
                    # 속은 맞는데 색인이 정답 밖 id로 보냈다 — 색인 결함이 남은 것이다
                    s["wrong_genus_redirect"] += 1
                if fid not in in_season or fid in seen:
                    continue
                seen.add(fid)
                if (not legacy) and how == "genus":
                    s["genus_hits"] += 1
                picked.append((fid, score))
                if len(picked) == CANDIDATE_COUNT:
                    break
            return picked

        for legacy, prefix in ((True, "legacy_"), (False, "")):
            picked = run_pipeline(legacy)
            if not picked:
                s[prefix + "pipe_empty"] = s.get(prefix + "pipe_empty", 0) + 1
                continue
            if picked[0][1] < IDENTIFY_FAILURE_FLOOR:
                s[prefix + "below_floor"] = s.get(prefix + "below_floor", 0) + 1
            if picked[0][0] in truth[cls]:
                s[prefix + "pipe_top1"] = s.get(prefix + "pipe_top1", 0) + 1
            # 정답이 3후보 안에 있나 (사용자가 화면 09에서 고를 수 있는가)
            if any(fid in truth[cls] for fid, _ in picked):
                s[prefix + "pipe_top3"] = s.get(prefix + "pipe_top3", 0) + 1

    keys = ["n", "raw_top1", "raw_top3", "pipe_top1", "pipe_top3", "pipe_empty",
            "below_floor", "legacy_pipe_top1", "legacy_pipe_top3", "legacy_pipe_empty"]
    print(f"{'클래스':<11}{'장수':>5}{'원시T1':>8}{'원시T3':>8}"
          f"{'구T1':>7}{'앱T1':>7}{'앱T3':>7}{'후보0':>6}{'floor↓':>7}{'중위':>8}")
    print("-" * 74)
    tot = {k: 0 for k in keys}
    for cls in sorted(stats):
        s = stats[cls]
        med = sorted(s["scores"])[len(s["scores"]) // 2] if s["scores"] else 0.0
        print(f"{cls:<11}{s['n']:>5}"
              f"{s['raw_top1']/s['n']*100:>7.1f}%"
              f"{s['raw_top3']/s['n']*100:>7.1f}%"
              f"{s['legacy_pipe_top1']/s['n']*100:>6.1f}%"
              f"{s['pipe_top1']/s['n']*100:>6.1f}%"
              f"{s['pipe_top3']/s['n']*100:>6.1f}%"
              f"{s['pipe_empty']:>6}{s['below_floor']:>7}{med:>8.3f}")
        for k in keys:
            tot[k] += s[k]

    print("-" * 74)
    n = tot["n"]
    print(f"{'합계':<11}{n:>5}"
          f"{tot['raw_top1']/n*100:>7.1f}%"
          f"{tot['raw_top3']/n*100:>7.1f}%"
          f"{tot['legacy_pipe_top1']/n*100:>6.1f}%"
          f"{tot['pipe_top1']/n*100:>6.1f}%"
          f"{tot['pipe_top3']/n*100:>6.1f}%"
          f"{tot['pipe_empty']:>6}{tot['below_floor']:>7}")
    print("\n  원시T1/T3 = PlantNet 자체 정확도(속 단위) · 구T1 = 색인 수정 전 앱")
    print("  앱T1/T3   = 색인 수정 후 앱 (개화월 하드 필터 + 후보 3개 통과)")

    print("\n[점수 분포 — identifyFailureFloor=0.30 이 무엇을 버리는가]")
    allscores = sorted(sc for s in stats.values() for sc in s["scores"])
    if allscores:
        for q, label in [(0.10, "10%"), (0.25, "25%"), (0.50, "중위"), (0.75, "75%"), (0.90, "90%")]:
            print(f"  {label:>4} 분위 {allscores[int(len(allscores)*q)]:.3f}")
        below = sum(1 for sc in allscores if sc < IDENTIFY_FAILURE_FLOOR)
        print(f"  0.30 미달: {below}/{len(allscores)} ({below/len(allscores)*100:.1f}%)")
        for name, th in sorted(CONFIDENCE_THRESHOLD.items(), key=lambda x: x[1]):
            n = sum(1 for sc in allscores if sc >= th)
            print(f"  {name} 임계 {th:.2f} 이상(1순위 크게 표시): {n}/{len(allscores)} ({n/len(allscores)*100:.1f}%)")

    print("\n[색인 결함 — 속은 맞는데 정답 밖 id로 보낸 횟수]")
    for cls in sorted(stats):
        print(f"  {cls:<12} genus fallback {stats[cls]['genus_hits']:>4}회 · "
              f"오분류 {stats[cls]['wrong_genus_redirect']:>4}회")

    out = pathlib.Path("/private/tmp/plantnet_measure_rows.json")
    out.write_text(json.dumps(rows, ensure_ascii=False, indent=1))
    print(f"\n원본 응답 캐시: {CACHE} · 정리표: {out}")


if __name__ == "__main__":
    main()
