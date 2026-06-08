#!/usr/bin/env python3
"""Generate PM-friendly M89 GM registry counters without support promotion."""

from __future__ import annotations

import json
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
}

NON_CLAIMS = {
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


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


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
    matrix: dict[str, dict[str, int]] = {}
    grouped: dict[str, Counter[str]] = defaultdict(Counter)
    for row in rows:
        grouped[str(row.get("family"))][str(row.get("status"))] += 1
    for family in sorted(grouped):
        matrix[family] = {status: grouped[family].get(status, 0) for status in statuses}
    return matrix


def build_report(registry: dict[str, Any]) -> dict[str, Any]:
    counters = registry.get("counters")
    rows = registry.get("rows")
    require(isinstance(counters, dict), "registry counters must be an object")
    require(isinstance(rows, list), "registry rows must be a list")
    typed_rows: list[dict[str, Any]] = []
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        typed_rows.append(row)

    for key, expected in EXPECTED_COUNTERS.items():
        require(counters.get(key) == expected, f"registry counter changed: {key}")

    status_counts = sorted_dict(Counter(str(row.get("status")) for row in typed_rows))
    family_counts = sorted_dict(Counter(str(row.get("family")) for row in typed_rows))
    source_counts = sorted_dict(Counter(str(row.get("source")) for row in typed_rows))
    support_claims = sum(1 for row in typed_rows if row.get("supportClaim") is True)
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

    require(status_counts == counters.get("status"), "derived status counts differ from registry counters")
    require(family_counts == counters.get("family"), "derived family counts differ from registry counters")
    require(source_counts == counters.get("source"), "derived source counts differ from registry counters")
    require(support_claims == counters.get("supportClaims"), "derived supportClaims differ from registry counters")
    require(
        expected_unsupported_with_fallback == counters.get("expectedUnsupportedWithFallback"),
        "derived expectedUnsupportedWithFallback differs from registry counters",
    )

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m89_gm_registry_pm_counters.py",
        "ticket": "M89-PM-COUNTERS",
        "classification": "gm-registry-pm-counters-reporting-only-no-support-promotion",
        "status": "generated evidence",
        "inputs": {
            "registryJson": "reports/wgsl-pipeline/m89-gm-registry/registry.json",
        },
        "counters": {
            **{key: counters[key] for key in EXPECTED_COUNTERS},
            "status": status_counts,
            "source": source_counts,
            "family": family_counts,
        },
        "familyStatus": derive_family_status(typed_rows),
        "supportRefusalSummary": {
            "pass": {
                "supportClaims": support_claims,
                "noSupportClaims": sum(
                    1 for row in typed_rows if row.get("status") == "pass" and row.get("supportClaim") is not True
                ),
            },
            "expectedUnsupported": {
                "supportClaims": sum(
                    1
                    for row in typed_rows
                    if row.get("status") == "expected-unsupported" and row.get("supportClaim") is True
                ),
                "noSupportClaims": expected_unsupported_no_claims,
                "withFallback": expected_unsupported_with_fallback,
            },
            "dependencyGated": {
                "statusRows": sum(1 for row in typed_rows if row.get("status") == "dependency-gated"),
                "linkedRows": sum(1 for row in typed_rows if row_has_dependency_gate(row)),
                "linkedRowsNoSupportClaims": sum(
                    1 for row in typed_rows if row_has_dependency_gate(row) and row.get("supportClaim") is False
                ),
            },
            "belowThresholdExcluded": {
                "count": sum(1 for row in typed_rows if row_is_below_threshold_excluded(row)),
                "countedAsProductionGap": False,
            },
            "reportingOnly": {
                "statusRows": sum(1 for row in typed_rows if row.get("status") == "reporting-only"),
                "supportClaims": sum(
                    1 for row in typed_rows if row.get("status") == "reporting-only" and row.get("supportClaim") is True
                ),
            },
            "policyOnlyVisibility": {
                "rows": sum(1 for row in typed_rows if row.get("policyOnly") is True),
                "supportClaims": sum(
                    1 for row in typed_rows if row.get("policyOnly") is True and row.get("supportClaim") is True
                ),
            },
        },
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m89_gm_registry_pm_counters.py",
            "rtk python3 scripts/validate_m89_gm_registry_pm_counters.py --check-worktree-scope",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m89-pm-counters-pycache python3 -m py_compile scripts/m89_gm_registry_pm_counters.py scripts/validate_m89_gm_registry_pm_counters.py",
            "rtk ./gradlew --no-daemon pipelineM89GmRegistryPmCounters",
            "rtk git diff --check",
        ],
    }


def markdown_table(headers: list[str], rows: list[list[str]]) -> list[str]:
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    lines.extend("| " + " | ".join(row) + " |" for row in rows)
    return lines


def render_markdown(report: dict[str, Any]) -> str:
    counters = report["counters"]
    summary = report["supportRefusalSummary"]
    family_status = report["familyStatus"]
    lines = [
        "# M89 GM Registry PM Counters",
        "",
        "Status: generated evidence",
        "",
        "This PM report derives counters from the existing M89 GM registry. It does not mutate registry rows, promote support, change thresholds, change edge budgets, or add rendering support claims.",
        "",
        "## Counter Summary",
        "",
        f"- Total rows: `{counters['totalRows']}`",
        f"- Support claims: `{counters['supportClaims']}`",
        f"- Policy-only rows: `{counters['policyOnlyRows']}`",
        f"- Expected unsupported with fallback: `{counters['expectedUnsupportedWithFallback']}`",
        f"- Unlinked unsupported rows: `{counters['unlinkedUnsupportedRows']}`",
        f"- Linked M66 rows: `{counters['linkedM66Rows']}`",
        f"- Linked M86 rows: `{counters['linkedM86Rows']}`",
        "",
        "## Status Counts",
        "",
    ]
    lines.extend(f"- `{key}`: `{value}`" for key, value in counters["status"].items())
    lines.extend(["", "## Family/Status Matrix", ""])
    statuses = sorted(counters["status"])
    matrix_rows = [
        [family] + [f"`{counts.get(status, 0)}`" for status in statuses]
        for family, counts in family_status.items()
    ]
    lines.extend(markdown_table(["Family", *statuses], matrix_rows))
    lines.extend(["", "## Source Counts", ""])
    lines.extend(f"- `{key}`: `{value}`" for key, value in counters["source"].items())
    lines.extend(
        [
            "",
            "## Support/Refusal Summary",
            "",
            f"- Pass rows with support claims: `{summary['pass']['supportClaims']}`",
            f"- Pass rows without support claims: `{summary['pass']['noSupportClaims']}`",
            f"- Expected-unsupported rows without support claims: `{summary['expectedUnsupported']['noSupportClaims']}`",
            f"- Expected-unsupported rows with fallback: `{summary['expectedUnsupported']['withFallback']}`",
            f"- Dependency-gated status rows: `{summary['dependencyGated']['statusRows']}`",
            f"- Dependency-gate linked rows: `{summary['dependencyGated']['linkedRows']}`",
            f"- Below-threshold/tolerance-only rows excluded from production missing-feature accounting: `{summary['belowThresholdExcluded']['count']}`",
            f"- Reporting-only status rows: `{summary['reportingOnly']['statusRows']}`",
            f"- Policy-only visibility rows: `{summary['policyOnlyVisibility']['rows']}`",
            "",
            "## Non-Claims",
            "",
        ]
    )
    lines.extend(f"- `{key}`: `{str(value).lower()}`" for key, value in report["nonClaims"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in report["validationCommands"])
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    registry = load_json(REGISTRY_JSON)
    report = build_report(registry)
    write_json(PM_COUNTERS_JSON, report)
    PM_COUNTERS_MD.write_text(render_markdown(report), encoding="utf-8")
    print(f"m89_gm_registry_pm_counters: wrote {rel(PM_COUNTERS_JSON)} and {rel(PM_COUNTERS_MD)}")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"m89_gm_registry_pm_counters: FAIL: {error}", file=sys.stderr)
        sys.exit(1)
