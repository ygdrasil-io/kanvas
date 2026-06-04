#!/usr/bin/env python3
"""Compare the FOR-327 Skia cell reference with the FOR-322 Kanvas CPU cell."""

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
LINEAR_ID = "FOR-328"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-compare-circular-arcs-stroke-butt-selected-cell-skia-and-cpu-reference-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-cpu-diff-for328"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-328-circular-arcs-stroke-butt-selected-cell-skia-cpu-diff.md"
)
DIFF = ARTIFACT_DIR / "cpu-vs-skia-diff.png"

FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR327_SCENE_ID
FOR327_ARTIFACT = FOR327_DIR / f"{FOR327_SCENE_ID}.json"
SKIA = FOR327_DIR / "skia.png"

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
CPU = FOR322_DIR / "cpu.png"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR322_ROUTE_GPU = FOR322_DIR / "route-gpu.json"

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
EXPECTED_SIZE = (80, 80)

DECISION_MATCH = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_CPU_MATCH_WITHIN_CELL_THRESHOLD"
DECISION_AUDIT = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_DIFF_REQUIRES_RASTER_AUDIT"
DECISION_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_REFERENCE_INPUT_INVALID"
FOR327_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
GPU_REFUSAL = "coverage.stroke-cap-join-visual-parity-below-threshold"

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


def fail(message: str) -> None:
    raise SystemExit(f"FOR-328 validation failed: {message}")


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def md_value(value: Any) -> str:
    return json.dumps(value, sort_keys=False)


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


def optional_json(path: Path, invalid: list[str]) -> dict[str, Any] | None:
    if not path.is_file():
        invalid.append(f"missing JSON file: {rel(path)}")
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        invalid.append(f"invalid JSON file: {rel(path)}: {error}")
        return None
    if not isinstance(data, dict):
        invalid.append(f"{rel(path)} must contain a JSON object")
        return None
    return data


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_size(path: Path, invalid: list[str], label: str) -> dict[str, int] | None:
    if not path.is_file():
        invalid.append(f"missing PNG file: {rel(path)}")
        return None
    try:
        with Image.open(path) as image:
            width, height = image.size
    except OSError as error:
        invalid.append(f"{label} is not a readable PNG: {error}")
        return None
    size = {"width": width, "height": height}
    if size != EXPECTED_DIMENSIONS:
        invalid.append(f"{label} dimensions are {size}, expected {EXPECTED_DIMENSIONS}")
    return size


def rgba_image(path: Path) -> Image.Image:
    with Image.open(path) as image:
        return image.convert("RGBA")


def validate_cell(cell: dict[str, Any] | None, label: str, invalid: list[str]) -> None:
    if not isinstance(cell, dict):
        invalid.append(f"{label} missing")
        return
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            invalid.append(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def assert_selected_cell(cell: dict[str, Any] | None, label: str) -> None:
    invalid: list[str] = []
    validate_cell(cell, label, invalid)
    if invalid:
        fail("; ".join(invalid))


def validate_inputs() -> tuple[dict[str, Any], list[str]]:
    invalid: list[str] = []
    for327 = optional_json(FOR327_ARTIFACT, invalid)
    for322 = optional_json(FOR322_ARTIFACT, invalid)
    for322_stats = optional_json(FOR322_STATS, invalid)
    route_gpu = optional_json(FOR322_ROUTE_GPU, invalid)

    skia_size = image_size(SKIA, invalid, "FOR-327 skia.png")
    cpu_size = image_size(CPU, invalid, "FOR-322 cpu.png")

    if for327 is not None:
        if for327.get("linear") != "FOR-327":
            invalid.append("FOR-327 artifact linear id changed")
        if for327.get("sceneId") != FOR327_SCENE_ID:
            invalid.append("FOR-327 artifact scene id changed")
        if for327.get("decision") != FOR327_DECISION_READY:
            invalid.append("FOR-327 Skia reference is not ready")
        if for327.get("acceptedSkiaPng") != rel(SKIA):
            invalid.append("FOR-327 acceptedSkiaPng does not point at the expected skia.png")
        candidate = for327.get("candidateSkiaReference")
        if not isinstance(candidate, dict):
            invalid.append("FOR-327 candidateSkiaReference missing")
        else:
            if candidate.get("accepted") is not True:
                invalid.append("FOR-327 candidate Skia reference is not accepted")
            if candidate.get("path") != rel(SKIA):
                invalid.append("FOR-327 candidate path does not point at the expected skia.png")
            if candidate.get("dimensions") != EXPECTED_DIMENSIONS:
                invalid.append("FOR-327 candidate dimensions changed")
            if SKIA.is_file() and candidate.get("sha256") != sha256(SKIA):
                invalid.append("FOR-327 candidate sha256 does not match skia.png")
            provenance = candidate.get("provenance")
            if not isinstance(provenance, dict):
                invalid.append("FOR-327 candidate provenance missing")
            else:
                if provenance.get("sourceType") != "isolated-skia-selected-cell-render":
                    invalid.append("FOR-327 provenance sourceType is not isolated Skia cell render")
                if provenance.get("fullGmCrop") is not False:
                    invalid.append("FOR-327 provenance accepts a full-GM crop")
                if provenance.get("fullGmSubstitutionAccepted") is not False:
                    invalid.append("FOR-327 provenance accepts full-GM substitution")
                if provenance.get("cpuKanvasOutputAcceptedAsSkia") is not False:
                    invalid.append("FOR-327 provenance accepts CPU Kanvas output as Skia")
        validate_cell(for327.get("selectedCell"), "FOR-327 selectedCell", invalid)

    if for322 is not None:
        if for322.get("linear") != "FOR-322":
            invalid.append("FOR-322 artifact linear id changed")
        if for322.get("sceneId") != FOR322_SCENE_ID:
            invalid.append("FOR-322 artifact scene id changed")
        if for322.get("decision") != FOR322_DECISION_READY:
            invalid.append("FOR-322 harness is not ready")
        validate_cell(for322.get("selectedCell"), "FOR-322 selectedCell", invalid)
        coverage = for322.get("artifactCoverage")
        if not isinstance(coverage, dict):
            invalid.append("FOR-322 artifactCoverage missing")
        else:
            items = coverage.get("items")
            if not isinstance(items, list):
                invalid.append("FOR-322 artifactCoverage.items missing")
            else:
                by_id = {item.get("id"): item for item in items if isinstance(item, dict)}
                cpu_item = by_id.get("cpu.png")
                if not isinstance(cpu_item, dict):
                    invalid.append("FOR-322 cpu.png artifact slot missing")
                else:
                    if cpu_item.get("status") != "available":
                        invalid.append("FOR-322 cpu.png artifact slot is not available")
                    if cpu_item.get("selectedCellPath") != rel(CPU):
                        invalid.append("FOR-322 cpu.png slot does not point at the expected CPU PNG")
                    if cpu_item.get("fullGmSubstitutionAccepted") is not False:
                        invalid.append("FOR-322 cpu.png slot accepts full-GM substitution")
        support_guard = for322.get("supportGuard")
        if not isinstance(support_guard, dict):
            invalid.append("FOR-322 supportGuard missing")
        else:
            if support_guard.get("supportStatus") != "not-supported":
                invalid.append("FOR-322 support status changed")
            if support_guard.get("fullGmSubstitutionAccepted") is not False:
                invalid.append("FOR-322 support guard accepts full-GM substitution")

    if for322_stats is not None:
        if for322_stats.get("linear") != "FOR-322" or for322_stats.get("sceneId") != FOR322_SCENE_ID:
            invalid.append("FOR-322 stats.json has wrong linear or scene id")
        if for322_stats.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM substitution")
        if for322_stats.get("fullGmReferenceAccepted") is not False:
            invalid.append("FOR-322 stats accepts the full-GM reference")
        validate_cell(for322_stats.get("cell"), "FOR-322 stats cell", invalid)

    if route_gpu is not None:
        if route_gpu.get("status") != "expected-unsupported":
            invalid.append("FOR-322 WebGPU route status changed")
        if route_gpu.get("refusalReason") != GPU_REFUSAL:
            invalid.append("FOR-322 WebGPU refusal reason changed")

    follows = {
        "for327Artifact": rel(FOR327_ARTIFACT),
        "for327Skia": rel(SKIA),
        "for322Artifact": rel(FOR322_ARTIFACT),
        "for322Cpu": rel(CPU),
        "for322Stats": rel(FOR322_STATS),
        "for322RouteGpu": rel(FOR322_ROUTE_GPU),
    }
    input_summary = {
        "skia": {
            "path": rel(SKIA),
            "sourceLinear": "FOR-327",
            "sourceSceneId": FOR327_SCENE_ID,
            "dimensions": skia_size,
            "sha256": sha256(SKIA) if SKIA.is_file() else None,
        },
        "cpu": {
            "path": rel(CPU),
            "sourceLinear": "FOR-322",
            "sourceSceneId": FOR322_SCENE_ID,
            "dimensions": cpu_size,
            "sha256": sha256(CPU) if CPU.is_file() else None,
        },
    }
    return {"follows": follows, "inputs": input_summary}, invalid


def compare_images() -> dict[str, Any]:
    skia = rgba_image(SKIA)
    cpu = rgba_image(CPU)
    if skia.size != EXPECTED_SIZE or cpu.size != EXPECTED_SIZE:
        fail("compare_images called with invalid dimensions")

    diff = Image.new("RGBA", EXPECTED_SIZE)
    diff_pixels = diff.load()
    skia_pixels = skia.load()
    cpu_pixels = cpu.load()

    different = 0
    max_delta = [0, 0, 0, 0]
    sum_abs_delta = [0, 0, 0, 0]
    min_x = EXPECTED_SIZE[0]
    min_y = EXPECTED_SIZE[1]
    max_x = -1
    max_y = -1

    for y in range(EXPECTED_SIZE[1]):
        for x in range(EXPECTED_SIZE[0]):
            skia_rgba = skia_pixels[x, y]
            cpu_rgba = cpu_pixels[x, y]
            delta = tuple(abs(int(cpu_rgba[index]) - int(skia_rgba[index])) for index in range(4))
            if delta != (0, 0, 0, 0):
                different += 1
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
                for index, value in enumerate(delta):
                    max_delta[index] = max(max_delta[index], value)
                    sum_abs_delta[index] += value
                diff_pixels[x, y] = (delta[0], delta[1], delta[2], 255)
            else:
                diff_pixels[x, y] = (0, 0, 0, 0)

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    diff.save(DIFF)
    total_pixels = EXPECTED_SIZE[0] * EXPECTED_SIZE[1]
    bbox = None if different == 0 else {"left": min_x, "top": min_y, "right": max_x, "bottom": max_y}
    similarity = ((total_pixels - different) / total_pixels) * 100.0
    return {
        "diffPng": rel(DIFF),
        "diffEncoding": "absolute RGB channel deltas with opaque changed pixels and transparent equal pixels",
        "pixelComparison": "strict RGBA pixel comparison, CPU minus Skia absolute deltas",
        "totalPixels": total_pixels,
        "differentPixels": different,
        "matchingPixels": total_pixels - different,
        "cellSimilarityPercent": round(similarity, 6),
        "maxDeltaByChannel": {
            "r": max_delta[0],
            "g": max_delta[1],
            "b": max_delta[2],
            "a": max_delta[3],
        },
        "sumAbsDeltaByChannel": {
            "r": sum_abs_delta[0],
            "g": sum_abs_delta[1],
            "b": sum_abs_delta[2],
            "a": sum_abs_delta[3],
        },
        "sumAbsDeltaTotal": sum(sum_abs_delta),
        "differentPixelBoundingBox": bbox,
        "hashes": {
            "skiaPngSha256": sha256(SKIA),
            "cpuPngSha256": sha256(CPU),
            "diffPngSha256": sha256(DIFF),
        },
    }


def build_artifact() -> dict[str, Any]:
    if DIFF.exists():
        DIFF.unlink()
    validated, invalid_reasons = validate_inputs()
    if invalid_reasons:
        decision = DECISION_INVALID
        comparison = None
        interpretation = {
            "requiresCpuRasterAudit": False,
            "reason": "reference input invalid; no Skia-vs-CPU raster conclusion is made",
            "classification": "input-invalid",
        }
    else:
        comparison = compare_images()
        decision = DECISION_MATCH if comparison["differentPixels"] == 0 else DECISION_AUDIT
        interpretation = {
            "requiresCpuRasterAudit": decision == DECISION_AUDIT,
            "reason": (
                "strict selected-cell pixels differ; the gap is classified as a CPU raster audit input, "
                "not as a renderer fix or scene promotion"
                if decision == DECISION_AUDIT
                else "strict selected-cell pixels match exactly"
            ),
            "classification": "cpu-diff-requires-raster-audit" if decision == DECISION_AUDIT else "match",
        }

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "allowedDecisions": [DECISION_MATCH, DECISION_AUDIT, DECISION_INVALID],
        "sceneId": SCENE_ID,
        "selectedCell": SELECTED_CELL_EXPECTED,
        "follows": validated["follows"],
        "inputs": validated["inputs"],
        "inputValidation": {
            "valid": not invalid_reasons,
            "expectedDimensions": EXPECTED_DIMENSIONS,
            "expectedSkiaInput": rel(SKIA),
            "expectedCpuInput": rel(CPU),
            "invalidReasons": invalid_reasons,
        },
        "comparison": comparison,
        "interpretation": interpretation,
        "preservedContracts": {
            "diffScope": "selected-cell-only",
            "fullGmSubstitutionAccepted": False,
            "fullGmCropAccepted": False,
            "fullGmScoreAccepted": False,
            "productionRendererChanged": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
            "gpuRendered": False,
            "scenePromotionChanged": False,
            "fidelityScoreCounted": False,
            "cpuRendererFixed": False,
        },
        "validation": [
            "rtk python3 scripts/validate_for328_circular_arcs_stroke_butt_selected_cell_skia_cpu_diff.py",
            "rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py",
            "rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py",
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
    skia = data["inputs"]["skia"]
    cpu = data["inputs"]["cpu"]
    comparison = data.get("comparison")
    invalid_reasons = data["inputValidation"]["invalidReasons"]
    invalid_lines = "\n".join(f"- {reason}" for reason in invalid_reasons) or "- none"
    validations = "\n".join(f"- `{command}`" for command in data["validation"])
    contracts = data["preservedContracts"]

    if comparison is None:
        stats_table = "| Field | Value |\n|---|---|\n| diff PNG | `None` |\n| reason | `reference input invalid` |"
        hashes_table = "| Field | SHA-256 |\n|---|---|\n| Skia input | `None` |\n| CPU input | `None` |\n| diff PNG | `None` |"
    else:
        bbox = comparison["differentPixelBoundingBox"]
        stats_table = f"""| Field | Value |
|---|---|
| diff PNG | `{comparison["diffPng"]}` |
| diff encoding | `{comparison["diffEncoding"]}` |
| total pixels | `{comparison["totalPixels"]}` |
| different pixels | `{comparison["differentPixels"]}` |
| matching pixels | `{comparison["matchingPixels"]}` |
| cell similarity percent | `{comparison["cellSimilarityPercent"]}` |
| max delta by channel | `{md_value(comparison["maxDeltaByChannel"])}` |
| sum abs delta by channel | `{md_value(comparison["sumAbsDeltaByChannel"])}` |
| sum abs delta total | `{comparison["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(bbox)}` |"""
        hashes = comparison["hashes"]
        hashes_table = f"""| Field | SHA-256 |
|---|---|
| Skia input | `{hashes["skiaPngSha256"]}` |
| CPU input | `{hashes["cpuPngSha256"]}` |
| diff PNG | `{hashes["diffPngSha256"]}` |"""

    report = f"""# FOR-328 CircularArcsStrokeButt Selected-Cell Skia CPU Diff

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-328 compares only the `80x80` selected-cell pixels from the accepted
FOR-327 isolated Skia reference and the FOR-322 Kanvas CPU harness output.
The comparison uses strict RGBA pixels and does not use a full-GM PNG, crop,
global score, GPU render, fallback, threshold change, or scene promotion.

Raster audit required: `{data["interpretation"]["requiresCpuRasterAudit"]}`

Interpretation: {data["interpretation"]["reason"]}.

## Inputs

| Input | Path | Source | Dimensions | SHA-256 |
|---|---|---|---|---|
| Skia | `{skia["path"]}` | `{skia["sourceLinear"]}` / `{skia["sourceSceneId"]}` | `{md_value(skia["dimensions"])}` | `{skia["sha256"]}` |
| CPU Kanvas | `{cpu["path"]}` | `{cpu["sourceLinear"]}` / `{cpu["sourceSceneId"]}` | `{md_value(cpu["dimensions"])}` | `{cpu["sha256"]}` |

Input validation valid: `{data["inputValidation"]["valid"]}`

Invalid reasons:

{invalid_lines}

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

## Cell Diff Stats

{stats_table}

## Hashes

{hashes_table}

## Qualification

The current decision is `{data["decision"]}`. A non-zero selected-cell pixel
diff is treated as evidence for a focused CPU raster audit only. This report
does not identify a CPU fix, does not reinterpret the FOR-327 Skia reference
as invalid when its provenance checks pass, and does not promote
`CircularArcsStrokeButtGM` or this selected cell.

## Preserved Contracts

| Field | Value |
|---|---|
| diff scope | `{contracts["diffScope"]}` |
| full-GM substitution accepted | `{contracts["fullGmSubstitutionAccepted"]}` |
| full-GM crop accepted | `{contracts["fullGmCropAccepted"]}` |
| full-GM score accepted | `{contracts["fullGmScoreAccepted"]}` |
| production renderer changed | `{contracts["productionRendererChanged"]}` |
| WGSL changed | `{contracts["wgslChanged"]}` |
| threshold changed | `{contracts["thresholdChanged"]}` |
| fallback policy changed | `{contracts["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{contracts["kadreNativeDependencyAdded"]}` |
| GPU rendered | `{contracts["gpuRendered"]}` |
| scene promotion changed | `{contracts["scenePromotionChanged"]}` |
| fidelity score counted | `{contracts["fidelityScoreCounted"]}` |
| CPU renderer fixed | `{contracts["cpuRendererFixed"]}` |

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
    assert_selected_cell(data.get("selectedCell"), "artifact selectedCell")
    input_validation = data.get("inputValidation")
    if not isinstance(input_validation, dict):
        fail("artifact missing inputValidation")
    if input_validation.get("expectedSkiaInput") != rel(SKIA):
        fail("artifact expected Skia input changed")
    if input_validation.get("expectedCpuInput") != rel(CPU):
        fail("artifact expected CPU input changed")
    comparison = data.get("comparison")
    if data.get("decision") == DECISION_INVALID:
        if input_validation.get("valid") is not False:
            fail("invalid decision requires invalid inputValidation")
        if comparison is not None:
            fail("invalid decision must not include comparison stats")
        if DIFF.exists():
            fail("invalid decision must not leave a diff PNG")
    else:
        if input_validation.get("valid") is not True:
            fail("non-invalid decision requires valid inputValidation")
        if not isinstance(comparison, dict):
            fail("non-invalid decision requires comparison stats")
        if comparison.get("diffPng") != rel(DIFF) or not DIFF.is_file():
            fail("comparison must write the expected diff PNG")
        if comparison.get("hashes", {}).get("diffPngSha256") != sha256(DIFF):
            fail("comparison diff hash does not match the diff PNG")
        if data.get("decision") == DECISION_MATCH and comparison.get("differentPixels") != 0:
            fail("match decision cannot contain different pixels")
        if data.get("decision") == DECISION_AUDIT and comparison.get("differentPixels", 0) <= 0:
            fail("audit decision requires at least one different pixel")
    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    for key in [
        "fullGmSubstitutionAccepted",
        "fullGmCropAccepted",
        "fullGmScoreAccepted",
        "productionRendererChanged",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
        "gpuRendered",
        "scenePromotionChanged",
        "fidelityScoreCounted",
        "cpuRendererFixed",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        data["decision"],
        "strict RGBA pixels",
        "Raster audit required",
        "Cell Diff Stats",
        "different pixels",
        "cell similarity percent",
        "max delta by channel",
        "sum abs delta total",
        "different pixel bounding box",
        "SHA-256",
        "focused CPU raster audit only",
        "does not identify a CPU fix",
        "does not promote",
        "GPU rendered",
        "False",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")
    unsafe_patterns = [
        r"\bfull-GM substitution accepted\s+\|\s+`?True",
        r"\bfull-GM crop accepted\s+\|\s+`?True",
        r"\bfull-GM score accepted\s+\|\s+`?True",
        r"\bproduction renderer changed\s+\|\s+`?True",
        r"\bWGSL changed\s+\|\s+`?True",
        r"\bthreshold changed\s+\|\s+`?True",
        r"\bfallback policy changed\s+\|\s+`?True",
        r"\bGPU rendered\s+\|\s+`?True",
        r"\bscene promotion changed\s+\|\s+`?True",
        r"\bfidelity score counted\s+\|\s+`?True",
        r"\bCPU renderer fixed\s+\|\s+`?True",
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
    validate_report(data)
    print(data["decision"])


if __name__ == "__main__":
    main()
