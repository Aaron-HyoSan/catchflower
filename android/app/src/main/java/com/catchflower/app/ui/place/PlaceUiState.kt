package com.catchflower.app.ui.place

import com.catchflower.app.data.PlaceDiscoveries

/**
 * 화면 15 장소 상세의 상태.
 *
 * 🔴 **빈 목록으로 다섯 상태를 대신하지 않는다.** [com.catchflower.app.ui.ranking.RegionRankingUi]가
 *    같은 이유로 갈라져 있고, 여기가 더 미끄럽다 — 화면 15는 **핀을 눌러서 들어오는
 *    화면**이라 "기록이 있는 것을 이미 봤는데 0개라고 말한다"가 가능하다.
 *    그러면 사용자는 **지도가 거짓말을 했다**고 읽는다(실제로는 조회가 실패했다).
 *
 * | 상태 | 화면 | 사용자가 할 일 |
 * |---|---|---|
 * | [Loading] | 지표 칸을 비운다 | 기다린다 |
 * | [Empty] | `아직 이 근처에 공유된 꽃이 없어요` | 꽃을 찍는다 |
 * | [Failed] | `연결이 불안정해요…` + `다시 시도` | 다시 시도한다 |
 * | [NotConfigured] | **아무 문구도 안 띄운다** | 없다(사용자 탓이 아니다) |
 * | [Loaded] | 지표·칩·기록 목록 | 기록을 눌러 화면 16 |
 */
sealed interface PlaceUi {
    /** 아직 서버 응답이 없다. **0종·0개로 깜빡이면 안 된다.** */
    data object Loading : PlaceUi

    /**
     * 이 반경에 공개된 기록이 없다.
     *
     * ⚠️ 지도 핀은 **기기 로컬 기록**으로 그려지므로(`MapPins`) 내 비공개 기록만 있는
     *    자리에서는 이 상태가 **정상**이다 — 화면 15는 "사람들의 기록"이고
     *    비공개는 조회에서 뺀다(`PlaceDiscoveryService.query`).
     */
    data object Empty : PlaceUi

    /** 서버를 못 불렀다. **빈 장소로 그리면 안 된다.** */
    data class Failed(val code: Int) : PlaceUi

    /** 키 없는 빌드. 오류 문구도 `다시 시도`도 띄우지 않는다. */
    data object NotConfigured : PlaceUi

    /**
     * @property data 서버가 준 것. 지표 3칸은 [PlaceDiscoveries.truncated]일 때
     *   **그리지 않는다** — 200행 상한에 걸린 장소에서 `기록 200개`는 틀린 값이다.
     * @property thisWeek `이번 주 3개`. 🔴 **주 시작 계산은 UI 층의 일이다**
     *   (`PlaceDiscoveries.countSince` 주석) — 서버가 아니라 기기 시계로 정한다.
     */
    data class Loaded(
        val data: PlaceDiscoveries,
        val thisWeek: Int,
    ) : PlaceUi
}
