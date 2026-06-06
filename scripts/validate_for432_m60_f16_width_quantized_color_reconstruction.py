#!/usr/bin/env python3
"""Validate FOR-432 M60 F16 width-quantized color reconstruction evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-width-quantized-color-reconstruction-for432"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-432-m60-f16-width-quantized-color-reconstruction.md"
FOR431_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/"
    "m60-f16-webgpu-width-quantized-render-fix-for431.json"
)
FOR430_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430/"
    "m60-f16-webgpu-cpu-width-quantization-alignment-for430.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled"
FOR431_FLAG = "kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "source-color-mismatch",
    "destination-before-blend-mismatch",
    "coverage-applied-to-wrong-subdraw",
    "fixed-function-blend-mismatch",
    "reconstruction-matches-regression",
    "trace-incomplete",
}
EXPECTED_CLASSIFICATION = "reconstruction-matches-regression"
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py",
    "scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py",
    "scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py",
    "reports/wgsl-pipeline/2026-06-06-for-432-m60-f16-width-quantized-color-reconstruction.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/reference-cpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/current-webgpu.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-webgpu-width-quantized.png",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/opt-in-diagnostic-webgpu-width-quantized.png",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-432 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def require_number(value: Any, field: str) -> float:
    require(isinstance(value, (int, float)) and not isinstance(value, bool), f"{field} must be numeric")
    return float(value)


def require_alpha(value: Any, expected: float, field: str) -> None:
    actual = require_number(value, field)
    require(math.isclose(actual, expected, abs_tol=0.000001), f"{field} expected {expected}, got {actual}")


def require_rgba(value: Any, expected: list[int], field: str) -> None:
    require(value == expected, f"{field} expected {expected}, got {value}")


def require_delta_object(value: Any, field: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{field} must be object")
    for key in ("signedRgbaFloat", "absoluteRgbaFloat", "absoluteTotalFloat", "maxChannelFloat", "withinTolerance"):
        require(key in value, f"{field}.{key} missing")
    return value


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 28000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16WidthQuantizedColorReconstructionFor432(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagPresent": FLAG in capture and FLAG in build and FLAG in device,
        "for431OptInForced": "withM60F16WidthQuantizedRenderFixFor431(true)" in capture,
        "for432OptInForced": "withM60F16WidthQuantizedColorReconstructionFor432(true)" in capture,
        "diagnosticShaderVariant": "loadM60F16WidthQuantizedColorReconstructionFor432Shader" in device,
        "strictOptInPipeline": "m60F16WidthQuantizedColorReconstructionFor432Requested" in device,
        "noPipelineKeyMutationInFor432Producer": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for432_m60_f16_width_quantized_color_reconstruction.py" in capture,
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    try:
        diff_result = subprocess.run(
            ["git", "diff", "--name-only", "HEAD"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        status_result = subprocess.run(
            ["git", "status", "--short"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError):
        return
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-432: {unexpected}")


def main() -> None:
    data = load_json(ARTIFACT)
    for431 = load_json(FOR431_ARTIFACT)
    for430 = load_json(FOR430_ARTIFACT)

    require(ARTIFACT.stat().st_size < 180_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-432", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceFindingMemory") == "global/kanvas/findings/for-431-web-gpu-width-quantized-opt-in-render-fix-regresses-m60-f16", "FOR-431 finding link missing")
    require(data.get("optInFlag") == FOR431_FLAG, "FOR-431 opt-in flag mismatch")
    require(data.get("diagnosticFlag") == FLAG, "FOR-432 diagnostic flag mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")

    for key in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
    ):
        require(data.get(key) is False, f"{key} must remain false")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "fallbackChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "activationByDefault",
        "renderingFixApplied",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    require(for431.get("classification") == "opt-in-render-fix-regresses-scene", "FOR-431 prerequisite changed")
    require(for430.get("classification") == "webgpu-cpu-width-quantization-diagnostic-matches-cpu", "FOR-430 prerequisite changed")
    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("currentWgslCoveredTotal") == 36, "current coverage total mismatch")
    require(summary.get("cpuWidthQuantizedCoveredTotal") == 60, "CPU width total mismatch")
    require(summary.get("optInWidthQuantizedCoveredTotal") == 60, "opt-in width total mismatch")
    require(summary.get("currentTotalResidual") == 2014, "current residual mismatch")
    require(summary.get("optInTotalResidual") == 2044, "opt-in residual mismatch")
    require(summary.get("residualDeltaOptInMinusCurrent") == 30, "residual delta mismatch")
    require(summary.get("perPixelCurrentResidual") == 105, "current per-pixel residual mismatch")
    require(summary.get("perPixelOptInResidual") == 110, "opt-in per-pixel residual mismatch")
    require(summary.get("traceIncompletePixelCount") == 0, "trace must be complete")
    require(summary.get("classificationCounts", {}).get(EXPECTED_CLASSIFICATION) == 6, "classification count mismatch")
    require_alpha(summary.get("currentWgslCoverageAlpha"), 36 / 96, "summary.currentWgslCoverageAlpha")
    require_alpha(summary.get("cpuWidthQuantizedCoverageAlpha"), 60 / 96, "summary.cpuWidthQuantizedCoverageAlpha")
    require_alpha(summary.get("optInWidthQuantizedCoverageAlpha"), 60 / 96, "summary.optInWidthQuantizedCoverageAlpha")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six records")
    require({(p.get("x"), p.get("y")) for p in pixels if isinstance(p, dict)} == EXPECTED_POINTS, "partial point set mismatch")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        point = (pixel.get("x"), pixel.get("y"))
        require(pixel.get("drawIndex") == 1, f"{point} drawIndex mismatch")
        require(pixel.get("effectiveRenderDrawIndex") == 3, f"{point} effective render drawIndex mismatch")
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, f"{point} classification mismatch")
        require(pixel.get("missingFields") == [], f"{point} must have complete trace")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], f"{point}.referenceCpuRgba")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], f"{point}.currentWebGpuRgba")
        require_rgba(pixel.get("optInWebGpuRgba"), [111, 147, 129, 255], f"{point}.optInWebGpuRgba")
        require_rgba(pixel.get("optInDiagnosticWebGpuRgba"), [111, 147, 129, 255], f"{point}.optInDiagnosticWebGpuRgba")
        require(pixel.get("currentResidual") == 105, f"{point} current residual mismatch")
        require(pixel.get("optInResidual") == 110, f"{point} opt-in residual mismatch")
        require(pixel.get("residualDeltaOptInMinusCurrent") == 5, f"{point} residual delta mismatch")

        coverage = pixel.get("coverage")
        require(isinstance(coverage, dict), f"{point}.coverage missing")
        require(coverage.get("currentWgslCoveredCount") == 6, f"{point} current count mismatch")
        require(coverage.get("cpuWidthQuantizedCoveredCount") == 10, f"{point} CPU width count mismatch")
        require(coverage.get("optInWidthQuantizedCoveredCount") == 10, f"{point} opt-in width count mismatch")
        require_alpha(coverage.get("currentWgslCoverageAlpha"), 6 / 16, f"{point}.currentWgslCoverageAlpha")
        require_alpha(coverage.get("cpuWidthQuantizedCoverageAlpha"), 10 / 16, f"{point}.cpuWidthQuantizedCoverageAlpha")
        require_alpha(coverage.get("optInWidthQuantizedCoverageAlpha"), 10 / 16, f"{point}.optInWidthQuantizedCoverageAlpha")
        require_alpha(coverage.get("coverageAppliedByShader"), 10 / 16, f"{point}.coverageAppliedByShader")

        source = pixel.get("sourceReturnedByShader")
        require(isinstance(source, dict), f"{point}.sourceReturnedByShader missing")
        require(source.get("available") is True, f"{point} shader source unavailable")
        require(source.get("subdrawOrdinal") == 0, f"{point} subdraw ordinal mismatch")
        require(source.get("subdrawRole") == "inside", f"{point} subdraw role mismatch")
        require(source.get("candidateBranchReached") is True, f"{point} FOR-431 candidate branch must be reached")
        require(isinstance(source.get("sourceColorSentToBlend"), list), f"{point} sourceColorSentToBlend missing")

        destination = pixel.get("destination")
        require(isinstance(destination, dict), f"{point}.destination missing")
        require_rgba(destination.get("dstBeforeRgba8"), [181, 191, 230, 255], f"{point}.dstBeforeRgba8")
        require_rgba(destination.get("dstAfterRgba8"), [111, 147, 129, 255], f"{point}.dstAfterRgba8")

        reconstruction = pixel.get("reconstruction")
        require(isinstance(reconstruction, dict), f"{point}.reconstruction missing")
        require(reconstruction.get("targetDrawIndex") == 3, f"{point} reconstruction target drawIndex mismatch")
        require(reconstruction.get("selectedCandidate") == "single-0-inside", f"{point} selected candidate mismatch")
        require(
            reconstruction.get("stencilContributionPolicy")
            == "The AA cover pipelines use opposite stencil compare ops, so one fragment is expected to contribute through one subdraw, not both.",
            f"{point} stencil contribution policy mismatch",
        )
        require(reconstruction.get("ambiguousSingleStencilCandidateCount") == 2, f"{point} single-stencil ambiguity mismatch")
        require(reconstruction.get("replayedSourceCount") == 1, f"{point} replayed source count mismatch")
        replayed = reconstruction.get("replayedSubdraws")
        require(isinstance(replayed, list) and len(replayed) == 1, f"{point} replayed subdraws mismatch")
        require(
            {(s.get("drawIndex"), s.get("subdrawOrdinal"), s.get("subdrawRole")) for s in replayed if isinstance(s, dict)}
            == {(3, 0, "inside")},
            f"{point} replayed subdraw identity mismatch",
        )
        require(isinstance(reconstruction.get("reconstructedRgbaFloat"), list), f"{point} reconstructed source-over missing")
        dst_after_delta = require_delta_object(reconstruction.get("reconstructedVsDstAfterDelta"), f"{point}.reconstructedVsDstAfterDelta")
        observed_delta = require_delta_object(reconstruction.get("reconstructedVsObservedImageDelta"), f"{point}.reconstructedVsObservedImageDelta")
        image_delta = require_delta_object(reconstruction.get("diagnosticImageVsOptInImageDelta"), f"{point}.diagnosticImageVsOptInImageDelta")
        require(dst_after_delta.get("withinTolerance") is True, f"{point} dstAfter delta must match single-subdraw reconstruction")
        require(observed_delta.get("withinTolerance") is True, f"{point} observed image delta must match single-subdraw reconstruction")
        require(image_delta.get("withinTolerance") is True, f"{point} diagnostic image must match opt-in image")
        require(require_number(observed_delta.get("maxChannelFloat"), f"{point}.observedMaxDelta") < 1 / 255, f"{point} observed delta too large")
        candidates = reconstruction.get("candidateReconstructions")
        require(isinstance(candidates, list) and len(candidates) == 3, f"{point} expected three reconstruction candidates")
        by_label = {candidate.get("label"): candidate for candidate in candidates if isinstance(candidate, dict)}
        require({"single-0-inside", "single-1-outside", "all-width-quantized-subdraws"} <= set(by_label), f"{point} candidate labels mismatch")
        for label in ("single-0-inside", "single-1-outside"):
            candidate = by_label[label]
            require(candidate.get("replayedSourceCount") == 1, f"{point} {label} replay count mismatch")
            candidate_dst_delta = require_delta_object(candidate.get("reconstructedVsDstAfterDelta"), f"{point}.{label}.dstDelta")
            candidate_observed_delta = require_delta_object(candidate.get("reconstructedVsObservedImageDelta"), f"{point}.{label}.observedDelta")
            require(candidate_dst_delta.get("withinTolerance") is True, f"{point} {label} must match dstAfter")
            require(candidate_observed_delta.get("withinTolerance") is True, f"{point} {label} must match observed image within byte tolerance")
        all_candidate = by_label["all-width-quantized-subdraws"]
        require(all_candidate.get("replayedSourceCount") == 2, f"{point} all-subdraw candidate replay count mismatch")
        all_observed_delta = require_delta_object(all_candidate.get("reconstructedVsObservedImageDelta"), f"{point}.allSubdraw.observedDelta")
        require(all_observed_delta.get("withinTolerance") is False, f"{point} all-subdraw replay must not be the selected proof")
        subdraws = pixel.get("shaderReturnSubdraws")
        require(isinstance(subdraws, list) and len(subdraws) == 6, f"{point} expected six captured shader subdraws")
        replayed_subdraws = [
            sample for sample in subdraws
            if isinstance(sample, dict) and sample.get("replayedByReconstruction") is True
        ]
        require(len(replayed_subdraws) == 1, f"{point} expected one replayed source record")
        require(
            all(sample.get("drawIndex") == 3 for sample in replayed_subdraws),
            f"{point} replayed records must come from effective drawIndex 3",
        )
        require(
            all(math.isclose(require_number(sample.get("coverageOrAaAlpha"), f"{point}.replayedCoverage"), 10 / 16, abs_tol=0.000001) for sample in replayed_subdraws),
            f"{point} replayed records must use width-quantized coverage",
        )

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-432",
        EXPECTED_CLASSIFICATION,
        FLAG,
        "[111, 147, 129, 255]",
        "[133, 150, 214, 255]",
        "36/96",
        "60/96",
        "no correction",
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-432 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
