package com.catchflower.app.data

/**
 * 화면 20 `프로필 수정`의 **판단**. HTTP를 모른다 — JVM에서 전부 잰다
 * ([com.catchflower.app.data.NicknameRulesTest]).
 *
 * 2026-08-13에 `프로필 수정`을 실제 동작으로 바꾸면서 생겼다.
 */
object NicknameRules {

    /**
     * 쓸 수 있는 최대 글자 수. A 문서 3절 ④의 `닉네임은 10자까지 쓸 수 있어요`가 이 값이다.
     *
     * 🔴 **서버에는 길이 제약이 없다**(`users.nickname text not null` · 0001) — 즉
     *    **우리가 정하는 숫자**이고, 근거는 랭킹 목록에서 종수와 겹치지 않는 한계다
     *    (A 문서 4절 3번이 확정되면 같이 바뀐다).
     *
     * ⚠️ 이 값을 바꾸면 **A 문서의 문구도 같이 바뀐다**(`10자`가 아니게 된다).
     *    `CfToast.NICKNAME_TOO_LONG`이 그 문구를 들고 있으므로 두 곳이다.
     */
    const val MAX_LENGTH = 10

    /**
     * 저장 버튼을 눌렀을 때의 판정.
     *
     * 🔴 **세 값이다.** `Boolean`으로 두면 화면이 `닉네임을 입력해 주세요`와
     *    `닉네임은 10자까지 쓸 수 있어요`를 고를 수 없고, 그러면 지운 사용자에게
     *    길이 얘기를 하게 된다(A 문서 3절 ④에 문구가 둘로 있는 이유).
     */
    sealed interface Verdict {
        /** [value]는 **앞뒤 공백을 뗀** 값이다 — 보낼 값은 이것뿐이다. */
        data class Ok(val value: String) : Verdict

        data object Empty : Verdict
        data object TooLong : Verdict
    }

    /**
     * 🔴 **공백만 넣은 것은 [Verdict.Empty]다.** 안 막으면 `"   "`이 저장되고,
     *    랭킹 목록에 **이름이 없는 줄**이 생긴다 — 서버 `not null`은 빈 문자열을 막지 않는다.
     *
     * ⚠️ 길이는 **코드 포인트**로 센다. `String.length`로 세면 이모지 닉네임이 두 배로
     *    세어져서 `🌸🌸🌸🌸🌸🌸`(6자)가 거부된다 — 한글로만 테스트하면 안 보인다
     *    ([FriendRules.tooShort]와 같은 함정).
     */
    fun validate(raw: String): Verdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Verdict.Empty
        if (trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH) return Verdict.TooLong
        return Verdict.Ok(trimmed)
    }

    /**
     * 저장할 것이 있는가. **같은 값이면 서버를 부르지 않는다.**
     *
     * ⚠️ 왜 굳이 막는가: PATCH가 성공하면 `프로필을 저장했어요`가 뜨는데, 아무것도
     *    바꾸지 않은 사용자에게 그 문구는 거짓말은 아니지만 **네트워크 실패로 죽을 수
     *    있는 왕복을 공짜로** 만든다. 앞뒤 공백만 지운 경우도 여기서 걸린다.
     */
    fun changed(current: String?, next: String): Boolean = current?.trim() != next
}
