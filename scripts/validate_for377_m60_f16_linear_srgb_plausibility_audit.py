#!/usr/bin/env python3
"""Validate the FOR-377 M60 F16 linear-sRGB plausibility audit evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-377"
SCENE_ID = "m60-f16-linear-srgb-plausibility-audit-for377"
SOURCE_SCENE_ID = "m60-f16-composition-quantization-candidate-for376"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-linear-srgb-plausibility-audit-for377/"
    "m60-f16-linear-srgb-plausibility-audit-for377.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-377-m60-f16-linear-srgb-plausibility-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
FOR376_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-composition-quantization-candidate-for376/"
    "m60-f16-composition-quantization-candidate-for376.json"
)

DECISION = "M60_F16_LINEAR_SRGB_PLAUSIBILITY_AUDIT_RECORDED"
FOR376_REQUIRED_DECISION = "M60_F16_COMPOSITION_QUANTIZATION_CANDIDATE_RECORDED"
FOR376_REQUIRED_CLASSIFICATION = "composition-quantization-candidate-reduces-residual"
LINEAR_VARIANT_ID = "linear_srgb_source_over_effective_destination_nearest_255"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL = 1033
REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL = 794
REQUIRED_FOR376_LINEAR_TOTAL_RESIDUAL = 607
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-audit-realite-linear-s-rgb-apres-for-376"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-376-isole-la-variante-linear-s-rgb-comme-meilleure-piste-m60-f16-apres-destination-effective"
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
ALLOWED_CLASSIFICATIONS = {
    "linear-srgb-plausible-next-axis",
    "linear-srgb-likely-diagnostic-artifact",
    "linear-srgb-mixed-needs-reference-color-evidence",
    "linear-srgb-blocked",
}
VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for377-pycache python3 -m py_compile "
        "scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-377 validation failed: {message}")


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
    out = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def rgb(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 3, f"{label} must be RGB")
    out = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


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


def channel_errors(reference: list[int], value: list[int]) -> dict[str, int]:
    return {
        "r": abs(reference[0] - value[0]),
        "g": abs(reference[1] - value[1]),
        "b": abs(reference[2] - value[2]),
        "a": abs(reference[3] - value[3]),
    }


def channel_improvement(current_error: dict[str, int], linear_error: dict[str, int]) -> dict[str, int]:
    return {channel: current_error[channel] - linear_error[channel] for channel in ("r", "g", "b", "a")}


def sign(value: int) -> int:
    if value > 0:
        return 1
    if value < 0:
        return -1
    return 0


def source_tint_direction(reference: list[int], current: list[int], source: list[int], index: int) -> str:
    source_delta = source[index] - reference[index]
    current_delta = current[index] - reference[index]
    if current_delta == 0:
        return "current-matches-reference"
    if source_delta == 0:
        return "source-matches-reference"
    if sign(source_delta) == sign(current_delta):
        return "current-error-with-source-tint"
    return "current-error-opposes-source-tint"


def sample_coherence(delta_vs_current: int, delta_vs_for375: int) -> str:
    if delta_vs_current < 0 and delta_vs_for375 < 0:
        return "improves-current-and-for375"
    if delta_vs_current < 0 and delta_vs_for375 > 0:
        return "improves-current-but-regresses-for375"
    if delta_vs_current < 0:
        return "improves-current-only"
    if delta_vs_current == 0:
        return "neutral-vs-current"
    return "regresses-vs-current"


def expected_channel_audit(
    sample: dict[str, Any],
    linear_rgba: list[int],
    current_error: dict[str, int],
    linear_error: dict[str, int],
    improvement: dict[str, int],
) -> dict[str, dict[str, Any]]:
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    source = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    inverse = sample.get("inverseDestinationEstimate")
    require(isinstance(inverse, dict), "inverseDestinationEstimate missing")
    rounded = inverse.get("rgbRounded")
    clamped = inverse.get("rgbClampedToSrgb")
    require(isinstance(rounded, list) and len(rounded) == 3, "inverseDestinationEstimate.rgbRounded missing")
    destination = rgb(clamped, "inverseDestinationEstimate.rgbClampedToSrgb")
    audit: dict[str, dict[str, Any]] = {}
    for index, channel in enumerate(("r", "g", "b", "a")):
        destination_rounded = rounded[index] if index < 3 else None
        destination_clamped = destination[index] if index < 3 else None
        clamped_to_limit = index < 3 and destination_rounded != destination_clamped
        audit[channel] = {
            "currentError": current_error[channel],
            "linearSrgbError": linear_error[channel],
            "improvement": improvement[channel],
            "linearSrgbMinusCurrentError": linear_error[channel] - current_error[channel],
            "sourceMinusReference": source[index] - reference[index],
            "currentMinusReference": current[index] - reference[index],
            "linearSrgbMinusReference": linear_rgba[index] - reference[index],
            "sourceTintDirection": source_tint_direction(reference, current, source, index),
            "destinationRounded": destination_rounded,
            "destinationClampedToSrgb": destination_clamped,
            "destinationClampToLimit": clamped_to_limit,
            "destinationClampLimit": destination_clamped if clamped_to_limit else None,
        }
    return audit


def expected_band_coherence(samples: list[dict[str, Any]]) -> list[dict[str, Any]]:
    bands: dict[str, list[dict[str, Any]]] = {}
    for sample in samples:
        bands.setdefault(sample["strokeBand"], []).append(sample["linearSrgbPlausibilityAudit"])
    rows = []
    for stroke_band in sorted(bands):
        audits = bands[stroke_band]
        current = sum(audit["currentResidual"] for audit in audits)
        linear = sum(audit["linearSrgbResidual"] for audit in audits)
        for375 = sum(audit["for375Residual"] for audit in audits)
        delta_current = linear - current
        delta_for375 = linear - for375
        rows.append(
            {
                "strokeBand": stroke_band,
                "sampleCount": len(audits),
                "currentResidual": current,
                "linearSrgbResidual": linear,
                "for375Residual": for375,
                "deltaVsCurrent": delta_current,
                "deltaVsFor375Candidate": delta_for375,
                "improvedVsCurrentSamples": sum(1 for audit in audits if audit["linearSrgbDeltaVsCurrent"] < 0),
                "regressedVsCurrentSamples": sum(1 for audit in audits if audit["linearSrgbDeltaVsCurrent"] > 0),
                "improvedVsFor375Samples": sum(1 for audit in audits if audit["linearSrgbDeltaVsFor375Candidate"] < 0),
                "regressedVsFor375Samples": sum(1 for audit in audits if audit["linearSrgbDeltaVsFor375Candidate"] > 0),
                "coherence": sample_coherence(delta_current, delta_for375),
            }
        )
    return rows


def expected_classification(
    sample_count: int,
    linear_total: int,
    current_total: int,
    clamp_improvement_share: float,
    band_coherence: list[dict[str, Any]],
) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT or linear_total != REQUIRED_FOR376_LINEAR_TOTAL_RESIDUAL:
        return "linear-srgb-blocked"
    if linear_total >= current_total:
        return "linear-srgb-blocked"
    if clamp_improvement_share >= 0.60:
        return "linear-srgb-likely-diagnostic-artifact"
    if all(row["deltaVsCurrent"] < 0 and row["deltaVsFor375Candidate"] < 0 for row in band_coherence) and clamp_improvement_share < 0.50:
        return "linear-srgb-plausible-next-axis"
    return "linear-srgb-mixed-needs-reference-color-evidence"


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
        "private fun m60F16LinearSrgbPlausibilityAuditJson",
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
    require("WebGpuSink.draw" not in producer_block, "FOR-377 producer reads from renderer")
    audit_block = function_block(
        source,
        "private fun linearSrgbPlausibilityAuditSample",
        "private data class InverseDestinationEstimate",
    )
    require("WebGpuSink.draw" not in audit_block, "FOR-377 audit helper reads from renderer")
    require("getPixel" not in audit_block, "FOR-377 audit helper reads image pixels")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16LinearSrgbPlausibilityAudit(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16LinearSrgbPlausibilityAudit"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16LinearSrgbPlausibilityAuditJson"),
        "sampleLine": find_line(CAPTURE_PRODUCER, "private fun linearSrgbPlausibilityAuditSample"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun linearSrgbPlausibilityClassification"),
    }


def validate_for376() -> dict[str, Any]:
    data = load_json(FOR376_ARTIFACT)
    require(data.get("linear") == "FOR-376", "FOR-376 identity changed")
    require(data.get("decision") == FOR376_REQUIRED_DECISION, "FOR-376 decision changed")
    require(data.get("classification") == FOR376_REQUIRED_CLASSIFICATION, "FOR-376 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-376 current residual changed")
    require(data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-376 FOR-373 total changed")
    require(
        data.get("for375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-376 FOR-375 total changed",
    )
    require(data.get("bestVariantId") == LINEAR_VARIANT_ID, "FOR-376 best variant changed")
    require(data.get("bestVariantTotalResidual") == REQUIRED_FOR376_LINEAR_TOTAL_RESIDUAL, "FOR-376 best total changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-376 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-376 coordinate changed")
    return data


def validate_sample(sample: dict[str, Any], for376_sample: dict[str, Any], index: int) -> dict[str, Any]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    preserved = dict(sample)
    audit = preserved.pop("linearSrgbPlausibilityAudit", None)
    require(preserved == for376_sample, f"FOR-376 preserved sample fields diverged at sample {index}")
    require(isinstance(audit, dict), f"FOR-377 audit block missing at sample {index}")

    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    source = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    inverse = sample.get("inverseDestinationEstimate")
    require(isinstance(inverse, dict), "inverseDestinationEstimate missing")
    destination = rgb(inverse.get("rgbClampedToSrgb"), "inverseDestinationEstimate.rgbClampedToSrgb")
    effective_alpha_byte = sample.get("effectiveSourceAlphaByte")
    require(isinstance(effective_alpha_byte, int), "effectiveSourceAlphaByte missing")
    recalculated_linear = linear_srgb_nearest_255(source, effective_alpha_byte, destination)
    variant = next(
        (
            row
            for row in sample.get("compositionQuantizationVariants", [])
            if isinstance(row, dict) and row.get("variantId") == LINEAR_VARIANT_ID
        ),
        None,
    )
    require(isinstance(variant, dict), f"linear variant missing at sample {index}")
    require(variant.get("candidateRgba") == recalculated_linear, f"linear variant RGBA mismatch at sample {index}")
    residual = sample_residual(reference, recalculated_linear)
    require(variant.get("residual") == residual, f"linear variant residual mismatch at sample {index}")
    require(audit.get("variantId") == LINEAR_VARIANT_ID, f"audit variant id mismatch at sample {index}")
    require(audit.get("currentResidual") == sample["currentResidual"], f"audit current residual mismatch at sample {index}")
    require(audit.get("linearSrgbResidual") == residual, f"audit linear residual mismatch at sample {index}")
    require(audit.get("for375Residual") == sample["effectiveDestinationCandidateResidual"], f"audit FOR-375 residual mismatch at sample {index}")
    require(audit.get("for373Residual") == sample["candidateResidual"], f"audit FOR-373 residual mismatch at sample {index}")
    require(audit.get("linearSrgbDeltaVsCurrent") == residual - sample["currentResidual"], f"audit delta current mismatch at sample {index}")
    require(
        audit.get("linearSrgbDeltaVsFor375Candidate") == residual - sample["effectiveDestinationCandidateResidual"],
        f"audit delta FOR-375 mismatch at sample {index}",
    )
    require(
        audit.get("linearSrgbDeltaVsFor373Candidate") == residual - sample["candidateResidual"],
        f"audit delta FOR-373 mismatch at sample {index}",
    )

    current_error = channel_errors(reference, current)
    linear_error = channel_errors(reference, recalculated_linear)
    improvement = channel_improvement(current_error, linear_error)
    require(audit.get("currentErrorByChannel") == current_error, f"current channel error mismatch at sample {index}")
    require(audit.get("linearSrgbErrorByChannel") == linear_error, f"linear channel error mismatch at sample {index}")
    require(audit.get("linearSrgbImprovementByChannel") == improvement, f"improvement channel mismatch at sample {index}")
    expected_channels = expected_channel_audit(sample, recalculated_linear, current_error, linear_error, improvement)
    require(audit.get("channelAudit") == expected_channels, f"channel audit mismatch at sample {index}")
    clamp_count = sum(1 for channel in expected_channels.values() if channel["destinationClampToLimit"])
    clamp_positive = sum(
        channel["improvement"]
        for channel in expected_channels.values()
        if channel["destinationClampToLimit"] and channel["improvement"] > 0
    )
    require(audit.get("destinationClampChannelCount") == clamp_count, f"clamp count mismatch at sample {index}")
    require(audit.get("destinationClampPositiveImprovement") == clamp_positive, f"clamp improvement mismatch at sample {index}")
    require(
        audit.get("sampleCoherence") == sample_coherence(residual - sample["currentResidual"], residual - sample["effectiveDestinationCandidateResidual"]),
        f"sample coherence mismatch at sample {index}",
    )
    return audit


def validate_artifact(data: dict[str, Any], for376: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor376Decision") == FOR376_REQUIRED_DECISION, "FOR-376 decision gate changed")
    require(data.get("requiredFor376Classification") == FOR376_REQUIRED_CLASSIFICATION, "FOR-376 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("auditedVariantId") == LINEAR_VARIANT_ID, "audited variant changed")
    for guard in (
        "destinationReadFromRenderer",
        "destinationReadFromGpuImage",
        "destinationAppliedToRenderer",
        "variantReadFromRenderer",
        "variantReadFromGpuImage",
        "variantAppliedToRenderer",
    ):
        require(data.get(guard) is False, f"guard changed: {guard}")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "current residual changed")
    require(data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 total changed")
    require(data.get("for375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL, "FOR-375 total changed")
    require(data.get("requiredFor376BestVariantId") == LINEAR_VARIANT_ID, "required FOR-376 best variant changed")
    require(data.get("requiredFor376BestVariantTotalResidual") == REQUIRED_FOR376_LINEAR_TOTAL_RESIDUAL, "required FOR-376 best residual changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    samples = data.get("samples")
    for376_samples = for376.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for376_samples, list) and len(for376_samples) == REQUIRED_SAMPLE_COUNT, "FOR-376 samples missing")
    audits = [validate_sample(sample, for376_samples[index - 1], index) for index, sample in enumerate(samples, start=1)]
    current_total = sum(audit["currentResidual"] for audit in audits)
    for373_total = sum(audit["for373Residual"] for audit in audits)
    for375_total = sum(audit["for375Residual"] for audit in audits)
    linear_total = sum(audit["linearSrgbResidual"] for audit in audits)
    require(current_total == REQUIRED_CURRENT_RESIDUAL, "current recomputation changed")
    require(for373_total == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 recomputation changed")
    require(for375_total == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL, "FOR-375 recomputation changed")
    require(linear_total == REQUIRED_FOR376_LINEAR_TOTAL_RESIDUAL, "linear total recomputation changed")
    require(data.get("linearSrgbTotalResidual") == linear_total, "linear total field mismatch")
    require(data.get("linearSrgbTotalDeltaVsCurrent") == linear_total - current_total, "linear delta current mismatch")
    require(data.get("linearSrgbTotalDeltaVsFor375Candidate") == linear_total - for375_total, "linear delta FOR-375 mismatch")
    require(data.get("linearSrgbTotalDeltaVsFor373Candidate") == linear_total - for373_total, "linear delta FOR-373 mismatch")
    require(data.get("improvedVsCurrentSampleCount") == sum(1 for audit in audits if audit["linearSrgbDeltaVsCurrent"] < 0), "improved current count mismatch")
    require(data.get("regressedVsCurrentSampleCount") == sum(1 for audit in audits if audit["linearSrgbDeltaVsCurrent"] > 0), "regressed current count mismatch")
    require(data.get("improvedVsFor375SampleCount") == sum(1 for audit in audits if audit["linearSrgbDeltaVsFor375Candidate"] < 0), "improved FOR-375 count mismatch")
    require(data.get("regressedVsFor375SampleCount") == sum(1 for audit in audits if audit["linearSrgbDeltaVsFor375Candidate"] > 0), "regressed FOR-375 count mismatch")
    clamp_count = sum(audit["destinationClampChannelCount"] for audit in audits)
    clamp_positive = sum(audit["destinationClampPositiveImprovement"] for audit in audits)
    total_improvement = current_total - linear_total
    clamp_share = clamp_positive / total_improvement
    require(data.get("destinationClampChannelCount") == clamp_count, "top-level clamp count mismatch")
    require(data.get("destinationClampPositiveImprovement") == clamp_positive, "top-level clamp improvement mismatch")
    require(data.get("linearSrgbPositiveImprovement") == total_improvement, "positive improvement mismatch")
    require(abs(float(data.get("destinationClampPositiveImprovementShare")) - clamp_share) < 0.000001, "clamp share mismatch")

    band_rows = expected_band_coherence(samples)
    require(data.get("bandCoherence") == band_rows, "band coherence mismatch")
    require(data.get("classification") == expected_classification(len(samples), linear_total, current_total, clamp_share, band_rows), "classification rule mismatch")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    band_rows = "\n".join(
        "| `{band}` | {count} | {current} | {linear} | {for375} | {delta_current} | {delta_for375} | `{coherence}` |".format(
            band=row["strokeBand"],
            count=row["sampleCount"],
            current=row["currentResidual"],
            linear=row["linearSrgbResidual"],
            for375=row["for375Residual"],
            delta_current=row["deltaVsCurrent"],
            delta_for375=row["deltaVsFor375Candidate"],
            coherence=row["coherence"],
        )
        for row in data["bandCoherence"]
    )
    sample_rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | {current} | {linear} | {for375} | {delta_current} | {delta_for375} | {clamps} | {clamp_gain} | `{coherence}` |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            current=sample["linearSrgbPlausibilityAudit"]["currentResidual"],
            linear=sample["linearSrgbPlausibilityAudit"]["linearSrgbResidual"],
            for375=sample["linearSrgbPlausibilityAudit"]["for375Residual"],
            delta_current=sample["linearSrgbPlausibilityAudit"]["linearSrgbDeltaVsCurrent"],
            delta_for375=sample["linearSrgbPlausibilityAudit"]["linearSrgbDeltaVsFor375Candidate"],
            clamps=sample["linearSrgbPlausibilityAudit"]["destinationClampChannelCount"],
            clamp_gain=sample["linearSrgbPlausibilityAudit"]["destinationClampPositiveImprovement"],
            coherence=sample["linearSrgbPlausibilityAudit"]["sampleCoherence"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-377 Linear-sRGB plausibility audit M60 F16

Linear: `FOR-377`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-377 audits the FOR-376 diagnostic variant
`linear_srgb_source_over_effective_destination_nearest_255` without changing
renderer/runtime behavior. The audit preserves the ten FOR-376 samples exactly
and appends a per-sample `linearSrgbPlausibilityAudit` block.

## Result

- Current residual: `{data['currentResidual']}`
- FOR-375 effective destination residual: `{data['for375EffectiveDestinationCandidateTotalResidual']}`
- Linear-sRGB residual: `{data['linearSrgbTotalResidual']}`
- Delta versus current: `{data['linearSrgbTotalDeltaVsCurrent']}`
- Delta versus FOR-375: `{data['linearSrgbTotalDeltaVsFor375Candidate']}`
- Destination clamp positive improvement: `{data['destinationClampPositiveImprovement']}` / `{data['linearSrgbPositiveImprovement']}` (`{data['destinationClampPositiveImprovementShare']}`)
- Reason: {data['classificationReason']}

## Band Coherence

| band | samples | current | linear-sRGB | FOR-375 | delta current | delta FOR-375 | coherence |
|---|---:|---:|---:|---:|---:|---:|---|
{band_rows}

## Producer

- Writer call: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-377: `{producer_path}:{producer_lines['writerLine']}`
- JSON producer: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Sample audit: `{producer_path}:{producer_lines['sampleLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`

## Samples

| # | x | y | band | current residual | linear-sRGB residual | FOR-375 residual | delta current | delta FOR-375 | clamp channels | clamp gain | coherence |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
{sample_rows}

## Validation Commands

{commands}
"""


def main() -> None:
    producer_lines = validate_producer_source()
    for376 = validate_for376()
    data = load_json(ARTIFACT)
    validate_artifact(data, for376)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(
        "FOR-377 validation passed: "
        f"classification={data['classification']} linearResidual={data['linearSrgbTotalResidual']} "
        f"deltaVsCurrent={data['linearSrgbTotalDeltaVsCurrent']}"
    )


if __name__ == "__main__":
    main()
