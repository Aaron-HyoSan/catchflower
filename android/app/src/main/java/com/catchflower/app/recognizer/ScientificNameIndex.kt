package com.catchflower.app.recognizer

import com.catchflower.app.data.model.Flower

/**
 * 학명 → `Flower.id` 색인. PlantNet 응답을 우리 도감 200종으로 번역한다.
 *
 * **학명이 유일한 연결 고리다.** PlantNet은 한국 이름을 모르고, 4,932종
 * (`k-eastern-asia`) 중에서 고른다. `flowers.json`의 `scientific_name`으로만 맞출 수 있다.
 *
 * **속(genus)까지만 맞는 경우도 받아들인다.** PlantNet이 `Taraxacum officinale`를 주고
 * 우리가 `Taraxacum coreanum`을 들고 있으면 **민들레라는 건 맞다.** 종을 정확히 못 가리는 건
 * B-4 유사종 통합이 풀 문제고, 여기서 버리면 **맞는 답을 판별 실패로 만든다.**
 *
 * ⚠️ 이 파일은 안드로이드 의존이 없는 순수 Kotlin이다. 아래 [flowerID]의 동작은
 *    화면으로 확인할 수 없어서(틀려도 다른 꽃 이름이 예쁘게 나온다) JVM 테스트로 고정한다.
 */
class ScientificNameIndex(flowers: List<Flower>) {

    private val exact: Map<String, Int>

    /**
     * 속 → 그 속의 **모든** 종 id (도감번호 순).
     *
     * ⚠️ **하나만 담지 않는다.** iOS가 사진 200장 실측에서 밟은 지뢰가 여기였다:
     *    속 대표를 도감번호 최솟값으로 고정하니 `Rosa`의 대표가 `찔레꽃`(5~6월)이 되어
     *    8월 장미 사진의 `Rosa chinensis 0.606`(정답)이 개화월 필터에 탈락하고,
     *    그 자리를 `Begonia grandis 0.003`(오답)이 차지했다.
     *    **200종 중 94종이 다종 속에 있고 그중 30속은 개화월이 서로 다르다** —
     *    예외가 아니라 절반의 문제였다. 어느 종을 고를지는 색인이 아니라
     *    **호출 시점의 후보 집합**이 정한다.
     */
    private val byGenus: Map<String, List<Int>>

    init {
        val exact = HashMap<String, Int>()
        val byGenus = HashMap<String, MutableList<Int>>()
        for (flower in flowers) {
            val name = normalize(flower.scientificName)
            if (name.isEmpty()) continue
            exact[name] = flower.id
            val genus = name.substringBefore(' ')
            if (genus.isNotEmpty()) byGenus.getOrPut(genus) { mutableListOf() }.add(flower.id)
        }
        this.exact = exact
        // 원본이 id 순이지만 의존하지 않고 정렬한다 — 동점일 때 결과가 결정론적이어야 한다.
        this.byGenus = byGenus.mapValues { (_, ids) -> ids.sorted() }
    }

    /**
     * @param preferring 이번 달 개화 종 등 **살아남을 수 있는 id 집합**.
     *   속에 여러 종이 있으면 이 집합에 든 종을 먼저 고른다.
     *   `null`이면 도감번호가 가장 작은 종을 준다(비필터 경로용).
     */
    fun flowerId(scientificName: String, preferring: Set<Int>? = null): Int? {
        val name = normalize(scientificName)
        // exact가 후보 밖이면 속으로 내려간다 — `Bellis perennis`(4~5월)를 정확히 맞혔더라도
        // 8월엔 쓸 수 없고, 같은 속의 다른 종이 개화 중일 수 있다.
        val hit = exact[name]
        if (hit != null && (preferring == null || hit in preferring)) return hit

        val genus = name.substringBefore(' ')
        val sameGenus = byGenus[genus] ?: return hit
        return if (preferring != null) {
            sameGenus.firstOrNull { it in preferring } ?: hit
        } else {
            sameGenus.first()
        }
    }

    private companion object {
        /** 대소문자·여분 공백·품종 표기(`var.`·`subsp.`)를 지우고 속+종 두 낱말만 남긴다. */
        fun normalize(name: String): String =
            name.lowercase()
                .replace("var.", " ")
                .replace("subsp.", " ")
                .split(' ', '\t', '\n')
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString(" ")
    }
}
