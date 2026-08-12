package com.catchflower.app.ui.place

import com.catchflower.app.core.GamePolicy
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 16의 판단 — **값이 없는 것을 0이나 빈 문자열로 그리지 않는가.**
 *
 * 🔴 이 화면의 위험한 계산은 전부 그 방향이다. A 문서 3절 `화면 16에서 값이 없거나
 *    실패한 칸`(2026-08-11)이 네 칸을 정해 두었고, 그 판정이 Composable 안에 있으면
 *    **아무 층에서도 검증되지 않는다** — 화면은 `좋아요 0 · 댓글 0`을 완벽히 정상으로 그린다.
 */
class RecordRulesTest {

    private val me = "5bf714f9-b172-4636-a878-bf8efbc26fb7"
    private val other = "3070caaa-0d53-4b2b-a71f-e5e230c6cda7"

    // ── 반응 줄 ────────────────────────────────────────────────────────

    /** 아는 경우: `좋아요 12 · 댓글 3` (A 문서 16번). */
    @Test
    fun 숫자를_알면_그대로_말한다() {
        assertEquals("좋아요 12", RecordRules.likeLabel(reactionsKnown = true, likeCount = 12))
        assertEquals("댓글 3", RecordRules.commentLabel(reactionsKnown = true, commentCount = 3))
    }

    /**
     * 🔴 **못 받은 숫자를 `0`으로 그리지 않는다.**
     *
     * 서버 함수 `discovery_reactions`는 **볼 권한이 없는 기록에도 오류가 아니라 0을
     * 준다**(0007 4절 — 집계라서 호출자의 RLS를 그대로 탄다). 즉 `0`은
     * "아무도 안 눌렀다"·"볼 수 없는 기록이다"·"조회에 실패했다"가 **한 값으로 겹친다.**
     * 12명이 좋아요한 기록에 `좋아요 0`이 뜨고, **화면은 완벽히 정상으로 보인다.**
     */
    @Test
    fun 숫자를_모르면_줄_전체를_뺀다() {
        assertNull(RecordRules.likeLabel(reactionsKnown = false, likeCount = 0))
        assertNull(RecordRules.commentLabel(reactionsKnown = false, commentCount = 0))
        // 호출처가 실수로 예전 값을 넘겨도 마찬가지다 — 모른다가 우선이다.
        assertNull(RecordRules.likeLabel(reactionsKnown = false, likeCount = 12))
    }

    /**
     * ⚠️ **정말 0인 경우는 `0`을 그린다.** 그건 사실이고, 위 단정과 헷갈리기 쉽다 —
     *    "0이면 뺀다"로 구현하면 아무도 안 누른 기록의 좋아요 버튼이 사라진다.
     */
    @Test
    fun 정말_영일_때는_영을_말한다() {
        assertEquals("좋아요 0", RecordRules.likeLabel(reactionsKnown = true, likeCount = 0))
        assertEquals("댓글 0", RecordRules.commentLabel(reactionsKnown = true, commentCount = 0))
    }

    // ── 작성자 줄 ──────────────────────────────────────────────────────

    /** 둘 다 있으면 `연남동 · 2시간 전` (A 문서 16번). */
    @Test
    fun 지역과_시각이_있으면_가운뎃점으로_잇는다() {
        assertEquals("연남동 · 2시간 전", RecordRules.authorMeta("연남동", "2시간 전"))
    }

    /**
     * 🔴 **가운뎃점을 미리 이어 두지 않았다.**
     *
     * 한쪽이 없을 때 ` · 2시간 전`처럼 **점으로 시작하는 줄**이 남으면 화면에서는
     * 오타로 보이고, 빈 지역을 "이름 없는 동네"로 읽게 만든다.
     */
    @Test
    fun 한쪽만_있으면_점을_붙이지_않는다() {
        assertEquals("2시간 전", RecordRules.authorMeta(null, "2시간 전"))
        assertEquals("연남동", RecordRules.authorMeta("연남동", null))
        assertTrue(
            "점으로 시작하는 줄이 만들어졌다",
            RecordRules.authorMeta(null, "2시간 전")!!.first() != '·',
        )
    }

    /** 공백만 있는 값은 없는 것과 같다 — 서버 `place_name`이 `""`로 올 수 있다. */
    @Test
    fun 공백만_있는_값은_없는_것으로_본다() {
        assertEquals("2시간 전", RecordRules.authorMeta("   ", "2시간 전"))
        assertNull(RecordRules.authorMeta("  ", "\t"))
    }

    /** A 문서 3절 `작성자 지역`: **둘 다 없으면 줄을 뺀다.** */
    @Test
    fun 둘_다_없으면_줄을_뺀다() {
        assertNull(RecordRules.authorMeta(null, null))
    }

    // ── 이름 칸 ────────────────────────────────────────────────────────

    @Test
    fun 닉네임이_있으면_그대로_쓴다() {
        assertEquals("꽃보다효산", RecordRules.authorName("꽃보다효산", namesLoaded = true))
        // 조회 성공 여부와 무관하게, 이름이 손에 있으면 쓴다.
        assertEquals("꽃보다효산", RecordRules.authorName("꽃보다효산", namesLoaded = false))
    }

    /**
     * 조회가 **성공했는데** 이름이 없다 → 탈퇴다. `public_profiles`는
     * `deleted_at is null`인 사용자만 담으므로(0001 7-2) 이름을 **영구히** 못 받는다.
     */
    @Test
    fun 조회_성공_후_이름이_없으면_탈퇴다() {
        assertEquals("탈퇴한 사용자예요", RecordRules.authorName(null, namesLoaded = true))
        assertEquals("탈퇴한 사용자예요", RecordRules.authorName("  ", namesLoaded = true))
    }

    /**
     * 🔴 **조회 실패에는 `탈퇴한 사용자예요`를 쓰지 않는다.**
     *
     * 다시 시도하면 이름이 오므로, 여기에 그 문구를 쓰면 **살아 있는 사람을 탈퇴로
     * 표시하고 그대로 굳는다.** 이름 칸을 비우고 본문은 그린다 —
     * 댓글이 사라지는 것보다 낫다.
     */
    @Test
    fun 조회_실패에는_탈퇴_문구를_쓰지_않는다() {
        assertNull(RecordRules.authorName(null, namesLoaded = false))
    }

    // ── 댓글 입력 ──────────────────────────────────────────────────────

    @Test
    fun 상한은_서버_제약과_같은_출처다() {
        assertEquals(GamePolicy.COMMENT_MAX_LENGTH, RecordRules.COMMENT_MAX)
    }

    /**
     * 🔴 **길이를 `String.length`로 세지 않는다.**
     *
     * 서버 제약은 `char_length(body)`(코드포인트)이고 코틀린 `length`는 UTF-16이다 —
     * 이모지 한 개가 2로 세어져 **화면은 통과시키는데 서버가 거절한다.**
     */
    @Test
    fun 이모지를_한_글자로_센다() {
        val emoji = "🌸"
        assertEquals("전제가 깨졌다 — 표본이 서로게이트 쌍이 아니다", 2, emoji.length)
        assertEquals(1, RecordRules.commentLength(emoji))
        // ⚠️ `"꽃$emoji핌"`으로 쓰면 안 된다 — 한글도 식별자 글자라서 Kotlin이
        //    `$emoji핌`을 **하나의 이름**으로 읽고 컴파일이 깨진다. 중괄호가 필수다.
        assertEquals(3, RecordRules.commentLength("꽃${emoji}핌"))
    }

    /**
     * 🔴 **서로게이트 쌍을 반토막 내지 않는다.**
     *
     * `take(200)`으로 자르면 상한 자리에 걸린 이모지가 절반만 남아 **깨진 글자**가
     * 서버로 간다. `offsetByCodePoints`로 자른다.
     */
    @Test
    fun 상한에서_이모지를_쪼개지_않는다() {
        val body = "🌸".repeat(RecordRules.COMMENT_MAX + 5)
        val cut = RecordRules.sanitizeComment(body)
        assertEquals(RecordRules.COMMENT_MAX, RecordRules.commentLength(cut))
        // 쪼개지지 않았다면 UTF-16 길이가 정확히 두 배다.
        assertEquals(RecordRules.COMMENT_MAX * 2, cut.length)
        assertTrue("서로게이트 쌍이 반토막 났다", !cut.last().isHighSurrogate())
    }

    /** 상한 이하는 손대지 않는다 — 통과시키는 값을 바꾸면 타이핑이 튄다. */
    @Test
    fun 상한_이하는_그대로_통과시킨다() {
        val body = "가".repeat(RecordRules.COMMENT_MAX)
        assertEquals(body, RecordRules.sanitizeComment(body))
    }

    /**
     * ⚠️ **공백을 압축하지 않는다.** 타이핑 중 뒤 공백을 지우면 다음 글자를 이어 쓸 수
     *    없다. 줄바꿈도 **살린다** — 댓글은 여러 줄이 정상이다(화면 13의 한 줄 설명과 다르다).
     */
    @Test
    fun 공백과_줄바꿈을_보존한다() {
        assertEquals("안녕 ", RecordRules.sanitizeComment("안녕 "))
        assertEquals("첫 줄\n둘째 줄", RecordRules.sanitizeComment("첫 줄\n둘째 줄"))
    }

    /**
     * `등록` 버튼. 공백만 있는 입력은 보내지 않는다 —
     * [com.catchflower.app.data.ReactionService]가 `trim` 후 거절하는데,
     * **거기까지 가면 사용자는 왕복을 기다린 뒤 아무 일도 안 일어난 것을 본다.**
     */
    @Test
    fun 공백만_있는_입력은_보낼_수_없다() {
        assertTrue(!RecordRules.canSubmit(""))
        assertTrue(!RecordRules.canSubmit("   "))
        assertTrue(!RecordRules.canSubmit("\n\t "))
        assertTrue(RecordRules.canSubmit("좋네요"))
    }

    /** 상한을 넘긴 입력도 보낼 수 없다(sanitize를 건너뛰고 붙여넣은 경우). */
    @Test
    fun 상한을_넘긴_입력은_보낼_수_없다() {
        assertTrue(!RecordRules.canSubmit("가".repeat(RecordRules.COMMENT_MAX + 1)))
        assertTrue(RecordRules.canSubmit("가".repeat(RecordRules.COMMENT_MAX)))
    }

    // ── 댓글 시각 ──────────────────────────────────────────────────────

    /**
     * 🔴 **`CommentRow.createdAt`은 ISO 문자열이다** — `discoveries`와 달리 그 층이
     *    서버 값을 그대로 담아 온다. 문자열을 그냥 그리면 댓글 옆에
     *    `2026-08-12T04:31:07.221Z`가 붙는다.
     */
    @Test
    fun ISO_문자열을_상대_시각으로_바꾼다() {
        val now = Instant.parse("2026-08-12T04:00:00Z").toEpochMilli()
        val fortyMinutesAgo = Instant.ofEpochMilli(now - TimeUnit.MINUTES.toMillis(40)).toString()
        assertEquals("40분 전", RecordRules.commentTime(fortyMinutesAgo, now))
    }

    /**
     * 🔴 **파싱 실패에 현재 시각을 넣지 않는다.**
     *
     * `now`로 대체하면 서버가 형식을 바꾼 날 **모든 댓글이 `1분 전`**이 되고,
     * 그건 완벽히 정상으로 보인다. `null`을 주고 화면이 그 칸을 뺀다.
     */
    @Test
    fun 파싱_실패에_현재_시각을_넣지_않는다() {
        val now = Instant.parse("2026-08-12T04:00:00Z").toEpochMilli()
        assertNull(RecordRules.commentTime("2026-08-12 04:00:00+00", now))
        assertNull(RecordRules.commentTime("", now))
        assertNull(RecordRules.commentTime("null", now))
    }

    // ── 댓글 삭제 (C-9 · A 문서 3절 화면 16 댓글 삭제) ──────────────────

    /** C-9의 두 갈래. 서버 `delete_comment` 본문이 세는 것과 같은 둘이다. */
    @Test
    fun 내_댓글과_내_사진의_댓글만_지울_수_있다() {
        // 내가 쓴 댓글 (남의 기록에 달았다)
        assertTrue(
            RecordRules.canDeleteComment(
                commentUserId = me, recordOwnerId = other, myUserId = me,
            ),
        )
        // 내 사진의 기록에 남이 쓴 댓글
        assertTrue(
            RecordRules.canDeleteComment(
                commentUserId = other, recordOwnerId = me, myUserId = me,
            ),
        )
    }

    /**
     * 🔴 **남의 기록에 달린 남의 댓글에는 그리지 않는다.**
     *
     * 그려 두고 누를 때 막으면 `댓글을 지우지 못했어요`가 뜨는 **누를 수 있는데 실패하는
     * 버튼**이 된다 — 이 저장소가 이미 세 번 만든 `죽은 버튼`이다. 화면 16은 대부분
     * 남의 기록이므로 그 상태면 **거의 모든 댓글에 죽은 삭제 버튼이 붙는다.**
     */
    @Test
    fun 남의_기록의_남의_댓글에는_안_그린다() {
        assertFalse(
            RecordRules.canDeleteComment(
                commentUserId = other, recordOwnerId = other, myUserId = me,
            ),
        )
    }

    /**
     * 🔴 **내 uuid가 없을 때 아무것도 지울 수 없다.**
     *
     * 익명 로그인은 네트워크라서 첫 프레임에 uuid가 없을 수 있다
     * (`DiscoveryRepository.userId` 주석 — 기기 로컬 uuid로 시작해 갈아탄다).
     * 이 비교는 **양쪽이 다 비면 참이 되는 모양**이라서, 빈 값을 안 끊으면
     * `userId`를 못 받은 댓글 전부에 삭제 버튼이 붙는다.
     *
     * ⚠️ 빈 문자열만이 아니라 공백도 끊는다 — `isBlank`로 세는 이유다.
     */
    @Test
    fun 내_uuid가_없으면_아무것도_못_지운다() {
        assertFalse(RecordRules.canDeleteComment("", "", ""))
        assertFalse(RecordRules.canDeleteComment(" ", " ", " "))
        assertFalse(RecordRules.canDeleteComment(commentUserId = "", recordOwnerId = "", myUserId = me))
    }

    /**
     * 🔴 **삭제 실패 두 갈래가 다른 문구를 쓴다.**
     *
     * 서버가 거절한 것(HTTP 200 + 본문 `false` = `DENIED`)은 권한 없음·없는 id·미로그인이
     * **일부러 한 값으로 묶여** 있고, 그중 몇은 **다시 시도해도 결과가 같다.** 거기에
     * `잠시 후 다시 시도해 주세요`를 쓰면 **틀린 안내**가 된다(화면 01
     * `manual_linking_disabled`에서 같은 판단을 했다). 네트워크 실패는 반대다.
     *
     * ⚠️ 이 판정을 화면에서 `if (code == 200)`으로 쓰면 **어느 층에서도 검증되지 않고**,
     *    틀렸을 때 증상은 문구 하나가 바뀌는 것뿐이라 아무도 못 본다.
     */
    @Test
    fun 거절과_네트워크_실패의_문구가_다르다() {
        assertFalse(
            "서버 거절에 `잠시 후 다시 시도해 주세요`를 쓰면 틀린 안내다",
            RecordRules.deleteToastIsNetwork(com.catchflower.app.data.ReactionService.DENIED),
        )
        // 전송 실패(HTTP 0) · 401 · 5xx는 pgCode가 없다 — 정말 다시 시도하면 된다.
        assertTrue(RecordRules.deleteToastIsNetwork(""))
        // 형식이 바뀐 사고도 네트워크 문구다 — **거절이 아니다.** 다시 시도하면 될 수도
        // 있고, 무엇보다 "지울 수 없는 댓글"이라고 단정할 근거가 없다.
        assertTrue(RecordRules.deleteToastIsNetwork(com.catchflower.app.data.ReactionService.PARSE_FAILED))
    }
}
