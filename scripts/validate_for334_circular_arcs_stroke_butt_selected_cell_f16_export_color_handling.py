#!/usr/bin/env python3
"""Validate the FOR-334 selected-cell F16 export color handling evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-334"
SCENE_ID = "circular-arcs-stroke-butt-selected-cell-f16-export-color-handling-for334"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-334-circular-arcs-stroke-butt-selected-cell-f16-export-color-handling.md"
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
FOR333_REQUIRED_BOUNDARY = "f16-readback-and-png-encode"

FOR331_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331"
FOR331_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR331_SCENE_ID
    / f"{FOR331_SCENE_ID}.json"
)
FOR331_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COLORSPACE_PREMUL_SUSPECTED"
)

SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"
SK_PNG_ENCODER_TEST = PROJECT_ROOT / "kanvas-skia/src/test/kotlin/org/skia/encode/SkPngEncoderTest.kt"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-f16-readback-png-export-color-handling-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-333-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-boundary-identified-finding"
)

DECISION_CORRECTED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_EXPORT_COLOR_HANDLING_CORRECTED"
)
DECISION_PARTIAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_EXPORT_COLOR_HANDLING_PARTIAL_REQUIRES_COLOR_POLICY_DECISION"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_F16_EXPORT_COLOR_HANDLING_INPUT_INVALID"
)

VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.encode.SkPngEncoderTest",
    "rtk python3 scripts/validate_for334_circular_arcs_stroke_butt_selected_cell_f16_export_color_handling.py",
    "rtk python3 scripts/validate_for333_circular_arcs_stroke_butt_selected_cell_kotlin_cpu_runtime_trace.py",
    "rtk python3 scripts/validate_for332_circular_arcs_stroke_butt_selected_cell_cpu_color_pipeline_trace.py",
    "rtk python3 scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py",
    "rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]

# Skia D50-adapted matrices mirrored from SkNamedGamut.kt.
SRGB_TO_XYZ_D50 = [
    [0.436065674, 0.385147095, 0.143066406],
    [0.222488403, 0.716873169, 0.060607910],
    [0.013916016, 0.097076416, 0.714096069],
]
REC2020_TO_XYZ_D50 = [
    [0.673459, 0.165661, 0.125100],
    [0.279033, 0.675338, 0.0456288],
    [-0.00193139, 0.0299794, 0.797162],
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-334 validation failed: {message}")


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


def invert3(m: list[list[float]]) -> list[list[float]]:
    a, b, c = m[0]
    d, e, f = m[1]
    g, h, i = m[2]
    det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if det == 0:
        fail("sRGB matrix is singular")
    return [
        [(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det],
        [(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det],
        [(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det],
    ]


def matmul(a: list[list[float]], b: list[list[float]]) -> list[list[float]]:
    return [[sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)] for r in range(3)]


REC2020_TO_SRGB = matmul(invert3(SRGB_TO_XYZ_D50), REC2020_TO_XYZ_D50)


def srgb_decode(x: float) -> float:
    return x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4


def srgb_encode(x: float) -> float:
    if x <= 0.0:
        return 0.0
    if x >= 1.0:
        return 1.0
    return x * 12.92 if x <= 0.0031308 else 1.055 * (x ** (1.0 / 2.4)) - 0.055


def quantize_256(x: float) -> int:
    if math.isnan(x):
        return 0
    return max(0, min(255, int(x * 256.0)))


def f16_rec2020_premul_to_srgb_rgba(premul: list[float]) -> list[int]:
    r, g, b, a = premul
    alpha = quantize_256(a)
    if alpha == 0:
        return [0, 0, 0, 0]
    inv_a = 1.0 / a
    unpremul = [r * inv_a, g * inv_a, b * inv_a]
    linear = [srgb_decode(c) for c in unpremul]
    srgb_linear = [
        sum(REC2020_TO_SRGB[row][col] * linear[col] for col in range(3))
        for row in range(3)
    ]
    encoded = [srgb_encode(c) for c in srgb_linear]
    return [quantize_256(encoded[0]), quantize_256(encoded[1]), quantize_256(encoded[2]), alpha]


def abs_delta(a: list[int], b: list[int]) -> list[int]:
    return [abs(a[i] - b[i]) for i in range(4)]


def signed_delta(a: list[int], b: list[int]) -> list[int]:
    return [a[i] - b[i] for i in range(4)]


def sum_delta(delta: list[int]) -> int:
    return sum(delta)


def validate_sources() -> None:
    required = {
        SK_BITMAP: [
            "f16PremulToSrgbUnpremul",
            "f16ReadbackToSrgbXformCache",
            "org.skia.core.SkAlphaType.kPremul",
            "SkColorSpace.makeSRGB()",
        ],
        SK_PNG_ENCODER: [
            "F16 non-sRGB bitmaps are converted to",
            "untagged sRGB RGBA samples",
            "SkBitmap.getPixelAsSrgb",
            "src.getPixelAsSrgb(x, y)",
        ],
        SK_PNG_ENCODER_TEST: [
            "F16 Rec2020 PNG export uses explicit sRGB readback boundary",
            "getPixel remains the historical internal F16 Rec2020 byte oracle",
            "bitmap.getPixelAsSrgb(0, 0)",
            "0.836928904f",
            "oldDirectRec2020Readback",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_inputs(for333: dict[str, Any], for331: dict[str, Any]) -> None:
    require(for333.get("linear") == "FOR-333", "FOR-333 artifact identity changed")
    require(for333.get("decision") == FOR333_REQUIRED_DECISION, "FOR-333 decision changed")
    require(
        for333.get("identifiedFirstDivergentBoundary") == FOR333_REQUIRED_BOUNDARY,
        "FOR-333 first divergent boundary changed",
    )
    require(for331.get("linear") == "FOR-331", "FOR-331 artifact identity changed")
    require(for331.get("decision") == FOR331_REQUIRED_DECISION, "FOR-331 decision changed")
    samples = for333.get("samples")
    require(isinstance(samples, list), "FOR-333 samples missing")
    require(len(samples) == 13, "FOR-333 sample count must remain 13")


def build_samples(for333: dict[str, Any]) -> list[dict[str, Any]]:
    out = []
    for sample in for333["samples"]:
        name = sample["name"]
        skia = sample["expectedSkiaOverWhiteRgba"]
        before = sample["for331CpuRgba"]
        premul = sample["f16Readback"]["bitmapPremulF16AfterRender"]
        after = f16_rec2020_premul_to_srgb_rgba(premul)
        before_abs = abs_delta(before, skia)
        after_abs = abs_delta(after, skia)
        out.append(
            {
                "name": name,
                "zone": sample["zone"],
                "paintColor": sample["paintColor"],
                "x": sample["x"],
                "y": sample["y"],
                "skiaOverWhiteRgba": skia,
                "cpuBeforeCorrectionRgba": before,
                "cpuAfterCorrectionRgba": after,
                "cpuBeforeMinusSkiaSignedDelta": signed_delta(before, skia),
                "cpuAfterMinusSkiaSignedDelta": signed_delta(after, skia),
                "cpuBeforeVsSkiaAbsDelta": before_abs,
                "cpuAfterVsSkiaAbsDelta": after_abs,
                "sumAbsDeltaBefore": sum_delta(before_abs),
                "sumAbsDeltaAfter": sum_delta(after_abs),
                "sumAbsDeltaReduction": sum_delta(before_abs) - sum_delta(after_abs),
                "f16PremulAfterRender": premul,
                "readbackBoundary": "SkBitmap.getPixelAsSrgb kRGBA_F16Norm Rec2020 to sRGB",
                "pngBoundary": "SkPngEncoder.rgbaRow via SkBitmap.getPixelAsSrgb",
            }
        )
    return out


def build_metrics(samples: list[dict[str, Any]]) -> dict[str, Any]:
    before_total = sum(s["sumAbsDeltaBefore"] for s in samples)
    after_total = sum(s["sumAbsDeltaAfter"] for s in samples)
    improved = sum(1 for s in samples if s["sumAbsDeltaAfter"] < s["sumAbsDeltaBefore"])
    unchanged = sum(1 for s in samples if s["sumAbsDeltaAfter"] == s["sumAbsDeltaBefore"])
    worsened = sum(1 for s in samples if s["sumAbsDeltaAfter"] > s["sumAbsDeltaBefore"])
    stroke = [s for s in samples if str(s["zone"]).startswith("stroke")]
    return {
        "sampleCount": len(samples),
        "strokeSampleCount": len(stroke),
        "sumAbsDeltaBefore": before_total,
        "sumAbsDeltaAfter": after_total,
        "sumAbsDeltaReduction": before_total - after_total,
        "sumAbsDeltaReductionPercent": round((before_total - after_total) * 100.0 / before_total, 6),
        "improvedSamples": improved,
        "unchangedSamples": unchanged,
        "worsenedSamples": worsened,
        "strokeSumAbsDeltaBefore": sum(s["sumAbsDeltaBefore"] for s in stroke),
        "strokeSumAbsDeltaAfter": sum(s["sumAbsDeltaAfter"] for s in stroke),
        "maxSampleDeltaBefore": max(s["sumAbsDeltaBefore"] for s in samples),
        "maxSampleDeltaAfter": max(s["sumAbsDeltaAfter"] for s in samples),
    }


def build_artifact(for333: dict[str, Any], for331: dict[str, Any]) -> dict[str, Any]:
    samples = build_samples(for333)
    metrics = build_metrics(samples)
    decision = DECISION_CORRECTED if metrics["sumAbsDeltaAfter"] < metrics["sumAbsDeltaBefore"] else DECISION_PARTIAL
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "decision": decision,
        "allowedDecisions": [DECISION_CORRECTED, DECISION_PARTIAL, DECISION_INPUT_INVALID],
        "decisionReason": (
            "F16 Rec2020 export now applies Rec2020-to-sRGB conversion at the explicit "
            "SkBitmap.getPixelAsSrgb and SkPngEncoder.rgbaRow boundary. The selected-cell residual is reduced "
            "without geometry, coverage, F16 store, GPU, WGSL, threshold, fallback, Kadre, "
            "promotion, or score changes."
        ),
        "selectedCellOnly": True,
        "inputValidation": {
            "for333Artifact": rel(FOR333_ARTIFACT),
            "for333Decision": for333.get("decision"),
            "for333RequiredDecision": FOR333_REQUIRED_DECISION,
            "for333IdentifiedFirstDivergentBoundary": for333.get("identifiedFirstDivergentBoundary"),
            "for333RequiredBoundary": FOR333_REQUIRED_BOUNDARY,
            "for331Artifact": rel(FOR331_ARTIFACT),
            "for331Decision": for331.get("decision"),
            "for331RequiredDecision": FOR331_REQUIRED_DECISION,
        },
        "implementation": {
            "skBitmap": rel(SK_BITMAP),
            "skPngEncoder": rel(SK_PNG_ENCODER),
            "test": rel(SK_PNG_ENCODER_TEST),
            "correctedBoundary": "SkBitmap.getPixelAsSrgb kRGBA_F16Norm non-sRGB export readback",
            "internalGetPixelPreserved": "SkBitmap.getPixel keeps the historical internal F16 non-sRGB byte oracle",
            "pngCoherence": "SkPngEncoder materializes RGBA rows through SkBitmap.getPixelAsSrgb",
        },
        "metrics": metrics,
        "samples": samples,
        "promotion": {
            "selectedCellPromotionAllowed": False,
            "reason": "FOR-334 reduces the export residual but does not change promotion or score policy.",
        },
        "nonGoalsPreserved": {
            "arcGeometry": True,
            "coverage": True,
            "internalF16Store": True,
            "gpu": True,
            "wgsl": True,
            "thresholds": True,
            "fallbacks": True,
            "kadre": True,
            "promotion": True,
            "score": True,
            "historicalArtifactsFOR329ToFOR333Rewritten": False,
        },
        "validation": {
            "commands": VALIDATION_COMMANDS,
        },
    }


def build_report(artifact: dict[str, Any]) -> str:
    m = artifact["metrics"]
    rows = "\n".join(
        "| {name} | {skia} | {before} | {after} | {bd} | {ad} | {red} |".format(
            name=s["name"],
            skia=s["skiaOverWhiteRgba"],
            before=s["cpuBeforeCorrectionRgba"],
            after=s["cpuAfterCorrectionRgba"],
            bd=s["sumAbsDeltaBefore"],
            ad=s["sumAbsDeltaAfter"],
            red=s["sumAbsDeltaReduction"],
        )
        for s in artifact["samples"]
    )
    return f"""# FOR-334 CircularArcsStrokeButt Selected-Cell F16 Export Color Handling

Linear: `FOR-334`

Decision: `{artifact["decision"]}`

FOR-334 corrects the exported CPU F16 readback boundary for the selected
`CircularArcsStrokeButt` cell. `SkBitmap.getPixelAsSrgb` converts non-sRGB F16
premultiplied bitmap values to sRGB unpremultiplied `SkColor` values, while
`SkBitmap.getPixel` remains the historical internal byte oracle used by
renderer comparisons. `SkPngEncoder` remains coherent because PNG rows are
materialized through `SkBitmap.getPixelAsSrgb`.

## Scope

- Changed boundary: explicit `SkBitmap.getPixelAsSrgb` for `kRGBA_F16Norm` non-sRGB exports.
- Preserved boundary: `SkBitmap.getPixel` for internal renderer/test byte oracles.
- PNG coherence: `SkPngEncoder` uses the same explicit sRGB result for RGBA rows.
- Not changed: arc geometry, coverage, internal F16 store, GPU, WGSL, thresholds,
  fallback policy, Kadre, promotion, or score.

## Impact

- 13 FOR-333 samples compared before and after.
- Sum absolute delta before: `{m["sumAbsDeltaBefore"]}`.
- Sum absolute delta after: `{m["sumAbsDeltaAfter"]}`.
- Reduction: `{m["sumAbsDeltaReduction"]}` ({m["sumAbsDeltaReductionPercent"]}%).
- Improved samples: `{m["improvedSamples"]}`.
- Unchanged samples: `{m["unchangedSamples"]}`.
- Worsened samples: `{m["worsenedSamples"]}`.

Promotion remains explicitly forbidden because FOR-334 only corrects the export
boundary and does not change score or promotion policy.

## Samples

| sample | Skia over white | CPU before | CPU after | abs before | abs after | reduction |
|---|---:|---:|---:|---:|---:|---:|
{rows}

## Artifacts

- JSON: `reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/{SCENE_ID}.json`
- Validator: `scripts/validate_for334_circular_arcs_stroke_butt_selected_cell_f16_export_color_handling.py`
- Report: `reports/wgsl-pipeline/2026-06-04-for-334-circular-arcs-stroke-butt-selected-cell-f16-export-color-handling.md`

## Validation

Required validation commands are listed in the JSON artifact. The implementation
handoff records the observed pass/fail status for this run.
"""


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "artifact linear id changed")
    require(data.get("sceneId") == SCENE_ID, "artifact scene id changed")
    require(data.get("decision") == DECISION_CORRECTED, "FOR-334 expected corrected decision")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == 13, "artifact must contain 13 samples")
    metrics = data.get("metrics")
    require(isinstance(metrics, dict), "metrics missing")
    require(metrics.get("sumAbsDeltaBefore") == 231, "unexpected before residual")
    require(metrics.get("sumAbsDeltaAfter") == 132, "unexpected after residual")
    require(metrics.get("worsenedSamples") == 0, "no sample may worsen")
    require(data.get("promotion", {}).get("selectedCellPromotionAllowed") is False, "promotion must remain forbidden")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    require(non_goals.get("historicalArtifactsFOR329ToFOR333Rewritten") is False, "historical rewrite flag changed")


def main() -> None:
    validate_sources()
    for333 = load_json(FOR333_ARTIFACT)
    for331 = load_json(FOR331_ARTIFACT)
    validate_inputs(for333, for331)
    artifact = build_artifact(for333, for331)
    write_if_changed(ARTIFACT, json.dumps(artifact, indent=2, ensure_ascii=True) + "\n")
    write_if_changed(REPORT, build_report(artifact))
    validate_artifact(load_json(ARTIFACT))
    print("FOR-334 validation passed")


if __name__ == "__main__":
    main()
