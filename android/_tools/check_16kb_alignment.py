#!/usr/bin/env python3
"""APK/AAB 안의 `.so`가 **16KB 페이지**를 지원하는지 잰다.

## 왜 필요한가

2025년 11월부터 Play는 **Android 15+를 타깃하는 새 앱·업데이트**에 16KB 페이지 크기
지원을 요구한다(우리는 `targetSdk 36`이라 해당된다). 조건은 앱이 직접 만든 코드가
아니라 **의존성이 가져온 `.so`의 ELF 헤더**에 달려 있다 — 이 앱에는 5종이 들어 있고
(카카오맵·ML Kit·CameraX 둘·androidx.graphics.path) 우리가 컴파일하지 않았다.

🔴 **틀렸을 때의 증상이 없다.** 빌드는 성공하고, 4KB 페이지 기기(지금 시장의 거의
전부)에서는 정상으로 돌고, 에뮬레이터도 4KB다. 막히는 곳은 **Play 업로드 화면**이고
(경고 또는 거부), 16KB 기기에서는 **앱이 시작하다 죽는다.** 그래서 사람 눈으로는
출시 당일에야 알게 된다.

## 무엇을 재는가

각 `.so`의 프로그램 헤더에서 `PT_LOAD`의 `p_align`을 읽어 **16384 이상**인지 본다.
`readelf`를 부르지 않는다 — 맥에는 없고(binutils 미설치), 있는 척하면 조용히 통과한다.

## 🔴 판정 대상은 **64비트 ABI뿐이다**

16KB 페이지로 도는 기기는 전부 64비트다 — `armeabi-v7a`·`x86` 라이브러리는 그 기기에
**애초에 적재되지 않는다.** 그래서 32비트 `.so`의 `p_align`이 4096인 것은 결함이 아니고,
그걸 실패로 세면 **고칠 수 없는 빨간불**이 생긴다(우리가 컴파일한 바이너리가 아니다).

실측(2026-08-16 · `assembleRelease`): 미달 3개가 나왔는데 전부 32비트였다 —
`armeabi-v7a/libK3fAndroid.so`(카카오맵) · `armeabi-v7a`·`x86`의
`libmlkitcommonpipeline.so`. **64비트 8개는 전부 16384다.** 즉 통과 상태다.

⚠️ 그래도 32비트 값을 **찍는다.** 안 찍으면 "검사가 그것도 봤다"를 알 수 없고,
   나중에 64비트 판정이 바뀌었을 때 이 파일의 실측 기록과 대조할 수 없다.

    python3 android/_tools/check_16kb_alignment.py <apk 또는 aab 경로>

종료 코드 0이면 통과, 1이면 **64비트에서** 미달이 있다.
"""

from __future__ import annotations

import struct
import sys
import zipfile

PT_LOAD = 1
REQUIRED_ALIGN = 16 * 1024


def load_aligns(data: bytes) -> list[int]:
    """ELF 바이트에서 `PT_LOAD` 세그먼트의 `p_align` 목록을 뽑는다."""
    if data[:4] != b"\x7fELF":
        raise ValueError("ELF가 아니다")
    is64 = data[4] == 2
    little = data[5] == 1
    end = "<" if little else ">"

    if is64:
        # e_phoff(0x20, 8B) · e_phentsize(0x36, 2B) · e_phnum(0x38, 2B)
        (phoff,) = struct.unpack_from(end + "Q", data, 0x20)
        phentsize, phnum = struct.unpack_from(end + "HH", data, 0x36)
    else:
        (phoff,) = struct.unpack_from(end + "I", data, 0x1C)
        phentsize, phnum = struct.unpack_from(end + "HH", data, 0x2A)

    aligns: list[int] = []
    for i in range(phnum):
        off = phoff + i * phentsize
        (p_type,) = struct.unpack_from(end + "I", data, off)
        if p_type != PT_LOAD:
            continue
        # 64비트: p_align은 헤더의 마지막 8바이트. 32비트: 마지막 4바이트.
        if is64:
            (p_align,) = struct.unpack_from(end + "Q", data, off + 0x30)
        else:
            (p_align,) = struct.unpack_from(end + "I", data, off + 0x1C)
        aligns.append(p_align)
    return aligns


def is_64bit_abi(name: str) -> bool:
    """`lib/<abi>/x.so`의 abi가 64비트인가. 판정 대상은 이것뿐이다(위 주석)."""
    return "/arm64-v8a/" in name or "/x86_64/" in name or "/riscv64/" in name


def main(path: str) -> int:
    bad: list[tuple[str, int]] = []
    checked64 = 0
    skipped32 = 0
    with zipfile.ZipFile(path) as z:
        names = [n for n in z.namelist() if n.endswith(".so")]
        if not names:
            print(f"⚠️ {path} 안에 `.so`가 없다 — 잴 것이 없다(그것도 결과다)")
            return 0
        for name in sorted(names):
            aligns = load_aligns(z.read(name))
            worst = min(aligns) if aligns else 0
            ok = worst >= REQUIRED_ALIGN
            if is_64bit_abi(name):
                checked64 += 1
                mark = "✅" if ok else "🔴"
                if not ok:
                    bad.append((name, worst))
            else:
                skipped32 += 1
                # 판정 대상이 아니다. `–`로 찍어서 "봤지만 안 센다"를 구별한다.
                mark = "–" if ok else "– (32비트 · 면제)"
            print(f"{mark} {worst:>6}  {name}")

    print(
        f"\n64비트 {checked64}개 검사 · 미달 {len(bad)}개"
        f" (기준 p_align ≥ {REQUIRED_ALIGN}) · 32비트 {skipped32}개는 면제",
    )
    if bad:
        print("\n🔴 16KB 페이지를 지원하지 않는 **64비트** 라이브러리가 있다. 길은 둘뿐이다:")
        print("   ① 해당 의존성을 16KB 지원 버전으로 올린다 (우리가 컴파일한 게 아니다)")
        print("   ② 그 기능을 뺀다")
        return 1
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
