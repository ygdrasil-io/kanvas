#!/usr/bin/env python3
"""Expose M89 GM registry evidence in the generated PM bundle."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

EXPECTED_PM_COUNTERS = {
    "totalRows": 47,
    "supportClaims": 22,
    "policyOnlyRows": 20,
    "expectedUnsupportedWithFallback": 25,
    "unlinkedUnsupportedRows": 0,
    "linkedM66Rows": 18,
    "linkedM86Rows": 18,
    "linkedM90Rows": 9,
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
EXPECTED_SOURCE_COUNTS = {
    "d50-visibility": 11,
    "d53-visibility": 9,
    "generated-dashboard": 27,
}


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise AssertionError(f"missing JSON file: {path}")
    root = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(root, dict):
        raise AssertionError(f"{path} root must be an object")
    return root


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def update_readme(readme: Path) -> None:
    marker = "- `registry/m89-gm-registry/`: M89 normalized GM support/refusal registry JSON and Markdown report."
    note = "- M89 registry counters, PM counters, and closeout live in `manifest.json` under `m89GmRegistry`; PM counters are reporting-only evidence with no registry row mutation or support promotion. Policy-only visibility rows do not count as support, dependency gates, edge-budget gates, image-filter prepass gates, text/glyph dependency gates, and refusal links remain unsupported, `unlinkedUnsupportedRows` must stay zero, threshold-only misses remain fidelity burn-down scope, and M90/M91/M92 are next-slice recommendations only."
    text = readme.read_text(encoding="utf-8") if readme.is_file() else "# WGSL Pipeline PM Bundle\n"
    insertion = f"{marker}\n{note}\n"
    if marker in text:
        lines = text.splitlines()
        rewritten: list[str] = []
        replaced = False
        skip_stale_note = False
        for line in lines:
            if line == marker:
                rewritten.extend([marker, note])
                replaced = True
                skip_stale_note = True
                continue
            if skip_stale_note:
                skip_stale_note = False
                if (
                    line.startswith("- M89 registry counters live in `manifest.json` under `m89GmRegistry`;")
                    or line.startswith("- M89 registry counters and closeout live in `manifest.json` under `m89GmRegistry`;")
                    or line.startswith("- M89 registry counters, PM counters, and closeout live in `manifest.json` under `m89GmRegistry`;")
                ):
                    continue
            rewritten.append(line)
        if replaced:
            readme.write_text("\n".join(rewritten) + "\n", encoding="utf-8")
            return
    anchor = "- `runtime/m87-runtime-effect-live-editing/`:"
    if anchor in text:
        text = text.replace(anchor, insertion + anchor, 1)
    else:
        if not text.endswith("\n"):
            text += "\n"
        text += insertion
    readme.write_text(text, encoding="utf-8")


def require_false_map(root: dict[str, Any], path: str) -> None:
    require(isinstance(root, dict), f"{path} must be an object")
    for field, value in root.items():
        require(value is False, f"{path}.{field} must stay false")


def validate_pm_counters(pm_counters: dict[str, Any], registry: dict[str, Any]) -> dict[str, Any]:
    require(pm_counters.get("ticket") == "M89-PM-COUNTERS", "PM counters ticket mismatch")
    require(
        pm_counters.get("classification") == "gm-registry-pm-counters-reporting-only-no-support-promotion",
        "PM counters classification mismatch",
    )
    require(pm_counters.get("status") == "generated evidence", "PM counters status mismatch")
    require(
        pm_counters.get("inputs", {}).get("registryJson") == "reports/wgsl-pipeline/m89-gm-registry/registry.json",
        "PM counters registry input mismatch",
    )

    registry_counters = registry.get("counters")
    counters = pm_counters.get("counters")
    require(isinstance(registry_counters, dict), "registry counters must be an object")
    require(isinstance(counters, dict), "PM counters must be an object")
    for key, expected in EXPECTED_PM_COUNTERS.items():
        require(registry_counters.get(key) == expected, f"registry counter changed: {key}")
        require(counters.get(key) == expected, f"PM counter changed: {key}")

    for key, expected in [
        ("status", EXPECTED_STATUS_COUNTS),
        ("family", EXPECTED_FAMILY_COUNTS),
        ("source", EXPECTED_SOURCE_COUNTS),
    ]:
        require(registry_counters.get(key) == expected, f"registry {key} map changed")
        require(counters.get(key) == expected, f"PM counters {key} map changed")

    require_false_map(pm_counters.get("nonClaims"), "PM counters nonClaims")
    summary = pm_counters.get("supportRefusalSummary")
    require(isinstance(summary, dict), "PM counters supportRefusalSummary must be an object")
    require(summary.get("reportingOnly", {}).get("statusRows") == 0, "PM counters reportingOnly.statusRows must stay zero")
    require(summary.get("policyOnlyVisibility", {}).get("rows") == 20, "PM counters policyOnlyVisibility.rows must stay 20")
    require(summary.get("dependencyGated", {}).get("statusRows") == 0, "PM counters dependencyGated.statusRows must stay zero")
    require(summary.get("dependencyGated", {}).get("linkedRows") == 6, "PM counters dependencyGated.linkedRows must stay 6")
    require(
        summary.get("belowThresholdExcluded", {}).get("countedAsProductionGap") is False,
        "PM counters belowThresholdExcluded.countedAsProductionGap must stay false",
    )
    return counters


def build_manifest_entry(registry: dict[str, Any], pm_counters: dict[str, Any]) -> dict[str, Any]:
    counters = registry.get("counters")
    evidence = registry.get("evidencePackages")
    require(isinstance(counters, dict), "registry counters must be an object")
    require(isinstance(evidence, dict), "registry evidencePackages must be an object")
    m86 = evidence.get("m86")
    m88 = evidence.get("m88")
    require(isinstance(m86, dict), "registry M86 evidence package must be an object")
    require(isinstance(m88, dict), "registry M88 evidence package must be an object")

    require(counters.get("supportClaims") == 22, "M89 supportClaims must stay 22")
    require(counters.get("policyOnlyRows") == 20, "M89 policyOnlyRows must stay 20")
    require(counters.get("rowSpecificRefusalRows") == 7, "M89 rowSpecificRefusalRows must stay 7")
    require(counters.get("dependencyGateLinkRows") == 4, "M89 dependencyGateLinkRows must stay 4")
    require(counters.get("groupedPolicyRefusalRows") == 9, "M89 groupedPolicyRefusalRows must stay 9")
    require(counters.get("edgeBudgetGateLinkRows") == 2, "M89 edgeBudgetGateLinkRows must stay 2")
    require(counters.get("imageFilterPrepassGateLinkRows") == 1, "M89 imageFilterPrepassGateLinkRows must stay 1")
    require(counters.get("textGlyphDependencyGateLinkRows") == 2, "M89 textGlyphDependencyGateLinkRows must stay 2")
    require(counters.get("unlinkedUnsupportedRows") == 0, "M89 unlinkedUnsupportedRows must stay 0")
    require(counters.get("expectedUnsupportedWithFallback") == 25, "M89 expectedUnsupportedWithFallback must stay 25")
    require(counters.get("linkedM66Rows") == 18, "M89 linkedM66Rows must stay 18")
    require(counters.get("linkedM86Rows") == 18, "M89 linkedM86Rows must stay 18")
    require(counters.get("linkedM90Rows") == 9, "M89 linkedM90Rows must stay 9")
    require(m86.get("globalThresholdWeakened") is False, "M89 must preserve M86 globalThresholdWeakened=false")
    require(m88.get("failRows") == 0, "M89 must preserve M88 failRows=0")
    require(m88.get("trackedGapRows") == 0, "M89 must preserve M88 trackedGapRows=0")
    pm_summary_counters = validate_pm_counters(pm_counters, registry)

    return {
        "totalRows": counters.get("totalRows", 0),
        "supportClaims": counters.get("supportClaims", 0),
        "policyOnlyRows": counters.get("policyOnlyRows", 0),
        "rowSpecificRefusalRows": counters.get("rowSpecificRefusalRows", 0),
        "dependencyGateLinkRows": counters.get("dependencyGateLinkRows", 0),
        "groupedPolicyRefusalRows": counters.get("groupedPolicyRefusalRows", 0),
        "edgeBudgetGateLinkRows": counters.get("edgeBudgetGateLinkRows", 0),
        "imageFilterPrepassGateLinkRows": counters.get("imageFilterPrepassGateLinkRows", 0),
        "textGlyphDependencyGateLinkRows": counters.get("textGlyphDependencyGateLinkRows", 0),
        "unlinkedUnsupportedRows": counters.get("unlinkedUnsupportedRows", 0),
        "expectedUnsupportedWithFallback": counters.get("expectedUnsupportedWithFallback", 0),
        "linkedM66Rows": counters.get("linkedM66Rows", 0),
        "linkedM86Rows": counters.get("linkedM86Rows", 0),
        "linkedM90Rows": counters.get("linkedM90Rows", 0),
        "statusCounts": counters.get("status", {}),
        "familyCounts": counters.get("family", {}),
        "sourceCounts": counters.get("source", {}),
        "registryJson": "registry/m89-gm-registry/registry.json",
        "registryReport": "registry/m89-gm-registry/registry.md",
        "closeoutJson": "registry/m89-gm-registry/closeout.json",
        "closeoutReport": "registry/m89-gm-registry/closeout.md",
        "pmCountersJson": "registry/m89-gm-registry/pm-counters.json",
        "pmCountersReport": "registry/m89-gm-registry/pm-counters.md",
        "pmCounters": {
            "classification": pm_counters.get("classification"),
            "status": pm_counters.get("status"),
            "totalRows": pm_summary_counters.get("totalRows"),
            "supportClaims": pm_summary_counters.get("supportClaims"),
            "policyOnlyRows": pm_summary_counters.get("policyOnlyRows"),
            "expectedUnsupportedWithFallback": pm_summary_counters.get("expectedUnsupportedWithFallback"),
            "unlinkedUnsupportedRows": pm_summary_counters.get("unlinkedUnsupportedRows"),
            "linkedM66Rows": pm_summary_counters.get("linkedM66Rows"),
            "linkedM86Rows": pm_summary_counters.get("linkedM86Rows"),
            "linkedM90Rows": pm_summary_counters.get("linkedM90Rows"),
            "nonClaims": pm_counters.get("nonClaims", {}),
        },
        "notice": "M89 normalizes generated dashboard and policy-only GM visibility rows into support/refusal registry evidence. Dependency gates, edge-budget gate links, image-filter prepass gate links, text/glyph dependency gate links, and row-specific/grouped refusal links remain unsupported evidence; unlinked unsupported rows must stay zero, and the registry does not promote policy-only rows, weaken thresholds, or change render paths.",
    }


def validate_closeout(closeout: dict[str, Any]) -> None:
    require(closeout.get("classification") == "pm-closeout-no-new-rendering-support", "M89 closeout classification mismatch")
    readiness = closeout.get("readiness")
    support_guard = closeout.get("supportGuard")
    unsupported_visibility = closeout.get("unsupportedVisibility")
    non_claims = closeout.get("nonClaims")
    require(isinstance(readiness, dict), "M89 closeout readiness must be an object")
    require(readiness.get("readinessBefore") == readiness.get("readinessAfter") == 67.75, "M89 closeout readiness must stay 67.75")
    require(readiness.get("readinessDelta") == 0.0, "M89 closeout readinessDelta must stay 0.0")
    require(isinstance(support_guard, dict), "M89 closeout supportGuard must be an object")
    for field in [
        "supportClaimsChanged",
        "renderPathsChanged",
        "thresholdsChanged",
        "globalThresholdWeakened",
        "policyOnlyRowsPromoted",
        "belowThresholdCountedAsProductionGap",
    ]:
        require(support_guard.get(field) is False, f"M89 closeout supportGuard.{field} must stay false")
    require(support_guard.get("unexpectedFailRows") == 0, "M89 closeout unexpectedFailRows must stay zero")
    require(support_guard.get("trackedGapRows") == 0, "M89 closeout trackedGapRows must stay zero")
    require(isinstance(unsupported_visibility, dict), "M89 closeout unsupportedVisibility must be an object")
    require(unsupported_visibility.get("expectedUnsupportedRows") == 25, "M89 closeout expectedUnsupportedRows must stay 25")
    require(unsupported_visibility.get("linkedUnsupportedRows") == 25, "M89 closeout linkedUnsupportedRows must stay 25")
    require(unsupported_visibility.get("unlinkedUnsupportedRows") == 0, "M89 closeout unlinkedUnsupportedRows must stay zero")
    require(unsupported_visibility.get("linkedM90Rows") == 9, "M89 closeout linkedM90Rows must stay 9")
    require(isinstance(non_claims, dict), "M89 closeout nonClaims must be an object")
    for field, value in non_claims.items():
        require(value is False, f"M89 closeout nonClaims.{field} must stay false")
    next_slices = closeout.get("nextRecommendedSlices")
    require(isinstance(next_slices, list), "M89 closeout nextRecommendedSlices must be a list")
    require([item.get("milestone") for item in next_slices if isinstance(item, dict)] == ["M90", "M91", "M92"], "M89 closeout next slice order mismatch")
    for item in next_slices:
        require(isinstance(item, dict), "M89 closeout next slice entries must be objects")
        require(item.get("supportClaimAllowedFromCloseout") is False, f"{item.get('milestone')}: M89 closeout cannot authorize support claims")


def expose_registry(project_root: Path, bundle_root: Path) -> None:
    source = project_root / "reports/wgsl-pipeline/m89-gm-registry"
    registry_json = source / "registry.json"
    registry_md = source / "registry.md"
    closeout_json = source / "closeout.json"
    closeout_md = source / "closeout.md"
    pm_counters_json = source / "pm-counters.json"
    pm_counters_md = source / "pm-counters.md"
    manifest_path = bundle_root / "manifest.json"
    readme_path = bundle_root / "README.md"
    require(source.is_dir(), f"missing M89 registry source dir: {source}")
    require(registry_json.is_file(), f"missing M89 registry JSON: {registry_json}")
    require(registry_md.is_file(), f"missing M89 registry Markdown: {registry_md}")
    require(closeout_json.is_file(), f"missing M89 registry closeout JSON: {closeout_json}")
    require(closeout_md.is_file(), f"missing M89 registry closeout Markdown: {closeout_md}")
    require(pm_counters_json.is_file(), f"missing M89 PM counters JSON: {pm_counters_json}")
    require(pm_counters_md.is_file(), f"missing M89 PM counters Markdown: {pm_counters_md}")
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")

    registry = load_json(registry_json)
    closeout = load_json(closeout_json)
    pm_counters = load_json(pm_counters_json)
    validate_closeout(closeout)
    manifest = load_json(manifest_path)
    manifest_entry = build_manifest_entry(registry, pm_counters)
    manifest_entry["closeout"] = {
        "classification": closeout.get("classification"),
        "readinessBefore": closeout.get("readiness", {}).get("readinessBefore"),
        "readinessAfter": closeout.get("readiness", {}).get("readinessAfter"),
        "readinessDelta": closeout.get("readiness", {}).get("readinessDelta"),
        "linkedM90Rows": closeout.get("unsupportedVisibility", {}).get("linkedM90Rows"),
        "nextRecommendedSlices": closeout.get("nextRecommendedSlices", []),
    }
    manifest["m89GmRegistry"] = manifest_entry

    target = bundle_root / "registry/m89-gm-registry"
    if target.exists():
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, target)

    write_json(manifest_path, manifest)
    update_readme(readme_path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=".")
    parser.add_argument("--bundle-root", default="build/reports/wgsl-pipeline-pm-bundle")
    args = parser.parse_args()

    project_root = Path(args.project_root).resolve()
    bundle_root = (project_root / args.bundle_root).resolve()
    try:
        expose_registry(project_root, bundle_root)
    except AssertionError as error:
        print(f"m89_pm_bundle_registry: FAIL: {error}", file=sys.stderr)
        return 1
    print(f"exposed M89 GM registry in {bundle_root.relative_to(project_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
