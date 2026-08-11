package com.catchflower.app.recognizer

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 학명 별칭(계약 1-1-e)이 **실제로 도는지** 고정한다.
 *
 * 🔴 **이 파일이 없으면 별칭은 전부 빠져도 모든 검사가 초록이다.** 세 층이 겹쳐서
 *    안 보인다:
 *
 *    | 층 | 왜 못 보는가 |
 *    |---|---|
 *    | 화면 | 틀려도 **다른 꽃 이름이 예쁘게 나온다** — 오류도 빈 화면도 아니다 |
 *    | Top-1·Top-3 | 정답 판정이 **속(genus) 단위**다(`class_to_genera`: daisy 17종) — 별칭은 "같은 속 안의 어느 종인가"를 고치므로 **원리상 안 나타난다** |
 *    | 화면 12 분해 | 후보가 생기는지만 세므로 **어느 종인지는 안 본다** |
 *
 *    그래서 여기서는 지표가 아니라 **"이 학명이 이 꽃으로 번역되는가"** 를 직접 잰다.
 *
 * ⚠️ **픽스처의 도감을 쓴다 — 손으로 만든 꽃으로 재지 않는다.** 별칭의 값은
 *    "우리가 실제로 싣는 2,057종에서 어떻게 번역되는가"이고, 그건 도감의 다른
 *    종과의 관계로 정해진다. 지어낸 3종으로는 `Lythrum salicaria`가 털부처꽃을
 *    빼앗는 종류의 문제를 **구조적으로 만들 수 없다.**
 */
class ScientificAliasTest {

    private val fixture: JSONObject by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("plantnet_replay.json")!!
            .bufferedReader().use { it.readText() }
        JSONObject(text)
    }

    private fun dex(key: String): List<Flower> {
        val arr = fixture.getJSONArray(key)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val months = o.getJSONArray("bloom_months")
            val aliases = o.getJSONArray("scientific_aliases")
            Flower(
                id = o.getInt("id"),
                name = o.getString("name"),
                scientificName = o.getString("scientific_name"),
                scientificAliases = (0 until aliases.length()).map { aliases.getString(it) },
                family = "",
                bloomMonths = (0 until months.length()).map { months.getInt(it) },
                bloomLabel = "",
                bloomSource = BloomSource.fromWire(o.getString("bloom_source")),
                season = Season.SPRING,
                color = "",
                rarity = Rarity.COMMON,
                habitat = "",
                aiDifficulty = AiDifficulty.LOW,
                similarFlowerIds = emptyList(),
                similarFlowerNames = emptyList(),
                illustBatch = 1,
            )
        }
    }

    private val full = dex("flowers_full")
    private val byId = full.associateBy { it.id }
    private val index = ScientificNameIndex(full)

    /** 이 달에 피는 종 — 개화월 하드 필터가 넘기는 후보 집합. */
    private fun blooming(month: Int): Set<Int> =
        full.filter { month in it.bloomMonths }.map { it.id }.toSet()

    // ── 별칭이 실려 있는가 ──────────────────────────────────────────────

    /**
     * 🔴 **가장 먼저 이걸 잰다.** 아래 번역 테스트들은 별칭이 아예 없으면
     *    속 fallback이 그럴듯한 답을 주기 때문에 **일부가 우연히 통과한다.**
     *    개수를 먼저 고정해야 "별칭이 실렸다"와 "속으로 얼렁뚱땅 맞았다"가 갈린다.
     */
    @Test
    fun `자산에 별칭 16건이 실려 있다`() {
        val total = full.sumOf { it.scientificAliases.size }
        assertEquals(
            "별칭이 안 실렸다 — `공용_적재/scientific_aliases.py` 고친 뒤 " +
                "`python3 꽃도감/_tools/build_app_data.py`와 픽스처 재생성을 돌린다",
            GamePolicy.SCIENTIFIC_ALIAS_COUNT,
            total,
        )
    }

    /**
     * ⚠️ **16건 전부 id ≤ 200이어야 한다.** 우연이 아니라 계약 1-1-a의 결과다 —
     *    사람이 정한 200종의 학명을 지키기 때문에 그쪽만 옛 이름으로 남는다.
     *    확장분(id>200)에 별칭이 붙었다면 그건 **다른 이유**이고, 근거를 다시 봐야 한다.
     */
    @Test
    fun `별칭은 사람이 정한 200종에만 붙는다`() {
        val outside = full.filter { it.id > 200 && it.scientificAliases.isNotEmpty() }
        assertTrue(
            "확장분에 별칭이 붙었다: ${outside.map { "${it.id} ${it.name}" }} — " +
                "확장 CSV는 이미 새 이름이라 별칭이 필요 없다(계약 1-1-e)",
            outside.isEmpty(),
        )
    }

    // ── 실측으로 확인된 한 건 ───────────────────────────────────────────

    /**
     * 🔴 **이 한 건만 실측 응답으로 확인됐다** — `Erigeron bonariensis`는 iOS 실측
     *    캐시 200장 중 **20장**에 등장한다. 우리 도감은 `Conyza bonariensis`(실망초,
     *    id 103)를 들고 있어서 **속조차 안 맞고**, 속 fallback이 `Erigeron` 속의
     *    개망초·민망초로 보냈다.
     *
     * ⚠️ 8월과 9월을 **따로** 잰다. 틀린 답이 달마다 달랐기 때문이다(8월 개망초 ·
     *    9월 민망초) — 한 달만 재면 다른 달의 회귀를 못 본다.
     */
    @Test
    fun `실망초 별칭이 8월과 9월 모두 실망초로 번역한다`() {
        val 실망초 = 103
        for (month in listOf(8, 9)) {
            val got = index.flowerId("Erigeron bonariensis", preferring = blooming(month))
            assertEquals(
                "${month}월: 실망초(103)여야 한다. 받은 답: $got ${byId[got]?.name} — " +
                    "별칭이 빠지면 속 fallback이 개망초·민망초로 보낸다(실측 20장/200장)",
                실망초,
                got,
            )
        }
    }

    /**
     * 🔴 **별칭이 옆 종을 밀어내지 않았는가.** 별칭을 속 색인에도 넣었을 때
     *    실제로 이 일이 일어났다: 9월에 `Erigeron annuus`(개망초)와
     *    `Erigeron strigosus`가 `민망초` → `망초`로 **함께 움직였다**(24건 vs 22건).
     *    별칭이 속의 후보 **순서**를 바꿔 버린 것이다.
     *    → 그래서 `exact`에만 넣는다. 이 테스트가 그 결정을 고정한다.
     */
    @Test
    fun `별칭이 같은 속의 다른 종을 밀어내지 않는다`() {
        val sep = blooming(9)
        // 별칭이 없는 세계와 비교한다 — **대조군이 없으면 "안 밀렸다"를 말할 수 없다.**
        val noAlias = ScientificNameIndex(full.map { it.copy(scientificAliases = emptyList()) })
        for (name in listOf("Erigeron annuus", "Erigeron strigosus", "Erigeron acris")) {
            assertEquals(
                "$name 의 9월 번역이 별칭 때문에 바뀌었다 — 별칭은 `exact`에만 넣어야 한다",
                noAlias.flowerId(name, preferring = sep),
                index.flowerId(name, preferring = sep),
            )
        }
    }

    // ── 별칭이 남의 답을 빼앗지 않는가 ─────────────────────────────────

    /**
     * 🔴 **별칭이 실재하는 종의 학명을 덮으면 그 종을 정확히 맞혀도 가로챈다.**
     *    실제로 두 건이 그랬다: `Lythrum salicaria`는 **털부처꽃(1909)의 학명 그
     *    자체**이고 `Phedimus aizoon`은 **가는기린초(206)**다(`take(2)`가 아종·품종
     *    표기를 지우기 때문이다). 그래서 그 둘은 별칭에서 뺐다.
     *
     * ⚠️ 이건 "빠졌는지"가 아니라 **"다시 들어오면 red가 나는지"** 를 재는 검사다.
     */
    @Test
    fun `별칭이 도감의 다른 종의 학명을 빼앗지 않는다`() {
        val hijacked = full.flatMap { flower ->
            flower.scientificAliases.mapNotNull { alias ->
                // 별칭이 다른 종의 **자기 학명**과 같은 값으로 정규화되는가.
                val owner = full.firstOrNull {
                    it.id != flower.id &&
                        ScientificNameIndex.normalize(it.scientificName) ==
                        ScientificNameIndex.normalize(alias)
                }
                owner?.let { "`$alias`(${flower.name}) ⟶ ${it.id} ${it.name}의 학명" }
            }
        }
        assertTrue(
            "별칭이 다른 종의 답을 빼앗는다: $hijacked — 그 종을 정확히 맞혀도 " +
                "이 별칭이 가로챈다(scientific_aliases.py의 DO_NOT_ALIAS_AMBIGUOUS로 옮긴다)",
            hijacked.isEmpty(),
        )
    }

    /**
     * 🔴 **색인 자체가 빼앗기를 막는가 — 자산이 틀렸을 때를 위한 층이다.**
     *
     * ⚠️ 이 테스트는 **위 테스트가 못 잡는 것을 잡으려고** 나중에 추가했다.
     *    위 검사는 "지금 실린 16건이 안 빼앗는다"만 본다. 그런데 지금은 빼앗는 별칭이
     *    하나도 없으므로 **색인의 `putIfAbsent`를 `put`으로 바꿔도 8개 전부 초록이었다**
     *    (돌연변이 Ⓓ). 즉 그 방어선은 **아무 검사도 지나지 않는 코드**였다.
     *    자산은 `flowers.json` 하나만 갈아 끼우면 바뀌므로(적재 검사를 안 타는 경로가
     *    있다) 색인 쪽 방어선이 실제로 서 있는지를 여기서 직접 잰다.
     *
     * 도감 순서상 별칭이 **나중에** 주입되므로, 먼저 들어간 진짜 학명이 이겨야 한다.
     */
    @Test
    fun `자산이 남의 학명을 별칭으로 들고 와도 색인이 원래 종을 지킨다`() {
        val 부처꽃 = full.first { it.id == 128 }
        val 털부처꽃 = full.first { it.id == 1909 }
        // 자산이 잘못 만들어진 상황을 그대로 만든다 — 이건 실제로 한 번 그랬다.
        val poisoned = full.map {
            if (it.id == 128) it.copy(scientificAliases = listOf("Lythrum salicaria subsp. anceps"))
            else it
        }
        val idx = ScientificNameIndex(poisoned)

        assertEquals(
            "별칭이 털부처꽃(1909)의 학명을 빼앗았다 — 색인은 먼저 들어간 진짜 학명을 " +
                "지켜야 한다(`exact.putIfAbsent`)",
            털부처꽃.id,
            idx.flowerId(털부처꽃.scientificName),
        )
        // 그리고 부처꽃은 자기 학명으로 계속 찾아진다 — 방어가 다른 것을 깨지 않았다.
        assertEquals(부처꽃.id, idx.flowerId(부처꽃.scientificName))
    }

    /** 위 두 건이 **여전히 빼앗는 관계인지** 확인한다 — 이유가 낡으면 별칭으로 옮길 수 있다. */
    @Test
    fun `빼앗기 때문에 뺀 두 학명은 실제로 다른 종의 것이다`() {
        // `Lythrum salicaria subsp. anceps` → 털부처꽃(1909) / 부처꽃은 128
        assertEquals(1909, index.flowerId("Lythrum salicaria subsp. anceps"))
        // `Phedimus aizoon var. floribundus` → 가는기린초(206) / 기린초는 133
        assertEquals(206, index.flowerId("Phedimus aizoon var. floribundus"))
    }

    // ── 계약 1-1-a: 사람이 정한 200종이 확장분에 밀리지 않는가 ──────────

    /**
     * 🔴 **별칭과 무관한 별건이지만 같은 층의 결함이다.** 도감 2,057종 안에
     *    정규화가 겹치는 학명이 **9쌍** 있다(`take(2)`가 아종·품종을 지운다).
     *    색인이 나중 것으로 덮으면 **번호가 큰 확장분이 이긴다**:
     *    `Rudbeckia hirta`가 원추천인국(107)이 아니라 수잔루드베키아(1315)로
     *    번역됐다 — 실측 응답 **11장**이 이 경로다.
     *
     *    계약 1-1-a가 200종을 지키기로 정했으니 색인도 그쪽을 지켜야 한다.
     *    ⚠️ 화면에는 증상이 없다(둘 다 실재하는 꽃 이름이다). 그리고 두 종이
     *       **같은 속**이라 속 단위 지표는 원리상 안 움직인다.
     */
    @Test
    fun `학명이 겹치면 사람이 정한 200종이 이긴다`() {
        assertEquals(
            "Rudbeckia hirta → 원추천인국(107)이어야 한다(계약 1-1-a). " +
                "수잔루드베키아(1315)가 나오면 색인이 큰 번호로 덮은 것이다",
            107,
            index.flowerId("Rudbeckia hirta"),
        )

        // 겹치는 쌍 전부에 대해 같은 규칙이 서는지 본다 — 한 건만 재면 나머지 8쌍이
        // 조용히 반대로 가도 초록이다.
        val loser = full
            .groupBy { ScientificNameIndex.normalize(it.scientificName) }
            .filterValues { it.size > 1 }
            .mapNotNull { (name, group) ->
                val expected = group.minOf { it.id }
                val got = index.flowerId(name)
                if (got != expected) "$name → $got(${byId[got]?.name}), 기대 $expected" else null
            }
        assertTrue(
            "학명이 겹치는 종에서 큰 번호가 이겼다: $loser",
            loser.isEmpty(),
        )
        // 겹치는 쌍이 **있다는 것 자체**를 고정한다 — 도감이 바뀌어 0쌍이 되면
        // 위 루프가 한 번도 안 돌고 통과한다(빈 루프는 초록이다).
        val pairs = full
            .groupBy { ScientificNameIndex.normalize(it.scientificName) }
            .count { it.value.size > 1 }
        assertEquals("정규화가 겹치는 학명 쌍의 수가 바뀌었다 — 규칙을 다시 확인한다", 9, pairs)
    }

    // ── 별칭이 없으면 무엇이 틀리는가 (대조군) ─────────────────────────

    /**
     * 🔴 **이 테스트의 목적은 "별칭이 뭔가를 실제로 바꾼다"를 증명하는 것이다.**
     *    위 검사들이 전부 초록인데 별칭이 **아무 일도 안 하고 있을** 수 있다 —
     *    속 fallback이 우연히 같은 답을 주면 그렇게 된다. 그러면 이 파일 전체가
     *    **아무것도 지키지 않는다.** 대조군과 비교해 차이가 있음을 확인한다.
     *
     *    ⚠️ 이 저장소가 반복해 당한 실패의 형태다: 검사가 초록인데 검사 대상이
     *       비어 있는 경우(빈 루프·빈 표본). "어떤 경우에 빨개지나"에 답하려면
     *       **효과가 있다는 것 자체**를 재야 한다.
     */
    @Test
    fun `별칭이 없으면 실제로 답이 달라진다`() {
        val noAlias = ScientificNameIndex(full.map { it.copy(scientificAliases = emptyList()) })
        val aug = blooming(8)
        assertNotEquals(
            "별칭을 빼도 답이 같다 — 그러면 별칭이 아무 일도 하지 않는 것이고 " +
                "이 파일의 다른 검사들은 아무것도 지키지 않는다",
            noAlias.flowerId("Erigeron bonariensis", preferring = aug),
            index.flowerId("Erigeron bonariensis", preferring = aug),
        )
    }
}
