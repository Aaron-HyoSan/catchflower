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
 *    빈 칸**이다 — 보낸 사용자는 접수됐다고 믿는다(A 문서 4절 17번). 지금 빌드가 정확히
 *    그 상태다(`CONTACT_EMAIL`이 비어 있다). 즉 **이 규칙이 지금 동작 중**이고,
 *    화면에 `if`로 두면 검증되지 않는다.
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
    enum class Row(val label: String, val clickable: Boolean) {
        /** 화면 02를 연다. 6개월 규칙은 그 화면이 판단한다. */
        REGION("활동 지역", clickable = true),

        /** **OS 알림 설정**을 연다. 우리 토글을 만들지 않는다(A 문서 3절 ⑤). */
        NOTIFICATION("알림 설정", clickable = true),

        /** 메일 앱을 연다. **주소가 있는 빌드에만 나온다** ([rows]). */
        CONTACT("고객문의", clickable = true),

        /** 🔴 **값 행이다.** 누를 수 없다 — 누를 수 있으면 그게 죽은 버튼이다. */
        VERSION("앱 버전", clickable = false),
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
}
