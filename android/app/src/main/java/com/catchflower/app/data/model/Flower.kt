package com.catchflower.app.data.model

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season

/**
 * 도감 마스터 1종. 공유계약 1-1이 필드명의 원본이다.
 *
 * ⚠️ [bloomMonths]는 **클라이언트가 파싱하지 않는다.** `꽃도감/_tools/build_app_data.py`가
 *    CSV의 `"3~4월"`을 한 번만 파싱해 `int[]`로 내려준다 (공유계약 1-2).
 *    양쪽이 각자 파싱하면 iOS와 Android가 다른 후보 집합을 쓰게 되고, 그건 판별이 갈리는 것이다.
 */
data class Flower(
    /** 도감번호 1~200. 고정 ID — 출시 후 변경하지 않는다. 발견 기록이 이 번호에 매달린다. */
    val id: Int,
    val name: String,
    val scientificName: String,
    val family: String,
    /** 개화월. 개화월 하드 필터(A-1 필수 구현)의 입력이다. */
    val bloomMonths: List<Int>,
    /** 화면 09 부연 "5~6월에 피는 꽃"에 쓰는 원문 표기. */
    val bloomLabel: String,
    val season: Season,
    val color: String,
    val rarity: Rarity,
    val habitat: String,
    val aiDifficulty: AiDifficulty,
    /** 도감 안에 있는 유사종 id. 화면 05·09의 '비슷한 꽃' 링크. */
    val similarFlowerIds: List<Int>,
    /** 도감에 없는 종까지 포함한 표시용 이름. 화면 09 힌트 문구가 이걸 쓴다. */
    val similarFlowerNames: List<String>,
    val illustBatch: Int,
) {
    /** 이 달에 피는가. 개화월 하드 필터의 판정. */
    fun bloomsIn(month: Int): Boolean = month in bloomMonths

    /**
     * 일러스트 assets 경로. **번호만 쓴다.**
     *
     * ⚠️ 원래 C 발주서 2절의 `flower_081_장미.svg`를 그대로 조립했는데,
     *    **실제 납품은 PNG이고 이름으로 찾으면 안 된다.** macOS 파일명의 한글은
     *    **NFD(자모 분리)** 로 저장되고 `flowers.json`의 `name`은 NFC라서,
     *    이름을 붙여 만든 문자열은 **200종 전부 파일과 불일치**한다
     *    (`개나리`.length가 3 vs 6 — 눈으로는 같은 글자다).
     *    빌드가 복사할 때 이름을 버리고 번호만 남기는 이유가 이것이다.
     */
    val illustAssetName: String
        get() = "flower_illust/%03d.png".format(id)
}
