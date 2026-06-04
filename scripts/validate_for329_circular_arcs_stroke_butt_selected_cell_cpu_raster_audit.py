#!/usr/bin/env python3
"""Audit the FOR-328 selected-cell CPU raster mismatch against Skia."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

from PIL import Image


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-329"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-329-circular-arcs-stroke-butt-selected-cell-cpu-raster-audit.md"
)

FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR327_SCENE_ID
FOR327_ARTIFACT = FOR327_DIR / f"{FOR327_SCENE_ID}.json"
SKIA = FOR327_DIR / "skia.png"
SKIA_PROVENANCE = FOR327_DIR / "skia-reference-provenance.json"

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
CPU = FOR322_DIR / "cpu.png"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR322_ROUTE_CPU = FOR322_DIR / "route-cpu.json"

FOR328_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328"
FOR328_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR328_SCENE_ID
FOR328_ARTIFACT = FOR328_DIR / f"{FOR328_SCENE_ID}.json"
DIFF = FOR328_DIR / "cpu-vs-skia-diff.png"

SKIA_SOURCE = PROJECT_ROOT / "tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp"
CPU_HARNESS = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    "CircularArcsStrokeButtSelectedCellCaptureTest.kt"
)
TEST_UTILS = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt"
RASTER_SINK_F16 = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/dm/RasterSinkF16.kt"
GM_SOURCE = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/tests/GM.kt"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
EXPECTED_SIZE = (80, 80)
DECISION_IDENTIFIED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED"
DECISION_TRACE = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_REQUIRES_TRACE"
DECISION_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_INPUT_INVALID"
CAUSE_BACKGROUND_ALPHA = "CAUSE_CANDIDATE_BACKGROUND_ALPHA_CONTRACT_MISMATCH"
CAUSE_RESIDUAL_TRACE = "CAUSE_RESIDUAL_REQUIRES_NORMALIZED_BACKGROUND_CPU_TRACE"
FOR327_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
FOR328_DECISION_AUDIT = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT"

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
    ("top_left_background", 0, 0),
    ("top_edge_background", 40, 0),
    ("left_edge_background", 0, 40),
    ("arc_rect_top_left", 20, 20),
    ("blue_top_stroke_center", 40, 20),
    ("red_right_stroke_center", 60, 40),
    ("red_bottom_stroke_center", 40, 60),
    ("cell_center_hole", 40, 40),
    ("bottom_right_background", 79, 79),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-329 validation failed: {message}")


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


def expected_hashes_from_for328(for328: dict[str, Any] | None, invalid: list[str]) -> dict[str, str | None]:
    hashes = {"skia": None, "cpu": None, "diff": None}
    if for328 is None:
        return hashes
    comparison = for328.get("comparison")
    if not isinstance(comparison, dict):
        invalid.append("FOR-328 comparison missing")
        return hashes
    comparison_hashes = comparison.get("hashes")
    if not isinstance(comparison_hashes, dict):
        invalid.append("FOR-328 comparison.hashes missing")
        return hashes
    hashes["skia"] = comparison_hashes.get("skiaPngSha256")
    hashes["cpu"] = comparison_hashes.get("cpuPngSha256")
    hashes["diff"] = comparison_hashes.get("diffPngSha256")
    return hashes


def validate_inputs() -> tuple[dict[str, Any], list[str]]:
    invalid: list[str] = []
    for327 = optional_json(FOR327_ARTIFACT, invalid)
    for322 = optional_json(FOR322_ARTIFACT, invalid)
    for322_stats = optional_json(FOR322_STATS, invalid)
    for322_route_cpu = optional_json(FOR322_ROUTE_CPU, invalid)
    for328 = optional_json(FOR328_ARTIFACT, invalid)
    provenance = optional_json(SKIA_PROVENANCE, invalid)

    skia_dimensions = image_dimensions(SKIA, invalid, "FOR-327 skia.png")
    cpu_dimensions = image_dimensions(CPU, invalid, "FOR-322 cpu.png")
    diff_dimensions = image_dimensions(DIFF, invalid, "FOR-328 cpu-vs-skia-diff.png")

    if for327 is not None:
        if for327.get("linear") != "FOR-327" or for327.get("decision") != FOR327_DECISION_READY:
            invalid.append("FOR-327 accepted Skia reference contract changed")
        if for327.get("acceptedSkiaPng") != rel(SKIA):
            invalid.append("FOR-327 acceptedSkiaPng does not point at skia.png")
        validate_cell(for327.get("selectedCell"), "FOR-327 selectedCell", invalid)

    if for322 is not None:
        if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
            invalid.append("FOR-322 harness contract changed")
        validate_cell(for322.get("selectedCell"), "FOR-322 selectedCell", invalid)

    if for322_stats is not None:
        if for322_stats.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM substitution")
        if for322_stats.get("fullGmReferenceAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM reference")
        validate_cell(for322_stats.get("cell"), "FOR-322 stats cell", invalid)

    if for322_route_cpu is not None:
        if for322_route_cpu.get("backend") != "CPU":
            invalid.append("FOR-322 route-cpu backend changed")
        if for322_route_cpu.get("selectedRoute") != "cpu.raster.selected-cell-test-harness":
            invalid.append("FOR-322 CPU selectedRoute changed")
        if for322_route_cpu.get("supportStatus") != "not-supported":
            invalid.append("FOR-322 CPU route support status changed")
        validate_cell(for322_route_cpu.get("cell"), "FOR-322 route-cpu cell", invalid)

    if for328 is not None:
        if for328.get("linear") != "FOR-328" or for328.get("decision") != FOR328_DECISION_AUDIT:
            invalid.append("FOR-328 diff prerequisite no longer requires raster audit")
        validate_cell(for328.get("selectedCell"), "FOR-328 selectedCell", invalid)
        comparison = for328.get("comparison")
        if isinstance(comparison, dict):
            if comparison.get("differentPixels") != 6400 or comparison.get("matchingPixels") != 0:
                invalid.append("FOR-328 strict pixel diff counts changed")
            if comparison.get("cellSimilarityPercent") != 0.0:
                invalid.append("FOR-328 similarity changed")

    if provenance is not None:
        if provenance.get("sourceType") != "isolated-skia-selected-cell-render":
            invalid.append("FOR-327 provenance sourceType changed")
        if provenance.get("fullGmCrop") is not False:
            invalid.append("FOR-327 provenance accepts full-GM crop")
        if provenance.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-327 provenance accepts full-GM substitution")
        if provenance.get("cpuKanvasOutputAcceptedAsSkia") is not False:
            invalid.append("FOR-327 provenance accepts CPU output as Skia")

    expected_hashes = expected_hashes_from_for328(for328, invalid)
    actual_hashes = {
        "skia": sha256(SKIA) if SKIA.is_file() else None,
        "cpu": sha256(CPU) if CPU.is_file() else None,
        "diff": sha256(DIFF) if DIFF.is_file() else None,
    }
    for key, expected in expected_hashes.items():
        if expected is not None and actual_hashes.get(key) != expected:
            invalid.append(f"{key} PNG sha256 changed from FOR-328 expected hash")

    return {
        "follows": {
            "for327Artifact": rel(FOR327_ARTIFACT),
            "for327Skia": rel(SKIA),
            "for327Provenance": rel(SKIA_PROVENANCE),
            "for322Artifact": rel(FOR322_ARTIFACT),
            "for322Cpu": rel(CPU),
            "for322Stats": rel(FOR322_STATS),
            "for322RouteCpu": rel(FOR322_ROUTE_CPU),
            "for328Artifact": rel(FOR328_ARTIFACT),
            "for328Diff": rel(DIFF),
        },
        "inputs": {
            "skia": {
                "path": rel(SKIA),
                "sourceLinear": "FOR-327",
                "dimensions": skia_dimensions,
                "sha256": actual_hashes["skia"],
                "expectedSha256FromFor328": expected_hashes["skia"],
            },
            "cpu": {
                "path": rel(CPU),
                "sourceLinear": "FOR-322",
                "dimensions": cpu_dimensions,
                "sha256": actual_hashes["cpu"],
                "expectedSha256FromFor328": expected_hashes["cpu"],
            },
            "diff": {
                "path": rel(DIFF),
                "sourceLinear": "FOR-328",
                "dimensions": diff_dimensions,
                "sha256": actual_hashes["diff"],
                "expectedSha256FromFor328": expected_hashes["diff"],
            },
        },
    }, invalid


def pixels(image: Image.Image) -> list[tuple[int, int, int, int]]:
    image_pixels = image.load()
    return [
        tuple(image_pixels[x, y])
        for y in range(image.size[1])
        for x in range(image.size[0])
    ]


def bbox_from_predicate(image: Image.Image, predicate: Any) -> dict[str, int] | None:
    width, height = image.size
    min_x = width
    min_y = height
    max_x = -1
    max_y = -1
    image_pixels = image.load()
    for y in range(height):
        for x in range(width):
            if predicate(image_pixels[x, y]):
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if max_x < 0:
        return None
    return {"left": min_x, "top": min_y, "right": max_x, "bottom": max_y}


def image_metrics(image: Image.Image, label: str) -> dict[str, Any]:
    image_pixels = pixels(image)
    total = len(image_pixels)
    alpha_values = [pixel[3] for pixel in image_pixels]
    alpha_counter = Counter(alpha_values)
    rgba_counter = Counter(image_pixels)
    return {
        "label": label,
        "dimensions": {"width": image.size[0], "height": image.size[1]},
        "totalPixels": total,
        "transparentPixels": alpha_counter.get(0, 0),
        "nonTransparentPixels": total - alpha_counter.get(0, 0),
        "opaquePixels": alpha_counter.get(255, 0),
        "partialAlphaPixels": total - alpha_counter.get(0, 0) - alpha_counter.get(255, 0),
        "nonTransparentBoundingBox": bbox_from_predicate(image, lambda pixel: pixel[3] != 0),
        "nonWhiteBoundingBox": bbox_from_predicate(image, lambda pixel: pixel != (255, 255, 255, 255)),
        "alphaDistributionTop": [
            {"alpha": alpha, "pixels": count}
            for alpha, count in alpha_counter.most_common(16)
        ],
        "distinctAlphaCount": len(alpha_counter),
        "topRgba": [
            {"rgba": list(rgba), "pixels": count}
            for rgba, count in rgba_counter.most_common(16)
        ],
        "distinctRgbaCount": len(rgba_counter),
    }


def compare_images(reference: Image.Image, actual: Image.Image, label: str) -> dict[str, Any]:
    if reference.size != actual.size:
        fail(f"image size mismatch for {label}")
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
    for y in range(height):
        for x in range(width):
            delta = tuple(abs(int(actual_pixels[x, y][index]) - int(ref_pixels[x, y][index])) for index in range(4))
            if delta != (0, 0, 0, 0):
                different += 1
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
                for index, value in enumerate(delta):
                    max_delta[index] = max(max_delta[index], value)
                    sum_abs_delta[index] += value
    total = width * height
    return {
        "label": label,
        "totalPixels": total,
        "differentPixels": different,
        "matchingPixels": total - different,
        "similarityPercent": round(((total - different) / total) * 100.0, 6),
        "maxDeltaByChannel": {"r": max_delta[0], "g": max_delta[1], "b": max_delta[2], "a": max_delta[3]},
        "sumAbsDeltaByChannel": {"r": sum_abs_delta[0], "g": sum_abs_delta[1], "b": sum_abs_delta[2], "a": sum_abs_delta[3]},
        "sumAbsDeltaTotal": sum(sum_abs_delta),
        "differentPixelBoundingBox": None if different == 0 else {"left": min_x, "top": min_y, "right": max_x, "bottom": max_y},
    }


def sample_points(skia: Image.Image, cpu: Image.Image, diff: Image.Image, skia_over_white: Image.Image) -> list[dict[str, Any]]:
    result = []
    skia_pixels = skia.load()
    cpu_pixels = cpu.load()
    diff_pixels = diff.load()
    white_pixels = skia_over_white.load()
    for name, x, y in SAMPLES:
        skia_rgba = tuple(skia_pixels[x, y])
        cpu_rgba = tuple(cpu_pixels[x, y])
        white_rgba = tuple(white_pixels[x, y])
        result.append(
            {
                "name": name,
                "x": x,
                "y": y,
                "skiaRgba": list(skia_rgba),
                "cpuRgba": list(cpu_rgba),
                "for328DiffRgba": list(tuple(diff_pixels[x, y])),
                "skiaCompositedOverWhiteRgba": list(white_rgba),
                "cpuMinusSkiaAbsDelta": [abs(cpu_rgba[index] - skia_rgba[index]) for index in range(4)],
                "cpuMinusSkiaOverWhiteAbsDelta": [abs(cpu_rgba[index] - white_rgba[index]) for index in range(4)],
            }
        )
    return result


def require_needles(text: str, label: str, needles: list[str], invalid: list[str]) -> None:
    for needle in needles:
        if needle not in text:
            invalid.append(f"{label} missing `{needle}`")


def source_audit(invalid: list[str]) -> dict[str, Any]:
    skia_source = read_text(SKIA_SOURCE)
    cpu_harness = read_text(CPU_HARNESS)
    test_utils = read_text(TEST_UTILS)
    raster_sink = read_text(RASTER_SINK_F16)
    gm_source = read_text(GM_SOURCE)

    require_needles(
        skia_source,
        "Skia source",
        [
            "SkImageInfo::MakeN32Premul(80, 80, SkColorSpace::MakeSRGB())",
            "canvas->clear(SK_ColorTRANSPARENT)",
            "SkRect::MakeLTRB(20, 20, 60, 60)",
            "red.setAntiAlias(true)",
            "red.setStyle(SkPaint::kStroke_Style)",
            "red.setStrokeWidth(15)",
            "red.setStrokeCap(SkPaint::kButt_Cap)",
            "SkColorSetARGB(100, 255, 0, 0)",
            "SkColorSetARGB(100, 0, 0, 255)",
            "canvas->drawArc(arcRect, 0, 90, false, red)",
            "canvas->drawArc(arcRect, 0, -270, false, blue)",
        ],
        invalid,
    )
    require_needles(
        cpu_harness,
        "FOR-322 CPU harness",
        [
            "val cpuBitmap = TestUtils.runGmTest(gm)",
            "SkISize.Make(WIDTH, HEIGHT)",
            "c.translate(20f, 20f)",
            "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
            "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
            "this.color = (100 shl 24) or (color and 0x00FFFFFF)",
            "isAntiAlias = true",
            "style = SkPaint.Style.kStroke_Style",
            "strokeWidth = 15f",
            "strokeCap = SkPaint.Cap.kButt_Cap",
            "SkRect.MakeLTRB(0f, 0f, 40f, 40f)",
        ],
        invalid,
    )
    require_needles(
        test_utils,
        "TestUtils.runGmTest",
        ["public fun runGmTest(gm: GM): SkBitmap", "RasterSinkF16(DM_REFERENCE_COLOR_SPACE)"],
        invalid,
    )
    require_needles(
        raster_sink,
        "RasterSinkF16",
        ["SkColorType.kRGBA_F16Norm", "bitmap.eraseColor(src.bgColor())"],
        invalid,
    )
    require_needles(
        gm_source,
        "GM",
        ["private var fBGColor: SkColor = SK_ColorWHITE", "public fun bgColor(): SkColor = fBGColor"],
        invalid,
    )

    return {
        "skia": {
            "source": rel(SKIA_SOURCE),
            "surface": "SkImageInfo::MakeN32Premul(80, 80, SkColorSpace::MakeSRGB())",
            "clearOrBackground": "canvas->clear(SK_ColorTRANSPARENT)",
            "arcRectDeviceLTRB": [20, 20, 60, 60],
            "localCoordinates": "direct device-space rect",
            "colors": [
                {"name": "red", "argb": [100, 255, 0, 0]},
                {"name": "blue", "argb": [100, 0, 0, 255]},
            ],
            "alpha": 100,
            "antiAlias": True,
            "strokeWidth": 15,
            "strokeCap": "kButt_Cap",
            "style": "kStroke_Style",
            "drawArcOrder": [
                {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90, "useCenter": False},
                {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270, "useCenter": False},
            ],
            "pngEncoding": "SkPngEncoder over SkPixmap from N32 premul sRGB surface",
        },
        "cpu": {
            "harness": rel(CPU_HARNESS),
            "testUtils": rel(TEST_UTILS),
            "rasterSink": rel(RASTER_SINK_F16),
            "gmSource": rel(GM_SOURCE),
            "surface": "RasterSinkF16 with SkColorType.kRGBA_F16Norm and DM_REFERENCE_COLOR_SPACE",
            "clearOrBackground": "bitmap.eraseColor(src.bgColor()); GM default bgColor is SK_ColorWHITE",
            "arcRectDeviceLTRB": [20, 20, 60, 60],
            "localCoordinates": "local SkRect(0,0,40,40) after c.translate(20,20)",
            "colors": [
                {"name": "red", "argb": [100, 255, 0, 0]},
                {"name": "blue", "argb": [100, 0, 0, 255]},
            ],
            "alpha": 100,
            "antiAlias": True,
            "strokeWidth": 15,
            "strokeCap": "kButt_Cap",
            "style": "kStroke_Style",
            "drawArcOrder": [
                {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90, "useCenter": False},
                {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270, "useCenter": False},
            ],
            "pngEncoding": "SkPngEncoder.Encode(bitmap) after F16/Rec.2020 CPU raster output",
        },
        "comparison": {
            "matchingSourceFacts": [
                "80x80 surface size",
                "device arc bounds [20, 20, 60, 60]",
                "red arc 0..90 before blue complement 0..-270",
                "useCenter=false",
                "aa=true",
                "strokeWidth=15",
                "strokeCap=kButt_Cap",
                "paint alpha=100",
            ],
            "divergentSourceFacts": [
                "Skia source clears to transparent",
                "CPU harness uses GM default white background through TestUtils.runGmTest/RasterSinkF16",
                "Skia source uses N32 premul sRGB, CPU harness renders through F16 Rec.2020 then PNG encodes",
            ],
            "notSourceSupportedAsPrimaryCause": [
                "arc order",
                "arc bounds",
                "stroke cap",
                "stroke width",
                "useCenter",
                "local placement",
            ],
        },
    }


def local_metrics() -> dict[str, Any]:
    skia = rgba_image(SKIA)
    cpu = rgba_image(CPU)
    diff = rgba_image(DIFF)
    white = Image.new("RGBA", skia.size, (255, 255, 255, 255))
    skia_over_white = Image.alpha_composite(white, skia)
    if skia.size != EXPECTED_SIZE or cpu.size != EXPECTED_SIZE or diff.size != EXPECTED_SIZE:
        fail("local_metrics called with invalid dimensions")
    return {
        "imageMetrics": {
            "skia": image_metrics(skia, "FOR-327 Skia isolated transparent reference"),
            "cpu": image_metrics(cpu, "FOR-322 Kanvas CPU harness output"),
            "for328Diff": image_metrics(diff, "FOR-328 strict RGBA diff"),
            "skiaCompositedOverWhiteProbe": image_metrics(skia_over_white, "Skia reference composited over opaque white probe"),
        },
        "strictCpuVsSkia": compare_images(skia, cpu, "strict CPU vs Skia RGBA"),
        "cpuVsSkiaCompositedOverWhiteProbe": compare_images(skia_over_white, cpu, "CPU vs Skia composited over opaque white probe"),
        "samplePoints": sample_points(skia, cpu, diff, skia_over_white),
    }


def classify_cause(metrics: dict[str, Any], sources: dict[str, Any], invalid_reasons: list[str]) -> dict[str, Any]:
    if invalid_reasons:
        return {
            "decision": DECISION_INVALID,
            "primaryCause": "CAUSE_INPUT_INVALID",
            "classification": "input-invalid",
            "confidence": "high",
            "rationale": "one or more prerequisite artifacts or source contracts are invalid",
            "residualCause": None,
            "nextAction": "regenerate or repair FOR-327/FOR-322/FOR-328 prerequisites before auditing CPU raster",
        }
    skia_metrics = metrics["imageMetrics"]["skia"]
    cpu_metrics = metrics["imageMetrics"]["cpu"]
    strict = metrics["strictCpuVsSkia"]
    normalized = metrics["cpuVsSkiaCompositedOverWhiteProbe"]
    source_divergence = sources["comparison"]["divergentSourceFacts"]
    background_mismatch = (
        skia_metrics["transparentPixels"] > 0
        and cpu_metrics["opaquePixels"] == cpu_metrics["totalPixels"]
        and strict["differentPixels"] == strict["totalPixels"]
        and any("transparent" in item for item in source_divergence)
        and any("white background" in item for item in source_divergence)
    )
    if background_mismatch:
        return {
            "decision": DECISION_IDENTIFIED,
            "primaryCause": CAUSE_BACKGROUND_ALPHA,
            "classification": "cause-identified-with-normalized-background-residual",
            "confidence": "high",
            "rationale": (
                "The Skia isolated reference clears to transparent and contains transparent background pixels, "
                "while the CPU harness renders the same GM through a default opaque white GM background. "
                "This explains the all-pixel FOR-328 alpha/background failure."
            ),
            "residualCause": CAUSE_RESIDUAL_TRACE if normalized["differentPixels"] > 0 else None,
            "residualRationale": (
                "Compositing the Skia reference over opaque white improves the comparison but still leaves "
                f"{normalized['differentPixels']} differing stroke pixels; those residuals need a normalized-background "
                "CPU trace before any renderer correction."
                if normalized["differentPixels"] > 0
                else "After white-background normalization the CPU and Skia pixels match exactly."
            ),
            "nextAction": (
                "First align the selected-cell reference/background contract or generate an apples-to-apples transparent "
                "CPU capture. Then trace the remaining normalized-background stroke/color residuals before changing CPU raster."
            ),
        }
    return {
        "decision": DECISION_TRACE,
        "primaryCause": "CAUSE_UNRESOLVED_REQUIRES_CPU_TRACE",
        "classification": "cause-unresolved",
        "confidence": "medium",
        "rationale": "existing artifacts do not isolate one source or pixel-domain cause",
        "residualCause": "CAUSE_UNRESOLVED_REQUIRES_CPU_TRACE",
        "nextAction": "add CPU raster trace for background, coverage, blend, premul, and PNG encode boundaries",
    }


def build_artifact() -> dict[str, Any]:
    validated, invalid_reasons = validate_inputs()
    source_invalid: list[str] = []
    sources = source_audit(source_invalid)
    invalid_reasons.extend(source_invalid)
    metrics = None if invalid_reasons else local_metrics()
    cause = classify_cause(metrics or {}, sources, invalid_reasons)
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": cause["decision"],
        "allowedDecisions": [DECISION_IDENTIFIED, DECISION_TRACE, DECISION_INVALID],
        "sceneId": SCENE_ID,
        "selectedCell": SELECTED_CELL_EXPECTED,
        "follows": validated["follows"],
        "inputs": validated["inputs"],
        "inputValidation": {
            "valid": not invalid_reasons,
            "expectedDimensions": EXPECTED_DIMENSIONS,
            "expectedHashSource": "FOR-328 comparison.hashes regenerated by its validator",
            "invalidReasons": invalid_reasons,
        },
        "sourceComparison": sources,
        "localMetrics": metrics,
        "causeAssessment": cause,
        "preservedContracts": {
            "auditScope": "selected-cell-only",
            "fullGmSubstitutionAccepted": False,
            "fullGmCropAccepted": False,
            "fullGmScoreAccepted": False,
            "productionRendererChanged": False,
            "cpuRendererFixed": False,
            "gpuRendered": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
            "scenePromotionChanged": False,
            "fidelityScoreCounted": False,
        },
        "validation": [
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


def table_rows_for_samples(samples: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{name}` | `{x},{y}` | `{skia}` | `{cpu}` | `{white}` | `{delta}` |".format(
            name=sample["name"],
            x=sample["x"],
            y=sample["y"],
            skia=sample["skiaRgba"],
            cpu=sample["cpuRgba"],
            white=sample["skiaCompositedOverWhiteRgba"],
            delta=sample["cpuMinusSkiaOverWhiteAbsDelta"],
        )
        for sample in samples
    )


def write_report(data: dict[str, Any]) -> None:
    cause = data["causeAssessment"]
    validation_lines = "\n".join(f"- `{command}`" for command in data["validation"])
    invalid_lines = "\n".join(f"- {reason}" for reason in data["inputValidation"]["invalidReasons"]) or "- none"
    contracts = data["preservedContracts"]
    sources = data["sourceComparison"]
    inputs = data["inputs"]
    metrics = data.get("localMetrics")

    if metrics is None:
        metrics_section = "Input validation failed; no pixel metrics were computed."
        samples = ""
    else:
        image_metrics_map = metrics["imageMetrics"]
        strict = metrics["strictCpuVsSkia"]
        normalized = metrics["cpuVsSkiaCompositedOverWhiteProbe"]
        samples = table_rows_for_samples(metrics["samplePoints"])
        metrics_section = f"""## Local Pixel Metrics

| Image | Transparent | Non-transparent | Opaque | Partial alpha | Non-transparent bbox | Top RGBA |
|---|---:|---:|---:|---:|---|---|
| Skia | `{image_metrics_map["skia"]["transparentPixels"]}` | `{image_metrics_map["skia"]["nonTransparentPixels"]}` | `{image_metrics_map["skia"]["opaquePixels"]}` | `{image_metrics_map["skia"]["partialAlphaPixels"]}` | `{md_value(image_metrics_map["skia"]["nonTransparentBoundingBox"])}` | `{md_value(image_metrics_map["skia"]["topRgba"][:4])}` |
| CPU Kanvas | `{image_metrics_map["cpu"]["transparentPixels"]}` | `{image_metrics_map["cpu"]["nonTransparentPixels"]}` | `{image_metrics_map["cpu"]["opaquePixels"]}` | `{image_metrics_map["cpu"]["partialAlphaPixels"]}` | `{md_value(image_metrics_map["cpu"]["nonTransparentBoundingBox"])}` | `{md_value(image_metrics_map["cpu"]["topRgba"][:4])}` |
| FOR-328 diff | `{image_metrics_map["for328Diff"]["transparentPixels"]}` | `{image_metrics_map["for328Diff"]["nonTransparentPixels"]}` | `{image_metrics_map["for328Diff"]["opaquePixels"]}` | `{image_metrics_map["for328Diff"]["partialAlphaPixels"]}` | `{md_value(image_metrics_map["for328Diff"]["nonTransparentBoundingBox"])}` | `{md_value(image_metrics_map["for328Diff"]["topRgba"][:4])}` |

Strict CPU-vs-Skia comparison:

| Field | Value |
|---|---|
| different pixels | `{strict["differentPixels"]}` / `{strict["totalPixels"]}` |
| matching pixels | `{strict["matchingPixels"]}` |
| similarity percent | `{strict["similarityPercent"]}` |
| max delta by channel | `{md_value(strict["maxDeltaByChannel"])}` |
| sum abs delta total | `{strict["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(strict["differentPixelBoundingBox"])}` |

Skia-over-white probe compared to CPU:

| Field | Value |
|---|---|
| different pixels | `{normalized["differentPixels"]}` / `{normalized["totalPixels"]}` |
| matching pixels | `{normalized["matchingPixels"]}` |
| similarity percent | `{normalized["similarityPercent"]}` |
| max delta by channel | `{md_value(normalized["maxDeltaByChannel"])}` |
| sum abs delta total | `{normalized["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(normalized["differentPixelBoundingBox"])}` |

## Samples

| Sample | XY | Skia RGBA | CPU RGBA | Skia over white RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|
{samples}
"""

    report = f"""# FOR-329 CircularArcsStrokeButt Selected-Cell CPU Raster Audit

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-329 audits the FOR-328 selected-cell mismatch and does not modify any CPU
renderer, GPU path, WGSL, threshold, fallback, Kadre integration, scene support
status, or fidelity score. The primary cause candidate is
`{cause["primaryCause"]}`.

Rationale: {cause["rationale"]}

Residual: `{cause.get("residualCause")}`. {cause.get("residualRationale", "")}

Recommended next action: {cause["nextAction"]}

## Inputs

| Input | Path | Dimensions | SHA-256 | Expected SHA-256 source |
|---|---|---|---|---|
| Skia | `{inputs["skia"]["path"]}` | `{md_value(inputs["skia"]["dimensions"])}` | `{inputs["skia"]["sha256"]}` | `{inputs["skia"]["expectedSha256FromFor328"]}` |
| CPU Kanvas | `{inputs["cpu"]["path"]}` | `{md_value(inputs["cpu"]["dimensions"])}` | `{inputs["cpu"]["sha256"]}` | `{inputs["cpu"]["expectedSha256FromFor328"]}` |
| FOR-328 diff | `{inputs["diff"]["path"]}` | `{md_value(inputs["diff"]["dimensions"])}` | `{inputs["diff"]["sha256"]}` | `{inputs["diff"]["expectedSha256FromFor328"]}` |

Input validation valid: `{data["inputValidation"]["valid"]}`

Invalid reasons:

{invalid_lines}

{metrics_section}

## Source Comparison

| Topic | Skia FOR-326/FOR-327 | CPU FOR-322 |
|---|---|---|
| surface | `{sources["skia"]["surface"]}` | `{sources["cpu"]["surface"]}` |
| clear / background | `{sources["skia"]["clearOrBackground"]}` | `{sources["cpu"]["clearOrBackground"]}` |
| arc bounds | `{sources["skia"]["arcRectDeviceLTRB"]}` | `{sources["cpu"]["arcRectDeviceLTRB"]}` |
| local coordinates | `{sources["skia"]["localCoordinates"]}` | `{sources["cpu"]["localCoordinates"]}` |
| colors / alpha | `{md_value(sources["skia"]["colors"])}` | `{md_value(sources["cpu"]["colors"])}` |
| anti-aliasing | `{sources["skia"]["antiAlias"]}` | `{sources["cpu"]["antiAlias"]}` |
| stroke width | `{sources["skia"]["strokeWidth"]}` | `{sources["cpu"]["strokeWidth"]}` |
| cap butt | `{sources["skia"]["strokeCap"]}` | `{sources["cpu"]["strokeCap"]}` |
| arc order | `{md_value(sources["skia"]["drawArcOrder"])}` | `{md_value(sources["cpu"]["drawArcOrder"])}` |
| color conversion / PNG encoding | `{sources["skia"]["pngEncoding"]}` | `{sources["cpu"]["pngEncoding"]}` |

Matching source facts:

{chr(10).join(f"- {item}" for item in sources["comparison"]["matchingSourceFacts"])}

Divergent source facts:

{chr(10).join(f"- {item}" for item in sources["comparison"]["divergentSourceFacts"])}

Not supported as the primary cause by source comparison:

{chr(10).join(f"- {item}" for item in sources["comparison"]["notSourceSupportedAsPrimaryCause"])}

## Conclusion

The FOR-328 all-pixel diff is not primarily explained by arc order, bounds,
stroke placement, butt cap, or local coordinates: those facts match after the
CPU harness translation is applied. The auditable primary mismatch is the
background/alpha contract and premultiplication boundary: Skia clears a premul
sRGB surface to transparent, whereas the CPU harness renders through
`TestUtils.runGmTest` into an F16 bitmap prefilled with the GM default white
background.

The Skia-over-white probe is diagnostic only and is not counted as a fidelity
score. Its remaining stroke/color differences keep a residual CPU trace risk
after the background contract is normalized.

## Preserved Contracts

| Field | Value |
|---|---|
| audit scope | `{contracts["auditScope"]}` |
| full-GM substitution accepted | `{contracts["fullGmSubstitutionAccepted"]}` |
| full-GM crop accepted | `{contracts["fullGmCropAccepted"]}` |
| full-GM score accepted | `{contracts["fullGmScoreAccepted"]}` |
| production renderer changed | `{contracts["productionRendererChanged"]}` |
| CPU renderer fixed | `{contracts["cpuRendererFixed"]}` |
| GPU rendered | `{contracts["gpuRendered"]}` |
| WGSL changed | `{contracts["wgslChanged"]}` |
| threshold changed | `{contracts["thresholdChanged"]}` |
| fallback policy changed | `{contracts["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{contracts["kadreNativeDependencyAdded"]}` |
| scene promotion changed | `{contracts["scenePromotionChanged"]}` |
| fidelity score counted | `{contracts["fidelityScoreCounted"]}` |

## Validation

{validation_lines}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("decision") not in data.get("allowedDecisions", []):
        fail("artifact decision is not allowed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    assert_cell(data.get("selectedCell"), "artifact selectedCell")
    input_validation = data.get("inputValidation")
    if not isinstance(input_validation, dict):
        fail("artifact missing inputValidation")
    if data.get("decision") == DECISION_INVALID:
        if input_validation.get("valid") is not False:
            fail("invalid decision requires invalid inputValidation")
        if data.get("localMetrics") is not None:
            fail("invalid decision must not include local metrics")
    else:
        if input_validation.get("valid") is not True:
            fail("non-invalid decision requires valid inputValidation")
        metrics = data.get("localMetrics")
        if not isinstance(metrics, dict):
            fail("non-invalid decision requires local metrics")
        strict = metrics.get("strictCpuVsSkia", {})
        normalized = metrics.get("cpuVsSkiaCompositedOverWhiteProbe", {})
        if strict.get("differentPixels") != 6400:
            fail("strict audit must preserve FOR-328 all-pixel difference")
        if normalized.get("differentPixels", 6400) >= strict.get("differentPixels", 0):
            fail("white-background probe must improve the strict diff")
    cause = data.get("causeAssessment")
    if not isinstance(cause, dict):
        fail("artifact missing causeAssessment")
    if data.get("decision") == DECISION_IDENTIFIED and cause.get("primaryCause") != CAUSE_BACKGROUND_ALPHA:
        fail("identified decision must name the background/alpha cause")
    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    for key in [
        "fullGmSubstitutionAccepted",
        "fullGmCropAccepted",
        "fullGmScoreAccepted",
        "productionRendererChanged",
        "cpuRendererFixed",
        "gpuRendered",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
        "scenePromotionChanged",
        "fidelityScoreCounted",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        data["decision"],
        "background",
        "alpha",
        "premul",
        "premultiplication",
        "arc order",
        "bounds",
        "stroke placement",
        "butt cap",
        "local coordinates",
        "color conversion",
        "PNG encoding",
        CAUSE_BACKGROUND_ALPHA if data["decision"] == DECISION_IDENTIFIED else data["decision"],
        "Skia-over-white probe",
        "does not modify",
        "fidelity score",
        "Validation",
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
        r"\bscene promotion changed\s+\|\s+`?True",
        r"\bfidelity score counted\s+\|\s+`?True",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, flags=re.IGNORECASE):
            fail(f"report contains unsafe support language matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    validate_artifact_shape(load_json(ARTIFACT))
    write_report(data)
    validate_report(data)
    print(data["decision"])


if __name__ == "__main__":
    main()
