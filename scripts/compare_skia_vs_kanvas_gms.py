#!/usr/bin/env python3
"""Compare Skia C++ GMs against Kanvas Kotlin GMs to find what's left to port.

Usage:
  python3 scripts/compare_skia_vs_kanvas_gms.py [--cpp-gm-dir PATH]

Resolution order for the Skia C++ gm/ source directory:
  1. --cpp-gm-dir PATH
  2. KANVAS_SKIA_GM_DIR
  3. extractor defaults from scripts/extract_skia_gm_names.py

If none of those resolve to a directory, the script exits with an error.
"""

import argparse
import os
import sys
from pathlib import Path

from extract_kanvas_gm_names import extract_kanvas_gm_names as extract_shared_kanvas_gm_names
from extract_skia_gm_names import (
    extract_gm_names as extract_cpp_gm_names,
    resolve_default_gm_dir,
)

REPO = Path(__file__).resolve().parent.parent
REF_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "resources" / "reference"
GM_DIR = REPO / "integration-tests" / "skia" / "src" / "test" / "kotlin" / "org" / "graphiks" / "kanvas" / "skia" / "gm"


def extract_kanvas_gm_names():
    return extract_shared_kanvas_gm_names(GM_DIR)


# ---------------------------------------------------------------------------
# 2. Extract Skia GM names from C++ source
# ---------------------------------------------------------------------------

CPP_GM_DIR_ENV = "KANVAS_SKIA_GM_DIR"


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cpp-gm-dir",
        type=Path,
        help=(
            "path to the Skia C++ gm/ directory; if omitted, the script uses "
            f"${CPP_GM_DIR_ENV} or the extractor defaults when available"
        ),
    )
    args = parser.parse_args()
    if args.cpp_gm_dir is not None and not args.cpp_gm_dir.is_dir():
        parser.error(f"--cpp-gm-dir is not a directory: {args.cpp_gm_dir}")
    return args


def resolve_cpp_gm_dir(cpp_gm_dir: Path | None, env: dict[str, str] | None = None) -> Path:
    if cpp_gm_dir is not None:
        return cpp_gm_dir

    if env is None:
        env = os.environ
    env_value = env.get(CPP_GM_DIR_ENV)
    if env_value:
        env_path = Path(env_value)
        if not env_path.is_dir():
            raise ValueError(f"{CPP_GM_DIR_ENV} is not a directory: {env_path}")
        return env_path

    default_gm_dir = resolve_default_gm_dir()
    if default_gm_dir is not None:
        return default_gm_dir

    raise ValueError(
        f"provide --cpp-gm-dir or {CPP_GM_DIR_ENV}, or make an extractor default available"
    )


def extract_skia_gm_names(cpp_gm_dir: Path):
    """Extract authoritative GM names from a caller-provided C++ gm directory."""
    return {
        name for name in extract_cpp_gm_names(cpp_gm_dir)
        if not name.startswith("<")
    }


# ---------------------------------------------------------------------------
# 3. Main comparison
# ---------------------------------------------------------------------------

def normalize(name):
    """Normalize a GM name for comparison (lowercase, strip trailing _, etc.)."""
    n = name.lower().strip()
    n = n.rstrip("_")  # Some Skia names have trailing _ (coloremoji_, etc.)
    return n


def main():
    args = parse_args()
    try:
        cpp_gm_dir = resolve_cpp_gm_dir(args.cpp_gm_dir)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc

    print("Extracting Kanvas GM names...", file=sys.stderr)
    kanvas_names = extract_kanvas_gm_names()

    print("Extracting Skia GM names from C++ sources...", file=sys.stderr)
    skia_names = extract_skia_gm_names(cpp_gm_dir)

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
