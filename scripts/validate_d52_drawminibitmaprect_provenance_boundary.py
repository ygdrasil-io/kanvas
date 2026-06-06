#!/usr/bin/env python3
"""Validate D52-4 DrawMiniBitmapRect drawImageRect boundary evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D52-4"
ROW_ID = "skia-gm-drawminibitmaprect"
SCENE_ID = "d52-drawminibitmaprect"
OLD_FALLBACK = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
GPU_THRESHOLD = 99.95
BEFORE_GPU_SIMILARITY = 94.9305
AFTER_GPU_SIMILARITY = 99.9864

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-4-drawminibitmaprect-draw-image-rect-provenance-boundary.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json"
STATS = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/stats.json"
ROUTE_GPU = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-gpu.json"
ROUTE_CPU = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-cpu.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect"
SK_CANVAS = ROOT / "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt"
RASTER_TEST = ROOT / "kanvas-skia/src/test/kotlin/org/skia/core/SkCanvasInternalsTest.kt"
WEBGPU_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BitmapShaderPaintRectTest.kt"

EXPECTED_CHANGED_FILES = {
    "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt",
    "kanvas-skia/src/test/kotlin/org/skia/core/SkCanvasInternalsTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/BitmapShaderPaintRectTest.kt",
    "reports/wgsl-pipeline/2026-06-06-d52-4-drawminibitmaprect-draw-image-rect-provenance-boundary.md",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json",
    "scripts/validate_d52_drawminibitmaprect_provenance_boundary.py",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/stats.json",
}

FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json",
    "reports/wgsl-pipeline/2026-06-06-d52-3-drawminibitmaprect-webgpu-root-cause.md",
    "reports/wgsl-pipeline/2026-06-06-d52-2-drawminibitmaprect-artifact-harness.md",
    "reports/wgsl-pipeline/2026-06-06-d52-1-drawminibitmaprect-promotion-readiness.md",
}

VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon --rerun-tasks :kanvas-skia:test --tests org.skia.core.SkCanvasInternalsTest",
    "rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderPaintRectTest",
    "rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
    "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
    "rtk python3 scripts/validate_d52_drawminibitmaprect_provenance_boundary.py",
    "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-draw-image-rect-provenance-boundary.json",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-4-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_provenance_boundary.py",
    "rtk git diff --check",
]


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
    forbidden = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden, f"forbidden historical/global evidence changed: {forbidden}")


def require_png_size(path: Path, width: int, height: int) -> None:
    require(path.is_file(), f"missing PNG artifact: {rel(path)}")
    with path.open("rb") as handle:
        header = handle.read(24)
    require(header.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} is not a PNG")
    actual_width, actual_height = struct.unpack(">II", header[16:24])
    require((actual_width, actual_height) == (width, height), f"{rel(path)} has unexpected size")


def require_code_and_tests() -> None:
    text = SK_CANVAS.read_text(encoding="utf-8")
    require("clipDrawImageRectSourceToImage" in text, "SkCanvas boundary helper missing")
    require("effectiveSrc" in text and "effectiveDst" in text, "SkCanvas does not route clipped src/dst")
    require("SkBitmapShader(kClamp)" not in text, "SkCanvas must not special-case user kClamp shaders")

    raster = RASTER_TEST.read_text(encoding="utf-8")
    require("drawImageRect clips source outside image without changing user kClamp shader" in raster, "raster kClamp guard missing")
    require("user kClamp shader keeps edge extension" in raster, "raster user kClamp assertion missing")

    webgpu = WEBGPU_TEST.read_text(encoding="utf-8")
    require("drawImageRect source outside image clips but user kClamp shader extends edge" in webgpu, "WebGPU kClamp guard missing")
    require("user kClamp shader keeps edge extension" in webgpu, "WebGPU user kClamp assertion missing")


def require_artifacts() -> None:
    for name in ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png"):
        require_png_size(ARTIFACT_DIR / name, 1024, 1024)

    stats = load_json(STATS)
    require(stats.get("sceneId") == SCENE_ID, "stats scene mismatch")
    require(stats.get("inventoryId") == ROW_ID, "stats inventory mismatch")
    require(stats.get("status") == "pass", "stats status must be pass")
    require(stats.get("fallbackReason") == "none", "stats fallback must be none")
    require(stats.get("supportClaim") is True, "stats must claim local support")
    require(float(stats.get("gpuPromotionThreshold")) == GPU_THRESHOLD, "stats GPU threshold mismatch")
    require(abs(float(stats.get("gpuSimilarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "stats GPU similarity mismatch")
    require(float(stats.get("gpuSimilarity")) >= GPU_THRESHOLD, "stats GPU similarity below threshold")
    require(stats.get("globalThresholdChanged") is False, "global threshold changed")
    require(stats.get("m66EvidenceInherited") is False, "M66 evidence inherited")

    route_gpu = load_json(ROUTE_GPU)
    require(route_gpu.get("status") == "pass", "GPU route status must be pass")
    require(route_gpu.get("fallbackReason") == "none", "GPU route fallback must be none")
    require(route_gpu.get("supportClaim") is True, "GPU route must claim local support")
    require(abs(float(route_gpu.get("similarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "GPU route similarity mismatch")
    require(float(route_gpu.get("threshold")) == GPU_THRESHOLD, "GPU route threshold mismatch")
    require(route_gpu.get("globalThresholdChanged") is False, "GPU route changed global threshold")

    route_cpu = load_json(ROUTE_CPU)
    require(route_cpu.get("status") == "pass", "CPU route status must remain pass")
    require(route_cpu.get("fallbackReason") == "none", "CPU route fallback must be none")


def require_evidence() -> None:
    report = REPORT.read_text(encoding="utf-8")
    require("99.9864%" in report, "report missing after score")
    require("SkBitmapShader(kClamp)" in report, "report missing user kClamp distinction")
    require("No global" in report, "report missing global non-change statement")

    evidence = load_json(EVIDENCE)
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory mismatch")
    require(evidence.get("sceneId") == SCENE_ID, "scene mismatch")
    require(evidence.get("classification") == "drawImageRect-boundary-source-image-intersection-correction", "classification mismatch")
    require(evidence.get("validationCommands") == VALIDATION_COMMANDS, "validation commands mismatch")

    decision = evidence.get("architectureDecision", {})
    require(decision.get("pipelineKeyChanged") is False, "PipelineKey must not change")
    require(decision.get("wgslChanged") is False, "WGSL must not change")
    require(decision.get("webgpuHeuristicAdded") is False, "WebGPU heuristic must not be added")
    require(decision.get("userKClampShaderChanged") is False, "user kClamp shader must not change")

    before = evidence.get("measurements", {}).get("before", {})
    after = evidence.get("measurements", {}).get("after", {})
    require(before.get("status") == "expected-unsupported", "before status mismatch")
    require(before.get("fallbackReason") == OLD_FALLBACK, "before fallback mismatch")
    require(abs(float(before.get("similarity")) - BEFORE_GPU_SIMILARITY) < 0.0001, "before similarity mismatch")
    require(after.get("status") == "pass", "after status mismatch")
    require(after.get("fallbackReason") == "none", "after fallback mismatch")
    require(abs(float(after.get("similarity")) - AFTER_GPU_SIMILARITY) < 0.0001, "after similarity mismatch")
    require(float(after.get("similarity")) >= GPU_THRESHOLD, "after similarity below threshold")

    non_claims = evidence.get("nonClaims", {})
    for key in (
        "resultsJsonChanged",
        "scenesJsonChanged",
        "d50ManifestChanged",
        "d51EvidenceChanged",
        "d52_1EvidenceChanged",
        "d52_2EvidenceChanged",
        "d52_3EvidenceChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "ganeshOrGraphitePorted",
        "skslCompilerIrVmRebuilt",
    ):
        require(non_claims.get(key) is False, f"nonClaim {key} must be false")


def main() -> None:
    require_scope()
    require_code_and_tests()
    require_artifacts()
    require_evidence()
    print(f"{TICKET} validation passed: drawImageRect boundary clips source/image intersection and WebGPU reaches {AFTER_GPU_SIMILARITY:.4f}%.")


if __name__ == "__main__":
    main()
