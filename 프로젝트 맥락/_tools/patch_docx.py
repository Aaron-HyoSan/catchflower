# -*- coding: utf-8 -*-
"""기획서 docx에 꽃 도감 범위 / 등급 기준 / 일러스트 정책 절을 추가한다.

- 기존 장 번호(7~16)를 건드리지 않기 위해 6장 안에 절을 늘리는 방식으로 넣는다.
- 15장에는 도감 확정 관련 검토 항목을 추가한다.
- 문서 끝에 17장(관련 산출물)을 추가한다.
- 문단 스타일은 원본과 동일하게 유지한다 (Heading3 / FirstParagraph / BodyText / Compact 불릿).
"""
import copy
import os
import sys
from collections import Counter

from docx import Document

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))          # game-project
sys.path.insert(0, os.path.join(ROOT, "꽃도감", "_tools"))
from flowers import FLOWERS, validate  # noqa: E402

DOCX = os.path.join(os.path.dirname(HERE), "캐치플라워 게임 기획서.docx")

# ── 통계는 하드코딩하지 않고 원본 데이터에서 뽑는다 ────────────────────────
err = validate()
if err:
    print("꽃 데이터 검증 실패 — 기획서를 수정하지 않는다:")
    for e in err:
        print(" -", e)
    raise SystemExit(1)

cs = Counter(r[5] for r in FLOWERS)
cr = Counter(r[7] for r in FLOWERS)
ca = Counter(r[9] for r in FLOWERS)
cb = Counter(r[11] for r in FLOWERS)
cc = Counter(r[6] for r in FLOWERS)
N = len(FLOWERS)

doc = Document(DOCX)
ps = doc.paragraphs

# 재실행 방지 — 이 스크립트는 멱등이 아니다 (두 번 돌리면 절이 중복된다)
if any(p.text.strip() == "도감 등록 범위" for p in ps):
    print("이미 적용된 문서다. 중복 삽입을 막기 위해 종료한다.")
    print("다시 적용하려면 백업본에서 복원한 뒤 실행할 것.")
    raise SystemExit(0)

# 불릿 pPr 템플릿 (Compact + numPr) 확보
BULLET_PPR = None
for p in ps:
    if p.style.name == "Compact" and p._p.pPr is not None and p._p.pPr.numPr is not None:
        BULLET_PPR = copy.deepcopy(p._p.pPr)
        break
assert BULLET_PPR is not None, "불릿 스타일 템플릿을 찾지 못했다"


def find(text, style=None):
    for p in doc.paragraphs:
        if p.text.strip() == text and (style is None or p.style.name == style):
            return p
    raise KeyError(text)


class Inserter:
    """지정 문단 '앞'에 순서대로 문단을 삽입한다."""

    def __init__(self, anchor):
        self.anchor = anchor._p

    def _new(self, style):
        p = doc.add_paragraph(style=style)
        self.anchor.addprevious(p._p)
        return p

    def h(self, text, level=3):
        self._new(f"Heading {level}").add_run(text)

    def first(self, text):
        self._new("First Paragraph").add_run(text)

    def body(self, text):
        self._new("Body Text").add_run(text)

    def block(self, text):
        self._new("Block Text").add_run(text)

    def li(self, text):
        p = self._new("Compact")
        p._p.get_or_add_pPr()
        p._p.replace(p._p.pPr, copy.deepcopy(BULLET_PPR))
        p.add_run(text)

    def blank(self):
        self._new("Normal")


# ══════════════════════════════════════════════════════════════════════
# 1) 6장 — 기존 '꽃 분류 기준' 절 보강
# ══════════════════════════════════════════════════════════════════════
anchor6 = find("7. 지도 시스템", "Heading 2")
# 6장 끝의 빈 문단 앞에 넣기 위해 빈 문단을 앵커로 삼는다
prev = anchor6._p.getprevious()
ins = Inserter(anchor6)
if prev is not None and (prev.text or "").strip() == "":
    class _W:  # 빈 문단을 앵커로 쓰기 위한 래퍼
        _p = prev
    ins = Inserter(_W())

ins.body("1차 도감 범위는 아래와 같이 200종으로 확정했다. AI 검증 결과에 따라 "
         "조정하는 것은 유사종의 통합 여부이며, 도감 규모 200종은 유지한다.")

ins.h("도감 등록 범위")
ins.first(f"도감에 등록되는 꽃은 총 {N}종으로 확정한다.")
ins.li(f"전체 종 목록은 별도 데이터 파일로 관리한다. 이 문서는 선정·분류 기준만 정의한다.")
ins.li("도감 번호는 고정 ID이며 출시 후 변경하지 않는다. 사용자의 발견 기록이 이 번호에 연결되기 때문이다.")
ins.li("계절 분포: " + ", ".join(f"{k} {cs[k]}종" for k in ["봄", "여름", "가을", "겨울"] if cs[k])
       + ". 겨울은 개화종 자체가 적어 계절 필터에서 종수 편차를 안내해야 한다.")
ins.li(f"희귀도 분포: " + ", ".join(f"{k} {cr[k]}종" for k in ["흔함", "보통", "귀함"]) + ".")
ins.li(f"{N}종에 없는 꽃을 촬영했을 때의 처리는 15장 검토 항목으로 둔다.")

ins.h("종 선정 기준")
ins.first("아래 다섯 가지 기준으로 종을 선정했다.")
ins.li("한국에서 실제로 만날 수 있는 꽃만 넣는다. 산책로, 공원, 화단, 길가에서 눈에 띄는 종을 "
       "우선했고 깊은 산의 희귀 야생화는 최소로 제한했다.")
ins.li("주 타깃(40~50대 여성)의 생활 반경을 기준으로 삼는다. 도심 화단 원예종(팬지, 페튜니아, "
       "메리골드 등)을 야생화와 동등하게 포함한 이유이며, 실제 촬영 빈도는 이쪽이 더 높다.")
ins.li("AI가 구분할 수 있는 수준에서 종을 쪼갠다. 품종 단위로 내려가지 않는다.")
ins.li("나무꽃(벚꽃, 목련, 능소화 등)도 포함한다. 사용자 인식에서 '꽃'이며 봄 촬영 수요의 "
       "상당 부분을 차지한다.")
ins.li("억새, 수크령은 엄밀히는 꽃이 아닌 꽃이삭이지만 가을 촬영 수요가 높아 포함한다.")

ins.h("종별 관리 항목")
ins.first("종마다 아래 항목을 관리한다. 앱의 필터, 도감 상세, 축하 연출 강도가 이 값에 연동된다.")
ins.li("도감번호 — 고정 ID. 출시 후 변경 금지")
ins.li("이름, 학명, 과 — 도감 상세에 노출. 이름은 정식명보다 대중 인지명을 우선한다")
ins.li("개화기, 계절 — 계절 필터와 제철 추천의 기준")
ins.li("대표색 — 색상 필터의 기준. 품종별 색 변이가 큰 종은 '기타'로 둔다")
ins.li("희귀도 — 발견 난이도. 신규 발견 축하 연출의 강도에 연동한다")
ins.li("AI난이도 — 오인식 위험도. 판별 신뢰도 임계값 조정의 근거")
ins.li("비슷한 꽃 — 도감 상세와 판별 결과 화면에 노출해 사용자가 오등록을 스스로 걸러내게 한다")
ins.li("주요서식지 — 어디서 찾을지에 대한 안내 문구의 근거")

ins.h("희귀도 기준")
ins.li(f"흔함({cr['흔함']}종) — 도심 생활 반경에서 해당 계절에 거의 확실히 만난다. "
       "온보딩 추천 꽃의 후보군이다.")
ins.li(f"보통({cr['보통']}종) — 공원이나 산책로를 찾아가면 만날 수 있다.")
ins.li(f"귀함({cr['귀함']}종) — 특정 지역이나 시기에만 있어 의도적으로 찾아가야 만난다. "
       "신규 발견 시 축하 연출을 가장 강하게 준다.")

ins.h("AI 판별 난이도 기준")
ins.first("유사종과의 형태 차이를 기준으로 세 등급으로 나눈다. 15장의 AI 인식 범위 검증에서 "
          "이 등급별로 결과를 확인한다.")
ins.li(f"하({ca['하']}종) — 형태가 특이해 오인식 가능성이 낮다.")
ins.li(f"중({ca['중']}종) — 유사종이 있으나 육안으로 구분이 가능하다. 도감 상세에 "
       "'비슷한 꽃'을 노출한다.")
ins.li(f"상({ca['상']}종) — 유사종과 형태가 거의 같다. 판별 신뢰도 임계값을 높이거나 "
       "유사종을 하나로 통합해 표시하는 방안을 검토한다.")

ins.h("표시명 및 유사종 통합 정책")
ins.first("판별 난이도 '상' 종에서 실제 문제가 되는 것은 AI 성능보다 도감 설계다. "
          "사용자가 구분하지 못하는 두 종을 별개 항목으로 두면 도감이 채워지지 않는다.")
ins.li("표시명은 대중 인지명을 우선한다. 예를 들어 종은 왕벚나무이지만 도감 표시명은 '벚꽃'으로 둔다.")
ins.li("사용자가 육안으로 구분할 수 없는 유사종은 하나의 표시명으로 통합하고, 통합된 종은 "
       "'비슷한 꽃' 설명에 남긴다.")
ins.li("통합 후보 그룹은 민들레류, 개망초·망초류, 금계국류, 쑥부쟁이류, 국화과 노란 꽃, "
       "석죽과 흰 꽃, 여뀌류, 메리골드류, 팬지·비올라, Prunus속 나무꽃의 10개 그룹이다.")
ins.li(f"통합을 선택하면 종수가 줄어든다. 이 경우 지역 한정 종을 추가해 {N}종을 유지한다.")
ins.li("통합 여부는 도감 번호 재배치를 유발하므로 출시 전에 반드시 확정한다.")

ins.h("꽃 일러스트 정책")
ins.first(f"{N}종 전체에 종별 일러스트가 필요하다. 미발견 종도 도감 그리드에 실루엣으로 "
          "표시되므로 일러스트가 곧 도감 화면 그 자체다.")
ins.li("형식은 벡터(SVG)로 한다. 같은 그림을 도감 그리드, 상세 히어로, 최근 발견, "
       "랭킹 대표 꽃의 4가지 크기로 쓰기 때문이다.")
ins.li("가장 작은 크기(약 32픽셀)에서도 종이 구별되는 것을 최우선 요구사항으로 한다. "
       "세밀한 묘사보다 형태와 색의 식별성을 앞세운다.")
ins.li("실제 꽃 크기와 무관하게 아트보드 대비 꽃의 비율을 종마다 통일한다. "
       "그리드에서 크기가 들쭉날쭉해 보이면 안 된다.")
ins.li("판별 난이도 '상' 종은 유사종과의 구분 포인트가 그림에 드러나야 한다. "
       "사용자는 이 그림을 보고 자기가 찍은 꽃이 맞는지 판단한다.")
ins.li(f"발주는 3배치로 나눈다. 배치 1({cb[1]}종)은 전국 어디서나 흔한 종과 온보딩 추천 종, "
       f"배치 2({cb[2]}종)는 계절마다 흔한 종으로 둘 다 출시 필수다. "
       f"배치 3({cb[3]}종)은 발견 확률이 낮아 출시 후 순차 납품이 가능하다.")
ins.li(f"흰색 꽃이 {cc['흰색']}종으로 가장 많다. 흰 배경에서 형태가 사라지지 않도록 "
       "연한 외곽선 또는 명도차 처리를 요구한다.")

# ══════════════════════════════════════════════════════════════════════
# 2) 15장 — 도감 확정 관련 검토 항목 추가
# ══════════════════════════════════════════════════════════════════════
anchor16 = find("16. 핵심 기획 요약", "Heading 2")
prev16 = anchor16._p.getprevious()
if prev16 is not None and (prev16.text or "").strip() == "":
    class _W16:
        _p = prev16
    ins15 = Inserter(_W16())
else:
    ins15 = Inserter(anchor16)

ins15.h("도감 200종 확정 관련")
ins15.li("유사종 통합 그룹 10개의 통합 여부. AI 검증 후 확정하며, 도감 번호 재배치를 "
         "유발하므로 출시 전에 반드시 결정해야 한다.")
ins15.li("도감에 없는 꽃을 촬영했을 때의 처리. 안내 문구와 제보 수집 여부를 정해야 한다.")
ins15.li("AI 판별 신뢰도 임계값. 난이도 '상' 종은 임계값을 높여 오등록을 줄이는 방안을 검토한다.")
ins15.li("꽃이 아닌 꽃이삭(억새, 수크령)의 포함 범위. 갈대, 강아지풀까지 넓힐지 여부.")
ins15.li("겨울 개화종이 6종뿐이므로 겨울 시즌의 콘텐츠 보완 방안이 필요하다. "
         "겨울 시즌 경쟁 기준을 종수 대신 발견 횟수로 두는 방안을 검토한다.")

# ══════════════════════════════════════════════════════════════════════
# 3) 17장 — 관련 산출물
# ══════════════════════════════════════════════════════════════════════
p = doc.add_paragraph(style="Normal")
h = doc.add_paragraph(style="Heading 2")
h.add_run("17. 관련 산출물")
fp = doc.add_paragraph(style="First Paragraph")
fp.add_run("이 기획서를 기준으로 아래 산출물을 작성했다. 화면 문구와 도감 데이터의 "
           "최신 값은 각 파일이 기준이다.")


def tail_li(text):
    q = doc.add_paragraph(style="Compact")
    q._p.get_or_add_pPr()
    q._p.replace(q._p.pPr, copy.deepcopy(BULLET_PPR))
    q.add_run(text)


tail_li("와이어프레임 23장 — 온보딩부터 시즌 종료까지 전체 화면. 화면별로 기획서 근거 장과 "
        "미정의 항목을 함께 표기했다.")
tail_li("전체 흐름도 — 23화면의 연결 관계와 조건 분기.")
tail_li(f"꽃 목록 {N}종 데이터 — 도감번호, 학명, 계절, 대표색, 희귀도, AI난이도, 비슷한 꽃, "
        "일러스트 배치를 종별로 관리한다.")
tail_li("문구·버튼 스펙 — 23화면에 들어가는 모든 문구와 버튼 텍스트의 확정안, 토스트와 "
        "다이얼로그 문구 포함.")
tail_li("화면 UI 디자인 업무 목록 — 디자인 시스템부터 반응형 검수까지의 작업 단위와 선행 관계.")
tail_li(f"꽃 일러스트 발주서 — {N}종의 규격, 아트 방향, 배치, 검수 기준.")

bt = doc.add_paragraph(style="Body Text")
bt.add_run("산출물과 이 문서가 어긋날 경우, 게임 규칙은 이 기획서를 기준으로 하고 "
           "화면 문구와 도감 데이터는 산출물을 기준으로 한다.")

# ══════════════════════════════════════════════════════════════════════
# 4) 장 사이 빈 문단 정리 — 삽입으로 장 중간에 남은 빈 문단을 다음 장 앞으로 옮긴다
# ══════════════════════════════════════════════════════════════════════
for head_text in ("7. 지도 시스템", "16. 핵심 기획 요약"):
    head = find(head_text, "Heading 2")._p
    if (head.getprevious() is None
            or "".join(head.getprevious().itertext()).strip() != ""):
        blank = next(p._p for p in doc.paragraphs
                     if p.style.name == "Normal" and not p.text.strip()
                     and p._p.getnext() is not None)
        head.addprevious(blank)

doc.save(DOCX)
print(f"저장 완료: {DOCX}")
print(f"  총 문단 {len(Document(DOCX).paragraphs)}개")
