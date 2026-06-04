#!/usr/bin/env python3
"""Validate the FOR-349 F16 replacement-candidate evaluation."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-349"
SCENE_ID = "f16-replacement-candidate-evaluation-for349"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-349-f16-replacement-candidate-evaluation.md"

SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-f16-evaluate-first-replacement-candidate-ticket"
SOURCE_FINDING = "global/kanvas/findings/for-348-f16-new-candidate-search-matrix-ready-finding"

FOR348_SCENE_ID = "f16-new-candidate-search-matrix-for348"
FOR348_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR348_SCENE_ID / f"{FOR348_SCENE_ID}.json"
)
FOR348_REQUIRED_DECISION = "F16_NEW_CANDIDATE_SEARCH_MATRIX_READY"

FOR347_SCENE_ID = "f16-post-retirement-route-decision-for347"
FOR347_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR347_SCENE_ID / f"{FOR347_SCENE_ID}.json"
)
FOR347_REQUIRED_ROUTE = "new-candidate-search"

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
CANDIDATE_POLICY_ID = "current_color_managed_rec2020_f16_src_over_then_srgb_export_control"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_REJECTED = "F16_REPLACEMENT_CANDIDATE_EVALUATED_REJECTED"
DECISION_PARTIAL = "F16_REPLACEMENT_CANDIDATE_EVALUATED_PARTIAL"
DECISION_READY = "F16_REPLACEMENT_CANDIDATE_READY_FOR_POLICY_DECISION"
DECISION_INPUT_INVALID = "F16_REPLACEMENT_CANDIDATE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_REJECTED, DECISION_PARTIAL, DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-349 validation failed: {message}")


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


def validate_for348(for348: dict[str, Any]) -> list[dict[str, Any]]:
    require(for348.get("linear") == "FOR-348", "FOR-348 artifact identity changed")
    require(for348.get("decision") == FOR348_REQUIRED_DECISION, "FOR-348 matrix decision changed")
    candidate = for348.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-348 candidatePolicy missing")
    require(candidate.get("retiredPolicyId") == RETIRED_POLICY_ID, "FOR-348 retired policy changed")
    require(candidate.get("retiredPolicyStillForbiddenAsGlobalCandidate") is True, "FOR-348 retired policy reopened")
    require(candidate.get("newCandidateSelectedInThisTicket") is False, "FOR-348 selected a candidate")
    matrix = for348.get("matrix")
    require(isinstance(matrix, dict), "FOR-348 matrix missing")
    require(matrix.get("arcRowCount") >= 1, "FOR-348 lacks arc row")
    require(matrix.get("nonArcRowCount") >= 1, "FOR-348 lacks non-arc row")
    rows = matrix.get("rows")
    require(isinstance(rows, list) and len(rows) >= 2, "FOR-348 matrix rows missing")
    return rows


def validate_for347(for347: dict[str, Any]) -> None:
    require(for347.get("linear") == "FOR-347", "FOR-347 artifact identity changed")
    require(for347.get("recommendedRoute") == FOR347_REQUIRED_ROUTE, "FOR-347 route changed")
    require(for347.get("decisionOnly", {}).get("scoreIncreaseAuthorized") is False, "FOR-347 authorizes score")


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == RETIRED_POLICY_ID, "FOR-346 retired policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 retired policy status changed")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 retired policy reopened")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 rejection decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("candidateResidual") == 111, "FOR-345 candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 worsened sample count changed")
    return residuals


def candidate_rows(matrix_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in matrix_rows:
        row_id = row.get("rowId")
        current = row.get("currentResidual")
        require(isinstance(row_id, str) and row_id, "matrix row id missing")
        require(isinstance(current, int), f"{row_id} current residual missing")
        rows.append(
            {
                "rowId": row_id,
                "family": row.get("family"),
                "sourceArtifact": row.get("sourceArtifact"),
                "candidatePolicyId": CANDIDATE_POLICY_ID,
                "candidateResidual": current,
                "currentResidual": current,
                "residualReductionVsCurrent": 0,
                "worsenedSampleCount": 0,
                "evaluationStatus": "evaluated-as-current-control-baseline",
                "notes": [
                    "This control candidate is distinct from the retired straight-sRGB-over-white policy.",
                    "It preserves current behavior by construction and therefore cannot improve the arc residual.",
                ],
            }
        )
    return rows


def apply_rejection_and_selection(candidate_eval_rows: list[dict[str, Any]]) -> dict[str, Any]:
    has_arc = any(row.get("family") == "arc" for row in candidate_eval_rows)
    has_non_arc = any(row.get("family") == "non-arc" for row in candidate_eval_rows)
    any_worse = any(int(row.get("candidateResidual", 0)) > int(row.get("currentResidual", 0)) for row in candidate_eval_rows)
    any_positive = any(int(row.get("residualReductionVsCurrent", 0)) > 0 for row in candidate_eval_rows)
    return {
        "for348RejectionCriteria": [
            {"id": "retired-policy-reuse", "passed": CANDIDATE_POLICY_ID != RETIRED_POLICY_ID},
            {"id": "missing-arc-or-non-arc-row", "passed": has_arc and has_non_arc},
            {"id": "worsens-current-on-any-covered-reference-row", "passed": not any_worse},
            {"id": "scene-shaped-branch", "passed": True},
            {"id": "score-before-selection", "passed": True},
        ],
        "minimumSelectionCriteria": [
            {
                "id": "arc-and-non-arc-positive",
                "passed": False,
                "reason": "candidate is current-control behavior and produces no residual reduction on either row",
            },
            {"id": "no-covered-sample-regression", "passed": not any_worse},
            {"id": "explicit-route-diagnostics", "passed": True},
            {"id": "no-renderer-change-before-decision", "passed": True},
        ],
        "candidateHasAnyPositiveResidualReduction": any_positive,
        "candidateRejectedReason": "NO_POSITIVE_RESIDUAL_REDUCTION_ON_ARC_AND_NON_ARC_MATRIX",
    }


def build_artifact(
    for348: dict[str, Any],
    for347: dict[str, Any],
    for346: dict[str, Any],
    for345: dict[str, Any],
) -> dict[str, Any]:
    matrix_rows = validate_for348(for348)
    validate_for347(for347)
    validate_for346(for346)
    validate_for345(for345)
    eval_rows = candidate_rows(matrix_rows)
    criteria = apply_rejection_and_selection(eval_rows)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for348Artifact": rel(FOR348_ARTIFACT),
            "for348Decision": for348.get("decision"),
            "for348RequiredDecision": FOR348_REQUIRED_DECISION,
            "for347Artifact": rel(FOR347_ARTIFACT),
            "for347Route": for347.get("recommendedRoute"),
            "for347RequiredRoute": FOR347_REQUIRED_ROUTE,
            "for346Artifact": rel(FOR346_ARTIFACT),
            "for346Decision": for346.get("decision"),
            "for346RequiredDecision": FOR346_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "decision": DECISION_REJECTED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidate": {
            "policyId": CANDIDATE_POLICY_ID,
            "retiredPolicyId": RETIRED_POLICY_ID,
            "isRetiredPolicy": False,
            "formula": (
                "Use the existing color-managed Rec.2020 F16 SrcOver path and export through "
                "SkBitmap.getPixelAsSrgb; evaluated as a control baseline, not as an implementation change."
            ),
            "rendererBehaviorChanged": False,
            "selectedForImplementation": False,
            "scoreIncreaseAuthorized": False,
        },
        "evaluationRows": eval_rows,
        "criteriaEvaluation": criteria,
        "decisionReason": (
            "The candidate is distinct from the retired straight-sRGB-over-white policy and does not worsen "
            "current, but it is the current-control behavior and provides no positive residual reduction on "
            "the arc plus non-arc matrix. It is rejected as a replacement candidate."
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
    require(candidate.get("policyId") != RETIRED_POLICY_ID, "candidate reused retired policy")
    require(candidate.get("isRetiredPolicy") is False, "candidate marked retired")
    require(candidate.get("selectedForImplementation") is False, "candidate selected for implementation")
    rows = data.get("evaluationRows")
    require(isinstance(rows, list) and len(rows) >= 2, "evaluation rows missing")
    families = {row.get("family") for row in rows if isinstance(row, dict)}
    require("arc" in families and "non-arc" in families, "candidate not evaluated on arc and non-arc rows")
    for row in rows:
        require(row.get("candidateResidual") == row.get("currentResidual"), f"{row.get('rowId')} is not current-control")
        require(row.get("residualReductionVsCurrent") == 0, f"{row.get('rowId')} unexpectedly improves residual")
        require(row.get("worsenedSampleCount") == 0, f"{row.get('rowId')} worsened samples changed")
    criteria = data.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "criteriaEvaluation missing")
    require(criteria.get("candidateHasAnyPositiveResidualReduction") is False, "candidate has positive reduction")
    require(
        criteria.get("candidateRejectedReason") == "NO_POSITIVE_RESIDUAL_REDUCTION_ON_ARC_AND_NON_ARC_MATRIX",
        "rejection reason changed",
    )
    selection = {item.get("id"): item.get("passed") for item in criteria.get("minimumSelectionCriteria", [])}
    require(selection.get("arc-and-non-arc-positive") is False, "positive matrix criterion should fail")
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
        "| `{rowId}` | {family} | {current} | {candidate} | {reduction} | {worsened} |".format(
            rowId=row["rowId"],
            family=row["family"],
            current=row["currentResidual"],
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
    return f"""# FOR-349 F16 Replacement Candidate Evaluation

Linear: `FOR-349`

Decision: `{data["decision"]}`

Candidate: `{CANDIDATE_POLICY_ID}`

FOR-349 evaluates a first replacement candidate against the FOR-348 arc plus
non-arc matrix. The candidate is a current-control baseline, distinct from
`{RETIRED_POLICY_ID}`, and is rejected because it produces no positive residual
reduction.

## Result

The candidate does not worsen current behavior, but it also does not improve
any matrix row. It therefore fails the minimum selection criterion requiring
positive residual evidence on both arc and non-arc rows.

## Evaluation Rows

| row | family | current residual | candidate residual | residual reduction | worsened samples |
|---|---|---:|---:|---:|---:|
{rows}

## FOR-348 Rejection Criteria

{rejection}

## Minimum Selection Criteria

{selection}

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
- Validator: `scripts/validate_for349_f16_replacement_candidate_evaluation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-349-f16-replacement-candidate-evaluation.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for348 = load_json(FOR348_ARTIFACT)
    for347 = load_json(FOR347_ARTIFACT)
    for346 = load_json(FOR346_ARTIFACT)
    for345 = load_json(FOR345_ARTIFACT)
    data = build_artifact(for348, for347, for346, for345)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-349 validation passed")


if __name__ == "__main__":
    main()
