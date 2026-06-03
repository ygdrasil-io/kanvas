#!/usr/bin/env python3
"""Validate FOR-266 stroke cap/join AA residual diagnostic evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "stroke-cap-join-aa-residual-for266.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/stroke-cap-join-aa-residual-for266"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
CONTRACTS = PROJECT_ROOT / "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For266StrokeCapJoinAaResidualAuditTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-266-stroke-cap-join-aa-residual.md"
FALLBACK_REASON = "coverage.stroke-cap-join-visual-parity-below-threshold"
CROP_FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
MISSING_CONDITION = "missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells"
REMAINING_BOUNDARY = "coverage.stroke-cap-join-aa-residual"
POLICIES = ("targetBlend-false-rgba16float", "targetBlend-true-rgba16float")


def fail(message: str) -> None:
    raise SystemExit(f"FOR-266 validation failed: {message}")


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


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def policy_by_id(probe: dict[str, Any], policy_id: str) -> dict[str, Any]:
    policies = probe.get("policies")
    if not isinstance(policies, list):
        fail("missing policies array")
    for policy in policies:
        if isinstance(policy, dict) and policy.get("policy") == policy_id:
            return policy
    fail(f"missing policy `{policy_id}`")


def region_by_id(policy: dict[str, Any], region_id: str) -> dict[str, Any]:
    residual = policy.get("residual")
    if not isinstance(residual, dict):
        fail(f"policy `{policy.get('policy')}` missing residual object")
    regions = residual.get("regions")
    if not isinstance(regions, list):
        fail(f"policy `{policy.get('policy')}` missing residual regions")
    for region in regions:
        if isinstance(region, dict) and region.get("id") == region_id:
            return region
    fail(f"policy `{policy.get('policy')}` missing region `{region_id}`")


def require_bounds(owner: dict[str, Any], expected: dict[str, int]) -> None:
    bounds = owner.get("bounds")
    if bounds != expected:
        fail(f"region `{owner.get('id')}` bounds expected {expected}, got {bounds}")


def validate_policy(
    probe: dict[str, Any],
    *,
    policy_id: str,
    target_blend: bool,
    matching: int,
    similarity: float,
    max_delta: int,
    representative: dict[str, Any],
    boundary: str,
    mismatch: int,
    one_unit: int,
    gt8: int,
    gt32: int,
    dominant: str,
    plausibility: str,
) -> dict[str, Any]:
    policy = policy_by_id(probe, policy_id)
    require_text(policy, "policy", policy_id)
    require_text(policy, "evaluationKind", "whole-scene-reference-vs-live-webgpu-experimental-stroke-cap-join")
    require_bool(policy, "targetColorSpaceBlend", target_blend)
    require_text(policy, "intermediateFormat", "RGBA16Float")
    require_number(policy, "totalPixels", 24576)
    require_number(policy, "matchingPixels", matching)
    require_number(policy, "exactSimilarity", similarity)
    require_number(policy, "maxDelta", max_delta)
    require_text(policy, "routeDiagnostics", "webgpu.coverage.stroke-cap-join.experimental-render")
    require_text(policy, "normalRoute", "webgpu.coverage.refuse")
    require_text(policy, "fallbackReason", FALLBACK_REASON)
    require_text(policy, "supportDecision", "REFUSED_INSUFFICIENT_PARITY")
    rationale = policy.get("routeDiagnosticsRationale")
    if not isinstance(rationale, str) or f"targetColorSpaceBlend={str(target_blend).lower()}" not in rationale:
        fail(f"policy `{policy_id}` rationale must name targetColorSpaceBlend={target_blend}")

    rep = policy.get("representativePixel")
    if not isinstance(rep, dict):
        fail(f"policy `{policy_id}` missing representativePixel")
    for field, expected in representative.items():
        if isinstance(expected, list):
            require_array(rep, field, expected)
        else:
            require_number(rep, field, expected)

    residual = policy.get("residual")
    if not isinstance(residual, dict):
        fail(f"policy `{policy_id}` missing residual")
    require_text(residual, "boundaryClassification", boundary)
    require_number(residual, "mismatchPixels", mismatch)
    require_number(residual, "oneUnitMismatchPixels", one_unit)
    require_number(residual, "greaterThanEightPixels", gt8)
    require_number(residual, "greaterThanThirtyTwoPixels", gt32)
    require_number(residual, "maxChannelDelta", max_delta)
    require_text(residual, "dominantRegion", dominant)
    require_text(residual, "boundedCoverageCorrectionPlausibility", plausibility)
    require_text(residual, "nextMissingCondition", MISSING_CONDITION)
    samples = residual.get("highDeltaSamples")
    if not isinstance(samples, list) or not samples:
        fail(f"policy `{policy_id}` must include highDeltaSamples")
    if len(samples) > 16:
        fail(f"policy `{policy_id}` highDeltaSamples must stay bounded, got {len(samples)}")
    return policy


def validate_regions(policy: dict[str, Any], expected: dict[str, tuple[int, int, int, int, int, dict[str, int]]]) -> None:
    for region_id, values in expected.items():
        mismatch, one_unit, gt8, gt32, max_delta, bounds = values
        region = region_by_id(policy, region_id)
        require_number(region, "mismatchPixels", mismatch)
        require_number(region, "oneUnitMismatchPixels", one_unit)
        require_number(region, "greaterThanEightPixels", gt8)
        require_number(region, "greaterThanThirtyTwoPixels", gt32)
        require_number(region, "maxChannelDelta", max_delta)
        require_bounds(region, bounds)


def validate_high_delta(policy: dict[str, Any], *, max_delta: int, gpu: list[int], signed: list[int]) -> None:
    sample = policy["residual"]["highDeltaSamples"][0]
    require_number(sample, "x", 92)
    require_number(sample, "y", 75)
    require_text(sample, "region", "round-round")
    require_array(sample, "referenceRgba", [133, 150, 214, 255])
    require_array(sample, "gpuRgba", gpu)
    require_array(sample, "signedDeltaRgba", signed)
    require_number(sample, "maxChannelDelta", max_delta)


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-266")
    require_text(probe, "probe", "stroke-cap-join-aa-residual-after-targetColorSpaceBlend")
    require_text(probe, "sceneId", "m60-bounded-stroke-cap-join")
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
    require_text(probe, "productionRoute", "webgpu.coverage.refuse")
    production_refusal = probe.get("productionRefusal")
    if not isinstance(production_refusal, str) or FALLBACK_REASON not in production_refusal:
        fail("productionRefusal must preserve the stroke cap/join fallback reason")
    require_text(probe, "fallbackReason", FALLBACK_REASON)
    require_number(probe, "policyCount", 2)
    require_number(probe, "supportThreshold", 99.95)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "correctionApplied", False)
    require_text(probe, "preservedUnsupportedReason", CROP_FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "missingCondition", MISSING_CONDITION)
    actual_policies = [policy.get("policy") for policy in probe.get("policies", [])]
    if actual_policies != list(POLICIES):
        fail(f"policy order expected {list(POLICIES)}, got {actual_policies}")

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    for field in (
        "targetBlendFalseObserved",
        "targetBlendTrueObserved",
        "rgba16FloatIntermediateObserved",
        "targetBlendTrueImprovesSimilarity",
        "targetBlendTrueStillBelowThreshold",
        "residualLocalizedToCapJoinRegions",
    ):
        require_bool(summary, field, True)
    require_bool(summary, "safeCoverageCorrectionProven", False)

    false_policy = validate_policy(
        probe,
        policy_id="targetBlend-false-rgba16float",
        target_blend=False,
        matching=22019,
        similarity=89.59554036458333,
        max_delta=39,
        representative={
            "x": 9,
            "y": 27,
            "referenceRgba": [192, 192, 192, 255],
            "gpuRgba": [184, 184, 184, 255],
            "signedDeltaRgba": [-8, -8, -8, 0],
            "maxChannelDelta": 8,
        },
        boundary="color-space.target-blend-required-plus-coverage.stroke-cap-join-aa-residual",
        mismatch=2557,
        one_unit=794,
        gt8=1738,
        gt32=8,
        dominant="square-bevel",
        plausibility="NOT_PROVEN",
    )
    validate_regions(
        false_policy,
        {
            "butt-bevel": (734, 387, 345, 1, 38, {"left": 9, "top": 27, "right": 47, "bottom": 92}),
            "round-round": (879, 407, 453, 7, 39, {"left": 48, "top": 27, "right": 95, "bottom": 92}),
            "square-bevel": (944, 0, 940, 0, 22, {"left": 96, "top": 27, "right": 191, "bottom": 92}),
        },
    )
    validate_high_delta(false_policy, max_delta=39, gpu=[168, 189, 229, 255], signed=[35, 39, 15, 0])

    true_policy = validate_policy(
        probe,
        policy_id="targetBlend-true-rgba16float",
        target_blend=True,
        matching=23572,
        similarity=95.91471354166667,
        max_delta=48,
        representative={
            "x": 9,
            "y": 27,
            "referenceRgba": [192, 192, 192, 255],
            "gpuRgba": [191, 191, 191, 255],
            "signedDeltaRgba": [-1, -1, -1, 0],
            "maxChannelDelta": 1,
        },
        boundary="coverage.stroke-cap-join-aa-residual-after-targetColorSpaceBlend",
        mismatch=1004,
        one_unit=994,
        gt8=10,
        gt32=6,
        dominant="round-round",
        plausibility="PLAUSIBLE_BUT_NOT_PROVEN",
    )
    validate_regions(
        true_policy,
        {
            "butt-bevel": (394, 392, 2, 0, 25, {"left": 9, "top": 27, "right": 47, "bottom": 92}),
            "round-round": (523, 515, 8, 6, 48, {"left": 48, "top": 38, "right": 95, "bottom": 81}),
            "square-bevel": (87, 87, 0, 0, 1, {"left": 96, "top": 37, "right": 153, "bottom": 82}),
        },
    )
    validate_high_delta(true_policy, max_delta=48, gpu=[181, 191, 230, 255], signed=[48, 41, 16, 0])


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-266 probe JSON differ")

    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    device_text = require_file_text(
        DEVICE,
        [
            "private val intermediateFormat: GPUTextureFormat = GPUTextureFormat.RGBA16Float",
            "targetColorSpaceBlend: Boolean = false",
            "kanvas.webgpu.strokeCapJoin.experimentalRender",
        ],
    )
    for forbidden in (
        "FOR266",
        "for266",
        "kanvas.webgpu.for266",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-266 must not add production switches or corrections: `{forbidden}`")

    require_file_text(CONTRACTS, [FALLBACK_REASON])

    require_file_text(
        TEST,
        [
            "FOR-266 stroke cap join AA residual stays diagnostic after targetColorSpaceBlend",
            "GPUTextureFormat.RGBA16Float",
            "targetColorSpaceBlend = policy.targetColorSpaceBlend",
            "kanvas.webgpu.strokeCapJoin.experimentalRender",
            "stroke-cap-join-aa-residual-for266.json",
            "KEEP_DIAGNOSTIC",
            "PLAUSIBLE_BUT_NOT_PROVEN",
            MISSING_CONDITION,
            CROP_FALLBACK_REASON,
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-266 Stroke Cap/Join AA Residual Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "targetBlend-false-rgba16float",
            "targetBlend-true-rgba16float",
            "89.59554",
            "95.914714",
            "PLAUSIBLE_BUT_NOT_PROVEN",
            "REFUSED_INSUFFICIENT_PARITY",
            FALLBACK_REASON,
            CROP_FALLBACK_REASON,
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
        ],
    )

    print(
        "FOR-266 validation passed: stroke cap/join AA residual evidence is "
        "diagnostic-only, compares targetColorSpaceBlend=false/true with normal "
        "RGBA16Float, localizes residuals by cap/join region, preserves production "
        "refusal and Crop diagnostics, and keeps support decision KEEP_DIAGNOSTIC."
    )


if __name__ == "__main__":
    main()
