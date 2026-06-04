#!/usr/bin/env python3
"""Validate the FOR-324 isolated Skia-reference decision."""

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
LINEAR_ID = "FOR-324"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-isolated-selected-cell-skia-reference-harness-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-324-circular-arcs-stroke-butt-isolated-skia-reference.md"
)
FOR324_SKIA = ARTIFACT_DIR / "skia.png"
FOR324_PROVENANCE = ARTIFACT_DIR / "skia-reference-provenance.json"

FOR323_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for323"
FOR323_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / FOR323_SCENE_ID
    / f"{FOR323_SCENE_ID}.json"
)
FOR323_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-323-circular-arcs-stroke-butt-selected-cell-skia-reference.md"
)
FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
FOR322_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-322-circular-arcs-stroke-butt-selected-cell-harness.md"
)
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
FOR322_CPU = FOR322_DIR / "cpu.png"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"
FOR322_STATS = FOR322_DIR / "stats.json"

DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_READY"
DECISION_MISSING = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_HARNESS_MISSING"
DECISION_PROVENANCE_INVALID = (
    "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_PROVENANCE_INVALID"
)
DECISION_AMBIGUOUS = "CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_AMBIGUOUS"

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
MISSING_API_OR_COMMAND = (
    "missing repo-owned headless upstream Skia selected-cell render API/command: "
    "no Gradle task, CLI flag, Kotlin helper, or checked-in executable renders only "
    "the FOR-319 CircularArcsStrokeButt selected cell through upstream Skia and "
    "writes an 80x80 skia.png plus skia-reference-provenance.json; existing "
    "TestUtils.runGmTest, RasterSinkF16, DmMain/DmCli, and the FOR-322 capture "
    "test are Kanvas CPU/DM/test paths, not upstream Skia reference generation"
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


def fail(message: str) -> None:
    raise SystemExit(f"FOR-324 validation failed: {message}")


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
    for322_report = read_text(FOR322_REPORT)
    for323_report = read_text(FOR323_REPORT)
    route_gpu = load_json(FOR322_ROUTE_GPU)
    stats = load_json(FOR322_STATS)

    if for319.get("linear") != "FOR-319" or for319.get("decision") != FOR319_DECISION_APPLIED:
        fail("FOR-319 preflight contract changed")
    if for320.get("linear") != "FOR-320" or for320.get("decision") != FOR320_DECISION_BLOCKED:
        fail("FOR-320 blocked contract changed")
    if for321.get("linear") != "FOR-321" or for321.get("decision") != FOR321_DECISION_BLOCKED:
        fail("FOR-321 blocked contract changed")
    if for322.get("linear") != "FOR-322" or for322.get("decision") != FOR322_DECISION_READY:
        fail("FOR-322 harness-ready contract changed")
    if for323.get("linear") != "FOR-323" or for323.get("decision") != FOR323_DECISION_CONTAMINATED:
        fail("FOR-323 full-GM crop contamination decision changed")

    selected = for322.get("selectedCell")
    if not isinstance(selected, dict):
        fail("FOR-322 selectedCell missing")
    validate_cell(selected, "FOR-322 selectedCell")
    for323_selected = for323.get("selectedCell")
    if not isinstance(for323_selected, dict):
        fail("FOR-323 selectedCell missing")
    validate_cell(for323_selected, "FOR-323 selectedCell")

    coverage = for322.get("artifactCoverage")
    if not isinstance(coverage, dict):
        fail("FOR-322 artifactCoverage missing")
    if "skia.png" not in coverage.get("blockedArtifactIds", []):
        fail("FOR-322 must keep skia.png blocked")
    if coverage.get("fullGmSubstitutionRejected") is not True:
        fail("FOR-322 must keep full-GM substitution rejected")
    if stats.get("artifacts", {}).get("skia.png") != "blocked-no-selected-cell-upstream-skia-reference":
        fail("FOR-322 stats no longer blocks selected-cell skia.png")
    if stats.get("fullGmReferenceAccepted") is not False:
        fail("FOR-322 stats accepts the full-GM reference")
    if route_gpu.get("status") != "expected-unsupported":
        fail("FOR-322 WebGPU route must remain expected-unsupported")
    if route_gpu.get("refusalReason") != GPU_REFUSAL:
        fail("FOR-322 WebGPU refusal reason changed")
    if route_gpu.get("supportStatus") != "not-supported":
        fail("FOR-322 WebGPU route support status changed")

    for needle in [
        "Full-GM substitution rejected",
        "not-supported",
        GPU_REFUSAL,
        "no CircularArcsStrokeButtGM support promotion",
    ]:
        if needle not in for322_report:
            fail(f"FOR-322 report missing `{needle}`")
    for needle in [
        FOR323_DECISION_CONTAMINATED,
        "full-GM crop pixels are non-white",
        "CPU Kanvas output accepted as Skia",
    ]:
        if needle not in for323_report:
            fail(f"FOR-323 report missing `{needle}`")

    return {
        "for319Artifact": rel(FOR319_ARTIFACT),
        "for320Artifact": rel(FOR320_ARTIFACT),
        "for321Artifact": rel(FOR321_ARTIFACT),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for323Artifact": rel(FOR323_ARTIFACT),
        "for322Cpu": rel(FOR322_CPU),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
        "for322Stats": rel(FOR322_STATS),
        "for323Report": rel(FOR323_REPORT),
    }


def validate_source_code_and_missing_path() -> dict[str, Any]:
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
    if "SkPngEncoder.Encode(bitmap)" not in test_utils:
        fail("TestUtils writePng helper changed unexpectedly")

    if "Out of scope for D4.4" not in dm_cli or "--writePath" not in dm_cli:
        fail("DmCli no longer documents that --writePath is out of scope")
    for forbidden in [
        r'"writePath"\s*->',
        r'"write-path"\s*->',
        r'"write_path"\s*->',
        r"writePath\s*:",
    ]:
        if re.search(forbidden, dm_cli):
            fail("DmCli appears to expose a writePath flag; update the FOR-324 decision")
    if "stops short of providing a `main`" not in dm_main:
        fail("DmMain no longer documents the absence of a standalone main")
    if re.search(r"^\s*(?:public\s+)?fun\s+main\s*\(", dm_main, flags=re.MULTILINE):
        fail("DmMain now exposes a main entrypoint; update the FOR-324 decision")
    if "System.out.write(report.toJson().toByteArray())" not in dm_main:
        fail("DmMain example no longer shows report-only output")
    if "SkPngEncoder.Encode(bitmap)" not in runner or "md5Hex(pngBytes)" not in runner:
        fail("Runner PNG encoding/hash path changed")
    if re.search(r"File\s*\([^)]*write", runner):
        fail("Runner appears to write PNG files; update the FOR-324 decision")

    for needle in [
        "class CircularArcsStrokeButtSelectedCellCaptureTest",
        "class SelectedCellGM",
        "TestUtils.runGmTest(gm)",
        "File(dir, \"skia.png\").delete()",
        "c.drawArc(ARC_RECT, 0f, 90f, useCenter = false, paint = red)",
        "c.drawArc(ARC_RECT, 0f, -270f, useCenter = false, paint = blue)",
        "strokeWidth = 15f",
        "strokeCap = SkPaint.Cap.kButt_Cap",
    ]:
        if needle not in for322_test:
            fail(f"FOR-322 capture test source missing `{needle}`")

    return {
        "repoHeadlessIsolatedSkiaPathExists": False,
        "missingApiOrCommand": MISSING_API_OR_COMMAND,
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
                "reason": "parses Kanvas DM config/match/skip only; --writePath is documented out of scope",
            },
            {
                "path": rel(DM_MAIN),
                "symbol": "DmMain.runFromArgs",
                "acceptedAsSkiaReference": False,
                "reason": "returns a Kanvas DM JSON report for an explicit Kotlin GM list and has no standalone image-generation command",
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


def validate_candidate_skia() -> dict[str, Any]:
    if not FOR324_SKIA.exists():
        if FOR324_PROVENANCE.exists():
            fail(f"{rel(FOR324_PROVENANCE)} exists without skia.png")
        return {
            "path": rel(FOR324_SKIA),
            "provenancePath": rel(FOR324_PROVENANCE),
            "present": False,
            "accepted": False,
            "status": "blocked-missing",
            "blockedReasons": [
                "no skia.png is present under the FOR-324 artifact directory",
                MISSING_API_OR_COMMAND,
            ],
        }
    if not FOR324_SKIA.is_file():
        fail(f"{rel(FOR324_SKIA)} exists but is not a file")

    size = image_size(FOR324_SKIA)
    candidate_sha = sha256(FOR324_SKIA)
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
    if not FOR324_PROVENANCE.is_file():
        reasons.append("candidate lacks skia-reference-provenance.json")

    provenance: dict[str, Any] | None = None
    if FOR324_PROVENANCE.is_file():
        provenance = load_json(FOR324_PROVENANCE)
        expected = {
            "sourceType": "isolated-skia-selected-cell-render",
            "fixtureId": FOR319_FIXTURE_ID,
            "sourceGm": SOURCE_GM,
            "dimensions": EXPECTED_DIMENSIONS,
            "fullGmCrop": False,
            "fullGmSubstitutionAccepted": False,
            "cpuKanvasOutputAcceptedAsSkia": False,
        }
        for key, expected_value in expected.items():
            if provenance.get(key) != expected_value:
                reasons.append(
                    f"provenance.{key} expected {expected_value!r}, got {provenance.get(key)!r}"
                )
        if not provenance.get("command"):
            reasons.append("provenance.command must document the isolated Skia render command")
        if provenance.get("outputSha256") not in {None, candidate_sha}:
            reasons.append("provenance.outputSha256 does not match skia.png")
        cell = provenance.get("selectedCell")
        if not isinstance(cell, dict):
            reasons.append("provenance.selectedCell is missing")
        else:
            try:
                validate_cell(cell, "provenance selectedCell")
            except SystemExit as exc:
                reasons.append(str(exc))

        command = str(provenance.get("command", ""))
        forbidden_command_terms = [
            "TestUtils.runGmTest",
            "CircularArcsStrokeButtSelectedCellCaptureTest",
            "original-888/circular_arcs_stroke_butt.png",
            "cpu.png",
            "crop",
            "test-similarity-scores",
        ]
        for term in forbidden_command_terms:
            if term in command:
                reasons.append(f"provenance.command uses forbidden source `{term}`")

    if reasons:
        fail(
            f"{rel(FOR324_SKIA)} is not an acceptable isolated Skia reference: "
            + "; ".join(reasons)
        )

    return {
        "path": rel(FOR324_SKIA),
        "provenancePath": rel(FOR324_PROVENANCE),
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
    missing_path = validate_source_code_and_missing_path()
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
        "missingApiOrCommand": None if skia_ready else MISSING_API_OR_COMMAND,
        "selectedCell": for322["selectedCell"],
        "follows": follows,
        "candidateSkiaReference": candidate,
        "missingPathAudit": missing_path,
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
        },
        "strictProvenanceRequirements": {
            "sourceType": "isolated-skia-selected-cell-render",
            "fixtureId": FOR319_FIXTURE_ID,
            "commandRequired": True,
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
            "rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py",
            "no true headless isolated Skia generation command exists in the repo for this cell",
            "rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py",
            "rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py",
            "rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py",
            "rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py",
            "rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py",
            "rtk ./gradlew pipelineSceneDashboardGate",
            f"rtk python3 -m json.tool {rel(ARTIFACT)} >/dev/null",
            "rtk git diff --check origin/master...HEAD",
        ],
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    cell = data["selectedCell"]
    candidate = data["candidateSkiaReference"]
    missing_path = data["missingPathAudit"]
    rejected = data["rejectedSources"]
    blocked = "\n".join(f"- {reason}" for reason in data["referenceDecision"]["blockedReasons"])
    audited = "\n".join(
        "| `{path}` | `{symbol}` | `{acceptedAsSkiaReference}` | {reason} |".format(**item)
        for item in missing_path["auditedEntryPoints"]
    )
    validations = "\n".join(f"- `{command}`" for command in data["validation"])

    report = f"""# FOR-324 CircularArcsStrokeButt Isolated Skia Reference

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-324 checked whether this repository currently has a true headless,
isolated upstream Skia reference-generation path for the FOR-319
`CircularArcsStrokeButtGM` selected cell. The decision is
`{data["decision"]}`. `skia.png` ready: `{data["skiaPngReady"]}`.

No `skia.png` is produced by this ticket. The repository has a bounded
selected-cell Kanvas harness from FOR-322 and a full-GM Skia PNG, but no
repo-owned command/API that renders only this 80x80 selected cell through
upstream Skia and writes strict provenance.

Missing API/command:

`{data["missingApiOrCommand"]}`

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

## Audited Entry Points

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

## Strict Provenance Required For A Future Ready Decision

If a future ticket creates `skia.png`, it must be 80x80 and accompanied by
`skia-reference-provenance.json` with:

- `sourceType=isolated-skia-selected-cell-render`
- `fixtureId={FOR319_FIXTURE_ID}`
- source GM `{SOURCE_GM}`
- a concrete headless upstream Skia render command
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
        fail("artifact decision is not an allowed FOR-324 decision")
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
        if data.get("decision") == DECISION_MISSING and FOR324_SKIA.exists():
            fail("missing decision cannot coexist with skia.png")
        if not data.get("missingApiOrCommand"):
            fail("blocked decision must name the missing API/command")

    missing_path = data.get("missingPathAudit")
    if not isinstance(missing_path, dict):
        fail("artifact missing missingPathAudit")
    if data.get("decision") == DECISION_MISSING:
        if missing_path.get("repoHeadlessIsolatedSkiaPathExists") is not False:
            fail("missing decision must record no repo headless isolated Skia path")
        if "upstream Skia" not in missing_path.get("missingApiOrCommand", ""):
            fail("missingApiOrCommand must name upstream Skia")
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
        MISSING_API_OR_COMMAND,
        FOR319_FIXTURE_ID,
        "start=0",
        "sweep=90",
        "complement=-270",
        "useCenter=false",
        "aa=true",
        "strokeWidth=15",
        "strokeCap=kButt_Cap",
        "sourceType=isolated-skia-selected-cell-render",
        "cpuKanvasOutputAcceptedAsSkia=false",
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
