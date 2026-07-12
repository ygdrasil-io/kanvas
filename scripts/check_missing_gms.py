#!/usr/bin/env python3
"""Check which reference PNGs have no corresponding SkiaGm Kotlin implementation.

Extracts GM names from Kotlin files using pattern matching, then compares
against reference PNG basenames.

Some reference PNGs come from Skia's C++ native test infrastructure with
parameterized suffixes (e.g., colrv1_clipbox_CLIO_200.00.png). These are
orphaned from the upstream import and don't correspond to Kanvas GMs.

Usage: python3 scripts/check_missing_gms.py [--cpp-gm-dir PATH]
"""

import argparse
import re
import sys
from pathlib import Path

from extract_skia_gm_names import extract_gm_names as extract_cpp_gm_names

REPO = Path(__file__).resolve().parent.parent
REF_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "resources" / "reference"
GM_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "kotlin" / "org" / "graphiks" / "kanvas" / "skia" / "gm"


def extract_subclass_names(text, parent_class):
    names = set()
    for m in re.finditer(
        r'(?:class|object)\s+\w+\s*(?:\([^)]*\))?\s*:\s*'
        + re.escape(parent_class)
        + r'\s*\(\s*"([^"]+)"',
        text,
    ):
        names.add(m.group(1))
    return names


def extract_kotlin_gm_names():
    names = set()

    for kt_file in sorted(GM_DIR.rglob("*Gm.kt")):
        text = kt_file.read_text()

        # override val name = "literal"
        for m in re.finditer(r'override\s+val\s+name\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))

        # override val name[: String] = if (c) "a" else "b"
        for m in re.finditer(
            r'override\s+val\s+name(?:\s*:\s*String)?\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
            text,
        ):
            names.add(m.group(1))
            names.add(m.group(2))

        # get() = if (c) "a" else "b"
        for m in re.finditer(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
            text,
        ):
            names.add(m.group(1))
            names.add(m.group(2))

        # get() = "prefix$var" -> look for subclass constructors
        m = re.search(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]*)\$',
            text,
        )
        if m:
            class_m = re.search(
                r'(?:class|object)\s+(\w+)\s*(?:\([^)]*\))?\s*[:\{]', text
            )
            if class_m:
                names.update(extract_subclass_names(text, class_m.group(1)))

        # constructor passthrough: override val name: String,
        for m in re.finditer(
            r'class\s+(\w+)\s*\([^)]*override\s+val\s+name\s*:\s*String',
            text,
        ):
            names.update(extract_subclass_names(text, m.group(1)))

        # gmName = "literal" (companion/factory)
        for m in re.finditer(r'gmName\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))

        # variantName = "literal"
        for m in re.finditer(r'variantName\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))

        # return "literal" in getters
        for m in re.finditer(r'return\s+"([^"]+)"', text):
            names.add(m.group(1))

        # get() = "literal" (simple string getters)
        for m in re.finditer(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]+)"',
            text,
        ):
            names.add(m.group(1))

        # get() {"..."; return "literal" } blocks
        for m in re.finditer(r'return "([^"]+)"', text):
            names.add(m.group(1))

        # name = "literal" (non-override, companion context)
        for m in re.finditer(r'name\s*=\s*"([^"]+)"', text):
            start = m.start()
            prefix = text[max(0, start - 40):start]
            if "override val" not in prefix and "kanvas.skia.gm" not in prefix:
                names.add(m.group(1))

        # subclass constructor calls: class X : Parent("literal")
        for m in re.finditer(
            r':\s*\w+\s*\(\s*"([a-z][a-z0-9_]+)"', text,
        ):
            names.add(m.group(1))

    return {n for n in names if is_gm_name(n)}


# Filter out strings that are clearly not GM names
_KNOWN_NON_NAMES = {
    "png", "jpg", "jpeg", "gif", "webp", "bmp",
    "opaque", "gray", "normal",
    "abc", "abcd", "abcde", "abcdef", "abcdefg",
    "hambur", "hamburger",
}


def is_gm_name(s):
    return s not in _KNOWN_NON_NAMES


def is_parameterized_variant(name, gm_names):
    if name in gm_names:
        return False
    parts = name.split("_")
    for i in range(len(parts) - 1, 0, -1):
        candidate = "_".join(parts[:i])
        if candidate in gm_names:
            return candidate
    return None


def normalize_name(value: str) -> str:
    return "".join(ch for ch in value.casefold() if ch.isalnum())


def classify_reference(gm_name: str, references: set[str], cpp_names: set[str] | None) -> dict[str, object]:
    if gm_name in references:
        return {
            "kind": "direct",
            "gm_name": gm_name,
            "reference": gm_name,
            "references": [gm_name],
        }

    normalized_gm_name = normalize_name(gm_name)
    normalized_matches = sorted(
        reference for reference in references if normalize_name(reference) == normalized_gm_name
    )
    if len(normalized_matches) == 1:
        return {
            "kind": "normalized-alias",
            "gm_name": gm_name,
            "reference": normalized_matches[0],
            "references": normalized_matches,
        }

    if cpp_names is not None and gm_name in cpp_names:
        variant_matches = sorted(
            reference
            for reference in references
            if normalize_name(reference).startswith(normalized_gm_name)
        )
        if variant_matches:
            return {
                "kind": "variant-family",
                "gm_name": gm_name,
                "reference": None,
                "references": variant_matches,
            }

    return {
        "kind": "missing",
        "gm_name": gm_name,
        "reference": None,
        "references": [],
    }


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cpp-gm-dir",
        type=Path,
        help="path to the Skia C++ gm/ directory for source-aware diagnostic evidence",
    )
    args = parser.parse_args()
    if args.cpp_gm_dir is not None and not args.cpp_gm_dir.is_dir():
        parser.error(f"--cpp-gm-dir is not a directory: {args.cpp_gm_dir}")
    return args


def main():
    args = parse_args()

    if not REF_DIR.is_dir():
        print(f"Error: {REF_DIR} not found", file=sys.stderr)
        sys.exit(1)

    ref_pngs = sorted(REF_DIR.glob("*.png"))
    gm_names = extract_kotlin_gm_names()
    cpp_names = None
    if args.cpp_gm_dir is not None:
        cpp_names = extract_cpp_gm_names(Path(args.cpp_gm_dir))

    ref_by_base = {}
    manual_names = set()
    for p in ref_pngs:
        name = p.stem
        if name.endswith("_manual"):
            manual_names.add(name)
        else:
            ref_by_base.setdefault(name, []).append(p)

    classifications = {}
    matched_references = set()
    normalized_aliases = []
    variant_families = []
    actionable_missing = []
    manual_orphans = []

    reference_names = set(ref_by_base)
    for gm_name in sorted(gm_names):
        result = classify_reference(gm_name, reference_names, cpp_names)
        classifications[gm_name] = result
        matched_references.update(result["references"])
        if result["kind"] == "normalized-alias":
            normalized_aliases.append(result)
        elif result["kind"] == "variant-family":
            variant_families.append(result)
        elif result["kind"] == "missing":
            actionable_missing.append(result)

    orphan_names = sorted(reference_names - matched_references)

    for name in sorted(manual_names):
        base = name.replace("_manual", "")
        if base in matched_references or any(
            result["gm_name"] == base or base in result["references"]
            for result in classifications.values()
        ):
            continue
        else:
            manual_orphans.append(name)

    print(f"Reference PNGs:     {len(ref_pngs)}")
    print(f"GM names extracted: {len(gm_names)}")

    direct_count = sum(1 for result in classifications.values() if result["kind"] == "direct")
    normalized_alias_count = len(normalized_aliases)
    variant_reference_count = sum(len(result["references"]) for result in variant_families)
    matched = direct_count + normalized_alias_count + variant_reference_count
    print(
        f"Matched: {matched} "
        f"({direct_count} direct + "
        f"{normalized_alias_count} normalized-alias + "
        f"{variant_reference_count} variant-family)"
    )

    if cpp_names is None:
        print("\nsource-evidence: unavailable")
    else:
        print(f"\nsource-evidence: cpp-gm-dir={args.cpp_gm_dir}")

    print("\n--- Normalized aliases ---")
    print(f"count: {len(normalized_aliases)}")
    for result in normalized_aliases:
        print(f"  {result['gm_name']}.png  <- alias {result['reference']}.png")

    print("\n--- Variant families from CPP source evidence ---")
    print(f"count: {len(variant_families)}")
    for result in variant_families:
        references = ", ".join(f"{reference}.png" for reference in result["references"])
        print(f"  {result['gm_name']}.png  <- variants {references}")

    if orphan_names:
        print(f"\n=== REFERENCE PNGs WITHOUT Kotlin GM ({len(orphan_names)}) ===\n")
        for name in orphan_names:
            print(f"  {name}.png")

    if manual_orphans:
        print(f"\n=== _manual variants w/o GM ({len(manual_orphans)}) ===\n")
        for name in manual_orphans:
            base = name.replace("_manual", "")
            print(f"  {name}.png (base '{base}' also missing)")

    print("\n=== ACTIONABLE missing references ===")
    print(f"count: {len(actionable_missing)}\n")
    for result in actionable_missing:
        print(f"  {result['gm_name']}.png")


if __name__ == "__main__":
    main()
