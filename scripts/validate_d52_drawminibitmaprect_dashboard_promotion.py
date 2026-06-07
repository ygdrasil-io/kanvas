#!/usr/bin/env python3
"""Validate D52-5 DrawMiniBitmapRect dashboard promotion evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D52-5"
ROW_ID = "skia-gm-drawminibitmaprect"
SCENE_ID = "d52-drawminibitmaprect"
OLD_FALLBACK = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
GPU_THRESHOLD = 99.95
BEFORE_GPU_SIMILARITY = 94.9305
AFTER_GPU_SIMILARITY = 99.9864
AFTER_MATCHING_PIXELS = 1_048_433
TOTAL_PIXELS = 1_048_576
SOURCE_PIPELINE_KEY = "not-promoted-by-D52-2"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-d52-5-drawminibitmaprect-dashboard-promotion.md"
RESULTS = ROOT / "reports/wgsl-pipeline/scenes/generated/results.json"
D52_4_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-4-drawminibitmaprect-draw-image-rect-provenance-boundary.md"
D52_4_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json"
STATS = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/stats.json"
ROUTE_GPU = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-gpu.json"
ROUTE_CPU = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-cpu.json"
DASHBOARD_DATA = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-08-d52-5-drawminibitmaprect-dashboard-promotion.md",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "scripts/validate_d52_drawminibitmaprect_dashboard_promotion.py",
}

FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/src/main/",
    "kanvas-skia/src/main/",
    "render-pipeline/",
    "cpu-raster/",
)


def fail(message: str) -> None:
    raise SystemExit(f"{TICKET} validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def git_changed_paths() -> set[str]:
    commands = (
        ["git", "diff", "--name-only"],
        ["git", "diff", "--cached", "--name-only"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    )
    changed: set[str] = set()
    for cmd in commands:
        result = subprocess.run(cmd, cwd=ROOT, check=True, text=True, capture_output=True)
        changed.update(line.strip() for line in result.stdout.splitlines() if line.strip())

    status = subprocess.run(["git", "status", "--short"], cwd=ROOT, check=True, text=True, capture_output=True)
    for line in status.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_CHANGED_FILES)
    require(not unexpected, f"unexpected changed files: {unexpected}")
    forbidden = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden, f"production or target files changed out of scope: {forbidden}")


def require_source_evidence() -> None:
    stats = load_json(STATS)
    require(stats.get("sceneId") == SCENE_ID, "stats scene mismatch")
    require(stats.get("inventoryId") == ROW_ID, "stats inventory mismatch")
    require(stats.get("status") == "pass", "stats must be pass")
    require(stats.get("fallbackReason") == "none", "stats fallback must be none")
    require(stats.get("supportClaim") is True, "stats supportClaim must be true")
    require(float(stats.get("gpuPromotionThreshold")) == GPU_THRESHOLD, "stats threshold mismatch")
    require(abs(float(stats.get("gpuSimilarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "stats GPU similarity mismatch")
    require(float(stats.get("gpuSimilarity")) >= GPU_THRESHOLD, "stats GPU similarity below threshold")
    require(stats.get("globalThresholdChanged") is False, "global threshold changed")
    require(stats.get("m66EvidenceInherited") is False, "M66 evidence inherited")

    route_gpu = load_json(ROUTE_GPU)
    require(route_gpu.get("sceneId") == SCENE_ID, "GPU route scene mismatch")
    require(route_gpu.get("inventoryId") == ROW_ID, "GPU route inventory mismatch")
    require(route_gpu.get("status") == "pass", "GPU route must be pass")
    require(route_gpu.get("fallbackReason") == "none", "GPU fallback must be none")
    require(route_gpu.get("supportClaim") is True, "GPU supportClaim must be true")
    require(route_gpu.get("pipelineKey") == SOURCE_PIPELINE_KEY, "GPU route pipelineKey mismatch")
    require(abs(float(route_gpu.get("similarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "GPU similarity mismatch")
    require(float(route_gpu.get("threshold")) == GPU_THRESHOLD, "GPU threshold mismatch")
    require(route_gpu.get("m66EvidenceInherited") is False, "GPU route inherited M66")

    route_cpu = load_json(ROUTE_CPU)
    require(route_cpu.get("sceneId") == SCENE_ID, "CPU route scene mismatch")
    require(route_cpu.get("inventoryId") == ROW_ID, "CPU route inventory mismatch")
    require(route_cpu.get("status") == "pass", "CPU route must be pass")
    require(route_cpu.get("fallbackReason") == "none", "CPU fallback must be none")

    evidence = load_json(D52_4_EVIDENCE)
    require(evidence.get("inventoryId") == ROW_ID, "D52-4 inventory mismatch")
    before = evidence.get("measurements", {}).get("before", {})
    after = evidence.get("measurements", {}).get("after", {})
    require(before.get("status") == "expected-unsupported", "D52-4 before status mismatch")
    require(before.get("fallbackReason") == OLD_FALLBACK, "D52-4 before fallback mismatch")
    require(abs(float(before.get("similarity")) - BEFORE_GPU_SIMILARITY) < 0.0001, "D52-4 before similarity mismatch")
    require(after.get("status") == "pass", "D52-4 after status mismatch")
    require(after.get("fallbackReason") == "none", "D52-4 after fallback mismatch")
    require(abs(float(after.get("similarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "D52-4 after similarity mismatch")


def find_row(root: dict[str, Any]) -> dict[str, Any]:
    scenes = root.get("scenes")
    require(isinstance(scenes, list), "generated results must contain scenes[]")
    matches = [scene for scene in scenes if isinstance(scene, dict) and scene.get("id") == SCENE_ID]
    require(len(matches) == 1, "D52 dashboard row must appear exactly once")
    return matches[0]


def require_generated_row() -> None:
    row = find_row(load_json(RESULTS))
    require(row.get("inventoryId") == ROW_ID, "row inventory mismatch")
    require(row.get("status") == "pass", "row must be pass")
    require(row.get("priority") == "P1", "row priority mismatch")
    require(row.get("referenceKind") == "skia-upstream", "row reference kind mismatch")
    require(row.get("reference") == "artifacts/d52-drawminibitmaprect/skia.png", "row reference path mismatch")
    require("source.inventory" in row.get("tags", []), "row must be inventory-derived")
    require("maturity.adapter-backed" in row.get("tags", []), "row must keep adapter-backed tag")
    require("risk.none" in row.get("tags", []), "row risk tag mismatch")

    generation = row.get("generation")
    require(isinstance(generation, dict), "row generation missing")
    require(generation.get("mode") == "generated", "generation mode mismatch")
    require(generation.get("producer") == "pipelineGeneratedSceneExport", "generation producer mismatch")
    require(generation.get("derivationTask") == "D52DashboardPromotion", "derivation task mismatch")
    require(generation.get("inventoryId") == ROW_ID, "generation inventory mismatch")
    require(generation.get("sourceReport") == rel(D52_4_REPORT), "generation source report mismatch")
    require(generation.get("derivationContract") == rel(D52_4_EVIDENCE), "generation contract mismatch")
    require(generation.get("artifactRoot") == "artifacts/d52-drawminibitmaprect", "generation artifact root mismatch")

    cpu = row.get("cpu")
    gpu = row.get("gpu")
    require(isinstance(cpu, dict) and isinstance(gpu, dict), "row CPU/GPU blocks missing")
    require(cpu.get("status") == "pass", "CPU status mismatch")
    require(gpu.get("status") == "pass", "GPU status mismatch")
    require(abs(float(cpu.get("similarity")) - 99.014) < 0.0001, "CPU similarity mismatch")
    require(abs(float(gpu.get("similarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "GPU similarity mismatch")
    require(gpu.get("route", {}).get("fallbackReason") == "none", "GPU fallback must be none")
    require(gpu.get("route", {}).get("pipelineKey") == SOURCE_PIPELINE_KEY, "dashboard pipelineKey must match source route")
    require(gpu.get("stats", {}).get("matchingPixels") == AFTER_MATCHING_PIXELS, "GPU matching pixels mismatch")
    require(gpu.get("stats", {}).get("pixels") == TOTAL_PIXELS, "GPU total pixels mismatch")
    require(gpu.get("stats", {}).get("threshold") == GPU_THRESHOLD, "GPU threshold mismatch")


def dashboard_counts(root: dict[str, Any], include_d52: bool) -> tuple[int, int, int, float]:
    scenes = root.get("scenes")
    require(isinstance(scenes, list), "dashboard must contain scenes[]")
    selected = [
        scene for scene in scenes
        if isinstance(scene, dict) and (include_d52 or scene.get("id") != SCENE_ID)
    ]
    total = len(selected)
    passing = sum(1 for scene in selected if scene.get("status") == "pass")
    unsupported = sum(1 for scene in selected if scene.get("status") == "expected-unsupported")
    ratio = (passing / total * 100.0) if total else 0.0
    return total, passing, unsupported, ratio


def require_dashboard_score_if_built() -> None:
    if not DASHBOARD_DATA.is_file():
        return
    dashboard = load_json(DASHBOARD_DATA)
    after = dashboard_counts(dashboard, include_d52=True)
    before = dashboard_counts(dashboard, include_d52=False)
    require(after[0] == before[0] + 1, "dashboard row count delta must be +1")
    require(after[1] == before[1] + 1, "dashboard pass count delta must be +1")
    require(after[2] == before[2], "expected-unsupported count must not change")
    require(after[3] > before[3], "pass ratio must improve")

    report = REPORT.read_text(encoding="utf-8")
    for value in (
        str(before[0]),
        str(after[0]),
        str(before[1]),
        str(after[1]),
        f"{before[3]:.2f}%",
        f"{after[3]:.2f}%",
        f"+{after[3] - before[3]:.2f} pts",
    ):
        require(value in report, f"report missing score value {value}")


def require_report() -> None:
    text = REPORT.read_text(encoding="utf-8")
    for required in (
        "skia-gm-drawminibitmaprect",
        "d52-drawminibitmaprect",
        "99.9864%",
        "99.95%",
        "fallback after D52-4 | `none`",
        "Support claim | `true`",
        "Global threshold changed | `false`",
        "M66 evidence inherited | `false`",
        "No render-path change",
        "No WGSL change",
        "No global threshold or tolerance loosening",
        "No M66 inheritance",
    ):
        require(required in text, f"report missing: {required}")


def main() -> None:
    require_scope()
    require_source_evidence()
    require_generated_row()
    require_report()
    require_dashboard_score_if_built()
    print(f"{TICKET} validation passed: {SCENE_ID} promoted with WebGPU {AFTER_GPU_SIMILARITY:.4f}% and fallbackReason=none.")


if __name__ == "__main__":
    main()
