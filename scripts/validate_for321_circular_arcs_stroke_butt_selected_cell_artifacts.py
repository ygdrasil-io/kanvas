#!/usr/bin/env python3
"""Generate and validate the FOR-321 selected-cell artifact decision."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-321"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-artifact-generation-ticket"
)

FOR321_ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321"
)
FOR321_ARTIFACT = (
    FOR321_ARTIFACT_DIR
    / "circular-arcs-stroke-butt-selected-cell-artifacts-for321.json"
)
FOR321_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-321-circular-arcs-stroke-butt-selected-cell-artifacts.md"
)

FOR320_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320.json"
)
FOR320_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-04-for-320-circular-arcs-stroke-butt-micro-fixture-proof.md"
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

STATIC_SCENES = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/data/scenes.json"
GENERATED_FILES = [
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json",
    PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json",
]

DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_APPLIED"
DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_BLOCKED"
DECISION_UNSAFE = "CIRCULAR_ARCS_STROKE_BUTT_UNSAFE_SUPPORT_CLAIM_FOUND"
FOR320_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED"
FOR319_DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED"

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

EXPECTED_ARTIFACT_IDS = [
    "skia.png",
    "cpu.png",
    "gpu.png",
    "cpu-reference-diff-and-stats",
    "gpu-reference-diff-and-stats",
    "route-cpu.json",
    "route-gpu.json",
]

SELECTED_CELL_EXPECTED = {
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


def fail(message: str) -> None:
    raise SystemExit(f"FOR-321 validation failed: {message}")


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
    expected_range = {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX}
    if policy.get("strokeWidthBudget") != expected_range:
        fail(f"{label}.strokeWidthBudget was weakened or malformed")


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


def validate_selected_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected}, got {cell.get(key)}")
    if cell.get("includedCaps") != ["kButt_Cap"]:
        fail(f"{label} must include only kButt_Cap")
    if sorted(cell.get("excludedCaps", [])) != ["kRound_Cap", "kSquare_Cap"]:
        fail(f"{label} must exclude round and square caps")
    if cell.get("useCenter") is not False:
        fail(f"{label} must exclude useCenter=true")
    if cell.get("strokeWidth") == 0:
        fail(f"{label} includes forbidden hairline strokeWidth=0")


def validate_for319_contract() -> dict[str, Any]:
    data = load_json(FOR319_ARTIFACT)
    report = read_text(FOR319_REPORT)
    if data.get("linear") != "FOR-319":
        fail("FOR-319 artifact has unexpected linear id")
    if data.get("decision") != FOR319_DECISION_APPLIED:
        fail("FOR-319 applied decision changed")

    fixture = data.get("microFixture")
    if not isinstance(fixture, dict):
        fail("FOR-319 artifact missing microFixture")
    if fixture.get("id") != FOR319_FIXTURE_ID:
        fail("FOR-319 fixture id changed")
    if fixture.get("sourceFutureTarget") != FOR318_TARGET_ID:
        fail("FOR-319 no longer follows FOR-318")
    if fixture.get("sourceRowId") != SOURCE_ROW_ID or fixture.get("sourceGm") != SOURCE_GM:
        fail("FOR-319 fixture must derive from CircularArcsStrokeButtGM")

    cell = fixture.get("selectedCell")
    if not isinstance(cell, dict):
        fail("FOR-319 fixture missing selectedCell")
    validate_selected_cell(cell, "FOR-319 selectedCell")

    budget = fixture.get("budgetPreflight")
    if not isinstance(budget, dict):
        fail("FOR-319 fixture missing budgetPreflight")
    if budget.get("webgpuAaEdgeBudget") != EDGE_BUDGET:
        fail("FOR-319 WebGPU AA edge budget changed")
    if budget.get("edgeBudgetMayIncrease") is not False:
        fail("FOR-319 unexpectedly allows edge-budget increase")
    if budget.get("selectedDrawArcCalls") != 2:
        fail("FOR-319 selected drawArc count changed")

    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("FOR-319 artifact missing supportGuard")
    if guard.get("supportStatus") != "not-supported":
        fail("FOR-319 support status must remain not-supported")
    if guard.get("currentSupportClaim") != "none":
        fail("FOR-319 unexpectedly claims support")

    policy = data.get("budgetPolicy")
    if not isinstance(policy, dict):
        fail("FOR-319 artifact missing budgetPolicy")
    validate_budget_fields(policy, "FOR-319 budgetPolicy")
    if policy.get("rendererOrShaderChangeAllowed") is not False:
        fail("FOR-319 unexpectedly allows renderer or shader changes")
    if policy.get("sceneStatusChangeAllowed") is not False:
        fail("FOR-319 unexpectedly allows scene status changes")

    for needle in [
        FOR319_FIXTURE_ID,
        "single-gm-cell-reporting-only",
        "not-supported",
        "start=0",
        "sweep=90",
        "useCenter=false",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
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


def validate_for320_contract() -> dict[str, Any]:
    data = load_json(FOR320_ARTIFACT)
    report = read_text(FOR320_REPORT)
    if data.get("linear") != "FOR-320":
        fail("FOR-320 artifact has unexpected linear id")
    if data.get("decision") != FOR320_DECISION_BLOCKED:
        fail("FOR-320 proof decision must remain blocked")

    follows = data.get("follows")
    if not isinstance(follows, dict) or not isinstance(follows.get("for319"), dict):
        fail("FOR-320 artifact must reference FOR-319")
    if follows["for319"].get("fixtureId") != FOR319_FIXTURE_ID:
        fail("FOR-320 FOR-319 linkage changed fixture id")

    fixture = data.get("microFixture")
    if not isinstance(fixture, dict):
        fail("FOR-320 artifact missing microFixture")
    if fixture.get("id") != FOR319_FIXTURE_ID:
        fail("FOR-320 fixture id changed")
    if fixture.get("supportStatus") != "not-supported":
        fail("FOR-320 fixture support status must remain not-supported")
    cell = fixture.get("selectedCell")
    if not isinstance(cell, dict):
        fail("FOR-320 fixture missing selectedCell")
    validate_selected_cell(cell, "FOR-320 selectedCell")

    bundle = data.get("proofBundle")
    if not isinstance(bundle, dict):
        fail("FOR-320 artifact missing proofBundle")
    if bundle.get("proofComplete") is not False:
        fail("FOR-320 proof must remain incomplete")
    blocked_until = bundle.get("blockedUntil")
    if not isinstance(blocked_until, list):
        fail("FOR-320 proofBundle.blockedUntil must be a list")
    for expected in [
        "Skia/reference artifact",
        "CPU artifact",
        "adapter-backed GPU artifact",
        "CPU/GPU diff and stats",
        "route diagnostics with edge-count and fallback fields",
    ]:
        if expected not in blocked_until:
            fail(f"FOR-320 blockedUntil missing `{expected}`")

    available = data.get("availableNonPromotingEvidence")
    if not isinstance(available, dict):
        fail("FOR-320 artifact missing availableNonPromotingEvidence")
    if available.get("acceptedForSelectedCellPromotion") is not False:
        fail("FOR-320 full-GM evidence must not be accepted as selected-cell proof")

    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("FOR-320 artifact missing supportGuard")
    if guard.get("supportStatus") != "not-supported":
        fail("FOR-320 support status must remain not-supported")
    if guard.get("currentSupportClaim") != "none":
        fail("FOR-320 unexpectedly claims support")
    if guard.get("readinessMovement") is not False or guard.get("releaseGateChanged") is not False:
        fail("FOR-320 unexpectedly moves readiness or release gates")

    policy = data.get("budgetPolicy")
    if not isinstance(policy, dict):
        fail("FOR-320 artifact missing budgetPolicy")
    validate_budget_fields(policy, "FOR-320 budgetPolicy")
    if policy.get("rendererOrShaderChangeAllowed") is not False:
        fail("FOR-320 unexpectedly allows renderer or shader changes")
    if policy.get("sceneStatusChangeAllowed") is not False:
        fail("FOR-320 unexpectedly allows scene status changes")

    for needle in [
        FOR320_DECISION_BLOCKED,
        FOR319_FIXTURE_ID,
        "row-specific Skia/reference",
        "adapter-backed GPU",
        "route diagnostics",
        "not-supported",
    ]:
        if needle not in report:
            fail(f"FOR-320 report missing `{needle}`")

    return {
        "artifact": rel(FOR320_ARTIFACT),
        "report": rel(FOR320_REPORT),
        "decision": data["decision"],
        "blockedUntil": blocked_until,
        "availableNonPromotingEvidence": available,
    }


def validate_source_contracts() -> dict[str, str]:
    for318 = load_json(FOR318_ARTIFACT)
    for318_report = read_text(FOR318_REPORT)
    m60 = read_text(M60_SPEC)
    fidelity = read_text(FIDELITY_SPEC)
    gm = read_text(CIRCULAR_ARCS_GM)
    test = read_text(STROKE_BUTT_TEST)
    gpu_scores = read_text(GPU_SCORE_FILE)
    cpu_scores = read_text(CPU_SCORE_FILE)
    cpu_report = read_text(CPU_REPORT_FILE)

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
        "canvas.drawArc(kRect, start, sweep, useCenter, p0)",
        "canvas.drawArc(kRect, start, -(360f - sweep), useCenter, p1)",
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
        fail("full-GM WebGPU score changed or disappeared")
    if "CircularArcsStrokeButtGM=45.6605" not in cpu_scores:
        fail("full-GM CPU score changed or disappeared")
    if "| CircularArcsStrokeButtGM | 45.66%" not in cpu_report:
        fail("full-GM CPU similarity report entry changed or disappeared")
    if FOR318_TARGET_ID not in for318_report or "not-supported" not in for318_report:
        fail("FOR-318 report no longer documents the future target")

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


def blocked_item(
    artifact_id: str,
    *,
    kind: str,
    blocked_reason: str,
    missing_tooling: str,
    selected_cell_path: str | None = None,
    selected_cell_paths: dict[str, str | None] | None = None,
    required_fields: list[str] | None = None,
    available_full_gm_evidence: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "id": artifact_id,
        "kind": kind,
        "status": "blocked",
        "complete": False,
        "selectedCellPath": selected_cell_path,
        "selectedCellPaths": selected_cell_paths,
        "blockedReason": blocked_reason,
        "missingTooling": missing_tooling,
        "requiredFields": required_fields or [],
        "availableFullGmEvidence": available_full_gm_evidence or [],
        "fullGmSubstitutionAccepted": False,
    }


def build_artifact_coverage(source_files: dict[str, str]) -> dict[str, Any]:
    full_gm_reference = source_files["fullGmReferencePng"]
    full_gm_gpu = source_files["fullGmGpuScoreFile"]
    full_gm_cpu = source_files["fullGmCpuScoreFile"]
    full_gm_cpu_report = source_files["fullGmCpuReport"]

    items = [
        blocked_item(
            "skia.png",
            kind="skia-reference-png",
            blocked_reason=(
                "No selected-cell Skia/reference PNG exists for the FOR-319 cell; "
                "the checked-in reference is the full 512-arc GM."
            ),
            missing_tooling=(
                "No strict-scope command, scene contract, or checked-in harness can "
                "render only the selected CircularArcsStrokeButtGM cell as skia.png."
            ),
            available_full_gm_evidence=[full_gm_reference],
        ),
        blocked_item(
            "cpu.png",
            kind="cpu-render-png",
            blocked_reason=(
                "No selected-cell CPU PNG exists; available CPU evidence is a "
                "full-GM similarity score/report."
            ),
            missing_tooling=(
                "No strict-scope CPU render artifact emitter exists for the selected "
                "drawArc pair without adding or changing tests/harness code."
            ),
            available_full_gm_evidence=[full_gm_cpu, full_gm_cpu_report],
        ),
        blocked_item(
            "gpu.png",
            kind="adapter-backed-webgpu-png",
            blocked_reason=(
                "No selected-cell adapter-backed GPU PNG exists; the existing WebGPU "
                "test renders the full GM."
            ),
            missing_tooling=(
                "No strict-scope adapter-backed WebGPU artifact emitter can target "
                "only the FOR-319 cell without modifying test or renderer code."
            ),
            available_full_gm_evidence=[full_gm_gpu],
        ),
        blocked_item(
            "cpu-reference-diff-and-stats",
            kind="cpu-reference-diff-stats",
            blocked_reason=(
                "No selected-cell CPU/reference diff image or stats payload can be "
                "computed because selected-cell skia.png and cpu.png are absent."
            ),
            missing_tooling=(
                "Missing selected-cell reference/CPU PNG inputs and a strict-scope "
                "diff/stat emission path for this fixture."
            ),
            selected_cell_paths={"diff": None, "stats": None},
            available_full_gm_evidence=[full_gm_cpu_report],
        ),
        blocked_item(
            "gpu-reference-diff-and-stats",
            kind="gpu-reference-diff-stats",
            blocked_reason=(
                "No selected-cell GPU/reference diff image or stats payload can be "
                "computed because selected-cell skia.png and adapter-backed gpu.png "
                "are absent."
            ),
            missing_tooling=(
                "Missing selected-cell reference/GPU PNG inputs and a strict-scope "
                "adapter-backed diff/stat emission path for this fixture."
            ),
            selected_cell_paths={"diff": None, "stats": None},
            available_full_gm_evidence=[full_gm_gpu],
        ),
        blocked_item(
            "route-cpu.json",
            kind="cpu-route-diagnostics",
            blocked_reason=(
                "No selected-cell CPU route JSON exists with edge-count and fallback "
                "fields for the two drawArc calls."
            ),
            missing_tooling=(
                "No strict-scope route diagnostics emitter can isolate the selected "
                "cell and serialize CPU edge-count/fallback fields."
            ),
            required_fields=["edge-count", "edgeCount", "fallback", "fallbackReason"],
        ),
        blocked_item(
            "route-gpu.json",
            kind="gpu-route-diagnostics",
            blocked_reason=(
                "No selected-cell GPU route JSON exists with edge-count and fallback "
                "fields for the two drawArc calls."
            ),
            missing_tooling=(
                "No strict-scope adapter-backed route diagnostics emitter can isolate "
                "the selected cell and serialize GPU edge-count/fallback fields."
            ),
            required_fields=["edge-count", "edgeCount", "fallback", "fallbackReason"],
        ),
    ]
    return {
        "expectedArtifactIds": EXPECTED_ARTIFACT_IDS,
        "items": items,
        "coverageComplete": all(item.get("complete") is True for item in items),
        "blockedArtifactIds": [item["id"] for item in items if item.get("complete") is not True],
        "fullGmSubstitutionRejected": True,
        "fullGmSubstitutionReason": (
            "Full-GM reference, CPU, and GPU scores cover the 512-arc GM and are "
            "not row-specific to the selected FOR-319 cell."
        ),
    }


def build_artifact() -> dict[str, Any]:
    for319 = validate_for319_contract()
    for320 = validate_for320_contract()
    source_files = validate_source_contracts()
    preserved_fallbacks = validate_preserved_fallbacks()
    artifact_coverage = build_artifact_coverage(source_files)

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_BLOCKED,
        "blockedDecisionReason": (
            "The strict-scope repository state cannot produce selected-cell "
            "skia.png, cpu.png, adapter-backed gpu.png, CPU/GPU reference diffs "
            "and stats, or route diagnostics for the FOR-319 cell without "
            "out-of-scope harness, test, renderer, or shader changes."
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
            "for320": {
                "artifact": for320["artifact"],
                "report": for320["report"],
                "decision": for320["decision"],
                "blockedUntil": for320["blockedUntil"],
            },
            "for318": {
                "artifact": source_files["for318Artifact"],
                "report": source_files["for318Report"],
                "futureTarget": FOR318_TARGET_ID,
                "supportStatus": "not-supported",
            },
        },
        "sourceReports": {
            "m60FeatureSpec": source_files["m60FeatureSpec"],
            "fidelitySpec": source_files["fidelitySpec"],
        },
        "sourceCode": {
            "circularArcsGm": source_files["circularArcsGm"],
            "strokeButtWebGpuTest": source_files["strokeButtWebGpuTest"],
        },
        "selectedCell": {
            "fixtureId": for319["fixtureId"],
            "sourceFutureTarget": for319["sourceFutureTarget"],
            "sourceRowId": for319["sourceRowId"],
            "sourceGm": for319["sourceGm"],
            "supportStatus": "not-supported",
            "cell": for319["selectedCell"],
        },
        "artifactCoverage": artifact_coverage,
        "availableNonPromotingEvidence": {
            "fullGmReferencePng": source_files["fullGmReferencePng"],
            "fullGmGpuScoreFile": source_files["fullGmGpuScoreFile"],
            "fullGmGpuScore": "CircularArcsStrokeButtGM=96.87",
            "fullGmCpuScoreFile": source_files["fullGmCpuScoreFile"],
            "fullGmCpuScore": "CircularArcsStrokeButtGM=45.6605",
            "fullGmCpuReport": source_files["fullGmCpuReport"],
            "acceptedForSelectedCellPromotion": False,
            "reason": artifact_coverage["fullGmSubstitutionReason"],
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
            "appliedDecisionIfComplete": DECISION_APPLIED,
            "supportedStatusesThatRequireCompleteProof": sorted(SUPPORTED_STATUSES),
        },
        "budgetPolicy": {
            "webgpuAaEdgeBudget": EDGE_BUDGET,
            "edgeBudgetMayIncrease": False,
            "strokeWidthBudget": {"minPx": STROKE_WIDTH_MIN, "maxPx": STROKE_WIDTH_MAX},
            "hairlineStrokeWidth0Supported": False,
            "fallbackWeakeningAllowed": False,
            "thresholdWeakeningAllowed": False,
            "rendererOrShaderChangeAllowed": False,
            "existingTestsChangeAllowed": False,
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
            "no support claim for CircularArcsStrokeButtGM or the selected cell",
            "no full-GM substitution",
            "no hairline strokeWidth=0",
            "no round cap, square cap, fill, dash, useCenter=true, or full-GM scope",
        ],
        "alternateDecisions": {
            "applied": DECISION_APPLIED,
            "unsafe": DECISION_UNSAFE,
        },
    }


def path_is_selected_cell_artifact(path_value: str) -> bool:
    return (
        "circular-arcs-stroke-butt-selected-cell-artifacts-for321" in path_value
        and "circular_arcs_stroke_butt.png" not in path_value
        and "CircularArcsStrokeButtGM=" not in path_value
    )


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")

    coverage = data.get("artifactCoverage")
    if not isinstance(coverage, dict):
        fail("artifact missing artifactCoverage")
    if coverage.get("expectedArtifactIds") != EXPECTED_ARTIFACT_IDS:
        fail("artifactCoverage expected artifact list changed")
    items = coverage.get("items")
    if not isinstance(items, list):
        fail("artifactCoverage.items must be a list")
    by_id = {item.get("id"): item for item in items if isinstance(item, dict)}
    if set(by_id) != set(EXPECTED_ARTIFACT_IDS):
        fail("artifactCoverage.items does not match the required artifact ids")

    coverage_complete = all(item.get("complete") is True for item in items)
    if coverage.get("coverageComplete") is not coverage_complete:
        fail("artifactCoverage.coverageComplete disagrees with item completeness")
    blocked = [item["id"] for item in items if item.get("complete") is not True]
    if coverage.get("blockedArtifactIds") != blocked:
        fail("artifactCoverage.blockedArtifactIds disagrees with item completeness")
    if coverage_complete and data.get("decision") != DECISION_APPLIED:
        fail("complete selected-cell artifacts must use the applied decision")
    if not coverage_complete and data.get("decision") != DECISION_BLOCKED:
        fail("incomplete selected-cell artifacts must use the blocked decision")
    if coverage.get("fullGmSubstitutionRejected") is not True:
        fail("full-GM substitution must be rejected")

    for artifact_id, item in by_id.items():
        status = item.get("status")
        complete = item.get("complete")
        selected_path = item.get("selectedCellPath")
        selected_paths = item.get("selectedCellPaths")
        if complete is True:
            if status != "available":
                fail(f"{artifact_id} complete item must be status=available")
            if isinstance(selected_path, str):
                if not path_is_selected_cell_artifact(selected_path):
                    fail(f"{artifact_id} path is not a selected-cell artifact path")
            elif isinstance(selected_paths, dict):
                for key, value in selected_paths.items():
                    if not isinstance(value, str) or not path_is_selected_cell_artifact(value):
                        fail(f"{artifact_id}.{key} is not a selected-cell artifact path")
            else:
                fail(f"{artifact_id} complete item has no selected-cell path")
        else:
            if status != "blocked":
                fail(f"{artifact_id} incomplete item must be status=blocked")
            if not item.get("blockedReason"):
                fail(f"{artifact_id} blocked item missing blockedReason")
            if not item.get("missingTooling"):
                fail(f"{artifact_id} blocked item missing missingTooling")
        if item.get("fullGmSubstitutionAccepted") is not False:
            fail(f"{artifact_id} accepts full-GM substitution")
        full_gm_evidence = item.get("availableFullGmEvidence")
        if full_gm_evidence is not None and not isinstance(full_gm_evidence, list):
            fail(f"{artifact_id} availableFullGmEvidence must be a list")

    for route_id in ["route-cpu.json", "route-gpu.json"]:
        required_fields = by_id[route_id].get("requiredFields")
        if not isinstance(required_fields, list):
            fail(f"{route_id} must list required route fields")
        for field in ["edge-count", "fallback"]:
            if field not in required_fields:
                fail(f"{route_id} must require {field}")

    selected = data.get("selectedCell")
    if not isinstance(selected, dict):
        fail("artifact missing selectedCell")
    if selected.get("fixtureId") != FOR319_FIXTURE_ID:
        fail("selectedCell fixture id changed")
    if selected.get("supportStatus") != "not-supported":
        fail("selectedCell support status must remain not-supported")
    cell = selected.get("cell")
    if not isinstance(cell, dict):
        fail("selectedCell.cell must be an object")
    validate_selected_cell(cell, "FOR-321 selectedCell")

    available = data.get("availableNonPromotingEvidence")
    if not isinstance(available, dict):
        fail("artifact missing availableNonPromotingEvidence")
    if available.get("acceptedForSelectedCellPromotion") is not False:
        fail("full-GM evidence must not be accepted as selected-cell promotion proof")

    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("artifact missing supportGuard")
    support_status = str(guard.get("supportStatus", "")).lower()
    readiness_moved = (
        guard.get("readinessMovement") is True
        or guard.get("readinessScoreChanged") is True
        or guard.get("releaseGateChanged") is True
    )
    if support_status in SUPPORTED_STATUSES and not coverage_complete:
        fail("selected cell claims support without complete artifact coverage")
    if guard.get("currentSupportClaim") != "none" and not coverage_complete:
        fail("selected cell declares support without complete artifact coverage")
    if guard.get("declaredSupportEvidenceComplete") is True and not coverage_complete:
        fail("supportGuard declares complete evidence while coverage is incomplete")
    if readiness_moved and not coverage_complete:
        fail("selected cell declares readiness/release movement without artifacts")
    if guard.get("supportStatus") != "not-supported":
        fail("FOR-321 must keep support not-supported")

    policy = data.get("budgetPolicy")
    if not isinstance(policy, dict):
        fail("artifact missing budgetPolicy")
    validate_budget_fields(policy, "FOR-321 budgetPolicy")
    if policy.get("rendererOrShaderChangeAllowed") is not False:
        fail("FOR-321 unexpectedly allows renderer or shader changes")
    if policy.get("existingTestsChangeAllowed") is not False:
        fail("FOR-321 unexpectedly allows existing test changes")
    if policy.get("sceneStatusChangeAllowed") is not False:
        fail("FOR-321 unexpectedly allows scene status changes")


def write_artifact(data: dict[str, Any]) -> None:
    FOR321_ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    FOR321_ARTIFACT.write_text(
        json.dumps(data, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )


def write_report(data: dict[str, Any]) -> None:
    selected = data["selectedCell"]
    cell = selected["cell"]
    coverage = data["artifactCoverage"]
    coverage_lines = "\n".join(
        "| `{id}` | `{status}` | `{complete}` | `{blockedReason}` | `{missingTooling}` |".format(
            **item
        )
        for item in coverage["items"]
    )
    fallback_lines = "\n".join(
        "| `{id}` | `{status}` | `{fallbackReason}` | `{source}` |".format(**row)
        for row in data["preservedFallbackEvidence"]
    )
    non_goal_lines = "\n".join(f"- {item}" for item in data["nonGoals"])

    report = f"""# FOR-321 CircularArcsStrokeButt Selected-Cell Artifacts

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-321 checks whether the exact FOR-319 selected cell can produce a complete
selected-cell artifact bundle. The decision is blocked: the current strict
scope has no generator for row-specific `skia.png`, `cpu.png`, adapter-backed
`gpu.png`, CPU/reference diff and stats, GPU/reference diff and stats, or
`route-cpu.json` / `route-gpu.json` diagnostics with `edge-count` and fallback
fields.

The selected cell and the source GM remain `not-supported`. No renderer,
shader, existing test, threshold, fallback, scene status, readiness score, or
release gate changes are made.

## Source Linkage

- FOR-319 artifact: `{data["follows"]["for319"]["artifact"]}`
- FOR-319 report: `{data["follows"]["for319"]["report"]}`
- FOR-320 artifact: `{data["follows"]["for320"]["artifact"]}`
- FOR-320 report: `{data["follows"]["for320"]["report"]}`
- FOR-318 artifact: `{data["follows"]["for318"]["artifact"]}`
- FOR-318 report: `{data["follows"]["for318"]["report"]}`
- M60 feature spec: `{data["sourceReports"]["m60FeatureSpec"]}`
- Fidelity spec: `{data["sourceReports"]["fidelitySpec"]}`
- GM source: `{data["sourceCode"]["circularArcsGm"]}`
- Existing WebGPU test: `{data["sourceCode"]["strokeButtWebGpuTest"]}`

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `{selected["fixtureId"]}` |
| source future target | `{selected["sourceFutureTarget"]}` |
| source row | `{selected["sourceRowId"]}` |
| source GM | `{selected["sourceGm"]}` |
| support status | `{selected["supportStatus"]}` |
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

The selected cell is exactly
`circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true`:
`start=0`, `sweep=90`, `complement=-270`, `useCenter=false`, `aa=true`,
`strokeWidth=15`, `strokeCap=kButt_Cap`, with two `drawArc` calls. Hairline,
round cap, square cap, fill, dash, `useCenter=true`, and full-GM substitution
remain excluded.

## Artifact Coverage

| Artifact | Status | Complete | Blocked reason | Missing tooling |
|---|---|---:|---|---|
{coverage_lines}

Coverage complete: `{coverage["coverageComplete"]}`

Blocked artifacts: `{", ".join(coverage["blockedArtifactIds"])}`

## Full-GM Substitution Rejection

Existing full-GM evidence is recorded but rejected as selected-cell proof:

- full-GM reference PNG: `{data["availableNonPromotingEvidence"]["fullGmReferencePng"]}`
- full-GM WebGPU score: `{data["availableNonPromotingEvidence"]["fullGmGpuScore"]}`
- full-GM CPU score: `{data["availableNonPromotingEvidence"]["fullGmCpuScore"]}`

Reason: {data["artifactCoverage"]["fullGmSubstitutionReason"]}

## Budget And Support Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `{data["budgetPolicy"]["webgpuAaEdgeBudget"]}` |
| edge budget may increase | `{data["budgetPolicy"]["edgeBudgetMayIncrease"]}` |
| stroke-width budget | `{data["budgetPolicy"]["strokeWidthBudget"]["minPx"]}..{data["budgetPolicy"]["strokeWidthBudget"]["maxPx"]}` |
| hairline strokeWidth=0 supported | `{data["budgetPolicy"]["hairlineStrokeWidth0Supported"]}` |
| threshold weakening allowed | `{data["budgetPolicy"]["thresholdWeakeningAllowed"]}` |
| fallback weakening allowed | `{data["budgetPolicy"]["fallbackWeakeningAllowed"]}` |
| renderer or shader change allowed | `{data["budgetPolicy"]["rendererOrShaderChangeAllowed"]}` |
| existing tests change allowed | `{data["budgetPolicy"]["existingTestsChangeAllowed"]}` |
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

- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(FOR321_ARTIFACT)} >/dev/null`
- `rtk git diff --check`
"""
    FOR321_REPORT.write_text(report, encoding="utf-8")


def validate_report() -> None:
    report = read_text(FOR321_REPORT)
    for needle in [
        DECISION_BLOCKED,
        FOR319_FIXTURE_ID,
        "start=0",
        "sweep=90",
        "complement=-270",
        "useCenter=false",
        "aa=true",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        "two `drawArc` calls",
        "not-supported",
        "skia.png",
        "cpu.png",
        "gpu.png",
        "CPU/reference diff and stats",
        "GPU/reference diff and stats",
        "route-cpu.json",
        "route-gpu.json",
        "edge-count",
        "full-GM substitution",
        "rejected as selected-cell proof",
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
        r"\bCoverage complete:\s+`True`",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, re.IGNORECASE):
            fail(f"report appears to contain unsafe claim matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    write_report(data)

    generated = load_json(FOR321_ARTIFACT)
    validate_artifact_shape(generated)
    validate_report()

    print(f"{LINEAR_ID}: {DECISION_BLOCKED}")
    print(f"fixture={data['selectedCell']['fixtureId']}")
    print(f"coverageComplete={data['artifactCoverage']['coverageComplete']}")
    print(f"blockedArtifacts={','.join(data['artifactCoverage']['blockedArtifactIds'])}")
    print(f"artifact={rel(FOR321_ARTIFACT)}")
    print(f"report={rel(FOR321_REPORT)}")


if __name__ == "__main__":
    main()
