#!/usr/bin/env python3
"""Validate FOR-359 re-evaluation of the F16 candidate after the FOR-358 guard."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-359"
SCENE_ID = "f16-candidate-after-for358-guard-for359"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-359-f16-candidate-after-for358-guard.md"

FOR358_SCENE_ID = "f16-real-additional-non-arc-row-for358"
FOR358_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR358_SCENE_ID / f"{FOR358_SCENE_ID}.json"
)
FOR357_SCENE_ID = "f16-additional-non-arc-comparable-row-for357"
FOR357_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR357_SCENE_ID / f"{FOR357_SCENE_ID}.json"
)
FOR356_SCENE_ID = "f16-generalized-non-scene-arc-delta-broader-evidence-for356"
FOR356_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR356_SCENE_ID / f"{FOR356_SCENE_ID}.json"
)
FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)

FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"
FOR357_REQUIRED_DECISION = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE"
FOR356_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL"
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-re-evaluate-f16-generalized-candidate-after-for-358-guard"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-358-real-additional-non-arc-f16-row-accepts-candidate",
    "global/kanvas/findings/for-357-additional-non-arc-comparable-row-reference-gap",
]

DECISION_READY = "F16_GENERALIZED_CANDIDATE_READY_FOR_IMPLEMENTATION_PLAN"
DECISION_STILL_REQUIRES_ARC = "F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE"
DECISION_REJECTED = "F16_GENERALIZED_CANDIDATE_REJECTED_AFTER_FOR358"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_STILL_REQUIRES_ARC, DECISION_REJECTED]

NEXT_REQUIRED_EVIDENCE = (
    "independent F16 arc scene outside circular_arcs_stroke_butt with "
    "reference/current/candidate comparable samples evaluated by the exact FOR-355 candidate "
    "while preserving both FOR-345 and FOR-358 non-arc guards"
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for359_f16_candidate_after_for358_guard.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-359 validation failed: {message}")


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


def validate_prerequisites(
    for358: dict[str, Any],
    for357: dict[str, Any],
    for356: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
) -> None:
    require(for358.get("linear") == "FOR-358", "FOR-358 artifact identity changed")
    require(for358.get("decision") == FOR358_REQUIRED_DECISION, "FOR-358 accepting decision missing")
    validate_candidate(for358, "FOR-358")

    require(for357.get("linear") == "FOR-357", "FOR-357 artifact identity changed")
    require(for357.get("decision") == FOR357_REQUIRED_DECISION, "FOR-357 historical gap decision missing")
    validate_candidate(for357, "FOR-357")

    require(for356.get("linear") == "FOR-356", "FOR-356 artifact identity changed")
    require(for356.get("decision") == FOR356_REQUIRED_DECISION, "FOR-356 partial decision missing")
    candidate356 = validate_candidate(for356, "FOR-356")
    require(candidate356.get("family") == CANDIDATE_FAMILY_ID, "FOR-356 candidate family changed")

    require(for355.get("linear") == "FOR-355", "FOR-355 artifact identity changed")
    require(for355.get("decision") == FOR355_REQUIRED_DECISION, "FOR-355 ready decision missing")
    candidate355 = validate_candidate(for355, "FOR-355")
    require(candidate355.get("family") == CANDIDATE_FAMILY_ID, "FOR-355 candidate family changed")

    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row345 = for345.get("row")
    require(isinstance(row345, dict), "FOR-345 row missing")
    require(row345.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    require(row345.get("referenceCurrentCandidateComparable") is True, "FOR-345 comparability changed")


def implementation_guard(data: dict[str, Any], linear: str) -> None:
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


def build_for345_guard(for356: dict[str, Any], for345: dict[str, Any]) -> dict[str, Any]:
    guard = for356.get("nonArcGuard")
    require(isinstance(guard, dict), "FOR-356 FOR-345 non-arc guard missing")
    require(guard.get("sourceArtifact") == rel(FOR345_ARTIFACT), "FOR-356 FOR-345 source artifact changed")
    require(guard.get("evaluatedFirst") is True, "FOR-345 guard was not evaluated first")
    require(guard.get("candidateResidual") == 0, "FOR-345 candidate residual changed")
    require(guard.get("candidateWorsenedSampleCount") == 0, "FOR-345 candidate worsened count changed")
    require(guard.get("passed") is True, "FOR-345 guard no longer passes")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    return {
        "sourceId": "FOR-345",
        "rowId": guard.get("rowId"),
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "role": "original-non-arc-identity-guard",
        "currentResidual": residuals.get("currentResidual"),
        "candidateResidual": guard.get("candidateResidual"),
        "candidateMinusCurrentResidual": guard.get("candidateResidual") - residuals.get("currentResidual"),
        "candidateWorsenedSampleCount": guard.get("candidateWorsenedSampleCount"),
        "passed": True,
    }


def build_for358_guard(for358: dict[str, Any]) -> dict[str, Any]:
    row = for358.get("row")
    require(isinstance(row, dict), "FOR-358 row missing")
    require(row.get("nonArc") is True, "FOR-358 row is no longer non-arc")
    require(row.get("distinctFromFor345") is True, "FOR-358 row is not distinct from FOR-345")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-358 comparability changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-358 residuals missing")
    require(residuals.get("currentResidual") == 3, "FOR-358 current residual changed")
    require(residuals.get("candidateResidual") == 3, "FOR-358 candidate residual changed")
    require(residuals.get("candidateMinusCurrentResidual") == 0, "FOR-358 residual delta changed")
    require(residuals.get("worsenedSampleCount") == 0, "FOR-358 worsened sample count changed")
    require(residuals.get("for355RuleAppliedSampleCount") == 0, "FOR-358 should remain outside FOR-355 stroke-center rule")
    return {
        "sourceId": "FOR-358",
        "rowId": row.get("rowId"),
        "sourceArtifact": rel(FOR358_ARTIFACT),
        "role": "additional-non-arc-comparable-guard",
        "distinctFromFor345": True,
        "currentResidual": residuals.get("currentResidual"),
        "candidateResidual": residuals.get("candidateResidual"),
        "candidateMinusCurrentResidual": residuals.get("candidateMinusCurrentResidual"),
        "candidateWorsenedSampleCount": residuals.get("worsenedSampleCount"),
        "passed": True,
    }


def build_artifact(
    for358: dict[str, Any],
    for357: dict[str, Any],
    for356: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    validate_prerequisites(for358, for357, for356, for355, for345)
    for source, linear in (
        (for358, "FOR-358"),
        (for357, "FOR-357"),
        (for356, "FOR-356"),
        (for355, "FOR-355"),
        (for345, "FOR-345"),
    ):
        implementation_guard(source, linear)

    summary356 = for356.get("broaderEvidenceSummary")
    require(isinstance(summary356, dict), "FOR-356 broader evidence summary missing")
    require(summary356.get("additionalComparableNonArcRows") == 0, "FOR-356 historical non-arc gap changed")
    require(summary356.get("independentArcSceneCountBeyondCircularArcsStrokeButt") == 0, "FOR-356 arc gap changed")
    require(summary356.get("readyForImplementationPlan") is False, "FOR-356 authorized implementation planning")
    require(summary356.get("candidateResidual") == 0, "FOR-356 candidate arc residual changed")
    require(summary356.get("residualReductionVsCurrent") == 750, "FOR-356 arc reduction changed")

    non_arc_guards = [build_for345_guard(for356, for345), build_for358_guard(for358)]
    for358_resolves_non_arc_gap = all(row["passed"] for row in non_arc_guards)
    independent_arc_scene_available = summary356.get("independentArcSceneCountBeyondCircularArcsStrokeButt") > 0
    decision = DECISION_READY if for358_resolves_non_arc_gap and independent_arc_scene_available else DECISION_STILL_REQUIRES_ARC
    require(decision == DECISION_STILL_REQUIRES_ARC, "FOR-359 decision unexpectedly became ready or rejected")

    formula = for355.get("candidate", {}).get("formula")
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "inputValidation": {
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for357Artifact": rel(FOR357_ARTIFACT),
            "for357Decision": for357.get("decision"),
            "for357RequiredDecision": FOR357_REQUIRED_DECISION,
            "for356Artifact": rel(FOR356_ARTIFACT),
            "for356Decision": for356.get("decision"),
            "for356RequiredDecision": FOR356_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-358 fully resolves the FOR-356 additional non-arc comparable-row gap, "
            "but it does not add an independent arc scene. The generalized candidate therefore "
            "remains evidence-only and still requires independent arc-scene proof before an "
            "implementation plan can be proposed."
        ),
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "formula": formula,
            "reusedFromFor355": True,
            "reusedExactly": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "for356BlockageResolution": {
            "for358LiftsBlockage": "partially",
            "nonArcComparableRowGapResolved": True,
            "resolvedBy": "FOR-358",
            "historicalGapSource": "FOR-357",
            "historicalGapDecision": for357.get("decision"),
            "independentArcSceneGapResolved": False,
            "remainingBlocker": NEXT_REQUIRED_EVIDENCE,
        },
        "nonArcGuardConsolidation": {
            "guardCount": len(non_arc_guards),
            "allNonArcGuardsPass": for358_resolves_non_arc_gap,
            "guards": non_arc_guards,
        },
        "arcEvidenceCarriedForward": {
            "source": "FOR-356",
            "comparableArcArtifactCount": summary356.get("comparableArcArtifactCount"),
            "derivativeEvidenceCount": summary356.get("derivativeEvidenceCount"),
            "independentArcSceneCountBeyondCircularArcsStrokeButt": summary356.get(
                "independentArcSceneCountBeyondCircularArcsStrokeButt"
            ),
            "currentResidual": summary356.get("currentResidual"),
            "candidateResidual": summary356.get("candidateResidual"),
            "residualReductionVsCurrent": summary356.get("residualReductionVsCurrent"),
            "limitation": (
                "Existing positive arc evidence remains concentrated in the same "
                "CircularArcsStrokeButt adjacent groups; FOR-342 remains derivative evidence."
            ),
        },
        "remainingLimitations": [
            {
                "id": "independent-arc-scene-missing",
                "status": "open",
                "requires": NEXT_REQUIRED_EVIDENCE,
            }
        ],
        "nextRequiredEvidence": NEXT_REQUIRED_EVIDENCE,
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
    require(data.get("decision") == DECISION_STILL_REQUIRES_ARC, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy changed")
    require(candidate.get("reusedExactly") is True, "candidate is not marked as reused exactly")
    require(candidate.get("newCandidateDefined") is False, "new candidate defined")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")

    resolution = data.get("for356BlockageResolution")
    require(isinstance(resolution, dict), "FOR-356 blockage resolution missing")
    require(resolution.get("for358LiftsBlockage") == "partially", "FOR-358 blockage resolution changed")
    require(resolution.get("nonArcComparableRowGapResolved") is True, "FOR-358 non-arc resolution missing")
    require(resolution.get("historicalGapDecision") == FOR357_REQUIRED_DECISION, "FOR-357 historical gap missing")
    require(resolution.get("independentArcSceneGapResolved") is False, "arc scene gap unexpectedly resolved")
    require(resolution.get("remainingBlocker") == NEXT_REQUIRED_EVIDENCE, "remaining blocker changed")

    guards = data.get("nonArcGuardConsolidation")
    require(isinstance(guards, dict), "non-arc guard consolidation missing")
    require(guards.get("guardCount") == 2, "expected FOR-345 and FOR-358 guards")
    require(guards.get("allNonArcGuardsPass") is True, "non-arc guards do not pass")
    guard_rows = guards.get("guards")
    require(isinstance(guard_rows, list) and len(guard_rows) == 2, "guard rows changed")
    by_source = {row.get("sourceId"): row for row in guard_rows if isinstance(row, dict)}
    require(set(by_source) == {"FOR-345", "FOR-358"}, "guard sources changed")
    require(by_source["FOR-345"].get("currentResidual") == 0, "FOR-345 current residual changed")
    require(by_source["FOR-345"].get("candidateResidual") == 0, "FOR-345 residual changed")
    require(by_source["FOR-345"].get("candidateMinusCurrentResidual") == 0, "FOR-345 residual delta changed")
    require(by_source["FOR-345"].get("candidateWorsenedSampleCount") == 0, "FOR-345 worsened count changed")
    require(by_source["FOR-358"].get("currentResidual") == 3, "FOR-358 current residual changed")
    require(by_source["FOR-358"].get("candidateResidual") == 3, "FOR-358 candidate residual changed")
    require(by_source["FOR-358"].get("candidateMinusCurrentResidual") == 0, "FOR-358 residual delta changed")
    require(by_source["FOR-358"].get("candidateWorsenedSampleCount") == 0, "FOR-358 worsened count changed")

    arc = data.get("arcEvidenceCarriedForward")
    require(isinstance(arc, dict), "arc evidence missing")
    require(arc.get("independentArcSceneCountBeyondCircularArcsStrokeButt") == 0, "independent arc count changed")
    require(arc.get("candidateResidual") == 0, "arc candidate residual changed")
    require(arc.get("residualReductionVsCurrent") == 750, "arc reduction changed")
    require(data.get("nextRequiredEvidence") == NEXT_REQUIRED_EVIDENCE, "next evidence changed")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
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
    guards = "\n".join(
        "| `{source}` | `{row}` | `{role}` | {current} | {candidate} | {delta} | {worsened} | {passed} |".format(
            source=row["sourceId"],
            row=row["rowId"],
            role=row["role"],
            current=row.get("currentResidual", "n/a"),
            candidate=row.get("candidateResidual"),
            delta=row.get("candidateMinusCurrentResidual", "n/a"),
            worsened=row.get("candidateWorsenedSampleCount"),
            passed="yes" if row["passed"] else "no",
        )
        for row in data["nonArcGuardConsolidation"]["guards"]
    )
    arc = data["arcEvidenceCarriedForward"]
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-359 F16 Candidate After FOR-358 Guard

Linear: `FOR-359`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-359 re-evaluates the exact FOR-355 candidate after FOR-358 added a real
additional non-arc comparable F16 row. This artifact is evidence-only: it does
not define a new candidate, select a renderer policy, raise score, or change
renderer behavior.

## Result

FOR-358 lifts the FOR-356 blockage only partially. It resolves the missing
additional non-arc comparable row recorded by FOR-357, but the FOR-356 arc
limitation remains open because no independent arc scene beyond
`circular_arcs_stroke_butt` has been added.

Next required proof: {data["nextRequiredEvidence"]}.

## Prerequisites

- FOR-358 decision: `{data["inputValidation"]["for358Decision"]}`
- FOR-357 decision: `{data["inputValidation"]["for357Decision"]}`
- FOR-356 decision: `{data["inputValidation"]["for356Decision"]}`
- FOR-355 decision: `{data["inputValidation"]["for355Decision"]}`
- Source memory: `{data["sourceMemory"]}`

## Consolidated Non-Arc Guards

| source | row | role | current residual | candidate residual | candidate-current delta | worsened samples | passed |
|---|---|---|---:|---:|---:|---:|---|
{guards}

## Arc Evidence Carried Forward

| metric | value |
|---|---:|
| comparable arc artifacts | {arc["comparableArcArtifactCount"]} |
| derivative evidence rows | {arc["derivativeEvidenceCount"]} |
| independent arc scenes beyond `circular_arcs_stroke_butt` | {arc["independentArcSceneCountBeyondCircularArcsStrokeButt"]} |
| current residual | {arc["currentResidual"]} |
| candidate residual | {arc["candidateResidual"]} |
| residual reduction | {arc["residualReductionVsCurrent"]} |

{arc["limitation"]}

## Decision Boundary

- FOR-358 non-arc gap resolved: `True`
- Independent arc-scene gap resolved: `False`
- FOR-358 blockage lift: `partially`
- Implementation plan authorized: `False`

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No implementation plan authorization.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution,
  full-GM crop, or threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for359_f16_candidate_after_for358_guard.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-359-f16-candidate-after-for358-guard.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR358_ARTIFACT),
        load_json(FOR357_ARTIFACT),
        load_json(FOR356_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-359 validation passed")


if __name__ == "__main__":
    main()
