package com.catchflower.app.ui.region

import com.catchflower.app.data.RegionCandidate

/**
 * 화면 02 활동 지역 선택의 상태.
 *
 * 🔴 **[Idle]과 [NoResult]를 합치지 않는다.** 둘 다 "목록이 비었다"인데
 *    [Idle]은 **아직 안 검색했다**(설명·안내를 보여줄 자리)이고
 *    [NoResult]는 **찾아봤는데 없다**(`검색 결과가 없어요`)다. 합치면 화면을 열자마자
 *    "검색 결과가 없어요"가 떠서 **자기 동네가 없는 앱**으로 읽힌다.
 *
 * ⚠️ [Failed]도 따로 둔다 — [RegionSearchResult]와 같은 이유다. 네트워크 실패를
 *    `검색 결과가 없어요`로 보여주면 사용자는 자기 동네 이름을 의심한다.
 *
 * 문구는 A 문서 2절 `02 활동 지역 선택` 표에 있다. **여기서 만들지 않는다.**
 */
sealed interface RegionPickerUi {
    /** 아직 검색어를 안 넣었다(또는 두 글자 미만이다). */
    data object Idle : RegionPickerUi

    data object Searching : RegionPickerUi

    /** 찾아봤는데 행정동이 없다. */
    data object NoResult : RegionPickerUi

    /** 카카오를 못 불렀다. */
    data object Failed : RegionPickerUi

    /** 키 없는 빌드. 오류 문구도 띄우지 않는다 — 사용자 탓이 아니다. */
    data object NotConfigured : RegionPickerUi

    data class Loaded(val candidates: List<RegionCandidate>) : RegionPickerUi
}

/**
 * 고른 동네를 저장하는 중·끝난 상태.
 *
 * 🔴 **[RuleRejected]를 [SaveFailed]와 합치지 않는다.** 6개월 규칙에 걸린 것은
 *    **다시 눌러도 안 된다.** 합치면 화면이 `다시 시도`를 내밀고 사용자는 눌러도
 *    안 되는 버튼을 계속 누른다.
 */
sealed interface RegionSaveUi {
    data object Idle : RegionSaveUi

    data object Saving : RegionSaveUi

    data object Saved : RegionSaveUi

    /** 네트워크·5xx. **다시 시도한다.** */
    data object SaveFailed : RegionSaveUi

    /**
     * 서버가 규칙으로 거절했다(0004 트리거의 6개월 규칙 등).
     *
     * ⚠️ **날짜를 화면이 만들어 내지 않는다.** 서버 메시지에 다음 변경 가능 날짜가
     *    들어 있지만 그걸 파싱해 보여주면 형식이 바뀌는 순간 조용히 틀린 날짜를
     *    말한다. 화면 20의 `{날짜}부터 가능` 줄은 `region_changed_at`을 읽어서 만든다
     *    (`RankingUiMapper.regionChangeLabel`) — **한 곳에서만 만든다.**
     */
    data object RuleRejected : RegionSaveUi
}
