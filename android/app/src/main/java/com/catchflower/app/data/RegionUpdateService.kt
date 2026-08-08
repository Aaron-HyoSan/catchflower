package com.catchflower.app.data

import com.catchflower.app.core.AppSecrets
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 고른 활동 지역을 `users`에 쓴다. 화면 02의 `{동명}으로 시작하기`가 부른다.
 *
 * ⚠️ **RPC가 아니라 테이블 PATCH다** — RLS `users_update_self`가 본인 행만 허용한다.
 *    [RankingService.myRegion]이 읽는 그 행이다.
 */
interface RegionUpdateSink {
    suspend fun save(candidate: RegionCandidate): RegionUpdateResult
}

/**
 * 저장 결과.
 *
 * 🔴 **[Rejected]와 [Failed]를 나눈다** — [DiscoveryUploader.Result]와 같은 이유다.
 *    둘 다 "안 저장됐다"인데 대응이 **반대**다:
 *    - [Failed] → 다시 시도한다 (네트워크·5xx)
 *    - [Rejected] → **다시 시도하면 안 된다** (6개월 규칙에 걸렸다)
 *
 *    하나로 뭉치면 화면 02가 `다시 시도` 버튼을 내밀고, 사용자는 눌러도 안 되는
 *    버튼을 계속 누른다. 6개월 규칙은 **눌러서 풀리는 것이 아니다.**
 */
sealed interface RegionUpdateResult {
    data object Saved : RegionUpdateResult

    /**
     * 서버가 **규칙으로** 거절했다.
     *
     * 실측 대상 코드:
     * - `P0001` = 0004 트리거의 6개월 규칙
     * - `23514` = 0004 check 제약(코드 형식·세 칸 불일치) → **앱에 버그가 있다는 신호**
     * - `PGRST204` = 계약에 없는 컬럼 이름
     */
    data class Rejected(val code: Int, val pgCode: String) : RegionUpdateResult

    /** 일시적 실패. 다시 시도한다. */
    data class Failed(val code: Int) : RegionUpdateResult

    /** 키 없는 빌드. 오류 문구도 띄우지 않는다. */
    data object NotConfigured : RegionUpdateResult
}

/**
 * Supabase 구현체.
 *
 * ## 🔴 `region_changed_at`을 **보내지 않는다**
 *
 * 0004 트리거가 `now()`로 채운다. 앱이 보내면 **7개월 전 날짜를 보내 6개월 규칙을
 * 우회할 수 있다**(0004 주석). 트리거가 덮으므로 보내도 무시되지만, 보내는 코드를
 * 두면 다음 사람이 "앱이 정하는 값"이라고 읽는다.
 *
 * ## ⚠️ 세 칸을 **항상 같이** 보낸다
 *
 * `dong_code`만 바꾸고 `gu_code`를 안 바꾸면 B-6 구 확장이 **옛 구로 묶인다** —
 * 오류 없이 랭킹에 남의 동네 사람이 섞인다. 0004의 `users_region_all_or_none`·
 * `users_gu_code_matches_dong`가 서버에서 한 번 더 막지만, **한 번에 보내는 것이
 * 원래 규칙**이라 [RegionCandidate]를 통째로 받는다(세 칸을 따로 받는 시그니처를
 * 만들지 않는다 — 그러면 호출부가 하나를 빠뜨릴 수 있다).
 */
class RegionUpdateService(
    /**
     * ⚠️ [AuthService]가 아니라 인터페이스다 — [DiscoveryUploader]와 같은 이유
     *    (생성자가 `Context`를 요구해서 JVM에서 만들 수 없다).
     *
     * ⚠️ **[TokenSource]가 아니라 [AuthAccount]다** — PATCH는 `?id=eq.{내 uuid}`
     *    필터가 필요하고 uuid는 `TokenSource`에 없다. 아래 URL 주석 참고.
     */
    private val auth: AuthAccount,
    private val baseUrl: String = AppSecrets.supabaseUrl,
    private val anonKey: String = AppSecrets.supabaseAnonKey,
    private val transport: Transport = HttpTransport,
    /** ⚠️ `android.util.Log`는 JVM 테스트에서 던진다 — 거절 경로가 전부 로그를 지난다. */
    private val log: (String) -> Unit = { android.util.Log.w("CatchFlower", it) },
) : RegionUpdateSink {

    interface Transport {
        suspend fun patch(
            url: String,
            apiKey: String,
            bearer: String,
            body: String,
        ): Pair<Int, String>
    }

    override suspend fun save(candidate: RegionCandidate): RegionUpdateResult {
        if (baseUrl.isEmpty() || anonKey.isEmpty()) return RegionUpdateResult.NotConfigured
        val token = auth.accessToken() ?: return RegionUpdateResult.Failed(401)

        // 🔴 **`?id=eq.{내 uuid}` 없이 PATCH하면 안 된다.** PostgREST는 필터 없는
        //    PATCH를 **테이블 전체 수정**으로 해석한다. RLS가 본인 행만 남기므로
        //    실제로 남의 행이 바뀌지는 않지만, `id`를 여기서 안 쓰려면 그 사실에
        //    의존해야 한다 — RLS 정책 한 줄이 바뀌면 전체가 덮인다.
        //    그래서 **필터를 명시한다.** `myRegion`이 select에 필터를 안 쓰는 것과
        //    다르다(읽기는 덮을 것이 없다).
        val userId = auth.userId()
        if (userId.isEmpty()) {
            log("내 uuid를 모른다 — 지역 저장을 보내지 않았다")
            return RegionUpdateResult.Failed(401)
        }
        val url = "$baseUrl/rest/v1/users?id=eq.$userId"
        val body = JSONObject().apply {
            put("region_name", candidate.regionName)
            put("dong_code", candidate.dongCode)
            put("gu_code", candidate.guCode)
        }.toString()

        val first = send(url, body, token)
        val res = if (first.first == 401) {
            val fresh = auth.refresh() ?: return RegionUpdateResult.Failed(401)
            send(url, body, fresh)
        } else {
            first
        }
        val (code, responseBody) = res
        if (code in 200..299) {
            // 🔴 **200이 "저장됐다"가 아니다.** PostgREST의 PATCH는 **필터에 맞는 행이
            //    없어도 200을 준다** — 본문만 `[]`다. 그리고 그게 실제로 일어난다:
            //    익명 로그인이 실패하면 [AuthAccount.userId]가 **기기 로컬 uuid**를
            //    돌려주고(`LocalUser.id` — 서버에 없는 값), `?id=eq.{그 uuid}`는
            //    0행을 고친다.
            //
            //    그러면 화면 02는 `동네 선택 완료`로 넘어가고, 랭킹은 **영원히 빈다.**
            //    사용자는 동네를 골랐다고 믿는데 서버에는 없고, 6개월 규칙 때문에
            //    다시 고를 화면도 안 열린다. **오류는 한 건도 안 난다.**
            //
            //    그래서 `Prefer: return=representation`을 붙여 **고쳐진 행을 세어**
            //    확인한다. 헤더를 지우면 이 검사가 무력해진다.
            if (!changedAnyRow(responseBody)) {
                log("지역 저장이 0행을 고쳤다 — 서버에 내 users 행이 없다(익명 로그인 실패?)")
                return RegionUpdateResult.Failed(code)
            }
            return RegionUpdateResult.Saved
        }

        val pgCode = runCatching { JSONObject(responseBody).optString("code") }.getOrDefault("")
        val message = runCatching { JSONObject(responseBody).optString("message") }
            .getOrDefault("")
        log("지역 저장 실패 · HTTP $code${if (pgCode.isEmpty()) "" else " · $pgCode"}")
        return if (pgCode in RULE_CODES) {
            // 🔴 **`23514`는 앱 버그다.** 0004 check 제약에 걸린 것이므로
            //    법정동 코드나 어긋난 gu_code를 보냈다는 뜻이다. 조용히 넘기면
            //    "저장이 안 되네" 로만 보이고 원인을 알 수 없다.
            if (pgCode == CHECK_VIOLATION) {
                log("지역 코드가 제약에 걸렸다 — 행정동 코드가 아닐 수 있다 · $message")
            }
            RegionUpdateResult.Rejected(code, pgCode)
        } else {
            RegionUpdateResult.Failed(code)
        }
    }

    /**
     * `return=representation` 응답이 **행을 하나라도 담고 있는가.**
     *
     * ⚠️ **읽을 수 없으면 `true`로 본다.** 형식이 바뀌었을 때 "저장 안 됨"으로
     *    단정하면 실제로 저장된 사용자에게 오류를 띄우고 **다시 저장을 시도하게**
     *    되는데, 그건 6개월 규칙을 소모한다. 헤더가 빠져 본문이 없는 경우
     *    (`204`)도 같은 이유로 통과시킨다 — 위 호출부 주석의 검사는 **0행을 확실히
     *    아는 경우**만 잡는 것이 목적이다.
     */
    private fun changedAnyRow(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return true
        return runCatching { org.json.JSONArray(trimmed).length() > 0 }.getOrDefault(true)
    }

    private suspend fun send(url: String, body: String, token: String): Pair<Int, String> =
        runCatching { transport.patch(url, anonKey, token, body) }.getOrElse { e ->
            if (e is CancellationException) throw e
            0 to ""
        }

    private companion object {
        /** 6개월 규칙(0004 트리거). */
        const val REGION_RULE = "P0001"

        /** 0004 check 제약. **앱이 잘못된 코드를 보냈다는 신호다.** */
        const val CHECK_VIOLATION = "23514"

        /** 컬럼 이름이 계약과 다르다. */
        const val UNKNOWN_COLUMN = "PGRST204"

        /**
         * 재시도해도 결과가 같은 코드들.
         *
         * ⚠️ **여기 없는 코드는 [RegionUpdateResult.Failed]다** — 모르는 실패를
         *    `Rejected`로 만들면 네트워크 문제인데 화면이 "규칙에 걸렸다"고 말한다.
         */
        val RULE_CODES = setOf(REGION_RULE, CHECK_VIOLATION, UNKNOWN_COLUMN)
    }

    internal object HttpTransport : Transport {
        override suspend fun patch(
            url: String,
            apiKey: String,
            bearer: String,
            body: String,
        ): Pair<Int, String> = withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                // 🔴 **`X-HTTP-Method-Override`로 우회하면 안 된다 — PostgREST는 그 헤더를
                //    무시한다. 실측했다.** 처음엔 `requestMethod = "POST"` +
                //    `X-HTTP-Method-Override: PATCH`로 썼는데, 서버는 그냥 **POST(= insert)**로
                //    처리한다. 이미 있는 내 행을 insert하려는 것이므로
                //    `users_insert_self` 정책의 WITH CHECK에 걸려 **403 `42501`**이 온다:
                //    `new row violates row-level security policy for table "users"`.
                //
                //    에뮬레이터에서 `연남동으로 시작하기`가 계속 실패해서 잡았다. 같은
                //    URL·본문·토큰을 **진짜 PATCH로** 보내면 200이고, 헤더 우회로 보내면
                //    403이다 — 두 요청을 나란히 재서 확인했다(진행.md (33)).
                //
                // ⚠️ 그러면서도 `HttpURLConnection`은 `requestMethod = "PATCH"`를
                //    `ProtocolException`으로 거부한다(알려진 제약). 그래서 **밑에 있는
                //    구현체의 필드를 직접 고쳐** 실제 메서드를 PATCH로 만든다.
                //    실패하면 예외가 밖으로 나가고 호출부가 [RegionUpdateResult.Failed]로
                //    받는다 — **조용히 POST로 되돌아가지 않는다.** 되돌아가면 위의 403이
                //    다시 시작되고, 그건 "저장했다고 믿는데 안 된" 상태로 이어진다.
                forcePatchMethod(this)
                setRequestProperty("apikey", apiKey)
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
                // 응답 본문을 받아야 pgCode를 읽을 수 있다. 기본은 본문이 없다(204).
                setRequestProperty("Prefer", "return=representation")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            try {
                // ⚠️ **보낸 메서드를 남긴다.** 이 자리에서 POST가 나가면 insert가 되어
                //    403 `42501`이 오는데, 그 응답만 보면 RLS 정책 문제로 읽힌다 —
                //    실제로는 **메서드가 안 바뀐 것**이다. 한 줄 남기면 그 둘이 구분된다.
                android.util.Log.i(
                    "CatchFlower",
                    "지역 저장 요청 · ${conn.requestMethod} · ${conn.javaClass.simpleName}",
                )
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                conn.disconnect()
            }
        }

        /**
         * 실제 HTTP 메서드를 `PATCH`로 만든다. **아니면 던진다.**
         *
         * ## 실측: 안드로이드는 `PATCH`를 받아 준다
         *
         * `java.net.HttpURLConnection.setRequestMethod`이 허용 목록으로 `PATCH`를
         * 막는다는 것은 **호스트 JVM 이야기다.** 에뮬레이터에서 실제로 재 보니
         * 그냥 통과했다 — 로그: `지역 저장 요청 · PATCH · HttpsURLConnectionImpl`.
         * 그래서 이 함수는 **설정하고 확인하는 것**이 전부다.
         *
         * ⚠️ **리플렉션으로 `method` 필드를 갈아타는 코드를 다시 넣지 마라.**
         *    한 번 그렇게 썼다가 지웠다. 두 가지 이유다:
         *    ① 안드로이드에서는 필요가 없다(위 실측).
         *    ② 호스트 JVM에서는 **되지도 않는다** — `InaccessibleObjectException:
         *       module java.base does not "opens java.net"`. 즉 그 코드는
         *       **어떤 테스트도 실행할 수 없는 분기**가 된다.
         *    그러면 `RegionUpdateVerbTest`가 지키는 것이 사라진다.
         */
        internal fun forcePatchMethod(conn: HttpURLConnection) {
            // 거부하는 구현체가 있으므로 예외는 삼킨다 — 판단은 아래 [check]가 한다.
            runCatching { conn.requestMethod = "PATCH" }
            // 🔴 **막히면 던진다. POST로 되돌아가지 않는다.** POST로 보내면 PostgREST가
            //    insert로 처리해 403 `42501`이 오고, 그 응답만 보면 **RLS 정책 문제로
            //    읽힌다** — 실제로는 메서드가 안 바뀐 것이다. 그 오진에 시간을 썼다.
            check(conn.requestMethod == "PATCH") {
                "메서드가 ${conn.requestMethod}다 — POST로 보내면 insert가 되어 42501이 온다"
            }
        }

        private const val TIMEOUT_MS = 15_000
    }
}
