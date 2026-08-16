package com.catchflower.app.core

import com.catchflower.app.core.AccountDeletionRules.Phase
import com.catchflower.app.core.AccountDeletionRules.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 회원 탈퇴의 순서와 판정.
 *
 * ## 🔴 이 검사가 어떤 경우에 빨개지나
 *
 * | 사고 | 화면 증상 | 잡는 검사 |
 * |---|---|---|
 * | 실패한 뒤에도 기기를 지운다 | **없다 — 성공 화면과 똑같다.** 서버에 계정이 남고 다시 시도할 방법이 사라진다 | [실패하면_기기를_지우지_않는다] |
 * | `PROFILE`을 앞으로 옮긴다 | 없다. 중간에 끊기면 이름만 지워진 유령 계정 | [프로필은_맨_끝이다] |
 * | `DISCOVERIES`를 뒤로 옮긴다 | 없다. 주인 없는 댓글이 남는다 | [발견_기록이_맨_앞이다] |
 *
 * ⚠️ **이 파일은 순수 규칙만 잰다.** 실제로 그 순서로 요청이 나가는지는
 *    [com.catchflower.app.data.AccountDeletionServiceTest]가 잰다 — 규칙만 맞고
 *    서비스가 자기 순서를 갖고 있으면 여기는 초록이다.
 */
class AccountDeletionRulesTest {

    // ── 순서 ─────────────────────────────────────────────────────────

    /**
     * 🔴 순서를 문자열로 못 박는다. 단계가 늘거나 순서가 바뀌면 여기서 걸린다 —
     * 그 두 사고 다 화면에 증상이 없다.
     */
    @Test
    fun 지우는_순서를_못_박는다() {
        assertEquals(
            listOf("DISCOVERIES", "LIKES", "FRIENDSHIPS", "BLOCKS", "PROFILE"),
            Step.entries.map { it.name },
        )
    }

    @Test
    fun 발견_기록이_맨_앞이다() {
        assertEquals(Step.DISCOVERIES, Step.entries.first())
    }

    @Test
    fun 프로필은_맨_끝이다() {
        assertEquals(Step.PROFILE, Step.entries.last())
    }

    /** 로그에 쓰는 이름이다. 비어 있으면 실패 로그가 `탈퇴  실패`가 된다. */
    @Test
    fun 단계마다_사람이_읽는_이름이_있다() {
        Step.entries.forEach { assertTrue("${it.name}에 what이 없다", it.what.isNotBlank()) }
        // 같은 이름이 둘이면 로그로 어디서 끊겼는지 구별할 수 없다.
        assertEquals(Step.entries.size, Step.entries.map { it.what }.toSet().size)
    }

    // ── 기기를 지워도 되는가 ─────────────────────────────────────────

    /**
     * 🔴 **이 기능에서 가장 나쁜 결과를 막는 한 줄이다.** 실패한 뒤에 기기를 지우면
     * 사용자는 탈퇴됐다고 믿고, 서버에는 다 남고, 토큰이 없어서 **다시 시도할 수도
     * 없다.** 그런데 화면은 성공과 완전히 같다.
     */
    @Test
    fun 실패하면_기기를_지우지_않는다() {
        Step.entries.forEach { step ->
            assertFalse("${step.name}에서 끊겼는데 기기를 지우려 한다", AccountDeletionRules.mayWipeDevice(step))
        }
    }

    @Test
    fun 전부_성공하면_기기를_지운다() {
        assertTrue(AccountDeletionRules.mayWipeDevice(null))
    }

    // ── 상태 ─────────────────────────────────────────────────────────

    /**
     * ⚠️ 상태가 넷이다. 다섯 번째를 넣으면 화면의 `when`이 컴파일 에러로 잡지만,
     *    **문구는 안 잡힌다** — A 문서 3절 ⑪ 표에 칸을 먼저 넣어야 한다.
     */
    @Test
    fun 상태는_넷이다() {
        val phases = listOf<Phase>(Phase.Confirm, Phase.Running, Phase.Done, Phase.Failed(null))
        assertEquals(4, phases.map { it::class }.toSet().size)
    }

    /** 실패 상태는 **어디서 끊겼는지 모를 수도 있다**(네트워크가 아예 안 됐을 때). */
    @Test
    fun 실패는_단계를_모를_수도_있다() {
        assertEquals(Phase.Failed(null), Phase.Failed(null))
        assertEquals(Phase.Failed(Step.LIKES), Phase.Failed(Step.LIKES))
        assertFalse(Phase.Failed(null) == Phase.Failed(Step.LIKES))
    }

    /**
     * 🔴 **재시도는 처음부터다.** false로 바꾸면 "어디까지 했는지"를 기기에 저장해야
     * 하고, 그 상태로 앱을 쓰면 화면이 거짓말을 한다([AccountDeletionRules.RETRY_FROM_START]).
     *
     * ⚠️ `const val`은 컴파일 때 인라인되므로 이 단정은 **상수를 읽는 것이 아니라
     *    문서를 읽는 것에 가깝다.** 실제로 처음부터 도는지는 서비스 검사가
     *    요청 목록으로 잰다(재시도 두 번 = 요청 10개).
     */
    @Test
    fun 재시도는_처음부터_한다() {
        assertTrue(AccountDeletionRules.RETRY_FROM_START)
    }
}
