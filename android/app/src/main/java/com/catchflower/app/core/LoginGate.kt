package com.catchflower.app.core

/**
 * **무엇을 로그인 없이 할 수 있는가**를 한 곳에서 판정한다.
 *
 * ## 오너 결정 (2026-08-14 · 공유계약 3절)
 *
 * > `들어와서 보는건 비로그인 가능 액션하려면 로그인 필요`
 * > `도감등록 - 비로그인은 판별 디바이스당 2회 · 여기 판별은 성공이라고 하면 될듯하다`
 *
 * | 행동 | 비로그인 |
 * |---|---|
 * | 도감·지도·랭킹·남의 기록 **열람** | ✅ 제한 없음 ([GatedAction]에 아예 없다) |
 * | 촬영 → 판별 → 도감 등록 → 지도 공유 | [GamePolicy.ANONYMOUS_IDENTIFY_LIMIT]회까지 |
 * | 댓글 · 좋아요 · 신고 · **친구 요청** · **닉네임 변경** | ❌ 0회 |
 *
 * 기준선은 **`내 기록을 만드는 것`과 `남의 것에 관여하는 것`**이다.
 *
 * ## 🔴 2026-08-16: 목록에 **없어서** 열려 있던 것 두 개를 넣었다
 *
 * 오너가 실기기에서 `마이프로필이나 설정을 바꿀때면 로그인이 없어도되네?`를 발견했다.
 * 맞았다 — [GatedAction]에 **아예 없는 행동은 게이트를 지나지 않는다.** 즉 비로그인이
 * `users.nickname`을 바꾸고(랭킹·댓글에서 남에게 보이는 이름) `friendships`에 요청을
 * 넣을 수 있었다.
 *
 * 🔴 **아래 `when`의 exhaustive 검사가 이걸 못 잡는다.** 그것은 "enum에 있는데 안 다룬
 * 것"을 잡고, 여기서 일어난 일은 **"enum에 넣지도 않은 것"**이다 — 없는 것은
 * 컴파일러도 grep도 못 찾는다(`구현현황_AOS.md` (35) ①과 같은 층). 그래서
 * `LoginGateTest.게이트가_걸리는_행동은_여덟_개다`와
 * `LoginGateTest.선언된_행동은_전부_부르는_곳이_있다`를 짝으로 둔다.
 *
 * ⚠️ **지역 선택(화면 02)은 일부러 안 넣었다.** 온보딩이 **로그인 전에** `users.dong_code`를
 *    쓰는 흐름이라 게이트를 걸면 첫 실행이 막힌다(오너 결정 2026-08-16: 친구 요청과
 *    닉네임만). 대신 남은 위험을 적어 둔다 — 익명으로 지역을 바꾸면 6개월 규칙
 *    (`users.region_changed_at`)이 그 자리에서 소진되고, 카카오 연결은 uuid를 유지하므로
 *    **로그인한 뒤에도 그대로 물려받는다.**
 *
 * ## 🔴 왜 화면이 아니라 여기서 판정하는가
 *
 * 이 판정은 **어느 쪽으로 틀려도 화면에 증상이 없다.** 너무 빡세면 "3번째부터 안 돼요"가
 * 되고(사용자는 고장으로 읽는다), 너무 느슨하면 게이트가 아예 없는 것과 같은데
 * **둘 다 화면은 정상으로 보인다.** 화면마다 `if (linked)`를 흩뿌리면 그 판정이
 * 검증 밖에 남는다 — 실제로 죽은 버튼 14개가 그 모양으로 생겼다.
 *
 * 그래서 **순수 함수 하나**로 두고 JVM 테스트로 고정한다. 여기는 `Context`도
 * 저장소도 모른다 — 값만 받는다.
 *
 * ## ⚠️ `core`는 `recognizer`를 import하지 않는다
 *
 * `IdentifyOutcome`을 여기서 받으면 순환 의존이 된다(`recognizer` → `core`).
 * 그래서 [requiresLogin]은 **Int/Boolean만** 받고, `IdentifyOutcome` → Boolean 변환은
 * `recognizer`의 `countsAsSuccess`가 한다. 두 곳 다 exhaustive/테스트로 묶여 있다.
 *
 * ## ⚠️ 이게 **막지 못하는 것**
 *
 * 카운터는 로컬이라 **재설치하면 0**이다. 하드웨어 식별자는 쓰지 않는다
 * ([GamePolicy.ANONYMOUS_IDENTIFY_LIMIT] 주석). 즉 이 게이트는 **로그인 유도**이고
 * 어뷰징 차단·API 쿼터 방어가 아니다. 쿼터를 지켜야 하면 서버에서 세야 한다.
 */
object LoginGate {

    /**
     * 로그인이 걸리는 행동. **열람은 여기 없다** — 없는 것이 곧 "제한 없음"이다.
     *
     * ⚠️ 새 행동을 넣을 때는 [requiresLogin]의 `when`이 컴파일 에러로 잡는다.
     *    `else ->`를 넣지 않은 이유가 그것이다 — 새 액션이 조용히 "허용"으로
     *    떨어지면 게이트에 구멍이 생기고, 그 구멍은 화면에 안 보인다.
     */
    enum class GatedAction {
        /** 촬영 → 판별 시작. 성공 횟수가 남아 있으면 비로그인도 된다. */
        IDENTIFY,

        /** 도감 등록(화면 10·11). 판별에 딸린 마지막 단계라 [IDENTIFY]와 같은 규칙이다. */
        REGISTER,

        /**
         * 지도 공유(화면 13).
         *
         * ⚠️ **등록 흐름 안에 넣었다.** 여기서 끊으면 "등록은 됐는데 공유 화면에서
         *    막힌다" — 이미 찍고 확정한 뒤라 사용자는 앱이 고장난 것으로 읽는다.
         */
        SHARE,

        /** 댓글(화면 16). 남의 기록에 관여하는 행위 → **비로그인 0회**. */
        COMMENT,

        /** 좋아요(화면 16·15). **비로그인 0회**. */
        LIKE,

        /** 신고(화면 16). **비로그인 0회**. */
        REPORT,

        /**
         * 친구 요청(화면 19 검색 · 화면 16 작성자 줄). **비로그인 0회**.
         *
         * 🔴 **남의 알림함에 내가 남는 행위다** — 댓글·좋아요와 같은 급이다. 받은 쪽은
         *    `꽃친구a1b2`(서버 트리거가 익명에게 자동으로 준 이름)에게서 요청을 받고,
         *    누군지 확인할 방법이 없다.
         *
         * ⚠️ 화면 16의 `친구 추가` 버튼은 **로그인 전에도 그려진다.**
         *    [com.catchflower.app.ui.place.RecordRules.canAddFriend]가 보는 `myUserId`는
         *    익명 uuid라 **비어 있지 않다** — 익명 세션이 생기기 전(2026-08-14 이전)에
         *    맞았던 주석이 조용히 거짓이 된 자리다.
         */
        FRIEND_REQUEST,

        /**
         * 닉네임 변경(화면 20-1). **비로그인 0회** (오너 결정 2026-08-16).
         *
         * 🔴 **내 행을 고치는 것인데 0회인 이유**는 그 값이 내 화면에만 있는 게 아니기
         *    때문이다 — `users.nickname`은 랭킹·댓글·친구 검색에서 **남에게 보이는
         *    이름**이다. 익명은 서버 트리거가 준 `꽃친구XXXX`를 그대로 쓰게 둔다.
         *
         * ⚠️ **막는 자리는 `저장`이 아니라 `프로필 수정` 버튼**이다. 저장에서 막으면
         *    다 쓴 이름을 버리게 된다([com.catchflower.app.ui.my.ProfileEditViewModel]
         *    `save`가 실패에도 화면을 안 닫는 것과 같은 판단).
         */
        PROFILE_EDIT,
    }

    /**
     * 이 행동에 로그인이 필요한가.
     *
     * @param kakaoLinked 카카오 계정이 이 익명 uuid에 **연결됐는가**.
     *   ⚠️ "세션이 있는가"가 아니다 — 익명 세션은 항상 있다(서버 읽기가 전부
     *   `auth.uid()` RLS를 타므로 세션을 없앨 수 없다). 연결 여부만이 로그인 여부다.
     * @param identifyCount 지금까지 **성공한** 판별 횟수(로컬 누계).
     * @return true면 [GatedAction]을 수행하기 전에 로그인 시트를 띄운다.
     */
    fun requiresLogin(
        action: GatedAction,
        kakaoLinked: Boolean,
        identifyCount: Int,
    ): Boolean {
        // 연결됐으면 전부 열린다. 횟수는 비로그인에만 있는 개념이다.
        if (kakaoLinked) return false

        return when (action) {
            GatedAction.IDENTIFY,
            GatedAction.REGISTER,
            GatedAction.SHARE,
            -> identifyCount >= GamePolicy.ANONYMOUS_IDENTIFY_LIMIT

            GatedAction.COMMENT,
            GatedAction.LIKE,
            GatedAction.REPORT,
            GatedAction.FRIEND_REQUEST,
            GatedAction.PROFILE_EDIT,
            -> true
        }
    }

    /**
     * 비로그인으로 몇 번 더 판별할 수 있는가. 0이면 다음 촬영에서 시트가 뜬다.
     *
     * ⚠️ **음수를 돌려주지 않는다.** 소급 적용을 안 하기로 했으므로
     *    ([GamePolicy.ANONYMOUS_IDENTIFY_LIMIT] 주석) 이미 한도를 넘긴 기기가
     *    존재한다 — `2 - 5 = -3`을 화면에 그리면 그 기기에서만 이상한 숫자가 뜬다.
     *
     * 🔴 **지금 이 값을 쓰는 화면이 없다.** `n회 남았어요` 문구가 A 문서에 없어서
     *    만들지 않았다(A 문서 4절 21번 · 오너 미답). 판정 근거를 화면 없이도
     *    테스트로 고정해 두려고 남긴다 — 문구가 오면 그때 붙인다.
     */
    fun remainingAnonymousIdentifies(kakaoLinked: Boolean, identifyCount: Int): Int {
        if (kakaoLinked) return Int.MAX_VALUE
        return (GamePolicy.ANONYMOUS_IDENTIFY_LIMIT - identifyCount).coerceAtLeast(0)
    }
}
