#!/usr/bin/env python3
"""Validate the FOR-337 cross-scene F16 color-policy evidence artifact."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-337"
SCENE_ID = "circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence-for337"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-337-circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence.md"
)

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-cross-scene-f16-renderer-color-policy-evidence-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-336-circular-arcs-stroke-butt-renderer-color-policy-needs-cross-scene-evidence-finding"
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

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR322_SCENE_ID
    / f"{FOR322_SCENE_ID}.json"
)
FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR327_SCENE_ID
    / f"{FOR327_SCENE_ID}.json"
)
M60_AA_DIAGNOSTIC = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/aa-residual-diagnostic.json"
)
M60_ROUTE_GPU = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json"
)

SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

DECISION_SUPPORTS_IMPLEMENTATION = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_SUPPORTS_IMPLEMENTATION"
)
DECISION_MIXED_REQUIRES_MORE_DATA = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_MIXED_REQUIRES_MORE_DATA"
)
DECISION_REJECTS_STRAIGHT_SRGB = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_REJECTS_STRAIGHT_SRGB"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_F16_COLOR_POLICY_CROSS_SCENE_EVIDENCE_INPUT_INVALID"
)
ALLOWED_DECISIONS = [
    DECISION_SUPPORTS_IMPLEMENTATION,
    DECISION_MIXED_REQUIRES_MORE_DATA,
    DECISION_REJECTS_STRAIGHT_SRGB,
    DECISION_INPUT_INVALID,
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py",
    "rtk python3 scripts/validate_for336_circular_arcs_stroke_butt_selected_cell_renderer_color_policy.py",
    "rtk python3 scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-337 validation failed: {message}")


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


def validate_inputs(for335: dict[str, Any], for336: dict[str, Any]) -> None:
    require(for335.get("linear") == "FOR-335", "FOR-335 artifact identity changed")
    require(for335.get("decision") == FOR335_REQUIRED_DECISION, "FOR-335 decision changed")
    metrics335 = for335.get("metrics")
    require(isinstance(metrics335, dict), "FOR-335 metrics missing")
    require(metrics335.get("strokeSampleCount") == 8, "FOR-335 stroke sample count changed")
    require(
        metrics335.get("currentCpuAfterFor334StrokeSumAbsDelta") == 132,
        "FOR-335 current residual changed",
    )
    require(metrics335.get("bestCandidatePolicySumAbsDelta") == 52, "FOR-335 candidate residual changed")
    require(
        metrics335.get("bestCandidateWorsenedSamplesVsCurrent") == 2,
        "FOR-335 worsened sample count changed",
    )

    require(for336.get("linear") == "FOR-336", "FOR-336 artifact identity changed")
    require(for336.get("decision") == FOR336_REQUIRED_DECISION, "FOR-336 decision changed")
    metrics336 = for336.get("metrics")
    require(isinstance(metrics336, dict), "FOR-336 metrics missing")
    require(metrics336.get("currentWorkingSpaceResidual") == 132, "FOR-336 current residual changed")
    require(metrics336.get("bestStraightSrgbResidual") == 52, "FOR-336 candidate residual changed")
    require(metrics336.get("worsenedEdgeSampleCount") == 2, "FOR-336 worsened edge count changed")


def residual_group(
    *,
    group_id: str,
    title: str,
    source_kind: str,
    sample_count: int,
    current_residual: int | None,
    candidate_residual: int | None,
    worsened_samples: int | None,
    data_comparable: bool,
    limitations: list[str],
    source_artifacts: list[str],
    notes: list[str],
) -> dict[str, Any]:
    residual_delta = None
    residual_reduction = None
    if current_residual is not None and candidate_residual is not None:
        residual_delta = candidate_residual - current_residual
        residual_reduction = current_residual - candidate_residual
    return {
        "groupId": group_id,
        "title": title,
        "sourceKind": source_kind,
        "sampleCount": sample_count,
        "currentResidual": current_residual,
        "candidateResidual": candidate_residual,
        "residualDeltaCandidateMinusCurrent": residual_delta,
        "residualReduction": residual_reduction,
        "worsenedSamples": worsened_samples,
        "dataComparableForPolicyDecision": data_comparable,
        "sourceLimitations": limitations,
        "sourceArtifacts": source_artifacts,
        "notes": notes,
    }


def build_selected_cell_group(for335: dict[str, Any], for336: dict[str, Any]) -> dict[str, Any]:
    metrics = for336["metrics"]
    return residual_group(
        group_id="selected_cell_instrumented_f16_rec2020",
        title="Selected CircularArcsStrokeButt cell with F16 runtime trace",
        source_kind="direct-selected-cell-runtime-trace",
        sample_count=metrics["selectedCellStrokeSampleCount"],
        current_residual=metrics["currentWorkingSpaceResidual"],
        candidate_residual=metrics["bestStraightSrgbResidual"],
        worsened_samples=metrics["worsenedEdgeSampleCount"],
        data_comparable=True,
        limitations=[
            "Only one isolated selected cell is instrumented through the FOR-333/FOR-335 F16 trace.",
            "The evidence is CPU/readback-side; it does not prove a GPU renderer policy.",
            "Two antialiased edge samples worsen under the best straight-sRGB candidate.",
        ],
        source_artifacts=[rel(FOR335_ARTIFACT), rel(FOR336_ARTIFACT)],
        notes=[
            f"Best candidate policy: {for335['metrics']['bestCandidatePolicyId']}",
            "Full-coverage stroke centers improve from 57 residual to 0.",
        ],
    )


def build_adjacent_arc_group(for322: dict[str, Any], for327: dict[str, Any]) -> dict[str, Any]:
    selected = for322.get("selectedCell", {})
    adjacent_candidates = [
        {"rowIndex": 0, "columnIndex": 1, "startDegrees": 0, "sweepDegrees": 45},
        {"rowIndex": 0, "columnIndex": 3, "startDegrees": 0, "sweepDegrees": 130},
        {"rowIndex": 1, "columnIndex": 2, "startDegrees": 10, "sweepDegrees": 90},
        {"rowIndex": 2, "columnIndex": 2, "startDegrees": 30, "sweepDegrees": 90},
    ]
    group = residual_group(
        group_id="adjacent_comparable_arc_stroke_cells",
        title="Adjacent/comparable CircularArcsStrokeButt cells",
        source_kind="grid-identified-but-not-runtime-traced",
        sample_count=len(adjacent_candidates),
        current_residual=None,
        candidate_residual=None,
        worsened_samples=None,
        data_comparable=False,
        limitations=[
            "Adjacent cells are identifiable from the GM grid, but no FOR-333-style runtime trace exists for them.",
            "No per-cell upstream Skia isolated reference or CPU F16 sample table exists for these candidates.",
            "Using the selected-cell residual for adjacent cells would be an extrapolation, so it is refused.",
        ],
        source_artifacts=[rel(FOR322_ARTIFACT), rel(FOR327_ARTIFACT)],
        notes=[
            "The selected cell is start=0 sweep=90 at row 0 column 2.",
            "The listed adjacent cells are future instrumentation targets, not evidence of policy safety.",
        ],
    )
    group["selectedCellAnchor"] = {
        "rowIndex": selected.get("rowIndex"),
        "columnIndex": selected.get("columnIndex"),
        "startDegrees": selected.get("startDegrees"),
        "sweepDegrees": selected.get("sweepDegrees"),
        "useCenter": selected.get("useCenter"),
        "aa": selected.get("aa"),
        "strokeCap": selected.get("strokeCap"),
    }
    group["candidateCells"] = adjacent_candidates
    group["skiaSelectedCellReferenceReady"] = for327.get("skiaPngReady") is True
    return group


def m60_current_sample_residual(samples: list[dict[str, Any]]) -> int:
    total = 0
    for sample in samples:
        ref = sample.get("referenceRgba")
        gpu = sample.get("gpuRgba")
        if isinstance(ref, list) and isinstance(gpu, list) and len(ref) == 4 and len(gpu) == 4:
            total += sum(abs(int(gpu[i]) - int(ref[i])) for i in range(4))
    return total


def build_non_arc_group(m60_aa: dict[str, Any], m60_route: dict[str, Any]) -> dict[str, Any]:
    raw_samples = m60_aa.get("highDeltaSamples", [])
    samples = raw_samples if isinstance(raw_samples, list) else []
    current_residual = m60_current_sample_residual(samples)
    group = residual_group(
        group_id="non_arc_blend_scene_available_but_not_f16_policy_comparable",
        title="Non-arc blend scene evidence gap",
        source_kind="non-arc-target-colorspace-blend-diagnostic",
        sample_count=len(samples),
        current_residual=current_residual,
        candidate_residual=None,
        worsened_samples=None,
        data_comparable=False,
        limitations=[
            "The available non-arc scene is a targetColorSpaceBlend diagnostic, not a Rec.2020 F16 CPU blend trace.",
            "It has reference/GPU high-delta samples, but no straight-sRGB candidate values.",
            "It proves that color/blend policy can be scene-sensitive, not that FOR-335's candidate is safe.",
        ],
        source_artifacts=[rel(M60_AA_DIAGNOSTIC), rel(M60_ROUTE_GPU)],
        notes=[
            f"Route status: {m60_route.get('status')}",
            f"Resolved root cause: {m60_route.get('resolvedRootCause')}",
            f"Remaining root cause: {m60_route.get('remainingRootCause')}",
        ],
    )
    group["diagnosticRoute"] = {
        "sceneId": m60_route.get("sceneId"),
        "drawKind": m60_route.get("drawKind"),
        "targetColorSpaceBlend": m60_aa.get("targetColorSpaceBlend"),
        "fallbackReason": m60_route.get("fallbackReason"),
        "edgeCount": m60_route.get("edgeCount"),
        "edgeBudget": m60_route.get("edgeBudget"),
    }
    return group


def build_artifact(
    for335: dict[str, Any],
    for336: dict[str, Any],
    for322: dict[str, Any],
    for327: dict[str, Any],
    m60_aa: dict[str, Any],
    m60_route: dict[str, Any],
) -> dict[str, Any]:
    groups = [
        build_selected_cell_group(for335, for336),
        build_adjacent_arc_group(for322, for327),
        build_non_arc_group(m60_aa, m60_route),
    ]
    comparable_groups = [group for group in groups if group["dataComparableForPolicyDecision"]]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": [SOURCE_FINDING],
        "inputValidation": {
            "for336Artifact": rel(FOR336_ARTIFACT),
            "for336Decision": for336.get("decision"),
            "for336RequiredDecision": FOR336_REQUIRED_DECISION,
            "for335Artifact": rel(FOR335_ARTIFACT),
            "for335Decision": for335.get("decision"),
            "for335RequiredDecision": FOR335_REQUIRED_DECISION,
        },
        "decision": DECISION_MIXED_REQUIRES_MORE_DATA,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "Only the selected cell has a comparable Rec.2020 F16 working-space versus "
            "straight-sRGB candidate residual table. Adjacent arc-stroke cells and the "
            "available non-arc blend scene expose source gaps rather than policy proof, "
            "so the evidence remains mixed and requires more data before any renderer change."
        ),
        "recommendation": {
            "type": "more_evidence_before_renderer_patch",
            "implementationAllowedNow": False,
            "nextTicketMustUseMemoryFirst": True,
            "requiredNextEvidence": [
                "Instrument adjacent CircularArcsStrokeButt cells with the same F16 store/readback sample schema.",
                "Add or identify a true non-arc Rec.2020 F16 SrcOver/blend scene with reference, current, and candidate samples.",
                "Keep SkBitmap.getPixel as internal oracle and SkBitmap.getPixelAsSrgb as export boundary unless explicitly migrated.",
            ],
        },
        "policyComparison": {
            "currentPolicy": "current_cpu_rec2020_f16_src_over_then_srgb_export",
            "candidatePolicy": "straight_srgb_quantized_alpha_src_over_white",
            "comparableGroupCount": len(comparable_groups),
            "requiredComparableGroupCountBeforeImplementation": 3,
        },
        "evidenceGroups": groups,
        "summary": {
            "groupCount": len(groups),
            "comparableGroupCount": len(comparable_groups),
            "selectedCellCurrentResidual": groups[0]["currentResidual"],
            "selectedCellCandidateResidual": groups[0]["candidateResidual"],
            "selectedCellResidualReduction": groups[0]["residualReduction"],
            "selectedCellWorsenedSamples": groups[0]["worsenedSamples"],
            "missingComparableGroups": [
                group["groupId"] for group in groups if not group["dataComparableForPolicyDecision"]
            ],
            "supportsImplementation": False,
            "rejectsStraightSrgb": False,
            "requiresMoreData": True,
        },
        "implementation": {
            "rendererBehaviorChanged": False,
            "reason": "FOR-337 is an evidence artifact only.",
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
            "historicalArtifactsFOR329ToFOR336Rewritten": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
        },
    }


def fmt(value: Any) -> str:
    return "n/a" if value is None else str(value)


def build_report(artifact: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {group} | {samples} | {current} | {candidate} | {delta} | {worse} | {comparable} |".format(
            group=group["groupId"],
            samples=group["sampleCount"],
            current=fmt(group["currentResidual"]),
            candidate=fmt(group["candidateResidual"]),
            delta=fmt(group["residualDeltaCandidateMinusCurrent"]),
            worse=fmt(group["worsenedSamples"]),
            comparable="yes" if group["dataComparableForPolicyDecision"] else "no",
        )
        for group in artifact["evidenceGroups"]
    )
    limitations = "\n".join(
        f"- `{group['groupId']}`: " + " ".join(group["sourceLimitations"])
        for group in artifact["evidenceGroups"]
    )
    validation = "\n".join(f"- `{command}`" for command in artifact["validation"]["commands"])
    missing = ", ".join(f"`{group}`" for group in artifact["summary"]["missingComparableGroups"])
    return f"""# FOR-337 CircularArcsStrokeButt F16 Color Policy Cross-Scene Evidence

Linear: `FOR-337`

Decision: `{artifact["decision"]}`

FOR-337 aggregates the available evidence before any renderer color-policy
change. It compares the current Rec.2020 F16 working-space blend/export path
against the straight-sRGB candidate from FOR-335/FOR-336 where the repository
has comparable sample data.

## Result

The selected cell still shows a real improvement signal: residual
`{artifact["summary"]["selectedCellCurrentResidual"]}` ->
`{artifact["summary"]["selectedCellCandidateResidual"]}`, with
`{artifact["summary"]["selectedCellWorsenedSamples"]}` worsened edge samples.

That is not enough for a renderer patch. Only
`{artifact["summary"]["comparableGroupCount"]}` of
`{artifact["policyComparison"]["requiredComparableGroupCountBeforeImplementation"]}`
required groups has comparable current-vs-candidate data. Missing comparable
groups: {missing}.

## Evidence Groups

| group | samples | current residual | candidate residual | candidate-current delta | worsened samples | comparable |
|---|---:|---:|---:|---:|---:|---|
{rows}

## Limitations

{limitations}

## Recommendation

Do not change `colorToF16Premul`, `blendF16PremulMode`, `SkBitmap.getPixel`, or
`SkBitmap.getPixelAsSrgb` from this ticket. The next ticket must go through
memory and add real comparable samples for adjacent arc-stroke cells plus a true
non-arc Rec.2020 F16 blend scene before an implementation ticket can be opened.

## Non-goals Preserved

- No renderer behavior change.
- No geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion, or score change.
- Historical artifacts FOR-329 through FOR-336 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for337_circular_arcs_stroke_butt_f16_color_policy_cross_scene_evidence.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-337-circular-arcs-stroke-butt-f16-color-policy-cross-scene-evidence.md`

## Validation

{validation}
"""


def validate_group(group: dict[str, Any]) -> None:
    for key in (
        "groupId",
        "sampleCount",
        "currentResidual",
        "candidateResidual",
        "residualDeltaCandidateMinusCurrent",
        "worsenedSamples",
        "sourceLimitations",
    ):
        require(key in group, f"{group.get('groupId', '<unknown>')} missing {key}")
    require(isinstance(group["sampleCount"], int) and group["sampleCount"] >= 0, "invalid sampleCount")
    require(isinstance(group["sourceLimitations"], list) and group["sourceLimitations"], "limitations missing")
    current = group["currentResidual"]
    candidate = group["candidateResidual"]
    if current is not None and candidate is not None:
        require(
            group["residualDeltaCandidateMinusCurrent"] == candidate - current,
            f"{group['groupId']} residual delta mismatch",
        )
        require(
            group["residualReduction"] == current - candidate,
            f"{group['groupId']} residual reduction mismatch",
        )
    else:
        require(
            group["dataComparableForPolicyDecision"] is False,
            f"{group['groupId']} cannot be comparable with incomplete residuals",
        )


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFindings") == [SOURCE_FINDING], "source finding changed")
    require(data.get("decision") == DECISION_MIXED_REQUIRES_MORE_DATA, "unexpected FOR-337 decision")
    require(data.get("allowedDecisions") == ALLOWED_DECISIONS, "allowed decisions changed")
    groups = data.get("evidenceGroups")
    require(isinstance(groups, list) and len(groups) >= 3, "expected at least three evidence groups")
    group_ids = {group.get("groupId") for group in groups}
    require(
        {
            "selected_cell_instrumented_f16_rec2020",
            "adjacent_comparable_arc_stroke_cells",
            "non_arc_blend_scene_available_but_not_f16_policy_comparable",
        }.issubset(group_ids),
        "required evidence groups missing",
    )
    for group in groups:
        validate_group(group)

    selected = next(group for group in groups if group["groupId"] == "selected_cell_instrumented_f16_rec2020")
    require(selected["sampleCount"] == 8, "selected-cell sample count changed")
    require(selected["currentResidual"] == 132, "selected-cell current residual changed")
    require(selected["candidateResidual"] == 52, "selected-cell candidate residual changed")
    require(selected["residualDeltaCandidateMinusCurrent"] == -80, "selected-cell residual delta changed")
    require(selected["worsenedSamples"] == 2, "selected-cell worsened samples changed")

    summary = data.get("summary")
    require(isinstance(summary, dict), "summary missing")
    require(summary.get("comparableGroupCount") == 1, "comparable group count changed")
    require(summary.get("requiresMoreData") is True, "FOR-337 must require more data")
    require(summary.get("supportsImplementation") is False, "FOR-337 must not support implementation")

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
        non_goals.get("historicalArtifactsFOR329ToFOR336Rewritten") is False,
        "historical artifact rewrite flag changed",
    )


def main() -> None:
    validate_source_guardrails()
    for335 = load_json(FOR335_ARTIFACT)
    for336 = load_json(FOR336_ARTIFACT)
    for322 = load_json(FOR322_ARTIFACT)
    for327 = load_json(FOR327_ARTIFACT)
    m60_aa = load_json(M60_AA_DIAGNOSTIC)
    m60_route = load_json(M60_ROUTE_GPU)
    validate_inputs(for335, for336)
    artifact = build_artifact(for335, for336, for322, for327, m60_aa, m60_route)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-337 validation passed")


if __name__ == "__main__":
    main()
