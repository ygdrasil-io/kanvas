#!/usr/bin/env python3
"""Validate the FOR-367 M60 bounded stroke cap/join comparable F16 evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-367"
SCENE_ID = "m60-bounded-stroke-cap-join-comparable-f16-evidence-for367"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
FAMILY_ID = "m60-bounded-stroke-cap-join-positive-residual-evidence-target"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-05-for-367-m60-bounded-stroke-cap-join-comparable-f16-evidence.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-m60-bounded-stroke-cap-join-comparable-f16-evidence-after-for-366"
)

FOR366_SCENE_ID = "f16-positive-residual-target-inventory-for366"
FOR366_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR366_SCENE_ID / f"{FOR366_SCENE_ID}.json"
)
FOR344_SCENE_ID = "f16-broader-non-arc-color-policy-for344"
FOR344_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR344_SCENE_ID / f"{FOR344_SCENE_ID}.json"
)

SOURCE_ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SOURCE_SCENE_ID
AA_RESIDUAL_ARTIFACT = SOURCE_ARTIFACT_DIR / "aa-residual-diagnostic.json"
STATS_ARTIFACT = SOURCE_ARTIFACT_DIR / "stats.json"
ROUTE_CPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "route-cpu.json"
ROUTE_GPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "route-gpu.json"
EXPERIMENTAL_GPU_ARTIFACT = SOURCE_ARTIFACT_DIR / "experimental-gpu-diagnostic.json"

FOR366_REQUIRED_DECISION = "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY"
FOR344_REQUIRED_DECISION = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"
DECISION = "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED"
CLASSIFICATION = "still-missing-comparable-metadata"
ALLOWED_CLASSIFICATIONS = ["ready-for-candidate-evaluation", CLASSIFICATION]
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10

CLOSED_FOR355_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
REJECTED_FOR365_POLICY_ID = "covered_source_alpha_src_over_white_without_non_arc_guard_probe"
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for367-pycache python3 -m py_compile "
        "scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

IMPLEMENTATION_FALSE_KEYS = (
    "rendererBehaviorChanged",
    "newColorPolicyImplemented",
    "candidateSelectedForImplementation",
    "selectableCandidateDefined",
    "implementationPlanAuthorized",
    "colorToF16PremulChanged",
    "blendF16PremulModeChanged",
    "skBitmapGetPixelChanged",
    "skBitmapGetPixelAsSrgbChanged",
    "gpuOrWgslChanged",
    "geometryChanged",
    "coverageChanged",
    "fallbackChanged",
    "thresholdsChanged",
    "promotionChanged",
    "scoreChanged",
    "kadreChanged",
    "rendererFixtureOrCoordinateBranchAdded",
    "rendererSceneBranchAdded",
    "rendererSelectedCellOrFullGmCropBranchAdded",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-367 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    result: list[int] = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def signed_delta(left: list[int], right: list[int]) -> list[int]:
    return [left[index] - right[index] for index in range(4)]


def abs_delta(left: list[int], right: list[int]) -> list[int]:
    return [abs(left[index] - right[index]) for index in range(4)]


def sample_residual(left: list[int], right: list[int]) -> int:
    return sum(abs(left[index] - right[index]) for index in range(4))


def validate_implementation_false(data: dict[str, Any], linear: str) -> None:
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), f"{linear} implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "gpuOrWgslChanged",
        "geometryChanged",
        "coverageChanged",
        "fallbackChanged",
        "thresholdsChanged",
        "promotionChanged",
        "scoreChanged",
        "kadreChanged",
    ):
        require(implementation.get(key) is False, f"{linear} implementation guard changed: {key}")


def row_from_for344(for344: dict[str, Any]) -> dict[str, Any]:
    require(for344.get("linear") == "FOR-344", "FOR-344 identity changed")
    require(for344.get("decision") == FOR344_REQUIRED_DECISION, "FOR-344 decision changed")
    validate_implementation_false(for344, "FOR-344")
    matrix = for344.get("matrix")
    require(isinstance(matrix, dict), "FOR-344 matrix missing")
    rows = matrix.get("rows")
    require(isinstance(rows, list), "FOR-344 rows missing")
    for row in rows:
        require(isinstance(row, dict), "FOR-344 row must be an object")
        if row.get("rowId") == ROW_ID:
            return row
    fail(f"FOR-344 row missing: {ROW_ID}")


def validate_for366(for366: dict[str, Any]) -> dict[str, Any]:
    require(for366.get("linear") == "FOR-366", "FOR-366 identity changed")
    require(for366.get("decision") == FOR366_REQUIRED_DECISION, "FOR-366 decision changed")
    validate_implementation_false(for366, "FOR-366")
    target = for366.get("proposedNextEvidenceTarget")
    require(isinstance(target, dict), "FOR-366 proposed next target missing")
    require(target.get("familyId") == FAMILY_ID, "FOR-366 selected family changed")
    require(target.get("selectedRowId") == ROW_ID, "FOR-366 selected row changed")
    require(target.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-366 selected residual changed")
    require(target.get("candidateImplementationAuthorized") is False, "FOR-366 authorized a candidate")
    require(target.get("scoreIncreaseAuthorized") is False, "FOR-366 authorized a score increase")
    return target


def region_for_sample(sample: dict[str, Any], regions: list[dict[str, Any]]) -> dict[str, Any]:
    x = sample["x"]
    y = sample["y"]
    for region in regions:
        bounds = region.get("bounds")
        if not isinstance(bounds, dict):
            continue
        left = bounds.get("left")
        top = bounds.get("top")
        right = bounds.get("right")
        bottom = bounds.get("bottom")
        if all(isinstance(value, int) for value in (left, top, right, bottom)):
            if left <= x <= right and top <= y <= bottom:
                return {
                    "id": region.get("id"),
                    "description": region.get("description"),
                    "edgeZone": "cap-join-aa-boundary",
                }
    return {"id": "unclassified", "description": "outside committed region bounds", "edgeZone": "unknown"}


def build_samples(aa: dict[str, Any]) -> tuple[list[dict[str, Any]], int]:
    raw_samples = aa.get("highDeltaSamples")
    require(isinstance(raw_samples, list), "M60 high delta samples missing")
    require(len(raw_samples) == REQUIRED_SAMPLE_COUNT, "M60 high delta sample count changed")
    regions = aa.get("regions")
    require(isinstance(regions, list), "M60 region diagnostics missing")

    samples: list[dict[str, Any]] = []
    residual = 0
    for index, raw in enumerate(raw_samples, start=1):
        require(isinstance(raw, dict), "M60 sample must be an object")
        x = raw.get("x")
        y = raw.get("y")
        require(isinstance(x, int) and isinstance(y, int), "M60 sample coordinates missing")
        reference = rgba(raw.get("referenceRgba"), "referenceRgba")
        current = rgba(raw.get("gpuRgba"), "gpuRgba")
        delta = abs_delta(reference, current)
        signed = signed_delta(current, reference)
        item_residual = sample_residual(reference, current)
        residual += item_residual
        require(raw.get("maxChannelDelta") == max(delta), "M60 max channel delta changed")
        sample = {
            "index": index,
            "x": x,
            "y": y,
            "region": region_for_sample({"x": x, "y": y}, regions),
            "referenceRgba": reference,
            "currentRgba": current,
            "currentSourceKey": "gpuRgba",
            "candidatePolicyRgba": None,
            "sourceInputRgba": None,
            "sourceInputRgbaStatus": "missing-from-committed-m60-artifacts",
            "signedCurrentMinusReferenceDelta": signed,
            "absoluteChannelDelta": delta,
            "sampleResidual": item_residual,
            "maxChannelDelta": max(delta),
        }
        samples.append(sample)
    return samples, residual


def build_artifact() -> dict[str, Any]:
    for366 = load_json(FOR366_ARTIFACT)
    for344 = load_json(FOR344_ARTIFACT)
    aa = load_json(AA_RESIDUAL_ARTIFACT)
    stats = load_json(STATS_ARTIFACT)
    route_cpu = load_json(ROUTE_CPU_ARTIFACT)
    route_gpu = load_json(ROUTE_GPU_ARTIFACT)
    experimental = load_json(EXPERIMENTAL_GPU_ARTIFACT)

    for366_target = validate_for366(for366)
    row = row_from_for344(for344)
    require(row.get("sceneId") == SOURCE_SCENE_ID, "FOR-344 source scene changed")
    require(row.get("family") == "bounded-stroke-cap-join", "FOR-344 M60 family changed")
    require(row.get("nonArc") is True, "FOR-344 M60 row is no longer non-arc")
    require(row.get("referenceCurrentCandidateComparable") is False, "FOR-344 M60 comparability changed")
    require(row.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-344 M60 residual changed")
    require(row.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-344 M60 sample count changed")

    for source, artifact in (
        ("aa-residual", aa),
        ("stats", stats),
        ("route-cpu", route_cpu),
        ("route-gpu", route_gpu),
        ("experimental-gpu", experimental),
    ):
        require(artifact.get("sceneId") == SOURCE_SCENE_ID, f"{source} scene id changed")

    require(aa.get("targetColorSpaceBlend") is True, "M60 targetColorSpaceBlend changed")
    require(aa.get("status") == "diagnostic-only", "M60 AA diagnostic status changed")
    require(stats.get("gpuStatus") == "expected-unsupported", "M60 GPU status changed")
    require(route_gpu.get("status") == "expected-unsupported", "M60 route status changed")
    require(route_cpu.get("status") == "pass", "M60 CPU route status changed")

    samples, computed_residual = build_samples(aa)
    require(computed_residual == REQUIRED_RESIDUAL, "computed M60 residual changed")

    coverage_metadata = {
        "coveragePlan": route_cpu.get("coveragePlan"),
        "normalGpuRoute": route_gpu.get("selectedRoute"),
        "experimentalGpuRoute": experimental.get("selectedRoute"),
        "fallbackReason": route_gpu.get("fallbackReason"),
        "rootCause": route_gpu.get("rootCause"),
        "resolvedRootCause": route_gpu.get("resolvedRootCause"),
        "remainingRootCause": route_gpu.get("remainingRootCause"),
        "pathVerbCount": stats.get("pathVerbCount"),
        "pathVerbBudget": stats.get("pathVerbBudget"),
        "edgeCount": stats.get("edgeCount"),
        "edgeBudget": stats.get("edgeBudget"),
        "strokeWidth": stats.get("strokeWidth"),
        "strokeWidthBudget": stats.get("strokeWidthBudget"),
        "strokeCaps": stats.get("strokeCaps"),
        "strokeJoins": stats.get("strokeJoins"),
        "dashIntervalCount": stats.get("dashIntervalCount"),
        "dashIntervalBudget": stats.get("dashIntervalBudget"),
        "deviceBounds": stats.get("deviceBounds"),
        "deviceBoundsBudget": stats.get("deviceBoundsBudget"),
        "dominantMismatchRegion": stats.get("dominantMismatchRegion"),
        "dominantMismatchDescription": stats.get("dominantMismatchDescription"),
        "regions": aa.get("regions"),
        "residualSummary": stats.get("residualSummary"),
    }

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": SOURCE_SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "decisionReason": (
            "FOR-367 records the FOR-366 selected M60 bounded stroke cap/join positive "
            "residual as comparable reference/current evidence, while refusing candidate "
            "evaluation until candidate samples and missing source metadata exist."
        ),
        "inputValidation": {
            "for366Artifact": rel(FOR366_ARTIFACT),
            "for366Decision": for366.get("decision"),
            "for366RequiredDecision": FOR366_REQUIRED_DECISION,
            "for366SelectedFamily": for366_target.get("familyId"),
            "for366SelectedRowId": for366_target.get("selectedRowId"),
            "for366CurrentResidual": for366_target.get("currentResidual"),
            "for344Artifact": rel(FOR344_ARTIFACT),
            "for344Decision": for344.get("decision"),
            "for344RequiredDecision": FOR344_REQUIRED_DECISION,
            "for344RowId": row.get("rowId"),
            "for344CurrentResidual": row.get("currentResidual"),
        },
        "sourceArtifacts": {
            "for366": rel(FOR366_ARTIFACT),
            "for344": rel(FOR344_ARTIFACT),
            "aaResidualDiagnostic": rel(AA_RESIDUAL_ARTIFACT),
            "stats": rel(STATS_ARTIFACT),
            "routeCpu": rel(ROUTE_CPU_ARTIFACT),
            "routeGpu": rel(ROUTE_GPU_ARTIFACT),
            "experimentalGpuDiagnostic": rel(EXPERIMENTAL_GPU_ARTIFACT),
        },
        "rejectedCandidateBoundaries": [
            {
                "source": "FOR-355",
                "policyId": CLOSED_FOR355_POLICY_ID,
                "selectableForFutureWork": False,
                "reason": "closed after independent FOR-361 evidence",
            },
            {
                "source": "FOR-365",
                "policyId": REJECTED_FOR365_POLICY_ID,
                "selectableForFutureWork": False,
                "reason": "worsened all mandatory FOR-365 guards",
            },
        ],
        "evidenceLine": {
            "rowId": ROW_ID,
            "familyId": FAMILY_ID,
            "family": row.get("family"),
            "sourceKind": row.get("sourceKind"),
            "rowKind": "non-arc",
            "rec2020F16SrcOverOrBlendSignal": row.get("rec2020F16SrcOverOrBlendSignal"),
            "referenceCurrentComparable": True,
            "referenceCurrentCandidateComparable": False,
            "currentResidual": REQUIRED_RESIDUAL,
            "candidateResidual": None,
            "sampleCount": REQUIRED_SAMPLE_COUNT,
            "residualComputation": {
                "method": "sum absolute RGBA channel deltas across committed highDeltaSamples",
                "computedResidual": computed_residual,
                "matchesFor344CurrentResidual": computed_residual == row.get("currentResidual"),
                "matchesFor366CurrentResidual": computed_residual == for366_target.get("currentResidual"),
            },
            "reference": {
                "status": "reference-rgba-high-delta-samples-available",
                "sampleKey": "referenceRgba",
                "colorType": "RGBA8 diagnostic sample",
                "colorSpace": "committed M60 diagnostic reference output",
                "samplesAvailable": True,
            },
            "current": {
                "status": "current-gpu-rgba-high-delta-samples-available",
                "sampleKey": "gpuRgba",
                "normalizedSampleKey": "currentRgba",
                "backend": aa.get("backend"),
                "adapter": aa.get("adapter"),
                "colorType": "RGBA8 diagnostic sample",
                "colorSpace": "committed M60 WebGPU diagnostic output",
                "samplesAvailable": True,
            },
            "candidate": {
                "policyId": F16_POLICY_ID,
                "status": "missing-for-f16-policy-candidate",
                "samplesAvailable": False,
                "candidateImplementationAuthorized": False,
                "scoreIncreaseAuthorized": False,
            },
            "sourceInputRgba": {
                "available": False,
                "status": "missing-from-committed-m60-artifacts",
                "reason": (
                    "The committed M60 artifacts expose reference/current RGBA samples but "
                    "do not expose source input RGBA or raw premul/unpremul F16 components."
                ),
            },
            "colorAndBlendMetadata": {
                "targetColorSpaceBlend": aa.get("targetColorSpaceBlend"),
                "resolvedRootCause": aa.get("resolvedRootCause"),
                "remainingRootCause": aa.get("remainingRootCause"),
                "premulAssumption": "not-explicitly-recorded-for-source-f16-components",
                "blendAssumption": "targetColorSpaceBlend diagnostic, not a selected F16 policy candidate",
                "candidatePolicyId": F16_POLICY_ID,
            },
            "coverageAndEdgeMetadata": coverage_metadata,
            "samples": samples,
        },
        "candidateReadiness": {
            "classification": CLASSIFICATION,
            "readyForCandidateEvaluation": False,
            "reasons": [
                "candidatePolicyRgba samples are missing for the FOR-341/FOR-342 F16 policy",
                "source input RGBA and raw F16 premul/unpremul metadata are not present in committed M60 artifacts",
                "the current line is a targetColorSpaceBlend diagnostic, not an applied candidate policy row",
            ],
            "metadataAvailable": [
                "reference/current RGBA samples",
                "sample coordinates",
                "per-sample region and cap/join AA edge-zone classification",
                "coverage route, edge budget, stroke cap/join, device bounds, and residual summaries",
                "targetColorSpaceBlend/root-cause diagnostics",
            ],
            "nextEvidenceNeeded": [
                "candidatePolicyRgba samples for the same coordinates",
                "source input/raw RGBA or explicit note from a capture that it cannot be exported",
                "explicit premul/blend metadata for the candidate evaluation row",
            ],
        },
        "dangerousRoutesRejected": [
            "renderer scene-id branch",
            "renderer coordinate branch",
            "selected-cell substitution",
            "fixture-only proof",
            "full-GM crop proof",
            "threshold relaxation",
            "score increase",
            "GPU/WGSL, geometry, coverage, fallback, Kadre, F16 premul, or blend mutation",
        ],
        "criteriaEvaluation": {
            "sourceMemoryRecorded": True,
            "for366DecisionCaptured": True,
            "for366DecisionRequired": True,
            "m60RowConsolidated": True,
            "currentResidualKeptAt856": True,
            "referenceSamplesRecorded": True,
            "currentSamplesRecorded": True,
            "availableMetadataRecorded": True,
            "classificationRecorded": True,
            "for355CandidateExcluded": True,
            "for365CandidateExcluded": True,
            "candidateImplementationAuthorized": False,
            "scoreIncreaseAuthorized": False,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "selectableCandidateDefined": False,
            "implementationPlanAuthorized": False,
            "colorToF16PremulChanged": False,
            "blendF16PremulModeChanged": False,
            "skBitmapGetPixelChanged": False,
            "skBitmapGetPixelAsSrgbChanged": False,
            "gpuOrWgslChanged": False,
            "geometryChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "thresholdsChanged": False,
            "promotionChanged": False,
            "scoreChanged": False,
            "kadreChanged": False,
            "rendererFixtureOrCoordinateBranchAdded": False,
            "rendererSceneBranchAdded": False,
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def render_report(data: dict[str, Any]) -> str:
    line = data["evidenceLine"]
    readiness = data["candidateReadiness"]
    samples = line["samples"]

    lines = [
        "# FOR-367 M60 Bounded Stroke Cap/Join Comparable F16 Evidence",
        "",
        f"Linear: `{LINEAR_ID}`",
        "",
        f"Decision: `{data['decision']}`",
        "",
        f"Classification: `{data['classification']}`",
        "",
        "FOR-367 is evidence-only. It consolidates the FOR-366 selected M60 positive",
        "residual row without changing renderer behavior, scores, thresholds, GPU/WGSL,",
        "geometry, coverage, fallback, Kadre, F16 premul, or blend code.",
        "",
        "## Source Memory",
        "",
        f"- `{SOURCE_MEMORY}`",
        "",
        "## Input Gate",
        "",
        f"- FOR-366 decision required: `{FOR366_REQUIRED_DECISION}`",
        f"- FOR-366 selected row: `{ROW_ID}`",
        f"- FOR-366 selected residual: `{REQUIRED_RESIDUAL}`",
        "",
        "## Consolidated Line",
        "",
        f"- Family: `{line['familyId']}`",
        f"- Row: `{line['rowId']}`",
        f"- Source scene: `{data['sourceSceneId']}`",
        f"- Current residual: `{line['currentResidual']}`",
        f"- Sample count: `{line['sampleCount']}`",
        f"- Reference/current comparable: `{line['referenceCurrentComparable']}`",
        f"- Reference/current/candidate comparable: `{line['referenceCurrentCandidateComparable']}`",
        "",
        "The residual is recomputed as the sum of absolute RGBA channel deltas across",
        "the committed high-delta samples and remains `856`.",
        "",
        "## Readiness",
        "",
        f"Classification: `{readiness['classification']}`",
        "",
    ]
    lines.extend(f"- {reason}" for reason in readiness["reasons"])
    lines.extend(
        [
            "",
            "## Available Metadata",
            "",
        ]
    )
    lines.extend(f"- {item}" for item in readiness["metadataAvailable"])
    lines.extend(
        [
            "",
            "## Missing For Candidate Evaluation",
            "",
        ]
    )
    lines.extend(f"- {item}" for item in readiness["nextEvidenceNeeded"])
    lines.extend(
        [
            "",
            "## Sample Table",
            "",
            "| # | x | y | region | reference RGBA | current RGBA | abs delta | residual |",
            "|---:|---:|---:|---|---|---|---|---:|",
        ]
    )
    for sample in samples:
        lines.append(
            f"| {sample['index']} | {sample['x']} | {sample['y']} | "
            f"`{sample['region']['id']}` | `{sample['referenceRgba']}` | "
            f"`{sample['currentRgba']}` | `{sample['absoluteChannelDelta']}` | "
            f"{sample['sampleResidual']} |"
        )

    lines.extend(
        [
            "",
            "## Boundaries Preserved",
            "",
            f"- FOR-355 candidate `{CLOSED_FOR355_POLICY_ID}` remains unselectable.",
            f"- FOR-365 candidate `{REJECTED_FOR365_POLICY_ID}` remains rejected and unselectable.",
            "- No renderer branch by scene id, coordinate, selected cell, fixture-only row,",
            "  or full-GM crop.",
            "- No score increase, threshold change, candidate implementation, or promotion.",
            "",
            "## Artifacts",
            "",
            f"- JSON: `{rel(ARTIFACT)}`",
            f"- Validator: `scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py`",
            f"- Report: `{rel(REPORT)}`",
            "",
            "## Validation",
            "",
        ]
    )
    lines.extend(f"- `{command}`" for command in VALIDATION_COMMANDS)
    return "\n".join(lines) + "\n"


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("allowedClassifications") == ALLOWED_CLASSIFICATIONS, "allowed classifications changed")

    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for366Decision") == FOR366_REQUIRED_DECISION, "FOR-366 input decision changed")
    require(inputs.get("for366SelectedFamily") == FAMILY_ID, "FOR-366 selected family changed")
    require(inputs.get("for366SelectedRowId") == ROW_ID, "FOR-366 selected row changed")
    require(inputs.get("for366CurrentResidual") == REQUIRED_RESIDUAL, "FOR-366 residual changed")
    require(inputs.get("for344Decision") == FOR344_REQUIRED_DECISION, "FOR-344 input decision changed")
    require(inputs.get("for344CurrentResidual") == REQUIRED_RESIDUAL, "FOR-344 residual changed")

    line = data.get("evidenceLine")
    require(isinstance(line, dict), "evidence line missing")
    require(line.get("rowId") == ROW_ID, "evidence row id changed")
    require(line.get("familyId") == FAMILY_ID, "evidence family changed")
    require(line.get("rowKind") == "non-arc", "evidence row kind changed")
    require(line.get("currentResidual") == REQUIRED_RESIDUAL, "evidence residual changed")
    require(line.get("candidateResidual") is None, "candidate residual unexpectedly exists")
    require(line.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    require(line.get("referenceCurrentComparable") is True, "reference/current comparability changed")
    require(line.get("referenceCurrentCandidateComparable") is False, "candidate comparability changed")

    computation = line.get("residualComputation")
    require(isinstance(computation, dict), "residual computation missing")
    require(computation.get("computedResidual") == REQUIRED_RESIDUAL, "computed residual changed")
    require(computation.get("matchesFor344CurrentResidual") is True, "FOR-344 residual mismatch")
    require(computation.get("matchesFor366CurrentResidual") is True, "FOR-366 residual mismatch")

    candidate = line.get("candidate")
    require(isinstance(candidate, dict), "candidate block missing")
    require(candidate.get("policyId") == F16_POLICY_ID, "candidate policy id changed")
    require(candidate.get("samplesAvailable") is False, "candidate samples unexpectedly available")
    require(candidate.get("candidateImplementationAuthorized") is False, "candidate implementation authorized")
    require(candidate.get("scoreIncreaseAuthorized") is False, "score increase authorized")

    source_input = line.get("sourceInputRgba")
    require(isinstance(source_input, dict), "source input RGBA block missing")
    require(source_input.get("available") is False, "source input RGBA unexpectedly available")

    samples = line.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples changed")
    residual = 0
    for index, sample in enumerate(samples, start=1):
        require(isinstance(sample, dict), "sample must be object")
        require(sample.get("index") == index, "sample index changed")
        reference = rgba(sample.get("referenceRgba"), "sample referenceRgba")
        current = rgba(sample.get("currentRgba"), "sample currentRgba")
        expected_delta = abs_delta(reference, current)
        require(sample.get("absoluteChannelDelta") == expected_delta, "sample delta changed")
        expected_residual = sample_residual(reference, current)
        require(sample.get("sampleResidual") == expected_residual, "sample residual changed")
        require(sample.get("candidatePolicyRgba") is None, "candidate sample unexpectedly available")
        require(sample.get("sourceInputRgba") is None, "source input sample unexpectedly available")
        residual += expected_residual
    require(residual == REQUIRED_RESIDUAL, "sample residual total changed")

    readiness = data.get("candidateReadiness")
    require(isinstance(readiness, dict), "candidate readiness missing")
    require(readiness.get("classification") == CLASSIFICATION, "readiness classification changed")
    require(readiness.get("readyForCandidateEvaluation") is False, "candidate readiness changed")
    require(isinstance(readiness.get("reasons"), list) and readiness["reasons"], "readiness reasons missing")

    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteria evaluation missing")
    for key, expected in (
        ("sourceMemoryRecorded", True),
        ("for366DecisionCaptured", True),
        ("for366DecisionRequired", True),
        ("m60RowConsolidated", True),
        ("currentResidualKeptAt856", True),
        ("referenceSamplesRecorded", True),
        ("currentSamplesRecorded", True),
        ("availableMetadataRecorded", True),
        ("classificationRecorded", True),
        ("for355CandidateExcluded", True),
        ("for365CandidateExcluded", True),
        ("candidateImplementationAuthorized", False),
        ("scoreIncreaseAuthorized", False),
    ):
        require(criteria.get(key) is expected, f"criteria changed: {key}")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("evidenceOnly") is True, "FOR-367 is no longer evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def main() -> None:
    data = build_artifact()
    json_text = json.dumps(data, indent=2, sort_keys=False) + "\n"
    report_text = render_report(data)
    write_if_changed(ARTIFACT, json_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    require(REPORT.read_text(encoding="utf-8") == report_text, "report is stale")
    print(
        f"{DECISION}: row={ROW_ID} residual={REQUIRED_RESIDUAL} "
        f"classification={CLASSIFICATION}"
    )


if __name__ == "__main__":
    main()
