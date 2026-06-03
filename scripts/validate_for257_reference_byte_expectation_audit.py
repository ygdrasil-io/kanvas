#!/usr/bin/env python3
"""Validate FOR-257 reference byte expectation audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "reference-byte-expectation-audit-for257.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/reference-byte-expectation-audit-for257"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/reference-byte-expectation-audit-for257"
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-257-reference-byte-expectation-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "shader-consumption/blend-store/present-quantization"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-257 validation failed: {message}")


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


def require_bool(owner: dict[str, Any], field: str, expected: bool) -> None:
    actual = owner.get(field)
    if actual is not expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def reconstruction_by_name(case: dict[str, Any], name: str) -> dict[str, Any]:
    reconstructions = case.get("reconstructions")
    if not isinstance(reconstructions, list):
        fail(f"{case.get('id')} missing reconstructions")
    for reconstruction in reconstructions:
        if reconstruction.get("name") == name:
            return reconstruction
    fail(f"{case.get('id')} missing reconstruction `{name}`")


def validate_reconstruction(
    case: dict[str, Any],
    *,
    name: str,
    expected_rgba: list[int],
    matches_reference: bool,
    matches_gpu: bool,
) -> None:
    reconstruction = reconstruction_by_name(case, name)
    require_array(reconstruction, "reconstructedRgba", expected_rgba)
    require_bool(reconstruction, "matchesReferenceBytes", matches_reference)
    require_bool(reconstruction, "matchesGpuReadbackBytes", matches_gpu)
    interpretation = reconstruction.get("interpretation")
    if not isinstance(interpretation, str) or not interpretation:
        fail(f"{case.get('id')} reconstruction `{name}` must carry interpretation text")


def validate_case(
    owner: dict[str, Any],
    *,
    expected_id: str,
    expected_route: str,
    expected_host: list[int],
    expected_write_upload: list[int],
    expected_xy: list[int],
    expected_reference: list[int],
    expected_gpu: list[int],
    expected_delta: list[int],
    required_reconstructions: dict[str, tuple[list[int], bool, bool]],
) -> None:
    require_text(owner, "id", expected_id)
    require_text(owner, "route", expected_route)
    require_array(owner, "previousHostBoundaryRgba8", expected_host)
    require_array(owner, "previousWriteOrUploadBoundaryRgba8", expected_write_upload)
    require_array(owner, "outputXy", expected_xy)
    require_array(owner, "outputReferenceRgba", expected_reference)
    require_array(owner, "outputGpuReadbackRgba", expected_gpu)
    require_array(owner, "outputSignedDeltaRgba", expected_delta)
    require_array(owner, "referenceOracleRgba", expected_reference)
    require_bool(owner, "referenceOracleMatchesComparison", True)
    require_bool(owner, "referenceByteExpectationExplainsResidual", False)
    if owner.get("outputMaxChannelDelta") != 1:
        fail(f"{expected_id} outputMaxChannelDelta expected 1, got {owner.get('outputMaxChannelDelta')}")
    for name, (expected_rgba, matches_reference, matches_gpu) in required_reconstructions.items():
        validate_reconstruction(
            owner,
            name=name,
            expected_rgba=expected_rgba,
            matches_reference=matches_reference,
            matches_gpu=matches_gpu,
        )
    verdict = owner.get("verdict")
    if not isinstance(verdict, str) or "no reference-byte correction is justified" not in verdict:
        fail(f"{expected_id} verdict must reject a reference-byte correction")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-257")
    require_text(probe, "probe", "reference-byte-expectation-audit")
    require_text(probe, "newRendererProperty", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", True)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "shaderLayoutChanged", False)
    require_bool(probe, "shaderSideProbeRequiredNext", True)
    if probe.get("correctionApplied") is not False:
        fail("FOR-257 must not apply a correction")

    reused = probe.get("reusedRuntimeProperties")
    expected_reused = [
        "kanvas.webgpu.rawColorSentinels",
        "kanvas.webgpu.for256.outputReadbackBoundary",
    ]
    if reused != expected_reused:
        fail(f"reusedRuntimeProperties expected {expected_reused}, got {reused}")

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    expected_observed = {
        "for255HostObserved": True,
        "for255WriteUploadObserved": True,
        "for256OutputReadbackObserved": True,
        "referenceOracleReconstructed": True,
        "shaderObserved": "notCaptured",
    }
    for field, expected in expected_observed.items():
        if observed.get(field) != expected:
            fail(f"observedBoundaries.{field} expected {expected}, got {observed.get(field)}")

    finding = probe.get("referenceByteExpectationFinding")
    if not isinstance(finding, str) or "not_proven" not in finding:
        fail("referenceByteExpectationFinding must state not_proven")
    correction = probe.get("admissibleCorrection")
    if not isinstance(correction, str) or "none_applied" not in correction:
        fail("admissibleCorrection must state none_applied")

    legacy = probe.get("legacySourceUniform")
    if not isinstance(legacy, dict):
        fail("missing legacySourceUniform")
    validate_case(
        legacy,
        expected_id="legacy-source-color-uniform.simple-offset-row1-col0",
        expected_route="webgpu.canvas.draw-rect.src-over",
        expected_host=[255, 0, 0, 102],
        expected_write_upload=[255, 0, 0, 102],
        expected_xy=[40, 40],
        expected_reference=[158, 90, 139, 255],
        expected_gpu=[157, 90, 138, 255],
        expected_delta=[-1, 0, -1, 0],
        required_reconstructions={
            "skia-upstream-reference-png-sample": ([158, 90, 139, 255], True, False),
            "reference-byte-roundtrip-quantization": ([158, 90, 139, 255], True, False),
            "local-source-over-present-formula": ([220, 153, 145, 255], False, False),
        },
    )

    bitmap = probe.get("bitmapTexelUpload")
    if not isinstance(bitmap, dict):
        fail("missing bitmapTexelUpload")
    validate_case(
        bitmap,
        expected_id="bitmap-texel-upload-sample.bitmap-rect-nearest",
        expected_route="webgpu.image-rect.strict-nearest",
        expected_host=[149, 193, 207, 255],
        expected_write_upload=[149, 193, 207, 255],
        expected_xy=[8, 24],
        expected_reference=[149, 193, 207, 255],
        expected_gpu=[148, 193, 207, 255],
        expected_delta=[-1, 0, 0, 0],
        required_reconstructions={
            "bitmap-nearest-reference-png-sample": ([149, 193, 207, 255], True, False),
            "nearest-upload-texel-reference-expectation": ([149, 193, 207, 255], True, False),
            "reference-byte-roundtrip-quantization": ([149, 193, 207, 255], True, False),
        },
    )


def validate_route_prepass(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")
    if "for257" in json.dumps(route_prepass).lower():
        fail("FOR-257 should not mutate crop route metadata")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-257 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)
    validate_route_prepass(ROUTE_PREPASS)
    validate_route_prepass(STATIC_ROUTE_PREPASS)

    device_text = require_file_text(
        WEBGPU_DEVICE,
        [
            "RAW_COLOR_SENTINELS_PROPERTY",
            "OUTPUT_READBACK_BOUNDARY_PROPERTY",
        ],
    )
    for forbidden in (
        "reference-byte-expectation-audit",
        "shaderSideProbeRequiredNext",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-257 must not add device/render-path `{forbidden}`")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-257 reference byte expectation audit preserves shader-side boundary",
            "reference-byte-expectation-audit-for257.json",
            "local-source-over-present-formula",
            REMAINING_BOUNDARY,
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-257 Reference Byte Expectation Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "shader-consumption/blend-store/present-quantization",
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-257 validation passed: reference/oracle bytes reconstruct for both "
        "residual cases, no reference-byte correction is proven, the remaining "
        "boundary is shader-consumption/blend-store/present-quantization, and "
        "Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
