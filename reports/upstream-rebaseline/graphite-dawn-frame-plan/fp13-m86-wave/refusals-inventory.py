#!/usr/bin/env python3
"""FP-13 Task 0 (M86 burn-down wave) — SkiaGmRunner refusal inventory generator.

Parses the COMMITTED JUnit XML snapshot
  fp13-m86-wave/junit-xml-2026-08-13/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml
programmatically (stdlib xml.etree) and emits:

  refusals-inventory.csv — all 498 SkiaGmRunner failure rows, one per GM case:
    489 terminal GPUPreparedSurfaceTerminalException refusals
      + 7 missing-reference + 1 size-mismatch + 1 below-threshold rows

Columns: gm (logical SkiaGm.name), refusalCode (extracted diagnostic code, or
the non-terminal row kind), rootCauseBucket (fp-11 §1/§2-style classification),
item (fp-13 plan §1 item this refusal maps to, or none).

The GM logical name is resolved from the GM Kotlin sources (class body `name`
declaration; parent-constructor literal/named arg; curated defaults for the
computed-name classes, each pinned by a source assertion). The script reads no
build output — only the committed XML copy and committed sources.

Usage:
  python3 refusals-inventory.py            # write CSV
  python3 refusals-inventory.py --check    # verify counts, write nothing
  python3 refusals-inventory.py --print    # write CSV and print counts
"""

from __future__ import annotations

import argparse
import csv
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))))))
OUT_DIR = os.path.dirname(os.path.abspath(__file__))

XML_PATH = os.path.join(
    OUT_DIR, "junit-xml-2026-08-13/TEST-org.graphiks.kanvas.skia.SkiaGmRunner.xml")
GM_DIR = os.path.join(
    ROOT, "integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm")
REFUSALS_CSV = os.path.join(OUT_DIR, "refusals-inventory.csv")

MIXED = "unsupported.recording.core_primitive_mixed_uniform_layouts"
PATH_DST_READ = "unsupported.native-core-primitive.path-destination-read"
CLIP_PRODUCER_AUTHORITY = "invalid.preflight.core_primitive_clip_producer_authority"

# fp-13 plan §1 item mapping for GM refusals (everything else is `none`).
ITEM_BY_CODE = {
    MIXED: 4,
    PATH_DST_READ: 6,
    CLIP_PRODUCER_AUTHORITY: 3,
}

# Root-cause buckets (fp-11 §1/§2 classification style). Prefix rules are
# ordered; the first matching prefix wins.
BUCKET_RULES = [
    ("unsupported.native-core-primitive", "unsupported execution feature"),
    ("unsupported.recording", "unsupported recording feature"),
    ("unsupported.material", "unsupported material feature"),
    ("unsupported.geometry", "unsupported geometry feature"),
    ("unsupported.core_primitive", "unsupported core-primitive execution feature"),
    ("unsupported.image", "unsupported image/sampler feature"),
    ("unsupported.composite", "unsupported composite operation"),
    ("unsupported.text", "unsupported text/glyph feature"),
    ("unsupported.vertices", "unsupported vertices/mesh feature"),
    ("unsupported.transform", "unsupported transform feature"),
    ("unsupported.layer", "unsupported layer feature"),
    ("unsupported.surface", "unsupported prepared-surface topology"),
    ("unsupported.native-mask-blur", "unsupported native mask-blur feature"),
    ("invalid", "invalid frame plan / preflight seal"),
    ("failed", "runtime failure class"),
    ("stale", "stale resource generation / preflight staleness"),
]


def root_cause_bucket(code: str) -> str:
    for prefix, bucket in BUCKET_RULES:
        if code.startswith(prefix):
            return bucket
    return f"unclassified ({code})"


# ---------------------------------------------------------------------------
# GM logical-name resolver (class body / parent ctor / curated)
# ---------------------------------------------------------------------------

# Curated defaults for computed-name classes (default no-arg construction is
# what SkiaGmRegistry.all() instantiates, SkiaGmRegistry.kt:20). Each entry is
# pinned by a source assertion in resolve_gm_names().
CURATED_NAMES = {
    "SurfacePropsGm": ("surfaceprops", "if (useDistanceField) \"surfaceprops_df\" else \"surfaceprops\""),
    "FontMgrBoundsGm": ("fontmgr_bounds", "if (fScaleX != 1f || fSkewX != 0f)"),
    "VerticesGm": ("vertices", "if (shaderScale != 1f) \"vertices_scaled_shader\" else \"vertices\""),
    "Dashing5Gm": ("dashing5_aa", "constructor() : this(true)"),
    "SimpleShapesGm": ("simpleshapes", "if (antialias) \"simpleshapes\" else \"simpleshapes_bw\""),
    "SimpleShapesBwGm": ("simpleshapes_bw", "SimpleShapesGm(antialias = false)"),
    "TilemodesGm": ("tilemodes", "if (powerOfTwoSize) \"tilemodes\" else \"tilemodes_npot\""),
    "ScaledTilemodesGm": ("scaled_tilemodes", "powerOfTwoSize: Boolean = true"),
    "ScaledTilemodesNpotGm": ("scaled_tilemodes_npot", "ScaledTilemodesGm(powerOfTwoSize = false)"),
    "EncodeColorTypesGm": ("encode-color-types-webp-lossless", "constructor() : this(Variant.kNormal, \"webp-lossless\")"),
    "EncodeSrgbGm": ("encode-srgb-png", "constructor() : this(\"png\")"),
    "PerspShadersGm": ("persp_shaders_aa", "if (doAA) \"persp_shaders_aa\" else \"persp_shaders_bw\""),
    "PerspShadersBwGm": ("persp_shaders_bw", "PerspShadersGm(false)"),
    "ManyPathAtlases128Gm": ("manypathatlases_128", "ManyPathAtlasesGm(128)"),
    "ManyPathAtlases2048Gm": ("manypathatlases_2048", "ManyPathAtlasesGm(2048)"),
    "ComposeShaderBitmapGm": ("composeshader_bitmap", "constructor() : this(false)"),
    "ComposeShaderBitmapLmGm": ("composeshader_bitmap_lm", "ComposeShaderBitmapGm(true)"),
    "DrawBitmapRect2FloatGm": ("bitmaprect_s", "Variant.FLOAT"),
    "DrawBitmapRect2IntGm": ("bitmaprect_i", "Variant.INT"),
    "GiantBitmapClampPointScale": ("giantbitmap_clamp_point_scale", "TileMode.CLAMP, false, false"),
    "GiantBitmapRepeatPointScale": ("giantbitmap_repeat_point_scale", "TileMode.REPEAT, false, false"),
    "GiantBitmapMirrorPointScale": ("giantbitmap_mirror_point_scale", "TileMode.MIRROR, false, false"),
    "GiantBitmapClampBilerpScale": ("giantbitmap_clamp_bilerp_scale", "TileMode.CLAMP, true, false"),
    "GiantBitmapRepeatBilerpScale": ("giantbitmap_repeat_bilerp_scale", "TileMode.REPEAT, true, false"),
    "GiantBitmapMirrorBilerpScale": ("giantbitmap_mirror_bilerp_scale", "TileMode.MIRROR, true, false"),
    "GiantBitmapClampPointRotate": ("giantbitmap_clamp_point_rotate", "TileMode.CLAMP, false, true"),
    "GiantBitmapRepeatPointRotate": ("giantbitmap_repeat_point_rotate", "TileMode.REPEAT, false, true"),
    "GiantBitmapMirrorPointRotate": ("giantbitmap_mirror_point_rotate", "TileMode.MIRROR, false, true"),
    "GiantBitmapClampBilerpRotate": ("giantbitmap_clamp_bilerp_rotate", "TileMode.CLAMP, true, true"),
    "GiantBitmapRepeatBilerpRotate": ("giantbitmap_repeat_bilerp_rotate", "TileMode.REPEAT, true, true"),
    "GiantBitmapMirrorBilerpRotate": ("giantbitmap_mirror_bilerp_rotate", "TileMode.MIRROR, true, true"),
}

CLASS_RE = re.compile(
    r"(?m)^(?:abstract\s+|data\s+|sealed\s+|open\s+|final\s+)*class\s+(\w+)(.*)$", re.M)


def _package_of(src: str) -> str:
    m = re.search(r"^package\s+([\w.]+)", src, re.M)
    return m.group(1) if m else ""


def _class_decls():
    """Map FQN -> (header, body scope, file path) for every SkiaGm class.

    Handles both bodied classes (`class X(...) { ... }`) and body-less
    subclasses (`class X : Parent(...)` closing with a bare `)`).
    """
    decls = {}
    next_class = re.compile(
        r"(?m)^(?:abstract\s+|data\s+|sealed\s+|open\s+|final\s+)*class\s+\w+")
    for path in glob.glob(os.path.join(GM_DIR, "**", "*.kt"), recursive=True):
        with open(path, encoding="utf-8") as fh:
            src = fh.read()
        pkg = _package_of(src)
        for m in CLASS_RE.finditer(src):
            cls = m.group(1)
            rest_of_line = m.group(2)
            brace = rest_of_line.find("{")
            if brace >= 0:
                header = rest_of_line[:brace]
                tail = src[m.end() - len(rest_of_line) + brace + 1:]
                nxt = next_class.search(tail)
                tail = tail[:nxt.start()] if nxt else tail
                close = tail.rfind("}")
                scope = tail[:close] if close >= 0 else tail
            else:
                tail = src[m.end():]
                nxt = next_class.search(tail)
                tail = tail[:nxt.start()] if nxt else tail
                header, scope = rest_of_line + tail, ""
            decls[f"{pkg}.{cls}"] = (header, scope, path)
    return decls


def _literal_name(scope: str):
    """A simple literal `val name = "..."` declaration (no interpolation)."""
    m = re.search(r"val\s+name\b[^=]*=\s*[\"']([^\"'$]+)[\"']", scope)
    return m.group(1) if m else None


def _header_name(header: str):
    m = re.search(r'name\s*=\s*["\']([^"\']+)["\']', header)
    if m:
        return m.group(1)
    m = re.search(r'["\']([^"\']+)["\']', header)
    return m.group(1) if m else None


def resolve_gm_names():
    """FQN -> logical GM name for every class referenced by the XML snapshot."""
    decls = _class_decls()
    tree = ET.parse(XML_PATH)
    root = tree.getroot()
    fqns = set()
    for tc in root.iter("testcase"):
        m = re.search(r"\[(\d+)\] (\S+)@[0-9a-f]+", tc.get("name"))
        assert m, f"unparsable testcase name: {tc.get('name')}"
        fqns.add(m.group(2))

    names = {}
    unresolved = []
    for fqn in sorted(fqns):
        simple = fqn.split(".")[-1]
        if simple in CURATED_NAMES:
            name, pin = CURATED_NAMES[simple]
            candidates = [path for (h, s, path) in decls.values() if False]
            hit = any(pin in s or pin in h for (h, s, path) in [
                d for f, d in decls.items() if f.endswith("." + simple)])
            assert hit, f"curated name pin not found for {simple}: {pin}"
            names[fqn] = name
            continue
        found = None
        for (header, scope, path) in [d for f, d in decls.items() if f == fqn]:
            name = _literal_name(scope)
            if name is None:
                name = _header_name(header)
            if name is not None:
                found = name
                break
        if found is not None:
            names[fqn] = found
        else:
            unresolved.append(fqn)
    return names, unresolved


# ---------------------------------------------------------------------------
# XML row extraction
# ---------------------------------------------------------------------------

REFERENCE_MISSING_RE = re.compile(r"Reference PNG not found at /reference/(\S+)\.png")


def extract_rows():
    """[(gm, refusalCode, rootCauseBucket, item, ordinal)] in XML order."""
    names, unresolved = resolve_gm_names()
    if unresolved:
        print("UNRESOLVED GM names:", sorted(unresolved))
        sys.exit(2)

    tree = ET.parse(XML_PATH)
    root = tree.getroot()
    rows = []
    ordinal = 0
    for tc in root.iter("testcase"):
        failure = tc.find("failure")
        if failure is None:
            continue
        ordinal += 1
        m = re.search(r"\[(\d+)\] (\S+)@[0-9a-f]+", tc.get("name"))
        fqn = m.group(2)
        gm = names[fqn]
        ftype = failure.get("type") or ""
        message = failure.get("message") or ""

        if ftype == "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceTerminalException":
            cm = re.search(r"GPUPreparedSurfaceTerminalException:\s+(\S+):", message)
            assert cm, f"unparsable terminal refusal message: {message[:120]}"
            code = cm.group(1)
            rows.append((gm, code, root_cause_bucket(code), str(ITEM_BY_CODE.get(code, "none")),
                         ordinal))
        elif "Reference PNG not found" in message:
            rm = REFERENCE_MISSING_RE.search(message)
            code = "reference-missing"
            bucket = "missing reference artifact (chantier B)"
            rows.append((gm, code, bucket, "none", ordinal))
        elif ftype == "java.lang.IllegalArgumentException":
            code = "size-mismatch"
            bucket = "reference size mismatch"
            rows.append((gm, code, bucket, "none", ordinal))
        elif ftype == "org.opentest4j.AssertionFailedError":
            code = "below-threshold-similarity"
            bucket = "below-threshold similarity (documented divergence)"
            rows.append((gm, code, bucket, "none", ordinal))
        else:
            raise AssertionError(f"unclassified failure: {fqn} {ftype} {message[:120]}")
    return rows


def verify(rows, print_counts=False):
    errors = []
    by_kind = {}
    for gm, code, bucket, item, ordinal in rows:
        by_kind[code] = by_kind.get(code, 0) + 1
    terminal = by_kind.get("unsupported:terminal", 0)
    terminal = sum(1 for r in rows if r[1] != "reference-missing" and
                   r[1] != "size-mismatch" and r[1] != "below-threshold-similarity")
    if len(rows) != 498:
        errors.append(f"total failure rows {len(rows)} != 498")
    if terminal != 489:
        errors.append(f"terminal refusals {terminal} != 489")
    if by_kind.get("reference-missing") != 7:
        errors.append(f"missing-reference rows {by_kind.get('reference-missing')} != 7")
    if by_kind.get("size-mismatch") != 1:
        errors.append(f"size-mismatch rows {by_kind.get('size-mismatch')} != 1")
    if by_kind.get("below-threshold-similarity") != 1:
        errors.append(f"below-threshold rows {by_kind.get('below-threshold-similarity')} != 1")
    if print_counts:
        distinct = sorted({r[1] for r in rows})
        print(f"refusals-inventory: total={len(rows)} (terminal=489, missing-reference=7, "
              f"size-mismatch=1, below-threshold=1); distinct codes={len(distinct)}")
    return errors


def write_csv(rows):
    with open(REFUSALS_CSV, "w", newline="", encoding="utf-8") as fh:
        writer = csv.writer(fh)
        writer.writerow(["gm", "refusalCode", "rootCauseBucket", "item"])
        for gm, code, bucket, item, ordinal in rows:
            writer.writerow([gm, code, bucket, item])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify counts only, write nothing")
    parser.add_argument("--print", action="store_true", help="print counts after writing")
    args = parser.parse_args()

    rows = extract_rows()
    errors = verify(rows, print_counts=args.print or args.check)
    if errors:
        for err in errors:
            print(f"VERIFY FAIL: {err}")
        sys.exit(1)
    if args.check:
        print("refusals-inventory: 498 rows verified (489 terminal / 7 missing / 1 size / 1 threshold)")
        return
    write_csv(rows)
    print(f"wrote {REFUSALS_CSV} ({len(rows)} rows)")
    if args.print:
        for gm, code, bucket, item, ordinal in rows[:5]:
            print(f"  {gm} | {code} | {bucket} | {item}")


if __name__ == "__main__":
    main()
