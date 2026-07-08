#!/usr/bin/env python3
"""Compare Skia C++ GMs against Kanvas Kotlin GMs to find what's left to port.

Usage: python3 scripts/compare_skia_vs_kanvas_gms.py
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REF_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "resources" / "reference"
GM_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "kotlin" / "org" / "graphiks" / "kanvas" / "skia" / "gm"
SKIA_GM_DIR = Path("/Users/chaos/workspace/kanvas-forge/skia-main/gm")


# ---------------------------------------------------------------------------
# 1. Extract Kanvas GM names (reuse logic from check_missing_gms.py)
# ---------------------------------------------------------------------------

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


def extract_kanvas_gm_names():
    names = set()
    for kt_file in sorted(GM_DIR.rglob("*Gm.kt")):
        text = kt_file.read_text()
        for m in re.finditer(r'override\s+val\s+name\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))
        for m in re.finditer(
            r'override\s+val\s+name(?:\s*:\s*String)?\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
            text,
        ):
            names.add(m.group(1)); names.add(m.group(2))
        for m in re.finditer(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*if\s*\([^)]*\)\s*"([^"]+)"\s*else\s*"([^"]+)"',
            text,
        ):
            names.add(m.group(1)); names.add(m.group(2))
        m = re.search(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]*)\$', text
        )
        if m:
            class_m = re.search(r'(?:class|object)\s+(\w+)\s*(?:\([^)]*\))?\s*[:\{]', text)
            if class_m:
                names.update(extract_subclass_names(text, class_m.group(1)))
        for m in re.finditer(
            r'class\s+(\w+)\s*\([^)]*override\s+val\s+name\s*:\s*String', text,
        ):
            names.update(extract_subclass_names(text, m.group(1)))
        for m in re.finditer(r'gmName\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))
        for m in re.finditer(r'variantName\s*=\s*"([^"]+)"', text):
            names.add(m.group(1))
        for m in re.finditer(r'return\s+"([^"]+)"', text):
            names.add(m.group(1))
        for m in re.finditer(
            r'override\s+val\s+name\s*:\s*String\s+get\s*\(\s*\)\s*=\s*"([^"]+)"', text,
        ):
            names.add(m.group(1))
        for m in re.finditer(r'return "([^"]+)"', text):
            names.add(m.group(1))
        for m in re.finditer(r'name\s*=\s*"([^"]+)"', text):
            start = m.start()
            prefix = text[max(0, start - 40):start]
            if "override val" not in prefix and "kanvas.skia.gm" not in prefix:
                names.add(m.group(1))
        for m in re.finditer(r':\s*\w+\s*\(\s*"([a-z][a-z0-9_]+)"', text):
            names.add(m.group(1))
    return names


# ---------------------------------------------------------------------------
# 2. Extract Skia GM names from C++ source
# ---------------------------------------------------------------------------

def extract_skia_gm_names():
    """Extract GM names from C++ source using the extract_skia_gm_names.py script."""
    import subprocess
    script = REPO / "scripts" / "extract_skia_gm_names.py"
    result = subprocess.run(
        [sys.executable, str(script), "--names"],
        capture_output=True, text=True, timeout=60,
    )
    names = set()
    for line in result.stdout.strip().split("\n"):
        line = line.strip()
        if line:
            names.add(line)
    return names


# ---------------------------------------------------------------------------
# 3. Main comparison
# ---------------------------------------------------------------------------

def normalize(name):
    """Normalize a GM name for comparison (lowercase, strip trailing _, etc.)."""
    n = name.lower().strip()
    n = n.rstrip("_")  # Some Skia names have trailing _ (coloremoji_, etc.)
    return n


def main():
    print("Extracting Kanvas GM names...", file=sys.stderr)
    kanvas_names = extract_kanvas_gm_names()

    print("Extracting Skia GM names from C++ sources...", file=sys.stderr)
    skia_names = extract_skia_gm_names()

    # Get reference PNG base names
    ref_names = set()
    if REF_DIR.is_dir():
        for p in REF_DIR.glob("*.png"):
            name = p.stem
            if not name.endswith("_manual"):
                ref_names.add(name)

    # Normalize for comparison
    kanvas_norm = {normalize(n) for n in kanvas_names}
    skia_norm = {normalize(n) for n in skia_names}
    ref_norm = {normalize(n) for n in ref_names}

    # --- Not yet ported: Skia names missing from Kanvas ---
    not_ported = sorted(skia_norm - kanvas_norm)

    # --- Ported but no reference PNG yet ---
    ported_no_ref = sorted(kanvas_norm - ref_norm)

    # --- In reference PNGs but not in Kanvas ---
    ref_no_kanvas = sorted(ref_norm - kanvas_norm)

    # --- In reference PNGs but not in Skia (orphaned from upstream) ---
    ref_no_skia = sorted(ref_norm - skia_norm)

    print(f"\n{'='*60}")
    print(f"  Kanvas GM names:      {len(kanvas_names)}")
    print(f"  Skia C++ GM names:    {len(skia_names)}")
    print(f"  Reference PNGs (base): {len(ref_names)}")
    print(f"{'='*60}")

    # Filter out noisy names
    SKIP_WORDS = {"aaa", "gm", "gpu", "hdr", "jpg", "png", "webp", "gif",
                  "rgb", "bgr", "rgba", "bgra", "gray", "etc", "ico", "bmp"}
    SKIP_PREFIXES = {"notosans", "hellobazel", "typeface_fontations", "images/",
                     "animcodecplayerexif"}

    def is_noise(name):
        n = name.lower().strip("_")
        if not n or n in SKIP_WORDS:
            return True
        if any(n.startswith(p) for p in SKIP_PREFIXES):
            return True
        if len(n) < 3:
            return True
        return False

    not_ported_filtered = sorted(
        n for n in skia_norm - kanvas_norm
        if not is_noise(n)
    )

    print(f"\n=== SKIA GMs NOT YET PORTED TO KANVAS ({len(not_ported_filtered)}) ===\n")
    for name in not_ported_filtered:
        has_ref = "  [ref]" if name in ref_norm else ""
        print(f"  {name} {has_ref}")

    # Filter template/false-positive names from ported_no_ref
    ported_real = sorted(
        n for n in ported_no_ref
        if "$" not in n and "{" not in n
        and not is_noise(n)
    )
    if ported_real:
        print(f"\n=== Ported GMs WITHOUT reference PNG ({len(ported_real)}) ===\n")
        for name in ported_real:
            print(f"  {name}")


if __name__ == "__main__":
    main()
