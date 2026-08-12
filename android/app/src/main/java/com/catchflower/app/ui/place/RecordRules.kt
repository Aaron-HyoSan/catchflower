package com.catchflower.app.ui.place

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.RelativeTime
import java.time.Instant

/**
 * 화면 16 꽃 기록 상세의 판단. **화면 밖에 둔다.**
 *
 * 🔴 이 화면의 위험한 계산은 전부 **"값이 없는 것을 0이나 빈 문자열로 그리는" 쪽**이다.
 *    A 문서 3절 `화면 16에서 값이 없거나 실패한 칸`(2026-08-11)이 네 칸을 정해 두었고,
 *    그 판정을 Composable 안에 두면 **아무 층에서도 검증되지 않는다** —
 *    화면은 `좋아요 0 · 댓글 0`을 완벽히 정상으로 그린다.
 *
 * ⚠️ **문구를 새로 만들지 않는다.** 여기 있는 문자열은 전부 A 문서에 있는 것이고,
 *    조립 규칙만 여기서 정한다.
 */
object RecordRules {

    /** 댓글 최대 길이. 원본은 [GamePolicy.COMMENT_MAX_LENGTH](서버 `comments_body_len`). */
    const val COMMENT_MAX = GamePolicy.COMMENT_MAX_LENGTH

    /**
     * 반응 줄 `좋아요 12 · 댓글 3`을 **그릴 수 있는가.**
     *
     * 🔴 **`null`이면 줄 전체를 뺀다.** A 문서 3절: 못 받은 숫자를 `0`으로 그리면
     *    "아무도 안 눌렀다"·"볼 수 없는 기록이다"·"조회에 실패했다"가 **한 값으로
     *    겹친다**(서버 `discovery_reactions`는 권한이 없어도 오류가 아니라 0을 준다).
     *    12명이 좋아요한 기록에 `좋아요 0`이 뜨고 **화면은 정상으로 보인다.**
     *
     * ⚠️ 값이 **정말 0인 경우**(아무도 안 눌렀다)는 `0`을 그린다 — 그건 사실이다.
     *    구분은 [reactionsKnown]으로만 한다(호출처가 `Reactions`를 받았는지 본다).
     */
    fun likeLabel(reactionsKnown: Boolean, likeCount: Int): String? =
        if (reactionsKnown) "좋아요 $likeCount" else null

    /** 위와 같은 규칙의 댓글 쪽. */
    fun commentLabel(reactionsKnown: Boolean, commentCount: Int): String? =
        if (reactionsKnown) "댓글 $commentCount" else null

    /**
     * 작성자 줄의 **둘째 칸** `연남동 · 2시간 전`.
     *
     * A 문서 3절 `작성자 지역`: **있는 것만 쓴다. 둘 다 없으면 줄을 뺀다.**
     *
     * ⚠️ **가운뎃점을 문자열로 미리 이어 두지 않는다.** 한쪽이 없을 때 ` · 2시간 전`
     *    처럼 **점으로 시작하는 줄**이 남는다 — 화면에서는 오타로 보이고,
     *    빈 지역을 "이름 없는 동네"로 읽게 만든다.
     *
     * @param place 기록의 장소명 또는 동명. 없으면 null.
     * @param relativeTime [com.catchflower.app.core.RelativeTime.detailed]의 결과.
     */
    fun authorMeta(place: String?, relativeTime: String?): String? {
        val parts = listOfNotNull(
            place?.trim()?.takeIf { it.isNotEmpty() },
            relativeTime?.trim()?.takeIf { it.isNotEmpty() },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * 작성자·댓글 작성자의 **이름 칸**.
     *
     * 🔴 **`null`인 이유가 두 가지고 문구가 서로 다르다**(A 문서 3절 · `CommentList` 주석):
     *    ① 이름 조회가 **성공했는데** 그 사람이 없다 → **탈퇴** → `탈퇴한 사용자예요`
     *    ② 이름 조회가 **실패했다** → 다시 시도하면 오므로 **비운다.**
     *       여기에 `탈퇴한 사용자예요`를 쓰면 **살아 있는 사람을 탈퇴로 표시하고 굳는다.**
     *
     * ⚠️ `알 수 없음`을 쓰지 않는다 — 오류처럼 읽힌다(A 문서 3절이 그래서 문구를 정했다).
     *
     * @return 그릴 문자열. `null`이면 **이름 칸을 그리지 않는다**(빈 Text도 그리지 않는다 —
     *   줄 높이만 남아서 이름이 잘린 것처럼 보인다).
     */
    fun authorName(nickname: String?, namesLoaded: Boolean): String? = when {
        !nickname.isNullOrBlank() -> nickname
        namesLoaded -> "탈퇴한 사용자예요"
        else -> null
    }

    /**
     * 댓글 입력값. **입력이 바뀔 때마다 통과시킨다.**
     *
     * ⚠️ **길이를 `String.length`로 세지 않는다.** 서버 제약은 `char_length(body)`
     *    (코드포인트)이고 코틀린 `length`는 UTF-16이다 — 이모지 한 개가 2로 세어져
     *    **화면은 통과시키는데 서버가 거절하거나, `take(200)`이 서로게이트 쌍을
     *    반토막 낸다**([com.catchflower.app.ui.capture.ShareRules]와 같은 함정이다).
     *
     * ⚠️ **공백을 압축하지 않는다.** 타이핑 중 뒤 공백을 지우면 다음 글자를 이어 쓸 수
     *    없다(ShareRules와 같은 이유). 앞뒤 공백은 보낼 때 [ReactionService]가 `trim`한다.
     *
     * ⚠️ 줄바꿈은 **살린다.** 댓글은 여러 줄이 정상이고, 서버 제약도 길이뿐이다
     *    (화면 13의 한 줄 설명과 다르다).
     */
    fun sanitizeComment(raw: String): String {
        if (commentLength(raw) <= COMMENT_MAX) return raw
        return raw.substring(0, raw.offsetByCodePoints(0, COMMENT_MAX))
    }

    /** 서버 `char_length`와 같은 셈. */
    fun commentLength(body: String): Int = body.codePointCount(0, body.length)

    /**
     * 댓글 한 줄의 `40분 전`.
     *
     * 🔴 **[com.catchflower.app.data.CommentRow.createdAt]은 `Long`이 아니라 ISO 문자열이다**
     *    — `discoveries`(`DiscoveryStore.decodeTime`을 지난다)와 달리 그 층이 서버 값을
     *    **그대로** 담아 온다. 화면에서 `toLong()`을 하면 `NumberFormatException`이고,
     *    문자열을 그냥 그리면 댓글 옆에 `2026-08-12T04:31:07.221Z`가 붙는다.
     *
     * ⚠️ **파싱 실패에 현재 시각을 넣지 않는다.** `now`로 대체하면 서버가 형식을 바꾼
     *    날 **모든 댓글이 `1분 전`**이 되고, 그건 완벽히 정상으로 보인다.
     *    → `null`을 주고 화면이 그 칸을 뺀다(본문은 그린다 — 댓글이 사라지는 것이 더 나쁘다).
     *
     * @return `null`이면 시각 칸을 그리지 않는다.
     */
    fun commentTime(isoCreatedAt: String, now: Long): String? {
        val millis = runCatching { Instant.parse(isoCreatedAt).toEpochMilli() }.getOrNull()
            ?: return null
        return RelativeTime.detailed(millis, now)
    }

    /**
     * `등록`을 누를 수 있는가.
     *
     * ⚠️ 공백만 있는 입력은 보내지 않는다 — [ReactionService.postComment]가
     *    `trim` 후 빈 문자열을 `Rejected(EMPTY_BODY)`로 막는데, **거기까지 가면
     *    사용자는 왕복을 기다린 뒤 아무 일도 안 일어난 것을 본다.**
     */
    fun canSubmit(body: String): Boolean =
        body.isNotBlank() && commentLength(body) <= COMMENT_MAX
}
