#!/usr/bin/env python3
"""Expose M90 Path AA REF gate closeout evidence in the generated PM bundle."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

TICKET = "M90-PAA-3-REF-PM-BUNDLE"
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
SOURCE_REPORT = "reports/wgsl-pipeline/2026-06-08-m90-path-aa-ref-gate-closeout.md"
SOURCE_CLOSEOUT = "reports/wgsl-pipeline/scenes/generated/m90-path-aa-ref-gate-closeout.json"
BUNDLE_DIR = "registry/m90-path-aa-ref-gate-closeout"
BUNDLE_REPORT = f"{BUNDLE_DIR}/2026-06-08-m90-path-aa-ref-gate-closeout.md"
BUNDLE_CLOSEOUT = f"{BUNDLE_DIR}/m90-path-aa-ref-gate-closeout.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{path} root must be an object")
    return data


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def require_false_map(data: dict[str, Any], label: str) -> None:
    require(isinstance(data, dict), f"{label} must be an object")
    for key, value in data.items():
        require(value is False, f"{label}.{key} must stay false")


def validate_closeout(closeout: dict[str, Any]) -> None:
    require(closeout.get("ticket") == "M90-PAA-3-REF-CLOSEOUT", "closeout ticket mismatch")
    require(closeout.get("classification") == "path-aa-ref-gate-closeout-no-support-promotion", "classification mismatch")
    require(closeout.get("status") == "generated evidence", "closeout status mismatch")
    counters = closeout.get("counters")
    require(counters == EXPECTED_COUNTERS, "closeout counters changed")
    require(closeout.get("activeNextRecommendedTicket", {}).get("id") == "M90-PAA-3A", "active next ticket changed")
    require(closeout.get("nextHandoff", {}).get("id") == "M90-PAA-3A-REF", "next handoff changed")
    require(closeout.get("activeNextRecommendedTicket", {}).get("supportClaimAllowed") is False, "active next ticket allows support")
    require(closeout.get("nextHandoff", {}).get("supportClaimAllowed") is False, "next handoff allows support")
    require_false_map(closeout.get("supportGuard"), "supportGuard")
    require_false_map(closeout.get("nonClaims"), "nonClaims")

    rows = closeout.get("rows")
    require(isinstance(rows, list) and len(rows) == 9, "closeout rows must contain 9 entries")
    for row in rows:
        require(isinstance(row, dict), "closeout rows must be objects")
        require(row.get("supportClaim") is False, f"{row.get('rowId')}: row support claim changed")
        require(row.get("promotionAllowed") is False, f"{row.get('rowId')}: promotion became allowed")


def source_paths(closeout: dict[str, Any]) -> list[str]:
    paths = [SOURCE_REPORT, SOURCE_CLOSEOUT]
    inputs = closeout.get("inputs")
    require(isinstance(inputs, dict), "closeout inputs must be an object")
    candidate = inputs.get("candidateIntakeCloseout")
    require(isinstance(candidate, str), "candidate intake closeout input missing")
    paths.append(candidate)
    hairlines = inputs.get("hairlines")
    row_gates = inputs.get("rowSpecificGates")
    require(isinstance(hairlines, list), "hairlines inputs must be a list")
    require(isinstance(row_gates, list), "row gate inputs must be a list")
    paths.extend(item for item in hairlines if isinstance(item, str))
    paths.extend(item for item in row_gates if isinstance(item, str))
    return paths


def bundle_path_for(source: str) -> str:
    return f"{BUNDLE_DIR}/{Path(source).name}"


def update_readme(readme: Path) -> None:
    marker = "- `registry/m90-path-aa-ref-gate-closeout/`: M90 Path AA REF gate closeout and row-gate coordination evidence."
    note = "- M90 Path AA REF gate closeout lives in `manifest.json` under `m90PathAaRefGateCloseout`; it is coordination evidence only and keeps support claims, readiness, thresholds, edge budget, dashboard promotion, Ganesh/Graphite, dynamic SkSL, and tolerance-only production-gap claims disabled."
    text = readme.read_text(encoding="utf-8") if readme.is_file() else "# WGSL Pipeline PM Bundle\n"
    if marker in text:
        lines = text.splitlines()
        rewritten: list[str] = []
        skip_note = False
        for line in lines:
            if line == marker:
                rewritten.extend([marker, note])
                skip_note = True
                continue
            if skip_note:
                skip_note = False
                if line.startswith("- M90 Path AA REF gate closeout lives in `manifest.json` under `m90PathAaRefGateCloseout`;"):
                    continue
            rewritten.append(line)
        readme.write_text("\n".join(rewritten) + "\n", encoding="utf-8")
        return
    anchor = "- `registry/m89-gm-registry/`:"
    insertion = f"{marker}\n{note}\n"
    if anchor in text:
        text = text.replace(anchor, insertion + anchor, 1)
    else:
        if not text.endswith("\n"):
            text += "\n"
        text += insertion
    readme.write_text(text, encoding="utf-8")


def expose(project_root: Path, bundle_root: Path) -> None:
    manifest_path = bundle_root / "manifest.json"
    readme_path = bundle_root / "README.md"
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")

    closeout = load_json(project_root / SOURCE_CLOSEOUT)
    validate_closeout(closeout)
    report_path = project_root / SOURCE_REPORT
    require(report_path.is_file(), f"missing closeout report: {report_path}")

    target = bundle_root / BUNDLE_DIR
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)

    copied: list[dict[str, str]] = []
    for source in source_paths(closeout):
        source_path = project_root / source
        require(source_path.is_file(), f"missing source artifact: {source}")
        target_path = bundle_root / bundle_path_for(source)
        shutil.copy2(source_path, target_path)
        copied.append({"source": source, "bundlePath": bundle_path_for(source)})

    manifest = load_json(manifest_path)
    manifest["m90PathAaRefGateCloseout"] = {
        "ticket": TICKET,
        "classification": closeout.get("classification"),
        "status": closeout.get("status"),
        "milestone": "M90",
        "report": BUNDLE_REPORT,
        "closeoutJson": BUNDLE_CLOSEOUT,
        "candidateRows": closeout.get("counters", {}).get("candidateRows"),
        "rowsWithRefGateOrHarness": closeout.get("counters", {}).get("rowsWithRefGateOrHarness"),
        "rowSpecificGateArtifacts": closeout.get("counters", {}).get("rowSpecificGateArtifacts"),
        "hairlinesHarnessArtifacts": closeout.get("counters", {}).get("hairlinesHarnessArtifacts"),
        "supportClaims": closeout.get("counters", {}).get("supportClaims"),
        "newSupportClaims": closeout.get("counters", {}).get("newSupportClaims"),
        "readinessDelta": closeout.get("counters", {}).get("readinessDelta"),
        "dashboardPromotions": closeout.get("counters", {}).get("dashboardPromotions"),
        "thresholdChanges": closeout.get("counters", {}).get("thresholdChanges"),
        "edgeBudgetChanges": closeout.get("counters", {}).get("edgeBudgetChanges"),
        "activeNextRecommendedTicket": closeout.get("activeNextRecommendedTicket"),
        "nextHandoff": closeout.get("nextHandoff"),
        "nonClaims": closeout.get("nonClaims"),
        "artifacts": copied,
        "notice": "M90 Path AA REF gate closeout is PM-visible coordination evidence only. It does not add rendering support, mutate the M89 registry, promote dashboard/readiness, change thresholds, change the 256-edge budget, or treat below-threshold tolerance-only rows as production-missing features.",
    }
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
        expose(project_root, bundle_root)
    except AssertionError as error:
        print(f"m90_path_aa_ref_pm_bundle: FAIL: {error}", file=sys.stderr)
        return 1
    print(f"exposed M90 Path AA REF closeout in {bundle_root.relative_to(project_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
