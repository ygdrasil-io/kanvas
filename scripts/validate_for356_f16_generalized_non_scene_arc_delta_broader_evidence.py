#!/usr/bin/env python3
"""Validate FOR-356 broader evidence for the FOR-355 F16 arc-delta candidate."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-356"
SCENE_ID = "f16-generalized-non-scene-arc-delta-broader-evidence-for356"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-356-f16-generalized-non-scene-arc-delta-broader-evidence.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-broader-evidence-for-generalized-non-scene-arc-delta-candidate-ticket"
)
SOURCE_FINDING = "global/kanvas/findings/for-355-f16-generalized-non-scene-arc-delta-candidate-ready-for-broader-evidence"

FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"

FOR354_SCENE_ID = "f16-nonzero-arc-delta-generalization-constraints-for354"
FOR354_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR354_SCENE_ID / f"{FOR354_SCENE_ID}.json"
)
FOR354_REQUIRED_DECISION = "F16_NONZERO_ARC_DELTA_GENERALIZATION_CONSTRAINTS_READY"

FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

FOR340_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-reference-for340"
FOR340_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR340_SCENE_ID / f"{FOR340_SCENE_ID}.json"
)
FOR340_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_CAPTURED"

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR341_SCENE_ID / f"{FOR341_SCENE_ID}.json"
)
FOR341_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"

FOR342_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342"
FOR342_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR342_SCENE_ID / f"{FOR342_SCENE_ID}.json"
)
FOR342_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE"

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_REJECTED = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_REJECTED"
DECISION_PARTIAL = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL"
DECISION_READY = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_READY_FOR_IMPLEMENTATION_PLAN"
DECISION_INPUT_INVALID = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_PARTIAL, DECISION_READY, DECISION_INPUT_INVALID]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py",
    "rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py",
    "rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py",
    "rtk python3 -m py_compile scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-356 validation failed: {message}")


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


def residual(a: list[int], b: list[int]) -> int:
    require(len(a) == 4 and len(b) == 4, "RGBA residual requires four channels")
    return sum(abs(int(left) - int(right)) for left, right in zip(a, b))


def over_white(raw_rgba: list[int]) -> list[int]:
    require(len(raw_rgba) == 4, "raw RGBA must have four channels")
    r, g, b, a = [int(channel) for channel in raw_rgba]
    require(0 <= a <= 255, "raw alpha out of range")
    return [
        (r * a + 255 * (255 - a) + 127) // 255,
        (g * a + 255 * (255 - a) + 127) // 255,
        (b * a + 255 * (255 - a) + 127) // 255,
        255,
    ]


def should_apply_candidate(sample: dict[str, Any], raw_key: str = "skiaReferenceRawRgba") -> bool:
    raw = sample.get(raw_key)
    return (
        sample.get("zone") == "stroke-center"
        and isinstance(raw, list)
        and len(raw) == 4
        and 0 < int(raw[3]) < 255
    )


def validate_for355(for355: dict[str, Any]) -> None:
    require(for355.get("linear") == "FOR-355", "FOR-355 artifact identity changed")
    require(for355.get("decision") == FOR355_REQUIRED_DECISION, "FOR-355 decision changed")
    candidate = for355.get("candidate")
    require(isinstance(candidate, dict), "FOR-355 candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-355 candidate changed")
    require(candidate.get("family") == CANDIDATE_FAMILY_ID, "FOR-355 family changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-355 selected candidate")


def validate_for354(for354: dict[str, Any]) -> None:
    require(for354.get("linear") == "FOR-354", "FOR-354 artifact identity changed")
    require(for354.get("decision") == FOR354_REQUIRED_DECISION, "FOR-354 decision changed")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row no longer non-arc")
    return row


def validate_decision(data: dict[str, Any], linear: str, decision: str) -> None:
    require(data.get("linear") == linear, f"{linear} artifact identity changed")
    require(data.get("decision") == decision, f"{linear} decision changed")


def evaluate_for345_guard(row: dict[str, Any]) -> dict[str, Any]:
    candidate_residual = 0
    worsened = 0
    sample_rows: list[dict[str, Any]] = []
    for sample in row["samples"]:
        current = sample["currentKanvasSrgbRgba"]
        reference = sample["referenceSrgbRgba"]
        current_residual = int(sample["currentVsReferenceSumAbsDelta"])
        candidate_rgba = current
        candidate_sample_residual = residual(candidate_rgba, reference)
        candidate_residual += candidate_sample_residual
        if candidate_sample_residual > current_residual:
            worsened += 1
        sample_rows.append(
            {
                "sample": sample["name"],
                "zone": sample["zone"],
                "candidateRuleApplied": False,
                "currentResidual": current_residual,
                "candidateResidual": candidate_sample_residual,
                "worsenedCurrent": candidate_sample_residual > current_residual,
            }
        )
    guard = {
        "rowId": row["rowId"],
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "evaluatedFirst": True,
        "requiredResidual": 0,
        "candidateResidual": candidate_residual,
        "requiredWorsenedSampleCount": 0,
        "candidateWorsenedSampleCount": worsened,
        "passed": candidate_residual == 0 and worsened == 0,
        "sampleCount": len(sample_rows),
        "sampleEvaluations": sample_rows,
    }
    require(guard["passed"], "FOR-345 guard must pass before broader evidence")
    return guard


def evaluate_arc_cells(source_id: str, artifact_path: Path, cells: list[dict[str, Any]]) -> dict[str, Any]:
    cell_rows: list[dict[str, Any]] = []
    for cell in cells:
        current_total = 0
        candidate_total = 0
        applied = 0
        samples: list[dict[str, Any]] = []
        for sample in cell["samples"]:
            current = sample["currentFor339ExportRgba"]
            target = sample["skiaReferenceOverWhiteRgba"]
            raw = sample["skiaReferenceRawRgba"]
            applies = should_apply_candidate(sample)
            candidate = over_white(raw) if applies else current
            current_residual = residual(current, target)
            candidate_residual = residual(candidate, target)
            current_total += current_residual
            candidate_total += candidate_residual
            if applies:
                applied += 1
            samples.append(
                {
                    "sample": sample["name"],
                    "zone": sample["zone"],
                    "candidateRuleApplied": applies,
                    "currentResidual": current_residual,
                    "candidateResidual": candidate_residual,
                    "residualReductionVsCurrent": current_residual - candidate_residual,
                }
            )
        cell_rows.append(
            {
                "groupId": cell["groupId"],
                "sampleCount": len(samples),
                "candidateRuleAppliedSampleCount": applied,
                "currentResidual": current_total,
                "candidateResidual": candidate_total,
                "residualReductionVsCurrent": current_total - candidate_total,
                "samples": samples,
            }
        )
    return {
        "sourceId": source_id,
        "sourceArtifact": rel(artifact_path),
        "comparability": "comparable-arc-evidence",
        "cellCount": len(cell_rows),
        "sampleCount": sum(row["sampleCount"] for row in cell_rows),
        "candidateRuleAppliedSampleCount": sum(row["candidateRuleAppliedSampleCount"] for row in cell_rows),
        "currentResidual": sum(row["currentResidual"] for row in cell_rows),
        "candidateResidual": sum(row["candidateResidual"] for row in cell_rows),
        "residualReductionVsCurrent": sum(row["residualReductionVsCurrent"] for row in cell_rows),
        "cells": cell_rows,
    }


def classify_for342(for342: dict[str, Any]) -> dict[str, Any]:
    evidence = for342.get("oldNewEvidence")
    require(isinstance(evidence, list) and evidence, "FOR-342 oldNewEvidence missing")
    return {
        "sourceId": "FOR-342",
        "sourceArtifact": rel(FOR342_ARTIFACT),
        "comparability": "derivative-scoped-route-evidence-not-independent-scene",
        "cellCount": len(evidence),
        "sampleCount": sum(int(row["oldNewEvidenceSampleCount"]) for row in evidence),
        "currentResidual": int(for342["residualTotals"]["oldCurrentOverWhiteResidual"]),
        "candidateResidual": int(for342["residualTotals"]["candidateNewOverWhiteResidual"]),
        "residualReductionVsCurrent": int(for342["residualTotals"]["candidateResidualReductionIfSafeRouteExists"]),
        "reason": (
            "FOR-342 confirms the same adjacent groups and records no safe renderer route; "
            "it is useful corroboration but not a broader independent scene."
        ),
    }


def build_artifact(
    for355: dict[str, Any],
    for354: dict[str, Any],
    for345: dict[str, Any],
    for340: dict[str, Any],
    for341: dict[str, Any],
    for342: dict[str, Any],
) -> dict[str, Any]:
    validate_for355(for355)
    validate_for354(for354)
    row345 = validate_for345(for345)
    validate_decision(for340, "FOR-340", FOR340_REQUIRED_DECISION)
    validate_decision(for341, "FOR-341", FOR341_REQUIRED_DECISION)
    validate_decision(for342, "FOR-342", FOR342_REQUIRED_DECISION)
    non_arc_guard = evaluate_for345_guard(row345)
    arc_rows = [
        evaluate_arc_cells("FOR-340", FOR340_ARTIFACT, for340["targetCells"]),
        evaluate_arc_cells("FOR-341", FOR341_ARTIFACT, for341["targetCells"]),
    ]
    derivative_rows = [classify_for342(for342)]
    independent_arc_scene_count = 0
    non_arc_inventory = [
        {
            "sourceId": "FOR-345",
            "sourceArtifact": rel(FOR345_ARTIFACT),
            "comparability": "comparable-non-arc-guard",
            "candidateResidual": non_arc_guard["candidateResidual"],
            "candidateWorsenedSampleCount": non_arc_guard["candidateWorsenedSampleCount"],
        },
        {
            "sourceId": "additional-non-arc-rows",
            "comparability": "missing",
            "reason": "No additional comparable non-arc row with current/reference/candidate fields is available in the current artifact set.",
        },
    ]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
            "for354Artifact": rel(FOR354_ARTIFACT),
            "for354Decision": for354.get("decision"),
            "for354RequiredDecision": FOR354_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
            "for340Artifact": rel(FOR340_ARTIFACT),
            "for340Decision": for340.get("decision"),
            "for340RequiredDecision": FOR340_REQUIRED_DECISION,
            "for341Artifact": rel(FOR341_ARTIFACT),
            "for341Decision": for341.get("decision"),
            "for341RequiredDecision": FOR341_REQUIRED_DECISION,
            "for342Artifact": rel(FOR342_ARTIFACT),
            "for342Decision": for342.get("decision"),
            "for342RequiredDecision": FOR342_REQUIRED_DECISION,
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "reusedFromFor355": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
            "familyZoneShaped": True,
            "familyZone": "stroke-center",
        },
        "nonArcGuard": non_arc_guard,
        "arcEvidence": arc_rows,
        "derivativeEvidence": derivative_rows,
        "nonArcEvidenceInventory": non_arc_inventory,
        "broaderEvidenceSummary": {
            "comparableArcArtifactCount": len(arc_rows),
            "derivativeEvidenceCount": len(derivative_rows),
            "independentArcSceneCountBeyondCircularArcsStrokeButt": independent_arc_scene_count,
            "currentResidual": sum(row["currentResidual"] for row in arc_rows),
            "candidateResidual": sum(row["candidateResidual"] for row in arc_rows),
            "residualReductionVsCurrent": sum(row["residualReductionVsCurrent"] for row in arc_rows),
            "additionalComparableNonArcRows": 0,
            "readyForImplementationPlan": False,
            "partialReason": (
                "The candidate improves the comparable FOR-340/FOR-341 adjacent arc evidence, "
                "but those artifacts cover the same two CircularArcsStrokeButt groups and FOR-342 is derivative. "
                "No independent arc scene or additional comparable non-arc row is available yet."
            ),
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
    non_arc = data.get("nonArcGuard")
    require(isinstance(non_arc, dict), "non-arc guard missing")
    require(non_arc.get("evaluatedFirst") is True, "FOR-345 was not evaluated first")
    require(non_arc.get("candidateResidual") == 0, "FOR-345 residual changed")
    require(non_arc.get("candidateWorsenedSampleCount") == 0, "FOR-345 worsened count changed")
    arc = data.get("arcEvidence")
    require(isinstance(arc, list) and len(arc) == 2, "arc evidence rows changed")
    for row in arc:
        require(row.get("candidateResidual") == 0, f"{row.get('sourceId')} candidate residual changed")
        require(row.get("residualReductionVsCurrent") > 0, f"{row.get('sourceId')} no longer improves")
    summary = data.get("broaderEvidenceSummary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("currentResidual") == 750, "broader current residual changed")
    require(summary.get("candidateResidual") == 0, "broader candidate residual changed")
    require(summary.get("residualReductionVsCurrent") == 750, "broader reduction changed")
    require(summary.get("readyForImplementationPlan") is False, "implementation plan became authorized")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
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
    summary = data["broaderEvidenceSummary"]
    arc_rows = "\n".join(
        "| `{source}` | {cells} | {samples} | {applied} | {current} | {candidate} | {reduction} |".format(
            source=row["sourceId"],
            cells=row["cellCount"],
            samples=row["sampleCount"],
            applied=row["candidateRuleAppliedSampleCount"],
            current=row["currentResidual"],
            candidate=row["candidateResidual"],
            reduction=row["residualReductionVsCurrent"],
        )
        for row in data["arcEvidence"]
    )
    non_arc_rows = "\n".join(
        f"- `{row['sourceId']}`: `{row['comparability']}`"
        + (f" - {row['reason']}" if "reason" in row else "")
        for row in data["nonArcEvidenceInventory"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-356 F16 Generalized Non-Scene Arc Delta Broader Evidence

Linear: `FOR-356`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-356 reuses the FOR-355 candidate without defining or selecting a new
candidate. It evaluates broader available evidence, then keeps the result
partial because the positive arc evidence still comes from the same adjacent
CircularArcsStrokeButt groups and no additional comparable non-arc row is
available.

## FOR-345 Guard

- Candidate residual: `{data["nonArcGuard"]["candidateResidual"]}`
- Candidate worsened samples: `{data["nonArcGuard"]["candidateWorsenedSampleCount"]}`
- Guard passed: `{data["nonArcGuard"]["passed"]}`

## Comparable Arc Evidence

| source | cells | samples | applied samples | current residual | candidate residual | reduction |
|---|---:|---:|---:|---:|---:|---:|
{arc_rows}

Combined current residual: `{summary["currentResidual"]}`

Combined candidate residual: `{summary["candidateResidual"]}`

Combined reduction: `{summary["residualReductionVsCurrent"]}`

## Derivative Evidence

`FOR-342` is classified as `{data["derivativeEvidence"][0]["comparability"]}`:
{data["derivativeEvidence"][0]["reason"]}

## Non-Arc Evidence Inventory

{non_arc_rows}

## Result

{summary["partialReason"]}

`familyZoneShaped` remains `{data["candidate"]["familyZoneShaped"]}`
(`{data["candidate"]["familyZone"]}`), so this ticket does not authorize an
implementation plan, renderer selection, promotion, or score increase.

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
- Validator: `scripts/validate_for356_f16_generalized_non_scene_arc_delta_broader_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-356-f16-generalized-non-scene-arc-delta-broader-evidence.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR355_ARTIFACT),
        load_json(FOR354_ARTIFACT),
        load_json(FOR345_ARTIFACT),
        load_json(FOR340_ARTIFACT),
        load_json(FOR341_ARTIFACT),
        load_json(FOR342_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-356 validation passed")


if __name__ == "__main__":
    main()
