#!/usr/bin/env python3
"""`01_스키마_전체.sql`을 만든다. **결과 파일을 직접 고치지 않는다.**

원본은 `supabase/migrations/0001~0003`이다. 합본을 손으로 고치면 원본과 어긋나고,
어긋난 걸 알 방법이 없다 — 오너는 합본만 보고 붙여넣으니 **틀린 쪽이 적용된다.**

    python3 오너_실행/build_합본.py

⚠️ 마이그레이션을 새로 추가하면(`0004_*.sql`) **여기 목록에도 넣는다.**
   목록을 안 고치면 새 파일이 조용히 빠지고, 오너는 다 붙여넣었다고 생각한다.
   그래서 아래는 하드코딩이 아니라 **디렉터리를 훑어서 번호순으로 전부** 넣는다.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "supabase" / "migrations"
OUT = ROOT / "오너_실행" / "01_스키마_전체.sql"

HEADER = """-- ════════════════════════════════════════════════════════════════
--  캐치플라워 DB 스키마 — 이 파일 전체를 복사해 SQL Editor에 붙여넣고 Run
-- ════════════════════════════════════════════════════════════════
--
--  이 파일은 자동 합본이다. 원본은 supabase/migrations/ 이고
--  고칠 일이 있으면 원본을 고친 뒤 `python3 오너_실행/build_합본.py`로 다시 만든다.
--  **이 파일을 직접 고치면 원본과 어긋나고, 어긋난 걸 알 방법이 없다.**
--
--  ✅ 두 번 돌려도 안전하다 (if not exists · drop policy if exists ·
--     create or replace · on conflict do update 로 작성했다).
--  ✅ 중간에 실패해도 이미 만들어진 것은 남는다 — 고친 뒤 그대로 다시 Run 하면 된다.
--
--  ⚠️ 다 돌린 뒤 `오너_실행/02_적용확인.sql` 을 붙여넣어 실제로 들어갔는지 확인한다.
--     "Success. No rows returned" 는 **성공했다는 뜻이 아니다** — 아무 것도 안 만들고
--     끝났을 때도 같은 메시지가 나온다.
--
"""


def main() -> None:
    # 번호순. `0010`이 `0002`보다 앞에 오면 순서가 깨지므로 이름 정렬로 충분한지
    # 확인한다 — 4자리 zero-pad 규칙이라 문자열 정렬이 곧 번호 정렬이다.
    files = sorted(SRC.glob("[0-9][0-9][0-9][0-9]_*.sql"))
    if not files:
        raise SystemExit(f"마이그레이션이 없다: {SRC}")

    parts = [HEADER]
    for f in files:
        parts.append(
            "\n\n-- ══════════════════════════════════════════════════════════════\n"
            f"-- ▼ {f.name}\n"
            "-- ══════════════════════════════════════════════════════════════\n\n"
            + f.read_text(encoding="utf-8")
        )

    OUT.write_text("".join(parts), encoding="utf-8")
    lines = OUT.read_text(encoding="utf-8").count("\n")
    print(f"{OUT.relative_to(ROOT)} 생성: {len(files)}개 파일 · {lines}줄")
    for f in files:
        print(f"  - {f.name}")


if __name__ == "__main__":
    main()
