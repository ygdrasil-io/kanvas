#!/usr/bin/env python3
"""Validate FOR-430 M60 F16 WebGPU/CPU width quantization alignment evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-cpu-width-quantization-alignment-for430"
FOR429_SCENE_ID = "m60-f16-cpu-span-quantization-for429"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
FOR429_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR429_SCENE_ID / f"{FOR429_SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-430-m60-f16-webgpu-cpu-width-quantization-alignment.md"
FOR429_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-429-m60-f16-cpu-span-quantization.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

EXPECTED_POINTS = {
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
}
EXPECTED_CLASSIFICATION = "webgpu-cpu-width-quantization-diagnostic-matches-cpu"
EXPECTED_DECISION = "diagnostic-only-ready-for-render-fix-ticket"
ALLOWED_CLASSIFICATIONS = {
    EXPECTED_CLASSIFICATION,
    "alignment-rejected-needs-coverage-strategy-change",
    "alignment-trace-unavailable",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py",
    "reports/wgsl-pipeline/2026-06-06-for-430-m60-f16-webgpu-cpu-width-quantization-alignment.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430/m60-f16-webgpu-cpu-width-quantization-alignment-for430.json",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py",
    "reports/wgsl-pipeline/2026-06-06-for-431-m60-f16-webgpu-width-quantized-render-fix.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-width-quantized-render-fix-for431.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-width-quantized-render-fix-for431-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431",
    "scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py",
    "reports/wgsl-pipeline/2026-06-06-for-432-m60-f16-width-quantized-color-reconstruction.md",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-width-quantized-color-reconstruction-for432",
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-430 validation failed: {message}")


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


def for429_by_point(data: dict[str, Any]) -> dict[tuple[int, int], dict[str, Any]]:
    pixels = data.get("partialPixels")
    require(isinstance(pixels, list), "FOR-429 partialPixels missing")
    return {(p.get("x"), p.get("y")): p for p in pixels if isinstance(p, dict)}


def require_span_rows_match(for430_pixel: dict[str, Any], for429_pixel: dict[str, Any]) -> None:
    for430_rows = for430_pixel.get("spanQuantizationRows")
    for429_rows = for429_pixel.get("spanQuantizationRows")
    require(isinstance(for430_rows, list) and isinstance(for429_rows, list), "spanQuantizationRows missing")
    require(for430_rows == for429_rows, "FOR-430 span rows must match FOR-429 exactly")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 18000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16WebGpuCpuWidthQuantizationAlignmentFor430(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "runtimeFlagPresent": "m60F16CpuWidthQuantizationAlignmentFor430.enabled" in capture
        and "m60F16CpuWidthQuantizationAlignmentFor430.enabled" in build,
        "simulationSourceNamed": "test-simulated-from-for429-cpu-span-quantization" in scene_window,
        "activationRefused": '"activationRefused": true' in scene_window,
        "noPipelineKeyMutationInFor430": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for diagnostic-only FOR-430: {unexpected}")


def main() -> None:
    data = load_json(ARTIFACT)
    for429 = load_json(FOR429_ARTIFACT)

    require(ARTIFACT.stat().st_size < 280_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-430", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR429_SCENE_ID, "FOR-429 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-for-430-m60-f16-evaluer-lalignement-web-gpu-sur-la-quantification-cpu-par-largeur",
        "FOR-430 draft memory link missing",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-429-cpu-add-span-coverage-span-quantization-explains-m60-f16-10-of-16-vs-6-of-16",
        "FOR-429 finding memory link missing",
    )
    require(data.get("sourceArtifacts", {}).get("for429") == rel(FOR429_ARTIFACT), "FOR-429 artifact link missing")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")
    require(data.get("decision") == EXPECTED_DECISION, "unexpected decision")

    for key in ("supportClaim", "promoted", "defaultRenderingChanged", "thresholdChanged", "scoringChanged"):
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
        "renderingFixApplied",
        "productionWgslChanged",
        "wgsl4kModified",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} must remain false")
    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("activationRefused") is True, "FOR-430 must refuse activation")
    require(policy.get("noRenderingFixApplied") is True, "FOR-430 must not apply a rendering fix")
    require(policy.get("noProductionWgslChanged") is True, "FOR-430 must not change production WGSL")
    require("simulation" in policy.get("webgpuWidthQuantizedModel", ""), "simulation model must be explicit")

    require(for429.get("classification") == "scanfill-rounded-width-exceeds-center-samples", "FOR-429 prerequisite changed")
    for429_pixels = for429_by_point(for429)
    require(set(for429_pixels) == EXPECTED_POINTS, "FOR-429 point set changed")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain exactly six pixels")
    require({(p.get("x"), p.get("y")) for p in pixels} == EXPECTED_POINTS, "partial point set mismatch")

    current_total = 0
    cpu_width_total = 0
    webgpu_width_total = 0
    current_delta_total = 0
    aligned_delta_total = 0
    for pixel in pixels:
        require(isinstance(pixel, dict), "pixel must be object")
        point = (pixel.get("x"), pixel.get("y"))
        source = for429_pixels.get(point)
        require(source is not None, f"pixel {point} missing from FOR-429")
        require(pixel.get("drawIndex") == source.get("drawIndex") == 1, "drawIndex mismatch")
        require(pixel.get("subdrawOrdinal") == source.get("subdrawOrdinal") == 0, "subdrawOrdinal mismatch")
        require(pixel.get("subdrawRole") == source.get("subdrawRole") == "inside", "subdrawRole mismatch")
        require(pixel.get("classification") == source.get("classification"), "pixel classification must mirror FOR-429")
        require(pixel.get("expectedCoverageByte") == source.get("expectedCoverageByte") == 160, "coverage byte mismatch")

        for field, expected in (
            ("cpuScanFillPathSamples", 10),
            ("cpuCenterMask4x4", 0x0137),
            ("wgslMask4x4", 0x0137),
            ("cpuCenterCoveredCount", 6),
            ("wgslCoveredCount", 6),
            ("spanQuantizedCoveredCount", 10),
        ):
            require(pixel.get(field) == source.get(field) == expected, f"{field} mismatch")

        require(pixel.get("webgpuWidthQuantizedCoveredCount") == 10, "WebGPU width quantized count must align to CPU")
        require_alpha(pixel.get("webgpuWidthQuantizedCoverageAlpha"), 10 / 16, "webgpuWidthQuantizedCoverageAlpha")
        require_alpha(pixel.get("cpuSpanCoverageAlpha"), 10 / 16, "cpuSpanCoverageAlpha")
        require_alpha(pixel.get("currentWgslCoverageAlpha"), 6 / 16, "currentWgslCoverageAlpha")
        require(pixel.get("currentDeltaToCpuSpan") == 4, "current delta must be 4 per pixel")
        require(pixel.get("alignedDeltaToCpuSpan") == 0, "aligned delta must be 0 per pixel")
        require(pixel.get("modelSource") == "test-simulated-from-for429-cpu-span-quantization", "model source mismatch")
        require(pixel.get("activationRefused") is True, "per-pixel activation must be refused")
        require_span_rows_match(pixel, source)

        current_total += int(pixel["wgslCoveredCount"])
        cpu_width_total += int(pixel["spanQuantizedCoveredCount"])
        webgpu_width_total += int(pixel["webgpuWidthQuantizedCoveredCount"])
        current_delta_total += int(pixel["currentDeltaToCpuSpan"])
        aligned_delta_total += int(pixel["alignedDeltaToCpuSpan"])

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "summary partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "summary expected partial count mismatch")
    require(summary.get("currentWgslCoveredTotal") == current_total == 36, "current WGSL total mismatch")
    require(summary.get("cpuWidthQuantizedCoveredTotal") == cpu_width_total == 60, "CPU width total mismatch")
    require(summary.get("webgpuWidthQuantizedCoveredTotal") == webgpu_width_total == 60, "aligned WebGPU total mismatch")
    require(summary.get("currentDeltaToCpuWidthTotal") == current_delta_total == 24, "current delta total mismatch")
    require(summary.get("alignedDeltaToCpuWidthTotal") == aligned_delta_total == 0, "aligned delta total mismatch")
    require_alpha(summary.get("currentWgslCoverageAlpha"), 36 / 96, "summary.currentWgslCoverageAlpha")
    require_alpha(summary.get("cpuSpanCoverageAlpha"), 60 / 96, "summary.cpuSpanCoverageAlpha")
    require_alpha(summary.get("webgpuWidthQuantizedCoverageAlpha"), 60 / 96, "summary.webgpuWidthQuantizedCoverageAlpha")

    source_audit()
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(FOR429_REPORT.is_file(), f"missing prerequisite report: {rel(FOR429_REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for token in (
        "FOR-430",
        "FOR-429",
        EXPECTED_CLASSIFICATION,
        EXPECTED_DECISION,
        "36/96",
        "60/96",
        "24/96",
        "0/96",
        "0x0137",
        "aucun changement de rendu",
    ):
        require(token in report, f"report missing {token}")

    print("FOR-430 validation passed")


if __name__ == "__main__":
    main()
