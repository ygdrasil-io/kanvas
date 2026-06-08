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
CONTRACT = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json"
BUILD_GRADLE = ROOT / "build.gradle.kts"
DASHBOARD = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"
GENERATED = ROOT / "build/reports/wgsl-pipeline-d50-lot1-generated/data/d50-gm-dashboard-generated-scenes.json"

EXPECTED_ROWS = {
    "skia-gm-image": "image.imagegm.row-specific-artifacts-required",
    "skia-gm-imagesource": "image.imagesource.row-specific-artifacts-required",
    "skia-gm-offsetimagefilter": "image-filter.offset.row-specific-artifacts-required",
    "skia-gm-pathfill": "path.pathfill.row-specific-artifacts-required",
    "skia-gm-rectpolystroke": "coverage.rectpolystroke.row-specific-artifacts-required",
    "skia-gm-imagemakewithfilter": "image-filter.imagemakewithfilter.row-specific-artifacts-required",
    "skia-gm-runtimeintrinsics": "runtime-effect.runtimeintrinsics.row-specific-artifacts-required",
    "skia-gm-textblobtransforms": "font.textblobtransforms.row-specific-artifacts-required",
    "skia-gm-runtimeimagefilter": "runtime-effect.runtimeimagefilter.row-specific-artifacts-required",
    "skia-gm-shadertext3": "font.shadertext3.row-specific-artifacts-required",
    "skia-gm-gradients2ptconical": "gradient.2ptconical.row-specific-artifacts-required",
}
FORBIDDEN_EXPECTED_UNSUPPORTED_ROWS = {"skia-gm-drawminibitmaprect"}
DASH_HAIRLINE_ROWS = {
    "skia-gm-dashcubics",
    "skia-gm-dashing",
    "skia-gm-hairlines",
    "skia-gm-hairmodes",
    "skia-gm-scaledstrokes",
    "skia-gm-strokedlines",
    "skia-gm-strokerect",
    "skia-gm-strokerects",
    "skia-gm-thinstrokedrects",
}
EXPECTED_D50_PROJECTED = {
    "total": 105,
    "pass": 71,
    "expected-unsupported": 34,
    "inventoryDerived": 57,
}
EXPECTED_D53_PROJECTED = {
    "total": 114,
    "pass": 71,
    "expected-unsupported": 43,
    "inventoryDerived": 66,
}
EXPECTED_REMAPS = {
    "skia-gm-runtimeintrinsics": "diagnostic-only",
    "skia-gm-textblobtransforms": "diagnostic-only",
    "skia-gm-runtimeimagefilter": "excluded",
    "skia-gm-shadertext3": "excluded",
    "skia-gm-gradients2ptconical": "excluded",
}
EXPECTED_EXCLUSION_REASONS = {
    "skia-gm-runtimeimagefilter": "Runtime image filters need registered descriptors before promotion; arbitrary SkSL/runtime image-filter input remains refused.",
    "skia-gm-shadertext3": "Combines text/glyph dependency and runtime shader behavior; not a clean D50 dashboard-expansion candidate.",
    "skia-gm-gradients2ptconical": "Two-point conical gradients remain outside the existing bounded linear/sweep gradient support envelope.",
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
        "d50-gm-dashboard-visibility.json",
        "data/d50-gm-dashboard-generated-scenes.json",
        "skia-gm-image\" to \"image.imagegm.row-specific-artifacts-required",
        "skia-gm-imagesource\" to \"image.imagesource.row-specific-artifacts-required",
        "skia-gm-offsetimagefilter\" to \"image-filter.offset.row-specific-artifacts-required",
        "skia-gm-pathfill\" to \"path.pathfill.row-specific-artifacts-required",
        "skia-gm-rectpolystroke\" to \"coverage.rectpolystroke.row-specific-artifacts-required",
        "skia-gm-imagemakewithfilter\" to \"image-filter.imagemakewithfilter.row-specific-artifacts-required",
        "skia-gm-runtimeintrinsics\" to \"runtime-effect.runtimeintrinsics.row-specific-artifacts-required",
        "skia-gm-textblobtransforms\" to \"font.textblobtransforms.row-specific-artifacts-required",
        "skia-gm-runtimeimagefilter\" to \"runtime-effect.runtimeimagefilter.row-specific-artifacts-required",
        "skia-gm-shadertext3\" to \"font.shadertext3.row-specific-artifacts-required",
        "skia-gm-gradients2ptconical\" to \"gradient.2ptconical.row-specific-artifacts-required",
    ]
    for marker in markers:
        require(marker in text, f"build.gradle.kts missing marker: {marker}")
    require(
        'skia-gm-drawminibitmaprect" to "bitmap.drawminibitmaprect.row-specific-artifacts-required' not in text,
        "build.gradle.kts must not allow an active skia-gm-drawminibitmaprect expected-unsupported row after D52-5",
    )


def validate_contract() -> None:
    root = load_json(CONTRACT)
    require(root.get("generatedBy") == "pipelineD50Lot1DashboardVisibilityPack", "contract generatedBy mismatch")
    require(root.get("outputManifest") == "data/d50-gm-dashboard-generated-scenes.json", "contract outputManifest mismatch")
    require(root.get("selectedCandidateCount") == len(EXPECTED_ROWS), "contract selectedCandidateCount mismatch")
    d52 = root.get("d52Interaction")
    require(isinstance(d52, dict), "contract must document D52-5 interaction")
    require(d52.get("excludedRow") == "skia-gm-drawminibitmaprect", "contract D52 excluded row mismatch")
    require(d52.get("activePassRow") == "d52-drawminibitmaprect", "contract D52 active pass row mismatch")
    scenes = scenes_from(CONTRACT)
    require([scene.get("id") for scene in scenes] == list(EXPECTED_ROWS), "contract row order mismatch")
    require(
        not FORBIDDEN_EXPECTED_UNSUPPORTED_ROWS.intersection(scene.get("id") for scene in scenes),
        "contract must not contain skia-gm-drawminibitmaprect expected-unsupported after D52-5",
    )
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
        remap = scene.get("visibilityStatusRemap")
        if scene_id in EXPECTED_REMAPS:
            require(isinstance(remap, dict), f"{scene_id}: visibilityStatusRemap required")
            require(remap.get("fromInventoryClassification") == EXPECTED_REMAPS[scene_id], f"{scene_id}: remap source mismatch")
            require(remap.get("toDashboardStatus") == "expected-unsupported", f"{scene_id}: remap target mismatch")
            require("counters do not move" in remap.get("reason", ""), f"{scene_id}: remap reason must protect support counters")
            if scene_id in EXPECTED_EXCLUSION_REASONS:
                require(
                    remap.get("fromExclusionReason") == EXPECTED_EXCLUSION_REASONS[scene_id],
                    f"{scene_id}: remap exclusion reason mismatch",
                )
                gpu_details = scene.get("gpuRouteDetails")
                require(isinstance(gpu_details, dict), f"{scene_id}: gpuRouteDetails required for excluded remap")
                require(
                    gpu_details.get("visibilityStatusRemap") == remap,
                    f"{scene_id}: gpuRouteDetails.visibilityStatusRemap must mirror contract remap",
                )
        else:
            require(remap is None, f"{scene_id}: unexpected visibilityStatusRemap")


def validate_generated_projection() -> None:
    generated = scenes_from(GENERATED)
    generated_by_id = {scene["id"]: scene for scene in generated}
    require(set(generated_by_id) == set(EXPECTED_ROWS), f"generated row ids mismatch: {sorted(generated_by_id)}")
    require(
        not FORBIDDEN_EXPECTED_UNSUPPORTED_ROWS.intersection(generated_by_id),
        "generated D50 rows must not include skia-gm-drawminibitmaprect after D52-5",
    )
    for scene_id, fallback in EXPECTED_ROWS.items():
        scene = generated_by_id[scene_id]
        require(scene.get("status") == "expected-unsupported", f"{scene_id}: generated status mismatch")
        require(scene.get("inventoryId") == scene_id, f"{scene_id}: generated inventoryId mismatch")
        require(scene.get("gpu", {}).get("route", {}).get("fallbackReason") == fallback, f"{scene_id}: generated fallback mismatch")
        require(scene.get("policyArtifact") == f"artifacts/{scene_id}/policy-artifact.json", f"{scene_id}: missing policy artifact")
        require(scene.get("generation", {}).get("derivationTask") == "pipelineD50Lot1DashboardVisibilityPack", f"{scene_id}: derivation task mismatch")
        if scene_id in EXPECTED_EXCLUSION_REASONS:
            gpu_route_path = GENERATED.parents[1] / "artifacts" / scene_id / "route-gpu.json"
            gpu_route = load_json(gpu_route_path)
            remap = gpu_route.get("visibilityStatusRemap")
            require(isinstance(remap, dict), f"{scene_id}: generated GPU route remap required")
            require(remap.get("fromInventoryClassification") == "excluded", f"{scene_id}: generated remap source mismatch")
            require(remap.get("fromExclusionReason") == EXPECTED_EXCLUSION_REASONS[scene_id], f"{scene_id}: generated exclusion reason mismatch")

    dashboard = scenes_from(DASHBOARD)
    dashboard_ids = {scene["id"] for scene in dashboard}
    forbidden = sorted(dashboard_ids.intersection(FORBIDDEN_EXPECTED_UNSUPPORTED_ROWS))
    require(not forbidden, f"dashboard must not contain contradictory expected-unsupported rows: {forbidden}")
    dashboard_d50_rows = dashboard_ids.intersection(EXPECTED_ROWS)
    if dashboard_d50_rows:
        require(dashboard_d50_rows == set(EXPECTED_ROWS), f"dashboard D50 row set mismatch: {sorted(dashboard_d50_rows)}")
        projected = dashboard
        expected = EXPECTED_D53_PROJECTED if dashboard_ids.intersection(DASH_HAIRLINE_ROWS) else EXPECTED_D50_PROJECTED
    else:
        projected = dashboard + generated
        expected = EXPECTED_D50_PROJECTED
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
    require(counters == expected, f"projected counters mismatch: {counters} != {expected}")


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
