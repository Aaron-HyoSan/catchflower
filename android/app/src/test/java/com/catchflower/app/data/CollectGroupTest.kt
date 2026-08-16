package com.catchflower.app.data

import com.catchflower.app.core.GamePolicy
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import com.catchflower.app.recognizer.IdentifyFlow
import com.catchflower.app.recognizer.IdentifyOutcome
import com.catchflower.app.recognizer.PlantNetRecognizer
import com.catchflower.app.recognizer.RankedCandidate
import com.catchflower.app.recognizer.ScientificNameIndex
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **B-4 수집 그룹** (계약 1-6) — 사진으로 못 가르는 종을 도감 한 칸으로 접는다.
 *
 * 원본 표는 `공용_적재/collect_groups.py`(8그룹 · 멤버 13종 · 칸 2,044개)이고
 * 그쪽 `self_check`가 표 자체를 검사한다. **여기서는 표가 아니라 앱의 동작을 잰다.**
 *
 * 🔴 **왜 이 파일이 따로 필요한가 — 접기는 네 층에서 각각 다르게 틀린다.**
 *
 * | 층 | 틀리는 방향 | 화면에 보이는 증상 |
 * |---|---|---|
 * | 도감 그리드 | 접기를 안 한다 | 2,057칸. **채울 수 없는 칸 13개** — 사용자는 자기가 못 찍는 줄 안다 |
 * | 후보 집합 | **접기를 너무 앞에서** 한다 | 8월 서양민들레(3~10월)가 후보에서 사라져 **인식이 준다** |
 * | 후보 자르기 | 종 단위로 3개를 센다 | 화면 09변형에 후보가 **2개**만 뜬다 |
 * | 저장 | 메모리에서만 접는다 | 도감 37칸인데 서버 랭킹은 38종 (**둘 다 그럴듯하다**) |
 *
 * ⚠️ 넷 중 **예외가 나는 것은 하나도 없다.** 그래서 "돌아간다"로는 못 가리고,
 *    층마다 "무엇이 달라야 하는가"를 따로 잰다.
 *
 * ⚠️ **도감은 픽스처를 쓴다** — [FixtureDex]. `FlowerRepository.get()`은 `assets`를
 *    타서 JVM에서 못 돈다. 즉 [FlowerRepository]의 적재 시점 `check`들(칸 수·사슬)은
 *    **이 테스트가 지나지 않는다** — 그래서 같은 불변식을 아래에서 다시 잰다.
 */
class CollectGroupTest {

    private val flowers = FixtureDex.full()
    private val repository = FlowerRepository.forTest(flowers)
    private val byId = flowers.associateBy { it.id }

    /** 실제로 접히는 멤버 수. 표(`collect_groups.py`)의 13과 같아야 한다. */
    private val memberCount = GamePolicy.TOTAL_FLOWER_COUNT - GamePolicy.DEX_SLOT_COUNT

    // ── 도감 칸 ─────────────────────────────────────────────────────────

    /**
     * 그리드가 그리는 칸은 **2,044개**이고, 목록(2,057행)은 줄지 않는다.
     *
     * 🔴 **둘을 같이 잰다.** 칸만 재면 "행을 13개 지워서" 통과할 수 있는데, 그러면
     *    `discoveries.flower_id`(FK)가 가리키는 종이 사라진다(계약 1-5).
     */
    @Test
    fun `도감 칸은 2044개이고 행은 2057개 그대로다`() {
        assertEquals("도감 행이 줄었다 — 종을 지우면 안 된다", GamePolicy.TOTAL_FLOWER_COUNT, flowers.size)
        assertEquals("도감 칸 수가 다르다", GamePolicy.DEX_SLOT_COUNT, repository.dexFlowers.size)
        assertEquals("접히는 멤버가 ${memberCount}종이 아니다", memberCount, flowers.size - repository.dexFlowers.size)
        // 번호는 재배치하지 않는다 — 1~2,057이 그대로 남는다.
        assertEquals("도감번호가 1~${GamePolicy.TOTAL_FLOWER_COUNT} 연속이 아니다",
            GamePolicy.TOTAL_FLOWER_COUNT, byId.keys.count { it in 1..GamePolicy.TOTAL_FLOWER_COUNT })
    }

    /**
     * **접기가 사슬이 되지 않는다** (A→B→C 금지).
     *
     * 사슬이면 한 번 접기로 안 끝나서 **칸을 세는 쪽과 등록하는 쪽이 다른 칸을 쓴다.**
     * 그래도 양쪽 다 그럴듯한 숫자가 나온다 — 그래서 여기서 잡는다.
     */
    @Test
    fun `접기가 사슬이 되지 않는다`() {
        for (flower in flowers) {
            val rep = byId[flower.collectGroupId]
            assertNotNull("${flower.id} ${flower.name}의 대표 ${flower.collectGroupId}가 도감에 없다", rep)
            assertTrue(
                "${flower.id} ${flower.name}의 대표 ${rep!!.id} ${rep.name}이 또 다른 그룹의 멤버다",
                rep.isDexRepresentative,
            )
        }
    }

    /** 멤버를 넣으면 대표가 나온다. **모르는 번호는 그대로 돌려준다**(서버가 새 종을 알 수 있다). */
    @Test
    fun `멤버를 넣으면 대표종이 나온다`() {
        assertEquals(31, repository.groupIdOf(32))          // 서양민들레 → 민들레
        assertEquals(31, repository.groupIdOf(31))          // 대표는 자기 자신 (멱등의 근거)
        assertEquals("민들레", repository.representativeOf(32)?.name)
        assertEquals(105, repository.groupIdOf(104))        // 큰금계국 → 금계국
        assertEquals(105, repository.groupIdOf(106))        // 기생초 → 금계국
        assertEquals(184, repository.groupIdOf(186))        // 미국쑥부쟁이(속이 다르다) → 쑥부쟁이

        // 도감에 없는 번호로 앱을 죽이지 않는다 — 이름을 못 찾는 것과 죽는 것은 다르다.
        val unknown = GamePolicy.TOTAL_FLOWER_COUNT + 1
        assertEquals(unknown, repository.groupIdOf(unknown))
        assertNull(repository.representativeOf(unknown))
    }

    /**
     * 🔴 **순서가 설계다: `후보(종) → 매칭(종) → 접기(그룹)`.**
     *
     * 멤버의 `bloom_months`를 대표종에 합치지도, 멤버를 후보에서 빼지도 않는다.
     * 이 테스트가 지키는 것은 **8월에 민들레를 찍을 수 있다**는 사실이다:
     *
     * | | 개화월 | 8월 후보 | 등록되는 칸 |
     * |---|---|---|---|
     * | 31 민들레(대표) | 3~5월 | ❌ 안 들어간다 | — |
     * | 32 서양민들레(멤버) | 3~10월 | ✅ **들어가야 한다** | 31 민들레 |
     *
     * ⚠️ 여기가 빨개지는 경우 = 누군가 후보 집합을 `dexFlowers`로 바꿨을 때다.
     *    그러면 도감·랭킹은 정상으로 보이고 **8월 민들레만 조용히 판별 실패**한다.
     */
    @Test
    fun `8월 후보에 멤버가 남아 있고 등록은 대표 칸으로 간다`() {
        val august = IdentifyFlow(repository).candidatesForMonth(8)
        assertTrue("8월 후보에 32 서양민들레가 없다 — 접기가 후보 만들기 앞에 왔다", 32 in august)
        assertFalse("8월 후보에 31 민들레가 있다 — 개화월(3~5월)이 무시됐다", 31 in august)
        // 멤버의 학명 색인도 살아 있어야 한다. 지우면 8월에 아무것도 안 걸린다.
        assertEquals(32, ScientificNameIndex(flowers).flowerId("Taraxacum officinale", august.toSet()))
        // 그런데 등록되는 칸은 대표다 — 그래서 "8월에 찍었는데 도감 칸이 없다"가 안 생긴다.
        assertEquals(31, repository.groupIdOf(32))
    }

    // ── 후보 3칸을 세는 단위 ─────────────────────────────────────────────

    /** 화면에 실제로 뜨는 후보. */
    private fun shown(outcome: IdentifyOutcome): List<RankedCandidate> = when (outcome) {
        is IdentifyOutcome.Confident -> listOf(outcome.top) + outcome.alternatives
        is IdentifyOutcome.Ambiguous -> outcome.candidates
        IdentifyOutcome.Failed -> emptyList()
    }

    private fun response(vararg pairs: Pair<String, Double>): String =
        """{"results":[""" + pairs.joinToString(",") { (name, score) ->
            """{"score":$score,"species":{"scientificNameWithoutAuthor":"$name"}}"""
        } + "]}"

    private fun recognizer(body: String, groupOf: (Int) -> Int) = PlantNetRecognizer(
        index = ScientificNameIndex(flowers),
        apiKey = "synthetic",
        transport = object : PlantNetRecognizer.Transport {
            override suspend fun post(url: String, contentType: String, b: ByteArray) = 200 to body
        },
        groupOf = groupOf,
    )

    /**
     * 🔴 **B-4의 실제 지점** — [PlantNetRecognizer.parse]가 후보를 **그룹 단위로 끊는다.**
     *
     * **왜 실측 200장으로는 이걸 못 재는가.** `PlantNetReplayTest`에서 재 봤더니
     * 전/후 후보 개수 분포가 **완전히 같았다**(`같은 칸 중복 0장`). 이유는 결함이
     * 아니라 색인이다 — 같은 그룹의 학명들이 별칭·속 폴백으로 **후보 목록에 오기 전에
     * 이미 한 종으로 모인다**(`Taraxacum mongolicum`은 31의 별칭이고
     * `Taraxacum sect. Taraxacum`은 속 폴백이다). 그래서 실제 응답에는 "같은 그룹의
     * 서로 다른 두 종"이 나란히 오는 경우가 없었다. **그건 이 축을 측정할 수 없다는
     * 뜻이고, 없다는 뜻이 아니다** — 금계국 3종(`Coreopsis` 3종이 학명으로 다 다르다)은
     * 실제로 그렇게 온다. 그래서 응답을 지어내서 잰다.
     *
     * 응답(점수 내림차순):
     *
     * | 학명 | 종 | 그룹 |
     * |---|---|---|
     * | `Coreopsis lanceolata` 0.50 | 104 큰금계국 | 105 |
     * | `Coreopsis tinctoria` 0.20 | 106 기생초 | **105 (같다)** |
     * | `Tagetes erecta` 0.15 | 161 메리골드 | 161 |
     * | `Persicaria hydropiper` 0.10 | 139 여뀌 | 139 |
     *
     * 종으로 3개를 끊으면 `104 · 106 · 161`이 오고 접힌 뒤 화면에 **2개**가 남는다.
     * 그룹으로 끊으면 `104 · 161 · 139` → 화면에 **3개**다.
     */
    @Test
    fun `후보 3칸을 종이 아니라 그룹으로 센다`() = runBlocking {
        val body = response(
            "Coreopsis lanceolata" to 0.50,
            "Coreopsis tinctoria" to 0.20,
            "Tagetes erecta" to 0.15,
            "Persicaria hydropiper" to 0.10,
        )
        val flow = IdentifyFlow(repository)
        val july = flow.candidatesForMonth(7)
        // 지어낸 응답이 **실제로 후보 집합을 통과하는지** 먼저 확인한다. 안 그러면
        // 개화월에서 전부 걸려도 "후보 0개"가 나오고 그건 통과처럼 안 보인다.
        assertTrue("7월 후보에 네 종이 다 있어야 한다", listOf(104, 106, 161, 139).all { it in july })

        // 대조군 — 끊는 단위가 **종**일 때 (B-4 이전 코드와 같다)
        val bySpecies = recognizer(body, groupOf = { it }).identify(ByteArray(1), july)
        assertEquals(listOf(104, 106, 161), bySpecies.map { it.flowerId })
        assertEquals("종으로 끊으면 화면 후보가 2개로 준다 — B-4가 후보를 줄이는 상태다",
            2, shown(flow.decide(bySpecies)).size)

        // 지금 코드 — 끊는 단위가 **그룹**
        val byGroup = recognizer(body, groupOf = repository::groupIdOf).identify(ByteArray(1), july)
        assertEquals("접힌 자리만큼 뒤 후보가 올라와야 한다", listOf(104, 161, 139), byGroup.map { it.flowerId })

        val outcome = flow.decide(byGroup)
        val cards = shown(outcome)
        assertEquals(GamePolicy.CANDIDATE_COUNT, cards.size)
        // 표시되는 것은 **대표종**이다(104 큰금계국이 아니라 105 금계국).
        assertEquals(listOf(105, 161, 139), cards.map { it.flower.id })
        assertEquals(listOf("금계국", "메리골드", "여뀌"), cards.map { it.flower.name })
        assertEquals(listOf(1, 2, 3), cards.map { it.rank })
        // ⚠️ **점수는 그룹에서 가장 높은 멤버의 것이다**(0.50 — 104). 대표종 105는
        //    응답에 아예 없었다. 여기가 0.20이면 인식기가 뒤에 온 멤버로 덮어썼다는 뜻이고,
        //    그러면 화면 09/09변형 분기가 낮은 점수로 갈린다.
        assertEquals(0.50f, cards[0].score, 1e-4f)
        // 임계값은 **대표종의 난이도**로 정해진다(105 금계국 = 상 0.85) → 09 변형.
        assertTrue("1순위 0.50 < 상 임계값 0.85이므로 화면 09변형이어야 한다",
            outcome is IdentifyOutcome.Ambiguous)
    }

    // ── 저장된 기록 이관 ─────────────────────────────────────────────────

    private lateinit var dir: File
    private lateinit var store: DiscoveryStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("cf-group").toFile()
        store = DiscoveryStore(File(dir, "discoveries.json"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun discovery(id: String, flowerId: Int) = Discovery(
        id = id,
        userId = "11111111-1111-4111-8111-111111111111",
        flowerId = flowerId,
        photoUrl = null,
        localPhotoPath = "$id.jpg",
        lat = 37.5445,
        lng = 127.0557,
        placeName = "서울숲",
        dongCode = "1120052000",
        guCode = "11200",
        visibility = Visibility.PRIVATE,
        aiConfidence = 0.82f,
        aiPickedRank = 1,
        isFirstDiscovery = true,
        createdAt = 1_786_000_000_000L,
        capturedAt = 1_786_000_000_000L,
    )

    private fun repositoryFor() = DiscoveryRepository(
        store = store,
        photos = PhotoStore(File(dir, "photos")),
        userId = "11111111-1111-4111-8111-111111111111",
        // auth·uploader가 null이면 네트워크를 안 탄다.
        log = { },
        groupOf = repository::groupIdOf,
    )

    /**
     * 🔴 **파일까지 고친다** — 메모리에서만 접으면 업로드 큐가 **멤버 번호를 서버로 보낸다.**
     *
     * 그러면 화면은 `모은 꽃 2종`인데 서버 랭킹은 3종을 센다. 둘 다 그럴듯해서
     * 어느 쪽이 맞는지 **화면으로는 알 수 없다.**
     *
     * ⚠️ 그래서 단정을 `discoveries`(메모리)로 끝내지 않고 **새 [DiscoveryStore]로
     *    다시 읽는다.** 메모리만 보면 `store.save`를 빼도 초록이다.
     */
    @Test
    fun `저장된 멤버 번호를 대표종으로 바꿔 파일에 다시 쓴다`() = runBlocking {
        store.save(
            listOf(
                discovery("a", 32),   // 서양민들레 → 31
                discovery("b", 106),  // 기생초 → 105
                discovery("c", 73),   // 팬지 = 대표 (그대로)
            ),
        )

        repositoryFor().load()

        // 파일을 **다시 읽는다.** 메모리가 아니라 여기가 서버로 가는 값이다.
        val onDisk = DiscoveryStore(File(dir, "discoveries.json")).load()
        assertEquals(mapOf("a" to 31, "b" to 105, "c" to 73), onDisk.associate { it.id to it.flowerId })
        // 도감 칸으로 세면 3건이 3칸이 아니라 **3칸**이다(겹치는 게 없다) — 개수도 확인한다.
        assertEquals(3, onDisk.size)
    }

    /** 멱등이다. 두 번 돌아도 대표종은 자기 자신으로 남는다 — 앱은 매 실행마다 이걸 지난다. */
    @Test
    fun `이관은 두 번 돌아도 같다`() = runBlocking {
        store.save(listOf(discovery("a", 32), discovery("b", 186)))

        repositoryFor().load()
        val first = DiscoveryStore(File(dir, "discoveries.json")).load().map { it.flowerId }
        // ⚠️ **새 인스턴스로** 다시 돈다. 같은 인스턴스는 `_loaded`가 true라서 두 번째
        //    `load()`가 바로 돌아온다 — 그러면 멱등을 재는 게 아니라 아무것도 안 잰다.
        repositoryFor().load()
        val second = DiscoveryStore(File(dir, "discoveries.json")).load().map { it.flowerId }

        assertEquals(listOf(31, 184), first)
        assertEquals(first, second)
    }

    /**
     * 접을 게 없으면 **파일을 다시 쓰지 않는다.**
     *
     * 매 실행마다 무조건 쓰면 기록 수천 건을 쓰는 I/O가 앱 시작에 붙고,
     * 그건 **증상이 느려짐뿐**이라 아무도 원인을 못 찾는다.
     */
    @Test
    fun `접을 게 없으면 파일을 건드리지 않는다`() = runBlocking {
        store.save(listOf(discovery("a", 31), discovery("b", 73)))
        val file = File(dir, "discoveries.json")
        val before = file.readText()
        file.setLastModified(0L)

        repositoryFor().load()

        assertEquals("내용이 같아야 한다", before, file.readText())
        assertEquals("접을 게 없는데 파일을 다시 썼다", 0L, file.lastModified())
    }
}
