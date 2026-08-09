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
    /**
     * null이면 로그인을 시도하지 않는다 (테스트·오프라인 전용).
     *
     * ⚠️ **`internal val`이다** — 랭킹 조회([RankingService])가 **같은 인스턴스**를
     *    써야 한다. 새로 만들면 토큰 갱신이 서로 다른 prefs 인스턴스를 통해 일어나
     *    회전된 `refresh_token`을 덮어쓰고, **며칠 뒤 조용히 로그인이 끊긴다.**
     *    아래 [get]이 이미 같은 이유로 하나만 만들고 있다.
     */
    internal val auth: AuthAccount? = null,
    /** null이면 서버에 올리지 않는다 (테스트·오프라인 전용). */
    private val uploader: UploadSink? = null,
    /** [uploader]와 짝이다. 둘 중 하나만 있으면 업로드하지 않는다. */
    private val uploadState: UploadState? = null,
    /**
     * ⚠️ **`android.util.Log`를 직접 부르면 JVM 테스트에서 던진다**(스텁이다).
     *    [syncPending]의 보류 경로가 로그를 지나가므로, 직접 부르면
     *    **"일시 실패에 멈추는가"를 테스트할 수 없다** — 거기가 이 함수의 핵심이다.
     */
    private val log: (String) -> Unit = { android.util.Log.i("CatchFlower", it) },
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
        // 로그인 **뒤에** 올린다. 앞에 두면 기기 로컬 id로 보내서 RLS가 42501로
        // 전부 거부하고, 그 기록들이 `rejected`로 박혀 **다시는 올라가지 않는다.**
        syncPending()
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

        // 🔴 **갱신 수단 없는 계정을 버리고 새로 받는다.** 업로드 전 버전은
        //    `refresh_token`을 저장하지 않았다 — 그 계정의 토큰은 1시간이면 죽고
        //    익명 계정은 비밀번호가 없어 **다시 로그인할 방법이 없다.**
        //    그대로 두면 그 기기는 영구히 401이고 한 건도 안 올라간다(이 개발 기기가
        //    실제로 그 상태였다 · 진행.md (30)).
        //
        // ⚠️ **아직 아무것도 안 올렸을 때만 버린다.** 올린 게 있으면 그 데이터가
        //    주인 없이 남는다 — 새 계정으로는 RLS 때문에 보이지도 지우지도 못한다.
        //    옛 계정에 올라간 게 있는 기기는 그냥 둔다. 어차피 그 기록들은
        //    이미 서버에 있고, 새 기록만 못 올라간다.
        if (service.needsReauth() && uploadState?.uploaded()?.isEmpty() != false) {
            log("갱신 불가 계정을 버리고 새로 발급한다")
            service.reset()
        }

        val signedIn = runCatching { service.userId() }.getOrNull() ?: return
        if (signedIn == userId) return
        userId = signedIn
        val moved = AuthService.migrate(store, signedIn)
        if (moved > 0) {
            log("사용자 id 이관: ${moved}건")
            // 파일이 바뀌었으니 메모리도 맞춘다. 안 하면 화면이 옛 id의 기록을 들고 있어
            // 다음 저장에서 되돌려 쓴다.
            _discoveries.value = store.load()
            // ⚠️ **이관된 기록은 다시 올려야 한다.** `user_id`가 바뀌었으므로 서버에
            //    있는 행은 옛 계정 것이다. `uploaded`로 남겨 두면 새 계정에는
            //    **한 건도 없는데 앱은 다 올렸다고 믿는다** — 랭킹이 0종으로 나오고
            //    도감은 정상으로 보인다. upsert라 다시 보내도 무해하다.
            uploadState?.clearUploaded()
        }
    }

    /**
     * 한 건 등록한다. 파일과 메모리를 함께 갱신하고 **서버에 올린다.**
     *
     * ⚠️ **저장이 먼저다.** 업로드를 먼저 하면 네트워크가 느린 곳에서 화면 10이
     *    그만큼 늦게 뜬다 — 등록은 이미 끝난 일이고 사용자가 기다릴 이유가 없다.
     *
     * ⚠️ **업로드 실패가 등록을 취소하지 않는다.** 비행기 모드에서 찍은 꽃도
     *    도감에 들어가고, 다음 실행의 [syncPending]이 올린다.
     */
    suspend fun add(discovery: Discovery): List<Discovery> {
        val updated = store.append(discovery)
        _discoveries.value = updated
        push(discovery)
        return updated
    }

    /**
     * 못 올린 기록을 올린다. 앱 시작 시 [load] 끝에서 부른다.
     *
     * ⚠️ **한 건씩 보낸다. 배열로 묶지 않는다.**
     *    🔴 PostgREST 배열 삽입은 **모든 객체의 키 집합이 같아야 한다** —
     *    다르면 `PGRST102 All object keys must match`로 **요청 전체가 400**이다(실측).
     *    우리 기록은 `putOpt`가 null 키를 빼기 때문에 **좌표 있는 기록과 없는 기록의
     *    키 집합이 다르다.** 즉 실제 데이터로 묶으면 거의 항상 실패한다.
     *    빠진 키를 명시적 null로 채워 맞추는 방법도 있지만, 그러면 **한 건이 규칙
     *    위반일 때 배열 전체가 들어가지 않는다**(같은 트랜잭션 — 실측으로 확인했다).
     *    B-5 위반 한 건이 그날 기록 전부를 막는 건 받아들일 수 없다.
     *
     * @return 이번에 올린 건수
     */
    suspend fun syncPending(): Int {
        val up = uploader ?: return 0
        val state = uploadState ?: return 0
        val all = _discoveries.value
        val pendingIds = state.pending(all.map { it.id }).toSet()
        if (pendingIds.isEmpty()) return 0

        var sent = 0
        for (d in all.filter { it.id in pendingIds }) {
            when (val result = up.upload(d)) {
                is DiscoveryUploader.Result.Uploaded -> {
                    state.markUploaded(d.id)
                    sent++
                }
                is DiscoveryUploader.Result.Rejected -> state.markRejected(d.id)
                // ⚠️ 일시 실패는 **그냥 남겨 둔다.** 여기서 반복 재시도하면
                //    비행기 모드에서 수백 번 왕복을 시도한다. 다음 실행에 다시 온다.
                is DiscoveryUploader.Result.Failed -> {
                    log("업로드 보류 · HTTP ${result.code}")
                    // ⚠️ **break가 아니라 return이다.** 한 건이 네트워크로 실패하면
                    //    나머지도 실패한다 — 계속 돌면 왕복만 늘어난다.
                    return sent
                }
            }
        }
        return sent
    }

    /** 한 건 업로드. 실패는 [syncPending]이 나중에 처리한다. */
    private suspend fun push(discovery: Discovery) {
        val up = uploader ?: return
        val state = uploadState ?: return
        when (up.upload(discovery)) {
            is DiscoveryUploader.Result.Uploaded -> state.markUploaded(discovery.id)
            is DiscoveryUploader.Result.Rejected -> state.markRejected(discovery.id)
            is DiscoveryUploader.Result.Failed -> Unit // 다음 실행에 재시도한다
        }
    }

    /**
     * 화면 13 공개 범위·한 줄 수정. 파일·메모리를 갱신하고 **서버에 다시 올린다.**
     *
     * 🔴 **[push]를 빼면 공유가 이 기기 밖으로 나가지 않는다.** [syncPending]은
     *    `pending()`(= 아직 안 올린 id)만 보내므로, 이미 올라간 기록을 고쳐도
     *    **다시 보낼 후보에 들어가지 않는다.** 그러면 서버 행은 계속
     *    `visibility = 'private'` · `note = null`인데 앱은 `모두에게 공개`를 보여준다 —
     *    화면·파일·서버 셋 중 둘만 맞는 상태라 **기기에서는 아무 증상이 없다.**
     *
     * ⚠️ **[UploadState.clearUploaded]를 부르지 않는다.** 그건 한 건 고칠 때마다
     *    전 기록을 다시 올린다(수백 건 왕복). 업로드는 클라이언트가 만든 id로 하는
     *    upsert(`resolution=merge-duplicates`)라 **같은 건을 다시 보내는 것이 안전하고**,
     *    이미 `uploaded`인 id에 [UploadState.markUploaded]를 또 부르는 것도 집합 합집합이라 무해하다.
     *
     * ⚠️ **저장이 먼저다** ([add]와 같은 이유). 업로드를 기다리면 지하철에서
     *    `공유하기`를 눌러도 화면이 안 넘어간다 — 공개 범위는 이미 정해진 일이다.
     */
    suspend fun update(discovery: Discovery) {
        val updated = _discoveries.value.map { if (it.id == discovery.id) discovery else it }
        store.save(updated)
        _discoveries.value = updated
        push(discovery)
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
                instance ?: run {
                    val auth = AuthService(context.applicationContext)
                    DiscoveryRepository(
                        store = DiscoveryStore.default(context),
                        photos = PhotoStore.default(context),
                        userId = LocalUser.id(context),
                        auth = auth,
                        // **같은 [AuthService] 인스턴스를 넘긴다.** 새로 만들면 토큰 갱신이
                        // 서로 다른 prefs 인스턴스를 통해 일어나 회전된 refresh_token을
                        // 덮어쓸 수 있다 — 그러면 며칠 뒤 조용히 로그인이 끊긴다.
                        uploader = DiscoveryUploader(auth),
                        uploadState = UploadState.default(context),
                    ).also { instance = it }
                }
            }

        /** 테스트가 격리된 저장소를 넣을 수 있게. 프로덕션 코드에서 부르지 않는다. */
        internal fun resetForTest() {
            synchronized(this) { instance = null }
        }
    }
}
