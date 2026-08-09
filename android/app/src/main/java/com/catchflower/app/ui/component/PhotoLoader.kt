package com.catchflower.app.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * 기기에 저장된 촬영 사진을 **화면 크기에 맞춰** 읽는다.
 *
 * ## 왜 `BitmapFactory.decodeFile`을 그냥 쓰지 않나
 *
 * 저장된 사진은 정사각이고 한 변이 **최대** 1600px이다
 * ([com.catchflower.app.core.GamePolicy.PHOTO_LONG_EDGE_PX]).
 * 1600px을 그대로 디코딩하면 `1600 × 1600 × 4바이트 = 약 10MB`가 비트맵 하나에 들어간다.
 * 화면 05 `내 발견 기록`은 **52dp 썸네일을 목록으로** 그린다 — 10장이면 100MB이고,
 * 저가형 기기의 힙 상한(48MB)을 한 화면에서 넘긴다.
 *
 * ⚠️ **1600은 상한이지 실측값이 아니다.** [com.catchflower.app.ui.capture.toSquareJpegBytes]가
 *    `minOf(side, PHOTO_LONG_EDGE_PX)`로 **키우지 않고 줄이기만** 한다 — 에뮬레이터
 *    카메라 실측은 **564px**이었다(2026-08-09). 즉 카메라 해상도에 따라 한 변이
 *    수백 px일 수도, 1600px일 수도 있다. 그래서 [sampleSize]에 **크기를 가정하지 않고**
 *    실제 경계를 넣는다 — 1600을 상수로 박으면 작은 사진을 과도하게 줄여 흐려진다.
 *
 * 🔴 **그런데 화면은 정상으로 보인다.** OOM이 나기 전까지는 사진이 예쁘게 나오고,
 * 죽을 때는 사진과 상관없어 보이는 자리에서 죽는다([FlowerIllustLoader]가 일러스트
 * 200장에 같은 이유로 캐시를 두는 것과 같은 판단이다).
 *
 * ⚠️ **캐시 키에 파일의 `lastModified`를 넣지 않는다.** 사진 파일은 한 번 쓰고
 *    **다시 쓰지 않는다**([com.catchflower.app.data.PhotoStore]가 UUID 파일명으로
 *    새로 만들고, 지울 때만 건드린다). 넣으면 매번 `stat`을 부르게 되고,
 *    지워진 파일은 `lastModified`가 0이라 **키가 조용히 바뀐다.**
 */
object PhotoLoader {

    private const val TAG = "CatchFlowerPhoto"

    /**
     * 캐시 상한 — **힙의 1/8**. [FlowerIllustLoader]와 같은 비율이다.
     *
     * ⚠️ 두 캐시가 각각 1/8을 쓰므로 합쳐서 1/4다. 도감 그리드(일러스트 200칸)와
     *    발견 기록(사진 목록)은 **다른 화면**이라 동시에 상한까지 차지 않는다 —
     *    같은 화면에 둘을 섞게 되면 이 비율을 다시 봐야 한다.
     */
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    /**
     * 이미 읽어 둔 것만 돌려준다. **디스크를 건드리지 않는다.**
     *
     * 🔴 **[DiscoveryPhoto]가 첫 프레임을 이걸로 그린다.** 캐시에 있는 사진까지
     *    비동기로 돌리면 **스크롤을 되돌릴 때마다 일러스트가 한 번 번쩍인다** —
     *    이미 메모리에 있는 것을 못 보여주는 것이라 사용자에게는 고장으로 보인다.
     */
    fun cached(file: File, reqSizePx: Int): Bitmap? = cache.get(keyOf(file, reqSizePx))

    /**
     * [file]을 [reqSizePx]보다 작지 않게, 그러나 필요 이상 크지 않게 읽는다.
     * 파일이 없거나 깨졌으면 null — 호출부가 일러스트로 되돌린다.
     *
     * 🔴 **메인 스레드에서 부르지 않는다.** 실측 **18~94ms/장**이고 60fps 한 프레임은
     *    16.7ms다 — 사진 한 장이 **프레임 1~6개**를 먹는다((39) 실측).
     *    호출부는 [DiscoveryPhoto] 하나이고 거기서 IO 디스패처로 보낸다.
     *
     * 🔴 **프레임 수로 이 규칙을 검증할 수 없다 — 시도했고 실패했다((40)).** 이 에뮬레이터는
     *    SwiftShader(소프트웨어 렌더링)라서 `gfxinfo`가 **사진과 무관한 화면도 100% janky**로
     *    보고한다. 실측 대조: 사진을 한 장도 안 읽는 도감 그리드가 `100% janky / p50 400ms`,
     *    사진 41장 목록이 `89.9% / p50 400ms`로 **사진 화면이 더 좋게** 나왔다.
     *    ⚠️ 즉 `Choreographer: Skipped`가 줄었는지로 판정하면 **고쳤는지 망쳤는지 모른다.**
     *    검증한 방법은 **디코딩 스레드 이름을 기기가 직접 보고하게 한 것**이다 —
     *    41장 전부 `main=false`(`DefaultDispatcher-worker-*`)이고, 되돌려 스크롤해도
     *    디코딩이 41회에서 늘지 않았다(캐시 적중). 소스 단정은
     *    [com.catchflower.app.ui.component.DiscoveryPhotoSourceTest]가 맡는다.
     */
    fun load(file: File, reqSizePx: Int): Bitmap? {
        // 키는 **파일명 + 요청 크기**다. 파일명이 UUID라 충돌이 없고, 같은 사진을
        // 52dp(목록)와 72dp(공유 카드)에서 다른 해상도로 쓰기 때문에 크기가 필요하다.
        // (주석이 한때 "길이를 키에 넣는다"고 적혀 있었는데 **길이는 키에 없다** —
        //  대신 실패를 캐시하지 않는 것으로 같은 문제를 막는다.)
        //
        // ⚠️ **실패는 캐시하지 않는다**(아래 `if (bitmap != null)`). 저장 중 죽어
        //    0바이트가 남은 파일을 null째로 캐시하면, 다음에 정상으로 채워져도
        //    같은 실행 안에서는 **계속 null을 돌려준다.**
        val key = keyOf(file, reqSizePx)
        cache.get(key)?.let { return it }

        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            // 경계 디코딩이 실패하면 크기가 -1이다. 이때 sampleSize를 계산하면
            // 1이 나와서 **깨진 파일을 원본 크기로 읽으려 한다.**
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "사진을 디코딩할 수 없다: ${file.name}")
                null
            } else {
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSizePx)
                        // 사진은 알파가 없다. RGB_565로 읽으면 메모리가 절반인데,
                        // 일러스트와 달리 **투명 배경이 없어서** 검게 칠해질 것이 없다.
                        inPreferredConfig = Bitmap.Config.RGB_565
                    },
                )
            }
        } catch (e: OutOfMemoryError) {
            // 디코딩 OOM은 Error다 — 안 잡으면 앱이 죽는다. 사진 한 장 때문에
            // 도감을 못 보게 만들 이유가 없다.
            Log.w(TAG, "사진 디코딩 실패(메모리): ${file.name}")
            null
        }

        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /**
     * `inSampleSize` — **2의 거듭제곱**이어야 한다(다른 값은 내림으로 반올림된다).
     *
     * 원본의 **짧은 변**을 기준으로 잡는다. 긴 변으로 잡으면 세로로 긴 사진에서
     * 짧은 변이 요청 크기보다 작아져 **썸네일이 흐려진다.**
     *
     * 결과가 지키는 것: **`짧은 변 / sample`이 `[reqSizePx, 2 × reqSizePx)` 안에 든다.**
     * 아래로는 요청보다 작아지지 않고(흐려지지 않고), 위로는 필요한 것의 2배를
     * 넘지 않는다(메모리 4배를 안 쓴다). 이걸
     * [com.catchflower.app.ui.component.PhotoLoaderTest]가 범위로 훑어서 단정한다.
     *
     * 🔴 **처음에 `> reqSizePx`로 쓰고 "`>=`면 요청보다 작아진다"고 주석에 적었는데
     *    그게 틀렸다.** `>=`는 **반쪽이 아직 요청 이상일 때만** 줄이므로 정확히
     *    요청 크기에서 멈춘다 — 절대 밑으로 내려가지 않는다. 틀린 쪽은 `>`였다:
     *    `104px`을 `52px` 칸에 넣을 때 한 단계를 **안** 줄여 픽셀 4배를 읽는다.
     *    ⚠️ 두 구현 모두 **사진이 정상으로 보인다** — 차이는 메모리에만 나타난다.
     *    (내림으로 따라가므로 실제 디코딩 크기 `ceil(원본/sample)`보다 작게 잡는다.
     *     즉 이 계산은 항상 안전한 쪽으로 틀린다.)
     *
     * 🔴 **`>=`로 바꾸면서 `reqSizePx <= 0` 가드가 "무한 루프 방지"로 승격됐다.**
     *    `>`였을 때 요청이 0이면 `0 / 2 > 0`이 거짓이라 그냥 멈췄다. `>=`에서는
     *    `0 / 2 >= 0`이 **영원히 참**이다 — 돌연변이로 가드를 지워 보니 테스트가
     *    빨개지지 않고 **멈추지 않았다**(10분 타임아웃에 걸렸다).
     *    ⚠️ 컴포저블 안에서 이게 터지면 화면이 **얼어붙고** 크래시 로그도 안 남는다.
     *    그래서 가드와 별도로 `short > 1`을 루프 조건에 둔다:
     *    가드는 **답을 맞히는 것**이고, `short > 1`은 **가드가 사라져도 끝나게 하는 것**이다.
     *    (`assertEquals(1, ...)`가 빨개지는 것이 멈추지 않는 것보다 낫다.)
     */
    internal fun sampleSize(width: Int, height: Int, reqSizePx: Int): Int {
        if (reqSizePx <= 0) return 1
        var short = minOf(width, height)
        var sample = 1
        while (short > 1 && short / 2 >= reqSizePx) {
            short /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * 캐시 키 — **파일명 + 요청 크기**.
     *
     * ⚠️ [cached]와 [load]가 **같은 키를 만들어야 한다.** 두 곳에서 따로 조립하면
     *    한쪽만 고쳤을 때 **캐시가 조용히 항상 빈 것처럼** 동작한다(디코딩이 매번
     *    다시 돌고, 화면은 정상으로 보인다).
     */
    private fun keyOf(file: File, reqSizePx: Int): String = "${file.name}@$reqSizePx"

    /** 테스트·메모리 압박 대응. */
    fun clear() = cache.evictAll()
}
