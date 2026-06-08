#!/usr/bin/env python3
"""Generate and validate M90 Path AA candidate-readiness evidence."""

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
ROUTE_SUMMARY_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-route-diagnostics/summary.json"
EDGE_BUDGET_SUMMARY_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-edge-budget-proof/summary.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-readiness"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

REQUIRED_PROMOTION_EVIDENCE = [
    "row-specific Skia reference",
    "CPU route evidence with fallbackReason=none",
    "WebGPU route evidence with fallbackReason=none",
    "CPU/GPU diff/stat artifacts",
    "performance impact evidence",
]
EXPECTED_ROWS = {
    "skia-gm-hairlines": {
        "sourceGm": "HairlinesGM",
        "candidateKind": "bounded-hairline",
        "priority": 1,
        "promotionTicket": "M90-PAA-3A",
        "notes": "Smallest hairline candidate by route taxonomy, but still policy-only and missing all promotion evidence.",
    },
    "skia-gm-strokerect": {
        "sourceGm": "StrokeRectGM",
        "candidateKind": "bounded-stroke-rect",
        "priority": 2,
        "promotionTicket": "M90-PAA-3B",
        "notes": "Smallest stroke-rect candidate after hairline; still lacks row-specific reference, pass routes, diff/stat, and perf evidence.",
    },
    "skia-gm-thinstrokedrects": {
        "sourceGm": "ThinStrokedRectsGM",
        "candidateKind": "thin-stroke-rect",
        "priority": 3,
        "promotionTicket": "M90-PAA-3C",
        "notes": "Thin rect strokes need subpixel coverage proof before any support evaluation.",
    },
    "skia-gm-strokedlines": {
        "sourceGm": "StrokedLinesGM",
        "candidateKind": "bounded-stroked-lines",
        "priority": 4,
        "promotionTicket": "M90-PAA-3D",
        "notes": "Line stroke caps require row-specific CPU/GPU agreement before promotion can be evaluated.",
    },
    "skia-gm-strokerects": {
        "sourceGm": "StrokeRectsGM",
        "candidateKind": "multi-stroke-rects",
        "priority": 5,
        "promotionTicket": "M90-PAA-3E",
        "notes": "Multiple stroked rects broaden the slice and are not first candidate material without row-specific evidence.",
    },
    "skia-gm-hairmodes": {
        "sourceGm": "HairModesGM",
        "candidateKind": "hairline-paint-mode",
        "priority": 6,
        "promotionTicket": "M90-PAA-3F",
        "notes": "Paint/blend mode interaction makes this later than the plain hairline candidate.",
    },
    "skia-gm-scaledstrokes": {
        "sourceGm": "ScaledStrokesGM",
        "candidateKind": "scaled-stroke",
        "priority": 7,
        "promotionTicket": "M90-PAA-3G",
        "notes": "Transform-dependent stroke scale should follow a simpler stroke slice.",
    },
    "skia-gm-dashing": {
        "sourceGm": "DashingGM",
        "candidateKind": "dash-stroke",
        "priority": 8,
        "promotionTicket": "M90-PAA-3H",
        "notes": "Dash intervals, caps, and joins broaden the slice; edge-budget refusal proof remains separate.",
    },
    "skia-gm-dashcubics": {
        "sourceGm": "DashCubicsGM",
        "candidateKind": "dash-cubic",
        "priority": 9,
        "promotionTicket": "M90-PAA-3I",
        "notes": "Cubic dash behavior is the broadest candidate in this set and must stay after bounded hairline/stroke proof.",
    },
}
SELECTION_ORDER = [
    "skia-gm-dashcubics",
    "skia-gm-dashing",
    "skia-gm-hairlines",
    "skia-gm-hairmodes",
    "skia-gm-scaledstrokes",
    "skia-gm-strokedlines",
    "skia-gm-strokerect",
    "skia-gm-strokerects",
    "skia-gm-thinstrokedrects",
]
CANDIDATE_RANKING = list(EXPECTED_ROWS)
RANKING_POLICY = {
    "source": "scripts/m90_path_aa_candidate_readiness.py",
    "contract": "M90-PAA-3 readiness ranking is a local PM ordering over the selected policy rows; it does not change registry support status or selection membership.",
    "rowOrder": CANDIDATE_RANKING,
}
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


def selected_rows(selection: dict[str, Any]) -> list[str]:
    require(selection.get("classification") == "path-aa-backlog-selection-no-new-rendering-support", "M90 selection classification mismatch")
    tickets = selection.get("nextTickets")
    require(isinstance(tickets, list), "M90 selection nextTickets must be a list")
    ticket = next((item for item in tickets if isinstance(item, dict) and item.get("id") == "M90-PAA-3"), None)
    require(isinstance(ticket, dict), "M90 selection missing M90-PAA-3")
    require(ticket.get("supportClaimAllowed") is False, "M90-PAA-3 must not allow support claims")
    require(ticket.get("type") == "bounded-promotion-candidate", "M90-PAA-3 type changed")
    rows = ticket.get("rows")
    require(rows == SELECTION_ORDER, "M90-PAA-3 row list changed")
    return [str(row) for row in rows]


def route_diagnostics(route_summary: dict[str, Any]) -> dict[str, dict[str, Any]]:
    require(route_summary.get("classification") == "path-aa-route-diagnostics-no-new-rendering-support", "route diagnostic classification mismatch")
    require(route_summary.get("ticket") == "M90-PAA-1", "route diagnostic ticket mismatch")
    counters = route_summary.get("counters")
    require(isinstance(counters, dict), "route diagnostic counters must be an object")
    require(counters.get("newSupportClaims") == 0, "route diagnostics must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "route diagnostics must not move readiness")
    diagnostics = route_summary.get("diagnostics")
    require(isinstance(diagnostics, list), "route diagnostics must be a list")
    result: dict[str, dict[str, Any]] = {}
    for item in diagnostics:
        require(isinstance(item, dict), "route diagnostic rows must be objects")
        row_id = item.get("rowId")
        require(isinstance(row_id, str) and row_id, "route diagnostic missing rowId")
        result[row_id] = item
    return result


def validate_edge_budget_proof(edge_summary: dict[str, Any]) -> None:
    require(edge_summary.get("classification") == "path-aa-edge-budget-refusal-proof-no-new-rendering-support", "edge-budget proof classification mismatch")
    counters = edge_summary.get("counters")
    require(isinstance(counters, dict), "edge-budget counters must be an object")
    require(counters.get("edgeBudget") == 256, "edge budget changed")
    require(counters.get("newSupportClaims") == 0, "edge-budget proof must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "edge-budget proof must not move readiness")


def validate_route_file(path_value: Any, row_id: str, backend: str, fallback: Any) -> str:
    require(isinstance(path_value, str) and path_value, f"{row_id}: missing {backend} route diagnostic path")
    route = load_json(ROOT / path_value)
    require(route.get("rowId") == row_id, f"{row_id}: {backend} route rowId mismatch")
    require(route.get("backend") == backend, f"{row_id}: {backend} route backend mismatch")
    require(route.get("status") == "expected-unsupported", f"{row_id}: {backend} route must remain expected-unsupported")
    require(route.get("fallbackReason") == fallback, f"{row_id}: {backend} route fallback mismatch")
    require(route.get("policyOnlyArtifact") is True, f"{row_id}: {backend} route must remain policy-only")
    return path_value


def missing_evidence(row: dict[str, Any], route: dict[str, Any]) -> list[str]:
    missing = list(REQUIRED_PROMOTION_EVIDENCE)
    artifacts = row.get("artifacts")
    if isinstance(artifacts, dict) and artifacts.get("reference"):
        missing.remove("row-specific Skia reference")
    if route.get("cpu") and route.get("cpu") != "expected-unsupported" and "fallbackReason=none" in str(route.get("cpu")):
        missing.remove("CPU route evidence with fallbackReason=none")
    if route.get("gpu") and route.get("gpu") != "expected-unsupported" and "fallbackReason=none" in str(route.get("gpu")):
        missing.remove("WebGPU route evidence with fallbackReason=none")
    if isinstance(artifacts, dict) and artifacts.get("cpuDiff") and artifacts.get("gpuDiff"):
        missing.remove("CPU/GPU diff/stat artifacts")
    return missing


def build_summary(
    registry: dict[str, Any],
    selection: dict[str, Any],
    route_summary: dict[str, Any],
    edge_summary: dict[str, Any],
) -> dict[str, Any]:
    selected = selected_rows(selection)
    require(set(selected) == set(EXPECTED_ROWS), "M90-PAA-3 row set changed")
    routes = route_diagnostics(route_summary)
    validate_edge_budget_proof(edge_summary)
    rows = registry_rows(registry)

    candidates: list[dict[str, Any]] = []
    for row_id, metadata in sorted(EXPECTED_ROWS.items(), key=lambda item: item[1]["priority"]):
        row = rows.get(row_id)
        require(isinstance(row, dict), f"missing registry row: {row_id}")
        route = routes.get(row_id)
        require(isinstance(route, dict), f"missing M90-PAA-1 route diagnostic row: {row_id}")
        require(row.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
        require(row.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
        require(row.get("policyOnly") is True, f"{row_id}: policyOnly must remain true")
        require(row.get("nextTicketType") == "policy-visibility", f"{row_id}: nextTicketType must remain policy-visibility")
        require(row.get("routeCpu") == "expected-unsupported", f"{row_id}: routeCpu must remain expected-unsupported")
        require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
        require(route.get("supportClaim") is False, f"{row_id}: route diagnostic supportClaim must remain false")
        require(route.get("policyOnly") is True, f"{row_id}: route diagnostic policyOnly must remain true")
        route_cpu = validate_route_file(route.get("routeCpuDiagnostic"), row_id, "CPU", row.get("fallbackReason"))
        route_gpu = validate_route_file(route.get("routeGpuDiagnostic"), row_id, "WebGPU", row.get("fallbackReason"))
        missing = missing_evidence(row, route)
        require(missing == REQUIRED_PROMOTION_EVIDENCE, f"{row_id}: unexpected promotion evidence appeared: {missing}")
        candidates.append(
            {
                "rowId": row_id,
                "sourceGm": metadata["sourceGm"],
                "candidateKind": metadata["candidateKind"],
                "priority": metadata["priority"],
                "readyForPromotion": False,
                "supportClaim": False,
                "status": "expected-unsupported",
                "fallbackReason": row.get("fallbackReason"),
                "routeCpu": row.get("routeCpu"),
                "routeGpu": row.get("routeGpu"),
                "routeCpuDiagnostic": route_cpu,
                "routeGpuDiagnostic": route_gpu,
                "missingEvidence": missing,
                "promotionTicket": metadata["promotionTicket"],
                "notes": metadata["notes"],
            }
        )

    next_ticket = {
        "id": "M90-PAA-3A",
        "rowId": "skia-gm-hairlines",
        "scope": "Collect row-specific Skia reference, CPU/GPU fallbackReason=none route evidence, diff/stat artifacts, and performance impact for HairlinesGM before any support evaluation.",
        "supportClaimAllowed": False,
        "promotionAllowedWithoutEvidence": False,
    }

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m90_path_aa_candidate_readiness.py",
        "milestone": "M90",
        "ticket": "M90-PAA-3",
        "classification": "path-aa-candidate-readiness-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "selection": rel(SELECTION_JSON),
            "routeDiagnostics": rel(ROUTE_SUMMARY_JSON),
            "edgeBudgetProof": rel(EDGE_BUDGET_SUMMARY_JSON),
            "pathAaBoundarySpec": ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md",
        },
        "counters": {
            "candidateRows": len(candidates),
            "readyForPromotionRows": 0,
            "blockedByMissingEvidenceRows": len(candidates),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
        },
        "rankingPolicy": RANKING_POLICY,
        "requiredPromotionEvidence": REQUIRED_PROMOTION_EVIDENCE,
        "candidates": candidates,
        "nextRecommendedTicket": next_ticket,
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m90_path_aa_candidate_readiness.py",
            "rtk ./gradlew --no-daemon pipelineM90PathAaCandidateReadiness",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M90 Path AA Candidate Readiness",
        "",
        "Status: generated evidence",
        "",
        "This report evaluates the M90-PAA-3 dash, hairline, and stroke candidates for readiness only. It does not promote rendering support because every candidate is still policy-only and lacks the required row-specific evidence.",
        "",
        "## Counters",
        "",
        f"- Candidate rows: `{counters['candidateRows']}`",
        f"- Ready for promotion rows: `{counters['readyForPromotionRows']}`",
        f"- Blocked by missing evidence rows: `{counters['blockedByMissingEvidenceRows']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        "",
        "## Required Promotion Evidence",
        "",
    ]
    lines.extend(f"- `{item}`" for item in summary["requiredPromotionEvidence"])
    lines.extend(
        [
            "",
            "## Ranking Policy",
            "",
            f"- Source: `{summary['rankingPolicy']['source']}`",
            f"- Contract: {summary['rankingPolicy']['contract']}",
            f"- Row order: `{', '.join(summary['rankingPolicy']['rowOrder'])}`",
        ]
    )
    lines.extend(["", "## Candidate Ranking", ""])
    for candidate in summary["candidates"]:
        lines.extend(
            [
                f"### {candidate['priority']}. {candidate['rowId']}",
                "",
                f"- Source GM: `{candidate['sourceGm']}`",
                f"- Candidate kind: `{candidate['candidateKind']}`",
                f"- Ready for promotion: `{candidate['readyForPromotion']}`",
                f"- Status: `{candidate['status']}`",
                f"- Fallback: `{candidate['fallbackReason']}`",
                f"- CPU route: `{candidate['routeCpu']}`",
                f"- GPU route: `{candidate['routeGpu']}`",
                f"- CPU diagnostic: `{candidate['routeCpuDiagnostic']}`",
                f"- GPU diagnostic: `{candidate['routeGpuDiagnostic']}`",
                f"- Promotion ticket: `{candidate['promotionTicket']}`",
                f"- Notes: {candidate['notes']}",
                "- Missing evidence:",
            ]
        )
        lines.extend(f"  - `{item}`" for item in candidate["missingEvidence"])
        lines.append("")
    ticket = summary["nextRecommendedTicket"]
    lines.extend(
        [
            "## Next Recommended Ticket",
            "",
            f"- ID: `{ticket['id']}`",
            f"- Row: `{ticket['rowId']}`",
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
    require(summary.get("classification") == "path-aa-candidate-readiness-no-new-rendering-support", "classification mismatch")
    counters = summary.get("counters")
    candidates = summary.get("candidates")
    guard = summary.get("supportGuard")
    ranking = summary.get("rankingPolicy")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("candidateRows") == 9, "candidate row count changed")
    require(counters.get("readyForPromotionRows") == 0, "ready promotion row count changed")
    require(counters.get("blockedByMissingEvidenceRows") == 9, "blocked candidate row count changed")
    require(counters.get("newSupportClaims") == 0, "candidate readiness must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "candidate readiness must not move readiness")
    require(isinstance(candidates, list) and len(candidates) == 9, "candidates must contain 9 rows")
    require(ranking == RANKING_POLICY, "rankingPolicy must match the full ranking contract")
    require([candidate.get("rowId") for candidate in candidates if isinstance(candidate, dict)] == CANDIDATE_RANKING, "candidate priority order changed")
    require(guard == NON_CLAIMS, "supportGuard must match the full non-claims contract")
    for candidate in candidates:
        require(isinstance(candidate, dict), "candidate entries must be objects")
        row_id = str(candidate.get("rowId"))
        require(candidate.get("readyForPromotion") is False, f"{row_id}: must not be ready for promotion")
        require(candidate.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
        require(candidate.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
        require(candidate.get("routeCpu") == "expected-unsupported", f"{row_id}: routeCpu must remain expected-unsupported")
        require(candidate.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
        require(candidate.get("missingEvidence") == REQUIRED_PROMOTION_EVIDENCE, f"{row_id}: missingEvidence changed")
    ticket = summary.get("nextRecommendedTicket")
    require(isinstance(ticket, dict), "nextRecommendedTicket must be an object")
    require(ticket.get("id") == "M90-PAA-3A", "next ticket id changed")
    require(ticket.get("supportClaimAllowed") is False, "next ticket must not allow support claims")
    require(ticket.get("promotionAllowedWithoutEvidence") is False, "next ticket must block promotion without evidence")
    expected_files = {SUMMARY_JSON, SUMMARY_MD}
    actual_files = {path for path in OUTPUT_DIR.rglob("*") if path.is_file()}
    require(actual_files == expected_files, f"unexpected generated files: {[rel(path) for path in sorted(actual_files ^ expected_files)]}")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        selection = load_json(SELECTION_JSON)
        route_summary = load_json(ROUTE_SUMMARY_JSON)
        edge_summary = load_json(EDGE_BUDGET_SUMMARY_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(registry, selection, route_summary, edge_summary)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        validate_summary(load_json(SUMMARY_JSON))
    except AssertionError as error:
        print(f"m90_path_aa_candidate_readiness: FAIL: {error}", file=sys.stderr)
        return 1
    print("M90 Path AA candidate-readiness validation passed: candidateRows=9 readyForPromotionRows=0 newSupportClaims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
