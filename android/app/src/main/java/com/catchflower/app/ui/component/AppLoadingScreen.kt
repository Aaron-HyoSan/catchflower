package com.catchflower.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catchflower.app.R
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * **화면 00 — 로딩(브랜드) 화면.** 2026-08-16 오너 지시로 추가했다.
 *
 * 구성은 오너가 말로 정했다: **로고 · `CatchFlower` · 하단 푸터 `SUJI & SAN corp`**.
 * 문구 원본은 `디자이너_업무/A_문구·버튼_스펙.md` 3절 `화면 00 로딩 화면` 표다.
 *
 * ## ⚠️ 와이어프레임에 없는 화면이다
 *
 * 22개 화면 세트(01~23)에 로딩 화면은 없다 — 오너가 나중에 추가한 것이라
 * `와이어프레임/`에 대응 SVG가 없다. **없다는 것을 여기 적어 둔다**: 다음 사람이
 * 와이어프레임과 코드를 대조하면 이 화면만 짝이 없고, 그때 "누가 지어냈나"를
 * 알 방법이 있어야 한다.
 *
 * ## 🔴 시스템 스플래시와 **두 장**이다 — 같은 그림이어야 한다
 *
 * 앱을 누르면 ① OS 스플래시가 먼저 뜨고 ② 이 화면이 이어 받는다.
 * 그래서 **배경 흰색 + 같은 배지**(`ic_splash_badge`)를 쓴다 — 다른 그림을 쓰면
 * 실행할 때마다 **한 번 깜빡이며 화면이 바뀌는** 것으로 보인다.
 *
 * ⚠️ **OS 스플래시가 이미 두 경로다.** API 31+는 시스템이 `ic_launcher_splash`(꽃만)를
 *    `ic_launcher_background` 원 안에 그리고(`values-v31/themes.xml`), 31 미만은
 *    `ic_splash_badge`(원+꽃이 한 장)를 그대로 그린다(`drawable/splash_background.xml`).
 *    **결과 그림은 같지만 리소스가 다르다** — 여기서 쓰는 것은 후자다.
 *    🔴 즉 배지를 고치면 **세 곳**을 같이 본다. 그리고 내 에뮬레이터는 API 36이라
 *    **31 미만 경로는 여기서 확인되지 않는다**(`values-v31/themes.xml` 주석).
 *
 * ### 🔴 그 "같은 그림"이 실제로는 **틀려 있었다** (2026-08-16 실측)
 *
 * 처음 쓸 때 배지를 `108.dp`로 그렸다. 에뮬레이터 실행 프레임을 픽셀로 재 보니
 * (420dpi · 1dp = 2.625px):
 *
 * | 무엇 | 지름 | 세로 중심 |
 * |---|---|---|
 * | ① 시스템 스플래시가 그린 원 | 420px = **160dp** | 1199px |
 * | ② 이 화면이 그린 배지 (고치기 전) | 284px = **108dp** | 1062px |
 * | ② 이 화면이 그린 배지 (고친 뒤 실측) | 419px = **160dp** | 1199~1200px |
 *
 * (화면 정중앙은 1200px · 1080×2400 · 420dpi. 고친 뒤 프레임 8장을 다시 재서
 * ①과 ②가 지름·중심 둘 다 같은 것을 확인했다.)
 *
 * 즉 고치기 전에는 넘어가는 순간 로고가 **1.48배 줄어들며** 위로 138px(≈53dp) 튀었다 —
 * 바로 위 문단이 "피한다"고 적어 둔 그 증상이다. 🔴 **주석은 의도를 적어 두는
 * 곳이지 증거가 아니다.** 화면은 두 장 다 "로고가 흰 배경에 있는" 그림이라
 * 눈으로 훑으면 정상으로 보이고, 빌드·테스트도 전부 통과했다.
 *
 * 원인은 배지 PNG가 108dp 캔버스로 생성돼 있던 것이다. 시스템은
 * `windowSplashScreenIconBackgroundColor`를 주면 원을 **지름 160dp**로 그린다
 * (생성 스크립트의 `SPLASH_SAFE` 주석에 적힌 옛 실측 `원 지름 417px`도 같은 값이다).
 * 그래서 **배지를 160dp로 다시 생성했다**(`BADGE_DP` · 1024px 시안이라 확대가 아니다)
 * 그리고 여기서도 [BadgeSize]로 그린다.
 *
 * ⚠️ **여기서 `Modifier.size`만 키우면 안 된다** — PNG가 108dp면 늘어나서 흐려진다.
 *    크기는 **생성 스크립트가 원본**이고, 세 값(스크립트 상수 · [BadgeSize] · 실제
 *    PNG 픽셀)이 같은지는 [AppLoadingGateTest]가 센다.
 *
 * ### 세로 위치도 맞춘다
 *
 * 시스템은 로고를 **화면 정중앙**에 그린다. 이 화면은 처음에 로고·이름·스피너를
 * 한 열로 묶어 가운데 정렬했는데, 그러면 **열의 중심**이 정중앙이라 로고가 그만큼
 * 위로 올라간다(위 실측의 138px). 그래서 위/아래 반쪽으로 나눠 **배지 중심이
 * 화면 중심**에 오게 하고, 이름·스피너를 아래 반쪽에 그린다.
 *
 * ## ⚠️ 이 화면은 **진짜 일을 기다린다**
 *
 * 도감 2,057종 JSON 파싱([com.catchflower.app.data.FlowerRepository])은 지금까지
 * `DexViewModel` 생성 시점, 즉 **메인 스레드**에서 돌았다. 이 화면이 뜨는 동안
 * IO 스레드에서 그것을 데워 두므로 대기 시간이 여기로 모인다 —
 * 🔴 **그러니 이 화면을 지우면 첫 화면이 그만큼 늦게 그려진다**(없어지는 게 아니다).
 * 판정은 [AppLoadingGate]가 하고 JVM 테스트가 그 규칙을 고정한다.
 */
@Composable
fun AppLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CfColor.Background),
    ) {
        // 화면을 위/아래 **같은 크기 반쪽**으로 나눈다. 두 반쪽의 경계가 곧 화면
        // 정중앙이고, 시스템 스플래시가 로고를 그리는 자리다.
        // ⚠️ 아래 두 줄(이름·스피너)의 높이를 여기서 알 필요가 없다 — 높이를 재서
        //    위쪽에 같은 만큼 여백을 주는 방식으로 짜면 **글꼴이 바뀌면 조용히 어긋난다.**
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_splash_badge),
                    // 장식이 아니라 **앱 자체를 가리키는 그림**이라 설명을 준다.
                    // ⚠️ `contentDescription = null`로 두면 스크린리더에게 이 화면은
                    //    "진행 중" 표시 하나뿐인 빈 화면이 된다.
                    contentDescription = "캐치플라워",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(BadgeSize)
                        // 아래쪽 정렬로 배지 **아래끝**이 경계에 붙으므로 반지름만큼
                        // 내려서 **중심**을 경계에 맞춘다(= 화면 정중앙).
                        .offset(y = BadgeSize / 2),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    // 배지의 아래 절반이 이 반쪽을 덮고 있으므로 그만큼 비운다.
                    modifier = Modifier.padding(top = BadgeSize / 2 + CfDimen.GapLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CfDimen.GapLarge),
                ) {
                    Text(
                        // 🔴 한글 `캐치플라워`가 아니라 **`CatchFlower`** 다(오너 지시).
                        //    런처 이름은 `@string/app_name`(캐치플라워)이라 **일부러 다르다** —
                        //    바꾸려면 두 곳을 같이 본다.
                        text = "CatchFlower",
                        style = CfText.Hero,
                        color = CfColor.Primary,
                    )
                    CircularProgressIndicator(
                        color = CfColor.Primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Text(
            text = "SUJI & SAN corp",
            style = CfText.Caption,
            color = CfColor.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // ⚠️ 하단 내비가 없는 화면이라 제스처 바를 직접 피한다.
                //    안 주면 푸터가 시스템 바에 깔린다(화면 21에서 같은 것을 겪었다).
                .navigationBarsPadding()
                .padding(bottom = CfDimen.GapLarge),
        )
    }
}

/**
 * 배지 지름. **원본은 생성 스크립트의 `BADGE_DP`이고 여기는 사본이다.**
 *
 * 🔴 이 숫자를 혼자 바꾸면 안 된다 — PNG는 `BADGE_DP` 캔버스로 만들어져 있어서
 *    여기만 키우면 **늘어나 흐려지고**, 여기만 줄이면 시스템 스플래시에서
 *    넘어올 때 다시 **깜빡인다**. 둘이 같은지는 [AppLoadingGateTest]가 센다.
 */
private val BadgeSize = 160.dp

/**
 * 로딩 화면을 **언제 내리나**.
 *
 * 규칙은 두 줄이다:
 * ① 데우기가 끝나지 않았으면 계속 보여준다(그래서 호출부가 데우기를 `await`한다).
 * ② 끝났어도 [MIN_VISIBLE_MS]까지는 보여준다([remainingMs]).
 *
 * ⚠️ **①을 위한 함수를 따로 두지 않았다.** `stillLoading(elapsed, done)` 같은 것을
 *    만들었다가 지웠다 — 호출부가 데우기를 `await`한 뒤에 [remainingMs]만큼 더
 *    기다리므로 **부르는 곳이 0곳**이었다. 안 쓰는 함수는 "판정이 검사로 고정돼 있다"는
 *    착각을 만든다(`CfToast` 클래스 주석과 같은 이유).
 *
 * ## 🔴 ②는 왜 있나 — 그리고 이게 **가짜 로딩이 아닌 이유**
 *
 * 데우기가 120ms에 끝나는 기기에서는 로고가 **한 프레임 깜빡이고 사라진다** —
 * 그건 브랜드 화면이 아니라 화면 결함으로 보인다. 그래서 하한을 준다.
 * ⚠️ **하한은 지연이다.** 일이 먼저 끝났으면 그만큼 앱이 늦게 열린다. 값을 올릴 때는
 *    "브랜드 노출"과 "시작이 느려짐"을 맞바꾸는 것임을 알고 올린다.
 *
 * ## ⚠️ 실패는 여기서 다루지 않는다
 *
 * 데우기가 예외를 던지면 **앱이 죽는 것이 맞다.** 삼켜서 이 화면에 남기면
 * 로고가 영원히 떠 있는 화면이 되는데, 그건 사용자에게 **멈춘 앱**이고
 * 로그도 크래시도 없어서 원인을 알 방법이 없다.
 * (도감 자산이 깨지면 지금도 `DexViewModel`에서 죽는다 — 시점만 앞으로 온다.)
 */
object AppLoadingGate {

    /** 일이 먼저 끝나도 이만큼은 보여준다. */
    const val MIN_VISIBLE_MS = 800L

    /**
     * 데우기가 끝난 뒤 **더 기다릴 시간**. 이미 하한을 넘었으면 0이다.
     *
     * ⚠️ 음수를 돌려주지 않는다 — `delay(-n)`은 예외는 아니지만 0과 같아서
     *    실수를 조용히 삼킨다. 여기서 잘라 두면 호출부가 그냥 넘길 수 있다.
     */
    fun remainingMs(elapsedMs: Long): Long = (MIN_VISIBLE_MS - elapsedMs).coerceAtLeast(0L)
}
