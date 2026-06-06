#!/usr/bin/env python3
"""Validate the FOR-466 skia-gm-image row-specific refusal evidence."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
LINEAR = "FOR-466"
ROW_ID = "skia-gm-image"
FALLBACK_REASON = "image.imagegm.row-specific-artifacts-required"
LOT1_MANIFEST = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-lot1.json"
LOT1_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d50-gm-dashboard-lot1.md"
ROW_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for466-skia-gm-image-evidence.json"
ROW_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-466-skia-gm-image-evidence.md"

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
    "reports/wgsl-pipeline/scenes/generated/m52-gm-feature-promotion-pack.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)
FEATURE_NON_CLAIMS = (
    "codecSupportClaimAddedByFor466",
    "yuvSupportClaimAddedByFor466",
    "animationSupportClaimAddedByFor466",
    "exifSupportClaimAddedByFor466",
    "mipmapSupportClaimAddedByFor466",
    "tileModeSupportClaimAddedByFor466",
    "colorManagedImageSupportClaimAddedByFor466",
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
    require(len(matches) == 1, "skia-gm-image row must appear exactly once")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "skia-gm-image must be expected-unsupported")
    require(row.get("dashboardRowId") is None, "skia-gm-image must not point to a dashboard row")
    require(row.get("dashboardStatus") is None, "skia-gm-image must not claim an active dashboard status")
    require(row.get("strictDashboardStatus") == "expected-unsupported", "skia-gm-image strict dashboard status mismatch")
    require(row.get("referenceKind") is None, "skia-gm-image must not claim a reference kind")
    require(row.get("fallbackReason") == FALLBACK_REASON, "skia-gm-image fallback mismatch")
    require(row.get("supportClaimAddedByFor466") is False, "FOR-466 must not add support")
    require(row.get("skiaComparableClaimAddedByFor466") is False, "FOR-466 must not add Skia-comparable claim")
    require(row.get("historicalImageEvidenceInherited") is False, "historical ImageGM evidence must not be inherited")
    non_claims = manifest.get("nonClaims", {})
    require(non_claims.get("dashboardRowsAddedByFor466") == 0, "FOR-466 must add 0 dashboard rows")
    require(non_claims.get("supportClaimsAddedByFor466") == 0, "FOR-466 must add 0 support claims")
    require(non_claims.get("skiaComparableClaimsAddedByFor466") == 0, "FOR-466 must add 0 Skia-comparable claims")
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
        evidence.get("statusCountsAfterFor466") == {"diagnostic-only": 3, "expected-unsupported": 2, "supported": 7},
        "FOR-466 snapshot status counts mismatch",
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
    provenance = row.get("decodeFixtureProvenance")
    require(isinstance(provenance, dict), "decode/fixture provenance must be recorded")
    require(provenance.get("imageContent") == "static synthetic 64x64 N32 premul raster surface snapshots created by ImageGM before drawImage/drawImageRect", "image content provenance mismatch")
    require(provenance.get("externalEncodedFixture") == "none", "must not claim an external encoded fixture")
    require("not exercised" in provenance.get("decodePath", ""), "decode path must be documented as not exercised")
    require("no row-specific D50 reference/CPU/GPU artifacts" in provenance.get("fixtureAvailability", ""), "fixture availability mismatch")
    require(provenance.get("kotlinSource") == "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageGM.kt", "Kotlin source provenance mismatch")
    require(provenance.get("upstreamSource") == "gm/image.cpp", "upstream source provenance mismatch")
    require(row.get("nonClaims", {}).get("historicalImageEvidenceInherited") is False, "historical image evidence must not be inherited")
    require(not any("For467" in key or "For468" in key for key in row.get("nonClaims", {})), "FOR-466 evidence must not include future-ticket non-claims")
    for key in FEATURE_NON_CLAIMS:
        require(row.get("nonClaims", {}).get(key) is False, f"{key} must remain false")
    require(evidence.get("scoreImpact", {}).get("supportScoreIncreased") is False, "support score must not increase")


def require_reports() -> None:
    lot_report = LOT1_REPORT.read_text(encoding="utf-8")
    row_report = ROW_REPORT.read_text(encoding="utf-8")
    for required in (
        "FOR-466 ajoute 0 ligne dashboard",
        "Le score de support ne monte pas",
        "`skia-gm-image` n'herite pas",
        "codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed",
    ):
        require(required in lot_report, f"lot report missing: {required}")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas herites",
        "Provenance decode / fixture",
        "Aucun fichier image encode externe n'est decode",
        "score de support ne monte pas",
        "0 support ajoute",
        "codec, YUV, animation, EXIF, mipmap, tile-mode ou image color-managed",
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
