#!/usr/bin/env python3
"""**올릴 파일 자체를** 뜯어서 검사한다 — 소스가 아니라 산출물을 본다.

    python3 출시/_tools/check_release_artifact.py

왜 소스 검사로는 부족한가
─────────────────────────
🔴 **`-PcfAllowLegalPlaceholders=true`로 만든 빌드는 겉이 똑같다.** 스토어 스크린샷을
   찍으려면 그 escape hatch가 필요한데(오너 값이 아직 없다), 그렇게 만든 APK/AAB는
   `{{시행일}}`이 적힌 개인정보 처리방침을 **품고 있다.** 파일 이름·크기·서명은 정상
   빌드와 구분이 안 되고, `./gradlew bundleRelease`가 성공했다는 사실도 증거가 아니다
   (플래그를 준 것은 나다). 그래서 **패키지 안의 asset을 직접 읽는다.**

🔴 **매니페스트도 그렇다.** `AndroidManifest.xml`을 고쳐도 R8/머지 결과가 다를 수 있고
   (`android:debuggable`은 빌드 타입이 넣는다), 소스에 `usesCleartextTraffic="false"`가
   있다는 것과 **패키지된 매니페스트가 그렇다**는 것은 다른 문장이다.

⚠️ 매니페스트는 **APK에서** 읽는다. AAB의 매니페스트는 protobuf라 `aapt2`가 못 읽는다.
   둘은 같은 빌드의 산출물이므로(같은 태스크가 만든다) APK를 대표로 본다 — 대신
   **두 파일의 mtime이 5분 이상 벌어지면 세운다**(다른 빌드를 섞어 보는 것을 막는다).

⚠️ 이 검사가 전부 초록이어도 **업로드 가능**이라는 뜻은 아니다. 여기서 못 재는 것:
   Play Console의 정책 설문·데이터 보안 양식·심사. 그건 `출시준비_AOS.md` 체크리스트다.
"""

from __future__ import annotations

import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
APK = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
AAB = ROOT / "android/app/build/outputs/bundle/release/app-release.aab"
LOCAL_PROPS = ROOT / "android/local.properties"

#: Play가 거부하는 다운로드 크기(압축 기준). AAB는 기기별 분할 뒤 값이라 여기서는
#: **on-disk 크기로 하한만** 본다 — 정확한 값은 bundletool `get-size total`이다.
PLAY_DOWNLOAD_LIMIT = 200 * 1024 * 1024

FAILS: list[str] = []
WARNS: list[str] = []


def fail(msg: str) -> None:
    FAILS.append(msg)
    print(f"   🔴 {msg}")


def warn(msg: str) -> None:
    WARNS.append(msg)
    print(f"   ⚠️ {msg}")


def ok(msg: str) -> None:
    print(f"   🔵 {msg}")


def aapt2() -> pathlib.Path:
    c = sorted((pathlib.Path.home() / "Library/Android/sdk/build-tools").glob("*/aapt2"))
    if not c:
        sys.exit("🔴 aapt2를 못 찾았다 (Android SDK build-tools)")
    return c[-1]


def dump_manifest() -> str:
    r = subprocess.run(
        [str(aapt2()), "dump", "xmltree", "--file", "AndroidManifest.xml", str(APK)],
        capture_output=True, text=True, timeout=180,
    )
    if r.returncode != 0:
        sys.exit(f"🔴 매니페스트를 못 읽었다:\n{r.stderr[:400]}")
    return r.stdout


#: aapt2는 속성 이름을 **네임스페이스까지 풀어서** 찍는다.
#: `A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false`
#: 🔴 여기를 `android:allowBackup`으로 읽던 판이 **없는 값 = None**을 돌려줬고,
#:    검사는 그 None을 그대로 화면에 찍었다(`allowBackup=None` · `권한 0개` ·
#:    `versionCode=None`을 🔵와 함께). 실제 매니페스트에는 셋 다 있었다 —
#:    **계측기가 산출물보다 먼저 틀렸다.** 그래서 아래 [must]는 못 읽으면 세운다.
NS = "http://schemas.android.com/apk/res/android"


def attr(tree: str, name: str) -> str | None:
    """`A: <ns>:이름(0x…)=값` 에서 값을 뽑는다. 없으면 None(**정말 없는 것**)."""
    m = re.search(rf'A: (?:{re.escape(NS)}:)?{re.escape(name)}(?:\([^)]*\))?=(?:"([^"]*)"|([^\s(]+))', tree)
    if not m:
        return None
    return (m.group(1) if m.group(1) is not None else m.group(2)).strip()


def must(tree: str, name: str) -> str:
    """**있는 것이 확실한 값**을 읽는다 — 못 읽으면 계측기가 틀린 것이므로 멈춘다."""
    v = attr(tree, name)
    if v is None:
        sys.exit(
            f"🔴 매니페스트에서 `{name}`을 못 읽었다. 이건 앱 문제가 아니라 **이 스크립트의**\n"
            f"   파싱 문제다(aapt2 출력 형식이 바뀌었을 수 있다). 값을 모른 채 초록을 찍지 않는다."
        )
    return v


def check_manifest() -> None:
    print("\n매니페스트 (패키지된 값)")
    tree = dump_manifest()

    print(f"   package={must(tree, 'package')}")

    # 🔴 **대조군으로 파서를 먼저 잰다.** `debuggable 없음`은 파서가 고장나도 초록이다
    #    (정규식이 아무것도 못 찾으면 "없다"와 구분이 안 된다). debug APK가 있으면
    #    거기서는 **반드시 찾아야** 한다 — 못 찾으면 이 검사 자체를 믿을 수 없다.
    debug_apk = ROOT / "android/app/build/outputs/apk/debug/app-debug.apk"
    if debug_apk.is_file():
        control = subprocess.run(
            [str(aapt2()), "dump", "xmltree", "--file", "AndroidManifest.xml", str(debug_apk)],
            capture_output=True, text=True, timeout=180,
        ).stdout
        if attr(control, "debuggable") != "true":
            sys.exit(
                "🔴 대조군 실패: debug APK에서도 `debuggable=true`를 못 찾았다.\n"
                "   → 아래 `debuggable 없음`은 **못 재는 초록**이다. 파서를 고치고 다시 돈다."
            )
        ok("파서 대조군 통과 (debug APK에서 debuggable=true를 읽었다)")
    else:
        warn("debug APK가 없어서 **파서 대조군을 못 돌렸다** — `debuggable 없음`은 약한 증거다")

    if attr(tree, "debuggable") is not None:
        fail("`android:debuggable`이 패키지된 매니페스트에 있다 — 업로드가 거부된다")
    else:
        ok("android:debuggable 없음")

    cleartext = attr(tree, "usesCleartextTraffic")
    if cleartext == "false":
        ok("usesCleartextTraffic=false — 평문 HTTP를 막는다")
    elif cleartext is None:
        # ⚠️ 없으면 targetSdk 28+ 기본값이 false다. 그래도 **명시가 낫다** —
        #    기본값은 SDK 버전에 딸린 값이라 다음 업그레이드에서 바뀔 수 있다.
        warn("usesCleartextTraffic이 매니페스트에 없다(기본값 false에 의존한다)")
    else:
        fail(f"usesCleartextTraffic={cleartext} — 평문 HTTP가 허용된 빌드다")

    backup = must(tree, "allowBackup")
    if backup == "false":
        ok("allowBackup=false")
    else:
        fail(f"allowBackup={backup} — 사진·기록이 기기 백업으로 나간다(오너 결정: false)")

    ok(f"versionCode={must(tree, 'versionCode')} versionName={must(tree, 'versionName')}")
    print("      ⚠️ **올릴 때마다 versionCode를 +1 한다.** 같은 값은 Console이 거부한다.")

    perms = sorted(set(re.findall(rf'A: {re.escape(NS)}:name\([^)]*\)="(android\.permission\.[^"]+)"', tree)))
    if not perms:
        sys.exit("🔴 권한을 한 개도 못 읽었다 — 이 앱은 카메라·위치를 쓴다. 파서 문제다.")
    print(f"   권한 {len(perms)}개: " + " · ".join(p.rsplit('.', 1)[-1] for p in perms))
    # 🔴 데이터 보안 양식은 **이 목록**과 맞아야 한다. 하나라도 늘면 양식도 고친다.
    for risky in ("READ_MEDIA_IMAGES", "ACCESS_BACKGROUND_LOCATION", "READ_EXTERNAL_STORAGE"):
        if any(p.endswith(risky) for p in perms):
            warn(f"{risky}가 있다 — 데이터 보안 양식과 심사 설명이 필요하다")

    ok(f"minSdk={must(tree, 'minSdkVersion')}")
    target = int(must(tree, "targetSdkVersion"), 0)
    # 2026년 8월 기준 신규 앱 요건은 targetSdk 35 이상이다(정책은 매년 8월 올라간다).
    if target < 35:
        fail(f"targetSdk={target} — Play 신규 앱 요건(35+)에 못 미친다")
    else:
        ok(f"targetSdk={target}")


def legal_placeholders() -> None:
    """🔴 **여기가 escape hatch를 잡는 자리다.**"""
    print("\n법적 문서 (패키지 안의 asset)")
    contact_injected = any(
        l.startswith("CONTACT_EMAIL=") and l.split("=", 1)[1].strip()
        for l in (LOCAL_PROPS.read_text(encoding="utf-8").splitlines() if LOCAL_PROPS.is_file() else [])
    )
    for artifact, prefix in ((APK, "assets/legal/"), (AAB, "base/assets/legal/")):
        with zipfile.ZipFile(artifact) as z:
            names = [n for n in z.namelist() if n.startswith(prefix) and n.endswith(".txt")]
            if not names:
                fail(f"{artifact.name}에 법적 문서가 없다({prefix}) — 화면 20-3이 빈다")
                continue
            for n in sorted(names):
                text = z.read(n).decode("utf-8", "replace")
                found = sorted(set(re.findall(r"\{\{[^}]+\}\}", text)))
                # `{{문의_이메일}}`은 앱이 실행 시점에 바꿔 넣는다(`LegalDocs.render`) —
                # **`CONTACT_EMAIL`이 주입된 빌드에서만** 정상이다.
                allowed = {"{{문의_이메일}}"} if contact_injected else set()
                bad = [p for p in found if p not in allowed]
                if bad:
                    fail(f"{n}: 채우지 않은 칸 {' '.join(bad)} — **이 빌드는 올리면 안 된다**")
                else:
                    ok(f"{n} ({len(text):,}자)")
    if not contact_injected:
        print("      ⚠️ `CONTACT_EMAIL`이 local.properties에 없다 — 주소를 받으면 다시 빌드한다.")


def download_size() -> tuple[int, int] | None:
    """기기가 **실제로 내려받는** 크기(최소·최대). 못 재면 None.

    🔴 **`get-size total`은 `--bundle`을 받지 않는다 — `--apks`다.** 예전 판이
       `--bundle=…`을 줘서 `Missing the required --apks flag.`로 매번 실패했는데,
       그 오류를 `bundletool get-size total:` 제목 아래 그대로 찍고 **통과했다**
       (2026-08-17 실측). 즉 이 검사는 **한 번도 크기를 재지 않았다** — 다운로드
       크기가 상한을 넘어도 아무 말을 안 하는 상태였다. 계측기가 산출물보다 먼저
       틀린 세 번째 사례다(`attr` 파싱 · debuggable 대조군에 이어).

    ⚠️ 재려면 `build-apks`로 **분할까지 해 봐야** 한다(AAB 하나로는 못 잰다).
       업로드 키가 아니라 디버그 키로 서명되지만 크기 차이는 서명 블록뿐이다.
    """
    apks = AAB.with_suffix(".size.apks")
    if not apks.is_file() or apks.stat().st_mtime < AAB.stat().st_mtime:
        apks.unlink(missing_ok=True)
        b = subprocess.run(
            ["bundletool", "build-apks", f"--bundle={AAB}", f"--output={apks}", "--mode=default"],
            capture_output=True, text=True, timeout=1800,
        )
        if b.returncode != 0 or not apks.is_file():
            fail(f"bundletool build-apks가 실패해서 **다운로드 크기를 못 쟀다**: {b.stderr.strip()[:200]}")
            return None
    r = subprocess.run(
        ["bundletool", "get-size", "total", f"--apks={apks}"],
        capture_output=True, text=True, timeout=1800,
    )
    # 🔴 출력은 `MIN,MAX` 머리글 + 숫자 두 개다. 파싱이 안 되면 **초록을 찍지 않는다** —
    #    "못 쟀다"와 "작다"는 다른 문장이다(이 함수가 생긴 이유가 그것이다).
    nums = re.findall(r"^(\d+),(\d+)$", r.stdout, re.MULTILINE)
    if r.returncode != 0 or not nums:
        fail(
            "bundletool get-size가 숫자를 안 줬다 — **다운로드 크기를 못 쟀다**: "
            f"{(r.stderr or r.stdout).strip()[:200]}"
        )
        return None
    return int(nums[0][0]), int(nums[0][1])


def size_check() -> None:
    print("\n크기")
    apk_mb = APK.stat().st_size / 1024 / 1024
    aab_mb = AAB.stat().st_size / 1024 / 1024
    ok(f"APK {apk_mb:,.1f}MB (참고용 · Play에 올리는 것은 AAB다)")
    ok(f"AAB {aab_mb:,.1f}MB (on-disk — **다운로드 크기가 아니다**)")
    if AAB.stat().st_size >= PLAY_DOWNLOAD_LIMIT:
        fail(f"AAB가 {aab_mb:,.1f}MB — on-disk만으로도 압축 200MB 상한을 넘었다")
    if not shutil.which("bundletool"):
        # 🔴 못 잰 것을 조용히 넘기지 않는다. on-disk 크기는 **기기별 다운로드 크기가
        #    아니다**(밀도·ABI·언어 분할 뒤가 실제 값이라 더 작다).
        warn("bundletool이 없어서 **기기별 다운로드 크기를 못 쟀다** — on-disk 크기로만 봤다")
        return
    size = download_size()
    if size is None:
        return
    lo, hi = size
    msg = f"다운로드 크기 {lo / 1024 / 1024:,.1f}~{hi / 1024 / 1024:,.1f}MB (기기별 분할 뒤 · 상한 200MB)"
    if hi >= PLAY_DOWNLOAD_LIMIT:
        fail(msg + " — 가장 큰 조합이 상한을 넘는다")
    else:
        ok(msg)
        # ⚠️ 밀도로 갈리지 않는 asset(일러스트 2,044장)이 대부분이라 밀도 분할이
        #    거의 안 줄인다 — 줄일 곳은 asset이지 리소스가 아니다(실측: 밀도 차 0.4MB,
        #    ABI 차 6.4MB).
        print(f"      ⚠️ 폭이 {(hi - lo) / 1024 / 1024:,.1f}MB뿐이다 — 대부분이 **밀도로 안 갈리는 asset**이다")


def signing() -> None:
    print("\n서명")
    check = ROOT / "android/_tools/check_kakao_map_auth.py"
    import importlib.util as u
    spec = u.spec_from_file_location("cka", check)
    m = u.module_from_spec(spec)
    spec.loader.exec_module(m)
    keyhash = m.keyhash_of_apk(APK)
    ok(f"업로드 키 서명 확인 · 카카오 키해시 {keyhash}")
    print("      🔴 이 값이 카카오 콘솔에 없으면 **지도 화면이 죽는다**(401):")
    print("         python3 android/_tools/check_kakao_map_auth.py")
    print("      🔴 Play 앱 서명을 쓰면 구글이 다시 서명한다 — 업로드 뒤 Console의")
    print("         `앱 서명 키 SHA-1`도 `sha1_to_keyhash.py`로 바꿔 등록한다.")


def main() -> int:
    for p in (APK, AAB):
        if not p.is_file():
            sys.exit(f"🔴 산출물이 없다: {p}\n   → cd android && ./gradlew assembleRelease bundleRelease")
    gap = abs(APK.stat().st_mtime - AAB.stat().st_mtime)
    if gap > 300:
        sys.exit(
            f"🔴 APK와 AAB가 {gap / 60:.0f}분 벌어져 있다 — 다른 빌드다.\n"
            "   매니페스트는 APK에서 읽으므로 이 상태로는 AAB를 검사한 것이 아니다."
        )
    print(f"APK {APK.name} · AAB {AAB.name} (같은 빌드 · 차이 {gap:.0f}초)")

    check_manifest()
    legal_placeholders()
    size_check()
    signing()

    print()
    if FAILS:
        print(f"🔴 **올리면 안 된다** — 막는 항목 {len(FAILS)}개:")
        for f in FAILS:
            print(f"   · {f}")
        return 1
    print(f"🔵 막는 항목 없음 (경고 {len(WARNS)}개)")
    print("⚠️ 이 검사는 Console 설문·데이터 보안 양식·심사를 못 잰다 — `출시준비_AOS.md`를 본다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
