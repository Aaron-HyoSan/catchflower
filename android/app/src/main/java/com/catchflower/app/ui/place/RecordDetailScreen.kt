package com.catchflower.app.ui.place

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.catchflower.app.core.RelativeTime
import com.catchflower.app.data.CommentRow
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.CfSmallButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.LoginGateSheet
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.ranking.FriendsViewModel
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 16 꽃 기록 상세 — `16_기록상세.svg`.
 *
 * 화면 15의 `사람들의 기록` 카드에서 들어온다.
 *
 * ## 🔴 사진 칸이 없다
 *
 * 와이어프레임 16 맨 위의 `사용자 촬영 사진`을 그리지 않는다 — **남의 사진은 서버에
 * 올라가 있지 않다**(기기 로컬 파일이고 `photo_url`은 항상 null이다).
 * [com.catchflower.app.ui.component.PhotoPlaceholder]로 채우면 회색 사각형이
 * **"사진을 못 불러왔다"로 읽히고** 다시 시도하게 만드는데, 다시 시도해도 영원히 안 온다.
 * 이 누락은 `구현현황_AOS.md`에 적혀 있다 — 여기서만 알고 있으면 다음 세션이
 * "사진을 빼먹었다"고 판단해 되살린다.
 *
 * ## 왕복 세 개가 따로 실패한다
 *
 * 반응(RPC) · 댓글(테이블) · 작성자 이름(뷰)이 각각 실패한다. **하나가 실패해도
 * 나머지를 그린다** — 판정은 전부 [RecordRules]에 있고 이 파일은 그리기만 한다.
 */
@Composable
fun RecordDetailScreen(
    vm: RecordViewModel,
    /**
     * `친구 추가`(2026-08-13 · A 문서 3절 ③).
     *
     * ⚠️ **화면 19 검색과 같은 인스턴스여야 한다.** 따로 만들면 방금 요청을 보낸
     *    사람이 다른 화면에서 다시 `친구 추가`로 보이고, 서버는 409를 성공으로
     *    삼키니 **아무 표시 없이 같은 일이 반복된다**([FriendsViewModel.justRequested]).
     */
    friendsVm: FriendsViewModel,
    onBack: () -> Unit,
    /** `도감에서 보기`. 도감 탭 상세로 건너간다. */
    onOpenDex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = vm.record ?: return
    val toast = rememberToaster()

    // 삭제를 확인 중인 댓글. null이면 다이얼로그가 없다.
    //
    // 🔴 **다이얼로그를 [CommentItem] 안에 두지 않는다.** 그 자리는 `LazyColumn`의
    //    항목이라 **화면 밖으로 스크롤되면 컴포지션에서 사라진다** — 다이얼로그가 열린
    //    채로 목록을 밀면 사라지고, 사용자는 확인을 누른 기억만 남는다. 그래서 상태를
    //    화면 층에 두고, 항목은 콜백만 올린다.
    var pendingDelete by remember { mutableStateOf<CommentRow?>(null) }
    val onDeleteRequest: (CommentRow) -> Unit = { pendingDelete = it }

    // 로그인 게이트(오너 결정 2026-08-14). 댓글·좋아요·신고는 **비로그인 0회**다.
    //
    // 🔴 **버튼을 숨기거나 비활성으로 만들지 않았다** — 그래서 이 시트가 유일한 설명이다.
    //    이 한 줄이 빠지면 `등록`·하트·`신고`가 **눌리지만 아무 일도 하지 않는다**
    //    (ViewModel은 `loginRequired`만 세우고 조용히 나간다). 죽은 버튼 14개가 그 모양이었다.
    // ⚠️ [ModalBottomSheet]는 별도 창에 그려지므로 `Column` 바깥·안쪽 어디에 두어도
    //    되지만, **`LazyColumn` 항목 안에는 두지 않는다** — 스크롤로 사라지면 시트도
    //    같이 사라진다(위 `pendingDelete`와 같은 이유).
    LoginGateSheet(visible = vm.loginRequired != null, onDismiss = vm::dismissLoginRequired)

    // 🔴 **친구 요청은 시트가 따로다**(2026-08-16). 판정 주인이 [FriendsViewModel]이라
    //    `vm.loginRequired`에는 안 들어온다 — 한 줄로 합칠 수 없다. 이게 없으면
    //    `친구 추가`가 **눌리지만 아무 일도 하지 않는 버튼**이 된다(화면 19와 같은 구조).
    LoginGateSheet(
        visible = friendsVm.loginRequired != null,
        onDismiss = friendsVm::dismissLoginRequired,
    )

    Column(modifier.fillMaxSize()) {
        CfHeader(
            title = "꽃 기록",
            onBack = onBack,
            trailing = {
                // A 문서 16번 헤더 우측 `신고`.
                //
                // ⚠️ **결과를 화면에서 확인할 방법이 없다** — 접수됐는지 안 됐는지
                //    화면이 안 바뀐다. 그래서 토스트로만 알린다(A 문서 3절 문구).
                CfTextButton(
                    text = "신고",
                    onClick = {
                        vm.report { ok ->
                            // 실패를 성공으로 말하지 않는다 — 접수되지 않은 신고가
                            // 조용히 사라진다.
                            toast(if (ok) CfToast.REPORT_RECEIVED else CfToast.NETWORK_ERROR)
                        }
                    },
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CfDimen.ScreenPadding,
                end = CfDimen.ScreenPadding,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(CfDimen.Gap),
        ) {
            item {
                AuthorRow(
                    vm = vm,
                    record = record,
                    // 내 기록·세션 없음·키 없는 빌드에서는 버튼을 안 그린다
                    // ([RecordRules.canAddFriend]).
                    //
                    // 🔴 **`로그인 전`이라고 적혀 있던 자리다 — 2026-08-16에 거짓임이
                    //    드러났다.** 익명 세션에도 uuid가 있으므로(그게 게이트 설계의
                    //    전제다) `myUserId`가 비어 있지 않고, 버튼은 **비로그인에도
                    //    그려진다.** 막는 것은 이 조건이 아니라 위 [LoginGateSheet]와
                    //    [FriendsViewModel.add]의 게이트다.
                    canAdd = vm.canAddFriend(friendsVm.searchable),
                    // 이미 보낸 요청은 **버튼이 아니라 상태 표시**다(A 문서 3절 ③).
                    requested = vm.authorId() in friendsVm.justRequested,
                    onAddFriend = {
                        val target = vm.authorId()
                        if (target == null) {
                            toast(CfToast.NETWORK_ERROR)
                        } else {
                            friendsVm.add(target) { ok ->
                                // 🔴 `{이름}님과 친구가 되었어요`가 아니다 — 요청은
                                //    `pending`이고 수락은 상대만 한다(C-2).
                                toast(if (ok) CfToast.FRIEND_REQUEST_SENT else CfToast.NETWORK_ERROR)
                            }
                        }
                    },
                )
            }
            item { FlowerRow(vm = vm, record = record, onOpenDex = onOpenDex) }

            // 기록의 한 줄 설명. 없으면 칸을 뺀다(빈 줄만 남기지 않는다).
            record.note?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Text(text = note, style = CfText.Body, color = CfColor.TextPrimary)
                }
            }

            // 장소. 화면 15에서 들어왔으면 같은 값이지만, **이 화면만 보고도 어디서
            // 찍은 것인지 알 수 있어야 한다**(공유 링크로 바로 열리는 날을 대비한다).
            record.placeName?.takeIf { it.isNotBlank() }?.let { place ->
                item {
                    Text(text = place, style = CfText.Caption, color = CfColor.TextTertiary)
                }
            }

            item { ReactionRow(vm = vm) }

            // 🔴 **여기에 `댓글` 섹션 제목을 두지 않는다.** 처음 그렇게 썼는데
            //    **내가 지어낸 문구였다** — A 문서 2절 16번 표에도, 와이어프레임 16에도
            //    그 제목이 없다(반응 줄 다음에 댓글 행이 바로 온다). 반응 줄이 이미
            //    `댓글 3`으로 개수를 말하므로 제목은 같은 말을 두 번 하는 것이기도 하다.
            //    ⚠️ 필요해지면 **A 문서에 먼저 넣고** 여기에 쓴다(1절 규칙 · `ButtonLabelSourceTest`
            //    는 버튼만 보므로 이 종류의 문구는 **어떤 검사도 막지 못한다**).
            //    → 그래서 [com.catchflower.app.ui.CopySourceTest]를 만들었다. 되붙이면
            //      이제 **빨개진다**(돌연변이로 확인했다).
            when (val state = vm.comments) {
                // 🔴 로딩 중에는 아무 문구도 그리지 않는다. `첫 댓글을 남겨보세요`가
                //    깜빡이면 3개 달린 기록에서 자기가 첫 사람인 줄 안다.
                CommentsUi.Loading -> Unit

                CommentsUi.Empty -> item {
                    Text(
                        "첫 댓글을 남겨보세요",
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                    )
                }

                is CommentsUi.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium)) {
                        Text(
                            "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                            style = CfText.Body,
                            color = CfColor.TextSecondary,
                        )
                        CfSecondaryButton(text = "다시 시도", onClick = vm::retry)
                    }
                }

                // 키 없는 빌드. 문구도 버튼도 없다.
                CommentsUi.NotConfigured -> Unit

                is CommentsUi.Loaded -> items(state.rows, key = { it.id }) { row ->
                    CommentItem(
                        row = row,
                        namesLoaded = state.namesLoaded,
                        // C-9의 두 갈래(내 댓글 · 내 사진의 기록)만 그린다 —
                        // 전부 그려 두고 누를 때 막으면 `죽은 버튼`이다.
                        canDelete = vm.canDelete(row),
                        onDelete = { onDeleteRequest(row) },
                    )
                }
            }

            // ⚠️ 입력창은 **댓글을 못 읽었을 때도** 그린다. 읽기 실패가 쓰기 실패는
            //    아니고(권한이 아니라 네트워크 한 번의 실패다), 감추면 사용자는
            //    이 기록에 댓글을 달 수 없는 기록이라고 읽는다.
            //    키 없는 빌드([CommentsUi.NotConfigured])만 예외다 — 보낼 곳이 없다.
            if (vm.comments != CommentsUi.NotConfigured) {
                item { CommentInput(vm = vm) }
            }
        }
    }

    // A 문서 3절 `화면 16 댓글 삭제` 확인 다이얼로그.
    //
    // 🔴 **확인을 한 번 받는다.** 되돌릴 수 없는 삭제이고(서버 soft delete는 목록에서
    //    영구히 빠진다), 버튼은 댓글 한 줄 옆의 좁은 자리라 잘못 눌리기 쉽다.
    pendingDelete?.let { target ->
        DeleteCommentDialog(
            onConfirm = {
                pendingDelete = null
                vm.deleteComment(target.id) { pgCode ->
                    toast(
                        when {
                            pgCode == null -> CfToast.COMMENT_DELETED
                            // 🔴 판정을 여기서 `if`로 쓰지 않는다 — [RecordRules]가 잰다.
                            //    서버가 거절한 것과 연결이 끊긴 것은 **사용자가 할 수 있는
                            //    일이 다르다**(다시 시도해도 같은 경우가 섞여 있다).
                            RecordRules.deleteToastIsNetwork(pgCode) -> CfToast.NETWORK_ERROR
                            else -> CfToast.COMMENT_DELETE_FAILED
                        },
                    )
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * `이 댓글을 지울까요?` (A 문서 3절 화면 16 댓글 삭제).
 *
 * ⚠️ **이 저장소의 첫 다이얼로그다.** `ui/component/`에 다이얼로그 컴포넌트가 없어서
 *    material3 [AlertDialog]를 직접 쓴다. 두 번째가 생기면 그때 컴포넌트로 뺀다 —
 *    지금 빼면 쓰는 곳이 하나인 추상이 되고, 그건 다음 사람이 규칙으로 읽는다.
 *
 * 🔴 **`지우기`가 오른쪽(confirmButton)이다.** 안드로이드 관례이고, 좌우를 바꾸면
 *    `취소`를 누르려던 손가락이 삭제를 누른다.
 */
@Composable
private fun DeleteCommentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("이 댓글을 지울까요?", style = CfText.BodyBold, color = CfColor.TextPrimary)
        },
        text = {
            Text("지우면 되돌릴 수 없어요", style = CfText.Body, color = CfColor.TextSecondary)
        },
        // ⚠️ `TextButton`을 직접 쓰지 않고 [CfTextButton]을 쓴다 — 최소 터치 44dp와
        //    글자 스타일이 그 안에 있다(A 문서 1절).
        confirmButton = {
            CfTextButton(text = "지우기", onClick = onConfirm, color = CfColor.Error)
        },
        dismissButton = {
            CfTextButton(text = "취소", onClick = onDismiss)
        },
        containerColor = CfColor.Background,
    )
}

/**
 * `꽃보다효산 / 연남동 · 2시간 전` + `친구 추가` (A 문서 16번).
 *
 * 🔴 이름 칸의 판정은 [RecordRules.authorName]이다 — 조회 실패에
 *    `탈퇴한 사용자예요`를 쓰면 살아 있는 사람이 그대로 굳는다.
 */
@Composable
private fun AuthorRow(
    vm: RecordViewModel,
    record: Discovery,
    canAdd: Boolean,
    requested: Boolean,
    onAddFriend: () -> Unit,
) {
    val name = RecordRules.authorName(vm.authorNickname, vm.authorNamesLoaded)
    val meta = RecordRules.authorMeta(
        place = record.placeName,
        relativeTime = RelativeTime.detailed(record.createdAt),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
        ) {
            // null이면 줄을 아예 그리지 않는다 — 빈 Text는 줄 높이만 남아서
            // 이름이 잘린 것처럼 보인다.
            if (name != null) {
                Text(text = name, style = CfText.BodyBold, color = CfColor.TextPrimary)
            }
            if (meta != null) {
                Text(text = meta, style = CfText.Caption, color = CfColor.TextTertiary)
            }
        }
        // 2026-08-13: `friendships` insert 정책이 이미 있어서 실제로 보낸다
        // ([com.catchflower.app.data.FriendService]). 마이그레이션은 필요 없었다.
        when {
            requested ->
                Text(text = "요청 보냄", style = CfText.Caption, color = CfColor.TextTertiary)

            canAdd -> CfSmallButton(text = "친구 추가", onClick = onAddFriend)

            // 그릴 수 없는 경우는 **자리를 비운다.** 회색 버튼을 두면 왜 안 눌리는지
            // 화면에 안 적혀서 고장으로 읽힌다(A 문서 1절 44번과 같은 이유).
            else -> Unit
        }
    }
}

/**
 * `금계국` / `국화과 · 6~8월` + `도감에서 보기` (A 문서 16번 버튼 칸).
 *
 * ⚠️ 도감에서 못 찾으면 **칸 전체를 그리지 않는다.** 이름 없는 카드에
 *    `도감에서 보기`만 남으면 눌러도 빈 상세로 간다.
 */
@Composable
private fun FlowerRow(vm: RecordViewModel, record: Discovery, onOpenDex: (Int) -> Unit) {
    val flower = vm.flower() ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
        ) {
            Text(text = flower.name, style = CfText.BodyBold, color = CfColor.TextPrimary)
            // `국화과 · 6~8월`. 개화기가 없는 종은 과만 나온다 — **문자열을 파싱하지 않는다.**
            Text(
                text = flower.familyAndBloom(),
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
        }
        CfSmallButton(text = "도감에서 보기", onClick = { onOpenDex(record.flowerId) })
    }
}

/**
 * `좋아요 12 · 댓글 3` (A 문서 16번). 좋아요는 **누를 수 있다.**
 *
 * 🔴 **숫자를 모르면 줄 전체를 뺀다**([RecordRules.likeLabel]) — `좋아요 0`은
 *    "아무도 안 눌렀다"·"볼 권한이 없다"·"조회 실패"를 한 값으로 겹친다.
 *    그러면 좋아요 버튼도 사라진다. **그게 맞다** — 지금 몇 개인지 모르는 상태에서
 *    누르면 낙관적 갱신이 기준값 없이 숫자를 만들어낸다([RecordViewModel.toggleLike]가
 *    그래서 `before`가 null이면 아무것도 하지 않는다).
 */
@Composable
private fun ReactionRow(vm: RecordViewModel) {
    val reactions = vm.reactionState
    val like = RecordRules.likeLabel(reactions != null, reactions?.likeCount ?: 0) ?: return
    val comment = RecordRules.commentLabel(reactions != null, reactions?.commentCount ?: 0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
    ) {
        // 눌린 상태를 **색으로만** 구분하지 않는다 — 채움 버튼으로 바꿔서
        // 대비가 낮은 화면에서도 눌린 것이 보인다(A 문서 1절 Small `filled`).
        CfSmallButton(
            text = like,
            onClick = vm::toggleLike,
            filled = vm.likedByMe,
        )
        if (comment != null) {
            Text(text = comment, style = CfText.Body, color = CfColor.TextSecondary)
        }
    }
}

/**
 * 댓글 한 줄. 이름·시각은 각각 없을 수 있고, **본문은 항상 그린다.**
 *
 * @param canDelete C-9 판정([RecordRules.canDeleteComment]). false면 `삭제`를
 *   **그리지 않는다** — 그려 두고 누를 때 막으면 `죽은 버튼`이다.
 */
@Composable
private fun CommentItem(
    row: CommentRow,
    namesLoaded: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val name = RecordRules.authorName(row.nickname, namesLoaded)
    // ⚠️ `now`를 파라미터로 받지 않는다 — 목록을 다시 그릴 때마다 같은 시계를 쓰면
    //    한 화면 안에서 `1시간 전`과 `59분 전`이 섞인다. 여기서는 그릴 때마다 잰다.
    val time = RecordRules.commentTime(row.createdAt, System.currentTimeMillis())
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapTiny),
    ) {
        // ⚠️ `canDelete`도 조건에 넣는다 — 이름·시각이 **둘 다 없는 댓글**(이름 조회
        //    실패 + 시각 파싱 실패)에서도 삭제할 수 있어야 한다. 처음엔 이름·시각만
        //    봤는데, 그러면 그 상태에서 **내 댓글을 지울 수 없다**(버튼이 그려지지 않는다).
        if (name != null || time != null || canDelete) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
            ) {
                if (name != null) {
                    Text(
                        text = name,
                        style = CfText.BodyBold,
                        color = CfColor.TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (time != null) {
                    Text(text = time, style = CfText.Tiny, color = CfColor.TextTertiary)
                }
                if (canDelete) {
                    // 🔴 **텍스트다. 휴지통 아이콘으로 바꾸지 않는다**(A 문서 1절 44번 ·
                    //    3절 화면 16 댓글 삭제). 자리가 좁아서 아이콘으로 만들기 쉬운
                    //    자리인데, 그 규칙에 예외를 두지 않는다.
                    Spacer(Modifier.weight(1f))
                    CfTextButton(text = "삭제", onClick = onDelete)
                }
            }
        }
        Text(text = row.body, style = CfText.Body, color = CfColor.TextPrimary)
    }
}

/**
 * `댓글을 남겨보세요` + `등록` (A 문서 16번 입력창 칸).
 *
 * 🔴 **보내는 중에는 `등록`을 끈다** — 왕복이 두 번이라(등록 → 목록 다시 읽기)
 *    1초 이상 화면이 그대로다. 안 끄면 **같은 댓글이 두 줄** 달린다
 *    (서버에 중복 제약이 없다 · [CommentSendState] 주석).
 *
 * ⚠️ 실패해도 **입력을 지우지 않는다.** 200자를 쓴 사람에게는 그게 더 큰 손실이다.
 */
@Composable
private fun CommentInput(vm: RecordViewModel) {
    val sending = vm.sendState == CommentSendState.SENDING
    val length = RecordRules.commentLength(vm.draft)
    Column(verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall)) {
        OutlinedTextField(
            value = vm.draft,
            onValueChange = vm::onDraftChange,
            enabled = !sending,
            placeholder = {
                Text("댓글을 남겨보세요", style = CfText.Body, color = CfColor.TextTertiary)
            },
            textStyle = CfText.Body,
            shape = RoundedCornerShape(CfDimen.RadiusCard),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CfColor.Background,
                unfocusedContainerColor = CfColor.Background,
                focusedIndicatorColor = CfColor.Primary,
                unfocusedIndicatorColor = CfColor.Border,
                cursorColor = CfColor.Primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$length/${RecordRules.COMMENT_MAX}",
                style = CfText.Tiny,
                // 한도에 닿으면 색으로 알린다 — 글자가 안 들어가는 이유가 화면에 없으면
                // 입력이 고장 난 것으로 읽는다(`ShareSettingsScreen.NoteSection`과 같은 처리).
                color = if (length >= RecordRules.COMMENT_MAX) CfColor.Warning
                else CfColor.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (vm.sendState == CommentSendState.FAILED) {
                Text(
                    "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                    style = CfText.Tiny,
                    color = CfColor.Error,
                    modifier = Modifier.padding(end = CfDimen.GapSmall),
                )
            }
            CfSmallButton(
                text = "등록",
                onClick = vm::submitComment,
                // 🔴 빈 입력·전송 중에는 못 누른다. 판정은 [RecordRules.canSubmit]이
                //    코드포인트로 센다(UTF-16 `length`로 세면 이모지가 2로 세어져
                //    화면은 통과시키는데 서버가 거절한다).
                enabled = !sending && RecordRules.canSubmit(vm.draft),
                filled = true,
            )
        }
        Spacer(Modifier.height(CfDimen.GapTiny))
    }
}
