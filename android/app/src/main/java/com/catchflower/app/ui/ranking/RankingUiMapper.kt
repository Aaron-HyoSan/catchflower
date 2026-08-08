package com.catchflower.app.ui.ranking

import com.catchflower.app.data.FriendRanking
import com.catchflower.app.data.MyRegion
import com.catchflower.app.data.RankingResult
import com.catchflower.app.data.RegionRanking

/**
 * 서버 응답 → 화면 상태.
 *
 * **왜 ViewModel 밖으로 뺐나.** [RankingViewModel]은 `AndroidViewModel`이라 JVM
 * 테스트에서 **만들 수가 없다**(`Application`을 요구한다. 이 프로젝트에 Robolectric은
 * 없고, 랭킹 하나 때문에 들이면 테스트 층 전체의 성격이 바뀐다).
 *
 * 🔴 **그런데 이 판정이 조용히 틀리는 자리다.** 응답이 비었을 때 왜 비었는지를
 *    고르는 일이고, 잘못 골라도 **화면은 멀쩡한 빈 상태로 보인다** —
 *    "동네를 안 골랐다"를 "아무도 안 찍었다"로 읽으면 갓 설치한 사용자는
 *    영원히 빈 랭킹을 보며 자기가 할 일이 있다는 걸 모른다.
 *    그래서 화면과 같이 두지 않고 **테스트가 붙는 순수 함수**로 뺐다.
 *    ([com.catchflower.app.data.model.RankingRules]가 같은 이유로 순수 객체다.)
 */
object RankingUiMapper {

    /** 서버 `region_ranking.scope`의 값. B-6이 구로 넓혔다는 뜻이다. */
    const val SCOPE_GU = "gu"

    /**
     * 화면 17.
     *
     * 🔴 **[mine]을 같이 받아야 한다.** 서버는 "지역을 안 정했다"와 "지역은 정했지만
     *    아무도 안 찍었다"에 **똑같이 빈 응답**을 준다(실측: `region_none.json` ==
     *    `region_with_dong.json` == `[]`). `scope`는 **행 안에** 실려 오므로 행이
     *    없으면 알 수 없다. 랭킹 응답만 보고 분기하면 두 상태가 한 문구로 뭉개진다.
     *
     * @param ranking `region_ranking` 결과
     * @param mine `myRegion` 결과. **실패했으면 [RegionRankingUi.Failed]로 떨어진다** —
     *   `false`(지역 미설정)로 뭉개면 네트워크가 죽은 사용자에게 `동네 선택하기`를
     *   내밀고, 이미 고른 동네를 **다시 고르게 만든다**(기획서 9장 6개월 규칙 위반).
     */
    fun region(
        ranking: RankingResult<RegionRanking>,
        mine: RankingResult<MyRegion>,
    ): RegionRankingUi = when (ranking) {
        is RankingResult.Loaded -> {
            val value = ranking.value
            val isGu = value.scope == SCOPE_GU
            when {
                value.rows.isNotEmpty() -> RegionRankingUi.Loaded(
                    rows = value.rows,
                    // B-6이 구로 넓혔으면 **구명을 쓴다.** 동명을 쓰면 거짓말이다.
                    regionLabel = regionLabel(mine, isGu = isGu),
                    scopeIsGu = isGu,
                    memberCount = value.memberCount,
                )
                // 여기부터 목록이 비었다. **왜 비었는지를 따로 물어서 가른다.**
                mine.isRegionSet == false -> RegionRankingUi.NoRegion
                // 지역 설정 여부조차 못 물었다. 빈 랭킹이라고 말하면 안 되는
                // 이유와 같다 — 모르는 것은 모른다고 말한다.
                mine.isRegionSet == null -> RegionRankingUi.Failed
                else -> RegionRankingUi.Empty
            }
        }
        is RankingResult.Failed -> RegionRankingUi.Failed
        RankingResult.NotConfigured -> RegionRankingUi.NotConfigured
    }

    /** 화면 18. 지역과 달리 따로 물을 것이 없다 — 친구 랭킹에는 지역 조건이 없다. */
    fun friends(ranking: RankingResult<FriendRanking>): FriendRankingUi = when (ranking) {
        is RankingResult.Loaded -> FriendRankingUi.Loaded(
            rows = ranking.value.rows,
            rankedFriendCount = ranking.value.rankedFriendCount,
        )
        is RankingResult.Failed -> FriendRankingUi.Failed
        RankingResult.NotConfigured -> FriendRankingUi.NotConfigured
    }

    /**
     * 제목·목록 라벨에 넣을 지역명.
     *
     * `region_name`은 `서울특별시 마포구 연남동` 전체다(실측). 동은 마지막 어절,
     * 구는 `구`로 끝나는 어절이다.
     *
     * ⚠️ 못 읽으면 **빈 문자열**이다. `연남동` 같은 기본값을 넣으면
     *    **남의 동네 이름을 내 화면에 박는다.**
     */
    internal fun regionLabel(mine: RankingResult<MyRegion>, isGu: Boolean): String {
        val value = (mine as? RankingResult.Loaded)?.value ?: return ""
        val parts = value.regionName?.trim()?.split(' ').orEmpty()
        return if (isGu) {
            parts.firstOrNull { it.endsWith("구") }.orEmpty()
        } else {
            parts.lastOrNull().orEmpty()
        }
    }

    /**
     * 화면 18 `친구 {N}명과 겨루는 중` · 화면 20 `친구 관리 {N}명`의 N.
     *
     * 🔴 **모르면 `null`이다. 0도 아니고 랭킹 행 수도 아니다.**
     *    ① 0으로 만들면 화면 18이 **초대 화면을 띄운다** — 친구 8명인 사용자가
     *      `아직 겨룰 친구가 없어요`를 본다. ② 랭킹 행 수로 대신하면 화면 18과
     *      화면 20이 **같은 것을 다른 숫자로 말한다**(실측: 이번 시즌 발견이 0건인
     *      친구는 랭킹 응답에 행이 없다). (18)에서 7 vs 8로 겪은 사고가 그것이다.
     *    `null`이면 화면은 **숫자를 안 쓴다** — 틀린 숫자보다 없는 숫자가 낫다.
     */
    fun friendCount(result: RankingResult<Int>): Int? = (result as? RankingResult.Loaded)?.value

    /**
     * 화면 18에서 랭킹 대신 **초대 유도를 전체 화면으로** 띄울지.
     *
     * 기준은 와이어프레임 18 주석 ④의 **2명 이하**다.
     *
     * 🔴 **로딩·실패 중에는 false다.** 응답이 오기 전에 초대 화면을 띄우면 친구가
     *    8명인 사용자도 화면을 열 때마다 `아직 겨룰 친구가 없어요`를 먼저 본다 —
     *    한 프레임이라도 보이면 "친구가 사라졌나?"로 읽힌다.
     *
     * 🔴 **[friendCount]로 판단한다. [FriendRankingUi.Loaded.rankedFriendCount]가
     *    아니다.** 친구가 8명인데 아무도 이번 시즌에 안 찍었으면 랭킹 행에는 나만
     *    있다 — 그걸로 세면 `아직 겨룰 친구가 없어요`가 뜨는데, 그 화면의 버튼은
     *    `초대 링크 보내기`다. **이미 친구인 8명을 다시 초대하라고 말하는 것**이 된다.
     *
     * @param friendCount `friendships`가 센 실제 친구 수. `null`(모름)이면 **false** —
     *   랭킹은 실제로 받아온 데이터이므로, 못 센 숫자 때문에 진짜 데이터를 감추지 않는다.
     */
    fun showInvite(friends: FriendRankingUi, friendCount: Int?): Boolean =
        friends is FriendRankingUi.Loaded &&
            friendCount != null &&
            friendCount <= FRIENDS_MIN_FOR_RANKING

    /**
     * 와이어프레임 18 주석 ④ `친구 0~2명일 때`.
     *
     * ⚠️ 이 숫자는 게임 규칙이 아니라 **화면 분기 기준**이라
     *    `GamePolicy`에 넣지 않았다 (게임 규칙 숫자만 GamePolicy — 공유계약 3절).
     */
    const val FRIENDS_MIN_FOR_RANKING = 2

    /**
     * 화면 20 프로필.
     *
     * 🔴 **못 받은 값은 `null`로 남긴다.** 더미에는 `꽃보다효산`·`연남동`·
     *    `2026년 3월부터 함께`가 항상 있었지만, 서버에서는 **하나씩 따로 없을 수 있다**
     *    (지역은 아직 안 골랐고 닉네임은 있는 상태가 기본값이다 — `handle_new_user`가
     *    닉네임만 넣는다). 기본값을 채우면 **남의 정보를 내 프로필에 박는다.**
     */
    fun profile(mine: RankingResult<MyRegion>): ProfileUi {
        val value = (mine as? RankingResult.Loaded)?.value ?: return ProfileUi()
        return ProfileUi(
            nickname = value.nickname,
            // 화면 20은 **전체 지역명**을 쓴다(`서울특별시 마포구 연남동`).
            regionFull = value.regionName,
            // 프로필 줄의 `{동명} · {가입}`은 동만 쓴다.
            dongName = value.dongName,
            joinedLabel = joinedLabel(value.createdAt),
            regionChangeLabel = regionChangeLabel(value.regionChangedAt),
        )
    }

    /**
     * 화면 20 `2026년 3월부터 함께`.
     *
     * 🔴 **`createdAt`을 못 읽으면 `null`이다. 오늘 날짜를 넣지 않는다** —
     *    3월에 가입한 사용자에게 `2026년 8월부터 함께`라고 말하는 것이 되고,
     *    **화면은 완벽하게 정상으로 보인다.**
     *
     * ⚠️ 형식은 실측한 `2026-08-08T09:45:53.504149+00:00`이다. 앞 7글자(`yyyy-MM`)만
     *    쓰므로 소수점 이하 자리수·타임존 표기가 달라져도 흔들리지 않는다.
     *    **`Date`로 파싱하지 않는다** — `SimpleDateFormat`은 마이크로초 6자리를
     *    못 읽고 조용히 틀린 날짜를 준다.
     */
    internal fun joinedLabel(createdAt: String?): String? {
        val head = createdAt?.trim()?.take(7) ?: return null
        // `2026-08` 꼴이 아니면 형식이 바뀐 것이다. 억지로 읽지 않는다.
        if (!Regex("""\d{4}-\d{2}""").matches(head)) return null
        val year = head.substring(0, 4)
        // `03` → `3`. 앞의 0을 남기면 `2026년 03월부터`가 된다.
        val month = head.substring(5, 7).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return "${year}년 ${month}월부터 함께"
    }

    /**
     * 화면 20 활동 지역 칸의 `변경 불가 · 2027. 2. 4. 부터 가능`.
     *
     * 🔴 **`regionChangedAt`이 없으면 `null`이고, 화면은 그 줄을 아예 안 쓴다.**
     *    실측으로 **서버가 이 컬럼을 안 채운다** — `users` PATCH가 성공해도 null로
     *    남는다(트리거가 없다). 그런데 날짜를 만들어 내면
     *    **`2027. 2. 4. 부터 가능`이라고 못을 박아 놓고 근거가 없는 상태**가 된다.
     *    사용자가 그날 와서 안 되면 우리가 거짓말한 것이 되고, 되면 규칙이 없는 것이다.
     *
     * ⚠️ **`가입일 + 6개월`로 대신 계산하지 않는다.** 6개월 규칙의 기준은
     *    "마지막으로 **바꾼** 시각"이고 가입은 바꾼 것이 아니다. 한 번도 안 바꾼
     *    사용자는 **지금 바꿀 수 있어야 한다** — 가입일로 계산하면 갓 가입한
     *    사용자가 6개월간 동네를 못 고르게 막힌다.
     *
     * §9와 A 문서에 올렸다: 이 줄을 쓰려면 서버가 `region_changed_at`을 채워야 한다.
     */
    internal fun regionChangeLabel(regionChangedAt: String?): String? {
        val head = regionChangedAt?.trim()?.take(10) ?: return null
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(head)) return null
        val year = head.substring(0, 4).toIntOrNull() ?: return null
        val month = head.substring(5, 7).toIntOrNull() ?: return null
        val day = head.substring(8, 10).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        // 6개월 뒤. 12를 넘기면 해가 넘어간다 — `13월`이 되면 안 된다.
        val shifted = month + REGION_CHANGE_MONTHS
        val nextYear = year + (shifted - 1) / 12
        val nextMonth = (shifted - 1) % 12 + 1
        // A 문서 표기 그대로 `2027. 2. 4. 부터 가능`. 날짜는 그대로 두고 달만 옮긴다
        // (2월 31일 같은 값이 나올 수 있으나 서버가 준 날짜를 우리가 고칠 근거가 없다).
        return "$nextYear. $nextMonth. $day. 부터 가능"
    }

    /** 기획서 9장 `활동 지역은 6개월에 한 번만`. */
    const val REGION_CHANGE_MONTHS = 6

    /**
     * 화면 20 프로필 값. **전부 `null`일 수 있다** — 서버를 못 불렀거나 키가 없는 빌드다.
     *
     * ⚠️ 화면은 `null`인 칸을 **그리지 않는다.** 빈 문자열로 그리면 레이아웃에
     *    설명 없는 빈 줄이 남아 "정보가 사라졌다"로 읽힌다.
     */
    data class ProfileUi(
        val nickname: String? = null,
        val regionFull: String? = null,
        val dongName: String? = null,
        val joinedLabel: String? = null,
        /**
         * `2027. 2. 4. 부터 가능`. **실측으로 서버가 `region_changed_at`을 안 채우므로
         * 지금은 항상 `null`이다** — 이유는 [regionChangeLabel]에 있다.
         */
        val regionChangeLabel: String? = null,
    )

    /**
     * "지역을 정했는가"를 **3값으로** 본다.
     *
     * 🔴 `Boolean`으로 만들면 조회 실패가 `false`(= 지역 미설정)로 뭉개진다. 그러면
     *    네트워크가 죽었을 때 화면이 `동네 선택하기`를 내밀고, 사용자는 **이미 고른
     *    동네를 다시 고르게 된다** — 기획서 9장 "6개월에 한 번만 변경"에 걸린다.
     */
    private val RankingResult<MyRegion>.isRegionSet: Boolean?
        get() = when (this) {
            is RankingResult.Loaded -> value.isSet
            is RankingResult.Failed -> null
            RankingResult.NotConfigured -> null
        }
}
