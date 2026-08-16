package com.catchflower.app.ui.component

import android.content.Context
import android.content.Intent

/**
 * 앱을 **처음 상태로** 되돌린다. 지금 쓰는 곳은 회원 탈퇴가 끝난 직후 하나뿐이다.
 *
 * ## 🔴 왜 화면만 되돌리면 안 되는가
 *
 * 탈퇴는 기기 파일과 prefs를 지우지만 **메모리는 안 지운다.** `Activity.recreate()`나
 * 탭 이동으로 되돌리면 `ViewModel`이 살아남아(설정 변경 경로라 `ViewModelStore`가
 * 유지된다) 도감·랭킹·내 정보가 **지워지기 전 값 그대로** 다시 그려진다 —
 * 사용자에게는 "탈퇴했는데 내 꽃이 그대로 있다"로 보이고, 그건 서버에 남았다는 뜻으로
 * 읽힌다. 실제로는 캐시인데 **구별할 방법이 없다.**
 *
 * 그래서 **프로세스를 죽이고 다시 띄운다.** 지금 이 앱에 메모리를 통째로 비우는 다른
 * 방법이 없다(싱글턴 캐시가 `FlowerRepository`·`DiscoveryRepository`·`AnonymousUsage`에
 * 흩어져 있다 — 하나씩 비우는 함수를 만들면 **다음에 생기는 캐시가 빠진다**).
 *
 * ⚠️ 이 함수 뒤의 코드는 실행되지 않는다. 부르기 전에 할 일을 다 끝내야 한다.
 */
object AppRestart {

    fun toFirstRun(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val component = launch?.component
        if (component != null) {
            // `makeRestartActivityTask`가 `CLEAR_TASK|NEW_TASK`를 붙여 **작업 스택을
            // 비운다.** 안 비우면 죽기 전 화면들이 뒤에 남아 있다가 되살아난다.
            context.startActivity(Intent.makeRestartActivityTask(component))
        }
        // 🔴 실행 아이콘을 못 찾아도 **종료는 한다.** 그대로 두면 계정이 없는 앱을
        //    계속 쓰게 되고, 그 화면은 전부 빈 값·실패다(원인이 화면에 안 보인다).
        Runtime.getRuntime().exit(0)
    }
}
