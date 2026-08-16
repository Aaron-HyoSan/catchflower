package com.catchflower.app.core

import java.io.File
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 20-3이 보여 주는 법적 문서 3종의 **원본·짝·재조립**을 잰다.
 *
 * ## 🔴 이 검사가 어떤 경우에 빨개지나 (먼저 답한다)
 *
 * | 사고 | 화면 증상 | 잡는 검사 |
 * |---|---|---|
 * | `LegalDoc.source`와 Gradle 복사 목록이 어긋난다 | `문서를 불러올 수 없어요` (빌드는 성공) | [짝은_Gradle_복사_목록과_같다] |
 * | 문서 파일 이름을 바꿨다 | 같음 | [원본_파일이_전부_있다] |
 * | 재조립이 글자를 먹는다 | **문장 한 조각이 사라진다**(안 세면 아무도 못 본다) | [재조립은_글자를_잃지_않는다] |
 * | 재조립이 조문 번호를 문장에 붙인다 | `제3조`가 앞 문단 속으로 사라진다 | [조문_번호는_줄_앞에_남는다] |
 * | 재조립이 가운뎃점 뒤에 공백을 넣는다 | `교통·지형· 타인의` — 법적 문서의 오타로 읽힌다 | [재조립이_가운뎃점_뒤에_공백을_만들지_않는다] |
 * | 오너가 채울 칸이 남았다 | 없다 — 심사자만 본다 | [채우지_않은_칸을_센다] |
 *
 * ## ⚠️ 이 검사가 **못 잡는 것**
 *
 * 🔴 **자산이 APK에 실제로 들어갔는지는 여기서 모른다.** 이건 JVM 테스트라
 *    `assets/`를 보지 않는다 — `SyncSharedAssets`가 안 돌아도 초록이다.
 *    그건 기기에서 화면 20-3을 눌러 봐야 안다(`구현현황_AOS.md` 확인 목록).
 * 🔴 **문장이 법적으로 맞는지도 모른다.** 글자 수와 모양만 센다.
 */
class LegalDocsTest {

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "법무").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError(
            "프로젝트 루트를 못 찾았다. 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 법적 문서 검증이 조용히 사라진다",
        )
    }

    /**
     * ⚠️ **NFC로 정규화해서 읽는다.** macOS 파일명은 NFD인데 소스에 적은 문자열은 NFC라
     *    이름을 그대로 비교하면 **전부 불일치**하는데 출력은 똑같아 보인다.
     *    (그래서 자산 이름은 ASCII다 — [LegalDoc] 주석.)
     */
    private fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun sourceFile(doc: LegalDoc) = File(projectRoot, "법무/${doc.source}")

    // ── 원본과 짝 ────────────────────────────────────────────────────

    @Test
    fun 원본_파일이_전부_있다() {
        LegalDoc.entries.forEach { doc ->
            val f = sourceFile(doc)
            assertTrue("법무/${doc.source} 가 없다 (${doc.title})", f.isFile)
            assertTrue("법무/${doc.source} 가 비었다", f.readText().length > 1000)
        }
    }

    /**
     * 🔴 **짝이 두 벌 있다.** 앱은 [LegalDoc]을, 빌드는 `build.gradle.kts`의
     * `legalPairs`를 본다 — Gradle이 코틀린 enum을 읽을 수 없어서 어쩔 수 없이 두 벌이다.
     * 어긋나면 앱이 없는 자산을 열어 `문서를 불러올 수 없어요`가 되고, **빌드는 성공한다.**
     * 그래서 테스트가 빌드 스크립트를 텍스트로 읽는다(이 저장소에서 유일한 경우다).
     */
    @Test
    fun 짝은_Gradle_복사_목록과_같다() {
        val script = File(projectRoot, "android/app/build.gradle.kts")
        assertTrue("빌드 스크립트를 못 찾았다: ${script.path}", script.isFile)
        val pairs = Regex(""""([^"]+\.txt)"\s+to\s+"([^"]+\.txt)"""")
            .findAll(nfc(script.readText()))
            .associate { nfc(it.groupValues[1]) to nfc(it.groupValues[2]) }

        val expected = LegalDoc.entries.associate {
            nfc(it.source) to nfc(it.asset.removePrefix("legal/"))
        }
        assertEquals(
            "build.gradle.kts의 legalPairs와 LegalDoc이 어긋났다",
            expected,
            pairs,
        )
    }

    /** 자산 이름은 ASCII다 — 한글이 섞이면 기기에서만 실패한다([LegalDoc] 주석). */
    @Test
    fun 자산_이름은_ASCII다() {
        LegalDoc.entries.forEach { doc ->
            assertTrue(
                "${doc.asset} 에 ASCII 아닌 글자가 있다 — 기기에서만 파일을 못 찾는다",
                doc.asset.all { it.code in 32..126 },
            )
            assertTrue("${doc.asset} 은 legal/ 아래여야 한다", doc.asset.startsWith("legal/"))
        }
    }

    // ── 재조립 ───────────────────────────────────────────────────────

    /**
     * 🔴 **가장 위험한 결함을 재는 자리다.** 재조립이 한 줄을 흘리면 화면에는
     * 문장 한 조각이 없는 문서가 그려지는데, 172줄짜리 방침에서 그걸 눈으로 찾을 수
     * 없다(그리고 `문서를 불러올 수 없어요`도 안 뜬다).
     *
     * 공백을 다 지운 글자열이 같은지로 본다 — 붙이고 자르는 것은 허용, **잃는 것은 금지**.
     */
    @Test
    fun 재조립은_글자를_잃지_않는다() {
        LegalDoc.entries.forEach { doc ->
            val raw = sourceFile(doc).readText()
            val out = LegalDocs.reflow(raw)
            assertEquals(
                "${doc.source} 재조립에서 글자가 변했다",
                raw.filterNot { it.isWhitespace() },
                out.filterNot { it.isWhitespace() },
            )
        }
    }

    /** 손으로 접힌 줄은 붙는다 — 붙지 않으면 화면에서 한 문장이 계단처럼 보인다. */
    @Test
    fun 접힌_줄은_다시_붙는다() {
        val raw = "이 문장은 78자에서 손으로 접힌 앞부분이고 길이가 충분히 길어서 이어 붙어야 한다\n뒷부분이다\n"
        assertEquals(
            "이 문장은 78자에서 손으로 접힌 앞부분이고 길이가 충분히 길어서 이어 붙어야 한다 뒷부분이다\n",
            LegalDocs.reflow(raw),
        )
    }

    /**
     * ⚠️ **짧은 줄 뒤는 원래 다른 줄이다.** 이 규칙이 없으면 머리말의
     * `시행일: …`과 `제정일: …`이 한 줄로 붙는다.
     */
    @Test
    fun 짧은_줄은_붙지_않는다() {
        val raw = "시행일: 2026년 8월 16일\n제정일: 2026년 8월 16일\n"
        assertEquals(raw, LegalDocs.reflow(raw))
    }

    /**
     * 🔴 조문 번호·항목 기호로 시작하는 줄은 **앞 문단이 꽉 찼어도** 붙지 않는다.
     * 붙으면 `제3조`가 문장 속으로 사라지고, 그건 문서를 읽을 수 없게 만든다.
     */
    @Test
    fun 조문_번호는_줄_앞에_남는다() {
        val 앞 = "앞 문단이 충분히 길어서 붙일 조건은 만족하지만 아래 줄은 새 항목이라 붙어서는 안 된다"
        listOf("제3조 (목적) 이 약관은", "1. 첫째 항목", "(2) 둘째 항목", "가. 가목", "- 목록 항목") .forEach { 항목 ->
            assertEquals(
                "'$항목' 이 앞 문단에 붙었다",
                "$앞\n$항목\n",
                LegalDocs.reflow("$앞\n$항목\n"),
            )
        }
    }

    /**
     * 🔴 **가운뎃점으로 끝나는 줄은 공백 없이 붙인다.** 이용약관 제7조가 `교통·지형·`에서
     * 접혀 있어서 `교통·지형· 타인의`가 됐다 — 법적 문서에 오타가 있는 것으로 읽힌다.
     *
     * ⚠️ 이 결함은 **위 검사들이 전부 초록인 채로** 화면에 나갔다(글자를 하나도 잃지
     *    않으므로). 재조립 사본을 눈으로 읽다가 찾았다.
     */
    @Test
    fun 가운뎃점으로_끝나면_공백없이_붙인다() {
        val raw = "이용자는 이동 중에 앱을 조작하지 않아야 하며, 촬영 시 주위의 교통·지형·\n   타인의 사유지를 침해하지 않아야 한다\n"
        assertEquals(
            "이용자는 이동 중에 앱을 조작하지 않아야 하며, 촬영 시 주위의 교통·지형·타인의 사유지를 침해하지 않아야 한다\n",
            LegalDocs.reflow(raw),
        )
    }

    /**
     * 🔴 위 단정의 **실제 문서 쪽 짝이다.** 픽스처만 검사하면 문서가 다른 자리에서
     * 같은 모양으로 접힐 때 못 잡는다. 원본에 있는 `· ` 개수와 재조립 결과의 개수가
     * 같아야 한다 — 즉 **재조립이 가운뎃점 뒤에 공백을 만들지 않는다.**
     */
    @Test
    fun 재조립이_가운뎃점_뒤에_공백을_만들지_않는다() {
        LegalDoc.entries.forEach { doc ->
            val raw = sourceFile(doc).readText()
            val out = LegalDocs.reflow(raw)
            assertEquals(
                "${doc.source}: 재조립이 가운뎃점 뒤에 공백을 넣었다 — 오타로 읽힌다",
                Regex("· ").findAll(raw).count(),
                Regex("· ").findAll(out).count(),
            )
        }
    }

    /** 빈 줄은 문단 구분이다 — 지우면 172줄 문서가 한 덩어리로 보인다. */
    @Test
    fun 빈_줄은_남는다() {
        assertEquals("가\n\n나\n", LegalDocs.reflow("가\n\n나\n"))
    }

    /** 실제 문서에도 조문 제목이 줄 앞에 남아 있는지 본다(위 단정의 대조군). */
    @Test
    fun 실제_문서의_조문_제목이_줄_앞에_남는다() {
        val terms = LegalDocs.reflow(sourceFile(LegalDoc.TERMS).readText())
        val 조문 = terms.lines().filter { it.trimStart().startsWith("제") && "조" in it.take(6) }
        assertTrue("이용약관에서 조문 줄을 못 찾았다 — 문서 형식이 바뀌었으면 이 검사를 고친다", 조문.size >= 5)
        조문.forEach { line ->
            assertTrue("조문이 줄 가운데에 있다: $line", line.startsWith("제"))
        }
    }

    // ── 치환자 ───────────────────────────────────────────────────────

    /**
     * 🔴 **증상이 Play 심사에만 나오는 결함이다.** 지금은 5종이 남아 있고 전부 오너가
     * 줄 값이다(A 문서 4절 24번). 숫자를 박아 두는 이유는 **채우면 이 검사가 빨개져서**
     * 문서·오너 항목을 같이 정리하게 만들기 위한 것이다.
     */
    @Test
    fun 채우지_않은_칸을_센다() {
        val 남은 = LegalDoc.entries
            .flatMap { LegalDocs.unresolved(sourceFile(it).readText()) }
            .distinct()
            .sorted()
        assertEquals(
            "법적 문서의 빈 칸이 달라졌다. 채웠으면 A 문서 4절 24번과 오너_결정사항도 같이 닫는다",
            listOf("{{개인정보_보호책임자}}", "{{시행일}}", "{{운영자}}", "{{위치기반서비스사업_신고}}", "{{위치정보관리책임자}}"),
            남은,
        )
    }

    /**
     * ⚠️ `{{문의_이메일}}`은 **기본 집계에서 빠진다** — 그 자리는 사람이 아니라 빌드
     *    설정이 채운다. 빠뜨리면 릴리스 검사가 영구히 실패하고, 반대로 세지 않으면
     *    주소 없는 빌드에서 화면에 `{{문의_이메일}}`이 보이는 것을 못 잡는다.
     *    그래서 `includeContact`로 두 방향을 다 잰다.
     */
    @Test
    fun 문의_이메일_칸은_따로_센다() {
        val raw = sourceFile(LegalDoc.PRIVACY).readText()
        assertFalse(LegalDocs.CONTACT in LegalDocs.unresolved(raw))
        assertTrue(LegalDocs.CONTACT in LegalDocs.unresolved(raw, includeContact = true))
    }

    @Test
    fun 주소가_있으면_문서에_박아_넣는다() {
        val out = LegalDocs.render("문의: ${LegalDocs.CONTACT} 로 주세요\n", "help@catchflower.app")
        assertEquals("문의: help@catchflower.app 로 주세요\n", out)
    }

    /**
     * 🔴 주소가 없으면 **치환자를 그대로 남긴다.** 지우면 "문의처가 없는 처리방침"이
     * 되는데 문장은 자연스러워 보여서 **빠진 것을 알 수 없다**(그리고 릴리스 검사도
     * 지나간다 — 지워졌으니 찾을 것이 없다).
     */
    @Test
    fun 주소가_없으면_치환자를_지우지_않는다() {
        val out = LegalDocs.render("문의: ${LegalDocs.CONTACT} 로 주세요\n", "")
        assertTrue(LegalDocs.CONTACT in out)
    }

    // ── 사람이 눈으로 읽을 사본 ──────────────────────────────────────

    /**
     * 🔴 **재조립 결과는 단정만으로 확인할 수 없다.** 위 검사들은 "글자를 잃지 않았다"와
     * "조문이 줄 앞에 있다"를 재는데, 문서가 **읽기 좋은가**는 사람이 봐야 안다
     * (녹화 영상의 컷을 시트로 묶어 눈으로 읽어야 했던 것과 같은 층이다).
     *
     * 그래서 결과를 `build/reports/legal_reflow/`에 떨어뜨린다. 이 테스트의 단정은
     * 파일이 써졌는지가 아니라 **비어 있지 않은가**다 — 파일 유무를 조건으로 쓰면
     * 검사가 스스로 꺼진다.
     */
    @Test
    fun 재조립_사본을_눈으로_읽을_수_있게_떨어뜨린다() {
        val out = File("build/reports/legal_reflow").apply { mkdirs() }
        LegalDoc.entries.forEach { doc ->
            val text = LegalDocs.render(sourceFile(doc).readText(), "help@example.com")
            assertTrue("${doc.source} 재조립 결과가 너무 짧다", text.length > 1000)
            File(out, doc.asset.removePrefix("legal/")).writeText(text)
        }
    }
}
