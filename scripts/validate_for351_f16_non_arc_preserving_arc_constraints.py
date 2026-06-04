#!/usr/bin/env python3
"""Validate the FOR-351 F16 non-arc-preserving arc constraints."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-351"
SCENE_ID = "f16-non-arc-preserving-arc-constraints-for351"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-351-f16-non-arc-preserving-arc-constraints.md"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-f16-derive-non-arc-preserving-arc-improving-candidate-constraints-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/for-350-f16-arc-improving-non-arc-safe-candidate-rejected"
)

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

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR341_SCENE_ID / f"{FOR341_SCENE_ID}.json"
)
FOR341_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"

RETIRED_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FOR350_REJECTED_CANDIDATE_ID = "halfway_to_retired_over_white_candidate"
BOUNDARY_ID = "cpu-raster-f16-color-policy-boundary"

DECISION_READY = "F16_NON_ARC_PRESERVING_ARC_CONSTRAINTS_READY"
DECISION_INPUT_INVALID = "F16_NON_ARC_PRESERVING_ARC_CONSTRAINTS_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_INPUT_INVALID]

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py",
    "rtk python3 scripts/validate_for350_f16_arc_improving_non_arc_safe_candidate.py",
    "rtk python3 scripts/validate_for349_f16_replacement_candidate_evaluation.py",
    "rtk python3 scripts/validate_for348_f16_new_candidate_search_matrix.py",
    "rtk python3 scripts/validate_for346_f16_global_color_policy_candidate_retired.py",
    "rtk python3 scripts/validate_for345_non_arc_rec2020_f16_reference_row.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-351 validation failed: {message}")


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


def validate_for350(for350: dict[str, Any]) -> dict[str, Any]:
    require(for350.get("linear") == "FOR-350", "FOR-350 artifact identity changed")
    require(for350.get("decision") == FOR350_REQUIRED_DECISION, "FOR-350 decision changed")
    candidate = for350.get("candidate")
    require(isinstance(candidate, dict), "FOR-350 candidate missing")
    require(candidate.get("policyId") == FOR350_REJECTED_CANDIDATE_ID, "FOR-350 candidate changed")
    require(candidate.get("selectedForImplementation") is False, "FOR-350 candidate was selected")
    criteria = for350.get("criteriaEvaluation")
    require(isinstance(criteria, dict), "FOR-350 criteria missing")
    require(criteria.get("arcPositive") is True, "FOR-350 no longer improves arc")
    require(criteria.get("nonArcPositive") is False, "FOR-350 non-arc rejection changed")
    rows = for350.get("evaluationRows")
    require(isinstance(rows, list), "FOR-350 rows missing")
    return {row["family"]: row for row in rows if isinstance(row, dict)}


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


def validate_for345(for345: dict[str, Any]) -> dict[str, Any]:
    require(for345.get("linear") == "FOR-345", "FOR-345 artifact identity changed")
    require(for345.get("decision") == FOR345_REQUIRED_DECISION, "FOR-345 decision changed")
    row = for345.get("row")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(row.get("nonArc") is True, "FOR-345 row is no longer non-arc")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("currentResidual") == 0, "FOR-345 current residual changed")
    require(residuals.get("candidateResidual") == 111, "FOR-345 retired candidate residual changed")
    require(residuals.get("worsenedSampleCount") == 3, "FOR-345 worsened sample count changed")
    samples = row.get("samples")
    require(isinstance(samples, list) and samples, "FOR-345 samples missing")
    return row


def validate_for341(for341: dict[str, Any]) -> dict[str, Any]:
    require(for341.get("linear") == "FOR-341", "FOR-341 artifact identity changed")
    require(for341.get("decision") == FOR341_REQUIRED_DECISION, "FOR-341 decision changed")
    policy = for341.get("policyDecision")
    require(isinstance(policy, dict), "FOR-341 policy decision missing")
    require(policy.get("sourceCandidatePolicyId") == RETIRED_POLICY_ID, "FOR-341 source policy changed")
    require(policy.get("codeChangeAllowedInThisTicket") is False, "FOR-341 changed renderer behavior")
    totals = for341.get("residualTotals")
    require(isinstance(totals, dict), "FOR-341 residual totals missing")
    require(totals.get("currentVsOverWhiteResidual") == 375, "FOR-341 arc residual changed")
    require(totals.get("candidateVsOverWhiteResidual") == 0, "FOR-341 candidate residual changed")
    cells = for341.get("targetCells")
    require(isinstance(cells, list) and cells, "FOR-341 target cells missing")
    return for341


def build_non_arc_constraints(row: dict[str, Any]) -> dict[str, Any]:
    residuals = row["residuals"]
    samples = row["samples"]
    covered_samples = [sample for sample in samples if sample.get("insideDraw") is True]
    require(len(covered_samples) == residuals.get("coveredSampleCount"), "FOR-345 covered sample count changed")
    for sample in samples:
        require(sample.get("currentVsReferenceSumAbsDelta") == 0, f"{sample.get('name')} current residual changed")
    guarded_samples = [
        {
            "sample": sample["name"],
            "zone": sample["zone"],
            "referenceSrgbRgba": sample["referenceSrgbRgba"],
            "currentKanvasSrgbRgba": sample["currentKanvasSrgbRgba"],
            "retiredPolicyRgba": sample["candidatePolicyRgba"],
            "retiredPolicyResidual": sample["candidateVsReferenceSumAbsDelta"],
            "retiredPolicyWorsensCurrent": sample["candidateWorsensCurrent"],
        }
        for sample in covered_samples
    ]
    return {
        "rowId": row["rowId"],
        "sourceArtifact": rel(FOR345_ARTIFACT),
        "requiredResidual": 0,
        "requiredWorsenedSampleCount": 0,
        "currentResidual": residuals["currentResidual"],
        "retiredPolicyResidual": residuals["candidateResidual"],
        "retiredPolicyWorsenedSampleCount": residuals["worsenedSampleCount"],
        "coveredSampleCount": residuals["coveredSampleCount"],
        "constraints": [
            {
                "id": "preserve-current-reference-equality",
                "requires": "future candidate residual must stay exactly 0 on the FOR-345 row",
                "evidence": "current Kanvas samples equal the isolated Skia reference on every sampled point",
            },
            {
                "id": "forbid-over-white-shift-on-covered-non-arc-samples",
                "requires": "covered non-arc samples must not move toward the retired over-white policy",
                "evidence": "retired policy residual is 111 with 3 worsened covered samples",
            },
            {
                "id": "non-arc-first-selection-gate",
                "requires": "arc improvement cannot count unless non-arc residual remains 0 and worsened samples remain 0",
                "evidence": "FOR-350 improved arc but was rejected after producing non-arc residual 56",
            },
        ],
        "guardedSamples": guarded_samples,
    }


def build_arc_targets(for341: dict[str, Any]) -> dict[str, Any]:
    targets: list[dict[str, Any]] = []
    for cell in for341["targetCells"]:
        group_id = cell["groupId"]
        for sample in cell["samples"]:
            residual = int(sample["currentVsOverWhiteSumAbsDelta"])
            if residual <= 0:
                continue
            targets.append(
                {
                    "groupId": group_id,
                    "sample": sample["name"],
                    "zone": sample["zone"],
                    "currentFor339ExportRgba": sample["currentFor339ExportRgba"],
                    "skiaOverWhiteTargetRgba": sample["skiaReferenceOverWhiteRgba"],
                    "currentResidual": residual,
                    "requiredResidualReduction": residual,
                    "retiredPolicyResidual": sample["candidateVsOverWhiteSumAbsDelta"],
                }
            )
    total = sum(target["currentResidual"] for target in targets)
    require(total == for341["residualTotals"]["currentVsOverWhiteResidual"], "FOR-341 target residual sum changed")
    return {
        "rowId": "arc-circular-arcs-stroke-butt-adjacent-for341",
        "sourceArtifact": rel(FOR341_ARTIFACT),
        "currentResidual": total,
        "requiredResidualReductionForPerfectArcFit": total,
        "retiredPolicyResidual": for341["residualTotals"]["candidateVsOverWhiteResidual"],
        "targetSampleCount": len(targets),
        "targets": targets,
    }


def build_artifact(
    for350: dict[str, Any],
    for348: dict[str, Any],
    for346: dict[str, Any],
    for345: dict[str, Any],
    for341: dict[str, Any],
) -> dict[str, Any]:
    for350_rows = validate_for350(for350)
    validate_for348(for348)
    validate_for346(for346)
    non_arc_row = validate_for345(for345)
    arc_source = validate_for341(for341)
    non_arc = build_non_arc_constraints(non_arc_row)
    arc = build_arc_targets(arc_source)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
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
            "for341Artifact": rel(FOR341_ARTIFACT),
            "for341Decision": for341.get("decision"),
            "for341RequiredDecision": FOR341_REQUIRED_DECISION,
        },
        "decision": DECISION_READY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "constraints": {
            "nonArcPreserving": non_arc,
            "arcResidualTargets": arc,
            "for350RejectedInterpolation": {
                "candidatePolicyId": FOR350_REJECTED_CANDIDATE_ID,
                "arcResidualReduction": for350_rows["arc"]["residualReductionVsCurrent"],
                "nonArcResidualRegression": for350_rows["non-arc"]["candidateResidual"]
                - for350_rows["non-arc"]["currentResidual"],
                "nonArcWorsenedSampleCount": for350_rows["non-arc"]["worsenedSampleCount"],
                "rejectionReason": "linear movement toward the retired over-white policy improves arc but violates non-arc identity",
            },
        },
        "nextCandidateFamilies": [
            {
                "id": "non_arc_identity_guarded_arc_delta_candidate_family",
                "status": "recommended-for-next-evaluation",
                "selectionStatus": "not-selected",
                "description": (
                    "Evaluate formulas whose first invariant is FOR-345 non-arc residual 0, "
                    "then measure whether they reduce FOR-341 arc residuals."
                ),
                "mandatoryRejectWhen": [
                    "FOR-345 candidate residual is greater than 0",
                    "FOR-345 worsened sample count is greater than 0",
                    "candidate reuses the retired over-white policy globally",
                    "candidate requires fixture, coordinate, selected-cell, or scene branching",
                ],
            },
            {
                "id": "additional_non_arc_rows_before_selection",
                "status": "recommended-validation-expansion",
                "selectionStatus": "not-selected",
                "description": (
                    "Add more non-arc rows before any renderer implementation decision if a future "
                    "candidate passes the current FOR-345 guard."
                ),
                "mandatoryRejectWhen": [
                    "candidate has only arc evidence",
                    "candidate has no comparable non-arc Rec.2020 F16 SrcOver or blend evidence",
                ],
            },
        ],
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
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "artifact source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "artifact source finding changed")
    require(data.get("decision") == DECISION_READY, "decision changed")
    constraints = data.get("constraints")
    require(isinstance(constraints, dict), "constraints missing")
    non_arc = constraints.get("nonArcPreserving")
    arc = constraints.get("arcResidualTargets")
    require(isinstance(non_arc, dict), "non-arc constraints missing")
    require(isinstance(arc, dict), "arc targets missing")
    require(non_arc.get("requiredResidual") == 0, "non-arc required residual changed")
    require(non_arc.get("requiredWorsenedSampleCount") == 0, "non-arc worsened guard changed")
    require(non_arc.get("currentResidual") == 0, "non-arc current residual changed")
    require(non_arc.get("retiredPolicyResidual") == 111, "non-arc retired residual changed")
    require(arc.get("currentResidual") == 375, "arc target residual changed")
    require(arc.get("targetSampleCount") == 10, "arc target sample count changed")
    rejected = constraints.get("for350RejectedInterpolation")
    require(isinstance(rejected, dict), "FOR-350 rejected interpolation missing")
    require(rejected.get("arcResidualReduction") == 187, "FOR-350 arc improvement changed")
    require(rejected.get("nonArcResidualRegression") == 56, "FOR-350 non-arc regression changed")
    families = data.get("nextCandidateFamilies")
    require(isinstance(families, list) and len(families) >= 1, "next candidate family missing")
    require(families[0].get("selectionStatus") == "not-selected", "next candidate family was selected")
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
    non_arc = data["constraints"]["nonArcPreserving"]
    arc = data["constraints"]["arcResidualTargets"]
    rejected = data["constraints"]["for350RejectedInterpolation"]
    non_arc_constraints = "\n".join(
        f"- `{item['id']}`: {item['requires']} ({item['evidence']})"
        for item in non_arc["constraints"]
    )
    arc_rows = "\n".join(
        "| `{sample}` | `{group}` | {current} | {target} | {residual} |".format(
            sample=target["sample"],
            group=target["groupId"],
            current=target["currentFor339ExportRgba"],
            target=target["skiaOverWhiteTargetRgba"],
            residual=target["currentResidual"],
        )
        for target in arc["targets"]
    )
    families = "\n".join(
        f"- `{family['id']}`: {family['description']} Status: `{family['selectionStatus']}`."
        for family in data["nextCandidateFamilies"]
    )
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    return f"""# FOR-351 F16 Non-Arc-Preserving Arc Constraints

Linear: `FOR-351`

Decision: `{data["decision"]}`

FOR-351 derives the measurable constraints for the next F16 candidate search.
The current non-arc FOR-345 row is already exact, so future candidates must keep
that residual at `0` before any arc gain can count.

## Non-Arc Preserving Constraints

Required residual: `{non_arc["requiredResidual"]}`

Required worsened samples: `{non_arc["requiredWorsenedSampleCount"]}`

Current residual: `{non_arc["currentResidual"]}`

Retired over-white residual: `{non_arc["retiredPolicyResidual"]}`

{non_arc_constraints}

## Arc Residual Targets

FOR-341 current arc residual: `{arc["currentResidual"]}`

Perfect arc-fit reduction target: `{arc["requiredResidualReductionForPerfectArcFit"]}`

| sample | group | current RGBA | target RGBA | current residual |
|---|---|---:|---:|---:|
{arc_rows}

## FOR-350 Rejection Carried Forward

`{rejected["candidatePolicyId"]}` reduced the arc residual by
`{rejected["arcResidualReduction"]}`, but introduced non-arc residual
`{rejected["nonArcResidualRegression"]}` with `{rejected["nonArcWorsenedSampleCount"]}`
worsened samples. That proves linear movement toward the retired over-white
policy is insufficient.

## Next Candidate Families

{families}

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
- Validator: `scripts/validate_for351_f16_non_arc_preserving_arc_constraints.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-351-f16-non-arc-preserving-arc-constraints.md`

## Validation

{validation}
"""


def main() -> None:
    validate_source_guardrails()
    data = build_artifact(
        load_json(FOR350_ARTIFACT),
        load_json(FOR348_ARTIFACT),
        load_json(FOR346_ARTIFACT),
        load_json(FOR345_ARTIFACT),
        load_json(FOR341_ARTIFACT),
    )
    validate_artifact(data)
    write_if_changed(ARTIFACT, json.dumps(data, indent=2, sort_keys=False) + "\n")
    write_if_changed(REPORT, build_report(data))
    print("FOR-351 validation passed")


if __name__ == "__main__":
    main()
