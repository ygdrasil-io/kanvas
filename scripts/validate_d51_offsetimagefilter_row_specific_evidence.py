#!/usr/bin/env python3
"""Validate D51-4 OffsetImageFilterGM row-specific refusal evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-4"
ROW_ID = "skia-gm-offsetimagefilter"
PREVIOUS_FALLBACK = "image-filter.offset.row-specific-artifacts-required"
FALLBACK_REASON = "image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-4-offsetimagefilter-row-specific-evidence.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
FOR468_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md"
FOR468_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json"
SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt"
TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/OffsetImageFilterTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png"
CPU_SCORE = ROOT / "skia-integration-tests/test-similarity-scores.properties"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-4-offsetimagefilter-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json",
    "scripts/validate_d51_offsetimagefilter_row_specific_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
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
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D51-4 files, got: {sorted(changed)}")
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
    for path in (SOURCE, TEST, REFERENCE, CPU_SCORE, FOR468_REPORT, FOR468_EVIDENCE):
        require(path.is_file(), f"missing row-specific audit input: {rel(path)}")

    source = SOURCE.read_text(encoding="utf-8")
    required_source_phrases = (
        'override fun getName(): String = "offsetimagefilter"',
        "override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)",
        "private const val WIDTH: Int = 600",
        "private const val HEIGHT: Int = 100",
        "private const val MARGIN: Int = 12",
        "bitmap = makeStringImage(80, 80, 0xFFD000D0.toInt(), 15, 65, 96, \"e\")",
        "checkerboard = makeCheckerboardImage(80, 80, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)",
        "for (i in 0 until 4)",
        "val image = if (i and 0x01 == 1) checkerboard else bitmap",
        "i * 12",
        "i * 8",
        "image.width - i * 8",
        "image.height - i * 12",
        "SkImageFilters.Image(image, SkSamplingOptions.nearest())",
        "val dx = i * 5f",
        "val dy = i * 10f",
        "SkImageFilters.Crop(",
        "SkTileMode.kDecal",
        "SkImageFilters.Offset(dx, dy, tileInput)",
        "drawClippedImage(c, image, paint, 1f, cropRect)",
        "c.translate((image.width + MARGIN).toFloat(), 0f)",
        "SkImageFilters.Offset(-5f, -10f, null)",
        "drawClippedImage(c, bitmap, paint, 2f, cropRect)",
        "canvas.clipRect(clipRect)",
        "canvas.scale(scale, scale)",
        "canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)",
        "SkMatrix.MakeScale(scale, scale).mapRect(SkRect.Make(cropRect))",
        "strokeWidth = 2f",
        "color = SK_ColorRED",
    )
    for phrase in required_source_phrases:
        require(phrase in source, f"OffsetImageFilterGM source missing phrase: {phrase}")

    test = TEST.read_text(encoding="utf-8")
    require("class OffsetImageFilterTest" in test, "OffsetImageFilterTest class missing")
    require("TestUtils.loadReferenceBitmap(gm.name())" in test, "OffsetImageFilterTest must load reference by GM name")
    require('SimilarityTracker.updateScore("OffsetImageFilterGM", comparison.similarity)' in test, "OffsetImageFilterGM score update missing")
    require("OffsetImageFilterGM matches offsetimagefilter_png within tolerance" in test, "OffsetImageFilterGM test name mismatch")
    require("private const val THRESHOLD: Double = 84.5" in test, "OffsetImageFilterGM threshold mismatch")

    scores = CPU_SCORE.read_text(encoding="utf-8")
    require("OffsetImageFilterGM=84.515" in scores, "missing OffsetImageFilterGM historical CPU score")

    width, height, bit_depth, color_type = read_png_header(REFERENCE)
    require((width, height) == (600, 100), "offsetimagefilter.png dimensions must be 600x100")
    require(bit_depth == 16 and color_type == 6, "offsetimagefilter.png must be PNG RGBA 16-bit/color")


def require_d50_manifest_unchanged() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "D50 manifest must contain one OffsetImageFilterGM row")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 OffsetImageFilterGM status must remain expected-unsupported")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "D50 OffsetImageFilterGM strict status must remain expected-unsupported")
    require(row.get("dashboardRowId") is None, "D50 OffsetImageFilterGM must not point to a dashboard row")
    require(row.get("dashboardStatus") is None, "D50 OffsetImageFilterGM dashboard status must remain null")
    require(row.get("referenceKind") is None, "D50 OffsetImageFilterGM must not claim a reference kind")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 fallback should remain the D50 closeout reason")
    require(row.get("reason") == PREVIOUS_FALLBACK, "D50 reason should remain the D50 closeout reason")
    require(row.get("cpuRoute") == "cpu.image-filter.offset.expected-unsupported", "D50 CPU route must remain expected-unsupported")
    require(row.get("gpuRoute") == "webgpu.image-filter.offset.expected-unsupported", "D50 GPU route must remain expected-unsupported")
    require(row.get("offsetImageFilterEvidenceInherited") is False, "D50 row must not inherit offset image-filter evidence")
    require(row.get("supportClaimAddedByFor468") is False, "D50 FOR-468 support claim guard changed")
    require(row.get("skiaComparableClaimAddedByFor468") is False, "D50 FOR-468 Skia-comparable guard changed")
    route = row.get("routeDiagnostics", {})
    require(route.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 route fallback should remain unchanged")
    require("fallbackReason=none" in route.get("policy", ""), "D50 policy must keep fallbackReason=none promotion gate")
    provenance = row.get("offsetImageFilterProvenance", {})
    require(provenance.get("scene") == "OffsetImageFilterGM", "D50 provenance scene mismatch")
    require("84.515" in provenance.get("historicalSimilarity", ""), "D50 historical score provenance mismatch")
    require(provenance.get("boundedOffsetFilter") == "not claimed as supported", "D50 bounded offset filter must not be supported")


def require_for468_evidence() -> None:
    for468 = load_json(FOR468_EVIDENCE)
    row = for468.get("row", {})
    require(row.get("inventoryId") == ROW_ID, "FOR-468 inventory mismatch")
    require(row.get("status") == "expected-unsupported", "FOR-468 status mismatch")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "FOR-468 fallback mismatch")
    provenance = row.get("offsetImageFilterProvenance", {})
    require(provenance.get("kotlinSource") == rel(SOURCE), "FOR-468 Kotlin source mismatch")
    require(provenance.get("scene") == "OffsetImageFilterGM", "FOR-468 scene mismatch")
    require(provenance.get("boundedOffsetFilter") == "not claimed as supported", "FOR-468 bounded offset claim mismatch")
    require("SimpleOffsetImageFilterGM" in provenance.get("adjacentHistoricalScene", ""), "FOR-468 adjacent scene guard mismatch")
    require(row.get("nonClaims", {}).get("offsetImageFilterEvidenceInherited") is False, "FOR-468 must not inherit offset image-filter evidence")

    report = FOR468_REPORT.read_text(encoding="utf-8")
    require("`skia-gm-offsetimagefilter` est traite comme `expected-unsupported` row-specific" in report, "FOR-468 report status missing")
    require("ne sont pas herites comme support D50" in report, "FOR-468 report inheritance guard missing")
    require("OffsetImageFilterGM=84.515" in report, "FOR-468 report score missing")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-precise-refusal", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("previousFallbackReason") == PREVIOUS_FALLBACK, "previous fallback mismatch")
    require(evidence.get("fallbackReasonImproved") is True, "fallback reason must be marked improved")
    require(evidence.get("d50ManifestChanged") is False, "D50 manifest must not be changed by D51-4")

    execution = evidence.get("executionAttachment", {})
    require(execution.get("linearParent") == "FOR-460", "execution must be attached to FOR-460")
    require(execution.get("dedicatedLinearIssueCreated") is False, "dedicated Linear issue must remain false")
    require("free issue limit" in execution.get("reason", ""), "execution attachment must mention Linear issue limit")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")

    audit = evidence.get("rowSpecificAudit", {})
    require(audit.get("historicalOffsetImageFilterEvidencePromotesRow") is False, "historical OffsetImageFilterGM evidence must not promote row")
    require(audit.get("simpleOffsetImageFilterEvidenceInherited") is False, "SimpleOffsetImageFilterGM evidence must not be inherited")
    source = audit.get("offsetImageFilterGmSource", {})
    require(source.get("kotlinSource") == rel(SOURCE), "OffsetImageFilterGM source mismatch")
    require(source.get("gmName") == "offsetimagefilter", "GM name mismatch")
    require(source.get("gmDimensions") == {"width": 600, "height": 100}, "GM dimensions mismatch")
    require(source.get("background") == "black", "GM background mismatch")
    source_images = source.get("sourceImages", [])
    require(isinstance(source_images, list) and len(source_images) == 2, "OffsetImageFilterGM must list two source images")
    require(source_images[0].get("kind") == "string image", "string image source mismatch")
    require(source_images[0].get("dimensions") == {"width": 80, "height": 80}, "string image dimensions mismatch")
    require("letter e" in source_images[0].get("content", ""), "string image content mismatch")
    require(source_images[1].get("kind") == "checkerboard image", "checkerboard source mismatch")
    require(source_images[1].get("tileSizePx") == 8, "checkerboard tile size mismatch")

    cells = source.get("cells", [])
    require(isinstance(cells, list) and len(cells) == 5, "OffsetImageFilterGM must list five cells")
    require([cell.get("cell") for cell in cells] == [0, 1, 2, 3, 4], "cell order mismatch")
    require([cell.get("offset") for cell in cells] == [
        {"dx": 0, "dy": 0},
        {"dx": 5, "dy": 10},
        {"dx": 10, "dy": 20},
        {"dx": 15, "dy": 30},
        {"dx": -5, "dy": -10},
    ], "cell offsets mismatch")
    require([cell.get("cropRect") for cell in cells] == [
        "SkIRect.MakeXYWH(0, 0, 80, 80)",
        "SkIRect.MakeXYWH(12, 8, 72, 68)",
        "SkIRect.MakeXYWH(24, 16, 64, 56)",
        "SkIRect.MakeXYWH(36, 24, 56, 44)",
        "SkIRect.MakeXYWH(0, 0, 100, 100)",
    ], "cell crop rects mismatch")
    require(cells[4].get("scale") == 2 and cells[4].get("tileInput") is None, "scaled fifth cell mismatch")

    chain = source.get("filterChain", {})
    require(chain.get("tileInput") == "SkImageFilters.Image(image, SkSamplingOptions.nearest())", "tile input mismatch")
    require(chain.get("offset") == "SkImageFilters.Offset(dx, dy, tileInput)", "offset filter mismatch")
    require(chain.get("crop") == "SkImageFilters.Crop(cropRectF, SkTileMode.kDecal, offset)", "crop wrapper mismatch")
    per_cell = source.get("perCellApplication", {})
    require(per_cell.get("helper") == "drawClippedImage", "helper mismatch")
    require(per_cell.get("paintField") == "paint.imageFilter", "paint field mismatch")
    require(per_cell.get("clipRect") == "SkRect.MakeIWH(image.width, image.height)", "clip rect mismatch")
    require("drawImage" in per_cell.get("draw", ""), "draw call mismatch")
    require("red 2-pixel" in per_cell.get("debugOverlay", ""), "debug overlay mismatch")
    require(per_cell.get("translationAfterFirstFourCellsPx") == 92, "translation mismatch")

    blockers = audit.get("whyNotPromotable", [])
    require(any("No D51 row-specific WebGPU dashboard artifact" in item for item in blockers), "missing WebGPU blocker")
    require(any("No D51 diff/stat payload" in item for item in blockers), "missing diff/stat blocker")
    require(any("fallbackReason=none" in item for item in blockers), "missing route diagnostics blocker")
    require(any("SimpleOffsetImageFilterGM evidence" in item and "scaled 2x cell" in item for item in blockers), "missing SimpleOffsetImageFilterGM non-inheritance blocker")
    adjacent = audit.get("adjacentEvidenceNotInherited", [])
    for required in ("SimpleOffsetImageFilterGM", "crop-image-filter-nonnull-prepass evidence", "simple-offsetimagefilter residual audits", "image-filter DAG V2 evidence", "ImageGM", "ImageSourceGM"):
        require(required in adjacent, f"missing adjacent non-inheritance item: {required}")

    artifacts = evidence.get("artifacts", {})
    reference = artifacts.get("reference", {})
    require(reference.get("path") == rel(REFERENCE), "reference path mismatch")
    require(reference.get("dimensions") == {"width": 600, "height": 100}, "reference dimensions mismatch")
    require(reference.get("format") == "PNG 16-bit/color RGBA", "reference format mismatch")
    require(reference.get("promotableAsDashboardReference") is False, "historical reference must not be promotable alone")
    cpu = artifacts.get("cpu", {})
    require(cpu.get("test") == rel(TEST), "CPU test path mismatch")
    require(cpu.get("historicalSimilarity") == {"OffsetImageFilterGM": 84.515}, "CPU score mismatch")
    require(cpu.get("promotableAsDashboardCpuArtifact") is False, "historical CPU score must not be promotable alone")
    require(artifacts.get("webgpu", {}).get("status") == "missing", "WebGPU status must remain missing")
    require(artifacts.get("webgpu", {}).get("required") is True, "WebGPU artifact must be required")
    require(artifacts.get("diffStats", {}).get("status") == "not-computed", "diff/stat must not be computed")

    routes = evidence.get("routeDiagnostics", {})
    require(routes.get("cpu") == "cpu.image-filter.offset.historical-cpu-similarity-only", "CPU route mismatch")
    require(routes.get("gpu") == "webgpu.image-filter.offset.expected-unsupported", "GPU route mismatch")
    require(routes.get("fallbackReason") == FALLBACK_REASON, "route fallback mismatch")
    require("offset image-filter crop/prepass cells" in routes.get("policy", ""), "route policy must name OffsetImageFilterGM scope")
    require("scaled 2x cell" in routes.get("policy", ""), "route policy must mention scaled cell")

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
        "`skia-gm-offsetimagefilter` reste `expected-unsupported`",
        "Execution attachment: FOR-460",
        FALLBACK_REASON,
        "`skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png`\nexiste en 600x100",
        "score CPU historique est `OffsetImageFilterGM=84.515`",
        "5 cellules horizontales sur fond noir",
        "`SkImageFilters.Image(image, SkSamplingOptions.nearest())`",
        "`SkImageFilters.Offset(dx, dy, tileInput)`",
        "`SkImageFilters.Crop(cropRectF, SkTileMode.kDecal, ...)`",
        "`(0,0)`, `(5,10)`, `(10,20)` et `(15,30)`",
        "La cinquieme cellule applique\n`Offset(-5,-10,null)` avec crop 100x100 et draw scale 2x",
        "pas d'artefact WebGPU row-specific dashboard",
        "pas de diff/stat\nD51",
        "pas de diagnostics de route avec `fallbackReason=none`",
        "Les preuves image-filter voisines ne couvrent pas ce chemin",
        "Aucun support image-filter DAG large, crop image-filter large,\n  picture prepass, arbitrary layer prepass ou pipeline couleur global",
        "Aucun support WebGPU pour `OffsetImageFilterGM`",
        "Aucun heritage depuis `SimpleOffsetImageFilterGM`",
        "Aucun gain de score support et aucun gain Skia-comparable",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_existing_inputs()
    require_d50_manifest_unchanged()
    require_for468_evidence()
    require_evidence(evidence)
    require_report()
    print(f"{TICKET} validation passed: {ROW_ID}=expected-unsupported fallback={FALLBACK_REASON}")


if __name__ == "__main__":
    main()
