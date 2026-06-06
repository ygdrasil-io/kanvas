#!/usr/bin/env python3
"""Validate D51-2 ImageGM row-specific refusal evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-2"
ROW_ID = "skia-gm-image"
PREVIOUS_FALLBACK = "image.imagegm.row-specific-artifacts-required"
FALLBACK_REASON = "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-2-imagegm-row-specific-evidence.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
FOR466_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md"
FOR466_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json"
SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt"
TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/ImageGMTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/image-surface.png"
CPU_SCORE = ROOT / "skia-integration-tests/test-similarity-scores.properties"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-2-imagegm-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json",
    "scripts/validate_d51_imagegm_row_specific_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "gpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
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
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ", 1)[1].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope(evidence: dict[str, Any]) -> None:
    require(set(evidence.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expectedChangedFiles mismatch")
    changed = git_changed_paths()
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D51-2 files, got: {sorted(changed)}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard/source inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def read_png_header(path: Path) -> tuple[int, int, int, int]:
    require(path.is_file(), f"missing PNG reference: {rel(path)}")
    with path.open("rb") as handle:
        header = handle.read(33)
    require(header.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} is not a PNG")
    require(header[12:16] == b"IHDR", f"{rel(path)} missing IHDR chunk")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", header[16:26])
    return width, height, bit_depth, color_type


def require_existing_inputs() -> None:
    for path in (SOURCE, TEST, REFERENCE, CPU_SCORE, FOR466_REPORT, FOR466_EVIDENCE):
        require(path.is_file(), f"missing row-specific audit input: {rel(path)}")

    source = SOURCE.read_text(encoding="utf-8")
    required_source_phrases = (
        'override fun getName(): String = "image-surface"',
        "override fun getISize(): SkISize = SkISize.Make(960, 1200)",
        "SkImageInfo.MakeN32(K_W, K_H, SkAlphaType.kPremul)",
        "val surf0 = SkSurface.MakeRaster(info)",
        "val surf1 = SkSurface.MakeRaster(info)",
        "canvas.drawImage(imgR, 0f, 0f, sampling, paint)",
        "canvas.drawImage(imgG, 0f, 80f, sampling, paint)",
        "surf.draw(canvas, 0f, 160f, paint)",
        "canvas.drawImageRect(imgR, src1, dst1, sampling, paint)",
        "canvas.drawImageRect(imgG, src2, dst2, sampling, paint)",
        "canvas.drawImageRect(imgR, src3, dst3, sampling, paint)",
        "canvas.drawImageRect(imgG, fullSrc, dst4, sampling, paint)",
        "GPU column intentionally left blank",
        "const val K_W: Int = 64",
        "const val K_H: Int = 64",
    )
    for phrase in required_source_phrases:
        require(phrase in source, f"ImageGM source missing phrase: {phrase}")

    test = TEST.read_text(encoding="utf-8")
    require("class ImageGMTest" in test, "ImageGMTest class missing")
    require("TestUtils.loadReferenceBitmap(gm.name())" in test, "ImageGMTest must load reference by GM name")
    require('SimilarityTracker.updateScore("ImageGM", comparison.similarity)' in test, "ImageGM score update missing")

    scores = CPU_SCORE.read_text(encoding="utf-8")
    require("ImageGM=98.16961805555555" in scores, "missing ImageGM historical CPU score")

    width, height, bit_depth, color_type = read_png_header(REFERENCE)
    require((width, height) == (960, 1200), "image-surface.png dimensions must be 960x1200")
    require(bit_depth == 16 and color_type == 6, "image-surface.png must be PNG RGBA 16-bit/color")


def require_d50_manifest_unchanged() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "D50 manifest must contain one ImageGM row")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 ImageGM status must remain expected-unsupported")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "D50 ImageGM strict status must remain expected-unsupported")
    require(row.get("dashboardRowId") is None, "D50 ImageGM must not point to a dashboard row")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 fallback should remain the D50 closeout reason")
    require(row.get("reason") == PREVIOUS_FALLBACK, "D50 reason should remain the D50 closeout reason")
    require(row.get("gpuRoute") == "webgpu.image.imagegm.expected-unsupported", "D50 GPU route must remain expected-unsupported")
    require(row.get("historicalImageEvidenceInherited") is False, "D50 row must not inherit historical image evidence")
    route = row.get("routeDiagnostics", {})
    require(route.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 route fallback should remain unchanged")
    require("fallbackReason=none" in route.get("policy", ""), "D50 policy must keep fallbackReason=none promotion gate")


def require_for466_evidence() -> None:
    for466 = load_json(FOR466_EVIDENCE)
    row = for466.get("row", {})
    require(row.get("inventoryId") == ROW_ID, "FOR-466 inventory mismatch")
    require(row.get("status") == "expected-unsupported", "FOR-466 status mismatch")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "FOR-466 fallback mismatch")
    provenance = row.get("decodeFixtureProvenance", {})
    require(provenance.get("kotlinSource") == rel(SOURCE), "FOR-466 Kotlin source mismatch")
    require("SkSurface raster snapshots" in provenance.get("decodePath", ""), "FOR-466 decode provenance mismatch")

    report = FOR466_REPORT.read_text(encoding="utf-8")
    require("`skia-gm-image` est traite comme `expected-unsupported` row-specific" in report, "FOR-466 report status missing")
    require("surfaces raster N32 premul" in report, "FOR-466 report must describe raster snapshots")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-precise-refusal", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("previousFallbackReason") == PREVIOUS_FALLBACK, "previous fallback mismatch")
    require(evidence.get("fallbackReasonImproved") is True, "fallback reason must be marked improved")
    require(evidence.get("d50ManifestChanged") is False, "D50 manifest must not be changed by D51-2")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")

    audit = evidence.get("rowSpecificAudit", {})
    require(audit.get("historicalImageEvidencePromotesRow") is False, "historical ImageGM evidence must not promote row")
    require(audit.get("adjacentImageEvidenceInherited") is False, "adjacent image evidence must not be inherited")
    source = audit.get("imageGmSource", {})
    require(source.get("kotlinSource") == rel(SOURCE), "ImageGM source mismatch")
    require(source.get("gmName") == "image-surface", "GM name mismatch")
    require(source.get("gmDimensions") == {"width": 960, "height": 1200}, "GM dimensions mismatch")
    require(source.get("snapshotSurfaceDimensions") == {"width": 64, "height": 64}, "snapshot dimensions mismatch")
    require(source.get("kanvasGpuColumnMaterialized") is False, "Kanvas GPU column must remain non-materialized")
    variants = source.get("rowVariants", [])
    require(isinstance(variants, list) and len(variants) == 7, "ImageGM must list seven row variants")
    blockers = audit.get("whyNotPromotable", [])
    require(any("No D51 row-specific WebGPU dashboard artifact" in item for item in blockers), "missing WebGPU blocker")
    require(any("No D51 diff/stat payload" in item for item in blockers), "missing diff/stat blocker")
    require(any("fallbackReason=none" in item for item in blockers), "missing route diagnostics blocker")
    require(any("GPU column" in item for item in blockers), "missing GPU column blocker")

    artifacts = evidence.get("artifacts", {})
    reference = artifacts.get("reference", {})
    require(reference.get("path") == rel(REFERENCE), "reference path mismatch")
    require(reference.get("dimensions") == {"width": 960, "height": 1200}, "reference dimensions mismatch")
    require(reference.get("format") == "PNG 16-bit/color RGBA", "reference format mismatch")
    require(reference.get("promotableAsDashboardReference") is False, "historical reference must not be promotable alone")
    cpu = artifacts.get("cpu", {})
    require(cpu.get("test") == rel(TEST), "CPU test path mismatch")
    require(cpu.get("historicalSimilarity") == {"ImageGM": 98.16961805555555}, "CPU score mismatch")
    require(cpu.get("promotableAsDashboardCpuArtifact") is False, "historical CPU score must not be promotable alone")
    require(artifacts.get("webgpu", {}).get("status") == "missing", "WebGPU status must remain missing")
    require(artifacts.get("webgpu", {}).get("required") is True, "WebGPU artifact must be required")
    require(artifacts.get("diffStats", {}).get("status") == "not-computed", "diff/stat must not be computed")

    routes = evidence.get("routeDiagnostics", {})
    require(routes.get("cpu") == "cpu.image.imagegm.historical-cpu-similarity-only", "CPU route mismatch")
    require(routes.get("gpu") == "webgpu.image.imagegm.expected-unsupported", "GPU route mismatch")
    require(routes.get("fallbackReason") == FALLBACK_REASON, "route fallback mismatch")
    require("surface-snapshot drawImage/drawImageRect" in routes.get("policy", ""), "route policy must name ImageGM scope")

    guard = evidence.get("d50Guard", {})
    require(guard.get("status") == "expected-unsupported", "D50 guard status mismatch")
    require(guard.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 guard fallback mismatch")
    require(guard.get("dashboardRowId") is None, "D50 guard dashboard row must be null")

    non_claims = evidence.get("nonClaims", {})
    for key, value in non_claims.items():
        require(value is False, f"{key} must remain false")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "`skia-gm-image` reste `expected-unsupported`",
        FALLBACK_REASON,
        "reference `skia-integration-tests/src/test/resources/original-888/image-surface.png` existe en 960x1200",
        "score CPU historique est `ImageGM=98.16961805555555`",
        "colonne GPU upstream est intentionnellement non materialisee dans le port Kanvas",
        "pas de diagnostics de route avec `fallbackReason=none`",
        "Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, image color-managed ou image arbitraire n'est revendique",
        "Aucun support WebGPU pour `ImageGM` n'est revendique",
        "aucun gain Skia-comparable",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_existing_inputs()
    require_d50_manifest_unchanged()
    require_for466_evidence()
    require_evidence(evidence)
    require_report()
    print(f"{TICKET} validation passed: {ROW_ID}=expected-unsupported fallback={FALLBACK_REASON}")


if __name__ == "__main__":
    main()
