package com.catchflower.app.data

import android.content.Context

/**
 * 위치 권한을 **한 번만** 묻는다.
 *
 * **왜 별도 상태가 필요한가.** 안드로이드는 "이 권한을 물어본 적이 있는가"를
 * 알려주지 않는다. `shouldShowRequestPermissionRationale`은 **거부 후에만** true라서
 * "아직 안 물어봄"과 "거부하고 다시 묻지 말라고 했음"이 **둘 다 false**로 같다.
 * 그 값으로 분기하면 후자에게 촬영마다 시스템 대화상자가 뜨는데,
 * 대화상자는 실제로 나타나지도 않아 사용자에게는 **아무 반응 없는 셔터**가 된다.
 *
 * ⚠️ 화면 03(권한 안내)이 붙으면 **묻는 자리가 그쪽으로 옮겨진다.** 그때 이 플래그를
 *    그대로 쓰면 온보딩에서 이미 물은 사람에게 촬영 화면이 다시 묻지 않는다 —
 *    화면 03도 [markAsked]를 부르면 된다. 지금은 온보딩이 없어서 촬영 화면이 대신 묻는다.
 */
object LocationPermissionPrompt {
    private const val PREFS = "catchflower"
    private const val KEY = "location_permission_asked"

    /**
     * 이미 권한이 있거나 한 번 물어봤으면 묻지 않는다.
     *
     * ⚠️ [hasPermission]을 **인자로 받는다.** 안에서 직접 읽으면 이 규칙을 테스트할 수
     *    없다 — 계측 테스트는 AGP가 APK를 `-g`(런타임 권한 전부 허용)로 설치하므로
     *    **항상 권한 있음 분기로만 들어간다.** 처음에 그렇게 써서 테스트가 초록인데
     *    "한 번만 묻는다"를 한 번도 확인하지 않았다. (진행 (22) 교훈의 반복이다.)
     */
    fun shouldAsk(
        context: Context,
        hasPermission: Boolean = PlatformLocationSource.hasPermission(context),
    ): Boolean {
        if (hasPermission) return false
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    /**
     * ⚠️ **요청을 띄우기 전에 부른다.** 결과 콜백에서 기록하면, 사용자가 대화상자를
     *    바깥 탭으로 닫아 콜백이 오지 않는 경우 플래그가 안 남아 다음 촬영에서 또 뜬다.
     */
    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }

    internal fun resetForTest(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
