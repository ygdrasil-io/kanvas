#!/usr/bin/env python3
"""Validate FOR-256 shader/store/reference boundary evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "shader-store-reference-boundary-for256.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/shader-store-reference-boundary-for256"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/shader-store-reference-boundary-for256"
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-256-shader-store-reference-boundary.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROPERTY_NAME = "kanvas.webgpu.for256.outputReadbackBoundary"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-256 validation failed: {message}")


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


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def validate_case(
    owner: dict[str, Any],
    *,
    expected_id: str,
    expected_host: list[int],
    expected_write_upload: list[int],
    expected_reference: list[int],
    expected_gpu: list[int],
    expected_xy: list[int],
    expected_delta: list[int],
) -> None:
    require_text(owner, "id", expected_id)
    require_array(owner, "previousHostBoundaryRgba8", expected_host)
    require_array(owner, "previousWriteOrUploadBoundaryRgba8", expected_write_upload)
    require_array(owner, "outputReferenceRgba", expected_reference)
    require_array(owner, "outputComparisonGpuRgba", expected_gpu)
    require_array(owner, "outputReadbackXy", expected_xy)
    require_array(owner, "outputReadbackRgba", expected_gpu)
    require_array(owner, "outputSignedDeltaRgba", expected_delta)
    if owner.get("outputMaxChannelDelta") != 1:
        fail(f"{expected_id} outputMaxChannelDelta expected 1, got {owner.get('outputMaxChannelDelta')}")
    if owner.get("readbackMatchesComparisonGpu") is not True:
        fail(f"{expected_id} readback must match the GPU comparison bytes")
    verdict = owner.get("verdict")
    if not isinstance(verdict, str) or "output/readback boundary" not in verdict:
        fail(f"{expected_id} verdict must name the output/readback boundary")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-256")
    require_text(probe, "probe", "shader-store-reference-boundary-audit")
    require_text(probe, "propertyName", PROPERTY_NAME)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    if probe.get("defaultEnabled") is not False:
        fail("FOR-256 output/readback probe must be disabled by default")
    if probe.get("runtimeSnapshotsEnabled") is not True:
        fail("test evidence must prove opt-in snapshots were enabled")
    if probe.get("shaderLayoutChanged") is not False:
        fail("FOR-256 must not change shader or pipeline layout")
    if probe.get("correctionApplied") is not False:
        fail("FOR-256 must not apply a correction")

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    expected_observed = {
        "for255HostObserved": True,
        "for255WriteUploadObserved": True,
        "shaderObserved": "notUsed",
        "outputReadbackObserved": True,
        "referenceComparisonObserved": True,
    }
    for field, expected in expected_observed.items():
        if observed.get(field) != expected:
            fail(f"observedBoundaries.{field} expected {expected}, got {observed.get(field)}")

    shader = probe.get("shaderSideProbe")
    if not isinstance(shader, dict):
        fail("missing shaderSideProbe")
    require_text(shader, "status", "notUsed")
    reason = shader.get("reason")
    if not isinstance(reason, str) or "not a storage-buffer shader probe" not in reason:
        fail("shaderSideProbe reason must document why shader-side layout is unchanged")

    layout = probe.get("pipelineLayoutDifference")
    if not isinstance(layout, str) or "none" not in layout or "does not add WGSL storage" not in layout:
        fail("pipelineLayoutDifference must document no shader/pipeline layout difference")

    legacy = probe.get("legacySourceUniform")
    if not isinstance(legacy, dict):
        fail("missing legacySourceUniform")
    validate_case(
        legacy,
        expected_id="legacy-source-color-uniform.simple-offset-row1-col0",
        expected_host=[255, 0, 0, 102],
        expected_write_upload=[255, 0, 0, 102],
        expected_reference=[158, 90, 139, 255],
        expected_gpu=[157, 90, 138, 255],
        expected_xy=[40, 40],
        expected_delta=[-1, 0, -1, 0],
    )

    bitmap = probe.get("bitmapTexelUpload")
    if not isinstance(bitmap, dict):
        fail("missing bitmapTexelUpload")
    validate_case(
        bitmap,
        expected_id="bitmap-texel-upload-sample.bitmap-rect-nearest",
        expected_host=[149, 193, 207, 255],
        expected_write_upload=[149, 193, 207, 255],
        expected_reference=[149, 193, 207, 255],
        expected_gpu=[148, 193, 207, 255],
        expected_xy=[8, 24],
        expected_delta=[-1, 0, 0, 0],
    )

    remaining = probe.get("remainingBoundary")
    if remaining != "shader-consumption/blend-store/present-quantization-or-reference-byte-expectation":
        fail(f"remainingBoundary changed: {remaining}")


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for256" in json.dumps(route_prepass).lower():
        fail("FOR-256 should not mutate crop route metadata")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-256 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    device_text = require_file_text(
        WEBGPU_DEVICE,
        [
            PROPERTY_NAME,
            "OutputReadbackBoundarySnapshot",
            "recordOutputReadbackBoundary",
            "outputReadbackBoundarySnapshot",
        ],
    )
    for forbidden in ("StorageBuffer", "correctionApplied = true"):
        if forbidden in device_text:
            fail(f"FOR-256 device patch must not add `{forbidden}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-256 output readback boundary isolates remaining rgb residual",
            "shader-store-reference-boundary-for256.json",
            "FOR256_OUTPUT_READBACK_BOUNDARY_PROPERTY",
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-256 Shader Store Reference Boundary",
            "Decision: `KEEP_DIAGNOSTIC`",
            PROPERTY_NAME,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-256 validation passed: final readback bytes match the GPU "
        "comparison bytes for both residual cases, shader layout is unchanged, "
        "and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
