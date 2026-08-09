#!/bin/bash
# 실기기에서 **판별이 왜 실패했는지** 로그로 읽는다.
#
# 🔴 **화면만으로는 원인을 가릴 수 없다.** 화면 12(`어떤 꽃인지 알 수 없었어요` /
#    3연속이면 `꽃이 아닐 수도 있어요`)는 서로 다른 네 가지가 **같은 얼굴**로 온다:
#
#      ① 1차 필터(ML Kit)가 막았다        → 유료 호출 0건
#      ② 개화월 후보가 0개였다             → 유료 호출 0건
#      ③ PlantNet이 답을 줬는데 우리 200종에 없다  → **과금됨**
#      ④ 1순위 점수 < 0.30 (MIN_CONFIDENCE) → **과금됨**
#
#    통신 오류(키 없음·비행기 모드·429 한도)는 **이 화면이 아니다** — 토스트가 뜨고
#    카메라로 되돌아간다(`CaptureViewModel`의 `RecognitionError` 분기). 그래서
#    "화면 12가 떴다"는 곧 **API 연결 문제가 아니라는 뜻**이다.
#
# 사용법:
#   1. 폰을 USB로 연결 (USB 모드 = 파일 전송, USB 디버깅 ON)
#   2. ./판별_진단.sh
#   3. 폰에서 꽃을 3장 찍는다 (실패해도 계속 찍는다)
#   4. 터미널에서 Ctrl+C
set -u
cd "$(dirname "$0")"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

REAL=""
for d in $(adb devices | awk '$2=="device" {print $1}'); do
  case "$d" in emulator-*) ;; *) REAL="$REAL $d" ;; esac
done
REAL=$(echo $REAL)
COUNT=$(echo $REAL | wc -w | tr -d ' ')

if [ "$COUNT" -eq 0 ]; then
  echo "✗ 실기기가 안 붙어 있다. USB로 연결하고 'USB 디버깅'을 켠다."
  echo "  (에뮬레이터로는 이 진단을 할 수 없다 — 카메라가 합성 도형만 비춘다.)"
  adb devices -l
  exit 1
fi
[ "$COUNT" -gt 1 ] && { echo "✗ 실기기가 ${COUNT}대다. 한 대만 남긴다: $REAL"; exit 1; }

DEV=$REAL
echo "▸ $(adb -s "$DEV" shell getprop ro.product.model | tr -d '\r') 에서 로그를 읽는다"
echo

# 앱을 다시 켠다. `인식기:`·`키 상태:` 줄은 **앱 시작 때 한 번만** 찍히므로
# 지금 켜지 않으면 그 두 줄을 영원히 못 본다.
adb -s "$DEV" logcat -c
adb -s "$DEV" shell am force-stop com.catchflower.app
adb -s "$DEV" shell am start -n com.catchflower.app/.MainActivity >/dev/null 2>&1
sleep 1

cat <<'GUIDE'
──────────────────────────────────────────────────────────────
지금 폰에서 **꽃을 3장 찍어라.** 다 찍으면 Ctrl+C.

읽는 방법:
  인식기: PlantNetRecognizer  → 실엔진이다 (Mock이면 키가 안 박힌 것)
  1차필터: 꽃=false           → ①  ML Kit가 막았다. 과금 0건.
                                  `top=`이 무엇인지 본다 (사진에 뭐가 크게 잡혔나)
  1차필터: 꽃=true            → 통과. 다음 줄을 본다
  판별 호출 직전: … 후보=0개   → ②  8월 개화종이 0개. 과금 0건
  판별 호출 직전: … 후보=98개  → **이 촬영은 과금됐다.** 그런데도 실패하면 ③ 또는 ④
  인식기 호출 실패            → 통신 오류. 이때만 "API 연결" 문제다
──────────────────────────────────────────────────────────────
GUIDE
echo

# `-v time`으로 시각을 남긴다 — 어느 촬영의 줄인지 짝지으려면 필요하다.
adb -s "$DEV" logcat -v time \
  | grep --line-buffered -E '1차필터|판별 호출 직전|인식기|키 상태|인식기 호출 실패|FATAL|AndroidRuntime'
