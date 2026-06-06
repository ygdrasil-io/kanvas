#!/usr/bin/env python3
"""Validate the FOR-468 skia-gm-offsetimagefilter row-specific refusal evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
LINEAR = "FOR-468"
ROW_ID = "skia-gm-offsetimagefilter"
FALLBACK_REASON = "image-filter.offset.row-specific-artifacts-required"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
LOT1_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md"
ROW_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json"
ROW_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md"

EXPECTED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md",
    "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md",
    "reports/wgsl-pipeline/2026-06-06-for-465-drawminibitmaprect-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-467-skia-gm-imagesource-evidence.md",
    "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json",
    "reports/wgsl-pipeline/scenes/generated/for465-drawminibitmaprect-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for467-skia-gm-imagesource-evidence.json",
    "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json",
    "scripts/validate_for462_d50_lot1_dashboard_integration.py",
    "scripts/validate_for465_drawminibitmaprect_evidence.py",
    "scripts/validate_for466_skia_gm_image_evidence.py",
    "scripts/validate_for467_skia_gm_imagesource_evidence.py",
    "scripts/validate_for468_skia_gm_offsetimagefilter_evidence.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)
FEATURE_NON_CLAIMS = (
    "broadImageFilterDagSupportClaimAddedByFor468",
    "cropImageFilterDagSupportClaimAddedByFor468",
    "picturePrepassSupportClaimAddedByFor468",
    "arbitraryLayerPrepassSupportClaimAddedByFor468",
    "colorPipelineGlobalSupportClaimAddedByFor468",
)


def fail(message: str) -> None:
    raise SystemExit(f"{LINEAR} validation failed: {message}")


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
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_FILES)
    require(not unexpected, f"unexpected local diffs: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"forbidden dashboard/source inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def require_manifest() -> None:
    manifest = load_json(LOT1_MANIFEST)
    require(manifest["statusCounts"] == {"diagnostic-only": 1, "expected-unsupported": 4, "supported": 7}, "manifest status counts mismatch")
    rows = manifest.get("rows")
    require(isinstance(rows, list), "manifest rows must be a list")
    require([row.get("inventoryId") for row in rows if isinstance(row, dict)] == [
        "skia-gm-drawbitmaprect",
        "skia-gm-drawminibitmaprect",
        "skia-gm-bitmappremul",
        "skia-gm-image",
        "skia-gm-imagesource",
        "skia-gm-localmatriximageshader",
        "skia-gm-gradientsdegenerate",
        "skia-gm-offsetimagefilter",
        "skia-gm-matriximagefilter",
        "skia-gm-imageblur",
        "skia-gm-simpleaaclip",
        "skia-gm-pathfill",
    ], "manifest order changed")
    matches = [row for row in rows if isinstance(row, dict) and row.get("inventoryId") == ROW_ID]
    require(len(matches) == 1, "skia-gm-offsetimagefilter row must appear exactly once")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "skia-gm-offsetimagefilter must be expected-unsupported")
    require(row.get("dashboardRowId") is None, "skia-gm-offsetimagefilter must not point to a dashboard row")
    require(row.get("dashboardStatus") is None, "skia-gm-offsetimagefilter must not claim an active dashboard status")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "strict dashboard status mismatch")
    require(row.get("referenceKind") is None, "skia-gm-offsetimagefilter must not claim a reference kind")
    require(row.get("fallbackReason") == FALLBACK_REASON, "skia-gm-offsetimagefilter fallback mismatch")
    require(row.get("cpuRoute") == "cpu.image-filter.offset.expected-unsupported", "CPU route mismatch")
    require(row.get("gpuRoute") == "webgpu.image-filter.offset.expected-unsupported", "GPU route mismatch")
    require(row.get("supportClaimAddedByFor468") is False, "FOR-468 must not add support")
    require(row.get("skiaComparableClaimAddedByFor468") is False, "FOR-468 must not add Skia-comparable claim")
    require(row.get("offsetImageFilterEvidenceInherited") is False, "offset image-filter evidence must not be inherited")
    provenance = row.get("offsetImageFilterProvenance")
    require(isinstance(provenance, dict), "offset image-filter provenance must be recorded")
    require(provenance.get("scene") == "OffsetImageFilterGM", "scene provenance mismatch")
    require("84.515" in provenance.get("historicalSimilarity", ""), "historical similarity provenance mismatch")
    require(provenance.get("boundedOffsetFilter") == "not claimed as supported", "bounded offset support must not be claimed")
    non_claims = manifest.get("nonClaims", {})
    require(non_claims.get("dashboardRowsAddedByFor468") == 0, "FOR-468 must add 0 dashboard rows")
    require(non_claims.get("supportClaimsAddedByFor468") == 0, "FOR-468 must add 0 support claims")
    require(non_claims.get("skiaComparableClaimsAddedByFor468") == 0, "FOR-468 must add 0 Skia-comparable claims")
    require(non_claims.get("thresholdChanged") is False, "threshold must not change")
    require(non_claims.get("scoringChanged") is False, "scoring must not change")
    require(non_claims.get("fallbackPolicyChanged") is False, "fallback policy must not change")
    require(non_claims.get("pipelineKeyChanged") is False, "PipelineKey must not change")
    require(non_claims.get("productionCodeChanged") is False, "production code must not change")
    require(non_claims.get("wgslProductionChanged") is False, "WGSL production must not change")
    for key in FEATURE_NON_CLAIMS:
        require(non_claims.get(key) is False, f"{key} must remain false")


def require_row_evidence() -> None:
    evidence = load_json(ROW_EVIDENCE)
    require(evidence.get("linear") == LINEAR, "row evidence linear mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-no-support-claim", "row evidence classification mismatch")
    require(
        evidence.get("statusCountsAfterFor468") == {"diagnostic-only": 1, "expected-unsupported": 4, "supported": 7},
        "FOR-468 snapshot status counts mismatch",
    )
    row = evidence.get("row")
    require(isinstance(row, dict), "row evidence must contain row object")
    require(row.get("inventoryId") == ROW_ID, "row evidence inventory mismatch")
    require(row.get("status") == "expected-unsupported", "row evidence status mismatch")
    require(row.get("fallbackReason") == FALLBACK_REASON, "row evidence fallback mismatch")
    require(row.get("reference", {}).get("status") == "not-generated", "reference must remain not-generated")
    require(row.get("cpu", {}).get("status") == "expected-unsupported", "CPU must remain expected-unsupported")
    require(row.get("gpu", {}).get("status") == "expected-unsupported", "GPU must remain expected-unsupported")
    require(row.get("diffStats", {}).get("status") == "not-computed", "diff/stat must remain not-computed")
    provenance = row.get("offsetImageFilterProvenance")
    require(isinstance(provenance, dict), "offset image-filter provenance must be recorded")
    require(provenance.get("kotlinSource") == "skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt", "Kotlin source provenance mismatch")
    require(provenance.get("upstreamSource") == "gm/offsetimagefilter.cpp", "upstream source provenance mismatch")
    require("no row-specific D50 reference/CPU/GPU artifacts" in provenance.get("fixtureAvailability", ""), "fixture availability mismatch")
    require(row.get("nonClaims", {}).get("offsetImageFilterEvidenceInherited") is False, "offset image-filter evidence must not be inherited")
    for key in FEATURE_NON_CLAIMS:
        require(row.get("nonClaims", {}).get(key) is False, f"{key} must remain false")
    require(evidence.get("scoreImpact", {}).get("supportScoreIncreased") is False, "support score must not increase")


def require_reports() -> None:
    lot_report = LOT1_REPORT.read_text(encoding="utf-8")
    row_report = ROW_REPORT.read_text(encoding="utf-8")
    for required in (
        "FOR-468 ajoute 0 ligne dashboard",
        "Le score de support ne monte pas",
        "`skia-gm-offsetimagefilter` n'herite pas",
        "support large image-filter DAG",
    ):
        require(required in lot_report, f"lot report missing: {required}")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas herites",
        "Provenance offset image-filter",
        "OffsetImageFilterGM=84.515",
        "score de support ne monte pas",
        "0 support ajoute",
        "support large image-filter DAG, crop image-filter DAG, picture-prepass, prepass arbitraire ou pipeline couleur global",
    ):
        require(required in row_report, f"row report missing: {required}")


def main() -> None:
    require_scope()
    require_manifest()
    require_row_evidence()
    require_reports()
    print(f"{LINEAR} validation passed: {ROW_ID}=expected-unsupported supportScoreIncreased=false")


if __name__ == "__main__":
    main()
