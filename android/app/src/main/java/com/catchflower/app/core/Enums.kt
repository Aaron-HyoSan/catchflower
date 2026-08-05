package com.catchflower.app.core

/**
 * 공유계약 1-4의 enum. **문자열 값이 원본이고 혼자 바꾸지 않는다.**
 *
 * 숫자 대신 문자열을 쓴다 — 나중에 값이 추가돼도 순서가 안 깨진다.
 * 화면 표시용 한글(`label`)은 플랫폼에서 로컬라이즈하는 몫이라 여기 둔다.
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
