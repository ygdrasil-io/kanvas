#!/usr/bin/env python3
"""Validate the M89 Skia-like GM support/refusal registry."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
REPORT = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.md"

EXPECTED_COUNTERS = {
    "totalRows": 47,
    "supportClaims": 22,
    "policyOnlyRows": 20,
    "rowSpecificRefusalRows": 4,
    "groupedPolicyRefusalRows": 9,
    "expectedUnsupportedWithFallback": 25,
    "linkedM66Rows": 18,
    "linkedM86Rows": 18,
}
EXPECTED_ROW_SPECIFIC_REFUSALS = {
    "skia-gm-image": {
        "linear": "FOR-466",
        "fallbackReason": "image.imagegm.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    },
    "skia-gm-imagesource": {
        "linear": "FOR-467",
        "fallbackReason": "image.imagesource.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    },
    "skia-gm-offsetimagefilter": {
        "linear": "FOR-468",
        "fallbackReason": "image-filter.offset.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md",
    },
    "skia-gm-pathfill": {
        "linear": "FOR-469",
        "fallbackReason": "path-aa.fill.row-specific-artifacts-required",
        "json": "reports/wgsl-pipeline/scenes/generated/for469-skia-gm-pathfill-evidence.json",
        "report": "reports/wgsl-pipeline/2026-06-06-for-469-skia-gm-pathfill-evidence.md",
    },
}
EXPECTED_GROUPED_POLICY_REFUSALS = {
    "skia-gm-dashcubics": "coverage.dash-cubic.row-specific-artifacts-required",
    "skia-gm-dashing": "coverage.dashing.row-specific-artifacts-required",
    "skia-gm-hairlines": "coverage.hairline.row-specific-artifacts-required",
    "skia-gm-hairmodes": "coverage.hairmode.row-specific-artifacts-required",
    "skia-gm-scaledstrokes": "coverage.scaled-stroke.row-specific-artifacts-required",
    "skia-gm-strokedlines": "coverage.stroked-lines.row-specific-artifacts-required",
    "skia-gm-strokerect": "coverage.stroke-rect.row-specific-artifacts-required",
    "skia-gm-strokerects": "coverage.stroke-rects.row-specific-artifacts-required",
    "skia-gm-thinstrokedrects": "coverage.thin-stroked-rects.row-specific-artifacts-required",
}
EXPECTED_SOURCE_COUNTS = {
    "d50-visibility": 11,
    "d53-visibility": 9,
    "generated-dashboard": 27,
}
EXPECTED_STATUS_COUNTS = {
    "expected-unsupported": 25,
    "pass": 22,
}
EXPECTED_FAMILY_COUNTS = {
    "bitmap-image": 7,
    "blend-color": 2,
    "gradient": 4,
    "image-filter": 5,
    "path-aa": 18,
    "runtime-effect": 3,
    "text-glyph": 7,
    "transform-layer": 1,
}
POLICY_SOURCES = {"d50-visibility", "d53-visibility"}


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


def validate_registry() -> None:
    registry = load_json(REGISTRY)
    require(registry.get("schemaVersion") == 1, "schemaVersion mismatch")
    require(registry.get("generatedBy") == "scripts/m89_gm_registry.py", "generatedBy mismatch")

    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    require(len(rows) == EXPECTED_COUNTERS["totalRows"], "registry row count mismatch")

    row_ids: list[str] = []
    status_counts: Counter[str] = Counter()
    source_counts: Counter[str] = Counter()
    family_counts: Counter[str] = Counter()
    support_claims = 0
    policy_only_rows = 0
    expected_unsupported_with_fallback = 0
    linked_m66_rows = 0
    linked_m86_rows = 0
    row_specific_refusal_rows = 0
    grouped_policy_refusal_rows = 0

    for index, row in enumerate(rows):
        require(isinstance(row, dict), f"rows[{index}] must be an object")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, f"rows[{index}] missing rowId")
        row_ids.append(row_id)

        source = row.get("source")
        status = row.get("status")
        family = row.get("family")
        fallback = row.get("fallbackReason")
        support_claim = row.get("supportClaim")
        policy_only = row.get("policyOnly")
        evidence_links = row.get("evidenceLinks")
        row_specific_refusals = row.get("rowSpecificRefusals")
        grouped_policy_refusals = row.get("groupedPolicyRefusals")

        require(isinstance(source, str) and source, f"{row_id}: missing source")
        require(isinstance(status, str) and status, f"{row_id}: missing status")
        require(isinstance(family, str) and family, f"{row_id}: missing family")
        require(isinstance(fallback, str) and fallback, f"{row_id}: missing fallbackReason")
        require(isinstance(support_claim, bool), f"{row_id}: supportClaim must be boolean")
        require(isinstance(policy_only, bool), f"{row_id}: policyOnly must be boolean")
        require(isinstance(evidence_links, dict), f"{row_id}: evidenceLinks must be object")
        require(isinstance(row_specific_refusals, list), f"{row_id}: rowSpecificRefusals must be list")
        require(isinstance(grouped_policy_refusals, list), f"{row_id}: groupedPolicyRefusals must be list")
        require(row.get("referenceKind") in {"skia-upstream", "skia-derived", "cpu-oracle", "test-oracle", "none"}, f"{row_id}: invalid referenceKind")
        require(row.get("nextTicketType") in {"implementation", "dependency", "fidelity-burndown", "policy-visibility", "performance-gate"}, f"{row_id}: invalid nextTicketType")
        require(row.get("pmImpact") in {"high", "medium", "low"}, f"{row_id}: invalid pmImpact")

        status_counts[status] += 1
        source_counts[source] += 1
        family_counts[family] += 1
        support_claims += int(support_claim)
        policy_only_rows += int(policy_only)
        expected_unsupported_with_fallback += int(status == "expected-unsupported" and fallback != "none")
        linked_m66_rows += int("m66" in evidence_links)
        linked_m86_rows += int("m86" in evidence_links)
        row_specific_refusal_rows += int(bool(row_specific_refusals))
        grouped_policy_refusal_rows += int(bool(grouped_policy_refusals))

        if status == "pass":
            require(source == "generated-dashboard", f"{row_id}: only generated dashboard rows may be pass in M89")
            require(support_claim, f"{row_id}: pass row must claim support")
            require(fallback == "none", f"{row_id}: pass row must keep fallbackReason=none")
            require(row.get("routeGpu") == "pass", f"{row_id}: pass row must keep routeGpu=pass")
        if status == "expected-unsupported":
            require(not support_claim, f"{row_id}: expected-unsupported must not claim support")
            require(fallback != "none", f"{row_id}: expected-unsupported must have stable fallback")
            require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: expected-unsupported must keep routeGpu expected-unsupported")
        if source in POLICY_SOURCES:
            require(policy_only, f"{row_id}: policy visibility row must be policyOnly")
            require(status == "expected-unsupported", f"{row_id}: policy visibility row must remain expected-unsupported")
            require(not support_claim, f"{row_id}: policy visibility row must not claim support")
            require(row.get("nextTicketType") == "policy-visibility", f"{row_id}: policy visibility row must keep policy-visibility next action")

        expected_refusal = EXPECTED_ROW_SPECIFIC_REFUSALS.get(row_id)
        if expected_refusal is None:
            require(not row_specific_refusals, f"{row_id}: unexpected row-specific refusal link")
        else:
            require(len(row_specific_refusals) == 1, f"{row_id}: expected exactly one row-specific refusal link")
            refusal = row_specific_refusals[0]
            require(isinstance(refusal, dict), f"{row_id}: row-specific refusal link must be object")
            require(policy_only, f"{row_id}: row-specific refusal remains attached to policy visibility row")
            require(status == "expected-unsupported", f"{row_id}: row-specific refusal must remain expected-unsupported")
            require(not support_claim, f"{row_id}: row-specific refusal must not claim support")
            require(refusal.get("linear") == expected_refusal["linear"], f"{row_id}: row-specific refusal Linear mismatch")
            require(
                refusal.get("classification") == "row-specific-expected-unsupported-no-support-claim",
                f"{row_id}: row-specific refusal classification mismatch",
            )
            require(refusal.get("json") == expected_refusal["json"], f"{row_id}: row-specific refusal JSON path mismatch")
            require(refusal.get("report") == expected_refusal["report"], f"{row_id}: row-specific refusal report path mismatch")
            require(refusal.get("fallbackReason") == expected_refusal["fallbackReason"], f"{row_id}: row-specific refusal fallback mismatch")
            require(refusal.get("registryFallbackReason") == fallback, f"{row_id}: registry fallback link mismatch")
            require(refusal.get("referenceStatus") in {"not-generated", "available-historical-skia-integration-reference"}, f"{row_id}: reference status must not imply support")
            require(refusal.get("cpuStatus") in {"expected-unsupported", "available-historical-disabled-stress-test"}, f"{row_id}: CPU status must not imply support")
            require(refusal.get("gpuStatus") == "expected-unsupported", f"{row_id}: GPU status must remain expected-unsupported")
            require(refusal.get("diffStatsStatus") == "not-computed", f"{row_id}: diff/stat must remain not-computed")
            require(refusal.get("supportScoreIncreased") is False, f"{row_id}: row-specific refusal must not increase support score")

        expected_grouped_fallback = EXPECTED_GROUPED_POLICY_REFUSALS.get(row_id)
        if expected_grouped_fallback is None:
            require(not grouped_policy_refusals, f"{row_id}: unexpected grouped policy refusal link")
        else:
            require(len(grouped_policy_refusals) == 1, f"{row_id}: expected exactly one grouped policy refusal link")
            grouped = grouped_policy_refusals[0]
            require(isinstance(grouped, dict), f"{row_id}: grouped policy refusal link must be object")
            require(source == "d53-visibility", f"{row_id}: grouped policy refusal must stay D53 visibility")
            require(policy_only, f"{row_id}: grouped policy refusal remains policy-only")
            require(status == "expected-unsupported", f"{row_id}: grouped policy refusal must remain expected-unsupported")
            require(not support_claim, f"{row_id}: grouped policy refusal must not claim support")
            require(grouped.get("linear") == "FOR-461", f"{row_id}: grouped policy refusal Linear mismatch")
            require(
                grouped.get("classification") == "grouped-policy-only-expected-unsupported-no-support-claim",
                f"{row_id}: grouped policy refusal classification mismatch",
            )
            require(
                grouped.get("json") == "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
                f"{row_id}: grouped policy refusal JSON path mismatch",
            )
            require(grouped.get("report") == "reports/upstream-rebaseline/2026-05-25-post-1047.md", f"{row_id}: grouped policy refusal report mismatch")
            require(grouped.get("fallbackReason") == expected_grouped_fallback, f"{row_id}: grouped policy refusal fallback mismatch")
            require(
                grouped.get("sourceTask") == "pipelineDashHairlineStrokeDashboardVisibilityPack",
                f"{row_id}: grouped policy refusal source task mismatch",
            )
            require("Policy-only expected-unsupported" in grouped.get("policyArtifactDescription", ""), f"{row_id}: grouped policy refusal policy artifact description mismatch")
            require(grouped.get("supportScoreIncreased") is False, f"{row_id}: grouped policy refusal must not increase support score")

        if str(fallback).startswith("font.") or row_id in {"skia-gm-shadertext3", "skia-gm-textblobtransforms"}:
            require(family == "text-glyph", f"{row_id}: font/text rows must remain text-glyph")

        if "m66" in evidence_links:
            m66_links = evidence_links["m66"]
            require(isinstance(m66_links, list) and m66_links, f"{row_id}: m66 evidence link must be non-empty list")
            for link in m66_links:
                require(link.get("status") in {"pass", "expected-unsupported"}, f"{row_id}: invalid m66 link status")
                if link.get("status") == "expected-unsupported":
                    require(link.get("fallbackReason") != "none", f"{row_id}: m66 expected-unsupported link needs fallback")
        if "m86" in evidence_links:
            m86_links = evidence_links["m86"]
            require(isinstance(m86_links, list) and m86_links, f"{row_id}: m86 evidence link must be non-empty list")
            for link in m86_links:
                require(isinstance(link.get("rootCause"), str) and link.get("rootCause"), f"{row_id}: m86 link needs rootCause")
                require(link.get("risk") in {"high", "medium", "dependency-gated"}, f"{row_id}: invalid m86 risk")

    duplicates = sorted(row_id for row_id, count in Counter(row_ids).items() if count > 1)
    require(not duplicates, f"duplicate row ids: {duplicates}")

    counters = registry.get("counters")
    require(isinstance(counters, dict), "missing counters object")
    for key, expected in EXPECTED_COUNTERS.items():
        require(counters.get(key) == expected, f"counter {key} mismatch: {counters.get(key)} != {expected}")
    require(counters.get("status") == EXPECTED_STATUS_COUNTS, "status counters mismatch")
    require(counters.get("source") == EXPECTED_SOURCE_COUNTS, "source counters mismatch")
    require(counters.get("family") == EXPECTED_FAMILY_COUNTS, "family counters mismatch")
    require(dict(status_counts) == EXPECTED_STATUS_COUNTS, "derived status counters mismatch")
    require(dict(source_counts) == EXPECTED_SOURCE_COUNTS, "derived source counters mismatch")
    require(dict(family_counts) == EXPECTED_FAMILY_COUNTS, "derived family counters mismatch")
    require(support_claims == EXPECTED_COUNTERS["supportClaims"], "derived support claim count mismatch")
    require(policy_only_rows == EXPECTED_COUNTERS["policyOnlyRows"], "derived policy-only count mismatch")
    require(row_specific_refusal_rows == EXPECTED_COUNTERS["rowSpecificRefusalRows"], "derived row-specific refusal count mismatch")
    require(grouped_policy_refusal_rows == EXPECTED_COUNTERS["groupedPolicyRefusalRows"], "derived grouped policy refusal count mismatch")
    require(
        expected_unsupported_with_fallback == EXPECTED_COUNTERS["expectedUnsupportedWithFallback"],
        "derived expected-unsupported fallback count mismatch",
    )
    require(linked_m66_rows == EXPECTED_COUNTERS["linkedM66Rows"], "derived M66 link count mismatch")
    require(linked_m86_rows == EXPECTED_COUNTERS["linkedM86Rows"], "derived M86 link count mismatch")

    evidence_packages = registry.get("evidencePackages")
    require(isinstance(evidence_packages, dict), "missing evidencePackages object")
    require(evidence_packages.get("m66", {}).get("selectedRows") == 19, "M66 selectedRows mismatch")
    require(evidence_packages.get("m66", {}).get("rejectedRows") == 4, "M66 rejectedRows mismatch")
    require(evidence_packages.get("m66", {}).get("linkedRegistryRows") == 18, "M66 linked rows mismatch")
    require(evidence_packages.get("m86", {}).get("rankedCandidates") == 19, "M86 rankedCandidates mismatch")
    require(evidence_packages.get("m86", {}).get("classifiedRows") == 7, "M86 classifiedRows mismatch")
    require(evidence_packages.get("m86", {}).get("skiaComparableSupportRows") == 6, "M86 Skia-comparable support rows mismatch")
    require(evidence_packages.get("m86", {}).get("globalThresholdWeakened") is False, "M86 must preserve globalThresholdWeakened=false")
    m88 = evidence_packages.get("m88", {})
    require(m88.get("status") == "pass", "M88 status mismatch")
    require(m88.get("passRows") == 21, "M88 passRows must stay frozen")
    require(m88.get("expectedUnsupportedRows") == 5, "M88 expectedUnsupportedRows must stay frozen")
    require(m88.get("failRows") == 0, "M88 failRows must stay zero")
    require(m88.get("trackedGapRows") == 0, "M88 trackedGapRows must stay zero")
    require(
        set(m88.get("categories", [])) == {"supported", "expected-unsupported", "dependency-gated", "implementation-gap", "reporting-only"},
        "M88 categories mismatch",
    )

    non_claims = registry.get("nonClaims")
    require(isinstance(non_claims, list) and non_claims, "nonClaims must be non-empty")
    require(
        any("Policy-only visibility rows do not count as support" in str(item) for item in non_claims),
        "registry must preserve policy-only non-claim",
    )
    require(
        any("WGSL remains the WebGPU shader target" in str(item) for item in non_claims),
        "registry must preserve WGSL/SkSL non-claim",
    )


def validate_report() -> None:
    require(REPORT.is_file(), f"missing markdown report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for marker in (
        "Total rows: `47`",
        "Support claims: `22`",
        "Policy-only rows: `20`",
        "Row-specific refusal links: `4`",
        "Grouped policy refusal links: `9`",
        "`expected-unsupported`: `25`",
        "`pass`: `22`",
        "Linked M66 rows: `18`",
        "Linked M86 rows: `18`",
        "does not promote support",
        "WGSL remains the WebGPU shader target",
    ):
        require(marker in text, f"registry.md missing marker: {marker}")


def main() -> int:
    try:
        validate_registry()
        validate_report()
    except AssertionError as error:
        print(f"validate_m89_gm_registry: FAIL: {error}", file=sys.stderr)
        return 1
    print("validate_m89_gm_registry: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
