#!/usr/bin/env python3
"""Validate D50 lot 1 policy-only Dashboard visibility rows."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-visibility.json"
BUILD_GRADLE = ROOT / "build.gradle.kts"
DASHBOARD = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"
GENERATED = ROOT / "build/reports/wgsl-pipeline-d50-lot1-generated/data/d50-lot1-generated-scenes.json"

EXPECTED_ROWS = {
    "skia-gm-drawminibitmaprect": "bitmap.drawminibitmaprect.row-specific-artifacts-required",
    "skia-gm-image": "image.imagegm.row-specific-artifacts-required",
    "skia-gm-imagesource": "image.imagesource.row-specific-artifacts-required",
    "skia-gm-offsetimagefilter": "image-filter.offset.row-specific-artifacts-required",
    "skia-gm-pathfill": "path.pathfill.row-specific-artifacts-required",
}
EXPECTED_PROJECTED = {
    "total": 98,
    "pass": 70,
    "expected-unsupported": 28,
    "inventoryDerived": 50,
}


def load_json(path: Path) -> Any:
    if not path.is_file():
        raise AssertionError(f"missing JSON file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def scenes_from(path: Path) -> list[dict[str, Any]]:
    root = load_json(path)
    require(isinstance(root, dict), f"{path.relative_to(ROOT)} root must be an object")
    scenes = root.get("scenes")
    require(isinstance(scenes, list), f"{path.relative_to(ROOT)} must contain scenes[]")
    typed = []
    for index, scene in enumerate(scenes):
        require(isinstance(scene, dict), f"{path.relative_to(ROOT)} scenes[{index}] must be an object")
        typed.append(scene)
    return typed


def validate_gradle_wiring() -> None:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    markers = [
        "pipelineD50Lot1DashboardVisibilityPack",
        "reports/wgsl-pipeline-d50-lot1-generated",
        "d50-lot1-dashboard-visibility.json",
        "data/d50-lot1-generated-scenes.json",
        "skia-gm-drawminibitmaprect\" to \"bitmap.drawminibitmaprect.row-specific-artifacts-required",
        "skia-gm-image\" to \"image.imagegm.row-specific-artifacts-required",
        "skia-gm-imagesource\" to \"image.imagesource.row-specific-artifacts-required",
        "skia-gm-offsetimagefilter\" to \"image-filter.offset.row-specific-artifacts-required",
        "skia-gm-pathfill\" to \"path.pathfill.row-specific-artifacts-required",
    ]
    for marker in markers:
        require(marker in text, f"build.gradle.kts missing marker: {marker}")


def validate_contract() -> None:
    root = load_json(CONTRACT)
    require(root.get("generatedBy") == "pipelineD50Lot1DashboardVisibilityPack", "contract generatedBy mismatch")
    require(root.get("outputManifest") == "data/d50-lot1-generated-scenes.json", "contract outputManifest mismatch")
    scenes = scenes_from(CONTRACT)
    require([scene.get("id") for scene in scenes] == list(EXPECTED_ROWS), "contract row order mismatch")
    for scene in scenes:
        scene_id = scene["id"]
        fallback = EXPECTED_ROWS[scene_id]
        require(scene.get("inventoryId") == scene_id, f"{scene_id}: inventoryId must match id")
        require(scene.get("status") == "expected-unsupported", f"{scene_id}: status must be expected-unsupported")
        require(scene.get("fallbackReason") == fallback, f"{scene_id}: fallback reason mismatch")
        require(scene.get("allowArtifactOnlyBase") is True, f"{scene_id}: allowArtifactOnlyBase must be true")
        require(scene.get("policyOnlyArtifacts") is True, f"{scene_id}: policyOnlyArtifacts must be true")
        require(scene.get("referenceKind") == "skia-upstream", f"{scene_id}: referenceKind mismatch")
        tags = set(scene.get("tags", []))
        for tag in ("source.generated", "source.inventory", "route.gpu.expected-unsupported", "risk.expected-unsupported"):
            require(tag in tags, f"{scene_id}: missing tag {tag}")
        require("does not claim" in scene.get("nonClaim", ""), f"{scene_id}: nonClaim must explicitly refuse support")


def validate_generated_projection() -> None:
    generated = scenes_from(GENERATED)
    generated_by_id = {scene["id"]: scene for scene in generated}
    require(set(generated_by_id) == set(EXPECTED_ROWS), f"generated row ids mismatch: {sorted(generated_by_id)}")
    for scene_id, fallback in EXPECTED_ROWS.items():
        scene = generated_by_id[scene_id]
        require(scene.get("status") == "expected-unsupported", f"{scene_id}: generated status mismatch")
        require(scene.get("inventoryId") == scene_id, f"{scene_id}: generated inventoryId mismatch")
        require(scene.get("gpu", {}).get("route", {}).get("fallbackReason") == fallback, f"{scene_id}: generated fallback mismatch")
        require(scene.get("policyArtifact") == f"artifacts/{scene_id}/policy-artifact.json", f"{scene_id}: missing policy artifact")
        require(scene.get("generation", {}).get("derivationTask") == "pipelineD50Lot1DashboardVisibilityPack", f"{scene_id}: derivation task mismatch")

    dashboard = scenes_from(DASHBOARD)
    dashboard_ids = {scene["id"] for scene in dashboard}
    overlap = dashboard_ids.intersection(EXPECTED_ROWS)
    require(not overlap, f"D50 visibility rows already exist before projection: {sorted(overlap)}")
    projected = dashboard + generated
    status_counts = Counter(scene.get("status") for scene in projected)
    inventory_derived = sum(
        1
        for scene in projected
        if scene.get("inventoryId") or "source.inventory" in set(scene.get("tags", []))
    )
    counters = {
        "total": len(projected),
        "pass": status_counts["pass"],
        "expected-unsupported": status_counts["expected-unsupported"],
        "inventoryDerived": inventory_derived,
    }
    require(counters == EXPECTED_PROJECTED, f"projected counters mismatch: {counters} != {EXPECTED_PROJECTED}")


def main() -> int:
    try:
        validate_gradle_wiring()
        validate_contract()
        validate_generated_projection()
    except AssertionError as error:
        print(f"validate_d50_dashboard_visibility: FAIL: {error}", file=sys.stderr)
        return 1
    print("validate_d50_dashboard_visibility: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
