#!/usr/bin/env python3
"""Validate FOR-265 RGBA16Float quantization family scope evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "rgba16float-quantization-family-scope-for265.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/rgba16float-quantization-family-scope-for265"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/rgba16float-quantization-family-scope-for265"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For265Rgba16FloatQuantizationFamilyScopeTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-265-rgba16float-quantization-family-scope.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
MISSING_CONDITION = (
    "missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend"
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-265 validation failed: {message}")


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


def require_number(owner: dict[str, Any], field: str, expected: int | float) -> None:
    actual = owner.get(field)
    if actual != expected:
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


def case_by_id(probe: dict[str, Any], case_id: str) -> dict[str, Any]:
    cases = probe.get("cases")
    if not isinstance(cases, list):
        fail("missing cases array")
    for case in cases:
        if isinstance(case, dict) and case.get("id") == case_id:
            return case
    fail(f"missing case `{case_id}`")


def require_policy(
    owner: dict[str, Any],
    *,
    policy: str,
    total: int,
    matching: int,
    similarity: float,
    max_delta: int,
    reference: list[int],
    observed: list[int],
    delta: list[int],
    route: str,
    classification: str,
) -> None:
    require_text(owner, "policy", policy)
    require_text(owner, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_number(owner, "totalPixels", total)
    require_number(owner, "matchingPixels", matching)
    require_number(owner, "exactSimilarity", similarity)
    require_number(owner, "maxDelta", max_delta)
    require_array(owner, "referenceRgba", reference)
    require_array(owner, "observedRgba", observed)
    require_array(owner, "signedDeltaRgba", delta)
    require_text(owner, "intermediateFormat", "RGBA16Float")
    require_text(owner, "routeDiagnostics", route)
    require_text(owner, "responsibilityClassification", classification)


def require_boundary(
    owner: dict[str, Any],
    *,
    xy: list[int],
    store_float: list[float],
    store_rgba8: list[int],
    presented: list[int],
    simulated_float: list[float],
    simulated_store: list[int],
    simulated_present: list[int],
    classification: str,
) -> None:
    require_array(owner, "shaderSideXy", xy)
    require_text(owner, "intermediateFormat", "RGBA16Float")
    require_float_array(owner, "intermediateStoreExpectedRgbaFloat", store_float)
    require_array(owner, "intermediateStoreExpectedRgba8", store_rgba8)
    require_array(owner, "presentedObservedRgba", presented)
    require_array(owner, "presentedReconstructedFromIntermediateRgba", presented)
    require_float_array(owner, "simulatedQuantizedStoreRgbaFloat", simulated_float)
    require_array(owner, "simulatedQuantizedStoreRgba8", simulated_store)
    require_array(owner, "simulatedQuantizedPresentRgba", simulated_present)
    require_text(owner, "boundaryClassification", classification)


def validate_simple_offset(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "residual-route.simple-offsetimagefilter")
    require_text(case, "sceneId", "simple-offsetimagefilter")
    require_text(case, "kind", "for261-residual-rgba16float-present-boundary")
    require_text(case, "correctionStatus", "CORRECTION_SIGNAL_DIAGNOSTIC_ONLY")
    require_text(case, "admissibility", "KEEP_DIAGNOSTIC")
    require_policy(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        total=128000,
        matching=121378,
        similarity=94.8265625,
        max_delta=1,
        reference=[158, 90, 139, 255],
        observed=[157, 90, 138, 255],
        delta=[-1, 0, -1, 0],
        route="webgpu.image-filter.offset-crop-prepass-and-src-over",
        classification="rgba16float-intermediate-store-to-present-byte-quantization-policy",
    )
    require_boundary(
        case.get("boundary", {}),
        xy=[40, 40],
        store_float=[0.7597656, 0.35961914, 0.5996094, 1.0],
        store_rgba8=[194, 92, 153, 255],
        presented=[157, 90, 138, 255],
        simulated_float=[0.7607843, 0.36078432, 0.6, 1.0],
        simulated_store=[194, 92, 153, 255],
        simulated_present=[158, 90, 139, 255],
        classification="rgba16float-present-byte-quantization-residual",
    )


def validate_bitmap(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "residual-route.bitmap-rect-nearest")
    require_text(case, "sceneId", "bitmap-rect-nearest")
    require_text(case, "kind", "for261-residual-rgba16float-present-boundary")
    require_policy(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        total=4096,
        matching=3792,
        similarity=92.578125,
        max_delta=1,
        reference=[93, 97, 171, 255],
        observed=[93, 96, 171, 255],
        delta=[0, -1, 0, 0],
        route="webgpu.image-rect.strict-nearest",
        classification="rgba16float-intermediate-store-to-present-byte-quantization-policy",
    )
    require_boundary(
        case.get("boundary", {}),
        xy=[8, 24],
        store_float=[0.47045898, 0.7998047, 0.8388672, 1.0],
        store_rgba8=[120, 204, 214, 255],
        presented=[148, 193, 207, 255],
        simulated_float=[0.47058824, 0.8, 0.8392157, 1.0],
        simulated_store=[120, 204, 214, 255],
        simulated_present=[149, 193, 207, 255],
        classification="rgba16float-present-byte-quantization-residual",
    )


def validate_exact_control(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "exact-control.black-white-rect")
    require_text(case, "sceneId", "black-white-rect")
    require_text(case, "kind", "exact-control-already-100-percent")
    require_text(case, "correctionStatus", "UNCHANGED")
    require_policy(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        total=64,
        matching=64,
        similarity=100.0,
        max_delta=0,
        reference=[255, 255, 255, 255],
        observed=[255, 255, 255, 255],
        delta=[0, 0, 0, 0],
        route="webgpu.coverage.analytic-rect",
        classification="none-needed",
    )
    boundary = case.get("boundary", {})
    require_text(boundary, "boundaryClassification", "already-exact-no-quantization-correction-needed")
    require_array(boundary, "presentedObservedRgba", [255, 255, 255, 255])
    require_array(boundary, "simulatedQuantizedPresentRgba", [255, 255, 255, 255])


def validate_target_blend(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "target-blend-sensitive.m60-target-colorspace-neutral-aa")
    require_text(case, "sceneId", "m60-target-colorspace-neutral-aa")
    require_text(case, "kind", "targetColorSpaceBlend-sensitive-control")
    require_text(case, "correctionStatus", "UNCHANGED_BY_QUANTIZATION_TARGET_BLEND_CORRECTS")
    require_policy(
        case.get("current", {}),
        policy="targetBlend-false-rgba16float",
        total=4,
        matching=2,
        similarity=50.0,
        max_delta=13,
        reference=[128, 128, 128, 255],
        observed=[115, 115, 115, 255],
        delta=[-13, -13, -13, 0],
        route="webgpu.present-pass.srgb-to-rec2020-after-blend",
        classification="targetColorSpaceBlend-not-present-quantization",
    )
    require_policy(
        case.get("targetBlendControl", {}),
        policy="targetBlend-true-rgba16float",
        total=4,
        matching=4,
        similarity=100.0,
        max_delta=0,
        reference=[128, 128, 128, 255],
        observed=[128, 128, 128, 255],
        delta=[0, 0, 0, 0],
        route="webgpu.target-colorspace-blend.solid-coverage",
        classification="targetColorSpaceBlend-not-present-quantization",
    )
    require_boundary(
        case.get("boundary", {}),
        xy=[0, 0],
        store_float=[0.5, 0.5, 0.5, 1.0],
        store_rgba8=[128, 128, 128, 255],
        presented=[115, 115, 115, 255],
        simulated_float=[0.5019608, 0.5019608, 0.5019608, 1.0],
        simulated_store=[128, 128, 128, 255],
        simulated_present=[115, 115, 115, 255],
        classification="targetColorSpaceBlend-required-not-quantization",
    )
    verdict = case.get("verdict")
    if not isinstance(verdict, str) or "targetColorSpaceBlend=true reaches exact" not in verdict:
        fail("targetColorSpaceBlend case verdict must preserve the color-space boundary")


def validate_blend_coverage_dashboard(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "dashboard-control.src-over-stack")
    require_text(case, "sceneId", "src-over-stack")
    require_text(case, "kind", "dashboard-blend-coverage-exact-control")
    require_text(case, "correctionStatus", "UNCHANGED")
    require_text(case, "admissibility", "REFUSED_NOT_NEEDED")
    require_policy(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        total=4096,
        matching=4096,
        similarity=100.0,
        max_delta=0,
        reference=[64, 0, 128, 192],
        observed=[64, 0, 128, 192],
        delta=[0, 0, 0, 0],
        route="webgpu.blend.src-over.fixed-function",
        classification="already-exact-blend-coverage-dashboard-control",
    )
    require_boundary(
        case.get("boundary", {}),
        xy=[24, 24],
        store_float=[0.2509804, 0.0, 0.5019608, 0.7529412],
        store_rgba8=[64, 0, 128, 192],
        presented=[64, 0, 128, 192],
        simulated_float=[0.2509804, 0.0, 0.5019608, 0.7529412],
        simulated_store=[64, 0, 128, 192],
        simulated_present=[64, 0, 128, 192],
        classification="already-exact-blend-coverage-dashboard-control",
    )
    verdict = case.get("verdict")
    if not isinstance(verdict, str) or "dashboard fixed-function blend/coverage route" not in verdict:
        fail("src-over-stack verdict must name the dashboard blend/coverage route")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-265")
    require_text(probe, "probe", "rgba16float-present-byte-quantization-family-scope")
    require_text(probe, "newRendererProperty", "none")
    for field in (
        "defaultEnabled",
        "runtimeSnapshotsEnabled",
        "normalRenderingChanged",
        "normalShadersChanged",
        "normalThresholdsChanged",
        "cropPolicyChanged",
        "fallbackPolicyChanged",
        "intermediateFormatPolicyChanged",
        "targetColorSpaceBlendGloballyEnabled",
    ):
        require_bool(probe, field, False)
    require_text(probe, "currentPolicy", "targetColorSpaceBlend=false with normal RGBA16Float intermediate")
    require_number(probe, "caseCount", 5)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "correctionApplied", False)
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "missingCondition", MISSING_CONDITION)

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    for field in (
        "rgba16FloatIntermediateObserved",
        "presentByteOutputObserved",
        "for261ResidualCasesCompared",
        "bitmapImageRectObserved",
        "exactControlObserved",
        "targetColorSpaceBlendSensitiveCaseObserved",
        "dashboardBlendCoverageControlObserved",
    ):
        require_bool(observed, field, True)

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    require_bool(summary, "residualCasesCompared", True)
    require_bool(summary, "bitmapImageRectObserved", True)
    require_bool(summary, "exactControlPreserved", True)
    require_bool(summary, "targetColorSpaceBlendSignalPreserved", True)
    require_bool(summary, "dashboardBlendCoverageControlObserved", True)
    require_bool(summary, "exactControlsRegressed", False)
    require_bool(summary, "quantizationExplainsAllRequiredCases", False)
    require_bool(summary, "safeCorrectionProven", False)

    validate_simple_offset(probe)
    validate_bitmap(probe)
    validate_exact_control(probe)
    validate_target_blend(probe)
    validate_blend_coverage_dashboard(probe)


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-265 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    device_text = require_file_text(
        DEVICE,
        [
            "private val intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float",
            "targetColorSpaceBlend: Boolean = false",
            FALLBACK_REASON,
        ],
    )
    for forbidden in (
        "FOR265",
        "for265",
        "kanvas.webgpu.for265",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-265 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-265 RGBA16Float quantization family scope stays diagnostic",
            "rgba16float-quantization-family-scope-for265.json",
            "RGBA16Float",
            "targetColorSpaceBlend=true reaches exact",
            "dashboard-control.src-over-stack",
            "webgpu.blend.src-over.fixed-function",
            "KEEP_DIAGNOSTIC",
            FALLBACK_REASON,
            MISSING_CONDITION,
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-265 RGBA16Float Quantization Family Scope",
            "Decision: `KEEP_DIAGNOSTIC`",
            "RGBA16Float",
            "targetColorSpaceBlend",
            "src-over-stack",
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-265 validation passed: RGBA16Float store-to-present byte "
        "quantization is audited across residual, bitmap/image-rect, exact "
        "opaque, targetColorSpaceBlend-sensitive, and dashboard blend/coverage "
        "scenes; the result stays diagnostic, no production policy changed, "
        "and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
