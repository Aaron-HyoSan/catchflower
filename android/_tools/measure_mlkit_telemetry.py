#!/usr/bin/env python3
"""**ML Kit이 구글로 무엇을 보내는지** 기기에서 잰다.

왜 이걸 재나 — 처방침이 "분석 도구를 하나도 안 쓴다"고 적었다
─────────────────────────────────────────────────────────
`법무/개인정보_처리방침.txt` 1항 2)는 이렇게 약속한다:

    광고 식별자, 기기 고유번호, 분석·통계 도구, 크래시 수집 도구를
    하나도 사용하지 않습니다.

그런데 **릴리스 매니페스트를 병합해 보면 구글의 원격 측정 전송기가 들어 있다**
(실측 · `app/build/outputs/logs/manifest-merger-release-report.txt`):

    uses-permission#android.permission.ACCESS_NETWORK_STATE
      ADDED from [com.google.mlkit:vision-internal-vkp:18.2.3]
      MERGED from [com.google.android.datatransport:transport-backend-cct:2.3.3]

    <service android:name="com.google.android.datatransport.runtime.
             backends.TransportBackendDiscovery">
      <meta-data android:name="backend:com.google.android.datatransport.cct.
                 CctBackendFactory" .../>
    <service android:name="…jobscheduling.JobInfoSchedulerService"/>

`transport-backend-cct`는 구글의 **Clearcut(CCT) 로그 업로더**다. 우리가 부른 적이
없어도 ML Kit이 자기 사용 통계를 여기에 넣는다. 🔴 **매니페스트에 있는 것만으로는
"보낸다"의 증거가 아니다** — 안 부르면 아무 일도 안 일어난다. 그래서 잰다.

무엇을 증거로 삼나
──────────────────
① **대조군이 먼저다.** ML Kit이 실제로 돌았다는 것을 `CatchFlower` 로그의
   `1차필터:` 줄로 확인한다. 🔴 이게 없으면 "원격 측정 없음"은 **아무 의미가 없다** —
   추론기를 한 번도 안 돌린 채로 초록이 나온다(이 저장소의 "초록 테스트도 증거가
   아니다" 5번: 못 재는 층).
② `dumpsys jobscheduler`에 우리 패키지의 **업로드 job이 등록되는가.**
   CCT는 이벤트를 즉시 안 보내고 job으로 미룬다 — job이 생기면 **보낼 것이 있다는 뜻**이다.
③ logcat에 `TransportRuntime`·`CctTransportBackend` 흔적이 남는가.

⚠️ **유료 호출은 0건이다.** 카메라를 **꽃이 아닌 것**(에뮬레이터 방의 TV 벽)에 대고
   찍으므로 1차 필터가 막고 PlantNet까지 가지 않는다 — 즉 ML Kit만 돌린다.
   `1차필터: 꽃=false`가 그 증거고, 스크립트가 그 줄을 확인한다.

⚠️ **루트 없이 잰다.** 플레이스토어 이미지 에뮬레이터는 `adb root`가 막혀 있어
   `/data/data/…/no_backup/com.google.android.datatransport.events`(CCT 이벤트 DB)를
   직접 볼 수 없다. 그래서 job과 로그로 잰다 — 둘 다 밖에서 보이는 신호다.

    python3 android/_tools/measure_mlkit_telemetry.py
"""

import importlib.util
import pathlib
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
PKG = "com.catchflower.app"

#: CCT(Clearcut) 업로더의 흔적. 로그 태그는 라이브러리가 정한 이름이다.
TELEMETRY_MARKS = (
    "TransportRuntime",
    "CctTransportBackend",
    "datatransport",
    "JobInfoScheduler",
    "clearcut",
    "Clearcut",
)


def recorder():
    """녹화 스크립트의 `ui_dump`·`tap_text`를 **그대로 쓴다**.

    🔴 여기 탭 코드를 다시 쓰지 않는다. 좌표로 본문 버튼을 누르면 앱을 나가서도
       화면은 그럴듯하게 진행돼 보인다(그 함정은 `tap_text` 독스트링에 적혀 있다).
    """
    path = ROOT / "제출물/_tools/record_play_video.py"
    spec = importlib.util.spec_from_file_location("record_play_video", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def adb(*args, check=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=180)
    if check and r.returncode != 0:
        raise SystemExit(f"🔴 실패: adb {' '.join(args)}\n{r.stderr or r.stdout}")
    return (r.stdout or "") + (r.stderr or "")


#: CCT 업로더가 도는 자리. **이 이름이 곧 증거다.**
CCT_SERVICE = (
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
)


def cct_evidence() -> tuple[list[str], list[str]]:
    """`dumpsys jobscheduler`에서 **CCT 업로드 job**을 찾는다.

    두 가지를 따로 본다:
      · 등록(registered) — 지금 예약되어 있다(= 보낼 것이 남아 있다)
      · 이력(job history) — **이미 돌았다.** 끝난 job은 등록 목록에서 사라지므로
        이력을 안 보면 "0건"이 나온다 — 그게 이 검사가 조용히 틀리는 방식이다.

    ⚠️ 패키지 이름으로 줄을 세면 안 된다. `dumpsys`에는 쿼터·타이머 통계에도 패키지가
       나와서(`::timeout-reg:`·`::anr:` 등) 앱을 죽여 놔도 18줄이 잡힌다 —
       처음에 그렇게 셌고 **의미 없는 숫자였다.**
    """
    out = adb("shell", "dumpsys", "jobscheduler")
    if "Registered" not in out:
        raise SystemExit("🔴 `dumpsys jobscheduler`가 예상 밖 출력이다 — 못 잰 것이지 0건이 아니다")
    registered, history = [], []
    for line in out.splitlines():
        s = line.strip()
        if CCT_SERVICE not in s or PKG not in s:
            continue
        if s.startswith(("START:", "STOP:")) or "START: #" in s or "STOP: #" in s:
            history.append(s)
        elif s.startswith("JOB #") or s.startswith("Service:"):
            registered.append(s)
    return registered, history


def telemetry_log_lines() -> list[str]:
    out = adb("logcat", "-d", "-v", "brief")
    return [l for l in out.splitlines() if any(m in l for m in TELEMETRY_MARKS)]


def main() -> int:
    devices = [l for l in adb("devices").splitlines()[1:] if "\tdevice" in l]
    if not devices:
        print("🔴 기기가 없다. 에뮬레이터를 먼저 띄운다:")
        print("   python3 제출물/_tools/record_play_video.py --setup")
        return 2
    print(f"기기: {devices[0].split()[0]}")

    rec = recorder()

    # ── 0. 깨끗한 상태에서 시작 ────────────────────────────────────
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PKG)
    reg0, hist0 = cct_evidence()
    print(f"시작 시점 — 예약된 CCT 업로드 job {len(reg0)}개 · 지나간 이력 {len(hist0)}줄")
    for h in hist0:
        print(f"   (이전) {h}")

    # ── 1. 앱을 띄우고 카메라로 간다 ───────────────────────────────
    #    ⚠️ `monkey`로 띄우면 런처가 먼저 뜬 상태에서 넘어가는 경우가 있다 —
    #       처음에 그렇게 했고 **런처의 구글 검색창을 카메라로 착각했다.**
    adb("shell", "am", "start", "-n", f"{PKG}/{PKG}.MainActivity", check=True)
    time.sleep(8)
    focus = adb("shell", "dumpsys", "window")
    if PKG not in focus.split("mCurrentFocus")[-1][:200]:
        print("🔴 앱이 포그라운드가 아니다 — 탭을 눌러도 다른 앱을 누른다")
        return 1
    rec.tap("촬영탭", wait=4)
    # 화면 03(권한 안내)이 뜨는 상태면 통과시킨다. 없으면 그냥 지나간다.
    rec.tap_text("확인했어요", "시작하기", wait=3, required=False)
    labels = {l for l, _, _ in rec.ui_dump()}
    if not any("찍기" in l or "플래시" in l for l in labels):
        print(f"🔴 카메라 화면이 아니다. 보이는 것: {' · '.join(sorted(labels)[:12])}")
        return 1

    # ── 2. **꽃이 아닌 것**을 찍는다 (PlantNet 0건) ─────────────────
    #    벽으로 걸어가지 않았으므로 프리뷰는 TV(체크무늬)다 — 1차 필터가 막는다.
    print("셔터를 누른다 (겨눈 곳은 꽃이 아니다 → PlantNet 0건)")
    rec.tap("셔터", wait=8)

    # ── 3. 대조군 — ML Kit이 정말 돌았나 ───────────────────────────
    diag = rec.diag_logs()
    first = [l for l in diag if "1차필터" in l]
    for l in first:
        print(f"   {l.split('CatchFlower')[-1].strip()}")
    if not first:
        print("🔴 `1차필터:` 로그가 없다 — ML Kit을 한 번도 안 돌렸다.")
        print("   이 상태의 '원격 측정 없음'은 증거가 아니다(못 잰 것이다).")
        return 1
    paid = [l for l in diag if "판별 호출 직전" in l or "판별 응답" in l]
    print(f"   PlantNet 호출 흔적: {len(paid)}건 (0이어야 한다)")

    # ── 4. 원격 측정 흔적 ──────────────────────────────────────────
    print("원격 측정 업로드가 예약·실행되는지 60초 기다린다…")
    time.sleep(60)
    reg1, hist1 = cct_evidence()
    fresh = [h for h in hist1 if h not in hist0]
    logs = telemetry_log_lines()

    print()
    print(f"① 지금 예약된 CCT 업로드 job: {len(reg1)}개")
    for j in reg1:
        print(f"   {j}")
    print(f"② 이 실행에서 **새로 돌아간** CCT 업로드: {len(fresh)}건")
    for h in fresh:
        print(f"   {h}")
    print(f"③ logcat 원격 측정 흔적: {len(logs)}줄")
    for l in logs[:10]:
        print(f"   {l}")

    sends = bool(reg1) or bool(fresh) or bool(logs)
    print()
    print(f"판정: ML Kit 원격 측정 흔적 {'있다' if sends else '못 찾았다'}")
    if sends:
        print("🔴 처방침 1항 2)의 '분석·통계 도구를 하나도 쓰지 않는다'를 고쳐야 한다.")
        print("   Play 데이터 보안 양식에도 진단 정보를 신고해야 한다(빠뜨리면 정책 위반이다).")
    else:
        print("⚠️ **'안 보낸다'는 결론이 아니다.** 루트가 없어 CCT 이벤트 DB를 못 봤고,")
        print("   업로드는 배터리·네트워크 조건에 따라 몇 시간 뒤에 갈 수도 있다.")
        print("   즉 여기서 얻는 것은 '이 30초 안에 보이는 흔적은 없었다'뿐이다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
