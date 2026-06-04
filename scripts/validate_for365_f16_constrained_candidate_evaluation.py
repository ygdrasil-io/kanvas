#!/usr/bin/env python3
"""Validate the FOR-365 constrained F16 candidate evaluation."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-365"
SCENE_ID = "f16-constrained-candidate-evaluation-for365"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-365-f16-constrained-candidate-evaluation.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-constrained-candidate-evaluation-after-for-364"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-364-captures-independent-comparable-f16-arc-evidence",
    "global/kanvas/findings/for-363-constrained-f16-candidate-search-matrix-ready",
]

FOR364_SCENE_ID = "f16-independent-comparable-arc-evidence-for364"
FOR364_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR364_SCENE_ID / f"{FOR364_SCENE_ID}.json"
)
FOR363_SCENE_ID = "f16-constrained-candidate-search-for363"
FOR363_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR363_SCENE_ID / f"{FOR363_SCENE_ID}.json"
)
FOR362_SCENE_ID = "f16-rejected-candidate-closeout-for362"
FOR362_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR362_SCENE_ID / f"{FOR362_SCENE_ID}.json"
)
FOR361_SCENE_ID = "f16-bounded-independent-arc-capture-for361"
FOR361_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR361_SCENE_ID / f"{FOR361_SCENE_ID}.json"
)
FOR358_SCENE_ID = "f16-real-additional-non-arc-row-for358"
FOR358_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR358_SCENE_ID / f"{FOR358_SCENE_ID}.json"
)
FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)

FOR364_REQUIRED_DECISION = "F16_INDEPENDENT_COMPARABLE_ARC_EVIDENCE_CAPTURED"
FOR363_REQUIRED_DECISION = "F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY"
FOR362_REQUIRED_DECISION = "F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361"
FOR361_REQUIRED_DECISION = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CLOSED_FOR355_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CLOSED_FOR355_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
CANDIDATE_POLICY_ID = "covered_source_alpha_src_over_white_without_non_arc_guard_probe"
CANDIDATE_FAMILY_ID = "covered_source_alpha_over_white_f16_probe_family"

DECISION_REJECTED = "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS"
DECISION_READY = "F16_CONSTRAINED_CANDIDATE_READY_FOR_FUTURE_IMPLEMENTATION_TICKET"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_READY]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk python3 scripts/validate_for364_f16_independent_comparable_arc_evidence.py",
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 -m py_compile scripts/validate_for365_f16_constrained_candidate_evaluation.py",
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
    raise SystemExit(f"FOR-365 validation failed: {message}")


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
    result = []
    for channel in value:
        require(isinstance(channel, int) and 0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def residual(left: list[int], right: list[int]) -> int:
    return sum(abs(left[i] - right[i]) for i in range(4))


def signed_delta(left: list[int], right: list[int]) -> list[int]:
    return [left[i] - right[i] for i in range(4)]


def abs_delta(left: list[int], right: list[int]) -> list[int]:
    return [abs(left[i] - right[i]) for i in range(4)]


def over_white(source_rgba: list[int]) -> list[int]:
    r, g, b, a = source_rgba
    require(0 <= a <= 255, "source alpha out of range")
    return [
        (r * a + 255 * (255 - a) + 127) // 255,
        (g * a + 255 * (255 - a) + 127) // 255,
        (b * a + 255 * (255 - a) + 127) // 255,
        255,
    ]


def validate_implementation_guard(data: dict[str, Any], linear: str) -> None:
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


def validate_required_decision(
    data: dict[str, Any],
    linear: str,
    required_decision: str,
) -> None:
    require(data.get("linear") == linear, f"{linear} artifact identity changed")
    require(data.get("decision") == required_decision, f"{linear} required decision changed")
    validate_implementation_guard(data, linear)


def validate_for364(for364: dict[str, Any]) -> None:
    validate_required_decision(for364, "FOR-364", FOR364_REQUIRED_DECISION)
    inputs = for364.get("inputValidation")
    require(isinstance(inputs, dict), "FOR-364 input validation missing")
    require(inputs.get("for363Decision") == FOR363_REQUIRED_DECISION, "FOR-364 no longer depends on FOR-363")
    row = for364.get("row")
    require(isinstance(row, dict), "FOR-364 row missing")
    require(row.get("arcScene") is True, "FOR-364 row no longer marks arcScene")
    require(row.get("newIndependentArcRow") is True, "FOR-364 no longer marks a new independent row")
    require(row.get("independentFromFor361") is True, "FOR-364 FOR-361 independence changed")
    require(row.get("selectedCellSubstitutionUsed") is False, "FOR-364 selected-cell substitution changed")
    require(row.get("fullGmCrop") is False, "FOR-364 full-GM crop changed")
    future = row.get("futureCandidatePlaceholder")
    require(isinstance(future, dict), "FOR-364 future candidate placeholder missing")
    require(future.get("candidateFormulaDefined") is False, "FOR-364 already defined a candidate formula")


def validate_for363(for363: dict[str, Any]) -> None:
    validate_required_decision(for363, "FOR-363", FOR363_REQUIRED_DECISION)
    closed = for363.get("closedRejectedCandidate")
    require(isinstance(closed, dict), "FOR-363 closed rejected candidate missing")
    require(closed.get("policyId") == CLOSED_FOR355_POLICY_ID, "FOR-363 closed policy changed")
    require(closed.get("reopenedByFor363") is False, "FOR-363 reopened FOR-355")
    require(closed.get("selectedForEvaluation") is False, "FOR-363 selected FOR-355 for evaluation")
    required = for363.get("requiredEvidenceForNextTicket")
    require(isinstance(required, list), "FOR-363 required evidence missing")
    required_ids = {item.get("id") for item in required if isinstance(item, dict)}
    for required_id in (
        "preserved-non-arc-guards",
        "for361-no-worsening-check",
        "independent-comparable-arc-scene",
        "deterministic-next-ticket-artifacts",
    ):
        require(required_id in required_ids, f"FOR-363 missing required evidence: {required_id}")


def validate_for362(for362: dict[str, Any]) -> None:
    validate_required_decision(for362, "FOR-362", FOR362_REQUIRED_DECISION)
    candidate = for362.get("candidate")
    require(isinstance(candidate, dict), "FOR-362 candidate missing")
    require(candidate.get("policyId") == CLOSED_FOR355_POLICY_ID, "FOR-362 closed candidate changed")
    require(candidate.get("rejectedForSelection") is True, "FOR-362 candidate is no longer rejected")
    closeout = for362.get("selectionCloseout")
    require(isinstance(closeout, dict), "FOR-362 closeout missing")
    require(closeout.get("closedAsRejected") is True, "FOR-362 did not close the candidate as rejected")
    require(closeout.get("implementationPlanAuthorized") is False, "FOR-362 authorized implementation")


def validate_for355(for355: dict[str, Any]) -> dict[str, Any]:
    validate_required_decision(for355, "FOR-355", FOR355_REQUIRED_DECISION)
    candidate = for355.get("candidate")
    require(isinstance(candidate, dict), "FOR-355 candidate missing")
    require(candidate.get("policyId") == CLOSED_FOR355_POLICY_ID, "FOR-355 policy changed")
    require(candidate.get("family") == CLOSED_FOR355_FAMILY_ID, "FOR-355 family changed")
    require(candidate.get("rendererSelectable") is False, "FOR-355 became renderer-selectable")
    require(candidate.get("selectedForImplementation") is False, "FOR-355 selected implementation")
    return {
        "source": "FOR-355",
        "sourceArtifact": rel(FOR355_ARTIFACT),
        "policyId": candidate.get("policyId"),
        "family": candidate.get("family"),
        "closedBy": "FOR-362",
        "reopenedByFor365": False,
        "selectableInFor365": False,
        "selectedForEvaluation": False,
        "selectedForImplementation": False,
    }


def validate_source_row(
    data: dict[str, Any],
    linear: str,
    required_decision: str,
    artifact: Path,
    row_kind_key: str,
) -> dict[str, Any]:
    validate_required_decision(data, linear, required_decision)
    row = data.get("row")
    require(isinstance(row, dict), f"{linear} row missing")
    require(row.get(row_kind_key) is True, f"{linear} row kind changed: {row_kind_key}")
    require(row.get("referenceCurrentCandidateComparable") is True, f"{linear} comparability changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), f"{linear} residuals missing")
    samples = row.get("samples")
    require(isinstance(samples, list) and samples, f"{linear} samples missing")
    return {
        "linear": linear,
        "sourceArtifact": rel(artifact),
        "row": row,
        "sourceResiduals": residuals,
    }


def sample_source_rgba(sample: dict[str, Any]) -> list[int] | None:
    raw = sample.get("rawReferenceRgba")
    if raw is None:
        raw = sample.get("paintSourceRgba")
    if raw is None:
        return None
    return rgba(raw, f"{sample.get('name')} source")


def sample_current_residual(sample: dict[str, Any], current: list[int], reference: list[int]) -> int:
    raw = sample.get("currentResidual")
    if raw is None:
        raw = sample.get("currentVsReferenceSumAbsDelta")
    require(isinstance(raw, int), f"{sample.get('name')} current residual missing")
    require(raw == residual(current, reference), f"{sample.get('name')} current residual mismatch")
    return raw


def evaluate_row(source: dict[str, Any]) -> dict[str, Any]:
    linear = source["linear"]
    row = source["row"]
    sample_evaluations: list[dict[str, Any]] = []
    current_total = 0
    candidate_total = 0
    worsened_count = 0
    applied_count = 0
    for sample in row["samples"]:
        require(isinstance(sample, dict), f"{linear} sample must be an object")
        name = sample.get("name")
        require(isinstance(name, str) and name, f"{linear} sample name missing")
        current = rgba(sample.get("currentKanvasSrgbRgba"), f"{linear} {name} current")
        reference = rgba(sample.get("referenceSrgbRgba"), f"{linear} {name} reference")
        current_sample_residual = sample_current_residual(sample, current, reference)
        source_rgba = sample_source_rgba(sample)
        covered = sample.get("covered")
        if covered is None:
            covered = sample.get("insideDraw")
        require(isinstance(covered, bool), f"{linear} {name} covered/insideDraw missing")
        apply_candidate = (
            covered
            and source_rgba is not None
            and 0 < source_rgba[3] < 255
        )
        candidate = over_white(source_rgba) if apply_candidate and source_rgba is not None else current
        candidate_sample_residual = residual(candidate, reference)
        worsened = candidate_sample_residual > current_sample_residual
        current_total += current_sample_residual
        candidate_total += candidate_sample_residual
        if apply_candidate:
            applied_count += 1
        if worsened:
            worsened_count += 1
        sample_evaluations.append(
            {
                "sample": name,
                "zone": sample.get("zone"),
                "x": sample.get("x"),
                "y": sample.get("y"),
                "covered": covered,
                "sourceRgba": source_rgba,
                "candidateRuleApplied": apply_candidate,
                "referenceSrgbRgba": reference,
                "currentKanvasSrgbRgba": current,
                "candidateRgba": candidate,
                "currentVsReferenceSignedDelta": signed_delta(current, reference),
                "candidateVsReferenceSignedDelta": signed_delta(candidate, reference),
                "candidateVsReferenceAbsDelta": abs_delta(candidate, reference),
                "currentResidual": current_sample_residual,
                "candidateResidual": candidate_sample_residual,
                "candidateMinusCurrentResidual": candidate_sample_residual - current_sample_residual,
                "candidateWorsensCurrent": worsened,
            }
        )
    source_residuals = source["sourceResiduals"]
    require(current_total == source_residuals.get("currentResidual"), f"{linear} current residual total changed")
    delta = candidate_total - current_total
    return {
        "source": linear,
        "sourceArtifact": source["sourceArtifact"],
        "rowId": row.get("rowId"),
        "rowKind": "arc" if row.get("arcScene") is True else "non-arc",
        "currentResidual": current_total,
        "candidateResidual": candidate_total,
        "candidateMinusCurrentResidual": delta,
        "worsenedSampleCount": worsened_count,
        "candidateRuleAppliedSampleCount": applied_count,
        "guardWorsened": delta > 0 or worsened_count > 0,
        "sampleCount": len(sample_evaluations),
        "sampleEvaluations": sample_evaluations,
    }


def build_artifact(
    for364: dict[str, Any],
    for363: dict[str, Any],
    for362: dict[str, Any],
    for361: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    validate_for364(for364)
    validate_for363(for363)
    validate_for362(for362)
    closed_candidate = validate_for355(for355)
    sources = [
        validate_source_row(for345, "FOR-345", FOR345_REQUIRED_DECISION, FOR345_ARTIFACT, "nonArc"),
        validate_source_row(for358, "FOR-358", FOR358_REQUIRED_DECISION, FOR358_ARTIFACT, "nonArc"),
        validate_source_row(for361, "FOR-361", FOR361_REQUIRED_DECISION, FOR361_ARTIFACT, "arcScene"),
        validate_source_row(for364, "FOR-364", FOR364_REQUIRED_DECISION, FOR364_ARTIFACT, "arcScene"),
    ]
    evaluation_rows = [evaluate_row(source) for source in sources]
    any_guard_worsened = any(row["guardWorsened"] for row in evaluation_rows)
    decision = DECISION_REJECTED if any_guard_worsened else DECISION_READY
    for361_row = next(row for row in evaluation_rows if row["source"] == "FOR-361")
    require(for361_row["currentResidual"] == 0, "FOR-361 current residual changed")
    source_for361_residuals = sources[2]["sourceResiduals"]
    require(source_for361_residuals.get("candidateResidual") == 37, "FOR-361 rejected candidate residual changed")
    require(source_for361_residuals.get("worsenedSampleCount") == 1, "FOR-361 rejected candidate worsened count changed")
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-365 evaluates exactly one new evidence-only F16 candidate against FOR-345, FOR-358, "
            "FOR-361, and FOR-364. The candidate is rejected because at least one mandatory guard worsens."
        )
        if decision == DECISION_REJECTED
        else (
            "FOR-365 evaluates exactly one new evidence-only F16 candidate and all mandatory guards pass. "
            "This only makes the candidate eligible for a future implementation ticket; FOR-365 authorizes none."
        ),
        "inputValidation": {
            "for364Artifact": rel(FOR364_ARTIFACT),
            "for364Decision": for364.get("decision"),
            "for364RequiredDecision": FOR364_REQUIRED_DECISION,
            "for363Artifact": rel(FOR363_ARTIFACT),
            "for363Decision": for363.get("decision"),
            "for363RequiredDecision": FOR363_REQUIRED_DECISION,
            "for362Artifact": rel(FOR362_ARTIFACT),
            "for362Decision": for362.get("decision"),
            "for362RequiredDecision": FOR362_REQUIRED_DECISION,
            "for361Artifact": rel(FOR361_ARTIFACT),
            "for361Decision": for361.get("decision"),
            "for361RequiredDecision": FOR361_REQUIRED_DECISION,
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "closedFor355Candidate": closed_candidate,
        "preservedFor361RejectedCandidateResidual": {
            "source": "FOR-361",
            "sourceArtifact": rel(FOR361_ARTIFACT),
            "currentResidual": source_for361_residuals.get("currentResidual"),
            "rejectedCandidatePolicyId": CLOSED_FOR355_POLICY_ID,
            "rejectedCandidateResidual": source_for361_residuals.get("candidateResidual"),
            "rejectedCandidateMinusCurrentResidual": source_for361_residuals.get("candidateMinusCurrentResidual"),
            "rejectedCandidateWorsenedSampleCount": source_for361_residuals.get("worsenedSampleCount"),
        },
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "closedFor355PolicyId": CLOSED_FOR355_POLICY_ID,
            "distinctFromFor355ClosedCandidate": True,
            "reusesFor355Policy": False,
            "exactlyOneCandidateEvaluated": True,
            "evaluatedByFor365": True,
            "selectedForImplementation": False,
            "implementationAuthorized": False,
            "scoreIncreaseAuthorized": False,
            "rendererBehaviorChanged": False,
            "samplesComputedOnlyInArtifact": True,
            "formula": (
                "Evidence-only probe: for any covered sample with captured source/raw RGBA alpha in (0, 255), "
                "compute that source color alpha-composited over white; otherwise preserve current Kanvas RGBA. "
                "The computed samples are artifact values only and are not renderer behavior."
            ),
            "shaping": {
                "sceneIdShaped": False,
                "coordinateShaped": False,
                "selectedCellShaped": False,
                "fixtureOnlyShaped": False,
                "fullGmCropShaped": False,
                "rendererBranchRequired": False,
            },
        },
        "evaluation": {
            "guardSources": ["FOR-345", "FOR-358", "FOR-361", "FOR-364"],
            "rejectIfAnyGuardWorsens": True,
            "anyGuardWorsened": any_guard_worsened,
            "table": evaluation_rows,
        },
        "criteriaEvaluation": {
            "for364DecisionCaptured": for364.get("decision") == FOR364_REQUIRED_DECISION,
            "for363SearchMatrixReady": for363.get("decision") == FOR363_REQUIRED_DECISION,
            "for362RejectedCandidateCloseoutReady": for362.get("decision") == FOR362_REQUIRED_DECISION,
            "for361CurrentResidualPreserved": source_for361_residuals.get("currentResidual") == 0,
            "for361RejectedCandidateResidualPreserved": source_for361_residuals.get("candidateResidual") == 37,
            "for355ClosedCandidateNotReopened": True,
            "candidateDistinctFromFor355": True,
            "candidateSamplesOnlyInArtifact": True,
            "noDisallowedBranchRequired": True,
            "noRendererChange": True,
            "noScoreIncrease": True,
            "noThresholdChange": True,
            "candidateRejectedBecauseAnyGuardWorsened": any_guard_worsened,
            "readyForFutureImplementationTicket": decision == DECISION_READY,
            "implementationAuthorizedByFor365": False,
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


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == SOURCE_FINDINGS, "source findings changed")
    require(data.get("decision") in ALLOWED_DECISIONS, "unexpected decision")
    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for364Decision") == FOR364_REQUIRED_DECISION, "FOR-364 decision dependency changed")
    require(inputs.get("for363Decision") == FOR363_REQUIRED_DECISION, "FOR-363 decision dependency changed")
    require(inputs.get("for362Decision") == FOR362_REQUIRED_DECISION, "FOR-362 decision dependency changed")
    require(inputs.get("for361Decision") == FOR361_REQUIRED_DECISION, "FOR-361 decision dependency changed")
    require(inputs.get("for358Decision") == FOR358_REQUIRED_DECISION, "FOR-358 decision dependency changed")
    require(inputs.get("for355Decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision dependency changed")
    require(inputs.get("for345Decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision dependency changed")

    closed = data.get("closedFor355Candidate")
    require(isinstance(closed, dict), "closed FOR-355 candidate missing")
    require(closed.get("policyId") == CLOSED_FOR355_POLICY_ID, "closed FOR-355 policy changed")
    require(closed.get("reopenedByFor365") is False, "FOR-365 reopened FOR-355")
    require(closed.get("selectableInFor365") is False, "FOR-365 made FOR-355 selectable")
    require(closed.get("selectedForEvaluation") is False, "FOR-365 selected FOR-355 for evaluation")
    require(closed.get("selectedForImplementation") is False, "FOR-365 selected FOR-355 for implementation")

    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy id changed")
    require(candidate.get("policyId") != CLOSED_FOR355_POLICY_ID, "candidate reuses FOR-355 policy id")
    require(candidate.get("distinctFromFor355ClosedCandidate") is True, "candidate distinctness changed")
    require(candidate.get("reusesFor355Policy") is False, "candidate reuses FOR-355")
    require(candidate.get("exactlyOneCandidateEvaluated") is True, "FOR-365 must evaluate exactly one candidate")
    require(candidate.get("samplesComputedOnlyInArtifact") is True, "candidate samples escaped artifact-only scope")
    shaping = candidate.get("shaping")
    require(isinstance(shaping, dict), "candidate shaping missing")
    for key in (
        "sceneIdShaped",
        "coordinateShaped",
        "selectedCellShaped",
        "fixtureOnlyShaped",
        "fullGmCropShaped",
        "rendererBranchRequired",
    ):
        require(shaping.get(key) is False, f"candidate requires disallowed branch: {key}")

    preserved = data.get("preservedFor361RejectedCandidateResidual")
    require(isinstance(preserved, dict), "FOR-361 preserved rejection missing")
    require(preserved.get("currentResidual") == 0, "FOR-361 current residual not preserved")
    require(preserved.get("rejectedCandidateResidual") == 37, "FOR-361 rejected candidate residual not preserved")
    require(preserved.get("rejectedCandidateWorsenedSampleCount") == 1, "FOR-361 rejected count not preserved")

    evaluation = data.get("evaluation")
    require(isinstance(evaluation, dict), "evaluation missing")
    require(evaluation.get("guardSources") == ["FOR-345", "FOR-358", "FOR-361", "FOR-364"], "guard sources changed")
    rows = evaluation.get("table")
    require(isinstance(rows, list) and len(rows) == 4, "evaluation table must contain four rows")
    rows_by_source = {row.get("source"): row for row in rows if isinstance(row, dict)}
    require(set(rows_by_source) == {"FOR-345", "FOR-358", "FOR-361", "FOR-364"}, "evaluation table sources changed")
    any_guard_worsened = False
    for source, row in rows_by_source.items():
        require(isinstance(row.get("currentResidual"), int), f"{source} current residual missing")
        require(isinstance(row.get("candidateResidual"), int), f"{source} candidate residual missing")
        require(
            row.get("candidateMinusCurrentResidual") == row["candidateResidual"] - row["currentResidual"],
            f"{source} residual delta changed",
        )
        require(isinstance(row.get("worsenedSampleCount"), int), f"{source} worsened count missing")
        require(isinstance(row.get("sampleEvaluations"), list), f"{source} sample evaluations missing")
        any_guard_worsened = any_guard_worsened or row.get("guardWorsened") is True
    require(evaluation.get("anyGuardWorsened") is any_guard_worsened, "aggregate guard worsening changed")
    expected_decision = DECISION_REJECTED if any_guard_worsened else DECISION_READY
    require(data.get("decision") == expected_decision, "decision does not match guard result")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    require(implementation.get("evidenceOnly") is True, "FOR-365 must be evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    candidate = data["candidate"]
    table_rows = "\n".join(
        "| `{source}` | `{row}` | {current} | {candidate_residual} | {delta} | {worsened} | `{guard}` |".format(
            source=row["source"],
            row=row["rowId"],
            current=row["currentResidual"],
            candidate_residual=row["candidateResidual"],
            delta=row["candidateMinusCurrentResidual"],
            worsened=row["worsenedSampleCount"],
            guard="worsened" if row["guardWorsened"] else "preserved",
        )
        for row in data["evaluation"]["table"]
    )
    sample_sections = []
    for row in data["evaluation"]["table"]:
        sample_rows = "\n".join(
            "| `{sample}` | `{zone}` | `{applied}` | {current} | {candidate} | {delta} | `{worsened}` |".format(
                sample=sample["sample"],
                zone=sample["zone"],
                applied=sample["candidateRuleApplied"],
                current=sample["currentResidual"],
                candidate=sample["candidateResidual"],
                delta=sample["candidateMinusCurrentResidual"],
                worsened=sample["candidateWorsensCurrent"],
            )
            for sample in row["sampleEvaluations"]
        )
        sample_sections.append(
            f"""### {row["source"]} Samples

| sample | zone | rule applied | current residual | candidate residual | delta | worsens |
|---|---|---:|---:|---:|---:|---|
{sample_rows}
"""
        )
    sample_text = "\n".join(sample_sections)
    shaping = "\n".join(
        f"- `{key}`: `{value}`" for key, value in candidate["shaping"].items()
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    preserved = data["preservedFor361RejectedCandidateResidual"]
    return f"""# FOR-365 F16 Constrained Candidate Evaluation

Linear: `FOR-365`

Decision: `{data["decision"]}`

Candidate: `{candidate["policyId"]}`

FOR-365 evaluates exactly one new evidence-only F16 candidate after FOR-364. It
does not change renderer behavior, raise score, change thresholds, authorize
implementation, or alter GPU/WGSL, geometry, coverage, fallback, Kadre, F16
premul, blend, or `SkBitmap.getPixel` behavior.

## Candidate Formula

{candidate["formula"]}

The candidate is distinct from closed FOR-355 policy
`{candidate["closedFor355PolicyId"]}` and reuses none of its selection state.

## Required Inputs

- FOR-364: `{data["inputValidation"]["for364Decision"]}`
- FOR-363: `{data["inputValidation"]["for363Decision"]}`
- FOR-362: `{data["inputValidation"]["for362Decision"]}`
- FOR-361: `{data["inputValidation"]["for361Decision"]}`
- FOR-358: `{data["inputValidation"]["for358Decision"]}`
- FOR-355: `{data["inputValidation"]["for355Decision"]}`
- FOR-345: `{data["inputValidation"]["for345Decision"]}`

FOR-361 rejection evidence is preserved: current residual
`{preserved["currentResidual"]}`, rejected FOR-355 candidate residual
`{preserved["rejectedCandidateResidual"]}`, delta
`{preserved["rejectedCandidateMinusCurrentResidual"]}`, worsened samples
`{preserved["rejectedCandidateWorsenedSampleCount"]}`.

## Guard Evaluation

| source | row | current residual | candidate residual | delta | worsened samples | guard |
|---|---|---:|---:|---:|---:|---|
{table_rows}

Result: the candidate is rejected because
`anyGuardWorsened={data["evaluation"]["anyGuardWorsened"]}`. This rejection is
evidence-only and does not authorize an implementation.

## Candidate Samples

Candidate samples are computed only in
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`.

{sample_text}
## Disallowed Branch Guards

{shaping}

## Closed FOR-355 Boundary

- Closed policy: `{data["closedFor355Candidate"]["policyId"]}`
- Reopened by FOR-365: `{data["closedFor355Candidate"]["reopenedByFor365"]}`
- Selectable in FOR-365: `{data["closedFor355Candidate"]["selectableInFor365"]}`
- Selected for evaluation: `{data["closedFor355Candidate"]["selectedForEvaluation"]}`
- Selected for implementation: `{data["closedFor355Candidate"]["selectedForImplementation"]}`

## Non-goals Preserved

- No renderer behavior change.
- No score increase, threshold change, candidate implementation, promotion, or
  selectable renderer policy.
- No GPU/WGSL, geometry, coverage, fallback, Kadre, `SkBitmap.getPixel`, F16
  premul, or blend change.
- No scene-id, coordinate, selected-cell, fixture-only, or full-GM-crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for365_f16_constrained_candidate_evaluation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-365-f16-constrained-candidate-evaluation.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR364_ARTIFACT),
        load_json(FOR363_ARTIFACT),
        load_json(FOR362_ARTIFACT),
        load_json(FOR361_ARTIFACT),
        load_json(FOR358_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    worsened = sum(1 for row in data["evaluation"]["table"] if row["guardWorsened"])
    print(
        f"{data['decision']}: candidate={CANDIDATE_POLICY_ID} "
        f"guards={len(data['evaluation']['table'])} worsenedGuards={worsened}"
    )


if __name__ == "__main__":
    main()
