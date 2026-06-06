#!/usr/bin/env python3
"""Validate D51-6 PM closeout for precise row-specific refusals."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D51-6"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-row-specific-refusal-precision-closeout.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-row-specific-refusal-precision-closeout.json"
D50_CLOSEOUT_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-lot1-pm-closeout.md"
D50_CLOSEOUT_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-lot1-pm-closeout.json"
D50_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d51-row-specific-refusal-precision-closeout.md",
    "reports/wgsl-pipeline/scenes/generated/d51-row-specific-refusal-precision-closeout.json",
    "scripts/validate_d51_row_specific_refusal_precision_closeout.py",
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

TICKETS = [
    {
        "ticket": "D51-1",
        "inventoryId": "skia-gm-drawminibitmaprect",
        "status": "expected-unsupported",
        "report": "reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md",
        "json": "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json",
        "validator": "scripts/validate_d51_drawminibitmaprect_row_specific_evidence.py",
        "pullRequest": "https://github.com/ygdrasil-io/kanvas/pull/1562",
        "mergeCommit": "a351466be631a96375d027e458ecd975591ce814",
        "fallbackReason": "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required",
    },
    {
        "ticket": "D51-2",
        "inventoryId": "skia-gm-image",
        "status": "expected-unsupported",
        "report": "reports/wgsl-pipeline/2026-06-06-d51-2-imagegm-row-specific-evidence.md",
        "json": "reports/wgsl-pipeline/scenes/generated/d51-imagegm-row-specific-evidence.json",
        "validator": "scripts/validate_d51_imagegm_row_specific_evidence.py",
        "pullRequest": "https://github.com/ygdrasil-io/kanvas/pull/1563",
        "mergeCommit": "d3832f2e0fb4ee6ec5cb4e0ed037f3ee04ac20ec",
        "fallbackReason": "image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required",
    },
    {
        "ticket": "D51-3",
        "inventoryId": "skia-gm-imagesource",
        "status": "expected-unsupported",
        "report": "reports/wgsl-pipeline/2026-06-06-d51-3-imagesource-row-specific-evidence.md",
        "json": "reports/wgsl-pipeline/scenes/generated/d51-imagesource-row-specific-evidence.json",
        "validator": "scripts/validate_d51_imagesource_row_specific_evidence.py",
        "pullRequest": "https://github.com/ygdrasil-io/kanvas/pull/1564",
        "mergeCommit": "15e0063f6b9af86657bbc543d2259f33ca85dbe4",
        "fallbackReason": "image.imagesource.image-filter-cubic-panels-webgpu-artifacts-required",
    },
    {
        "ticket": "D51-4",
        "inventoryId": "skia-gm-offsetimagefilter",
        "status": "expected-unsupported",
        "report": "reports/wgsl-pipeline/2026-06-06-d51-4-offsetimagefilter-row-specific-evidence.md",
        "json": "reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json",
        "validator": "scripts/validate_d51_offsetimagefilter_row_specific_evidence.py",
        "pullRequest": "https://github.com/ygdrasil-io/kanvas/pull/1565",
        "mergeCommit": "0201611d16164f6cc26aecc4e44cb309f9728d53",
        "fallbackReason": "image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required",
    },
    {
        "ticket": "D51-5",
        "inventoryId": "skia-gm-pathfill",
        "status": "expected-unsupported",
        "report": "reports/wgsl-pipeline/2026-06-06-d51-5-pathfill-row-specific-evidence.md",
        "json": "reports/wgsl-pipeline/scenes/generated/d51-pathfill-row-specific-evidence.json",
        "validator": "scripts/validate_d51_pathfill_row_specific_evidence.py",
        "pullRequest": "https://github.com/ygdrasil-io/kanvas/pull/1566",
        "mergeCommit": "e1e9712cd404b217719ef1d8a08662d40770e921",
        "fallbackReason": "path-aa.fill.multi-shape-conic-cubic-transform-webgpu-artifacts-required",
    },
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
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D51-6 files, got: {sorted(changed)}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_source_files_exist() -> None:
    for path in (REPORT, EVIDENCE, D50_CLOSEOUT_REPORT, D50_CLOSEOUT_JSON, D50_MANIFEST):
        require(path.is_file(), f"missing required input: {rel(path)}")
    for ticket in TICKETS:
        for key in ("report", "json", "validator"):
            path = ROOT / ticket[key]
            require(path.is_file(), f"missing {ticket['ticket']} {key}: {ticket[key]}")


def require_d50_inputs() -> None:
    closeout = load_json(D50_CLOSEOUT_JSON)
    require(closeout.get("classification") == "pm-closeout-no-new-rendering-support", "D50 closeout classification changed")
    lot1 = closeout.get("lot1", {})
    require(lot1.get("expectedUnsupportedRows") == 5, "D50 closeout expected unsupported count changed")
    require(lot1.get("supportScoreIncreasedByFor465ToFor469") is False, "D50 closeout support guard changed")
    dashboard = closeout.get("dashboardCounters", {})
    require(dashboard.get("unchangedByD50_9") is True, "D50 closeout dashboard guard changed")
    non_claims = closeout.get("nonClaims", {})
    for key in (
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "productionCodeChanged",
        "wgslProductionChanged",
        "upstreamSourceChanged",
    ):
        require(non_claims.get(key) is False, f"D50 non-claim changed: {key}")

    manifest = load_json(D50_MANIFEST)
    require(manifest.get("statusCounts") == {"diagnostic-only": 0, "expected-unsupported": 5, "supported": 7}, "D50 manifest status counts changed")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "D50 manifest rows must be a list")
    d50_rows = {row.get("inventoryId"): row for row in rows if isinstance(row, dict)}
    for ticket in TICKETS:
        row = d50_rows.get(ticket["inventoryId"])
        require(isinstance(row, dict), f"D50 manifest missing {ticket['inventoryId']}")
        require(row.get("status") == "expected-unsupported", f"D50 status changed for {ticket['inventoryId']}")
        require(row.get("strictDashboardStatus") == "expected-unsupported", f"D50 strict status changed for {ticket['inventoryId']}")
        require(row.get("dashboardRowId") is None, f"D50 dashboard row appeared for {ticket['inventoryId']}")


def require_d51_evidence() -> None:
    for ticket in TICKETS:
        data = load_json(ROOT / ticket["json"])
        require(data.get("ticket") == ticket["ticket"], f"{ticket['ticket']} ticket mismatch")
        require(data.get("inventoryId") == ticket["inventoryId"], f"{ticket['ticket']} inventory mismatch")
        require(data.get("classification") == "row-specific-expected-unsupported-precise-refusal", f"{ticket['ticket']} classification mismatch")
        require(data.get("status") == "expected-unsupported", f"{ticket['ticket']} status must remain expected-unsupported")
        require(data.get("fallbackReason") == ticket["fallbackReason"], f"{ticket['ticket']} fallback reason mismatch")
        require(data.get("fallbackReasonImproved") is True, f"{ticket['ticket']} fallbackReasonImproved must be true")
        require(data.get("d50ManifestChanged") is False, f"{ticket['ticket']} must not change D50 manifest")
        score = data.get("scoreImpact", {})
        require(score.get("supportScoreIncreased") is False, f"{ticket['ticket']} support score must not increase")
        require(score.get("skiaComparableScoreIncreased") is False, f"{ticket['ticket']} Skia-comparable score must not increase")
        non_claims = data.get("nonClaims", {})
        for key in (
            "d50ManifestChanged",
            "thresholdChanged",
            "scoringChanged",
            "fallbackPolicyChanged",
            "pipelineKeyChanged",
            "productionCodeChanged",
            "wgslProductionChanged",
            "upstreamSourceChanged",
        ):
            require(non_claims.get(key) is False, f"{ticket['ticket']} non-claim changed: {key}")
        if "skiaIntegrationTestsChanged" in non_claims:
            require(non_claims.get("skiaIntegrationTestsChanged") is False, f"{ticket['ticket']} must not change skia-integration-tests")


def require_closeout_json(evidence: dict[str, Any]) -> None:
    require(evidence.get("classification") == "pm-closeout-no-new-rendering-support", "closeout classification mismatch")
    require(evidence.get("refinedRefusals") == 5, "refinedRefusals must be 5")
    require(evidence.get("refinedRefusalsTotal") == 5, "refinedRefusalsTotal must be 5")
    require(evidence.get("refinedRefusalsPercent") == 100.0, "refinedRefusalsPercent must be 100.0")
    require(evidence.get("promotedRows") == 0, "promotedRows must be 0")
    require(evidence.get("promotedRowsTotal") == 5, "promotedRowsTotal must be 5")
    require(evidence.get("promotedRowsPercent") == 0.0, "promotedRowsPercent must be 0.0")
    require(evidence.get("supportScoreIncreased") is False, "supportScoreIncreased must be false")
    require(evidence.get("skiaComparableScoreIncreased") is False, "skiaComparableScoreIncreased must be false")
    require(evidence.get("d50ManifestChanged") is False, "d50ManifestChanged must be false")
    require(evidence.get("dashboardActiveChanged") is False, "dashboardActiveChanged must be false")
    require(evidence.get("activeDashboardRowsChanged") == 0, "activeDashboardRowsChanged must be 0")

    for key in (
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "rendererChanged",
        "wgslProductionChanged",
        "upstreamChanged",
        "skiaIntegrationTestsChanged",
    ):
        require(evidence.get(key) is False, f"top-level non-claim changed: {key}")

    tickets = evidence.get("tickets")
    require(isinstance(tickets, list), "closeout tickets must be a list")
    require(len(tickets) == len(TICKETS), "closeout must list exactly five D51 tickets")
    by_ticket = {ticket.get("ticket"): ticket for ticket in tickets if isinstance(ticket, dict)}
    for expected in TICKETS:
        actual = by_ticket.get(expected["ticket"])
        require(isinstance(actual, dict), f"closeout missing {expected['ticket']}")
        for key in ("inventoryId", "status", "report", "json", "validator", "pullRequest", "mergeCommit", "fallbackReason"):
            require(actual.get(key) == expected[key], f"{expected['ticket']} closeout {key} mismatch")

    requirements = evidence.get("promotionRequirements")
    require(isinstance(requirements, list), "promotionRequirements must be a list")
    for phrase in ("reference row-specific comparable", "artefact CPU row-specific", "artefact WebGPU row-specific", "payload diff/stat", "diagnostics de route", "fallbackReason=none"):
        require(phrase in requirements, f"missing promotion requirement: {phrase}")

    non_claims = evidence.get("nonClaims", {})
    require(non_claims.get("newRenderingSupportAddedByD51_6") is False, "D51-6 must not add rendering support")
    require(non_claims.get("dashboardActiveRowsChangedByD51_6") == 0, "D51-6 must not change dashboard rows")
    require(non_claims.get("supportClaimsAddedByD51_6") == 0, "D51-6 must not add support claims")
    require(non_claims.get("skiaComparableClaimsAddedByD51_6") == 0, "D51-6 must not add Skia-comparable claims")
    for key in (
        "d50ManifestChanged",
        "resultsJsonChanged",
        "scenesJsonChanged",
        "thresholdChanged",
        "scoringChanged",
        "fallbackPolicyChanged",
        "pipelineKeyChanged",
        "rendererChanged",
        "wgslProductionChanged",
        "upstreamChanged",
        "skiaIntegrationTestsChanged",
        "broadSkiaGmParityClaim",
        "broadImageSupportClaim",
        "broadImageFilterDagSupportClaim",
        "broadPathAaSupportClaim",
    ):
        require(non_claims.get(key) is False, f"closeout non-claim changed: {key}")


def require_report_text() -> None:
    text = REPORT.read_text(encoding="utf-8")
    required_phrases = (
        "5 refus D50 sur 5, soit 100% du perimetre D51",
        "0/5, soit 0%",
        "0 changement du tableau de bord actif",
        "D51 ameliore la lecture PM",
        "elle ne change pas le score",
        "0 changement de seuil global",
        "0 changement de scoring",
        "0 changement de fallback globale",
        "0 changement de `PipelineKey`",
        "0 changement renderer",
        "0 changement WGSL de production",
        "0 changement upstream",
        "0 changement `skia-integration-tests`",
        "reference row-specific",
        "Artefact CPU",
        "Artefact WebGPU",
        "Payload diff/stat",
        "Diagnostics de route",
        "`fallbackReason=none`",
    )
    for phrase in required_phrases:
        require(phrase in text, f"report missing phrase: {phrase}")
    for ticket in TICKETS:
        require(ticket["ticket"] in text, f"report missing {ticket['ticket']}")
        require(ticket["inventoryId"] in text, f"report missing {ticket['inventoryId']}")
        require(ticket["fallbackReason"] in text, f"report missing fallback {ticket['fallbackReason']}")
        require(ticket["pullRequest"] in text, f"report missing PR {ticket['pullRequest']}")


def main() -> None:
    require_source_files_exist()
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_d50_inputs()
    require_d51_evidence()
    require_closeout_json(evidence)
    require_report_text()
    print("D51-6 closeout validation passed")


if __name__ == "__main__":
    main()
