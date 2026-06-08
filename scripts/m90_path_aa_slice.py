#!/usr/bin/env python3
"""Generate and validate the M90 Path AA backlog slice from the M89 registry."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
M89_CLOSEOUT_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/closeout.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m90-path-aa-slice"
OUTPUT_JSON = OUTPUT_DIR / "selection.json"
OUTPUT_MD = OUTPUT_DIR / "selection.md"

EXPECTED_PASS_ROWS = [
    "analytic-aa-convex",
    "clip-rect-difference",
    "draw-paint-clipped-rect",
    "draw-paint-full-clip",
    "path-aa-stroke-primitive",
]
EXPECTED_EDGE_BUDGET_ROWS = [
    "path-aa-convexpaths-edge-budget",
    "path-aa-dashing-edge-budget",
]
EXPECTED_ROW_SPECIFIC_ROWS = [
    "skia-gm-pathfill",
    "skia-gm-rectpolystroke",
]
EXPECTED_GROUPED_POLICY_ROWS = [
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


def rows_by_id(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    by_id: dict[str, dict[str, Any]] = {}
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, "registry row missing rowId")
        by_id[row_id] = row
    return by_id


def require_m89_closeout_handoff(closeout: dict[str, Any]) -> None:
    require(closeout.get("classification") == "pm-closeout-no-new-rendering-support", "M89 closeout classification mismatch")
    require(closeout.get("readiness", {}).get("readinessDelta") == 0.0, "M89 closeout readinessDelta changed")
    slices = closeout.get("nextRecommendedSlices")
    require(isinstance(slices, list), "M89 closeout nextRecommendedSlices must be a list")
    m90 = next((item for item in slices if isinstance(item, dict) and item.get("milestone") == "M90"), None)
    require(isinstance(m90, dict), "M89 closeout missing M90 handoff")
    require(m90.get("family") == "path-aa", "M89 closeout M90 handoff must target path-aa")
    require(m90.get("supportClaimAllowedFromCloseout") is False, "M89 closeout must not authorize M90 support claims")


def summarize_rows(by_id: dict[str, dict[str, Any]], row_ids: list[str]) -> list[dict[str, Any]]:
    summary: list[dict[str, Any]] = []
    for row_id in row_ids:
        row = by_id.get(row_id)
        require(isinstance(row, dict), f"missing M89 registry row: {row_id}")
        summary.append(
            {
                "rowId": row_id,
                "source": row.get("source"),
                "status": row.get("status"),
                "fallbackReason": row.get("fallbackReason"),
                "supportClaim": row.get("supportClaim"),
                "policyOnly": row.get("policyOnly"),
                "nextTicketType": row.get("nextTicketType"),
            }
        )
    return summary


def build_selection(registry: dict[str, Any], closeout: dict[str, Any]) -> dict[str, Any]:
    require_m89_closeout_handoff(closeout)
    counters = registry.get("counters")
    require(isinstance(counters, dict), "registry counters must be an object")
    require(counters.get("supportClaims") == 22, "M90 slice cannot change M89 supportClaims")
    require(counters.get("unlinkedUnsupportedRows") == 0, "M90 slice requires M89 unlinkedUnsupportedRows=0")
    require(counters.get("family", {}).get("path-aa") == 18, "M89 path-aa row count changed")
    by_id = rows_by_id(registry)

    pass_rows = summarize_rows(by_id, EXPECTED_PASS_ROWS)
    edge_budget_rows = summarize_rows(by_id, EXPECTED_EDGE_BUDGET_ROWS)
    row_specific_rows = summarize_rows(by_id, EXPECTED_ROW_SPECIFIC_ROWS)
    grouped_policy_rows = summarize_rows(by_id, EXPECTED_GROUPED_POLICY_ROWS)

    for item in pass_rows:
        require(item["status"] == "pass", f"{item['rowId']} must remain pass baseline")
        require(item["supportClaim"] is True, f"{item['rowId']} pass baseline must retain supportClaim=true")
    for item in edge_budget_rows:
        require(item["status"] == "expected-unsupported", f"{item['rowId']} must remain expected-unsupported")
        require(item["fallbackReason"] == "coverage.edge-count-exceeded", f"{item['rowId']} edge-budget fallback changed")
        require(item["supportClaim"] is False, f"{item['rowId']} must not become support")
        require(len(by_id[item["rowId"]].get("edgeBudgetGateLinks", [])) == 1, f"{item['rowId']} missing edgeBudgetGateLinks")
    for item in row_specific_rows:
        require(item["status"] == "expected-unsupported", f"{item['rowId']} must remain expected-unsupported")
        require(item["supportClaim"] is False, f"{item['rowId']} must not become support")
        require(len(by_id[item["rowId"]].get("rowSpecificRefusals", [])) == 1, f"{item['rowId']} missing rowSpecificRefusals")
    for item in grouped_policy_rows:
        require(item["status"] == "expected-unsupported", f"{item['rowId']} must remain expected-unsupported")
        require(item["policyOnly"] is True, f"{item['rowId']} must remain policyOnly=true")
        require(item["supportClaim"] is False, f"{item['rowId']} must not become support")
        require(len(by_id[item["rowId"]].get("groupedPolicyRefusals", [])) == 1, f"{item['rowId']} missing groupedPolicyRefusals")

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m90_path_aa_slice.py",
        "milestone": "M90",
        "classification": "path-aa-backlog-selection-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "m89Closeout": rel(M89_CLOSEOUT_JSON),
            "pathAaBoundarySpec": ".upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md",
            "edgeBudgetAdr": ".upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md",
        },
        "counters": {
            "pathAaRows": 18,
            "existingPassRows": len(pass_rows),
            "edgeBudgetRefusalRows": len(edge_budget_rows),
            "rowSpecificRefusalRows": len(row_specific_rows),
            "groupedPolicyRefusalRows": len(grouped_policy_rows),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
        },
        "clusters": {
            "existingBoundedSupportBaseline": pass_rows,
            "edgeBudgetRefusals": edge_budget_rows,
            "rowSpecificRefusals": row_specific_rows,
            "groupedDashHairlineStrokePolicyRefusals": grouped_policy_rows,
        },
        "nextTickets": [
            {
                "id": "M90-PAA-1",
                "type": "policy-visibility",
                "scope": "Add route diagnostics for dash, hairline, scaled-stroke, and stroke-rect GM policy rows without changing support claims.",
                "rows": EXPECTED_GROUPED_POLICY_ROWS,
                "supportClaimAllowed": False,
            },
            {
                "id": "M90-PAA-2",
                "type": "implementation-or-refusal-proof",
                "scope": "Refresh edge-budget refusal proof for ConvexPathsGM and DashingGM; keep 256-edge budget unless benchmark/ADR evidence justifies change.",
                "rows": EXPECTED_EDGE_BUDGET_ROWS,
                "supportClaimAllowed": False,
            },
            {
                "id": "M90-PAA-3",
                "type": "bounded-promotion-candidate",
                "scope": "Only after row-specific Skia reference, CPU/GPU route, diff/stat, and performance impact evidence exists, evaluate one bounded dash or hairline slice for support.",
                "rows": EXPECTED_GROUPED_POLICY_ROWS,
                "supportClaimAllowed": False,
            },
        ],
        "supportGuard": {
            "supportClaimsChanged": False,
            "thresholdsChanged": False,
            "edgeBudgetChanged": False,
            "policyOnlyRowsPromoted": False,
            "belowThresholdCountedAsProductionGap": False,
            "broadPathAASupportClaimed": False,
            "ganeshPort": False,
            "graphitePort": False,
        },
        "validationCommands": [
            "rtk python3 scripts/m90_path_aa_slice.py",
            "rtk ./gradlew --no-daemon pipelineM90PathAaSlice",
            "rtk git diff --check",
        ],
    }


def render_markdown(selection: dict[str, Any]) -> str:
    counters = selection["counters"]
    guard = selection["supportGuard"]
    lines = [
        "# M90 Path AA Backlog Slice",
        "",
        "Status: generated evidence",
        "",
        "This slice turns the M89 registry closeout into the first M90 Path AA backlog contract. It does not promote support, change thresholds, or change the 256-edge WebGPU AA budget.",
        "",
        "## Counters",
        "",
        f"- Path AA rows: `{counters['pathAaRows']}`",
        f"- Existing pass baseline rows: `{counters['existingPassRows']}`",
        f"- Edge-budget refusal rows: `{counters['edgeBudgetRefusalRows']}`",
        f"- Row-specific refusal rows: `{counters['rowSpecificRefusalRows']}`",
        f"- Grouped policy refusal rows: `{counters['groupedPolicyRefusalRows']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        "",
        "## Clusters",
        "",
    ]
    for title, rows in selection["clusters"].items():
        lines.extend([f"### {title}", ""])
        for row in rows:
            lines.append(
                f"- `{row['rowId']}`: `{row['status']}`, fallback `{row['fallbackReason']}`, supportClaim `{row['supportClaim']}`"
            )
        lines.append("")
    lines.extend(["## Next Tickets", ""])
    for ticket in selection["nextTickets"]:
        lines.extend(
            [
                f"### {ticket['id']}",
                "",
                f"- Type: `{ticket['type']}`",
                f"- Scope: {ticket['scope']}",
                f"- Rows: {', '.join(f'`{row}`' for row in ticket['rows'])}",
                f"- Support claim allowed: `{ticket['supportClaimAllowed']}`",
                "",
            ]
        )
    lines.extend(["## Support Guard", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in guard.items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in selection["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_selection(selection: dict[str, Any]) -> None:
    require(selection.get("classification") == "path-aa-backlog-selection-no-new-rendering-support", "classification mismatch")
    counters = selection.get("counters")
    clusters = selection.get("clusters")
    tickets = selection.get("nextTickets")
    guard = selection.get("supportGuard")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("pathAaRows") == 18, "pathAaRows changed")
    require(counters.get("existingPassRows") == 5, "existing pass baseline count changed")
    require(counters.get("edgeBudgetRefusalRows") == 2, "edge-budget refusal count changed")
    require(counters.get("rowSpecificRefusalRows") == 2, "row-specific refusal count changed")
    require(counters.get("groupedPolicyRefusalRows") == 9, "grouped policy refusal count changed")
    require(counters.get("newSupportClaims") == 0, "M90 slice must not add support claims")
    require(counters.get("readinessDelta") == 0.0, "M90 slice must not move readiness")
    require(isinstance(clusters, dict), "clusters must be an object")
    require(isinstance(tickets, list) and len(tickets) == 3, "nextTickets must contain three scoped tickets")
    for ticket in tickets:
        require(isinstance(ticket, dict), "ticket entries must be objects")
        require(ticket.get("supportClaimAllowed") is False, f"{ticket.get('id')}: supportClaimAllowed must be false")
    require(isinstance(guard, dict), "supportGuard must be an object")
    for key, value in guard.items():
        require(value is False, f"support guard changed: {key}")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        closeout = load_json(M89_CLOSEOUT_JSON)
        selection = build_selection(registry, closeout)
        validate_selection(selection)
        write_json(OUTPUT_JSON, selection)
        OUTPUT_MD.write_text(render_markdown(selection), encoding="utf-8")
        validate_selection(load_json(OUTPUT_JSON))
    except AssertionError as error:
        print(f"m90_path_aa_slice: FAIL: {error}", file=sys.stderr)
        return 1
    print("M90 Path AA slice validation passed: pathAaRows=18 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
