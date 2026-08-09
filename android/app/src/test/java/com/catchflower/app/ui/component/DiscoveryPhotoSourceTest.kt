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

    /**
     * 사진 디코딩은 **메인 스레드에서 하지 않는다.**
     *
     * 🔴 (39)에서 `remember { PhotoLoader.load(...) }`로 **동기 디코딩**을 했고,
     *    기기가 **18~94ms/장 · `main=true`**를 보고했다 — 60fps 한 프레임(16.7ms)의
     *    1~6배다. 기록 41개를 스크롤하는 동안 프레임을 계속 놓쳤다.
     *
     * ⚠️ **프레임 수로는 이 규칙을 지킬 수 없다((40)에서 확인).** 이 에뮬레이터는
     *    SwiftShader라서 `gfxinfo`가 사진과 무관한 화면도 100% janky로 보고한다 —
     *    대조 실측에서 **사진을 안 읽는 도감 그리드(100%)가 사진 41장 목록(89.9%)보다
     *    더 나빴다.** 그래서 판정을 **스레드 배치**로 한다: 기기 검증은
     *    `main=false`(41/41)로 했고, 회귀를 막는 것은 이 소스 단정이다.
     *
     * 판정 기준은 "[PhotoLoader.load]를 부르는 곳이 `withContext`/코루틴 안인가"다.
     * `remember {` 블록 안에서 부르면 **그 자리가 메인 스레드다.**
     */
    @Test
    fun 사진_디코딩을_메인_스레드에서_하지_않는다() {
        val callers = mainSources
            .filter { it.name != "PhotoLoader.kt" }
            .filter { bodyOf(it).contains("PhotoLoader.load(") }
            .map { it.name }
        // 호출부가 사라지면(이름이 바뀌면) 검사가 조용히 비어 버린다.
        assertEquals(
            "PhotoLoader.load를 부르는 곳이 하나여야 한다(DiscoveryPhoto): $callers",
            listOf("FlowerIllust.kt"),
            callers,
        )

        val body = bodyOf(File("src/main/java/com/catchflower/app/ui/component/FlowerIllust.kt"))

        // `remember { ... PhotoLoader.load ... }`는 컴포지션(=메인)에서 즉시 돈다.
        val syncInRemember = Regex("""remember\([^)]*\)\s*\{[^}]*PhotoLoader\.load\(""")
        assertTrue(
            "remember 블록에서 PhotoLoader.load를 부른다 — 그 자리가 메인 스레드다",
            !syncInRemember.containsMatchIn(body),
        )

        // 디코딩은 IO 디스패처로 넘긴다.
        //
        // ⚠️ `[^}]*`로 쓰면 안 된다 — 실제 코드는 `withContext(Dispatchers.IO) {`와
        //    `PhotoLoader.load(` 사이에 `takeIf { it.exists() }`가 있어서 **중간에
        //    `}`가 나온다.** 처음 그렇게 써서 **고쳐 놓은 코드가 위반으로 잡혔다.**
        //    (테스트가 빨갰던 이유가 코드가 아니라 검사였다.)
        assertTrue(
            "PhotoLoader.load가 withContext(Dispatchers.IO) 안에 없다",
            Regex("""withContext\(Dispatchers\.IO\)\s*\{[\s\S]{0,300}?PhotoLoader\.load\(""")
                .containsMatchIn(body),
        )

        // 첫 프레임은 캐시 조회(메모리)로 그린다 — 없으면 스크롤 되돌릴 때 일러스트가 번쩍인다.
        assertTrue(
            "첫 프레임을 PhotoLoader.cached로 그리지 않는다 — 스크롤 되돌리면 번쩍인다",
            body.contains("PhotoLoader.cached("),
        )
    }

    /**
     * **위 검사가 실제로 주석을 걷어내고 보는지** 스스로 확인한다.
     *
     * 🔴 이 저장소에서 **주석이 증거로 세어진 적이 두 번** 있다. 지금 `FlowerIllust.kt`의
     *    KDoc에는 `remember { PhotoLoader.load(...) }`라는 **금지된 형태가 설명으로
     *    적혀 있다** — [bodyOf]가 주석을 안 지우면 위 단정이 **영원히 빨갛다.**
     */
    @Test
    fun 금지형태가_주석에만_있으면_통과한다() {
        val raw = File("src/main/java/com/catchflower/app/ui/component/FlowerIllust.kt").readText()
        val pattern = Regex("""remember\([^)]*\)\s*\{[^}]*PhotoLoader\.load\(""")
        assertTrue(
            "KDoc에 금지 형태가 없어졌다 — 이 검사가 무의미해졌으니 지워도 된다",
            raw.contains("remember { PhotoLoader.load("),
        )
        assertTrue(
            "bodyOf가 주석을 걷어내지 못했다 — 주석 속 예시가 위반으로 잡힌다",
            !pattern.containsMatchIn(bodyOf(File("src/main/java/com/catchflower/app/ui/component/FlowerIllust.kt"))),
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
