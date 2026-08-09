package com.catchflower.app.ui.onboarding

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 화면 03의 문구가 **A 문서와 글자까지 같은가.**
 *
 * **왜 테스트로 두는가.** 프로젝트 규칙은 "UI 문구를 새로 쓰지 않는다"인데,
 * 이건 사람이 지키는 규칙이라 **조용히 깨진다.** 마침표 하나, `돼요`/`되요`,
 * `저장됩니다`/`저장돼요` — 화면을 봐도 틀린 걸 알 수 없고, A 문서를 나란히 놓고
 * 대조해야만 보인다. 중장년 타깃이라 문장 종결까지 통일이 스펙의 일부다.
 *
 * ⚠️ **`contains`로 검사하지 않는다.** 처음에 그렇게 썼는데, 돌연변이로
 *    `앨범 사진은 등록할 수 없어요.`의 **마침표를 지웠더니 그대로 초록이었다** —
 *    부분 문자열이라 접두사가 통과한다. 이 테스트는 문구가 정확히 같은지를 봐야
 *    의미가 있으므로, A 문서의 표를 파싱해서 **셀 값과 정확히 비교한다.**
 *
 * ⚠️ **문구를 고쳐야 하면 A 문서를 먼저 고친다.** 이 테스트가 빨개지면 그 순서를
 *    어긴 것이다. 문구가 없는 화면을 만들 때도 A 문서에 추가하고 `진행.md`에 남긴다.
 */
class PermissionCopyTest {

    /**
     * A 문서 03절의 표를 `위치 → 문구`로 읽는다.
     *
     * ⚠️ **`assumeTrue`로 건너뛰지 않는다.** 문서를 못 찾으면 이 테스트는 아무것도
     *    검증하지 않는데 결과는 초록이다 — (22)에서 겪은 거짓 초록과 같은 모양이다.
     *    못 찾으면 **실패한다.**
     */
    private val rows: Map<String, String> by lazy {
        val relative = "디자이너_업무/A_문구·버튼_스펙.md"
        var dir: File? = File("").absoluteFile
        var text: String? = null
        while (dir != null && text == null) {
            val candidate = File(dir, relative)
            if (candidate.exists()) text = candidate.readText()
            dir = dir.parentFile
        }
        val doc = text ?: throw AssertionError(
            "A 문서를 못 찾았다 ($relative). 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 문구 검증이 조용히 사라진다",
        )

        // `### 03 …`부터 다음 `### `까지.
        val section = Regex("^### 03 [^\\n]*\\n(.*?)(?=^### )", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
            .find(doc)?.groupValues?.get(1)
            ?: throw AssertionError("A 문서에서 `### 03` 절을 못 찾았다")

        val parsed = section.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") && !it.startsWith("|---") }
            .map { line -> line.trim('|').split("|").map(String::trim) }
            .filter { it.size >= 2 && it[0] != "위치" } // 표 헤더 줄 제외
            .associate { it[0] to it[1] }

        check(parsed.size >= 8) { "A 문서 03절 표를 제대로 못 읽었다 (${parsed.size}줄)" }
        parsed
    }

    /** `꽃을 …합니다. / 앨범 …없어요.` → (용도, 단서). 구분자는 A 문서의 ` / `다. */
    private fun split(key: String): Pair<String, String> {
        val cell = rows[key] ?: throw AssertionError("A 문서 03절에 `$key` 줄이 없다")
        val parts = cell.split(" / ")
        assertEquals("`$key` 셀이 `용도 / 단서` 두 조각이 아니다: $cell", 2, parts.size)
        return parts[0] to parts[1]
    }

    /**
     * ⚠️ **[PermissionIntroItem.allInSpec]으로 잰다** — 예선에서 화면에 안 그리는
     *    연락처 줄까지 포함이다. `all`로 재면 **뺀 줄의 문구가 검증에서 조용히
     *    사라진다**(되살릴 때 아무도 대조해 주지 않는 문구가 된다).
     */
    @Test
    fun 세_권한_문구가_A문서_그대로다() {
        val expected = mapOf(
            "카메라" to "카메라 `필수`",
            "위치" to "위치 `선택`",
            "연락처" to "연락처 `선택`",
        )
        PermissionIntroItem.allInSpec.forEach { item ->
            val (purpose, caveat) = split(expected.getValue(item.title))
            assertEquals("${item.title} 용도 문구가 A 문서와 다르다", purpose, item.purpose)
            assertEquals("${item.title} 단서 문구가 A 문서와 다르다", caveat, item.caveat)
        }
    }

    /**
     * 🔴 **연락처 줄을 화면에 그리지 않는다** (예선 범위).
     *
     * 목표 4에서 가짜 친구 목록과 `READ_CONTACTS` 선언을 지웠는데 **이 화면만 남아서**
     * `이미 가입한 지인을 친구로 연결합니다`라고 약속했다 — 선언이 없으니 눌러도
     * 아무 일이 없다. 실측(에뮬레이터 온보딩 2/2)으로 발견했다.
     *
     * 빨개지는 경우: 연락처를 [PermissionIntroItem.all]에 다시 넣었는데 매니페스트
     * 선언과 읽는 코드를 같이 살리지 않았을 때. **그때는 이 테스트를 지우는 것이
     * 아니라, 셋을 같이 되살리고 이 테스트를 반대로 뒤집는다.**
     */
    @Test
    fun 연락처_안내는_예선_빌드에_없다() {
        assertEquals(
            "연락처를 읽는 코드도 매니페스트 선언도 없는 빌드가 " +
                "`지인을 친구로 연결합니다`라고 약속하고 있다",
            listOf("카메라", "위치"),
            PermissionIntroItem.all.map { it.title },
        )
        // 문구 자체는 보관돼 있어야 한다 — 되살릴 때 새로 쓰지 않기 위해서다.
        assertEquals("연락처", PermissionIntroItem.CONTACTS.title)
    }

    /**
     * 🔴 **제목이 화면에 있는 카드 수를 센다.**
     *
     * A 문서 원문 제목은 `이 세 가지만…`인데 **숫자가 문장에 박혀 있다.** 연락처
     * 카드를 뺀 예선 빌드에서 그 문장을 그대로 쓰면 **화면에 없는 세 번째 카드를
     * 세는 제목**이 된다 — 실측(온보딩 2/2)에서 카드 2장 + 제목 `세 가지`였다.
     *
     * 빨개지는 경우: 제목을 문자열로 다시 박거나, [PermissionIntroItem.allInSpec]으로
     * 세거나, 카드를 뺐는데 제목을 안 고쳤을 때.
     */
    @Test
    fun 제목이_카드_수를_센다() {
        assertEquals(
            "카드 ${PermissionIntroItem.all.size}장인데 제목이 다른 수를 세고 있다",
            "이 두 가지만 허용하면 준비 끝!",
            PermissionIntroItem.title(PermissionIntroItem.all.size),
        )
        assertEquals("이 세 가지만 허용하면 준비 끝!", PermissionIntroItem.title(3))
    }

    /**
     * ⚠️ **문구가 없는 카드 수는 예외를 던진다.**
     *
     * 🔴 기본값으로 `세 가지`를 돌려주면 카드가 4장이 돼도 **조용히 틀린 제목**이
     *    나온다. A 문서에 줄을 추가하라는 신호가 코드에서 나야 한다.
     */
    @Test
    fun 문구_없는_카드_수는_거절한다() {
        for (n in listOf(0, 1, 4, 5)) {
            try {
                PermissionIntroItem.title(n)
                throw AssertionError("카드 ${n}장에 제목을 만들어 줬다 — 조용히 틀린 제목이 나온다")
            } catch (expected: IllegalArgumentException) {
                // 기대한 경로다.
            }
        }
    }

    /**
     * ⚠️ **화면이 요청하는 권한과 그리는 카드가 같은 집합인가.**
     *
     * 카드는 3장인데 요청은 2개(카메라·위치)였던 것이 이 사고의 모양이다.
     * 카드를 뺐으니 이제 **둘 다 2개**여야 한다.
     */
    @Test
    fun 그리는_카드와_요청하는_권한_수가_같다() {
        // 화면 코드가 요청하는 권한: CAMERA · ACCESS_COARSE/FINE_LOCATION
        // (위치 둘은 한 카드가 함께 요청한다 → 카드 기준으로 2장)
        assertEquals(2, PermissionIntroItem.all.size)
    }

    /**
     * 화면에 직접 박은 문구들.
     *
     * ⚠️ 이 목록은 **화면 코드에서 손으로 옮겨 온 것**이다. 화면에서 문구를 바꾸고
     *    여기를 안 고치면 잡히지 않는다. 그래서 아래 [문구_개수가_A문서와_맞는다]로
     *    "빠뜨린 줄이 없는지"를 따로 센다.
     */
    @Test
    fun 화면_문구가_A문서_그대로다() {
        // 🔴 **A 문서 원문은 `세 가지`(카드 3장 기준)다.** 화면이 그리는 제목은
        //    카드 수에 따라 갈리므로 아래 [제목이_카드_수를_센다]가 따로 잰다.
        assertEquals("이 세 가지만 허용하면 준비 끝!", rows["제목"])
        assertEquals(rows["제목"], PermissionIntroItem.title(3))
        assertEquals("허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요.", rows["설명"])
        assertEquals("허용하고 시작하기", rows["CTA (Primary)"])
        assertEquals("나중에 설정에서 바꿀 수 있어요", rows["하단"])
        assertEquals("권한 안내 (우측 `2/2`)", rows["헤더"])

        val (line1, line2) = split("안심 박스")
        assertEquals("촬영한 사진은 내 도감에만 저장됩니다.", line1)
        assertEquals("지도 공유는 매번 직접 선택해요.", line2)
    }

    /**
     * A 문서 03절이 요구하는 줄 수와 화면이 그리는 줄 수가 같은가.
     *
     * ⚠️ **권한 줄을 하나 빼먹어도 화면은 멀쩡해 보인다.** 연락처 칸이 없으면
     *    "번호는 암호화해 보관하며 저장하지 않아요"라는 신뢰 문구가 사라지는데
     *    (와이어프레임 주석 ③: 기획서 15장 보안 검토 항목),
     *    빈 자리가 생기지 않으니 육안으로는 알 수 없다.
     */
    @Test
    fun 문구_개수가_A문서와_맞는다() {
        val permissionRows = rows.keys.filter { it.contains("`필수`") || it.contains("`선택`") }
        assertEquals(
            "A 문서의 권한 줄 수와 대조 대상 줄 수가 다르다 (문서 $permissionRows)",
            permissionRows.size,
            // ⚠️ `all`이 아니라 `allInSpec`이다 — 예선에서 화면에 안 그리는 줄도
            //    문구는 대조돼야 한다(위 [세_권한_문구가_A문서_그대로다] 주석).
            PermissionIntroItem.allInSpec.size,
        )
    }

    /**
     * ⚠️ **`필수`는 카메라 하나뿐이다.** 위치를 필수로 표시하면 화면 03의 약속
     *    (`허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`)과 정면으로
     *    어긋나고, 위치를 거부한 사람은 앱을 못 쓴다고 읽는다.
     *
     * A 문서에서 `필수`가 붙은 줄을 읽어 온다 — 손으로 적으면 문서가 바뀌어도 안 잡힌다.
     */
    @Test
    fun 필수_표시가_A문서와_같다() {
        val requiredInSpec = rows.keys
            .filter { it.contains("`필수`") }
            .map { it.substringBefore(" `") }
        assertEquals(
            requiredInSpec,
            PermissionIntroItem.allInSpec.filter { it.isRequired }.map { it.title },
        )
    }

    /** 순서도 스펙이다 — 카메라 · 위치 · 연락처 (와이어프레임 ①②③ 번호와 같다). */
    @Test
    fun 순서가_A문서와_같다() {
        val orderInSpec = rows.keys
            .filter { it.contains("`필수`") || it.contains("`선택`") }
            .map { it.substringBefore(" `") }
        assertEquals(orderInSpec, PermissionIntroItem.allInSpec.map { it.title })
        // 화면에 그리는 것은 그 **앞 두 줄**이다 — 순서를 유지한 채 뒤에서 뺀다.
        assertEquals(orderInSpec.take(2), PermissionIntroItem.all.map { it.title })
    }
}
