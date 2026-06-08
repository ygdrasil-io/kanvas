#!/usr/bin/env python3
"""Validate D54-2 ImageGM WebGPU gap diagnostic evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D54-2"
ROW_ID = "skia-gm-image"
SCENE_ID = "d54-skia-gm-image"
FALLBACK_REASON = "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required"
SOURCE_DRAFT = "global/kanvas/tickets/drafts/brouillon-ticket-d54-2-diagnostiquer-lecart-web-gpu-image-gm-apres-artefacts-row-specific"
SOURCE_FINDING = "global/kanvas/findings/d54-1-skia-gm-image-artifact-harness"
GPU_THRESHOLD = 99.95
CPU_THRESHOLD = 98.0

BUILD_FILE = ROOT / "gpu-raster/build.gradle.kts"
TEST_FILE = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ImageGmSceneCaptureTest.kt"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-d54-2-imagegm-webgpu-gap-diagnostic.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d54-imagegm-webgpu-gap-diagnostic.json"
DIAGNOSTIC = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap/region-diagnostic.json"
D54_1_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json"
D54_1_ARTIFACTS = ROOT / "reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image"

EXPECTED_CHANGED_FILES = {
    "gpu-raster/build.gradle.kts",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ImageGmSceneCaptureTest.kt",
    "reports/wgsl-pipeline/2026-06-08-d54-2-imagegm-webgpu-gap-diagnostic.md",
    "reports/wgsl-pipeline/scenes/generated/d54-imagegm-webgpu-gap-diagnostic.json",
    "reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap/region-diagnostic.json",
    "scripts/validate_d54_imagegm_webgpu_gap_diagnostic.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/d53-dashboard-visibility-reconcile.json",
    "reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
)
FEATURE_NON_CLAIMS = {
    "codecSupportClaim",
    "yuvSupportClaim",
    "animationSupportClaim",
    "exifSupportClaim",
    "mipmapSupportClaim",
    "tileModeSupportClaim",
    "colorManagedImageDecodeSupportClaim",
    "arbitraryImageDecodeSupportClaim",
    "broadImageSupportClaim",
}
NEIGHBOR_NON_CLAIMS = {
    "neighborEvidenceInherited",
    "bitmappremulEvidenceInherited",
    "drawbitmaprectEvidenceInherited",
    "drawminibitmaprectEvidenceInherited",
    "imageSourceEvidenceInherited",
}


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
        ["git", "diff", "--name-only", "origin/master"],
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
    changed.discard("reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap")
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_CHANGED_FILES)
    require(not unexpected, f"unexpected changed files: {unexpected}")
    missing = sorted(path for path in EXPECTED_CHANGED_FILES if not (ROOT / path).is_file())
    require(not missing, f"missing expected D54-2 files: {missing}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard or D54-1 evidence changed: {forbidden_paths}")
    forbidden_prefixes = sorted(
        path for path in changed
        if path.startswith(FORBIDDEN_PREFIXES) and path not in EXPECTED_CHANGED_FILES
    )
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_build_file() -> None:
    text = BUILD_FILE.read_text(encoding="utf-8")
    phrase = 'System.getProperty("kanvas.imageGmGapDiagnostic.write")?.let'
    require(phrase in text, "gpu-raster test task must forward kanvas.imageGmGapDiagnostic.write")
    require('systemProperty("kanvas.imageGmGapDiagnostic.write", it)' in text, "missing forwarded diagnostic system property")


def require_test_file() -> None:
    text = TEST_FILE.read_text(encoding="utf-8")
    required = (
        "imagegm writes webgpu gap diagnostic when requested",
        "writeGapDiagnostic(",
        "gapDiagnosticJson(",
        "reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap",
        "region-diagnostic.json",
        "safeCorrectionIdentifiedByD54_2",
        "supportClaimAddedByD54_2",
        "globalDashboardPromotedByD54_2",
        "rendererChanged",
        "wgslProductionChanged",
        "PNG exports are encoded through SkBitmap.getPixelAsSrgb",
        FALLBACK_REASON,
    )
    for phrase in required:
        require(phrase in text, f"test file missing phrase: {phrase}")


def require_d54_1_inputs() -> None:
    d54_1 = load_json(D54_1_EVIDENCE)
    require(d54_1.get("ticket") == "D54-1", "D54-1 evidence ticket mismatch")
    require(d54_1.get("inventoryId") == ROW_ID, "D54-1 inventory mismatch")
    require(d54_1.get("status") == "expected-unsupported", "D54-1 must remain expected-unsupported")
    require(d54_1.get("fallbackReason") == FALLBACK_REASON, "D54-1 fallback mismatch")
    for name in ("skia.png", "cpu.png", "cpu-diff.png", "gpu.png", "gpu-diff.png", "route-cpu.json", "route-gpu.json", "stats.json"):
        require((D54_1_ARTIFACTS / name).is_file(), f"missing D54-1 artifact: {name}")


def require_evidence(evidence: dict[str, Any], diagnostic: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == 1, "schema version mismatch")
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("sourceDraftMemory") == SOURCE_DRAFT, "source draft memory mismatch")
    require(evidence.get("sourceFindingMemory") == SOURCE_FINDING, "source finding memory mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory mismatch")
    require(evidence.get("sceneId") == SCENE_ID, "scene mismatch")
    require(evidence.get("status") == "expected-unsupported", "D54-2 must not promote status")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback reason mismatch")

    diag = evidence.get("diagnostic", {})
    require(diag.get("artifactPath") == rel(DIAGNOSTIC), "diagnostic artifact path mismatch")
    require(diag.get("safeCorrectionIdentifiedByD54_2") is False, "safe correction must remain false")
    require(diag.get("gradlePropertyForwardedByGpuRaster") is True, "Gradle forwarding evidence missing")
    require("snapshot-image texture sampling" in diag.get("nextAction", ""), "next action must remain diagnostic")

    cpu = evidence.get("metrics", {}).get("cpu", {})
    gpu = evidence.get("metrics", {}).get("webgpu", {})
    require(cpu.get("status") == "pass", "CPU must remain pass")
    require(cpu.get("similarity", 0.0) >= CPU_THRESHOLD, "CPU similarity below threshold")
    require(cpu.get("threshold") == CPU_THRESHOLD, "CPU threshold changed")
    require(gpu.get("status") == "expected-unsupported", "WebGPU must remain expected-unsupported")
    require(gpu.get("similarity", 100.0) < GPU_THRESHOLD, "WebGPU unexpectedly passes threshold")
    require(gpu.get("threshold") == GPU_THRESHOLD, "GPU threshold changed")
    require(gpu.get("fallbackReason") == FALLBACK_REASON, "WebGPU fallback mismatch")
    require(gpu.get("supportClaim") is False, "WebGPU support claim must be false")

    top = evidence.get("topWebGpuVsReferenceRegions", [])
    require(isinstance(top, list) and len(top) == 14, "top region list must contain 14 ImageGM cells")
    require(top[0].get("name") == "pre-alloc/full-crop", "dominant top region mismatch")
    require(top[0].get("mismatchingPixels") == 1046, "dominant mismatch count changed")

    non_claims = evidence.get("nonClaims", {})
    for key in FEATURE_NON_CLAIMS | NEIGHBOR_NON_CLAIMS:
        require(non_claims.get(key) is False, f"non-claim must be false: {key}")
    for key in (
        "supportClaimAddedByD54_2",
        "globalDashboardPromotedByD54_2",
        "resultsJsonChanged",
        "scenesJsonChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "rendererChanged",
        "productionCodeChanged",
        "wgslProductionChanged",
        "wgsl4kChanged",
        "upstreamSourceChanged",
    ):
        require(non_claims.get(key) is False, f"non-claim must be false: {key}")

    require(diagnostic.get("ticket") == TICKET, "diagnostic ticket mismatch")
    require(diagnostic.get("sourceDraftMemory") == SOURCE_DRAFT, "diagnostic source draft mismatch")
    require(diagnostic.get("sourceFindingMemory") == SOURCE_FINDING, "diagnostic source finding mismatch")
    require(diagnostic.get("status") == "expected-unsupported", "diagnostic status mismatch")
    require(diagnostic.get("fallbackReason") == FALLBACK_REASON, "diagnostic fallback mismatch")
    require(diagnostic.get("diagnosis", {}).get("safeCorrectionIdentifiedByD54_2") is False, "diagnostic correction flag must be false")
    require(diagnostic.get("nonClaims", {}).get("rendererChanged") is False, "diagnostic renderer non-claim missing")
    require(diagnostic.get("nonClaims", {}).get("wgslProductionChanged") is False, "diagnostic WGSL non-claim missing")

    d_cpu = diagnostic.get("metrics", {}).get("cpu", {})
    d_gpu = diagnostic.get("metrics", {}).get("webgpu", {})
    require(d_cpu.get("similarity") == cpu.get("similarity"), "CPU metric mismatch between evidence and diagnostic")
    require(d_gpu.get("similarity") == gpu.get("similarity"), "GPU metric mismatch between evidence and diagnostic")

    regions = diagnostic.get("webgpuVsReferenceRegions", [])
    require(isinstance(regions, list) and len(regions) == 14, "diagnostic must contain 14 WebGPU/reference regions")
    dominant = max(regions, key=lambda item: item.get("mismatchingPixels", -1))
    require(dominant.get("name") == "pre-alloc/full-crop", "dominant diagnostic region mismatch")
    require(dominant.get("mismatchingPixels") == 1046, "dominant diagnostic mismatch count changed")

    samples = diagnostic.get("samples", [])
    require(isinstance(samples, list) and len(samples) == 14, "diagnostic must contain 14 cell samples")


def require_report(evidence: dict[str, Any]) -> None:
    text = REPORT.read_text(encoding="utf-8")
    required = (
        SOURCE_DRAFT,
        SOURCE_FINDING,
        rel(EVIDENCE),
        rel(DIAGNOSTIC),
        FALLBACK_REASON,
        "98.5962%",
        "99.95%",
        "Aucune correction locale sure n'est identifiee",
        "Aucun support WebGPU ImageGM n'est revendique",
        "Aucun gain de score n'est revendique",
        "Aucune preuve voisine n'est heritee",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")
    for region in evidence.get("topWebGpuVsReferenceRegions", []):
        require(region["name"] in text, f"report missing region: {region['name']}")


def main() -> None:
    require_scope()
    require_build_file()
    require_test_file()
    require_d54_1_inputs()
    evidence = load_json(EVIDENCE)
    diagnostic = load_json(DIAGNOSTIC)
    require_evidence(evidence, diagnostic)
    require_report(evidence)
    print(f"{TICKET} ImageGM WebGPU gap diagnostic validation passed")


if __name__ == "__main__":
    main()
