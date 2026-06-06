#!/usr/bin/env python3
"""Validate FOR-461 D50 GM dashboard candidate inventory evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-candidate-inventory.md"
JSON_PATH = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json"

EXPECTED_LOT1 = [
    "skia-gm-drawbitmaprect",
    "skia-gm-drawminibitmaprect",
    "skia-gm-bitmappremul",
    "skia-gm-image",
    "skia-gm-imagesource",
    "skia-gm-localmatriximageshader",
    "skia-gm-gradientsdegenerate",
    "skia-gm-offsetimagefilter",
    "skia-gm-matriximagefilter",
    "skia-gm-imageblur",
    "skia-gm-simpleaaclip",
    "skia-gm-pathfill",
]
EXPECTED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-candidate-inventory.md",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
    "scripts/validate_for461_d50_gm_dashboard_candidate_inventory.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-461 validation failed: {message}")


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
    require(not unexpected, f"unexpected local diffs for FOR-461: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"dashboard active rows changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_counters(data: dict[str, Any]) -> None:
    current = data.get("currentCounters")
    target = data.get("targetCounters")
    families = data.get("familyCounters")
    require(isinstance(current, dict), "currentCounters must be an object")
    require(isinstance(target, dict), "targetCounters must be an object")
    require(isinstance(families, dict), "familyCounters must be an object")

    expected_current = {
        "localMaterializedDashboardRows": 28,
        "localMaterializedSupportedRows": 21,
        "localMaterializedExpectedUnsupportedRows": 7,
        "localMaterializedDiagnosticOnlyRows": 0,
        "localMaterializedSkiaComparableRows": 5,
    }
    for key, expected in expected_current.items():
        require(current.get(key) == expected, f"currentCounters.{key} mismatch")

    expected_target = {
        "candidateRowsInventoried": 25,
        "lot1RecommendedRows": 12,
        "projectedSelectedRowsIfAllCandidatesLand": 53,
        "selectedRowsNeededToExceed50FromLocalDashboard": 23,
        "supportClaimsAddedByThisInventory": 0,
        "skiaComparableClaimsAddedByThisInventory": 0,
        "diagnosticOnlyCandidates": 2,
        "expectedUnsupportedBoundaryCandidates": 2,
        "intendedSupportCandidatesRequiringEvidence": 21,
        "supportedRowsRemainUntilPromotion": 21,
        "skiaComparableRowsRemainUntilReferenceCapture": 5,
    }
    for key, expected in expected_target.items():
        require(target.get(key) == expected, f"targetCounters.{key} mismatch")

    totals = {
        "candidates": 0,
        "lot1": 0,
        "intendedSupport": 0,
        "expectedUnsupported": 0,
        "diagnosticOnly": 0,
    }
    for family, values in families.items():
        require(isinstance(values, dict), f"familyCounters.{family} must be an object")
        for key in totals:
            value = values.get(key)
            require(isinstance(value, int), f"familyCounters.{family}.{key} must be an int")
            totals[key] += value
    require(totals["candidates"] == 25, "family candidate total must be 25")
    require(totals["lot1"] == 12, "family lot1 total must be 12")
    require(totals["intendedSupport"] == 21, "family intended support total must be 21")
    require(totals["expectedUnsupported"] == 2, "family expected unsupported total must be 2")
    require(totals["diagnosticOnly"] == 2, "family diagnostic-only total must be 2")


def require_candidates(data: dict[str, Any]) -> None:
    candidates = data.get("candidates")
    require(isinstance(candidates, list), "candidates must be a list")
    require(len(candidates) == 25, "candidate count must be 25")
    ranks = [candidate.get("rank") for candidate in candidates if isinstance(candidate, dict)]
    require(ranks == list(range(1, 26)), "candidate ranks must be contiguous 1..25")

    ids = []
    lot1 = []
    for candidate in candidates:
        require(isinstance(candidate, dict), "each candidate must be an object")
        inventory_id = candidate.get("inventoryId")
        ids.append(inventory_id)
        require(isinstance(inventory_id, str) and inventory_id.startswith("skia-gm-"), "invalid inventoryId")
        require(candidate.get("inventoryStatus") == "promotion-candidate", f"{inventory_id} must remain candidate")
        require(candidate.get("candidateClassification") in {
            "selected-candidate",
            "expected-unsupported-boundary",
            "diagnostic-only",
        }, f"{inventory_id} has invalid candidateClassification")
        require(candidate.get("intendedDashboardStatusAfterEvidence") in {
            "pass",
            "expected-unsupported",
            "diagnostic-only",
        }, f"{inventory_id} has invalid intended status")
        require(candidate.get("referencePlan"), f"{inventory_id} missing referencePlan")
        require(candidate.get("cpuExpectation"), f"{inventory_id} missing cpuExpectation")
        require(candidate.get("gpuExpectation"), f"{inventory_id} missing gpuExpectation")
        require(candidate.get("risk"), f"{inventory_id} missing risk")
        if candidate.get("lot") == 1:
            lot1.append(inventory_id)
    require(len(set(ids)) == 25, "candidate inventoryIds must be unique")
    require(lot1 == EXPECTED_LOT1, "lot 1 candidate order mismatch")

    lot1_payload = data.get("lot1Recommendation")
    require(isinstance(lot1_payload, dict), "lot1Recommendation must be an object")
    require(lot1_payload.get("size") == 12, "lot1Recommendation.size must be 12")
    require(lot1_payload.get("candidateIds") == EXPECTED_LOT1, "lot1Recommendation candidateIds mismatch")
    evidence = lot1_payload.get("requiredPromotionEvidence")
    require(isinstance(evidence, list), "requiredPromotionEvidence must be a list")
    for required in (
        "row-specific reference artifact",
        "CPU artifact and route diagnostics",
        "GPU artifact or stable expected-unsupported refusal",
        "diff and stats artifacts",
        "fallbackReason=none for support rows",
        "unchanged dashboard thresholds and gate policy",
    ):
        require(required in evidence, f"missing required promotion evidence: {required}")


def require_non_claims(data: dict[str, Any]) -> None:
    non_claims = data.get("nonClaims")
    exclusions = data.get("exclusions")
    require(isinstance(non_claims, list), "nonClaims must be a list")
    require(isinstance(exclusions, list), "exclusions must be a list")
    joined = "\n".join(str(item) for item in non_claims)
    for required in (
        "does not change any dashboard row status",
        "adds 0 support claims and 0 Skia-comparable fidelity claims",
        "not visual support percentage",
        "No global threshold, scoring rule, fallback policy, or PipelineKey axis is changed",
        "No broad Skia GM parity",
    ):
        require(required in joined, f"missing non-claim: {required}")
    exclusion_ids = {item.get("inventoryId") for item in exclusions if isinstance(item, dict)}
    for required in (
        "skia-gm-runtimeimagefilter",
        "skia-gm-shadertext3",
        "skia-gm-gradients2ptconical",
        "codec-and-yuv-gm-family",
        "dash-and-hairline-family",
    ):
        require(required in exclusion_ids, f"missing exclusion: {required}")


def require_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report file: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    lower = text.lower()
    compact = " ".join(lower.split())
    for required in (
        "inventory status is not support status",
        "support claims added now | 0",
        "skia-comparable claims added now | 0",
        "projected selected rows if all candidates land | 53",
        "no dashboard status changes are made by this ticket",
        "no global thresholds, scoring logic, fallback policy, or `pipelinekey` axes changed",
    ):
        require(required in compact, f"report missing: {required}")
    for inventory_id in EXPECTED_LOT1:
        require(inventory_id in text, f"report missing lot 1 candidate {inventory_id}")
    require(str(data["targetCounters"]["candidateRowsInventoried"]) in text, "report must include candidate count")
    require(str(data["targetCounters"]["lot1RecommendedRows"]) in text, "report must include lot1 count")


def main() -> None:
    require_scope()
    data = load_json(JSON_PATH)
    require(data.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(data.get("generatedBy") == "FOR-461 D50 GM dashboard candidate inventory", "generatedBy mismatch")
    require(data.get("date") == "2026-06-06", "date mismatch")
    sources = data.get("sourceInputs")
    require(isinstance(sources, list), "sourceInputs must be a list")
    for required in (
        "reports/wgsl-pipeline/scenes/data/scenes.json",
        "reports/wgsl-pipeline/scenes/generated/results.json",
        "build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json",
        "build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.md",
        "reports/upstream-rebaseline/",
        ".upstream/source/map/",
    ):
        require(required in sources, f"missing source input: {required}")
    require_counters(data)
    require_candidates(data)
    require_non_claims(data)
    require_report(data)
    print(
        "FOR-461 validation passed: "
        f"candidates={data['targetCounters']['candidateRowsInventoried']} "
        f"lot1={data['targetCounters']['lot1RecommendedRows']} "
        f"projected={data['targetCounters']['projectedSelectedRowsIfAllCandidatesLand']}"
    )


if __name__ == "__main__":
    main()
