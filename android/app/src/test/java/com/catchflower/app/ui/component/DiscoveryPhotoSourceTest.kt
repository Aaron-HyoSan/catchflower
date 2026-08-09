package com.catchflower.app.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **사용자 사진을 그리는 곳은 한 군데뿐인가.**
 *
 * ## 무엇을 잡는가
 *
 * 🔴 화면 05(`내 발견 기록`)와 화면 13(공유 설정)이 **같은 사진을 각각 그리고 있었다.**
 * 화면 13은 `BitmapFactory.decodeFile`을 옵션 없이 불렀고(원본 그대로 = 최대 10MB),
 * 화면 05는 **회색 박스**를 그렸다 — 사진은 (28)부터 기기에 저장되고 있었는데
 * `DexViewModel.photoFile`의 **호출자가 0개**였다((39)에서 고쳤다).
 *
 * ⚠️ **두 결함 모두 화면에는 증상이 없었다.** 화면 13은 사진이 잘 나왔고(느리고 무겁기만 했다),
 *    화면 05는 "아직 사진 기능이 없구나"로 읽혔다. 같은 데이터를 두 곳에서 각각 그리면
 *    **한쪽이 틀렸을 때 어느 쪽이 맞는지 화면만 봐서는 알 수 없다.**
 *
 * ## 왜 값이 아니라 소스를 읽는가
 *
 * `@Composable`은 JVM에서 실행할 수 없고, 디코딩 결과는 기기에서만 나온다.
 * "사진이 나온다"는 실기로 확인했고(진행.md (39)), 여기서 막는 것은
 * **다음 사람이 또 각자 그리는 것**이다.
 */
class DiscoveryPhotoSourceTest {

    @Test
    fun 소스를_실제로_읽었다() {
        assertTrue("main 소스를 못 찾았다", mainSources.size > 30)
        assertTrue(
            "사진을 그리는 공용 함수가 없어졌다",
            component.contains("fun DiscoveryPhoto("),
        )
    }

    /**
     * 사진 디코딩은 [PhotoLoader] **한 곳**에서만 한다.
     *
     * ⚠️ 화면이 직접 `BitmapFactory.decodeFile`을 부르면 **다운샘플링을 안 하게 되고**
     *    (옵션 없이 부르는 것이 가장 쉬운 길이다) 목록에서 힙을 넘긴다.
     *    일러스트 쪽은 [FlowerIllustLoader]가 같은 역할을 한다.
     */
    @Test
    fun 화면이_직접_사진을_디코딩하지_않는다() {
        val offenders = mainSources
            .filter { it.name != "PhotoLoader.kt" && it.name != "FlowerIllustLoader.kt" }
            .filter { bodyOf(it).contains("BitmapFactory.decodeFile") }
            .map { it.name }
        assertEquals(
            "화면이 사진을 직접 디코딩한다 — PhotoLoader를 쓰게 만들어야 한다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * 사용자 사진을 쓰는 화면은 **[DiscoveryPhoto]를 통해서만** 그린다.
     *
     * 판정 기준은 "`photoFile(...)`의 결과를 화면으로 넘기는 파일"이다 —
     * 즉 **사진 파일을 손에 든 화면**이 대상이고, 그 화면은 공용 함수를 불러야 한다.
     */
    @Test
    fun 사진을_쓰는_화면은_공용_함수를_쓴다() {
        val holders = mainSources
            .filter { it.name.endsWith("Screen.kt") || it.name.endsWith("Flow.kt") }
            .filter { bodyOf(it).contains("photo = ") || bodyOf(it).contains("photoFile(") }

        assertTrue("사진을 다루는 화면을 하나도 못 찾았다(검사가 비어 있다)", holders.size >= 2)

        val missing = holders.filter { f ->
            val b = bodyOf(f)
            // 사진을 **직접 그리는** 파일만 공용 함수를 불러야 한다.
            // 사진을 아래로 넘기기만 하는 파일(CaptureFlow)은 대상이 아니다.
            val draws = b.contains("Image(") || b.contains("decodeFile")
            draws && !b.contains("DiscoveryPhoto(")
        }.map { it.name }
        assertEquals(
            "사진을 자기 손으로 그리는 화면이 있다 — DiscoveryPhoto를 쓰게 해야 한다: $missing",
            emptyList<String>(),
            missing,
        )
    }

    /**
     * 사진이 없을 때 **회색 박스를 두지 않는다.**
     *
     * 🔴 [PhotoPlaceholder]는 "사진이 있을 수 없는 자리"(랭킹의 빈 칸)에만 쓴다.
     *    발견 기록에 쓰면 **파일이 기기에 있는데도 회색 박스**가 나오고, 그건
     *    "사진이 없다"와 화면에서 구별되지 않는다 — 그것이 (39) 이전의 상태였다.
     */
    @Test
    fun 발견_기록에_회색_박스를_쓰지_않는다() {
        val users = mainSources.filter { bodyOf(it).contains("PhotoPlaceholder(") }
            .map { it.name }
            .filter { it != "FlowerIllust.kt" } // 정의부
        // 랭킹은 정당한 사용처다(꽃 정보를 못 찾은 칸 — 사진이 아예 없는 자리).
        assertEquals(
            "발견 기록 화면이 회색 박스를 쓴다: $users",
            listOf("RankingScreens.kt"),
            users,
        )
    }

    private companion object {
        val mainSources: List<File> by lazy {
            File("src/main/java/com/catchflower/app")
                .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

        val component: String by lazy {
            File("src/main/java/com/catchflower/app/ui/component/FlowerIllust.kt").readText()
        }

        /**
         * 주석을 뺀 본문. **KDoc이 증거로 세어지면 안 된다** —
         * 이 파일들의 주석에는 `decodeFile`·`PhotoPlaceholder`가 "쓰지 말라"는
         * 설명으로 여러 번 나온다. 같은 함정을 [PhotoLoaderTest]에서 이미 한 번 밟았다.
         */
        fun bodyOf(file: File): String =
            file.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
    }
}
