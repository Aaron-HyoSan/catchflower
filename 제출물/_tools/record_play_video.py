#!/usr/bin/env python3
"""플레이 영상을 에뮬레이터에서 녹화한다 — 실물 꽃 없이.

**왜 이게 가능한가.** 에뮬레이터의 `virtualscene` 카메라는 3D 방 안 **벽에 사진을 걸 수
있고**(`adb emu virtualscene-image wall <path>`), 앱은 그것을 **진짜 카메라로 촬영**한다.
그래서 앱 코드를 고치지 않고, QA 우회 스위치도 켜지 않고, 1차 필터 → 개화월 압축 →
PlantNet 실호출이 전부 실제로 돈다. 실측 로그로 확인했다:

    1차필터: 꽃=true top=Flower conf=0.947 1801ms
    판별 호출 직전: PlantNetRecognizer 후보=98개 (8월)
    판별 응답: 받은 후보=1 [Helianthus annuus 0.891] → 통과=1 [109:0.891]

🔴 **AVD `config.ini`를 먼저 고쳐야 한다.** `-camera-back virtualscene` 플래그만으로는
카메라가 **아예 안 붙는다** — config의 `hw.camera.back=emulated`가 이긴다. 증상은
`CameraX: No available camera can be found. Cams:1`이고, 화면에는 **검은 프리뷰 +
비활성 셔터**만 보여서 "카메라가 원래 에뮬에선 안 된다"로 오해하게 된다.
이 스크립트가 `--setup`에서 config를 고치고 원본을 백업한다.

🔴 **포스터는 카메라 뒤쪽 벽에 걸린다.** 부팅 직후 프리뷰에는 TV(체크무늬 텍스처)만
보인다. 에뮬레이터 기본 매크로 `Walk_to_image_room`을 재생해 그 벽까지 걸어가야
사진이 화면을 채운다. **이 걸음 없이 셔터를 누르면 1차 필터가 옳게 막는다.**

⚠️ **유료 호출이 발생한다.** 촬영 1건 = PlantNet 1건이다(무료 500/일). `--dry-run`은
셔터를 누르지 않아 0건이고, 컷 구성만 확인한다.

사용:
    python3 제출물/_tools/record_play_video.py --setup      # AVD config 고치고 부팅
    python3 제출물/_tools/record_play_video.py --dry-run    # 호출 0건, 화면만 확인
    python3 제출물/_tools/record_play_video.py              # 녹화 (PlantNet 1건)
    python3 제출물/_tools/record_play_video.py --restore    # AVD config 원복
"""
import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import time

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SDK = pathlib.Path.home() / "Library/Android/sdk"
EMULATOR = SDK / "emulator/emulator"
MACROS = SDK / "emulator/resources/macros"
AVD_CONFIG = pathlib.Path.home() / ".android/avd/CatchFlower_Pixel8.avd/config.ini"
AVD_BACKUP = AVD_CONFIG.with_suffix(".ini.before_virtualscene")

#: 벽에 걸 꽃 사진. **1차 필터 실측 데이터셋**(사진 1,000장)에서 가져온다 —
#: 커밋되지 않는 재생성 가능 자산이라 `fetch_prefilter_dataset.py`로 먼저 만들어야 한다.
DATASET = ROOT / "android/app/src/androidTest/assets/flower"
#: 8월에 실제로 피는 종만 쓴다. 장미는 개화기 5~10월이라 8월 후보 98종에 들어 있다.
#: 🔴 **봄꽃(튤립·민들레)을 걸면 개화월 하드 필터가 옳게 막는데, 영상에서는
#: "앱이 못 알아본다"로 보인다.** 그래서 계절이 맞는 종만 후보로 둔다.
#: 🔴 **이미 도감에 있는 종을 찍으면 `새로운 꽃을 발견했어요!` 컷이 안 나온다** —
#: 같은 자리·같은 날이면 B-5(하루 중복)가 발동해 `오늘 여기서 만난 꽃이에요`가 뜬다.
#: 앱이 옳게 동작하는 것이지만 영상에는 신규 등록 컷이 필요하다. `--poster`로 바꾼다.
#: **그래서 이 값은 테이크마다 갱신해야 한다** — 도감(화면 04)에서 아직 없는 종을 고른다.
#: 지금 도감에 있는 것: 애기똥풀·고들빼기·해바라기·장미(2026-08-10 · 5/200종).
#: 데이터셋 5종 중 8월 개화는 roses(81·5~10월) · sunflowers(109·7~9월) ·
#: dandelion(**32 서양민들레 3~10월** — 31 민들레는 3~5월이라 걸린다)뿐이고
#: tulips(68·4~5월)·daisy(75·4~5월)는 **개화월 하드 필터가 옳게 막는다.**
#: 🔴 **민들레는 홀씨 사진을 고르면 안 된다.** 데이터셋 dandelion 100장의 절반이 홀씨
#: (하얀 씨공)이고 그중엔 색보정된 것도 있다 — 앱 안내가 "꽃 한 송이를 네모 안에 꽉
#: 채워 주세요"인데 홀씨는 노란 꽃이 아니라서 1차 필터·PlantNet이 다르게 읽는다.
#: 이 파일은 **가운데 40%가 노란 픽셀 99%**로 골랐고 눈으로도 확인했다.
POSTER = "dandelion_1128626197_3f52424215_n.jpg"

PKG = "com.catchflower.app"
OUT_DIR = ROOT / "제출물/영상"
DEVICE_MP4 = "/sdcard/catchflower_play.mp4"

#: 1080x2400에서 실측한 탭 좌표. **`uiautomator dump`로 뽑은 값**이고 스크린샷을 눈으로
#: 재서 찍은 값이 아니다 — Read 도구가 보여주는 좌표는 축소돼 있어서 1.20배가 필요하다.
#: 하단 탭바만 좌표로 누른다(고정 위치). **본문 버튼은 좌표로 누르지 않는다** — 아래 참조.
TAP = {
    "촬영탭": (540, 2270),
    "셔터": (540, 2250),
    "지도탭": (324, 2270),
    "도감탭": (108, 2270),
    "랭킹탭": (756, 2270),
}


def ui_dump():
    """지금 화면의 노드를 (텍스트, 중심좌표) 목록으로 읽는다."""
    adb("shell", "uiautomator", "dump", "/sdcard/_rec_ui.xml", check=False)
    xml = adb("shell", "cat", "/sdcard/_rec_ui.xml", check=False)
    out = []
    for m in re.finditer(
        r'(?:text|content-desc)="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml
    ):
        label = m.group(1).strip()
        if not label:
            continue
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(2, 6))
        out.append((label, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def tap_text(*wanted, wait=4.0, required=True, exact=False):
    """**화면에 그 글자가 있을 때만** 누른다.

    🔴 **좌표로 본문 버튼을 누르면 안 된다.** 첫 녹화가 그렇게 망했다 — 컷 6에서
    B-5(하루 중복)가 발동해 `오늘 여기서 만난 꽃이에요`가 떴는데, 스크립트는 예상
    좌표를 계속 두드려 **앱을 나가 구글 검색 화면을 녹화했다.** 스크린샷 두 장의
    바이트 수가 똑같았던 것이 유일한 단서였다 — **화면은 그럴듯하게 진행돼 보인다.**

    🔴 **부분 일치가 제목을 누른다.** 화면 13에는 제목 `지도에 공유하기`와 버튼
    `공유하기`가 같이 있고, `ui_dump()`는 위→아래 순서라 **제목이 먼저 걸린다.**
    누르면 아무 일도 안 일어나고 다음 단계만 조용히 틀어진다 — 그런 버튼은 `exact=True`.

    Returns: 실제로 누른 라벨. 없으면 None(required면 예외).
    """
    nodes = ui_dump()
    for w in wanted:
        for label, x, y in nodes:
            if (label == w) if exact else (w in label):
                adb("shell", "input", "tap", str(x), str(y))
                time.sleep(wait)
                return label
    seen = " · ".join(sorted({l for l, _, _ in nodes})[:12])
    if required:
        raise SystemExit(f"화면에 {wanted} 가 없다.\n  지금 보이는 것: {seen}")
    return None


def wait_for_text(*wanted, timeout=25.0, every=1.5):
    """그 글자가 나타날 때까지 **여러 번** 본다. 없으면 None.

    ⚠️ 반환형 주석을 `str | None`으로 쓰면 안 된다 — 이 도구를 돌리는 파이썬은
    **`/usr/bin/python3`(3.9)** 이고(PIL이 그쪽에만 있다) 3.9는 그 문법을 모른다.
    이 파일에는 `from __future__ import annotations`도 없다.

    🔴 `tap_text`는 화면을 **한 번만** 본다(탭하고 `wait`초 자고 한 번 덤프한다).
    그래서 **네트워크를 기다리는 자리에서는 원리상 깜빡인다** — 2026-09-11에 실제로
    걸렸다: 활동지역 화면에서 카카오 역지오코딩이 6초를 넘겨 `…동으로 시작하기`가
    아직 없었고, 스크립트는 **거기서 죽었다**(그 뒤 컷을 한 장도 못 찍었다).
    손으로 같은 걸음을 밟으니 8초쯤에 `삼성2동으로 시작하기`가 떴다 — 즉 앱은
    정상이고 **재는 쪽이 성급했다.**

    ⚠️ `wait`를 키우는 것으로 고치지 않는다. 그건 느린 날에 또 깜빡이고, 빠른 날에는
    매번 그만큼 더 기다린다. 「생겼나」를 **반복해서 묻는 것**이 고침이다.
    """
    끝 = time.time() + timeout
    while True:
        for label, _, _ in ui_dump():
            for w in wanted:
                if w in label:
                    return label
        if time.time() >= 끝:
            return None
        time.sleep(every)


def sh(*args, check=True, capture=True):
    """adb/emulator를 부른다. 실패를 조용히 넘기지 않는다."""
    r = subprocess.run(args, capture_output=capture, text=True, timeout=180)
    if check and r.returncode != 0:
        raise SystemExit(f"실패({r.returncode}): {' '.join(args)}\n{r.stderr or r.stdout}")
    return (r.stdout or "").strip()


def adb(*args, **kw):
    return sh("adb", *args, **kw)


def setup_avd_config():
    """AVD config의 카메라를 virtualscene으로 바꾼다. 원본은 백업한다.

    🔴 **이걸 안 하면 카메라가 안 붙는다.** 커맨드라인 `-camera-back virtualscene`은
    config에 지고, CameraX는 `Cams:1`로 죽는다 — **화면에 오류 문구가 없다.**
    """
    if not AVD_CONFIG.exists():
        raise SystemExit(f"AVD config가 없다: {AVD_CONFIG}")
    if not AVD_BACKUP.exists():
        shutil.copy2(AVD_CONFIG, AVD_BACKUP)
        print(f"  원본 백업 → {AVD_BACKUP.name}")
    t = AVD_CONFIG.read_text()
    t = t.replace("hw.camera.back=emulated", "hw.camera.back=virtualscene")
    # 앞카메라도 켠다. CameraX `validateCameras()`가 **앞/뒤 둘 다** 있는지 보고,
    # 하나뿐이면 재시도를 반복하다 포기한다(`might underreport the amount of cameras`).
    t = t.replace("hw.camera.front=none", "hw.camera.front=emulated")
    AVD_CONFIG.write_text(t)
    cams = [l for l in t.splitlines() if l.startswith("hw.camera")]
    print("  " + " · ".join(cams))


def restore_avd_config():
    if not AVD_BACKUP.exists():
        raise SystemExit(f"백업이 없다: {AVD_BACKUP}")
    shutil.copy2(AVD_BACKUP, AVD_CONFIG)
    print(f"원복했다 ← {AVD_BACKUP.name}")


def poster_path(name=POSTER):
    p = DATASET / name
    if not p.exists():
        raise SystemExit(
            f"꽃 사진이 없다: {p}\n"
            "  → python3 android/_tools/fetch_prefilter_dataset.py 로 먼저 만든다"
        )
    return p


def boot(poster):
    """에뮬레이터를 가상 씬 카메라로 띄운다."""
    running = adb("devices").splitlines()[1:]
    if any("emulator" in l and "device" in l for l in running):
        print("이미 떠 있다 — 끄고 다시 띄운다(카메라 설정이 부팅 시점에 정해진다)")
        subprocess.run(["adb", "emu", "kill"], capture_output=True, text=True)
        time.sleep(6)
    print("부팅 중 (약 45초)")
    log = OUT_DIR / "_emulator.log"
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with log.open("w") as f:
        subprocess.Popen(
            [
                str(EMULATOR), "-avd", "CatchFlower_Pixel8",
                "-no-window", "-no-audio", "-no-snapshot-load",
                "-camera-back", "virtualscene",
                "-virtualscene-poster", f"wall={poster}",
            ],
            stdout=f, stderr=subprocess.STDOUT,
        )
    adb("wait-for-device")
    for _ in range(60):
        if adb("shell", "getprop", "sys.boot_completed", check=False) == "1":
            break
        time.sleep(2)
    else:
        raise SystemExit("부팅이 안 끝났다")
    time.sleep(8)  # SystemUI가 자리 잡기를 기다린다. 바로 만지면 ANR 대화상자가 뜬다
    cams = adb("shell", "dumpsys", "media.camera", check=False)
    n = [l for l in cams.splitlines() if "Number of camera devices" in l]
    print(f"  {n[0].strip() if n else '카메라 수를 못 읽었다'}")
    if n and "2" not in n[0]:
        raise SystemExit(
            "카메라가 2개가 아니다 — --setup 없이 띄웠을 것이다.\n"
            "  증상: CameraX 'No available camera. Cams:1' · 화면은 검은 프리뷰"
        )


def prepare(reset=False):
    """권한·위치를 넣고 앱을 띄운다. 녹화에 방해되는 것을 끈다.

    `reset`이면 앱 데이터를 지운다. 🔴 **이게 없으면 테이크를 반복할 수 없다** —
    한 번 등록한 종은 다시 찍어도 `새로운 꽃을 발견했어요!`가 아니라
    `서양민들레를 다시 발견했어요!`(재발견)나 B-5 하루중복이 뜬다. **셋 다 정상
    동작이고 화면이 비슷해서** 좌표로 진행하면 조용히 엉뚱한 컷을 녹화한다.
    데이터셋 5종 중 8월 개화는 3종뿐이라 세 테이크면 소진된다 — 지우는 게 정답이다.
    지우면 온보딩(화면 03·02)부터 시작해서 **제출 영상으로도 이쪽이 낫다**
    (`0 / 2044종` 빈 도감 → `아직 모은 꽃이 없어요` 안내까지 보인다.
    🔴 이 숫자는 `GamePolicy.DEX_SLOT_COUNT`다 — 여기 `200`이라고 적혀 있었고 그건
    확장 전 범위(`꽃목록_200종.csv`)를 베낀 것이었다).
    """
    if reset:
        adb("shell", "pm", "clear", PKG, check=False)
        time.sleep(2)
    for p in ("CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"):
        adb("shell", "pm", "grant", PKG, f"android.permission.{p}", check=False)
    # 위치가 없으면 화면 13이 **빨간 글씨로 '위치 권한이 필요해요'**를 띄운다 —
    # 앱이 옳게 알려주는 것이지만 영상에서는 결함으로 보인다.
    sh("adb", "emu", "geo", "fix", "127.0495556", "37.514575", check=False)
    # 방해 금지: 알림 배너가 컷을 덮는다
    adb("shell", "settings", "put", "global", "heads_up_notifications_enabled", "0", check=False)
    adb("shell", "cmd", "notification", "set_dnd", "priority", check=False)
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PKG, check=False)


def hue_histogram(source, bins=12, width=96):
    """가운데 40%의 **색조 분포**를 읽는다(채도·명도가 낮은 픽셀은 뺀다).

    `source`는 파일 경로거나 이미 열린 PIL 이미지다.

    ⚠️ `width`로 줄여서 센다. 원본 크기(414k 픽셀)로 파이썬 루프를 돌면 한 번에 4초가
       걸리고, **그 시간이 녹화에 그대로 들어간다**(도착 판정에 21초를 썼다 — 걸음은
       8초다). 줄여도 판정이 바뀌지 않는지는 라벨 붙인 대조군으로 확인했다(아래).
    """
    im = (source if isinstance(source, Image.Image) else Image.open(source)).convert("RGB")
    w, h = im.size
    box = im.crop((int(w * 0.3), int(h * 0.3), int(w * 0.7), int(h * 0.7)))
    if box.width > width:
        box = box.resize((width, max(1, box.height * width // box.width)), Image.BILINEAR)
    hist = [0] * bins
    n = 0
    for hue, s, v in box.convert("HSV").getdata():
        if s > 60 and v > 60:
            hist[hue * bins // 256] += 1
            n += 1
    return [x / n for x in hist] if n else hist


def poster_match(shot_path, poster):
    """프리뷰가 **벽에 걸린 그 사진**을 보고 있나 (0~1, 색조 분포 교집합).

    🔴 왜 이렇게 재나 — 처음에는 "가운데가 진한 색인가"(채도)로 쟀는데, **탁자와 의자
       프레임이 40%로 통과했다.** 가상 씬의 벽·바닥·가구가 전부 채도 높은 나무색이다.
       색조 히스토그램도 그냥 노랑을 찾으면 안 된다 — 나무색(주황)과 해바라기(노랑)가
       8비트 색조에서 겹쳐 교집합 0.41까지 나온다. 그래서 **포스터 자신과 대조한다.**
       포스터를 바꿔도 고칠 데가 없다.

    실측 대조군 — **라벨을 먼저 붙이고 쟀다**(걸으면서 4초마다 찍은 프레임 + 포스터):
        나쁨  탁자와 의자(걷는 중) vs 해바라기 벽    0.13
        좋음  해바라기 도착                          0.82
        좋음  민들레 도착                            0.66
        대조  해바라기 도착 vs **엉뚱한 포스터**(장미) 0.18
        대조  탁자 vs 민들레                          0.07
      → 경계 0.45 (나쁨·대조는 0.18 이하 · 좋음은 0.66 이상)

    ⚠️ 여기까지 오는 데 두 번 틀렸다. ① 처음엔 "가운데가 진한 색인가"로 쟀고 탁자가
       40%로 통과했다. ② 색조를 **이웃 칸까지 허용**해 보니 나쁨 1.46 · 좋음 1.34로
       **뒤집혔다.** 칸을 12개로 줄이고 이웃 허용을 뺀 것이 위 표다.
    """
    return sum(min(a, b) for a, b in zip(hue_histogram(poster), hue_histogram(shot_path)))


#: 지금 벽에 걸려 있는 사진. `set_wall()`이 채운다. `walk_to_poster()`가 도착 판정에 쓴다.
WALL = None


def set_wall(poster):
    """벽에 사진을 걸고 **무엇을 걸었는지 기억한다**(도착 판정에 필요하다)."""
    global WALL
    sh("adb", "emu", "virtualscene-image", "wall", str(poster), check=False)
    WALL = pathlib.Path(poster)
    return WALL


def walk_to_poster(timeout=32.0, need=0.45):
    """포스터가 걸린 벽까지 카메라를 옮기고 **도착했는지 확인한다.**

    🔴 **이 걸음이 없으면 프리뷰에 TV(체크무늬)만 보이고**, 셔터를 누르면 1차 필터가
    `Pattern`·`Textile`로 **옳게** 막는다 — 앱 결함이 아니라 겨눈 곳이 꽃이 아닌 것이다.

    🔴 **`sleep(4)`이었고 그건 짧았다.** `adb emu automation play`는 **바로 돌아오고**
       매크로는 8초 넘게 걸어간다. 그래서 컷 4(`04_aim`)에 **탁자와 의자**가 찍혔고
       (꽃이 없다) 셔터도 걸음이 끝나기 직전에 눌렸다 — 그때 1차 필터가 막지 않은 것은
       운이었다. 검사는 전부 초록이었고 **눈으로 봐서 알았다.**
       ⚠️ 그렇다고 길게 자면 안 된다 — 첫 완주 테이크가 148초 중 50초를 정지된
       프리뷰로 쓰고 **뒤쪽 지도·도감·랭킹 컷을 잘랐다.** 그래서 **도착하면 곧 나온다.**

    ⚠️ 이미 벽 앞에 있어도 매크로는 **처음 자리로 돌아가서 다시 걷는다**(실측: 재생
       직후 4초 프레임이 탁자였다). 그래서 "이미 도착했으니 건너뛴다"를 넣지 않는다.
    """
    macro = MACROS / "Walk_to_image_room"
    if not macro.exists():
        raise SystemExit(f"매크로가 없다: {macro}")
    sh("adb", "emu", "automation", "play", str(macro))
    if WALL is None:
        # 무엇이 걸렸는지 모르면 도착을 잴 수 없다. 조용히 넘기지 않는다 —
        # 여기서 넘기면 다시 탁자를 찍는다.
        raise SystemExit("벽에 걸린 사진을 모른다 — `set_wall()`로 걸어야 한다")
    started = time.monotonic()
    best = 0.0
    while time.monotonic() - started < timeout:
        time.sleep(2)
        score = poster_match(screenshot("_walk"), WALL)
        best = max(best, score)
        if score >= need:
            print(f"    도착 ({time.monotonic() - started:.0f}초 · 일치 {score:.2f})")
            time.sleep(1)  # 프리뷰 노출·초점이 자리 잡기를 기다린다
            return score
    raise SystemExit(
        f"걸어갔는데 포스터가 안 보인다 (최고 일치 {best:.2f} < {need} · {timeout:.0f}초).\n"
        f"  지금 프리뷰: 제출물/영상/_check__walk.png 를 **눈으로 본다**\n"
        "  · 카메라가 다른 곳을 보고 있거나 벽 사진이 안 바뀌었다"
    )


def tap(name, wait=2.0):
    x, y = TAP[name]
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def screenshot(name):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    p = OUT_DIR / f"_check_{name}.png"
    with p.open("wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=60)
    return p


def diag_logs():
    """진단 로그 4줄을 읽는다. **화면에 증상이 없는 것을 여기서만 구분할 수 있다.**"""
    out = adb("shell", "logcat", "-d", "-s", "CatchFlower", check=False)
    return [l for l in out.splitlines() if "CatchFlower" in l]


def run_cuts(dry_run, reset=False, deadline=None):
    """컷을 순서대로 태운다. 각 단계마다 확인용 스크린샷을 남긴다.

    `deadline`은 녹화가 스스로 끝나는 시각(monotonic). 🔴 **넘겼는지 여기서 재야
    한다** — `screenrecord --time-limit`은 컷이 남았어도 그 시각에 끝내고, 스크립트는
    남은 탭을 계속 눌러 "완주했다"를 찍는다. **로그는 전부 통과인데 mp4에는 마지막
    컷이 없다.** 실측: 컷 전체 176초 > 상한 175초 → 랭킹 컷이 9.8초 잘렸다.
    """
    print("\n[컷 1] 앱 실행")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", check=False)
    time.sleep(6)
    screenshot("01_home")

    if reset:
        # 화면 03 활동지역 → 화면 02 권한안내. **동네는 카카오 REST 역지오코딩으로
        # 실제로 찾는다**(`adb emu geo fix`로 넣은 좌표 → `서울특별시 강남구 삼성2동`).
        print("[컷 1-1] 활동 지역 — 현재 위치로 동네 찾기")
        tap_text("현재 위치로 우리 동네 찾기", wait=6)
        screenshot("01a_region")
        # 검색 결과 항목은 **누를 필요가 있을 때만** 누른다 — 역지오코딩이 한 곳만
        # 주면 이미 선택된 상태로 `…동으로 시작하기`가 활성화된다. `required=False`가
        # 아니면 여기서 멈춘다(실제로 한 번 멈췄다).
        dong = tap_text("서울특별시", wait=3, required=False)
        print(f"    찾은 동네: {dong or '(이미 선택됨)'}")
        # 🔴 여기는 **네트워크를 기다리는 자리**다(카카오 역지오코딩). 한 번만 보면
        #    느린 날에 죽는다 → 생길 때까지 묻는다. 못 찾으면 tap_text가 지금 보이는
        #    것을 찍고 멈추므로, 여기서 조용히 넘기지는 않는다.
        생겼나 = wait_for_text("으로 시작하기", timeout=30)
        if 생겼나:
            print(f"    동네 버튼: {생겼나}")
        tap_text("으로 시작하기", wait=6)
        print("[컷 1-2] 권한 안내")
        screenshot("01b_permission")
        tap_text("허용하고 시작하기", wait=7)

    print("[컷 2] 도감 홈 — 발견한 칸만 색")
    time.sleep(2)
    screenshot("02_dex")

    print("[컷 3] 촬영 화면 열기")
    tap("촬영탭", wait=6)
    screenshot("03_camera")

    print("[컷 4] 꽃을 겨눈다 (포스터 벽으로 이동)")
    walk_to_poster()
    screenshot("04_aim")

    if dry_run:
        print("\n--dry-run: 셔터를 누르지 않는다 (PlantNet 호출 0건)")
        return

    print("[컷 5] 셔터 → AI 분석 (PlantNet 실호출 1건)")
    tap("셔터", wait=14)
    screenshot("05_identify")
    for l in diag_logs():
        print("    " + l.split("CatchFlower: ", 1)[-1])

    print("[컷 6] 이 꽃 맞아요 → 등록")
    # 🔴 **화면 09는 두 모양이고, 실제로 나오는 쪽은 후보 목록이다.** 1순위 점수가
    #    `confidenceThreshold`(0.60) 미만이면 `어느 꽃인가요? / 가장 비슷한 꽃을 골라
    #    주세요` + 후보마다 `이 꽃이에요`가 뜬다. PlantNet 점수는 4,932종에 퍼진
    #    확률이라 정답도 0.1 근처가 흔하다 — 실측 장미는 **0.104**였다.
    #    `네, 맞아요`만 찾으면 여기서 멈춘다(첫 수정판이 그랬다).
    #    `이 꽃이에요`는 후보마다 있고 `ui_dump()`는 화면 위→아래 순서라 첫 매치가 1순위다.
    hit = tap_text("네, 맞아요", "이 꽃이에요", "맞아요", wait=8)
    print(f"    눌렀다: {hit}")
    screenshot("06_register")
    # 🔴 **여기서 무엇이 떴는지 확인해야 한다.** 신규 등록(`새로운 꽃을 발견했어요!`)과
    #    B-5 하루중복(`오늘 여기서 만난 꽃이에요`)은 **둘 다 정상 동작**이고 화면 구조가
    #    비슷하다. 구분하지 않으면 이후 탭이 전부 엉뚱한 곳을 누른다.
    nodes = [l for l, _, _ in ui_dump()]
    if any("새로운 꽃" in n for n in nodes):
        print("    신규 등록 ✅")
        print("[컷 7] 지도에 공유")
        tap_text("지도에 공유하기", wait=5)
        screenshot("07_share")
        # 🔴 **여기서 `공유하지 않기`를 누르면 안 된다.** 화면은 똑같이 지도로 돌아가고
        #    영상도 그럴듯한데, **핀이 안 찍힌다** — 컷 8의 지도가 비어서 "지도 기능이
        #    안 되는 앱"으로 보인다. 제출해야 하는 것은 공개 범위를 고른 뒤의 `공유하기`다.
        tap_text("공유하기", wait=8, exact=True)
        screenshot("08_shared")
    elif any("오늘 여기서" in n for n in nodes):
        print("    ⚠️ B-5 하루중복이 발동했다 — 이미 같은 자리에서 잡은 종이다")
        print("       (앱은 옳게 동작한다. 신규 등록 컷이 필요하면 --poster로 다른 종을 쓴다)")
        tap_text("도감에서 보기", wait=6)
        screenshot("07_share")
    else:
        raise SystemExit(f"예상 못한 화면이다: {nodes[:10]}")

    print("[컷 9] 지도 · 도감 · 랭킹")
    tap("지도탭", wait=8)
    screenshot("09_map")
    tap("도감탭", wait=5)
    screenshot("10_dex_after")
    tap("랭킹탭", wait=6)
    screenshot("11_ranking")

    # 앱 안에 있는지 마지막으로 확인한다. **첫 녹화는 여기서 구글 검색 화면이었다.**
    focus = adb("shell", "dumpsys", "window", check=False)
    if PKG not in focus.split("mCurrentFocus")[-1][:200]:
        raise SystemExit("앱을 벗어났다 — 녹화를 버리고 다시 찍는다")

    # 🔴 마지막 컷이 녹화 안에 들어갔는지 잰다. **여기까지 왔다는 것이 영상에 담겼다는
    #    뜻이 아니다** — 넘겼으면 mp4는 앞부분만 있고 로그는 전부 초록이다.
    if deadline is not None:
        over = time.monotonic() - deadline
        if over > -3:
            raise SystemExit(
                f"컷이 녹화 상한을 {over:+.0f}초로 넘겼다 — **마지막 컷이 잘렸다.**\n"
                f"  → --seconds 를 {int(over) + 12}초 늘리거나 대기를 줄인다"
            )
        print(f"    (녹화 상한까지 {-over:.0f}초 남았다)")


def record(seconds, dry_run, reset=False):
    """화면 녹화를 켠 상태로 컷을 태운다.

    ⚠️ `screenrecord`는 **최대 3분**이고 오디오가 없다. Ctrl+C로 끊으면 mp4가 깨질 수
    있어서 `--time-limit`으로 스스로 끝나게 둔다.
    """
    if dry_run:
        run_cuts(dry_run=True, reset=reset)
        return None
    adb("shell", "rm", "-f", DEVICE_MP4, check=False)
    print(f"\n녹화 시작 (최대 {seconds}초)")
    rec = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--time-limit", str(seconds),
         "--size", "1080x2400", "--bit-rate", "8000000", DEVICE_MP4],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    time.sleep(2)
    deadline = time.monotonic() + seconds - 2  # 위에서 2초 기다린 만큼 뺀다
    try:
        run_cuts(dry_run=False, reset=reset, deadline=deadline)
    finally:
        print("\n녹화 종료를 기다린다")
        try:
            rec.wait(timeout=seconds + 30)
        except subprocess.TimeoutExpired:
            rec.terminate()
        time.sleep(3)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out = OUT_DIR / "catchflower_play.mp4"
    adb("pull", DEVICE_MP4, str(out))
    adb("shell", "rm", "-f", DEVICE_MP4, check=False)
    # 🔴 **`pull` 성공을 증거로 쓰지 않는다.** screenrecord는 실패해도 파일을 남기고
    # 성공처럼 끝날 수 있다. 크기 → 컨테이너 마무리 → 재생시간, 세 가지를 본다.
    size = out.stat().st_size if out.exists() else 0
    if size < 100_000:
        raise SystemExit(f"녹화가 사실상 비었다({size} bytes) — 화면이 꺼져 있었을 수 있다")
    dur = mp4_duration(out)
    print(f"\n✅ {out}\n   {size:,} bytes · {dur:.1f}초")
    return out


def mp4_duration(path):
    """mp4의 재생시간을 읽는다. **크기로는 잘린 것을 알 수 없다.**

    🔴 이 맥에는 ffprobe가 없어서 박스를 직접 걷는다. `moov`가 없으면 컨테이너가
    마무리되지 않은 것이다 — 크기가 4MB여도 재생이 안 된다.
    `mdat` 크기는 64비트 확장(size==1)으로 적히니 그 경우를 처리해야 순회가 멈추지 않는다.
    """
    d = path.read_bytes()
    if d.find(b"moov") < 0:
        raise SystemExit("mp4에 moov가 없다 — 녹화가 마무리되지 않았다(강제 종료됐을 것이다)")
    off = 0
    while off + 8 <= len(d):
        size = int.from_bytes(d[off:off + 4], "big")
        typ = d[off + 4:off + 8]
        hdr = 8
        if size == 1:
            size = int.from_bytes(d[off + 8:off + 16], "big")
            hdr = 16
        elif size == 0:
            size = len(d) - off
        if size < hdr:
            break
        if typ == b"moov":
            i = d.find(b"mvhd", off, off + size)
            if i < 0:
                raise SystemExit("moov에 mvhd가 없다")
            b = i + 4
            ts = int.from_bytes(d[b + 12:b + 16], "big")
            du = int.from_bytes(d[b + 16:b + 20], "big")
            return du / ts if ts else 0.0
        off += size
    raise SystemExit("mp4 구조를 읽을 수 없다")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--setup", action="store_true", help="AVD config를 고치고 부팅한다")
    ap.add_argument("--restore", action="store_true", help="AVD config를 원복한다")
    ap.add_argument("--dry-run", action="store_true", help="셔터를 안 누른다 (호출 0건)")
    ap.add_argument("--seconds", type=int, default=175,
                    help="녹화 상한 (기본 175 · screenrecord 상한은 180초)")
    ap.add_argument("--poster", default=POSTER, help="벽에 걸 사진 파일명 (flower/ 안)")
    # 🔴 기본이 **초기화**다. 안 지우면 두 번째 테이크부터 신규 등록 컷이 안 나온다.
    ap.add_argument("--no-reset", action="store_true",
                    help="앱 데이터를 지우지 않는다 (재발견/하루중복 컷을 찍을 때만)")
    a = ap.parse_args()

    if a.restore:
        restore_avd_config()
        return

    poster = poster_path(a.poster)
    print(f"벽에 걸 사진: {poster.name}")

    if a.setup:
        print("AVD config 수정")
        setup_avd_config()
        boot(poster)
        set_wall(poster)  # 부팅 플래그로도 걸리지만 **무엇이 걸렸는지 기억**해야 한다
    else:
        # 이미 떠 있는 에뮬레이터를 쓴다. 카메라가 붙어 있는지 먼저 본다 —
        # **여기서 확인하지 않으면 검은 프리뷰를 녹화해 놓고 알게 된다.**
        cams = adb("shell", "dumpsys", "media.camera", check=False)
        if "Number of camera devices: 2" not in cams:
            raise SystemExit("카메라가 2개가 아니다 — 먼저 --setup 으로 띄운다")
        set_wall(poster)

    prepare(reset=not a.no_reset)
    record(a.seconds, a.dry_run, reset=not a.no_reset)
    print("\n확인용 스크린샷: 제출물/영상/_check_*.png")


if __name__ == "__main__":
    sys.exit(main())
