#!/usr/bin/env python3
"""Validate FOR-436 M60 F16 host draw-to-paint binding diagnostic evidence."""

from __future__ import annotations

import json
import math
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-host-draw-paint-binding-for436"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-436-m60-f16-host-draw-paint-binding.md"
FOR435_SCENE_ID = "m60-f16-paint-stroke-input-trace-for435"
FOR435_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR435_SCENE_ID / f"{FOR435_SCENE_ID}.json"
FOR435_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-435-m60-f16-paint-stroke-input-trace.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"

FLAG = "kanvas.webgpu.m60F16HostDrawPaintBindingFor436.enabled"
FOR435_FLAG = "kanvas.webgpu.m60F16PaintStrokeInputTraceFor435.enabled"
EXPECTED_POINTS = {(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)}
EXPECTED_CLASSIFICATION = "cpu-reference-source-expects-different-draw"
ALLOWED_CLASSIFICATIONS = {
    "host-draw-paint-binding-mismatch",
    "cpu-reference-source-expects-different-draw",
    "draw-index-remap-mismatch",
    "fixture-expectation-mismatch",
    "trace-incomplete",
    "paint-binding-origin-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for434_m60_f16_stencil_source_payload_trace.py",
    "scripts/validate_for435_m60_f16_paint_stroke_input_trace.py",
    "scripts/validate_for436_m60_f16_host_draw_paint_binding.py",
    "scripts/validate_for437_m60_f16_cpu_reference_source_expectation.py",
    "scripts/validate_for438_m60_f16_cpu_vs_webgpu_green_draw_coverage.py",
    "reports/wgsl-pipeline/2026-06-06-for-436-m60-f16-host-draw-paint-binding.md",
    "reports/wgsl-pipeline/2026-06-06-for-437-m60-f16-cpu-reference-source-expectation.md",
    "reports/wgsl-pipeline/2026-06-06-for-438-m60-f16-cpu-vs-webgpu-green-draw-coverage.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-reference-source-expectation-for437/m60-f16-cpu-reference-source-expectation-for437.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438/m60-f16-cpu-vs-webgpu-green-draw-coverage-for438.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-436 validation failed: {message}")


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
    device = DEVICE.read_text(encoding="utf-8")
    sink = WEBGPU_SINK.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    scene_index = capture.find(SCENE_ID)
    scene_window = capture[scene_index : scene_index + 26000] if scene_index >= 0 else ""
    checks = {
        "writerCalled": "writeM60F16HostDrawPaintBindingFor436(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in device and FLAG in build,
        "deviceRecordsHostBinding": "recordM60F16HostDrawPaintBindingFor436(" in device,
        "sinkExportsSnapshot": "hostDrawPaintBindingFor436Snapshot" in sink,
        "usesHostBindingFields": all(
            token in scene_window
            for token in (
                "sourcePaintHexArgb",
                "strokeBandId",
                "coverSubdrawRoles",
                "selectedHostDrawIndex",
                "sourceFindingMemory",
            )
        ),
        "doesNotTouchPipelineKey": "PipelineKey" not in scene_window,
        "validatorCommandPresent": "validate_for436_m60_f16_host_draw_paint_binding.py" in capture,
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
    require(not unexpected, f"unexpected local diffs for FOR-436: {unexpected}")
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


def require_selected_binding(binding: dict[str, Any]) -> None:
    require(binding.get("drawIndex") == 3, "selected drawIndex mismatch")
    require(binding.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "pipeline family mismatch")
    require(binding.get("route") == "solid-color-aa-stencil-cover", "route mismatch")
    require(binding.get("fillType") == "kWinding", "fill type mismatch")
    require(binding.get("blendMode") == "kSrcOver", "blend mode mismatch")
    require(binding.get("scissor") == [0, 0, 192, 128], "scissor mismatch")
    require(binding.get("edgeCount") == 39, "edge count mismatch")
    require(binding.get("coverVertexCount") == 6, "cover vertex count mismatch")
    require_rgba(binding.get("sourcePaintRgba8"), [0, 138, 76, 255], "sourcePaintRgba8")
    require(binding.get("sourcePaintHexArgb") == "0xFF008A4C", "source paint hex mismatch")

    stroke = binding.get("stroke")
    require(isinstance(stroke, dict), "stroke missing")
    require_close(stroke.get("width"), 10.0, "stroke.width")
    require(stroke.get("cap") == "round", "stroke cap mismatch")
    require(stroke.get("join") == "round", "stroke join mismatch")

    band = binding.get("bandMetadata")
    require(isinstance(band, dict), "band metadata missing")
    require_close(band.get("bandXStart"), 48.0, "bandXStart")
    require_close(band.get("bandXEnd"), 96.0, "bandXEnd")
    require_close(band.get("strokeBandId"), 2.0, "strokeBandId")
    require_close(band.get("capId"), 2.0, "capId")
    require_close(band.get("joinId"), 2.0, "joinId")

    runtime = binding.get("runtime")
    require(isinstance(runtime, dict), "runtime missing")
    require(runtime.get("targetColorSpaceBlend") is True, "targetColorSpaceBlend must be true")
    require(runtime.get("intermediateFormat") == "RGBA16Float", "intermediate format mismatch")
    require(runtime.get("sourceColorCorrectionProbe") is False, "source correction probe must be false")
    require(runtime.get("boundedRuntimeCorrectionProbe") is False, "bounded correction probe must be false")
    require(runtime.get("widthQuantizedRenderFixFor431") is True, "FOR-431 opt-in must be true in diagnostic replay")
    require(runtime.get("coverSubdrawRoles") == ["inside", "outside"], "cover subdraw roles mismatch")
    require(binding.get("classification") == EXPECTED_CLASSIFICATION, "selected binding classification mismatch")


def main() -> None:
    data = load_json(ARTIFACT)
    for435 = load_json(FOR435_ARTIFACT)

    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    require(ARTIFACT.stat().st_size < 80_000, "artifact must stay bounded")
    require(data.get("schemaVersion") == 1, "schema version mismatch")
    require(data.get("linear") == "FOR-436", "Linear id mismatch")
    require(data.get("sceneId") == SCENE_ID, "scene id mismatch")
    require(data.get("sourceSceneId") == FOR435_SCENE_ID, "FOR-435 source scene mismatch")
    require(
        data.get("sourceDraftMemory")
        == "global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-lier-draw-hote-et-paint-selectionne-pour-aa-stencil-cover-draw-index-3",
        "source draft memory link mismatch",
    )
    require(
        data.get("sourceFindingMemory")
        == "global/kanvas/findings/for-435-web-gpu-paint-stroke-input-trace-identifies-host-paint-input-mismatch",
        "FOR-435 finding link missing",
    )
    require(data.get("sourceArtifact") == rel(FOR435_ARTIFACT), "FOR-435 artifact link mismatch")
    require(data.get("sourceReport") == rel(FOR435_REPORT), "FOR-435 report link mismatch")
    require(set(data.get("allowedClassifications", [])) == ALLOWED_CLASSIFICATIONS, "allowed classifications mismatch")
    require(data.get("classification") == EXPECTED_CLASSIFICATION, "unexpected classification")
    require(data.get("diagnosticFlag") == FLAG, "FOR-436 diagnostic flag mismatch")
    require(data.get("sourceFor435DiagnosticFlag") == FOR435_FLAG, "FOR-435 flag mismatch")
    require(for435.get("classification") == "host-paint-input-mismatch", "FOR-435 prerequisite changed")

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
    require(policy.get("hostSnapshotProperty") == FLAG, "host snapshot property mismatch")
    require(policy.get("hostSnapshotEnabled") is True, "host snapshot must be enabled")
    require(policy.get("noRenderingFixApplied") is True, "rendering fix policy mismatch")
    require(policy.get("boundedToSixPixels") is True, "six pixel bound missing")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partialPixelCount") == 6, "partial count mismatch")
    require(summary.get("expectedPartialPixelCount") == 6, "expected partial count mismatch")
    require(require_number(summary.get("hostBindingEventCount"), "hostBindingEventCount") >= 1, "missing host events")
    require(summary.get("selectedHostDrawIndex") == 3, "selected host draw mismatch")
    require(summary.get("selectedPaintHexArgb") == "0xFF008A4C", "selected paint mismatch")
    require_close(summary.get("selectedStrokeWidth"), 10.0, "selected stroke width")
    require(summary.get("selectedStrokeCap") == "round", "selected stroke cap mismatch")
    require(summary.get("selectedStrokeJoin") == "round", "selected stroke join mismatch")
    require_close(summary.get("selectedBandXStart"), 48.0, "selected band start")
    require_close(summary.get("selectedBandXEnd"), 96.0, "selected band end")
    require(summary.get("traceComplete") is True, "trace must be complete")

    bindings = data.get("hostDrawBindings")
    require(isinstance(bindings, list) and len(bindings) == 3, "hostDrawBindings must contain three events")
    selected_bindings = [binding for binding in bindings if isinstance(binding, dict) and binding.get("drawIndex") == 3]
    require(len(selected_bindings) == 1, "exactly one drawIndex 3 binding required")
    require_selected_binding(selected_bindings[0])

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six pixels")
    seen_points = {(pixel.get("x"), pixel.get("y")) for pixel in pixels if isinstance(pixel, dict)}
    require(seen_points == EXPECTED_POINTS, f"partial point set mismatch: {seen_points}")
    for pixel in pixels:
        require(isinstance(pixel, dict), "partial pixel must be object")
        require(pixel.get("expectedEffectiveDrawIndex") == 3, "expected effective draw mismatch")
        require(pixel.get("linkedHostDrawIndex") == 3, "linked host draw mismatch")
        require_rgba(pixel.get("referenceCpuRgba"), [133, 150, 214, 255], "referenceCpuRgba")
        require_rgba(pixel.get("currentWebGpuRgba"), [181, 191, 230, 255], "currentWebGpuRgba")
        require_rgba(pixel.get("optInFor431Rgba"), [111, 147, 129, 255], "optInFor431Rgba")
        require(pixel.get("classification") == EXPECTED_CLASSIFICATION, "pixel classification mismatch")

    source_audit()
    print(
        "FOR-436 validation passed: host binding is drawIndex 3 / 0xFF008A4C / "
        "round-round, while CPU reference still expects a bluer source."
    )


if __name__ == "__main__":
    main()
