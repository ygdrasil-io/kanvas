#!/usr/bin/env python3
"""Validate the FOR-327 upstream-Skia selected-cell reference output."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

from PIL import Image


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-327"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-build-and-run-skia-selected-cell-source-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-327-circular-arcs-stroke-butt-selected-cell-skia-reference.md"
)
SKIA = ARTIFACT_DIR / "skia.png"
PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"
SOURCE = PROJECT_ROOT / "tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp"
RUNNER = PROJECT_ROOT / "tools/skia-reference/build_for327_circular_arcs_stroke_butt_selected_cell.py"

FOR326_SCENE_ID = "circular-arcs-stroke-butt-skia-selected-cell-source-for326"
FOR326_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR326_SCENE_ID
    / f"{FOR326_SCENE_ID}.json"
)
FOR325_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325"
FOR325_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR325_SCENE_ID
    / f"{FOR325_SCENE_ID}.json"
)
FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
FOR322_CPU = FOR322_DIR / "cpu.png"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"
FOR322_STATS = FOR322_DIR / "stats.json"
FULL_GM_REFERENCE = (
    PROJECT_ROOT / "skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png"
)

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_BUILD_MISSING"
DECISION_PROVENANCE_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_PROVENANCE_INVALID"
)
FOR326_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SKIA_SELECTED_CELL_SOURCE_READY"
FOR325_DECISION_MISSING = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVISIONING_MISSING"
)
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
GPU_REFUSAL = "coverage.stroke-cap-join-visual-parity-below-threshold"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
FULL_GM_CROP_BOX = (120, 500, 200, 580)
SELECTED_CELL_EXPECTED = {
    "fixtureId": "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true",
    "sourceGm": "CircularArcsStrokeButtGM",
    "sourceRowId": "circular-arcs-stroke-butt-webgpu",
    "sourceFutureTarget": "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe",
    "boundedHarnessGm": "circular-arcs-stroke-butt-selected-cell-harness-for322",
    "cellCount": 1,
    "quadrant": "bottom-left",
    "fullGmCanvasArcRectLTRB": [140, 520, 180, 560],
    "boundedCanvasArcRectLTRB": [20, 20, 60, 60],
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
    "paintAlpha": 100,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}
FORBIDDEN_PROVENANCE_TERMS = [
    "TestUtils.runGmTest",
    "CircularArcsStrokeButtSelectedCellCaptureTest",
    "original-888/circular_arcs_stroke_butt.png",
    "cpu.png",
    "test-similarity-scores",
    "test-similarity-report",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-327 validation failed: {message}")


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


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


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_size(path: Path) -> dict[str, int]:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        width, height = image.size
    return {"width": width, "height": height}


def rgba_bytes(path: Path) -> bytes:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        return image.convert("RGBA").tobytes()


def full_gm_crop_rgba_bytes() -> bytes | None:
    if not FULL_GM_REFERENCE.is_file():
        return None
    with Image.open(FULL_GM_REFERENCE) as image:
        crop = image.convert("RGBA").crop(FULL_GM_CROP_BOX)
        return crop.tobytes()


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def validate_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def validate_source() -> dict[str, Any]:
    source = read_text(SOURCE)
    for needle in [
        "SkImageInfo::MakeN32Premul(80, 80",
        "SkRect::MakeLTRB(20, 20, 60, 60)",
        "red.setStrokeCap(SkPaint::kButt_Cap)",
        "SkColorSetARGB(100, 255, 0, 0)",
        "SkColorSetARGB(100, 0, 0, 255)",
        "canvas->drawArc(arcRect, 0, 90, false, red)",
        "canvas->drawArc(arcRect, 0, -270, false, blue)",
    ]:
        if needle not in source:
            fail(f"FOR-326 source missing `{needle}`")
    return {"path": rel(SOURCE), "sha256": sha256(SOURCE), "present": True}


def validate_previous_contracts() -> dict[str, Any]:
    for326 = load_json(FOR326_ARTIFACT)
    for325 = load_json(FOR325_ARTIFACT)
    for322 = load_json(FOR322_ARTIFACT)
    route_gpu = load_json(FOR322_ROUTE_GPU)
    stats = load_json(FOR322_STATS)

    if for326.get("linear") != "FOR-326" or for326.get("decision") != FOR326_DECISION_READY:
        fail("FOR-326 source-ready prerequisite changed")
    if for325.get("linear") != "FOR-325" or for325.get("decision") != FOR325_DECISION_MISSING:
        fail("FOR-325 command-provisioning prerequisite changed")
    if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
        fail("FOR-322 selected-cell harness prerequisite changed")
    selected = for322.get("selectedCell")
    if not isinstance(selected, dict):
        fail("FOR-322 selectedCell missing")
    validate_cell(selected, "FOR-322 selectedCell")
    if route_gpu.get("status") != "expected-unsupported":
        fail("FOR-322 WebGPU route must remain expected-unsupported")
    if route_gpu.get("refusalReason") != GPU_REFUSAL:
        fail("FOR-322 WebGPU refusal reason changed")
    if stats.get("fullGmReferenceAccepted") is not False:
        fail("FOR-322 stats accepts the full-GM reference")

    return {
        "for326Artifact": rel(FOR326_ARTIFACT),
        "for325Artifact": rel(FOR325_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for322Cpu": rel(FOR322_CPU),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
        "for322Stats": rel(FOR322_STATS),
    }


def validate_runner() -> dict[str, Any]:
    text = read_text(RUNNER)
    for needle in [
        "circular_arcs_stroke_butt_selected_cell.cpp",
        "libskia.a",
        "libpng.a",
        "libzlib.a",
        "OUTPUT.unlink(missing_ok=True)",
        "sourceType",
        "isolated-skia-selected-cell-render",
        "fullGmCrop",
        "cpuKanvasOutputAcceptedAsSkia",
    ]:
        if needle not in text:
            fail(f"FOR-327 runner missing `{needle}`")
    return {"path": rel(RUNNER), "present": True, "headless": True}


def validate_no_reference() -> dict[str, Any]:
    if SKIA.exists():
        fail(f"{rel(SKIA)} exists but provenance was not accepted")
    if PROVENANCE.exists():
        fail(f"{rel(PROVENANCE)} exists without accepted skia.png")
    return {
        "path": rel(SKIA),
        "provenancePath": rel(PROVENANCE),
        "present": False,
        "accepted": False,
        "status": "blocked-upstream-skia-build-missing",
        "blockedReasons": [
            "no accepted skia.png is present under the FOR-327 artifact directory",
            "upstream Skia build/execution for the repo-owned FOR-326 source is not available",
        ],
    }


def validate_provenance(provenance: dict[str, Any], image_sha: str, source_sha: str) -> None:
    expected = {
        "sourceType": "isolated-skia-selected-cell-render",
        "fixtureId": SELECTED_CELL_EXPECTED["fixtureId"],
        "sourceGm": SELECTED_CELL_EXPECTED["sourceGm"],
        "sourceRowId": SELECTED_CELL_EXPECTED["sourceRowId"],
        "dimensions": EXPECTED_DIMENSIONS,
        "fullGmCrop": False,
        "fullGmSubstitutionAccepted": False,
        "cpuKanvasOutputAcceptedAsSkia": False,
    }
    for key, expected_value in expected.items():
        if provenance.get(key) != expected_value:
            fail(f"provenance.{key} expected {expected_value!r}, got {provenance.get(key)!r}")
    if provenance.get("outputSha256") != image_sha:
        fail("provenance.outputSha256 must match skia.png")
    if provenance.get("sourceImplementation") != rel(SOURCE):
        fail("provenance.sourceImplementation must name the FOR-326 source")
    source_for326 = provenance.get("sourceFor326")
    if not isinstance(source_for326, dict):
        fail("provenance.sourceFor326 missing")
    if source_for326.get("path") != rel(SOURCE) or source_for326.get("sha256") != source_sha:
        fail("provenance.sourceFor326 must match the repo-owned FOR-326 source")
    if not provenance.get("command"):
        fail("provenance.command must document the headless command")
    if rel(RUNNER) not in str(provenance.get("command")):
        fail("provenance.command must use the FOR-327 runner")
    build_command = provenance.get("buildCommand")
    if not isinstance(build_command, list) or not build_command:
        fail("provenance.buildCommand must document the non-empty upstream Skia compile command")
    build_command_text = json.dumps(build_command, sort_keys=True)
    for needle in [str(SOURCE), "libskia.a", "libpng.a", "libzlib.a"]:
        if needle not in build_command_text:
            fail(f"provenance.buildCommand must include `{needle}`")
    execute_command = provenance.get("executeCommand")
    if not isinstance(execute_command, list) or not execute_command:
        fail("provenance.executeCommand must document the non-empty generated binary invocation")
    execute_command_text = json.dumps(execute_command, sort_keys=True)
    if str(SKIA) not in execute_command_text:
        fail("provenance.executeCommand must write the FOR-327 skia.png path")
    if not (provenance.get("upstreamSkiaGitRevision") or provenance.get("upstreamSkiaSourceVersion")):
        fail("provenance must include upstream Skia revision/source evidence")
    proof = provenance.get("sourceTypeProof")
    if not isinstance(proof, dict):
        fail("provenance.sourceTypeProof missing")
    if proof.get("compiledRepoOwnedSource") is not True or proof.get("executedBinary") is not True:
        fail("provenance must prove compile and execution of the repo-owned source")
    if proof.get("compiledSourcePath") != rel(SOURCE):
        fail("provenance.sourceTypeProof.compiledSourcePath must match FOR-326 source")
    linked_lib = proof.get("linkedAgainstUpstreamSkiaLib")
    if not isinstance(linked_lib, str) or not linked_lib.endswith("libskia.a"):
        fail("provenance.sourceTypeProof.linkedAgainstUpstreamSkiaLib must name upstream libskia.a")
    if "libskia.a" not in build_command_text or linked_lib not in build_command_text:
        fail("provenance.buildCommand must include the upstream libskia.a proof path")
    selected = provenance.get("selectedCell")
    if not isinstance(selected, dict):
        fail("provenance.selectedCell missing")
    validate_cell(selected, "provenance selectedCell")
    cell_parameters = provenance.get("cellParameters")
    if not isinstance(cell_parameters, dict):
        fail("provenance.cellParameters missing")
    validate_cell(cell_parameters, "provenance cellParameters")

    for field in ["command", "sourceImplementation", "buildCommand", "executeCommand"]:
        value = json.dumps(provenance.get(field, ""), sort_keys=True)
        for term in FORBIDDEN_PROVENANCE_TERMS:
            if term in value:
                fail(f"provenance.{field} uses forbidden source `{term}`")
    if re.search(r"\bcrop\b", str(provenance.get("sourceType", "")), flags=re.IGNORECASE):
        fail("provenance.sourceType must not identify a crop")


def validate_candidate_reference(source_sha: str) -> dict[str, Any]:
    if not SKIA.exists():
        return validate_no_reference()
    if not SKIA.is_file():
        fail(f"{rel(SKIA)} exists but is not a file")
    if not PROVENANCE.is_file():
        fail(f"{rel(SKIA)} exists without skia-reference-provenance.json")

    size = image_size(SKIA)
    image_sha = sha256(SKIA)
    if size != EXPECTED_DIMENSIONS:
        fail(f"{rel(SKIA)} dimensions are {size}, expected {EXPECTED_DIMENSIONS}")
    if FULL_GM_REFERENCE.is_file() and image_sha == sha256(FULL_GM_REFERENCE):
        fail(f"{rel(SKIA)} is byte-identical to the full-GM PNG")
    full_gm_crop = full_gm_crop_rgba_bytes()
    if full_gm_crop is not None and rgba_bytes(SKIA) == full_gm_crop:
        fail(f"{rel(SKIA)} is pixel-identical to the contaminated full-GM crop {FULL_GM_CROP_BOX}")
    if FOR322_CPU.is_file() and image_sha == sha256(FOR322_CPU):
        fail(f"{rel(SKIA)} is byte-identical to FOR-322 cpu.png")

    provenance = load_json(PROVENANCE)
    validate_provenance(provenance, image_sha, source_sha)
    return {
        "path": rel(SKIA),
        "provenancePath": rel(PROVENANCE),
        "present": True,
        "accepted": True,
        "status": "available-isolated-skia-selected-cell-render",
        "dimensions": size,
        "sha256": image_sha,
        "provenance": provenance,
        "blockedReasons": [],
    }


def build_artifact() -> dict[str, Any]:
    follows = validate_previous_contracts()
    source = validate_source()
    runner = validate_runner()
    candidate = validate_candidate_reference(source["sha256"])
    ready = candidate["accepted"] is True
    decision = DECISION_READY if ready else DECISION_MISSING
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "allowedDecisions": [DECISION_READY, DECISION_MISSING, DECISION_PROVENANCE_INVALID],
        "sceneId": SCENE_ID,
        "skiaPngReady": ready,
        "acceptedSkiaPng": candidate["path"] if ready else None,
        "skiaPngPresent": candidate["present"],
        "selectedCell": SELECTED_CELL_EXPECTED,
        "follows": follows,
        "source": source,
        "runner": runner,
        "candidateSkiaReference": candidate,
        "rejectedSources": {
            "fullGmPng": {
                "path": rel(FULL_GM_REFERENCE),
                "accepted": False,
                "reason": "full-GM PNG is not an isolated selected-cell render",
            },
            "fullGmCrop": {
                "accepted": False,
                "reason": "FOR-327 requires executing the FOR-326 source, not slicing a GM image",
            },
            "for322CpuPng": {
                "path": rel(FOR322_CPU),
                "accepted": False,
                "reason": "FOR-322 cpu.png is Kanvas CPU output, not upstream Skia",
            },
            "fullGmScores": {
                "accepted": False,
                "reason": "similarity scores are not selected-cell reference pixels",
            },
        },
        "preservedContracts": {
            "supportStatus": "not-supported",
            "fullGmSubstitutionAccepted": False,
            "fullGmScoreEvidenceAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
            "gpuRouteStatus": "expected-unsupported",
            "gpuRefusalReason": GPU_REFUSAL,
            "readinessMovement": False,
            "releaseGateChanged": False,
            "productionRendererChanged": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
            "scenePromotionChanged": False,
        },
        "validation": [
            "rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py",
            "rtk python3 tools/skia-reference/build_for327_circular_arcs_stroke_butt_selected_cell.py",
            "rtk python3 scripts/validate_for326_circular_arcs_stroke_butt_skia_selected_cell_source.py",
            "rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py",
            f"rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null",
            "rtk ./gradlew pipelineSceneDashboardGate",
            "rtk git diff --check origin/master...HEAD",
        ],
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    cell = data["selectedCell"]
    candidate = data["candidateSkiaReference"]
    validations = "\n".join(f"- `{command}`" for command in data["validation"])
    blocked = "\n".join(f"- {reason}" for reason in candidate["blockedReasons"]) or "- none"
    provenance = candidate.get("provenance", {}) if isinstance(candidate.get("provenance"), dict) else {}
    report = f"""# FOR-327 CircularArcsStrokeButt Selected-Cell Skia Reference

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-327 builds and runs the repo-owned FOR-326 C++ source against an upstream
Skia checkout when available. `skia.png` ready: `{data["skiaPngReady"]}`.

Blocked reasons:

{blocked}

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
| alpha | `{cell["paintAlpha"]}` |
| full-GM rect | `{md_value(cell["fullGmCanvasArcRectLTRB"])}` |
| bounded rect | `{md_value(cell["boundedCanvasArcRectLTRB"])}` |

## Accepted `skia.png`

| Field | Value |
|---|---|
| path | `{candidate["path"]}` |
| provenance path | `{candidate["provenancePath"]}` |
| present | `{candidate["present"]}` |
| accepted | `{candidate["accepted"]}` |
| status | `{candidate["status"]}` |
| dimensions | `{md_value(candidate.get("dimensions"))}` |
| sha256 | `{candidate.get("sha256")}` |

## Provenance

| Field | Value |
|---|---|
| sourceType | `{provenance.get("sourceType")}` |
| command | `{provenance.get("command")}` |
| sourceImplementation | `{provenance.get("sourceImplementation")}` |
| upstream Skia root | `{provenance.get("upstreamSkiaRoot")}` |
| upstream Skia revision | `{provenance.get("upstreamSkiaGitRevision")}` |
| fullGmCrop | `{provenance.get("fullGmCrop")}` |
        | fullGmSubstitutionAccepted | `{provenance.get("fullGmSubstitutionAccepted")}` |
        | cpuKanvasOutputAcceptedAsSkia | `{provenance.get("cpuKanvasOutputAcceptedAsSkia")}` |

## Residual Risk

The reference is generated from the repo-owned FOR-326 source, but reproduction
depends on the local upstream Skia checkout `{provenance.get("upstreamSkiaRoot")}`
and its `{provenance.get("upstreamSkiaGitRevision")}` revision plus `out/Release`
libraries. This is reference evidence for this selected cell only; it does not
promote `CircularArcsStrokeButtGM`, does not change Kanvas scene support, and
does not claim broad Skia parity.

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG `{data["rejectedSources"]["fullGmPng"]["path"]}` | `False` | {data["rejectedSources"]["fullGmPng"]["reason"]} |
| full-GM crop | `False` | {data["rejectedSources"]["fullGmCrop"]["reason"]} |
| FOR-322 `cpu.png` `{data["rejectedSources"]["for322CpuPng"]["path"]}` | `False` | {data["rejectedSources"]["for322CpuPng"]["reason"]} |
| full-GM scores | `False` | {data["rejectedSources"]["fullGmScores"]["reason"]} |

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `{data["preservedContracts"]["supportStatus"]}` |
| GPU route status | `{data["preservedContracts"]["gpuRouteStatus"]}` |
| GPU refusal reason | `{data["preservedContracts"]["gpuRefusalReason"]}` |
| production renderer changed | `{data["preservedContracts"]["productionRendererChanged"]}` |
| WGSL changed | `{data["preservedContracts"]["wgslChanged"]}` |
| threshold changed | `{data["preservedContracts"]["thresholdChanged"]}` |
| fallback policy changed | `{data["preservedContracts"]["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{data["preservedContracts"]["kadreNativeDependencyAdded"]}` |
| scene promotion changed | `{data["preservedContracts"]["scenePromotionChanged"]}` |

## Validation

{validations}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("decision") not in data.get("allowedDecisions", []):
        fail("artifact decision is not allowed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    selected = data.get("selectedCell")
    if not isinstance(selected, dict):
        fail("artifact missing selectedCell")
    validate_cell(selected, "artifact selectedCell")
    candidate = data.get("candidateSkiaReference")
    if not isinstance(candidate, dict):
        fail("artifact missing candidateSkiaReference")
    if data.get("decision") == DECISION_READY:
        if data.get("skiaPngReady") is not True or data.get("acceptedSkiaPng") != rel(SKIA):
            fail("ready decision must expose accepted skia.png")
        if candidate.get("accepted") is not True or candidate.get("present") is not True:
            fail("ready decision requires an accepted present candidate")
    else:
        if data.get("skiaPngReady") is not False or data.get("acceptedSkiaPng") is not None:
            fail("missing decision must not expose accepted skia.png")
        if SKIA.exists():
            fail("missing decision cannot coexist with skia.png")
    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    for key in [
        "fullGmSubstitutionAccepted",
        "fullGmScoreEvidenceAccepted",
        "cpuKanvasOutputAcceptedAsSkia",
        "readinessMovement",
        "releaseGateChanged",
        "productionRendererChanged",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
        "scenePromotionChanged",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        data["decision"],
        "sourceType",
        "isolated-skia-selected-cell-render",
        "Residual Risk",
        "local upstream Skia checkout",
        "does not claim broad Skia parity",
        "fullGmCrop",
        "cpuKanvasOutputAcceptedAsSkia",
        "not-supported",
        GPU_REFUSAL,
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    for pattern in [
        r"\bfull-GM crop\s+\|\s+`?True",
        r"\bFOR-322 `cpu\.png`.*\|\s+`?True",
        r"\bproduction renderer changed\s+\|\s+`?True",
        r"\bWGSL changed\s+\|\s+`?True",
        r"\bscene promotion changed\s+\|\s+`?True",
    ]:
        if re.search(pattern, report, flags=re.IGNORECASE):
            fail(f"report contains unsafe support language matching {pattern}")


def main() -> None:
    data = build_artifact()
    validate_artifact_shape(data)
    write_artifact(data)
    validate_artifact_shape(load_json(ARTIFACT))
    write_report(data)
    validate_report(data)
    print(data["decision"])


if __name__ == "__main__":
    main()
