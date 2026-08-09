package com.catchflower.app.ui.map

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catchflower.app.data.DiscoveryRepository
import com.catchflower.app.data.FlowerRepository
import kotlinx.coroutines.launch

/**
 * 화면 14 지도 홈.
 *
 * 🔴 **생성자에 인자를 더 붙일 때는 `@JvmOverloads`를 같이 붙여야 한다.**
 *    `AndroidViewModelFactory`는 리플렉션으로 `(Application)` **단일 인자** 생성자를
 *    찾는데, 코틀린 기본값은 그 생성자를 만들지 않는다 — 화면 07·17·02에서 세 번
 *    당했다. **컴파일은 통과하고 탭을 누르는 순간 죽는다.**
 *    지금은 인자가 `app` 하나뿐이라 그 생성자가 저절로 있어서 안 붙였다
 *    (붙이면 "기본값 없는 생성자에는 효과 없음" 경고가 난다).
 *
 * 🔴 **프로퍼티 선언을 [init] 아래로 내리지 마라** — 화면 17에서 겪은 그 사고다
 *    (`NullPointerException … MutableState.setValue`). `init`이 코루틴을 띄우기 전에
 *    프로퍼티가 이미 초기화돼 있어야 한다.
 *
 * ## ⚠️ JVM 테스트가 이 클래스를 **만들 수 없다**
 *
 * 그래서 판단은 전부 [MapPins]에 있다. 여기 남은 것은 조립뿐이고,
 * **에뮬레이터로만 확인된다.**
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val discoveries = DiscoveryRepository.get(app)
    private val flowers = FlowerRepository.get(app)

    /**
     * 지도에 찍을 핀.
     *
     * ⚠️ **`loading`과 구분한다.** 읽는 중을 빈 목록으로 그리면 기록이 있는
     *    사용자에게 `아직 이 근처에 공유된 꽃이 없어요`가 한 프레임 깜빡인다.
     */
    var pins by mutableStateOf<List<MapPin>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    /** 사용자가 누른 핀. null이면 프리뷰 카드를 그리지 않는다. */
    var selected by mutableStateOf<MapPin?>(null)
        private set

    init {
        viewModelScope.launch {
            // 도감과 **같은 저장소**를 읽는다 — 각자 읽으면 도감에 있는 꽃이
            // 지도에 없을 수 있고, 그건 "등록이 반만 됐다"로 보인다.
            discoveries.load()
            discoveries.discoveries.collect { records ->
                pins = MapPins.from(records)
                loading = false
                // ⚠️ 고른 핀을 **새 목록에서 다시 찾는다.** 안 하면 방금 등록한 기록이
                //    붙은 핀을 열어 둔 채로 옛 기록 수(`기록 3개`)가 계속 보인다.
                selected = selected?.let { old -> pins.firstOrNull { it.id == old.id } }
            }
        }
    }

    fun onPinClick(pin: MapPin) {
        selected = pin
    }

    /** 핀이 아닌 곳을 누르면 카드를 닫는다. */
    fun onMapClick() {
        selected = null
    }

    /**
     * 프리뷰 카드 둘째 줄 `장미, 개망초, 금계국 외 2종`.
     *
     * ⚠️ **도감에서 못 찾은 id는 빼고 넘긴다.** 빈 이름을 넘기면 `외 N종`의 숫자가
     *    화면에 보이는 이름 개수와 어긋난다([MapPins.flowerSummary] 주석).
     */
    fun flowerSummary(pin: MapPin): String =
        MapPins.flowerSummary(pin.flowerIdsRecentFirst.mapNotNull { flowers.byId(it)?.name })
}
