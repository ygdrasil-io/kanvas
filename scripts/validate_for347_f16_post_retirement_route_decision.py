#!/usr/bin/env python3
"""Validate the FOR-347 F16 post-retirement route decision."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-347"
SCENE_ID = "f16-post-retirement-route-decision-for347"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-347-f16-post-retirement-route-decision.md"

SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-f16-post-retirement-route-decision-ticket"
SOURCE_FINDING = "global/kanvas/findings/for-346-f16-global-color-policy-candidate-retired-finding"

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

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"
DECISION_SELECTED = "F16_POST_RETIREMENT_ROUTE_SELECTED"
DECISION_INPUT_INVALID = "F16_POST_RETIREMENT_ROUTE_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_SELECTED, DECISION_INPUT_INVALID]
RECOMMENDED_ROUTE = "new-candidate-search"

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for347_f16_post_retirement_route_decision.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-347 validation failed: {message}")


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


def validate_for346(for346: dict[str, Any]) -> dict[str, Any]:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 retirement decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-346 candidate policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 candidate is no longer retired")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 candidate reopened globally")
    require(candidate.get("mayRaiseGlobalF16Score") is False, "FOR-346 candidate can raise score")
    require(candidate.get("mayAuthorizeGlobalF16Migration") is False, "FOR-346 candidate can authorize migration")
    boundary = for346.get("boundary")
    require(isinstance(boundary, dict), "FOR-346 boundary missing")
    require(boundary.get("id") == BOUNDARY_ID, "FOR-346 boundary id changed")
    require(boundary.get("rendererBehaviorChanged") is False, "FOR-346 changed renderer behavior")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "FOR-346 allows global F16 migration")
    require(boundary.get("candidateRetiredAsGlobalPolicy") is True, "FOR-346 retirement flag missing")
    evidence = for346.get("evidence")
    require(isinstance(evidence, dict), "FOR-346 evidence missing")
    require(evidence.get("currentResidual") == 0, "FOR-346 current residual changed")
    require(evidence.get("candidateResidual") == 111, "FOR-346 candidate residual changed")
    require(evidence.get("worsenedSampleCount") == 3, "FOR-346 worsened sample count changed")
    return evidence


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 rejection decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row must stay non-arc")
    require(row.get("excludedScene") == "circular_arcs_stroke_butt", "FOR-345 row exclusion changed")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 row must stay comparable")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("candidateResidual") == 111, "FOR-345 candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 worsened sample count changed")
    return residuals


def route_matrix() -> list[dict[str, Any]]:
    return [
        {
            "id": "localized-policy",
            "status": "not-selected",
            "scorePotential": "limited",
            "risk": "medium",
            "reason": (
                "A local policy can be valid only with strict scope and route diagnostics, "
                "but it risks reintroducing scene-shaped behavior if used as the immediate score path."
            ),
            "childTicketSeeds": [
                "Define exact local policy domain and route diagnostics.",
                "Collect positive reference/current/candidate rows for the local domain.",
            ],
        },
        {
            "id": "stable-fallback",
            "status": "not-selected",
            "scorePotential": "none",
            "risk": "low",
            "reason": (
                "A stable fallback is the safest renderer behavior, but it does not move the score objective."
            ),
            "childTicketSeeds": [
                "Document unsupported global F16 color-policy migration route.",
                "Assert refusal diagnostics for unsupported broad migration attempts.",
            ],
        },
        {
            "id": RECOMMENDED_ROUTE,
            "status": "selected",
            "scorePotential": "highest",
            "risk": "controlled-by-evidence",
            "reason": (
                "After the global candidate was retired, the only score-oriented route is a new candidate "
                "search that starts with arc and non-arc evidence instead of generalizing from one scene."
            ),
            "childTicketSeeds": [
                "Create an arc plus non-arc F16 candidate-search matrix with reference/current/candidate rows.",
                "Define candidate rejection criteria before evaluating new formulas.",
                "Select a replacement candidate only after positive evidence across both families.",
            ],
        },
    ]


def build_artifact(for346: dict[str, Any], for345: dict[str, Any]) -> dict[str, Any]:
    evidence346 = validate_for346(for346)
    residuals345 = validate_for345(for345)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for346Artifact": rel(FOR346_ARTIFACT),
            "for346Decision": for346.get("decision"),
            "for346RequiredDecision": FOR346_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "decision": DECISION_SELECTED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "recommendedRoute": RECOMMENDED_ROUTE,
        "candidatePolicy": {
            "policyId": CANDIDATE_POLICY_ID,
            "globalStatus": "retired",
            "globalCandidateOpen": False,
            "stillForbiddenAsGlobalCandidate": True,
            "mayRaiseGlobalF16Score": False,
            "mayAuthorizeGlobalF16Migration": False,
        },
        "evidence": {
            "for346CurrentResidual": evidence346.get("currentResidual"),
            "for346CandidateResidual": evidence346.get("candidateResidual"),
            "for346WorsenedSampleCount": evidence346.get("worsenedSampleCount"),
            "for345CurrentResidual": residuals345.get("currentResidual"),
            "for345CandidateResidual": residuals345.get("candidateResidual"),
            "for345WorsenedSampleCount": residuals345.get("worsenedSampleCount"),
        },
        "routeMatrix": route_matrix(),
        "decisionOnly": {
            "childTicketsCreatedByThisValidator": False,
            "scoreIncreaseAuthorized": False,
            "rendererBehaviorChangeAuthorized": False,
            "globalF16MigrationAuthorized": False,
        },
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
        },
        "implementation": {
            "decisionOnly": True,
            "rendererBehaviorChanged": False,
            "newColorPolicyImplemented": False,
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
    require(data.get("decision") == DECISION_SELECTED, "decision changed")
    require(data.get("recommendedRoute") == RECOMMENDED_ROUTE, "recommended route changed")
    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for346Decision") == FOR346_REQUIRED_DECISION, "FOR-346 gate not encoded")
    require(input_validation.get("for345Decision") == FOR345_REQUIRED_DECISION, "FOR-345 gate not encoded")

    candidate = data.get("candidatePolicy")
    require(isinstance(candidate, dict), "candidatePolicy missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy changed")
    require(candidate.get("globalStatus") == "retired", "candidate is not retired")
    require(candidate.get("stillForbiddenAsGlobalCandidate") is True, "candidate global ban missing")
    require(candidate.get("mayRaiseGlobalF16Score") is False, "candidate may raise global score")
    require(candidate.get("mayAuthorizeGlobalF16Migration") is False, "candidate may authorize migration")

    evidence = data.get("evidence")
    require(isinstance(evidence, dict), "evidence missing")
    for prefix in ("for346", "for345"):
        require(evidence.get(f"{prefix}CurrentResidual") == 0, f"{prefix} current residual changed")
        require(evidence.get(f"{prefix}CandidateResidual") == 111, f"{prefix} candidate residual changed")
        require(evidence.get(f"{prefix}WorsenedSampleCount") == 3, f"{prefix} worsened count changed")

    matrix = data.get("routeMatrix")
    require(isinstance(matrix, list) and len(matrix) == 3, "route matrix changed")
    statuses = {item.get("id"): item.get("status") for item in matrix if isinstance(item, dict)}
    require(statuses == {
        "localized-policy": "not-selected",
        "stable-fallback": "not-selected",
        "new-candidate-search": "selected",
    }, "route selection changed")
    for item in matrix:
        require(isinstance(item.get("childTicketSeeds"), list) and item["childTicketSeeds"], "child seeds missing")

    decision_only = data.get("decisionOnly")
    require(isinstance(decision_only, dict), "decisionOnly missing")
    require(decision_only.get("childTicketsCreatedByThisValidator") is False, "validator creates child tickets")
    require(decision_only.get("scoreIncreaseAuthorized") is False, "score increase authorized")
    require(decision_only.get("rendererBehaviorChangeAuthorized") is False, "renderer behavior change authorized")
    require(decision_only.get("globalF16MigrationAuthorized") is False, "global migration authorized")

    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation missing")
    for key in (
        "rendererBehaviorChanged",
        "newColorPolicyImplemented",
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
    matrix_rows = "\n".join(
        "| `{id}` | {status} | {score} | {risk} | {reason} |".format(
            id=item["id"],
            status=item["status"],
            score=item["scorePotential"],
            risk=item["risk"],
            reason=item["reason"],
        )
        for item in data["routeMatrix"]
    )
    child_seed_lines = []
    for item in data["routeMatrix"]:
        child_seed_lines.append(f"### `{item['id']}`")
        child_seed_lines.extend(f"- {seed}" for seed in item["childTicketSeeds"])
        child_seed_lines.append("")
    child_seeds = "\n".join(child_seed_lines).rstrip()
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    evidence = data["evidence"]
    return f"""# FOR-347 F16 Post-Retirement Route Decision

Linear: `FOR-347`

Decision: `{data["decision"]}`

Recommended route: `{data["recommendedRoute"]}`

FOR-347 selects the next route after retiring `{CANDIDATE_POLICY_ID}` as a
global F16 candidate. This is a decision artifact only; it does not change
renderer behavior or authorize a score increase.

## Result

The selected route is `new-candidate-search`. It is the only route that can
support a future score-oriented path without reusing the retired global
candidate or generalizing from one scene.

`{CANDIDATE_POLICY_ID}` remains forbidden as a global candidate.

## Preserved Evidence

| source | current residual | candidate residual | worsened samples |
|---|---:|---:|---:|
| FOR-346 | {evidence["for346CurrentResidual"]} | {evidence["for346CandidateResidual"]} | {evidence["for346WorsenedSampleCount"]} |
| FOR-345 | {evidence["for345CurrentResidual"]} | {evidence["for345CandidateResidual"]} | {evidence["for345WorsenedSampleCount"]} |

## Route Matrix

| route | status | score potential | risk | reason |
|---|---|---|---|---|
{matrix_rows}

## Child Ticket Seeds

{child_seeds}

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No score increase.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for347_f16_post_retirement_route_decision.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-347-f16-post-retirement-route-decision.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for346 = load_json(FOR346_ARTIFACT)
    for345 = load_json(FOR345_ARTIFACT)
    data = build_artifact(for346, for345)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-347 validation passed")


if __name__ == "__main__":
    main()
