#!/usr/bin/env python3
"""Validate D51-5 PathFillGM row-specific refusal evidence."""

from __future__ import annotations

import json
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-5"
ROW_ID = "skia-gm-pathfill"
PREVIOUS_FALLBACK = "path-aa.fill.row-specific-artifacts-required"
FALLBACK_REASON = "path-aa.fill.multi-shape-conic-cubic-transform-webgpu-artifacts-required"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-5-pathfill-row-specific-evidence.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
FOR469_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md"
FOR469_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json"
SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/PathFillGM.kt"
TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/PathFillTest.kt"
CROSS_BACKEND_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/PathFillCrossBackendTest.kt"
REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/pathfill.png"
CPU_SCORE = ROOT / "skia-integration-tests/test-similarity-scores.properties"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-5-pathfill-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json",
    "scripts/validate_d51_pathfill_row_specific_evidence.py",
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
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D51-5 files, got: {sorted(changed)}")
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
    for path in (SOURCE, TEST, CROSS_BACKEND_TEST, REFERENCE, CPU_SCORE, FOR469_REPORT, FOR469_EVIDENCE):
        require(path.is_file(), f"missing row-specific audit input: {rel(path)}")

    source = SOURCE.read_text(encoding="utf-8")
    required_source_phrases = (
        'override fun getName(): String = "pathfill"',
        "override fun getISize(): SkISize = SkISize.Make(640, 480)",
        "SkPaint().apply { isAntiAlias = true }",
        "makeFrame()",
        "makeTriangle()",
        "makeRect()",
        "makeOval()",
        "makeSawtooth(32)",
        "makeStar(5)",
        "makeStar(13)",
        "makeLine()",
        "makeHouse()",
        "makeSawtooth(3)",
        "SkPathUtils.FillPathWithPaint(src, stroke, dst)",
        "SkPathBuilder().addRect(r).offset(10f, 0f)",
        "SkPathBuilder().addOval(r).offset(10f, 0f)",
        "for (i in 0 until teeth)",
        "pb.lineTo(x, y - dy)",
        "pb.lineTo(x, y + dy)",
        "moveTo(30f, 30f).lineTo(120f, 40f).close()",
        "lineTo(150f, 30f).lineTo(300f, 40f).close()",
        "20-point polygon outer ring, 10-point polyline cavity",
        "fInfoPath = makeInfo()",
        "fAccessibilityPath = makeAccessibility()",
        "fVisualizerPath = makeVisualizer()",
        "c.scale(0.300000011920929f, 0.300000011920929f)",
        "c.translate(50f, 50f)",
        "c.scale(2f, 2f)",
        "c.translate(5f, 15f)",
        "c.scale(0.5f, 0.5f)",
        "c.translate(5f, 50f)",
        "pb.cubicTo",
        "pb.conicTo",
    )
    for phrase in required_source_phrases:
        require(phrase in source, f"PathFillGM source missing phrase: {phrase}")

    test = TEST.read_text(encoding="utf-8")
    require("class PathFillTest" in test, "PathFillTest class missing")
    require("TestUtils.loadReferenceBitmap(gm.name())" in test, "PathFillTest must load reference by GM name")
    require('SimilarityTracker.updateScore("PathFillGM", comparison.similarity)' in test, "PathFillGM score update missing")
    require("PathFillGM matches pathfill_png within tolerance" in test, "PathFillGM test name mismatch")
    require("comparison.similarity >= 0.0" in test, "PathFillGM historical floor must remain 0.0")

    cross_backend = CROSS_BACKEND_TEST.read_text(encoding="utf-8")
    require("class PathFillCrossBackendTest" in cross_backend, "PathFillCrossBackendTest class missing")
    require("gm = PathFillGM()" in cross_backend, "cross-backend test must use PathFillGM")
    require("rasterFloor = 0.0" in cross_backend, "cross-backend raster floor must remain 0.0")
    require("gpuFloor = 0.0" in cross_backend, "cross-backend GPU floor must remain 0.0")

    scores = CPU_SCORE.read_text(encoding="utf-8")
    require("PathFillGM=97.89453125" in scores, "missing PathFillGM historical CPU score")

    width, height, bit_depth, color_type = read_png_header(REFERENCE)
    require((width, height) == (640, 480), "pathfill.png dimensions must be 640x480")
    require(bit_depth == 16 and color_type == 6, "pathfill.png must be PNG RGBA 16-bit/color")


def require_d50_manifest_unchanged() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "D50 manifest must contain one PathFillGM row")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 PathFillGM status must remain expected-unsupported")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "D50 PathFillGM strict status must remain expected-unsupported")
    require(row.get("dashboardRowId") is None, "D50 PathFillGM must not point to a dashboard row")
    require(row.get("dashboardStatus") is None, "D50 PathFillGM dashboard status must remain null")
    require(row.get("referenceKind") is None, "D50 PathFillGM must not claim a reference kind")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 fallback should remain the D50 closeout reason")
    require(row.get("reason") == PREVIOUS_FALLBACK, "D50 reason should remain the D50 closeout reason")
    require(row.get("cpuRoute") == "cpu.path-aa.fill.expected-unsupported", "D50 CPU route must remain expected-unsupported")
    require(row.get("gpuRoute") == "webgpu.path-aa.fill.expected-unsupported", "D50 GPU route must remain expected-unsupported")
    require(row.get("pathFillEvidenceInherited") is False, "D50 row must not inherit pathfill evidence")
    require(row.get("supportClaimAddedByFor469") is False, "D50 FOR-469 support claim guard changed")
    require(row.get("skiaComparableClaimAddedByFor469") is False, "D50 FOR-469 Skia-comparable guard changed")
    route = row.get("routeDiagnostics", {})
    require(route.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 route fallback should remain unchanged")
    require("fallbackReason=none" in route.get("policy", ""), "D50 policy must keep fallbackReason=none promotion gate")
    provenance = row.get("pathFillProvenance", {})
    require(provenance.get("scene") == "PathFillGM", "D50 provenance scene mismatch")
    require(provenance.get("kotlinSource") == rel(SOURCE), "D50 Kotlin source mismatch")
    require(provenance.get("upstreamSource") == "gm/pathfill.cpp", "D50 upstream source mismatch")
    require(provenance.get("boundedFillUnderEdgeBudget") == "not claimed as supported", "D50 bounded fill must not be supported")
    require(provenance.get("fillRule") == "not claimed as supported", "D50 fill rule must not be supported")


def require_for469_evidence() -> None:
    for469 = load_json(FOR469_EVIDENCE)
    row = for469.get("row", {})
    require(row.get("inventoryId") == ROW_ID, "FOR-469 inventory mismatch")
    require(row.get("status") == "expected-unsupported", "FOR-469 status mismatch")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "FOR-469 fallback mismatch")
    provenance = row.get("pathFillProvenance", {})
    require(provenance.get("kotlinSource") == rel(SOURCE), "FOR-469 Kotlin source mismatch")
    require(provenance.get("scene") == "PathFillGM", "FOR-469 scene mismatch")
    require(provenance.get("boundedFillUnderEdgeBudget") == "not claimed as supported", "FOR-469 bounded fill claim mismatch")
    require(row.get("nonClaims", {}).get("pathFillEvidenceInherited") is False, "FOR-469 must not inherit pathfill evidence")

    report = FOR469_REPORT.read_text(encoding="utf-8")
    require("`skia-gm-pathfill` est traite comme `expected-unsupported` row-specific" in report, "FOR-469 report status missing")
    require("ne sont pas heritees comme support D50" in report, "FOR-469 report inheritance guard missing")
    require("path-aa.fill.row-specific-artifacts-required" in report, "FOR-469 report fallback missing")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-precise-refusal", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("previousFallbackReason") == PREVIOUS_FALLBACK, "previous fallback mismatch")
    require(evidence.get("fallbackReasonImproved") is True, "fallback reason must be marked improved")
    require(evidence.get("d50ManifestChanged") is False, "D50 manifest must not be changed by D51-5")

    execution = evidence.get("executionAttachment", {})
    require(execution.get("linearParent") == "FOR-460", "execution must be attached to FOR-460")
    require(execution.get("dedicatedLinearIssueCreated") is False, "dedicated Linear issue must remain false")
    require("free issue limit" in execution.get("reason", ""), "execution attachment must mention Linear issue limit")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")

    audit = evidence.get("rowSpecificAudit", {})
    require(audit.get("historicalPathFillEvidencePromotesRow") is False, "historical PathFillGM evidence must not promote row")
    require(audit.get("historicalPathSceneEvidenceInherited") is False, "historical path scene evidence must not be inherited")
    source = audit.get("pathFillGmSource", {})
    require(source.get("kotlinSource") == rel(SOURCE), "PathFillGM source mismatch")
    require(source.get("upstreamSource") == "gm/pathfill.cpp", "upstream source mismatch")
    require(source.get("gmName") == "pathfill", "GM name mismatch")
    require(source.get("gmDimensions") == {"width": 640, "height": 480}, "GM dimensions mismatch")
    require(source.get("paint") == {"antiAlias": True, "style": "fill"}, "paint audit mismatch")

    paths = source.get("stackedPaths", [])
    require(isinstance(paths, list) and len(paths) == 10, "PathFillGM must list ten stacked paths")
    require([path.get("index") for path in paths] == list(range(10)), "stacked path order mismatch")
    require([path.get("name") for path in paths] == [
        "frame",
        "triangle",
        "rect",
        "oval",
        "sawtooth-32",
        "star-5",
        "star-13",
        "line-degenerate-line",
        "house",
        "sawtooth-3",
    ], "stacked path names mismatch")
    require(paths[0].get("dy") == 15, "frame dy mismatch")
    require(paths[5].get("dy") == 48 and paths[6].get("dy") == 48, "star dy mismatch")
    require("stroke" in paths[0].get("description", ""), "frame stroke-expanded description missing")
    require("degenerate" in paths[7].get("description", ""), "degenerate line description missing")
    require("cavity" in paths[8].get("description", ""), "house cavity description missing")

    pictograms = source.get("pictogramPaths", [])
    require(isinstance(pictograms, list) and len(pictograms) == 3, "PathFillGM must list three pictograms")
    require([item.get("name") for item in pictograms] == ["info", "accessibility", "visualizer"], "pictogram order mismatch")
    require(pictograms[0].get("curveTypes") == ["cubic"], "info pictogram must use cubic curves")
    require(pictograms[1].get("curveTypes") == ["cubic"], "accessibility pictogram must use cubic curves")
    require(pictograms[2].get("curveTypes") == ["conic"], "visualizer pictogram must use conic curves")
    require("scale(0.300000011920929" in pictograms[0].get("transform", ""), "info transform mismatch")
    require("scale(2, 2)" in pictograms[1].get("transform", ""), "accessibility transform mismatch")
    require("scale(0.5, 0.5)" in pictograms[2].get("transform", ""), "visualizer transform mismatch")

    coverage = source.get("combinedCoverageFeatures", [])
    for required in (
        "anti-aliased path fill",
        "multi-shape path stack",
        "stroke-expanded filled frame path",
        "degenerate line fill inputs",
        "cubic pictogram curves",
        "conic pictogram curves",
        "successive scale and translate transforms",
    ):
        require(required in coverage, f"missing coverage feature: {required}")

    blockers = audit.get("whyNotPromotable", [])
    require(any("No D51 row-specific WebGPU dashboard artifact" in item for item in blockers), "missing WebGPU blocker")
    require(any("No D51 diff/stat payload" in item for item in blockers), "missing diff/stat blocker")
    require(any("fallbackReason=none" in item for item in blockers), "missing route diagnostics blocker")
    require(any("rasterFloor=0.0 and gpuFloor=0.0" in item for item in blockers), "missing floor-0 blocker")
    require(any("historical path scene evidence is not inherited" in item for item in blockers), "missing path non-inheritance blocker")
    adjacent = audit.get("adjacentEvidenceNotInherited", [])
    for required in (
        "broad Path AA evidence",
        "stroke evidence",
        "cap/join/dash evidence",
        "convex path evidence",
        "edge-budget evidence",
        "historical path scenes",
        "pictogram coverage evidence",
        "broad conic/cubic coverage evidence",
    ):
        require(required in adjacent, f"missing adjacent non-inheritance item: {required}")

    artifacts = evidence.get("artifacts", {})
    reference = artifacts.get("reference", {})
    require(reference.get("path") == rel(REFERENCE), "reference path mismatch")
    require(reference.get("dimensions") == {"width": 640, "height": 480}, "reference dimensions mismatch")
    require(reference.get("format") == "PNG 16-bit/color RGBA", "reference format mismatch")
    require(reference.get("promotableAsDashboardReference") is False, "historical reference must not be promotable")

    cpu = artifacts.get("cpu", {})
    require(cpu.get("test") == rel(TEST), "CPU test path mismatch")
    require(cpu.get("historicalSimilarity") == {"PathFillGM": 97.89453125}, "CPU historical score mismatch")
    require(cpu.get("promotableAsDashboardCpuArtifact") is False, "historical CPU score must not be promotable")

    cross = artifacts.get("crossBackend", {})
    require(cross.get("test") == rel(CROSS_BACKEND_TEST), "cross-backend test path mismatch")
    require(cross.get("rasterFloor") == 0.0, "cross-backend raster floor mismatch")
    require(cross.get("gpuFloor") == 0.0, "cross-backend GPU floor mismatch")
    require(cross.get("promotableAsDashboardGpuArtifact") is False, "floor-0 cross-backend evidence must not promote")

    webgpu = artifacts.get("webgpu", {})
    require(webgpu.get("status") == "missing", "WebGPU artifact must remain missing")
    require(webgpu.get("required") is True, "WebGPU artifact must be required for promotion")
    require(webgpu.get("route") == "webgpu.path-aa.fill.expected-unsupported", "WebGPU route mismatch")
    require(webgpu.get("artifact") == "not-generated", "WebGPU artifact should not be generated")

    diff = artifacts.get("diffStats", {})
    require(diff.get("status") == "not-computed", "diff/stat must not be computed")
    require(diff.get("required") is True, "diff/stat must be required for promotion")

    route = evidence.get("routeDiagnostics", {})
    require(route.get("cpu") == "cpu.path-aa.fill.historical-cpu-similarity-only", "CPU route mismatch")
    require(route.get("gpu") == "webgpu.path-aa.fill.expected-unsupported", "GPU route mismatch")
    require(route.get("fallbackReason") == FALLBACK_REASON, "route fallback mismatch")
    require("fallbackReason=none" in route.get("policy", ""), "route policy must keep fallbackReason=none promotion gate")
    require("conic/cubic pictograms" in route.get("policy", ""), "route policy must mention conic/cubic pictograms")

    guard = evidence.get("d50Guard", {})
    require(guard.get("manifest") == rel(LOT1_MANIFEST), "D50 guard manifest mismatch")
    require(guard.get("status") == "expected-unsupported", "D50 guard status mismatch")
    require(guard.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 guard fallback mismatch")
    require(guard.get("strictDashboardStatus") == "expected-unsupported", "D50 guard strict status mismatch")
    require(guard.get("dashboardRowId") is None, "D50 guard dashboard row must stay null")

    non_claims = evidence.get("nonClaims", {})
    required_false_non_claims = (
        "supportClaimAddedByD51_5",
        "skiaComparableClaimAddedByD51_5",
        "dashboardRowAddedByD51_5",
        "d50ManifestChanged",
        "resultsJsonChanged",
        "scenesJsonChanged",
        "defaultRenderingChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionCodeChanged",
        "wgslProductionChanged",
        "wgsl4kChanged",
        "upstreamSourceChanged",
        "skiaIntegrationTestsChanged",
        "webgpuPathFillGmSupportClaim",
        "broadPathAaSupportClaim",
        "strokeSupportClaim",
        "capJoinDashSupportClaim",
        "convexPathSupportClaim",
        "edgeBudgetSupportClaim",
        "pictogramCoverageSupportClaim",
        "broadConicCubicCoverageSupportClaim",
        "historicalPathSceneInheritanceClaim",
    )
    for key in required_false_non_claims:
        require(non_claims.get(key) is False, f"non-claim must be false: {key}")

    require(FALLBACK_REASON not in PREVIOUS_FALLBACK, "D51 fallback must be more specific than D50 fallback")
    require(evidence.get("validationCommands") == [
        "rtk python3 scripts/validate_d51_pathfill_row_specific_evidence.py",
        "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json",
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d51-pathfill-pycache python3 -m py_compile scripts/validate_d51_pathfill_row_specific_evidence.py",
        "rtk git diff --check",
    ], "validation command list mismatch")


def require_report(evidence: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report file: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    required_phrases = (
        "`skia-gm-pathfill` reste `expected-unsupported`",
        rel(SOURCE),
        rel(REFERENCE),
        "640x480 au format PNG 16-bit/color RGBA",
        "PathFillGM=97.89453125",
        FALLBACK_REASON,
        PREVIOUS_FALLBACK,
        "`rasterFloor = 0.0` et `gpuFloor = 0.0`",
        "`fallbackReason=none`",
        "Aucun support Path AA large",
        "Aucun support WebGPU pour `PathFillGM`",
    )
    for phrase in required_phrases:
        require(phrase in report, f"report missing phrase: {phrase}")
    require(evidence.get("fallbackReason") in report, "report must include evidence fallback")
    require("Ne pas" not in report, "report should state results, not ticket instructions")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_existing_inputs()
    require_d50_manifest_unchanged()
    require_for469_evidence()
    require_evidence(evidence)
    require_report(evidence)
    print(f"{TICKET} PathFillGM row-specific evidence validation passed")


if __name__ == "__main__":
    main()
