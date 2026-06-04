#!/usr/bin/env python3
"""Generate and validate the FOR-319 CircularArcsStrokeButt micro-fixture."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-319"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-micro-fixture-preflight-ticket"
)

FOR318_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "path-aa-arc-stroke-hairline-scout-for318/"
    "path-aa-arc-stroke-hairline-scout-for318.json"
)
FOR318_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md"
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
CIRCULAR_ARCS_GM = (
    PROJECT_ROOT
    / "skia-integration-tests/src/main/kotlin/org/skia/tests/CircularArcsGM.kt"
)
STROKE_BUTT_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    "CircularArcsStrokeButtWebGpuTest.kt"
)

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-for319"
)
ARTIFACT = ARTIFACT_DIR / "circular-arcs-stroke-butt-micro-fixture-for319.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-319-circular-arcs-stroke-butt-micro-fixture.md"
)

STATIC_SCENES = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/data/scenes.json"
GENERATED_FILES = [
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
]

DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED"
DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_BLOCKED"
DECISION_UNSAFE = "CIRCULAR_ARCS_STROKE_BUTT_UNSAFE_SUPPORT_CLAIM_FOUND"

FOR318_TARGET_ID = "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe"
SOURCE_ROW_ID = "circular-arcs-stroke-butt-webgpu"
SOURCE_GM = "CircularArcsStrokeButtGM"
EDGE_FALLBACK = "coverage.edge-count-exceeded"
STROKE_OUTLINE_FALLBACK = "coverage.stroke-outline-edge-count-exceeded"
CAP_JOIN_FALLBACK = "coverage.stroke-cap-join-visual-parity-below-threshold"
EDGE_BUDGET = 256
STROKE_WIDTH_MIN = 0.5
STROKE_WIDTH_MAX = 64.0

REQUIRED_COMPLETE_PROOF = [
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
    "promoted",
    "support-promoted",
    "supported",
    "gpu-supported",
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


def fail(message: str) -> None:
    raise SystemExit(f"FOR-319 validation failed: {message}")


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


def proof_is_complete(proof: dict[str, Any]) -> bool:
    return all(proof.get(item) is True for item in REQUIRED_COMPLETE_PROOF)


def validate_for318_contract() -> dict[str, Any]:
    data = load_json(FOR318_ARTIFACT)
    report = read_text(FOR318_REPORT)
    if data.get("linear") != "FOR-318":
        fail("FOR-318 artifact has unexpected linear id")
    if data.get("scoutStatus") != "reporting-only":
        fail("FOR-318 scout must remain reporting-only")

    target = data.get("futureMicroTarget")
    if not isinstance(target, dict):
        fail("FOR-318 artifact missing futureMicroTarget")
    if target.get("id") != FOR318_TARGET_ID:
        fail(
            "FOR-318 future target changed from "
            f"{FOR318_TARGET_ID} to {target.get('id')}"
        )
    if target.get("sourceRowId") != SOURCE_ROW_ID or target.get("sourceGm") != SOURCE_GM:
        fail("FOR-318 no longer points at CircularArcsStrokeButtGM")
    if target.get("supportStatus") != "not-supported":
        fail("FOR-318 target must remain not-supported")
    if target.get("stableFallbackIfNotIsolated") != EDGE_FALLBACK:
        fail("FOR-318 target fallback changed away from coverage.edge-count-exceeded")

    budget = data.get("budgetPolicy")
    if not isinstance(budget, dict):
        fail("FOR-318 artifact missing budgetPolicy")
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
            fail(f"FOR-318 budgetPolicy.{key} expected {expected}, got {budget.get(key)}")
    if budget.get("strokeWidthBudget") != {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX}:
        fail("FOR-318 stroke-width budget was weakened or malformed")

    support_guard = data.get("supportGuard")
    if not isinstance(support_guard, dict):
        fail("FOR-318 artifact missing supportGuard")
    if support_guard.get("currentSupportClaim") != "none":
        fail("FOR-318 unexpectedly claims support")
    if support_guard.get("requiredCompleteProof") != REQUIRED_COMPLETE_PROOF:
        fail("FOR-318 proof list changed")

    rows = data.get("candidateRows")
    if not isinstance(rows, list):
        fail("FOR-318 candidateRows missing")
    source = next((row for row in rows if row.get("id") == SOURCE_ROW_ID), None)
    if not isinstance(source, dict):
        fail("FOR-318 missing CircularArcsStrokeButt source row")
    if source.get("currentSupportStatus") != "expected-unsupported":
        fail("FOR-318 CircularArcsStrokeButt source row changed support status")
    if source.get("currentFallbackReason") != EDGE_FALLBACK:
        fail("FOR-318 CircularArcsStrokeButt source row changed fallback")
    if source.get("scoutDisposition") != "selected-future-micro-target-source":
        fail("FOR-318 CircularArcsStrokeButt source row is no longer selected")

    for needle in [
        FOR318_TARGET_ID,
        "not-supported",
        "no readiness score changed",
        "no visual threshold changed",
        "no scene status changed",
        "no fallback reason changed",
        "no edge budget changed",
    ]:
        if needle not in report:
            fail(f"FOR-318 report missing `{needle}`")

    return {
        "artifact": rel(FOR318_ARTIFACT),
        "report": rel(FOR318_REPORT),
        "futureTarget": target["id"],
        "sourceRowId": target["sourceRowId"],
        "sourceGm": target["sourceGm"],
        "supportStatus": target["supportStatus"],
        "stableFallbackIfNotIsolated": target["stableFallbackIfNotIsolated"],
    }


def validate_source_files() -> dict[str, str]:
    m37 = read_text(M37_REPORT)
    m60 = read_text(M60_SPEC)
    fidelity = read_text(FIDELITY_SPEC)
    gm = read_text(CIRCULAR_ARCS_GM)
    test = read_text(STROKE_BUTT_TEST)

    for needle in [
        "CircularArcsStrokeButtWebGpuTest#CircularArcsStrokeButtGM",
        "Arc stroke/hairline",
        EDGE_FALLBACK,
        "Rank next after strokes; keep expected unsupported until arc subdivision route is scoped",
    ]:
        if needle not in m37:
            fail(f"M37 report missing `{needle}`")

    for needle in [
        "| Coverage edge count | <= 256 edges |",
        "| Stroke width range | 0.5 px to 64 px | Excludes hairline",
        "common stroke caps: butt, round, square",
        "no broad Path AA support claim",
    ]:
        if needle not in m60:
            fail(f"M60 feature spec missing `{needle}`")

    for needle in [
        "Reference Provenance Policy",
        "A promoted support row must include:",
        "reference artifact",
        "CPU artifact",
        "GPU artifact when GPU-eligible",
        "diff and stats artifacts",
    ]:
        if needle not in fidelity:
            fail(f"fidelity spec missing `{needle}`")

    for needle in [
        "val kStarts: FloatArray = floatArrayOf(0f, 10f, 30f, 45f, 90f, 165f, 180f, 270f)",
        "val kSweeps: FloatArray = floatArrayOf(1f, 45f, 90f, 130f, 180f, 184f, 300f, 355f)",
        "strokeWidth = 15f",
        "paint.strokeCap = SkPaint.Cap.kButt_Cap",
        "paint.strokeWidth = 0f",
        "paint.strokeCap = SkPaint.Cap.kRound_Cap",
        "paint.strokeCap = SkPaint.Cap.kSquare_Cap",
    ]:
        if needle not in gm:
            fail(f"CircularArcsGM.kt missing `{needle}`")

    for needle in [
        "CircularArcsStrokeButtGM",
        "strokeWidth = 15",
        "paint.strokeCap = kButt_Cap",
        "512 stroked-butt arcs",
        "observed 96.87 %",
        "runGpuCrossTest(CircularArcsStrokeButtGM(), floor = 96.8)",
    ]:
        if needle not in test:
            fail(f"CircularArcsStrokeButtWebGpuTest.kt missing `{needle}`")

    return {
        "m37": rel(M37_REPORT),
        "m60FeatureSpec": rel(M60_SPEC),
        "fidelitySpec": rel(FIDELITY_SPEC),
        "circularArcsGm": rel(CIRCULAR_ARCS_GM),
        "strokeButtWebGpuTest": rel(STROKE_BUTT_TEST),
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


def build_micro_fixture() -> dict[str, Any]:
    return {
        "id": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
        "fixtureKind": "single-gm-cell-reporting-only",
        "sourceFutureTarget": FOR318_TARGET_ID,
        "sourceRowId": SOURCE_ROW_ID,
        "sourceGm": SOURCE_GM,
        "sourceGmName": "circular_arcs_stroke_butt",
        "selectionReason": (
            "The first start row and 90-degree sweep column provide a deterministic "
            "butt-cap, non-hairline, AA circular arc cell while avoiding hairline, "
            "round-cap, square-cap, fill, dash, useCenter, and full-GM breadth."
        ),
        "gmGrid": {
            "quadrants": 4,
            "starts": [0, 10, 30, 45, 90, 165, 180, 270],
            "sweeps": [1, 45, 90, 130, 180, 184, 300, 355],
            "arcsPerCell": 2,
            "fullGmDrawArcCalls": 512,
        },
        "selectedCell": {
            "cellCount": 1,
            "quadrant": "bottom-left",
            "quadrantOrigin": {"x": 0, "y": 500},
            "rowIndex": 0,
            "columnIndex": 2,
            "startDegrees": 0,
            "sweepDegrees": 90,
            "complementSweepDegrees": -270,
            "useCenter": False,
            "aa": True,
            "style": "kStroke_Style",
            "strokeWidth": 15,
            "strokeCap": "kButt_Cap",
            "includedCaps": ["kButt_Cap"],
            "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
            "includesHairlineStrokeWidth0": False,
            "includesFill": False,
            "includesDash": False,
            "localArcRectLTRB": [0, 0, 40, 40],
            "canvasArcRectLTRB": [140, 520, 180, 560],
            "paintAlpha": 100,
            "drawArcCalls": [
                {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
                {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
            ],
        },
        "budgetPreflight": {
            "webgpuAaEdgeBudget": EDGE_BUDGET,
            "edgeBudgetMayIncrease": False,
            "measuredEdgeCountKnown": False,
            "measuredEdgeCountRequiredBeforeSupport": True,
            "fallbackIfMeasuredOverBudget": EDGE_FALLBACK,
            "fullGmDrawArcCalls": 512,
            "selectedDrawArcCalls": 2,
            "drawArcReductionFactorVsFullGm": 256,
            "strokeWidthWithinM60Budget": True,
            "hairlineExcludedByM60Budget": True,
        },
    }


def build_support_guard() -> dict[str, Any]:
    return {
        "status": "reporting-only",
        "supportStatus": "not-supported",
        "currentSupportClaim": "none",
        "declaredSupportEvidenceComplete": False,
        "readinessMovement": False,
        "readinessScoreChanged": False,
        "releaseGateChanged": False,
        "unsafeDecision": DECISION_UNSAFE,
        "requiredCompleteProof": REQUIRED_COMPLETE_PROOF,
        "proofStatus": {item: False for item in REQUIRED_COMPLETE_PROOF},
        "supportedStatusesThatRequireCompleteProof": sorted(SUPPORTED_STATUSES),
    }


def build_artifact() -> dict[str, Any]:
    follows_for318 = validate_for318_contract()
    source_files = validate_source_files()
    preserved_fallbacks = validate_preserved_fallbacks()

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "follows": {"for318": follows_for318},
        "sourceReports": {
            "m37": source_files["m37"],
            "m60FeatureSpec": source_files["m60FeatureSpec"],
            "fidelitySpec": source_files["fidelitySpec"],
        },
        "sourceCode": {
            "circularArcsGm": source_files["circularArcsGm"],
            "strokeButtWebGpuTest": source_files["strokeButtWebGpuTest"],
        },
        "microFixture": build_micro_fixture(),
        "supportGuard": build_support_guard(),
        "budgetPolicy": {
            "webgpuAaEdgeBudget": EDGE_BUDGET,
            "edgeBudgetMayIncrease": False,
            "strokeWidthBudget": {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX},
            "hairlineStrokeWidth0Supported": False,
            "fallbackWeakeningAllowed": False,
            "thresholdWeakeningAllowed": False,
            "rendererOrShaderChangeAllowed": False,
            "sceneStatusChangeAllowed": False,
            "releaseGateChangeAllowed": False,
            "readinessScoreChangeAllowed": False,
            "preservedFallbackReasons": sorted(
                {EDGE_FALLBACK, STROKE_OUTLINE_FALLBACK, CAP_JOIN_FALLBACK}
            ),
        },
        "preservedFallbackEvidence": preserved_fallbacks,
        "blockedUntil": REQUIRED_COMPLETE_PROOF,
        "nonGoals": [
            "no CPU/GPU/reference images generated or modified",
            "no renderer or shader code changed",
            "no existing tests changed",
            "no scene status changed",
            "no fallback reason changed",
            "no edge budget changed",
            "no visual threshold changed",
            "no readiness score changed",
            "no release gate changed",
            "no support claim for CircularArcsStrokeButtGM or the micro-fixture",
            "no hairline, round cap, square cap, fill, dash, or full-GM scope",
        ],
        "alternateDecisions": {
            "blocked": DECISION_BLOCKED,
            "unsafe": DECISION_UNSAFE,
        },
    }


def validate_micro_fixture(data: dict[str, Any]) -> None:
    fixture = data.get("microFixture")
    if not isinstance(fixture, dict):
        fail("microFixture must be an object")
    if fixture.get("sourceFutureTarget") != FOR318_TARGET_ID:
        fail("microFixture no longer follows the FOR-318 future target")
    if fixture.get("sourceRowId") != SOURCE_ROW_ID or fixture.get("sourceGm") != SOURCE_GM:
        fail("microFixture must derive from CircularArcsStrokeButtGM")

    cell = fixture.get("selectedCell")
    if not isinstance(cell, dict):
        fail("microFixture.selectedCell must be an object")
    expected_cell = {
        "cellCount": 1,
        "rowIndex": 0,
        "columnIndex": 2,
        "startDegrees": 0,
        "sweepDegrees": 90,
        "complementSweepDegrees": -270,
        "useCenter": False,
        "aa": True,
        "style": "kStroke_Style",
        "strokeWidth": 15,
        "strokeCap": "kButt_Cap",
        "includesHairlineStrokeWidth0": False,
        "includesFill": False,
        "includesDash": False,
        "drawArcCalls": [
            {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
            {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
        ],
    }
    for key, expected in expected_cell.items():
        if cell.get(key) != expected:
            fail(f"selectedCell.{key} expected {expected}, got {cell.get(key)}")

    if cell.get("strokeWidth") == 0 or cell.get("includesHairlineStrokeWidth0") is not False:
        fail("micro-fixture includes forbidden hairline strokeWidth=0")
    if cell.get("includedCaps") != ["kButt_Cap"]:
        fail("micro-fixture must include only kButt_Cap")
    if any(cap in cell.get("includedCaps", []) for cap in ["kRound_Cap", "kSquare_Cap"]):
        fail("micro-fixture mixes round/square caps into the butt-cap fixture")
    if sorted(cell.get("excludedCaps", [])) != ["kRound_Cap", "kSquare_Cap"]:
        fail("micro-fixture must explicitly exclude round and square caps")

    budget = fixture.get("budgetPreflight")
    if not isinstance(budget, dict):
        fail("microFixture.budgetPreflight must be an object")
    expected_budget = {
        "webgpuAaEdgeBudget": EDGE_BUDGET,
        "edgeBudgetMayIncrease": False,
        "measuredEdgeCountKnown": False,
        "measuredEdgeCountRequiredBeforeSupport": True,
        "fallbackIfMeasuredOverBudget": EDGE_FALLBACK,
        "fullGmDrawArcCalls": 512,
        "selectedDrawArcCalls": 2,
        "drawArcReductionFactorVsFullGm": 256,
        "strokeWidthWithinM60Budget": True,
        "hairlineExcludedByM60Budget": True,
    }
    for key, expected in expected_budget.items():
        if budget.get(key) != expected:
            fail(f"budgetPreflight.{key} expected {expected}, got {budget.get(key)}")


def validate_support_guard(data: dict[str, Any]) -> None:
    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("supportGuard must be an object")
    proof = guard.get("proofStatus")
    if not isinstance(proof, dict):
        fail("supportGuard.proofStatus must be an object")
    if set(proof) != set(REQUIRED_COMPLETE_PROOF):
        fail("supportGuard.proofStatus must list the complete proof checklist")

    complete = proof_is_complete(proof)
    status = str(guard.get("supportStatus", "")).lower()
    fixture = data.get("microFixture", {})
    fixture_kind = str(fixture.get("fixtureKind", "")).lower() if isinstance(fixture, dict) else ""
    readiness_moved = (
        guard.get("readinessMovement") is True
        or guard.get("readinessScoreChanged") is True
        or guard.get("releaseGateChanged") is True
    )
    if status in SUPPORTED_STATUSES and not complete:
        fail("micro-fixture claims support without complete proof")
    if guard.get("currentSupportClaim") != "none" and not complete:
        fail("micro-fixture declares a support claim without complete proof")
    if guard.get("declaredSupportEvidenceComplete") is True and not complete:
        fail("supportGuard declares complete support evidence but proofStatus is incomplete")
    if readiness_moved and not complete:
        fail("micro-fixture declares readiness/release movement without complete proof")
    if fixture_kind != "single-gm-cell-reporting-only":
        fail("micro-fixture must remain a reporting-only single-cell contract")


def validate_budget_policy(data: dict[str, Any]) -> None:
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
        "sceneStatusChangeAllowed": False,
        "releaseGateChangeAllowed": False,
        "readinessScoreChangeAllowed": False,
    }
    for key, expected in expected_budget.items():
        if budget.get(key) != expected:
            fail(f"budgetPolicy.{key} expected {expected}, got {budget.get(key)}")
    if budget.get("strokeWidthBudget") != {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX}:
        fail("strokeWidthBudget was weakened or malformed")
    reasons = set(budget.get("preservedFallbackReasons", []))
    for reason in [EDGE_FALLBACK, STROKE_OUTLINE_FALLBACK, CAP_JOIN_FALLBACK]:
        if reason not in reasons:
            fail(f"budgetPolicy no longer preserves fallback reason {reason}")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("decision") != DECISION_APPLIED:
        fail("artifact does not record the applied preflight decision")
    follows = data.get("follows")
    if not isinstance(follows, dict) or "for318" not in follows:
        fail("artifact must reference FOR-318")
    for source_group in ["sourceReports", "sourceCode"]:
        if not isinstance(data.get(source_group), dict):
            fail(f"{source_group} must be an object")
    validate_micro_fixture(data)
    validate_support_guard(data)
    validate_budget_policy(data)
    if data.get("blockedUntil") != REQUIRED_COMPLETE_PROOF:
        fail("artifact blockedUntil must match the complete proof list")


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    fixture = data["microFixture"]
    cell = fixture["selectedCell"]
    budget = fixture["budgetPreflight"]
    proof_lines = "\n".join(f"- {item}" for item in REQUIRED_COMPLETE_PROOF)
    non_goal_lines = "\n".join(f"- {item}" for item in data["nonGoals"])
    fallback_lines = "\n".join(
        "| `{id}` | `{status}` | `{fallbackReason}` | `{source}` |".format(**row)
        for row in data["preservedFallbackEvidence"]
    )

    report = f"""# FOR-319 CircularArcsStrokeButt Micro-Fixture Preflight

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-319 defines a reporting-only micro-fixture contract for one deterministic
`CircularArcsStrokeButtGM` cell. It follows the FOR-318 future target
`{FOR318_TARGET_ID}` and keeps that target `not-supported`.

No renderer, shader, threshold, fallback, scene status, readiness score, or
release gate changes are made. The fixture is a preflight selection only: it
does not generate reference/CPU/GPU images, route JSON, diff stats, or support
evidence.

## Source Linkage

- FOR-318 artifact: `{data["follows"]["for318"]["artifact"]}`
- FOR-318 report: `{data["follows"]["for318"]["report"]}`
- M37 audit: `{data["sourceReports"]["m37"]}`
- M60 feature spec: `{data["sourceReports"]["m60FeatureSpec"]}`
- Fidelity spec: `{data["sourceReports"]["fidelitySpec"]}`
- GM source: `{data["sourceCode"]["circularArcsGm"]}`
- Existing WebGPU test: `{data["sourceCode"]["strokeButtWebGpuTest"]}`

## Selected Micro-Fixture

| Field | Value |
|---|---|
| id | `{fixture["id"]}` |
| source future target | `{fixture["sourceFutureTarget"]}` |
| source row | `{fixture["sourceRowId"]}` |
| source GM | `{fixture["sourceGm"]}` |
| fixture kind | `{fixture["fixtureKind"]}` |
| cell count | `{cell["cellCount"]}` |
| quadrant | `{cell["quadrant"]}` |
| row / column | `{cell["rowIndex"]}` / `{cell["columnIndex"]}` |
| start | `{cell["startDegrees"]}` |
| sweep | `{cell["sweepDegrees"]}` |
| complement sweep | `{cell["complementSweepDegrees"]}` |
| useCenter | `{cell["useCenter"]}` |
| AA | `{cell["aa"]}` |
| style | `{cell["style"]}` |
| stroke width | `{cell["strokeWidth"]}` |
| stroke cap | `{cell["strokeCap"]}` |
| local rect | `{cell["localArcRectLTRB"]}` |
| canvas rect | `{cell["canvasArcRectLTRB"]}` |
| drawArc calls | `{len(cell["drawArcCalls"])}` |

The selected cell is `start=0`, `sweep=90`, `useCenter=false`, `aa=true`,
`strokeWidth=15`, `strokeCap=kButt_Cap`. It is faithful to the GM cell shape,
so the cell contains two draws: the red 90-degree arc and the blue -270-degree
complement arc.

## Budget Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `{budget["webgpuAaEdgeBudget"]}` |
| edge budget may increase | `{budget["edgeBudgetMayIncrease"]}` |
| measured edge count known | `{budget["measuredEdgeCountKnown"]}` |
| measured edge count required before support | `{budget["measuredEdgeCountRequiredBeforeSupport"]}` |
| fallback if measured over budget | `{budget["fallbackIfMeasuredOverBudget"]}` |
| full GM drawArc calls | `{budget["fullGmDrawArcCalls"]}` |
| selected drawArc calls | `{budget["selectedDrawArcCalls"]}` |
| drawArc reduction factor vs full GM | `{budget["drawArcReductionFactorVsFullGm"]}` |
| stroke width within M60 budget | `{budget["strokeWidthWithinM60Budget"]}` |
| hairline excluded by M60 budget | `{budget["hairlineExcludedByM60Budget"]}` |

## Support Guard

- Status: `{data["supportGuard"]["status"]}`
- Support status: `{data["supportGuard"]["supportStatus"]}`
- Current support claim: `{data["supportGuard"]["currentSupportClaim"]}`
- Readiness movement: `{data["supportGuard"]["readinessMovement"]}`
- Release gate changed: `{data["supportGuard"]["releaseGateChanged"]}`
- Unsafe decision: `{data["supportGuard"]["unsafeDecision"]}`

## Required Future Promotion Proof

{proof_lines}

## Preserved Fallback Rows

| Scene id | Status | Fallback reason | Source |
|---|---|---|---|
{fallback_lines}

## Non-Goals And Non-Changes

{non_goal_lines}

## Validation

- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null`
- `rtk git diff --check`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report() -> None:
    report = read_text(REPORT)
    for needle in [
        DECISION_APPLIED,
        FOR318_TARGET_ID,
        "reporting-only micro-fixture contract",
        "not-supported",
        "start=0",
        "sweep=90",
        "useCenter=false",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        EDGE_FALLBACK,
        "no support claim",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")

    unsafe_patterns = [
        r"\bsupport status:\s+`?(pass|supported|promoted|gpu-supported)",
        r"\breadiness movement:\s+`?True",
        r"\brelease gate changed:\s+`?True",
        r"\bedge budget may increase\s+\|\s+`?True",
        r"\bthreshold.*weakened",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, re.IGNORECASE):
            fail(f"report appears to contain unsafe claim matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    write_report(data)

    generated = load_json(ARTIFACT)
    validate_artifact_shape(generated)
    validate_report()

    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"sourceFutureTarget={FOR318_TARGET_ID}")
    print(f"microFixture={data['microFixture']['id']}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
