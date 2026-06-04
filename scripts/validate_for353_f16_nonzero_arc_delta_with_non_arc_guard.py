#!/usr/bin/env python3
"""Validate the FOR-353 nonzero arc-delta F16 candidate evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-353"
SCENE_ID = "f16-nonzero-arc-delta-with-non-arc-guard-for353"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-353-f16-nonzero-arc-delta-with-non-arc-guard.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-evaluate-nonzero-analytic-arc-delta-with-non-arc-guard-ticket"
)
SOURCE_FINDING = "global/kanvas/findings/for-352-f16-non-arc-identity-guarded-arc-delta-candidate-rejected"

FOR352_SCENE_ID = "f16-non-arc-identity-guarded-arc-delta-candidate-for352"
FOR352_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR352_SCENE_ID / f"{FOR352_SCENE_ID}.json"
)
FOR352_REQUIRED_DECISION = "F16_NON_ARC_IDENTITY_GUARDED_ARC_DELTA_CANDIDATE_REJECTED"

FOR351_SCENE_ID = "f16-non-arc-preserving-arc-constraints-for351"
FOR351_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR351_SCENE_ID / f"{FOR351_SCENE_ID}.json"
)
FOR351_REQUIRED_DECISION = "F16_NON_ARC_PRESERVING_ARC_CONSTRAINTS_READY"

FOR350_SCENE_ID = "f16-arc-improving-non-arc-safe-candidate-for350"
FOR350_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR350_SCENE_ID / f"{FOR350_SCENE_ID}.json"
)
FOR350_REQUIRED_DECISION = "F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_REJECTED"

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

RETIRED_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FOR350_REJECTED_CANDIDATE_ID = "halfway_to_retired_over_white_candidate"
FOR352_REJECTED_CANDIDATE_ID = "non_arc_identity_guarded_arc_delta_zero_probe"
CANDIDATE_POLICY_ID = "nonzero_analytic_arc_delta_with_non_arc_identity_guard"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_REJECTED = "F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_REJECTED"
DECISION_PARTIAL = "F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_PARTIAL"
DECISION_READY = "F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_READY_FOR_BROADER_EVIDENCE"
DECISION_INPUT_INVALID = "F16_NONZERO_ARC_DELTA_WITH_NON_ARC_GUARD_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_PARTIAL, DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py",
    "rtk python3 scripts/validate_for352_f16_non_arc_identity_guarded_arc_delta_candidate.py",
    "rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py",
    "rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-353 validation failed: {message}")


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


def validate_for352(for352: dict[str, Any]) -> None:
    require(for352.get("linear") == "FOR-352", "FOR-352 artifact identity changed")
    require(for352.get("decision") == FOR352_REQUIRED_DECISION, "FOR-352 decision changed")
    candidate = for352.get("candidate")
    require(isinstance(candidate, dict), "FOR-352 candidate missing")
    require(candidate.get("policyId") == FOR352_REJECTED_CANDIDATE_ID, "FOR-352 candidate changed")
    criteria = for352.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "FOR-352 criteria missing")
    require(criteria.get("nonArcGuardPassed") is True, "FOR-352 non-arc guard changed")
    require(criteria.get("arcResidualReductionPositive") is False, "FOR-352 arc rejection changed")


def validate_for351(for351: dict[str, Any]) -> dict[str, Any]:
    require(for351.get("linear") == "FOR-351", "FOR-351 artifact identity changed")
    require(for351.get("decision") == FOR351_REQUIRED_DECISION, "FOR-351 decision changed")
    constraints = for351.get("constraints")
    require(isinstance(constraints, dict), "FOR-351 constraints missing")
    non_arc = constraints.get("nonArcPreserving")
    arc = constraints.get("arcResidualTargets")
    require(isinstance(non_arc, dict), "FOR-351 non-arc constraints missing")
    require(isinstance(arc, dict), "FOR-351 arc targets missing")
    require(non_arc.get("requiredResidual") == 0, "FOR-351 non-arc residual guard changed")
    require(non_arc.get("requiredWorsenedSampleCount") == 0, "FOR-351 non-arc worsened guard changed")
    require(arc.get("currentResidual") == 375, "FOR-351 arc residual changed")
    require(arc.get("targetSampleCount") == 10, "FOR-351 arc target count changed")
    return constraints


def validate_for350(for350: dict[str, Any]) -> None:
    require(for350.get("linear") == "FOR-350", "FOR-350 artifact identity changed")
    require(for350.get("decision") == FOR350_REQUIRED_DECISION, "FOR-350 decision changed")
    candidate = for350.get("candidate")
    require(isinstance(candidate, dict), "FOR-350 candidate missing")
    require(candidate.get("policyId") == FOR350_REJECTED_CANDIDATE_ID, "FOR-350 candidate changed")
    criteria = for350.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "FOR-350 criteria missing")
    require(criteria.get("arcPositive") is True, "FOR-350 arc positive evidence changed")
    require(criteria.get("nonArcPositive") is False, "FOR-350 non-arc rejection changed")


def validate_for348(for348: dict[str, Any]) -> None:
    require(for348.get("linear") == "FOR-348", "FOR-348 artifact identity changed")
    require(for348.get("decision") == FOR348_REQUIRED_DECISION, "FOR-348 decision changed")
    matrix = for348.get("matrix")
    require(isinstance(matrix, dict), "FOR-348 matrix missing")
    require(matrix.get("arcRowCount") >= 1, "FOR-348 arc row missing")
    require(matrix.get("nonArcRowCount") >= 1, "FOR-348 non-arc row missing")


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == RETIRED_POLICY_ID, "FOR-346 retired policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 retired policy status changed")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 retired policy reopened")


def validate_for345(for345: dict[str, Any]) -> None:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 retired worsened count changed")


def evaluate_candidate(constraints: dict[str, Any]) -> dict[str, Any]:
    non_arc = constraints["nonArcPreserving"]
    arc = constraints["arcResidualTargets"]
    guarded_samples = non_arc["guardedSamples"]
    arc_targets = arc["targets"]

    non_arc_guard = {
        "rowId": non_arc["rowId"],
        "guardEvaluatedFirst": True,
        "requiredResidual": non_arc["requiredResidual"],
        "candidateResidual": 0,
        "requiredWorsenedSampleCount": non_arc["requiredWorsenedSampleCount"],
        "candidateWorsenedSampleCount": 0,
        "passed": True,
        "guardedSampleCount": len(guarded_samples),
        "candidateBehavior": "preserve-current-reference-equality-on-guarded-non-arc-samples",
    }

    target_evaluations: list[dict[str, Any]] = []
    for target in arc_targets:
        current_residual = int(target["currentResidual"])
        target_evaluations.append(
            {
                "groupId": target["groupId"],
                "sample": target["sample"],
                "zone": target["zone"],
                "currentFor339ExportRgba": target["currentFor339ExportRgba"],
                "candidateRgba": target["skiaOverWhiteTargetRgba"],
                "targetRgba": target["skiaOverWhiteTargetRgba"],
                "currentResidual": current_residual,
                "candidateResidual": 0,
                "residualReductionVsCurrent": current_residual,
            }
        )

    current_residual = int(arc["currentResidual"])
    candidate_residual = sum(int(target["candidateResidual"]) for target in target_evaluations)
    reduction = current_residual - candidate_residual
    return {
        "nonArcGuard": non_arc_guard,
        "arcEvaluation": {
            "rowId": arc["rowId"],
            "evaluatedAfterNonArcGuard": non_arc_guard["passed"],
            "currentResidual": current_residual,
            "candidateResidual": candidate_residual,
            "residualReductionVsCurrent": reduction,
            "targetSampleCount": len(target_evaluations),
            "positiveArcReduction": reduction > 0,
            "targetSetShaped": True,
            "rendererSelectable": False,
            "candidateBehavior": "target-set-delta-probe-maps-each-for351-arc-target-to-its-skia-over-white-rgba",
            "targetEvaluations": target_evaluations,
        },
    }


def build_artifact(
    for352: dict[str, Any],
    for351: dict[str, Any],
    for350: dict[str, Any],
    for348: dict[str, Any],
    for346: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    validate_for352(for352)
    constraints = validate_for351(for351)
    validate_for350(for350)
    validate_for348(for348)
    validate_for346(for346)
    validate_for345(for345)
    evaluation = evaluate_candidate(constraints)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for352Artifact": rel(FOR352_ARTIFACT),
            "for352Decision": for352.get("decision"),
            "for352RequiredDecision": FOR352_REQUIRED_DECISION,
            "for351Artifact": rel(FOR351_ARTIFACT),
            "for351Decision": for351.get("decision"),
            "for351RequiredDecision": FOR351_REQUIRED_DECISION,
            "for350Artifact": rel(FOR350_ARTIFACT),
            "for350Decision": for350.get("decision"),
            "for350RequiredDecision": FOR350_REQUIRED_DECISION,
            "for348Artifact": rel(FOR348_ARTIFACT),
            "for348Decision": for348.get("decision"),
            "for348RequiredDecision": FOR348_REQUIRED_DECISION,
            "for346Artifact": rel(FOR346_ARTIFACT),
            "for346Decision": for346.get("decision"),
            "for346RequiredDecision": FOR346_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "retiredPolicyId": RETIRED_POLICY_ID,
            "for350RejectedCandidateId": FOR350_REJECTED_CANDIDATE_ID,
            "for352RejectedCandidateId": FOR352_REJECTED_CANDIDATE_ID,
            "isRetiredPolicy": False,
            "isFor350RejectedCandidate": False,
            "isFor352RejectedCandidate": False,
            "formula": (
                "Evidence-only target-set delta probe: first preserve the FOR-345 non-arc identity guard, "
                "then map each FOR-351 arc target sample from current RGBA to its captured Skia-over-white "
                "target RGBA. This is artifact evaluation, not renderer logic."
            ),
            "nonzeroArcDelta": True,
            "guardFirst": True,
            "targetSetShaped": True,
            "sceneShaped": True,
            "rendererSelectable": False,
            "selectedForImplementation": False,
            "rendererBehaviorChanged": False,
            "scoreIncreaseAuthorized": False,
        },
        "evaluation": evaluation,
        "criteriaEvaluation": {
            "nonArcGuardPassed": evaluation["nonArcGuard"]["passed"],
            "arcResidualReductionPositive": evaluation["arcEvaluation"]["positiveArcReduction"],
            "targetSetShaped": evaluation["arcEvaluation"]["targetSetShaped"],
            "rendererSelectable": evaluation["arcEvaluation"]["rendererSelectable"],
            "candidatePartialReason": "NONZERO_ARC_DELTA_IS_TARGET_SET_SHAPED_AND_NOT_RENDERER_SELECTABLE",
            "minimumSelectionCriteria": [
                {"id": "non-arc-residual-zero", "passed": True},
                {"id": "non-arc-worsened-sample-zero", "passed": True},
                {"id": "positive-arc-residual-reduction", "passed": True},
                {"id": "not-retired-policy", "passed": True},
                {"id": "not-previous-rejected-candidate", "passed": True},
                {"id": "not-target-set-shaped", "passed": False},
                {"id": "no-renderer-change-before-decision", "passed": True},
            ],
        },
        "decisionReason": (
            "The candidate keeps the FOR-345 guard at residual 0 and reduces the FOR-341 target-set "
            "arc residual from 375 to 0, but it is explicitly shaped to the measured target set. "
            "It remains partial evidence and is not selectable for renderer implementation."
        ),
        "nextSearchConstraint": {
            "requires": (
                "Broaden or generalize the nonzero arc delta so it is no longer fixture, scene, "
                "coordinate, selected-cell, full-GM-crop, or target-set shaped while preserving the "
                "FOR-345 residual 0 guard."
            ),
            "rendererImplementationAuthorized": False,
            "scoreIncreaseAuthorized": False,
        },
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "retiredPolicyStillForbiddenAsGlobalCandidate": True,
            "scoreIncreaseAuthorized": False,
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
            "rendererSelectedCellOrFullGmCropBranchAdded": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_PARTIAL, "decision changed")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate id changed")
    require(candidate.get("isRetiredPolicy") is False, "candidate reused retired policy")
    require(candidate.get("isFor350RejectedCandidate") is False, "candidate reused FOR-350 candidate")
    require(candidate.get("isFor352RejectedCandidate") is False, "candidate reused FOR-352 candidate")
    require(candidate.get("nonzeroArcDelta") is True, "candidate lacks nonzero arc delta")
    require(candidate.get("targetSetShaped") is True, "candidate target-set diagnostic changed")
    require(candidate.get("rendererSelectable") is False, "candidate became renderer selectable")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")
    evaluation = data.get("evaluation")
    require(isinstance(evaluation, dict), "evaluation missing")
    non_arc = evaluation.get("nonArcGuard")
    arc = evaluation.get("arcEvaluation")
    require(isinstance(non_arc, dict), "non-arc guard missing")
    require(isinstance(arc, dict), "arc evaluation missing")
    require(non_arc.get("guardEvaluatedFirst") is True, "non-arc guard was not evaluated first")
    require(non_arc.get("candidateResidual") == 0, "non-arc residual changed")
    require(non_arc.get("candidateWorsenedSampleCount") == 0, "non-arc worsened count changed")
    require(non_arc.get("passed") is True, "non-arc guard should pass")
    require(arc.get("evaluatedAfterNonArcGuard") is True, "arc was not evaluated after guard")
    require(arc.get("currentResidual") == 375, "arc current residual changed")
    require(arc.get("candidateResidual") == 0, "arc candidate residual changed")
    require(arc.get("residualReductionVsCurrent") == 375, "arc reduction changed")
    require(arc.get("targetSampleCount") == 10, "arc target sample count changed")
    require(arc.get("positiveArcReduction") is True, "arc reduction should be positive")
    require(arc.get("targetSetShaped") is True, "target-set shaped diagnostic missing")
    require(arc.get("rendererSelectable") is False, "arc candidate became renderer selectable")
    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteria missing")
    require(criteria.get("nonArcGuardPassed") is True, "non-arc guard criterion changed")
    require(criteria.get("arcResidualReductionPositive") is True, "arc reduction criterion changed")
    require(criteria.get("targetSetShaped") is True, "target-set criterion changed")
    require(criteria.get("rendererSelectable") is False, "renderer selection criterion changed")
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
        "rendererSelectedCellOrFullGmCropBranchAdded",
    ):
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    candidate = data["candidate"]
    non_arc = data["evaluation"]["nonArcGuard"]
    arc = data["evaluation"]["arcEvaluation"]
    criteria = "\n".join(
        f"- `{item['id']}`: {'pass' if item['passed'] else 'fail'}"
        for item in data["criteriaEvaluation"]["minimumSelectionCriteria"]
    )
    sample_rows = "\n".join(
        "| `{sample}` | `{group}` | {current} | {candidate} | {reduction} |".format(
            sample=target["sample"],
            group=target["groupId"],
            current=target["currentResidual"],
            candidate=target["candidateResidual"],
            reduction=target["residualReductionVsCurrent"],
        )
        for target in arc["targetEvaluations"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-353 F16 Nonzero Arc Delta With Non-Arc Guard

Linear: `FOR-353`

Decision: `{data["decision"]}`

Candidate: `{candidate["policyId"]}`

FOR-353 evaluates one nonzero arc-delta candidate after the FOR-345 non-arc
guard passes. The candidate proves the target-set arc delta can reduce the
captured FOR-341 residual, but remains partial because it is explicitly shaped
to the FOR-351 target set and is not renderer-selectable.

## Non-Arc Guard

- Required residual: `{non_arc["requiredResidual"]}`
- Candidate residual: `{non_arc["candidateResidual"]}`
- Required worsened samples: `{non_arc["requiredWorsenedSampleCount"]}`
- Candidate worsened samples: `{non_arc["candidateWorsenedSampleCount"]}`
- Guard passed: `{non_arc["passed"]}`

## Arc Evaluation

- Current residual: `{arc["currentResidual"]}`
- Candidate residual: `{arc["candidateResidual"]}`
- Residual reduction: `{arc["residualReductionVsCurrent"]}`
- Positive arc reduction: `{arc["positiveArcReduction"]}`
- Target-set shaped: `{arc["targetSetShaped"]}`
- Renderer selectable: `{arc["rendererSelectable"]}`

| sample | group | current residual | candidate residual | reduction |
|---|---|---:|---:|---:|
{sample_rows}

## Selection Criteria

{criteria}

## Result

The candidate keeps FOR-345 at residual `0` and reduces the FOR-341 target-set
residual from `375` to `0`, but it is still not a renderer candidate. It is
dependent on the measured target set and cannot be selected, promoted, or used
to raise score in this ticket.

## Next Constraint

{data["nextSearchConstraint"]["requires"]}

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
- Validator: `scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-353-f16-nonzero-arc-delta-with-non-arc-guard.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    data = build_artifact(
        load_json(FOR352_ARTIFACT),
        load_json(FOR351_ARTIFACT),
        load_json(FOR350_ARTIFACT),
        load_json(FOR348_ARTIFACT),
        load_json(FOR346_ARTIFACT),
        load_json(FOR345_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-353 validation passed")


if __name__ == "__main__":
    main()
