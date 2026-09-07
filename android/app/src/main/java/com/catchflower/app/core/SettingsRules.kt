package com.catchflower.app.core

/**
 * 화면 20-2 `설정`에 **어떤 행이 나오는가.** 문구는 A 문서 3절 ⑤가 전부다.
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸면서 생겼다.
 *
 * ## 🔴 왜 화면이 아니라 여기서 정하는가
 *
 * 두 가지가 **화면에서만 틀리고 아무 오류도 안 낸다**:
 *
 * 1. **주소 없는 빌드의 `고객문의`.** 행을 그리면 눌러서 메일 앱이 열리고 **받는 사람이
 *    빈 칸**이다 — 보낸 사용자는 접수됐다고 믿는다(A 문서 4절 17번).
 *    ⚠️ ~~지금 빌드가 정확히 그 상태다~~ → **2026-09-08에 `CONTACT_EMAIL`이 들어왔다.**
 *    즉 이 규칙은 지금 **아무 행도 안 숨기고 있다**(설정 7행 → 8행). 규칙을 지우지 않는
 *    이유는 값이 빠진 빌드가 다시 나올 수 있고 그때 증상이 **빈 메일 한 통**뿐이기
 *    때문이다 — 그리고 화면에 `if`로 두면 검증되지 않는다.
 *    🔴 **조건이 있다 ≠ 조건이 걸러낸다** — 이 항이 지금 0행을 숨긴다는 사실은
 *    화면·테스트 어디에도 안 나타난다. 두 방향을 `SettingsRulesTest`가 다 잰다
 *    (`주소가_있으면_여덟_행이다` · `주소가_없어도_나머지_행은_남는다`).
 * 2. **`앱 버전`을 누를 수 있게 두는 것.** 눌러도 아무 일이 없으면 그게 죽은 버튼이다.
 *    "값 행"과 "메뉴 행"의 차이가 화면 코드에만 있으면 다음 사람이 `MenuRow`로 바꿔
 *    적는다 — 그리고 컴파일도 테스트도 통과한다.
 */
object SettingsRules {

    /**
     * 설정 행. **[label]은 A 문서 3절 ⑤ `행` 칸의 문구를 그대로 쓴다.**
     *
     * ⚠️ `활동 지역`은 2절 20번 본문에도 같은 이름으로 있다. 여기서 `동네 설정` 같은
     *    다른 말을 쓰면 같은 자리를 두 이름으로 부르게 된다.
     */
    enum class Row(
        val label: String,
        val clickable: Boolean,
        /**
         * 되돌릴 수 없는 행. **다른 행과 같은 얼굴이면 안 된다**(글자색 `CfColor.Error`).
         *
         * 🔴 판정을 화면에 두지 않는 이유는 위 ①②와 같다 — 화면의 `if`는
         *    행이 늘어날 때 조용히 빠지고, 그때 증상은 "탈퇴가 활동 지역처럼 보인다"다.
         */
        val destructive: Boolean = false,
    ) {
        /** 화면 02를 연다. 6개월 규칙은 그 화면이 판단한다. */
        REGION("활동 지역", clickable = true),

        /** **OS 알림 설정**을 연다. 우리 토글을 만들지 않는다(A 문서 3절 ⑤). */
        NOTIFICATION("알림 설정", clickable = true),

        /** 메일 앱을 연다. **주소가 있는 빌드에만 나온다** ([rows]). */
        CONTACT("고객문의", clickable = true),

        /**
         * 화면 20-3을 연다(A 문서 3절 ⑨·⑩). 아래 셋은 **출시 필수**다 —
         * 개인정보 처리방침을 앱 안에서 볼 수 없으면 Play 심사에서 내려간다.
         */
        PRIVACY("개인정보 처리방침", clickable = true),

        TERMS("이용약관", clickable = true),

        /**
         * 🔴 **이용약관에 합치지 않는다.** 위치정보법이 별도 약관을 요구한다
         *    (8세 이하의 아동등 보호의무자 조항·위치정보관리책임자가 그 안에 있다).
         *    합치면 법적으로 없는 것과 같아지는데, 화면에는 아무 증상이 없다.
         */
        LOCATION_TERMS("위치기반서비스 이용약관", clickable = true),

        /** 🔴 **값 행이다.** 누를 수 없다 — 누를 수 있으면 그게 죽은 버튼이다. */
        VERSION("앱 버전", clickable = false),

        /**
         * 확인 다이얼로그를 띄운다(A 문서 3절 ⑪ · [AccountDeletionRules]).
         *
         * 🔴 **맨 아래다.** 위로 올리면 `활동 지역`을 누르려던 손가락이 닿는다.
         *
         * ⚠️ **로그인 여부와 무관하게 항상 보인다.** 익명 계정도 서버에 실제로 있는
         *    계정이라(`handle_new_user()`가 닉네임까지 만든다) 로그인해야 지울 수 있게
         *    하면 **지울 방법이 없는 계정**이 생긴다. 그래서 `LoginGate.GatedAction`에
         *    넣지 않았다 — 게이트가 걸리지 않는 유일한 파괴적 행동이고, 그 예외는
         *    [com.catchflower.app.core.LoginGate] 주석에도 적었다.
         */
        DELETE_ACCOUNT("회원 탈퇴", clickable = true, destructive = true),
    }

    /**
     * 그릴 행 목록.
     *
     * @param contactEmail `AppSecrets.contactEmail`. **비었으면 [Row.CONTACT]를 뺀다.**
     */
    fun rows(contactEmail: String): List<Row> =
        Row.entries.filter { it != Row.CONTACT || contactVisible(contactEmail) }

    /**
     * `고객문의`를 그릴 것인가.
     *
     * ⚠️ **화면 20의 메뉴도 이 함수를 쓴다.** 두 곳이 각자 판단하면 한쪽만 숨겨지고,
     *    안 숨겨진 쪽이 위 ①의 빈 메일을 연다.
     */
    fun contactVisible(contactEmail: String): Boolean = contactEmail.isNotBlank()

    /**
     * 이 행이 열 법적 문서. 문서 행이 아니면 null.
     *
     * ⚠️ 짝을 화면의 `when`에만 두지 않는다 — 행이 늘어날 때 `else -> null`로 새 행이
     *    **조용히 아무것도 안 여는 줄**이 된다. 여기 있으면 검사가 셀 수 있다.
     */
    fun legalDoc(row: Row): LegalDoc? = when (row) {
        Row.PRIVACY -> LegalDoc.PRIVACY
        Row.TERMS -> LegalDoc.TERMS
        Row.LOCATION_TERMS -> LegalDoc.LOCATION
        Row.REGION, Row.NOTIFICATION, Row.CONTACT, Row.VERSION, Row.DELETE_ACCOUNT -> null
    }
}
