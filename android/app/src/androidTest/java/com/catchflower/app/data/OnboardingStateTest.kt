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
 * 온보딩(화면 03)과 촬영 화면이 **같은 플래그를 공유하는가.**
 *
 * ⚠️ 이게 안 맞으면 **온보딩에서 위치를 거부한 사람이 첫 촬영에서 또 시스템 대화상자를
 *    본다.** 화면 03의 `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`가
 *    안내가 아니라 강요가 된다. 그리고 두 번 거부하면 안드로이드가 그 권한을 **영구히
 *    못 묻게** 만들어서, 나중에 지도 기능을 켜고 싶어도 설정 앱까지 들어가야 한다.
 *
 * **화면으로는 확인이 거의 불가능하다** — 첫 실행에서만, 그것도 거부한 경우에만 보인다.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingStateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        OnboardingState.resetForTest(context)
        LocationPermissionPrompt.resetForTest(context)
    }

    @After
    fun tearDown() {
        OnboardingState.resetForTest(context)
        LocationPermissionPrompt.resetForTest(context)
    }

    @Test
    fun 첫_실행에는_온보딩을_보여준다() {
        assertFalse(OnboardingState.isDone(context))
    }

    @Test
    fun 한_번_끝내면_다시_보여주지_않는다() {
        OnboardingState.markDone(context)
        assertTrue(OnboardingState.isDone(context))
    }

    /**
     * 이 테스트가 이 파일의 이유다.
     *
     * ⚠️ `hasPermission = false`를 **명시로 넘긴다.** AGP는 계측 APK를 `-g`(런타임 권한
     *    전부 허용)로 설치하므로, 기본값을 쓰면 `shouldAsk`가 권한 있음 분기로 조기
     *    반환해서 **플래그를 한 번도 안 보고 초록이 된다.**
     */
    @Test
    fun 온보딩이_끝나면_촬영_화면은_위치를_다시_묻지_않는다() {
        assertTrue(
            "온보딩 전에는 촬영 화면이 물어야 한다 (이게 false면 아래 검사가 무의미하다)",
            LocationPermissionPrompt.shouldAsk(context, hasPermission = false),
        )

        OnboardingState.markDone(context)

        assertFalse(
            "온보딩에서 이미 물었는데 촬영 화면이 또 묻는다 — 두 번 거부하면 영구히 못 묻는다",
            LocationPermissionPrompt.shouldAsk(context, hasPermission = false),
        )
    }
}
