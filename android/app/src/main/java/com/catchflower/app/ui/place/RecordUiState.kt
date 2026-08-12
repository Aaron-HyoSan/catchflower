package com.catchflower.app.ui.place

import com.catchflower.app.data.CommentRow

/**
 * 화면 16 꽃 기록 상세의 **댓글 칸** 상태.
 *
 * 🔴 **빈 목록으로 세 상태를 대신하지 않는다.** A 문서 3절 `화면 16에서 값이 없거나
 *    실패한 칸`이 이 화면의 네 칸을 정해 두었고, 댓글 칸은 그중 **문구가 두 개**다:
 *    0건이면 `첫 댓글을 남겨보세요`, 실패면 `연결이 불안정해요…` + `다시 시도`.
 *    실패를 0건으로 그리면 **댓글 3개가 달린 기록이 "첫 댓글"을 권한다** —
 *    사용자는 자기가 첫 사람인 줄 알고 같은 질문을 다시 쓴다.
 *
 * ⚠️ 반응 줄(`좋아요 12 · 댓글 3`)은 이 타입에 넣지 않는다. **왕복이 다르고
 *    실패도 따로 난다** — 댓글은 왔는데 반응만 못 받은 상태가 실제로 있다
 *    (`discovery_reactions`는 RPC고 `comments`는 테이블 조회다).
 *    합쳐 두면 한쪽 실패가 다른 쪽 값을 지운다.
 */
sealed interface CommentsUi {
    /** 아직 응답이 없다. **`첫 댓글을 남겨보세요`가 깜빡이면 안 된다.** */
    data object Loading : CommentsUi

    /** 진짜 0건. A 문서 3절 `첫 댓글을 남겨보세요`. */
    data object Empty : CommentsUi

    /** 못 불렀다. `연결이 불안정해요. 잠시 후 다시 시도해 주세요.` + `다시 시도`. */
    data class Failed(val code: Int) : CommentsUi

    /** 키 없는 빌드. 문구도 버튼도 없다. */
    data object NotConfigured : CommentsUi

    /**
     * @property rows 최신순.
     * @property namesLoaded 🔴 **false면 [CommentRow.nickname]의 null은 탈퇴가 아니라
     *   "모른다"** 다 — 화면은 이름 칸을 비운다([RecordRules.authorName]).
     */
    data class Loaded(
        val rows: List<CommentRow>,
        val namesLoaded: Boolean,
    ) : CommentsUi
}

/**
 * 댓글 등록 진행 상태. **버튼 하나에 세 모양이 필요하다.**
 *
 * ⚠️ **[Sending]을 안 두면 두 번 눌린다.** 등록은 왕복이고 성공하면 목록을 다시 읽으므로
 *    (두 번째 왕복) 눌린 뒤 1초 이상 화면이 그대로다 — 사용자는 안 눌렸다고 생각하고
 *    다시 누른다. 그러면 **같은 댓글이 두 줄** 달린다(서버에 중복 제약이 없다).
 */
enum class CommentSendState {
    IDLE,
    SENDING,

    /**
     * 보내다 실패했다. **입력은 지우지 않는다.**
     *
     * 🔴 실패했는데 입력을 비우면 **쓴 글이 사라진다** — 200자를 쓴 사람에게는
     *    네트워크 오류보다 그게 더 큰 손실이다.
     */
    FAILED,
}
