#!/usr/bin/env python3
"""Validate FOR-261 whole-scene RGBA8 intermediate audit evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROBE_NAME = "whole-scene-rgba8-intermediate-audit-for261.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts/whole-scene-rgba8-intermediate-audit-for261"
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/whole-scene-rgba8-intermediate-audit-for261"
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/For261WholeSceneIntermediateAuditTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-261-whole-scene-rgba8-intermediate-audit.md"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REMAINING_BOUNDARY = "rgba16float-intermediate-store-to-present-byte-quantization-policy"
MISSING_CONDITION = (
    "missing_precision_sensitive_whole_scene_rgba8_intermediate_correction_without_targetColorSpaceBlend"
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-261 validation failed: {message}")


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


def validate_policy(
    owner: dict[str, Any],
    *,
    policy: str,
    total: int,
    matching: int,
    similarity: float,
    max_delta: int,
    intermediate_format: str,
) -> None:
    require_text(owner, "policy", policy)
    require_text(owner, "evaluationKind", "whole-scene-reference-vs-live-webgpu")
    require_number(owner, "totalPixels", total)
    require_number(owner, "matchingPixels", matching)
    require_number(owner, "exactSimilarity", similarity)
    require_number(owner, "maxDelta", max_delta)
    require_bool(owner, "intermediateObserved", True)
    require_text(owner, "intermediateFormat", intermediate_format)
    route = owner.get("routeDiagnostics")
    if not isinstance(route, str) or not route.startswith("webgpu."):
        fail(f"routeDiagnostics must name a WebGPU route, got `{route}`")
    rationale = owner.get("routeDiagnosticsRationale")
    if not isinstance(rationale, str) or f"intermediateFormat={intermediate_format}" not in rationale:
        fail(f"routeDiagnosticsRationale must name intermediateFormat={intermediate_format}")


def validate_case(
    probe: dict[str, Any],
    *,
    case_id: str,
    scene_id: str,
    kind: str,
    total: int,
    current_matching: int,
    current_similarity: float,
    current_max_delta: int,
    candidate_matching: int,
    candidate_similarity: float,
    candidate_max_delta: int,
    correction_status: str,
    regression: bool,
) -> None:
    case = case_by_id(probe, case_id)
    require_text(case, "id", case_id)
    require_text(case, "sceneId", scene_id)
    require_text(case, "kind", kind)
    require_bool(case, "regression", regression)
    require_text(case, "correctionStatus", correction_status)
    validate_policy(
        case.get("current", {}),
        policy="current-rgba16float-intermediate",
        total=total,
        matching=current_matching,
        similarity=current_similarity,
        max_delta=current_max_delta,
        intermediate_format="RGBA16Float",
    )
    validate_policy(
        case.get("candidate", {}),
        policy="diagnostic-rgba8unorm-intermediate",
        total=total,
        matching=candidate_matching,
        similarity=candidate_similarity,
        max_delta=candidate_max_delta,
        intermediate_format="RGBA8Unorm",
    )


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "linear", "FOR-261")
    require_text(probe, "probe", "whole-scene-rgba8-intermediate-candidate-audit")
    require_text(probe, "newRendererProperty", "none")
    require_bool(probe, "defaultEnabled", False)
    require_bool(probe, "runtimeSnapshotsEnabled", False)
    require_bool(probe, "normalRenderingChanged", False)
    require_bool(probe, "normalShadersChanged", False)
    require_bool(probe, "normalThresholdsChanged", False)
    require_bool(probe, "cropPolicyChanged", False)
    require_bool(probe, "fallbackPolicyChanged", False)
    require_bool(probe, "targetColorSpaceBlendGloballyEnabled", False)
    require_text(probe, "currentPolicy", "RGBA16Float intermediate store/load before present")
    require_text(
        probe,
        "boundedDiagnosticCandidate",
        "constructor-scoped RGBA8Unorm intermediate store/load before present",
    )
    require_number(probe, "caseCount", 5)
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "correctionApplied", False)
    require_text(probe, "preservedUnsupportedReason", FALLBACK_REASON)
    require_text(probe, "remainingBoundary", REMAINING_BOUNDARY)
    require_text(probe, "missingCondition", MISSING_CONDITION)

    observed = probe.get("observedBoundaries")
    if not isinstance(observed, dict):
        fail("missing observedBoundaries")
    require_bool(observed, "wholeSceneCurrentRgba16FloatObserved", True)
    require_bool(observed, "wholeSceneIntermediateCandidateObserved", True)
    require_bool(observed, "for260ExactScenesObserved", True)
    require_bool(observed, "precisionSensitiveSceneObserved", True)

    summary = probe.get("summary")
    if not isinstance(summary, dict):
        fail("missing summary")
    require_bool(summary, "candidateImprovesAnyScene", True)
    require_bool(summary, "candidateRegressesAnyScene", False)
    require_bool(summary, "exactControlsRegressed", False)
    require_bool(summary, "precisionFixtureCorrected", False)
    require_bool(summary, "safeCorrectionProven", False)

    validate_case(
        probe,
        case_id="residual-route.simple-offsetimagefilter",
        scene_id="simple-offsetimagefilter",
        kind="residual-whole-scene-equivalent",
        total=128000,
        current_matching=121378,
        current_similarity=94.8265625,
        current_max_delta=1,
        candidate_matching=123944,
        candidate_similarity=96.83125,
        candidate_max_delta=1,
        correction_status="CORRECTION_SIGNAL",
        regression=False,
    )
    validate_case(
        probe,
        case_id="residual-route.bitmap-rect-nearest",
        scene_id="bitmap-rect-nearest",
        kind="residual-whole-scene-equivalent",
        total=4096,
        current_matching=3792,
        current_similarity=92.578125,
        current_max_delta=1,
        candidate_matching=4096,
        candidate_similarity=100.0,
        candidate_max_delta=0,
        correction_status="CORRECTION_SIGNAL",
        regression=False,
    )
    validate_case(
        probe,
        case_id="exact-control.solid-rect",
        scene_id="solid-rect",
        kind="exact-or-near-exact-for260-whole-scene",
        total=64,
        current_matching=64,
        current_similarity=100.0,
        current_max_delta=0,
        candidate_matching=64,
        candidate_similarity=100.0,
        candidate_max_delta=0,
        correction_status="UNCHANGED",
        regression=False,
    )
    validate_case(
        probe,
        case_id="exact-control.linear-gradient-rect",
        scene_id="linear-gradient-rect",
        kind="exact-or-near-exact-for260-whole-scene",
        total=4096,
        current_matching=4096,
        current_similarity=100.0,
        current_max_delta=0,
        candidate_matching=4096,
        candidate_similarity=100.0,
        candidate_max_delta=0,
        correction_status="UNCHANGED",
        regression=False,
    )
    precision = case_by_id(probe, "precision-fixture.m60-target-colorspace-neutral-aa")
    validate_case(
        probe,
        case_id="precision-fixture.m60-target-colorspace-neutral-aa",
        scene_id="m60-target-colorspace-neutral-aa",
        kind="precision-intermediate-sensitive-whole-scene",
        total=4,
        current_matching=2,
        current_similarity=50.0,
        current_max_delta=13,
        candidate_matching=2,
        candidate_similarity=50.0,
        candidate_max_delta=13,
        correction_status="UNCHANGED",
        regression=False,
    )
    require_array(precision["candidate"], "referenceRgba", [128, 128, 128, 255])
    require_array(precision["candidate"], "observedRgba", [115, 115, 115, 255])
    verdict = precision.get("verdict")
    if not isinstance(verdict, str) or "targetColorSpaceBlend remains disabled" not in verdict:
        fail("precision verdict must keep targetColorSpaceBlend disabled")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-261 probe JSON differ")

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
        "FOR261",
        "for261",
        "kanvas.webgpu.for261",
        "targetColorSpaceBlend: Boolean = true",
        "correctionApplied = true",
    ):
        if forbidden in device_text:
            fail(f"FOR-261 must not add production switches or corrections: `{forbidden}`")

    require_file_text(
        TEST,
        [
            "FOR-261 whole scene RGBA8 intermediate candidate audit stays diagnostic",
            "GPUTextureFormat.RGBA8Unorm",
            "whole-scene-rgba8-intermediate-audit-for261.json",
            "KEEP_DIAGNOSTIC",
            FALLBACK_REASON,
            MISSING_CONDITION,
        ],
    )
    require_file_text(
        REPORT,
        [
            "FOR-261 Whole-Scene RGBA8 Intermediate Audit",
            "Decision: `KEEP_DIAGNOSTIC`",
            "RGBA16Float",
            "RGBA8Unorm",
            MISSING_CONDITION,
            REMAINING_BOUNDARY,
            FALLBACK_REASON,
        ],
    )

    print(
        "FOR-261 validation passed: whole-scene RGBA8Unorm candidate renders are "
        "observed, residual scenes improve, FOR-260 exact controls do not regress, "
        "the precision fixture remains uncorrected, no normal policy changed, and "
        "Crop diagnostics are preserved."
    )


if __name__ == "__main__":
    main()
