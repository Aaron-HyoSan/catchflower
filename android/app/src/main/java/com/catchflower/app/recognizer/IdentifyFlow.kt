package com.catchflower.app.recognizer

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.model.Flower

/**
 * 인식 결과를 화면 09 / 09변형 / 12 중 하나로 가른다.
 *
 * B-3 권고 "③ 항상 후보 3개 + ② 난이도별 임계값 병용"을 그대로 구현한다.
 * **임계값은 "확정 여부"가 아니라 "후보를 어떻게 보여줄지"에 쓴다** — 이게 원안과 다른 점이다.
 *
 * | 1순위 점수 | 화면 | 동작 |
 * |---|---|---|
 * | 난이도별 임계값 이상 | 09 | 1순위를 크게 + 2·3순위를 작게 |
 * | 임계값 미달 | 09 변형 | 후보 3개를 같은 크기로 |
 * | 1순위도 낮음 | 12 | 판별 실패 + 재촬영 팁 |
 */
sealed interface IdentifyOutcome {

    /** 화면 09 — 1순위를 크게, 2·3순위는 작게 함께 보여준다. */
    data class Confident(
        val top: RankedCandidate,
        val alternatives: List<RankedCandidate>,
    ) : IdentifyOutcome

    /** 화면 09 변형 — "어느 꽃인가요?" 후보 3개를 같은 크기로. */
    data class Ambiguous(val candidates: List<RankedCandidate>) : IdentifyOutcome

    /** 화면 12 — "어떤 꽃인지 알 수 없었어요". */
    data object Failed : IdentifyOutcome
}

/**
 * 이 결과가 **비로그인 판별 횟수를 깎는가** (오너 결정 2026-08-14 · 공유계약 3절).
 *
 * 오너 원문은 `여기 판별은 성공이라고 하면 될듯하다`다. 그 "성공"을
 * **후보가 1개 이상 떴다**로 읽었다 — 사용자가 고를 화면(09·09변형)까지 갔으면
 * 앱이 할 일은 다 한 것이고, 거기서 등록을 안 한 것은 사용자의 선택이다.
 *
 * | 결과 | 센다 | 왜 |
 * |---|---|---|
 * | [IdentifyOutcome.Confident] | ✅ | 화면 09 — 답을 줬다 |
 * | [IdentifyOutcome.Ambiguous] | ✅ | 화면 09변형 — 후보를 줬다. 사용자가 고른다 |
 * | [IdentifyOutcome.Failed] | ❌ | 화면 12 — floor 미달. **우리가 못 맞힌 것이다** |
 *
 * 🔴 **네트워크 실패·일일 한도 초과도 세지 않는다.** 그 경로는 [IdentifyOutcome]을
 *    만들지 않고 토스트([com.catchflower.app.ui.component.CfToast.NETWORK_ERROR])로
 *    끝나므로 이 값을 지나지 않는다 — 즉 **구조로 지켜진다.** 만약 나중에 실패를
 *    `Failed`로 뭉개서 이 함수에 넣으면 그때도 ❌이므로 안전한 방향이다.
 *
 * ⚠️ **`when`에 `else`가 없다.** 새 결과 타입이 생기면 **컴파일이 깨진다** — 그게
 *    의도다. 조용히 "성공"으로 떨어지면 무료 판별이 무한이 되고, 조용히 "실패"로
 *    떨어지면 성공한 사람의 횟수가 안 줄어든다. **둘 다 화면에 증상이 없다.**
 */
val IdentifyOutcome.countsAsSuccess: Boolean
    get() = when (this) {
        is IdentifyOutcome.Confident -> true
        is IdentifyOutcome.Ambiguous -> true
        IdentifyOutcome.Failed -> false
    }

/**
 * 순위가 붙은 후보. [rank]는 1부터다.
 *
 * ⚠️ [rank]는 화면에 안 보이지만 **DB에 저장한다** (discoveries.ai_picked_rank).
 *    B-3 어뷰징 가드 ②의 입력이다.
 */
data class RankedCandidate(
    val rank: Int,
    val flower: Flower,
    val score: Float,
) {
    /**
     * 이 후보를 고르면 사진을 한 장 더 받아야 하는가 (B-3 어뷰징 가드 ①).
     *
     * 후보 3개를 보여주면 **3순위에 '귀함' 종이 있을 때 그걸 고르는 사람이 생긴다.**
     * 희귀종을 낮은 순위·낮은 점수에서 고르면 즉시 확정하지 않는다.
     */
    val needsExtraPhoto: Boolean
        get() = flower.rarity == Rarity.RARE &&
            (rank >= GamePolicy.RARE_PICK_NEEDS_EXTRA_PHOTO_FROM_RANK ||
                score < GamePolicy.RARE_PICK_LOW_SCORE)
}

class IdentifyFlow(private val repository: FlowerRepository) {

    /**
     * 개화월 하드 필터 (A-1 필수 구현).
     *
     * **PlantNet은 79,047종에서 고르므로 11월에 "벚꽃"이 올 수 있다.**
     * 이건 선택이 아니라 필수다. 실제로는 서버가 적용하지만, 서버가 붙기 전과
     * 서버 응답을 신뢰할 수 없는 경우를 위해 클라이언트에도 같은 판정을 둔다.
     *
     * ⚠️ 판정 근거는 `Flower.bloomMonths`이고, 그건 적재 스크립트가 한 번만 파싱한 값이다.
     *    **여기서 개화기 문자열을 다시 파싱하지 않는다** (공유계약 1-2).
     */
    fun candidatesForMonth(month: Int): List<Int> =
        repository.bloomingIn(month).map { it.id }

    /**
     * 인식 결과를 화면 분기로 바꾼다.
     *
     * @param candidates 인식기가 돌려준 점수 내림차순 후보.
     */
    fun decide(candidates: List<Candidate>): IdentifyOutcome {
        val ranked = candidates
            .sortedByDescending { it.score }
            // ① 종 → **수집 그룹 대표종**으로 접는다 (B-4 · 계약 1-6).
            //    화면에 뜨는 이름·일러스트와 등록되는 도감 칸이 여기서 정해진다.
            .mapNotNull { candidate ->
                repository.representativeOf(candidate.flowerId)?.let { it to candidate.score }
            }
            // ② **같은 칸을 두 번 보여주지 않는다.** 접기만 하고 중복을 안 지우면
            //    화면 09 변형의 후보 3개가 `민들레 / 민들레 / 별꽃`이 된다 —
            //    같은 이름 두 개 중 하나를 고르라는 화면이고, 예외는 안 난다.
            .distinctBy { (flower, _) -> flower.id }
            // ③ 자르기는 **접은 뒤에** 한다.
            //    🔴 **다만 여기서 자르는 것으로는 후보가 늘지 않는다.** 이 목록은
            //    [PlantNetRecognizer.parse]가 이미 3개로 잘라서 준다 — 그래서 접기를
            //    여기서만 하면 `민들레 / 서양민들레 / 별꽃`이 **2개**로 줄어든다.
            //    후보를 채우는 일은 **끊는 단위를 그룹으로 바꾼** 인식기 쪽이 한다
            //    ([PlantNetRecognizer.groupOf]). 여기 `take`는 인식기를 갈아 끼웠을 때
            //    (Mock·서버 판별) 3개를 넘겨도 화면이 깨지지 않게 하는 방어선이다.
            .take(GamePolicy.CANDIDATE_COUNT)
            .mapIndexed { index, (flower, score) ->
                RankedCandidate(rank = index + 1, flower = flower, score = score)
            }

        val top = ranked.firstOrNull() ?: return IdentifyOutcome.Failed

        // 1순위조차 낮으면 후보를 보여주는 것 자체가 의미 없다 → 화면 12
        if (top.score < GamePolicy.MIN_CONFIDENCE_FOR_ANY_CANDIDATE) return IdentifyOutcome.Failed

        val threshold = GamePolicy.confidenceThreshold.getValue(top.flower.aiDifficulty)
        return if (top.score >= threshold) {
            IdentifyOutcome.Confident(top = top, alternatives = ranked.drop(1))
        } else {
            IdentifyOutcome.Ambiguous(ranked)
        }
    }
}
