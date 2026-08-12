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
        // 코드도 `deleted_at`만 보낸다. body를 실으면 위 정책이 못 막는다.
        val patch = Regex("""JSONObject\(\)\.put\("deleted_at",[^\n]*""").find(source)
        assertTrue("deleted_at PATCH 본문을 못 찾았다", patch != null)
        assertTrue(
            "삭제 PATCH에 body가 실려 있다 — C-9 수정 불가가 우회된다",
            !patch!!.value.contains("\"body\""),
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
     * ⚠️ **`migrations/`에 0009를 만들지 않았다.** 계약 6절이 "DB 스키마·마이그레이션은
     *    한쪽만 만든다 · 고칠 필요가 생기면 진행.md에 먼저 쓰고 알린다"이고, 이 세션은
     *    오너 승인 전이다. 그래서 제안은 `프로젝트 맥락/제안/`에 두고 **합본 빌더가
     *    집어가지 못하게** 했다(`build_합본.py`는 `migrations/`만 훑는다).
     */
    @Test
    fun 댓글_삭제는_서버_고침_전까지_막혀_있다() {
        // ① 원인과 제안이 저장소에 남아 있다.
        val proposal = File(projectRoot, "프로젝트 맥락/제안/0009_제안_댓글삭제_RPC.sql")
        assertTrue(
            "댓글 삭제 원인·고침 제안 파일이 없어졌다 — 없으면 다음 세션이 0008을 다시 " +
                "적용하며 원인을 추측한다(이미 한 번 그렇게 틀렸다): ${proposal.path}",
            proposal.isFile,
        )
        val sql = proposal.readText()
        assertTrue(
            "제안 파일에 `delete_comment` RPC가 없다 — 다른 파일로 바뀌었으면 이 검사를 고친다",
            sql.contains("function public.delete_comment(c_id uuid)"),
        )
        assertTrue(
            "제안 파일이 `security definer`가 아니다 — invoker면 같은 42501에 다시 걸린다",
            sql.contains("security definer"),
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

        // ② 아직 마이그레이션이 아니다 — 오너 승인 전에 서버로 갈 수 없다.
        val migrations = File(projectRoot, "supabase/migrations")
        val leaked = migrations.listFiles()?.filter { it.name.startsWith("0009") }.orEmpty()
        assertEquals(
            "0009가 `migrations/`에 들어갔다 — 합본 빌더가 집어가면 오너 승인 없이 서버에 " +
                "적용된다. 계약 6절은 스키마를 **한쪽만** 만들고 진행.md에 먼저 쓰라고 한다. " +
                "오너가 승인했다면 이 검사를 고치는 것이 그 기록이다: " + leaked.map { it.name },
            emptyList<String>(),
            leaked.map { it.name },
        )

        // ③ 화면이 삭제를 부르지 않는다 — 지금 붙이면 무조건 실패하는 버튼이 된다.
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
        assertEquals(
            "화면이 `deleteComment`를 부른다 — 서버 고침(제안 0009)이 적용되기 전에는 " +
                "**누르면 무조건 실패하는 버튼**이다. 적용됐다면 실기기로 재고 이 검사를 " +
                "뒤집는다(`ReactionLiveTest.댓글을_달고_읽고_지운다`가 초록이 된다): $callers",
            emptyList<String>(),
            callers,
        )
    }

    // ── 도우미 ──────────────────────────────────────────────────────

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
