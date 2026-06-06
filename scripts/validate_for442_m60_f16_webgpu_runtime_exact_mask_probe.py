#!/usr/bin/env python3
"""Validate FOR-442 M60 F16 WebGPU runtime exact mask probe evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-runtime-exact-mask-probe-for442"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-442-m60-f16-webgpu-runtime-exact-mask-probe.md"
FOR441_SCENE_ID = "m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441"
FOR441_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR441_SCENE_ID / f"{FOR441_SCENE_ID}.json"
FOR441_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-441-m60-f16-webgpu-exact-subsample-mask-vs-cpu-green.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled"
FOR441_FLAG = "kanvas.webgpu.m60F16ExactSubsampleMaskVsCpuGreenFor441.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
RUNTIME_MASK_FIELD = "M60F16AaStencilCoverShaderReturnDiagnosticSample.wgslSubsampleMask4x4"
ALLOWED_CLASSIFICATIONS = {
    "webgpu-runtime-exact-mask-overincludes-cpu-excluded-samples",
    "webgpu-runtime-exact-mask-probe-unavailable",
    "webgpu-runtime-exact-mask-count-mismatch",
    "webgpu-runtime-exact-mask-coordinate-shift",
    "cpu-green-mask-fixture-mismatch",
    "trace-incomplete",
    "webgpu-runtime-exact-mask-unresolved",
}
INCOMPLETE_CLASSIFICATIONS = {
    "trace-incomplete",
    "webgpu-runtime-exact-mask-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
    "scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
    "scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
    "scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py",
    "reports/wgsl-pipeline/2026-06-06-for-442-m60-f16-webgpu-runtime-exact-mask-probe.md",
    "reports/wgsl-pipeline/2026-06-06-for-443-m60-f16-webgpu-low-level-exact-mask-probe.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-low-level-exact-mask-probe-for443",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-low-level-exact-mask-probe-for443/m60-f16-webgpu-low-level-exact-mask-probe-for443.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-442 validation failed: {message}")


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


def git_changed_paths() -> set[str]:
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
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 76000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16RuntimeExactMaskProbeFor442(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "for442Requested": "FOR442_RUNTIME_EXACT_MASK_PROBE_PROPERTY" in capture,
        "runtimeDeviceFlag": "WEBGPU_M60_F16_RUNTIME_EXACT_MASK_PROBE_FOR442_FLAG" in device,
        "runtimeShaderVariant": "shader-return-runtime-exact-mask-for442" in device,
        "runtimeMaskStorage": "subsampleMaskFor427Diagnostic = m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled" in device,
        "runtimeBufferSize": (
            "shaderReturnDiagnosticForDraw && m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled" in device
            and "M60_F16_SUBSAMPLE_MASK_FOR427_BUFFER_SIZE" in device
        ),
        "usesCpuGreenFixture": "BoundedStrokeCapJoinGreenCoverageFor438GM" in scene_window,
        "usesSourceFindingMemory": "for-441-web-gpu-exact-mask-unavailable" in scene_window,
        "usesRuntimeMaskFields": all(
            token in scene_window
            for token in (
                "webGpuRuntimeExactMask",
                "runtimeBlockingPoint",
                "webgpu-runtime-exact-mask-probe-unavailable",
                "webgpu-runtime-exact-mask-overincludes-cpu-excluded-samples",
                RUNTIME_MASK_FIELD,
            )
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    try:
        changed = git_changed_paths()
        capture_diff = subprocess.run(
            ["git", "diff", "--unified=0", "--", rel(CAPTURE_TEST)],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        ).stdout
        device_diff = subprocess.run(
            ["git", "diff", "--unified=0", "--", rel(DEVICE)],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return

    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-442: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/shader diffs: {forbidden}")

    dangerous_capture_lines = [
        line
        for line in capture_diff.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
            or "PipelineKey" in line
        )
    ]
    require(not dangerous_capture_lines, f"threshold/scoring/PipelineKey lines changed: {dangerous_capture_lines}")

    allowed_device_markers = (
        "WEBGPU_M60_F16_RUNTIME_EXACT_MASK_PROBE_FOR442_FLAG",
        "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled",
        "m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled",
        "System.getProperty(",
        '"false",',
        ").toBoolean()",
        'cacheKey = "diagnostic://m60-f16-aa-stencil-cover-shader-return-for412"',
        '} else {',
        "},",
        '"diagnostic://m60-f16-aa-stencil-cover-shader-return-for412"',
        "shader-return-runtime-exact-mask-for442",
        "subsampleMaskFor427Diagnostic = m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled",
        "shaderReturnDiagnosticForDraw && m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled",
        "M60_F16_SUBSAMPLE_MASK_FOR427_BUFFER_SIZE",
    )

    def allowed_for443_device_insert(line: str) -> bool:
        return (
            line.startswith("+")
            and not line.startswith("+++")
            and "GPU_SUPPORT_THRESHOLD" not in line
            and "similarity <" not in line
            and "similarity >" not in line
            and "coverage.stroke-cap-join-visual-parity-below-threshold" not in line
            and "PipelineKey" not in line
            and "fallbackPolicy" not in line
        )

    unexpected_device_lines = [
        line
        for line in device_diff.splitlines()
        if line.startswith(("+", "-"))
        and not line.startswith(("+++", "---"))
        and not any(marker in line for marker in allowed_device_markers)
        and not allowed_for443_device_insert(line)
    ]
    require(not unexpected_device_lines, f"unexpected SkWebGpuDevice changes: {unexpected_device_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-442 diagnostic flag")
    require(RUNTIME_MASK_FIELD in text, "report must name the runtime exact mask field")
    require(rel(ARTIFACT) in text, "report must link artifact path")


def require_for441_source() -> None:
    source = load_json(FOR441_ARTIFACT)
    require(FOR441_REPORT.is_file(), f"missing FOR-441 report: {rel(FOR441_REPORT)}")
    require(source.get("classification") == "webgpu-exact-mask-unavailable", "FOR-441 source must remain unavailable")
    pixels = source.get("partialPixels")
    require(isinstance(pixels, list), "FOR-441 partialPixels must be a list")
    require({(pixel.get("x"), pixel.get("y")) for pixel in pixels} == EXPECTED_POINTS, "FOR-441 point set mismatch")


def require_pixel(pixel: dict[str, Any], global_classification: str) -> None:
    point = (pixel.get("x"), pixel.get("y"))
    require(point in EXPECTED_POINTS, f"unexpected pixel: {point}")
    require(pixel.get("drawIndex") == 3, f"pixel {point} drawIndex must be 3")
    if global_classification == "webgpu-runtime-exact-mask-probe-unavailable":
        require(
            pixel.get("classification")
            in {
                "webgpu-runtime-exact-mask-probe-unavailable",
                "webgpu-runtime-exact-mask-count-mismatch",
                "webgpu-runtime-exact-mask-overincludes-cpu-excluded-samples",
            },
            f"pixel {point} classification mismatch",
        )
    else:
        require(pixel.get("classification") == global_classification, f"pixel {point} classification mismatch")

    cpu = pixel.get("cpuGreenMask")
    runtime = pixel.get("webGpuRuntimeExactMask")
    relation = pixel.get("maskRelation")
    context = pixel.get("webGpuEdgePredicateContext")
    grid = pixel.get("subsampleComparison4x4")
    require(isinstance(cpu, dict), f"pixel {point} cpuGreenMask must be an object")
    require(isinstance(runtime, dict), f"pixel {point} webGpuRuntimeExactMask must be an object")
    require(isinstance(relation, dict), f"pixel {point} maskRelation must be an object")
    require(isinstance(context, dict), f"pixel {point} webGpuEdgePredicateContext must be an object")
    require(isinstance(grid, list) and len(grid) == 16, f"pixel {point} grid must contain 16 cells")

    require(cpu.get("coverageAlphaByte") == 0, f"pixel {point} CPU green alpha must be zero")
    require(cpu.get("subsampleMask4x4") == 0, f"pixel {point} CPU green mask must be 0")
    require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"pixel {point} CPU green mask hex mismatch")
    require(cpu.get("coveredSubsamples4x4") == 0, f"pixel {point} CPU covered count must be zero")
    if runtime.get("coverageOrAaAlpha") is not None:
        require(context.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"pixel {point} pipeline mismatch")
        require(context.get("subdrawRole") == "inside", f"pixel {point} subdraw role mismatch")
        require(context.get("targetWithinScissor") is True, f"pixel {point} must be in scissor")
        require_close(runtime.get("coverageOrAaAlpha"), 0.625, f"pixel {point} coverageOrAaAlpha")
        require(runtime.get("coverageDerivedCoveredSubsamples4x4") == 10, f"pixel {point} coverage count mismatch")

    if global_classification == "webgpu-runtime-exact-mask-probe-unavailable":
        if runtime.get("available") is False:
            require(runtime.get("runtimeBlockingPoint"), f"pixel {point} must name runtime blocking point")
            require(runtime.get("subsampleMask4x4") is None, f"pixel {point} exact mask should be null")
            require(relation.get("runtimeExactMaskAvailable") is False, f"pixel {point} relation availability mismatch")
            require(
                any(
                    field in pixel.get("missingFields", [])
                    for field in (RUNTIME_MASK_FIELD, "webGpuDrawIndex3InsideStencilCoverRuntimeProbeSample")
                ),
                f"pixel {point} missingFields must name runtime field or missing runtime sample",
            )
            for cell in grid:
                require(cell.get("cpuCovered") is False, f"pixel {point} grid CPU cell must be false")
                require(cell.get("wgslCovered") is None, f"pixel {point} grid WebGPU cell must be null")
        else:
            mask = runtime.get("subsampleMask4x4")
            require(isinstance(mask, int) and mask != 0, f"pixel {point} partial mask must be nonzero")
            require(relation.get("runtimeExactMaskAvailable") is True, f"pixel {point} relation availability mismatch")
            require(relation.get("webGpuOnlySubsamples", 0) > 0, f"pixel {point} must expose WebGPU-only cells")
    elif global_classification == "webgpu-runtime-exact-mask-overincludes-cpu-excluded-samples":
        mask = runtime.get("subsampleMask4x4")
        require(isinstance(mask, int), f"pixel {point} exact mask must be integer")
        require(mask != 0, f"pixel {point} exact mask must be nonzero")
        require(runtime.get("available") is True, f"pixel {point} exact mask must be available")
        require(runtime.get("coveredSubsamples4x4") == 10, f"pixel {point} exact popcount must be 10")
        require(relation.get("matchesCoverageCount") is True, f"pixel {point} exact mask/count mismatch")
        require(relation.get("webGpuOnlySubsamples") == 10, f"pixel {point} WebGPU-only count mismatch")
        require(relation.get("divergentSubsamples") == 10, f"pixel {point} divergent count mismatch")
        require(not pixel.get("missingFields"), f"pixel {point} must not have missing fields")
        for cell in grid:
            require(cell.get("cpuCovered") is False, f"pixel {point} grid CPU cell must be false")
    elif global_classification == "webgpu-runtime-exact-mask-count-mismatch":
        require(runtime.get("available") is True, f"pixel {point} count mismatch requires exact mask")


def main() -> None:
    source_audit()
    require_for441_source()
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-442", "linear must be FOR-442")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("sourceSceneId") == FOR441_SCENE_ID, "sourceSceneId mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("sourceFor441DiagnosticFlag") == FOR441_FLAG, "source FOR-441 flag mismatch")
    require(data.get("runtimeExactMaskField") == RUNTIME_MASK_FIELD, "runtime field mismatch")
    require(data.get("runtimeInstrumentationOptInOnly") is True, "runtime instrumentation must be opt-in")
    require(data.get("runtimeInstrumentationDefaultActive") is False, "runtime instrumentation must be off by default")

    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification}")
    require(classification not in INCOMPLETE_CLASSIFICATIONS, f"classification must be complete, got {classification}")
    for field in (
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
        require(data.get(field) is False, f"{field} must be false")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be an object")
    require(summary.get("partialPixelCount") == 6, "partialPixelCount must be 6")
    require(summary.get("expectedPartialPixelCount") == 6, "expectedPartialPixelCount must be 6")
    require(summary.get("cpuGreenCoverageZeroCount") == 6, "CPU green zero count must be 6")
    require(summary.get("cpuGreenMaskZero4x4Count") == 6, "CPU green mask zero count must be 6")
    require(summary.get("hostBoundDrawIndex") == 3, "host bound draw index must be 3")

    if classification == "webgpu-runtime-exact-mask-probe-unavailable":
        available_count = summary.get("webGpuRuntimeExactMaskAvailableCount")
        unavailable_count = summary.get("webGpuRuntimeExactMaskUnavailableCount")
        require(isinstance(available_count, int), "exact mask availability count must be an integer")
        require(isinstance(unavailable_count, int), "exact mask unavailable count must be an integer")
        require(0 <= available_count < 6, "unavailable classification requires fewer than six exact masks")
        require(unavailable_count > 0, "unavailable classification requires at least one missing exact mask")
        require(available_count + unavailable_count == 6, "exact mask availability counts must cover six pixels")
        require(summary.get("runtimeBlockingPoint"), "summary must name runtime blocking point")
    elif classification == "webgpu-runtime-exact-mask-overincludes-cpu-excluded-samples":
        require(summary.get("coverageDerived10Of16Count") == 6, "coverage-derived 10/16 count must be 6")
        require(summary.get("webGpuRuntimeExactMaskAvailableCount") == 6, "exact mask availability count must be 6")
        require(summary.get("webGpuRuntimeExactMask10Of16Count") == 6, "exact mask 10/16 count must be 6")
        require(summary.get("runtimeMaskMatchesCoverageCount") == 6, "exact mask/count match count must be 6")
        require(summary.get("traceComplete") is True, "runtime exact mask trace must be complete")

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
        "validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
        "validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
        "validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
        "git diff --check",
    ):
        require(any(expected in command for command in commands), f"missing validation command: {expected}")

    require_report(data)
    print(f"FOR-442 validation passed: {classification}")


if __name__ == "__main__":
    main()
