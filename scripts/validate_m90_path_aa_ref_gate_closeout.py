#!/usr/bin/env python3
"""Validate the M90-PAA-3-REF-CLOSEOUT coordination artifact."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "M90-PAA-3-REF-CLOSEOUT"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-m90-path-aa-ref-gate-closeout.md"
CLOSEOUT = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json"
CANDIDATE = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-intake-closeout/summary.json"
HAIRLINES_HARNESS = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-hairlines-artifact-harness.json"
HAIRLINES_ADAPTER_GATE = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-hairlines-adapter-backed-gate.json"

ROW_GATES = [
    {
        "ticket": "M90-PAA-3B-REF",
        "rowId": "skia-gm-strokerect",
        "sourceGm": "StrokeRectGM",
        "fallbackReason": "coverage.stroke-rect.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-strokerect-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3C-REF",
        "rowId": "skia-gm-thinstrokedrects",
        "sourceGm": "ThinStrokedRectsGM",
        "fallbackReason": "coverage.thin-stroked-rects.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-thinstrokedrects-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3D-REF",
        "rowId": "skia-gm-strokedlines",
        "sourceGm": "StrokedLinesGM",
        "fallbackReason": "coverage.stroked-lines.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-strokedlines-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3E-REF",
        "rowId": "skia-gm-strokerects",
        "sourceGm": "StrokeRectsGM",
        "fallbackReason": "coverage.stroke-rects.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-strokerects-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3F-REF",
        "rowId": "skia-gm-hairmodes",
        "sourceGm": "HairModesGM",
        "fallbackReason": "coverage.hairmode.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-hairmodes-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3G-REF",
        "rowId": "skia-gm-scaledstrokes",
        "sourceGm": "ScaledStrokesGM",
        "fallbackReason": "coverage.scaled-stroke.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-scaledstrokes-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3H-REF",
        "rowId": "skia-gm-dashing",
        "sourceGm": "DashingGM",
        "fallbackReason": "coverage.dashing.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-dashing-row-specific-evidence-gate.json",
    },
    {
        "ticket": "M90-PAA-3I-REF",
        "rowId": "skia-gm-dashcubics",
        "sourceGm": "DashCubicsGM",
        "fallbackReason": "coverage.dash-cubic.row-specific-artifacts-required",
        "path": "reports/wgsl-pipeline/scenes/generated/m90-dashcubics-row-specific-evidence-gate.json",
    },
]

EXPECTED_ROWS = [
    {
        "ticket": "M90-PAA-3A-REF",
        "rowId": "skia-gm-hairlines",
        "sourceGm": "HairlinesGM",
        "status": "expected-unsupported",
        "artifactKind": "artifact-harness-plus-adapter-backed-gate",
    },
    *[
        {
            "ticket": row["ticket"],
            "rowId": row["rowId"],
            "sourceGm": row["sourceGm"],
            "status": "dependency-gated",
            "artifactKind": "row-specific-evidence-gate",
        }
        for row in ROW_GATES
    ],
]

EXPECTED_COUNTERS = {
    "candidateRows": 9,
    "rowsWithRefGateOrHarness": 9,
    "rowSpecificGateArtifacts": 8,
    "hairlinesHarnessArtifacts": 2,
    "supportClaims": 0,
    "newSupportClaims": 0,
    "readinessDelta": 0.0,
    "dashboardPromotions": 0,
    "thresholdChanges": 0,
    "edgeBudgetChanges": 0,
}

FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
}

ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "reports/wgsl-pipeline/2026-06-08-m90-path-aa-ref-gate-closeout.md",
    "reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json",
    "scripts/validate_m90_path_aa_ref_gate_closeout.py",
}


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def require_exact_keys(data: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(data)
    require(actual == expected, f"{label} keys changed: expected={sorted(expected)} actual={sorted(actual)}")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} root must be an object")
    return data


def require_text(path: Path, snippets: list[str]) -> str:
    require(path.is_file(), f"missing file: {rel(path)}")
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        require(snippet in text, f"{rel(path)} missing required snippet: {snippet}")
    return text


def require_all_false(data: dict[str, Any], label: str) -> None:
    for key, value in data.items():
        require(value is False, f"{label}.{key} must remain false")


def require_score_impact_zero(data: dict[str, Any], label: str) -> None:
    impact = data.get("scoreImpact")
    require(isinstance(impact, dict), f"{label} missing scoreImpact")
    require(impact.get("newSupportClaims") == 0, f"{label} gained support claims")
    require(impact.get("readinessDelta") == 0.0, f"{label} readiness delta changed")
    require(impact.get("dashboardPromotion") is False, f"{label} dashboard promotion changed")
    require(impact.get("thresholdChanged") is False, f"{label} threshold changed")
    require(impact.get("edgeBudgetChanged") is False, f"{label} edge budget changed")


def validate_candidate_closeout() -> None:
    data = load_json(CANDIDATE)
    require(data.get("classification") == "path-aa-candidate-intake-closeout-no-new-rendering-support", "candidate classification changed")
    require(data.get("status") == "generated evidence", "candidate status changed")
    counters = data.get("counters")
    require(isinstance(counters, dict), "candidate missing counters")
    require(counters.get("candidateRows") == 9, "candidate row count changed")
    require(counters.get("newSupportClaims") == 0, "candidate gained support claims")
    require(counters.get("readinessDelta") == 0.0, "candidate readiness changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "candidate historical signals became promotional")
    for row in data.get("rows", []):
        require(isinstance(row, dict), "candidate rows must be objects")
        require(row.get("supportClaim") is False, f"candidate row gained support claim: {row.get('rowId')}")
        require(row.get("newSupportClaims") == 0, f"candidate row gained new support claim: {row.get('rowId')}")
        require(row.get("readinessDelta") == 0.0, f"candidate row readiness changed: {row.get('rowId')}")
    active = data.get("activeNextRecommendedTicket")
    require(isinstance(active, dict), "candidate missing active next ticket")
    require(active.get("id") == "M90-PAA-3A", "candidate active next ticket changed")
    require(active.get("rowId") == "skia-gm-hairlines", "candidate active next row changed")
    require(active.get("supportClaimAllowed") is False, "candidate active next ticket allows support claims")
    require(active.get("promotionAllowedWithoutEvidence") is False, "candidate active next ticket allows promotion without evidence")
    handoff = data.get("nextHandoff")
    require(isinstance(handoff, dict), "candidate missing next handoff")
    require(handoff.get("id") == "M90-PAA-3A-REF", "candidate next handoff changed")
    require(handoff.get("rowId") == "skia-gm-hairlines", "candidate next handoff row changed")
    require(handoff.get("supportClaimAllowedFromCloseout") is False, "candidate next handoff allows support claims")
    guard = data.get("supportGuard")
    require(isinstance(guard, dict), "candidate missing supportGuard")
    require_all_false(guard, "candidate supportGuard")
    non_claims = data.get("nonClaims")
    require(isinstance(non_claims, dict), "candidate missing nonClaims")
    require_all_false(non_claims, "candidate nonClaims")


def validate_hairlines_artifacts() -> None:
    harness = load_json(HAIRLINES_HARNESS)
    require(harness.get("ticket") == "M90-PAA-3A-REF", "hairlines harness ticket changed")
    require(harness.get("sceneId") == "skia-gm-hairlines", "hairlines harness scene changed")
    require(harness.get("status") == "expected-unsupported", "hairlines harness status changed")
    require(harness.get("supportClaim") is False, "hairlines harness must not claim support")
    require(harness.get("fallbackReason") == "coverage.hairline.row-specific-artifacts-required", "hairlines harness fallback changed")
    require_score_impact_zero(harness, "hairlines harness")
    non_claims = harness.get("nonClaims")
    require(isinstance(non_claims, dict), "hairlines harness missing nonClaims")
    require_all_false(non_claims, "hairlines harness nonClaims")
    promotion = harness.get("promotionGate")
    require(isinstance(promotion, dict), "hairlines harness missing promotionGate")
    for key in ["supportByStructure", "promotionAllowedWithoutArtifacts", "promotionAllowedWithoutFallbackReasonNone"]:
        require(promotion.get(key) is False, f"hairlines harness promotion flag changed: {key}")

    adapter = load_json(HAIRLINES_ADAPTER_GATE)
    require(adapter.get("ticket") == "M90-PAA-3A-REF-GPU", "hairlines adapter ticket changed")
    require(adapter.get("parentTicket") == "M90-PAA-3A-REF", "hairlines adapter parent changed")
    require(adapter.get("sceneId") == "skia-gm-hairlines", "hairlines adapter scene changed")
    require(adapter.get("status") == "dependency-gated", "hairlines adapter status changed")
    require(adapter.get("supportClaim") is False, "hairlines adapter must not claim support")
    require_score_impact_zero(adapter, "hairlines adapter")
    non_claims = adapter.get("nonClaims")
    require(isinstance(non_claims, dict), "hairlines adapter missing nonClaims")
    require_all_false(non_claims, "hairlines adapter nonClaims")
    promotion = adapter.get("promotionGate")
    require(isinstance(promotion, dict), "hairlines adapter missing promotionGate")
    require_all_false(promotion, "hairlines adapter promotionGate")


def validate_row_gates() -> None:
    for row in ROW_GATES:
        path = ROOT / row["path"]
        data = load_json(path)
        require(data.get("milestone") == "M90", f"{row['path']} milestone changed")
        require(data.get("ticket") == row["ticket"], f"{row['path']} ticket changed")
        require(data.get("sceneId") == row["rowId"], f"{row['path']} scene changed")
        require(data.get("inventoryId") == row["rowId"], f"{row['path']} inventory changed")
        require(data.get("sourceGm") == row["sourceGm"], f"{row['path']} source GM changed")
        require(data.get("status") == "dependency-gated", f"{row['path']} status changed")
        require(data.get("supportClaim") is False, f"{row['path']} must not claim support")
        require(data.get("fallbackReason") == row["fallbackReason"], f"{row['path']} fallback changed")
        evidence = data.get("evidenceGate")
        require(isinstance(evidence, dict), f"{row['path']} missing evidenceGate")
        require(evidence.get("kind") == "row-specific-reference-cpu-webgpu-artifact-bundle", f"{row['path']} evidence kind changed")
        require(evidence.get("requiredRouteStatus") == "pass", f"{row['path']} route status changed")
        require(evidence.get("requiredFallbackReason") == "none", f"{row['path']} required fallback changed")
        require(evidence.get("currentlyPresentArtifacts") == [], f"{row['path']} unexpectedly has present artifacts")
        promotion = data.get("promotionGate")
        require(isinstance(promotion, dict), f"{row['path']} missing promotionGate")
        require_all_false(promotion, f"{row['path']} promotionGate")
        require_score_impact_zero(data, row["path"])
        non_claims = data.get("nonClaims")
        require(isinstance(non_claims, dict), f"{row['path']} missing nonClaims")
        require_all_false(non_claims, f"{row['path']} nonClaims")


def validate_closeout_json() -> None:
    data = load_json(CLOSEOUT)
    require_exact_keys(
        data,
        {
            "schemaVersion",
            "generatedBy",
            "ticket",
            "milestone",
            "classification",
            "status",
            "scope",
            "inputs",
            "counters",
            "rows",
            "activeNextRecommendedTicket",
            "nextHandoff",
            "supportGuard",
            "nonClaims",
            "validationCommands",
        },
        "closeout root",
    )
    require(data.get("schemaVersion") == 1, "closeout schema version changed")
    require(data.get("ticket") == TICKET, "closeout ticket changed")
    require(data.get("milestone") == "M90", "closeout milestone changed")
    require(data.get("classification") == "path-aa-ref-gate-closeout-no-support-promotion", "closeout classification changed")
    require(data.get("status") == "generated evidence", "closeout status changed")
    require("Aggregates already-materialized M90 Path AA REF gates and harnesses" in data.get("scope", ""), "closeout scope does not name aggregation")
    require("does not add rendering support claims" in data.get("scope", ""), "closeout scope must reject support claims")

    inputs = data.get("inputs")
    require(isinstance(inputs, dict), "closeout missing inputs")
    require(inputs.get("candidateIntakeCloseout") == rel(CANDIDATE), "closeout candidate input changed")
    require(inputs.get("hairlines") == [rel(HAIRLINES_HARNESS), rel(HAIRLINES_ADAPTER_GATE)], "closeout hairlines inputs changed")
    require(inputs.get("rowSpecificGates") == [row["path"] for row in ROW_GATES], "closeout row gate inputs changed")

    require(data.get("counters") == EXPECTED_COUNTERS, "closeout counters changed")

    rows = data.get("rows")
    require(isinstance(rows, list), "closeout rows must be a list")
    require(len(rows) == 9, "closeout must include 9 rows")
    for index, (actual, expected) in enumerate(zip(rows, EXPECTED_ROWS), start=1):
        require(isinstance(actual, dict), f"closeout row {index} must be an object")
        require(actual.get("order") == index, f"closeout row order changed: {index}")
        for key, value in expected.items():
            require(actual.get(key) == value, f"closeout row {index} {key} changed")
        require(actual.get("supportClaim") is False, f"closeout row {index} must not claim support")
        require(actual.get("promotionAllowed") is False, f"closeout row {index} must not allow promotion")
    hairlines = rows[0]
    require(hairlines.get("artifacts") == [
        {"kind": "artifact-harness", "path": rel(HAIRLINES_HARNESS)},
        {"kind": "adapter-backed-gate", "path": rel(HAIRLINES_ADAPTER_GATE)},
    ], "hairlines must link harness plus adapter-backed gate")
    for actual, row in zip(rows[1:], ROW_GATES):
        require(actual.get("gate") == row["path"], f"{row['rowId']} gate link changed")

    active = data.get("activeNextRecommendedTicket")
    require(isinstance(active, dict), "closeout missing active next ticket")
    require(active.get("id") == "M90-PAA-3A", "closeout active next ticket changed")
    require(active.get("rowId") == "skia-gm-hairlines", "closeout active next row changed")
    require(active.get("supportClaimAllowed") is False, "closeout active next ticket allows support claims")
    require(active.get("promotionAllowedWithoutEvidence") is False, "closeout active next ticket allows promotion without evidence")
    handoff = data.get("nextHandoff")
    require(isinstance(handoff, dict), "closeout missing next handoff")
    require(handoff.get("id") == "M90-PAA-3A-REF", "closeout next handoff changed")
    require(handoff.get("rowId") == "skia-gm-hairlines", "closeout next handoff row changed")
    require(handoff.get("supportClaimAllowed") is False, "closeout next handoff allows support claims")
    require(handoff.get("promotionAllowedWithoutEvidence") is False, "closeout next handoff allows promotion without evidence")
    guard = data.get("supportGuard")
    require(isinstance(guard, dict), "closeout missing supportGuard")
    require_all_false(guard, "closeout supportGuard")
    non_claims = data.get("nonClaims")
    require(isinstance(non_claims, dict), "closeout missing nonClaims")
    require_all_false(non_claims, "closeout nonClaims")

    commands = data.get("validationCommands")
    require(commands == [
        "rtk python3 scripts/validate_m90_path_aa_ref_gate_closeout.py --check-worktree-scope",
        "rtk ./gradlew --no-daemon pipelineM90PathAaRefGateCloseout",
        "rtk git diff --check",
    ], "closeout validation commands changed")


def validate_report() -> None:
    require_text(
        REPORT,
        [
            "M90 Path AA REF Gate Closeout",
            TICKET,
            "coordination/visibility closeout only",
            "without adding rendering support claims",
            "does not replace row-specific Skia reference, CPU/WebGPU route,",
            "Candidate rows: `9`",
            "Rows with REF gate or harness: `9`",
            "Row-specific gate artifacts: `8`",
            "Hairlines harness artifacts: `2`",
            "Support claims: `0`",
            "New support claims: `0`",
            "Readiness delta: `0.0`",
            "Dashboard promotions: `0`",
            "Threshold changes: `0`",
            "Edge-budget changes: `0`",
            "`HairlinesGM` / `skia-gm-hairlines`",
            "`artifact-harness` plus `adapter-backed-gate`",
            "m90-hairlines-artifact-harness.json",
            "m90-hairlines-adapter-backed-gate.json",
            "m90-strokerect-row-specific-evidence-gate.json",
            "m90-thinstrokedrects-row-specific-evidence-gate.json",
            "m90-strokedlines-row-specific-evidence-gate.json",
            "m90-strokerects-row-specific-evidence-gate.json",
            "m90-hairmodes-row-specific-evidence-gate.json",
            "m90-scaledstrokes-row-specific-evidence-gate.json",
            "m90-dashing-row-specific-evidence-gate.json",
            "m90-dashcubics-row-specific-evidence-gate.json",
            "No row support claim",
            "No new rendering support claim",
            "No registry promotion",
            "No dashboard promotion",
            "No readiness promotion",
            "No global threshold change",
            "No edge-budget change",
            "No broad Path AA support claim",
            "No broad dash support claim",
            "No broad hairline support claim",
            "No broad stroke support claim",
            "No Ganesh or Graphite port",
            "No dynamic SkSL compiler, IR, or VM",
            "below-threshold/tolerance-only case",
            "The active next handoff remains `M90-PAA-3A-REF` / `skia-gm-hairlines`.",
            "`supportClaimAllowed=false` and `promotionAllowedWithoutEvidence=false`",
            "rtk python3 scripts/validate_m90_path_aa_ref_gate_closeout.py --check-worktree-scope",
            "rtk ./gradlew --no-daemon pipelineM90PathAaRefGateCloseout",
            "rtk git diff --check",
        ],
    )


def validate_worktree_scope() -> None:
    proc = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    forbidden: list[str] = []
    unexpected: list[str] = []
    for raw in proc.stdout.splitlines():
        if not raw.strip():
            continue
        path = raw[3:] if len(raw) > 3 else raw.strip()
        if path == "tmp/" or path.startswith("tmp/"):
            continue
        if path in FORBIDDEN_PROMOTION_FILES:
            forbidden.append(path)
        if path not in ALLOWED_STATUS_PATHS:
            unexpected.append(raw)
    require(not forbidden, f"forbidden promotion/dashboard files modified: {forbidden}")
    require(not unexpected, f"unexpected worktree paths for this scoped item: {unexpected}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-worktree-scope", action="store_true", help="also reject unrelated dirty worktree paths")
    args = parser.parse_args()

    validate_candidate_closeout()
    validate_hairlines_artifacts()
    validate_row_gates()
    validate_closeout_json()
    validate_report()
    if args.check_worktree_scope:
        validate_worktree_scope()
    print(f"validated {TICKET} coordination closeout")


if __name__ == "__main__":
    main()
