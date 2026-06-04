#!/usr/bin/env python3
"""Build and run the FOR-358 non-arc Rec.2020 F16 Skia reference source."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
LINEAR_ID = "FOR-358"
SCENE_ID = "f16-real-additional-non-arc-row-for358"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
REFERENCE_JSON = ARTIFACT_DIR / "skia-reference-samples.json"
REFERENCE_PNG = ARTIFACT_DIR / "skia-reference.png"
PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
SOURCE = PROJECT_ROOT / "tools/skia-reference/non_arc_rec2020_f16_for358_reference.cpp"

DEFAULT_SKIA_ROOT = Path("/Users/chaos/workspace/kanvas-forge/skia-main")
SKIA_ROOT = Path(os.environ.get("KANVAS_FOR358_UPSTREAM_SKIA_ROOT", DEFAULT_SKIA_ROOT))
SKIA_OUT = Path(os.environ.get("KANVAS_FOR358_UPSTREAM_SKIA_OUT", SKIA_ROOT / "out/Release"))
CXX = os.environ.get("CXX", "/usr/bin/clang++")

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-capture-real-additional-non-arc-f16-comparable-row-ticket"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-357-additional-non-arc-comparable-row-reference-gap",
]


class BuildMissing(RuntimeError):
    """Raised when the local upstream Skia checkout cannot build this source."""


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def run(command: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=str(cwd) if cwd else None,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def git_value(args: list[str], cwd: Path) -> str | None:
    result = run(["git", *args], cwd=cwd)
    if result.returncode != 0:
        return None
    return result.stdout.strip()


def require_file(path: Path, label: str) -> None:
    if not path.is_file():
        raise BuildMissing(f"missing {label}: {path}")


def compile_command(binary: Path) -> list[str]:
    return [
        CXX,
        "-std=c++17",
        f"-I{SKIA_ROOT}",
        f"-I{SKIA_ROOT / 'include'}",
        f"-I{SKIA_ROOT / 'include/core'}",
        f"-I{SKIA_ROOT / 'include/encode'}",
        str(SOURCE),
        str(SKIA_OUT / "libskia.a"),
        str(SKIA_OUT / "libpng.a"),
        str(SKIA_OUT / "libzlib.a"),
        "-framework",
        "CoreFoundation",
        "-framework",
        "CoreGraphics",
        "-framework",
        "CoreText",
        "-framework",
        "Foundation",
        "-framework",
        "ImageIO",
        "-framework",
        "Metal",
        "-framework",
        "QuartzCore",
        "-o",
        str(binary),
    ]


def ensure_inputs() -> None:
    require_file(SOURCE, "FOR-358 source")
    require_file(SKIA_ROOT / "include/core/SkCanvas.h", "upstream SkCanvas header")
    require_file(SKIA_ROOT / "include/core/SkColorSpace.h", "upstream SkColorSpace header")
    require_file(SKIA_ROOT / "include/encode/SkPngEncoder.h", "upstream SkPngEncoder header")
    require_file(SKIA_OUT / "libskia.a", "upstream libskia.a")
    require_file(SKIA_OUT / "libpng.a", "upstream libpng.a")
    require_file(SKIA_OUT / "libzlib.a", "upstream libzlib.a")


def write_provenance(command: list[str], build_command: list[str], execute_command: list[str]) -> None:
    reference = json.loads(REFERENCE_JSON.read_text(encoding="utf-8"))
    provenance: dict[str, Any] = {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "sceneId": SCENE_ID,
        "sourceType": "isolated-skia-non-arc-rec2020-f16-for358-src-over-rect",
        "sourceImplementation": rel(SOURCE),
        "sourceSha256": sha256(SOURCE),
        "dimensions": reference["dimensions"],
        "command": " ".join(command),
        "commandArgv": command,
        "buildCommand": build_command,
        "executeCommand": execute_command,
        "headless": True,
        "upstreamSkiaRoot": str(SKIA_ROOT),
        "upstreamSkiaOut": str(SKIA_OUT),
        "upstreamSkiaGitRevision": git_value(["rev-parse", "HEAD"], SKIA_ROOT),
        "upstreamSkiaGitStatusShort": git_value(["status", "--short"], SKIA_ROOT) or "",
        "referenceJsonPath": rel(REFERENCE_JSON),
        "referenceJsonSha256": sha256(REFERENCE_JSON),
        "referencePngPath": rel(REFERENCE_PNG),
        "referencePngSha256": sha256(REFERENCE_PNG),
        "sourceTypeProof": {
            "compiledRepoOwnedSource": True,
            "compiledSourcePath": rel(SOURCE),
            "linkedAgainstUpstreamSkiaLib": str(SKIA_OUT / "libskia.a"),
            "executedBinary": True,
            "isolatedNonArcOutput": True,
            "distinctFromFor345": True,
        },
        "nonArc": True,
        "excludedScene": "circular_arcs_stroke_butt",
        "fullGmCrop": False,
        "selectedCellSubstitutionAccepted": False,
        "cpuKanvasOutputAcceptedAsSkia": False,
    }
    PROVENANCE.write_text(json.dumps(provenance, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def main() -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    REFERENCE_JSON.unlink(missing_ok=True)
    REFERENCE_PNG.unlink(missing_ok=True)
    PROVENANCE.unlink(missing_ok=True)

    try:
        ensure_inputs()
        with tempfile.TemporaryDirectory(prefix="kanvas-for358-skia-") as tmp:
            binary = Path(tmp) / "f16_real_additional_non_arc_row"
            build_command = compile_command(binary)
            build = run(build_command)
            if build.returncode != 0:
                raise BuildMissing("FOR-358 source compile failed against upstream Skia:\n" + build.stdout + build.stderr)

            execute_command = [str(binary), str(REFERENCE_JSON), str(REFERENCE_PNG)]
            execution = run(execute_command)
            if execution.returncode != 0:
                raise BuildMissing(
                    "FOR-358 source execution failed against upstream Skia:\n"
                    + execution.stdout
                    + execution.stderr
                )
            if not REFERENCE_JSON.is_file() or not REFERENCE_PNG.is_file():
                raise BuildMissing("execution completed but did not create both Skia reference outputs")

            command = ["rtk", "python3", rel(Path(__file__).resolve())]
            write_provenance(command, build_command, execute_command)
    except BuildMissing as exc:
        REFERENCE_JSON.unlink(missing_ok=True)
        REFERENCE_PNG.unlink(missing_ok=True)
        PROVENANCE.unlink(missing_ok=True)
        print(f"F16_REAL_ADDITIONAL_NON_ARC_ROW_CAPTURE_PARTIAL: {exc}", file=sys.stderr)
        raise SystemExit(2)

    print("F16_REAL_ADDITIONAL_NON_ARC_ROW_SKIA_REFERENCE_CAPTURED")


if __name__ == "__main__":
    main()
