#!/usr/bin/env python3
"""Validate the FOR-325 isolated upstream-Skia selected-cell command decision."""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any

from PIL import Image

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-325"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-provision-repo-owned-isolated-skia-selected-cell-reference-command-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-325-circular-arcs-stroke-butt-isolated-skia-reference-command.md"
)
FOR325_SKIA = ARTIFACT_DIR / "skia.png"
FOR325_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"

FOR324_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324"
FOR324_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR324_SCENE_ID
    / f"{FOR324_SCENE_ID}.json"
)
FOR324_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-324-circular-arcs-stroke-butt-isolated-skia-reference.md"
)
FOR323_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for323"
FOR323_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR323_SCENE_ID
    / f"{FOR323_SCENE_ID}.json"
)
FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
FOR322_CPU = FOR322_DIR / "cpu.png"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR321_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321/"
    "circular-arcs-stroke-butt-selected-cell-artifacts-for321.json"
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

CIRCULAR_ARCS_GM = (
    PROJECT_ROOT / "skia-integration-tests/src/main/kotlin/org/skia/tests/CircularArcsGM.kt"
)
TEST_UTILS = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt"
DM_CLI = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/dm/DmCli.kt"
DM_MAIN = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/dm/DmMain.kt"
DM_RUNNER = PROJECT_ROOT / "cpu-raster/src/main/kotlin/org/skia/dm/Runner.kt"
FOR322_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/"
    "CircularArcsStrokeButtSelectedCellCaptureTest.kt"
)
FULL_GM_REFERENCE = (
    PROJECT_ROOT
    / "skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png"
)

DEFAULT_UPSTREAM_SKIA_ROOT = Path("/Users/chaos/workspace/kanvas-forge/skia-main")
UPSTREAM_SKIA_ROOT = Path(os.environ.get("KANVAS_FOR325_UPSTREAM_SKIA_ROOT", DEFAULT_UPSTREAM_SKIA_ROOT))
UPSTREAM_CIRCULAR_ARCS_CPP = UPSTREAM_SKIA_ROOT / "gm/circulararcs.cpp"
UPSTREAM_DM = UPSTREAM_SKIA_ROOT / "out/Release/dm"

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_READY"
DECISION_MISSING = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVISIONING_MISSING"
)
DECISION_PROVENANCE_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVENANCE_INVALID"
)
DECISION_AMBIGUOUS = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_AMBIGUOUS"
)

FOR324_DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_HARNESS_MISSING"
FOR323_DECISION_CONTAMINATED = "CIRCULAR_ARCS_STROKE_BUTT_FULL_GM_CROP_CONTAMINATED"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
FOR321_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_BLOCKED"
FOR320_DECISION_BLOCKED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED"
FOR319_DECISION_APPLIED = "CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED"
FOR319_FIXTURE_ID = "circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true"
SOURCE_GM = "CircularArcsStrokeButtGM"
SOURCE_ROW_ID = "circular-arcs-stroke-butt-webgpu"
FOR318_TARGET_ID = "future-circular-arcs-stroke-butt-nonhairline-subdivision-probe"
GPU_REFUSAL = "coverage.stroke-cap-join-visual-parity-below-threshold"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
FULL_GM_DIMENSIONS = {"width": 1000, "height": 1000}
FULL_GM_CROP_BOX = [120, 500, 200, 580]
UPSTREAM_FULL_GM_COMMAND = (
    "rtk /Users/chaos/workspace/kanvas-forge/skia-main/out/Release/dm "
    "--src gm --config 8888 --match ^circular_arcs_stroke_butt$ --writePath <dir>"
)
MISSING_DEPENDENCY = (
    "missing repo-owned headless upstream Skia selected-cell render command or "
    "checked-in upstream Skia tool source that draws only the FOR-319 "
    "CircularArcsStrokeButt selected cell and writes an 80x80 skia.png plus "
    "skia-reference-provenance.json; the available upstream Skia dm binary and "
    "gm/circulararcs.cpp render the full 1000x1000 GM, and full-GM output or "
    "crops are forbidden"
)

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
    "paintAlpha": 100,
    "drawArcCalls": [
        {"paintColor": "red", "startDegrees": 0, "sweepDegrees": 90},
        {"paintColor": "blue", "startDegrees": 0, "sweepDegrees": -270},
    ],
}

FORBIDDEN_COMMAND_TERMS = [
    "TestUtils.runGmTest",
    "CircularArcsStrokeButtSelectedCellCaptureTest",
    "original-888/circular_arcs_stroke_butt.png",
    "cpu.png",
    "crop",
    "test-similarity-scores",
    "test-similarity-report",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-325 validation failed: {message}")


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


def image_size(path: Path) -> dict[str, int]:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        width, height = image.size
    return {"width": width, "height": height}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


def validate_cell(cell: dict[str, Any], label: str) -> None:
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            fail(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")
    if cell.get("fullGmCanvasArcRectLTRB") != [140, 520, 180, 560]:
        fail(f"{label} must document the full-GM selected-cell rect")
    if cell.get("boundedCanvasArcRectLTRB") != [20, 20, 60, 60]:
        fail(f"{label} must document the bounded selected-cell rect")


def validate_previous_contracts() -> dict[str, Any]:
    for319 = load_json(FOR319_ARTIFACT)
    for320 = load_json(FOR320_ARTIFACT)
    for321 = load_json(FOR321_ARTIFACT)
    for322 = load_json(FOR322_ARTIFACT)
    for323 = load_json(FOR323_ARTIFACT)
    for324 = load_json(FOR324_ARTIFACT)
    for324_report = read_text(FOR324_REPORT)
    route_gpu = load_json(FOR322_ROUTE_GPU)
    stats = load_json(FOR322_STATS)

    expected_decisions = [
        (for319, "FOR-319", FOR319_DECISION_APPLIED),
        (for320, "FOR-320", FOR320_DECISION_BLOCKED),
        (for321, "FOR-321", FOR321_DECISION_BLOCKED),
        (for322, "FOR-322", FOR322_DECISION_READY),
        (for323, "FOR-323", FOR323_DECISION_CONTAMINATED),
        (for324, "FOR-324", FOR324_DECISION_MISSING),
    ]
    for data, linear, decision in expected_decisions:
        if data.get("linear") != linear or data.get("decision") != decision:
            fail(f"{linear} prerequisite decision changed")

    selected = for322.get("selectedCell")
    if not isinstance(selected, dict):
        fail("FOR-322 selectedCell missing")
    validate_cell(selected, "FOR-322 selectedCell")
    for324_selected = for324.get("selectedCell")
    if not isinstance(for324_selected, dict):
        fail("FOR-324 selectedCell missing")
    validate_cell(for324_selected, "FOR-324 selectedCell")

    if stats.get("artifacts", {}).get("skia.png") != "blocked-no-selected-cell-upstream-skia-reference":
        fail("FOR-322 stats no longer blocks selected-cell skia.png")
    if stats.get("fullGmReferenceAccepted") is not False:
        fail("FOR-322 stats accepts the full-GM reference")
    if route_gpu.get("status") != "expected-unsupported":
        fail("FOR-322 WebGPU route must remain expected-unsupported")
    if route_gpu.get("refusalReason") != GPU_REFUSAL:
        fail("FOR-322 WebGPU refusal reason changed")
    if route_gpu.get("supportStatus") != "not-supported":
        fail("FOR-322 WebGPU support status changed")
    for needle in [
        FOR324_DECISION_MISSING,
        "repo-owned command/API",
        "full-GM Skia PNG",
        "FOR-322 `cpu.png`",
    ]:
        if needle not in for324_report:
            fail(f"FOR-324 report missing `{needle}`")

    return {
        "for319Artifact": rel(FOR319_ARTIFACT),
        "for320Artifact": rel(FOR320_ARTIFACT),
        "for321Artifact": rel(FOR321_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for323Artifact": rel(FOR323_ARTIFACT),
        "for324Artifact": rel(FOR324_ARTIFACT),
        "for324Report": rel(FOR324_REPORT),
        "for322Cpu": rel(FOR322_CPU),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
        "for322Stats": rel(FOR322_STATS),
    }


def validate_repo_entry_points() -> dict[str, Any]:
    gm = read_text(CIRCULAR_ARCS_GM)
    test_utils = read_text(TEST_UTILS)
    dm_cli = read_text(DM_CLI)
    dm_main = read_text(DM_MAIN)
    runner = read_text(DM_RUNNER)
    for322_test = read_text(FOR322_TEST)

    for needle in [
        "public class CircularArcsStrokeButtGM",
        "paint.strokeCap = SkPaint.Cap.kButt_Cap",
        "strokeWidth = 15f",
        "val kStarts: FloatArray = floatArrayOf(0f, 10f, 30f, 45f, 90f, 165f, 180f, 270f)",
        "val kSweeps: FloatArray = floatArrayOf(1f, 45f, 90f, 130f, 180f, 184f, 300f, 355f)",
    ]:
        if needle not in gm:
            fail(f"CircularArcsGM source missing `{needle}`")
    if "public fun runGmTest(gm: GM): SkBitmap" not in test_utils:
        fail("TestUtils.runGmTest signature changed")
    if "RasterSinkF16(DM_REFERENCE_COLOR_SPACE)" not in test_utils:
        fail("TestUtils.runGmTest no longer documents the Kanvas RasterSinkF16 route")
    if "Out of scope for D4.4" not in dm_cli or "--writePath" not in dm_cli:
        fail("Kanvas DmCli no longer documents that --writePath is out of scope")
    if re.search(r"^\s*(?:public\s+)?fun\s+main\s*\(", dm_main, flags=re.MULTILINE):
        fail("Kanvas DmMain now exposes a standalone main; update FOR-325")
    for needle in [
        "class CircularArcsStrokeButtSelectedCellCaptureTest",
        "class SelectedCellGM",
        "TestUtils.runGmTest(gm)",
        "File(dir, \"skia.png\").delete()",
        "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
        "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
    ]:
        if needle not in for322_test:
            fail(f"FOR-322 capture test source missing `{needle}`")
    if "SkPngEncoder.Encode(bitmap)" not in runner or "md5Hex(pngBytes)" not in runner:
        fail("Kanvas DM Runner PNG/hash path changed")

    return {
        "repoOwnedSelectedCellCommandPresent": False,
        "missingDependency": MISSING_DEPENDENCY,
        "auditedEntryPoints": [
            {
                "path": rel(TEST_UTILS),
                "symbol": "TestUtils.runGmTest",
                "acceptedAsSkiaReference": False,
                "reason": "renders through Kanvas RasterSinkF16, not upstream Skia",
            },
            {
                "path": rel(DM_CLI),
                "symbol": "DmCli",
                "acceptedAsSkiaReference": False,
                "reason": "Kanvas DM config parser; --writePath is explicitly out of scope here",
            },
            {
                "path": rel(DM_MAIN),
                "symbol": "DmMain.runFromArgs",
                "acceptedAsSkiaReference": False,
                "reason": "returns a Kanvas DM JSON report and has no standalone image-generation command",
            },
            {
                "path": rel(DM_RUNNER),
                "symbol": "Runner",
                "acceptedAsSkiaReference": False,
                "reason": "hashes encoded Kanvas sink output for reports; it does not write selected-cell reference PNGs",
            },
            {
                "path": rel(FOR322_TEST),
                "symbol": "CircularArcsStrokeButtSelectedCellCaptureTest",
                "acceptedAsSkiaReference": False,
                "reason": "bounded selected-cell harness writes Kanvas CPU output and explicitly deletes skia.png",
            },
        ],
    }


def validate_upstream_dependency() -> dict[str, Any]:
    source_present = UPSTREAM_CIRCULAR_ARCS_CPP.is_file()
    dm_present = UPSTREAM_DM.is_file() and os.access(UPSTREAM_DM, os.X_OK)
    upstream_source_evidence: dict[str, Any] | None = None
    if source_present:
        text = read_text(UPSTREAM_CIRCULAR_ARCS_CPP)
        for needle in [
            "constexpr SkScalar kStarts[] = {0.f, 10.f, 30.f, 45.f, 90.f, 165.f, 180.f, 270.f}",
            "constexpr SkScalar kSweeps[] = {1.f, 45.f, 90.f, 130.f, 180.f, 184.f, 300.f, 355.f}",
            "canvas->drawArc(kRect, start, sweep, useCenter, p0)",
            "canvas->drawArc(kRect, start, -(360.f - sweep), useCenter, p1)",
            "DEF_ARC_GM(stroke_butt)",
            "p->setStrokeCap(SkPaint::kButt_Cap)",
        ]:
            if needle not in text:
                fail(f"upstream circulararcs.cpp missing `{needle}`")
        upstream_source_evidence = {
            "path": rel(UPSTREAM_CIRCULAR_ARCS_CPP),
            "present": True,
            "acceptedAsSelectedCellCommand": False,
            "reason": "source defines the full 1000x1000 GM grid, not a checked-in headless selected-cell renderer",
        }

    return {
        "upstreamSkiaRoot": str(UPSTREAM_SKIA_ROOT),
        "upstreamCircularArcsSource": upstream_source_evidence
        or {
            "path": str(UPSTREAM_CIRCULAR_ARCS_CPP),
            "present": False,
            "acceptedAsSelectedCellCommand": False,
            "reason": "upstream circulararcs.cpp was not found in the expected environment",
        },
        "upstreamDmBinary": {
            "path": str(UPSTREAM_DM),
            "present": dm_present,
            "acceptedAsSelectedCellCommand": False,
            "rejectedCommand": UPSTREAM_FULL_GM_COMMAND,
            "reason": "dm --writePath can emit the full 1000x1000 GM circular_arcs_stroke_butt; FOR-325 requires only the 80x80 FOR-319 cell and forbids crop/full-GM substitution",
        },
        "provisionableSelectedCellCommand": False,
        "missingDependency": MISSING_DEPENDENCY,
    }


def validate_candidate_skia() -> dict[str, Any]:
    if not FOR325_SKIA.exists():
        if FOR325_PROVENANCE.exists():
            fail(f"{rel(FOR325_PROVENANCE)} exists without skia.png")
        return {
            "path": rel(FOR325_SKIA),
            "provenancePath": rel(FOR325_PROVENANCE),
            "present": False,
            "accepted": False,
            "status": "blocked-provisioning-missing",
            "blockedReasons": [
                "no skia.png is present under the FOR-325 artifact directory",
                MISSING_DEPENDENCY,
            ],
        }
    if not FOR325_SKIA.is_file():
        fail(f"{rel(FOR325_SKIA)} exists but is not a file")
    if not FOR325_PROVENANCE.is_file():
        fail(f"{rel(FOR325_SKIA)} exists without skia-reference-provenance.json")

    size = image_size(FOR325_SKIA)
    candidate_sha = sha256(FOR325_SKIA)
    full_sha = sha256(FULL_GM_REFERENCE) if FULL_GM_REFERENCE.is_file() else None
    cpu_sha = sha256(FOR322_CPU) if FOR322_CPU.is_file() else None
    reasons = []
    if size == FULL_GM_DIMENSIONS:
        reasons.append("candidate skia.png has full-GM dimensions")
    if size != EXPECTED_DIMENSIONS:
        reasons.append(f"candidate skia.png dimensions are {size}, expected {EXPECTED_DIMENSIONS}")
    if full_sha is not None and candidate_sha == full_sha:
        reasons.append("candidate skia.png is byte-identical to the full-GM PNG")
    if cpu_sha is not None and candidate_sha == cpu_sha:
        reasons.append("candidate skia.png is byte-identical to FOR-322 Kanvas CPU output")

    provenance = load_json(FOR325_PROVENANCE)
    expected = {
        "sourceType": "isolated-skia-selected-cell-render",
        "fixtureId": FOR319_FIXTURE_ID,
        "sourceGm": SOURCE_GM,
        "sourceRowId": SOURCE_ROW_ID,
        "dimensions": EXPECTED_DIMENSIONS,
        "fullGmCrop": False,
        "fullGmSubstitutionAccepted": False,
        "cpuKanvasOutputAcceptedAsSkia": False,
    }
    for key, expected_value in expected.items():
        if provenance.get(key) != expected_value:
            reasons.append(f"provenance.{key} expected {expected_value!r}, got {provenance.get(key)!r}")
    if provenance.get("outputSha256") != candidate_sha:
        reasons.append("provenance.outputSha256 must match skia.png")
    if not provenance.get("command"):
        reasons.append("provenance.command must document the isolated Skia render command")
    if not provenance.get("sourceImplementation"):
        reasons.append("provenance.sourceImplementation must name the upstream Skia implementation")
    if not (provenance.get("upstreamSkiaGitRevision") or provenance.get("upstreamSkiaSourceVersion")):
        reasons.append("provenance must include upstream Skia source/version evidence")
    cell = provenance.get("selectedCell")
    if not isinstance(cell, dict):
        reasons.append("provenance.selectedCell is missing")
    else:
        try:
            validate_cell(cell, "provenance selectedCell")
        except SystemExit as exc:
            reasons.append(str(exc))
    command = str(provenance.get("command", ""))
    for term in FORBIDDEN_COMMAND_TERMS:
        if term in command:
            reasons.append(f"provenance.command uses forbidden source `{term}`")
    source_impl = str(provenance.get("sourceImplementation", ""))
    for term in FORBIDDEN_COMMAND_TERMS:
        if term in source_impl:
            reasons.append(f"provenance.sourceImplementation uses forbidden source `{term}`")

    if reasons:
        fail(
            f"{rel(FOR325_SKIA)} is not an acceptable isolated Skia reference: "
            + "; ".join(reasons)
        )

    return {
        "path": rel(FOR325_SKIA),
        "provenancePath": rel(FOR325_PROVENANCE),
        "present": True,
        "accepted": True,
        "status": "available-isolated-skia-selected-cell-render",
        "dimensions": size,
        "sha256": candidate_sha,
        "provenance": provenance,
        "blockedReasons": [],
    }


def build_artifact() -> dict[str, Any]:
    follows = validate_previous_contracts()
    repo_audit = validate_repo_entry_points()
    upstream_audit = validate_upstream_dependency()
    candidate = validate_candidate_skia()
    for322 = load_json(FOR322_ARTIFACT)
    for323 = load_json(FOR323_ARTIFACT)

    decision = DECISION_READY if candidate["accepted"] else DECISION_MISSING
    skia_ready = decision == DECISION_READY
    blocked_reasons = [] if skia_ready else candidate["blockedReasons"]

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "allowedDecisions": [
            DECISION_READY,
            DECISION_MISSING,
            DECISION_PROVENANCE_INVALID,
            DECISION_AMBIGUOUS,
        ],
        "sceneId": SCENE_ID,
        "skiaPngReady": skia_ready,
        "acceptedSkiaPng": candidate["path"] if skia_ready else None,
        "skiaPngPresent": candidate["present"],
        "missingDependency": None if skia_ready else MISSING_DEPENDENCY,
        "selectedCell": for322["selectedCell"],
        "follows": follows,
        "candidateSkiaReference": candidate,
        "repoEntryPointAudit": repo_audit,
        "upstreamSkiaDependencyAudit": upstream_audit,
        "rejectedSources": {
            "fullGmPng": {
                "path": rel(FULL_GM_REFERENCE),
                "accepted": False,
                "reason": "full-GM PNG is not an isolated selected-cell render",
            },
            "fullGmCrop": {
                "accepted": False,
                "for323Decision": for323["decision"],
                "cropBoxLTRB": FULL_GM_CROP_BOX,
                "reason": "FOR-323 proved the crop is contaminated by neighboring stroke margins",
            },
            "for322CpuPng": {
                "path": rel(FOR322_CPU),
                "present": FOR322_CPU.is_file(),
                "accepted": False,
                "reason": "FOR-322 cpu.png is Kanvas CPU output, not Skia",
            },
            "fullGmScores": {
                "accepted": False,
                "reason": "full-GM similarity scores are not selected-cell reference pixels",
            },
            "upstreamDmFullGmWritePath": {
                "command": UPSTREAM_FULL_GM_COMMAND,
                "accepted": False,
                "reason": "upstream dm writes the whole GM; FOR-325 needs an isolated 80x80 selected-cell renderer",
            },
        },
        "strictProvenanceRequirements": {
            "sourceType": "isolated-skia-selected-cell-render",
            "fixtureId": FOR319_FIXTURE_ID,
            "sourceGm": SOURCE_GM,
            "sourceRowId": SOURCE_ROW_ID,
            "commandRequired": True,
            "sourceImplementationRequired": True,
            "upstreamSkiaVersionRequired": True,
            "dimensions": EXPECTED_DIMENSIONS,
            "fullGmCrop": False,
            "fullGmSubstitutionAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
            "selectedCellRequired": True,
        },
        "referenceDecision": {
            "decision": decision,
            "readyDecision": DECISION_READY,
            "missingDecision": DECISION_MISSING,
            "provenanceInvalidDecision": DECISION_PROVENANCE_INVALID,
            "ambiguousDecision": DECISION_AMBIGUOUS,
            "skiaPngReady": skia_ready,
            "blockedReasons": blocked_reasons,
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
        },
        "validation": [
            "rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py",
            "no true repo-owned headless upstream Skia selected-cell generation command is provisionable for this cell",
            "rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py",
            "rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py",
            "rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py",
            "rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py",
            "rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py",
            "rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py",
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
    repo_audit = data["repoEntryPointAudit"]
    upstream_audit = data["upstreamSkiaDependencyAudit"]
    rejected = data["rejectedSources"]
    blocked = "\n".join(f"- {reason}" for reason in data["referenceDecision"]["blockedReasons"])
    audited = "\n".join(
        "| `{path}` | `{symbol}` | `{acceptedAsSkiaReference}` | {reason} |".format(**item)
        for item in repo_audit["auditedEntryPoints"]
    )
    validations = "\n".join(f"- `{command}`" for command in data["validation"])

    report = f"""# FOR-325 CircularArcsStrokeButt Isolated Skia Reference Command

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-325 attempted to provision a repo-owned, headless upstream Skia command
that renders only the FOR-319 `CircularArcsStrokeButtGM` selected cell. The
decision is `{data["decision"]}`. `skia.png` ready: `{data["skiaPngReady"]}`.

No `skia.png` is produced by this ticket. A Skia checkout and upstream `dm`
binary may be present in the local environment, but the available upstream
entry point writes the full 1000x1000 GM. FOR-325 needs a selected-cell
80x80 upstream Skia render command with strict provenance, and full-GM output,
crops, scores, Kanvas CPU output, and Kanvas test harnesses remain rejected.

Missing dependency:

`{data["missingDependency"]}`

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
| drawArc calls | `{len(cell["drawArcCalls"])}` |

The target cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`,
alpha `100`, with two arcs.

## Candidate `skia.png`

| Field | Value |
|---|---|
| path | `{candidate["path"]}` |
| provenance path | `{candidate["provenancePath"]}` |
| present | `{candidate["present"]}` |
| accepted | `{candidate["accepted"]}` |
| status | `{candidate["status"]}` |

Blocked reasons:

{blocked}

## Upstream Skia Dependency Audit

| Item | Present | Accepted | Reason |
|---|---:|---:|---|
| source `{upstream_audit["upstreamCircularArcsSource"]["path"]}` | `{upstream_audit["upstreamCircularArcsSource"]["present"]}` | `{upstream_audit["upstreamCircularArcsSource"]["acceptedAsSelectedCellCommand"]}` | {upstream_audit["upstreamCircularArcsSource"]["reason"]} |
| binary `{upstream_audit["upstreamDmBinary"]["path"]}` | `{upstream_audit["upstreamDmBinary"]["present"]}` | `{upstream_audit["upstreamDmBinary"]["acceptedAsSelectedCellCommand"]}` | {upstream_audit["upstreamDmBinary"]["reason"]} |

Rejected upstream full-GM command:

`{upstream_audit["upstreamDmBinary"]["rejectedCommand"]}`

## Repo Entry Point Audit

| Path | Symbol | Accepted as Skia reference | Reason |
|---|---|---:|---|
{audited}

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG `{rejected["fullGmPng"]["path"]}` | `{rejected["fullGmPng"]["accepted"]}` | {rejected["fullGmPng"]["reason"]} |
| full-GM crop `{md_value(rejected["fullGmCrop"]["cropBoxLTRB"])}` | `{rejected["fullGmCrop"]["accepted"]}` | {rejected["fullGmCrop"]["reason"]} |
| FOR-322 `cpu.png` `{rejected["for322CpuPng"]["path"]}` | `{rejected["for322CpuPng"]["accepted"]}` | {rejected["for322CpuPng"]["reason"]} |
| full-GM scores | `{rejected["fullGmScores"]["accepted"]}` | {rejected["fullGmScores"]["reason"]} |
| upstream `dm --writePath` full-GM output | `{rejected["upstreamDmFullGmWritePath"]["accepted"]}` | {rejected["upstreamDmFullGmWritePath"]["reason"]} |

## Strict Provenance Required For A Future Ready Decision

If a future patch creates `skia.png`, it must be 80x80 and accompanied by
`skia-reference-provenance.json` with:

- `sourceType=isolated-skia-selected-cell-render`
- `fixtureId={FOR319_FIXTURE_ID}`
- `sourceGm={SOURCE_GM}`
- `sourceRowId={SOURCE_ROW_ID}`
- a concrete headless upstream Skia selected-cell render command
- `sourceImplementation` naming the upstream Skia implementation
- upstream Skia source/version evidence
- `fullGmCrop=false`
- `fullGmSubstitutionAccepted=false`
- `cpuKanvasOutputAcceptedAsSkia=false`
- the selected-cell geometry above

The validator fails if `skia.png` is the full-GM PNG, a crop, a full-GM
score substitute, FOR-322 `cpu.png`, or lacks strict provenance.

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `{data["preservedContracts"]["supportStatus"]}` |
| full-GM substitution accepted | `{data["preservedContracts"]["fullGmSubstitutionAccepted"]}` |
| full-GM score evidence accepted | `{data["preservedContracts"]["fullGmScoreEvidenceAccepted"]}` |
| CPU Kanvas output accepted as Skia | `{data["preservedContracts"]["cpuKanvasOutputAcceptedAsSkia"]}` |
| GPU route status | `{data["preservedContracts"]["gpuRouteStatus"]}` |
| GPU refusal reason | `{data["preservedContracts"]["gpuRefusalReason"]}` |
| readiness movement | `{data["preservedContracts"]["readinessMovement"]}` |
| release gate changed | `{data["preservedContracts"]["releaseGateChanged"]}` |
| production renderer changed | `{data["preservedContracts"]["productionRendererChanged"]}` |
| WGSL changed | `{data["preservedContracts"]["wgslChanged"]}` |
| threshold changed | `{data["preservedContracts"]["thresholdChanged"]}` |
| fallback policy changed | `{data["preservedContracts"]["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{data["preservedContracts"]["kadreNativeDependencyAdded"]}` |

## Validation

{validations}
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact_shape(data: dict[str, Any]) -> None:
    if data.get("linear") != LINEAR_ID:
        fail("artifact linear id changed")
    if data.get("sourceMemory") != SOURCE_MEMORY:
        fail("artifact source memory changed")
    if data.get("sceneId") != SCENE_ID:
        fail("artifact scene id changed")
    if data.get("decision") not in data.get("allowedDecisions", []):
        fail("artifact decision is not an allowed FOR-325 decision")
    selected = data.get("selectedCell")
    if not isinstance(selected, dict):
        fail("artifact missing selectedCell")
    validate_cell(selected, "artifact selectedCell")
    candidate = data.get("candidateSkiaReference")
    if not isinstance(candidate, dict):
        fail("artifact missing candidateSkiaReference")

    if data.get("decision") == DECISION_READY:
        if data.get("skiaPngReady") is not True or not data.get("acceptedSkiaPng"):
            fail("ready decision must expose acceptedSkiaPng")
        if candidate.get("accepted") is not True or candidate.get("present") is not True:
            fail("ready decision requires accepted present skia.png")
    else:
        if data.get("skiaPngReady") is not False or data.get("acceptedSkiaPng") is not None:
            fail("blocked decision must keep skia.png not ready")
        if data.get("decision") == DECISION_MISSING and FOR325_SKIA.exists():
            fail("missing decision cannot coexist with skia.png")
        if not data.get("missingDependency"):
            fail("blocked decision must name the missing dependency")

    repo_audit = data.get("repoEntryPointAudit")
    if not isinstance(repo_audit, dict):
        fail("artifact missing repoEntryPointAudit")
    if repo_audit.get("repoOwnedSelectedCellCommandPresent") is not False and data.get("decision") == DECISION_MISSING:
        fail("missing decision cannot claim repo-owned selected-cell command exists")
    upstream_audit = data.get("upstreamSkiaDependencyAudit")
    if not isinstance(upstream_audit, dict):
        fail("artifact missing upstreamSkiaDependencyAudit")
    if upstream_audit.get("provisionableSelectedCellCommand") is not False and data.get("decision") == DECISION_MISSING:
        fail("missing decision cannot claim provisionable selected-cell command exists")
    if "full 1000x1000 GM" not in upstream_audit.get("upstreamDmBinary", {}).get("reason", ""):
        fail("upstream dm rejection must name full-GM output")

    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    unsafe_true = [
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
    ]
    for key in unsafe_true:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")
    if preserved.get("supportStatus") != "not-supported":
        fail("supportStatus must remain not-supported")
    if preserved.get("gpuRouteStatus") != "expected-unsupported":
        fail("GPU route must remain expected-unsupported")


def validate_report() -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        DECISION_MISSING,
        MISSING_DEPENDENCY,
        FOR319_FIXTURE_ID,
        "start=0",
        "sweep=90",
        "complement=-270",
        "useCenter=false",
        "aa=true",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        "sourceType=isolated-skia-selected-cell-render",
        "sourceImplementation",
        "cpuKanvasOutputAcceptedAsSkia=false",
        "full 1000x1000 GM",
        "not-supported",
        GPU_REFUSAL,
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    unsafe_patterns = [
        r"\bskia\.png`? ready:\s+`?True",
        r"\bfull-GM substitution accepted\s+\|\s+`?True",
        r"\bCPU Kanvas output accepted as Skia\s+\|\s+`?True",
        r"\breadiness movement\s+\|\s+`?True",
        r"\brelease gate changed\s+\|\s+`?True",
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
    print(data["decision"])


if __name__ == "__main__":
    main()
