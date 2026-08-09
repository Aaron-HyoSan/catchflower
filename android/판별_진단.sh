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

# 앱이 안 깔려 있으면 `am start`가 조용히 실패하고 **아무 로그도 안 나온다** —
# 그러면 "로그가 안 찍힌다"를 판별 결함으로 오해한다. 먼저 확인한다.
if ! adb -s "$DEV" shell pm list packages | tr -d '\r' | grep -q '^package:com.catchflower.app$'; then
  echo "✗ 이 폰에 앱이 안 깔려 있다. 먼저 설치한다:  ./실기기_설치.sh"
  exit 1
fi
echo

# 앱을 다시 켠다. `키 상태:`는 **앱 시작 때 한 번만** 찍히므로 지금 켜지 않으면
# 그 줄을 영원히 못 본다.
#
# ⚠️ `인식기:` 줄은 다르다 — `CaptureViewModel`이 만들어질 때, 즉 **`꽃 촬영` 탭에
#    처음 들어갈 때** 찍힌다(`CaptureFlow.kt`의 `viewModel()`). 앱을 켜기만 하고
#    촬영 화면에 안 들어가면 안 나오는 게 정상이다.
adb -s "$DEV" logcat -c
adb -s "$DEV" shell am force-stop com.catchflower.app
adb -s "$DEV" shell am start -n com.catchflower.app/.MainActivity >/dev/null 2>&1
sleep 1

cat <<'GUIDE'
──────────────────────────────────────────────────────────────
지금 폰에서 **꽃을 3장 찍어라.** 다 찍으면 Ctrl+C.

🔴 **아무 꽃이나 찍으면 결과를 해석할 수 없다.** 8월에 안 피는 꽃(튤립·벚꽃·데이지)을
   찍으면 개화월 필터가 **옳게** 떨궈서 실패하는데, 그건 고장이 아니다.
   **8월 도감종 중 판별이 쉬운 7종**으로 찍어라 — 실패하면 그건 진짜 결함이다:

     토끼풀·붉은토끼풀(잔디밭) · 능소화(담장·화단) · 해바라기 · 코스모스(길가·하천변)
     낮달맞이꽃(화단·도로변) · 깨꽃(화단)

   찍는 법: 꽃 **한 송이가 네모 가이드를 꽉 채우게** 가까이. 배경에 잎·건물이 넓게
   들어가면 1차 필터가 `Plant`로 보고 점수가 흩어진다(가이드 안만 전송된다).

읽는 방법 (위에서 아래로, 촬영 1장에 2줄씩 나온다):
  키 상태: PlantNet=true      → 키가 박혀 있다 (앱 켤 때 1번)
  인식기: PlantNetRecognizer  → 실엔진이다 (`꽃 촬영` 탭 처음 들어갈 때 1번.
                                Mock이면 키가 안 박힌 것)
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
