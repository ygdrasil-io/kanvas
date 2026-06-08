#!/usr/bin/env python3
"""Generate and validate M91-IF-3A-PROV OffsetImageFilterGM reference provenance gate."""

from __future__ import annotations

import json
import shutil
import struct
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REFERENCE_PLAN_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-plan.json"
REFERENCE_PLAN_SUMMARY_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-reference-package-plan/summary.json"
INTAKE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-evidence-intake/summary.json"
HISTORICAL_REFERENCE = ROOT / "skia-integration-tests/src/test/resources/original-888/offsetimagefilter.png"
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-offset-reference-provenance-gate"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-offsetimagefilter"
SOURCE_GM = "OffsetImageFilterGM"
FALLBACK = "image-filter.offset.row-specific-artifacts-required"
CLASSIFICATION = "image-filter-offset-reference-provenance-gate-no-new-rendering-support"

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
EXPECTED_FUTURE_OUTPUTS = [
    {
        "kind": "row-specific Skia/reference artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/skia.png",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "reference provenance",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/reference-provenance.json",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "CPU route evidence with fallbackReason=none",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-cpu.json",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "WebGPU route evidence with fallbackReason=none",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/route-gpu.json",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "CPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu.png",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "WebGPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu.png",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "CPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/cpu-diff.png",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "WebGPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/gpu-diff.png",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "CPU/GPU diff/stat artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/stats.json",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
    {
        "kind": "performance impact evidence",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-offsetimagefilter/performance.json",
        "requiredBeforePromotion": True,
        "status": "not-generated",
    },
]

NON_CLAIMS = {
    "supportClaimAdded": False,
    "referenceArtifactAdded": False,
    "referenceProvenanceAdded": False,
    "historicalReferencePromoted": False,
    "policyOnlyPromoted": False,
    "fallbackReasonNoneClaimed": False,
    "renderArtifactsAdded": False,
    "diffStatsAdded": False,
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


def png_header(path: Path) -> dict[str, int]:
    require(path.is_file(), f"missing historical reference fixture: {rel(path)}")
    data = path.read_bytes()
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} must remain a PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    require((width, height, bit_depth, color_type) == (600, 100, 16, 6), f"{rel(path)} PNG header changed")
    return {"width": width, "height": height, "bitDepth": bit_depth, "colorType": color_type}


def validate_row(row: dict[str, Any], source: str) -> None:
    require(row.get("rowId") == ROW_ID, f"{source}: rowId changed")
    require(row.get("sourceGm") == SOURCE_GM, f"{source}: sourceGm changed")
    require(row.get("status") == "expected-unsupported", f"{source}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{source}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{source}: policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, f"{source}: fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", f"{source}: CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", f"{source}: GPU route changed")


def validate_reference_plan(reference_plan: dict[str, Any]) -> None:
    require(
        reference_plan.get("classification") == "image-filter-offset-reference-package-plan-no-new-rendering-support",
        "reference plan classification changed",
    )
    require(
        reference_plan.get("status") == "reference-package-planned-no-reference-artifact-generated",
        "reference plan status changed",
    )
    row = reference_plan.get("row")
    counters = reference_plan.get("counters")
    historical = reference_plan.get("historicalReference")
    gate = reference_plan.get("promotionGate")
    future_outputs = reference_plan.get("requiredFutureOutputs")
    require(isinstance(row, dict), "reference plan row must be an object")
    validate_row(row, "reference plan")
    require(isinstance(counters, dict), "reference plan counters must be an object")
    require(counters.get("referenceArtifactsAdded") == 0, "reference plan must not add reference artifacts")
    require(counters.get("historicalReferencePromoted") == 0, "reference plan must not promote historical reference")
    require(counters.get("newSupportClaims") == 0, "reference plan must not add support claims")
    require(counters.get("presentEvidenceItemsStill") == 2, "reference plan present evidence count changed")
    require(counters.get("missingEvidenceItemsStill") == 9, "reference plan missing evidence count changed")
    require(isinstance(historical, dict), "reference plan historicalReference must be an object")
    require(historical.get("path") == rel(HISTORICAL_REFERENCE), "reference plan historical fixture path changed")
    require(historical.get("promotableAsDashboardReference") is False, "historical fixture must remain non-promotable")
    require(isinstance(gate, dict), "reference plan promotionGate must be an object")
    require(gate.get("supportClaimAllowedNow") is False, "support claim must not be allowed")
    require(gate.get("referenceArtifactAddedNow") is False, "reference artifact must not be added")
    require(gate.get("historicalFixtureCanStandInForPackagedReference") is False, "historical fixture must not stand in")
    require(gate.get("readyForSupportEvaluation") is False, "reference plan must not be ready for support")
    require(future_outputs == EXPECTED_FUTURE_OUTPUTS, "future output contract changed")


def validate_reference_plan_summary(summary: dict[str, Any]) -> None:
    require(
        summary.get("classification") == "image-filter-offset-reference-package-plan-no-new-rendering-support",
        "reference plan summary classification changed",
    )
    require(summary.get("status") == "reference-package-plan-written-no-new-rendering-support", "reference plan summary status changed")
    row = summary.get("row")
    counters = summary.get("counters")
    require(isinstance(row, dict), "reference plan summary row must be an object")
    validate_row(row, "reference plan summary")
    require(isinstance(counters, dict), "reference plan summary counters must be an object")
    require(counters.get("referenceArtifactsAdded") == 0, "summary must not add reference artifact")
    require(counters.get("newSupportClaims") == 0, "summary must not add support claim")
    require(counters.get("presentEvidenceItemsStill") == 2, "summary present evidence count changed")
    require(counters.get("missingEvidenceItemsStill") == 9, "summary missing evidence count changed")


def validate_intake(intake: dict[str, Any]) -> None:
    require(intake.get("classification") == "image-filter-offset-evidence-intake-no-new-rendering-support", "intake classification changed")
    row = intake.get("row")
    counters = intake.get("counters")
    require(isinstance(row, dict), "intake row must be an object")
    validate_row(row, "intake")
    require(isinstance(counters, dict), "intake counters must be an object")
    require(counters.get("presentEvidenceItems") == 2, "intake present evidence count changed")
    require(counters.get("missingEvidenceItems") == 9, "intake missing evidence count changed")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("dashboardPromotions") == 0, "intake must not promote dashboard")
    require(counters.get("thresholdChanges") == 0, "intake must not change thresholds")


def validate_forbidden_outputs_absent() -> list[dict[str, Any]]:
    states: list[dict[str, Any]] = []
    for path in FORBIDDEN_SUPPORT_OUTPUTS:
        states.append({"path": rel(path), "present": path.exists(), "requiredAbsentForThisGate": True})
        require(not path.exists(), f"support/provenance output must remain absent in this gate: {rel(path)}")
    return states


def build_summary(reference_plan: dict[str, Any]) -> dict[str, Any]:
    header = png_header(HISTORICAL_REFERENCE)
    absent_outputs = validate_forbidden_outputs_absent()
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_offset_reference_provenance_gate.py",
        "milestone": "M91",
        "ticket": "M91-IF-3A-PROV",
        "classification": CLASSIFICATION,
        "status": "blocked-until-row-specific-reference-provenance-exists",
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
            "referencePlan": rel(REFERENCE_PLAN_JSON),
            "referencePlanSummary": rel(REFERENCE_PLAN_SUMMARY_JSON),
            "evidenceIntake": rel(INTAKE_JSON),
            "historicalFixture": rel(HISTORICAL_REFERENCE),
        },
        "historicalFixtureBoundary": {
            "path": rel(HISTORICAL_REFERENCE),
            **header,
            "allowedUse": "provenance-boundary-check-only",
            "canSatisfySkiaReferenceArtifact": False,
            "canSatisfyReferenceProvenance": False,
            "reason": "The checked-in fixture is historical test data, not a row-specific M91 reference package with generation provenance.",
        },
        "requiredAbsentOutputs": absent_outputs,
        "requiredFutureEvidence": reference_plan.get("requiredFutureOutputs"),
        "counters": {
            "provenanceGates": 1,
            "referenceProvenanceArtifactsAdded": 0,
            "referenceArtifactsAdded": 0,
            "historicalFixturePromoted": 0,
            "fallbackReasonNoneRoutesAdded": 0,
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
            "rtk python3 scripts/m91_image_filter_offset_reference_provenance_gate.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-offset-reference-provenance-gate-pycache python3 -m py_compile scripts/m91_image_filter_offset_reference_provenance_gate.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterOffsetReferenceProvenanceGate",
            "rtk git diff --check",
        ],
    }


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(summary.get("status") == "blocked-until-row-specific-reference-provenance-exists", "summary status mismatch")
    row = summary.get("row")
    counters = summary.get("counters")
    fixture = summary.get("historicalFixtureBoundary")
    absent_outputs = summary.get("requiredAbsentOutputs")
    require(isinstance(row, dict), "summary row must be an object")
    validate_row(row, "summary")
    require(isinstance(fixture, dict), "historicalFixtureBoundary must be an object")
    require(fixture.get("canSatisfySkiaReferenceArtifact") is False, "historical fixture must not satisfy skia.png")
    require(fixture.get("canSatisfyReferenceProvenance") is False, "historical fixture must not satisfy reference provenance")
    require(isinstance(absent_outputs, list) and len(absent_outputs) == len(FORBIDDEN_SUPPORT_OUTPUTS), "absent output list changed")
    require(all(item.get("present") is False for item in absent_outputs if isinstance(item, dict)), "support outputs must remain absent")
    require(isinstance(counters, dict), "summary counters must be an object")
    for key in [
        "referenceProvenanceArtifactsAdded",
        "referenceArtifactsAdded",
        "historicalFixturePromoted",
        "fallbackReasonNoneRoutesAdded",
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
    fixture = summary["historicalFixtureBoundary"]
    counters = summary["counters"]
    lines = [
        "# M91 OffsetImageFilterGM Reference Provenance Gate",
        "",
        f"Status: {summary['status']}",
        "",
        "This report blocks reference promotion for `M91-IF-3A-PROV` until a row-specific `skia.png` and `reference-provenance.json` exist. It deliberately validates that those files, plus route/render/diff/performance artifacts, are absent in this gate.",
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
        "## Historical Fixture Boundary",
        "",
        f"- Fixture: `{fixture['path']}`",
        f"- Dimensions: `{fixture['width']}x{fixture['height']}`",
        f"- Bit depth: `{fixture['bitDepth']}`",
        f"- Color type: `{fixture['colorType']}`",
        f"- Allowed use: `{fixture['allowedUse']}`",
        f"- Can satisfy `skia.png`: `{fixture['canSatisfySkiaReferenceArtifact']}`",
        f"- Can satisfy `reference-provenance.json`: `{fixture['canSatisfyReferenceProvenance']}`",
        f"- Reason: {fixture['reason']}",
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
        reference_plan = load_json(REFERENCE_PLAN_JSON)
        reference_plan_summary = load_json(REFERENCE_PLAN_SUMMARY_JSON)
        intake = load_json(INTAKE_JSON)
        validate_reference_plan(reference_plan)
        validate_reference_plan_summary(reference_plan_summary)
        validate_intake(intake)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(reference_plan)
        write_json(SUMMARY_JSON, summary)
        reloaded = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded), encoding="utf-8")
        validate_summary(reloaded)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_offset_reference_provenance_gate: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 OffsetImageFilterGM reference provenance gate validation passed: "
        f"referenceProvenanceArtifactsAdded={reloaded['counters']['referenceProvenanceArtifactsAdded']} "
        f"missingEvidenceItemsStill={reloaded['counters']['missingEvidenceItemsStill']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
