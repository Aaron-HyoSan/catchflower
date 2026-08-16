package com.catchflower.app.data

import com.catchflower.app.core.AiDifficulty
import com.catchflower.app.core.BloomSource
import com.catchflower.app.core.Rarity
import com.catchflower.app.core.Season
import com.catchflower.app.data.model.Flower
import org.json.JSONObject

/**
 * JVM 테스트용 도감 — `plantnet_replay.json`에서 읽는다.
 *
 * **왜 픽스처인가.** 유닛 테스트는 `Context.assets`를 못 읽는다(그건 계측 테스트다).
 * 그래서 실제 자산 `꽃도감/flowers.json`에서 **필요한 칸만 뽑은 배열**을 픽스처에
 * 함께 담아 두고 여기서 되돌린다. 재생성:
 * `python3 android/_tools/build_plantnet_replay_fixture.py` (API 호출 0건).
 *
 * 🔴 **왜 한 군데로 모았는가.** 같은 코드가 [com.catchflower.app.recognizer.PlantNetReplayTest]·
 *    [com.catchflower.app.recognizer.ScientificAliasTest]에 각각 있었는데,
 *    `Flower`에 필수 필드가 하나 붙자(계약 1-6 `collect_group_id`) **세 곳이 동시에
 *    컴파일 실패**했다. 그때는 컴파일러가 알려주지만, 필드가 `optInt`처럼
 *    기본값으로 넘어가는 종류면 **한 곳만 고치고 나머지는 조용히 옛 값으로 돈다.**
 *
 * ⚠️ **`opt*`를 쓰지 않는다.** 픽스처에 칸이 없으면 여기서 죽는 게 맞다 —
 *    빈 목록·0으로 넘어가면 별칭 없는 색인·접기 없는 도감으로 재면서
 *    **지표는 그대로 나온다**(그 둘은 Top-1·화면12 비율에 원리상 안 나타난다).
 */
internal object FixtureDex {

    private val fixture: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream(RESOURCE)
        requireNotNull(stream) {
            "$RESOURCE 이 없다. python3 android/_tools/build_plantnet_replay_fixture.py 를 돌린다"
        }
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    const val RESOURCE = "plantnet_replay.json"

    /** 사람이 정한 **대조군 200종**(id 1~200). iOS 실측 77.0%와 비교하는 기준이다. */
    fun control(): List<Flower> = read("flowers")

    /** 앱이 실제로 싣는 **2,057종**. */
    fun full(): List<Flower> = read("flowers_full")

    /** 픽스처의 다른 칸을 읽어야 할 때. */
    fun raw(): JSONObject = fixture

    private fun read(key: String): List<Flower> {
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
                // ⚠️ 아래 다섯 칸은 **픽스처에 없다**(판별·그룹 검증에 안 쓰인다).
                //    화면 필터를 재는 테스트는 이 도감으로 재지 않는다 — 색·계절이 전부 같아서
                //    "필터가 도는가"를 **원리상 못 잰다**(`DexFilterTest`가 지어낸 꽃으로 잰다).
                family = "",
                bloomMonths = (0 until months.length()).map { months.getInt(it) },
                bloomLabel = "",
                bloomSource = BloomSource.fromWire(o.getString("bloom_source")),
                season = Season.SPRING,
                color = "",
                rarity = Rarity.COMMON,
                habitat = "",
                // 🔴 **`LOW`로 고정하지 않는다.** 접으면 임계값이 **대표종의 난이도**로
                //    갈린다(32 서양민들레 상 0.85 → 31 민들레 중 0.70). 전 종을 `LOW`로
                //    두면 09 / 09변형 분기가 **원리상 안 움직인다.**
                aiDifficulty = AiDifficulty.fromWire(o.getString("ai_difficulty")),
                similarFlowerIds = emptyList(),
                similarFlowerNames = emptyList(),
                collectGroupId = o.getInt("collect_group_id"),
                illustBatch = 1,
            )
        }
    }
}
