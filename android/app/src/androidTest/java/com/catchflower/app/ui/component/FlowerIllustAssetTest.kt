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
 * 꽃 일러스트가 **APK 안에 실제로 있고 디코딩되는가.**
 *
 * ⚠️ **화면으로는 절대 못 잡는다.** 파일이 없으면 [FlowerIllust]가 플레이스홀더로
 *    되돌리므로 **예외도 안 나고 빈 칸도 안 생긴다** — 그냥 "아트가 아직 안 온 종"처럼
 *    보인다. 몇 칸이 그런지 눈으로 세는 것은 불가능하다.
 *
 * ⚠️ **JVM 테스트로는 할 수 없다.** `BitmapFactory`도 `assets`도 android.jar에서는
 *    껍데기다. 실제 APK에 패키징된 것을 확인해야 의미가 있으므로 계측 테스트다.
 *
 * 🔴 **"일러스트 개수 == 종 수"는 더 이상 단정할 수 없다** (계약 1-5).
 *    도감은 2,057종이고 납품된 그림은 200장이다 — 없는 것이 정상 상태다.
 *    그래서 검사 축을 뒤집었다: **assets에 실제로 들어간 파일 목록**을 기준으로
 *    (a) 목록이 비거나 줄지 않았는가 (b) 파일과 [Flower.illustAssetName]의 규칙이
 *    같은가 (c) 있는 파일이 규격대로인가 를 본다.
 *    (b)가 이 파일의 새 핵심이다 — 파일명 자릿수를 한쪽만 고치면 200장이 **전부**
 *    안 나오는데, 화면에서는 "아직 안 온 그림"과 **완전히 같아 보인다.**
 */
@RunWith(AndroidJUnit4::class)
class FlowerIllustAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** APK에 들어간 일러스트 파일명. `SyncSharedAssets`가 번호만 남겨 복사한 결과다. */
    private fun assetNames(): List<String> =
        context.assets.list("flower_illust").orEmpty().sorted()

    private companion object {
        /**
         * 지금까지 납품된 장수. **리터럴이다.**
         *
         * ⚠️ [GamePolicy.TOTAL_FLOWER_COUNT]를 쓰면 안 된다 — 종수를 2,057로 올리는
         *    순간 이 단정이 **따라 움직여서** 아무것도 빨개지지 않는다(이미 한 번
         *    당했다: `PlantNetReplayTest`의 대조군 크기).
         *    그림이 더 오면 이 숫자를 **손으로** 올린다.
         */
        const val DELIVERED = 200
    }

    /**
     * 납품된 200장이 APK 안에 있고, **파일명 규칙이 코드와 같은가.**
     *
     * ⚠️ 여기서 "있는 것만 다 열렸다"로 쓰면 목록이 비어도 통과한다 —
     *    0개 중 0개 성공은 성공이 아니다. 그래서 [DELIVERED] 하한을 둔다.
     */
    @Test
    fun 납품된_일러스트가_APK에_들어있고_코드와_같은_이름이다() {
        val names = assetNames()
        assertTrue(
            "assets/flower_illust 에 파일이 ${names.size}개다 — ${DELIVERED}장 이상이어야 한다. " +
                "빌드의 SyncSharedAssets가 복사에 실패했거나 정규식이 원본 파일명과 안 맞는다",
            names.size >= DELIVERED,
        )

        // 🔴 파일명 규칙 대조. `Flower.illustAssetName`이 `%04d`인데 자산이 `001.png`면
        //    (또는 그 반대면) **한 장도 안 열린다.** 두 규칙이 한 군데서만 바뀌는 것이
        //    이 프로젝트에서 실제로 예고된 함정이라(계약 1-5) 여기서 직접 맞춰 본다.
        val flowers = FlowerRepository.get(context).flowers
        val expected = flowers.associateBy { it.illustAssetName.substringAfterLast('/') }
        val orphan = names.filterNot { it in expected }
        assertTrue(
            "코드가 찾지 않는 이름의 일러스트 ${orphan.size}개: ${orphan.take(10)} — " +
                "Flower.illustAssetName(%04d)과 SyncSharedAssets의 복사 이름이 어긋났거나, " +
                "도감에 없는 번호가 납품됐다(그 그림은 앱에서 영원히 안 보인다)",
            orphan.isEmpty(),
        )

        // 자산에 있는 종은 반드시 열려야 한다. (이름이 맞아도 패키징이 깨질 수 있다.)
        val unopenable = flowers.filter { it.illustAssetName.substringAfterLast('/') in names }
            .filter { runCatching { context.assets.open(it.illustAssetName).close() }.isFailure }
            .map { "${it.id} ${it.name}" }
        assertTrue("파일은 있는데 열리지 않는 종: ${unopenable.take(10)}", unopenable.isEmpty())
    }

    /**
     * 원본이 발주 규격(512×512 · 투명)대로 왔는가.
     *
     * ⚠️ **알파 채널이 핵심이다.** 배경이 불투명하면 도감 셀의 원형 배경 위에
     *    **흰 사각형이 얹혀 보인다.** 발주서는 투명을 요구했고 실측으로 확인했지만,
     *    남은 1,857종이 배치로 들어오므로 그때 규격이 어긋나는 것을 여기서 잡는다.
     *
     * ⚠️ **종을 도는 게 아니라 파일을 돈다.** 종을 돌면 없는 파일에서 `open`이 던져
     *    1,857종 앞에서 첫 칸부터 실패한다 — 그건 규격 위반이 아니라 미납품이다.
     */
    @Test
    fun 일러스트가_512픽셀_투명배경이다() {
        val names = assetNames()
        // 🔴 메시지가 "0장이다"였는데 조건은 `>= DELIVERED`다 — **199장에서도 "0장"이라고
        //    말했다**(돌연변이로 확인: 1장을 빼니 이 문구가 그대로 나왔다). 읽는 사람은
        //    자산이 통째로 안 들어간 줄 알고 `SyncSharedAssets`를 뒤지게 된다.
        //    실패 문구는 **실제 수를 말해야** 원인으로 바로 간다.
        assertTrue(
            "검사할 일러스트가 ${names.size}장이다 — ${DELIVERED}장 이상이어야 한다 " +
                "(0장이면 자산 자체가 안 들어간 것 · ${DELIVERED}장 미만이면 복사에서 빠진 것)",
            names.size >= DELIVERED,
        )

        val wrong = mutableListOf<String>()
        names.forEach { name ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("flower_illust/$name").use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth != 512 || options.outHeight != 512) {
                wrong += "$name ${options.outWidth}x${options.outHeight}"
            }
        }

        assertTrue("512x512가 아닌 일러스트 ${wrong.size}장: ${wrong.take(10)}", wrong.isEmpty())
    }

    /**
     * **그림 없는 종이 앱을 세우지 않는가.**
     *
     * 2,057종 중 1,857종은 파일이 없다. [FlowerIllustLoader]는 null을 돌려주고
     * 호출부가 플레이스홀더로 되돌려야 한다 — 여기서 예외가 나면 도감 스크롤이
     * 200번째 칸에서 죽는다.
     */
    @Test
    fun 그림_없는_종은_null을_돌려주고_죽지_않는다() {
        val names = assetNames().toSet()
        val withoutFile = FlowerRepository.get(context).flowers
            .filter { it.illustAssetName.substringAfterLast('/') !in names }

        // 없는 종이 하나도 없다면 이 테스트는 아무것도 재지 않는다. 그 상태를 드러낸다.
        Log.i("FlowerIllustAsset", "그림 없는 종 ${withoutFile.size}종 / 파일 ${names.size}장")

        withoutFile.take(50).forEach { flower ->
            assertEquals("${flower.id} ${flower.name}: 파일이 없는데 비트맵이 나왔다",
                null, FlowerIllustLoader.load(context, flower, 200))
        }
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
