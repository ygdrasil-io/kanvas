#!/usr/bin/env python3
"""Validate FOR-444 M60 F16 runtime mask packing vs low-level probe evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "m60-f16-runtime-mask-packing-vs-low-level-probe-for444"
ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID / f"{SCENE_ID}.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-444-m60-f16-runtime-mask-packing-vs-low-level-probe.md"
FOR442_SCENE_ID = "m60-f16-webgpu-runtime-exact-mask-probe-for442"
FOR442_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR442_SCENE_ID / f"{FOR442_SCENE_ID}.json"
FOR443_SCENE_ID = "m60-f16-webgpu-low-level-exact-mask-probe-for443"
FOR443_ARTIFACT = ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR443_SCENE_ID / f"{FOR443_SCENE_ID}.json"
CAPTURE_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
BUILD = ROOT / "gpu-raster/build.gradle.kts"
DEVICE = ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
PRODUCTION_SHADER = ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

FLAG = "kanvas.webgpu.m60F16RuntimeMaskPackingVsLowLevelProbeFor444.enabled"
FOR442_FLAG = "kanvas.webgpu.m60F16RuntimeExactMaskProbeFor442.enabled"
FOR443_FLAG = "kanvas.webgpu.m60F16LowLevelExactMaskProbeFor443.enabled"
EXPECTED_POINTS = [(92, 75), (91, 76), (90, 77), (89, 78), (88, 79), (87, 80)]
EXPECTED_POINT_SET = set(EXPECTED_POINTS)
EXPECTED_RUNTIME_MASKS = {(92, 75): "0x005C", (89, 78): "0x0058"}
ALLOWED_CLASSIFICATIONS = {
    "runtime-mask-storage-packing-mismatch",
    "runtime-mask-sample-order-mismatch",
    "runtime-mask-fragment-coverage-path-diverges",
    "runtime-mask-source-field-ambiguous",
    "runtime-mask-audit-inconclusive",
}
ALLOWED_LOCAL_DIFFS = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/WebGpuSink.kt",
    "scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py",
    "scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py",
    "scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py",
    "reports/wgsl-pipeline/2026-06-06-for-444-m60-f16-runtime-mask-packing-vs-low-level-probe.md",
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
    raise SystemExit(f"FOR-444 validation failed: {message}")


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
    shader = PRODUCTION_SHADER.read_text(encoding="utf-8")
    checks = {
        "writerCalled": "writeM60F16RuntimeMaskPackingVsLowLevelProbeFor444(" in capture,
        "sceneIdPresent": SCENE_ID in capture,
        "flagRelayed": FLAG in capture and FLAG in build and FLAG in device,
        "for442ActivatedByFor444": (
            "WEBGPU_M60_F16_RUNTIME_MASK_PACKING_VS_LOW_LEVEL_PROBE_FOR444_FLAG" in device
            and "m60F16RuntimeExactMaskProbeFor442DiagnosticsEnabled" in device
        ),
        "for443ActivatedByFor444": "m60F16LowLevelExactMaskProbeFor443DiagnosticsEnabled" in device,
        "storageAuditFields": all(
            token in capture
            for token in (
                '"sampleStrideBytes": 112',
                '"numericTypeRead": "f32 rounded to Int"',
                '"subsampleMaskFieldOffsetBytes": ${runtimeBaseOffset?.plus(96)',
                '"sampleStrideBytes": 32',
                '"numericTypeRead": "u32 masked with 0xFFFF"',
                '"probeTagOffsetBytes": ${lowLevelBaseOffset + 28}',
            )
        ),
        "allowedClassifications": all(token in capture for token in ALLOWED_CLASSIFICATIONS),
        "sourceMemories": (
            "brouillon-ticket-m60-f16-auditer-packing-runtime-for-442-contre-masque-bas-niveau-for-443" in capture
            and "for-443-web-gpu-low-level-exact-masks-are-zero-for-m60-f16-six-pixel-set" in capture
        ),
        "productionShaderStillHasPredicate": all(
            token in shader for token in ("fn winding_at", "fn sample_covered", "fn supersampled_path_cov")
        ),
    }
    missing = [name for name, ok in checks.items() if not ok]
    require(not missing, f"source audit failed: {missing}")

    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in ALLOWED_LOCAL_DIFFS)
    require(not unexpected, f"unexpected local diffs for FOR-444: {unexpected}")
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
            or "aa_stencil_cover.wgsl" in line
        )
    ]
    require(not dangerous_lines, f"threshold/scoring/fallback/PipelineKey/shader lines changed: {dangerous_lines}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    require(data["classification"] in text, "report must include artifact classification")
    require(FLAG in text, "report must include FOR-444 diagnostic flag")
    require("0x005C" in text and "0x0058" in text, "report must mention the two FOR-442 masks")
    require("0x0000" in text, "report must mention FOR-443 zero masks")
    require("global/kanvas/findings/for-443-web-gpu-low-level-exact-masks-are-zero-for-m60-f16-six-pixel-set" in text, "report must cite source finding memory")


def require_sources() -> None:
    for442 = load_json(FOR442_ARTIFACT)
    for443 = load_json(FOR443_ARTIFACT)
    require(for442.get("classification") == "webgpu-runtime-exact-mask-probe-unavailable", "FOR-442 source classification changed")
    require(for443.get("classification") == "webgpu-low-level-mask-unresolved", "FOR-443 source classification changed")


def require_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("linear") == "FOR-444", "linear must be FOR-444")
    require(data.get("sceneId") == SCENE_ID, "sceneId mismatch")
    require(data.get("diagnosticFlag") == FLAG, "diagnostic flag mismatch")
    require(data.get("for442RuntimeFlag") == FOR442_FLAG, "FOR-442 flag mismatch")
    require(data.get("for443LowLevelFlag") == FOR443_FLAG, "FOR-443 flag mismatch")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification not allowed")
    require(data.get("classification") == "runtime-mask-source-field-ambiguous", "expected generated classification")

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
        "for444ProbeDefaultActive",
    ):
        require(data.get(field) is False, f"{field} must be false")
    require(data.get("for444ProbeOptInOnly") is True, "for444ProbeOptInOnly must be true")

    layouts = data.get("storageLayouts")
    require(isinstance(layouts, dict), "storageLayouts must be object")
    runtime_layout = layouts.get("runtimeFor442")
    low_layout = layouts.get("lowLevelFor443")
    require(runtime_layout.get("sampleStrideBytes") == 112, "runtime stride mismatch")
    require(runtime_layout.get("subsampleMaskFieldOffsetBytes") == 96, "runtime mask offset mismatch")
    require(runtime_layout.get("numericTypeRead") == "f32 rounded to Int", "runtime numeric type mismatch")
    require(low_layout.get("sampleStrideBytes") == 32, "low-level stride mismatch")
    require(low_layout.get("subsampleMaskFieldOffsetBytes") == 8, "low-level mask offset mismatch")
    require(low_layout.get("numericTypeRead") == "u32 masked with 0xFFFF", "low-level numeric type mismatch")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary must be object")
    expected_summary = {
        "partialPixelCount": 6,
        "expectedPartialPixelCount": 6,
        "cpuGreenMaskZero4x4Count": 6,
        "runtimeMaskAvailableCount": 2,
        "runtimeMaskNonZeroCount": 2,
        "lowLevelExactMaskAvailableCount": 6,
        "lowLevelExactMaskNonZeroCount": 0,
        "runtimeLowLevelMismatchCount": 2,
        "runtimeStorageTupleValidCount": 2,
        "lowLevelStorageTupleValidCount": 6,
        "hostBoundDrawIndex": 3,
        "lowLevelEventDrawIndex": 3,
    }
    for key, expected in expected_summary.items():
        require(summary.get(key) == expected, f"summary.{key} expected {expected}, got {summary.get(key)}")
    require(summary.get("computeCopySucceeded") is True, "computeCopySucceeded must be true")

    pixels = data.get("partialPixels")
    require(isinstance(pixels, list) and len(pixels) == 6, "partialPixels must contain six entries")
    require([(p.get("x"), p.get("y")) for p in pixels] == EXPECTED_POINTS, "partialPixels order mismatch")
    for index, pixel in enumerate(pixels):
        point = (pixel.get("x"), pixel.get("y"))
        require(point in EXPECTED_POINT_SET, f"unexpected point {point}")
        require(pixel.get("drawIndex") == 3, f"{point} drawIndex must be 3")
        require(pixel.get("missingFields") == ([] if point in EXPECTED_RUNTIME_MASKS else ["runtimeShaderReturnInsideSample", "runtimeWgslSubsampleMask4x4"]), f"{point} missing fields mismatch")

        cpu = pixel.get("cpuGreenMask")
        runtime = pixel.get("runtimeFor442Storage")
        low = pixel.get("lowLevelFor443Storage")
        relation = pixel.get("maskRelation")
        require(cpu.get("subsampleMask4x4Hex") == "0x0000", f"{point} CPU mask mismatch")
        require(low.get("available") is True and low.get("valid") is True, f"{point} low-level sample must be valid")
        require(low.get("storageSampleIndex") == index, f"{point} low-level sample index mismatch")
        require(low.get("sampleStrideBytes") == 32, f"{point} low-level stride mismatch")
        require(low.get("subsampleMaskFieldOffsetBytes") == index * 32 + 8, f"{point} low-level mask offset mismatch")
        require(low.get("subsampleMask4x4Hex") == "0x0000", f"{point} low-level mask hex mismatch")
        require(low.get("coveredSubsamples4x4") == 0, f"{point} low-level covered count mismatch")
        require(low.get("probeTag") == 443, f"{point} low-level probe tag mismatch")
        require(low.get("edgeCountEcho") == 39, f"{point} low-level edge count mismatch")
        require(low.get("fillTypeEcho") == 0, f"{point} low-level fill type mismatch")

        if point in EXPECTED_RUNTIME_MASKS:
            runtime_index = index * 2
            require(pixel.get("classification") == "runtime-mask-source-field-ambiguous", f"{point} classification mismatch")
            require(runtime.get("available") is True and runtime.get("valid") is True, f"{point} runtime sample must be valid")
            require(runtime.get("storageSampleIndex") == runtime_index, f"{point} runtime sample index mismatch")
            require(runtime.get("sampleStrideBytes") == 112, f"{point} runtime stride mismatch")
            require(runtime.get("subsampleMaskFieldOffsetBytes") == runtime_index * 112 + 96, f"{point} runtime mask offset mismatch")
            require(runtime.get("numericTypeRead") == "f32 rounded to Int", f"{point} runtime numeric type mismatch")
            require(runtime.get("expectedCoordinate") == [point[0], point[1]], f"{point} runtime expected coordinate mismatch")
            require(runtime.get("writtenCoordinate") == [point[0], point[1]], f"{point} runtime written coordinate mismatch")
            require(runtime.get("subdrawOrdinal") == 0, f"{point} runtime subdraw ordinal mismatch")
            require(runtime.get("subdrawRole") == "inside", f"{point} runtime subdraw role mismatch")
            require(runtime.get("edgeCount") == 39, f"{point} runtime edge count mismatch")
            require(runtime.get("fillType") == "kWinding", f"{point} runtime fill type mismatch")
            require(runtime.get("subsampleMask4x4Hex") == EXPECTED_RUNTIME_MASKS[point], f"{point} runtime mask mismatch")
            require(relation.get("runtimeMatchesLowLevel") is False, f"{point} relation must mismatch")
            require(relation.get("runtimeNonZeroLowLevelZero") is True, f"{point} relation must be runtime nonzero / low-level zero")
        else:
            require(pixel.get("classification") == "runtime-mask-source-field-ambiguous", f"{point} classification mismatch")
            require(runtime.get("available") is False, f"{point} runtime sample should be unavailable")
            require(runtime.get("valid") is False, f"{point} runtime sample should be invalid")
            require(runtime.get("subsampleMask4x4Hex") is None, f"{point} runtime mask should be null")
    return data


def main() -> None:
    source_audit()
    require_sources()
    data = require_artifact()
    require_report(data)
    print(f"FOR-444 validation passed: {data['classification']}")


if __name__ == "__main__":
    main()
