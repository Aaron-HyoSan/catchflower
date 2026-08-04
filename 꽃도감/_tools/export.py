# -*- coding: utf-8 -*-
"""꽃 200종 → CSV / 도감 MD / 일러스트 발주 목록 생성."""
import csv
import os
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
OUT = os.path.dirname(HERE)
from flowers import FLOWERS, validate  # noqa: E402

COLS = ["도감번호", "이름", "학명", "과", "개화기", "계절", "대표색", "희귀도",
        "주요서식지", "AI난이도", "비슷한꽃", "일러스트배치", "비고"]


def w_csv():
    p = os.path.join(OUT, "꽃목록_200종.csv")
    with open(p, "w", encoding="utf-8-sig", newline="") as f:
        wr = csv.writer(f)
        wr.writerow(COLS)
        for r in FLOWERS:
            wr.writerow(list(r))
    return p


def w_illust_csv():
    """디자이너 전달용 일러스트 발주 목록 (배치·계절 정렬)."""
    p = os.path.join(OUT, "일러스트_발주목록.csv")
    rows = sorted(FLOWERS, key=lambda r: (r[11], r[5], r[0]))
    with open(p, "w", encoding="utf-8-sig", newline="") as f:
        wr = csv.writer(f)
        wr.writerow(["배치", "순번", "도감번호", "파일명", "꽃이름", "학명",
                     "계절", "대표색", "형태 참고", "특징 (일러스트 시 강조점)",
                     "작업상태", "1차 시안", "피드백", "최종"])
        for i, r in enumerate(rows, 1):
            no, name, sci, fam, bloom, season, color, rar, hab, ai, sim, batch, note = r
            fname = f"flower_{no:03d}_{name}.svg"
            hint = f"{fam} · {bloom} 개화 · {hab}"
            feat = note if note else f"{color} 계열 {season}꽃"
            if sim != "-":
                feat = (feat + " / " if feat else "") + f"'{sim}'와 구분되게"
            wr.writerow([batch, i, no, fname, name, sci, season, color, hint, feat,
                         "미착수", "", "", ""])
    return p


def w_md():
    p = os.path.join(OUT, "꽃도감_200종.md")
    by_season = defaultdict(list)
    for r in FLOWERS:
        by_season[r[5]].append(r)
    season_order = ["봄", "여름", "가을", "겨울"]

    cs = Counter(r[5] for r in FLOWERS)
    cc = Counter(r[6] for r in FLOWERS)
    cr = Counter(r[7] for r in FLOWERS)
    ca = Counter(r[9] for r in FLOWERS)
    cb = Counter(r[11] for r in FLOWERS)

    L = []
    A = L.append
    A("# 캐치플라워 꽃 도감 200종\n")
    A("> 캐치플라워 도감에 등록되는 꽃 전체 목록. 도감 번호는 고정 ID이며 변경하지 않는다.\n")
    A("> 원본 데이터: `_tools/flowers.py` · 배포 파일: `꽃목록_200종.csv`\n")

    A("\n## 요약\n")
    A(f"- 총 **{len(FLOWERS)}종**")
    A("- 계절: " + " · ".join(f"{k} {cs[k]}종" for k in season_order if cs[k]))
    A("- 희귀도: " + " · ".join(f"{k} {cr[k]}종" for k in ["흔함", "보통", "귀함"]))
    A("- 대표색: " + " · ".join(f"{k} {cc[k]}" for k, _ in cc.most_common()))
    A("- AI 판별 난이도: " + " · ".join(f"{k} {ca[k]}종" for k in ["하", "중", "상"]))
    A("- 일러스트 배치: " + " · ".join(f"배치{k} {cb[k]}종" for k in [1, 2, 3]))

    A("\n## 선정 기준\n")
    A("1. **한국에서 실제로 만날 수 있는 꽃**만 넣는다. 산책로·공원·화단·길가에서 "
      "눈에 띄는 종을 우선했고, 심산유곡의 희귀 야생화는 최소로 제한했다.")
    A("2. **주 타깃(40~50대 여성)의 생활 반경**을 기준으로 삼았다. 도심 화단 원예종"
      "(팬지·페튜니아·메리골드 등)을 야생화와 동등하게 포함한 이유다. 실제 촬영 빈도는 "
      "이쪽이 더 높다.")
    A("3. **AI가 구분할 수 있는 수준**에서 종을 쪼갰다. 품종 단위(장미 '아이스버그' 등)로 "
      "내려가지 않고 기획서 6장의 '꽃 종류' 기준을 지켰다.")
    A("4. 나무꽃(벚꽃·목련·능소화 등)도 포함했다. 사용자 인식에서 '꽃'이며 봄 촬영 "
      "수요의 상당 부분을 차지한다.")
    A("5. 억새·수크령은 엄밀히는 꽃이 아닌 꽃이삭이지만, 가을 촬영 수요가 매우 높아 "
      "도감에 넣었다.")

    A("\n## 컬럼 설명\n")
    A("| 컬럼 | 설명 |")
    A("|---|---|")
    for k, v in [
        ("도감번호", "고정 ID. 출시 후 변경 금지 (사용자 도감 기록과 연결)"),
        ("계절", "주 개화 계절. 시즌 컬렉션·계절 필터의 기준"),
        ("대표색", "색상 필터 기준. 품종별 색 변이가 큰 종은 `기타`"),
        ("희귀도", "발견 난이도. `흔함`은 온보딩 추천 후보"),
        ("AI난이도", "`상`은 유사종과 형태가 거의 같아 오인식 위험이 높은 종"),
        ("비슷한꽃", "도감 상세 '비슷한 꽃'에 노출. 오등록을 사용자가 스스로 걸러내게 함"),
        ("일러스트배치", "발주 순서. 1 → 2는 출시 필수, 3은 출시 후 순차 가능"),
    ]:
        A(f"| {k} | {v} |")

    A("\n## 등급·분류 기준\n")
    A("### 희귀도")
    A("| 등급 | 기준 | 종수 |")
    A("|---|---|---|")
    A(f"| 흔함 | 도심 생활 반경에서 해당 계절에 거의 확실히 만난다 | {cr['흔함']} |")
    A(f"| 보통 | 공원·산책로를 찾아가면 만날 수 있다 | {cr['보통']} |")
    A(f"| 귀함 | 특정 지역·시기에만, 의도적으로 찾아가야 만난다 | {cr['귀함']} |")
    A("\n### AI 판별 난이도")
    A("| 등급 | 의미 | 종수 | 대응 |")
    A("|---|---|---|---|")
    A(f"| 하 | 형태가 특이해 오인식 가능성이 낮다 | {ca['하']} | - |")
    A(f"| 중 | 유사종이 있으나 육안 구분이 가능하다 | {ca['중']} | '비슷한 꽃' 노출 |")
    A(f"| 상 | 유사종과 형태가 거의 같다 | {ca['상']} | 통합 표시 검토 또는 신뢰도 임계값 상향 |")

    A("\n## 종 목록\n")
    for s in season_order:
        if not by_season[s]:
            continue
        A(f"### {s} ({len(by_season[s])}종)\n")
        A("| No | 이름 | 학명 | 과 | 개화기 | 색 | 희귀도 | 서식지 | AI | 비슷한 꽃 | 배치 |")
        A("|---:|---|---|---|---|---|---|---|:-:|---|:-:|")
        for r in by_season[s]:
            no, name, sci, fam, bloom, se, color, rar, hab, ai, sim, batch, note = r
            A(f"| {no} | **{name}** | *{sci}* | {fam} | {bloom} | {color} | {rar} "
              f"| {hab} | {ai} | {sim} | {batch} |")
        A("")

    A("\n## AI 오인식 주의 그룹\n")
    A("아래 종들은 형태가 매우 비슷해 AI 판별 난이도가 `상`이다. 개발 전 "
      "**같은 종으로 묶어 표시할지** 결정해야 한다 (기획서 15장 검토 항목).\n")
    groups = [
        ("Prunus속 나무꽃", ["벚꽃", "매화", "살구꽃", "복사꽃", "자두꽃"],
         "꽃자루 길이·잎 동반 여부로 구분. 사용자 대부분이 '벚꽃'으로 인식하므로 "
         "벚꽃만 남기고 나머지를 합치는 안도 가능."),
        ("민들레류", ["민들레", "서양민들레"],
         "총포가 젖혀졌는지로만 구분된다. 도심 개체는 거의 전부 서양민들레. "
         "**'민들레' 하나로 통합 권고.**"),
        ("개망초·망초류", ["개망초", "망초", "실망초", "미국쑥부쟁이"],
         "개망초는 혀꽃이 뚜렷해 구분 가능. 망초·실망초는 통합 권고."),
        ("금계국류", ["큰금계국", "금계국", "기생초"],
         "도로변 군락은 대부분 큰금계국. 기획서 예시에 '금계국'이 쓰였으므로 "
         "**표시명을 '금계국'으로 두고 큰금계국을 흡수**하는 안 권고."),
        ("쑥부쟁이류", ["쑥부쟁이", "벌개미취", "개미취", "미국쑥부쟁이", "참취"],
         "전문가도 어렵다. **'쑥부쟁이' 하나로 통합 권고** (벌개미취는 잎이 뚜렷히 달라 유지 가능)."),
        ("국화과 노란 꽃", ["씀바귀", "고들빼기", "뽀리뱅이", "방가지똥", "왕고들빼기"],
         "잎 모양으로만 구분. 최소 2종으로 축소 권고."),
        ("석죽과 흰 꽃", ["별꽃", "쇠별꽃", "개별꽃", "봄맞이꽃"],
         "'별꽃'으로 통합 권고."),
        ("마디풀과 여뀌류", ["여뀌", "개여뀌", "고마리", "이삭여뀌"],
         "고마리는 형태가 달라 유지, 나머지는 '여뀌'로 통합 권고."),
        ("메리골드류", ["메리골드", "만수국"],
         "크기 차이뿐. '메리골드'로 통합 권고."),
        ("팬지·비올라", ["팬지", "비올라"],
         "크기 차이뿐. 통합 여부는 도감 규모(200종 유지)와 함께 판단."),
    ]
    A("| 그룹 | 해당 종 | 판단 |")
    A("|---|---|---|")
    for gname, members, judg in groups:
        A(f"| {gname} | {' · '.join(members)} | {judg} |")
    A("\n> 통합을 선택하면 200종이 줄어든다. 이 경우 배치 3의 후보종"
      "(지역 한정·산지 야생화)을 추가해 200종을 유지한다.\n")

    A("\n## 미해결 · 결정 필요\n")
    for i, x in enumerate([
        "위 오인식 그룹의 통합 여부 (AI 검증 후 확정). 통합 시 도감 번호 재배치가 "
        "필요하므로 **출시 전에 반드시 확정**해야 한다.",
        "200종에 없는 꽃을 촬영했을 때의 처리. 안내 문구(예: '아직 도감에 없는 꽃이에요')와 "
        "제보 수집 여부를 정해야 한다.",
        "AI 신뢰도 임계값. 난이도 `상` 종은 임계값을 높여 오등록을 줄이는 안 검토.",
        "표시명 정책. 대중 인지명(라일락·아카시아꽃·봄까치꽃 등)을 정식명보다 앞세울지 "
        "— 타깃 고려 시 인지명 우선 권고.",
        "억새·수크령처럼 꽃이 아닌 종의 포함 범위. 갈대·강아지풀까지 넓힐지 여부.",
    ], 1):
        A(f"{i}. {x}")
    A("")

    with open(p, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    return p


if __name__ == "__main__":
    e = validate()
    if e:
        print("검증 실패:")
        for x in e:
            print(" -", x)
        raise SystemExit(1)
    for p in (w_csv(), w_illust_csv(), w_md()):
        print(f"  {os.path.basename(p)}  ({os.path.getsize(p):,} bytes)")
    print(f"\n총 {len(FLOWERS)}종 · 검증 통과")
