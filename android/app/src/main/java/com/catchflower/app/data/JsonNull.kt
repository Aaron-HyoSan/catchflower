package com.catchflower.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON의 `null`을 코틀린 `null`로 읽는다.
 *
 * 🔴 **`optString(key).ifEmpty { null }`을 쓰지 마라. 그게 이 파일이 생긴 이유다.**
 *
 *    안드로이드 `org.json`은 JSON `null`에 대해 **문자열 `"null"`(4글자)** 을 준다.
 *    빈 문자열이 아니므로 `ifEmpty`가 **안 걸린다.** 그러면 그 값이 그대로 화면까지
 *    올라가서 사용자가 **`null · 2026년 8월부터 함께`** 를 읽고, 활동 지역 칸에는
 *    **`null`** 이라고 적힌 동네가 뜬다. 실제로 그렇게 떴다(에뮬레이터 실측).
 *
 * 🔴 **이 결함은 JVM 테스트로 잡을 수 없다.** 테스트 클래스패스의 `org.json:json`
 *    (`app/build.gradle.kts` — android.jar의 `org.json`은 껍데기라 넣은 것)은 같은
 *    입력에 **빈 문자열**을 준다. 즉 **두 구현의 동작이 다르다**(실측):
 *
 *    | 입력 `{"region_name":null}` | `optString` | `isNull` |
 *    |---|---|---|
 *    | 안드로이드 기기            | `"null"`    | `true`   |
 *    | JVM 테스트 (json:20250517) | `""`        | `true`   |
 *
 *    그래서 `"region_name":null`을 그대로 넣은 픽스처가 이미 있었는데도
 *    (`RankingServiceTest.지역_미설정을_알아본다`) **249개가 전부 초록이었다.**
 *    테스트가 틀린 게 아니다 — 테스트가 **기기와 다른 라이브러리를 재고 있었다.**
 *    이 프로젝트에서 "초록 테스트도 증거가 아니다"의 세 번째 사례다.
 *
 * ⚠️ **`isNull`을 쓰는 이유가 이것이다 — 두 구현이 유일하게 일치하는 접근자다.**
 *    문자열 `"null"`을 걸러내는 방식으로 고치지 않았다. 그건 닉네임을 `null`로
 *    지은 사용자의 **진짜 이름을 지운다** — 서버가 준 값을 클라이언트가 삭제하는
 *    것이고, 화면에는 조회 실패와 똑같이 보여서 원인을 알 수 없다.
 *
 * ⚠️ 되돌아가는 것을 [JsonNullTest]가 **소스를 읽어서** 막는다. 값으로는 막을 수
 *    없기 때문이다(위 표의 두 번째 줄이 그 이유다).
 */

/** 없는 키 · JSON `null` · 빈 문자열을 **모두** 코틀린 `null`로 본다. */
internal fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

/** 배열 원소용. 같은 이유로 [stringOrNull]과 같은 판정을 쓴다. */
internal fun JSONArray.stringOrNull(index: Int): String? =
    if (isNull(index)) null else optString(index).ifEmpty { null }
