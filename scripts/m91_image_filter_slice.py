#!/usr/bin/env python3
"""Generate and validate the M91 image-filter backlog slice from the M89 registry."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
M89_CLOSEOUT_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/closeout.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m91-image-filter-slice"
OUTPUT_JSON = OUTPUT_DIR / "selection.json"
OUTPUT_MD = OUTPUT_DIR / "selection.md"

EXPECTED_PASS_ROWS = [
    "crop-image-filter-nonnull-prepass",
    "image-filter-compose-cf-matrix-transform",
]
EXPECTED_POLICY_ROWS = [
    "skia-gm-imagemakewithfilter",
    "skia-gm-offsetimagefilter",
]
EXPECTED_PREPASS_ROWS = [
    "image-filter-crop-nonnull-prepass-required",
]
EXPECTED_POLICY_FALLBACKS = {
    "skia-gm-imagemakewithfilter": "image-filter.imagemakewithfilter.row-specific-artifacts-required",
    "skia-gm-offsetimagefilter": "image-filter.offset.row-specific-artifacts-required",
}
EXPECTED_PREPASS_FALLBACKS = {
    "image-filter-crop-nonnull-prepass-required": "image-filter.crop-input-nonnull-prepass-required",
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


def rows_by_id(registry: dict[str, Any]) -> dict[str, dict[str, Any]]:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    indexed: dict[str, dict[str, Any]] = {}
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        row_id = row.get("rowId")
        require(isinstance(row_id, str) and row_id, "registry row missing rowId")
        indexed[row_id] = row
    return indexed


def require_m89_closeout_handoff(closeout: dict[str, Any]) -> None:
    require(closeout.get("classification") == "pm-closeout-no-new-rendering-support", "M89 closeout classification mismatch")
    require(closeout.get("readiness", {}).get("readinessDelta") == 0.0, "M89 closeout readinessDelta changed")
    slices = closeout.get("nextRecommendedSlices")
    require(isinstance(slices, list), "M89 closeout nextRecommendedSlices must be a list")
    m91 = next((item for item in slices if isinstance(item, dict) and item.get("milestone") == "M91"), None)
    require(isinstance(m91, dict), "M89 closeout missing M91 handoff")
    require(m91.get("family") == "image-filter", "M89 closeout M91 handoff must target image-filter")
    require(m91.get("supportClaimAllowedFromCloseout") is False, "M89 closeout must not authorize M91 support claims")


def summarize_rows(by_id: dict[str, dict[str, Any]], row_ids: list[str]) -> list[dict[str, Any]]:
    summary: list[dict[str, Any]] = []
    for row_id in row_ids:
        row = by_id.get(row_id)
        require(isinstance(row, dict), f"missing M89 registry row: {row_id}")
        summary.append(
            {
                "rowId": row_id,
                "family": row.get("family"),
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
    require(counters.get("supportClaims") == 22, "M91 slice cannot change M89 supportClaims")
    require(counters.get("unlinkedUnsupportedRows") == 0, "M91 slice requires M89 unlinkedUnsupportedRows=0")
    require(counters.get("family", {}).get("image-filter") == 5, "M89 image-filter row count changed")

    by_id = rows_by_id(registry)
    pass_rows = summarize_rows(by_id, EXPECTED_PASS_ROWS)
    policy_rows = summarize_rows(by_id, EXPECTED_POLICY_ROWS)
    prepass_rows = summarize_rows(by_id, EXPECTED_PREPASS_ROWS)

    for item in pass_rows:
        require(item["family"] == "image-filter", f"{item['rowId']} family changed")
        require(item["status"] == "pass", f"{item['rowId']} must remain pass baseline")
        require(item["supportClaim"] is True, f"{item['rowId']} pass baseline must retain supportClaim=true")
        require(item["fallbackReason"] == "none", f"{item['rowId']} pass fallback changed")
    for item in policy_rows:
        row = by_id[item["rowId"]]
        require(item["family"] == "image-filter", f"{item['rowId']} family changed")
        require(item["status"] == "expected-unsupported", f"{item['rowId']} must remain expected-unsupported")
        require(item["policyOnly"] is True, f"{item['rowId']} must remain policyOnly=true")
        require(item["supportClaim"] is False, f"{item['rowId']} must not become support")
        require(item["fallbackReason"] == EXPECTED_POLICY_FALLBACKS[item["rowId"]], f"{item['rowId']} fallback changed")
        require(len(row.get("rowSpecificRefusals", [])) == 1, f"{item['rowId']} missing rowSpecificRefusals")
        refusal = row["rowSpecificRefusals"][0]
        require(refusal.get("classification") == "row-specific-expected-unsupported-no-support-claim", f"{item['rowId']} refusal classification changed")
        require(refusal.get("fallbackReason") == EXPECTED_POLICY_FALLBACKS[item["rowId"]], f"{item['rowId']} refusal fallback changed")
        require(refusal.get("supportScoreIncreased") is False, f"{item['rowId']} refusal must not increase support score")
        require(refusal.get("referenceStatus") == "not-generated", f"{item['rowId']} reference status changed")
        require(refusal.get("diffStatsStatus") == "not-computed", f"{item['rowId']} diff status changed")
    for item in prepass_rows:
        row = by_id[item["rowId"]]
        require(item["family"] == "image-filter", f"{item['rowId']} family changed")
        require(item["status"] == "expected-unsupported", f"{item['rowId']} must remain expected-unsupported")
        require(item["supportClaim"] is False, f"{item['rowId']} must not become support")
        require(item["policyOnly"] is False, f"{item['rowId']} generated refusal must not become policy-only")
        require(item["fallbackReason"] == EXPECTED_PREPASS_FALLBACKS[item["rowId"]], f"{item['rowId']} fallback changed")
        links = row.get("imageFilterPrepassGateLinks")
        require(isinstance(links, list) and len(links) == 1, f"{item['rowId']} missing imageFilterPrepassGateLinks")
        gate = links[0]
        require(gate.get("classification") == "image-filter-prepass-gated-expected-unsupported-no-support-claim", f"{item['rowId']} gate classification changed")
        require(gate.get("fallbackReason") == EXPECTED_PREPASS_FALLBACKS[item["rowId"]], f"{item['rowId']} gate fallback changed")
        require(gate.get("generalDagCompilerAdded") is False, f"{item['rowId']} must not add general DAG compiler")
        require(gate.get("cpuReadbackFallbackAdded") is False, f"{item['rowId']} must not add CPU/readback fallback")
        require(gate.get("requiredSmokeCandidateAllowed") is False, f"{item['rowId']} must not enter required smoke")
        require(gate.get("supportScoreIncreased") is False, f"{item['rowId']} gate must not increase support score")

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m91_image_filter_slice.py",
        "milestone": "M91",
        "classification": "image-filter-backlog-selection-no-new-rendering-support",
        "status": "generated evidence",
        "inputs": {
            "registry": rel(REGISTRY_JSON),
            "m89Closeout": rel(M89_CLOSEOUT_JSON),
            "imageFilterSpec": ".upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md",
            "pmSpec": ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md",
        },
        "counters": {
            "imageFilterRows": 5,
            "existingPassRows": len(pass_rows),
            "policyOnlyRowSpecificRefusalRows": len(policy_rows),
            "prepassGateRefusalRows": len(prepass_rows),
            "newSupportClaims": 0,
            "readinessDelta": 0.0,
            "dashboardPromotions": 0,
            "thresholdChanges": 0,
        },
        "clusters": {
            "existingBoundedSupportBaseline": pass_rows,
            "policyOnlyRowSpecificRefusals": policy_rows,
            "prepassGateRefusals": prepass_rows,
        },
        "nextTickets": [
            {
                "id": "M91-IF-1",
                "type": "policy-visibility",
                "scope": "Keep ImageMakeWithFilterGM and OffsetImageFilterGM visible as row-specific expected-unsupported evidence until reference, CPU/GPU route, render, diff/stat, and graph ownership artifacts exist.",
                "rows": EXPECTED_POLICY_ROWS,
                "supportClaimAllowed": False,
            },
            {
                "id": "M91-IF-2",
                "type": "dependency-gate-proof",
                "scope": "Keep Crop(input=nonNull) blocked on explicit prepass/layer ownership evidence; do not add generic DAG compiler or CPU/readback fallback.",
                "rows": EXPECTED_PREPASS_ROWS,
                "supportClaimAllowed": False,
            },
            {
                "id": "M91-IF-3",
                "type": "bounded-promotion-candidate",
                "scope": "Only after graph dump, intermediate texture ownership, CPU/GPU/reference/diff artifacts, route diagnostics, and performance impact exist, evaluate one bounded image-filter DAG candidate.",
                "rows": EXPECTED_POLICY_ROWS + EXPECTED_PREPASS_ROWS,
                "supportClaimAllowed": False,
            },
        ],
        "supportGuard": {
            "supportClaimsChanged": False,
            "dashboardPromotions": False,
            "thresholdsChanged": False,
            "policyOnlyRowsPromoted": False,
            "belowThresholdCountedAsProductionGap": False,
            "generalImageFilterDagSupportClaimed": False,
            "cpuReadbackFallbackAdded": False,
            "ganeshPort": False,
            "graphitePort": False,
            "dynamicSkSLCompiler": False,
            "dynamicSkSLIR": False,
            "dynamicSkSLVM": False,
        },
        "validationCommands": [
            "rtk python3 scripts/m91_image_filter_slice.py",
            "rtk ./gradlew --no-daemon pipelineM91ImageFilterSlice",
            "rtk git diff --check",
        ],
    }


def render_markdown(selection: dict[str, Any]) -> str:
    counters = selection["counters"]
    lines = [
        "# M91 Image-Filter Backlog Slice",
        "",
        "Status: generated evidence",
        "",
        "This slice turns the M89 registry closeout M91 recommendation into an image-filter backlog contract. It does not promote support, add a generic DAG compiler, add CPU/readback fallback, weaken thresholds, or change render paths.",
        "",
        "## Counters",
        "",
        f"- Image-filter rows: `{counters['imageFilterRows']}`",
        f"- Existing pass baseline rows: `{counters['existingPassRows']}`",
        f"- Policy-only row-specific refusal rows: `{counters['policyOnlyRowSpecificRefusalRows']}`",
        f"- Prepass gate refusal rows: `{counters['prepassGateRefusalRows']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        f"- Dashboard promotions: `{counters['dashboardPromotions']}`",
        f"- Threshold changes: `{counters['thresholdChanges']}`",
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
    lines.extend(f"- {key}: `{value}`" for key, value in selection["supportGuard"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in selection["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_selection(selection: dict[str, Any]) -> None:
    require(selection.get("classification") == "image-filter-backlog-selection-no-new-rendering-support", "classification mismatch")
    counters = selection.get("counters")
    tickets = selection.get("nextTickets")
    guard = selection.get("supportGuard")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("imageFilterRows") == 5, "imageFilterRows changed")
    require(counters.get("existingPassRows") == 2, "existing pass count changed")
    require(counters.get("policyOnlyRowSpecificRefusalRows") == 2, "policy-only refusal count changed")
    require(counters.get("prepassGateRefusalRows") == 1, "prepass gate count changed")
    for field in ["newSupportClaims", "dashboardPromotions", "thresholdChanges"]:
        require(counters.get(field) == 0, f"{field} changed")
    require(counters.get("readinessDelta") == 0.0, "readinessDelta changed")
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
        print(f"m91_image_filter_slice: FAIL: {error}", file=sys.stderr)
        return 1
    print("M91 image-filter slice validation passed: imageFilterRows=5 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
