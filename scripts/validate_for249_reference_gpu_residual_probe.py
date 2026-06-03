#!/usr/bin/env python3
"""Validate FOR-249 reference/GPU residual window evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
PROBE_NAME = "reference-gpu-residual-window-for249.json"
TARGET_REFERENCE_RGBA = [221, 153, 145, 255]
TARGET_GPU_RGBA = [220, 153, 145, 255]
TARGET_DELTA_RGBA = [1, 0, 0, 0]
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
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-249-reference-gpu-residual-window.md"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-249 validation failed: {message}")


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


def require_bool(owner: dict[str, Any], field: str, expected: bool) -> None:
    actual = owner.get(field)
    if actual is not expected:
        fail(f"`{field}` expected {expected}, got {actual}")


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


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "referenceBackend", "skia-upstream")
    require_text(probe, "sceneId", SCENE_ID)
    require_text(probe, "linear", "FOR-249")
    require_text(probe, "probe", "reference-gpu-residual-window")
    require_text(probe, "targetCell", "crop == clip == dst")
    require_text(probe, "selectedRoute", SELECTED_ROUTE)
    require_text(probe, "fallbackReason", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")

    target = probe.get("targetFragment")
    if not isinstance(target, dict):
        fail("probe must include targetFragment object")
    require_array(target, "xy", [385, 125])
    require_array(target, "referenceRgba", TARGET_REFERENCE_RGBA)
    require_array(target, "gpuRgba", TARGET_GPU_RGBA)
    require_array(target, "deltaRgba", TARGET_DELTA_RGBA)
    if target.get("maxChannelDelta") != 1:
        fail(f"target maxChannelDelta expected 1, got {target.get('maxChannelDelta')}")
    require_bool(target, "withinTolerance8", True)

    window = probe.get("window")
    if not isinstance(window, dict):
        fail("probe must include window object")
    require_array(window, "center", [385, 125])
    require_array(window, "boundsInclusive", [383, 123, 387, 127])
    if window.get("radius") != 2:
        fail(f"window radius expected 2, got {window.get('radius')}")
    if window.get("maxChannelDelta") != 1:
        fail(f"window maxChannelDelta expected 1, got {window.get('maxChannelDelta')}")
    require_bool(window, "allPixelsWithinTolerance8", True)
    pixels = window.get("pixels")
    if not isinstance(pixels, list) or len(pixels) != 25:
        fail("window must contain exactly 25 pixels")
    for pixel in pixels:
        if not isinstance(pixel, dict):
            fail("each window pixel must be an object")
        require_array(pixel, "referenceRgba", TARGET_REFERENCE_RGBA)
        require_array(pixel, "gpuRgba", TARGET_GPU_RGBA)
        require_array(pixel, "deltaRgba", TARGET_DELTA_RGBA)
        if pixel.get("maxChannelDelta") != 1:
            fail(f"window pixel {pixel.get('xy')} maxChannelDelta changed")
        require_bool(pixel, "withinTolerance8", True)

    samples = probe.get("cellSamples")
    if not isinstance(samples, list) or len(samples) != 4:
        fail("cellSamples must contain four named samples")
    names = {sample.get("name") for sample in samples if isinstance(sample, dict)}
    expected_names = {"cellOrigin", "cropStartInside", "targetFragment", "cropEndInside"}
    if names != expected_names:
        fail(f"cellSamples names expected {expected_names}, got {names}")
    if probe.get("maxObservedDelta") != 1:
        fail(f"maxObservedDelta expected 1, got {probe.get('maxObservedDelta')}")


def validate_route(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "for247ScratchProbe", "scratch-probe-for247.json")
    require_text(route_prepass, "for248FinalCropCompositeProbe", "final-crop-composite-probe-for248.json")
    require_text(route_prepass, "for249ReferenceGpuResidualProbe", PROBE_NAME)
    require_text(
        route_prepass,
        "for249ProbeResult",
        "normal WebGPU fragment window around (385,125) matches Skia reference "
        "within maxChannelDelta=1; remaining score residual is outside this "
        "local Crop composite target window",
    )


def main() -> None:
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
    if "kanvas.webgpu.for249" in device_text or "FOR249" in device_text:
        fail("FOR-249 must not add a renderer-side diagnostic property or route")

    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-249 residual window compares reference and GPU around crop clip dst fragment",
            "reference-gpu-residual-window-for249.json",
            "probe.windowMaxChannelDelta <= 1",
            "TestUtils.loadReferenceBitmap",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "normal WebGPU fragment (385,125) -> rgba(220,153,145,255)",
            "local 5x5 maxChannelDelta",
            "No bounded renderer correction is applied",
            "image-filter.crop-input-nonnull-prepass-required",
        ],
    )

    print(
        "FOR-249 reference/GPU residual probe OK: "
        "target (385,125) reference rgba(221,153,145,255), "
        "GPU rgba(220,153,145,255), local maxChannelDelta=1; "
        f"route={SELECTED_ROUTE}"
    )


if __name__ == "__main__":
    main()
