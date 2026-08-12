package com.catchflower.app.ui.place

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.ReactionResult
import com.catchflower.app.data.ReactionService
import com.catchflower.app.data.ReactionSource
import com.catchflower.app.data.Reactions
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.data.model.Flower
import kotlinx.coroutines.launch

/**
 * 화면 16 꽃 기록 상세.
 *
 * 🔴 **`@JvmOverloads`가 필수다** — 이유는 [PlaceViewModel]과 같다(세 번 당했다).
 * 🔴 **모든 `mutableStateOf`가 [init]보다 위**여야 한다. 여기는 `init`이 없지만
 *    선언 순서를 유지한다.
 *
 * ## 🔴 사진을 그리지 않는다 (와이어프레임 16의 `사용자 촬영 사진` 칸)
 *
 * 와이어프레임 16 맨 위는 남이 찍은 사진인데, **우리는 사진을 서버에 올리지 않는다** —
 * 사진은 기기 로컬 파일이고(`PhotoStore`) `photo_url`은 항상 null이다.
 * 그래서 이 화면에서 남의 사진을 **그릴 방법이 없다.**
 *
 * ⚠️ [com.catchflower.app.ui.component.PhotoPlaceholder]로 채우지 않는다. 그 컴포넌트
 *    주석이 `발견 기록에는 쓰지 않는다`라고 못 박아 뒀다 — 회색 사각형은
 *    **"사진을 못 불러왔다"로 읽히고**, 실제로는 존재하지 않는 사진이다.
 *    `연결이 불안정해요`를 띄우면 다시 시도하게 만드는데 영원히 안 온다
 *    (A 문서가 화면 01 `manual_linking_disabled`에서 같은 판단을 했다).
 *    → **칸 자체를 그리지 않고**, 그 사실을 `구현현황_AOS.md`에 적는다.
 *
 * ## JVM 테스트가 이 클래스를 만들 수 없다
 *
 * 판단은 [RecordRules]에 있다. 여기는 왕복 세 번의 조립이다:
 * ① `discovery_reactions`(RPC) ② `comments`(테이블) ③ `public_profiles`(작성자 이름).
 * **세 개가 따로 실패한다** — 하나가 실패해도 나머지를 그려야 한다.
 */
class RecordViewModel @JvmOverloads constructor(
    app: Application,
    /**
     * null이면 서버 기능이 꺼진 빌드다.
     *
     * ⚠️ [DiscoveryRepository]가 든 것과 **같은 `AuthService`**를 쓴다
     *    (이유는 [PlaceViewModel]과 같다 — 토큰 회전이 갈리면 며칠 뒤 로그인이 끊긴다).
     */
    private val reactions: ReactionSource? = DiscoveryRepository.get(app).let { repo ->
        repo.auth?.let { account -> ReactionService(account, myUserId = { account.userId() }) }
    },
) : AndroidViewModel(app) {

    private val flowers = FlowerRepository.get(app)

    /** 보고 있는 기록. null이면 화면이 열려 있지 않다. */
    var record by mutableStateOf<Discovery?>(null)
        private set

    /**
     * 반응 3칸. **null이면 "모른다"** 다 — 화면은 [RecordRules.likeLabel]로 줄을 뺀다.
     *
     * 🔴 `Reactions(0, 0, false)`를 초기값으로 두면 안 된다. 첫 프레임에
     *    `좋아요 0 · 댓글 0`이 뜨고, 그게 A 문서가 금지한 그 거짓말이다.
     */
    var reactionState by mutableStateOf<Reactions?>(null)
        private set

    var comments by mutableStateOf<CommentsUi>(CommentsUi.Loading)
        private set

    /** 작성자 닉네임. null과 "모른다"의 구분은 [authorNamesLoaded]가 한다. */
    var authorNickname by mutableStateOf<String?>(null)
        private set

    /**
     * 작성자 이름 조회가 **성공했는가.**
     *
     * 🔴 이 값이 false면 [authorNickname]의 null은 **탈퇴가 아니라 "모른다"** 다.
     *    A 문서 3절: 실패에 `탈퇴한 사용자예요`를 쓰면 살아 있는 사람이 그대로 굳는다.
     */
    var authorNamesLoaded by mutableStateOf(false)
        private set

    /** 댓글 입력값. [RecordRules.sanitizeComment]를 지나온 값만 담긴다. */
    var draft by mutableStateOf("")
        private set

    var sendState by mutableStateOf(CommentSendState.IDLE)
        private set

    /**
     * 내가 좋아요를 눌렀는가. **[reactionState]와 따로 둔다.**
     *
     * ⚠️ 눌린 즉시 화면을 바꾸고(낙관적 갱신) 실패하면 되돌린다 — 왕복을 기다리면
     *    하트가 1초 뒤에 켜져서 안 눌린 것처럼 보인다.
     */
    val likedByMe: Boolean get() = reactionState?.likedByMe == true

    /**
     * 기록을 눌러 들어왔다. 화면 15가 **행 전체를 넘긴다**(id만 넘기면 다시 조회해야 한다).
     */
    fun open(discovery: Discovery) {
        record = discovery
        // 🔴 **이전 기록의 값을 남기지 않는다.** 다른 기록을 열었는데 앞 기록의
        //    `좋아요 12`가 남아 있으면 그 순간 화면이 **다른 기록의 숫자**를 말한다.
        reactionState = null
        comments = CommentsUi.Loading
        authorNickname = null
        authorNamesLoaded = false
        draft = ""
        sendState = CommentSendState.IDLE
        loadAll(discovery)
    }

    /** A 문서 3절 댓글 조회 실패의 `다시 시도`. */
    fun retry() {
        record?.let { loadAll(it) }
    }

    private fun loadAll(discovery: Discovery) {
        val src = reactions ?: run {
            comments = CommentsUi.NotConfigured
            return
        }
        viewModelScope.launch {
            // ⚠️ **세 왕복을 한 코루틴에서 순서대로 부른다.** 병렬로 띄우면 빠르지만,
            //    실패 조합이 8가지가 되고 화면이 그중 어느 상태인지 읽기 어려워진다.
            //    (반응 → 댓글 → 이름 순서에 의미는 없다. 어느 하나가 실패해도 나머지는 진행한다.)
            reactionState = (src.reactions(discovery.id) as? ReactionResult.Loaded)?.value
            comments = when (val res = src.comments(discovery.id)) {
                is ReactionResult.Loaded ->
                    if (res.value.isEmpty) CommentsUi.Empty
                    else CommentsUi.Loaded(res.value.rows, res.value.namesLoaded)

                is ReactionResult.Failed -> CommentsUi.Failed(res.code)
                is ReactionResult.Rejected -> CommentsUi.Failed(0)
                ReactionResult.NotConfigured -> CommentsUi.NotConfigured
            }
            when (val names = src.profiles(setOf(discovery.userId))) {
                is ReactionResult.Loaded -> {
                    authorNickname = names.value[discovery.userId]
                    // 🔴 조회가 성공한 뒤에 켠다. 이 순서가 뒤집히면 실패한 조회가
                    //    작성자를 `탈퇴한 사용자예요`로 만든다.
                    authorNamesLoaded = true
                }

                else -> {
                    authorNickname = null
                    authorNamesLoaded = false
                }
            }
        }
    }

    /** 입력이 바뀔 때마다 통과시킨다. 길이 제한은 [RecordRules]가 코드포인트로 센다. */
    fun onDraftChange(raw: String) {
        draft = RecordRules.sanitizeComment(raw)
        // 실패 상태에서 글자를 고치면 오류 표시를 내린다 — 안 내리면 다시 보내는데도
        // 화면에 실패가 남아 있어서 또 실패한 것처럼 보인다.
        if (sendState == CommentSendState.FAILED) sendState = CommentSendState.IDLE
    }

    /**
     * `등록`. 성공하면 **목록을 다시 읽는다.**
     *
     * ⚠️ 방금 쓴 댓글을 화면에 직접 끼워 넣지 않는다 — 서버가 붙이는 `id`·`created_at`이
     *    없어서 가짜 값을 만들어야 하고, 그러면 `1분 전`이 기기 시계로 계산된 값이 된다.
     */
    fun submitComment() {
        val discovery = record ?: return
        val src = reactions ?: return
        val body = draft
        if (!RecordRules.canSubmit(body)) return
        if (sendState == CommentSendState.SENDING) return
        sendState = CommentSendState.SENDING
        viewModelScope.launch {
            when (src.postComment(discovery.id, body)) {
                is ReactionResult.Loaded -> {
                    draft = ""
                    sendState = CommentSendState.IDLE
                    // 반응 줄의 `댓글 3`도 같이 늘어야 한다 — 목록만 다시 읽으면
                    // 줄에는 3인데 아래에 4개가 깔린다(FriendsScreen에서 겪은 그 모양).
                    loadAll(discovery)
                }

                else -> sendState = CommentSendState.FAILED
            }
        }
    }

    /**
     * 좋아요 토글. **낙관적으로 먼저 바꾸고 실패하면 되돌린다.**
     *
     * ⚠️ 되돌릴 때 숫자도 같이 되돌린다 — 하트만 되돌리면 `좋아요 13`에 꺼진 하트가
     *    남는다.
     */
    fun toggleLike() {
        val discovery = record ?: return
        val src = reactions ?: return
        val before = reactionState ?: return // 숫자를 모르면 토글할 기준이 없다
        val wasLiked = before.likedByMe
        reactionState = before.copy(
            likeCount = (before.likeCount + if (wasLiked) -1 else 1).coerceAtLeast(0),
            likedByMe = !wasLiked,
        )
        viewModelScope.launch {
            val res = if (wasLiked) src.unlike(discovery.id) else src.like(discovery.id)
            if (res !is ReactionResult.Loaded) reactionState = before
        }
    }

    /**
     * 헤더 우측 `신고`.
     *
     * ⚠️ **결과를 화면에서 확인할 방법이 없다**(`reports_read_own`이 본인 것만 주고
     *    자동 숨김은 3명이 모여야 도는 서버 판정이다). 그래서 성공 여부를 콜백으로
     *    올려 화면이 토스트를 띄운다 — 조용히 실패하면 아무도 모른다.
     *
     * @param onDone true면 A 문서 3절 `신고를 접수했어요. 확인 후 처리됩니다.`
     */
    fun report(onDone: (Boolean) -> Unit) {
        val discovery = record ?: return onDone(false)
        val src = reactions ?: return onDone(false)
        viewModelScope.launch {
            // reason은 null이다 — 신고 사유를 고르는 화면이 없다(A 문서에도 없다).
            // 빈 문자열을 넣으면 `reason = ''`인 행이 생겨 "사유를 안 썼다"와
            // "빈 사유를 썼다"가 구분되지 않는다(`ShareRules.toStored`와 같은 판단).
            onDone(src.report(discovery.id, reason = null) is ReactionResult.Loaded)
        }
    }

    /** 도감 정보(`금계국` · `국화과 · 6~8월`). 못 찾으면 null — 그 칸을 그리지 않는다. */
    fun flower(): Flower? = record?.let { flowers.byId(it.flowerId) }
}
