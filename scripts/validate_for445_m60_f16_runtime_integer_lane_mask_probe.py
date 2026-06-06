#!/usr/bin/env python3
"""Validate FOR-445 M60 F16 runtime integer-lane mask probe evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-runtime-integer-lane-mask-probe-for445"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-445-m60-f16-runtime-integer-lane-mask-probe.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16RuntimeIntegerLaneMaskProbeFor445.enabled"
FOR442_FLAG = "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled"
FOR443_FLAG = "kanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
EXPECTED_FLOAT_MASKS = {(92, 75): "0x005C", (89, 78): "0x0058"}
ALLOWED_CLASSIFICATIONS = {
    "runtime-integer-lane-confirms-float-mask",
    "runtime-integer-lane-refutes-float-mask",
    "runtime-integer-lane-fragment-path-diverges-from-low-level",
    "runtime-integer-lane-source-unavailable",
    "runtime-integer-lane-audit-inconclusive",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py",
    "scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py",
    "scripts/validate_for447_m60_f16_zero_mask_opt_in_correction.py",
    "reports/wgsl-pipeline/2026-06-06-for-445-m60-f16-runtime-integer-lane-mask-probe.md",
    "reports/wgsl-pipeline/2026-06-06-for-446-m60-f16-for442-float-mask-field-audit.md",
    "reports/wgsl-pipeline/2026-06-06-for-447-m60-f16-zero-mask-opt-in-correction.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-for442-float-mask-field-audit-for446",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-for442-float-mask-field-audit-for446/m60-f16-for442-float-mask-field-audit-for446.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/m60-f16-zero-mask-opt-in-correction-for447.json",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/reference-cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/current-webgpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/current-webgpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/opt-in-webgpu-zero-mask-correction.png",
    "reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-mask-opt-in-correction-for447/opt-in-webgpu-zero-mask-correction-diff.png",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-445 validation failed: {message}")


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
    build = BUILD.read_text(encoding="utf-8")
    device = DEVICE.read_text(encoding="utf-8")
    sink = SINK.read_text(encoding="utf-8")
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16RuntimeIntegerLaneMaskProbeFor445(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "snapshotExposed": "runtimeIntegerLaneMaskProbeFor445Snapshot" in sink,
        "integerStorage": all(
            token in device
            for token in (
                "m60F16RuntimeIntegerLaneMaskProbeFor445: array<vec4u",
                "M60_F16_RUNTIME_INTEGER_LANE_MASK_PROBE_FOR445_SAMPLE_STRIDE_BYTES",
                "recordM60F16RuntimeIntegerLaneMaskProbeFor445Readbacks",
            )
        ) and "runtime-integer-lane-refutes-float-mask" in capture,
        "for445ActivatesFor442": "WEBGPU_M60_F16_RUNTIME_INTEGER_LANE_MASK_PROBE_FOR445_FLAG" in device
        and "m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled" in device,
        "for445ActivatesFor443": "m60F16LowLevelExactMaskProbeFor443DiagnosticsEnabled" in device,
        "widthQuantizedVariant": "width-quantized-color-reconstruction-runtime-integer-lane-mask-for445" in device,
        "storageAuditFields": all(
            token in capture
            for token in (
                '"storageDeclaration": "array<vec4u, 18>"',
                '"numericTypeRead": "u32 masked with 0xFFFF"',
                '"sampleStrideBytes": 48',
                '"coveredCountOffsetBytes": 12',
                '"probeTagOffsetBytes": ${integerBaseOffset + 36}',
            )
        ),
        "sourceMemories": (
            "brouillon-ticket-m60-f16-sonde-integer-lane-runtime-mask-et-covered-count" in capture
            and "for-444-runtime-mask-source-field-remains-ambiguous-against-low-level-zero-masks" in capture
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-445: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_DIFF_PREFIXES))
    require(not forbidden, f"forbidden spec/external/production-shader diffs: {forbidden}")

    diff_text = subprocess.run(
        [
            "git",
            "diff",
            "--unified=0",
            "--",
            rel(CAPTURE_TEST),
            rel(DEVICE),
            rel(SINK),
            rel(BUILD),
        ],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    ).stdout
    dangerous_lines = [
        line
        for line in diff_text.splitlines()
        if (line.startswith("+") or line.startswith("-"))
        and not line.startswith(("+++", "---"))
        and (
            "GPU_SUPPORT_THRESHOLD" in line
            or "similarity <" in line
            or "similarity >" in line
            or "coverage.stroke-cap-join-visual-parity-below-threshold" in line
            or "PipelineKey" in line
            or ("fallbackPolicy" in line and '"fallbackPolicyChanged": false' not in line)
            or "src/main/resources/shaders/aa_stencil_cover.wgsl" in line
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey/shader lines changed: {dangerous_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-445 diagnostic flag")
    require("0x005C" in text and "0x0058" in text, "report must mention FOR-442 masks")
    require("0x0000" in text, "report must mention FOR-445/FOR-443 zero masks")
    require("global/kanvas/findings/for-444-runtime-mask-source-field-remains-ambiguous-against-low-level-zero-masks" in text, "report must cite source finding memory")


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-445", "linear must be FOR-445")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("for442RuntimeFlag") == FOR442_FLAG, "FOR-442 flag mismatch")
    require(data.get("for443LowLevelFlag") == FOR443_FLAG, "FOR-443 flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == "runtime-integer-lane-refutes-float-mask", "expected generated classification")

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
        "for445ProbeDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("for445ProbeOptInOnly") is True, "probe must be opt-in only")

    layouts = data.get("storageLayouts")
    require(isinstance(layouts, dict), "storageLayouts must be object")
    integer_layout = layouts.get("runtimeIntegerFor445")
    require(integer_layout.get("sampleStrideBytes") == 48, "FOR-445 stride mismatch")
    require(integer_layout.get("subsampleMaskFieldOffsetBytes") == 8, "FOR-445 mask offset mismatch")
    require(integer_layout.get("coveredCountOffsetBytes") == 12, "FOR-445 covered offset mismatch")
    require(integer_layout.get("numericTypeRead") == "u32 masked with 0xFFFF", "FOR-445 numeric type mismatch")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    expected_summary = {
        "partialPixelCount": 6,
        "expectedPartialPixelCount": 6,
        "cpuGreenMaskZero4x4Count": 6,
        "runtimeFloatMaskAvailableCount": 2,
        "runtimeFloatMaskNonZeroCount": 2,
        "runtimeIntegerMaskAvailableCount": 6,
        "runtimeIntegerMaskNonZeroCount": 0,
        "runtimeIntegerCoveredCountAvailableCount": 6,
        "runtimeIntegerMaskPopcountMismatchCount": 0,
        "lowLevelExactMaskAvailableCount": 6,
        "lowLevelExactMaskNonZeroCount": 0,
        "runtimeIntegerFloatMismatchCount": 2,
        "runtimeIntegerLowLevelMismatchCount": 0,
        "runtimeIntegerStorageTupleValidCount": 6,
        "lowLevelStorageTupleValidCount": 6,
        "hostBoundDrawIndex": 3,
        "runtimeIntegerEventDrawIndex": 3,
        "lowLevelEventDrawIndex": 3,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"summary.{key} expected {expected}, got {summary.get(key)}")
    require(summary.get("runtimeIntegerCopySucceeded") is True, "runtimeIntegerCopySucceeded must be true")
    require(summary.get("lowLevelCopySucceeded") is True, "lowLevelCopySucceeded must be true")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six entries")
    require([(p.get("x"), p.get("y")) for p in pixels] == EXPECTED_POINTS, "partialPixels order mismatch")
    for index, pixel in enumerate(pixels):
        point = (pixel.get("x"), pixel.get("y"))
        require(pixel.get("drawIndex") == 3, f"{point} drawIndex must be 3")
        cpu = pixel.get("cpuGreenMask")
        runtime_float = pixel.get("runtimeFor442FloatStorage")
        runtime_integer = pixel.get("runtimeFor445IntegerStorage")
        low = pixel.get("lowLevelFor443Storage")
        relation = pixel.get("maskRelation")

        require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"{point} CPU mask mismatch")
        require(runtime_integer.get("available") is True and runtime_integer.get("valid") is True, f"{point} FOR-445 tuple must be valid")
        require(runtime_integer.get("storageSampleIndex") == index, f"{point} FOR-445 sample index mismatch")
        require(runtime_integer.get("sampleStrideBytes") == 48, f"{point} FOR-445 stride mismatch")
        require(runtime_integer.get("subsampleMaskFieldOffsetBytes") == index * 48 + 8, f"{point} FOR-445 mask offset mismatch")
        require(runtime_integer.get("coveredCountOffsetBytes") == index * 48 + 12, f"{point} FOR-445 covered offset mismatch")
        require(runtime_integer.get("writtenCoordinate") == [point[0], point[1]], f"{point} FOR-445 written coordinate mismatch")
        require(runtime_integer.get("subdrawOrdinal") == 0, f"{point} FOR-445 subdraw ordinal mismatch")
        require(runtime_integer.get("subdrawRole") == "inside", f"{point} FOR-445 subdraw role mismatch")
        require(runtime_integer.get("edgeCountEcho") == 39, f"{point} FOR-445 edge count mismatch")
        require(runtime_integer.get("fillTypeEcho") == 0, f"{point} FOR-445 fill type mismatch")
        require(runtime_integer.get("probeTag") == 445, f"{point} FOR-445 probe tag mismatch")
        require(runtime_integer.get("subsampleMask4x4Hex") == "0x0000", f"{point} FOR-445 integer mask mismatch")
        require(runtime_integer.get("coveredSubsamples4x4") == 0, f"{point} FOR-445 covered count mismatch")
        require(runtime_integer.get("popcount") == 0, f"{point} FOR-445 popcount mismatch")
        require(runtime_integer.get("popcountMatchesCoveredCount") is True, f"{point} FOR-445 count consistency mismatch")

        require(low.get("available") is True and low.get("valid") is True, f"{point} low-level sample must be valid")
        require(low.get("subsampleMask4x4Hex") == "0x0000", f"{point} low-level mask mismatch")
        require(low.get("coveredSubsamples4x4") == 0, f"{point} low-level covered count mismatch")
        require(relation.get("integerMatchesLowLevel") is True, f"{point} FOR-445 must match FOR-443")

        if point in EXPECTED_FLOAT_MASKS:
            require(pixel.get("classification") == "runtime-integer-lane-refutes-float-mask", f"{point} classification mismatch")
            require(runtime_float.get("available") is True, f"{point} FOR-442 float mask must be available")
            require(runtime_float.get("subsampleMask4x4Hex") == EXPECTED_FLOAT_MASKS[point], f"{point} FOR-442 float mask mismatch")
            require(relation.get("integerRefutesFloat") is True, f"{point} FOR-445 must refute FOR-442")
        else:
            require(pixel.get("classification") == "runtime-integer-lane-audit-inconclusive", f"{point} classification mismatch")
            require(runtime_float.get("available") is False, f"{point} FOR-442 float mask should be unavailable")
            require(relation.get("integerRefutesFloat") is None, f"{point} refute relation should be null")
    return data


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(f"FOR-445 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
