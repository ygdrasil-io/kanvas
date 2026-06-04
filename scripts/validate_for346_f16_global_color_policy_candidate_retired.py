#!/usr/bin/env python3
"""Validate the FOR-346 F16 global color-policy candidate retirement decision."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-346"
SCENE_ID = "f16-global-color-policy-candidate-retired-for346"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-346-f16-global-color-policy-candidate-retired.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-retire-straight-srgb-quantized-alpha-src-over-white-global-candidate-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-345-non-arc-rec2020-f16-reference-row-rejects-candidate-finding"
)

FOR343_SCENE_ID = "f16-color-policy-boundary-for343"
FOR343_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR343_SCENE_ID / f"{FOR343_SCENE_ID}.json"
)
FOR343_REQUIRED_DECISION = "F16_COLOR_POLICY_BOUNDARY_READY_FOR_BROADER_EVIDENCE"

FOR344_SCENE_ID = "f16-broader-non-arc-color-policy-for344"
FOR344_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR344_SCENE_ID / f"{FOR344_SCENE_ID}.json"
)
FOR344_REQUIRED_DECISION = "F16_BROADER_NON_ARC_EVIDENCE_PARTIAL_REQUIRES_MORE_REFERENCE_ROWS"

FOR345_SCENE_ID = "non-arc-rec2020-f16-reference-row-for345"
FOR345_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR345_SCENE_ID / f"{FOR345_SCENE_ID}.json"
)
FOR345_REQUIRED_DECISION = "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE"

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"
DECISION_RETIRED = "F16_GLOBAL_COLOR_POLICY_CANDIDATE_RETIRED"
DECISION_INPUT_INVALID = "F16_GLOBAL_COLOR_POLICY_CANDIDATE_RETIREMENT_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_RETIRED, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk python3 scripts/validate_for344_f16_broader_non_arc_color_policy_evidence.py",
    "rtk python3 scripts/validate_for343_f16_color_policy_boundary.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-346 validation failed: {message}")


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


def validate_for343(for343: dict[str, Any]) -> None:
    require(for343.get("linear") == "FOR-343", "FOR-343 artifact identity changed")
    require(for343.get("decision") == FOR343_REQUIRED_DECISION, "FOR-343 required decision changed")
    boundary = for343.get("boundary")
    require(isinstance(boundary, dict), "FOR-343 boundary missing")
    require(boundary.get("id") == BOUNDARY_ID, "FOR-343 boundary id changed")
    require(boundary.get("rendererBehaviorChanged") is False, "FOR-343 changed renderer behavior")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "FOR-343 allows global F16 change")
    require(boundary.get("broaderEvidenceCollectionAllowed") is True, "FOR-343 no longer allows evidence collection")


def validate_for344(for344: dict[str, Any]) -> None:
    require(for344.get("linear") == "FOR-344", "FOR-344 artifact identity changed")
    require(for344.get("decision") == FOR344_REQUIRED_DECISION, "FOR-344 required decision changed")
    boundary = for344.get("boundary")
    require(isinstance(boundary, dict), "FOR-344 boundary missing")
    require(boundary.get("candidatePolicyId") == CANDIDATE_POLICY_ID, "FOR-344 candidate policy changed")
    require(boundary.get("rendererBehaviorChanged") is False, "FOR-344 changed renderer behavior")
    gap = for344.get("referenceGapClassification")
    require(isinstance(gap, dict), "FOR-344 reference gap missing")
    require(
        gap.get("stableReasonCode") == "NON_ARC_REC2020_F16_REFERENCE_CURRENT_CANDIDATE_ROWS_MISSING",
        "FOR-344 gap reason changed",
    )
    require(gap.get("requiresMoreReferenceRows") is True, "FOR-344 no longer records the missing-row gate")


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 required rejecting decision changed")
    require(for345.get("sourceFindings") == [
        "global/kanvas/findings/for-344-broader-non-arc-f16-color-evidence-partial-finding"
    ], "FOR-345 source finding changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row must stay non-arc")
    require(row.get("excludedScene") == "circular_arcs_stroke_butt", "FOR-345 row exclusion changed")
    require(row.get("rec2020F16SrcOverOrBlendSignal") is True, "FOR-345 row signal changed")
    require(row.get("referenceCurrentCandidateComparable") is True, "FOR-345 row must stay comparable")
    candidate = row.get("candidate")
    require(isinstance(candidate, dict), "FOR-345 candidate block missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "FOR-345 candidate policy changed")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("sampleCount") == 4, "FOR-345 sample count changed")
    require(residuals.get("coveredSampleCount") == 3, "FOR-345 covered sample count changed")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("candidateResidual") == 111, "FOR-345 candidate residual changed")
    require(residuals.get("candidateMinusCurrentResidual") == 111, "FOR-345 residual delta changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 worsened sample count changed")
    boundary = for345.get("boundary")
    require(isinstance(boundary, dict), "FOR-345 boundary missing")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "FOR-345 now allows global F16 change")
    require(boundary.get("rendererBehaviorChanged") is False, "FOR-345 changed renderer behavior")
    return residuals


def build_artifact(for343: dict[str, Any], for344: dict[str, Any], for345: dict[str, Any]) -> dict[str, Any]:
    residuals = validate_for345(for345)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for343Artifact": rel(FOR343_ARTIFACT),
            "for343Decision": for343.get("decision"),
            "for343RequiredDecision": FOR343_REQUIRED_DECISION,
            "for344Artifact": rel(FOR344_ARTIFACT),
            "for344Decision": for344.get("decision"),
            "for344RequiredDecision": FOR344_REQUIRED_DECISION,
            "for345Artifact": rel(FOR345_ARTIFACT),
            "for345Decision": for345.get("decision"),
            "for345RequiredDecision": FOR345_REQUIRED_DECISION,
        },
        "decision": DECISION_RETIRED,
        "allowedDecisions": ALLOWED_DECISIONS,
        "candidatePolicy": {
            "policyId": CANDIDATE_POLICY_ID,
            "globalStatus": "retired",
            "globalCandidateOpen": False,
            "mayRaiseGlobalF16Score": False,
            "mayAuthorizeGlobalF16Migration": False,
            "retirementReasonCode": "NON_ARC_REC2020_F16_REFERENCE_ROW_REJECTS_CANDIDATE",
            "retirementBasis": [
                "FOR-343 keeps global F16 renderer migration behind an explicit color-policy boundary.",
                "FOR-344 records that non-arc evidence was required before broader policy use.",
                "FOR-345 supplies the comparable non-arc row and the candidate worsens covered samples.",
            ],
        },
        "evidence": {
            "for345RowId": for345.get("row", {}).get("rowId"),
            "nonArc": for345.get("row", {}).get("nonArc"),
            "excludedScene": for345.get("row", {}).get("excludedScene"),
            "colorSpace": "Rec.2020",
            "colorType": "kRGBA_F16Norm",
            "blendMode": "kSrcOver",
            "sampleCount": residuals.get("sampleCount"),
            "coveredSampleCount": residuals.get("coveredSampleCount"),
            "currentResidual": residuals.get("currentResidual"),
            "candidateResidual": residuals.get("candidateResidual"),
            "candidateMinusCurrentResidual": residuals.get("candidateMinusCurrentResidual"),
            "worsenedSampleCount": residuals.get("worsenedSampleCount"),
        },
        "remainingOptions": [
            {
                "id": "localized-policy",
                "status": "open",
                "description": (
                    "A narrower policy may still be explored only with explicit scope, "
                    "route diagnostics, and positive reference/current/candidate evidence."
                ),
            },
            {
                "id": "stable-fallback",
                "status": "open",
                "description": "Keep the current behavior and document unsupported broader F16 migration routes.",
            },
            {
                "id": "new-candidate-search",
                "status": "open",
                "description": "Search for a different candidate using non-arc and arc evidence from the start.",
            },
        ],
        "boundary": {
            "id": BOUNDARY_ID,
            "rendererBehaviorChanged": False,
            "globalF16RendererChangeAllowedNow": False,
            "candidatePolicyId": CANDIDATE_POLICY_ID,
            "candidateRetiredAsGlobalPolicy": True,
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
        "nonGoalsPreserved": {
            "globalF16PolicyChange": True,
            "newColorPolicy": True,
            "scoreIncrease": True,
            "rendererBehaviorChange": True,
            "selectedCellSubstitutionRefused": True,
            "fixtureBranchRefused": True,
            "coordinateBranchRefused": True,
            "fullGmCropRefused": True,
            "thresholdRelaxationRefused": True,
            "ganeshGraphitePortNotAdded": True,
            "skslCompilerVmWorkNotAdded": True,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_RETIRED, "retirement decision changed")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("for343Decision") == FOR343_REQUIRED_DECISION, "FOR-343 gate not encoded")
    require(input_validation.get("for344Decision") == FOR344_REQUIRED_DECISION, "FOR-344 gate not encoded")
    require(input_validation.get("for345Decision") == FOR345_REQUIRED_DECISION, "FOR-345 rejection not encoded")

    candidate = data.get("candidatePolicy")
    require(isinstance(candidate, dict), "candidatePolicy missing")
    require(candidate.get("policyId") == CANDIDATE_POLICY_ID, "candidate policy id changed")
    require(candidate.get("globalStatus") == "retired", "candidate is not retired")
    require(candidate.get("globalCandidateOpen") is False, "candidate remains globally open")
    require(candidate.get("mayRaiseGlobalF16Score") is False, "candidate may still raise global F16 score")
    require(candidate.get("mayAuthorizeGlobalF16Migration") is False, "candidate may still authorize migration")
    require(
        candidate.get("retirementReasonCode") == "NON_ARC_REC2020_F16_REFERENCE_ROW_REJECTS_CANDIDATE",
        "retirement reason changed",
    )

    evidence = data.get("evidence")
    require(isinstance(evidence, dict), "evidence missing")
    require(evidence.get("nonArc") is True, "evidence must stay non-arc")
    require(evidence.get("excludedScene") == "circular_arcs_stroke_butt", "excluded scene changed")
    require(evidence.get("currentResidual") == 0, "current residual changed")
    require(evidence.get("candidateResidual") == 111, "candidate residual changed")
    require(evidence.get("candidateMinusCurrentResidual") == 111, "residual delta changed")
    require(evidence.get("worsenedSampleCount") == 3, "worsened sample count changed")

    remaining_options = data.get("remainingOptions")
    require(isinstance(remaining_options, list) and len(remaining_options) == 3, "remaining options changed")
    require({item.get("id") for item in remaining_options if isinstance(item, dict)} == {
        "localized-policy",
        "stable-fallback",
        "new-candidate-search",
    }, "remaining option ids changed")

    boundary = data.get("boundary")
    require(isinstance(boundary, dict), "boundary missing")
    require(boundary.get("rendererBehaviorChanged") is False, "renderer behavior changed")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "global F16 migration allowed")
    require(boundary.get("candidateRetiredAsGlobalPolicy") is True, "candidate retirement flag missing")

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
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    basis = "\n".join(f"- {item}" for item in data["candidatePolicy"]["retirementBasis"])
    options = "\n".join(
        f"- `{item['id']}`: {item['description']}" for item in data["remainingOptions"]
    )
    evidence = data["evidence"]
    return f"""# FOR-346 F16 Global Color Policy Candidate Retired

Linear: `FOR-346`

Decision: `{data["decision"]}`

FOR-346 retires `{CANDIDATE_POLICY_ID}` as a global F16 color-policy
candidate. This is a decision artifact only; it does not change renderer behavior.

## Result

The candidate is no longer globally open, cannot be used to raise the global
F16 score, and cannot authorize a global F16 renderer migration.

The retirement is based on the FOR-343/FOR-344/FOR-345 evidence chain:

{basis}

## FOR-345 Evidence

| metric | value |
|---|---:|
| non-arc | {str(evidence["nonArc"]).lower()} |
| excluded scene | `{evidence["excludedScene"]}` |
| color type | `{evidence["colorType"]}` |
| color space | `{evidence["colorSpace"]}` |
| blend mode | `{evidence["blendMode"]}` |
| samples | {evidence["sampleCount"]} |
| covered samples | {evidence["coveredSampleCount"]} |
| current residual | {evidence["currentResidual"]} |
| candidate residual | {evidence["candidateResidual"]} |
| candidate minus current | {evidence["candidateMinusCurrentResidual"]} |
| worsened samples | {evidence["worsenedSampleCount"]} |

## Remaining Options

{options}

## Non-goals Preserved

- No renderer behavior change.
- No new color policy implementation.
- No change to `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
  `SkBitmap.getPixelAsSrgb`.
- No GPU/WGSL, geometry, coverage, fallback, threshold, promotion, score, or
  Kadre change.
- No selected-cell substitution, fixture/coordinate branch, full-GM crop, or
  threshold relaxation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for346_f16_global_color_policy_candidate_retired.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-346-f16-global-color-policy-candidate-retired.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    for343 = load_json(FOR343_ARTIFACT)
    for344 = load_json(FOR344_ARTIFACT)
    for345 = load_json(FOR345_ARTIFACT)
    validate_for343(for343)
    validate_for344(for344)
    data = build_artifact(for343, for344, for345)
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-346 validation passed")


if __name__ == "__main__":
    main()
