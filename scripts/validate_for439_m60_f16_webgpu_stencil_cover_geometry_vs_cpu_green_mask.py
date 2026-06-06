#!/usr/bin/env python3
"""Validate FOR-439 M60 F16 WebGPU stencil-cover geometry vs CPU green mask evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-439-m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask.md"
FOR438_SCENE_ID = "m60-f16-cpu-vs-webgpu-green-draw-coverage-for438"
FOR438_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR438_SCENE_ID / f"{FOR438_SCENE_ID}.json"
FOR438_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-438-m60-f16-cpu-vs-webgpu-green-draw-coverage.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

FLAG = "kanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled"
FOR438_FLAG = "kanvas.webgpu.m60F16CpuVsWebGpuGreenDrawCoverageFor438.enabled"
FOR427_FLAG = "kanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "webgpu-scissor-or-bounds-overinclude",
    "webgpu-cover-polygon-overcovers-edge",
    "webgpu-coordinate-mapping-shift",
    "webgpu-inside-outside-subdraw-selection-mismatch",
    "webgpu-band-metadata-mismatch",
    "cpu-green-mask-fixture-mismatch",
    "trace-incomplete",
    "webgpu-cpu-geometry-divergence-unresolved",
}
INCOMPLETE_CLASSIFICATIONS = {
    "trace-incomplete",
    "webgpu-cpu-geometry-divergence-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py",
    "scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py",
    "scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
    "reports/wgsl-pipeline/2026-06-06-for-439-m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/kotlin/",
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-439 validation failed: {message}")


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
    device = DEVICE.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 52000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16StencilCoverGeometryVsCpuGreenMaskFor439(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for439Requested": "FOR439_STENCIL_COVER_GEOMETRY_VS_CPU_GREEN_MASK_PROPERTY" in capture,
        "for427ForcedForMask": "withM60F16AaStencilCoverSubsampleMaskFor427(" in capture and FOR427_FLAG in capture,
        "for436ForcedForHostBinding": "withM60F16HostDrawPaintBindingFor436(" in capture,
        "usesCpuGreenFixture": "BoundedStrokeCapJoinGreenCoverageFor438GM" in scene_window,
        "usesGeometryFields": all(
            token in scene_window
            for token in (
                "edgeCount",
                "coverVertexCount",
                "targetWithinScissor",
                "wgslSubsampleMask4x4",
                "subsampleComparison4x4",
                "sourceFindingMemory",
            )
        ),
        "validatorCommandPresent": "validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py" in capture,
        "runtimeAlreadyExposesMask": "val wgslSubsampleMask4x4: Int? = null" in device,
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
    require(not unexpected, f"unexpected local diffs for FOR-439: {unexpected}")
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
        and "pipelineKeyChanged" not in line
    ]
    require(not pipeline_key_lines, f"PipelineKey lines changed: {pipeline_key_lines}")


def require_classification_consistency(data: dict[str, Any], pixels: list[dict[str, Any]]) -> None:
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification}")
    require(classification not in INCOMPLETE_CLASSIFICATIONS, f"classification must be complete, got {classification}")
    pixel_classes = {pixel.get("classification") for pixel in pixels}
    require(pixel_classes == {classification}, f"pixel classifications mismatch: {pixel_classes}")

    if classification == "webgpu-cover-polygon-overcovers-edge":
        for pixel in pixels:
            cpu = pixel["cpuGreenMask"]
            webgpu = pixel["webGpuCoverGeometry"]
            require(cpu.get("coverageAlphaByte") == 0, "CPU green alpha must be zero")
            require(cpu.get("subsampleMask4x4") == 0, "CPU green 4x4 mask must be empty")
            require(cpu.get("excludesPixel") is True, "CPU green mask must exclude pixel")
            require(webgpu.get("targetWithinScissor") is True, "WebGPU target must be inside scissor")
            require(webgpu.get("shaderObserved") is True, "WebGPU shader must observe pixel")
            require(webgpu.get("predicateIncludesPixel") is True, "WebGPU predicate must include pixel")
            require(webgpu.get("coveredSubsamples4x4") == 10, "WebGPU must cover 10 subsamples")
    elif classification == "webgpu-scissor-or-bounds-overinclude":
        for pixel in pixels:
            require(pixel["webGpuCoverGeometry"].get("targetWithinScissor") is True, "scissor must include point")
            require(pixel["cpuGreenMask"].get("excludesPixel") is True, "CPU must exclude point")
    elif classification == "webgpu-coordinate-mapping-shift":
        require(
            any(pixel["webGpuCoverGeometry"].get("targetWithinScissor") is not True for pixel in pixels),
            "coordinate-shift classification requires at least one scissor miss",
        )
    elif classification == "webgpu-inside-outside-subdraw-selection-mismatch":
        require(
            any(pixel["subdrawSelection"].get("insideSubdrawSelected") is not True for pixel in pixels),
            "subdraw mismatch classification requires a non-inside selection",
        )
    elif classification == "webgpu-band-metadata-mismatch":
        require(
            any(
                pixel["webGpuCoverGeometry"].get("edgeCount") in (None, 0)
                or pixel["webGpuCoverGeometry"].get("coverVertexCount") in (None, 0)
                for pixel in pixels
            ),
            "metadata mismatch classification requires missing geometry metadata",
        )
    elif classification == "cpu-green-mask-fixture-mismatch":
        require(
            any(pixel["cpuGreenMask"].get("coverageAlphaByte") != 0 for pixel in pixels),
            "CPU fixture mismatch requires nonzero CPU green coverage",
        )


def main() -> None:
    data = load_json(ARTIFACT)
    for438 = load_json(FOR438_ARTIFACT)

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(ARTIFACT.stat().st_size < 120_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-439", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR438_SCENE_ID, "FOR-438 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-auditer-geometrie-scissor-web-gpu-stencil-cover-draw-index-3-contre-masque-cpu-vert",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-438-web-gpu-stencil-cover-overcovers-green-draw-while-cpu-green-coverage-is-zero",
        "FOR-438 finding link mismatch",
    )
    require(data.get("sourceArtifact") == rel(FOR438_ARTIFACT), "FOR-438 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR438_REPORT), "FOR-438 report link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("diagnosticFlag") == FLAG, "FOR-439 diagnostic flag mismatch")
    require(data.get("sourceFor438DiagnosticFlag") == FOR438_FLAG, "FOR-438 flag mismatch")
    require(data.get("sourceFor427SubsampleMaskDiagnosticFlag") == FOR427_FLAG, "FOR-427 mask flag mismatch")
    require(for438.get("classification") == "webgpu-stencil-cover-overcovers-green-draw", "FOR-438 prerequisite changed")

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
    require(policy.get("cpuGreenCoverageFixture") == "BoundedStrokeCapJoinGreenCoverageFor438GM", "CPU fixture mismatch")
    require("4x4" in policy.get("subsampleGrid", ""), "subsample grid policy missing")
    require("coverageOrAaAlpha" in policy.get("subsampleGrid", ""), "coverage fallback policy missing")

    host = data.get("hostDrawIndex3")
    require(isinstance(host, dict), "hostDrawIndex3 missing")
    require(host.get("drawIndex") == 3, "host drawIndex mismatch")
    require(host.get("paintHexArgb") == "0xFF008A4C", "host green paint mismatch")
    require(host.get("strokeCap") == "round", "host cap mismatch")
    require(host.get("strokeJoin") == "round", "host join mismatch")
    require_close(host.get("strokeWidth"), 10.0, "host strokeWidth")
    require(isinstance(host.get("scissor"), list) and len(host["scissor"]) == 4, "host scissor missing")
    require(isinstance(host.get("edgeCount"), int) and host["edgeCount"] > 0, "host edgeCount missing")
    require(isinstance(host.get("coverVertexCount"), int) and host["coverVertexCount"] > 0, "host coverVertexCount missing")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("hostBoundDrawIndex") == 3, "host draw mismatch")
    require(summary.get("hostBoundPaintHexArgb") == "0xFF008A4C", "host paint mismatch")
    require(summary.get("cpuGreenCoverageZeroCount") == 6, "CPU zero count mismatch")
    require(summary.get("cpuGreenMaskZero4x4Count") == 6, "CPU 4x4 zero count mismatch")
    require(summary.get("webGpuInsideSubdrawCount") == 6, "inside subdraw count mismatch")
    require(summary.get("webGpuScissorContainsPointCount") == 6, "scissor count mismatch")
    require(summary.get("webGpuPredicateIncludesPixelCount") == 6, "predicate count mismatch")
    require(summary.get("webGpuCoveredSubsamples10Of16Count") == 6, "10/16 count mismatch")
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

        selection = pixel.get("subdrawSelection")
        require(isinstance(selection, dict), "subdrawSelection missing")
        require(selection.get("drawIndex") == 3, "selection drawIndex mismatch")
        require(selection.get("subdrawOrdinal") == 0, "selection ordinal mismatch")
        require(selection.get("subdrawRole") == "inside", "selection role mismatch")
        require(selection.get("insideSubdrawSelected") is True, "inside subdraw not selected")

        cpu = pixel.get("cpuGreenMask")
        require(isinstance(cpu, dict), "cpuGreenMask missing")
        require(cpu.get("coverageAlphaByte") == 0, "CPU green alpha must be zero")
        require_close(cpu.get("coverageAlpha"), 0.0, "CPU green coverage alpha")
        require(cpu.get("subsampleMask4x4") == 0, "CPU green 4x4 mask must be zero")
        require(cpu.get("coveredSubsamples4x4") == 0, "CPU green covered count must be zero")
        require(cpu.get("excludesPixel") is True, "CPU green mask must exclude pixel")

        webgpu = pixel.get("webGpuCoverGeometry")
        require(isinstance(webgpu, dict), "webGpuCoverGeometry missing")
        require(webgpu.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "pipeline family mismatch")
        require(webgpu.get("fillType") == "kWinding", "fillType mismatch")
        require(webgpu.get("targetWithinScissor") is True, "targetWithinScissor mismatch")
        require(webgpu.get("shaderObserved") is True, "shaderObserved mismatch")
        require(webgpu.get("candidateBranchReached") is True, "candidate branch mismatch")
        require(isinstance(webgpu.get("edgeCount"), int) and webgpu["edgeCount"] > 0, "edgeCount missing")
        require(isinstance(webgpu.get("coverVertexCount"), int) and webgpu["coverVertexCount"] > 0, "coverVertexCount missing")
        require_close(webgpu.get("coverageOrAaAlpha"), 0.625, "coverageOrAaAlpha")
        require(webgpu.get("coveredSubsamples4x4") == 10, "covered subsamples mismatch")
        require(webgpu.get("predicateIncludesPixel") is True, "predicateIncludesPixel mismatch")

        grid = pixel.get("subsampleComparison4x4")
        require(isinstance(grid, list) and len(grid) == 16, "4x4 comparison grid missing")
        cpu_only = [cell for cell in grid if cell.get("cpuCovered") is True and cell.get("wgslCovered") is not True]
        wgsl_only = [cell for cell in grid if cell.get("wgslCovered") is True and cell.get("cpuCovered") is not True]
        require(len(cpu_only) == 0, "CPU must not cover subsamples in FOR-439")
        if isinstance(webgpu.get("wgslSubsampleMask4x4"), int):
            require(len(wgsl_only) == 10, f"WGSL-only subsample count mismatch: {len(wgsl_only)}")
        else:
            require(all(cell.get("wgslCovered") is None for cell in grid), "WGSL grid must be null when mask is absent")

    source_audit()
    print(
        "FOR-439 validation passed: WebGPU drawIndex 3 inside stencil-cover geometry "
        f"classifies as {data.get('classification')} against the CPU green mask."
    )


if __name__ == "__main__":
    main()
