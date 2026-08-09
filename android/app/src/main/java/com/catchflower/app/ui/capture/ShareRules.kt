package com.catchflower.app.ui.capture

import com.catchflower.app.core.GamePolicy

/**
 * 화면 13(지도 공유 설정)의 판단. **화면 밖에 둔다.**
 *
 * 🔴 **한글 입력 규칙은 기기로 확인할 수 없다.** `adb shell input text`는 한글을
 *    못 보낸다(8차에 겪었다 · [com.catchflower.app.ui.region.RegionPickerUi] 쪽과
 *    같은 이유). 그래서 "40자에서 자른다"를 Composable 안에 두면 **아무 층에서도
 *    검증되지 않는다** — 붙여넣기로 넘치는 경로가 특히 그렇다.
 *
 * ⚠️ **길이를 `String.length`로 세지 않는다.** 코틀린 `length`는 UTF-16 단위이고
 *    서버 제약은 `char_length(note) <= 40`(01_스키마 186행)으로 **문자 수**다.
 *    이모지 한 개는 `length`로 2, `char_length`로 1이다 —
 *    ① `length`로 세면 화면 카운터가 서버와 다른 숫자를 보여주고,
 *    ② `take(40)`으로 자르면 **서로게이트 쌍이 반토막 난다**(깨진 글자 한 개가
 *       남고 JSON에 그대로 실려 나간다). 둘 다 화면에서는 정상으로 보인다.
 */
object ShareRules {

    /** 화면 13 카운터 `0 / 40`의 분모. 원본은 [GamePolicy.SHARE_NOTE_MAX_LENGTH]다. */
    const val NOTE_MAX = GamePolicy.SHARE_NOTE_MAX_LENGTH

    /**
     * 입력값을 저장 가능한 모양으로 만든다. **입력이 바뀔 때마다 통과시킨다.**
     *
     * ⚠️ **공백을 압축하지 않는다.** `숲길 `을 타이핑하는 중에 뒤 공백을 지우면
     *    다음 글자를 이어 쓸 수 없다 — 입력이 되지 않는 필드가 된다.
     *    앞뒤 공백은 저장 시점([toStored])에만 정리한다.
     *
     * 줄바꿈·탭만 공백으로 바꾼다. 한 줄 필드라 `singleLine`이 입력은 막지만
     * **붙여넣기는 막지 못한다.**
     */
    fun sanitize(raw: String): String = clamp(raw.replace(BREAKS, " "))

    /** 서버 `char_length`와 같은 셈(문자 수). 카운터가 이 값을 쓴다. */
    fun length(note: String): Int = note.codePointCount(0, note.length)

    /** 화면 13 `0 / 40`. */
    fun counter(note: String): String = "${length(note)} / $NOTE_MAX"

    /** 한도에 닿았는가 — 카운터 색을 바꾸는 조건. */
    fun isAtLimit(note: String): Boolean = length(note) >= NOTE_MAX

    /**
     * 저장할 값. **빈 문자열이 아니라 null이다.**
     *
     * ⚠️ `""`를 넣으면 `putOpt`가 키를 넣어 버려서 `note = ''`인 행이 생긴다.
     *    "한 줄을 안 썼다"와 "빈 줄을 썼다"가 구분되지 않고, 화면 16이 빈 줄을
     *    그리게 된다. `한 줄 남기기 (안 써도 돼요)`가 약속한 건 후자가 아니다.
     */
    fun toStored(note: String): String? = clamp(note.trim()).ifBlank { null }

    /**
     * 이 기록을 지도에 올릴 수 있는가.
     *
     * 🔴 **좌표가 없으면 공유해도 지도에 나타나지 않는다.**
     *    [com.catchflower.app.ui.map.MapPins.from]이 좌표 없는 기록을 **버린다**
     *    (0,0으로 채우면 기니 만 앞바다에 핀이 찍힌다). 위치 권한 없이 찍으면
     *    실제로 좌표가 null이고 **그게 정상 경로다**(화면 03의 약속).
     *
     *    그래서 그 기록에 `공유하기`를 눌리게 두면 **`모두에게 공개`로 저장되는데
     *    어느 지도에도 안 보인다** — 사용자는 공유가 됐다고 믿고, 우리는
     *    동의만 받아 놓고 아무것도 못 보여준다. 화면에서 먼저 막는다.
     */
    fun canPlaceOnMap(lat: Double?, lng: Double?): Boolean = lat != null && lng != null

    private val BREAKS = Regex("[\\n\\r\\t]")

    /** 문자(코드포인트) 단위로 자른다 — 서로게이트 쌍을 쪼개지 않는다. */
    private fun clamp(text: String): String {
        if (length(text) <= NOTE_MAX) return text
        return text.substring(0, text.offsetByCodePoints(0, NOTE_MAX))
    }
}
