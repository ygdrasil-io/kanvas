#!/usr/bin/env python3
"""Build and run the FOR-326 selected-cell Skia source for FOR-327."""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
LINEAR_ID = "FOR-327"
SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
OUTPUT = ARTIFACT_DIR / "skia.png"
PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
SOURCE = PROJECT_ROOT / "tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp"

DEFAULT_SKIA_ROOT = Path("/Users/chaos/workspace/kanvas-forge/skia-main")
SKIA_ROOT = Path(os.environ.get("KANVAS_FOR327_UPSTREAM_SKIA_ROOT", DEFAULT_SKIA_ROOT))
SKIA_OUT = Path(os.environ.get("KANVAS_FOR327_UPSTREAM_SKIA_OUT", SKIA_ROOT / "out/Release"))
CXX = os.environ.get("CXX", "/usr/bin/clang++")

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-build-and-run-skia-selected-cell-source-ticket"
)

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
SELECTED_CELL = {
    "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
    "sourceGm": "CircularArcsStrokeButtGM",
    "sourceRowId": "circular-arcs-stroke-butt-webgpu",
    "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
    "boundedHarnessGm": "circular-arcs-stroke-butt-selected-cell-harness-for322",
    "cellCount": 1,
    "quadrant": "bottom-left",
    "fullGmCanvasArcRectLTRB": [140, 520, 180, 560],
    "boundedCanvasArcRectLTRB": [20, 20, 60, 60],
    "rowIndex": 0,
    "columnIndex": 2,
    "startDegrees": 0,
    "sweepDegrees": 90,
    "complementSweepDegrees": -270,
    "useCenter": False,
    "aa": True,
    "style": "kStroke_Style",
    "strokeWidth": 15,
    "strokeCap": "kButt_Cap",
    "includedCaps": ["kButt_Cap"],
    "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
    "includesHairlineStrokeWidth0": False,
    "includesFill": False,
    "includesDash": False,
    "paintAlpha": 100,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}


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
    require_file(SOURCE, "FOR-326 source")
    require_file(SKIA_ROOT / "include/core/SkCanvas.h", "upstream SkCanvas header")
    require_file(SKIA_ROOT / "include/encode/SkPngEncoder.h", "upstream SkPngEncoder header")
    require_file(SKIA_OUT / "libskia.a", "upstream libskia.a")
    require_file(SKIA_OUT / "libpng.a", "upstream libpng.a")
    require_file(SKIA_OUT / "libzlib.a", "upstream libzlib.a")


def write_provenance(
    command: list[str],
    build_command: list[str],
    execute_command: list[str],
) -> None:
    upstream_revision = git_value(["rev-parse", "HEAD"], SKIA_ROOT)
    upstream_status = git_value(["status", "--short"], SKIA_ROOT) or ""
    provenance: dict[str, Any] = {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sceneId": SCENE_ID,
        "sourceType": "isolated-skia-selected-cell-render",
        "fixtureId": SELECTED_CELL["fixtureId"],
        "sourceGm": SELECTED_CELL["sourceGm"],
        "sourceRowId": SELECTED_CELL["sourceRowId"],
        "dimensions": EXPECTED_DIMENSIONS,
        "outputPath": rel(OUTPUT),
        "outputSha256": sha256(OUTPUT),
        "command": " ".join(command),
        "commandArgv": command,
        "buildCommand": build_command,
        "executeCommand": execute_command,
        "headless": True,
        "sourceImplementation": rel(SOURCE),
        "sourceFor326": {
            "linear": "FOR-326",
            "path": rel(SOURCE),
            "sha256": sha256(SOURCE),
        },
        "upstreamSkiaRoot": str(SKIA_ROOT),
        "upstreamSkiaOut": str(SKIA_OUT),
        "upstreamSkiaGitRevision": upstream_revision,
        "upstreamSkiaGitStatusShort": upstream_status,
        "upstreamSkiaSourceVersion": upstream_revision,
        "sourceTypeProof": {
            "compiledRepoOwnedSource": True,
            "compiledSourcePath": rel(SOURCE),
            "linkedAgainstUpstreamSkiaLib": str(SKIA_OUT / "libskia.a"),
            "executedBinary": True,
        },
        "selectedCell": SELECTED_CELL,
        "cellParameters": SELECTED_CELL,
        "fullGmCrop": False,
        "fullGmSubstitutionAccepted": False,
        "cpuKanvasOutputAcceptedAsSkia": False,
        "rejectedSources": {
            "fullGmPng": {"accepted": False},
            "fullGmCrop": {"accepted": False},
            "for322CpuPng": {"accepted": False},
            "fullGmScores": {"accepted": False},
        },
    }
    PROVENANCE.write_text(json.dumps(provenance, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def main() -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT.unlink(missing_ok=True)
    PROVENANCE.unlink(missing_ok=True)

    try:
        ensure_inputs()
        with tempfile.TemporaryDirectory(prefix="kanvas-for327-skia-") as tmp:
            binary = Path(tmp) / "circular_arcs_stroke_butt_selected_cell"
            build_command = compile_command(binary)
            build = run(build_command)
            if build.returncode != 0:
                raise BuildMissing(
                    "FOR-326 source compile failed against upstream Skia:\n"
                    + build.stdout
                    + build.stderr
                )

            execute_command = [str(binary), str(OUTPUT)]
            execution = run(execute_command)
            if execution.returncode != 0:
                OUTPUT.unlink(missing_ok=True)
                raise BuildMissing(
                    "FOR-326 source execution failed against upstream Skia:\n"
                    + execution.stdout
                    + execution.stderr
                )
            if not OUTPUT.is_file():
                raise BuildMissing("execution completed but did not create skia.png")

            command = ["rtk", "python3", rel(Path(__file__).resolve())]
            write_provenance(command, build_command, execute_command)
    except BuildMissing as exc:
        OUTPUT.unlink(missing_ok=True)
        PROVENANCE.unlink(missing_ok=True)
        print(
            "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_BUILD_MISSING: "
            f"{exc}",
            file=sys.stderr,
        )
        raise SystemExit(2)

    print("CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY")


if __name__ == "__main__":
    main()
