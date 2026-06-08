#!/usr/bin/env python3
"""Validate grouped dash/hairline/stroke policy-only Dashboard visibility rows."""

from __future__ import annotations

import json
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"
BUILD_GRADLE = ROOT / "build.gradle.kts"
DASHBOARD = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"
GENERATED = ROOT / "build/reports/wgsl-pipeline-dash-hairline-stroke-generated/data/dash-hairline-stroke-generated-scenes.json"

EXPECTED_ROWS = {
    "skia-gm-dashcubics": "coverage.dash-cubic.row-specific-artifacts-required",
    "skia-gm-dashing": "coverage.dashing.row-specific-artifacts-required",
    "skia-gm-hairlines": "coverage.hairline.row-specific-artifacts-required",
    "skia-gm-hairmodes": "coverage.hairmode.row-specific-artifacts-required",
    "skia-gm-scaledstrokes": "coverage.scaled-stroke.row-specific-artifacts-required",
    "skia-gm-strokedlines": "coverage.stroked-lines.row-specific-artifacts-required",
    "skia-gm-strokerect": "coverage.stroke-rect.row-specific-artifacts-required",
    "skia-gm-strokerects": "coverage.stroke-rects.row-specific-artifacts-required",
    "skia-gm-thinstrokedrects": "coverage.thin-stroked-rects.row-specific-artifacts-required",
}
EXPECTED_PROJECTED = {
    "baselineRowsAfterD50D52Reconcile": 105,
    "addedRows": 9,
    "total": 114,
    "pass": 71,
    "expectedUnsupported": 43,
    "inventoryDerived": 66,
    "excludedD50VisibilityRows": ["skia-gm-drawminibitmaprect"],
    "d52ActivePassRow": "d52-drawminibitmaprect",
}
EXPECTED_FINAL_DASHBOARD = {
    "total": 114,
    "pass": 71,
    "expected-unsupported": 43,
    "inventoryDerived": 66,
}
EXPECTED_CHANGED = {
    "build.gradle.kts",
    "reports/wgsl-pipeline/2026-06-08-d53-dashboard-visibility-reconcile.md",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
    "scripts/validate_d50_dashboard_visibility.py",
    "scripts/validate_dash_hairline_stroke_dashboard_visibility.py",
}
FORBIDDEN_CHANGED = {
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
}
FORBIDDEN_ACTIVE_ROWS = {"skia-gm-drawminibitmaprect"}


def fail(message: str) -> None:
    raise AssertionError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    return json.loads(path.read_text(encoding="utf-8"))


def scenes_from(path: Path) -> list[dict[str, Any]]:
    root = load_json(path)
    require(isinstance(root, dict), f"{rel(path)} root must be an object")
    scenes = root.get("scenes")
    require(isinstance(scenes, list), f"{rel(path)} must contain scenes[]")
    typed: list[dict[str, Any]] = []
    for index, scene in enumerate(scenes):
        require(isinstance(scene, dict), f"{rel(path)} scenes[{index}] must be an object")
        typed.append(scene)
    return typed


def git_changed_paths() -> set[str]:
    status = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    changed: set[str] = set()
    for line in status.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def validate_scope() -> None:
    changed = git_changed_paths()
    missing = sorted(path for path in EXPECTED_CHANGED if path not in changed and not (ROOT / path).is_file())
    require(not missing, f"missing expected files: {missing}")
    forbidden = sorted(path for path in changed if path in FORBIDDEN_CHANGED)
    require(not forbidden, f"forbidden D50/dashboard contract changes: {forbidden}")
    unexpected_tracked = sorted(
        path
        for path in changed
        if path not in EXPECTED_CHANGED
        and not path.startswith("gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawMiniBitmapRectSceneCaptureTest.kt")
        and not path.startswith("reports/wgsl-pipeline/2026-06-06-d52-2-drawminibitmaprect-artifact-harness.md")
        and not path.startswith("reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect")
        and path != "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json"
        and path != "scripts/validate_d52_drawminibitmaprect_artifact_harness.py"
    )
    require(not unexpected_tracked, f"unexpected non-D52 changed files: {unexpected_tracked}")


def validate_gradle_wiring() -> None:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    markers = [
        "pipelineDashHairlineStrokeDashboardVisibilityPack",
        "reports/wgsl-pipeline-dash-hairline-stroke-generated",
        "dash-hairline-stroke-gm-dashboard-visibility.json",
        "data/dash-hairline-stroke-generated-scenes.json",
        "dashHairlineStrokeGeneratedDir",
    ]
    markers.extend(f'"{row}" to "{fallback}' for row, fallback in EXPECTED_ROWS.items())
    for marker in markers:
        require(marker in text, f"build.gradle.kts missing marker: {marker}")


def validate_contract() -> None:
    root = load_json(CONTRACT)
    require(root.get("generatedBy") == "pipelineDashHairlineStrokeDashboardVisibilityPack", "generatedBy mismatch")
    require(root.get("outputManifest") == "data/dash-hairline-stroke-generated-scenes.json", "outputManifest mismatch")
    require(root.get("selectedCandidateCount") == len(EXPECTED_ROWS), "selectedCandidateCount mismatch")
    require(root.get("projectedDashboardCounters") == EXPECTED_PROJECTED, "projected counters mismatch")
    require(root.get("sourceReport") == "reports/upstream-rebaseline/2026-05-25-post-1047.md", "sourceReport mismatch")

    scenes = scenes_from(CONTRACT)
    require([scene.get("id") for scene in scenes] == list(EXPECTED_ROWS), "contract row order mismatch")
    for scene in scenes:
        scene_id = scene["id"]
        fallback = EXPECTED_ROWS[scene_id]
        require(scene.get("inventoryId") == scene_id, f"{scene_id}: inventoryId must match id")
        require(scene.get("status") == "expected-unsupported", f"{scene_id}: status must be expected-unsupported")
        require(scene.get("fallbackReason") == fallback, f"{scene_id}: fallback mismatch")
        require(scene.get("allowArtifactOnlyBase") is True, f"{scene_id}: allowArtifactOnlyBase must be true")
        require(scene.get("policyOnlyArtifacts") is True, f"{scene_id}: policyOnlyArtifacts must be true")
        require(scene.get("referenceKind") == "skia-upstream", f"{scene_id}: referenceKind mismatch")
        require("does not claim" in scene.get("nonClaim", ""), f"{scene_id}: nonClaim must refuse support")
        tags = set(scene.get("tags", []))
        for tag in ("source.generated", "source.inventory", "route.gpu.expected-unsupported", "risk.expected-unsupported"):
            require(tag in tags, f"{scene_id}: missing tag {tag}")
        require(
            {"feature.dash", "feature.hairline", "feature.stroke"}.intersection(tags),
            f"{scene_id}: missing dash/hairline/stroke feature tag",
        )


def validate_generated_projection() -> None:
    generated = scenes_from(GENERATED)
    generated_by_id = {scene["id"]: scene for scene in generated}
    require(set(generated_by_id) == set(EXPECTED_ROWS), f"generated row ids mismatch: {sorted(generated_by_id)}")
    status_counts = Counter(scene.get("status") for scene in generated)
    require(status_counts == {"expected-unsupported": len(EXPECTED_ROWS)}, f"generated status counts mismatch: {status_counts}")

    for scene_id, fallback in EXPECTED_ROWS.items():
        scene = generated_by_id[scene_id]
        require(scene.get("inventoryId") == scene_id, f"{scene_id}: generated inventory mismatch")
        require(scene.get("policyArtifact") == f"artifacts/{scene_id}/policy-artifact.json", f"{scene_id}: policy artifact mismatch")
        require(scene.get("generation", {}).get("derivationTask") == "pipelineDashHairlineStrokeDashboardVisibilityPack", f"{scene_id}: derivationTask mismatch")
        require(scene.get("gpu", {}).get("route", {}).get("fallbackReason") == fallback, f"{scene_id}: generated fallback mismatch")
        route_path = GENERATED.parents[1] / "artifacts" / scene_id / "route-gpu.json"
        route = load_json(route_path)
        require(route.get("fallbackReason") == fallback, f"{scene_id}: route-gpu fallback mismatch")
        policy_path = GENERATED.parents[1] / "artifacts" / scene_id / "policy-artifact.json"
        policy = load_json(policy_path)
        require(policy.get("kind") == "policy-only-expected-unsupported", f"{scene_id}: policy artifact kind mismatch")

    if DASHBOARD.is_file():
        dashboard = scenes_from(DASHBOARD)
        dashboard_ids = {scene["id"] for scene in dashboard}
        forbidden = sorted(dashboard_ids.intersection(FORBIDDEN_ACTIVE_ROWS))
        require(not forbidden, f"dashboard must not contain contradictory expected-unsupported rows: {forbidden}")
        d52 = next((scene for scene in dashboard if scene.get("id") == "d52-drawminibitmaprect"), None)
        require(isinstance(d52, dict), "dashboard missing D52 drawminibitmaprect pass row")
        require(d52.get("status") == "pass", "d52-drawminibitmaprect must remain pass")
        require(d52.get("gpu", {}).get("status") == "pass", "d52-drawminibitmaprect GPU status must remain pass")
        require(
            d52.get("gpu", {}).get("route", {}).get("fallbackReason") == "none",
            "d52-drawminibitmaprect pass row must keep gpu.route.fallbackReason=none",
        )

        dashboard_dash_rows = dashboard_ids.intersection(EXPECTED_ROWS)
        if dashboard_dash_rows:
            require(dashboard_dash_rows == set(EXPECTED_ROWS), f"dashboard dash row set mismatch: {sorted(dashboard_dash_rows)}")
            projected = dashboard
        else:
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
        require(counters == EXPECTED_FINAL_DASHBOARD, f"final projected counters mismatch: {counters} != {EXPECTED_FINAL_DASHBOARD}")


def main() -> int:
    try:
        validate_scope()
        validate_gradle_wiring()
        validate_contract()
        validate_generated_projection()
    except AssertionError as error:
        print(f"validate_dash_hairline_stroke_dashboard_visibility: FAIL: {error}", file=sys.stderr)
        return 1
    print("validate_dash_hairline_stroke_dashboard_visibility: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
