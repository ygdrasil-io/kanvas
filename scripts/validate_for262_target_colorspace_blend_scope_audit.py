#!/usr/bin/env python3
"""Validate FOR-262 targetColorSpaceBlend scope audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "target-colorspace-blend-scope-audit-for262.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/target-colorspace-blend-scope-audit-for262"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/target-colorspace-blend-scope-audit-for262"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For262TargetColorSpaceBlendScopeAuditTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-262-target-colorspace-blend-scope-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
MISSING_CONDITION = (
    "missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation"
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-262 validation failed: {message}")


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


def validate_rendered_policy(
    owner: dict[str, Any],
    *,
    policy: str,
    target_blend: bool,
    total: int,
    matching: int,
    similarity: float,
    max_delta: int,
    reference: list[int],
    observed: list[int],
    signed_delta: list[int],
    route_prefix: str,
) -> None:
    require_text(owner, "policy", policy)
    require_text(owner, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_text(owner, "evaluationStatus", "rendered")
    require_bool(owner, "targetColorSpaceBlend", target_blend)
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
    expected_toggle = f"targetColorSpaceBlend={str(target_blend).lower()}"
    if not isinstance(rationale, str) or expected_toggle not in rationale:
        fail(f"routeDiagnosticsRationale must name {expected_toggle}")
    require_null(owner, "refusalReason")


def validate_rendered_case(
    probe: dict[str, Any],
    *,
    case_id: str,
    scene_id: str,
    kind: str,
    route_prefix: str,
    current_total: int,
    current_matching: int,
    current_similarity: float,
    current_max_delta: int,
    current_reference: list[int],
    current_observed: list[int],
    current_delta: list[int],
    target_matching: int,
    target_similarity: float,
    target_max_delta: int,
    target_reference: list[int],
    target_observed: list[int],
    target_delta: list[int],
    correction_status: str,
    admissibility: str,
    regression: bool,
    route_diagnostics_path: str | None = None,
) -> None:
    case = case_by_id(probe, case_id)
    require_text(case, "id", case_id)
    require_text(case, "sceneId", scene_id)
    require_text(case, "kind", kind)
    require_bool(case, "regression", regression)
    require_text(case, "correctionStatus", correction_status)
    require_text(case, "admissibility", admissibility)
    validate_rendered_policy(
        case.get("current", {}),
        policy="current-targetColorSpaceBlend-false",
        target_blend=False,
        total=current_total,
        matching=current_matching,
        similarity=current_similarity,
        max_delta=current_max_delta,
        reference=current_reference,
        observed=current_observed,
        signed_delta=current_delta,
        route_prefix=route_prefix,
    )
    validate_rendered_policy(
        case.get("target", {}),
        policy="diagnostic-targetColorSpaceBlend-true",
        target_blend=True,
        total=current_total,
        matching=target_matching,
        similarity=target_similarity,
        max_delta=target_max_delta,
        reference=target_reference,
        observed=target_observed,
        signed_delta=target_delta,
        route_prefix=route_prefix,
    )
    if route_diagnostics_path is not None:
        require_text(case["target"], "routeDiagnosticsPath", route_diagnostics_path)


def validate_refusal_case(probe: dict[str, Any]) -> None:
    case = case_by_id(probe, "for261-residual.simple-offsetimagefilter")
    require_text(case, "sceneId", "simple-offsetimagefilter")
    require_text(case, "kind", "for261-residual-intermediate-boundary-control")
    require_bool(case, "regression", False)
    require_text(case, "correctionStatus", "REFUSED")
    require_text(case, "admissibility", "REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND")
    validate_rendered_policy(
        case.get("current", {}),
        policy="current-targetColorSpaceBlend-false",
        target_blend=False,
        total=128000,
        matching=121378,
        similarity=94.8265625,
        max_delta=1,
        reference=[158, 90, 139, 255],
        observed=[157, 90, 138, 255],
        signed_delta=[-1, 0, -1, 0],
        route_prefix="webgpu.image-filter.offset-crop-prepass-and-src-over",
    )
    target = case.get("target", {})
    require_text(target, "policy", "diagnostic-targetColorSpaceBlend-true")
    require_text(target, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_text(target, "evaluationStatus", "refused-before-render")
    require_bool(target, "targetColorSpaceBlend", True)
    require_number(target, "totalPixels", 128000)
    for field in ("matchingPixels", "exactSimilarity", "maxDelta", "observedRgba", "signedDeltaRgba"):
        require_null(target, field)
    require_array(target, "referenceRgba", [158, 90, 139, 255])
    require_text(target, "routeDiagnostics", "color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw")
    require_text(target, "refusalReason", "color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-262")
    require_text(probe, "probe", "target-colorspace-blend-whole-scene-scope-audit")
    require_text(probe, "newRendererProperty", "none")
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", False)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "normalShadersChanged", False)
    require_bool(probe, "normalThresholdsChanged", False)
    require_bool(probe, "cropPolicyChanged", False)
    require_bool(probe, "fallbackPolicyChanged", False)
    require_bool(probe, "intermediateFormatPolicyChanged", False)
    require_bool(probe, "targetColorSpaceBlendGloballyEnabled", False)
    require_text(probe, "currentPolicy", "targetColorSpaceBlend=false with normal RGBA16Float intermediate")
    require_text(
        probe,
        "boundedDiagnosticCandidate",
        "test-only targetColorSpaceBlend=true with normal RGBA16Float intermediate",
    )
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
        "positiveFixtureObserved",
        "improvesWithoutExactParityObserved",
        "alreadyExactControlObserved",
        "for261ResidualBoundaryObserved",
    ):
        require_bool(observed, field, True)

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    for field in (
        "positiveFixtureCorrected",
        "targetBlendImprovesAnyCase",
        "improvesWithoutExactParity",
        "exactControlDoesNotNeedTargetBlend",
        "for261ResidualTargetBlendRefused",
    ):
        require_bool(summary, field, True)
    require_bool(summary, "targetBlendRegressesAnyRenderedCase", False)
    require_bool(summary, "safeScopeProven", False)

    validate_rendered_case(
        probe,
        case_id="positive-fixture.m60-target-colorspace-neutral-aa",
        scene_id="m60-target-colorspace-neutral-aa",
        kind="isolated-positive-target-blend-fixture",
        route_prefix="webgpu.target-colorspace-blend.solid-coverage",
        current_total=4,
        current_matching=2,
        current_similarity=50.0,
        current_max_delta=13,
        current_reference=[128, 128, 128, 255],
        current_observed=[115, 115, 115, 255],
        current_delta=[-13, -13, -13, 0],
        target_matching=4,
        target_similarity=100.0,
        target_max_delta=0,
        target_reference=[128, 128, 128, 255],
        target_observed=[128, 128, 128, 255],
        target_delta=[0, 0, 0, 0],
        correction_status="CORRECTION_SIGNAL",
        admissibility="ADMISSIBLE_DIAGNOSTIC_ONLY",
        regression=False,
    )
    validate_rendered_case(
        probe,
        case_id="insufficient-scope.m60-bounded-stroke-cap-join",
        scene_id="m60-bounded-stroke-cap-join",
        kind="improves-without-exact-parity",
        route_prefix="webgpu.coverage.stroke-cap-join.experimental-render",
        current_total=24576,
        current_matching=22019,
        current_similarity=89.59554036458333,
        current_max_delta=39,
        current_reference=[192, 192, 192, 255],
        current_observed=[184, 184, 184, 255],
        current_delta=[-8, -8, -8, 0],
        target_matching=23572,
        target_similarity=95.91471354166667,
        target_max_delta=48,
        target_reference=[192, 192, 192, 255],
        target_observed=[191, 191, 191, 255],
        target_delta=[-1, -1, -1, 0],
        correction_status="CORRECTION_SIGNAL_INSUFFICIENT",
        admissibility="REFUSED_INSUFFICIENT_PARITY",
        regression=False,
        route_diagnostics_path=(
            "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/"
            "experimental-gpu-diagnostic.json"
        ),
    )
    validate_rendered_case(
        probe,
        case_id="exact-control.black-white-rect",
        scene_id="black-white-rect",
        kind="already-exact-target-blend-not-needed",
        route_prefix="webgpu.coverage.analytic-rect",
        current_total=64,
        current_matching=64,
        current_similarity=100.0,
        current_max_delta=0,
        current_reference=[255, 255, 255, 255],
        current_observed=[255, 255, 255, 255],
        current_delta=[0, 0, 0, 0],
        target_matching=64,
        target_similarity=100.0,
        target_max_delta=0,
        target_reference=[255, 255, 255, 255],
        target_observed=[255, 255, 255, 255],
        target_delta=[0, 0, 0, 0],
        correction_status="UNCHANGED",
        admissibility="REFUSED_NOT_NEEDED",
        regression=False,
    )
    validate_refusal_case(probe)


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-262 probe JSON differ")

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
        "FOR262",
        "for262",
        "kanvas.webgpu.for262",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-262 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-262 targetColorSpaceBlend whole scene scope audit stays diagnostic",
            "target-colorspace-blend-scope-audit-for262.json",
            "KEEP_DIAGNOSTIC",
            "targetColorSpaceBlend=false",
            "targetColorSpaceBlend=true",
            "REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND",
            "EXPECTED_FOR261_TARGET_BLEND_REFUSAL",
            FALLBACK_REASON,
            MISSING_CONDITION,
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-262 TargetColorSpaceBlend Scope Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "targetColorSpaceBlend=false",
            "targetColorSpaceBlend=true",
            "CORRECTION_SIGNAL_INSUFFICIENT",
            "REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND",
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-262 validation passed: targetColorSpaceBlend corrects the isolated "
        "neutral AA fixture, improves but does not promote bounded stroke cap/join, "
        "is unnecessary for an exact control, refuses the FOR-261 residual image-filter "
        "route, stays diagnostic, and preserves Crop diagnostics."
    )


if __name__ == "__main__":
    main()
