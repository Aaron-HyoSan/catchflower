package com.catchflower.app.core

/**
 * 게임 정책 숫자를 **한 곳에** 모은다.
 *
 * 공유계약 3절이 이 파일을 요구한 이유: 아래 숫자 대부분이 **오너 미확정**이다.
 * 흩어서 하드코딩하면 오너 답변 한 번에 양쪽(iOS·AOS)에서 수십 군데를 고쳐야 한다.
 * 답이 오면 이 파일 한 줄만 고친다.
 *
 * ⚠️ 이 값들은 공유 자산이다. 혼자 바꾸지 않는다 —
 *    `프로젝트 맥락/공유계약_iOS_AOS.md`를 먼저 고치고 `진행.md`에 기록한다.
 */
object GamePolicy {

    // ── B-3 AI 판별 방식 ─────────────────────────────────────────────
    // 권고: ③ 항상 후보 3개 + ② 난이도별 임계값 병용 (오너 ★ 미답)
    // 임계값은 "확정 여부"가 아니라 "후보를 어떻게 보여줄지"에 쓴다.

    /** 항상 반환·표시하는 후보 수. 3개 미만이면 있는 만큼. */
    const val CANDIDATE_COUNT = 3

    /** 난이도별 1순위 신뢰도 임계값 (0.0~1.0). PlantNet은 보정된 확률이라 그대로 비교한다. */
    val confidenceThreshold: Map<AiDifficulty, Float> = mapOf(
        AiDifficulty.LOW to 0.60f,
        AiDifficulty.MID to 0.70f,
        AiDifficulty.HIGH to 0.85f,
    )

    /**
     * 1순위가 이 값에도 못 미치면 판별 실패(화면 12)로 보낸다.
     *
     * ⚠️ **iOS와 어긋나 있었다** — AOS 0.20 / iOS `identifyFailureFloor = 0.30`.
     *    같은 사진에 두 앱이 다른 답을 주는 값이라 iOS 값으로 맞췄다(공유계약 3절).
     *    AOS의 0.20은 근거가 없었고, iOS 0.30은 최소한 실측 대상이 된 값이다.
     *
     * ⚠️ **다만 0.30도 근거가 없다.** iOS 실측 200장에서 이 값이 정답 66장(43%)을
     *    버리고 오답 9장을 막았다 — 어느 값에서도 순효과가 음수다(진행 (19) 4절).
     *    B-3-a ★ 오너 미결이고 권고는 0.05다. **답이 오면 이 한 줄만 고친다.**
     */
    const val MIN_CONFIDENCE_FOR_ANY_CANDIDATE = 0.30f

    // ── B-3 어뷰징 가드 3종 ──────────────────────────────────────────
    // 후보 3개를 보여주면 "3순위에 귀함 종이 있을 때 그걸 고르는" 구멍이 생긴다.

    /** ① 희귀종을 낮은 순위에서 고르면 즉시 확정하지 않고 사진을 한 장 더 받는다. */
    const val RARE_PICK_NEEDS_EXTRA_PHOTO_FROM_RANK = 2

    /** ① 희귀종을 이 점수 미만에서 고른 경우도 추가 사진을 받는다. */
    const val RARE_PICK_LOW_SCORE = 0.50f

    // ② 사용자가 몇 순위를 골랐는지 기록한다 → discoveries.ai_picked_rank (스키마 항목)
    // ③ 개화월 하드 필터로 애초에 후보에 올리지 않는다 → BloomMonthFilter

    // ── B-5 중복 제한 ────────────────────────────────────────────────
    /**
     * 같은 종 + **같은 장소**는 하루 1회만 기록 누적 (권고 ③ · ★ 미답).
     * 지도 도배 방지로 넣었지만 AI 호출을 35% 줄이는 효과가 더 크다.
     */
    const val SAME_FLOWER_SAME_PLACE_DAILY_LIMIT = 1

    /** "같은 장소"로 볼 반경(m). 좌표 반올림 캐시 단위와 같이 쓴다. */
    const val SAME_PLACE_RADIUS_METERS = 100

    // ── B-6 지역 랭킹 ────────────────────────────────────────────────
    /**
     * 동 단위 참여자가 이 수 미만이면 구 단위 랭킹으로 확장 (권고 10명 · ★ 미답).
     * ⚠️ 런타임에 동에서 구를 유도할 수 없어 discoveries에 dong_code·gu_code를 둘 다 저장한다.
     */
    const val REGION_RANKING_MIN_MEMBERS = 10

    // ── B-11 판별 실패 ───────────────────────────────────────────────
    /** 이 횟수만큼 연속 실패하면 화면 12의 제목을 "꽃이 아닐 수도 있어요"로 바꾼다 (권고 3회 · ● 미답). */
    const val FAIL_STREAK_FOR_NOT_A_FLOWER = 3

    // ── C-1 신고 ─────────────────────────────────────────────────────
    /** 신고가 이 횟수 누적되면 자동 숨김 (권고 3회 · ★ 미답). 운영 인력이 없어 자동 숨김이 필요하다. */
    const val REPORT_COUNT_FOR_AUTO_HIDE = 3

    // ── C-4 연령 ─────────────────────────────────────────────────────
    /** 만 14세 이상만 가입 (권고 · ★ 미답). 미만을 받으면 법정대리인 동의 개발이 붙는다. */
    const val MIN_AGE = 14

    // ── C-9 댓글 ─────────────────────────────────────────────────────
    /** 댓글 최대 길이 (권고 200자 · ○ 미답). 수정 불가. */
    const val COMMENT_MAX_LENGTH = 200

    /** 지도 공유 한 줄 설명 길이. 화면 13의 카운터가 `0 / 40`이다. */
    const val SHARE_NOTE_MAX_LENGTH = 40

    // ── A-4 사진 ─────────────────────────────────────────────────────
    /** 업로드 사진의 장변 픽셀. 압축 1장만 보관 (권고 1600px · ● 미답). */
    const val PHOTO_LONG_EDGE_PX = 1600

    /** JPEG 압축 품질. */
    const val PHOTO_JPEG_QUALITY = 85

    // ── API 상한 ─────────────────────────────────────────────────────
    /**
     * 서버 일일 API 상한은 예상치의 3배로 둔다 (권고).
     * PlantNet 무료 등급은 일 500건에서 그날 멈추므로 피크가 한계를 정한다.
     */
    const val DAILY_API_LIMIT_SAFETY_FACTOR = 3

    /** 카카오 지오코딩 캐시용 좌표 반올림 자릿수. 같은 자리 재조회를 막는다. */
    const val GEOCODE_CACHE_COORD_DECIMALS = 4

    // ── B-1 시즌 ─────────────────────────────────────────────────────
    // 권고: 3~8월 / 9~11월 + 12~2월 휴지기 (★ 미답).
    // 겨울 개화종이 6종뿐이라 6개월로 자르면 한 시즌 후반 3개월이 죽는다.

    /** 시즌 경계. 휴지기(12~2월)에는 랭킹 대신 "다음 시즌 D-day"를 띄운다. */
    val seasonWindows: List<SeasonWindow> = listOf(
        SeasonWindow(index = 1, startMonth = 3, endMonth = 8),
        SeasonWindow(index = 2, startMonth = 9, endMonth = 11),
    )

    /** 시즌이 돌지 않는 달 (휴지기). */
    val dormantMonths: Set<Int> = setOf(12, 1, 2)

    data class SeasonWindow(val index: Int, val startMonth: Int, val endMonth: Int) {
        fun contains(month: Int): Boolean = month in startMonth..endMonth
    }

    // ── 도감 ─────────────────────────────────────────────────────────
    /** 도감 전체 종수. 유사종을 통합해도 200종은 유지한다 (B-4). */
    const val TOTAL_FLOWER_COUNT = 200
}
