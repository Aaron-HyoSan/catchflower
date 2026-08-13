package com.catchflower.app.data

/**
 * 화면 19 친구 검색의 **판단**. HTTP를 모른다 — 그래서 JVM에서 전부 잰다
 * ([com.catchflower.app.data.FriendRulesTest]).
 *
 * 2026-08-13에 `검색`·`친구 추가`를 실제 동작으로 바꾸면서 생겼다.
 */
object FriendRules {

    /**
     * 검색에 필요한 **최소 글자 수**.
     *
     * 🔴 **사용자 편의가 아니라 서버 보호다.** `public_profiles` 뷰는 `security_invoker`가
     *    꺼져 있어서 **RLS를 우회해 공개 컬럼을 내보낸다**(0001 7-2 · 그게 그 뷰의 목적이다).
     *    한 글자로 `ilike.*ㄱ*`을 던지면 **사실상 전체 사용자 목록**이 내려온다.
     *    A 문서 3절 ③에 적었듯 화면 문구는 이 이유를 말하지 않는다 —
     *    사용자가 할 일(`두 글자 이상 입력해 주세요`)만 말한다.
     *
     * ⚠️ 이 값을 1로 낮추는 것은 **정책 변경**이다. 뷰를 먼저 좁혀야 한다.
     */
    const val MIN_QUERY = 2

    /** 한 번에 받을 최대 인원. 목록이 길어지는 것 자체가 위 문제의 다른 얼굴이다. */
    const val SEARCH_LIMIT = 20

    /**
     * 글자 수를 **코드 포인트**로 센다.
     *
     * 🔴 `String.length`를 쓰면 이모지 닉네임 한 글자가 2가 되어 `🌸`만 넣고도
     *    검색이 돌아간다. 한글은 둘 다 1이라 **한국어로만 테스트하면 안 보인다**
     *    ([ReactionService.charLength]와 같은 함정).
     */
    fun tooShort(query: String): Boolean {
        val q = query.trim()
        return q.codePointCount(0, q.length) < MIN_QUERY
    }

    /**
     * PostgREST `ilike` 값으로 안전한 문자열을 만든다.
     *
     * 🔴 **`%`·`_`를 그대로 보내면 사용자가 와일드카드를 쥔다.** `%` 한 글자는
     *    [tooShort]를 통과하지 못하지만 `%%`는 통과하고, 그건 **전체 사용자 목록**이다.
     *    (위 [MIN_QUERY] 주석의 그 위험을 두 글자 제한만으로는 못 막는다.)
     *    Postgres `like`의 기본 이스케이프가 `\`라서 앞에 붙이면 **글자 그대로** 찾는다 —
     *    닉네임에 `_`를 쓰는 사람을 못 찾게 되는 쪽이 더 흔한 손해다.
     *
     * ⚠️ `*`는 **이스케이프할 수 없다.** PostgREST가 값을 읽을 때 `*`를 `%`로 바꾸므로
     *    `\*`도 `\%`가 되어 버린다 — 그래서 지운다.
     * ⚠️ `,`·`(`·`)`는 PostgREST의 값 구분자라 필터가 통째로 깨진다(400).
     */
    fun sanitize(query: String): String {
        val out = StringBuilder()
        for (ch in query.trim()) {
            when (ch) {
                '*', ',', '(', ')' -> Unit
                '\\', '%', '_' -> out.append('\\').append(ch)
                else -> if (!ch.isISOControl()) out.append(ch)
            }
        }
        return out.toString()
    }

    /**
     * 검색 결과 한 사람의 **버튼 자리 상태**.
     *
     * ⚠️ 라벨은 화면이 고른다(A 문서 3절 ③) — 여기서 문구를 만들지 않는다.
     */
    enum class State {
        /** `추가`를 누를 수 있다. */
        NONE,

        /** `요청 보냄` — 내가 이미 보냈고 상대가 아직 수락하지 않았다. */
        REQUESTED,

        /** `이미 친구예요` — `state = accepted`. */
        FRIEND,
    }

    /** 화면이 그리는 한 줄. */
    data class Found(val id: String, val nickname: String, val state: State)

    /** `friendships` 한 행. 서버가 준 것만 담는다. */
    data class Edge(val requesterId: String, val addresseeId: String, val state: String)

    /**
     * 공개 프로필 목록과 내 친구 관계를 합쳐 화면 줄을 만든다.
     *
     * 🔴 **나를 목록에서 뺀다.** 안 빼면 자기 닉네임을 검색한 사용자에게
     *    `추가` 버튼이 보이고, 눌러도 서버가 `friendships_no_self`로 막는다 —
     *    **누를 수 있는데 실패하는 버튼**이다.
     *
     * 🔴 **`blocked`인 사람은 아예 안 보여준다.** 두 방향 모두 그렇게 한다:
     *    내가 차단했다면 목록에 뜨는 것이 이상하고, 상대가 나를 차단했다면
     *    `요청 보냄`이든 `추가`든 **차단 사실을 알려 주는 화면**이 된다.
     *    (그 결과 `그 닉네임을 가진 사람이 없어요`가 뜬다 — 참이 아니지만
     *    차단을 통보하지 않는 쪽을 골랐다.)
     *
     * ⚠️ **[State.REQUESTED]는 내가 보낸 것만이다.** 상대가 나에게 보낸 `pending`을
     *    `요청 보냄`으로 그리면 **내가 보낸 것처럼** 말한다. 수락 화면이 아직 없으므로
     *    (C-2) 그 경우는 [State.NONE]으로 둔다 — `추가`를 누르면 반대 방향 행이
     *    새로 생기고, 그건 상대가 수락할 수 있는 요청이다(키가 `(requester, addressee)`라
     *    두 방향이 공존한다).
     */
    fun merge(profiles: List<Pair<String, String>>, edges: List<Edge>, myUserId: String): List<Found> {
        val out = mutableListOf<Found>()
        for ((id, nickname) in profiles) {
            if (id == myUserId) continue
            val mine = edges.filter { it.requesterId == id || it.addresseeId == id }
            if (mine.any { it.state == BLOCKED }) continue
            val state = when {
                mine.any { it.state == ACCEPTED } -> State.FRIEND
                mine.any { it.state == PENDING && it.requesterId == myUserId } -> State.REQUESTED
                else -> State.NONE
            }
            out.add(Found(id = id, nickname = nickname, state = state))
        }
        return out
    }

    // ── 0001의 `friend_state` enum 값. 혼자 바꾸지 않는다(공유계약 6절). ──
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val BLOCKED = "blocked"
}
