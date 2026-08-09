package com.catchflower.app.recognizer

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **QA 우회 스위치가 기본으로 닫혀 있고, 켰을 때만 열리는지** 검증한다.
 *
 * **왜 이 검사가 필요한가.** 이 기능은 성질상 **켜 두면 아무 증상이 없다** —
 * 화면 12는 "1차 필터가 막았다"와 "유료 호출이 실패했다"를 똑같이 보여주므로((39)),
 * 우회가 켜진 채 배포돼도 **화면으로는 알 수 없다.** 알 수 있는 곳은 로그와 이 검사뿐이다.
 *
 * ⚠️ **`enabled`를 직접 부르는 테스트는 여기서 거의 못 쓴다.** `BuildConfig.DEBUG`가
 *    JVM 단위 테스트에서 `true`이므로 파일 존재 여부만 남고, 그러면
 *    `/data/local/tmp`가 없는 맥에서는 **꺼진 경로만** 검증하게 된다. 그래서
 *    [QaBypassPreFilter]가 판정 함수를 주입받게 만들고 켠 상태를 여기서 재현한다.
 */
class QaPreFilterSwitchTest {

    /** 항상 "꽃 아님"으로 막는 필터. 실기기에서 화면 사진을 찍었을 때와 같은 결과다. */
    private val blocking = object : FlowerPreFilter {
        override suspend fun check(jpeg: ByteArray) = PreFilterResult(
            isLikelyFlower = false,
            topLabel = "Pattern",
            matchedLabel = null,
            confidence = 0.83f,
            elapsedMillis = 12L,
        )
    }

    /** 통과시키는 필터. */
    private val passing = object : FlowerPreFilter {
        override suspend fun check(jpeg: ByteArray) = PreFilterResult(
            isLikelyFlower = true,
            topLabel = "Flower",
            matchedLabel = "Flower",
            confidence = 0.71f,
            elapsedMillis = 12L,
        )
    }

    @Test
    fun `꺼져 있으면 막은 판정을 그대로 넘긴다`() = runBlocking {
        val result = QaBypassPreFilter(blocking, enabled = { false }).check(ByteArray(1))
        assertFalse("우회가 꺼져 있는데 통과시켰다", result.isLikelyFlower)
        // 🔴 라벨까지 손대지 않아야 한다 — `top=Pattern`이 실기기 진단의 유일한 단서였다.
        assertEquals("Pattern", result.topLabel)
    }

    @Test
    fun `켜져 있으면 막은 사진을 통과시킨다`() = runBlocking {
        val result = QaBypassPreFilter(blocking, enabled = { true }).check(ByteArray(1))
        assertTrue("우회가 켜져 있는데 막았다 — QA로 도감 등록을 확인할 수 없다", result.isLikelyFlower)
    }

    /**
     * 🔴 **우회한 촬영은 로그만 보고 구분할 수 있어야 한다.**
     *
     * 안 그러면 우회로 통과한 촬영과 정상 통과한 촬영이 `1차필터:` 줄에서 똑같이 보이고,
     * QA 결과를 나중에 볼 때 **"이건 원래도 통과했을 사진인가"에 답할 수 없다.**
     */
    @Test
    fun `우회로 통과한 사진은 라벨에 표시가 남는다`() = runBlocking {
        val bypassed = QaBypassPreFilter(blocking, enabled = { true }).check(ByteArray(1))
        val normal = QaBypassPreFilter(passing, enabled = { true }).check(ByteArray(1))

        assertTrue(
            "우회 표시가 없다 (topLabel=${bypassed.topLabel}) — 로그에서 정상 통과와 구분되지 않는다",
            bypassed.topLabel!!.startsWith("QA우회"),
        )
        assertTrue(
            "원래 라벨이 사라졌다 (${bypassed.topLabel}) — 왜 막혔는지가 QA 중 가장 알고 싶은 정보다",
            bypassed.topLabel!!.contains("Pattern"),
        )
        assertNotEquals("정상 통과에도 우회 표시가 붙었다", bypassed.topLabel, normal.topLabel)
        assertEquals("정상 통과한 사진의 라벨을 건드렸다", "Flower", normal.topLabel)
    }

    /**
     * **켜져 있어도 통과할 사진은 실제 필터의 결과를 쓴다.**
     *
     * 우회를 `AlwaysPassPreFilter`로 구현했다면 이 검사가 빨개진다 —
     * 그 구현은 필터를 아예 돌리지 않아 `matched`·`conf`·`ms`가 전부 가짜가 되고,
     * **QA 중에 1차 필터가 실제로 어떻게 동작하는지 관찰할 수 없게 된다.**
     */
    @Test
    fun `우회 중에도 실제 필터가 돈다`() = runBlocking {
        val result = QaBypassPreFilter(blocking, enabled = { true }).check(ByteArray(1))
        assertEquals("실제 필터의 신뢰도가 사라졌다", 0.83f, result.confidence, 0.0001f)
        assertEquals("실제 필터의 소요 시간이 사라졌다", 12L, result.elapsedMillis)
    }

    /** 스위치 파일이 없으면 닫혀 있다. 맥에는 `/data/local/tmp`가 없으므로 여기는 항상 이 상태다. */
    @Test
    fun `스위치 파일이 없으면 닫혀 있다`() {
        if (File(QaPreFilterSwitch.PATH).exists()) return // 실기기·에뮬 환경에서 돌 때는 건너뛴다
        assertFalse("스위치 파일이 없는데 열려 있다", QaPreFilterSwitch.enabled)
    }

    /**
     * 🔴 **`preFilter`에 `AlwaysPassPreFilter`가 박히지 않았는지** 원본을 읽어 확인한다.
     *
     * 이건 세션 규칙이다 — "`preFilter = AlwaysPassPreFilter`를 그대로 두지 않는다".
     * 한 번 그렇게 두면 **모든 사진이 유료 API로 가는데 테스트는 전부 초록**이다.
     *
     * ⚠️ **이 검사는 `.kt`가 바뀔 때만 확실히 돈다** — Gradle은 소스를 입력으로 알지만
     *    문서·리소스는 모른다(메모리 `gradle-file-reading-tests-not-inputs`).
     *    여기서 읽는 대상이 `.kt`라서 성립한다. **다른 확장자로 옮기지 않는다.**
     */
    @Test
    fun `촬영 화면이 무조건 통과 필터를 쓰지 않는다`() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java").isDirectory) dir = dir.parentFile
        val root = requireNotNull(dir) { "android 모듈 루트를 못 찾았다" }

        val vm = File(root, "app/src/main/java/com/catchflower/app/ui/capture/CaptureViewModel.kt")
        assertTrue("CaptureViewModel.kt가 없다 — 경로가 바뀌었으면 이 검사도 옮긴다", vm.isFile)

        val code = vm.readText().lineSequence()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .joinToString("\n")

        assertFalse(
            "CaptureViewModel이 AlwaysPassPreFilter를 쓴다 — 모든 사진이 유료 API로 간다",
            "AlwaysPassPreFilter" in code,
        )
        assertTrue(
            "1차 필터가 MlKitFlowerPreFilter가 아니다 — 기본값이 QA 우회로 바뀌었는가",
            "MlKitFlowerPreFilter()" in code,
        )
    }
}
