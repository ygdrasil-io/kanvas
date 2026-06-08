#!/usr/bin/env python3
"""Validate the M89 GM registry PM counters artifact."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
PM_COUNTERS_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/pm-counters.json"
PM_COUNTERS_MD = ROOT / "reports/wgsl-pipeline/m89-gm-registry/pm-counters.md"

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
EXPECTED_STATUS = {
    "expected-unsupported": 25,
    "pass": 22,
}
EXPECTED_SOURCE = {
    "d50-visibility": 11,
    "d53-visibility": 9,
    "generated-dashboard": 27,
}
EXPECTED_FAMILY = {
    "bitmap-image": 7,
    "blend-color": 2,
    "gradient": 4,
    "image-filter": 5,
    "path-aa": 18,
    "runtime-effect": 3,
    "text-glyph": 7,
    "transform-layer": 1,
}
EXPECTED_FAMILY_STATUS = {
    "bitmap-image": {"expected-unsupported": 2, "pass": 5},
    "blend-color": {"expected-unsupported": 0, "pass": 2},
    "gradient": {"expected-unsupported": 1, "pass": 3},
    "image-filter": {"expected-unsupported": 3, "pass": 2},
    "path-aa": {"expected-unsupported": 13, "pass": 5},
    "runtime-effect": {"expected-unsupported": 2, "pass": 1},
    "text-glyph": {"expected-unsupported": 4, "pass": 3},
    "transform-layer": {"expected-unsupported": 0, "pass": 1},
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
    "scripts/m89_gm_registry_pm_counters.py",
    "scripts/validate_m89_gm_registry_pm_counters.py",
    "reports/wgsl-pipeline/m89-gm-registry/pm-counters.json",
    "reports/wgsl-pipeline/m89-gm-registry/pm-counters.md",
}
FORBIDDEN_PROMOTION_FILES = {
    "reports/wgsl-pipeline/m89-gm-registry/registry.json",
    "reports/wgsl-pipeline/m89-gm-registry/registry.md",
    "reports/wgsl-pipeline/scenes/generated/results.json",
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


def sorted_dict(counter: Counter[str] | dict[str, int]) -> dict[str, int]:
    return {key: counter[key] for key in sorted(counter)}


def row_has_dependency_gate(row: dict[str, Any]) -> bool:
    return bool(row.get("dependencyGateLinks")) or bool(row.get("textGlyphDependencyGateLinks"))


def row_is_below_threshold_excluded(row: dict[str, Any]) -> bool:
    text = " ".join(
        str(row.get(key, ""))
        for key in (
            "rowId",
            "title",
            "status",
            "fallbackReason",
            "nextTicketType",
            "nonClaim",
        )
    ).lower()
    return "below-threshold" in text or "tolerance-only" in text


def derive_family_status(rows: list[dict[str, Any]]) -> dict[str, dict[str, int]]:
    statuses = sorted({str(row.get("status")) for row in rows})
    grouped: dict[str, Counter[str]] = defaultdict(Counter)
    for row in rows:
        grouped[str(row.get("family"))][str(row.get("status"))] += 1
    return {
        family: {status: grouped[family].get(status, 0) for status in statuses}
        for family in sorted(grouped)
    }


def validate_artifact() -> None:
    registry = load_json(REGISTRY_JSON)
    report = load_json(PM_COUNTERS_JSON)
    markdown = PM_COUNTERS_MD.read_text(encoding="utf-8")

    require(report.get("schemaVersion") == 1, "schemaVersion mismatch")
    require(report.get("generatedBy") == "scripts/m89_gm_registry_pm_counters.py", "generatedBy mismatch")
    require(report.get("ticket") == "M89-PM-COUNTERS", "ticket mismatch")
    require(
        report.get("classification") == "gm-registry-pm-counters-reporting-only-no-support-promotion",
        "classification mismatch",
    )
    require(report.get("status") == "generated evidence", "status mismatch")
    require(
        report.get("inputs", {}).get("registryJson") == "reports/wgsl-pipeline/m89-gm-registry/registry.json",
        "registry input mismatch",
    )

    registry_counters = registry.get("counters")
    rows = registry.get("rows")
    require(isinstance(registry_counters, dict), "registry counters must be an object")
    require(isinstance(rows, list), "registry rows must be a list")
    typed_rows: list[dict[str, Any]] = []
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        typed_rows.append(row)

    for key, expected in EXPECTED_COUNTERS.items():
        require(registry_counters.get(key) == expected, f"registry counter changed: {key}")
        require(report.get("counters", {}).get(key) == expected, f"PM counter changed: {key}")
    require(registry_counters.get("status") == EXPECTED_STATUS, "registry status counters changed")
    require(registry_counters.get("source") == EXPECTED_SOURCE, "registry source counters changed")
    require(registry_counters.get("family") == EXPECTED_FAMILY, "registry family counters changed")

    status_counts = sorted_dict(Counter(str(row.get("status")) for row in typed_rows))
    source_counts = sorted_dict(Counter(str(row.get("source")) for row in typed_rows))
    family_counts = sorted_dict(Counter(str(row.get("family")) for row in typed_rows))
    family_status = derive_family_status(typed_rows)
    require(status_counts == EXPECTED_STATUS, "derived registry status counts changed")
    require(source_counts == EXPECTED_SOURCE, "derived registry source counts changed")
    require(family_counts == EXPECTED_FAMILY, "derived registry family counts changed")
    require(family_status == EXPECTED_FAMILY_STATUS, "derived family/status matrix changed")
    require(report["counters"].get("status") == status_counts, "PM status counts differ from rows")
    require(report["counters"].get("source") == source_counts, "PM source counts differ from rows")
    require(report["counters"].get("family") == family_counts, "PM family counts differ from rows")
    require(report.get("familyStatus") == family_status, "PM family/status matrix differs from rows")

    support_claims = sum(1 for row in typed_rows if row.get("supportClaim") is True)
    pass_no_claims = sum(1 for row in typed_rows if row.get("status") == "pass" and row.get("supportClaim") is not True)
    expected_unsupported_claims = sum(
        1
        for row in typed_rows
        if row.get("status") == "expected-unsupported" and row.get("supportClaim") is True
    )
    expected_unsupported_no_claims = sum(
        1
        for row in typed_rows
        if row.get("status") == "expected-unsupported" and row.get("supportClaim") is False
    )
    expected_unsupported_with_fallback = sum(
        1
        for row in typed_rows
        if row.get("status") == "expected-unsupported" and bool(row.get("fallbackReason"))
    )
    dependency_gated = sum(1 for row in typed_rows if row_has_dependency_gate(row))
    dependency_gated_status_rows = sum(1 for row in typed_rows if row.get("status") == "dependency-gated")
    dependency_gated_no_claims = sum(
        1 for row in typed_rows if row_has_dependency_gate(row) and row.get("supportClaim") is False
    )
    below_threshold_excluded = sum(1 for row in typed_rows if row_is_below_threshold_excluded(row))
    reporting_only_status_rows = sum(1 for row in typed_rows if row.get("status") == "reporting-only")
    reporting_only_claims = sum(
        1 for row in typed_rows if row.get("status") == "reporting-only" and row.get("supportClaim") is True
    )
    policy_only_visibility = sum(1 for row in typed_rows if row.get("policyOnly") is True)
    policy_only_visibility_claims = sum(
        1 for row in typed_rows if row.get("policyOnly") is True and row.get("supportClaim") is True
    )

    summary = report.get("supportRefusalSummary")
    require(isinstance(summary, dict), "supportRefusalSummary must be an object")
    require(summary.get("pass", {}).get("supportClaims") == support_claims, "pass/supportClaims mismatch")
    require(summary.get("pass", {}).get("noSupportClaims") == pass_no_claims, "pass/noSupportClaims mismatch")
    require(
        summary.get("expectedUnsupported", {}).get("supportClaims") == expected_unsupported_claims,
        "expectedUnsupported/supportClaims mismatch",
    )
    require(
        summary.get("expectedUnsupported", {}).get("noSupportClaims") == expected_unsupported_no_claims,
        "expectedUnsupported/noSupportClaims mismatch",
    )
    require(
        summary.get("expectedUnsupported", {}).get("withFallback") == expected_unsupported_with_fallback,
        "expectedUnsupported/withFallback mismatch",
    )
    require(
        summary.get("dependencyGated", {}).get("statusRows") == dependency_gated_status_rows,
        "dependencyGated statusRows mismatch",
    )
    require(summary.get("dependencyGated", {}).get("linkedRows") == dependency_gated, "dependencyGated linkedRows mismatch")
    require(
        summary.get("dependencyGated", {}).get("linkedRowsNoSupportClaims") == dependency_gated_no_claims,
        "dependencyGated linkedRowsNoSupportClaims mismatch",
    )
    require(
        summary.get("belowThresholdExcluded", {}).get("count") == below_threshold_excluded,
        "belowThresholdExcluded count mismatch",
    )
    require(
        summary.get("belowThresholdExcluded", {}).get("countedAsProductionGap") is False,
        "below-threshold rows must not count as production gaps",
    )
    require(
        summary.get("reportingOnly", {}).get("statusRows") == reporting_only_status_rows,
        "reportingOnly statusRows mismatch",
    )
    require(
        summary.get("reportingOnly", {}).get("supportClaims") == reporting_only_claims,
        "reportingOnly supportClaims mismatch",
    )
    require(
        summary.get("policyOnlyVisibility", {}).get("rows") == policy_only_visibility,
        "policyOnlyVisibility rows mismatch",
    )
    require(
        summary.get("policyOnlyVisibility", {}).get("supportClaims") == policy_only_visibility_claims,
        "policyOnlyVisibility supportClaims mismatch",
    )
    require(support_claims == 22, "support claim count changed")
    require(expected_unsupported_claims == 0, "expected-unsupported rows must not claim support")
    require(expected_unsupported_no_claims == 25, "expected-unsupported no-claim count changed")
    require(dependency_gated == 6, "dependency-gated count changed")
    require(dependency_gated_status_rows == 0, "dependency-gated status row count changed")
    require(below_threshold_excluded == 0, "below-threshold excluded count changed")
    require(reporting_only_status_rows == 0, "reporting-only status row count changed")
    require(policy_only_visibility == 20, "policy-only visibility count changed")

    require(report.get("nonClaims") == EXPECTED_NON_CLAIMS, "nonClaims changed")
    require(all(value is False for value in report["nonClaims"].values()), "all nonClaims must be false")

    required_markdown = [
        "# M89 GM Registry PM Counters",
        "Status: generated evidence",
        "Total rows: `47`",
        "Support claims: `22`",
        "Expected unsupported with fallback: `25`",
        "Linked M90 rows: `9`",
        "| Family | expected-unsupported | pass |",
        "| path-aa | `13` | `5` |",
        "`generated-dashboard`: `27`",
        "Expected-unsupported rows without support claims: `25`",
        "Dependency-gated status rows: `0`",
        "Dependency-gate linked rows: `6`",
        "Below-threshold/tolerance-only rows excluded from production missing-feature accounting: `0`",
        "Reporting-only status rows: `0`",
        "Policy-only visibility rows: `20`",
        "`dynamicSkSLCompiler`: `false`",
        "`ganeshPort`: `false`",
        "rtk ./gradlew --no-daemon pipelineM89GmRegistryPmCounters",
    ]
    for snippet in required_markdown:
        require(snippet in markdown, f"missing Markdown snippet: {snippet}")


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
    require(not forbidden, f"forbidden registry/dashboard files modified: {forbidden}")
    require(not unexpected, f"unexpected worktree paths for this scoped item: {unexpected}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--generate", action="store_true", help="regenerate PM counters before validation")
    parser.add_argument("--check-worktree-scope", action="store_true", help="also reject unrelated dirty worktree paths")
    args = parser.parse_args()

    if args.generate:
        subprocess.run(
            ["python3", "scripts/m89_gm_registry_pm_counters.py"],
            cwd=ROOT,
            check=True,
        )
    validate_artifact()
    if args.check_worktree_scope:
        validate_worktree_scope()
    print("validate_m89_gm_registry_pm_counters: PASS")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"validate_m89_gm_registry_pm_counters: FAIL: {error}", file=sys.stderr)
        sys.exit(1)
