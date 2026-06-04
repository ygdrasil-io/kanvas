#!/usr/bin/env python3
"""Validate the FOR-338 comparable-sample instrumentation boundary."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-338"
SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-comparable-samples-for338"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-338-circular-arcs-stroke-butt-f16-color-policy-comparable-samples.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-comparable-adjacent-and-non-arc-f16-color-policy-samples-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-337-circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-mixed-finding"
)

FOR337_SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337"
FOR337_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR337_SCENE_ID
    / f"{FOR337_SCENE_ID}.json"
)
FOR337_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_MIXED_REQUIRES_MORE_DATA"
)

FOR336_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-renderer-color-policy-for336"
FOR336_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR336_SCENE_ID
    / f"{FOR336_SCENE_ID}.json"
)
FOR336_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_NEEDS_CROSS_SCENE_EVIDENCE"
)

FOR335_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-f16-blend-policy-for335"
FOR335_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR335_SCENE_ID
    / f"{FOR335_SCENE_ID}.json"
)
FOR335_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_REQUIRES_RENDERER_COLOR_POLICY_DECISION"
)

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

DECISION_CAPTURED = "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_CAPTURED"
DECISION_PARTIAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_PARTIAL_REQUIRES_MORE_INSTRUMENTATION"
)
DECISION_INPUT_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_COMPARABLE_SAMPLES_INPUT_INVALID"
ALLOWED_DECISIONS = [DECISION_CAPTURED, DECISION_PARTIAL, DECISION_INPUT_INVALID]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py",
    "rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py",
    "rtk python3 scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-338 validation failed: {message}")


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
            "[getPixel] preserves the historical internal byte oracle",
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


def validate_inputs(for335: dict[str, Any], for336: dict[str, Any], for337: dict[str, Any]) -> None:
    require(for335.get("linear") == "FOR-335", "FOR-335 artifact identity changed")
    require(for335.get("decision") == FOR335_REQUIRED_DECISION, "FOR-335 decision changed")
    metrics335 = for335.get("metrics")
    require(isinstance(metrics335, dict), "FOR-335 metrics missing")
    require(metrics335.get("strokeSampleCount") == 8, "FOR-335 selected sample count changed")
    require(metrics335.get("bestCandidatePolicyId") == "straight_srgb_quantized_alpha_src_over_white",
            "FOR-335 best candidate changed")
    require(metrics335.get("currentCpuAfterFor334StrokeSumAbsDelta") == 132, "FOR-335 residual changed")
    require(metrics335.get("bestCandidatePolicySumAbsDelta") == 52, "FOR-335 candidate residual changed")
    require(metrics335.get("bestCandidateWorsenedSamplesVsCurrent") == 2, "FOR-335 worsened count changed")

    require(for336.get("linear") == "FOR-336", "FOR-336 artifact identity changed")
    require(for336.get("decision") == FOR336_REQUIRED_DECISION, "FOR-336 decision changed")

    require(for337.get("linear") == "FOR-337", "FOR-337 artifact identity changed")
    require(for337.get("decision") == FOR337_REQUIRED_DECISION, "FOR-337 decision changed")
    require(for337.get("sourceFindings"), "FOR-337 source finding missing")
    summary337 = for337.get("summary")
    require(isinstance(summary337, dict), "FOR-337 summary missing")
    require(summary337.get("comparableGroupCount") == 1, "FOR-337 comparable group count changed")
    require(summary337.get("requiresMoreData") is True, "FOR-337 must still require more data")


def empty_residuals() -> dict[str, Any]:
    return {
        "sampleCount": 0,
        "currentResidual": None,
        "candidateResidual": None,
        "residualDeltaCandidateMinusCurrent": None,
        "residualReduction": None,
        "worsenedSamples": None,
        "dataComparableForPolicyDecision": False,
    }


def adjacent_group(group_id: str, cell: dict[str, Any]) -> dict[str, Any]:
    group = {
        "groupId": group_id,
        "title": "Adjacent CircularArcsStrokeButt cell comparable-sample target",
        "sourceKind": "adjacent-arc-stroke-target-no-runtime-trace",
        "requiredForCapturedDecision": True,
        "measuredEvidence": False,
        "futureTarget": True,
        "cell": cell,
        "requiredSampleSchema": [
            "isolated upstream Skia RGBA reference for this exact cell",
            "Kanvas CPU Rec.2020 F16 SrcOver current result after SkBitmap.getPixelAsSrgb export",
            "straight-sRGB candidate result using the FOR-335 candidate policy",
            "coverage scale and F16 store/readback values for every counted stroke sample",
        ],
        "sourceLimitations": [
            "FOR-337 only identified this cell; it did not capture a runtime F16 sample table for it.",
            "No checked-in isolated upstream Skia reference exists for this adjacent cell.",
            "Selected-cell FOR-335 residuals are not reused here because that would be extrapolation.",
        ],
        "sourceArtifacts": [rel(FOR337_ARTIFACT)],
    }
    group.update(empty_residuals())
    return group


def non_arc_group() -> dict[str, Any]:
    group = {
        "groupId": "non_arc_rec2020_f16_src_over_blend_target",
        "title": "Non-arc Rec.2020 F16 SrcOver/blend comparable-sample target",
        "sourceKind": "non-arc-rec2020-f16-blend-target-no-fixture",
        "requiredForCapturedDecision": True,
        "measuredEvidence": False,
        "futureTarget": True,
        "requiredSampleSchema": [
            "non-arc geometry or rect fixture rendered into Rec.2020 kRGBA_F16Norm",
            "source and destination colors using kSrcOver through blendF16PremulMode",
            "current exported RGBA, candidate straight-sRGB RGBA, and reference/expected RGBA",
            "sample table with residuals and worsened-sample count",
        ],
        "sourceLimitations": [
            "The repository has F16 blend tests, but they use sRGB destination space and do not satisfy this ticket.",
            "The FOR-337 M60 diagnostic is non-arc, but it is not a Rec.2020 F16 CPU SrcOver trace.",
            "No existing artifact supplies current/candidate comparable values for this group.",
        ],
        "sourceArtifacts": [rel(FOR337_ARTIFACT)],
    }
    group.update(empty_residuals())
    return group


def selected_prerequisite_group(for337: dict[str, Any]) -> dict[str, Any]:
    groups = for337["evidenceGroups"]
    selected = next(group for group in groups if group["groupId"] == "selected_cell_instrumented_f16_rec2020")
    return {
        "groupId": "selected_cell_prerequisite_from_for337",
        "title": "Selected cell prerequisite retained for comparison context",
        "sourceKind": "prior-measured-selected-cell-runtime-trace",
        "requiredForCapturedDecision": False,
        "measuredEvidence": True,
        "futureTarget": False,
        "sampleCount": selected["sampleCount"],
        "currentResidual": selected["currentResidual"],
        "candidateResidual": selected["candidateResidual"],
        "residualDeltaCandidateMinusCurrent": selected["residualDeltaCandidateMinusCurrent"],
        "residualReduction": selected["residualReduction"],
        "worsenedSamples": selected["worsenedSamples"],
        "dataComparableForPolicyDecision": True,
        "sourceLimitations": [
            "This is prior selected-cell evidence, not a newly captured FOR-338 adjacent/non-arc group.",
            "It remains useful only as a prerequisite baseline for the next instrumented samples.",
        ],
        "sourceArtifacts": selected["sourceArtifacts"],
    }


def build_artifact(for335: dict[str, Any], for336: dict[str, Any], for337: dict[str, Any]) -> dict[str, Any]:
    adjacent_cells = [
        {
            "rowIndex": 0,
            "columnIndex": 1,
            "startDegrees": 0,
            "sweepDegrees": 45,
            "useCenter": False,
            "aa": True,
            "strokeCap": "kButt_Cap",
            "paintAlpha": 100,
        },
        {
            "rowIndex": 0,
            "columnIndex": 3,
            "startDegrees": 0,
            "sweepDegrees": 130,
            "useCenter": False,
            "aa": True,
            "strokeCap": "kButt_Cap",
            "paintAlpha": 100,
        },
    ]
    groups = [
        selected_prerequisite_group(for337),
        adjacent_group("adjacent_arc_stroke_start0_sweep45_target", adjacent_cells[0]),
        adjacent_group("adjacent_arc_stroke_start0_sweep130_target", adjacent_cells[1]),
        non_arc_group(),
    ]
    newly_comparable = [
        group
        for group in groups
        if group["requiredForCapturedDecision"] and group["dataComparableForPolicyDecision"]
    ]
    decision = DECISION_PARTIAL
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for337Artifact": rel(FOR337_ARTIFACT),
            "for337Decision": for337.get("decision"),
            "for337RequiredDecision": FOR337_REQUIRED_DECISION,
            "for336Artifact": rel(FOR336_ARTIFACT),
            "for336Decision": for336.get("decision"),
            "for336RequiredDecision": FOR336_REQUIRED_DECISION,
            "for335Artifact": rel(FOR335_ARTIFACT),
            "for335Decision": for335.get("decision"),
            "for335RequiredDecision": FOR335_REQUIRED_DECISION,
        },
        "decision": decision,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-338 cannot honestly mark the missing adjacent and non-arc groups as captured "
            "without new isolated upstream references and a generalized opt-in Kotlin F16 trace. "
            "The patch therefore records the exact comparable-sample targets and keeps the "
            "policy decision partial instead of reusing selected-cell values as adjacent proof."
        ),
        "captureStatus": {
            "requiredAdjacentComparableGroupCount": 2,
            "capturedAdjacentComparableGroupCount": 0,
            "requiredNonArcComparableGroupCount": 1,
            "capturedNonArcComparableGroupCount": 0,
            "newComparableGroupCount": len(newly_comparable),
            "existingSelectedCellComparableGroupCount": 1,
            "capturedDecisionAllowed": False,
        },
        "evidenceGroups": groups,
        "summary": {
            "partial": True,
            "inputInvalid": False,
            "supportsImplementation": False,
            "implementationAllowedNow": False,
            "measuredSelectedCellResidualReduction": 80,
            "measuredSelectedCellWorsenedSamples": 2,
            "missingComparableGroups": [
                group["groupId"]
                for group in groups
                if group["requiredForCapturedDecision"] and not group["dataComparableForPolicyDecision"]
            ],
            "nextTicketMustUseMemoryFirst": True,
        },
        "requiredNextInstrumentation": [
            {
                "id": "adjacent-arc-kotlin-runtime-trace",
                "description": (
                    "Generalize the FOR-333 opt-in Kotlin trace so it can render at least two "
                    "adjacent CircularArcsStrokeButt cells and write per-sample F16 store/readback data."
                ),
                "mustNotChangeRenderer": True,
            },
            {
                "id": "adjacent-arc-isolated-skia-reference",
                "description": (
                    "Produce isolated upstream Skia references for those exact adjacent cells. "
                    "Do not substitute the selected-cell FOR-327 image or the full-GM crop."
                ),
                "mustNotUseSelectedCellExtrapolation": True,
            },
            {
                "id": "non-arc-rec2020-f16-src-over-fixture",
                "description": (
                    "Add a bounded non-arc Rec.2020 kRGBA_F16Norm SrcOver/blend fixture with "
                    "current, candidate, and reference/expected sample values."
                ),
                "mustNotUseM60TargetColorSpaceBlendAsSubstitute": True,
            },
        ],
        "implementation": {
            "rendererBehaviorChanged": False,
            "reason": "FOR-338 is an evidence/instrumentation boundary artifact only.",
        },
        "nonGoalsPreserved": {
            "colorToF16Premul": True,
            "blendF16PremulMode": True,
            "skBitmapGetPixelInternalOracle": True,
            "skBitmapGetPixelAsSrgbExportBoundary": True,
            "arcGeometry": True,
            "coverage": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "historicalArtifactsFOR329ToFOR337Rewritten": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
        },
    }


def fmt(value: Any) -> str:
    return "n/a" if value is None else str(value)


def build_report(artifact: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {group} | {samples} | {current} | {candidate} | {worse} | {comparable} | {measured} |".format(
            group=group["groupId"],
            samples=group["sampleCount"],
            current=fmt(group["currentResidual"]),
            candidate=fmt(group["candidateResidual"]),
            worse=fmt(group["worsenedSamples"]),
            comparable="yes" if group["dataComparableForPolicyDecision"] else "no",
            measured="yes" if group["measuredEvidence"] else "no",
        )
        for group in artifact["evidenceGroups"]
    )
    missing = ", ".join(f"`{group}`" for group in artifact["summary"]["missingComparableGroups"])
    next_steps = "\n".join(
        f"- `{step['id']}`: {step['description']}" for step in artifact["requiredNextInstrumentation"]
    )
    validation = "\n".join(f"- `{command}`" for command in artifact["validation"]["commands"])
    status = artifact["captureStatus"]
    return f"""# FOR-338 CircularArcsStrokeButt F16 Color Policy Comparable Samples

Linear: `FOR-338`

Decision: `{artifact["decision"]}`

FOR-338 keeps the color-policy work as evidence only. It does not change
renderer behavior, geometry, coverage, GPU, WGSL, thresholds, fallback policy,
Kadre, promotion, or score.

## Result

The requested adjacent and non-arc comparable samples are not captured yet.
The missing instrumentation is wider than a report-only patch because it needs
both generalized Kotlin F16 runtime traces and isolated upstream Skia references
for cells that FOR-337 only identified.

Captured comparable groups for this ticket:

- adjacent arc-stroke groups: `{status["capturedAdjacentComparableGroupCount"]}` /
  `{status["requiredAdjacentComparableGroupCount"]}`
- non-arc Rec.2020 F16 SrcOver/blend groups:
  `{status["capturedNonArcComparableGroupCount"]}` /
  `{status["requiredNonArcComparableGroupCount"]}`

Missing comparable groups: {missing}.

## Evidence Groups

| group | samples | current residual | candidate residual | worsened samples | comparable | measured |
|---|---:|---:|---:|---:|---|---|
{rows}

## Interpretation

The selected-cell signal from FOR-337 remains real: current residual `132`,
candidate residual `52`, and `2` worsened edge samples. FOR-338 refuses to
project those numbers onto adjacent cells. The adjacent groups and the non-arc
group are recorded as concrete instrumentation targets, not as measured policy
proof.

## Required Next Instrumentation

{next_steps}

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- Historical artifacts FOR-329 through FOR-337 are not rewritten.
- The next ticket must pass through Basic Memory before Linear writing or implementation.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for338_circular_arcs_stroke_butt_f16_color_policy_comparable_samples.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-338-circular-arcs-stroke-butt-f16-color-policy-comparable-samples.md`

## Validation

{validation}
"""


def validate_group(group: dict[str, Any]) -> None:
    for key in (
        "groupId",
        "sampleCount",
        "currentResidual",
        "candidateResidual",
        "worsenedSamples",
        "dataComparableForPolicyDecision",
        "sourceLimitations",
    ):
        require(key in group, f"{group.get('groupId', '<unknown>')} missing {key}")
    require(isinstance(group["sampleCount"], int) and group["sampleCount"] >= 0, "invalid sampleCount")
    require(isinstance(group["sourceLimitations"], list) and group["sourceLimitations"], "limitations missing")
    if group["dataComparableForPolicyDecision"]:
        require(group["currentResidual"] is not None, f"{group['groupId']} comparable without current residual")
        require(group["candidateResidual"] is not None, f"{group['groupId']} comparable without candidate residual")
    else:
        require(group["currentResidual"] is None, f"{group['groupId']} non-comparable must not claim current residual")
        require(group["candidateResidual"] is None, f"{group['groupId']} non-comparable must not claim candidate residual")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_PARTIAL, "FOR-338 must remain partial until comparable groups exist")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")

    status = data.get("captureStatus")
    require(isinstance(status, dict), "captureStatus missing")
    require(status.get("requiredAdjacentComparableGroupCount") == 2, "adjacent requirement changed")
    require(status.get("capturedAdjacentComparableGroupCount") == 0, "unexpected adjacent capture claim")
    require(status.get("requiredNonArcComparableGroupCount") == 1, "non-arc requirement changed")
    require(status.get("capturedNonArcComparableGroupCount") == 0, "unexpected non-arc capture claim")
    require(status.get("capturedDecisionAllowed") is False, "captured decision must not be allowed")

    groups = data.get("evidenceGroups")
    require(isinstance(groups, list) and len(groups) == 4, "expected four evidence groups")
    group_ids = {group.get("groupId") for group in groups}
    require("selected_cell_prerequisite_from_for337" in group_ids, "selected prerequisite missing")
    require("adjacent_arc_stroke_start0_sweep45_target" in group_ids, "first adjacent target missing")
    require("adjacent_arc_stroke_start0_sweep130_target" in group_ids, "second adjacent target missing")
    require("non_arc_rec2020_f16_src_over_blend_target" in group_ids, "non-arc target missing")
    for group in groups:
        validate_group(group)

    selected = next(group for group in groups if group["groupId"] == "selected_cell_prerequisite_from_for337")
    require(selected["sampleCount"] == 8, "selected prerequisite sample count changed")
    require(selected["currentResidual"] == 132, "selected prerequisite residual changed")
    require(selected["candidateResidual"] == 52, "selected prerequisite candidate residual changed")
    require(selected["worsenedSamples"] == 2, "selected prerequisite worsened count changed")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("partial") is True, "summary must be partial")
    require(summary.get("supportsImplementation") is False, "FOR-338 must not support implementation")
    require(summary.get("implementationAllowedNow") is False, "implementation must remain blocked")
    require(summary.get("nextTicketMustUseMemoryFirst") is True, "next ticket must use memory first")
    require(len(summary.get("missingComparableGroups", [])) == 3, "missing comparable group count changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "colorToF16Premul",
        "blendF16PremulMode",
        "skBitmapGetPixelInternalOracle",
        "skBitmapGetPixelAsSrgbExportBoundary",
        "arcGeometry",
        "coverage",
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
        non_goals.get("historicalArtifactsFOR329ToFOR337Rewritten") is False,
        "historical artifact rewrite flag changed",
    )


def main() -> None:
    validate_source_guardrails()
    for335 = load_json(FOR335_ARTIFACT)
    for336 = load_json(FOR336_ARTIFACT)
    for337 = load_json(FOR337_ARTIFACT)
    validate_inputs(for335, for336, for337)
    artifact = build_artifact(for335, for336, for337)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-338 validation passed")


if __name__ == "__main__":
    main()
