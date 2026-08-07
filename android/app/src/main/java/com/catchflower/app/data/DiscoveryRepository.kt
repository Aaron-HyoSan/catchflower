package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.data.model.Discovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 발견 기록의 **유일한 창구**. iOS `AppSession`의 저장 부분에 대응한다.
 *
 * **왜 싱글턴인가.** 촬영 흐름([com.catchflower.app.ui.capture.CaptureViewModel])과
 * 도감([com.catchflower.app.ui.dex.DexViewModel])은 **별개 ViewModel**이다.
 * 각자 [DiscoveryStore]를 들면 촬영으로 등록한 꽃이 도감에 나타나지 않는다 —
 * 앱을 껐다 켜야 보인다. 그건 "저장은 되는데 화면이 안 바뀐다"로 보여서
 * 저장 버그처럼 읽히지도 않는다.
 *
 * **메모리가 진실이고 파일은 그 사본이다.** 화면은 [discoveries]를 읽고,
 * 쓰기는 여기를 통해서만 한다. 서버(A-2)가 붙으면 [store] 자리에 업로드가 들어간다 —
 * 화면 코드는 건드리지 않는다.
 *
 * ⚠️ `StateFlow`로 내보낸다. `mutableStateOf`를 쓰면 Compose 없는 JVM 테스트에서
 *    못 읽는다 — 이 층은 **테스트로 고정해야 하는 층**이다.
 */
class DiscoveryRepository(
    private val store: DiscoveryStore,
    val photos: PhotoStore,
    /**
     * 기록에 박히는 사용자 id.
     *
     * ⚠️ **`val`이 아니다.** 익명 로그인은 네트워크라서 첫 프레임에는 결과가 없다.
     *    로그인을 기다리게 하면 비행기 모드에서 도감이 안 열리므로, 기기 로컬 uuid로
     *    시작해서 **로그인이 되면 갈아탄다**([load]가 [AuthService.migrate]로
     *    그동안의 기록을 옮긴다).
     */
    userId: String,
    /** null이면 로그인을 시도하지 않는다 (테스트·오프라인 전용). */
    private val auth: AuthService? = null,
) {

    var userId: String = userId
        private set

    private val _discoveries = MutableStateFlow<List<Discovery>>(emptyList())
    val discoveries: StateFlow<List<Discovery>> = _discoveries.asStateFlow()

    /**
     * 아직 파일을 읽었는가.
     *
     * ⚠️ **이게 없으면 첫 프레임에서 도감이 0종으로 보인다.** 화면 22(빈 상태)는
     *    `collectedCount == 0`으로 분기하므로, 로딩 중과 진짜 0종이 구분되지 않아
     *    **200종을 모은 사용자에게 `처음이라면 이 꽃부터`가 깜빡인다.**
     */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /**
     * 파일에서 읽어 메모리를 채운다. 앱 시작 시 한 번 부른다.
     *
     * ⚠️ **두 번 불러도 안전해야 한다.** ViewModel 두 개가 각자 `init`에서 부른다
     *    (어느 쪽이 먼저 만들어지는지는 화면 진입 순서에 달렸다). 파일이 진실이므로
     *    두 번 읽어도 결과가 같지만, **이미 읽었으면 다시 읽지 않는다** —
     *    로드 중 사이에 등록된 기록을 파일 내용으로 덮어쓸 수 있다.
     */
    suspend fun load() {
        if (_loaded.value) return
        val loaded = store.load()
        _discoveries.value = loaded
        _loaded.value = true

        // 로그인은 **읽기를 끝낸 뒤에** 한다. 앞에 두면 네트워크가 느린 곳에서
        // 도감이 그만큼 늦게 열린다 — 로그인은 지금 화면에 필요한 게 아니다.
        signIn()
    }

    /**
     * 익명 로그인 → id 교체 → 기록 이관.
     *
     * ⚠️ **이관을 빼면 로그인이 붙는 날 그동안의 기록이 서버에 못 올라간다.**
     *    `user_id`가 다른 계정 것이어서 RLS(`auth.uid() = user_id`)가 거부하는데,
     *    화면에는 도감이 그대로 보여서 아무 증상이 없다.
     */
    private suspend fun signIn() {
        val service = auth ?: return
        val signedIn = runCatching { service.userId() }.getOrNull() ?: return
        if (signedIn == userId) return
        userId = signedIn
        val moved = AuthService.migrate(store, signedIn)
        if (moved > 0) {
            android.util.Log.i("CatchFlower", "사용자 id 이관: ${moved}건")
            // 파일이 바뀌었으니 메모리도 맞춘다. 안 하면 화면이 옛 id의 기록을 들고 있어
            // 다음 저장에서 되돌려 쓴다.
            _discoveries.value = store.load()
        }
    }

    /** 한 건 등록한다. 파일과 메모리를 함께 갱신한다. */
    suspend fun add(discovery: Discovery): List<Discovery> {
        val updated = store.append(discovery)
        _discoveries.value = updated
        return updated
    }

    /** 화면 13 공개 범위·한 줄 수정. */
    suspend fun update(discovery: Discovery) {
        val updated = _discoveries.value.map { if (it.id == discovery.id) discovery else it }
        store.save(updated)
        _discoveries.value = updated
    }

    /**
     * 기록을 지운다. **사진 파일도 같이 지운다** —
     * 안 지우면 참조 없는 사진이 기기에 계속 쌓인다.
     */
    suspend fun delete(id: String) {
        val target = _discoveries.value.firstOrNull { it.id == id } ?: return
        val updated = _discoveries.value.filterNot { it.id == id }
        store.save(updated)
        _discoveries.value = updated
        target.localPhotoPath?.let { photos.delete(it) }
    }

    /**
     * 기록에 없는 사진 파일을 정리한다.
     *
     * ⚠️ **[loaded]가 true가 되기 전에 부르면 사진을 전부 날린다.** 기록을 아직
     *    못 읽은 상태에서는 "참조되지 않는 사진"이 전부이기 때문이다.
     *    [PhotoStore.pruneExcept]가 빈 집합을 거부하지만 여기서 한 번 더 막는다.
     */
    suspend fun prunePhotos(): Int {
        if (!_loaded.value) return 0
        val keep = _discoveries.value.mapNotNullTo(HashSet()) { it.localPhotoPath }
        if (keep.isEmpty()) return 0
        return photos.pruneExcept(keep)
    }

    companion object {
        @Volatile
        private var instance: DiscoveryRepository? = null

        fun get(context: Context): DiscoveryRepository =
            instance ?: synchronized(this) {
                instance ?: DiscoveryRepository(
                    store = DiscoveryStore.default(context),
                    photos = PhotoStore.default(context),
                    userId = LocalUser.id(context),
                    auth = AuthService(context.applicationContext),
                ).also { instance = it }
            }

        /** 테스트가 격리된 저장소를 넣을 수 있게. 프로덕션 코드에서 부르지 않는다. */
        internal fun resetForTest() {
            synchronized(this) { instance = null }
        }
    }
}
