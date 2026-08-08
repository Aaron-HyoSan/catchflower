package com.catchflower.app.data

import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 지역 저장이 **진짜 PATCH로 나가는가.**
 *
 * ## 🔴 왜 이 파일이 있어야 하는가 — 이 결함은 초록 테스트를 통과해서 배포됐다
 *
 * 처음 구현은 `requestMethod = "POST"` + `X-HTTP-Method-Override: PATCH`였다.
 * **PostgREST는 그 헤더를 무시한다 — 실측했다.** 그래서 요청은 그냥 POST(= insert)로
 * 처리되고, 이미 있는 내 행을 insert하려는 것이므로 `users_insert_self` 정책의
 * WITH CHECK에 걸려 **403 `42501`**이 온다:
 * `new row violates row-level security policy for table "users"`.
 *
 * ⚠️ **`RegionUpdateServiceTest`는 이걸 잡을 수 없다.** 그쪽은 `FakeTransport`를
 *    끼우므로 [RegionUpdateService.HttpTransport]를 **한 줄도 실행하지 않는다.**
 *    즉 이 결함이 배포된 이유는 정확히 "전송 계층에 테스트가 없었다"다.
 *
 * ## ⚠️ **실물 연결로는 이 규칙을 잴 수 없다 — 실측으로 확인했다**
 *
 * 처음엔 `URL(...).openConnection()`으로 진짜 연결 객체를 만들어 재려고 했다.
 * 두 가지가 막았다:
 *
 * | 시도 | 호스트 JVM 결과 |
 * |---|---|
 * | `requestMethod = "PATCH"` | **거부** — 허용 목록에 없다(`ProtocolException`) |
 * | 리플렉션으로 `method` 필드 쓰기 | **거부** — `InaccessibleObjectException: module java.base does not "opens java.net"` |
 *
 * 즉 **호스트 JVM에서는 PATCH를 만들 방법이 없다.** 반면 에뮬레이터의
 * `HttpsURLConnectionImpl`은 그냥 받아 줬다 — 실측 로그
 * `지역 저장 요청 · PATCH · HttpsURLConnectionImpl`, 그리고 저장이 200으로 성공해
 * 화면이 03으로 넘어갔다.
 *
 * 그래서 여기서는 **두 구현체를 흉내내서 계약만 고정한다.** 실제 전송 확인은
 * 에뮬레이터가 한 것이고, 이 파일이 그걸 대신하지는 않는다.
 *
 * ## 이 테스트가 고정하는 것
 *
 * `forcePatchMethod`를 지난 연결은 **`PATCH`이거나, 아니면 예외다.**
 * 조용히 `POST`로 남는 경우는 없다 — 그게 403 `42501`의 원인이었다.
 */
class RegionUpdateVerbTest {

    /**
     * 안드로이드처럼 `PATCH`를 받아 주는 구현체.
     *
     * ⚠️ 소켓을 열지 않는다 — [connect]가 아무것도 안 한다.
     */
    private class AcceptsPatch : HttpURLConnection(URL("https://example.com/")) {
        private var verb: String = "POST"

        override fun setRequestMethod(method: String) {
            verb = method
        }

        override fun getRequestMethod(): String = verb
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
    }

    /** 호스트 JVM처럼 허용 목록으로 `PATCH`를 막는 구현체(실측된 그 동작이다). */
    private class RejectsPatch : HttpURLConnection(URL("https://example.com/")) {
        private var verb: String = "POST"

        override fun setRequestMethod(method: String) {
            if (method == "PATCH") throw ProtocolException("PATCH 거부")
            verb = method
        }

        override fun getRequestMethod(): String = verb
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
    }

    /**
     * 🔴 **이 테스트가 이 파일의 이유다.** POST로 나가면 insert가 되어 403 `42501`이고,
     *    화면 02는 `연결이 불안정해요`에서 영원히 멈춘다 — 서버·키·네트워크는 정상인데.
     */
    @Test
    fun 지역_저장은_PATCH로_나간다() {
        val conn = AcceptsPatch()
        RegionUpdateService.HttpTransport.forcePatchMethod(conn)
        assertEquals(
            "POST로 나가면 PostgREST가 insert로 처리해 403 42501이 온다",
            "PATCH",
            conn.requestMethod,
        )
    }

    /**
     * 🔴 **PATCH를 거부하는 구현체에서는 던진다 — POST로 되돌아가지 않는다.**
     *
     * 조용히 POST로 보내면 위의 403 `42501`이 다시 시작되는데, 그 응답만 보면
     * **RLS 정책 문제로 읽힌다**(실제로는 메서드가 안 바뀐 것이다). 그 오진에
     * 시간을 썼기 때문에 여기서 **크게 실패하도록** 고정한다.
     *
     * ⚠️ 안드로이드는 `PATCH`를 받아 주므로 실기기에서는 이 경로로 오지 않는다 —
     *    **다음 안드로이드 버전이 막기 시작하면** 이 규칙이 발동한다.
     */
    @Test
    fun 메서드를_못_바꾸면_POST로_보내지_않고_던진다() {
        val conn = RejectsPatch()
        val e = assertThrows(IllegalStateException::class.java) {
            RegionUpdateService.HttpTransport.forcePatchMethod(conn)
        }
        assertEquals(
            "메서드를 못 바꿨는데 조용히 POST로 계속하면 403 42501이 다시 시작된다",
            "POST",
            conn.requestMethod,
        )
        // 메시지가 원인을 말해야 한다 — 이 결함은 응답만 보면 RLS 문제로 읽힌다.
        assert(e.message?.contains("42501") == true) {
            "예외 메시지가 원인(42501)을 말하지 않는다: ${e.message}"
        }
    }

    /**
     * ⚠️ **`X-HTTP-Method-Override`를 다시 붙이지 못하게 막는다.**
     *
     * 이 헤더가 실패의 원인이었다. `HttpURLConnection`은 설정한 헤더를 읽을 수
     * 있으므로([HttpURLConnection.getRequestProperty]) 흉내낸 연결로 확인한다 —
     * `forcePatchMethod`는 **메서드만** 만지고 헤더를 붙이지 않아야 한다.
     */
    @Test
    fun 메서드_우회_헤더를_붙이지_않는다() {
        val conn = AcceptsPatch()
        RegionUpdateService.HttpTransport.forcePatchMethod(conn)
        assertEquals(
            "PostgREST는 이 헤더를 무시한다 — 붙이면 POST(insert)로 처리된다",
            null,
            conn.getRequestProperty("X-HTTP-Method-Override"),
        )
    }
}
