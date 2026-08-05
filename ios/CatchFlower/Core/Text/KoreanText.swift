import Foundation

/// 한국어 조사·서수 처리.
///
/// A 문서가 **직접 요구한 것**이다:
/// > 조사 처리 주의: `{꽃이름}가/이` — 받침 유무에 따라 분기해야 한다. 개발 시 조사 자동 처리 함수 필요.
/// > 서수 표기: 두 번째 ~ 열한 번째 까지 한글, 12회 이상은 `12번째`
///
/// 꽃 이름 200종에는 `억새`(받침 없음) · `개망초`(없음) · `민들레`(없음) ·
/// `할미꽃`(있음) · `봄맞이꽃`(있음)이 섞여 있다. 하드코딩으로는 못 막는다.
enum KoreanText {

    /// 마지막 글자에 받침(종성)이 있는가.
    ///
    /// 한글 음절은 `0xAC00 + (초성×21 + 중성)×28 + 종성` 이므로
    /// `(코드 - 0xAC00) % 28 != 0` 이면 받침이 있다.
    static func hasFinalConsonant(_ word: String) -> Bool? {
        guard let scalar = word.unicodeScalars.last else { return nil }
        let value = scalar.value
        guard (0xAC00...0xD7A3).contains(value) else {
            // 한글이 아니면 판단하지 않는다 (학명·숫자 등). 호출부가 기본형을 쓰게 한다.
            return nil
        }
        return (value - 0xAC00) % 28 != 0
    }

    /// 받침에 따라 조사를 고른다.
    ///
    /// - Parameters:
    ///   - withFinal: 받침 **있을 때** 쓰는 조사 (`이` `을` `은` `과` `으로`)
    ///   - withoutFinal: 받침 **없을 때** 쓰는 조사 (`가` `를` `는` `와` `로`)
    ///
    /// 한글이 아니면 `withoutFinal`을 쓴다 — 어느 쪽이든 틀리지만
    /// `장미(이)` 같은 괄호 표기는 A 문서의 어조("사람이 말하듯")에 어긋난다.
    static func particle(
        after word: String,
        withFinal: String,
        withoutFinal: String
    ) -> String {
        switch hasFinalConsonant(word) {
        case true: return withFinal
        case false, nil: return withoutFinal
        }
    }

    /// `억새가` · `할미꽃이`
    static func subject(_ word: String) -> String {
        word + particle(after: word, withFinal: "이", withoutFinal: "가")
    }

    /// `억새를` · `할미꽃을`
    static func object(_ word: String) -> String {
        word + particle(after: word, withFinal: "을", withoutFinal: "를")
    }

    /// `억새는` · `할미꽃은`
    static func topic(_ word: String) -> String {
        word + particle(after: word, withFinal: "은", withoutFinal: "는")
    }

    /// `연남동으로` · `성수동2가로`
    static func direction(_ word: String) -> String {
        word + particle(after: word, withFinal: "으로", withoutFinal: "로")
    }

    private static let koreanOrdinals = [
        2: "두 번째", 3: "세 번째", 4: "네 번째", 5: "다섯 번째",
        6: "여섯 번째", 7: "일곱 번째", 8: "여덟 번째", 9: "아홉 번째",
        10: "열 번째", 11: "열한 번째",
    ]

    /// 화면 11 — `이번이 **{서수}** 발견입니다.`
    ///
    /// 2~11회는 한글, 12회 이상은 `12번째` (A 문서 규정).
    /// 1회는 신규 등록(화면 10)이라 여기 오지 않지만, 방어적으로 `첫 번째`를 돌려준다.
    static func ordinal(_ count: Int) -> String {
        if count <= 1 { return "첫 번째" }
        if let korean = koreanOrdinals[count] { return korean }
        return "\(count)번째"
    }
}
