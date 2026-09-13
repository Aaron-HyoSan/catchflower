package com.catchflower.app.core

/**
 * 공유 시트로 **앱 밖으로 나가는 문장**을 만든다.
 *
 * 원본은 `디자이너_업무/A_문구·버튼_스펙.md` 3절
 * `죽은 버튼 14개를 실제 기능으로 바꾸며 생긴 문구` ①이다.
 *
 * ## 왜 화면이 아니라 여기서 만드는가
 *
 * 🔴 **이 세 줄은 우리 화면에 안 보인다.** 카카오톡·문자·메일에 붙어서 **사용자
 *    이름으로 남에게 전달**되는데, 화면 스크린샷을 아무리 봐도 확인할 수 없다.
 *    `Text(...)`가 아니므로 [com.catchflower.app.ui.CopySourceTest]도 못 본다 —
 *    그래서 값을 만드는 층에서 재는 것이 유일한 방법이다([ShareTextTest]).
 *
 * ## 못 만들면 null이다
 *
 * ⚠️ **빈 문장을 만들어 내보내지 않는다.** 꽃 이름을 모르거나 종수를 아직 못 셌으면
 *    [flower]·[seasonResult]가 null을 주고, **부르는 쪽은 버튼을 그리지 않는다.**
 *    `캐치플라워에서  모았어요`나 `꽃 0종을 모았어요`가 남의 대화창에 남는 것보다
 *    버튼이 없는 것이 낫다(A 문서 3절 ① 주의문).
 */
object ShareText {

    /**
     * 공유 시트 제목. A 문서 3절 ①: `공유 시트 제목 | 공유하기`
     *
     * ⚠️ 화면 13의 `공유하기` 버튼과 **글자가 같지만 다른 자리다** — 이건 안드로이드
     *    시스템 시트의 제목이고 우리 버튼이 아니다.
     */
    const val CHOOSER_TITLE = "공유하기"

    /**
     * 화면 05 도감 상세 `공유`.
     *
     * A 문서 3절 ①: `캐치플라워에서 {꽃이름}을 모았어요`
     *
     * 🔴 **`을`을 박아 두지 않는다.** `금계국을`·`개나리를`가 갈린다 —
     *    [KoreanText.objectOf]가 받침을 보고 고른다. 표의 `을`은 예시다.
     *
     * @return 꽃 이름이 비었으면 null (버튼을 그리지 않는다)
     */
    fun flower(name: String?): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return "캐치플라워에서 ${KoreanText.objectOf(trimmed)} 모았어요"
    }

    /**
     * 화면 19 `초대 링크 보내기`.
     *
     * A 문서 3절 ①: `같이 동네 꽃을 모아요 · 캐치플라워` + 링크
     *
     * ⚠️ **링크는 4절 16번이 확정되기 전이다.** 지금은 [AppLinks.playStore]가 주는
     *    스토어 URL이고 **출시 전이라 404**다 — 그 사실이 4절 16번에 적혀 있다.
     *    여기서 링크를 지어내지 않는다(빈 초대장을 보내는 것과 같다).
     */
    fun invite(url: String): String = "같이 동네 꽃을 모아요 · 캐치플라워\n$url"

    /**
     * 화면 21 `결과 공유하기`.
     *
     * A 문서 3절 ①: `이번 시즌에 꽃 {N}종을 모았어요 · 캐치플라워`
     *
     * 🔴 **순위를 말하지 않는다.** 지난 시즌 순위를 보관하는 테이블이 없어서(4절 9번)
     *    이 화면은 순위를 영구히 모른다 — **아는 것만 쓴다.** 종수는 기기 도감에서
     *    센 값이라 서버 없이도 참이다.
     *
     * @param speciesCount 도감 누적 종수. null(아직 못 셌다)이거나 0이면 null을 준다 —
     *   `꽃 0종을 모았어요`를 남에게 보내는 일을 막는다.
     */
    fun seasonResult(speciesCount: Int?): String? {
        if (speciesCount == null || speciesCount <= 0) return null
        return "이번 시즌에 꽃 ${speciesCount}종을 모았어요 · 캐치플라워"
    }
}

/**
 * 앱 밖 URL을 만든다.
 *
 * ⚠️ **`applicationId`를 글자로 적지 않는다.** `build.gradle.kts`가 바꾸면 링크가
 *    조용히 남의 앱을 가리킨다. 부르는 쪽이 `BuildConfig.APPLICATION_ID`를 넘긴다.
 */
object AppLinks {

    /** 플레이스토어 앱 페이지. 4절 16번 — **출시 전에는 404다.** */
    fun playStore(applicationId: String): String =
        "https://play.google.com/store/apps/details?id=$applicationId"

    /**
     * **스토어 페이지가 살아 있는가.** [playStore]가 주는 URL이 404가 아닌 날 `true`로 바꾼다.
     *
     * ## 왜 상수 하나로 두는가
     *
     * 🔴 **화면이 「링크를 보내세요」라고 말하는데 그 링크가 404다.** 클로즈드 테스트에서도
     *    스토어 페이지는 없다 — 테스터 12명이 첫날 그 링크를 받는다(A 문서 4절 16번).
     *    그 사실을 **문구로 말하는 것**이 유일하게 정직한 상태이고, 그 문구는 출시하는 날
     *    **거짓이 된다.** 그래서 지울 자리를 한 곳으로 모았다.
     *
     * ⚠️ **`BuildConfig.DEBUG`로 가르지 않는다.** 클로즈드 테스트에 올라가는 것은
     *    **릴리스 빌드**다 — 디버그로 가르면 정작 테스터가 보는 빌드에서 안내가 사라진다.
     *
     * 🔵 **바꾸는 날 할 일:** 이 값을 `true`로 + `A_문구·버튼_스펙.md` 3절
     *    `19 초대 링크 · 출시 전 안내` 줄 삭제. 두 곳뿐이다
     *    ([com.catchflower.app.ui.ranking.FriendsScreen]이 유일한 사용처).
     */
    const val STORE_LISTING_LIVE = false
}
