#!/usr/bin/env python3
"""Generate M91 image-filter route diagnostics for policy-only refusal rows."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SELECTION_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-slice/selection.json"
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"
ROUTES_DIR = OUTPUT_DIR / "routes"

EXPECTED_ROWS = {
    "skia-gm-imagemakewithfilter": {
        "sourceGm": "ImageMakeWithFilterGM",
        "routeStem": "image-make-with-filter",
        "graphFacts": [
            "source image lifetime",
            "image-filter graph ownership",
            "intermediate texture ownership",
            "row-specific reference/render/diff artifacts",
        ],
    },
    "skia-gm-offsetimagefilter": {
        "sourceGm": "OffsetImageFilterGM",
        "routeStem": "offset-image-filter",
        "graphFacts": [
            "offset node bounds",
            "input image lifetime",
            "intermediate texture ownership",
            "row-specific reference/render/diff artifacts",
        ],
    },
}
NON_CLAIMS = {
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


def registry_rows(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "M89 registry rows must be a list")
    by_id: dict[str, dict[str, Any]] = {}
    for row in rows:
        require(isinstance(row, dict), "M89 registry rows must be objects")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, "M89 registry row missing rowId")
        by_id[row_id] = row
    return by_id


def selected_rows(selection: dict[str, Any]) -> list[str]:
    require(selection.get("classification") == "image-filter-backlog-selection-no-new-rendering-support", "M91 selection classification mismatch")
    tickets = selection.get("nextTickets")
    require(isinstance(tickets, list), "M91 selection nextTickets must be a list")
    ticket = next((item for item in tickets if isinstance(item, dict) and item.get("id") == "M91-IF-1"), None)
    require(isinstance(ticket, dict), "M91 selection missing M91-IF-1")
    require(ticket.get("type") == "policy-visibility", "M91-IF-1 must remain policy-visibility")
    require(ticket.get("supportClaimAllowed") is False, "M91-IF-1 must not allow support claims")
    rows = ticket.get("rows")
    require(isinstance(rows, list), "M91-IF-1 rows must be a list")
    require(rows == list(EXPECTED_ROWS), "M91-IF-1 row order changed")
    return [str(row) for row in rows]


def validate_refusal(row: dict[str, Any], row_id: str, fallback: str) -> None:
    refusals = row.get("rowSpecificRefusals")
    require(isinstance(refusals, list) and len(refusals) == 1, f"{row_id} must keep exactly one rowSpecificRefusals entry")
    refusal = refusals[0]
    require(isinstance(refusal, dict), f"{row_id} refusal must be an object")
    require(refusal.get("classification") == "row-specific-expected-unsupported-no-support-claim", f"{row_id} refusal classification changed")
    require(refusal.get("fallbackReason") == fallback, f"{row_id} refusal fallback changed")
    require(refusal.get("registryFallbackReason") == fallback, f"{row_id} registry fallback changed")
    require(refusal.get("cpuStatus") == "expected-unsupported", f"{row_id} CPU status changed")
    require(refusal.get("gpuStatus") == "expected-unsupported", f"{row_id} GPU status changed")
    require(refusal.get("referenceStatus") == "not-generated", f"{row_id} reference status changed")
    require(refusal.get("diffStatsStatus") == "not-computed", f"{row_id} diff/stat status changed")
    require(refusal.get("supportScoreIncreased") is False, f"{row_id} refusal must not increase support score")


def build_route(row: dict[str, Any], row_id: str, metadata: dict[str, Any], backend: str) -> dict[str, Any]:
    fallback = row.get("fallbackReason")
    route_prefix = "cpu" if backend == "CPU" else "webgpu"
    route = f"{route_prefix}.image-filter.{metadata['routeStem']}.expected-unsupported"
    return {
        "schemaVersion": 1,
        "milestone": "M91",
        "ticket": "M91-IF-1",
        "rowId": row_id,
        "sourceGm": metadata["sourceGm"],
        "backend": backend,
        "status": "expected-unsupported",
        "route": route,
        "fallbackReason": fallback,
        "policyOnlyArtifact": True,
        "graphFactsRequired": metadata["graphFacts"],
        "policy": (
            "Future support requires a row-specific graph dump, intermediate texture ownership, Skia/reference "
            "artifacts, CPU/GPU route evidence, render artifacts, diff/stat artifacts, performance impact, and "
            "fallbackReason=none without adding a generic image-filter DAG compiler, CPU/readback fallback, "
            "threshold change, scoring change, or hidden fallback-policy change."
        ),
        "nonClaims": NON_CLAIMS,
        "sourceRegistry": rel(REGISTRY_JSON),
        "sourceSelection": rel(SELECTION_JSON),
    }


def build_summary(selection: dict[str, Any], registry: dict[str, Any]) -> dict[str, Any]:
    by_id = registry_rows(registry)
    rows = selected_rows(selection)
    diagnostics: list[dict[str, Any]] = []

    for row_id in rows:
        row = by_id.get(row_id)
        require(isinstance(row, dict), f"missing M89 registry row: {row_id}")
        fallback = row.get("fallbackReason")
        require(row.get("family") == "image-filter", f"{row_id} must remain image-filter")
        require(row.get("status") == "expected-unsupported", f"{row_id} must remain expected-unsupported")
        require(row.get("supportClaim") is False, f"{row_id} must not become support")
        require(row.get("policyOnly") is True, f"{row_id} must remain policyOnly=true")
        require(row.get("nextTicketType") == "policy-visibility", f"{row_id} must remain policy-visibility")
        require(row.get("routeCpu") == "expected-unsupported", f"{row_id} routeCpu must remain expected-unsupported")
        require(row.get("routeGpu") == "expected-unsupported", f"{row_id} routeGpu must remain expected-unsupported")
        require(fallback == expected_fallback(row_id), f"{row_id} fallback changed")
        validate_refusal(row, row_id, str(fallback))
        metadata = EXPECTED_ROWS[row_id]
        route_cpu = build_route(row, row_id, metadata, "CPU")
        route_gpu = build_route(row, row_id, metadata, "WebGPU")
        write_json(ROUTES_DIR / row_id / "route-cpu.json", route_cpu)
        write_json(ROUTES_DIR / row_id / "route-gpu.json", route_gpu)
        diagnostics.append(
            {
                "rowId": row_id,
                "sourceGm": metadata["sourceGm"],
                "fallbackReason": fallback,
                "cpu": route_cpu["route"],
                "gpu": route_gpu["route"],
                "routeCpuDiagnostic": rel(ROUTES_DIR / row_id / "route-cpu.json"),
                "routeGpuDiagnostic": rel(ROUTES_DIR / row_id / "route-gpu.json"),
                "supportClaim": False,
                "policyOnly": True,
            }
        )

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_route_diagnostics.py",
        "milestone": "M91",
        "ticket": "M91-IF-1",
        "classification": "image-filter-route-diagnostics-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "selection": rel(SELECTION_JSON),
            "registry": rel(REGISTRY_JSON),
            "imageFilterSpec": ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md",
            "pmSpec": ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md",
        },
        "counters": {
            "diagnosticRows": len(diagnostics),
            "cpuRouteDiagnostics": len(diagnostics),
            "gpuRouteDiagnostics": len(diagnostics),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "diagnostics": diagnostics,
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_route_diagnostics.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterRouteDiagnostics",
            "rtk git diff --check",
        ],
    }


def expected_fallback(row_id: str) -> str:
    return {
        "skia-gm-imagemakewithfilter": "image-filter.imagemakewithfilter.row-specific-artifacts-required",
        "skia-gm-offsetimagefilter": "image-filter.offset.row-specific-artifacts-required",
    }[row_id]


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M91 Image Filter Route Diagnostics",
        "",
        "Status: generated evidence",
        "",
        "This report adds CPU/WebGPU route diagnostics for the ImageMakeWithFilterGM and OffsetImageFilterGM policy-only refusal rows selected by `M91-IF-1`. It does not promote support, change thresholds, add a generic image-filter DAG compiler, or add a CPU/readback fallback.",
        "",
        "## Counters",
        "",
        f"- Diagnostic rows: `{counters['diagnosticRows']}`",
        f"- CPU route diagnostics: `{counters['cpuRouteDiagnostics']}`",
        f"- GPU route diagnostics: `{counters['gpuRouteDiagnostics']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
        "",
        "## Diagnostics",
        "",
    ]
    for item in summary["diagnostics"]:
        lines.extend(
            [
                f"### {item['rowId']}",
                "",
                f"- Source GM: `{item['sourceGm']}`",
                f"- Fallback: `{item['fallbackReason']}`",
                f"- CPU route: `{item['cpu']}`",
                f"- GPU route: `{item['gpu']}`",
                f"- CPU diagnostic: `{item['routeCpuDiagnostic']}`",
                f"- GPU diagnostic: `{item['routeGpuDiagnostic']}`",
                f"- Support claim: `{item['supportClaim']}`",
                f"- Policy-only: `{item['policyOnly']}`",
                "",
            ]
        )
    lines.extend(["## Support Guard", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_route(route: dict[str, Any], row_id: str, backend: str, fallback: str) -> None:
    require(route.get("rowId") == row_id, f"{row_id}: route rowId mismatch")
    require(route.get("backend") == backend, f"{row_id}: route backend mismatch")
    require(route.get("status") == "expected-unsupported", f"{row_id}: route status must remain expected-unsupported")
    require(route.get("fallbackReason") == fallback, f"{row_id}: route fallback mismatch")
    require(route.get("policyOnlyArtifact") is True, f"{row_id}: route must remain policy-only")
    policy = str(route.get("policy", ""))
    require("fallbackReason=none" in policy, f"{row_id}: route policy must name future proof boundary")
    require("without adding a generic image-filter DAG compiler" in policy, f"{row_id}: route policy must preserve DAG compiler guard")
    require("CPU/readback fallback" in policy, f"{row_id}: route policy must preserve CPU/readback guard")
    non_claims = route.get("nonClaims")
    require(isinstance(non_claims, dict), f"{row_id}: route nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"{row_id}: route nonClaims.{key} must be false")


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "image-filter-route-diagnostics-no-new-rendering-support", "classification mismatch")
    counters = summary.get("counters")
    diagnostics = summary.get("diagnostics")
    guard = summary.get("supportGuard")
    require(isinstance(counters, dict), "summary counters must be an object")
    require(counters.get("diagnosticRows") == 2, "diagnostic row count changed")
    require(counters.get("cpuRouteDiagnostics") == 2, "CPU route diagnostic count changed")
    require(counters.get("gpuRouteDiagnostics") == 2, "GPU route diagnostic count changed")
    require(counters.get("newSupportClaims") == 0, "route diagnostics must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "route diagnostics must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "route diagnostics must not promote dashboard rows")
    require(counters.get("thresholdChanges") == 0, "route diagnostics must not change thresholds")
    require(isinstance(diagnostics, list) and len(diagnostics) == 2, "diagnostics must contain 2 rows")
    require([item.get("rowId") for item in diagnostics if isinstance(item, dict)] == list(EXPECTED_ROWS), "diagnostic row order changed")
    require(isinstance(guard, dict), "supportGuard must be an object")
    for key, value in guard.items():
        require(value is False, f"supportGuard.{key} must remain false")
    for item in diagnostics:
        require(isinstance(item, dict), "diagnostic entries must be objects")
        row_id = str(item.get("rowId"))
        fallback = str(item.get("fallbackReason"))
        require(item.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
        require(item.get("policyOnly") is True, f"{row_id}: policyOnly must remain true")
        validate_route(load_json(ROOT / str(item["routeCpuDiagnostic"])), row_id, "CPU", fallback)
        validate_route(load_json(ROOT / str(item["routeGpuDiagnostic"])), row_id, "WebGPU", fallback)
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    for row_id in EXPECTED_ROWS:
        expected_files.add(ROUTES_DIR / row_id / "route-cpu.json")
        expected_files.add(ROUTES_DIR / row_id / "route-gpu.json")
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def main() -> int:
    try:
        selection = load_json(SELECTION_JSON)
        registry = load_json(REGISTRY_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(selection, registry)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        validate_summary(load_json(SUMMARY_JSON))
    except AssertionError as error:
        print(f"m91_image_filter_route_diagnostics: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 image-filter route diagnostics validation passed: diagnosticRows=2 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
