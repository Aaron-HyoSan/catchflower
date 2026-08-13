package com.catchflower.app.ui.place

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.FlowerRepository
import com.catchflower.app.data.PlaceDiscoverySource
import com.catchflower.app.data.PlaceDiscoveryService
import com.catchflower.app.data.PlaceResult
import com.catchflower.app.data.model.Discovery
import kotlinx.coroutines.launch

/**
 * 화면 15 장소 상세.
 *
 * 🔴 **`@JvmOverloads`가 없으면 화면을 여는 순간 죽는다.** `AndroidViewModelFactory`는
 *    리플렉션으로 `(Application)` **단일 인자** 생성자를 찾는데 코틀린 기본값은 그
 *    생성자를 만들지 않는다 — 화면 02·07·17에서 세 번 당했다.
 *    **컴파일은 통과하고 탭을 누르는 순간 죽는다.**
 *
 * 🔴 **모든 `mutableStateOf` 선언이 [init]보다 위에 있어야 한다.** 아래 있으면
 *    `init`의 코루틴이 아직 null인 `MutableState`에 쓰면서
 *    `NullPointerException … MutableState.setValue`로 죽는다(화면 17에서 겪었다).
 *    지금 [init]이 없지만 **선언 순서를 유지한다** — 나중에 `init`이 생기는 순간
 *    이 함정이 되살아난다.
 *
 * ## JVM 테스트가 이 클래스를 만들 수 없다
 *
 * 그래서 판단은 [PlaceRules]와 [com.catchflower.app.data.PlaceDiscoveries]에 있고
 * 여기 남은 것은 조립이다.
 */
class PlaceViewModel @JvmOverloads constructor(
    app: Application,
    /**
     * 남의 공개 기록 조회. null이면 서버 기능이 꺼진 빌드다.
     *
     * ⚠️ **[DiscoveryRepository]가 든 것과 같은 `AuthService`를 쓴다.** 새로 만들면
     *    토큰 갱신이 서로 다른 prefs 인스턴스를 통해 일어나 회전된 `refresh_token`을
     *    덮어써서 **며칠 뒤 조용히 로그인이 끊긴다**([RankingService]와 같은 이유).
     */
    private val source: PlaceDiscoverySource? =
        DiscoveryRepository.get(app).auth?.let { PlaceDiscoveryService(it) },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(app) {

    private val flowers = FlowerRepository.get(app)
    private val discoveries = DiscoveryRepository.get(app)

    /** 지금 보고 있는 장소. null이면 화면이 열려 있지 않다. */
    var placeName by mutableStateOf<String?>(null)
        private set

    var ui by mutableStateOf<PlaceUi>(PlaceUi.Loading)
        private set

    /**
     * 조회한 좌표. `다시 시도`가 같은 자리를 다시 부르기 위해 붙들어 둔다.
     *
     * ⚠️ **화면에서 다시 넘겨받지 않는다.** 재시도 때 좌표를 인자로 다시 받으면
     *    그 사이에 핀 선택이 바뀌었을 때 **다른 장소를 조회하고 제목만 그대로**다.
     */
    private var lat: Double? = null
    private var lng: Double? = null

    /**
     * `길찾기`가 목적지로 쓰는 좌표. 화면이 열려 있지 않으면 null이다.
     *
     * 🔴 **화면이 좌표를 따로 붙들지 않게 한다.** 화면이 인자로 받아 두면 다른 핀을
     *    누른 뒤에도 **먼저 본 자리로 길을 안내**한다 — [open]이 좌표를 바꿔도 화면의
     *    복사본은 그대로이기 때문이다(위 `다시 시도`가 좌표를 다시 안 받는 것과 같은 이유).
     *
     * ⚠️ [lat]·[lng]를 각각 열지 않고 **쌍으로** 준다. 하나만 null인 상태가 없어서
     *    부르는 쪽이 `!!`를 쓰게 되는 자리를 없앤다.
     */
    val coords: Pair<Double, Double>?
        get() {
            val la = lat ?: return null
            val ln = lng ?: return null
            return la to ln
        }

    /**
     * 핀을 눌러 들어왔다. [com.catchflower.app.ui.map.MapPin]의 좌표·장소명을 받는다.
     *
     * ⚠️ **같은 자리를 다시 열면 다시 조회한다.** 캐시하면 남이 방금 올린 기록이
     *    안 보이고, 그건 "지도에 핀은 늘었는데 목록은 그대로"로 보인다.
     */
    fun open(lat: Double, lng: Double, placeName: String?) {
        this.lat = lat
        this.lng = lng
        this.placeName = placeName
        load()
    }

    /** A 문서 3절 `조회 실패 → 다시 시도`가 부른다. */
    fun retry() {
        load()
    }

    private fun load() {
        val src = source ?: run {
            ui = PlaceUi.NotConfigured
            return
        }
        val la = lat ?: return
        val ln = lng ?: return
        ui = PlaceUi.Loading
        viewModelScope.launch {
            ui = when (val res = src.near(la, ln, RADIUS_METERS)) {
                is PlaceResult.Loaded -> {
                    val value = res.value
                    if (value.rows.isEmpty()) PlaceUi.Empty
                    else PlaceUi.Loaded(
                        data = value,
                        // 🔴 주 시작은 **여기서** 정한다 — `PlaceDiscoveries`는 시각을
                        //    모르고, 서버는 주 경계를 모른다.
                        thisWeek = value.countSince(PlaceRules.weekStart(nowProvider())),
                    )
                }

                is PlaceResult.Failed -> PlaceUi.Failed(res.code)
                PlaceResult.NotConfigured -> PlaceUi.NotConfigured
            }
        }
    }

    /** 칩 라벨. 도감에서 못 찾은 id는 [PlaceRules.chips]가 뺀다. */
    fun flowerName(flowerId: Int): String? = flowers.byId(flowerId)?.name

    /**
     * 내 uuid. `사람들의 기록`에서 **내 기록을 뺀다.**
     *
     * 🔴 안 빼면 사용자가 **자기 기록에 신고 버튼이 있는 화면**을 본다
     *    (`PlaceDiscoveries.othersRecords` 주석).
     */
    val myUserId: String get() = discoveries.userId

    /**
     * 화면 16으로 넘길 기록.
     *
     * ⚠️ **id만 넘기지 않는다.** 남의 기록은 기기에 없으므로 id로 다시 찾을 곳이
     *    없다 — 화면 16이 다시 조회하려면 서버를 한 번 더 부르는데, 방금 받은 행에
     *    같은 값이 다 있다.
     */
    fun record(id: String): Discovery? =
        (ui as? PlaceUi.Loaded)?.data?.rows?.firstOrNull { it.id == id }

    private companion object {
        /**
         * 조회 반경.
         *
         * 지도 핀은 소수 4자리(약 11m)로 묶이고([com.catchflower.app.ui.map.MapPins]),
         * 화면 15는 **그 핀 주변의 남의 기록**을 모은다. 300m는 도보 3~4분이라
         * "이곳"이라고 말할 수 있는 범위다.
         *
         * ⚠️ [com.catchflower.app.core.GamePolicy.SAME_PLACE_RADIUS_METERS](100m)를
         *    쓰지 않는다 — 그건 어뷰징 규칙(같은 꽃 하루 한 번)이고 이건 표시 범위다.
         *    같은 상수를 쓰면 한쪽을 조정할 때 다른 쪽이 조용히 따라 움직인다.
         */
        const val RADIUS_METERS = 300
    }
}
