#!/usr/bin/env python3
"""Validate FOR-441 M60 F16 WebGPU exact subsample mask vs CPU green evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-441-m60-f16-webgpu-exact-subsample-mask-vs-cpu-green.md"
FOR440_SCENE_ID = "m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440"
FOR440_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR440_SCENE_ID / f"{FOR440_SCENE_ID}.json"
FOR440_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-440-m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled"
FOR440_FLAG = "kanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled"
FOR427_FLAG = "kanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
RUNTIME_MISSING_FIELD = "M60F16AaStencilCoverShaderReturnDiagnosticSample.wgslSubsampleMask4x4"
ALLOWED_CLASSIFICATIONS = {
    "webgpu-exact-mask-overincludes-cpu-excluded-samples",
    "webgpu-exact-mask-unavailable",
    "webgpu-exact-mask-count-mismatch",
    "cpu-green-mask-fixture-mismatch",
    "trace-incomplete",
    "webgpu-exact-mask-unresolved",
}
INCOMPLETE_CLASSIFICATIONS = {
    "trace-incomplete",
    "webgpu-exact-mask-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
    "scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
    "scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
    "scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
    "reports/wgsl-pipeline/2026-06-06-for-441-m60-f16-webgpu-exact-subsample-mask-vs-cpu-green.md",
    "reports/wgsl-pipeline/2026-06-06-for-442-m60-f16-webgpu-runtime-exact-mask-probe.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-runtime-exact-mask-probe-for442",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-runtime-exact-mask-probe-for442/m60-f16-webgpu-runtime-exact-mask-probe-for442.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/kotlin/",
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-441 validation failed: {message}")


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


def require_close(value: Any, expected: float, field: str, tol: float = 0.000001) -> None:
    require(isinstance(value, (int, float)) and not isinstance(value, bool), f"{field} must be numeric")
    require(math.isclose(float(value), expected, abs_tol=tol), f"{field} expected {expected}, got {value}")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 76000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16ExactSubsampleMaskVsCpuGreenFor441(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for441Requested": "FOR441_EXACT_SUBSAMPLE_MASK_VS_CPU_GREEN_PROPERTY" in capture,
        "for427ForcedForMask": "withM60F16AaStencilCoverSubsampleMaskFor427(" in capture
        and FOR427_FLAG in capture
        and "FOR441_EXACT_SUBSAMPLE_MASK_VS_CPU_GREEN_PROPERTY" in capture,
        "usesCpuGreenFixture": "BoundedStrokeCapJoinGreenCoverageFor438GM" in scene_window,
        "usesSourceFindingMemory": "for-440-web-gpu-edge-predicate-overincludes-cpu-excluded" in scene_window,
        "usesExactMaskFields": all(
            token in scene_window
            for token in (
                "webGpuExactMask",
                "runtimeMissingField",
                "subsampleMask4x4Hex",
                "webgpu-exact-mask-unavailable",
                RUNTIME_MISSING_FIELD,
            )
        ),
        "deviceFieldStillNullable": "val wgslSubsampleMask4x4: Int? = null" in device,
        "shaderPredicateExists": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
        "validatorCommandPresent": "validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for FOR-441: {unexpected}")
    forbidden = sorted(
        path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES) and path != rel(DEVICE)
    )
    require(not forbidden, f"forbidden production/spec/external diffs: {forbidden}")

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
    pipeline_key_lines = [
        line
        for line in capture_diff.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and "PipelineKey" in line
        and "pipelineKeyChanged" not in line
    ]
    require(not pipeline_key_lines, f"PipelineKey lines changed: {pipeline_key_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-441 diagnostic flag")
    require(RUNTIME_MISSING_FIELD in text, "report must name the runtime missing field")
    require(rel(ARTIFACT) in text, "report must link artifact path")


def require_for440_source() -> None:
    source = load_json(FOR440_ARTIFACT)
    require(FOR440_REPORT.is_file(), f"missing FOR-440 report: {rel(FOR440_REPORT)}")
    require(
        source.get("classification") == "webgpu-edge-predicate-overincludes-cpu-excluded-samples",
        f"FOR-440 prerequisite classification mismatch: {source.get('classification')}",
    )
    pixels = source.get("partialPixels")
    require(isinstance(pixels, list), "FOR-440 partialPixels must be a list")
    require({(pixel.get("x"), pixel.get("y")) for pixel in pixels} == EXPECTED_POINTS, "FOR-440 point set mismatch")


def require_pixel(pixel: dict[str, Any], global_classification: str) -> None:
    point = (pixel.get("x"), pixel.get("y"))
    require(point in EXPECTED_POINTS, f"unexpected pixel: {point}")
    require(pixel.get("drawIndex") == 3, f"pixel {point} drawIndex must be 3")
    require(pixel.get("classification") == global_classification, f"pixel {point} classification mismatch")

    cpu = pixel.get("cpuGreenMask")
    exact = pixel.get("webGpuExactMask")
    relation = pixel.get("maskRelation")
    context = pixel.get("webGpuEdgePredicateContext")
    grid = pixel.get("subsampleComparison4x4")
    require(isinstance(cpu, dict), f"pixel {point} cpuGreenMask must be an object")
    require(isinstance(exact, dict), f"pixel {point} webGpuExactMask must be an object")
    require(isinstance(relation, dict), f"pixel {point} maskRelation must be an object")
    require(isinstance(context, dict), f"pixel {point} webGpuEdgePredicateContext must be an object")
    require(isinstance(grid, list) and len(grid) == 16, f"pixel {point} grid must contain 16 cells")

    require(cpu.get("coverageAlphaByte") == 0, f"pixel {point} CPU green alpha must be zero")
    require(cpu.get("subsampleMask4x4") == 0, f"pixel {point} CPU green mask must be 0")
    require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"pixel {point} CPU green mask hex mismatch")
    require(cpu.get("coveredSubsamples4x4") == 0, f"pixel {point} CPU covered count must be zero")
    require(context.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"pixel {point} pipeline mismatch")
    require(context.get("subdrawRole") == "inside", f"pixel {point} subdraw role mismatch")
    require(context.get("targetWithinScissor") is True, f"pixel {point} must be in scissor")
    require_close(exact.get("coverageOrAaAlpha"), 0.625, f"pixel {point} coverageOrAaAlpha")
    require(exact.get("coverageDerivedCoveredSubsamples4x4") == 10, f"pixel {point} coverage count mismatch")
    require(exact.get("fallbackCoveredSubsamples4x4") == 10, f"pixel {point} fallback count mismatch")

    if global_classification == "webgpu-exact-mask-unavailable":
        require(exact.get("available") is False, f"pixel {point} exact mask must be unavailable")
        require(exact.get("runtimeMissingField") == RUNTIME_MISSING_FIELD, f"pixel {point} missing field mismatch")
        require(exact.get("subsampleMask4x4") is None, f"pixel {point} exact mask should be null")
        require(relation.get("exactMaskAvailable") is False, f"pixel {point} relation availability mismatch")
        require(relation.get("matchesCoverageCount") is None, f"pixel {point} match must be null without exact mask")
        require(RUNTIME_MISSING_FIELD in pixel.get("missingFields", []), f"pixel {point} missingFields must name runtime field")
        for cell in grid:
            require(cell.get("cpuGreenCovered") is False, f"pixel {point} grid CPU cell must be false")
            require(cell.get("webGpuEdgePredicateCovered") is None, f"pixel {point} grid WebGPU cell must be null")
    elif global_classification == "webgpu-exact-mask-overincludes-cpu-excluded-samples":
        mask = exact.get("subsampleMask4x4")
        require(isinstance(mask, int), f"pixel {point} exact mask must be integer")
        require(mask != 0, f"pixel {point} exact mask must be nonzero")
        require(exact.get("available") is True, f"pixel {point} exact mask must be available")
        require(exact.get("coveredSubsamples4x4") == 10, f"pixel {point} exact popcount must be 10")
        require(relation.get("matchesCoverageCount") is True, f"pixel {point} exact mask/count mismatch")
        require(relation.get("webGpuOnlySubsamples") == 10, f"pixel {point} WebGPU-only count mismatch")
    elif global_classification == "webgpu-exact-mask-count-mismatch":
        require(exact.get("available") is True, f"pixel {point} count mismatch requires exact mask")


def main() -> None:
    source_audit()
    require_for440_source()
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-441", "linear must be FOR-441")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("sourceSceneId") == FOR440_SCENE_ID, "sourceSceneId mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("sourceFor440DiagnosticFlag") == FOR440_FLAG, "source FOR-440 flag mismatch")
    require(data.get("sourceFor427SubsampleMaskDiagnosticFlag") == FOR427_FLAG, "source FOR-427 flag mismatch")

    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification}")
    require(classification not in INCOMPLETE_CLASSIFICATIONS, f"classification must be complete, got {classification}")
    require(data.get("supportClaim") is False, "supportClaim must remain false")
    require(data.get("promoted") is False, "promoted must remain false")
    for field in (
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionWgslChanged",
        "wgsl4kModified",
        "renderingFixApplied",
    ):
        require(data.get(field) is False, f"{field} must be false")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be an object")
    require(summary.get("partialPixelCount") == 6, "partialPixelCount must be 6")
    require(summary.get("expectedPartialPixelCount") == 6, "expectedPartialPixelCount must be 6")
    require(summary.get("cpuGreenCoverageZeroCount") == 6, "CPU green zero count must be 6")
    require(summary.get("cpuGreenMaskZero4x4Count") == 6, "CPU green mask zero count must be 6")
    require(summary.get("coverageDerived10Of16Count") == 6, "coverage-derived 10/16 count must be 6")
    require(summary.get("webGpuCoveredSubsamples10Of16Count") == 6, "fallback 10/16 count must be 6")
    require(summary.get("hostBoundDrawIndex") == 3, "host bound draw index must be 3")

    if classification == "webgpu-exact-mask-unavailable":
        require(summary.get("webGpuExactMaskAvailableCount") == 0, "exact mask availability count must be 0")
        require(summary.get("webGpuExactMaskUnavailableCount") == 6, "exact mask unavailable count must be 6")
        require(summary.get("runtimeMissingField") == RUNTIME_MISSING_FIELD, "summary runtime missing field mismatch")
        require(summary.get("traceCompleteExceptExactMask") is True, "trace must be complete except exact mask")
    elif classification == "webgpu-exact-mask-overincludes-cpu-excluded-samples":
        require(summary.get("webGpuExactMaskAvailableCount") == 6, "exact mask availability count must be 6")
        require(summary.get("exactMaskMatchesCoverageCount") == 6, "exact mask/count match count must be 6")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved must be an object")
    for field, value in non_goals.items():
        require(value is False, f"non-goal {field} must be false")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list), "partialPixels must be a list")
    require({(pixel.get("x"), pixel.get("y")) for pixel in pixels} == EXPECTED_POINTS, "point set mismatch")
    for pixel in pixels:
        require_pixel(pixel, classification)

    commands = data.get("validationCommands")
    require(isinstance(commands, list), "validationCommands must be a list")
    for expected in (
        "validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
        "validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
        "validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
        "git diff --check",
    ):
        require(any(expected in command for command in commands), f"missing validation command: {expected}")

    require_report(data)
    print(f"FOR-441 validation passed: {classification}")


if __name__ == "__main__":
    main()
