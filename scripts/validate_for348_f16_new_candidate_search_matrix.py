#!/usr/bin/env python3
"""Validate the FOR-348 F16 new-candidate search matrix."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-348"
SCENE_ID = "f16-new-candidate-search-matrix-for348"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-348-f16-new-candidate-search-matrix.md"

SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-f16-arc-non-arc-candidate-search-matrix-ticket"
SOURCE_FINDING = "global/kanvas/findings/for-347-f16-post-retirement-route-selected-finding"

FOR347_SCENE_ID = "f16-post-retirement-route-decision-for347"
FOR347_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR347_SCENE_ID / f"{FOR347_SCENE_ID}.json"
)
FOR347_REQUIRED_DECISION = "F16_POST_RETIREMENT_ROUTE_SELECTED"
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

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR341_SCENE_ID / f"{FOR341_SCENE_ID}.json"
)
FOR341_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"
DECISION_READY = "F16_NEW_CANDIDATE_SEARCH_MATRIX_READY"
DECISION_INPUT_INVALID = "F16_NEW_CANDIDATE_SEARCH_MATRIX_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-348 validation failed: {message}")


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


def validate_for347(for347: dict[str, Any]) -> None:
    require(for347.get("linear") == "FOR-347", "FOR-347 artifact identity changed")
    require(for347.get("decision") == FOR347_REQUIRED_DECISION, "FOR-347 decision changed")
    require(for347.get("recommendedRoute") == FOR347_REQUIRED_ROUTE, "FOR-347 selected route changed")
    decision_only = for347.get("decisionOnly")
    require(isinstance(decision_only, dict), "FOR-347 decisionOnly missing")
    require(decision_only.get("scoreIncreaseAuthorized") is False, "FOR-347 authorizes score increase")
    require(decision_only.get("rendererBehaviorChangeAuthorized") is False, "FOR-347 authorizes renderer change")


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 retirement decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-346 candidate policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 candidate is no longer retired")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 candidate reopened globally")
    require(candidate.get("mayRaiseGlobalF16Score") is False, "FOR-346 candidate can raise score")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 rejection decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row must be non-arc")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 row must stay comparable")
    require(row.get("rec2020F16SrcOverOrBlendSignal") is True, "FOR-345 row signal changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("candidateResidual") == 111, "FOR-345 candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 worsened sample count changed")
    return residuals


def validate_for341(for341: dict[str, Any]) -> dict[str, Any]:
    require(for341.get("linear") == "FOR-341", "FOR-341 artifact identity changed")
    require(for341.get("decision") == FOR341_REQUIRED_DECISION, "FOR-341 decision changed")
    policy = for341.get("policyDecision")
    require(isinstance(policy, dict), "FOR-341 policyDecision missing")
    require(policy.get("sourceCandidatePolicyId") == CANDIDATE_POLICY_ID, "FOR-341 source candidate changed")
    require(policy.get("codeChangeAllowedInThisTicket") is False, "FOR-341 allowed code changes in evidence ticket")
    totals = for341.get("residualTotals")
    require(isinstance(totals, dict), "FOR-341 residualTotals missing")
    require(totals.get("sampleCount") == 12, "FOR-341 sample count changed")
    require(totals.get("strokeSampleCount") == 10, "FOR-341 stroke sample count changed")
    require(totals.get("currentVsOverWhiteResidual") == 375, "FOR-341 current residual changed")
    require(totals.get("candidateVsOverWhiteResidual") == 0, "FOR-341 candidate residual changed")
    cells = for341.get("targetCells")
    require(isinstance(cells, list) and len(cells) == 2, "FOR-341 target cells changed")
    for cell in cells:
        require(cell.get("reference", {}).get("accepted") is True, "FOR-341 reference no longer accepted")
        require(cell.get("reference", {}).get("fullGmCrop") is False, "FOR-341 uses full GM crop")
        require(cell.get("reference", {}).get("selectedCellExtrapolationUsed") is False, "FOR-341 uses selected-cell extrapolation")
    return totals


def build_rows(for341: dict[str, Any], for345: dict[str, Any]) -> list[dict[str, Any]]:
    arc_totals = validate_for341(for341)
    non_arc_residuals = validate_for345(for345)
    return [
        {
            "rowId": "arc-circular-arcs-stroke-butt-adjacent-for341",
            "family": "arc",
            "sourceArtifact": rel(FOR341_ARTIFACT),
            "referenceStatus": "available-isolated-skia-adjacent-cell-render",
            "currentStatus": "available-current-for339-export-rgba",
            "candidateStatus": "historical-retired-candidate-baseline-only",
            "retiredCandidatePolicyId": CANDIDATE_POLICY_ID,
            "sampleCount": arc_totals.get("sampleCount"),
            "currentResidual": arc_totals.get("currentVsOverWhiteResidual"),
            "candidateResidual": arc_totals.get("candidateVsOverWhiteResidual"),
            "worsenedSampleCount": 0,
            "dataComparableForSearchMatrix": True,
            "remainingGaps": [
                "The candidate that fits this arc row is retired globally and cannot be selected.",
                "A new formula must be evaluated against this row and at least one non-arc row.",
            ],
        },
        {
            "rowId": "non-arc-rec2020-f16-src-over-rect-for345",
            "family": "non-arc",
            "sourceArtifact": rel(FOR345_ARTIFACT),
            "referenceStatus": "available-isolated-skia-non-arc-rec2020-f16-src-over-reference",
            "currentStatus": "available-current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples",
            "candidateStatus": "historical-retired-candidate-rejected",
            "retiredCandidatePolicyId": CANDIDATE_POLICY_ID,
            "sampleCount": non_arc_residuals.get("sampleCount"),
            "currentResidual": non_arc_residuals.get("currentResidual"),
            "candidateResidual": non_arc_residuals.get("candidateResidual"),
            "worsenedSampleCount": non_arc_residuals.get("worsenedSampleCount"),
            "dataComparableForSearchMatrix": True,
            "remainingGaps": [
                "Only one non-arc row exists in this matrix.",
                "A future candidate should add more non-arc rows before any score or renderer decision.",
            ],
        },
    ]


def rejection_criteria() -> list[dict[str, Any]]:
    return [
        {
            "id": "retired-policy-reuse",
            "rejectWhen": f"candidate policy id is `{CANDIDATE_POLICY_ID}` or algebraically identical without new evidence",
        },
        {
            "id": "missing-arc-or-non-arc-row",
            "rejectWhen": "candidate lacks at least one comparable arc row and one comparable non-arc row",
        },
        {
            "id": "worsens-current-on-any-covered-reference-row",
            "rejectWhen": "candidate residual exceeds current residual on any covered comparable sample group",
        },
        {
            "id": "scene-shaped-branch",
            "rejectWhen": "candidate requires fixture, coordinate, selected-cell, or full-GM-crop branching",
        },
        {
            "id": "score-before-selection",
            "rejectWhen": "candidate is used to raise F16 score before selection evidence is complete",
        },
    ]


def selection_criteria() -> list[dict[str, Any]]:
    return [
        {
            "id": "arc-and-non-arc-positive",
            "requires": "positive residual evidence on both arc and non-arc comparable rows",
        },
        {
            "id": "no-covered-sample-regression",
            "requires": "zero covered samples worsened versus current across the required matrix",
        },
        {
            "id": "explicit-route-diagnostics",
            "requires": "stable diagnostics for unsupported or out-of-domain routes",
        },
        {
            "id": "no-renderer-change-before-decision",
            "requires": "candidate remains evidence-only until a later implementation ticket",
        },
    ]


def build_artifact(for347: dict[str, Any], for346: dict[str, Any], for345: dict[str, Any], for341: dict[str, Any]) -> dict[str, Any]:
    validate_for347(for347)
    validate_for346(for346)
    rows = build_rows(for341, for345)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for347Artifact": rel(FOR347_ARTIFACT),
            "for347Decision": for347.get("decision"),
            "for347RequiredDecision": FOR347_REQUIRED_DECISION,
            "for347Route": for347.get("recommendedRoute"),
            "for347RequiredRoute": FOR347_REQUIRED_ROUTE,
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
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidatePolicy": {
            "retiredPolicyId": CANDIDATE_POLICY_ID,
            "retiredPolicyStillForbiddenAsGlobalCandidate": True,
            "newCandidateSelectedInThisTicket": False,
            "scoreIncreaseAuthorized": False,
            "globalF16MigrationAuthorized": False,
        },
        "matrix": {
            "rowCount": len(rows),
            "arcRowCount": sum(1 for row in rows if row["family"] == "arc"),
            "nonArcRowCount": sum(1 for row in rows if row["family"] == "non-arc"),
            "rows": rows,
        },
        "rejectionCriteriaBeforeEvaluation": rejection_criteria(),
        "minimumSelectionCriteria": selection_criteria(),
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
        },
        "implementation": {
            "evidenceOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
            "newCandidateSelected": False,
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
    require(data.get("decision") == DECISION_READY, "decision changed")

    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for347Decision") == FOR347_REQUIRED_DECISION, "FOR-347 gate not encoded")
    require(input_validation.get("for347Route") == FOR347_REQUIRED_ROUTE, "FOR-347 route not encoded")
    require(input_validation.get("for346Decision") == FOR346_REQUIRED_DECISION, "FOR-346 gate not encoded")
    require(input_validation.get("for345Decision") == FOR345_REQUIRED_DECISION, "FOR-345 gate not encoded")

    candidate = data.get("candidatePolicy")
    require(isinstance(candidate, dict), "candidatePolicy missing")
    require(candidate.get("retiredPolicyId") == CANDIDATE_POLICY_ID, "retired policy id changed")
    require(candidate.get("retiredPolicyStillForbiddenAsGlobalCandidate") is True, "retired policy global ban missing")
    require(candidate.get("newCandidateSelectedInThisTicket") is False, "new candidate selected")
    require(candidate.get("scoreIncreaseAuthorized") is False, "score increase authorized")

    matrix = data.get("matrix")
    require(isinstance(matrix, dict), "matrix missing")
    require(matrix.get("rowCount") == 2, "matrix row count changed")
    require(matrix.get("arcRowCount") >= 1, "matrix lacks arc row")
    require(matrix.get("nonArcRowCount") >= 1, "matrix lacks non-arc row")
    rows = matrix.get("rows")
    require(isinstance(rows, list) and len(rows) == 2, "matrix rows missing")
    by_id = {row.get("rowId"): row for row in rows if isinstance(row, dict)}
    arc = by_id.get("arc-circular-arcs-stroke-butt-adjacent-for341")
    non_arc = by_id.get("non-arc-rec2020-f16-src-over-rect-for345")
    require(isinstance(arc, dict), "arc row missing")
    require(isinstance(non_arc, dict), "non-arc row missing")
    require(arc.get("currentResidual") == 375, "arc current residual changed")
    require(arc.get("candidateResidual") == 0, "arc candidate residual changed")
    require(non_arc.get("currentResidual") == 0, "non-arc current residual changed")
    require(non_arc.get("candidateResidual") == 111, "non-arc candidate residual changed")
    require(non_arc.get("worsenedSampleCount") == 3, "non-arc worsened count changed")

    rejection_ids = {item.get("id") for item in data.get("rejectionCriteriaBeforeEvaluation", [])}
    require("retired-policy-reuse" in rejection_ids, "retired-policy rejection criterion missing")
    require("missing-arc-or-non-arc-row" in rejection_ids, "coverage rejection criterion missing")
    selection_ids = {item.get("id") for item in data.get("minimumSelectionCriteria", [])}
    require("arc-and-non-arc-positive" in selection_ids, "arc/non-arc selection criterion missing")
    require("no-covered-sample-regression" in selection_ids, "regression selection criterion missing")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    for key in (
        "rendererBehaviorChanged",
        "newColorPolicyImplemented",
        "newCandidateSelected",
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
    rows = data["matrix"]["rows"]
    matrix_rows = "\n".join(
        "| `{rowId}` | {family} | {reference} | {current} | {candidate} | {currentResidual} | {candidateResidual} | {worsened} |".format(
            rowId=row["rowId"],
            family=row["family"],
            reference=row["referenceStatus"],
            current=row["currentStatus"],
            candidate=row["candidateStatus"],
            currentResidual=row["currentResidual"],
            candidateResidual=row["candidateResidual"],
            worsened=row["worsenedSampleCount"],
        )
        for row in rows
    )
    rejection = "\n".join(
        f"- `{item['id']}`: reject when {item['rejectWhen']}"
        for item in data["rejectionCriteriaBeforeEvaluation"]
    )
    selection = "\n".join(
        f"- `{item['id']}`: {item['requires']}" for item in data["minimumSelectionCriteria"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-348 F16 New-Candidate Search Matrix

Linear: `FOR-348`

Decision: `{data["decision"]}`

FOR-348 starts the `new-candidate-search` route selected by FOR-347. It builds
an arc plus non-arc matrix for future candidate evaluation. It does not select
a new candidate, change renderer behavior, or raise score.

## Result

The search matrix is ready with `{data["matrix"]["arcRowCount"]}` arc row and
`{data["matrix"]["nonArcRowCount"]}` non-arc row.

`{CANDIDATE_POLICY_ID}` remains forbidden as a global candidate.

## Matrix Rows

| row | family | reference | current | candidate | current residual | candidate residual | worsened samples |
|---|---|---|---|---|---:|---:|---:|
{matrix_rows}

## Rejection Criteria Before Evaluation

{rejection}

## Minimum Selection Criteria

{selection}

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No new candidate selected.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for348_f16_new_candidate_search_matrix.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-348-f16-new-candidate-search-matrix.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for347 = load_json(FOR347_ARTIFACT)
    for346 = load_json(FOR346_ARTIFACT)
    for345 = load_json(FOR345_ARTIFACT)
    for341 = load_json(FOR341_ARTIFACT)
    data = build_artifact(for347, for346, for345, for341)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-348 validation passed")


if __name__ == "__main__":
    main()
