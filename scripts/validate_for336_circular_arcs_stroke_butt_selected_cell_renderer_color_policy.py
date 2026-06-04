#!/usr/bin/env python3
"""Validate the FOR-336 selected-cell renderer color-policy decision."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-336"
SCENE_ID = "circular-arcs-stroke-butt-selected-cell-renderer-color-policy-for336"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-336-circular-arcs-stroke-butt-selected-cell-renderer-color-policy.md"
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

SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-renderer-color-policy-decision-for-circular-arcs-stroke-butt-f16-blend"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-335-circular-arcs-stroke-butt-f16-blend-policy-requires-renderer-color-policy-decision-finding"
)

DECISION_KEEP_WORKING_SPACE = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_KEEP_WORKING_SPACE_BLEND"
)
DECISION_ADOPT_STRAIGHT_SRGB = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_ADOPT_STRAIGHT_SRGB_REFERENCE"
)
DECISION_NEEDS_CROSS_SCENE = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_NEEDS_CROSS_SCENE_EVIDENCE"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_RENDERER_COLOR_POLICY_INPUT_INVALID"
)
ALLOWED_DECISIONS = [
    DECISION_KEEP_WORKING_SPACE,
    DECISION_ADOPT_STRAIGHT_SRGB,
    DECISION_NEEDS_CROSS_SCENE,
    DECISION_INPUT_INVALID,
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py",
    "rtk python3 scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py",
    "rtk python3 scripts/validate_for334_circular_arcs_stroke_butt_selected_cell_f16_export_color_handling.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-336 validation failed: {message}")


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


def validate_sources() -> None:
    required = {
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "getPixel] preserves the historical internal byte oracle",
        ],
        SK_BITMAP_DEVICE: [
            "colorToF16Premul(c: SkColor4f",
            "blendF16PremulMode",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_for335(data: dict[str, Any]) -> dict[str, Any]:
    require(data.get("linear") == "FOR-335", "FOR-335 artifact identity changed")
    require(data.get("decision") == FOR335_REQUIRED_DECISION, "FOR-335 decision is not the required policy gate")
    require(data.get("sourceMemory"), "FOR-335 source memory is missing")
    metrics = data.get("metrics")
    require(isinstance(metrics, dict), "FOR-335 metrics missing")
    expected = {
        "strokeSampleCount": 8,
        "currentCpuAfterFor334StrokeSumAbsDelta": 132,
        "bestCandidatePolicySumAbsDelta": 52,
        "bestCandidateWorsenedSamplesVsCurrent": 2,
        "strokeCenterCurrentCpuAfterFor334SumAbsDelta": 57,
        "strokeCenterBestCandidateSumAbsDelta": 0,
    }
    for key, value in expected.items():
        require(metrics.get(key) == value, f"FOR-335 metric {key} changed")
    best = metrics.get("bestCandidatePolicyId")
    require(best == "straight_srgb_quantized_alpha_src_over_white", "FOR-335 best candidate changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == 8, "FOR-335 must contain 8 selected-cell stroke samples")
    worsened = [
        sample["name"]
        for sample in samples
        if next(policy for policy in sample["candidatePolicies"] if policy["policyId"] == best)[
            "worsensCurrentCpuAfterFor334"
        ]
    ]
    require(len(worsened) == 2, "FOR-335 worsened edge sample set changed")
    return {"metrics": metrics, "worsenedSamples": worsened}


def policy(
    policy_id: str,
    title: str,
    behavior: str,
    residual_impact: str,
    regression_risk: str,
    validation_gates: list[str],
    recommendation: str,
    accepted_now: bool,
) -> dict[str, Any]:
    return {
        "policyId": policy_id,
        "title": title,
        "expectedBehavior": behavior,
        "knownResidualImpact": residual_impact,
        "regressionRisk": regression_risk,
        "requiredValidationGates": validation_gates,
        "recommendation": recommendation,
        "acceptedInFor336": accepted_now,
    }


def build_artifact(for335: dict[str, Any], analysis: dict[str, Any]) -> dict[str, Any]:
    metrics = analysis["metrics"]
    worsened = analysis["worsenedSamples"]
    current_residual = metrics["currentCpuAfterFor334StrokeSumAbsDelta"]
    best_residual = metrics["bestCandidatePolicySumAbsDelta"]
    center_current = metrics["strokeCenterCurrentCpuAfterFor334SumAbsDelta"]
    center_best = metrics["strokeCenterBestCandidateSumAbsDelta"]
    best_policy = metrics["bestCandidatePolicyId"]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for335Artifact": rel(FOR335_ARTIFACT),
            "for335Decision": for335.get("decision"),
            "for335RequiredDecision": FOR335_REQUIRED_DECISION,
            "for335BestCandidatePolicyId": best_policy,
        },
        "decision": DECISION_NEEDS_CROSS_SCENE,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "FOR-335 proves a local selected-cell improvement signal, but not a renderer-wide "
            "color policy. The straight-sRGB candidate reduces total residual and clears the "
            "full-coverage stroke centers, while worsening two antialiased edge samples that "
            "FOR-334 already improved. A behavior change touching F16 premul or SrcOver policy "
            "therefore needs cross-scene CPU/GPU evidence before implementation."
        ),
        "selectedPolicy": {
            "policyId": "require_cross_scene_evidence_before_renderer_color_policy_change",
            "selected": True,
            "codeChangeAllowedInThisTicket": False,
        },
        "candidatePolicies": [
            policy(
                "keep_current_rec2020_f16_working_space_blend",
                "Keep Rec.2020 F16 working-space blend",
                (
                    "Keep paintColorXformAndPremul, F16 premul coverage, and "
                    "SkBitmapDevice.blendF16PremulMode.kSrcOver as the renderer truth; "
                    "SkBitmap.getPixel remains the internal oracle and SkBitmap.getPixelAsSrgb "
                    "remains only the encoded export boundary."
                ),
                (
                    f"Preserves the current selected-cell residual of {current_residual}; "
                    f"full-coverage stroke centers remain at {center_current} residual."
                ),
                (
                    "Lowest immediate regression risk because existing CPU/GPU comparison "
                    "oracles stay stable, but it leaves the selected-cell mismatch unresolved."
                ),
                [
                    "FOR-335 remains complete with its renderer-policy decision gate.",
                    "GPU smoke and scene dashboard gates continue to compare against the current CPU oracle.",
                    "No historical FOR-329..FOR-335 artifact is rewritten.",
                ],
                (
                    "Do not claim selected-cell support complete; keep as the fallback default "
                    "unless cross-scene evidence contradicts it."
                ),
                False,
            ),
            policy(
                "adopt_straight_srgb_reference_for_selected_cell_class",
                "Adopt straight-sRGB reference",
                (
                    "Treat selected-cell evidence for this class as straight sRGB SrcOver over "
                    "white, with the best FOR-335 candidate using quantized covered alpha."
                ),
                (
                    f"Reduces selected-cell stroke residual from {current_residual} to "
                    f"{best_residual}; full-coverage centers go from {center_current} to "
                    f"{center_best}."
                ),
                (
                    f"Not accepted in FOR-336: the candidate worsens {len(worsened)} edge "
                    f"samples ({', '.join(worsened)}), so applying it locally could hide "
                    "coverage or quantization regressions."
                ),
                [
                    "CPU old/new pixel table for this selected cell and adjacent arc stroke cells.",
                    "GPU smoke proof that internal byte-oracle comparisons do not move unexpectedly.",
                    "Explicit test coverage for edge samples worsened by FOR-335.",
                    "No change to SkBitmap.getPixel or SkBitmap.getPixelAsSrgb semantics.",
                ],
                (
                    "Prepare an evidence ticket before any implementation ticket; acceptance "
                    "requires no silent edge regression."
                ),
                False,
            ),
            policy(
                "require_cross_scene_evidence_before_renderer_color_policy_change",
                "Require cross-scene evidence",
                (
                    "Block renderer color behavior changes until the same policy is evaluated "
                    "against selected-cell, adjacent cells, CPU dashboard evidence, and GPU "
                    "comparison gates."
                ),
                (
                    f"Keeps FOR-334/FOR-335 runtime behavior unchanged for now; records the "
                    f"candidate improvement {current_residual}->{best_residual} and center "
                    f"improvement {center_current}->{center_best} as evidence, not as behavior."
                ),
                (
                    "Balanced risk: avoids committing a global F16 blend policy from one "
                    "selected cell while preserving a clear path to prove or reject it."
                ),
                [
                    "FOR-336 validator passes and confirms FOR-335 prerequisite state.",
                    "Next ticket samples at least selected cell, adjacent arc-stroke cells, and one non-arc F16/blend scene.",
                    "CPU and GPU gates must pass before any change to colorToF16Premul or blendF16PremulMode.",
                    "Any proposed comparison-policy migration must explicitly keep or replace SkBitmap.getPixel as oracle.",
                ],
                (
                    "Open the next ticket as a cross-scene evidence artifact, not a renderer "
                    "patch. The implementation ticket can follow only if it proves no hidden "
                    "edge, GPU, threshold, or fallback regression."
                ),
                True,
            ),
        ],
        "metrics": {
            "selectedCellStrokeSampleCount": metrics["strokeSampleCount"],
            "currentWorkingSpaceResidual": current_residual,
            "bestStraightSrgbCandidatePolicyId": best_policy,
            "bestStraightSrgbResidual": best_residual,
            "residualReduction": current_residual - best_residual,
            "currentStrokeCenterResidual": center_current,
            "bestStraightSrgbStrokeCenterResidual": center_best,
            "worsenedEdgeSampleCount": len(worsened),
            "worsenedEdgeSamples": worsened,
        },
        "futureCodeChangeAcceptanceCriteria": [
            "Do not change colorToF16Premul unless the artifact proves the policy across selected-cell and cross-scene evidence.",
            "Do not change blendF16PremulMode unless old/new CPU samples and GPU smoke checks prove no silent oracle movement.",
            "Do not change SkBitmap.getPixel unless a complete comparison-oracle migration is explicitly approved.",
            "Do not change SkBitmap.getPixelAsSrgb unless encoded export behavior and PNG evidence are covered.",
            "Do not change geometry, coverage, WGSL, thresholds, fallback policy, Kadre, promotion, or score in a color-policy ticket.",
        ],
        "nextTicketRecommendation": {
            "type": "cross_scene_evidence_artifact",
            "title": "Prouver la politique couleur F16 renderer sur plusieurs scenes avant correction",
            "objective": (
                "Compare current Rec.2020 F16 working-space blend against straight-sRGB "
                "reference policy on selected-cell, adjacent CircularArcsStrokeButt cells, "
                "and at least one non-arc F16/blend scene before any renderer behavior change."
            ),
            "mustUseMemoryFirst": True,
            "mustNotImplementRendererPatch": True,
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "reason": "FOR-336 is a decision gate; the selected decision requires more evidence before code changes.",
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
            "historicalArtifactsFOR329ToFOR335Rewritten": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
        },
    }


def build_report(artifact: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {policy} | {impact} | {risk} | {accepted} |".format(
            policy=entry["title"],
            impact=entry["knownResidualImpact"],
            risk=entry["regressionRisk"],
            accepted="yes" if entry["acceptedInFor336"] else "no",
        )
        for entry in artifact["candidatePolicies"]
    )
    metrics = artifact["metrics"]
    gates = "\n".join(f"- {gate}" for gate in artifact["candidatePolicies"][2]["requiredValidationGates"])
    criteria = "\n".join(f"- {criterion}" for criterion in artifact["futureCodeChangeAcceptanceCriteria"])
    return f"""# FOR-336 CircularArcsStrokeButt Selected-Cell Renderer Color Policy

Linear: `FOR-336`

Decision: `{artifact["decision"]}`

FOR-336 turns the FOR-335 F16 blend-policy signal into an explicit renderer
color-policy decision. No renderer behavior is changed in this ticket.

## Decision

Kanvas should not adopt the straight-sRGB candidate or declare the current
Rec.2020 F16 working-space blend final from a single selected cell. The selected
policy is to require cross-scene evidence before any change to F16 premul,
SrcOver storage, or comparison-oracle behavior.

The straight-sRGB candidate is real evidence: it reduces the selected-cell
stroke residual from `{metrics["currentWorkingSpaceResidual"]}` to
`{metrics["bestStraightSrgbResidual"]}` and reduces full-coverage stroke-center
residual from `{metrics["currentStrokeCenterResidual"]}` to
`{metrics["bestStraightSrgbStrokeCenterResidual"]}`. It is still not safe as a
renderer patch because it worsens `{metrics["worsenedEdgeSampleCount"]}` edge
samples: `{", ".join(metrics["worsenedEdgeSamples"])}`.

## Candidate Policies

| policy | known residual impact | regression risk | accepted in FOR-336 |
|---|---|---|---:|
{rows}

## Required Gates For The Next Ticket

{gates}

## Future Code-Change Criteria

{criteria}

## Recommendation

Create the next ticket through memory as a cross-scene evidence artifact. It
should compare current Rec.2020 F16 working-space blend against the straight-sRGB
reference policy on the selected cell, adjacent `CircularArcsStrokeButt` cells,
and at least one non-arc F16/blend scene. It must not apply a renderer patch
until CPU and GPU evidence prove no hidden edge, threshold, fallback, or oracle
regression.

## Non-goals Preserved

- No changes to `colorToF16Premul` or `blendF16PremulMode`.
- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or
  score change.
- Historical artifacts FOR-329 through FOR-335 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-336-circular-arcs-stroke-butt-selected-cell-renderer-color-policy.md`

## Validation

Required validation commands are listed in the JSON artifact. The handoff records
the observed pass/fail status for this run.
"""


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_NEEDS_CROSS_SCENE, "FOR-336 must require cross-scene evidence")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    policies = data.get("candidatePolicies")
    require(isinstance(policies, list) and len(policies) == 3, "must compare 3 policy options")
    require(
        [entry["policyId"] for entry in policies]
        == [
            "keep_current_rec2020_f16_working_space_blend",
            "adopt_straight_srgb_reference_for_selected_cell_class",
            "require_cross_scene_evidence_before_renderer_color_policy_change",
        ],
        "policy comparison order changed",
    )
    require(sum(1 for entry in policies if entry.get("acceptedInFor336") is True) == 1, "exactly one policy must be selected")
    metrics = data.get("metrics")
    require(isinstance(metrics, dict), "metrics missing")
    require(metrics.get("currentWorkingSpaceResidual") == 132, "current residual changed")
    require(metrics.get("bestStraightSrgbResidual") == 52, "straight-sRGB residual changed")
    require(metrics.get("residualReduction") == 80, "residual reduction changed")
    require(metrics.get("currentStrokeCenterResidual") == 57, "center current residual changed")
    require(metrics.get("bestStraightSrgbStrokeCenterResidual") == 0, "center best residual changed")
    require(metrics.get("worsenedEdgeSampleCount") == 2, "worsened edge sample count changed")
    require(len(data.get("futureCodeChangeAcceptanceCriteria", [])) >= 5, "future code-change criteria missing")
    require(data.get("nextTicketRecommendation", {}).get("mustUseMemoryFirst") is True, "next ticket memory rule missing")
    require(data.get("implementation", {}).get("rendererBehaviorChanged") is False, "renderer behavior must not change")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in [
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
    ]:
        require(non_goals.get(key) is True, f"non-goal not preserved: {key}")
    require(non_goals.get("historicalArtifactsFOR329ToFOR335Rewritten") is False, "historical rewrite flag changed")


def main() -> None:
    validate_sources()
    for335 = load_json(FOR335_ARTIFACT)
    analysis = validate_for335(for335)
    artifact = build_artifact(for335, analysis)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-336 validation passed")


if __name__ == "__main__":
    main()
