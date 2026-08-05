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
    }

    buildFeatures {
        compose = true
        // buildConfigField를 쓰려면 명시해야 한다 (AGP 8부터 기본 off).
        buildConfig = true
    }

}

// 도감 200종 데이터는 `꽃도감/flowers.json`이 원본이다 (공유 자산 — iOS도 같은 파일을 쓴다).
// assets로 손으로 복사하면 조용히 낡는다. 빌드마다 원본에서 가져온다.
//
// ⚠️ AGP 9는 sourceSets에 Provider를 못 넣는다("You cannot add Provider instances to the
//    Android SourceSet API"). Variant API의 addGeneratedSourceDirectory를 쓰면
//    태스크 의존성도 자동으로 걸린다.
abstract class SyncSharedAssets : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val target = outputDir.get().asFile
        target.mkdirs()
        source.get().asFile.copyTo(target.resolve("flowers.json"), overwrite = true)
    }
}

androidComponents {
    onVariants { variant ->
        val sharedJson = rootProject.layout.projectDirectory.file("../꽃도감/flowers.json")
        check(sharedJson.asFile.exists()) {
            "꽃도감/flowers.json이 없다. `python3 꽃도감/_tools/build_app_data.py`를 먼저 돌린다."
        }
        val task = tasks.register<SyncSharedAssets>("sync${variant.name.replaceFirstChar(Char::uppercase)}SharedAssets") {
            source.set(sharedJson)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, SyncSharedAssets::outputDir)
    }
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
