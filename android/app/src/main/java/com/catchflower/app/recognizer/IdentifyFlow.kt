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
            .take(GamePolicy.CANDIDATE_COUNT)
            .mapIndexedNotNull { index, candidate ->
                val flower = repository.byId(candidate.flowerId) ?: return@mapIndexedNotNull null
                RankedCandidate(rank = index + 1, flower = flower, score = candidate.score)
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
