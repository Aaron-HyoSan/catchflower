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

/**
 * 같은 곳에서 **날값**을 읽는다. 서명 설정은 자바 소스가 아니라 Gradle이 그대로 쓰므로
 * [secret]의 따옴표가 붙으면 안 된다 — 붙으면 `"…/x.jks"` 라는 이름의 파일을 찾는다.
 */
fun prop(name: String): String =
    (secrets.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.catchflower.app"
    // ⚠️ compileSdk 37: androidx.core 1.19.0이 "compile against 37 or later"를 요구한다.
    //   targetSdk는 36으로 둔다 — compileSdk(새 API 사용 가능)와 targetSdk(런타임 동작 변경)는
    //   따로 올릴 수 있고, 런타임 동작을 지금 바꿀 이유가 없다.
    compileSdk = 37

    // ── 릴리스 서명 ──────────────────────────────────────────────────
    //
    // 키스토어는 **저장소 밖**(`~/keys/catchflower/`)에 있고 경로·비밀번호는
    // `local.properties`의 `CF_KEYSTORE_*` 네 줄에서 읽는다(키 주입과 같은 길).
    // `.gitignore`가 `*.jks`·`*.keystore`도 막고 있지만, 애초에 저장소 안에 두지 않는다.
    //
    // 🔴 **값이 없으면 서명 설정을 만들지 않는다** — 빈 문자열로 만들어 두면
    //    `bundleRelease`가 "keystore not found"로 죽거나(다른 맥) 더 나쁘게는
    //    **서명 없이 성공**해서 Play 업로드 화면에서야 막힌다. 없으면 아예 없는 것으로
    //    두고(=디버그 키로 서명), 무엇이 없는지는 아래 `check`가 말한다.
    //
    // ⚠️ **debug에 `applicationIdSuffix`를 주지 않는다.** 패키지명이 바뀌면 카카오
    //    콘솔에 등록된 패키지·키해시와 어긋나서 **지도와 로그인이 401**이 된다.
    val keystoreFile = prop("CF_KEYSTORE_FILE").takeIf { it.isNotEmpty() }?.let(::file)
    if (keystoreFile != null && keystoreFile.exists()) {
        signingConfigs.create("release") {
            storeFile = keystoreFile
            storePassword = prop("CF_KEYSTORE_PASSWORD")
            keyAlias = prop("CF_KEY_ALIAS")
            keyPassword = prop("CF_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.catchflower.app"
        minSdk = 26
        targetSdk = 36
        // ── 버전 ────────────────────────────────────────────────────
        //
        // 🔴 **규칙은 하나뿐이다: Play에 올릴 때마다 `versionCode`를 +1 한다.**
        //    같은 값으로 두 번 올리면 Play가 거부한다("이미 사용된 버전 코드").
        //    그런데 그건 **업로드 화면에서야** 알 수 있고, 그때는 이미 릴리스 노트까지
        //    다 쓴 상태다. 그래서 올리는 날 첫 번째로 하는 일이 이 줄을 고치는 것이다.
        //
        // ⚠️ **되돌릴 수 없다.** 한 번 3으로 올려 업로드하면 2는 영구히 못 쓴다.
        //    내부 테스트에 올린 것도 소진된다 — 트랙별로 따로 세지 않는다.
        //
        // `versionName`은 사람이 읽는 값이고 화면 20-2 `앱 버전` 행에 그대로 나온다
        // (`BuildConfig.VERSION_NAME`). 규칙: 기능이 늘면 1.1, 고치기만 하면 1.0.1.
        //
        // | 올린 날 | versionCode | versionName | 트랙 |
        // |---|---|---|---|
        // | (아직) | 1 | 1.0 | **한 번도 안 올렸다** — 1은 소진되지 않았다 |
        // | (준비) | 2 | 1.0 | 클로즈드 테스트 (첫 업로드 후보) |
        //
        // 🔵 **2026-09-13에 1 → 2로 올렸다**(`출시준비_AOS.md` 2절 ④).
        // ⚠️ **1을 소진해서 올린 게 아니다.** 공개된 해커톤 APK(GitHub 릴리스)가
        //    `versionCode=1`이라, 그 APK가 깔린 기기에 스토어 빌드가 **업그레이드로
        //    보이게** 하려면 더 큰 값이어야 한다. 🔴 그래도 그 기기는 **서명이 달라서
        //    설치 자체가 막힌다**(4-9) — versionCode는 그 문제를 고치지 못한다.
        //    두 개는 다른 층이고, 여기 올린 것은 「같은 값 재업로드 거부」쪽 대비다.
        versionCode = 2
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

    buildTypes {
        release {
            // R8. 🔴 **켜는 것과 켜고 확인하는 것은 다르다** — 축소는 컴파일도 테스트도
            //    통과하고 **실행 중에만** `ClassNotFoundException`·빈 화면으로 나온다.
            //    JVM 테스트 669개는 축소된 코드를 **한 줄도 보지 않는다.**
            //    그래서 릴리스 빌드는 반드시 기기에 설치해서 화면을 눌러 봐야 한다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 없으면 `signingConfig`가 null이고 AGP가 **디버그 키로** 서명한다
            // (빌드는 성공하고 Play만 거부한다 — 위 주석).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // 🔴 **언어별 분할을 끈다.** 이 앱의 문구는 전부 `values/`(기본)에 있어서 분할이
    //    켜져 있어도 지금은 문제가 없지만, 나중에 `values-en/`이 하나라도 생기면
    //    **기기 언어가 영어인 사용자만** 우리가 만들지 않은 조합을 받게 된다.
    //    무게는 무시할 만하다(용량의 대부분은 일러스트 assets이고 그건 분할 대상이 아니다).
    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
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

    /**
     * `법무/` 원본 디렉터리(개인정보 처리방침 · 이용약관 · 위치기반서비스 이용약관).
     *
     * ⚠️ 폴더째 입력으로 잡는다. 나중에 `법무/웹/`(같은 문서의 HTML 사본)이 생기면
     *    관계없는 파일 때문에 이 태스크가 한 번 더 도는데, 파일 3개 복사라 무게가 없다.
     *    반대로 파일을 하나씩 선언하면 **문서를 추가할 때 선언을 빠뜨리고**, 그때
     *    증상은 "고쳤는데 앱에는 옛 문서가 나온다"(=`UP-TO-DATE`)다.
     */
    @get:InputDirectory
    // ⚠️ 애너테이션은 `PathSensitive`고 `PathSensitivity`는 그 인자인 enum이다
    //    (`inputs.dir(...).withPathSensitivity(...)`와 이름이 헷갈린다).
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val legalSource: DirectoryProperty

    /**
     * `원본 파일명 → 자산 이름` 짝. 원본은 [com.catchflower.app.core.LegalDoc] 이지만
     * Gradle은 앱 코드를 읽을 수 없어서 **여기 한 벌이 더 있다.**
     *
     * 🔴 두 벌이 어긋나면 앱은 `assets/legal/…`을 못 찾아 `문서를 불러올 수 없어요`를
     *    그린다 — 빌드는 성공한다. 그래서 `LegalDocsTest`가 이 파일의 텍스트와 enum을
     *    맞대 본다(테스트가 `build.gradle.kts`를 읽는 유일한 이유다).
     */
    @get:Input
    abstract val legalPairs: MapProperty<String, String>

    /**
     * 남아 있어도 되는 치환자. 지금은 `{{문의_이메일}}` 하나이고, 그것도
     * **`CONTACT_EMAIL`이 주입된 빌드에서만** 허용된다(앱이 실행 시점에 바꿔 넣는다).
     */
    @get:Input
    abstract val allowedPlaceholders: SetProperty<String>

    /** true면 치환자가 남아 있을 때 빌드를 세운다(릴리스에서만 true). */
    @get:Input
    abstract val failOnLegalPlaceholder: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val target = outputDir.get().asFile
        target.mkdirs()
        source.get().asFile.copyTo(target.resolve("flowers.json"), overwrite = true)
        copyLegalDocs(target)

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

    /**
     * 법적 문서 3개를 `assets/legal/`로 복사한다. **원본은 `법무/` 폴더 하나다** —
     * (⚠️ 이 주석에 `법무` 뒤 슬래시+별표를 쓰지 않는다. **코틀린 블록 주석은 중첩돼서**
     * 그 두 글자가 주석을 하나 더 열고, 닫는 별표+슬래시가 안쪽을 닫아 **함수 본문까지
     * 주석으로 먹힌다** — 증상은 이 함수가 아니라 아래쪽 `}`에서 `Missing '}'`이다.)
     * 앱 안에 사본을 두면 문서를 고친 날 한쪽만 고쳐지고, 그때 앱은 **낡은 방침을
     * 사용자에게 보여 준다**(고지 의무가 있는 문서라 그게 곧 위반이다).
     *
     * 🔴 **자산 이름은 ASCII다**(`privacy.txt`). 한글 이름을 그대로 쓰면 macOS 파일명은
     *    NFD로 저장되고 앱이 `assets.open("개인정보_처리방침.txt")`에 넘기는 문자열은
     *    NFC라 **기기에서만 FileNotFound**가 된다(이 저장소가 이미 전 종 불일치로
     *    겪었다 — 위 일러스트 주석). 여기서 이름을 바꿔 담으므로 앱 코드에는 한글
     *    파일명이 한 글자도 없다.
     *
     * ⚠️ 원본을 **경로로 열고, 폴더를 훑어 이름을 비교하지 않는다.** 이름 비교가 바로
     *    NFD/NFC가 터지는 자리다.
     */
    private fun copyLegalDocs(target: File) {
        val legalDir = target.resolve("legal")
        legalDir.deleteRecursively()
        legalDir.mkdirs()

        val remaining = linkedMapOf<String, MutableSet<String>>()
        val allowed = allowedPlaceholders.get()
        val placeholder = "\\{\\{[^}]+}}".toRegex()

        legalPairs.get().forEach { (sourceName, assetName) ->
            val src = legalSource.get().asFile.resolve(sourceName)
            // 없으면 세운다. 조용히 건너뛰면 설정 8행은 그대로 있고 누르면
            // `문서를 불러올 수 없어요`가 뜬다 — 심사에서 반려되는 모양이다.
            check(src.isFile) { "법무/$sourceName 이 없다. 문서 원본은 `법무/`에만 있다." }
            val raw = src.readText()
            placeholder.findAll(raw).map { it.value }.filterNot { it in allowed }.forEach {
                remaining.getOrPut(sourceName) { linkedSetOf() }.add(it)
            }
            src.copyTo(legalDir.resolve(assetName), overwrite = true)
        }

        if (remaining.isEmpty()) return

        val report = remaining.entries.joinToString("\n") { (f, ph) -> "  법무/$f: ${ph.joinToString(" ")}" }
        // 🔴 **디버그에서는 경고로 끝낸다.** 오너 답(사업자명·시행일·책임자)이 오기
        //    전에도 화면을 눌러 봐야 하고, 그걸 막으면 개발이 서 버린다.
        //    반대로 릴리스에서 통과시키면 **`{{시행일}}`이 적힌 방침이 스토어에 올라간다.**
        if (!failOnLegalPlaceholder.get()) {
            logger.warn("⚠️ 법적 문서에 아직 채우지 않은 칸이 있다(릴리스 빌드는 여기서 멈춘다):\n$report")
            return
        }
        error(
            "법적 문서에 채우지 않은 칸이 남아 있어 릴리스 빌드를 멈췄다:\n$report\n" +
                "오너에게 받을 값이다(A 문서 4절 24번). 값 없이 확인만 하려면 " +
                "`-PcfAllowLegalPlaceholders=true`.",
        )
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
        val legalDir = rootProject.layout.projectDirectory.dir("../법무")
        check(legalDir.asFile.isDirectory) { "법무/ 폴더가 없다. 법적 문서 원본이 거기 있다." }
        // 🔴 **`{{문의_이메일}}`은 주소가 주입된 빌드에서만 허용한다.** 앱이 실행 시점에
        //    바꿔 넣기 때문에(`LegalDocs.render`) 치환자로 남아 있는 것이 정상인데,
        //    `CONTACT_EMAIL`이 비어 있으면 바꿔 넣을 값이 없어서 **화면에 `{{문의_이메일}}`
        //    이 그대로 보인다.** 그때는 다른 치환자와 똑같이 릴리스를 세워야 한다.
        val contactInjected = prop("CONTACT_EMAIL").isNotEmpty()
        val task = tasks.register<SyncSharedAssets>("sync${variant.name.replaceFirstChar(Char::uppercase)}SharedAssets") {
            source.set(sharedJson)
            illustSource.set(illustDir)
            legalSource.set(legalDir)
            // 짝의 원본은 `LegalDoc` enum이다. 어긋나면 `LegalDocsTest`가 잡는다.
            legalPairs.set(
                mapOf(
                    "개인정보_처리방침.txt" to "privacy.txt",
                    "서비스_이용약관.txt" to "terms.txt",
                    "위치기반서비스_이용약관.txt" to "location.txt",
                ),
            )
            allowedPlaceholders.set(if (contactInjected) setOf("{{문의_이메일}}") else emptySet())
            // ⚠️ `variant.buildType`은 String?다. null이면(있을 수 없지만) 세우지 않는다 —
            //    빌드 타입을 못 읽었다고 릴리스를 막으면 원인이 안 보이는 실패가 된다.
            failOnLegalPlaceholder.set(
                variant.buildType == "release" && !project.hasProperty("cfAllowLegalPlaceholders"),
            )
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
    // 법적 문서 = 화면 20-3 본문의 원본. `LegalDocsTest`가 이 파일들을 직접 읽어
    // 줄바꿈 재조립과 남은 치환자를 잰다. 선언하지 않으면 문서만 고친 커밋에서
    // 조용히 `UP-TO-DATE`가 된다(위 표와 같은 층).
    inputs.dir(root.dir("../법무"))
        .withPropertyName("cfTestReadsLegal").withPathSensitivity(PathSensitivity.RELATIVE)
    // 🔴 **`build.gradle.kts` 자신도 입력이다.** `LegalDocsTest`가 위 `legalPairs`
    //    (원본↔자산 짝)를 이 파일의 텍스트에서 읽어 `LegalDoc` enum과 대조한다.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("cfTestReadsBuildScript").withPathSensitivity(PathSensitivity.NONE)
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
