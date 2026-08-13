import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin 플러그인은 선언하지 않는다 (AGP 9 내장). 단 Compose Compiler는 필요하다.
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── 키 주입 ──────────────────────────────────────────────────────────
// **키는 소스에 없다.** `local.properties`(.gitignore에 걸려 있다 — `git check-ignore`로
// 확인함) → `buildConfigField` → `AppSecrets`. iOS의 Secrets.xcconfig → Info.plist →
// AppSecrets와 같은 구조다.
//
// ⚠️ 없으면 **빌드를 깨지 않고 빈 문자열을 넣는다.** 키 하나 빠졌다고 도감을 못 보게
//    만들 이유가 없다 — 그 기능만 꺼지고 `AppSecrets.missingKeys`가 알린다.
//    새 맥에서는 `android/local.properties.template`을 복사해 값을 채운다.
val secrets = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * `buildConfigField`에 넣을 문자열 리터럴을 만든다.
 *
 * ⚠️ 반드시 따옴표로 감싸고 이스케이프한다. 값을 그대로 넣으면 키에 `"`나 `\`가
 *    있을 때 **생성된 자바 코드가 깨져서** "키가 틀렸다"가 아니라 "빌드가 안 된다"가 된다.
 */
fun secret(name: String): String {
    val raw = (secrets.getProperty(name) ?: System.getenv(name) ?: "").trim()
    val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.catchflower.app"
    // ⚠️ compileSdk 37: androidx.core 1.19.0이 "compile against 37 or later"를 요구한다.
    //   targetSdk는 36으로 둔다 — compileSdk(새 API 사용 가능)와 targetSdk(런타임 동작 변경)는
    //   따로 올릴 수 있고, 런타임 동작을 지금 바꿀 이유가 없다.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.catchflower.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 공유계약과 같은 이름을 쓴다. iOS는 같은 값을 xcconfig에서 읽는다.
        buildConfigField("String", "SUPABASE_URL", secret("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", secret("SUPABASE_ANON_KEY"))
        buildConfigField("String", "PLANTNET_API_KEY", secret("PLANTNET_API_KEY"))
        buildConfigField("String", "KAKAO_REST_API_KEY", secret("KAKAO_REST_API_KEY"))
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", secret("KAKAO_NATIVE_APP_KEY"))

        // 🔴 **키가 아니라 창구 주소다.** 그래도 같은 길로 넣는다 — 저장소 어디에도
        //    연락처가 없고(A 문서 4절 17번), 개인 메일을 소스에 커밋하면 앱스토어
        //    심사 페이지에 그 주소가 공개된다. 비어 있으면 화면 20-2가
        //    **`고객문의` 행을 아예 안 그린다**(빈 받는사람으로 메일 앱이 열리면
        //    보낸 사람은 접수됐다고 믿는다).
        buildConfigField("String", "CONTACT_EMAIL", secret("CONTACT_EMAIL"))

        // 🔴 **넘겨줄 APK만 x86을 뺀다** — `-PcfPhoneOnly` 를 줄 때만 적용된다.
        //
        // APK 107MB 중 **24MB가 x86·x86_64**다. 실제 폰에는 그 ABI가 없어서
        // **한 번도 실행되지 않는 코드**인데, 카카오톡 파일 전송(100MB)에는 그 24MB가
        // 걸린다. 그렇다고 기본값으로 빼면 **x86 에뮬레이터에서 앱이 설치되고 나서
        // 켜는 순간 죽는다**(`UnsatisfiedLinkError`) — 이 맥은 arm64 에뮬레이터라
        // 내가 그 고장을 못 본다. 그래서 **넘겨줄 때만** 켠다.
        if (project.hasProperty("cfPhoneOnly")) {
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    buildFeatures {
        compose = true
        // buildConfigField를 쓰려면 명시해야 한다 (AGP 8부터 기본 off).
        buildConfig = true
    }

}

// 도감 2,057종 데이터는 `꽃도감/flowers.json`이 원본이다 (공유 자산 — iOS도 같은 파일을 쓴다).
// 꽃 일러스트 2,057장도 같은 규칙이지만 원본이 **한 단계 더 있다**:
// `꽃도감_일러스트_전수/*.png`(생성물) → `pack_illust_webp.py` → `꽃도감_일러스트_전수_webp/*.webp`.
// 빌드가 읽는 것은 마지막 것이다 (PNG는 130MB라 스토어 상한을 넘는다).
// assets로 손으로 복사하면 조용히 낡는다. 빌드마다 원본에서 가져온다.
//
// ⚠️ AGP 9는 sourceSets에 Provider를 못 넣는다("You cannot add Provider instances to the
//    Android SourceSet API"). Variant API의 addGeneratedSourceDirectory를 쓰면
//    태스크 의존성도 자동으로 걸린다.
//
// ⚠️ **일러스트를 `res/drawable`이 아니라 `assets`에 넣는다.** 안드로이드 리소스
//    이름은 `[a-z0-9_]`만 허용해서 `001_개나리`가 들어갈 수 없다. 영문 변환표를 만들면
//    표와 파일이 어긋날 때 **그림만 조용히 안 나온다**(예외도 안 난다).
//    assets는 파일명을 그대로 쓰므로 도감번호로 바로 찾는다.
//
// ⚠️ **파일명은 도감번호로만 찾는다.** macOS 파일명은 한글이 **NFD(자모 분리)** 로
//    저장되는데 `flowers.json`의 이름은 NFC다 — `"001_개나리.png"`를 이름으로 조립해
//    비교하면 **전 종 불일치**한다(실제로 겪었다). 그래서 원본 파일명이
//    **번호만** 남아 있다(`0001.webp`). 이름을 자산 키로 쓰지 않는다.
abstract class SyncSharedAssets : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    /** `꽃도감/꽃도감_일러스트_전수_webp` 원본 디렉터리. */
    @get:InputDirectory
    abstract val illustSource: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val target = outputDir.get().asFile
        target.mkdirs()
        source.get().asFile.copyTo(target.resolve("flowers.json"), overwrite = true)

        // 일러스트: `0001.webp` → `flower_illust/0001.webp`
        val illustDir = target.resolve("flower_illust")
        illustDir.deleteRecursively()
        illustDir.mkdirs()

        // 🔴 **확장자와 `Flower.illustAssetName`은 같이 움직여야 한다.** 지금은 `.webp`다.
        //    한쪽만 고치면 빌드가 그 파일을 **조용히 건너뛰고**(정규식 불일치) 또는
        //    앱이 없는 이름을 찾아서, 화면에서는 **그 칸만 빈다** — 예외도 안 나고
        //    빌드 로그의 장수만 줄어든다. 그리고 그 증상은 "아트가 아직 안 온 종"과
        //    구별되지 않는다. `FlowerIllustAssetTest`가 APK 안의 실제 이름으로 잰다.
        //    ⚠️ PNG는 받지 않는다. 전수 2,057장 PNG는 130MB라 Play 업로드 상한을 넘긴다
        //    (AAB 150MB · APK 직접 100MB) —
        //    받아 주면 **빌드는 성공하고 스토어에서 막힌다**(`pack_illust_webp.py` 주석).
        //    3자리도 계속 받는다: 옛 이름(`001.webp`)이 와도 4자리로 정규화된다.
        val numbered = "^(\\d{3,4})\\.webp$".toRegex()
        var copied = 0
        val seen = mutableSetOf<Int>()
        illustSource.get().asFile.listFiles().orEmpty().sorted().forEach { f ->
            val id = numbered.find(f.name)?.groupValues?.get(1)?.toInt() ?: return@forEach
            // 같은 번호가 두 개면(`0001.webp`와 `001.webp`) 조용히 하나가 이기고
            // 어느 쪽이 들어갔는지 알 수 없다. 빌드를 세운다.
            check(seen.add(id)) { "일러스트 도감번호 $id 가 중복이다: ${f.name}" }
            f.copyTo(illustDir.resolve("%04d.webp".format(id)), overwrite = true)
            copied++
        }
        // 🔴 **장수를 센다.** 몇 장이 빠져도 앱은 잘 돈다 — 그 칸만 빈다.
        //    변환 폴더가 낡아서 200장만 들어가도 빌드는 성공한다. 그래서 로그로 남긴다.
        logger.lifecycle("꽃 일러스트 $copied 장을 assets/flower_illust 로 복사했다")
    }
}

androidComponents {
    onVariants { variant ->
        val sharedJson = rootProject.layout.projectDirectory.file("../꽃도감/flowers.json")
        check(sharedJson.asFile.exists()) {
            "꽃도감/flowers.json이 없다. `python3 꽃도감/_tools/build_app_data.py`를 먼저 돌린다."
        }
        // 🔴 **생성물을 가리킨다.** `pack_illust_webp.py`가 만든다 —
        //    `꽃도감_일러스트_전수/`(PNG 130MB)를 직접 가리키면 APK가 스토어 상한을 넘는다.
        val illustDir = rootProject.layout.projectDirectory.dir("../꽃도감/꽃도감_일러스트_전수_webp")
        check(illustDir.asFile.isDirectory) {
            "꽃도감/꽃도감_일러스트_전수_webp 폴더가 없다. " +
                "→ python3 꽃도감/_tools/pack_illust_webp.py 를 먼저 돌린다."
        }
        val task = tasks.register<SyncSharedAssets>("sync${variant.name.replaceFirstChar(Char::uppercase)}SharedAssets") {
            source.set(sharedJson)
            illustSource.set(illustDir)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, SyncSharedAssets::outputDir)
    }
}

/**
 * 🔴 **파일을 읽는 테스트는 그 파일을 입력으로 선언해야 한다.**
 *
 * 이 저장소의 테스트 12개는 `File("src/main/…")`·A 문서·`res/`를 직접 읽는다
 * (`IconAssetTest`, `ButtonLabelSourceTest`, `PermissionCopyTest`, `DeadButtonTest` …).
 * 그런데 Gradle은 **테스트 소스와 클래스패스만** 입력으로 안다 — `res/`·매니페스트·
 * 마크다운을 고쳐도 `testDebugUnitTest`가 `UP-TO-DATE`로 통과한다.
 *
 * 실측(2026-08-09, 돌연변이 168·169·171):
 *
 * | 훼손 | 캐시 그대로 | `--rerun-tasks` |
 * |---|---|---|
 * | `drawable-xxhdpi/ic_tab_map.png` 삭제 | **BUILD SUCCESSFUL** | 1 failed |
 * | 매니페스트 `android:icon` 제거 | **BUILD SUCCESSFUL** | 1 failed |
 * | A 문서 버튼 문구 훼손 | **BUILD SUCCESSFUL** | 2 failed |
 *
 * ⚠️ **`.kt`를 고칠 때는 제대로 돈다**(돌연변이 170은 캐시 상태로 잡혔다). 그래서
 *    "테스트가 캐시 때문에 안 도는구나"를 평소에 눈치챌 수 없다 — 코드를 고치는 동안엔
 *    항상 다시 돌기 때문이다. 문서·리소스만 고친 커밋에서만 조용히 통과한다.
 *
 * ⚠️ **`--rerun-tasks`를 습관으로 만드는 것으로 대신하지 않는다.** 그건 사람이 기억해야
 *    하고, CI에서는 빠진다. 입력을 선언하면 Gradle이 대신 기억한다.
 */
tasks.withType<Test>().configureEach {
    val root = rootProject.layout.projectDirectory
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("cfTestReadsRes").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("src/main/AndroidManifest.xml"))
        .withPropertyName("cfTestReadsManifest").withPathSensitivity(PathSensitivity.RELATIVE)
    // A 문서 = 모든 버튼 문구의 원본. `문구를 새로 쓰지 않는다`를 지키는 검사가 이걸 읽는다.
    inputs.file(root.file("../디자이너_업무/A_문구·버튼_스펙.md"))
        .withPropertyName("cfTestReadsCopySpec").withPathSensitivity(PathSensitivity.NONE)
    // 마이그레이션 SQL = 서버 계약의 원본. `ReactionContractTest`가 RPC 인자 이름·
    // 컬럼명·댓글 길이 상한을 **SQL 원문에서 읽어** 코드와 대조한다.
    //
    // ⚠️ **선언하지 않으면 위 표와 똑같이 조용히 통과한다.** 실측(2026-08-11):
    //    0007의 시그니처를 `d_id` → `discovery_id`로 훼손해도 캐시 상태에서는
    //    **BUILD SUCCESSFUL**, `--rerun-tasks`에서만 1 failed였다.
    //    SQL만 고치는 커밋(오너가 스키마를 손보는 날이 그렇다)에서 정확히 새어 나간다.
    inputs.dir(root.dir("../supabase/migrations"))
        .withPropertyName("cfTestReadsMigrations").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    // LocalLifecycleOwner가 compose-ui에서 여기로 옮겨졌다 (CameraX 바인딩에 필요).
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // 카메라 (화면 07). 앨범 선택은 **일부러 넣지 않는다** — 기획서 5장 규칙이고
    // 화면 07에 "앨범 사진은 등록할 수 없어요"라고 고지한다.
    val cameraX = "1.5.2"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-core:$cameraX")

    // 지도 (화면 14~16). A-3 확정 = 카카오맵.
    //
    // 🔴 **`mavenCentral()`에 없다.** `settings.gradle.kts`의 `devrepo.kakao.com`에서
    //    받는다 — 그 줄을 지우면 이 의존성이 404가 되고 **빌드가 죽는다.**
    //
    // ⚠️ 이 SDK는 **네이티브 앱 키**로 초기화한다(REST 키가 아니다). REST 키는 화면 02의
    //    주소↔좌표 되짚기가 이미 쓰고 있고, **둘은 서로 대체되지 않는다.**
    //    게다가 콘솔에 **키 해시와 패키지명**이 등록돼야 타일이 내려온다 —
    //    등록 전에는 `MapAuthException(401)`이고 화면은 **회색 지도**다(오류로 보이지 않는다).
    //
    // ⚠️ **`카카오맵 Android SDK 사용 신청`을 먼저 의심하지 마라.** 2026-08-09에 그걸로
    //    의심했는데 아니었다 — 인증 엔드포인트 본문이 이유를 문장으로 말해 준다
    //    (`android keyhash mismatched!`). 추측하지 말고 그 본문을 읽는다((35)).
    implementation("com.kakao.maps.open:android:2.14.1")

    // 온디바이스 '꽃 여부' 1차 필터 (비용 문서 4절 절감 장치 ②의 Android 쪽).
    // 번들 모델 — 네트워크·과금 없음. iOS Vision 프레임워크에 대응하는 자리다.
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // DexFilter·GamePolicy는 안드로이드 의존이 없는 순수 Kotlin이라 JVM 테스트로 돈다
    // (에뮬레이터 불필요).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // ⚠️ `org.json`은 android.jar에 **껍데기만** 있어서 JVM 테스트에서 호출하면
    //    "Method optJSONArray in org.json.JSONObject not mocked"로 던진다.
    //    실물을 테스트 클래스패스에 넣는다 (android.jar보다 앞에 놓인다).
    //    `unitTests.isReturnDefaultValues = true`로 덮으면 파싱이 조용히 null을 돌려주고
    //    **테스트가 초록으로 통과한다** — 그게 더 위험하다.
    testImplementation("org.json:json:20250517")

    // 1차 필터 실측은 실기기/에뮬레이터가 필요하다 (ML Kit 추론).
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
