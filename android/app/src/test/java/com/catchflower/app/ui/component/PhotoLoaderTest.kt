package com.catchflower.app.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoLoader.sampleSize]의 계약: **요청보다 작지 않게, 그러나 2배 미만으로.**
 *
 * ## 왜 이걸 재는가
 *
 * 이 함수가 틀리면 **사진은 정상으로 보인다.** 너무 작게 잡으면(sample이 크면)
 * 흐려지는데 52dp 썸네일에서는 눈치채기 어렵고, 너무 크게 잡으면(sample이 작으면)
 * 완벽하게 선명한 채로 **메모리를 4배** 쓴다 — 죽을 때는 사진과 무관한 자리에서 죽는다.
 * 즉 화면·스크린샷으로는 **어느 방향으로 틀렸는지조차** 알 수 없다.
 *
 * ⚠️ **`PhotoLoader.load`는 여기서 못 잰다.** `BitmapFactory`·`LruCache`·`Log`는
 *    android.jar 껍데기라 JVM에서 던진다. 이 파일이 재는 것은 **순수 계산 하나**이고,
 *    디코딩·캐시·OOM 처리는 **기기에서만** 확인된다
 *    (`구현현황_AOS.md` §5의 "못 재는 층"에 같이 적었다).
 *
 * ⚠️ 그래서 [사진_로더가_순수_계산만_JVM에서_검증된다]로 **범위를 문서화**한다 —
 *    이 파일이 초록인 것을 "사진 표시가 검증됐다"로 읽으면 안 된다.
 */
class PhotoLoaderTest {

    /**
     * 계약을 **범위로 훑는다.** 개별 사례 몇 개를 박으면 그 사례만 맞는 구현
     * (예: 표를 하드코딩한 것)도 통과한다.
     */
    @Test
    fun 짧은_변이_요청과_요청2배_사이에_들어온다() {
        val reqs = listOf(1, 13, 52, 72, 156, 512)
        // 정사각(촬영 저장 형식) · 세로로 긴 것 · 가로로 긴 것을 섞는다.
        val sizes = listOf(
            64 to 64, 104 to 104, 110 to 110, 512 to 512, 1600 to 1600,
            800 to 1600, 1600 to 800, 1200 to 1600, 3000 to 1000,
        )
        var checked = 0
        for (req in reqs) {
            for ((w, h) in sizes) {
                val sample = PhotoLoader.sampleSize(w, h, req)
                assertTrue(
                    "sample은 2의 거듭제곱이어야 한다: ${w}x$h@$req -> $sample",
                    sample > 0 && (sample and (sample - 1)) == 0,
                )
                val short = minOf(w, h)
                val decoded = short / sample
                // 원본이 요청보다 작으면 더 줄일 수 없다 — sample은 1이고
                // 결과가 요청보다 작은 것이 정상이다.
                if (short >= req) {
                    assertTrue(
                        "요청보다 작아졌다(흐려진다): ${w}x$h@$req -> sample=$sample, 짧은변=$decoded",
                        decoded >= req,
                    )
                } else {
                    assertEquals("원본이 요청보다 작으면 줄이지 않는다: ${w}x$h@$req", 1, sample)
                }
                assertTrue(
                    "필요한 것의 2배 이상을 읽는다(메모리 4배): ${w}x$h@$req -> sample=$sample, 짧은변=$decoded",
                    decoded < req * 2 || sample == 1,
                )
                checked++
            }
        }
        // 루프가 0바퀴를 돌고 통과하지 않는지 센다.
        assertEquals(reqs.size * sizes.size, checked)
        assertTrue("표본이 너무 적다", checked >= 40)
    }

    /**
     * 🔴 **`>` 대신 `>=`인 이유가 여기 박혀 있다.**
     *
     * `104px` 사진을 `52dp` 칸에 넣을 때가 갈리는 지점이다 — `>`로 쓰면 반쪽(52)이
     * 요청과 **같아서** 줄이지 않고 sample=1로 104px을 읽는다(픽셀 4배).
     * `>=`는 정확히 52에서 멈춘다. **요청보다 작아지지는 않는다** — 그게 처음 주석에
     * 적었던 걱정이었고 틀렸다.
     *
     * 위의 범위 테스트도 이 사례를 포함하지만, **경계 하나를 따로 박아 두는 것**이
     * 나중에 이 줄을 되돌리려는 사람에게 이유를 보여준다.
     */
    @Test
    fun 정확히_2배인_경계에서_한_단계_줄인다() {
        assertEquals(2, PhotoLoader.sampleSize(104, 104, 52))
        assertEquals(52, 104 / PhotoLoader.sampleSize(104, 104, 52))
        // 2배에서 1px 모자라면 줄이지 않는다(줄이면 51px < 52px로 흐려진다).
        assertEquals(1, PhotoLoader.sampleSize(103, 103, 52))
    }

    /**
     * 1600px 정사각(실제 저장 형식, [com.catchflower.app.core.GamePolicy] `PHOTO_LONG_EDGE_PX`)이
     * 52dp 썸네일에서 얼마나 줄어드는지를 **숫자로** 박는다.
     *
     * 3x 기기 기준 156px 요청 → sample 8 → 200px → RGB_565로 **약 78KB**다.
     * 원본을 그대로 읽으면 `1600×1600×4 = 10MB`였다. 목록 10칸이면 **100MB vs 0.8MB**.
     *
     * ⚠️ 1600은 2의 거듭제곱이 아니라(`2^10 = 1024`, `2^11 = 2048`) 줄어드는 자리가
     *    `800 · 400 · 200 · 100`이다. 그래서 **104px 요청과 156px 요청이 같은 200px에
     *    떨어진다** — 처음에 이 값을 16으로 적었는데(100px) 테스트가 잡았다.
     *    2의 거듭제곱만 고를 수 있으니 요청 크기를 조금 바꿔도 결과가 안 변하는
     *    구간이 있는 게 정상이다.
     */
    @Test
    fun 저장된_1600px_사진이_썸네일_크기로_줄어든다() {
        assertEquals(8, PhotoLoader.sampleSize(1600, 1600, 156)) // 52dp @3x -> 200px
        assertEquals(8, PhotoLoader.sampleSize(1600, 1600, 104)) // 52dp @2x -> 200px
        assertEquals(4, PhotoLoader.sampleSize(1600, 1600, 216)) // 72dp @3x -> 400px
        assertEquals(16, PhotoLoader.sampleSize(1600, 1600, 100)) // 100px 요청은 100px
    }

    /**
     * 크기를 아직 모르는 순간(측정 전 첫 컴포지션)에 `0`이 들어온다.
     *
     * ⚠️ 여기서 나눗셈을 하면 **`ArithmeticException`으로 화면이 죽는다** —
     *    사진이 문제가 아니라 도감 상세가 아예 안 열린다.
     */
    @Test
    fun 요청_크기가_0이하면_원본_크기로_읽는다() {
        assertEquals(1, PhotoLoader.sampleSize(1600, 1600, 0))
        assertEquals(1, PhotoLoader.sampleSize(1600, 1600, -1))
    }

    /**
     * **이 파일이 무엇을 재지 않는지**를 소스로 확인한다.
     *
     * 🔴 `PhotoLoaderTest`가 초록인 것은 "사진이 화면에 나온다"의 증거가 아니다.
     *    [PhotoLoader.load]는 `BitmapFactory`·`LruCache`·`Log`를 부르고,
     *    그 셋은 JVM 테스트에서 **던지거나 아무 일도 하지 않는다.**
     *    누군가 `load`의 계산을 늘리면(예: 캐시 키 규칙 변경) 이 파일은 그것을
     *    **한 줄도 재지 못하는데 계속 초록이다.** 그래서 안드로이드 의존이 실제로
     *    거기 있다는 것과, 순수 함수가 그 밖에 있다는 것을 단정해 둔다.
     */
    @Test
    fun 사진_로더가_순수_계산만_JVM에서_검증된다() {
        val source = File("src/main/java/com/catchflower/app/ui/component/PhotoLoader.kt")
        assertTrue("소스를 못 찾았다: ${source.absolutePath}", source.exists())
        // 🔴 **주석을 뺀 본문만 읽는다.** 처음엔 `readText()` 전체를 봤는데,
        //    `short > 1`을 루프에서 지우는 돌연변이가 **살아남았다** — 내가 그 조건을
        //    **왜 뒀는지 KDoc에 적었기 때문에** 낱말이 문서 안에 그대로 있었다.
        //    즉 **설명한 것이 지킨 것으로 계산됐다.**
        //    (`진행.md` (38)에서 A 문서 전체 `contains`가 같은 이유로 무력화됐다.
        //     같은 함정을 **같은 세션에 두 번** 밟았다.)
        val text = bodyOf(source)

        // load는 안드로이드 밖에서 돌 수 없다 — 그래서 여기서 안 부른다.
        assertTrue("load가 BitmapFactory를 안 쓴다면 이 테스트의 전제가 바뀐 것이다",
            text.contains("BitmapFactory.decodeFile"))
        assertTrue("캐시가 LruCache가 아니게 바뀌었다", text.contains("LruCache"))

        // 🔴 **여기가 값으로는 안 잡히는 자리다.** `RGB_565` -> `ARGB_8888` 돌연변이는
        //    이 파일의 다른 테스트 4개를 **전부 통과했다** — 픽셀 값이 같고 계산도
        //    같으니 JVM에서는 차이가 없다. 차이는 **메모리 2배**뿐이고, 그건
        //    이 클래스가 존재하는 이유 그 자체다. 그래서 소스로 못 박는다.
        assertTrue(
            "사진 디코딩이 RGB_565가 아니면 메모리가 2배다(화면은 똑같이 보인다)",
            text.contains("Bitmap.Config.RGB_565"),
        )
        assertTrue(
            "경계 디코딩(inJustDecodeBounds) 없이 크기를 알 수 없다",
            text.contains("inJustDecodeBounds = true"),
        )
        // 무한 루프 안전장치. 가드(`reqSizePx <= 0`)가 지워졌을 때 **멈추게** 하는 것이
        // 이 조건이다 — 없으면 테스트가 빨개지는 대신 타임아웃까지 돈다(실제로 그랬다).
        assertTrue(
            "루프에 `short > 1`이 없으면 요청 0에서 멈추지 않는다",
            text.contains("short > 1"),
        )

        // sampleSize는 반대로 안드로이드 타입을 **하나도** 만지지 않아야 JVM에서 잴 수 있다.
        val body = text.substringAfter("internal fun sampleSize").substringBefore("\n    }")
        for (forbidden in listOf("Bitmap", "BitmapFactory", "Log", "LruCache", "Context")) {
            assertTrue(
                "sampleSize가 안드로이드 타입 `$forbidden`을 만지면 JVM에서 못 잰다",
                !body.contains(forbidden),
            )
        }
    }

    /**
     * 위 테스트가 **주석을 실제로 뺐는지** 직접 단정한다.
     *
     * ⚠️ [사진_로더가_순수_계산만_JVM에서_검증된다]가 `bodyOf`를 안 쓰게 되돌려지면
     *    그 테스트는 **계속 초록인 채로** 아무것도 안 지킨다(문서에 낱말이 있으니까).
     *    "미래에 누가 되돌리면 걸린다"는 검사는 지금 아무것도 지키지 않으므로
     *    **규칙 자체**를 여기서 잰다.
     */
    @Test
    fun 주석은_증거로_세지_않는다() {
        val stripped = bodyOf(File("src/main/java/com/catchflower/app/ui/component/PhotoLoader.kt"))
        // KDoc에는 `RGB_565`·`short > 1`이 설명으로 여러 번 나온다. 본문에는 한 번뿐이다.
        assertEquals(
            "주석이 안 벗겨졌다 — 설명이 구현으로 세어졌다",
            1,
            Regex("""short > 1""").findAll(stripped).count(),
        )
        assertTrue(
            "주석 제거가 코드까지 지웠다",
            stripped.contains("internal fun sampleSize") && stripped.contains("while (short > 1"),
        )
    }

    private companion object {
        /**
         * 블록 주석(`/* */`, KDoc 포함)과 줄 주석을 뺀 본문.
         * [com.catchflower.app.ui.DeadButtonTest]의 `bodyOf`와 같은 이유·같은 방식이다.
         */
        fun bodyOf(file: File): String =
            file.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
    }
}
