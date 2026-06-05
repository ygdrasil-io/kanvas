#!/usr/bin/env python3
"""Validate the FOR-375 M60 F16 effective destination candidate evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-375"
SCENE_ID = "m60-f16-effective-destination-candidate-for375"
SOURCE_SCENE_ID = "m60-f16-candidate-regression-audit-for374"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-destination-candidate-for375/"
    "m60-f16-effective-destination-candidate-for375.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-375-m60-f16-effective-destination-candidate.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
FOR374_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-regression-audit-for374/"
    "m60-f16-candidate-regression-audit-for374.json"
)

DECISION = "M60_F16_EFFECTIVE_DESTINATION_CANDIDATE_RECORDED"
FOR374_REQUIRED_DECISION = "M60_F16_CANDIDATE_REGRESSION_AUDIT_RECORDED"
FOR374_REQUIRED_CLASSIFICATION = "candidate-regression-likely-destination-model"
SOURCE_CANDIDATE_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
EFFECTIVE_DESTINATION_POLICY_ID = "source_over_effective_destination_diagnostic"
REQUIRED_CURRENT_RESIDUAL = 856
REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL = 1033
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-candidate-sur-destination-effective-apres-for-374"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-374-isole-le-modele-de-destination-effective-comme-cause-probable-de-regression-m60-f16"
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
    "effective-destination-candidate-explains-reference",
    "effective-destination-candidate-reduces-residual",
    "effective-destination-candidate-neutral",
    "effective-destination-candidate-regresses",
    "effective-destination-candidate-blocked",
}
VALIDATION_COMMANDS = [
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for375-pycache python3 -m py_compile "
        "scripts/validate_for375_m60_f16_effective_destination_candidate.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-375 validation failed: {message}")


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


def source_over_effective_destination(
    source: list[int],
    effective_alpha_byte: int,
    destination_rgb: list[int],
) -> list[int]:
    alpha = effective_alpha_byte / 255.0
    return [
        quantize_256((source[index] / 255.0) * alpha + (destination_rgb[index] / 255.0) * (1.0 - alpha))
        for index in range(3)
    ] + [255]


def expected_classification(sample_count: int, candidate_total: int, current_total: int) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT:
        return "effective-destination-candidate-blocked"
    if candidate_total <= 64:
        return "effective-destination-candidate-explains-reference"
    if candidate_total < current_total:
        return "effective-destination-candidate-reduces-residual"
    if candidate_total == current_total:
        return "effective-destination-candidate-neutral"
    return "effective-destination-candidate-regresses"


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
        "private fun m60F16EffectiveDestinationCandidateJson",
        "private fun candidatePolicySample",
    )
    require("WebGpuSink.draw" not in block, "FOR-375 candidate block reads from renderer")
    require('"candidateReadFromRenderer": false' in block, "candidate renderer-read guard missing")
    require('"candidateReadFromGpuImage": false' in block, "candidate GPU-image guard missing")
    require('"candidateAppliedToRenderer": false' in block, "candidate renderer-apply guard missing")
    require('"destinationReadFromRenderer": false' in block, "destination renderer-read guard missing")
    require('"destinationReadFromGpuImage": false' in block, "destination GPU-image guard missing")
    require('"destinationAppliedToRenderer": false' in block, "destination apply guard missing")
    formula_block = function_block(
        source,
        "private fun sourceOverEffectiveDestinationRgba",
        "private fun quantizeAlphaRound",
    )
    require("WebGpuSink.draw" not in formula_block, "candidate formula reads renderer")
    require("getPixel" not in formula_block, "candidate formula reads image pixels")
    require("destinationRgb" in formula_block, "candidate formula does not use destinationRgb")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16EffectiveDestinationCandidate(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16EffectiveDestinationCandidate"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16EffectiveDestinationCandidateJson"),
        "sampleLine": find_line(CAPTURE_PRODUCER, "private fun effectiveDestinationCandidateSample"),
        "formulaLine": find_line(CAPTURE_PRODUCER, "private fun sourceOverEffectiveDestinationRgba"),
        "classificationLine": find_line(CAPTURE_PRODUCER, "private fun effectiveDestinationCandidateClassification"),
    }


def validate_for374() -> dict[str, Any]:
    data = load_json(FOR374_ARTIFACT)
    require(data.get("linear") == "FOR-374", "FOR-374 identity changed")
    require(data.get("decision") == FOR374_REQUIRED_DECISION, "FOR-374 decision changed")
    require(data.get("classification") == FOR374_REQUIRED_CLASSIFICATION, "FOR-374 classification changed")
    require(data.get("currentResidual") == REQUIRED_CURRENT_RESIDUAL, "FOR-374 current residual changed")
    require(
        data.get("candidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL,
        "FOR-374 candidate total changed",
    )
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-374 sample count changed")
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-374 samples missing")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-374 coords changed")
        require(sample.get("coverageReadSource") == "alpha-channel-from-transparent-cpu-diagnostic-mask", "coverage source changed")
        require(sample.get("candidatePolicyRgbaReadFromRenderer") is False, "FOR-374 candidate read renderer")
        require(sample.get("candidatePolicyRgbaReadFromGpuImage") is False, "FOR-374 candidate read GPU image")
        require(sample.get("inverseDestinationUsedAsCorrection") is False, "FOR-374 inverse used as correction")
        require(sample.get("rendererAppliedCandidate") is False, "FOR-374 renderer applied candidate")
    return data


def validate_sample(sample: dict[str, Any], for374_sample: dict[str, Any], index: int) -> dict[str, Any]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    for key, value in for374_sample.items():
        require(sample.get(key) == value, f"FOR-374 field {key} diverged at sample {index}")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    source = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    inverse = sample.get("inverseDestinationEstimate")
    require(isinstance(inverse, dict), "inverseDestinationEstimate missing")
    require(inverse.get("possible") is True, "inverse destination should be available for FOR-375")
    destination_rgb = rgb(inverse.get("rgbClampedToSrgb"), "inverseDestinationEstimate.rgbClampedToSrgb")
    require(sample.get("effectiveDestinationInputSource") == "inverseDestinationEstimate.rgbClampedToSrgb", "destination source changed")
    require(sample.get("effectiveDestinationRgba") == destination_rgb + [255], "effective destination RGBA mismatch")
    effective_alpha_byte = sample.get("effectiveSourceAlphaByte")
    require(isinstance(effective_alpha_byte, int), "effectiveSourceAlphaByte missing")
    expected_candidate = source_over_effective_destination(source, effective_alpha_byte, destination_rgb)
    candidate = rgba(sample.get("effectiveDestinationCandidateRgba"), "effectiveDestinationCandidateRgba")
    require(candidate == expected_candidate, f"effective destination candidate mismatch at sample {index}")
    residual = sample_residual(reference, candidate)
    require(sample.get("effectiveDestinationCandidateResidual") == residual, "candidate residual mismatch")
    require(
        sample.get("effectiveDestinationCandidateDeltaVsCurrent") == residual - sample["currentResidual"],
        "candidate delta vs current mismatch",
    )
    require(
        sample.get("effectiveDestinationCandidateDeltaVsFor373Candidate") == residual - sample["candidateResidual"],
        "candidate delta vs FOR-373 mismatch",
    )
    require(
        sample.get("effectiveDestinationCandidateImprovesCurrent") == (residual < sample["currentResidual"]),
        "improves current flag mismatch",
    )
    require(
        sample.get("effectiveDestinationCandidateImprovesFor373Candidate") == (residual < sample["candidateResidual"]),
        "improves FOR-373 flag mismatch",
    )
    require(sample.get("effectiveDestinationReadFromRenderer") is False, "destination read from renderer")
    require(sample.get("effectiveDestinationReadFromGpuImage") is False, "destination read from GPU image")
    require(sample.get("effectiveDestinationAppliedToRenderer") is False, "destination applied to renderer")
    require(sample.get("effectiveDestinationCandidatePolicyId") == EFFECTIVE_DESTINATION_POLICY_ID, "policy id changed")
    require(sample.get("effectiveDestinationCandidateSource") == "calculated-by-diagnostic-policy", "candidate source changed")
    require(sample.get("effectiveDestinationCandidateReadFromRenderer") is False, "candidate read from renderer")
    require(sample.get("effectiveDestinationCandidateReadFromGpuImage") is False, "candidate read from GPU image")
    require(sample.get("effectiveDestinationCandidateAppliedToRenderer") is False, "candidate applied to renderer")
    return {
        "currentResidual": sample["currentResidual"],
        "for373CandidateResidual": sample["candidateResidual"],
        "effectiveDestinationCandidateResidual": residual,
    }


def validate_artifact(data: dict[str, Any], for374: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor374Decision") == FOR374_REQUIRED_DECISION, "FOR-374 decision gate changed")
    require(data.get("requiredFor374Classification") == FOR374_REQUIRED_CLASSIFICATION, "FOR-374 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("candidatePolicyId") == EFFECTIVE_DESTINATION_POLICY_ID, "candidate policy id changed")
    require(data.get("sourceCandidatePolicyId") == SOURCE_CANDIDATE_POLICY_ID, "source candidate policy id changed")
    require(data.get("candidateInputSource") == "FOR-374 preserved samples plus inverseDestinationEstimate.rgbClampedToSrgb", "input source changed")
    require(data.get("candidateRgbaSource") == "calculated-by-diagnostic-policy", "candidate source changed")
    require(data.get("candidateReadFromRenderer") is False, "candidate read from renderer")
    require(data.get("candidateReadFromGpuImage") is False, "candidate read from GPU image")
    require(data.get("candidateAppliedToRenderer") is False, "candidate applied to renderer")
    require(data.get("destinationSource") == "inverseDestinationEstimate.rgbClampedToSrgb", "destination source changed")
    require(data.get("destinationReadFromRenderer") is False, "destination read from renderer")
    require(data.get("destinationReadFromGpuImage") is False, "destination read from GPU image")
    require(data.get("destinationAppliedToRenderer") is False, "destination applied to renderer")
    require(data.get("requiredCurrentResidual") == REQUIRED_CURRENT_RESIDUAL, "required current residual changed")
    require(
        data.get("requiredFor373CandidateTotalResidual") == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL,
        "required FOR-373 candidate total changed",
    )
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    formula = data.get("candidateFormula")
    require(isinstance(formula, str), "candidate formula missing")
    for needle in ("rgbClampedToSrgb", "effectiveSourceAlphaByte", "floor", "clamped to [0,255]"):
        require(needle in formula, f"candidate formula missing {needle}")

    samples = data.get("samples")
    for374_samples = for374.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for374_samples, list) and len(for374_samples) == REQUIRED_SAMPLE_COUNT, "FOR-374 samples missing")
    current_total = 0
    for373_candidate_total = 0
    effective_destination_total = 0
    for index, sample in enumerate(samples, start=1):
        metrics = validate_sample(sample, for374_samples[index - 1], index)
        current_total += metrics["currentResidual"]
        for373_candidate_total += metrics["for373CandidateResidual"]
        effective_destination_total += metrics["effectiveDestinationCandidateResidual"]

    require(current_total == REQUIRED_CURRENT_RESIDUAL, "current residual recomputation changed")
    require(for373_candidate_total == REQUIRED_FOR373_CANDIDATE_TOTAL_RESIDUAL, "FOR-373 candidate recomputation changed")
    require(data.get("currentResidual") == current_total, "currentResidual field changed")
    require(data.get("for373CandidateTotalResidual") == for373_candidate_total, "FOR-373 candidate field changed")
    require(
        data.get("effectiveDestinationCandidateTotalResidual") == effective_destination_total,
        "effective destination total changed",
    )
    require(
        data.get("effectiveDestinationCandidateTotalDeltaVsCurrent") == effective_destination_total - current_total,
        "total delta vs current changed",
    )
    require(
        data.get("effectiveDestinationCandidateTotalDeltaVsFor373Candidate")
        == effective_destination_total - for373_candidate_total,
        "total delta vs FOR-373 changed",
    )
    require(
        data.get("classification") == expected_classification(len(samples), effective_destination_total, current_total),
        "classification rule mismatch",
    )
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{for373}` | `{destination}` | `{candidate}` | {current_residual} | {for373_residual} | {candidate_residual} | {delta_current} | {delta_for373} |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            for373=sample["candidatePolicyRgba"],
            destination=sample["effectiveDestinationRgba"],
            candidate=sample["effectiveDestinationCandidateRgba"],
            current_residual=sample["currentResidual"],
            for373_residual=sample["candidateResidual"],
            candidate_residual=sample["effectiveDestinationCandidateResidual"],
            delta_current=sample["effectiveDestinationCandidateDeltaVsCurrent"],
            delta_for373=sample["effectiveDestinationCandidateDeltaVsFor373Candidate"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-375 Effective destination candidate M60 F16

Linear: `FOR-375`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-375 teste uniquement l'axe destination effective identifie par FOR-374.
La candidate est calculee depuis `inverseDestinationEstimate.rgbClampedToSrgb`
et n'est pas appliquee au renderer/runtime.

## Formule

Ordre de calcul diagnostique:

1. `destinationRgb = inverseDestinationEstimate.rgbClampedToSrgb`.
2. `alpha = effectiveSourceAlphaByte / 255.0`.
3. Pour chaque canal source sRGB droit:
   `floor(((source / 255.0) * alpha + (destination / 255.0) * (1.0 - alpha)) * 256.0)`.
4. Chaque canal est borne dans `[0,255]`; alpha de sortie fixe a `255`.

La destination effective provient de l'inversion FOR-374, puis est bornee en
sRGB. Ce clamp explique pourquoi la candidate reduit le residuel sans expliquer
parfaitement la reference.

## Resultat

- Residuel courant preserve: `{data['currentResidual']}`
- Residuel candidate FOR-373 preserve: `{data['for373CandidateTotalResidual']}`
- Residuel candidate destination effective: `{data['effectiveDestinationCandidateTotalResidual']}`
- Delta versus courant: `{data['effectiveDestinationCandidateTotalDeltaVsCurrent']}`
- Delta versus candidate FOR-373: `{data['effectiveDestinationCandidateTotalDeltaVsFor373Candidate']}`

## Producteur

- Appel writer: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-375: `{producer_path}:{producer_lines['writerLine']}`
- Producteur JSON: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Sample diagnostique: `{producer_path}:{producer_lines['sampleLine']}`
- Formule candidate: `{producer_path}:{producer_lines['formulaLine']}`
- Classification: `{producer_path}:{producer_lines['classificationLine']}`

## Samples

| # | x | y | bande | reference RGBA | current RGBA | candidate FOR-373 | destination effective | candidate destination | residuel courant | residuel FOR-373 | residuel destination | delta courant | delta FOR-373 |
|---|---:|---:|---|---|---|---|---|---|---:|---:|---:|---:|---:|
{rows}

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- La destination effective et sa candidate restent des preuves diagnostiques calculees, jamais lues depuis le renderer ou appliquees.

## Validations

{commands}
"""


def main() -> int:
    for374 = validate_for374()
    producer_lines = validate_producer_source()
    data = load_json(ARTIFACT)
    validate_artifact(data, for374)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(f"FOR-375 effective destination candidate validated: {rel(ARTIFACT)}")
    print(f"FOR-375 report validated: {rel(REPORT)}")
    print(f"decision={data['decision']}")
    print(f"classification={data['classification']}")
    print(f"effectiveDestinationCandidateTotalResidual={data['effectiveDestinationCandidateTotalResidual']}")
    print(f"effectiveDestinationCandidateTotalDeltaVsCurrent={data['effectiveDestinationCandidateTotalDeltaVsCurrent']}")
    print(
        "effectiveDestinationCandidateTotalDeltaVsFor373Candidate="
        f"{data['effectiveDestinationCandidateTotalDeltaVsFor373Candidate']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
