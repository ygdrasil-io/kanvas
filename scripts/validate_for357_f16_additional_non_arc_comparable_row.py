#!/usr/bin/env python3
"""Validate FOR-357 additional non-arc comparable F16 row evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-357"
SCENE_ID = "f16-additional-non-arc-comparable-row-for357"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-357-f16-additional-non-arc-comparable-row.md"

SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-additional-non-arc-f16-comparable-row-ticket"
SOURCE_FINDING = "global/kanvas/findings/for-356-f16-broader-evidence-remains-partial"

FOR356_SCENE_ID = "f16-generalized-non-scene-arc-delta-broader-evidence-for356"
FOR356_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR356_SCENE_ID / f"{FOR356_SCENE_ID}.json"
)
FOR356_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL"

FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"

FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

FOR344_SCENE_ID = "f16-broader-non-arc-color-policy-for344"
FOR344_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR344_SCENE_ID / f"{FOR344_SCENE_ID}.json"
)
FOR344_REQUIRED_DECISION = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"

FOR338_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338"
FOR338_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR338_SCENE_ID / f"{FOR338_SCENE_ID}.json"
)
FOR338_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_PARTIAL_REQUIRES_MORE_INSTRUMENTATION"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_ACCEPTS = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_ACCEPTS_CANDIDATE"
DECISION_REJECTS = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_REJECTS_CANDIDATE"
DECISION_PARTIAL = "F16_ADDITIONAL_NON_ARC_COMPARABLE_ROW_PARTIAL_INSUFFICIENT_REFERENCE"
ALLOWED_DECISIONS = [DECISION_ACCEPTS, DECISION_REJECTS, DECISION_PARTIAL]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for357_f16_additional_non_arc_comparable_row.py",
    "rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for357_f16_additional_non_arc_comparable_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-357 validation failed: {message}")


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


def validate_for356(for356: dict[str, Any]) -> None:
    require(for356.get("linear") == "FOR-356", "FOR-356 artifact identity changed")
    require(for356.get("decision") == FOR356_REQUIRED_DECISION, "FOR-356 decision changed")
    candidate = for356.get("candidate")
    require(isinstance(candidate, dict), "FOR-356 candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-356 candidate id changed")
    require(candidate.get("family") == CANDIDATE_FAMILY_ID, "FOR-356 family changed")
    require(candidate.get("newCandidateDefined") is False, "FOR-356 defined a new candidate")
    require(candidate.get("selectedForImplementation") is False, "FOR-356 selected candidate")
    summary = for356.get("broaderEvidenceSummary")
    require(isinstance(summary, dict), "FOR-356 summary missing")
    require(summary.get("additionalComparableNonArcRows") == 0, "FOR-356 non-arc inventory changed")
    require(summary.get("readyForImplementationPlan") is False, "FOR-356 authorized implementation planning")


def validate_for355(for355: dict[str, Any]) -> None:
    require(for355.get("linear") == "FOR-355", "FOR-355 artifact identity changed")
    require(for355.get("decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision changed")
    candidate = for355.get("candidate")
    require(isinstance(candidate, dict), "FOR-355 candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-355 candidate id changed")
    require(candidate.get("family") == CANDIDATE_FAMILY_ID, "FOR-355 family changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-355 selected candidate")
    require(candidate.get("rendererBehaviorChanged") is False, "FOR-355 changed renderer behavior")
    require(candidate.get("scoreIncreaseAuthorized") is False, "FOR-355 authorized score increase")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 comparability changed")
    return row


def validate_inventory_artifact(data: dict[str, Any], linear: str, decision: str) -> None:
    require(data.get("linear") == linear, f"{linear} artifact identity changed")
    require(data.get("decision") == decision, f"{linear} decision changed")


def build_for345_exclusion(row: dict[str, Any]) -> dict[str, Any]:
    residuals = row.get("residuals")
    samples = row.get("samples")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(isinstance(samples, list) and samples, "FOR-345 samples missing")
    return {
        "sourceId": "FOR-345",
        "rowId": row["rowId"],
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "nonArc": True,
        "referenceCurrentCandidateComparable": True,
        "eligibleAsAdditionalRow": False,
        "exclusionReason": "FOR-357 requires a non-arc row distinct from FOR-345; this row remains only the existing guard.",
        "sampleCount": len(samples),
        "currentResidual": residuals["currentResidual"],
        "candidateResidualUnderFor355Rule": 0,
        "candidateWorsenedSampleCountUnderFor355Rule": 0,
    }


def build_for344_inventory(for344: dict[str, Any]) -> list[dict[str, Any]]:
    validate_inventory_artifact(for344, "FOR-344", FOR344_REQUIRED_DECISION)
    matrix = for344.get("matrix")
    require(isinstance(matrix, dict), "FOR-344 matrix missing")
    rows = matrix.get("rows")
    require(isinstance(rows, list), "FOR-344 rows missing")
    inventory: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict) or row.get("nonArc") is not True:
            continue
        inventory.append(
            {
                "sourceId": "FOR-344",
                "rowId": row.get("rowId"),
                "sourceArtifact": rel(FOR344_ARTIFACT),
                "family": row.get("family"),
                "sourceKind": row.get("sourceKind"),
                "nonArc": True,
                "referenceCurrentCandidateComparable": row.get("referenceCurrentCandidateComparable") is True,
                "eligibleAsAdditionalRow": False,
                "referenceStatus": row.get("reference", {}).get("status"),
                "currentStatus": row.get("current", {}).get("status"),
                "candidateStatus": row.get("candidate", {}).get("status"),
                "currentResidual": row.get("currentResidual"),
                "candidateResidualUnderFor355Rule": None,
                "candidateWorsenedSampleCountUnderFor355Rule": None,
                "rejectionReason": row.get("classificationReason"),
            }
        )
    require(inventory, "FOR-344 non-arc inventory missing")
    return inventory


def build_for338_inventory(for338: dict[str, Any]) -> dict[str, Any]:
    validate_inventory_artifact(for338, "FOR-338", FOR338_REQUIRED_DECISION)
    text = json.dumps(for338, sort_keys=True)
    require("non-arc-rec2020-f16-blend-target-no-fixture" in text, "FOR-338 missing non-arc target")
    require("No existing artifact supplies current/candidate comparable values for this group." in text, "FOR-338 gap changed")
    return {
        "sourceId": "FOR-338",
        "rowId": "non-arc-rec2020-f16-blend-target-no-fixture",
        "sourceArtifact": rel(FOR338_ARTIFACT),
        "nonArc": True,
        "referenceCurrentCandidateComparable": False,
        "eligibleAsAdditionalRow": False,
        "currentResidual": None,
        "candidateResidualUnderFor355Rule": None,
        "candidateWorsenedSampleCountUnderFor355Rule": None,
        "rejectionReason": "FOR-338 records the desired non-arc target but explicitly lacks current/candidate comparable values.",
    }


def build_insufficient_row() -> dict[str, Any]:
    samples = [
        {
            "name": "additional_non_arc_reference_probe_background",
            "zone": "background",
            "referenceAccepted": False,
            "insufficiencyReason": "no additional isolated non-arc Rec.2020 F16 reference/current/candidate row exists beyond FOR-345",
            "currentKanvasSrgbRgba": None,
            "candidateRgba": None,
            "currentResidual": None,
            "candidateResidual": None,
            "candidateRuleApplied": False,
            "worsenedCurrent": None,
        },
        {
            "name": "additional_non_arc_reference_probe_covered_sample",
            "zone": "non-arc-covered-sample",
            "referenceAccepted": False,
            "insufficiencyReason": "candidate computation would require a comparable reference and current Kanvas sample pair; the repository does not contain one",
            "currentKanvasSrgbRgba": None,
            "candidateRgba": None,
            "currentResidual": None,
            "candidateResidual": None,
            "candidateRuleApplied": False,
            "worsenedCurrent": None,
        },
    ]
    return {
        "rowId": "additional-non-arc-f16-comparable-row-reference-gap-for357",
        "sceneId": SCENE_ID,
        "family": "additional-non-arc-f16-reference-gap",
        "nonArc": True,
        "distinctFromFor345": True,
        "referenceAccepted": False,
        "referenceStatus": "insufficient-reference",
        "referenceInsufficiencyReason": (
            "No additional non-arc row distinct from FOR-345 supplies accepted reference samples, "
            "current Kanvas values, and candidate values computed by the FOR-355 rule."
        ),
        "currentKanvasValuesAvailable": False,
        "candidateValuesComputedByFor355Rule": False,
        "candidateRuleAppliedSampleCount": 0,
        "sampleCount": len(samples),
        "samples": samples,
        "residuals": {
            "currentResidual": None,
            "candidateResidual": None,
            "residualReductionVsCurrent": None,
            "candidateWorsenedSampleCount": None,
        },
        "decision": DECISION_PARTIAL,
    }


def build_artifact(
    for356: dict[str, Any],
    for355: dict[str, Any],
    for345: dict[str, Any],
    for344: dict[str, Any],
    for338: dict[str, Any],
) -> dict[str, Any]:
    validate_for356(for356)
    validate_for355(for355)
    row345 = validate_for345(for345)
    inventory = [build_for345_exclusion(row345), *build_for344_inventory(for344), build_for338_inventory(for338)]
    additional_candidates = [row for row in inventory if row.get("eligibleAsAdditionalRow") is True]
    require(not additional_candidates, "unexpected additional comparable non-arc row became available")
    row = build_insufficient_row()
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for356Artifact": rel(FOR356_ARTIFACT),
            "for356Decision": for356.get("decision"),
            "for356RequiredDecision": FOR356_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
            "for344Artifact": rel(FOR344_ARTIFACT),
            "for344Decision": for344.get("decision"),
            "for344RequiredDecision": FOR344_REQUIRED_DECISION,
            "for338Artifact": rel(FOR338_ARTIFACT),
            "for338Decision": for338.get("decision"),
            "for338RequiredDecision": FOR338_REQUIRED_DECISION,
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "reusedFromFor355": True,
            "reusedFromFor356": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
            "candidateRule": "FOR-355 stroke-center partial raw-alpha-over-white rule, guarded by non-arc identity evidence.",
        },
        "additionalNonArcRow": row,
        "inventory": {
            "searchedForAdditionalComparableNonArcRows": True,
            "for345ReusedAsAdditionalRow": False,
            "selectedCellProofUsed": False,
            "fullGmCropProofUsed": False,
            "candidateComparableRowsFoundDistinctFromFor345": 0,
            "rows": inventory,
        },
        "summary": {
            "referenceAccepted": False,
            "currentResidual": None,
            "candidateResidual": None,
            "candidateWorsenedSampleCount": None,
            "partialReason": (
                "The current artifact set still has no additional comparable non-arc F16 row distinct from FOR-345. "
                "FOR-357 records the insufficiency explicitly and does not convert missing reference data into acceptance."
            ),
            "blocksImplementationPlanning": True,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "candidateSelectedForImplementation": False,
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
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererImplementationAuthorized": False,
            "scoreIncreaseAuthorized": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_PARTIAL, "decision changed")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate id changed")
    require(candidate.get("newCandidateDefined") is False, "new candidate defined")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")
    row = data.get("additionalNonArcRow")
    require(isinstance(row, dict), "additional row missing")
    require(row.get("rowId") != "non-arc-rec2020-f16-src-over-rect", "FOR-345 reused as additional row")
    require(row.get("distinctFromFor345") is True, "additional row is not distinct from FOR-345")
    require(row.get("referenceAccepted") is False, "reference unexpectedly accepted")
    require(row.get("decision") == DECISION_PARTIAL, "row decision changed")
    samples = row.get("samples")
    require(isinstance(samples, list) and len(samples) >= 2, "named insufficiency samples missing")
    for sample in samples:
        require(sample.get("name"), "sample name missing")
        require(sample.get("referenceAccepted") is False, "sample has fake reference acceptance")
        require(sample.get("currentKanvasSrgbRgba") is None, "current value fabricated")
        require(sample.get("candidateRgba") is None, "candidate value fabricated")
    inventory = data.get("inventory")
    require(isinstance(inventory, dict), "inventory missing")
    require(inventory.get("for345ReusedAsAdditionalRow") is False, "FOR-345 reused")
    require(inventory.get("selectedCellProofUsed") is False, "selected-cell proof used")
    require(inventory.get("fullGmCropProofUsed") is False, "full-GM crop proof used")
    require(inventory.get("candidateComparableRowsFoundDistinctFromFor345") == 0, "unexpected comparable row count")
    rows = inventory.get("rows")
    require(isinstance(rows, list) and rows, "inventory rows missing")
    require(any(item.get("sourceId") == "FOR-345" and item.get("eligibleAsAdditionalRow") is False for item in rows), "FOR-345 exclusion missing")
    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("referenceAccepted") is False, "summary accepted missing reference")
    require(summary.get("blocksImplementationPlanning") is True, "implementation planning no longer blocked")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation guard missing")
    for key in (
        "rendererBehaviorChanged",
        "newColorPolicyImplemented",
        "candidateSelectedForImplementation",
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
    inventory_rows = "\n".join(
        "| `{row_id}` | `{source}` | `{status}` | `{reason}` |".format(
            row_id=row.get("rowId"),
            source=row.get("sourceId"),
            status="eligible" if row.get("eligibleAsAdditionalRow") else "rejected",
            reason=row.get("exclusionReason") or row.get("rejectionReason") or row.get("candidateStatus") or "not comparable",
        )
        for row in data["inventory"]["rows"]
    )
    sample_rows = "\n".join(
        "| `{name}` | `{zone}` | `{reference}` | `{current}` | `{candidate}` |".format(
            name=sample["name"],
            zone=sample["zone"],
            reference=sample["referenceAccepted"],
            current=sample["currentKanvasSrgbRgba"],
            candidate=sample["candidateRgba"],
        )
        for sample in data["additionalNonArcRow"]["samples"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-357 F16 Additional Non-Arc Comparable Row

Linear: `FOR-357`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-357 reuses the FOR-355/FOR-356 candidate exactly. It does not define,
select, or implement a new candidate.

## Result

{data["summary"]["partialReason"]}

No additional comparable non-arc row distinct from FOR-345 was found. The
ticket therefore records `PARTIAL_INSUFFICIENT_REFERENCE` instead of accepting
or rejecting the candidate from incomplete data.

## Additional Row Status

- Row id: `{data["additionalNonArcRow"]["rowId"]}`
- Reference accepted: `{data["additionalNonArcRow"]["referenceAccepted"]}`
- Current residual: `{data["additionalNonArcRow"]["residuals"]["currentResidual"]}`
- Candidate residual: `{data["additionalNonArcRow"]["residuals"]["candidateResidual"]}`
- Candidate worsened samples: `{data["additionalNonArcRow"]["residuals"]["candidateWorsenedSampleCount"]}`

| sample | zone | reference accepted | current RGBA | candidate RGBA |
|---|---|---:|---|---|
{sample_rows}

## Inventory

| row | source | status | reason |
|---|---|---|---|
{inventory_rows}

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No renderer fixture/coordinate/scene branch, selected-cell substitution,
  full-GM crop, or threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for357_f16_additional_non_arc_comparable_row.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-357-f16-additional-non-arc-comparable-row.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR356_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR345_ARTIFACT),
        load_json(FOR344_ARTIFACT),
        load_json(FOR338_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-357 validation passed")


if __name__ == "__main__":
    main()
