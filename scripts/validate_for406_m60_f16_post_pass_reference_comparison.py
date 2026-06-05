#!/usr/bin/env python3
"""Validate FOR-406 M60 F16 post-pass/reference comparison evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from PIL import Image


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-406"
SCENE_ID = "m60-f16-post-pass-reference-comparison-for406"
ROW_ID = "non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend"
SOURCE_MEMORY = (
    "global/kanvas/findings/"
    "for-405-observe-les-couleurs-post-passe-aa-stencil-cover-m60-f16"
)
SOURCE_DRAFT_MEMORY = (
    "global/kanvas/tickets/drafts/"
    "brouillon-ticket-for-406-m60-f16-comparer-post-passe-aa-stencil-cover-aux-references-cpu-skia"
)

ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts" / SCENE_ID
ARTIFACT = ARTIFACT_DIR / f"{SCENE_ID}.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline"
    / "2026-06-05-for-406-m60-f16-post-pass-reference-comparison.md"
)
FOR401_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-final-residual-origin-map-for401"
    / "m60-f16-final-residual-origin-map-for401.json"
)
FOR404_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-runtime-hook-for404"
    / "m60-f16-aa-stencil-cover-runtime-hook-for404.json"
)
FOR405_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405"
    / "m60-f16-aa-stencil-cover-post-pass-readback-for405.json"
)
SCENE_ARTIFACT_DIR = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join"
CPU_PNG = SCENE_ARTIFACT_DIR / "cpu.png"
SKIA_PNG = SCENE_ARTIFACT_DIR / "skia.png"
CAPTURE_TEST = (
    PROJECT_ROOT
    / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

ALLOWED_PIXEL_CLASSIFICATIONS = {
    "post-pass-already-diverged",
    "present-readback-diverged",
    "reference-match-inconclusive",
    "comparison-data-missing",
}
ALLOWED_GLOBAL_CLASSIFICATIONS = ALLOWED_PIXEL_CLASSIFICATIONS
EXPECTED_VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
    "rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py",
    "rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py",
    "rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-406 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_text(path: Path) -> str:
    require(path.is_file(), f"missing file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_rgba_pixels(path: Path) -> tuple[Image.Image, dict[str, Any]]:
    require(path.is_file(), f"missing PNG file: {rel(path)}")
    image = Image.open(path).convert("RGBA")
    metadata = {
        "path": rel(path),
        "dimensions": [image.width, image.height],
        "sha256": sha256(path),
    }
    return image, metadata


def rgba_at(image: Image.Image, x: int, y: int) -> list[int]:
    require(0 <= x < image.width and 0 <= y < image.height, f"pixel {(x, y)} outside image bounds")
    return list(image.getpixel((x, y)))


def delta(a: list[int], b: list[int]) -> dict[str, Any]:
    signed = [a[i] - b[i] for i in range(4)]
    absolute = [abs(v) for v in signed]
    return {
        "signedByChannel": {"r": signed[0], "g": signed[1], "b": signed[2], "a": signed[3]},
        "absoluteByChannel": {"r": absolute[0], "g": absolute[1], "b": absolute[2], "a": absolute[3]},
        "absoluteTotal": sum(absolute),
        "maxChannelDelta": max(absolute),
    }


def pixel_key(pixel: dict[str, Any]) -> tuple[int, int]:
    x = pixel.get("x")
    y = pixel.get("y")
    require(isinstance(x, int) and isinstance(y, int), "pixel coordinate missing")
    return x, y


def by_coordinate(pixels: list[Any], source_name: str) -> dict[tuple[int, int], dict[str, Any]]:
    out: dict[tuple[int, int], dict[str, Any]] = {}
    for pixel in pixels:
        require(isinstance(pixel, dict), f"{source_name} pixel must be an object")
        key = pixel_key(pixel)
        require(key not in out, f"duplicate coordinate in {source_name}: {key}")
        out[key] = pixel
    return out


def classify_pixel(
    post_pass: list[int] | None,
    current_gpu_final: list[int] | None,
    cpu: list[int] | None,
    skia: list[int] | None,
) -> str:
    if post_pass is None or current_gpu_final is None or cpu is None or skia is None:
        return "comparison-data-missing"
    if post_pass != cpu or post_pass != skia:
        return "post-pass-already-diverged"
    if current_gpu_final != skia:
        return "present-readback-diverged"
    return "reference-match-inconclusive"


def global_classification(counts: dict[str, int]) -> str:
    if counts.get("comparison-data-missing", 0) > 0:
        return "comparison-data-missing"
    if counts.get("post-pass-already-diverged", 0) > 0:
        return "post-pass-already-diverged"
    if counts.get("present-readback-diverged", 0) > 0:
        return "present-readback-diverged"
    return "reference-match-inconclusive"


def validate_sources() -> None:
    capture = read_text(CAPTURE_TEST)
    for needle in (
        "writeM60F16AaStencilCoverPostPassReadback",
        "for401SelectedResidualOriginPixels",
        "writePng(File(dir, \"skia.png\"), reference)",
        "writePng(File(dir, \"cpu.png\"), cpuBitmap)",
        "assertEquals(100.0, cpuCmp.similarity, 0.0)",
        "assertTrue(cpuCmp.matchingPixels == cpuCmp.totalPixels)",
    ):
        require(needle in capture, f"capture test missing source evidence: {needle}")


def validate_source_artifacts(
    for401: dict[str, Any],
    for404: dict[str, Any],
    for405: dict[str, Any],
) -> None:
    require(for401.get("linear") == "FOR-401", "FOR-401 source Linear id changed")
    require(for401.get("classification") == "residual-visible-only-at-final-readback", "FOR-401 classification changed")
    summary401 = for401.get("residualOriginSummary")
    require(isinstance(summary401, dict), "FOR-401 residualOriginSummary missing")
    require(summary401.get("currentTotalResidual") == 62748, "FOR-401 total residual changed")
    require(summary401.get("currentMismatchPixels") == 1615, "FOR-401 mismatch count changed")
    require(summary401.get("selectedResidualTotal") == 1560, "FOR-401 selected residual changed")
    require(summary401.get("readbackOnlyUnknownCount") == 16, "FOR-401 unknown count changed")

    require(for404.get("linear") == "FOR-404", "FOR-404 source Linear id changed")
    require(for404.get("classification") == "aa-stencil-cover-post-pass-readback-blocked", "FOR-404 classification changed")
    runtime404 = for404.get("runtimeHook")
    require(isinstance(runtime404, dict), "FOR-404 runtimeHook missing")
    require(runtime404.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "FOR-404 pipeline changed")
    require(runtime404.get("postPassReadbackAvailable") is False, "FOR-404 must remain readback-blocked")

    require(for405.get("linear") == "FOR-405", "FOR-405 source Linear id changed")
    require(for405.get("classification") == "aa-stencil-cover-post-pass-color-observed", "FOR-405 classification changed")
    runtime405 = for405.get("runtimeReadback")
    require(isinstance(runtime405, dict), "FOR-405 runtimeReadback missing")
    require(runtime405.get("pipelineFamily") == "StencilCoverAaPolygonDraw", "FOR-405 pipeline changed")
    require(runtime405.get("intermediateFormat") == "RGBA16Float", "FOR-405 intermediate format changed")
    require(runtime405.get("postPassReadbackAvailable") is True, "FOR-405 post-pass readback must be available")
    summary405 = for405.get("postPassSummary")
    require(isinstance(summary405, dict), "FOR-405 postPassSummary missing")
    require(summary405.get("postPassObservedCount") == 16, "FOR-405 observed count changed")


def build_artifact() -> dict[str, Any]:
    for401 = load_json(FOR401_ARTIFACT)
    for404 = load_json(FOR404_ARTIFACT)
    for405 = load_json(FOR405_ARTIFACT)
    validate_sources()
    validate_source_artifacts(for401, for404, for405)

    cpu_image, cpu_metadata = load_rgba_pixels(CPU_PNG)
    skia_image, skia_metadata = load_rgba_pixels(SKIA_PNG)
    require(cpu_metadata["dimensions"] == skia_metadata["dimensions"], "CPU and Skia PNG dimensions differ")

    selected401 = for401.get("selectedPixels")
    selected405 = for405.get("selectedPixels")
    require(isinstance(selected401, list) and len(selected401) == 16, "FOR-401 selected pixel count changed")
    require(isinstance(selected405, list) and len(selected405) == 16, "FOR-405 selected pixel count changed")
    for405_by_coord = by_coordinate(selected405, "FOR-405")

    compared_pixels: list[dict[str, Any]] = []
    counts = {classification: 0 for classification in sorted(ALLOWED_PIXEL_CLASSIFICATIONS)}
    for index, source_pixel in enumerate(selected401):
        require(isinstance(source_pixel, dict), f"FOR-401 pixel {index} must be an object")
        x, y = pixel_key(source_pixel)
        post_source = for405_by_coord.get((x, y))
        post_pass = None
        post_pass_float = None
        inspected_events = None
        if post_source is not None:
            post = post_source.get("postPass")
            require(isinstance(post, dict), f"FOR-405 postPass missing at {(x, y)}")
            post_pass = post.get("observedRgba8")
            post_pass_float = post.get("observedRgbaFloat")
            inspected_events = post.get("inspectedEvents")
            require(isinstance(post_pass, list) and len(post_pass) == 4, f"FOR-405 RGBA8 missing at {(x, y)}")
            require(isinstance(post_pass_float, list) and len(post_pass_float) == 4, f"FOR-405 RGBA float missing at {(x, y)}")
            require(post.get("observed") is True, f"FOR-405 post-pass not observed at {(x, y)}")
            require(post.get("readbackAvailable") is True, f"FOR-405 readback unavailable at {(x, y)}")
            require(post.get("pipelineFamily") == "StencilCoverAaPolygonDraw", f"FOR-405 pipeline changed at {(x, y)}")

        current_gpu_final = source_pixel.get("currentGpuRgba")
        skia_reference = source_pixel.get("referenceRgba")
        residual_by_channel = source_pixel.get("residualByChannel")
        require(isinstance(current_gpu_final, list) and len(current_gpu_final) == 4, f"current GPU missing at {(x, y)}")
        require(isinstance(skia_reference, list) and len(skia_reference) == 4, f"FOR-401 reference missing at {(x, y)}")

        cpu = rgba_at(cpu_image, x, y)
        skia = rgba_at(skia_image, x, y)
        require(cpu == skia, f"CPU/Skia PNGs differ at {(x, y)}")
        require(skia == skia_reference, f"FOR-401 reference does not match Skia PNG at {(x, y)}")

        classification = classify_pixel(post_pass, current_gpu_final, cpu, skia)
        counts[classification] += 1
        compared_pixels.append(
            {
                "index": index,
                "x": x,
                "y": y,
                "postPassObservedRgba8": post_pass,
                "postPassObservedRgbaFloat": post_pass_float,
                "currentGpuFinalRgba": current_gpu_final,
                "cpuRgba": cpu,
                "skiaRgba": skia,
                "for401ReferenceRgba": skia_reference,
                "postPassToCpuDelta": delta(post_pass, cpu) if post_pass is not None else None,
                "postPassToSkiaDelta": delta(post_pass, skia) if post_pass is not None else None,
                "currentGpuFinalToSkiaDelta": delta(current_gpu_final, skia),
                "for401ResidualByChannel": residual_by_channel,
                "for401ResidualTotal": source_pixel.get("residualTotal"),
                "postPassEqualsCurrentGpuFinal": post_pass == current_gpu_final,
                "cpuEqualsSkia": cpu == skia,
                "for400UsedAsDirectProof": False,
                "classification": classification,
                "classificationReason": (
                    "The FOR-405 post-pass RGBA8 sample already differs from the CPU/Skia references "
                    "at the FOR-401 coordinate, and it matches the current GPU final byte sample."
                    if classification == "post-pass-already-diverged"
                    else "The available samples do not prove post-pass divergence."
                ),
                "inspectedPostPassEvents": inspected_events,
            },
        )

    global_result = global_classification(counts)
    summary401 = for401["residualOriginSummary"]
    summary405 = for405["postPassSummary"]
    selected_residual_total = sum(pixel["currentGpuFinalToSkiaDelta"]["absoluteTotal"] for pixel in compared_pixels)
    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": SCENE_ID,
        "sourceSceneId": ROW_ID,
        "sourceMemory": SOURCE_MEMORY,
        "sourceDraftMemory": SOURCE_DRAFT_MEMORY,
        "sourceArtifacts": {
            "for401": rel(FOR401_ARTIFACT),
            "for404": rel(FOR404_ARTIFACT),
            "for405": rel(FOR405_ARTIFACT),
        },
        "sourceContext": {
            "for401Classification": for401.get("classification"),
            "for401CurrentTotalResidual": summary401.get("currentTotalResidual"),
            "for401CurrentMismatchPixels": summary401.get("currentMismatchPixels"),
            "for401SelectedResidualTotal": summary401.get("selectedResidualTotal"),
            "for401SelectedPixelCount": len(selected401),
            "for404Classification": for404.get("classification"),
            "for405Classification": for405.get("classification"),
            "for405PostPassObservedCount": summary405.get("postPassObservedCount"),
            "for400EvidencePolicy": "context-only-not-direct-write-proof",
            "for400UsedAsDirectProof": False,
        },
        "producer": "scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
        "sourceProducer": rel(CAPTURE_TEST),
        "decision": "M60_F16_POST_PASS_REFERENCE_COMPARISON_RECORDED",
        "classification": global_result,
        "globalClassification": global_result,
        "globalConclusion": (
            "divergence-already-visible-in-post-pass"
            if global_result == "post-pass-already-diverged"
            else "divergence-not-proven-at-post-pass"
        ),
        "allowedPixelClassifications": sorted(ALLOWED_PIXEL_CLASSIFICATIONS),
        "allowedGlobalClassifications": sorted(ALLOWED_GLOBAL_CLASSIFICATIONS),
        "supportClaim": False,
        "promoted": False,
        "correctionAppliedByDefault": False,
        "defaultRenderingChanged": False,
        "comparisonInputs": {
            "cpuPng": cpu_metadata,
            "skiaPng": skia_metadata,
            "cpuSkiaEqualitySource": "StrokeCapJoinSceneCaptureTest asserts cpuCmp similarity 100.0 and matchingPixels == totalPixels.",
            "postPassSource": "FOR-405 selectedPixels[].postPass.observedRgba8",
            "currentGpuFinalSource": "FOR-401 selectedPixels[].currentGpuRgba",
            "skiaSource": "m60-bounded-stroke-cap-join/skia.png sampled at the FOR-401 coordinates",
            "cpuSource": "m60-bounded-stroke-cap-join/cpu.png sampled at the FOR-401 coordinates",
        },
        "classificationRules": {
            "post-pass-already-diverged": (
                "post-pass observed RGBA8 differs from CPU or Skia at the same coordinate"
            ),
            "present-readback-diverged": (
                "post-pass observed RGBA8 matches CPU and Skia, but current GPU final differs from Skia"
            ),
            "reference-match-inconclusive": (
                "post-pass, current GPU final, CPU, and Skia byte samples match, so this comparison "
                "does not explain a residual"
            ),
            "comparison-data-missing": "one or more required per-pixel inputs are absent",
        },
        "comparisonSummary": {
            "selectedPixelCount": len(compared_pixels),
            "postPassAlreadyDivergedCount": counts["post-pass-already-diverged"],
            "presentReadbackDivergedCount": counts["present-readback-diverged"],
            "referenceMatchInconclusiveCount": counts["reference-match-inconclusive"],
            "comparisonDataMissingCount": counts["comparison-data-missing"],
            "postPassObservedCount": summary405.get("postPassObservedCount"),
            "postPassEqualsCurrentGpuFinalCount": sum(
                1 for pixel in compared_pixels if pixel["postPassEqualsCurrentGpuFinal"]
            ),
            "cpuEqualsSkiaCount": sum(1 for pixel in compared_pixels if pixel["cpuEqualsSkia"]),
            "selectedPostPassToSkiaDeltaTotal": sum(
                pixel["postPassToSkiaDelta"]["absoluteTotal"] for pixel in compared_pixels
            ),
            "selectedCurrentGpuFinalToSkiaDeltaTotal": selected_residual_total,
            "for401SelectedResidualTotal": summary401.get("selectedResidualTotal"),
        },
        "selectedPixels": compared_pixels,
        "nonGoalsPreserved": {
            "defaultRenderingChanged": False,
            "supportClaimRaised": False,
            "promoted": False,
            "thresholdChanged": False,
            "scoringChanged": False,
            "correctionApplied": False,
            "for380BroadCorrectionReintroduced": False,
            "for400UsedAsDirectProof": False,
            "generalizedOutsideM60F16": False,
        },
        "classificationReason": (
            "All 16 FOR-401 coordinates have FOR-405 post-pass RGBA8 samples that match the "
            "FOR-401 current GPU final bytes and differ from byte-identical CPU/Skia references; "
            "the divergence is therefore already visible after the AA stencil-cover pass, not only "
            "at final present/readback."
        ),
        "validationCommands": EXPECTED_VALIDATION_COMMANDS,
    }
    return artifact


def md_code(value: Any) -> str:
    return f"`{value}`"


def build_report(data: dict[str, Any]) -> str:
    summary = data["comparisonSummary"]
    rows = []
    for pixel in data["selectedPixels"]:
        rows.append(
            "| ({x}, {y}) | `{post}` | `{final}` | `{cpu}` | `{skia}` | `{post_cpu}` | `{post_skia}` | `{final_skia}` | `{classification}` |".format(
                x=pixel["x"],
                y=pixel["y"],
                post=pixel["postPassObservedRgba8"],
                final=pixel["currentGpuFinalRgba"],
                cpu=pixel["cpuRgba"],
                skia=pixel["skiaRgba"],
                post_cpu=pixel["postPassToCpuDelta"]["absoluteTotal"],
                post_skia=pixel["postPassToSkiaDelta"]["absoluteTotal"],
                final_skia=pixel["currentGpuFinalToSkiaDelta"]["absoluteTotal"],
                classification=pixel["classification"],
            ),
        )
    rows_text = "\n".join(rows)
    commands = "\n".join(f"- `{command}`" for command in data["validationCommands"])
    return f"""# FOR-406 M60 F16 post-pass reference comparison

Date: 2026-06-05

This analytical report compares the FOR-405 post-pass AA stencil-cover
readback values with current GPU final bytes and CPU/Skia references for the
16 FOR-401 residual coordinates. It does not change default rendering, apply a
correction, promote the scene, or use FOR-400 as direct write proof.

## Result

Global classification: `{data["globalClassification"]}`.
Conclusion: `{data["globalConclusion"]}`.

The divergence is already visible in the FOR-405 post-pass samples: all 16
post-pass RGBA8 values match the FOR-401 current GPU final bytes and differ
from the byte-identical CPU/Skia reference values at the same coordinates.

## Sources

| Source | Path |
|---|---|
| FOR-405 post-pass readback | `{data["sourceArtifacts"]["for405"]}` |
| FOR-401 selected residual pixels | `{data["sourceArtifacts"]["for401"]}` |
| FOR-404 runtime-hook context | `{data["sourceArtifacts"]["for404"]}` |
| CPU PNG | `{data["comparisonInputs"]["cpuPng"]["path"]}` |
| Skia PNG | `{data["comparisonInputs"]["skiaPng"]["path"]}` |
| Finding memory | `{data["sourceMemory"]}` |
| Ticket draft memory | `{data["sourceDraftMemory"]}` |

FOR-400 remains context only and is not used as direct writer proof.

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `{summary["selectedPixelCount"]}` |
| Post-pass already diverged | `{summary["postPassAlreadyDivergedCount"]}` |
| Present/readback diverged only | `{summary["presentReadbackDivergedCount"]}` |
| Reference-match inconclusive | `{summary["referenceMatchInconclusiveCount"]}` |
| Comparison data missing | `{summary["comparisonDataMissingCount"]}` |
| Post-pass equals current GPU final | `{summary["postPassEqualsCurrentGpuFinalCount"]}` |
| CPU equals Skia | `{summary["cpuEqualsSkiaCount"]}` |
| Sum delta post-pass -> Skia | `{summary["selectedPostPassToSkiaDeltaTotal"]}` |
| Sum delta final GPU -> Skia | `{summary["selectedCurrentGpuFinalToSkiaDeltaTotal"]}` |
| FOR-401 selected residual total | `{summary["for401SelectedResidualTotal"]}` |

## Pixel Comparison

| Coordinate | Post-pass observed | Current GPU final | CPU | Skia | Delta post-pass->CPU | Delta post-pass->Skia | Delta final GPU->Skia | Classification |
|---|---|---|---|---|---:|---:|---:|---|
{rows_text}

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `{str(data["supportClaim"]).lower()}` |
| promoted=false | `{str(data["promoted"]).lower()}` |
| correctionAppliedByDefault=false | `{str(data["correctionAppliedByDefault"]).lower()}` |
| defaultRenderingChanged=false | `{str(data["defaultRenderingChanged"]).lower()}` |
| FOR-400 direct proof | `{data["nonGoalsPreserved"]["for400UsedAsDirectProof"]}` |

## Validations

{commands}
"""


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def validate_artifact(actual: dict[str, Any], expected: dict[str, Any]) -> str:
    require(actual == expected, f"{rel(ARTIFACT)} is stale or does not match recomputed FOR-406 comparison")
    require(actual.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(actual.get("linear") == LINEAR_ID, "wrong Linear id")
    require(actual.get("sceneId") == SCENE_ID, "wrong scene id")
    require(actual.get("sourceSceneId") == ROW_ID, "wrong source scene id")
    require(actual.get("sourceMemory") == SOURCE_MEMORY, "wrong finding memory")
    require(actual.get("sourceDraftMemory") == SOURCE_DRAFT_MEMORY, "wrong ticket draft memory")
    require(actual.get("sourceArtifacts", {}).get("for401") == rel(FOR401_ARTIFACT), "FOR-401 artifact not linked")
    require(actual.get("sourceArtifacts", {}).get("for404") == rel(FOR404_ARTIFACT), "FOR-404 artifact not linked")
    require(actual.get("sourceArtifacts", {}).get("for405") == rel(FOR405_ARTIFACT), "FOR-405 artifact not linked")
    classification = actual.get("classification")
    require(classification == "post-pass-already-diverged", "global classification changed")
    require(actual.get("globalConclusion") == "divergence-already-visible-in-post-pass", "global conclusion changed")
    require(set(actual.get("allowedPixelClassifications", [])) == ALLOWED_PIXEL_CLASSIFICATIONS, "pixel taxonomy changed")
    require(set(actual.get("allowedGlobalClassifications", [])) == ALLOWED_GLOBAL_CLASSIFICATIONS, "global taxonomy changed")
    require(actual.get("supportClaim") is False, "supportClaim must remain false")
    require(actual.get("promoted") is False, "promoted must remain false")
    require(actual.get("correctionAppliedByDefault") is False, "correction must not be default")
    require(actual.get("defaultRenderingChanged") is False, "default rendering must not change")

    context = actual.get("sourceContext")
    require(isinstance(context, dict), "sourceContext missing")
    require(context.get("for400EvidencePolicy") == "context-only-not-direct-write-proof", "FOR-400 policy changed")
    require(context.get("for400UsedAsDirectProof") is False, "FOR-400 used as direct proof")
    require(context.get("for401SelectedPixelCount") == 16, "FOR-401 selected count changed")
    require(context.get("for405PostPassObservedCount") == 16, "FOR-405 observed count changed")

    summary = actual.get("comparisonSummary")
    require(isinstance(summary, dict), "comparisonSummary missing")
    require(summary.get("selectedPixelCount") == 16, "selected count changed")
    require(summary.get("postPassAlreadyDivergedCount") == 16, "post-pass divergence count changed")
    require(summary.get("presentReadbackDivergedCount") == 0, "present/readback-only count changed")
    require(summary.get("referenceMatchInconclusiveCount") == 0, "inconclusive count changed")
    require(summary.get("comparisonDataMissingCount") == 0, "missing-data count changed")
    require(summary.get("postPassEqualsCurrentGpuFinalCount") == 16, "post-pass/final equality count changed")
    require(summary.get("cpuEqualsSkiaCount") == 16, "CPU/Skia equality count changed")
    require(summary.get("selectedPostPassToSkiaDeltaTotal") == 1560, "post-pass selected delta changed")
    require(summary.get("selectedCurrentGpuFinalToSkiaDeltaTotal") == 1560, "final selected delta changed")
    require(summary.get("for401SelectedResidualTotal") == 1560, "FOR-401 selected residual changed")

    selected = actual.get("selectedPixels")
    require(isinstance(selected, list) and len(selected) == 16, "selectedPixels missing")
    for index, pixel in enumerate(selected):
        require(pixel.get("index") == index, f"pixel index changed at {index}")
        require(pixel.get("classification") == "post-pass-already-diverged", f"pixel classification changed at {index}")
        require(pixel.get("postPassObservedRgba8") == pixel.get("currentGpuFinalRgba"), f"post-pass/final mismatch at {index}")
        require(pixel.get("cpuRgba") == pixel.get("skiaRgba"), f"CPU/Skia mismatch at {index}")
        require(pixel.get("for400UsedAsDirectProof") is False, f"FOR-400 used as direct proof at {index}")
        require(
            pixel.get("postPassToSkiaDelta", {}).get("absoluteTotal")
            == pixel.get("currentGpuFinalToSkiaDelta", {}).get("absoluteTotal"),
            f"post-pass/final delta mismatch at {index}",
        )

    non_goals = actual.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "defaultRenderingChanged",
        "supportClaimRaised",
        "promoted",
        "thresholdChanged",
        "scoringChanged",
        "correctionApplied",
        "for380BroadCorrectionReintroduced",
        "for400UsedAsDirectProof",
        "generalizedOutsideM60F16",
    ):
        require(non_goals.get(key) is False, f"non-goal changed: {key}")
    require(actual.get("validationCommands") == EXPECTED_VALIDATION_COMMANDS, "validation command list changed")
    return str(classification)


def validate_report(classification: str) -> None:
    text = read_text(REPORT)
    for needle in (
        "FOR-406 M60 F16 post-pass reference comparison",
        f"Global classification: `{classification}`",
        "Conclusion: `divergence-already-visible-in-post-pass`",
        "FOR-400 remains context only and is not used as direct writer proof.",
        "Post-pass already diverged | `16`",
        "Present/readback diverged only | `0`",
        "Sum delta post-pass -> Skia | `1560`",
        "Sum delta final GPU -> Skia | `1560`",
        "supportClaim=false",
        "promoted=false",
        "correctionAppliedByDefault=false",
        "defaultRenderingChanged=false",
        "rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py",
    ):
        require(needle in text, f"report missing: {needle}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite the FOR-406 artifact and report")
    args = parser.parse_args()

    expected = build_artifact()
    report = build_report(expected)
    if args.write:
        ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
        write_if_changed(ARTIFACT, json.dumps(expected, indent=2, sort_keys=False) + "\n")
        write_if_changed(REPORT, report)

    actual = load_json(ARTIFACT)
    classification = validate_artifact(actual, expected)
    validate_report(classification)
    print(f"FOR-406 validation passed: {classification}")


if __name__ == "__main__":
    main()
