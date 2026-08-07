package com.catchflower.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 위치 권한을 **한 번만** 묻는가.
 *
 * ⚠️ JVM에서는 못 한다 — `SharedPreferences`와 `checkSelfPermission`이 둘 다
 *    실물 컨텍스트를 요구한다. 그래서 기기 테스트다.
 *
 * ⚠️ 실제 권한 대화상자를 띄우지 않는다. 여기서 검증하는 것은
 *    **"묻기로 결정하는 규칙"**이고, 그게 틀리면 촬영마다 대화상자가 뜨거나
 *    (거부한 사람에게는) 아무 반응 없는 셔터가 된다.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionPromptTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = LocationPermissionPrompt.resetForTest(context)

    @After
    fun tearDown() = LocationPermissionPrompt.resetForTest(context)

    /**
     * 한 번 물으면 다시 묻지 않는다.
     *
     * 빨개지는 경우: `markAsked`가 저장을 안 하거나 `shouldAsk`가 플래그를 안 보면.
     * 그러면 **촬영마다 시스템 대화상자가 뜬다** — 거부하고 "다시 묻지 않음" 상태인
     * 사용자에게는 대화상자가 실제로 안 떠서 **셔터가 반응 없는 것처럼 보인다.**
     */
    @Test
    fun 한_번_물으면_다시_묻지_않는다() {
        // ⚠️ `hasPermission = false`를 **넣어 준다.** AGP는 계측 APK를 `-g`로 설치해서
        //    실제 권한 상태는 늘 "허용"이다 — 실제 값을 읽으면 이 테스트는 첫 줄에서
        //    끝나고 "한 번만 묻는다"를 한 번도 확인하지 않는다.
        assertTrue(
            "첫 촬영에서 위치를 묻지 않는다 — 장소가 영구히 안 붙고 B-6 랭킹이 죽는다",
            LocationPermissionPrompt.shouldAsk(context, hasPermission = false),
        )
        LocationPermissionPrompt.markAsked(context)
        assertFalse(
            "두 번째 촬영에서 또 묻는다",
            LocationPermissionPrompt.shouldAsk(context, hasPermission = false),
        )
    }

    /**
     * 권한이 이미 있으면 묻지 않는다.
     *
     * 빨개지는 경우: 권한 확인을 빼고 플래그만 보면. 그러면 설정에서 직접 허용한
     * 사용자에게 한 번 더 대화상자가 뜬다 (이미 허용된 권한은 즉시 콜백만 오고
     * 화면에는 아무 일도 안 일어나는 것처럼 보인다).
     */
    @Test
    fun 권한이_이미_있으면_묻지_않는다() {
        assertFalse(
            "권한이 있는데 또 묻는다",
            LocationPermissionPrompt.shouldAsk(context, hasPermission = true),
        )
    }

    /**
     * 플래그는 **프로세스를 넘어 남는다.**
     *
     * 빨개지는 경우: 메모리 변수로 기억하면. 앱을 껐다 켜면 초기화되어
     * 실행마다 한 번씩 다시 묻는다 — 하루에 여러 번 쓰는 앱에서는 매번이나 같다.
     */
    @Test
    fun 물어봤다는_사실이_저장에_남는다() {
        LocationPermissionPrompt.markAsked(context)
        val fresh = context.getSharedPreferences("catchflower", android.content.Context.MODE_PRIVATE)
        assertTrue(
            "SharedPreferences에 남지 않았다 — 앱 재시작마다 다시 묻는다",
            fresh.getBoolean("location_permission_asked", false),
        )
    }
}
