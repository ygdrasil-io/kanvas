#!/usr/bin/env python3
"""Generate and validate M90 Path AA edge-budget refusal proof."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
SELECTION_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-slice/selection.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m90-path-aa-edge-budget-proof"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"
SCENE_ARTIFACT_ROOT = ROOT / "reports/wgsl-pipeline/scenes/artifacts"
EDGE_BUDGET = 256
FALLBACK = "coverage.edge-count-exceeded"
POLICY_REPORT = "reports/wgsl-pipeline/2026-05-31-m47-path-aa-expected-unsupported-policy-validation.md"
EVIDENCE_REPORT = "reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md"

EXPECTED_ROWS = {
    "path-aa-convexpaths-edge-budget": {
        "sourceScene": "ConvexPathsGM",
        "cpuRoute": "cpu.path-coverage.convexpaths-oracle",
        "gpuPipelineKey": "coverageKind=pathCoverageUnsupported,pathFillRule=winding,topology=triangleList,source=ConvexPathsGM",
    },
    "path-aa-dashing-edge-budget": {
        "sourceScene": "DashingGM",
        "cpuRoute": "cpu.path-coverage.dashing-oracle",
        "gpuPipelineKey": "coverageKind=pathStrokeDashOverflow,pathFillRule=winding,topology=triangleList,source=DashingGM",
    },
}
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "thresholdChanged": False,
    "edgeBudgetChanged": False,
    "smokeCandidateAllowed": False,
    "belowThresholdCountedAsProductionGap": False,
    "broadPathAASupport": False,
    "broadDashSupport": False,
    "broadStrokeSupport": False,
    "ganeshPort": False,
    "graphitePort": False,
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
    require(selection.get("classification") == "path-aa-backlog-selection-no-new-rendering-support", "M90 selection classification mismatch")
    tickets = selection.get("nextTickets")
    require(isinstance(tickets, list), "M90 selection nextTickets must be a list")
    ticket = next((item for item in tickets if isinstance(item, dict) and item.get("id") == "M90-PAA-2"), None)
    require(isinstance(ticket, dict), "M90 selection missing M90-PAA-2")
    require(ticket.get("supportClaimAllowed") is False, "M90-PAA-2 must not allow support claims")
    require(ticket.get("rows") == list(EXPECTED_ROWS), "M90-PAA-2 row list changed")


def validate_registry_row(row_id: str, row: dict[str, Any], expected: dict[str, str]) -> dict[str, Any]:
    require(row.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
    require(row.get("fallbackReason") == FALLBACK, f"{row_id}: fallback changed")
    require(row.get("routeCpu") == "pass", f"{row_id}: CPU route must remain pass oracle")
    require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: GPU route must remain expected-unsupported")
    require(row.get("referenceKind") == "cpu-oracle", f"{row_id}: referenceKind must remain cpu-oracle")
    links = row.get("edgeBudgetGateLinks")
    require(isinstance(links, list) and len(links) == 1, f"{row_id}: expected exactly one edgeBudgetGateLinks entry")
    link = links[0]
    require(isinstance(link, dict), f"{row_id}: edgeBudgetGateLinks entry must be object")
    require(link.get("edgeBudget") == EDGE_BUDGET, f"{row_id}: edge budget changed")
    require(link.get("fallbackReason") == FALLBACK, f"{row_id}: link fallback changed")
    require(link.get("sourceScene") == expected["sourceScene"], f"{row_id}: sourceScene changed")
    require(link.get("cpuRoute") == expected["cpuRoute"], f"{row_id}: CPU route link changed")
    require(link.get("gpuRoute") == "webgpu.coverage.refuse.edge-count", f"{row_id}: GPU route link changed")
    require(link.get("gpuCoverageStrategy") == "webgpu.coverage.refuse", f"{row_id}: GPU coverage strategy changed")
    require(link.get("gpuPipelineKey") == expected["gpuPipelineKey"], f"{row_id}: GPU pipeline key changed")
    require(link.get("policyReport") == POLICY_REPORT, f"{row_id}: policy report link changed")
    require(link.get("evidenceReport") == EVIDENCE_REPORT, f"{row_id}: evidence report link changed")
    require((ROOT / POLICY_REPORT).is_file(), f"{row_id}: missing policy report")
    require((ROOT / EVIDENCE_REPORT).is_file(), f"{row_id}: missing evidence report")
    require(link.get("globalEdgeBudgetIncreased") is False, f"{row_id}: global edge budget increased")
    require(link.get("supportScoreIncreased") is False, f"{row_id}: support score increased")
    require(link.get("smokeCandidateAllowed") is False, f"{row_id}: smoke candidate must stay disallowed")
    return link


def validate_artifacts(row_id: str, expected: dict[str, str]) -> dict[str, Any]:
    artifact_root = SCENE_ARTIFACT_ROOT / row_id
    cpu_route = load_json(artifact_root / "route-cpu.json")
    gpu_route = load_json(artifact_root / "route-gpu.json")
    stats = load_json(artifact_root / "stats.json")
    require(cpu_route.get("sceneId") == row_id, f"{row_id}: CPU route sceneId mismatch")
    require(cpu_route.get("backend") == "CPU", f"{row_id}: CPU route backend mismatch")
    require(cpu_route.get("status") == "pass", f"{row_id}: CPU artifact route must remain pass")
    require(cpu_route.get("selectedRoute") == expected["cpuRoute"], f"{row_id}: CPU artifact route changed")
    require(cpu_route.get("fallbackReason") == "none", f"{row_id}: CPU fallback must remain none")
    require(cpu_route.get("sourceReport") == EVIDENCE_REPORT, f"{row_id}: CPU route sourceReport changed")
    require(gpu_route.get("sceneId") == row_id, f"{row_id}: GPU route sceneId mismatch")
    require(gpu_route.get("backend") == "WebGPU", f"{row_id}: GPU route backend mismatch")
    require(gpu_route.get("status") == "expected-unsupported", f"{row_id}: GPU artifact route must remain expected-unsupported")
    require(gpu_route.get("coverageStrategy") == "webgpu.coverage.refuse", f"{row_id}: GPU coverage strategy changed")
    require(gpu_route.get("pipelineKey") == expected["gpuPipelineKey"], f"{row_id}: GPU pipeline key changed")
    require(gpu_route.get("fallbackReason") == FALLBACK, f"{row_id}: GPU fallback changed")
    require(gpu_route.get("sourceReport") == EVIDENCE_REPORT, f"{row_id}: GPU route sourceReport changed")
    require("sceneId" not in stats, f"{row_id}: inventory stats unexpectedly claim scene identity")
    require("sourceReport" not in stats, f"{row_id}: inventory stats unexpectedly claim source report identity")
    require(stats.get("backend") == "inventory", f"{row_id}: stats backend changed")
    require(stats.get("status") == "expected-unsupported", f"{row_id}: stats status changed")
    require(stats.get("fallbackReason") == FALLBACK, f"{row_id}: stats fallback changed")
    require(stats.get("threshold") == 0.0, f"{row_id}: stats threshold changed")
    return {
        "routeCpuDiagnostic": rel(artifact_root / "route-cpu.json"),
        "routeGpuDiagnostic": rel(artifact_root / "route-gpu.json"),
        "inventoryStats": rel(artifact_root / "stats.json"),
        "identityEvidence": "route diagnostics and registry edgeBudgetGateLinks; stats are inventory counters only",
    }


def build_summary(registry: dict[str, Any], selection: dict[str, Any]) -> dict[str, Any]:
    require_selection(selection)
    rows = registry_rows(registry)
    proofs: list[dict[str, Any]] = []
    for row_id, expected in EXPECTED_ROWS.items():
        row = rows.get(row_id)
        require(isinstance(row, dict), f"missing registry row: {row_id}")
        link = validate_registry_row(row_id, row, expected)
        artifacts = validate_artifacts(row_id, expected)
        proofs.append(
            {
                "rowId": row_id,
                "sourceScene": expected["sourceScene"],
                "status": "expected-unsupported",
                "supportClaim": False,
                "fallbackReason": FALLBACK,
                "edgeBudget": EDGE_BUDGET,
                "routeCpu": "pass",
                "routeGpu": "expected-unsupported",
                "cpuRoute": expected["cpuRoute"],
                "gpuRoute": "webgpu.coverage.refuse.edge-count",
                "gpuCoverageStrategy": "webgpu.coverage.refuse",
                "gpuPipelineKey": expected["gpuPipelineKey"],
                "smokeCandidateAllowed": False,
                "globalEdgeBudgetIncreased": False,
                "supportScoreIncreased": False,
                "policyReport": link.get("policyReport"),
                "evidenceReport": link.get("evidenceReport"),
                "artifacts": artifacts,
            }
        )
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m90_path_aa_edge_budget_proof.py",
        "milestone": "M90",
        "ticket": "M90-PAA-2",
        "classification": "path-aa-edge-budget-refusal-proof-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "selection": rel(SELECTION_JSON),
            "pathAaBoundarySpec": ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md",
            "edgeBudgetAdr": ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md",
        },
        "counters": {
            "proofRows": len(proofs),
            "edgeBudget": EDGE_BUDGET,
            "gpuExpectedUnsupportedRows": len(proofs),
            "cpuOraclePassRows": len(proofs),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
        },
        "proofs": proofs,
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m90_path_aa_edge_budget_proof.py",
            "rtk ./gradlew --no-daemon pipelineM90PathAaEdgeBudgetProof",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M90 Path AA Edge-Budget Refusal Proof",
        "",
        "Status: generated evidence",
        "",
        "This report refreshes the refusal proof for the two M90 edge-budget rows. It keeps the WebGPU AA edge budget at 256 and does not promote rendering support.",
        "",
        "## Counters",
        "",
        f"- Proof rows: `{counters['proofRows']}`",
        f"- WebGPU AA edge budget: `{counters['edgeBudget']}`",
        f"- GPU expected-unsupported rows: `{counters['gpuExpectedUnsupportedRows']}`",
        f"- CPU oracle pass rows: `{counters['cpuOraclePassRows']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        "",
        "## Proof Rows",
        "",
    ]
    for proof in summary["proofs"]:
        lines.extend(
            [
                f"### {proof['rowId']}",
                "",
                f"- Source scene: `{proof['sourceScene']}`",
                f"- Fallback: `{proof['fallbackReason']}`",
                f"- Edge budget: `{proof['edgeBudget']}`",
                f"- CPU route: `{proof['cpuRoute']}` (`{proof['routeCpu']}`)",
                f"- GPU route: `{proof['gpuRoute']}` (`{proof['routeGpu']}`)",
                f"- GPU pipeline key: `{proof['gpuPipelineKey']}`",
                f"- Smoke candidate allowed: `{proof['smokeCandidateAllowed']}`",
                f"- Support claim: `{proof['supportClaim']}`",
                f"- CPU diagnostic: `{proof['artifacts']['routeCpuDiagnostic']}`",
                f"- GPU diagnostic: `{proof['artifacts']['routeGpuDiagnostic']}`",
                f"- Inventory stats: `{proof['artifacts']['inventoryStats']}`",
                f"- Identity evidence: {proof['artifacts']['identityEvidence']}",
                "",
            ]
        )
    lines.extend(["## Support Guard", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in summary["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in summary["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_summary(summary: dict[str, Any]) -> None:
    require(summary.get("classification") == "path-aa-edge-budget-refusal-proof-no-new-rendering-support", "classification mismatch")
    counters = summary.get("counters")
    proofs = summary.get("proofs")
    guard = summary.get("supportGuard")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("proofRows") == 2, "proof row count changed")
    require(counters.get("edgeBudget") == EDGE_BUDGET, "edge budget changed")
    require(counters.get("gpuExpectedUnsupportedRows") == 2, "GPU expected-unsupported count changed")
    require(counters.get("cpuOraclePassRows") == 2, "CPU oracle pass count changed")
    require(counters.get("newSupportClaims") == 0, "new support claims changed")
    require(counters.get("readinessDelta") == 0.0, "readiness changed")
    require(isinstance(proofs, list) and len(proofs) == 2, "proofs must contain two rows")
    require([proof.get("rowId") for proof in proofs if isinstance(proof, dict)] == list(EXPECTED_ROWS), "proof row order changed")
    require(isinstance(guard, dict), "supportGuard must be an object")
    for key, value in guard.items():
        require(value is False, f"supportGuard.{key} must remain false")
    for proof in proofs:
        require(isinstance(proof, dict), "proof entries must be objects")
        row_id = str(proof.get("rowId"))
        require(proof.get("edgeBudget") == EDGE_BUDGET, f"{row_id}: edge budget changed")
        require(proof.get("routeCpu") == "pass", f"{row_id}: routeCpu changed")
        require(proof.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu changed")
        require(proof.get("fallbackReason") == FALLBACK, f"{row_id}: fallback changed")
        require(proof.get("supportClaim") is False, f"{row_id}: support claim changed")
        require(proof.get("smokeCandidateAllowed") is False, f"{row_id}: smoke candidate changed")
        require(proof.get("globalEdgeBudgetIncreased") is False, f"{row_id}: global budget changed")
        require(proof.get("supportScoreIncreased") is False, f"{row_id}: support score changed")
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
        print(f"m90_path_aa_edge_budget_proof: FAIL: {error}", file=sys.stderr)
        return 1
    print("M90 Path AA edge-budget proof validation passed: proofRows=2 edgeBudget=256 newSupportClaims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
