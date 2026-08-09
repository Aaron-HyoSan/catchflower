package com.catchflower.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.ui.component.CfIcon
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 03 권한 안내 — `03_권한안내.svg`. **문구는 A 문서 03번 표가 전부다.**
 *
 * **왜 시스템 대화상자 앞에 화면을 하나 두는가.** 안드로이드는 두 번 거부하면
 * 그 권한을 **영구히 못 묻는다**(`shouldShowRequestPermissionRationale`도 false).
 * 아무 설명 없이 대화상자를 먼저 띄우면 사용자는 뭘 묻는지 모르는 채 거부하고,
 * 그 뒤로는 설정 앱까지 들어가야 복구된다. 먼저 왜 필요한지 보여주고 묻는다.
 *
 * ⚠️ **토글을 그리지 않는다.** 와이어프레임 주석 ①은 `카메라 = 필수, 토글 고정 ON`인데,
 *    **끌 수 없는 스위치는 스위치가 아니다** — 눌러도 안 움직이면 고장으로 읽힌다.
 *    `필수`/`선택` 배지가 같은 정보를 준다.
 *
 * ⚠️ **거부해도 다음으로 넘어간다.** 필수는 카메라지만 여기서 막아 세우면
 *    도감(04)조차 못 보게 된다 — `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이
 *    제한돼요`가 이 화면의 약속이다. 촬영 진입 시점에 다시 안내한다.
 */
@Composable
fun PermissionIntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 요청 중에는 CTA를 막는다. 연달아 누르면 요청이 겹친다.
    var requesting by remember { mutableStateOf(false) }

    /**
     * ⚠️ **카메라와 위치를 한 번에 요청한다.** 따로 부르면 두 번째 요청이 첫 대화상자가
     *    닫히기 전에 나가서 조용히 버려진다 — iOS에서 "카메라만 묻고 위치는 아예 못
     *    물어보게 된다"로 겪은 것과 같은 함정이다.
     *
     * ⚠️ **연락처는 여기서 요청하지 않는다.** 설명 줄은 스펙대로 남긴다(앞으로 무엇에
     *    쓰는지 미리 알리는 것이 그 칸의 목적이다). 지금 받아도 쓸 코드가 없고
     *    (화면 19가 연락처를 읽지 않는다), **쓰지 않는 권한을 미리 받는 앱은 심사에서도
     *    지적된다.** 화면 19를 만들 때 그 화면에서 묻는다.
     */
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 결과를 보지 않는다. 허용이든 거부든 다음으로 간다.
        requesting = false
        onFinish()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PermissionIntroPalette.ScreenBackground)
            .statusBarsPadding(),
    ) {
        Header()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CfDimen.ScreenPadding)
                .padding(top = CfDimen.GapLarge, bottom = CfDimen.GapLarge),
        ) {
            // 🔴 **제목이 카드 수를 센다.** A 문서 원문은 `이 세 가지만…`인데 숫자가
            //    문장에 박혀 있어서, 예선처럼 연락처 카드를 빼면 **화면에 없는 세 번째
            //    카드를 세는 제목**이 된다(실측: 카드 2장 + 제목 `세 가지`).
            //    문구는 A 문서 3절 `화면 03에서 권한 카드가 두 장일 때의 제목`에 있다.
            Text(
                text = PermissionIntroItem.title(PermissionIntroItem.all.size),
                style = CfText.Hero,
                color = PermissionIntroPalette.Title,
            )
            Spacer(Modifier.height(CfDimen.GapMedium))
            Text(
                text = "허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요.",
                style = CfText.Body,
                color = PermissionIntroPalette.Description,
            )
            Spacer(Modifier.height(CfDimen.GapLarge))

            PermissionIntroItem.all.forEach { item ->
                PermissionRow(item)
                Spacer(Modifier.height(CfDimen.GapMedium))
            }

            Spacer(Modifier.height(CfDimen.GapSmall))
            AssuranceBox()
        }

        Footer(
            enabled = !requesting,
            onClick = {
                requesting = true
                // ⚠️ **요청을 띄우기 전에 기록한다.** 결과 콜백에서 기록하면, 사용자가
                //    대화상자를 바깥 탭으로 닫아 콜백이 안 올 때 플래그가 남지 않아
                //    다음 실행에서 온보딩이 또 뜬다.
                com.catchflower.app.data.OnboardingState.markDone(context)
                launcher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        // COARSE와 FINE을 **함께** 요청한다. API 31+에서 FINE만 요청하면
                        // `대략적 위치` 선택지가 나오지 않는다. 동 단위면 대략도 충분하다.
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                )
            },
        )
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PermissionIntroPalette.HeaderBackground)
            .padding(horizontal = CfDimen.ScreenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "권한 안내",
            style = CfText.ScreenTitle,
            color = PermissionIntroPalette.HeaderTitle,
            modifier = Modifier.weight(1f),
        )
        // 온보딩 2단계 중 2번째. 화면 02(지역선택)는 카카오맵 대기라 지금은 여기가
        // 첫 화면이지만 **표기는 스펙대로 둔다** — 02가 붙을 때 이 숫자를 다시 안 손대게.
        Text(text = "2/2", style = CfText.Body, color = PermissionIntroPalette.HeaderStep)
    }
}

@Composable
private fun PermissionRow(item: PermissionIntroItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(PermissionIntroPalette.CardBackground)
            .padding(14.dp)
            // 세 줄을 한 덩어리로 읽어준다 — 따로 읽으면 어느 권한 설명인지 알 수 없다.
            .clearAndSetSemantics {
                contentDescription =
                    "${item.title} ${if (item.isRequired) "필수" else "선택"}. " +
                        "${item.purpose} ${item.caveat}"
            },
        horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium),
    ) {
        // 아이콘. **납품 아이콘 3종을 쓴다**(`꽃 촬영`·`위치`·`친구 추가` · 2026-08-09).
        //
        // ⚠️ 처음에는 `item.title.take(1)`로 첫 글자(`카`·`위`·`연`)를 넣어 뒀다.
        //    화면만 보면 원 안에 글자가 들어차 있어 "아이콘 자리"로 보이지만,
        //    **와이어프레임이 요구한 건 아이콘이었다.** 그다음엔 직접 그린 카메라 글리프와
        //    Material `Place`·`Person`으로 채웠고, 이제 납품 아트로 바꿨다(그 글리프는
        //    호출처가 0이 돼서 지웠다 — `BottomNav.kt`).
        //    같은 앱 안에서 위치를 두 가지 그림으로 그리면 같은 개념이 화면마다
        //    달라 보인다 — 중장년 타깃에서 아이콘 일관성은 학습 비용에 직결된다.
        //    **하단 내비의 `지도`·`마이`와 같은 세트여야 한다는 것이 판정 기준이다.**
        //
        // ⚠️ **`tint`를 주지 않는다.** 납품 아이콘은 컬러이고, 이 원의 배경은
        //    `PrimaryLight`(연한 초록)라서 초록으로 덮으면 거의 안 보인다([CfIcon]).
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PermissionIntroPalette.IconCircleBackground),
            contentAlignment = Alignment.Center,
        ) {
            // contentDescription은 주지 않는다 — 바깥 Row가 `clearAndSetSemantics`로
            // 카드 전체를 한 덩어리로 읽으므로 여기서 주면 무시되거나 중복된다.
            CfIcon(
                id = when (item.icon) {
                    PermissionIcon.CAMERA -> R.drawable.ic_capture
                    PermissionIcon.PLACE -> R.drawable.ic_place
                    // 연락처 = `친구 추가`. 납품 세트에 `연락처`는 없고, 이 권한이
                    // 하는 일이 곧 친구 찾기다(`purpose` 문구가 그렇게 말한다).
                    PermissionIcon.PERSON -> R.drawable.ic_person_add
                },
                size = 20.dp,
                // contentDescription은 주지 않는다 — 위 Row가 카드 전체를 한 덩어리로 읽는다.
                contentDescription = null,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
            ) {
                Text(text = item.title, style = CfText.Section, color = PermissionIntroPalette.CardTitle)
                RequirementBadge(item.isRequired)
            }
            Spacer(Modifier.height(CfDimen.GapTiny))
            Text(text = item.purpose, style = CfText.Body, color = PermissionIntroPalette.CardPurpose)
            // ⚠️ **`TextTertiary`를 쓰지 않는다.** 와이어프레임의 단서 줄 색은 `#9a9a9a`고
            //    `TextTertiary`(#767676)도 "대비 4.5:1"이라고 적혀 있지만, 그 4.5는
            //    **흰 배경 기준**이다. 이 카드 배경은 `Surface`(#F7F7F5)라서 실제로는
            //    **4.23:1** — B 문서 25행(`명도 대비 4.5:1 이상`)을 넘지 못한다.
            //    게다가 이건 날짜·개수가 아니라 문장이다(`앨범 사진은 등록할 수 없어요.`).
            //    색을 옅게 만드는 건 노안 타깃에서 가장 하면 안 되는 절약이다.
            Text(text = item.caveat, style = CfText.Caption, color = PermissionIntroPalette.CardCaveat)
        }
    }
}

/** `필수` / `선택`. **색만으로 구분하지 않는다** — 글자가 같은 말을 한다. */
@Composable
private fun RequirementBadge(isRequired: Boolean) {
    Text(
        text = if (isRequired) "필수" else "선택",
        style = CfText.Tiny,
        color = if (isRequired) PermissionIntroPalette.RequiredBadge else PermissionIntroPalette.OptionalBadge,
        modifier = Modifier
            .clip(RoundedCornerShape(CfDimen.RadiusChip))
            .background(
                if (isRequired) {
                    PermissionIntroPalette.RequiredBadgeBackground
                } else {
                    PermissionIntroPalette.OptionalBadgeBackground
                },
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * 안심 박스. 와이어프레임 주석 ④: `'자동 공개가 아니다'를 가입 단계에서 못 박는다.`
 * 지도에 꽃 위치가 올라가는 앱이라, 이 약속이 없으면 촬영 자체를 망설인다.
 */
@Composable
private fun AssuranceBox() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CfDimen.RadiusCard))
            .background(PermissionIntroPalette.AssuranceBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
    ) {
        listOf(
            "촬영한 사진은 내 도감에만 저장됩니다.",
            "지도 공유는 매번 직접 선택해요.",
        ).forEach { line ->
            Text(text = line, style = CfText.Body, color = PermissionIntroPalette.AssuranceText)
        }
    }
}

@Composable
private fun Footer(enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PermissionIntroPalette.FooterBackground)
            .navigationBarsPadding()
            .padding(horizontal = CfDimen.ScreenPadding)
            .padding(top = CfDimen.GapMedium, bottom = CfDimen.GapSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CfPrimaryButton(text = "허용하고 시작하기", onClick = onClick, enabled = enabled)
        Spacer(Modifier.height(CfDimen.GapSmall))
        Text(
            text = "나중에 설정에서 바꿀 수 있어요",
            style = CfText.Caption,
            color = PermissionIntroPalette.FooterNote,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A 문서 03번 표의 세 줄.
 *
 * ⚠️ **문구를 화면에서 만들지 않는다.** 데이터로 두면 A 문서와 나란히 놓고 대조할 수 있다.
 *
 * 🔴 **연락처 줄은 예선 빌드에서 [all]에 넣지 않는다**(2026-08-09 · 실측으로 발견).
 *    목표 4에서 가짜 친구 목록과 `READ_CONTACTS` 선언을 지웠는데 **이 화면은 그대로
 *    남아서** `이미 가입한 지인을 친구로 연결합니다`라고 약속하고 있었다 —
 *    선언이 없으니 눌러도 아무 일도 일어나지 않는다. 선언만 남은 것과 **같은 종류의
 *    거짓**이고, 방향만 반대다(이번엔 화면이 남았다).
 *
 *    ⚠️ 연락처 매칭을 붙일 때 **[CONTACTS] 항목·매니페스트 선언·읽는 코드를 같이**
 *       되살린다. 문구는 [CONTACTS]에 그대로 보관해 뒀다 — A 문서에서 지우지 않았고,
 *       [PermissionCopyTest]가 그 문구를 계속 A 문서와 대조한다.
 */
internal data class PermissionIntroItem(
    val title: String,
    val isRequired: Boolean,
    val purpose: String,
    val caveat: String,
    val icon: PermissionIcon,
) {
    companion object {
        /**
         * 연락처 안내 — **예선 빌드에서는 그리지 않는다.**
         *
         * 🔴 **지우지 않고 여기 남겨 둔다.** 문구는 A 문서 03절에 그대로 있고
         *    [PermissionCopyTest]가 그것과 대조한다. 코드에서 없애면 **되살릴 때
         *    문구를 다시 쓰게 되고**, 그게 "문구를 새로 쓰지 않는다"를 깨는 경로다.
         */
        val CONTACTS = PermissionIntroItem(
            title = "연락처",
            isRequired = false,
            purpose = "이미 가입한 지인을 친구로 연결합니다.",
            caveat = "번호는 암호화해 보관하며 저장하지 않아요.",
            icon = PermissionIcon.PERSON,
        )

        /**
         * 화면이 실제로 그리는 줄.
         *
         * 🔴 **[CONTACTS]가 없다.** 연락처를 읽는 코드도, 매니페스트 선언도 없는
         *    빌드에서 "지인을 친구로 연결합니다"를 약속하면 **눌러도 아무 일이
         *    없는 안내**가 된다(실측: 온보딩 2/2에 그대로 떠 있었다).
         */
        val all = listOf(
            PermissionIntroItem(
                title = "카메라",
                isRequired = true,
                purpose = "꽃을 직접 촬영해 도감에 등록합니다.",
                caveat = "앨범 사진은 등록할 수 없어요.",
                icon = PermissionIcon.CAMERA,
            ),
            PermissionIntroItem(
                title = "위치",
                isRequired = false,
                purpose = "꽃을 발견한 장소를 지도에 남깁니다.",
                caveat = "끄면 지도 공유를 쓸 수 없어요.",
                icon = PermissionIcon.PLACE,
            ),
        )

        /**
         * A 문서 대조용 — **문서에 있는 세 줄 전부.**
         *
         * ⚠️ [all]과 나누는 이유: [PermissionCopyTest]가 "문구가 A 문서와 같은가"를
         *    재는 것과 "이번 빌드가 무엇을 그리는가"는 **다른 질문**이다. 하나로
         *    합치면 예선에서 뺀 줄의 문구가 **검증 대상에서 조용히 사라진다.**
         */
        val allInSpec = all + CONTACTS

        /**
         * 제목 — **카드 수를 센 문장**을 준다 (A 문서 3절 `화면 03에서 권한 카드가
         * 두 장일 때의 제목`).
         *
         * 🔴 **숫자를 뺀 문장으로 바꾸지 않는다.** `세 가지`는 "몇 개만 하면 된다"를
         *    적게 느끼게 하는 장치다 — 빼면 **끝이 안 보이는 절차**로 읽힌다.
         *
         * ⚠️ 세는 값이 [all]이어야 한다. [allInSpec]으로 세면 화면에 안 그리는
         *    카드까지 세어서 **원래 사고로 돌아간다.**
         *
         * @throws IllegalArgumentException 문구가 정해지지 않은 카드 수. 🔴 기본값으로
         *   `세 가지`를 돌려주면 카드가 4장이 돼도 **조용히 틀린 제목**이 나온다 —
         *   A 문서에 줄을 추가하라는 신호가 여기서 나야 한다.
         */
        fun title(cardCount: Int): String = when (cardCount) {
            2 -> "이 두 가지만 허용하면 준비 끝!"
            3 -> "이 세 가지만 허용하면 준비 끝!"
            else -> throw IllegalArgumentException(
                "권한 카드 ${cardCount}장에 맞는 제목이 A 문서에 없다 — " +
                    "3절 `화면 03에서 권한 카드가 두 장일 때의 제목`에 줄을 추가하고 여기 넣는다",
            )
        }
    }
}

/**
 * 카드 아이콘. **하단 내비와 같은 납품 세트를 쓴다** — 카메라는 셔터와 같은
 * `꽃 촬영`, 위치는 `위치`, 연락처는 `친구 추가`다.
 *
 * ⚠️ **리소스 id를 이 enum에 필드로 넣지 않는다.** `PermissionIntroItem`을 JVM
 *    테스트가 그대로 읽는데, id는 `R`(생성 클래스)을 끌고 들어온다 — 그리기 쪽에서
 *    `when`으로 옮긴다. 같은 이유로 `ImageVector`도 넣지 않았다.
 */
internal enum class PermissionIcon { CAMERA, PLACE, PERSON }

/**
 * 이 화면이 쓰는 **(글자색, 배경색) 쌍 전부.**
 *
 * ⚠️ **화면에 색을 직접 쓰지 않고 여기를 거치게 한 이유가 테스트다.** 처음에는
 *    대비 테스트가 `assertReadable(TextSecondary, Surface)`처럼 **쌍을 테스트에
 *    다시 적었다.** 그랬더니 화면의 색을 `TextTertiary`로 되돌리는 돌연변이를 심어도
 *    **테스트는 초록이었다** — 테스트가 검사한 건 화면이 아니라 자기가 적어 둔
 *    상수 쌍이었기 때문이다. 대비는 **쌍**의 속성이므로, 쌍이 한 곳에만 있어야
 *    검사에 의미가 있다.
 *
 * ⚠️ 그래서 **[PermissionIntroScreen]은 `CfColor`를 직접 참조하지 않는다.**
 *    새 문구·새 요소를 넣을 때도 여기에 쌍을 먼저 추가한다.
 */
internal object PermissionIntroPalette {

    data class Pair(val label: String, val foreground: Color, val background: Color)

    val ScreenBackground = CfColor.Background
    val CardBackground = CfColor.Surface
    val HeaderBackground = CfColor.Surface
    val AssuranceBackground = CfColor.PrimaryLight
    val IconCircleBackground = CfColor.PrimaryLight
    val RequiredBadgeBackground = CfColor.Primary
    val OptionalBadgeBackground = CfColor.GhostBackground

    val Title = CfColor.TextPrimary
    val Description = CfColor.TextSecondary
    val HeaderTitle = CfColor.TextPrimary
    val HeaderStep = CfColor.TextSecondary
    val CardTitle = CfColor.TextPrimary
    val CardPurpose = CfColor.TextSecondary

    /**
     * 단서 줄(`앨범 사진은 등록할 수 없어요.`).
     *
     * ⚠️ **[CfColor.TextTertiary]를 쓰지 않는다.** 그 색 주석의 `대비 4.5:1`은
     *    **흰 배경 기준**이고, 이 줄의 배경은 [CardBackground](#F7F7F5)라서
     *    실제 대비는 **4.23:1** — B 문서 25행(4.5:1 이상)을 넘지 못한다.
     *    게다가 날짜·개수가 아니라 문장이다. 노안 타깃에서 가장 하면 안 되는 절약이다.
     */
    val CardCaveat = CfColor.TextSecondary

    val RequiredBadge = CfColor.TextOnDark
    val OptionalBadge = CfColor.TextSecondary
    val AssuranceText = CfColor.TextPrimary
    val Icon = CfColor.Primary
    val FooterNote = CfColor.TextSecondary
    val FooterBackground = CfColor.Surface

    /** 대비 검사 대상 전체. **새 쌍을 추가하면 여기에도 넣는다.** */
    val pairs = listOf(
        Pair("제목", Title, ScreenBackground),
        Pair("설명", Description, ScreenBackground),
        Pair("헤더 제목", HeaderTitle, HeaderBackground),
        Pair("헤더 2/2", HeaderStep, HeaderBackground),
        Pair("카드 제목", CardTitle, CardBackground),
        Pair("카드 용도 줄", CardPurpose, CardBackground),
        Pair("카드 단서 줄", CardCaveat, CardBackground),
        Pair("필수 배지", RequiredBadge, RequiredBadgeBackground),
        Pair("선택 배지", OptionalBadge, OptionalBadgeBackground),
        Pair("안심 박스", AssuranceText, AssuranceBackground),
        Pair("아이콘", Icon, IconCircleBackground),
        Pair("하단 안내", FooterNote, FooterBackground),
    )
}
