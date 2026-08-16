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

    /**
     * 이 댓글에 **`삭제` 버튼을 그리는가.**
     *
     * C-9의 두 갈래다 — **내가 쓴 댓글** 또는 **내 사진의 기록에 달린 댓글**.
     * 서버 `delete_comment` 본문이 같은 두 갈래를 센다(`c_user_id = auth.uid()` ·
     * `is_discovery_owner(c_discovery_id)`).
     *
     * 🔴 **전부 그려 두고 누를 때 막지 않는다.** 그러면 남의 댓글마다 `삭제`가 있고
     *    누르면 `댓글을 지우지 못했어요`가 뜨는 **누를 수 있는데 실패하는 버튼**이 된다 —
     *    이 저장소가 이미 세 번 만든 `죽은 버튼`이다(A 문서 3절 화면 16 댓글 삭제).
     *
     * ⚠️ **이 판정이 자물쇠가 아니다.** 자물쇠는 `delete_comment` **본문**이고
     *    (`security definer`라 RLS를 지나가므로 그 안에만 있다), 여기가 틀려도 남의
     *    댓글은 안 지워진다. 반대로 **여기만 믿어서도 안 된다** — 화면 판정을 우회한
     *    호출은 서버가 막고, 서버 판정을 우회한 화면은 사용자에게 죽은 버튼을 준다.
     *
     * 🔴 **`myUserId`가 빈 문자열이면 아무 댓글에도 그리지 않는다.** 익명 로그인은
     *    네트워크라서 첫 프레임에 uuid가 없을 수 있는데(`DiscoveryRepository.userId`
     *    주석), 빈 문자열끼리 비교하면 **`userId`를 못 받은 댓글 전부가 내 것이 된다.**
     *    `discoveries`의 `user_id`는 `not null`이라 그쪽이 빌 일은 없지만,
     *    이 비교는 **양쪽이 다 비었을 때 참이 되는 모양**이라 미리 끊는다.
     *
     * @param commentUserId 댓글 작성자 uuid([com.catchflower.app.data.CommentRow.userId]).
     * @param recordOwnerId 이 기록(사진) 주인 uuid([com.catchflower.app.data.model.Discovery.userId]).
     * @param myUserId 내 uuid. 로그인 전이면 빈 문자열일 수 있다.
     */
    fun canDeleteComment(
        commentUserId: String,
        recordOwnerId: String,
        myUserId: String,
    ): Boolean {
        if (myUserId.isBlank()) return false
        return commentUserId == myUserId || recordOwnerId == myUserId
    }

    /**
     * 삭제 실패에 **어느 문구를 쓰는가.**
     *
     * 🔴 **두 실패가 같은 문구를 쓰면 안 된다**(A 문서 3절 화면 16 댓글 삭제):
     *    ① 서버가 **거절**했다 — HTTP 200 + 본문 `false`
     *       ([com.catchflower.app.data.ReactionService.DENIED]). 권한 없음·없는 id·
     *       미로그인이 **일부러 한 값으로 묶여** 있고, 그중 몇은 **다시 시도해도 결과가
     *       같다.** 그래서 `잠시 후 다시 시도해 주세요`는 **틀린 안내**다.
     *    ② 네트워크가 끊겼다 — HTTP 0(전송 실패)·5xx·401. 이건 정말 다시 시도하면 된다.
     *
     * ⚠️ **화면에서 `if (code == 200)`으로 나누지 않는다.** 그러면 이 판정이 어느 층에서도
     *    검증되지 않는데, 틀렸을 때의 증상은 **문구 하나가 바뀌는 것뿐**이라 아무도 못 본다
     *    (`증상 없는 UI 결함`의 `같은 얼굴의 다른 원인`).
     *
     * @param pgCode [com.catchflower.app.data.ReactionResult.Failed.pgCode].
     * @return true면 [com.catchflower.app.ui.component.CfToast.NETWORK_ERROR],
     *   false면 [com.catchflower.app.ui.component.CfToast.COMMENT_DELETE_FAILED].
     */
    fun deleteToastIsNetwork(pgCode: String): Boolean =
        pgCode != com.catchflower.app.data.ReactionService.DENIED

    /**
     * 작성자 줄에 `친구 추가`를 그리는가(2026-08-13 · A 문서 3절 ③).
     *
     * 🔴 **내 기록에는 안 그린다.** 서버 `friendships_no_self`가 막으므로 눌러도 실패한다 —
     *    [canDeleteComment]에서 피한 그 `누를 수 있는데 실패하는 버튼`이다. 지도에는
     *    내가 공유한 기록이 섞여 있어서 **실제로 자주 열린다.**
     *
     * 🔴 **`myUserId`가 비면 그리지 않는다.** 익명 로그인이 아직 안 끝난 프레임인데,
     *    빈 문자열끼리 비교하면 이 판정이 뒤집힌다([canDeleteComment]와 같은 함정).
     *    `discoveries.user_id`는 `not null`이지만 **양쪽이 다 비면 참이 되는 모양**을
     *    미리 끊는다.
     *
     * ⚠️ **키 없는 빌드에서도 그리지 않는다.** 서버가 없으면 눌러도 영원히
     *    `연결이 불안정해요`뿐이고, 그건 죽은 버튼의 다른 얼굴이다(4절 17번).
     *
     * ⚠️ **이 판정은 로그인 여부를 보지 않는다** — 그건
     *    [com.catchflower.app.core.LoginGate.GatedAction.FRIEND_REQUEST]가 본다.
     *    익명 세션에도 uuid가 있어서 여기서는 로그인한 것과 구분되지 않는다.
     *
     * @param authorId 기록 주인 uuid([com.catchflower.app.data.model.Discovery.userId]).
     * @param myUserId 내 uuid. **익명 로그인이 끝나기 전** 프레임에서만 빈 문자열이다
     *   (익명 세션이 붙으면 값이 있다 — `카카오 연결 여부`와는 무관하다).
     * @param serverReady 친구 요청을 보낼 수 있는 빌드인가
     *   ([com.catchflower.app.ui.ranking.FriendsViewModel.searchable]).
     */
    fun canAddFriend(authorId: String, myUserId: String, serverReady: Boolean): Boolean {
        if (!serverReady) return false
        if (myUserId.isBlank()) return false
        return authorId != myUserId
    }
}
