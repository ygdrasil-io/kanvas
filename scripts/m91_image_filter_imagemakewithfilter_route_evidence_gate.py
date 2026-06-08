#!/usr/bin/env python3
"""Generate and validate M91-IF-3B-ROUTE ImageMakeWithFilterGM route evidence gate."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
INTAKE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-evidence-intake/summary.json"
REFERENCE_PLAN_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-plan.json"
PROVENANCE_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-reference-provenance-gate/summary.json"
ROUTE_DIAG_CPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json"
ROUTE_DIAG_GPU_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-route-evidence-gate"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-imagemakewithfilter"
SOURCE_GM = "ImageMakeWithFilterGM"
FALLBACK = "image-filter.imagemakewithfilter.row-specific-artifacts-required"
CLASSIFICATION = "image-filter-imagemakewithfilter-route-evidence-gate-no-new-rendering-support"
EXPECTED_CPU_ROUTE = "cpu.image-filter.image-make-with-filter.expected-unsupported"
EXPECTED_GPU_ROUTE = "webgpu.image-filter.image-make-with-filter.expected-unsupported"
EXPECTED_POLICY = (
    "Future support requires a row-specific graph dump, intermediate texture ownership, "
    "Skia/reference artifacts, CPU/GPU route evidence, render artifacts, diff/stat artifacts, "
    "performance impact, and fallbackReason=none without adding a generic image-filter DAG compiler, "
    "CPU/readback fallback, threshold change, scoring change, or hidden fallback-policy change."
)
EXPECTED_GRAPH_FACTS = [
    "source image lifetime",
    "image-filter graph ownership",
    "intermediate texture ownership",
    "row-specific reference/render/diff artifacts",
]
ROUTE_NON_CLAIMS = {
    "arbitraryLayerPrepass": False,
    "belowThresholdCountedAsProductionGap": False,
    "broadImageFilterDAGSupport": False,
    "cpuReadbackFallbackAdded": False,
    "dynamicSkSLCompiler": False,
    "dynamicSkSLIR": False,
    "dynamicSkSLVM": False,
    "ganeshPort": False,
    "genericImageFilterDagCompiler": False,
    "graphitePort": False,
    "policyOnlyPromoted": False,
    "supportClaimAdded": False,
    "thresholdChanged": False,
}
NON_CLAIMS = {
    "supportClaimAdded": False,
    "routeEvidenceArtifactAdded": False,
    "fallbackReasonNoneClaimed": False,
    "policyOnlyPromoted": False,
    "referenceArtifactAdded": False,
    "referenceProvenanceAdded": False,
    "makeWithFilterExecuted": False,
    "renderArtifactsAdded": False,
    "diffStatsAdded": False,
    "performanceGatePromoted": False,
    "readinessMoved": False,
    "dashboardPromoted": False,
    "thresholdChanged": False,
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
FORBIDDEN_SUPPORT_OUTPUTS = [
    ARTIFACT_DIR / "skia.png",
    ARTIFACT_DIR / "reference-provenance.json",
    ARTIFACT_DIR / "route-cpu.json",
    ARTIFACT_DIR / "route-gpu.json",
    ARTIFACT_DIR / "cpu.png",
    ARTIFACT_DIR / "gpu.png",
    ARTIFACT_DIR / "cpu-diff.png",
    ARTIFACT_DIR / "gpu-diff.png",
    ARTIFACT_DIR / "stats.json",
    ARTIFACT_DIR / "performance.json",
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


def validate_row(row: dict[str, Any], source: str) -> None:
    require(row.get("rowId") == ROW_ID, f"{source}: rowId changed")
    require(row.get("sourceGm") == SOURCE_GM, f"{source}: sourceGm changed")
    require(row.get("status") == "expected-unsupported", f"{source}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{source}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{source}: policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, f"{source}: fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", f"{source}: CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", f"{source}: GPU route changed")


def validate_route_diag(route: dict[str, Any], backend: str) -> None:
    expected_route = EXPECTED_CPU_ROUTE if backend == "CPU" else EXPECTED_GPU_ROUTE
    require(route.get("schemaVersion") == 1, f"{backend} route diagnostic schema changed")
    require(route.get("milestone") == "M91", f"{backend} route diagnostic milestone changed")
    require(route.get("ticket") == "M91-IF-1", f"{backend} route diagnostic ticket changed")
    require(route.get("rowId") == ROW_ID, f"{backend} route diagnostic rowId changed")
    require(route.get("sourceGm") == SOURCE_GM, f"{backend} route diagnostic sourceGm changed")
    require(route.get("backend") == backend, f"{backend} route diagnostic backend changed")
    require(route.get("status") == "expected-unsupported", f"{backend} route diagnostic must remain expected-unsupported")
    require(route.get("route") == expected_route, f"{backend} route diagnostic route changed")
    require(route.get("fallbackReason") == FALLBACK, f"{backend} route diagnostic fallback changed")
    require(route.get("fallbackReason") != "none", f"{backend} route diagnostic must not claim fallbackReason=none")
    require(route.get("policyOnlyArtifact") is True, f"{backend} route diagnostic must remain policy-only")
    require(route.get("graphFactsRequired") == EXPECTED_GRAPH_FACTS, f"{backend} route graph facts changed")
    require(route.get("policy") == EXPECTED_POLICY, f"{backend} route policy changed")
    require(route.get("nonClaims") == ROUTE_NON_CLAIMS, f"{backend} route nonClaims changed")


def validate_intake(intake: dict[str, Any]) -> None:
    require(intake.get("classification") == "image-filter-imagemakewithfilter-evidence-intake-no-new-rendering-support", "intake classification changed")
    row = intake.get("row")
    counters = intake.get("counters")
    required = intake.get("requiredEvidence")
    require(isinstance(row, dict), "intake row must be an object")
    validate_row(row, "intake")
    require(isinstance(counters, dict), "intake counters must be an object")
    require(counters.get("presentEvidenceItems") == 2, "intake present evidence count changed")
    require(counters.get("missingEvidenceItems") == 9, "intake missing evidence count changed")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("thresholdChanges") == 0, "intake must not change thresholds")
    require(isinstance(required, list), "intake requiredEvidence must be a list")
    by_path = {item.get("path"): item for item in required if isinstance(item, dict)}
    require(by_path.get(rel(ARTIFACT_DIR / "route-cpu.json"), {}).get("status") == "missing", "CPU route evidence must remain missing")
    require(by_path.get(rel(ARTIFACT_DIR / "route-gpu.json"), {}).get("status") == "missing", "WebGPU route evidence must remain missing")


def validate_reference_plan(plan: dict[str, Any]) -> None:
    require(plan.get("classification") == "image-filter-imagemakewithfilter-reference-package-plan-no-new-rendering-support", "reference plan classification changed")
    require(plan.get("status") == "reference-package-planned-no-reference-artifact-generated", "reference plan status changed")
    row = plan.get("row")
    counters = plan.get("counters")
    require(isinstance(row, dict), "reference plan row must be an object")
    validate_row(row, "reference plan")
    require(isinstance(counters, dict), "reference plan counters must be an object")
    require(counters.get("referenceArtifactsAdded") == 0, "reference plan must not add reference artifacts")
    require(counters.get("fallbackReasonNoneRoutesAdded") == 0, "reference plan must not add fallbackReason=none routes")
    require(counters.get("newSupportClaims") == 0, "reference plan must not add support claims")
    require(counters.get("missingEvidenceItemsStill") == 9, "reference plan missing evidence count changed")


def validate_provenance_gate(gate: dict[str, Any]) -> None:
    require(gate.get("classification") == "image-filter-imagemakewithfilter-reference-provenance-gate-no-new-rendering-support", "provenance gate classification changed")
    require(gate.get("status") == "blocked-until-row-specific-reference-provenance-exists", "provenance gate status changed")
    row = gate.get("row")
    counters = gate.get("counters")
    require(isinstance(row, dict), "provenance gate row must be an object")
    validate_row(row, "provenance gate")
    require(isinstance(counters, dict), "provenance gate counters must be an object")
    require(counters.get("referenceProvenanceArtifactsAdded") == 0, "provenance gate must not add provenance artifacts")
    require(counters.get("fallbackReasonNoneRoutesAdded") == 0, "provenance gate must not add fallbackReason=none routes")
    require(counters.get("newSupportClaims") == 0, "provenance gate must not add support claims")
    require(counters.get("missingEvidenceItemsStill") == 9, "provenance gate missing evidence count changed")


def validate_forbidden_outputs_absent() -> list[dict[str, Any]]:
    states: list[dict[str, Any]] = []
    for path in FORBIDDEN_SUPPORT_OUTPUTS:
        states.append({"path": rel(path), "present": path.exists(), "requiredAbsentForThisGate": True})
        require(not path.exists(), f"support output must remain absent in this route gate: {rel(path)}")
    return states


def build_summary(route_cpu: dict[str, Any], route_gpu: dict[str, Any]) -> dict[str, Any]:
    absent_outputs = validate_forbidden_outputs_absent()
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_imagemakewithfilter_route_evidence_gate.py",
        "milestone": "M91",
        "ticket": "M91-IF-3B-ROUTE",
        "classification": CLASSIFICATION,
        "status": "blocked-until-row-specific-route-evidence-can-use-fallback-none",
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
            "evidenceIntake": rel(INTAKE_JSON),
            "referencePlan": rel(REFERENCE_PLAN_JSON),
            "referenceProvenanceGate": rel(PROVENANCE_GATE_JSON),
            "routeDiagnosticCpu": rel(ROUTE_DIAG_CPU_JSON),
            "routeDiagnosticGpu": rel(ROUTE_DIAG_GPU_JSON),
        },
        "routeDiagnosticsBoundary": {
            "cpu": {
                "path": rel(ROUTE_DIAG_CPU_JSON),
                "backend": route_cpu.get("backend"),
                "status": route_cpu.get("status"),
                "route": route_cpu.get("route"),
                "fallbackReason": route_cpu.get("fallbackReason"),
                "policyOnlyArtifact": route_cpu.get("policyOnlyArtifact"),
                "canSatisfySceneRouteEvidence": False,
            },
            "gpu": {
                "path": rel(ROUTE_DIAG_GPU_JSON),
                "backend": route_gpu.get("backend"),
                "status": route_gpu.get("status"),
                "route": route_gpu.get("route"),
                "fallbackReason": route_gpu.get("fallbackReason"),
                "policyOnlyArtifact": route_gpu.get("policyOnlyArtifact"),
                "canSatisfySceneRouteEvidence": False,
            },
            "reason": "Existing M91 route diagnostics are policy-only refusal records. Scene route evidence requires row-specific route-cpu.json and route-gpu.json with fallbackReason=none plus reference, render, diff/stat, and performance evidence.",
        },
        "requiredAbsentOutputs": absent_outputs,
        "promotionGate": {
            "fallbackReasonNoneAllowedNow": False,
            "cpuSceneRouteEvidenceAddedNow": False,
            "webgpuSceneRouteEvidenceAddedNow": False,
            "routeDiagnosticsCanStandInForSceneRoutes": False,
            "readyForSupportEvaluation": False,
        },
        "counters": {
            "routeEvidenceGates": 1,
            "routeDiagnosticInputs": 2,
            "sceneRouteEvidenceArtifactsAdded": 0,
            "fallbackReasonNoneRoutesAdded": 0,
            "referenceArtifactsAdded": 0,
            "referenceProvenanceArtifactsAdded": 0,
            "renderArtifactsAdded": 0,
            "diffStatsAdded": 0,
            "performanceArtifactsAdded": 0,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
            "presentEvidenceItemsStill": 2,
            "missingEvidenceItemsStill": 9,
        },
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_imagemakewithfilter_route_evidence_gate.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-route-evidence-gate-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_route_evidence_gate.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterRouteEvidenceGate",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(summary.get("status") == "blocked-until-row-specific-route-evidence-can-use-fallback-none", "summary status mismatch")
    row = summary.get("row")
    counters = summary.get("counters")
    boundary = summary.get("routeDiagnosticsBoundary")
    gate = summary.get("promotionGate")
    absent_outputs = summary.get("requiredAbsentOutputs")
    require(isinstance(row, dict), "summary row must be an object")
    validate_row(row, "summary")
    require(isinstance(boundary, dict), "routeDiagnosticsBoundary must be an object")
    require(boundary.get("cpu", {}).get("canSatisfySceneRouteEvidence") is False, "CPU diagnostic must not satisfy scene route evidence")
    require(boundary.get("gpu", {}).get("canSatisfySceneRouteEvidence") is False, "GPU diagnostic must not satisfy scene route evidence")
    require(boundary.get("cpu", {}).get("fallbackReason") == FALLBACK, "CPU boundary fallback changed")
    require(boundary.get("gpu", {}).get("fallbackReason") == FALLBACK, "GPU boundary fallback changed")
    require(isinstance(gate, dict), "promotionGate must be an object")
    require(gate.get("fallbackReasonNoneAllowedNow") is False, "fallbackReason=none must not be allowed now")
    require(gate.get("cpuSceneRouteEvidenceAddedNow") is False, "CPU scene route must not be added now")
    require(gate.get("webgpuSceneRouteEvidenceAddedNow") is False, "WebGPU scene route must not be added now")
    require(gate.get("routeDiagnosticsCanStandInForSceneRoutes") is False, "route diagnostics must not stand in for scene routes")
    require(gate.get("readyForSupportEvaluation") is False, "route gate must not be ready for support evaluation")
    require(isinstance(absent_outputs, list) and len(absent_outputs) == len(FORBIDDEN_SUPPORT_OUTPUTS), "absent output list changed")
    require(all(item.get("present") is False for item in absent_outputs if isinstance(item, dict)), "support outputs must remain absent")
    require(isinstance(counters, dict), "summary counters must be an object")
    for key in [
        "sceneRouteEvidenceArtifactsAdded",
        "fallbackReasonNoneRoutesAdded",
        "referenceArtifactsAdded",
        "referenceProvenanceArtifactsAdded",
        "renderArtifactsAdded",
        "diffStatsAdded",
        "performanceArtifactsAdded",
        "newSupportClaims",
        "dashboardPromotions",
        "thresholdChanges",
    ]:
        require(counters.get(key) == 0, f"counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "readinessDelta must remain zero")
    require(counters.get("presentEvidenceItemsStill") == 2, "present evidence count must remain 2")
    require(counters.get("missingEvidenceItemsStill") == 9, "missing evidence count must remain 9")
    require(summary.get("nonClaims") == NON_CLAIMS, "nonClaims contract changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def render_markdown(summary: dict[str, Any]) -> str:
    row = summary["row"]
    boundary = summary["routeDiagnosticsBoundary"]
    counters = summary["counters"]
    lines = [
        "# M91 ImageMakeWithFilterGM Route Evidence Gate",
        "",
        f"Status: {summary['status']}",
        "",
        "This report keeps `M91-IF-3B-ROUTE` non-promotional. Existing CPU/WebGPU route diagnostics remain policy-only expected-unsupported records and cannot stand in for scene route evidence with `fallbackReason=none`.",
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
        "## Route Diagnostics Boundary",
        "",
        f"- CPU diagnostic: `{boundary['cpu']['path']}` status=`{boundary['cpu']['status']}` fallback=`{boundary['cpu']['fallbackReason']}` scene-evidence=`{boundary['cpu']['canSatisfySceneRouteEvidence']}`",
        f"- WebGPU diagnostic: `{boundary['gpu']['path']}` status=`{boundary['gpu']['status']}` fallback=`{boundary['gpu']['fallbackReason']}` scene-evidence=`{boundary['gpu']['canSatisfySceneRouteEvidence']}`",
        f"- Reason: {boundary['reason']}",
        "",
        "## Required Absent Outputs",
        "",
    ]
    for item in summary["requiredAbsentOutputs"]:
        lines.append(f"- `{item['path']}`: present=`{item['present']}`")
    lines.extend(["", "## Counters", ""])
    for key, value in counters.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "## Non-Claims", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in summary["nonClaims"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def main() -> int:
    try:
        intake = load_json(INTAKE_JSON)
        reference_plan = load_json(REFERENCE_PLAN_JSON)
        provenance_gate = load_json(PROVENANCE_GATE_JSON)
        route_cpu = load_json(ROUTE_DIAG_CPU_JSON)
        route_gpu = load_json(ROUTE_DIAG_GPU_JSON)
        validate_intake(intake)
        validate_reference_plan(reference_plan)
        validate_provenance_gate(provenance_gate)
        validate_route_diag(route_cpu, "CPU")
        validate_route_diag(route_gpu, "WebGPU")
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(route_cpu, route_gpu)
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_imagemakewithfilter_route_evidence_gate: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 ImageMakeWithFilterGM route evidence gate validation passed: "
        f"sceneRouteEvidenceArtifactsAdded={reloaded['counters']['sceneRouteEvidenceArtifactsAdded']} "
        f"fallbackReasonNoneRoutesAdded={reloaded['counters']['fallbackReasonNoneRoutesAdded']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
