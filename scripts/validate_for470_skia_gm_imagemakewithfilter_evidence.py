#!/usr/bin/env python3
"""Validate the FOR-470 skia-gm-imagemakewithfilter row-specific refusal evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
LINEAR = "FOR-470"
ROW_ID = "skia-gm-imagemakewithfilter"
FALLBACK_REASON = "image-filter.imagemakewithfilter.row-specific-artifacts-required"
VISIBILITY = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json"
ROW_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json"
ROW_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-for-470-skia-gm-imagemakewithfilter-evidence.md"

FEATURE_NON_CLAIMS = (
    "arbitraryImageFilterDagSupportClaimAddedByFor470",
    "broadImageFilterSupportClaimAddedByFor470",
    "cropPrepassSupportClaimAddedByFor470",
    "picturePrepassSupportClaimAddedByFor470",
    "layerPrepassSupportClaimAddedByFor470",
    "imagemakewithfilterEvidenceInherited",
)
GLOBAL_NON_CLAIMS = (
    "dashboardRowAddedByFor470",
    "dashboardStatusChangedByFor470",
    "fallbackPolicyChanged",
    "pipelineKeyChanged",
    "productionCodeChanged",
    "scoringChanged",
    "skiaComparableClaimAddedByFor470",
    "supportClaimAddedByFor470",
    "thresholdChanged",
    "upstreamSourceChanged",
    "wgslProductionChanged",
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


def require_visibility_row() -> None:
    visibility = load_json(VISIBILITY)
    scenes = visibility.get("scenes")
    require(isinstance(scenes, list), "D50 visibility must contain scenes[]")
    matches = [scene for scene in scenes if isinstance(scene, dict) and scene.get("id") == ROW_ID]
    require(len(matches) == 1, "D50 visibility row must appear exactly once")
    row = matches[0]
    require(row.get("status") == "expected-unsupported", "D50 visibility row must remain expected-unsupported")
    require(row.get("policyOnlyArtifacts") is True, "D50 visibility row must remain policy-only")
    require(row.get("fallbackReason") == FALLBACK_REASON, "D50 visibility fallback mismatch")
    require(row.get("cpuRoute") == "cpu.image-filter.imagemakewithfilter.expected-unsupported", "D50 CPU route mismatch")
    require(row.get("gpuRoute") == "webgpu.image-filter.imagemakewithfilter.expected-unsupported", "D50 GPU route mismatch")
    require("does not claim ImageMakeWithFilterGM" in row.get("nonClaim", ""), "D50 non-claim must name ImageMakeWithFilterGM")
    require("arbitrary image-filter DAG" in row.get("nonClaim", ""), "D50 non-claim must preserve arbitrary DAG refusal")


def require_row_evidence() -> None:
    evidence = load_json(ROW_EVIDENCE)
    require(evidence.get("schemaVersion") == 1, "schemaVersion mismatch")
    require(evidence.get("linear") == LINEAR, "row evidence Linear mismatch")
    require(evidence.get("classification") == "row-specific-expected-unsupported-no-support-claim", "classification mismatch")
    require(evidence.get("scoreImpact", {}).get("supportScoreIncreased") is False, "support score must not increase")

    for key in FEATURE_NON_CLAIMS + GLOBAL_NON_CLAIMS:
        require(evidence.get("nonClaims", {}).get(key) is False, f"{key} must remain false")

    row = evidence.get("row")
    require(isinstance(row, dict), "row evidence must contain row object")
    require(row.get("inventoryId") == ROW_ID, "row evidence inventory mismatch")
    require(row.get("linear") == LINEAR, "row evidence row Linear mismatch")
    require(row.get("status") == "expected-unsupported", "row evidence status mismatch")
    require(row.get("fallbackReason") == FALLBACK_REASON, "row evidence fallback mismatch")
    require(row.get("reference", {}).get("status") == "not-generated", "reference must remain not-generated")
    require(row.get("cpu", {}).get("status") == "expected-unsupported", "CPU must remain expected-unsupported")
    require(row.get("cpu", {}).get("route") == "cpu.image-filter.imagemakewithfilter.expected-unsupported", "CPU route mismatch")
    require(row.get("gpu", {}).get("status") == "expected-unsupported", "GPU must remain expected-unsupported")
    require(row.get("gpu", {}).get("route") == "webgpu.image-filter.imagemakewithfilter.expected-unsupported", "GPU route mismatch")
    require(row.get("diffStats", {}).get("status") == "not-computed", "diff/stat must remain not-computed")

    provenance = row.get("imageMakeWithFilterProvenance")
    require(isinstance(provenance, dict), "ImageMakeWithFilter provenance must be recorded")
    require(provenance.get("scene") == "ImageMakeWithFilterGM", "scene provenance mismatch")
    require(provenance.get("kotlinSource") == "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt", "Kotlin source provenance mismatch")
    require(provenance.get("upstreamSource") == "gm/imagemakewithfilter.cpp", "upstream source provenance mismatch")
    require("84.35382962588474" in provenance.get("historicalSimilarity", ""), "historical similarity provenance mismatch")
    require("not treated as a production missing feature when the only signal is tolerance" in provenance.get("historicalSimilarity", ""), "tolerance-only policy must be explicit")
    unsupported = set(provenance.get("unsupportedBroadClaims", []))
    require(
        {
            "arbitrary image-filter DAG",
            "broad image-filter support",
            "crop prepass",
            "picture prepass",
            "layer prepass",
            "bounded M61/M89 evidence inheritance",
        }
        <= unsupported,
        "unsupported broad claims mismatch",
    )
    route_diagnostics = row.get("routeDiagnostics")
    require(isinstance(route_diagnostics, dict), "route diagnostics must be recorded")
    require(route_diagnostics.get("cpu") == "cpu.image-filter.imagemakewithfilter.expected-unsupported", "route diagnostics CPU mismatch")
    require(route_diagnostics.get("gpu") == "webgpu.image-filter.imagemakewithfilter.expected-unsupported", "route diagnostics GPU mismatch")
    require(route_diagnostics.get("fallbackReason") == FALLBACK_REASON, "route diagnostics fallback mismatch")
    route_policy = route_diagnostics.get("policy", "")
    require("fallbackReason=none" in route_policy, "route policy must describe future support proof")
    require("without threshold or scoring changes" in route_policy, "route policy must preserve threshold/scoring")
    for key in FEATURE_NON_CLAIMS + GLOBAL_NON_CLAIMS:
        require(row.get("nonClaims", {}).get(key) is False, f"row {key} must remain false")


def require_report() -> None:
    require(ROW_REPORT.is_file(), f"missing row report: {rel(ROW_REPORT)}")
    text = ROW_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "ne sont pas heritees",
        "ImageMakeWithFilterGM=84.35382962588474",
        "pas une preuve dashboard D50 ligne par ligne",
        "pas classee comme feature manquante de production sur la seule base d'une tolerance non atteinte",
        "Le score de support ne monte pas",
        "0 support ajoute",
        "Aucun support image-filter large, DAG arbitraire, crop prepass, picture prepass, layer prepass ou heritage de preuve M61/M89",
        "Aucun changement de seuil",
    ):
        require(required in text, f"row report missing: {required}")


def main() -> None:
    require_visibility_row()
    require_row_evidence()
    require_report()
    print(f"{LINEAR} validation passed: {ROW_ID}=expected-unsupported supportScoreIncreased=false")


if __name__ == "__main__":
    main()
