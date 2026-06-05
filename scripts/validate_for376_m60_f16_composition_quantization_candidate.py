#!/usr/bin/env python3
"""Validate the FOR-376 M60 F16 composition/quantization candidate evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-376"
SCENE_ID = "m60-f16-composition-quantization-candidate-for376"
SOURCE_SCENE_ID = "m60-f16-effective-destination-candidate-for375"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-quantization-candidate-for376/"
    "m60-f16-composition-quantization-candidate-for376.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-376-m60-f16-composition-quantization-candidate.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
FOR375_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-destination-candidate-for375/"
    "m60-f16-effective-destination-candidate-for375.json"
)

DECISION = "M60_F16_COMPOSITION_QUANTIZATION_CANDIDATE_RECORDED"
FOR375_REQUIRED_DECISION = "M60_F16_EFFECTIVE_DESTINATION_CANDIDATE_RECORDED"
FOR375_REQUIRED_CLASSIFICATION = "effective-destination-candidate-reduces-residual"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL = 1033
REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL = 794
REQUIRED_BEST_VARIANT_ID = "linear_srgb_source_over_effective_destination_nearest_255"
REQUIRED_BEST_VARIANT_TOTAL_RESIDUAL = 607
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-axe-espace-de-composition-et-quantification-apres-for-375"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-375-reduit-le-residuel-m60-f16-avec-une-candidate-sur-destination-effective-diagnostique"
)
EXPECTED_COORDINATES = [
    (92, 75),
    (91, 76),
    (90, 77),
    (89, 78),
    (88, 79),
    (87, 80),
    (21, 81),
    (93, 74),
    (17, 77),
    (69, 81),
]
EXPECTED_VARIANT_IDS = [
    "source_over_effective_destination_floor_256",
    "straight_srgb_source_over_effective_destination_nearest_255",
    "linear_srgb_source_over_effective_destination_nearest_255",
    "premultiplied_srgb_terms_floor_256_source_over_effective_destination",
]
ALLOWED_CLASSIFICATIONS = {
    "composition-quantization-candidate-explains-reference",
    "composition-quantization-candidate-reduces-residual",
    "composition-quantization-candidate-neutral",
    "composition-quantization-candidate-regresses",
    "composition-quantization-candidate-blocked",
}
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
    "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
    "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
    "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for376-pycache python3 -m py_compile "
        "scripts/validate_for376_m60_f16_composition_quantization_candidate.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-376 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def write_if_changed(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == text:
        return
    path.write_text(text, encoding="utf-8")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def rgb(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 3, f"{label} must be RGB")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def quantize_256(value: float) -> int:
    if math.isnan(value):
        return 0
    return max(0, min(255, int(value * 256.0)))


def quantize_nearest_255(value: float) -> int:
    if math.isnan(value):
        return 0
    return max(0, min(255, int(value * 255.0 + 0.5)))


def linear_from_srgb_byte(value: int) -> float:
    srgb = value / 255.0
    if srgb <= 0.04045:
        return srgb / 12.92
    return ((srgb + 0.055) / 1.055) ** 2.4


def srgb_from_linear(value: float) -> float:
    value = max(0.0, min(1.0, value))
    if value <= 0.0031308:
        return 12.92 * value
    return 1.055 * (value ** (1.0 / 2.4)) - 0.055


def source_over_floor_256(source: list[int], effective_alpha_byte: int, destination_rgb: list[int]) -> list[int]:
    alpha = effective_alpha_byte / 255.0
    return [
        quantize_256((source[index] / 255.0) * alpha + (destination_rgb[index] / 255.0) * (1.0 - alpha))
        for index in range(3)
    ] + [255]


def source_over_nearest_255(source: list[int], effective_alpha_byte: int, destination_rgb: list[int]) -> list[int]:
    alpha = effective_alpha_byte / 255.0
    return [
        quantize_nearest_255(
            (source[index] / 255.0) * alpha + (destination_rgb[index] / 255.0) * (1.0 - alpha)
        )
        for index in range(3)
    ] + [255]


def linear_srgb_nearest_255(source: list[int], effective_alpha_byte: int, destination_rgb: list[int]) -> list[int]:
    alpha = effective_alpha_byte / 255.0
    out = []
    for index in range(3):
        linear = (
            linear_from_srgb_byte(source[index]) * alpha
            + linear_from_srgb_byte(destination_rgb[index]) * (1.0 - alpha)
        )
        out.append(quantize_nearest_255(srgb_from_linear(linear)))
    return out + [255]


def premultiplied_terms_floor_256(
    source: list[int],
    effective_alpha_byte: int,
    destination_rgb: list[int],
) -> list[int]:
    alpha = effective_alpha_byte / 255.0
    return [
        max(
            0,
            min(
                255,
                quantize_256((source[index] / 255.0) * alpha)
                + quantize_256((destination_rgb[index] / 255.0) * (1.0 - alpha)),
            ),
        )
        for index in range(3)
    ] + [255]


def expected_variant_rgba(variant_id: str, sample: dict[str, Any]) -> list[int]:
    source = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    effective_alpha_byte = sample.get("effectiveSourceAlphaByte")
    require(isinstance(effective_alpha_byte, int), "effectiveSourceAlphaByte missing")
    inverse = sample.get("inverseDestinationEstimate")
    require(isinstance(inverse, dict), "inverseDestinationEstimate missing")
    destination_rgb = rgb(inverse.get("rgbClampedToSrgb"), "inverseDestinationEstimate.rgbClampedToSrgb")
    if variant_id == "source_over_effective_destination_floor_256":
        return source_over_floor_256(source, effective_alpha_byte, destination_rgb)
    if variant_id == "straight_srgb_source_over_effective_destination_nearest_255":
        return source_over_nearest_255(source, effective_alpha_byte, destination_rgb)
    if variant_id == "linear_srgb_source_over_effective_destination_nearest_255":
        return linear_srgb_nearest_255(source, effective_alpha_byte, destination_rgb)
    if variant_id == "premultiplied_srgb_terms_floor_256_source_over_effective_destination":
        return premultiplied_terms_floor_256(source, effective_alpha_byte, destination_rgb)
    fail(f"unknown variant id: {variant_id}")


def expected_classification(sample_count: int, blocked_variant_count: int, best_total: int, current: int, for375: int) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT or blocked_variant_count > 0:
        return "composition-quantization-candidate-blocked"
    if best_total <= 64:
        return "composition-quantization-candidate-explains-reference"
    if best_total < for375 or best_total < current:
        return "composition-quantization-candidate-reduces-residual"
    if best_total == for375:
        return "composition-quantization-candidate-neutral"
    return "composition-quantization-candidate-regresses"


def find_line(path: Path, needle: str) -> int:
    source = path.read_text(encoding="utf-8")
    for index, line in enumerate(source.splitlines(), start=1):
        if needle in line:
            return index
    fail(f"{rel(path)} no longer contains: {needle}")


def function_block(source: str, start_needle: str, next_needle: str) -> str:
    start = source.find(start_needle)
    require(start >= 0, f"source missing block start: {start_needle}")
    end = source.find(next_needle, start + len(start_needle))
    require(end > start, f"source missing block end: {next_needle}")
    return source[start:end]


def validate_producer_source() -> dict[str, int]:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    producer_block = function_block(
        source,
        "private fun m60F16CompositionQuantizationCandidateJson",
        "private fun candidatePolicySample",
    )
    for needle in (
        '"variantReadFromRenderer": false',
        '"variantReadFromGpuImage": false',
        '"variantAppliedToRenderer": false',
        '"destinationReadFromRenderer": false',
        '"destinationReadFromGpuImage": false',
        '"destinationAppliedToRenderer": false',
    ):
        require(needle in producer_block, f"producer guard missing: {needle}")
    require("WebGpuSink.draw" not in producer_block, "FOR-376 producer reads from renderer")
    variant_block = function_block(
        source,
        "private fun compositionQuantizationVariantDefinitions",
        "private fun straightSrgbSourceOverEffectiveDestinationNearestRgba",
    )
    for variant_id in EXPECTED_VARIANT_IDS:
        require(variant_id in variant_block, f"variant definition missing: {variant_id}")
    formula_block = function_block(
        source,
        "private fun straightSrgbSourceOverEffectiveDestinationNearestRgba",
        "private fun quantizeAlphaRound",
    )
    require("WebGpuSink.draw" not in formula_block, "variant formula reads renderer")
    require("getPixel" not in formula_block, "variant formula reads image pixels")
    require("destinationRgb" in formula_block, "variant formula does not use destinationRgb")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16CompositionQuantizationCandidate(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16CompositionQuantizationCandidate"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16CompositionQuantizationCandidateJson"),
        "sampleLine": find_line(CAPTURE_PRODUCER, "private fun compositionQuantizationCandidateSample"),
        "definitionLine": find_line(CAPTURE_PRODUCER, "private fun compositionQuantizationVariantDefinitions"),
        "linearFormulaLine": find_line(CAPTURE_PRODUCER, "private fun linearSrgbSourceOverEffectiveDestinationNearestRgba"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun compositionQuantizationCandidateClassification"),
    }


def validate_for375() -> dict[str, Any]:
    data = load_json(FOR375_ARTIFACT)
    require(data.get("linear") == "FOR-375", "FOR-375 identity changed")
    require(data.get("decision") == FOR375_REQUIRED_DECISION, "FOR-375 decision changed")
    require(data.get("classification") == FOR375_REQUIRED_CLASSIFICATION, "FOR-375 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-375 current residual changed")
    require(
        data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-375 FOR-373 total changed",
    )
    require(
        data.get("effectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-375 effective destination total changed",
    )
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-375 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-375 coords changed")
        require(sample.get("effectiveDestinationInputSource") == "inverseDestinationEstimate.rgbClampedToSrgb", "FOR-375 destination source changed")
        require(sample.get("effectiveDestinationCandidateReadFromRenderer") is False, "FOR-375 candidate read renderer")
        require(sample.get("effectiveDestinationCandidateReadFromGpuImage") is False, "FOR-375 candidate read GPU image")
        require(sample.get("effectiveDestinationCandidateAppliedToRenderer") is False, "FOR-375 candidate applied")
        require(sample.get("effectiveDestinationReadFromRenderer") is False, "FOR-375 destination read renderer")
        require(sample.get("effectiveDestinationReadFromGpuImage") is False, "FOR-375 destination read GPU image")
        require(sample.get("effectiveDestinationAppliedToRenderer") is False, "FOR-375 destination applied")
    return data


def validate_variant_definitions(data: dict[str, Any]) -> None:
    definitions = data.get("variantDefinitions")
    require(isinstance(definitions, list), "variantDefinitions missing")
    require([definition.get("variantId") for definition in definitions] == EXPECTED_VARIANT_IDS, "variant definitions order changed")
    for definition in definitions:
        variant_id = definition.get("variantId")
        for key in ("formula", "quantizationOrder", "compositionSpace", "alphaSource", "destinationSource"):
            require(isinstance(definition.get(key), str) and definition[key], f"{variant_id} missing {key}")
        require(definition.get("alphaSource") == "effectiveSourceAlphaByte/255.0", f"{variant_id} alpha source changed")
        require(
            definition.get("destinationSource") == "inverseDestinationEstimate.rgbClampedToSrgb",
            f"{variant_id} destination source changed",
        )
        require(definition.get("candidateSource") == "calculated-by-diagnostic-policy", f"{variant_id} source changed")
        require(definition.get("candidateReadFromRenderer") is False, f"{variant_id} reads renderer")
        require(definition.get("candidateReadFromGpuImage") is False, f"{variant_id} reads GPU image")
        require(definition.get("candidateAppliedToRenderer") is False, f"{variant_id} applied to renderer")
    linear = next(definition for definition in definitions if definition["variantId"] == REQUIRED_BEST_VARIANT_ID)
    formula = linear.get("formula", "")
    for needle in ("0.04045", "12.92", "2.4", "0.0031308", "round"):
        require(needle in formula, f"linear variant formula missing {needle}")


def validate_sample(sample: dict[str, Any], for375_sample: dict[str, Any], index: int) -> dict[str, int]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    for key, value in for375_sample.items():
        require(sample.get(key) == value, f"FOR-375 field {key} diverged at sample {index}")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    variants = sample.get("compositionQuantizationVariants")
    require(isinstance(variants, list), "compositionQuantizationVariants missing")
    require([variant.get("variantId") for variant in variants] == EXPECTED_VARIANT_IDS, f"variant order changed at sample {index}")

    totals: dict[str, int] = {}
    best_variant_id = None
    best_residual = None
    for variant in variants:
        variant_id = variant["variantId"]
        expected = expected_variant_rgba(variant_id, sample)
        candidate = rgba(variant.get("candidateRgba"), f"{variant_id}.candidateRgba")
        require(candidate == expected, f"{variant_id} candidate mismatch at sample {index}")
        residual = sample_residual(reference, candidate)
        require(variant.get("residual") == residual, f"{variant_id} residual mismatch at sample {index}")
        require(variant.get("deltaVsCurrent") == residual - sample["currentResidual"], f"{variant_id} delta vs current mismatch")
        require(
            variant.get("deltaVsFor375Candidate") == residual - sample["effectiveDestinationCandidateResidual"],
            f"{variant_id} delta vs FOR-375 mismatch",
        )
        require(
            variant.get("deltaVsFor373Candidate") == residual - sample["candidateResidual"],
            f"{variant_id} delta vs FOR-373 mismatch",
        )
        require(variant.get("improvesCurrent") == (residual < sample["currentResidual"]), f"{variant_id} improves current mismatch")
        require(
            variant.get("improvesFor375Candidate") == (residual < sample["effectiveDestinationCandidateResidual"]),
            f"{variant_id} improves FOR-375 mismatch",
        )
        require(variant.get("improvesFor373Candidate") == (residual < sample["candidateResidual"]), f"{variant_id} improves FOR-373 mismatch")
        require(variant.get("candidateSource") == "calculated-by-diagnostic-policy", f"{variant_id} source changed")
        require(variant.get("candidateReadFromRenderer") is False, f"{variant_id} reads renderer")
        require(variant.get("candidateReadFromGpuImage") is False, f"{variant_id} reads GPU image")
        require(variant.get("candidateAppliedToRenderer") is False, f"{variant_id} applied to renderer")
        totals[variant_id] = residual
        if best_residual is None or (residual, variant_id) < (best_residual, best_variant_id or ""):
            best_residual = residual
            best_variant_id = variant_id

    require(sample.get("compositionQuantizationBestVariantId") == best_variant_id, "sample best variant id mismatch")
    require(sample.get("compositionQuantizationBestVariantResidual") == best_residual, "sample best residual mismatch")
    require(
        sample.get("compositionQuantizationBestVariantDeltaVsCurrent") == best_residual - sample["currentResidual"],
        "sample best delta vs current mismatch",
    )
    require(
        sample.get("compositionQuantizationBestVariantDeltaVsFor375Candidate")
        == best_residual - sample["effectiveDestinationCandidateResidual"],
        "sample best delta vs FOR-375 mismatch",
    )
    require(
        sample.get("compositionQuantizationBestVariantDeltaVsFor373Candidate") == best_residual - sample["candidateResidual"],
        "sample best delta vs FOR-373 mismatch",
    )
    return totals


def validate_artifact(data: dict[str, Any], for375: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor375Decision") == FOR375_REQUIRED_DECISION, "FOR-375 decision gate changed")
    require(data.get("requiredFor375Classification") == FOR375_REQUIRED_CLASSIFICATION, "FOR-375 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("candidateInputSource") == "FOR-375 preserved samples plus inverseDestinationEstimate.rgbClampedToSrgb", "input source changed")
    require(data.get("destinationSource") == "inverseDestinationEstimate.rgbClampedToSrgb", "destination source changed")
    require(data.get("destinationReadFromRenderer") is False, "destination read renderer")
    require(data.get("destinationReadFromGpuImage") is False, "destination read GPU image")
    require(data.get("destinationAppliedToRenderer") is False, "destination applied")
    require(data.get("variantRgbaSource") == "calculated-by-diagnostic-policy", "variant source changed")
    require(data.get("variantReadFromRenderer") is False, "variant read renderer")
    require(data.get("variantReadFromGpuImage") is False, "variant read GPU image")
    require(data.get("variantAppliedToRenderer") is False, "variant applied")
    require(data.get("variantCount") == len(EXPECTED_VARIANT_IDS), "variant count changed")
    require(data.get("requiredCurrentResidual") == REQUIRED_CURRENT_RESIDUAL, "required current residual changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "current residual changed")
    require(
        data.get("requiredFor373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL,
        "required FOR-373 total changed",
    )
    require(data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 total changed")
    require(
        data.get("requiredFor375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL,
        "required FOR-375 total changed",
    )
    require(
        data.get("for375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-375 total changed",
    )
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    validate_variant_definitions(data)

    samples = data.get("samples")
    for375_samples = for375.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for375_samples, list) and len(for375_samples) == REQUIRED_SAMPLE_COUNT, "FOR-375 samples missing")

    totals = {variant_id: 0 for variant_id in EXPECTED_VARIANT_IDS}
    current_total = 0
    for373_total = 0
    for375_total = 0
    blocked_variant_count = 0
    for index, sample in enumerate(samples, start=1):
        sample_totals = validate_sample(sample, for375_samples[index - 1], index)
        current_total += sample["currentResidual"]
        for373_total += sample["candidateResidual"]
        for375_total += sample["effectiveDestinationCandidateResidual"]
        for variant_id, residual in sample_totals.items():
            totals[variant_id] += residual
        blocked_variant_count += sum(
            1
            for variant in sample["compositionQuantizationVariants"]
            if variant.get("candidateRgba") is None
        )

    require(current_total == REQUIRED_CURRENT_RESIDUAL, "current recomputation changed")
    require(for373_total == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 recomputation changed")
    require(for375_total == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL, "FOR-375 recomputation changed")

    expected_ranking = sorted(totals.items(), key=lambda item: (item[1], item[0]))
    ranking = data.get("variantRanking")
    require(isinstance(ranking, list) and len(ranking) == len(EXPECTED_VARIANT_IDS), "variant ranking missing")
    for rank, (variant_id, total) in enumerate(expected_ranking, start=1):
        row = ranking[rank - 1]
        require(row.get("rank") == rank, "ranking rank mismatch")
        require(row.get("variantId") == variant_id, "ranking variant mismatch")
        require(row.get("totalResidual") == total, f"{variant_id} total mismatch")
        require(row.get("totalDeltaVsCurrent") == total - current_total, f"{variant_id} delta vs current mismatch")
        require(row.get("totalDeltaVsFor375Candidate") == total - for375_total, f"{variant_id} delta vs FOR-375 mismatch")
        require(row.get("totalDeltaVsFor373Candidate") == total - for373_total, f"{variant_id} delta vs FOR-373 mismatch")
        require(row.get("improvesCurrent") == (total < current_total), f"{variant_id} improves current mismatch")
        require(row.get("improvesFor375Candidate") == (total < for375_total), f"{variant_id} improves FOR-375 mismatch")
        require(row.get("improvesFor373Candidate") == (total < for373_total), f"{variant_id} improves FOR-373 mismatch")

    best_variant_id, best_total = expected_ranking[0]
    require(best_variant_id == REQUIRED_BEST_VARIANT_ID, "best variant id changed")
    require(best_total == REQUIRED_BEST_VARIANT_TOTAL_RESIDUAL, "best total changed")
    require(data.get("bestVariantId") == best_variant_id, "best variant field changed")
    require(data.get("bestVariantTotalResidual") == best_total, "best residual field changed")
    require(data.get("bestVariantTotalDeltaVsCurrent") == best_total - current_total, "best delta vs current changed")
    require(data.get("bestVariantTotalDeltaVsFor375Candidate") == best_total - for375_total, "best delta vs FOR-375 changed")
    require(data.get("bestVariantTotalDeltaVsFor373Candidate") == best_total - for373_total, "best delta vs FOR-373 changed")
    require(
        data.get("classification") == expected_classification(len(samples), blocked_variant_count, best_total, current_total, for375_total),
        "classification rule mismatch",
    )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    ranking_rows = "\n".join(
        "| {rank} | `{variant}` | {residual} | {delta_current} | {delta_for375} | {delta_for373} |".format(
            rank=row["rank"],
            variant=row["variantId"],
            residual=row["totalResidual"],
            delta_current=row["totalDeltaVsCurrent"],
            delta_for375=row["totalDeltaVsFor375Candidate"],
            delta_for373=row["totalDeltaVsFor373Candidate"],
        )
        for row in data["variantRanking"]
    )
    sample_rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{for375}` | `{best}` | `{best_rgba}` | {current_residual} | {for375_residual} | {best_residual} | {delta_current} | {delta_for375} |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            for375=sample["effectiveDestinationCandidateRgba"],
            best=sample["compositionQuantizationBestVariantId"],
            best_rgba=next(
                variant["candidateRgba"]
                for variant in sample["compositionQuantizationVariants"]
                if variant["variantId"] == sample["compositionQuantizationBestVariantId"]
            ),
            current_residual=sample["currentResidual"],
            for375_residual=sample["effectiveDestinationCandidateResidual"],
            best_residual=sample["compositionQuantizationBestVariantResidual"],
            delta_current=sample["compositionQuantizationBestVariantDeltaVsCurrent"],
            delta_for375=sample["compositionQuantizationBestVariantDeltaVsFor375Candidate"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-376 Composition/quantization candidate M60 F16

Linear: `FOR-376`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-376 teste uniquement l'axe espace de composition et quantification autour
de la destination effective FOR-375. Les variantes sont calculees comme preuves
diagnostiques depuis les samples FOR-375 preserves; elles ne sont pas appliquees
au renderer/runtime.

## Resultat

- Residuel courant preserve: `{data['currentResidual']}`
- Residuel candidate FOR-373 preserve: `{data['for373CandidateTotalResidual']}`
- Residuel candidate FOR-375 preserve: `{data['for375EffectiveDestinationCandidateTotalResidual']}`
- Meilleure variante: `{data['bestVariantId']}`
- Residuel meilleure variante: `{data['bestVariantTotalResidual']}`
- Delta versus courant: `{data['bestVariantTotalDeltaVsCurrent']}`
- Delta versus FOR-375: `{data['bestVariantTotalDeltaVsFor375Candidate']}`
- Delta versus FOR-373: `{data['bestVariantTotalDeltaVsFor373Candidate']}`

## Classement

| rang | variante | residuel total | delta courant | delta FOR-375 | delta FOR-373 |
|---:|---|---:|---:|---:|---:|
{ranking_rows}

## Producteur

- Appel writer: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-376: `{producer_path}:{producer_lines['writerLine']}`
- Producteur JSON: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Sample diagnostique: `{producer_path}:{producer_lines['sampleLine']}`
- Definitions variantes: `{producer_path}:{producer_lines['definitionLine']}`
- Formule linear-sRGB: `{producer_path}:{producer_lines['linearFormulaLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate FOR-375 | meilleure variante | candidate meilleure variante | residuel courant | residuel FOR-375 | residuel meilleure | delta courant | delta FOR-375 |
|---|---:|---:|---|---|---|---|---|---|---:|---:|---:|---:|---:|
{sample_rows}

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend runtime, score, seuil ou promotion.
- Les variantes restent des preuves diagnostiques calculees, jamais lues depuis le renderer ou appliquees.

## Validations

{commands}
"""


def main() -> int:
    for375 = validate_for375()
    producer_lines = validate_producer_source()
    data = load_json(ARTIFACT)
    validate_artifact(data, for375)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(f"FOR-376 composition/quantization candidate validated: {rel(ARTIFACT)}")
    print(f"FOR-376 report validated: {rel(REPORT)}")
    print(f"decision={data['decision']}")
    print(f"classification={data['classification']}")
    print(f"bestVariantId={data['bestVariantId']}")
    print(f"bestVariantTotalResidual={data['bestVariantTotalResidual']}")
    print(f"bestVariantTotalDeltaVsCurrent={data['bestVariantTotalDeltaVsCurrent']}")
    print(f"bestVariantTotalDeltaVsFor375Candidate={data['bestVariantTotalDeltaVsFor375Candidate']}")
    print(f"bestVariantTotalDeltaVsFor373Candidate={data['bestVariantTotalDeltaVsFor373Candidate']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
