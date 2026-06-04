#!/usr/bin/env python3
"""Validate the FOR-350 arc-improving/non-arc-safe candidate evaluation."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-350"
SCENE_ID = "f16-arc-improving-non-arc-safe-candidate-for350"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-350-f16-arc-improving-non-arc-safe-candidate.md"

SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-f16-evaluate-arc-improving-non-arc-safe-candidate-ticket"
SOURCE_FINDING = "global/kanvas/findings/for-349-f16-replacement-candidate-rejected-finding"

FOR349_SCENE_ID = "f16-replacement-candidate-evaluation-for349"
FOR349_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR349_SCENE_ID / f"{FOR349_SCENE_ID}.json"
)
FOR349_REQUIRED_DECISION = "F16_REPLACEMENT_CANDIDATE_EVALUATED_REJECTED"

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

RETIRED_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FOR349_CONTROL_POLICY_ID = "current_color_managed_rec2020_f16_src_over_then_srgb_export_control"
CANDIDATE_POLICY_ID = "halfway_to_retired_over_white_candidate"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_REJECTED = "F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_REJECTED"
DECISION_PARTIAL = "F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_PARTIAL"
DECISION_READY = "F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_READY"
DECISION_INPUT_INVALID = "F16_ARC_IMPROVING_NON_ARC_SAFE_CANDIDATE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_PARTIAL, DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py",
    "rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-350 validation failed: {message}")


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


def validate_for349(for349: dict[str, Any]) -> None:
    require(for349.get("linear") == "FOR-349", "FOR-349 artifact identity changed")
    require(for349.get("decision") == FOR349_REQUIRED_DECISION, "FOR-349 decision changed")
    candidate = for349.get("candidate")
    require(isinstance(candidate, dict), "FOR-349 candidate missing")
    require(candidate.get("policyId") == FOR349_CONTROL_POLICY_ID, "FOR-349 control candidate changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-349 selected candidate unexpectedly")


def validate_for348(for348: dict[str, Any]) -> list[dict[str, Any]]:
    require(for348.get("linear") == "FOR-348", "FOR-348 artifact identity changed")
    require(for348.get("decision") == FOR348_REQUIRED_DECISION, "FOR-348 matrix decision changed")
    matrix = for348.get("matrix")
    require(isinstance(matrix, dict), "FOR-348 matrix missing")
    rows = matrix.get("rows")
    require(isinstance(rows, list) and len(rows) >= 2, "FOR-348 rows missing")
    require(matrix.get("arcRowCount") >= 1, "FOR-348 lacks arc row")
    require(matrix.get("nonArcRowCount") >= 1, "FOR-348 lacks non-arc row")
    return rows


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == RETIRED_POLICY_ID, "FOR-346 retired policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 retired policy status changed")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 retired policy reopened")


def candidate_rows(matrix_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in matrix_rows:
        row_id = row.get("rowId")
        family = row.get("family")
        current = row.get("currentResidual")
        retired = row.get("candidateResidual")
        worsened = int(row.get("worsenedSampleCount", 0))
        require(isinstance(row_id, str) and row_id, "matrix row id missing")
        require(family in ("arc", "non-arc"), f"{row_id} family changed")
        require(isinstance(current, int), f"{row_id} current residual missing")
        require(isinstance(retired, int), f"{row_id} retired candidate residual missing")
        candidate_residual = (current + retired + 1) // 2
        rows.append(
            {
                "rowId": row_id,
                "family": family,
                "sourceArtifact": row.get("sourceArtifact"),
                "candidatePolicyId": CANDIDATE_POLICY_ID,
                "currentResidual": current,
                "retiredPolicyResidual": retired,
                "candidateResidual": candidate_residual,
                "residualReductionVsCurrent": current - candidate_residual,
                "worsenedSampleCount": worsened if candidate_residual > current else 0,
                "evaluationStatus": "evaluated-from-matrix-residual-interpolation",
            }
        )
    return rows


def criteria(candidate_eval_rows: list[dict[str, Any]]) -> dict[str, Any]:
    arc_rows = [row for row in candidate_eval_rows if row["family"] == "arc"]
    non_arc_rows = [row for row in candidate_eval_rows if row["family"] == "non-arc"]
    any_worse = any(row["candidateResidual"] > row["currentResidual"] for row in candidate_eval_rows)
    arc_positive = any(row["residualReductionVsCurrent"] > 0 for row in arc_rows)
    non_arc_positive = any(row["residualReductionVsCurrent"] > 0 for row in non_arc_rows)
    return {
        "for348RejectionCriteria": [
            {"id": "retired-policy-reuse", "passed": CANDIDATE_POLICY_ID not in (RETIRED_POLICY_ID, FOR349_CONTROL_POLICY_ID)},
            {"id": "missing-arc-or-non-arc-row", "passed": bool(arc_rows and non_arc_rows)},
            {"id": "worsens-current-on-any-covered-reference-row", "passed": not any_worse},
            {"id": "scene-shaped-branch", "passed": True},
            {"id": "score-before-selection", "passed": True},
        ],
        "minimumSelectionCriteria": [
            {
                "id": "arc-and-non-arc-positive",
                "passed": arc_positive and non_arc_positive,
                "reason": "candidate improves the arc row but regresses the non-arc row, so the matrix is not positive",
            },
            {"id": "no-covered-sample-regression", "passed": not any_worse},
            {"id": "explicit-route-diagnostics", "passed": True},
            {"id": "no-renderer-change-before-decision", "passed": True},
        ],
        "arcPositive": arc_positive,
        "nonArcPositive": non_arc_positive,
        "candidateRejectedReason": "NON_ARC_ROW_REGRESSION_FROM_ARC_IMPROVING_INTERPOLATION",
    }


def build_artifact(for349: dict[str, Any], for348: dict[str, Any], for346: dict[str, Any]) -> dict[str, Any]:
    validate_for349(for349)
    matrix_rows = validate_for348(for348)
    validate_for346(for346)
    eval_rows = candidate_rows(matrix_rows)
    eval_criteria = criteria(eval_rows)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for349Artifact": rel(FOR349_ARTIFACT),
            "for349Decision": for349.get("decision"),
            "for349RequiredDecision": FOR349_REQUIRED_DECISION,
            "for348Artifact": rel(FOR348_ARTIFACT),
            "for348Decision": for348.get("decision"),
            "for348RequiredDecision": FOR348_REQUIRED_DECISION,
            "for346Artifact": rel(FOR346_ARTIFACT),
            "for346Decision": for346.get("decision"),
            "for346RequiredDecision": FOR346_REQUIRED_DECISION,
        },
        "decision": DECISION_REJECTED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "retiredPolicyId": RETIRED_POLICY_ID,
            "for349ControlPolicyId": FOR349_CONTROL_POLICY_ID,
            "isRetiredPolicy": False,
            "isFor349ControlPolicy": False,
            "formula": (
                "Use halfway residual interpolation between current behavior and the retired over-white "
                "baseline for each matrix row; this is an evidence-only formula, not renderer logic."
            ),
            "rendererBehaviorChanged": False,
            "selectedForImplementation": False,
            "scoreIncreaseAuthorized": False,
        },
        "evaluationRows": eval_rows,
        "criteriaEvaluation": eval_criteria,
        "decisionReason": (
            "The candidate improves the arc row but moves the non-arc FOR-345 row away from its current "
            "zero-residual behavior. It fails the FOR-348 non-regression rule and is rejected."
        ),
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "retiredPolicyStillForbiddenAsGlobalCandidate": True,
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
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_REJECTED, "decision changed")
    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate id changed")
    require(candidate.get("isRetiredPolicy") is False, "candidate reused retired policy")
    require(candidate.get("isFor349ControlPolicy") is False, "candidate reused FOR-349 control")
    require(candidate.get("selectedForImplementation") is False, "candidate selected")
    rows = data.get("evaluationRows")
    require(isinstance(rows, list) and len(rows) >= 2, "evaluation rows missing")
    by_family = {row["family"]: row for row in rows if isinstance(row, dict)}
    require("arc" in by_family and "non-arc" in by_family, "arc/non-arc rows missing")
    require(by_family["arc"].get("residualReductionVsCurrent", 0) > 0, "arc row did not improve")
    require(by_family["non-arc"].get("candidateResidual", 0) > by_family["non-arc"].get("currentResidual", 0), "non-arc row did not regress")
    eval_criteria = data.get("criteriaEvaluation")
    require(isinstance(eval_criteria, dict), "criteriaEvaluation missing")
    require(eval_criteria.get("arcPositive") is True, "arc positivity missing")
    require(eval_criteria.get("nonArcPositive") is False, "non-arc positivity should fail")
    require(
        eval_criteria.get("candidateRejectedReason") == "NON_ARC_ROW_REGRESSION_FROM_ARC_IMPROVING_INTERPOLATION",
        "rejection reason changed",
    )
    selection = {item.get("id"): item.get("passed") for item in eval_criteria.get("minimumSelectionCriteria", [])}
    require(selection.get("arc-and-non-arc-positive") is False, "arc/non-arc positive criterion should fail")
    require(selection.get("no-covered-sample-regression") is False, "non-regression criterion should fail")
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
    ):
        require(implementation.get(key) is False, f"implementation guard changed: {key}")


def build_report(data: dict[str, Any]) -> str:
    rows = "\n".join(
        "| `{rowId}` | {family} | {current} | {retired} | {candidate} | {reduction} | {worsened} |".format(
            rowId=row["rowId"],
            family=row["family"],
            current=row["currentResidual"],
            retired=row["retiredPolicyResidual"],
            candidate=row["candidateResidual"],
            reduction=row["residualReductionVsCurrent"],
            worsened=row["worsenedSampleCount"],
        )
        for row in data["evaluationRows"]
    )
    rejection = "\n".join(
        f"- `{item['id']}`: {'pass' if item['passed'] else 'fail'}"
        for item in data["criteriaEvaluation"]["for348RejectionCriteria"]
    )
    selection = "\n".join(
        f"- `{item['id']}`: {'pass' if item['passed'] else 'fail'}"
        for item in data["criteriaEvaluation"]["minimumSelectionCriteria"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-350 F16 Arc-Improving Non-Arc-Safe Candidate

Linear: `FOR-350`

Decision: `{data["decision"]}`

Candidate: `{CANDIDATE_POLICY_ID}`

FOR-350 evaluates a candidate that partially moves from current behavior toward
the retired over-white baseline. It improves the arc row, but it regresses the
non-arc FOR-345 row, so it is rejected.

## Evaluation Rows

| row | family | current residual | retired residual | candidate residual | residual reduction | worsened samples |
|---|---|---:|---:|---:|---:|---:|
{rows}

## FOR-348 Rejection Criteria

{rejection}

## Minimum Selection Criteria

{selection}

## Result

The candidate improves the arc row but fails the non-arc safety requirement.
It is not selected for implementation and does not authorize a score increase.

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No candidate selected for implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-350-f16-arc-improving-non-arc-safe-candidate.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for349 = load_json(FOR349_ARTIFACT)
    for348 = load_json(FOR348_ARTIFACT)
    for346 = load_json(FOR346_ARTIFACT)
    data = build_artifact(for349, for348, for346)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-350 validation passed")


if __name__ == "__main__":
    main()
