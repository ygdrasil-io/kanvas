#!/usr/bin/env python3
"""Validate the FOR-398 M60 F16 bounded runtime correction probe evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-398"
DECISION = "M60_F16_BOUNDED_RUNTIME_CORRECTION_PROBE_RECORDED"
SCENE_ID = "m60-f16-bounded-runtime-correction-probe-for398"
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-397-exporte-le-snapshot-runtime-du-canal-fragment-m60-f16-et-prouve-8-pixels-exacts"
)
GUARD = "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
FRAGMENT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
OLD_FOR380_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-bounded-runtime-correction-probe-for398/"
    "m60-f16-bounded-runtime-correction-probe-for398.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-398-m60-f16-bounded-runtime-correction-probe.md"
)
FOR397_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-fragment-lane-runtime-snapshot-export-for397/"
    "m60-f16-fragment-lane-runtime-snapshot-export-for397.json"
)
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_PIXELS = [
    {"x": 93, "y": 74},
    {"x": 92, "y": 75},
    {"x": 91, "y": 76},
    {"x": 17, "y": 77},
    {"x": 90, "y": 77},
    {"x": 89, "y": 78},
    {"x": 88, "y": 79},
    {"x": 87, "y": 80},
]
ALLOWED_CLASSIFICATIONS = {
    "bounded-correction-reduces-residual",
    "bounded-correction-regresses",
    "bounded-correction-refused",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-398 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    require(path.is_file(), f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def pixel_set(pixels: Any) -> set[tuple[int, int]]:
    require(isinstance(pixels, list), f"pixel list expected, got {type(pixels).__name__}")
    out: set[tuple[int, int]] = set()
    for pixel in pixels:
        require(isinstance(pixel, dict), f"pixel entry must be object: {pixel!r}")
        x = pixel.get("x")
        y = pixel.get("y")
        require(isinstance(x, int) and isinstance(y, int), f"invalid pixel coordinate: {pixel!r}")
        out.add((x, y))
    return out


def validate_sources() -> None:
    for397 = load_json(FOR397_ARTIFACT)
    require(for397.get("linear") == "FOR-397", "FOR-397 source artifact has wrong Linear id")
    require(
        for397.get("classification") == "fragment-lane-runtime-snapshot-exported",
        "FOR-397 must remain exact-match source evidence",
    )
    comparison = for397.get("pixelComparison")
    require(isinstance(comparison, dict), "FOR-397 pixelComparison missing")
    require(comparison.get("exactMatchProvenByRuntimeReadback") is True, "FOR-397 exact match not proven")
    require(comparison.get("expectedUsefulPixels") == EXPECTED_PIXELS, "FOR-397 expected pixels changed")
    require(comparison.get("shaderObservedPixelCount") == 8, "FOR-397 observed pixel count changed")
    require(comparison.get("falsePositiveCount") == 0, "FOR-397 false positives changed")
    require(comparison.get("falseNegativeCount") == 0, "FOR-397 false negatives changed")

    renderer = read_text(RENDERER)
    capture = read_text(CAPTURE_TEST)
    build = read_text(GPU_RASTER_BUILD)

    for needle in (
        f'"{GUARD}"',
        "m60F16BoundedRuntimeCorrectionProbeEnabled",
        "loadM60F16BoundedRuntimeCorrectionShader",
        "loadM60F16BoundedRuntimeCorrectionDiagnosticShader",
        "m60_f16_bounded_runtime_corrected_color",
        "m60_f16_quantize_after_bounded_runtime_correction",
        "d.m60F16BoundedRuntimeCorrectionProbe && d.m60F16BandMetadata != null",
    ):
        require(needle in renderer, f"renderer missing FOR-398 proof: {needle}")

    require(OLD_FOR380_GUARD in renderer, "FOR-380 guard name should remain distinct")
    require("WEBGPU_M60_F16_SOURCE_COLOR_CORRECTION_PROBE_FLAG" in renderer, "FOR-380 flag was removed")

    for needle in (
        "withM60F16BoundedRuntimeCorrectionProbe(true)",
        "writeM60F16BoundedRuntimeCorrectionProbe",
        "m60F16BoundedRuntimeCorrectionProbeJson",
        "bounded-correction-reduces-residual",
        "bounded-correction-regresses",
        "bounded-correction-refused",
        '"supportClaim": false',
        '"promoted": false',
    ):
        require(needle in capture, f"capture test missing FOR-398 proof: {needle}")

    for needle in (
        f'System.getProperty("{GUARD}")?.let',
        f'systemProperty("{GUARD}", it)',
    ):
        require(needle in build, f"Gradle test property propagation missing: {needle}")


def validate_artifact(data: dict[str, Any]) -> str:
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == LINEAR_ID, "wrong Linear id")
    require(data.get("sceneId") == SCENE_ID, "wrong scene id")
    require(data.get("decision") == DECISION, "wrong decision")
    require(data.get("sourceFinding") == SOURCE_FINDING, "wrong source finding")
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification!r}")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    require(data.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(data.get("correctionGuard") == GUARD, "wrong correction guard")

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards block missing")
    require(guards.get("boundedRuntimeCorrection", {}).get("guardId") == GUARD, "FOR-398 guard missing")
    require(guards.get("boundedRuntimeCorrection", {}).get("enabledByDefault") is False, "FOR-398 guard default changed")
    require(guards.get("bandMetadataTransport", {}).get("guardId") == TRANSPORT_GUARD, "FOR-394 guard missing")
    require(guards.get("fragmentLaneDiagnostic", {}).get("guardId") == FRAGMENT_GUARD, "FOR-396 guard missing")

    proof = data.get("predicateProof")
    require(isinstance(proof, dict), "predicateProof missing")
    require(proof.get("exactMatchProvenByRuntimeReadback") is True, "runtime predicate exact match not proven")
    require(proof.get("expectedPixelCount") == 8, "expected pixel count must be 8")
    require(proof.get("observedPixelCount") == 8, "observed pixel count must be 8")
    require(proof.get("falsePositiveCount") == 0, "false positives must be 0")
    require(proof.get("falseNegativeCount") == 0, "false negatives must be 0")
    require(pixel_set(proof.get("expectedPixels")) == pixel_set(EXPECTED_PIXELS), "expected pixels changed")
    require(pixel_set(proof.get("observedPixels")) == pixel_set(EXPECTED_PIXELS), "observed pixels changed")

    mutation = data.get("boundedMutationCheck")
    require(isinstance(mutation, dict), "boundedMutationCheck missing")
    require(
        mutation.get("changedPixelsWithinExpectedPredicate") is True,
        "correction changed pixels outside expected predicate",
    )
    require(mutation.get("outsideExpectedChangedPixelCount") == 0, "outside-predicate changes must be 0")

    residual = data.get("residualComparison")
    require(isinstance(residual, dict), "residualComparison missing")
    current_total = residual.get("currentTotalResidual")
    corrected_total = residual.get("correctedTotalResidual")
    delta = residual.get("deltaCorrectedMinusCurrent")
    require(isinstance(current_total, int) and current_total > 0, "current residual invalid")
    require(isinstance(corrected_total, int) and corrected_total >= 0, "corrected residual invalid")
    require(delta == corrected_total - current_total, "total residual delta is inconsistent")
    require(residual.get("gainVsCurrent") == current_total - corrected_total, "gain is inconsistent")

    predicate = data.get("predicateResidualComparison")
    require(isinstance(predicate, dict), "predicateResidualComparison missing")
    pred_current = predicate.get("currentPredicateResidual")
    pred_corrected = predicate.get("correctedPredicateResidual")
    require(isinstance(pred_current, int) and pred_current > 0, "predicate current residual invalid")
    require(isinstance(pred_corrected, int) and pred_corrected >= 0, "predicate corrected residual invalid")
    require(
        predicate.get("deltaCorrectedMinusCurrent") == pred_corrected - pred_current,
        "predicate residual delta is inconsistent",
    )
    improved = predicate.get("improvedPixels")
    regressed = predicate.get("regressedPixels")
    unchanged = predicate.get("unchangedPixels")
    require(improved + regressed + unchanged == 8, "predicate pixel counts must sum to 8")

    if classification == "bounded-correction-reduces-residual":
        require(corrected_total < current_total, "reduced classification must reduce total residual")
    elif classification == "bounded-correction-regresses":
        require(corrected_total > current_total, "regression classification must increase total residual")
    else:
        require(
            corrected_total >= current_total or mutation.get("changedPixelCount") == 0,
            "refusal must explain no reduction or no effective mutation",
        )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "productionRuntimeConnected",
        "generalizedOutsideM60F16",
        "for380BroadCorrectionReintroduced",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    return str(classification)


def validate_report(classification: str) -> None:
    report = read_text(REPORT)
    for needle in (
        "# FOR-398 M60 F16 bounded runtime correction probe",
        f"Classification: `{classification}`",
        f"Guard: `{GUARD}`",
        "supportClaim remains `false`",
        "promoted remains `false`",
        "current total residual: `62748`",
        "corrected total residual: `62748`",
        "changed pixels: `0`",
        "outside expected changed pixels: `0`",
        "rtk python3 scripts/validate_for398_m60_f16_bounded_runtime_correction_probe.py",
    ):
        require(needle in report, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-398 M60 F16 bounded runtime correction probe validation passed ({classification})")


if __name__ == "__main__":
    main()
