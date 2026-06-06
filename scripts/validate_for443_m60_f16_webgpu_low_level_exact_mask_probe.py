#!/usr/bin/env python3
"""Validate FOR-443 M60 F16 WebGPU low-level exact mask probe evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-webgpu-low-level-exact-mask-probe-for443"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-443-m60-f16-webgpu-low-level-exact-mask-probe.md"
FOR442_SCENE_ID = "m60-f16-webgpu-runtime-exact-mask-probe-for442"
FOR442_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR442_SCENE_ID / f"{FOR442_SCENE_ID}.json"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
WEBGPU_SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled"
FOR442_FLAG = "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
EXPECTED_POINT_SET = set(EXPECTED_POINTS)
ALLOWED_CLASSIFICATIONS = {
    "webgpu-low-level-mask-overincludes-cpu-excluded-samples",
    "webgpu-low-level-mask-probe-unavailable",
    "webgpu-low-level-mask-count-mismatch",
    "webgpu-low-level-mask-coordinate-shift",
    "cpu-green-mask-fixture-mismatch",
    "trace-incomplete",
    "webgpu-low-level-mask-unresolved",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for441_m60_f16_webgpu_exact_subsample_mask_vs_cpu_green.py",
    "scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
    "scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py",
    "scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py",
    "reports/wgsl-pipeline/2026-06-06-for-443-m60-f16-webgpu-low-level-exact-mask-probe.md",
    "reports/wgsl-pipeline/2026-06-06-for-444-m60-f16-runtime-mask-packing-vs-low-level-probe.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-runtime-mask-packing-vs-low-level-probe-for444",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-runtime-mask-packing-vs-low-level-probe-for444/m60-f16-runtime-mask-packing-vs-low-level-probe-for444.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-443 validation failed: {message}")


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
    sink = WEBGPU_SINK.read_text(encoding="utf-8")
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16LowLevelExactMaskProbeFor443(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "sinkSnapshot": "lowLevelExactMaskProbeFor443Snapshot" in sink,
        "deviceSnapshot": "m60F16LowLevelExactMaskProbeFor443Snapshot" in device,
        "computeShader": all(
            token in device
            for token in (
                "m60F16LowLevelExactMaskProbeFor443Wgsl",
                "fn winding_at",
                "fn sample_covered",
                "m60_f16_subsample_mask_4x4",
                "dispatchWorkgroups(M60_F16_LOW_LEVEL_EXACT_MASK_PROBE_FOR443_SAMPLE_COUNT)",
            )
        ),
        "runtimeIndependent": "diagnostic compute shader reads the same AA uniform edge list" in device,
        "usesSourceFindingMemory": "for-442-web-gpu-runtime-exact-mask-probe-unavailable" in capture,
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-443: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

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
    dangerous_lines = [
        line
        for line in (capture_diff + "\n" + device_diff).splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
            or "PipelineKey" in line
            or ("fallbackPolicy" in line and '"fallbackPolicyChanged": false' not in line)
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey lines changed: {dangerous_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-443 diagnostic flag")
    require("0x0000" in text, "report must mention the low-level 0x0000 masks")
    require("FOR-442" in text, "report must mention FOR-442 source evidence")


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    source = load_json(FOR442_ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-443", "linear must be FOR-443")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("sourceSceneId") == FOR442_SCENE_ID, "sourceSceneId mismatch")
    require(data.get("sourceFindingMemory", "").endswith("complete-m60-f16-six-pixel-set"), "source finding mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("sourceFor442DiagnosticFlag") == FOR442_FLAG, "FOR-442 flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == "webgpu-low-level-mask-unresolved", "expected generated classification")
    require(source.get("classification") == "webgpu-runtime-exact-mask-probe-unavailable", "FOR-442 source changed")

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
        "lowLevelProbeDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("lowLevelProbeOptInOnly") is True, "lowLevelProbeOptInOnly must be true")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    expected_summary = {
        "partialPixelCount": 6,
        "expectedPartialPixelCount": 6,
        "cpuGreenCoverageZeroCount": 6,
        "cpuGreenMaskZero4x4Count": 6,
        "lowLevelExactMaskAvailableCount": 6,
        "lowLevelExactMaskUnavailableCount": 0,
        "lowLevelExactMaskNonZeroCount": 0,
        "lowLevelExactMask10Of16Count": 0,
        "for442RuntimeMaskAvailableCount": 2,
        "hostBoundDrawIndex": 3,
        "lowLevelEventDrawIndex": 3,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"summary.{key} expected {expected}, got {summary.get(key)}")
    require(summary.get("traceComplete") is True, "traceComplete must be true")
    require(summary.get("computeCopySucceeded") is True, "computeCopySucceeded must be true")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six entries")
    require([(p.get("x"), p.get("y")) for p in pixels] == EXPECTED_POINTS, "partialPixels order mismatch")
    for pixel in pixels:
        point = (pixel.get("x"), pixel.get("y"))
        require(point in EXPECTED_POINT_SET, f"unexpected point {point}")
        require(pixel.get("classification") == "webgpu-low-level-mask-unresolved", f"{point} classification mismatch")
        require(pixel.get("missingFields") == [], f"{point} missingFields must be empty")
        cpu = pixel.get("cpuGreenMask")
        low = pixel.get("webGpuLowLevelExactMask")
        runtime = pixel.get("for442RuntimeExactMask")
        relation = pixel.get("maskRelation")
        grid = pixel.get("subsampleComparison4x4")
        require(cpu.get("coverageAlphaByte") == 0, f"{point} CPU coverage must be zero")
        require(cpu.get("subsampleMask4x4") == 0, f"{point} CPU mask must be zero")
        require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"{point} CPU mask hex mismatch")
        require(low.get("available") is True, f"{point} low-level mask must be available")
        require(low.get("runtimeIndependentOfFragmentCoverage") is True, f"{point} runtime independence missing")
        require(low.get("subsampleMask4x4") == 0, f"{point} low-level mask must be zero")
        require(low.get("subsampleMask4x4Hex") == "0x0000", f"{point} low-level mask hex mismatch")
        require(low.get("coveredSubsamples4x4") == 0, f"{point} low-level covered count must be zero")
        require(low.get("valid") is True, f"{point} low-level sample must be valid")
        require(low.get("probeTag") == 443, f"{point} probe tag mismatch")
        require(low.get("edgeCountEcho") == 39, f"{point} edge count echo mismatch")
        require(low.get("fillTypeEcho") == 0, f"{point} fill type echo mismatch")
        require(relation.get("lowLevelPopcount") == 0, f"{point} low-level popcount mismatch")
        require(relation.get("divergentSubsamples") == 0, f"{point} divergentSubsamples mismatch")
        require(isinstance(grid, list) and len(grid) == 16, f"{point} grid must contain 16 cells")
        require(all(cell.get("wgslCovered") is False for cell in grid), f"{point} low-level grid must be empty")
        if point == (92, 75):
            require(runtime.get("available") is True and runtime.get("subsampleMask4x4Hex") == "0x005C", "FOR-442 92,75 mask mismatch")
            require(relation.get("matchesFor442RuntimeMaskWhenAvailable") is False, "FOR-442 relation mismatch")
        elif point == (89, 78):
            require(runtime.get("available") is True and runtime.get("subsampleMask4x4Hex") == "0x0058", "FOR-442 89,78 mask mismatch")
            require(relation.get("matchesFor442RuntimeMaskWhenAvailable") is False, "FOR-442 relation mismatch")
        else:
            require(runtime.get("available") is False, f"{point} FOR-442 runtime mask should be unavailable")
    return data


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(f"FOR-443 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
