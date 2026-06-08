#!/usr/bin/env python3
"""Generate and validate M91-IF-3B ImageMakeWithFilterGM evidence intake."""

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
ROUTE_CPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json"
ROUTE_GPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json"
FOR470_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json"
FOR470_REPORT = ROOT / "reports/wgsl-pipeline/2026-06-08-for-470-skia-gm-imagemakewithfilter-evidence.md"
GM_SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt"
GM_TEST = ROOT / "skia-integration-tests/src/test/kotlin/org/skia/tests/ImageMakeWithFilterTest.kt"
SKIA_SIMILARITY_PROPERTIES = ROOT / "skia-integration-tests/test-similarity-scores.properties"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter"
GRAPH_JSON = ARTIFACT_DIR / "graph.json"
OWNERSHIP_JSON = ARTIFACT_DIR / "intermediate-ownership.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-evidence-intake"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-imagemakewithfilter"
SOURCE_GM = "ImageMakeWithFilterGM"
FALLBACK = "image-filter.imagemakewithfilter.row-specific-artifacts-required"
EXPECTED_CPU_ROUTE = "cpu.image-filter.image-make-with-filter.expected-unsupported"
EXPECTED_GPU_ROUTE = "webgpu.image-filter.image-make-with-filter.expected-unsupported"
EXPECTED_FOR470_CPU_ROUTE = "cpu.image-filter.imagemakewithfilter.expected-unsupported"
EXPECTED_FOR470_GPU_ROUTE = "webgpu.image-filter.imagemakewithfilter.expected-unsupported"
HISTORICAL_SCORE = "84.35382962588474"
CLASSIFICATION = "image-filter-imagemakewithfilter-evidence-intake-no-new-rendering-support"

REQUIRED_EVIDENCE = [
    ("row-specific graph dump", "graph.json"),
    ("intermediate texture ownership", "intermediate-ownership.json"),
    ("row-specific Skia/reference artifact", "skia.png"),
    ("CPU route evidence with fallbackReason=none", "route-cpu.json"),
    ("WebGPU route evidence with fallbackReason=none", "route-gpu.json"),
    ("CPU/GPU render artifacts", "cpu.png"),
    ("CPU/GPU render artifacts", "gpu.png"),
    ("CPU/GPU diff/stat artifacts", "cpu-diff.png"),
    ("CPU/GPU diff/stat artifacts", "gpu-diff.png"),
    ("CPU/GPU diff/stat artifacts", "stats.json"),
    ("performance impact evidence", "performance.json"),
]
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "imagemakewithfilterEvidenceInherited": False,
    "boundedM61M89EvidenceInherited": False,
    "broadImageFilterDAGSupport": False,
    "genericImageFilterDagCompiler": False,
    "cropPrepassSupport": False,
    "picturePrepassSupport": False,
    "layerPrepassSupport": False,
    "cpuReadbackFallbackAdded": False,
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
FOR470_NON_CLAIMS = {
    "arbitraryImageFilterDagSupportClaimAddedByFor470": False,
    "broadImageFilterSupportClaimAddedByFor470": False,
    "cropPrepassSupportClaimAddedByFor470": False,
    "dashboardRowAddedByFor470": False,
    "dashboardStatusChangedByFor470": False,
    "fallbackPolicyChanged": False,
    "imagemakewithfilterEvidenceInherited": False,
    "layerPrepassSupportClaimAddedByFor470": False,
    "picturePrepassSupportClaimAddedByFor470": False,
    "pipelineKeyChanged": False,
    "productionCodeChanged": False,
    "scoringChanged": False,
    "skiaComparableClaimAddedByFor470": False,
    "supportClaimAddedByFor470": False,
    "thresholdChanged": False,
    "upstreamSourceChanged": False,
    "wgslProductionChanged": False,
}
EXPECTED_CANDIDATE_PRESENT = [
    "row-specific refusal link",
    "CPU expected-unsupported route diagnostic",
    "WebGPU expected-unsupported route diagnostic",
]
EXPECTED_CANDIDATE_MISSING = [
    "row-specific graph dump",
    "intermediate texture ownership",
    "row-specific Skia/reference artifact",
    "CPU route evidence with fallbackReason=none",
    "WebGPU route evidence with fallbackReason=none",
    "CPU/GPU render artifacts",
    "CPU/GPU diff/stat artifacts",
    "performance impact evidence",
]


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(payload, dict), f"{rel(path)} root must be an object")
    return payload


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def property_value(path: Path, key: str) -> str:
    values: list[str] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            current, value = line.split("=", 1)
            if current.strip() == key:
                values.append(value.strip())
    require(len(values) == 1, f"{rel(path)} must contain exactly one {key} value")
    return values[0]


def validate_row(row: dict[str, Any], source: str) -> None:
    require(row.get("rowId") == ROW_ID, f"{source}: rowId changed")
    require(row.get("sourceGm") == SOURCE_GM, f"{source}: sourceGm changed")
    require(row.get("status") == "expected-unsupported", f"{source}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{source}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{source}: policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, f"{source}: fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", f"{source}: CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", f"{source}: GPU route changed")


def validate_registry(registry: dict[str, Any]) -> None:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(row, dict), f"missing registry row {ROW_ID}")
    require(row.get("family") == "image-filter", "registry family changed")
    require(row.get("status") == "expected-unsupported", "registry status changed")
    require(row.get("supportClaim") is False, "registry supportClaim must remain false")
    require(row.get("policyOnly") is True, "registry policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, "registry fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", "registry CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", "registry GPU route changed")
    refusals = row.get("rowSpecificRefusals")
    require(isinstance(refusals, list) and len(refusals) == 1, "registry must keep one row-specific refusal")
    refusal = refusals[0]
    require(refusal.get("linear") == "FOR-470", "FOR-470 refusal link changed")
    require(refusal.get("json") == rel(FOR470_JSON), "FOR-470 JSON link changed")
    require(refusal.get("report") == rel(FOR470_REPORT), "FOR-470 report link changed")
    require(refusal.get("supportScoreIncreased") is False, "FOR-470 must not increase support")


def validate_candidate_readiness(readiness: dict[str, Any]) -> None:
    require(readiness.get("classification") == "image-filter-candidate-readiness-no-new-rendering-support", "candidate readiness classification changed")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "candidate readiness candidates must be a list")
    candidate = next((item for item in candidates if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(candidate, dict), f"candidate readiness missing {ROW_ID}")
    require(candidate.get("sourceGm") == SOURCE_GM, "candidate sourceGm changed")
    require(candidate.get("candidateKind") == "image-make-with-filter-gm", "candidate kind changed")
    require(candidate.get("priority") == 2, "ImageMakeWithFilter candidate priority changed")
    require(candidate.get("promotionTicket") == "M91-IF-3B", "promotion ticket changed")
    require(candidate.get("readyForPromotion") is False, "candidate must remain not ready")
    require(candidate.get("supportClaim") is False, "candidate supportClaim must remain false")
    require(candidate.get("policyOnly") is True, "candidate policyOnly must remain true")
    require(candidate.get("status") == "expected-unsupported", "candidate status changed")
    require(candidate.get("fallbackReason") == FALLBACK, "candidate fallback changed")
    require(candidate.get("routeCpu") == "expected-unsupported", "candidate CPU route changed")
    require(candidate.get("routeGpu") == "expected-unsupported", "candidate GPU route changed")
    require(candidate.get("routeCpuDiagnostic") == rel(ROUTE_CPU_JSON), "candidate CPU diagnostic path changed")
    require(candidate.get("routeGpuDiagnostic") == rel(ROUTE_GPU_JSON), "candidate GPU diagnostic path changed")
    require(candidate.get("presentEvidence") == EXPECTED_CANDIDATE_PRESENT, "candidate present evidence changed")
    require(candidate.get("missingEvidence") == EXPECTED_CANDIDATE_MISSING, "candidate missing evidence changed")


def validate_route(route: dict[str, Any], backend: str) -> None:
    expected_route = EXPECTED_CPU_ROUTE if backend == "CPU" else EXPECTED_GPU_ROUTE
    require(route.get("schemaVersion") == 1, f"{backend} route schema changed")
    require(route.get("rowId") == ROW_ID, f"{backend} route rowId changed")
    require(route.get("sourceGm") == SOURCE_GM, f"{backend} route sourceGm changed")
    require(route.get("backend") == backend, f"{backend} route backend changed")
    require(route.get("status") == "expected-unsupported", f"{backend} route status changed")
    require(route.get("route") == expected_route, f"{backend} route changed")
    require(route.get("fallbackReason") == FALLBACK, f"{backend} route fallback changed")
    require(route.get("fallbackReason") != "none", f"{backend} route must not claim fallbackReason=none")
    require(route.get("policyOnlyArtifact") is True, f"{backend} route must remain policy-only")
    require(route.get("nonClaims") == ROUTE_NON_CLAIMS, f"{backend} route nonClaims changed")


def validate_for470(payload: dict[str, Any]) -> None:
    require(payload.get("classification") == "row-specific-expected-unsupported-no-support-claim", "FOR-470 classification changed")
    require(payload.get("linear") == "FOR-470", "FOR-470 linear id changed")
    row = payload.get("row")
    require(isinstance(row, dict), "FOR-470 row must be an object")
    require(row.get("inventoryId") == ROW_ID, "FOR-470 rowId changed")
    require(row.get("status") == "expected-unsupported", "FOR-470 status changed")
    require(row.get("fallbackReason") == FALLBACK, "FOR-470 fallback changed")
    require(row.get("reference", {}).get("status") == "not-generated", "FOR-470 reference status changed")
    reference = row.get("reference", {})
    cpu = row.get("cpu", {})
    gpu = row.get("gpu", {})
    diff = row.get("diffStats", {})
    require(reference.get("required") is True, "FOR-470 reference must remain required")
    require(reference.get("refusalReason") == "candidate-specific Skia GM reference capture is missing", "FOR-470 reference refusal changed")
    require(cpu.get("status") == "expected-unsupported", "FOR-470 CPU status changed")
    require(cpu.get("required") is True, "FOR-470 CPU artifact must remain required")
    require(cpu.get("artifact") == "not-generated", "FOR-470 CPU artifact status changed")
    require(cpu.get("route") == EXPECTED_FOR470_CPU_ROUTE, "FOR-470 CPU route changed")
    require(cpu.get("refusalReason") == "row-specific CPU image-filter artifact is missing", "FOR-470 CPU refusal changed")
    require(gpu.get("status") == "expected-unsupported", "FOR-470 GPU status changed")
    require(gpu.get("required") is True, "FOR-470 GPU artifact must remain required")
    require(gpu.get("artifact") == "not-generated", "FOR-470 GPU artifact status changed")
    require(gpu.get("route") == EXPECTED_FOR470_GPU_ROUTE, "FOR-470 GPU route changed")
    require(gpu.get("fallbackReason") == FALLBACK, "FOR-470 GPU fallback changed")
    require(gpu.get("refusalReason") == "row-specific WebGPU artifact is missing", "FOR-470 GPU refusal changed")
    require(diff.get("status") == "not-computed", "FOR-470 diff/stat status changed")
    require(diff.get("required") is True, "FOR-470 diff/stat must remain required")
    require(payload.get("scoreImpact", {}).get("supportScoreIncreased") is False, "FOR-470 support score changed")
    provenance = row.get("imageMakeWithFilterProvenance")
    require(isinstance(provenance, dict), "FOR-470 provenance must be an object")
    require(provenance.get("scene") == SOURCE_GM, "FOR-470 provenance scene changed")
    require(provenance.get("kotlinSource") == rel(GM_SOURCE), "FOR-470 Kotlin source changed")
    require(provenance.get("historicalSimilarity") == f"{SOURCE_GM}={HISTORICAL_SCORE} in skia-integration-tests/test-similarity-scores.properties; this is not a D50 row-specific dashboard artifact and is not treated as a production missing feature when the only signal is tolerance.", "FOR-470 historical similarity boundary changed")
    route_diagnostics = row.get("routeDiagnostics")
    require(isinstance(route_diagnostics, dict), "FOR-470 routeDiagnostics must be an object")
    require(route_diagnostics.get("cpu") == EXPECTED_FOR470_CPU_ROUTE, "FOR-470 routeDiagnostics CPU changed")
    require(route_diagnostics.get("gpu") == EXPECTED_FOR470_GPU_ROUTE, "FOR-470 routeDiagnostics GPU changed")
    require(route_diagnostics.get("fallbackReason") == FALLBACK, "FOR-470 routeDiagnostics fallback changed")
    require(payload.get("nonClaims") == FOR470_NON_CLAIMS, "FOR-470 nonClaims changed")
    require(row.get("nonClaims") == FOR470_NON_CLAIMS, "FOR-470 row nonClaims changed")


def validate_present_artifact(kind: str, path: Path) -> tuple[str, bool]:
    if not path.exists():
        return "missing", False
    require(path in {GRAPH_JSON, OWNERSHIP_JSON}, f"{rel(path)} must remain absent until its scoped evidence ticket lands")
    payload = load_json(path)
    require(payload.get("rowId") == ROW_ID, f"{rel(path)} rowId changed")
    require(payload.get("sourceGm") == SOURCE_GM, f"{rel(path)} sourceGm changed")
    require(payload.get("status") == "expected-unsupported", f"{rel(path)} status changed")
    require(payload.get("supportClaim") is False, f"{rel(path)} supportClaim must remain false")
    require(payload.get("fallbackReason") == FALLBACK, f"{rel(path)} fallback changed")
    non_claims = payload.get("nonClaims")
    require(isinstance(non_claims, dict), f"{rel(path)} nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"{rel(path)} nonClaims.{key} must remain false")
    if path == GRAPH_JSON:
        require(kind == "row-specific graph dump", "graph kind changed")
        require(payload.get("classification") == "image-filter-imagemakewithfilter-graph-dump-no-new-rendering-support", "graph classification changed")
        require(payload.get("currentPort", {}).get("makeWithFilterExecuted") is False, "graph must not claim MakeWithFilter execution")
        grid = payload.get("grid")
        require(isinstance(grid, dict), "graph grid must be an object")
        require(grid.get("cells") == 78, "graph cell count changed")
    elif path == OWNERSHIP_JSON:
        require(kind == "intermediate texture ownership", "ownership kind changed")
        require(payload.get("classification") == "image-filter-imagemakewithfilter-intermediate-ownership-no-new-rendering-support", "ownership classification changed")
        require(payload.get("ownershipStatus") == "requirements-only", "ownership must remain requirements-only")
        require(payload.get("generalDagCompilerAdded") is False, "ownership must not add generic DAG compiler")
        require(payload.get("cpuReadbackFallbackAdded") is False, "ownership must not add CPU/readback fallback")
    return "present-non-promotional", True


def evidence_status() -> list[dict[str, Any]]:
    statuses: list[dict[str, Any]] = []
    for kind, filename in REQUIRED_EVIDENCE:
        path = ARTIFACT_DIR / filename
        status, validated = validate_present_artifact(kind, path)
        statuses.append(
            {
                "kind": kind,
                "path": rel(path),
                "present": path.exists(),
                "promotional": False,
                "validatedNonPromotional": validated,
                "status": status,
            }
        )
    return statuses


def validate_sources() -> list[dict[str, Any]]:
    for path in [FOR470_REPORT, FOR470_JSON, GM_SOURCE, GM_TEST, SKIA_SIMILARITY_PROPERTIES]:
        require(path.is_file(), f"missing source signal: {rel(path)}")
    source = GM_SOURCE.read_text(encoding="utf-8")
    test = GM_TEST.read_text(encoding="utf-8")
    require("public class ImageMakeWithFilterGM" in source, "GM source class changed")
    require('override fun getName(): String = "imagemakewithfilter"' in source, "GM name changed")
    require("13 filter columns" in source, "GM source must keep filter-column limitation visible")
    require("without actually running" in source, "GM source must keep unsupported MakeWithFilter limitation visible")
    require("ImageMakeWithFilterGM()" in test, "GM test no longer references ImageMakeWithFilterGM")
    require(property_value(SKIA_SIMILARITY_PROPERTIES, SOURCE_GM) == HISTORICAL_SCORE, "historical score changed")
    return [
        {"kind": "FOR-470 row-specific refusal", "path": rel(FOR470_REPORT), "promotional": False},
        {"kind": "FOR-470 structured evidence", "path": rel(FOR470_JSON), "promotional": False},
        {"kind": "CPU expected-unsupported route diagnostic", "path": rel(ROUTE_CPU_JSON), "promotional": False},
        {"kind": "WebGPU expected-unsupported route diagnostic", "path": rel(ROUTE_GPU_JSON), "promotional": False},
        {"kind": "ImageMakeWithFilterGM Kotlin source", "path": rel(GM_SOURCE), "promotional": False},
        {
            "kind": "historical skia similarity",
            "path": rel(SKIA_SIMILARITY_PROPERTIES),
            "observed": f"{SOURCE_GM}={HISTORICAL_SCORE}",
            "promotional": False,
        },
    ]


def build_summary() -> dict[str, Any]:
    required = evidence_status()
    signals = validate_sources()
    present_count = sum(1 for item in required if item["present"])
    missing_count = len(required) - present_count
    status = "blocked-by-missing-row-specific-evidence"
    if present_count > 0 and missing_count > 0:
        status = "partial-row-specific-evidence-present-non-promotional"
    elif present_count == len(required):
        status = "row-specific-evidence-present-non-promotional"
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_imagemakewithfilter_evidence_intake.py",
        "milestone": "M91",
        "ticket": "M91-IF-3B",
        "classification": CLASSIFICATION,
        "status": status,
        "row": {
            "rowId": ROW_ID,
            "sourceGm": SOURCE_GM,
            "status": "expected-unsupported",
            "supportClaim": False,
            "policyOnly": True,
            "fallbackReason": FALLBACK,
            "routeCpu": "expected-unsupported",
            "routeGpu": "expected-unsupported",
        },
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "routeCpu": rel(ROUTE_CPU_JSON),
            "routeGpu": rel(ROUTE_GPU_JSON),
            "for470Json": rel(FOR470_JSON),
            "for470Report": rel(FOR470_REPORT),
            "graph": rel(GRAPH_JSON),
            "intermediateOwnership": rel(OWNERSHIP_JSON),
        },
        "counters": {
            "requiredEvidenceItems": len(required),
            "presentEvidenceItems": present_count,
            "missingEvidenceItems": missing_count,
            "validatedNonPromotionalEvidenceItems": sum(1 for item in required if item["validatedNonPromotional"]),
            "nonPromotionalSignals": len(signals),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "requiredEvidence": required,
        "nonPromotionalSignals": signals,
        "nextRecommendedTicket": {
            "id": "M91-IF-3B-REF",
            "scope": "Produce row-specific ImageMakeWithFilterGM reference/provenance package and keep CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance evidence blocked until their artifacts exist.",
            "supportClaimAllowed": False,
            "promotionAllowedWithoutEvidence": False,
        },
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_imagemakewithfilter_evidence_intake.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-intake-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_evidence_intake.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterGraphOwnershipProof",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterEvidenceIntake",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(
        summary.get("status") in {
            "blocked-by-missing-row-specific-evidence",
            "partial-row-specific-evidence-present-non-promotional",
            "row-specific-evidence-present-non-promotional",
        },
        "summary status mismatch",
    )
    validate_row(summary.get("row", {}), "summary")
    counters = summary.get("counters")
    required = summary.get("requiredEvidence")
    ticket = summary.get("nextRecommendedTicket")
    require(isinstance(counters, dict), "summary counters must be an object")
    require(counters.get("requiredEvidenceItems") == len(REQUIRED_EVIDENCE), "required evidence count changed")
    require(0 <= counters.get("presentEvidenceItems") <= len(REQUIRED_EVIDENCE), "present evidence count out of range")
    require(counters.get("missingEvidenceItems") == len(REQUIRED_EVIDENCE) - counters.get("presentEvidenceItems"), "missing evidence count changed")
    require(counters.get("validatedNonPromotionalEvidenceItems") == counters.get("presentEvidenceItems"), "present evidence must validate as non-promotional")
    for key in ["newSupportClaims", "dashboardPromotions", "thresholdChanges"]:
        require(counters.get(key) == 0, f"counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "readiness must not move")
    require(isinstance(required, list) and len(required) == len(REQUIRED_EVIDENCE), "requiredEvidence list changed")
    require(all(item.get("promotional") is False for item in required if isinstance(item, dict)), "required evidence must remain non-promotional")
    require(all(item.get("validatedNonPromotional") == item.get("present") for item in required if isinstance(item, dict)), "present evidence must validate as non-promotional")
    require(isinstance(ticket, dict), "nextRecommendedTicket must be an object")
    require(ticket.get("supportClaimAllowed") is False, "next ticket must not allow support claims")
    require(ticket.get("promotionAllowedWithoutEvidence") is False, "next ticket must block promotion")
    require(summary.get("nonClaims") == NON_CLAIMS, "nonClaims contract changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def render_markdown(summary: dict[str, Any]) -> str:
    row = summary["row"]
    counters = summary["counters"]
    lines = [
        "# M91 ImageMakeWithFilterGM Evidence Intake",
        "",
        f"Status: {summary['status']}",
        "",
        "This report starts `M91-IF-3B` for `skia-gm-imagemakewithfilter`. It aggregates existing refusal and policy-only route evidence, and keeps support evaluation blocked until row-specific graph, ownership, reference, route, render, diff/stat, and performance artifacts exist.",
        "",
        "## Row",
        "",
        f"- Row ID: `{row['rowId']}`",
        f"- Source GM: `{row['sourceGm']}`",
        f"- Status: `{row['status']}`",
        f"- Support claim: `{row['supportClaim']}`",
        f"- Policy-only: `{row['policyOnly']}`",
        f"- Fallback: `{row['fallbackReason']}`",
        "",
        "## Counters",
        "",
    ]
    for key, value in counters.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "## Required Evidence", ""])
    for item in summary["requiredEvidence"]:
        lines.append(
            f"- `{item['kind']}`: `{item['status']}` at `{item['path']}`; "
            f"present=`{item['present']}` promotional=`{item['promotional']}`"
        )
    lines.extend(["", "## Non-Promotional Signals", ""])
    for item in summary["nonPromotionalSignals"]:
        observed = f" (`{item['observed']}`)" if "observed" in item else ""
        lines.append(f"- `{item['kind']}`{observed}: `{item['path']}`; promotional=`{item['promotional']}`")
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
            "## Non-Claims",
            "",
        ]
    )
    lines.extend(f"- {key}: `{value}`" for key, value in summary["nonClaims"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def main() -> int:
    try:
        validate_registry(load_json(REGISTRY_JSON))
        validate_candidate_readiness(load_json(CANDIDATE_READINESS_JSON))
        validate_route(load_json(ROUTE_CPU_JSON), "CPU")
        validate_route(load_json(ROUTE_GPU_JSON), "WebGPU")
        validate_for470(load_json(FOR470_JSON))
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary()
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_imagemakewithfilter_evidence_intake: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 ImageMakeWithFilterGM evidence intake validation passed: "
        f"missingEvidenceItems={reloaded['counters']['missingEvidenceItems']} "
        f"presentEvidenceItems={reloaded['counters']['presentEvidenceItems']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
