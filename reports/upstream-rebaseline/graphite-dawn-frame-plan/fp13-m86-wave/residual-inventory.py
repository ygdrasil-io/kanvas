#!/usr/bin/env python3
"""FP-13 Task 0 (M86 burn-down wave) — residual-row inventory generator.

Parses the blend-suite matrix source and the clip pin sources programmatically
and emits:

  residual-inventory.csv    — every one of the 341 residual rows, one per line
  ranked-candidates.md     — the same rows ranked by PM value / risk

Row data is derived from:
  * kanvas/src/main/kotlin/org/graphiks/kanvas/paint/BlendMode.kt
    (the 29-mode enum order the matrix iterates)
  * kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt
    (the 7-core-API route matrix and its mode-set constants)
  * kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageSurfaceTest.kt
  * kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipAdvancedBlendSurfaceTest.kt
  * kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPathClipRegressionTest.kt
    (the 16 clip pins)

The refusal-code columns come from the fp-11 evidence §0.3 distribution mapping
(verified closure-HEAD measurement) plus the pinned codes in the test source;
pmValue/risk are item-level judgment fields encoded below so every row of an
item gets identical values.

Usage:
  python3 residual-inventory.py            # write CSV + ranked md
  python3 residual-inventory.py --check    # verify counts, write nothing
  python3 residual-inventory.py --print    # write files and print counts

No external dependencies; Python 3.8+ stdlib only.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))))))
OUT_DIR = os.path.dirname(os.path.abspath(__file__))

BLEND_MODE_PATH = os.path.join(
    ROOT, "kanvas/src/main/kotlin/org/graphiks/kanvas/paint/BlendMode.kt")
BLEND_MATRIX_PATH = os.path.join(
    ROOT, "kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAllApiBlendSurfaceTest.kt")
CLIP_COVERAGE_PATH = os.path.join(
    ROOT, "kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipCoverageSurfaceTest.kt")
CLIP_ADVANCED_PATH = os.path.join(
    ROOT, "kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUClipAdvancedBlendSurfaceTest.kt")
CLIP_PATH_PATH = os.path.join(
    ROOT, "kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPathClipRegressionTest.kt")

RESIDUAL_CSV = os.path.join(OUT_DIR, "residual-inventory.csv")
RANKED_MD = os.path.join(OUT_DIR, "ranked-candidates.md")

# ---------------------------------------------------------------------------
# fp-11 §0.3 verified refusal-code mapping (closure HEAD measurement)
# ---------------------------------------------------------------------------

MIXED = "unsupported.recording.core_primitive_mixed_uniform_layouts"
PATH_DST_READ = "unsupported.native-core-primitive.path-destination-read"
DIRECT_GEOMETRY = "invalid.preflight.core_primitive_direct_geometry_resources"
FRAME_GLOBAL = "unsupported.native-core-primitive.frame-global-pipeline"
ANALYTIC_NON_DIRECT = "unsupported.recording.core_primitive_analytic_clip_non_direct_geometry"
DST_READ_FORMULA = "unsupported.native-core-primitive.dst-read-formula"
MULTI_KEY = "unsupported.native-core-primitive.analytic-shape-multi-key"
CLIP_PRODUCER_AUTHORITY = "invalid.preflight.core_primitive_clip_producer_authority"

# Reference kind: every residual row is verified (or would be verified) against
# the pure-Kotlin pixel oracle of the blend/clip suites — never against an
# upstream Skia reference, so none of them count as Skia-comparable fidelity.
REFERENCE_KIND = "cpu-oracle"

# Item-level judgment fields (M86 ranked-candidate columns). Score = value/risk
# with high=3, medium=2, low=1; ranking is score desc, then rows desc, then
# item number asc. Root-cause and route columns mirror the fp-13 plan §1 table.
ITEM_META = {
    1: {
        "rows": 32,  # 2 dst-read-formula pins + 30 frame-global-pipeline fallout
        "rootCause": ("no closed analytic-shape dst-read formula pipeline on the prepared lane "
                      "(fp-11 §5; the 30 frame-global re-point rows share this root)"),
        "route": "prepared-dst-read-formula",
        "pm": "high",
        "pmReason": "closes the dst-read formula root and the 30-row frame-global fallout with one pipeline",
        "risk": "medium",
        "riskReason": "new prepared-lane pipeline wiring on an execution surface not exercised before",
        "owner": 3,
    },
    2: {
        "rows": 2,
        "rootCause": "same root as item 1; multi-key analytic shape x dst-read matrix rows",
        "route": "prepared-dst-read-formula",
        "pm": "low",
        "pmReason": "two AA-coverage edge pins that ride the item 1 pipeline",
        "risk": "low",
        "riskReason": "no new execution feature; semantics already documented (fp-11 §2)",
        "owner": 4,
    },
    3: {
        "rows": 2,
        "rootCause": ("core_primitive_clip_producer_authority: mask-blur composite under a complex "
                      "(multi-rect) analytic clip refused at the clip producer preflight"),
        "route": "prepared-composite-analytic-clip",
        "pm": "medium",
        "pmReason": "blur under complex clip is common product behavior; extends the shipped FP-11 Task 7 ABI",
        "risk": "low",
        "riskReason": "preflight/clip-producer admission on the already-shipped analytic-clip ABI",
        "owner": 5,
    },
    4: {
        "rows": 209,  # 199 blend rows + 10 clip pins
        "rootCause": ("unwired analytic-clip 64/160 split: builder gate "
                      "GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2132; needs the per-step "
                      "continuation/ownership design (fp-11 §4); split-lane lease cleanup already "
                      "landed (3bd78e180)"),
        "route": "split-uniform64-160",
        "pm": "high",
        "pmReason": "largest row family (209); mechanical split; biggest breadth-score delta",
        "risk": "medium",
        "riskReason": "mechanical but needs the continuation/ownership design; deterministic session-close residual documented",
        "owner": 6,
    },
    5: {
        "rows": 4,
        "rootCause": ("analytic_clip_non_direct_geometry gate "
                      "GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009 (twin :2016): analytic clip "
                      "over non-direct/stencil-shaded geometry is a new execution feature (fp-11 §2)"),
        "route": "analytic-clip-non-direct",
        "pm": "low",
        "pmReason": "four ALPHA_MASK x DST edge rows only",
        "risk": "medium",
        "riskReason": "new admission for non-direct shading geometry; depends on the item 4 frame",
        "owner": 7,
    },
    6: {
        "rows": 60,
        "rootCause": ("path-stencil execution model cannot express dst-read: recording refusal "
                      "GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2565-2575, preflighter "
                      "exactly-one-pass gate GPUFramePreflighter.kt:2437-2440, materializer "
                      "supportedPathComponents exclusion, per-run stencil Clear+Discard (fp-11 §3)"),
        "route": "stencil-continuation-path-cover",
        "pm": "high",
        "pmReason": "60 rows of the most common paint family (path blends) convert to rendered output",
        "risk": "high",
        "riskReason": "new execution feature (stencil-continuation); fp-11 §3 documents wrong-pixels risk",
        "owner": 8,
    },
}

def _score(item: int) -> float:
    meta = ITEM_META[item]
    value = {"high": 3, "medium": 2, "low": 1}[meta["pm"]]
    risk = {"high": 3, "medium": 2, "low": 1}[meta["risk"]]
    return value / risk


RANK_ORDER = sorted(
    ITEM_META,
    key=lambda i: (-_score(i), -ITEM_META[i]["rows"], i),
)
assert RANK_ORDER == [3, 4, 1, 6, 2, 5], RANK_ORDER


# ---------------------------------------------------------------------------
# Source parsing helpers
# ---------------------------------------------------------------------------

def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def parse_blend_modes() -> list:
    """Parse the BlendMode enum order from BlendMode.kt (29 entries)."""
    src = read(BLEND_MODE_PATH)
    m = re.search(r"enum class BlendMode\s*{(.*?)}", src, re.S)
    assert m, "BlendMode enum block not found"
    modes = [tok.strip() for tok in m.group(1).replace("\n", " ").split(",")]
    modes = [tok for tok in modes if tok]
    assert len(modes) == 29, f"expected 29 BlendMode entries, got {len(modes)}"
    return modes


def parse_blend_contexts() -> list:
    """Parse the BlendContext enum from the blend matrix source (4 entries)."""
    src = read(BLEND_MATRIX_PATH)
    m = re.search(r"private enum class BlendContext\s*{(.*?)}", src, re.S)
    assert m, "BlendContext enum block not found"
    ctxs = [tok.strip() for tok in m.group(1).split(",") if tok.strip()]
    assert ctxs == ["UNCLIPPED", "SCISSOR", "ALPHA_MASK", "SAVE_LAYER"], ctxs
    return ctxs


def parse_artistic_modes(modes: list) -> set:
    """Evaluate ARTISTIC_MODES = entries.filter { ordinal >= MULTIPLY.ordinal }."""
    src = read(BLEND_MATRIX_PATH)
    m = re.search(r"val ARTISTIC_MODES = BlendMode\.entries\.filter \{ it\.ordinal >= BlendMode\.(\w+)\.ordinal \}",
                  src)
    assert m, "ARTISTIC_MODES expression not found"
    start = modes.index(m.group(1))
    return set(modes[start:])


def parse_dst_copy_modes(modes: list) -> set:
    """Evaluate MULTI_RENDER_DST_COPY_MODES from its source expression.

    Expected: (ARTISTIC_MODES - BlendMode.SCREEN) + BlendMode.PLUS, which the
    fp-11 §0.3 measurement counts as the 15 dst-read/dst-copy modes.
    """
    src = read(BLEND_MATRIX_PATH)
    m = re.search(
        r"val MULTI_RENDER_DST_COPY_MODES = \(ARTISTIC_MODES - BlendMode\.(\w+)\) \+ BlendMode\.(\w+)",
        src)
    assert m, "MULTI_RENDER_DST_COPY_MODES expression not found"
    result = parse_artistic_modes(modes) - {m.group(1)} | {m.group(2)}
    assert len(result) == 15, \
        f"parsed MULTI_RENDER_DST_COPY_MODES has {len(result)} modes, expected 15 (fp-11 §0.3)"
    return result


def pin_test_name(src: str, line_index: int) -> str:
    """Find the enclosing test function name for a source line."""
    before = src.split("\n")[:line_index]
    for line in reversed(before):
        m = re.search(r"fun\s+`([^`]+)`\s*\(", line)
        if m:
            return m.group(1)
        m = re.search(r"fun\s+(\w+)\s*\(", line)
        if m:
            return m.group(1)
    return "(unknown pin test)"


# ---------------------------------------------------------------------------
# Row enumeration (blend matrix + clip pins)
# ---------------------------------------------------------------------------

def blend_rows(modes: list, contexts: list, dst_copy: set):
    """Enumerate the residual blend-matrix rows (fp-11 §0.3 distribution).

    Mirrors the core-primitive `when` branches of expectedPreparedProductRoute
    (GPUAllApiBlendSurfaceTest.kt:582-643); only residual (non-Prepared) rows
    are emitted. `dst` is the DST mode; dst-read and dst-copy coincide on the
    15 parsed modes (verified below by the fp-11 §0.3 counts).
    """
    dst = modes.index("DST")
    dst_mode = modes[dst]
    rows = []
    for family in ["DrawRRect", "DrawRect", "DrawColor", "DrawPath", "DrawDRRect",
                   "DrawPoint", "DrawPoints"]:
        for mode in modes:
            for context in contexts:
                row = _route(family, mode, context, dst_mode, dst_copy)
                if row is not None:
                    item, code, route = row
                    rows.append({
                        "item": item,
                        "family": family,
                        "mode": mode,
                        "context": context,
                        "refusalCode": code,
                        "referenceKind": REFERENCE_KIND,
                        "expectedGpuRoute": route,
                        "pmValue": ITEM_META[item]["pm"],
                        "risk": ITEM_META[item]["risk"],
                        "ownerTask": ITEM_META[item]["owner"],
                    })
    return rows


def _route(family, mode, context, dst_mode, dst_copy):
    """The fp-11 §0.3 mapping per family branch (returns None for Prepared)."""
    if context == "ALPHA_MASK":
        if family in ("DrawRect", "DrawColor", "DrawPoint", "DrawPoints"):
            if mode == dst_mode:
                return (5, ANALYTIC_NON_DIRECT, ITEM_META[5]["route"])
        return (4, MIXED, ITEM_META[4]["route"])
    # UNCLIPPED / SCISSOR
    if family == "DrawRRect":
        if mode == dst_mode:
            return (4, DIRECT_GEOMETRY, ITEM_META[4]["route"])
        if mode in dst_copy:
            return (1, FRAME_GLOBAL, ITEM_META[1]["route"])
    elif family in ("DrawPath", "DrawDRRect"):
        if mode in dst_copy:
            return (6, PATH_DST_READ, ITEM_META[6]["route"])
    elif family == "DrawPoint":
        if mode in dst_copy:
            return (4, DIRECT_GEOMETRY, ITEM_META[4]["route"])
    return None


def clip_pin_rows():
    """Parse the three clip-suite pin files for the 16 clip rows."""
    rows = []

    def add(item, family, mode, context, code, source, line):
        rows.append({
            "item": item,
            "family": family,
            "mode": mode,
            "context": context,
            "refusalCode": code,
            "referenceKind": REFERENCE_KIND,
            "expectedGpuRoute": ITEM_META[item]["route"],
            "pmValue": ITEM_META[item]["pm"],
            "risk": ITEM_META[item]["risk"],
            "ownerTask": ITEM_META[item]["owner"],
            "_src": source,
            "_line": line,
            "_test": pin_test_name(source, line - 1),
        })

    coverage = read(CLIP_COVERAGE_PATH)
    coverage_lines = coverage.split("\n")

    # 10 pins on the mixed-layout code: Coverage 1 + Advanced 8 + PathClip 1.
    assert "assertTerminal(PREPARED_MIXED_UNIFORM_LAYOUTS_REFUSAL" in coverage_lines[111]
    add(4, "clip:coverage", "n/a", "ALPHA_MASK", MIXED, coverage, 112)

    advanced = read(CLIP_ADVANCED_PATH)
    advanced_lines = advanced.split("\n")
    m = re.search(r"val expectedByMode = listOf\((.*?)\)", advanced, re.S)
    assert m, "expectedByMode list not found in GPUClipAdvancedBlendSurfaceTest"
    loop_modes = re.findall(r"BlendMode\.(\w+)", m.group(1))
    assert loop_modes == ["MULTIPLY", "SCREEN", "OVERLAY", "DARKEN", "LIGHTEN",
                          "DIFFERENCE", "EXCLUSION"], loop_modes
    # 7 loop modes asserted at :53-59 plus the partial-alpha DARKEN case at :61-67.
    assert "renderClippedBlend(" in advanced_lines[53]
    for idx, mode in enumerate(loop_modes):
        add(4, "clip:advanced", mode, "ALPHA_MASK", MIXED, advanced, 53 + idx)
    assert "withAlphaByte(128)" in advanced_lines[63]
    add(4, "clip:advanced", "DARKEN(partial-alpha)", "ALPHA_MASK", MIXED, advanced, 61)

    path_clip = read(CLIP_PATH_PATH)
    path_clip_lines = path_clip.split("\n")
    assert '"unsupported.recording.core_primitive_mixed_uniform_layouts"' in path_clip_lines[58]
    add(4, "clip:path", "n/a", "device-rect-clip", MIXED, path_clip, 25)

    # 2 complex-clip blur pins on clip_producer_authority (Coverage :175, :193).
    assert "assertTerminal(PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL)" in coverage_lines[174]
    add(3, "clip:coverage", "n/a", "complex-clip(AA-intersect+path-difference)", CLIP_PRODUCER_AUTHORITY,
        coverage, 175)
    assert "assertEquals(PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL" in coverage_lines[192]
    add(3, "clip:coverage", "sigma=1.5", "complex-clip(AA-intersect+path-difference)", CLIP_PRODUCER_AUTHORITY,
        coverage, 193)

    # 2 dst-read-formula pins (item 1): single-key DARKEN :409, multi-key COLOR_DODGE :433.
    assert "assertTerminal(PREPARED_DST_READ_FORMULA_REFUSAL" in coverage_lines[408]
    add(1, "clip:coverage", "DARKEN", "UNCLIPPED", DST_READ_FORMULA, coverage, 409)
    assert "assertTerminal(PREPARED_DST_READ_FORMULA_REFUSAL" in coverage_lines[432]
    add(1, "clip:coverage", "COLOR_DODGE", "UNCLIPPED", DST_READ_FORMULA, coverage, 433)

    # 2 analytic-shape multi-key pins (item 2): AA clip :331, scissor :354.
    assert "assertTerminal(PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL" in coverage_lines[330]
    add(2, "clip:coverage", "CLEAR/SRC/DST_IN", "ALPHA_MASK", MULTI_KEY, coverage, 331)
    assert "assertTerminal(PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL" in coverage_lines[353]
    add(2, "clip:coverage", "CLEAR/SRC/DST_IN", "SCISSOR", MULTI_KEY, coverage, 354)

    return rows


# ---------------------------------------------------------------------------
# Verification (fp-11 §0.3 arithmetic)
# ---------------------------------------------------------------------------

def expected_distribution():
    """The fp-11 §0.3 / plan §1 arithmetic the enumeration must reproduce."""
    return {
        # Item 4 = 209 primary rows (199 blend + 10 clip pins) + the 32 Task-6
        # split-resource fallout rows (2 DrawRRect DST + 30 DrawPoint on
        # direct_geometry_resources, owned by Task 6 per plan §1/§5).
        "item": {1: 32, 2: 2, 3: 2, 4: 241, 5: 4, 6: 60},
        "code": {
            MIXED: 199 + 10,
            PATH_DST_READ: 60,
            DIRECT_GEOMETRY: 32,
            FRAME_GLOBAL: 30,
            ANALYTIC_NON_DIRECT: 4,
            DST_READ_FORMULA: 2,
            MULTI_KEY: 2,
            CLIP_PRODUCER_AUTHORITY: 2,
        },
        "blend": {MIXED: 199, PATH_DST_READ: 60, DIRECT_GEOMETRY: 32,
                  FRAME_GLOBAL: 30, ANALYTIC_NON_DIRECT: 4},
        "clip": {MIXED: 10, CLIP_PRODUCER_AUTHORITY: 2, DST_READ_FORMULA: 2, MULTI_KEY: 2},
        "families": {  # fp-11 §0.3 per-family blend counts
            "DrawRRect": 61, "DrawRect": 29, "DrawColor": 29, "DrawPath": 59,
            "DrawDRRect": 59, "DrawPoint": 59, "DrawPoints": 29,
        },
    }


def verify(rows, print_counts=False):
    errors = []
    exp = expected_distribution()
    total = len(rows)
    if total != 341:
        errors.append(f"total rows {total} != 341")
    by_item = {}
    by_code = {}
    by_family = {}
    for r in rows:
        by_item[r["item"]] = by_item.get(r["item"], 0) + 1
        by_code[r["refusalCode"]] = by_code.get(r["refusalCode"], 0) + 1
        by_family[r["family"]] = by_family.get(r["family"], 0) + 1
    for item, count in exp["item"].items():
        if by_item.get(item) != count:
            errors.append(f"item {item}: {by_item.get(item)} rows != {count}")
    for code, count in exp["code"].items():
        if by_code.get(code) != count:
            errors.append(f"code {code}: {by_code.get(code)} rows != {count}")
    blend_codes = {r["refusalCode"] for r in rows if r["family"] in
                   ("DrawRRect", "DrawRect", "DrawColor", "DrawPath", "DrawDRRect",
                    "DrawPoint", "DrawPoints")}
    blend_families = {r["family"] for r in rows if r["family"] in
                      ("DrawRRect", "DrawRect", "DrawColor", "DrawPath", "DrawDRRect",
                       "DrawPoint", "DrawPoints")}
    for family, count in exp["families"].items():
        got = sum(1 for r in rows if r["family"] == family)
        if got != count:
            errors.append(f"family {family}: {got} blend rows != {count}")
    # The 30 frame-global rows must be exactly DrawRRect x dst-copy x (UNCLIPPED, SCISSOR)
    # and the 30 direct-geometry DrawPoint rows exactly the dst-copy x 2-context set.
    if any(r["family"] != "DrawRRect" for r in rows if r["refusalCode"] == FRAME_GLOBAL):
        errors.append("frame-global rows include non-DrawRRect families")
    if any(r["family"] != "DrawPoint" or r["context"] not in ("UNCLIPPED", "SCISSOR")
           for r in rows if r["refusalCode"] == DIRECT_GEOMETRY and r["family"] != "DrawRRect"):
        errors.append("direct-geometry rows include non-DrawPoint/DrawRRect families")
    if print_counts:
        print(f"residual-inventory: total={total}")
        for item in sorted(by_item):
            meta = ITEM_META[item]
            print(f"  item {item}: {by_item[item]:3d} rows  pm={meta['pm']} risk={meta['risk']} "
                  f"route={meta['route']} ownerTask={meta['owner']}")
        for code, count in sorted(by_code.items(), key=lambda kv: -kv[1]):
            print(f"  {count:3d} {code}")
    return errors


def rank_key(row):
    item = row["item"]
    family_order = ["DrawRRect", "DrawRect", "DrawColor", "DrawPath", "DrawDRRect",
                    "DrawPoint", "DrawPoints", "clip:coverage", "clip:advanced", "clip:path"]
    context_order = ["UNCLIPPED", "SCISSOR", "ALPHA_MASK", "device-rect-clip",
                     "complex-clip(AA-intersect+path-difference)"]
    return (RANK_ORDER.index(item), family_order.index(row["family"]),
            row.get("_src", ""), row.get("_line", 0),
            _mode_index(row), context_order.index(row["context"]))


def _mode_index(row):
    if row["family"] == "clip:advanced":
        order = ["MULTIPLY", "SCREEN", "OVERLAY", "DARKEN", "LIGHTEN",
                 "DIFFERENCE", "EXCLUSION", "DARKEN(partial-alpha)"]
        return order.index(row["mode"])
    return 0


def write_csv(rows):
    columns = ["item", "family", "mode", "context", "refusalCode", "referenceKind",
               "expectedGpuRoute", "pmValue", "risk", "ownerTask"]
    with open(RESIDUAL_CSV, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in sorted(rows, key=rank_key):
            writer.writerow(row)


def write_ranked_md(rows):
    lines = []
    lines.append("# FP-13 Task 0 (M86 burn-down wave) — ranked candidate list\n")
    lines.append("Ranking formula: PM value score (high=3, medium=2, low=1) divided by risk score "
                 "(high=3, medium=2, low=1); ties broken by row count desc, then item number. "
                 "Per-row machine-readable source: `residual-inventory.csv` (341 rows).\n")
    lines.append("Reference-kind note: **every residual row is `cpu-oracle`** (pure-Kotlin pixel "
                 "oracle of the blend/clip suites). CPU-oracle rows do not count as Skia-comparable "
                 "fidelity (M86 statement, evidence §1.3).\n")
    lines.append("| rank | item | rows | refusal code | expected GPU route | PM value | risk | owner task |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for rank, item in enumerate(RANK_ORDER, start=1):
        meta = ITEM_META[item]
        lines.append(f"| {rank} | {item} | {meta['rows']} | {_item_code(item)} | `{meta['route']}` "
                     f"| **{meta['pm']}** | **{meta['risk']}** | Task {meta['owner']} |")
    lines.append("")
    for rank, item in enumerate(RANK_ORDER, start=1):
        meta = ITEM_META[item]
        item_rows = [r for r in sorted(rows, key=rank_key) if r["item"] == item]
        if item == 4:
            primary = len(item_rows) - 32
            lines.append(f"## Item {item} (rank {rank}) — {len(item_rows)} rows "
                         f"({primary} primary + 32 Task-6 split-resource fallout)\n")
        else:
            lines.append(f"## Item {item} (rank {rank}) — {len(item_rows)} rows\n")
        lines.append(f"- **Root cause**: {meta['rootCause']}")
        lines.append(f"- **Expected GPU route**: `{meta['route']}`")
        lines.append(f"- **PM value**: {meta['pm']} — {meta['pmReason']}")
        lines.append(f"- **Risk**: {meta['risk']} — {meta['riskReason']}")
        lines.append(f"- **Owner task**: Task {meta['owner']} (plan §5)\n")
        lines.append("| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for idx, row in enumerate(item_rows, start=1):
            lines.append(f"| {idx} | {row['family']} | {row['mode']} | {row['context']} | "
                         f"`{row['refusalCode']}` | {row['referenceKind']} | "
                         f"`{row['expectedGpuRoute']}` | {row['pmValue']} | {row['risk']} | "
                         f"Task {row['ownerTask']} |")
        lines.append("")
    lines.append("## Cross-checks (fp-11 §0.3 / plan §1 arithmetic)\n")
    lines.append("- 341 rows = 199 mixed-layout blend + 10 clip pins + 60 path-destination-read + "
                 "32 direct-geometry re-points (2 DrawRRect DST + 30 DrawPoint) + 30 "
                 "frame-global-pipeline re-points + 4 analytic-clip-non-direct + 2 dst-read-formula "
                 "+ 2 multi-key + 2 complex-clip blur pins.")
    lines.append("- Blend distribution: mixed 199, path-destination-read 60, direct_geometry 32 "
                 "(incl. DrawPoint), frame-global 30, analytic-clip-non-direct 4.")
    lines.append("- Clip pins: mixed 10 (Coverage 1, Advanced 8, PathClip 1), clip-producer-authority 2, "
                 "dst-read-formula 2, multi-key 2.")
    with open(RANKED_MD, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def _item_code(item):
    return {1: DST_READ_FORMULA, 2: MULTI_KEY, 3: CLIP_PRODUCER_AUTHORITY,
            4: MIXED, 5: ANALYTIC_NON_DIRECT, 6: PATH_DST_READ}[item]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify counts only, write nothing")
    parser.add_argument("--print", action="store_true", help="print counts after writing")
    args = parser.parse_args()

    modes = parse_blend_modes()
    contexts = parse_blend_contexts()[:3]  # UNCLIPPED, SCISSOR, ALPHA_MASK
    dst_copy = parse_dst_copy_modes(modes)

    rows = blend_rows(modes, contexts, dst_copy) + clip_pin_rows()
    errors = verify(rows, print_counts=args.print or args.check)
    if errors:
        for err in errors:
            print(f"VERIFY FAIL: {err}")
        sys.exit(1)
    if args.check:
        print("residual-inventory: 341 rows verified (item/code/family arithmetic matches "
              "fp-11 §0.3 + plan §1)")
        return
    write_csv(rows)
    write_ranked_md(rows)
    print(f"wrote {RESIDUAL_CSV} ({len(rows)} rows)")
    print(f"wrote {RANKED_MD}")
    if not args.print:
        print("residual-inventory: OK (run with --print for the distribution)")


if __name__ == "__main__":
    main()
