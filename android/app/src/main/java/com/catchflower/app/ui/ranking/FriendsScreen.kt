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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catchflower.app.data.model.RankedEntry
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.CfToast
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
 * 깔려 있었다. 헤더만 실측 소스로 바꾸고((31)) 목록은 [DummyRanking]에 남겨 둔
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(FriendTab.MINE) }
    // 예선 범위 밖 버튼(`검색`·`초대 링크 보내기`). 빈 람다로 두면 눌러도 아무 일이
    // 없어서 앱이 고장난 것으로 보인다((38)).
    val toast = rememberToaster()
    val notReady: () -> Unit = { toast(CfToast.NOT_READY) }

    Column(modifier.fillMaxSize()) {
        CfHeader(
            title = "친구",
            onBack = onBack,
            trailing = { CfTextButton(text = "검색", onClick = notReady) },
        )

        Row(Modifier.fillMaxWidth()) {
            val mineLabel = friendCount?.let { "내 친구 $it" } ?: "내 친구"
            FriendTabItem(mineLabel, tab == FriendTab.MINE) { tab = FriendTab.MINE }
            FriendTabItem("초대하기", tab == FriendTab.INVITE) { tab = FriendTab.INVITE }
        }

        when (tab) {
            FriendTab.MINE -> MyFriendsList(friendCount, friends)
            // 연락처 매칭이 없는 동안은 **초대 링크 하나만** 둔다. 가짜 연락처 목록을
            // 그리는 것보다 낫다(클래스 주석).
            FriendTab.INVITE -> ContactsDeniedFallback(notReady)
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
 */
@Composable
private fun ContactsDeniedFallback(
    /** 예선 범위 밖 `초대 링크 보내기`가 쓴다 — 공유 시트가 아직 없다. */
    notReady: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "초대 링크로도 친구가 될 수 있어요",
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            "링크를 받은 지인이 가입하면 자동으로 연결돼요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapLarge))
        CfPrimaryButton(text = "초대 링크 보내기", onClick = notReady)
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
private fun MyFriendsList(friendCount: Int?, all: List<RankedEntry>) {
    // 나를 뺀다 — `friend_ranking`은 내 행을 같이 준다(랭킹 화면이 `나`를 표시해야 해서).
    // ⚠️ `remember(all)`로 키를 준다. 키 없이 `remember`만 쓰면 서버 응답이 늦게 와도
    //    **첫 프레임의 빈 목록이 그대로 남는다**(더미 시절에는 항상 값이 있어서 안 보였다).
    val friends = remember(all) { all.filterNot { it.entry.isMe } }

    // A 문서 3절 빈 상태 `친구 없음`. **문구를 새로 쓰지 않는다.**
    if (friends.isEmpty()) {
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
                "연락처에서 지인을 찾아보세요",
                style = CfText.Body,
                color = CfColor.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(CfDimen.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
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
//    자리다. 전부 [DummyRanking]의 **가짜 사람·가짜 번호**였고, 화면은
//    `연락처에 캐치플라워 이웃 3명이 있어요`라고 **읽은 적도 없는 연락처를 읽은 것처럼**
//    말했다. 마스킹(`maskPhone`)까지 성실하게 해서 더 진짜처럼 보였다.
//
//    ⚠️ **다시 만들 때는 연락처 매칭(전화번호 해시 대조)과 같이 붙인다.** 화면만
//    되살리면 같은 사고가 그대로 돌아온다. 권한 요청 UI([ContactsPermissionPrompt])는
//    그때 쓰려고 **지우지 않고 남겨 뒀다.**

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
