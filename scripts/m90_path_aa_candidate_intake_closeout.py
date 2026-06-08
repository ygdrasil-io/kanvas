#!/usr/bin/env python3
"""Generate and validate the M90 Path AA candidate-intake closeout."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
CANDIDATE_READINESS_JSON = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-readiness/summary.json"
OUTPUT_DIR = ROOT / "reports/wgsl-pipeline/m90-path-aa-candidate-intake-closeout"
CLOSEOUT_JSON = OUTPUT_DIR / "summary.json"
CLOSEOUT_MD = OUTPUT_DIR / "summary.md"

EXPECTED_ROWS = [
    ("M90-PAA-3A", "skia-gm-hairlines", "HairlinesGM", "m90-path-aa-hairlines-evidence-intake"),
    ("M90-PAA-3B", "skia-gm-strokerect", "StrokeRectGM", "m90-path-aa-strokerect-evidence-intake"),
    ("M90-PAA-3C", "skia-gm-thinstrokedrects", "ThinStrokedRectsGM", "m90-path-aa-thinstrokedrects-evidence-intake"),
    ("M90-PAA-3D", "skia-gm-strokedlines", "StrokedLinesGM", "m90-path-aa-strokedlines-evidence-intake"),
    ("M90-PAA-3E", "skia-gm-strokerects", "StrokeRectsGM", "m90-path-aa-strokerects-evidence-intake"),
    ("M90-PAA-3F", "skia-gm-hairmodes", "HairModesGM", "m90-path-aa-hairmodes-evidence-intake"),
    ("M90-PAA-3G", "skia-gm-scaledstrokes", "ScaledStrokesGM", "m90-path-aa-scaledstrokes-evidence-intake"),
    ("M90-PAA-3H", "skia-gm-dashing", "DashingGM", "m90-path-aa-dashing-evidence-intake"),
    ("M90-PAA-3I", "skia-gm-dashcubics", "DashCubicsGM", "m90-path-aa-dashcubics-evidence-intake"),
]
EXPECTED_NON_CLAIMS = {
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
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def validate_candidate_readiness(readiness: dict[str, Any]) -> dict[str, Any]:
    require(readiness.get("classification") == "path-aa-candidate-readiness-no-new-rendering-support", "candidate readiness classification changed")
    counters = readiness.get("counters")
    require(isinstance(counters, dict), "candidate readiness counters must be an object")
    require(counters.get("candidateRows") == 9, "candidate readiness row count changed")
    require(counters.get("readyForPromotionRows") == 0, "candidate readiness must not contain ready rows")
    require(counters.get("blockedByMissingEvidenceRows") == 9, "candidate readiness blocked row count changed")
    require(counters.get("newSupportClaims") == 0, "candidate readiness added support claims")
    require(counters.get("readinessDelta") == 0.0, "candidate readiness moved readiness")
    next_ticket = readiness.get("nextRecommendedTicket")
    require(isinstance(next_ticket, dict), "candidate readiness missing nextRecommendedTicket")
    require(next_ticket.get("id") == "M90-PAA-3A", "active next ticket changed")
    require(next_ticket.get("rowId") == "skia-gm-hairlines", "active next row changed")
    require(next_ticket.get("supportClaimAllowed") is False, "active next ticket must not allow support claims")
    require(next_ticket.get("promotionAllowedWithoutEvidence") is False, "active next ticket must block promotion without evidence")
    candidates = readiness.get("candidates")
    require(isinstance(candidates, list), "candidate readiness candidates must be a list")
    row_order = [item.get("rowId") for item in candidates if isinstance(item, dict)]
    require(row_order == [row_id for _, row_id, _, _ in EXPECTED_ROWS], "candidate row order changed")
    return next_ticket


def intake_path(directory: str) -> Path:
    return ROOT / "reports/wgsl-pipeline" / directory / "summary.json"


def validate_intake(ticket: str, row_id: str, source_gm: str, directory: str) -> dict[str, Any]:
    path = intake_path(directory)
    intake = load_json(path)
    require(intake.get("milestone") == "M90", f"{ticket}: milestone changed")
    require(intake.get("ticket") == ticket, f"{ticket}: ticket mismatch")
    require(intake.get("status") == "blocked-by-missing-row-specific-evidence", f"{ticket}: intake status changed")
    require(str(intake.get("classification", "")).endswith("-evidence-intake-no-new-rendering-support"), f"{ticket}: classification changed")

    row = intake.get("row")
    counters = intake.get("counters")
    required = intake.get("requiredEvidence")
    signals = intake.get("historicalSignals")
    guard = intake.get("supportGuard")
    next_ticket = intake.get("nextRecommendedTicket")
    upstream = intake.get("upstreamReadinessState")
    require(isinstance(row, dict), f"{ticket}: row must be an object")
    require(row.get("rowId") == row_id, f"{ticket}: rowId changed")
    require(row.get("sourceGm") == source_gm, f"{ticket}: sourceGm changed")
    require(row.get("status") == "expected-unsupported", f"{ticket}: row status must stay expected-unsupported")
    require(row.get("supportClaim") is False, f"{ticket}: supportClaim must stay false")
    require(row.get("policyOnly") is True, f"{ticket}: policyOnly must stay true")
    require(row.get("routeCpu") == "expected-unsupported", f"{ticket}: CPU route must stay expected-unsupported")
    require(row.get("routeGpu") == "expected-unsupported", f"{ticket}: GPU route must stay expected-unsupported")

    require(isinstance(counters, dict), f"{ticket}: counters must be an object")
    require(counters.get("requiredEvidenceItems") == 10, f"{ticket}: required evidence count changed")
    require(counters.get("presentEvidenceItems") == 0, f"{ticket}: present evidence count must stay zero")
    require(counters.get("missingEvidenceItems") == 10, f"{ticket}: missing evidence count changed")
    require(counters.get("newSupportClaims") == 0, f"{ticket}: new support claims changed")
    require(counters.get("readinessDelta") == 0.0, f"{ticket}: readiness delta changed")
    require(counters.get("promotionalHistoricalSignals") == 0, f"{ticket}: historical signals became promotional")

    require(isinstance(required, list) and len(required) == 10, f"{ticket}: required evidence list changed")
    require(all(isinstance(item, dict) and item.get("present") is False for item in required), f"{ticket}: required evidence must remain absent")
    require(all(isinstance(item, dict) and item.get("promotional") is False for item in required), f"{ticket}: required evidence cannot be promotional")
    require(all(isinstance(item, dict) and item.get("status") == "missing" for item in required), f"{ticket}: required evidence status must stay missing")

    require(isinstance(signals, list), f"{ticket}: historicalSignals must be a list")
    require(len(signals) == counters.get("historicalSignals"), f"{ticket}: historical signal count mismatch")
    require(all(isinstance(item, dict) and item.get("promotional") is False for item in signals), f"{ticket}: historical signals must stay non-promotional")

    require(guard == EXPECTED_NON_CLAIMS, f"{ticket}: support guard changed")
    require(isinstance(next_ticket, dict), f"{ticket}: nextRecommendedTicket must be an object")
    require(next_ticket.get("supportClaimAllowed") is False, f"{ticket}: next ticket cannot allow support claims")
    require(next_ticket.get("promotionAllowedWithoutEvidence") is False, f"{ticket}: next ticket cannot allow promotion without evidence")
    if ticket == "M90-PAA-3A" and upstream is None:
        upstream_state_present = False
    else:
        require(isinstance(upstream, dict), f"{ticket}: upstreamReadinessState must be an object")
        require(upstream.get("activeNextRecommendedTicket") == "M90-PAA-3A", f"{ticket}: active upstream ticket changed")
        require(upstream.get("activeNextRecommendedRow") == "skia-gm-hairlines", f"{ticket}: active upstream row changed")
        upstream_state_present = True

    return {
        "ticket": ticket,
        "rowId": row_id,
        "sourceGm": source_gm,
        "intake": rel(path),
        "status": row["status"],
        "supportClaim": row["supportClaim"],
        "policyOnly": row["policyOnly"],
        "requiredEvidenceItems": counters["requiredEvidenceItems"],
        "presentEvidenceItems": counters["presentEvidenceItems"],
        "missingEvidenceItems": counters["missingEvidenceItems"],
        "historicalSignals": counters["historicalSignals"],
        "promotionalHistoricalSignals": counters["promotionalHistoricalSignals"],
        "newSupportClaims": counters["newSupportClaims"],
        "readinessDelta": counters["readinessDelta"],
        "nextEvidenceTicket": next_ticket.get("id"),
        "upstreamReadinessStatePresent": upstream_state_present,
    }


def build_closeout(readiness: dict[str, Any]) -> dict[str, Any]:
    active_next = validate_candidate_readiness(readiness)
    rows = [validate_intake(ticket, row_id, source_gm, directory) for ticket, row_id, source_gm, directory in EXPECTED_ROWS]
    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m90_path_aa_candidate_intake_closeout.py",
        "classification": "path-aa-candidate-intake-closeout-no-new-rendering-support",
        "status": "generated evidence",
        "milestone": "M90",
        "scope": "M90-PAA-3 candidate intake closeout and strict handoff to row-specific evidence collection.",
        "inputs": {
            "candidateReadiness": rel(CANDIDATE_READINESS_JSON),
            "intakes": [row["intake"] for row in rows],
        },
        "counters": {
            "candidateRows": len(rows),
            "intakeReports": len(rows),
            "expectedUnsupportedRows": sum(1 for row in rows if row["status"] == "expected-unsupported"),
            "policyOnlyRows": sum(1 for row in rows if row["policyOnly"] is True),
            "supportClaims": sum(1 for row in rows if row["supportClaim"] is True),
            "requiredEvidenceItems": sum(row["requiredEvidenceItems"] for row in rows),
            "presentEvidenceItems": sum(row["presentEvidenceItems"] for row in rows),
            "missingEvidenceItems": sum(row["missingEvidenceItems"] for row in rows),
            "historicalSignals": sum(row["historicalSignals"] for row in rows),
            "promotionalHistoricalSignals": sum(row["promotionalHistoricalSignals"] for row in rows),
            "newSupportClaims": sum(row["newSupportClaims"] for row in rows),
            "readinessDelta": sum(row["readinessDelta"] for row in rows),
        },
        "rows": rows,
        "activeNextRecommendedTicket": {
            "id": active_next["id"],
            "rowId": active_next["rowId"],
            "scope": active_next["scope"],
            "supportClaimAllowed": False,
            "promotionAllowedWithoutEvidence": False,
        },
        "nextHandoff": {
            "id": "M90-PAA-3A-REF",
            "rowId": "skia-gm-hairlines",
            "scope": "Produce row-specific HairlinesGM reference, CPU/WebGPU fallbackReason=none route, render, diff/stat, and performance evidence before any support evaluation.",
            "supportClaimAllowedFromCloseout": False,
        },
        "supportGuard": {
            "supportClaimsChanged": False,
            "renderPathsChanged": False,
            "thresholdsChanged": False,
            "edgeBudgetChanged": False,
            "policyOnlyRowsPromoted": False,
            "belowThresholdCountedAsProductionGap": False,
            "readinessMoved": False,
        },
        "nonClaims": {
            "ganeshPort": False,
            "graphitePort": False,
            "dynamicSkSLCompiler": False,
            "dynamicSkSLIR": False,
            "dynamicSkSLVM": False,
            "broadPathAASupport": False,
            "broadDashSupport": False,
            "broadHairlineSupport": False,
            "broadStrokeSupport": False,
        },
        "validationCommands": [
            "rtk python3 scripts/m90_path_aa_candidate_intake_closeout.py",
            "rtk ./gradlew --no-daemon pipelineM90PathAaCandidateIntakeCloseout",
            "rtk git diff --check",
        ],
    }


def render_markdown(closeout: dict[str, Any]) -> str:
    counters = closeout["counters"]
    lines = [
        "# M90 Path AA Candidate Intake Closeout",
        "",
        "Status: generated evidence",
        "",
        "This closeout freezes the M90-PAA-3 candidate intake wave. It aggregates the nine policy-only intake reports and hands off to row-specific evidence collection without promoting rendering support.",
        "",
        "## Counters",
        "",
        f"- Candidate rows: `{counters['candidateRows']}`",
        f"- Intake reports: `{counters['intakeReports']}`",
        f"- Expected unsupported rows: `{counters['expectedUnsupportedRows']}`",
        f"- Policy-only rows: `{counters['policyOnlyRows']}`",
        f"- Support claims: `{counters['supportClaims']}`",
        f"- Required evidence items: `{counters['requiredEvidenceItems']}`",
        f"- Present evidence items: `{counters['presentEvidenceItems']}`",
        f"- Missing evidence items: `{counters['missingEvidenceItems']}`",
        f"- Historical signals: `{counters['historicalSignals']}`",
        f"- Promotional historical signals: `{counters['promotionalHistoricalSignals']}`",
        f"- New support claims: `{counters['newSupportClaims']}`",
        f"- Readiness delta: `{counters['readinessDelta']}`",
        "",
        "## Rows",
        "",
    ]
    for row in closeout["rows"]:
        lines.append(
            f"- `{row['ticket']}` / `{row['rowId']}` / `{row['sourceGm']}`: "
            f"status=`{row['status']}`, missingEvidence=`{row['missingEvidenceItems']}`, "
            f"historicalSignals=`{row['historicalSignals']}`, supportClaim=`{row['supportClaim']}`"
        )
    active = closeout["activeNextRecommendedTicket"]
    handoff = closeout["nextHandoff"]
    lines.extend(
        [
            "",
            "## Active Recommendation",
            "",
            f"- Active ticket: `{active['id']}`",
            f"- Active row: `{active['rowId']}`",
            f"- Scope: {active['scope']}",
            f"- Support claim allowed: `{active['supportClaimAllowed']}`",
            f"- Promotion allowed without evidence: `{active['promotionAllowedWithoutEvidence']}`",
            "",
            "## Next Handoff",
            "",
            f"- ID: `{handoff['id']}`",
            f"- Row: `{handoff['rowId']}`",
            f"- Scope: {handoff['scope']}",
            f"- Support claim allowed from closeout: `{handoff['supportClaimAllowedFromCloseout']}`",
            "",
            "## Support Guard",
            "",
        ]
    )
    lines.extend(f"- {key}: `{value}`" for key, value in closeout["supportGuard"].items())
    lines.extend(["", "## Non-Claims", ""])
    lines.extend(f"- {key}: `{value}`" for key, value in closeout["nonClaims"].items())
    lines.extend(["", "## Validation Commands", ""])
    lines.extend(f"- `{command}`" for command in closeout["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_closeout(closeout: dict[str, Any]) -> None:
    require(closeout.get("classification") == "path-aa-candidate-intake-closeout-no-new-rendering-support", "classification mismatch")
    counters = closeout.get("counters")
    rows = closeout.get("rows")
    guard = closeout.get("supportGuard")
    non_claims = closeout.get("nonClaims")
    handoff = closeout.get("nextHandoff")
    active = closeout.get("activeNextRecommendedTicket")
    require(isinstance(counters, dict), "counters must be an object")
    require(counters.get("candidateRows") == 9, "candidate row count changed")
    require(counters.get("intakeReports") == 9, "intake report count changed")
    require(counters.get("expectedUnsupportedRows") == 9, "expected unsupported count changed")
    require(counters.get("policyOnlyRows") == 9, "policy-only count changed")
    require(counters.get("supportClaims") == 0, "support claims changed")
    require(counters.get("requiredEvidenceItems") == 90, "required evidence total changed")
    require(counters.get("presentEvidenceItems") == 0, "present evidence total must stay zero")
    require(counters.get("missingEvidenceItems") == 90, "missing evidence total changed")
    require(counters.get("promotionalHistoricalSignals") == 0, "historical signals became promotional")
    require(counters.get("newSupportClaims") == 0, "new support claims changed")
    require(counters.get("readinessDelta") == 0.0, "readiness moved")
    require(isinstance(rows, list) and len(rows) == 9, "rows must list all nine intakes")
    require([row.get("ticket") for row in rows if isinstance(row, dict)] == [ticket for ticket, _, _, _ in EXPECTED_ROWS], "row ticket order changed")
    require(isinstance(active, dict) and active.get("id") == "M90-PAA-3A", "active next recommendation changed")
    require(isinstance(handoff, dict) and handoff.get("id") == "M90-PAA-3A-REF", "next handoff changed")
    require(handoff.get("supportClaimAllowedFromCloseout") is False, "closeout cannot authorize support claims")
    require(isinstance(guard, dict), "supportGuard must be an object")
    for key, value in guard.items():
        require(value is False, f"supportGuard changed: {key}")
    require(isinstance(non_claims, dict), "nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"nonClaim changed: {key}")


def main() -> int:
    try:
        readiness = load_json(CANDIDATE_READINESS_JSON)
        closeout = build_closeout(readiness)
        validate_closeout(closeout)
        write_json(CLOSEOUT_JSON, closeout)
        CLOSEOUT_MD.write_text(render_markdown(closeout), encoding="utf-8")
        reloaded = load_json(CLOSEOUT_JSON)
        validate_closeout(reloaded)
        require(CLOSEOUT_MD.read_text(encoding="utf-8") == render_markdown(reloaded), "summary markdown is not deterministic from summary json")
    except AssertionError as error:
        print(f"m90_path_aa_candidate_intake_closeout: FAIL: {error}", file=sys.stderr)
        return 1
    print("M90 Path AA candidate intake closeout validation passed: intakeReports=9 missingEvidenceItems=90 newSupportClaims=0 readinessDelta=0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
