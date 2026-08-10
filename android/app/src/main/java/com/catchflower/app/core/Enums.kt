package com.catchflower.app.core

/**
 * 공유계약 1-4의 enum. **문자열 값이 원본이고 혼자 바꾸지 않는다.**
 *
 * 숫자 대신 문자열을 쓴다 — 나중에 값이 추가돼도 순서가 안 깨진다.
 * 화면 표시용 한글(`label`)은 플랫폼에서 로컬라이즈하는 몫이라 여기 둔다.
 */

/**
 * 개화 계절 (공유계약 1-1 / 1-1-d).
 *
 * 🔴 **`Flower.season`은 nullable이다.** 계절은 원천 데이터에 없어서 개화월에서
 *    파생하는데, 관찰 0건인 278종은 대표월조차 없다. 근거 없이 찍으면 **화면 06
 *    `봄 꽃 보기`에 여름 꽃이 섞이고**, 그건 예외 없이 조용히 틀린다.
 *    그래서 모르면 `null`이고, 필터에 걸리지 않는다.
 *
 * ⚠️ 그래서 [fromWire]에 빈 문자열이 오면 여기서 죽는다 —
 *    **적재가 `""`를 내리는 것을 막았지만**(`flower_master._verify`),
 *    파서는 `isNull`로 판정한다. `optString("season")`을 쓰면 안 된다:
 *    JSON `null`에 대해 **기기에서는 `"null"`, JVM 테스트에서는 `""`** 를 준다 —
 *    픽스처 기반 테스트로는 절대 안 잡히는 차이다.
 */
enum class Season(val wire: String, val label: String) {
    SPRING("spring", "봄"),
    SUMMER("summer", "여름"),
    AUTUMN("autumn", "가을"),
    WINTER("winter", "겨울");

    companion object {
        fun fromWire(value: String): Season =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 season: $value")
    }
}

/**
 * 개화월을 **무슨 근거로 정했는가** (공유계약 1-2-b).
 *
 * ⚠️ **화면에 내보내지 않는다.** 사용자에게 보일 문구가 A 문서에 없다.
 *    이 값은 두 가지에만 쓴다 — (a) 개화기 표기를 낼 수 있는지 판정(계약 1-2-c),
 *    (b) 사람이 채워야 하는 종을 세기.
 *
 * 🔴 [PEAK_WINDOW]·[UNKNOWN]은 **개화기 표기가 없다.** 1,017종이 여기 해당한다.
 *    그 종의 `bloomLabel`은 빈 문자열이고, 화면은 문구를 만들지 않고 **절을 뺀다**.
 *    판정은 [Flower.bloomText] 한 곳에서만 한다 — 화면마다 따로 하면 갈린다.
 */
enum class BloomSource(val wire: String) {
    /** ① 사람이 정한 200종. */
    HUMAN("human"),

    /** ② `개화기_초안` ±1개월. 803종. */
    DRAFT("draft"),

    /** ③ 관찰 ≥30건에서 추정한 연속 구간. 37종. */
    OBSERVED("observed"),

    /** ④ 관찰 1~29건 — 최다월 ±4. **구간을 추정하지 않는다.** 739종. */
    PEAK_WINDOW("peak_window"),

    /** ⑤ 관찰 0건. 전월 허용. **"일 년 내내 핀다"가 아니라 "모른다"는 뜻이다.** 278종. */
    UNKNOWN("unknown");

    /**
     * 개화기 표기(`5~6월`)를 화면에 낼 수 있는가.
     *
     * ④⑤는 낼 수 없다 — ④는 관찰 최다월 ±4(9개월)라 `12~8월`처럼 나오고
     * 그건 개화기가 아니라 **탐색 창**이다. 그걸 `12~8월에 피어요`로 말하면
     * 모르는 것을 아는 것처럼 말하게 된다(계약 1-2-c).
     */
    val hasLabel: Boolean
        get() = this != PEAK_WINDOW && this != UNKNOWN

    companion object {
        fun fromWire(value: String): BloomSource =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 bloom_source: $value")
    }
}

enum class Rarity(val wire: String, val label: String) {
    /** 도심 생활 반경에서 거의 확실히 만난다. 온보딩 추천 후보군. */
    COMMON("common", "흔함"),

    /** 공원·산책로를 찾아가면 만날 수 있다. */
    NORMAL("normal", "보통"),

    /** 특정 지역·시기에만 있다. 신규 발견 축하 연출을 가장 강하게 준다. */
    RARE("rare", "귀함");

    companion object {
        fun fromWire(value: String): Rarity =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 rarity: $value")
    }
}

/** 오인식 위험도. 판별 신뢰도 임계값의 근거 (GamePolicy.confidenceThreshold). */
enum class AiDifficulty(val wire: String, val label: String) {
    LOW("low", "하"),
    MID("mid", "중"),
    HIGH("high", "상");

    companion object {
        fun fromWire(value: String): AiDifficulty =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 ai_difficulty: $value")
    }
}

/** 발견 기록의 공개 범위. 화면 13에서 매번 사용자가 고른다. */
enum class Visibility(val wire: String, val label: String) {
    PUBLIC("public", "모두에게 공개"),
    FRIENDS("friends", "친구에게만 공개"),
    PRIVATE("private", "나만 보기");

    companion object {
        fun fromWire(value: String): Visibility =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 visibility: $value")
    }
}

/** C-2 상호 수락. 단방향이 아니라 요청→수락이다. */
enum class FriendState(val wire: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    BLOCKED("blocked");

    companion object {
        fun fromWire(value: String): FriendState =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 friend_state: $value")
    }
}

/** C-1 신고 3회 누적 자동 숨김. */
enum class ReportState(val wire: String) {
    OPEN("open"),
    HIDDEN("hidden"),
    RESOLVED("resolved");

    companion object {
        fun fromWire(value: String): ReportState =
            entries.firstOrNull { it.wire == value }
                ?: error("알 수 없는 report_state: $value")
    }
}
