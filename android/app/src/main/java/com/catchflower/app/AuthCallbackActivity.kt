package com.catchflower.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.KakaoLinkLauncher
import com.catchflower.app.ui.component.showToastNow

/**
 * `catchflower://auth-callback`을 받는다. **화면이 없다** — 받아서 표시하고 [MainActivity]로 넘긴다.
 *
 * ## 🔴 왜 [MainActivity]에 intent-filter를 붙이지 않았나
 *
 * 붙이려면 `launchMode="singleTask"`가 필요하고, 그건 **앱 전체의 태스크 동작을
 * 바꾼다** — 다른 앱에서 공유로 돌아오는 경로·백스택·`onNewIntent` 처리가 전부
 * 영향을 받는다. 로그인 콜백 하나 때문에 그 위험을 지지 않는다.
 * 여기는 `noHistory=true`라 최근 앱 목록에도 남지 않는다.
 *
 * ## 🔴 인텐트가 왔다는 것은 성공의 증거가 아니다
 *
 * GoTrue는 **실패도 같은 주소로** 돌려보낸다(`#error=…`). 판정은
 * [com.catchflower.app.data.KakaoLinkService.callbackSucceeded]가 한다 —
 * 여기서 `if (data != null)`로 다시 쓰면 **연결이 안 됐는데 게이트가 열린다.**
 *
 * ## 🔵 결과를 **말한다** (2026-08-16 · 오너가 문구 작성을 위임했다)
 *
 * 그전까지는 성공해도 아무 말도 하지 않았다(A 문서에 문구가 없어서 지어내지 않았다 ·
 * 4절 22번). 🔴 문제는 **실패했을 때와 화면이 똑같다는 것**이었다 — 사용자는 연결됐는지
 * 모르는 채로 다시 촬영해 보고, 시트가 뜨는지로 결과를 알아냈다.
 * 지금은 [CfToast.LOGIN_LINKED] / [CfToast.LOGIN_NOT_LINKED]를 띄운다.
 *
 * ⚠️ **성공/실패를 `ok` 하나로 갈라 띄운다.** 원인별 문구를 만들지 않았다 —
 *    `callbackSucceeded`가 취소와 서버 설정 오류를 **구분해 주지 않기 때문**이고,
 *    구분 못 하는 것을 구분한 듯 말하면 틀린 안내가 된다([CfToast.LOGIN_NOT_LINKED]).
 */
class AuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⚠️ `intent.data`를 문자열로만 넘긴다. 파싱은 `callbackSucceeded`가 하고,
        //    그 함수는 `android.net.Uri`를 쓰지 않는다 — JVM 테스트에서 죽기 때문이다.
        val linked = KakaoLinkLauncher.onCallback(this, intent?.data?.toString())

        // 🔴 **결과를 여기서 말한다.** 이 액티비티는 곧 `finish()`되지만 토스트는
        //    시스템 창이라 그 뒤에도 뜬다([showToastNow] 주석).
        showToastNow(this, if (linked) CfToast.LOGIN_LINKED else CfToast.LOGIN_NOT_LINKED)

        // 앱으로 되돌린다. **새 태스크를 만들지 않는다**(`CLEAR_TOP`이면 이미 떠 있는
        // MainActivity가 앞으로 온다) — 안 그러면 앱이 두 개 열린 것처럼 보인다.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
