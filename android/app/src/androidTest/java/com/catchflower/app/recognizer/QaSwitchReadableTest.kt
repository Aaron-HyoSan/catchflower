package com.catchflower.app.recognizer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 🔴 **QA 스위치 파일을 앱이 실제로 읽을 수 있는가.** 계측 테스트여야만 답이 나온다.
 *
 * **왜 JVM 테스트로는 안 되는가.** `File.exists()`가 맥에서 도는 것과
 * **앱 UID·SELinux 도메인(`untrusted_app`)에서 `/data/local/tmp`를 읽는 것**은 다른 문제다.
 * `adb shell run-as`로 확인한 것도 근거가 약하다 — `run-as`는 UID만 바꾸고
 * **SELinux 도메인은 shell 쪽에 가깝게 남는다.** 그래서 "앱에서도 읽힌다"의 증거가 못 된다.
 * 이 테스트는 앱과 같은 프로세스 컨텍스트에서 돌아 그 차이를 없앤다.
 *
 * ⚠️ **네트워크·유료 호출이 없다.** 파일 하나만 본다. 그래서
 *    `/data/local/tmp/cf_run_network_tests` 잠금이 필요 없다.
 *
 * 돌리는 법 — 스위치를 켠 채로 돌려야 켠 경로가 검증된다:
 * ```
 * adb shell touch /data/local/tmp/cf_qa_allow_any_photo
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.catchflower.app.recognizer.QaSwitchReadableTest
 * adb shell rm /data/local/tmp/cf_qa_allow_any_photo
 * ```
 */
@RunWith(AndroidJUnit4::class)
class QaSwitchReadableTest {

    /**
     * 스위치 파일이 있으면 앱이 그것을 **본다.**
     *
     * ⚠️ 파일이 없으면 이 테스트는 **꺼져 있음**만 확인한다. 그 상태로 초록이 뜬 것을
     *    "읽을 수 있다"로 읽지 않게 로그에 어느 쪽을 검증했는지 남긴다 —
     *    조건부로 건너뛰는 검사는 **아무것도 안 하고 초록**이 될 수 있다.
     */
    @Test
    fun qaSwitchFileIsVisibleToApp() {
        val present = File(QaPreFilterSwitch.PATH).exists()
        Log.i(
            "CatchFlower",
            "QA 스위치 검증: 파일 ${if (present) "있음 → 켠 경로" else "없음 → 꺼진 경로"} 확인",
        )

        if (present) {
            assertTrue(
                "스위치 파일이 있는데 앱에서 열리지 않았다 — SELinux나 권한 때문에 " +
                    "이 방식으로는 QA 우회를 켤 수 없다. 다른 경로를 써야 한다",
                QaPreFilterSwitch.enabled,
            )
        } else {
            assertFalse("스위치 파일이 없는데 열려 있다", QaPreFilterSwitch.enabled)
        }
    }

    /** 우회를 켠 상태에서 막힌 판정이 통과로 뒤집히는가 — 실기기 클래스로 확인한다. */
    @Test
    fun bypassFlipsBlockedResultWhenEnabled() {
        val blocking = object : FlowerPreFilter {
            override suspend fun check(jpeg: ByteArray) = PreFilterResult(
                isLikelyFlower = false, topLabel = "Pattern", matchedLabel = null,
                confidence = 0.8f, elapsedMillis = 1L,
            )
        }
        val result = kotlinx.coroutines.runBlocking {
            QaBypassPreFilter(blocking, enabled = { true }).check(ByteArray(1))
        }
        assertTrue("우회가 막힌 판정을 뒤집지 못했다", result.isLikelyFlower)
        assertTrue("우회 표시가 없다", result.topLabel!!.startsWith("QA우회"))
    }
}
