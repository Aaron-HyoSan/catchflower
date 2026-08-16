# R8 규칙 (릴리스 빌드 전용)
#
# 🔴 **여기서 틀리면 증상이 "실행 중 어느 화면에서만 죽는다"다.**
#    빌드는 성공하고, JVM 테스트 669개는 축소된 코드를 한 줄도 보지 않는다.
#    그래서 이 파일을 고친 날은 **릴리스 APK를 설치해서 화면을 눌러야** 한다.
#
# ⚠️ `-dontobfuscate`로 도망가지 않는다. 축소(shrink)는 남기고 난독화만 끄면
#    용량은 줄지만 **크래시 스택은 그대로 읽히고** 규칙이 틀린 것도 안 드러난다 —
#    즉 문제를 미뤄 두는 것이지 없애는 게 아니다.

# ── 1. 네이티브(JNI)가 이름으로 찾는 것 ─────────────────────────────
#
# 🔴 **R8은 자바 쪽 이름만 바꾼다. `.so` 안의 문자열은 못 바꾼다.**
#    네이티브가 `FindClass`/`GetMethodID`로 찾는 클래스·메서드가 이름이 바뀌면
#    그 순간 `NoSuchMethodError`이고, 빌드에는 아무 흔적이 없다.
#    이 앱에 실제로 들어 있는 `.so`는 5개다(카카오맵 libK3fAndroid,
#    ML Kit libmlkitcommonpipeline, CameraX libimage_processing_util_jni,
#    libsurface_util_jni, androidx.graphics.path).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 카카오맵 SDK. AAR에 규칙이 들어 있지만(consumer rules) **믿고 넘어가지 않는다** —
# 틀렸을 때의 증상이 "지도가 회색"이고, 그건 키해시 401과 화면에서 구별되지 않는다.
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.** { *; }
-dontwarn com.kakao.**

# ML Kit 이미지 라벨링(1차 필터). 모델·파이프라인을 리플렉션으로 로드한다.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# ── 2. 열거형 ────────────────────────────────────────────────────────
# 기본 규칙에도 있지만 **우리 열거형이 판정의 원본**이라 여기 한 번 더 적는다
# (`GatedAction`·`SettingsRules.Row`·`IdentifyOutcome` …). `values()`/`valueOf`가
# 지워지면 게이트 판정이 실행 중에 죽는다.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── 3. 우리 코드 ────────────────────────────────────────────────────
# 🔴 **`-keep class com.catchflower.**`를 쓰지 않는다.** 전부 남기면 축소가 사실상
#    꺼지고(용량이 안 줄고) 규칙이 맞는지도 확인할 수 없다. 이 앱은 main 소스에
#    리플렉션이 **한 곳도 없다**(`Class.forName`·`kotlin.reflect` 0건 · 실측).
#    그래서 남길 것이 없는 것이 정상이고, 필요해지는 날은 **리플렉션을 넣는 날**이다.
#    (JVM 테스트가 `const val`을 리플렉션으로 읽지만 그건 테스트 클래스패스다.)

# ── 4. 경고 억제 ────────────────────────────────────────────────────
# 컴파일에만 쓰이는 애노테이션·선택 의존성 참조. 남겨 두면 `bundleRelease`가
# `Missing class` 경고 수백 줄을 뿜고, **진짜 경고가 그 안에 묻힌다.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**
