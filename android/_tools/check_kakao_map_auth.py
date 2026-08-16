#!/usr/bin/env python3
"""**설치·제출할 APK의 서명으로** 카카오맵 인증이 통과하는지 잰다 (무료 API).

    python3 android/_tools/check_kakao_map_auth.py                      # 릴리스 APK
    python3 android/_tools/check_kakao_map_auth.py --apk <경로>
    python3 android/_tools/check_kakao_map_auth.py --keyhash "AbC…="    # 해시만 알 때

왜 이 파일이 필요한가
─────────────────────
🔴 **지도 401은 화면에 이유를 말하지 않는다.** 앱이 받는 것은
`MapAuthException(401): Unauthorized` 한 줄뿐이고, 원인이 세 가지다(이 저장소가 세 번
다 겪었다):

| 원인 | 앱 로그 | 구분하는 방법 |
|---|---|---|
| 에뮬레이터·기기 DNS 실패 | 똑같은 401 | 같은 시각 다른 호출도 실패하나 · `X-Android-Response-Source` |
| 키가 없거나 다른 키 | 똑같은 401 | 본문 `appKey(...) does not exist` |
| **키해시 미등록** | 똑같은 401 | 본문 `android keyhash mismatched! caller=…` |

**이유는 응답 본문에만 있다.** 그래서 SDK와 같은 `KA:` 헤더로 인증 엔드포인트를 직접
부르고 본문을 읽는다. 이 스크립트는 **대조군을 같이 잰다** — 통과가 확인된 해시
(`Vbb1YnBobsq+CLwfh3xnNgVs6a8=` · 이 맥 debug 키스토어)로 한 번 더 부른다. 그게 200이면
네트워크·키·신청은 정상이고 **틀린 것은 콘솔 등록값뿐**이라고 말할 수 있다.
🔴 대조군 없이 401만 보고 오너에게 일을 시키면, 그게 예전에 `카카오맵 SDK 사용 신청`을
   의심하며 **엉뚱한 일을 시킬 뻔한** 그 자리다.

🔴 **서명 키가 두 개 이상이다.** debug 키스토어 · 업로드 키(우리 릴리스 APK) ·
   **Play 앱 서명 키**(사용자가 받는 APK는 구글이 다시 서명한다). 셋 다 등록해야 하고,
   우리 맥에서 만든 APK가 되는 것은 **아무 증거가 아니다**(그건 업로드 키다).

⚠️ 키 값은 **찍지 않는다.** `local.properties`에서 읽어 헤더에만 쓰고, 출력에서
   지운다(길이만 보여 준다). 해시는 공개 정보라 그대로 찍는다 — 콘솔에 넣을 값이다.
"""

from __future__ import annotations

import argparse
import base64
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCAL_PROPS = ROOT / "android" / "local.properties"
RELEASE_APK = ROOT / "android/app/build/outputs/apk/release/app-release.apk"
AUTH_URL = "https://dapi.kakao.com/v2/maps/vector/auth"
PKG = "com.catchflower.app"

#: 통과가 확인된 해시(이 맥 debug 키스토어 · 2026-08-09 실측 200). **대조군으로만 쓴다.**
CONTROL_KEYHASH = "Vbb1YnBobsq+CLwfh3xnNgVs6a8="


def native_key() -> str:
    if not LOCAL_PROPS.is_file():
        sys.exit(f"🔴 {LOCAL_PROPS}가 없다 — 키는 저장소에 없다(빌드 시점 주입)")
    for line in LOCAL_PROPS.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("KAKAO_NATIVE_APP_KEY="):
            value = line.split("=", 1)[1].strip()
            if value:
                return value
    sys.exit("🔴 `KAKAO_NATIVE_APP_KEY`가 local.properties에 없다")


def apksigner() -> pathlib.Path:
    candidates = sorted((pathlib.Path.home() / "Library/Android/sdk/build-tools").glob("*/apksigner"))
    if not candidates:
        sys.exit("🔴 apksigner를 못 찾았다 (Android SDK build-tools)")
    return candidates[-1]


def keyhash_of_apk(apk: pathlib.Path) -> str:
    """APK **서명 인증서**의 SHA-1 → 카카오 키해시.

    🔴 키스토어에서 뽑지 않고 **APK에서** 뽑는다. 빌드가 어떤 키로 서명됐는지는
       `local.properties`의 별칭을 읽어서는 알 수 없다(설정이 안 먹었으면 debug 키로
       서명된 APK가 나오고, 그건 로컬에서 지도가 **되는** 상태다 — 가장 위험한 초록불).
    """
    if not apk.is_file():
        sys.exit(f"🔴 APK가 없다: {apk}")
    out = subprocess.run(
        [str(apksigner()), "verify", "--print-certs", str(apk)],
        capture_output=True, text=True, timeout=120,
    ).stdout
    m = re.search(r"certificate SHA-1 digest:\s*([0-9a-fA-F]{40})", out)
    if not m:
        sys.exit(f"🔴 APK 서명 SHA-1을 못 읽었다:\n{out[:400]}")
    return base64.b64encode(bytes.fromhex(m.group(1))).decode()


def probe(keyhash: str, key: str) -> tuple[int, str]:
    """SDK와 같은 모양의 `KA:` 헤더로 인증 엔드포인트를 부른다. (HTTP, 본문)"""
    req = urllib.request.Request(
        AUTH_URL,
        headers={
            "Authorization": f"KakaoAK {key}",
            "KA": (
                f"sdk/2.12.8 os/android-34 lang/ko-KR "
                f"origin/{keyhash} appPkg/{PKG}"
            ),
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except urllib.error.URLError as e:
        # 🔴 여기가 "네트워크가 없다"다. 401로 뭉개지 않는다 — 앱에서는 이것도 401로 보인다.
        sys.exit(f"🔴 dapi.kakao.com에 닿지 못했다({e.reason}) — 이 맥의 네트워크 문제다")


def reason(body: str) -> str:
    """본문에서 사람이 읽을 이유를 뽑는다. 모르는 모양이면 **그대로 보여준다.**"""
    m = re.search(r'"message"\s*:\s*"([^"]+)"', body)
    return m.group(1) if m else body.strip()[:200]


def check(keyhash: str, label: str, key: str) -> bool:
    status, body = probe(keyhash, key)
    body = body.replace(key, "«NATIVE_KEY»")
    mark = "🔵" if status == 200 else "🔴"
    print(f"   {mark} {label}: HTTP {status} · {keyhash}")
    if status != 200:
        print(f"      이유(서버 본문): {reason(body)}")
    return status == 200


def map_auth_ok(apk: pathlib.Path = RELEASE_APK) -> bool:
    """다른 스크립트가 쓰는 입구 — 지도 화면을 찍어도 되는지 한 줄로 답한다."""
    return probe(keyhash_of_apk(apk), native_key())[0] == 200


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", type=pathlib.Path, default=RELEASE_APK)
    ap.add_argument("--keyhash", help="APK 대신 이 해시로 잰다 (Play 앱 서명 키 확인용)")
    a = ap.parse_args()

    key = native_key()
    print(f"네이티브 앱 키: {len(key)}자 (값은 찍지 않는다)")

    if a.keyhash:
        target, label = a.keyhash, "받은 해시"
    else:
        target, label = keyhash_of_apk(a.apk), f"{a.apk.name} 서명"
    print()
    ok = check(target, label, key)
    # 🔴 대조군을 **항상** 잰다. 위가 401일 때 원인을 가르는 유일한 근거다.
    control = check(CONTROL_KEYHASH, "대조군(debug 키스토어 · 등록됨)", key)

    print()
    if ok:
        print("판정: 통과 — 이 서명으로 지도 타일이 뜬다")
        return 0
    if control:
        print(
            "판정: **이 서명의 키해시가 카카오 콘솔에 없다.**\n"
            "   네트워크·앱 키·SDK 사용 신청은 정상이다(대조군 200).\n"
            f"   오너가 등록할 값: {target}\n"
            "   위치: 카카오 개발자 콘솔 > 내 애플리케이션 > 앱 설정 > 플랫폼 > Android\n"
            "        (`=`까지 그대로 · 패키지명 com.catchflower.app · 여러 개 등록 가능)\n"
            "   🔴 Play 앱 서명을 쓰면 **사용자가 받는 APK는 구글 키로 서명된다** —\n"
            "      Console > 앱 서명 > 앱 서명 키 SHA-1을 `sha1_to_keyhash.py`로 바꿔 그것도 등록한다."
        )
        return 1
    print(
        "판정: 대조군도 실패했다 — 키해시 문제가 아니다.\n"
        "   앱 키나 SDK 사용 신청, 또는 카카오 쪽 장애를 본다(위 본문 참조)."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
