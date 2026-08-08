package com.catchflower.app.ui.component

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.data.FlowerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 꽃 일러스트 200장이 **APK 안에 실제로 있고 디코딩되는가.**
 *
 * ⚠️ **화면으로는 절대 못 잡는다.** 파일이 없으면 [FlowerIllust]가 플레이스홀더로
 *    되돌리므로 **예외도 안 나고 빈 칸도 안 생긴다** — 그냥 "아트가 아직 안 온 종"처럼
 *    보인다. 200칸 중 몇 개가 그런지 눈으로 세는 것은 불가능하다.
 *
 * ⚠️ **JVM 테스트로는 할 수 없다.** `BitmapFactory`도 `assets`도 android.jar에서는
 *    껍데기다. 실제 APK에 패키징된 것을 확인해야 의미가 있으므로 계측 테스트다.
 */
@RunWith(AndroidJUnit4::class)
class FlowerIllustAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 200종 전부 파일이 있는가.
     *
     * ⚠️ 개수를 [GamePolicy.TOTAL_FLOWER_COUNT]로 단정한다. "있는 것만 다 열렸다"로
     *    쓰면 목록이 비어도 통과한다 — 0개 중 0개 성공은 성공이 아니다.
     */
    @Test
    fun 도감_200종의_일러스트가_모두_있다() {
        val flowers = FlowerRepository.get(context).flowers
        assertEquals(
            "도감 종 수가 정책 상수와 다르다",
            GamePolicy.TOTAL_FLOWER_COUNT,
            flowers.size,
        )

        val missing = flowers.filter { flower ->
            runCatching { context.assets.open(flower.illustAssetName).close() }.isFailure
        }.map { "${it.id} ${it.name}" }

        assertTrue(
            "일러스트 없는 종 ${missing.size}개: ${missing.take(20)}",
            missing.isEmpty(),
        )
    }

    /**
     * 원본이 발주 규격(512×512 · 투명)대로 왔는가.
     *
     * ⚠️ **알파 채널이 핵심이다.** 배경이 불투명하면 도감 셀의 원형 배경 위에
     *    **흰 사각형이 얹혀 보인다.** 발주서는 투명을 요구했고 실측으로 확인했지만,
     *    배치 3(65종)이 나중에 들어오므로 그때 규격이 어긋나는 것을 여기서 잡는다.
     */
    @Test
    fun 일러스트가_512픽셀_투명배경이다() {
        val flowers = FlowerRepository.get(context).flowers
        val wrong = mutableListOf<String>()

        flowers.forEach { flower ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(flower.illustAssetName).use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth != 512 || options.outHeight != 512) {
                wrong += "${flower.id} ${flower.name} ${options.outWidth}x${options.outHeight}"
            }
        }

        assertTrue("512x512가 아닌 일러스트: ${wrong.take(10)}", wrong.isEmpty())
    }

    /**
     * **`inSampleSize`가 실제로 먹는가.**
     *
     * ⚠️ 이게 이 파일에서 가장 중요한 단정이다. 축소가 안 되면 한 장이 512×512×4 =
     *    **1MB**이고 200종이면 **200MB**다. 그런데 **화면은 똑같이 보인다** —
     *    작게 그리니까. 메모리만 16배 쓰고 저가형에서 OOM으로 죽는다.
     *    `inSampleSize`는 2의 거듭제곱이 아니면 조용히 내림되므로 버킷을 맞춰 뒀다.
     */
    @Test
    fun 요청한_크기로_축소해서_디코딩한다() {
        val flower = FlowerRepository.get(context).byId(1)!!

        // 도감 셀 크기(66dp)를 3x 기기 픽셀로 환산한 값 근처.
        val small = FlowerIllustLoader.load(context, flower, 200)!!
        assertEquals("버킷 256으로 디코딩되어야 한다", 256, small.width)
        assertTrue("한 장이 512KB를 넘으면 축소가 안 된 것이다", small.byteCount <= 256 * 256 * 4)

        FlowerIllustLoader.clear()
        val large = FlowerIllustLoader.load(context, flower, 512)!!
        assertEquals(512, large.width)

        // 알파를 유지해야 한다. RGB_565로 떨어지면 투명 배경이 검게 칠해진다.
        assertTrue("알파 채널이 없다 — 투명 배경이 검게 나온다", large.hasAlpha())
    }

    /**
     * 버킷 경계. [FlowerIllustLoader.sampleBucket]이 512의 약수를 돌려주지 않으면
     * `inSampleSize`가 내림되어 **캐시 키와 실제 크기가 어긋난다.**
     */
    @Test
    fun 버킷은_항상_512의_약수다() {
        listOf(1, 63, 64, 65, 127, 128, 129, 255, 256, 257, 511, 512, 2000).forEach { req ->
            val bucket = FlowerIllustLoader.sampleBucket(req)
            assertEquals("$req → $bucket 은 512를 나누지 못한다", 0, 512 % bucket)
            assertTrue("$req → $bucket 이 요청보다 작다", bucket >= minOf(req, 512))
        }
    }

    /**
     * **메인 스레드 디코딩 비용을 실측한다.**
     *
     * 에뮬레이터에서 스크롤 중 `Skipped 82 frames`를 봤다. 디코딩은 컴포지션 중에
     * (= 메인 스레드에서) 일어나므로 여기가 원인 후보다. 시간 단정은 기기 성능에
     * 따라 흔들리므로 **로그로 남기고 상한만 느슨하게 둔다** — 목적은 회귀 감지가
     * 아니라 **숫자를 기록에 남기는 것**이다.
     */
    @Test
    fun 디코딩_비용을_측정한다() {
        val flowers = FlowerRepository.get(context).flowers.take(30)

        FlowerIllustLoader.clear()
        val coldNs = System.nanoTime().let { start ->
            flowers.forEach { FlowerIllustLoader.load(context, it, 200) }
            System.nanoTime() - start
        }

        // 두 번째는 캐시에서 나와야 한다.
        val warmNs = System.nanoTime().let { start ->
            flowers.forEach { FlowerIllustLoader.load(context, it, 200) }
            System.nanoTime() - start
        }

        val coldPerMs = coldNs / flowers.size / 1_000_000.0
        val warmPerMs = warmNs / flowers.size / 1_000_000.0
        Log.i(
            "FlowerIllustPerf",
            "디코딩 %d장: 첫 로딩 %.2fms/장 · 캐시 %.3fms/장".format(flowers.size, coldPerMs, warmPerMs),
        )

        // 캐시가 실제로 듣는가. 안 들으면 스크롤마다 디코딩한다.
        assertTrue(
            "캐시가 첫 로딩보다 빠르지 않다 — 캐시가 안 듣는다 (cold %.2f / warm %.2f ms)"
                .format(coldPerMs, warmPerMs),
            warmNs < coldNs / 2,
        )
    }
}
