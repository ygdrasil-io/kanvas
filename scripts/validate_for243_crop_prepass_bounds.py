#!/usr/bin/env python3
"""Validate FOR-243 crop image-filter pre-pass bounds diagnostics."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCENE_ID = "crop-image-filter-nonnull-prepass"
REFUSAL_ID = "image-filter-crop-nonnull-prepass-required"
STRICT_THRESHOLD = 99.95
FALLBACK_REASON = "image-filter.crop-input-nonnull-prepass-required"


def fail(message: str) -> None:
    raise SystemExit(f"FOR-243 validation failed: {message}")


def load_json(relative_path: str) -> Any:
    path = PROJECT_ROOT / relative_path
    if not path.is_file():
        fail(f"missing JSON file: {relative_path}")
    return json.loads(path.read_text())


def require_file_contains(relative_path: str, expected: str) -> None:
    path = PROJECT_ROOT / relative_path
    if not path.is_file():
        fail(f"missing file: {relative_path}")
    text = path.read_text()
    if expected not in text:
        fail(f"{relative_path} does not contain `{expected}`")


def find_scene(results: dict[str, Any], scene_id: str) -> dict[str, Any]:
    for scene in results.get("scenes", []):
        if scene.get("id") == scene_id:
            return scene
    fail(f"missing generated scene `{scene_id}`")


def number(owner: dict[str, Any], field: str) -> float:
    value = owner.get(field)
    if not isinstance(value, (int, float)):
        fail(f"missing numeric field `{field}`")
    return float(value)


def require_route_text(owner: dict[str, Any], field: str, expected: str) -> None:
    value = owner.get(field)
    if value != expected:
        fail(f"`{field}` expected `{expected}`, got `{value}`")


def main() -> None:
    results = load_json("reports/wgsl-pipeline/scenes/generated/results.json")
    scene = find_scene(results, SCENE_ID)
    tags = set(scene.get("tags", []))

    gpu = scene.get("gpu")
    cpu = scene.get("cpu")
    if not isinstance(gpu, dict) or not isinstance(cpu, dict):
        fail("generated scene must include CPU and GPU evidence")

    gpu_similarity = number(gpu, "similarity")
    cpu_similarity = number(cpu, "similarity")
    if gpu_similarity < 98.43:
        fail(f"GPU route regressed below the FOR-243 floor: {gpu_similarity:.2f}%")
    if gpu_similarity >= STRICT_THRESHOLD or cpu_similarity >= STRICT_THRESHOLD:
        fail("FOR-243 validator expects a below-strict diagnostic, not a promotion")
    if "risk.fidelity-gap" not in tags:
        fail("below-strict crop pre-pass row must carry `risk.fidelity-gap`")
    if "risk.none" in tags:
        fail("below-strict crop pre-pass row must not carry `risk.none`")

    route = gpu.get("route")
    if not isinstance(route, dict):
        fail("GPU route diagnostics missing")
    require_route_text(route, "fallbackReason", "none")
    require_route_text(
        route,
        "selectedRoute",
        "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
    )

    prepass = load_json(
        "reports/wgsl-pipeline/scenes/generated/artifacts/"
        f"{SCENE_ID}/route-prepass.json"
    )
    require_route_text(prepass, "fallbackReason", "none")
    require_route_text(prepass, "unsupportedReasonRemoved", FALLBACK_REASON)

    refusal = find_scene(results, REFUSAL_ID)
    refusal_gpu = refusal.get("gpu")
    if not isinstance(refusal_gpu, dict):
        fail("refusal row must keep GPU diagnostics")
    refusal_route = refusal_gpu.get("route")
    if not isinstance(refusal_route, dict):
        fail("refusal row route diagnostics missing")
    require_route_text(refusal_route, "fallbackReason", FALLBACK_REASON)

    require_file_contains(
        "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt",
        "layerBounds.join(filteredBounds)",
    )
    require_file_contains(
        "kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageFilters.kt",
        "override fun computeFastBounds(src: SkRect): SkRect = rect",
    )
    require_file_contains(
        "reports/wgsl-pipeline/2026-06-03-for-243-crop-image-filter-prepass-bounds.md",
        "KEEP_DIAGNOSTIC",
    )

    print(
        "FOR-243 crop pre-pass bounds diagnostic OK: "
        f"cpu={cpu_similarity:.2f}% gpu={gpu_similarity:.2f}% strict={STRICT_THRESHOLD:.2f}%"
    )


if __name__ == "__main__":
    main()
