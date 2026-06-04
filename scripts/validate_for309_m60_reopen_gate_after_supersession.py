#!/usr/bin/env python3
"""Generate and validate FOR-309 M60 reopen gate after supersession."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-309"
SCENE_ID = "m60-bounded-nested-rrect-clip"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-m60-reopen-gate-after-supersession-ticket"

SCENE_DIR = PROJECT_ROOT / f"reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}"
ARTIFACT = SCENE_DIR / "m60-reopen-gate-after-supersession-for309.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-309-m60-reopen-gate-after-supersession.md"

DECISION_APPLIED = "M60_REOPEN_GATE_AFTER_SUPERSESSION_APPLIED"
DECISION_UNSAFE = "M60_REOPEN_UNSAFE_PRE_SUPERSESSION_ACTION_FOUND"
DECISION_AMBIGUOUS = "M60_REOPEN_GATE_AMBIGUOUS_HYPOTHESIS_FOUND"

FALLBACK_REASON = "coverage.nested-clip-visual-parity-below-threshold"

FOR283 = SCENE_DIR / "m60-cpu-dispatch-blend-store-trace-for283.json"
FOR286 = SCENE_DIR / "m60-cpu-active-aa-difference-store-trace-for286.json"
FOR301 = SCENE_DIR / "m60-skaaclip-band-trace-for301.json"
FOR302 = SCENE_DIR / "m60-analytic-clip-model-reconciliation-for302.json"
FOR303 = SCENE_DIR / "m60-analytic-model-supersession-guard-for303.json"
M60_PACK = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json"

SOURCE_REPORTS = [
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-302-m60-analytic-clip-model-reconciliation.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-303-m60-analytic-model-supersession-guard.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-304-renderer-feature-conversion-wave-closeout.md",
]

HISTORICAL_ACTIONS = [
    {
        "linear": "FOR-283",
        "path": FOR283,
        "expectedDecision": "TARGETED_CORRECTION_POSSIBLE_AT_CPU_MASK_FILTER_DISPATCH_CALLSITE",
        "expectedNextAction": "PATCH_CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_DISPATCH_PAYLOAD_AND_REGENERATE_M60",
        "classification": "historical-pre-supersession-action",
        "reason": "Predates FOR-301/FOR-302 runtime reconciliation and cannot reopen M60 alone.",
    },
    {
        "linear": "FOR-286",
        "path": FOR286,
        "expectedDecision": "NEXT_FIX_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER_NOT_SRCOVER_OR_PAYLOAD",
        "expectedNextAction": "TARGET_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER",
        "classification": "historical-pre-supersession-action",
        "reason": "Predates FOR-301/FOR-302 runtime reconciliation and cannot reopen M60 alone.",
    },
]

REQUIRED_REOPEN_PROOF = [
    "post-FOR-302 renderer-side hypothesis",
    "explicit citation of FOR-301 runtime SkAAClip trace",
    "explicit citation of FOR-302 analytic/runtime reconciliation",
    "local pixel-level prediction that differs from the reconciled runtime model",
    "reference, CPU, GPU, diff, stats, and route diagnostics",
    "preserved fallback policy for non-selected rows",
    "no threshold weakening",
    "no use of FOR-293 as standalone oracle",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-309 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing report: {rel(path)}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def require_field(data: dict[str, Any], field: str, expected: str, *, source: str) -> None:
    actual = data.get(field)
    if actual != expected:
        fail(f"{source} expected {field}={expected}, got {actual}")


def validate_source_reports() -> dict[str, str]:
    expected_snippets = {
        SOURCE_REPORTS[0]: [
            "M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT",
            "Runtime contradiction",
            "M60 remains `expected-unsupported`",
        ],
        SOURCE_REPORTS[1]: [
            "future M60 promotion/support consumers must cite both",
            "Unsafe consumers: `0`",
            "Ambiguous consumers: `0`",
        ],
        SOURCE_REPORTS[2]: [
            "Only reopen if a new renderer-side causal hypothesis is available",
            "do not use FOR-293 as oracle",
        ],
    }
    summary: dict[str, str] = {}
    for path, snippets in expected_snippets.items():
        text = read_text(path)
        compact_text = compact(text)
        lower_text = compact_text.lower()
        for snippet in snippets:
            compact_snippet = compact(snippet)
            if (
                snippet not in text
                and compact_snippet not in compact_text
                and compact_snippet.lower() not in lower_text
            ):
                fail(f"{rel(path)} missing required snippet `{snippet}`")
        summary[rel(path)] = "verified"
    return summary


def validate_historical_actions() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for spec in HISTORICAL_ACTIONS:
        data = load_json(spec["path"])
        require_field(data, "decision", spec["expectedDecision"], source=rel(spec["path"]))
        require_field(data, "nextAction", spec["expectedNextAction"], source=rel(spec["path"]))
        require_field(data, "supportDecision", "KEEP_EXPECTED_UNSUPPORTED", source=rel(spec["path"]))
        if FALLBACK_REASON not in json.dumps(data):
            fail(f"{rel(spec['path'])} must preserve {FALLBACK_REASON}")
        rows.append(
            {
                "linear": spec["linear"],
                "path": rel(spec["path"]),
                "historicalDecision": spec["expectedDecision"],
                "historicalNextAction": spec["expectedNextAction"],
                "classification": spec["classification"],
                "reason": spec["reason"],
            }
        )
    return rows


def validate_supersession() -> dict[str, Any]:
    for301 = load_json(FOR301)
    for302 = load_json(FOR302)
    for303 = load_json(FOR303)
    require_field(for301, "decision", "SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE", source=rel(FOR301))
    require_field(for302, "decision", "M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT", source=rel(FOR302))
    require_field(for303, "decision", "M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED", source=rel(FOR303))
    require_field(for301, "supportDecision", "KEEP_EXPECTED_UNSUPPORTED", source=rel(FOR301))
    require_field(for302, "supportDecision", "KEEP_EXPECTED_UNSUPPORTED", source=rel(FOR302))
    policy = for303.get("policy")
    if not isinstance(policy, dict) or "FOR-301" not in json.dumps(policy) or "FOR-302" not in json.dumps(policy):
        fail("FOR-303 policy must cite FOR-301 and FOR-302")
    return {
        "for301": {
            "path": rel(FOR301),
            "decision": for301["decision"],
            "supportDecision": for301["supportDecision"],
        },
        "for302": {
            "path": rel(FOR302),
            "decision": for302["decision"],
            "supportDecision": for302["supportDecision"],
        },
        "for303": {
            "path": rel(FOR303),
            "decision": for303["decision"],
            "policy": policy,
        },
    }


def validate_dashboard_row() -> dict[str, str]:
    data = load_json(M60_PACK)
    scenes = data.get("scenes")
    if not isinstance(scenes, list):
        fail(f"{rel(M60_PACK)} must contain scenes")
    row = next((item for item in scenes if isinstance(item, dict) and item.get("id") == SCENE_ID), None)
    if row is None:
        fail(f"{SCENE_ID} missing from generated results")
    route = ((row.get("gpu") or {}).get("route") or {})
    reason = row.get("fallbackReason") or route.get("fallbackReason")
    if row.get("status") != "expected-unsupported":
        fail(f"{SCENE_ID} must remain expected-unsupported")
    if reason != FALLBACK_REASON:
        fail(f"{SCENE_ID} fallback expected {FALLBACK_REASON}, got {reason}")
    return {
        "id": SCENE_ID,
        "status": row["status"],
        "gpuRoute": row.get("gpuRoute") or route.get("selectedRoute", ""),
        "fallbackReason": reason,
    }


def classify_reopen_case(
    *,
    uses_pre_supersession_action: bool,
    cites_for301: bool,
    cites_for302: bool,
    has_new_renderer_hypothesis: bool,
    claims_support: bool,
    weakens_threshold: bool,
    complete_proof: bool,
) -> tuple[str, bool, str]:
    if weakens_threshold:
        return ("forbidden", False, "Threshold weakening cannot reopen M60.")
    if uses_pre_supersession_action and not (cites_for301 and cites_for302 and has_new_renderer_hypothesis):
        return (
            "forbidden",
            False,
            "Pre-supersession nextAction evidence cannot reopen M60 without FOR-301/FOR-302 and a new hypothesis.",
        )
    if claims_support and not complete_proof:
        return ("forbidden", False, "M60 support requires complete rendered evidence.")
    if has_new_renderer_hypothesis and not (cites_for301 and cites_for302):
        return ("ambiguous", False, "A post-supersession hypothesis must cite FOR-301 and FOR-302.")
    if not has_new_renderer_hypothesis:
        return (
            "keep-closed",
            True,
            "No post-supersession renderer hypothesis is available, so the gate keeps M60 closed.",
        )
    if not complete_proof:
        return (
            "future-audit-candidate",
            True,
            "A cited post-supersession hypothesis may open a future audit, but not a support claim.",
        )
    return (
        "future-promotion-candidate",
        True,
        "Complete proof belongs in a future implementation ticket, not this gate-only ticket.",
    )


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "FOR-309 gate keeps M60 closed without new hypothesis",
            "uses_pre_supersession_action": False,
            "cites_for301": True,
            "cites_for302": True,
            "has_new_renderer_hypothesis": False,
            "claims_support": False,
            "weakens_threshold": False,
            "complete_proof": False,
            "expected": "keep-closed",
        },
        {
            "name": "FOR-283/FOR-286 action alone is forbidden",
            "uses_pre_supersession_action": True,
            "cites_for301": False,
            "cites_for302": False,
            "has_new_renderer_hypothesis": False,
            "claims_support": False,
            "weakens_threshold": False,
            "complete_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Post-supersession hypothesis without FOR-301/FOR-302 is ambiguous",
            "uses_pre_supersession_action": False,
            "cites_for301": True,
            "cites_for302": False,
            "has_new_renderer_hypothesis": True,
            "claims_support": False,
            "weakens_threshold": False,
            "complete_proof": False,
            "expected": "ambiguous",
        },
        {
            "name": "Threshold weakening is forbidden",
            "uses_pre_supersession_action": False,
            "cites_for301": True,
            "cites_for302": True,
            "has_new_renderer_hypothesis": True,
            "claims_support": False,
            "weakens_threshold": True,
            "complete_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Support claim without complete proof is forbidden",
            "uses_pre_supersession_action": False,
            "cites_for301": True,
            "cites_for302": True,
            "has_new_renderer_hypothesis": True,
            "claims_support": True,
            "weakens_threshold": False,
            "complete_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Cited hypothesis can become future audit candidate",
            "uses_pre_supersession_action": False,
            "cites_for301": True,
            "cites_for302": True,
            "has_new_renderer_hypothesis": True,
            "claims_support": False,
            "weakens_threshold": False,
            "complete_proof": False,
            "expected": "future-audit-candidate",
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_reopen_case(
            uses_pre_supersession_action=case["uses_pre_supersession_action"],
            cites_for301=case["cites_for301"],
            cites_for302=case["cites_for302"],
            has_new_renderer_hypothesis=case["has_new_renderer_hypothesis"],
            claims_support=case["claims_support"],
            weakens_threshold=case["weakens_threshold"],
            complete_proof=case["complete_proof"],
        )
        if decision != case["expected"]:
            fail(f"policy case `{case['name']}` expected {case['expected']}, got {decision}")
        rows.append({"name": case["name"], "decision": decision, "allowed": allowed, "reason": reason})
    return rows


def validate_gate() -> dict[str, Any]:
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sceneId": SCENE_ID,
        "decision": DECISION_APPLIED,
        "supportDecision": "KEEP_M60_CLOSED_UNTIL_POST_SUPERSESSION_RENDERER_HYPOTHESIS",
        "sourceReports": validate_source_reports(),
        "dashboardRow": validate_dashboard_row(),
        "supersession": validate_supersession(),
        "historicalActions": validate_historical_actions(),
        "requiredFutureReopenProof": REQUIRED_REOPEN_PROOF,
        "policyCases": validate_policy_cases(),
        "alternateDecisions": {"unsafe": DECISION_UNSAFE, "ambiguous": DECISION_AMBIGUOUS},
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    action_rows = "\n".join(
        "| {linear} | `{historicalNextAction}` | `{classification}` | {reason} |".format(**row)
        for row in data["historicalActions"]
    )
    policy_rows = "\n".join(
        "| {name} | `{decision}` | {allowed} | {reason} |".format(**row)
        for row in data["policyCases"]
    )
    proof_rows = "\n".join(f"- {item}" for item in data["requiredFutureReopenProof"])
    dashboard = data["dashboardRow"]
    report = f"""# FOR-309 M60 Reopen Gate After Supersession

Linear: `{LINEAR_ID}`

Scene: `{SCENE_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-309 keeps M60 closed unless a future ticket brings a post-FOR-302
renderer-side hypothesis. Historical FOR-283/FOR-286 next actions remain useful
audit evidence, but they cannot reopen M60 alone because FOR-301/FOR-302
superseded the FOR-293 analytic visibility assumption.

No renderer, shader, runtime, `SkAAClip`, threshold, fallback, scene status, or
readiness score changes.

## Dashboard Row Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
| `{dashboard["id"]}` | `{dashboard["status"]}` | `{dashboard["gpuRoute"]}` | `{dashboard["fallbackReason"]}` |

## Supersession Sources

- FOR-301: `{data["supersession"]["for301"]["decision"]}`
- FOR-302: `{data["supersession"]["for302"]["decision"]}`
- FOR-303: `{data["supersession"]["for303"]["decision"]}`

## Historical Actions Quarantined

| Source | Historical next action | Classification | Reason |
|---|---|---|---|
{action_rows}

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
{policy_rows}

## Required Future Reopen Proof

{proof_rows}

## Validation

- `rtk python3 scripts/validate_for309_m60_reopen_gate_after_supersession.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_gate()
    write_artifact(data)
    write_report(data)
    load_json(ARTIFACT)
    if DECISION_APPLIED not in read_text(REPORT):
        fail("report does not contain the applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
