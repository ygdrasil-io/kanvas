#!/usr/bin/env python3
"""Validate the FOR-335 selected-cell F16 blend policy evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-335"
SCENE_ID = "circular-arcs-stroke-butt-selected-cell-f16-blend-policy-for335"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-335-circular-arcs-stroke-butt-selected-cell-f16-blend-policy.md"
)

FOR334_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-f16-export-color-handling-for334"
FOR334_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR334_SCENE_ID
    / f"{FOR334_SCENE_ID}.json"
)
FOR334_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_EXPORT_COLOR_HANDLING_CORRECTED"
)

FOR333_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333"
FOR333_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR333_SCENE_ID
    / f"{FOR333_SCENE_ID}.json"
)
FOR333_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_BOUNDARY_IDENTIFIED"
)

SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"
SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-f16-blend-color-policy-ticket"
)
SOURCE_FINDINGS = [
    "global/kanvas/findings/for-334-circular-arcs-stroke-butt-f16-export-color-handling-corrected-finding",
    "global/kanvas/findings/for-333-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-boundary-identified-finding",
]

DECISION_BOUNDARY_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_BOUNDARY_IDENTIFIED"
)
DECISION_CORRECTED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_CORRECTED"
DECISION_REQUIRES_POLICY = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_REQUIRES_RENDERER_COLOR_POLICY_DECISION"
)
DECISION_INPUT_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_BLEND_POLICY_INPUT_INVALID"
ALLOWED_DECISIONS = [
    DECISION_BOUNDARY_IDENTIFIED,
    DECISION_CORRECTED,
    DECISION_REQUIRES_POLICY,
    DECISION_INPUT_INVALID,
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py",
    "rtk python3 scripts/validate_for334_circular_arcs_stroke_butt_selected_cell_f16_export_color_handling.py",
    "rtk python3 scripts/validate_for333_circular_arcs_stroke_butt_selected_cell_kotlin_cpu_runtime_trace.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-335 validation failed: {message}")


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


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def quantize_256(x: float) -> int:
    if math.isnan(x):
        return 0
    return max(0, min(255, int(x * 256.0)))


def quantize_alpha_round(x: float) -> int:
    if math.isnan(x):
        return 0
    return max(0, min(255, int(x * 255.0 + 0.5)))


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(a[i] - b[i]) for i in range(4)]


def signed_delta(a: list[int], b: list[int]) -> list[int]:
    return [a[i] - b[i] for i in range(4)]


def sum_delta(delta: list[int]) -> int:
    return sum(delta)


def srgb_src_over_white(sample: dict[str, Any], *, quantized_alpha: bool) -> list[int]:
    rgba = sample["paintSource"]["rgba"]
    coverage = sample["strokeCoverage"]["coverageScale"]
    alpha = (rgba[3] / 255.0) * coverage
    if quantized_alpha:
        alpha = quantize_alpha_round(alpha) / 255.0
    r = rgba[0] / 255.0
    g = rgba[1] / 255.0
    b = rgba[2] / 255.0
    return [
        quantize_256(r * alpha + (1.0 - alpha)),
        quantize_256(g * alpha + (1.0 - alpha)),
        quantize_256(b * alpha + (1.0 - alpha)),
        255,
    ]


def validate_sources() -> None:
    required = {
        SK_BITMAP: [
            "public fun getPixelAsSrgb",
            "getPixel] preserves the historical internal byte oracle",
            "f16PremulToSrgbUnpremul",
        ],
        SK_PNG_ENCODER: [
            "SkBitmap.getPixelAsSrgb",
            "src.getPixelAsSrgb(x, y)",
        ],
        SK_BITMAP_DEVICE: [
            "colorToF16Premul(c: SkColor4f",
            "blendF16PremulMode",
            "srcPremulBeforeCoverageF16",
            "dstPremulAfterStoreF16",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_inputs(for333: dict[str, Any], for334: dict[str, Any]) -> None:
    require(for333.get("linear") == "FOR-333", "FOR-333 artifact identity changed")
    require(for333.get("decision") == FOR333_REQUIRED_DECISION, "FOR-333 decision changed")
    require(for334.get("linear") == "FOR-334", "FOR-334 artifact identity changed")
    require(for334.get("decision") == FOR334_REQUIRED_DECISION, "FOR-334 decision is not corrected")
    require(for334.get("metrics", {}).get("sumAbsDeltaAfter") == 132, "FOR-334 residual changed")
    require(for334.get("metrics", {}).get("worsenedSamples") == 0, "FOR-334 worsened sample count changed")


def policy_entry(policy_id: str, formula: str, rgba: list[int], skia: list[int], current: list[int]) -> dict[str, Any]:
    skia_abs = abs_delta(rgba, skia)
    current_abs = abs_delta(current, skia)
    return {
        "policyId": policy_id,
        "formula": formula,
        "rgba": rgba,
        "minusSkiaSignedDelta": signed_delta(rgba, skia),
        "vsSkiaAbsDelta": skia_abs,
        "sumAbsDeltaVsSkia": sum_delta(skia_abs),
        "sumAbsDeltaVsCurrentCpuAfterFor334": sum_delta(skia_abs) - sum_delta(current_abs),
        "worsensCurrentCpuAfterFor334": sum_delta(skia_abs) > sum_delta(current_abs),
    }


def build_samples(for333: dict[str, Any], for334: dict[str, Any]) -> list[dict[str, Any]]:
    current_by_name = {sample["name"]: sample for sample in for334["samples"]}
    out = []
    for sample in for333["samples"]:
        if not str(sample.get("zone")).startswith("stroke"):
            continue
        name = sample["name"]
        current = current_by_name[name]["cpuAfterCorrectionRgba"]
        skia = sample["expectedSkiaOverWhiteRgba"]
        current_abs = abs_delta(current, skia)
        candidates = [
            policy_entry(
                "straight_srgb_float_coverage_src_over_white",
                "source sRGB non-premul channels, alpha=(paintAlpha/255)*coverageScale, SrcOver over white, floor(channel*256)",
                srgb_src_over_white(sample, quantized_alpha=False),
                skia,
                current,
            ),
            policy_entry(
                "straight_srgb_quantized_alpha_src_over_white",
                "source sRGB non-premul channels, alpha=round((paintAlpha/255)*coverageScale*255)/255, SrcOver over white, floor(channel*256)",
                srgb_src_over_white(sample, quantized_alpha=True),
                skia,
                current,
            ),
        ]
        out.append(
            {
                "name": name,
                "zone": sample["zone"],
                "paintColor": sample["paintColor"],
                "x": sample["x"],
                "y": sample["y"],
                "coverage": {
                    "coverageSamples": sample["strokeCoverage"]["coverageSamples"],
                    "coverageMaxSamples": sample["strokeCoverage"]["coverageMaxSamples"],
                    "coverageScale": sample["strokeCoverage"]["coverageScale"],
                },
                "paintSourceRgba": sample["paintSource"]["rgba"],
                "paintColor4fAfterXform": sample["paintColorXformAndPremul"]["paintColor4fAfterXform"],
                "srcPremulBeforeCoverageF16": sample["paintColorXformAndPremul"]["srcPremulBeforeCoverageF16"],
                "srcPremulAfterCoverageF16": sample["srcOverF16Store"]["srcPremulAfterCoverageF16"],
                "dstPremulBeforeStoreF16": sample["srcOverF16Store"]["dstPremulBeforeStoreF16"],
                "dstPremulAfterStoreF16": sample["srcOverF16Store"]["dstPremulAfterStoreF16"],
                "skiaOverWhiteRgba": skia,
                "currentCpuAfterFor334Rgba": current,
                "currentCpuAfterFor334MinusSkiaSignedDelta": signed_delta(current, skia),
                "currentCpuAfterFor334VsSkiaAbsDelta": current_abs,
                "currentCpuAfterFor334SumAbsDelta": sum_delta(current_abs),
                "candidatePolicies": candidates,
            }
        )
    return out


def build_metrics(samples: list[dict[str, Any]]) -> dict[str, Any]:
    policy_ids = [p["policyId"] for p in samples[0]["candidatePolicies"]]
    policy_totals = {
        policy_id: sum(
            next(p for p in sample["candidatePolicies"] if p["policyId"] == policy_id)["sumAbsDeltaVsSkia"]
            for sample in samples
        )
        for policy_id in policy_ids
    }
    policy_worsened = {
        policy_id: sum(
            1
            for sample in samples
            if next(p for p in sample["candidatePolicies"] if p["policyId"] == policy_id)[
                "worsensCurrentCpuAfterFor334"
            ]
        )
        for policy_id in policy_ids
    }
    current_total = sum(sample["currentCpuAfterFor334SumAbsDelta"] for sample in samples)
    center_samples = [sample for sample in samples if sample["zone"] == "stroke-center"]
    best_policy = min(policy_totals, key=policy_totals.get)
    return {
        "strokeSampleCount": len(samples),
        "currentCpuAfterFor334StrokeSumAbsDelta": current_total,
        "candidatePolicySumAbsDelta": policy_totals,
        "candidatePolicyWorsenedSamplesVsCurrent": policy_worsened,
        "bestCandidatePolicyId": best_policy,
        "bestCandidatePolicySumAbsDelta": policy_totals[best_policy],
        "bestCandidateWorsenedSamplesVsCurrent": policy_worsened[best_policy],
        "strokeCenterCurrentCpuAfterFor334SumAbsDelta": sum(
            sample["currentCpuAfterFor334SumAbsDelta"] for sample in center_samples
        ),
        "strokeCenterBestCandidateSumAbsDelta": sum(
            next(p for p in sample["candidatePolicies"] if p["policyId"] == best_policy)["sumAbsDeltaVsSkia"]
            for sample in center_samples
        ),
    }


def build_artifact(for333: dict[str, Any], for334: dict[str, Any]) -> dict[str, Any]:
    samples = build_samples(for333, for334)
    metrics = build_metrics(samples)
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFindings": SOURCE_FINDINGS,
        "decision": DECISION_REQUIRES_POLICY,
        "allowedDecisions": ALLOWED_DECISIONS,
        "decisionReason": (
            "The remaining full-coverage stroke residual disappears under a straight sRGB "
            "SrcOver-over-white policy, while the current CPU path stores a Rec2020 F16 "
            "working-space SrcOver result before export. The best bounded candidate reduces "
            "the 8-stroke residual but worsens two existing FOR-334 samples, so this is not "
            "safe as a local renderer patch without an explicit renderer color policy decision."
        ),
        "identifiedRemainingBoundary": (
            "paintColorXformAndPremul/source premul plus SkBitmapDevice.blendF16PremulMode.kSrcOver "
            "working-space blend policy before SkBitmap.getPixelAsSrgb export"
        ),
        "selectedCellOnly": True,
        "inputValidation": {
            "for334Artifact": rel(FOR334_ARTIFACT),
            "for334Decision": for334.get("decision"),
            "for334RequiredDecision": FOR334_REQUIRED_DECISION,
            "for334StrokeResidualAfterExport": for334.get("metrics", {}).get("strokeSumAbsDeltaAfter"),
            "for333Artifact": rel(FOR333_ARTIFACT),
            "for333Decision": for333.get("decision"),
            "for333RequiredDecision": FOR333_REQUIRED_DECISION,
        },
        "policyComparison": {
            "currentPolicy": {
                "policyId": "current_cpu_rec2020_f16_src_over_then_srgb_export",
                "formula": (
                    "paint color transformed into destination Rec2020, premultiplied as F16, "
                    "coverage applied in F16 premul, SrcOver blended into the F16 destination, "
                    "then exported through SkBitmap.getPixelAsSrgb"
                ),
            },
            "candidatePolicies": [
                {
                    "policyId": "straight_srgb_float_coverage_src_over_white",
                    "description": "Use original sRGB source channels and float coverage alpha for SrcOver over white.",
                },
                {
                    "policyId": "straight_srgb_quantized_alpha_src_over_white",
                    "description": "Use original sRGB source channels and 8-bit rounded covered alpha for SrcOver over white.",
                },
            ],
        },
        "metrics": metrics,
        "samples": samples,
        "implementation": {
            "codeCorrectionApplied": False,
            "reason": (
                "The best local candidate is a renderer-wide color/blend policy change and "
                "would worsen two FOR-334 stroke samples in this bounded artifact."
            ),
        },
        "promotion": {
            "selectedCellPromotionAllowed": False,
            "reason": "FOR-335 only traces and bounds blend policy; it does not change score or promotion policy.",
        },
        "nonGoalsPreserved": {
            "arcGeometry": True,
            "coverage": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "skBitmapGetPixelInternalOracle": True,
            "skBitmapGetPixelAsSrgbExportBoundary": True,
            "historicalArtifactsFOR329ToFOR334Rewritten": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
        },
    }


def build_report(artifact: dict[str, Any]) -> str:
    metrics = artifact["metrics"]
    best_policy = metrics["bestCandidatePolicyId"]
    rows = "\n".join(
        "| {name} | {coverage} | {skia} | {current} | {current_delta} | {float_rgba} | {float_delta} | {quant_rgba} | {quant_delta} |".format(
            name=sample["name"],
            coverage=f'{sample["coverage"]["coverageSamples"]}/{sample["coverage"]["coverageMaxSamples"]}',
            skia=sample["skiaOverWhiteRgba"],
            current=sample["currentCpuAfterFor334Rgba"],
            current_delta=sample["currentCpuAfterFor334SumAbsDelta"],
            float_rgba=sample["candidatePolicies"][0]["rgba"],
            float_delta=sample["candidatePolicies"][0]["sumAbsDeltaVsSkia"],
            quant_rgba=sample["candidatePolicies"][1]["rgba"],
            quant_delta=sample["candidatePolicies"][1]["sumAbsDeltaVsSkia"],
        )
        for sample in artifact["samples"]
    )
    return f"""# FOR-335 CircularArcsStrokeButt Selected-Cell F16 Blend Policy

Linear: `FOR-335`

Decision: `{artifact["decision"]}`

FOR-335 bounds the selected-cell residual that remains after FOR-334. No
renderer code is changed. The artifact compares the 8 stroke samples against
the current CPU F16 path and two explicit straight-sRGB SrcOver-over-white
candidate policies.

## Result

The remaining boundary is:

`{artifact["identifiedRemainingBoundary"]}`

The best bounded candidate is `{best_policy}`. It reduces the 8-stroke sum
absolute delta from `{metrics["currentCpuAfterFor334StrokeSumAbsDelta"]}` to
`{metrics["bestCandidatePolicySumAbsDelta"]}`. It also worsens
`{metrics["bestCandidateWorsenedSamplesVsCurrent"]}` current FOR-334 samples,
so no local correction is applied in this ticket.

Full-coverage stroke centers are the strongest signal: the current CPU path has
sum absolute delta `{metrics["strokeCenterCurrentCpuAfterFor334SumAbsDelta"]}`,
while the best straight-sRGB policy has `{metrics["strokeCenterBestCandidateSumAbsDelta"]}`.
That points at the color transform / premul / SrcOver working-space policy, not
PNG export.

## Samples

| sample | coverage | Skia over white | current CPU after FOR-334 | current abs | sRGB float coverage | float abs | sRGB quantized alpha | quant abs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
{rows}

## Non-goals Preserved

- `SkBitmap.getPixel` remains the internal renderer/test oracle.
- `SkBitmap.getPixelAsSrgb` remains the encoded export boundary.
- No arc geometry, coverage, GPU, WGSL, threshold, fallback, Kadre, promotion,
  or score change.
- Historical artifacts FOR-329 through FOR-334 are not rewritten.

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for335_circular_arcs_stroke_butt_selected_cell_f16_blend_policy.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-335-circular-arcs-stroke-butt-selected-cell-f16-blend-policy.md`

## Validation

Required validation commands are listed in the JSON artifact. The handoff records
the observed pass/fail status for this run.
"""


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("decision") == DECISION_REQUIRES_POLICY, "FOR-335 expected renderer-policy decision")
    require(data.get("identifiedRemainingBoundary"), "remaining boundary missing")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == 8, "artifact must contain 8 stroke samples")
    metrics = data.get("metrics")
    require(isinstance(metrics, dict), "metrics missing")
    require(metrics.get("currentCpuAfterFor334StrokeSumAbsDelta") == 132, "unexpected current residual")
    require(metrics.get("bestCandidatePolicySumAbsDelta") == 52, "unexpected best candidate residual")
    require(metrics.get("bestCandidateWorsenedSamplesVsCurrent") == 2, "unexpected worsened-sample count")
    require(metrics.get("strokeCenterCurrentCpuAfterFor334SumAbsDelta") == 57, "unexpected center current residual")
    require(metrics.get("strokeCenterBestCandidateSumAbsDelta") == 0, "unexpected center best residual")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    require(non_goals.get("skBitmapGetPixelInternalOracle") is True, "getPixel oracle must be preserved")
    require(non_goals.get("skBitmapGetPixelAsSrgbExportBoundary") is True, "getPixelAsSrgb must be preserved")
    require(non_goals.get("historicalArtifactsFOR329ToFOR334Rewritten") is False, "historical rewrite flag changed")
    for sample in samples:
        candidates = sample.get("candidatePolicies")
        require(isinstance(candidates, list) and len(candidates) >= 2, f"{sample.get('name')}: candidates missing")
        require("currentCpuAfterFor334Rgba" in sample, f"{sample.get('name')}: current CPU missing")
        require("skiaOverWhiteRgba" in sample, f"{sample.get('name')}: Skia reference missing")


def main() -> None:
    validate_sources()
    for333 = load_json(FOR333_ARTIFACT)
    for334 = load_json(FOR334_ARTIFACT)
    validate_inputs(for333, for334)
    artifact = build_artifact(for333, for334)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-335 validation passed")


if __name__ == "__main__":
    main()
