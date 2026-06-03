#!/usr/bin/env python3
"""Validate FOR-244 crop image-filter color/source residual diagnostics."""

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
ARTIFACT_ROOT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/generated/artifacts"
    / SCENE_ID
)
REPORT = (
    "reports/wgsl-pipeline/"
    "2026-06-03-for-244-crop-image-filter-color-source.md"
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-244 validation failed: {message}")


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
    value = owner.get(field)
    if value != expected:
        fail(f"`{field}` expected `{expected}`, got `{value}`")


def require_number(owner: dict[str, Any], field: str) -> float:
    value = owner.get(field)
    if not isinstance(value, (int, float)):
        fail(f"missing numeric field `{field}`")
    return float(value)


def require_int(owner: dict[str, Any], field: str) -> int:
    value = owner.get(field)
    if not isinstance(value, int):
        fail(f"missing integer field `{field}`")
    return value


def require_metric_exact(name: str, actual: float, expected: float) -> None:
    if round(actual, 2) != expected:
        fail(f"{name}: expected {expected:.2f}%, got {actual:.2f}%")


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
        fail(f"{name}: expected GPU dominant {expected_actual_dominant}, got {actual_dominant}")


def main() -> None:
    results = load_json("reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    tags = set(scene.get("tags", []))

    gpu = scene.get("gpu")
    cpu = scene.get("cpu")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("generated scene must include CPU and GPU evidence")

    gpu_similarity = require_number(gpu, "similarity")
    cpu_similarity = require_number(cpu, "similarity")
    require_metric_exact("GPU similarity", gpu_similarity, EXPECTED_GPU_SIMILARITY)
    require_metric_exact("CPU similarity", cpu_similarity, EXPECTED_CPU_SIMILARITY)
    if gpu_similarity >= STRICT_THRESHOLD or cpu_similarity >= STRICT_THRESHOLD:
        fail("FOR-244 expects a diagnostic below strict promotion")
    if "risk.fidelity-gap" not in tags or "risk.none" in tags:
        fail("crop pre-pass row must remain a fidelity-gap diagnostic")

    stats = scene.get("stats")
    if not isinstance(stats, dict):
        fail("generated scene must include aggregate stats")
    if require_int(stats, "matchingPixels") != EXPECTED_GPU_MATCHING_PIXELS:
        fail(
            "aggregate GPU matching pixel count changed: "
            f"expected {EXPECTED_GPU_MATCHING_PIXELS}, got {stats.get('matchingPixels')}"
        )

    gpu_stats = gpu.get("stats")
    cpu_stats = cpu.get("stats")
    if not isinstance(gpu_stats, dict) or not isinstance(cpu_stats, dict):
        fail("generated scene must include CPU/GPU pixel stats")
    if require_int(gpu_stats, "matchingPixels") != EXPECTED_GPU_MATCHING_PIXELS:
        fail(
            "GPU matching pixel count changed: "
            f"expected {EXPECTED_GPU_MATCHING_PIXELS}, got {gpu_stats.get('matchingPixels')}"
        )
    if require_int(cpu_stats, "matchingPixels") != EXPECTED_CPU_MATCHING_PIXELS:
        fail(
            "CPU matching pixel count changed: "
            f"expected {EXPECTED_CPU_MATCHING_PIXELS}, got {cpu_stats.get('matchingPixels')}"
        )

    gpu_route = gpu.get("route")
    if not isinstance(gpu_route, dict):
        fail("missing GPU route diagnostics")
    require_text(gpu_route, "fallbackReason", "none")
    require_text(
        gpu_route,
        "selectedRoute",
        "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
    )

    prepass = load_json(
        "reports/wgsl-pipeline/scenes/generated/artifacts/"
        f"{SCENE_ID}/route-prepass.json"
    )
    require_text(prepass, "fallbackReason", "none")
    require_text(prepass, "unsupportedReasonRemoved", FALLBACK_REASON)

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict):
        fail("refusal row must keep GPU diagnostics")
    refusal_route = refusal_gpu.get("route")
    if not isinstance(refusal_route, dict):
        fail("refusal row route diagnostics missing")
    require_text(refusal_route, "fallbackReason", FALLBACK_REASON)

    skia = load_image("skia")
    gpu_img = load_image("gpu")
    cpu_img = load_image("cpu")

    r1 = residual_points(skia, gpu_img, origin=(540, 40), threshold=1)
    require_residual_block(
        "row1 clip == dst",
        r1,
        origin=(540, 40),
        expected_local_bbox=(40, 40, 59, 59),
        expected_count=400,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )

    r2 = residual_points(skia, gpu_img, origin=(340, 120), threshold=1)
    require_residual_block(
        "row2 crop == clip == dst",
        r2,
        origin=(340, 120),
        expected_local_bbox=(40, 0, 79, 39),
        expected_count=1600,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )

    cpu_r2 = residual_points(skia, cpu_img, origin=(340, 120), threshold=50)
    cpu_r1 = residual_points(skia, cpu_img, origin=(540, 40), threshold=50)
    require_residual_block(
        "row1 CPU clip == dst",
        cpu_r1,
        origin=(540, 40),
        expected_local_bbox=(40, 40, 59, 59),
        expected_count=400,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )
    require_residual_block(
        "row2 CPU crop == clip == dst",
        cpu_r2,
        origin=(340, 120),
        expected_local_bbox=(40, 0, 79, 39),
        expected_count=1600,
        expected_ref_dominant=(221, 153, 145, 255),
        expected_actual_dominant=(255, 255, 255, 255),
    )

    report_path = PROJECT_ROOT / REPORT
    if not report_path.is_file():
        fail(f"missing report `{REPORT}`")
    report_text = report_path.read_text()
    for expected in [
        "KEEP_DIAGNOSTIC",
        "clip is constraining capture/materialisation",
        "No arbitrary image-filter DAG compiler is added",
    ]:
        if expected not in report_text:
            fail(f"report missing `{expected}`")

    print(
        "FOR-244 crop pre-pass color/source diagnostic OK: "
        f"cpu={cpu_similarity:.2f}% gpu={gpu_similarity:.2f}% strict={STRICT_THRESHOLD:.2f}%"
    )


if __name__ == "__main__":
    main()
