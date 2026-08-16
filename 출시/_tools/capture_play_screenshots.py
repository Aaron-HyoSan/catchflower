#!/usr/bin/env python3
"""Play 휴대전화 스크린샷을 **에뮬레이터에서 실제로 돌려서** 만든다.

    python3 출시/_tools/capture_play_screenshots.py            # 촬영 + 합성
    python3 출시/_tools/capture_play_screenshots.py --compose   # 이미 찍힌 원본만 합성

왜 이렇게 만드나
────────────────
Play는 스토어 스크린샷이 **실제 앱 화면**이어야 한다고 요구한다(오해를 유발하는 이미지
금지). 그래서 손으로 그리지 않고, 제출 영상과 **같은 걸음**(`제출물/_tools/record_play_video.py`
의 `run_cuts`)을 태워서 나온 화면을 그대로 쓴다. 🔴 그 파일의 걸음을 여기 베껴 쓰지
않는다 — 두 벌이 되면 영상과 스크린샷이 서로 다른 앱처럼 보인다.

크기를 왜 1080×1920으로 맞추나
──────────────────────────────
기기 화면은 1080×2400(=1:2.22)이다. Play의 일반 규격은 통과하지만, **추천·선별 대상
요건**은 `16:9 또는 9:16 · 각 변 1080px 이상`이다. 그래서 원본을 **줄이지도 자르지도
않고** 1080×1920 캔버스 가운데에 얹는다(배경은 배지 초록 그라데이션 — 그래픽 이미지와
같은 색을 같은 함수에서 뽑는다). 자르면 상태바나 하단 내비가 사라져 실제 화면이
아니게 되고, 늘리면 글자가 뭉개진다.

🔴 **낡은 빌드로 찍는 것이 가장 위험하다.** 화면은 정상이고 파일도 새로 생기는데, 그
   화면이 제출하는 AAB와 다른 앱이다. 그래서 시작할 때 세 가지를 본다:
   ① APK가 설치된 것과 같은 시각인가 ② `ALLOW_BACKUP`이 없는가(= 새 매니페스트인가)
   ③ 화면 크기가 1080×2400인가(`wm size`를 만졌으면 좌표 탭이 엉뚱한 곳을 누른다).

⚠️ **꽃 사진은 벽에 걸어서 진짜 카메라로 찍는다**(`adb emu virtualscene-image`).
   벽 앞까지 걷는 것은 `run_cuts` 안에서 한 번만 한다 — 이미 도착해 있는데 매크로를 또
   재생하면 **처음 자리로 돌아갔다가 다시 걸어온다**(실측: 재생 4초 뒤 프레임이 탁자였고
   도착까지 20초). 카메라 자세는 앱을 다시 띄워도 남으므로, 겨눔 컷은 카메라만 다시
   열어서 찍는다(`aim_shot` — **셔터를 누르지 않는다**).

🔵 **PlantNet 호출은 이 스크립트 한 번에 1건이다**(`run_cuts`의 판별). 종을 더 모으지
   않는 이유는 아래 `POSTER`의 🔴 — 일러스트가 겹쳐서 도감 컷이 망가진다.
"""

import argparse
import importlib.util
import pathlib
import subprocess
import sys
import time

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "출시" / "스토어_그래픽"
RAW = OUT / "_raw"
APK = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
PKG = "com.catchflower.app"

DEVICE = (1080, 2400)
CANVAS = (1080, 1920)  # 9:16 — 위 독스트링
RADIUS = 40

#: 벽에 걸 꽃 사진. 8월에 피는 종만 쓴다 — 개화월 하드필터가 그 외를 후보에서 뺀다
#: (튤립·데이지를 넣으면 후보가 0개가 되고 등록이 안 된다).
#: 🔴 파일명을 짐작하면 안 된다 — 처음 두 개를 지어냈고 `꽃 사진이 없다`로 죽었다.
#: 고른 방법: 가운데 40%의 꽃 색 비율로 100장을 줄 세운 뒤 **눈으로 봤다.** 1등
#: 해바라기는 노란 데이지 **군락** 사진이라 버렸다(안내가 "꽃 한 송이를 네모 안에 꽉
#: 채워 주세요"다 — 데이터셋 라벨이 틀려 있다).
#:
#: 🔴 **왜 해바라기 한 종만 모으나.** 이 앱의 일러스트는 형태·색 템플릿 렌더라
#: **2,057종이 (형태,색) 483조합으로 그려진다.** 그래서 두 종을 모으면 도감
#: `최근 발견한 꽃`에 `해바라기`와 `민들레`가 **똑같은 해바라기 그림**으로 나란히
#: 찍힌다(실측 · 눈으로 확인). 장미로 바꾸면 그림은 갈리는데 **흰 꽃**이 나와서
#: 빨간 사진과 어긋난다(색 근거가 `과`다). 해바라기는 **그림이 맞는 종**이고,
#: 한 종만 모으면 스토어에 나가는 프레임에 어긋난 그림이 하나도 없다.
#: 도감이 `1 / 2044종`으로 보이는 건 새로 깐 앱의 사실이다.
#: (근거·오너 확인 항목: `프로젝트 맥락/진행.md` (82))
POSTER = "sunflowers_4847062576_bae870479c_n.jpg"

#: (원본 이름, 설명, **스토어에 올리나**, 없어도 되는가). 앞의 2~3장이 가장 많이 보인다.
#:
#: 🔴 **찍는 것과 올리는 것을 나눴다.** 예전 판은 찍은 것을 전부 올렸고, 그래서 **지도
#:    오류 화면**(`연결이 불안정해요`)과 **랭킹의 어긋난 그림**이 스토어 규격 검사를
#:    전부 통과했다(크기·색·mtime 초록). 못 올릴 이유가 있는 컷은 `_raw/`에 **증거로
#:    남기고** 합성에서 뺀다 — 지우면 왜 뺐는지가 다음 사람에게 안 남는다.
#:
#: 🔴 **`run_cuts`의 `04_aim`을 쓰지 않는다.** 첫 실행에서 나온 그 컷은 **탁자와 의자**
#:    였다(꽃이 없다) — `walk_to_poster()`가 `adb emu automation play`를 던지고 4초만
#:    잤는데 걸음은 8초 넘게 걸린다. 검사는 전부 초록이었다(단색 아님·크기 맞음·APK보다
#:    새 파일) — **눈으로 봐서 알았다.** 그 4초는 이제 도착 판정으로 바뀌었지만
#:    (`walk_to_poster`), 겨눔 컷은 그래도 `aim_shot`이 **직접 찍고 포스터 일치를 다시
#:    잰다.** 이 폴더의 원본은 전부 이 파일이 찍은 것이어야 한다.
#:
#: 🔴 **`06_register`(등록 축하)와 후보 화면(`11_identify`)은 스토어에 못 올린다.**
#:    둘 다 **일러스트가 주인공인 화면**이고, 일러스트는 (형태,색) 483조합 렌더다.
#:    첫 실행에서 남은 증거(`_raw/11_identify.png` · `_raw/06_register.png`는 지우지
#:    않는다): 후보 세 개가 `해바라기` · `목향` · `민들레`인데 **그림 세 개가 똑같다**
#:    (전부 `ray_disc` 노랑). 게다가 8월인데 `민들레 · 3~5월`이 후보에 보인다
#:    (개화월 필터는 서양민들레 3~10월로 맞췄고, 화면에는 수집 그룹 **대표종**의
#:    개화월이 나온다). 둘 다 오너 확인 항목이고 근거는 `프로젝트 맥락/진행.md` (82)다.
#:    **일러스트가 종마다 달라지면 이 두 컷을 넣는다.**
#:
#: 🔴 **`22_ranking`을 올리지 않는다**(2026-08-17 실측 · 이유 둘). ① 이웃 줄의 `대표 꽃`
#:    라벨과 그림이 어긋난다 — 네 줄이 **똑같은 해바라기 그림**인데 라벨은 `민들레`다
#:    (위 `POSTER`의 🔴과 같은 원인: 483조합). ② 그 이웃 8명은 **내 테스트 계정**이다
#:    (`꽃친구XXXX` = 서버 트리거가 익명에게 준 이름 · `pm clear`를 반복해서 생겼다).
#:    오너가 지우면 이 화면은 나 혼자가 된다 — 지워질 데이터를 스토어에 올리는 셈이다.
#:    `_raw/22_ranking.png`은 종수 표시 수정(`?: 0`)의 증거라서 남긴다.
#:
#: 🔴 **`21_map`은 카카오 콘솔에 릴리스 키해시가 등록되면 자동으로 들어온다.**
#:    지금은 `map_gate()`가 인증을 재서 401이면 **찍지도 않는다** — 예전 판은 오류 화면
#:    (`연결이 불안정해요…`)을 찍어 `screenshot_4.png`으로 올렸고 검사 전부 초록이었다.
SHOTS = [
    # 이름, 설명, 스토어에 올리나, 없어도 되는가
    ("10_aim", "꽃을 겨눈 촬영 화면", True, False),
    ("20_dex", "도감", True, False),
    ("23_detail", "발견 기록 — 내가 찍은 사진", True, True),
    ("24_my", "내 프로필 · 배지", True, True),
    ("21_map", "동네 지도", True, True),
    ("22_ranking", "동네 순위", False, True),
]


def module(path: pathlib.Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def adb(*args, check=False) -> str:
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=300)
    if check and r.returncode != 0:
        raise SystemExit(f"🔴 adb {' '.join(args)} 실패\n{r.stderr or r.stdout}")
    return (r.stdout or "") + (r.stderr or "")


def preflight() -> None:
    if not [l for l in adb("devices").splitlines()[1:] if "\tdevice" in l]:
        raise SystemExit(
            "🔴 기기가 없다. 가상 씬 카메라로 띄운다:\n"
            "   python3 제출물/_tools/record_play_video.py --setup --dry-run"
        )
    size = adb("shell", "wm", "size")
    if f"{DEVICE[0]}x{DEVICE[1]}" not in size:
        raise SystemExit(f"🔴 화면 크기가 {DEVICE}가 아니다: {size.strip()} → `adb shell wm size reset`")
    # 🔴 카메라는 **부팅 시점에 정해진다.** 이미 떠 있는 에뮬레이터에는 붙일 수 없고,
    #    안 붙은 채로 찍으면 검은 프리뷰가 나온다(오류 문구는 없다).
    if "Number of camera devices: 2" not in adb("shell", "dumpsys", "media.camera"):
        raise SystemExit(
            "🔴 가상 씬 카메라가 없다(카메라 1개) — 껐다가 다시 띄운다:\n"
            "   python3 -c \"import importlib.util as u; s=u.spec_from_file_location("
            "'r','제출물/_tools/record_play_video.py'); m=u.module_from_spec(s); "
            "s.loader.exec_module(m); m.setup_avd_config(); m.boot(m.poster_path())\""
        )

    dump = adb("shell", "dumpsys", "package", PKG)
    flags = [l.strip() for l in dump.splitlines() if l.strip().startswith("flags=[")]
    if not flags:
        raise SystemExit("🔴 앱이 설치돼 있지 않다")
    if "ALLOW_BACKUP" in flags[0]:
        raise SystemExit(
            "🔴 설치된 것이 **낡은 빌드**다(`ALLOW_BACKUP`이 남아 있다 = "
            "`allowBackup=\"false\"` 이전 매니페스트).\n"
            f"   → adb install -r {APK.relative_to(ROOT)}"
        )
    if "DEBUGGABLE" in flags[0]:
        raise SystemExit("🔴 디버그 빌드가 설치돼 있다 — 스토어 스크린샷은 릴리스 빌드로 찍는다")
    print(f"설치된 앱 플래그: {flags[0]}")

    # 🔴 벽에 걸 사진은 **여기서** 확인한다. 뒤에서 죽으면 이미 걸음과 PlantNet 호출을
    #    한 번 쓴 뒤다(실제로 그렇게 죽었다 — 파일명을 지어냈다).
    dataset = ROOT / "android/app/src/androidTest/assets/flower"
    if not (dataset / POSTER).is_file():
        raise SystemExit(f"🔴 데이터셋에 없는 사진: {POSTER}")


def shot(name: str) -> pathlib.Path:
    RAW.mkdir(parents=True, exist_ok=True)
    p = RAW / f"{name}.png"
    with p.open("wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=60)
    im = Image.open(p)
    if im.size != DEVICE:
        raise SystemExit(f"🔴 {name}: 화면이 {im.size}다 — {DEVICE}가 아니면 합성 규격이 깨진다")
    print(f"  찍었다 {name}.png")
    return p


#: 프리뷰가 포스터를 보고 있나 — 판정 경계. `record_play_video.poster_match`의 실측
#: 대조군(나쁨·대조 0.18 이하 / 좋음 0.66 이상)에서 고른 값이다.
#: 🔴 재는 함수를 여기 다시 쓰지 않는다 — 경계도 그쪽 표를 보고 정한다.
POSTER_MATCH_MIN = 0.45


def tab(rec, name: str, wait: float = 6.0) -> None:
    """하단 탭을 누른다 — **누르기 전에 앱이 화면 주인인지 확인한다.**

    🔴 좌표 탭은 앱을 벗어난 것을 모른다. 실측: 발견 기록 화면에서 `keyevent 4`(뒤로)를
       눌렀더니 **앱이 닫히고 런처로 나갔고**, 그 뒤 탭 좌표가 런처의 검색창을 눌러
       `21_map` · `22_ranking`이 **구글 검색 + 키보드**로 찍혔다(크기·색 검사는 통과).
       그래서 뒤로가기를 쓰지 않고(발견 기록 화면에도 탭바가 있다) 여기서 두 겹으로 본다.
    """
    focus = adb("shell", "dumpsys", "window")
    if PKG not in focus.split("mCurrentFocus")[-1][:200]:
        raise SystemExit(
            f"🔴 `{name}`을 누르기 전에 앱을 벗어났다 — 지금 화면 주인:\n"
            f"   {focus.split('mCurrentFocus')[-1][:120].strip()}"
        )
    labels = {l for l, _, _ in rec.ui_dump()}
    if not {"도감", "지도", "랭킹"} & labels:
        raise SystemExit(f"🔴 하단 탭바가 안 보인다 — 좌표로 누르면 엉뚱한 곳이다: {sorted(labels)[:10]}")
    if name in rec.TAP:
        rec.tap(name, wait=wait)
    else:
        # ⚠️ `마이탭`은 영상 도구의 좌표표에 없다(영상 시나리오가 안 들어간다).
        #    좌표를 새로 지어내지 않고 **라벨로 누른다** — 탭바 라벨은 정확히 일치한다.
        rec.tap_text(name.removesuffix("탭"), wait=wait, exact=True)


def aim_shot(rec, poster: pathlib.Path) -> None:
    """겨눔 컷(`10_aim`)을 찍는다. 🔴 **셔터를 누르지 않는다.**

    등록은 `run_cuts`가 이미 한 번 했다. 카메라를 다시 열기만 하면 프리뷰에 벽의 꽃이
    그대로 차 있다(카메라 자세는 앱을 다시 띄워도 남는다 — 실측). 그래서 이 컷은
    **PlantNet 호출 0건**이고, 판별 횟수(비로그인 2회)도 쓰지 않는다.

    ⚠️ 여기서 셔터를 누르면 종이 하나 더 늘어나고, 그러면 도감 컷에 **같은 그림 두 개**가
       나란히 찍힌다(위 `POSTER`의 🔴). 두 번째 종을 등록하지 않는 것이 이 함수의 요점이다.
    """
    rec.tap("촬영탭", wait=6)
    labels = {l for l, _, _ in rec.ui_dump()}
    if not any("찍기" in l or "플래시" in l for l in labels):
        raise SystemExit(f"🔴 카메라 화면이 아니다: {sorted(labels)[:10]}")
    aim = shot("10_aim")
    # 🔴 **프리뷰가 벽·탁자인 것을 여기서 걸러야 한다.** 벽에 걸린 사진과 대조한다
    #    (`record_play_video.poster_match` — 왜 채도로 재지 않는지 그쪽 독스트링).
    match = rec.poster_match(aim, poster)
    print(f"    겨눔 컷 포스터 일치 {match:.2f}")
    if match < POSTER_MATCH_MIN:
        raise SystemExit(
            f"🔴 `10_aim`에 꽃이 없다(일치 {match:.2f} < {POSTER_MATCH_MIN}) — "
            "프리뷰가 벽·탁자다.\n"
            "   카메라가 벽을 안 보고 있다. 걸음(`walk_to_poster`)부터 다시 돌린다."
        )
    # 카메라를 닫는다. 🔴 뒤로가기로 나가면 **앱을 벗어날 수 있다**(실측: 도감 첫 화면에서
    #    뒤로가기 → 런처). 닫기 버튼이 있으면 그것을 누르고, 없으면 뒤로가기로 나간 뒤
    #    아래 `tab()`이 화면 주인을 확인한다.
    if not rec.tap_text("닫기", "취소", wait=4, required=False):
        adb("shell", "input", "keyevent", "4")
        time.sleep(4)


def map_gate() -> bool:
    """지도 화면을 찍어도 되는 상태인가 — **서버에 물어서** 답한다.

    🔴 지도 401은 **화면으로 구분이 안 된다.** `연결이 불안정해요…`는 비행기 모드에서도,
       키해시 미등록에서도 똑같이 뜬다(같은 얼굴의 다른 원인). 그래서 화면을 보고 판단하지
       않고, 설치된 것과 **같은 서명**의 APK로 인증 엔드포인트를 재서 결정한다.
       실측(2026-08-17): 릴리스 서명 `YrZy…zZA=` → 401 `android keyhash mismatched!`,
       같은 명령의 대조군 debug 서명 → 200. 근거는 `프로젝트 맥락/진행.md` (82).
    """
    check = module(ROOT / "android/_tools/check_kakao_map_auth.py", "check_kakao_map_auth")
    ok = check.map_auth_ok(APK)
    if not ok:
        print(
            "\n   🔴 지도 컷을 **건너뛴다** — 릴리스 서명의 키해시가 카카오 콘솔에 없다.\n"
            "      찍으면 오류 화면이 나오고 규격 검사는 통과한다(그렇게 한 번 올라갔다).\n"
            "      등록 뒤 다시 돌리면 이 컷이 자동으로 들어온다:\n"
            "        python3 android/_tools/check_kakao_map_auth.py"
        )
    return ok


def walkthrough() -> None:
    rec = module(ROOT / "제출물/_tools/record_play_video.py", "record_play_video")
    poster = rec.set_wall(rec.poster_path(POSTER))
    print(f"벽에 걸 사진: {poster.name}")

    # 🔴 앱 데이터를 지우고 시작한다. 안 지우면 신규 등록 컷이 아니라 재발견·하루중복이
    #    뜨고(둘 다 정상 동작) **화면이 비슷해서** 엉뚱한 스크린샷이 나온다.
    rec.prepare(reset=True)
    rec.run_cuts(dry_run=False, reset=True)  # 판별 1회 = PlantNet 1건. 이게 유일한 호출이다

    print("\n겨눔 컷 (셔터 없음)")
    aim_shot(rec, poster)

    # 마지막 상태로 탭을 다시 찍는다 — `run_cuts`가 남긴 컷은 `제출물/영상/_check_*.png`이고
    # 그건 영상 확인용이다(이 폴더로 복사하지 않는다 — 아래 🔴).
    print("\n마지막 상태 촬영 (도감 · 발견 기록 · 지도 · 랭킹)")
    tab(rec, "도감탭")
    dex = [l for l, _, _ in rec.ui_dump()]
    print(f"    도감: {' · '.join(dex[:6])}")
    shot("20_dex")

    # 발견 기록 상세 — **내가 찍은 사진**이 나오는 유일한 화면이다(일러스트가 아니다).
    # 없어도 되는 컷이라(`SHOTS`의 세 번째 값) 못 열면 넘어가고 **넘어간 것을 적는다.**
    # ⚠️ 여기서 뒤로가기를 누르지 않는다 — 이 화면에도 탭바가 있다(위 `tab`의 🔴).
    opened = rec.tap_text("해바라기", wait=5, required=False)
    if opened:
        labels = [l for l, _, _ in rec.ui_dump()]
        if any("발견 기록" in l or "꽃 이야기" in l for l in labels):
            shot("23_detail")
            print(f"    발견 기록 열었다: {opened}")
        else:
            print(f"    ⚠️ `{opened}`를 눌렀는데 상세가 아니다({labels[:6]}) — 이 컷은 뺀다")
    else:
        print("    ⚠️ 도감에서 발견한 꽃 칸을 못 찾았다 — 이 컷은 뺀다")

    tab(rec, "마이탭", wait=6)
    my = [l for l, _, _ in rec.ui_dump()]
    if any("모은 꽃" in l or "배지" in l or "칭호" in l for l in my):
        shot("24_my")
        print(f"    마이: {' · '.join(my[:6])}")
    else:
        print(f"    ⚠️ 마이 화면이 아니다({my[:6]}) — 이 컷은 뺀다")

    # 🔴 지도는 **인증이 통과할 때만** 찍는다. 401이면 화면은 오류 문구를 띄우는데
    #    크기·색·mtime 검사는 전부 통과한다(실측: 그 오류 화면이 스토어 4번이 됐다).
    if map_gate():
        tab(rec, "지도탭", wait=9)
        shot("21_map")
    tab(rec, "랭킹탭", wait=8)
    shot("22_ranking")  # 올리지 않는다(위 `SHOTS`의 🔴) — 종수 수정 증거로 남긴다

    focus = adb("shell", "dumpsys", "window")
    if PKG not in focus.split("mCurrentFocus")[-1][:200]:
        raise SystemExit("🔴 앱을 벗어났다 — 찍힌 그림을 버리고 다시 찍는다")

    # 🔴 `run_cuts`의 `_check_*.png`를 여기로 복사하지 않는다. 예전 판이 그렇게 했고,
    #    그래서 **걷는 중에 찍힌 `04_aim`**(탁자와 의자)이 스토어 1번 스크린샷이 됐다.
    #    이 폴더의 원본은 전부 이 함수가 직접 찍은 것이어야 한다.
    stale = [p.name for p in RAW.glob("*.png")
             if p.name[:-4] not in {s[0] for s in SHOTS}]
    if stale:
        print(f"    (안 쓰는 옛 원본 {len(stale)}개는 그대로 둔다: {' '.join(sorted(stale))})")


def gradient(size, green) -> Image.Image:
    graphics = module(ROOT / "출시/_tools/build_play_graphics.py", "build_play_graphics")
    w, h = size
    top = graphics.mix(green, (255, 255, 255), 0.30)
    bottom = graphics.mix(green, (0, 0, 0), 0.18)
    im = Image.new("RGB", size, top)
    d = ImageDraw.Draw(im)
    for y in range(h):
        d.line([(0, y), (w, y)], fill=graphics.mix(top, bottom, y / (h - 1)))
    return im


def rounded(im: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], radius, fill=255)
    im.putalpha(mask)
    return im


def compose() -> int:
    launcher = module(ROOT / "디자이너_업무/아이콘/_tools/build_android_launcher.py", "launcher")
    src = Image.open(launcher.SRC).convert("RGBA")
    green = launcher.badge_green(src)

    if not APK.is_file():
        raise SystemExit(f"🔴 릴리스 APK가 없다: {APK}")
    apk_time = APK.stat().st_mtime

    # 🔴 **지난 실행의 결과를 먼저 지운다.** 컷 수가 줄면 `screenshot_6.png`이 남는데,
    #    그 한 장이 옛 실행의 화면이고(첫 실행에서는 걷는 중에 찍힌 프리뷰였다) 폴더를
    #    통째로 올리면 스토어에 섞인다. 아래 검사는 **만드는 파일만** 본다.
    for old in sorted(OUT.glob("screenshot_*.png")):
        old.unlink()

    made = []
    held = []
    i = 0
    for name, purpose, publish, optional in SHOTS:
        raw = RAW / f"{name}.png"
        if not publish:
            # 🔴 찍혀 있어도 올리지 않는다(`SHOTS`의 🔴). **왜 뺐는지 매번 찍는다** —
            #    말없이 빠지면 다음 사람은 4장이 원래 전부인 줄 안다.
            held.append((name, purpose, "스토어 제외(위 SHOTS 주석)"))
            continue
        if not raw.is_file():
            if optional:
                # 🔴 조용히 빠지면 안 된다 — 4장 미만이면 추천 대상 요건이 깨진다.
                print(f"   ⚠️ {name}.png이 없어서 뺀다 ({purpose}) — 아래 장수를 확인한다")
                held.append((name, purpose, "원본이 없다"))
                continue
            raise SystemExit(f"🔴 원본이 없다: {raw} — `--compose` 없이 한 번 돌려서 찍는다")
        # 🔴 낡은 원본을 조용히 다시 쓰지 않는다(위 독스트링).
        if raw.stat().st_mtime < apk_time:
            if optional:
                # 🔴 이 자리가 실제로 걸렸다(2026-08-17): 지도 인증이 401이라 이번 실행이
                #    `21_map`을 안 찍었는데 **옛 빌드의 오류 화면 원본이 폴더에 남아 있었다.**
                #    지우지 않는다(증거다) — mtime 규칙이 알아서 뺀다.
                held.append((name, purpose, "옛 빌드의 원본이다(이번 실행이 안 찍었다)"))
                continue
            raise SystemExit(
                f"🔴 {raw.name}이 릴리스 APK보다 낡았다 — 다른 빌드의 화면이다. 다시 찍는다."
            )
        i += 1
        canvas = gradient(CANVAS, green)
        im = Image.open(raw).convert("RGB")
        inner = (CANVAS[0] - 144, CANVAS[1] - 112)
        im.thumbnail(inner, Image.LANCZOS)
        im = rounded(im, RADIUS)
        canvas.paste(im, ((CANVAS[0] - im.width) // 2, (CANVAS[1] - im.height) // 2), im)

        out = OUT / f"screenshot_{i}.png"
        canvas.save(out)
        # 🔴 한 색으로 덮인 그림(검은 프리뷰·잠긴 화면)을 통과시키지 않는다.
        colors = len(set(canvas.getdata()))
        if colors < 500:
            raise SystemExit(f"🔴 {out.name}이 사실상 단색이다(색 {colors}종) — 화면이 안 찍혔다")
        size = out.stat().st_size
        if canvas.size != CANVAS or canvas.mode != "RGB":
            raise SystemExit(f"🔴 {out.name} 규격이 틀렸다: {canvas.size} {canvas.mode}")
        if size > 8 * 1024 * 1024:
            raise SystemExit(f"🔴 {out.name}이 8MB를 넘는다({size:,})")
        made.append((out, purpose, im.size, size))

    print()
    for out, purpose, inner, size in made:
        print(f"   🔵 {out.relative_to(ROOT)} {CANVAS[0]}×{CANVAS[1]} "
              f"(화면 {inner[0]}×{inner[1]}) {size:,}바이트 — {purpose}")
    for name, purpose, why in held:
        print(f"   ⏸ {name} — {purpose}: {why}")

    print()
    print(f"스크린샷 {len(made)}장 (Play 최소 2장 · 추천 대상 요건 4장 이상)")
    if len(made) < 2:
        raise SystemExit("🔴 2장 미만이면 스토어 등록 자체가 안 된다")
    if len(made) < 4:
        # 🔴 여기서 멈추지 않는다. 4장은 **추천 대상 요건**이지 등록 요건이 아니고,
        #    숫자를 채우려고 못 올릴 컷(지도 오류·어긋난 그림)을 끼우는 것이 더 나쁘다.
        #    막힌 것을 이름으로 말하고 넘어간다.
        print(
            f"⚠️ **{len(made)}장이다 — 추천·선별 대상 요건(4장)은 아직 못 채웠다.**\n"
            "   못 채운 이유는 위 ⏸ 줄이 전부다. 지도는 카카오 키해시 등록,\n"
            "   랭킹은 일러스트 종별 구분 + 테스트 계정 정리가 끝나면 채워진다."
        )
    print("⚠️ **눈으로 봐야 끝난다.** 대화상자가 걸쳐 있는지, 빈 도감이 찍혔는지는")
    print("   위 검사가 못 잡는다.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--compose", action="store_true", help="찍지 않고 원본만 합성한다")
    a = ap.parse_args()
    if not a.compose:
        preflight()
        walkthrough()
    return compose()


if __name__ == "__main__":
    sys.exit(main())
