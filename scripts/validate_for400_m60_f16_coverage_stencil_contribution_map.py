#!/usr/bin/env python3
"""Validate FOR-400 M60 F16 coverage/stencil contribution-map evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-400"
SCENE_ID = "m60-f16-coverage-stencil-contribution-map-for400"
DECISION = "M60_F16_COVERAGE_STENCIL_CONTRIBUTION_MAP_RECORDED"
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-399-prouve-que-la-correction-m60-f16-bornee-atteint-le-shader-mais-ne-contribue-pas-aux-pixels-finaux"
)

FOR400_GUARD = "kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled"
FOR399_GUARD = "kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled"
FOR398_GUARD = "kanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled"
FRAGMENT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
OLD_FOR380_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-400-m60-f16-coverage-stencil-contribution-map.md"
)
FOR399_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-bounded-correction-shader-application-point-for399"
    / "m60-f16-bounded-correction-shader-application-point-for399.json"
)
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CAPTURE_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
GPU_RASTER_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_PIXELS = [
    (93, 74),
    (92, 75),
    (91, 76),
    (17, 77),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
]
EXPECTED_WINDOW = {
    (xx, yy)
    for x, y in EXPECTED_PIXELS
    for yy in range(y - 1, y + 2)
    for xx in range(x - 1, x + 2)
}
ALLOWED_CLASSIFICATIONS = {
    "neighbor-contribution-candidates-found",
    "predicate-window-zero-contribution",
    "inside-outside-side-mismatch-suspected",
    "coverage-stencil-map-inconclusive",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-400 validation failed: {message}")


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
    require(isinstance(pixels, list), "pixel list expected")
    out: set[tuple[int, int]] = set()
    for pixel in pixels:
        require(isinstance(pixel, dict), f"pixel entry must be object: {pixel!r}")
        x = pixel.get("x")
        y = pixel.get("y")
        require(isinstance(x, int) and isinstance(y, int), f"invalid pixel coordinate: {pixel!r}")
        out.add((x, y))
    return out


def effective(sample: dict[str, Any]) -> bool:
    color = sample.get("colorSentToBlend")
    require(isinstance(color, list) and len(color) == 4, f"colorSentToBlend must be vec4: {sample!r}")
    coverage = sample.get("coverageAlpha")
    alpha = sample.get("sourceAlphaAfterCoverage")
    require(isinstance(coverage, (int, float)), f"coverageAlpha missing: {sample!r}")
    require(isinstance(alpha, (int, float)), f"sourceAlphaAfterCoverage missing: {sample!r}")
    return coverage > 0.0 and alpha > 0.0 and any(abs(float(channel)) > 0.000001 for channel in color)


def validate_sources() -> None:
    for399 = load_json(FOR399_ARTIFACT)
    require(for399.get("linear") == "FOR-399", "FOR-399 source artifact has wrong Linear id")
    require(for399.get("supportClaim") is False, "FOR-399 supportClaim changed")
    require(for399.get("promoted") is False, "FOR-399 promoted changed")
    render = for399.get("renderComparison")
    require(isinstance(render, dict), "FOR-399 renderComparison missing")
    require(render.get("currentTotalResidual") == 62748, "FOR-399 current residual changed")
    require(render.get("correctedTotalResidual") == 62748, "FOR-399 corrected residual changed")
    shader = for399.get("shaderApplicationPoint")
    require(isinstance(shader, dict), "FOR-399 shaderApplicationPoint missing")
    require(shader.get("effectiveContributionHintCount") == 0, "FOR-399 contribution count changed")
    require(shader.get("blendInputNonZeroCount") == 0, "FOR-399 blend input count changed")

    renderer = read_text(RENDERER)
    capture = read_text(CAPTURE_TEST)
    sink = read_text(WEBGPU_SINK)
    build = read_text(GPU_RASTER_BUILD)

    for needle in (
        f'"{FOR400_GUARD}"',
        "m60F16CoverageStencilContributionMapDiagnosticsEnabled",
        "M60F16CoverageStencilContributionMapSnapshot",
        "m60F16CoverageStencilContributionMapSnapshot()",
        "loadM60F16CoverageStencilContributionMapDiagnosticShader",
        "M60_F16_COVERAGE_STENCIL_CONTRIBUTION_MAP_SAMPLE_LIMIT",
        "M60_F16_COVERAGE_STENCIL_CONTRIBUTION_MAP_POINTS",
    ):
        require(needle in renderer, f"renderer missing FOR-400 proof: {needle}")

    for needle in (
        "withM60F16CoverageStencilContributionMapDiagnostic(true)",
        "writeM60F16CoverageStencilContributionMap",
        "m60F16CoverageStencilContributionMapJson",
        "collapseM60F16CoverageStencilContributionMapSamples",
        '"supportClaim": false',
        '"promoted": false',
        "predicate-window-zero-contribution",
    ):
        require(needle in capture, f"capture test missing FOR-400 proof: {needle}")

    require("coverageStencilContributionMapSnapshot" in sink, "WebGpuSink does not expose FOR-400 snapshot")
    require(OLD_FOR380_GUARD in renderer, "FOR-380 guard name should remain distinct")

    for needle in (
        f'System.getProperty("{FOR400_GUARD}")?.let',
        f'systemProperty("{FOR400_GUARD}", it)',
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
    require(guards.get("coverageStencilContributionMap", {}).get("guardId") == FOR400_GUARD, "FOR-400 guard missing")
    require(guards.get("coverageStencilContributionMap", {}).get("enabledByDefault") is False, "FOR-400 default changed")
    require(guards.get("boundedRuntimeCorrection", {}).get("guardId") == FOR398_GUARD, "FOR-398 guard missing")
    require(guards.get("boundedRuntimeCorrection", {}).get("enabledByDefault") is False, "FOR-398 default changed")
    require(guards.get("fragmentLaneDiagnostic", {}).get("guardId") == FRAGMENT_GUARD, "FOR-396 guard missing")
    require(guards.get("bandMetadataTransport", {}).get("guardId") == TRANSPORT_GUARD, "FOR-394 guard missing")

    snapshot = data.get("runtimeSnapshot")
    require(isinstance(snapshot, dict), "runtimeSnapshot missing")
    require(snapshot.get("api") == "SkWebGpuDevice.m60F16CoverageStencilContributionMapSnapshot()", "wrong API")
    require(snapshot.get("propertyName") == FOR400_GUARD, "wrong snapshot property")
    require(snapshot.get("enabled") is True, "FOR-400 snapshot must be enabled in evidence")
    require(snapshot.get("windowRadius") == 1, "window radius must be 1")
    require(snapshot.get("sampleLimit") == 48, "strict sample limit must be 48")
    require(snapshot.get("sampleCount") == 48, "artifact must export 48 bounded samples")
    require(snapshot.get("rawReadbackSampleCount") == 144, "raw readback count changed")
    require(snapshot.get("observedSampleCount") == 48, "observed sample count changed")

    window = data.get("predicateWindow")
    require(isinstance(window, dict), "predicateWindow missing")
    require(window.get("radius") == 1, "predicate window radius changed")
    require(window.get("strictSampleLimit") == 48, "predicate window sample limit changed")
    require(window.get("for397PixelCount") == 8, "FOR-397 pixel count changed")
    require(pixel_set(window.get("for397Pixels")) == set(EXPECTED_PIXELS), "FOR-397 pixels changed")
    require(window.get("sampleCount") == 48, "window sample count changed")
    require(window.get("predicateSampleCount") == 8, "predicate sample count changed")
    require(window.get("neighborSampleCount") == 40, "neighbor sample count changed")

    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == 48, "samples must contain 48 entries")
    coords: set[tuple[int, int]] = set()
    for sample in samples:
        require(isinstance(sample, dict), f"sample must be object: {sample!r}")
        x = sample.get("x")
        y = sample.get("y")
        require(isinstance(x, int) and isinstance(y, int), f"invalid sample coordinate: {sample!r}")
        coords.add((x, y))
        require(sample.get("belongsToFor397Predicate") == ((x, y) in EXPECTED_PIXELS), "predicate membership mismatch")
        require(sample.get("side") in {"inside", "outside", "unknown"}, f"invalid side: {sample!r}")
        require(sample.get("observedByShader") is True, f"FOR-400 window sample not observed: {sample!r}")
        require(sample.get("valid") is True, f"FOR-400 window sample invalid: {sample!r}")
        require(sample.get("effectiveContribution") == effective(sample), f"effectiveContribution mismatch: {sample!r}")
        for key in (
            "colorSentToBlend",
            "quantizedColorSentToBlend",
            "colorAfterApplyColorFilter",
            "colorAfterApplyTargetColorspaceIfNeeded",
        ):
            value = sample.get(key)
            require(isinstance(value, list) and len(value) == 4, f"{key} must be vec4: {sample!r}")
        for key in (
            "currentResidualVsReference",
            "correctedResidualVsReference",
            "coverageStencilContributionMapResidualVsReference",
            "deltaResidualCurrentVsReference",
        ):
            require(isinstance(sample.get(key), int), f"{key} must be int: {sample!r}")
    require(coords == EXPECTED_WINDOW, "FOR-400 window coordinates changed")

    summary = data.get("contributionSummary")
    require(isinstance(summary, dict), "contributionSummary missing")
    effective_samples = [sample for sample in samples if effective(sample)]
    neighbor_effective = [sample for sample in effective_samples if not sample["belongsToFor397Predicate"]]
    predicate_effective = [sample for sample in effective_samples if sample["belongsToFor397Predicate"]]
    require(summary.get("effectiveContributionCount") == len(effective_samples), "effective count mismatch")
    require(summary.get("predicateEffectiveContributionCount") == len(predicate_effective), "predicate effective count mismatch")
    require(summary.get("neighborEffectiveContributionCount") == len(neighbor_effective), "neighbor effective count mismatch")
    require(summary.get("effectiveContributionCount") == 0, "FOR-400 expected zero effective contribution")
    require(summary.get("neighborEffectiveContributionCount") == 0, "FOR-400 expected zero neighbor contribution")
    require(summary.get("dominantUsefulSide") == "none", "dominant side should be none")
    require(summary.get("for397ObservedCount") == 8, "FOR-397 observed count changed")
    require(summary.get("for397CandidateBranchReachedCount") == 8, "FOR-397 branch count changed")
    require(summary.get("expectedFor397PixelsPreserved") is True, "FOR-397 pixels not preserved")
    require(classification == "predicate-window-zero-contribution", "classification changed")

    render = data.get("renderComparison")
    require(isinstance(render, dict), "renderComparison missing")
    require(render.get("currentTotalResidual") == 62748, "current residual changed")
    require(render.get("correctedTotalResidual") == 62748, "corrected residual changed")
    require(render.get("for398ChangedPixelCount") == 0, "FOR-398 changed pixels")
    require(render.get("for400DiagnosticChangedPixelCount") == 0, "FOR-400 diagnostic changed pixels")
    require(render.get("for400DiagnosticMatchesFor398Correction") is True, "FOR-400 diagnostic changed rendering")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}={value!r}")

    return str(classification)


def validate_report(classification: str) -> None:
    report = read_text(REPORT)
    for needle in (
        "FOR-400",
        SCENE_ID,
        SOURCE_FINDING,
        classification,
        "supportClaim=false",
        "promoted=false",
        "48",
        "neighbor effective contribution: `0`",
        "rtk python3 scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py",
    ):
        require(needle in report, f"report missing: {needle}")


def main() -> None:
    validate_sources()
    classification = validate_artifact(load_json(ARTIFACT))
    validate_report(classification)
    print(f"FOR-400 validation passed: {classification}")


if __name__ == "__main__":
    main()
