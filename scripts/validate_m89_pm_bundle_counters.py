#!/usr/bin/env python3
"""Validate M89 PM counters exposure in the generated PM bundle."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "M89-PM-BUNDLE-COUNTERS-GATE"
BUNDLE_ROOT = ROOT / "build/reports/wgsl-pipeline-pm-bundle"
MANIFEST = BUNDLE_ROOT / "manifest.json"
README = BUNDLE_ROOT / "README.md"
SOURCE_PM_COUNTERS = ROOT / "reports/wgsl-pipeline/m89-gm-registry/pm-counters.json"
BUNDLE_PM_COUNTERS = BUNDLE_ROOT / "registry/m89-gm-registry/pm-counters.json"
BUNDLE_PM_COUNTERS_MD = BUNDLE_ROOT / "registry/m89-gm-registry/pm-counters.md"

EXPECTED_COUNTERS = {
    "totalRows": 47,
    "supportClaims": 22,
    "policyOnlyRows": 20,
    "expectedUnsupportedWithFallback": 25,
    "unlinkedUnsupportedRows": 0,
    "linkedM66Rows": 18,
    "linkedM86Rows": 18,
    "linkedM90Rows": 9,
}
EXPECTED_NON_CLAIMS = {
    "supportPromotion": False,
    "registryRowMutation": False,
    "dashboardPromotion": False,
    "readinessPromotion": False,
    "thresholdChange": False,
    "edgeBudgetChange": False,
    "broadPathAASupport": False,
    "broadDashSupport": False,
    "broadHairlineSupport": False,
    "broadStrokeSupport": False,
    "ganeshPort": False,
    "graphitePort": False,
    "dynamicSkSLCompiler": False,
    "dynamicSkSLIR": False,
    "dynamicSkSLVM": False,
    "belowThresholdCountedAsProductionGap": False,
}
ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "scripts/validate_m89_pm_bundle_counters.py",
}
FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/m89-gm-registry/registry.md",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
}


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} root must be an object")
    return data


def require_false_map(data: dict[str, Any], label: str) -> None:
    require(isinstance(data, dict), f"{label} must be an object")
    for key, value in data.items():
        require(value is False, f"{label}.{key} must stay false")


def validate_manifest() -> None:
    manifest = load_json(MANIFEST)
    source_pm = load_json(SOURCE_PM_COUNTERS)
    bundle_pm = load_json(BUNDLE_PM_COUNTERS)
    readme = README.read_text(encoding="utf-8")
    bundle_pm_md = BUNDLE_PM_COUNTERS_MD.read_text(encoding="utf-8")

    require(manifest.get("generatedBy") == "pipelinePmBundle", "manifest generatedBy changed")
    require(manifest.get("generationCommand") == "rtk ./gradlew --no-daemon pipelinePmBundle", "manifest generation command changed")
    entry = manifest.get("m89GmRegistry")
    require(isinstance(entry, dict), "manifest missing m89GmRegistry")
    require(entry.get("pmCountersJson") == "registry/m89-gm-registry/pm-counters.json", "manifest pmCountersJson mismatch")
    require(entry.get("pmCountersReport") == "registry/m89-gm-registry/pm-counters.md", "manifest pmCountersReport mismatch")
    require(entry.get("registryJson") == "registry/m89-gm-registry/registry.json", "manifest registryJson mismatch")
    require(entry.get("closeoutJson") == "registry/m89-gm-registry/closeout.json", "manifest closeoutJson mismatch")

    require(source_pm == bundle_pm, "bundled PM counters JSON differs from source artifact")
    require(BUNDLE_PM_COUNTERS_MD.is_file(), "bundled PM counters Markdown missing")
    for snippet in [
        "M89 GM Registry PM Counters",
        "PM counters are reporting-only evidence",
        "no registry row mutation or support promotion",
        "Policy-only visibility rows do not count as support",
        "threshold-only misses remain fidelity burn-down scope",
    ]:
        require(snippet in readme or snippet in bundle_pm_md, f"PM bundle wording missing: {snippet}")

    pm_summary = entry.get("pmCounters")
    require(isinstance(pm_summary, dict), "manifest missing pmCounters summary")
    require(pm_summary.get("classification") == "gm-registry-pm-counters-reporting-only-no-support-promotion", "pmCounters classification mismatch")
    require(pm_summary.get("status") == "generated evidence", "pmCounters status mismatch")
    for key, expected in EXPECTED_COUNTERS.items():
        require(pm_summary.get(key) == expected, f"manifest pmCounters.{key} changed")
        require(source_pm.get("counters", {}).get(key) == expected, f"source pm-counters {key} changed")
    require(pm_summary.get("nonClaims") == EXPECTED_NON_CLAIMS, "manifest pmCounters nonClaims mismatch")
    require_false_map(pm_summary["nonClaims"], "manifest pmCounters nonClaims")

    source_summary = source_pm.get("supportRefusalSummary")
    require(isinstance(source_summary, dict), "source PM counters missing supportRefusalSummary")
    require(source_summary.get("reportingOnly", {}).get("statusRows") == 0, "reporting-only status row count changed")
    require(source_summary.get("policyOnlyVisibility", {}).get("rows") == 20, "policy-only visibility count changed")
    require(source_summary.get("dependencyGated", {}).get("statusRows") == 0, "dependency-gated status row count changed")
    require(source_summary.get("dependencyGated", {}).get("linkedRows") == 6, "dependency-gate linked row count changed")
    require(
        source_summary.get("belowThresholdExcluded", {}).get("countedAsProductionGap") is False,
        "below-threshold rows must not count as production gaps",
    )

    closeout = entry.get("closeout")
    require(isinstance(closeout, dict), "manifest missing M89 closeout summary")
    require(closeout.get("readinessBefore") == closeout.get("readinessAfter") == 67.75, "M89 closeout readiness changed")
    require(closeout.get("readinessDelta") == 0.0, "M89 closeout readiness delta changed")
    require(closeout.get("linkedM90Rows") == 9, "M89 closeout linkedM90Rows changed")
    require("M90" in str(closeout.get("nextRecommendedSlices")), "M89 closeout must keep M90 next slice visible")


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

    validate_manifest()
    if args.check_worktree_scope:
        validate_worktree_scope()
    print(f"validated {TICKET} PM bundle counters exposure")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"validate_m89_pm_bundle_counters: FAIL: {error}", file=sys.stderr)
        sys.exit(1)
