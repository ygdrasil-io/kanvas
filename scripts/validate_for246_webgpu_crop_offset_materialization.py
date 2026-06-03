#!/usr/bin/env python3
"""Validate FOR-246 WebGPU Crop(input = Offset) materialization evidence."""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path
from typing import Any

from PIL import Image


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
STRICT_THRESHOLD = 99.95
EXPECTED_GPU_SIMILARITY = 98.44
EXPECTED_CPU_SIMILARITY = 84.88
EXPECTED_GPU_MATCHING_PIXELS = 126000
EXPECTED_CPU_MATCHING_PIXELS = 108644
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
SELECTED_ROUTE = "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite"
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts"
    / SCENE_ID
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-03-for-246-webgpu-crop-offset-materialization.md"
)
WEBGPU_DEVICE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
LAYER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/layer_composite.wgsl"
GM_SOURCE = PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/SimpleOffsetImageFilterGM.kt"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-246 validation failed: {message}")


def load_json(relative_path: str) -> Any:
    path = PROJECT_ROOT / relative_path
    if not path.is_file():
        fail(f"missing JSON file: {relative_path}")
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


def require_number(owner: dict[str, Any], field: str) -> float:
    actual = owner.get(field)
    if not isinstance(actual, (int, float)):
        fail(f"missing numeric field `{field}`")
    return float(actual)


def require_int(owner: dict[str, Any], field: str, expected: int) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"`{field}` expected `{expected}`, got `{actual}`")


def require_metric_exact(name: str, actual: float, expected: float) -> None:
    if round(actual, 2) != expected:
        fail(f"{name}: expected {expected:.2f}%, got {actual:.2f}%")


def require_file_text(path: Path, needles: list[str]) -> None:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


def load_image(name: str) -> Image.Image:
    path = ARTIFACT_ROOT / f"{name}.png"
    if not path.is_file():
        fail(f"missing image artifact: {path.relative_to(PROJECT_ROOT)}")
    return Image.open(path).convert("RGBA")


def residual_points(
    reference: Image.Image,
    actual: Image.Image,
    origin: tuple[int, int],
    threshold: int,
) -> list[tuple[int, int, tuple[int, int, int, int], tuple[int, int, int, int]]]:
    ox, oy = origin
    points: list[tuple[int, int, tuple[int, int, int, int], tuple[int, int, int, int]]] = []
    for y in range(oy, min(oy + 80, reference.height)):
        for x in range(ox, min(ox + 80, reference.width)):
            ref = reference.getpixel((x, y))
            got = actual.getpixel((x, y))
            if max(abs(got[i] - ref[i]) for i in range(4)) > threshold:
                points.append((x, y, ref, got))
    return points


def require_residual_block(
    name: str,
    points: list[tuple[int, int, tuple[int, int, int, int], tuple[int, int, int, int]]],
    origin: tuple[int, int],
    expected_local_bbox: tuple[int, int, int, int],
    expected_count: int,
    expected_ref_dominant: tuple[int, int, int, int],
    expected_actual_dominant: tuple[int, int, int, int],
) -> None:
    if len(points) != expected_count:
        fail(f"{name}: expected {expected_count} residual pixels, got {len(points)}")
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    ox, oy = origin
    bbox = (min(xs) - ox, min(ys) - oy, max(xs) - ox, max(ys) - oy)
    if bbox != expected_local_bbox:
        fail(f"{name}: expected local bbox {expected_local_bbox}, got {bbox}")
    ref_dominant = Counter(p[2] for p in points).most_common(1)[0][0]
    actual_dominant = Counter(p[3] for p in points).most_common(1)[0][0]
    if ref_dominant != expected_ref_dominant:
        fail(f"{name}: expected Skia dominant {expected_ref_dominant}, got {ref_dominant}")
    if actual_dominant != expected_actual_dominant:
        fail(f"{name}: expected actual dominant {expected_actual_dominant}, got {actual_dominant}")


def require_pixel(
    name: str,
    image: Image.Image,
    xy: tuple[int, int],
    expected: tuple[int, int, int, int],
) -> None:
    actual = image.getpixel(xy)
    if actual != expected:
        fail(f"{name}: pixel {xy} expected {expected}, got {actual}")


def main() -> None:
    results = load_json("reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    tags = set(scene.get("tags", []))
    if "risk.fidelity-gap" not in tags or "risk.none" in tags:
        fail("selected scene must remain a fidelity-gap diagnostic")

    gpu = scene.get("gpu")
    cpu = scene.get("cpu")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("generated scene must include CPU and GPU evidence")

    gpu_similarity = require_number(gpu, "similarity")
    cpu_similarity = require_number(cpu, "similarity")
    require_metric_exact("GPU similarity", gpu_similarity, EXPECTED_GPU_SIMILARITY)
    require_metric_exact("CPU similarity", cpu_similarity, EXPECTED_CPU_SIMILARITY)
    if gpu_similarity >= STRICT_THRESHOLD or cpu_similarity >= STRICT_THRESHOLD:
        fail("FOR-246 must not claim strict promotion")

    stats = scene.get("stats")
    gpu_stats = gpu.get("stats")
    cpu_stats = cpu.get("stats")
    if not isinstance(stats, dict) or not isinstance(gpu_stats, dict) or not isinstance(cpu_stats, dict):
        fail("selected scene must include aggregate, GPU, and CPU stats")
    require_int(stats, "matchingPixels", EXPECTED_GPU_MATCHING_PIXELS)
    require_int(gpu_stats, "matchingPixels", EXPECTED_GPU_MATCHING_PIXELS)
    require_int(cpu_stats, "matchingPixels", EXPECTED_CPU_MATCHING_PIXELS)

    gpu_route = gpu.get("route")
    if not isinstance(gpu_route, dict):
        fail("missing GPU route diagnostics")
    require_text(gpu_route, "fallbackReason", "none")
    require_text(gpu_route, "selectedRoute", SELECTED_ROUTE)

    route_gpu = load_json(
        f"reports/wgsl-pipeline/scenes/generated/artifacts/{SCENE_ID}/route-gpu.json"
    )
    require_text(route_gpu, "selectedRoute", SELECTED_ROUTE)
    require_text(route_gpu, "intermediateTexture", "SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch")
    require_text(route_gpu, "fallbackReason", "none")

    prepass = load_json(
        f"reports/wgsl-pipeline/scenes/generated/artifacts/{SCENE_ID}/route-prepass.json"
    )
    require_text(prepass, "fallbackReason", "none")
    require_text(prepass, "unsupportedReasonRemoved", FALLBACK_REASON)
    require_text(prepass, "sampleRemap", "scratchPixel(p) samples childSource(p - offset)")

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict) or not isinstance(refusal_gpu.get("route"), dict):
        fail("refusal row must keep GPU route diagnostics")
    require_text(refusal_gpu["route"], "fallbackReason", FALLBACK_REASON)

    require_file_text(
        WEBGPU_DEVICE,
        [
            "resolveCropNonNullOffsetPrePassPlan(paint)",
            "dstOriginX = cropNonNullOffsetPrePass.offsetDx",
            "dstOriginY = cropNonNullOffsetPrePass.offsetDy",
            "imageFilterPacked = cropPacked",
            "shiftedOriginX = originX + sdx",
            "shiftedOriginY = originY + sdy",
        ],
    )
    require_file_text(
        LAYER_SHADER,
        [
            "var layer_px = dst_px - origin_px;",
            "let tx = tile_axis(layer_px.x, rl, rr, mode);",
            "let ty = tile_axis(layer_px.y, rt, rb, mode);",
            "sampled = textureLoad(layer_texture, layer_px, 0);",
        ],
    )
    require_file_text(
        GM_SOURCE,
        [
            "doDraw(c, r, SkImageFilters.Offset(20f, 20f, null), clipR = clipR)",
            "doDraw(c, r, SkImageFilters.Offset(40f, 0f, null, SkRect.Make(cr2)), cr2, r2)",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "`scratchPixel(p)` reads `childSource(p - offset)`",
            "origin=(340,120), layerExtent=(80,40), offset=(40,0), crop=(40,0,80,40)",
            "No arbitrary image-filter DAG compiler is added",
        ],
    )

    skia = load_image("skia")
    gpu_img = load_image("gpu")
    cpu_img = load_image("cpu")

    row1_gpu = residual_points(skia, gpu_img, origin=(540, 40), threshold=1)
    require_residual_block(
        "row1 GPU clip == dst",
        row1_gpu,
        origin=(540, 40),
        expected_local_bbox=(40, 40, 59, 59),
        expected_count=400,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )
    row2_gpu = residual_points(skia, gpu_img, origin=(340, 120), threshold=1)
    require_residual_block(
        "row2 GPU crop == clip == dst",
        row2_gpu,
        origin=(340, 120),
        expected_local_bbox=(40, 0, 79, 39),
        expected_count=1600,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )
    row1_cpu = residual_points(skia, cpu_img, origin=(540, 40), threshold=50)
    require_residual_block(
        "row1 CPU clip == dst",
        row1_cpu,
        origin=(540, 40),
        expected_local_bbox=(40, 40, 59, 59),
        expected_count=400,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )
    row2_cpu = residual_points(skia, cpu_img, origin=(340, 120), threshold=50)
    require_residual_block(
        "row2 CPU crop == clip == dst",
        row2_cpu,
        origin=(340, 120),
        expected_local_bbox=(40, 0, 79, 39),
        expected_count=1600,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )

    require_pixel("row1 Skia representative", skia, (585, 85), (221, 153, 145, 255))
    require_pixel("row1 GPU representative", gpu_img, (585, 85), (255, 255, 255, 255))
    require_pixel("row1 CPU representative", cpu_img, (585, 85), (255, 255, 255, 255))
    require_pixel("row2 Skia representative", skia, (385, 125), (221, 153, 145, 255))
    require_pixel("row2 GPU representative", gpu_img, (385, 125), (255, 255, 255, 255))
    require_pixel("row2 CPU representative", cpu_img, (385, 125), (255, 255, 255, 255))

    print(
        "FOR-246 WebGPU crop/offset materialization diagnostic OK: "
        f"cpu={cpu_similarity:.2f}% gpu={gpu_similarity:.2f}% "
        f"strict={STRICT_THRESHOLD:.2f}% route={SELECTED_ROUTE}"
    )


if __name__ == "__main__":
    main()
