#!/usr/bin/env python3
"""Validate the FOR-322 CircularArcsStrokeButt selected-cell harness."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-322"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-harness-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-322-circular-arcs-stroke-butt-selected-cell-harness.md"
)
TEST_FILE = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    "CircularArcsStrokeButtSelectedCellCaptureTest.kt"
)

FOR321_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321.json"
)
FOR321_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-321-circular-arcs-stroke-butt-selected-cell-artifacts.md"
)
FOR320_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320/"
    "circular-arcs-stroke-butt-micro-fixture-proof-for320.json"
)
FOR319_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-micro-fixture-for319/"
    "circular-arcs-stroke-butt-micro-fixture-for319.json"
)
FULL_GM_REFERENCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png"
)

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_BLOCKED"
DECISION_GPU_ADAPTER_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_GPU_ADAPTER_MISSING"
DECISION_CPU_ROUTE_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_ROUTE_MISSING"

FOR321_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_BLOCKED"
FOR320_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED"
FOR319_DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED"
FOR319_FIXTURE_ID = "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true"
SOURCE_GM = "CircularArcsStrokeButtGM"
SOURCE_ROW_ID = "circular-arcs-stroke-butt-webgpu"
FOR318_TARGET_ID = "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe"
EDGE_BUDGET = 256
COMMAND = (
    "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
    "--tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellCaptureTest"
)

EXPECTED_SLOTS = [
    "skia.png",
    "cpu.png",
    "gpu.png",
    "cpu-diff.png",
    "gpu-diff.png",
    "route-cpu.json",
    "route-gpu.json",
    "stats.json",
]

SELECTED_CELL_EXPECTED = {
    "fixtureId": FOR319_FIXTURE_ID,
    "sourceGm": SOURCE_GM,
    "sourceRowId": SOURCE_ROW_ID,
    "sourceFutureTarget": FOR318_TARGET_ID,
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
    "includedCaps": ["kButt_Cap"],
    "excludedCaps": ["kRound_Cap", "kSquare_Cap"],
    "includesHairlineStrokeWidth0": False,
    "includesFill": False,
    "includesDash": False,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-322 validation failed: {message}")


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


def optional_json(path: Path) -> dict[str, Any] | None:
    if not path.is_file():
        return None
    return load_json(path)


def validate_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")
    if cell.get("fullGmCanvasArcRectLTRB") != [140, 520, 180, 560]:
        fail(f"{label} must document the full-GM selected cell rect")
    if cell.get("boundedCanvasArcRectLTRB") != [20, 20, 60, 60]:
        fail(f"{label} must document the bounded harness rect")


def validate_previous_contracts() -> dict[str, Any]:
    for319 = load_json(FOR319_ARTIFACT)
    for320 = load_json(FOR320_ARTIFACT)
    for321 = load_json(FOR321_ARTIFACT)
    for321_report = read_text(FOR321_REPORT)

    if for319.get("linear") != "FOR-319" or for319.get("decision") != FOR319_DECISION_APPLIED:
        fail("FOR-319 preflight contract changed")
    fixture = for319.get("microFixture")
    if not isinstance(fixture, dict) or fixture.get("id") != FOR319_FIXTURE_ID:
        fail("FOR-319 fixture id changed")
    if fixture.get("sourceGm") != SOURCE_GM or fixture.get("sourceRowId") != SOURCE_ROW_ID:
        fail("FOR-319 no longer derives from CircularArcsStrokeButtGM")
    selected = fixture.get("selectedCell")
    if not isinstance(selected, dict):
        fail("FOR-319 selectedCell missing")
    for key in [
        "rowIndex",
        "columnIndex",
        "startDegrees",
        "sweepDegrees",
        "complementSweepDegrees",
        "useCenter",
        "aa",
        "style",
        "strokeWidth",
        "strokeCap",
        "includedCaps",
        "excludedCaps",
        "includesHairlineStrokeWidth0",
        "includesFill",
        "includesDash",
        "drawArcCalls",
    ]:
        if selected.get(key) != SELECTED_CELL_EXPECTED[key]:
            fail(f"FOR-319 selectedCell.{key} changed")

    if for320.get("linear") != "FOR-320" or for320.get("decision") != FOR320_DECISION_BLOCKED:
        fail("FOR-320 proof contract must remain blocked")
    if for321.get("linear") != "FOR-321" or for321.get("decision") != FOR321_DECISION_BLOCKED:
        fail("FOR-321 selected-cell artifact decision must remain blocked")
    if "full-GM substitution" not in for321_report or "rejected as selected-cell proof" not in for321_report:
        fail("FOR-321 report no longer rejects full-GM substitution")

    return {
        "for319Artifact": rel(FOR319_ARTIFACT),
        "for320Artifact": rel(FOR320_ARTIFACT),
        "for321Artifact": rel(FOR321_ARTIFACT),
        "for321Report": rel(FOR321_REPORT),
    }


def validate_test_source() -> None:
    text = read_text(TEST_FILE)
    required = [
        "class CircularArcsStrokeButtSelectedCellCaptureTest",
        "class SelectedCellGM",
        'private const val SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"',
        "SkISize.Make(WIDTH, HEIGHT)",
        "SkRect.MakeLTRB(0f, 0f, 40f, 40f)",
        "c.translate(20f, 20f)",
        "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
        "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
        "isAntiAlias = true",
        "strokeWidth = 15f",
        "strokeCap = SkPaint.Cap.kButt_Cap",
        "supportStatus=not-supported",
        "fullGmSubstitutionAccepted",
        "File(dir, \"skia.png\").delete()",
    ]
    for needle in required:
        if needle not in text:
            fail(f"test source missing `{needle}`")
    forbidden = [
        r"runGpuCrossTest\s*\(",
        r"runCrossBackendTest\s*\(",
        r"CircularArcsStrokeButtGM\s*\(",
        r"loadReferenceBitmap\s*\(\s*\"?circular_arcs_stroke_butt",
    ]
    for pattern in forbidden:
        if re.search(pattern, text):
            fail(f"test source uses forbidden full-GM substitute pattern `{pattern}`")


def validate_no_full_gm_png(path: Path, label: str) -> None:
    if not path.is_file():
        return
    if path.resolve() == FULL_GM_REFERENCE.resolve():
        fail(f"{label} points at the full-GM reference PNG")
    if FULL_GM_REFERENCE.is_file() and path.read_bytes() == FULL_GM_REFERENCE.read_bytes():
        fail(f"{label} duplicates the full-GM reference PNG bytes")


def validate_route(path: Path, backend: str) -> dict[str, Any]:
    data = load_json(path)
    if data.get("linear") != LINEAR_ID:
        fail(f"{rel(path)} has wrong linear id")
    if data.get("sceneId") != SCENE_ID:
        fail(f"{rel(path)} has wrong scene id")
    if data.get("backend") != backend:
        fail(f"{rel(path)} backend expected {backend}")
    if data.get("drawKind") != "CircularArcsStrokeButtSelectedCell":
        fail(f"{rel(path)} drawKind changed")
    if data.get("command") != COMMAND:
        fail(f"{rel(path)} command changed")
    if not data.get("status"):
        fail(f"{rel(path)} missing status")
    if "fallbackReason" not in data or "refusalReason" not in data:
        fail(f"{rel(path)} missing fallback/refusal fields")
    if "edgeCount" not in data:
        fail(f"{rel(path)} missing edgeCount field")
    if data.get("edgeCount") is None and not data.get("edgeCountAbsentReason"):
        fail(f"{rel(path)} must explain absent edgeCount")
    if data.get("edgeBudget") != EDGE_BUDGET:
        fail(f"{rel(path)} edge budget changed")
    if data.get("supportStatus") != "not-supported":
        fail(f"{rel(path)} must keep supportStatus not-supported")
    if data.get("fullGmSubstitutionAccepted") is not False:
        fail(f"{rel(path)} accepts full-GM substitution")
    cell = data.get("cell")
    if not isinstance(cell, dict):
        fail(f"{rel(path)} missing cell")
    validate_cell(cell, f"{rel(path)} cell")
    return data


def validate_stats(path: Path) -> dict[str, Any]:
    data = load_json(path)
    if data.get("linear") != LINEAR_ID or data.get("sceneId") != SCENE_ID:
        fail("stats.json has wrong linear or scene id")
    if data.get("decision") != DECISION_READY:
        fail("stats.json must record the harness-ready decision")
    if data.get("supportStatus") != "not-supported":
        fail("stats.json must keep supportStatus not-supported")
    if data.get("fullGmSubstitutionAccepted") is not False:
        fail("stats.json accepts full-GM substitution")
    if data.get("fullGmReferenceAccepted") is not False:
        fail("stats.json accepts the full-GM reference")
    if data.get("command") != COMMAND:
        fail("stats.json command changed")
    if data.get("edgeBudget") != EDGE_BUDGET:
        fail("stats.json edge budget changed")
    artifacts = data.get("artifacts")
    if not isinstance(artifacts, dict):
        fail("stats.json missing artifacts map")
    for slot in EXPECTED_SLOTS:
        if slot not in artifacts:
            fail(f"stats.json missing artifact slot {slot}")
    cell = data.get("cell")
    if not isinstance(cell, dict):
        fail("stats.json missing cell")
    validate_cell(cell, "stats.json cell")
    return data


def slot_item(slot: str, status: str, reason: str, path: Path | None) -> dict[str, Any]:
    selected_path = rel(path) if path is not None and path.is_file() else None
    return {
        "id": slot,
        "status": status,
        "complete": status == "available",
        "selectedCellPath": selected_path,
        "blockedReason": None if status == "available" else reason,
        "fullGmSubstitutionAccepted": False,
    }


def build_artifact() -> dict[str, Any]:
    follows = validate_previous_contracts()
    validate_test_source()

    paths = {slot: ARTIFACT_DIR / slot for slot in EXPECTED_SLOTS}
    for slot in ["skia.png", "cpu.png", "gpu.png", "cpu-diff.png", "gpu-diff.png"]:
        validate_no_full_gm_png(paths[slot], slot)

    route_cpu = validate_route(paths["route-cpu.json"], "CPU")
    route_gpu = validate_route(paths["route-gpu.json"], "WebGPU")
    stats = validate_stats(paths["stats.json"])

    items = [
        slot_item(
            "skia.png",
            "available" if paths["skia.png"].is_file() else "blocked",
            "No checked-in upstream Skia selected-cell reference exists; the harness does not substitute the full-GM PNG.",
            paths["skia.png"],
        ),
        slot_item("cpu.png", "available" if paths["cpu.png"].is_file() else "blocked", "CPU selected-cell PNG missing; run the FOR-322 harness.", paths["cpu.png"]),
        slot_item(
            "gpu.png",
            "available" if paths["gpu.png"].is_file() else "blocked",
            f"WebGPU selected-cell PNG unavailable: {route_gpu.get('refusalReason', 'gpu-capture-missing')}",
            paths["gpu.png"],
        ),
        slot_item("cpu-diff.png", "available" if paths["cpu-diff.png"].is_file() else "blocked", "CPU diff PNG missing; run the FOR-322 harness.", paths["cpu-diff.png"]),
        slot_item(
            "gpu-diff.png",
            "available" if paths["gpu-diff.png"].is_file() else "blocked",
            f"WebGPU selected-cell diff unavailable: {route_gpu.get('refusalReason', 'gpu-capture-missing')}",
            paths["gpu-diff.png"],
        ),
        slot_item("route-cpu.json", "available", "CPU route missing.", paths["route-cpu.json"]),
        slot_item("route-gpu.json", "available", "WebGPU route missing.", paths["route-gpu.json"]),
        slot_item("stats.json", "available", "stats missing.", paths["stats.json"]),
    ]
    blocked = [item["id"] for item in items if item["status"] != "available"]

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_READY,
        "sceneId": SCENE_ID,
        "harness": {
            "test": rel(TEST_FILE),
            "command": COMMAND,
            "headless": True,
            "bounded": True,
            "modifiesProductionRenderer": False,
            "modifiesWgslShaders": False,
            "modifiesThresholds": False,
            "modifiesFallbackPolicy": False,
            "modifiesSceneSupportStatus": False,
        },
        "follows": follows,
        "selectedCell": stats["cell"],
        "artifactCoverage": {
            "expectedArtifactIds": EXPECTED_SLOTS,
            "coverageComplete": len(blocked) == 0,
            "blockedArtifactIds": blocked,
            "items": items,
            "fullGmSubstitutionRejected": True,
            "fullGmReferencePng": rel(FULL_GM_REFERENCE),
        },
        "routes": {
            "cpu": {
                "path": rel(paths["route-cpu.json"]),
                "backend": route_cpu["backend"],
                "status": route_cpu["status"],
                "fallbackReason": route_cpu["fallbackReason"],
                "refusalReason": route_cpu["refusalReason"],
                "edgeCount": route_cpu["edgeCount"],
                "edgeCountAbsentReason": route_cpu["edgeCountAbsentReason"],
                "edgeBudget": route_cpu["edgeBudget"],
                "drawKind": route_cpu["drawKind"],
                "command": route_cpu["command"],
            },
            "gpu": {
                "path": rel(paths["route-gpu.json"]),
                "backend": route_gpu["backend"],
                "status": route_gpu["status"],
                "fallbackReason": route_gpu["fallbackReason"],
                "refusalReason": route_gpu["refusalReason"],
                "edgeCount": route_gpu["edgeCount"],
                "edgeCountAbsentReason": route_gpu["edgeCountAbsentReason"],
                "edgeBudget": route_gpu["edgeBudget"],
                "drawKind": route_gpu["drawKind"],
                "command": route_gpu["command"],
            },
        },
        "stats": {
            "path": rel(paths["stats.json"]),
            "cpuSimilarity": stats["cpuSimilarity"],
            "gpuStatus": stats["gpuStatus"],
            "gpuSimilarityToCpu": stats["gpuSimilarityToCpu"],
            "edgeBudget": stats["edgeBudget"],
        },
        "supportGuard": {
            "supportStatus": "not-supported",
            "currentSupportClaim": "none",
            "readinessMovement": False,
            "releaseGateChanged": False,
            "fullGmSubstitutionAccepted": False,
            "alternativeDecisions": [
                DECISION_GPU_ADAPTER_MISSING,
                DECISION_CPU_ROUTE_MISSING,
                DECISION_BLOCKED,
            ],
        },
        "nonGoals": [
            "no production renderer behavior changed",
            "no WGSL shader changed",
            "no support threshold changed",
            "no fallback policy changed",
            "no scene support status changed",
            "no CircularArcsStrokeButtGM support promotion",
            "no selected-cell support promotion",
            "no full-GM PNG or score substitution",
        ],
    }


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("summary linear id changed")
    if data.get("decision") != DECISION_READY:
        fail("summary decision changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("summary source memory changed")
    if data.get("selectedCell", {}).get("fixtureId") != FOR319_FIXTURE_ID:
        fail("summary selected cell changed")
    coverage = data.get("artifactCoverage")
    if not isinstance(coverage, dict):
        fail("summary missing artifactCoverage")
    if coverage.get("expectedArtifactIds") != EXPECTED_SLOTS:
        fail("summary expectedArtifactIds changed")
    items = coverage.get("items")
    if not isinstance(items, list):
        fail("summary artifactCoverage.items must be a list")
    by_id = {item.get("id"): item for item in items if isinstance(item, dict)}
    if set(by_id) != set(EXPECTED_SLOTS):
        fail("summary artifact slots changed")
    for item in items:
        if item.get("fullGmSubstitutionAccepted") is not False:
            fail(f"{item.get('id')} accepts full-GM substitution")
        path = item.get("selectedCellPath")
        if path is not None:
            if "original-888/circular_arcs_stroke_butt.png" in path:
                fail(f"{item.get('id')} points at full-GM reference")
            if "test-similarity-scores" in path:
                fail(f"{item.get('id')} points at full-GM score evidence")
            if SCENE_ID not in path:
                fail(f"{item.get('id')} path is not under the FOR-322 selected-cell harness directory")
    guard = data.get("supportGuard")
    if not isinstance(guard, dict):
        fail("summary missing supportGuard")
    if guard.get("supportStatus") != "not-supported":
        fail("summary must keep supportStatus not-supported")
    if guard.get("currentSupportClaim") != "none":
        fail("summary must not claim support")
    if guard.get("readinessMovement") is not False or guard.get("releaseGateChanged") is not False:
        fail("summary must not move readiness or release gates")


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    coverage = data["artifactCoverage"]
    route_cpu = data["routes"]["cpu"]
    route_gpu = data["routes"]["gpu"]
    cell = data["selectedCell"]
    slot_lines = "\n".join(
        "| `{id}` | `{status}` | `{complete}` | `{selectedCellPath}` | `{blockedReason}` |".format(**item)
        for item in coverage["items"]
    )
    non_goals = "\n".join(f"- {item}" for item in data["nonGoals"])

    report = f"""# FOR-322 CircularArcsStrokeButt Selected-Cell Harness

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-322 adds a headless bounded capture harness for the exact FOR-319
`CircularArcsStrokeButtGM` selected cell. The harness renders only the
selected cell and emits route/stat diagnostics under
`reports/wgsl-pipeline/scenes/artifacts/{SCENE_ID}/`.

The selected cell and `CircularArcsStrokeButtGM` remain `not-supported`.
This ticket does not change renderer behavior, WGSL shaders, thresholds,
fallback policy, scene status, readiness score, or release gates.

## Harness

- test: `{data["harness"]["test"]}`
- command: `{data["harness"]["command"]}`
- artifact JSON: `{rel(ARTIFACT)}`
- headless: `{data["harness"]["headless"]}`
- bounded: `{data["harness"]["bounded"]}`

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `{cell["fixtureId"]}` |
| source GM | `{cell["sourceGm"]}` |
| source row | `{cell["sourceRowId"]}` |
| row / column | `{cell["rowIndex"]}` / `{cell["columnIndex"]}` |
| start | `{cell["startDegrees"]}` |
| sweep | `{cell["sweepDegrees"]}` |
| complement | `{cell["complementSweepDegrees"]}` |
| useCenter | `{cell["useCenter"]}` |
| aa | `{cell["aa"]}` |
| stroke width | `{cell["strokeWidth"]}` |
| stroke cap | `{cell["strokeCap"]}` |
| full-GM rect | `{cell["fullGmCanvasArcRectLTRB"]}` |
| bounded rect | `{cell["boundedCanvasArcRectLTRB"]}` |
| drawArc calls | `{len(cell["drawArcCalls"])}` |

The selected cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`, with
two `drawArc` calls.

## Artifact Slots

| Artifact | Status | Complete | Selected-cell path | Blocked reason |
|---|---|---:|---|---|
{slot_lines}

Coverage complete: `{coverage["coverageComplete"]}`

Blocked artifacts: `{", ".join(coverage["blockedArtifactIds"]) if coverage["blockedArtifactIds"] else "none"}`

Full-GM substitution rejected: `{coverage["fullGmSubstitutionRejected"]}`

## Route Diagnostics

| Route | Backend | Status | Fallback/refusal | Edge-count | Budget | Draw kind |
|---|---|---|---|---|---:|---|
| `route-cpu.json` | `{route_cpu["backend"]}` | `{route_cpu["status"]}` | `{route_cpu["fallbackReason"]}` / `{route_cpu["refusalReason"]}` | `{route_cpu["edgeCount"]}` ({route_cpu["edgeCountAbsentReason"]}) | `{route_cpu["edgeBudget"]}` | `{route_cpu["drawKind"]}` |
| `route-gpu.json` | `{route_gpu["backend"]}` | `{route_gpu["status"]}` | `{route_gpu["fallbackReason"]}` / `{route_gpu["refusalReason"]}` | `{route_gpu["edgeCount"]}` ({route_gpu["edgeCountAbsentReason"]}) | `{route_gpu["edgeBudget"]}` | `{route_gpu["drawKind"]}` |

## Support Guard

| Field | Value |
|---|---|
| support status | `{data["supportGuard"]["supportStatus"]}` |
| support claim | `{data["supportGuard"]["currentSupportClaim"]}` |
| readiness movement | `{data["supportGuard"]["readinessMovement"]}` |
| release gate changed | `{data["supportGuard"]["releaseGateChanged"]}` |
| full-GM substitution accepted | `{data["supportGuard"]["fullGmSubstitutionAccepted"]}` |

## Non-Goals And Non-Changes

{non_goals}

## Validation

- `{COMMAND}`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report() -> None:
    report = read_text(REPORT)
    for needle in [
        DECISION_READY,
        FOR319_FIXTURE_ID,
        "start=0",
        "sweep=90",
        "complement=-270",
        "useCenter=false",
        "aa=true",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        "two `drawArc` calls",
        "route-cpu.json",
        "route-gpu.json",
        "edge-count",
        "Full-GM substitution rejected",
        "not-supported",
        "no selected-cell support promotion",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    unsafe_patterns = [
        r"\bsupport status\s+\|\s+`?(pass|supported|promoted|gpu-supported)",
        r"\breadiness movement\s+\|\s+`?True",
        r"\brelease gate changed\s+\|\s+`?True",
        r"\bfull-GM substitution accepted\s+\|\s+`?True",
    ]
    for pattern in unsafe_patterns:
        if re.search(pattern, report, flags=re.IGNORECASE):
            fail(f"report contains unsafe support language matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    validate_artifact_shape(load_json(ARTIFACT))
    write_report(data)
    validate_report()
    print(DECISION_READY)


if __name__ == "__main__":
    main()
