package com.catchflower.app.core

/**
 * 앱이 보여 주는 **법적 문서 3종**. 화면 20-3(A 문서 3절 ⑩).
 *
 * 2026-08-16에 출시 준비로 생겼다.
 *
 * ## 🔴 문서 본문을 코드에 넣지 않는다 — 원본은 `법무/` 폴더 하나다
 *
 * 같은 문장이 앱과 웹(심사에 내는 URL)에 **두 벌** 있으면 반드시 갈라지고, 갈라진 쪽은
 * 아무도 모른다. 그래서 원본은 저장소 루트의 `법무/` 텍스트 파일이고, 빌드가 그것을
 * `assets/legal/`로 **복사**한다(`SyncSharedAssets`). 앱은 읽기만 한다.
 *
 * ## 🔴 [asset] 이름이 ASCII다 — 한글 파일명을 그대로 쓰면 기기에서만 실패한다
 *
 * 원본 파일명은 한글인데(`법무/개인정보_처리방침.txt`), macOS 파일 이름은 **NFD**로
 * 저장되고 Kotlin 소스에 적는 문자열 리터럴은 **NFC**다. 두 값은 눈으로 같고
 * `==`로 다르다 — `assets.open("개인정보_처리방침.txt")`가 **파일을 못 찾는다.**
 * 이 함정은 이미 한 번 겪었다(맛집지도: 이름을 키로 썼더니 전량 불일치했는데 출력이
 * 똑같았다). 그래서 **복사할 때 ASCII 이름으로 바꾼다.**
 *
 * ⚠️ 즉 [asset]과 [source]는 **짝이고, 짝을 맞추는 곳은 Gradle이다.** 한쪽만 고치면
 *    화면 20-3이 `문서를 불러올 수 없어요`가 된다 — 빌드는 성공한다.
 */
enum class LegalDoc(
    /** 화면 20-3 헤더 제목. **설정 행 이름과 같은 문자열이다**(A 문서 3절 ⑨·⑩). */
    val title: String,
    /** `assets/` 안의 경로. ASCII (위 주석). */
    val asset: String,
    /** 저장소의 원본 파일. Gradle이 이 파일을 [asset]으로 복사한다. */
    val source: String,
) {
    PRIVACY("개인정보 처리방침", "legal/privacy.txt", "개인정보_처리방침.txt"),
    TERMS("이용약관", "legal/terms.txt", "서비스_이용약관.txt"),
    LOCATION("위치기반서비스 이용약관", "legal/location.txt", "위치기반서비스_이용약관.txt"),
}

/**
 * 문서 본문을 **화면에 올리기 전에 손보는 규칙.** 파일을 읽는 일은 하지 않는다
 * (그건 `LegalDocStore`다) — 여기는 JVM에서 전부 잴 수 있어야 한다.
 */
object LegalDocs {

    /**
     * 앱이 채우는 유일한 자리.
     *
     * 🔴 **이 값을 문서 파일에 손으로 적지 않는다.** 창구 주소의 원본은
     * `local.properties`의 `CONTACT_EMAIL` 하나이고(설정 > 고객문의가 그 값으로 메일을
     * 연다), 문서에 적어 두면 **두 곳이 된다** — 주소를 바꾼 날 한쪽이 낡는다.
     */
    const val CONTACT = "{{문의_이메일}}"

    /**
     * 아직 안 채운 자리의 모양. `{{시행일}}` 처럼 생겼다.
     *
     * 🔴 **닫는 중괄호도 반드시 escape한다(`\\}\\}`).** 여기 `}}`를 그냥 뒀더니
     *    **화면 20-3을 열 때마다 앱이 죽었다**(2026-08-17 실측 · API 36 에뮬레이터 ·
     *    `PatternSyntaxException: Syntax error in regexp pattern near index 10`).
     *    안드로이드의 `java.util.regex`는 **ICU 엔진**이라 짝 없는 `}`를 문법 오류로
     *    보고, 데스크톱 JVM은 그것을 리터럴 `}`로 받아 준다 —
     *    즉 **JVM 테스트는 전부 초록인데 기기에서만 100% 죽는다.**
     *    이 값은 `object`의 `<clinit>`에서 컴파일되므로 [render]만 불러도 죽는다
     *    (스택: `z81.<clinit>` → `ExceptionInInitializerError`).
     *
     * ⚠️ 대괄호 안(`[^}]`)의 `}`는 그대로 둬도 된다 — 문자 클래스 안에서는 리터럴이다.
     *    고칠 곳은 **클래스 밖의 `}`뿐**이다. 이 규칙을 지키는지는
     *    `LegalDocsTest`의 `정규식 리터럴에 짝 없는 중괄호가 없다`가 세고,
     *    **기기 층은 `LegalDocsDeviceTest`가 실제로 컴파일해서 잰다.**
     */
    private val PLACEHOLDER = Regex("""\{\{[^}]+\}\}""")

    /**
     * 화면에 그릴 본문을 만든다.
     *
     * @param contactEmail 비어 있으면 [CONTACT]를 **지우지 않고 그대로 둔다** —
     *   지우면 "문의처가 없는 처리방침"이 되어 심사에서 반려되고, 우리 눈에는
     *   문장이 자연스러워 보여서 **빠진 것을 알 수 없다.** 남겨 두면 [unresolved]가
     *   릴리스 빌드에서 그것을 세운다.
     */
    fun render(raw: String, contactEmail: String): String =
        reflow(if (contactEmail.isBlank()) raw else raw.replace(CONTACT, contactEmail))

    /**
     * 원본은 **78자에서 손으로 줄바꿈한** 텍스트다(사람이 파일로도 읽는다).
     * 폭 360dp 화면에 그대로 올리면 한 줄이 두 줄로 접히면서 **한 문장이 계단처럼**
     * 보인다 — 문서가 깨진 것으로 읽힌다. 그래서 접힌 줄을 다시 붙인다.
     *
     * 규칙은 넷이고, **넷 다 실측으로 정했다**(아래 표).
     *
     * 1. **앞 줄이 꽉 찼을 때만** 이어 붙인다([WRAP_MIN]자 이상). 손 줄바꿈은 줄이
     *    거의 찼을 때만 일어나므로, 짧은 줄 뒤의 줄은 **원래 다른 줄**이다 —
     *    이 규칙이 없으면 `시행일: …`과 `제정일: …`이 한 줄로 붙는다.
     * 2. **항목의 첫 줄은 절대 이어 붙이지 않는다**([ITEM_START]). `1.`·`가.`·`-`·
     *    `제3조`·`(1)`로 시작하는 줄이 앞 문단에 붙으면 조문 번호가 문장 속으로
     *    사라진다.
     * 3. **`레이블: ` 줄도 이어 붙이지 않는다**([LABEL_START]). 처리방침 5항은
     *    `보내는 것: … / 목적: … / 보관: …` 세 줄을 나란히 쓰는데, 앞 줄이 꽉 차
     *    있어서 규칙 1·2로는 막히지 않는다 — 실제로 **네 줄이 잘못 붙었다.**
     * 4. **앞 줄이 콜론으로 끝나면** 뒤에 목록이 온다는 뜻이라 붙이지 않는다.
     * 5. **가운뎃점(`·`)으로 끝나는 줄은 공백 없이 붙인다**([NO_SPACE_AFTER]). 붙일 때
     *    공백을 넣는 것이 기본인데, 이용약관 제7조가 `교통·지형·` 에서 접혀 있어서
     *    `교통·지형· 타인의`가 됐다 — **오타로 보이는 문장이다.** 자동 검사로는 못 봤고
     *    재조립 사본을 눈으로 읽다가 찾았다(글자는 하나도 안 잃었으므로 전부 초록이었다).
     *
     * ## 🔴 [WRAP_MIN]이 55에서 36으로 내려온 이유 — 처음엔 **아무 일도 안 했다**
     *
     * 원본이 78 **칸**에서 접혀 있는데 한글은 두 칸을 차지하므로 한 줄은 **글자 수로
     * 40 남짓**이다(실측: 세 문서의 최대 줄이 53·50·55자, 중앙값 33·34·39).
     * 55자 기준에서는 **한 줄도 붙지 않았고** 그래도 위 규칙 검사와 글자 보존 검사는
     * 전부 초록이었다 — "재조립이 도는가"를 아무도 세지 않았기 때문이다
     * (정확도 지표가 중간 단계에서 끝난 것과 같은 층이다).
     *
     * 36자로 내리면 74쌍이 붙는다. **그 74쌍을 한 줄씩 눈으로 읽고 정했다** —
     * 자동 검사로는 "잘못 붙었는지"를 알 수 없다(글자는 하나도 안 잃는다).
     *
     * ⚠️ 36은 **이 세 문서의 줄 길이 분포에서** 나온 값이다. 오너가 문서를 다시 써서
     *    40자짜리 독립 줄을 나란히 쓰면 잘못 붙을 수 있다 — 그때 보이는 곳은
     *    `LegalDocsTest`가 떨어뜨리는 재조립 사본이다(그래서 그 사본을 남긴다).
     *
     * ⚠️ 원본을 한 줄짜리 문단으로 바꾸는 쪽(줄바꿈을 아예 없애는 것)을 고르지 않았다.
     *    그러면 `법무/`의 원본을 그냥 열어 보는 오너에게 가로로 무한히 긴 파일이 된다.
     */
    fun reflow(raw: String): String {
        val out = StringBuilder()
        var pending: String? = null
        fun flush() {
            pending?.let { out.append(it).append('\n') }
            pending = null
        }
        for (line in raw.replace("\r\n", "\n").split('\n')) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    flush()
                    out.append('\n')
                }
                pending == null -> pending = line.trimEnd()
                ITEM_START.containsMatchIn(trimmed) -> {
                    flush()
                    pending = line.trimEnd()
                }
                LABEL_START.containsMatchIn(trimmed) -> {
                    flush()
                    pending = line.trimEnd()
                }
                // ⚠️ 길이는 **들여쓰기를 포함해서** 센다. 빼면 깊게 들여쓴 이어짐 줄
                //    두 개가 기준 아래로 떨어져 안 붙었다(실측으로 이쪽을 골랐다).
                pending!!.length < WRAP_MIN -> {
                    flush()
                    pending = line.trimEnd()
                }
                pending!!.endsWith(":") -> {
                    flush()
                    pending = line.trimEnd()
                }
                // ⚠️ 가운뎃점 뒤에는 공백을 넣지 않는다(규칙 5). 실측으로 찾았다.
                NO_SPACE_AFTER.any { pending!!.endsWith(it) } -> pending = "${pending!!}$trimmed"
                else -> pending = "${pending!!} $trimmed"
            }
        }
        flush()
        // 마지막 빈 줄 하나만 남긴다(파일 끝 개행).
        return out.toString().trimEnd('\n') + "\n"
    }

    /**
     * 손 줄바꿈이 일어나는 최소 **글자 수**. 원본은 78 **칸**에서 접히고 한글은 두 칸을
     * 차지하므로 글자 수로는 40 남짓이다(위 주석의 실측).
     */
    private const val WRAP_MIN = 36

    /** 새 항목의 시작. 이 줄은 앞 문단에 붙지 않는다. */
    private val ITEM_START = Regex("""^(제\d+조|\d+\.|\(\d+\)|\d+\)|[가-힣]\.|[-*•]|\*)\s""")

    /**
     * `보내는 것: …` 처럼 **레이블로 시작하는 줄.** 15자 이내 + `: `(콜론 뒤 공백)만
     * 본다 — 그래서 `https://plantnet.org 의 …`은 걸리지 않는다(콜론 뒤가 `//`다).
     * 그 줄은 실제로 앞 문장의 이어짐이라 붙어야 한다.
     */
    private val LABEL_START = Regex("""^[^:\s][^:]{0,14}:\s""")

    /**
     * 이 글자로 끝나는 줄은 **공백 없이** 이어 붙인다. 한국어에서 가운뎃점은 앞뒤에
     * 공백을 두지 않는다 — 세 문서에 `· `(가운뎃점+공백)이 **하나도 없다**(실측).
     *
     * ⚠️ 늘리기 전에 원본에서 그 글자+공백을 세 봐야 한다. 원본에 이미 있는 조합을
     *    넣으면 정상인 공백까지 없애 버린다.
     */
    private val NO_SPACE_AFTER = listOf("·")

    /**
     * 아직 사람이 채우지 않은 자리 목록. **릴리스 빌드를 세우는 판정이다.**
     *
     * 🔴 **여기가 없으면 심사자가 `{{운영자}}`를 본다.** 개발자는 이 화면을 안 열고,
     *    열어도 디버그 빌드에는 아무 경고가 없다 — 증상이 나오는 곳이 Play 심사뿐인
     *    결함이다(16KB 정렬과 같은 종류).
     *
     * @param includeContact false면 [CONTACT]는 세지 않는다. 그 자리는 사람이 아니라
     *   빌드 설정이 채우므로 문서 파일에 남아 있는 것이 **정상**이다.
     */
    fun unresolved(raw: String, includeContact: Boolean = false): List<String> =
        PLACEHOLDER.findAll(raw)
            .map { it.value }
            .filter { includeContact || it != CONTACT }
            .distinct()
            .toList()
}
