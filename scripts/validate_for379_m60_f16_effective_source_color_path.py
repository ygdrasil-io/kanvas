#!/usr/bin/env python3
"""Validate the FOR-379 M60 F16 effective source/color path evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-379"
SCENE_ID = "m60-f16-effective-source-color-path-for379"
SOURCE_SCENE_ID = "m60-f16-direct-source-color-evidence-for378"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-source-color-path-for379/"
    "m60-f16-effective-source-color-path-for379.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-379-m60-f16-effective-source-color-path.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
FOR378_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-source-color-evidence-for378/"
    "m60-f16-direct-source-color-evidence-for378.json"
)

DECISION = "M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED"
FOR378_REQUIRED_DECISION = "M60_F16_DIRECT_SOURCE_COLOR_EVIDENCE_RECORDED"
FOR378_REQUIRED_CLASSIFICATION = "direct-source-color-confirms-linear-axis"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_DIRECT_RESIDUAL = 19
REQUIRED_GAIN_VS_CURRENT = 837
REQUIRED_CURRENT_DIRECT_DISTANCE = 869
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-chemin-couleur-source-effectif-apres-for-378"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-378-confirme-la-preuve-source-directe-m60-f16-avec-residuel-19-sans-destination-clampee"
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
    "source-color-path-ready-for-correction",
    "composition-color-still-ambiguous",
    "evidence-insufficient",
}
ALLOWED_SAMPLE_CLASSIFICATIONS = {
    "source-color-ready-for-correction",
    "composition-color-ambiguous",
    "evidence-insufficient",
}
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for379-pycache python3 -m py_compile "
        "scripts/validate_for379_m60_f16_effective_source_color_path.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-379 validation failed: {message}")


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


def channel_dict(value: Any, label: str, *, signed: bool = True) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be a channel object")
    out = {}
    for channel in ("r", "g", "b", "a"):
        raw = value.get(channel)
        require(isinstance(raw, int), f"{label}.{channel} must be int")
        if not signed:
            require(raw >= 0, f"{label}.{channel} must be non-negative")
        out[channel] = raw
    return out


def sample_residual(reference: list[int], value: list[int]) -> int:
    return sum(abs(reference[index] - value[index]) for index in range(4))


def channel_error(reference: list[int], value: list[int]) -> dict[str, int]:
    return {
        "r": abs(reference[0] - value[0]),
        "g": abs(reference[1] - value[1]),
        "b": abs(reference[2] - value[2]),
        "a": abs(reference[3] - value[3]),
    }


def channel_subtract(left: list[int], right: list[int]) -> dict[str, int]:
    return {
        "r": left[0] - right[0],
        "g": left[1] - right[1],
        "b": left[2] - right[2],
        "a": left[3] - right[3],
    }


def channel_improvement(current_error: dict[str, int], direct_error: dict[str, int]) -> dict[str, int]:
    return {channel: current_error[channel] - direct_error[channel] for channel in ("r", "g", "b", "a")}


def channel_distance(channels: dict[str, int]) -> int:
    return sum(abs(channels[channel]) for channel in ("r", "g", "b", "a"))


def dominant_rgb_abs_channel(channels: dict[str, int]) -> str:
    names = ("r", "g", "b")
    labels = {"r": "red", "g": "green", "b": "blue"}
    best = max(names, key=lambda name: abs(channels[name]))
    return labels[best]


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
    producer_block = function_block(
        source,
        "private fun m60F16EffectiveSourceColorPathJson",
        "private fun candidatePolicySample",
    )
    for needle in (
        '"decision": "M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED"',
        '"primaryComparison": "current-rgba-vs-transparent-source-direct-recomposed-on-white"',
        '"inverseDestinationEstimateUsedAsPrimaryEvidence": false',
        '"inverseDestinationEstimateAppliedAsCorrection": false',
    ):
        require(needle in producer_block, f"producer guard missing: {needle}")
    require("WebGpuSink.draw" not in producer_block, "FOR-379 producer reads from GPU renderer")
    helper_block = function_block(
        source,
        "private fun effectiveSourceColorPathSample",
        "private data class EffectiveSourceColorPathSample",
    )
    require("currentMinusDirect" in helper_block, "FOR-379 helper missing current/direct comparison")
    require("inverseDestinationEstimate" not in helper_block, "FOR-379 helper uses inverse destination")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16EffectiveSourceColorPath(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16EffectiveSourceColorPath"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16EffectiveSourceColorPathJson"),
        "sampleLine": find_line(CAPTURE_PRODUCER, "private fun effectiveSourceColorPathSample"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun effectiveSourceColorPathClassification"),
    }


def validate_for378() -> dict[str, Any]:
    data = load_json(FOR378_ARTIFACT)
    require(data.get("linear") == "FOR-378", "FOR-378 identity changed")
    require(data.get("decision") == FOR378_REQUIRED_DECISION, "FOR-378 decision changed")
    require(data.get("classification") == FOR378_REQUIRED_CLASSIFICATION, "FOR-378 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-378 current residual changed")
    require(data.get("directRecomposedOnWhiteTotalResidual") == REQUIRED_DIRECT_RESIDUAL, "FOR-378 direct residual changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-378 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-378 coordinate changed")
    return data


def expected_sample_classification(
    direct_residual: int,
    delta_vs_current: int,
    coverage_alpha_delta_abs: int,
    unpremul_possible: bool,
    premul_valid: bool,
) -> str:
    if not unpremul_possible or not premul_valid or coverage_alpha_delta_abs > 1:
        return "evidence-insufficient"
    if direct_residual <= 4 and delta_vs_current < 0:
        return "source-color-ready-for-correction"
    if delta_vs_current < 0:
        return "composition-color-ambiguous"
    return "evidence-insufficient"


def expected_artifact_classification(
    sample_count: int,
    direct_total: int,
    current_total: int,
    ready_count: int,
    insufficient_count: int,
) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT or insufficient_count > 0:
        return "evidence-insufficient"
    if ready_count == sample_count and direct_total == REQUIRED_DIRECT_RESIDUAL and current_total == REQUIRED_CURRENT_RESIDUAL:
        return "source-color-path-ready-for-correction"
    if direct_total < current_total:
        return "composition-color-still-ambiguous"
    return "evidence-insufficient"


def expected_effective_block(sample: dict[str, Any], index: int) -> dict[str, Any]:
    block = sample.get("effectiveSourceColorPath")
    require(isinstance(block, dict), f"effectiveSourceColorPath missing at sample {index}")
    direct_block = sample.get("directSourceColorEvidence")
    require(isinstance(direct_block, dict), f"directSourceColorEvidence missing at sample {index}")

    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    direct_source = rgba(direct_block.get("directSourceRgba"), "directSourceRgba")
    direct_recomposed = rgba(direct_block.get("directRecomposedOnWhiteRgba"), "directRecomposedOnWhiteRgba")
    direct_premul = rgb(direct_block.get("directPremultipliedRgb"), "directPremultipliedRgb")
    alpha_byte = direct_block.get("directSourceAlphaByte")
    require(isinstance(alpha_byte, int), "directSourceAlphaByte missing")
    premul_valid = all(channel <= alpha_byte for channel in direct_premul)
    current_error = channel_error(reference, current)
    direct_error = channel_error(reference, direct_recomposed)
    current_minus_direct = channel_subtract(current, direct_recomposed)
    direct_residual = sample_residual(reference, direct_recomposed)
    current_residual = sample_residual(reference, current)
    coverage_alpha_delta_abs = direct_block.get("sourceCoverageAlphaDeltaAbs")
    require(isinstance(coverage_alpha_delta_abs, int), "sourceCoverageAlphaDeltaAbs missing")
    unpremul_possible = direct_block.get("directUnpremultipliedRgbPossible") is True
    classification = expected_sample_classification(
        direct_residual,
        direct_residual - current_residual,
        coverage_alpha_delta_abs,
        unpremul_possible,
        premul_valid,
    )
    expected = {
        "comparisonId": "current-vs-direct-source-recomposed-on-white",
        "comparisonUsesInverseDestinationEstimate": False,
        "referenceRgba": reference,
        "currentRgba": current,
        "directSourceTransparentUnpremultipliedRgba": direct_source,
        "directSourceTransparentPremultipliedRgb": direct_premul,
        "directSourceAlphaByte": alpha_byte,
        "sourceCoverageByte": sample["sourceCoverageByte"],
        "sourceCoverageAlphaDelta": direct_block["sourceCoverageAlphaDelta"],
        "sourceCoverageAlphaDeltaAbs": coverage_alpha_delta_abs,
        "directRecomposedOnWhiteRgba": direct_recomposed,
        "currentResidual": current_residual,
        "directRecomposedOnWhiteResidual": direct_residual,
        "directRecomposedOnWhiteDeltaVsCurrent": direct_residual - current_residual,
        "directRecomposedOnWhiteGainVsCurrent": current_residual - direct_residual,
        "currentMinusDirectRecomposedRgba": current_minus_direct,
        "currentVsDirectRecomposedRgbaDistance": channel_distance(current_minus_direct),
        "currentErrorByChannel": current_error,
        "directRecomposedErrorByChannel": direct_error,
        "directRecomposedImprovementByChannel": channel_improvement(current_error, direct_error),
        "dominantCurrentMinusDirectRecomposedChannel": dominant_rgb_abs_channel(current_minus_direct),
        "directPremultipliedChannelsWithinAlpha": premul_valid,
        "classification": classification,
    }
    require(block == expected, f"effective source/color block mismatch at sample {index}")
    return expected


def validate_sample(sample: dict[str, Any], for378_sample: dict[str, Any], index: int) -> dict[str, Any]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    preserved, block = dict_without(sample, "effectiveSourceColorPath")
    require(preserved == for378_sample, f"FOR-378 preserved sample fields diverged at sample {index}")
    require(isinstance(block, dict), f"effectiveSourceColorPath missing at sample {index}")
    return expected_effective_block(sample, index)


def validate_artifact(data: dict[str, Any], for378: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor378Decision") == FOR378_REQUIRED_DECISION, "FOR-378 decision gate changed")
    require(data.get("requiredFor378Classification") == FOR378_REQUIRED_CLASSIFICATION, "FOR-378 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(
        data.get("primaryComparison") == "current-rgba-vs-transparent-source-direct-recomposed-on-white",
        "primary comparison changed",
    )
    require(data.get("additionalComparisonIsolates") == "effective-source-color-path", "additional comparison changed")
    require(data.get("inverseDestinationEstimateUsedAsPrimaryEvidence") is False, "inverse destination is primary evidence")
    require(data.get("inverseDestinationEstimateAppliedAsCorrection") is False, "inverse destination applied")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit produces correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit applies renderer change")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    samples = data.get("samples")
    for378_samples = for378.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for378_samples, list) and len(for378_samples) == REQUIRED_SAMPLE_COUNT, "FOR-378 samples missing")
    blocks = [validate_sample(sample, for378_samples[index - 1], index) for index, sample in enumerate(samples, start=1)]

    for block in blocks:
        require(block["classification"] in ALLOWED_SAMPLE_CLASSIFICATIONS, "sample classification is not stable")
        require(block["comparisonUsesInverseDestinationEstimate"] is False, "sample uses inverse destination")

    current_total = sum(block["currentResidual"] for block in blocks)
    direct_total = sum(block["directRecomposedOnWhiteResidual"] for block in blocks)
    distance_total = sum(block["currentVsDirectRecomposedRgbaDistance"] for block in blocks)
    current_error_totals = {channel: sum(block["currentErrorByChannel"][channel] for block in blocks) for channel in ("r", "g", "b", "a")}
    direct_error_totals = {
        channel: sum(block["directRecomposedErrorByChannel"][channel] for block in blocks) for channel in ("r", "g", "b", "a")
    }
    improvement_totals = {channel: current_error_totals[channel] - direct_error_totals[channel] for channel in ("r", "g", "b", "a")}
    ready_count = sum(1 for block in blocks if block["classification"] == "source-color-ready-for-correction")
    ambiguous_count = sum(1 for block in blocks if block["classification"] == "composition-color-ambiguous")
    insufficient_count = sum(1 for block in blocks if block["classification"] == "evidence-insufficient")

    require(data.get("currentResidual") == current_total == REQUIRED_CURRENT_RESIDUAL, "current residual mismatch")
    require(
        data.get("directRecomposedOnWhiteTotalResidual") == direct_total == REQUIRED_DIRECT_RESIDUAL,
        "direct residual mismatch",
    )
    require(data.get("directRecomposedOnWhiteGainVsCurrent") == REQUIRED_GAIN_VS_CURRENT, "gain mismatch")
    require(data.get("directRecomposedOnWhiteDeltaVsCurrent") == direct_total - current_total, "delta mismatch")
    require(
        data.get("currentVsDirectRecomposedRgbaDistanceTotal") == distance_total == REQUIRED_CURRENT_DIRECT_DISTANCE,
        "current/direct distance mismatch",
    )
    require(channel_dict(data.get("currentErrorTotalsByChannel"), "currentErrorTotalsByChannel", signed=False) == current_error_totals, "current error totals mismatch")
    require(
        channel_dict(data.get("directRecomposedErrorTotalsByChannel"), "directRecomposedErrorTotalsByChannel", signed=False)
        == direct_error_totals,
        "direct error totals mismatch",
    )
    require(
        channel_dict(data.get("directRecomposedImprovementTotalsByChannel"), "directRecomposedImprovementTotalsByChannel")
        == improvement_totals,
        "improvement totals mismatch",
    )
    require(data.get("sourceColorReadyForCorrectionSampleCount") == ready_count, "ready count mismatch")
    require(data.get("compositionColorAmbiguousSampleCount") == ambiguous_count, "ambiguous count mismatch")
    require(data.get("evidenceInsufficientSampleCount") == insufficient_count, "insufficient count mismatch")
    require(
        data.get("classification")
        == expected_artifact_classification(len(samples), direct_total, current_total, ready_count, insufficient_count),
        "classification rule mismatch",
    )
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    sample_rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | {alpha} | {coverage} | {direct} | {current} | {gain} | {distance} | `{classification}` |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            alpha=sample["effectiveSourceColorPath"]["directSourceAlphaByte"],
            coverage=sample["effectiveSourceColorPath"]["sourceCoverageByte"],
            direct=sample["effectiveSourceColorPath"]["directRecomposedOnWhiteResidual"],
            current=sample["effectiveSourceColorPath"]["currentResidual"],
            gain=sample["effectiveSourceColorPath"]["directRecomposedOnWhiteGainVsCurrent"],
            distance=sample["effectiveSourceColorPath"]["currentVsDirectRecomposedRgbaDistance"],
            classification=sample["effectiveSourceColorPath"]["classification"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-379 Effective source/color path M60 F16

Linear: `FOR-379`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-379 preserves the exact ten FOR-378 samples and adds an
`effectiveSourceColorPath` block per sample. The added comparison is
`currentRgba` versus the transparent direct source recomposed on white; it does
not use the clamped inverse destination estimate as primary evidence.

## Result

- Current residual: `{data['currentResidual']}`
- Direct source recomposed-on-white residual: `{data['directRecomposedOnWhiteTotalResidual']}`
- Gain versus current: `{data['directRecomposedOnWhiteGainVsCurrent']}`
- Current/direct RGBA distance total: `{data['currentVsDirectRecomposedRgbaDistanceTotal']}`
- Ready samples: `{data['sourceColorReadyForCorrectionSampleCount']}`
- Ambiguous samples: `{data['compositionColorAmbiguousSampleCount']}`
- Insufficient samples: `{data['evidenceInsufficientSampleCount']}`
- Current error totals: `{data['currentErrorTotalsByChannel']}`
- Direct recomposed error totals: `{data['directRecomposedErrorTotalsByChannel']}`
- Improvement totals: `{data['directRecomposedImprovementTotalsByChannel']}`
- Reason: {data['classificationReason']}

## Producer

- Writer call: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-379: `{producer_path}:{producer_lines['writerLine']}`
- JSON producer: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Sample audit: `{producer_path}:{producer_lines['sampleLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`

## Samples

| # | x | y | band | alpha | coverage | direct residual | current residual | gain | current/direct distance | classification |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|---|
{sample_rows}

## Validation Commands

{commands}
"""


def main() -> None:
    producer_lines = validate_producer_source()
    for378 = validate_for378()
    data = load_json(ARTIFACT)
    validate_artifact(data, for378)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(
        "FOR-379 validation passed: "
        f"classification={data['classification']} "
        f"currentResidual={data['currentResidual']} "
        f"directResidual={data['directRecomposedOnWhiteTotalResidual']} "
        f"gainVsCurrent={data['directRecomposedOnWhiteGainVsCurrent']}"
    )


if __name__ == "__main__":
    main()
