package com.catchflower.app.recognizer

import java.io.File

/**
 * 🔴 **QA 전용 1차 필터 우회 스위치.** 기본은 **항상 꺼짐**이다.
 *
 * **왜 필요한가.** 오너가 도감 등록 → 지도 핀까지 손으로 QA하려는데,
 * 계절·장소 때문에 실물 꽃을 바로 구할 수 없을 때가 있다. 화면(모니터·폰) 속
 * 꽃 사진을 찍으면 [MlKitFlowerPreFilter]가 **옳게** 막는다 —
 * 디스플레이 서브픽셀이 모아레를 만들어 `top=Pattern`이 뜨고 `Flower`가
 * 임계값을 못 넘는다(실측 로그 2026-08-09 20:43). 필터 입장에서 그건 꽃 사진이 아니다.
 * 그 판단은 맞지만, **그 때문에 뒤쪽 흐름(등록·도감·지도)을 QA할 수 없다.**
 *
 * ⚠️ **UI 버튼으로 만들지 않는다.** 개발용 버튼을 사용자 화면에 남겨
 *    두 번 배포한 적이 있다(`[개발] 친구 없는 화면` · `0종 보기` · (35)·(38) ·
 *    `ButtonLabelSourceTest`가 그래서 있다). 화면에 없으면 남을 수도 없다.
 *
 * ⚠️ **`BuildConfig.DEBUG`로 열지 않는다.** 그러면 **디버그 빌드 전체가
 *    항상 우회**가 되어, 지금 폰에 깔린 앱으로 1차 필터를 두 번 다시 검증할 수 없다.
 *    파일이 있어야만 열리게 해서 **평소에는 실제 경로가 돌게** 한다.
 *
 * ⚠️ **release 빌드에서는 파일이 있어도 열리지 않는다** — [enabled] 참조.
 *
 * 켜는 법 / 끄는 법:
 * ```
 * adb shell touch /data/local/tmp/cf_qa_allow_any_photo   # 켜기
 * adb shell rm    /data/local/tmp/cf_qa_allow_any_photo   # 끄기 (QA 끝나면 바로)
 * ```
 * 앱을 다시 켤 필요는 없다 — 촬영할 때마다 파일을 확인한다.
 */
object QaPreFilterSwitch {

    const val PATH = "/data/local/tmp/cf_qa_allow_any_photo"

    /**
     * 지금 우회가 켜져 있는가.
     *
     * ⚠️ **값을 캐시하지 않는다.** 캐시하면 `adb shell rm`으로 끈 뒤에도
     *    앱을 재시작할 때까지 열려 있어서, **끈 줄 알고 QA를 계속하는** 상태가 된다.
     *    스위치가 "지금" 열려 있는지가 매 촬영의 사실이어야 한다.
     *
     * 🔴 **release에서는 항상 false다.** 파일 검사보다 이 조건을 먼저 둔다 —
     *    QA 스위치가 출시 빌드에서 살아 있으면 아무 사진이나 유료 API로 가고,
     *    그건 화면에 증상이 없다.
     */
    val enabled: Boolean
        get() = com.catchflower.app.BuildConfig.DEBUG && File(PATH).exists()
}

/**
 * QA 스위치가 켜져 있으면 [delegate]의 판정을 **통과로 덮는다.**
 *
 * ⚠️ **`AlwaysPassPreFilter`로 갈아끼우지 않는다.** 그러면 실제 필터가 한 줄도
 *    안 돌아서 **무엇을 우회했는지 로그에 남지 않는다** — `top=Pattern`처럼
 *    "왜 막혔는지"가 QA 중에 가장 알고 싶은 정보다. 그래서 필터를 **그대로 돌리고**
 *    결과만 덮는다. 대신 [PreFilterResult.topLabel]에 표시를 남겨
 *    **로그를 보는 사람이 우회 상태임을 놓칠 수 없게** 한다.
 */
class QaBypassPreFilter(
    private val delegate: FlowerPreFilter,
    /**
     * ⚠️ **테스트가 켠 상태를 재현할 수 있어야 해서 주입한다.** 안 그러면
     *    `/data/local/tmp`가 없는 JVM에서는 **꺼진 경로만 검증**하게 되고,
     *    켠 경로는 "화면에서 눌러 봤다"가 유일한 근거가 된다.
     */
    private val enabled: () -> Boolean = { QaPreFilterSwitch.enabled },
) : FlowerPreFilter {

    override suspend fun check(jpeg: ByteArray): PreFilterResult {
        val real = delegate.check(jpeg)
        if (!enabled() || real.isLikelyFlower) return real

        // ⚠️ **여기서 `android.util.Log`를 부르지 않는다.** JVM 단위 테스트에서
        //    `Method w in android.util.Log not mocked`로 죽는다(실제로 3개가 빨개졌다).
        //    피하려면 `unitTests.isReturnDefaultValues = true`가 필요한데 그건
        //    **모든 안드로이드 API를 조용히 0/null로 만드는** 설정이라 거부된 항목이다.
        //    로그는 [PreFilterResult.topLabel]에 표시를 실어 호출처의 `1차필터:` 줄이 찍는다.
        return real.copy(
            isLikelyFlower = true,
            // 🔴 이 문자열이 로그의 `1차필터:` 줄에 그대로 찍힌다. 우회한 촬영과
            //    정상 통과한 촬영을 **로그만 보고 구분할 수 있어야** 한다.
            topLabel = "QA우회(원래=${real.topLabel})",
        )
    }
}
