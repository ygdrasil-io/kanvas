#!/usr/bin/env python3
"""Validate FOR-267 round cap/join boundary-cell coverage audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "round-cap-join-coverage-equivalence-for267.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/round-cap-join-coverage-equivalence-for267"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For267RoundCapJoinCoverageEquivalenceTest.kt"
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-267-round-cap-join-coverage-equivalence.md"
FALLBACK_REASON = "coverage.stroke-cap-join-visual-parity-below-threshold"
CROP_FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
MISSING_CONDITION = "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells"
EXPECTED_CELLS = {
    "round-left-cap-boundary": "round-cap-boundary",
    "round-join-apex": "round-join-boundary",
    "round-right-cap-boundary": "round-cap-boundary",
    "for266-high-delta-round-bin-overlap": "round-region-overlap-with-butt-cap-boundary",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-267 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_bool(owner: dict[str, Any], field: str, expected: bool) -> None:
    actual = owner.get(field)
    if actual is not expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_number(owner: dict[str, Any], field: str, expected: int | float) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def cell_by_id(probe: dict[str, Any], cell_id: str) -> dict[str, Any]:
    cells = probe.get("cellsInspected")
    if not isinstance(cells, list):
        fail("missing cellsInspected array")
    for cell in cells:
        if isinstance(cell, dict) and cell.get("id") == cell_id:
            return cell
    fail(f"missing cell `{cell_id}`")


def validate_cell(cell: dict[str, Any], expected_classification: str) -> None:
    require_text(cell, "geometricClassification", expected_classification)
    require_text(cell, "regionalClassification", "round-round")
    require_number(cell, "totalPixels", (cell["radius"] * 2 + 1) ** 2)
    if cell.get("matchingPixels", -1) > cell["totalPixels"]:
        fail(f"cell `{cell.get('id')}` matchingPixels exceeds totalPixels")
    if cell.get("mismatchPixels") != cell["totalPixels"] - cell["matchingPixels"]:
        fail(f"cell `{cell.get('id')}` mismatchPixels must equal total - matching")
    if cell.get("maxChannelDelta", 0) < 0:
        fail(f"cell `{cell.get('id')}` maxChannelDelta must be non-negative")
    for field in ("averageCoverageExpected", "averageCoverageObserved"):
        value = cell.get(field)
        if not isinstance(value, (int, float)) or value < 0 or value > 1:
            fail(f"cell `{cell.get('id')}` {field} must be in [0, 1], got {value}")
    samples = cell.get("representativeSamples")
    if not isinstance(samples, list) or not samples:
        fail(f"cell `{cell.get('id')}` must include representativeSamples")
    if len(samples) > 8:
        fail(f"cell `{cell.get('id')}` representativeSamples must stay bounded")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-267")
    require_text(probe, "parent", "FOR-241")
    require_text(probe, "probe", "round-cap-join-boundary-cell-coverage-equivalence")
    require_text(probe["scene"], "sceneId", "m60-bounded-stroke-cap-join")
    require_bool(probe["colorPolicy"], "applyColorspaceTransform", True)
    require_bool(probe["colorPolicy"], "targetColorSpaceBlend", True)
    require_bool(probe["colorPolicy"], "targetColorSpaceBlendGloballyEnabled", False)
    require_bool(probe["colorPolicy"], "defaultTargetColorSpaceBlend", False)
    if "diagnostic proxy only" not in probe["colorPolicy"].get("coverageProxy", ""):
        fail("colorPolicy.coverageProxy must state that it is diagnostic-only")
    require_text(probe, "intermediateFormat", "RGBA16Float")
    require_text(probe["routeDiagnostics"], "diagnosticRoute", "webgpu.coverage.stroke-cap-join.experimental-render")
    require_text(probe["routeDiagnostics"], "normalRoute", "webgpu.coverage.refuse")
    require_text(probe["routeDiagnostics"], "fallbackReason", FALLBACK_REASON)
    if FALLBACK_REASON not in probe["routeDiagnostics"].get("productionRefusal", ""):
        fail("productionRefusal must preserve stroke cap/join fallback reason")
    for field in (
        "defaultEnabled",
        "runtimeSnapshotsEnabled",
        "normalRenderingChanged",
        "normalShadersChanged",
        "normalThresholdsChanged",
        "cropPolicyChanged",
        "fallbackPolicyChanged",
        "intermediateFormatPolicyChanged",
        "targetColorSpaceBlendGloballyEnabled",
        "correctionApplied",
    ):
        require_bool(probe, field, False)
    require_number(probe, "supportThreshold", 99.95)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "supportDecisionReason", "REFUSED_BOUNDARY_COVERAGE_EQUIVALENCE_NOT_PROVEN")
    require_text(probe, "boundedCoverageCorrectionStatus", "REFUSED")
    require_text(probe, "nextMissingCondition", MISSING_CONDITION)
    require_text(probe, "remainingBoundary", "coverage.stroke-cap-join-aa-residual")
    require_text(probe, "preservedUnsupportedReason", CROP_FALLBACK_REASON)
    if "none_applied" not in probe.get("admissibleCorrection", ""):
        fail("admissibleCorrection must be none_applied")

    cells = probe.get("cellsInspected")
    if not isinstance(cells, list) or len(cells) != len(EXPECTED_CELLS):
        fail(f"expected {len(EXPECTED_CELLS)} cellsInspected, got {len(cells) if isinstance(cells, list) else cells}")
    actual_ids = [cell.get("id") for cell in cells]
    if actual_ids != list(EXPECTED_CELLS):
        fail(f"cell order expected {list(EXPECTED_CELLS)}, got {actual_ids}")
    for cell_id, classification in EXPECTED_CELLS.items():
        validate_cell(cell_by_id(probe, cell_id), classification)

    stats = probe.get("coverageStatistics")
    if not isinstance(stats, dict):
        fail("missing coverageStatistics")
    require_number(stats, "cellCount", 4)
    require_bool(stats, "roundCapCellsObserved", True)
    require_bool(stats, "roundJoinCellsObserved", True)
    require_bool(stats, "overlapHighDeltaCellObserved", True)
    require_bool(stats, "safeBoundedCoverageCorrectionProven", False)
    if stats.get("notEquivalentCells", 0) < 1:
        fail("FOR-267 must keep at least one non-equivalent cell")
    if stats.get("maxCellDelta", 0) <= 1:
        fail("FOR-267 must preserve a material boundary-cell delta")
    if stats.get("maxCoverageProxyDelta", 0) <= 0:
        fail("FOR-267 must record a non-zero coverage proxy delta")

    samples = probe.get("representativeSamples")
    if not isinstance(samples, list) or not samples:
        fail("missing representativeSamples")
    if len(samples) > 12:
        fail("representativeSamples must stay bounded")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-267 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    require_file_text(
        TEST,
        [
            "FOR-267 round cap join boundary cells keep diagnostic",
            "GPUTextureFormat.RGBA16Float",
            "targetColorSpaceBlend = true",
            "kanvas.webgpu.strokeCapJoin.experimentalRender",
            "round-cap-join-coverage-equivalence-for267.json",
            "KEEP_DIAGNOSTIC",
            "REFUSED_BOUNDARY_COVERAGE_EQUIVALENCE_NOT_PROVEN",
            MISSING_CONDITION,
            CROP_FALLBACK_REASON,
        ],
    )
    device_text = require_file_text(
        DEVICE,
        [
            "private val intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float",
            "targetColorSpaceBlend: Boolean = false",
            "kanvas.webgpu.strokeCapJoin.experimentalRender",
        ],
    )
    for forbidden in (
        "FOR267",
        "for267",
        "kanvas.webgpu.for267",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-267 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        REPORT,
        [
            "FOR-267 Round Cap/Join Coverage Equivalence Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "Correction de couverture bornée: refusée",
            "round-left-cap-boundary",
            "round-join-apex",
            "for266-high-delta-round-bin-overlap",
            FALLBACK_REASON,
            CROP_FALLBACK_REASON,
            MISSING_CONDITION,
        ],
    )

    print(
        "FOR-267 validation passed: round cap/join boundary-cell audit is "
        "diagnostic-only, preserves production defaults/refusals, keeps "
        "targetColorSpaceBlend and RGBA16Float scoped to evidence, and leaves "
        "supportDecision=KEEP_DIAGNOSTIC."
    )


if __name__ == "__main__":
    main()
