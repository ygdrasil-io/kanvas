#!/usr/bin/env python3
"""Validate the M90-PAA-3H-REF DashingGM evidence gate."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
ROW_ID = "skia-gm-dashing"
SOURCE_GM = "DashingGM"
TICKET = "M90-PAA-3H-REF"
PARENT_TICKET = "M90-PAA-3H"
FALLBACK_REASON = "coverage.dashing.row-specific-artifacts-required"

GATE = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-dashing-row-specific-evidence-gate.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-m90-dashing-evidence-gate.md"
INTAKE = ROOT / "reports/wgsl-pipeline/m90-path-aa-dashing-evidence-intake/summary.json"
ROUTE_CPU = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-cpu.json"
ROUTE_GPU = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-dashing/route-gpu.json"

EXPECTED_REQUIRED_EVIDENCE = [
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/stats.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/cpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-dashing/gpu-performance.json",
]
EXPECTED_HISTORICAL_SIGNALS = [
    "skia-integration-tests/src/test/kotlin/org/skia/tests/DashingTest.kt",
    "skia-integration-tests/src/main/kotlin/org/skia/tests/DashingGM.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/DashingCrossBackendTest.kt",
    "gpu-raster/test-similarity-scores-webgpu.properties#DashingGM-gpu=96.80; DashingGM-raster=96.08",
    "skia-integration-tests/test-similarity-scores.properties#DashingGM=96.06985294117646",
    "cpu-raster/test-similarity-scores.properties#DashingGM=96.06985294117646",
    "kanvas-skia/test-similarity-scores.properties#DashingGM=96.06985294117646",
]
EXPECTED_ROUTE_POLICY = (
    "Future support requires row-specific Skia reference, CPU/GPU route evidence, "
    "diff/stat artifacts, and fallbackReason=none without threshold, scoring, "
    "edge-budget, or fallback-policy changes."
)
EXPECTED_ROUTE_SHAPE_FACTS = [
    "dash intervals",
    "cap/join facts",
    "stroke outline",
]

FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
}

ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "scripts/validate_m90_dashing_evidence_gate.py",
    "reports/wgsl-pipeline/2026-06-08-m90-dashing-evidence-gate.md",
    "reports/wgsl-pipeline/scenes/generated/m90-dashing-row-specific-evidence-gate.json",
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


def validate_gate() -> None:
    data = load_json(GATE)
    require_exact_keys(
        data,
        {
            "schemaVersion",
            "generatedBy",
            "milestone",
            "ticket",
            "parentTicket",
            "classification",
            "sceneId",
            "inventoryId",
            "sourceGm",
            "status",
            "supportClaim",
            "fallbackReason",
            "evidenceGate",
            "promotionGate",
            "scoreImpact",
            "nonClaims",
        },
        "gate root",
    )
    require(data.get("schemaVersion") == 1, "gate schema version changed")
    require(data.get("milestone") == "M90", "gate milestone changed")
    require(data.get("ticket") == TICKET, "gate ticket mismatch")
    require(data.get("parentTicket") == PARENT_TICKET, "gate parent ticket mismatch")
    require(data.get("sceneId") == ROW_ID, "gate scene mismatch")
    require(data.get("inventoryId") == ROW_ID, "gate inventory mismatch")
    require(data.get("sourceGm") == SOURCE_GM, "gate source GM mismatch")
    require(data.get("status") == "dependency-gated", "gate status changed")
    require(data.get("supportClaim") is False, "gate must not claim support")
    require(data.get("fallbackReason") == FALLBACK_REASON, "gate fallback changed")

    evidence_gate = data.get("evidenceGate")
    require(isinstance(evidence_gate, dict), "missing evidenceGate")
    require_exact_keys(
        evidence_gate,
        {
            "kind",
            "requiredRouteStatus",
            "requiredFallbackReason",
            "requiredArtifacts",
            "currentlyPresentArtifacts",
            "historicalSignalsOnly",
        },
        "gate evidenceGate",
    )
    require(evidence_gate.get("kind") == "row-specific-reference-cpu-webgpu-artifact-bundle", "gate kind changed")
    require(evidence_gate.get("requiredRouteStatus") == "pass", "required route status changed")
    require(evidence_gate.get("requiredFallbackReason") == "none", "required fallback changed")
    require(evidence_gate.get("currentlyPresentArtifacts") == [], "gate must not count present artifacts")
    require(evidence_gate.get("requiredArtifacts") == EXPECTED_REQUIRED_EVIDENCE, "required artifact list changed")
    require(evidence_gate.get("historicalSignalsOnly") == EXPECTED_HISTORICAL_SIGNALS, "historical signal list changed")

    promotion = data.get("promotionGate")
    require(isinstance(promotion, dict), "missing promotionGate")
    expected_promotion_keys = {
        "promotionAllowed",
        "supportAllowedFromToleranceOnly",
        "supportAllowedFromHistoricalSignalsOnly",
        "supportAllowedWithoutRowSpecificArtifactBundle",
        "supportAllowedWithoutFallbackReasonNone",
    }
    require_exact_keys(promotion, expected_promotion_keys, "gate promotionGate")
    for key in expected_promotion_keys:
        require(promotion.get(key) is False, f"promotion flag changed: {key}")

    impact = data.get("scoreImpact")
    require(isinstance(impact, dict), "missing scoreImpact")
    require_exact_keys(
        impact,
        {
            "newSupportClaims",
            "readinessDelta",
            "dashboardPromotion",
            "thresholdChanged",
            "edgeBudgetChanged",
        },
        "gate scoreImpact",
    )
    require(impact.get("newSupportClaims") == 0, "gate must not add support claims")
    require(impact.get("readinessDelta") == 0.0, "gate readiness delta changed")
    require(impact.get("dashboardPromotion") is False, "gate dashboard promotion changed")
    require(impact.get("thresholdChanged") is False, "gate threshold changed")
    require(impact.get("edgeBudgetChanged") is False, "gate edge budget changed")

    non_claims = data.get("nonClaims")
    require(isinstance(non_claims, dict), "missing nonClaims")
    expected_non_claims = {
        "rowSupportClaimed",
        "broadPathAASupport",
        "broadDashSupport",
        "broadHairlineSupport",
        "broadStrokeSupport",
        "ganeshPort",
        "graphitePort",
        "dynamicSkSLCompiler",
        "thresholdChanged",
        "dashboardPromoted",
        "belowThresholdCountedAsProductionGap",
    }
    require_exact_keys(non_claims, expected_non_claims, "gate nonClaims")
    for key in expected_non_claims:
        require(non_claims.get(key) is False, f"gate nonClaim changed: {key}")


def validate_intake() -> None:
    intake = load_json(INTAKE)
    require(intake.get("ticket") == PARENT_TICKET, "intake ticket changed")
    require(intake.get("classification") == "path-aa-dashing-evidence-intake-no-new-rendering-support", "intake classification changed")
    require(intake.get("status") == "blocked-by-missing-row-specific-evidence", "intake status changed")

    row = intake.get("row")
    require(isinstance(row, dict), "intake missing row")
    require(row.get("rowId") == ROW_ID, "intake row changed")
    require(row.get("sourceGm") == SOURCE_GM, "intake source GM changed")
    require(row.get("status") == "expected-unsupported", "intake row status changed")
    require(row.get("supportClaim") is False, "intake row must not claim support")
    require(row.get("fallbackReason") == FALLBACK_REASON, "intake fallback changed")
    require(row.get("policyOnly") is True, "intake policyOnly changed")

    counters = intake.get("counters")
    require(isinstance(counters, dict), "intake missing counters")
    require(counters.get("requiredEvidenceItems") == 10, "required evidence count changed")
    require(counters.get("presentEvidenceItems") == 0, "present evidence count changed")
    require(counters.get("missingEvidenceItems") == 10, "missing evidence count changed")
    require(counters.get("historicalSignals") == 7, "historical signal count changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "historical signals must remain non-promotional")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake readiness delta changed")

    required_evidence = intake.get("requiredEvidence")
    require(isinstance(required_evidence, list), "intake missing requiredEvidence")
    require([item.get("path") for item in required_evidence if isinstance(item, dict)] == EXPECTED_REQUIRED_EVIDENCE, "intake required evidence paths changed")
    for item in required_evidence:
        require(isinstance(item, dict), "intake required evidence entries must be objects")
        require(item.get("present") is False, "intake required evidence must remain missing")
        require(item.get("promotional") is False, "intake required evidence must remain non-promotional")
        require(item.get("status") == "missing", "intake required evidence status changed")

    historical_signals = intake.get("historicalSignals")
    require(isinstance(historical_signals, list), "intake missing historicalSignals")
    observed_historical = []
    for item in historical_signals:
        require(isinstance(item, dict), "intake historical signal entries must be objects")
        path = item.get("path")
        observed = item.get("observed")
        observed_historical.append(f"{path}#{observed}" if isinstance(observed, str) else path)
        require(item.get("promotional") is False, "intake historical signals must remain non-promotional")
    require(observed_historical == EXPECTED_HISTORICAL_SIGNALS, "intake historical signal paths changed")

    next_ticket = intake.get("nextRecommendedTicket")
    require(isinstance(next_ticket, dict), "missing next recommended ticket")
    require(next_ticket.get("id") == TICKET, "next recommended ticket changed")
    require(next_ticket.get("supportClaimAllowed") is False, "next ticket must not allow support claims")
    require(next_ticket.get("promotionAllowedWithoutEvidence") is False, "next ticket must require evidence")

    support_guard = intake.get("supportGuard")
    require(isinstance(support_guard, dict), "missing support guard")
    expected_support_guard = {
        "supportClaimAdded",
        "readinessMoved",
        "policyOnlyPromoted",
        "thresholdChanged",
        "edgeBudgetChanged",
        "belowThresholdCountedAsProductionGap",
        "broadPathAASupport",
        "broadDashSupport",
        "broadHairlineSupport",
        "broadStrokeSupport",
        "ganeshPort",
        "graphitePort",
    }
    require_exact_keys(support_guard, expected_support_guard, "intake supportGuard")
    for key in expected_support_guard:
        require(support_guard.get(key) is False, f"intake support guard changed: {key}")


def validate_routes() -> None:
    expected_routes = {
        "CPU": "cpu.path.dashing.expected-unsupported",
        "WebGPU": "webgpu.path.dashing.expected-unsupported",
    }
    for path, backend in [(ROUTE_CPU, "CPU"), (ROUTE_GPU, "WebGPU")]:
        data = load_json(path)
        require_exact_keys(
            data,
            {
                "backend",
                "fallbackReason",
                "milestone",
                "nonClaims",
                "policy",
                "policyOnlyArtifact",
                "route",
                "rowId",
                "schemaVersion",
                "shapeFactsRequired",
                "sourceGm",
                "sourceRegistry",
                "sourceSelection",
                "status",
                "ticket",
            },
            f"{backend} route root",
        )
        require(data.get("schemaVersion") == 1, f"{backend} route schema version changed")
        require(data.get("milestone") == "M90", f"{backend} route milestone changed")
        require(data.get("ticket") == "M90-PAA-1", f"{backend} route ticket changed")
        require(data.get("rowId") == ROW_ID, f"{backend} route row mismatch")
        require(data.get("sourceGm") == SOURCE_GM, f"{backend} route source GM mismatch")
        require(data.get("backend") == backend, f"{backend} route backend mismatch")
        require(data.get("route") == expected_routes[backend], f"{backend} route id changed")
        require(data.get("status") == "expected-unsupported", f"{backend} route status changed")
        require(data.get("fallbackReason") == FALLBACK_REASON, f"{backend} route fallback changed")
        require(data.get("policy") == EXPECTED_ROUTE_POLICY, f"{backend} route policy changed")
        require(data.get("policyOnlyArtifact") is True, f"{backend} route must remain policy-only")
        require(data.get("sourceRegistry") == "reports/wgsl-pipeline/m89-gm-registry/registry.json", f"{backend} route source registry changed")
        require(data.get("sourceSelection") == "reports/wgsl-pipeline/m90-path-aa-slice/selection.json", f"{backend} route source selection changed")
        require(data.get("shapeFactsRequired") == EXPECTED_ROUTE_SHAPE_FACTS, f"{backend} route shape facts changed")
        non_claims = data.get("nonClaims")
        require(isinstance(non_claims, dict), f"{backend} route missing nonClaims")
        expected_route_non_claims = {
            "supportClaimAdded",
            "thresholdChanged",
            "edgeBudgetChanged",
            "belowThresholdCountedAsProductionGap",
            "broadPathAASupport",
            "broadDashSupport",
            "broadHairlineSupport",
            "broadStrokeSupport",
            "ganeshPort",
            "graphitePort",
            "policyOnlyPromoted",
        }
        require_exact_keys(non_claims, expected_route_non_claims, f"{backend} route nonClaims")
        for key in expected_route_non_claims:
            require(non_claims.get(key) is False, f"{backend} route nonClaim changed: {key}")


def validate_report() -> None:
    require_text(
        REPORT,
        [
            "M90 Dashing Evidence Gate",
            TICKET,
            "`DashingGM` /",
            "`skia-gm-dashing`",
            "`expected-unsupported`",
            FALLBACK_REASON,
            "Present row-specific evidence items: `0`",
            "Missing row-specific evidence items: `10`",
            "Historical signals: `7`, all non-promotional",
            "GPU score `DashingGM-gpu=96.80; DashingGM-raster=96.08`",
            "CPU score\n`96.06985294117646`",
            "cpu-raster mirror `96.06985294117646`",
            "kanvas-skia\nmirror `96.06985294117646`",
            "no\nstandalone WebGPU historical Dashing test",
            "m90-dashing-row-specific-evidence-gate.json",
            "No row support claim",
            "No broad Path AA support claim",
            "No broad dash support claim",
            "No broad hairline support claim",
            "No broad stroke support claim",
            "No dynamic SkSL compiler, IR, or VM",
            "No global threshold reduction",
            "No dashboard promotion",
            "below-threshold/tolerance-only case",
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

    validate_gate()
    validate_intake()
    validate_routes()
    validate_report()
    if args.check_worktree_scope:
        validate_worktree_scope()
    print(f"validated {TICKET} DashingGM evidence gate")


if __name__ == "__main__":
    main()
