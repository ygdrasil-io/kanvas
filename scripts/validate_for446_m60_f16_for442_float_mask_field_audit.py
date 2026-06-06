#!/usr/bin/env python3
"""Validate FOR-446 M60 F16 FOR-442 float mask field audit evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-for442-float-mask-field-audit-for446"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-446-m60-f16-for442-float-mask-field-audit.md"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
SINK = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16For442FloatMaskFieldAuditFor446.enabled"
FOR442_FLAG = "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled"
FOR443_FLAG = "kanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled"
FOR445_FLAG = "kanvas.webgpu.m60F16RuntimeIntegerLaneMaskProbeFor445.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
EXPECTED_FLOAT_MASKS = {(92, 75): "0x005C", (89, 78): "0x0058"}
ALLOWED_CLASSIFICATIONS = {
    "for442-float-mask-field-offset-mismatch",
    "for442-float-mask-field-overwritten",
    "for442-float-mask-field-conversion-artifact",
    "for442-float-mask-field-sample-selection-mismatch",
    "for442-float-mask-field-retired-as-unreliable",
    "for442-float-mask-field-audit-inconclusive",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py",
    "scripts/validate_for446_m60_f16_for442_float_mask_field_audit.py",
    "reports/wgsl-pipeline/2026-06-06-for-446-m60-f16-for442-float-mask-field-audit.md",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}",
    f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json",
}
FORBIDDEN_DIFF_PREFIXES = (
    "gpu-raster/src/main/resources/shaders/",
    ".upstream/",
    "external/",
    "buildSrc/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-446 validation failed: {message}")


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
        "writerCalled": "writeM60F16For442FloatMaskFieldAuditFor446(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "for446ActivatesFor442": (
            "WEBGPU_M60_F16_FOR442_FLOAT_MASK_FIELD_AUDIT_FOR446_FLAG" in device
            and "m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled" in device
        ),
        "for446ActivatesFor443": "m60F16LowLevelExactMaskProbeFor443DiagnosticsEnabled" in device,
        "for446ActivatesFor445": "m60F16RuntimeIntegerLaneMaskProbeFor445DiagnosticsEnabled" in device,
        "rawFloatExposed": "wgslSubsampleMask4x4RawFloat" in device and "floatMaskRawF32" in capture,
        "rawVec4sExposed": "diagnosticStorageVec4s" in device and '"rawStorageVec4s"' in capture,
        "snapshotAvailable": "runtimeIntegerLaneMaskProbeFor445Snapshot" in sink,
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "sourceMemories": (
            "brouillon-ticket-m60-f16-auditer-le-champ-float-for-442-refute-par-for-445" in capture
            and "for-445-runtime-integer-lane-mask-refutes-for-442-float-masks" in capture
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-446: {unexpected}")
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
    require(FLAG in text, "report must include FOR-446 diagnostic flag")
    require("0x005C" in text and "0x0058" in text, "report must mention FOR-442 masks")
    require("0x0000" in text, "report must mention FOR-445/FOR-443 zero masks")
    require("global/kanvas/findings/for-445-runtime-integer-lane-mask-refutes-for-442-float-masks" in text, "report must cite source finding memory")


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-446", "linear must be FOR-446")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("for442RuntimeFlag") == FOR442_FLAG, "FOR-442 flag mismatch")
    require(data.get("for443LowLevelFlag") == FOR443_FLAG, "FOR-443 flag mismatch")
    require(data.get("for445RuntimeIntegerFlag") == FOR445_FLAG, "FOR-445 flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == "for442-float-mask-field-retired-as-unreliable", "expected generated classification")

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
        "for446ProbeDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("for446ProbeOptInOnly") is True, "probe must be opt-in only")

    layouts = data.get("storageLayouts")
    require(isinstance(layouts, dict), "storageLayouts must be object")
    runtime_layout = layouts.get("runtimeFor442")
    require(runtime_layout.get("sampleStrideBytes") == 112, "FOR-442 stride mismatch")
    require(runtime_layout.get("subsampleMaskFieldOffsetBytes") == 96, "FOR-442 mask offset mismatch")
    require(runtime_layout.get("numericTypeRead") == "f32 raw plus f32 rounded to Int", "FOR-442 numeric type mismatch")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    expected_summary = {
        "partialPixelCount": 6,
        "expectedPartialPixelCount": 6,
        "cpuGreenMaskZero4x4Count": 6,
        "runtimeFloatMaskAvailableCount": 2,
        "runtimeFloatRawF32AvailableCount": 2,
        "runtimeFloatMaskNonZeroCount": 2,
        "runtimeFloatRawRoundMismatchCount": 0,
        "runtimeFloatMaskNonZeroButCoveredCountZeroCount": 2,
        "runtimeIntegerMaskAvailableCount": 6,
        "runtimeIntegerMaskNonZeroCount": 0,
        "runtimeIntegerCoveredCountZeroCount": 6,
        "lowLevelExactMaskAvailableCount": 6,
        "lowLevelExactMaskNonZeroCount": 0,
        "floatIntegerMismatchCount": 2,
        "floatLowLevelMismatchCount": 2,
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
        runtime_float = pixel.get("runtimeFor442FloatFieldAudit")
        runtime_integer = pixel.get("runtimeFor445IntegerStorage")
        low = pixel.get("lowLevelFor443Storage")
        relation = pixel.get("maskRelation")

        require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"{point} CPU mask mismatch")
        require(runtime_integer.get("available") is True and runtime_integer.get("valid") is True, f"{point} FOR-445 tuple must be valid")
        require(runtime_integer.get("subsampleMask4x4Hex") == "0x0000", f"{point} FOR-445 integer mask mismatch")
        require(runtime_integer.get("coveredSubsamples4x4") == 0, f"{point} FOR-445 covered count mismatch")
        require(low.get("available") is True and low.get("valid") is True, f"{point} low-level tuple must be valid")
        require(low.get("subsampleMask4x4Hex") == "0x0000", f"{point} low-level mask mismatch")

        if point in EXPECTED_FLOAT_MASKS:
            require(pixel.get("classification") == "for442-float-mask-field-retired-as-unreliable", f"{point} classification mismatch")
            require(runtime_float.get("available") is True, f"{point} FOR-442 float mask must be available")
            require(runtime_float.get("floatMaskRoundedHex") == EXPECTED_FLOAT_MASKS[point], f"{point} FOR-442 float mask mismatch")
            require(runtime_float.get("rawRoundMatchesRoundedField") is True, f"{point} raw f32 round should match")
            require(runtime_float.get("coveredSubsamples4x4FromNeighborVec4") == 0, f"{point} covered neighbor mismatch")
            require(runtime_float.get("popcountMatchesCoveredNeighbor") is False, f"{point} should refute neighbor count")
            require(isinstance(runtime_float.get("rawStorageVec4s"), list) and len(runtime_float["rawStorageVec4s"]) == 7, f"{point} raw vec4 audit missing")
            require(relation.get("floatNonZeroButIntegerAndLowLevelZero") is True, f"{point} relation mismatch")
        else:
            require(pixel.get("classification") == "for442-float-mask-field-audit-inconclusive", f"{point} classification mismatch")
            require(runtime_float.get("available") is False, f"{point} FOR-442 float mask should be unavailable")
    return data


def main() -> None:
    source_audit()
    data = require_artifact()
    require_report(data)
    print(f"FOR-446 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
