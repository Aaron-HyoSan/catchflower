package com.catchflower.app.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **화면 00 로딩 화면이 진짜 일을 기다리는가** (2026-08-16).
 *
 * ## 무엇을 잡는가
 *
 * 🔴 로딩 화면은 **가짜여도 정상으로 보인다.** `delay(800)`만 두고 아무 일도 안 해도
 *    화면은 똑같고, 그러면 도감 2,057종 파싱이 **첫 화면의 메인 스레드에 그대로 남는다** —
 *    즉 시작이 0.8초 **더** 느려진다. 화면만 보면 그게 개선인지 손해인지 알 수 없다.
 *    그래서 여기서 재는 것은 그림이 아니라 **① 대기 규칙 ② 배선**이다.
 *
 * ## ⚠️ `@Composable`은 JVM에서 못 돈다
 *
 * 그림과 실제 프레임은 에뮬레이터로 눈으로 확인한다(진행.md). 이 검사가 막는 것은
 * **다음 사람이 데우기를 떼고 `delay`만 남기는 것**이다.
 */
class AppLoadingGateTest {

    // ── ① 대기 규칙 ────────────────────────────────────────────────

    /**
     * 하한값을 **리플렉션으로 읽는다.**
     *
     * 🔴 `const val`은 **호출부에 인라인**된다 — 상수를 그냥 참조해서 단정을 쓰면
     *    "무엇을 비교하는지"가 컴파일 시점에 사라진다(이 저장소에서 한 번 밟은 함정이다).
     *    정적 필드를 직접 읽고, **소스에 적힌 숫자와도 교차 확인**한다.
     */
    @Test
    fun 하한이_800ms다() {
        val field = AppLoadingGate::class.java.getDeclaredField("MIN_VISIBLE_MS")
        field.isAccessible = true
        assertEquals("MIN_VISIBLE_MS가 바뀌었다", 800L, field.get(null))

        val declared = Regex("""const val MIN_VISIBLE_MS = (\d+)L""")
            .find(source)?.groupValues?.get(1)
        assertEquals("소스에 적힌 값과 실제 값이 다르다", "800", declared)
    }

    /** 데우기가 늦게 끝났으면 **더 기다리지 않는다.** */
    @Test
    fun 하한을_넘겼으면_0이다() {
        assertEquals(0L, AppLoadingGate.remainingMs(800L))
        assertEquals(0L, AppLoadingGate.remainingMs(801L))
        assertEquals(0L, AppLoadingGate.remainingMs(5_000L))
    }

    /** 빠른 기기에서는 남은 만큼 더 보여준다 — 로고가 한 프레임 깜빡이고 사라지지 않게. */
    @Test
    fun 하한에_못_미치면_남은_만큼_기다린다() {
        assertEquals(800L, AppLoadingGate.remainingMs(0L))
        assertEquals(680L, AppLoadingGate.remainingMs(120L))
        assertEquals(1L, AppLoadingGate.remainingMs(799L))
    }

    /**
     * **음수를 돌려주지 않는다.**
     *
     * ⚠️ `delay(-n)`은 예외가 아니라 **0과 같다** — 즉 호출부에서는 아무 증상이 없고,
     *    실수가 조용히 삼켜진다. 시계가 뒤로 갈 수 있는 경로(`System.currentTimeMillis`)라
     *    음수 elapsed가 불가능하지도 않다.
     */
    @Test
    fun 음수를_돌려주지_않는다() {
        assertTrue("음수가 나왔다", AppLoadingGate.remainingMs(10_000L) >= 0L)
        // 시계가 뒤로 간 경우는 **더 기다리는 쪽**으로 답한다. 잘라내지 않는다 —
        // 그런 값이 들어왔다는 것 자체가 신호이고, 여기서 감추면 원인이 사라진다.
        assertEquals(1_300L, AppLoadingGate.remainingMs(-500L))
    }

    // ── ② 배선 ────────────────────────────────────────────────────

    /**
     * 로딩 화면이 **도감을 실제로 데운다** — 그리고 그것을 **IO 스레드에서** 한다.
     *
     * 🔴 `withContext(Dispatchers.IO)`가 빠지면 파싱이 **메인 스레드**로 돌아온다.
     *    화면은 똑같이 보이고(로고가 떠 있다) 다만 그 동안 UI가 얼어 있다 —
     *    로딩 화면에서는 **얼어 있는 것과 기다리는 것이 구별되지 않는다.**
     */
    @Test
    fun 로딩_화면이_도감을_IO에서_데운다() {
        assertTrue("MainActivity를 못 읽었다", mainActivity.length > 1_000)
        assertTrue(
            "로딩 화면을 안 띄운다",
            mainActivity.contains("AppLoadingScreen("),
        )
        assertTrue(
            "도감 데우기가 withContext(Dispatchers.IO) 안에 없다 — 메인 스레드에서 파싱한다",
            Regex("""withContext\(Dispatchers\.IO\)\s*\{[\s\S]{0,200}?FlowerRepository\.get\(""")
                .containsMatchIn(mainActivity),
        )
    }

    /**
     * 하한을 **[AppLoadingGate]로 계산한다** — 숫자를 화면에 박지 않는다.
     *
     * 🔴 `delay(800)`이라고 쓰면 이 파일의 단정들이 **아무것도 지키지 않게 된다**
     *    (규칙이 검사 밖으로 나간다). 그리고 그때도 화면은 똑같다.
     */
    @Test
    fun 대기_시간을_게이트가_정한다() {
        assertTrue(
            "delay에 숫자를 직접 박았다 — AppLoadingGate.remainingMs를 써야 한다",
            mainActivity.contains("delay(AppLoadingGate.remainingMs("),
        )
        assertTrue(
            "MainActivity에 상수 delay가 들어왔다",
            !Regex("""delay\(\s*\d""").containsMatchIn(mainActivity),
        )
    }

    /**
     * 데우기 실패를 **삼키지 않는다.**
     *
     * 🔴 `try/catch`로 감싸면 도감 자산이 깨졌을 때 **로고가 영원히 떠 있는 화면**이 된다 —
     *    사용자에게는 멈춘 앱이고 크래시도 로그도 없어서 원인을 알 방법이 없다.
     *    지금은 `DexViewModel`에서 죽던 것이 여기서 죽는다(시점만 앞으로 온다).
     */
    @Test
    fun 데우기_실패를_삼키지_않는다() {
        val warmup = Regex("""LaunchedEffect\(Unit\)\s*\{[\s\S]{0,600}?loadingDone = true""")
            .find(mainActivity)?.value
        assertTrue("데우기 블록을 못 찾았다 — 검사가 비어 있다", warmup != null)
        assertTrue(
            "데우기를 try/catch로 감쌌다 — 실패하면 로고가 영원히 떠 있는다",
            !warmup!!.contains("try"),
        )
    }

    // ── ③ 미발견 셀은 **면으로 채운다** (오너 결정 (b) · 2026-08-16) ──

    /**
     * 🔴 **`Stroke`로 그리면 안 보인다.** 처음엔 `Stroke(width = 1.5f)`였는데 Canvas 안의
     *    그 숫자는 **dp가 아니라 픽셀**이다 — 3x 기기에서 0.5dp로 그려져 흰 배경에서
     *    사실상 사라지고, 셀은 **빈 칸**으로 읽혔다("회색 처리"의 반대다).
     *
     * ⚠️ **에뮬레이터 스크린샷으로는 이 결함이 잘 안 보인다** — 캡처를 확대하면 회색이
     *    한 줄 찍혀 있어서 "그려지고 있다"로 읽힌다. 그래서 값이 아니라 **그리는 방식**을
     *    소스에서 고정한다.
     */
    @Test
    fun 미발견_실루엣은_면으로_채운다() {
        val body = bodyOf(illustFile)
        val silhouette = body.substringAfter("fun FlowerSilhouette(", "")
        assertTrue("FlowerSilhouette를 못 찾았다 — 검사가 비어 있다", silhouette.length > 200)

        val onlyThisFunction = silhouette.substringBefore("\nprivate val SilhouetteGray")
        assertTrue(
            "미발견 실루엣을 테두리(Stroke)로 그린다 — 3x 기기에서 0.5dp가 되어 안 보인다",
            !onlyThisFunction.contains("Stroke("),
        )
        assertTrue("회색 꽃잎 색을 안 쓴다", onlyThisFunction.contains("SilhouetteGray"))
        assertTrue("꽃심 색을 안 쓴다", onlyThisFunction.contains("SilhouetteGrayCenter"))

        // 실제 일러스트를 회색으로 덮는 (a)안이 아니다 — 꽃 모양이 새면 안 된다.
        assertTrue(
            "미발견 셀이 실제 일러스트를 그린다 — 모양이 새어 (a)안이 된다",
            !onlyThisFunction.contains("FlowerIllust("),
        )
    }

    /** 실루엣 회색이 **발견한 셀보다 눈에 띄지 않는** 아주 연한 값인지. */
    @Test
    fun 실루엣_회색이_연한_값이다() {
        val body = illustFile.readText()
        assertTrue("꽃잎 회색이 바뀌었다", body.contains("SilhouetteGray = Color(0xFFD9D9D9)"))
        assertTrue("꽃심 회색이 바뀌었다", body.contains("SilhouetteGrayCenter = Color(0xFFC4C4C4)"))
    }

    // ── ④ 배지가 **시스템 스플래시와 같은 크기·자리**인가 ──────────────

    /**
     * 배지 크기가 **코드와 실제 PNG에서 같다.**
     *
     * 🔴 **여기서 실제로 틀렸다(2026-08-16).** 배지 PNG는 108dp 캔버스로 생성돼
     *    있었는데 시스템 스플래시는 원을 **160dp**로 그린다 — 실행할 때마다 로고가
     *    1.48배 줄어들며 깜빡였다(에뮬레이터 실측 420px → 284px). **빌드·테스트·
     *    화면 스크린샷 전부 정상이었다** — 두 장 다 "흰 배경에 로고"라서 한 장씩
     *    보면 틀린 것을 알 수 없고, 프레임을 나란히 재야 보인다.
     *
     * ## 왜 PNG 픽셀을 읽는가
     *
     * 크기의 원본은 생성 스크립트(`build_android_launcher.py`의 `BADGE_DP`)지만
     * 그 파일을 읽지 않는다 — ⚠️ 경로에 한글이 있어서 macOS(NFD)와 자바 문자열(NFC)이
     * 어긋날 수 있고(이 저장소가 밟은 함정), 무엇보다 **스크립트를 고치고 안 돌린
     * 상태**를 잡아야 한다. 그래서 **실제로 앱에 들어가는 PNG**를 잰다.
     *
     * ⚠️ `Modifier.size`만 키우는 것도 여기서 빨개진다 — 그건 늘려서 흐려진 그림이다.
     */
    @Test
    fun 배지_크기가_코드와_PNG에서_같다() {
        val declared = Regex("""private val BadgeSize = (\d+)\.dp""")
            .find(source)?.groupValues?.get(1)?.toInt()
        assertTrue("BadgeSize 선언을 못 찾았다 — 이 검사가 비었다", declared != null)
        assertEquals(
            "배지 dp가 바뀌었다 — 시스템 스플래시가 그리는 원은 실측 160dp다",
            160,
            declared,
        )

        // mdpi=1x … xxxhdpi=4x. PNG가 그 배율로 생성돼 있어야 한다.
        mapOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0, "xxxhdpi" to 4.0)
            .forEach { (density, scale) ->
                val png = File("src/main/res/mipmap-$density/ic_splash_badge.png")
                assertTrue("mipmap-$density/ic_splash_badge.png 이 없다", png.exists())
                assertEquals(
                    "mipmap-$density 배지가 ${declared}dp가 아니다 — 생성 스크립트(BADGE_DP)를 " +
                        "고치고 돌리지 않았나",
                    Math.round(declared!! * scale).toInt(),
                    pngWidth(png),
                )
            }
    }

    /**
     * 배지 **중심이 화면 정중앙**에 온다 — 시스템이 로고를 그리는 자리와 같다.
     *
     * 🔴 크기를 맞춰도 자리가 다르면 로고가 **튄다**(실측 138px = 53dp 위로).
     *    로고·이름·스피너를 한 열로 묶어 가운데 정렬하면 **열의 중심**이 정중앙이라
     *    로고는 반드시 위로 올라간다 — 즉 "가운데 정렬했으니 맞다"가 틀린다.
     *
     * ⚠️ **이 검사는 그림을 못 본다.** 진짜 증거는 실행 프레임을 픽셀로 잰 것이고
     *    (진행.md), 여기서 막는 것은 **다음 사람이 반쪽 구조를 되돌려 한 열로
     *    묶는 것**이다. 그러면 화면은 여전히 "가운데 정렬된 로딩 화면"으로 보인다.
     */
    @Test
    fun 배지가_화면_정중앙에_온다() {
        val body = bodyOf(File("src/main/java/com/catchflower/app/ui/component/AppLoadingScreen.kt"))
        assertEquals(
            "위/아래 반쪽 구조가 아니다 — 배지가 화면 중심에서 위로 올라간다",
            2,
            Regex("""weight\(1f\)""").findAll(body).count(),
        )
        assertTrue(
            "배지를 반지름만큼 내리지 않는다 — 아래끝이 화면 중심에 붙는다",
            body.contains("offset(y = BadgeSize / 2)"),
        )
        assertTrue(
            "배지가 아래쪽 정렬이 아니다 — offset 계산의 전제가 깨진다",
            body.contains("Alignment.BottomCenter"),
        )
    }

    private companion object {
        /**
         * PNG 폭을 **헤더에서** 읽는다(IHDR: 16~19바이트 빅엔디언).
         *
         * ⚠️ JVM 단위테스트에는 안드로이드 `BitmapFactory`가 없고, 파일 크기(바이트)로는
         *    해상도를 알 수 없다 — 압축률이 그림에 따라 달라서 **바뀐 것을 못 잡는다**.
         */
        fun pngWidth(file: File): Int {
            val head = file.readBytes()
            check(head.size > 24) { "$file 이 PNG가 아니다" }
            return (0..3).fold(0) { acc, i -> (acc shl 8) or (head[16 + i].toInt() and 0xFF) }
        }

        val source: String by lazy {
            File("src/main/java/com/catchflower/app/ui/component/AppLoadingScreen.kt").readText()
        }

        val mainActivity: String by lazy {
            bodyOf(File("src/main/java/com/catchflower/app/MainActivity.kt"))
        }

        val illustFile = File("src/main/java/com/catchflower/app/ui/component/FlowerIllust.kt")

        /**
         * 주석을 뺀 본문.
         *
         * ⚠️ **KDoc이 증거로 세어지면 안 된다.** 이 파일들의 주석에는 `Stroke(width = 1.5f)`·
         *    `delay(800)`이 **"쓰지 말라"는 설명으로** 적혀 있다 — 걷어내지 않으면
         *    고쳐 놓은 코드가 위반으로 잡힌다([DiscoveryPhotoSourceTest]에서 같은 함정을 밟았다).
         */
        fun bodyOf(file: File): String =
            file.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
    }
}
