#!/usr/bin/env python3
"""Validate D51-3 ImageSourceGM row-specific refusal evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-3"
ROW_ID = "skia-gm-imagesource"
PREVIOUS_FALLBACK = "image.imagesource.row-specific-artifacts-required"
FALLBACK_REASON = "image.imagesource.image-filter-cubic-panels-webgpu-artifacts-required"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-3-imagesource-row-specific-evidence.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-imagesource-row-specific-evidence.json"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
FOR467_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md"
FOR467_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json"
SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageSourceGM.kt"
TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/ImageSourceTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/imagesource.png"
CPU_SCORE = ROOT / "skia-integration-tests/test-similarity-scores.properties"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-3-imagesource-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d51-imagesource-row-specific-evidence.json",
    "scripts/validate_d51_imagesource_row_specific_evidence.py",
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
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D51-3 files, got: {sorted(changed)}")
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
    for path in (SOURCE, TEST, REFERENCE, CPU_SCORE, FOR467_REPORT, FOR467_EVIDENCE):
        require(path.is_file(), f"missing row-specific audit input: {rel(path)}")

    source = SOURCE.read_text(encoding="utf-8")
    required_source_phrases = (
        'override fun getName(): String = "imagesource"',
        "override fun getISize(): SkISize = SkISize.Make(500, 150)",
        "makeStringImage(",
        "w = 100, h = 100",
        'x = 20, y = 70, textSize = 96, str = "e"',
        "SkImageInfo.MakeN32Premul(w, h)",
        "val srcRect = SkRect.MakeXYWH(20f, 20f, 30f, 30f)",
        "val dstRect = SkRect.MakeXYWH(0f, 10f, 60f, 60f)",
        "val clipRect = SkRect.MakeXYWH(0f, 0f, 100f, 100f)",
        "SkCubicResampler(1f / 3f, 1f / 3f)",
        "SkImageFilters.Image(fImage, SkSamplingOptions(SkFilterMode.kNearest))",
        "SkImageFilters.Image(fImage, srcRect, srcRect, sampling)",
        "SkImageFilters.Image(fImage, srcRect, dstRect, sampling)",
        "SkImageFilters.Image(fImage, bounds, dstRect, sampling)",
        "SkPaint().apply { imageFilter = filter }",
        "c.clipRect(clipRect)",
        "c.drawPaint(paint)",
        "c.translate(100f, 0f)",
    )
    for phrase in required_source_phrases:
        require(phrase in source, f"ImageSourceGM source missing phrase: {phrase}")

    test = TEST.read_text(encoding="utf-8")
    require("class ImageSourceTest" in test, "ImageSourceTest class missing")
    require("TestUtils.loadReferenceBitmap(gm.name())" in test, "ImageSourceTest must load reference by GM name")
    require('SimilarityTracker.updateScore("ImageSourceGM", comparison.similarity)' in test, "ImageSourceGM score update missing")
    require("ImageSourceGM matches imagesource_png within tolerance" in test, "ImageSourceGM test name mismatch")

    scores = CPU_SCORE.read_text(encoding="utf-8")
    require("ImageSourceGM=98.53466666666667" in scores, "missing ImageSourceGM historical CPU score")

    width, height, bit_depth, color_type = read_png_header(REFERENCE)
    require((width, height) == (500, 150), "imagesource.png dimensions must be 500x150")
    require(bit_depth == 16 and color_type == 6, "imagesource.png must be PNG RGBA 16-bit/color")


def require_d50_manifest_unchanged() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "D50 manifest must contain one ImageSourceGM row")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 ImageSourceGM status must remain expected-unsupported")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "D50 ImageSourceGM strict status must remain expected-unsupported")
    require(row.get("dashboardRowId") is None, "D50 ImageSourceGM must not point to a dashboard row")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 fallback should remain the D50 closeout reason")
    require(row.get("reason") == PREVIOUS_FALLBACK, "D50 reason should remain the D50 closeout reason")
    require(row.get("gpuRoute") == "webgpu.image-source.imagesource.expected-unsupported", "D50 GPU route must remain expected-unsupported")
    require(row.get("imageSourceEvidenceInherited") is False, "D50 row must not inherit image-source evidence")
    route = row.get("routeDiagnostics", {})
    require(route.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 route fallback should remain unchanged")
    require("fallbackReason=none" in route.get("policy", ""), "D50 policy must keep fallbackReason=none promotion gate")


def require_for467_evidence() -> None:
    for467 = load_json(FOR467_EVIDENCE)
    row = for467.get("row", {})
    require(row.get("inventoryId") == ROW_ID, "FOR-467 inventory mismatch")
    require(row.get("status") == "expected-unsupported", "FOR-467 status mismatch")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "FOR-467 fallback mismatch")
    provenance = row.get("sourceImageProvenance", {})
    require(provenance.get("kotlinSource") == rel(SOURCE), "FOR-467 Kotlin source mismatch")
    require(provenance.get("scene") == "ImageSourceGM", "FOR-467 scene mismatch")
    require(provenance.get("dynamicSourceImage") == "not claimed", "FOR-467 dynamic source image claim mismatch")

    report = FOR467_REPORT.read_text(encoding="utf-8")
    require("`skia-gm-imagesource` est traite comme `expected-unsupported` row-specific" in report, "FOR-467 report status missing")
    require("ne sont pas herites comme support D50" in report, "FOR-467 report inheritance guard missing")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-precise-refusal", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("previousFallbackReason") == PREVIOUS_FALLBACK, "previous fallback mismatch")
    require(evidence.get("fallbackReasonImproved") is True, "fallback reason must be marked improved")
    require(evidence.get("d50ManifestChanged") is False, "D50 manifest must not be changed by D51-3")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")

    audit = evidence.get("rowSpecificAudit", {})
    require(audit.get("historicalImageSourceEvidencePromotesRow") is False, "historical ImageSourceGM evidence must not promote row")
    require(audit.get("adjacentImageEvidenceInherited") is False, "adjacent image evidence must not be inherited")
    source = audit.get("imageSourceGmSource", {})
    require(source.get("kotlinSource") == rel(SOURCE), "ImageSourceGM source mismatch")
    require(source.get("gmName") == "imagesource", "GM name mismatch")
    require(source.get("gmDimensions") == {"width": 500, "height": 150}, "GM dimensions mismatch")
    require(source.get("sourceImage", {}).get("dimensions") == {"width": 100, "height": 100}, "source image dimensions mismatch")
    require(source.get("sourceImage", {}).get("colorType") == "N32 premul", "source image color type mismatch")
    require("letter e" in source.get("sourceImage", {}).get("content", ""), "source image content mismatch")
    require(source.get("sampling", {}).get("panel1") == "SkFilterMode.kNearest", "nearest panel sampling mismatch")
    require(source.get("sampling", {}).get("panels2To4") == "SkCubicResampler(1/3, 1/3)", "cubic panel sampling mismatch")
    panels = source.get("panelBehavior", [])
    require(isinstance(panels, list) and len(panels) == 4, "ImageSourceGM must list four panels")
    require([panel.get("shape") for panel in panels] == [
        "full image nearest",
        "subset->subset cubic",
        "subset->dst cubic",
        "bounds->dst cubic",
    ], "panel shapes mismatch")
    per_panel = source.get("perPanelApplication", {})
    require(per_panel.get("paintField") == "paint.imageFilter", "paint field mismatch")
    require(per_panel.get("clipRect") == "SkRect.MakeXYWH(0, 0, 100, 100)", "clip rect mismatch")
    require(per_panel.get("draw") == "drawPaint", "draw call mismatch")
    require(per_panel.get("translationAfterPanelPx") == 100, "translation mismatch")
    blockers = audit.get("whyNotPromotable", [])
    require(any("No D51 row-specific WebGPU dashboard artifact" in item for item in blockers), "missing WebGPU blocker")
    require(any("No D51 diff/stat payload" in item for item in blockers), "missing diff/stat blocker")
    require(any("fallbackReason=none" in item for item in blockers), "missing route diagnostics blocker")
    require(any("Adjacent image evidence" in item and "SkImageFilters.Image" in item for item in blockers), "missing adjacent evidence blocker")
    adjacent = audit.get("adjacentEvidenceNotInherited", [])
    for required in ("ImageGM", "BitmapImageGM", "SimpleSnapImageGM", "MakeRasterImageGM", "DrawBitmapRectGM", "image-filter crop/prepass evidence"):
        require(required in adjacent, f"missing adjacent non-inheritance item: {required}")

    artifacts = evidence.get("artifacts", {})
    reference = artifacts.get("reference", {})
    require(reference.get("path") == rel(REFERENCE), "reference path mismatch")
    require(reference.get("dimensions") == {"width": 500, "height": 150}, "reference dimensions mismatch")
    require(reference.get("format") == "PNG 16-bit/color RGBA", "reference format mismatch")
    require(reference.get("promotableAsDashboardReference") is False, "historical reference must not be promotable alone")
    cpu = artifacts.get("cpu", {})
    require(cpu.get("test") == rel(TEST), "CPU test path mismatch")
    require(cpu.get("historicalSimilarity") == {"ImageSourceGM": 98.53466666666667}, "CPU score mismatch")
    require(cpu.get("promotableAsDashboardCpuArtifact") is False, "historical CPU score must not be promotable alone")
    require(artifacts.get("webgpu", {}).get("status") == "missing", "WebGPU status must remain missing")
    require(artifacts.get("webgpu", {}).get("required") is True, "WebGPU artifact must be required")
    require(artifacts.get("diffStats", {}).get("status") == "not-computed", "diff/stat must not be computed")

    routes = evidence.get("routeDiagnostics", {})
    require(routes.get("cpu") == "cpu.image-source.imagesource.historical-cpu-similarity-only", "CPU route mismatch")
    require(routes.get("gpu") == "webgpu.image-source.imagesource.expected-unsupported", "GPU route mismatch")
    require(routes.get("fallbackReason") == FALLBACK_REASON, "route fallback mismatch")
    require("image-filter source panels under clip" in routes.get("policy", ""), "route policy must name ImageSourceGM scope")

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
        "`skia-gm-imagesource` reste `expected-unsupported`",
        FALLBACK_REASON,
        "reference\n`skia-integration-tests/src/test/resources/original-888/imagesource.png` existe\nen 500x150",
        "score CPU historique est\n`ImageSourceGM=98.53466666666667`",
        "image source N32 premul 100x100 avec la lettre `e`",
        "full image nearest, subset->subset cubic,\nsubset->dst cubic, et bounds->dst cubic",
        "`paint.imageFilter`, `clipRect(0,0,100,100)` et `drawPaint`",
        "pas d'artefact WebGPU row-specific dashboard",
        "pas de\ndiff/stat D51",
        "pas de diagnostics de route avec `fallbackReason=none`",
        "Les preuves image voisines ne couvrent pas ce chemin",
        "Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode, source image\n  dynamique, image color-managed ou image arbitraire",
        "Aucun support WebGPU pour `ImageSourceGM`",
        "Aucun gain de score support et aucun gain Skia-comparable",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_existing_inputs()
    require_d50_manifest_unchanged()
    require_for467_evidence()
    require_evidence(evidence)
    require_report()
    print(f"{TICKET} validation passed: {ROW_ID}=expected-unsupported fallback={FALLBACK_REASON}")


if __name__ == "__main__":
    main()
