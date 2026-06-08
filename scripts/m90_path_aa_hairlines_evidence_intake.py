#!/usr/bin/env python3
"""Generate and validate M90-PAA-3A HairlinesGM evidence intake."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
CANDIDATE_READINESS_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-readiness/summary.json"
ROUTE_CPU_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-cpu.json"
ROUTE_GPU_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/routes/skia-gm-hairlines/route-gpu.json"
DASH_HAIRLINE_VISIBILITY_JSON = ROOT / "reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json"
SIMILARITY_PROPERTIES = ROOT / "gpu-raster/test-similarity-scores-webgpu.properties"
HISTORICAL_CROSSBACKEND_TEST = ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m90-path-aa-hairlines-evidence-intake"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"
ROW_ID = "skia-gm-hairlines"
SOURCE_GM = "HairlinesGM"
FALLBACK = "coverage.hairline.row-specific-artifacts-required"
EXPECTED_CPU_ROUTE = "cpu.path.hairline.expected-unsupported"
EXPECTED_GPU_ROUTE = "webgpu.path.hairline.expected-unsupported"
EXPECTED_VISIBILITY_CPU_ROUTE = "cpu.path-coverage.hairlines.expected-unsupported"
EXPECTED_VISIBILITY_GPU_ROUTE = "webgpu.coverage.hairlines.expected-unsupported"
EXPECTED_VISIBILITY_PIPELINE_KEY = "pathAA=hairlines source=HairlinesGM visibility=policy-only"
EXPECTED_SHAPE_FACTS = [
    "hairline stroke",
    "subpixel coverage",
    "AA edge facts",
]
REQUIRED_EVIDENCE = [
    {
        "kind": "row-specific Skia reference",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/skia.png",
    },
    {
        "kind": "CPU route evidence with fallbackReason=none",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/route-cpu.json",
    },
    {
        "kind": "WebGPU route evidence with fallbackReason=none",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/route-gpu.json",
    },
    {
        "kind": "CPU/GPU rendered artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu.png",
    },
    {
        "kind": "CPU/GPU rendered artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu-diff.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu-diff.png",
    },
    {
        "kind": "CPU/GPU diff/stat artifacts",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/stats.json",
    },
    {
        "kind": "performance impact evidence",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/cpu-performance.json",
    },
    {
        "kind": "performance impact evidence",
        "requiredPath": "reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/gpu-performance.json",
    },
]
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "thresholdChanged": False,
    "edgeBudgetChanged": False,
    "belowThresholdCountedAsProductionGap": False,
    "broadPathAASupport": False,
    "broadDashSupport": False,
    "broadHairlineSupport": False,
    "broadStrokeSupport": False,
    "ganeshPort": False,
    "graphitePort": False,
}
ROUTE_NON_CLAIMS = {
    key: value for key, value in NON_CLAIMS.items() if key != "readinessMoved"
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


def registry_row(registry: dict[str, Any]) -> dict[str, Any]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(row, dict), f"missing registry row: {ROW_ID}")
    require(row.get("status") == "expected-unsupported", f"{ROW_ID}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{ROW_ID}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{ROW_ID}: policyOnly must remain true")
    require(row.get("fallbackReason") == FALLBACK, f"{ROW_ID}: fallback changed")
    require(row.get("routeCpu") == "expected-unsupported", f"{ROW_ID}: routeCpu must remain expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", f"{ROW_ID}: routeGpu must remain expected-unsupported")
    require(row.get("nextTicketType") == "policy-visibility", f"{ROW_ID}: nextTicketType must remain policy-visibility")
    return row


def candidate_row(readiness: dict[str, Any]) -> dict[str, Any]:
    require(readiness.get("classification") == "path-aa-candidate-readiness-no-new-rendering-support", "candidate readiness classification mismatch")
    ticket = readiness.get("nextRecommendedTicket")
    require(isinstance(ticket, dict), "candidate readiness missing nextRecommendedTicket")
    require(ticket.get("id") == "M90-PAA-3A", "candidate readiness no longer points to M90-PAA-3A")
    require(ticket.get("rowId") == ROW_ID, "candidate readiness next row changed")
    require(ticket.get("supportClaimAllowed") is False, "M90-PAA-3A must not allow support claims")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "candidate readiness candidates must be a list")
    candidate = next((item for item in candidates if isinstance(item, dict) and item.get("rowId") == ROW_ID), None)
    require(isinstance(candidate, dict), f"candidate readiness missing {ROW_ID}")
    require(candidate.get("readyForPromotion") is False, f"{ROW_ID}: candidate must remain not ready")
    require(candidate.get("supportClaim") is False, f"{ROW_ID}: candidate supportClaim must remain false")
    require(candidate.get("status") == "expected-unsupported", f"{ROW_ID}: candidate status changed")
    require(candidate.get("fallbackReason") == FALLBACK, f"{ROW_ID}: candidate fallback changed")
    return candidate


def validate_route_payload(route: dict[str, Any], backend: str) -> None:
    expected_route = EXPECTED_CPU_ROUTE if backend == "CPU" else EXPECTED_GPU_ROUTE
    require(route.get("rowId") == ROW_ID, f"{backend} route rowId mismatch")
    require(route.get("sourceGm") == SOURCE_GM, f"{backend} route sourceGm mismatch")
    require(route.get("backend") == backend, f"{backend} route backend mismatch")
    require(route.get("status") == "expected-unsupported", f"{backend} route status must remain expected-unsupported")
    require(route.get("fallbackReason") == FALLBACK, f"{backend} route fallback changed")
    require(route.get("policyOnlyArtifact") is True, f"{backend} route must remain policy-only")
    require(route.get("route") == expected_route, f"{backend} route id changed")
    require(route.get("ticket") == "M90-PAA-1", f"{backend} route ticket changed")
    require(route.get("schemaVersion") == 1, f"{backend} route schemaVersion changed")
    require(route.get("sourceRegistry") == rel(REGISTRY_JSON), f"{backend} route sourceRegistry changed")
    require(route.get("sourceSelection") == "reports/wgsl-pipeline/m90-path-aa-slice/selection.json", f"{backend} route sourceSelection changed")
    require(route.get("shapeFactsRequired") == EXPECTED_SHAPE_FACTS, f"{backend} route shape facts changed")
    require(route.get("nonClaims") == ROUTE_NON_CLAIMS, f"{backend} route nonClaims changed")


def validate_route(path: Path, backend: str) -> dict[str, Any]:
    route = load_json(path)
    validate_route_payload(route, backend)
    return route


def visibility_row(visibility: dict[str, Any]) -> dict[str, Any]:
    require(visibility.get("generatedBy") == "pipelineDashHairlineStrokeDashboardVisibilityPack", "visibility generatedBy mismatch")
    scenes = visibility.get("scenes")
    require(isinstance(scenes, list), "visibility scenes must be a list")
    scene = next((item for item in scenes if isinstance(item, dict) and item.get("id") == ROW_ID), None)
    require(isinstance(scene, dict), f"visibility missing {ROW_ID}")
    require(scene.get("status") == "expected-unsupported", "visibility status must remain expected-unsupported")
    require(scene.get("policyOnlyArtifacts") is True, "visibility row must remain policy-only")
    require(scene.get("fallbackReason") == FALLBACK, "visibility fallback changed")
    require(scene.get("referenceKind") == "skia-upstream", "visibility reference kind changed")
    require(scene.get("cpuRoute") == EXPECTED_VISIBILITY_CPU_ROUTE, "visibility CPU route changed")
    require(scene.get("gpuRoute") == EXPECTED_VISIBILITY_GPU_ROUTE, "visibility GPU route changed")
    require(scene.get("pipelineKey") == EXPECTED_VISIBILITY_PIPELINE_KEY, "visibility pipeline key changed")
    require("feature.hairline" in scene.get("tags", []), "visibility row missing hairline tag")
    return scene


def historical_signals() -> list[dict[str, Any]]:
    require(HISTORICAL_CROSSBACKEND_TEST.is_file(), f"missing historical crossbackend test: {rel(HISTORICAL_CROSSBACKEND_TEST)}")
    require(SIMILARITY_PROPERTIES.is_file(), f"missing similarity properties: {rel(SIMILARITY_PROPERTIES)}")
    similarity_text = SIMILARITY_PROPERTIES.read_text(encoding="utf-8")
    require("HairlinesGM=98.97" in similarity_text, "HairlinesGM historical score changed or missing")
    return [
        {
            "kind": "historical-crossbackend-test",
            "path": rel(HISTORICAL_CROSSBACKEND_TEST),
            "promotional": False,
            "reason": "Historical test signal is not row-specific M90 evidence and does not provide current skia/cpu/gpu/diff/stat/perf artifacts.",
        },
        {
            "kind": "historical-similarity-floor",
            "path": rel(SIMILARITY_PROPERTIES),
            "observed": "HairlinesGM=98.97",
            "promotional": False,
            "reason": "A historical similarity score is not a support claim; below-threshold/tolerance-only status is not counted as a production missing feature.",
        },
    ]


def evidence_status() -> list[dict[str, Any]]:
    statuses: list[dict[str, Any]] = []
    for item in REQUIRED_EVIDENCE:
        path = ROOT / str(item["requiredPath"])
        statuses.append(
            {
                "kind": item["kind"],
                "path": item["requiredPath"],
                "present": path.exists(),
                "promotional": False,
                "status": "missing" if not path.exists() else "present-but-not-validated",
            }
        )
    return statuses


def build_summary(
    registry: dict[str, Any],
    readiness: dict[str, Any],
    route_cpu: dict[str, Any],
    route_gpu: dict[str, Any],
    visibility: dict[str, Any],
) -> dict[str, Any]:
    row = registry_row(registry)
    candidate = candidate_row(readiness)
    visible = visibility_row(visibility)
    validate_route_payload(route_cpu, "CPU")
    validate_route_payload(route_gpu, "WebGPU")
    required = evidence_status()
    require(all(item["present"] is False for item in required), "M90-PAA-3A intake found unexpected row-specific artifacts")
    signals = historical_signals()
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m90_path_aa_hairlines_evidence_intake.py",
        "milestone": "M90",
        "ticket": "M90-PAA-3A",
        "classification": "path-aa-hairlines-evidence-intake-no-new-rendering-support",
        "status": "blocked-by-missing-row-specific-evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "routeCpu": rel(ROUTE_CPU_JSON),
            "routeGpu": rel(ROUTE_GPU_JSON),
            "visibility": rel(DASH_HAIRLINE_VISIBILITY_JSON),
            "similarityProperties": rel(SIMILARITY_PROPERTIES),
            "historicalCrossBackendTest": rel(HISTORICAL_CROSSBACKEND_TEST),
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
        "counters": {
            "requiredEvidenceItems": len(required),
            "presentEvidenceItems": sum(1 for item in required if item["present"]),
            "missingEvidenceItems": sum(1 for item in required if not item["present"]),
            "historicalSignals": len(signals),
            "promotionalHistoricalSignals": sum(1 for item in signals if item["promotional"]),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
        },
        "requiredEvidence": required,
        "historicalSignals": signals,
        "nextRecommendedTicket": {
            "id": "M90-PAA-3A-REF",
            "scope": "Produce row-specific HairlinesGM Skia reference plus CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance artifacts before any support evaluation.",
            "supportClaimAllowed": False,
            "promotionAllowedWithoutEvidence": False,
        },
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m90_path_aa_hairlines_evidence_intake.py",
            "rtk ./gradlew --no-daemon pipelineM90PathAaHairlinesEvidenceIntake",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    row = summary["row"]
    lines = [
        "# M90 Path AA Hairlines Evidence Intake",
        "",
        "Status: blocked by missing row-specific evidence",
        "",
        "This report materializes the `M90-PAA-3A` intake for `skia-gm-hairlines`. It records the active refusal state, inventories historical HairlinesGM signals as non-promotional, and keeps support evaluation blocked until row-specific artifacts exist.",
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
        f"- Historical signals: `{counters['historicalSignals']}`",
        f"- Promotional historical signals: `{counters['promotionalHistoricalSignals']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        "",
        "## Required Evidence",
        "",
    ]
    for item in summary["requiredEvidence"]:
        lines.append(f"- `{item['kind']}`: `{item['status']}` at `{item['path']}`")
    lines.extend(["", "## Historical Signals", ""])
    for item in summary["historicalSignals"]:
        observed = f" (`{item['observed']}`)" if "observed" in item else ""
        lines.append(f"- `{item['kind']}`{observed}: `{item['path']}`; promotional=`{item['promotional']}`. {item['reason']}")
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
    require(summary.get("classification") == "path-aa-hairlines-evidence-intake-no-new-rendering-support", "classification mismatch")
    require(summary.get("status") == "blocked-by-missing-row-specific-evidence", "status mismatch")
    counters = summary.get("counters")
    row = summary.get("row")
    required = summary.get("requiredEvidence")
    signals = summary.get("historicalSignals")
    ticket = summary.get("nextRecommendedTicket")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("requiredEvidenceItems") == len(REQUIRED_EVIDENCE), "required evidence count changed")
    require(counters.get("presentEvidenceItems") == 0, "present evidence count must remain zero for intake")
    require(counters.get("missingEvidenceItems") == len(REQUIRED_EVIDENCE), "missing evidence count changed")
    require(counters.get("historicalSignals") == 2, "historical signal count changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "historical signals must remain non-promotional")
    require(counters.get("newSupportClaims") == 0, "intake must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "intake must not move readiness")
    require(isinstance(row, dict), "row must be an object")
    require(row.get("rowId") == ROW_ID, "rowId changed")
    require(row.get("supportClaim") is False, "supportClaim must remain false")
    require(row.get("policyOnly") is True, "policyOnly must remain true")
    require(row.get("routeCpu") == "expected-unsupported", "routeCpu must remain expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", "routeGpu must remain expected-unsupported")
    require(isinstance(required, list) and len(required) == len(REQUIRED_EVIDENCE), "requiredEvidence list changed")
    require(all(item.get("present") is False for item in required if isinstance(item, dict)), "required evidence must remain absent")
    require(isinstance(signals, list) and len(signals) == 2, "historicalSignals list changed")
    require(all(item.get("promotional") is False for item in signals if isinstance(item, dict)), "historical signals must be non-promotional")
    require(isinstance(ticket, dict), "nextRecommendedTicket must be an object")
    require(ticket.get("supportClaimAllowed") is False, "next ticket must not allow support claims")
    require(ticket.get("promotionAllowedWithoutEvidence") is False, "next ticket must block promotion without evidence")
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
        visibility = load_json(DASH_HAIRLINE_VISIBILITY_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(registry, readiness, route_cpu, route_gpu, visibility)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        validate_summary(load_json(SUMMARY_JSON))
    except AssertionError as error:
        print(f"m90_path_aa_hairlines_evidence_intake: FAIL: {error}", file=sys.stderr)
        return 1
    print("M90 Path AA Hairlines evidence intake validation passed: missingEvidenceItems=10 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
