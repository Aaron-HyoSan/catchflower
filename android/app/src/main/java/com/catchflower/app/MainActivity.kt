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
import androidx.compose.runtime.LaunchedEffect
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
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.OnboardingState
import com.catchflower.app.ui.onboarding.PermissionIntroScreen
import com.catchflower.app.ui.capture.CaptureFlow
import com.catchflower.app.ui.component.AppLoadingGate
import com.catchflower.app.ui.component.AppLoadingScreen
import com.catchflower.app.ui.component.CfBottomNav
import com.catchflower.app.ui.component.NavTab
import com.catchflower.app.ui.dex.DexDetailScreen
import com.catchflower.app.ui.dex.DexFilterSheet
import com.catchflower.app.ui.dex.DexHomeScreen
import com.catchflower.app.ui.dex.DiscoveryListScreen
import com.catchflower.app.ui.dex.DiscoveryListMode
import com.catchflower.app.ui.dex.DexViewModel
import com.catchflower.app.ui.map.MapScreen
import com.catchflower.app.ui.map.MapViewModel
import com.catchflower.app.ui.my.MyScreen
import com.catchflower.app.ui.my.ProfileEditScreen
import com.catchflower.app.ui.my.ProfileEditViewModel
import com.catchflower.app.ui.place.PlaceScreen
import com.catchflower.app.ui.place.PlaceViewModel
import com.catchflower.app.ui.place.RecordDetailScreen
import com.catchflower.app.ui.place.RecordViewModel
import com.catchflower.app.ui.my.SeasonResultScreen
import com.catchflower.app.ui.my.SettingsScreen
import com.catchflower.app.ui.ranking.FriendsScreen
import com.catchflower.app.ui.ranking.FriendsViewModel
import com.catchflower.app.ui.ranking.RankingScreen
import com.catchflower.app.ui.ranking.RankingViewModel
import com.catchflower.app.ui.region.RegionPickerScreen
import com.catchflower.app.ui.theme.CatchFlowerTheme
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

    // 화면 00(로딩) — **모든 것보다 앞이다.** 온보딩보다도 먼저 뜬다.
    //
    // 여기서 도감 2,057종 파싱을 **IO 스레드로 옮겨 데운다.** 그 전까지는
    // `DexViewModel`의 생성자(= 메인 스레드)에서 돌고 있었다.
    // 🔴 **캐시는 `FlowerRepository`가 스스로 든다**(`@Volatile` + `synchronized`).
    //    그래서 데운 뒤 ViewModel이 `get()`을 다시 불러도 파싱은 한 번뿐이다 —
    //    여기서 인스턴스를 들고 있다가 넘기는 배선을 만들지 않는다(넘기는 길이
    //    하나 더 생기면 어느 쪽이 쓰였는지 화면으로 구분할 수 없다).
    //
    // ⚠️ **예외를 삼키지 않는다.** 자산이 깨지면 앱이 죽는 것이 맞다 —
    //    잡아서 이 화면에 남기면 로고가 영원히 떠 있고, 그건 크래시도 로그도 없는
    //    "멈춘 앱"이다([AppLoadingGate] 주석).
    var loadingDone by remember { mutableStateOf(false) }
    if (!loadingDone) {
        AppLoadingScreen(modifier = Modifier.fillMaxSize())
        LaunchedEffect(Unit) {
            val startedAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) { FlowerRepository.get(context) }
            val elapsed = System.currentTimeMillis() - startedAt
            android.util.Log.i("CatchFlower", "화면 00: 도감 데우기 ${elapsed}ms")
            // 일이 먼저 끝났으면 하한까지 기다린다(깜빡임 방지 · 규칙은 게이트에 있다).
            delay(AppLoadingGate.remainingMs(elapsed))
            loadingDone = true
        }
        return
    }

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

    // 화면 23. 도감 탭 `전체 보기`와 마이 탭 `내가 공유한 꽃`이 **같은 화면을 다른 제목으로**
    // 연다([DiscoveryListMode]). 그래서 열림 여부를 `Boolean`이 아니라 **모드**로 든다 —
    // 플래그 두 개로 두면 둘 다 켜진 상태가 만들어지고, 그때 어느 제목이 나오는지는
    // `when`의 순서에 달린다(= 화면으로만 드러나는 버그).
    var discoveryListMode by remember { mutableStateOf<DiscoveryListMode?>(null) }

    val dexViewModel: DexViewModel = viewModel()
    val rankingViewModel: RankingViewModel = viewModel()
    // ⚠️ **지도 탭 안에서 만들지 않는다.** 탭을 옮길 때마다 새로 생기면 `MapView`가
    //    매번 다시 시작하고, 그때마다 네이티브 렌더러가 붙었다 떨어진다.
    val mapViewModel: MapViewModel = viewModel()

    // 화면 15·16. 지도 탭 **안에서** 겹쳐 여는 하위 화면이라 `NavTab`에 넣지 않는다.
    //
    // ⚠️ **여기서 만든다.** 화면 15 안에서 `viewModel()`을 부르면 그 화면이 사라질 때
    //    ViewModel도 사라지고, 다시 열 때마다 서버를 다시 묻는다 — 그건 지도 핀을
    //    번갈아 누르는 흐름에서 **같은 조회를 몇 번씩** 하게 만든다.
    //    (화면 15가 `open()`으로 좌표를 받아 스스로 다시 읽으므로 캐시 문제는 없다.)
    val placeViewModel: PlaceViewModel = viewModel()
    val recordViewModel: RecordViewModel = viewModel()

    // 화면 19 검색 · 화면 16 `친구 추가`(2026-08-13).
    //
    // ⚠️ **랭킹 탭·마이 탭·화면 16이 같은 인스턴스를 쓴다.** 각자 만들면
    //    방금 요청을 보낸 사람이 다른 화면에서는 다시 `친구 추가`로 보이고,
    //    누르면 서버가 409를 성공으로 삼켜서 **아무 표시 없이 같은 일이 반복된다**
    //    ([FriendsViewModel.justRequested]).
    val friendsViewModel: FriendsViewModel = viewModel()

    // 화면 20-1 `프로필 수정`(2026-08-13).
    //
    // ⚠️ **여기서 만든다.** 화면 안에서 `viewModel()`을 부르면 화면이 사라질 때 같이
    //    사라지고, 그러면 저장 중에 회전한 사용자가 **저장 중이 아닌 화면**으로 돌아온다.
    val profileEditViewModel: ProfileEditViewModel = viewModel()

    // 지도 → 화면 15 → 화면 16. **두 단을 각각 플래그로 둔다** — 한 개로 합치면
    // 화면 16의 `뒤로`가 지도까지 돌아가고, 그러면 사용자가 방금 본 장소를 잃는다.
    var placeOpen by remember { mutableStateOf(false) }
    var recordOpen by remember { mutableStateOf(false) }

    // 랭킹·마이 탭에서 **위로 겹쳐 여는** 화면들. 탭 자체가 아니라서 NavTab에 넣지 않는다.
    // 두 탭이 같은 화면(친구 관리·지난 시즌)을 공유하므로 상태도 여기서 공유한다.
    var friendsOpen by remember { mutableStateOf(false) }
    var seasonResultOpen by remember { mutableStateOf(false) }
    var profileEditOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

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
                        val listMode = discoveryListMode
                        when {
                            // ⚠️ **도감 상세가 화면 23 위에 있다.** 순서를 뒤집으면
                            //    목록에서 줄을 눌러도 목록이 그대로 남아 **아무 일도
                            //    안 일어난 것처럼 보인다.** 이 순서 덕에 상세의 `뒤로`가
                            //    목록으로 돌아온다(목록을 닫고 상세를 열면 뒤로가 도감
                            //    홈으로 튀어서 방금 보던 줄을 잃는다).
                            id != null -> DexDetailScreen(
                                vm = dexViewModel,
                                flowerId = id,
                                onBack = { detailFlowerId = null },
                            )

                            listMode != null -> DiscoveryListScreen(
                                vm = dexViewModel,
                                mode = listMode,
                                onBack = { discoveryListMode = null },
                                onFlowerClick = { detailFlowerId = it },
                                onCapture = { tab = NavTab.CAPTURE },
                            )

                            else -> DexHomeScreen(
                                vm = dexViewModel,
                                onFlowerClick = { detailFlowerId = it },
                                onOpenFilter = { filterOpen = true },
                                onCapture = { tab = NavTab.CAPTURE },
                                onOpenAllDiscoveries = {
                                    discoveryListMode = DiscoveryListMode.ALL
                                },
                            )
                        }
                    }

                    // 위에서 return하므로 여기 오지 않는다. when을 완전하게 두기 위해 명시한다.
                    NavTab.CAPTURE -> Unit
                    NavTab.MAP -> when {
                        // 화면 16이 화면 15 **위에** 있다. 순서를 뒤집으면 화면 15가
                        // 화면 16을 덮어서 기록 상세를 열 수 없다.
                        recordOpen -> RecordDetailScreen(
                            vm = recordViewModel,
                            // 화면 19 검색 결과와 **같은 인스턴스**다(위 주석).
                            friendsVm = friendsViewModel,
                            // 뒤로 = 화면 15로. `placeOpen`은 그대로 둔다.
                            onBack = { recordOpen = false },
                            // `도감에서 보기`는 도감 탭 상세로 건너간다.
                            //
                            // ⚠️ **지도 탭의 하위 화면들을 닫는다.** 안 닫으면 나중에
                            //    지도 탭으로 돌아왔을 때 그때 보던 기록 상세가 그대로
                            //    떠 있다 — 지도를 눌렀는데 남의 댓글창이 나온다.
                            onOpenDex = { flowerId ->
                                recordOpen = false
                                placeOpen = false
                                detailFlowerId = flowerId
                                tab = NavTab.DEX
                            },
                        )

                        placeOpen -> PlaceScreen(
                            vm = placeViewModel,
                            // 🔴 **도감 ViewModel의 값을 그대로 쓴다.** 화면 15가
                            //    따로 도감을 읽으면 `!` 배지 기준이 두 개가 되고,
                            //    도감 탭과 이 화면이 **다른 미보유 목록**을 말한다.
                            collectedIds = dexViewModel.collectedIds,
                            // 🔴 읽는 중에는 배지를 붙이지 않는다 — 전부 `!`가 되고
                            //    그건 200종을 모은 사용자에게 거짓이다.
                            dexLoaded = !dexViewModel.loading,
                            onBack = { placeOpen = false },
                            onOpenRecord = { discovery ->
                                recordViewModel.open(discovery)
                                recordOpen = true
                            },
                        )

                        else -> MapScreen(
                            vm = mapViewModel,
                            onCapture = { tab = NavTab.CAPTURE },
                            onOpenPlace = { lat, lng, placeName ->
                                placeViewModel.open(lat, lng, placeName)
                                placeOpen = true
                            },
                        )
                    }
                    NavTab.RANKING -> when {
                        // 화면 19는 헤더에 `뒤로`가 있는 하위 화면이라 탭 안에서 대체한다.
                        friendsOpen -> FriendsScreen(
                            friendCount = rankingViewModel.friendCount,
                            // 🔴 **더미가 아니라 `friend_ranking` 결과를 넘긴다.**
                            //    friendCount와 **같은 출처**여야 한다 — 갈리면
                            //    `친구 0명` 아래에 8명이 깔린다(FriendsScreen 주석).
                            friends = rankingViewModel.friendRanking,
                            friendsVm = friendsViewModel,
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
                            friendsVm = friendsViewModel,
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

                        // 화면 20-2 설정.
                        //
                        // ⚠️ **`regionPickerOpen`보다 뒤에 온다.** 설정의 `활동 지역`이
                        //    화면 02를 위에 겹쳐 띄우고, 그 화면을 닫으면 **설정으로
                        //    돌아온다** — 순서를 바꾸면 화면 02가 설정 밑에 깔려
                        //    영원히 안 보인다.
                        settingsOpen -> SettingsScreen(
                            // 🔴 문자열을 박지 않는다 — `1.0`을 적어 두면 버전을 올린
                            //    뒤에도 설정 화면만 옛 버전을 말한다.
                            version = BuildConfig.VERSION_NAME,
                            onPickRegion = { regionPickerOpen = true },
                            onBack = { settingsOpen = false },
                        )

                        // 화면 20-1. `프로필 수정`이 여는 자리.
                        //
                        // 🔴 **`onSaved`에서 랭킹을 다시 읽는다.** 안 읽으면 저장에
                        //    성공하고 돌아온 화면 20이 **옛 닉네임**을 그린다 —
                        //    위 `regionPickerOpen`과 같은 이유이고, 사용자에게는
                        //    "저장했다고 해 놓고 안 바뀌었다"로 보인다.
                        profileEditOpen -> ProfileEditScreen(
                            vm = profileEditViewModel,
                            // 랭킹 탭과 같은 출처다 — 여기 다시 물으면 두 화면이
                            // 다른 이름을 말할 수 있다.
                            current = rankingViewModel.profile.nickname,
                            onClose = { profileEditOpen = false },
                            onSaved = rankingViewModel::refresh,
                        )

                        // 화면 23을 `내가 공유한 꽃` 제목으로 연다. 목록은 도감 탭과
                        // **같은 `DexViewModel`**에서 나온다 — 지표 3칸의 `공유 N개`와
                        // 줄 수가 어긋나지 않게(DiscoveryListScreen 주석).
                        discoveryListMode != null -> DiscoveryListScreen(
                            vm = dexViewModel,
                            mode = discoveryListMode!!,
                            onBack = { discoveryListMode = null },
                            // 🔴 **탭을 옮긴다.** 마이 탭 안에서 도감 상세를 그리면
                            //    거기서 `뒤로`를 눌렀을 때 돌아갈 자리가 없다.
                            onFlowerClick = { flowerId ->
                                discoveryListMode = null
                                detailFlowerId = flowerId
                                tab = NavTab.DEX
                            },
                            onCapture = { tab = NavTab.CAPTURE },
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
                            onOpenSharedList = {
                                discoveryListMode = DiscoveryListMode.SHARED
                            },
                            onEditProfile = { profileEditOpen = true },
                            onOpenSettings = { settingsOpen = true },
                            profileEditable = profileEditViewModel.savable,
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
                profileEditOpen = false
                settingsOpen = false
                // 화면 23도 닫는다. 안 닫으면 마이 탭에서 `내가 공유한 꽃`을 열어 둔 채
                // 도감 탭으로 갔을 때 **도감이 아니라 공유 목록**이 나온다.
                discoveryListMode = null
                // 화면 15·16도 같이 닫는다. 안 닫으면 지도 탭을 눌렀을 때 지도가 아니라
                // 아까 보던 **남의 기록 상세**가 나온다(친구 관리에서 겪은 그 모양).
                placeOpen = false
                recordOpen = false
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
