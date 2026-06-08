#!/usr/bin/env python3
"""Generate and validate M91 image-filter prepass dependency-gate proof."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
SELECTION_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-slice/selection.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-prepass-gate-proof"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"
SCENE_ARTIFACT_ROOT = ROOT / "reports/wgsl-pipeline/scenes/artifacts"

ROW_ID = "image-filter-crop-nonnull-prepass-required"
SUPPORTED_SIBLING = "crop-image-filter-nonnull-prepass"
FALLBACK = "image-filter.crop-input-nonnull-prepass-required"
CPU_ROUTE = "cpu.image-filter.crop-nonnull-reference"
GPU_ROUTE = "webgpu.image-filter.refuse.prepass-required"
GPU_STRATEGY = "webgpu.image-filter.refuse"
GPU_PIPELINE_KEY = "imageFilter=Crop(input=nonNull),prePass=required,selectedM38Shape=false"
POLICY_REPORT = "reports/wgsl-pipeline/2026-05-28-m38-image-filter-policy-update.md"
IMPLEMENTATION_REPORT = "reports/wgsl-pipeline/2026-05-28-m38-crop-nonnull-prepass-implementation.md"
GENERATED_EVIDENCE_REPORT = "reports/wgsl-pipeline/2026-05-28-m41-crop-image-filter-generated-evidence.md"
EVIDENCE_REPORT = "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md"

NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "requiredSmokeCandidateAllowed": False,
    "generalImageFilterDagCompiler": False,
    "cpuReadbackFallbackAdded": False,
    "arbitraryLayerPrepass": False,
    "recursiveCropPrepass": False,
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


def registry_rows(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, "registry row missing rowId")
        result[row_id] = row
    return result


def require_selection(selection: dict[str, Any]) -> None:
    require(selection.get("classification") == "image-filter-backlog-selection-no-new-rendering-support", "M91 selection classification mismatch")
    tickets = selection.get("nextTickets")
    require(isinstance(tickets, list), "M91 selection nextTickets must be a list")
    ticket = next((item for item in tickets if isinstance(item, dict) and item.get("id") == "M91-IF-2"), None)
    require(isinstance(ticket, dict), "M91 selection missing M91-IF-2")
    require(ticket.get("type") == "dependency-gate-proof", "M91-IF-2 must remain dependency-gate-proof")
    require(ticket.get("supportClaimAllowed") is False, "M91-IF-2 must not allow support claims")
    require(ticket.get("rows") == [ROW_ID], "M91-IF-2 row list changed")


def validate_registry_row(row: dict[str, Any]) -> dict[str, Any]:
    require(row.get("family") == "image-filter", f"{ROW_ID}: family changed")
    require(row.get("status") == "expected-unsupported", f"{ROW_ID}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{ROW_ID}: supportClaim must remain false")
    require(row.get("policyOnly") is False, f"{ROW_ID}: generated refusal must not become policy-only")
    require(row.get("fallbackReason") == FALLBACK, f"{ROW_ID}: fallback changed")
    require(row.get("routeCpu") == "pass", f"{ROW_ID}: CPU route must remain pass oracle")
    require(row.get("routeGpu") == "expected-unsupported", f"{ROW_ID}: GPU route must remain expected-unsupported")
    require(row.get("referenceKind") == "cpu-oracle", f"{ROW_ID}: referenceKind must remain cpu-oracle")
    require(row.get("nextTicketType") == "implementation", f"{ROW_ID}: nextTicketType changed")
    links = row.get("imageFilterPrepassGateLinks")
    require(isinstance(links, list) and len(links) == 1, f"{ROW_ID}: expected exactly one imageFilterPrepassGateLinks entry")
    link = links[0]
    require(isinstance(link, dict), f"{ROW_ID}: imageFilterPrepassGateLinks entry must be an object")
    require(link.get("classification") == "image-filter-prepass-gated-expected-unsupported-no-support-claim", f"{ROW_ID}: gate classification changed")
    require(link.get("fallbackReason") == FALLBACK, f"{ROW_ID}: gate fallback changed")
    require(link.get("sourceShape") == "Crop(input=nonNull)", f"{ROW_ID}: source shape changed")
    require(link.get("supportedSiblingScene") == SUPPORTED_SIBLING, f"{ROW_ID}: supported sibling changed")
    require(link.get("cpuRoute") == CPU_ROUTE, f"{ROW_ID}: CPU route link changed")
    require(link.get("gpuRoute") == GPU_ROUTE, f"{ROW_ID}: GPU route link changed")
    require(link.get("gpuCoverageStrategy") == GPU_STRATEGY, f"{ROW_ID}: GPU coverage strategy changed")
    require(link.get("gpuPipelineKey") == GPU_PIPELINE_KEY, f"{ROW_ID}: GPU pipeline key changed")
    require(link.get("policyReport") == POLICY_REPORT, f"{ROW_ID}: policy report link changed")
    require(link.get("implementationReport") == IMPLEMENTATION_REPORT, f"{ROW_ID}: implementation report link changed")
    require(link.get("generatedEvidenceReport") == GENERATED_EVIDENCE_REPORT, f"{ROW_ID}: generated evidence report link changed")
    require(link.get("evidenceReport") == EVIDENCE_REPORT, f"{ROW_ID}: evidence report link changed")
    require(link.get("requiredSmokeCandidateAllowed") is False, f"{ROW_ID}: required smoke candidate must stay disallowed")
    require(link.get("generalDagCompilerAdded") is False, f"{ROW_ID}: general DAG compiler must remain absent")
    require(link.get("cpuReadbackFallbackAdded") is False, f"{ROW_ID}: CPU/readback fallback must remain absent")
    require(link.get("supportScoreIncreased") is False, f"{ROW_ID}: support score must not increase")
    for report in [POLICY_REPORT, IMPLEMENTATION_REPORT, GENERATED_EVIDENCE_REPORT, EVIDENCE_REPORT]:
        require((ROOT / report).is_file(), f"{ROW_ID}: missing report {report}")
    return link


def validate_refusal_artifacts(link: dict[str, Any]) -> dict[str, Any]:
    artifact_root = SCENE_ARTIFACT_ROOT / ROW_ID
    cpu_route = load_json(artifact_root / "route-cpu.json")
    gpu_route = load_json(artifact_root / "route-gpu.json")
    stats = load_json(artifact_root / "stats.json")
    require(cpu_route.get("sceneId") == ROW_ID, "CPU refusal route sceneId mismatch")
    require(cpu_route.get("backend") == "CPU", "CPU refusal route backend changed")
    require(cpu_route.get("status") == "pass", "CPU refusal route must remain pass")
    require(cpu_route.get("selectedRoute") == CPU_ROUTE, "CPU refusal route changed")
    require(cpu_route.get("fallbackReason") == "none", "CPU refusal fallback must remain none")
    require(cpu_route.get("sourceReport") == EVIDENCE_REPORT, "CPU refusal source report changed")
    require(gpu_route.get("sceneId") == ROW_ID, "GPU refusal route sceneId mismatch")
    require(gpu_route.get("backend") == "WebGPU", "GPU refusal route backend changed")
    require(gpu_route.get("status") == "expected-unsupported", "GPU refusal route must remain expected-unsupported")
    require(gpu_route.get("coverageStrategy") == GPU_STRATEGY, "GPU refusal coverage strategy changed")
    require(gpu_route.get("pipelineKey") == GPU_PIPELINE_KEY, "GPU refusal pipeline key changed")
    require(gpu_route.get("fallbackReason") == FALLBACK, "GPU refusal fallback changed")
    require(gpu_route.get("sourceReport") == EVIDENCE_REPORT, "GPU refusal source report changed")
    require(link.get("routeCpuDiagnostic") == rel(artifact_root / "route-cpu.json"), "registry CPU diagnostic link changed")
    require(link.get("routeGpuDiagnostic") == rel(artifact_root / "route-gpu.json"), "registry GPU diagnostic link changed")
    require(stats.get("backend") == "inventory", "refusal stats backend changed")
    require(stats.get("status") == "expected-unsupported", "refusal stats status changed")
    require(stats.get("fallbackReason") == FALLBACK, "refusal stats fallback changed")
    require(stats.get("threshold") == 0.0, "refusal stats threshold changed")
    require(stats.get("pixels") == 0, "refusal stats must remain inventory-only")
    return {
        "routeCpuDiagnostic": rel(artifact_root / "route-cpu.json"),
        "routeGpuDiagnostic": rel(artifact_root / "route-gpu.json"),
        "inventoryStats": rel(artifact_root / "stats.json"),
        "cpuStatus": "pass",
        "gpuStatus": "expected-unsupported",
    }


def validate_supported_sibling() -> dict[str, Any]:
    artifact_root = SCENE_ARTIFACT_ROOT / SUPPORTED_SIBLING
    route_gpu = load_json(artifact_root / "route-gpu.json")
    route_prepass = load_json(artifact_root / "route-prepass.json")
    stats = load_json(artifact_root / "stats.json")
    require(route_gpu.get("sceneId") == SUPPORTED_SIBLING, "supported sibling GPU route sceneId mismatch")
    require(route_gpu.get("status") == "pass", "supported sibling GPU route must remain pass")
    require(route_gpu.get("fallbackReason") == "none", "supported sibling GPU fallback must remain none")
    require(route_gpu.get("coverageStrategy") == "webgpu.image-filter.crop-nonnull-offset-prepass", "supported sibling route changed")
    require(route_prepass.get("backend") == "WebGPU", "supported sibling prepass backend changed")
    require(route_prepass.get("fallbackReason") == "none", "supported sibling prepass fallback must remain none")
    require(route_prepass.get("unsupportedReasonRemoved") == FALLBACK, "supported sibling must document removed fallback")
    require(route_prepass.get("destination") == "SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch", "supported sibling prepass destination changed")
    require(stats.get("sceneId") == SUPPORTED_SIBLING, "supported sibling stats sceneId mismatch")
    require(stats.get("backend") == "WebGPU", "supported sibling stats backend changed")
    require(stats.get("unsupportedImageFilterAfter") == 0, "supported sibling unsupported count changed")
    return {
        "sceneId": SUPPORTED_SIBLING,
        "routeGpuDiagnostic": rel(artifact_root / "route-gpu.json"),
        "routePrepassDiagnostic": rel(artifact_root / "route-prepass.json"),
        "stats": rel(artifact_root / "stats.json"),
        "boundary": "M38 bounded Crop(kDecal, input=Offset(null)) prepass only; does not generalize to Crop(input=nonNull) graph shapes.",
    }


def build_summary(registry: dict[str, Any], selection: dict[str, Any]) -> dict[str, Any]:
    require_selection(selection)
    rows = registry_rows(registry)
    row = rows.get(ROW_ID)
    require(isinstance(row, dict), f"missing registry row: {ROW_ID}")
    link = validate_registry_row(row)
    refusal_artifacts = validate_refusal_artifacts(link)
    supported_sibling = validate_supported_sibling()
    proof = {
        "rowId": ROW_ID,
        "status": "expected-unsupported",
        "supportClaim": False,
        "policyOnly": False,
        "fallbackReason": FALLBACK,
        "sourceShape": "Crop(input=nonNull)",
        "supportedSiblingScene": SUPPORTED_SIBLING,
        "routeCpu": "pass",
        "routeGpu": "expected-unsupported",
        "cpuRoute": CPU_ROUTE,
        "gpuRoute": GPU_ROUTE,
        "gpuCoverageStrategy": GPU_STRATEGY,
        "gpuPipelineKey": GPU_PIPELINE_KEY,
        "requiredSmokeCandidateAllowed": False,
        "generalDagCompilerAdded": False,
        "cpuReadbackFallbackAdded": False,
        "supportScoreIncreased": False,
        "policyReport": POLICY_REPORT,
        "implementationReport": IMPLEMENTATION_REPORT,
        "generatedEvidenceReport": GENERATED_EVIDENCE_REPORT,
        "evidenceReport": EVIDENCE_REPORT,
        "refusalArtifacts": refusal_artifacts,
        "supportedSiblingBoundary": supported_sibling,
    }
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_prepass_gate_proof.py",
        "milestone": "M91",
        "ticket": "M91-IF-2",
        "classification": "image-filter-prepass-gate-proof-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "selection": rel(SELECTION_JSON),
            "imageFilterSpec": ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md",
            "refusalArtifactRoot": rel(SCENE_ARTIFACT_ROOT / ROW_ID),
            "supportedSiblingArtifactRoot": rel(SCENE_ARTIFACT_ROOT / SUPPORTED_SIBLING),
        },
        "counters": {
            "proofRows": 1,
            "prepassGateRows": 1,
            "gpuExpectedUnsupportedRows": 1,
            "cpuOraclePassRows": 1,
            "requiredSmokeCandidatesAdded": 0,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "proofs": [proof],
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_prepass_gate_proof.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterPrepassGateProof",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    proof = summary["proofs"][0]
    lines = [
        "# M91 Image Filter Prepass Gate Proof",
        "",
        "Status: generated evidence",
        "",
        "This report exposes the `M91-IF-2` dependency gate for `Crop(input=nonNull)` image-filter graphs. It keeps the out-of-scope row expected-unsupported while pointing to the bounded M38 sibling that is already supported.",
        "",
        "## Counters",
        "",
        f"- Proof rows: `{counters['proofRows']}`",
        f"- Prepass gate rows: `{counters['prepassGateRows']}`",
        f"- GPU expected-unsupported rows: `{counters['gpuExpectedUnsupportedRows']}`",
        f"- CPU oracle pass rows: `{counters['cpuOraclePassRows']}`",
        f"- Required smoke candidates added: `{counters['requiredSmokeCandidatesAdded']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
        "",
        "## Gate Proof",
        "",
        f"- Row: `{proof['rowId']}`",
        f"- Status: `{proof['status']}`",
        f"- Fallback: `{proof['fallbackReason']}`",
        f"- Source shape: `{proof['sourceShape']}`",
        f"- Supported sibling: `{proof['supportedSiblingScene']}`",
        f"- CPU route: `{proof['cpuRoute']}` (`{proof['routeCpu']}`)",
        f"- GPU route: `{proof['gpuRoute']}` (`{proof['routeGpu']}`)",
        f"- GPU pipeline key: `{proof['gpuPipelineKey']}`",
        f"- Required smoke candidate allowed: `{proof['requiredSmokeCandidateAllowed']}`",
        f"- General DAG compiler added: `{proof['generalDagCompilerAdded']}`",
        f"- CPU/readback fallback added: `{proof['cpuReadbackFallbackAdded']}`",
        f"- Support claim: `{proof['supportClaim']}`",
        f"- Refusal CPU diagnostic: `{proof['refusalArtifacts']['routeCpuDiagnostic']}`",
        f"- Refusal GPU diagnostic: `{proof['refusalArtifacts']['routeGpuDiagnostic']}`",
        f"- Refusal inventory stats: `{proof['refusalArtifacts']['inventoryStats']}`",
        "",
        "## Supported Sibling Boundary",
        "",
        f"- Sibling scene: `{proof['supportedSiblingBoundary']['sceneId']}`",
        f"- GPU diagnostic: `{proof['supportedSiblingBoundary']['routeGpuDiagnostic']}`",
        f"- Prepass diagnostic: `{proof['supportedSiblingBoundary']['routePrepassDiagnostic']}`",
        f"- Stats: `{proof['supportedSiblingBoundary']['stats']}`",
        f"- Boundary: {proof['supportedSiblingBoundary']['boundary']}",
        "",
        "## Support Guard",
        "",
    ]
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "image-filter-prepass-gate-proof-no-new-rendering-support", "classification mismatch")
    counters = summary.get("counters")
    proofs = summary.get("proofs")
    guard = summary.get("supportGuard")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("proofRows") == 1, "proof row count changed")
    require(counters.get("prepassGateRows") == 1, "prepass gate count changed")
    require(counters.get("gpuExpectedUnsupportedRows") == 1, "GPU expected-unsupported count changed")
    require(counters.get("cpuOraclePassRows") == 1, "CPU oracle pass count changed")
    require(counters.get("requiredSmokeCandidatesAdded") == 0, "required smoke candidate count changed")
    require(counters.get("newSupportClaims") == 0, "new support claims changed")
    require(counters.get("readinessDelta") == 0.0, "readiness changed")
    require(counters.get("dashboardPromotions") == 0, "dashboard promotions changed")
    require(counters.get("thresholdChanges") == 0, "threshold changes changed")
    require(isinstance(proofs, list) and len(proofs) == 1, "proofs must contain one row")
    require(isinstance(guard, dict), "supportGuard must be an object")
    for key, value in guard.items():
        require(value is False, f"supportGuard.{key} must remain false")
    proof = proofs[0]
    require(isinstance(proof, dict), "proof entry must be an object")
    require(proof.get("rowId") == ROW_ID, "proof row changed")
    require(proof.get("status") == "expected-unsupported", "proof status changed")
    require(proof.get("supportClaim") is False, "support claim changed")
    require(proof.get("fallbackReason") == FALLBACK, "fallback changed")
    require(proof.get("routeCpu") == "pass", "CPU route status changed")
    require(proof.get("routeGpu") == "expected-unsupported", "GPU route status changed")
    require(proof.get("requiredSmokeCandidateAllowed") is False, "required smoke candidate changed")
    require(proof.get("generalDagCompilerAdded") is False, "general DAG compiler changed")
    require(proof.get("cpuReadbackFallbackAdded") is False, "CPU/readback fallback changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        selection = load_json(SELECTION_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(registry, selection)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        validate_summary(load_json(SUMMARY_JSON))
    except AssertionError as error:
        print(f"m91_image_filter_prepass_gate_proof: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 image-filter prepass gate proof validation passed: proofRows=1 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
