#!/usr/bin/env python3
"""Validate D51-1 DrawMiniBitmapRect row-specific refusal evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-1"
ROW_ID = "skia-gm-drawminibitmaprect"
PREVIOUS_FALLBACK = "bitmap.drawminibitmaprect.row-specific-artifacts-required"
FALLBACK_REASON = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
FORBIDDEN_M66_ROW = "m66-bitmap-rect-nearest-skia"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
M66_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"
M66_STATS = ROOT / "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/stats.json"
CPU_SCORE = ROOT / "skia-integration-tests/test-similarity-scores.properties"
REFERENCE_PATHS = (
    ROOT / "skia-integration-tests/src/test/resources/original-888/drawminibitmaprect.png",
    ROOT / "skia-integration-tests/src/test/resources/original-888/drawminibitmaprect_aa.png",
)
TEST_PATHS = (
    ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/DrawMiniBitmapRectTest.kt",
    ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/DrawMiniBitmapRectAaTest.kt",
)
EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json",
    "scripts/validate_d51_drawminibitmaprect_row_specific_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
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
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope(evidence: dict[str, Any]) -> None:
    require(set(evidence.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expectedChangedFiles mismatch")
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_CHANGED_FILES)
    require(not unexpected, f"unexpected local diffs: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard/source inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_existing_inputs() -> None:
    for path in REFERENCE_PATHS + TEST_PATHS:
        require(path.is_file(), f"missing row-specific audit input: {rel(path)}")
    scores = CPU_SCORE.read_text(encoding="utf-8")
    require("DrawMiniBitmapRectGM=93.95856857299805" in scores, "missing non-AA historical CPU score")
    require("DrawMiniBitmapRectAaGM=92.79909133911133" in scores, "missing AA historical CPU score")
    for path in TEST_PATHS:
        text = path.read_text(encoding="utf-8")
        require("@Disabled" in text, f"{rel(path)} must remain a disabled stress test")
        require("SLOW.GM_STRESS" in text, f"{rel(path)} must keep slow stress-test reason")


def require_d50_manifest_unchanged() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "D50 manifest must contain one drawminibitmaprect row")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 drawminibitmaprect status must remain expected-unsupported")
    require(row.get("dashboardRowId") is None, "D50 drawminibitmaprect must not point to a dashboard row")
    require(row.get("fallbackReason") == PREVIOUS_FALLBACK, "D50 fallback should remain the D50 closeout reason")
    require(row.get("drawbitmaprectEvidenceInherited") is False, "D50 row must not inherit drawbitmaprect evidence")


def require_m66_is_not_row_specific() -> None:
    m66 = load_json(M66_MANIFEST)
    scenes = m66.get("scenes")
    require(isinstance(scenes, list), "M66 scenes must be a list")
    matches = [row for row in scenes if isinstance(row, dict) and row.get("id") == FORBIDDEN_M66_ROW]
    require(len(matches) == 1, "M66 nearest bitmap row must exist exactly once")
    row = matches[0]
    require(row.get("inventoryId") == "skia-gm-drawbitmaprect", "M66 row must belong to drawbitmaprect, not drawminibitmaprect")
    require(row.get("cpuRoute") == "cpu.image-rect.strict-nearest", "M66 CPU route must remain strict-nearest")
    require(row.get("gpuRoute") == "webgpu.image-rect.strict-nearest", "M66 GPU route must remain strict-nearest")
    stats = load_json(M66_STATS)
    require(stats.get("dimensions") == {"width": 64, "height": 64}, "M66 stats must be 64x64 smoke evidence")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-precise-refusal", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status mismatch")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback mismatch")
    require(evidence.get("previousFallbackReason") == PREVIOUS_FALLBACK, "previous fallback mismatch")
    require(evidence.get("fallbackReasonImproved") is True, "fallback reason must be marked improved")
    require(evidence.get("d50ManifestChanged") is False, "D50 manifest must not be changed by D51-1")
    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")

    audit = evidence.get("rowSpecificAudit", {})
    require(audit.get("rowSpecificEvidenceInheritedFromM66") is False, "M66 evidence must not be inherited")
    require(audit.get("forbiddenInheritedDashboardRowId") == FORBIDDEN_M66_ROW, "forbidden M66 row mismatch")
    m66_comparison = audit.get("m66Comparison", {})
    require(m66_comparison.get("inventoryId") == "skia-gm-drawbitmaprect", "M66 comparison inventory mismatch")
    require(m66_comparison.get("sceneId") == "bitmap-rect-nearest", "M66 comparison scene mismatch")
    require("not the DrawMiniBitmapRectGM stress grid" in m66_comparison.get("whyNotReusable", ""), "M66 comparison must explain non-reuse")
    source = audit.get("drawMiniBitmapRectSource", {})
    require(source.get("gmDimensions") == {"width": 1024, "height": 1024}, "GM dimensions mismatch")
    require(source.get("atlasDimensions") == {"width": 2048, "height": 2048}, "atlas dimensions mismatch")
    require(source.get("srcRectConstraint") == "SrcRectConstraint.kFast", "src rect constraint mismatch")

    artifacts = evidence.get("artifacts", {})
    require(artifacts.get("reference", {}).get("status") == "available-historical-skia-integration-reference", "reference status mismatch")
    require(artifacts.get("cpu", {}).get("status") == "available-historical-disabled-stress-test", "CPU status mismatch")
    require(artifacts.get("webgpu", {}).get("status") == "missing", "WebGPU status must remain missing")
    require(artifacts.get("webgpu", {}).get("required") is True, "WebGPU artifact must be required")
    require(artifacts.get("diffStats", {}).get("status") == "not-computed", "diff/stat must not be computed")
    require(artifacts.get("reference", {}).get("promotableAsDashboardReference") is False, "historical references must not be promotable alone")
    require(artifacts.get("cpu", {}).get("promotableAsDashboardCpuArtifact") is False, "historical CPU score must not be promotable alone")

    routes = evidence.get("routeDiagnostics", {})
    require(routes.get("gpu") == "webgpu.image-rect.drawminibitmaprect.expected-unsupported", "GPU route mismatch")
    require(routes.get("fallbackReason") == FALLBACK_REASON, "route fallback mismatch")

    non_claims = evidence.get("nonClaims", {})
    for key, value in non_claims.items():
        require(value is False, f"{key} must remain false")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "`skia-gm-drawminibitmaprect` reste `expected-unsupported`",
        FALLBACK_REASON,
        "ne peut donc pas heriter de `m66-bitmap-rect-nearest-skia`",
        "GM 1024x1024",
        "atlas radial 2048x2048",
        "Aucun support WebGPU pour `DrawMiniBitmapRectGM` n'est revendique",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_existing_inputs()
    require_d50_manifest_unchanged()
    require_m66_is_not_row_specific()
    require_evidence(evidence)
    require_report()
    print(f"{TICKET} validation passed: {ROW_ID}=expected-unsupported fallback={FALLBACK_REASON}")


if __name__ == "__main__":
    main()
