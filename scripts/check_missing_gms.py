#!/usr/bin/env python3
"""Check which reference PNGs have no corresponding SkiaGm Kotlin implementation.

Extracts GM names from Kotlin files using pattern matching, then compares
against reference PNG basenames.

Some reference PNGs come from Skia's C++ native test infrastructure with
parameterized suffixes (e.g., colrv1_clipbox_CLIO_200.00.png). These are
orphaned from the upstream import and don't correspond to Kanvas GMs.

Usage: python3 scripts/check_missing_gms.py
"""

import re
import sys
from pathlib import Path

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


def extract_gm_names():
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


def main():
    if not REF_DIR.is_dir():
        print(f"Error: {REF_DIR} not found", file=sys.stderr)
        sys.exit(1)

    ref_pngs = sorted(REF_DIR.glob("*.png"))
    gm_names = extract_gm_names()

    ref_by_base = {}
    manual_names = set()
    for p in ref_pngs:
        name = p.stem
        if name.endswith("_manual"):
            manual_names.add(name)
        else:
            ref_by_base.setdefault(name, []).append(p)

    found_names = set()
    variant_names = {}
    orphan_names = []
    manual_orphans = []

    for name in ref_by_base:
        if name in gm_names:
            found_names.add(name)
        else:
            parent = is_parameterized_variant(name, gm_names)
            if parent:
                variant_names.setdefault(parent, []).append(name)
            else:
                orphan_names.append(name)

    for name in sorted(manual_names):
        base = name.replace("_manual", "")
        if base in gm_names:
            found_names.add(base)
        else:
            manual_orphans.append(name)

    print(f"Reference PNGs:     {len(ref_pngs)}")
    print(f"GM names extracted: {len(gm_names)}")

    matched = len(found_names) + sum(len(v) for v in variant_names.values())
    print(
        f"Matched: {matched} "
        f"({len(found_names)} direct + "
        f"{sum(len(v) for v in variant_names.values())} parameterized)"
    )

    if variant_names:
        print(f"\n--- Parameterized variants of existing GMs ---")
        for parent in sorted(variant_names):
            for v in sorted(variant_names[parent]):
                print(f"  {v}.png  <- from {parent}")

    if orphan_names:
        print(f"\n=== REFERENCE PNGs WITHOUT Kotlin GM ({len(orphan_names)}) ===\n")
        for name in orphan_names:
            print(f"  {name}.png")

    if manual_orphans:
        print(f"\n=== _manual variants w/o GM ({len(manual_orphans)}) ===\n")
        for name in manual_orphans:
            base = name.replace("_manual", "")
            print(f"  {name}.png (base '{base}' also missing)")

    extra = sorted(
        set(gm_names) - set(ref_by_base.keys()) - manual_names
    )
    extra = [
        n for n in extra
        if not any(n in vlist for vlist in variant_names.values())
        and not any(n in str(v) for v in variant_names.values())
        and "$" not in n and "{" not in n
    ]
    if extra:
        print(f"\n=== GM names WITHOUT reference PNG ({len(extra)}) ===\n")
        for name in extra:
            print(f"  {name}.png")


if __name__ == "__main__":
    main()
