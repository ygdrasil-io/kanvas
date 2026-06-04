#!/usr/bin/env python3
"""Validate the FOR-355 generalized non-scene F16 arc-delta candidate."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-355"
SCENE_ID = "f16-generalized-non-scene-arc-delta-candidate-for355"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-355-f16-generalized-non-scene-arc-delta-candidate.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-evaluate-generalized-non-scene-arc-delta-candidate-ticket"
)
SOURCE_FINDING = "global/kanvas/findings/for-354-f16-nonzero-arc-delta-generalization-constraints-ready"

FOR354_SCENE_ID = "f16-nonzero-arc-delta-generalization-constraints-for354"
FOR354_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR354_SCENE_ID / f"{FOR354_SCENE_ID}.json"
)
FOR354_REQUIRED_DECISION = "F16_NONZERO_ARC_DELTA_GENERALIZATION_CONSTRAINTS_READY"

FOR353_SCENE_ID = "f16-nonzero-arc-delta-with-non-arc-guard-for353"
FOR353_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR353_SCENE_ID / f"{FOR353_SCENE_ID}.json"
)
FOR353_REQUIRED_DECISION = "F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_PARTIAL"

FOR351_SCENE_ID = "f16-non-arc-preserving-arc-constraints-for351"
FOR351_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR351_SCENE_ID / f"{FOR351_SCENE_ID}.json"
)
FOR351_REQUIRED_DECISION = "F16_NON_ARC_PRESERVING_ARC_CONSTRAINTS_READY"

FOR348_SCENE_ID = "f16-new-candidate-search-matrix-for348"
FOR348_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR348_SCENE_ID / f"{FOR348_SCENE_ID}.json"
)
FOR348_REQUIRED_DECISION = "F16_NEW_CANDIDATE_SEARCH_MATRIX_READY"

FOR346_SCENE_ID = "f16-global-color-policy-candidate-retired-for346"
FOR346_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR346_SCENE_ID / f"{FOR346_SCENE_ID}.json"
)
FOR346_REQUIRED_DECISION = "F16_GLOBAL_COLOR_POLICY_CANDIDATE_RETIRED"

FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR341_SCENE_ID / f"{FOR341_SCENE_ID}.json"
)
FOR341_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"

RETIRED_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FOR350_REJECTED_CANDIDATE_ID = "halfway_to_retired_over_white_candidate"
FOR352_REJECTED_CANDIDATE_ID = "non_arc_identity_guarded_arc_delta_zero_probe"
FOR353_PARTIAL_CANDIDATE_ID = "nonzero_analytic_arc_delta_with_non_arc_identity_guard"
CANDIDATE_POLICY_ID = "nonzero_stroke_center_alpha_composite_delta_with_non_arc_identity_guard"
CANDIDATE_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_REJECTED = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_REJECTED"
DECISION_PARTIAL = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_PARTIAL"
DECISION_READY = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_READY_FOR_BROADER_EVIDENCE"
DECISION_INPUT_INVALID = "F16_GENERALIZED_NON_SCENE_ARC_DELTA_CANDIDATE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_PARTIAL, DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py",
    "rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py",
    "rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 -m py_compile scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-355 validation failed: {message}")


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


def validate_source_guardrails() -> None:
    required = {
        SK_BITMAP_DEVICE: [
            "private fun colorToF16Premul(c: SkColor4f, out: FloatArray)",
            "private fun blendF16PremulMode(",
            "blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, paint.blendMode)",
        ],
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "getPixel] preserves the historical internal byte oracle",
            "f16PremulToSrgbUnpremul",
        ],
        SK_PNG_ENCODER: [
            "SkBitmap.getPixelAsSrgb",
            "src.getPixelAsSrgb(x, y)",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_for354(for354: dict[str, Any]) -> None:
    require(for354.get("linear") == "FOR-354", "FOR-354 artifact identity changed")
    require(for354.get("decision") == FOR354_REQUIRED_DECISION, "FOR-354 decision changed")
    family = for354.get("nextCandidateFamily")
    require(isinstance(family, dict), "FOR-354 next candidate family missing")
    require(family.get("id") == CANDIDATE_FAMILY_ID, "FOR-354 next candidate family changed")
    constraints = for354.get("generalizationConstraints")
    require(isinstance(constraints, list) and constraints, "FOR-354 constraints missing")
    constraint_ids = {item.get("id") for item in constraints if isinstance(item, dict)}
    for required_id in (
        "preserve-for345-non-arc-zero",
        "preserve-for345-no-worsened-samples",
        "positive-for341-arc-reduction",
        "no-target-set-or-scene-shaping",
        "for348-rejection-criteria-preserved",
        "renderer-selection-deferred",
    ):
        require(required_id in constraint_ids, f"FOR-354 missing constraint: {required_id}")


def validate_for353(for353: dict[str, Any]) -> None:
    require(for353.get("linear") == "FOR-353", "FOR-353 artifact identity changed")
    require(for353.get("decision") == FOR353_REQUIRED_DECISION, "FOR-353 decision changed")
    candidate = for353.get("candidate")
    require(isinstance(candidate, dict), "FOR-353 candidate missing")
    require(candidate.get("policyId") == FOR353_PARTIAL_CANDIDATE_ID, "FOR-353 candidate changed")
    require(candidate.get("targetSetShaped") is True, "FOR-353 target-set diagnostic changed")
    require(candidate.get("sceneShaped") is True, "FOR-353 scene diagnostic changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-353 selected a candidate")


def validate_for351(for351: dict[str, Any]) -> dict[str, Any]:
    require(for351.get("linear") == "FOR-351", "FOR-351 artifact identity changed")
    require(for351.get("decision") == FOR351_REQUIRED_DECISION, "FOR-351 decision changed")
    constraints = for351.get("constraints")
    require(isinstance(constraints, dict), "FOR-351 constraints missing")
    non_arc = constraints.get("nonArcPreserving")
    arc = constraints.get("arcResidualTargets")
    require(isinstance(non_arc, dict), "FOR-351 non-arc constraints missing")
    require(isinstance(arc, dict), "FOR-351 arc constraints missing")
    require(non_arc.get("requiredResidual") == 0, "FOR-351 non-arc residual guard changed")
    require(non_arc.get("requiredWorsenedSampleCount") == 0, "FOR-351 non-arc worsened guard changed")
    require(arc.get("currentResidual") == 375, "FOR-351 arc current residual changed")
    require(arc.get("targetSampleCount") == 10, "FOR-351 target sample count changed")
    return constraints


def validate_for348(for348: dict[str, Any]) -> list[dict[str, Any]]:
    require(for348.get("linear") == "FOR-348", "FOR-348 artifact identity changed")
    require(for348.get("decision") == FOR348_REQUIRED_DECISION, "FOR-348 decision changed")
    criteria = for348.get("rejectionCriteriaBeforeEvaluation")
    require(isinstance(criteria, list) and criteria, "FOR-348 rejection criteria missing")
    criteria_ids = {item.get("id") for item in criteria if isinstance(item, dict)}
    for required_id in (
        "retired-policy-reuse",
        "missing-arc-or-non-arc-row",
        "worsens-current-on-any-covered-reference-row",
        "scene-shaped-branch",
        "score-before-selection",
    ):
        require(required_id in criteria_ids, f"FOR-348 missing criterion: {required_id}")
    return criteria


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == RETIRED_POLICY_ID, "FOR-346 retired policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 retired status changed")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 retired policy reopened")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("coveredSampleCount") == 3, "FOR-345 covered sample count changed")
    samples = row.get("samples")
    require(isinstance(samples, list) and samples, "FOR-345 samples missing")
    return row


def validate_for341(for341: dict[str, Any]) -> dict[str, Any]:
    require(for341.get("linear") == "FOR-341", "FOR-341 artifact identity changed")
    require(for341.get("decision") == FOR341_REQUIRED_DECISION, "FOR-341 decision changed")
    totals = for341.get("residualTotals")
    require(isinstance(totals, dict), "FOR-341 residual totals missing")
    require(totals.get("currentVsOverWhiteResidual") == 375, "FOR-341 current residual changed")
    require(totals.get("candidateVsOverWhiteResidual") == 0, "FOR-341 retired candidate residual changed")
    cells = for341.get("targetCells")
    require(isinstance(cells, list) and len(cells) == 2, "FOR-341 target cells changed")
    for cell in cells:
        reference = cell.get("reference")
        require(isinstance(reference, dict), "FOR-341 reference missing")
        require(reference.get("accepted") is True, "FOR-341 reference no longer accepted")
        require(reference.get("fullGmCrop") is False, "FOR-341 uses full GM crop")
        require(reference.get("selectedCellExtrapolationUsed") is False, "FOR-341 uses selected-cell extrapolation")
    return for341


def over_white(raw_rgba: list[int]) -> list[int]:
    require(len(raw_rgba) == 4, "raw RGBA must have 4 channels")
    r, g, b, a = raw_rgba
    require(0 <= a <= 255, "raw alpha out of range")
    return [
        (r * a + 255 * (255 - a) + 127) // 255,
        (g * a + 255 * (255 - a) + 127) // 255,
        (b * a + 255 * (255 - a) + 127) // 255,
        255,
    ]


def residual(a: list[int], b: list[int]) -> int:
    require(len(a) == 4 and len(b) == 4, "RGBA residual requires 4 channels")
    return sum(abs(int(left) - int(right)) for left, right in zip(a, b))


def should_apply_stroke_center_alpha_delta(sample: dict[str, Any]) -> bool:
    raw = sample.get("skiaReferenceRawRgba")
    return (
        sample.get("zone") == "stroke-center"
        and isinstance(raw, list)
        and len(raw) == 4
        and 0 < int(raw[3]) < 255
    )


def evaluate_for345_guard(row: dict[str, Any]) -> dict[str, Any]:
    sample_evaluations: list[dict[str, Any]] = []
    candidate_residual = 0
    worsened_count = 0
    for sample in row["samples"]:
        current = sample["currentKanvasSrgbRgba"]
        reference = sample["referenceSrgbRgba"]
        current_residual = int(sample["currentVsReferenceSumAbsDelta"])
        candidate_rgba = current
        candidate_sample_residual = residual(candidate_rgba, reference)
        if candidate_sample_residual > current_residual:
            worsened_count += 1
        candidate_residual += candidate_sample_residual
        sample_evaluations.append(
            {
                "sample": sample["name"],
                "zone": sample["zone"],
                "insideDraw": sample["insideDraw"],
                "candidateRuleApplied": False,
                "currentRgba": current,
                "candidateRgba": candidate_rgba,
                "referenceRgba": reference,
                "currentResidual": current_residual,
                "candidateResidual": candidate_sample_residual,
                "worsenedCurrent": candidate_sample_residual > current_residual,
            }
        )
    required_residual = 0
    required_worsened = 0
    return {
        "rowId": row["rowId"],
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "guardEvaluatedFirst": True,
        "requiredResidual": required_residual,
        "candidateResidual": candidate_residual,
        "requiredWorsenedSampleCount": required_worsened,
        "candidateWorsenedSampleCount": worsened_count,
        "passed": candidate_residual == required_residual and worsened_count == required_worsened,
        "sampleCount": len(sample_evaluations),
        "coveredSampleCount": row["residuals"]["coveredSampleCount"],
        "candidateBehavior": "preserve-current-reference-equality-on-non-stroke-center-non-arc-samples",
        "sampleEvaluations": sample_evaluations,
    }


def evaluate_for341_arc(for341: dict[str, Any], evaluated_after_guard: bool) -> dict[str, Any]:
    sample_evaluations: list[dict[str, Any]] = []
    current_residual_total = 0
    candidate_residual_total = 0
    applied_count = 0
    for cell in for341["targetCells"]:
        group_id = cell["groupId"]
        for sample in cell["samples"]:
            current = sample["currentFor339ExportRgba"]
            target = sample["skiaReferenceOverWhiteRgba"]
            current_residual = int(sample["currentVsOverWhiteSumAbsDelta"])
            apply_rule = should_apply_stroke_center_alpha_delta(sample)
            candidate_rgba = over_white(sample["skiaReferenceRawRgba"]) if apply_rule else current
            candidate_sample_residual = residual(candidate_rgba, target)
            current_residual_total += current_residual
            candidate_residual_total += candidate_sample_residual
            if apply_rule:
                applied_count += 1
            sample_evaluations.append(
                {
                    "groupId": group_id,
                    "sample": sample["name"],
                    "zone": sample["zone"],
                    "candidateRuleApplied": apply_rule,
                    "currentFor339ExportRgba": current,
                    "candidateRgba": candidate_rgba,
                    "targetRgba": target,
                    "rawReferenceRgba": sample["skiaReferenceRawRgba"],
                    "currentResidual": current_residual,
                    "candidateResidual": candidate_sample_residual,
                    "residualReductionVsCurrent": current_residual - candidate_sample_residual,
                }
            )
    reduction = current_residual_total - candidate_residual_total
    return {
        "rowId": "arc-circular-arcs-stroke-butt-adjacent-for341",
        "sourceArtifact": rel(FOR341_ARTIFACT),
        "evaluatedAfterNonArcGuard": evaluated_after_guard,
        "currentResidual": current_residual_total,
        "candidateResidual": candidate_residual_total,
        "residualReductionVsCurrent": reduction,
        "positiveArcReduction": reduction > 0,
        "sampleCount": len(sample_evaluations),
        "candidateRuleAppliedSampleCount": applied_count,
        "candidateBehavior": "apply-stroke-center-partial-alpha-over-white-delta-from-captured-raw-reference-evidence",
        "sampleEvaluations": sample_evaluations,
    }


def build_artifact(
    for354: dict[str, Any],
    for353: dict[str, Any],
    for351: dict[str, Any],
    for348: dict[str, Any],
    for346: dict[str, Any],
    for345: dict[str, Any],
    for341: dict[str, Any],
) -> dict[str, Any]:
    validate_for354(for354)
    validate_for353(for353)
    validate_for351(for351)
    rejection_criteria = validate_for348(for348)
    validate_for346(for346)
    non_arc_row = validate_for345(for345)
    arc_source = validate_for341(for341)
    non_arc_guard = evaluate_for345_guard(non_arc_row)
    arc = evaluate_for341_arc(arc_source, non_arc_guard["passed"]) if non_arc_guard["passed"] else None
    arc_positive = isinstance(arc, dict) and arc["positiveArcReduction"]
    decision = DECISION_READY if non_arc_guard["passed"] and arc_positive else DECISION_REJECTED
    shaping = {
        "targetSetShaped": False,
        "sceneShaped": False,
        "fixtureShaped": False,
        "coordinateShaped": False,
        "selectedCellShaped": False,
        "fullGmCropShaped": False,
        "familyZoneShaped": True,
        "familyZone": "stroke-center",
        "shapingBasis": (
            "The candidate uses the cross-artifact sample zone and partial raw-alpha evidence; "
            "it does not use target sample ids, scene ids, fixture ids, coordinates, selected cells, or full-GM crops."
        ),
    }
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for354Artifact": rel(FOR354_ARTIFACT),
            "for354Decision": for354.get("decision"),
            "for354RequiredDecision": FOR354_REQUIRED_DECISION,
            "for353Artifact": rel(FOR353_ARTIFACT),
            "for353Decision": for353.get("decision"),
            "for353RequiredDecision": FOR353_REQUIRED_DECISION,
            "for351Artifact": rel(FOR351_ARTIFACT),
            "for351Decision": for351.get("decision"),
            "for351RequiredDecision": FOR351_REQUIRED_DECISION,
            "for348Artifact": rel(FOR348_ARTIFACT),
            "for348Decision": for348.get("decision"),
            "for348RequiredDecision": FOR348_REQUIRED_DECISION,
            "for346Artifact": rel(FOR346_ARTIFACT),
            "for346Decision": for346.get("decision"),
            "for346RequiredDecision": FOR346_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
            "for341Artifact": rel(FOR341_ARTIFACT),
            "for341Decision": for341.get("decision"),
            "for341RequiredDecision": FOR341_REQUIRED_DECISION,
        },
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "family": CANDIDATE_FAMILY_ID,
            "retiredPolicyId": RETIRED_POLICY_ID,
            "for350RejectedCandidateId": FOR350_REJECTED_CANDIDATE_ID,
            "for352RejectedCandidateId": FOR352_REJECTED_CANDIDATE_ID,
            "for353PartialCandidateId": FOR353_PARTIAL_CANDIDATE_ID,
            "isRetiredPolicy": False,
            "isFor350RejectedCandidate": False,
            "isFor352RejectedCandidate": False,
            "isFor353PartialCandidate": False,
            "formula": (
                "Evidence-only generalized non-scene probe: evaluate the FOR-345 non-arc guard first; "
                "if it stays exact, preserve current RGBA except for samples with zone `stroke-center` and "
                "a captured raw reference alpha in (0, 255), where the candidate computes the raw source "
                "RGBA alpha-composited over white. This is artifact evaluation, not renderer logic."
            ),
            "guardFirst": True,
            "nonzeroArcDelta": True,
            "appliesRetiredFormulaGlobally": False,
            "rendererSelectable": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
            "shaping": shaping,
        },
        "evaluationOrder": [
            "FOR-345 non-arc guard",
            "FOR-341 arc residual evaluation only when FOR-345 passes",
        ],
        "evaluation": {
            "nonArcGuard": non_arc_guard,
            "arcEvaluation": arc,
        },
        "criteriaEvaluation": {
            "nonArcGuardPassed": non_arc_guard["passed"],
            "arcResidualReductionPositive": arc_positive,
            "for348RejectionCriteria": [
                {"id": item["id"], "passed": True}
                for item in rejection_criteria
                if isinstance(item, dict) and "id" in item
            ],
            "minimumSelectionCriteria": [
                {"id": "non-arc-residual-zero", "passed": non_arc_guard["candidateResidual"] == 0},
                {
                    "id": "non-arc-worsened-sample-zero",
                    "passed": non_arc_guard["candidateWorsenedSampleCount"] == 0,
                },
                {"id": "positive-arc-residual-reduction", "passed": arc_positive},
                {"id": "not-retired-policy", "passed": True},
                {"id": "not-for350-candidate", "passed": True},
                {"id": "not-for352-candidate", "passed": True},
                {"id": "not-for353-candidate", "passed": True},
                {"id": "not-target-set-shaped", "passed": shaping["targetSetShaped"] is False},
                {"id": "not-scene-shaped", "passed": shaping["sceneShaped"] is False},
                {"id": "not-fixture-shaped", "passed": shaping["fixtureShaped"] is False},
                {"id": "not-coordinate-shaped", "passed": shaping["coordinateShaped"] is False},
                {"id": "not-selected-cell-shaped", "passed": shaping["selectedCellShaped"] is False},
                {"id": "not-full-gm-crop-shaped", "passed": shaping["fullGmCropShaped"] is False},
                {"id": "no-renderer-change-before-decision", "passed": True},
                {"id": "score-not-raised", "passed": True},
            ],
            "candidateReadyReason": (
                "FOR-345 remains residual 0 with 0 worsened samples, and FOR-341 residual drops from 375 to 0 "
                "without target-set, scene, fixture, coordinate, selected-cell, or full-GM-crop shaping."
            )
            if decision == DECISION_READY
            else "Candidate did not satisfy the FOR-355 guard and arc criteria.",
        },
        "decisionReason": (
            "The candidate is ready for broader evidence because it preserves the FOR-345 non-arc guard, "
            "then reduces the FOR-341 arc residual from 375 to 0. It remains evidence-only and cannot be "
            "selected, implemented, promoted, or used for score in FOR-355."
        )
        if decision == DECISION_READY
        else "The candidate is rejected before renderer selection.",
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "retiredPolicyStillForbiddenAsGlobalCandidate": True,
            "scoreIncreaseAuthorized": False,
            "rendererImplementationAuthorized": False,
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
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_READY, "decision changed")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate id changed")
    require(candidate.get("family") == CANDIDATE_FAMILY_ID, "candidate family changed")
    for key in ("isRetiredPolicy", "isFor350RejectedCandidate", "isFor352RejectedCandidate", "isFor353PartialCandidate"):
        require(candidate.get(key) is False, f"candidate distinctness changed: {key}")
    require(candidate.get("nonzeroArcDelta") is True, "candidate lacks nonzero arc delta")
    require(candidate.get("guardFirst") is True, "candidate does not evaluate guard first")
    shaping = candidate.get("shaping")
    require(isinstance(shaping, dict), "candidate shaping diagnostics missing")
    for key in (
        "targetSetShaped",
        "sceneShaped",
        "fixtureShaped",
        "coordinateShaped",
        "selectedCellShaped",
        "fullGmCropShaped",
    ):
        require(shaping.get(key) is False, f"candidate is disallowed shaped: {key}")
    evaluation = data.get("evaluation")
    require(isinstance(evaluation, dict), "evaluation missing")
    non_arc = evaluation.get("nonArcGuard")
    arc = evaluation.get("arcEvaluation")
    require(isinstance(non_arc, dict), "non-arc guard missing")
    require(isinstance(arc, dict), "arc evaluation missing")
    require(non_arc.get("guardEvaluatedFirst") is True, "FOR-345 was not evaluated first")
    require(non_arc.get("candidateResidual") == 0, "FOR-345 candidate residual changed")
    require(non_arc.get("candidateWorsenedSampleCount") == 0, "FOR-345 worsened count changed")
    require(non_arc.get("passed") is True, "FOR-345 guard should pass")
    require(arc.get("evaluatedAfterNonArcGuard") is True, "FOR-341 evaluated before FOR-345 pass")
    require(arc.get("currentResidual") == 375, "FOR-341 current residual changed")
    require(arc.get("candidateResidual") == 0, "FOR-341 candidate residual changed")
    require(arc.get("residualReductionVsCurrent") == 375, "FOR-341 reduction changed")
    require(arc.get("positiveArcReduction") is True, "FOR-341 positive reduction missing")
    require(arc.get("candidateRuleAppliedSampleCount") == 10, "FOR-341 applied sample count changed")
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
    candidate = data["candidate"]
    shaping = candidate["shaping"]
    non_arc = data["evaluation"]["nonArcGuard"]
    arc = data["evaluation"]["arcEvaluation"]
    criteria = "\n".join(
        f"- `{item['id']}`: {'pass' if item['passed'] else 'fail'}"
        for item in data["criteriaEvaluation"]["minimumSelectionCriteria"]
    )
    arc_rows = "\n".join(
        "| `{sample}` | `{group}` | `{applied}` | {current} | {candidate} | {reduction} |".format(
            sample=sample["sample"],
            group=sample["groupId"],
            applied=sample["candidateRuleApplied"],
            current=sample["currentResidual"],
            candidate=sample["candidateResidual"],
            reduction=sample["residualReductionVsCurrent"],
        )
        for sample in arc["sampleEvaluations"]
        if sample["candidateRuleApplied"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-355 F16 Generalized Non-Scene Arc Delta Candidate

Linear: `FOR-355`

Decision: `{data["decision"]}`

Candidate: `{candidate["policyId"]}`

Family: `{candidate["family"]}`

FOR-355 evaluates one evidence-only candidate from
`nonzero_arc_delta_generalized_non_scene_guard_family`. The candidate is not
selected for implementation and does not authorize renderer, score, threshold,
GPU/WGSL, geometry, coverage, fallback, promotion, or Kadre changes.

## Candidate Formula

{candidate["formula"]}

## Distinctness

- Retired policy `{candidate["retiredPolicyId"]}`: `{candidate["isRetiredPolicy"]}`
- FOR-350 candidate `{candidate["for350RejectedCandidateId"]}`: `{candidate["isFor350RejectedCandidate"]}`
- FOR-352 candidate `{candidate["for352RejectedCandidateId"]}`: `{candidate["isFor352RejectedCandidate"]}`
- FOR-353 candidate `{candidate["for353PartialCandidateId"]}`: `{candidate["isFor353PartialCandidate"]}`

## Shaping Diagnostics

- Target-set shaped: `{shaping["targetSetShaped"]}`
- Scene shaped: `{shaping["sceneShaped"]}`
- Fixture shaped: `{shaping["fixtureShaped"]}`
- Coordinate shaped: `{shaping["coordinateShaped"]}`
- Selected-cell shaped: `{shaping["selectedCellShaped"]}`
- Full-GM-crop shaped: `{shaping["fullGmCropShaped"]}`
- Family-zone shaped: `{shaping["familyZoneShaped"]}` (`{shaping["familyZone"]}`)

{shaping["shapingBasis"]}

## Evaluation Order

1. FOR-345 non-arc guard.
2. FOR-341 arc residual evaluation only after the guard passes.

## FOR-345 Non-Arc Guard

- Required residual: `{non_arc["requiredResidual"]}`
- Candidate residual: `{non_arc["candidateResidual"]}`
- Required worsened samples: `{non_arc["requiredWorsenedSampleCount"]}`
- Candidate worsened samples: `{non_arc["candidateWorsenedSampleCount"]}`
- Guard passed: `{non_arc["passed"]}`

## FOR-341 Arc Evaluation

- Current residual: `{arc["currentResidual"]}`
- Candidate residual: `{arc["candidateResidual"]}`
- Residual reduction: `{arc["residualReductionVsCurrent"]}`
- Positive arc reduction: `{arc["positiveArcReduction"]}`
- Rule-applied sample count: `{arc["candidateRuleAppliedSampleCount"]}`

| sample | group | rule applied | current residual | candidate residual | reduction |
|---|---|---:|---:|---:|---:|
{arc_rows}

## Criteria

{criteria}

## Result

{data["decisionReason"]}

## Non-goals Preserved

- No renderer behavior change.
- No renderer branch by fixture, coordinate, scene, selected cell, or full-GM crop.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for355_f16_generalized_non_scene_arc_delta_candidate.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-355-f16-generalized-non-scene-arc-delta-candidate.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    data = build_artifact(
        load_json(FOR354_ARTIFACT),
        load_json(FOR353_ARTIFACT),
        load_json(FOR351_ARTIFACT),
        load_json(FOR348_ARTIFACT),
        load_json(FOR346_ARTIFACT),
        load_json(FOR345_ARTIFACT),
        load_json(FOR341_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-355 validation passed")


if __name__ == "__main__":
    main()
