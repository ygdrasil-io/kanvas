#!/usr/bin/env python3
"""Validate FOR-438 M60 F16 CPU-vs-WebGPU green draw coverage evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-cpu-vs-webgpu-green-draw-coverage-for438"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-438-m60-f16-cpu-vs-webgpu-green-draw-coverage.md"
FOR437_SCENE_ID = "m60-f16-cpu-reference-source-expectation-for437"
FOR437_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR437_SCENE_ID / f"{FOR437_SCENE_ID}.json"
FOR437_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-437-m60-f16-cpu-reference-source-expectation.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled"
FOR437_FLAG = "kanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled"
FOR436_FLAG = "kanvas.webgpu.m60F16HostDrawPaintBindingFor436.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "cpu-green-draw-does-not-cover-pixels",
    "cpu-green-draw-covers-but-does-not-mutate",
    "webgpu-stencil-cover-overcovers-green-draw",
    "draw-order-or-prefix-fixture-mismatch",
    "trace-incomplete",
    "cpu-webgpu-green-draw-coverage-unresolved",
}
INCOMPLETE_CLASSIFICATIONS = {
    "trace-incomplete",
    "cpu-webgpu-green-draw-coverage-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for436_m60_f16_host_draw_paint_binding.py",
    "scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py",
    "scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py",
    "scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
    "scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
    "reports/wgsl-pipeline/2026-06-06-for-438-m60-f16-cpu-vs-webgpu-green-draw-coverage.md",
    "reports/wgsl-pipeline/2026-06-06-for-439-m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask.md",
    "reports/wgsl-pipeline/2026-06-06-for-440-m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440/m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/kotlin/",
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-438 validation failed: {message}")


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


def require_close(value: Any, expected: float, field: str, tol: float = 0.000001) -> None:
    actual = require_number(value, field)
    require(math.isclose(actual, expected, abs_tol=tol), f"{field} expected {expected}, got {actual}")


def require_rgba(value: Any, expected: list[int], field: str) -> None:
    require(value == expected, f"{field} expected {expected}, got {value}")


def source_audit() -> None:
    capture = CAPTURE_TEST.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 45000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16CpuVsWebGpuGreenDrawCoverageFor438(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for438Requested": "FOR438_CPU_VS_WEBGPU_GREEN_DRAW_COVERAGE_PROPERTY" in capture,
        "for436ForcedForSnapshot": "withM60F16HostDrawPaintBindingFor436(" in capture and FOR436_FLAG in capture,
        "cpuBeforeFixturePresent": "BoundedStrokeCapJoinPrefixFor437GM" in scene_window,
        "cpuThroughGreenFixturePresent": "BoundedStrokeCapJoinThroughGreenFor438GM" in capture,
        "cpuGreenCoverageFixturePresent": "BoundedStrokeCapJoinGreenCoverageFor438GM" in capture,
        "usesCpuAndWebGpuFields": all(
            token in scene_window
            for token in (
                "cpuGreenCoverageByte",
                "cpuGreenDrawMutates",
                "webGpuStencilCoverCovers",
                "sourceDraftMemory",
                "sourceFindingMemory",
            )
        ),
        "validatorCommandPresent": "validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for FOR-438: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
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
        and "do not change coverage" not in line
        and "before changing" not in line
        and "pipelineKeyChanged" not in line
    ]
    require(not pipeline_key_lines, f"PipelineKey lines changed: {pipeline_key_lines}")


def require_classification_consistency(data: dict[str, Any], pixels: list[dict[str, Any]]) -> None:
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification}")
    require(classification not in INCOMPLETE_CLASSIFICATIONS, f"classification must be complete, got {classification}")
    pixel_classes = {pixel.get("classification") for pixel in pixels}
    require(pixel_classes == {classification}, f"pixel classifications mismatch: {pixel_classes}")

    if classification == "webgpu-stencil-cover-overcovers-green-draw":
        for pixel in pixels:
            cpu = pixel["cpuDecision"]
            webgpu = pixel["webGpuStencilCoverDecision"]
            require(cpu.get("greenDrawCoversPixel") is False, "CPU green coverage must be false")
            require(cpu.get("greenDrawMutatesPixel") is False, "CPU green mutation must be false")
            require(webgpu.get("stencilCoverCoversPixel") is True, "WebGPU stencil-cover must cover")
    elif classification == "cpu-green-draw-does-not-cover-pixels":
        for pixel in pixels:
            require(pixel["cpuDecision"].get("greenDrawCoversPixel") is False, "CPU green coverage must be false")
    elif classification == "cpu-green-draw-covers-but-does-not-mutate":
        for pixel in pixels:
            cpu = pixel["cpuDecision"]
            require(cpu.get("greenDrawCoversPixel") is True, "CPU green coverage must be true")
            require(cpu.get("greenDrawMutatesPixel") is False, "CPU green mutation must be false")
    elif classification == "draw-order-or-prefix-fixture-mismatch":
        mismatched = [
            pixel
            for pixel in pixels
            if not pixel["cpuDecision"].get("beforeGreenEqualsReference")
            or not pixel["cpuDecision"].get("throughGreenEqualsReference")
        ]
        require(mismatched, "draw-order classification requires at least one CPU fixture mismatch")


def main() -> None:
    data = load_json(ARTIFACT)
    for437 = load_json(FOR437_ARTIFACT)

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(ARTIFACT.stat().st_size < 80_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-438", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR437_SCENE_ID, "FOR-437 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-isoler-divergence-draw-coverage-cpu-conserve-bleu-web-gpu-applique-vert",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-437-cpu-reference-six-m60-f16-pixels-come-from-pre-draw-index-3-blue-prefix",
        "FOR-437 finding link missing",
    )
    require(data.get("sourceArtifact") == rel(FOR437_ARTIFACT), "FOR-437 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR437_REPORT), "FOR-437 report link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("diagnosticFlag") == FLAG, "FOR-438 diagnostic flag mismatch")
    require(data.get("sourceFor437DiagnosticFlag") == FOR437_FLAG, "FOR-437 flag mismatch")
    require(data.get("sourceFor436DiagnosticFlag") == FOR436_FLAG, "FOR-436 flag mismatch")
    require(for437.get("classification") == "cpu-reference-source-derived-from-different-draw", "FOR-437 prerequisite changed")

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

    policy = data.get("comparisonPolicy")
    require(isinstance(policy, dict), "comparisonPolicy missing")
    require(policy.get("boundedToSixPixels") is True, "six pixel bound missing")
    require(policy.get("noRenderingFixApplied") is True, "rendering fix policy mismatch")
    require(policy.get("coverageNumerator") == 10, "coverage numerator mismatch")
    require(policy.get("coverageDenominator") == 16, "coverage denominator mismatch")
    require_close(policy.get("coverageAlpha"), 0.625, "coverageAlpha")
    require(policy.get("cpuBeforeGreenFixture") == "BoundedStrokeCapJoinPrefixFor437GM", "CPU before fixture mismatch")
    require(policy.get("cpuThroughGreenFixture") == "BoundedStrokeCapJoinThroughGreenFor438GM", "CPU through fixture mismatch")
    require(policy.get("cpuGreenCoverageFixture") == "BoundedStrokeCapJoinGreenCoverageFor438GM", "CPU coverage fixture mismatch")

    host = data.get("hostDrawIndex3")
    require(isinstance(host, dict), "hostDrawIndex3 missing")
    require(host.get("paintHexArgb") == "0xFF008A4C", "host green paint mismatch")
    require(host.get("strokeCap") == "round", "host cap mismatch")
    require(host.get("strokeJoin") == "round", "host join mismatch")
    require_close(host.get("strokeWidth"), 10.0, "host strokeWidth")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("hostBoundDrawIndex") == 3, "host draw mismatch")
    require(summary.get("hostBoundPaintHexArgb") == "0xFF008A4C", "host paint mismatch")
    require(summary.get("webGpuStencilCoverCount") == 6, "WebGPU stencil-cover count mismatch")
    require(summary.get("webGpuCoverage10Of16Count") == 6, "WebGPU 10/16 count mismatch")
    require(summary.get("traceComplete") is True, "trace must be complete")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    require_classification_consistency(data, pixels)
    seen_points = {(pixel.get("x"), pixel.get("y")) for pixel in pixels if isinstance(pixel, dict)}
    require(seen_points == EXPECTED_POINTS, f"partial point set mismatch: {seen_points}")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        require(pixel.get("drawIndex") == 3, "pixel drawIndex mismatch")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], "referenceCpuRgba")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], "currentWebGpuRgba")
        require_rgba(pixel.get("optInFor431Rgba"), [111, 147, 129, 255], "optInFor431Rgba")
        require(pixel.get("missingFields") == [], "pixel trace must be complete")

        cpu = pixel.get("cpuDecision")
        require(isinstance(cpu, dict), "cpuDecision missing")
        require_rgba(cpu.get("beforeGreenRgba"), [133, 150, 214, 255], "CPU before green")
        require(cpu.get("beforeGreenEqualsReference") is True, "CPU before green must equal reference")
        require(isinstance(cpu.get("greenCoverageAlphaByte"), int), "CPU green coverage byte missing")
        require_number(cpu.get("greenCoverageAlpha"), "CPU green coverage alpha")

        webgpu = pixel.get("webGpuStencilCoverDecision")
        require(isinstance(webgpu, dict), "webGpuStencilCoverDecision missing")
        require(webgpu.get("drawIndex") == 3, "WebGPU drawIndex mismatch")
        require(webgpu.get("subdrawOrdinal") == 0, "WebGPU subdraw ordinal mismatch")
        require(webgpu.get("subdrawRole") == "inside", "WebGPU subdraw role mismatch")
        require(webgpu.get("shaderObserved") is True, "WebGPU shaderObserved mismatch")
        require(webgpu.get("targetWithinScissor") is True, "WebGPU targetWithinScissor mismatch")
        require_close(webgpu.get("coverageOrAaAlpha"), 0.625, "WebGPU coverage")
        require(webgpu.get("coverageNumeratorOf16") == 10, "WebGPU coverage numerator mismatch")
        require(webgpu.get("stencilCoverCoversPixel") is True, "WebGPU stencil cover mismatch")

    source_audit()
    print(
        "FOR-438 validation passed: CPU green draw coverage and WebGPU drawIndex 3 "
        f"stencil-cover evidence classify as {data.get('classification')}."
    )


if __name__ == "__main__":
    main()
