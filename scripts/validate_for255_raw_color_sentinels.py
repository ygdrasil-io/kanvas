#!/usr/bin/env python3
"""Validate FOR-255 raw color sentinel boundary evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "raw-color-sentinel-audit-for255.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/raw-color-sentinel-audit-for255"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/raw-color-sentinel-audit-for255"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
ROUTE_PREPASS = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json"
)
STATIC_ROUTE_PREPASS = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-prepass.json"
)
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-255-raw-color-sentinels.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROPERTY_NAME = "kanvas.webgpu.rawColorSentinels"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-255 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_array(owner: dict[str, Any], field: str, expected: list[int]) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_float_array(owner: dict[str, Any], field: str, expected: list[float]) -> None:
    actual = owner.get(field)
    if not isinstance(actual, list) or len(actual) != len(expected):
        fail(f"`{field}` expected {expected}, got {actual}")
    for index, (actual_value, expected_value) in enumerate(zip(actual, expected)):
        if abs(float(actual_value) - expected_value) > 1e-6:
            fail(f"`{field}` index {index} expected {expected_value}, got {actual_value}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def validate_output_pixel(owner: dict[str, Any], expected_ref: list[int], expected_gpu: list[int]) -> None:
    output = owner.get("outputReferenceGpu")
    if not isinstance(output, dict):
        fail("missing outputReferenceGpu")
    require_array(output, "referenceRgba", expected_ref)
    require_array(output, "gpuRgba", expected_gpu)
    if output.get("maxChannelDelta") != 1:
        fail(f"output maxChannelDelta expected 1, got {output.get('maxChannelDelta')}")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-255")
    require_text(probe, "probe", "raw-color-sentinel-boundary-audit")
    require_text(probe, "propertyName", PROPERTY_NAME)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    if probe.get("defaultEnabled") is not False:
        fail("raw sentinels must be disabled by default")
    if probe.get("runtimeSnapshotsEnabled") is not True:
        fail("test evidence must prove opt-in snapshots were enabled")
    if probe.get("correctionApplied") is not False:
        fail("FOR-255 must not apply a correction")

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    if observed.get("hostObserved") is not True:
        fail("hostObserved boundary missing")
    if observed.get("uploadWriteObserved") is not True:
        fail("uploadWriteObserved boundary missing")
    if observed.get("shaderObserved") != "notCaptured":
        fail("shaderObserved status must be notCaptured")
    if observed.get("outputReferenceGpuObserved") is not True:
        fail("outputReferenceGpuObserved boundary missing")

    uniform = probe.get("legacySourceUniform")
    if not isinstance(uniform, dict):
        fail("missing legacySourceUniform")
    require_text(uniform, "id", "legacy-source-color-uniform.simple-offset-row1-col0")
    require_array(uniform, "hostObservedRgba8", [255, 0, 0, 102])
    require_float_array(uniform, "hostObservedNormalizedRgba", [1.0, 0.0, 0.0, 0.4])
    require_array(uniform, "rawWriteObservedRgba8", [255, 0, 0, 102])
    require_array(uniform, "rawUniformWordBits", [1065353216, 0, 0, 1053609165])
    if uniform.get("rawUniformWordHex") != ["3f800000", "00000000", "00000000", "3ecccccd"]:
        fail(f"rawUniformWordHex changed: {uniform.get('rawUniformWordHex')}")
    require_array(uniform, "scissor", [40, 40, 40, 40])
    validate_output_pixel(uniform, [158, 90, 139, 255], [157, 90, 138, 255])

    bitmap = probe.get("bitmapTexelUpload")
    if not isinstance(bitmap, dict):
        fail("missing bitmapTexelUpload")
    require_text(bitmap, "id", "bitmap-texel-upload-sample.bitmap-rect-nearest")
    require_array(bitmap, "hostObservedRgba8", [149, 193, 207, 255])
    require_array(bitmap, "rawUploadObservedRgba8", [149, 193, 207, 255])
    require_array(bitmap, "uploadSize", [1, 1])
    require_array(bitmap, "uploadTexelXy", [0, 0])
    validate_output_pixel(bitmap, [149, 193, 207, 255], [148, 193, 207, 255])

    shader = probe.get("shaderObserved")
    if not isinstance(shader, dict):
        fail("missing shaderObserved")
    require_text(shader, "status", "notCaptured")
    reason = shader.get("reason")
    if not isinstance(reason, str) or "storage-buffer" not in reason or "normal rendering unchanged" not in reason:
        fail("shaderObserved reason must explain the bounded non-capture")


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for255" in json.dumps(route_prepass).lower():
        fail("FOR-255 should not mutate crop route metadata")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-255 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    device_text = require_file_text(
        WEBGPU_DEVICE,
        [
            PROPERTY_NAME,
            "rawColorSentinelSnapshot",
            "recordRawRectUniformColorWrite",
            "recordRawRgba8TextureUpload",
        ],
    )
    for forbidden in ("cpu fallback", "readback fallback", "correctionApplied = true"):
        if forbidden in device_text:
            fail(f"FOR-255 must not add `{forbidden}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-255 raw color sentinels capture uniform and texel input boundary",
            "raw-color-sentinel-audit-for255.json",
            "shaderObserved",
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-255 Raw Color Sentinels",
            "Decision: `KEEP_DIAGNOSTIC`",
            PROPERTY_NAME,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-255 validation passed: raw uniform words and RGBA8 upload bytes "
        "match host inputs, shader-observed values remain explicitly uncaptured, "
        "and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
