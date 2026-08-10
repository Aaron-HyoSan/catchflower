# -*- coding: utf-8 -*-
"""**적재 스크립트가 실제로 내는 값**을 캐시 200장에 태워, 계약에 적은 수치가
   재현되는지 본다. API 호출 0건.

🔴 이게 없으면 "계약에 75.0%라고 적었다"와 "코드가 75.0%를 낸다"가 다를 수 있다.
   앞선 측정은 별도 하니스가 만든 배열로 잰 것이고, 지금 재는 것은
   `flower_master.build_master()`가 내는 **진짜 출력**이다.
"""
import sys, json
sys.path.insert(0, "공용_적재")
import flower_master as fm

fx = json.load(open("android/app/src/test/resources/plantnet_replay.json"))
FLOOR, CC = 0.05, 3

def norm(n):
    s = n.lower().replace("var.", " ").replace("subsp.", " ")
    return " ".join(s.split()[:2])

class Index:
    def __init__(self, fl):
        self.e, self.g = {}, {}
        for f in fl:
            n = norm(f["scientific_name"])
            if not n: continue
            self.e[n] = f["id"]
            self.g.setdefault(n.split(" ")[0], []).append(f["id"])
        self.g = {k: sorted(v) for k, v in self.g.items()}
    def fid(self, sci, pref):
        n = norm(sci); h = self.e.get(n)
        if h is not None and (pref is None or h in pref): return h
        same = self.g.get(n.split(" ")[0])
        if same is None: return h
        return next((i for i in same if i in pref), h) if pref is not None else same[0]

def parse(res, al, ix):
    out, seen = [], set()
    for r in res:
        f = ix.fid(r["name"], al)
        if f is None or f not in al or f in seen: continue
        seen.add(f); out.append((f, min(max(r["score"], 0.0), 1.0)))
        if len(out) == CC: break
    return out

def replay(fl, label):
    ix = Index(fl)
    mo = {f["id"]: set(f["bloom_months"]) for f in fl}
    gen = {f["id"]: norm(f["scientific_name"]).split(" ")[0] for f in fl}
    pk, tg = fx["class_peak_month"], fx["class_to_genera"]
    t1 = t3 = f12 = n = 0; sz = []
    for p in fx["photos"]:
        cls = p["cls"]
        al = {i for i, m in mo.items() if pk[cls] in m}
        sz.append(len(al))
        c = parse(p["results"], al, ix)
        ok = {g.lower() for g in tg[cls]}
        n += 1
        if c and gen[c[0][0]] in ok: t1 += 1
        if any(gen[x[0]] in ok for x in c): t3 += 1
        if not c or c[0][1] < FLOOR: f12 += 1
    print(f"{label:40s} Top-1 {t1/n*100:5.1f}%  Top-3 {t3/n*100:5.1f}%  화면12 {f12:3d}  평균후보 {sum(sz)//len(sz):5d}")
    return t1/n*100, t3/n*100, f12

print("── 대조군 (커밋된 픽스처 200종) ──")
replay(fx["flowers"], "① 현행 200종  (77.0%여야 한다)")
print()
print("── 적재 스크립트의 실제 출력 ──")
fl, st, dang = fm.build_master()
got = replay(fl, "★ build_master() 2,057종")
print()
print("계약에 적은 값: Top-1 75.0% · Top-3 83.0% · 화면12 42")
want = (75.0, 83.0, 42)
ok = abs(got[0]-want[0]) < 0.1 and abs(got[1]-want[1]) < 0.1 and got[2] == want[2]
print("판정:", "✅ 재현됨" if ok else f"🔴 불일치 — 실제 {got}")
print()
print(fm.format_stats(st, fl))
