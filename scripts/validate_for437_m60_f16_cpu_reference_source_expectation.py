#!/usr/bin/env python3
"""Validate FOR-437 M60 F16 CPU/reference source expectation diagnostic evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-cpu-reference-source-expectation-for437"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-437-m60-f16-cpu-reference-source-expectation.md"
FOR436_SCENE_ID = "m60-f16-host-draw-paint-binding-for436"
FOR436_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR436_SCENE_ID / f"{FOR436_SCENE_ID}.json"
FOR436_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-436-m60-f16-host-draw-paint-binding.md"
FOR435_ARTIFACT = (
    ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-paint-stroke-input-trace-for435/"
    "m60-f16-paint-stroke-input-trace-for435.json"
)
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16CpuReferenceSourceExpectationFor437.enabled"
FOR436_FLAG = "kanvas.webgpu.m60F16HostDrawPaintBindingFor436.enabled"
FOR435_FLAG = "kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
EXPECTED_CLASSIFICATION = "cpu-reference-source-derived-from-different-draw"
ALLOWED_CLASSIFICATIONS = {
    "cpu-reference-source-is-fixture-blue",
    "cpu-reference-source-derived-from-different-draw",
    "cpu-reference-composition-inversion-mismatch",
    "fixture-expectation-mismatch",
    "trace-incomplete",
    "cpu-reference-source-expectation-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "scripts/validate_for435_m60_f16_paint_stroke_input_trace.py",
    "scripts/validate_for436_m60_f16_host_draw_paint_binding.py",
    "scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py",
    "scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py",
    "scripts/validate_for439_m60_f16_webgpu_stencil_cover_geometry_vs_cpu_green_mask.py",
    "reports/wgsl-pipeline/2026-06-06-for-437-m60-f16-cpu-reference-source-expectation.md",
    "reports/wgsl-pipeline/2026-06-06-for-438-m60-f16-cpu-vs-webgpu-green-draw-coverage.md",
    "reports/wgsl-pipeline/2026-06-06-for-439-m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439/m60-f16-webgpu-stencil-cover-geometry-vs-cpu-green-mask-for439.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/kotlin/",
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-437 validation failed: {message}")


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
    scene_window = capture[scene_index : scene_index + 40000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16CpuReferenceSourceExpectationFor437(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build,
        "for436ForcedForSnapshot": "withM60F16HostDrawPaintBindingFor436(" in capture and FOR436_FLAG in capture,
        "cpuPrefixFixturePresent": "BoundedStrokeCapJoinPrefixFor437GM" in capture,
        "usesCpuPrefixFields": all(
            token in scene_window
            for token in (
                "cpuReferenceBeforeDrawIndex3Rgba",
                "cpuReferencePrefixMatchCount",
                "sourceDraftMemory",
                "sourceFindingMemory",
            )
        ),
        "validatorCommandPresent": "validate_for437_m60_f16_cpu_reference_source_expectation.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for FOR-437: {unexpected}")
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


def main() -> None:
    data = load_json(ARTIFACT)
    for436 = load_json(FOR436_ARTIFACT)
    load_json(FOR435_ARTIFACT)

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(ARTIFACT.stat().st_size < 90_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-437", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR436_SCENE_ID, "FOR-436 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-attente-source-cpu-reference-pour-pixels-draw-index-3",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-436-web-gpu-host-draw-paint-binding-trace-classifies-cpu-reference-source-expectation-mismatch",
        "FOR-436 finding link missing",
    )
    require(data.get("sourceArtifact") == rel(FOR436_ARTIFACT), "FOR-436 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR436_REPORT), "FOR-436 report link mismatch")
    require(data.get("sourceFor435Artifact") == rel(FOR435_ARTIFACT), "FOR-435 artifact link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")
    require(data.get("diagnosticFlag") == FLAG, "FOR-437 diagnostic flag mismatch")
    require(data.get("sourceFor436DiagnosticFlag") == FOR436_FLAG, "FOR-436 flag mismatch")
    require(data.get("sourceFor435DiagnosticFlag") == FOR435_FLAG, "FOR-435 flag mismatch")
    require(for436.get("classification") == "cpu-reference-source-expects-different-draw", "FOR-436 prerequisite changed")

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
    require("BoundedStrokeCapJoinPrefixFor437GM" in policy.get("cpuReferencePrefixBeforeDrawIndex3", ""), "CPU prefix policy missing")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(summary.get("hostBindingEventCount") == 3, "host binding count mismatch")
    require(summary.get("hostBoundDrawIndex") == 3, "host draw mismatch")
    require(summary.get("hostBoundPaintHexArgb") == "0xFF008A4C", "host paint mismatch")
    require(summary.get("hostBoundStrokeCap") == "round", "host cap mismatch")
    require(summary.get("hostBoundStrokeJoin") == "round", "host join mismatch")
    require(summary.get("bestFixtureCandidateDrawIndex") == 1, "best candidate draw mismatch")
    require(summary.get("bestFixtureCandidatePaintHexArgb") == "0xFF0066CC", "best candidate paint mismatch")
    require(summary.get("cpuReferencePrefixMatchCount") == 6, "CPU prefix match count mismatch")
    require_number(summary.get("maxRequiredPaintVsFixtureBlueTargetDelta"), "max blue delta")
    require_number(summary.get("maxRequiredPaintVsHostBoundTargetDelta"), "max host delta")
    require(summary.get("traceComplete") is True, "trace must be complete")

    candidates = data.get("fixtureSourceCandidates")
    require(isinstance(candidates, list) and len(candidates) == 3, "fixtureSourceCandidates must contain three candidates")
    by_draw = {candidate.get("drawIndex"): candidate for candidate in candidates if isinstance(candidate, dict)}
    require(set(by_draw) == {1, 3, 5}, f"candidate draw set mismatch: {set(by_draw)}")
    require_rgba(by_draw[1].get("paintRgba8"), [0, 102, 204, 255], "draw1 paint")
    require_rgba(by_draw[3].get("paintRgba8"), [0, 138, 76, 255], "draw3 paint")
    require_rgba(by_draw[5].get("paintRgba8"), [179, 60, 0, 255], "draw5 paint")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    seen_points = {(pixel.get("x"), pixel.get("y")) for pixel in pixels if isinstance(pixel, dict)}
    require(seen_points == EXPECTED_POINTS, f"partial point set mismatch: {seen_points}")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        require(pixel.get("effectiveRenderDrawIndex") == 3, "effective draw mismatch")
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], "referenceCpuRgba")
        require_rgba(pixel.get("cpuReferenceBeforeDrawIndex3Rgba"), [133, 150, 214, 255], "CPU prefix rgba")
        require(pixel.get("cpuReferenceEqualsPrefixBeforeDrawIndex3") is True, "CPU prefix match must be true")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], "currentWebGpuRgba")
        require_rgba(pixel.get("optInFor431Rgba"), [111, 147, 129, 255], "optInFor431Rgba")

        coverage = pixel.get("coverage")
        require(isinstance(coverage, dict), "coverage missing")
        require(coverage.get("numerator") == 10, "pixel coverage numerator mismatch")
        require(coverage.get("denominator") == 16, "pixel coverage denominator mismatch")
        require_close(coverage.get("alpha"), 0.625, "pixel coverage alpha")
        require_close(coverage.get("capturedCoverageOrAaAlpha"), 0.625, "captured alpha")

        required_payload = pixel.get("requiredPaintPayloadBeforeCoverage")
        require(isinstance(required_payload, dict), "required payload missing")
        require_rgba(required_payload.get("rgba8Approx"), [104, 125, 204, 255], "required payload")

        selected = pixel.get("selectedWebGpuSource")
        require(isinstance(selected, dict), "selected WebGPU source missing")
        require(selected.get("drawIndex") == 3, "selected WebGPU draw mismatch")
        require(selected.get("subdrawOrdinal") == 0, "selected WebGPU subdraw mismatch")
        require(selected.get("subdrawRole") == "inside", "selected WebGPU role mismatch")

        best = pixel.get("bestFixtureSourceCandidate")
        require(isinstance(best, dict), "best fixture candidate missing")
        require(best.get("drawIndex") == 1, "best fixture candidate draw mismatch")
        require(best.get("paintHexArgb") == "0xFF0066CC", "best fixture candidate paint mismatch")

        require(pixel.get("missingFields") == [], "pixel trace must be complete")

    source_audit()
    print(
        "FOR-437 validation passed: CPU reference six pixels match the pre-drawIndex-3 "
        "blue prefix while WebGPU traces drawIndex 3 green coverage."
    )


if __name__ == "__main__":
    main()
