#!/usr/bin/env python3
"""Materialize the FOR-330 white-background Skia-vs-CPU selected-cell diff."""

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
LINEAR_ID = "FOR-330"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-for-next-circular-arcs-stroke-butt-selected-cell-white-background-skia-cpu-comparison-ticket"
)

SCENE_ID = "circular-arcs-stroke-butt-selected-cell-white-background-diff-for330"
ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-330-circular-arcs-stroke-butt-selected-cell-white-background-diff.md"
)
SKIA_OVER_WHITE = ARTIFACT_DIR / "skia-over-white.png"
NORMALIZED_DIFF = ARTIFACT_DIR / "cpu-vs-skia-over-white-diff.png"

FOR327_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-skia-reference-for327"
FOR327_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR327_SCENE_ID
FOR327_ARTIFACT = FOR327_DIR / f"{FOR327_SCENE_ID}.json"
SKIA = FOR327_DIR / "skia.png"
SKIA_PROVENANCE = FOR327_DIR / "skia-reference-provenance.json"

FOR322_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-harness-for322"
FOR322_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR322_SCENE_ID
FOR322_ARTIFACT = FOR322_DIR / f"{FOR322_SCENE_ID}.json"
CPU = FOR322_DIR / "cpu.png"
FOR322_STATS = FOR322_DIR / "stats.json"
FOR322_ROUTE_CPU = FOR322_DIR / "route-cpu.json"

FOR329_SCENE_ID = "circular-arcs-stroke-butt-selected-cell-cpu-raster-audit-for329"
FOR329_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / FOR329_SCENE_ID
FOR329_ARTIFACT = FOR329_DIR / f"{FOR329_SCENE_ID}.json"
FOR329_REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/2026-06-04-for-329-circular-arcs-stroke-butt-selected-cell-cpu-raster-audit.md"
)

EXPECTED_DIMENSIONS = {"width": 80, "height": 80}
EXPECTED_SIZE = (80, 80)
EXPECTED_FOR329_NORMALIZED = {
    "totalPixels": 6400,
    "differentPixels": 2031,
    "matchingPixels": 4369,
    "similarityPercent": 68.265625,
    "maxDeltaByChannel": {"r": 39, "g": 43, "b": 31, "a": 0},
    "sumAbsDeltaTotal": 63527,
    "differentPixelBoundingBox": {"left": 12, "top": 12, "right": 67, "bottom": 67},
}

DECISION_RESIDUAL = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_RESIDUAL_PRESENT"
)
DECISION_MATCH = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_MATCH"
DECISION_INVALID = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_WHITE_BACKGROUND_DIFF_INPUT_INVALID"
FOR327_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY"
FOR322_DECISION_READY = "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY"
FOR329_DECISION_IDENTIFIED = (
    "CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_CPU_RASTER_AUDIT_CAUSE_IDENTIFIED"
)
FOR329_CAUSE_BACKGROUND_ALPHA = "CAUSE_CANDIDATE_BACKGROUND_ALPHA_CONTRACT_MISMATCH"
FOR329_RESIDUAL_TRACE = "CAUSE_RESIDUAL_REQUIRES_NORMALIZED_BACKGROUND_CPU_TRACE"

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

SAMPLES = [
    ("top_left_background", 0, 0),
    ("top_edge_background", 40, 0),
    ("left_edge_background", 0, 40),
    ("arc_rect_top_left", 20, 20),
    ("blue_top_stroke_center", 40, 20),
    ("red_right_stroke_center", 60, 40),
    ("red_bottom_stroke_center", 40, 60),
    ("cell_center_hole", 40, 40),
    ("bottom_right_background", 79, 79),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-330 validation failed: {message}")


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
            size = {"width": image.size[0], "height": image.size[1]}
    except OSError as error:
        invalid.append(f"{label} is not a readable PNG: {error}")
        return None
    if size != EXPECTED_DIMENSIONS:
        invalid.append(f"{label} dimensions are {size}, expected {EXPECTED_DIMENSIONS}")
    return size


def rgba_image(path: Path) -> Image.Image:
    if not path.is_file():
        fail(f"missing PNG file: {rel(path)}")
    with Image.open(path) as image:
        return image.convert("RGBA")


def validate_cell(cell: dict[str, Any] | None, label: str, invalid: list[str]) -> None:
    if not isinstance(cell, dict):
        invalid.append(f"{label} missing")
        return
    for key, expected in SELECTED_CELL_EXPECTED.items():
        if cell.get(key) != expected:
            invalid.append(f"{label}.{key} expected {expected!r}, got {cell.get(key)!r}")


def assert_cell(cell: dict[str, Any] | None, label: str) -> None:
    invalid: list[str] = []
    validate_cell(cell, label, invalid)
    if invalid:
        fail("; ".join(invalid))


def nested_get(data: dict[str, Any], path: list[str]) -> Any:
    current: Any = data
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def validate_for329_metrics(for329: dict[str, Any], invalid: list[str]) -> None:
    normalized = nested_get(for329, ["localMetrics", "cpuVsSkiaCompositedOverWhiteProbe"])
    if not isinstance(normalized, dict):
        invalid.append("FOR-329 normalized white-background probe metrics missing")
        return
    for key, expected in EXPECTED_FOR329_NORMALIZED.items():
        if normalized.get(key) != expected:
            invalid.append(f"FOR-329 normalized metric {key} expected {expected!r}, got {normalized.get(key)!r}")

    strict = nested_get(for329, ["localMetrics", "strictCpuVsSkia"])
    if not isinstance(strict, dict):
        invalid.append("FOR-329 strict CPU-vs-Skia metrics missing")
    elif strict.get("differentPixels") != 6400 or strict.get("similarityPercent") != 0.0:
        invalid.append("FOR-329 strict all-pixel mismatch metrics changed")

    cause = for329.get("causeAssessment")
    if not isinstance(cause, dict):
        invalid.append("FOR-329 causeAssessment missing")
        return
    if cause.get("primaryCause") != FOR329_CAUSE_BACKGROUND_ALPHA:
        invalid.append("FOR-329 primary background/alpha cause changed")
    if cause.get("residualCause") != FOR329_RESIDUAL_TRACE:
        invalid.append("FOR-329 residual trace cause changed")


def validate_inputs() -> tuple[dict[str, Any], list[str]]:
    invalid: list[str] = []
    for327 = optional_json(FOR327_ARTIFACT, invalid)
    for322 = optional_json(FOR322_ARTIFACT, invalid)
    for322_stats = optional_json(FOR322_STATS, invalid)
    route_cpu = optional_json(FOR322_ROUTE_CPU, invalid)
    provenance = optional_json(SKIA_PROVENANCE, invalid)
    for329 = optional_json(FOR329_ARTIFACT, invalid)

    skia_size = image_size(SKIA, invalid, "FOR-327 skia.png")
    cpu_size = image_size(CPU, invalid, "FOR-322 cpu.png")

    if for327 is not None:
        if for327.get("linear") != "FOR-327" or for327.get("sceneId") != FOR327_SCENE_ID:
            invalid.append("FOR-327 artifact identity changed")
        if for327.get("decision") != FOR327_DECISION_READY:
            invalid.append("FOR-327 Skia reference is not ready")
        if for327.get("acceptedSkiaPng") != rel(SKIA):
            invalid.append("FOR-327 acceptedSkiaPng does not point at skia.png")
        candidate = for327.get("candidateSkiaReference")
        if not isinstance(candidate, dict):
            invalid.append("FOR-327 candidateSkiaReference missing")
        else:
            if candidate.get("accepted") is not True:
                invalid.append("FOR-327 candidate Skia reference is not accepted")
            if candidate.get("dimensions") != EXPECTED_DIMENSIONS:
                invalid.append("FOR-327 candidate dimensions changed")
            if SKIA.is_file() and candidate.get("sha256") != sha256(SKIA):
                invalid.append("FOR-327 candidate sha256 does not match skia.png")
        validate_cell(for327.get("selectedCell"), "FOR-327 selectedCell", invalid)

    if provenance is not None:
        if provenance.get("sourceType") != "isolated-skia-selected-cell-render":
            invalid.append("FOR-327 provenance sourceType changed")
        if provenance.get("fullGmCrop") is not False:
            invalid.append("FOR-327 provenance accepts full-GM crop")
        if provenance.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-327 provenance accepts full-GM substitution")
        if provenance.get("cpuKanvasOutputAcceptedAsSkia") is not False:
            invalid.append("FOR-327 provenance accepts CPU output as Skia")

    if for322 is not None:
        if for322.get("linear") != "FOR-322" or for322.get("sceneId") != FOR322_SCENE_ID:
            invalid.append("FOR-322 artifact identity changed")
        if for322.get("decision") != FOR322_DECISION_READY:
            invalid.append("FOR-322 harness is not ready")
        validate_cell(for322.get("selectedCell"), "FOR-322 selectedCell", invalid)
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
            invalid.append("FOR-322 stats.json identity changed")
        if for322_stats.get("fullGmSubstitutionAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM substitution")
        if for322_stats.get("fullGmReferenceAccepted") is not False:
            invalid.append("FOR-322 stats accepts full-GM reference")
        validate_cell(for322_stats.get("cell"), "FOR-322 stats cell", invalid)

    if route_cpu is not None:
        if route_cpu.get("backend") != "CPU":
            invalid.append("FOR-322 route-cpu backend changed")
        if route_cpu.get("selectedRoute") != "cpu.raster.selected-cell-test-harness":
            invalid.append("FOR-322 CPU selectedRoute changed")
        if route_cpu.get("supportStatus") != "not-supported":
            invalid.append("FOR-322 CPU route support status changed")
        validate_cell(route_cpu.get("cell"), "FOR-322 route-cpu cell", invalid)

    if for329 is not None:
        if for329.get("linear") != "FOR-329" or for329.get("sceneId") != FOR329_SCENE_ID:
            invalid.append("FOR-329 artifact identity changed")
        if for329.get("decision") != FOR329_DECISION_IDENTIFIED:
            invalid.append("FOR-329 cause-identified decision changed")
        validate_cell(for329.get("selectedCell"), "FOR-329 selectedCell", invalid)
        validate_for329_metrics(for329, invalid)

    return {
        "follows": {
            "for327Artifact": rel(FOR327_ARTIFACT),
            "for327Skia": rel(SKIA),
            "for327Provenance": rel(SKIA_PROVENANCE),
            "for322Artifact": rel(FOR322_ARTIFACT),
            "for322Cpu": rel(CPU),
            "for322Stats": rel(FOR322_STATS),
            "for322RouteCpu": rel(FOR322_ROUTE_CPU),
            "for329Artifact": rel(FOR329_ARTIFACT),
            "for329Report": rel(FOR329_REPORT),
        },
        "inputs": {
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
            "for329Artifact": {
                "path": rel(FOR329_ARTIFACT),
                "sourceLinear": "FOR-329",
                "sourceSceneId": FOR329_SCENE_ID,
                "sha256": sha256(FOR329_ARTIFACT) if FOR329_ARTIFACT.is_file() else None,
                "expectedDecision": FOR329_DECISION_IDENTIFIED,
                "expectedNormalizedMetrics": EXPECTED_FOR329_NORMALIZED,
            },
        },
    }, invalid


def compare_and_write_outputs() -> dict[str, Any]:
    skia = rgba_image(SKIA)
    cpu = rgba_image(CPU)
    if skia.size != EXPECTED_SIZE or cpu.size != EXPECTED_SIZE:
        fail("compare_and_write_outputs called with invalid dimensions")

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    white = Image.new("RGBA", EXPECTED_SIZE, (255, 255, 255, 255))
    skia_over_white = Image.alpha_composite(white, skia)
    skia_over_white.save(SKIA_OVER_WHITE)

    diff = Image.new("RGBA", EXPECTED_SIZE)
    diff_pixels = diff.load()
    skia_white_pixels = skia_over_white.load()
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
            ref_rgba = skia_white_pixels[x, y]
            cpu_rgba = cpu_pixels[x, y]
            delta = tuple(abs(int(cpu_rgba[index]) - int(ref_rgba[index])) for index in range(4))
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

    diff.save(NORMALIZED_DIFF)
    total = EXPECTED_SIZE[0] * EXPECTED_SIZE[1]
    bbox = None if different == 0 else {"left": min_x, "top": min_y, "right": max_x, "bottom": max_y}
    similarity = round(((total - different) / total) * 100.0, 6)

    samples = []
    for name, x, y in SAMPLES:
        skia_rgba = tuple(skia_pixels[x, y])
        cpu_rgba = tuple(cpu_pixels[x, y])
        white_rgba = tuple(skia_white_pixels[x, y])
        diff_rgba = tuple(diff_pixels[x, y])
        samples.append(
            {
                "name": name,
                "x": x,
                "y": y,
                "skiaRgba": list(skia_rgba),
                "skiaOverWhiteRgba": list(white_rgba),
                "cpuRgba": list(cpu_rgba),
                "diffRgba": list(diff_rgba),
                "cpuMinusSkiaOverWhiteAbsDelta": [
                    abs(int(cpu_rgba[index]) - int(white_rgba[index])) for index in range(4)
                ],
            }
        )

    return {
        "normalization": {
            "skiaOverWhitePng": rel(SKIA_OVER_WHITE),
            "method": "PIL Image.alpha_composite(opaque white RGBA background, FOR-327 skia.png)",
            "backgroundRgba": [255, 255, 255, 255],
            "diagnosticOnly": True,
            "fidelityScoreCounted": False,
            "scenePromotionChanged": False,
            "dimensions": EXPECTED_DIMENSIONS,
            "sha256": sha256(SKIA_OVER_WHITE),
        },
        "comparison": {
            "diffPng": rel(NORMALIZED_DIFF),
            "diffEncoding": "absolute RGB channel deltas with opaque changed pixels and transparent equal pixels",
            "pixelComparison": "strict RGBA pixel comparison, CPU minus Skia-over-white absolute deltas",
            "totalPixels": total,
            "differentPixels": different,
            "matchingPixels": total - different,
            "cellSimilarityPercent": similarity,
            "maxDeltaByChannel": {"r": max_delta[0], "g": max_delta[1], "b": max_delta[2], "a": max_delta[3]},
            "sumAbsDeltaByChannel": {"r": sum_abs_delta[0], "g": sum_abs_delta[1], "b": sum_abs_delta[2], "a": sum_abs_delta[3]},
            "sumAbsDeltaTotal": sum(sum_abs_delta),
            "differentPixelBoundingBox": bbox,
            "samples": samples,
            "hashes": {
                "skiaInputPngSha256": sha256(SKIA),
                "cpuInputPngSha256": sha256(CPU),
                "for329ArtifactSha256": sha256(FOR329_ARTIFACT),
                "skiaOverWhitePngSha256": sha256(SKIA_OVER_WHITE),
                "diffPngSha256": sha256(NORMALIZED_DIFF),
            },
        },
    }


def decision_for(comparison: dict[str, Any]) -> str:
    if comparison["differentPixels"] == 0:
        return DECISION_MATCH
    expected = EXPECTED_FOR329_NORMALIZED
    if (
        comparison["differentPixels"] == expected["differentPixels"]
        and comparison["matchingPixels"] == expected["matchingPixels"]
        and comparison["cellSimilarityPercent"] == expected["similarityPercent"]
        and comparison["maxDeltaByChannel"] == expected["maxDeltaByChannel"]
        and comparison["sumAbsDeltaTotal"] == expected["sumAbsDeltaTotal"]
        and comparison["differentPixelBoundingBox"] == expected["differentPixelBoundingBox"]
    ):
        return DECISION_RESIDUAL
    return DECISION_INVALID


def build_artifact() -> dict[str, Any]:
    for output in [SKIA_OVER_WHITE, NORMALIZED_DIFF]:
        if output.exists():
            output.unlink()

    validated, invalid_reasons = validate_inputs()
    outputs = None
    decision = DECISION_INVALID
    interpretation = {
        "classification": "input-invalid",
        "residualPresent": False,
        "reason": "one or more FOR-327/FOR-322/FOR-329 prerequisites are invalid; no renderer conclusion is made",
        "recommendedNextAction": "regenerate or repair prerequisites before comparing the white-background selected cell",
    }

    if not invalid_reasons:
        outputs = compare_and_write_outputs()
        comparison = outputs["comparison"]
        decision = decision_for(comparison)
        if decision == DECISION_RESIDUAL:
            interpretation = {
                "classification": "white-background-residual-present",
                "residualPresent": True,
                "reason": (
                    "FOR-329 normalized-background metrics are reproduced: background/alpha is isolated, "
                    "while stroke/color residuals remain in the selected cell."
                ),
                "recommendedNextAction": (
                    "Trace the normalized-background CPU stroke/color path before any CPU renderer correction "
                    "or support claim."
                ),
            }
        elif decision == DECISION_MATCH:
            interpretation = {
                "classification": "match-after-white-background-normalization",
                "residualPresent": False,
                "reason": "CPU and Skia-over-white selected-cell pixels match exactly.",
                "recommendedNextAction": "keep this as diagnostic evidence only; do not promote scene support from this probe",
            }
        else:
            invalid_reasons.append("white-background metrics diverge from FOR-329 expected residual metrics")

    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": decision,
        "allowedDecisions": [DECISION_RESIDUAL, DECISION_MATCH, DECISION_INVALID],
        "sceneId": SCENE_ID,
        "selectedCell": SELECTED_CELL_EXPECTED,
        "follows": validated["follows"],
        "inputs": validated["inputs"],
        "inputValidation": {
            "valid": not invalid_reasons,
            "expectedDimensions": EXPECTED_DIMENSIONS,
            "expectedFor329Decision": FOR329_DECISION_IDENTIFIED,
            "expectedFor329PrimaryCause": FOR329_CAUSE_BACKGROUND_ALPHA,
            "expectedFor329ResidualCause": FOR329_RESIDUAL_TRACE,
            "expectedFor329NormalizedMetrics": EXPECTED_FOR329_NORMALIZED,
            "invalidReasons": invalid_reasons,
        },
        "normalization": None if outputs is None else outputs["normalization"],
        "comparison": None if outputs is None else outputs["comparison"],
        "interpretation": interpretation,
        "preservedContracts": {
            "diffScope": "selected-cell-only",
            "diagnosticOnly": True,
            "fullGmSubstitutionAccepted": False,
            "fullGmCropAccepted": False,
            "fullGmScoreAccepted": False,
            "productionRendererChanged": False,
            "cpuRendererFixed": False,
            "gpuRendered": False,
            "wgslChanged": False,
            "thresholdChanged": False,
            "fallbackPolicyChanged": False,
            "kadreNativeDependencyAdded": False,
            "scenePromotionChanged": False,
            "fidelityScoreCounted": False,
        },
        "validation": [
            "rtk python3 scripts/validate_for330_circular_arcs_stroke_butt_selected_cell_white_background_diff.py",
            "rtk python3 scripts/validate_for329_circular_arcs_stroke_butt_selected_cell_cpu_raster_audit.py",
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


def sample_table(samples: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{name}` | `{x},{y}` | `{skia}` | `{white}` | `{cpu}` | `{delta}` |".format(
            name=sample["name"],
            x=sample["x"],
            y=sample["y"],
            skia=sample["skiaRgba"],
            white=sample["skiaOverWhiteRgba"],
            cpu=sample["cpuRgba"],
            delta=sample["cpuMinusSkiaOverWhiteAbsDelta"],
        )
        for sample in samples
    )


def write_report(data: dict[str, Any]) -> None:
    validation_lines = "\n".join(f"- `{command}`" for command in data["validation"])
    invalid_lines = "\n".join(f"- {reason}" for reason in data["inputValidation"]["invalidReasons"]) or "- none"
    contracts = data["preservedContracts"]
    inputs = data["inputs"]
    normalization = data.get("normalization")
    comparison = data.get("comparison")

    if comparison is None or normalization is None:
        comparison_section = "Input validation failed; no white-background PNG or diff PNG was produced."
        samples_section = ""
        hash_section = ""
    else:
        samples_section = sample_table(comparison["samples"])
        hashes = comparison["hashes"]
        comparison_section = f"""| Field | Value |
|---|---|
| Skia-over-white PNG | `{normalization["skiaOverWhitePng"]}` |
| normalization method | `{normalization["method"]}` |
| diff PNG | `{comparison["diffPng"]}` |
| diff encoding | `{comparison["diffEncoding"]}` |
| total pixels | `{comparison["totalPixels"]}` |
| different pixels | `{comparison["differentPixels"]}` |
| matching pixels | `{comparison["matchingPixels"]}` |
| cell similarity percent | `{comparison["cellSimilarityPercent"]}` |
| max delta by channel | `{md_value(comparison["maxDeltaByChannel"])}` |
| sum abs delta by channel | `{md_value(comparison["sumAbsDeltaByChannel"])}` |
| sum abs delta total | `{comparison["sumAbsDeltaTotal"]}` |
| different pixel bounding box | `{md_value(comparison["differentPixelBoundingBox"])}` |"""
        hash_section = f"""## Hashes

| Field | SHA-256 |
|---|---|
| Skia input | `{hashes["skiaInputPngSha256"]}` |
| CPU input | `{hashes["cpuInputPngSha256"]}` |
| FOR-329 artifact | `{hashes["for329ArtifactSha256"]}` |
| Skia over white | `{hashes["skiaOverWhitePngSha256"]}` |
| normalized diff | `{hashes["diffPngSha256"]}` |

## Samples

| Sample | XY | Skia RGBA | Skia over white RGBA | CPU RGBA | CPU vs Skia-over-white abs delta |
|---|---|---|---|---|---|
{samples_section}
"""

    report = f"""# FOR-330 CircularArcsStrokeButt Selected-Cell White-Background Diff

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-330 materializes the selected-cell diagnostic from FOR-329: the accepted
FOR-327 Skia transparent reference is alpha-composited over opaque white and
then compared with the FOR-322 Kanvas CPU selected-cell PNG. This is a
diagnostic probe only, not a CPU renderer fix, not a fidelity score, and not a
scene promotion.

Interpretation: {data["interpretation"]["reason"]}

Recommended next action: {data["interpretation"]["recommendedNextAction"]}

## Inputs

| Input | Path | Source | Dimensions / decision | SHA-256 |
|---|---|---|---|---|
| Skia | `{inputs["skia"]["path"]}` | `{inputs["skia"]["sourceLinear"]}` / `{inputs["skia"]["sourceSceneId"]}` | `{md_value(inputs["skia"]["dimensions"])}` | `{inputs["skia"]["sha256"]}` |
| CPU Kanvas | `{inputs["cpu"]["path"]}` | `{inputs["cpu"]["sourceLinear"]}` / `{inputs["cpu"]["sourceSceneId"]}` | `{md_value(inputs["cpu"]["dimensions"])}` | `{inputs["cpu"]["sha256"]}` |
| FOR-329 audit | `{inputs["for329Artifact"]["path"]}` | `{inputs["for329Artifact"]["sourceLinear"]}` / `{inputs["for329Artifact"]["sourceSceneId"]}` | `{inputs["for329Artifact"]["expectedDecision"]}` | `{inputs["for329Artifact"]["sha256"]}` |

Input validation valid: `{data["inputValidation"]["valid"]}`

Invalid reasons:

{invalid_lines}

## White-Background Comparison

{comparison_section}

{hash_section}
## Preserved Contracts

| Field | Value |
|---|---|
| diff scope | `{contracts["diffScope"]}` |
| diagnostic only | `{contracts["diagnosticOnly"]}` |
| full-GM substitution accepted | `{contracts["fullGmSubstitutionAccepted"]}` |
| full-GM crop accepted | `{contracts["fullGmCropAccepted"]}` |
| full-GM score accepted | `{contracts["fullGmScoreAccepted"]}` |
| production renderer changed | `{contracts["productionRendererChanged"]}` |
| CPU renderer fixed | `{contracts["cpuRendererFixed"]}` |
| GPU rendered | `{contracts["gpuRendered"]}` |
| WGSL changed | `{contracts["wgslChanged"]}` |
| threshold changed | `{contracts["thresholdChanged"]}` |
| fallback policy changed | `{contracts["fallbackPolicyChanged"]}` |
| Kadre/native dependency added | `{contracts["kadreNativeDependencyAdded"]}` |
| scene promotion changed | `{contracts["scenePromotionChanged"]}` |
| fidelity score counted | `{contracts["fidelityScoreCounted"]}` |

## Validation

{validation_lines}
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
    assert_cell(data.get("selectedCell"), "artifact selectedCell")

    input_validation = data.get("inputValidation")
    if not isinstance(input_validation, dict):
        fail("artifact missing inputValidation")
    if input_validation.get("expectedFor329NormalizedMetrics") != EXPECTED_FOR329_NORMALIZED:
        fail("artifact expected FOR-329 metrics changed")

    comparison = data.get("comparison")
    normalization = data.get("normalization")
    if data.get("decision") == DECISION_INVALID:
        if input_validation.get("valid") is not False:
            fail("invalid decision requires invalid inputValidation")
        return

    if input_validation.get("valid") is not True:
        fail("non-invalid decision requires valid inputValidation")
    if not isinstance(normalization, dict) or not isinstance(comparison, dict):
        fail("non-invalid decision requires normalization and comparison stats")
    if normalization.get("skiaOverWhitePng") != rel(SKIA_OVER_WHITE) or not SKIA_OVER_WHITE.is_file():
        fail("normalization must write the expected skia-over-white PNG")
    if comparison.get("diffPng") != rel(NORMALIZED_DIFF) or not NORMALIZED_DIFF.is_file():
        fail("comparison must write the expected normalized diff PNG")
    if normalization.get("sha256") != sha256(SKIA_OVER_WHITE):
        fail("skia-over-white hash does not match PNG")
    if comparison.get("hashes", {}).get("diffPngSha256") != sha256(NORMALIZED_DIFF):
        fail("normalized diff hash does not match PNG")
    if comparison.get("hashes", {}).get("skiaOverWhitePngSha256") != sha256(SKIA_OVER_WHITE):
        fail("comparison Skia-over-white hash does not match PNG")

    if data.get("decision") == DECISION_MATCH and comparison.get("differentPixels") != 0:
        fail("match decision cannot contain different pixels")
    if data.get("decision") == DECISION_RESIDUAL:
        expected = EXPECTED_FOR329_NORMALIZED
        for key, expected_value in expected.items():
            comparison_key = "cellSimilarityPercent" if key == "similarityPercent" else key
            if comparison.get(comparison_key) != expected_value:
                fail(f"residual decision requires FOR-329 metric {key}")

    samples = comparison.get("samples")
    if not isinstance(samples, list) or len(samples) != len(SAMPLES):
        fail("comparison samples missing")

    preserved = data.get("preservedContracts")
    if not isinstance(preserved, dict):
        fail("artifact missing preservedContracts")
    if preserved.get("diagnosticOnly") is not True:
        fail("preservedContracts.diagnosticOnly must be true")
    for key in [
        "fullGmSubstitutionAccepted",
        "fullGmCropAccepted",
        "fullGmScoreAccepted",
        "productionRendererChanged",
        "cpuRendererFixed",
        "gpuRendered",
        "wgslChanged",
        "thresholdChanged",
        "fallbackPolicyChanged",
        "kadreNativeDependencyAdded",
        "scenePromotionChanged",
        "fidelityScoreCounted",
    ]:
        if preserved.get(key) is not False:
            fail(f"preservedContracts.{key} must be false")


def validate_report(data: dict[str, Any]) -> None:
    report = read_text(REPORT)
    for needle in [
        LINEAR_ID,
        SOURCE_MEMORY,
        data["decision"],
        "alpha-composited over opaque white",
        "diagnostic probe only",
        "not a CPU renderer fix",
        "not a fidelity score",
        "scene promotion",
        "White-Background Comparison",
        "Skia-over-white PNG",
        "diff encoding",
        "different pixels",
        "max delta by channel",
        "sum abs delta total",
        "different pixel bounding box",
        "Preserved Contracts",
        "CPU renderer fixed",
        "GPU rendered",
        "WGSL changed",
        "threshold changed",
        "fallback policy changed",
        "Kadre/native dependency added",
    ]:
        if needle not in report:
            fail(f"report missing `{needle}`")

    unsafe_patterns = [
        r"\bproduction renderer changed\s+\|\s+`?True",
        r"\bCPU renderer fixed\s+\|\s+`?True",
        r"\bGPU rendered\s+\|\s+`?True",
        r"\bWGSL changed\s+\|\s+`?True",
        r"\bthreshold changed\s+\|\s+`?True",
        r"\bfallback policy changed\s+\|\s+`?True",
        r"\bscene promotion changed\s+\|\s+`?True",
        r"\bfidelity score counted\s+\|\s+`?True",
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
    if data["decision"] == DECISION_INVALID:
        fail("; ".join(data["inputValidation"]["invalidReasons"]) or "input invalid")
    print(data["decision"])


if __name__ == "__main__":
    main()
