#!/usr/bin/env python3
"""Validate the FOR-342 scoped adjacent CircularArcsStrokeButt F16 policy decision."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-342"
SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation-for342"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-342-circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-scoped-implementation-of-adjacent-circular-arcs-stroke-butt-f16-color-policy-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-341-circular-arcs-stroke-butt-adjacent-f16-color-policy-ready-for-scoped-implementation-finding"
)

FOR341_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
FOR341_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR341_SCENE_ID
    / f"{FOR341_SCENE_ID}.json"
)
FOR341_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"
)

DECISION_APPLIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_APPLIED"
)
DECISION_PARTIAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_PARTIAL_REQUIRES_SAFER_ROUTE"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_SCOPED_IMPLEMENTATION_INPUT_INVALID"
)
ALLOWED_DECISIONS = [DECISION_APPLIED, DECISION_PARTIAL, DECISION_INPUT_INVALID]

AUTHORIZED_POLICY_ID = (
    "adjacent_circular_arcs_stroke_butt_f16_straight_srgb_quantized_alpha_src_over_white"
)
SOURCE_CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"

EXPECTED_GROUPS = [
    "adjacent_arc_stroke_start0_sweep45_target",
    "adjacent_arc_stroke_start0_sweep130_target",
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py",
    "rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py",
    "rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py",
    "rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-342 validation failed: {message}")


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


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(int(a[i]) - int(b[i])) for i in range(4)]


def sum_abs_delta(a: list[int], b: list[int]) -> int:
    return sum(abs_delta(a, b))


def validate_for341(for341: dict[str, Any]) -> None:
    require(for341.get("linear") == "FOR-341", "FOR-341 artifact identity changed")
    require(for341.get("decision") == FOR341_REQUIRED_DECISION, "FOR-341 decision is not ready")
    require(for341.get("sourceFindings") == ["global/kanvas/findings/for-340-circular-arcs-stroke-butt-adjacent-f16-skia-reference-captured-finding"], "FOR-341 source finding changed")
    policy = for341.get("policyDecision")
    require(isinstance(policy, dict), "FOR-341 policyDecision missing")
    require(policy.get("implementationAllowedNow") is True, "FOR-341 implementation authorization changed")
    require(policy.get("codeChangeAllowedInThisTicket") is False, "FOR-341 should remain evidence-only")
    require(policy.get("selectedCandidatePolicyId") == AUTHORIZED_POLICY_ID, "FOR-341 authorized policy changed")
    require(policy.get("sourceCandidatePolicyId") == SOURCE_CANDIDATE_POLICY_ID, "FOR-341 source candidate policy changed")
    require(policy.get("rawTransparentPngBasisAcceptedForImplementation") is False, "raw transparent PNG basis accepted")
    require(policy.get("skiaOverWhiteBasisAcceptedForImplementation") is True, "Skia-over-white basis not accepted")
    require(policy.get("affectedCells") == EXPECTED_GROUPS, "FOR-341 affected cells changed")

    totals = for341.get("residualTotals")
    require(isinstance(totals, dict), "FOR-341 residualTotals missing")
    require(totals.get("sampleCount") == 12, "FOR-341 sample count changed")
    require(totals.get("strokeSampleCount") == 10, "FOR-341 stroke sample count changed")
    require(totals.get("currentVsRawResidual") == 7065, "FOR-341 raw residual changed")
    require(totals.get("currentVsOverWhiteResidual") == 375, "FOR-341 current over-white residual changed")
    require(totals.get("candidateVsOverWhiteResidual") == 0, "FOR-341 candidate residual changed")

    implementation = for341.get("implementation")
    require(isinstance(implementation, dict), "FOR-341 implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "FOR-341 renderer behavior changed")
    require(implementation.get("evidenceOnly") is True, "FOR-341 should remain evidence-only")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "FOR-341 selected-cell extrapolation changed")
    require(implementation.get("for327SubstitutionUsed") is False, "FOR-341 FOR-327 substitution changed")
    require(implementation.get("cropEvidenceUsed") is False, "FOR-341 crop evidence changed")


def build_sample(sample: dict[str, Any]) -> dict[str, Any]:
    old_rgba = sample["currentFor339ExportRgba"]
    skia_over_white = sample["skiaReferenceOverWhiteRgba"]
    candidate_rgba = sample["candidatePolicyRgba"]
    zone = sample["zone"]
    is_stroke = str(zone).startswith("stroke")
    if is_stroke:
        require(sample.get("candidatePolicyApplicable") is True, f"{sample['name']} candidate not applicable")
        require(sample.get("candidatePolicyId") == SOURCE_CANDIDATE_POLICY_ID, f"{sample['name']} source policy changed")
        require(candidate_rgba == skia_over_white, f"{sample['name']} candidate no longer matches Skia-over-white")
    else:
        require(old_rgba == skia_over_white == candidate_rgba, f"{sample['name']} background sample changed")

    old_delta = abs_delta(old_rgba, skia_over_white)
    candidate_delta = abs_delta(candidate_rgba, skia_over_white)
    return {
        "name": sample["name"],
        "zone": zone,
        "localX": sample["localX"],
        "localY": sample["localY"],
        "oldCurrentFor339ExportRgba": old_rgba,
        "actualNewRendererRgba": old_rgba,
        "candidateNewPolicyRgba": candidate_rgba,
        "skiaReferenceOverWhiteRgba": skia_over_white,
        "rawTransparentPngReferenceRgba": sample["skiaReferenceRawRgba"],
        "oldVsSkiaOverWhiteDeltaAbs": old_delta,
        "oldVsSkiaOverWhiteSumAbsDelta": sum(old_delta),
        "actualNewVsSkiaOverWhiteDeltaAbs": old_delta,
        "actualNewVsSkiaOverWhiteSumAbsDelta": sum(old_delta),
        "candidateNewVsSkiaOverWhiteDeltaAbs": candidate_delta,
        "candidateNewVsSkiaOverWhiteSumAbsDelta": sum(candidate_delta),
        "candidateMatchesSkiaOverWhite": sum(candidate_delta) == 0,
        "implementationAppliedToRenderer": False,
        "oldNewEvidenceKind": "old-current-plus-candidate-new-policy-no-renderer-change",
    }


def build_cell(cell: dict[str, Any]) -> dict[str, Any]:
    group_id = cell["groupId"]
    samples = [build_sample(sample) for sample in cell["samples"]]
    stroke_samples = [sample for sample in samples if str(sample["zone"]).startswith("stroke")]
    return {
        "groupId": group_id,
        "cell": cell["cell"],
        "implementationScopeMatched": group_id in EXPECTED_GROUPS,
        "oldNewEvidenceSampleCount": len(samples),
        "strokeSampleCount": len(stroke_samples),
        "oldCurrentOverWhiteResidual": sum(sample["oldVsSkiaOverWhiteSumAbsDelta"] for sample in samples),
        "actualNewOverWhiteResidual": sum(sample["actualNewVsSkiaOverWhiteSumAbsDelta"] for sample in samples),
        "candidateNewOverWhiteResidual": sum(sample["candidateNewVsSkiaOverWhiteSumAbsDelta"] for sample in samples),
        "samples": samples,
    }


def build_artifact(for341: dict[str, Any]) -> dict[str, Any]:
    cells = [build_cell(cell) for cell in for341.get("targetCells", [])]
    require([cell["groupId"] for cell in cells] == EXPECTED_GROUPS, "FOR-341 target cell order changed")
    sample_count = sum(cell["oldNewEvidenceSampleCount"] for cell in cells)
    stroke_sample_count = sum(cell["strokeSampleCount"] for cell in cells)
    old_residual = sum(cell["oldCurrentOverWhiteResidual"] for cell in cells)
    actual_new_residual = sum(cell["actualNewOverWhiteResidual"] for cell in cells)
    candidate_residual = sum(cell["candidateNewOverWhiteResidual"] for cell in cells)

    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for341Artifact": rel(FOR341_ARTIFACT),
            "for341Decision": for341.get("decision"),
            "for341RequiredDecision": FOR341_REQUIRED_DECISION,
            "for341ReadyForScopedImplementation": for341.get("decision") == FOR341_REQUIRED_DECISION,
            "for341AuthorizedPolicyId": for341["policyDecision"]["selectedCandidatePolicyId"],
            "rawTransparentPngBasisAcceptedForImplementation": False,
            "skiaOverWhiteBasisAcceptedForImplementation": True,
        },
        "decision": DECISION_PARTIAL,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-341 proves the candidate policy would reduce the exact adjacent-cell over-white residual "
            "from 375 to 0, but the only renderer hooks found are global F16 color conversion, global "
            "F16 blending, and the global encoded-export boundary. Applying the policy to only these two "
            "cells would require fixture/coordinate-specific renderer branching; applying it generally "
            "would be a color-management migration outside FOR-342. Renderer behavior is therefore left "
            "unchanged and this ticket records the safer-route decision."
        ),
        "policy": {
            "authorizedPolicyId": AUTHORIZED_POLICY_ID,
            "sourceCandidatePolicyId": SOURCE_CANDIDATE_POLICY_ID,
            "candidatePolicyDescription": (
                "straight sRGB source channels with rounded covered alpha, SrcOver composited over white"
            ),
            "rawTransparentPngBasisAcceptedForImplementation": False,
            "skiaOverWhiteBasisAcceptedForImplementation": True,
            "affectedCells": EXPECTED_GROUPS,
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "implementationApplied": False,
            "partialDecisionStable": True,
            "partialDecisionReason": "no-safe-scoped-renderer-route-without-fixture-branch-or-global-color-migration",
            "unsafeRoutesRejected": [
                "fixture-or-coordinate-specific renderer branch for only the two FOR-339/FOR-340 cells",
                "global colorToF16Premul change",
                "global blendF16PremulMode change",
                "global SkBitmap.getPixelAsSrgb export-boundary change",
                "selected-cell extrapolation or FOR-327 substitution",
            ],
            "safeFutureRouteRequired": [
                "introduce an explicit, product-approved color policy boundary rather than a fixture branch",
                "prove the boundary on broader non-arc F16 fixtures before changing global F16 renderer behavior",
                "keep SkBitmap.getPixel as the internal renderer/test oracle unless separately approved",
                "keep SkBitmap.getPixelAsSrgb as the encoded export boundary unless separately approved",
            ],
        },
        "residualTotals": {
            "sampleCount": sample_count,
            "strokeSampleCount": stroke_sample_count,
            "oldCurrentOverWhiteResidual": old_residual,
            "actualNewOverWhiteResidual": actual_new_residual,
            "candidateNewOverWhiteResidual": candidate_residual,
            "candidateResidualReductionIfSafeRouteExists": old_residual - candidate_residual,
            "rendererResidualReductionApplied": old_residual - actual_new_residual,
            "rawTransparentPngResidualRejected": for341["residualTotals"]["currentVsRawResidual"],
        },
        "nonGoalsPreserved": {
            "colorToF16Premul": True,
            "blendF16PremulMode": True,
            "skBitmapGetPixelInternalOracle": True,
            "skBitmapGetPixelAsSrgbExportBoundary": True,
            "geometry": True,
            "coveragePolicy": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "historicalArtifactsFOR329ToFOR341Rewritten": False,
        },
        "oldNewEvidence": cells,
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact identity changed")
    require(data.get("decision") == DECISION_PARTIAL, "expected stable partial decision")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "renderer behavior changed")
    require(implementation.get("implementationApplied") is False, "partial decision must not apply renderer")
    require(implementation.get("partialDecisionStable") is True, "partial decision must be stable")

    policy = data.get("policy")
    require(isinstance(policy, dict), "policy block missing")
    require(policy.get("authorizedPolicyId") == AUTHORIZED_POLICY_ID, "authorized policy changed")
    require(policy.get("rawTransparentPngBasisAcceptedForImplementation") is False, "raw PNG basis accepted")

    totals = data.get("residualTotals")
    require(isinstance(totals, dict), "residualTotals missing")
    require(totals.get("sampleCount") == 12, "sample count changed")
    require(totals.get("strokeSampleCount") == 10, "stroke sample count changed")
    require(totals.get("oldCurrentOverWhiteResidual") == 375, "old residual changed")
    require(totals.get("actualNewOverWhiteResidual") == 375, "actual-new residual changed")
    require(totals.get("candidateNewOverWhiteResidual") == 0, "candidate residual changed")
    require(totals.get("rawTransparentPngResidualRejected") == 7065, "raw residual changed")

    cells = data.get("oldNewEvidence")
    require(isinstance(cells, list), "oldNewEvidence missing")
    require([cell.get("groupId") for cell in cells] == EXPECTED_GROUPS, "evidence cell order changed")
    sample_count = 0
    stroke_count = 0
    for cell in cells:
        samples = cell.get("samples")
        require(isinstance(samples, list), f"{cell.get('groupId')} samples missing")
        sample_count += len(samples)
        for sample in samples:
            is_stroke = str(sample.get("zone", "")).startswith("stroke")
            stroke_count += int(is_stroke)
            require(sample.get("implementationAppliedToRenderer") is False, f"{sample.get('name')} renderer flag changed")
            require(sample.get("actualNewRendererRgba") == sample.get("oldCurrentFor339ExportRgba"), f"{sample.get('name')} actual-new must equal old")
            if is_stroke:
                require(sample.get("candidateMatchesSkiaOverWhite") is True, f"{sample.get('name')} candidate mismatch")
                require(sample.get("candidateNewVsSkiaOverWhiteSumAbsDelta") == 0, f"{sample.get('name')} candidate residual changed")
            else:
                require(sample.get("oldVsSkiaOverWhiteSumAbsDelta") == 0, f"{sample.get('name')} background old residual changed")
    require(sample_count == 12, "old/new evidence sample count changed")
    require(stroke_count == 10, "old/new evidence stroke sample count changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        if key == "historicalArtifactsFOR329ToFOR341Rewritten":
            require(value is False, f"{key} changed")
        else:
            require(value is True, f"{key} not preserved")


def markdown_table(data: dict[str, Any]) -> str:
    rows = [
        "| group | sample | zone | old current | actual new | candidate new | Skia over white | old abs | actual abs | candidate abs |",
        "|---|---|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for cell in data["oldNewEvidence"]:
        group_id = cell["groupId"]
        for sample in cell["samples"]:
            rows.append(
                "| {group} | {sample} | {zone} | {old} | {actual} | {candidate} | {skia} | {old_abs} | {actual_abs} | {candidate_abs} |".format(
                    group=group_id,
                    sample=sample["name"],
                    zone=sample["zone"],
                    old=sample["oldCurrentFor339ExportRgba"],
                    actual=sample["actualNewRendererRgba"],
                    candidate=sample["candidateNewPolicyRgba"],
                    skia=sample["skiaReferenceOverWhiteRgba"],
                    old_abs=sample["oldVsSkiaOverWhiteSumAbsDelta"],
                    actual_abs=sample["actualNewVsSkiaOverWhiteSumAbsDelta"],
                    candidate_abs=sample["candidateNewVsSkiaOverWhiteSumAbsDelta"],
                )
            )
    return "\n".join(rows)


def build_report(data: dict[str, Any]) -> str:
    totals = data["residualTotals"]
    return f"""# FOR-342 CircularArcsStrokeButt Adjacent F16 Color Policy Scoped Implementation

Linear: `FOR-342`

Decision: `{data["decision"]}`

FOR-342 reuses the FOR-341 authorization for
`{AUTHORIZED_POLICY_ID}` and evaluates the smallest possible implementation
route for the exact two adjacent `CircularArcsStrokeButt` F16 cells. No renderer
behavior is changed in this ticket.

## Decision

The scoped renderer implementation is refused for this ticket because the safe
intervention point is not bounded enough. The only concrete hooks are global
F16 color conversion, global F16 blending, and the global
`SkBitmap.getPixelAsSrgb` encoded-export boundary. A renderer change limited to
these two cells would require fixture/coordinate-specific branching, while a
general change would be a broader color-management migration.

The stable follow-up route is to introduce an explicit color-policy boundary
with broader F16 evidence before changing global F16 renderer behavior.

## Residuals

- Samples: `{totals["sampleCount"]}`
- Stroke samples: `{totals["strokeSampleCount"]}`
- Old/current Skia-over-white residual: `{totals["oldCurrentOverWhiteResidual"]}`
- Actual-new renderer residual: `{totals["actualNewOverWhiteResidual"]}`
- Candidate-new policy residual: `{totals["candidateNewOverWhiteResidual"]}`
- Candidate residual reduction if a safe route exists: `{totals["candidateResidualReductionIfSafeRouteExists"]}`
- Raw transparent PNG residual rejected: `{totals["rawTransparentPngResidualRejected"]}`

## Old/New Pixel Evidence

`actual new` equals `old current` because the renderer was intentionally left
unchanged. `candidate new` records the FOR-341 authorized policy result and
matches Skia-over-white for all ten stroke samples.

{markdown_table(data)}

## Rejected Routes

- Fixture or coordinate-specific renderer branch for only the two
  FOR-339/FOR-340 cells.
- Global `colorToF16Premul` change.
- Global `blendF16PremulMode` change.
- Global `SkBitmap.getPixelAsSrgb` export-boundary change.
- Selected-cell extrapolation, crop evidence, or FOR-327 substitution.

## Non-goals Preserved

- No change to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No GPU/WGSL, geometry, coverage, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-341 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for342_circular_arcs_stroke_butt_adjacent_f16_color_policy_scoped_implementation.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-342-circular-arcs-stroke-butt-adjacent-f16-color-policy-scoped-implementation.md`

## Validation

{chr(10).join(f"- `{command}`" for command in VALIDATION_COMMANDS)}
"""


def main() -> None:
    for341 = load_json(FOR341_ARTIFACT)
    validate_for341(for341)
    data = build_artifact(for341)
    validate_artifact(data)
    artifact_text = json.dumps(data, indent=2) + "\n"
    report_text = build_report(data)
    write_if_changed(ARTIFACT, artifact_text)
    write_if_changed(REPORT, report_text)
    validate_artifact(load_json(ARTIFACT))
    if REPORT.read_text(encoding="utf-8") != report_text:
        fail(f"{rel(REPORT)} does not match generated report")


if __name__ == "__main__":
    main()
