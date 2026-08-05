package com.catchflower.app.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.catchflower.app.core.GamePolicy
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CameraX 바인딩과 촬영 후처리.
 *
 * **Compose에서 분리한 이유**: 여기서 하는 일(정사각 크롭·회전 보정·리샘플·JPEG 인코딩)은
 * 화면과 무관하고 **틀리면 조용히 틀린다** (회전이 90도 돌아간 사진이 AI로 간다).
 * 화면 코드에 섞으면 눈으로 확인할 방법이 없다.
 */

/** 카메라 준비에 실패했을 때 화면이 무엇을 말할지 정하려면 이유가 필요하다. */
sealed interface CameraBindResult {
    data class Ready(val capture: ImageCapture) : CameraBindResult
    data class Failed(val cause: Throwable) : CameraBindResult
}

/**
 * 프리뷰와 촬영을 라이프사이클에 붙인다.
 *
 * ⚠️ `bindToLifecycle`은 **이전 바인딩을 자동으로 풀지 않는다.** 다시 호출하면
 *    "Camera is already in use" 로 죽거나 프리뷰가 검게 남는다.
 *    그래서 매번 `unbindAll()`을 먼저 한다 (전면/후면 전환이 이 경로를 다시 탄다).
 */
suspend fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    surfaceProvider: Preview.SurfaceProvider,
    useFrontCamera: Boolean,
    flashMode: Int,
): CameraBindResult {
    val provider = awaitCameraProvider(context)
    val preview = Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }
    val capture = ImageCapture.Builder()
        // 화면 08의 대기 시간을 늘리지 않으려면 지연을 최소화한다.
        // 화질 대신 속도 — 어차피 정사각 크롭 후 1600px로 줄인다.
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(flashMode)
        .build()

    val selector = if (useFrontCamera) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    return try {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
        CameraBindResult.Ready(capture)
    } catch (e: Exception) {
        // 에뮬레이터·전면 카메라 없는 기기에서 실제로 던진다. 앱을 죽이지 않는다.
        CameraBindResult.Failed(e)
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, mainExecutor(context))
    }

private fun mainExecutor(context: Context): Executor = androidx.core.content.ContextCompat
    .getMainExecutor(context)

/**
 * 화면에 보이는 가이드 프레임의 위치. **프리뷰 뷰포트 좌표계**(왼쪽 위 0,0, 픽셀)다.
 *
 * 이걸 넘겨야 "네모 안"이 실제 전송 영역이 된다. null이면 이미지 중앙 정사각으로
 * 떨어지는데, 그건 **화면에 보이는 네모와 다른 영역이다** ([mapGuideToImage] 주석 참조).
 */
data class GuideRect(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val left: Float,
    val top: Float,
    val size: Float,
)

/**
 * 사진을 찍어 **가이드 프레임 안만 잘라낸 정사각 JPEG**을 돌려준다.
 *
 * 와이어프레임 07 주석 ②가 규정한다:
 * > 1:1 크롭 영역만 AI에 전송. 배경 노이즈를 줄여 오인식을 낮춘다.
 *
 * 즉 크롭은 화면 장식이 아니라 **인식 정확도 장치**다. 가이드 프레임 안이 곧 전송 영역이다.
 */
suspend fun ImageCapture.takeSquareJpeg(context: Context, guide: GuideRect? = null): ByteArray =
    suspendCancellableCoroutine { cont ->
        takePicture(
            mainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        cont.resume(image.toSquareJpeg(guide))
                    } catch (e: Throwable) {
                        cont.resumeWithException(e)
                    } finally {
                        // 닫지 않으면 두어 장 뒤에 카메라가 멈춘다 (이미지 버퍼 고갈).
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }

/**
 * `ImageProxy`(JPEG) → 정사각·회전보정·리샘플 → JPEG 바이트.
 *
 * ⚠️ **회전 보정이 핵심이다.** JPEG은 회전을 EXIF로만 갖고 있고 픽셀은 센서 방향
 *    그대로다. `BitmapFactory`는 EXIF를 **적용하지 않는다.** 그냥 크롭하면
 *    세로로 든 폰의 사진이 90도 누운 채 AI로 간다 — 화면에는 정상으로 보이는데
 *    인식률만 떨어진다. 그래서 `imageInfo.rotationDegrees`로 직접 돌린다.
 */
internal fun ImageProxy.toSquareJpeg(guide: GuideRect? = null): ByteArray {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("촬영 이미지를 디코딩할 수 없다")

    val crop = guide?.let {
        mapGuideToImage(it, imageWidth = decoded.width, imageHeight = decoded.height)
    }
    return decoded.toSquareJpegBytes(imageInfo.rotationDegrees, crop)
}

/** 이미지 픽셀 좌표계의 정사각 크롭 영역. */
internal data class SquareCrop(val left: Int, val top: Int, val size: Int)

/**
 * 화면의 가이드 프레임을 **이미지 픽셀 좌표**로 옮긴다.
 *
 * ⚠️ **이 변환이 없으면 "네모 안"과 전송 영역이 다르다.** 실기 확인에서 실제로 걸렸다:
 *    프리뷰는 `fillMaxSize` + `FILL_CENTER`라 센서 이미지의 가운데 띠만 화면에 보이는데,
 *    크롭은 **이미지 전체의 중앙** 정사각을 잘랐다. 게다가 가이드 프레임은 헤더와
 *    안내 문구 아래에 놓여 **화면 중앙보다 위에** 있다. 세 영역이 다 달랐다.
 *    결과는 조용하다 — 사진은 정상으로 보이고 **인식률만 떨어진다.**
 *
 * `FILL_CENTER`는 뷰포트를 꽉 채우도록 이미지를 확대해 넘치는 부분을 버린다.
 * 그래서 배율은 가로/세로 중 **큰 쪽**이고, 버려진 만큼이 오프셋이다.
 */
internal fun mapGuideToImage(guide: GuideRect, imageWidth: Int, imageHeight: Int): SquareCrop {
    val scale = maxOf(
        guide.viewportWidth / imageWidth,
        guide.viewportHeight / imageHeight,
    )
    // 이미지를 scale배 했을 때 뷰포트 밖으로 잘려 나간 양(뷰포트 픽셀) ÷ 2.
    val offsetX = (imageWidth * scale - guide.viewportWidth) / 2f
    val offsetY = (imageHeight * scale - guide.viewportHeight) / 2f

    val size = (guide.size / scale).toInt().coerceAtLeast(1)
    // 가이드 좌상단을 이미지 좌표로. 잘려 나간 만큼을 더해 되돌린다.
    val left = ((guide.left + offsetX) / scale).toInt()
    val top = ((guide.top + offsetY) / scale).toInt()

    // 반올림 오차로 이미지 밖을 가리키지 않게 잠근다.
    val clampedSize = minOf(size, imageWidth, imageHeight)
    return SquareCrop(
        left = left.coerceIn(0, imageWidth - clampedSize),
        top = top.coerceIn(0, imageHeight - clampedSize),
        size = clampedSize,
    )
}

/**
 * 정사각 크롭 → 회전 보정 → 긴 변 [GamePolicy.PHOTO_LONG_EDGE_PX] → JPEG.
 *
 * 순서가 중요하다. **크롭을 먼저 하고 회전한다** — 정사각은 회전에 불변이라
 * 결과가 같지만, 회전한 큰 비트맵을 크롭하면 메모리를 두 배로 쓴다.
 *
 * @param crop 잘라낼 영역. null이면 이미지 중앙 정사각이다 — 가이드 프레임과
 *   어긋날 수 있으므로 카메라 경로에서는 [mapGuideToImage] 결과를 넘긴다.
 */
internal fun Bitmap.toSquareJpegBytes(
    rotationDegrees: Int,
    crop: SquareCrop? = null,
): ByteArray {
    val side = crop?.size ?: minOf(width, height)
    val cropped = Bitmap.createBitmap(
        this,
        crop?.left ?: ((width - side) / 2),
        crop?.top ?: ((height - side) / 2),
        side,
        side,
    )

    val target = minOf(side, GamePolicy.PHOTO_LONG_EDGE_PX)
    val matrix = Matrix().apply {
        val scale = target.toFloat() / side
        postScale(scale, scale)
        if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
    }
    val finished = Bitmap.createBitmap(cropped, 0, 0, side, side, matrix, true)

    return ByteArrayOutputStream().use { out ->
        finished.compress(Bitmap.CompressFormat.JPEG, GamePolicy.PHOTO_JPEG_QUALITY, out)
        out.toByteArray()
    }
}
