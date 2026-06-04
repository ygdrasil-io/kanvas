#!/usr/bin/env python3
"""Validate the FOR-332 CPU color pipeline boundary trace."""

from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-332"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-instrumented-cpu-color-pipeline-trace-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-331-circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-colorspace-premul-suspected-finding"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-for332"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-332-circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace.md"
)

FOR331_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331"
FOR331_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR331_SCENE_ID
FOR331_ARTIFACT = FOR331_DIR / f"{FOR331_SCENE_ID}.json"
FOR331_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-331-circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace.md"
)
FOR331_VALIDATOR = (
    PROJECT_ROOT / "scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py"
)

FOR330_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-white-background-diff-for330"
FOR330_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR330_SCENE_ID
    / f"{FOR330_SCENE_ID}.json"
)
FOR329_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329"
FOR329_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR329_SCENE_ID
    / f"{FOR329_SCENE_ID}.json"
)
FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR327_SCENE_ID
    / f"{FOR327_SCENE_ID}.json"
)
FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR322_SCENE_ID
    / f"{FOR322_SCENE_ID}.json"
)
FOR322_ROUTE_CPU = (
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID / "route-cpu.json"
)

TEST_UTILS = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt"
RASTER_SINK_F16 = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/dm/RasterSinkF16.kt"
SELECTED_CELL_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt"
)
SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"
SK_BITMAP = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt"
SK_PNG_ENCODER = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/encode/SkPngEncoder.kt"

DECISION_BOUNDARY_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_BOUNDARY_IDENTIFIED"
)
DECISION_REQUIRES_KOTLIN_INSTRUMENTATION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_INPUT_INVALID"
)
FOR331_DECISION_REQUIRED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COLORSPACE_PREMUL_SUSPECTED"
)
FOR330_DECISION_RESIDUAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT"
)
FOR329_DECISION_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED"
)
FOR327_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"

EXPECTED_FOR331_METRICS = {
    "totalPixels": 6400,
    "differentPixels": 2031,
    "matchingPixels": 4369,
    "cellSimilarityPercent": 68.265625,
    "maxDeltaByChannel": {"r": 39, "g": 43, "b": 31, "a": 0},
    "sumAbsDeltaByChannel": {"r": 33893, "g": 18839, "b": 10795, "a": 0},
    "sumAbsDeltaTotal": 63527,
    "differentPixelBoundingBox": {"left": 12, "top": 12, "right": 67, "bottom": 67},
    "differentPixelsOutsideExpectedStrokeBoundingBox": 0,
}
EXPECTED_HYPOTHESIS = {
    "id": "colorspace-premul-or-png-encode",
    "weight": 0.62,
    "status": "most-likely",
}
EXPECTED_SELECTED_CELL = {
    "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
    "sourceGm": "CircularArcsStrokeButtGM",
    "sourceRowId": "circular-arcs-stroke-butt-webgpu",
    "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
    "boundedHarnessGm": "circular-arcs-stroke-butt-selected-cell-harness-for322",
    "cellCount": 1,
    "quadrant": "bottom-left",
    "fullGmCanvasArcRectLTRB": [140, 520, 180, 560],
    "boundedCanvasArcRectLTRB": [20, 20, 60, 60],
    "rowIndex": 0,
    "columnIndex": 2,
    "startDegrees": 0,
    "sweepDegrees": 90,
    "complementSweepDegrees": -270,
    "useCenter": False,
    "aa": True,
    "style": "kStroke_Style",
    "strokeWidth": 15,
    "strokeCap": "kButt_Cap",
    "includedCaps": ["kButt_Cap"],
    "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
    "includesHairlineStrokeWidth0": False,
    "includesFill": False,
    "includesDash": False,
    "paintAlpha": 100,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}
EXPECTED_SAMPLE_NAMES = [
    "top_left_background",
    "top_edge_background",
    "left_edge_background",
    "blue_left_aa_edge",
    "blue_top_outer_edge",
    "arc_rect_top_left",
    "blue_top_stroke_center",
    "red_right_stroke_center",
    "red_bottom_stroke_center",
    "red_outer_edge",
    "red_bottom_outer_edge",
    "cell_center_hole",
    "bottom_right_background",
]
REQUIRED_SOURCE_SNIPPETS = {
    TEST_UTILS: [
        "public fun runGmTest(gm: GM): SkBitmap",
        "RasterSinkF16(DM_REFERENCE_COLOR_SPACE)",
    ],
    RASTER_SINK_F16: [
        "SkColorType.kRGBA_F16Norm",
        "bitmap.eraseColor(src.bgColor())",
    ],
    SELECTED_CELL_TEST: [
        "TestUtils.runGmTest(gm)",
        "SkPngEncoder.Encode(bitmap)",
        "this.color = (100 shl 24) or (color and 0x00FFFFFF)",
        "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
        "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
    ],
    SK_BITMAP_DEVICE: [
        "private fun transformPaintColor(c: SkColor4f): SkColor4f",
        "private fun colorToF16Premul(c: SkColor4f, out: FloatArray)",
        "fillPath(outline, ctm, clip, color4f, baseA, supers, shader, mode, blender)",
        "blendF16PremulMode(x, y, sr * cov, sg * cov, sb * cov, saCov, mode)",
        "pixels[i]     = sr + pixels[i]     * invSa",
    ],
    SK_BITMAP: [
        "SkColorType.kRGBA_F16Norm -> {",
        "val a = (pa * 256f).toInt().coerceIn(0, 255)",
        "f16PremulToSrgbUnpremul",
    ],
    SK_PNG_ENCODER: [
        "src.getPixel(x, y)",
        "row[offset++] = ((argb ushr 16) and 0xFF).toByte()",
        "the caller's [SkBitmap.colorSpace] is **not**",
    ],
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-332 validation failed: {message}")


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def optional_json(path: Path, invalid: list[str]) -> dict[str, Any] | None:
    if not path.is_file():
        invalid.append(f"missing JSON file: {rel(path)}")
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        invalid.append(f"invalid JSON file: {rel(path)}: {error}")
        return None
    if not isinstance(data, dict):
        invalid.append(f"{rel(path)} must contain a JSON object")
        return None
    return data


def sha256(path: Path) -> str | None:
    if not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def validate_cell(cell: dict[str, Any] | None, label: str, invalid: list[str]) -> None:
    if not isinstance(cell, dict):
        invalid.append(f"{label} missing")
        return
    for key, expected in EXPECTED_SELECTED_CELL.items():
        if cell.get(key) != expected:
            invalid.append(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def validate_prerequisites() -> tuple[dict[str, Any] | None, dict[str, Any], list[str]]:
    invalid: list[str] = []
    for331 = optional_json(FOR331_ARTIFACT, invalid)
    for330 = optional_json(FOR330_ARTIFACT, invalid)
    for329 = optional_json(FOR329_ARTIFACT, invalid)
    for327 = optional_json(FOR327_ARTIFACT, invalid)
    for322 = optional_json(FOR322_ARTIFACT, invalid)
    route_cpu = optional_json(FOR322_ROUTE_CPU, invalid)

    if for331 is not None:
        if for331.get("linear") != "FOR-331" or for331.get("sceneId") != FOR331_SCENE_ID:
            invalid.append("FOR-331 artifact identity changed")
        if for331.get("decision") != FOR331_DECISION_REQUIRED:
            invalid.append(f"FOR-331 decision must be {FOR331_DECISION_REQUIRED}")
        validate_cell(for331.get("selectedCell"), "FOR-331 selectedCell", invalid)
        input_validation = for331.get("inputValidation")
        if not isinstance(input_validation, dict) or input_validation.get("valid") is not True:
            invalid.append("FOR-331 inputValidation must remain valid")
        trace = for331.get("trace")
        metrics = trace.get("metrics") if isinstance(trace, dict) else None
        if not isinstance(metrics, dict):
            invalid.append("FOR-331 trace.metrics missing")
        else:
            for key, expected in EXPECTED_FOR331_METRICS.items():
                if metrics.get(key) != expected:
                    invalid.append(f"FOR-331 metric {key} expected {expected!r}, got {metrics.get(key)!r}")
        assessment = for331.get("assessment")
        hypotheses = assessment.get("hypotheses") if isinstance(assessment, dict) else None
        if not isinstance(hypotheses, list):
            invalid.append("FOR-331 assessment.hypotheses missing")
        else:
            main = next((item for item in hypotheses if item.get("id") == EXPECTED_HYPOTHESIS["id"]), None)
            if main is None:
                invalid.append("FOR-331 missing colorspace-premul-or-png-encode hypothesis")
            else:
                for key, expected in EXPECTED_HYPOTHESIS.items():
                    if main.get(key) != expected:
                        invalid.append(
                            f"FOR-331 hypothesis {EXPECTED_HYPOTHESIS['id']}.{key} "
                            f"expected {expected!r}, got {main.get(key)!r}"
                        )

    if for330 is not None:
        if for330.get("linear") != "FOR-330" or for330.get("decision") != FOR330_DECISION_RESIDUAL:
            invalid.append("FOR-330 residual prerequisite changed")
        validate_cell(for330.get("selectedCell"), "FOR-330 selectedCell", invalid)

    if for329 is not None:
        if for329.get("linear") != "FOR-329" or for329.get("decision") != FOR329_DECISION_IDENTIFIED:
            invalid.append("FOR-329 CPU raster audit prerequisite changed")
        validate_cell(for329.get("selectedCell"), "FOR-329 selectedCell", invalid)

    if for327 is not None:
        if for327.get("linear") != "FOR-327" or for327.get("decision") != FOR327_DECISION_READY:
            invalid.append("FOR-327 Skia reference prerequisite changed")
        validate_cell(for327.get("selectedCell"), "FOR-327 selectedCell", invalid)

    if for322 is not None:
        if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
            invalid.append("FOR-322 selected-cell harness prerequisite changed")
        validate_cell(for322.get("selectedCell"), "FOR-322 selectedCell", invalid)

    if route_cpu is not None:
        if route_cpu.get("backend") != "CPU":
            invalid.append("FOR-322 route-cpu backend changed")
        if route_cpu.get("selectedRoute") != "cpu.raster.selected-cell-test-harness":
            invalid.append("FOR-322 route-cpu selectedRoute changed")
        if route_cpu.get("supportStatus") != "not-supported":
            invalid.append("FOR-322 route-cpu support status changed")
        validate_cell(route_cpu.get("cell"), "FOR-322 route-cpu cell", invalid)

    for path, snippets in REQUIRED_SOURCE_SNIPPETS.items():
        if not path.is_file():
            invalid.append(f"missing inspected source file: {rel(path)}")
            continue
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            if snippet not in text:
                invalid.append(f"{rel(path)} missing required snippet: {snippet}")

    follows = {
        "for331Artifact": rel(FOR331_ARTIFACT),
        "for331Report": rel(FOR331_REPORT),
        "for331Validator": rel(FOR331_VALIDATOR),
        "for330Artifact": rel(FOR330_ARTIFACT),
        "for329Artifact": rel(FOR329_ARTIFACT),
        "for327Artifact": rel(FOR327_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for322RouteCpu": rel(FOR322_ROUTE_CPU),
    }
    inputs = {
        "for331Artifact": {
            "path": rel(FOR331_ARTIFACT),
            "sourceLinear": "FOR-331",
            "sourceSceneId": FOR331_SCENE_ID,
            "requiredDecision": FOR331_DECISION_REQUIRED,
            "sha256": sha256(FOR331_ARTIFACT),
        },
        "for330Artifact": {
            "path": rel(FOR330_ARTIFACT),
            "sourceLinear": "FOR-330",
            "sourceSceneId": FOR330_SCENE_ID,
            "requiredDecision": FOR330_DECISION_RESIDUAL,
            "sha256": sha256(FOR330_ARTIFACT),
        },
        "for329Artifact": {
            "path": rel(FOR329_ARTIFACT),
            "sourceLinear": "FOR-329",
            "sourceSceneId": FOR329_SCENE_ID,
            "requiredDecision": FOR329_DECISION_IDENTIFIED,
            "sha256": sha256(FOR329_ARTIFACT),
        },
        "for327Artifact": {
            "path": rel(FOR327_ARTIFACT),
            "sourceLinear": "FOR-327",
            "sourceSceneId": FOR327_SCENE_ID,
            "requiredDecision": FOR327_DECISION_READY,
            "sha256": sha256(FOR327_ARTIFACT),
        },
        "for322Artifact": {
            "path": rel(FOR322_ARTIFACT),
            "sourceLinear": "FOR-322",
            "sourceSceneId": FOR322_SCENE_ID,
            "requiredDecision": FOR322_DECISION_READY,
            "sha256": sha256(FOR322_ARTIFACT),
        },
        "inspectedSources": [
            {"path": rel(path), "sha256": sha256(path), "requiredSnippets": snippets}
            for path, snippets in REQUIRED_SOURCE_SNIPPETS.items()
        ],
    }
    return for331, {"follows": follows, "inputs": inputs}, invalid


def sample_boundary_classification(sample: dict[str, Any]) -> str:
    delta = sample.get("cpuVsSkiaOverWhiteAbsDelta")
    zone = sample.get("zone")
    skia_naive_delta = sample.get("skiaOverWhiteVsNaiveAbsDelta")
    if delta == [0, 0, 0, 0]:
        return "background-or-center-hole-exact-after-white-normalization"
    if zone == "stroke-center" and skia_naive_delta == [0, 0, 0, 0]:
        return "colorspace-premul-or-png-encode-boundary-candidate-runtime-unresolved"
    if zone == "stroke-aa-edge":
        return "coverage-plus-colorspace-premul-boundary-candidate-runtime-unresolved"
    return "stroke-residual-boundary-candidate-runtime-unresolved"


def abs_delta(left: list[int] | None, right: list[int] | None) -> list[int] | None:
    if left is None or right is None:
        return None
    return [abs(int(left[index]) - int(right[index])) for index in range(4)]


def build_samples(for331: dict[str, Any]) -> list[dict[str, Any]]:
    trace = for331.get("trace")
    if not isinstance(trace, dict):
        fail("FOR-331 trace missing")
    samples = trace.get("samples")
    if not isinstance(samples, list):
        fail("FOR-331 trace.samples missing")
    by_name = {sample.get("name"): sample for sample in samples if isinstance(sample, dict)}
    out: list[dict[str, Any]] = []
    for name in EXPECTED_SAMPLE_NAMES:
        sample = by_name.get(name)
        if sample is None:
            fail(f"FOR-331 sample missing: {name}")
        skia_rgba = sample.get("skiaRgba")
        skia_over_white_rgba = sample.get("skiaOverWhiteRgba")
        cpu_rgba = sample.get("cpuRgba")
        out.append(
            {
                "name": name,
                "zone": sample.get("zone"),
                "paintColor": sample.get("paintColor"),
                "x": sample.get("x"),
                "y": sample.get("y"),
                "skiaRgba": skia_rgba,
                "skiaOverWhiteRgba": skia_over_white_rgba,
                "cpuRgba": cpu_rgba,
                "naiveAlphaOverWhiteRgba": sample.get("naiveAlphaOverWhiteRgba"),
                "cpuVsSkiaOverWhiteAbsDelta": sample.get("cpuVsSkiaOverWhiteAbsDelta"),
                "deltaCpuVsSkiaOverWhiteRgba": sample.get("cpuVsSkiaOverWhiteAbsDelta"),
                "deltaCpuVsSkiaRgba": abs_delta(cpu_rgba, skia_rgba),
                "cpuMinusSkiaOverWhiteSignedDelta": sample.get("cpuMinusSkiaOverWhiteSignedDelta"),
                "skiaOverWhiteVsNaiveAbsDelta": sample.get("skiaOverWhiteVsNaiveAbsDelta"),
                "probableBoundaryClassification": sample_boundary_classification(sample),
                "boundaryProofStrength": "static-final-pixel-only",
            }
        )
    return out


def build_boundary_audit() -> list[dict[str, Any]]:
    return [
        {
            "boundaryId": "selected-cell-input-and-paint",
            "boundary": "selected-cell harness draw order, paint color, alpha, stroke, and PNG write request",
            "inspectedPoints": [
                {
                    "file": rel(SELECTED_CELL_TEST),
                    "symbols": ["SelectedCellGM.onDraw", "SelectedCellGM.paint", "writePng"],
                    "staticEvidence": [
                        "red arc 0..90 and blue arc 0..-270 are drawn into the 80x80 bounded cell",
                        "paint color packs alpha 100 with SK_ColorRED/SK_ColorBLUE RGB",
                        "evidence PNG is written through SkPngEncoder.Encode(bitmap)",
                    ],
                }
            ],
            "proved": [
                "paint source alpha is 100/255 for both arcs",
                "geometry and draw order match the selected cell audited by FOR-329/FOR-331",
            ],
            "notProved": [
                "post-color-xform float RGBA for any selected sample",
                "post-store F16 premul value for any selected sample",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
        {
            "boundaryId": "testutils-raster-sink-f16",
            "boundary": "GM execution surface creation and background/store format",
            "inspectedPoints": [
                {
                    "file": rel(TEST_UTILS),
                    "symbols": ["TestUtils.runGmTest"],
                    "staticEvidence": ["runGmTest routes through RasterSinkF16(DM_REFERENCE_COLOR_SPACE)"],
                },
                {
                    "file": rel(RASTER_SINK_F16),
                    "symbols": ["RasterSinkF16.draw"],
                    "staticEvidence": [
                        "bitmap is kRGBA_F16Norm",
                        "bitmap is tagged with DM_REFERENCE_COLOR_SPACE",
                        "bitmap.eraseColor(src.bgColor()) seeds the GM background",
                    ],
                },
            ],
            "proved": [
                "Kanvas CPU selected-cell output is rendered into F16 Rec.2020 before PNG encoding",
                "the CPU harness starts from GM background white, not transparent",
            ],
            "notProved": [
                "whether the residual is introduced before store or only during F16 readback/PNG encode",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
        {
            "boundaryId": "paint-color-xform-and-premul",
            "boundary": "paint color conversion from sRGB/unpremul into device-space premul F16 source",
            "inspectedPoints": [
                {
                    "file": rel(SK_BITMAP_DEVICE),
                    "symbols": ["transformPaintColor(SkColor4f)", "colorToF16Premul(SkColor4f)"],
                    "staticEvidence": [
                        "drawPath transforms solid paint color into bitmap color space as SkColor4f",
                        "F16 path premultiplies color4f before coverage and blend",
                    ],
                }
            ],
            "proved": [
                "the selected-cell CPU stroke uses a color-space conversion plus premul source contract before F16 blend",
            ],
            "notProved": [
                "the exact converted red/blue float values at the sampled stroke pixels",
                "whether converted values already match the final CPU residual direction",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
        {
            "boundaryId": "stroke-coverage",
            "boundary": "drawArc stroke lowering and AA coverage modulation",
            "inspectedPoints": [
                {
                    "file": rel(SK_BITMAP_DEVICE),
                    "symbols": ["drawPath", "fillPath", "blendF16PremulMode"],
                    "staticEvidence": [
                        "stroke style is lowered through SkStroker.fromPaint(...).stroke(effectivePath)",
                        "AA fillPath uses supersampling and folds coverage into premul source before blend",
                    ],
                }
            ],
            "proved": [
                "coverage participates before the F16 SrcOver store boundary",
                "FOR-331 residual remains inside the expected stroke bbox with 0 different pixels outside",
            ],
            "notProved": [
                "per-sample coverage values for the FOR-331 stroke-center and AA-edge samples",
                "whether AA coverage alone can explain edge samples after color/readback effects are removed",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
        {
            "boundaryId": "src-over-f16-store",
            "boundary": "premul F16 SrcOver blend and in-place store",
            "inspectedPoints": [
                {
                    "file": rel(SK_BITMAP_DEVICE),
                    "symbols": ["blendF16PremulMode", "blendF16Premul"],
                    "staticEvidence": [
                        "kSrcOver writes sr + dst * (1 - sa) directly into pixelsF16",
                        "no 8-bit quantization occurs inside the pure premul-float F16 SrcOver body",
                    ],
                }
            ],
            "proved": [
                "the final CPU store before PNG is premul float in the destination bitmap buffer",
            ],
            "notProved": [
                "stored premul float RGBA before readback for any selected sample",
                "whether store math or prior color/coverage inputs first diverge from Skia-over-white",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
        {
            "boundaryId": "f16-readback-and-png-encode",
            "boundary": "F16 premul readback to unpremul 8-bit RGBA and PNG encoding",
            "inspectedPoints": [
                {
                    "file": rel(SK_BITMAP),
                    "symbols": ["SkBitmap.getPixel"],
                    "staticEvidence": [
                        "kRGBA_F16Norm getPixel converts premul float to non-premul 8-bit ARGB",
                        "conversion uses floor-style truncation via (channel * 256f).toInt()",
                    ],
                },
                {
                    "file": rel(SK_PNG_ENCODER),
                    "symbols": ["SkPngEncoder.rgbaRow"],
                    "staticEvidence": [
                        "PNG rows are produced from src.getPixel(x, y)",
                        "PNG writes 8-bit RGBA samples",
                        "the encoder does not embed the bitmap color-space tag as iCCP",
                    ],
                },
            ],
            "proved": [
                "PNG evidence samples are downstream of an F16 premul to 8-bit unpremul readback boundary",
                "the color-space tag is not preserved in the checked PNG output",
            ],
            "notProved": [
                "whether the residual first appears at readback/encode rather than earlier color conversion or store",
            ],
            "runtimeValuesCaptured": False,
            "boundaryIdentified": False,
        },
    ]


def build_artifact() -> dict[str, Any]:
    for331, validated, invalid_reasons = validate_prerequisites()
    samples = [] if for331 is None or invalid_reasons else build_samples(for331)
    audit = build_boundary_audit()
    proved_boundaries = [entry["boundaryId"] for entry in audit if entry["boundaryIdentified"]]

    if invalid_reasons:
        decision = DECISION_INPUT_INVALID
        conclusion = "input-invalid"
        correction_targetable = False
        instrumentation_required = False
        rationale = "Prerequisite artifacts or inspected source points changed; FOR-332 cannot classify the CPU boundary."
        next_action = "Repair FOR-331/FOR-330/FOR-329/FOR-327/FOR-322 prerequisites before tracing the CPU color pipeline."
    elif proved_boundaries:
        decision = DECISION_BOUNDARY_IDENTIFIED
        conclusion = "correction-targetable"
        correction_targetable = True
        instrumentation_required = False
        rationale = "A single CPU color pipeline boundary is proven by runtime evidence."
        next_action = "Prepare a renderer correction scoped to the identified boundary."
    else:
        decision = DECISION_REQUIRES_KOTLIN_INSTRUMENTATION
        conclusion = "kotlin-instrumentation-required"
        correction_targetable = False
        instrumentation_required = True
        rationale = (
            "FOR-331 final-pixel evidence and static source inspection keep the residual in the "
            "color-space/premul/F16-readback/PNG cluster, but no inspected boundary captures runtime "
            "pre/post values for the selected samples. A renderer correction is not targetable yet."
        )
        next_action = (
            "Add targeted Kotlin instrumentation around transformPaintColor/colorToF16Premul, coverage, "
            "blendF16PremulMode store, SkBitmap.getPixel F16 readback, and SkPngEncoder.rgbaRow for the "
            "selected FOR-331 samples."
        )

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "decision": decision,
        "allowedDecisions": [
            DECISION_BOUNDARY_IDENTIFIED,
            DECISION_REQUIRES_KOTLIN_INSTRUMENTATION,
            DECISION_INPUT_INVALID,
        ],
        "sceneId": SCENE_ID,
        "traceType": "static-cpu-color-pipeline-boundary-audit",
        "selectedCell": EXPECTED_SELECTED_CELL,
        "follows": validated["follows"],
        "inputs": validated["inputs"],
        "inputValidation": {
            "valid": not invalid_reasons,
            "requiredFor331Decision": FOR331_DECISION_REQUIRED,
            "expectedFor331Metrics": EXPECTED_FOR331_METRICS,
            "expectedPrimaryHypothesis": EXPECTED_HYPOTHESIS,
            "invalidReasons": invalid_reasons,
        },
        "for331ResidualEvidence": None if for331 is None or invalid_reasons else {
            "metrics": for331["trace"]["metrics"],
            "hypotheses": for331["assessment"]["hypotheses"],
            "strokeResidualLocalization": for331["trace"]["strokeResidualLocalization"],
        },
        "samples": samples,
        "cpuPipelineBoundaryAudit": audit,
        "boundaryAssessment": {
            "conclusion": conclusion,
            "correctionTargetable": correction_targetable,
            "kotlinInstrumentationRequired": instrumentation_required,
            "provedBoundaries": proved_boundaries,
            "candidateBoundaryCluster": [
                "paint-color-xform-and-premul",
                "stroke-coverage",
                "src-over-f16-store",
                "f16-readback-and-png-encode",
            ],
            "rationale": rationale,
            "recommendedNextAction": next_action,
        },
        "preservedContracts": {
            "auditScope": "selected-cell-only",
            "diagnosticOnly": True,
            "productionRendererChanged": False,
            "cpuRendererFixed": False,
            "gpuRendered": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
            "scenePromotionChanged": False,
            "fidelityScoreCounted": False,
            "fullGmScoreAccepted": False,
            "fullGmCropAccepted": False,
            "fullGmSubstitutionAccepted": False,
        },
        "validation": [
            "rtk python3 scripts/validate_for332_circular_arcs_stroke_butt_selected_cell_cpu_color_pipeline_trace.py",
            "rtk python3 scripts/validate_for331_circular_arcs_stroke_butt_selected_cell_normalized_stroke_trace.py",
            "rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py",
            "rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py",
            "rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py",
            "rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py",
            "rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py",
            f"rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null",
            "rtk ./gradlew pipelineSceneDashboardGate",
            "rtk git diff --check origin/master...HEAD",
        ],
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def sample_table(samples: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{name}` | `{zone}` | `{xy}` | `{skia}` | `{white}` | `{cpu}` | `{naive}` | `{delta}` | `{classification}` |".format(
            name=sample["name"],
            zone=sample["zone"],
            xy=f"{sample['x']},{sample['y']}",
            skia=sample["skiaRgba"],
            white=sample["skiaOverWhiteRgba"],
            cpu=sample["cpuRgba"],
            naive=sample["naiveAlphaOverWhiteRgba"],
            delta=sample["cpuVsSkiaOverWhiteAbsDelta"],
            classification=sample["probableBoundaryClassification"],
        )
        for sample in samples
    )


def boundary_table(audit: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{boundary}` | {files} | {proved} | {not_proved} | `{captured}` |".format(
            boundary=item["boundaryId"],
            files=", ".join(f"`{point['file']}`" for point in item["inspectedPoints"]),
            proved="; ".join(item["proved"]),
            not_proved="; ".join(item["notProved"]),
            captured=item["runtimeValuesCaptured"],
        )
        for item in audit
    )


def write_report(data: dict[str, Any]) -> None:
    validation_lines = "\n".join(f"- `{command}`" for command in data["validation"])
    invalid_lines = "\n".join(f"- {reason}" for reason in data["inputValidation"]["invalidReasons"]) or "- none"
    inputs = data["inputs"]
    contracts = data["preservedContracts"]
    assessment = data["boundaryAssessment"]
    residual = data.get("for331ResidualEvidence")

    if residual is None:
        metrics_section = "Input validation failed; no CPU color boundary trace was produced."
        sample_section = ""
    else:
        metrics = residual["metrics"]
        metrics_section = f"""| Field | Value |
|---|---|
| total pixels | `{metrics["totalPixels"]}` |
| different pixels | `{metrics["differentPixels"]}` |
| matching pixels | `{metrics["matchingPixels"]}` |
| cell similarity percent | `{metrics["cellSimilarityPercent"]}` |
| max delta by channel | `{md_value(metrics["maxDeltaByChannel"])}` |
| sum abs delta by channel | `{md_value(metrics["sumAbsDeltaByChannel"])}` |
| sum abs delta total | `{metrics["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(metrics["differentPixelBoundingBox"])}` |
| different pixels outside expected stroke bbox | `{metrics["differentPixelsOutsideExpectedStrokeBoundingBox"]}` |
| primary FOR-331 hypothesis | `{EXPECTED_HYPOTHESIS["id"]}` |
| primary FOR-331 hypothesis weight | `{EXPECTED_HYPOTHESIS["weight"]}` |"""
        sample_section = f"""## Samples

| Sample | Zone | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | Naive alpha-over-white RGBA | CPU vs Skia abs delta | Probable boundary classification |
|---|---|---|---|---|---|---|---|---|
{sample_table(data["samples"])}
"""

    report = f"""# FOR-332 CircularArcsStrokeButt Selected-Cell CPU Color Pipeline Trace

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Source finding:
`{SOURCE_FINDING}`

Decision: `{data["decision"]}`

## Result

FOR-332 adds a selected-cell CPU color pipeline boundary trace for the FOR-331
residual. It does not fix the CPU renderer and does not change GPU, WGSL,
threshold, fallback, Kadre, scene-promotion, or fidelity-score behavior.

Conclusion: `{assessment["conclusion"]}`.

Correction targetable: `{assessment["correctionTargetable"]}`.

Kotlin instrumentation required: `{assessment["kotlinInstrumentationRequired"]}`.

Interpretation: {assessment["rationale"]}

Recommended next action: {assessment["recommendedNextAction"]}

## Inputs

| Input | Path | Source | Required decision | SHA-256 |
|---|---|---|---|---|
| FOR-331 trace | `{inputs["for331Artifact"]["path"]}` | `{inputs["for331Artifact"]["sourceLinear"]}` / `{inputs["for331Artifact"]["sourceSceneId"]}` | `{inputs["for331Artifact"]["requiredDecision"]}` | `{inputs["for331Artifact"]["sha256"]}` |
| FOR-330 white diff | `{inputs["for330Artifact"]["path"]}` | `{inputs["for330Artifact"]["sourceLinear"]}` / `{inputs["for330Artifact"]["sourceSceneId"]}` | `{inputs["for330Artifact"]["requiredDecision"]}` | `{inputs["for330Artifact"]["sha256"]}` |
| FOR-329 CPU audit | `{inputs["for329Artifact"]["path"]}` | `{inputs["for329Artifact"]["sourceLinear"]}` / `{inputs["for329Artifact"]["sourceSceneId"]}` | `{inputs["for329Artifact"]["requiredDecision"]}` | `{inputs["for329Artifact"]["sha256"]}` |
| FOR-327 Skia reference | `{inputs["for327Artifact"]["path"]}` | `{inputs["for327Artifact"]["sourceLinear"]}` / `{inputs["for327Artifact"]["sourceSceneId"]}` | `{inputs["for327Artifact"]["requiredDecision"]}` | `{inputs["for327Artifact"]["sha256"]}` |
| FOR-322 harness | `{inputs["for322Artifact"]["path"]}` | `{inputs["for322Artifact"]["sourceLinear"]}` / `{inputs["for322Artifact"]["sourceSceneId"]}` | `{inputs["for322Artifact"]["requiredDecision"]}` | `{inputs["for322Artifact"]["sha256"]}` |

Input validation valid: `{data["inputValidation"]["valid"]}`

Invalid reasons:

{invalid_lines}

## FOR-331 Metrics Gate

{metrics_section}

{sample_section}
## CPU Pipeline Boundary Audit

| Boundary | Inspected files | Proved | Not proved | Runtime values captured |
|---|---|---|---|---|
{boundary_table(data["cpuPipelineBoundaryAudit"])}

## Decision

The static audit narrows the residual to the color-space / premul / coverage /
F16 store / PNG-readback cluster, but it does not prove a single boundary. The
stable decision is therefore
`{DECISION_REQUIRES_KOTLIN_INSTRUMENTATION}` unless future runtime values prove
one boundary and switch the artifact to `{DECISION_BOUNDARY_IDENTIFIED}`.

## Non-Goals And Preserved Contracts

| Field | Value |
|---|---|
| audit scope | `{contracts["auditScope"]}` |
| diagnostic only | `{contracts["diagnosticOnly"]}` |
| production renderer changed | `{contracts["productionRendererChanged"]}` |
| CPU renderer fixed | `{contracts["cpuRendererFixed"]}` |
| GPU rendered | `{contracts["gpuRendered"]}` |
| WGSL changed | `{contracts["wgslChanged"]}` |
| threshold changed | `{contracts["thresholdChanged"]}` |
| fallback policy changed | `{contracts["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{contracts["kadreNativeDependencyAdded"]}` |
| scene promotion changed | `{contracts["scenePromotionChanged"]}` |
| fidelity score counted | `{contracts["fidelityScoreCounted"]}` |
| full-GM score accepted | `{contracts["fullGmScoreAccepted"]}` |
| full-GM crop accepted | `{contracts["fullGmCropAccepted"]}` |
| full-GM substitution accepted | `{contracts["fullGmSubstitutionAccepted"]}` |

## Validation

{validation_lines}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_sample_shape(samples: Any) -> None:
    if not isinstance(samples, list) or len(samples) != len(EXPECTED_SAMPLE_NAMES):
        fail("samples must contain the FOR-331 sample table")
    by_name = {sample.get("name"): sample for sample in samples if isinstance(sample, dict)}
    for name in EXPECTED_SAMPLE_NAMES:
        sample = by_name.get(name)
        if sample is None:
            fail(f"sample missing: {name}")
        for key in [
            "zone",
            "skiaRgba",
            "skiaOverWhiteRgba",
            "cpuRgba",
            "naiveAlphaOverWhiteRgba",
            "cpuVsSkiaOverWhiteAbsDelta",
            "deltaCpuVsSkiaOverWhiteRgba",
            "deltaCpuVsSkiaRgba",
            "probableBoundaryClassification",
        ]:
            if key not in sample:
                fail(f"sample {name} missing {key}")
    for name in ["blue_top_stroke_center", "red_right_stroke_center", "red_bottom_stroke_center"]:
        sample = by_name[name]
        if sample.get("skiaOverWhiteVsNaiveAbsDelta") != [0, 0, 0, 0]:
            fail(f"{name} must keep Skia-over-white equal to naive alpha-over-white")
        if sample.get("cpuVsSkiaOverWhiteAbsDelta") == [0, 0, 0, 0]:
            fail(f"{name} must preserve the FOR-331 CPU residual")
        if sample.get("probableBoundaryClassification") != (
            "colorspace-premul-or-png-encode-boundary-candidate-runtime-unresolved"
        ):
            fail(f"{name} classification must identify unresolved colorspace/premul/PNG candidate")


def validate_boundary_audit_shape(audit: Any) -> None:
    if not isinstance(audit, list) or len(audit) < 5:
        fail("cpuPipelineBoundaryAudit must list inspected CPU pipeline boundaries")
    required_ids = {
        "selected-cell-input-and-paint",
        "testutils-raster-sink-f16",
        "paint-color-xform-and-premul",
        "stroke-coverage",
        "src-over-f16-store",
        "f16-readback-and-png-encode",
    }
    seen = {item.get("boundaryId") for item in audit if isinstance(item, dict)}
    missing = required_ids - seen
    if missing:
        fail(f"cpuPipelineBoundaryAudit missing {sorted(missing)}")
    for item in audit:
        if not isinstance(item, dict):
            fail("cpuPipelineBoundaryAudit entries must be objects")
        if not isinstance(item.get("inspectedPoints"), list) or not item["inspectedPoints"]:
            fail(f"{item.get('boundaryId')} inspectedPoints missing")
        if not isinstance(item.get("proved"), list) or not item["proved"]:
            fail(f"{item.get('boundaryId')} proved list missing")
        if not isinstance(item.get("notProved"), list) or not item["notProved"]:
            fail(f"{item.get('boundaryId')} notProved list missing")
        if item.get("runtimeValuesCaptured") is not False:
            fail(f"{item.get('boundaryId')} must document static-only runtime capture")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("sourceFinding") != SOURCE_FINDING:
        fail("artifact source finding changed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    if data.get("decision") not in data.get("allowedDecisions", []):
        fail("artifact decision is not allowed")
    if data.get("selectedCell") != EXPECTED_SELECTED_CELL:
        fail("artifact selectedCell changed")

    input_validation = data.get("inputValidation")
    if not isinstance(input_validation, dict):
        fail("artifact inputValidation missing")
    if input_validation.get("requiredFor331Decision") != FOR331_DECISION_REQUIRED:
        fail("artifact must gate on FOR-331 colorspace/premul decision")
    if input_validation.get("expectedFor331Metrics") != EXPECTED_FOR331_METRICS:
        fail("artifact expected FOR-331 metrics changed")
    if input_validation.get("expectedPrimaryHypothesis") != EXPECTED_HYPOTHESIS:
        fail("artifact expected primary hypothesis changed")

    assessment = data.get("boundaryAssessment")
    if not isinstance(assessment, dict):
        fail("artifact boundaryAssessment missing")
    if data.get("decision") == DECISION_INPUT_INVALID:
        if input_validation.get("valid") is not False:
            fail("input-invalid decision requires invalid inputValidation")
        return

    if input_validation.get("valid") is not True:
        fail("non-input-invalid decision requires valid inputValidation")
    residual = data.get("for331ResidualEvidence")
    if not isinstance(residual, dict):
        fail("for331ResidualEvidence missing")
    metrics = residual.get("metrics")
    if not isinstance(metrics, dict):
        fail("for331ResidualEvidence.metrics missing")
    for key, expected in EXPECTED_FOR331_METRICS.items():
        if metrics.get(key) != expected:
            fail(f"FOR-331 residual metric {key} expected {expected!r}, got {metrics.get(key)!r}")

    validate_sample_shape(data.get("samples"))
    validate_boundary_audit_shape(data.get("cpuPipelineBoundaryAudit"))

    if data.get("decision") == DECISION_REQUIRES_KOTLIN_INSTRUMENTATION:
        if assessment.get("kotlinInstrumentationRequired") is not True:
            fail("requires-instrumentation decision must require Kotlin instrumentation")
        if assessment.get("correctionTargetable") is not False:
            fail("static-only trace must not be correction targetable")
        if assessment.get("provedBoundaries") != []:
            fail("requires-instrumentation decision must not list proved boundaries")

    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact preservedContracts missing")
    for key in [
        "productionRendererChanged",
        "cpuRendererFixed",
        "gpuRendered",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
        "scenePromotionChanged",
        "fidelityScoreCounted",
        "fullGmScoreAccepted",
        "fullGmCropAccepted",
        "fullGmSubstitutionAccepted",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    text = read_text(REPORT)
    for required in [
        "# FOR-332 CircularArcsStrokeButt Selected-Cell CPU Color Pipeline Trace",
        f"Decision: `{data['decision']}`",
        "Correction targetable: `False`",
        "Kotlin instrumentation required: `True`",
        "## CPU Pipeline Boundary Audit",
        "## Non-Goals And Preserved Contracts",
    ]:
        if required not in text:
            fail(f"report missing required text: {required}")


def main() -> None:
    data = build_artifact()
    if os.environ.get("KANVAS_FOR332_REWRITE") == "true":
        write_artifact(data)
        write_report(data)
    reloaded = load_json(ARTIFACT)
    if reloaded.get("decision") == DECISION_INPUT_INVALID:
        invalid_reasons = reloaded.get("inputValidation", {}).get("invalidReasons", [])
        fail(f"input prerequisites invalid: {invalid_reasons}")
    validate_artifact_shape(reloaded)
    validate_report(reloaded)
    print(reloaded["decision"])


if __name__ == "__main__":
    main()
