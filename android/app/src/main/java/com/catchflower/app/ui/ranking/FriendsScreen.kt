package com.catchflower.app.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catchflower.app.BuildConfig
import com.catchflower.app.core.AppLinks
import com.catchflower.app.core.ShareText
import com.catchflower.app.data.FriendRules
import com.catchflower.app.data.model.RankedEntry
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSmallButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
import com.catchflower.app.ui.component.ExternalOpen
import com.catchflower.app.ui.component.LoginGateSheet
import com.catchflower.app.ui.component.rememberNamedToaster
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/** 화면 19의 탭. */
private enum class FriendTab { MINE, INVITE }

/**
 * 화면 19 친구 관리·초대 — `19_친구관리.svg`.
 *
 * ⚠️ **연락처를 실제로 읽지 않는다.** 서버 대조(전화번호 해시)가 없는 상태에서
 *    실제 연락처를 읽으면 화면 03 고지("번호는 저장하지 않아요")를 지킬 방법이 없다 —
 *    대조할 상대가 없으니 읽을 이유도 없다.
 *
 * ## 🔴 2026-08-09: 더미를 지웠다. **가짜 사람을 보여주는 것이 미구현보다 나쁘다**
 *
 * 에뮬레이터에서 이 화면이 **스스로 모순된 상태**로 떠 있었다:
 * 헤더는 서버가 센 `친구 0명`인데 그 밑에 `연남댁 38종`·`효산맘 31종` … **8명**이
 * 깔려 있었다. 헤더만 실측 소스로 바꾸고((31)) 목록은 옛 `DummyRanking`에 남겨 둔
 * 결과다 — **한 화면에 진짜와 가짜가 같이 있으면 사용자는 가짜를 믿는다**(숫자가
 * 작은 쪽이 틀린 것처럼 보인다).
 *
 * 초대하기 탭은 더 나빴다. `연락처에 캐치플라워 이웃 3명이 있어요` 아래로
 * `김영희 010-2•••-4567` 같은 **가짜 전화번호**를 띄웠다. 연락처를 읽은 적이
 * 없는데 읽은 것처럼 말하는 화면이고, 그건 기능 미구현이 아니라 **신뢰 사고**다.
 *
 * 그래서 지금은 이렇게 한다:
 * - `내 친구` 탭 = **`friend_ranking`이 준 실제 친구**([friends] 인자). 서버가 센
 *   [friendCount]와 **같은 출처**라 두 숫자가 어긋날 수 없다.
 * - `초대하기` 탭 = 연락처 매칭이 붙을 때까지 **초대 링크 하나만** 둔다.
 *   문구는 이미 [ContactsDeniedFallback]에 있던 것을 쓴다 — 새 문구를 쓰지 않는다.
 *
 * ⚠️ **연락처 권한을 이제 요청하지 않는다.** 매칭이 없는 동안 권한을 받아도 할 일이
 *    없다 — iOS가 화면 03에서 연락처를 뺀 그 이유((16))가 여기에도 그대로 적용된다.
 *    매칭이 붙으면 [ContactsPermissionPrompt]를 다시 연결한다(지우지 않고 남겨 뒀다).
 */
@Composable
fun FriendsScreen(
    /** `null`이면 **모른다** — 숫자를 안 쓴다(A 문서 `친구 수를 모를 때의 문구`). */
    friendCount: Int?,
    /**
     * `friend_ranking`이 준 **실제** 친구 목록. 나를 포함해서 넘어오므로 여기서 뺀다.
     *
     * 🔴 **더미로 되돌리지 마라.** 위 클래스 주석의 사고가 그것이다.
     */
    friends: List<RankedEntry>,
    /**
     * 닉네임 검색·친구 요청(2026-08-13 · A 문서 3절 ③).
     *
     * ⚠️ **화면이 직접 만들지 않고 받는다.** `viewModel()`을 여기서 부르면
     *    미리보기·테스트가 이 화면을 그릴 수 없다(다른 화면과 같은 방식).
     */
    friendsVm: FriendsViewModel,
    onBack: () -> Unit,
    /**
     * 친구 관계가 바뀌었다 — **랭킹을 다시 읽어야 한다**(2026-08-27 · 항목 1·2).
     *
     * 🔴 **이 인자가 없으면 수락이 안 된 것처럼 보인다.** [friendCount]와 [friends]는
     *    `friend_ranking`이 준 값이고 이 화면은 그걸 다시 읽을 수 없다 — 수락 뒤에
     *    `내 친구 0`이 그대로 남고 목록도 빈 채라 사용자는 **수락이 실패한 것으로
     *    읽는다.** 화면 02(지역 저장)에서 같은 이유로 같은 배선을 했다.
     */
    onFriendsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(FriendTab.MINE) }
    /** `검색`을 누르면 목록 대신 검색 화면이 뜬다. 다시 누르거나 뒤로 가면 닫힌다. */
    var searching by remember { mutableStateOf(false) }
    val toast = rememberToaster()
    val context = androidx.compose.ui.platform.LocalContext.current

    /**
     * `초대 링크 보내기` → 시스템 공유 시트(A 문서 3절 ①).
     *
     * ⚠️ **링크가 지금 404다.** 앱이 출시 전이라 스토어 페이지가 없다 —
     *    A 문서 4절 16번에 적어 두었고, 출시되면 **코드 변경 없이** 살아난다.
     *    그래서 링크를 다른 것으로 바꿔 두지 않는다(랜딩 페이지를 지어내면 그건
     *    영구히 아무도 안 만드는 페이지가 된다).
     */
    val invite: () -> Unit = {
        val text = ShareText.invite(AppLinks.playStore(BuildConfig.APPLICATION_ID))
        if (!ExternalOpen.share(context, text)) toast(CfToast.SHARE_NO_APP)
    }

    // 로그인 게이트(오너 결정 2026-08-16). 친구 요청은 **비로그인 0회**다.
    //
    // 🔴 **이 한 줄이 빠지면 `친구 추가`가 눌리지만 아무 일도 하지 않는다** —
    //    [FriendsViewModel.add]는 막히면 `loginRequired`만 세우고 조용히 나가고,
    //    `onDone`도 부르지 않으므로 토스트조차 없다(화면 16과 같은 구조).
    LoginGateSheet(
        visible = friendsVm.loginRequired != null,
        onDismiss = friendsVm::dismissLoginRequired,
    )

    Column(modifier.fillMaxSize()) {
        CfHeader(
            title = "친구",
            // ⚠️ 검색 중이면 **뒤로가 검색을 닫는다.** 화면을 통째로 나가면 방금 찾던
            //    사람을 다시 찾아야 한다.
            onBack = { if (searching) searching = false else onBack() },
            trailing = {
                // 🔴 **키 없는 빌드에서는 그리지 않는다.** 서버가 없으면 눌러도 영원히
                //    `연결이 불안정해요`뿐이다 — 그건 죽은 버튼의 다른 얼굴이다
                //    (화면 20 `고객문의`와 같은 처리).
                if (friendsVm.searchable) {
                    CfTextButton(text = "검색", onClick = { searching = !searching })
                }
            },
        )

        if (searching) {
            FriendSearchPanel(vm = friendsVm, onFriendsChanged = onFriendsChanged)
            return@Column
        }

        Row(Modifier.fillMaxWidth()) {
            val mineLabel = friendCount?.let { "내 친구 $it" } ?: "내 친구"
            FriendTabItem(mineLabel, tab == FriendTab.MINE) { tab = FriendTab.MINE }
            FriendTabItem("초대하기", tab == FriendTab.INVITE) { tab = FriendTab.INVITE }
        }

        when (tab) {
            FriendTab.MINE -> MyFriendsList(
                friendCount = friendCount,
                all = friends,
                vm = friendsVm,
                onFriendsChanged = onFriendsChanged,
            )
            // 연락처 매칭이 없는 동안은 **초대 링크 하나만** 둔다. 가짜 연락처 목록을
            // 그리는 것보다 낫다(클래스 주석).
            FriendTab.INVITE -> ContactsDeniedFallback(onInvite = invite)
        }
    }
}

/**
 * 닉네임 검색 — A 문서 3절 ③.
 *
 * ## 이 화면이 말하는 다섯 가지가 서로 다르다
 *
 * 🔴 빈 검색창 · 한 글자 · 찾는 중 · 없음 · 실패를 **한 문장으로 뭉치면 각각 틀린 말이
 *    된다.** 특히 `그 닉네임을 가진 사람이 없어요`를 실패에 쓰면 **못 물어본 것을
 *    없다고 말한다**([FriendSearchUi] 주석).
 *
 * ## 버튼 자리
 *
 * ⚠️ `요청 보냄`·`이미 친구예요`는 **버튼이 아니라 상태 표시다.** 누를 수 있게 두고
 *    눌렀을 때 막으면 `누를 수 있는데 실패하는 버튼`이 된다(A 문서 3절 ③).
 */
@Composable
private fun FriendSearchPanel(vm: FriendsViewModel, onFriendsChanged: () -> Unit) {
    val toast = rememberToaster()
    val namedToast = rememberNamedToaster()
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
    ) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            // A 문서 3절 ③ `19 검색창`. placeholder다 — 라벨로 두면 입력 중에 사라진다.
            placeholder = {
                Text("닉네임으로 찾기", style = CfText.Body, color = CfColor.TextTertiary)
            },
            singleLine = true,
            shape = RoundedCornerShape(CfDimen.RadiusCard),
            // ⚠️ 화면 02와 같은 이유로 `Search`가 아니라 `Done`이다 — 검색은 타이핑
            //    중에 자동으로 돌아서 눌러야 하는 키가 없다.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CfColor.Background,
                unfocusedContainerColor = CfColor.Background,
                focusedIndicatorColor = CfColor.Primary,
                unfocusedIndicatorColor = CfColor.Border,
                cursorColor = CfColor.Primary,
            ),
        )
        Spacer(Modifier.height(CfDimen.GapMedium))

        when (val state = vm.list) {
            // 빈 검색창에는 아무 말도 하지 않는다.
            FriendSearchUi.Idle -> Unit

            FriendSearchUi.TooShort -> Text(
                "두 글자 이상 입력해 주세요",
                style = CfText.Body,
                color = CfColor.TextSecondary,
            )

            // 찾는 중에는 문구를 바꾸지 않는다 — 0.3초마다 글자가 바뀌면 읽을 수 없다.
            FriendSearchUi.Searching -> Unit

            FriendSearchUi.NoResult -> Text(
                "그 닉네임을 가진 사람이 없어요",
                style = CfText.Body,
                color = CfColor.TextSecondary,
            )

            is FriendSearchUi.Failed -> Column {
                // 3절 토스트의 네트워크 오류 문구. **새 문구를 쓰지 않는다.**
                Text(
                    "연결이 불안정해요. 잠시 후 다시 시도해 주세요.",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )
                Spacer(Modifier.height(CfDimen.GapSmall))
                CfTextButton(text = "다시 시도", onClick = vm::retry)
            }

            is FriendSearchUi.Loaded -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
            ) {
                items(vm.rowsWithLocalState(state.rows), key = { it.id }) { row ->
                    SearchResultRow(
                        row = row,
                        onAdd = { id ->
                            vm.add(id) { ok ->
                                // 🔴 **`{이름}님과 친구가 되었어요`를 쓰지 않는다.** 요청은
                                //    `pending`으로 들어가고 수락은 상대만 할 수 있다(C-2).
                                toast(if (ok) CfToast.FRIEND_REQUEST_SENT else CfToast.NETWORK_ERROR)
                            }
                        },
                        // 🔵 **검색 결과에서도 수락할 수 있다**(2026-08-27). 그전에는 이
                        //    자리에 `친구 추가`가 떠서, 누르면 반대 방향 요청이 하나 더
                        //    생기고 **아무도 친구가 되지 않았다**([FriendRules.State.INCOMING]).
                        onAccept = { id ->
                            vm.accept(id) { ok ->
                                if (ok) {
                                    namedToast(CfToast.FRIEND_ACCEPTED, row.nickname)
                                    onFriendsChanged()
                                    // 목록의 그 줄을 `이미 친구예요`로 바꾸려면 다시 물어야 한다.
                                    vm.retry()
                                } else {
                                    toast(CfToast.FRIEND_ACCEPT_FAILED)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    row: FriendRules.Found,
    onAdd: (String) -> Unit,
    onAccept: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.nickname)
        Spacer(Modifier.width(CfDimen.GapMedium))
        Text(
            row.nickname,
            style = CfText.BodyBold,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (row.state) {
            // 2절 16번의 라벨을 그대로 쓴다 — 같은 동작을 화면마다 다르게 부르지 않는다.
            FriendRules.State.NONE ->
                CfSmallButton(text = "친구 추가", onClick = { onAdd(row.id) })

            FriendRules.State.REQUESTED ->
                Text("요청 보냄", style = CfText.Body, color = CfColor.TextTertiary)

            FriendRules.State.FRIEND ->
                Text("이미 친구예요", style = CfText.Body, color = CfColor.TextSecondary)

            // A 문서 3절 `화면 19에 받은 친구 요청이 뜬다`의 `수락`을 그대로 쓴다 —
            // 같은 동작을 화면 자리마다 다르게 부르지 않는다.
            FriendRules.State.INCOMING ->
                CfSmallButton(text = "수락", onClick = { onAccept(row.id) })
        }
    }
}

/**
 * 화면 19의 탭 1칸.
 *
 * ⚠️ `RowScope` 확장으로 둔다. `Modifier.weight`는 **RowScope 안에서만** 쓸 수 있고,
 *    `fillMaxWidth(0.5f)`로 흉내내면 두 탭 라벨 길이가 달라졌을 때(`내 친구 8` vs
 *    `초대하기`) 폭이 어긋난다.
 */
@Composable
private fun RowScope.FriendTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = CfDimen.GapMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = if (selected) CfText.BodyBold else CfText.Body,
            color = if (selected) CfColor.Primary else CfColor.TextSecondary,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) CfColor.Primary else CfColor.Border),
        )
    }
}

// ⚠️ **`ContactsPermissionPrompt`를 지웠다**(2026-08-09). 연락처 매칭이 없는 동안
//    권한을 받아도 할 일이 없어서 부르는 곳이 사라졌고, 안 부르는 `@Composable`을
//    남겨 두면 "권한을 요청하는 화면이 있다"고 읽힌다. 문구는 A 문서 3절
//    (`지인을 찾으려면 연락처 권한이 필요해요` / `번호는 저장하지 않아요`)에 그대로
//    있으므로 **매칭을 붙일 때 거기서 다시 가져온다** — 여기 코드가 원본이 아니다.

/**
 * `초대하기` 탭 — 초대 링크 하나만 둔다 (와이어프레임 주석 ④의 대체 화면).
 *
 * 원래는 연락처 권한을 거부했을 때만 쓰던 화면이다. 연락처 매칭이 붙기 전까지는
 * **모든 사용자가 이 화면을 본다** — 가짜 연락처 목록을 그리는 것보다 낫다.
 *
 * **재요청 버튼을 두지 않는다.** 주석이 "권한 재요청은 강요하지 않는다"고 못 박았고,
 * 연락처 없이도 초대 링크는 그대로 동작한다.
 *
 * ## 🔴 2026-09-13: 이 화면이 **거짓말을 하고 있었다** (A 문서 3절 ⑨)
 *
 * `링크를 받은 지인이 가입하면 자동으로 연결돼요`라고 말했는데 **그걸 하는 코드가
 * 0줄이었다** — 링크는 파라미터 없는 스토어 URL이고, 서버에 「누가 초대했는지」를 적는
 * 컬럼이 없다. 🔴 **여기서 결함은 코드가 아니라 문장이다** — 버튼은 잘 눌리고 공유
 * 시트도 정상으로 뜬다. 그래서 빈 람다·`TODO`·죽은 버튼을 재는 검사가 **전부 초록**이었다.
 *
 * ⚠️ **함정이 하나 더 있었다.** 이 파일의 옛 주석은 "연락처 매칭이 붙을 때까지"라고
 *    적어서 **언젠가 붙는 것처럼** 읽혔다. 실제로는 공개된 `docs/legal/privacy.html`
 *    §1 3)이 「연락처 권한 자체를 요청하지 않습니다」라고 약속했으므로 **영구히 안 붙는다.**
 *    → 연락처를 가리키는 문구는 임시가 아니라 **틀린 문구**였다.
 *
 * 🔵 **지금 문구는 이미 도는 것만 가리킨다** — 헤더 `검색` → [FriendSearchPanel] →
 *    `친구 추가`(`pending`) → 상대가 `수락`. C-2(상호 수락)를 문장에 넣은 이유가 그것이다:
 *    `친구가 돼요`로 끝내면 **요청을 보낸 것만으로 친구가 된다는 새 거짓말**이 된다.
 */
@Composable
private fun ContactsDeniedFallback(
    /** `초대 링크 보내기` → 공유 시트(2026-08-13 · A 문서 3절 ①). */
    onInvite: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "닉네임으로 친구를 찾을 수 있어요",
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            "오른쪽 위 검색으로 닉네임을 찾아 요청을 보내면, 상대가 수락할 때 친구가 돼요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapLarge))
        CfPrimaryButton(text = "초대 링크 보내기", onClick = onInvite)
        // 🔴 **버튼은 그대로 두고 링크가 404인 사실을 말한다.** 지우면 와이어프레임과
        //    어긋나고(3절 죽은 버튼 처리 원칙), `준비 중` 토스트로 뭉개면
        //    [com.catchflower.app.ui.DeadButtonTest]가 막아 둔 되돌림이 된다.
        if (!AppLinks.STORE_LISTING_LIVE) {
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(
                "지금은 스토어 준비 중이라 링크가 열리지 않아요",
                style = CfText.Caption,
                color = CfColor.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * `내 친구 {n}` 탭 — 이미 친구인 사람. 랭킹과 같은 목록이라 닉네임만 보여준다.
 *
 * 🔴 **[all]은 `friend_ranking`이 준 실제 목록이다.** [friendCount]와 **같은 출처**라야
 *    한다 — 예전에는 헤더만 서버 숫자였고 목록은 더미여서 `친구 0명` 아래에 8명이
 *    깔렸다(클래스 주석). 두 값의 출처가 갈리면 그 모순이 다시 생긴다.
 */
@Composable
private fun MyFriendsList(
    friendCount: Int?,
    all: List<RankedEntry>,
    vm: FriendsViewModel,
    onFriendsChanged: () -> Unit,
) {
    // 나를 뺀다 — `friend_ranking`은 내 행을 같이 준다(랭킹 화면이 `나`를 표시해야 해서).
    // ⚠️ `remember(all)`로 키를 준다. 키 없이 `remember`만 쓰면 서버 응답이 늦게 와도
    //    **첫 프레임의 빈 목록이 그대로 남는다**(더미 시절에는 항상 값이 있어서 안 보였다).
    val friends = remember(all) { all.filterNot { it.entry.isMe } }

    // 🔴 **탭을 열 때 한 번 읽는다.** `Unit` 키라 탭을 오갈 때마다 다시 부르지 않는다 —
    //    수락·거절 뒤 갱신은 [FriendsViewModel.loadIncoming]이 스스로 한다.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadIncoming() }

    val incoming = vm.incoming
    // 받은 요청이 있으면 **빈 상태 문구를 쓰지 않는다.** `아직 겨룰 친구가 없어요`를
    // 전체 화면으로 덮으면 수락 버튼이 그 아래 묻혀 보이지 않는다 — 요청이 와 있는데
    // 화면은 "친구를 만들 방법이 검색뿐"이라고 말하는 상태가 된다.
    // (2026-09-13까지 이 자리의 문구는 `연락처`였다 — A 문서 3절 ⑨)
    val hasIncoming = incoming is IncomingUi.Loaded || incoming is IncomingUi.Failed

    // A 문서 3절 빈 상태 `친구 없음`. **문구를 새로 쓰지 않는다.**
    if (friends.isEmpty() && !hasIncoming) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(CfDimen.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "아직 겨룰 친구가 없어요",
                style = CfText.Section,
                color = CfColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(CfDimen.GapSmall))
            Text(
                "닉네임으로 친구를 찾아보세요",
                style = CfText.Body,
                color = CfColor.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val toast = rememberToaster()
    val namedToast = rememberNamedToaster()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CfDimen.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        // ── 받은 친구 요청 (A 문서 3절 · 2026-08-27) ──
        // 🔴 **0건이면 아무것도 안 그린다.** `받은 친구 요청 0`을 그리면 늘 보이는
        //    빈 섹션이 되고, 그건 죽은 자리다(탭을 안 만든 것과 같은 이유).
        when (incoming) {
            IncomingUi.Idle, IncomingUi.Loading, IncomingUi.Empty -> Unit

            is IncomingUi.Failed -> item {
                Column {
                    Text(
                        "받은 요청을 불러오지 못했어요",
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                    )
                    Spacer(Modifier.height(CfDimen.GapSmall))
                    CfTextButton(text = "다시 시도", onClick = vm::loadIncoming)
                }
            }

            is IncomingUi.Loaded -> {
                item {
                    Text(
                        "받은 친구 요청 ${incoming.rows.size}",
                        style = CfText.Section,
                        color = CfColor.TextPrimary,
                    )
                }
                items(incoming.rows, key = { "incoming-${it.requesterId}" }) { row ->
                    IncomingRequestRow(
                        row = row,
                        onAccept = {
                            vm.accept(row.requesterId) { ok ->
                                if (ok) {
                                    namedToast(CfToast.FRIEND_ACCEPTED, row.nickname)
                                    // 🔴 여기서 랭킹을 다시 읽는다 — 안 읽으면
                                    //    `내 친구 {n}`이 그대로여서 수락이 실패한
                                    //    것처럼 보인다([onFriendsChanged] 주석).
                                    onFriendsChanged()
                                } else {
                                    toast(CfToast.FRIEND_ACCEPT_FAILED)
                                }
                            }
                        },
                        onDecline = {
                            vm.decline(row.requesterId) { ok ->
                                toast(
                                    if (ok) CfToast.FRIEND_DECLINED else CfToast.FRIEND_ACCEPT_FAILED,
                                )
                            }
                        },
                    )
                }
            }
        }

        item {
            Text(
                friendCount?.let { "친구 ${it}명" } ?: "친구",
                style = CfText.Section,
                color = CfColor.TextPrimary,
            )
        }
        items(friends, key = { it.entry.userId }) { friend ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CfDimen.RadiusCard))
                    .background(CfColor.Surface)
                    .padding(CfDimen.GapMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(friend.entry.nickname)
                Spacer(Modifier.width(CfDimen.GapMedium))
                Text(
                    friend.entry.nickname,
                    style = CfText.BodyBold,
                    color = CfColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${friend.entry.speciesCount}종",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )
            }
        }
    }
}

// 🔴 **`InviteList`·`ContactRow`·`SectionLabel`을 지웠다**(2026-08-09).
//    연락처에서 찾은 친구 3명(`김영희 010-2•••-4567` …)과 미가입 지인 3명을 그리던
//    자리다. 전부 옛 `DummyRanking`의 **가짜 사람·가짜 번호**였고, 화면은
//    `연락처에 캐치플라워 이웃 3명이 있어요`라고 **읽은 적도 없는 연락처를 읽은 것처럼**
//    말했다. 마스킹(`maskPhone`)까지 성실하게 해서 더 진짜처럼 보였다.
//
//    ⚠️ **다시 만들 때는 연락처 매칭(전화번호 해시 대조)과 같이 붙인다.** 화면만
//    되살리면 같은 사고가 그대로 돌아온다. 권한 요청 UI([ContactsPermissionPrompt])는
//    그때 쓰려고 **지우지 않고 남겨 뒀다.**

/**
 * 받은 친구 요청 한 줄 — `{닉네임}` + `수락` / `거절` (A 문서 3절 · 2026-08-27).
 *
 * ⚠️ **`거절`을 [CfTextButton]으로 둔다.** `수락`과 같은 무게로 그리면 중장년 타깃에서
 *    둘 중 무엇이 기본 행동인지 알 수 없다(1절 Ghost 규칙).
 *
 * ⚠️ **닉네임이 유일한 단서다** — 종수·지역이 없다(A 문서 4절 27번).
 */
@Composable
private fun IncomingRequestRow(
    row: FriendRules.Incoming,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.nickname)
        Spacer(Modifier.width(CfDimen.GapMedium))
        Text(
            row.nickname,
            style = CfText.BodyBold,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CfTextButton(text = "거절", onClick = onDecline)
        Spacer(Modifier.width(CfDimen.GapSmall))
        CfSmallButton(text = "수락", onClick = onAccept)
    }
}

/**
 * 이름 첫 글자 아바타.
 *
 * 프로필 사진이 없다. 회색 원만 두면 행이 서로 구분되지 않아서 첫 글자를 넣는다.
 */
@Composable
private fun Avatar(name: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(CfColor.PrimaryLight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1),
            style = CfText.BodyBold,
            color = CfColor.Primary,
        )
    }
}
