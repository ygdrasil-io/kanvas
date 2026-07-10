#!/usr/bin/env python3
"""Retag measured Skia GMs by registry index without touching sibling classes."""

import argparse
import difflib
import json
import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "reports/skia-gm-render-cost/2026-07-10-blocking-reclassification.json"
SERVICE = ROOT / "integration-tests/skia/src/test/resources/META-INF/services/org.graphiks.kanvas.skia.SkiaGm"
SOURCE_ROOT = ROOT / "integration-tests/skia/src/test/kotlin"


def matching_brace(text, opening):
    depth = 0
    for position in range(opening, len(text)):
        if text[position] == "{":
            depth += 1
        elif text[position] == "}":
            depth -= 1
            if depth == 0:
                return position
    raise ValueError("unclosed Kotlin class body")


def class_shape(text, match):
    """Return the declaration's body bounds, or its header end if bodyless."""
    parens = 0
    position = match.end()
    while position < len(text):
        char = text[position]
        if char == "(":
            parens += 1
        elif char == ")":
            parens -= 1
        elif char == "{" and parens == 0:
            return position, matching_brace(text, position), None
        elif char == "\n" and parens == 0:
            next_position = position + 1
            while next_position < len(text) and text[next_position] in " \t\r\n":
                next_position += 1
            if next_position < len(text) and text[next_position] == "{":
                return next_position, matching_brace(text, next_position), None
            return None, None, position
        position += 1
    raise ValueError("unterminated Kotlin class declaration")


def locate_class(files, fqcn, contents=None):
    package, name = fqcn.rsplit(".", 1)
    declaration = re.compile(r"\b(?:class|object)\s+" + re.escape(name) + r"\b")
    found = []
    for path in files:
        text = contents if contents is not None else path.read_text()
        if not re.search(r"^package\s+" + re.escape(package) + r"\s*$", text, re.MULTILINE):
            continue
        for match in declaration.finditer(text):
            opening, closing, header_end = class_shape(text, match)
            found.append((path, text, match, opening, closing, header_end))
    if len(found) != 1:
        raise ValueError(f"{fqcn}: expected exactly one Kotlin class, found {len(found)}")
    return found[0]


def replace_render_cost(text, class_info, tag):
    path, _, match, opening, closing, header_end = class_info
    if opening is not None:
        body = text[opening:closing]
        cost = re.search(r"override\s+val\s+renderCost(?:\s*:\s*RenderCost)?\s*=\s*RenderCost\.(\w+)", body)
        if cost:
            return text[:opening + cost.start(1)] + tag + text[opening + cost.end(1):]
        indent = re.match(r"[ \t]*", text[text.rfind("\n", 0, match.start()) + 1:match.start()]).group(0)
        insertion = "\n" + indent + "    override val renderCost = RenderCost." + tag
        return text[:opening + 1] + insertion + text[opening + 1:]

    indent = re.match(r"[ \t]*", text[text.rfind("\n", 0, match.start()) + 1:match.start()]).group(0)
    body = " {\n" + indent + "    override val renderCost = RenderCost." + tag + "\n" + indent + "}"
    return text[:header_end] + body + text[header_end:]


def report_and_services():
    rows = json.loads(REPORT.read_text())["rows"]
    expected = {row["registryIndex"]: row["tag"] for row in rows}
    services = [line.strip() for line in SERVICE.read_text().splitlines() if line.strip()]
    if len(rows) != 511 or len(expected) != 511:
        raise ValueError("report must contain exactly 511 unique registry rows")
    if max(expected) >= len(services):
        raise ValueError("report registry index exceeds the service registry")
    if Counter(expected.values()) != Counter({"FAST": 460, "MEDIUM": 14, "SLOW": 1, "BLOCKING": 36}):
        raise ValueError("report tag counts differ from the approved reclassification")
    return expected, services


def build_patch():
    expected, services = report_and_services()
    files = list(SOURCE_ROOT.rglob("*.kt"))
    before = {}
    after = {}
    operations = Counter()
    for index, tag in sorted(expected.items()):
        info = locate_class(files, services[index])
        path = info[0]
        before.setdefault(path, path.read_text())
        after.setdefault(path, before[path])
        after[path] = replace_render_cost(
            after[path],
            locate_class([path], services[index], after[path]),
            tag,
        )
        operations[tag] += 1
    if operations != Counter({"FAST": 460, "MEDIUM": 14, "SLOW": 1, "BLOCKING": 36}):
        raise ValueError(f"unexpected patch operations: {operations}")
    lines = []
    for path in sorted(after):
        if before[path] != after[path]:
            unified = list(difflib.unified_diff(
                before[path].splitlines(keepends=True),
                after[path].splitlines(keepends=True),
                fromfile=str(path.relative_to(ROOT)),
                tofile=str(path.relative_to(ROOT)),
            ))
            lines.append("*** Update File: " + str(path.relative_to(ROOT)) + "\n")
            lines.extend("@@\n" if line.startswith("@@") else line for line in unified[2:])
    return "*** Begin Patch\n" + "".join(lines) + "*** End Patch\n", operations, len(after)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--patch", action="store_true", help="write the verified unified source patch to stdout")
    args = parser.parse_args()
    patch, operations, files = build_patch()
    if args.patch:
        sys.stdout.write(patch)
        return
    print(json.dumps({"operations": dict(sorted(operations.items())), "files": files}, sort_keys=True))


if __name__ == "__main__":
    main()
