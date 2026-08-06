# Android 세션 시작 문서

이 터미널은 **Android 전담**이다. 새 세션을 열면 이 파일부터 읽는다.

---

## 0. 30초 요약

**캐치플라워** — 꽃을 찍으면 AI가 종을 판별해 도감에 모으고, 지도에 남기고,
동네·친구끼리 시즌 랭킹으로 경쟁하는 앱. 화면 22개 · 도감 200종.

**기획은 끝났다. 코드는 6,870줄이고 화면 22개 중 15개가 돈다** (2026-08-06 기준).
**iOS는 다른 터미널에서 동시에 진행 중이다.**

⚠️ **`프로젝트 맥락/구현현황_AOS.md`가 지금 시점의 단면이다.** 이 파일은 환경·규칙이고,
"무엇이 되고 무엇이 안 되나"는 그 문서를 본다. **되는 것 중에도 진짜가 아닌 것이 있다**(3절).

**지금 최대 구멍은 화면이 아니라 `PlantNetRecognizer`가 0줄인 것이다.**
키는 이미 이 맥에 있다 — 막고 있는 건 아직 안 쓴 코드다 (구현현황 2-1).

---

## 1. 반드시 먼저 읽을 것 (순서대로)

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **`프로젝트 맥락/구현현황_AOS.md`** | **지금 무엇이 되고 무엇이 안 되나 · 다음 순번. 여기부터 읽는다** |
| 2 | **`프로젝트 맥락/공유계약_iOS_AOS.md`** | **필드명·enum·정책 숫자·Mock 동작. 혼자 바꾸면 안 되는 것들** |
| 3 | `프로젝트 맥락/진행.md` **맨 끝부터 거꾸로** | 최근 결정이 뒤에 있다. AOS는 (15)·(17)·(18) · **(19)는 iOS인데 AOS도 물린다** |
| 4 | `디자이너_업무/A_문구·버튼_스펙.md` | **화면 문구가 전부 여기 있다. 새로 쓰지 않는다** |
| 5 | `프로젝트 맥락/오너_결정사항.md` | 확정/미확정 구분 |
| 6 | (필요시) `와이어프레임/index.html` | 화면 22개를 브라우저로 본다 |

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

## 3. 할 일 — **1~7단계는 끝났다**

| 순서 | 일 | 상태 |
|---|---|---|
| 1 | 프로젝트 골격 | ✅ (13) |
| 2 | 데이터 모델 + `GamePolicy` object | ✅ (14) — 단 **호출처 0건 상수가 8개**다 (구현현황 4절) |
| 3 | 200종 적재 | ✅ — `flowers.json`을 **빌드마다 원본에서 복사**한다 |
| 4 | 도감 홈·상세·필터 (04·05·06·22) | ✅ (15) |
| 5 | Mock FlowerRecognizer | ✅ |
| 6 | 카메라~판별 (07~12) · CameraX | ✅ (17) — **Mock 엔진** |
| 7 | 랭킹·친구·마이 (17~21 · 더미) | ✅ (18) |
| **8** | **로그인·온보딩 (01·02·03)** | ❌ **다음 순번.** 화면·검증까지는 키 없이 된다 |

**다음에 할 것은 `구현현황_AOS.md` 9절이 원본이다.** 요약하면
8단계 → 키 주입 장치 → `PlantNetRecognizer` → 저장 계층 → 화면 13.

⚠️ **`PlantNetRecognizer`를 만들 때 iOS 구현을 먼저 읽는다.**
iOS가 실측 200장으로 잡은 버그(학명 색인 genus fallback — **정답이 오답에게 졌다**)를
AOS는 아직 안 밟았을 뿐이다. `진행.md` (19) 3절.

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
| **유료 API 호출** | 실측 보고 전 금지 (오너 규칙) |

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

---

## 7. 오너 대기 중 (전부 0원)

**`구현현황_AOS.md` 8절이 원본이다.** 요약:

| | 상태 |
|---|---|
| PlantNet 키 | ⚠️ **이미 이 맥에 있다** (`ios/Config/Secrets.xcconfig`). **AOS에 구현이 없는 것** |
| 카카오 키 | 있다. **서비스 토글**이 꺼져 있다 (`disabled OPEN_MAP_AND_LOCAL` 403) |
| Supabase URL | ✅ **받았다** — `https://ngfkkazyvbbhrcznqkar.supabase.co` (iOS·AOS **같은 프로젝트**. 갈라지면 서로의 랭킹이 안 보인다) |
| Supabase 스키마 | ✅ **확정** (iOS가 만듦). 남은 건 **오너가 SQL Editor에 붙여넣는 것** — anon 키로는 DDL을 못 돌린다 |
| Supabase provider | ⛔ 대시보드에 **kakao·apple 둘 다 꺼짐**(email만) → 로그인 화면 01 |
| B-3-a / B-3-b | iOS가 실측 근거를 올렸다. **숫자는 안 바꿨다** |

⚠️ **키는 소스에 넣지 않는다.** AOS는 아직 주입 장치가 없다 —
`local.properties`(gitignore됨) → `buildConfigField` → 읽기 전용 object.
iOS `AppSecrets`와 같은 규칙으로 만든다: **키가 없어도 앱은 죽지 않고 그 기능만 꺼진다.**
