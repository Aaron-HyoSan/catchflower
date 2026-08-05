package com.catchflower.app.ui.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.KoreanText
import com.catchflower.app.data.model.Flower
import com.catchflower.app.recognizer.IdentifyOutcome
import com.catchflower.app.recognizer.RankedCandidate
import com.catchflower.app.ui.component.CfGhostButton
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.CfTextButton
import com.catchflower.app.ui.component.FlowerIllust
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText
import kotlinx.coroutines.delay

/**
 * 화면 08 AI 분석 중 — `08_AI분석중.svg`. **어두운 화면.**
 *
 * 10초를 넘기면 `조금 더 걸리고 있어요`로 바뀐다. 그 전환을 여기서 타이머로 만든다 —
 * ViewModel에 두면 화면이 떠 있지 않을 때도 타이머가 돈다.
 */
@Composable
fun AnalyzingScreen(
    jpeg: ByteArray,
    overdue: Boolean,
    onOverdue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(jpeg) {
        delay(OVERDUE_MILLIS)
        onOverdue()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.SurfaceDark)
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CapturedPhoto(jpeg, Modifier.fillMaxWidth(0.62f))

        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(color = Color.White)

        Spacer(Modifier.height(24.dp))
        Text("어떤 꽃인지 보고 있어요", style = CfText.Hero, color = Color.White)

        Spacer(Modifier.height(10.dp))
        Text(
            // 10초를 넘기면 "5초 정도 걸려요"가 거짓말이 된다. 그래서 문구를 갈아탄다.
            text = if (overdue) "조금 더 걸리고 있어요" else "5초 정도 걸려요",
            style = CfText.Body,
            color = Color(0xFFCCCCCC),
        )

        Spacer(Modifier.height(40.dp))
        CfTextButton(text = "취소", onClick = onCancel, color = Color.White)
    }
}

/**
 * 화면 09 AI 판별 결과 — `09_판별확인.svg`.
 *
 * **두 변형이 한 화면이다** (B-3 결정):
 * - [IdentifyOutcome.Confident] → 1순위를 크게 + 2·3순위를 작게
 * - [IdentifyOutcome.Ambiguous] → 후보 3개를 같은 크기로
 *
 * ⚠️ 와이어프레임 주석 ④에는 "후보 목록은 제공하지 않는다"고 적혀 있는데
 *    **오너 결정 B-3(항상 후보 3개)이 그 뒤에 확정됐다.** 결정이 와이어프레임을 덮는다
 *    (오너_결정사항 507행). 와이어프레임을 보고 되돌리지 않는다.
 */
@Composable
fun ConfirmScreen(
    jpeg: ByteArray,
    outcome: IdentifyOutcome,
    similarNamesOf: (Flower) -> List<String>,
    onConfirm: (RankedCandidate) -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 주석 ①: 촬영 사진을 상단에 고정한다 — 판정 대상과 결과를 대조할 수 있어야 한다.
        CapturedPhoto(jpeg, Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        when (outcome) {
            is IdentifyOutcome.Confident -> ConfidentBody(
                top = outcome.top,
                alternatives = outcome.alternatives,
                similarNames = similarNamesOf(outcome.top.flower),
                onConfirm = onConfirm,
                onRetake = onRetake,
            )

            is IdentifyOutcome.Ambiguous -> AmbiguousBody(
                candidates = outcome.candidates,
                onConfirm = onConfirm,
                onRetake = onRetake,
            )

            // 실패는 화면 12로 간다. 여기 오면 호출부가 잘못 분기한 것이다.
            IdentifyOutcome.Failed -> Unit
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ConfidentBody(
    top: RankedCandidate,
    alternatives: List<RankedCandidate>,
    similarNames: List<String>,
    onConfirm: (RankedCandidate) -> Unit,
    onRetake: () -> Unit,
) {
    val flower = top.flower

    Column(
        Modifier.fillMaxWidth().padding(horizontal = CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 주석 ②: `이 꽃은 / {꽃이름} / 인가요?` 3줄. 이름만 32pt로 키운다 (노안 고려).
        Text("이 꽃은", style = CfText.Body, color = CfColor.TextSecondary)
        Text(
            text = flower.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text("인가요?", style = CfText.Body, color = CfColor.TextSecondary)

        Spacer(Modifier.height(16.dp))
        FlowerIllust(flower = flower, size = 96.dp)

        Spacer(Modifier.height(12.dp))
        // `장미과 · 5~6월에 피는 꽃`
        Text(
            text = "${flower.family} · ${flower.bloomLabel}에 피는 꽃",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        CfPrimaryButton(text = "네, 맞아요", onClick = { onConfirm(top) })

        Spacer(Modifier.height(10.dp))
        CfSecondaryButton(text = "아니에요, 다시 찍을게요", onClick = onRetake)

        // 주석 ⑤: 오인식 빈발 종을 미리 보여줘 사용자가 스스로 걸러내게 한다.
        // 오등록이 도감에 쌓이는 것을 막는 저비용 장치다.
        if (similarNames.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "비슷한 꽃 · ${similarNames.joinToString(", ")}",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "다르면 다시 찍어 주세요",
                style = CfText.Caption,
                color = CfColor.TextTertiary,
            )
        }

        // 2·3순위를 작게 함께 보여준다 (B-3). 1순위가 틀렸을 때 재촬영 없이 고칠 수 있다
        // — 비용 문서 4절 절감 장치 ③이 바로 이것이다.
        if (alternatives.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "혹시 이 꽃인가요?",
                style = CfText.Section,
                color = CfColor.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            alternatives.forEach { candidate ->
                AlternativeRow(candidate = candidate, onClick = { onConfirm(candidate) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 화면 09 변형 — 1순위가 임계값에 못 미쳤다. 후보 3개를 **같은 크기로** 둔다. */
@Composable
private fun AmbiguousBody(
    candidates: List<RankedCandidate>,
    onConfirm: (RankedCandidate) -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1순위를 크게 두면 "AI가 이걸로 봤다"는 신호가 되어 사용자가 그냥 따라간다.
        // 확신이 없을 때는 크기를 같게 해서 **사용자가 실제로 고르게** 한다.
        Text(
            text = "어느 꽃인가요?",
            style = CfText.Hero,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "가장 비슷한 꽃을 골라 주세요",
            style = CfText.Body,
            color = CfColor.TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        candidates.forEach { candidate ->
            CandidateCard(candidate = candidate, onClick = { onConfirm(candidate) })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(10.dp))
        CfSecondaryButton(text = "아니에요, 다시 찍을게요", onClick = onRetake)
    }
}

@Composable
private fun CandidateCard(candidate: RankedCandidate, onClick: () -> Unit) {
    val flower = candidate.flower
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, CfColor.Border, RoundedCornerShape(14.dp))
            .background(CfColor.Surface)
            .androidClickable(onClick, flower.name)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowerIllust(flower = flower, size = 56.dp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(flower.name, style = CfText.BodyBold, color = CfColor.TextPrimary)
            Text(
                text = "${flower.family} · ${flower.bloomLabel}",
                style = CfText.Caption,
                color = CfColor.TextSecondary,
            )
        }
        // ⚠️ 점수(%)를 보여주지 않는다. "55%"는 중장년 대상에게 판단 근거가 되지 못하고
        //    낮은 숫자를 보면 고르기를 망설인다. 순서만으로 충분하다.
        Text("이 꽃이에요", style = CfText.ButtonSmall, color = CfColor.Primary)
    }
}

@Composable
private fun AlternativeRow(candidate: RankedCandidate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CfColor.Surface)
            .androidClickable(onClick, candidate.flower.name)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowerIllust(flower = candidate.flower, size = 36.dp)
        Spacer(Modifier.size(10.dp))
        Text(
            text = candidate.flower.name,
            style = CfText.Body,
            color = CfColor.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text("이거예요", style = CfText.ButtonSmall, color = CfColor.Primary)
    }
}

/**
 * 화면 12 AI 판별 실패 — `12_판별실패.svg`.
 *
 * [streak]가 [GamePolicy.FAIL_STREAK_FOR_NOT_A_FLOWER] 이상이면 제목을
 * `꽃이 아닐 수도 있어요`로 교체하고 도감 홈 복귀를 유도한다 (A 문서).
 */
@Composable
fun IdentifyFailedScreen(
    streak: Int,
    onRetake: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notAFlower = streak >= GamePolicy.FAIL_STREAK_FOR_NOT_A_FLOWER

    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .verticalScroll(rememberScrollState())
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = if (notAFlower) "꽃이 아닐 수도 있어요" else "어떤 꽃인지 알 수 없었어요",
            style = CfText.Hero,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (notAFlower) {
                "도감에서 어떤 꽃이 있는지 먼저 볼까요?"
            } else {
                "다시 한 번 찍어 주시겠어요?"
            },
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // 사진을 다시 보여주지 않는다 — 실패한 사진은 저장하지 않기 때문이다.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CfColor.Surface)
                .padding(16.dp),
        ) {
            Text("이렇게 찍으면 잘 알아봐요", style = CfText.Section, color = CfColor.TextPrimary)
            Spacer(Modifier.height(10.dp))
            listOf(
                "꽃 한 송이가 화면에 꽉 차게",
                "그림자 없는 밝은 곳에서",
                "정면이나 살짝 위에서",
                "흔들리지 않게 잠시 멈춰서",
            ).forEach { tip ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("· ", style = CfText.Body, color = CfColor.TextSecondary)
                    Text(tip, style = CfText.Body, color = CfColor.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "이 사진은 저장되지 않았어요",
            style = CfText.Caption,
            color = CfColor.TextTertiary,
        )

        Spacer(Modifier.height(24.dp))
        // 3회 연속이면 재촬영을 주버튼으로 두지 않는다 — 같은 실패를 반복하게 만든다.
        if (notAFlower) {
            CfPrimaryButton(text = "도감 보기", onClick = onGiveUp)
            Spacer(Modifier.height(10.dp))
            CfGhostButton(text = "다시 찍기", onClick = onRetake)
        } else {
            CfPrimaryButton(text = "다시 찍기", onClick = onRetake)
            Spacer(Modifier.height(10.dp))
            CfGhostButton(text = "나중에 할게요", onClick = onGiveUp)
        }
    }
}

/**
 * 화면 10 신규 꽃 등록 — `10_신규등록.svg`.
 *
 * 조사 처리는 [KoreanText.subject] — `{꽃이름}가/이`가 A 문서의 명시 요구다.
 */
@Composable
fun NewFlowerScreen(
    flower: Flower,
    dexOrder: Int,
    collectedCount: Int,
    seasonCount: Int,
    onShare: () -> Unit,
    onKeepPrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .verticalScroll(rememberScrollState())
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Text("새로운 꽃을 발견했어요!", style = CfText.Hero, color = CfColor.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${KoreanText.subject(flower.name)} 도감에 등록되었습니다.",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        FlowerIllust(flower = flower, size = 132.dp)

        Spacer(Modifier.height(12.dp))
        Text(
            text = "${dexOrder}번째 꽃",
            style = CfText.ButtonSmall,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(CfColor.Primary)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )

        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CfColor.Surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricColumn("도감", "$collectedCount / ${GamePolicy.TOTAL_FLOWER_COUNT}종")
            MetricColumn("이번 시즌", "${seasonCount}종 (+1)")
        }

        Spacer(Modifier.height(24.dp))
        CfPrimaryButton(text = "지도에 공유하기", onClick = onShare)
        Spacer(Modifier.height(10.dp))
        CfGhostButton(text = "나만 보기", onClick = onKeepPrivate)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 화면 11 기존 꽃 재발견 — `11_재발견.svg`.
 *
 * 서수는 [KoreanText.ordinal] (2~11 한글, 12+ 숫자), 목적격 조사는
 * [KoreanText.objectOf] — `{꽃이름}를 다시 발견했어요!`.
 */
@Composable
fun RediscoveredScreen(
    flower: Flower,
    count: Int,
    onShare: () -> Unit,
    onKeepPrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .verticalScroll(rememberScrollState())
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "${KoreanText.objectOf(flower.name)} 다시 발견했어요!",
            style = CfText.Hero,
            color = CfColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "이번이 ${KoreanText.ordinal(count)} 발견입니다.",
            style = CfText.Body,
            color = CfColor.TextSecondary,
        )

        Spacer(Modifier.height(20.dp))
        FlowerIllust(flower = flower, size = 120.dp)

        Spacer(Modifier.height(24.dp))
        // 안내 박스 — **이게 이 화면의 핵심이다.** 종수가 안 늘어난 걸 오류로 오해하기 쉽다.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CfColor.Surface)
                .padding(16.dp),
        ) {
            Text("이번 시즌 종수는 늘지 않아요", style = CfText.Section, color = CfColor.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "같은 꽃은 한 종으로 계산해요. 사진은 도감에 쌓여요.",
                style = CfText.Body,
                color = CfColor.TextSecondary,
            )
        }

        Spacer(Modifier.height(24.dp))
        CfPrimaryButton(text = "지도에 공유하기", onClick = onShare)
        Spacer(Modifier.height(10.dp))
        CfGhostButton(text = "나만 보기", onClick = onKeepPrivate)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * B-5 하루 중복 안내.
 *
 * 문구는 A 문서의 `B-5 하루 중복 안내` 표를 그대로 쓴다 (iOS 세션이 추가 · **오너 검토 대상**).
 * 제목을 부정형으로 쓰지 않는다 — 막는 게 아니라 **이미 기록됐다**는 뜻이다.
 */
@Composable
fun DailyDuplicateScreen(
    flower: Flower,
    onOpenDex: () -> Unit,
    onCaptureAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(CfColor.Background)
            .padding(CfDimen.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FlowerIllust(flower = flower, size = 110.dp)
        Spacer(Modifier.height(20.dp))
        Text("오늘 여기서 만난 꽃이에요", style = CfText.Hero, color = CfColor.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "같은 자리에서 같은 꽃은 하루에 한 번 기록해요.",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            "장소를 옮기면 다시 기록할 수 있어요.",
            style = CfText.Body,
            color = CfColor.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))
        CfPrimaryButton(text = "도감에서 보기", onClick = onOpenDex)
        Spacer(Modifier.height(10.dp))
        CfSecondaryButton(text = "다른 꽃 찍기", onClick = onCaptureAnother)
    }
}

/** 촬영 사진. 정사각이다 (전송 영역과 같다). */
@Composable
private fun CapturedPhoto(jpeg: ByteArray, modifier: Modifier = Modifier) {
    // 매 리컴포지션마다 디코딩하면 스크롤이 끊긴다. 같은 바이트면 재사용한다.
    val bitmap = remember(jpeg) {
        android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(CfColor.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "방금 찍은 사진",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = CfText.Caption, color = CfColor.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = CfText.BodyBold, color = CfColor.TextPrimary)
    }
}

/**
 * 카드 전체를 누르게 한다. 접근성 라벨을 반드시 붙인다 —
 * 카드 안에 텍스트가 여러 개라 라벨이 없으면 스크린리더가 뭘 누르는지 못 읽는다.
 */
private fun Modifier.androidClickable(onClick: () -> Unit, label: String): Modifier =
    this.clickable(onClickLabel = label, onClick = onClick)

/** 화면 08 — 이 시간을 넘기면 `조금 더 걸리고 있어요`. */
private const val OVERDUE_MILLIS = 10_000L
