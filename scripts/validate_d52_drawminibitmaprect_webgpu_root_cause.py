#!/usr/bin/env python3
"""Validate the D52-3 DrawMiniBitmapRect WebGPU root-cause evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D52-3"
ROW_ID = "skia-gm-drawminibitmaprect"
SCENE_ID = "d52-drawminibitmaprect"
FALLBACK_REASON = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
SOURCE_DRAFT = "global/kanvas/tickets/drafts/brouillon-ticket-d52-3-diagnostiquer-et-corriger-l-ecart-web-gpu-draw-mini-bitmap-rect"
GPU_THRESHOLD = 99.95
GPU_SIMILARITY = 94.9305

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-3-drawminibitmaprect-webgpu-root-cause.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json"
D52_2_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-2-drawminibitmaprect-artifact-harness.md"
D52_2_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json"
D52_2_VALIDATOR = ROOT / "scripts/validate_d52_drawminibitmaprect_artifact_harness.py"
TEST_FILE = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/DrawMiniBitmapRectSceneCaptureTest.kt"
STATS = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/stats.json"
ROUTE_GPU = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/route-gpu.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d52-3-drawminibitmaprect-webgpu-root-cause.md",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json",
    "scripts/validate_d52_drawminibitmaprect_webgpu_root_cause.py",
}
ALLOWED_ARTIFACT_FILES = {
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/gpu-diff.png",
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
    "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "render-pipeline/",
    "kanvas-skia/",
    "skia-integration-tests/",
)
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_d52_drawminibitmaprect_webgpu_root_cause.py",
    "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-webgpu-root-cause.json",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-3-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_webgpu_root_cause.py",
    "rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
    "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest",
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
    diff_result = subprocess.run(
        ["git", "diff", "--name-only"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    cached_result = subprocess.run(
        ["git", "diff", "--cached", "--name-only"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    status_result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    untracked_result = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    changed.update(line.strip() for line in cached_result.stdout.splitlines() if line.strip())
    for line in status_result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1].strip()
        if path:
            changed.add(path.rstrip("/"))
    for line in untracked_result.stdout.splitlines():
        path = line.strip()
        if path:
            changed.add(path)
    changed.discard("reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect")
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    allowed = EXPECTED_CHANGED_FILES | ALLOWED_ARTIFACT_FILES
    unexpected = sorted(path for path in changed if path not in allowed)
    require(not unexpected, f"unexpected changed files: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard or previous evidence changed: {forbidden_paths}")
    forbidden_prefixes = sorted(
        path for path in changed
        if path.startswith(FORBIDDEN_PREFIXES) and path not in ALLOWED_ARTIFACT_FILES
    )
    require(not forbidden_prefixes, f"production/shared diffs are out of scope for D52-3 refusal: {forbidden_prefixes}")


def require_png_size(path: Path, width: int, height: int) -> None:
    require(path.is_file(), f"missing PNG artifact: {rel(path)}")
    with path.open("rb") as f:
        header = f.read(24)
    require(header.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} is not a PNG")
    actual_width, actual_height = struct.unpack(">II", header[16:24])
    require((actual_width, actual_height) == (width, height), f"{rel(path)} has unexpected size")


def require_inputs() -> None:
    for path in (D52_2_REPORT, D52_2_EVIDENCE, D52_2_VALIDATOR, TEST_FILE, STATS, ROUTE_GPU):
        require(path.is_file(), f"missing required input: {rel(path)}")
    for name in ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png"):
        require_png_size(ARTIFACT_DIR / name, 1024, 1024)

    d52_2 = load_json(D52_2_EVIDENCE)
    require(d52_2.get("ticket") == "D52-2", "D52-2 ticket mismatch")
    require(d52_2.get("inventoryId") == ROW_ID, "D52-2 inventory mismatch")
    require(d52_2.get("sceneId") == SCENE_ID, "D52-2 scene mismatch")
    require(d52_2.get("status") == "expected-unsupported", "D52-2 status must remain expected-unsupported")
    require(d52_2.get("fallbackReason") == FALLBACK_REASON, "D52-2 fallback mismatch")

    stats = load_json(STATS)
    require(stats.get("sceneId") == SCENE_ID, "stats scene mismatch")
    require(stats.get("inventoryId") == ROW_ID, "stats inventory mismatch")
    require(stats.get("status") == "expected-unsupported", "stats status mismatch")
    require(stats.get("fallbackReason") == FALLBACK_REASON, "stats fallback mismatch")
    require(stats.get("supportClaim") is False, "stats must not claim support")
    require(stats.get("gpuPromotionThreshold") == GPU_THRESHOLD, "stats GPU threshold mismatch")
    require(abs(float(stats.get("gpuSimilarity")) - GPU_SIMILARITY) < 0.0001, "stats GPU similarity mismatch")
    require(stats.get("globalThresholdChanged") is False, "global threshold must not change")
    require(stats.get("m66EvidenceInherited") is False, "M66 evidence must not be inherited")

    route_gpu = load_json(ROUTE_GPU)
    require(route_gpu.get("status") == "expected-unsupported", "GPU route status mismatch")
    require(route_gpu.get("fallbackReason") == FALLBACK_REASON, "GPU route fallback mismatch")
    require(route_gpu.get("supportClaim") is False, "GPU route must not claim support")

    test_text = TEST_FILE.read_text(encoding="utf-8")
    require("GPU_PROMOTION_THRESHOLD = 99.95" in test_text, "capture test threshold changed")
    require(FALLBACK_REASON in test_text, "capture test fallback changed")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == 1, "schema version mismatch")
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("sourceDraftMemory") == SOURCE_DRAFT, "source draft mismatch")
    require(evidence.get("classification") == "root-cause-diagnosed-local-webgpu-fix-refused", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory mismatch")
    require(evidence.get("sceneId") == SCENE_ID, "scene mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("validationCommands") == VALIDATION_COMMANDS, "validation commands mismatch")
    require(set(evidence.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expected changed files mismatch")

    measurements = evidence.get("measurements", {})
    before = measurements.get("before", {})
    after = measurements.get("after", {})
    for label, measurement in (("before", before), ("after", after)):
        require(measurement.get("backend") == "WebGPU", f"{label} backend mismatch")
        require(measurement.get("status") == "expected-unsupported", f"{label} status mismatch")
        require(measurement.get("fallbackReason") == FALLBACK_REASON, f"{label} fallback mismatch")
        require(measurement.get("threshold") == GPU_THRESHOLD, f"{label} threshold mismatch")
        require(abs(float(measurement.get("similarity")) - GPU_SIMILARITY) < 0.0001, f"{label} similarity mismatch")
    require(measurements.get("correctionApplied") is False, "correction must be refused")
    require(measurements.get("fallbackReasonNoneAllowed") is False, "fallbackReason=none must remain forbidden")

    root = evidence.get("rootCause", {})
    require(root.get("gmSourceImageSize") == {"width": 2048, "height": 2048}, "source image size mismatch")
    require(root.get("sourceRectSizes") == [1, 3, 9, 27, 81, 243, 729, 2187, 6561], "source rect sizes mismatch")
    require(root.get("totalDraws") == 81, "total draw count mismatch")
    require(root.get("outOfImageBoundsDraws") == 32, "out-of-bounds draw count mismatch")
    require("SkBitmapShader(kClamp)" in root.get("summary", ""), "root cause summary must name kClamp shader rewrite")

    decision = evidence.get("correctionDecision", {})
    require(decision.get("decision") == "refused", "decision must be refused")
    require("cannot reliably distinguish" in decision.get("reason", ""), "decision reason must name ambiguity")

    compatibility = evidence.get("d52_2ValidatorCompatibility", {})
    require(
        compatibility.get("command") == "rtk python3 scripts/validate_d52_drawminibitmaprect_artifact_harness.py --require-artifacts",
        "D52-2 validator compatibility command mismatch",
    )
    require(compatibility.get("notFinalD52_3Validation") is True, "D52-2 validator must be marked non-final for D52-3")
    require("scope guard" in compatibility.get("reason", ""), "compatibility reason must name scope guard")

    non_claims = evidence.get("nonClaims", {})
    for key, value in non_claims.items():
        require(value is False, f"non-claim {key} must be false")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "D52-3 diagnoses the D52-2 WebGPU gap but does not apply a rendering fix.",
        "32 of 81 draws",
        "SkBitmapShader(kClamp)",
        "No WebGPU production patch is applied",
        FALLBACK_REASON,
        "94.9305%",
        "99.95%",
        "No support promotion is claimed.",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")


def main() -> int:
    require_scope()
    require_inputs()
    evidence = load_json(EVIDENCE)
    require_evidence(evidence)
    require_report()
    print("D52-3 DrawMiniBitmapRect WebGPU root-cause evidence validated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
