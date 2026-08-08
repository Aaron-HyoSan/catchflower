package com.catchflower.app.ui.ranking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.DummyRanking
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSmallButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/** 화면 19의 탭. */
private enum class FriendTab { MINE, INVITE }

/**
 * 화면 19 친구 관리·초대 — `19_친구관리.svg`.
 *
 * ⚠️ **연락처 권한을 여기서 요청한다.** iOS 세션이 화면 03(권한 안내)에서 연락처를
 *    뺐고((16) 기록) 그 이유가 "쓰는 코드가 없어서 권한을 받아도 할 일이 없다"였다.
 *    화면 19가 그 "할 일"이다. AOS도 같은 자리에서 요청해 양쪽 흐름을 맞춘다.
 *
 * ⚠️ **권한을 거부하면 이 화면 전체를 `초대 링크 보내기` 단독 화면으로 대체한다**
 *    (와이어프레임 주석 ④). 재요청을 강요하지 않는다 — 연락처 없이도 초대는 된다.
 *
 * ⚠️ **연락처를 실제로 읽지 않는다.** 목록은 더미다. 서버 대조(전화번호 해시)가
 *    없는 상태에서 실제 연락처를 읽으면 화면 03 고지("번호는 저장하지 않아요")를
 *    지킬 방법이 없다 — 대조할 상대가 없으니 읽을 이유도 없다.
 */
@Composable
fun FriendsScreen(
    /** `null`이면 **모른다** — 숫자를 안 쓴다(A 문서 `친구 수를 모를 때의 문구`). */
    friendCount: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(FriendTab.MINE) }

    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // 요청을 이미 했는가. 거부 후에도 대체 화면을 보여주려면 "아직 안 물어봄"과 구분해야 한다.
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsGranted = granted
        asked = true
    }

    Column(modifier.fillMaxSize()) {
        CfHeader(
            title = "친구",
            onBack = onBack,
            trailing = { CfTextButton(text = "검색", onClick = { /* TODO(서버 붙은 뒤): 닉네임 검색 */ }) },
        )

        Row(Modifier.fillMaxWidth()) {
            val mineLabel = friendCount?.let { "내 친구 $it" } ?: "내 친구"
            FriendTabItem(mineLabel, tab == FriendTab.MINE) { tab = FriendTab.MINE }
            FriendTabItem("초대하기", tab == FriendTab.INVITE) { tab = FriendTab.INVITE }
        }

        when {
            // ⚠️ **권한 게이트는 `초대하기` 탭에만 걸린다.** 처음에 화면 전체를 막았더니
            //    `내 친구 8`이 선택된 채로 "연락처 권한이 필요해요"가 떴다 — 내 친구
            //    목록은 우리 서버 데이터라 연락처와 아무 관계가 없다. 권한을 안 준
            //    사용자가 이미 있는 친구도 못 보게 되는 건 명백한 잘못이다.
            tab == FriendTab.MINE -> MyFriendsList(friendCount)

            // 거부했다 → 초대 링크 단독으로 대체 (주석 ④). 재요청을 강요하지 않는다.
            asked && !contactsGranted -> ContactsDeniedFallback()

            !contactsGranted -> ContactsPermissionPrompt(
                onRequest = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )

            else -> InviteList()
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

/**
 * 권한을 아직 안 물어봤다.
 *
 * 문구는 A 문서 3절 권한 재요청 시트 `연락처` 항목을 쓴다:
 * `지인을 찾으려면 연락처 권한이 필요해요` / `번호는 저장하지 않아요`.
 */
@Composable
private fun ContactsPermissionPrompt(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "지인을 찾으려면 연락처 권한이 필요해요",
            style = CfText.Section,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text("번호는 저장하지 않아요", style = CfText.Body, color = CfColor.TextSecondary)
        Spacer(Modifier.height(CfDimen.GapLarge))
        CfPrimaryButton(text = "연락처에서 찾기", onClick = onRequest)
    }
}

/**
 * 권한 거부 — 초대 링크 단독 화면 (주석 ④).
 *
 * **재요청 버튼을 두지 않는다.** 주석이 "권한 재요청은 강요하지 않는다"고 못 박았고,
 * 연락처 없이도 초대 링크는 그대로 동작한다.
 */
@Composable
private fun ContactsDeniedFallback() {
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
        CfPrimaryButton(text = "초대 링크 보내기", onClick = { /* TODO: 공유 시트 */ })
    }
}

/** `내 친구 {n}` 탭 — 이미 친구인 사람. 랭킹과 같은 목록이라 닉네임만 보여준다. */
@Composable
private fun MyFriendsList(friendCount: Int?) {
    // 목록을 한 번만 만든다. `items` 블록 안에서 `friends()`를 다시 부르면
    // 행마다 리스트를 새로 만들고 필터링한다.
    val friends = remember { DummyRanking.friends().filterNot { it.isMe } }
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
        items(friends, key = { it.userId }) { friend ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CfDimen.RadiusCard))
                    .background(CfColor.Surface)
                    .padding(CfDimen.GapMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(friend.nickname)
                Spacer(Modifier.width(CfDimen.GapMedium))
                Text(
                    friend.nickname,
                    style = CfText.BodyBold,
                    color = CfColor.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${friend.speciesCount}종", style = CfText.Body, color = CfColor.TextSecondary)
            }
        }
    }
}

/** `초대하기` 탭 — 가입자는 즉시 추가, 미가입자는 초대. */
@Composable
private fun InviteList() {
    val onService = remember { DummyRanking.contactsOnService() }
    val toInvite = remember { DummyRanking.contactsToInvite() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CfDimen.ScreenPadding,
            end = CfDimen.ScreenPadding,
            top = CfDimen.Gap,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        item {
            // 안내 박스 — 매칭 결과 요약 (주석 ①).
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CfDimen.RadiusCard))
                    .background(CfColor.PrimaryLight)
                    .padding(CfDimen.Gap),
            ) {
                Text(
                    "연락처에 캐치플라워 이웃 ${onService.size}명이 있어요",
                    style = CfText.BodyBold,
                    color = CfColor.Primary,
                )
                Spacer(Modifier.height(CfDimen.GapTiny))
                Text(
                    "친구로 추가하면 순위를 함께 볼 수 있어요",
                    style = CfText.Caption,
                    color = CfColor.TextSecondary,
                )
            }
        }

        item { SectionLabel("연락처에서 찾은 친구") }
        items(onService, key = { it.phone }) { contact ->
            ContactRow(contact, actionLabel = "추가", showPhone = true)
        }

        item { SectionLabel("아직 가입하지 않은 지인") }
        items(toInvite, key = { it.phone }) { contact ->
            // 미가입자는 번호를 보여주지 않는다 — 우리 서비스 사용자가 아니라
            // 대조할 근거가 없고, 화면에 띄울 이유도 없다.
            ContactRow(contact, actionLabel = "초대하기", showPhone = false)
        }

        item {
            Spacer(Modifier.height(CfDimen.GapSmall))
            CfPrimaryButton(text = "초대 링크 보내기", onClick = { /* TODO: 공유 시트 */ })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = CfText.Section, color = CfColor.TextPrimary)
}

/**
 * 연락처 행.
 *
 * ⚠️ 번호는 [KoreanText.maskPhone]으로 **반드시 마스킹한다** (A 문서 `마스킹 필수`).
 */
@Composable
private fun ContactRow(
    contact: DummyRanking.Contact,
    actionLabel: String,
    showPhone: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(CfColor.Surface)
            .padding(CfDimen.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact.name)
        Spacer(Modifier.width(CfDimen.GapMedium))
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = CfText.BodyBold, color = CfColor.TextPrimary, maxLines = 1)
            if (showPhone) {
                Text(
                    KoreanText.maskPhone(contact.phone),
                    style = CfText.Tiny,
                    color = CfColor.TextTertiary,
                )
            }
        }
        Spacer(Modifier.width(CfDimen.GapSmall))
        CfSmallButton(text = actionLabel, onClick = { /* TODO(서버 붙은 뒤): 친구 요청 */ })
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
