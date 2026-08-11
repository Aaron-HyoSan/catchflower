package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.json.JSONObject

/**
 * 도감 마스터 **2,057종**을 assets에서 읽는다.
 *
 * 원본은 `꽃도감/꽃목록_확장_2057종.csv` + `꽃목록_200종.csv`(id 1~200) →
 * `공용_적재/flower_master.py` → `꽃도감/_tools/build_app_data.py` → `flowers.json`.
 * **CSV도 JSON도 직접 고치지 않는다** (둘 다 생성물이다).
 *
 * 서버(Supabase)가 붙어도 이 2,057종은 앱 번들에 남긴다 — 도감 화면이 네트워크 없이 떠야 한다.
 */
class FlowerRepository private constructor(val flowers: List<Flower>) {

    private val byId: Map<Int, Flower> = flowers.associateBy { it.id }

    fun byId(id: Int): Flower? = byId[id]

    /** 이 달에 피는 종. **개화월 하드 필터의 후보 집합**이다 (A-1 필수 구현). */
    fun bloomingIn(month: Int): List<Flower> = flowers.filter { it.bloomsIn(month) }

    fun bySeason(season: Season): List<Flower> = flowers.filter { it.season == season }

    fun byRarity(rarity: Rarity): List<Flower> = flowers.filter { it.rarity == rarity }

    fun byDifficulty(difficulty: AiDifficulty): List<Flower> =
        flowers.filter { it.aiDifficulty == difficulty }

    /** 화면 05·09의 '비슷한 꽃' — 도감 안에 있는 것만. */
    fun similarTo(flower: Flower): List<Flower> = flower.similarFlowerIds.mapNotNull(byId::get)

    /**
     * 화면 06 색상 필터에 쓰는 대표색 목록.
     *
     * ⚠️ **빈 문자열을 뺀다.** 신규 1,857종은 대표색이 없어서 그대로 두면
     *    목록에 `""` 항목이 하나 생기고, 그게 칩으로 그려지면 **테두리만 있는 칩**이 된다.
     */
    val colors: List<String> by lazy { flowers.mapNotNull { it.color.ifEmpty { null } }.distinct() }

    companion object {
        /**
         * 도감은 **번들 자산에서만** 읽는다. `flowers` 표를 REST로 읽지 않는다.
         *
         * 🔴 **여기에 함정이 하나 잠들어 있다 — 지금은 안 터지지만 이유를 적어 둔다.**
         *    이 자산은 값이 없는 칸을 **빈 문자열 `""`** 로 준다(`color`·`habitat`·
         *    `bloom_label`). 그런데 **DB(`0006` 적재)는 같은 칸을 `null`로** 넣는다 —
         *    `is null` 검사가 서고 잊은 분기가 크게 터지도록 일부러 그렇게 정했다
         *    (계약 1-1-c 정정). 즉 **두 원본의 표현이 다르다.**
         *
         *    그래서 누군가 나중에 도감을 REST로 갈아 끼우면, 아래 `getString("color")`가
         *    JSON `null`을 만나 **기기에서는 `"null"` 네 글자를 돌려준다**(그 문자열이
         *    속성 칩에 그려진다). JVM `org.json`에서는 예외라 **유닛 테스트만 빨개지고
         *    기기에서는 조용히 틀린다** — 방향이 반대라 픽스처로는 절대 못 잡는다.
         *    `season`이 이미 그 경로라서 `isNull`로 판정하고 있다(아래).
         *
         *    → REST로 바꿀 때는 **`getString`을 쓰는 칸 전부를 `isNull` 분기로** 옮긴다.
         */
        private const val ASSET = "flowers.json"

        @Volatile
        private var instance: FlowerRepository? = null

        fun get(context: Context): FlowerRepository =
            instance ?: synchronized(this) {
                instance ?: load(context).also { instance = it }
            }

        /**
         * 이미 읽어 둔 200종으로 만든다 — **JVM 테스트가 [IdentifyFlow]를 태우기 위한 문**이다.
         *
         * 🔴 **화면 12까지 재려면 이게 필요하다.** [get]은 `Context.assets`를 타서
         *    JVM 테스트에서 못 쓴다. 그래서 실측 캐시 200장을 재현하는
         *    `PlantNetReplayTest`가 `identify()`까지만 재고 **`IdentifyFlow.decide()`는
         *    한 번도 태우지 않았다** — 즉 `MIN_CONFIDENCE_FOR_ANY_CANDIDATE`가
         *    **사용자에게 보이는 실패율**로 얼마가 되는지 재는 검사가 없었다.
         *    Top-1 77%는 초록인데 실제로는 8월 촬영의 72%가 화면 12로 갔다(실측 (42)).
         *
         * ⚠️ [instance]를 건드리지 않는다. 캐시에 넣으면 테스트가 만든 목록이
         *    앱 경로로 새어 나간다.
         */
        fun forTest(flowers: List<Flower>): FlowerRepository = FlowerRepository(flowers)

        private fun load(context: Context): FlowerRepository {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val array = JSONObject(text).getJSONArray("flowers")
            val flowers = ArrayList<Flower>(array.length())

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                flowers += Flower(
                    id = o.getInt("id"),
                    name = o.getString("name"),
                    scientificName = o.getString("scientific_name"),
                    // 계약 1-1-e. 🔴 **`optJSONArray`를 쓰지 않는다** — 칸이 없으면
                    //    조용히 빈 목록이 되고, 그러면 별칭 16건이 **하나도 안 실린
                    //    상태로 앱이 정상 동작한다**(증상이 없다: 꽃 이름은 예쁘게
                    //    나오고 다만 틀린 종이다). 없으면 여기서 죽는 게 맞다.
                    scientificAliases = o.getJSONArray("scientific_aliases").let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    },
                    family = o.getString("family"),
                    bloomMonths = o.getJSONArray("bloom_months").let { arr ->
                        List(arr.length()) { arr.getInt(it) }
                    },
                    bloomLabel = o.getString("bloom_label"),
                    bloomSource = BloomSource.fromWire(o.getString("bloom_source")),
                    // 🔴 **`isNull`로 판정한다.** `optString("season")`을 쓰면
                    //    JSON `null`에 대해 **기기에서는 `"null"`**(문자열 네 글자),
                    //    **JVM 테스트에서는 `""`** 를 준다 — org.json 구현이 다르다.
                    //    즉 픽스처로 짠 유닛 테스트는 초록인데 **기기에서만**
                    //    `error("알 수 없는 season: null")`로 죽는다. 278종이 여기 걸린다.
                    season = if (o.isNull("season")) null
                    else Season.fromWire(o.getString("season")),
                    color = o.getString("color"),
                    rarity = Rarity.fromWire(o.getString("rarity")),
                    habitat = o.getString("habitat"),
                    aiDifficulty = AiDifficulty.fromWire(o.getString("ai_difficulty")),
                    similarFlowerIds = o.getJSONArray("similar_flower_ids").let { arr ->
                        List(arr.length()) { arr.getInt(it) }
                    },
                    similarFlowerNames = o.getJSONArray("similar_flower_names").let { arr ->
                        List(arr.length()) { arr.getString(it) }
                    },
                    illustBatch = o.getInt("illust_batch"),
                )
            }

            // 적재 단위로 검증한다. 종수가 다르면 도감 진행률(`37 / 2057종`)이 틀리고
            // 그건 화면 04·10·22에 그대로 노출된다.
            check(flowers.size == GamePolicy.TOTAL_FLOWER_COUNT) {
                "도감이 ${GamePolicy.TOTAL_FLOWER_COUNT}종이어야 한다. 실제 ${flowers.size}종"
            }
            // 🔴 개수만 세면 **번호가 겹치거나 비어도 통과한다.** 2,057종에서는
            //    눈으로 못 본다 — 그리고 `discoveries.flower_id`가 외래키라
            //    번호가 밀리면 **사용자 발견 기록이 다른 꽃을 가리킨다**(계약 1-5).
            //    적재 스크립트도 같은 검사를 하지만, 번들이 갈아 끼워질 수 있으니 여기서도 센다.
            val ids = flowers.mapTo(HashSet(flowers.size)) { it.id }
            check(ids.size == flowers.size) { "도감번호가 중복이다 (고유 ${ids.size} / ${flowers.size}종)" }
            val missing = (1..GamePolicy.TOTAL_FLOWER_COUNT).firstOrNull { it !in ids }
            check(missing == null) { "도감번호 ${missing}번이 없다 — 1~${GamePolicy.TOTAL_FLOWER_COUNT} 연속이어야 한다" }
            // 🔴 **별칭 총수를 센다** (계약 1-1-e). 위 `getJSONArray`는 칸이 있는지만
            //    보므로, 적재가 **전부 빈 배열**을 내려도 통과한다 — 그러면 실측 200장
            //    중 20장이 걸린 `Erigeron bonariensis` 경로가 조용히 되살아난다.
            //    자산이 갈아 끼워질 수 있으니 적재 쪽 검사에 의존하지 않고 여기서도 센다.
            val aliasCount = flowers.sumOf { it.scientificAliases.size }
            check(aliasCount == GamePolicy.SCIENTIFIC_ALIAS_COUNT) {
                "학명 별칭이 ${GamePolicy.SCIENTIFIC_ALIAS_COUNT}개여야 한다. 실제 ${aliasCount}개" +
                    " — `공용_적재/scientific_aliases.py`와 자산이 어긋났다"
            }
            return FlowerRepository(flowers)
        }
    }
}
