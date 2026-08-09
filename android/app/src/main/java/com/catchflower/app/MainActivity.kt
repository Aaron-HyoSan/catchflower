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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.data.OnboardingState
import com.catchflower.app.ui.onboarding.PermissionIntroScreen
import com.catchflower.app.ui.capture.CaptureFlow
import com.catchflower.app.ui.component.CfBottomNav
import com.catchflower.app.ui.component.NavTab
import com.catchflower.app.ui.dex.DexDetailScreen
import com.catchflower.app.ui.dex.DexFilterSheet
import com.catchflower.app.ui.dex.DexHomeScreen
import com.catchflower.app.ui.dex.DexViewModel
import com.catchflower.app.ui.map.MapScreen
import com.catchflower.app.ui.map.MapViewModel
import com.catchflower.app.ui.my.MyScreen
import com.catchflower.app.ui.my.SeasonResultScreen
import com.catchflower.app.ui.ranking.FriendsScreen
import com.catchflower.app.ui.ranking.RankingScreen
import com.catchflower.app.ui.ranking.RankingViewModel
import com.catchflower.app.ui.region.RegionPickerScreen
import com.catchflower.app.ui.theme.CatchFlowerTheme
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

class CatchFlowerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 어떤 키가 들어왔는지 **실행 시점에** 남긴다.
        //
        // ⚠️ 키가 없으면 앱은 조용히 Mock으로 돈다 — 그게 설계지만, 로그가 없으면
        //    "실인식이 도는 줄 알았는데 Mock이었다"를 알 방법이 없다. iOS는 그 상태로
        //    `PlantNetRecognizer`를 한 번도 실행하지 않은 채 커밋까지 갔다.
        //    **값은 절대 찍지 않는다** — 있음/없음만 남긴다.
        //
        // 🔴 **카카오 키를 하나로 뭉쳐 찍지 않는다.** `Kakao=true` 한 줄만 남기던 때는
        //    REST 키만 있는 빌드도 그렇게 말했고, **지도만 회색인 이유를 로그에서 찾을
        //    수 없었다.** REST(주소↔좌표)와 지도(네이티브 앱 키)는 **서로 대체되지
        //    않는다** — `AppSecrets.hasKakaoKey`/`hasKakaoMapKey`를 나눈 이유가 이것이다.
        android.util.Log.i(
            "CatchFlower",
            "키 상태: PlantNet=${AppSecrets.hasPlantNetKey} " +
                "KakaoREST=${AppSecrets.hasKakaoKey} KakaoMap=${AppSecrets.hasKakaoMapKey} " +
                "Supabase=${AppSecrets.hasSupabase}" +
                if (AppSecrets.missingKeys.isEmpty()) "" else " · 없는 키 ${AppSecrets.missingKeys}",
        )
    }
}

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
 * navigation-compose를 아직 쓰지 않는다. 화면 01·02(로그인·지역선택)가 붙으면 시작
 * 목적지가 또 달라지므로 라우팅은 그때 한 번에 정한다.
 */
@Composable
private fun CatchFlowerRoot() {
    val context = LocalContext.current

    // 화면 03(권한 안내)은 **첫 실행에만** 보인다.
    //
    // ⚠️ **권한 상태로 분기하지 않는다.** 화면 03은 권한 게이트가 아니라 약속을 보여주는
    //    화면이다(`촬영한 사진은 내 도감에만 저장됩니다`). 권한으로 판단하면 재설치 후
    //    권한이 남은 사용자는 그 약속을 한 번도 못 본다. `OnboardingState` 주석 참고.
    //
    // ⚠️ **`remember`로 한 번만 읽는다.** 매 recomposition마다 읽으면 온보딩이 끝나
    //    플래그가 켜지는 순간 이 값이 바뀌면서 화면이 통째로 갈리는데,
    //    onFinish로 넘어가는 흐름과 겹쳐서 어느 쪽이 이겼는지 알 수 없게 된다.
    var onboardingDone by remember { mutableStateOf(OnboardingState.isDone(context)) }
    // 온보딩은 **02 → 03** 순서다(A 문서 헤더 표기 `1/2` · `2/2`).
    //
    // ⚠️ **플래그를 하나 더 만들지 않았다.** 완료 표시는 화면 03이 [OnboardingState]에
    //    남기므로, 02를 `나중에 하기`로 건너뛴 사용자도 03을 지나면 두 화면이 같이
    //    끝난다 — 그리고 02는 화면 17·20의 `동네 선택하기`로 언제든 다시 열 수 있다.
    //    02에 별도 플래그를 주면 "지역을 안 정한 사용자"에게 매 실행마다 온보딩이
    //    다시 뜨거나(강요), 반대로 저장 실패가 영구히 굳는다.
    var regionStepDone by remember { mutableStateOf(false) }
    if (!onboardingDone) {
        if (!regionStepDone) {
            RegionPickerScreen(
                // 저장 성공·`나중에 하기` 모두 여기로 온다. **둘을 구분하지 않는다** —
                // 다음 화면이 같고, 정했는지는 서버가 아는 사실이다(화면 17이 읽는다).
                onDone = { regionStepDone = true },
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        PermissionIntroScreen(
            onFinish = { onboardingDone = true },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    var tab by remember { mutableStateOf(NavTab.DEX) }
    // 도감 상세로 들어간 종. null이면 도감 홈이다.
    var detailFlowerId by remember { mutableStateOf<Int?>(null) }
    var filterOpen by remember { mutableStateOf(false) }

    val dexViewModel: DexViewModel = viewModel()
    val rankingViewModel: RankingViewModel = viewModel()
    // ⚠️ **지도 탭 안에서 만들지 않는다.** 탭을 옮길 때마다 새로 생기면 `MapView`가
    //    매번 다시 시작하고, 그때마다 네이티브 렌더러가 붙었다 떨어진다.
    val mapViewModel: MapViewModel = viewModel()

    // 랭킹·마이 탭에서 **위로 겹쳐 여는** 화면들. 탭 자체가 아니라서 NavTab에 넣지 않는다.
    // 두 탭이 같은 화면(친구 관리·지난 시즌)을 공유하므로 상태도 여기서 공유한다.
    var friendsOpen by remember { mutableStateOf(false) }
    var seasonResultOpen by remember { mutableStateOf(false) }

    // 화면 17·20의 `동네 선택하기`가 여는 자리. 화면 02를 위에 겹쳐 띄운다.
    //
    // ⚠️ **닫을 때 랭킹을 다시 읽어야 한다.** 안 읽으면 동네를 막 정하고 돌아온
    //    화면 17이 여전히 `활동 지역을 정하면 순위를 볼 수 있어요`를 보여준다 —
    //    저장은 성공했는데 화면만 안 바뀌므로 **저장이 안 된 것처럼 보인다.**
    var regionPickerOpen by remember { mutableStateOf(false) }

    // 화면 21은 **전면 화면**이다(와이어프레임 21 주석 ①: 시즌 종료 후 첫 실행 1회 강제 노출).
    // ⚠️ 탭 안에 넣었더니 하단 내비와 촬영 FAB이 위에 그려져서, FAB이 `결과 공유하기`
    //    버튼을 덮었다. 화면에서만 보이는 문제였다 — `시즌 2 시작하기`를 누르라는
    //    화면에서 다른 탭으로 샐 길을 열어 두는 것도 주석 ①과 어긋난다.
    if (seasonResultOpen) {
        SeasonResultScreen(
            // 🔴 **`null`이다. 예시 결과를 넣지 않는다.** 지난 시즌 순위를 보관하는
            //    곳이 서버에도 기기에도 없다 — 이유는 [SeasonResult]에 있다.
            //    강제로 뜨는 화면에 `연남동 4위`를 그리면 한 번도 랭킹에 든 적 없는
            //    사용자에게 4위를 받았다고 말한다.
            result = null,
            // 초기화 안내의 `모은 꽃 {N}종`. 도감과 **같은 값을 읽는다.**
            stats = dexViewModel.profileStats,
            // 시즌 번호를 박지 않는다 — `시즌 2 시작하기`가 시즌 3 뒤에도 나온다.
            newSeasonLabel = "시즌 ${rankingViewModel.season.seasonIndex}",
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
            // 화면 13에서 공유를 마치면 **지도로 간다** (와이어프레임 13 흐름
            // `10/11 → 13 → 14`). 방금 찍은 핀이 보이는 것이 공유의 결과다.
            //
            // ⚠️ **지도를 따로 새로 읽지 않는다.** `MapViewModel`이 저장소 흐름을
            //    구독하므로 핀이 저절로 붙는다 — 여기서 refresh를 부르면 같은 일을
            //    두 번 하고, 안 부르면 안 붙는 것으로 착각하기 쉽다.
            onShared = { tab = NavTab.MAP },
            // `공유하지 않기`는 **도감 상세로** 간다 (주석 ⑤). 도감 홈으로 보내면
            // 방금 등록한 꽃이 200칸 그리드 어딘가에 섞여서 "저장됐다"가 눈에 안 보인다.
            onSkipShare = { flowerId ->
                detailFlowerId = flowerId
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
                    NavTab.MAP -> MapScreen(
                        vm = mapViewModel,
                        onCapture = { tab = NavTab.CAPTURE },
                    )
                    NavTab.RANKING -> when {
                        // 화면 19는 헤더에 `뒤로`가 있는 하위 화면이라 탭 안에서 대체한다.
                        friendsOpen -> FriendsScreen(
                            friendCount = rankingViewModel.friendCount,
                            // 🔴 **더미가 아니라 `friend_ranking` 결과를 넘긴다.**
                            //    friendCount와 **같은 출처**여야 한다 — 갈리면
                            //    `친구 0명` 아래에 8명이 깔린다(FriendsScreen 주석).
                            friends = rankingViewModel.friendRanking,
                            onBack = { friendsOpen = false },
                        )

                        regionPickerOpen -> RegionPickerScreen(
                            // 저장 성공·`나중에 하기` 모두 닫는다. 닫으면서
                            // **랭킹을 다시 읽는다** — 위 주석의 이유.
                            onDone = {
                                regionPickerOpen = false
                                rankingViewModel.refresh()
                            },
                        )

                        else -> RankingScreen(
                            vm = rankingViewModel,
                            onOpenFriends = { friendsOpen = true },
                            onOpenLastSeason = { seasonResultOpen = true },
                            onPickRegion = { regionPickerOpen = true },
                            onCapture = { tab = NavTab.CAPTURE },
                        )
                    }

                    NavTab.MY -> when {
                        friendsOpen -> FriendsScreen(
                            friendCount = rankingViewModel.friendCount,
                            // 🔴 **더미가 아니라 `friend_ranking` 결과를 넘긴다.**
                            //    friendCount와 **같은 출처**여야 한다 — 갈리면
                            //    `친구 0명` 아래에 8명이 깔린다(FriendsScreen 주석).
                            friends = rankingViewModel.friendRanking,
                            onBack = { friendsOpen = false },
                        )

                        // 화면 20 활동 지역 칸의 `동네 선택하기`도 같은 자리를 연다.
                        // 랭킹 탭과 상태를 공유하므로 두 화면이 다른 곳으로 가지 않는다.
                        regionPickerOpen -> RegionPickerScreen(
                            // 저장 성공·`나중에 하기` 모두 닫는다. 닫으면서
                            // **랭킹을 다시 읽는다** — 위 주석의 이유.
                            onDone = {
                                regionPickerOpen = false
                                rankingViewModel.refresh()
                            },
                        )

                        else -> MyScreen(
                            // 랭킹 탭과 **같은 ViewModel**을 읽는다 — 각자 물으면
                            // 화면 17과 화면 20이 다른 동네를 말할 수 있다.
                            profile = rankingViewModel.profile,
                            // 지표 3칸은 **기기 기록**이다(누적치 · 오프라인에서도 있다).
                            stats = dexViewModel.profileStats,
                            friendCount = rankingViewModel.friendCount,
                            onOpenFriends = { friendsOpen = true },
                            onOpenLastSeason = { seasonResultOpen = true },
                            onPickRegion = { regionPickerOpen = true },
                            onRetryProfile = rankingViewModel::refresh,
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
                regionPickerOpen = false
                tab = selected
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (filterOpen) {
        DexFilterSheet(vm = dexViewModel, onDismiss = { filterOpen = false })
    }
}

// ✅ **`Placeholder`를 지웠다**(2026-08-09). 마지막 사용처였던 지도 탭에 화면 14가
//    들어왔다 — **`준비 중`을 띄우는 탭이 이제 없다.**
