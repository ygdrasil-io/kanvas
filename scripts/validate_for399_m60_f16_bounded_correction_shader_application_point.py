#!/usr/bin/env python3
"""Validate FOR-399 M60 F16 bounded correction shader application-point evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-399"
SCENE_ID = "m60-f16-bounded-correction-shader-application-point-for399"
DECISION = "M60_F16_BOUNDED_CORRECTION_SHADER_APPLICATION_POINT_RECORDED"
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-398-applique-une-sonde-de-correction-m60-f16-bornee-mais-refuse-la-promotion-faute-de-gain"
)

FOR399_GUARD = "kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled"
FOR398_GUARD = "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
FRAGMENT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
OLD_FOR380_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    / "2026-06-05-for-399-m60-f16-bounded-correction-shader-application-point.md"
)
FOR398_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    / "m60-f16-bounded-runtime-correction-probe-for398"
    / "m60-f16-bounded-runtime-correction-probe-for398.json"
)
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
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
    "correction-branch-not-hit",
    "correction-values-identical-before-blend",
    "correction-overwritten-by-stencil-cover-composition",
    "correction-point-still-ambiguous",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-399 validation failed: {message}")


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
    for398 = load_json(FOR398_ARTIFACT)
    require(for398.get("linear") == "FOR-398", "FOR-398 source artifact has wrong Linear id")
    require(for398.get("supportClaim") is False, "FOR-398 supportClaim changed")
    require(for398.get("promoted") is False, "FOR-398 promoted changed")
    residual = for398.get("residualComparison")
    require(isinstance(residual, dict), "FOR-398 residualComparison missing")
    require(residual.get("currentTotalResidual") == 62748, "FOR-398 current residual changed")
    require(residual.get("correctedTotalResidual") == 62748, "FOR-398 corrected residual changed")
    require(residual.get("gainVsCurrent") == 0, "FOR-398 gain changed")

    renderer = read_text(RENDERER)
    capture = read_text(CAPTURE_TEST)
    sink = read_text(WEBGPU_SINK)
    build = read_text(GPU_RASTER_BUILD)

    for needle in (
        f'"{FOR399_GUARD}"',
        "m60F16BoundedCorrectionApplicationPointDiagnosticsEnabled",
        "M60F16BoundedCorrectionApplicationPointSnapshot",
        "m60F16BoundedCorrectionApplicationPointSnapshot()",
        "loadM60F16BoundedCorrectionApplicationPointDiagnosticShader",
        "m60_f16_application_point_output",
        "m60_f16_record_application_point",
        "M60_F16_BOUNDED_CORRECTION_APPLICATION_POINT_BUFFER_SIZE",
        "applicationPointDiagnosticForDraw",
    ):
        require(needle in renderer, f"renderer missing FOR-399 proof: {needle}")

    for needle in (
        "withM60F16BoundedCorrectionApplicationPointDiagnostic(true)",
        "writeM60F16BoundedCorrectionApplicationPoint",
        "m60F16BoundedCorrectionApplicationPointJson",
        '"supportClaim": false',
        '"promoted": false',
        "correction-overwritten-by-stencil-cover-composition",
    ):
        require(needle in capture, f"capture test missing FOR-399 proof: {needle}")

    require("applicationPointSnapshot" in sink, "WebGpuSink does not expose application-point snapshot")
    require(OLD_FOR380_GUARD in renderer, "FOR-380 guard name should remain distinct")

    for needle in (
        f'System.getProperty("{FOR399_GUARD}")?.let',
        f'systemProperty("{FOR399_GUARD}", it)',
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

    guards = data.get("guards")
    require(isinstance(guards, dict), "guards block missing")
    require(guards.get("applicationPointDiagnostic", {}).get("guardId") == FOR399_GUARD, "FOR-399 guard missing")
    require(guards.get("applicationPointDiagnostic", {}).get("enabledByDefault") is False, "FOR-399 default changed")
    require(guards.get("boundedRuntimeCorrection", {}).get("guardId") == FOR398_GUARD, "FOR-398 guard missing")
    require(guards.get("boundedRuntimeCorrection", {}).get("enabledByDefault") is False, "FOR-398 default changed")
    require(guards.get("fragmentLaneDiagnostic", {}).get("guardId") == FRAGMENT_GUARD, "FOR-396 guard missing")
    require(guards.get("bandMetadataTransport", {}).get("guardId") == TRANSPORT_GUARD, "FOR-394 guard missing")

    snapshot = data.get("runtimeSnapshot")
    require(isinstance(snapshot, dict), "runtimeSnapshot missing")
    require(snapshot.get("api") == "SkWebGpuDevice.m60F16BoundedCorrectionApplicationPointSnapshot()", "wrong API")
    require(snapshot.get("propertyName") == FOR399_GUARD, "wrong snapshot property")
    require(snapshot.get("enabled") is True, "FOR-399 snapshot must be enabled in evidence")
    require(snapshot.get("sampleCount") == 8, "FOR-399 must export 8 samples")

    proof = data.get("predicateProof")
    require(isinstance(proof, dict), "predicateProof missing")
    require(proof.get("expectedPixelCount") == 8, "expected pixel count must be 8")
    require(proof.get("observedPixelCount") == 8, "observed pixel count must be 8")
    require(proof.get("candidateBranchHitAllExpectedPixels") is True, "candidate branch was not hit")
    require(proof.get("falsePositiveCount") == 0, "false positives must be 0")
    require(proof.get("falseNegativeCount") == 0, "false negatives must be 0")
    require(pixel_set(proof.get("expectedPixels")) == pixel_set(EXPECTED_PIXELS), "expected pixels changed")
    require(pixel_set(proof.get("observedPixels")) == pixel_set(EXPECTED_PIXELS), "observed pixels changed")

    application = data.get("shaderApplicationPoint")
    require(isinstance(application, dict), "shaderApplicationPoint missing")
    require(application.get("measurementScope") == "M60 F16 AA stencil-cover bounded FOR-397 pixels only", "scope changed")
    samples = application.get("samples")
    require(isinstance(samples, list) and len(samples) == 8, "samples must contain 8 entries")
    require(application.get("effectiveContributionHintCount") == 0, "expected zero effective contribution")
    require(application.get("blendInputNonZeroCount") == 0, "expected zero non-zero blend inputs")
    for sample in samples:
        require(sample.get("candidateBranchReached") is True, f"branch not reached: {sample!r}")
        require(sample.get("valid") is True, f"invalid sample: {sample!r}")
        require(sample.get("side") in {"inside", "outside"}, f"invalid side: {sample!r}")
        for key in (
            "colorAfterApplyColorFilter",
            "colorAfterApplyTargetColorspaceIfNeeded",
            "colorSentToBlendBeforeQuantization",
            "quantizedColorSentToBlend",
        ):
            value = sample.get(key)
            require(isinstance(value, list) and len(value) == 4, f"{key} must be vec4: {sample!r}")
        require(sample.get("coverageAlphaUsed") == 0.0, f"coverage must be zero: {sample!r}")
        require(sample.get("sourceAlphaAfterCoverage") == 0.0, f"source alpha must be zero: {sample!r}")
        require(sample.get("finalPixelChangedByFor398Correction") is False, "FOR-398 changed a predicate pixel")
        require(sample.get("finalPixelChangedByFor399Diagnostic") is False, "FOR-399 diagnostic changed a pixel")

    render = data.get("renderComparison")
    require(isinstance(render, dict), "renderComparison missing")
    require(render.get("currentTotalResidual") == 62748, "current residual changed")
    require(render.get("correctedTotalResidual") == 62748, "corrected residual changed")
    require(render.get("gainVsCurrent") == 0, "gain must remain 0")
    require(render.get("for398ChangedPixelCount") == 0, "FOR-398 changed pixels")
    require(render.get("for399DiagnosticChangedPixelCount") == 0, "FOR-399 diagnostic changed pixels")
    require(render.get("for399DiagnosticMatchesFor398Correction") is True, "FOR-399 render differs from FOR-398")

    if classification == "correction-overwritten-by-stencil-cover-composition":
        require(application.get("effectiveContributionHintCount") == 0, "overwritten classification needs zero contribution")
    elif classification == "correction-values-identical-before-blend":
        require(application.get("filteredTargetDifferentCount") == 0, "identical classification requires identical values")
    elif classification == "correction-branch-not-hit":
        require(proof.get("candidateBranchHitAllExpectedPixels") is False, "branch-not-hit classification is inconsistent")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "for380BroadCorrectionReintroduced",
        "generalizedOutsideM60F16",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")

    return str(classification)


def validate_report(classification: str) -> None:
    report = read_text(REPORT)
    for needle in (
        "# FOR-399 M60 F16 bounded correction shader application point",
        f"Classification: `{classification}`",
        f"Guard: `{FOR399_GUARD}`",
        "supportClaim remains `false`",
        "promoted remains `false`",
        "current residual: `62748`",
        "corrected residual: `62748`",
        "gain: `0`",
        "candidate branch hit: `8 / 8`",
        "effective contribution hints: `0 / 8`",
        "rtk python3 scripts/validate_for399_m60_f16_bounded_correction_shader_application_point.py",
    ):
        require(needle in report, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    data = load_json(ARTIFACT)
    classification = validate_artifact(data)
    validate_report(classification)
    print(f"FOR-399 M60 F16 bounded correction shader application-point validation passed ({classification})")


if __name__ == "__main__":
    main()
