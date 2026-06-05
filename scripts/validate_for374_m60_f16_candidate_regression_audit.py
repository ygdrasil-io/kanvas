#!/usr/bin/env python3
"""Validate the FOR-374 M60 F16 candidate regression audit evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-374"
SCENE_ID = "m60-f16-candidate-regression-audit-for374"
SOURCE_SCENE_ID = "m60-f16-candidate-policy-rgba-probe-for373"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-regression-audit-for374/"
    "m60-f16-candidate-regression-audit-for374.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-374-m60-f16-candidate-regression-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

FOR373_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-policy-rgba-probe-for373/"
    "m60-f16-candidate-policy-rgba-probe-for373.json"
)

DECISION = "M60_F16_CANDIDATE_REGRESSION_AUDIT_RECORDED"
FOR373_REQUIRED_DECISION = "M60_F16_CANDIDATE_POLICY_RGBA_PROBE_RECORDED"
FOR373_REQUIRED_CLASSIFICATION = "candidate-policy-regresses"
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_CANDIDATE_RESIDUAL = 1033
REQUIRED_DELTA = 177
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-diagnostic-source-over-white-regresse-apres-for-373"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-373-calcule-la-candidate-policy-rgba-m60-f16-mais-augmente-le-residuel"
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
    "candidate-regression-likely-destination-model",
    "candidate-regression-likely-coverage-model",
    "candidate-regression-likely-quantization-model",
    "candidate-regression-mixed",
    "candidate-regression-blocked",
}
VALIDATION_COMMANDS = [
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for374-pycache python3 -m py_compile "
        "scripts/validate_for374_m60_f16_candidate_regression_audit.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-374 validation failed: {message}")


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


def channel_map(value: Any, label: str) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be a channel map")
    out: dict[str, int] = {}
    for key in ("r", "g", "b", "a"):
        require(isinstance(value.get(key), int), f"{label}.{key} must be int")
        out[key] = value[key]
    return out


def channel_abs_error(reference: list[int], value: list[int]) -> dict[str, int]:
    return {
        "r": abs(reference[0] - value[0]),
        "g": abs(reference[1] - value[1]),
        "b": abs(reference[2] - value[2]),
        "a": abs(reference[3] - value[3]),
    }


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def rounded_coverage(byte_value: int) -> float:
    return round(byte_value / 255.0, 6)


def inverse_destination(reference: list[int], source: list[int], alpha: float) -> dict[str, Any]:
    if alpha <= 0.0 or alpha >= 1.0:
        return {
            "possible": False,
            "alpha": round(alpha, 6),
            "rgbFloat": None,
            "rgbRounded": None,
            "rgbClampedToSrgb": None,
            "hasOutOfSrgbChannel": False,
            "averageDeviationFromWhite": None,
            "conflictsWithWhiteDestination": False,
            "status": "blocked-by-alpha-boundary",
        }
    rgb_float = [
        ((reference[index] / 255.0) - (source[index] / 255.0) * alpha) / (1.0 - alpha) * 255.0
        for index in range(3)
    ]
    rgb_rounded = [int(math.floor(value + 0.5)) for value in rgb_float]
    rgb_clamped = [max(0, min(255, value)) for value in rgb_rounded]
    has_out = any(value < 0.0 or value > 255.0 for value in rgb_float)
    average_deviation = sum(abs(value - 255.0) for value in rgb_float) / 3.0
    conflicts = has_out or average_deviation >= 32.0
    return {
        "possible": True,
        "alpha": round(alpha, 6),
        "rgbFloat": [round(value, 3) for value in rgb_float],
        "rgbRounded": rgb_rounded,
        "rgbClampedToSrgb": rgb_clamped,
        "hasOutOfSrgbChannel": has_out,
        "averageDeviationFromWhite": round(average_deviation, 3),
        "conflictsWithWhiteDestination": conflicts,
        "status": (
            "inverse-destination-incompatible-with-white-assumption"
            if conflicts
            else "inverse-destination-compatible-with-white-assumption"
        ),
    }


def dominant_channel(delta: dict[str, int]) -> tuple[str, str]:
    order = ("r", "g", "b")
    largest = max(order, key=lambda key: delta[key])
    if delta[largest] <= 0:
        return "none", {"r": "red", "g": "green", "b": "blue"}[largest]
    return {"r": "red", "g": "green", "b": "blue"}[largest], {"r": "red", "g": "green", "b": "blue"}[largest]


def regression_direction(
    reference: list[int],
    source: list[int],
    candidate: list[int],
    delta: dict[str, int],
    largest_channel: str,
) -> str:
    channel_index = {"red": 0, "green": 1, "blue": 2}[largest_channel]
    if delta[{"red": "r", "green": "g", "blue": "b"}[largest_channel]] <= 0:
        return "candidate-improves-or-neutral"
    candidate_delta = candidate[channel_index] - reference[channel_index]
    source_delta = source[channel_index] - reference[channel_index]
    if candidate_delta and source_delta and (candidate_delta > 0) == (source_delta > 0):
        return "source-tint-too-strong"
    if candidate_delta > 0:
        return "too-light"
    if candidate_delta < 0:
        return "too-dark"
    return "mixed"


def expected_classification(
    sample_count: int,
    candidate_total: int,
    current_total: int,
    regressing_samples: int,
    destination_conflicts: int,
    source_tint_samples: int,
    quantization_only_samples: int,
) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT or candidate_total <= current_total:
        return "candidate-regression-blocked"
    if destination_conflicts >= 7 and source_tint_samples >= 7:
        return "candidate-regression-likely-destination-model"
    if regressing_samples >= 7 and destination_conflicts < 4:
        return "candidate-regression-likely-coverage-model"
    if quantization_only_samples >= 7:
        return "candidate-regression-likely-quantization-model"
    return "candidate-regression-mixed"


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
    block = function_block(
        source,
        "private fun m60F16CandidateRegressionAuditJson",
        "private fun candidatePolicySample",
    )
    require("WebGpuSink.draw" not in block, "FOR-374 audit block reads from renderer")
    require('"auditDoesNotApplyRendererChange": true' in block, "renderer-change guard missing")
    require('"candidatePolicyRgbaReadFromRenderer": false' in block, "renderer read guard missing")
    require('"inverseDestinationAppliedAsCorrection": false' in block, "inverse correction guard missing")
    require("candidateRegressionClassification(" in block, "classification function not used")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16CandidateRegressionAudit(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16CandidateRegressionAudit"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16CandidateRegressionAuditJson"),
        "inverseLine": find_line(CAPTURE_PRODUCER, "private fun inverseDestinationEstimate"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun candidateRegressionClassification"),
        "rendererGuardLine": find_line(CAPTURE_PRODUCER, '"auditDoesNotApplyRendererChange": true'),
    }


def validate_for373() -> dict[str, Any]:
    data = load_json(FOR373_ARTIFACT)
    require(data.get("linear") == "FOR-373", "FOR-373 identity changed")
    require(data.get("decision") == FOR373_REQUIRED_DECISION, "FOR-373 decision changed")
    require(data.get("classification") == FOR373_REQUIRED_CLASSIFICATION, "FOR-373 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-373 current residual changed")
    require(data.get("candidateTotalResidual") == REQUIRED_CANDIDATE_RESIDUAL, "FOR-373 candidate residual changed")
    require(data.get("candidateTotalResidualDeltaVsCurrent") == REQUIRED_DELTA, "FOR-373 delta changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-373 sample count changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-373 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-373 coords changed")
        require(sample.get("candidatePolicyRgbaAppliedToRenderer") is not True, "FOR-373 applied candidate")
        require(sample.get("candidatePolicyRgbaReadFromRenderer") is False, "FOR-373 read renderer")
        require(sample.get("candidatePolicyRgbaReadFromGpuImage") is False, "FOR-373 read GPU image")
        require(sample.get("rendererAppliedCandidate") is False, "FOR-373 renderer applied candidate")
    return data


def validate_sample(sample: dict[str, Any], for373_sample: dict[str, Any], index: int) -> dict[str, Any]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    preserved_keys = (
        "referenceRgba",
        "currentRgba",
        "gpuRgba",
        "sampleResidual",
        "maxChannelDelta",
        "paintSourceRgba",
        "paintSourceAlpha",
        "cap",
        "join",
        "strokeWidth",
        "sourceCoverageByte",
        "sourceCoverage",
        "sourceCoverageStatus",
        "effectiveSourceAlphaByte",
        "effectiveSourceAlpha",
        "effectiveSourceAlphaStatus",
        "coverageProvenance",
        "coverageReadSource",
        "coverageReconstructedFromRgbaDeltas",
        "referenceCurrentRgbaUsedForCoverage",
        "sampleDeltaRgbaUsedForCoverage",
        "candidatePolicyId",
        "candidatePolicyRgba",
        "candidatePolicyRgbaStatus",
        "candidatePolicyRgbaSource",
        "candidatePolicyRgbaReadFromRenderer",
        "candidatePolicyRgbaReadFromGpuImage",
        "candidateResidual",
        "candidateResidualDeltaVsCurrent",
        "candidateImprovesSample",
        "rendererAppliedCandidate",
    )
    for key in preserved_keys:
        require(sample.get(key) == for373_sample.get(key), f"{key} diverged from FOR-373 at sample {index}")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    source = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    candidate = rgba(sample.get("candidatePolicyRgba"), "candidatePolicyRgba")
    current_error = channel_abs_error(reference, current)
    candidate_error = channel_abs_error(reference, candidate)
    candidate_minus_current = {
        key: candidate_error[key] - current_error[key]
        for key in ("r", "g", "b", "a")
    }
    require(channel_map(sample.get("currentErrorByChannel"), "currentErrorByChannel") == current_error, "current error mismatch")
    require(
        channel_map(sample.get("candidateErrorByChannel"), "candidateErrorByChannel") == candidate_error,
        "candidate error mismatch",
    )
    require(
        channel_map(sample.get("candidateMinusCurrentErrorByChannel"), "candidateMinusCurrentErrorByChannel")
        == candidate_minus_current,
        "candidate-current error mismatch",
    )
    dominant, largest = dominant_channel(candidate_minus_current)
    require(sample.get("dominantRegressionChannel") == dominant, "dominant regression channel mismatch")
    require(sample.get("largestCandidateMinusCurrentErrorChannel") == largest, "largest channel mismatch")
    direction = regression_direction(reference, source, candidate, candidate_minus_current, largest)
    require(sample.get("regressionDirection") == direction, "regression direction mismatch")
    coverage_byte = sample.get("sourceCoverageByte")
    require(isinstance(coverage_byte, int), "sourceCoverageByte missing")
    require(sample.get("sourceCoverage") == rounded_coverage(coverage_byte), "sourceCoverage changed")
    require(sample.get("effectiveSourceAlpha") == rounded_coverage(coverage_byte), "effectiveSourceAlpha changed")
    require(sample.get("currentResidual") == sample_residual(reference, current), "currentResidual mismatch")
    require(sample.get("candidateResidual") == sample_residual(reference, candidate), "candidateResidual mismatch")
    expected_inverse = inverse_destination(reference, source, sample["effectiveSourceAlpha"])
    inverse = sample.get("inverseDestinationEstimate")
    require(isinstance(inverse, dict), "inverseDestinationEstimate missing")
    for key, expected_value in expected_inverse.items():
        require(inverse.get(key) == expected_value, f"inverse destination {key} mismatch at sample {index}")
    require(inverse.get("diagnosticOnly") is True, "inverse destination is not diagnostic only")
    require(inverse.get("usedAsCorrection") is False, "inverse destination used as correction")
    require(sample.get("inverseDestinationUsedAsCorrection") is False, "inverse destination used as correction")
    require(sample.get("rendererAppliedCandidate") is False, "renderer applied candidate")
    require(sample.get("candidatePolicyRgbaReadFromRenderer") is False, "candidate read from renderer")
    require(sample.get("candidatePolicyRgbaReadFromGpuImage") is False, "candidate read from GPU image")
    require(sample.get("coverageReconstructedFromRgbaDeltas") is False, "coverage reconstructed from deltas")
    return {
        "currentResidual": sample["currentResidual"],
        "candidateResidual": sample["candidateResidual"],
        "delta": sample["candidateResidualDeltaVsCurrent"],
        "regresses": sample["candidateResidualDeltaVsCurrent"] > 0,
        "improves": sample["candidateResidualDeltaVsCurrent"] < 0,
        "destinationConflict": inverse["conflictsWithWhiteDestination"],
        "sourceTint": direction == "source-tint-too-strong",
        "quantizationOnly": abs(sample["candidateResidualDeltaVsCurrent"]) <= 4
        and not inverse["conflictsWithWhiteDestination"],
    }


def validate_artifact(data: dict[str, Any], for373: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor373Decision") == FOR373_REQUIRED_DECISION, "FOR-373 decision gate changed")
    require(data.get("requiredFor373Classification") == FOR373_REQUIRED_CLASSIFICATION, "FOR-373 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit produced correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit applies renderer change")
    require(data.get("requiredCurrentResidual") == REQUIRED_CURRENT_RESIDUAL, "required current residual changed")
    require(
        data.get("requiredFor373CandidateTotalResidual") == REQUIRED_CANDIDATE_RESIDUAL,
        "required FOR-373 candidate residual changed",
    )
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    samples = data.get("samples")
    for373_samples = for373.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for373_samples, list) and len(for373_samples) == REQUIRED_SAMPLE_COUNT, "FOR-373 samples missing")
    current_total = 0
    candidate_total = 0
    regressing = 0
    improved = 0
    destination_conflicts = 0
    source_tint = 0
    quantization_only = 0
    for index, sample in enumerate(samples, start=1):
        metrics = validate_sample(sample, for373_samples[index - 1], index)
        current_total += metrics["currentResidual"]
        candidate_total += metrics["candidateResidual"]
        regressing += int(metrics["regresses"])
        improved += int(metrics["improves"])
        destination_conflicts += int(metrics["destinationConflict"])
        source_tint += int(metrics["sourceTint"])
        quantization_only += int(metrics["quantizationOnly"])
    require(current_total == REQUIRED_CURRENT_RESIDUAL, "current residual recomputation changed")
    require(candidate_total == REQUIRED_CANDIDATE_RESIDUAL, "candidate residual recomputation changed")
    require(data.get("currentResidual") == current_total, "currentResidual field changed")
    require(data.get("candidateTotalResidual") == candidate_total, "candidateTotalResidual field changed")
    require(data.get("candidateTotalResidualDeltaVsCurrent") == REQUIRED_DELTA, "delta changed")
    require(data.get("regressingSampleCount") == regressing, "regressing sample count mismatch")
    require(data.get("improvedSampleCount") == improved, "improved sample count mismatch")
    require(data.get("destinationConflictSampleCount") == destination_conflicts, "destination conflict count mismatch")
    require(data.get("sourceTintRegressionSampleCount") == source_tint, "source tint count mismatch")
    require(data.get("quantizationOnlySampleCount") == quantization_only, "quantization-only count mismatch")
    require(
        data.get("classification")
        == expected_classification(
            len(samples),
            candidate_total,
            current_total,
            regressing,
            destination_conflicts,
            source_tint,
            quantization_only,
        ),
        "classification rule mismatch",
    )
    require(
        data.get("likelyMissingParameter")
        == "effective-destination-color-or-background-model-before-stroke-composition",
        "likely missing parameter should isolate destination model",
    )
    inverse_model = data.get("inverseDestinationModel")
    require(isinstance(inverse_model, dict), "inverseDestinationModel missing")
    require(inverse_model.get("diagnosticOnly") is True, "inverse model is not diagnostic")
    require(inverse_model.get("appliedToRenderer") is False, "inverse model applied to renderer")
    require(inverse_model.get("usedAsCorrection") is False, "inverse model used as correction")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{candidate}` | {current_residual} | {candidate_residual} | {delta} | `{candidate_error}` | `{current_error}` | `{minus}` | `{dominant}` | `{direction}` | `{inverse}` |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            candidate=sample["candidatePolicyRgba"],
            current_residual=sample["currentResidual"],
            candidate_residual=sample["candidateResidual"],
            delta=sample["candidateResidualDeltaVsCurrent"],
            candidate_error=sample["candidateErrorByChannel"],
            current_error=sample["currentErrorByChannel"],
            minus=sample["candidateMinusCurrentErrorByChannel"],
            dominant=sample["dominantRegressionChannel"],
            direction=sample["regressionDirection"],
            inverse=sample["inverseDestinationEstimate"]["rgbRounded"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-374 Candidate regression audit M60 F16

Linear: `FOR-374`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-374 audite pourquoi la candidate `{data['candidatePolicyId']}` issue de
FOR-373 regresse M60 F16. L'audit ne produit pas de correction et n'applique
aucun changement renderer/runtime.

## Resultat

- Residuel courant preserve: `{data['currentResidual']}`
- Residuel candidate preserve: `{data['candidateTotalResidual']}`
- Delta preserve: `{data['candidateTotalResidualDeltaVsCurrent']}`
- Samples qui regressent: `{data['regressingSampleCount']}`
- Samples ameliores: `{data['improvedSampleCount']}`
- Conflits avec destination blanche: `{data['destinationConflictSampleCount']}`
- Direction source trop forte: `{data['sourceTintRegressionSampleCount']}`
- Parametre probablement manquant: `{data['likelyMissingParameter']}`

La destination inverse simple demande souvent une destination non blanche ou
hors sRGB pour reproduire la reference avec la source et la couverture FOR-373.
Le prochain axe experimental doit donc isoler le modele de destination effective
avant d'essayer une correction de rendu.

## Producteur

- Appel writer: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-374: `{producer_path}:{producer_lines['writerLine']}`
- Producteur JSON: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Estimation destination inverse: `{producer_path}:{producer_lines['inverseLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`
- Garde renderer: `{producer_path}:{producer_lines['rendererGuardLine']}`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate RGBA | residuel courant | residuel candidate | delta | erreur candidate | erreur current | delta erreur | canal | direction | destination inverse RGB |
|---|---:|---:|---|---|---|---|---:|---:|---:|---|---|---|---|---|---|
{rows}

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- L'estimation de destination inverse reste une preuve diagnostique et n'est pas appliquee.

## Validations

{commands}
"""


def main() -> int:
    for373 = validate_for373()
    producer_lines = validate_producer_source()
    data = load_json(ARTIFACT)
    validate_artifact(data, for373)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(f"FOR-374 candidate regression audit validated: {rel(ARTIFACT)}")
    print(f"FOR-374 report validated: {rel(REPORT)}")
    print(f"decision={data['decision']}")
    print(f"classification={data['classification']}")
    print(f"likelyMissingParameter={data['likelyMissingParameter']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
