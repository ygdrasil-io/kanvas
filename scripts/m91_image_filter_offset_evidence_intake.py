#!/usr/bin/env python3
"""Generate and validate M91-IF-3A OffsetImageFilterGM evidence intake."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
CANDIDATE_READINESS_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-candidate-readiness/summary.json"
ROUTE_CPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-cpu.json"
ROUTE_GPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-gpu.json"
FOR468_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/for468-skia-gm-offsetimagefilter-evidence.json"
FOR468_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-468-skia-gm-offsetimagefilter-evidence.md"
D51_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/d51-offsetimagefilter-row-specific-evidence.json"
D51_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-d51-4-offsetimagefilter-row-specific-evidence.md"
D50_VISIBILITY_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json"
GM_SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/OffsetImageFilterGM.kt"
GM_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/OffsetImageFilterTest.kt"
SKIA_SIMILARITY_PROPERTIES = ROOT / "skia-integration-tests/test-similarity-scores.properties"
CPU_RASTER_SIMILARITY_PROPERTIES = ROOT / "cpu-raster/test-similarity-scores.properties"
KANVAS_SKIA_SIMILARITY_PROPERTIES = ROOT / "kanvas-skia/test-similarity-scores.properties"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-evidence-intake"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-offsetimagefilter"
SOURCE_GM = "OffsetImageFilterGM"
FALLBACK = "image-filter.offset.row-specific-artifacts-required"
EXPECTED_CPU_ROUTE = "cpu.image-filter.offset-image-filter.expected-unsupported"
EXPECTED_GPU_ROUTE = "webgpu.image-filter.offset-image-filter.expected-unsupported"

REQUIRED_EVIDENCE = [
    {
        "kind": "row-specific graph dump",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/graph.json",
    },
    {
        "kind": "intermediate texture ownership",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/intermediate-ownership.json",
    },
    {
        "kind": "row-specific Skia/reference artifact",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png",
    },
    {
        "kind": "CPU route evidence with fallbackReason=none",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json",
    },
    {
        "kind": "WebGPU route evidence with fallbackReason=none",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json",
    },
    {
        "kind": "CPU/GPU render artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png",
    },
    {
        "kind": "CPU/GPU render artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json",
    },
    {
        "kind": "performance impact evidence",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json",
    },
]
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "requiredSmokeCandidateAllowed": False,
    "generalImageFilterDagSupport": False,
    "genericImageFilterDagCompiler": False,
    "cropImageFilterDagSupport": False,
    "picturePrepassSupport": False,
    "arbitraryLayerPrepass": False,
    "cpuReadbackFallbackAdded": False,
    "adjacentSimpleOffsetEvidenceInherited": False,
    "ganeshPort": False,
    "graphitePort": False,
    "dynamicSkSLCompiler": False,
    "dynamicSkSLIR": False,
    "dynamicSkSLVM": False,
}
ROUTE_NON_CLAIMS = {
    "supportClaimAdded": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "belowThresholdCountedAsProductionGap": False,
    "broadImageFilterDAGSupport": False,
    "genericImageFilterDagCompiler": False,
    "cpuReadbackFallbackAdded": False,
    "arbitraryLayerPrepass": False,
    "ganeshPort": False,
    "graphitePort": False,
    "dynamicSkSLCompiler": False,
    "dynamicSkSLIR": False,
    "dynamicSkSLVM": False,
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
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def load_property_value(path: Path, key: str) -> str:
    values: list[str] = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        current_key, value = line.split("=", 1)
        if current_key.strip() == key:
            values.append(value.strip())
    require(len(values) == 1, f"{rel(path)} must contain exactly one {key} entry")
    return values[0]


def registry_row(registry: dict[str, Any]) -> dict[str, Any]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(row, dict), f"missing registry row: {ROW_ID}")
    require(row.get("family") == "image-filter", f"{ROW_ID}: family changed")
    require(row.get("status") == "expected-unsupported", f"{ROW_ID}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{ROW_ID}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{ROW_ID}: policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, f"{ROW_ID}: fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", f"{ROW_ID}: routeCpu must remain expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", f"{ROW_ID}: routeGpu must remain expected-unsupported")
    require(row.get("nextTicketType") == "policy-visibility", f"{ROW_ID}: nextTicketType must remain policy-visibility")
    refusals = row.get("rowSpecificRefusals")
    require(isinstance(refusals, list) and len(refusals) == 1, f"{ROW_ID}: expected one rowSpecificRefusals entry")
    refusal = refusals[0]
    require(refusal.get("classification") == "row-specific-expected-unsupported-no-support-claim", f"{ROW_ID}: refusal classification changed")
    require(refusal.get("linear") == "FOR-468", f"{ROW_ID}: refusal linear ticket changed")
    require(refusal.get("json") == rel(FOR468_JSON), f"{ROW_ID}: FOR-468 JSON link changed")
    require(refusal.get("report") == rel(FOR468_REPORT), f"{ROW_ID}: FOR-468 report link changed")
    require(refusal.get("supportScoreIncreased") is False, f"{ROW_ID}: support score must not increase")
    return row


def candidate_row(readiness: dict[str, Any]) -> dict[str, Any]:
    require(readiness.get("classification") == "image-filter-candidate-readiness-no-new-rendering-support", "candidate readiness classification mismatch")
    ticket = readiness.get("nextRecommendedTicket")
    require(isinstance(ticket, dict), "candidate readiness missing nextRecommendedTicket")
    require(ticket.get("id") == "M91-IF-3A", "candidate readiness no longer points to M91-IF-3A")
    require(ticket.get("rowId") == ROW_ID, "candidate readiness next row changed")
    require(ticket.get("supportClaimAllowed") is False, "M91-IF-3A must not allow support claims")
    require(ticket.get("promotionAllowedWithoutEvidence") is False, "M91-IF-3A must block promotion without evidence")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "candidate readiness candidates must be a list")
    candidate = next((item for item in candidates if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(candidate, dict), f"candidate readiness missing {ROW_ID}")
    require(candidate.get("priority") == 1, f"{ROW_ID}: candidate priority changed")
    require(candidate.get("promotionTicket") == "M91-IF-3A", f"{ROW_ID}: promotionTicket changed")
    require(candidate.get("readyForPromotion") is False, f"{ROW_ID}: candidate must remain not ready")
    require(candidate.get("supportClaim") is False, f"{ROW_ID}: candidate supportClaim must remain false")
    require(candidate.get("policyOnly") is True, f"{ROW_ID}: candidate must remain policy-only")
    require(candidate.get("status") == "expected-unsupported", f"{ROW_ID}: candidate status changed")
    require(candidate.get("fallbackReason") == FALLBACK, f"{ROW_ID}: candidate fallback changed")
    require(candidate.get("missingEvidence") == [item["kind"] for item in REQUIRED_EVIDENCE[:3]] + [
        "CPU route evidence with fallbackReason=none",
        "WebGPU route evidence with fallbackReason=none",
        "CPU/GPU render artifacts",
        "CPU/GPU diff/stat artifacts",
        "performance impact evidence",
    ], f"{ROW_ID}: candidate missing evidence changed")
    return candidate


def validate_route_payload(route: dict[str, Any], backend: str) -> None:
    expected_route = EXPECTED_CPU_ROUTE if backend == "CPU" else EXPECTED_GPU_ROUTE
    require(route.get("rowId") == ROW_ID, f"{backend} route rowId mismatch")
    require(route.get("sourceGm") == SOURCE_GM, f"{backend} route sourceGm mismatch")
    require(route.get("backend") == backend, f"{backend} route backend mismatch")
    require(route.get("status") == "expected-unsupported", f"{backend} route must remain expected-unsupported")
    require(route.get("route") == expected_route, f"{backend} route changed")
    require(route.get("fallbackReason") == FALLBACK, f"{backend} route fallback changed")
    require(route.get("policyOnlyArtifact") is True, f"{backend} route must remain policy-only")
    require(route.get("ticket") == "M91-IF-1", f"{backend} route ticket changed")
    require(route.get("nonClaims") == ROUTE_NON_CLAIMS, f"{backend} route nonClaims changed")


def validate_route(path: Path, backend: str) -> dict[str, Any]:
    route = load_json(path)
    validate_route_payload(route, backend)
    return route


def validate_for468(payload: dict[str, Any]) -> None:
    require(payload.get("classification") == "row-specific-expected-unsupported-no-support-claim", "FOR-468 classification changed")
    require(payload.get("linear") == "FOR-468", "FOR-468 linear id changed")
    row = payload.get("row")
    require(isinstance(row, dict), "FOR-468 row must be an object")
    require(row.get("inventoryId") == ROW_ID, "FOR-468 row id changed")
    require(row.get("status") == "expected-unsupported", "FOR-468 row status changed")
    require(row.get("fallbackReason") == FALLBACK, "FOR-468 fallback changed")
    require(row.get("reference", {}).get("status") == "not-generated", "FOR-468 reference status changed")
    require(row.get("cpu", {}).get("status") == "expected-unsupported", "FOR-468 CPU status changed")
    require(row.get("gpu", {}).get("status") == "expected-unsupported", "FOR-468 GPU status changed")
    require(row.get("diffStats", {}).get("status") == "not-computed", "FOR-468 diff/stat status changed")
    require(payload.get("scoreImpact", {}).get("supportScoreIncreased") is False, "FOR-468 support score changed")
    non_claims = payload.get("nonClaims")
    require(isinstance(non_claims, dict), "FOR-468 nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"FOR-468 nonClaims.{key} must remain false")


def validate_d51(payload: dict[str, Any]) -> None:
    require(payload.get("inventoryId") == ROW_ID, "D51 inventory id changed")
    require(payload.get("status") == "expected-unsupported", "D51 status changed")
    require(payload.get("fallbackReason") in {FALLBACK, "image-filter.offset.crop-prepass-scaled-clipped-webgpu-artifacts-required"}, "D51 fallback changed")
    require(payload.get("scoreImpact", {}).get("supportScoreIncreased") is False, "D51 support score changed")
    require(payload.get("scoreImpact", {}).get("skiaComparableScoreIncreased") is False, "D51 Skia-comparable score changed")
    non_claims = payload.get("nonClaims")
    require(isinstance(non_claims, dict), "D51 nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"D51 nonClaims.{key} must remain false")
    missing = payload.get("missingEvidence")
    if missing is None:
        missing = payload.get("rowSpecificAudit", {}).get("whyNotPromotable")
    require(isinstance(missing, list) and missing, "D51 missing evidence must remain non-empty")


def validate_visibility(visibility: dict[str, Any]) -> dict[str, Any]:
    scenes = visibility.get("scenes")
    require(isinstance(scenes, list), "D50 visibility scenes must be a list")
    scene = next((item for item in scenes if isinstance(item, dict) and item.get("id") == ROW_ID), None)
    require(isinstance(scene, dict), f"D50 visibility missing {ROW_ID}")
    require(scene.get("status") == "expected-unsupported", "D50 visibility status changed")
    require(scene.get("policyOnlyArtifacts") is True, "D50 visibility must remain policy-only")
    require(scene.get("fallbackReason") == FALLBACK, "D50 visibility fallback changed")
    require(scene.get("sourceReport") == rel(FOR468_REPORT), "D50 visibility source report changed")
    return scene


def png_present(path: Path) -> bool:
    return path.is_file() and path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def validate_present_artifact(item: dict[str, str], path: Path) -> tuple[str, bool]:
    required_path = item["requiredPath"]
    if not path.exists():
        return "missing", False
    if required_path.endswith(".png"):
        require(png_present(path), f"{required_path}: present PNG has invalid header")
        return "present-non-promotional", True
    payload = load_json(path)
    require(payload.get("rowId", ROW_ID) == ROW_ID or payload.get("sceneId", ROW_ID) == ROW_ID, f"{required_path}: row/scene id changed")
    require(payload.get("supportClaim", False) is False, f"{required_path}: supportClaim must remain false")
    if required_path.endswith("graph.json"):
        require(payload.get("fallbackReason") in {FALLBACK, None}, f"{required_path}: graph fallback changed")
    elif required_path.endswith("intermediate-ownership.json"):
        require(payload.get("generalDagCompilerAdded", False) is False, f"{required_path}: must not add general DAG compiler")
        require(payload.get("cpuReadbackFallbackAdded", False) is False, f"{required_path}: must not add CPU/readback fallback")
    elif required_path.endswith("route-cpu.json"):
        require(payload.get("backend") == "CPU", f"{required_path}: backend mismatch")
        require(payload.get("fallbackReason") == "none", f"{required_path}: CPU route must use fallbackReason=none")
    elif required_path.endswith("route-gpu.json"):
        require(payload.get("backend") == "WebGPU", f"{required_path}: backend mismatch")
        require(payload.get("fallbackReason") == "none", f"{required_path}: GPU route must use fallbackReason=none")
    elif required_path.endswith("stats.json"):
        require(payload.get("thresholdLowered", False) is False, f"{required_path}: threshold must not be lowered")
    elif required_path.endswith("performance.json"):
        require(payload.get("readinessMoved", False) is False, f"{required_path}: performance must not move readiness")
    return "present-non-promotional", True


def evidence_status() -> list[dict[str, Any]]:
    statuses: list[dict[str, Any]] = []
    for item in REQUIRED_EVIDENCE:
        path = ROOT / item["requiredPath"]
        status, validated = validate_present_artifact(item, path)
        statuses.append(
            {
                "kind": item["kind"],
                "path": item["requiredPath"],
                "present": path.exists(),
                "promotional": False,
                "validatedNonPromotional": validated,
                "status": status,
            }
        )
    return statuses


def historical_signals() -> list[dict[str, Any]]:
    for path in [
        FOR468_REPORT,
        FOR468_JSON,
        D51_REPORT,
        D51_JSON,
        GM_SOURCE,
        GM_TEST,
        SKIA_SIMILARITY_PROPERTIES,
        CPU_RASTER_SIMILARITY_PROPERTIES,
        KANVAS_SKIA_SIMILARITY_PROPERTIES,
    ]:
        require(path.is_file(), f"missing historical signal: {rel(path)}")
    gm_source = GM_SOURCE.read_text(encoding="utf-8")
    gm_test = GM_TEST.read_text(encoding="utf-8")
    require("public class OffsetImageFilterGM" in gm_source, "OffsetImageFilterGM class missing")
    require('override fun getName(): String = "offsetimagefilter"' in gm_source, "OffsetImageFilterGM name changed")
    require("OffsetImageFilterGM()" in gm_test, "OffsetImageFilterGM test no longer references GM")
    require(load_property_value(SKIA_SIMILARITY_PROPERTIES, SOURCE_GM) == "84.515", "OffsetImageFilterGM skia similarity changed")
    require(load_property_value(CPU_RASTER_SIMILARITY_PROPERTIES, SOURCE_GM) == "84.505", "OffsetImageFilterGM cpu-raster similarity changed")
    require(load_property_value(KANVAS_SKIA_SIMILARITY_PROPERTIES, SOURCE_GM) == "84.505", "OffsetImageFilterGM kanvas-skia similarity changed")
    return [
        {
            "kind": "FOR-468 row-specific refusal",
            "path": rel(FOR468_REPORT),
            "promotional": False,
            "reason": "FOR-468 formalizes the expected-unsupported refusal and missing row-specific artifacts.",
        },
        {
            "kind": "FOR-468 structured evidence",
            "path": rel(FOR468_JSON),
            "promotional": False,
            "reason": "Structured refusal evidence keeps reference, CPU, GPU, and diff/stat unavailable.",
        },
        {
            "kind": "D51 row-specific precision evidence",
            "path": rel(D51_REPORT),
            "promotional": False,
            "reason": "D51 narrows the missing artifact contract without promoting OffsetImageFilterGM.",
        },
        {
            "kind": "OffsetImageFilterGM Kotlin source",
            "path": rel(GM_SOURCE),
            "promotional": False,
            "reason": "A source port is provenance only; it is not route, render, diff/stat, or performance evidence.",
        },
        {
            "kind": "OffsetImageFilterGM historical test",
            "path": rel(GM_TEST),
            "promotional": False,
            "reason": "Historical CPU similarity coverage is not M91 row-specific CPU/WebGPU route evidence.",
        },
        {
            "kind": "historical skia similarity",
            "path": rel(SKIA_SIMILARITY_PROPERTIES),
            "observed": "OffsetImageFilterGM=84.515",
            "promotional": False,
            "reason": "A historical below-threshold similarity signal is not counted as a production feature gap or support proof.",
        },
        {
            "kind": "historical cpu-raster similarity",
            "path": rel(CPU_RASTER_SIMILARITY_PROPERTIES),
            "observed": "OffsetImageFilterGM=84.505",
            "promotional": False,
            "reason": "Mirrored historical score only preserves provenance.",
        },
        {
            "kind": "historical kanvas-skia similarity",
            "path": rel(KANVAS_SKIA_SIMILARITY_PROPERTIES),
            "observed": "OffsetImageFilterGM=84.505",
            "promotional": False,
            "reason": "Mirrored historical score only preserves provenance.",
        },
    ]


def build_summary(
    registry: dict[str, Any],
    readiness: dict[str, Any],
    route_cpu: dict[str, Any],
    route_gpu: dict[str, Any],
    for468: dict[str, Any],
    d51: dict[str, Any],
    visibility: dict[str, Any],
) -> dict[str, Any]:
    row = registry_row(registry)
    candidate = candidate_row(readiness)
    validate_route_payload(route_cpu, "CPU")
    validate_route_payload(route_gpu, "WebGPU")
    validate_for468(for468)
    validate_d51(d51)
    visible = validate_visibility(visibility)
    required = evidence_status()
    present_count = sum(1 for item in required if item["present"])
    missing_count = sum(1 for item in required if not item["present"])
    signals = historical_signals()
    status = "blocked-by-missing-row-specific-evidence"
    if present_count > 0 and missing_count > 0:
        status = "partial-row-specific-evidence-present-non-promotional"
    elif present_count == len(required):
        status = "row-specific-evidence-present-non-promotional"
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_offset_evidence_intake.py",
        "milestone": "M91",
        "ticket": "M91-IF-3A",
        "classification": "image-filter-offset-evidence-intake-no-new-rendering-support",
        "status": status,
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "routeCpu": rel(ROUTE_CPU_JSON),
            "routeGpu": rel(ROUTE_GPU_JSON),
            "for468Json": rel(FOR468_JSON),
            "for468Report": rel(FOR468_REPORT),
            "d51Json": rel(D51_JSON),
            "d51Report": rel(D51_REPORT),
            "visibility": rel(D50_VISIBILITY_JSON),
        },
        "upstreamReadinessState": {
            "activeNextRecommendedTicket": "M91-IF-3A",
            "activeNextRecommendedRow": ROW_ID,
            "thisIntakeIsOutOfOrder": False,
            "reason": "M91-IF-3A is the active candidate-readiness recommendation; this intake inventories evidence and remains non-promotional.",
        },
        "row": {
            "rowId": ROW_ID,
            "sourceGm": SOURCE_GM,
            "status": row.get("status"),
            "supportClaim": False,
            "policyOnly": True,
            "fallbackReason": FALLBACK,
            "routeCpu": row.get("routeCpu"),
            "routeGpu": row.get("routeGpu"),
            "candidatePriority": candidate.get("priority"),
            "visibilityPolicyOnlyArtifacts": visible.get("policyOnlyArtifacts"),
        },
        "routeDiagnostics": {
            "cpu": route_cpu.get("route"),
            "gpu": route_gpu.get("route"),
            "fallbackReason": FALLBACK,
            "status": "expected-unsupported",
        },
        "counters": {
            "requiredEvidenceItems": len(required),
            "presentEvidenceItems": present_count,
            "missingEvidenceItems": missing_count,
            "validatedNonPromotionalEvidenceItems": sum(1 for item in required if item["validatedNonPromotional"]),
            "historicalSignals": len(signals),
            "promotionalHistoricalSignals": sum(1 for item in signals if item["promotional"]),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "requiredEvidence": required,
        "historicalSignals": signals,
        "nextRecommendedTicket": {
            "id": "M91-IF-3A-REF",
            "scope": "Produce row-specific OffsetImageFilterGM graph dump, intermediate ownership, Skia/reference, CPU/WebGPU fallbackReason=none routes, render, diff/stat, and performance artifacts before any support evaluation.",
            "supportClaimAllowed": False,
            "promotionAllowedWithoutEvidence": False,
        },
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_offset_evidence_intake.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetEvidenceIntake",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    row = summary["row"]
    lines = [
        "# M91 Image Filter Offset Evidence Intake",
        "",
        f"Status: {summary['status']}",
        "",
        "This report materializes `M91-IF-3A` for `skia-gm-offsetimagefilter`. It inventories current evidence and keeps support evaluation blocked until row-specific graph, ownership, reference, CPU/WebGPU route, render, diff/stat, and performance artifacts exist.",
        "",
        "## Row",
        "",
        f"- Row ID: `{row['rowId']}`",
        f"- Source GM: `{row['sourceGm']}`",
        f"- Status: `{row['status']}`",
        f"- Support claim: `{row['supportClaim']}`",
        f"- Policy-only: `{row['policyOnly']}`",
        f"- Fallback: `{row['fallbackReason']}`",
        f"- CPU route: `{row['routeCpu']}`",
        f"- GPU route: `{row['routeGpu']}`",
        "",
        "## Counters",
        "",
        f"- Required evidence items: `{counters['requiredEvidenceItems']}`",
        f"- Present evidence items: `{counters['presentEvidenceItems']}`",
        f"- Missing evidence items: `{counters['missingEvidenceItems']}`",
        f"- Validated non-promotional evidence items: `{counters['validatedNonPromotionalEvidenceItems']}`",
        f"- Historical signals: `{counters['historicalSignals']}`",
        f"- Promotional historical signals: `{counters['promotionalHistoricalSignals']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
        "",
        "## Required Evidence",
        "",
    ]
    for item in summary["requiredEvidence"]:
        lines.append(
            f"- `{item['kind']}`: `{item['status']}` at `{item['path']}`; "
            f"present=`{item['present']}` promotional=`{item['promotional']}`"
        )
    lines.extend(["", "## Historical Signals", ""])
    for item in summary["historicalSignals"]:
        observed = f" (`{item['observed']}`)" if "observed" in item else ""
        lines.append(f"- `{item['kind']}`{observed}: `{item['path']}`; promotional=`{item['promotional']}`. {item['reason']}")
    upstream = summary["upstreamReadinessState"]
    lines.extend(
        [
            "",
            "## Upstream Readiness State",
            "",
            f"- Active next recommended ticket: `{upstream['activeNextRecommendedTicket']}`",
            f"- Active next recommended row: `{upstream['activeNextRecommendedRow']}`",
            f"- This intake is out of order: `{upstream['thisIntakeIsOutOfOrder']}`",
            f"- Reason: {upstream['reason']}",
        ]
    )
    ticket = summary["nextRecommendedTicket"]
    lines.extend(
        [
            "",
            "## Next Recommended Ticket",
            "",
            f"- ID: `{ticket['id']}`",
            f"- Scope: {ticket['scope']}",
            f"- Support claim allowed: `{ticket['supportClaimAllowed']}`",
            f"- Promotion allowed without evidence: `{ticket['promotionAllowedWithoutEvidence']}`",
            "",
            "## Support Guard",
            "",
        ]
    )
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "image-filter-offset-evidence-intake-no-new-rendering-support", "classification mismatch")
    require(
        summary.get("status") in {
            "blocked-by-missing-row-specific-evidence",
            "partial-row-specific-evidence-present-non-promotional",
            "row-specific-evidence-present-non-promotional",
        },
        "status mismatch",
    )
    counters = summary.get("counters")
    row = summary.get("row")
    required = summary.get("requiredEvidence")
    signals = summary.get("historicalSignals")
    ticket = summary.get("nextRecommendedTicket")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("requiredEvidenceItems") == len(REQUIRED_EVIDENCE), "required evidence count changed")
    require(0 <= counters.get("presentEvidenceItems") <= len(REQUIRED_EVIDENCE), "present evidence count out of range")
    require(counters.get("missingEvidenceItems") == len(REQUIRED_EVIDENCE) - counters.get("presentEvidenceItems"), "missing evidence count mismatch")
    require(counters.get("validatedNonPromotionalEvidenceItems") == counters.get("presentEvidenceItems"), "present evidence must validate as non-promotional")
    require(counters.get("historicalSignals") == 8, "historical signal count changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "historical signals must remain non-promotional")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "intake must not promote dashboard")
    require(counters.get("thresholdChanges") == 0, "intake must not change thresholds")
    require(isinstance(row, dict), "row must be an object")
    require(row.get("rowId") == ROW_ID, "rowId changed")
    require(row.get("supportClaim") is False, "supportClaim must remain false")
    require(row.get("policyOnly") is True, "policyOnly must remain true")
    require(row.get("routeCpu") == "expected-unsupported", "routeCpu must remain expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", "routeGpu must remain expected-unsupported")
    require(isinstance(required, list) and len(required) == len(REQUIRED_EVIDENCE), "requiredEvidence list changed")
    require(all(item.get("promotional") is False for item in required if isinstance(item, dict)), "required evidence must remain non-promotional")
    require(all(item.get("validatedNonPromotional") == item.get("present") for item in required if isinstance(item, dict)), "present evidence must be validated non-promotional")
    require(isinstance(signals, list) and len(signals) == 8, "historicalSignals list changed")
    require(all(item.get("promotional") is False for item in signals if isinstance(item, dict)), "historical signals must be non-promotional")
    require(isinstance(ticket, dict), "nextRecommendedTicket must be an object")
    require(ticket.get("supportClaimAllowed") is False, "next ticket must not allow support claims")
    require(ticket.get("promotionAllowedWithoutEvidence") is False, "next ticket must block promotion without evidence")
    upstream = summary.get("upstreamReadinessState")
    require(isinstance(upstream, dict), "upstreamReadinessState must be an object")
    require(upstream.get("activeNextRecommendedTicket") == "M91-IF-3A", "upstream active next ticket changed")
    require(upstream.get("activeNextRecommendedRow") == ROW_ID, "upstream active next row changed")
    require(upstream.get("thisIntakeIsOutOfOrder") is False, "Offset intake must remain the active recommendation")
    require(summary.get("supportGuard") == NON_CLAIMS, "supportGuard must match the full non-claims contract")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        readiness = load_json(CANDIDATE_READINESS_JSON)
        route_cpu = validate_route(ROUTE_CPU_JSON, "CPU")
        route_gpu = validate_route(ROUTE_GPU_JSON, "WebGPU")
        for468 = load_json(FOR468_JSON)
        d51 = load_json(D51_JSON)
        visibility = load_json(D50_VISIBILITY_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(registry, readiness, route_cpu, route_gpu, for468, d51, visibility)
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_offset_evidence_intake: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 image-filter OffsetImageFilterGM evidence intake validation passed: "
        f"presentEvidenceItems={reloaded['counters']['presentEvidenceItems']} "
        f"missingEvidenceItems={reloaded['counters']['missingEvidenceItems']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
