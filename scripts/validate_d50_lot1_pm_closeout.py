#!/usr/bin/env python3
"""Validate the D50 lot 1 PM closeout report."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-lot1-pm-closeout.md"
SUMMARY = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json"
MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md",
    "reports/wgsl-pipeline/2026-06-06-d50-lot1-pm-closeout.md",
    "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json",
    "scripts/validate_for462_d50_lot1_dashboard_integration.py",
    "scripts/validate_for465_drawminibitmaprect_evidence.py",
    "scripts/validate_for466_skia_gm_image_evidence.py",
    "scripts/validate_for467_skia_gm_imagesource_evidence.py",
    "scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
    "scripts/validate_for469_skia_gm_pathfill_evidence.py",
    "scripts/validate_d50_lot1_pm_closeout.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/results.json",
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "gpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
)
EXPECTED_STATUS_COUNTS = {
    "diagnostic-only": 0,
    "expected-unsupported": 5,
    "supported": 7,
}
EXPECTED_DASHBOARD_COUNTERS = {
    "total": 93,
    "pass": 70,
    "expectedUnsupported": 23,
    "trackedGap": 0,
    "fail": 0,
}
EXPECTED_TICKETS = {
    "FOR-461": ("1553", "e5a9be67ee72592c5c0611ea865b712256fa0535"),
    "FOR-462": ("1554", "48094aa1a90489f4d6b245ea95aed93157903777"),
    "FOR-464": ("1555", "c34246afc367588c4c621d6b5d86baf205d2055e"),
    "FOR-465": ("1556", "9079020cef4e93f28d07c633aa8182860e375ee9"),
    "FOR-466": ("1557", "dcf7deba8e7b5004a90d0ed62578fe620a83e0ea"),
    "FOR-467": ("1558", "9f754d8c50d1fd570cb4ab03db856960282464cf"),
    "FOR-468": ("1559", "6d2f49cb7f8c77a710940cee48b80c14f126857f"),
    "FOR-469": ("1560", "fd3a54e89b319768311a03ffdbcce8e714a214c6"),
}
EXPECTED_REFUSALS = {
    "skia-gm-drawminibitmaprect": "bitmap.drawminibitmaprect.row-specific-artifacts-required",
    "skia-gm-image": "image.imagegm.row-specific-artifacts-required",
    "skia-gm-imagesource": "image.imagesource.row-specific-artifacts-required",
    "skia-gm-offsetimagefilter": "image-filter.offset.row-specific-artifacts-required",
    "skia-gm-pathfill": "path-aa.fill.row-specific-artifacts-required",
}
EXPECTED_NON_CLAIMS = (
    "newRenderingSupportAddedByD50_9",
    "dashboardActiveRowsChangedByD50_9",
    "skiaComparableClaimAddedByD50_9",
    "visualSupportAbove50PercentIncreaseClaim",
    "thresholdChanged",
    "scoringChanged",
    "fallbackPolicyChanged",
    "pipelineKeyChanged",
    "productionCodeChanged",
    "wgslProductionChanged",
    "upstreamSourceChanged",
    "broadSkiaGmParityClaim",
    "broadImageSupportClaim",
    "broadImageFilterDagSupportClaim",
    "broadPathAaSupportClaim",
)


def fail(message: str) -> None:
    raise SystemExit(f"D50 lot 1 PM closeout validation failed: {message}")


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


def require_scope(summary: dict[str, Any]) -> None:
    require(set(summary.get("expectedChangedFiles", [])) == EXPECTED_CHANGED_FILES, "expected changed files list mismatch")
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_CHANGED_FILES)
    require(not unexpected, f"unexpected local diffs: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden source/dashboard inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_manifest() -> None:
    manifest = load_json(MANIFEST)
    require(manifest.get("statusCounts") == EXPECTED_STATUS_COUNTS, "lot 1 manifest status counts mismatch")
    after = manifest.get("afterCounters", {})
    require(after.get("selectedRows") == 93, "dashboard selected row count mismatch")
    require(after.get("supportedRows") == 70, "dashboard supported row count mismatch")
    require(after.get("expectedUnsupportedRows") == 23, "dashboard expected-unsupported count mismatch")
    require(after.get("diagnosticOnlyRows") == 0, "dashboard diagnostic-only count mismatch")
    rows = manifest.get("rows")
    require(isinstance(rows, list) and len(rows) == 12, "lot 1 must contain 12 rows")
    refusal_rows = {
        row.get("inventoryId"): row
        for row in rows
        if isinstance(row, dict) and row.get("inventoryId") in EXPECTED_REFUSALS
    }
    require(set(refusal_rows) == set(EXPECTED_REFUSALS), "stable refusal row set mismatch")
    for inventory_id, fallback in EXPECTED_REFUSALS.items():
        row = refusal_rows[inventory_id]
        require(row.get("status") == "expected-unsupported", f"{inventory_id} must be expected-unsupported")
        require(row.get("dashboardRowId") is None, f"{inventory_id} must not have an active dashboard row")
        require(row.get("fallbackReason") == fallback, f"{inventory_id} fallback mismatch")
        require(row.get("referenceKind") is None, f"{inventory_id} must not claim a reference kind")


def require_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "pm-closeout-no-new-rendering-support", "classification mismatch")
    require(summary.get("epic") == "FOR-460", "epic mismatch")
    lot = summary.get("lot1", {})
    require(lot.get("totalRows") == 12, "summary total row count mismatch")
    require(lot.get("supportedRows") == 7, "summary supported row count mismatch")
    require(lot.get("expectedUnsupportedRows") == 5, "summary expected-unsupported count mismatch")
    require(lot.get("diagnosticOnlyRows") == 0, "summary diagnostic-only count mismatch")
    require(lot.get("supportScoreIncreasedByFor465ToFor469") is False, "FOR-465..469 must not increase support")
    require(summary.get("dashboardCounters") == {**EXPECTED_DASHBOARD_COUNTERS, "unchangedByD50_9": True}, "dashboard counters mismatch")

    tickets = summary.get("tickets")
    require(isinstance(tickets, list) and len(tickets) == len(EXPECTED_TICKETS), "ticket list mismatch")
    ticket_map = {ticket.get("linear"): ticket for ticket in tickets if isinstance(ticket, dict)}
    require(set(ticket_map) == set(EXPECTED_TICKETS), "ticket ids mismatch")
    for linear, (pr_number, commit) in EXPECTED_TICKETS.items():
        ticket = ticket_map[linear]
        require(ticket.get("pullRequest", "").endswith(f"/pull/{pr_number}"), f"{linear} PR mismatch")
        require(ticket.get("mergeCommit") == commit, f"{linear} merge commit mismatch")

    refusals = summary.get("stableRefusals")
    require(isinstance(refusals, list) and len(refusals) == len(EXPECTED_REFUSALS), "stable refusal list mismatch")
    refusal_map = {row.get("inventoryId"): row.get("fallbackReason") for row in refusals if isinstance(row, dict)}
    require(refusal_map == EXPECTED_REFUSALS, "stable refusal fallback map mismatch")

    non_claims = summary.get("nonClaims", {})
    for key in EXPECTED_NON_CLAIMS:
        require(non_claims.get(key) is False, f"{key} must remain false")
    require(non_claims.get("dashboardRowsAddedByD50_9") == 0, "D50-9 must add 0 dashboard rows")
    require(non_claims.get("supportClaimsAddedByFor465ToFor469") == 0, "FOR-465..469 must add 0 support claims")


def require_report(summary: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required_phrases = (
        "Les tickets FOR-465 a FOR-469 ne montent pas le score de support.",
        "7/12, soit 58.3%",
        "5/12, soit 41.7%",
        "0/12, soit 0.0%",
        "Dashboard global",
        "FOR-461",
        "FOR-462",
        "FOR-464",
        "FOR-465",
        "FOR-466",
        "FOR-467",
        "FOR-468",
        "FOR-469",
        "Aucune nouvelle ligne dashboard active n'est ajoutee.",
        "Aucune nouvelle compatibilite Skia-comparable n'est revendiquee.",
        "Aucune hausse de support visuel superieure a 50% n'est revendiquee",
    )
    for phrase in required_phrases:
        require(phrase in text, f"report missing phrase: {phrase}")
    for inventory_id, fallback in EXPECTED_REFUSALS.items():
        require(inventory_id in text, f"report missing refusal row: {inventory_id}")
        require(fallback in text, f"report missing fallback: {fallback}")
    for linear, (pr_number, commit) in EXPECTED_TICKETS.items():
        require(linear in text, f"report missing ticket: {linear}")
        require(f"/pull/{pr_number}" in text, f"report missing PR number for {linear}")
        require(commit in text, f"report missing commit for {linear}")
    forbidden_claims = (
        "D50-9 ajoute un nouveau rendu",
        "D50-9 ajoute une nouvelle ligne dashboard active",
        "nouvelle compatibilite Skia-comparable est revendiquee",
        "FOR-465 a FOR-469 montent le score de support",
    )
    for claim in forbidden_claims:
        require(claim not in text, f"report contains forbidden claim: {claim}")
    require(summary.get("pmReport") == rel(REPORT), "summary must point to PM report")


def main() -> None:
    summary = load_json(SUMMARY)
    require_scope(summary)
    require_manifest()
    require_summary(summary)
    require_report(summary)
    print("D50 lot 1 PM closeout validation passed: 12 rows, 7 supported, 5 expected-unsupported, 0 diagnostic-only")


if __name__ == "__main__":
    main()
