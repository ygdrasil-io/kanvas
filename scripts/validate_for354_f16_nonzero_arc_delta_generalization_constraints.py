#!/usr/bin/env python3
"""Validate the FOR-354 F16 arc-delta generalization constraints."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-354"
SCENE_ID = "f16-nonzero-arc-delta-generalization-constraints-for354"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-354-f16-nonzero-arc-delta-generalization-constraints.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-generalize-nonzero-arc-delta-beyond-target-set-ticket"
)
SOURCE_FINDING = "global/kanvas/findings/for-353-f16-nonzero-arc-delta-with-non-arc-guard-partial"

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

RETIRED_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FOR353_CANDIDATE_ID = "nonzero_analytic_arc_delta_with_non_arc_identity_guard"
NEXT_FAMILY_ID = "nonzero_arc_delta_generalized_non_scene_guard_family"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_READY = "F16_NONZERO_ARC_DELTA_GENERALIZATION_CONSTRAINTS_READY"
DECISION_INPUT_INVALID = "F16_NONZERO_ARC_DELTA_GENERALIZATION_CONSTRAINTS_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py",
    "rtk python3 scripts/validate_for353_f16_nonzero_arc_delta_with_non_arc_guard.py",
    "rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-354 validation failed: {message}")


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


def validate_for353(for353: dict[str, Any]) -> dict[str, Any]:
    require(for353.get("linear") == "FOR-353", "FOR-353 artifact identity changed")
    require(for353.get("decision") == FOR353_REQUIRED_DECISION, "FOR-353 decision changed")
    candidate = for353.get("candidate")
    require(isinstance(candidate, dict), "FOR-353 candidate missing")
    require(candidate.get("policyId") == FOR353_CANDIDATE_ID, "FOR-353 candidate changed")
    require(candidate.get("targetSetShaped") is True, "FOR-353 target-set diagnostic changed")
    require(candidate.get("sceneShaped") is True, "FOR-353 scene diagnostic changed")
    require(candidate.get("rendererSelectable") is False, "FOR-353 became renderer selectable")
    require(candidate.get("selectedForImplementation") is False, "FOR-353 selected a candidate")
    evaluation = for353.get("evaluation")
    require(isinstance(evaluation, dict), "FOR-353 evaluation missing")
    arc = evaluation.get("arcEvaluation")
    require(isinstance(arc, dict), "FOR-353 arc evaluation missing")
    require(arc.get("currentResidual") == 375, "FOR-353 current arc residual changed")
    require(arc.get("candidateResidual") == 0, "FOR-353 candidate arc residual changed")
    require(arc.get("residualReductionVsCurrent") == 375, "FOR-353 arc reduction changed")
    return candidate


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
    require(arc.get("currentResidual") == 375, "FOR-351 arc residual changed")
    return constraints


def validate_for348(for348: dict[str, Any]) -> list[dict[str, Any]]:
    require(for348.get("linear") == "FOR-348", "FOR-348 artifact identity changed")
    require(for348.get("decision") == FOR348_REQUIRED_DECISION, "FOR-348 decision changed")
    criteria = for348.get("rejectionCriteriaBeforeEvaluation")
    require(isinstance(criteria, list) and criteria, "FOR-348 rejection criteria missing")
    return criteria


def validate_for346(for346: dict[str, Any]) -> None:
    require(for346.get("linear") == "FOR-346", "FOR-346 artifact identity changed")
    require(for346.get("decision") == FOR346_REQUIRED_DECISION, "FOR-346 decision changed")
    candidate = for346.get("candidatePolicy")
    require(isinstance(candidate, dict), "FOR-346 candidatePolicy missing")
    require(candidate.get("policyId") == RETIRED_POLICY_ID, "FOR-346 retired policy changed")
    require(candidate.get("globalStatus") == "retired", "FOR-346 retired policy status changed")
    require(candidate.get("globalCandidateOpen") is False, "FOR-346 retired policy reopened")


def build_blockers() -> list[dict[str, Any]]:
    return [
        {
            "id": "target-set-dependence",
            "status": "blocks-renderer-selection",
            "evidence": "FOR-353 maps only the captured FOR-351 target samples.",
            "clearanceRequirement": "candidate formula must evaluate from general draw/color/coverage state, not target sample ids",
        },
        {
            "id": "scene-dependence",
            "status": "blocks-renderer-selection",
            "evidence": "FOR-353 declares `sceneShaped=true`.",
            "clearanceRequirement": "candidate must apply across comparable arc evidence without scene identity",
        },
        {
            "id": "broader-non-arc-coverage-missing",
            "status": "blocks-renderer-selection",
            "evidence": "current guard has one FOR-345 non-arc row",
            "clearanceRequirement": "future evidence must add or preserve broader non-arc rows before implementation",
        },
        {
            "id": "non-scene-analytic-rule-missing",
            "status": "blocks-renderer-selection",
            "evidence": "FOR-353 is an artifact probe, not an analytic renderer rule",
            "clearanceRequirement": "candidate must define a renderer-independent rule with stable diagnostics",
        },
    ]


def build_generalization_constraints(constraints: dict[str, Any], rejection_criteria: list[dict[str, Any]]) -> list[dict[str, Any]]:
    non_arc = constraints["nonArcPreserving"]
    arc = constraints["arcResidualTargets"]
    return [
        {
            "id": "preserve-for345-non-arc-zero",
            "requires": "candidate residual on FOR-345 remains 0",
            "requiredValue": non_arc["requiredResidual"],
        },
        {
            "id": "preserve-for345-no-worsened-samples",
            "requires": "candidate worsened sample count on FOR-345 remains 0",
            "requiredValue": non_arc["requiredWorsenedSampleCount"],
        },
        {
            "id": "positive-for341-arc-reduction",
            "requires": "candidate reduces FOR-341 arc residual versus current",
            "currentResidual": arc["currentResidual"],
            "requiredRelation": "candidateResidual < currentResidual",
        },
        {
            "id": "no-target-set-or-scene-shaping",
            "requires": "candidate is not fixture, coordinate, selected-cell, full-GM-crop, scene, or target-set shaped",
            "requiredValue": True,
        },
        {
            "id": "for348-rejection-criteria-preserved",
            "requires": "candidate passes every FOR-348 pre-evaluation rejection rule before selection",
            "sourceCriteriaCount": len(rejection_criteria),
        },
        {
            "id": "renderer-selection-deferred",
            "requires": "candidate remains evidence-only until a later implementation ticket",
            "requiredValue": True,
        },
    ]


def build_artifact(for353: dict[str, Any], for351: dict[str, Any], for348: dict[str, Any], for346: dict[str, Any]) -> dict[str, Any]:
    validate_for353(for353)
    constraints = validate_for351(for351)
    rejection_criteria = validate_for348(for348)
    validate_for346(for346)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
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
        },
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "for353CandidateStatus": {
            "policyId": FOR353_CANDIDATE_ID,
            "targetSetShaped": True,
            "sceneShaped": True,
            "rendererSelectable": False,
            "selectedForImplementation": False,
            "scoreIncreaseAuthorized": False,
        },
        "generalizationBlockers": build_blockers(),
        "generalizationConstraints": build_generalization_constraints(constraints, rejection_criteria),
        "nextCandidateFamily": {
            "id": NEXT_FAMILY_ID,
            "status": "recommended-for-next-evaluation",
            "selectedForImplementation": False,
            "description": (
                "Evaluate a generalized nonzero arc-delta formula that preserves FOR-345 residual 0 "
                "and produces positive FOR-341 reduction without target-set, scene, fixture, or coordinate shaping."
            ),
            "mandatoryRejectWhen": [
                "FOR-345 residual exceeds 0",
                "FOR-345 worsened sample count exceeds 0",
                "candidate requires target sample ids or scene identity",
                "candidate reuses the retired over-white policy globally",
                "candidate authorizes score or renderer implementation before broader evidence",
            ],
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
    require(data.get("decision") == DECISION_READY, "decision changed")
    status = data.get("for353CandidateStatus")
    require(isinstance(status, dict), "FOR-353 status missing")
    require(status.get("targetSetShaped") is True, "FOR-353 target-set diagnostic changed")
    require(status.get("sceneShaped") is True, "FOR-353 scene diagnostic changed")
    require(status.get("rendererSelectable") is False, "FOR-353 became renderer selectable")
    require(status.get("selectedForImplementation") is False, "FOR-353 candidate selected")
    blockers = data.get("generalizationBlockers")
    require(isinstance(blockers, list) and len(blockers) == 4, "blocker list changed")
    constraints = data.get("generalizationConstraints")
    require(isinstance(constraints, list) and len(constraints) >= 6, "constraints missing")
    constraint_ids = {item.get("id") for item in constraints if isinstance(item, dict)}
    for required_id in (
        "preserve-for345-non-arc-zero",
        "preserve-for345-no-worsened-samples",
        "positive-for341-arc-reduction",
        "no-target-set-or-scene-shaping",
        "for348-rejection-criteria-preserved",
        "renderer-selection-deferred",
    ):
        require(required_id in constraint_ids, f"missing constraint: {required_id}")
    family = data.get("nextCandidateFamily")
    require(isinstance(family, dict), "next candidate family missing")
    require(family.get("id") == NEXT_FAMILY_ID, "next candidate family changed")
    require(family.get("selectedForImplementation") is False, "next candidate family selected")
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
    blockers = "\n".join(
        f"- `{item['id']}`: {item['status']} - {item['clearanceRequirement']}"
        for item in data["generalizationBlockers"]
    )
    constraints = "\n".join(
        f"- `{item['id']}`: {item['requires']}"
        for item in data["generalizationConstraints"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-354 F16 Nonzero Arc Delta Generalization Constraints

Linear: `FOR-354`

Decision: `{data["decision"]}`

FOR-354 turns the FOR-353 partial proof into the constraints required for a
future generalized F16 arc-delta candidate. No candidate is selected or
implemented here.

## FOR-353 Status

- Candidate: `{data["for353CandidateStatus"]["policyId"]}`
- Target-set shaped: `{data["for353CandidateStatus"]["targetSetShaped"]}`
- Scene shaped: `{data["for353CandidateStatus"]["sceneShaped"]}`
- Renderer selectable: `{data["for353CandidateStatus"]["rendererSelectable"]}`
- Selected for implementation: `{data["for353CandidateStatus"]["selectedForImplementation"]}`

## Generalization Blockers

{blockers}

## Generalization Constraints

{constraints}

## Next Candidate Family

`{data["nextCandidateFamily"]["id"]}`

{data["nextCandidateFamily"]["description"]}

Status: `{data["nextCandidateFamily"]["status"]}`

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
- Validator: `scripts/validate_for354_f16_nonzero_arc_delta_generalization_constraints.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-354-f16-nonzero-arc-delta-generalization-constraints.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    data = build_artifact(
        load_json(FOR353_ARTIFACT),
        load_json(FOR351_ARTIFACT),
        load_json(FOR348_ARTIFACT),
        load_json(FOR346_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-354 validation passed")


if __name__ == "__main__":
    main()
