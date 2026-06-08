#!/usr/bin/env python3
"""Generate and validate M91-IF-3A OffsetImageFilterGM readiness recap."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
INTAKE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-evidence-intake/summary.json"
REFERENCE_PLAN_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-reference-package-plan/summary.json"
PROVENANCE_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-reference-provenance-gate/summary.json"
ROUTE_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-route-evidence-gate/summary.json"
RENDER_GATE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-render-diff-perf-gate/summary.json"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-readiness-recap"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-offsetimagefilter"
SOURCE_GM = "OffsetImageFilterGM"
FALLBACK = "image-filter.offset.row-specific-artifacts-required"
CLASSIFICATION = "image-filter-offset-readiness-recap-no-new-rendering-support"

EXPECTED_GATES = [
    {
        "id": "M91-IF-3A-REF",
        "path": REFERENCE_PLAN_JSON,
        "classification": "image-filter-offset-reference-package-plan-no-new-rendering-support",
        "status": "reference-package-plan-written-no-new-rendering-support",
        "zeroCounters": ["referenceArtifactsAdded", "historicalReferencePromoted", "fallbackReasonNoneRoutesAdded"],
    },
    {
        "id": "M91-IF-3A-PROV",
        "path": PROVENANCE_GATE_JSON,
        "classification": "image-filter-offset-reference-provenance-gate-no-new-rendering-support",
        "status": "blocked-until-row-specific-reference-provenance-exists",
        "zeroCounters": ["referenceProvenanceArtifactsAdded", "referenceArtifactsAdded", "fallbackReasonNoneRoutesAdded"],
    },
    {
        "id": "M91-IF-3A-ROUTE",
        "path": ROUTE_GATE_JSON,
        "classification": "image-filter-offset-route-evidence-gate-no-new-rendering-support",
        "status": "blocked-until-row-specific-route-evidence-can-use-fallback-none",
        "zeroCounters": ["sceneRouteEvidenceArtifactsAdded", "fallbackReasonNoneRoutesAdded"],
    },
    {
        "id": "M91-IF-3A-RENDER",
        "path": RENDER_GATE_JSON,
        "classification": "image-filter-offset-render-diff-perf-gate-no-new-rendering-support",
        "status": "blocked-until-row-specific-render-diff-stat-performance-exists",
        "zeroCounters": ["renderArtifactsAdded", "diffStatsAdded", "performanceArtifactsAdded"],
    },
]
REQUIRED_ABSENT_OUTPUTS = [
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-provenance.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json",
    "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json",
]
INTAKE_REQUIRED_ABSENT_OUTPUTS = [
    path for path in REQUIRED_ABSENT_OUTPUTS if not path.endswith("/reference-provenance.json")
]
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "dashboardPromoted": False,
    "policyOnlyPromoted": False,
    "referenceArtifactAdded": False,
    "referenceProvenanceAdded": False,
    "routeEvidenceArtifactAdded": False,
    "fallbackReasonNoneClaimed": False,
    "renderArtifactsAdded": False,
    "diffStatsAdded": False,
    "performanceArtifactsAdded": False,
    "performanceGatePromoted": False,
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
    require(intake.get("status") == "partial-row-specific-evidence-present-non-promotional", "intake status changed")
    row = intake.get("row")
    counters = intake.get("counters")
    required = intake.get("requiredEvidence")
    require(isinstance(row, dict), "intake row must be an object")
    validate_row(row, "intake")
    require(isinstance(counters, dict), "intake counters must be an object")
    require(counters.get("requiredEvidenceItems") == 11, "intake required evidence count changed")
    require(counters.get("presentEvidenceItems") == 2, "intake present evidence count changed")
    require(counters.get("missingEvidenceItems") == 9, "intake missing evidence count changed")
    require(counters.get("validatedNonPromotionalEvidenceItems") == 2, "intake validated evidence count changed")
    for key in ["newSupportClaims", "dashboardPromotions", "thresholdChanges"]:
        require(counters.get(key) == 0, f"intake counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "intake must not move readiness")
    require(isinstance(required, list) and len(required) == 11, "intake required evidence list changed")
    missing_paths = {item.get("path") for item in required if isinstance(item, dict) and item.get("status") == "missing"}
    require(set(INTAKE_REQUIRED_ABSENT_OUTPUTS).issubset(missing_paths), "intake missing evidence paths changed")


def validate_gate(gate: dict[str, Any], expected: dict[str, Any]) -> dict[str, Any]:
    require(gate.get("classification") == expected["classification"], f"{expected['id']}: classification changed")
    require(gate.get("status") == expected["status"], f"{expected['id']}: status changed")
    row = gate.get("row")
    counters = gate.get("counters")
    require(isinstance(row, dict), f"{expected['id']}: row must be an object")
    validate_row(row, expected["id"])
    require(isinstance(counters, dict), f"{expected['id']}: counters must be an object")
    for key in expected["zeroCounters"] + ["newSupportClaims", "dashboardPromotions", "thresholdChanges"]:
        require(key in counters, f"{expected['id']}: missing required counter {key}")
        require(counters.get(key) == 0, f"{expected['id']}: counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, f"{expected['id']}: readiness must not move")
    require(counters.get("presentEvidenceItemsStill") == 2, f"{expected['id']}: present evidence count changed")
    require(counters.get("missingEvidenceItemsStill") == 9, f"{expected['id']}: missing evidence count changed")
    return {
        "id": expected["id"],
        "path": rel(expected["path"]),
        "classification": gate.get("classification"),
        "status": gate.get("status"),
        "readinessDelta": counters.get("readinessDelta"),
        "missingEvidenceItemsStill": counters.get("missingEvidenceItemsStill"),
        "newSupportClaims": counters.get("newSupportClaims"),
    }


def absent_outputs() -> list[dict[str, Any]]:
    outputs: list[dict[str, Any]] = []
    for relative in REQUIRED_ABSENT_OUTPUTS:
        path = ROOT / relative
        outputs.append({"path": relative, "present": path.exists(), "requiredAbsentForRecap": True})
        require(not path.exists(), f"support output must remain absent for readiness recap: {relative}")
    return outputs


def build_summary(intake: dict[str, Any], gate_summaries: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_offset_readiness_recap.py",
        "milestone": "M91",
        "ticket": "M91-IF-3A-RECAP",
        "classification": CLASSIFICATION,
        "status": "not-ready-for-support-evaluation",
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
            "gates": gate_summaries,
        },
        "evidenceState": {
            "requiredEvidenceItems": intake["counters"]["requiredEvidenceItems"],
            "presentEvidenceItems": intake["counters"]["presentEvidenceItems"],
            "missingEvidenceItems": intake["counters"]["missingEvidenceItems"],
            "presentNonPromotional": [
                "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/graph.json",
                "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/intermediate-ownership.json",
            ],
            "missingSupportOutputs": absent_outputs(),
        },
        "readinessDecision": {
            "readyForSupportEvaluation": False,
            "readyForPromotion": False,
            "nextRecommendedTicket": "M91-IF-3B",
            "nextRecommendedScope": "Start the next M91 image-filter policy-only row, or generate real OffsetImageFilterGM reference/provenance/route/render/diff/perf artifacts before any support evaluation.",
            "reason": "OffsetImageFilterGM has graph and ownership evidence only. Reference, provenance, fallbackReason=none routes, render, diff/stat, and performance artifacts remain absent.",
        },
        "counters": {
            "recapReports": 1,
            "gatesSummarized": len(gate_summaries),
            "presentEvidenceItemsStill": 2,
            "missingEvidenceItemsStill": 9,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
            "supportOutputsAdded": 0,
        },
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_offset_readiness_recap.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-readiness-recap-pycache python3 -m py_compile scripts/m91_image_filter_offset_readiness_recap.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetReadinessRecap",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(summary.get("status") == "not-ready-for-support-evaluation", "summary status mismatch")
    row = summary.get("row")
    decision = summary.get("readinessDecision")
    counters = summary.get("counters")
    evidence = summary.get("evidenceState")
    require(isinstance(row, dict), "summary row must be an object")
    validate_row(row, "summary")
    require(isinstance(decision, dict), "readinessDecision must be an object")
    require(decision.get("readyForSupportEvaluation") is False, "support evaluation must remain blocked")
    require(decision.get("readyForPromotion") is False, "promotion must remain blocked")
    require(isinstance(evidence, dict), "evidenceState must be an object")
    require(evidence.get("presentEvidenceItems") == 2, "present evidence count changed")
    require(evidence.get("missingEvidenceItems") == 9, "missing evidence count changed")
    missing = evidence.get("missingSupportOutputs")
    require(isinstance(missing, list) and len(missing) == len(REQUIRED_ABSENT_OUTPUTS), "missing support output list changed")
    require(all(item.get("present") is False for item in missing if isinstance(item, dict)), "support outputs must remain absent")
    require(isinstance(counters, dict), "counters must be an object")
    for key in ["newSupportClaims", "dashboardPromotions", "thresholdChanges", "supportOutputsAdded"]:
        require(counters.get(key) == 0, f"counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "readiness must not move")
    require(counters.get("gatesSummarized") == len(EXPECTED_GATES), "gate count changed")
    require(summary.get("nonClaims") == NON_CLAIMS, "nonClaims contract changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def render_markdown(summary: dict[str, Any]) -> str:
    row = summary["row"]
    counters = summary["counters"]
    decision = summary["readinessDecision"]
    evidence = summary["evidenceState"]
    lines = [
        "# M91 OffsetImageFilterGM Readiness Recap",
        "",
        f"Status: {summary['status']}",
        "",
        "This recap closes the current `M91-IF-3A` evidence package without promoting support. `OffsetImageFilterGM` remains expected-unsupported until row-specific reference, provenance, route, render, diff/stat, and performance artifacts exist.",
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
        "## Evidence State",
        "",
        f"- Required evidence items: `{evidence['requiredEvidenceItems']}`",
        f"- Present evidence items: `{evidence['presentEvidenceItems']}`",
        f"- Missing evidence items: `{evidence['missingEvidenceItems']}`",
        "",
        "## Gates",
        "",
    ]
    for gate in summary["inputs"]["gates"]:
        lines.append(f"- `{gate['id']}`: `{gate['status']}` at `{gate['path']}`")
    lines.extend(
        [
            "",
            "## Readiness Decision",
            "",
            f"- Ready for support evaluation: `{decision['readyForSupportEvaluation']}`",
            f"- Ready for promotion: `{decision['readyForPromotion']}`",
            f"- Next recommended ticket: `{decision['nextRecommendedTicket']}`",
            f"- Scope: {decision['nextRecommendedScope']}",
            f"- Reason: {decision['reason']}",
            "",
            "## Missing Support Outputs",
            "",
        ]
    )
    for item in evidence["missingSupportOutputs"]:
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
        validate_intake(intake)
        gate_summaries: list[dict[str, Any]] = []
        for expected in EXPECTED_GATES:
            gate_summaries.append(validate_gate(load_json(expected["path"]), expected))
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(intake, gate_summaries)
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_offset_readiness_recap: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 OffsetImageFilterGM readiness recap validation passed: "
        f"gatesSummarized={reloaded['counters']['gatesSummarized']} "
        f"missingEvidenceItemsStill={reloaded['counters']['missingEvidenceItemsStill']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
