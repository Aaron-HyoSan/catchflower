package com.catchflower.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReactionService]가 쓰는 이름이 **실제 마이그레이션 SQL과 같은가.**
 *
 * ## 왜 이 테스트가 있나
 *
 * 🔴 **이 층은 지금 실서버에 대고 확인할 수가 없다.** `0007_likes_comments.sql`이
 *    아직 적용되지 않아서 무엇을 물어도 `PGRST202`(함수 없음)·`PGRST205`(테이블 없음)가
 *    온다. 즉 **인자 이름을 틀리게 써도, 컬럼 이름을 틀리게 써도, 응답이 똑같다.**
 *    적용된 뒤에도 그 두 코드는 여전히 겹친다 — "아직 배포 안 됨"과 "이름을 틀림"이
 *    같은 응답이라 **로그를 봐도 원인을 모른다.**
 *
 * 🔴 그리고 [ReactionServiceTest]는 이걸 못 잡는다. 그쪽 응답은 **내가 만든 것**이라
 *    내가 `discovery_id`로 쓰면 픽스처도 `discovery_id`가 되어 **함께 틀리고 함께 초록**이다
 *    ((22)에서 실제로 그랬다: 상상한 형식으로 쓴 테스트가 통과하고 앱이 안 됐다).
 *    그래서 픽스처가 아니라 **SQL 원문**을 읽어서 대조한다.
 *
 * ⚠️ **이 파일이 빨개지면 SQL이 아니라 코드를 고친다.** SQL은 오너가 그대로 붙여넣는
 *    원본이고 서버가 그걸로 만들어진다. 다만 SQL을 의도적으로 바꿨다면
 *    `공유계약_iOS_AOS.md`를 먼저 고치는 것이 순서다(혼자 바꾸지 않는다).
 */
class ReactionContractTest {

    private val projectRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase").isDirectory) dir = dir.parentFile
        dir ?: throw AssertionError(
            "프로젝트 루트를 못 찾았다. 경로가 바뀌었으면 이 테스트를 고친다 — " +
                "건너뛰게 만들면 계약 검증이 조용히 사라진다",
        )
    }

    private fun sql(name: String): String {
        val f = File(projectRoot, "supabase/migrations/$name")
        assertTrue("$name 이 없다 — 파일이 옮겨졌으면 이 테스트를 고친다", f.isFile)
        return f.readText()
    }

    private val source: String by lazy {
        val f = File("src/main/java/com/catchflower/app/data/ReactionService.kt")
        assertTrue("ReactionService.kt를 못 찾았다", f.isFile)
        f.readText()
    }

    /**
     * 빨개지는 경우: 0007의 시그니처가 `d_id`가 아닌 다른 이름으로 바뀌었거나,
     * 코드가 `discovery_id`로 보내게 됐을 때. 실서버 증상은 **`PGRST202`뿐이고
     * 그건 "아직 배포 안 됨"과 같은 코드**라 원인을 못 가린다.
     */
    @Test
    fun RPC_인자_이름이_서버_시그니처와_같다() {
        val migration = sql("0007_likes_comments.sql")
        val sig = Regex("""create or replace function public\.discovery_reactions\(\s*(\w+)\s+uuid\s*\)""")
            .find(migration)
        assertTrue("0007에 discovery_reactions 정의가 없다", sig != null)
        val argName = sig!!.groupValues[1]

        val inCode = Regex("""ARG_DISCOVERY = "([^"]+)"""").find(source)
        assertTrue("ARG_DISCOVERY 상수를 못 찾았다", inCode != null)
        assertEquals(
            "RPC 인자 이름이 서버와 다르다 — 실서버에서는 PGRST202로만 보인다",
            argName,
            inCode!!.groupValues[1],
        )
    }

    /**
     * 빨개지는 경우: `returns table`의 칸 이름이 바뀌었을 때. 코드가 못 읽으면
     * `PARSE` 실패가 되고, **한 칸만 바뀌면 그 칸만 0이 된다.**
     */
    @Test
    fun 반응_함수의_칸_이름_세_개를_그대로_읽는다() {
        val migration = sql("0007_likes_comments.sql")
        val returns = Regex(
            """create or replace function public\.discovery_reactions\([^)]*\)\s*returns table \(([^)]*)\)""",
        ).find(migration)
        assertTrue("discovery_reactions의 returns table을 못 찾았다", returns != null)
        val columns = returns!!.groupValues[1]
            .split(',')
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotEmpty() }

        assertEquals(3, columns.size)
        for (c in columns) {
            assertTrue("서버가 주는 칸 `$c`을 코드가 읽지 않는다", source.contains("\"$c\""))
        }
    }

    /**
     * 빨개지는 경우: `likes`·`comments`의 컬럼명이 바뀌었을 때.
     *
     * ⚠️ **`user_id`를 통째로 찾지 않고 `likes` 정의 안에서 찾는다.** 이 저장소에는
     *    `user_id`가 여러 테이블에 있어서 "어딘가에 있다"는 검사는 아무것도 안 잰다.
     */
    @Test
    fun 좋아요_테이블의_키_두_칸을_그대로_쓴다() {
        val body = tableBody(sql("0007_likes_comments.sql"), "likes")
        val cols = columnsOf(body)
        // PK가 (discovery_id, user_id)라는 것이 0007의 핵심이다 —
        // id 컬럼이 생기면 **같은 사람이 두 번 좋아요**를 넣을 수 있다.
        assertTrue("likes에 id 컬럼이 생겼다 — 중복 좋아요가 가능해진다", "id" !in cols)
        assertTrue("discovery_id" in cols)
        assertTrue("user_id" in cols)
        assertTrue(source.contains("""COL_DISCOVERY_ID = "discovery_id""""))
        assertTrue(source.contains("""COL_USER_ID = "user_id""""))
    }

    /**
     * 빨개지는 경우: 서버 `comments_body_len`의 상한이 바뀌었는데
     * [com.catchflower.app.core.GamePolicy.COMMENT_MAX_LENGTH]는 그대로일 때.
     *
     * 🔴 **두 값이 갈리면 증상이 방향에 따라 다르다.** 서버가 더 짧으면 사용자가 쓴
     *    댓글이 `23514`로 거부되는데 화면은 `연결이 불안정해요`를 띄운다(자기 입력이
     *    원인인데 네트워크 탓으로 보인다). 서버가 더 길면 클라이언트가 **서버가
     *    받아 줄 댓글을 거절한다.** C-9는 아직 오너 미답이라 바뀔 수 있는 값이다.
     */
    @Test
    fun 댓글_길이_상한이_서버_check와_같다() {
        val migration = sql("0007_likes_comments.sql")
        val check = Regex("""char_length\(body\) between (\d+) and (\d+)""").find(migration)
        assertTrue("comments_body_len check를 못 찾았다", check != null)
        assertEquals("서버가 빈 댓글을 허용하게 바뀌었다", 1, check!!.groupValues[1].toInt())
        assertEquals(
            "서버 상한과 GamePolicy.COMMENT_MAX_LENGTH가 다르다",
            check.groupValues[2].toInt(),
            com.catchflower.app.core.GamePolicy.COMMENT_MAX_LENGTH,
        )
    }

    /**
     * 빨개지는 경우: C-9 "수정 불가"를 뒤집는 `update` 정책이 생겼을 때, 혹은
     * `deleted_at is not null` 조건이 빠졌을 때. 그러면 클라이언트의 PATCH가
     * **본문 수정 경로**가 된다.
     */
    @Test
    fun 댓글은_soft_delete만_허용된다() {
        val migration = sql("0007_likes_comments.sql")
        val policies = Regex("""create policy (\w+) on public\.comments\s+for (\w+)""")
            .findAll(migration).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val updates = policies.filter { it.second == "update" }
        assertEquals("comments의 update 정책은 하나뿐이어야 한다", 1, updates.size)
        assertEquals("comments_soft_delete", updates[0].first)
        assertTrue(
            "soft delete 정책에 `deleted_at is not null`이 없다 — 본문 수정이 열린다",
            migration.contains("deleted_at is not null"),
        )
        // 🔵 **코드가 `deleted_at`을 보내는지 보던 단정을 지웠다 (2026-08-12).**
        //    이 층은 더 이상 PATCH를 쓰지 않는다 — 0009의 `delete_comment` RPC를 부르고
        //    삭제 시각은 **서버 `now()`**가 채운다. 클라이언트가 `deleted_at`을 보내는 것
        //    자체가 이제 되돌아간 신호이므로, 반대로 **없는지**를 본다.
        //    ⚠️ `comments_soft_delete` 정책은 그대로 살아 있어야 한다(위 단정) — 그 정책으로는
        //       이제 아무도 성공할 수 없고, 그게 의도다(직접 PATCH를 전부 막는다 · 0009 머리말).
        assertTrue(
            "코드가 다시 `deleted_at`을 실어 PATCH를 보낸다 — 그 경로는 원리상 42501이다 " +
                "(`update`의 새 행에도 `select` 정책이 걸린다 · 0009). 삭제는 RPC 한 길뿐이다",
            !Regex("""put\("deleted_at"""").containsMatchIn(source),
        )
    }

    /**
     * 빨개지는 경우: 남의 닉네임을 `users`에서 받게 코드가 바뀌었을 때.
     * `users`의 RLS는 본인 행만 주므로 **남의 이름이 조용히 빈다** — 화면 16의
     * 댓글은 대부분 남의 것이라 거의 전부가 이름 없이 뜬다.
     */
    @Test
    fun 남의_닉네임은_공개뷰에서만_받는다() {
        val init = sql("0001_init.sql")
        assertTrue(
            "public_profiles 뷰가 없어졌다",
            init.contains("create or replace view public.public_profiles"),
        )
        // `users_read_self`가 살아 있다는 것이 이 규칙의 근거다.
        assertTrue("users_read_self 정책이 없다", init.contains("users_read_self"))
        assertTrue("코드가 public_profiles를 안 쓴다", source.contains("rest/v1/public_profiles"))
        assertTrue(
            "코드가 users를 직접 조회한다 — 남의 이름이 빈다",
            !source.contains("rest/v1/users"),
        )
    }

    /**
     * 빨개지는 경우: `reports`의 신고자 컬럼명이 바뀌었을 때. 지금은 `reporter_id`이고
     * 다른 테이블들과 달라서 **`user_id`로 쓰기 쉽다** — 그러면 `PGRST204`로 전부 실패하고,
     * 신고는 결과를 화면에서 확인할 방법이 없어서(정책상 못 읽는다) 아무도 모른다.
     */
    @Test
    fun 신고_컬럼_이름을_그대로_쓴다() {
        val cols = columnsOf(tableBody(sql("0001_init.sql"), "reports"))
        assertTrue("reporter_id" in cols)
        assertTrue("reason" in cols)
        assertTrue(source.contains("""put("reporter_id""""))
    }

    /**
     * ✅ **화면이 이 층을 부른다** (2026-08-12에 뒤집었다).
     *
     * ## 이 검사가 어떻게 바뀌었나
     *
     * 원래는 `아직_어느_화면도_이_층을_부르지_않는다`였다 — 0007이 미적용이고 남의 기록을
     * 주는 경로가 없어서 **부르는 곳이 0개**였고, 그 사실을 검사로 못 박아 뒀다
     * (`읽는 사람이 0명인 데이터`는 "됐다"고 착각하게 만드는 자리다).
     * 화면 15·16이 붙으면서 **의도대로 빨개졌고**, 그래서 방향을 뒤집었다.
     *
     * 🔴 이제 빨개지는 경우: **화면 16을 지우거나 반응 층 호출을 되돌렸을 때.**
     *    그러면 `ReactionService`는 다시 아무도 안 부르는 코드가 되는데,
     *    `구현현황_AOS.md`에는 ✅가 남는다 — 그 어긋남을 여기서 잡는다.
     */
    @Test
    fun 화면이_반응_층을_부른다() {
        val ui = File("src/main/java/com/catchflower/app/ui")
        assertTrue(ui.isDirectory)
        val callers = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("ReactionService") || t.contains("ReactionSource")
            }
            .map { it.name }
            .toList()
        assertTrue(
            "반응 층을 부르는 화면이 없어졌다 — 화면 16을 지웠으면 구현현황_AOS.md의 " +
                "✅와 ReactionService의 '화면이 부른다' 주석을 같이 되돌린다",
            callers.isNotEmpty(),
        )
        assertTrue(
            "RecordViewModel.kt가 이 층을 안 부른다 — 화면 16의 반응·댓글 조립이 " +
                "다른 곳으로 옮겨졌으면 이 검사를 고친다: $callers",
            "RecordViewModel.kt" in callers,
        )
    }

    /**
     * 🔴 **Composable이 [ReactionService]를 직접 만들지 않는다.**
     *
     * 위 검사는 "부르는 곳이 있다"만 본다. 그런데 **어디서 부르는지가 더 중요하다** —
     * `@Composable` 안에서 `ReactionService(...)`를 만들면 recomposition마다 새 인스턴스가
     * 생기고, 그때마다 **토큰 갱신 상태가 초기화된다.** 증상은 며칠 뒤에 나온다:
     * refresh 토큰이 회전한 기기에서 좋아요만 조용히 실패한다(화면은 낙관적 갱신 때문에
     * **성공한 것처럼 보이고** 다음에 열면 숫자가 되돌아 있다).
     *
     * 그래서 생성은 ViewModel의 기본 인자에서만 한다 — `DiscoveryRepository`와
     * **같은 `AuthService`**를 쓰는 것도 그 자리에서만 보장된다.
     *
     * 빨개지는 경우: 화면(`*Screen.kt`)에서 `ReactionService(`를 만들 때.
     */
    @Test
    fun 화면_파일이_반응_층을_직접_만들지_않는다() {
        val ui = File("src/main/java/com/catchflower/app/ui")
        val offenders = ui.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") }
            .filter { f ->
                // 주석은 뺀다 — 이 판단을 주석으로 적어 두는 관행이 있어서
                // 안 빼면 **설명이 위반으로 걸린다**(ButtonLabelSourceTest와 같은 함정).
                val body = f.readText()
                    .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                    .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
                Regex("""ReactionService\s*\(""").containsMatchIn(body)
            }
            .map { it.name }
            .toList()
        assertEquals(
            "Composable이 ReactionService를 직접 만든다 — recomposition마다 토큰 상태가 " +
                "초기화되고, 증상은 며칠 뒤 좋아요 실패로만 나온다: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * 🔴 **댓글 삭제가 서버에서 막혀 있다는 사실을 못 박는다** (2026-08-12).
     *
     * ## 무엇이 확정됐나
     *
     * `deleteComment`는 실기기에서 `Failed(403, 42501)`다. 0008 머리말은 원인을
     * "서버에 붙은 식이 파일과 다르다"로 **추측**했는데 그게 틀렸다 — 오너가 0008을
     * 적용하고 진단 8행이 **전부 기대와 일치**했는데도 403이 그대로였다.
     *
     * 로컬 Postgres 16.2에 0001~0008을 그대로 올려 `set role authenticated`로 재현했고,
     * 원인은 **Postgres 규칙**이었다: `update`의 새 행에도 `select` 정책이 적용된다.
     * `comments_read`가 `deleted_at is null`을 요구하므로(0007 3절) soft delete는
     * **자기 행을 자기 SELECT 정책에서 사라지게** 만든다.
     *
     * 표 하나로 규칙을 못 박았다 — 갈림선은 **행을 읽어야 하나**다:
     * ```
     * update t set gone = true where id = 2       → 42501
     * update t set gone = true                     → 통과 (WHERE·RETURNING이 없다)
     * update t set gone = true returning id        → 42501
     * ```
     * PostgREST의 PATCH는 항상 `?id=eq.…`를 WHERE로 만든다 — **클라이언트가 우회할
     * 방법이 없다.** 즉 이건 앱에서 고칠 수 있는 결함이 아니다.
     *
     * ## 이 검사가 빨개지는 경우
     *
     * ① 누군가 `deleteComment`를 "고쳤다" — 클라이언트 쪽 우회는 존재하지 않으므로
     *    무엇을 했든 원인을 안 고친 것이다(제안 파일 적용이 순서다).
     * ② 화면에 댓글 삭제 버튼을 붙였다 — **지금 붙이면 무조건 실패하는 버튼**이 된다.
     *    `죽은 버튼`은 이 저장소가 이미 세 번 만든 것이다(`증상 없는 UI 결함` 4종).
     * ③ 제안 파일이 사라졌다 — 원인 기록이 사라지면 다음 세션이 **또 추측한다**.
     *
     * ## ✅ 승격됐다 (2026-08-12) — 그래서 이 검사는 **뒤집혔다**
     *
     * 오너가 0009를 서버에 적용했고(진단 6행 ✅) `0009 승격해라`로 승인했다. 그래서
     * `supabase/migrations/0009_comment_delete_rpc.sql`이 **있어야 한다** — 없으면
     * 합본(`01_스키마_전체.sql`)에서 이 고침이 빠지고, 새 환경에서 댓글 삭제가 다시
     * 403이 되는데 **진단 19행은 전부 ✅라서 원인을 처음부터 다시 찾게 된다**(그게 (65)다).
     *
     * ⚠️ **뒤집힌 것은 ②뿐이다.** 가드 검사(①)는 그대로 두고 **승격된 파일**을 본다 —
     *    그 함수는 `security definer`라 RLS를 지나가므로 자물쇠가 본문 안에만 있고,
     *    실제로 첫 판이 뚫려 **anon이 남의 댓글을 지웠다.** ③(화면이 안 부른다)도
     *    아직 유효하다 — 버튼은 A 문서에 문구를 넣은 뒤에 붙인다.
     */
    @Test
    fun 댓글_삭제_고침이_마이그레이션에_있고_가드가_살아_있다() {
        // ① 승격된 마이그레이션이 있다. 🔴 **`제안/`이 아니라 여기를 본다** —
        //    합본 빌더가 훑는 곳이 `migrations/`이고, 서버에 실제로 가는 것은 이 파일이다.
        val promoted = File(projectRoot, "supabase/migrations/0009_comment_delete_rpc.sql")
        assertTrue(
            "0009가 `migrations/`에 없다 — 서버에는 적용됐는데(진단 6행 ✅) 저장소에 " +
                "없으면 **합본에서 이 고침만 조용히 빠진다.** 그 환경에서 댓글 삭제는 " +
                "다시 403이고 진단 19행은 전부 ✅다: ${promoted.path}",
            promoted.isFile,
        )
        val sql = promoted.readText()
        assertTrue(
            "승격된 파일에 `delete_comment` RPC가 없다 — 다른 파일로 바뀌었으면 이 검사를 고친다",
            sql.contains("function public.delete_comment(c_id uuid)"),
        )
        assertTrue(
            "승격된 파일이 `security definer`가 아니다 — invoker면 같은 42501에 다시 걸린다",
            sql.contains("security definer"),
        )
        // 🔴 **서버에 간 것과 저장소가 같은가.** 오너가 적용한 것은 `제안/`의 원본이므로,
        //    승격판의 **실행 SQL**이 그것과 달라지면 저장소가 서버와 갈린다 — 그런데
        //    합본은 승격판을 쓰므로 다음 환경만 조용히 달라진다(증상은 며칠 뒤).
        //    ⚠️ 머리말 주석은 다르다(승격 사실을 적었다). 그래서 **주석을 뗀 뒤** 비교한다.
        val proposal = File(projectRoot, "프로젝트 맥락/제안/0009_제안_댓글삭제_RPC.sql")
        assertTrue(
            "제안 원본이 없어졌다 — 오너가 적용한 것이 그 파일이라 대조할 기준이 사라진다: " +
                proposal.path,
            proposal.isFile,
        )
        val codeOnly = { s: String ->
            s.lineSequence()
                .map { it.substringBefore("--") }
                .filter { it.isNotBlank() }
                .joinToString("\n") { it.trimEnd() }
        }
        assertEquals(
            "승격판의 실행 SQL이 오너가 적용한 제안 원본과 다르다 — 합본은 승격판을 쓰므로 " +
                "**서버와 저장소가 갈린다.** 고칠 것이 있으면 새 마이그레이션을 더한다",
            codeOnly(proposal.readText()),
            codeOnly(sql),
        )

        // 🔴 **함수 본문만 본다.** 파일 전체에서 찾으면 안 된다 — 이 파일의 진단 절이
        //    `prosrc like '%auth.uid() is null%'` 로 **그 글자를 그대로 들고 있고**,
        //    주석에도 설명이 적혀 있다. 그래서 처음에 파일 전체를 `contains`로 봤더니
        //    **가드를 `if false then`으로 바꿨는데도 초록이었다**(돌연변이 M3 실측).
        //    ⚠️ 이 저장소에서 **네 번째** 같은 사고다: 내가 남긴 설명·진단·취소선 기록이
        //       검사의 입력으로 세어졌다(`ButtonLabelSourceTest` 산문 · `PhotoLoaderTest`
        //       KDoc · `CopySourceTest` 취소선 · 그리고 여기).
        //    → **`$$ … $$` 안쪽으로 좁히고 `--` 주석을 떼어 낸다.**
        val bodyStart = sql.indexOf("as $$")
        val bodyEnd = sql.indexOf("$$;", bodyStart + 1)
        assertTrue(
            "함수 본문(`as $$ … $$;`)을 못 찾았다 — 제안 파일 구조가 바뀌었으면 이 검사를 고친다",
            bodyStart in 0 until bodyEnd,
        )
        val fnBody = sql.substring(bodyStart, bodyEnd)
            .lineSequence()
            .map { it.substringBefore("--") }   // SQL 주석 제거
            .joinToString("\n")

        // anon 가드 두 겹. 첫 판이 여기서 뚫려 **anon이 남의 댓글을 지웠다**
        // (`= null`이 false가 아니라 null이라 `if not null`이 분기를 건너뛴다).
        assertTrue(
            "제안 파일 **함수 본문**에 anon 가드가 두 겹으로 없다 — `auth.uid() is null` + " +
                "`coalesce`. 첫 판은 이게 없어서 로컬 실측에서 anon이 남의 댓글을 지웠다. " +
                "⚠️ 진단 절이나 주석에 그 글자가 있는 것은 승인이 아니다",
            fnBody.contains("auth.uid() is null") && fnBody.contains("coalesce("),
        )
        // 권한 판정 두 갈래도 본문에 있어야 한다(진단 3번이 서버에서 세는 것과 같은 것을
        // 여기서는 파일로 센다 — 서버에 가기 전에 잡는 게 목적이다).
        assertTrue(
            "함수 본문이 권한을 세지 않는다 — `security definer`는 RLS를 지나가므로 " +
                "이게 빠지면 **아무나 남의 댓글을 지운다**",
            fnBody.contains("c_user_id = auth.uid()") &&
                fnBody.contains("is_discovery_owner(c_discovery_id)"),
        )

        // ② 🔴 **합본에 실제로 들어갔는가.** 파일이 `migrations/`에 있는 것과 오너가
        //    붙여넣는 파일에 들어간 것은 **다르다** — `build_합본.py`를 다시 돌리지
        //    않으면 합본은 옛 내용 그대로다. 이 저장소가 그 사고를 이미 한 번 냈다
        //    (커밋 `785a3d6` "합본에 0008이 없었다" — **문법 검사는 PASS였다**).
        //    🔴 **이 검사는 `./gradlew test`만으로는 안 돈다.** Gradle은 `오너_실행/`이나
        //    `supabase/`를 이 태스크의 입력으로 모르므로, 합본을 옛 판으로 되돌려도
        //    **UP-TO-DATE로 건너뛰고 초록이 나온다**(돌연변이 M2에서 실제로 그랬다 —
        //    검사가 아니라 검사를 안 돌린 것이었다). `--rerun-tasks`를 주면 잡힌다.
        //    ⚠️ 그래서 스키마·문서를 고친 뒤에는 **`--rerun-tasks`로 한 번 돌린다.**
        val combined = File(projectRoot, "오너_실행/01_스키마_전체.sql")
        assertTrue("합본 파일이 없다: ${combined.path}", combined.isFile)
        val combinedText = combined.readText()
        assertTrue(
            "합본(`01_스키마_전체.sql`)에 `delete_comment`가 없다 — `migrations/`에는 " +
                "파일이 있는데 합본을 다시 빌드하지 않은 것이다. **새 환경에 붙여넣으면 " +
                "이 고침만 빠진다.** `python3 오너_실행/build_합본.py`를 돌린다",
            combinedText.contains("function public.delete_comment(c_id uuid)"),
        )
        assertTrue(
            "합본에 0009 파일 표시가 없다 — 빌더가 이 파일을 목록에 안 넣었다",
            combinedText.contains("0009_comment_delete_rpc.sql"),
        )
        // 🔴 합본이 **가드까지** 담고 있는가. 함수 이름만 확인하면 본문이 옛 판이어도 통과한다.
        assertTrue(
            "합본의 delete_comment에 anon 가드가 없다 — 이름만 같고 본문이 옛 판이다",
            combinedText.contains("if auth.uid() is null then"),
        )

        // ③ ✅ **화면이 삭제를 부른다** (2026-08-12에 뒤집었다 — 0009가 적용됐다).
        //
        // 원래는 "부르지 않는다"였다. 서버가 막혀 있던 동안은 붙이면 **누르면 무조건
        // 실패하는 버튼**이었기 때문이다. 0009가 적용되고 A 문서에 문구를 넣은 뒤 붙였다.
        //
        // 🔴 이제 빨개지는 경우: **삭제 버튼을 지웠을 때.** 그러면 `deleteComment`는
        //    다시 아무도 안 부르는 코드가 되는데 `구현현황_AOS.md`에는 기록이 남는다.
        val ui = File("src/main/java/com/catchflower/app/ui")
        val callers = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val body = f.readText()
                    .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                    .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
                body.contains("deleteComment")
            }
            .map { it.name }
            .toList()
        assertTrue(
            "화면이 `deleteComment`를 안 부른다 — 삭제 버튼을 지웠으면 " +
                "`구현현황_AOS.md`와 A 문서 3절 `화면 16 댓글 삭제`도 같이 되돌린다",
            callers.isNotEmpty(),
        )
        assertTrue(
            "`RecordViewModel.kt`가 삭제를 안 부른다 — 삭제 조립이 다른 곳으로 옮겨졌으면 " +
                "이 검사를 고친다: $callers",
            "RecordViewModel.kt" in callers,
        )

        // 🔴 **`vm.deleteComment(`를 실제로 부르는 화면이 있는가 — 정의만으로는 안 된다.**
        //
        // ⚠️ **이 단정이 없을 때 돌연변이가 살아남았다**(M6 실측). 다이얼로그의 `지우기`를
        //    `CfToast.NOT_READY`로 바꿔 **화면에서 삭제 호출을 통째로 지웠는데 초록이었다** —
        //    위 두 단정은 `RecordViewModel.kt`가 `deleteComment`라는 글자를 **들고 있는지**만
        //    봤고, 그 파일에는 함수 **정의**가 남아 있었다. 즉 검사가 "부른다"가 아니라
        //    "정의돼 있다"를 재고 있었다. `DeadButtonTest`도 못 잡는다(`onClick`이 비어
        //    있지 않다) — `삭제` 버튼은 A 문서에 있으니 라벨 검사도 통과한다.
        //    → **부르는 자리를 센다.** 이게 `읽는 사람이 0명인 데이터`의 그 자리다.
        val invocations = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val body = f.readText()
                    .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                    .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
                Regex("""\bvm\s*\.\s*deleteComment\s*\(""").containsMatchIn(body)
            }
            .map { it.name }
            .toList()
        assertTrue(
            "`vm.deleteComment(`를 부르는 화면이 없다 — ViewModel에 함수만 남고 **누르는 " +
                "자리가 사라졌다.** 버튼을 `아직 준비 중이에요`로 되돌렸으면 A 문서 3절 " +
                "`화면 16 댓글 삭제`와 `구현현황_AOS.md`도 같이 되돌린다",
            invocations.isNotEmpty(),
        )
        // 🔴 **Composable이 이 층을 직접 부르지 않는다 — ViewModel을 지난다.** 삭제는
        //    성공 뒤 목록을 다시 읽어야 하고(낙관적 갱신 금지) 그건 화면의 일이 아니다.
        //    화면에서 데이터 층을 직접 부르면 `viewModelScope`가 아니라 컴포지션 수명에
        //    묶여, **스크롤로 항목이 사라지면 응답을 아무도 안 읽는다.**
        //
        // ⚠️ **이 검사의 첫 red는 검사가 틀린 것이었다.** 처음에 `Screen.kt`가
        //    `deleteComment`라는 글자를 담고 있으면 위반으로 봤는데, 화면은
        //    **`vm.deleteComment(...)`** 를 부른다 — 그게 바로 이 검사가 원하는 모양이다.
        //    즉 검사가 정답을 위반으로 잡았다(`계측기는 대조군으로 먼저 잰다`).
        //    → 그래서 **수신자를 본다.** 화면의 삭제 호출은 전부 `vm.`이어야 한다.
        //    ⚠️ "`vm.`이 아닌 것"을 부정 lookbehind로 찾으면 안 된다 —
        //       `src.deleteComment(`도 점 뒤라서 함께 빠진다(대조군으로 확인했다).
        //       **수신자를 꺼내서 `vm`인지 본다.**
        val directCallers = ui.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") }
            .filter { f ->
                val body = f.readText()
                    .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
                    .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
                Regex("""(\w+)?\s*\.?\s*deleteComment\s*\(""").findAll(body)
                    .any { it.groupValues[1] != "vm" }
            }
            .map { it.name }
            .toList()
        assertEquals(
            "화면 파일이 `deleteComment`를 ViewModel 없이 부른다 — 컴포지션이 사라지면 " +
                "응답을 아무도 안 읽는다: $directCallers",
            emptyList<String>(),
            directCallers,
        )
    }

    /**
     * 🔴 **RPC 인자·함수 이름이 0009의 시그니처와 같은가.**
     *
     * 위 검사는 파일이 있고 가드가 살아 있는지를 본다. 이름이 어긋나는 것은 다른 실패다 —
     * 서버는 `PGRST202`(함수 없음)를 주는데 그건 **"0009가 아직 적용 안 됐다"와 같은
     * 코드**라, 로그를 봐도 원인을 못 가린다(`RPC_인자_이름이_서버_시그니처와_같다`가
     * `d_id`에 대해 하는 것과 같은 검사다).
     *
     * ⚠️ **`ReactionServiceTest`는 이걸 못 잡는다** — 그쪽 픽스처는 내가 만든 것이라
     *    `comment_id`로 쓰면 단정도 `comment_id`가 되어 **함께 틀리고 함께 초록**이다.
     */
    @Test
    fun 삭제_RPC_이름이_0009와_같다() {
        val migration = sql("0009_comment_delete_rpc.sql")
        val sig = Regex("""create or replace function public\.(\w+)\(\s*(\w+)\s+uuid\s*\)""")
            .find(migration)
        assertTrue("0009에서 함수 시그니처를 못 찾았다", sig != null)
        val (fnName, argName) = sig!!.groupValues[1] to sig.groupValues[2]

        val fnInCode = Regex("""FN_DELETE_COMMENT = "([^"]+)"""").find(source)
        assertTrue("FN_DELETE_COMMENT 상수를 못 찾았다", fnInCode != null)
        assertEquals(
            "삭제 RPC 함수 이름이 서버와 다르다 — 실서버에서는 PGRST202로만 보이고, " +
                "그건 `0009 미적용`과 같은 코드다",
            fnName,
            fnInCode!!.groupValues[1],
        )

        val argInCode = Regex("""ARG_COMMENT = "([^"]+)"""").find(source)
        assertTrue("ARG_COMMENT 상수를 못 찾았다", argInCode != null)
        assertEquals(
            "삭제 RPC 인자 이름이 서버와 다르다 — 같은 PGRST202다",
            argName,
            argInCode!!.groupValues[1],
        )

        // 🔴 **`false`를 성공으로 접지 않는가.** 서버가 `returns boolean`이고
        //    **실패도 HTTP 200**이므로(0009 1절), 본문을 안 읽는 `sendUnit`으로 부르면
        //    모든 거절이 성공이 된다 — 그러면 화면이 서버에 남아 있는 댓글을 지운다.
        assertTrue(
            "0009가 `returns boolean`이 아니게 바뀌었다 — 이 검사의 전제가 깨졌다",
            migration.contains("returns boolean"),
        )
        val fnBodyInCode = Regex(
            """override suspend fun deleteComment\([^)]*\)[^{]*\{([\s\S]*?)\n    \}""",
        ).find(source)
        assertTrue("코드에서 deleteComment 본문을 못 찾았다", fnBodyInCode != null)
        val impl = fnBodyInCode!!.groupValues[1]
            .lineSequence().map { it.substringBefore("//") }.joinToString("\n")
        assertTrue(
            "삭제가 `sendUnit`으로 보내진다 — 그 경로는 **본문을 안 읽어서** HTTP 200 + " +
                "`false`(거절)를 성공으로 만든다. 0008의 `남의 댓글 삭제도 204 + 0행`과 같다",
            !impl.contains("sendUnit"),
        )
        assertTrue(
            "삭제 응답에서 `false`를 판정하지 않는다 — 거절이 성공으로 접힌다",
            impl.contains("\"false\"") || impl.contains("res.value"),
        )
    }

    /**
     * 🔴 **삭제 결과의 배선** — 성공 문구가 실패 분기에 없고, 성공이면 목록을 다시 읽는가.
     *
     * ⚠️ **두 돌연변이가 살아남아서 만든 검사다**(M8·M9 실측 · 536개 전부 초록이었다):
     *    - **M8**: 실패 분기(`deleteToastIsNetwork` → `NETWORK_ERROR`)의 결과를
     *      `COMMENT_DELETED`로 바꿨다. 화면은 **안 지워진 댓글에 `댓글을 지웠어요`**를
     *      띄운다. 뒤이어 목록을 다시 읽어도 그 줄은 그대로라, 사용자는 "삭제가 실패했다"가
     *      아니라 **"지웠는데 다시 생긴다"**로 읽는다 — 원인이 정반대로 보고된다.
     *    - **M9**: 성공 분기의 `loadAll(discovery)`를 지웠다. 토스트만 뜨고 **지운 댓글이
     *      화면에 남는다**(`댓글 3`도 안 줄어든다). 낙관적 갱신 금지의 **반대편 구멍**이다 —
     *      낙관적으로 지우지도 않고 다시 읽지도 않으면 화면은 영원히 옛 목록이다.
     *
     * 두 결함의 공통점은 **화면이 완벽히 정상으로 보인다**는 것이다(`증상 없는 UI 결함`의
     * `같은 얼굴의 다른 원인`). Composable과 `viewModelScope`는 JVM 테스트가 못 도는
     * 층이라(`구현현황_AOS.md`), 이 저장소는 그 층을 **소스 텍스트로** 잰다.
     */
    @Test
    fun 삭제_결과_배선이_문구와_다시읽기를_지킨다() {
        // ① 화면: 세 분기의 문구가 서로 다른 값이고, 성공 문구는 성공 분기에만 있다.
        val screen = code("ui/place/RecordDetailScreen.kt")
        val at = screen.indexOf("vm.deleteComment(")
        assertTrue(
            "화면에서 `vm.deleteComment(` 호출을 못 찾았다 — 위 검사가 먼저 빨개져야 한다",
            at >= 0,
        )
        val whenBlock = braceBlock(screen, screen.indexOf("when {", at))
        // 🔴 `->`가 있는 줄만 본다. 조건과 결과를 **따로** 봐야 M8이 잡힌다 —
        //    "세 문구가 다 등장하는가"만 보면 자리만 바뀐 M8은 통과한다.
        val branches = whenBlock.lines()
            .filter { it.contains("->") }
            .map { it.substringBefore("->").trim() to it.substringAfter("->").trim() }
        assertEquals(
            "삭제 결과 분기가 세 개가 아니다 — 성공 / 네트워크 / 거절이다: $branches",
            3,
            branches.size,
        )
        val toastOf = { cond: String ->
            branches.firstOrNull { cond in it.first }
                ?.second
                ?.let { Regex("""CfToast\.(\w+)""").find(it)?.groupValues?.get(1) }
        }
        assertEquals(
            "성공(`pgCode == null`)에 성공 문구를 안 쓴다: $branches",
            "COMMENT_DELETED",
            toastOf("pgCode == null"),
        )
        assertEquals(
            "네트워크 실패 분기가 `deleteToastIsNetwork` 판정을 안 쓰거나 문구가 다르다 — " +
                "화면에서 `if (code == 200)`으로 나누면 그 판정은 어느 층에서도 검증되지 " +
                "않는다($branches)",
            "NETWORK_ERROR",
            toastOf("RecordRules.deleteToastIsNetwork(pgCode)"),
        )
        assertEquals(
            "거절(`else`)에 거절 문구를 안 쓴다 — `잠시 후 다시 시도해 주세요`는 " +
                "다시 시도해도 같은 경우에 **틀린 안내**다: $branches",
            "COMMENT_DELETE_FAILED",
            toastOf("else"),
        )
        // 🔴 **성공 문구가 두 자리에 있으면 안 된다.** M8이 정확히 이 모양이었다.
        assertEquals(
            "`COMMENT_DELETED`가 실패 분기에도 있다 — 안 지워진 댓글에 `댓글을 지웠어요`가 " +
                "뜬다: $branches",
            1,
            branches.count { Regex("""CfToast\.COMMENT_DELETED\b""").containsMatchIn(it.second) },
        )

        // ② ViewModel: 성공이면 **다시 읽고 나서** 알린다.
        val vm = code("ui/place/RecordViewModel.kt")
        val impl = braceBlock(vm, vm.indexOf("{", vm.indexOf("fun deleteComment(")))
        val reload = impl.indexOf("loadAll(")
        val done = impl.indexOf("onDone(null)")
        assertTrue(
            "삭제 성공 뒤에 `loadAll(`이 없다 — 낙관적으로 지우지도 않고 다시 읽지도 " +
                "않으면 **지운 댓글이 화면에 그대로 남는다**(`댓글 3`도 안 줄어든다)",
            reload >= 0,
        )
        assertTrue(
            "`onDone(null)`(성공 알림)이 `loadAll(`보다 먼저다 — 토스트가 뜬 화면에 지운 " +
                "댓글이 남아 있는 프레임이 생긴다",
            done > reload,
        )
    }

    // ── 도우미 ──────────────────────────────────────────────────────

    /** 주석을 떼어 낸 `main` 소스. 내가 쓴 설명이 검사의 입력으로 세어지지 않게 한다. */
    private fun code(relative: String): String =
        File("src/main/java/com/catchflower/app/$relative").readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .lineSequence().map { it.substringBefore("//") }.joinToString("\n")

    /**
     * `openBrace` 위치의 `{`부터 짝이 맞는 `}`까지.
     *
     * ⚠️ 정규식으로 블록을 자르면 안 된다 — 안쪽에 `{`가 또 있다(`when` 안의 람다·
     *    문자열 템플릿). 짝을 세면 중첩이 깊어져도 맞는다.
     */
    private fun braceBlock(text: String, openBrace: Int): String {
        assertTrue("블록의 `{`를 못 찾았다 — 소스 모양이 바뀌면 이 검사를 고친다", openBrace >= 0)
        var depth = 0
        for (i in openBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(openBrace, i + 1)
            }
        }
        throw AssertionError("블록의 닫는 `}`를 못 찾았다(중괄호 짝이 안 맞는다)")
    }

    private fun tableBody(sql: String, table: String): String {
        val m = Regex(
            """create table if not exists public\.$table \((.*?)\n\);""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(sql)
        assertTrue("$table 정의를 못 찾았다", m != null)
        return m!!.groupValues[1]
    }

    /** 컬럼 이름만. `constraint`·`primary key` 줄은 컬럼이 아니다. */
    private fun columnsOf(body: String): Set<String> =
        body.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
            .map { it.substringBefore(' ') }
            .filter { it !in setOf("constraint", "primary", "unique", "check", "foreign") }
            .toSet()
}
