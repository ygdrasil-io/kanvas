#!/usr/bin/env python3
"""Validate the FOR-472 skia-gm-gradients2ptconical row-specific refusal evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
LINEAR = "FOR-472"
ROW_ID = "skia-gm-gradients2ptconical"
FALLBACK_REASON = "gradient.2ptconical.row-specific-artifacts-required"
VISIBILITY = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json"
ROW_EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/for472-skia-gm-gradients2ptconical-evidence.json"
ROW_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-for-472-skia-gm-gradients2ptconical-evidence.md"

FEATURE_NON_CLAIMS = (
    "broadGradientEnvelopeSupportClaimAddedByFor472",
    "conicalGradientSupportClaimAddedByFor472",
    "gradientTileModeSupportClaimAddedByFor472",
    "gradients2ptConicalEvidenceInherited",
    "sweepGradientEvidenceInherited",
)
GLOBAL_NON_CLAIMS = (
    "dashboardRowAddedByFor472",
    "dashboardStatusChangedByFor472",
    "fallbackPolicyChanged",
    "pipelineKeyChanged",
    "productionCodeChanged",
    "scoringChanged",
    "skiaComparableClaimAddedByFor472",
    "supportClaimAddedByFor472",
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
    require(row.get("cpuRoute") == "cpu.gradient.2ptconical.expected-unsupported", "D50 CPU route mismatch")
    require(row.get("gpuRoute") == "webgpu.gradient.2ptconical.expected-unsupported", "D50 GPU route mismatch")
    require("Gradients2ptConicalGM" in row.get("nonClaim", ""), "D50 non-claim must name Gradients2ptConicalGM")
    require("two-point conical gradients" in row.get("nonClaim", ""), "D50 non-claim must preserve two-point conical refusal")
    remap = row.get("visibilityStatusRemap")
    require(isinstance(remap, dict), "D50 row must preserve visibilityStatusRemap")
    require(remap.get("fromInventoryClassification") == "excluded", "visibility remap source classification mismatch")
    require(remap.get("toDashboardStatus") == "expected-unsupported", "visibility remap target status mismatch")
    require("readiness and fidelity support counters do not move" in remap.get("reason", ""), "visibility remap must not move support counters")


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
    require(row.get("cpu", {}).get("route") == "cpu.gradient.2ptconical.expected-unsupported", "CPU route mismatch")
    require(row.get("gpu", {}).get("status") == "expected-unsupported", "GPU must remain expected-unsupported")
    require(row.get("gpu", {}).get("route") == "webgpu.gradient.2ptconical.expected-unsupported", "GPU route mismatch")
    require(row.get("diffStats", {}).get("status") == "not-computed", "diff/stat must remain not-computed")

    provenance = row.get("gradients2ptConicalProvenance")
    require(isinstance(provenance, dict), "Gradients2ptConical provenance must be recorded")
    require(provenance.get("scene") == "ConicalGradientsGM", "scene provenance mismatch")
    require(provenance.get("kotlinSource") == "skia-integration-tests/src/main/kotlin/org/skia/tests/ConicalGradientsGM.kt", "Kotlin source provenance mismatch")
    require(provenance.get("upstreamSource") == "gm/gradients_2pt_conical.cpp", "upstream source provenance mismatch")
    require("alias for gradients_2pt_conical_inside" in provenance.get("aliasStatus", ""), "alias status mismatch")
    require("readiness and fidelity support counters do not move" in provenance.get("visibilityRemap", ""), "visibility remap policy mismatch")
    boundary_evidence = set(provenance.get("boundaryEvidence", []))
    require(
        {
            "reports/wgsl-pipeline/2026-05-31-m56-pm-report.md",
            "reports/wgsl-pipeline/2026-05-31-m56-unsupported-to-pass-selection.md",
            "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
        }
        <= boundary_evidence,
        "boundary evidence mismatch",
    )
    historical_tests = set(provenance.get("historicalTests", []))
    require(
        {
            "skia-integration-tests/src/test/kotlin/org/skia/tests/ConicalGradientsTest.kt",
            "skia-integration-tests/src/test/kotlin/org/skia/tests/ConicalGradients2ptTest.kt",
            "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ConicalGradientsWebGpuTest.kt",
        }
        <= historical_tests,
        "historical test provenance mismatch",
    )
    unsupported = set(provenance.get("unsupportedBroadClaims", []))
    require(
        {
            "two-point conical gradient support",
            "broad conical-gradient support",
            "gradient tile-mode parity",
            "broader gradient envelope",
            "sweep-gradient evidence inheritance",
            "historical conical test inheritance",
        }
        <= unsupported,
        "unsupported broad claims mismatch",
    )
    route_diagnostics = row.get("routeDiagnostics")
    require(isinstance(route_diagnostics, dict), "route diagnostics must be recorded")
    require(route_diagnostics.get("cpu") == "cpu.gradient.2ptconical.expected-unsupported", "route diagnostics CPU mismatch")
    require(route_diagnostics.get("gpu") == "webgpu.gradient.2ptconical.expected-unsupported", "route diagnostics GPU mismatch")
    require(route_diagnostics.get("fallbackReason") == FALLBACK_REASON, "route diagnostics fallback mismatch")
    route_policy = route_diagnostics.get("policy", "")
    require("M56/M63 sweep-gradient and bounded gradient evidence" in route_policy, "route policy must name sweep/bounded boundary")
    require("fallbackReason=none" in route_policy, "route policy must describe future support proof")
    require("without threshold, scoring, fallback policy, PipelineKey, or broad gradient-envelope changes" in route_policy, "route policy must preserve non-claims")
    for key in FEATURE_NON_CLAIMS + GLOBAL_NON_CLAIMS:
        require(row.get("nonClaims", {}).get(key) is False, f"row {key} must remain false")


def require_report() -> None:
    require(ROW_REPORT.is_file(), f"missing row report: {rel(ROW_REPORT)}")
    text = ROW_REPORT.read_text(encoding="utf-8")
    for required in (
        "expected-unsupported",
        "ne promeut pas la scene",
        "exclusion inventory remappee",
        "ne deplace pas les compteurs de support",
        "support borne de sweep-gradient ne prouve pas les gradients two-point conical",
        "ne s'etend pas a `skia-gm-gradients2ptconical`",
        "ne remplacent pas une preuve registry M89",
        "Le score de support ne monte pas",
        "0 support ajoute",
        "Aucun support two-point conical gradient, conical-gradient large, gradient tile-mode parity, broader gradient envelope ou heritage de preuve sweep-gradient/conical historique",
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
