#!/usr/bin/env python3
"""Validate FOR-433 M60 F16 stencil subdraw source-color diagnostic evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-stencil-subdraw-source-color-for433"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-433-m60-f16-stencil-subdraw-source-color.md"
FOR432_SCENE_ID = "m60-f16-width-quantized-color-reconstruction-for432"
FOR432_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR432_SCENE_ID / f"{FOR432_SCENE_ID}.json"
FOR431_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/"
    "m60-f16-webgpu-width-quantized-render-fix-for431.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16StencilSubdrawSourceColorFor433.enabled"
FOR432_FLAG = "kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled"
FOR431_FLAG = "kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
EXPECTED_CLASSIFICATION = "source-payload-mismatch"
ALLOWED_CLASSIFICATIONS = {
    "source-payload-mismatch",
    "coverage-alpha-mismatch",
    "stencil-side-ambiguous",
    "cpu-reference-inversion-inconsistent",
    "trace-incomplete",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py",
    "scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py",
    "scripts/validate_for433_m60_f16_stencil_subdraw_source_color.py",
    "scripts/validate_for434_m60_f16_stencil_source_payload_trace.py",
    "reports/wgsl-pipeline/2026-06-06-for-433-m60-f16-stencil-subdraw-source-color.md",
    "reports/wgsl-pipeline/2026-06-06-for-434-m60-f16-stencil-source-payload-trace.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-source-payload-trace-for434",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-433 validation failed: {message}")


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


def require_rgba(value: Any, expected: list[int], field: str) -> None:
    require(value == expected, f"{field} expected {expected}, got {value}")


def require_float_list(value: Any, field: str, length: int = 4) -> list[float]:
    require(isinstance(value, list) and len(value) == length, f"{field} must be a {length}-float list")
    return [require_number(item, f"{field}[{index}]") for index, item in enumerate(value)]


def require_alpha(value: Any, expected: float, field: str, tol: float = 0.000001) -> None:
    actual = require_number(value, field)
    require(math.isclose(actual, expected, abs_tol=tol), f"{field} expected {expected}, got {actual}")


def require_delta_object(value: Any, field: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{field} must be object")
    for key in ("signedRgbaFloat", "absoluteRgbaFloat", "absoluteTotalFloat", "maxChannelFloat", "withinTolerance", "tolerance"):
        require(key in value, f"{field}.{key} missing")
    return value


def inverse_src_over(cpu: list[float], dst: list[float], alpha: float) -> list[float]:
    return [
        cpu[0] - dst[0] * (1.0 - alpha),
        cpu[1] - dst[1] * (1.0 - alpha),
        cpu[2] - dst[2] * (1.0 - alpha),
        alpha,
    ]


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 26000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16StencilSubdrawSourceColorFor433(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagOptInTestRelayOnly": FLAG in capture and FLAG not in device and FLAG in build,
        "for433DoesNotTouchPipelineKey": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for433_m60_f16_stencil_subdraw_source_color.py" in capture,
        "for431StillOptIn": "withM60F16WidthQuantizedRenderFixFor431(true)" in capture and FOR431_FLAG in capture,
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
        capture_diff = subprocess.run(
            ["git", "diff", "--unified=0", "--", rel(CAPTURE_TEST)],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return

    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-433: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden production/spec/external diffs: {forbidden}")
    require(rel(DEVICE) not in changed, "SkWebGpuDevice.kt must not change for FOR-433")

    dangerous_threshold_lines = [
        line
        for line in capture_diff.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
        )
    ]
    require(not dangerous_threshold_lines, f"threshold/scoring/fallback lines changed: {dangerous_threshold_lines}")


def main() -> None:
    data = load_json(ARTIFACT)
    for432 = load_json(FOR432_ARTIFACT)
    for431 = load_json(FOR431_ARTIFACT)

    require(ARTIFACT.stat().st_size < 120_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-433", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR432_SCENE_ID, "FOR-432 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-isoler-source-couleur-du-sous-passage-stencil-regresse",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-432-web-gpu-width-quantized-color-reconstruction-matches-single-stencil-gated-subdraw",
        "FOR-432 finding link missing",
    )
    require(data.get("sourceArtifact") == rel(FOR432_ARTIFACT), "FOR-432 artifact link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")
    require(data.get("diagnosticFlag") == FLAG, "FOR-433 diagnostic flag mismatch")
    require(data.get("sourceFor432DiagnosticFlag") == FOR432_FLAG, "FOR-432 flag mismatch")
    require(data.get("sourceFor431OptInFlag") == FOR431_FLAG, "FOR-431 flag mismatch")

    for key in (
        "supportClaim",
        "promoted",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "renderingFixApplied",
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
        "for431ActivatedByDefault",
        "renderingFixApplied",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")

    require(for432.get("classification") == "reconstruction-matches-regression", "FOR-432 prerequisite changed")
    require(for431.get("classification") == "opt-in-render-fix-regresses-scene", "FOR-431 prerequisite changed")
    for432_pixels = {
        (pixel.get("x"), pixel.get("y")): pixel
        for pixel in for432.get("partialPixels", [])
        if isinstance(pixel, dict)
    }

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("sourcePayloadMismatchCount") == 6, "source mismatch count mismatch")
    require(summary.get("coverageAlphaMismatchCount") == 0, "coverage mismatch count must be zero")
    require(summary.get("stencilSideAmbiguousCount") == 0, "stencil ambiguity count must be zero")
    require(summary.get("cpuReferenceInversionInconsistentCount") == 0, "CPU inversion count must be zero")
    require(summary.get("traceIncompletePixelCount") == 0, "trace must be complete")
    require(require_number(summary.get("maxSourceVsRequiredChannelDelta"), "summary.maxSourceVsRequiredChannelDelta") > 0.32, "source delta must expose large payload mismatch")
    require(require_number(summary.get("maxCapturedCoverageAlphaDelta"), "summary.maxCapturedCoverageAlphaDelta") <= 1 / 255, "coverage alpha should match 10/16 within byte tolerance")

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require_alpha(policy.get("coverageAlpha"), 10 / 16, "comparisonPolicy.coverageAlpha")
    require(policy.get("noRenderingFixApplied") is True, "policy must be diagnostic-only")
    require(policy.get("boundedToSixPixels") is True, "policy must be bounded")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six records")
    require({(p.get("x"), p.get("y")) for p in pixels if isinstance(p, dict)} == EXPECTED_POINTS, "partial point set mismatch")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        point = (pixel.get("x"), pixel.get("y"))
        require(point in for432_pixels, f"{point} missing from FOR-432 source artifact")
        source_pixel = for432_pixels[point]
        require(pixel.get("drawIndex") == 1, f"{point} drawIndex mismatch")
        require(pixel.get("effectiveRenderDrawIndex") == 3, f"{point} effective render drawIndex mismatch")
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, f"{point} classification mismatch")
        require(pixel.get("missingFields") == [], f"{point} must have complete trace")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], f"{point}.referenceCpuRgba")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], f"{point}.currentWebGpuRgba")
        require_rgba(pixel.get("optInFor431Rgba"), [111, 147, 129, 255], f"{point}.optInFor431Rgba")
        require_rgba(source_pixel.get("referenceCpuRgba"), pixel.get("referenceCpuRgba"), f"{point}.FOR432.reference")
        require_rgba(source_pixel.get("currentWebGpuRgba"), pixel.get("currentWebGpuRgba"), f"{point}.FOR432.current")
        require_rgba(source_pixel.get("optInWebGpuRgba"), pixel.get("optInFor431Rgba"), f"{point}.FOR432.optIn")

        coverage = pixel.get("coverage")
        require(isinstance(coverage, dict), f"{point}.coverage missing")
        require(coverage.get("numerator") == 10, f"{point} coverage numerator mismatch")
        require(coverage.get("denominator") == 16, f"{point} coverage denominator mismatch")
        require_alpha(coverage.get("alpha"), 10 / 16, f"{point}.coverage.alpha")
        require_alpha(coverage.get("capturedCoverageOrAaAlpha"), 10 / 16, f"{point}.coverage.captured", tol=1 / 255)
        require(require_number(coverage.get("capturedCoverageAlphaDelta"), f"{point}.coverage.delta") <= 1 / 255, f"{point} coverage delta too high")

        destination = pixel.get("destination")
        require(isinstance(destination, dict), f"{point}.destination missing")
        dst = require_float_list(destination.get("dstBeforeRgbaFloat"), f"{point}.dstBeforeRgbaFloat")
        require_rgba(destination.get("dstBeforeRgba8"), [181, 191, 230, 255], f"{point}.dstBeforeRgba8")
        for432_dst = source_pixel.get("destination", {})
        require_rgba(for432_dst.get("dstBeforeRgba8"), destination.get("dstBeforeRgba8"), f"{point}.FOR432.dstBefore")

        candidate = pixel.get("selectedStencilCandidate")
        require(isinstance(candidate, dict), f"{point}.selectedStencilCandidate missing")
        require(candidate.get("label") == "single-0-inside", f"{point} selected candidate label mismatch")
        require(candidate.get("drawIndex") == 3, f"{point} selected candidate draw index mismatch")
        require(candidate.get("subdrawOrdinal") == 0, f"{point} selected subdraw ordinal mismatch")
        require(candidate.get("subdrawRole") == "inside", f"{point} selected subdraw role mismatch")
        require(candidate.get("candidateBranchReached") is True, f"{point} candidate branch must be reached")
        require(candidate.get("ambiguousSingleStencilCandidateCount") == 2, f"{point} candidate count mismatch")
        require(candidate.get("alternateSingleCandidatesShareSource") is True, f"{point} alternate candidate should share source")

        source = pixel.get("source")
        require(isinstance(source, dict), f"{point}.source missing")
        captured = require_float_list(source.get("capturedWebGpuPremulRgbaFloat"), f"{point}.capturedWebGpuPremulRgbaFloat")
        before_quant = require_float_list(source.get("sourceColorBeforeQuantization"), f"{point}.sourceColorBeforeQuantization")
        require(math.isclose(captured[3], 10 / 16, abs_tol=1 / 255), f"{point} captured source alpha mismatch")
        require(math.isclose(before_quant[3], 10 / 16, abs_tol=0.000001), f"{point} pre-quant alpha mismatch")
        for432_source = source_pixel.get("sourceReturnedByShader", {})
        require(for432_source.get("sourceColorSentToBlend") == source.get("capturedWebGpuPremulRgbaFloat"), f"{point} captured source must match FOR-432")

        required = pixel.get("requiredSourcePremul")
        require(isinstance(required, dict), f"{point}.requiredSourcePremul missing")
        required_rgba = require_float_list(required.get("rgbaFloat"), f"{point}.requiredSourcePremul.rgbaFloat")
        expected_required = inverse_src_over([133 / 255, 150 / 255, 214 / 255, 1.0], dst, 10 / 16)
        for index, (actual, expected) in enumerate(zip(required_rgba, expected_required)):
            require(math.isclose(actual, expected, abs_tol=0.000002), f"{point} required source channel {index} mismatch: {actual} vs {expected}")
        require(required.get("premulConsistent") is True, f"{point} required premul source must be valid")
        reconstructed_delta = require_delta_object(required.get("reconstructedVsCpuReferenceDelta"), f"{point}.reconstructedVsCpuReferenceDelta")
        require(reconstructed_delta.get("withinTolerance") is True, f"{point} inverse source must reconstruct CPU reference")
        require(require_number(reconstructed_delta.get("maxChannelFloat"), f"{point}.reconstructedMax") <= 1 / 255, f"{point} reconstructed CPU delta too high")

        delta = require_delta_object(pixel.get("webGpuSourceVsRequiredDelta"), f"{point}.webGpuSourceVsRequiredDelta")
        signed = require_float_list(delta.get("signedRgbaFloat"), f"{point}.delta.signed")
        absolute = require_float_list(delta.get("absoluteRgbaFloat"), f"{point}.delta.absolute")
        require(delta.get("withinTolerance") is False, f"{point} source delta must exceed tolerance")
        require(require_number(delta.get("maxChannelFloat"), f"{point}.delta.max") > 0.32, f"{point} source delta must expose blue-channel payload mismatch")
        require(signed[2] < -0.32 and absolute[2] > 0.32, f"{point} expected captured blue source to be far below required source")

        candidates = pixel.get("candidateSubdraws")
        require(isinstance(candidates, list) and len(candidates) == 2, f"{point} candidate subdraws mismatch")
        require(
            {(sample.get("drawIndex"), sample.get("subdrawOrdinal"), sample.get("subdrawRole")) for sample in candidates if isinstance(sample, dict)}
            == {(3, 0, "inside"), (3, 1, "outside")},
            f"{point} candidate subdraw identities mismatch",
        )

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-433",
        EXPECTED_CLASSIFICATION,
        FLAG,
        "10/16",
        "[181, 191, 230, 255]",
        "[133, 150, 214, 255]",
        "[111, 147, 129, 255]",
        "diagnostic-only",
        "source payload",
    ):
        require(token in report, f"report missing {token}")

    print(f"FOR-433 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
