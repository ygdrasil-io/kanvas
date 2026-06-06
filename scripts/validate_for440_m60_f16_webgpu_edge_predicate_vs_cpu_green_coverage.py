#!/usr/bin/env python3
"""Validate FOR-440 M60 F16 WebGPU edge predicate vs CPU green coverage evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage-for440"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-440-m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage.md"
FOR439_SCENE_ID = "m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439"
FOR439_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR439_SCENE_ID / f"{FOR439_SCENE_ID}.json"
FOR439_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-439-m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16EdgePredicateVsCpuGreenCoverageFor440.enabled"
FOR439_FLAG = "kanvas.webgpu.m60F16StencilCoverGeometryVsCpuGreenMaskFor439.enabled"
FOR427_FLAG = "kanvas.webgpu.m60F16AaStencilCoverSubsampleMaskFor427.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
ALLOWED_CLASSIFICATIONS = {
    "webgpu-edge-predicate-overincludes-cpu-excluded-samples",
    "webgpu-cover-polygon-vertex-expansion-overincludes",
    "webgpu-winding-or-orientation-mismatch",
    "webgpu-coordinate-rounding-shift",
    "cpu-stroke-coverage-rule-needs-export",
    "trace-incomplete",
    "webgpu-edge-predicate-unresolved",
}
INCOMPLETE_CLASSIFICATIONS = {
    "trace-incomplete",
    "webgpu-edge-predicate-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py",
    "scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
    "scripts/validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py",
    "scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
    "scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
    "reports/wgsl-pipeline/2026-06-06-for-440-m60-f16-webgpu-edge-predicate-vs-cpu-green-coverage.md",
    "reports/wgsl-pipeline/2026-06-06-for-441-m60-f16-webgpu-exact-subsample-mask-vs-cpu-green.md",
    "reports/wgsl-pipeline/2026-06-06-for-442-m60-f16-webgpu-runtime-exact-mask-probe.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441/m60-f16-webgpu-exact-subsample-mask-vs-cpu-green-for441.json",
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
    raise SystemExit(f"FOR-440 validation failed: {message}")


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
    shader = SHADER.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 62000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16EdgePredicateVsCpuGreenCoverageFor440(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for440Requested": "FOR440_EDGE_PREDICATE_VS_CPU_GREEN_COVERAGE_PROPERTY" in capture,
        "coverageCountExported": "coveredSubsamples4x4" in scene_window and "coverageDerivedCoveredSubsamples" in scene_window,
        "for439SourceLinked": "for-439-web-gpu-cover-polygon-overcovers-cpu-green-excluded" in scene_window,
        "usesCpuGreenFixture": "BoundedStrokeCapJoinGreenCoverageFor438GM" in scene_window,
        "usesEdgePredicateFields": all(
            token in scene_window
            for token in (
                "webGpuSubsampleMask4x4",
                "coverageDerivedCoveredSubsamples",
                "subsampleEdgePredicate4x4",
                "webGpuEdgePredicate",
                "sourceDraftMemory",
                "sourceFindingMemory",
            )
        ),
        "shaderPredicateExists": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
        "validatorCommandPresent": "validate_for440_m60_f16_webgpu_edge_predicate_vs_cpu_green_coverage.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for FOR-440: {unexpected}")
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


def require_classification_consistency(data: dict[str, Any], pixels: list[dict[str, Any]]) -> None:
    classification = data.get("classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"unexpected classification: {classification}")
    require(classification not in INCOMPLETE_CLASSIFICATIONS, f"classification must be complete, got {classification}")
    pixel_classes = {pixel.get("classification") for pixel in pixels}
    require(pixel_classes == {classification}, f"pixel classifications mismatch: {pixel_classes}")

    if classification == "webgpu-edge-predicate-overincludes-cpu-excluded-samples":
        for pixel in pixels:
            cpu = pixel["cpuGreenMask"]
            webgpu = pixel["webGpuEdgePredicate"]
            relation = pixel["coverageRelation"]
            require(cpu.get("subsampleMask4x4") == 0, "CPU mask must be empty")
            require(cpu.get("coveredSubsamples4x4") == 0, "CPU covered count must be zero")
            require(webgpu.get("coveredSubsamples4x4") == 10, "WebGPU must include 10 subsamples")
            require(webgpu.get("predicateIncludesPixel") is True, "WebGPU predicate must include pixel")
            require(relation.get("coverageNumeratorOf16") == 10, "coverage numerator mismatch")
            require(relation.get("coveredSubsamples4x4") == 10, "covered subsample relation mismatch")
            require(relation.get("exactSubsampleMaskAvailable") is False, "exact mask should be declared unavailable")
            require(relation.get("matchesCoverageCount") is True, "coverage must match covered subsample count")
    elif classification == "webgpu-cover-polygon-vertex-expansion-overincludes":
        require(any(pixel["webGpuEdgePredicate"].get("coverVertexCount") in (None, 0) for pixel in pixels), "vertex expansion classification requires empty cover geometry")
    elif classification == "webgpu-winding-or-orientation-mismatch":
        require(any(pixel["webGpuEdgePredicate"].get("subdrawRole") != "inside" for pixel in pixels), "winding classification requires non-inside role")
    elif classification == "webgpu-coordinate-rounding-shift":
        require(any(pixel["webGpuEdgePredicate"].get("targetWithinScissor") is not True for pixel in pixels), "coordinate classification requires scissor miss")
    elif classification == "cpu-stroke-coverage-rule-needs-export":
        require(any(pixel["cpuGreenMask"].get("coverageAlphaByte") != 0 for pixel in pixels), "CPU export classification requires nonzero CPU green coverage")


def main() -> None:
    data = load_json(ARTIFACT)
    for439 = load_json(FOR439_ARTIFACT)

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(ARTIFACT.stat().st_size < 130_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-440", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR439_SCENE_ID, "FOR-439 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-auditer-predicat-arete-web-gpu-stencil-cover-aa-polygon-draw-contre-couverture-cpu-trait-vert",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-439-web-gpu-cover-polygon-overcovers-cpu-green-excluded-m60-f16-pixels",
        "FOR-439 finding link mismatch",
    )
    require(data.get("sourceArtifact") == rel(FOR439_ARTIFACT), "FOR-439 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR439_REPORT), "FOR-439 report link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("diagnosticFlag") == FLAG, "FOR-440 diagnostic flag mismatch")
    require(data.get("sourceFor439DiagnosticFlag") == FOR439_FLAG, "FOR-439 flag mismatch")
    require(data.get("sourceFor427SubsampleMaskDiagnosticFlag") == FOR427_FLAG, "FOR-427 flag mismatch")
    require(for439.get("classification") == "webgpu-cover-polygon-overcovers-edge", "FOR-439 prerequisite changed")

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
    require("supersampled_path_cov" in policy.get("webGpuPredicateFormula", ""), "predicate formula missing")
    require("coverageOrAaAlpha 0.625 equals 10 / 16" in policy.get("coverageRelation", ""), "coverage relation missing")

    host = data.get("hostDrawIndex3")
    require(isinstance(host, dict), "hostDrawIndex3 missing")
    require(host.get("drawIndex") == 3, "host drawIndex mismatch")
    require(host.get("paintHexArgb") == "0xFF008A4C", "host green paint mismatch")
    require(host.get("strokeCap") == "round", "host cap mismatch")
    require(host.get("strokeJoin") == "round", "host join mismatch")
    require_close(host.get("strokeWidth"), 10.0, "host strokeWidth")
    require(isinstance(host.get("edgeCount"), int) and host["edgeCount"] > 0, "host edgeCount missing")
    require(isinstance(host.get("coverVertexCount"), int) and host["coverVertexCount"] > 0, "host coverVertexCount missing")
    require(host.get("coverSubdrawRoles") == ["inside", "outside"], "cover subdraw roles mismatch")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("hostBoundDrawIndex") == 3, "host draw mismatch")
    require(summary.get("hostBoundPaintHexArgb") == "0xFF008A4C", "host paint mismatch")
    require(summary.get("cpuGreenCoverageZeroCount") == 6, "CPU zero count mismatch")
    require(summary.get("cpuGreenMaskZero4x4Count") == 6, "CPU 4x4 zero count mismatch")
    require(summary.get("webGpuMaskAvailableCount") == 0, "WebGPU exact mask availability mismatch")
    require(summary.get("webGpuInsideSubdrawCount") == 6, "inside subdraw count mismatch")
    require(summary.get("webGpuScissorContainsPointCount") == 6, "scissor count mismatch")
    require(summary.get("webGpuPredicateIncludesPixelCount") == 6, "predicate count mismatch")
    require(summary.get("webGpuCoveredSubsamples10Of16Count") == 6, "10/16 count mismatch")
    require(summary.get("coverageRelationMatchesMaskCount") == 6, "coverage/count relation mismatch")
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

        coords = pixel.get("coordinates")
        require(isinstance(coords, dict), "coordinates missing")
        require(coords.get("wgslFragmentCoordinateSource") == "@builtin(position) frag.xy", "coordinate source mismatch")

        cpu = pixel.get("cpuGreenMask")
        require(isinstance(cpu, dict), "cpuGreenMask missing")
        require(cpu.get("coverageAlphaByte") == 0, "CPU green alpha must be zero")
        require_close(cpu.get("coverageAlpha"), 0.0, "CPU green coverage alpha")
        require(cpu.get("subsampleMask4x4") == 0, "CPU green 4x4 mask must be zero")
        require(cpu.get("coveredSubsamples4x4") == 0, "CPU green covered count must be zero")
        require(cpu.get("excludesPixel") is True, "CPU green mask must exclude pixel")

        webgpu = pixel.get("webGpuEdgePredicate")
        require(isinstance(webgpu, dict), "webGpuEdgePredicate missing")
        require(webgpu.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "pipeline family mismatch")
        require(webgpu.get("fillType") == "kWinding", "fillType mismatch")
        require(webgpu.get("subdrawOrdinal") == 0, "subdraw ordinal mismatch")
        require(webgpu.get("subdrawRole") == "inside", "subdraw role mismatch")
        require(webgpu.get("targetWithinScissor") is True, "targetWithinScissor mismatch")
        require(webgpu.get("shaderObserved") is True, "shaderObserved mismatch")
        require(webgpu.get("candidateBranchReached") is True, "candidate branch mismatch")
        require(isinstance(webgpu.get("edgeCount"), int) and webgpu["edgeCount"] > 0, "edgeCount missing")
        require(isinstance(webgpu.get("coverVertexCount"), int) and webgpu["coverVertexCount"] > 0, "coverVertexCount missing")
        require_close(webgpu.get("coverageOrAaAlpha"), 0.625, "coverageOrAaAlpha")
        require(webgpu.get("coverageDerivedCoveredSubsamples4x4") == 10, "coverage-derived count mismatch")
        require(webgpu.get("coveredSubsamples4x4") == 10, "covered subsamples mismatch")
        require(webgpu.get("predicateIncludesPixel") is True, "predicateIncludesPixel mismatch")
        require(webgpu.get("wgslSubsampleMask4x4") is None, "WGSL exact subsample mask should be unavailable")

        relation = pixel.get("coverageRelation")
        require(isinstance(relation, dict), "coverageRelation missing")
        require_close(relation.get("coverageOrAaAlpha"), 0.625, "relation coverage")
        require(relation.get("coverageNumeratorOf16") == 10, "relation numerator mismatch")
        require(relation.get("maskPopcount") is None, "relation mask popcount should be unavailable")
        require(relation.get("coveredSubsamples4x4") == 10, "relation covered count mismatch")
        require(relation.get("exactSubsampleMaskAvailable") is False, "relation exact mask availability mismatch")
        require(relation.get("matchesCoverageCount") is True, "relation must match")

        grid = pixel.get("subsampleEdgePredicate4x4")
        require(isinstance(grid, list) and len(grid) == 16, "edge predicate grid missing")
        cpu_only = [cell for cell in grid if cell.get("cpuGreenCovered") is True]
        require(len(cpu_only) == 0, "CPU green must not cover subsamples")
        require(all(cell.get("webGpuEdgePredicateCovered") is None for cell in grid), "exact WebGPU subsample cells must be null")
        require(all(cell.get("divergent") is None for cell in grid), "exact divergence cells must be null")

    source_audit()
    print(
        "FOR-440 validation passed: WebGPU edge predicate evidence classifies as "
        f"{data.get('classification')} against the CPU green mask."
    )


if __name__ == "__main__":
    main()
