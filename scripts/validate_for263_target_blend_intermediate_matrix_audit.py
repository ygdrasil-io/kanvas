#!/usr/bin/env python3
"""Validate FOR-263 targetColorSpaceBlend x intermediateFormat matrix evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "target-blend-intermediate-matrix-audit-for263.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/target-blend-intermediate-matrix-audit-for263"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/target-blend-intermediate-matrix-audit-for263"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For263TargetBlendIntermediateMatrixAuditTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-263-target-blend-intermediate-matrix-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REFUSAL_REASON = "color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
MISSING_CONDITION = (
    "missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation"
)
POLICIES = (
    "targetBlend-false-rgba16float",
    "targetBlend-false-rgba8unorm",
    "targetBlend-true-rgba16float",
    "targetBlend-true-rgba8unorm",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-263 validation failed: {message}")


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


def require_null(owner: dict[str, Any], field: str) -> None:
    if owner.get(field) is not None:
        fail(f"`{field}` expected null, got `{owner.get(field)}`")


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


def case_by_id(probe: dict[str, Any], case_id: str) -> dict[str, Any]:
    cases = probe.get("cases")
    if not isinstance(cases, list):
        fail("missing cases array")
    for case in cases:
        if isinstance(case, dict) and case.get("id") == case_id:
            return case
    fail(f"missing case `{case_id}`")


def policy_by_id(case: dict[str, Any], policy_id: str) -> dict[str, Any]:
    policies = case.get("policies")
    if not isinstance(policies, list):
        fail(f"case `{case.get('id')}` missing policies array")
    for policy in policies:
        if isinstance(policy, dict) and policy.get("policy") == policy_id:
            return policy
    fail(f"case `{case.get('id')}` missing policy `{policy_id}`")


def require_matrix_policies(case: dict[str, Any]) -> None:
    actual = [policy.get("policy") for policy in case.get("policies", [])]
    if actual != list(POLICIES):
        fail(f"case `{case.get('id')}` policies expected {list(POLICIES)}, got {actual}")


def validate_rendered_policy(
    owner: dict[str, Any],
    *,
    policy: str,
    target_blend: bool,
    intermediate_format: str,
    total: int,
    matching: int,
    similarity: float,
    max_delta: int,
    reference: list[int],
    observed: list[int],
    signed_delta: list[int],
    route_prefix: str,
    classification: str,
) -> None:
    require_text(owner, "policy", policy)
    require_text(owner, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_text(owner, "evaluationStatus", "rendered")
    require_bool(owner, "targetColorSpaceBlend", target_blend)
    require_text(owner, "intermediateFormat", intermediate_format)
    require_number(owner, "totalPixels", total)
    require_number(owner, "matchingPixels", matching)
    require_number(owner, "exactSimilarity", similarity)
    require_number(owner, "maxDelta", max_delta)
    require_array(owner, "referenceRgba", reference)
    require_array(owner, "observedRgba", observed)
    require_array(owner, "signedDeltaRgba", signed_delta)
    route = owner.get("routeDiagnostics")
    if not isinstance(route, str) or not route.startswith(route_prefix):
        fail(f"routeDiagnostics must start with `{route_prefix}`, got `{route}`")
    rationale = owner.get("routeDiagnosticsRationale")
    expected_target = f"targetColorSpaceBlend={str(target_blend).lower()}"
    expected_format = f"intermediateFormat={intermediate_format}"
    if not isinstance(rationale, str) or expected_target not in rationale or expected_format not in rationale:
        fail(f"routeDiagnosticsRationale must name {expected_target} and {expected_format}")
    require_null(owner, "refusalReason")
    require_text(owner, "responsibilityClassification", classification)


def validate_refused_policy(
    owner: dict[str, Any],
    *,
    policy: str,
    intermediate_format: str,
    total: int,
    reference: list[int],
    classification: str,
) -> None:
    require_text(owner, "policy", policy)
    require_text(owner, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_text(owner, "evaluationStatus", "refused-before-render")
    require_bool(owner, "targetColorSpaceBlend", True)
    require_text(owner, "intermediateFormat", intermediate_format)
    require_number(owner, "totalPixels", total)
    for field in ("matchingPixels", "exactSimilarity", "maxDelta", "observedRgba", "signedDeltaRgba"):
        require_null(owner, field)
    require_array(owner, "referenceRgba", reference)
    require_text(owner, "routeDiagnostics", REFUSAL_REASON)
    require_text(owner, "refusalReason", REFUSAL_REASON)
    rationale = owner.get("routeDiagnosticsRationale")
    if not isinstance(rationale, str) or f"intermediateFormat={intermediate_format}" not in rationale:
        fail(f"refusal rationale must name intermediateFormat={intermediate_format}")
    require_text(owner, "responsibilityClassification", classification)


def validate_case_header(
    case: dict[str, Any],
    *,
    case_id: str,
    scene_id: str,
    kind: str,
    baseline: str,
    best: str,
    correction_status: str,
    admissibility: str,
    dimension: str,
) -> None:
    require_text(case, "id", case_id)
    require_text(case, "sceneId", scene_id)
    require_text(case, "kind", kind)
    require_matrix_policies(case)
    require_text(case, "baselinePolicy", baseline)
    require_text(case, "bestRenderedPolicy", best)
    require_bool(case, "regression", False)
    require_text(case, "correctionStatus", correction_status)
    require_text(case, "admissibility", admissibility)
    require_text(case, "dimensionResponsible", dimension)


def validate_positive_fixture(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "positive-fixture.m60-target-colorspace-neutral-aa")
    validate_case_header(
        case,
        case_id="positive-fixture.m60-target-colorspace-neutral-aa",
        scene_id="m60-target-colorspace-neutral-aa",
        kind="isolated-positive-target-blend-fixture",
        baseline="targetBlend-false-rgba16float",
        best="targetBlend-true-rgba16float",
        correction_status="CORRECTION_SIGNAL",
        admissibility="ADMISSIBLE_DIAGNOSTIC_ONLY",
        dimension="targetColorSpaceBlend",
    )
    for policy_id, target_blend, fmt, matching, similarity, max_delta, observed, delta in (
        ("targetBlend-false-rgba16float", False, "RGBA16Float", 2, 50.0, 13, [115, 115, 115, 255], [-13, -13, -13, 0]),
        ("targetBlend-false-rgba8unorm", False, "RGBA8Unorm", 2, 50.0, 13, [115, 115, 115, 255], [-13, -13, -13, 0]),
        ("targetBlend-true-rgba16float", True, "RGBA16Float", 4, 100.0, 0, [128, 128, 128, 255], [0, 0, 0, 0]),
        ("targetBlend-true-rgba8unorm", True, "RGBA8Unorm", 4, 100.0, 0, [128, 128, 128, 255], [0, 0, 0, 0]),
    ):
        validate_rendered_policy(
            policy_by_id(case, policy_id),
            policy=policy_id,
            target_blend=target_blend,
            intermediate_format=fmt,
            total=4,
            matching=matching,
            similarity=similarity,
            max_delta=max_delta,
            reference=[128, 128, 128, 255],
            observed=observed,
            signed_delta=delta,
            route_prefix="webgpu.target-colorspace-blend.solid-coverage",
            classification="targetColorSpaceBlend",
        )


def validate_bounded_stroke(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "insufficient-scope.m60-bounded-stroke-cap-join")
    validate_case_header(
        case,
        case_id="insufficient-scope.m60-bounded-stroke-cap-join",
        scene_id="m60-bounded-stroke-cap-join",
        kind="improves-without-exact-parity",
        baseline="targetBlend-false-rgba16float",
        best="targetBlend-true-rgba16float",
        correction_status="CORRECTION_SIGNAL_INSUFFICIENT",
        admissibility="REFUSED_INSUFFICIENT_PARITY",
        dimension="targetColorSpaceBlend-improves-but-insufficient-parity",
    )
    for policy_id, target_blend, fmt, matching, similarity, max_delta, observed, delta in (
        ("targetBlend-false-rgba16float", False, "RGBA16Float", 22019, 89.59554036458333, 39, [184, 184, 184, 255], [-8, -8, -8, 0]),
        ("targetBlend-false-rgba8unorm", False, "RGBA8Unorm", 22019, 89.59554036458333, 39, [184, 184, 184, 255], [-8, -8, -8, 0]),
        ("targetBlend-true-rgba16float", True, "RGBA16Float", 23572, 95.91471354166667, 48, [191, 191, 191, 255], [-1, -1, -1, 0]),
        ("targetBlend-true-rgba8unorm", True, "RGBA8Unorm", 23540, 95.78450520833333, 48, [191, 191, 191, 255], [-1, -1, -1, 0]),
    ):
        validate_rendered_policy(
            policy_by_id(case, policy_id),
            policy=policy_id,
            target_blend=target_blend,
            intermediate_format=fmt,
            total=24576,
            matching=matching,
            similarity=similarity,
            max_delta=max_delta,
            reference=[192, 192, 192, 255],
            observed=observed,
            signed_delta=delta,
            route_prefix="webgpu.coverage.stroke-cap-join.experimental-render",
            classification="targetColorSpaceBlend-improves-but-insufficient-parity",
        )


def validate_exact_control(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "exact-control.black-white-rect")
    validate_case_header(
        case,
        case_id="exact-control.black-white-rect",
        scene_id="black-white-rect",
        kind="already-exact-no-dimension-needed",
        baseline="targetBlend-false-rgba16float",
        best="targetBlend-false-rgba16float",
        correction_status="UNCHANGED",
        admissibility="REFUSED_NOT_NEEDED",
        dimension="none-needed",
    )
    for policy_id, target_blend, fmt in (
        ("targetBlend-false-rgba16float", False, "RGBA16Float"),
        ("targetBlend-false-rgba8unorm", False, "RGBA8Unorm"),
        ("targetBlend-true-rgba16float", True, "RGBA16Float"),
        ("targetBlend-true-rgba8unorm", True, "RGBA8Unorm"),
    ):
        validate_rendered_policy(
            policy_by_id(case, policy_id),
            policy=policy_id,
            target_blend=target_blend,
            intermediate_format=fmt,
            total=64,
            matching=64,
            similarity=100.0,
            max_delta=0,
            reference=[255, 255, 255, 255],
            observed=[255, 255, 255, 255],
            signed_delta=[0, 0, 0, 0],
            route_prefix="webgpu.coverage.analytic-rect",
            classification="none-needed",
        )


def validate_residual(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "for261-residual.simple-offsetimagefilter")
    validate_case_header(
        case,
        case_id="for261-residual.simple-offsetimagefilter",
        scene_id="simple-offsetimagefilter",
        kind="for261-residual-intermediate-boundary-control",
        baseline="targetBlend-false-rgba16float",
        best="targetBlend-false-rgba8unorm",
        correction_status="REFUSED_FOR_TARGET_BLEND_POLICIES",
        admissibility="REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND",
        dimension="intermediateFormat-when-targetColorSpaceBlend-refused",
    )
    validate_rendered_policy(
        policy_by_id(case, "targetBlend-false-rgba16float"),
        policy="targetBlend-false-rgba16float",
        target_blend=False,
        intermediate_format="RGBA16Float",
        total=128000,
        matching=121378,
        similarity=94.8265625,
        max_delta=1,
        reference=[158, 90, 139, 255],
        observed=[157, 90, 138, 255],
        signed_delta=[-1, 0, -1, 0],
        route_prefix="webgpu.image-filter.offset-crop-prepass-and-src-over",
        classification="intermediateFormat-when-targetColorSpaceBlend-refused",
    )
    validate_rendered_policy(
        policy_by_id(case, "targetBlend-false-rgba8unorm"),
        policy="targetBlend-false-rgba8unorm",
        target_blend=False,
        intermediate_format="RGBA8Unorm",
        total=128000,
        matching=123944,
        similarity=96.83125,
        max_delta=1,
        reference=[132, 87, 244, 255],
        observed=[132, 87, 243, 255],
        signed_delta=[0, 0, -1, 0],
        route_prefix="webgpu.image-filter.offset-crop-prepass-and-src-over",
        classification="intermediateFormat-when-targetColorSpaceBlend-refused",
    )
    validate_refused_policy(
        policy_by_id(case, "targetBlend-true-rgba16float"),
        policy="targetBlend-true-rgba16float",
        intermediate_format="RGBA16Float",
        total=128000,
        reference=[158, 90, 139, 255],
        classification="intermediateFormat-when-targetColorSpaceBlend-refused",
    )
    validate_refused_policy(
        policy_by_id(case, "targetBlend-true-rgba8unorm"),
        policy="targetBlend-true-rgba8unorm",
        intermediate_format="RGBA8Unorm",
        total=128000,
        reference=[158, 90, 139, 255],
        classification="intermediateFormat-when-targetColorSpaceBlend-refused",
    )


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-263")
    require_text(probe, "probe", "target-blend-intermediate-format-matrix-audit")
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
    require_number(probe, "matrixPolicyCount", 4)
    if probe.get("matrixPolicies") != list(POLICIES):
        fail("matrixPolicies does not match the required FOR-263 policy order")
    require_number(probe, "caseCount", 4)
    require_number(probe, "supportThreshold", 99.95)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "correctionApplied", False)
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "missingCondition", MISSING_CONDITION)

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    for field in (
        "targetColorSpaceBlendFalseObserved",
        "targetColorSpaceBlendTrueObserved",
        "rgba16FloatIntermediateObserved",
        "rgba8UnormIntermediateObserved",
        "allMatrixPoliciesObserved",
        "for261ResidualBoundaryObserved",
    ):
        require_bool(observed, field, True)

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    for field in (
        "positiveFixtureCorrectedByTargetBlend",
        "boundedStrokeImprovesWithoutExactParity",
        "exactControlNoDimensionNeeded",
        "residualImprovedByRgba8WhenTargetBlendFalse",
        "for261ResidualTargetBlendRefusedForBothFormats",
    ):
        require_bool(summary, field, True)
    require_bool(summary, "matrixRegressesAnyRenderedCase", False)
    require_bool(summary, "safeScopeProven", False)

    validate_positive_fixture(probe)
    validate_bounded_stroke(probe)
    validate_exact_control(probe)
    validate_residual(probe)


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-263 probe JSON differ")

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
        "FOR263",
        "for263",
        "kanvas.webgpu.for263",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-263 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-263 targetColorSpaceBlend intermediateFormat matrix audit stays diagnostic",
            "GPUTextureFormat.RGBA16Float",
            "GPUTextureFormat.RGBA8Unorm",
            "targetColorSpaceBlend = policy.targetColorSpaceBlend",
            "intermediateFormat = policy.intermediateFormat",
            "target-blend-intermediate-matrix-audit-for263.json",
            "KEEP_DIAGNOSTIC",
            "EXPECTED_FOR261_TARGET_BLEND_REFUSAL",
            FALLBACK_REASON,
            MISSING_CONDITION,
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-263 TargetColorSpaceBlend x IntermediateFormat Matrix Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "targetBlend-false-rgba16float",
            "targetBlend-false-rgba8unorm",
            "targetBlend-true-rgba16float",
            "targetBlend-true-rgba8unorm",
            "REFUSED_FOR_TARGET_BLEND_POLICIES",
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-263 validation passed: the targetColorSpaceBlend x intermediateFormat "
        "matrix is diagnostic-only, separates the neutral-AA target-colour fix "
        "from the FOR-261 intermediate boundary, refuses the image-filter residual "
        "for target blend, preserves Crop diagnostics, and keeps normal rendering unchanged."
    )


if __name__ == "__main__":
    main()
