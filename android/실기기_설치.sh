#!/bin/bash
# 실기기에 캐치플라워 디버그 APK를 설치한다.
#
# 🔴 **에뮬레이터에 잘못 설치하는 것을 막는다.** `adb install`은 기기가 2대면
#    `error: more than one device` 로 죽거나, 상황에 따라 엉뚱한 쪽에 들어간다.
#    이 스크립트는 **실기기만 골라서** 넣고, 실기기가 없으면 그 이유를 말한다.
#
# 사용법:  ./실기기_설치.sh
set -u
cd "$(dirname "$0")"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

APK=app/build/outputs/apk/debug/app-debug.apk

echo "▸ 기기를 찾는다"
# `emulator-`로 시작하지 않고 상태가 `device`인 것 = 실기기.
# ⚠️ `mapfile`을 쓰지 않는다 — macOS 기본 bash는 3.2라서 그 명령이 없다
#    (`command not found`가 뜨고 배열이 비어서 "실기기가 없다"로 잘못 말한다).
REAL=""
for d in $(adb devices | awk '$2=="device" {print $1}'); do
  case "$d" in emulator-*) ;; *) REAL="$REAL $d" ;; esac
done
REAL=$(echo $REAL)
COUNT=$(echo $REAL | wc -w | tr -d ' ')

if [ "$COUNT" -eq 0 ]; then
  echo "✗ 실기기가 안 붙어 있다."
  echo
  # 승인 대기 상태를 따로 알려준다 — 이게 가장 흔한 막힘이고,
  # `adb devices`에 `unauthorized`로만 나와서 원인을 모른 채 케이블을 의심하게 된다.
  if adb devices | grep -q 'unauthorized'; then
    echo "  🔴 기기가 보이는데 `unauthorized`다 — **폰 화면에 뜬 'USB 디버깅을 허용하시겠습니까?'**"
    echo "     대화상자에서 [항상 허용] 을 눌러라. 화면이 꺼져 있으면 안 뜬다."
  elif adb devices | grep -q 'offline'; then
    echo "  🔴 `offline`이다 — 케이블을 뺐다 끼우고, 그래도 안 되면 `adb kill-server` 후 다시."
  else
    echo "  확인할 것:"
    echo "   1. 폰: 설정 > 휴대전화 정보 > 소프트웨어 정보 > 빌드번호 7번 탭 (개발자 옵션 켜기)"
    echo "   2. 폰: 설정 > 개발자 옵션 > **USB 디버깅** ON"
    echo "   3. 케이블로 맥에 연결 후, 폰 알림에서 USB 모드를 **파일 전송(MTP)** 으로"
    echo "      (충전 전용이면 adb가 못 본다)"
    echo "   4. 다시 이 스크립트를 돌린다"
  fi
  echo
  adb devices -l
  exit 1
fi

if [ "$COUNT" -gt 1 ]; then
  echo "✗ 실기기가 ${COUNT}대다. 한 대만 남기고 다시 돌려라: $REAL"
  exit 1
fi

DEV=$REAL
MODEL=$(adb -s "$DEV" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
REL=$(adb -s "$DEV" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
SDKV=$(adb -s "$DEV" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
ABI=$(adb -s "$DEV" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
echo "  → $MODEL (안드로이드 $REL · API $SDKV · $ABI)"

# minSdk 26. 이걸 넘으면 `INSTALL_FAILED_OLDER_SDK`가 나오는데 메시지가 불친절하다.
if [ -n "$SDKV" ] && [ "$SDKV" -lt 26 ]; then
  echo "✗ 이 앱은 안드로이드 8.0(API 26) 이상이다. 이 기기는 API $SDKV."
  exit 1
fi

if [ ! -f "$APK" ]; then
  echo "▸ APK가 없다 — 빌드한다"
  ./gradlew assembleDebug -q || { echo "✗ 빌드 실패"; exit 1; }
fi
echo "▸ APK: $(du -h "$APK" | cut -f1)"

echo "▸ 설치한다 (덮어쓰기)"
OUT=$(adb -s "$DEV" install -r "$APK" 2>&1)
echo "$OUT" | tail -3
if ! echo "$OUT" | grep -q 'Success'; then
  # 서명이 다른 같은 패키지가 이미 깔려 있으면 이걸로 죽는다.
  if echo "$OUT" | grep -qE 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match'; then
    echo
    echo "  🔴 같은 패키지가 **다른 서명으로** 이미 깔려 있다(예: 다른 맥에서 넣은 빌드)."
    echo "     지우고 다시 넣어라 — **앱 데이터(도감 기록·사진)도 같이 지워진다:**"
    echo "       adb -s $DEV uninstall com.catchflower.app && ./실기기_설치.sh"
  fi
  exit 1
fi

echo "▸ 실행한다"
adb -s "$DEV" shell am start -n com.catchflower.app/.MainActivity >/dev/null 2>&1
echo
echo "✅ 설치 완료 — 폰 화면을 봐라."
echo
echo "확인할 것:"
echo "  1. **홈 화면 아이콘** — 초록 원 + 꽃 (기본 안드로이드 로봇이면 잘못된 것)"
echo "  2. **앱 켤 때 스플래시** — 흰 배경 + 초록 원 + 꽃"
echo "     🔴 이 폰이 **안드로이드 11 이하**면 여기가 미검증 구간이다(에뮬레이터가 API 36이라"
echo "        확인하지 못했다). 스플래시가 안 나오거나 이상하면 그게 원인이다."
echo "  3. **꽃 촬영** — 실제 카메라로 꽃을 찍어 판별까지 (에뮬레이터로 불가능했던 칸)"
echo
echo "문제가 생기면 로그를 이렇게 뜬다:"
echo "  adb -s $DEV logcat -d | grep -E 'FATAL|catchflower|CatchFlower' | tail -40"
