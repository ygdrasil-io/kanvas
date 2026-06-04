#!/usr/bin/env python3
"""Generate and validate FOR-307 Path AA edge-budget candidate selection."""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-307"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-path-aa-edge-budget-candidate-selection-ticket"
)
ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "path-aa-edge-budget-candidate-selection-for307"
)
ARTIFACT = ARTIFACT_DIR / "path-aa-edge-budget-candidate-selection-for307.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-307-path-aa-edge-budget-candidate-selection.md"
)

DECISION_SELECTED = "PATH_AA_EDGE_BUDGET_CANDIDATE_SELECTED"
DECISION_UNSAFE = "PATH_AA_EDGE_BUDGET_POLICY_UNSAFE_PROMOTION_FOUND"
DECISION_AMBIGUOUS = "PATH_AA_EDGE_BUDGET_CANDIDATE_SELECTION_AMBIGUOUS"

STATIC_SCENES = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/data/scenes.json"
GENERATED_FILES = [
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
]

M37_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-05-28-m37-path-aa-breadth-audit.md"
M44_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-05-28-m44-path-aa-dashboard-evidence.md"
M49_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-05-31-m49-dashboard-gate-invariants.md"
FOR304_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-304-renderer-feature-conversion-wave-closeout.md"
)

EDGE_FALLBACK = "coverage.edge-count-exceeded"
STROKE_OUTLINE_FALLBACK = "coverage.stroke-outline-edge-count-exceeded"
SELECTED_CANDIDATE = "arc-stroke-hairline-subdivision-scout"

EXPECTED_STATIC_SENTINELS = {
    "path-aa-stroke-outline-fallback": STROKE_OUTLINE_FALLBACK,
    "path-aa-edge-budget-boundary": EDGE_FALLBACK,
}

EXPECTED_GENERATED_REFUSALS = {
    "path-aa-convexpaths-edge-budget": EDGE_FALLBACK,
    "path-aa-dashing-edge-budget": EDGE_FALLBACK,
    "m52-closed-capped-hairlines-edge-budget": EDGE_FALLBACK,
    "m54-dash-circle-boundary": EDGE_FALLBACK,
    "m66-path-aa-dashing-edge-budget-refusal": EDGE_FALLBACK,
}

M37_FAMILY_RANKS = [
    {
        "rank": 1,
        "family": "Stroke rectangle/circle",
        "disposition": "already-promoted",
        "reason": "M44 promoted path-aa-stroke-primitive for StrokeRectGM and StrokeCircleGM.",
    },
    {
        "rank": 2,
        "family": "Arc stroke/hairline",
        "disposition": "selected-candidate",
        "candidate": SELECTED_CANDIDATE,
        "reason": (
            "M37 ranks arc stroke/hairline directly after primitive strokes; it is narrower "
            "than dash/fill/composition packs but still needs curve-subdivision evidence."
        ),
    },
    {
        "rank": 3,
        "family": "General stroke/dash",
        "disposition": "rejected-for-this-ticket",
        "reason": "Dash/cap/join expansion combines unrelated behavior and must be split first.",
    },
    {
        "rank": 4,
        "family": "Fill/convex/path pack",
        "disposition": "rejected-for-this-ticket",
        "reason": "Broad GM packs include many shapes, convexity cases, and fill rules.",
    },
    {
        "rank": 5,
        "family": "Filter/shader over path",
        "disposition": "rejected-for-this-ticket",
        "reason": "Composition must wait for a stable base path coverage route.",
    },
    {
        "rank": 6,
        "family": "Benchmark stress",
        "disposition": "rejected-for-this-ticket",
        "reason": "Stress rows are performance signals, not support conversion candidates.",
    },
]

REQUIRED_FUTURE_PROMOTION_PROOF = [
    "row-specific geometry",
    "Skia/reference artifact",
    "CPU artifact",
    "adapter-backed GPU artifact",
    "CPU/GPU diff and stats",
    "route diagnostics with edge-count and fallback fields",
    "fallback policy preserving refusals outside the selected row",
    "no global edge-budget increase",
    "no threshold weakening",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-307 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing report: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def scenes_from(path: Path) -> list[dict[str, Any]]:
    data = load_json(path)
    scenes = data.get("scenes")
    if not isinstance(scenes, list):
        fail(f"{rel(path)} must contain a scenes list")
    rows: list[dict[str, Any]] = []
    for scene in scenes:
        if not isinstance(scene, dict):
            fail(f"{rel(path)} scene entries must be objects")
        row = scene.get("row", scene)
        if not isinstance(row, dict):
            fail(f"{rel(path)} row entries must be objects")
        rows.append(row)
    return rows


def fallback_reason(row: dict[str, Any]) -> str | None:
    direct = row.get("fallbackReason")
    if isinstance(direct, str):
        return direct
    gpu = row.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict):
            nested = route.get("fallbackReason")
            if isinstance(nested, str):
                return nested
    return None


def require_scene_fallback(
    rows: list[dict[str, Any]],
    expected: dict[str, str],
    *,
    source: str,
) -> list[dict[str, str]]:
    by_id = {row.get("id"): row for row in rows if isinstance(row.get("id"), str)}
    evidence: list[dict[str, str]] = []
    for scene_id, expected_reason in expected.items():
        row = by_id.get(scene_id)
        if row is None:
            fail(f"{source} missing expected Path AA row {scene_id}")
        status = row.get("status")
        actual_reason = fallback_reason(row)
        if status != "expected-unsupported":
            fail(f"{scene_id} must remain expected-unsupported, got {status}")
        if actual_reason != expected_reason:
            fail(f"{scene_id} fallback expected {expected_reason}, got {actual_reason}")
        evidence.append(
            {
                "id": scene_id,
                "source": source,
                "status": status,
                "fallbackReason": actual_reason,
            }
        )
    return evidence


def validate_reports() -> dict[str, Any]:
    m37 = read_text(M37_REPORT)
    m44 = read_text(M44_REPORT)
    m49 = read_text(M49_REPORT)
    for304 = read_text(FOR304_REPORT)

    required_m37 = [
        "Arc stroke/hairline",
        "Rank next after strokes",
        "Stroke rectangle/circle",
        "General stroke/dash",
        "Fill/convex/path pack",
        "Filter/shader over path",
        "Benchmark stress",
        EDGE_FALLBACK,
    ]
    for needle in required_m37:
        if needle not in m37:
            fail(f"M37 report missing required signal `{needle}`")

    required_m44 = [
        "path-aa-stroke-primitive",
        "StrokeRectGM",
        "StrokeCircleGM",
        f"{EDGE_FALLBACK}` 50 -> 46",
    ]
    for needle in required_m44:
        if needle not in m44:
            fail(f"M44 report missing primitive-stroke promotion signal `{needle}`")

    for scene_id, reason in {
        **EXPECTED_STATIC_SENTINELS,
        "path-aa-convexpaths-edge-budget": EDGE_FALLBACK,
        "path-aa-dashing-edge-budget": EDGE_FALLBACK,
    }.items():
        if scene_id not in m49:
            fail(f"M49 invariant report missing stable Path AA row `{scene_id}`")
        if reason not in m49:
            fail(f"M49 invariant report missing stable fallback `{reason}`")

    if "Path AA edge-budget candidate selection" not in for304:
        fail("FOR-304 closeout does not list Path AA candidate selection as next backlog")
    if "no global budget weakening" not in for304:
        fail("FOR-304 closeout does not preserve the no-global-budget-weakening gate")

    return {
        "m37": rel(M37_REPORT),
        "m44": rel(M44_REPORT),
        "m49": rel(M49_REPORT),
        "for304": rel(FOR304_REPORT),
    }


def classify_policy_case(
    *,
    candidate: str | None,
    already_promoted_primitive: bool,
    broad_pack: bool,
    changes_global_budget: bool,
    changes_threshold: bool,
    changes_fallback_or_status: bool,
    has_complete_rendered_proof: bool,
) -> tuple[str, bool, str]:
    if changes_global_budget or changes_threshold or changes_fallback_or_status:
        return (
            "forbidden",
            False,
            "Policy changes to budget, threshold, fallback, or scene status are forbidden here.",
        )
    if broad_pack:
        return (
            "forbidden",
            False,
            "Broad Path AA packs cannot be selected before a narrower route is proven.",
        )
    if candidate != SELECTED_CANDIDATE:
        return (
            "ambiguous",
            False,
            "Candidate selection must name the M37 rank-2 arc stroke/hairline scout.",
        )
    if not already_promoted_primitive:
        return (
            "ambiguous",
            False,
            "Arc stroke/hairline is only next after primitive stroke promotion is verified.",
        )
    if has_complete_rendered_proof:
        return (
            "future-promotion-candidate",
            True,
            "Complete rendered proof belongs in a future implementation ticket, not this selection ticket.",
        )
    return (
        "candidate-only",
        True,
        "Candidate selection is allowed because it preserves all current refusals and claims no support.",
    )


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "FOR-307 selection-only arc stroke/hairline candidate",
            "candidate": SELECTED_CANDIDATE,
            "already_promoted_primitive": True,
            "broad_pack": False,
            "changes_global_budget": False,
            "changes_threshold": False,
            "changes_fallback_or_status": False,
            "has_complete_rendered_proof": False,
            "expected": "candidate-only",
        },
        {
            "name": "Global edge-budget increase is forbidden",
            "candidate": SELECTED_CANDIDATE,
            "already_promoted_primitive": True,
            "broad_pack": False,
            "changes_global_budget": True,
            "changes_threshold": False,
            "changes_fallback_or_status": False,
            "has_complete_rendered_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Broad convex/fill/dash pack selection is forbidden",
            "candidate": "convexpaths-or-dashing-pack",
            "already_promoted_primitive": True,
            "broad_pack": True,
            "changes_global_budget": False,
            "changes_threshold": False,
            "changes_fallback_or_status": False,
            "has_complete_rendered_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Arc selection before primitive promotion is ambiguous",
            "candidate": SELECTED_CANDIDATE,
            "already_promoted_primitive": False,
            "broad_pack": False,
            "changes_global_budget": False,
            "changes_threshold": False,
            "changes_fallback_or_status": False,
            "has_complete_rendered_proof": False,
            "expected": "ambiguous",
        },
        {
            "name": "Rendered proof remains future implementation scope",
            "candidate": SELECTED_CANDIDATE,
            "already_promoted_primitive": True,
            "broad_pack": False,
            "changes_global_budget": False,
            "changes_threshold": False,
            "changes_fallback_or_status": False,
            "has_complete_rendered_proof": True,
            "expected": "future-promotion-candidate",
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_policy_case(
            candidate=case["candidate"],
            already_promoted_primitive=case["already_promoted_primitive"],
            broad_pack=case["broad_pack"],
            changes_global_budget=case["changes_global_budget"],
            changes_threshold=case["changes_threshold"],
            changes_fallback_or_status=case["changes_fallback_or_status"],
            has_complete_rendered_proof=case["has_complete_rendered_proof"],
        )
        if decision != case["expected"]:
            fail(f"policy case `{case['name']}` expected {case['expected']}, got {decision}")
        rows.append(
            {
                "name": case["name"],
                "decision": decision,
                "allowed": allowed,
                "reason": reason,
            }
        )
    return rows


def validate_selection() -> dict[str, Any]:
    source_reports = validate_reports()

    static_evidence = require_scene_fallback(
        scenes_from(STATIC_SCENES),
        EXPECTED_STATIC_SENTINELS,
        source=rel(STATIC_SCENES),
    )

    generated_rows: list[dict[str, Any]] = []
    for path in GENERATED_FILES:
        generated_rows.extend(scenes_from(path))
    generated_evidence = require_scene_fallback(
        generated_rows,
        EXPECTED_GENERATED_REFUSALS,
        source="reports/wgsl-pipeline/scenes/generated/*.json",
    )

    family_counter = Counter(item["disposition"] for item in M37_FAMILY_RANKS)
    if family_counter["selected-candidate"] != 1:
        fail("exactly one M37 family must be selected as the next candidate")
    selected_family = next(
        item for item in M37_FAMILY_RANKS if item["disposition"] == "selected-candidate"
    )
    if selected_family.get("candidate") != SELECTED_CANDIDATE:
        fail("selected M37 family does not name the expected candidate")

    policy_cases = validate_policy_cases()

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_SELECTED,
        "selectedCandidate": SELECTED_CANDIDATE,
        "candidateStatus": "candidate-only",
        "supportDecision": "KEEP_EXPECTED_UNSUPPORTED_UNTIL_ROW_LOCAL_PROOF",
        "sourceReports": source_reports,
        "familyRanking": M37_FAMILY_RANKS,
        "preservedFallbackEvidence": static_evidence + generated_evidence,
        "policyCases": policy_cases,
        "requiredFuturePromotionProof": REQUIRED_FUTURE_PROMOTION_PROOF,
        "forbiddenChanges": [
            "global edge-budget increase",
            "similarity-threshold weakening",
            "renderer/shader change in this selection ticket",
            "fallback reason or scene status relabel",
            "broad GM pack support claim",
        ],
        "alternateDecisions": {
            "unsafe": DECISION_UNSAFE,
            "ambiguous": DECISION_AMBIGUOUS,
        },
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    preserved_rows = "\n".join(
        "| `{id}` | `{status}` | `{fallbackReason}` | `{source}` |".format(**row)
        for row in data["preservedFallbackEvidence"]
    )
    ranking_rows = "\n".join(
        "| {rank} | {family} | `{disposition}` | {reason} |".format(**row)
        for row in data["familyRanking"]
    )
    policy_rows = "\n".join(
        "| {name} | `{decision}` | {allowed} | {reason} |".format(**row)
        for row in data["policyCases"]
    )
    proof_rows = "\n".join(f"- {item}" for item in data["requiredFuturePromotionProof"])

    report = f"""# FOR-307 Path AA Edge-Budget Candidate Selection

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-307 selects `{data["selectedCandidate"]}` as the next Path AA
edge-budget candidate, in candidate-only status. This ticket does not promote a
scene, change the renderer, change shader behavior, adjust thresholds, increase
the WebGPU edge budget, or relabel any fallback.

The selection is based on the existing M37 ranking plus the M44 evidence that
the first-ranked primitive stroke family is already promoted as
`path-aa-stroke-primitive`. The next bounded family is therefore arc
stroke/hairline, but it remains expected unsupported until a future
implementation ticket produces row-local rendered proof.

## Family Ranking

| Rank | Family | Disposition | Reason |
|---:|---|---|---|
{ranking_rows}

## Preserved Fallback Rows

| Scene id | Status | Fallback reason | Source |
|---|---|---|---|
{preserved_rows}

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
{policy_rows}

## Required Future Promotion Proof

{proof_rows}

## Non-Changes

- No renderer or shader code changed.
- No scene status changed.
- No fallback reason changed.
- No edge budget changed.
- No visual threshold changed.
- No broad GM pack is claimed as supported.

## Validation

- `rtk python3 scripts/validate_for307_path_aa_edge_budget_candidate_selection.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_selection()
    write_artifact(data)
    write_report(data)
    # Re-read generated files so formatting or accidental corruption fails in the same run.
    load_json(ARTIFACT)
    if DECISION_SELECTED not in read_text(REPORT):
        fail("report does not contain the selected decision")
    print(f"{LINEAR_ID}: {DECISION_SELECTED}")
    print(f"selectedCandidate={SELECTED_CANDIDATE}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
