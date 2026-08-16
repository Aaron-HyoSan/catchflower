#!/usr/bin/env python3
"""인증서 **SHA-1 지문**을 카카오 **키 해시**로 바꾼다.

## 왜 필요한가

카카오 개발자 콘솔이 요구하는 키 해시는 인증서 SHA-1의 **바이트를 base64로 인코딩한
값**이다(16진수 문자열이 아니다). 그런데 Play 콘솔은 SHA-1을 `AA:BB:CC:…` 16진수로만
보여준다 — 그래서 **손으로 옮길 수 없다.**

🔴 **이게 필요한 이유는 서명 키가 두 개이기 때문이다.** Play 앱 서명을 쓰면 사용자가
받는 APK는 우리 업로드 키가 아니라 **구글의 앱 서명 키**로 다시 서명된다. 우리 맥에서
만든 릴리스 APK는 지도·로그인이 되는데 **스토어에서 받은 앱만 401**이 되는 사고가
정확히 이 차이에서 나온다(증상은 회색 지도와 로그인 실패이고, 오류 화면이 없다).

    # Play 콘솔 > 테스트 및 출시 > 설정 > 앱 서명 > 앱 서명 키 인증서의 SHA-1
    python3 android/_tools/sha1_to_keyhash.py "AA:BB:CC:DD:..."

    # 로컬 키스토어에서 바로 뽑을 때는 이 스크립트가 필요 없다:
    keytool -exportcert -alias <별칭> -keystore <파일> | openssl sha1 -binary | openssl base64

구분자(`:`·공백)와 대소문자는 알아서 걸러낸다. 40자(20바이트)가 아니면 세운다 —
SHA-256을 잘못 붙여넣는 것이 가장 흔한 실수이고, 그때 나오는 값도 **그럴듯하다.**
"""

from __future__ import annotations

import base64
import re
import sys


def keyhash(sha1: str) -> str:
    cleaned = re.sub(r"[^0-9a-fA-F]", "", sha1)
    if len(cleaned) != 40:
        raise SystemExit(
            f"SHA-1은 16진수 40자여야 한다 (받은 것: {len(cleaned)}자).\n"
            "  · 64자면 SHA-256을 붙여넣은 것이다 — 카카오 키 해시는 SHA-1에서 만든다.\n"
            "  · Play 콘솔의 '앱 서명 키 인증서' 항목에서 SHA-1 줄을 복사한다.",
        )
    return base64.b64encode(bytes.fromhex(cleaned)).decode()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    print(keyhash(" ".join(sys.argv[1:])))
