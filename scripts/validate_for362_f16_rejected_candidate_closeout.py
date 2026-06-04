#!/usr/bin/env python3
"""Validate the FOR-362 evidence-only closeout for the rejected F16 candidate."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-362"
SCENE_ID = "f16-rejected-candidate-closeout-for362"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-362-f16-rejected-candidate-closeout.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-close-rejected-f16-candidate-after-for-361"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-361-bounded-independent-f16-arc-capture-rejects-candidate",
    "global/kanvas/findings/for-360-independent-arc-scene-evidence-remains-partial",
    "global/kanvas/findings/for-359-f16-candidate-still-requires-independent-arc-scene",
]

FOR361_SCENE_ID = "f16-bounded-independent-arc-capture-for361"
FOR361_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR361_SCENE_ID / f"{FOR361_SCENE_ID}.json"
)
FOR360_SCENE_ID = "f16-independent-arc-scene-evidence-for360"
FOR360_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR360_SCENE_ID / f"{FOR360_SCENE_ID}.json"
)
FOR359_SCENE_ID = "f16-candidate-after-for358-guard-for359"
FOR359_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR359_SCENE_ID / f"{FOR359_SCENE_ID}.json"
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

FOR361_REQUIRED_DECISION = "F16_BOUNDED_INDEPENDENT_ARC_CAPTURE_REJECTS_CANDIDATE"
FOR360_REQUIRED_DECISION = "F16_INDEPENDENT_ARC_SCENE_EVIDENCE_PARTIAL"
FOR359_REQUIRED_DECISION = "F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
REJECTED_SAMPLE = "for361_arc_right_stroke_center"

DECISION_CLOSED = "F16_REJECTED_CANDIDATE_CLOSEOUT_AFTER_FOR361"
ALLOWED_DECISIONS = [DECISION_CLOSED]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk python3 scripts/validate_for361_f16_bounded_independent_arc_capture.py",
    "rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py",
    "rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for362_f16_rejected_candidate_closeout.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

NEXT_CANDIDATE_SEARCH_CONSTRAINTS = [
    {
        "id": "preserve-for345",
        "source": "FOR-345",
        "required": "preserve the original non-arc Rec.2020 F16 comparable guard",
    },
    {
        "id": "preserve-for358",
        "source": "FOR-358",
        "required": "preserve the additional real non-arc Rec.2020 F16 comparable guard",
    },
    {
        "id": "do-not-worsen-for361",
        "source": "FOR-361",
        "required": "do not worsen the bounded independent arc row where current residual is zero",
    },
    {
        "id": "require-independent-comparable-arc-scene",
        "required": "require at least one independent comparable F16 arc scene",
    },
    {
        "id": "refuse-scene-coordinate-selected-cell-full-gm-crop-branches",
        "refuse": [
            "scene branch",
            "coordinate branch",
            "selected-cell branch",
            "full-GM crop branch",
        ],
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-362 validation failed: {message}")


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


def validate_required_decision(data: dict[str, Any], linear: str, required_decision: str) -> dict[str, Any]:
    require(data.get("linear") == linear, f"{linear} artifact identity changed")
    require(data.get("decision") == required_decision, f"{linear} required decision missing")
    return validate_candidate(data, linear)


def sample_by_name(samples: Any, name: str) -> dict[str, Any]:
    require(isinstance(samples, list), "FOR-361 samples missing")
    for sample in samples:
        if isinstance(sample, dict) and sample.get("name") == name:
            return sample
    fail(f"FOR-361 missing sample: {name}")


def validate_for361_rejection(for361: dict[str, Any]) -> dict[str, Any]:
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
    require(residuals.get("worsenedSampleCount") == 1, "FOR-361 worsened sample count changed")
    require(residuals.get("for355RuleAppliedSampleCount") == 1, "FOR-361 FOR-355 rule count changed")

    sample = sample_by_name(row.get("samples"), REJECTED_SAMPLE)
    require(sample.get("for355RuleApplies") is True, "FOR-361 rejected sample no longer applies FOR-355 rule")
    require(sample.get("currentResidual") == 0, "FOR-361 rejected sample current residual changed")
    require(sample.get("candidateResidual") == 37, "FOR-361 rejected sample candidate residual changed")
    require(sample.get("candidateWorsensCurrent") is True, "FOR-361 rejected sample no longer worsens current")
    require(sample.get("referenceSrgbRgba") == [180, 167, 255, 255], "FOR-361 reference sample changed")
    require(sample.get("currentKanvasSrgbRgba") == [180, 167, 255, 255], "FOR-361 current sample changed")
    require(sample.get("candidateRgba") == [155, 155, 255, 255], "FOR-361 candidate sample changed")

    return {
        "source": "FOR-361",
        "sourceArtifact": rel(FOR361_ARTIFACT),
        "rowId": row.get("rowId"),
        "sample": REJECTED_SAMPLE,
        "currentResidual": residuals.get("currentResidual"),
        "candidateResidual": residuals.get("candidateResidual"),
        "candidateMinusCurrentResidual": residuals.get("candidateMinusCurrentResidual"),
        "worsenedSampleCount": residuals.get("worsenedSampleCount"),
        "for355RuleAppliedSampleCount": residuals.get("for355RuleAppliedSampleCount"),
        "rejectionSample": {
            "referenceSrgbRgba": sample.get("referenceSrgbRgba"),
            "currentKanvasSrgbRgba": sample.get("currentKanvasSrgbRgba"),
            "candidateRgba": sample.get("candidateRgba"),
            "candidateResidual": sample.get("candidateResidual"),
            "candidateWorsensCurrent": True,
        },
    }


def validate_next_constraints(for359: dict[str, Any], for358: dict[str, Any], for345: dict[str, Any]) -> list[dict[str, Any]]:
    guards = for359.get("nonArcGuardConsolidation")
    require(isinstance(guards, dict), "FOR-359 guard consolidation missing")
    require(guards.get("allNonArcGuardsPass") is True, "FOR-359 non-arc guards no longer pass")
    guard_rows = guards.get("guards")
    require(isinstance(guard_rows, list), "FOR-359 guard rows missing")
    by_source = {row.get("sourceId"): row for row in guard_rows if isinstance(row, dict)}
    require({"FOR-345", "FOR-358"}.issubset(set(by_source)), "FOR-359 missing FOR-345/FOR-358 guards")
    require(by_source["FOR-345"].get("candidateResidual") == 0, "FOR-345 guard residual changed")
    require(by_source["FOR-345"].get("candidateWorsenedSampleCount") == 0, "FOR-345 guard worsened count changed")
    require(by_source["FOR-358"].get("candidateMinusCurrentResidual") == 0, "FOR-358 guard residual delta changed")
    require(by_source["FOR-358"].get("candidateWorsenedSampleCount") == 0, "FOR-358 guard worsened count changed")

    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row345 = for345.get("row")
    require(isinstance(row345, dict), "FOR-345 row missing")
    require(row345.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    require(row345.get("referenceCurrentCandidateComparable") is True, "FOR-345 comparability changed")

    row358 = for358.get("row")
    require(isinstance(row358, dict), "FOR-358 row missing")
    require(row358.get("nonArc") is True, "FOR-358 row is no longer non-arc")
    require(row358.get("distinctFromFor345") is True, "FOR-358 distinction from FOR-345 changed")
    require(row358.get("referenceCurrentCandidateComparable") is True, "FOR-358 comparability changed")

    return NEXT_CANDIDATE_SEARCH_CONSTRAINTS


def build_artifact(
    for361: dict[str, Any],
    for360: dict[str, Any],
    for359: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    candidate355 = validate_required_decision(for355, "FOR-355", FOR355_REQUIRED_DECISION)
    validate_required_decision(for358, "FOR-358", FOR358_REQUIRED_DECISION)
    validate_required_decision(for359, "FOR-359", FOR359_REQUIRED_DECISION)
    validate_required_decision(for360, "FOR-360", FOR360_REQUIRED_DECISION)
    validate_required_decision(for361, "FOR-361", FOR361_REQUIRED_DECISION)
    for source, linear in (
        (for355, "FOR-355"),
        (for358, "FOR-358"),
        (for359, "FOR-359"),
        (for360, "FOR-360"),
        (for361, "FOR-361"),
    ):
        validate_implementation_guard(source, linear)

    rejection = validate_for361_rejection(for361)
    next_constraints = validate_next_constraints(for359, for358, for345)

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_CLOSED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-361 rejected the exact FOR-355 candidate on a bounded independent arc scene: "
            "current Kanvas residual stayed 0 while the candidate residual became 37 on "
            f"{REJECTED_SAMPLE}. The candidate is therefore closed as rejected for selection."
        ),
        "inputValidation": {
            "for361Artifact": rel(FOR361_ARTIFACT),
            "for361Decision": for361.get("decision"),
            "for361RequiredDecision": FOR361_REQUIRED_DECISION,
            "for360Artifact": rel(FOR360_ARTIFACT),
            "for360Decision": for360.get("decision"),
            "for360RequiredDecision": FOR360_REQUIRED_DECISION,
            "for359Artifact": rel(FOR359_ARTIFACT),
            "for359Decision": for359.get("decision"),
            "for359RequiredDecision": FOR359_REQUIRED_DECISION,
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
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "formula": candidate355.get("formula"),
            "reusedFromFor355": True,
            "reusedExactly": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "implementedInRenderer": False,
            "rejectedForSelection": True,
            "rejectionSource": "FOR-361",
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "selectionCloseout": {
            "candidateSelectable": False,
            "closedAsRejected": True,
            "rejectionSource": "FOR-361",
            "rejectionDecision": FOR361_REQUIRED_DECISION,
            "implementationPlanAuthorized": False,
            "scoreRaised": False,
            "newCandidateDefinedByFor362": False,
        },
        "rejectionEvidence": rejection,
        "nonArcGuardsPreserved": {
            "source": "FOR-359",
            "for345GuardPassed": True,
            "for358GuardPassed": True,
            "for345GuardSourceArtifact": rel(FOR345_ARTIFACT),
            "for358GuardSourceArtifact": rel(FOR358_ARTIFACT),
        },
        "nextCandidateSearchConstraints": next_constraints,
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
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
    require(data.get("decision") == DECISION_CLOSED, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy changed")
    require(candidate.get("reusedExactly") is True, "candidate is not reused exactly")
    require(candidate.get("newCandidateDefined") is False, "new candidate defined")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")
    require(candidate.get("implementedInRenderer") is False, "candidate implemented")
    require(candidate.get("rejectedForSelection") is True, "candidate is not rejected for selection")
    require(candidate.get("scoreIncreaseAuthorized") is False, "score increase authorized")

    closeout = data.get("selectionCloseout")
    require(isinstance(closeout, dict), "selection closeout missing")
    require(closeout.get("candidateSelectable") is False, "candidate is still selectable")
    require(closeout.get("closedAsRejected") is True, "candidate not closed as rejected")
    require(closeout.get("implementationPlanAuthorized") is False, "implementation plan authorized")
    require(closeout.get("scoreRaised") is False, "score raised")
    require(closeout.get("newCandidateDefinedByFor362") is False, "FOR-362 defined a new candidate")

    rejection = data.get("rejectionEvidence")
    require(isinstance(rejection, dict), "rejection evidence missing")
    require(rejection.get("source") == "FOR-361", "rejection source changed")
    require(rejection.get("sample") == REJECTED_SAMPLE, "rejection sample changed")
    require(rejection.get("currentResidual") == 0, "closeout current residual changed")
    require(rejection.get("candidateResidual") == 37, "closeout candidate residual changed")
    require(rejection.get("candidateMinusCurrentResidual") == 37, "closeout residual delta changed")
    require(rejection.get("worsenedSampleCount") == 1, "closeout worsened sample count changed")

    constraints = data.get("nextCandidateSearchConstraints")
    require(isinstance(constraints, list), "next candidate constraints missing")
    constraint_ids = {item.get("id") for item in constraints if isinstance(item, dict)}
    for required_id in (
        "preserve-for345",
        "preserve-for358",
        "do-not-worsen-for361",
        "require-independent-comparable-arc-scene",
        "refuse-scene-coordinate-selected-cell-full-gm-crop-branches",
    ):
        require(required_id in constraint_ids, f"missing next constraint: {required_id}")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    for key in (
        "rendererBehaviorChanged",
        "newColorPolicyImplemented",
        "candidateSelectedForImplementation",
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
    ):
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    prerequisites = "\n".join(
        f"- {label} decision: `{data['inputValidation'][key]}`"
        for label, key in (
            ("FOR-361", "for361Decision"),
            ("FOR-360", "for360Decision"),
            ("FOR-359", "for359Decision"),
            ("FOR-358", "for358Decision"),
            ("FOR-355", "for355Decision"),
        )
    )
    constraints = "\n".join(
        f"- `{item['id']}`"
        for item in data["nextCandidateSearchConstraints"]
    )
    rejection = data["rejectionEvidence"]
    sample = rejection["rejectionSample"]
    return f"""# FOR-362 F16 Rejected Candidate Closeout

Linear: `FOR-362`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-362 closes the exact FOR-355 candidate after FOR-361 rejected it on a
bounded independent arc scene. This artifact is evidence-only: it does not
define a new candidate, select or implement the candidate, raise score, or
change renderer behavior.

## Result

The candidate is rejected for selection. FOR-361 kept current Kanvas at residual
`{rejection["currentResidual"]}` on the independent arc row, while the candidate
produced residual `{rejection["candidateResidual"]}` and worsened
`{rejection["worsenedSampleCount"]}` sample.

Rejected sample `{rejection["sample"]}`:

| reference | current | candidate | candidate residual | worsens current |
|---|---|---|---:|---|
| {sample["referenceSrgbRgba"]} | {sample["currentKanvasSrgbRgba"]} | {sample["candidateRgba"]} | {sample["candidateResidual"]} | yes |

## Prerequisites

{prerequisites}

## Selection Closeout

- Candidate selectable: `False`
- Closed as rejected: `True`
- Rejection source: `FOR-361`
- New candidate defined by FOR-362: `False`
- Implementation plan authorized: `False`
- Score raised: `False`

## Next Candidate Search Constraints

{constraints}

The next search must preserve FOR-345 and FOR-358, must not worsen FOR-361,
must require at least one independent comparable F16 arc scene, and must refuse
scene, coordinate, selected-cell, and full-GM crop branches.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected or implemented.
- No implementation plan authorization.
- No score increase.
- No threshold, GPU/WGSL, geometry, coverage, fallback, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution, or
  full-GM crop branch.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for362_f16_rejected_candidate_closeout.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-362-f16-rejected-candidate-closeout.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR361_ARTIFACT),
        load_json(FOR360_ARTIFACT),
        load_json(FOR359_ARTIFACT),
        load_json(FOR358_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print(
        f"{DECISION_CLOSED}: candidate={CANDIDATE_POLICY_ID} "
        f"candidateResidual={data['rejectionEvidence']['candidateResidual']}"
    )


if __name__ == "__main__":
    main()
