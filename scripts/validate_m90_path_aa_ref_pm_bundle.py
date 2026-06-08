#!/usr/bin/env python3
"""Validate M90 Path AA REF gate closeout exposure in the generated PM bundle."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
TICKET = "M90-PAA-3-REF-PM-BUNDLE"
BUNDLE_ROOT = ROOT / "build/reports/wgsl-pipeline-pm-bundle"
MANIFEST = BUNDLE_ROOT / "manifest.json"
README = BUNDLE_ROOT / "README.md"
SOURCE_CLOSEOUT = ROOT / "reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json"
SOURCE_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-m90-path-aa-ref-gate-closeout.md"
BUNDLE_DIR = BUNDLE_ROOT / "registry/m90-path-aa-ref-gate-closeout"
BUNDLE_CLOSEOUT = BUNDLE_DIR / "m90-path-aa-ref-gate-closeout.json"
BUNDLE_REPORT = BUNDLE_DIR / "2026-06-08-m90-path-aa-ref-gate-closeout.md"

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
    "reports/wgsl-pipeline/m89-gm-registry/registry.md",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/dashboard-results.json",
    "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json",
}
ALLOWED_STATUS_PATHS = {
    "build.gradle.kts",
    "scripts/m90_path_aa_ref_pm_bundle.py",
    "scripts/validate_m90_path_aa_ref_pm_bundle.py",
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


def expected_sources(closeout: dict[str, Any]) -> list[str]:
    inputs = closeout.get("inputs")
    require(isinstance(inputs, dict), "source closeout inputs must be an object")
    sources = [
        "reports/wgsl-pipeline/2026-06-08-m90-path-aa-ref-gate-closeout.md",
        "reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json",
    ]
    candidate = inputs.get("candidateIntakeCloseout")
    require(isinstance(candidate, str), "candidate intake closeout input missing")
    sources.append(candidate)
    hairlines = inputs.get("hairlines")
    row_gates = inputs.get("rowSpecificGates")
    require(isinstance(hairlines, list), "hairlines inputs must be a list")
    require(isinstance(row_gates, list), "row gate inputs must be a list")
    sources.extend(item for item in hairlines if isinstance(item, str))
    sources.extend(item for item in row_gates if isinstance(item, str))
    return sources


def validate_manifest() -> None:
    manifest = load_json(MANIFEST)
    source = load_json(SOURCE_CLOSEOUT)
    bundle = load_json(BUNDLE_CLOSEOUT)
    readme = README.read_text(encoding="utf-8")
    bundle_report = BUNDLE_REPORT.read_text(encoding="utf-8")

    require(source == bundle, "bundled M90 closeout JSON differs from source")
    require(SOURCE_REPORT.read_text(encoding="utf-8") == bundle_report, "bundled M90 report differs from source")
    require(manifest.get("generatedBy") == "pipelinePmBundle", "manifest generatedBy changed")
    require(manifest.get("generationCommand") == "rtk ./gradlew --no-daemon pipelinePmBundle", "manifest generation command changed")

    entry = manifest.get("m90PathAaRefGateCloseout")
    require(isinstance(entry, dict), "manifest missing m90PathAaRefGateCloseout")
    require(entry.get("ticket") == TICKET, "manifest M90 PM ticket mismatch")
    require(entry.get("classification") == "path-aa-ref-gate-closeout-no-support-promotion", "manifest M90 classification mismatch")
    require(entry.get("status") == "generated evidence", "manifest M90 status mismatch")
    require(entry.get("milestone") == "M90", "manifest M90 milestone mismatch")
    require(entry.get("report") == "registry/m90-path-aa-ref-gate-closeout/2026-06-08-m90-path-aa-ref-gate-closeout.md", "manifest M90 report path mismatch")
    require(entry.get("closeoutJson") == "registry/m90-path-aa-ref-gate-closeout/m90-path-aa-ref-gate-closeout.json", "manifest M90 closeout path mismatch")
    for key, expected in EXPECTED_COUNTERS.items():
        require(entry.get(key) == expected, f"manifest M90 {key} changed")
        require(source.get("counters", {}).get(key) == expected, f"source M90 {key} changed")

    require(entry.get("activeNextRecommendedTicket", {}).get("supportClaimAllowed") is False, "active next ticket allows support")
    require(entry.get("nextHandoff", {}).get("supportClaimAllowed") is False, "next handoff allows support")
    require_false_map(entry.get("nonClaims"), "manifest M90 nonClaims")
    require_false_map(source.get("supportGuard"), "source M90 supportGuard")
    require_false_map(source.get("nonClaims"), "source M90 nonClaims")

    expected = expected_sources(source)
    artifacts = entry.get("artifacts")
    require(isinstance(artifacts, list), "manifest M90 artifacts must be a list")
    require([item.get("source") for item in artifacts if isinstance(item, dict)] == expected, "manifest M90 artifact sources changed")
    for source_path in expected:
        bundled = BUNDLE_DIR / Path(source_path).name
        require(bundled.is_file(), f"missing bundled artifact: {rel(bundled)}")

    for snippet in [
        "M90 Path AA REF gate closeout",
        "coordination evidence only",
        "support claims",
        "dynamic SkSL",
        "tolerance-only production-gap claims disabled",
    ]:
        require(snippet in readme or snippet in bundle_report, f"PM bundle wording missing: {snippet}")


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
    parser.add_argument("--check-worktree-scope", action="store_true")
    args = parser.parse_args()
    validate_manifest()
    if args.check_worktree_scope:
        validate_worktree_scope()
    print(f"validated {TICKET} PM bundle exposure")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"validate_m90_path_aa_ref_pm_bundle: FAIL: {error}", file=sys.stderr)
        sys.exit(1)
