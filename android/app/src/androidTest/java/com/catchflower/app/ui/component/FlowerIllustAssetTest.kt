package com.catchflower.app.ui.component

import android.graphics.BitmapFactory
import android.os.Debug
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
 *    그래서 검사 축을 뒤집었다: **assets에 실제로 들어간 파일 목록**을 기준으로
 *    (a) 목록이 비거나 줄지 않았는가 (b) 파일과 [Flower.illustAssetName]의 규칙이
 *    같은가 (c) 있는 파일이 규격대로인가 를 본다.
 *    (b)가 이 파일의 새 핵심이다 — 파일명 규칙을 한쪽만 고치면 전량이
 *    안 나오는데, 화면에서는 "아직 안 온 그림"과 **완전히 같아 보인다.**
 *
 * 🔴 **2026-08-12: 전수 2,057장이 들어왔고 확장자가 `.webp`가 됐다.**
 *    PNG로는 130MB라 Play 업로드 상한(AAB 150MB · APK 직접 100MB)을 넘어
 *    **빌드 성공 후 업로드에서 막힌다**
 *    (`꽃도감/_tools/pack_illust_webp.py` 주석의 실측표). 그래서 이 파일이 재는 것이
 *    하나 늘었다: **WebP가 기기에서 실제로 디코딩되고 알파가 살아 있는가.**
 *    ⚠️ 투명 WebP는 API 18+다. `minSdk 26`이라 안전하지만, 알파가 죽으면
 *    도감 셀의 원형 배경 위에 **흰 사각형**이 얹히고 그건 규격 위반이다.
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
         * ⚠️ [GamePolicy.TOTAL_FLOWER_COUNT]를 쓰면 안 된다 — 종수가 바뀌는 순간
         *    이 단정이 **따라 움직여서** 아무것도 빨개지지 않는다(이미 한 번
         *    당했다: `PlantNetReplayTest`의 대조군 크기).
         *    그림이 더 오면 이 숫자를 **손으로** 올린다.
         *
         * 🔴 200 → 2057 (2026-08-12, 전수 납품). 이 숫자가 리터럴이라서
         *    **1,857장이 복사에서 빠지면 빨개진다** — 그게 이 값의 존재 이유다.
         *    ⚠️ 그리고 이 숫자를 올렸으므로 아래
         *    [그림_없는_종은_null을_돌려주고_죽지_않는다]는 **잴 대상이 0종**이 됐다.
         *    "0종을 다 통과했다"는 성공이 아니므로 그 테스트를 고쳤다(그 주석 참고).
         */
        const val DELIVERED = 2057

        /**
         * 실제 디코딩·알파 검사 표본 수.
         *
         * ⚠️ 전량을 디코딩하면 계측 테스트가 수 GB를 만들어 죽는다(한 장이
         *    512×512×4 = 1MB). 헤더 검사(`inJustDecodeBounds`)는 전량을 돌고,
         *    본문까지 만드는 것은 표본만 돈다.
         */
        const val ALPHA_SAMPLE = 60
    }

    /**
     * 납품된 전량(2,057장)이 APK 안에 있고, **파일명 규칙이 코드와 같은가.**
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
     *    **흰 사각형이 얹혀 보인다.** WebP는 `-alpha_q 100`으로 변환했지만, 변환
     *    설정이 바뀌거나 알파 없는 산출물이 섞이는 것을 여기서 잡는다.
     *
     * ⚠️ **종을 도는 게 아니라 파일을 돈다.** 종을 돌면 없는 파일에서 `open`이 던져
     *    첫 칸부터 실패한다 — 그건 규격 위반이 아니라 미납품이고, 다른 테스트의 일이다.
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

        // 🔴 **`inJustDecodeBounds`는 디코딩을 안 한다.** 위 반복문은 헤더만 읽으므로
        //    WebP 본문이 깨져 있어도 512×512라고 말한다. 그래서 실제로 픽셀까지 만든다.
        //    ⚠️ 전량을 원본 크기로 만들면 2,057 × 1MB = 2GB라 OOM이다. 표본만 돈다.
        val step = maxOf(1, names.size / ALPHA_SAMPLE)
        val opaque = mutableListOf<String>()
        val undecodable = mutableListOf<String>()
        names.filterIndexed { i, _ -> i % step == 0 }.forEach { name ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = context.assets.open("flower_illust/$name").use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (bmp == null) {
                // WebP를 못 읽는 기기라면 여기서 전부 걸린다(구형 기기 방어).
                undecodable += name
                return@forEach
            }
            // 알파가 없으면 셀의 원형 배경 위에 **흰 사각형**이 얹혀 보인다.
            if (!bmp.hasAlpha()) opaque += name
            bmp.recycle()
        }
        assertTrue(
            "디코딩이 안 되는 일러스트 ${undecodable.size}장: ${undecodable.take(10)} — " +
                "WebP 본문이 깨졌거나 이 기기가 투명 WebP를 못 읽는다(API 18+ 필요)",
            undecodable.isEmpty(),
        )
        assertTrue(
            "알파가 없는 일러스트 ${opaque.size}장: ${opaque.take(10)} — " +
                "셀의 원형 배경 위에 흰 사각형이 얹혀 보인다(alpha_q 100으로 다시 변환한다)",
            opaque.isEmpty(),
        )
    }

    /**
     * **그림 없는 종이 앱을 세우지 않는가.**
     *
     * [FlowerIllustLoader]는 null을 돌려주고 호출부가 플레이스홀더로 되돌려야 한다 —
     * 여기서 예외가 나면 도감 스크롤이 그 칸에서 죽는다.
     *
     * 🔴 **2026-08-12: 이 테스트는 잴 대상이 0종이 됐다.** 전수 2,057장이 들어와서
     *    "파일이 없는 종"이 사라졌다. 그런데 반복문이 0바퀴를 돌면 **그대로 초록이다** —
     *    이 저장소가 세는 거짓 초록의 전형(`catchflower-green-tests-are-not-evidence`)이고,
     *    로그로 남기는 것은 **고친 기분만 준다**(아무도 안 읽는다).
     *
     *    그래서 축을 바꿨다: 도감에 **없는 번호**를 합성해서 넣는다. 그 경로는
     *    납품과 무관하게 항상 살아 있어야 한다 — 그림이 한 장 빠지거나, 앞으로 종이
     *    추가되어 다시 미납품 상태가 될 때 앱이 죽지 않는 것이 이 단정의 목적이다.
     */
    @Test
    fun 그림_없는_종은_null을_돌려주고_죽지_않는다() {
        val names = assetNames().toSet()
        val flowers = FlowerRepository.get(context).flowers

        // ① 실제로 파일이 없는 종이 있으면 그것을 먼저 잰다(과거 상태 · 앞으로 다시 올 상태).
        val withoutFile = flowers.filter { it.illustAssetName.substringAfterLast('/') !in names }
        withoutFile.take(50).forEach { flower ->
            assertEquals(
                "${flower.id} ${flower.name}: 파일이 없는데 비트맵이 나왔다",
                null, FlowerIllustLoader.load(context, flower, 200),
            )
        }

        // ② 🔴 **없는 종을 합성한다.** ①이 0종이어도 이 단정은 반드시 돈다.
        //    번호를 도감 밖(9999)으로 두면 자산이 있을 수 없다.
        val ghost = flowers.first().copy(id = 9999)
        assertTrue(
            "9999번 자산이 실제로 존재한다 — 대조군이 무효다: ${ghost.illustAssetName}",
            ghost.illustAssetName.substringAfterLast('/') !in names,
        )
        assertEquals(
            "없는 자산인데 비트맵이 나왔다 — 로더가 어딘가에서 대체 그림을 만들고 있다",
            null, FlowerIllustLoader.load(context, ghost, 200),
        )

        Log.i("FlowerIllustAsset", "그림 없는 종 ${withoutFile.size}종 / 파일 ${names.size}장")
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
     * **전 종을 읽어도 힙이 버티는가** (전수 납품 2026-08-12로 새로 생긴 위험).
     *
     * 실측 (에뮬레이터 API 36 · 힙 상한 192MB):
     *
     * | 캐시 상한 | 2,057장 순차 로딩 후 붙들고 있는 네이티브 |
     * |---|---|
     * | 힙의 1/8 (현행) | **48MB** — 3회 반복 전부 48 |
     * | `Int.MAX_VALUE` (돌연변이) | **521MB** → FAIL |
     *
     * ⚠️ 이 검사를 만들면서 **두 번 틀렸다.** 둘 다 초록이었다:
     *   1. 자바 힙(`Runtime.totalMemory`)으로 쟀다 → API 26+ 비트맵 픽셀은
     *      **네이티브**에 있어서 무제한 캐시도 18MB로 보였다(**못 재는 층**).
     *   2. GC 없이 네이티브를 쟀다 → evict된 픽셀이 아직 안 풀려서 정상인데도
     *      161·177·193MB로 **실행마다 흔들렸다**(회귀가 아니라 GC 타이밍을 재고 있었다).
     */
    @Test
    fun 전_종을_읽어도_힙이_터지지_않는다() {
        // 🔴 **전수 2,057장이 들어오면서 이 위험이 10배가 됐다.** 디스크에서는 46MB지만
        //    512×512 ARGB_8888로 **디코딩하면 한 장이 1MB** — 전 종을 캐시에 담으면 2GB다.
        //    캐시 상한(힙의 1/8)이 실제로 evict하는지 여기서 잰다.
        //
        // ⚠️ **`OutOfMemoryError`를 기다리는 방식으로 재지 않는다.** 그러면 통과할 때
        //    아무것도 재지 않고(그냥 안 죽었다), 실패할 때는 테스트 런너가 같이 죽어서
        //    **원인이 안 남는다.** 그래서 캐시가 **버렸는지**를 직접 본다.
        val flowers = FlowerRepository.get(context).flowers
        assertTrue("도감이 200종 이하다 — 전수 데이터가 아니다(${flowers.size}종)", flowers.size > 1000)

        // 🔴🔴 **`Runtime.totalMemory()`로 재면 이 검사는 아무것도 안 잰다.**
        //
        // 처음에 자바 힙으로 쟀고, **돌연변이(캐시 상한을 `Int.MAX_VALUE`로)가 초록이었다.**
        // 원인: **API 26부터 비트맵 픽셀은 네이티브 힙에 있다**(그 전에는 자바 힙).
        // 이 앱은 `minSdk 26`이므로 **전 기기에서** 자바 힙에는 비트맵이 안 보인다 —
        // 2,057장을 무제한으로 쌓아도 자바 힙은 18MB에서 안 움직였다(실측).
        //
        // ⚠️ 이게 이 저장소가 세는 **"못 재는 층"** 이다: 검사가 도는 것처럼 보이고
        //    숫자까지 로그에 남지만, 그 숫자가 **감시 대상과 다른 층**의 값이다.
        //    그래서 `Debug.getNativeHeapAllocatedSize()`로 잰다.
        // 🔴 **회수 전 픽셀까지 세면 상한을 지켜도 193MB가 나온다**(실측 161·177·193).
        //    API 26+에서 evict는 참조만 끊고, 네이티브 픽셀은 **GC가 돌 때** 풀린다
        //    (`NativeAllocationRegistry`). 즉 그 숫자는 "새는 양"이 아니라
        //    "아직 안 치운 양"이라 **실행마다 32MB씩 흔들린다** — 그런 값을 상한에 걸면
        //    검사가 진짜 회귀가 아닌 GC 타이밍으로 빨개진다.
        //
        //    그래서 **주기적으로 GC를 돌린 뒤의 값**을 본다 = 캐시가 붙들고 있는 양.
        //    ⚠️ `System.gc()`는 권고지만, 여기서는 대조군이 그것을 증명한다:
        //       캐시 상한을 `Int.MAX_VALUE`로 바꾸면 이 값이 521MB로 뛴다(실측).
        FlowerIllustLoader.clear()
        Runtime.getRuntime().gc()
        val baseNative = Debug.getNativeHeapAllocatedSize()
        var peakMb = 0L
        flowers.forEachIndexed { i, flower ->
            FlowerIllustLoader.load(context, flower, 200)
            if (i % 100 == 99) {
                Runtime.getRuntime().gc()
                val mb = (Debug.getNativeHeapAllocatedSize() - baseNative) / 1048576
                if (mb > peakMb) peakMb = mb
            }
        }
        Runtime.getRuntime().gc()
        ((Debug.getNativeHeapAllocatedSize() - baseNative) / 1048576).let {
            if (it > peakMb) peakMb = it
        }
        val limitMb = Runtime.getRuntime().maxMemory() / 1048576

        Log.i(
            "FlowerIllustPerf",
            "전 종 ${flowers.size}장 순차 로딩: 네이티브 증가 최대 ${peakMb}MB / 힙 상한 ${limitMb}MB",
        )

        // 🔴 캐시가 안 버리면 여기가 상한에 붙는다(또는 그 전에 OOM으로 죽는다).
        //    2,057장 × 256×256×4 = 약 526MB가 무제한일 때의 값이다.
        assertTrue(
            "전 종을 읽는 동안 네이티브 힙이 ${peakMb}MB 늘었다 — 상한 ${limitMb}MB의 80%가 넘는다. " +
                "LruCache가 evict하지 않으면 저가형 기기(힙 48MB)에서 죽는다",
            peakMb < limitMb * 8 / 10,
        )

        // ⚠️ **대조군.** 위 단정은 로더가 아무것도 안 해도 통과한다(안 쓰면 안 늘어난다).
        //    실제로 디코딩이 일어났다는 것을 여기서 고정한다.
        assertTrue("전 종을 읽고도 네이티브 힙이 안 늘었다 — 로더가 아무것도 디코딩하지 않았다", peakMb > 0)
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
