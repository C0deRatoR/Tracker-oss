#!/usr/bin/env python3
"""Move the one-arm side lateral sets onto the cable row they belong on.

Library version 8 merged "One-Arm Side Laterals" into "Lateral Raise (Dumbbell)". It is the
cable variant, which only the owner could say -- both are loaded in the same range, so nothing
in the data gives it away. Version 9 corrects the mapping, but a phone that already ran 8 has
the sets merged and the old row deleted, so the app cannot put them right on its own.

This repoints that one block. The two blocks are still distinguishable: a merge is the only
way one exercise appears twice in a session under different `position` values, and the block
that came from the one-arm row is the later one.

Read-only by default; pass --apply to write. Always takes a backup first.

Usage:
    python3 tools/repair_one_arm_laterals.py            # report what it would do
    python3 tools/repair_one_arm_laterals.py --apply
"""
import shutil
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

ADB = Path.home() / "Android/Sdk/platform-tools/adb"
PACKAGE = "com.yash.tracker.debug"
FROM_NAME = "Lateral Raise (Dumbbell)"
TO_NAME = "Lateral Raise (Cable)"
WORK = Path("/tmp/tracker-lateral-repair")


def adb(*args: str, check: bool = True) -> bytes:
    result = subprocess.run([str(ADB), *args], capture_output=True, timeout=120)
    if check and result.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {result.stderr.decode()}")
    return result.stdout


def pull(name: str, into: Path) -> None:
    data = adb("shell", f"run-as {PACKAGE} cat databases/{name}", check=False)
    if data:
        (into / name).write_bytes(data)


def rebuild_records(db: sqlite3.Connection, exercise_id: int) -> None:
    """The four bests, computed the way PersonalRecords.bestsIn does on the phone.

    Warmups and unticked rows do not count, and the three weighted records only look at sets
    that carry both a weight and reps.
    """
    sets = db.execute(
        "SELECT session_id, date, reps, weight_kg FROM workout_set s"
        " JOIN workout_session w ON w.id = s.session_id"
        " WHERE s.exercise_id = ? AND s.is_completed = 1 AND s.is_warmup = 0",
        (exercise_id,),
    ).fetchall()
    if not sets:
        return

    weighted = [r for r in sets if (r[3] or 0) > 0 and (r[2] or 0) > 0]
    reps = [r[2] for r in sets if (r[2] or 0) > 0]

    bests: list[tuple[str, float]] = []
    if weighted:
        bests.append(("MAX_WEIGHT", max(r[3] for r in weighted)))
        # Epley: w x (1 + reps / 30), and a single rep is already the max.
        bests.append((
            "EST_1RM",
            max(r[3] if r[2] <= 1 else r[3] * (1 + r[2] / 30.0) for r in weighted),
        ))
        bests.append(("MAX_VOLUME", max(r[3] * r[2] for r in weighted)))
    if reps:
        bests.append(("MAX_REPS", float(max(reps))))

    # Attributed to the session that actually reached each best.
    for kind, value in bests:
        row = next(
            r for r in sets
            if (kind == "MAX_WEIGHT" and r[3] == value)
            or (kind == "MAX_REPS" and r[2] == value)
            or (kind == "MAX_VOLUME" and (r[3] or 0) * (r[2] or 0) == value)
            or (kind == "EST_1RM" and r[3] and r[2] and
                (r[3] if r[2] <= 1 else r[3] * (1 + r[2] / 30.0)) == value)
        )
        db.execute(
            "INSERT INTO personal_record (exercise_id, type, value, date, session_id)"
            " VALUES (?, ?, ?, ?, ?)",
            (exercise_id, kind, value, row[1], row[0]),
        )


def main() -> int:
    apply = "--apply" in sys.argv

    if b"device" not in adb("devices"):
        raise SystemExit("no device attached")

    WORK.mkdir(parents=True, exist_ok=True)
    for stale in WORK.glob("tracker.db*"):
        stale.unlink()

    # The app must not be mid-write, and its WAL has to come along or the copy reads a state
    # from before the last few sessions.
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1)
    for name in ("tracker.db", "tracker.db-wal", "tracker.db-shm"):
        pull(name, WORK)

    db_path = WORK / "tracker.db"
    if not db_path.exists():
        raise SystemExit("could not read the database — is this a debuggable build?")

    backup = WORK / f"tracker-backup-{int(time.time())}.db"
    shutil.copy(db_path, backup)

    db = sqlite3.connect(db_path)
    source = db.execute("SELECT id FROM exercise WHERE name = ?", (FROM_NAME,)).fetchone()
    target = db.execute("SELECT id FROM exercise WHERE name = ?", (TO_NAME,)).fetchone()
    if not source or not target:
        raise SystemExit(f"expected both {FROM_NAME!r} and {TO_NAME!r} to exist")

    blocks = db.execute(
        "SELECT session_id, position, COUNT(*) FROM workout_set WHERE exercise_id = ?"
        " GROUP BY session_id, position ORDER BY session_id, position",
        (source[0],),
    ).fetchall()

    if len(blocks) < 2:
        print("nothing to repair: the exercise appears once per session")
        return 0

    session_id, position, count = blocks[-1]
    print(f"{FROM_NAME} -> {TO_NAME}")
    print(f"  session {session_id}, position {position}, {count} sets")
    for row in db.execute(
        "SELECT set_index, reps, weight_kg FROM workout_set"
        " WHERE session_id = ? AND position = ? ORDER BY set_index",
        (session_id, position),
    ):
        print(f"    set {row[0] + 1}: {row[1]} x {row[2]} kg")

    if not apply:
        print("\nread-only; pass --apply to write")
        return 0

    db.execute(
        "UPDATE workout_set SET exercise_id = ? WHERE session_id = ? AND position = ?",
        (target[0], session_id, position),
    )
    # Records carry no position, so the ones belonging to this block cannot be told apart from
    # the ones that stayed. Both exercises' records are dropped and rebuilt from the sets, which
    # is where they came from — leaving them deleted would blank the personal bests on two
    # exercises that plainly have some.
    for exercise_id in (source[0], target[0]):
        db.execute("DELETE FROM personal_record WHERE exercise_id = ?", (exercise_id,))
        rebuild_records(db, exercise_id)

    db.commit()
    db.close()

    # Push the checkpointed file back and drop the WAL, or the app replays the old one over it.
    adb("shell", f"run-as {PACKAGE} rm -f databases/tracker.db-wal databases/tracker.db-shm")
    # exec-in, not shell: `adb shell` runs the command under a pty and rewrites newlines, which
    # corrupts a database on the way in. exec-in passes stdin through untouched.
    with db_path.open("rb") as repaired:
        written = subprocess.run(
            [str(ADB), "exec-in", f"run-as {PACKAGE} sh -c 'cat > databases/tracker.db'"],
            stdin=repaired, capture_output=True, timeout=180,
        )
    if written.returncode != 0:
        raise SystemExit(f"writing the database back failed: {written.stderr.decode()}")
    print(f"\napplied. backup at {backup}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
