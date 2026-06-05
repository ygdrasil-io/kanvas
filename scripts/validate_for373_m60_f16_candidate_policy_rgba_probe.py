#!/usr/bin/env python3
"""Validate the FOR-373 M60 F16 candidate policy RGBA probe evidence."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-373"
SCENE_ID = "m60-f16-candidate-policy-rgba-probe-for373"
SOURCE_SCENE_ID = "m60-f16-effective-coverage-export-for372"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-policy-rgba-probe-for373/"
    "m60-f16-candidate-policy-rgba-probe-for373.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-373-m60-f16-candidate-policy-rgba-probe.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

FOR365_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-constrained-candidate-evaluation-for365/"
    "f16-constrained-candidate-evaluation-for365.json"
)
FOR366_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/f16-positive-residual-target-inventory-for366/"
    "f16-positive-residual-target-inventory-for366.json"
)
FOR367_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join-comparable-f16-evidence-for367/"
    "m60-bounded-stroke-cap-join-comparable-f16-evidence-for367.json"
)
FOR368_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-candidate-metadata-capture-for368/"
    "m60-f16-candidate-metadata-capture-for368.json"
)
FOR369_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-candidate-coordinate-probe-for369/"
    "m60-f16-source-candidate-coordinate-probe-for369.json"
)
FOR370_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-paint-capture-extension-for370/"
    "m60-f16-source-paint-capture-extension-for370.json"
)
FOR371_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-coverage-access-audit-for371/"
    "m60-f16-effective-coverage-access-audit-for371.json"
)
FOR372_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-coverage-export-for372/"
    "m60-f16-effective-coverage-export-for372.json"
)

DECISION = "M60_F16_CANDIDATE_POLICY_RGBA_PROBE_RECORDED"
FOR372_REQUIRED_DECISION = "M60_F16_EFFECTIVE_COVERAGE_EXPORT_READY_FOR_CANDIDATE_PROBE"
FOR372_REQUIRED_CLASSIFICATION = "coverage-export-ready-for-candidate-probe"
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-candidate-policy-rgba-comparable-apres-for-372"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-372-exporte-la-couverture-aa-effective-m60-f16-depuis-un-masque-cpu-diagnostique"
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
    "candidate-policy-reduces-residual",
    "candidate-policy-neutral",
    "candidate-policy-regresses",
    "candidate-policy-blocked",
}
VALIDATION_COMMANDS = [
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for373-pycache python3 -m py_compile "
        "scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-373 validation failed: {message}")


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


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def quantize_256(value: float) -> int:
    if math.isnan(value):
        return 0
    return max(0, min(255, int(value * 256.0)))


def quantize_alpha_round(value: float) -> int:
    if math.isnan(value):
        return 0
    return max(0, min(255, int(value * 255.0 + 0.5)))


def rounded_coverage(byte_value: int) -> float:
    return round(byte_value / 255.0, 6)


def candidate_policy_rgba(paint: list[int], source_coverage_byte: int) -> list[int]:
    alpha_byte = quantize_alpha_round((paint[3] / 255.0) * (source_coverage_byte / 255.0))
    alpha = alpha_byte / 255.0
    return [
        quantize_256((paint[0] / 255.0) * alpha + (1.0 - alpha)),
        quantize_256((paint[1] / 255.0) * alpha + (1.0 - alpha)),
        quantize_256((paint[2] / 255.0) * alpha + (1.0 - alpha)),
        255,
    ]


def require_identity(data: dict[str, Any], linear: str, decision: str | None = None) -> None:
    require(data.get("linear") == linear, f"{linear} identity changed")
    if decision is not None:
        require(data.get("decision") == decision, f"{linear} decision changed")


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


def validate_prior_artifacts() -> dict[str, Any]:
    require_identity(
        load_json(FOR365_ARTIFACT),
        "FOR-365",
        "F16_CONSTRAINED_CANDIDATE_REJECTED_BY_CURRENT_GUARDS",
    )
    require_identity(load_json(FOR366_ARTIFACT), "FOR-366", "F16_POSITIVE_RESIDUAL_TARGET_INVENTORY_READY")
    for367 = load_json(FOR367_ARTIFACT)
    require_identity(for367, "FOR-367", "M60_BOUNDED_STROKE_CAP_JOIN_COMPARABLE_F16_EVIDENCE_RECORDED")
    require(for367.get("classification") == "still-missing-comparable-metadata", "FOR-367 classification changed")
    for368 = load_json(FOR368_ARTIFACT)
    require_identity(for368, "FOR-368", "M60_F16_CANDIDATE_METADATA_STILL_MISSING")
    require(for368.get("classification") == "candidate-metadata-still-missing", "FOR-368 classification changed")
    for369 = load_json(FOR369_ARTIFACT)
    require_identity(
        for369,
        "FOR-369",
        "M60_F16_SOURCE_CANDIDATE_PROBE_CAPTURE_PATH_STILL_MISSING_SOURCE_METADATA",
    )
    require(for369.get("classification") == "capture-path-still-missing-source-metadata", "FOR-369 changed")
    for370 = load_json(FOR370_ARTIFACT)
    require_identity(for370, "FOR-370", "M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE")
    require(for370.get("classification") == "candidate-probe-refused-by-ambiguous-coverage", "FOR-370 changed")
    for371 = load_json(FOR371_ARTIFACT)
    require_identity(for371, "FOR-371", "M60_F16_EFFECTIVE_COVERAGE_ACCESS_REQUIRES_NEW_EXPORT_POINT")
    require(for371.get("classification") == "coverage-access-requires-new-export-point", "FOR-371 changed")
    for372 = load_json(FOR372_ARTIFACT)
    require_identity(for372, "FOR-372", FOR372_REQUIRED_DECISION)
    require(for372.get("classification") == FOR372_REQUIRED_CLASSIFICATION, "FOR-372 classification changed")
    require(for372.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-372 current residual changed")
    require(for372.get("computedResidual") == REQUIRED_RESIDUAL, "FOR-372 computed residual changed")
    require(for372.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-372 sample count changed")
    require(for372.get("candidatePolicyRgbaProduced") is False, "FOR-372 unexpectedly produced candidate")
    require(for372.get("candidatePolicyAppliedToRenderer") is False, "FOR-372 applied candidate")
    samples = for372.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-372 samples changed")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-372 coords changed")
        require(sample.get("candidatePolicyRgba") is None, "FOR-372 candidate no longer null")
        require(sample.get("rendererAppliedCandidate") is False, "FOR-372 applied candidate")
    return for372


def validate_producer_source() -> dict[str, int]:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    block = function_block(
        source,
        "private fun m60F16CandidatePolicyRgbaProbeJson",
        "private fun m60F16EffectiveCoverageExportJson",
    )
    require("WebGpuSink.draw" not in block, "candidate probe block reads from renderer")
    require("candidatePolicyRgbaReadFromRenderer\": false" in block, "renderer read guard missing")
    require("candidatePolicyRgbaReadFromGpuImage\": false" in block, "GPU image read guard missing")
    require("candidatePolicyRgbaAppliedToRenderer\": false" in block, "renderer apply guard missing")
    formula_block = function_block(source, "private fun candidatePolicyRgba", "private fun quantizeAlphaRound")
    require("coverageMask.getPixel" not in formula_block, "candidate formula reads coverage image directly")
    require("WebGpuSink.draw" not in formula_block, "candidate formula reads renderer")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16CandidatePolicyRgbaProbe(residualStats, adapter)"),
        "writerLine": find_line(CAPTURE_PRODUCER, "private fun writeM60F16CandidatePolicyRgbaProbe"),
        "jsonProducerLine": find_line(CAPTURE_PRODUCER, "private fun m60F16CandidatePolicyRgbaProbeJson"),
        "formulaLine": find_line(CAPTURE_PRODUCER, "private fun candidatePolicyRgba(sourceColor: Int, sourceCoverageByte: Int)"),
        "rendererReadGuardLine": find_line(CAPTURE_PRODUCER, '"candidatePolicyRgbaReadFromRenderer": false'),
        "rendererApplyGuardLine": find_line(CAPTURE_PRODUCER, '"candidatePolicyRgbaAppliedToRenderer": false'),
    }


def expected_classification(candidate_total: int, sample_count: int) -> str:
    if sample_count != REQUIRED_SAMPLE_COUNT:
        return "candidate-policy-blocked"
    if candidate_total < REQUIRED_RESIDUAL:
        return "candidate-policy-reduces-residual"
    if candidate_total == REQUIRED_RESIDUAL:
        return "candidate-policy-neutral"
    return "candidate-policy-regresses"


def validate_sample(sample: dict[str, Any], for372_sample: dict[str, Any], index: int) -> tuple[int, int]:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    for key in (
        "referenceRgba",
        "currentRgba",
        "gpuRgba",
        "paintSourceRgba",
        "sourceCoverageByte",
        "sourceCoverage",
        "effectiveSourceAlphaByte",
        "effectiveSourceAlpha",
    ):
        require(sample.get(key) == for372_sample.get(key), f"{key} diverged from FOR-372 at sample {index}")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    paint = rgba(sample.get("paintSourceRgba"), "paintSourceRgba")
    source_coverage_byte = sample.get("sourceCoverageByte")
    require(isinstance(source_coverage_byte, int), "sourceCoverageByte missing")
    require(0 <= source_coverage_byte <= 255, "sourceCoverageByte out of range")
    require(sample.get("sourceCoverage") == rounded_coverage(source_coverage_byte), "sourceCoverage rounding changed")
    require(sample.get("effectiveSourceAlphaByte") == source_coverage_byte, "effective alpha byte changed")
    require(sample.get("effectiveSourceAlpha") == rounded_coverage(source_coverage_byte), "effective alpha changed")
    current_residual = sample_residual(reference, current)
    require(sample.get("sampleResidual") == current_residual, "sampleResidual changed")
    expected_candidate = candidate_policy_rgba(paint, source_coverage_byte)
    candidate = rgba(sample.get("candidatePolicyRgba"), "candidatePolicyRgba")
    require(candidate == expected_candidate, f"candidatePolicyRgba formula mismatch at sample {index}")
    candidate_residual = sample_residual(reference, candidate)
    require(sample.get("candidateResidual") == candidate_residual, "candidate residual changed")
    require(
        sample.get("candidateResidualDeltaVsCurrent") == candidate_residual - current_residual,
        "candidate delta changed",
    )
    require(sample.get("candidateImprovesSample") == (candidate_residual < current_residual), "improve flag changed")
    require(sample.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(
        sample.get("candidatePolicyRgbaSource") == "calculated-by-diagnostic-policy",
        "candidate source is not diagnostic formula",
    )
    require(sample.get("candidatePolicyRgbaReadFromRenderer") is False, "candidate read from renderer")
    require(sample.get("candidatePolicyRgbaReadFromGpuImage") is False, "candidate read from GPU image")
    require(sample.get("rendererAppliedCandidate") is False, "candidate applied to renderer")
    require(sample.get("coverageReconstructedFromRgbaDeltas") is False, "coverage reconstructed from deltas")
    require(sample.get("referenceCurrentRgbaUsedForCoverage") is False, "reference/current used for coverage")
    require(sample.get("sampleDeltaRgbaUsedForCoverage") is False, "sample delta used for coverage")
    return current_residual, candidate_residual


def validate_artifact(data: dict[str, Any], for372: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor372Decision") == FOR372_REQUIRED_DECISION, "FOR-372 decision gate changed")
    require(data.get("requiredFor372Classification") == FOR372_REQUIRED_CLASSIFICATION, "FOR-372 class gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") in ALLOWED_CLASSIFICATIONS, "classification is not stable")
    require(data.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(data.get("candidatePolicyRgbaSource") == "calculated-by-diagnostic-policy", "candidate source changed")
    require(data.get("candidatePolicyRgbaReadFromRenderer") is False, "candidate read from renderer")
    require(data.get("candidatePolicyRgbaReadFromGpuImage") is False, "candidate read from GPU image")
    require(data.get("candidatePolicyRgbaAppliedToRenderer") is False, "candidate applied to renderer")
    require(data.get("rendererAppliedCandidate") is False, "renderer applied candidate")
    require(data.get("requiredCurrentResidual") == REQUIRED_RESIDUAL, "required current residual changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")

    samples = data.get("samples")
    for372_samples = for372.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "samples missing")
    require(isinstance(for372_samples, list) and len(for372_samples) == REQUIRED_SAMPLE_COUNT, "FOR-372 samples missing")
    current_total = 0
    candidate_total = 0
    for index, sample in enumerate(samples, start=1):
        current, candidate = validate_sample(sample, for372_samples[index - 1], index)
        current_total += current
        candidate_total += candidate
    require(current_total == REQUIRED_RESIDUAL, "current residual recomputation changed")
    require(data.get("currentResidual") == current_total, "currentResidual field changed")
    require(data.get("candidateTotalResidual") == candidate_total, "candidate total changed")
    require(
        data.get("candidateTotalResidualDeltaVsCurrent") == candidate_total - REQUIRED_RESIDUAL,
        "candidate total delta changed",
    )
    require(
        data.get("classification") == expected_classification(candidate_total, len(samples)),
        "classification does not match residual rule",
    )

    formula = data.get("candidatePolicyFormula")
    require(isinstance(formula, str), "formula missing")
    for needle in ("alphaByte=round", "sourceCoverageByte", "floor", "clamped to [0,255]"):
        require(needle in formula, f"formula missing {needle}")
    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, int]) -> str:
    rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{paint}` | {coverage_byte} | {coverage:.6f} | `{candidate}` | {sample_residual} | {candidate_residual} | {delta} | {improves} |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            paint=sample["paintSourceRgba"],
            coverage_byte=sample["sourceCoverageByte"],
            coverage=sample["sourceCoverage"],
            candidate=sample["candidatePolicyRgba"],
            sample_residual=sample["sampleResidual"],
            candidate_residual=sample["candidateResidual"],
            delta=sample["candidateResidualDeltaVsCurrent"],
            improves=sample["candidateImprovesSample"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-373 Candidate policy RGBA M60 F16

Linear: `FOR-373`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-373 calcule une candidate diagnostique par sample M60 depuis les champs
FOR-372. La candidate n'est pas appliquee au renderer et n'est pas lue depuis
une image GPU.

## Formule

Politique: `{data['candidatePolicyId']}`.

Ordre de calcul:

1. `alphaByte = round((paintAlpha / 255.0) * (sourceCoverageByte / 255.0) * 255)`.
2. `alpha = alphaByte / 255.0`.
3. Pour chaque canal source sRGB droit: `floor(((source / 255.0) * alpha + (1.0 - alpha)) * 256.0)`.
4. Chaque canal est borne dans `[0,255]`; alpha de sortie fixe a `255`.

## Resultat

- Residuel courant: `{data['currentResidual']}`
- Residuel candidate: `{data['candidateTotalResidual']}`
- Delta candidate versus courant: `{data['candidateTotalResidualDeltaVsCurrent']}`

## Producteur

- Appel writer: `{producer_path}:{producer_lines['writerCallLine']}`
- Writer FOR-373: `{producer_path}:{producer_lines['writerLine']}`
- Producteur JSON: `{producer_path}:{producer_lines['jsonProducerLine']}`
- Formule candidate: `{producer_path}:{producer_lines['formulaLine']}`
- Garde lecture renderer: `{producer_path}:{producer_lines['rendererReadGuardLine']}`
- Garde application renderer: `{producer_path}:{producer_lines['rendererApplyGuardLine']}`

## Samples

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | coverage byte | coverage | candidatePolicyRgba | residuel courant | residuel candidate | delta | ameliore |
|---|---:|---:|---|---|---|---|---:|---:|---|---:|---:|---:|---|
{rows}

## Non-objectifs preserves

- Aucun changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Aucun changement de fallback, Kadre, F16 premul/blend, score, seuil ou promotion.
- La candidate reste une preuve diagnostique et n'est pas appliquee.

## Validations

{commands}
"""


def main() -> int:
    for372 = validate_prior_artifacts()
    producer_lines = validate_producer_source()
    data = load_json(ARTIFACT)
    validate_artifact(data, for372)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(f"FOR-373 candidate policy RGBA probe validated: {rel(ARTIFACT)}")
    print(f"FOR-373 report validated: {rel(REPORT)}")
    print(f"decision={data['decision']}")
    print(f"classification={data['classification']}")
    print(f"candidateTotalResidual={data['candidateTotalResidual']}")
    print(f"candidateTotalResidualDeltaVsCurrent={data['candidateTotalResidualDeltaVsCurrent']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
