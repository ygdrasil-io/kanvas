#!/usr/bin/env python3
"""Generate and validate M91 image-filter candidate-readiness evidence."""

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
ROUTE_SUMMARY_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-route-diagnostics/summary.json"
PREPASS_GATE_SUMMARY_JSON = ROOT / "reports/wgsl-pipeline/m91-image-filter-prepass-gate-proof/summary.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-candidate-readiness"
SUMMARY_JSON = OUTPUT_DIR / "summary.json"
SUMMARY_MD = OUTPUT_DIR / "summary.md"

REQUIRED_PROMOTION_EVIDENCE = [
    "row-specific graph dump",
    "intermediate texture ownership",
    "row-specific Skia/reference artifact",
    "CPU route evidence with fallbackReason=none",
    "WebGPU route evidence with fallbackReason=none",
    "CPU/GPU render artifacts",
    "CPU/GPU diff/stat artifacts",
    "performance impact evidence",
]

EXPECTED_ROWS = {
    "skia-gm-offsetimagefilter": {
        "sourceGm": "OffsetImageFilterGM",
        "candidateKind": "offset-image-filter-gm",
        "priority": 1,
        "promotionTicket": "M91-IF-3A",
        "reason": "Smallest policy-only image-filter GM candidate, but still lacks row-specific graph/reference/render/diff/perf evidence.",
    },
    "skia-gm-imagemakewithfilter": {
        "sourceGm": "ImageMakeWithFilterGM",
        "candidateKind": "image-make-with-filter-gm",
        "priority": 2,
        "promotionTicket": "M91-IF-3B",
        "reason": "Broader source-image ownership case; must follow explicit graph ownership and route evidence.",
    },
    "image-filter-crop-nonnull-prepass-required": {
        "sourceGm": "Crop(input=nonNull)",
        "candidateKind": "crop-nonnull-prepass-gate",
        "priority": 3,
        "promotionTicket": "M91-IF-3C",
        "reason": "Has bounded sibling evidence but remains an out-of-scope prepass gate for non-selected graph shapes.",
    },
}
SELECTION_ORDER = [
    "skia-gm-imagemakewithfilter",
    "skia-gm-offsetimagefilter",
    "image-filter-crop-nonnull-prepass-required",
]
CANDIDATE_RANKING = list(EXPECTED_ROWS)
RANKING_POLICY = {
    "source": "scripts/m91_image_filter_candidate_readiness.py",
    "contract": "M91-IF-3 readiness ranking is a local PM ordering over selected image-filter refusal rows; it does not change registry support status, route status, dashboard counters, or selection membership.",
    "rowOrder": CANDIDATE_RANKING,
}
NON_CLAIMS = {
    "supportClaimAdded": False,
    "readinessMoved": False,
    "policyOnlyPromoted": False,
    "prepassGatePromoted": False,
    "thresholdChanged": False,
    "dashboardPromoted": False,
    "belowThresholdCountedAsProductionGap": False,
    "requiredSmokeCandidateAllowed": False,
    "generalImageFilterDagSupport": False,
    "genericImageFilterDagCompiler": False,
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


def selected_rows(selection: dict[str, Any]) -> list[str]:
    require(selection.get("classification") == "image-filter-backlog-selection-no-new-rendering-support", "M91 selection classification mismatch")
    tickets = selection.get("nextTickets")
    require(isinstance(tickets, list), "M91 selection nextTickets must be a list")
    ticket = next((item for item in tickets if isinstance(item, dict) and item.get("id") == "M91-IF-3"), None)
    require(isinstance(ticket, dict), "M91 selection missing M91-IF-3")
    require(ticket.get("type") == "bounded-promotion-candidate", "M91-IF-3 type changed")
    require(ticket.get("supportClaimAllowed") is False, "M91-IF-3 must not allow support claims")
    rows = ticket.get("rows")
    require(rows == SELECTION_ORDER, "M91-IF-3 row list changed")
    return [str(row) for row in rows]


def route_diagnostics(route_summary: dict[str, Any]) -> dict[str, dict[str, Any]]:
    require(route_summary.get("classification") == "image-filter-route-diagnostics-no-new-rendering-support", "route diagnostics classification mismatch")
    require(route_summary.get("ticket") == "M91-IF-1", "route diagnostics ticket mismatch")
    counters = route_summary.get("counters")
    require(isinstance(counters, dict), "route diagnostics counters must be an object")
    require(counters.get("newSupportClaims") == 0, "route diagnostics must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "route diagnostics must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "route diagnostics must not promote dashboard rows")
    require(counters.get("thresholdChanges") == 0, "route diagnostics must not change thresholds")
    diagnostics = route_summary.get("diagnostics")
    require(isinstance(diagnostics, list), "route diagnostics must be a list")
    result: dict[str, dict[str, Any]] = {}
    for item in diagnostics:
        require(isinstance(item, dict), "route diagnostic rows must be objects")
        row_id = item.get("rowId")
        require(isinstance(row_id, str) and row_id, "route diagnostic missing rowId")
        result[row_id] = item
    return result


def prepass_gate(prepass_summary: dict[str, Any]) -> dict[str, Any]:
    require(prepass_summary.get("classification") == "image-filter-prepass-gate-proof-no-new-rendering-support", "prepass proof classification mismatch")
    require(prepass_summary.get("ticket") == "M91-IF-2", "prepass proof ticket mismatch")
    counters = prepass_summary.get("counters")
    require(isinstance(counters, dict), "prepass proof counters must be an object")
    require(counters.get("newSupportClaims") == 0, "prepass proof must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "prepass proof must not move readiness")
    require(counters.get("requiredSmokeCandidatesAdded") == 0, "prepass proof must not add smoke candidates")
    proofs = prepass_summary.get("proofs")
    require(isinstance(proofs, list) and len(proofs) == 1, "prepass proof must contain one row")
    proof = proofs[0]
    require(isinstance(proof, dict), "prepass proof entry must be an object")
    return proof


def validate_route_file(path_value: Any, row_id: str, backend: str, fallback: Any) -> str:
    require(isinstance(path_value, str) and path_value, f"{row_id}: missing {backend} route diagnostic path")
    route = load_json(ROOT / path_value)
    require(route.get("rowId") == row_id, f"{row_id}: {backend} route rowId mismatch")
    require(route.get("backend") == backend, f"{row_id}: {backend} route backend mismatch")
    require(route.get("status") == "expected-unsupported", f"{row_id}: {backend} route must remain expected-unsupported")
    require(route.get("fallbackReason") == fallback, f"{row_id}: {backend} route fallback mismatch")
    require(route.get("policyOnlyArtifact") is True, f"{row_id}: {backend} route must remain policy-only")
    return path_value


def validate_prepass_artifact(path_value: Any, expected_status: str, expected_fallback: str) -> str:
    require(isinstance(path_value, str) and path_value, "missing prepass artifact path")
    artifact = load_json(ROOT / path_value)
    require(artifact.get("status") == expected_status, f"{path_value}: status changed")
    require(artifact.get("fallbackReason") == expected_fallback, f"{path_value}: fallback changed")
    return path_value


def policy_row_candidate(row_id: str, row: dict[str, Any], route: dict[str, Any], metadata: dict[str, Any]) -> dict[str, Any]:
    require(row.get("family") == "image-filter", f"{row_id}: family changed")
    require(row.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
    require(row.get("policyOnly") is True, f"{row_id}: policyOnly must remain true")
    require(row.get("nextTicketType") == "policy-visibility", f"{row_id}: nextTicketType must remain policy-visibility")
    require(row.get("routeCpu") == "expected-unsupported", f"{row_id}: routeCpu must remain expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
    require(route.get("supportClaim") is False, f"{row_id}: route supportClaim must remain false")
    require(route.get("policyOnly") is True, f"{row_id}: route policyOnly must remain true")
    route_cpu = validate_route_file(route.get("routeCpuDiagnostic"), row_id, "CPU", row.get("fallbackReason"))
    route_gpu = validate_route_file(route.get("routeGpuDiagnostic"), row_id, "WebGPU", row.get("fallbackReason"))
    return {
        "rowId": row_id,
        "sourceGm": metadata["sourceGm"],
        "candidateKind": metadata["candidateKind"],
        "priority": metadata["priority"],
        "readyForPromotion": False,
        "supportClaim": False,
        "status": "expected-unsupported",
        "policyOnly": True,
        "fallbackReason": row.get("fallbackReason"),
        "routeCpu": row.get("routeCpu"),
        "routeGpu": row.get("routeGpu"),
        "presentEvidence": [
            "row-specific refusal link",
            "CPU expected-unsupported route diagnostic",
            "WebGPU expected-unsupported route diagnostic",
        ],
        "missingEvidence": REQUIRED_PROMOTION_EVIDENCE,
        "routeCpuDiagnostic": route_cpu,
        "routeGpuDiagnostic": route_gpu,
        "promotionTicket": metadata["promotionTicket"],
        "reason": metadata["reason"],
    }


def prepass_candidate(row: dict[str, Any], proof: dict[str, Any], metadata: dict[str, Any]) -> dict[str, Any]:
    row_id = "image-filter-crop-nonnull-prepass-required"
    require(row.get("family") == "image-filter", f"{row_id}: family changed")
    require(row.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
    require(row.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
    require(row.get("policyOnly") is False, f"{row_id}: policyOnly must remain false")
    require(row.get("routeCpu") == "pass", f"{row_id}: routeCpu must remain pass")
    require(row.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
    require(proof.get("rowId") == row_id, f"{row_id}: prepass proof row changed")
    require(proof.get("supportClaim") is False, f"{row_id}: prepass proof supportClaim must remain false")
    require(proof.get("requiredSmokeCandidateAllowed") is False, f"{row_id}: smoke candidate must remain disallowed")
    require(proof.get("generalDagCompilerAdded") is False, f"{row_id}: general DAG compiler must remain absent")
    require(proof.get("cpuReadbackFallbackAdded") is False, f"{row_id}: CPU/readback fallback must remain absent")
    artifacts = proof.get("refusalArtifacts")
    require(isinstance(artifacts, dict), f"{row_id}: refusalArtifacts must be an object")
    route_cpu = validate_prepass_artifact(artifacts.get("routeCpuDiagnostic"), "pass", "none")
    route_gpu = validate_prepass_artifact(artifacts.get("routeGpuDiagnostic"), "expected-unsupported", str(row.get("fallbackReason")))
    inventory_stats = validate_prepass_artifact(artifacts.get("inventoryStats"), "expected-unsupported", str(row.get("fallbackReason")))
    return {
        "rowId": row_id,
        "sourceGm": metadata["sourceGm"],
        "candidateKind": metadata["candidateKind"],
        "priority": metadata["priority"],
        "readyForPromotion": False,
        "supportClaim": False,
        "status": "expected-unsupported",
        "policyOnly": False,
        "fallbackReason": row.get("fallbackReason"),
        "routeCpu": row.get("routeCpu"),
        "routeGpu": row.get("routeGpu"),
        "presentEvidence": [
            "CPU oracle route with fallbackReason=none",
            "static reference/CPU image artifacts",
            "bounded M38 sibling prepass boundary",
            "prepass gate proof",
        ],
        "missingEvidence": [
            "row-specific graph dump",
            "intermediate texture ownership",
            "WebGPU route evidence with fallbackReason=none",
            "CPU/GPU render artifacts",
            "CPU/GPU diff/stat artifacts",
            "performance impact evidence",
        ],
        "routeCpuDiagnostic": route_cpu,
        "routeGpuDiagnostic": route_gpu,
        "inventoryStats": inventory_stats,
        "supportedSiblingBoundary": proof.get("supportedSiblingBoundary"),
        "promotionTicket": metadata["promotionTicket"],
        "reason": metadata["reason"],
    }


def build_summary(
    registry: dict[str, Any],
    selection: dict[str, Any],
    route_summary: dict[str, Any],
    prepass_summary: dict[str, Any],
) -> dict[str, Any]:
    selected = selected_rows(selection)
    require(set(selected) == set(EXPECTED_ROWS), "M91-IF-3 row set changed")
    routes = route_diagnostics(route_summary)
    proof = prepass_gate(prepass_summary)
    rows = registry_rows(registry)

    candidates: list[dict[str, Any]] = []
    for row_id, metadata in sorted(EXPECTED_ROWS.items(), key=lambda item: item[1]["priority"]):
        row = rows.get(row_id)
        require(isinstance(row, dict), f"missing registry row: {row_id}")
        if row_id == "image-filter-crop-nonnull-prepass-required":
            candidates.append(prepass_candidate(row, proof, metadata))
        else:
            route = routes.get(row_id)
            require(isinstance(route, dict), f"missing M91-IF-1 route diagnostic row: {row_id}")
            candidates.append(policy_row_candidate(row_id, row, route, metadata))

    next_ticket = {
        "id": "M91-IF-3A",
        "rowId": "skia-gm-offsetimagefilter",
        "scope": "Collect row-specific graph dump, intermediate ownership, Skia/reference artifacts, CPU/WebGPU fallbackReason=none routes, render/diff/stat artifacts, and performance impact for OffsetImageFilterGM before any support evaluation.",
        "supportClaimAllowed": False,
        "promotionAllowedWithoutEvidence": False,
    }
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_candidate_readiness.py",
        "milestone": "M91",
        "ticket": "M91-IF-3",
        "classification": "image-filter-candidate-readiness-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "selection": rel(SELECTION_JSON),
            "routeDiagnostics": rel(ROUTE_SUMMARY_JSON),
            "prepassGateProof": rel(PREPASS_GATE_SUMMARY_JSON),
            "imageFilterSpec": ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md",
        },
        "counters": {
            "candidateRows": len(candidates),
            "readyForPromotionRows": 0,
            "blockedByMissingEvidenceRows": len(candidates),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "rankingPolicy": RANKING_POLICY,
        "requiredPromotionEvidence": REQUIRED_PROMOTION_EVIDENCE,
        "candidates": candidates,
        "nextRecommendedTicket": next_ticket,
        "supportGuard": NON_CLAIMS,
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_candidate_readiness.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterCandidateReadiness",
            "rtk git diff --check",
        ],
    }


def render_markdown(summary: dict[str, Any]) -> str:
    counters = summary["counters"]
    lines = [
        "# M91 Image Filter Candidate Readiness",
        "",
        "Status: generated evidence",
        "",
        "This report evaluates the M91-IF-3 image-filter candidates for readiness only. It does not promote rendering support because every candidate still lacks required row-specific graph, ownership, route, render, diff/stat, or performance evidence.",
        "",
        "## Counters",
        "",
        f"- Candidate rows: `{counters['candidateRows']}`",
        f"- Ready for promotion rows: `{counters['readyForPromotionRows']}`",
        f"- Blocked by missing evidence rows: `{counters['blockedByMissingEvidenceRows']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
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
            "",
            "## Candidate Ranking",
            "",
        ]
    )
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
                f"- Reason: {candidate['reason']}",
                "- Present evidence:",
            ]
        )
        lines.extend(f"  - `{item}`" for item in candidate["presentEvidence"])
        lines.append("- Missing evidence:")
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
    require(summary.get("classification") == "image-filter-candidate-readiness-no-new-rendering-support", "classification mismatch")
    counters = summary.get("counters")
    candidates = summary.get("candidates")
    guard = summary.get("supportGuard")
    ranking = summary.get("rankingPolicy")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("candidateRows") == 3, "candidate row count changed")
    require(counters.get("readyForPromotionRows") == 0, "ready promotion row count changed")
    require(counters.get("blockedByMissingEvidenceRows") == 3, "blocked candidate row count changed")
    require(counters.get("newSupportClaims") == 0, "candidate readiness must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "candidate readiness must not move readiness")
    require(counters.get("dashboardPromotions") == 0, "candidate readiness must not promote dashboard rows")
    require(counters.get("thresholdChanges") == 0, "candidate readiness must not change thresholds")
    require(isinstance(candidates, list) and len(candidates) == 3, "candidates must contain 3 rows")
    require(ranking == RANKING_POLICY, "rankingPolicy must match the full ranking contract")
    require([candidate.get("rowId") for candidate in candidates if isinstance(candidate, dict)] == CANDIDATE_RANKING, "candidate priority order changed")
    require(guard == NON_CLAIMS, "supportGuard must match the full non-claims contract")
    for candidate in candidates:
        require(isinstance(candidate, dict), "candidate entries must be objects")
        row_id = str(candidate.get("rowId"))
        require(candidate.get("readyForPromotion") is False, f"{row_id}: must not be ready for promotion")
        require(candidate.get("supportClaim") is False, f"{row_id}: supportClaim must remain false")
        require(candidate.get("status") == "expected-unsupported", f"{row_id}: status must remain expected-unsupported")
        require(isinstance(candidate.get("missingEvidence"), list) and candidate["missingEvidence"], f"{row_id}: missingEvidence must stay non-empty")
        if row_id == "image-filter-crop-nonnull-prepass-required":
            require(candidate.get("routeCpu") == "pass", f"{row_id}: routeCpu must remain pass")
            require(candidate.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
            require("WebGPU route evidence with fallbackReason=none" in candidate["missingEvidence"], f"{row_id}: WebGPU route proof must remain missing")
        else:
            require(candidate.get("policyOnly") is True, f"{row_id}: policyOnly must remain true")
            require(candidate.get("routeCpu") == "expected-unsupported", f"{row_id}: routeCpu must remain expected-unsupported")
            require(candidate.get("routeGpu") == "expected-unsupported", f"{row_id}: routeGpu must remain expected-unsupported")
            require(candidate.get("missingEvidence") == REQUIRED_PROMOTION_EVIDENCE, f"{row_id}: missingEvidence changed")
    ticket = summary.get("nextRecommendedTicket")
    require(isinstance(ticket, dict), "nextRecommendedTicket must be an object")
    require(ticket.get("id") == "M91-IF-3A", "next ticket id changed")
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
        prepass_summary = load_json(PREPASS_GATE_SUMMARY_JSON)
        if OUTPUT_DIR.exists():
            shutil.rmtree(OUTPUT_DIR)
        summary = build_summary(registry, selection, route_summary, prepass_summary)
        write_json(SUMMARY_JSON, summary)
        SUMMARY_MD.write_text(render_markdown(summary), encoding="utf-8")
        validate_summary(load_json(SUMMARY_JSON))
    except AssertionError as error:
        print(f"m91_image_filter_candidate_readiness: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 image-filter candidate-readiness validation passed: candidateRows=3 readyForPromotionRows=0 newSupportClaims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
