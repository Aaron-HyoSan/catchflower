package com.catchflower.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchflower.app.ui.capture.CaptureFlow
import com.catchflower.app.ui.component.CfBottomNav
import com.catchflower.app.ui.component.NavTab
import com.catchflower.app.ui.dex.DexDetailScreen
import com.catchflower.app.ui.dex.DexFilterSheet
import com.catchflower.app.ui.dex.DexHomeScreen
import com.catchflower.app.ui.dex.DexViewModel
import com.catchflower.app.ui.my.MyScreen
import com.catchflower.app.ui.my.SeasonResultScreen
import com.catchflower.app.ui.ranking.FriendsScreen
import com.catchflower.app.ui.ranking.RankingScreen
import com.catchflower.app.ui.ranking.RankingViewModel
import com.catchflower.app.ui.theme.CatchFlowerTheme
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

class CatchFlowerApp : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CatchFlowerTheme {
                CatchFlowerRoot()
            }
        }
    }
}

/**
 * 앱 셸.
 *
 * ⚠️ 지금은 도감(화면 04·05·06·22)만 실물이다. 나머지 탭은 자리만 잡아 둔다 —
 *    하단 내비를 나중에 붙이면 화면마다 하단 여백 계산이 어긋나므로
 *    껍데기를 먼저 세운다.
 *
 * navigation-compose를 아직 쓰지 않는다. 로그인(화면 01~03)이 붙으면 시작
 * 목적지가 달라지므로 라우팅은 그때 한 번에 정한다.
 */
@Composable
private fun CatchFlowerRoot() {
    var tab by remember { mutableStateOf(NavTab.DEX) }
    // 도감 상세로 들어간 종. null이면 도감 홈이다.
    var detailFlowerId by remember { mutableStateOf<Int?>(null) }
    var filterOpen by remember { mutableStateOf(false) }

    val dexViewModel: DexViewModel = viewModel()
    val rankingViewModel: RankingViewModel = viewModel()

    // 랭킹·마이 탭에서 **위로 겹쳐 여는** 화면들. 탭 자체가 아니라서 NavTab에 넣지 않는다.
    // 두 탭이 같은 화면(친구 관리·지난 시즌)을 공유하므로 상태도 여기서 공유한다.
    var friendsOpen by remember { mutableStateOf(false) }
    var seasonResultOpen by remember { mutableStateOf(false) }

    // 화면 21은 **전면 화면**이다(와이어프레임 21 주석 ①: 시즌 종료 후 첫 실행 1회 강제 노출).
    // ⚠️ 탭 안에 넣었더니 하단 내비와 촬영 FAB이 위에 그려져서, FAB이 `결과 공유하기`
    //    버튼을 덮었다. 화면에서만 보이는 문제였다 — `시즌 2 시작하기`를 누르라는
    //    화면에서 다른 탭으로 샐 길을 열어 두는 것도 주석 ①과 어긋난다.
    if (seasonResultOpen) {
        SeasonResultScreen(
            onStartNewSeason = { seasonResultOpen = false },
            onClose = { seasonResultOpen = false },
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // 하단 내비가 없으므로 제스처 바를 직접 피한다. 안 주면 `결과 공유하기`가
                // 시스템 바 밑에 깔린다 — 여백은 [CfBottomNav]가 대신 주던 것이다.
                .navigationBarsPadding(),
        )
        return
    }

    // 촬영 흐름(07~12)은 **하단 내비 바깥**이다. 화면 07·08이 어두운 전체화면이고
    // 자체 헤더(`닫기`·`꽃 촬영`·`도움말`)를 갖고 있어서, 내비 안에 넣으면
    // 닫기 버튼과 하단 탭이 동시에 보이는 이상한 화면이 된다.
    if (tab == NavTab.CAPTURE) {
        CaptureFlow(
            onExit = { tab = NavTab.DEX },
            onShare = {
                // TODO(다음 단계): 화면 13 지도 공유 설정.
                // 지금 조용히 도감으로 보내면 "공유하기를 눌렀는데 아무 일도 안 났다"가 된다.
                tab = NavTab.DEX
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CfColor.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    NavTab.DEX -> {
                        val id = detailFlowerId
                        if (id == null) {
                            DexHomeScreen(
                                vm = dexViewModel,
                                onFlowerClick = { detailFlowerId = it },
                                onOpenFilter = { filterOpen = true },
                                onCapture = { tab = NavTab.CAPTURE },
                            )
                        } else {
                            DexDetailScreen(
                                vm = dexViewModel,
                                flowerId = id,
                                onBack = { detailFlowerId = null },
                            )
                        }
                    }

                    // 위에서 return하므로 여기 오지 않는다. when을 완전하게 두기 위해 명시한다.
                    NavTab.CAPTURE -> Unit
                    NavTab.MAP -> Placeholder(tab.label, "화면 14~16")
                    NavTab.RANKING -> when {
                        // 화면 19는 헤더에 `뒤로`가 있는 하위 화면이라 탭 안에서 대체한다.
                        friendsOpen -> FriendsScreen(
                            friendCount = rankingViewModel.friendCount,
                            onBack = { friendsOpen = false },
                        )

                        else -> RankingScreen(
                            vm = rankingViewModel,
                            onOpenFriends = { friendsOpen = true },
                            onOpenLastSeason = { seasonResultOpen = true },
                        )
                    }

                    NavTab.MY -> when {
                        friendsOpen -> FriendsScreen(
                            friendCount = rankingViewModel.friendCount,
                            onBack = { friendsOpen = false },
                        )

                        else -> MyScreen(
                            friendCount = rankingViewModel.friendCount,
                            onOpenFriends = { friendsOpen = true },
                            onOpenLastSeason = { seasonResultOpen = true },
                        )
                    }
                }
            }
        }

        CfBottomNav(
            current = tab,
            onSelect = { selected ->
                // 도감 탭을 다시 누르면 상세에서 목록으로 돌아온다.
                if (selected == tab && selected == NavTab.DEX) detailFlowerId = null
                // 탭을 옮기면 겹쳐 둔 하위 화면을 닫는다. 안 닫으면 `마이`에서 친구 관리를
                // 열어 둔 채 `랭킹`으로 갔을 때 랭킹 대신 친구 관리가 나온다.
                friendsOpen = false
                seasonResultOpen = false
                tab = selected
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (filterOpen) {
        DexFilterSheet(vm = dexViewModel, onDismiss = { filterOpen = false })
    }
}

/** 아직 안 만든 탭. 무엇이 들어올 자리인지 화면 번호로 남긴다. */
@Composable
private fun Placeholder(title: String, screens: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = CfText.ScreenTitle, color = CfColor.TextPrimary)
        Text(
            text = "$screens · 준비 중",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
