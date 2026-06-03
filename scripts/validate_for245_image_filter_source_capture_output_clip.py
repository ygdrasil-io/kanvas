#!/usr/bin/env python3
"""Validate FOR-245 image-filter source-capture/output-clip evidence."""

from __future__ import annotations

import json
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
STRICT_THRESHOLD = 99.95
EXPECTED_GPU_SIMILARITY = 98.44
EXPECTED_CPU_SIMILARITY = 84.88
EXPECTED_GPU_MATCHING_PIXELS = 126000
EXPECTED_CPU_MATCHING_PIXELS = 108644
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-03-for-245-image-filter-source-capture-output-clip.md"
CANVAS = PROJECT_ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt"
CANVAS_TEST = PROJECT_ROOT / "kanvas-skia/src/test/kotlin/org/skia/core/SkCanvasInternalsTest.kt"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-245 validation failed: {message}")


def load_json(relative_path: str) -> dict:
    path = PROJECT_ROOT / relative_path
    if not path.is_file():
        fail(f"missing JSON file: {relative_path}")
    return json.loads(path.read_text())


def find_scene(results: dict, scene_id: str) -> dict:
    for scene in results.get("scenes", []):
        if scene.get("id") == scene_id:
            return scene
    fail(f"missing generated scene `{scene_id}`")


def require_exact_percent(name: str, actual: object, expected: float) -> None:
    if not isinstance(actual, (int, float)):
        fail(f"{name}: missing numeric value")
    if round(float(actual), 2) != expected:
        fail(f"{name}: expected {expected:.2f}%, got {float(actual):.2f}%")


def require_int(name: str, owner: dict, expected: int) -> None:
    actual = owner.get(name)
    if actual != expected:
        fail(f"{name}: expected {expected}, got {actual}")


def require_text(owner: dict, field: str, expected: str) -> None:
    actual = owner.get(field)
    if actual != expected:
        fail(f"{field}: expected `{expected}`, got `{actual}`")


def require_file_text(path: Path, expected: list[str]) -> None:
    if not path.is_file():
        fail(f"missing file: {path.relative_to(PROJECT_ROOT)}")
    text = path.read_text()
    for needle in expected:
        if needle not in text:
            fail(f"{path.relative_to(PROJECT_ROOT)} missing `{needle}`")


def main() -> None:
    results = load_json("reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    tags = set(scene.get("tags", []))
    if "risk.fidelity-gap" not in tags or "risk.none" in tags:
        fail("selected scene must remain a fidelity-gap diagnostic")

    gpu = scene.get("gpu")
    cpu = scene.get("cpu")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("selected scene must include CPU and GPU evidence")
    require_exact_percent("GPU similarity", gpu.get("similarity"), EXPECTED_GPU_SIMILARITY)
    require_exact_percent("CPU similarity", cpu.get("similarity"), EXPECTED_CPU_SIMILARITY)
    if float(gpu["similarity"]) >= STRICT_THRESHOLD or float(cpu["similarity"]) >= STRICT_THRESHOLD:
        fail("FOR-245 must not claim strict promotion")

    stats = scene.get("stats")
    gpu_stats = gpu.get("stats")
    cpu_stats = cpu.get("stats")
    if not isinstance(stats, dict) or not isinstance(gpu_stats, dict) or not isinstance(cpu_stats, dict):
        fail("selected scene must include aggregate, GPU, and CPU stats")
    require_int("matchingPixels", stats, EXPECTED_GPU_MATCHING_PIXELS)
    require_int("matchingPixels", gpu_stats, EXPECTED_GPU_MATCHING_PIXELS)
    require_int("matchingPixels", cpu_stats, EXPECTED_CPU_MATCHING_PIXELS)

    route = gpu.get("route")
    if not isinstance(route, dict):
        fail("missing GPU route diagnostics")
    require_text(route, "fallbackReason", "none")
    require_text(route, "selectedRoute", "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite")

    prepass = load_json(f"reports/wgsl-pipeline/scenes/generated/artifacts/{SCENE_ID}/route-prepass.json")
    require_text(prepass, "fallbackReason", "none")
    require_text(prepass, "unsupportedReasonRemoved", FALLBACK_REASON)

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict) or not isinstance(refusal_gpu.get("route"), dict):
        fail("refusal row must keep GPU route diagnostics")
    require_text(refusal_gpu["route"], "fallbackReason", FALLBACK_REASON)

    require_file_text(
        CANVAS,
        [
            "saveLayerForImageFilterSourceCapture",
            "captureClipToCurrentClip = false",
            "val captureClip = if (captureClipToCurrentClip) s.clip else s.device.deviceClipBounds()",
        ],
    )
    require_file_text(
        CANVAS_TEST,
        [
            "drawRect imageFilter captures source outside output clip",
            "drawRect crop offset imageFilter captures source before output clip",
        ],
    )
    require_file_text(
        REPORT,
        [
            "KEEP_DIAGNOSTIC",
            "Public `saveLayer(bounds, paint)` behavior remains clipped",
            "GPU/reference similarity | 98.44% | 98.44%",
            "No arbitrary image-filter DAG compiler is added",
        ],
    )

    print(
        "FOR-245 source-capture/output-clip diagnostic OK: "
        f"cpu={float(cpu['similarity']):.2f}% gpu={float(gpu['similarity']):.2f}% "
        f"strict={STRICT_THRESHOLD:.2f}%"
    )


if __name__ == "__main__":
    main()
