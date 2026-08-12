package com.catchflower.app.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.catchflower.app.data.model.Flower
import java.io.IOException

/**
 * 꽃 일러스트(`assets/flower_illust/0001.webp` …)를 읽는다.
 *
 * 원본은 `꽃도감/꽃도감_일러스트_전수_webp/`이고 빌드가 그대로 복사한다
 * (`app/build.gradle.kts`의 `SyncSharedAssets`).
 *
 * 🔴 **2026-08-12: 전수 2,057장이 들어왔다.** 그 전에는 200장이었다. 그리고
 *    **확장자가 `.webp`다** — PNG 2,057장은 130MB로 Play 업로드 상한(AAB 150MB ·
 *    APK 직접 100MB)을 넘겨
 *    **빌드는 성공하고 스토어 업로드에서 막힌다**(`꽃도감/_tools/pack_illust_webp.py`).
 *    ⚠️ 투명 WebP는 API 18+이고 이 앱은 `minSdk 26`이라 안전하다. 이걸 확인 안 하고
 *    갈면 **오래된 기기에서만** 빈 칸이 되고 이 맥의 에뮬레이터(API 36)로는 안 보인다.
 *
 * ⚠️ **그래도 [missing] 경로를 지우지 않는다.** 지금은 전 종에 그림이 있지만,
 *    변환 폴더가 낡거나 종이 추가되면 **다시 없는 상태가 된다.** 그때 로그 상한이
 *    없으면 1,857줄이 logcat 5MiB 링버퍼를 채워 판별 진단 로그 4줄을 밀어낸다.
 *
 * ⚠️ **파일 키는 도감번호다. 이름이 아니다.** macOS는 파일명의 한글을 **NFD(자모 분리)** 로
 *    저장하는데 `flowers.json`의 `name`은 NFC다. `"%04d_%s.webp".format(id, name)`으로
 *    조립해서 찾으면 **전 종 못 찾는다** — 눈으로는 같은 글자라 원인을 찾기 어렵다.
 *    번호만 쓰면 이 문제가 아예 생기지 않는다.
 *
 * ⚠️ **캐시가 필수다.** 512×512 RGBA 한 장이 메모리에서 **1MB**이고 2,057장은 **2GB**다
 *    (전수가 들어오면서 이 위험이 10배가 됐다 — 디스크에서는 46MB인데 **디코딩하면
 *    압축이 풀린다**). 도감 그리드(화면 04)는 2,057칸을 스크롤하므로 캐시가 없으면
 *    스크롤할 때마다 디코딩하고, 무제한 캐시면 OOM으로 죽는다. [LruCache]로 상한을 둔다.
 *
 * ⚠️ **디코딩 크기를 화면 크기에 맞춘다.** 84dp 셀에 512px 원본을 그대로 올리면
 *    한 장에 1MB를 쓰면서 화면에는 그 이상 보이지 않는다. `inSampleSize`로 줄여 읽는다.
 */
object FlowerIllustLoader {

    private const val TAG = "FlowerIllust"

    /**
     * 캐시 상한 — **앱에 허용된 힙의 1/8**.
     *
     * 고정 MB로 박지 않는다. 기기 힙은 48MB(저가형)에서 512MB까지 벌어지고,
     * 고정값은 한쪽에서 OOM이거나 다른 쪽에서 낭비다.
     */
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    /**
     * 없는 것으로 확인된 번호. 매 프레임 `assets.open`으로 예외를 만들지 않기 위해 기억한다.
     *
     * ⚠️ 전수 납품(2026-08-12) 후에는 **하나도 없는 것이 정상**이다. 그래도 이 경로를
     *    지우지 않는다: 전량이 **전부** 안 나오는 상황(자산 복사 실패, 확장자·자릿수
     *    불일치)과 "아직 안 온 그림"이 화면에서 똑같이 보이기 때문이다.
     *    ⚠️ 종당 한 줄씩 찍으면 최악 2,057줄이 되어 logcat 5MiB 링버퍼가
     *    판별 진단 로그를 밀어낸다 — 그래서 [MISSING_LOG_LIMIT]까지만 찍고 끊는다.
     */
    private val missing = HashSet<Int>()

    /** 누락 로그 상한. 넘으면 마지막에 요약 한 줄만 남긴다. */
    private const val MISSING_LOG_LIMIT = 20

    /**
     * [flower]의 일러스트. 없으면 null — 호출부가 플레이스홀더로 되돌린다.
     *
     * ⚠️ **경로를 여기서 조립하지 않는다.** [Flower.illustAssetName]이 규칙의 유일한
     *    원본이다. 같은 규칙을 두 곳에 적으면 한쪽만 고쳐졌을 때 **그림이 조용히
     *    안 나오고 예외도 안 난다** — 화면으로는 "아트가 아직 안 왔나"로 보인다.
     *
     * @param reqSizePx 화면에 실제로 그릴 픽셀 크기. 이보다 크게 디코딩하지 않는다.
     */
    fun load(context: Context, flower: Flower, reqSizePx: Int): Bitmap? {
        val id = flower.id
        // 같은 종을 셀(작게)과 상세(크게)에서 같이 쓴다. 크기별로 따로 캐시하면
        // 같은 꽃이 여러 장 남아 상한을 빨리 먹는다 — 버킷을 2의 거듭제곱으로 묶는다.
        val bucket = sampleBucket(reqSizePx)
        val key = "$id@$bucket"
        cache.get(key)?.let { return it }
        if (id in missing) return null

        val path = flower.illustAssetName
        val bitmap = try {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inSampleSize = 512 / bucket
                    // ARGB_8888 유지. RGB_565로 줄이면 **투명 배경이 검게 칠해진다** —
                    // 일러스트가 투명 PNG라 알파를 버릴 수 없다.
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (e: IOException) {
            // 파일이 없다 (복사 누락 · 배치 미납품). 한 번만 알린다.
            if (missing.add(id)) {
                when {
                    missing.size <= MISSING_LOG_LIMIT -> Log.w(TAG, "일러스트 없음: $path")
                    missing.size == MISSING_LOG_LIMIT + 1 ->
                        Log.w(TAG, "일러스트 없음이 ${MISSING_LOG_LIMIT}건을 넘었다 — 이후 생략")
                }
            }
            null
        } catch (e: OutOfMemoryError) {
            // 디코딩은 OOM을 Error로 던진다 — catch하지 않으면 앱이 죽는다.
            // 그림 한 장 때문에 도감을 못 보게 만들 이유가 없다.
            Log.w(TAG, "일러스트 디코딩 실패(메모리): $path")
            null
        }

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /**
     * 요청 크기를 **512의 약수**로 올림한다 (64·128·256·512).
     *
     * `inSampleSize`는 2의 거듭제곱만 유효하므로(다른 값은 내려서 반올림된다)
     * 여기서 미리 맞춰 실제 디코딩 크기와 캐시 키가 어긋나지 않게 한다.
     */
    internal fun sampleBucket(reqSizePx: Int): Int = when {
        reqSizePx <= 64 -> 64
        reqSizePx <= 128 -> 128
        reqSizePx <= 256 -> 256
        else -> 512
    }

    /** 테스트·메모리 압박 대응. */
    fun clear() {
        cache.evictAll()
        missing.clear()
    }
}
