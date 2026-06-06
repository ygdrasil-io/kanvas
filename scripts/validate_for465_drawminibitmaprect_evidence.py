#!/usr/bin/env python3
"""Validate the FOR-465 drawminibitmaprect row-specific refusal evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
LINEAR = "FOR-465"
ROW_ID = "skia-gm-drawminibitmaprect"
FALLBACK_REASON = "bitmap.drawminibitmaprect.row-specific-artifacts-required"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
LOT1_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md"
ROW_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json"
ROW_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-465-drawminibitmaprect-evidence.md"

EXPECTED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md",
    "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md",
    "reports/wgsl-pipeline/2026-06-06-for-465-drawminibitmaprect-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json",
    "scripts/validate_for462_d50_lot1_dashboard_integration.py",
    "scripts/validate_for465_drawminibitmaprect_evidence.py",
    "scripts/validate_for466_skia_gm_image_evidence.py",
    "scripts/validate_for467_skia_gm_imagesource_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
    "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)


def fail(message: str) -> None:
    raise SystemExit(f"{LINEAR} validation failed: {message}")


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


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_FILES)
    require(not unexpected, f"unexpected local diffs: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard/source inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_manifest() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest["statusCounts"] == {"diagnostic-only": 2, "expected-unsupported": 3, "supported": 7}, "manifest status counts mismatch")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "manifest rows must be a list")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "drawminibitmaprect row must appear exactly once")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "drawminibitmaprect must be expected-unsupported")
    require(row.get("dashboardRowId") is None, "drawminibitmaprect must not point to a dashboard row")
    require(row.get("dashboardStatus") is None, "drawminibitmaprect must not claim an active dashboard status")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "drawminibitmaprect strict dashboard status mismatch")
    require(row.get("referenceKind") is None, "drawminibitmaprect must not claim a reference kind")
    require(row.get("fallbackReason") == FALLBACK_REASON, "drawminibitmaprect fallback mismatch")
    require(row.get("supportClaimAddedByFor465") is False, "FOR-465 must not add support")
    require(row.get("skiaComparableClaimAddedByFor465") is False, "FOR-465 must not add Skia-comparable claim")
    require(row.get("drawbitmaprectEvidenceInherited") is False, "drawbitmaprect evidence must not be inherited")
    non_claims = manifest.get("nonClaims", {})
    require(non_claims.get("dashboardRowsAddedByFor465") == 0, "FOR-465 must add 0 dashboard rows")
    require(non_claims.get("supportClaimsAddedByFor465") == 0, "FOR-465 must add 0 support claims")
    require(non_claims.get("skiaComparableClaimsAddedByFor465") == 0, "FOR-465 must add 0 Skia-comparable claims")
    require(non_claims.get("thresholdChanged") is False, "threshold must not change")
    require(non_claims.get("scoringChanged") is False, "scoring must not change")
    require(non_claims.get("fallbackPolicyChanged") is False, "fallback policy must not change")
    require(non_claims.get("pipelineKeyChanged") is False, "PipelineKey must not change")
    require(non_claims.get("productionCodeChanged") is False, "production code must not change")
    require(non_claims.get("wgslProductionChanged") is False, "WGSL production must not change")


def require_row_evidence() -> None:
    evidence = load_json(ROW_EVIDENCE)
    require(evidence.get("linear") == LINEAR, "row evidence linear mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-no-support-claim", "row evidence classification mismatch")
    row = evidence.get("row")
    require(isinstance(row, dict), "row evidence must contain row object")
    require(row.get("inventoryId") == ROW_ID, "row evidence inventory mismatch")
    require(row.get("status") == "expected-unsupported", "row evidence status mismatch")
    require(row.get("fallbackReason") == FALLBACK_REASON, "row evidence fallback mismatch")
    require(row.get("reference", {}).get("status") == "not-generated", "reference must remain not-generated")
    require(row.get("cpu", {}).get("status") == "expected-unsupported", "CPU must remain expected-unsupported")
    require(row.get("gpu", {}).get("status") == "expected-unsupported", "GPU must remain expected-unsupported")
    require(row.get("diffStats", {}).get("status") == "not-computed", "diff/stat must remain not-computed")
    require(row.get("nonClaims", {}).get("drawbitmaprectEvidenceInherited") is False, "drawbitmaprect evidence must not be inherited")
    require(evidence.get("scoreImpact", {}).get("supportScoreIncreased") is False, "support score must not increase")


def require_reports() -> None:
    lot_report = LOT1_REPORT.read_text(encoding="utf-8")
    row_report = ROW_REPORT.read_text(encoding="utf-8")
    for required in (
        "FOR-465 ajoute 0 ligne dashboard",
        "Le score de support ne monte pas",
        "`skia-gm-drawminibitmaprect` n'herite pas",
    ):
        require(required in lot_report, f"lot report missing: {required}")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "n'est pas heritee",
        "score de support ne monte pas",
        "0 support ajoute",
    ):
        require(required in row_report, f"row report missing: {required}")


def main() -> None:
    require_scope()
    require_manifest()
    require_row_evidence()
    require_reports()
    print(f"{LINEAR} validation passed: {ROW_ID}=expected-unsupported supportScoreIncreased=false")


if __name__ == "__main__":
    main()
