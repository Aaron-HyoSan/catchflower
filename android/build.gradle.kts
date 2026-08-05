// 루트 빌드 스크립트
//
// ⚠️ 버전 조합은 임의로 올리지 않는다 (프로젝트 맥락/세션시작_AOS.md 2절).
//   Gradle 9.6.1 + AGP 9.3.1 조합에서만 빌드가 통과한다.
//   AGP 8.x → Gradle 9.6.0이 제거한 내부 API를 참조해 죽는다.
//   Kotlin 플러그인(org.jetbrains.kotlin.android)은 선언하지 않는다 — AGP 9에 내장이다.
//
// ⚠️ 단, Compose Compiler 플러그인은 별개다. buildFeatures.compose = true 를 켜면
//   AGP 9가 "Compose Compiler Gradle plugin is required"로 구성 단계에서 죽는다.
//   버전은 AGP 9.3.1이 내장한 Kotlin 버전(2.2.10)과 반드시 일치시킨다.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
