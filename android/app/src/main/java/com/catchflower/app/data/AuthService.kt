package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 업로드가 실제로 쓰는 것은 **토큰 두 함수뿐이다.**
 *
 * ⚠️ **이 인터페이스가 없으면 업로드 층을 JVM 테스트로 고정할 수 없다.**
 *    [AuthService]는 생성자가 `Context`를 요구하므로(SharedPreferences) JVM에서
 *    만들 수 없고, 상속으로 흉내내도 생성자가 먼저 걸린다. 그러면 업로드 층의
 *    검증이 **전부 에뮬레이터 필요**가 되는데, 401 갱신·RLS 거절 같은 경우는
 *    기기에서 **재현할 방법이 사실상 없다**(토큰을 만료시켜야 한다).
 *
 * ⚠️ **최상위에 둔다.** `AuthService` 안에 중첩하고 `AuthService`가 그걸 구현하면
 *    Kotlin이 상위 타입 순환으로 거부한다.
 */
interface TokenSource {
    /** 유효한 액세스 토큰. 없으면 null. */
    suspend fun accessToken(): String?

    /** 강제로 갱신한다. 갱신 수단이 없으면 null. */
    suspend fun refresh(): String?
}

/**
 * [DiscoveryRepository]가 쓰는 계정 기능. [TokenSource]와 같은 이유로 존재한다.
 *
 * ⚠️ 특히 **id 이관은 계정당 한 번뿐인 사건**이다. 기기에서 재현하려면 계정을
 *    갈아야 하고, 틀려도 증상이 **랭킹 0종**뿐이라 눈에 띄지 않는다.
 *    JVM 테스트로 고정해야 하는 대표적인 자리다.
 */
interface AuthAccount : TokenSource {
    suspend fun userId(): String
    fun needsReauth(): Boolean
    fun reset()
}

/**
 * 익명 로그인. **오너 결정: "누구나 바로 플레이"이면서 계정은 있어야 한다.**
 *
 * Supabase GoTrue `POST /auth/v1/signup` (`{"data":{}}` · 익명 provider)로
 * 계정을 만들고 uuid·토큰을 기기에 보관한다. 화면 01·02(카카오/애플)는 나중에
 * **`계정 연결하기`**(기기 이동)로 붙는다 — 그때 이 uuid가 그대로 승격된다.
 *
 * ⚠️ **로그인 실패가 플레이를 막지 않는다.** 비행기 모드에서 앱을 처음 켠 사람도
 *    도감을 쓸 수 있어야 한다. 실패하면 [LocalUser]의 기기 로컬 uuid로 계속 쓰고,
 *    다음 실행에서 다시 시도한다 — 그때 [migrate]가 그동안의 기록을 옮긴다.
 *
 * ⚠️ **`service_role` 키를 쓰지 않는다.** anon(publishable) 키로만 부른다.
 *    익명 로그인은 anon 키로 되는 것이 정상이고, service_role은 클라이언트에
 *    두면 안 된다(오너 규칙).
 */
class AuthService(
    private val context: Context,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /**
     * ⚠️ **테스트가 바꿔 끼운다.** 기본값을 쓰면 실기기에서 테스트를 돌릴 때
     *    **사용자의 계정이 테스트 계정으로 덮인다** — 그 기기의 도감이 다른 계정 것이 되고,
     *    되돌릴 방법이 없다.
     */
    private val prefsName: String = PREFS,
) : AuthAccount {

    /** HTTP를 갈아 끼울 수 있게 둔다 — 테스트가 네트워크를 부르면 안 된다. */
    interface Transport {
        suspend fun post(url: String, apiKey: String, body: String): Pair<Int, String>
    }

    /**
     * 서버 기능을 쓸 수 있는가. **[AppSecrets]가 아니라 주입된 값으로 판단한다.**
     *
     * ⚠️ 전역을 보면 **주입이 무의미해진다** — 테스트가 baseUrl을 넣어도 전역이 비어 있으면
     *    아무것도 안 하고, 반대로 빈 baseUrl을 넣어도 전역이 채워져 있으면 실호출을 한다.
     *    즉 **테스트 결과가 그 맥에 `local.properties`가 있느냐로 갈린다.**
     */
    private val configured: Boolean get() = baseUrl.isNotEmpty() && anonKey.isNotEmpty()

    /**
     * 로그인된 사용자 id를 준다. 없으면 만들고, 못 만들면 기기 로컬 uuid를 준다.
     *
     * 돌려주는 값은 **항상 uuid 문자열**이다 — 계약 1-3의 `user_id`가 uuid이므로
     * `"local-user"` 같은 값을 넣으면 서버가 붙는 날 그동안의 기록이 전부 못 올라간다.
     */
    override suspend fun userId(): String {
        storedUserId()?.let { return it }
        if (!configured) return LocalUser.id(context)

        val created = signUpAnonymously()
        if (created == null) {
            // 실패해도 진행한다. 다음 실행에서 다시 시도하고, 성공하면 migrate가 옮긴다.
            android.util.Log.w("CatchFlower", "익명 로그인 실패 — 기기 로컬 id로 계속한다")
            return LocalUser.id(context)
        }
        save(created)
        return created.userId
    }

    /**
     * 업로드에 쓸 **유효한** 액세스 토큰. 만료가 가까우면 갱신해서 준다.
     *
     * 🔴 **토큰은 1시간(`expires_in: 3600`)만 산다 — 실측했다.** 처음에는 받은 토큰을
     *    그대로 보관하고 그게 끝이었다. 그러면 앱을 켜 둔 채 한 시간이 지나면
     *    **그때부터 모든 업로드가 401이 되고, 화면에는 아무 증상이 없다** —
     *    도감은 로컬에서 잘 보이고 실패는 로그에만 남는다. 하루 뒤에 켜면 첫 업로드부터
     *    401이라 **한 건도 안 올라간다.**
     *
     * ⚠️ **만료 판단에 서버가 준 `expires_at`(절대 시각)을 쓰지 않는다.** 그건 서버 시계
     *    기준이고 비교는 기기 시계로 한다. 기기 시계가 2시간 느리면 만료된 토큰을
     *    "아직 2시간 남았다"로 읽어서 401을 맞는다. **받은 순간 + `expires_in`**으로
     *    기기 시계 안에서 일관되게 센다 — 절대 오차가 있어도 경과 시간은 맞는다.
     *
     * ⚠️ 그래도 **401은 여전히 날 수 있다**(시계 변경·계정 정리·서버 폐기).
     *    그래서 호출부는 401을 받으면 [refresh]를 한 번 더 시도한다 —
     *    선제 갱신만으로는 부족하다.
     *
     * @return 유효한 토큰. 갱신할 방법이 없으면 null.
     */
    override suspend fun accessToken(): String? {
        val p = prefs()
        val token = p.getString(K_TOKEN, null)?.ifEmpty { null }
        val expiresAt = p.getLong(K_EXPIRES_AT, 0L)
        // 만료 직전에 보낸 요청이 서버에 닿을 때 이미 만료돼 있을 수 있다. 여유를 둔다.
        if (token != null && System.currentTimeMillis() < expiresAt - EXPIRY_MARGIN_MS) return token
        return refresh()
    }

    /**
     * `refresh_token`으로 새 액세스 토큰을 받는다.
     *
     * 🔴 **`refresh_token`은 쓸 때마다 새 값으로 바뀐다(회전) — 실측했다.**
     *    응답의 새 값을 저장하지 않으면 **다음 갱신부터 영구히 실패한다.**
     *    (옛 값도 잠깐은 통하는 재사용 창이 있어서 **바로는 증상이 안 나온다** —
     *    며칠 뒤에 조용히 로그인이 끊긴다. 이게 저장을 빼먹기 쉬운 이유다.)
     *
     * @return 새 액세스 토큰. `refresh_token`이 없거나 거부되면 null.
     */
    override suspend fun refresh(): String? {
        val stored = prefs().getString(K_REFRESH, null)?.ifEmpty { null } ?: return null
        val url = "$baseUrl/auth/v1/token?grant_type=refresh_token"
        val (code, body) = runCatching {
            transport.post(url, anonKey, JSONObject().put("refresh_token", stored).toString())
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            return null
        }
        if (code != 200) {
            val reason = runCatching { JSONObject(body).optString("error_code") }.getOrDefault("")
            android.util.Log.w("CatchFlower", "토큰 갱신 응답 $code${if (reason.isEmpty()) "" else " · $reason"}")
            return null
        }
        val session = runCatching { parseSession(JSONObject(body)) }.getOrNull() ?: return null
        save(session)
        return session.accessToken
    }

    /**
     * 저장된 세션을 버린다.
     *
     * ⚠️ **복구 경로 전용이다.** 부르면 다음 [userId] 호출이 **새 계정을 만든다** —
     *    이미 서버에 올라간 기록이 있으면 그 기록은 **주인 없는 데이터가 된다**
     *    (새 계정으로는 RLS 때문에 보이지도, 지우지도 못한다).
     *    그래서 호출부가 "아직 아무것도 안 올렸다"를 확인한 뒤에만 부른다.
     */
    override fun reset() {
        prefs().edit().remove(K_USER_ID).remove(K_TOKEN).remove(K_REFRESH)
            .remove(K_EXPIRES_AT).apply()
    }

    /**
     * 계정은 있는데 **갱신 수단이 없는가.**
     *
     * 🔴 **실측: 이 기기가 그 상태였다.** 업로드를 붙이기 전 버전은
     *    `access_token`만 저장하고 `refresh_token`을 버렸다. 그 토큰은 1시간이면 죽고,
     *    익명 계정은 비밀번호가 없어서 **다시 로그인할 방법이 없다.**
     *    그대로 두면 그 기기는 **영구히 401**이고 한 건도 안 올라간다 —
     *    화면에는 도감이 정상으로 보여서 아무 증상이 없다.
     *
     * 복구는 [reset] + 새 계정 발급뿐이다. 그건 옛 계정의 서버 데이터를 버리는
     * 행위라서, **아직 아무것도 안 올렸을 때만** 해도 된다 — 판단은 호출부가 한다.
     */
    override fun needsReauth(): Boolean {
        val p = prefs()
        val hasAccount = p.getString(K_USER_ID, null)?.isNotEmpty() == true
        val hasRefresh = p.getString(K_REFRESH, null)?.isNotEmpty() == true
        return hasAccount && !hasRefresh
    }

    private fun storedUserId(): String? =
        prefs().getString(K_USER_ID, null)?.ifEmpty { null }

    private suspend fun signUpAnonymously(): Session? {
        val url = "$baseUrl/auth/v1/signup"
        val (code, body) = runCatching {
            transport.post(url, anonKey, "{\"data\":{}}")
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            return null
        }
        if (code != 200) {
            // ⚠️ **본문을 로그에 그대로 찍지 않는다** — 성공 응답에는 토큰이 들어 있다.
            //    실패 코드와 GoTrue `error_code`만 남긴다.
            val reason = runCatching { JSONObject(body).optString("error_code") }.getOrDefault("")
            android.util.Log.w("CatchFlower", "익명 로그인 응답 $code${if (reason.isEmpty()) "" else " · $reason"}")
            return null
        }
        return runCatching { parseSession(JSONObject(body)) }.getOrNull()
    }

    private fun save(session: Session) {
        prefs().edit()
            .putString(K_USER_ID, session.userId)
            .putString(K_TOKEN, session.accessToken ?: "")
            // 회전된 값을 반드시 덮어쓴다. 안 쓰면 다음 갱신부터 영구 실패한다.
            .putString(K_REFRESH, session.refreshToken ?: "")
            .putLong(K_EXPIRES_AT, session.expiresAt)
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    data class Session(
        val userId: String,
        val accessToken: String?,
        val refreshToken: String? = null,
        /** 기기 시계 기준 만료 시각(ms). 0이면 모른다 = 즉시 갱신 대상. */
        val expiresAt: Long = 0L,
    )

    /** 실물 HTTP. 계측 테스트가 실호출에 쓸 수 있게 `internal`이다. */
    internal object HttpTransport : Transport {
        override suspend fun post(
            url: String,
            apiKey: String,
            body: String,
        ): Pair<Int, String> = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                // ⚠️ 2xx가 아니면 `inputStream`을 읽으면 던진다 — 그러면 모든 상태 코드가
                //    "네트워크 실패"로 뭉개져서 원인을 알 수 없다.
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {

        /**
         * 기기 로컬 id로 저장된 기록을 로그인 id로 옮긴다.
         *
         * ⚠️ **이관이 없으면 로그인이 붙는 날 그동안의 기록이 사라진 것과 같다.**
         *    파일에는 남아 있지만 `user_id`가 다른 계정 것이어서 **서버에 올릴 수 없고**,
         *    RLS(`auth.uid() = user_id`)가 거부한다. 화면에는 도감이 그대로 보이므로
         *    **아무도 이 사고를 알아채지 못한다** — 업로드를 붙이는 날 처음 드러난다.
         *
         * ⚠️ **멱등해야 한다.** 매 실행마다 불리고, 이미 옮긴 기록은 건드리지 않는다.
         *
         * **`Context`를 받지 않는다.** 저장소만 만지는 규칙이라 안드로이드에 의존할
         * 이유가 없고, 의존하면 **JVM 테스트로 고정할 수 없다** — 이관은 한 번뿐인
         * 사건이라 기기에서 재현해 확인할 기회가 사실상 없다.
         * (처음에 인스턴스 메서드로 썼다가 테스트가 이 결합을 드러냈다.)
         *
         * @return 옮긴 기록 수
         */
        suspend fun migrate(store: DiscoveryStore, toUserId: String): Int {
            val records = store.load()
            val staleCount = records.count { it.userId != toUserId }
            if (staleCount == 0) return 0
            store.save(
                records.map { if (it.userId == toUserId) it else it.copy(userId = toUserId) },
            )
            // ⚠️ **여기서 로그를 찍지 않는다.** `android.util.Log`는 JVM에서 스텁이라
            //    던진다 — 이 함수를 JVM 테스트로 고정할 수 없게 된다. 로그는 호출부가 한다.
            return staleCount
        }

        /**
         * signup·refresh 응답을 세션으로 바꾼다. **두 응답의 형식이 같다**(실측).
         *
         * ⚠️ **`expires_at`을 쓰지 않고 `expires_in`으로 계산한다.** 이유는
         *    [accessToken] 주석에 있다 — 기기 시계와 서버 시계를 섞으면 안 된다.
         *
         * **`Context`를 받지 않는다.** JVM 테스트로 고정하기 위한 것이고
         * ([migrate]와 같은 이유), 토큰 수명 계산은 화면 없이 검증해야 하는 규칙이다.
         */
        internal fun parseSession(o: JSONObject, now: Long = System.currentTimeMillis()): Session {
            val expiresIn = o.optLong("expires_in", 0L)
            return Session(
                userId = o.getJSONObject("user").getString("id"),
                // [stringOrNull]이다 — 토큰이 JSON `null`로 오면 안드로이드에서는
                // 문자열 `"null"`이 되어 **`Bearer null`을 헤더에 실어 보낸다.**
                // 401이 아니라 "토큰이 있는데 거부당했다"로 보여서 원인을 못 찾는다.
                accessToken = o.stringOrNull("access_token"),
                refreshToken = o.stringOrNull("refresh_token"),
                // `expires_in`이 없으면 0을 남긴다 — "만료 시각을 모른다"는
                // **곧 만료됐다로 취급**해야 안전하다. 먼 미래를 넣으면 401을 맞는다.
                expiresAt = if (expiresIn > 0L) now + expiresIn * 1000L else 0L,
            )
        }

        private const val PREFS = "catchflower"
        private const val K_USER_ID = "auth_user_id"
        private const val K_TOKEN = "auth_access_token"
        private const val K_REFRESH = "auth_refresh_token"
        private const val K_EXPIRES_AT = "auth_expires_at"
        private const val TIMEOUT_MS = 10_000

        /**
         * 만료 여유(ms). 실측 수명이 3600초라 5분을 뺀다.
         *
         * ⚠️ 0으로 두면 **"아직 1초 남았다"로 판단한 요청이 서버에 닿을 때 만료돼 있다.**
         *    업로드는 사진 없이도 왕복이 있고, 지하철에서는 그 왕복이 몇 초다.
         */
        private const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
    }
}
