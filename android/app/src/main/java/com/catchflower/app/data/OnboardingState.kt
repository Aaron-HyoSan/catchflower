package com.catchflower.app.data

import android.content.Context

/**
 * 온보딩(화면 01~03)을 한 번만 보여준다.
 *
 * **왜 별도 플래그인가.** "권한이 다 허용됐으면 온보딩을 건너뛴다"로 쓰면 안 된다.
 * 화면 03은 권한 게이트가 아니라 **약속을 보여주는 화면**이다
 * (`촬영한 사진은 내 도감에만 저장됩니다` · `지도 공유는 매번 직접 선택해요` —
 * 와이어프레임 주석 ④: "'자동 공개가 아니다'를 가입 단계에서 못 박는다").
 * 권한 상태로 분기하면 **재설치 후 권한이 남아 있는 사용자는 이 약속을 한 번도 못 본다.**
 */
object OnboardingState {
    private const val PREFS = "catchflower"
    private const val KEY = "onboarding_done"

    fun isDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY, false)

    /**
     * ⚠️ **위치도 "물어봤다"로 함께 표시한다.** 안 하면 온보딩에서 위치를 거부한 사람에게
     *    촬영 화면([com.catchflower.app.ui.capture.CameraScreen])이 **한 번 더 묻는다** —
     *    화면 03의 `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`가
     *    안내가 아니라 강요가 된다. 두 화면이 같은 플래그를 공유해야 한다.
     */
    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
        LocationPermissionPrompt.markAsked(context)
    }

    internal fun resetForTest(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
