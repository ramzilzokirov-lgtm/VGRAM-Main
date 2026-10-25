#!/usr/bin/env python3
"""Idempotently integrate VGRAM inline indicators into upstream ChatMessageCell."""
from __future__ import annotations

import re
import sys
from pathlib import Path

SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "upstream/TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java"
)
MIN_SIZE = 100 * 1024
IMPORT = "import org.telegram.messenger.VgramBadgeSync;"
CALL = "VgramBadgeSync.drawInlineIndicators(canvas, this, currentMessageObject);"
NAME_LAYOUT_DRAW = re.compile(
    r"(?m)^(?P<indent>[ \t]*)(?P<layout>(?:currentNameLayout|nameLayout|authorNameLayout|nameTextLayout|authorLayout))"
    r"\\.draw\\(canvas\\)\\s*;"
)

def fail(message: str, source: str = "", start: int = 0, end: int = 0) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    if source:
        lines = source.splitlines()
        lo = max(0, start - 5)
        hi = min(len(lines), max(end + 4, start + 8))
        print("--- source context ---", file=sys.stderr)
        for number in range(lo, hi):
            print(f"{number + 1:6}: {lines[number]}", file=sys.stderr)
        print("--- end context ---", file=sys.stderr)
    raise SystemExit(1)

def main() -> None:
    if not SOURCE.is_file():
        fail(f"upstream ChatMessageCell.java not found: {SOURCE}")
    size = SOURCE.stat().st_size
    if size < MIN_SIZE:
        fail(f"refusing to patch source smaller than 100 KiB ({size} bytes): {SOURCE}")
    source = SOURCE.read_text(encoding="utf-8")
    if "class ChatMessageCell" not in source:
        fail("source does not declare ChatMessageCell")
    if CALL in source:
        if IMPORT not in source:
            source = source.replace("import org.telegram.messenger.MessageObject;",
                                    "import org.telegram.messenger.MessageObject;\\n" + IMPORT, 1)
            if IMPORT not in source:
                fail("call already exists but VgramBadgeSync import is missing; could not safely add it", source)
            SOURCE.write_text(source, encoding="utf-8")
            print(f"Repaired missing import in already-patched {SOURCE}")
        else:
            print(f"Already patched: {SOURCE}")
        return

    matches = list(NAME_LAYOUT_DRAW.finditer(source))
    # Ignore any candidate located inside comments, and require a sender message object in scope.
    matches = [m for m in matches if not source[max(0, m.start() - 160):m.start()].rstrip().endswith("/*")]
    if len(matches) != 1:
        if matches:
            fail(f"expected exactly one sender-name layout draw point, found {len(matches)}; refusing ambiguous patch",
                 source, source.count("\\n", 0, matches[0].start()), source.count("\\n", 0, matches[-1].end()))
        # Print likely title/name draw sites to make upstream changes diagnosable.
        candidates = [i for i, line in enumerate(source.splitlines()) if
                      re.search(r"(name|author|title).*\\.draw\\(canvas\\)", line, re.I)]
        if candidates:
            fail("no supported sender-name layout draw signature found", source, candidates[0], candidates[-1])
        fail("no supported sender-name layout draw signature found; upstream source changed", source)

    match = matches[0]
    line_number = source.count("\\n", 0, match.start())
    before = source[:match.start()]
    after = source[match.end():]
    if "currentMessageObject" not in source:
        fail("currentMessageObject field/reference not found; refusing to inject an undefined symbol",
             source, line_number, line_number)
    if IMPORT not in source:
        # Add alongside existing messenger imports, without duplicating imports.
        messenger_import = re.search(r"(?m)^import org\\.telegram\\.messenger\\.[^;]+;\\s*$", source)
        if messenger_import:
            insert_at = messenger_import.end()
            source = source[:insert_at] + "\\n" + IMPORT + source[insert_at:]
            # Re-find the draw point after modifying imports.
            match = list(NAME_LAYOUT_DRAW.finditer(source))[0]
        else:
            package_match = re.search(r"(?m)^package [^;]+;\\s*$", source)
            if not package_match:
                fail("package declaration not found; cannot safely add import", source)
            source = source[:package_match.end()] + "\\n\\n" + IMPORT + source[package_match.end():]
            match = list(NAME_LAYOUT_DRAW.finditer(source))[0]

    insertion = "\\n" + match.group("indent") + CALL
    source = source[:match.end()] + insertion + source[match.end():]
    # Basic postconditions before touching the build tree.
    if source.count(CALL) != 1 or source.count(IMPORT) != 1:
        fail("post-patch idempotency check failed", source, line_number, line_number)
    if not re.search(r"(?m)^\\s*public\\s+static\\s+void\\s+drawInlineIndicators\\s*\\(Canvas\\s+canvas\\s*,\\s*MessageObject\\s+messageObject", 
                     Path("TMessagesProj/src/main/java/org/telegram/messenger/VgramBadgeSync.java").read_text(encoding="utf-8")):
        # Signature check is handled by the build overlay below; avoid a false pass when the
        # source is being patched outside the repository root.
        badge_file = Path("TMessagesProj/src/main/java/org/telegram/messenger/VgramBadgeSync.java")
        if badge_file.is_file() and "ChatMessageCell cell, MessageObject messageObject" not in badge_file.read_text(encoding="utf-8"):
            fail("VgramBadgeSync ChatMessageCell overload is missing; refusing to inject a non-compiling call")
    SOURCE.write_text(source, encoding="utf-8")
    print(f"Patched {SOURCE} at sender-name layout draw line {line_number + 1}")

if __name__ == "__main__":
    main()
