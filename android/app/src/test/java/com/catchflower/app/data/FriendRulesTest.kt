package com.catchflower.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 19 친구 검색의 판정([FriendRules]).
 *
 * ## 이 테스트가 어떤 경우에 빨개지나
 *
 * - [MIN_QUERY]를 1로 낮추면 → `한_글자는_검색하지_않는다`
 * - `String.length`로 세면 → `이모지_한_글자도_짧다`
 * - `%`·`_`를 그냥 보내면 → `와일드카드를_사용자가_쥐지_못한다`
 * - 나를 결과에 남기면 → `내_닉네임은_결과에서_뺀다`
 * - 상대가 보낸 요청을 `요청 보냄`으로 그리면 → `상대가_보낸_요청은_내가_보낸_것이_아니다`
 */
class FriendRulesTest {

    @Test
    fun 한_글자는_검색하지_않는다() {
        assertTrue(FriendRules.tooShort("가"))
        assertTrue(FriendRules.tooShort(" 가 "))
        assertTrue(FriendRules.tooShort(""))
        assertFalse(FriendRules.tooShort("가나"))
    }

    /**
     * 🔴 `String.length`로 세면 `🌸`가 2가 되어 **한 글자로 전체 목록을 받는다.**
     *    한글은 둘 다 1이라 **한국어로만 테스트하면 이 구멍이 안 보인다.**
     */
    @Test
    fun 이모지_한_글자도_짧다() {
        assertEquals("전제가 깨지면 이 테스트가 아무것도 재지 않는다", 2, "🌸".length)
        assertTrue(FriendRules.tooShort("🌸"))
        assertFalse(FriendRules.tooShort("🌸🌸"))
    }

    /**
     * 🔴 `%%`는 두 글자라 [FriendRules.tooShort]를 **통과한다** — 즉 길이 제한만으로는
     *    전체 사용자 목록을 못 막는다. 그걸 막는 건 이스케이프다.
     */
    @Test
    fun 와일드카드를_사용자가_쥐지_못한다() {
        assertFalse("두 글자 제한은 이걸 못 막는다", FriendRules.tooShort("%%"))
        assertEquals("\\%\\%", FriendRules.sanitize("%%"))
        assertEquals("가\\_나", FriendRules.sanitize("가_나"))
        assertEquals("\\\\", FriendRules.sanitize("\\"))
    }

    /** ⚠️ `*`는 이스케이프할 수 없다(PostgREST가 `%`로 바꾼다) — 그래서 지운다. */
    @Test
    fun 값_구분자와_별표는_지운다() {
        assertEquals("가나", FriendRules.sanitize("*가,나()"))
        // 지운 결과가 짧아질 수 있다 — 그래서 서비스가 sanitize **뒤에** 다시 센다.
        assertTrue(FriendRules.tooShort(FriendRules.sanitize("*가*")))
    }

    @Test
    fun 제어문자를_보내지_않는다() {
        assertEquals("가나", FriendRules.sanitize("가\n나"))
        // 🔴 **NUL을 소스에 날바이트로 넣지 않는다** — `\u0000`으로 쓴다.
        //    전에는 진짜 NUL 한 바이트가 들어 있었다. 컴파일도 되고 이 파일의
        //    테스트 13개도 정상으로 돌았지만, **`grep`이 이 파일을 바이너리로 보고
        //    조용히 건너뛴다**(테스트 애노테이션을 grep으로 세면 13을 1로 셌다).
        //    ⚠️ 이 주석에 그 낱말을 **적지 않는다** — 적으면 grep이 이 파일의
        //       테스트를 하나 더 세서 숫자가 또 틀린다(고치다가 한 번 그랬다).
        //    테스트 개수를 grep으로 세는 이 저장소의 관행이 그래서 12개를 놓쳤다 —
        //    문서의 숫자가 틀리는 새로운 경로였다.
        assertEquals("가나", FriendRules.sanitize("가\u0000나"))
    }

    private val me = "me-uuid"
    private val other = "other-uuid"

    @Test
    fun 내_닉네임은_결과에서_뺀다() {
        val rows = FriendRules.merge(
            profiles = listOf(me to "나", other to "남"),
            edges = emptyList(),
            myUserId = me,
        )
        assertEquals(listOf(other), rows.map { it.id })
    }

    @Test
    fun 관계가_없으면_추가할_수_있다() {
        val rows = FriendRules.merge(listOf(other to "남"), emptyList(), me)
        assertEquals(FriendRules.State.NONE, rows.single().state)
    }

    @Test
    fun 수락된_관계는_이미_친구다() {
        val rows = FriendRules.merge(
            profiles = listOf(other to "남"),
            edges = listOf(FriendRules.Edge(other, me, FriendRules.ACCEPTED)),
            myUserId = me,
        )
        assertEquals(FriendRules.State.FRIEND, rows.single().state)
    }

    @Test
    fun 내가_보낸_요청은_요청_보냄이다() {
        val rows = FriendRules.merge(
            profiles = listOf(other to "남"),
            edges = listOf(FriendRules.Edge(me, other, FriendRules.PENDING)),
            myUserId = me,
        )
        assertEquals(FriendRules.State.REQUESTED, rows.single().state)
    }

    /**
     * 🔴 방향을 안 보면 **내가 보낸 것처럼** 말한다. 수락 화면이 없으므로(C-2)
     *    이 경우는 [FriendRules.State.NONE]으로 둔다.
     */
    @Test
    fun 상대가_보낸_요청은_내가_보낸_것이_아니다() {
        val rows = FriendRules.merge(
            profiles = listOf(other to "남"),
            edges = listOf(FriendRules.Edge(other, me, FriendRules.PENDING)),
            myUserId = me,
        )
        assertEquals(FriendRules.State.NONE, rows.single().state)
    }

    /** 🔴 차단은 **두 방향 모두** 숨긴다 — 어느 쪽이든 화면이 차단을 통보하게 된다. */
    @Test
    fun 차단된_사람은_양방향_모두_안_보인다() {
        val iBlocked = FriendRules.merge(
            listOf(other to "남"),
            listOf(FriendRules.Edge(me, other, FriendRules.BLOCKED)),
            me,
        )
        assertTrue(iBlocked.isEmpty())

        val theyBlocked = FriendRules.merge(
            listOf(other to "남"),
            listOf(FriendRules.Edge(other, me, FriendRules.BLOCKED)),
            me,
        )
        assertTrue(theyBlocked.isEmpty())
    }

    /**
     * ⚠️ 남들끼리의 관계가 섞여 들어오면(`friendships_read_involved`가 내 관계만 주지만
     * 그건 서버 정책이고 여기 판정은 아니다) **엉뚱한 줄에 상태가 붙는다.**
     */
    @Test
    fun 나와_무관한_관계는_상태를_바꾸지_않는다() {
        val rows = FriendRules.merge(
            profiles = listOf(other to "남"),
            edges = listOf(FriendRules.Edge("third", "fourth", FriendRules.ACCEPTED)),
            myUserId = me,
        )
        assertEquals(FriendRules.State.NONE, rows.single().state)
    }

    @Test
    fun 한_번에_받는_인원에_상한이_있다() {
        assertEquals(20, FriendRules.SEARCH_LIMIT)
        assertEquals(2, FriendRules.MIN_QUERY)
    }
}
