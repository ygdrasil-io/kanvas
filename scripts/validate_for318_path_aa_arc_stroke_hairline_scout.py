#!/usr/bin/env python3
"""Generate and validate FOR-318 Path AA arc stroke/hairline scout evidence."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-318"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-path-aa-arc-stroke-hairline-subdivision-scout-ticket"
)

FOR307_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "path-aa-edge-budget-candidate-selection-for307/"
    "path-aa-edge-budget-candidate-selection-for307.json"
)
FOR307_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-307-path-aa-edge-budget-candidate-selection.md"
)
M37_REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-05-28-m37-path-aa-breadth-audit.md"
M60_SPEC = (
    PROJECT_ROOT
    / ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
)
FIDELITY_SPEC = (
    PROJECT_ROOT
    / ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md"
)

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "path-aa-arc-stroke-hairline-scout-for318"
)
ARTIFACT = ARTIFACT_DIR / "path-aa-arc-stroke-hairline-scout-for318.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"
)

STATIC_SCENES = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/data/scenes.json"
GENERATED_FILES = [
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
]

DECISION_APPLIED = "PATH_AA_ARC_STROKE_HAIRLINE_SCOUT_APPLIED"
DECISION_BLOCKED = "PATH_AA_ARC_STROKE_HAIRLINE_SCOUT_BLOCKED"
DECISION_UNSAFE = "PATH_AA_ARC_STROKE_HAIRLINE_UNSAFE_SUPPORT_CLAIM_FOUND"

SELECTED_CANDIDATE = "arc-stroke-hairline-subdivision-scout"
EDGE_FALLBACK = "coverage.edge-count-exceeded"
STROKE_OUTLINE_FALLBACK = "coverage.stroke-outline-edge-count-exceeded"
CAP_JOIN_FALLBACK = "coverage.stroke-cap-join-visual-parity-below-threshold"
STROKE_WIDTH_MIN = 0.5
STROKE_WIDTH_MAX = 64.0
EDGE_BUDGET = 256

REQUIRED_CLASSIFICATIONS = [
    "arc subdivision",
    "cap/join behavior",
    "hairline strokeWidth=0",
    "edge budget",
    "reference provenance",
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

SUPPORTED_STATUSES = {
    "pass",
    "supported",
    "promoted",
    "gpu-supported",
    "support-promoted",
}

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

ARC_HAIRLINE_TEST_ROWS = [
    {
        "id": "addarc-webgpu",
        "test": "org.skia.gpu.webgpu.AddArcWebGpuTest#AddArcGM renders close to reference PNG on the GPU backend()",
        "gm": "AddArcGM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "cap/join behavior",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-broad-gm-row",
        "reason": "AddArcGM remains a broad arc row; it does not isolate cap behavior or edge count.",
    },
    {
        "id": "circular-arcs-hairline-webgpu",
        "test": "org.skia.gpu.webgpu.CircularArcsHairlineWebGpuTest#CircularArcsHairlineGM renders close to reference PNG on the GPU backend()",
        "gm": "CircularArcsHairlineGM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "hairline strokeWidth=0",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-by-m60-stroke-width-floor",
        "reason": "Hairline strokeWidth=0 is outside the M60 0.5 px to 64 px stroke-width budget.",
    },
    {
        "id": "circular-arcs-stroke-butt-webgpu",
        "test": "org.skia.gpu.webgpu.CircularArcsStrokeButtWebGpuTest#CircularArcsStrokeButtGM renders close to reference PNG on the GPU backend()",
        "gm": "CircularArcsStrokeButtGM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "cap/join behavior",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "selected-future-micro-target-source",
        "reason": "Butt-cap circular arcs are the narrowest named non-hairline arc stroke row in M37.",
    },
    {
        "id": "circular-arcs-stroke-round-webgpu",
        "test": "org.skia.gpu.webgpu.CircularArcsStrokeRoundWebGpuTest#CircularArcsStrokeRoundGM renders close to reference PNG on the GPU backend()",
        "gm": "CircularArcsStrokeRoundGM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "cap/join behavior",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "defer-cap-join-risk",
        "reason": "Round cap behavior should wait until the butt-cap subdivision probe has proof.",
    },
    {
        "id": "circular-arcs-stroke-square-webgpu",
        "test": "org.skia.gpu.webgpu.CircularArcsStrokeSquareWebGpuTest#CircularArcsStrokeSquareGM renders close to reference PNG on the GPU backend()",
        "gm": "CircularArcsStrokeSquareGM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "cap/join behavior",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "defer-cap-join-risk",
        "reason": "Square cap inflation should not be mixed into the first arc subdivision probe.",
    },
    {
        "id": "crbug1472747-webgpu",
        "test": "org.skia.gpu.webgpu.Crbug1472747WebGpuTest#Crbug1472747GM renders close to reference PNG on the GPU backend()",
        "gm": "Crbug1472747GM",
        "sourceRoute": "WebGPU GM",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "CPU/reference PNG oracle for WebGPU GM from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-regression-fixture",
        "reason": "Regression GM is useful evidence but too broad for the first micro-target.",
    },
    {
        "id": "addarc-crossbackend",
        "test": "org.skia.gpu.webgpu.crossbackend.AddArcCrossBackendTest#AddArcGM matches reference on raster and GPU backends()",
        "gm": "AddArcGM",
        "sourceRoute": "cross-backend",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "Raster CPU backend in cross-backend oracle from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "cap/join behavior",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-broad-gm-row",
        "reason": "Cross-backend AddArc confirms provenance but not a bounded support slice.",
    },
    {
        "id": "circular-arcs-hairline-crossbackend",
        "test": "org.skia.gpu.webgpu.crossbackend.CircularArcsHairlineCrossBackendTest#CircularArcsHairlineGM matches reference on raster and GPU backends()",
        "gm": "CircularArcsHairlineGM",
        "sourceRoute": "cross-backend",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "Raster CPU backend in cross-backend oracle from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "hairline strokeWidth=0",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-by-m60-stroke-width-floor",
        "reason": "Cross-backend hairline evidence remains outside the selected bounded stroke-width policy.",
    },
    {
        "id": "crbug1472747-crossbackend",
        "test": "org.skia.gpu.webgpu.crossbackend.Crbug1472747CrossBackendTest#Crbug1472747GM matches reference on raster and GPU backends()",
        "gm": "Crbug1472747GM",
        "sourceRoute": "cross-backend",
        "currentFallbackReason": EDGE_FALLBACK,
        "currentSupportStatus": "expected-unsupported",
        "referenceKind": "skia-upstream",
        "referenceProvenance": "Raster CPU backend in cross-backend oracle from M37 inventory.",
        "classifications": [
            "arc subdivision",
            "edge budget",
            "reference provenance",
        ],
        "scoutDisposition": "blocked-regression-fixture",
        "reason": "Cross-backend regression row is not isolated enough for first implementation.",
    },
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-318 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing file: {rel(path)}")
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


def validate_for307_contract() -> dict[str, Any]:
    data = load_json(FOR307_ARTIFACT)
    report = read_text(FOR307_REPORT)
    if data.get("linear") != "FOR-307":
        fail("FOR-307 artifact has unexpected linear id")
    if data.get("selectedCandidate") != SELECTED_CANDIDATE:
        fail("FOR-307 no longer selects arc-stroke-hairline-subdivision-scout")
    if data.get("candidateStatus") != "candidate-only":
        fail("FOR-307 selected candidate must remain candidate-only")
    if data.get("supportDecision") != "KEEP_EXPECTED_UNSUPPORTED_UNTIL_ROW_LOCAL_PROOF":
        fail("FOR-307 support decision changed away from row-local proof requirement")
    if SELECTED_CANDIDATE not in report:
        fail("FOR-307 report no longer names the selected arc stroke/hairline scout")
    if "No edge budget changed." not in report:
        fail("FOR-307 report no longer preserves the edge-budget non-change")

    required = set(REQUIRED_FUTURE_PROMOTION_PROOF)
    actual = data.get("requiredFuturePromotionProof")
    if not isinstance(actual, list) or set(actual) != required:
        fail("FOR-307 future proof list no longer matches expected row-local proof")

    forbidden = data.get("forbiddenChanges")
    if not isinstance(forbidden, list):
        fail("FOR-307 artifact missing forbiddenChanges")
    for needle in [
        "global edge-budget increase",
        "similarity-threshold weakening",
        "fallback reason or scene status relabel",
    ]:
        if needle not in forbidden:
            fail(f"FOR-307 artifact no longer forbids `{needle}`")

    return {
        "artifact": rel(FOR307_ARTIFACT),
        "report": rel(FOR307_REPORT),
        "selectedCandidate": data["selectedCandidate"],
        "candidateStatus": data["candidateStatus"],
        "supportDecision": data["supportDecision"],
    }


def validate_source_reports() -> dict[str, str]:
    m37 = read_text(M37_REPORT)
    m60 = read_text(M60_SPEC)
    fidelity = read_text(FIDELITY_SPEC)

    for row in ARC_HAIRLINE_TEST_ROWS:
        if row["test"] not in m37:
            fail(f"M37 report missing arc/hairline test row `{row['test']}`")
        if row["gm"] not in m37:
            fail(f"M37 report missing GM `{row['gm']}`")

    for needle in [
        "| 2 | Arc stroke/hairline | 9 |",
        "curve subdivision and cap behavior need dedicated acceptance bounds",
        EDGE_FALLBACK,
    ]:
        if needle not in m37:
            fail(f"M37 report missing required scout signal `{needle}`")

    for needle in [
        "| Stroke width range | 0.5 px to 64 px | Excludes hairline",
        "| Coverage edge count | <= 256 edges |",
        "no broad Path AA support claim",
    ]:
        if needle not in m60:
            fail(f"M60 feature spec missing required budget/support signal `{needle}`")

    for needle in [
        "Reference Provenance Policy",
        "A promoted support row must include:",
        "reference artifact",
        "CPU artifact",
        "GPU artifact when GPU-eligible",
        "diff and stats artifacts",
    ]:
        if needle not in fidelity:
            fail(f"fidelity spec missing promotion proof signal `{needle}`")

    return {
        "m37": rel(M37_REPORT),
        "m60FeatureSpec": rel(M60_SPEC),
        "fidelitySpec": rel(FIDELITY_SPEC),
    }


def validate_preserved_fallbacks() -> list[dict[str, str]]:
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

    return static_evidence + generated_evidence


def proof_is_complete(proof: dict[str, Any]) -> bool:
    return all(proof.get(item) is True for item in REQUIRED_FUTURE_PROMOTION_PROOF)


def validate_no_unsafe_support_claim(rows: list[dict[str, Any]]) -> None:
    for row in rows:
        status = str(row.get("currentSupportStatus", "")).lower()
        disposition = str(row.get("scoutDisposition", "")).lower()
        proof = row.get("promotionProof", {})
        if not isinstance(proof, dict):
            fail(f"{row.get('id')} promotionProof must be an object")
        if status in SUPPORTED_STATUSES or disposition in SUPPORTED_STATUSES:
            if not proof_is_complete(proof):
                fail(
                    f"{row.get('id')} claims support without complete "
                    "reference/CPU/GPU/diff/stats/routes proof"
                )


def build_risk_classifications(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    classifications: list[dict[str, Any]] = [
        {
            "label": "arc subdivision",
            "decision": "requires-isolated-subdivision-bound",
            "rowIds": [row["id"] for row in rows if "arc subdivision" in row["classifications"]],
            "reason": "Every M37 arc/hairline row exceeds the current edge budget through arc subdivision breadth.",
        },
        {
            "label": "cap/join behavior",
            "decision": "split-before-round-or-square-cap-promotion",
            "rowIds": [row["id"] for row in rows if "cap/join behavior" in row["classifications"]],
            "reason": "Butt, round, and square cap rows must not be promoted as one broad cap/join claim.",
        },
        {
            "label": "hairline strokeWidth=0",
            "decision": "blocked-by-current-m60-stroke-width-budget",
            "rowIds": [
                row["id"] for row in rows if "hairline strokeWidth=0" in row["classifications"]
            ],
            "reason": "M60 starts at strokeWidth >= 0.5 px, so hairline remains explicit future scope.",
        },
        {
            "label": "edge budget",
            "decision": "preserve-256-edge-budget-and-current-refusals",
            "rowIds": [row["id"] for row in rows if "edge budget" in row["classifications"]],
            "reason": "FOR-318 is reporting-only and cannot increase the WebGPU AA edge budget.",
        },
        {
            "label": "reference provenance",
            "decision": "requires-skia-reference-plus-cpu-and-adapter-backed-gpu-proof",
            "rowIds": [row["id"] for row in rows if "reference provenance" in row["classifications"]],
            "reason": "Skia-like fidelity movement requires row-local reference, CPU, GPU, diff, stats, and route artifacts.",
        },
    ]
    for item in classifications:
        if not item["rowIds"]:
            fail(f"classification `{item['label']}` must classify at least one row")
    return classifications


def build_candidate_rows() -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    empty_proof = {item: False for item in REQUIRED_FUTURE_PROMOTION_PROOF}
    for source in ARC_HAIRLINE_TEST_ROWS:
        row = dict(source)
        row["promotionProof"] = dict(empty_proof)
        rows.append(row)
    validate_no_unsafe_support_claim(rows)
    return rows


def build_artifact() -> dict[str, Any]:
    for307 = validate_for307_contract()
    source_reports = validate_source_reports()
    preserved_fallbacks = validate_preserved_fallbacks()
    rows = build_candidate_rows()
    classifications = build_risk_classifications(rows)

    micro_target = {
        "id": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
        "sourceRowId": "circular-arcs-stroke-butt-webgpu",
        "sourceGm": "CircularArcsStrokeButtGM",
        "status": "selected-future-micro-target",
        "supportStatus": "not-supported",
        "scope": [
            "single bounded circular arc stroke derived from CircularArcsStrokeButtGM",
            "non-hairline strokeWidth within 0.5 px to 64 px",
            "butt cap only; round and square cap behavior deferred",
            "coverageEdgeCount must stay <= 256 before any support ticket starts",
        ],
        "blockedUntil": REQUIRED_FUTURE_PROMOTION_PROOF,
        "stableFallbackIfNotIsolated": EDGE_FALLBACK,
        "reason": (
            "This is the narrowest named non-hairline arc stroke source in M37; it "
            "can become an implementation ticket only after a bounded fixture proves "
            "edge count, reference provenance, CPU/GPU rendering, diff/stats, and routes."
        ),
    }

    support_guard = {
        "supportDecision": "MICRO_TARGET_SELECTED_SUPPORT_BLOCKED_UNTIL_ROW_LOCAL_PROOF",
        "currentSupportClaim": "none",
        "unsafeDecision": DECISION_UNSAFE,
        "requiredCompleteProof": REQUIRED_FUTURE_PROMOTION_PROOF,
        "supportedStatusesThatRequireCompleteProof": sorted(SUPPORTED_STATUSES),
    }

    budget_policy = {
        "webgpuAaEdgeBudget": EDGE_BUDGET,
        "edgeBudgetMayIncrease": False,
        "strokeWidthBudget": {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX},
        "hairlineStrokeWidth0Supported": False,
        "fallbackWeakeningAllowed": False,
        "thresholdWeakeningAllowed": False,
        "rendererOrShaderChangeAllowed": False,
        "releaseGateChangeAllowed": False,
        "readinessScoreChangeAllowed": False,
        "preservedFallbackReasons": sorted({EDGE_FALLBACK, STROKE_OUTLINE_FALLBACK, CAP_JOIN_FALLBACK}),
    }

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "follows": {
            "for307": for307,
            "m37": source_reports["m37"],
        },
        "sourceReports": source_reports,
        "selectedCandidate": SELECTED_CANDIDATE,
        "scoutStatus": "reporting-only",
        "supportGuard": support_guard,
        "budgetPolicy": budget_policy,
        "candidateRows": rows,
        "riskClassifications": classifications,
        "futureMicroTarget": micro_target,
        "preservedFallbackEvidence": preserved_fallbacks,
        "nonChanges": [
            "no renderer or shader code changed",
            "no scene status changed",
            "no fallback reason changed",
            "no edge budget changed",
            "no visual threshold changed",
            "no readiness score changed",
            "no release gate changed",
            "no arc/hairline support claimed",
        ],
        "alternateDecisions": {
            "blocked": DECISION_BLOCKED,
            "unsafe": DECISION_UNSAFE,
        },
    }


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("decision") != DECISION_APPLIED:
        fail("artifact does not record the applied scout decision")
    if data.get("selectedCandidate") != SELECTED_CANDIDATE:
        fail("artifact selectedCandidate changed unexpectedly")
    if data.get("scoutStatus") != "reporting-only":
        fail("FOR-318 must remain reporting-only")

    budget = data.get("budgetPolicy")
    if not isinstance(budget, dict):
        fail("budgetPolicy must be an object")
    expected_budget = {
        "webgpuAaEdgeBudget": EDGE_BUDGET,
        "edgeBudgetMayIncrease": False,
        "hairlineStrokeWidth0Supported": False,
        "fallbackWeakeningAllowed": False,
        "thresholdWeakeningAllowed": False,
        "rendererOrShaderChangeAllowed": False,
        "releaseGateChangeAllowed": False,
        "readinessScoreChangeAllowed": False,
    }
    for key, expected in expected_budget.items():
        if budget.get(key) != expected:
            fail(f"budgetPolicy.{key} expected {expected}, got {budget.get(key)}")
    stroke_width = budget.get("strokeWidthBudget")
    if stroke_width != {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX}:
        fail("strokeWidthBudget was weakened or malformed")

    rows = data.get("candidateRows")
    if not isinstance(rows, list) or len(rows) != 9:
        fail("candidateRows must list the 9 M37 arc stroke/hairline rows")
    validate_no_unsafe_support_claim(rows)

    labels = {
        item.get("label")
        for item in data.get("riskClassifications", [])
        if isinstance(item, dict)
    }
    missing = set(REQUIRED_CLASSIFICATIONS) - labels
    if missing:
        fail(f"missing risk classifications: {sorted(missing)}")

    micro_target = data.get("futureMicroTarget")
    if not isinstance(micro_target, dict):
        fail("futureMicroTarget must be an object")
    if micro_target.get("supportStatus") != "not-supported":
        fail("future micro-target must not claim support")
    if micro_target.get("blockedUntil") != REQUIRED_FUTURE_PROMOTION_PROOF:
        fail("future micro-target must be blocked on the full proof list")


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    row_lines = "\n".join(
        "| `{id}` | `{gm}` | `{currentSupportStatus}` | `{currentFallbackReason}` | `{scoutDisposition}` |".format(
            **row
        )
        for row in data["candidateRows"]
    )
    class_lines = "\n".join(
        "| {label} | `{decision}` | {count} | {reason} |".format(
            label=item["label"],
            decision=item["decision"],
            count=len(item["rowIds"]),
            reason=item["reason"],
        )
        for item in data["riskClassifications"]
    )
    fallback_lines = "\n".join(
        "| `{id}` | `{status}` | `{fallbackReason}` | `{source}` |".format(**row)
        for row in data["preservedFallbackEvidence"]
    )
    micro_target = data["futureMicroTarget"]
    proof_lines = "\n".join(f"- {item}" for item in REQUIRED_FUTURE_PROMOTION_PROOF)
    non_change_lines = "\n".join(f"- {item}" for item in data["nonChanges"])

    report = f"""# FOR-318 Path AA Arc Stroke/Hairline Scout

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-318 converts the FOR-307 `{data["selectedCandidate"]}` selection into a
reporting-only scout dossier. It selects one future micro-target source,
`{micro_target["sourceGm"]}`, but it does not promote support, change renderer
behavior, change shader behavior, adjust thresholds, increase the WebGPU edge
budget, relabel fallbacks, move readiness, or change release gates.

The selected future micro-target is `{micro_target["id"]}`. It is scoped to a
single bounded non-hairline butt-cap circular arc probe derived from
`{micro_target["sourceGm"]}`. It remains `not-supported` and blocked until the
full proof list below exists. If the row cannot be isolated under the existing
256-edge budget, the stable decision is to keep `{EDGE_FALLBACK}`.

## Source Linkage

- FOR-307 artifact: `{data["follows"]["for307"]["artifact"]}`
- FOR-307 report: `{data["follows"]["for307"]["report"]}`
- M37 audit: `{data["sourceReports"]["m37"]}`
- M60 feature spec: `{data["sourceReports"]["m60FeatureSpec"]}`
- Fidelity spec: `{data["sourceReports"]["fidelitySpec"]}`

## Arc/Hairline Candidate Rows

| Row id | GM | Status | Fallback | Scout disposition |
|---|---|---|---|---|
{row_lines}

## Risk Classification

| Classification | Decision | Rows | Reason |
|---|---|---:|---|
{class_lines}

## Future Micro-Target

| Field | Value |
|---|---|
| id | `{micro_target["id"]}` |
| source row | `{micro_target["sourceRowId"]}` |
| source GM | `{micro_target["sourceGm"]}` |
| status | `{micro_target["status"]}` |
| support status | `{micro_target["supportStatus"]}` |
| stable fallback if not isolated | `{micro_target["stableFallbackIfNotIsolated"]}` |

## Required Future Promotion Proof

{proof_lines}

## Preserved Fallback Rows

| Scene id | Status | Fallback reason | Source |
|---|---|---|---|
{fallback_lines}

## Non-Changes

{non_change_lines}

## Validation

- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk python3 scripts/validate_for307_path_aa_edge_budget_candidate_selection.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null`
- `rtk git diff --check`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    write_report(data)

    generated = load_json(ARTIFACT)
    validate_artifact_shape(generated)
    report = read_text(REPORT)
    for needle in [
        DECISION_APPLIED,
        "reporting-only scout dossier",
        "not-supported",
        EDGE_FALLBACK,
        "no arc/hairline support claimed",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    if re.search(r"\b(readiness|release gate).*(increased|promoted|supported)", report, re.I):
        fail("report appears to claim readiness or release-gate movement")

    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"selectedCandidate={SELECTED_CANDIDATE}")
    print(f"futureMicroTarget={data['futureMicroTarget']['id']}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
