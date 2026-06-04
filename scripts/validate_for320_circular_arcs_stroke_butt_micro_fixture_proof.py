#!/usr/bin/env python3
"""Generate and validate the FOR-320 CircularArcsStrokeButt proof bundle."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-320"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-micro-fixture-proof-bundle-ticket"
)

FOR319_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-for319/"
    "circular-arcs-stroke-butt-micro-fixture-for319.json"
)
FOR319_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-319-circular-arcs-stroke-butt-micro-fixture.md"
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
FULL_GM_REFERENCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/resources/original-888/"
    "circular_arcs_stroke_butt.png"
)
GPU_SCORE_FILE = PROJECT_ROOT / "gpu-raster/test-similarity-scores-webgpu.properties"
CPU_SCORE_FILE = PROJECT_ROOT / "cpu-raster/test-similarity-scores.properties"
CPU_REPORT_FILE = PROJECT_ROOT / "skia-integration-tests/test-similarity-report.md"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320"
)
ARTIFACT = ARTIFACT_DIR / "circular-arcs-stroke-butt-micro-fixture-proof-for320.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-320-circular-arcs-stroke-butt-micro-fixture-proof.md"
)

STATIC_SCENES = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/data/scenes.json"
GENERATED_FILES = [
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
]

DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED"
DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_APPLIED"
DECISION_UNSAFE = "CIRCULAR_ARCS_STROKE_BUTT_UNSAFE_SUPPORT_CLAIM_FOUND"

FOR318_TARGET_ID = "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe"
FOR319_FIXTURE_ID = "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true"
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
    raise SystemExit(f"FOR-320 validation failed: {message}")


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


def validate_for319_contract() -> dict[str, Any]:
    data = load_json(FOR319_ARTIFACT)
    report = read_text(FOR319_REPORT)
    if data.get("linear") != "FOR-319":
        fail("FOR-319 artifact has unexpected linear id")
    if data.get("decision") != "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED":
        fail("FOR-319 preflight decision changed")

    follows = data.get("follows")
    if not isinstance(follows, dict):
        fail("FOR-319 artifact missing follows")
    for318 = follows.get("for318")
    if not isinstance(for318, dict):
        fail("FOR-319 artifact missing FOR-318 linkage")
    if for318.get("futureTarget") != FOR318_TARGET_ID:
        fail("FOR-319 no longer follows the FOR-318 target")
    if for318.get("supportStatus") != "not-supported":
        fail("FOR-319 source target must remain not-supported")

    fixture = data.get("microFixture")
    if not isinstance(fixture, dict):
        fail("FOR-319 artifact missing microFixture")
    if fixture.get("id") != FOR319_FIXTURE_ID:
        fail("FOR-319 micro-fixture id changed")
    if fixture.get("sourceFutureTarget") != FOR318_TARGET_ID:
        fail("FOR-319 micro-fixture no longer follows FOR-318")
    if fixture.get("sourceRowId") != SOURCE_ROW_ID or fixture.get("sourceGm") != SOURCE_GM:
        fail("FOR-319 micro-fixture must derive from CircularArcsStrokeButtGM")

    cell = fixture.get("selectedCell")
    if not isinstance(cell, dict):
        fail("FOR-319 micro-fixture missing selectedCell")
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
    }
    for key, expected in expected_cell.items():
        if cell.get(key) != expected:
            fail(f"FOR-319 selectedCell.{key} expected {expected}, got {cell.get(key)}")
    if cell.get("includedCaps") != ["kButt_Cap"]:
        fail("FOR-319 fixture must include only kButt_Cap")
    if sorted(cell.get("excludedCaps", [])) != ["kRound_Cap", "kSquare_Cap"]:
        fail("FOR-319 fixture must exclude round and square caps")

    budget = fixture.get("budgetPreflight")
    if not isinstance(budget, dict):
        fail("FOR-319 micro-fixture missing budgetPreflight")
    if budget.get("webgpuAaEdgeBudget") != EDGE_BUDGET:
        fail("FOR-319 WebGPU AA edge budget changed")
    if budget.get("edgeBudgetMayIncrease") is not False:
        fail("FOR-319 allows an edge-budget increase")
    if budget.get("measuredEdgeCountKnown") is not False:
        fail("FOR-319 unexpectedly knows a selected-cell edge count")

    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("FOR-319 artifact missing supportGuard")
    if guard.get("supportStatus") != "not-supported":
        fail("FOR-319 support status must remain not-supported")
    if guard.get("currentSupportClaim") != "none":
        fail("FOR-319 unexpectedly claims support")
    if guard.get("requiredCompleteProof") != REQUIRED_COMPLETE_PROOF:
        fail("FOR-319 proof checklist changed")

    policy = data.get("budgetPolicy")
    if not isinstance(policy, dict):
        fail("FOR-319 artifact missing budgetPolicy")
    validate_budget_fields(policy, "FOR-319 budgetPolicy")

    for needle in [
        FOR319_FIXTURE_ID,
        "reporting-only",
        "not-supported",
        "no support claim",
        "no edge budget changed",
        "no visual threshold changed",
        "no scene status changed",
        "no fallback reason changed",
    ]:
        if needle not in report:
            fail(f"FOR-319 report missing `{needle}`")

    return {
        "artifact": rel(FOR319_ARTIFACT),
        "report": rel(FOR319_REPORT),
        "fixtureId": fixture["id"],
        "sourceFutureTarget": fixture["sourceFutureTarget"],
        "sourceRowId": fixture["sourceRowId"],
        "sourceGm": fixture["sourceGm"],
        "supportStatus": guard["supportStatus"],
        "selectedCell": cell,
        "budgetPreflight": budget,
    }


def validate_source_contracts() -> dict[str, Any]:
    for318 = load_json(FOR318_ARTIFACT)
    for318_report = read_text(FOR318_REPORT)
    m60 = read_text(M60_SPEC)
    fidelity = read_text(FIDELITY_SPEC)
    gm = read_text(CIRCULAR_ARCS_GM)
    test = read_text(STROKE_BUTT_TEST)
    gpu_scores = read_text(GPU_SCORE_FILE)
    cpu_scores = read_text(CPU_SCORE_FILE)
    cpu_report = read_text(CPU_REPORT_FILE)

    if for318.get("linear") != "FOR-318":
        fail("FOR-318 artifact has unexpected linear id")
    target = for318.get("futureMicroTarget")
    if not isinstance(target, dict):
        fail("FOR-318 artifact missing futureMicroTarget")
    if target.get("id") != FOR318_TARGET_ID or target.get("supportStatus") != "not-supported":
        fail("FOR-318 future target changed or no longer remains not-supported")
    if target.get("sourceRowId") != SOURCE_ROW_ID or target.get("sourceGm") != SOURCE_GM:
        fail("FOR-318 future target no longer points at CircularArcsStrokeButtGM")

    for needle in [
        "| Coverage edge count | <= 256 edges |",
        "| Stroke width range | 0.5 px to 64 px | Excludes hairline",
        "no broad Path AA support claim",
    ]:
        if needle not in m60:
            fail(f"M60 feature spec missing `{needle}`")
    for needle in [
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
        "runGpuCrossTest(CircularArcsStrokeButtGM(), floor = 96.8)",
    ]:
        if needle not in test:
            fail(f"CircularArcsStrokeButtWebGpuTest.kt missing `{needle}`")
    if not FULL_GM_REFERENCE.is_file():
        fail(f"missing full-GM reference PNG: {rel(FULL_GM_REFERENCE)}")
    if "CircularArcsStrokeButtGM=96.87" not in gpu_scores:
        fail("full-GM WebGPU similarity score changed or disappeared")
    if "CircularArcsStrokeButtGM=45.6605" not in cpu_scores:
        fail("full-GM CPU similarity score changed or disappeared")
    if "| CircularArcsStrokeButtGM | 45.66%" not in cpu_report:
        fail("full-GM CPU similarity report entry changed or disappeared")
    if FOR318_TARGET_ID not in for318_report or "not-supported" not in for318_report:
        fail("FOR-318 report no longer documents the blocked future target")

    return {
        "for318Artifact": rel(FOR318_ARTIFACT),
        "for318Report": rel(FOR318_REPORT),
        "m60FeatureSpec": rel(M60_SPEC),
        "fidelitySpec": rel(FIDELITY_SPEC),
        "circularArcsGm": rel(CIRCULAR_ARCS_GM),
        "strokeButtWebGpuTest": rel(STROKE_BUTT_TEST),
        "fullGmReferencePng": rel(FULL_GM_REFERENCE),
        "fullGmGpuScoreFile": rel(GPU_SCORE_FILE),
        "fullGmCpuScoreFile": rel(CPU_SCORE_FILE),
        "fullGmCpuReport": rel(CPU_REPORT_FILE),
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


def validate_budget_fields(policy: dict[str, Any], label: str) -> None:
    expected = {
        "webgpuAaEdgeBudget": EDGE_BUDGET,
        "edgeBudgetMayIncrease": False,
        "hairlineStrokeWidth0Supported": False,
        "fallbackWeakeningAllowed": False,
        "thresholdWeakeningAllowed": False,
        "releaseGateChangeAllowed": False,
        "readinessScoreChangeAllowed": False,
    }
    for key, expected_value in expected.items():
        if policy.get(key) != expected_value:
            fail(f"{label}.{key} expected {expected_value}, got {policy.get(key)}")
    if policy.get("strokeWidthBudget") != {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX}:
        fail(f"{label}.strokeWidthBudget was weakened or malformed")


def build_proof_requirements(
    for319: dict[str, Any],
    preserved_fallbacks: list[dict[str, str]],
) -> list[dict[str, Any]]:
    cell = for319["selectedCell"]
    return [
        {
            "name": "row-specific geometry",
            "status": "captured",
            "complete": True,
            "evidence": {
                "source": for319["artifact"],
                "fixtureId": for319["fixtureId"],
                "selectedCell": {
                    "startDegrees": cell["startDegrees"],
                    "sweepDegrees": cell["sweepDegrees"],
                    "complementSweepDegrees": cell["complementSweepDegrees"],
                    "useCenter": cell["useCenter"],
                    "aa": cell["aa"],
                    "style": cell["style"],
                    "strokeWidth": cell["strokeWidth"],
                    "strokeCap": cell["strokeCap"],
                    "drawArcCalls": cell["drawArcCalls"],
                    "canvasArcRectLTRB": cell["canvasArcRectLTRB"],
                },
            },
            "justification": "FOR-319 defines the single GM cell and drawArc pair.",
        },
        {
            "name": "Skia/reference artifact",
            "status": "blocked",
            "complete": False,
            "evidence": {
                "availableFullGmReference": rel(FULL_GM_REFERENCE),
                "acceptedForPromotion": False,
            },
            "justification": (
                "The repository has only the full 512-arc GM reference PNG; no "
                "row-specific Skia/reference artifact exists for the selected cell."
            ),
        },
        {
            "name": "CPU artifact",
            "status": "blocked",
            "complete": False,
            "evidence": {
                "availableFullGmCpuScore": "CircularArcsStrokeButtGM=45.6605",
                "acceptedForPromotion": False,
            },
            "justification": (
                "The available CPU evidence is full-GM similarity output, not a "
                "selected-cell CPU render artifact."
            ),
        },
        {
            "name": "adapter-backed GPU artifact",
            "status": "blocked",
            "complete": False,
            "evidence": {
                "availableFullGmGpuScore": "CircularArcsStrokeButtGM=96.87",
                "acceptedForPromotion": False,
            },
            "justification": (
                "The existing adapter-backed test covers the full GM and does not "
                "emit a selected-cell GPU artifact."
            ),
        },
        {
            "name": "CPU/GPU diff and stats",
            "status": "blocked",
            "complete": False,
            "evidence": {
                "artifactPath": None,
                "acceptedForPromotion": False,
            },
            "justification": "No selected-cell CPU/GPU diff image or stats payload exists.",
        },
        {
            "name": "route diagnostics with edge-count and fallback fields",
            "status": "blocked",
            "complete": False,
            "evidence": {
                "routeDiagnosticsPath": None,
                "edgeCount": None,
                "fallbackReason": None,
                "requiredFallbackIfUnsupported": EDGE_FALLBACK,
                "acceptedForPromotion": False,
            },
            "justification": (
                "No selected-cell route JSON exposes edge-count and fallback fields."
            ),
        },
        {
            "name": "fallback policy preserving refusals outside the selected row",
            "status": "captured",
            "complete": True,
            "evidence": preserved_fallbacks,
            "justification": "Existing Path AA expected-unsupported rows retain their fallback reasons.",
        },
        {
            "name": "no global edge-budget increase",
            "status": "captured",
            "complete": True,
            "evidence": {
                "webgpuAaEdgeBudget": EDGE_BUDGET,
                "edgeBudgetMayIncrease": False,
            },
            "justification": "The FOR-320 artifact keeps the WebGPU AA edge budget at 256.",
        },
        {
            "name": "no threshold weakening",
            "status": "captured",
            "complete": True,
            "evidence": {
                "thresholdWeakeningAllowed": False,
            },
            "justification": "The FOR-320 artifact does not change visual thresholds.",
        },
    ]


def proof_status(requirements: list[dict[str, Any]]) -> dict[str, bool]:
    return {item["name"]: item.get("complete") is True for item in requirements}


def build_artifact() -> dict[str, Any]:
    for319 = validate_for319_contract()
    source_contracts = validate_source_contracts()
    preserved_fallbacks = validate_preserved_fallbacks()
    requirements = build_proof_requirements(for319, preserved_fallbacks)
    status = proof_status(requirements)

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_BLOCKED,
        "blockedDecisionReason": (
            "The selected FOR-319 micro-fixture has geometry and guard evidence, "
            "but lacks row-specific Skia/reference, CPU, adapter-backed GPU, "
            "CPU/GPU diff/stat, and route diagnostics artifacts."
        ),
        "follows": {
            "for319": {
                "artifact": for319["artifact"],
                "report": for319["report"],
                "fixtureId": for319["fixtureId"],
                "sourceFutureTarget": for319["sourceFutureTarget"],
                "sourceRowId": for319["sourceRowId"],
                "sourceGm": for319["sourceGm"],
                "supportStatus": for319["supportStatus"],
            },
            "for318": {
                "artifact": source_contracts["for318Artifact"],
                "report": source_contracts["for318Report"],
                "futureTarget": FOR318_TARGET_ID,
                "supportStatus": "not-supported",
            },
        },
        "sourceReports": {
            "m60FeatureSpec": source_contracts["m60FeatureSpec"],
            "fidelitySpec": source_contracts["fidelitySpec"],
        },
        "sourceCode": {
            "circularArcsGm": source_contracts["circularArcsGm"],
            "strokeButtWebGpuTest": source_contracts["strokeButtWebGpuTest"],
        },
        "microFixture": {
            "id": for319["fixtureId"],
            "fixtureKind": "single-gm-cell-proof-blocked",
            "sourceFutureTarget": for319["sourceFutureTarget"],
            "sourceRowId": for319["sourceRowId"],
            "sourceGm": for319["sourceGm"],
            "supportStatus": "not-supported",
            "selectedCell": for319["selectedCell"],
        },
        "availableNonPromotingEvidence": {
            "fullGmReferencePng": source_contracts["fullGmReferencePng"],
            "fullGmGpuScoreFile": source_contracts["fullGmGpuScoreFile"],
            "fullGmGpuScore": "CircularArcsStrokeButtGM=96.87",
            "fullGmCpuScoreFile": source_contracts["fullGmCpuScoreFile"],
            "fullGmCpuScore": "CircularArcsStrokeButtGM=45.6605",
            "fullGmCpuReport": source_contracts["fullGmCpuReport"],
            "acceptedForSelectedCellPromotion": False,
            "reason": "Full-GM evidence is not row-specific to the selected FOR-319 cell.",
        },
        "proofBundle": {
            "requiredCompleteProof": REQUIRED_COMPLETE_PROOF,
            "proofStatus": status,
            "proofComplete": proof_is_complete(status),
            "requirements": requirements,
            "blockedUntil": [item["name"] for item in requirements if item.get("complete") is not True],
        },
        "supportGuard": {
            "status": "blocked",
            "supportStatus": "not-supported",
            "currentSupportClaim": "none",
            "declaredSupportEvidenceComplete": False,
            "readinessMovement": False,
            "readinessScoreChanged": False,
            "releaseGateChanged": False,
            "unsafeDecision": DECISION_UNSAFE,
            "supportedStatusesThatRequireCompleteProof": sorted(SUPPORTED_STATUSES),
            "appliedDecisionIfComplete": DECISION_APPLIED,
        },
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
        "nonGoals": [
            "no renderer or shader code changed",
            "no existing tests changed",
            "no scene status changed",
            "no fallback reason changed",
            "no edge budget changed",
            "no visual threshold changed",
            "no readiness score changed",
            "no release gate changed",
            "no support claim for CircularArcsStrokeButtGM or the micro-fixture",
            "no hairline strokeWidth=0",
            "no round cap, square cap, fill, dash, useCenter=true, or full-GM scope",
        ],
        "alternateDecisions": {
            "applied": DECISION_APPLIED,
            "unsafe": DECISION_UNSAFE,
        },
    }


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")

    bundle = data.get("proofBundle")
    if not isinstance(bundle, dict):
        fail("artifact missing proofBundle")
    status = bundle.get("proofStatus")
    if not isinstance(status, dict):
        fail("proofBundle.proofStatus must be an object")
    if set(status) != set(REQUIRED_COMPLETE_PROOF):
        fail("proofBundle.proofStatus must list the complete proof checklist")
    complete = proof_is_complete(status)
    if bundle.get("proofComplete") is not complete:
        fail("proofBundle.proofComplete disagrees with proofStatus")
    if complete and data.get("decision") != DECISION_APPLIED:
        fail("complete proof must use the applied decision")
    if not complete and data.get("decision") != DECISION_BLOCKED:
        fail("incomplete proof must use the blocked decision")
    if bundle.get("blockedUntil") != [key for key in REQUIRED_COMPLETE_PROOF if status.get(key) is not True]:
        fail("proofBundle.blockedUntil does not match incomplete proof items")

    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("artifact missing supportGuard")
    support_status = str(guard.get("supportStatus", "")).lower()
    readiness_moved = (
        guard.get("readinessMovement") is True
        or guard.get("readinessScoreChanged") is True
        or guard.get("releaseGateChanged") is True
    )
    if support_status in SUPPORTED_STATUSES and not complete:
        fail("micro-fixture claims support without complete proof")
    if guard.get("currentSupportClaim") != "none" and not complete:
        fail("micro-fixture declares a support claim without complete proof")
    if guard.get("declaredSupportEvidenceComplete") is True and not complete:
        fail("supportGuard declares complete support evidence but proofStatus is incomplete")
    if readiness_moved and not complete:
        fail("micro-fixture declares readiness/release movement without complete proof")
    if guard.get("supportStatus") != "not-supported":
        fail("FOR-320 must keep the micro-fixture not-supported")

    fixture = data.get("microFixture")
    if not isinstance(fixture, dict):
        fail("artifact missing microFixture")
    if fixture.get("id") != FOR319_FIXTURE_ID:
        fail("microFixture id changed")
    if fixture.get("fixtureKind") != "single-gm-cell-proof-blocked":
        fail("FOR-320 fixture kind must remain proof-blocked")
    cell = fixture.get("selectedCell")
    if not isinstance(cell, dict):
        fail("microFixture missing selectedCell")
    if cell.get("strokeWidth") == 0 or cell.get("includesHairlineStrokeWidth0") is not False:
        fail("FOR-320 includes forbidden hairline strokeWidth=0")
    if cell.get("strokeCap") != "kButt_Cap" or cell.get("includedCaps") != ["kButt_Cap"]:
        fail("FOR-320 must include only kButt_Cap")
    if cell.get("useCenter") is not False:
        fail("FOR-320 must exclude useCenter=true")
    if cell.get("includesFill") is not False or cell.get("includesDash") is not False:
        fail("FOR-320 must exclude fill and dash")

    policy = data.get("budgetPolicy")
    if not isinstance(policy, dict):
        fail("artifact missing budgetPolicy")
    validate_budget_fields(policy, "FOR-320 budgetPolicy")
    if policy.get("rendererOrShaderChangeAllowed") is not False:
        fail("FOR-320 unexpectedly allows renderer or shader changes")
    if policy.get("sceneStatusChangeAllowed") is not False:
        fail("FOR-320 unexpectedly allows scene status changes")

    available = data.get("availableNonPromotingEvidence")
    if not isinstance(available, dict):
        fail("artifact missing availableNonPromotingEvidence")
    if available.get("acceptedForSelectedCellPromotion") is not False:
        fail("full-GM evidence must not be accepted as selected-cell promotion proof")


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    fixture = data["microFixture"]
    cell = fixture["selectedCell"]
    bundle = data["proofBundle"]
    blocked_lines = "\n".join(f"- {item}" for item in bundle["blockedUntil"])
    proof_lines = "\n".join(
        "| `{name}` | `{status}` | `{complete}` | {justification} |".format(**item)
        for item in bundle["requirements"]
    )
    fallback_lines = "\n".join(
        "| `{id}` | `{status}` | `{fallbackReason}` | `{source}` |".format(**row)
        for row in data["preservedFallbackEvidence"]
    )
    non_goal_lines = "\n".join(f"- {item}" for item in data["nonGoals"])

    report = f"""# FOR-320 CircularArcsStrokeButt Micro-Fixture Proof Bundle

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-320 packages the available proof state for the single FOR-319
`CircularArcsStrokeButtGM` cell `{fixture["id"]}`. The decision is blocked,
not applied: the repository does not contain row-specific Skia/reference, CPU,
adapter-backed GPU, CPU/GPU diff/stat, or route diagnostics artifacts for this
selected cell.

The FOR-318/FOR-319 target remains `not-supported`. No renderer, shader,
threshold, fallback, scene status, readiness score, or release gate changes are
made.

## Source Linkage

- FOR-319 artifact: `{data["follows"]["for319"]["artifact"]}`
- FOR-319 report: `{data["follows"]["for319"]["report"]}`
- FOR-318 artifact: `{data["follows"]["for318"]["artifact"]}`
- FOR-318 report: `{data["follows"]["for318"]["report"]}`
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
| support status | `{fixture["supportStatus"]}` |
| row / column | `{cell["rowIndex"]}` / `{cell["columnIndex"]}` |
| start | `{cell["startDegrees"]}` |
| sweep | `{cell["sweepDegrees"]}` |
| complement sweep | `{cell["complementSweepDegrees"]}` |
| useCenter | `{cell["useCenter"]}` |
| AA | `{cell["aa"]}` |
| style | `{cell["style"]}` |
| stroke width | `{cell["strokeWidth"]}` |
| stroke cap | `{cell["strokeCap"]}` |
| canvas rect | `{cell["canvasArcRectLTRB"]}` |
| drawArc calls | `{len(cell["drawArcCalls"])}` |

## Proof Checklist

| Proof | Status | Complete | Justification |
|---|---|---:|---|
{proof_lines}

## Blocking Evidence

The proof bundle is incomplete until these row-specific items exist:

{blocked_lines}

Existing full-GM evidence is recorded but is not accepted as selected-cell
promotion proof:

- full-GM reference PNG: `{data["availableNonPromotingEvidence"]["fullGmReferencePng"]}`
- full-GM WebGPU score: `{data["availableNonPromotingEvidence"]["fullGmGpuScore"]}`
- full-GM CPU score: `{data["availableNonPromotingEvidence"]["fullGmCpuScore"]}`

## Budget And Support Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `{data["budgetPolicy"]["webgpuAaEdgeBudget"]}` |
| edge budget may increase | `{data["budgetPolicy"]["edgeBudgetMayIncrease"]}` |
| threshold weakening allowed | `{data["budgetPolicy"]["thresholdWeakeningAllowed"]}` |
| fallback weakening allowed | `{data["budgetPolicy"]["fallbackWeakeningAllowed"]}` |
| hairline strokeWidth=0 supported | `{data["budgetPolicy"]["hairlineStrokeWidth0Supported"]}` |
| renderer or shader change allowed | `{data["budgetPolicy"]["rendererOrShaderChangeAllowed"]}` |
| scene status change allowed | `{data["budgetPolicy"]["sceneStatusChangeAllowed"]}` |
| readiness movement | `{data["supportGuard"]["readinessMovement"]}` |
| release gate changed | `{data["supportGuard"]["releaseGateChanged"]}` |
| unsafe decision | `{data["supportGuard"]["unsafeDecision"]}` |

## Preserved Fallback Rows

| Scene id | Status | Fallback reason | Source |
|---|---|---|---|
{fallback_lines}

## Non-Goals And Non-Changes

{non_goal_lines}

## Validation

- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
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
        DECISION_BLOCKED,
        FOR319_FIXTURE_ID,
        "not-supported",
        "The proof bundle is incomplete",
        "row-specific Skia/reference",
        "WebGPU AA edge budget",
        "edge budget may increase",
        "threshold weakening allowed",
        "hairline strokeWidth=0 supported",
        "no support claim",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")

    unsafe_patterns = [
        r"\bsupport status\s+\|\s+`?(pass|supported|promoted|gpu-supported)",
        r"\breadiness movement\s+\|\s+`?True",
        r"\brelease gate changed\s+\|\s+`?True",
        r"\bedge budget may increase\s+\|\s+`?True",
        r"\bthreshold weakening allowed\s+\|\s+`?True",
        r"\bfallback weakening allowed\s+\|\s+`?True",
        r"\bhairline strokeWidth=0 supported\s+\|\s+`?True",
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

    print(f"{LINEAR_ID}: {DECISION_BLOCKED}")
    print(f"microFixture={data['microFixture']['id']}")
    print(f"proofComplete={data['proofBundle']['proofComplete']}")
    print(f"blockedUntil={','.join(data['proofBundle']['blockedUntil'])}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
