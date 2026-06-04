#!/usr/bin/env python3
"""Validate the FOR-363 constrained F16 candidate-search matrix."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-363"
SCENE_ID = "f16-constrained-candidate-search-for363"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-363-f16-constrained-candidate-search.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-constrained-f16-candidate-search-after-for-362"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-362-closes-rejected-for-355-f16-candidate",
    "global/kanvas/findings/for-361-bounded-independent-f16-arc-capture-rejects-candidate",
]

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

FOR362_REQUIRED_DECISION = "F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361"
FOR361_REQUIRED_DECISION = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
REJECTED_SAMPLE = "for361_arc_right_stroke_center"

DECISION_READY = "F16_CONSTRAINED_CANDIDATE_SEARCH_MATRIX_READY"
ALLOWED_DECISIONS = [DECISION_READY]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 -m py_compile scripts/validate_for363_f16_constrained_candidate_search.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

IMPLEMENTATION_FALSE_KEYS = (
    "rendererBehaviorChanged",
    "newColorPolicyImplemented",
    "candidateSelectedForImplementation",
    "candidateSelectedForEvaluation",
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
    raise SystemExit(f"FOR-363 validation failed: {message}")


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


def validate_candidate(data: dict[str, Any], linear: str) -> dict[str, Any]:
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), f"{linear} candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, f"{linear} candidate policy changed")
    require(candidate.get("newCandidateDefined") is not True, f"{linear} defined a new candidate")
    require(candidate.get("selectedForImplementation") is False, f"{linear} selected candidate")
    require(candidate.get("rendererBehaviorChanged") is False, f"{linear} changed renderer behavior")
    require(candidate.get("scoreIncreaseAuthorized") is False, f"{linear} authorized score increase")
    return candidate


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


def validate_for362(for362: dict[str, Any]) -> list[dict[str, Any]]:
    require(for362.get("linear") == "FOR-362", "FOR-362 identity changed")
    require(
        for362.get("decision") == FOR362_REQUIRED_DECISION,
        "FOR-362 closeout decision changed",
    )
    candidate = validate_candidate(for362, "FOR-362")
    require(candidate.get("reusedFromFor355") is True, "FOR-362 no longer closes the FOR-355 candidate")
    require(candidate.get("rejectedForSelection") is True, "FOR-362 candidate is no longer rejected")

    closeout = for362.get("selectionCloseout")
    require(isinstance(closeout, dict), "FOR-362 selection closeout missing")
    require(closeout.get("candidateSelectable") is False, "FOR-362 candidate became selectable")
    require(closeout.get("closedAsRejected") is True, "FOR-362 did not close as rejected")
    require(closeout.get("implementationPlanAuthorized") is False, "FOR-362 authorized implementation")
    require(closeout.get("scoreRaised") is False, "FOR-362 raised score")
    require(closeout.get("newCandidateDefinedByFor362") is False, "FOR-362 defined a new candidate")

    constraints = for362.get("nextCandidateSearchConstraints")
    require(isinstance(constraints, list), "FOR-362 nextCandidateSearchConstraints missing")
    constraint_ids = {item.get("id") for item in constraints if isinstance(item, dict)}
    for required_id in (
        "preserve-for345",
        "preserve-for358",
        "do-not-worsen-for361",
        "require-independent-comparable-arc-scene",
        "refuse-scene-coordinate-selected-cell-full-gm-crop-branches",
    ):
        require(required_id in constraint_ids, f"FOR-362 missing constraint: {required_id}")
    validate_implementation_guard(for362, "FOR-362")
    return constraints


def validate_for361(for361: dict[str, Any]) -> dict[str, Any]:
    require(for361.get("linear") == "FOR-361", "FOR-361 identity changed")
    require(for361.get("decision") == FOR361_REQUIRED_DECISION, "FOR-361 rejection decision changed")
    candidate = validate_candidate(for361, "FOR-361")
    require(candidate.get("reusedFromFor355") is True, "FOR-361 no longer evaluates FOR-355 candidate")
    row = for361.get("row")
    require(isinstance(row, dict), "FOR-361 row missing")
    require(row.get("arcScene") is True, "FOR-361 row is no longer arc evidence")
    require(row.get("independentFromFor340For341AdjacentGroups") is True, "FOR-361 independence changed")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-361 comparability changed")
    require(row.get("selectedCellSubstitutionUsed") is False, "FOR-361 selected-cell substitution changed")
    require(row.get("fullGmCrop") is False, "FOR-361 full-GM crop changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-361 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-361 current residual changed")
    require(residuals.get("candidateResidual") == 37, "FOR-361 candidate residual changed")
    require(residuals.get("candidateMinusCurrentResidual") == 37, "FOR-361 residual delta changed")
    require(residuals.get("worsenedSampleCount") == 1, "FOR-361 worsened count changed")
    validate_implementation_guard(for361, "FOR-361")
    return {
        "source": "FOR-361",
        "sourceArtifact": rel(FOR361_ARTIFACT),
        "rowId": row.get("rowId"),
        "sample": REJECTED_SAMPLE,
        "currentResidual": residuals.get("currentResidual"),
        "candidateResidual": residuals.get("candidateResidual"),
        "candidateMinusCurrentResidual": residuals.get("candidateMinusCurrentResidual"),
        "worsenedSampleCount": residuals.get("worsenedSampleCount"),
    }


def validate_for358(for358: dict[str, Any]) -> dict[str, Any]:
    require(for358.get("linear") == "FOR-358", "FOR-358 identity changed")
    require(for358.get("decision") == FOR358_REQUIRED_DECISION, "FOR-358 decision changed")
    validate_candidate(for358, "FOR-358")
    row = for358.get("row")
    require(isinstance(row, dict), "FOR-358 row missing")
    require(row.get("nonArc") is True, "FOR-358 row is no longer non-arc")
    require(row.get("distinctFromFor345") is True, "FOR-358 distinction from FOR-345 changed")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-358 comparability changed")
    validate_implementation_guard(for358, "FOR-358")
    return {
        "source": "FOR-358",
        "sourceArtifact": rel(FOR358_ARTIFACT),
        "rowId": row.get("rowId"),
        "guardPreserved": True,
    }


def validate_for355(for355: dict[str, Any]) -> dict[str, Any]:
    require(for355.get("linear") == "FOR-355", "FOR-355 identity changed")
    require(for355.get("decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision changed")
    candidate = validate_candidate(for355, "FOR-355")
    require(candidate.get("family") == CANDIDATE_FAMILY_ID, "FOR-355 family changed")
    require(candidate.get("rendererSelectable") is False, "FOR-355 candidate became renderer-selectable")
    require(candidate.get("selectedForImplementation") is False, "FOR-355 candidate was selected")
    validate_implementation_guard(for355, "FOR-355")
    return {
        "source": "FOR-355",
        "sourceArtifact": rel(FOR355_ARTIFACT),
        "policyId": candidate.get("policyId"),
        "family": candidate.get("family"),
        "selectedForImplementation": False,
        "rendererSelectable": False,
        "remainsRejectedAndUnselected": True,
    }


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 comparability changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    return {
        "source": "FOR-345",
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "rowId": row.get("rowId"),
        "guardPreserved": True,
    }


def rejection_criteria() -> list[dict[str, Any]]:
    return [
        {
            "id": "rejected-candidate-reuse",
            "rejectWhen": (
                "the future proposal reuses `nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard`, "
                "or an equivalent FOR-355 policy, as a selectable candidate"
            ),
        },
        {
            "id": "for361-worsening",
            "rejectWhen": "the future proposal worsens the bounded independent FOR-361 arc row versus current Kanvas",
        },
        {
            "id": "losing-for345-for358-guards",
            "rejectWhen": "the future proposal loses either preserved non-arc guard from FOR-345 or FOR-358",
        },
        {
            "id": "scene-coordinate-selected-cell-full-gm-crop-branch",
            "rejectWhen": (
                "the future proposal depends on scene id, coordinate, selected-cell, fixture, or full-GM crop branching"
            ),
        },
        {
            "id": "missing-independent-comparable-arc-scene",
            "rejectWhen": "the future proposal lacks an independent comparable F16 arc scene with reference/current samples",
        },
    ]


def candidate_family_search_matrix() -> list[dict[str, Any]]:
    return [
        {
            "familyId": "guard-first-independent-arc-alpha-family",
            "description": (
                "Abstract guard-first family for independent F16 arc alpha/composite behavior that keeps "
                "FOR-345 and FOR-358 non-arc rows comparable before any arc evaluation."
            ),
            "requiresFutureEvidence": True,
            "selectedForEvaluation": False,
            "selectableCandidateDefined": False,
            "candidateFormulaDefined": False,
            "mustPreserve": ["FOR-345", "FOR-358", "FOR-361"],
        },
        {
            "familyId": "non-scene-cross-row-f16-composition-family",
            "description": (
                "Abstract cross-row F16 composition family that may only be explored after new independent "
                "arc evidence proves it is not scene, coordinate, selected-cell, or full-GM-crop shaped."
            ),
            "requiresFutureEvidence": True,
            "selectedForEvaluation": False,
            "selectableCandidateDefined": False,
            "candidateFormulaDefined": False,
            "mustPreserve": ["FOR-345", "FOR-358", "FOR-361"],
        },
        {
            "familyId": "bounded-arc-non-arc-invariant-family",
            "description": (
                "Abstract invariant family for candidates that would need to demonstrate no non-arc regression "
                "and no FOR-361 worsening before any score, implementation, or promotion discussion."
            ),
            "requiresFutureEvidence": True,
            "selectedForEvaluation": False,
            "selectableCandidateDefined": False,
            "candidateFormulaDefined": False,
            "mustPreserve": ["FOR-345", "FOR-358", "FOR-361"],
        },
    ]


def required_evidence() -> list[dict[str, Any]]:
    return [
        {
            "id": "preserved-non-arc-guards",
            "requires": [
                "FOR-345 reference/current comparable samples remain present",
                "FOR-358 reference/current comparable samples remain present and distinct from FOR-345",
                "future candidate-computed samples show no loss of either non-arc guard",
            ],
        },
        {
            "id": "for361-no-worsening-check",
            "requires": [
                "FOR-361 bounded independent arc row remains comparable",
                "future candidate-computed samples do not exceed current residual on FOR-361",
                "the FOR-361 rejection sample remains explicitly audited",
            ],
        },
        {
            "id": "independent-comparable-arc-scene",
            "requires": [
                "at least one independent F16 arc scene with isolated Skia reference samples",
                "matching current Kanvas CPU samples",
                "candidate-computed samples generated only as evidence, not renderer behavior",
                "no selected-cell substitution and no full-GM crop branch",
            ],
        },
        {
            "id": "deterministic-next-ticket-artifacts",
            "requires": [
                "deterministic JSON under reports/wgsl-pipeline/scenes/artifacts",
                "human-readable report under reports/wgsl-pipeline",
                "focused validator that fails before evaluation when any rejection criterion is hit",
            ],
        },
    ]


def build_artifact(
    for362: dict[str, Any],
    for361: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    next_constraints = validate_for362(for362)
    rejection = validate_for361(for361)
    for358_guard = validate_for358(for358)
    rejected_candidate = validate_for355(for355)
    for345_guard = validate_for345(for345)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-363 restarts the F16 search only as a constrained evidence matrix. FOR-362 closed the "
            "FOR-355 candidate as rejected, FOR-361 rejected that candidate on an independent arc row, "
            "and FOR-362 preserved FOR-345/FOR-358 as mandatory non-arc guards for any next search."
        ),
        "inputValidation": {
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
        "closedRejectedCandidate": {
            **rejected_candidate,
            "closedBy": "FOR-362",
            "rejectionSource": "FOR-361",
            "reopenedByFor363": False,
            "selectedForEvaluation": False,
            "selectedForImplementation": False,
            "selectableInThisTicket": False,
        },
        "preservedConstraintsFromFor362": next_constraints,
        "rejectionEvidence": rejection,
        "nonArcGuardsPreserved": {
            "for345": for345_guard,
            "for358": for358_guard,
        },
        "rejectionCriteriaBeforeEvaluation": rejection_criteria(),
        "candidateFamilySearchMatrix": candidate_family_search_matrix(),
        "requiredEvidenceForNextTicket": required_evidence(),
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
            "candidateSelectedForEvaluation": False,
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
    require(data.get("decision") == DECISION_READY, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    inputs = data.get("inputValidation")
    require(isinstance(inputs, dict), "input validation missing")
    require(inputs.get("for362Decision") == FOR362_REQUIRED_DECISION, "FOR-362 closeout decision missing")
    require(inputs.get("for362RequiredDecision") == FOR362_REQUIRED_DECISION, "FOR-362 required decision missing")
    require(inputs.get("for361Decision") == FOR361_REQUIRED_DECISION, "FOR-361 decision missing")
    require(inputs.get("for358Decision") == FOR358_REQUIRED_DECISION, "FOR-358 decision missing")
    require(inputs.get("for355Decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision missing")

    candidate = data.get("closedRejectedCandidate")
    require(isinstance(candidate, dict), "closed rejected candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "rejected candidate policy changed")
    require(candidate.get("reopenedByFor363") is False, "FOR-363 reopened rejected candidate")
    require(candidate.get("selectedForEvaluation") is False, "FOR-363 selected rejected candidate for evaluation")
    require(candidate.get("selectedForImplementation") is False, "FOR-363 selected rejected candidate")
    require(candidate.get("selectableInThisTicket") is False, "FOR-363 made rejected candidate selectable")

    rejection_ids = {
        item.get("id") for item in data.get("rejectionCriteriaBeforeEvaluation", []) if isinstance(item, dict)
    }
    for required_id in (
        "rejected-candidate-reuse",
        "for361-worsening",
        "losing-for345-for358-guards",
        "scene-coordinate-selected-cell-full-gm-crop-branch",
        "missing-independent-comparable-arc-scene",
    ):
        require(required_id in rejection_ids, f"missing rejection criterion: {required_id}")

    families = data.get("candidateFamilySearchMatrix")
    require(isinstance(families, list), "candidate family matrix missing")
    require(2 <= len(families) <= 4, "candidate family matrix must contain 2-4 abstract families")
    for family in families:
        require(isinstance(family, dict), "candidate family row must be an object")
        require(family.get("requiresFutureEvidence") is True, "family does not require future evidence")
        require(family.get("selectedForEvaluation") is False, "family selected for evaluation")
        require(family.get("selectableCandidateDefined") is False, "family defines selectable candidate")
        require(family.get("candidateFormulaDefined") is False, "family defines candidate formula")

    required_evidence = data.get("requiredEvidenceForNextTicket")
    require(isinstance(required_evidence, list) and required_evidence, "required evidence missing")
    required_evidence_ids = {item.get("id") for item in required_evidence if isinstance(item, dict)}
    for required_id in (
        "preserved-non-arc-guards",
        "for361-no-worsening-check",
        "independent-comparable-arc-scene",
        "deterministic-next-ticket-artifacts",
    ):
        require(required_id in required_evidence_ids, f"missing required evidence: {required_id}")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("evidenceOnly") is True, "artifact is not evidence-only")
    for key in IMPLEMENTATION_FALSE_KEYS:
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    criteria = "\n".join(
        f"- `{item['id']}`: reject when {item['rejectWhen']}"
        for item in data["rejectionCriteriaBeforeEvaluation"]
    )
    families = "\n".join(
        "| `{familyId}` | {requires} | {selected} | {selectable} |".format(
            familyId=item["familyId"],
            requires=item["requiresFutureEvidence"],
            selected=item["selectedForEvaluation"],
            selectable=item["selectableCandidateDefined"],
        )
        for item in data["candidateFamilySearchMatrix"]
    )
    evidence = "\n".join(
        f"- `{item['id']}`: " + "; ".join(item["requires"])
        for item in data["requiredEvidenceForNextTicket"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    inputs = data["inputValidation"]
    rejection = data["rejectionEvidence"]
    return f"""# FOR-363 F16 Constrained Candidate Search

Linear: `FOR-363`

Decision: `{data["decision"]}`

FOR-363 restarts F16 candidate search after the rejected FOR-355 candidate was
closed. It is evidence-only: it does not reopen the rejected candidate, define a
selectable new candidate, select any candidate family for evaluation, raise
score, or change renderer behavior.

## Required Gates

- FOR-362 required closeout decision: `{inputs["for362Decision"]}`
- FOR-361 decision: `{inputs["for361Decision"]}`
- FOR-358 decision: `{inputs["for358Decision"]}`
- FOR-355 decision: `{inputs["for355Decision"]}`

The FOR-355 candidate remains unselected and unavailable for reuse:
`{data["closedRejectedCandidate"]["policyId"]}`.

## Rejection Evidence

FOR-361 row `{rejection["rowId"]}` rejected the candidate at
`{rejection["sample"]}`: current residual `{rejection["currentResidual"]}`,
candidate residual `{rejection["candidateResidual"]}`, delta
`{rejection["candidateMinusCurrentResidual"]}`, worsened samples
`{rejection["worsenedSampleCount"]}`.

## Rejection Criteria Before Evaluation

{criteria}

## Candidate Family Search Matrix

| family | requires future evidence | selected for evaluation | selectable candidate defined |
|---|---:|---:|---:|
{families}

Every family is abstract, requires future evidence, and is not selected for
evaluation by this ticket.

## Required Evidence For Next Ticket

{evidence}

## Non-goals Preserved

- No renderer behavior change.
- No candidate implementation, selectable candidate, score increase, threshold
  change, GPU/WGSL change, geometry/coverage/fallback/promotion change, or
  Kadre change.
- No scene, coordinate, selected-cell, fixture, or full-GM crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for363_f16_constrained_candidate_search.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-363-f16-constrained-candidate-search.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR362_ARTIFACT),
        load_json(FOR361_ARTIFACT),
        load_json(FOR358_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print(
        f"{DECISION_READY}: families={len(data['candidateFamilySearchMatrix'])} "
        f"selectedForEvaluation=false"
    )


if __name__ == "__main__":
    main()
