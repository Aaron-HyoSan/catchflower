package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.json.JSONObject

/**
 * 도감 마스터 200종을 assets에서 읽는다.
 *
 * 원본은 `꽃도감/꽃목록_200종.csv` → `_tools/build_app_data.py` → `flowers.json`.
 * **CSV를 직접 고치지 않는다** (생성물이다).
 *
 * 서버(Supabase)가 붙어도 이 200종은 앱 번들에 남긴다 — 도감 화면이 네트워크 없이 떠야 한다.
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

    /** 화면 06 색상 필터에 쓰는 대표색 목록. */
    val colors: List<String> by lazy { flowers.map { it.color }.distinct() }

    companion object {
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
                    family = o.getString("family"),
                    bloomMonths = o.getJSONArray("bloom_months").let { arr ->
                        List(arr.length()) { arr.getInt(it) }
                    },
                    bloomLabel = o.getString("bloom_label"),
                    season = Season.fromWire(o.getString("season")),
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

            // 적재 단위로 검증한다. 200종이 아니면 도감 진행률(`37 / 200종`)이 틀리고
            // 그건 화면 04·10·22에 그대로 노출된다.
            check(flowers.size == GamePolicy.TOTAL_FLOWER_COUNT) {
                "도감이 ${GamePolicy.TOTAL_FLOWER_COUNT}종이어야 한다. 실제 ${flowers.size}종"
            }
            return FlowerRepository(flowers)
        }
    }
}
