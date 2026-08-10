package com.catchflower.app.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **납품 아이콘이 실제로 리소스로 들어와 있고, 코드가 그걸 쓰는가.**
 *
 * ## 무엇을 잡는가
 *
 * 🔴 **아이콘이 빠지면 앱은 죽지 않는다.** 안드로이드는 없는 리소스를 컴파일 시점에
 *    잡아 주지만(`R.drawable.x` 없음 = 컴파일 에러), **PNG 파일만 사라진 경우는
 *    빌드가 통과한다** — `drawable-xxhdpi/`에서 한 밀도만 지워도 다른 밀도가 대신
 *    쓰이고, **전부 지우면 그때야 컴파일이 깨진다.** 즉 "3개 밀도만 있는" 상태는
 *    조용히 통과하고, 그 밀도의 기기에서만 흐릿하게 나온다.
 *
 * 🔴 **런처 아이콘은 앱을 켜서 확인할 수 없다.** `android:icon`이 없으면 안드로이드
 *    기본 로봇 아이콘이 뜨는데, 앱 안 화면은 전부 정상이라 **개발 중에 한 번도
 *    눈에 띄지 않는다**(홈 화면을 봐야 보인다).
 *
 * ## 왜 파일과 소스를 읽는가
 *
 * `R` 클래스와 리소스 병합은 안드로이드 빌드 산물이라 JVM 테스트에서 볼 수 없다.
 * 실제 렌더는 기기로 확인했다(진행.md (41)). 여기서 막는 것은 **파일이 조용히
 * 빠지거나 손으로 고쳐지는 것**이다.
 */
class IconAssetTest {

    /**
     * 앱 내 아이콘이 **모든 밀도에** 있다.
     *
     * ⚠️ 개수를 상수로 박지 않는다 — 생성 스크립트의 `MAPPING`이 원본이고,
     *    여기서는 **`mdpi`에 있는 것이 다른 밀도에도 다 있는지**를 본다.
     *    (한 밀도만 빠지는 것이 가장 잡기 어려운 상태다.)
     */
    @Test
    fun 앱내_아이콘이_모든_밀도에_있다() {
        val base = densityDirs.getValue("mdpi").listFiles().orEmpty()
            .filter { it.extension == "png" }.map { it.name }.sorted()
        assertTrue("drawable-mdpi에 아이콘이 없다 — 생성 스크립트를 돌리지 않았나", base.size >= 7)

        densityDirs.forEach { (density, dir) ->
            val here = dir.listFiles().orEmpty().filter { it.extension == "png" }
                .map { it.name }.sorted()
            assertEquals("drawable-$density 의 아이콘이 mdpi와 다르다", base, here)
        }
    }

    /**
     * 원본이 44종인데 **쓰는 것만 들어와 있다.**
     *
     * 🔴 오너 지시: "모든 아이콘을 적용시킬 필요는 없다. 현재 디자인에 있는 아이콘들에
     *    필요한 부분들을 적용시키는 거야"(2026-08-09). 안 쓰는 아이콘을 미리 넣으면
     *    **그것을 놓을 자리를 만들게 되고**, 그 자리는 눌러도 아무 일이 없는 버튼이 된다
     *    (이 저장소가 반복해서 지적한 결함).
     *
     * ⚠️ 그래서 "많이 들어오는 것"도 실패다. 상한을 둔다.
     */
    @Test
    fun 쓰지_않는_아이콘을_넣지_않았다() {
        val names = densityDirs.getValue("mdpi").listFiles().orEmpty()
            .filter { it.extension == "png" }.map { it.nameWithoutExtension }.sorted()
        assertEquals(
            "리소스에 들어온 아이콘 목록이 바뀌었다 — 화면에서 실제로 쓰는지 확인하고 이 목록을 고친다",
            listOf(
                "ic_back", "ic_cancel", "ic_capture", "ic_close", "ic_filter",
                "ic_flash", "ic_friends_only", "ic_help", "ic_person_add",
                "ic_place", "ic_public", "ic_season", "ic_switch_camera",
                "ic_tab_dex", "ic_tab_map", "ic_tab_my", "ic_tab_ranking",
            ),
            names,
        )
    }

    /**
     * 리소스에 있는 아이콘을 **코드가 실제로 쓴다.**
     *
     * 🔴 위 [쓰지_않는_아이콘을_넣지_않았다]는 **목록이 바뀌었는지만** 본다 —
     *    목록에 이름을 넣고 아무도 안 쓰면 그대로 초록이다. 실제로 그랬다:
     *    `ic_retry`·`ic_region`이 **다섯 밀도에 다 있는데 참조 0곳**이었고
     *    (생성 스크립트가 만들고, 붙일 자리가 아트와 안 맞아 안 붙였다),
     *    빌드·테스트·화면 전부 정상이었다.
     *
     * ⚠️ 남아 있으면 다음 사람이 **"쓰라고 만든 것"으로 읽고 자리를 만든다** —
     *    죽은 버튼이 되는 경로다. 생성 스크립트도 같은 것을 세지만
     *    (`build_android_ui_icons.py`의 `sweep`), 그건 **스크립트를 돌려야** 돈다.
     */
    @Test
    fun 리소스에_있는_아이콘을_코드가_쓴다() {
        val sources = mainSources.joinToString("\n") { bodyOf(it) }
        val unused = densityDirs.getValue("mdpi").listFiles().orEmpty()
            .filter { it.extension == "png" }
            .map { it.nameWithoutExtension }
            .filter { !sources.contains("R.drawable.$it") }
            .sorted()
        assertEquals(
            "res/에 있는데 코드에서 아무도 안 쓰는 아이콘이다 — 지우거나 붙인다: $unused",
            emptyList<String>(),
            unused,
        )
    }

    /**
     * **위 검사가 실제로 빨개질 수 있다** — 없는 리소스 이름을 하나 넣어 확인한다.
     *
     * 🔴 이 대조군이 없으면 `sources.contains(...)`가 **항상 참이 되는 실수**
     *    (예: `R.drawable.` 접두사를 빼먹어 `it`만 찾는 것 — 파일명 문자열은
     *    KDoc·주석 어디에나 있다)를 알아챌 방법이 없다. 그러면 위 검사는
     *    영구히 초록이고 아무것도 막지 못한다.
     */
    @Test
    fun 미사용_검사가_실제로_잡는다() {
        val sources = mainSources.joinToString("\n") { bodyOf(it) }
        assertTrue(
            "쓰지 않는 이름(ic_definitely_unused)을 '쓴다'고 판정한다 — 이 검사는 무력하다",
            !sources.contains("R.drawable.ic_definitely_unused"),
        )
        // 그리고 실제로 쓰는 것은 찾아야 한다(양쪽 다 확인).
        assertTrue(
            "실제로 쓰는 ic_tab_dex를 못 찾는다 — 경로나 주석 제거가 잘못됐다",
            sources.contains("R.drawable.ic_tab_dex"),
        )
    }

    /**
     * 런처 아이콘이 **매니페스트에 걸려 있다.**
     *
     * 🔴 이게 없으면 홈 화면에 **안드로이드 기본 로봇**이 뜬다. 빌드·실행·앱 내 화면
     *    전부 정상이라 앱을 켜서 확인하는 동안에는 드러나지 않는다.
     */
    @Test
    fun 런처_아이콘이_매니페스트에_걸려_있다() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "android:icon이 없다 — 런처에 기본 로봇 아이콘이 뜬다",
            manifest.contains("""android:icon="@mipmap/ic_launcher""""),
        )
        assertTrue(
            "android:roundIcon이 없다 — 원형 런처가 레거시 PNG를 다시 깎는다",
            manifest.contains("""android:roundIcon="@mipmap/ic_launcher""""),
        )
        // 어댑티브 아이콘 XML과 그 두 층이 다 있어야 한다.
        val adaptive = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue("어댑티브 아이콘 XML이 없다", adaptive.exists())
        val xml = adaptive.readText()
        assertTrue("전경 층이 없다", xml.contains("@mipmap/ic_launcher_foreground"))
        assertTrue("배경 층이 없다", xml.contains("@color/ic_launcher_background"))

        mipmapDirs.forEach { (density, dir) ->
            listOf("ic_launcher.png", "ic_launcher_foreground.png").forEach { name ->
                assertTrue("mipmap-$density/$name 이 없다", File(dir, name).exists())
            }
        }
    }

    /**
     * 스플래시가 **두 경로 다** 걸려 있다.
     *
     * 🔴 안드로이드 12(API 31)부터는 시스템이 스플래시를 그리고 `windowSplashScreen*`
     *    속성으로만 꾸민다. 그 속성은 **v31 미만에서 컴파일 에러**라 디렉터리를 갈랐다.
     *    ⚠️ **내 에뮬레이터는 API 36이라 v31 미만 경로가 확인되지 않는다** —
     *       한쪽만 고치면 minSdk 26~30 기기에서 스플래시가 사라지고, 나는 못 본다.
     *       그래서 두 파일이 같이 있는지를 테스트가 센다.
     */
    @Test
    fun 스플래시가_두_경로에_다_걸려_있다() {
        val v31 = File("src/main/res/values-v31/themes.xml")
        assertTrue("values-v31/themes.xml이 없다 — 안드로이드 12+에 스플래시가 없다", v31.exists())
        val v31Text = v31.readText()
        assertTrue(
            "windowSplashScreenAnimatedIcon이 없다",
            v31Text.contains("android:windowSplashScreenAnimatedIcon"),
        )
        assertTrue(
            "스플래시 아이콘에 배지를 넣었다 — 시스템이 배경 원을 또 그려서 초록 원이 겹친다",
            v31Text.contains("@mipmap/ic_launcher_splash"),
        )
        assertTrue(
            "같은 스타일 이름이어야 매니페스트의 android:theme가 이걸 집는다",
            v31Text.contains("""name="Theme.CatchFlower""""),
        )

        val base = File("src/main/res/values/themes.xml").readText()
        assertTrue(
            "v31 미만 경로(windowBackground)가 없다 — minSdk 26~30 기기에 스플래시가 없다",
            base.contains("@drawable/splash_background"),
        )
        assertTrue(
            "splash_background.xml이 없다",
            File("src/main/res/drawable/splash_background.xml").exists(),
        )
        mipmapDirs.forEach { (density, dir) ->
            listOf("ic_launcher_splash.png", "ic_splash_badge.png").forEach { name ->
                assertTrue("mipmap-$density/$name 이 없다", File(dir, name).exists())
            }
        }
    }

    /**
     * 컬러 아이콘에 **`tint`를 줄 수 없게** 되어 있다.
     *
     * 🔴 [androidx.compose.material3.Icon]은 그림을 `tint` 한 색으로 **덮어 그린다.**
     *    납품 아이콘은 컬러(분홍 꽃잎·노란 꽃심·초록 잎)라서 tint를 주면 **초록
     *    실루엣**이 된다 — 형태는 남으니 "아이콘이 나온다"로 보이고, 원본과 나란히
     *    놓고 봐야 틀린 것을 안다.
     *
     * ⚠️ **"호출부에 `tint`라는 글자가 있나"로 검사하면 안 된다 — 그렇게 썼다가
     *    고쳤다.** `BottomNav.kt`에는 `val tint = if (selected) …`가 있는데 그건
     *    **라벨 텍스트 색**이고 아이콘과 무관하다. 그 검사는 정상 코드를 위반으로
     *    잡는다(그리고 진짜 위반은 `color = `처럼 다른 이름으로 들어올 수 있다).
     *
     * 그래서 **글자를 세지 않고 구조로 막는다**: [CfIcon]이 색 인자를 아예 받지
     * 않으므로 tint를 주려는 시도는 **컴파일 에러**다. 이 테스트가 지키는 것은
     * "누가 편의상 색 인자를 추가하는 것"이다.
     */
    @Test
    fun 납품_아이콘은_색_인자를_받지_않는다() {
        val body = bodyOf(File("src/main/java/com/catchflower/app/ui/component/CfIcon.kt"))
        val signature = body.substringAfter("fun CfIcon(").substringBefore(")")
        assertTrue("CfIcon 정의를 못 찾았다", signature.contains("id"))
        listOf("tint", "color", "colorFilter").forEach { param ->
            assertTrue(
                "CfIcon이 `$param` 인자를 받는다 — 컬러 아이콘이 단색으로 덮일 수 있다",
                !signature.contains(param),
            )
        }
    }

    /**
     * 납품 아이콘이 **[CfIcon]만 거쳐서** 그려진다.
     *
     * 🔴 **이 검사를 두 번 잘못 썼다. 두 번 다 정상 코드를 위반으로 잡았다.**
     *
     * | 시도 | 왜 틀렸나 |
     * |---|---|
     * | 호출부에 `tint`라는 글자가 있나 | `BottomNav.kt`의 `val tint = …`는 **라벨 텍스트 색**이다 |
     * | 본문에 `"Icon(\n"`이 있나 | **`CfIcon(`의 뒷부분과 같다** — 방금 쓴 정상 호출부 전부가 걸렸다 |
     *
     * ⚠️ 두 번째는 **새 검사의 첫 red를 코드 탓으로 돌리면** 잘 쓴 코드를 되돌렸을
     *    상황이었다. 부분 문자열로 식별자를 찾을 때는 **앞 경계**가 반드시 필요하다
     *    ([MATERIAL_ICON_CALL]), 그리고 그 경계가 실제로 동하는지는
     *    [예약된_검사가_CfIcon을_잡지_않는다]가 센다.
     *
     * ## 무엇이 위반인가
     *
     * `Icon(imageVector = …, tint = …)`는 **위반이 아니다.** Material 아이콘은 단색
     * 벡터라 tint가 맞는 사용법이다(`Buttons.kt`의 [CfTextButton]이 그렇다).
     * 위반은 **컬러 납품 아트를 tint 가능한 경로로 그리는 것**이고, 납품 아트는
     * `painterResource`로만 들어온다. 그래서 두 가지를 본다:
     *
     * 1. `painterResource`가 [CfIcon] 파일 밖에 없다 ← 컴포저블 종류와 무관하게 막힌다
     * 2. material3 `Icon(`이 `painter`를 받지 않는다
     *
     * ⚠️ 1번이 나중에 걸리적거릴 수 있다(납품 **일러스트**를 `painterResource`로 넣고
     *    싶어질 때). 그때는 이 목록에 그 파일을 추가하되, **왜 tint 위험이 없는지**를
     *    같이 적는다 — 목록을 늘리는 것 자체가 판단을 요구해야 한다.
     */
    @Test
    fun 납품_아이콘은_CfIcon만_거친다() {
        val allowed = setOf("CfIcon.kt")
        val viaPainter = mainSources
            .filter { it.name !in allowed && bodyOf(it).contains("painterResource") }
            .map { it.name }
        assertEquals(
            "납품 아트를 CfIcon 밖에서 그린다 — tint를 줄 수 있는 경로가 열린다: $viaPainter",
            emptyList<String>(),
            viaPainter,
        )

        val iconWithPainter = mainSources.filter { file ->
            val body = bodyOf(file)
            MATERIAL_ICON_CALL.findAll(body).any { match ->
                // ⚠️ 인자 목록을 `substringBefore(")")`로 자르면 안 된다 —
                //    `modifier = Modifier.size(20.dp)`의 닫는 괄호에서 먼저 끊겨서
                //    그 뒤의 `painter =`를 놓친다. 넉넉한 창으로 본다.
                body.substring(match.range.last, minOf(body.length, match.range.last + 300))
                    .contains("painter")
            }
        }.map { it.name }
        assertEquals(
            "material3 Icon에 painter를 넘긴다 — CfIcon을 쓴다: $iconWithPainter",
            emptyList<String>(),
            iconWithPainter,
        )
    }

    /**
     * **위 검사가 `CfIcon(`을 잡지 않는다** — 그리고 진짜 `Icon(`은 잡는다.
     *
     * 🔴 이 테스트가 없으면 [MATERIAL_ICON_CALL]을 다시 부분 문자열로 되돌려도
     *    **아무것도 빨개지지 않는다**(위 검사는 위반이 0건일 때 초록이고, 경계가
     *    깨지면 초록이 아니라 **거짓 red**가 되므로 그때는 코드를 의심하게 된다).
     *    검사기는 **대조군으로 먼저 잰다.**
     */
    @Test
    fun 예약된_검사가_CfIcon을_잡지_않는다() {
        listOf("CfIcon(", "    CfIcon(\n", "CfIconRow(", "myIcon(").forEach {
            assertTrue("$it 를 material3 Icon 호출로 잡는다 — 앞 경계가 없다", !MATERIAL_ICON_CALL.containsMatchIn(it))
        }
        listOf("    Icon(\n", "Icon(imageVector", "androidx.compose.material3.Icon(").forEach {
            assertTrue("$it 를 놓친다 — 이 검사는 아무것도 막지 못한다", MATERIAL_ICON_CALL.containsMatchIn(it))
        }
    }

    /**
     * 탭 아이콘 표가 **`else`로 닫혀 있지 않다.**
     *
     * ⚠️ `else -> ic_tab_dex` 같은 기본값을 두면 탭이 하나 늘 때 **새 탭이 도감
     *    아이콘을 달고 조용히 나온다.** `when`을 완전하게 두면 그 순간 컴파일이 깨진다.
     */
    @Test
    fun 탭_아이콘_표에_기본값이_없다() {
        val body = bodyOf(File("src/main/java/com/catchflower/app/ui/component/CfIcon.kt"))
        val table = body.substringAfter("val NavTab.iconRes").substringBefore("\n}")
        assertTrue("iconRes 표를 못 찾았다(이름이 바뀌었나)", table.contains("NavTab.DEX"))
        assertTrue(
            "탭 아이콘 표에 else가 있다 — 새 탭이 남의 아이콘을 달고 나온다",
            !table.contains("else ->"),
        )
        // 5탭 전부가 표에 있어야 한다.
        listOf("DEX", "MAP", "RANKING", "MY", "CAPTURE").forEach {
            assertTrue("iconRes 표에 NavTab.$it 이 없다", table.contains("NavTab.$it"))
        }
    }

    /**
     * 아이콘이 텍스트를 **대체하지 않았다.**
     *
     * 🔴 A 문서 1절 44번: `아이콘만 있는 버튼은 만들지 않는다. 반드시 텍스트를 병기한다`.
     *    아이콘이 예뻐지면 라벨을 지우고 싶어지는데, 이 앱의 타깃(중장년)에서
     *    라벨은 **학습 비용을 없애는 장치**다. 게다가 선택 상태를 색으로 말할 수
     *    없어서([CfIcon]) **라벨 색이 선택 표시의 절반을 나른다** — 라벨을 지우면
     *    회색조 하나만 남고, 색약 사용자에게는 구분이 사라진다.
     */
    @Test
    fun 하단_내비에_라벨이_남아_있다() {
        val body = bodyOf(File("src/main/java/com/catchflower/app/ui/component/BottomNav.kt"))
        assertTrue(
            "탭 라벨(Text)이 없어졌다 — A 문서 1절 44번 위반이고 선택 표시도 약해진다",
            body.contains("text = tab.label"),
        )
        assertTrue(
            "셔터 라벨(`꽃 촬영`)이 없어졌다",
            body.contains("text = NavTab.CAPTURE.label"),
        )
        // 라벨 색이 선택 여부를 계속 말해야 한다.
        assertTrue(
            "라벨 색이 선택 상태를 따르지 않는다",
            body.contains("if (selected) CfColor.Primary"),
        )
    }

    /**
     * **생성물을 손으로 고치지 못하게** 표시가 붙어 있다.
     *
     * ⚠️ `ic_launcher_background.xml`은 스크립트가 쓴다. 손으로 색을 바꾸면
     *    다음 실행에서 **조용히 되돌아간다** — 바꾼 사람은 자기 수정이 사라진 이유를
     *    알 수 없다.
     */
    @Test
    fun 생성물에_경고가_붙어_있다() {
        val color = File("src/main/res/values/ic_launcher_background.xml").readText()
        assertTrue(
            "ic_launcher_background.xml에 생성물 경고가 없다",
            color.contains("생성물") && color.contains("build_android_launcher.py"),
        )
    }

    private companion object {

        /**
         * material3 `Icon(` **호출**. `CfIcon(`·`MyIcon(`을 잡지 않는다.
         *
         * 🔴 **앞 경계가 이 정규식의 전부다.** `"Icon("`을 부분 문자열로 찾으면
         *    `CfIcon(`이 걸린다(실제로 그렇게 썼고, 정상 호출부 3곳이 위반으로 나왔다).
         *    코틀린 `\b`는 `Cf` 뒤에서 경계로 보지 않으므로 **식별자 문자가 아닌 것**을
         *    직접 요구한다. 줄 시작도 허용해야 한다(`Icon(`이 첫 토큰인 줄).
         *
         * ⚠️ [예약된_검사가_CfIcon을_잡지_않는다]가 이 경계를 대조군으로 센다.
         *    이 상수를 고치면 그 테스트가 먼저 빨개진다.
         */
        val MATERIAL_ICON_CALL = Regex("""(^|[^A-Za-z0-9_])Icon\s*\(""", RegexOption.MULTILINE)

        val densityDirs: Map<String, File> by lazy {
            listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").associateWith {
                File("src/main/res/drawable-$it").also { d ->
                    check(d.isDirectory) { "$d 가 없다 — 아이콘 생성 스크립트를 돌린다" }
                }
            }
        }

        val mipmapDirs: Map<String, File> by lazy {
            listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").associateWith {
                File("src/main/res/mipmap-$it").also { d ->
                    check(d.isDirectory) { "$d 가 없다 — 런처 아이콘 스크립트를 돌린다" }
                }
            }
        }

        val mainSources: List<File> by lazy {
            File("src/main/java/com/catchflower/app")
                .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

        /**
         * 주석을 뺀 본문. **KDoc이 증거로 세어지면 안 된다** — 이 파일들의 주석에는
         * `tint`·`else ->`가 "쓰지 말라"는 설명으로 여러 번 나온다.
         * 같은 함정을 이 저장소에서 이미 세 번 밟았다.
         */
        fun bodyOf(file: File): String =
            file.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
    }
}
