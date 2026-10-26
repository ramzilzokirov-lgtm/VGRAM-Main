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
GLASS_IMPORT = "import org.telegram.ui.VgramModules.UI.VgramGlassStyle;"
GLASS_CALL = "VgramGlassStyle.drawDrawableWithGlass(canvas, currentBackgroundDrawable, getContext());"
GLASS_DRAW = re.compile(r"(?im)^(?P<indent>[ \t]*)currentBackgroundDrawable\.draw\(canvas\)\s*;")
NAME_LAYOUT_DRAW = re.compile(
    r"(?im)^(?P<indent>[ \t]*)(?P<layout>\w*(?:name|author|title)\w*Layout)"
    r"\.draw\(canvas\)\s*;"
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
        if GLASS_CALL not in source:
            match_glass = GLASS_DRAW.search(source)
            if match_glass:
                if GLASS_IMPORT not in source:
                    messenger_import = re.search(r"(?m)^import org\.telegram\.messenger\.[^;]+;\s*$", source)
                    if messenger_import:
                        insert_at = messenger_import.end()
                        source = source[:insert_at] + "\n" + GLASS_IMPORT + source[insert_at:]
                        match_glass = GLASS_DRAW.search(source)
                if GLASS_IMPORT in source and match_glass:
                    source = source[:match_glass.start()] + match_glass.group("indent") + GLASS_CALL + source[match_glass.end():]
                    SOURCE.write_text(source, encoding="utf-8")
                    print(f"Enabled liquid-glass bubble rendering in {SOURCE}")
        if IMPORT not in source:
            anchor = "import org.telegram.messenger.MessageObject;"
            if anchor not in source:
                fail("call already exists but no safe messenger import anchor was found", source)
            source = source.replace(anchor, anchor + "\n" + IMPORT, 1)
            SOURCE.write_text(source, encoding="utf-8")
            print(f"Repaired missing import in already-patched {SOURCE}")
        else:
            print(f"Already patched: {SOURCE}")
        return

    matches = list(NAME_LAYOUT_DRAW.finditer(source))
    if not matches:
        lines = source.splitlines()
        candidates = [i for i, line in enumerate(lines)
                      if re.search(r"(name|author|title).*\.(?:draw|drawText)\s*\(", line, re.I)]
        if candidates:
            fail("no supported sender-name layout draw signature found", source, candidates[0], candidates[-1])
        fail("no sender-name/title layout draw signature found; upstream source changed", source)

    # The pinned cell can draw title layouts in multiple visual states. The last
    # matching layout draw is the final main-cell title rendering path.
    match = matches[-1]
    line_number = source.count("\n", 0, match.start())
    if "currentMessageObject" not in source:
        fail("currentMessageObject field/reference not found; refusing to inject an undefined symbol",
             source, line_number, line_number)

    if IMPORT not in source:
        messenger_import = re.search(r"(?m)^import org\.telegram\.messenger\.[^;]+;\s*$", source)
        if messenger_import:
            insert_at = messenger_import.end()
            source = source[:insert_at] + "\n" + IMPORT + source[insert_at:]
        else:
            package_match = re.search(r"(?m)^package [^;]+;\s*$", source)
            if not package_match:
                fail("package declaration not found; cannot safely add import", source)
            source = source[:package_match.end()] + "\n\n" + IMPORT + source[package_match.end():]
        match = list(NAME_LAYOUT_DRAW.finditer(source))[-1]

    source = source[:match.end()] + "\n" + match.group("indent") + CALL + source[match.end():]
    glass_match = GLASS_DRAW.search(source)
    if glass_match:
        if GLASS_IMPORT not in source:
            source = source[:glass_match.start()] + GLASS_IMPORT + "\n" + source[glass_match.start():]
            glass_match = GLASS_DRAW.search(source)
        if glass_match and GLASS_CALL not in source:
            source = source[:glass_match.start()] + glass_match.group("indent") + GLASS_CALL + source[glass_match.end():]
    if source.count(CALL) != 1 or source.count(IMPORT) != 1:
        fail("post-patch idempotency check failed", source, line_number, line_number)

    badge_file = Path("TMessagesProj/src/main/java/org/telegram/messenger/VgramBadgeSync.java")
    if badge_file.is_file() and "ChatMessageCell cell, MessageObject messageObject" not in badge_file.read_text(encoding="utf-8"):
        fail("VgramBadgeSync ChatMessageCell overload is missing; refusing to inject a non-compiling call")

    SOURCE.write_text(source, encoding="utf-8")
    print(f"Patched {SOURCE} at sender-name layout draw line {line_number + 1}")

if __name__ == "__main__":
    main()
