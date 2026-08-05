package com.catchflower.app.ui.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catchflower.app.core.GamePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 정사각 크롭·회전·리샘플 검증.
 *
 * **왜 필요한가**: 이 계산이 틀리면 **화면에는 정상으로 보이고 인식률만 떨어진다.**
 * 회전이 90도 돌아간 사진, 가이드 프레임과 다른 영역이 잘린 사진은
 * 눈으로 앱을 써봐도 못 잡는다. 유료 API 호출이 조용히 낭비될 뿐이다.
 *
 * `Bitmap`은 안드로이드 구현이 필요해서 계측 테스트다 (JVM 단위 테스트로는 못 돈다).
 */
@RunWith(AndroidJUnit4::class)
class SquareCropTest {

    /** 좌우를 구분할 수 있는 비대칭 이미지. 회전을 검증하려면 대칭이면 안 된다. */
    private fun sourceBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                // 왼쪽 절반은 빨강, 오른쪽 절반은 파랑. 위쪽 10%는 초록 띠.
                val color = when {
                    y < height / 10 -> Color.GREEN
                    x < width / 2 -> Color.RED
                    else -> Color.BLUE
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private fun decode(jpeg: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    @Test
    fun 세로_사진은_정사각이_된다() {
        val result = decode(sourceBitmap(1080, 1920).toSquareJpegBytes(0))
        assertEquals(result.width, result.height)
    }

    @Test
    fun 가로_사진도_정사각이_된다() {
        val result = decode(sourceBitmap(1920, 1080).toSquareJpegBytes(0))
        assertEquals(result.width, result.height)
    }

    @Test
    fun 긴변은_정책값을_넘지_않는다() {
        // 4000px 원본 → 1600px. 그대로 보내면 업로드·토큰이 커진다.
        val result = decode(sourceBitmap(4000, 3000).toSquareJpegBytes(0))
        assertEquals(GamePolicy.PHOTO_LONG_EDGE_PX, result.width)
    }

    @Test
    fun 작은_사진을_억지로_키우지_않는다() {
        // 확대는 화질만 버리고 인식에 도움이 안 된다.
        val result = decode(sourceBitmap(600, 800).toSquareJpegBytes(0))
        assertEquals(600, result.width)
    }

    @Test
    fun 중앙을_자른다() {
        // 세로 1920의 중앙 1080을 자르면 위쪽 초록 띠(192px)는 사라진다.
        // 위에서부터 자르면 초록이 남는다 — 즉 가이드 프레임과 다른 곳을 보낸 것이다.
        val result = decode(sourceBitmap(1080, 1920).toSquareJpegBytes(0))
        val topCenter = result.getPixel(result.width / 4, 4)
        assertTrue(
            "중앙 크롭이 아니다. 상단에 초록 띠가 남았다 (색=$topCenter)",
            Color.green(topCenter) < 200 || Color.red(topCenter) > 100,
        )
    }

    /**
     * 회전 보정이 실제로 픽셀을 돌리는지 본다.
     *
     * ⚠️ 이게 이 파일에서 가장 중요한 테스트다. `BitmapFactory`는 EXIF를 적용하지 않으므로
     *    보정하지 않으면 세로로 든 폰의 사진이 90도 누운 채 AI로 간다.
     */
    @Test
    fun 회전을_적용한다() {
        val source = sourceBitmap(1000, 1000)

        val notRotated = decode(source.toSquareJpegBytes(0))
        val rotated90 = decode(source.toSquareJpegBytes(90))

        // 원본은 왼쪽=빨강 / 오른쪽=파랑.
        // 90도(시계방향) 돌리면 왼쪽이던 빨강이 **위쪽**으로 간다.
        val leftOfOriginal = notRotated.getPixel(notRotated.width / 6, notRotated.height / 2)
        assertTrue("원본 왼쪽이 빨강이 아니다", Color.red(leftOfOriginal) > 150)

        val topOfRotated = rotated90.getPixel(rotated90.width / 2, rotated90.height / 6)
        assertTrue(
            "90도 회전이 적용되지 않았다. 위쪽이 빨강이어야 한다 (색=$topOfRotated)",
            Color.red(topOfRotated) > 150,
        )

        // 회전해도 정사각은 유지된다.
        assertEquals(rotated90.width, rotated90.height)
    }

    /**
     * 가이드 프레임 → 이미지 좌표 변환.
     *
     * ⚠️ 실기 확인에서 걸린 버그다. 프리뷰는 `FILL_CENTER`로 확대되어 화면에 **일부만**
     *    보이는데, 크롭은 이미지 전체의 중앙을 잘랐다. 게다가 프레임은 화면 중앙이 아니다.
     *    이 테스트가 없으면 다시 어긋나도 아무도 모른다 (사진은 정상으로 보인다).
     */
    @Test
    fun 가이드_프레임을_이미지_좌표로_옮긴다() {
        // 4:3 센서(1600x1200)를 1080x2400 세로 화면에 FILL_CENTER로 넣는다.
        // 세로를 채우려면 2400/1200 = 2.0배. 가로는 1600*2.0 = 3200 → 좌우 1060씩 잘린다.
        val guide = GuideRect(
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            left = 40f,
            top = 500f,
            size = 1000f,
        )
        val crop = mapGuideToImage(guide, imageWidth = 1600, imageHeight = 1200)

        // 화면 1000px은 이미지에서 1000/2.0 = 500px.
        assertEquals(500, crop.size)
        // 화면 x=40 → 이미지 x=(40+1060)/2.0 = 550
        assertEquals(550, crop.left)
        // 화면 y=500 → 이미지 y=(500+0)/2.0 = 250
        assertEquals(250, crop.top)
    }

    @Test
    fun 크롭_영역은_이미지를_벗어나지_않는다() {
        // 프레임이 화면 아래쪽에 붙은 극단적 경우.
        val guide = GuideRect(
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            left = 0f,
            top = 2300f,
            size = 1080f,
        )
        val crop = mapGuideToImage(guide, imageWidth = 1600, imageHeight = 1200)
        assertTrue("left가 음수다: ${crop.left}", crop.left >= 0)
        assertTrue("top이 음수다: ${crop.top}", crop.top >= 0)
        assertTrue("가로를 넘는다", crop.left + crop.size <= 1600)
        assertTrue("세로를 넘는다", crop.top + crop.size <= 1200)
    }

    @Test
    fun 가이드를_주면_그_영역이_잘린다() {
        // 원본 위쪽 10%는 초록 띠다. 프레임을 맨 위에 두면 초록이 **남아야** 한다
        // (중앙 크롭이면 사라진다). 즉 가이드가 실제로 쓰였는지 색으로 구분한다.
        val source = sourceBitmap(1000, 1000)
        val crop = SquareCrop(left = 0, top = 0, size = 100)
        val result = decode(source.toSquareJpegBytes(0, crop))

        val pixel = result.getPixel(result.width / 2, result.height / 2)
        assertTrue(
            "가이드 영역(좌상단)이 잘리지 않았다. 초록이어야 한다 (색=$pixel)",
            Color.green(pixel) > 150 && Color.red(pixel) < 150,
        )
    }

    @Test
    fun 유효한_JPEG을_만든다() {
        val jpeg = sourceBitmap(1080, 1920).toSquareJpegBytes(0)
        // 디코딩되면 유효하다. null이면 손상된 바이트다.
        assertTrue("JPEG 디코딩 실패", BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) != null)
        // JPEG 매직 바이트 (FF D8).
        assertEquals(0xFF, jpeg[0].toInt() and 0xFF)
        assertEquals(0xD8, jpeg[1].toInt() and 0xFF)
    }
}
