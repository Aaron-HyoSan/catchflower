package com.catchflower.app.ui.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.catchflower.app.core.LegalDoc
import com.catchflower.app.data.LegalDocStore
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 20-3 **법적 문서 보기**(A 문서 3절 ⑩). 설정 3·4·5행에서 열린다.
 *
 * 🔴 **문구를 만들지 않는다.** 본문은 `법무/` 폴더의 txt 원본이고 이 화면은 그것을 그린다 —
 *    여기서 문장을 손보면 앱과 웹(심사에 낸 URL)이 갈라진다. 유일한 예외는
 *    `{{문의_이메일}}`이고 그 치환은 [com.catchflower.app.core.LegalDocs.render]가 한다.
 *
 * ⚠️ **글자를 선택할 수 있게 두었다**([SelectionContainer]). 약관은 사람들이 일부를
 *    복사해서 문의하는 문서다. 그리고 이 화면에는 링크가 없어서 선택이 다른 동작을
 *    가로채지 않는다.
 */
@Composable
fun LegalDocScreen(
    doc: LegalDoc,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 🔴 `produceState`로 읽는다 — assets 읽기는 IO다(문서 3개 합쳐 20KB가 넘는다).
    //    컴포지션에서 바로 읽으면 첫 프레임이 그만큼 늦는다.
    val body by produceState<Body>(initialValue = Body.Loading, doc) {
        val text = LegalDocStore.read(context, doc)
        value = if (text == null) Body.Failed else Body.Loaded(text)
    }

    Column(modifier.fillMaxSize()) {
        // 제목은 **누른 행의 이름 그대로**다(A 문서 3절 ⑨·⑩). 두 이름을 쓰면
        // 사용자는 다른 문서를 열었다고 생각한다.
        CfHeader(title = doc.title, onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CfDimen.ScreenPadding)
                .navigationBarsPadding(),
        ) {
            when (val state = body) {
                // ⚠️ 로딩 문구를 만들지 않았다(A 문서에 없다). 파일 읽기는 수십 ms라
                //    문구가 깜빡이는 것이 더 나쁘다 — 빈 화면으로 두고 바로 채운다.
                Body.Loading -> Unit

                // 🔴 실패를 빈 화면으로 두지 않는다. 약관이 없는 앱과 구별되지 않는다.
                Body.Failed -> Text(
                    text = "문서를 불러올 수 없어요",
                    style = CfText.Body,
                    color = CfColor.TextSecondary,
                )

                is Body.Loaded -> SelectionContainer {
                    Text(
                        text = state.text,
                        style = CfText.Body,
                        color = CfColor.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(CfDimen.GapSection))
        }
    }
}

/** 세 가지 상태를 `Boolean` 두 개로 나누지 않는다 — 로딩과 실패가 겹친다. */
private sealed interface Body {
    data object Loading : Body
    data object Failed : Body
    data class Loaded(val text: String) : Body
}
