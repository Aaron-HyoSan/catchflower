# Android 세션 시작 문서

이 터미널은 **Android 전담**이다. 새 세션을 열면 이 파일부터 읽는다.

---

## 0. 30초 요약

**캐치플라워** — 꽃을 찍으면 AI가 종을 판별해 도감에 모으고, 지도에 남기고,
동네·친구끼리 시즌 랭킹으로 경쟁하는 앱. 화면 22개 · 도감 200종.

**기획은 끝났다. 코드는 0줄이다.** 지금부터 앱을 만든다.
**iOS는 다른 터미널에서 동시에 진행 중이다.**

---

## 1. 반드시 먼저 읽을 것 (순서대로)

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **`프로젝트 맥락/공유계약_iOS_AOS.md`** | **필드명·enum·정책 숫자·Mock 동작. 혼자 바꾸면 안 되는 것들** |
| 2 | `프로젝트 맥락/진행.md` **맨 끝부터 거꾸로** | 최근 결정이 뒤에 있다. **(9)절이 이 환경 구축 기록** |
| 3 | `디자이너_업무/A_문구·버튼_스펙.md` | **화면 문구가 전부 여기 있다. 새로 쓰지 않는다** |
| 4 | `프로젝트 맥락/오너_결정사항.md` | 확정/미확정 구분 |
| 5 | (필요시) `와이어프레임/index.html` | 화면 22개를 브라우저로 본다 |

**시간 없으면 1번만 읽고 시작해도 된다.**

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

## 3. 지금 할 일 — 키 없이 여기까지 간다

| 순서 | 일 | 산출물 |
|---|---|---|
| 1 | 프로젝트 골격 (위 2절 조합 그대로 · 빌드 통과) | 빈 앱이 에뮬레이터에 설치된다 |
| 2 | **데이터 모델 + `GamePolicy` object** (공유계약 1·3절 그대로) | 정책 숫자가 **한 파일에** 모인다 |
| 3 | **CSV 200종을 assets로 적재** (`꽃도감/꽃목록_200종.csv`) | 도감 데이터가 앱 안에 있다 |
| 4 | 도감 홈·상세·필터 (화면 04·05·06) | 200종을 실제로 넘겨본다 |
| 5 | **Mock FlowerRecognizer** (공유계약 2절 동작 고정) | |
| 6 | 카메라~판별 흐름 (화면 07~12) · CameraX | **Mock으로 전 흐름이 돈다** |
| 7 | 랭킹·친구·마이페이지 (더미 데이터) | 화면 17~21 |
| 8 | 로그인·온보딩 (화면 01·02·03) | 마지막 (인증 필요) |

**2번을 건너뛰지 않는다.** 정책 숫자 9개가 **전부 오너 미확정**이라
흩어서 하드코딩하면 답변 한 번에 수십 군데를 고쳐야 한다.

---

## 4. ⚠️ Android 세션의 고유 숙제 — 비용 계산에 구멍이 있다

**이건 iOS에 없는 일이라 이 세션이 해야 한다.**

`AI인식_API_비용검토.md` 4절의 **호출 절감 48%**와 **"DAU 135명까지 0원"**이
**"iOS Vision 프레임워크로 꽃 여부를 공짜로 판별한다"**를 전제로 계산돼 있다.
**Android에는 Vision이 없다.**

**확인할 것: ML Kit Image Labeling(온디바이스·무료)이 대체 가능한가.**

| 항목 | 확인 내용 |
|---|---|
| 비용 | 온디바이스 모델이 정말 무료인가 (번들 vs Play 서비스 배포) |
| 성능 | "꽃/식물"을 라벨로 잡아내는가 · 신발·하늘을 걸러내는가 |
| 앱 크기 | 번들형이면 APK가 얼마나 커지는가 |
| minSdk 26 | 지원하는가 |

**안 되면 절감 계산이 Android에서 틀어진다** → 진행.md에 결과를 기록하고,
틀어지면 `AI인식_API_비용검토.md` 수정이 필요하다고 알린다 (혼자 고치지 않는다).

**우선순위: 3절 1~4번을 먼저 하고 그 다음.** 지금 당장 막는 일은 아니다.

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
| **DB 스키마를 양쪽에서 만들기** | 먼저 도달한 세션이 만든다 |
| **유료 API 호출** | 실측 보고 전 금지 (오너 규칙) |

---

## 6. 한 스텝 끝나면

**`프로젝트 맥락/진행.md`에 덧붙인다** (오너의 상시 규칙).
**세션 제목에 `[AOS]`를 붙인다** — iOS 세션도 같은 파일에 쓴다.

```
## (10) 2026-08-XX [AOS] · 프로젝트 골격 + 데이터 모델
```

---

## 7. 오너 대기 중 (전부 0원)

**PlantNet 키** · **카카오 REST 키** · **Supabase 계정** · 그리고 **B-3 답변**.
없어도 3절 1~7번은 다 된다.
