# Android 세션 시작 문서

이 터미널은 **Android 전담**이다. 새 세션을 열면 이 파일부터 읽는다.

---

## 0. 30초 요약

**캐치플라워** — 꽃을 찍으면 AI가 종을 판별해 도감에 모으고, 지도에 남기고,
동네·친구끼리 시즌 랭킹으로 경쟁하는 앱. 화면 22개 · 도감 200종.

**기획은 끝났다. 코드는 main 14,789줄 + 테스트 10,084줄이고 화면 22개 중 19개가 돈다**
(2026-08-10 기준 · **14차**). **iOS는 다른 터미널에서 동시에 진행 중이다.**

⚠️ **`프로젝트 맥락/구현현황_AOS.md`가 지금 시점의 단면이다.** 이 파일은 환경·규칙이고,
"무엇이 되고 무엇이 안 되나"는 그 문서를 본다. **되는 것 중에도 진짜가 아닌 것이 있다**(3절).

🎉 **오너가 정한 예선 범위(1~7단계)에 미구현 화면이 없다.** 판별은 **실기기에서 PlantNet
실호출로 돈다**(누적 16건/500건·일 · 진행.md (43)). 지도 타일·핀·사용자 사진까지 실기 확인됐다.
미구현은 **로그인 01 · 장소상세 15 · 기록상세 16**뿐이고 셋 다 예선 범위 밖이다.

🔴 **지금 최대 구멍은 코드가 아니라 도감 200종이 좁은 것이다** (B-4 · 구현현황 2-13).
속이 143개뿐이라 PlantNet 답이 걸러져, **실측 200장 중 130장에서 후보가 1개만 남는다** —
"후보 3개 중 고르기"(화면 09 변형)가 사실상 안 돌고 있다. **오너 결정 대기 항목이다.**

⏸ **지금 이어서 할 일이 하나 있다 — 오너가 실물 꽃을 찍어 오면 로그를 읽는 것이다**
(진행.md (46) 끝 · 8절).

📦 **저장소가 공개돼 있고 제출물이 올라가 있다 — 9절을 먼저 본다.**
**git 원격이 생겼다**(이 문서가 8절까지 "없다"고 말했던 것). 남은 제출 요건은 **②플레이 동영상**뿐이다.

---

## 1. 반드시 먼저 읽을 것 (순서대로)

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **`프로젝트 맥락/구현현황_AOS.md`** | **지금 무엇이 되고 무엇이 안 되나 · 다음 순번. 여기부터 읽는다** |
| 2 | **`프로젝트 맥락/공유계약_iOS_AOS.md`** | **필드명·enum·정책 숫자·Mock 동작. 혼자 바꾸면 안 되는 것들** |
| 2 | **이 파일 8절** | ⏸ **지금 이어서 할 일이 거기 있다** — 오너의 실물 꽃 QA 로그 |
| 3 | **`프로젝트 맥락/공유계약_iOS_AOS.md`** | **필드명·enum·정책 숫자·Mock 동작. 혼자 바꾸면 안 되는 것들** |
| 4 | `프로젝트 맥락/진행.md` **맨 끝부터 거꾸로** | 최근 결정이 뒤에 있다. **(43)~(46)이 지금 맥락**이고 (19)는 iOS인데 AOS도 물린다 |
| 5 | `디자이너_업무/A_문구·버튼_스펙.md` | **화면 문구가 전부 여기 있다. 새로 쓰지 않는다** |
| 6 | `프로젝트 맥락/오너_결정사항.md` | 확정/미확정 구분 |
| 7 | (필요시) `와이어프레임/index.html` | 화면 22개를 브라우저로 본다 |

**시간 없으면 1·2번만 읽고 시작해도 된다.**

---

## 2. ⚠️ 환경 — 버전 조합이 정해져 있다. 벗어나면 빌드가 안 된다

설치·검증 완료 (2026-08-04). **함정을 두 번 밟고 나온 조합이라 임의로 올리지 않는다.**

| | 값 |
|---|---|
| JDK | **OpenJDK 21.0.12** · `/opt/homebrew/opt/openjdk@21` |
| Gradle | **9.6.1** |
| **AGP** | **9.3.1** ← **8.x는 빌드 불가** |
| **Kotlin** | **AGP 내장** ← **`org.jetbrains.kotlin.android` 선언 금지** |
| compileSdk / targetSdk | **36** |
| minSdk | **26** (Android 8.0) |
| SDK | `~/Library/Android/sdk` (4.1GB) |
| Build-tools / platform-tools | 36.1.0 / 37.0.1 (adb 1.0.41) |
| 에뮬레이터 | 37.1.11 · **AVD `CatchFlower_Pixel8`** (arm64 · 부팅 40초) |

환경변수는 `~/.zshrc`에 영구화됨 (`JAVA_HOME` · `ANDROID_HOME` · `ANDROID_SDK_ROOT` · `PATH`).

### 두 함정의 정확한 증상

**1. Gradle 9.6.1 + AGP 8.x →**
```
Plugin 'com.android.internal.application' relies on
'org.gradle.api.problems.internal.InternalProblems',
a Gradle internal API that was removed in Gradle 9.6.0.
```
Gradle 9.6.0이 AGP 8.x가 쓰던 내부 API를 제거했다. → **AGP 9.3.1을 쓴다.**

**2. AGP 9 + kotlin 플러그인 →** `Cannot add extension with name 'kotlin'`
AGP 9에 Kotlin 컴파일러가 내장돼 있다. → **플러그인 선언을 뺀다.**
`kotlinOptions`·`compileOptions` 블록도 필요 없다.
컴파일 산출물이 `intermediates/built_in_kotlinc/`에 나오는 걸로 확인했다.

**동작 확인된 최소 build.gradle.kts (app):**
```kotlin
plugins { id("com.android.application") }
android {
    namespace = "..."
    compileSdk = 36
    defaultConfig { minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0" }
}
```
루트에서는 `id("com.android.application") version "9.3.1" apply false`.

### 에뮬레이터 실행

```bash
emulator -avd CatchFlower_Pixel8 -no-window -no-audio &   # 헤드리스, 40초
adb wait-for-device shell getprop sys.boot_completed        # 1이면 준비 완료
```
**arm64 네이티브 이미지라 빠르다. x86 이미지를 추가하지 않는다.**

### 이 맥의 제약

**`brew install --cask`가 안 된다** (sudo 비밀번호 입력이 없는 환경).
Android Studio·temurin cask 설치 실패한다. **formula와 zip 직접 전개로만 설치한다.**
지금 조합은 그 방식으로 깔아 검증했으니 **추가 설치가 필요하면 같은 방식을 쓴다.**

---

## 3. 할 일 — **1~7단계는 끝났다**

| 순서 | 일 | 상태 |
|---|---|---|
| 1 | 프로젝트 골격 | ✅ (13) |
| 2 | 데이터 모델 + `GamePolicy` object | ✅ (14) — 단 **호출처 0건 상수가 8개**다 (구현현황 4절) |
| 3 | 200종 적재 | ✅ — `flowers.json`을 **빌드마다 원본에서 복사**한다 |
| 4 | 도감 홈·상세·필터 (04·05·06·22) | ✅ (15) |
| 5 | Mock FlowerRecognizer | ✅ |
| 6 | 카메라~판별 (07~12) · CameraX | ✅ (17)(22) — **PlantNet 실호출로 돈다**(키 없으면 Mock 폴백) |
| 7 | 랭킹·친구·마이 (17~21) | ✅ (18)(31) — 더미 걷어냈다. 남은 더미는 `FriendsScreen` 하나 |
| 8 | 온보딩·활동지역 (02·03) | ✅ (32) — **로그인 01은 예선 범위 밖**(익명 로그인으로 돈다) |
| 9 | 저장 계층 + 서버 업로드 + 지도 13·14 | ✅ (30)(35)(36)(37) — **실기 실측** |

**다음에 할 것은 `구현현황_AOS.md` 9절이 원본이다.** 지금 이어서 할 일은 8절에 있다.

⚠️ **`PlantNetRecognizer`는 이미 있다** (`recognizer/PlantNetRecognizer.kt`).
iOS가 실측 200장으로 잡은 버그(학명 색인 genus fallback — **정답이 오답에게 졌다**)는
**처음부터 피했다** — `ScientificNameIndex.flowerId(name, preferring = 후보집합)`이다.
🔴 **`preferring`을 안 넘기면 속 대표가 도감번호 최솟값으로 정해져 8월 장미가 찔레꽃(5~6월)로
번역되고 탈락한다.** 그 케이스는 `PlantNetRecognizerTest`가 고정하고 있다. `진행.md` (19) 3절.

---

## 4. ✅ Android 고유 숙제 — 끝났다 (절감 47%)

`AI인식_API_비용검토.md` 4절의 **절감 48%**가 "iOS Vision으로 꽃 여부를 공짜 판별"을
전제로 계산됐는데 **Android에는 Vision이 없었다.**

**결론: ML Kit Image Labeling으로 대체 가능. 실측 절감 47%** (가정 48%).
사진 1,000장 실측 · 번들 모델 · 과금 없음 · minSdk 26 지원. 수치는 `진행.md` (15).

⚠️ **`preFilter`를 `AlwaysPassPreFilter`로 바꾼 채 두지 않는다.**
화면 확인용으로 임시 교체한 적이 있고 원복했다 —
**남으면 모든 사진이 유료 API로 간다.** 차단율 92.4%가 곧 비용 설계다.
같은 이유로 `val month = 4` 고정도 원복했다 (**남으면 개화월 하드 필터가 죽는다**).
📌 지금은 `QaPreFilterSwitchTest`의 소스 검사가 이 둘을 막는다 — 4-1 참조.

### 4-1. QA용 1차 필터 우회 — 스위치는 **파일**이다 ((45))

화면(모니터·폰) 속 꽃 사진을 찍으면 1차 필터가 **옳게** 막는다(서브픽셀 모아레 →
`top=Pattern`·`Textile`). 그 판단은 맞지만 **뒤쪽 흐름(등록→도감→지도)을 손으로 QA할 수 없다.**

```bash
adb shell touch /data/local/tmp/cf_qa_allow_any_photo   # 켜기
adb shell rm    /data/local/tmp/cf_qa_allow_any_photo   # 끄기 (QA 끝나면 바로)
```
앱 재시작 불필요(촬영마다 파일을 본다) · **release 빌드에서는 파일이 있어도 안 열린다.**

| 하지 말 것 | 이유 |
|---|---|
| **UI 버튼으로 만들기** | 개발용 버튼을 사용자 화면에 두 번 남겼다((35)(38)). **화면에 없으면 남을 수도 없다** |
| **`BuildConfig.DEBUG`만으로 열기** | 디버그 빌드 **전체가 항상 우회**가 되어 1차 필터를 두 번 다시 검증할 수 없다 |
| **`enabled` 값 캐시** | `rm`으로 껐는데 재시작까지 열려 있다 — **끈 줄 알고 QA를 계속한다** |
| **`AlwaysPassPreFilter`로 교체** | 실제 필터가 0줄 돌아 **무엇을 우회했는지 로그에 안 남는다.** 감싸고 결과만 덮어 `QA우회(원래=Pattern)`을 남긴다 |
| **래퍼 안에서 `android.util.Log`** | JVM 테스트가 `not mocked`로 죽는다(실제로 3개 빨개졌다). 우회 표시는 `topLabel`에 싣는다 |

🔴 **켠 채로 잊으면 아무 사진이나 유료 API로 가는데 화면에는 증상이 없다.**
그래서 앱이 뜰 때 `🔴 QA 우회가 켜져 있다`를 경고로 찍는다.

---

## 5. 하지 말 것

| | 이유 |
|---|---|
| **AGP를 8.x로 내리거나 kotlin 플러그인 추가** | **빌드가 죽는다** (2절) |
| **공유계약 문서의 필드명·enum을 혼자 바꾸기** | iOS와 갈라진다 |
| **`꽃도감/꽃목록_200종.csv` 직접 수정** | **생성물이다.** 원본은 `꽃도감/_tools/flowers.py` |
| **와이어프레임 SVG 직접 수정** | 생성물. 원본은 `와이어프레임/_tools/screens_*.py` |
| **기획서 docx에 `patch_docx.py` 재적용** | **멱등이 아니다.** 원본백업에서 복원 후 적용 |
| **문구를 새로 창작** | A 문서에 이미 있다. 없으면 A에 추가하고 기록 |
| **개화기 `"3~4월"` 문자열을 클라이언트에서 파싱** | 서버/적재에서 한 번만 한다 (공유계약 1-2) |
| **DB 스키마를 만들기** | 🔵 **iOS가 이미 만들었다** — 진행.md (23) "스키마 확정" · `supabase/migrations/` 3개 · 읽는 법은 `supabase/README.md`. 고칠 게 있으면 `0004_*.sql`을 추가하고 진행.md에 남긴다 (기존 파일을 고치면 **이미 적용한 DB와 어긋난다**) |
| **랭킹을 클라이언트에서 세기** | B-6("동 10명 미만이면 구로 확장") 때문에 양쪽이 각자 세면 **다른 랭킹이 나온다.** `region_ranking()`·`friend_ranking()`을 부른다 — 📌 **`RankingRules` 16개 테스트가 이 자리에 있다**(정렬 규칙 자체는 같으니 버리지 않되, 화면 값은 서버 결과여야 한다) |
| **유료 API 호출** | 실측 보고 전 금지 (오너 규칙). PlantNet 무료 500/일 · **누적 16건** |
| **`MIN_CONFIDENCE_FOR_ANY_CANDIDATE`(0.05) 건드리기** | **오너가 승인한 계약값**이다((44) B-3-a). iOS `identifyFailureFloor`와 짝이다. 낮은 점수를 보고 "높다"고 판단하지 않는다 — [[plantnet-score-is-not-confidence]] |
| **`CaptureViewModel`·`PlantNetRecognizer`의 진단 로그 4줄 지우기** | `1차필터:` → `판별 호출 직전:` → `판별 응답:` → `화면 12:`. **이게 없으면 화면 12의 원인 3가지와 "과금됐는가"를 구분할 수 없다**((39)(46)). 로그는 **조용해져도 증상이 없어서** 테스트로 고정해 뒀다 |
| **인식기 클래스 안에서 `android.util.Log` 부르기** | JVM 테스트가 `Method i in android.util.Log not mocked`로 죽는다. `log: (String) -> Unit = {}`을 주입받고 **호출처가 넘긴다** |
| **`unitTests.isReturnDefaultValues = true` 추가** | 거부된 항목이다 — **모든 안드로이드 API가 조용히 0/null**이 되어 파싱 결함이 초록으로 통과한다. `testImplementation("org.json:json")`도 빼지 않는다 |
| **`connectedDebugAndroidTest`를 돌리고 그냥 두기** | 🔴 **끝나면서 앱을 지운다.** 오너 폰에서 앱이 사라졌다 — `adb install -r`로 다시 깔고 `pm list packages`로 확인한다 |
| **`/data/local/tmp/cf_run_network_tests`를 남겨 두기** | CI가 유료 쿼터를 태운다. 쓰고 나면 지운다 |

---

## 6. 한 스텝 끝나면

**`프로젝트 맥락/진행.md`에 덧붙인다** (오너의 상시 규칙).
**세션 제목에 `[AOS]`를 붙인다** — iOS 세션도 같은 파일에 쓴다.
**`구현현황_AOS.md`도 같이 갱신한다** — 진행.md는 시간순, 그쪽은 현재 단면이다.

```
## (20) 2026-08-XX [AOS] · 무엇을 했나
```

⚠️ **화면을 만들었으면 에뮬레이터로 본다.** `BUILD SUCCESSFUL`은 증거가 아니다 —
(17)·(18)에서 빌드·테스트가 통과한 상태로 결함 7개가 남아 있었다.
⚠️ **문서·리소스만 고친 커밋에서는 테스트가 아예 안 돈다** — `UP-TO-DATE`다((41)).
`.kt`가 아닌 것을 고쳤으면 **`--rerun-tasks`를 준다.** 개수는 `TEST-*.xml`로 센다.

---

## 7. 오너 대기 중 (전부 0원)

**`구현현황_AOS.md` 8절이 원본이다.** 요약:

| | 상태 |
|---|---|
| PlantNet 키 | ✅ **주입 장치까지 끝났다** — `local.properties` → `buildConfigField` → `AppSecrets`. 실호출 확인((43)) |
| 카카오 키 | ✅ 열렸다. 오너가 **키해시를 등록**해 `vector/auth` 200 · 타일·핀 실기 확인((37)) |
| Supabase URL | ✅ `https://ngfkkazyvbbhrcznqkar.supabase.co` (iOS·AOS **같은 프로젝트**) |
| Supabase 스키마 | ✅ **적용 확인**((29)). 랭킹 함수 3개가 실제 JWT로 200 |
| Supabase provider | ⛔ kakao·apple 둘 다 꺼짐(email만) → **화면 01은 예선 범위 밖**(익명 로그인으로 돈다) |
| **B-3-a floor** | ✅ **0.05로 확정**((44)). **건드리지 않는다**(5절) |
| **B-3-b 임계값** | ⏸ **오너 대기.** `confidenceThreshold` 0.60/0.70/0.85가 같은 점수 오해 위에 있다 — 실기기 정답 10건 중 **0.60을 넘은 건 5건**뿐이라 화면 09(1순위 크게)가 거의 안 나온다 |
| **B-4 유사종 통합** | ⏸ **오너 대기 · 남은 최대 병목**(0절) |
| 0004 SQL | ⏸ 오너가 대시보드에 붙여넣을 것 — `rpc/dong_member_count`가 `PGRST202`(404) |
| 뒷정리 | ⏸ **익명 테스트 계정 12개**를 대시보드에서 지울 것. **service_role 키는 받아서도 코드에 넣지 않는다** |

⚠️ **키는 소스에 넣지 않는다.** `local.properties`(gitignore됨) → `buildConfigField` →
`AppSecrets`. 규칙은 **키가 없어도 앱은 죽지 않고 그 기능만 꺼진다.**
**값을 찍거나 커밋하지 않는다** — 확인은 **이름 + 길이**로만 한다.

---

## 8. ⏸ 지금 이어서 할 일 — **오너의 실물 꽃 QA 로그를 읽는 것**

`진행.md` (46) 끝이 원본이다. 오너가 **실물 꽃을 찍으러 나갔고 폰을 뽑았다**(2026-08-09 밤).
**폰을 다시 꽂으면** 그때까지의 촬영 로그가 남아 있다(버퍼 비우고 스팸 태그를 침묵시켜 뒀다).

```bash
adb -s R5CX21QNVVN logcat -d -s CatchFlower
```

**네 줄을 순서대로 읽어 원인을 귀속한다** — 화면에는 아래 셋이 **똑같이**
`어떤 꽃인지 알 수 없었어요`로 보인다:

```
1차필터: 꽃=true top=… conf=…              ← false면 유료 호출 0건(과금 안 됨)
판별 호출 직전: PlantNetRecognizer 후보=98개 (8월)   ← N>0이면 그 촬영은 과금됐다
판별 응답: 받은 후보=5 […학명 점수…] → 통과=2 […] (floor=0.05)
화면 12: 통과 후보=2 1순위점수=0.018 연속실패=1
```

| | 읽는 법 | 원인 | 우리 층인가 |
|---|---|---|---|
| ⓐ | `받은 후보=0` | PlantNet이 아무것도 못 알아봤다 | 아니다 (API) |
| ⓑ | `받은 후보=N` · `통과=0` | 도감 200종·개화월에서 전부 걸렸다 | **우리 도감** (B-4) |
| ⓒ | `통과=N`인데 화면 12 | 1순위가 floor 미달 | **우리 정책** (B-3-a) |

**성공했으면** 화면 09/10/11로 가고 그 뒤 화면 13(지도 공유) → 화면 14 핀까지가 QA 대상이다.

⚠️ **QA 우회 스위치는 껐다** — 실물 꽃에는 필요 없다(4-1).
⚠️ **화면 사진이 floor에 걸린 것을 "floor가 높다"로 읽지 않는다.** 실기기 실물 민들레는
0.112·0.185였고 화면 사진은 **0.018**이었다 — 1차 필터도 그것을 `Textile`로 읽었다.
**표본의 한계이지 정책 결함이 아니다**((46)).

**폰을 꽂기 전 상태(확인해 둔 것):** QA 스위치 off · `cf_run_network_tests` 없음 ·
`View`/`VRI`/`OpenGLRenderer`/`HWUI` 태그 `SILENT`(`persist.log.tag.*` · 재부팅에도 유지) ·
새 APK 설치됨(dex에서 새 로그 문자열 확인) · 로그 버퍼 비움.
🔴 **logcat 링버퍼 상한은 5MiB다** — 크기로는 못 늘린다(`setprop persist.logd.size 16M` 실패).
Compose가 초당 수십 줄을 쏟아 **촬영 흔적이 밀려 나간 적이 있다.** 그래서 태그를 침묵시켰다.

기기: **`R5CX21QNVVN`** (SM-A256N · Android 16 / API 36 · arm64-v8a).

---

## 9. 제출 상태 — 저장소가 공개돼 있다 (2026-08-10 · 48차)

🔴 **git 원격이 생겼다.** 이 문서는 8절까지 "원격이 없다"고 말했었다 — 지금은 push된다.

| | |
|---|---|
| 저장소 | **https://github.com/Aaron-HyoSan/catchflower** (공개 · `main`) |
| 커밋 신원 | `Aaron-HyoSan` / `310650246+Aaron-HyoSan@users.noreply.github.com` **(Vercel 때와 같은 이유로 형식이 정해져 있다)** |
| APK | Release **v1.0** · `catchflower-v1.0.apk` (110,035,173 bytes) — **100MB를 넘어 Release 자산으로만 올라간다** |
| 소개 페이지 | **https://aaron-hyosan.github.io/catchflower/** (`main /docs`) |
| 제출 문서 | `제출물/` — 게임 소개 · AI 활용 · 팀원 롤 양식 · 영상 콘티 (**PDF는 생성물**) |

⚠️ **배포 APK는 debug 서명이다. release 키로 다시 서명하면 지도만 조용히 죽는다** —
카카오 콘솔에 등록된 키해시가 이 맥의 **debug 키스토어 SHA-1**(`Vbb1YnBobsq+CLwfh3xnNgVs6a8=`)이고,
불일치하면 `vector/auth`가 **401**을 주는데 화면에는 회색 배경만 보인다((35)(37)).

⚠️ **`제출물/*.pdf`를 손으로 고치지 않는다.** `python3 제출물/_tools/build_pdf.py`가 원본이고
`test_build_pdf.py`가 변환기를 검사한다. **이미지 경로가 틀려도 PDF는 정상 생성되고 그 칸만 빈다**
— 그래서 빌드 전에 이미지 존재를 확인하고, 만든 뒤에는 **페이지를 눈으로 읽어 확인**한다.

⏸ **남은 것은 ②플레이 동영상뿐이다** — 실물 꽃 촬영 + 유튜브 업로드는 사람이 한다.
콘티·녹화 명령·실패 로그 판정표가 `제출물/플레이영상_콘티.md`에 있다.
링크를 받으면 **`docs/index.html` · `제출물/게임_소개.md` · `README.md` 세 곳**에 넣는다.
