package com.catchflower.app.ui.my

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// ⚠️ `weight`는 import하지 않는다 — `RowScope`의 확장이라 `Row { }` 안에서 이미 보인다.
//    `androidx.compose.foundation.layout.weight`를 넣으면 **internal 심볼을 집어** 컴파일이 깨진다.
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.catchflower.app.ui.component.CfHeader
import com.catchflower.app.ui.component.CfPrimaryButton
import com.catchflower.app.ui.component.CfSecondaryButton
import com.catchflower.app.ui.component.rememberToaster
import com.catchflower.app.ui.theme.CfColor
import com.catchflower.app.ui.theme.CfDimen
import com.catchflower.app.ui.theme.CfText

/**
 * 화면 20에서 여는 `프로필 수정`. 문구는 **A 문서 3절 ④가 전부다.**
 *
 * 2026-08-13에 죽은 버튼을 실제 동작으로 바꾸며 생겼다.
 *
 * 🔴 **닉네임을 못 받았으면 이 화면을 열지 않는다** — 판단은
 *    [ProfileEditViewModel.openable]에 있고, 부르는 곳은 화면 20이다.
 *    빈 칸에서 저장하면 **닉네임을 지운다.**
 *
 * ⚠️ 바꿀 수 있는 칸이 **닉네임 하나뿐**이다. 활동 지역은 6개월 규칙이 걸린
 *    별도 화면(02)이고, 대표 꽃·칭호는 서버가 계산한다 — 여기 두면 **못 바꾸는 칸**이 된다.
 */
@Composable
fun ProfileEditScreen(
    vm: ProfileEditViewModel,
    /** 화면 20이 서버에서 받은 지금 닉네임. 화면을 열 때 한 번 넣는다. */
    current: String?,
    onClose: () -> Unit,
    /** 저장이 **실제로** 됐을 때만 부른다 — 화면 20·17·18이 같은 값을 다시 읽는다. */
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = rememberToaster()

    // ⚠️ `current`가 바뀔 때만 다시 넣는다. 매 조합마다 넣으면 **타이핑이 지워진다.**
    LaunchedEffect(current) { vm.open(current) }

    Column(modifier.fillMaxSize()) {
        CfHeader(title = "프로필 수정", onBack = onClose)

        Column(
            Modifier.padding(horizontal = CfDimen.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(CfDimen.GapSmall),
        ) {
            // A 문서 3절 ④ `입력 라벨`. 값이 들어 있는 칸이라 placeholder가 아니다 —
            // placeholder로 두면 채워진 칸에서 무엇을 고치는 칸인지 알 수 없다.
            Text("닉네임", style = CfText.Caption, color = CfColor.TextSecondary)

            OutlinedTextField(
                value = vm.input,
                onValueChange = vm::onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(CfDimen.RadiusCard),
                // ⚠️ **글자 수를 여기서 자르지 않는다.** `maxLength`로 막으면 11번째
                //    글자가 그냥 안 들어가고, 사용자는 **키보드가 고장난 것으로** 본다.
                //    A 문서 3절 ④가 `닉네임은 10자까지 쓸 수 있어요`라는 문구를 둔 이유다.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                enabled = !vm.saving,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CfColor.Background,
                    unfocusedContainerColor = CfColor.Background,
                    focusedIndicatorColor = CfColor.Primary,
                    unfocusedIndicatorColor = CfColor.Border,
                    cursorColor = CfColor.Primary,
                ),
            )

            Spacer(Modifier.height(CfDimen.GapMedium))

            Row(horizontalArrangement = Arrangement.spacedBy(CfDimen.GapMedium)) {
                // 취소가 왼쪽이다 — 화면 16 삭제 다이얼로그와 같은 순서.
                CfSecondaryButton(
                    text = "취소",
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
                CfPrimaryButton(
                    text = "저장",
                    onClick = {
                        // 🔴 **문구와 닫기를 따로 넘긴다.** 묶으면 `10자까지 쓸 수 있어요`를
                        //    띄우면서 화면이 닫혀 **고칠 자리가 없어진다**
                        //    ([ProfileEditViewModel.save] 주석).
                        vm.save(onToast = toast, onClose = onClose, onSaved = onSaved)
                    },
                    // 저장 중에는 못 누른다 — 두 번 누르면 PATCH가 두 번 나간다.
                    enabled = !vm.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
