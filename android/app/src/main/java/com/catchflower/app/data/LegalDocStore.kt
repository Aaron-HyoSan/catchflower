package com.catchflower.app.data

import android.content.Context
import com.catchflower.app.core.AppSecrets
import com.catchflower.app.core.LegalDoc
import com.catchflower.app.core.LegalDocs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 법적 문서 본문을 `assets/legal/`에서 읽는다. 화면 20-3(A 문서 3절 ⑩).
 *
 * ⚠️ **읽기 실패를 빈 문자열로 만들지 않는다.** 빈 화면은 "약관이 없는 앱"으로 보이고
 *    심사자에게는 그게 **약관을 안 만든 것**과 구별되지 않는다 — 실패는 실패라고 말한다
 *    (`문서를 불러올 수 없어요`).
 */
object LegalDocStore {

    /**
     * @return 본문. 못 읽었으면 null(파일이 없거나 Gradle 복사가 빠진 것이다).
     */
    suspend fun read(
        context: Context,
        doc: LegalDoc,
        contactEmail: String = AppSecrets.contactEmail,
    ): String? = withContext(Dispatchers.IO) {
        // 🔴 **읽기와 [LegalDocs.render]를 같은 `runCatching` 안에 둔다.**
        //    예전 판은 `runCatching { 읽기 }.map { render }`였는데 `Result.map`은
        //    **transform의 예외를 잡지 않는다** — 그래서 `render`가 던진
        //    `ExceptionInInitializerError`가 그대로 올라가 **화면 20-3을 여는 순간
        //    앱이 죽었다**(2026-08-17 실측 · 원인은 [LegalDocs] PLACEHOLDER 정규식).
        //    약관 화면이 죽는 것은 심사에서 바로 걸리는 종류의 사고다.
        runCatching {
            val raw = context.assets.open(doc.asset).use { it.readBytes().decodeToString() }
            LegalDocs.render(raw, contactEmail)
        }.getOrElse { e ->
            // 🔴 이 로그가 유일한 단서다. 빌드는 성공하고 화면만 안내 문구를 띄운다.
            //    원인은 둘이고 **화면에서는 구별되지 않는다**:
            //    ① `SyncSharedAssets`가 파일을 못 찾았다(이름이 짝이 아니거나
            //       `법무/`에서 파일이 사라졌다) ② 본문 손질이 던졌다(위 🔴).
            //    예외 이름이 그 둘을 가른다 — `FileNotFoundException`인가 아닌가.
            android.util.Log.w("CatchFlower", "법적 문서를 읽을 수 없다: ${doc.asset} · $e")
            null
        }
    }
}
