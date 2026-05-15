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

# `testPathOpCheck(reporter, path.detach(), pathB.detach(),
#                 kUnion_SkPathOp, filename, true);` — variant that
# takes an extra bool. Maps to the same Op call ; we just ignore the
# trailing flag (it controls upstream's empty-handling, not the
# operation result).
TEST_PATH_OP_CHECK_RE = re.compile(
    r"^\s*testPathOpCheck\s*\(\s*\w+\s*,\s*(\w+)\.detach\(\)\s*,\s*"
    r"(\w+)\.detach\(\)\s*,\s*(k\w+_SkPathOp)\s*,\s*\w+\s*,\s*"
    r"(?:true|false)\s*\)\s*;\s*$",
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

# `path.addRect(left, top, right, bottom);` — 4-scalar variant.
# Mirrors `SkPath::addRect(SkScalar, SkScalar, SkScalar, SkScalar)`.
# The optional 5th `SkPathDirection` argument is matched separately.
ADD_RECT_4_RE = re.compile(
    r"^\s*(\w+)\.addRect\s*\(\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*"
    r"(?:,\s*SkPathDirection::(\w+)\s*)?"
    r"\)\s*;\s*$",
)

# `path.addRect({l, t, r, b}, SkPathDirection::kCW);` — brace-init form
# (C++ aggregate initialiser for `SkRect`). Common in newer fixtures.
ADD_RECT_BRACE_RE = re.compile(
    r"^\s*(\w+)\.addRect\s*\(\s*\{\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*\}\s*"
    r"(?:,\s*SkPathDirection::(\w+)\s*)?"
    r"\)\s*;\s*$",
)

# `path.addRect(SkRect::MakeLTRB(l, t, r, b));` — wrapped variant.
ADD_RECT_LTRB_RE = re.compile(
    r"^\s*(\w+)\.addRect\s*\(\s*SkRect::MakeLTRB\s*\(\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*\)\s*"
    r"(?:,\s*SkPathDirection::(\w+)\s*)?"
    r"\)\s*;\s*$",
)

# `path.addRect(SkRect::MakeXYWH(x, y, w, h));` — XYWH variant.
ADD_RECT_XYWH_RE = re.compile(
    r"^\s*(\w+)\.addRect\s*\(\s*SkRect::MakeXYWH\s*\(\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*\)\s*"
    r"(?:,\s*SkPathDirection::(\w+)\s*)?"
    r"\)\s*;\s*$",
)

# `path.addCircle(cx, cy, radius);` — optional direction arg.
# Skia's circle is 4 cubic Bézier arcs ; we approximate using the
# canonical kappa = 0.5522847498 unit-circle control offsets.
ADD_CIRCLE_RE = re.compile(
    r"^\s*(\w+)\.addCircle\s*\(\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*,\s*"
    r"(" + NUMBER + r")\s*"
    r"(?:,\s*SkPathDirection::(\w+)\s*)?"
    r"\)\s*;\s*$",
)

# Magic constant for cubic-Bézier approximation of a circular arc.
# `kappa = 4 * (sqrt(2) - 1) / 3 ≈ 0.5522847498` — the offset, in
# units of the radius, from the on-axis points to the cubic control
# points such that the resulting Bézier matches a true quarter-circle
# to within ~0.00027 max radial error.
KAPPA = 0.5522847498307933

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


# `SkBits2Float(0x43b40000)` — upstream's exact-bit-pattern encoding.
# Many fixtures use this instead of decimal literals to round-trip the
# bytes Skia emitted into source ; we evaluate the hex back to a float
# in a preprocessing pass so the per-line regex sees a normal number.
SK_BITS2FLOAT_RE = re.compile(r"SkBits2Float\s*\(\s*0[xX]([0-9A-Fa-f]+)\s*\)")

# `SkScalar xA = 0.65f;` — named scalar constant declaration. We
# collect these into a substitution dict in a pre-pass, then replace
# token references in subsequent geometry calls so the per-line parser
# only sees raw numbers. Only matches simple numeric-literal RHS — any
# arithmetic / function call / cast on the RHS leaves the declaration
# unparsed (it's safer to skip the fixture than to evaluate C++
# expressions). Trailing `f` suffix is tolerated.
SK_SCALAR_DECL_RE = re.compile(
    r"^\s*(?:static\s+)?(?:const\s+)?SkScalar\s+(\w+)\s*=\s*"
    r"(" + NUMBER + r")\s*;\s*$",
)

# `SkPoint pts[] = { {x,y}, {x,y}, ... };` — array literal of
# 2-component points. We collect the (x, y) pairs into a substitution
# table indexed by array name + integer index. References look like
# `pts[0].fX`, `pts[0].fY`, or bare `pts[0]` (which expands to a comma-
# separated `x, y` pair so e.g. `moveTo(pts[0])` becomes
# `moveTo(x, y)`). Only matches single-line declarations after the
# multi-line statement joiner has collapsed continuation lines.
SK_POINT_ARRAY_DECL_RE = re.compile(
    r"^\s*SkPoint\s+(\w+)\s*\[\s*\]\s*=\s*\{(.*?)\}\s*;\s*$",
)
# Match each `{x, y}` element inside the array initialiser body.
SK_POINT_ELEM_RE = re.compile(
    r"\{\s*(" + NUMBER + r")\s*,\s*(" + NUMBER + r")\s*\}",
)


def apply_named_scalar_subst(line: str, scalars: dict[str, str]) -> str:
    """
    Replace each whole-word occurrence of a scalar name in [scalars]
    with its literal value. Word-boundary anchors avoid clobbering
    identifiers that *contain* a scalar name as a substring
    (e.g. `xAxis` shouldn't match `xA`). The substitution dict is
    populated by [collect_named_scalars] in a top-of-fixture pre-pass.
    """
    if not scalars:
        return line
    # Build a single alternation, longest-name-first, so `xAB` matches
    # before `xA` if both are defined.
    keys = sorted(scalars.keys(), key=len, reverse=True)
    pattern = re.compile(r"\b(?:" + "|".join(re.escape(k) for k in keys) + r")\b")
    return pattern.sub(lambda m: scalars[m.group(0)], line)


def apply_point_array_subst(
    line: str,
    arrays: dict[str, list[tuple[str, str]]],
) -> str:
    """
    Replace `<name>[<idx>].fX` / `.fY` / bare `<name>[<idx>]` refs with
    their stored coordinate literals. Bare-element form expands to a
    comma-separated `x, y` pair so the surrounding call site (e.g.
    `path.moveTo(pts[0])`) parses as a normal 2-arg moveTo. Out-of-
    bounds indices are left untouched — the per-line parser will
    reject the fixture as unparseable, which is the correct behaviour
    (skip rather than silently fabricate a value).
    """
    if not arrays:
        return line
    # `<name>[<idx>].fX` / `.fY` first — they shadow the bare form
    # match below (which would otherwise eat the `.fX` suffix as a
    # separate ref).
    def replace_field(m: re.Match) -> str:
        name, idx_str, field = m.group(1), m.group(2), m.group(3)
        arr = arrays.get(name)
        if arr is None:
            return m.group(0)
        try:
            idx = int(idx_str)
            x, y = arr[idx]
        except (ValueError, IndexError):
            return m.group(0)
        return x if field == "fX" else y

    name_alt = "|".join(re.escape(n) for n in arrays.keys())
    field_pat = re.compile(
        r"\b(" + name_alt + r")\s*\[\s*(\d+)\s*\]\s*\.\s*(fX|fY)\b",
    )
    line = field_pat.sub(replace_field, line)

    # Bare `<name>[<idx>]` form — expand to `x, y` so callers passing
    # a whole SkPoint get two scalar args. This is exactly what Skia's
    # `path.moveTo(SkPoint)` overload does behind the scenes.
    def replace_bare(m: re.Match) -> str:
        name, idx_str = m.group(1), m.group(2)
        arr = arrays.get(name)
        if arr is None:
            return m.group(0)
        try:
            idx = int(idx_str)
            x, y = arr[idx]
        except (ValueError, IndexError):
            return m.group(0)
        return f"{x}, {y}"

    bare_pat = re.compile(r"\b(" + name_alt + r")\s*\[\s*(\d+)\s*\]")
    line = bare_pat.sub(replace_bare, line)
    return line


def expand_sk_bits2float(line: str) -> str:
    """
    Replace every `SkBits2Float(0x…)` in [line] with the decimal float
    it encodes. Preserves all other content. Handles negatives, NaNs,
    and infinities silently — Python's `struct.unpack('<f', ...)`
    propagates them as `nan` / `inf` and the downstream verb-arg-count
    check rejects them.
    """
    import struct

    def replace(m: re.Match) -> str:
        bits = int(m.group(1), 16)
        try:
            f = struct.unpack("<f", struct.pack("<I", bits & 0xFFFFFFFF))[0]
        except Exception:
            return m.group(0)
        # Use repr for round-trip-able decimal (avoids "3.4e+38" vs
        # "340282346638528859811704183484516925440" precision loss).
        return repr(f)

    return SK_BITS2FLOAT_RE.sub(replace, line)


def rect_verbs(
    l: float, t: float, r: float, b: float, direction: str | None,
) -> list[list] | None:
    """
    Expand a rectangle into the verb sequence Skia's
    `SkPath::addRect` would produce. Returns `None` for unknown
    direction. Mirrors `SkPath.cpp:emitRectPath` exactly :
    `kCW`  : moveTo(l,t) → lineTo(r,t) → lineTo(r,b) → lineTo(l,b) → close.
    `kCCW` : moveTo(l,t) → lineTo(l,b) → lineTo(r,b) → lineTo(r,t) → close.
    """
    if direction == "kCW":
        return [
            ["moveTo", l, t],
            ["lineTo", r, t],
            ["lineTo", r, b],
            ["lineTo", l, b],
            ["close"],
        ]
    if direction == "kCCW":
        return [
            ["moveTo", l, t],
            ["lineTo", l, b],
            ["lineTo", r, b],
            ["lineTo", r, t],
            ["close"],
        ]
    return None


def circle_verbs(
    cx: float, cy: float, r: float, direction: str | None,
) -> list[list] | None:
    """
    Approximate a circle with 4 cubic Bézier arcs starting from the
    rightmost point `(cx + r, cy)` and going CW (or CCW) around. Each
    quarter-arc uses the canonical [KAPPA] offset for the on-axis
    cubic control points :

        moveTo  (cx + r, cy)
        cubicTo (cx + r,    cy + kr,  cx + kr,  cy + r,   cx,     cy + r)   # to bottom
        cubicTo (cx - kr,   cy + r,   cx - r,   cy + kr,  cx - r, cy)        # to left
        cubicTo (cx - r,    cy - kr,  cx - kr,  cy - r,   cx,     cy - r)    # to top
        cubicTo (cx + kr,   cy - r,   cx + r,   cy - kr,  cx + r, cy)        # to right
        close

    Mirrors `SkPathBuilder.addCircle`'s 4-cubic emitter. Returns
    `None` for unknown direction.
    """
    if direction not in {"kCW", "kCCW"}:
        return None
    kr = KAPPA * r
    if direction == "kCW":
        # CW order : right → bottom → left → top → right.
        verbs = [
            ["moveTo", cx + r, cy],
            ["cubicTo", cx + r, cy + kr, cx + kr, cy + r, cx, cy + r],
            ["cubicTo", cx - kr, cy + r, cx - r, cy + kr, cx - r, cy],
            ["cubicTo", cx - r, cy - kr, cx - kr, cy - r, cx, cy - r],
            ["cubicTo", cx + kr, cy - r, cx + r, cy - kr, cx + r, cy],
            ["close"],
        ]
    else:
        # CCW : right → top → left → bottom → right (mirror of CW).
        verbs = [
            ["moveTo", cx + r, cy],
            ["cubicTo", cx + r, cy - kr, cx + kr, cy - r, cx, cy - r],
            ["cubicTo", cx - kr, cy - r, cx - r, cy - kr, cx - r, cy],
            ["cubicTo", cx - r, cy + kr, cx - kr, cy + r, cx, cy + r],
            ["cubicTo", cx + kr, cy + r, cx + r, cy + kr, cx + r, cy],
            ["close"],
        ]
    return verbs


def parse_fixture(name: str, body: list[str]) -> tuple[Fixture | None, str | None]:
    """Parse one fixture body. Returns (fixture, error_msg)."""
    fix = Fixture(name=name, op="")
    # Map of variable-name -> internal slot. The first path declared is
    # tagged "_a", the second "_b" (in declaration / first-use order),
    # so `path` and `pathB` (or `path1` and `path2` etc.) are stable.
    var_to_slot: dict[str, str] = {}
    # Per-fixture substitution tables populated as we walk the body :
    #   - [named_scalars] : `SkScalar xA = 0.65f;` → {"xA": "0.65"}.
    #     Later geometry calls that reference `xA` are rewritten before
    #     per-line regex matching.
    #   - [point_arrays]  : `SkPoint pts[] = { {5,6}, ... };` →
    #     {"pts": [("5", "6"), ...]}. References like `pts[0].fX`,
    #     `pts[0].fY`, and bare `pts[0]` are expanded inline.
    # Both dicts only grow ; once a name is recorded, every subsequent
    # line in this fixture's body sees the substitution.
    named_scalars: dict[str, str] = {}
    point_arrays: dict[str, list[tuple[str, str]]] = {}

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
        # Strip trailing line comments — many SkBits2Float fixtures
        # carry an inline `// 360, -2.14748e+09f` annotation that
        # would otherwise break the `\)\s*;\s*$` anchors below.
        # (Block comments inside lines are left alone — they're rare.)
        line = re.sub(r"\s*//.*$", "", line).rstrip()
        if not line or line.startswith("/*"):
            continue
        if line.startswith("if ") or line.startswith("for ") or line.startswith("while ") or line.startswith("switch "):
            return None, f"unparseable control flow at line: {line!r}"
        # Decode `SkBits2Float(0x…)` → decimal float before per-line
        # regex matching. Fixtures with hex-encoded coords are the
        # single largest skip category once addRect / Rect-factory /
        # multi-line cubics are handled.
        line = expand_sk_bits2float(line)
        # `SkScalar <name> = <literal>;` declarations — collect into
        # the substitution dict and skip. Must run BEFORE the generic
        # `unparseable line` fallback or these would abort the fixture.
        m = SK_SCALAR_DECL_RE.match(line)
        if m:
            var, val = m.group(1), m.group(2)
            named_scalars[var] = val.rstrip("f")
            continue
        # `SkPoint <name>[] = { {x, y}, ... };` array literals —
        # collect the (x, y) pairs into the substitution dict. Indices
        # are 0-based ; bare `<name>[<i>]` references and `.fX`/`.fY`
        # field accesses expand inline on later lines.
        m = SK_POINT_ARRAY_DECL_RE.match(line)
        if m:
            arr_name = m.group(1)
            body_str = m.group(2)
            elems: list[tuple[str, str]] = []
            for em in SK_POINT_ELEM_RE.finditer(body_str):
                elems.append((em.group(1).rstrip("f"), em.group(2).rstrip("f")))
            if elems:
                point_arrays[arr_name] = elems
            continue
        # Apply collected substitutions BEFORE per-line regex matching.
        # Order : point-array first (it can introduce new commas the
        # scalar pass doesn't care about), then named scalars.
        line = apply_point_array_subst(line, point_arrays)
        line = apply_named_scalar_subst(line, named_scalars)
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
        # testPathOpCheck variant — same as testPathOp but with a
        # trailing `bool ignoreEmpty` flag we don't care about.
        m = TEST_PATH_OP_CHECK_RE.match(line)
        if m:
            varA, varB, op = m.group(1), m.group(2), m.group(3)
            slot_a = var_to_slot.get(varA)
            slot_b = var_to_slot.get(varB)
            if slot_a != "_a" or slot_b != "_b":
                return None, f"path order mismatch at testPathOpCheck: {line!r}"
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
            verbs_for_rect = rect_verbs(l, t, r, b, direction)
            if verbs_for_rect is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_rect)
            continue
        # `path.addRect({l, t, r, b})` — brace-init form.
        m = ADD_RECT_BRACE_RE.match(line)
        if m:
            var = m.group(1)
            l, t, r, b = (float(m.group(i).rstrip("f")) for i in (2, 3, 4, 5))
            direction = m.group(6) or "kCW"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            verbs_for_rect = rect_verbs(l, t, r, b, direction)
            if verbs_for_rect is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_rect)
            continue
        # `path.addRect(l, t, r, b)` — 4-scalar variant.
        m = ADD_RECT_4_RE.match(line)
        if m:
            var = m.group(1)
            l, t, r, b = (float(m.group(i).rstrip("f")) for i in (2, 3, 4, 5))
            direction = m.group(6) or "kCW"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            verbs_for_rect = rect_verbs(l, t, r, b, direction)
            if verbs_for_rect is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_rect)
            continue
        # `path.addRect(SkRect::MakeLTRB(l, t, r, b))` — wrapped variant.
        m = ADD_RECT_LTRB_RE.match(line)
        if m:
            var = m.group(1)
            l, t, r, b = (float(m.group(i).rstrip("f")) for i in (2, 3, 4, 5))
            direction = m.group(6) or "kCW"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            verbs_for_rect = rect_verbs(l, t, r, b, direction)
            if verbs_for_rect is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_rect)
            continue
        # `path.addRect(SkRect::MakeXYWH(x, y, w, h))` — XYWH variant.
        m = ADD_RECT_XYWH_RE.match(line)
        if m:
            var = m.group(1)
            x, y, w, h = (float(m.group(i).rstrip("f")) for i in (2, 3, 4, 5))
            l, t, r, b = x, y, x + w, y + h
            direction = m.group(6) or "kCW"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            verbs_for_rect = rect_verbs(l, t, r, b, direction)
            if verbs_for_rect is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_rect)
            continue
        # `path.addCircle(cx, cy, r)` — expand to 4 cubic Bézier arcs.
        m = ADD_CIRCLE_RE.match(line)
        if m:
            var = m.group(1)
            cx, cy, r = (float(m.group(i).rstrip("f")) for i in (2, 3, 4))
            direction = m.group(5) or "kCW"
            slot = slot_for(var)
            if slot is None:
                return None, f"too many paths declared at line: {line!r}"
            verbs_for_circle = circle_verbs(cx, cy, r, direction)
            if verbs_for_circle is None:
                return None, f"unknown SkPathDirection: {direction!r}"
            fix.paths[slot]["verbs"].extend(verbs_for_circle)
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


def join_multiline_statements(body: list[str]) -> list[str]:
    """
    Concatenate continuation lines into single logical statements before
    per-line parsing. Many upstream fixtures emit a single
    `path.cubicTo(...)` whose arguments span 2-3 source lines, e.g. ::

        path.cubicTo(36.71843719482421875, 0.8886508941650390625,
                     38.51341247558594, 1.7773017883300781,
                     39.999961853027344, 3.255859375);

    Without joining, each continuation line ends up as `38.51341..., …,`
    which the per-line parser rejects. We join only when the previous
    line ended with a non-zero paren depth ; lines that close at depth
    0 are flushed verbatim (so multi-decl lines like
    `SkPath one = SkPath::Rect({...}), \n two = SkPath::Rect({...});`
    are NOT collapsed into one giant line — each declaration line is
    still a self-contained statement that the per-line parser handles).
    """
    out: list[str] = []
    buffer: list[str] = []
    depth = 0
    # When a line starts an `SkPoint <name>[] = {` array initialiser
    # that doesn't close on the same line, we'd normally flush
    # immediately (the paren-depth tracker doesn't care about braces).
    # We add a separate "brace-init" mode that latches on until a
    # matching `};` is seen, then flushes the joined block. This is
    # narrowly scoped to SkPoint array literals — generic brace
    # tracking would mistakenly fold control-flow blocks together.
    in_brace_init = False
    array_init_re = re.compile(r"^\s*SkPoint\s+\w+\s*\[\s*\]\s*=\s*\{")
    for raw in body:
        # Strip line comments (`// …`) before counting parens — a
        # `// (oops)` would otherwise unbalance the depth tracker.
        stripped = re.sub(r"//.*$", "", raw)
        line_open_minus_close = stripped.count("(") - stripped.count(")")
        new_depth = depth + line_open_minus_close
        buffer.append(raw.rstrip())
        # Detect entry into an SkPoint-array brace block. We only enter
        # if this is the first line of the buffer (i.e. we're not in
        # the middle of an open paren statement) and the line opens but
        # doesn't close the array init.
        if not in_brace_init and len(buffer) == 1 and depth == 0:
            if array_init_re.match(stripped) and "};" not in stripped:
                in_brace_init = True
        if in_brace_init:
            # Stay latched until we see the closing `};` on this line.
            if "};" in stripped:
                in_brace_init = False
                joined = " ".join(s.strip() for s in buffer if s.strip())
                if joined:
                    out.append(joined)
                buffer = []
                depth = 0
            continue
        if new_depth <= 0:
            # Statement boundary : flush whatever's in the buffer as
            # one joined line. Empty / brace-only lines flush to
            # themselves untouched.
            joined = " ".join(s.strip() for s in buffer if s.strip())
            if joined:
                out.append(joined)
            buffer = []
            depth = 0
        else:
            depth = new_depth
    if buffer:
        joined = " ".join(s.strip() for s in buffer if s.strip())
        if joined:
            out.append(joined)
    return out


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
        raw_body = lines[body_start : j - 1]
        # Pre-pass : join multi-line statements so cubicTo / quadTo
        # spanning 2-3 source lines parse as one logical line.
        body = join_multiline_statements(raw_body)
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
