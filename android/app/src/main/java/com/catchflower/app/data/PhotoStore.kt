package com.catchflower.app.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 촬영 사진을 기기에 저장한다. iOS `PhotoStore`의 대응물이다.
 *
 * **왜 필요한가.** 지금까지 판별에 쓴 JPEG를 그냥 버렸다. 그래서 화면 10·11의
 * `지금까지 만난 {꽃}` 스트립과 화면 05 `내 발견 기록`이 전부 회색 아이콘이었다.
 *
 * **사진을 레코드에 넣지 않는다.** `Discovery.localPhotoPath`에는 **파일명만** 넣는다.
 * 서버(A-2)가 붙으면 같은 파일명이 스토리지 키가 되므로 레코드 모양이 안 바뀐다.
 *
 * ⚠️ 저장 위치는 `filesDir/photos/`다. **`cacheDir`를 쓰면 안 된다** —
 *    안드로이드가 저장공간이 부족할 때 임의로 비우고, 그러면 **도감 사진이 소리 없이
 *    사라진다.** 도감은 사용자의 자산이다. (iOS `Caches/`와 같은 함정.)
 *
 * ⚠️ 외부 저장소(`getExternalFilesDir`)도 쓰지 않는다. 다른 앱과 갤러리에 노출되는데
 *    화면 03이 "사진은 앱 안에만 보관한다"고 말하고 있다.
 */
class PhotoStore(private val root: File) {

    companion object {
        private const val DIR = "photos"

        fun default(context: Context): PhotoStore =
            PhotoStore(File(context.filesDir, DIR))
    }

    /**
     * 저장하고 **파일명**을 돌려준다. 실패하면 null.
     *
     * ⚠️ **경로 전체를 저장하지 않는다.** 앱 컨테이너 경로는 재설치·복원·기기 이관 때
     *    바뀔 수 있어서 다음 실행에 못 찾는다. 파일명만 들고 `file()`로 다시 만든다.
     */
    suspend fun save(jpeg: ByteArray): String? = withContext(Dispatchers.IO) {
        // 빈 데이터를 저장하면 `exists`는 true인데 디코딩은 실패해서 판단이 꼬인다.
        // (iOS 시뮬레이터 픽스처가 빈 Data()라서 실제로 겪은 문제다.)
        if (jpeg.isEmpty()) return@withContext null
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(root, name)
        try {
            root.mkdirs()
            // 원자적으로 쓴다 — 저장 중 죽으면 반쪽 JPEG가 남고, 그건 `exists`를 통과한다.
            val tmp = File(root, "$name.tmp")
            tmp.writeBytes(jpeg)
            if (!tmp.renameTo(target)) {
                target.writeBytes(jpeg)
                tmp.delete()
            }
            name
        } catch (e: java.io.IOException) {
            // 저장 실패를 삼키면 도감에 사진 없는 기록이 조용히 쌓인다. 기록은 남긴다.
            android.util.Log.e("CatchFlower", "사진 저장 실패", e)
            null
        }
    }

    fun file(fileName: String): File = File(root, fileName)

    fun exists(fileName: String): Boolean = file(fileName).let { it.exists() && it.length() > 0 }

    suspend fun bytes(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { file(fileName).readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 한 장 지운다. 기록을 지울 때 함께 부른다.
     *
     * ⚠️ 빈 파일명을 그냥 넘기면 `File(root, "")`가 **디렉터리 자체를 가리킨다.**
     *    빈 디렉터리라면 `delete()`가 성공해서 사진 폴더가 사라진다.
     */
    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (fileName.isBlank()) return@withContext false
        file(fileName).delete()
    }

    /**
     * 기록에서 사라진 사진을 지운다.
     *
     * **왜 필요한가.** 저장은 하고 지우지 않으면 사진만 무한히 쌓인다 —
     * 장변 1600px JPEG가 건당 200~400KB라 1,000건이면 수백 MB다.
     * B-5로 등록이 취소된 사진, 판별 실패 후 버린 사진이 여기 해당한다.
     *
     * ⚠️ `keep`이 빈 집합이면 **전부 지운다.** 호출부가 기록을 아직 못 읽은 상태에서
     *    부르면 사진을 다 날린다 — 그래서 빈 집합은 명시적으로 거부한다.
     */
    suspend fun pruneExcept(keep: Set<String>): Int = withContext(Dispatchers.IO) {
        if (keep.isEmpty()) return@withContext 0
        val files = root.listFiles() ?: return@withContext 0
        var removed = 0
        for (f in files) {
            if (f.name.endsWith(".tmp") || f.name !in keep) {
                if (f.delete()) removed++
            }
        }
        removed
    }
}
