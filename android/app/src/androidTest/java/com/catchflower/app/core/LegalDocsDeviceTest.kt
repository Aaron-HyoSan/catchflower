package com.catchflower.app.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catchflower.app.data.LegalDocStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 화면 20-3의 법적 문서 3종이 **기기에서 실제로 열리는가.**
 *
 * ## 🔴 왜 계측 테스트여야 하나 — JVM 테스트 전부 초록인데 기기에서 100% 죽었다
 *
 * 2026-08-17 실측(API 36 에뮬레이터 · 릴리스 빌드): 설정 > 개인정보 처리방침을 누르면
 * **앱이 죽었다**(`캐치플라워 keeps stopping`). 원인은 [LegalDocs]의 자리표시자
 * 정규식 `\{\{[^}]+}}` — 닫는 `}}`를 escape하지 않았다.
 *
 * | 층 | 결과 |
 * |---|---|
 * | 데스크톱 JVM (`LegalDocsTest` 20여 개) | 🔵 전부 초록 — OpenJDK는 짝 없는 `}`를 **리터럴로 받아 준다** |
 * | 안드로이드 기기 | 🔴 `PatternSyntaxException` → `ExceptionInInitializerError` → 프로세스 종료 |
 *
 * 안드로이드의 `java.util.regex`는 **ICU 엔진**(`com.android.icu.util.regex`)이라
 * 문법이 더 엄격하다. 즉 **이 결함은 JVM에서는 원리상 못 잡는다** — 재는 층이
 * 기기여야 한다. 게다가 `PLACEHOLDER`는 `object`의 `<clinit>`에서 컴파일되므로
 * [LegalDocs]의 **아무 멤버나 건드리면** 같이 죽는다(스택: `z81.<clinit>`).
 *
 * ## ⚠️ 이 테스트가 **어떤 경우에 빨개지나**
 *
 * ① 정규식이 ICU 문법에 안 맞다(위 사고) — [placeholderRegexCompiles]가 던진다.
 * ② `assets/legal/`에 문서가 없다(Gradle 복사 누락) — [LegalDocStore.read]가 null이다.
 * ③ 본문 손질([LegalDocs.reflow])이 기기에서만 던진다.
 *
 * 🔴 **`read()`가 null인 것을 통과시키지 않는다.** 화면은 그때 `문서를 불러올 수 없어요`를
 *    띄우는데, 그건 "약관을 안 만든 앱"과 심사자 눈에 구별되지 않는다.
 *
 * ⚠️ ~~이 빌드의 문서에는 아직 오너 값이 안 들어와서 `{{시행일}}` 같은 칸이 남아 있다.~~
 *    → **2026-09-08에 다 채워졌다.** 그래도 `{{문의_이메일}}` **하나는 원래 남는 자리다**
 *    (빌드가 `CONTACT_EMAIL`로 채운다). 즉 여기서 **자리표시자가 없는 것을 요구하면 안
 *    되는 이유가 사라지지 않았다** — 그건 업로드 직전 검사
 *    (`출시/_tools/check_release_artifact.py`)가 산출물에서 본다. 여기서 재는 것은
 *    **문서가 열리는가**다.
 */
@RunWith(AndroidJUnit4::class)
class LegalDocsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 자리표시자 정규식이 **이 기기의 엔진에서 컴파일되는가.**
     *
     * 🔴 [LegalDocs.unresolved]를 부르는 것 자체가 검사다 — `PLACEHOLDER`가
     *    `<clinit>`에서 컴파일되므로 문법이 틀리면 이 줄에서 죽는다.
     *    (그러므로 아래 `assertTrue`가 아니라 **호출이 검사다.**)
     */
    @Test
    fun placeholderRegexCompiles() {
        val holes = LegalDocs.unresolved("앞 {{시행일}} 뒤 {{운영자}} 끝")
        assertEquals(listOf("{{시행일}}", "{{운영자}}"), holes)

        // 짝 없는 중괄호가 본문에 있어도 **터지지 않아야** 한다(사용자 문서는 우리가
        // 다 못 고른다). ICU에서 문제가 되는 것은 패턴이지 입력이 아니다.
        LegalDocs.unresolved("닫는 괄호만 있는 줄 }} 과 {{정상}}")
    }

    /** 세 문서가 **전부** 열려야 한다. 하나만 열려도 나머지는 죽은 행이다. */
    @Test
    fun allThreeDocsOpenOnDevice() = runBlocking {
        for (doc in LegalDoc.entries) {
            val body = LegalDocStore.read(context, doc, contactEmail = "qa@example.com")
            assertNotNull("${doc.title}(${doc.asset})을 못 읽었다", body)
            assertTrue(
                "${doc.title} 본문이 너무 짧다(${body!!.length}자) — 빈 파일이 복사됐다",
                body.length > 500,
            )
            // 제목 행과 본문이 같은 문서인지 최소한 확인한다(엉뚱한 파일이 복사되는 사고).
            assertTrue(
                "${doc.title} 본문에 문서 이름이 없다: ${body.take(60)}",
                body.take(200).contains("캐치플라워") || body.take(200).contains(doc.title),
            )
        }
    }
}
