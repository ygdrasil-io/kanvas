#!/usr/bin/env python3
"""
extract_pathops_fixtures.py — D1.4 PathOps regression harvest.

Scrapes upstream Skia's `tests/PathOpsOpTest.cpp` (or any file with the
same fixture pattern) and emits a JSON dump that the
`PathOpsRegressionRunner` Kotlin harness consumes.

Each fixture is a function of the form :

    static void <NAME>(skiatest::Reporter* reporter, const char* filename) {
        SkPathBuilder path, pathB;
        path.setFillType(SkPathFillType::kWinding);
        path.moveTo(...);
        path.cubicTo(...);
        path.close();
        pathB.setFillType(SkPathFillType::kEvenOdd);
        pathB.moveTo(...);
        pathB.lineTo(...);
        pathB.close();
        testPathOp(reporter, path.detach(), pathB.detach(),
                   kDifference_SkPathOp, filename);
    }

We extract :
  - name              : the function name (= test fixture id)
  - fillTypeA / B     : "kWinding" | "kEvenOdd" | "kInverseWinding" |
                        "kInverseEvenOdd" (default: kWinding)
  - pathA / pathB     : ordered list of [verb, arg0, arg1, ...] tuples.
                        Verbs : moveTo / lineTo / quadTo / conicTo /
                                cubicTo / close.
  - op                : "kDifference" | "kIntersect" | "kUnion" |
                        "kXOR" | "kReverseDifference"

Fixtures with non-trivial control flow (for / while / if / helper-fn
calls beyond what we recognise) are reported as "unparseable" and
skipped — they can be ported manually later.

Usage :
    python3 extract_pathops_fixtures.py \
        /path/to/tests/PathOpsOpTest.cpp \
        > pathops_op_fixtures.json
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


# ─── Regexes ───────────────────────────────────────────────────────────

# `static void <name>(skiatest::Reporter* reporter, const char* filename) {`
FN_RE = re.compile(
    r"^static\s+void\s+(\w+)\s*\(\s*skiatest::Reporter\s*\*\s*\w+\s*,\s*"
    r"const\s+char\s*\*\s*\w+\s*\)\s*\{",
)

# `SkPathBuilder path, pathB;` or `SkPathBuilder path;` etc.
DECL_RE = re.compile(r"^\s*SkPathBuilder\s+([\w\s,]+);\s*$")

# `path.setFillType(SkPathFillType::kWinding);`
SET_FILL_RE = re.compile(
    r"^\s*(\w+)\.setFillType\s*\(\s*SkPathFillType::(\w+)\s*\)\s*;\s*$",
)

# `path.setFillType((SkPathFillType) 1);` — older fixtures cast int → enum.
SET_FILL_INT_RE = re.compile(
    r"^\s*(\w+)\.setFillType\s*\(\s*\(\s*SkPathFillType\s*\)\s*([0-3])\s*\)\s*;\s*$",
)
SET_FILL_INT_MAP = {
    "0": "kWinding",
    "1": "kEvenOdd",
    "2": "kInverseWinding",
    "3": "kInverseEvenOdd",
}

# `path.moveTo(0,1);`, `path.cubicTo(0,2, 1,0, 1,0);`, etc.
# Coords may be unary-negated, integer or float, with or without `f` suffix.
# We parse the arg list with a separate pass via [parse_numbers] to keep
# this regex free of nested quantifiers (which catastrophically backtrack
# on long lines — Python's `re` engine is not linear-time).
NUMBER = r"-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?f?"
VERB_RE = re.compile(r"^\s*(\w+)\.(\w+)\s*\((.*)\)\s*;\s*$")

# `testPathOp(reporter, path.detach(), pathB.detach(), kDifference_SkPathOp, filename);`
# Some variants: testPathOpFuzz, testSimplify (different harness — skip).
TEST_PATH_OP_RE = re.compile(
    r"^\s*testPathOp\s*\(\s*\w+\s*,\s*(\w+)\.detach\(\)\s*,\s*"
    r"(\w+)\.detach\(\)\s*,\s*(k\w+_SkPathOp)\s*,\s*\w+\s*\)\s*;\s*$",
)

# `testPathOp(reporter, one, two, kIntersect_SkPathOp, filename);` — bare
# variable form, used by fixtures that declare paths via `SkPath one =
# SkPath::Rect(...)` static factories instead of builders.
TEST_PATH_OP_BARE_RE = re.compile(
    r"^\s*testPathOp\s*\(\s*\w+\s*,\s*(\w+)\s*,\s*(\w+)\s*,\s*"
    r"(k\w+_SkPathOp)\s*,\s*\w+\s*\)\s*;\s*$",
)

# `SkPath one = SkPath::Rect({0, 0, 6, 6}, SkPathDirection::kCW),`
# `       two = SkPath::Rect({3, 3, 9, 9}, SkPathDirection::kCW);`
# Each line declares one path via a static factory. The trailing comma
# (multi-line `SkPath x = …, y = …;`) is normalised before matching.
RECT_FACTORY_RE = re.compile(
    r"^\s*(?:SkPath\s+)?(\w+)\s*=\s*SkPath::Rect\s*\(\s*\{\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*\}\s*,\s*"
    r"SkPathDirection::(\w+)\s*\)\s*[,;]\s*$",
)

# Verbs we know how to translate to Kotlin SkPathBuilder calls.
KNOWN_VERBS = {
    "moveTo": 2,
    "lineTo": 2,
    "quadTo": 4,
    "conicTo": 5,
    "cubicTo": 6,
    "close": 0,
    # `path.reset()` — clear the path. We model it as a special "reset"
    # pseudo-verb : the harness flushes verbs accumulated so far and
    # starts a fresh contour stream. Re-using the existing verb pipeline
    # keeps the JSON format uniform.
    "reset": 0,
}

# Op enum mapping (C++ token -> Kotlin token).
OP_MAP = {
    "kDifference_SkPathOp": "kDifference",
    "kIntersect_SkPathOp": "kIntersect",
    "kUnion_SkPathOp": "kUnion",
    "kXOR_SkPathOp": "kXOR",
    "kXor_SkPathOp": "kXOR",
    "kReverseDifference_SkPathOp": "kReverseDifference",
}

# Fill type enum mapping.
FILL_MAP = {
    "kWinding": "kWinding",
    "kEvenOdd": "kEvenOdd",
    "kInverseWinding": "kInverseWinding",
    "kInverseEvenOdd": "kInverseEvenOdd",
}


@dataclass
class Fixture:
    name: str
    op: str
    paths: dict[str, dict[str, Any]] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any] | None:
        # We need exactly two paths and a known op.
        if self.op not in OP_MAP.values():
            return None
        if "_a" not in self.paths or "_b" not in self.paths:
            return None
        return {
            "name": self.name,
            "fillTypeA": self.paths["_a"]["fill"],
            "fillTypeB": self.paths["_b"]["fill"],
            "pathA": self.paths["_a"]["verbs"],
            "pathB": self.paths["_b"]["verbs"],
            "op": self.op,
        }


def parse_numbers(s: str) -> list[float]:
    """Parse a comma-separated number list. `f` suffix tolerated."""
    out: list[float] = []
    for tok in re.findall(NUMBER, s):
        try:
            out.append(float(tok.rstrip("f")))
        except ValueError:
            pass
    return out


def parse_fixture(name: str, body: list[str]) -> tuple[Fixture | None, str | None]:
    """Parse one fixture body. Returns (fixture, error_msg)."""
    fix = Fixture(name=name, op="")
    # Map of variable-name -> internal slot. The first path declared is
    # tagged "_a", the second "_b" (in declaration / first-use order),
    # so `path` and `pathB` (or `path1` and `path2` etc.) are stable.
    var_to_slot: dict[str, str] = {}

    def slot_for(var: str) -> str | None:
        if var not in var_to_slot:
            if len(var_to_slot) == 0:
                var_to_slot[var] = "_a"
            elif len(var_to_slot) == 1:
                var_to_slot[var] = "_b"
            else:
                # Fixture uses a 3rd path — too exotic, skip.
                return None
            fix.paths[var_to_slot[var]] = {"fill": "kWinding", "verbs": []}
        return var_to_slot[var]

    for line in body:
        line = line.strip()
        if not line or line.startswith("//") or line.startswith("/*"):
            continue
        if line.startswith("if ") or line.startswith("for ") or line.startswith("while ") or line.startswith("switch "):
            return None, f"unparseable control flow at line: {line!r}"
        # Variable declarations.
        m = DECL_RE.match(line)
        if m:
            for var in (v.strip() for v in m.group(1).split(",")):
                if var:
                    slot_for(var)
            continue
        # setFillType — enum form.
        m = SET_FILL_RE.match(line)
        if m:
            var, fill = m.group(1), m.group(2)
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            if fill not in FILL_MAP:
                return None, f"unknown fillType: {fill!r}"
            fix.paths[slot]["fill"] = FILL_MAP[fill]
            continue
        # setFillType — int-cast form `(SkPathFillType) 0..3`.
        m = SET_FILL_INT_RE.match(line)
        if m:
            var, n = m.group(1), m.group(2)
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            fix.paths[slot]["fill"] = SET_FILL_INT_MAP[n]
            continue
        # testPathOp call (`.detach()` form for SkPathBuilder fixtures).
        m = TEST_PATH_OP_RE.match(line)
        if m:
            varA, varB, op = m.group(1), m.group(2), m.group(3)
            slot_a = var_to_slot.get(varA)
            slot_b = var_to_slot.get(varB)
            if slot_a != "_a" or slot_b != "_b":
                return None, f"path order mismatch at testPathOp: {line!r}"
            if op not in OP_MAP:
                return None, f"unknown op: {op!r}"
            fix.op = OP_MAP[op]
            return fix, None
        # testPathOp call (bare-variable form for SkPath::Rect fixtures).
        m = TEST_PATH_OP_BARE_RE.match(line)
        if m:
            varA, varB, op = m.group(1), m.group(2), m.group(3)
            slot_a = var_to_slot.get(varA)
            slot_b = var_to_slot.get(varB)
            if slot_a != "_a" or slot_b != "_b":
                return None, f"path order mismatch at testPathOp: {line!r}"
            if op not in OP_MAP:
                return None, f"unknown op: {op!r}"
            fix.op = OP_MAP[op]
            return fix, None
        # `SkPath one = SkPath::Rect({l, t, r, b}, dir),` — static factory.
        # Expand to the verb sequence the rect produces.
        m = RECT_FACTORY_RE.match(line)
        if m:
            var = m.group(1)
            l, t, r, b = (float(m.group(i).rstrip("f")) for i in (2, 3, 4, 5))
            direction = m.group(6)  # kCW | kCCW
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            # SkPath::Rect emits : moveTo(l,t), lineTo(r,t), lineTo(r,b),
            # lineTo(l,b), close — for kCW. kCCW reverses the corner order.
            if direction == "kCW":
                fix.paths[slot]["verbs"].extend([
                    ["moveTo", l, t],
                    ["lineTo", r, t],
                    ["lineTo", r, b],
                    ["lineTo", l, b],
                    ["close"],
                ])
            elif direction == "kCCW":
                fix.paths[slot]["verbs"].extend([
                    ["moveTo", l, t],
                    ["lineTo", l, b],
                    ["lineTo", r, b],
                    ["lineTo", r, t],
                    ["close"],
                ])
            else:
                return None, f"unknown SkPathDirection: {direction!r}"
            continue
        # Verb call.
        m = VERB_RE.match(line)
        if m:
            var, verb, args = m.group(1), m.group(2), m.group(3)
            if verb not in KNOWN_VERBS:
                # Unknown helper (e.g. addRect, addOval, transform, etc.)
                return None, f"unknown verb {verb!r} at: {line!r}"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            nums = parse_numbers(args)
            if len(nums) != KNOWN_VERBS[verb]:
                return None, f"verb {verb!r} got {len(nums)} args, expected {KNOWN_VERBS[verb]}: {line!r}"
            fix.paths[slot]["verbs"].append([verb, *nums])
            continue
        # Anything else : function call (testSimplify / helper / etc.) —
        # too exotic, skip.
        if "testSimplify" in line or "testPathOpFuzz" in line or "testPathOpFail" in line:
            return None, f"non-Op test harness: {line!r}"
        if line == "}" or line.startswith("SkPath ") or line.startswith("//"):
            continue
        return None, f"unparseable line: {line!r}"

    return None, "fixture body ended without testPathOp call"


def extract(src: Path) -> dict[str, Any]:
    """Walk source file, extract fixtures."""
    text = src.read_text(encoding="utf-8")
    lines = text.splitlines()

    fixtures: list[dict[str, Any]] = []
    skipped: list[dict[str, str]] = []

    i = 0
    n = len(lines)
    while i < n:
        m = FN_RE.match(lines[i])
        if not m:
            i += 1
            continue
        name = m.group(1)
        # Find matching closing brace.
        depth = 1
        body_start = i + 1
        j = body_start
        while j < n and depth > 0:
            depth += lines[j].count("{")
            depth -= lines[j].count("}")
            j += 1
        body = lines[body_start : j - 1]
        fix, err = parse_fixture(name, body)
        if fix:
            j_obj = fix.to_json()
            if j_obj is not None:
                fixtures.append(j_obj)
            else:
                skipped.append({"name": name, "reason": "incomplete fixture (missing pathA/pathB/op)"})
        else:
            skipped.append({"name": name, "reason": err or "unknown"})
        i = j

    return {"fixtures": fixtures, "skipped": skipped}


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <PathOpsOpTest.cpp>", file=sys.stderr)
        return 2
    src = Path(sys.argv[1])
    if not src.is_file():
        print(f"error: not a file: {src}", file=sys.stderr)
        return 2
    result = extract(src)
    print(
        f"Extracted {len(result['fixtures'])} fixtures, "
        f"skipped {len(result['skipped'])}",
        file=sys.stderr,
    )
    json.dump(result, sys.stdout, indent=2)
    print(file=sys.stdout)
    return 0


if __name__ == "__main__":
    sys.exit(main())
