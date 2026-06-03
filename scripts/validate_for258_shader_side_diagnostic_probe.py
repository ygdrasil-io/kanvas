#!/usr/bin/env python3
"""Validate FOR-258 shader-side diagnostic probe evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "shader-side-diagnostic-probe-for258.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/shader-side-diagnostic-probe-for258"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/shader-side-diagnostic-probe-for258"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/shader_side_probe_for258_diagnostic_only.wgsl"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-258-shader-side-diagnostic-probe.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROPERTY = "kanvas.webgpu.for258.shaderSideProbe"
REMAINING_BOUNDARY = "shader-consumption/blend-store-before-present"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-258 validation failed: {message}")


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
    expected_present: list[int],
    expected_reference: list[int],
    expected_gpu: list[int],
    expected_delta: list[int],
) -> None:
    require_text(owner, "id", expected_id)
    require_text(owner, "route", expected_route)
    require_array(owner, "previousHostBoundaryRgba8", expected_host)
    require_array(owner, "previousWriteOrUploadBoundaryRgba8", expected_write_upload)
    require_array(owner, "shaderSideXy", expected_xy)
    require_float_array(owner, "shaderSideObservedRgbaFloat", expected_shader_float)
    require_array(owner, "shaderSideObservedRgba8", expected_shader_rgba8)
    require_bool(owner, "shaderSideSampleValid", True)
    require_array(owner, "presentReconstructedRgba", expected_present)
    require_bool(owner, "presentReconstructionMatchesGpuReadback", True)
    require_bool(owner, "presentReconstructionMatchesReference", False)
    require_array(owner, "outputReferenceRgba", expected_reference)
    require_array(owner, "outputGpuReadbackRgba", expected_gpu)
    require_array(owner, "outputSignedDeltaRgba", expected_delta)
    require_array(owner, "referenceOracleRgba", expected_reference)
    verdict = owner.get("verdict")
    if not isinstance(verdict, str) or "no bounded normal-path correction is isolated" not in verdict:
        fail(f"{expected_id} verdict must reject a normal-path correction")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-258")
    require_text(probe, "probe", "shader-side-diagnostic-probe")
    require_text(probe, "propertyName", PROPERTY)
    require_text(probe, "diagnosticShader", "shaders/shader_side_probe_for258_diagnostic_only.wgsl")
    require_text(
        probe,
        "diagnosticPipelineLayout",
        "diagnostic-only compute layout: binding0 intermediate texture, binding1 storage buffer",
    )
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", True)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "diagnosticLayoutChanged", True)
    require_bool(probe, "shaderLayoutChangedForNormalPath", False)
    require_bool(probe, "correctionApplied", False)

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
    for field in (
        "for255HostObserved",
        "for255WriteUploadObserved",
        "for256OutputReadbackObserved",
        "for257ReferenceOracleReconstructed",
        "shaderSideIntermediateObserved",
        "presentReconstructedFromShaderSide",
    ):
        if observed.get(field) is not True:
            fail(f"observedBoundaries.{field} expected true, got {observed.get(field)}")

    validate_case(
        probe.get("legacySourceUniform", {}),
        expected_id="legacy-source-color-uniform.simple-offset-row1-col0",
        expected_route="webgpu.canvas.draw-rect.src-over",
        expected_host=[255, 0, 0, 102],
        expected_write_upload=[255, 0, 0, 102],
        expected_xy=[40, 40],
        expected_shader_float=[0.7597656, 0.35961914, 0.5996094, 1.0],
        expected_shader_rgba8=[194, 92, 153, 255],
        expected_present=[157, 90, 138, 255],
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
        expected_present=[148, 193, 207, 255],
        expected_reference=[149, 193, 207, 255],
        expected_gpu=[148, 193, 207, 255],
        expected_delta=[-1, 0, 0, 0],
    )

    interpretation = probe.get("interpretation")
    if not isinstance(interpretation, str) or "No bounded normal-path correction is isolated" not in interpretation:
        fail("interpretation must reject a bounded correction")
    method = probe.get("observationMethod")
    if not isinstance(method, str) or "normal render shaders" not in method or "fallback policy" not in method:
        fail("observationMethod must document normal-path isolation")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-258 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    device_text = require_file_text(
        DEVICE,
        [
            "FOR258_SHADER_SIDE_PROBE_PROPERTY",
            "FOR258_SHADER_SIDE_PROBE_SHADER",
            "for258ShaderSideProbePipelineLazy",
            "for258ShaderSideProbePipelineLazy.isInitialized()",
            "for258ShaderSideProbeShaderLazy.isInitialized()",
            "GPUBufferBindingType.Storage",
            "SkWebGpuDevice.for258ShaderSideProbe.pipeline.diagnosticOnly",
        ],
    )
    for forbidden in (
        "correctionApplied = true",
        "image-filter.crop-input-nonnull-prepass-required\".replace",
    ):
        if forbidden in device_text:
            fail(f"FOR-258 must not add normal-path correction or mutate Crop diagnostics: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-258 shader-side diagnostic probe isolates pre-present boundary",
            PROPERTY,
            "shader-side-diagnostic-probe-for258.json",
            "normalRenderingChanged",
            REMAINING_BOUNDARY,
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        SHADER,
        [
            "FOR-258 diagnostic-only shader-side probe",
            "@group(0) @binding(0) var intermediate_texture",
            "@group(0) @binding(1) var<storage, read_write> probe_values",
            "textureLoad(intermediate_texture",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-258 Shader-Side Diagnostic Probe",
            "Decision: `KEEP_DIAGNOSTIC`",
            PROPERTY,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-258 validation passed: diagnostic shader-side samples are isolated, "
        "generated/static evidence matches, normal rendering remains unchanged, "
        "no correction is applied, and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
