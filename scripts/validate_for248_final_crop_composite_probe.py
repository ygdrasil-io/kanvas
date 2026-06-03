#!/usr/bin/env python3
"""Validate FOR-248 WebGPU final Crop composite probe evidence."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
SCRATCH_RGBA = [202, 59, 19, 255]
FINAL_COMPOSITE_RGBA = [202, 59, 19, 102]
FINAL_FRAGMENT_RGBA = [220, 153, 145, 255]
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
PROBE_NAME = "final-crop-composite-probe-for248.json"
PROBE = ARTIFACT_ROOT / PROBE_NAME
STATIC_PROBE = STATIC_ARTIFACT_ROOT / PROBE_NAME
ROUTE_PREPASS = ARTIFACT_ROOT / "route-prepass.json"
STATIC_ROUTE_PREPASS = STATIC_ARTIFACT_ROOT / "route-prepass.json"
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
WEBGPU_TEST = PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleOffsetImageFilterWebGpuTest.kt"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-248-final-crop-composite-probe.md"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-248 validation failed: {message}")


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


def require_occurrences(path: Path, needle: str, expected: int) -> None:
    text = require_file_text(path, [needle])
    actual = text.count(needle)
    if actual != expected:
        fail(
            f"{path.relative_to(PROJECT_ROOT)} expected {expected} occurrences "
            f"of `{needle}`, got {actual}"
        )


def validate_probe(path: Path) -> None:
    probe = load_json(path)
    require_text(probe, "backend", "WebGPU")
    require_text(probe, "sceneId", SCENE_ID)
    require_text(probe, "linear", "FOR-248")
    require_text(probe, "targetCell", "crop == clip == dst")
    require_text(probe, "selectedRoute", SELECTED_ROUTE)
    require_text(probe, "fallbackReason", "none")
    require_text(probe, "supportDecision", "KEEP_DIAGNOSTIC")
    require_bool(probe, "finalCompositeReadsScratchPixel45x5", True)
    require_bool(probe, "finalFragmentObservedWhite", False)

    final_fragment = probe.get("finalFragment")
    scratch = probe.get("scratchPixel")
    final_sample = probe.get("finalCropCompositeShaderSample")
    if not isinstance(final_fragment, dict):
        fail("probe must include finalFragment object")
    if not isinstance(scratch, dict):
        fail("probe must include scratchPixel object")
    if not isinstance(final_sample, dict):
        fail("probe must include finalCropCompositeShaderSample object")

    require_array(final_fragment, "xy", [385, 125])
    require_array(final_fragment, "dstOrigin", [340, 120])
    require_array(final_fragment, "preCropLayerPixel", [45, 5])
    require_array(final_fragment, "postCropScratchPixel", [45, 5])
    require_array(final_fragment, "rgba", FINAL_FRAGMENT_RGBA)
    require_bool(final_fragment, "insideFinalScissor", True)
    require_bool(final_fragment, "insideCropRect", True)
    require_bool(final_fragment, "decalTransparentAfterCropRemap", False)

    require_array(scratch, "xy", [45, 5])
    require_array(scratch, "sentinel", [2, 0])
    require_array(scratch, "rgba", SCRATCH_RGBA)

    require_array(final_sample, "sentinel", [3, 0])
    require_array(final_sample, "forcedDstOrigin", [-42, -5])
    require_array(final_sample, "preCropLayerPixel", [45, 5])
    require_array(final_sample, "postCropScratchPixel", [45, 5])
    require_array(final_sample, "rgba", FINAL_COMPOSITE_RGBA)


def validate_route(path: Path) -> None:
    route_prepass = load_json(path)
    require_text(route_prepass, "fallbackReason", "none")
    require_text(route_prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(route_prepass, "sampleRemap", "scratchPixel(p) samples childSource(p - offset)")
    require_text(route_prepass, "for247ScratchProbe", "scratch-probe-for247.json")
    require_text(route_prepass, "for248FinalCropCompositeProbe", PROBE_NAME)
    require_text(
        route_prepass,
        "for248ProbeResult",
        "final Crop composite sentinel reads scratchPixel(45,5) as "
        "rgba(202,59,19,102); normal final GM fragment observes "
        "rgba(220,153,145,255)",
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

    require_file_text(
        WEBGPU_DEVICE,
        [
            "kanvas.webgpu.for248.finalCropCompositeProbe",
            "shouldProbeFor248FinalCropComposite",
            "isSelectedCropOffsetPrePassProbeCase",
            "dstOriginX = -42",
            "dstOriginY = -5",
            "scissor = intArrayOf(3, 0, 1, 1)",
            "imageFilterPacked = cropPacked",
        ],
    )
    require_occurrences(
        WEBGPU_DEVICE,
        "\"kanvas.webgpu.for248.finalCropCompositeProbe\"",
        1,
    )
    require_occurrences(
        WEBGPU_DEVICE,
        "private fun shouldProbeFor248FinalCropComposite(",
        1,
    )
    require_file_text(
        WEBGPU_TEST,
        [
            "FOR-248 final crop composite maps target fragment to scratch pixel probe",
            "final Crop composite remap must preserve scratchPixel(45,5) RGB",
            "final-crop-composite-probe-for248.json",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "final Crop composite sentinel -> rgba(202,59,19,102)",
            "normal final GM fragment       -> rgba(220,153,145,255)",
            "No bounded final-composite correction is applied",
            "image-filter.crop-input-nonnull-prepass-required",
        ],
    )

    print(
        "FOR-248 final Crop composite probe OK: "
        "sentinel reads scratchPixel(45,5) as rgba(202,59,19,102); "
        "normal fragment rgba(220,153,145,255); "
        f"route={SELECTED_ROUTE}"
    )


if __name__ == "__main__":
    main()
