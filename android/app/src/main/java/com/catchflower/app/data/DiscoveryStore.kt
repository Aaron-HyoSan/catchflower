package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.core.Visibility
import com.catchflower.app.data.model.Discovery
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 발견 기록을 기기에 저장한다. iOS `DiscoveryStore`(actor · JSON · atomic)의 대응물이다.
 *
 * **왜 Room이 아닌가.** iOS가 SwiftData를 안 쓴 이유와 같다:
 * 1. 이 레코드는 **공유계약 1-3의 snake_case와 1:1**이다. `@Entity`로 바꾸면
 *    계약 필드명을 한 번 더 손으로 맞춰야 하고 **양쪽이 어긋날 자리가 하나 더 생긴다.**
 * 2. 서버(A-2 Supabase)가 붙으면 이 JSON이 **그대로 올라간다.** Room 엔티티는 변환이 한 번 더다.
 * 3. 한 사용자당 수천 건 규모다. 쿼리·관계·마이그레이션이 필요한 양이 아니다.
 *
 * Room이 필요해지는 시점은 **오프라인 동기화 큐**를 만들 때다. 그때 다시 판단한다.
 *
 * **파일 하나에 전부 쓴다.** 건당 파일로 쪼개면 200건에 200번 읽기가 된다.
 *
 * ⚠️ **kotlinx.serialization을 안 쓴다.** 직렬화 플러그인은 Kotlin 버전에 묶여 있는데
 *    이 프로젝트의 Kotlin은 **AGP 9에 내장**이라 버전을 따로 못 고른다
 *    (`org.jetbrains.kotlin.android` 선언 금지와 같은 이유). `org.json`은
 *    `PlantNetRecognizer`가 이미 쓰고 있고, 키를 손으로 쓰면 **계약 필드명이 코드에
 *    그대로 보인다** — 애노테이션 뒤에 숨는 것보다 이 경우엔 낫다.
 */
class DiscoveryStore(private val file: File) {

    /**
     * 읽기·쓰기 전체를 직렬화한다.
     *
     * ⚠️ **`save`만 잠그면 부족하다.** `append`는 읽기→추가→쓰기인데, 읽기가 락 밖이면
     *    두 코루틴이 같은 목록을 읽고 각자 1건씩 붙여 저장해서 **한 건이 사라진다.**
     *    (처음에 그렇게 썼다 — 주석으로 위험을 적어 놓고 코드는 그 위험을 그대로 뒀다.)
     *    Mutex는 재진입이 안 되므로 공개 함수만 잠그고 내부는 `*Unlocked`를 부른다.
     */
    private val lock = Mutex()

    companion object {
        private const val FILE_NAME = "discoveries.json"

        fun default(context: Context): DiscoveryStore =
            DiscoveryStore(File(context.filesDir, FILE_NAME))

        // ── 계약 1-3의 컬럼명. **여기가 유일한 원본이고 혼자 바꾸지 않는다.** ──
        private const val K_ID = "id"
        private const val K_USER_ID = "user_id"
        private const val K_FLOWER_ID = "flower_id"
        private const val K_PHOTO_URL = "photo_url"
        private const val K_LAT = "lat"
        private const val K_LNG = "lng"
        private const val K_PLACE_NAME = "place_name"
        private const val K_DONG_CODE = "dong_code"
        private const val K_GU_CODE = "gu_code"
        private const val K_VISIBILITY = "visibility"
        private const val K_AI_CONFIDENCE = "ai_confidence"
        private const val K_AI_PICKED_RANK = "ai_picked_rank"
        private const val K_IS_FIRST = "is_first_discovery"
        private const val K_CREATED_AT = "created_at"
        private const val K_CAPTURED_AT = "captured_at"
        private const val K_NOTE = "note"

        /**
         * ⚠️ **계약에 없는 필드다.** 업로드 전 로컬 사진 파일명이라 서버로 보내지 않는다.
         *    `photo_url`에 로컬 파일명을 넣으면 서버가 그걸 스토리지 키로 읽는다.
         *    `_` 접두사로 "로컬 전용"을 표시한다.
         */
        private const val K_LOCAL_PHOTO = "_local_photo_path"

        /**
         * ⚠️ **시각은 ISO-8601 문자열로 쓴다. epoch millis를 그대로 넣으면 안 된다.**
         *
         * 계약 1-3의 `created_at`·`captured_at`은 `timestamptz`이고, **iOS는
         * `dateEncodingStrategy = .iso8601`로 문자열을 쓴다.** AOS 모델은 `Long`이라
         * 그대로 직렬화하면 같은 컬럼에 `"2026-08-06T02:00:00Z"`와 `1780000000000`이
         * 섞여 올라간다 — **floor 0.20 vs 0.30과 똑같은 조용한 불일치다.**
         * 코드 내부는 각 언어 관용구(Long), **전송 형식은 계약**을 따른다.
         */
        internal fun encodeTime(epochMillis: Long): String =
            Instant.ofEpochMilli(epochMillis).toString()

        internal fun decodeTime(value: String): Long = Instant.parse(value).toEpochMilli()

        /**
         * 신뢰도를 사람이 읽을 수 있는 소수로 만든다.
         *
         * ⚠️ **`toDouble()`로는 안 된다.** 0.82f를 Double로 넓히면
         *    `0.8199999928474426`이 그대로 남는다 — Float가 애초에 그 값이기 때문이다.
         *    처음에 "Double로 넓힌다"고 주석까지 달아 놓고 이 값이 파일에 박혔다.
         *    **테스트가 잡았다** (`신뢰도가_지저분한_소수로_저장되지_않는다`).
         *
         * ⚠️ 왜 신경 쓰는가: iOS `JSONEncoder`는 같은 값을 `0.82`로 쓴다. 같은
         *    `numeric` 컬럼에 `0.82`와 `0.8199999928474426`이 섞이면
         *    **B-3 임계값을 실측으로 재채점할 때 두 플랫폼 분포가 달라 보인다.**
         *    0.30 floor 논의처럼 숫자로 판단하는 자리라서 형식이 곧 근거의 품질이다.
         *
         * 소수 4자리로 자른다 — PlantNet 점수는 소수 3~4자리가 유효하고,
         * [GamePolicy] 임계값(0.60·0.70·0.85·0.30)은 2자리다.
         */
        internal fun encodeConfidence(value: Float): Double =
            Math.round(value.toDouble() * 10_000.0) / 10_000.0

        /**
         * 서버로 보내는 형태. **[toJson]과 다르다.**
         *
         * 🔴 **로컬 전용 필드를 빼야 한다 — 안 빼면 전송이 400으로 전부 실패한다.**
         *    실측: `{"code":"PGRST204","message":"Could not find the
         *    '_local_photo_path' column of 'discoveries' in the schema cache"}`.
         *    "파일에 쓰는 JSON이 계약 컬럼명 그대로니 그대로 올리면 된다"고 문서에
         *    적어 뒀었는데, **`_local_photo_path` 한 칸 때문에 그게 틀렸다.**
         *    PostgREST는 모르는 컬럼을 무시하지 않고 요청 전체를 거부한다 —
         *    한 건도 안 올라가고, 이유는 로그에만 남는다.
         *
         * `captured_date`도 넣지 않는다. 서버 트리거가 채우고
         * **클라이언트가 날짜를 정하면 B-5 하루 1회를 우회할 수 있다**(0001 주석).
         */
        internal fun toWireJson(d: Discovery): JSONObject =
            toJson(d).apply { remove(K_LOCAL_PHOTO) }

        internal fun toJson(d: Discovery): JSONObject = JSONObject().apply {
            put(K_ID, d.id)
            put(K_USER_ID, d.userId)
            put(K_FLOWER_ID, d.flowerId)
            // ⚠️ `putOpt`는 값이 null이면 **키를 넣지 않는다.** 읽을 때 `orNull`로 되살린다.
            //    `JSONObject.NULL`을 쓰면 Supabase가 명시적 null로 받아 기존 값을 덮는다.
            putOpt(K_PHOTO_URL, d.photoUrl)
            putOpt(K_LOCAL_PHOTO, d.localPhotoPath)
            putOpt(K_LAT, d.lat)
            putOpt(K_LNG, d.lng)
            putOpt(K_PLACE_NAME, d.placeName)
            putOpt(K_DONG_CODE, d.dongCode)
            putOpt(K_GU_CODE, d.guCode)
            put(K_VISIBILITY, d.visibility.wire)
            // ⚠️ **`putOpt`가 아니라 `put`이다.** DB는 둘 다 `not null`이라
            //    키가 빠지면 서버가 `400 23502`로 거부하고, 400은 영구 거절이라
            //    그 기록은 **다시는 올라가지 않는다**(도감에는 보인다).
            put(K_AI_CONFIDENCE, encodeConfidence(d.aiConfidence))
            put(K_AI_PICKED_RANK, d.aiPickedRank)
            put(K_IS_FIRST, d.isFirstDiscovery)
            putOpt(K_NOTE, d.note)
            put(K_CREATED_AT, encodeTime(d.createdAt))
            put(K_CAPTURED_AT, encodeTime(d.capturedAt))
        }

        internal fun fromJson(o: JSONObject): Discovery = Discovery(
            id = o.getString(K_ID),
            userId = o.getString(K_USER_ID),
            flowerId = o.getInt(K_FLOWER_ID),
            photoUrl = o.orNull(K_PHOTO_URL),
            localPhotoPath = o.orNull(K_LOCAL_PHOTO),
            lat = if (o.has(K_LAT)) o.getDouble(K_LAT) else null,
            lng = if (o.has(K_LNG)) o.getDouble(K_LNG) else null,
            placeName = o.orNull(K_PLACE_NAME),
            dongCode = o.orNull(K_DONG_CODE),
            guCode = o.orNull(K_GU_CODE),
            visibility = Visibility.fromWire(o.getString(K_VISIBILITY)),
            // ⚠️ **기본값을 만들어 넣지 않는다.** 키가 없으면 `getDouble`이
            //    `JSONException`을 던지고 [loadUnlocked]가 파일을 격리한다.
            //    0.0 같은 기본값을 넣으면 **없는 신뢰도를 만들어 서버에 올리고**,
            //    B-3 임계값을 실측으로 재채점할 때 그 값이 분포를 오염시킨다.
            aiConfidence = o.getDouble(K_AI_CONFIDENCE).toFloat(),
            aiPickedRank = o.getInt(K_AI_PICKED_RANK),
            isFirstDiscovery = o.getBoolean(K_IS_FIRST),
            note = o.orNull(K_NOTE),
            createdAt = decodeTime(o.getString(K_CREATED_AT)),
            capturedAt = decodeTime(o.getString(K_CAPTURED_AT)),
        )

        /** 없는 키와 빈 문자열을 똑같이 null로 본다. `optString`은 없을 때 ""를 준다. */
        private fun JSONObject.orNull(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key).ifEmpty { null }
    }

    /** 전부 읽는다. 없으면 빈 목록. */
    suspend fun load(): List<Discovery> = withContext(Dispatchers.IO) {
        lock.withLock { loadUnlocked() }
    }

    suspend fun save(discoveries: List<Discovery>) = withContext(Dispatchers.IO) {
        lock.withLock { saveUnlocked(discoveries) }
    }

    /**
     * 한 건 추가하고 **갱신된 전체 목록**을 돌려준다.
     *
     * 전체를 돌려주는 이유: 호출부(ViewModel)가 "몇 번째 꽃인가"·"오늘 몇 번째인가"를
     * 바로 세야 한다. 저장만 하고 끝내면 화면이 다시 읽어야 하고, 그 사이가 어긋난다.
     */
    suspend fun append(discovery: Discovery): List<Discovery> = withContext(Dispatchers.IO) {
        lock.withLock {
            val updated = loadUnlocked() + discovery
            saveUnlocked(updated)
            updated
        }
    }

    /**
     * ⚠️ **깨진 파일에 조용히 빈 목록을 돌려주면 사용자 도감이 사라진 것처럼 보인다.**
     *    원본을 `.corrupt`로 치워 두고 빈 상태로 시작한다 — 최소한 복구는 가능하다.
     */
    private fun loadUnlocked(): List<Discovery> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: JSONException) {
            quarantine(e); emptyList()
        } catch (e: DateTimeParseException) {
            // 시각 형식이 계약과 다르다 (예: epoch millis로 쓰인 옛 파일).
            quarantine(e); emptyList()
        } catch (e: IllegalStateException) {
            // `Visibility.fromWire`가 모르는 값에 error()를 던진다 — 계약 위반 데이터다.
            quarantine(e); emptyList()
        }
    }

    private fun saveUnlocked(discoveries: List<Discovery>) {
        val arr = JSONArray()
        discoveries.forEach { arr.put(toJson(it)) }
        val text = arr.toString()
        // **원자적으로 쓴다.** 같은 파일에 바로 쓰다가 앱이 죽으면 반쪽 JSON이 남고,
        // 다음 실행에서 `quarantine`이 도감을 통째로 치운다.
        // 임시 파일 → rename은 같은 파일시스템에서 원자적이다.
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            // rename 실패는 드물지만 **삼키지 않는다** — `try?`로 삼켜서 조용히 저장이
            // 안 된 사고가 iOS에 있었다.
            android.util.Log.w("CatchFlower", "임시 파일 교체 실패 — 직접 쓴다")
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun quarantine(cause: Exception) {
        android.util.Log.e("CatchFlower", "발견 기록을 읽을 수 없다 — 격리한다", cause)
        val backup = File(file.parentFile, "${file.name}.corrupt")
        backup.delete()
        file.renameTo(backup)
    }
}

/**
 * 로그인 전까지 쓰는 기기 로컬 사용자 id.
 *
 * ⚠️ **이건 임시다.** 오너가 익명 로그인으로 결정했으므로, Supabase Anonymous provider가
 *    켜지면 그 계정의 uuid로 **교체된다**(콘솔 토글 대기 — `anonymous_provider_disabled`).
 *    그때 이 id로 저장된 기록을 새 id로 옮기는 이관이 필요하다.
 *
 * **계약이 uuid를 요구하므로 uuid로 만든다.** `"local-user"` 같은 문자열을 넣으면
 * 서버가 붙는 날 uuid 컬럼에 들어가지 못해 **그동안 쌓인 기록이 전부 못 올라간다.**
 */
object LocalUser {
    private const val PREFS = "catchflower"
    private const val KEY = "local_user_id"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}
