#!/usr/bin/env python3
"""Validate FOR-259 intermediate store/present audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "intermediate-store-present-audit-for259.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/intermediate-store-present-audit-for259"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/intermediate-store-present-audit-for259"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-259-intermediate-store-present-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROPERTY_FOR258 = "kanvas.webgpu.for258.shaderSideProbe"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-259 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_bool(owner: dict[str, Any], field: str, expected: bool) -> None:
    actual = owner.get(field)
    if actual is not expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_array(owner: dict[str, Any], field: str, expected: list[int]) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_float_array(owner: dict[str, Any], field: str, expected: list[float]) -> None:
    actual = owner.get(field)
    if not isinstance(actual, list) or len(actual) != len(expected):
        fail(f"`{field}` expected {expected}, got {actual}")
    for index, (actual_value, expected_value) in enumerate(zip(actual, expected)):
        if abs(float(actual_value) - expected_value) > 0.000001:
            fail(f"`{field}`[{index}] expected {expected_value}, got {actual_value}")


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
    expected_route: str,
    expected_host: list[int],
    expected_write_upload: list[int],
    expected_xy: list[int],
    expected_shader_float: list[float],
    expected_shader_rgba8: list[int],
    expected_current_present: list[int],
    expected_rgba8_store_float: list[float],
    expected_rgba8_store_present: list[int],
    expected_reference: list[int],
    expected_gpu: list[int],
    expected_delta: list[int],
) -> None:
    require_text(owner, "id", expected_id)
    require_text(owner, "route", expected_route)
    require_array(owner, "previousHostBoundaryRgba8", expected_host)
    require_array(owner, "previousWriteOrUploadBoundaryRgba8", expected_write_upload)
    require_array(owner, "shaderSideXy", expected_xy)
    require_float_array(owner, "for258ShaderSideRgbaFloat", expected_shader_float)
    require_array(owner, "for258ShaderSideRgba8", expected_shader_rgba8)
    require_array(owner, "currentRgba16FloatPresentReconstructedRgba", expected_current_present)
    require_bool(owner, "currentRgba16FloatPresentMatchesGpuReadback", True)
    require_bool(owner, "currentRgba16FloatPresentMatchesReference", False)
    require_float_array(owner, "rgba8StoreBeforePresentRgbaFloat", expected_rgba8_store_float)
    require_array(owner, "rgba8StoreBeforePresentRgba8", expected_shader_rgba8)
    require_array(owner, "rgba8StoreBeforePresentReconstructedRgba", expected_rgba8_store_present)
    require_bool(owner, "rgba8StoreBeforePresentMatchesGpuReadback", False)
    require_bool(owner, "rgba8StoreBeforePresentMatchesReference", True)
    require_array(owner, "outputReferenceRgba", expected_reference)
    require_array(owner, "outputGpuReadbackRgba", expected_gpu)
    require_array(owner, "outputSignedDeltaRgba", expected_delta)
    require_array(owner, "referenceOracleRgba", expected_reference)
    verdict = owner.get("verdict")
    if not isinstance(verdict, str) or "intermediate store-to-present byte-quantization policy" not in verdict:
        fail(f"{expected_id} verdict must name the narrowed boundary")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-259")
    require_text(probe, "probe", "intermediate-store-present-audit")
    require_text(probe, "newRendererProperty", "none")
    require_text(probe, "intermediateFormat", "RGBA16Float")
    require_text(
        probe,
        "boundedDiagnosticAlternative",
        "test-side RGBA8Unorm store/load simulation before present reconstruction",
    )
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", True)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "normalShadersChanged", False)
    require_bool(probe, "normalThresholdsChanged", False)
    require_bool(probe, "cropPolicyChanged", False)
    require_bool(probe, "fallbackPolicyChanged", False)
    require_bool(probe, "targetColorSpaceBlendGloballyEnabled", False)
    require_bool(probe, "correctionApplied", False)

    reused = probe.get("reusedRuntimeProperties")
    expected_reused = [
        "kanvas.webgpu.rawColorSentinels",
        "kanvas.webgpu.for256.outputReadbackBoundary",
        PROPERTY_FOR258,
    ]
    if reused != expected_reused:
        fail(f"reusedRuntimeProperties expected {expected_reused}, got {reused}")

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    for field in (
        "for255HostObserved",
        "for255WriteUploadObserved",
        "for256OutputReadbackObserved",
        "for257ReferenceOracleReconstructed",
        "for258ShaderSideRgba16FloatObserved",
        "currentPresentReconstructedFromRgba16Float",
        "rgba8StoreBeforePresentSimulated",
    ):
        require_bool(observed, field, True)

    validate_case(
        probe.get("legacySourceUniform", {}),
        expected_id="legacy-source-color-uniform.simple-offset-row1-col0",
        expected_route="webgpu.canvas.draw-rect.src-over",
        expected_host=[255, 0, 0, 102],
        expected_write_upload=[255, 0, 0, 102],
        expected_xy=[40, 40],
        expected_shader_float=[0.7597656, 0.35961914, 0.5996094, 1.0],
        expected_shader_rgba8=[194, 92, 153, 255],
        expected_current_present=[157, 90, 138, 255],
        expected_rgba8_store_float=[0.7607843, 0.36078432, 0.6, 1.0],
        expected_rgba8_store_present=[158, 90, 139, 255],
        expected_reference=[158, 90, 139, 255],
        expected_gpu=[157, 90, 138, 255],
        expected_delta=[-1, 0, -1, 0],
    )
    validate_case(
        probe.get("bitmapTexelUpload", {}),
        expected_id="bitmap-texel-upload-sample.bitmap-rect-nearest",
        expected_route="webgpu.image-rect.strict-nearest",
        expected_host=[149, 193, 207, 255],
        expected_write_upload=[149, 193, 207, 255],
        expected_xy=[8, 24],
        expected_shader_float=[0.47045898, 0.7998047, 0.8388672, 1.0],
        expected_shader_rgba8=[120, 204, 214, 255],
        expected_current_present=[148, 193, 207, 255],
        expected_rgba8_store_float=[0.47058824, 0.8, 0.8392157, 1.0],
        expected_rgba8_store_present=[149, 193, 207, 255],
        expected_reference=[149, 193, 207, 255],
        expected_gpu=[148, 193, 207, 255],
        expected_delta=[-1, 0, 0, 0],
    )

    finding = probe.get("formatStoreFinding")
    if not isinstance(finding, str) or "rgba8_store_before_present_simulation_matches_reference" not in finding:
        fail("formatStoreFinding must capture the RGBA8 store-before-present comparison")
    correction = probe.get("admissibleCorrection")
    if not isinstance(correction, str) or "none_applied" not in correction:
        fail("admissibleCorrection must state none_applied")
    interpretation = probe.get("interpretation")
    if not isinstance(interpretation, str) or "does not justify changing the normal precision policy" not in interpretation:
        fail("interpretation must reject a normal-path precision-policy correction")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-259 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    device_text = require_file_text(
        DEVICE,
        [
            "private val intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float",
            "FOR258_SHADER_SIDE_PROBE_PROPERTY",
            "targetColorSpaceBlend: Boolean = false",
            FALLBACK_REASON,
        ],
    )
    for forbidden in (
        "FOR259",
        "kanvas.webgpu.for259",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-259 must not add renderer normal-path switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-259 intermediate store present audit narrows residual boundary",
            "intermediate-store-present-audit-for259.json",
            "RGBA16Float",
            "RGBA8Unorm store/load simulation before present reconstruction",
            REMAINING_BOUNDARY,
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-259 Intermediate Store Present Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "RGBA16Float",
            "RGBA8Unorm",
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-259 validation passed: RGBA16Float current store/present matches GPU "
        "readback, bounded RGBA8 store-before-present simulation matches reference "
        "bytes, no normal correction is applied, and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
