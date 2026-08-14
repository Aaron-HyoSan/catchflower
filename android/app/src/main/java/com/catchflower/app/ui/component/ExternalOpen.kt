package com.catchflower.app.ui.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.catchflower.app.core.ShareText

/**
 * **다른 앱을 여는 자리**를 한곳에 모았다. 공유 시트 · 지도 길찾기 · 시스템 알림 설정 ·
 * 메일 앱이 전부 여기를 지난다.
 *
 * 2026-08-13에 죽은 버튼 14개를 실제 동작으로 바꾸면서 생겼다(A 문서 3절
 * `죽은 버튼 14개를 실제 기능으로 바꾸며 생긴 문구`).
 *
 * ## 🔴 `resolveActivity()`로 열 수 있는지 판단하지 않는다
 *
 * `targetSdk = 36`이라 **패키지 가시성 필터**가 걸린다(안드로이드 11+). 매니페스트에
 * `<queries>`가 없으면 `resolveActivity()`·`queryIntentActivities()`가
 * **카카오맵이 깔려 있어도 null을 준다.** 그 값으로 판단하면:
 *
 * - `길찾기`를 눌렀을 때 **설치된 카카오맵을 열지 않고** `지도 앱을 열 수 없어요`를 띄운다.
 * - 즉 버튼은 "동작"하고(토스트가 뜬다) 아무도 고장이라고 생각하지 않는다.
 *   죽은 버튼을 지웠다고 적어 놓고 **다른 종류의 죽은 버튼**을 만드는 모양이다.
 * - 개발 기기에 지도 앱이 없으면 증상이 "원래 그런 것"으로 보인다.
 *
 * 그래서 **실제로 [Context.startActivity]를 부르고 [ActivityNotFoundException]을 받는다.**
 * 암시적 인텐트 실행 자체는 가시성 필터의 대상이 아니다 — 필터는 *조회*를 막는다.
 *
 * ## 왜 Boolean을 돌려주는가
 *
 * ⚠️ 여기서 토스트를 띄우지 않는다. 실패 문구가 자리마다 다르고
 * ([CfToast.SHARE_NO_APP] · [CfToast.MAP_NO_APP]), 이 파일이 문구를 고르기 시작하면
 * A 문서와의 대응이 화면에서 안 보이게 된다.
 */
object ExternalOpen {

    /**
     * 공유 시트를 띄운다. 화면 05 `공유` · 19 `초대 링크 보내기` · 21 `결과 공유하기`.
     *
     * ⚠️ **[Intent.createChooser]로 감싼다.** 안 감싸면 "항상 이 앱으로" 기본값이 잡힌
     * 기기에서 **선택 없이 한 앱으로 바로 나간다** — 카카오톡으로 보내려던 사람이
     * 메모 앱에 붙는다.
     *
     * @return 시트를 띄웠으면 true. false면 [CfToast.SHARE_NO_APP].
     */
    fun share(context: Context, text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return start(context, Intent.createChooser(send, ShareText.CHOOSER_TITLE))
    }

    /**
     * 주소 목록을 **앞에서부터** 열어 보고 처음 성공한 것에서 멈춘다.
     * 화면 15 `길찾기`가 [com.catchflower.app.core.MapLinks.routeChain]을 넘긴다.
     *
     * @return 하나라도 열렸으면 true. false면 [CfToast.MAP_NO_APP].
     */
    fun firstThatOpens(context: Context, urls: List<String>): Boolean =
        urls.any { start(context, Intent(Intent.ACTION_VIEW, Uri.parse(it))) }

    /**
     * 브라우저로 주소 하나를 연다. 카카오 로그인(웹 OAuth)이 쓴다.
     *
     * ⚠️ **[firstThatOpens]를 돌려쓰지 않는다.** 저쪽은 지도 앱 후보를 순서대로
     *    시도하는 함수라 "하나라도 열리면 성공"이고, 로그인은 **그 한 주소가 열려야**
     *    한다. 이름이 하는 일을 말해야 호출부가 실패를 옳게 다룬다.
     *
     * ⚠️ 커스텀 탭을 쓰지 않는다 — `androidx.browser` 의존성이 늘고, 실패하면
     *    어차피 여기로 내려온다. 로그인 후 앱으로 돌아오는 것은 브라우저가 아니라
     *    `catchflower://auth-callback` intent-filter가 한다.
     *
     * @return 열었으면 true. false면 브라우저가 없는 기기다.
     */
    fun browser(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /**
     * 이 앱의 **시스템 알림 설정**을 연다. 화면 20 `알림 설정`.
     *
     * 🔴 **우리 화면에 토글을 만들지 않는다.** 앱이 보내는 알림이 하나도 없어서
     *    켜고 끌 대상이 없다 — 자체 스위치는 아무 것도 하지 않는 스위치가 된다
     *    (`배지 3개` 더미를 지운 것과 같은 이유). OS 화면의 그 스위치는 **실제로 있다.**
     *
     * ⚠️ 실패하면 앱 정보 화면으로 내려간다. 거기에도 알림 항목이 있어서
     *    **사용자가 하려던 일을 끝낼 수 있다** — 토스트로 끝내는 것보다 낫다.
     */
    fun notificationSettings(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (start(context, direct)) return true
        val details = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        return start(context, details)
    }

    /**
     * 메일 앱을 연다. 화면 20 `고객문의`.
     *
     * ⚠️ **`ACTION_SENDTO` + `mailto:`다.** `ACTION_SEND`로 열면 카카오톡·SNS까지
     *    후보에 뜨고, 사용자는 문의를 **친구에게 보낸다.**
     *
     * @param address 빈 문자열이면 아무 것도 하지 않고 false. 부르는 쪽은 애초에
     *   주소가 없으면 **행 자체를 그리지 않는다**(받는 사람이 빈 메일 앱이 열리면
     *   보낸 사람은 접수됐다고 믿는다 · 4절 17번).
     */
    fun email(context: Context, address: String): Boolean {
        if (address.isBlank()) return false
        val mail = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
        return start(context, mail)
    }

    /**
     * ⚠️ [SecurityException]도 받는다. 일부 제조사 롬은 설정 화면을 여는 인텐트에
     *    권한을 요구해서 **`ActivityNotFoundException`이 아니라 이걸 던진다** — 안 잡으면
     *    `알림 설정`을 누르는 순간 앱이 죽는다(그 기기에서만, 100% 재현).
     */
    private fun start(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            android.util.Log.i("CatchFlower", "외부 앱 없음: ${intent.action} ${intent.data} $e")
            false
        } catch (e: SecurityException) {
            android.util.Log.i("CatchFlower", "외부 앱 거부: ${intent.action} ${intent.data} $e")
            false
        }
}
