#!/usr/bin/env python3
"""Validate FOR-260 intermediate quantization candidate audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "intermediate-quantization-candidate-audit-for260.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/intermediate-quantization-candidate-audit-for260"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/intermediate-quantization-candidate-audit-for260"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-260-intermediate-quantization-candidate-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
MISSING_CONDITION = (
    "missing_whole_scene_intermediate_rgba8_candidate_evidence_for_exact_and_precision_sensitive_routes"
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-260 validation failed: {message}")


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


def case_by_id(probe: dict[str, Any], case_id: str) -> dict[str, Any]:
    cases = probe.get("cases")
    if not isinstance(cases, list):
        fail("missing cases array")
    for case in cases:
        if isinstance(case, dict) and case.get("id") == case_id:
            return case
    fail(f"missing case `{case_id}`")


def validate_policy_stats(
    owner: dict[str, Any],
    *,
    policy: str,
    matching: int,
    similarity: float,
    max_delta: int,
    reference: list[int],
    observed: list[int],
    signed_delta: list[int],
    intermediate_observed: bool,
    intermediate_format: str,
) -> None:
    require_text(owner, "policy", policy)
    require_number(owner, "matchingPixels", matching)
    require_number(owner, "exactSimilarity", similarity)
    require_number(owner, "maxDelta", max_delta)
    require_array(owner, "referenceRgba", reference)
    require_array(owner, "observedRgba", observed)
    require_array(owner, "signedDeltaRgba", signed_delta)
    require_bool(owner, "intermediateObserved", intermediate_observed)
    require_text(owner, "intermediateFormat", intermediate_format)


def validate_residual_case(
    case: dict[str, Any],
    *,
    expected_id: str,
    scene_id: str,
    route: str,
    current_reference: list[int],
    current_observed: list[int],
    current_delta: list[int],
    candidate_observed: list[int],
) -> None:
    require_text(case, "id", expected_id)
    require_text(case, "sceneId", scene_id)
    require_text(case, "kind", "for259-residual-representative-sample")
    require_text(case, "route", route)
    require_bool(case, "regression", False)
    validate_policy_stats(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        matching=0,
        similarity=0.0,
        max_delta=1,
        reference=current_reference,
        observed=current_observed,
        signed_delta=current_delta,
        intermediate_observed=True,
        intermediate_format="RGBA16Float",
    )
    validate_policy_stats(
        case.get("candidate", {}),
        policy="diagnostic-rgba8unorm-store-load-before-present",
        matching=1,
        similarity=100.0,
        max_delta=0,
        reference=current_reference,
        observed=candidate_observed,
        signed_delta=[0, 0, 0, 0],
        intermediate_observed=True,
        intermediate_format="RGBA8Unorm diagnostic simulation",
    )


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-260")
    require_text(probe, "probe", "intermediate-quantization-candidate-before-after-audit")
    require_text(probe, "newRendererProperty", "none")
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", True)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "normalShadersChanged", False)
    require_bool(probe, "normalThresholdsChanged", False)
    require_bool(probe, "cropPolicyChanged", False)
    require_bool(probe, "fallbackPolicyChanged", False)
    require_bool(probe, "targetColorSpaceBlendGloballyEnabled", False)
    require_text(probe, "currentPolicy", "RGBA16Float intermediate store/load before present")
    candidate = probe.get("boundedDiagnosticCandidate")
    if not isinstance(candidate, str) or "RGBA8Unorm store/load" not in candidate:
        fail("boundedDiagnosticCandidate must name RGBA8Unorm store/load")
    require_number(probe, "caseCount", 5)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "correctionApplied", False)
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "missingCondition", MISSING_CONDITION)

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    require_bool(observed, "for259ResidualRepresentativeSamples", True)
    require_bool(observed, "exactControlWholeSceneArtifacts", True)
    require_bool(observed, "precisionFixtureArtifact", True)
    require_bool(observed, "wholeSceneIntermediateCandidateObserved", False)

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    require_bool(summary, "candidateImprovesResidualRepresentatives", True)
    require_bool(summary, "exactControlsRegressed", False)
    require_bool(summary, "precisionFixtureCorrected", False)
    require_bool(summary, "safeCorrectionProven", False)

    validate_residual_case(
        case_by_id(probe, "legacy-source-color-uniform.simple-offset-row1-col0"),
        expected_id="legacy-source-color-uniform.simple-offset-row1-col0",
        scene_id="simple-offsetimagefilter",
        route="webgpu.canvas.draw-rect.src-over",
        current_reference=[158, 90, 139, 255],
        current_observed=[157, 90, 138, 255],
        current_delta=[-1, 0, -1, 0],
        candidate_observed=[158, 90, 139, 255],
    )
    validate_residual_case(
        case_by_id(probe, "bitmap-texel-upload-sample.bitmap-rect-nearest"),
        expected_id="bitmap-texel-upload-sample.bitmap-rect-nearest",
        scene_id="bitmap-rect-nearest",
        route="webgpu.image-rect.strict-nearest",
        current_reference=[149, 193, 207, 255],
        current_observed=[148, 193, 207, 255],
        current_delta=[-1, 0, 0, 0],
        candidate_observed=[149, 193, 207, 255],
    )

    for case_id, pixels in (
        ("generated-solid-control.solid-rect", 64),
        ("generated-gradient-control.linear-gradient-rect", 4096),
    ):
        case = case_by_id(probe, case_id)
        require_text(case, "kind", "exact-or-near-exact-whole-scene-control")
        require_bool(case, "regression", False)
        current = case.get("current", {})
        candidate_stats = case.get("candidate", {})
        require_number(current, "totalPixels", pixels)
        require_number(current, "matchingPixels", pixels)
        require_number(current, "exactSimilarity", 100.0)
        require_number(current, "maxDelta", 0)
        require_bool(current, "intermediateObserved", False)
        require_number(candidate_stats, "matchingPixels", pixels)
        require_number(candidate_stats, "exactSimilarity", 100.0)
        require_number(candidate_stats, "maxDelta", 0)
        require_bool(candidate_stats, "intermediateObserved", False)

    precision = case_by_id(probe, "precision-fixture.m60-target-colorspace-neutral-aa")
    require_text(precision, "kind", "precision-intermediate-sensitive-fixture")
    require_bool(precision, "regression", False)
    validate_policy_stats(
        precision.get("current", {}),
        policy="current-rgba16float-intermediate",
        matching=0,
        similarity=0.0,
        max_delta=13,
        reference=[128, 128, 128, 255],
        observed=[115, 115, 115, 255],
        signed_delta=[-13, -13, -13, 0],
        intermediate_observed=False,
        intermediate_format="RGBA16Float",
    )
    validate_policy_stats(
        precision.get("candidate", {}),
        policy="diagnostic-rgba8unorm-store-load-before-present",
        matching=0,
        similarity=0.0,
        max_delta=13,
        reference=[128, 128, 128, 255],
        observed=[115, 115, 115, 255],
        signed_delta=[-13, -13, -13, 0],
        intermediate_observed=False,
        intermediate_format="RGBA8Unorm diagnostic proxy",
    )
    verdict = precision.get("verdict")
    if not isinstance(verdict, str) or "does not enable targetColorSpaceBlend" not in verdict:
        fail("precision fixture verdict must keep targetColorSpaceBlend disabled")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-260 probe JSON differ")

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
        "FOR260",
        "for260",
        "kanvas.webgpu.for260",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-260 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-260 intermediate quantization candidate before after audit stays diagnostic",
            "intermediate-quantization-candidate-audit-for260.json",
            "RGBA16Float",
            "RGBA8Unorm",
            MISSING_CONDITION,
            "KEEP_DIAGNOSTIC",
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-260 Intermediate Quantization Candidate Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "RGBA16Float",
            "RGBA8Unorm",
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-260 validation passed: candidate improves FOR-259 representative "
        "samples, exact controls do not regress under the diagnostic proxy, the "
        "precision fixture remains uncorrected without targetColorSpaceBlend, no "
        "normal policy changed, and Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
