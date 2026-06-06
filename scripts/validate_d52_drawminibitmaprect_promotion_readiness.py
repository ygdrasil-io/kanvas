#!/usr/bin/env python3
"""Validate D52-1 DrawMiniBitmapRect promotion readiness evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "D52-1"
ROW_ID = "skia-gm-drawminibitmaprect"
FALLBACK_REASON = "bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required"
FORBIDDEN_M66_ROW = "m66-bitmap-rect-nearest-skia"
SOURCE_DRAFT = "global/kanvas/tickets/drafts/brouillon-ticket-d52-1-preparer-paquet-promotion-drawminibitmaprect"

REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d52-1-drawminibitmaprect-promotion-readiness.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json"
D51_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md"
D51_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json"
D51_VALIDATOR = ROOT / "scripts/validate_d51_drawminibitmaprect_row_specific_evidence.py"
M66_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"
M66_STATS = ROOT / "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/stats.json"

EXPECTED_CHANGED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-d52-1-drawminibitmaprect-promotion-readiness.md",
    "reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json",
    "scripts/validate_d52_drawminibitmaprect_promotion_readiness.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d51-drawminibitmaprect-row-specific-evidence.json",
    "reports/wgsl-pipeline/2026-06-06-d51-1-drawminibitmaprect-row-specific-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "cpu-raster/",
    "gpu-raster/",
    "render-pipeline/",
    "skia-integration-tests/",
)
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_d52_drawminibitmaprect_promotion_readiness.py",
    "rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-promotion-readiness.json",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-drawminibitmaprect-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_promotion_readiness.py",
    "rtk git diff --check",
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
    require(changed == EXPECTED_CHANGED_FILES, f"changed files must be exactly D52-1 files, got: {sorted(changed)}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard or previous evidence changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_source_evidence() -> None:
    for path in (D51_REPORT, D51_EVIDENCE, D51_VALIDATOR, M66_MANIFEST, M66_STATS):
        require(path.is_file(), f"missing required input: {rel(path)}")

    d51 = load_json(D51_EVIDENCE)
    require(d51.get("ticket") == "D51-1", "D51 source ticket mismatch")
    require(d51.get("inventoryId") == ROW_ID, "D51 source inventory mismatch")
    require(d51.get("status") == "expected-unsupported", "D51 source status changed")
    require(d51.get("fallbackReason") == FALLBACK_REASON, "D51 source fallback changed")
    require(d51.get("rowSpecificAudit", {}).get("rowSpecificEvidenceInheritedFromM66") is False, "D51 must not inherit M66")
    require(d51.get("artifacts", {}).get("webgpu", {}).get("status") == "missing", "D51 WebGPU status changed")
    require(d51.get("artifacts", {}).get("diffStats", {}).get("status") == "not-computed", "D51 diff/stat status changed")
    require(d51.get("artifacts", {}).get("cpu", {}).get("status") == "available-historical-disabled-stress-test", "D51 CPU status changed")
    require(d51.get("artifacts", {}).get("reference", {}).get("status") == "available-historical-skia-integration-reference", "D51 reference status changed")


def require_m66_is_not_row_specific() -> None:
    m66 = load_json(M66_MANIFEST)
    scenes = m66.get("scenes")
    require(isinstance(scenes, list), "M66 scenes must be a list")
    matches = [row for row in scenes if isinstance(row, dict) and row.get("id") == FORBIDDEN_M66_ROW]
    require(len(matches) == 1, "M66 nearest bitmap row must exist exactly once")
    row = matches[0]
    require(row.get("inventoryId") == "skia-gm-drawbitmaprect", "M66 row must remain drawbitmaprect")
    require(row.get("fallbackReason") == "none", "M66 row is support for its own row only")
    require(row.get("gpuRoute") == "webgpu.image-rect.strict-nearest", "M66 GPU route changed")
    stats = load_json(M66_STATS)
    require(stats.get("dimensions") == {"width": 64, "height": 64}, "M66 stats must remain 64x64 smoke evidence")


def require_evidence(evidence: dict[str, Any]) -> None:
    require(evidence.get("ticket") == TICKET, "ticket mismatch")
    require(evidence.get("sourceDraftMemory") == SOURCE_DRAFT, "source draft memory mismatch")
    require(evidence.get("classification") == "promotion-readiness-no-new-rendering-support", "classification mismatch")
    require(evidence.get("inventoryId") == ROW_ID, "inventory id mismatch")
    require(evidence.get("status") == "expected-unsupported", "status must remain expected-unsupported")
    require(evidence.get("fallbackReason") == FALLBACK_REASON, "fallback reason mismatch")

    previous = evidence.get("previousEvidence", {})
    require(previous.get("ticket") == "D51-1", "previous evidence ticket mismatch")
    require(previous.get("report") == rel(D51_REPORT), "previous report link mismatch")
    require(previous.get("json") == rel(D51_EVIDENCE), "previous JSON link mismatch")
    require(previous.get("validator") == rel(D51_VALIDATOR), "previous validator link mismatch")
    require(previous.get("status") == "expected-unsupported", "previous status mismatch")
    require(previous.get("fallbackReason") == FALLBACK_REASON, "previous fallback mismatch")

    decision = evidence.get("readinessDecision", {})
    require(decision.get("promotableNow") is False, "row must not be promotable now")
    require(decision.get("retainExpectedUnsupported") is True, "row must retain expected-unsupported")
    require("reference, CPU, WebGPU, diff/stat, and route diagnostics" in decision.get("reason", ""), "decision must name required packet")

    classification = evidence.get("evidenceClassification", {})
    require(classification.get("reference", {}).get("status") == "available-historical-skia-integration-reference", "reference status mismatch")
    require(classification.get("reference", {}).get("promotableAlone") is False, "reference must not be promotable alone")
    require(classification.get("cpu", {}).get("status") == "available-historical-disabled-stress-test", "CPU status mismatch")
    require(classification.get("cpu", {}).get("promotableAlone") is False, "CPU must not be promotable alone")
    require(classification.get("webgpu", {}).get("status") == "missing", "WebGPU must be missing")
    require(classification.get("webgpu", {}).get("requiredForPromotion") is True, "WebGPU must be required")
    require(classification.get("diffStats", {}).get("status") == "missing", "diff/stat must be missing")
    require(classification.get("diffStats", {}).get("requiredForPromotion") is True, "diff/stat must be required")
    require(classification.get("routeDiagnostics", {}).get("status") == "missing-promotion-route", "route diagnostics promotion status mismatch")
    require(classification.get("routeDiagnostics", {}).get("availableDiagnosticsKind") == "expected-unsupported-refusal-only", "available route diagnostics must be refusal-only")

    m66 = evidence.get("m66InheritancePolicy", {})
    require(m66.get("forbiddenInheritedDashboardRowId") == FORBIDDEN_M66_ROW, "forbidden M66 row mismatch")
    require(m66.get("canInheritAsSupport") is False, "M66 support inheritance must be forbidden")
    require(m66.get("m66InventoryId") == "skia-gm-drawbitmaprect", "M66 inventory mismatch")
    require(m66.get("d52InventoryId") == ROW_ID, "D52 inventory mismatch")
    require("64x64 strict-nearest" in m66.get("whyNotReusable", ""), "M66 non-reuse reason must name smoke scope")
    require("1024x1024 stress grid" in m66.get("whyNotReusable", ""), "M66 non-reuse reason must name GM scope")

    next_slice = evidence.get("nextImplementationSlice", {})
    require(next_slice.get("id") == "D52-2-drawminibitmaprect-row-specific-artifacts", "next slice id mismatch")
    require("DrawMiniBitmapRectGM 1024x1024" in next_slice.get("goal", ""), "next slice must target the 1024x1024 GM")
    required_kinds = {item.get("kind") for item in next_slice.get("requiredArtifacts", []) if isinstance(item, dict)}
    require(required_kinds == {"reference", "cpu", "webgpu", "diff-stat", "route"}, f"next slice required artifact kinds mismatch: {sorted(required_kinds)}")
    gate = next_slice.get("acceptanceGate", {})
    require(gate.get("fallbackReasonNoneAllowedOnlyWhenWebGpuPasses") is True, "fallbackReason none gate missing")
    require(gate.get("globalThresholdChangeAllowed") is False, "global threshold change must be forbidden")
    require(gate.get("m66EvidenceInheritanceAllowed") is False, "M66 inheritance must be forbidden")
    require(gate.get("keepExpectedUnsupportedIfAnyArtifactMissing") is True, "missing artifact must keep expected-unsupported")

    score = evidence.get("scoreImpact", {})
    require(score.get("supportScoreIncreased") is False, "support score must not increase")
    require(score.get("skiaComparableScoreIncreased") is False, "Skia-comparable score must not increase")
    require(score.get("readinessScoreIncreased") is False, "readiness score must not increase")

    non_claims = evidence.get("nonClaims", {})
    for key, value in non_claims.items():
        require(value is False, f"{key} must remain false")

    require(evidence.get("validationCommands") == VALIDATION_COMMANDS, "validation commands mismatch")


def require_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    required = (
        "`skia-gm-drawminibitmaprect` reste `expected-unsupported`",
        FALLBACK_REASON,
        "`available-historical-skia-integration-reference`",
        "`available-historical-disabled-stress-test`",
        "`missing`",
        "`m66-bitmap-rect-nearest-skia` ne peut pas etre herite comme support",
        "GM 1024x1024",
        "`fallbackReason=none` que si la route WebGPU passe sans changer de",
        "Aucun gain de score n'est revendique",
        "Aucun support WebGPU pour `DrawMiniBitmapRectGM` n'est revendique",
    )
    for phrase in required:
        require(phrase in text, f"report missing phrase: {phrase}")
    for command in VALIDATION_COMMANDS:
        require(command in text, f"report missing validation command: {command}")


def main() -> None:
    evidence = load_json(EVIDENCE)
    require_scope(evidence)
    require_source_evidence()
    require_m66_is_not_row_specific()
    require_evidence(evidence)
    require_report()
    print(f"{TICKET} validation passed: {ROW_ID}=expected-unsupported readiness=not-promotable-now")


if __name__ == "__main__":
    main()
