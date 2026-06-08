#!/usr/bin/env python3
"""Generate and validate M91-IF-3A-RENDER OffsetImageFilterGM render/diff/perf gate."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
INTAKE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-evidence-intake/summary.json"
REFERENCE_PLAN_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-plan.json"
PROVENANCE_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-reference-provenance-gate/summary.json"
ROUTE_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-route-evidence-gate/summary.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-render-diff-perf-gate"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-offsetimagefilter"
SOURCE_GM = "OffsetImageFilterGM"
FALLBACK = "image-filter.offset.row-specific-artifacts-required"
CLASSIFICATION = "image-filter-offset-render-diff-perf-gate-no-new-rendering-support"

RENDER_DIFF_PERF_OUTPUTS = [
    {
        "kind": "CPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "WebGPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "WebGPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU/GPU diff/stat artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "performance impact evidence",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json",
        "requiredBeforePromotion": True,
    },
]
EXPECTED_RENDER_DIFF_PERF_CONTRACT = [
    ("CPU render artifact", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png"),
    ("WebGPU render artifact", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png"),
    ("CPU diff artifact", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png"),
    ("WebGPU diff artifact", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png"),
    ("CPU/GPU diff/stat artifact", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json"),
    ("performance impact evidence", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json"),
]
EXPECTED_INTAKE_RENDER_DIFF_PERF_CONTRACT = [
    ("CPU/GPU render artifacts", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png"),
    ("CPU/GPU render artifacts", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png"),
    ("CPU/GPU diff/stat artifacts", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png"),
    ("CPU/GPU diff/stat artifacts", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png"),
    ("CPU/GPU diff/stat artifacts", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json"),
    ("performance impact evidence", "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json"),
]
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
NON_CLAIMS = {
    "supportClaimAdded": False,
    "renderArtifactsAdded": False,
    "diffStatsAdded": False,
    "performanceArtifactsAdded": False,
    "routeEvidenceArtifactAdded": False,
    "fallbackReasonNoneClaimed": False,
    "referenceArtifactAdded": False,
    "referenceProvenanceAdded": False,
    "historicalReferencePromoted": False,
    "policyOnlyPromoted": False,
    "performanceGatePromoted": False,
    "readinessMoved": False,
    "dashboardPromoted": False,
    "thresholdChanged": False,
    "belowThresholdCountedAsProductionGap": False,
    "genericImageFilterDagCompiler": False,
    "cpuReadbackFallbackAdded": False,
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


def validate_intake(intake: dict[str, Any]) -> None:
    require(intake.get("classification") == "image-filter-offset-evidence-intake-no-new-rendering-support", "intake classification changed")
    row = intake.get("row")
    counters = intake.get("counters")
    required = intake.get("requiredEvidence")
    require(isinstance(row, dict), "intake row must be an object")
    validate_row(row, "intake")
    require(isinstance(counters, dict), "intake counters must be an object")
    require(counters.get("presentEvidenceItems") == 2, "intake present evidence count changed")
    require(counters.get("missingEvidenceItems") == 9, "intake missing evidence count changed")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake must not move readiness")
    require(counters.get("thresholdChanges") == 0, "intake must not change thresholds")
    require(isinstance(required, list), "intake requiredEvidence must be a list")
    by_path = {item.get("path"): item for item in required if isinstance(item, dict)}
    for kind, path in EXPECTED_INTAKE_RENDER_DIFF_PERF_CONTRACT:
        evidence = by_path.get(path)
        require(isinstance(evidence, dict), f"intake missing required evidence row: {path}")
        require(evidence.get("kind") == kind, f"{path}: intake kind changed")
        require(evidence.get("status") == "missing", f"{path} must remain missing")
        require(evidence.get("present") is False, f"{path} must remain absent")


def validate_upstream_gate(
    gate: dict[str, Any],
    *,
    classification: str,
    status: str,
    source: str,
    zero_counters: list[str],
) -> None:
    require(gate.get("classification") == classification, f"{source}: classification changed")
    require(gate.get("status") == status, f"{source}: status changed")
    row = gate.get("row")
    counters = gate.get("counters")
    require(isinstance(row, dict), f"{source}: row must be an object")
    validate_row(row, source)
    require(isinstance(counters, dict), f"{source}: counters must be an object")
    for key in zero_counters:
        require(counters.get(key) == 0, f"{source}: counter {key} must remain zero")
    require(counters.get("newSupportClaims") == 0, f"{source}: must not add support claims")
    require(counters.get("readinessDelta") == 0.0, f"{source}: must not move readiness")
    require(counters.get("missingEvidenceItemsStill") == 9, f"{source}: missing evidence count changed")


def validate_reference_plan(plan: dict[str, Any]) -> None:
    require(plan.get("classification") == "image-filter-offset-reference-package-plan-no-new-rendering-support", "reference plan classification changed")
    require(plan.get("status") == "reference-package-planned-no-reference-artifact-generated", "reference plan status changed")
    row = plan.get("row")
    counters = plan.get("counters")
    require(isinstance(row, dict), "reference plan row must be an object")
    validate_row(row, "reference plan")
    require(isinstance(counters, dict), "reference plan counters must be an object")
    for key in ["referenceArtifactsAdded", "renderArtifactsAdded", "diffStatsAdded", "newSupportClaims"]:
        require(counters.get(key) == 0, f"reference plan counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "reference plan must not move readiness")
    require(counters.get("missingEvidenceItemsStill") == 9, "reference plan missing evidence count changed")


def validate_forbidden_outputs_absent() -> list[dict[str, Any]]:
    states: list[dict[str, Any]] = []
    for path in FORBIDDEN_SUPPORT_OUTPUTS:
        states.append({"path": rel(path), "present": path.exists(), "requiredAbsentForThisGate": True})
        require(not path.exists(), f"support output must remain absent in this render/diff/perf gate: {rel(path)}")
    return states


def build_summary() -> dict[str, Any]:
    absent_outputs = validate_forbidden_outputs_absent()
    render_diff_perf_absent = [
        {**item, "present": (ROOT / item["path"]).exists(), "status": "not-generated"}
        for item in RENDER_DIFF_PERF_OUTPUTS
    ]
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_offset_render_diff_perf_gate.py",
        "milestone": "M91",
        "ticket": "M91-IF-3A-RENDER",
        "classification": CLASSIFICATION,
        "status": "blocked-until-row-specific-render-diff-stat-performance-exists",
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
            "routeEvidenceGate": rel(ROUTE_GATE_JSON),
        },
        "renderDiffPerformanceBoundary": {
            "requiredArtifacts": render_diff_perf_absent,
            "readyForDiffComputation": False,
            "readyForPerformanceMeasurement": False,
            "readyForSupportEvaluation": False,
            "reason": "OffsetImageFilterGM has graph and ownership requirements only. Row-specific CPU/WebGPU render outputs, diff/stat payloads, and performance evidence are absent, so no visual support or performance claim can be evaluated.",
        },
        "requiredAbsentOutputs": absent_outputs,
        "promotionGate": {
            "renderArtifactsAddedNow": False,
            "diffStatsAddedNow": False,
            "performanceArtifactsAddedNow": False,
            "thresholdEvaluationAllowedNow": False,
            "performanceGatePromotionAllowedNow": False,
            "readyForSupportEvaluation": False,
        },
        "counters": {
            "renderDiffPerfGates": 1,
            "requiredRenderDiffPerfArtifacts": len(RENDER_DIFF_PERF_OUTPUTS),
            "renderArtifactsAdded": 0,
            "diffStatsAdded": 0,
            "performanceArtifactsAdded": 0,
            "sceneRouteEvidenceArtifactsAdded": 0,
            "fallbackReasonNoneRoutesAdded": 0,
            "referenceArtifactsAdded": 0,
            "referenceProvenanceArtifactsAdded": 0,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
            "performanceGatePromotions": 0,
            "presentEvidenceItemsStill": 2,
            "missingEvidenceItemsStill": 9,
        },
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_offset_render_diff_perf_gate.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-render-diff-perf-gate-pycache python3 -m py_compile scripts/m91_image_filter_offset_render_diff_perf_gate.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetRenderDiffPerfGate",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(summary.get("status") == "blocked-until-row-specific-render-diff-stat-performance-exists", "summary status mismatch")
    row = summary.get("row")
    counters = summary.get("counters")
    boundary = summary.get("renderDiffPerformanceBoundary")
    gate = summary.get("promotionGate")
    absent_outputs = summary.get("requiredAbsentOutputs")
    require(isinstance(row, dict), "summary row must be an object")
    validate_row(row, "summary")
    require(isinstance(boundary, dict), "renderDiffPerformanceBoundary must be an object")
    require(boundary.get("readyForDiffComputation") is False, "diff computation must not be ready")
    require(boundary.get("readyForPerformanceMeasurement") is False, "performance measurement must not be ready")
    require(boundary.get("readyForSupportEvaluation") is False, "support evaluation must not be ready")
    required = boundary.get("requiredArtifacts")
    require(isinstance(required, list) and len(required) == len(RENDER_DIFF_PERF_OUTPUTS), "render/diff/perf artifact list changed")
    require(all(item.get("present") is False and item.get("status") == "not-generated" for item in required if isinstance(item, dict)), "render/diff/perf outputs must remain not-generated")
    actual_contract = [(item.get("kind"), item.get("path")) for item in required if isinstance(item, dict)]
    require(actual_contract == EXPECTED_RENDER_DIFF_PERF_CONTRACT, "render/diff/perf artifact contract changed")
    require(len({path for _, path in actual_contract}) == len(EXPECTED_RENDER_DIFF_PERF_CONTRACT), "render/diff/perf artifact paths must be unique")
    require(isinstance(gate, dict), "promotionGate must be an object")
    require(gate.get("renderArtifactsAddedNow") is False, "render artifacts must not be added now")
    require(gate.get("diffStatsAddedNow") is False, "diff/stat must not be added now")
    require(gate.get("performanceArtifactsAddedNow") is False, "performance artifacts must not be added now")
    require(gate.get("thresholdEvaluationAllowedNow") is False, "threshold evaluation must not be allowed now")
    require(gate.get("performanceGatePromotionAllowedNow") is False, "performance gate promotion must not be allowed now")
    require(gate.get("readyForSupportEvaluation") is False, "support evaluation must not be ready")
    require(isinstance(absent_outputs, list) and len(absent_outputs) == len(FORBIDDEN_SUPPORT_OUTPUTS), "absent output list changed")
    require(all(item.get("present") is False for item in absent_outputs if isinstance(item, dict)), "support outputs must remain absent")
    require(isinstance(counters, dict), "summary counters must be an object")
    for key in [
        "renderArtifactsAdded",
        "diffStatsAdded",
        "performanceArtifactsAdded",
        "sceneRouteEvidenceArtifactsAdded",
        "fallbackReasonNoneRoutesAdded",
        "referenceArtifactsAdded",
        "referenceProvenanceArtifactsAdded",
        "newSupportClaims",
        "dashboardPromotions",
        "thresholdChanges",
        "performanceGatePromotions",
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
    boundary = summary["renderDiffPerformanceBoundary"]
    counters = summary["counters"]
    lines = [
        "# M91 OffsetImageFilterGM Render Diff Performance Gate",
        "",
        f"Status: {summary['status']}",
        "",
        "This report keeps `M91-IF-3A-RENDER` non-promotional. `OffsetImageFilterGM` has no row-specific CPU/WebGPU render outputs, diff/stat payloads, or performance evidence yet, so support evaluation remains blocked.",
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
        "## Render Diff Performance Boundary",
        "",
        f"- Ready for diff computation: `{boundary['readyForDiffComputation']}`",
        f"- Ready for performance measurement: `{boundary['readyForPerformanceMeasurement']}`",
        f"- Ready for support evaluation: `{boundary['readyForSupportEvaluation']}`",
        f"- Reason: {boundary['reason']}",
        "",
        "## Required Render/Diff/Performance Artifacts",
        "",
    ]
    for item in boundary["requiredArtifacts"]:
        lines.append(f"- `{item['kind']}`: `{item['status']}` at `{item['path']}`")
    lines.extend(["", "## Required Absent Outputs", ""])
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
        route_gate = load_json(ROUTE_GATE_JSON)
        validate_intake(intake)
        validate_reference_plan(reference_plan)
        validate_upstream_gate(
            provenance_gate,
            classification="image-filter-offset-reference-provenance-gate-no-new-rendering-support",
            status="blocked-until-row-specific-reference-provenance-exists",
            source="provenance gate",
            zero_counters=["referenceProvenanceArtifactsAdded", "referenceArtifactsAdded", "fallbackReasonNoneRoutesAdded"],
        )
        validate_upstream_gate(
            route_gate,
            classification="image-filter-offset-route-evidence-gate-no-new-rendering-support",
            status="blocked-until-row-specific-route-evidence-can-use-fallback-none",
            source="route gate",
            zero_counters=["sceneRouteEvidenceArtifactsAdded", "fallbackReasonNoneRoutesAdded", "renderArtifactsAdded", "diffStatsAdded"],
        )
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary()
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_offset_render_diff_perf_gate: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 OffsetImageFilterGM render/diff/perf gate validation passed: "
        f"renderArtifactsAdded={reloaded['counters']['renderArtifactsAdded']} "
        f"diffStatsAdded={reloaded['counters']['diffStatsAdded']} "
        f"performanceArtifactsAdded={reloaded['counters']['performanceArtifactsAdded']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
