#!/usr/bin/env python3
"""Generate and validate M91-IF-3B-REF ImageMakeWithFilterGM reference package plan."""

from __future__ import annotations

import json
import shutil
import struct
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
INTAKE_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-evidence-intake/summary.json"
GRAPH_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/graph.json"
OWNERSHIP_JSON = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/intermediate-ownership.json"
FOR470_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/for470-skia-gm-imagemakewithfilter-evidence.json"
GM_SOURCE = ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/ImageMakeWithFilterGM.kt"
HISTORICAL_REFERENCES = [
    ROOT / "skia-integration-tests/src/test/resources/original-888/imagemakewithfilter.png",
    ROOT / "skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_ref.png",
    ROOT / "skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop.png",
    ROOT / "skia-integration-tests/src/test/resources/original-888/imagemakewithfilter_crop_ref.png",
]
ARTIFACT_DIR = ROOT / "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter"
REFERENCE_PLAN_JSON = ARTIFACT_DIR / "reference-plan.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-imagemakewithfilter-reference-package-plan"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

ROW_ID = "skia-gm-imagemakewithfilter"
SOURCE_GM = "ImageMakeWithFilterGM"
FALLBACK = "image-filter.imagemakewithfilter.row-specific-artifacts-required"
EXPECTED_INTAKE_CLASSIFICATION = "image-filter-imagemakewithfilter-evidence-intake-no-new-rendering-support"
CLASSIFICATION = "image-filter-imagemakewithfilter-reference-package-plan-no-new-rendering-support"
EXPECTED_REFERENCE_DIMS = (1840, 860, 16, 6)
NON_CLAIMS = {
    "supportClaimAdded": False,
    "referenceArtifactAdded": False,
    "historicalReferencePromoted": False,
    "policyOnlyPromoted": False,
    "fallbackReasonNoneClaimed": False,
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
FUTURE_OUTPUTS = [
    {
        "kind": "row-specific Skia/reference artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "reference provenance",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-provenance.json",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU route evidence with fallbackReason=none",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-cpu.json",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "WebGPU route evidence with fallbackReason=none",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/route-gpu.json",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu.png",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "WebGPU render artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu.png",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/cpu-diff.png",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "WebGPU diff artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/gpu-diff.png",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "CPU/GPU diff/stat artifact",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/stats.json",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
    {
        "kind": "performance impact evidence",
        "path": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/performance.json",
        "status": "not-generated",
        "requiredBeforePromotion": True,
    },
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


def png_header(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing historical reference: {rel(path)}")
    data = path.read_bytes()
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{rel(path)} must be a PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    require((width, height, bit_depth, color_type) == EXPECTED_REFERENCE_DIMS, f"{rel(path)} PNG header changed")
    return {
        "path": rel(path),
        "width": width,
        "height": height,
        "bitDepth": bit_depth,
        "colorType": color_type,
        "promotableAsDashboardReference": False,
        "reason": "Historical fixture only; M91 still requires a row-specific packaged Skia/reference artifact with provenance before support evaluation.",
    }


def validate_intake(intake: dict[str, Any]) -> None:
    require(intake.get("classification") == EXPECTED_INTAKE_CLASSIFICATION, "intake classification changed")
    require(intake.get("status") == "partial-row-specific-evidence-present-non-promotional", "intake status changed")
    row = intake.get("row")
    counters = intake.get("counters")
    required = intake.get("requiredEvidence")
    require(isinstance(row, dict), "intake row must be an object")
    require(row.get("rowId") == ROW_ID, "intake rowId changed")
    require(row.get("sourceGm") == SOURCE_GM, "intake sourceGm changed")
    require(row.get("status") == "expected-unsupported", "intake status must remain expected-unsupported")
    require(row.get("supportClaim") is False, "intake supportClaim must remain false")
    require(row.get("policyOnly") is True, "intake policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, "intake fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", "intake CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", "intake GPU route changed")
    require(isinstance(counters, dict), "intake counters must be an object")
    require(counters.get("requiredEvidenceItems") == 11, "required evidence count changed")
    require(counters.get("presentEvidenceItems") == 2, "present evidence count changed")
    require(counters.get("missingEvidenceItems") == 9, "missing evidence count changed")
    require(counters.get("validatedNonPromotionalEvidenceItems") == 2, "validated evidence count changed")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "intake must not promote dashboard")
    require(counters.get("thresholdChanges") == 0, "intake must not change thresholds")
    require(isinstance(required, list) and len(required) == 11, "intake requiredEvidence list changed")
    evidence_by_path = {item.get("path"): item for item in required if isinstance(item, dict)}
    require(evidence_by_path.get(rel(GRAPH_JSON), {}).get("status") == "present-non-promotional", "graph evidence must remain present")
    require(evidence_by_path.get(rel(OWNERSHIP_JSON), {}).get("status") == "present-non-promotional", "ownership evidence must remain present")
    for name in ["skia.png", "route-cpu.json", "route-gpu.json", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png", "stats.json", "performance.json"]:
        require(evidence_by_path.get(rel(ARTIFACT_DIR / name), {}).get("status") == "missing", f"{name} must remain missing")


def validate_graph(graph: dict[str, Any]) -> None:
    require(graph.get("classification") == "image-filter-imagemakewithfilter-graph-dump-no-new-rendering-support", "graph classification changed")
    require(graph.get("rowId") == ROW_ID, "graph rowId changed")
    require(graph.get("sourceGm") == SOURCE_GM, "graph sourceGm changed")
    require(graph.get("status") == "expected-unsupported", "graph status changed")
    require(graph.get("supportClaim") is False, "graph supportClaim must remain false")
    require(graph.get("fallbackReason") == FALLBACK, "graph fallback changed")
    grid = graph.get("grid")
    current_port = graph.get("currentPort")
    require(isinstance(grid, dict), "graph grid must be an object")
    require(grid.get("rows") == 6, "graph row count changed")
    require(grid.get("columns") == 13, "graph column count changed")
    require(grid.get("cells") == 78, "graph cell count changed")
    require(isinstance(current_port, dict), "graph currentPort must be an object")
    require(current_port.get("makeWithFilterExecuted") is False, "graph must not claim MakeWithFilter execution")


def validate_ownership(ownership: dict[str, Any]) -> None:
    require(ownership.get("classification") == "image-filter-imagemakewithfilter-intermediate-ownership-no-new-rendering-support", "ownership classification changed")
    require(ownership.get("rowId") == ROW_ID, "ownership rowId changed")
    require(ownership.get("sourceGm") == SOURCE_GM, "ownership sourceGm changed")
    require(ownership.get("status") == "expected-unsupported", "ownership status changed")
    require(ownership.get("supportClaim") is False, "ownership supportClaim must remain false")
    require(ownership.get("fallbackReason") == FALLBACK, "ownership fallback changed")
    require(ownership.get("ownershipStatus") == "requirements-only", "ownership must remain requirements-only")
    require(ownership.get("generalDagCompilerAdded") is False, "ownership must not add a generic DAG compiler")
    require(ownership.get("cpuReadbackFallbackAdded") is False, "ownership must not add CPU/readback fallback")
    current = ownership.get("currentImplementation")
    require(isinstance(current, dict), "ownership currentImplementation must be an object")
    require(current.get("fallbackReasonNone") is False, "ownership must not claim fallbackReason=none")
    require(current.get("makeWithFilterExecuted") is False, "ownership must not claim MakeWithFilter execution")
    require(current.get("renderArtifactsAdded") is False, "ownership must not add render artifacts")


def validate_for470(payload: dict[str, Any]) -> dict[str, Any]:
    require(payload.get("classification") == "row-specific-expected-unsupported-no-support-claim", "FOR-470 classification changed")
    row = payload.get("row")
    require(isinstance(row, dict), "FOR-470 row must be an object")
    require(row.get("inventoryId") == ROW_ID, "FOR-470 row id changed")
    require(row.get("reference", {}).get("status") == "not-generated", "FOR-470 reference status changed")
    provenance = row.get("imageMakeWithFilterProvenance")
    require(isinstance(provenance, dict), "FOR-470 provenance must be an object")
    require(provenance.get("scene") == SOURCE_GM, "FOR-470 source GM changed")
    require(provenance.get("kotlinSource") == rel(GM_SOURCE), "FOR-470 Kotlin source changed")
    require(provenance.get("upstreamSource") == "gm/imagemakewithfilter.cpp", "FOR-470 upstream source changed")
    return provenance


def validate_source_contract() -> None:
    source = GM_SOURCE.read_text(encoding="utf-8")
    for phrase in [
        'override fun getName(): String = "imagemakewithfilter"',
        "SkImages.MakeWithFilter",
        "13 filter factories",
        "6 clip-bound rows",
        "without actually running",
    ]:
        require(phrase in source, f"ImageMakeWithFilterGM source missing phrase: {phrase}")


def build_reference_plan(intake: dict[str, Any], graph: dict[str, Any], ownership: dict[str, Any], provenance: dict[str, Any]) -> dict[str, Any]:
    historical_references = [png_header(path) for path in HISTORICAL_REFERENCES]
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py",
        "milestone": "M91",
        "ticket": "M91-IF-3B-REF",
        "classification": CLASSIFICATION,
        "status": "reference-package-planned-no-reference-artifact-generated",
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
        "currentEvidence": {
            "graph": {
                "path": rel(GRAPH_JSON),
                "classification": graph.get("classification"),
                "status": graph.get("status"),
                "supportClaim": graph.get("supportClaim"),
                "gridCells": graph.get("grid", {}).get("cells"),
                "makeWithFilterExecuted": graph.get("currentPort", {}).get("makeWithFilterExecuted"),
            },
            "intermediateOwnership": {
                "path": rel(OWNERSHIP_JSON),
                "classification": ownership.get("classification"),
                "status": ownership.get("status"),
                "supportClaim": ownership.get("supportClaim"),
                "ownershipStatus": ownership.get("ownershipStatus"),
            },
            "intake": {
                "path": rel(INTAKE_JSON),
                "presentEvidenceItems": intake.get("counters", {}).get("presentEvidenceItems"),
                "missingEvidenceItems": intake.get("counters", {}).get("missingEvidenceItems"),
            },
        },
        "historicalReferences": historical_references,
        "upstreamProvenance": {
            "scene": provenance.get("scene"),
            "kotlinSource": provenance.get("kotlinSource"),
            "upstreamSource": provenance.get("upstreamSource"),
            "historicalSimilarity": provenance.get("historicalSimilarity"),
            "boundedM61Evidence": provenance.get("boundedM61Evidence"),
        },
        "referencePackageContract": {
            "selectedCapture": "one bounded ImageMakeWithFilterGM candidate cell before any full-grid support evaluation",
            "requiredReferenceKind": "skia-upstream",
            "requiredProvenancePath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/reference-provenance.json",
            "requiredPackagedReferencePath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-imagemakewithfilter/skia.png",
            "historicalFixturesCanStandIn": False,
            "reason": "Historical original-888 fixtures preserve provenance, but they are not the row-specific M91 packaged reference/provenance artifact.",
        },
        "requiredFutureOutputs": FUTURE_OUTPUTS,
        "promotionGate": {
            "supportClaimAllowedNow": False,
            "referenceArtifactAddedNow": False,
            "historicalFixtureCanStandInForPackagedReference": False,
            "fallbackReasonNoneRoutesAddedNow": False,
            "readyForSupportEvaluation": False,
            "reason": "This plan only names the reference/provenance package contract; it deliberately does not create row-specific reference, route, render, diff/stat, or performance evidence.",
        },
        "counters": {
            "referencePlans": 1,
            "historicalReferencesInventoried": len(historical_references),
            "referenceArtifactsAdded": 0,
            "historicalReferencePromoted": 0,
            "renderArtifactsAdded": 0,
            "diffStatsAdded": 0,
            "fallbackReasonNoneRoutesAdded": 0,
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
            "presentEvidenceItemsStill": 2,
            "missingEvidenceItemsStill": 9,
        },
        "nonClaims": NON_CLAIMS,
    }


def build_summary(reference_plan: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py",
        "milestone": "M91",
        "ticket": "M91-IF-3B-REF",
        "classification": CLASSIFICATION,
        "status": "reference-package-plan-written-no-new-rendering-support",
        "outputs": {
            "referencePlan": rel(REFERENCE_PLAN_JSON),
            "summaryJson": rel(SUMMARY_JSON),
            "summaryMarkdown": rel(SUMMARY_MD),
        },
        "row": reference_plan["row"],
        "historicalReferences": reference_plan["historicalReferences"],
        "referencePackageContract": reference_plan["referencePackageContract"],
        "requiredFutureOutputs": reference_plan["requiredFutureOutputs"],
        "counters": reference_plan["counters"],
        "nonClaims": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m91-imagemakewithfilter-reference-plan-pycache python3 -m py_compile scripts/m91_image_filter_imagemakewithfilter_reference_package_plan.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterImageMakeWithFilterReferencePackagePlan",
            "rtk git diff --check",
        ],
    }


def validate_reference_plan(reference_plan: dict[str, Any]) -> None:
    require(reference_plan.get("classification") == CLASSIFICATION, "reference plan classification mismatch")
    require(reference_plan.get("status") == "reference-package-planned-no-reference-artifact-generated", "reference plan status mismatch")
    row = reference_plan.get("row")
    counters = reference_plan.get("counters")
    gate = reference_plan.get("promotionGate")
    outputs = reference_plan.get("requiredFutureOutputs")
    historical = reference_plan.get("historicalReferences")
    contract = reference_plan.get("referencePackageContract")
    require(isinstance(row, dict), "reference plan row must be an object")
    require(row.get("rowId") == ROW_ID, "reference plan rowId changed")
    require(row.get("status") == "expected-unsupported", "reference plan must keep row expected-unsupported")
    require(row.get("supportClaim") is False, "reference plan must not add support claim")
    require(row.get("policyOnly") is True, "reference plan must keep policy-only")
    require(row.get("fallbackReason") == FALLBACK, "reference plan fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", "reference plan CPU route changed")
    require(row.get("routeGpu") == "expected-unsupported", "reference plan GPU route changed")
    require(isinstance(historical, list) and len(historical) == 4, "historical reference inventory changed")
    require(all(item.get("promotableAsDashboardReference") is False for item in historical if isinstance(item, dict)), "historical fixtures must not be promoted")
    require(all((item.get("width"), item.get("height"), item.get("bitDepth"), item.get("colorType")) == EXPECTED_REFERENCE_DIMS for item in historical if isinstance(item, dict)), "historical fixture dimensions changed")
    require(isinstance(contract, dict), "referencePackageContract must be an object")
    require(contract.get("historicalFixturesCanStandIn") is False, "historical fixtures must not stand in")
    require(contract.get("requiredReferenceKind") == "skia-upstream", "required reference kind changed")
    require(isinstance(outputs, list) and len(outputs) == len(FUTURE_OUTPUTS), "future output contract changed")
    require(all(item.get("status") == "not-generated" for item in outputs if isinstance(item, dict)), "future outputs must remain not-generated")
    require(isinstance(gate, dict), "promotionGate must be an object")
    require(gate.get("supportClaimAllowedNow") is False, "support claim must not be allowed now")
    require(gate.get("referenceArtifactAddedNow") is False, "reference artifact must not be added now")
    require(gate.get("historicalFixtureCanStandInForPackagedReference") is False, "historical fixture must not stand in")
    require(gate.get("fallbackReasonNoneRoutesAddedNow") is False, "fallbackReason=none routes must not be claimed")
    require(gate.get("readyForSupportEvaluation") is False, "row must not be ready for support evaluation")
    require(isinstance(counters, dict), "reference plan counters must be an object")
    require(counters.get("referencePlans") == 1, "reference plan count changed")
    require(counters.get("historicalReferencesInventoried") == 4, "historical reference count changed")
    for key in ["referenceArtifactsAdded", "historicalReferencePromoted", "renderArtifactsAdded", "diffStatsAdded", "fallbackReasonNoneRoutesAdded", "newSupportClaims", "dashboardPromotions", "thresholdChanges"]:
        require(counters.get(key) == 0, f"counter {key} must remain zero")
    require(counters.get("readinessDelta") == 0.0, "readinessDelta must remain zero")
    require(counters.get("presentEvidenceItemsStill") == 2, "present evidence count must remain 2")
    require(counters.get("missingEvidenceItemsStill") == 9, "missing evidence count must remain 9")
    require(reference_plan.get("nonClaims") == NON_CLAIMS, "nonClaims contract changed")


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == CLASSIFICATION, "summary classification mismatch")
    require(summary.get("status") == "reference-package-plan-written-no-new-rendering-support", "summary status mismatch")
    require(summary.get("row", {}).get("supportClaim") is False, "summary supportClaim must remain false")
    require(summary.get("row", {}).get("status") == "expected-unsupported", "summary row status changed")
    require(summary.get("counters", {}).get("referenceArtifactsAdded") == 0, "summary must not add reference artifact")
    require(summary.get("counters", {}).get("historicalReferencesInventoried") == 4, "summary historical reference count changed")
    require(summary.get("counters", {}).get("newSupportClaims") == 0, "summary must not add support claims")
    require(summary.get("counters", {}).get("presentEvidenceItemsStill") == 2, "summary present evidence count changed")
    require(summary.get("counters", {}).get("missingEvidenceItemsStill") == 9, "summary missing evidence count changed")
    require(summary.get("nonClaims") == NON_CLAIMS, "summary nonClaims contract changed")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_output_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_output_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_output_files ^ expected_files)]}")
    require(REFERENCE_PLAN_JSON.is_file(), f"missing reference plan output: {rel(REFERENCE_PLAN_JSON)}")


def render_markdown(summary: dict[str, Any]) -> str:
    row = summary["row"]
    counters = summary["counters"]
    contract = summary["referencePackageContract"]
    lines = [
        "# M91 ImageMakeWithFilterGM Reference Package Plan",
        "",
        f"Status: {summary['status']}",
        "",
        "This report records the reference/provenance package contract for `M91-IF-3B-REF`. It is intentionally non-promotional: the packaged Skia/reference artifact, CPU/WebGPU `fallbackReason=none` routes, render artifacts, diff/stat artifacts, and performance evidence remain missing.",
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
        "## Reference Package Contract",
        "",
        f"- Selected capture: {contract['selectedCapture']}",
        f"- Required reference kind: `{contract['requiredReferenceKind']}`",
        f"- Packaged reference path: `{contract['requiredPackagedReferencePath']}`",
        f"- Provenance path: `{contract['requiredProvenancePath']}`",
        f"- Historical fixtures can stand in: `{contract['historicalFixturesCanStandIn']}`",
        f"- Reason: {contract['reason']}",
        "",
        "## Historical Reference Boundary",
        "",
    ]
    for item in summary["historicalReferences"]:
        lines.append(
            f"- `{item['path']}`: `{item['width']}x{item['height']}`, "
            f"bitDepth=`{item['bitDepth']}`, colorType=`{item['colorType']}`, "
            f"promotable=`{item['promotableAsDashboardReference']}`"
        )
    lines.extend(["", "## Counters", ""])
    for key, value in counters.items():
        lines.append(f"- {key}: `{value}`")
    lines.extend(["", "## Required Future Outputs", ""])
    for item in summary["requiredFutureOutputs"]:
        lines.append(f"- `{item['kind']}`: `{item['status']}` at `{item['path']}`")
    lines.extend(["", "## Non-Claims", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in summary["nonClaims"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def main() -> int:
    try:
        intake = load_json(INTAKE_JSON)
        graph = load_json(GRAPH_JSON)
        ownership = load_json(OWNERSHIP_JSON)
        for470 = load_json(FOR470_JSON)
        validate_intake(intake)
        validate_graph(graph)
        validate_ownership(ownership)
        provenance = validate_for470(for470)
        validate_source_contract()
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        reference_plan = build_reference_plan(intake, graph, ownership, provenance)
        write_json(REFERENCE_PLAN_JSON, reference_plan)
        reloaded_plan = load_json(REFERENCE_PLAN_JSON)
        validate_reference_plan(reloaded_plan)
        summary = build_summary(reloaded_plan)
        write_json(SUMMARY_JSON, summary)
        reloaded_summary = load_json(SUMMARY_JSON)
        SUMMARY_MD.write_text(render_markdown(reloaded_summary), encoding="utf-8")
        validate_summary(reloaded_summary)
        require(SUMMARY_MD.read_text(encoding="utf-8") == render_markdown(reloaded_summary), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m91_image_filter_imagemakewithfilter_reference_package_plan: FAIL: {error}", file=sys.stderr)
        return 1
    print(
        "M91 ImageMakeWithFilterGM reference package plan validation passed: "
        f"referenceArtifactsAdded={reloaded_summary['counters']['referenceArtifactsAdded']} "
        f"historicalReferencesInventoried={reloaded_summary['counters']['historicalReferencesInventoried']} "
        f"missingEvidenceItemsStill={reloaded_summary['counters']['missingEvidenceItemsStill']} "
        "newSupportClaims=0 readinessDelta=0.0"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
