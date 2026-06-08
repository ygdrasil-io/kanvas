#!/usr/bin/env python3
"""Validate the M90-PAA-3E-REF StrokeRectsGM evidence gate."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
ROW_ID = "skia-gm-strokerects"
SOURCE_GM = "StrokeRectsGM"
TICKET = "M90-PAA-3E-REF"
PARENT_TICKET = "M90-PAA-3E"
FALLBACK_REASON = "coverage.stroke-rects.row-specific-artifacts-required"

GATE = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-strokerects-row-specific-evidence-gate.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-m90-strokerects-evidence-gate.md"
INTAKE = ROOT / "reports/wgsl-pipeline/m90-path-aa-strokerects-evidence-intake/summary.json"
ROUTE_CPU = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-cpu.json"
ROUTE_GPU = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-strokerects/route-gpu.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects"

REQUIRED_ARTIFACTS = [
    "skia.png",
    "cpu.png",
    "gpu.png",
    "cpu-diff.png",
    "gpu-diff.png",
    "route-cpu.json",
    "route-gpu.json",
    "stats.json",
    "cpu-performance.json",
    "gpu-performance.json",
]
EXPECTED_REQUIRED_EVIDENCE = [
    f"reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/{name}"
    for name in REQUIRED_ARTIFACTS
]
EXPECTED_INTAKE_REQUIRED_EVIDENCE = [
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/stats.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/cpu-performance.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-strokerects/gpu-performance.json",
]
EXPECTED_HISTORICAL_SIGNALS = [
    "skia-integration-tests/src/test/kotlin/org/skia/tests/StrokeRectsTest.kt",
    "skia-integration-tests/src/main/kotlin/org/skia/tests/StrokeRectsGM.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeRectsWebGpuTest.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/StrokeRectsCrossBackendTest.kt",
    "gpu-raster/test-similarity-scores-webgpu.properties#StrokeRectsGM=99.86",
    "skia-integration-tests/test-similarity-scores.properties#StrokeRectsGM=92.0928125",
    "cpu-raster/test-similarity-scores.properties#StrokeRectsGM=92.0928125",
    "kanvas-skia/test-similarity-scores.properties#StrokeRectsGM=92.0928125",
]

FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
}

ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "scripts/validate_m90_strokerects_evidence_gate.py",
    "reports/wgsl-pipeline/2026-06-08-m90-strokerects-evidence-gate.md",
    "reports/wgsl-pipeline/scenes/generated/m90-strokerects-row-specific-evidence-gate.json",
}


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


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
    require(data.get("ticket") == TICKET, "gate ticket mismatch")
    require(data.get("parentTicket") == PARENT_TICKET, "gate parent ticket mismatch")
    require(data.get("sceneId") == ROW_ID, "gate scene mismatch")
    require(data.get("sourceGm") == SOURCE_GM, "gate source GM mismatch")
    require(data.get("status") == "dependency-gated", "gate status changed")
    require(data.get("supportClaim") is False, "gate must not claim support")
    require(data.get("fallbackReason") == FALLBACK_REASON, "gate fallback changed")

    evidence_gate = data.get("evidenceGate")
    require(isinstance(evidence_gate, dict), "missing evidenceGate")
    require(evidence_gate.get("kind") == "row-specific-reference-cpu-webgpu-artifact-bundle", "gate kind changed")
    require(evidence_gate.get("requiredRouteStatus") == "pass", "required route status changed")
    require(evidence_gate.get("requiredFallbackReason") == "none", "required fallback changed")
    require(evidence_gate.get("currentlyPresentArtifacts") == [], "gate must not count present artifacts")
    require(evidence_gate.get("requiredArtifacts") == EXPECTED_REQUIRED_EVIDENCE, "required artifact list changed")
    require(evidence_gate.get("historicalSignalsOnly") == EXPECTED_HISTORICAL_SIGNALS, "historical signal list changed")

    promotion = data.get("promotionGate")
    require(isinstance(promotion, dict), "missing promotionGate")
    for key in [
        "promotionAllowed",
        "supportAllowedFromToleranceOnly",
        "supportAllowedFromHistoricalSignalsOnly",
        "supportAllowedWithoutRowSpecificArtifactBundle",
        "supportAllowedWithoutFallbackReasonNone",
    ]:
        require(promotion.get(key) is False, f"promotion flag changed: {key}")

    impact = data.get("scoreImpact")
    require(isinstance(impact, dict), "missing scoreImpact")
    require(impact.get("newSupportClaims") == 0, "gate must not add support claims")
    require(impact.get("readinessDelta") == 0.0, "gate readiness delta changed")
    require(impact.get("dashboardPromotion") is False, "gate dashboard promotion changed")
    require(impact.get("thresholdChanged") is False, "gate threshold changed")
    require(impact.get("edgeBudgetChanged") is False, "gate edge budget changed")

    non_claims = data.get("nonClaims")
    require(isinstance(non_claims, dict), "missing nonClaims")
    for key in [
        "rowSupportClaimed",
        "broadPathAASupport",
        "broadStrokeSupport",
        "ganeshPort",
        "graphitePort",
        "dynamicSkSLCompiler",
        "thresholdChanged",
        "dashboardPromoted",
        "belowThresholdCountedAsProductionGap",
    ]:
        require(non_claims.get(key) is False, f"gate nonClaim changed: {key}")


def validate_intake() -> None:
    intake = load_json(INTAKE)
    require(intake.get("ticket") == PARENT_TICKET, "intake ticket changed")
    require(intake.get("classification") == "path-aa-strokerects-evidence-intake-no-new-rendering-support", "intake classification changed")
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
    require(counters.get("historicalSignals") == 8, "historical signal count changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "historical signals must remain non-promotional")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake readiness delta changed")

    required_evidence = intake.get("requiredEvidence")
    require(isinstance(required_evidence, list), "intake missing requiredEvidence")
    require([item.get("path") for item in required_evidence if isinstance(item, dict)] == EXPECTED_INTAKE_REQUIRED_EVIDENCE, "intake required evidence paths changed")
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


def validate_routes() -> None:
    for path, backend in [(ROUTE_CPU, "CPU"), (ROUTE_GPU, "WebGPU")]:
        data = load_json(path)
        require(data.get("rowId") == ROW_ID, f"{backend} route row mismatch")
        require(data.get("sourceGm") == SOURCE_GM, f"{backend} route source GM mismatch")
        require(data.get("backend") == backend, f"{backend} route backend mismatch")
        require(data.get("status") == "expected-unsupported", f"{backend} route status changed")
        require(data.get("fallbackReason") == FALLBACK_REASON, f"{backend} route fallback changed")
        require(data.get("policyOnlyArtifact") is True, f"{backend} route must remain policy-only")
        non_claims = data.get("nonClaims")
        require(isinstance(non_claims, dict), f"{backend} route missing nonClaims")
        for key in [
            "supportClaimAdded",
            "thresholdChanged",
            "edgeBudgetChanged",
            "belowThresholdCountedAsProductionGap",
            "broadPathAASupport",
            "broadStrokeSupport",
            "ganeshPort",
            "graphitePort",
        ]:
            require(non_claims.get(key) is False, f"{backend} route nonClaim changed: {key}")


def validate_report() -> None:
    require_text(
        REPORT,
        [
            "M90 StrokeRects Evidence Gate",
            TICKET,
            "`StrokeRectsGM` /",
            "`skia-gm-strokerects`",
            "`expected-unsupported`",
            FALLBACK_REASON,
            "Present row-specific evidence items: `0`",
            "Missing row-specific evidence items: `10`",
            "Historical signals: `8`, all non-promotional",
            "m90-strokerects-row-specific-evidence-gate.json",
            "No broad Path AA support claim",
            "No dynamic SkSL compiler, IR, or VM",
            "No global threshold reduction",
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
    print(f"validated {TICKET} StrokeRectsGM evidence gate")


if __name__ == "__main__":
    main()
