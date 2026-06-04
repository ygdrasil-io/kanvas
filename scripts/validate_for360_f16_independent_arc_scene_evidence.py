#!/usr/bin/env python3
"""Validate FOR-360 independent F16 arc-scene evidence for the F16 candidate."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-360"
SCENE_ID = "f16-independent-arc-scene-evidence-for360"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-360-f16-independent-arc-scene-evidence.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-independent-arc-scene-evidence-for-f16-candidate"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-359-f16-candidate-still-requires-independent-arc-scene",
    "global/kanvas/findings/for-358-real-additional-non-arc-f16-row-accepts-candidate",
]

FOR359_SCENE_ID = "f16-candidate-after-for358-guard-for359"
FOR359_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR359_SCENE_ID / f"{FOR359_SCENE_ID}.json"
)
FOR359_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-359-f16-candidate-after-for358-guard.md"
FOR359_REQUIRED_DECISION = "F16_GENERALIZED_CANDIDATE_STILL_REQUIRES_INDEPENDENT_ARC_SCENE"

FOR358_SCENE_ID = "f16-real-additional-non-arc-row-for358"
FOR358_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR358_SCENE_ID / f"{FOR358_SCENE_ID}.json"
)
FOR358_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-358-f16-real-additional-non-arc-row.md"
FOR358_REQUIRED_DECISION = "F16_REAL_ADDITIONAL_NON_ARC_ROW_ACCEPTS_CANDIDATE"

FOR355_SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
FOR355_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR355_SCENE_ID / f"{FOR355_SCENE_ID}.json"
)
FOR355_REPORT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-355-f16-generalized-non-scene-arc-delta-candidate.md"
)
FOR355_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"

FOR356_SCENE_ID = "f16-generalized-non-scene-arc-delta-broader-evidence-for356"
FOR356_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR356_SCENE_ID / f"{FOR356_SCENE_ID}.json"
)
FOR356_REQUIRED_DECISION = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_BROADER_EVIDENCE_PARTIAL"

FOR340_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-reference-for340"
FOR340_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR340_SCENE_ID / f"{FOR340_SCENE_ID}.json"
)

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR341_SCENE_ID / f"{FOR341_SCENE_ID}.json"
)

FOR342_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342"
FOR342_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR342_SCENE_ID / f"{FOR342_SCENE_ID}.json"
)

FOR337_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337"
FOR337_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR337_SCENE_ID / f"{FOR337_SCENE_ID}.json"
)

FOR338_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338"
FOR338_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR338_SCENE_ID / f"{FOR338_SCENE_ID}.json"
)

FOR318_SCENE_ID = "path-aa-arc-stroke-hairline-scout-for318"
FOR318_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR318_SCENE_ID / f"{FOR318_SCENE_ID}.json"
)

CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"

DECISION_SUPPORTS = "F16_INDEPENDENT_ARC_SCENE_SUPPORTS_CANDIDATE"
DECISION_REJECTS = "F16_INDEPENDENT_ARC_SCENE_REJECTS_CANDIDATE"
DECISION_PARTIAL = "F16_INDEPENDENT_ARC_SCENE_EVIDENCE_PARTIAL"
ALLOWED_DECISIONS = [DECISION_SUPPORTS, DECISION_REJECTS, DECISION_PARTIAL]

PARTIAL_REASON = (
    "No inspected repository artifact supplies an independent arc scene outside the FOR-340/FOR-341 "
    "CircularArcsStrokeButt adjacent groups with accepted reference samples, current Kanvas F16 "
    "samples, and candidate RGBA values for the exact FOR-355 candidate. FOR-360 therefore records "
    "PARTIAL instead of accepting or rejecting the candidate from incomplete data."
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for360_f16_independent_arc_scene_evidence.py",
    "rtk python3 scripts/validate_for359_f16_candidate_after_for358_guard.py",
    "rtk python3 scripts/validate_for358_f16_real_additional_non_arc_row.py",
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 -m py_compile scripts/validate_for360_f16_independent_arc_scene_evidence.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-360 validation failed: {message}")


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


def read_required_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing report file: {rel(path)}")
    return path.read_text(encoding="utf-8")


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


def validate_report_decision(path: Path, decision: str) -> None:
    text = read_required_text(path)
    require(decision in text, f"{rel(path)} does not contain required decision {decision}")
    require(CANDIDATE_POLICY_ID in text, f"{rel(path)} does not contain the candidate policy id")


def validate_prerequisite(data: dict[str, Any], linear: str, required_decision: str, report: Path) -> dict[str, Any]:
    require(data.get("linear") == linear, f"{linear} identity changed")
    require(data.get("decision") == required_decision, f"{linear} required decision missing")
    validate_report_decision(report, required_decision)
    return validate_candidate(data, linear)


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


def row(
    *,
    source_id: str,
    path: Path,
    scene_id: str,
    source_kind: str,
    independent_from_for340_for341: bool,
    comparable_reference_current_candidate: bool,
    selected_cell: bool,
    full_gm_crop: bool,
    eligible: bool,
    exact_reason: str,
    row_ids: list[str] | None = None,
    current_residual: int | None = None,
    candidate_residual: int | None = None,
    candidate_worsened_sample_count: int | None = None,
) -> dict[str, Any]:
    require(path.is_file(), f"missing inspected path: {rel(path)}")
    return {
        "sourceId": source_id,
        "path": rel(path),
        "sceneId": scene_id,
        "sourceKind": source_kind,
        "rowIds": row_ids or [],
        "exists": True,
        "arcScene": True,
        "independentFromFor340For341AdjacentGroups": independent_from_for340_for341,
        "referenceCurrentCandidateComparable": comparable_reference_current_candidate,
        "selectedCellProof": selected_cell,
        "fullGmCropProof": full_gm_crop,
        "eligibleAsIndependentArcSceneEvidence": eligible,
        "currentResidual": current_residual,
        "candidateResidual": candidate_residual,
        "candidateWorsenedSampleCount": candidate_worsened_sample_count,
        "candidateEvaluationStatus": "not-evaluated-as-independent-proof" if not eligible else "eligible",
        "exactReason": exact_reason,
    }


def build_inspected_paths(
    for356: dict[str, Any],
    for340: dict[str, Any],
    for341: dict[str, Any],
    for342: dict[str, Any],
    for337: dict[str, Any],
    for338: dict[str, Any],
    for318: dict[str, Any],
) -> list[dict[str, Any]]:
    require(for356.get("linear") == "FOR-356", "FOR-356 identity changed")
    require(for356.get("decision") == FOR356_REQUIRED_DECISION, "FOR-356 decision changed")
    summary = for356.get("broaderEvidenceSummary")
    require(isinstance(summary, dict), "FOR-356 broader evidence summary missing")
    require(
        summary.get("independentArcSceneCountBeyondCircularArcsStrokeButt") == 0,
        "FOR-356 independent arc count changed",
    )

    require(for340.get("linear") == "FOR-340", "FOR-340 identity changed")
    require(for341.get("linear") == "FOR-341", "FOR-341 identity changed")
    require(for342.get("linear") == "FOR-342", "FOR-342 identity changed")
    require(for337.get("linear") == "FOR-337", "FOR-337 identity changed")
    require(for338.get("linear") == "FOR-338", "FOR-338 identity changed")
    require(for318.get("linear") == "FOR-318", "FOR-318 identity changed")

    comparable_arc_evidence = for356.get("arcEvidence")
    require(isinstance(comparable_arc_evidence, list) and comparable_arc_evidence, "FOR-356 arc evidence missing")

    for341_totals = for341.get("residualTotals")
    require(isinstance(for341_totals, dict), "FOR-341 residual totals missing")
    target_cells = for341.get("targetCells")
    require(isinstance(target_cells, list) and len(target_cells) == 2, "FOR-341 target cells changed")
    target_group_ids = [str(cell.get("groupId")) for cell in target_cells]
    require(
        target_group_ids == [
            "adjacent_arc_stroke_start0_sweep45_target",
            "adjacent_arc_stroke_start0_sweep130_target",
        ],
        "FOR-341 target groups changed",
    )

    scout_rows = for318.get("candidateRows")
    require(isinstance(scout_rows, list) and scout_rows, "FOR-318 candidate rows missing")
    independent_scout_ids = [
        str(item.get("id"))
        for item in scout_rows
        if isinstance(item, dict)
        and item.get("id") in {
            "addarc-webgpu",
            "circular-arcs-hairline-webgpu",
            "circular-arcs-stroke-round-webgpu",
            "circular-arcs-stroke-square-webgpu",
            "crbug1472747-webgpu",
        }
    ]
    require(len(independent_scout_ids) >= 5, "FOR-318 independent arc scout inventory changed")

    rows = [
        row(
            source_id="FOR-340",
            path=FOR340_ARTIFACT,
            scene_id=FOR340_SCENE_ID,
            source_kind="isolated-skia-reference-for-forbidden-adjacent-circular-arcs-stroke-butt-groups",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=True,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=target_group_ids,
            exact_reason=(
                "FOR-340 is the accepted Skia reference for the same two adjacent CircularArcsStrokeButt "
                "groups already used by FOR-341/FOR-355, so FOR-360 must not count it as independent proof."
            ),
        ),
        row(
            source_id="FOR-341",
            path=FOR341_ARTIFACT,
            scene_id=FOR341_SCENE_ID,
            source_kind="forbidden-adjacent-circular-arcs-stroke-butt-current-reference-candidate-evidence",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=True,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=target_group_ids,
            current_residual=for341_totals.get("currentVsOverWhiteResidual"),
            candidate_residual=for341_totals.get("candidateVsOverWhiteResidual"),
            candidate_worsened_sample_count=0,
            exact_reason=(
                "FOR-341 has the comparable current/reference/candidate arc rows, but they are exactly the "
                "two adjacent CircularArcsStrokeButt groups prohibited as new independent FOR-360 evidence."
            ),
        ),
        row(
            source_id="FOR-342",
            path=FOR342_ARTIFACT,
            scene_id=FOR342_SCENE_ID,
            source_kind="derivative-scoped-route-record-for-same-adjacent-groups",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=True,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=target_group_ids,
            current_residual=375,
            candidate_residual=0,
            candidate_worsened_sample_count=0,
            exact_reason=(
                "FOR-342 is derivative evidence for the same adjacent CircularArcsStrokeButt groups and records "
                "no independent scene beyond FOR-340/FOR-341."
            ),
        ),
        row(
            source_id="FOR-337",
            path=FOR337_ARTIFACT,
            scene_id=FOR337_SCENE_ID,
            source_kind="selected-cell-and-target-inventory",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=False,
            selected_cell=True,
            full_gm_crop=False,
            eligible=False,
            row_ids=["selected_cell_instrumented_f16_rec2020", "adjacent_comparable_arc_stroke_cells"],
            exact_reason=(
                "FOR-337 contains selected-cell evidence and only identifies adjacent targets without a comparable "
                "current/reference/candidate table. FOR-360 forbids selected-cell substitution."
            ),
        ),
        row(
            source_id="FOR-338",
            path=FOR338_ARTIFACT,
            scene_id=FOR338_SCENE_ID,
            source_kind="future-comparable-sample-target-inventory",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=False,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=["adjacent_arc_stroke_start0_sweep45_target", "adjacent_arc_stroke_start0_sweep130_target"],
            exact_reason=(
                "FOR-338 records adjacent CircularArcsStrokeButt instrumentation targets and explicitly lacks "
                "captured current/candidate comparable values; it is not an independent arc scene."
            ),
        ),
        row(
            source_id="FOR-318",
            path=FOR318_ARTIFACT,
            scene_id=FOR318_SCENE_ID,
            source_kind="path-aa-independent-arc-scout-inventory",
            independent_from_for340_for341=True,
            comparable_reference_current_candidate=False,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=independent_scout_ids,
            exact_reason=(
                "FOR-318 identifies independent arc GM rows such as AddArc and Crbug1472747, but they are "
                "scout/broad-GM expected-unsupported entries without accepted Rec.2020 F16 current/reference/"
                "candidate sample tables for the FOR-355 candidate."
            ),
        ),
        row(
            source_id="FOR-356",
            path=FOR356_ARTIFACT,
            scene_id=FOR356_SCENE_ID,
            source_kind="broader-evidence-summary",
            independent_from_for340_for341=False,
            comparable_reference_current_candidate=False,
            selected_cell=False,
            full_gm_crop=False,
            eligible=False,
            row_ids=["broaderEvidenceSummary"],
            exact_reason=(
                "FOR-356 already summarizes that the candidate has zero independent arc scenes beyond "
                "CircularArcsStrokeButt; FOR-360 treats that as prior evidence, not as a new scene."
            ),
        ),
    ]
    require(not [item for item in rows if item["eligibleAsIndependentArcSceneEvidence"]], "unexpected eligible row")
    return rows


def build_artifact(
    for359: dict[str, Any],
    for358: dict[str, Any],
    for355: dict[str, Any],
    for356: dict[str, Any],
    for340: dict[str, Any],
    for341: dict[str, Any],
    for342: dict[str, Any],
    for337: dict[str, Any],
    for338: dict[str, Any],
    for318: dict[str, Any],
) -> dict[str, Any]:
    candidate359 = validate_prerequisite(for359, "FOR-359", FOR359_REQUIRED_DECISION, FOR359_REPORT)
    validate_prerequisite(for358, "FOR-358", FOR358_REQUIRED_DECISION, FOR358_REPORT)
    candidate355 = validate_prerequisite(for355, "FOR-355", FOR355_REQUIRED_DECISION, FOR355_REPORT)
    for source, linear in ((for359, "FOR-359"), (for358, "FOR-358"), (for355, "FOR-355"), (for356, "FOR-356")):
        implementation_guard(source, linear)

    inspected_paths = build_inspected_paths(for356, for340, for341, for342, for337, for338, for318)
    independent_comparable = [
        item
        for item in inspected_paths
        if item["independentFromFor340For341AdjacentGroups"]
        and item["referenceCurrentCandidateComparable"]
        and not item["selectedCellProof"]
        and not item["fullGmCropProof"]
    ]
    require(not independent_comparable, "unexpected independent comparable arc scene became available")

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "inputValidation": {
            "for359Artifact": rel(FOR359_ARTIFACT),
            "for359Report": rel(FOR359_REPORT),
            "for359Decision": for359.get("decision"),
            "for359RequiredDecision": FOR359_REQUIRED_DECISION,
            "for358Artifact": rel(FOR358_ARTIFACT),
            "for358Report": rel(FOR358_REPORT),
            "for358Decision": for358.get("decision"),
            "for358RequiredDecision": FOR358_REQUIRED_DECISION,
            "for355Artifact": rel(FOR355_ARTIFACT),
            "for355Report": rel(FOR355_REPORT),
            "for355Decision": for355.get("decision"),
            "for355RequiredDecision": FOR355_REQUIRED_DECISION,
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": PARTIAL_REASON,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "formula": candidate355.get("formula") or candidate359.get("formula"),
            "reusedFromFor355": True,
            "reusedExactly": True,
            "newCandidateDefined": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "independentArcSceneSearch": {
            "searchedForIndependentArcScene": True,
            "capturedIndependentComparableScene": False,
            "comparableIndependentArcSceneAvailable": False,
            "selectedCellProofUsed": False,
            "fullGmCropProofUsed": False,
            "for340For341AdjacentGroupsReusedAsNewProof": False,
            "inspectedPathCount": len(inspected_paths),
            "inspectedPaths": inspected_paths,
        },
        "candidateEvaluation": {
            "evaluatedOnIndependentArcScene": False,
            "status": "not-evaluated-no-independent-comparable-arc-scene",
            "sampleCount": 0,
            "samples": [],
            "totals": {
                "currentResidual": None,
                "candidateResidual": None,
                "residualDeltaCandidateMinusCurrent": None,
                "candidateWorsenedSampleCount": None,
                "for355RuleAppliedSampleCount": None,
            },
            "exactReason": PARTIAL_REASON,
        },
        "summary": {
            "independentArcScenesComparableFound": 0,
            "decisionIsPartial": True,
            "partialReason": PARTIAL_REASON,
            "blocksImplementationPlanning": True,
        },
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
    require(data.get("decision") == DECISION_PARTIAL, "decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy changed")
    require(candidate.get("reusedExactly") is True, "candidate not reused exactly")
    require(candidate.get("newCandidateDefined") is False, "new candidate defined")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")

    search = data.get("independentArcSceneSearch")
    require(isinstance(search, dict), "search block missing")
    require(search.get("searchedForIndependentArcScene") is True, "search not recorded")
    require(search.get("capturedIndependentComparableScene") is False, "unexpected capture recorded")
    require(search.get("comparableIndependentArcSceneAvailable") is False, "unexpected scene availability")
    require(search.get("selectedCellProofUsed") is False, "selected-cell proof used")
    require(search.get("fullGmCropProofUsed") is False, "full-GM crop proof used")
    require(search.get("for340For341AdjacentGroupsReusedAsNewProof") is False, "forbidden adjacent groups reused")
    inspected = search.get("inspectedPaths")
    require(isinstance(inspected, list) and len(inspected) >= 6, "inspected paths missing")
    require(search.get("inspectedPathCount") == len(inspected), "inspected path count changed")
    require(not [item for item in inspected if item.get("eligibleAsIndependentArcSceneEvidence") is True], "eligible path present")
    require(any(item.get("sourceId") == "FOR-318" for item in inspected), "FOR-318 independent scout path missing")
    require(any(item.get("sourceId") == "FOR-341" for item in inspected), "FOR-341 forbidden adjacent path missing")

    evaluation = data.get("candidateEvaluation")
    require(isinstance(evaluation, dict), "candidate evaluation missing")
    require(evaluation.get("evaluatedOnIndependentArcScene") is False, "candidate unexpectedly evaluated")
    require(evaluation.get("sampleCount") == 0, "samples fabricated")
    require(evaluation.get("samples") == [], "sample values fabricated")
    totals = evaluation.get("totals")
    require(isinstance(totals, dict), "totals missing")
    for key in (
        "currentResidual",
        "candidateResidual",
        "residualDeltaCandidateMinusCurrent",
        "candidateWorsenedSampleCount",
        "for355RuleAppliedSampleCount",
    ):
        require(totals.get(key) is None, f"{key} should remain null without an independent scene")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("independentArcScenesComparableFound") == 0, "unexpected independent scene count")
    require(summary.get("decisionIsPartial") is True, "partial summary changed")
    require(summary.get("blocksImplementationPlanning") is True, "implementation planning no longer blocked")

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
    inspected_rows = "\n".join(
        "| `{source}` | `{path}` | `{independent}` | `{comparable}` | `{eligible}` | `{reason}` |".format(
            source=item["sourceId"],
            path=item["path"],
            independent=item["independentFromFor340For341AdjacentGroups"],
            comparable=item["referenceCurrentCandidateComparable"],
            eligible=item["eligibleAsIndependentArcSceneEvidence"],
            reason=item["exactReason"],
        )
        for item in data["independentArcSceneSearch"]["inspectedPaths"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-360 F16 Independent Arc Scene Evidence

Linear: `FOR-360`

Decision: `{data["decision"]}`

Candidate: `{data["candidate"]["policyId"]}`

FOR-360 searched for an independent comparable arc scene beyond the two
adjacent `CircularArcsStrokeButt` groups used by FOR-340/FOR-341, then kept
the exact FOR-355 candidate unchanged. This artifact is evidence-only: it does
not define a new candidate, select a renderer policy, raise score, or change
renderer behavior.

## Result

{data["summary"]["partialReason"]}

The candidate was not evaluated on a new independent arc scene because no
accepted comparable scene exists in the inspected repository artifacts. No
sample RGBA values or residual totals are fabricated for the missing scene.

## Prerequisites

- FOR-359 decision: `{data["inputValidation"]["for359Decision"]}`
- FOR-358 decision: `{data["inputValidation"]["for358Decision"]}`
- FOR-355 decision: `{data["inputValidation"]["for355Decision"]}`
- Source memory: `{data["sourceMemory"]}`

## Inspected Paths

| source | path | independent from FOR-340/FOR-341 | comparable reference/current/candidate | eligible | exact reason |
|---|---|---:|---:|---:|---|
{inspected_rows}

## Candidate Evaluation

| metric | value |
|---|---:|
| evaluated on independent arc scene | `{data["candidateEvaluation"]["evaluatedOnIndependentArcScene"]}` |
| sample count | `{data["candidateEvaluation"]["sampleCount"]}` |
| current residual | `{data["candidateEvaluation"]["totals"]["currentResidual"]}` |
| candidate residual | `{data["candidateEvaluation"]["totals"]["candidateResidual"]}` |
| candidate-current residual delta | `{data["candidateEvaluation"]["totals"]["residualDeltaCandidateMinusCurrent"]}` |
| worsened samples | `{data["candidateEvaluation"]["totals"]["candidateWorsenedSampleCount"]}` |

## Decision Boundary

- Independent comparable arc scenes found: `0`
- Selected-cell proof used: `False`
- Full-GM crop proof used: `False`
- FOR-340/FOR-341 adjacent groups reused as new proof: `False`
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
- Validator: `scripts/validate_for360_f16_independent_arc_scene_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-360-f16-independent-arc-scene-evidence.md`

## Validation

{validation}
"""


def main() -> None:
    data = build_artifact(
        load_json(FOR359_ARTIFACT),
        load_json(FOR358_ARTIFACT),
        load_json(FOR355_ARTIFACT),
        load_json(FOR356_ARTIFACT),
        load_json(FOR340_ARTIFACT),
        load_json(FOR341_ARTIFACT),
        load_json(FOR342_ARTIFACT),
        load_json(FOR337_ARTIFACT),
        load_json(FOR338_ARTIFACT),
        load_json(FOR318_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-360 validation passed")


if __name__ == "__main__":
    main()
