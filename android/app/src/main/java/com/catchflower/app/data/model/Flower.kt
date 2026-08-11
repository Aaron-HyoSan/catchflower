package com.catchflower.app.data.model

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season

/**
 * 도감 마스터 1종. 공유계약 1-1이 필드명의 원본이다.
 *
 * ⚠️ [bloomMonths]는 **클라이언트가 파싱하지 않는다.** `공용_적재/flower_master.py`가
 *    CSV의 `"3~4월"`을 한 번만 파싱해 `int[]`로 내려준다 (공유계약 1-2).
 *    양쪽이 각자 파싱하면 iOS와 Android가 다른 후보 집합을 쓰게 되고, 그건 판별이 갈리는 것이다.
 *
 * 🔴 **2,057종부터 "값이 없는 종"이 생겼다.** 신규 1,857종은 대표색·서식지가 없고
 *    1,017종은 개화기 표기가 없다. 이 필드들은 화면 문구에 **그대로 보간되는 자리**라
 *    빈칸이면 예외가 아니라 **깨진 문장**이 나온다(`국화과 · 에 피는 꽃`).
 *    그래서 문구 조립을 화면에 두지 않고 [bloomText]·[storyText]·[attributeChips]에
 *    모아 뒀다 — 화면마다 각자 분기하면 **한 군데를 빠뜨려도 아무 검사도 빨개지지 않는다.**
 */
data class Flower(
    /** 도감번호 1~2057. 고정 ID — 출시 후 변경하지 않는다. 발견 기록이 이 번호에 매달린다. */
    val id: Int,
    val name: String,
    val scientificName: String,
    /**
     * PlantNet이 이 종에 대해 **줄 수 있는 다른 학명** (공유계약 1-1-e). 16종만 비어 있지 않다.
     *
     * 🔴 **왜 필요한가.** 우리 도감의 id 1~200은 사람이 정한 `꽃목록_200종.csv`가
     *    이기는데(계약 1-1-a), 그 파일의 학명 일부가 지금 학계가 쓰는 이름보다 옛
     *    것이다. PlantNet은 새 이름을 준다 — `Erigeron bonariensis`를 주는데 우리는
     *    `Conyza bonariensis`를 들고 있어서 **속조차 안 맞고**, 속 fallback이
     *    엉뚱한 종(개망초·민망초)으로 보낸다. 실측 200장 중 **20장**이 이 경로였다.
     *
     * ⚠️ **`scientificName`을 고치는 것이 아니다.** 실측으로 3건은 PlantNet이 **옛
     *    이름을** 주고 있어서(원추천인국은 새 이름이 200장에 한 번도 안 나온다)
     *    덮으면 지금 맞던 것이 사라진다. 그래서 **양쪽 다 받는다.**
     *
     * ⚠️ 이 필드가 틀려도 **화면에는 다른 꽃 이름이 예쁘게 나온다** — 증상이 없다.
     *    그래서 검증은 화면이 아니라 `ScientificNameIndexTest`가 한다.
     */
    val scientificAliases: List<String>,
    val family: String,
    /** 개화월. 개화월 하드 필터(A-1 필수 구현)의 입력이다. */
    val bloomMonths: List<Int>,
    /**
     * 화면 09 부연 "5~6월에 피는 꽃"에 쓰는 원문 표기.
     *
     * 🔴 **1,017종은 빈 문자열이다**([bloomSource]가 `PEAK_WINDOW`·`UNKNOWN`).
     *    직접 보간하지 말고 [bloomText]를 쓴다.
     */
    val bloomLabel: String,
    /** [bloomLabel]을 낼 수 있는지의 **유일한 판정 근거** (공유계약 1-2-b·1-2-c). */
    val bloomSource: BloomSource,
    /** 🔴 **278종은 null이다** — 근거가 없다는 뜻이고, 화면 06 필터에 걸리지 않는다(계약 1-1-d). */
    val season: Season?,
    /** 신규 1,857종은 빈 문자열이다 — 이름 형태소로 추정하면 58.8%만 맞아서 만들지 않았다. */
    val color: String,
    val rarity: Rarity,
    /** 신규 1,857종은 빈 문자열이다 — 원천 데이터가 아예 없다. */
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
     * 개화기 표기. 모르면 `null` — **화면은 그 절을 뺀다** (공유계약 1-2-c).
     *
     * 🔴 판정을 [bloomSource]로 한다. `bloomLabel.isNotEmpty()`로 해도 지금은 같은
     *    답이 나오지만, 그러면 **적재가 실수로 빈칸이 아닌 무언가를 내리는 순간
     *    조용히 갈린다.** 적재 쪽(`flower_master._verify`)이 둘의 일치를 양방향으로
     *    검사하는 이유가 이것이다.
     */
    val bloomText: String?
        get() = bloomLabel.takeIf { bloomSource.hasLabel && it.isNotEmpty() }

    /**
     * 화면 09 부연 · 05 학명줄에 쓰는 `과 · 개화기` 한 줄.
     *
     * | | 개화기 있음 | 개화기 모름 |
     * |---|---|---|
     * | [detailSuffix] `= "에 피는 꽃"` | `장미과 · 5~6월에 피는 꽃` | `장미과` |
     * | [detailSuffix] `= ""` | `장미과 · 5~6월` | `장미과` |
     *
     * ⚠️ **새 문구가 아니다** — 승인된 문장에서 `·` 이하 절을 뺀 것이다.
     *    `한 해 내내 피어요` 같은 말로 채우지 않는다: ⑤ 전월 허용은
     *    "일 년 내내 핀다"가 아니라 **"모른다"** 는 뜻이다.
     */
    fun familyAndBloom(detailSuffix: String = ""): String {
        val bloom = bloomText ?: return family
        return "$family · $bloom$detailSuffix"
    }

    /**
     * 화면 05 '꽃 이야기'. 가진 절만 이어 붙인다.
     *
     * 절이 **둘 다 없는 종이 있다**(신규종 중 개화기까지 모르는 경우) → `null`.
     * 그때 화면은 섹션 자체를 그리지 않는다 — 제목만 남은 빈 섹션은 정보가 아니다.
     */
    val storyText: String?
        get() {
            val parts = listOfNotNull(
                bloomText?.let { "${it}에 피어요." },
                habitat.takeIf { it.isNotEmpty() }?.let { "${it}에서 흔히 만납니다." },
            )
            return parts.joinToString(" ").takeIf { it.isNotEmpty() }
        }

    /**
     * 화면 05 속성 칩. **값이 있는 것만 낸다.**
     *
     * 원래 `계절 / 희귀도 / 대표색` 3개 고정이었는데, 신규종은 대표색이 없고
     * 278종은 계절도 없다. 고정 3개로 두면 **테두리만 있는 빈 칩**이 나온다
     * (계약 1-1-c 표에 적힌 그 증상이다). 희귀도는 전 종에 있으므로 최소 1개는 남는다.
     */
    val attributeChips: List<String>
        get() = listOfNotNull(
            season?.label,
            rarity.label,
            color.takeIf { it.isNotEmpty() },
        )

    /**
     * 일러스트 assets 경로. **번호만 쓴다.**
     *
     * ⚠️ 원래 C 발주서 2절의 `flower_081_장미.svg`를 그대로 조립했는데,
     *    **실제 납품은 PNG이고 이름으로 찾으면 안 된다.** macOS 파일명의 한글은
     *    **NFD(자모 분리)** 로 저장되고 `flowers.json`의 `name`은 NFC라서,
     *    이름을 붙여 만든 문자열은 **200종 전부 파일과 불일치**한다
     *    (`개나리`.length가 3 vs 6 — 눈으로는 같은 글자다).
     *    빌드가 복사할 때 이름을 버리고 번호만 남기는 이유가 이것이다.
     *
     * ⚠️ **4자리다.** 도감이 2,057종이 되면서 `%03d`는 1000번 이상을 `1000`으로
     *    내보내 자릿수가 섞인다(`999.png`와 `1000.png`). 그래서 자산 이름을 4자리로
     *    통일했고, **이 값과 `SyncSharedAssets`의 정규식·복사 이름은 같이 움직여야
     *    한다.** 한쪽만 고치면 그림만 안 나오고 예외는 없다 — 그 칸이 빌 뿐이다.
     */
    val illustAssetName: String
        get() = "flower_illust/%04d.png".format(id)
}
