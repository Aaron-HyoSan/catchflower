package com.catchflower.app.ui.component

import android.content.Context
import com.catchflower.app.data.AnonymousUsage
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.KakaoLinkService
import com.catchflower.app.data.KakaoLogin

/**
 * 카카오 연결을 **시작하고 돌아온 것을 받는다.** 지금 시작점은 로그인 시트
 * ([LoginGateSheet]) **하나뿐이다** — 설정의 `계정 연결하기` 행은 만들지 않았다
 * (그 행 이름이 A 문서에 없다 · 4절 23번). 막힌 액션을 누르면 어디서든 시트가 뜨므로
 * 로그인으로 갈 길은 있다.
 *
 * ## 왜 한 곳인가
 *
 * 시작(브라우저 열기)과 마무리(콜백 → [AnonymousUsage.markKakaoLinked])는 **짝이다.**
 * 둘이 다른 파일에 흩어지면 한쪽만 고쳐지고, 그때 증상은 **"로그인은 됐는데
 * 게이트가 계속 걸린다"** 또는 그 반대다 — 둘 다 원인이 화면에 안 보인다.
 *
 * ⚠️ **호출 순서를 지킨다.** 브라우저를 열기 **전에** 연결 성공으로 표시하지 않는다.
 *    사용자가 카카오 화면에서 뒤로 가면 연결은 안 됐는데 게이트가 열린다.
 */
object KakaoLinkLauncher {

    sealed interface Result {
        /** 브라우저를 열었다. 나머지는 콜백([onCallback])이 마무리한다. */
        data object Opened : Result

        /**
         * 시작하지 못했다.
         *
         * ⚠️ **문구를 여기서 고르지 않는다.** `manual_linking_disabled` 같은 원인은
         *    다시 시도해도 결과가 같아서 `잠시 후 다시 시도해 주세요`가 **틀린
         *    안내**가 된다([CfToast.COMMENT_DELETE_FAILED]에서 한 판단과 같다).
         *    지금 A 문서에 이 실패용 문구가 없어서 [CfToast.NETWORK_ERROR]를 쓰되,
         *    원인은 로그로 남긴다(A 문서 4절 19번에 함께 올렸다).
         */
        data class Failed(val reason: KakaoLogin.LinkFailure) : Result

        /** 브라우저가 없는 기기다. */
        data object NoBrowser : Result
    }

    /**
     * ①②③ — 토큰으로 연결 주소를 받아 브라우저를 연다.
     *
     * 🔴 **suspend다.** 토큰 갱신과 HTTP가 들어 있다. 메인 스레드에서 부르면
     *    `NetworkOnMainThreadException`으로 앱이 죽는다.
     */
    suspend fun start(
        context: Context,
        service: KakaoLinkService = KakaoLinkService(
            log = { android.util.Log.i("CatchFlower", it) },
        ),
    ): Result {
        // 익명 세션의 토큰이다. 🔴 **이 토큰이 있는 계정에 identity가 붙는다** —
        // 즉 도감의 주인이 그대로 유지된다(`SupabaseAuthUrls.linkIdentity` 주석).
        val token = DiscoveryRepository.get(context).auth?.accessToken()
        if (token.isNullOrEmpty()) {
            android.util.Log.w("CatchFlower", "카카오 연결: 익명 세션 토큰이 없다")
            return Result.Failed(KakaoLogin.LinkFailure.UNKNOWN)
        }
        return when (val start = service.start(token)) {
            is KakaoLinkService.Start.Failed -> Result.Failed(start.reason)
            is KakaoLinkService.Start.OpenBrowser ->
                if (ExternalOpen.browser(context, start.url)) Result.Opened else Result.NoBrowser
        }
    }

    /**
     * ⑤ — `catchflower://auth-callback`으로 돌아왔다.
     *
     * 🔴 **인텐트가 왔다는 것이 성공의 증거가 아니다.** GoTrue는 실패도 같은 주소로
     *    돌려보낸다([KakaoLinkService.callbackSucceeded]). 그래서 판정을 그 함수에
     *    두고 여기서 `if (uri != null)`로 다시 쓰지 않는다.
     *
     * @return 연결됐으면 true. 호출부가 화면을 갱신한다.
     */
    fun onCallback(context: Context, uri: String?): Boolean {
        val ok = KakaoLinkService.callbackSucceeded(uri)
        // ⚠️ **주소 전체를 로그에 찍지 않는다** — 프래그먼트에 토큰이 섞여 온다.
        android.util.Log.i("CatchFlower", "카카오 콜백: ${if (ok) "연결됨" else "실패 또는 취소"}")
        if (ok) AnonymousUsage.get(context).markKakaoLinked()
        return ok
    }
}
