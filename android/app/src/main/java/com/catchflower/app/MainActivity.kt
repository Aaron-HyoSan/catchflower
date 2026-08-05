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
import com.catchflower.app.ui.component.CfBottomNav
import com.catchflower.app.ui.component.NavTab
import com.catchflower.app.ui.dex.DexDetailScreen
import com.catchflower.app.ui.dex.DexFilterSheet
import com.catchflower.app.ui.dex.DexHomeScreen
import com.catchflower.app.ui.dex.DexViewModel
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

                    NavTab.MAP -> Placeholder(tab.label, "화면 14~16")
                    NavTab.CAPTURE -> Placeholder(tab.label, "화면 07~13")
                    NavTab.RANKING -> Placeholder(tab.label, "화면 17~19")
                    NavTab.MY -> Placeholder(tab.label, "화면 20~21")
                }
            }
        }

        CfBottomNav(
            current = tab,
            onSelect = { selected ->
                // 도감 탭을 다시 누르면 상세에서 목록으로 돌아온다.
                if (selected == tab && selected == NavTab.DEX) detailFlowerId = null
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
