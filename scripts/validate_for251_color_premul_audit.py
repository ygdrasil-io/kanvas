#!/usr/bin/env python3
"""Validate FOR-251 SimpleOffset color/premultiplication residual audit."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROBE_NAME = "color-premul-audit-for251.json"
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-251-color-premul-audit.md"

EXPECTED_GLOBAL_HISTOGRAM = {
    "r": {-1: 5727, 0: 895},
    "g": {0: 6583, 1: 39},
    "b": {-1: 3227, 0: 3044, 1: 351},
    "a": {0: 6622},
}

EXPECTED_DOMINANT_CELLS = {
    "row1-col0-no-filter": {
        "residualPixels": 1600,
        "histogram": {
            "r": {-1: 1600},
            "g": {0: 1600},
            "b": {-1: 1600},
            "a": {0: 1600},
        },
        "topPair": ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0], 1600),
    },
    "row1-col1-offset-no-crop": {
        "residualPixels": 1600,
        "histogram": {
            "r": {-1: 1600},
            "g": {0: 1600},
            "b": {-1: 400, 0: 1200},
            "a": {0: 1600},
        },
        "topPair": ([221, 153, 145, 255], [220, 153, 145, 255], [-1, 0, 0, 0], 1200),
    },
    "row2-col3-crop-clip-dst": {
        "residualPixels": 1444,
        "histogram": {
            "r": {-1: 1444},
            "g": {0: 1444},
            "b": {0: 1444},
            "a": {0: 1444},
        },
        "topPair": ([221, 153, 145, 255], [220, 153, 145, 255], [-1, 0, 0, 0], 1444),
    },
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-251 validation failed: {message}")


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


def normalize_histogram(histogram: dict[str, Any]) -> dict[str, dict[int, int]]:
    normalized: dict[str, dict[int, int]] = {}
    for channel, entries in histogram.items():
        if not isinstance(entries, list):
            fail(f"histogram channel `{channel}` must be a list")
        normalized[channel] = {}
        for entry in entries:
            if not isinstance(entry, dict):
                fail(f"histogram channel `{channel}` entry must be an object")
            normalized[channel][int(entry.get("delta"))] = int(entry.get("count"))
    return normalized


def validate_histogram(actual: dict[str, Any], expected: dict[str, dict[int, int]], label: str) -> None:
    normalized = normalize_histogram(actual)
    if normalized != expected:
        fail(f"{label} signed delta histogram changed: {normalized}")


def validate_route(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "for250HighDeltaScanProbe", "high-delta-scan-for250.json")
    require_text(route_prepass, "for251ColorPremulAuditProbe", PROBE_NAME)
    require_text(
        route_prepass,
        "for251ProbeResult",
        "6622 residual pixels remain RGB-only byte deltas with "
        "alphaDeltaNonZeroPixels=0; dominant cells include row1-col0 "
        "no-filter, so no bounded Crop renderer correction is justified",
    )
    require_text(route_prepass, "supportDecision", "risk.fidelity-gap; no strict promotion")


def validate_color_pair(group: dict[str, Any], expected: tuple[list[int], list[int], list[int], int]) -> None:
    expected_ref, expected_gpu, expected_delta, expected_count = expected
    require_array(group, "referenceRgba", expected_ref)
    require_array(group, "gpuRgba", expected_gpu)
    require_array(group, "signedDeltaRgba", expected_delta)
    if group.get("count") != expected_count:
        fail(f"color pair count expected {expected_count}, got {group.get('count')}")


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "referenceBackend", "skia-upstream")
    require_text(probe, "sceneId", SCENE_ID)
    require_text(probe, "linear", "FOR-251")
    require_text(probe, "probe", "color-premul-byte-residual-audit")
    require_text(probe, "selectedRoute", SELECTED_ROUTE)
    require_text(probe, "fallbackReason", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_array(probe, "imageSize", [640, 200])

    if probe.get("deltaDefinition") != "signed channel delta is GPU minus reference":
        fail("deltaDefinition must remain GPU minus reference")
    if probe.get("totalResidualPixels") != 6622:
        fail(f"totalResidualPixels expected 6622, got {probe.get('totalResidualPixels')}")
    if probe.get("maxChannelDelta") != 1:
        fail(f"maxChannelDelta expected 1, got {probe.get('maxChannelDelta')}")
    if probe.get("alphaDeltaNonZeroPixels") != 0:
        fail(f"alphaDeltaNonZeroPixels expected 0, got {probe.get('alphaDeltaNonZeroPixels')}")
    if probe.get("rgbOnlyResidualPixels") != 6622:
        fail(f"rgbOnlyResidualPixels expected 6622, got {probe.get('rgbOnlyResidualPixels')}")

    validate_histogram(probe.get("signedDeltaHistogram", {}), EXPECTED_GLOBAL_HISTOGRAM, "global")

    top_pairs = probe.get("topColorPairs")
    if not isinstance(top_pairs, list) or len(top_pairs) < 3:
        fail("topColorPairs must contain stable dominant color-pair groups")
    validate_color_pair(top_pairs[0], ([221, 153, 145, 255], [220, 153, 145, 255], [-1, 0, 0, 0], 3005))
    validate_color_pair(top_pairs[1], ([158, 90, 139, 255], [157, 90, 138, 255], [-1, 0, -1, 0], 2722))

    if probe.get("dominantCellIds") != list(EXPECTED_DOMINANT_CELLS):
        fail(f"dominantCellIds changed: {probe.get('dominantCellIds')}")
    aggregates = probe.get("dominantCellAggregates")
    if not isinstance(aggregates, list) or len(aggregates) != len(EXPECTED_DOMINANT_CELLS):
        fail("dominantCellAggregates must contain exactly the FOR-250 dominant cells")
    by_id = {aggregate.get("id"): aggregate for aggregate in aggregates if isinstance(aggregate, dict)}
    if set(by_id) != set(EXPECTED_DOMINANT_CELLS):
        fail(f"dominant cell aggregate ids changed: {sorted(by_id)}")
    for cell_id, expected in EXPECTED_DOMINANT_CELLS.items():
        aggregate = by_id[cell_id]
        if aggregate.get("residualPixels") != expected["residualPixels"]:
            fail(f"{cell_id} residualPixels changed: {aggregate.get('residualPixels')}")
        if aggregate.get("maxChannelDelta") != 1:
            fail(f"{cell_id} maxChannelDelta expected 1, got {aggregate.get('maxChannelDelta')}")
        if aggregate.get("alphaDeltaNonZeroPixels") != 0:
            fail(f"{cell_id} alpha deltas must remain zero")
        if aggregate.get("rgbOnlyResidualPixels") != expected["residualPixels"]:
            fail(f"{cell_id} rgbOnlyResidualPixels changed: {aggregate.get('rgbOnlyResidualPixels')}")
        validate_histogram(
            aggregate.get("signedDeltaHistogram", {}),
            expected["histogram"],  # type: ignore[arg-type]
            cell_id,
        )
        cell_pairs = aggregate.get("topColorPairs")
        if not isinstance(cell_pairs, list) or not cell_pairs:
            fail(f"{cell_id} must include top color pairs")
        validate_color_pair(cell_pairs[0], expected["topPair"])  # type: ignore[arg-type]


def main() -> None:
    generated_probe = load_json(PROBE)
    static_probe = load_json(STATIC_PROBE)
    if generated_probe != static_probe:
        fail("generated and static FOR-251 probe JSON differ")

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

    device_text = require_file_text(WEBGPU_DEVICE, ["cropNonNullOffsetChildPrePassScratch"])
    if "kanvas.webgpu.for251" in device_text or "FOR251" in device_text:
        fail("FOR-251 must not add a renderer-side diagnostic property or route")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-251 color premul audit classifies SimpleOffset byte residual",
            "color-premul-audit-for251.json",
            "signed channel delta is GPU minus reference",
            "TestUtils.loadReferenceBitmap",
            "WebGpuSink.draw",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "alphaDeltaNonZeroPixels",
            "RGB-only",
            "row1-col0-no-filter",
            "image-filter.crop-input-nonnull-prepass-required",
            "98.44%",
        ],
    )

    print(
        "FOR-251 color/premul audit OK: 6622 RGB-only byte residual pixels, "
        "alpha deltas zero, dominant cells include row1-col0,row1-col1,row2-col3"
    )


if __name__ == "__main__":
    main()
