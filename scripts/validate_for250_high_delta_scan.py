#!/usr/bin/env python3
"""Validate FOR-250 high-delta SimpleOffset residual scan evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROBE_NAME = "high-delta-scan-for250.json"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts"
    / SCENE_ID
)
STATIC_ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / SCENE_ID
)
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
ROUTE_PREPASS = ARTIFACT_ROOT / "route-prepass.json"
STATIC_ROUTE_PREPASS = STATIC_ARTIFACT_ROOT / "route-prepass.json"
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-250-high-delta-scan.md"

EXPECTED_CELL_COUNTS = {
    "row1-col0-no-filter": (1600, 1),
    "row1-col1-offset-no-crop": (1600, 1),
    "row1-col2-offset-crop-src": (517, 1),
    "row1-col3-offset-clip-src": (400, 1),
    "row1-col4-offset-crop-20x20": (76, 1),
    "row1-col5-offset-clip-dst": (517, 1),
    "row2-col0-crop-clip-src": (0, 0),
    "row2-col1-crop-src-clip-dst": (312, 1),
    "row2-col2-crop-dst-clip-src": (156, 1),
    "row2-col3-crop-clip-dst": (1444, 1),
    "outside-simple-offset-cells": (0, 0),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-250 validation failed: {message}")


def load_json(path: Path) -> Any:
    if not path.is_file():
        fail(f"missing JSON file: {path.relative_to(PROJECT_ROOT)}")
    return json.loads(path.read_text())


def find_scene(results: dict[str, Any], scene_id: str) -> dict[str, Any]:
    for scene in results.get("scenes", []):
        if scene.get("id") == scene_id:
            return scene
    fail(f"missing generated scene `{scene_id}`")


def require_text(owner: dict[str, Any], field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_array(owner: dict[str, Any], field: str, expected: list[int]) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected {expected}, got {actual}")


def require_file_text(path: Path, needles: list[str]) -> str:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")
    return text


def validate_route(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "for247ScratchProbe", "scratch-probe-for247.json")
    require_text(route_prepass, "for248FinalCropCompositeProbe", "final-crop-composite-probe-for248.json")
    require_text(route_prepass, "for249ReferenceGpuResidualProbe", "reference-gpu-residual-window-for249.json")
    require_text(route_prepass, "for250HighDeltaScanProbe", PROBE_NAME)
    require_text(
        route_prepass,
        "for250ProbeResult",
        "no pixel exceeds maxChannelDelta>8; all non-identical pixels are "
        "byte-level maxChannelDelta=1, concentrated in row1-col0, row1-col1, "
        "and row2-col3",
    )


def validate_pixel(pixel: dict[str, Any]) -> None:
    if pixel.get("maxChannelDelta") != 1:
        fail(f"pixel {pixel.get('xy')} maxChannelDelta expected 1, got {pixel.get('maxChannelDelta')}")
    if pixel.get("withinTolerance8") is not True:
        fail(f"pixel {pixel.get('xy')} must stay within tolerance 8")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "referenceBackend", "skia-upstream")
    require_text(probe, "sceneId", SCENE_ID)
    require_text(probe, "linear", "FOR-250")
    require_text(probe, "probe", "high-delta-scene-scan")
    require_text(probe, "selectedRoute", SELECTED_ROUTE)
    require_text(probe, "fallbackReason", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_array(probe, "imageSize", [640, 200])

    threshold = probe.get("threshold")
    if not isinstance(threshold, dict) or threshold.get("value") != 0 or threshold.get("operator") != ">":
        fail(f"threshold expected maxChannelDelta > 0, got {threshold}")
    strict = probe.get("strictHighDeltaThreshold")
    if (
        not isinstance(strict, dict)
        or strict.get("value") != 8
        or strict.get("operator") != ">"
        or strict.get("totalPixelsAboveThreshold") != 0
    ):
        fail(f"strict high-delta threshold expected >8 with zero pixels, got {strict}")
    if probe.get("totalPixelsAboveThreshold") != 6622:
        fail(f"total non-identical pixels expected 6622, got {probe.get('totalPixelsAboveThreshold')}")
    if probe.get("maxChannelDelta") != 1:
        fail(f"maxChannelDelta expected 1, got {probe.get('maxChannelDelta')}")
    if probe.get("topPixelLimit") != 20:
        fail(f"topPixelLimit expected 20, got {probe.get('topPixelLimit')}")

    top_pixels = probe.get("topPixels")
    if not isinstance(top_pixels, list) or len(top_pixels) != 20:
        fail("topPixels must contain exactly 20 entries")
    first = top_pixels[0]
    if not isinstance(first, dict) or first.get("cellId") != "row1-col0-no-filter":
        fail(f"first top pixel expected in row1-col0-no-filter, got {first}")
    pixel = first.get("pixel")
    if not isinstance(pixel, dict):
        fail("first top pixel must contain pixel object")
    require_array(pixel, "xy", [40, 40])
    require_array(pixel, "deltaRgba", [1, 0, 1, 0])
    validate_pixel(pixel)
    for entry in top_pixels:
        if not isinstance(entry, dict) or not isinstance(entry.get("pixel"), dict):
            fail("each topPixels entry must contain a pixel object")
        validate_pixel(entry["pixel"])

    aggregates = probe.get("cellAggregates")
    if not isinstance(aggregates, list) or len(aggregates) != len(EXPECTED_CELL_COUNTS):
        fail("cellAggregates must contain the ten SimpleOffset cells plus outside fallback")
    by_id = {aggregate.get("id"): aggregate for aggregate in aggregates if isinstance(aggregate, dict)}
    if set(by_id) != set(EXPECTED_CELL_COUNTS):
        fail(f"cell aggregate ids changed: {sorted(by_id)}")
    for cell_id, (expected_count, expected_max) in EXPECTED_CELL_COUNTS.items():
        aggregate = by_id[cell_id]
        if aggregate.get("pixelsAboveThreshold") != expected_count:
            fail(
                f"{cell_id} pixelsAboveThreshold expected {expected_count}, "
                f"got {aggregate.get('pixelsAboveThreshold')}"
            )
        if aggregate.get("maxChannelDelta") != expected_max:
            fail(f"{cell_id} maxChannelDelta expected {expected_max}, got {aggregate.get('maxChannelDelta')}")

    dominant = sorted(
        (
            (aggregate["id"], aggregate["pixelsAboveThreshold"])
            for aggregate in aggregates
            if isinstance(aggregate, dict)
        ),
        key=lambda item: (-item[1], item[0]),
    )[:3]
    if dominant != [
        ("row1-col0-no-filter", 1600),
        ("row1-col1-offset-no-crop", 1600),
        ("row2-col3-crop-clip-dst", 1444),
    ]:
        fail(f"dominant residual cells changed: {dominant}")


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-250 probe JSON differ")

    results = load_json(PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    gpu = scene.get("gpu")
    if not isinstance(gpu, dict) or not isinstance(gpu.get("route"), dict):
        fail("selected scene must include GPU route diagnostics")
    require_text(gpu["route"], "selectedRoute", SELECTED_ROUTE)
    require_text(gpu["route"], "fallbackReason", "none")
    if gpu.get("similarity") != 98.44:
        fail(f"GPU/reference similarity must remain 98.44, got {gpu.get('similarity')}")

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict) or not isinstance(refusal_gpu.get("route"), dict):
        fail("refusal row must keep GPU route diagnostics")
    require_text(refusal_gpu["route"], "fallbackReason", FALLBACK_REASON)

    validate_route(ROUTE_PREPASS)
    validate_route(STATIC_ROUTE_PREPASS)
    validate_probe(PROBE)
    validate_probe(STATIC_PROBE)

    if not WEBGPU_DEVICE.is_file():
        fail(f"missing file: {WEBGPU_DEVICE.relative_to(PROJECT_ROOT)}")
    device_text = WEBGPU_DEVICE.read_text()
    if "kanvas.webgpu.for250" in device_text or "FOR250" in device_text:
        fail("FOR-250 must not add a renderer-side diagnostic property or route")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-250 high delta scan classifies SimpleOffset residual pixels by cell",
            "high-delta-scan-for250.json",
            "STRICT_HIGH_DELTA_THRESHOLD",
            "TestUtils.loadReferenceBitmap",
            "WebGpuSink.draw",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "maxChannelDelta > 8",
            "0 pixels",
            "6622",
            "row2-col3-crop-clip-dst",
            "image-filter.crop-input-nonnull-prepass-required",
        ],
    )

    print(
        "FOR-250 high-delta scan OK: no pixels >8, "
        "6622 non-identical pixels at maxChannelDelta=1; "
        "dominant cells row1-col0,row1-col1,row2-col3"
    )


if __name__ == "__main__":
    main()
