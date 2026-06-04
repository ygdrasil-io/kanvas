#!/usr/bin/env python3
"""Trace the FOR-330 normalized selected-cell stroke/color residual."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from PIL import Image


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-331"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-normalized-stroke-color-cpu-trace-ticket"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-330-circular-arcs-stroke-butt-selected-cell-white-background-diff-residual-present-finding"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace-for331"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-331-circular-arcs-stroke-butt-selected-cell-normalized-stroke-trace.md"
)

FOR330_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-white-background-diff-for330"
FOR330_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR330_SCENE_ID
FOR330_ARTIFACT = FOR330_DIR / f"{FOR330_SCENE_ID}.json"
SKIA_OVER_WHITE = FOR330_DIR / "skia-over-white.png"
FOR330_DIFF = FOR330_DIR / "cpu-vs-skia-over-white-diff.png"

FOR329_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329"
FOR329_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR329_SCENE_ID
FOR329_ARTIFACT = FOR329_DIR / f"{FOR329_SCENE_ID}.json"

FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR327_SCENE_ID
FOR327_ARTIFACT = FOR327_DIR / f"{FOR327_SCENE_ID}.json"
SKIA = FOR327_DIR / "skia.png"

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
CPU = FOR322_DIR / "cpu.png"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR322_ROUTE_CPU = FOR322_DIR / "route-cpu.json"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
EXPECTED_SIZE = (80, 80)
EXPECTED_STROKE_BBOX = {"left": 12, "top": 12, "right": 67, "bottom": 67}
EXPECTED_FOR330_NORMALIZED = {
    "totalPixels": 6400,
    "differentPixels": 2031,
    "matchingPixels": 4369,
    "cellSimilarityPercent": 68.265625,
    "maxDeltaByChannel": {"r": 39, "g": 43, "b": 31, "a": 0},
    "sumAbsDeltaByChannel": {"r": 33893, "g": 18839, "b": 10795, "a": 0},
    "sumAbsDeltaTotal": 63527,
    "differentPixelBoundingBox": EXPECTED_STROKE_BBOX,
}

DECISION_COLORSPACE_PREMUL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COLORSPACE_PREMUL_SUSPECTED"
)
DECISION_COVERAGE = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_COVERAGE_SUSPECTED"
)
DECISION_REQUIRES_TRACE = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_REQUIRES_INSTRUMENTED_CPU_TRACE"
)
DECISION_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_NORMALIZED_STROKE_TRACE_INPUT_INVALID"
)
FOR330_DECISION_RESIDUAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT"
)
FOR329_DECISION_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED"
)
FOR327_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"

SELECTED_CELL_EXPECTED = {
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

SAMPLES = [
    ("top_left_background", "background", None, 0, 0),
    ("top_edge_background", "background", None, 40, 0),
    ("left_edge_background", "background", None, 0, 40),
    ("blue_left_aa_edge", "stroke-aa-edge", "blue", 12, 40),
    ("blue_top_outer_edge", "stroke-aa-edge", "blue", 40, 12),
    ("arc_rect_top_left", "stroke-aa-edge", "blue", 20, 20),
    ("blue_top_stroke_center", "stroke-center", "blue", 40, 20),
    ("red_right_stroke_center", "stroke-center", "red", 60, 40),
    ("red_bottom_stroke_center", "stroke-center", "red", 40, 60),
    ("red_outer_edge", "stroke-aa-edge", "red", 67, 40),
    ("red_bottom_outer_edge", "stroke-aa-edge", "red", 40, 67),
    ("cell_center_hole", "center-hole", None, 40, 40),
    ("bottom_right_background", "background", None, 79, 79),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-331 validation failed: {message}")


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


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


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def rgba_image(path: Path) -> Image.Image:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        return image.convert("RGBA")


def image_dimensions(path: Path, invalid: list[str], label: str) -> dict[str, int] | None:
    if not path.is_file():
        invalid.append(f"missing PNG file: {rel(path)}")
        return None
    try:
        with Image.open(path) as image:
            dimensions = {"width": image.size[0], "height": image.size[1]}
    except OSError as error:
        invalid.append(f"{label} is not a readable PNG: {error}")
        return None
    if dimensions != EXPECTED_DIMENSIONS:
        invalid.append(f"{label} dimensions are {dimensions}, expected {EXPECTED_DIMENSIONS}")
    return dimensions


def validate_cell(cell: dict[str, Any] | None, label: str, invalid: list[str]) -> None:
    if not isinstance(cell, dict):
        invalid.append(f"{label} missing")
        return
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            invalid.append(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def assert_cell(cell: dict[str, Any] | None, label: str) -> None:
    invalid: list[str] = []
    validate_cell(cell, label, invalid)
    if invalid:
        fail("; ".join(invalid))


def nested_get(data: dict[str, Any], path: list[str]) -> Any:
    current: Any = data
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def compare_images(reference: Image.Image, actual: Image.Image) -> dict[str, Any]:
    if reference.size != actual.size:
        fail("image size mismatch")
    width, height = reference.size
    ref_pixels = reference.load()
    actual_pixels = actual.load()
    different = 0
    max_delta = [0, 0, 0, 0]
    sum_abs_delta = [0, 0, 0, 0]
    min_x = width
    min_y = height
    max_x = -1
    max_y = -1
    outside_stroke_bbox_different = 0

    for y in range(height):
        for x in range(width):
            delta = tuple(abs(int(actual_pixels[x, y][index]) - int(ref_pixels[x, y][index])) for index in range(4))
            if delta == (0, 0, 0, 0):
                continue
            different += 1
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
            if not (
                EXPECTED_STROKE_BBOX["left"] <= x <= EXPECTED_STROKE_BBOX["right"]
                and EXPECTED_STROKE_BBOX["top"] <= y <= EXPECTED_STROKE_BBOX["bottom"]
            ):
                outside_stroke_bbox_different += 1
            for index, value in enumerate(delta):
                max_delta[index] = max(max_delta[index], value)
                sum_abs_delta[index] += value

    total = width * height
    return {
        "totalPixels": total,
        "differentPixels": different,
        "matchingPixels": total - different,
        "cellSimilarityPercent": round(((total - different) / total) * 100.0, 6),
        "maxDeltaByChannel": {"r": max_delta[0], "g": max_delta[1], "b": max_delta[2], "a": max_delta[3]},
        "sumAbsDeltaByChannel": {"r": sum_abs_delta[0], "g": sum_abs_delta[1], "b": sum_abs_delta[2], "a": sum_abs_delta[3]},
        "sumAbsDeltaTotal": sum(sum_abs_delta),
        "differentPixelBoundingBox": None if different == 0 else {"left": min_x, "top": min_y, "right": max_x, "bottom": max_y},
        "differentPixelsOutsideExpectedStrokeBoundingBox": outside_stroke_bbox_different,
    }


def alpha_over_white_naive(color: str, alpha: int) -> list[int]:
    alpha_fraction = alpha / 255.0
    if color == "red":
        src = [255, 0, 0]
    elif color == "blue":
        src = [0, 0, 255]
    else:
        raise ValueError(f"unknown color {color}")
    return [round(src[index] * alpha_fraction + 255 * (1.0 - alpha_fraction)) for index in range(3)] + [255]


def abs_delta(left: list[int] | tuple[int, ...], right: list[int] | tuple[int, ...]) -> list[int]:
    return [abs(int(left[index]) - int(right[index])) for index in range(4)]


def signed_delta(left: list[int] | tuple[int, ...], right: list[int] | tuple[int, ...]) -> list[int]:
    return [int(left[index]) - int(right[index]) for index in range(4)]


def sum_rgb(delta: list[int]) -> int:
    return sum(delta[:3])


def sample_trace(skia: Image.Image, skia_over_white: Image.Image, cpu: Image.Image) -> list[dict[str, Any]]:
    skia_pixels = skia.load()
    skia_white_pixels = skia_over_white.load()
    cpu_pixels = cpu.load()
    samples: list[dict[str, Any]] = []
    for name, zone, paint_color, x, y in SAMPLES:
        skia_rgba = list(tuple(skia_pixels[x, y]))
        skia_white_rgba = list(tuple(skia_white_pixels[x, y]))
        cpu_rgba = list(tuple(cpu_pixels[x, y]))
        naive = alpha_over_white_naive(paint_color, skia_rgba[3]) if paint_color else None
        samples.append(
            {
                "name": name,
                "zone": zone,
                "paintColor": paint_color,
                "x": x,
                "y": y,
                "withinExpectedStrokeBoundingBox": (
                    EXPECTED_STROKE_BBOX["left"] <= x <= EXPECTED_STROKE_BBOX["right"]
                    and EXPECTED_STROKE_BBOX["top"] <= y <= EXPECTED_STROKE_BBOX["bottom"]
                ),
                "skiaRgba": skia_rgba,
                "skiaOverWhiteRgba": skia_white_rgba,
                "cpuRgba": cpu_rgba,
                "naiveAlphaOverWhiteRgba": naive,
                "cpuVsSkiaOverWhiteAbsDelta": abs_delta(cpu_rgba, skia_white_rgba),
                "cpuMinusSkiaOverWhiteSignedDelta": signed_delta(cpu_rgba, skia_white_rgba),
                "skiaOverWhiteVsNaiveAbsDelta": None if naive is None else abs_delta(skia_white_rgba, naive),
                "cpuVsNaiveAbsDelta": None if naive is None else abs_delta(cpu_rgba, naive),
            }
        )
    return samples


def validate_samples(samples: list[dict[str, Any]], invalid: list[str]) -> None:
    sample_by_name = {sample["name"]: sample for sample in samples}
    for name in ["top_left_background", "top_edge_background", "left_edge_background", "cell_center_hole", "bottom_right_background"]:
        sample = sample_by_name.get(name)
        if sample is None:
            invalid.append(f"required zero-delta sample {name} missing")
        elif sample.get("cpuVsSkiaOverWhiteAbsDelta") != [0, 0, 0, 0]:
            invalid.append(f"{name} must have CPU vs Skia-over-white delta [0, 0, 0, 0]")

    for name in ["blue_top_stroke_center", "red_right_stroke_center", "red_bottom_stroke_center"]:
        sample = sample_by_name.get(name)
        if sample is None:
            invalid.append(f"required stroke-center sample {name} missing")
            continue
        if sample.get("skiaOverWhiteVsNaiveAbsDelta") != [0, 0, 0, 0]:
            invalid.append(f"{name} Skia-over-white must match naive alpha-over-white")
        if sum_rgb(sample.get("cpuVsSkiaOverWhiteAbsDelta", [0, 0, 0, 0])) == 0:
            invalid.append(f"{name} must preserve a residual CPU-vs-Skia stroke delta")


def validate_for330_metrics(for330: dict[str, Any] | None, invalid: list[str]) -> None:
    if for330 is None:
        return
    if for330.get("linear") != "FOR-330" or for330.get("sceneId") != FOR330_SCENE_ID:
        invalid.append("FOR-330 artifact identity changed")
    if for330.get("decision") != FOR330_DECISION_RESIDUAL:
        invalid.append(f"FOR-330 decision must be {FOR330_DECISION_RESIDUAL}")
    validate_cell(for330.get("selectedCell"), "FOR-330 selectedCell", invalid)
    comparison = for330.get("comparison")
    if not isinstance(comparison, dict):
        invalid.append("FOR-330 comparison missing")
        return
    for key, expected in EXPECTED_FOR330_NORMALIZED.items():
        if comparison.get(key) != expected:
            invalid.append(f"FOR-330 normalized metric {key} expected {expected!r}, got {comparison.get(key)!r}")
    for index, sample in enumerate(comparison.get("samples", [])):
        if not isinstance(sample, dict):
            invalid.append(f"FOR-330 comparison.samples[{index}] is not an object")
            continue
        if sample.get("name") in {"top_left_background", "top_edge_background", "left_edge_background", "cell_center_hole", "bottom_right_background"}:
            if sample.get("cpuMinusSkiaOverWhiteAbsDelta") != [0, 0, 0, 0]:
                invalid.append(f"FOR-330 sample {sample.get('name')} no longer has zero normalized delta")


def validate_inputs() -> tuple[dict[str, Any], list[str]]:
    invalid: list[str] = []
    for330 = optional_json(FOR330_ARTIFACT, invalid)
    for329 = optional_json(FOR329_ARTIFACT, invalid)
    for327 = optional_json(FOR327_ARTIFACT, invalid)
    for322 = optional_json(FOR322_ARTIFACT, invalid)
    for322_stats = optional_json(FOR322_STATS, invalid)
    route_cpu = optional_json(FOR322_ROUTE_CPU, invalid)

    skia_dimensions = image_dimensions(SKIA, invalid, "FOR-327 skia.png")
    skia_over_white_dimensions = image_dimensions(SKIA_OVER_WHITE, invalid, "FOR-330 skia-over-white.png")
    cpu_dimensions = image_dimensions(CPU, invalid, "FOR-322 cpu.png")
    for330_diff_dimensions = image_dimensions(FOR330_DIFF, invalid, "FOR-330 cpu-vs-skia-over-white-diff.png")

    validate_for330_metrics(for330, invalid)

    if for329 is not None:
        if for329.get("linear") != "FOR-329" or for329.get("decision") != FOR329_DECISION_IDENTIFIED:
            invalid.append("FOR-329 cause-identified prerequisite changed")
        validate_cell(for329.get("selectedCell"), "FOR-329 selectedCell", invalid)

    if for327 is not None:
        if for327.get("linear") != "FOR-327" or for327.get("decision") != FOR327_DECISION_READY:
            invalid.append("FOR-327 Skia reference prerequisite changed")
        validate_cell(for327.get("selectedCell"), "FOR-327 selectedCell", invalid)

    if for322 is not None:
        if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
            invalid.append("FOR-322 harness prerequisite changed")
        validate_cell(for322.get("selectedCell"), "FOR-322 selectedCell", invalid)

    if for322_stats is not None:
        if for322_stats.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM substitution")
        if for322_stats.get("fullGmReferenceAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM reference")
        validate_cell(for322_stats.get("cell"), "FOR-322 stats cell", invalid)

    if route_cpu is not None:
        if route_cpu.get("backend") != "CPU":
            invalid.append("FOR-322 route-cpu backend changed")
        if route_cpu.get("selectedRoute") != "cpu.raster.selected-cell-test-harness":
            invalid.append("FOR-322 route-cpu selectedRoute changed")
        if route_cpu.get("supportStatus") != "not-supported":
            invalid.append("FOR-322 route-cpu support status changed")
        validate_cell(route_cpu.get("cell"), "FOR-322 route-cpu cell", invalid)

    return {
        "follows": {
            "for330Artifact": rel(FOR330_ARTIFACT),
            "for330SkiaOverWhite": rel(SKIA_OVER_WHITE),
            "for330Diff": rel(FOR330_DIFF),
            "for329Artifact": rel(FOR329_ARTIFACT),
            "for327Artifact": rel(FOR327_ARTIFACT),
            "for327Skia": rel(SKIA),
            "for322Artifact": rel(FOR322_ARTIFACT),
            "for322Cpu": rel(CPU),
            "for322Stats": rel(FOR322_STATS),
            "for322RouteCpu": rel(FOR322_ROUTE_CPU),
        },
        "inputs": {
            "for330Artifact": {
                "path": rel(FOR330_ARTIFACT),
                "sourceLinear": "FOR-330",
                "sourceSceneId": FOR330_SCENE_ID,
                "expectedDecision": FOR330_DECISION_RESIDUAL,
                "sha256": sha256(FOR330_ARTIFACT) if FOR330_ARTIFACT.is_file() else None,
            },
            "skia": {
                "path": rel(SKIA),
                "sourceLinear": "FOR-327",
                "sourceSceneId": FOR327_SCENE_ID,
                "dimensions": skia_dimensions,
                "sha256": sha256(SKIA) if SKIA.is_file() else None,
            },
            "skiaOverWhite": {
                "path": rel(SKIA_OVER_WHITE),
                "sourceLinear": "FOR-330",
                "sourceSceneId": FOR330_SCENE_ID,
                "dimensions": skia_over_white_dimensions,
                "sha256": sha256(SKIA_OVER_WHITE) if SKIA_OVER_WHITE.is_file() else None,
            },
            "cpu": {
                "path": rel(CPU),
                "sourceLinear": "FOR-322",
                "sourceSceneId": FOR322_SCENE_ID,
                "dimensions": cpu_dimensions,
                "sha256": sha256(CPU) if CPU.is_file() else None,
            },
            "for330Diff": {
                "path": rel(FOR330_DIFF),
                "sourceLinear": "FOR-330",
                "sourceSceneId": FOR330_SCENE_ID,
                "dimensions": for330_diff_dimensions,
                "sha256": sha256(FOR330_DIFF) if FOR330_DIFF.is_file() else None,
            },
        },
    }, invalid


def trace_residual() -> dict[str, Any]:
    skia = rgba_image(SKIA)
    skia_over_white = rgba_image(SKIA_OVER_WHITE)
    cpu = rgba_image(CPU)
    if skia.size != EXPECTED_SIZE or skia_over_white.size != EXPECTED_SIZE or cpu.size != EXPECTED_SIZE:
        fail("trace_residual called with invalid dimensions")

    computed_metrics = compare_images(skia_over_white, cpu)
    samples = sample_trace(skia, skia_over_white, cpu)
    sample_invalid: list[str] = []
    validate_samples(samples, sample_invalid)

    center_samples = [sample for sample in samples if sample["zone"] == "stroke-center"]
    aa_samples = [sample for sample in samples if sample["zone"] == "stroke-aa-edge"]
    center_delta_total = sum(sum_rgb(sample["cpuVsSkiaOverWhiteAbsDelta"]) for sample in center_samples)
    aa_delta_total = sum(sum_rgb(sample["cpuVsSkiaOverWhiteAbsDelta"]) for sample in aa_samples)
    skia_naive_mismatches = [
        sample["name"]
        for sample in samples
        if sample["paintColor"] is not None and sample["skiaOverWhiteVsNaiveAbsDelta"] != [0, 0, 0, 0]
    ]

    naive_models = {
        "method": "round(src_srgb_channel * (alpha / 255) + 255 * (1 - alpha / 255)); output alpha fixed at 255",
        "paintAlpha": 100,
        "fullCoverage": {
            "redOverWhiteRgba": alpha_over_white_naive("red", 100),
            "blueOverWhiteRgba": alpha_over_white_naive("blue", 100),
        },
        "sampleSpecific": [
            {
                "sample": sample["name"],
                "paintColor": sample["paintColor"],
                "skiaAlphaUsedAsEffectiveAlpha": sample["skiaRgba"][3],
                "naiveAlphaOverWhiteRgba": sample["naiveAlphaOverWhiteRgba"],
                "skiaOverWhiteVsNaiveAbsDelta": sample["skiaOverWhiteVsNaiveAbsDelta"],
                "cpuVsNaiveAbsDelta": sample["cpuVsNaiveAbsDelta"],
            }
            for sample in samples
            if sample["paintColor"] is not None
        ],
    }

    return {
        "metrics": computed_metrics,
        "samples": samples,
        "sampleValidation": {
            "valid": not sample_invalid,
            "zeroDeltaSamples": [
                "top_left_background",
                "top_edge_background",
                "left_edge_background",
                "cell_center_hole",
                "bottom_right_background",
            ],
            "invalidReasons": sample_invalid,
        },
        "naiveAlphaOverWhite": naive_models,
        "comparisons": {
            "cpuVsSkiaOverWhite": {
                "summary": "strict RGBA comparison after FOR-330 white-background normalization",
                "metrics": computed_metrics,
            },
            "skiaVsNaiveAlphaOverWhite": {
                "summary": "sample-level check that Skia-over-white matches naive alpha-over-white for sampled stroke pixels",
                "mismatchingSampleNames": skia_naive_mismatches,
            },
            "cpuVsNaiveAlphaOverWhite": {
                "summary": "CPU remains offset from the same naive model at stroke-center and AA-edge samples",
                "strokeCenterRgbDeltaTotal": center_delta_total,
                "aaEdgeRgbDeltaTotal": aa_delta_total,
            },
        },
        "strokeResidualLocalization": {
            "expectedStrokeBoundingBox": EXPECTED_STROKE_BBOX,
            "actualDifferentPixelBoundingBox": computed_metrics["differentPixelBoundingBox"],
            "differentPixelsOutsideExpectedStrokeBoundingBox": computed_metrics[
                "differentPixelsOutsideExpectedStrokeBoundingBox"
            ],
            "backgroundAndCenterHoleMatch": not sample_invalid,
        },
    }


def classify(trace: dict[str, Any], invalid_reasons: list[str]) -> dict[str, Any]:
    if invalid_reasons:
        return {
            "decision": DECISION_INVALID,
            "classification": "input-invalid",
            "primaryHypothesis": "input invalid",
            "confidence": "high",
            "rationale": "one or more prerequisite artifacts changed or disappeared",
            "recommendedNextAction": "repair FOR-330/FOR-329/FOR-327/FOR-322 prerequisites before tracing the residual",
            "hypotheses": [
                {
                    "id": "input-invalid",
                    "weight": 1.0,
                    "status": "accepted-only-when-prerequisites-are-invalid",
                    "evidence": invalid_reasons,
                }
            ],
        }

    metrics = trace["metrics"]
    sample_validation = trace["sampleValidation"]
    comparisons = trace["comparisons"]
    stroke_center_delta = comparisons["cpuVsNaiveAlphaOverWhite"]["strokeCenterRgbDeltaTotal"]
    skia_naive_mismatches = comparisons["skiaVsNaiveAlphaOverWhite"]["mismatchingSampleNames"]
    localized = (
        metrics["differentPixelBoundingBox"] == EXPECTED_STROKE_BBOX
        and metrics["differentPixelsOutsideExpectedStrokeBoundingBox"] == 0
    )
    centers_differ = stroke_center_delta > 0
    skia_matches_naive = not skia_naive_mismatches

    if localized and sample_validation["valid"] and centers_differ and skia_matches_naive:
        decision = DECISION_COLORSPACE_PREMUL
        primary = "colorspace/premul suspected"
        confidence = "medium"
        rationale = (
            "Background and center-hole samples are exact after white normalization, and Skia-over-white matches "
            "the naive alpha-over-white model at sampled stroke pixels. The CPU still differs at full paint-alpha "
            "stroke centers, so the residual is not explained by background normalization or by edge coverage alone."
        )
        next_action = (
            "Add an instrumented CPU trace at paint color conversion, premul/unpremul, F16/Rec.2020 to PNG encode, "
            "coverage, and SrcOver store boundaries before changing the CPU renderer."
        )
    elif localized and sample_validation["valid"]:
        decision = DECISION_COVERAGE
        primary = "coverage suspected"
        confidence = "low"
        rationale = (
            "The residual is localized to the stroke bbox, but the current sample set does not show a stronger "
            "stroke-center color/premul signal than edge coverage."
        )
        next_action = "Instrument CPU coverage and store boundaries before any renderer change."
    else:
        decision = DECISION_REQUIRES_TRACE
        primary = "requires instrumented CPU trace"
        confidence = "medium"
        rationale = "The available artifact-level evidence is insufficient to classify the residual safely."
        next_action = "Capture an instrumented CPU trace before making a renderer correction."

    return {
        "decision": decision,
        "classification": primary,
        "primaryHypothesis": primary,
        "confidence": confidence,
        "rationale": rationale,
        "recommendedNextAction": next_action,
        "hypotheses": [
            {
                "id": "colorspace-premul-or-png-encode",
                "weight": 0.62 if decision == DECISION_COLORSPACE_PREMUL else 0.34,
                "status": "most-likely" if decision == DECISION_COLORSPACE_PREMUL else "possible",
                "evidence": [
                    "stroke-center samples differ after background normalization",
                    "Skia-over-white equals the naive alpha-over-white sample model",
                    "FOR-329 source audit found CPU uses F16/Rec.2020 output before PNG encoding while Skia reference is N32 premul sRGB",
                ],
            },
            {
                "id": "stroke-coverage-aa",
                "weight": 0.24 if decision == DECISION_COLORSPACE_PREMUL else 0.45,
                "status": "secondary" if decision == DECISION_COLORSPACE_PREMUL else "possible",
                "evidence": [
                    "residual bbox is exactly [12,12]-[67,67]",
                    "AA-edge samples also differ",
                    "coverage cannot explain the stroke-center color offsets by itself from the current samples",
                ],
            },
            {
                "id": "paint-alpha-or-src-over-formula",
                "weight": 0.09,
                "status": "less-likely-from-samples",
                "evidence": [
                    "naive 100/255 alpha-over-white produces Skia center values [255,155,155,255] and [155,155,255,255]",
                    "CPU remains offset from those values",
                ],
            },
            {
                "id": "requires-instrumented-cpu-trace-before-fix",
                "weight": 0.05,
                "status": "required-next-evidence",
                "evidence": [
                    "artifact-level PNG samples cannot separate color-space conversion, premul/unpremul, coverage, and store boundaries",
                    "no renderer correction is made by this ticket",
                ],
            },
        ],
    }


def build_artifact() -> dict[str, Any]:
    validated, invalid_reasons = validate_inputs()
    trace = None if invalid_reasons else trace_residual()
    if trace is not None:
        invalid_reasons.extend(trace["sampleValidation"]["invalidReasons"])
        for key, expected in EXPECTED_FOR330_NORMALIZED.items():
            if trace["metrics"].get(key) != expected:
                invalid_reasons.append(
                    f"computed normalized metric {key} expected {expected!r}, got {trace['metrics'].get(key)!r}"
                )
    decision = classify(trace or {}, invalid_reasons)
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "decision": decision["decision"],
        "allowedDecisions": [
            DECISION_COLORSPACE_PREMUL,
            DECISION_COVERAGE,
            DECISION_REQUIRES_TRACE,
            DECISION_INVALID,
        ],
        "sceneId": SCENE_ID,
        "selectedCell": SELECTED_CELL_EXPECTED,
        "follows": validated["follows"],
        "inputs": validated["inputs"],
        "inputValidation": {
            "valid": not invalid_reasons,
            "expectedDimensions": EXPECTED_DIMENSIONS,
            "requiredFor330Decision": FOR330_DECISION_RESIDUAL,
            "expectedFor330NormalizedMetrics": EXPECTED_FOR330_NORMALIZED,
            "invalidReasons": invalid_reasons,
        },
        "trace": trace,
        "assessment": decision,
        "nonGoals": {
            "cpuRendererFix": False,
            "gpuOrWgslWork": False,
            "thresholdChange": False,
            "fallbackPolicyChange": False,
            "kadreNativeDependency": False,
            "scenePromotion": False,
            "fidelityScore": False,
            "fullGmCropAccepted": False,
            "fullGmSubstitutionAccepted": False,
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
        "| `{name}` | `{zone}` | `{xy}` | `{skia}` | `{white}` | `{cpu}` | `{naive}` | `{delta}` |".format(
            name=sample["name"],
            zone=sample["zone"],
            xy=f"{sample['x']},{sample['y']}",
            skia=sample["skiaRgba"],
            white=sample["skiaOverWhiteRgba"],
            cpu=sample["cpuRgba"],
            naive=sample["naiveAlphaOverWhiteRgba"],
            delta=sample["cpuVsSkiaOverWhiteAbsDelta"],
        )
        for sample in samples
    )


def hypothesis_table(hypotheses: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{id}` | `{weight}` | `{status}` | {evidence} |".format(
            id=item["id"],
            weight=item["weight"],
            status=item["status"],
            evidence="; ".join(item["evidence"]),
        )
        for item in hypotheses
    )


def write_report(data: dict[str, Any]) -> None:
    validation_lines = "\n".join(f"- `{command}`" for command in data["validation"])
    invalid_lines = "\n".join(f"- {reason}" for reason in data["inputValidation"]["invalidReasons"]) or "- none"
    inputs = data["inputs"]
    contracts = data["preservedContracts"]
    assessment = data["assessment"]
    trace = data.get("trace")

    if trace is None:
        trace_section = "Input validation failed; no residual trace was produced."
        samples_section = ""
        hypothesis_section = hypothesis_table(assessment["hypotheses"])
    else:
        metrics = trace["metrics"]
        localization = trace["strokeResidualLocalization"]
        naive = trace["naiveAlphaOverWhite"]
        trace_section = f"""| Field | Value |
|---|---|
| total pixels | `{metrics["totalPixels"]}` |
| different pixels | `{metrics["differentPixels"]}` |
| matching pixels | `{metrics["matchingPixels"]}` |
| cell similarity percent | `{metrics["cellSimilarityPercent"]}` |
| max delta by channel | `{md_value(metrics["maxDeltaByChannel"])}` |
| sum abs delta by channel | `{md_value(metrics["sumAbsDeltaByChannel"])}` |
| sum abs delta total | `{metrics["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(metrics["differentPixelBoundingBox"])}` |
| expected stroke bounding box | `{md_value(localization["expectedStrokeBoundingBox"])}` |
| different pixels outside expected stroke bbox | `{localization["differentPixelsOutsideExpectedStrokeBoundingBox"]}` |
| background and center-hole match | `{localization["backgroundAndCenterHoleMatch"]}` |
| naive red alpha-over-white | `{naive["fullCoverage"]["redOverWhiteRgba"]}` |
| naive blue alpha-over-white | `{naive["fullCoverage"]["blueOverWhiteRgba"]}` |"""
        samples_section = f"""## Samples

| Sample | Zone | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | Naive alpha-over-white RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|---|---|
{sample_table(trace["samples"])}
"""
        hypothesis_section = hypothesis_table(assessment["hypotheses"])

    report = f"""# FOR-331 CircularArcsStrokeButt Selected-Cell Normalized Stroke Trace

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Source finding:
`{SOURCE_FINDING}`

Decision: `{data["decision"]}`

## Result

FOR-331 adds a selected-cell audit trace for the FOR-330 normalized-background
residual. It does not fix the CPU renderer and does not change GPU, WGSL,
threshold, fallback, Kadre, scene-promotion, or fidelity-score behavior.

Interpretation: {assessment["rationale"]}

Most likely track: `{assessment["primaryHypothesis"]}` with `{assessment["confidence"]}` confidence.

Recommended next action: {assessment["recommendedNextAction"]}

## Inputs

| Input | Path | Source | Dimensions / decision | SHA-256 |
|---|---|---|---|---|
| FOR-330 audit | `{inputs["for330Artifact"]["path"]}` | `{inputs["for330Artifact"]["sourceLinear"]}` / `{inputs["for330Artifact"]["sourceSceneId"]}` | `{inputs["for330Artifact"]["expectedDecision"]}` | `{inputs["for330Artifact"]["sha256"]}` |
| Skia | `{inputs["skia"]["path"]}` | `{inputs["skia"]["sourceLinear"]}` / `{inputs["skia"]["sourceSceneId"]}` | `{md_value(inputs["skia"]["dimensions"])}` | `{inputs["skia"]["sha256"]}` |
| Skia over white | `{inputs["skiaOverWhite"]["path"]}` | `{inputs["skiaOverWhite"]["sourceLinear"]}` / `{inputs["skiaOverWhite"]["sourceSceneId"]}` | `{md_value(inputs["skiaOverWhite"]["dimensions"])}` | `{inputs["skiaOverWhite"]["sha256"]}` |
| CPU Kanvas | `{inputs["cpu"]["path"]}` | `{inputs["cpu"]["sourceLinear"]}` / `{inputs["cpu"]["sourceSceneId"]}` | `{md_value(inputs["cpu"]["dimensions"])}` | `{inputs["cpu"]["sha256"]}` |
| FOR-330 diff | `{inputs["for330Diff"]["path"]}` | `{inputs["for330Diff"]["sourceLinear"]}` / `{inputs["for330Diff"]["sourceSceneId"]}` | `{md_value(inputs["for330Diff"]["dimensions"])}` | `{inputs["for330Diff"]["sha256"]}` |

Input validation valid: `{data["inputValidation"]["valid"]}`

Invalid reasons:

{invalid_lines}

## Normalized Stroke Trace

{trace_section}

{samples_section}
## Weighted Hypotheses

| Hypothesis | Weight | Status | Evidence |
|---|---|---|---|
{hypothesis_section}

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


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("sourceFinding") != SOURCE_FINDING:
        fail("artifact source finding changed")
    if data.get("decision") not in data.get("allowedDecisions", []):
        fail("artifact decision is not allowed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    assert_cell(data.get("selectedCell"), "artifact selectedCell")

    input_validation = data.get("inputValidation")
    if not isinstance(input_validation, dict):
        fail("artifact missing inputValidation")
    if input_validation.get("requiredFor330Decision") != FOR330_DECISION_RESIDUAL:
        fail("artifact required FOR-330 decision changed")
    if input_validation.get("expectedFor330NormalizedMetrics") != EXPECTED_FOR330_NORMALIZED:
        fail("artifact expected FOR-330 metrics changed")

    if data.get("decision") == DECISION_INVALID:
        if input_validation.get("valid") is not False:
            fail("invalid decision requires invalid inputValidation")
        return

    if input_validation.get("valid") is not True:
        fail("non-invalid decision requires valid inputValidation")
    trace = data.get("trace")
    if not isinstance(trace, dict):
        fail("non-invalid decision requires trace")
    metrics = trace.get("metrics")
    if not isinstance(metrics, dict):
        fail("trace metrics missing")
    for key, expected in EXPECTED_FOR330_NORMALIZED.items():
        if metrics.get(key) != expected:
            fail(f"trace metric {key} expected {expected!r}, got {metrics.get(key)!r}")
    if metrics.get("differentPixelsOutsideExpectedStrokeBoundingBox") != 0:
        fail("trace residual must be concentrated inside the expected stroke bbox")

    samples = trace.get("samples")
    if not isinstance(samples, list) or len(samples) != len(SAMPLES):
        fail("trace samples missing")
    sample_invalid: list[str] = []
    validate_samples(samples, sample_invalid)
    if sample_invalid:
        fail("; ".join(sample_invalid))

    naive = trace.get("naiveAlphaOverWhite")
    if not isinstance(naive, dict):
        fail("trace naiveAlphaOverWhite missing")
    full_coverage = naive.get("fullCoverage")
    if not isinstance(full_coverage, dict):
        fail("trace naiveAlphaOverWhite.fullCoverage missing")
    if full_coverage.get("redOverWhiteRgba") != [255, 155, 155, 255]:
        fail("naive red alpha-over-white changed")
    if full_coverage.get("blueOverWhiteRgba") != [155, 155, 255, 255]:
        fail("naive blue alpha-over-white changed")

    assessment = data.get("assessment")
    if not isinstance(assessment, dict):
        fail("artifact assessment missing")
    if assessment.get("decision") != data.get("decision"):
        fail("assessment decision must match top-level decision")
    if not isinstance(assessment.get("hypotheses"), list) or len(assessment["hypotheses"]) < 3:
        fail("assessment hypotheses missing")

    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    if preserved.get("diagnosticOnly") is not True:
        fail("preservedContracts.diagnosticOnly must be true")
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

    non_goals = data.get("nonGoals")
    if not isinstance(non_goals, dict):
        fail("artifact missing nonGoals")
    for key, value in non_goals.items():
        if value is not False:
            fail(f"nonGoals.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        SOURCE_FINDING,
        data["decision"],
        "Most likely track",
        "colorspace/premul suspected",
        "instrumented CPU trace",
        "Normalized Stroke Trace",
        "naive red alpha-over-white",
        "naive blue alpha-over-white",
        "background and center-hole match",
        "different pixels outside expected stroke bbox",
        "Weighted Hypotheses",
        "Non-Goals And Preserved Contracts",
        "does not fix the CPU renderer",
        "GPU rendered",
        "WGSL changed",
        "threshold changed",
        "fallback policy changed",
        "Kadre/native dependency added",
        "scene promotion changed",
        "fidelity score counted",
        "full-GM crop accepted",
        "full-GM substitution accepted",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")

    unsafe_patterns = [
        r"\bproduction renderer changed\s+\|\s+`?True",
        r"\bCPU renderer fixed\s+\|\s+`?True",
        r"\bGPU rendered\s+\|\s+`?True",
        r"\bWGSL changed\s+\|\s+`?True",
        r"\bthreshold changed\s+\|\s+`?True",
        r"\bfallback policy changed\s+\|\s+`?True",
        r"\bKadre/native dependency added\s+\|\s+`?True",
        r"\bscene promotion changed\s+\|\s+`?True",
        r"\bfidelity score counted\s+\|\s+`?True",
        r"\bfull-GM crop accepted\s+\|\s+`?True",
        r"\bfull-GM substitution accepted\s+\|\s+`?True",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, flags=re.IGNORECASE):
            fail(f"report contains unsafe support language matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    loaded = load_json(ARTIFACT)
    validate_artifact_shape(loaded)
    write_report(loaded)
    validate_report(loaded)
    if loaded["decision"] == DECISION_INVALID:
        fail("; ".join(loaded["inputValidation"]["invalidReasons"]) or "input invalid")
    print(loaded["decision"])


if __name__ == "__main__":
    main()
