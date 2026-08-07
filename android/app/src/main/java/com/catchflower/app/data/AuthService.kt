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
) {

    /** HTTP를 갈아 끼울 수 있게 둔다 — 테스트가 네트워크를 부르면 안 된다. */
    interface Transport {
        suspend fun post(url: String, apiKey: String, body: String): Pair<Int, String>
    }

    /**
     * 로그인된 사용자 id를 준다. 없으면 만들고, 못 만들면 기기 로컬 uuid를 준다.
     *
     * 돌려주는 값은 **항상 uuid 문자열**이다 — 계약 1-3의 `user_id`가 uuid이므로
     * `"local-user"` 같은 값을 넣으면 서버가 붙는 날 그동안의 기록이 전부 못 올라간다.
     */
    suspend fun userId(): String {
        storedUserId()?.let { return it }
        if (!AppSecrets.hasSupabase) return LocalUser.id(context)

        val created = signUpAnonymously()
        if (created == null) {
            // 실패해도 진행한다. 다음 실행에서 다시 시도하고, 성공하면 migrate가 옮긴다.
            android.util.Log.w("CatchFlower", "익명 로그인 실패 — 기기 로컬 id로 계속한다")
            return LocalUser.id(context)
        }
        save(created)
        return created.userId
    }

    /** 로그인 토큰. 업로드가 붙으면 `Authorization: Bearer`로 쓴다. */
    fun accessToken(): String? = prefs().getString(K_TOKEN, null)?.ifEmpty { null }

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
        return runCatching {
            val o = JSONObject(body)
            val id = o.getJSONObject("user").getString("id")
            Session(userId = id, accessToken = o.optString("access_token").ifEmpty { null })
        }.getOrNull()
    }

    private fun save(session: Session) {
        prefs().edit()
            .putString(K_USER_ID, session.userId)
            .putString(K_TOKEN, session.accessToken ?: "")
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    data class Session(val userId: String, val accessToken: String?)

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

        private const val PREFS = "catchflower"
        private const val K_USER_ID = "auth_user_id"
        private const val K_TOKEN = "auth_access_token"
        private const val TIMEOUT_MS = 10_000
    }
}
