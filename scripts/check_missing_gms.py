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

from extract_kanvas_gm_names import extract_kanvas_gm_names
from extract_skia_gm_names import extract_gm_names as extract_cpp_gm_names

REPO = Path(__file__).resolve().parent.parent
REF_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "resources" / "reference"
GM_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "kotlin" / "org" / "graphiks" / "kanvas" / "skia" / "gm"

def extract_kotlin_gm_names():
    return {name for name in extract_kanvas_gm_names(GM_DIR) if is_gm_name(name)}


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


def extract_family_reference_prefix(reference: str, normalized_family: str) -> str | None:
    normalized_index = 0
    for index, char in enumerate(reference.casefold()):
        if not char.isalnum():
            continue
        if normalized_index >= len(normalized_family) or char != normalized_family[normalized_index]:
            return None
        normalized_index += 1
        if normalized_index == len(normalized_family):
            if index + 1 >= len(reference) or reference[index + 1] not in "-_":
                return None
            return reference[:index + 1]
    return None


def is_authoritative_variant_family(gm_name: str, references: list[str]) -> bool:
    normalized_family = normalize_name(gm_name)
    if len(references) < 2:
        return False

    family_prefixes = [
        extract_family_reference_prefix(reference, normalized_family)
        for reference in references
    ]
    if any(prefix is None for prefix in family_prefixes):
        return False

    token_counts = [
        len([token for token in re.split(r"[-_]+", prefix) if token])
        for prefix in family_prefixes
    ]
    return max(token_counts, default=0) >= 2 or len(normalized_family) >= 10


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
            if extract_family_reference_prefix(reference, normalized_gm_name) is not None
        )
        if variant_matches and is_authoritative_variant_family(gm_name, variant_matches):
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


def collect_reference_matches(reference_names, gm_names):
    found_names = set()
    variant_names = {}
    orphan_names = []

    for name in reference_names:
        if name in gm_names:
            found_names.add(name)
        else:
            parent = is_parameterized_variant(name, gm_names)
            if parent:
                variant_names.setdefault(parent, []).append(name)
            else:
                orphan_names.append(name)

    return found_names, variant_names, orphan_names


def collect_manual_orphans(manual_names, gm_names, found_names):
    manual_orphans = []
    for name in sorted(manual_names):
        base = name.replace("_manual", "")
        if base in gm_names:
            found_names.add(base)
        else:
            manual_orphans.append(name)
    return manual_orphans


def collect_gm_names_without_reference(gm_names, reference_names, manual_names, variant_names):
    extra = sorted(set(gm_names) - set(reference_names) - manual_names)
    return [
        name for name in extra
        if not any(name in variants for variants in variant_names.values())
        and not any(name in str(variants) for variants in variant_names.values())
        and "$" not in name
        and "{" not in name
    ]


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

    reference_names = set(ref_by_base)
    found_names, variant_names, orphan_names = collect_reference_matches(
        reference_names,
        gm_names,
    )
    manual_orphans = collect_manual_orphans(manual_names, gm_names, found_names)
    extra_gm_names = collect_gm_names_without_reference(
        gm_names,
        reference_names,
        manual_names,
        variant_names,
    )

    normalized_aliases = []
    variant_families = []
    actionable_missing = extra_gm_names
    if cpp_names is not None:
        actionable_missing = []
        for gm_name in extra_gm_names:
            result = classify_reference(gm_name, reference_names, cpp_names)
            if result["kind"] == "normalized-alias":
                normalized_aliases.append(result)
            elif result["kind"] == "variant-family":
                variant_families.append(result)
            else:
                actionable_missing.append(gm_name)

    print(f"Reference PNGs:     {len(ref_pngs)}")
    print(f"GM names extracted: {len(gm_names)}")

    direct_count = len(found_names)
    parameterized_count = sum(len(variants) for variants in variant_names.values())
    matched = direct_count + parameterized_count
    print(
        f"Matched: {matched} "
        f"({direct_count} direct + "
        f"{parameterized_count} parameterized)"
    )

    if cpp_names is None:
        print("\nsource-evidence: unavailable")
    else:
        print(f"\nsource-evidence: cpp-gm-dir={args.cpp_gm_dir}")

    if variant_names:
        print(f"\n--- Parameterized variants of existing GMs ---")
        for parent in sorted(variant_names):
            for variant_name in sorted(variant_names[parent]):
                print(f"  {variant_name}.png  <- from {parent}")

    if orphan_names:
        print(f"\n=== REFERENCE PNGs WITHOUT Kotlin GM ({len(orphan_names)}) ===\n")
        for name in sorted(orphan_names):
            print(f"  {name}.png")

    if manual_orphans:
        print(f"\n=== _manual variants w/o GM ({len(manual_orphans)}) ===\n")
        for name in manual_orphans:
            base = name.replace("_manual", "")
            print(f"  {name}.png (base '{base}' also missing)")

    if cpp_names is not None:
        print("\n--- Normalized aliases ---")
        print(f"count: {len(normalized_aliases)}")
        for result in normalized_aliases:
            print(f"  {result['gm_name']}.png  <- alias {result['reference']}.png")

        print("\n--- Variant families from CPP source evidence ---")
        print(f"count: {len(variant_families)}")
        for result in variant_families:
            references = ", ".join(f"{reference}.png" for reference in result["references"])
            print(f"  {result['gm_name']}.png  <- variants {references}")

    if actionable_missing:
        print(f"\n=== GM names WITHOUT reference PNG ({len(actionable_missing)}) ===\n")
        for name in actionable_missing:
            print(f"  {name}.png")


if __name__ == "__main__":
    main()
