package com.catchflower.app.data

import android.content.Context
import java.io.File

/**
 * 회원 탈퇴의 **기기 쪽**. 서버 쪽은 [AccountDeletionService]가 한다.
 *
 * 2026-08-16 출시 준비.
 *
 * ## 🔴 이 함수를 부르는 조건은 하나뿐이다
 *
 * [com.catchflower.app.core.AccountDeletionRules.mayWipeDevice]가 true일 때만 부른다.
 * 서버가 하나라도 실패한 상태에서 여기를 부르면 **토큰이 사라져서 그 계정으로 다시
 * 로그인할 수 없고**, 서버에는 발견 기록과 계정이 그대로 남는다. 사용자 화면은
 * 성공과 똑같다 — 이 기능에서 가장 나쁜 결과이고 증상이 없다.
 *
 * ## 🔴 사진은 여기서만 사라진다
 *
 * 사진은 서버에 올라간 적이 없다(개인정보 처리방침 1항). 즉 `filesDir/photos/`를
 * 지우는 이 코드가 **약속을 지키는 유일한 곳**이다 — 서버 삭제가 성공해도 이걸
 * 빼먹으면 기기에 사진이 남고, 처리방침 6항 가가 거짓이 된다.
 */
object LocalDataWiper {

    /**
     * 앱이 기기에 만든 것을 전부 지운다.
     *
     * 지우는 것: `filesDir` 전체(사진 · `discoveries.json`) · `cacheDir` 전체 ·
     * `SharedPreferences("catchflower")`(`local_user_id` · `onboarding_done` ·
     * `auth_*` 토큰 4개).
     *
     * ⚠️ **파일 하나하나를 이름으로 지우지 않는다.** 목록으로 두면 새 파일이 생긴 날
     *    거기 추가하는 것을 잊고, 그 파일만 남는다 — 그리고 아무 증상이 없다.
     *    `filesDir` 전체를 지우는 쪽이 "앱이 만든 것"과 정확히 같다.
     *
     * ⚠️ 도감 일러스트·`flowers.json`은 `assets`에 있어서 여기에 없다(빌드 산출물이다).
     *    지울 필요도 없고 지울 수도 없다 — 개인 데이터가 아니다.
     *
     * @return 지우지 못한 파일 수. **0이 아니어도 탈퇴는 성공으로 본다** —
     *   서버 데이터는 이미 없고, 남은 것은 앱을 지우면 사라지는 파일이다.
     *   실패로 되돌리면 사용자는 이미 계정이 없는데 "다시 시도"만 반복한다.
     */
    fun wipe(context: Context): Int {
        var failed = 0
        failed += clearChildren(context.filesDir)
        failed += clearChildren(context.cacheDir)
        // 🔴 prefs는 파일이 아니라 `clear()`로 지운다. `files`처럼 파일을 지우면
        //    이미 메모리에 올라온 값이 다시 써지면서 **토큰이 되살아난다.**
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit() // ⚠️ `apply()`가 아니다 — 바로 뒤에 프로세스를 재시작한다.
        return failed
    }

    /** 디렉터리 **안**을 비운다(디렉터리 자체는 남긴다 — 시스템이 만든 것이다). */
    private fun clearChildren(dir: File): Int {
        val children = dir.listFiles() ?: return 0
        var failed = 0
        for (child in children) {
            if (!child.deleteRecursively()) failed++
        }
        return failed
    }

    /** [AuthService] · [OnboardingState] · [LocalUser]가 **같은 파일 하나**를 쓴다. */
    private const val PREFS = "catchflower"
}
