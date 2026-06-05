#!/usr/bin/env python3
"""Validate the FOR-378 M60 F16 direct source color evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-378"
SCENE_ID = "m60-f16-direct-source-color-evidence-for378"
SOURCE_SCENE_ID = "m60-f16-linear-srgb-plausibility-audit-for377"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-source-color-evidence-for378/"
    "m60-f16-direct-source-color-evidence-for378.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-378-m60-f16-direct-source-color-evidence.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
FOR377_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-linear-srgb-plausibility-audit-for377/"
    "m60-f16-linear-srgb-plausibility-audit-for377.json"
)

DECISION = "M60_F16_DIRECT_SOURCE_COLOR_EVIDENCE_RECORDED"
FOR377_REQUIRED_DECISION = "M60_F16_LINEAR_SRGB_PLAUSIBILITY_AUDIT_RECORDED"
FOR377_REQUIRED_CLASSIFICATION = "linear-srgb-mixed-needs-reference-color-evidence"
LINEAR_VARIANT_ID = "linear_srgb_source_over_effective_destination_nearest_255"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL = 1033
REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL = 794
REQUIRED_FOR377_LINEAR_TOTAL_RESIDUAL = 607
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-preuve-couleur-source-transparente-apres-for-377"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-377-classe-la-piste-linear-s-rgb-m60-f16-comme-mixte-et-exige-une-preuve-couleur-directe"
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
    "direct-source-color-confirms-linear-axis",
    "direct-source-color-points-coverage-mismatch",
    "direct-source-color-points-destination-artifact",
    "direct-source-color-mixed-needs-next-axis",
    "direct-source-color-blocked",
}
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for378-pycache python3 -m py_compile "
        "scripts/validate_for378_m60_f16_direct_source_color_evidence.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-378 validation failed: {message}")


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


def rgb_delta(value: Any, label: str) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be an RGB delta object")
    out = {}
    for channel in ("r", "g", "b"):
        raw = value.get(channel)
        require(isinstance(raw, int), f"{label}.{channel} must be int")
        out[channel] = raw
    return out


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def quantize_nearest_255(value: float) -> int:
    if math.isnan(value):
        return 0
    return max(0, min(255, int(value * 255.0 + 0.5)))


def source_unpremul_over_white(source_rgba: list[int]) -> list[int]:
    alpha = source_rgba[3] / 255.0
    return [
        quantize_nearest_255((source_rgba[index] / 255.0) * alpha + (1.0 - alpha))
        for index in range(3)
    ] + [255]


def dict_without(dct: dict[str, Any], key: str) -> tuple[dict[str, Any], Any]:
    out = dict(dct)
    value = out.pop(key, None)
    return out, value


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
    require("private class BoundedStrokeCapJoinTransparentSourceGM" in source, "transparent source GM missing")
    transparent_block = function_block(
        source,
        "private class BoundedStrokeCapJoinTransparentSourceGM",
        "private class NeutralAaCoverageGM",
    )
    for needle in (
        "setBGColor(0x00000000)",
        "0xFF0066CC.toInt()",
        "0xFF008A4C.toInt()",
        "0xFFB33C00.toInt()",
        "strokeWidth = 10f",
        "strokeCap = case.cap",
        "strokeJoin = case.join",
    ):
        require(needle in transparent_block, f"transparent source GM missing: {needle}")
    require("drawColor(SK_ColorWHITE)" not in transparent_block, "transparent source GM draws production white background")
    producer_block = function_block(
        source,
        "private fun m60F16DirectSourceColorEvidenceJson",
        "private fun candidatePolicySample",
    )
    for needle in (
        '"directSourceReadFromRenderer": false',
        '"directSourceReadFromGpuImage": false',
        '"directSourceAppliedToRenderer": false',
        '"inverseDestinationEstimateUsedAsPrimaryEvidence": false',
        '"inverseDestinationEstimateAppliedAsCorrection": false',
    ):
        require(needle in producer_block, f"producer guard missing: {needle}")
    require("WebGpuSink.draw" not in producer_block, "FOR-378 producer reads from GPU renderer")
    helper_block = function_block(
        source,
        "private fun directSourceColorEvidenceSample",
        "private fun m60F16EffectiveCoverageExportJson",
    )
    require("transparentSource.getPixel" in helper_block, "FOR-378 helper does not read transparent source sample")
    require("WebGpuSink.draw" not in helper_block, "FOR-378 helper reads from GPU renderer")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16DirectSourceColorEvidence(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16DirectSourceColorEvidence"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16DirectSourceColorEvidenceJson"),
        "transparentGmLine": find_line(CAPTURE_PRODUCER, "private class BoundedStrokeCapJoinTransparentSourceGM"),
        "sampleLine": find_line(CAPTURE_PRODUCER, "private fun directSourceColorEvidenceSample"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun directSourceColorClassification"),
    }


def validate_for377() -> dict[str, Any]:
    data = load_json(FOR377_ARTIFACT)
    require(data.get("linear") == "FOR-377", "FOR-377 identity changed")
    require(data.get("decision") == FOR377_REQUIRED_DECISION, "FOR-377 decision changed")
    require(data.get("classification") == FOR377_REQUIRED_CLASSIFICATION, "FOR-377 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-377 current residual changed")
    require(data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-377 FOR-373 total changed")
    require(
        data.get("for375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-377 FOR-375 total changed",
    )
    require(data.get("linearSrgbTotalResidual") == REQUIRED_FOR377_LINEAR_TOTAL_RESIDUAL, "FOR-377 linear total changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-377 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-377 coordinate changed")
    return data


def expected_direct_block(sample: dict[str, Any], index: int) -> dict[str, Any]:
    block = sample.get("directSourceColorEvidence")
    require(isinstance(block, dict), f"direct source block missing at sample {index}")

    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    direct = rgba(block.get("directSourceRgba"), "directSourceRgba")
    paint = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    alpha_byte = direct[3]
    alpha = alpha_byte / 255.0
    unpremul = None if alpha_byte == 0 else direct[:3]
    source_coverage_byte = sample.get("sourceCoverageByte")
    require(isinstance(source_coverage_byte, int), "sourceCoverageByte missing")
    premul = [quantize_nearest_255((unpremul[i] / 255.0) * alpha) if unpremul is not None else 0 for i in range(3)]
    for value in premul:
        require(value <= alpha_byte, "direct premultiplied channel exceeds alpha")
    expected_premul = [quantize_nearest_255((paint[index] / 255.0) * alpha) for index in range(3)]
    premul_delta = {channel: premul[i] - expected_premul[i] for i, channel in enumerate(("r", "g", "b"))}
    unpremul_delta = None
    if unpremul is not None:
        unpremul_delta = {channel: unpremul[i] - paint[i] for i, channel in enumerate(("r", "g", "b"))}
    recomposed = source_unpremul_over_white(direct)
    residual = sample_residual(reference, recomposed)
    linear_audit = sample.get("linearSrgbPlausibilityAudit")
    require(isinstance(linear_audit, dict), "linearSrgbPlausibilityAudit missing")
    expected = {
        "directSourceScene": "m60_bounded_stroke_cap_join_transparent_source_for378",
        "directSourceReadSource": "CPU/reference diagnostic transparent source bitmap",
        "directSourceReadFromRenderer": False,
        "directSourceReadFromGpuImage": False,
        "directSourceAppliedToRenderer": False,
        "directSourceRgba": direct,
        "directSourceReadbackRgbDomain": "SkBitmap.getPixel SkColor unpremultiplied 8-bit readback",
        "directSourceAlphaByte": alpha_byte,
        "directSourceAlpha": round(alpha, 6),
        "directPremultipliedRgb": premul,
        "directUnpremultipliedRgb": unpremul,
        "directUnpremultipliedRgbPossible": unpremul is not None,
        "paintSourceRgba": paint,
        "paintSourceUnpremultipliedRgbDelta": unpremul_delta,
        "paintSourceUnpremultipliedRgbDeltaAbsMax": (
            max(abs(value) for value in unpremul_delta.values()) if unpremul_delta is not None else 255
        ),
        "paintSourcePremultipliedRgbExpected": expected_premul,
        "paintSourcePremultipliedRgbDelta": premul_delta,
        "sourceCoverageByte": source_coverage_byte,
        "sourceCoverageAlphaDelta": alpha_byte - source_coverage_byte,
        "sourceCoverageAlphaDeltaAbs": abs(alpha_byte - source_coverage_byte),
        "directRecomposedOnWhiteRgba": recomposed,
        "directRecompositionFormula": "rgb=round((directUnpremultipliedRgb/255.0)*directSourceAlpha + (1.0-directSourceAlpha))*255; a=255",
        "directRecomposedOnWhiteResidual": residual,
        "directRecomposedOnWhiteDeltaVsCurrent": residual - sample["currentResidual"],
        "directRecomposedOnWhiteDeltaVsFor373Candidate": residual - sample["candidateResidual"],
        "directRecomposedOnWhiteDeltaVsFor375Candidate": residual - sample["effectiveDestinationCandidateResidual"],
        "directRecomposedOnWhiteDeltaVsFor377LinearSrgb": residual - linear_audit["linearSrgbResidual"],
        "directRecompositionImprovesCurrent": residual < sample["currentResidual"],
        "directRecompositionImprovesFor375Candidate": residual < sample["effectiveDestinationCandidateResidual"],
        "directRecompositionImprovesFor377LinearSrgb": residual < linear_audit["linearSrgbResidual"],
        "inverseDestinationEstimateUsedAsPrimaryEvidence": False,
    }
    require(block == expected, f"direct source block mismatch at sample {index}")
    return expected


def expected_classification(
    sample_count: int,
    direct_total: int,
    current_total: int,
    for375_total: int,
    linear_total: int,
    coverage_delta_total: int,
    coverage_delta_max: int,
    paint_delta_max: int,
) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT or linear_total != REQUIRED_FOR377_LINEAR_TOTAL_RESIDUAL:
        return "direct-source-color-blocked"
    if coverage_delta_max > 1 or coverage_delta_total > 4:
        return "direct-source-color-points-coverage-mismatch"
    if direct_total <= linear_total and direct_total < current_total and direct_total < for375_total:
        return "direct-source-color-confirms-linear-axis"
    if direct_total >= for375_total and direct_total >= linear_total and paint_delta_max <= 4:
        return "direct-source-color-points-destination-artifact"
    return "direct-source-color-mixed-needs-next-axis"


def validate_sample(sample: dict[str, Any], for377_sample: dict[str, Any], index: int) -> dict[str, Any]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    preserved, block = dict_without(sample, "directSourceColorEvidence")
    require(preserved == for377_sample, f"FOR-377 preserved sample fields diverged at sample {index}")
    require(isinstance(block, dict), f"directSourceColorEvidence missing at sample {index}")
    return expected_direct_block(sample, index)


def validate_artifact(data: dict[str, Any], for377: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor377Decision") == FOR377_REQUIRED_DECISION, "FOR-377 decision gate changed")
    require(data.get("requiredFor377Classification") == FOR377_REQUIRED_CLASSIFICATION, "FOR-377 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("primaryEvidenceSource") == "transparent-cpu-diagnostic-source-rgba", "primary evidence source changed")
    require(data.get("inverseDestinationEstimateUsedAsPrimaryEvidence") is False, "inverse destination is primary evidence")
    require(data.get("inverseDestinationEstimateAppliedAsCorrection") is False, "inverse destination applied as correction")
    require(data.get("directSourceReadSource") == "CPU/reference diagnostic transparent source bitmap", "direct source provenance changed")
    for guard in (
        "directSourceReadFromRenderer",
        "directSourceReadFromGpuImage",
        "directSourceAppliedToRenderer",
        "auditDoesNotProduceCorrection",
        "auditDoesNotApplyRendererChange",
    ):
        expected = False if guard.startswith("directSource") else True
        require(data.get(guard) is expected, f"guard changed: {guard}")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "current residual changed")
    require(data.get("for373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 total changed")
    require(data.get("for375EffectiveDestinationCandidateTotalResidual") == REQUIRED_FOR375_CANDIDATE_TOTAL_RESIDUAL, "FOR-375 total changed")
    require(data.get("requiredFor376BestVariantId") == LINEAR_VARIANT_ID, "required FOR-376 variant changed")
    require(data.get("requiredFor376BestVariantTotalResidual") == REQUIRED_FOR377_LINEAR_TOTAL_RESIDUAL, "required FOR-376 residual changed")
    require(data.get("linearSrgbTotalResidual") == REQUIRED_FOR377_LINEAR_TOTAL_RESIDUAL, "linear total changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    samples = data.get("samples")
    for377_samples = for377.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for377_samples, list) and len(for377_samples) == REQUIRED_SAMPLE_COUNT, "FOR-377 samples missing")
    blocks = [validate_sample(sample, for377_samples[index - 1], index) for index, sample in enumerate(samples, start=1)]

    current_total = sum(sample["currentResidual"] for sample in samples)
    for373_total = sum(sample["candidateResidual"] for sample in samples)
    for375_total = sum(sample["effectiveDestinationCandidateResidual"] for sample in samples)
    linear_total = sum(sample["linearSrgbPlausibilityAudit"]["linearSrgbResidual"] for sample in samples)
    direct_total = sum(block["directRecomposedOnWhiteResidual"] for block in blocks)
    coverage_delta_total = sum(block["sourceCoverageAlphaDeltaAbs"] for block in blocks)
    coverage_delta_max = max(block["sourceCoverageAlphaDeltaAbs"] for block in blocks)
    paint_delta_max = max(block["paintSourceUnpremultipliedRgbDeltaAbsMax"] for block in blocks)

    require(data.get("currentResidual") == current_total, "current total recomputation mismatch")
    require(data.get("for373CandidateTotalResidual") == for373_total, "FOR-373 total recomputation mismatch")
    require(data.get("for375EffectiveDestinationCandidateTotalResidual") == for375_total, "FOR-375 total recomputation mismatch")
    require(data.get("linearSrgbTotalResidual") == linear_total, "linear total recomputation mismatch")
    require(data.get("directRecomposedOnWhiteTotalResidual") == direct_total, "direct total mismatch")
    require(data.get("directRecomposedOnWhiteTotalDeltaVsCurrent") == direct_total - current_total, "direct delta current mismatch")
    require(data.get("directRecomposedOnWhiteTotalDeltaVsFor373Candidate") == direct_total - for373_total, "direct delta FOR-373 mismatch")
    require(data.get("directRecomposedOnWhiteTotalDeltaVsFor375Candidate") == direct_total - for375_total, "direct delta FOR-375 mismatch")
    require(data.get("directRecomposedOnWhiteTotalDeltaVsFor377LinearSrgb") == direct_total - linear_total, "direct delta FOR-377 mismatch")
    require(data.get("sourceCoverageAlphaDeltaAbsTotal") == coverage_delta_total, "coverage alpha delta total mismatch")
    require(data.get("sourceCoverageAlphaDeltaAbsMax") == coverage_delta_max, "coverage alpha delta max mismatch")
    require(data.get("paintSourceUnpremultipliedRgbDeltaAbsMax") == paint_delta_max, "paint source delta max mismatch")
    require(
        data.get("directRecompositionImprovesCurrentSampleCount")
        == sum(1 for block in blocks if block["directRecomposedOnWhiteDeltaVsCurrent"] < 0),
        "direct improves current count mismatch",
    )
    require(
        data.get("directRecompositionRegressesCurrentSampleCount")
        == sum(1 for block in blocks if block["directRecomposedOnWhiteDeltaVsCurrent"] > 0),
        "direct regresses current count mismatch",
    )
    require(
        data.get("directRecompositionImprovesFor375SampleCount")
        == sum(1 for block in blocks if block["directRecomposedOnWhiteDeltaVsFor375Candidate"] < 0),
        "direct improves FOR-375 count mismatch",
    )
    require(
        data.get("directRecompositionRegressesFor375SampleCount")
        == sum(1 for block in blocks if block["directRecomposedOnWhiteDeltaVsFor375Candidate"] > 0),
        "direct regresses FOR-375 count mismatch",
    )
    require(
        data.get("classification")
        == expected_classification(
            len(samples),
            direct_total,
            current_total,
            for375_total,
            linear_total,
            coverage_delta_total,
            coverage_delta_max,
            paint_delta_max,
        ),
        "classification rule mismatch",
    )
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    sample_rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | {alpha} | {coverage} | {alpha_delta} | {direct} | {current} | {for375} | {linear} | {delta_current} | {delta_linear} |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            alpha=sample["directSourceColorEvidence"]["directSourceAlphaByte"],
            coverage=sample["sourceCoverageByte"],
            alpha_delta=sample["directSourceColorEvidence"]["sourceCoverageAlphaDelta"],
            direct=sample["directSourceColorEvidence"]["directRecomposedOnWhiteResidual"],
            current=sample["currentResidual"],
            for375=sample["effectiveDestinationCandidateResidual"],
            linear=sample["linearSrgbPlausibilityAudit"]["linearSrgbResidual"],
            delta_current=sample["directSourceColorEvidence"]["directRecomposedOnWhiteDeltaVsCurrent"],
            delta_linear=sample["directSourceColorEvidence"]["directRecomposedOnWhiteDeltaVsFor377LinearSrgb"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-378 Direct source color evidence M60 F16

Linear: `FOR-378`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-378 captures a transparent CPU diagnostic source for the same ten FOR-377
samples. It preserves each FOR-377 sample exactly and appends
`directSourceColorEvidence`; the inverse destination estimate is retained only
as historical FOR-377 context, not as primary proof.

## Result

- Current residual: `{data['currentResidual']}`
- FOR-375 effective destination residual: `{data['for375EffectiveDestinationCandidateTotalResidual']}`
- FOR-377 linear-sRGB residual: `{data['linearSrgbTotalResidual']}`
- Direct source recomposed-on-white residual: `{data['directRecomposedOnWhiteTotalResidual']}`
- Delta versus current: `{data['directRecomposedOnWhiteTotalDeltaVsCurrent']}`
- Delta versus FOR-375: `{data['directRecomposedOnWhiteTotalDeltaVsFor375Candidate']}`
- Delta versus FOR-377 linear-sRGB: `{data['directRecomposedOnWhiteTotalDeltaVsFor377LinearSrgb']}`
- Source alpha versus FOR-372 coverage: total `{data['sourceCoverageAlphaDeltaAbsTotal']}`, max `{data['sourceCoverageAlphaDeltaAbsMax']}`
- Paint source unpremultiplied RGB max delta: `{data['paintSourceUnpremultipliedRgbDeltaAbsMax']}`
- Reason: {data['classificationReason']}

## Producer

- Writer call: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-378: `{producer_path}:{producer_lines['writerLine']}`
- Transparent source GM: `{producer_path}:{producer_lines['transparentGmLine']}`
- JSON producer: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Sample audit: `{producer_path}:{producer_lines['sampleLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`

## Samples

| # | x | y | band | direct alpha | coverage | alpha delta | direct residual | current | FOR-375 | FOR-377 linear | delta current | delta linear |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
{sample_rows}

## Validation Commands

{commands}
"""


def main() -> None:
    producer_lines = validate_producer_source()
    for377 = validate_for377()
    data = load_json(ARTIFACT)
    validate_artifact(data, for377)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(
        "FOR-378 validation passed: "
        f"classification={data['classification']} "
        f"directResidual={data['directRecomposedOnWhiteTotalResidual']} "
        f"deltaVsLinear={data['directRecomposedOnWhiteTotalDeltaVsFor377LinearSrgb']}"
    )


if __name__ == "__main__":
    main()
