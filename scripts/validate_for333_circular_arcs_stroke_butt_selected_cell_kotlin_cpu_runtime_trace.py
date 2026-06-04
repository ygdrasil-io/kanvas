#!/usr/bin/env python3
"""Validate the FOR-333 selected-cell Kotlin CPU runtime trace."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-333"
SCENE_ID = "circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace-for333"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
    / f"{SCENE_ID}.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-333-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-trace.md"
)

FOR332_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-for332"
FOR332_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR332_SCENE_ID
    / f"{FOR332_SCENE_ID}.json"
)
FOR332_REQUIRED_DECISION = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_COLOR_PIPELINE_TRACE_REQUIRES_KOTLIN_INSTRUMENTATION"
)

TRACE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    / "CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest.kt"
)
GPU_BUILD = PROJECT_ROOT / "gpu-raster/build.gradle.kts"
SK_BITMAP_DEVICE = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkBitmapDevice.kt"

SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-kotlin-cpu-runtime-instrumentation-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-332-circular-arcs-stroke-butt-selected-cell-cpu-color-pipeline-trace-requires-kotlin-instrumentation-finding"
)

DECISION_BOUNDARY_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_BOUNDARY_IDENTIFIED"
)
DECISION_PARTIAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_PARTIAL_REQUIRES_MORE_INSTRUMENTATION"
)
DECISION_INPUT_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_KOTLIN_CPU_RUNTIME_TRACE_INPUT_INVALID"
)
ALLOWED_DECISIONS = {DECISION_BOUNDARY_IDENTIFIED, DECISION_PARTIAL, DECISION_INPUT_INVALID}

COMMAND = (
    "rtk ./gradlew --no-daemon -Dkanvas.for333.runtimeTrace.write=true "
    ":gpu-raster:test --tests "
    "org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellKotlinCpuRuntimeTraceTest"
)

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

EXPECTED_SAMPLES = [
    ("top_left_background", "background", None, 0, 0, [255, 255, 255, 255], [255, 255, 255, 255]),
    ("top_edge_background", "background", None, 40, 0, [255, 255, 255, 255], [255, 255, 255, 255]),
    ("left_edge_background", "background", None, 0, 40, [255, 255, 255, 255], [255, 255, 255, 255]),
    ("blue_left_aa_edge", "stroke-aa-edge", "blue", 12, 40, [210, 210, 255, 255], [214, 208, 253, 255]),
    ("blue_top_outer_edge", "stroke-aa-edge", "blue", 40, 12, [209, 209, 255, 255], [214, 208, 253, 255]),
    ("arc_rect_top_left", "stroke-aa-edge", "blue", 20, 20, [215, 215, 255, 255], [224, 220, 253, 255]),
    ("blue_top_stroke_center", "stroke-center", "blue", 40, 20, [155, 155, 255, 255], [172, 160, 250, 255]),
    ("red_right_stroke_center", "stroke-center", "red", 60, 40, [255, 155, 155, 255], [235, 178, 162, 255]),
    ("red_bottom_stroke_center", "stroke-center", "red", 40, 60, [255, 155, 155, 255], [235, 178, 162, 255]),
    ("red_outer_edge", "stroke-aa-edge", "red", 67, 40, [255, 210, 210, 255], [248, 227, 221, 255]),
    ("red_bottom_outer_edge", "stroke-aa-edge", "red", 40, 67, [255, 209, 209, 255], [248, 227, 221, 255]),
    ("cell_center_hole", "center-hole", None, 40, 40, [255, 255, 255, 255], [255, 255, 255, 255]),
    ("bottom_right_background", "background", None, 79, 79, [255, 255, 255, 255], [255, 255, 255, 255]),
]
STROKE_SAMPLE_NAMES = {name for name, zone, *_ in EXPECTED_SAMPLES if zone.startswith("stroke")}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-333 validation failed: {message}")


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


def validate_for332_gate() -> None:
    for332 = load_json(FOR332_ARTIFACT)
    require(for332.get("linear") == "FOR-332", "FOR-332 artifact identity changed")
    require(
        for332.get("decision") == FOR332_REQUIRED_DECISION,
        f"FOR-332 decision must remain {FOR332_REQUIRED_DECISION}",
    )


def validate_sources() -> None:
    required = {
        TRACE_TEST: [
            "kanvas.for333.runtimeTrace.write",
            "SkCpuWriteChronologyTrace.configureForTargets",
            "includeBitmapDirectWrites = true",
            "SkPngEncoder.Encode(bitmap)",
            "SelectedCellGM",
            COMMAND,
        ],
        GPU_BUILD: [
            'System.getProperty("kanvas.for333.runtimeTrace.write")',
            'systemProperty("kanvas.for333.runtimeTrace.write", it)',
        ],
        SK_BITMAP_DEVICE: [
            "recordF16PremulStore",
            "srcPremulBeforeCoverageF16",
            "dstPremulAfterStoreF16",
            "SkCpuWriteChronologyTrace.shouldTrace(x, y, width, height)",
        ],
    }
    for path, snippets in required.items():
        if not path.is_file():
            fail(f"missing source file: {rel(path)}")
        text = path.read_text(encoding="utf-8")
        for snippet in snippets:
            require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")


def validate_selected_cell(data: dict[str, Any]) -> None:
    cell = data.get("selectedCell")
    require(isinstance(cell, dict), "selectedCell missing")
    for key, expected in EXPECTED_SELECTED_CELL.items():
        require(cell.get(key) == expected, f"selectedCell.{key} expected {expected!r}, got {cell.get(key)!r}")


def validate_sample(sample: dict[str, Any], expected: tuple[Any, ...]) -> None:
    name, zone, paint, x, y, skia_over_white, for331_cpu = expected
    require(sample.get("name") == name, f"sample order/name expected {name}")
    require(sample.get("zone") == zone, f"{name}.zone changed")
    require(sample.get("paintColor") == paint, f"{name}.paintColor changed")
    require(sample.get("x") == x and sample.get("y") == y, f"{name}.xy changed")
    require(sample.get("expectedSkiaOverWhiteRgba") == skia_over_white, f"{name}.expectedSkiaOverWhiteRgba changed")
    require(sample.get("for331CpuRgba") == for331_cpu, f"{name}.for331CpuRgba changed")
    require(sample.get("runtimeValuesCaptured") is True, f"{name} runtime values not captured")

    readback = sample.get("f16Readback")
    png = sample.get("pngEncode")
    comparison = sample.get("boundaryComparison")
    require(isinstance(readback, dict), f"{name}.f16Readback missing")
    require(isinstance(png, dict), f"{name}.pngEncode missing")
    require(isinstance(comparison, dict), f"{name}.boundaryComparison missing")
    require(readback.get("captured") is True, f"{name}.f16Readback not captured")
    require(png.get("captured") is True, f"{name}.pngEncode not captured")
    require(readback.get("skBitmapGetPixelRgba") == for331_cpu, f"{name}.getPixel does not match FOR-331 CPU RGBA")
    require(png.get("rgbaRowBytes") == for331_cpu, f"{name}.PNG rgba row equivalent does not match readback")
    require(comparison.get("cpuReadbackMatchesFor331CpuRgba") is True, f"{name}.readback match flag false")
    require(comparison.get("pngRgbaMatchesReadback") is True, f"{name}.png/readback match flag false")

    if name in STROKE_SAMPLE_NAMES:
        for key in ("paintColorXformAndPremul", "strokeCoverage", "srcOverF16Store"):
            block = sample.get(key)
            require(isinstance(block, dict), f"{name}.{key} missing")
            require(block.get("captured") is True, f"{name}.{key} not captured")
        require(sample["srcOverF16Store"].get("srcPremulAfterCoverageF16"), f"{name} missing srcPremulAfterCoverageF16")
        require(sample["srcOverF16Store"].get("dstPremulBeforeStoreF16"), f"{name} missing dstPremulBeforeStoreF16")
        require(sample["srcOverF16Store"].get("dstPremulAfterStoreF16"), f"{name} missing dstPremulAfterStoreF16")
        require(sample["strokeCoverage"].get("coverageSamples") is not None, f"{name} missing coverageSamples")


def validate_trace(data: dict[str, Any]) -> None:
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "sceneId changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "sourceMemory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "sourceFinding changed")
    decision = data.get("decision")
    require(decision in ALLOWED_DECISIONS, f"unexpected decision: {decision!r}")
    require(decision == DECISION_BOUNDARY_IDENTIFIED, "FOR-333 expected boundary-identified decision for current evidence")
    require(data.get("identifiedFirstDivergentBoundary") == "f16-readback-and-png-encode", "identified boundary changed")
    require(data.get("correctionTargetable") is True, "correctionTargetable must be true")
    require(data.get("selectedCellOnly") is True, "trace must be selected-cell-only")

    input_validation = data.get("inputValidation")
    require(isinstance(input_validation, dict), "inputValidation missing")
    require(input_validation.get("valid") is True, "inputValidation must be valid")
    require(input_validation.get("requiresFor332Decision") == FOR332_REQUIRED_DECISION, "FOR-332 gate not encoded")

    opt_in = data.get("optIn")
    require(isinstance(opt_in, dict), "optIn missing")
    require(opt_in.get("defaultActive") is False, "trace must be inactive by default")
    require(opt_in.get("enabledBySystemProperty") == "kanvas.for333.runtimeTrace.write", "opt-in property changed")
    require(opt_in.get("command") == COMMAND, "generation command changed")

    non_goals = data.get("nonGoals")
    require(isinstance(non_goals, dict), "nonGoals missing")
    for key in (
        "cpuRendererFixed",
        "gpuChanged",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreChanged",
        "scenePromotionChanged",
        "fidelityScoreCounted",
    ):
        require(non_goals.get(key) is False, f"nonGoal {key} must remain false")

    require(data.get("for331MetricsPreserved") == {
        "differentPixels": 2031,
        "cellSimilarityPercent": 68.265625,
        "differentPixelsOutsideExpectedStrokeBoundingBox": 0,
    }, "FOR-331 metrics preservation block changed")
    require(data.get("for332MetricsPreserved", {}).get("decision") == FOR332_REQUIRED_DECISION, "FOR-332 decision not preserved")

    summary = data.get("runtimeCaptureSummary")
    require(isinstance(summary, dict), "runtimeCaptureSummary missing")
    require(summary.get("targetSampleCount") == len(EXPECTED_SAMPLES), "target sample count changed")
    require(summary.get("f16StoreEventCount") == len(STROKE_SAMPLE_NAMES), "F16 store event count must match stroke samples")
    require(summary.get("boundariesAbsent") == [], "no inaccessible boundaries expected for current decision")

    validate_selected_cell(data)

    samples = data.get("samples")
    require(isinstance(samples, list), "samples missing")
    require(len(samples) == len(EXPECTED_SAMPLES), "sample count changed")
    for sample, expected in zip(samples, EXPECTED_SAMPLES):
        require(isinstance(sample, dict), "sample entries must be objects")
        validate_sample(sample, expected)

    events = data.get("traceEvents")
    require(isinstance(events, list), "traceEvents missing")
    require(len(events) == summary.get("traceEventCount"), "traceEvents count does not match summary")
    require(any(event.get("source") == "SkBitmap.eraseColor" for event in events), "eraseColor trace missing")
    require(
        sum(1 for event in events if event.get("srcPremulAfterCoverageF16") is not None) == len(STROKE_SAMPLE_NAMES),
        "traceEvents F16 store count changed",
    )


def validate_report() -> None:
    if not REPORT.is_file():
        fail(f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for snippet in [
        "# FOR-333 CircularArcsStrokeButt Selected-Cell Kotlin CPU Runtime Trace",
        DECISION_BOUNDARY_IDENTIFIED,
        "f16-readback-and-png-encode",
        COMMAND,
        "FOR-331/FOR-332 metrics unchanged",
        "CPU renderer fixed: `False`",
    ]:
        require(snippet in text, f"report missing snippet: {snippet}")


def main() -> None:
    validate_for332_gate()
    validate_sources()
    data = load_json(ARTIFACT)
    validate_trace(data)
    validate_report()
    print("FOR-333 validation passed")


if __name__ == "__main__":
    main()
