#!/usr/bin/env python3
"""Validate the FOR-341 adjacent CircularArcsStrokeButt F16 color-policy decision."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-341"
SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-for341"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-341-circular-arcs-stroke-butt-adjacent-f16-color-policy-decision.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-adjacent-f16-color-policy-decision-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-340-circular-arcs-stroke-butt-adjacent-f16-skia-reference-captured-finding"
)

FOR339_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-runtime-trace-for339"
FOR339_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR339_SCENE_ID
    / f"{FOR339_SCENE_ID}.json"
)
FOR339_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_RUNTIME_TRACE_PARTIAL_REQUIRES_REFERENCE_SOURCE"
)

FOR340_SCENE_ID = "circular-arcs-stroke-butt-adjacent-f16-reference-for340"
FOR340_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR340_SCENE_ID
    / f"{FOR340_SCENE_ID}.json"
)
FOR340_REQUIRED_DECISION = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_REFERENCE_CAPTURED"

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_READY_FOR_SCOPED_IMPLEMENTATION"
DECISION_PARTIAL = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_PARTIAL_REQUIRES_MORE_EVIDENCE"
DECISION_INPUT_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_ADJACENT_F16_COLOR_POLICY_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_READY, DECISION_PARTIAL, DECISION_INPUT_INVALID]

CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
FUTURE_POLICY_ID = "adjacent_circular_arcs_stroke_butt_f16_straight_srgb_quantized_alpha_src_over_white"

EXPECTED_CELLS = {
    "adjacent_arc_stroke_start0_sweep45_target": {
        "rowIndex": 0,
        "columnIndex": 1,
        "startDegrees": 0,
        "sweepDegrees": 45,
        "strokeCap": "kButt_Cap",
        "aa": True,
        "paintAlpha": 100,
        "sampleCount": 6,
        "strokeSampleCount": 5,
        "rawReferenceSumAbsDelta": 3532,
        "overWhiteReferenceSumAbsDelta": 187,
    },
    "adjacent_arc_stroke_start0_sweep130_target": {
        "rowIndex": 0,
        "columnIndex": 3,
        "startDegrees": 0,
        "sweepDegrees": 130,
        "strokeCap": "kButt_Cap",
        "aa": True,
        "paintAlpha": 100,
        "sampleCount": 6,
        "strokeSampleCount": 5,
        "rawReferenceSumAbsDelta": 3533,
        "overWhiteReferenceSumAbsDelta": 188,
    },
}

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py",
    "rtk python3 scripts/validate_for340_circular_arcs_stroke_butt_adjacent_f16_reference.py",
    "rtk python3 scripts/validate_for339_circular_arcs_stroke_butt_adjacent_f16_runtime_trace.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-341 validation failed: {message}")


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


def validate_for339(for339: dict[str, Any]) -> None:
    require(for339.get("linear") == "FOR-339", "FOR-339 artifact identity changed")
    require(for339.get("decision") == FOR339_REQUIRED_DECISION, "FOR-339 decision changed")
    status = for339.get("captureStatus")
    require(isinstance(status, dict), "FOR-339 captureStatus missing")
    require(status.get("runtimeCapturedCellCount") == 2, "FOR-339 runtime capture count changed")
    require(status.get("isolatedSkiaReferenceCellCount") == 0, "FOR-339 history must not be rewritten")
    implementation = for339.get("implementation")
    require(isinstance(implementation, dict), "FOR-339 implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "FOR-339 renderer behavior changed")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "FOR-339 selected-cell extrapolation changed")


def validate_for340(for340: dict[str, Any]) -> None:
    require(for340.get("linear") == "FOR-340", "FOR-340 artifact identity changed")
    require(for340.get("decision") == FOR340_REQUIRED_DECISION, "FOR-340 reference decision changed")
    require(for340.get("sourceFindings") == ["global/kanvas/findings/for-339-circular-arcs-stroke-butt-adjacent-f16-runtime-trace-partial-finding"], "FOR-340 source finding changed")
    status = for340.get("captureStatus")
    require(isinstance(status, dict), "FOR-340 captureStatus missing")
    require(status.get("targetCellCount") == 2, "FOR-340 target count changed")
    require(status.get("isolatedSkiaReferenceCellCount") == 2, "FOR-340 reference count changed")
    require(status.get("referenceBoundaryAccessible") is True, "FOR-340 reference boundary not accessible")
    require(status.get("residualsComputed") is True, "FOR-340 residuals not computed")
    totals = for340.get("residualTotals")
    require(isinstance(totals, dict), "FOR-340 residualTotals missing")
    require(totals.get("sampleCount") == 12, "FOR-340 sample count changed")
    require(totals.get("strokeSampleCount") == 10, "FOR-340 stroke sample count changed")
    require(totals.get("rawReferenceSumAbsDelta") == 7065, "FOR-340 raw residual changed")
    require(totals.get("overWhiteReferenceSumAbsDelta") == 375, "FOR-340 over-white residual changed")
    rejected = for340.get("rejectedSources")
    require(isinstance(rejected, dict), "FOR-340 rejected sources missing")
    require(rejected.get("fullGmCrop", {}).get("accepted") is False, "FOR-340 accepts crop source")
    require(
        rejected.get("for339RuntimeExport", {}).get("acceptedAsSkiaReference") is False,
        "FOR-340 accepts Kanvas runtime export as Skia reference",
    )
    implementation = for340.get("implementation")
    require(isinstance(implementation, dict), "FOR-340 implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "FOR-340 renderer behavior changed")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "FOR-340 selected-cell extrapolation changed")


def runtime_samples_by_name(for339: dict[str, Any]) -> dict[str, dict[str, Any]]:
    samples: dict[str, dict[str, Any]] = {}
    for group in for339.get("targetCells", []):
        for sample in group.get("samples", []):
            samples[sample["name"]] = sample
    return samples


def build_sample(reference_sample: dict[str, Any], runtime_sample: dict[str, Any]) -> dict[str, Any]:
    name = reference_sample["name"]
    zone = reference_sample["zone"]
    current = reference_sample["currentFor339ExportRgba"]
    over_white = reference_sample["skiaReferenceOverWhiteRgba"]
    candidate = runtime_sample.get("candidateStraightSrgb", {})
    candidate_captured = candidate.get("captured") is True
    candidate_rgba = candidate.get("rgba") if candidate_captured else current
    candidate_delta = abs_delta(candidate_rgba, over_white)
    candidate_sum = sum(candidate_delta)
    is_stroke = str(zone).startswith("stroke")
    if is_stroke:
        require(candidate.get("policyId") == CANDIDATE_POLICY_ID, f"{name} candidate policy changed")
        require(candidate_captured is True, f"{name} missing candidate policy value")
        require(candidate_rgba == over_white, f"{name} candidate no longer matches Skia-over-white")
        require(candidate_sum == 0, f"{name} candidate residual should be zero")
    return {
        "name": name,
        "zone": zone,
        "localX": reference_sample["localX"],
        "localY": reference_sample["localY"],
        "currentFor339ExportRgba": current,
        "skiaReferenceRawRgba": reference_sample["skiaReferenceRawRgba"],
        "skiaReferenceOverWhiteRgba": over_white,
        "currentVsRawDeltaAbs": reference_sample["rawReferenceDeltaAbs"],
        "currentVsRawSumAbsDelta": reference_sample["rawReferenceSumAbsDelta"],
        "currentVsOverWhiteDeltaAbs": reference_sample["overWhiteReferenceDeltaAbs"],
        "currentVsOverWhiteSumAbsDelta": reference_sample["overWhiteReferenceSumAbsDelta"],
        "candidatePolicyId": CANDIDATE_POLICY_ID,
        "candidatePolicyApplicable": is_stroke,
        "candidatePolicyRgba": candidate_rgba,
        "candidateVsOverWhiteDeltaAbs": candidate_delta,
        "candidateVsOverWhiteSumAbsDelta": candidate_sum,
        "candidateMatchesSkiaOverWhite": candidate_sum == 0,
    }


def build_cell(reference_cell: dict[str, Any], runtime_samples: dict[str, dict[str, Any]]) -> dict[str, Any]:
    group_id = reference_cell["groupId"]
    expected = EXPECTED_CELLS[group_id]
    cell = reference_cell["cell"]
    for key in ("rowIndex", "columnIndex", "startDegrees", "sweepDegrees", "strokeCap", "aa", "paintAlpha"):
        require(cell.get(key) == expected[key], f"{group_id}.{key} changed")
    summary = reference_cell["residualSummary"]
    for key in ("sampleCount", "strokeSampleCount", "rawReferenceSumAbsDelta", "overWhiteReferenceSumAbsDelta"):
        require(summary.get(key) == expected[key], f"{group_id}.{key} residual summary changed")
    samples = []
    for reference_sample in reference_cell["samples"]:
        runtime_sample = runtime_samples.get(reference_sample["name"])
        require(runtime_sample is not None, f"{group_id}.{reference_sample['name']} missing in FOR-339")
        samples.append(build_sample(reference_sample, runtime_sample))
    candidate_sum = sum(sample["candidateVsOverWhiteSumAbsDelta"] for sample in samples)
    stroke_candidate_sum = sum(
        sample["candidateVsOverWhiteSumAbsDelta"] for sample in samples if sample["candidatePolicyApplicable"]
    )
    return {
        "groupId": group_id,
        "cell": {
            "fixtureId": cell["fixtureId"],
            "sourceGm": cell["sourceGm"],
            "rowIndex": cell["rowIndex"],
            "columnIndex": cell["columnIndex"],
            "startDegrees": cell["startDegrees"],
            "sweepDegrees": cell["sweepDegrees"],
            "strokeCap": cell["strokeCap"],
            "aa": cell["aa"],
            "paintAlpha": cell["paintAlpha"],
        },
        "reference": reference_cell["reference"],
        "sampleCount": summary["sampleCount"],
        "strokeSampleCount": summary["strokeSampleCount"],
        "currentVsRawResidual": summary["rawReferenceSumAbsDelta"],
        "currentVsOverWhiteResidual": summary["overWhiteReferenceSumAbsDelta"],
        "candidateVsOverWhiteResidual": candidate_sum,
        "candidateStrokeVsOverWhiteResidual": stroke_candidate_sum,
        "currentOverWhiteResidualReductionIfCandidateApplied": summary["overWhiteReferenceSumAbsDelta"] - candidate_sum,
        "samples": samples,
    }


def build_artifact(for339: dict[str, Any], for340: dict[str, Any]) -> dict[str, Any]:
    runtime_samples = runtime_samples_by_name(for339)
    reference_cells = for340.get("targetCells")
    require(isinstance(reference_cells, list) and len(reference_cells) == 2, "FOR-340 target cells missing")
    cells = [build_cell(cell, runtime_samples) for cell in reference_cells]
    current_raw = sum(cell["currentVsRawResidual"] for cell in cells)
    current_white = sum(cell["currentVsOverWhiteResidual"] for cell in cells)
    candidate_white = sum(cell["candidateVsOverWhiteResidual"] for cell in cells)
    stroke_candidate_white = sum(cell["candidateStrokeVsOverWhiteResidual"] for cell in cells)
    decision = DECISION_READY if candidate_white == 0 and stroke_candidate_white == 0 else DECISION_PARTIAL
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for339Artifact": rel(FOR339_ARTIFACT),
            "for339Decision": for339.get("decision"),
            "for339RequiredDecision": FOR339_REQUIRED_DECISION,
            "for340Artifact": rel(FOR340_ARTIFACT),
            "for340Decision": for340.get("decision"),
            "for340RequiredDecision": FOR340_REQUIRED_DECISION,
            "for340ReferenceBoundaryAccessible": True,
            "for339RuntimeExportAcceptedAsSkiaReference": False,
        },
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-340 supplies isolated upstream Skia references for both exact adjacent cells. "
            "The raw transparent PNG residual is rejected as an implementation basis because "
            "FOR-339 export samples are opaque. On the Skia-over-white basis, all ten stroke "
            "samples match the existing FOR-339 straight_srgb_quantized_alpha_src_over_white "
            "candidate exactly, reducing the current over-white residual from 375 to 0."
        ),
        "policyDecision": {
            "implementationAllowedNow": decision == DECISION_READY,
            "codeChangeAllowedInThisTicket": False,
            "futureTicketRequired": True,
            "selectedCandidatePolicyId": FUTURE_POLICY_ID,
            "sourceCandidatePolicyId": CANDIDATE_POLICY_ID,
            "candidatePolicyDescription": (
                "For these adjacent CircularArcsStrokeButt F16 stroke samples, compare and "
                "implement the color result as straight sRGB source channels with rounded "
                "covered alpha, SrcOver composited over white, matching the isolated Skia "
                "over-white reference."
            ),
            "rawTransparentPngBasisAcceptedForImplementation": False,
            "skiaOverWhiteBasisAcceptedForImplementation": True,
            "affectedCells": [cell["groupId"] for cell in cells],
            "requiredFutureGuards": [
                "Keep the implementation scoped to the two FOR-339/FOR-340 adjacent cells unless new evidence expands it.",
                "Add old/new pixel evidence for every sample listed in this artifact.",
                "Keep SkBitmap.getPixel as the internal renderer/test oracle unless an explicit oracle migration is approved.",
                "Keep SkBitmap.getPixelAsSrgb as the encoded export boundary unless an explicit export migration is approved.",
                "Do not change geometry, coverage, GPU/WGSL, thresholds, fallback policy, Kadre, promotion, or score.",
            ],
        },
        "residualTotals": {
            "sampleCount": sum(cell["sampleCount"] for cell in cells),
            "strokeSampleCount": sum(cell["strokeSampleCount"] for cell in cells),
            "currentVsRawResidual": current_raw,
            "currentVsOverWhiteResidual": current_white,
            "candidateVsOverWhiteResidual": candidate_white,
            "currentOverWhiteResidualReductionIfCandidateApplied": current_white - candidate_white,
            "residualBasis": [
                "raw Skia transparent PNG RGBA compared to current FOR-339 getPixelAsSrgb export RGBA",
                "Skia PNG alpha-composited over white compared to current FOR-339 getPixelAsSrgb export RGBA",
                "FOR-339 straight_srgb_quantized_alpha_src_over_white candidate compared to Skia-over-white RGBA",
            ],
        },
        "targetCells": cells,
        "rejectedSources": {
            "selectedCellFor327": {
                "accepted": False,
                "reason": "FOR-327 is the selected 90-degree cell, not either adjacent FOR-339/FOR-340 cell.",
            },
            "fullGmPng": {"accepted": False, "reason": "full-GM PNG is not an isolated adjacent-cell render."},
            "fullGmCrop": {"accepted": False, "reason": "crop-based adjacent reference is forbidden."},
            "for339RuntimeExport": {
                "acceptedAsSkiaReference": False,
                "reason": "FOR-339 is Kanvas runtime export data and is only the current comparison side.",
            },
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "evidenceOnly": True,
            "selectedCellExtrapolationUsed": False,
            "for327SubstitutionUsed": False,
            "cropEvidenceUsed": False,
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
            "historicalArtifactsFOR329ToFOR340Rewritten": False,
        },
        "validation": {"commands": VALIDATION_COMMANDS},
    }


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_READY, "FOR-341 expected ready-for-scoped-implementation decision")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    policy = data.get("policyDecision")
    require(isinstance(policy, dict), "policyDecision missing")
    require(policy.get("implementationAllowedNow") is True, "implementation should be allowed for a future scoped ticket")
    require(policy.get("codeChangeAllowedInThisTicket") is False, "FOR-341 must not allow code change in this ticket")
    require(policy.get("selectedCandidatePolicyId") == FUTURE_POLICY_ID, "candidate policy changed")
    require(policy.get("rawTransparentPngBasisAcceptedForImplementation") is False, "raw PNG basis accepted")
    require(policy.get("skiaOverWhiteBasisAcceptedForImplementation") is True, "over-white basis not accepted")
    totals = data.get("residualTotals")
    require(isinstance(totals, dict), "residualTotals missing")
    require(totals.get("sampleCount") == 12, "sample count changed")
    require(totals.get("strokeSampleCount") == 10, "stroke sample count changed")
    require(totals.get("currentVsRawResidual") == 7065, "raw residual changed")
    require(totals.get("currentVsOverWhiteResidual") == 375, "over-white residual changed")
    require(totals.get("candidateVsOverWhiteResidual") == 0, "candidate should match over-white reference")
    require(totals.get("currentOverWhiteResidualReductionIfCandidateApplied") == 375, "residual reduction changed")
    cells = data.get("targetCells")
    require(isinstance(cells, list) and len(cells) == 2, "expected two target cells")
    for cell in cells:
        group_id = cell.get("groupId")
        require(group_id in EXPECTED_CELLS, f"unexpected cell {group_id!r}")
        expected = EXPECTED_CELLS[group_id]
        require(cell.get("sampleCount") == expected["sampleCount"], f"{group_id} sample count changed")
        require(cell.get("strokeSampleCount") == expected["strokeSampleCount"], f"{group_id} stroke count changed")
        require(cell.get("currentVsRawResidual") == expected["rawReferenceSumAbsDelta"], f"{group_id} raw residual changed")
        require(
            cell.get("currentVsOverWhiteResidual") == expected["overWhiteReferenceSumAbsDelta"],
            f"{group_id} over-white residual changed",
        )
        require(cell.get("candidateVsOverWhiteResidual") == 0, f"{group_id} candidate residual changed")
        samples = cell.get("samples")
        require(isinstance(samples, list) and len(samples) == expected["sampleCount"], f"{group_id} samples missing")
        stroke_samples = [sample for sample in samples if sample.get("candidatePolicyApplicable") is True]
        require(len(stroke_samples) == expected["strokeSampleCount"], f"{group_id} candidate stroke count changed")
        require(
            all(sample.get("candidateMatchesSkiaOverWhite") is True for sample in stroke_samples),
            f"{group_id} candidate samples do not match Skia-over-white",
        )
    implementation = data.get("implementation")
    require(isinstance(implementation, dict), "implementation block missing")
    require(implementation.get("rendererBehaviorChanged") is False, "renderer behavior changed")
    require(implementation.get("evidenceOnly") is True, "FOR-341 must be evidence-only")
    require(implementation.get("selectedCellExtrapolationUsed") is False, "selected-cell extrapolation used")
    require(implementation.get("for327SubstitutionUsed") is False, "FOR-327 substitution used")
    require(implementation.get("cropEvidenceUsed") is False, "crop evidence used")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "colorToF16Premul",
        "blendF16PremulMode",
        "skBitmapGetPixelInternalOracle",
        "skBitmapGetPixelAsSrgbExportBoundary",
        "geometry",
        "coveragePolicy",
        "gpu",
        "wgsl",
        "thresholds",
        "fallbacks",
        "kadre",
        "promotion",
        "score",
    ):
        require(non_goals.get(key) is True, f"non-goal guard changed: {key}")
    require(
        non_goals.get("historicalArtifactsFOR329ToFOR340Rewritten") is False,
        "historical artifact rewrite flag changed",
    )


def build_report(data: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {group} | {column} | {sweep} | {samples} | {raw} | {white} | {candidate} | {reduction} |".format(
            group=cell["groupId"],
            column=cell["cell"]["columnIndex"],
            sweep=cell["cell"]["sweepDegrees"],
            samples=cell["sampleCount"],
            raw=cell["currentVsRawResidual"],
            white=cell["currentVsOverWhiteResidual"],
            candidate=cell["candidateVsOverWhiteResidual"],
            reduction=cell["currentOverWhiteResidualReductionIfCandidateApplied"],
        )
        for cell in data["targetCells"]
    )
    sample_rows = "\n".join(
        "| {cell} | {sample} | {zone} | {current} | {over_white} | {current_delta} | {candidate} | {candidate_delta} |".format(
            cell=cell["groupId"],
            sample=sample["name"],
            zone=sample["zone"],
            current=sample["currentFor339ExportRgba"],
            over_white=sample["skiaReferenceOverWhiteRgba"],
            current_delta=sample["currentVsOverWhiteSumAbsDelta"],
            candidate=sample["candidatePolicyRgba"],
            candidate_delta=sample["candidateVsOverWhiteSumAbsDelta"],
        )
        for cell in data["targetCells"]
        for sample in cell["samples"]
    )
    guards = "\n".join(f"- {guard}" for guard in data["policyDecision"]["requiredFutureGuards"])
    validation = "\n".join(f"- `{command}`" for command in data["validation"]["commands"])
    totals = data["residualTotals"]
    return f"""# FOR-341 CircularArcsStrokeButt Adjacent F16 Color Policy Decision

Linear: `FOR-341`

Decision: `{data["decision"]}`

FOR-341 reads the FOR-339 runtime trace and the FOR-340 isolated upstream Skia
references for the exact adjacent `CircularArcsStrokeButt` F16 cells. It is a
decision artifact only; no renderer behavior is changed in this ticket.

## Decision

A future scoped implementation ticket is allowed for
`{data["policyDecision"]["selectedCandidatePolicyId"]}`.

The raw transparent PNG residual remains large (`{totals["currentVsRawResidual"]}`)
because the Skia reference PNGs have transparent background/alpha while the
FOR-339 export side is opaque `SkBitmap.getPixelAsSrgb` output. That raw basis is
not accepted for implementation.

The Skia-over-white basis is the implementation basis. It reduces the current
FOR-339 export residual from `{totals["currentVsOverWhiteResidual"]}` to
`{totals["candidateVsOverWhiteResidual"]}` across `{totals["sampleCount"]}`
samples. The ten stroke samples match the existing FOR-339
`{CANDIDATE_POLICY_ID}` candidate exactly.

## Cell Totals

| group | column | sweep | samples | raw residual | over-white residual | candidate over-white residual | reduction |
|---|---:|---:|---:|---:|---:|---:|---:|
{rows}

## Sample Deltas

| group | sample | zone | current export | Skia over white | current abs | candidate | candidate abs |
|---|---|---|---:|---:|---:|---:|---:|
{sample_rows}

## Future Implementation Scope

{guards}

## Rejected Inputs

- FOR-327 selected-cell reference is not used as adjacent-cell evidence.
- Full-GM PNGs and crops are not accepted.
- FOR-339 runtime export is only the current Kanvas comparison side, never the
  upstream Skia reference.
- The raw transparent PNG residual is recorded as evidence but is not accepted
  as the renderer policy basis.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-340 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for341_circular_arcs_stroke_butt_adjacent_f16_color_policy_decision.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-341-circular-arcs-stroke-butt-adjacent-f16-color-policy-decision.md`

## Validation

{validation}
"""


def main() -> None:
    for339 = load_json(FOR339_ARTIFACT)
    for340 = load_json(FOR340_ARTIFACT)
    validate_for339(for339)
    validate_for340(for340)
    artifact = build_artifact(for339, for340)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-341 validation passed")


if __name__ == "__main__":
    main()
