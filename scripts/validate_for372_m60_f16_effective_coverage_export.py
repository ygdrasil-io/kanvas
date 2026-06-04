#!/usr/bin/env python3
"""Validate the FOR-372 M60 F16 effective coverage export evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-372"
SCENE_ID = "m60-f16-effective-coverage-export-for372"
SOURCE_SCENE_ID = "m60-bounded-stroke-cap-join"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-coverage-export-for372/"
    "m60-f16-effective-coverage-export-for372.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-372-m60-f16-effective-coverage-export.md"
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

DECISION = "M60_F16_EFFECTIVE_COVERAGE_EXPORT_READY_FOR_CANDIDATE_PROBE"
CLASSIFICATION = "coverage-export-ready-for-candidate-probe"
FOR371_REQUIRED_DECISION = "M60_F16_EFFECTIVE_COVERAGE_ACCESS_REQUIRES_NEW_EXPORT_POINT"
FOR371_REQUIRED_CLASSIFICATION = "coverage-access-requires-new-export-point"
FOR370_REQUIRED_DECISION = "M60_F16_SOURCE_PAINT_CAPTURE_EXTENSION_REFUSED_BY_AMBIGUOUS_COVERAGE"
FOR370_REQUIRED_CLASSIFICATION = "candidate-probe-refused-by-ambiguous-coverage"
F16_POLICY_ID = "straight_srgb_quantized_alpha_src_over_white"
CPU_OWNER_ROUTE = "cpu.coverage.stroke-cap-join-oracle"
CPU_COVERAGE_PLAN = (
    "PathStrokeCoverage(openPolyline,aa=true,strokeWidth=10,"
    "capJoinMatrix=butt-bevel+round-round+square-bevel)"
)
REQUIRED_RESIDUAL = 856
REQUIRED_SAMPLE_COUNT = 10
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
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-export-diagnostique-couverture-aa-effective-apres-for-371"
)
SOURCE_FINDING = "global/kanvas/findings/for-371-identifie-le-point-dexport-couverture-m60-f16-requis"
PROVENANCE_NEEDLES = (
    "cpu.coverage.stroke-cap-join-oracle",
    "PathStrokeCoverage",
    "diagnostic transparent GM alpha mask",
)

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    (
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for372-pycache python3 -m py_compile "
        "scripts/validate_for372_m60_f16_effective_coverage_export.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
    (
        "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-372 validation failed: {message}")


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
    result: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        result.append(channel)
    return result


def sample_residual(reference: list[int], current: list[int]) -> int:
    return sum(abs(reference[index] - current[index]) for index in range(4))


def find_line(path: Path, needle: str) -> int:
    source = path.read_text(encoding="utf-8")
    for index, line in enumerate(source.splitlines(), start=1):
        if needle in line:
            return index
    fail(f"{rel(path)} no longer contains: {needle}")


def rounded_coverage(byte_value: int) -> float:
    return round(byte_value / 255.0, 6)


def require_identity(data: dict[str, Any], linear: str, decision: str | None = None) -> None:
    require(data.get("linear") == linear, f"{linear} identity changed")
    if decision is not None:
        require(data.get("decision") == decision, f"{linear} decision changed")


def validate_prior_artifacts() -> tuple[dict[str, Any], dict[str, Any]]:
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
    require_identity(for370, "FOR-370", FOR370_REQUIRED_DECISION)
    require(for370.get("classification") == FOR370_REQUIRED_CLASSIFICATION, "FOR-370 classification changed")
    require(for370.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-370 residual changed")
    require(for370.get("computedResidual") == REQUIRED_RESIDUAL, "FOR-370 computed residual changed")
    require(for370.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-370 sample count changed")
    require(for370.get("sourcePaintLinkedToSamples") is True, "FOR-370 source paint link missing")

    for371 = load_json(FOR371_ARTIFACT)
    require_identity(for371, "FOR-371", FOR371_REQUIRED_DECISION)
    require(for371.get("classification") == FOR371_REQUIRED_CLASSIFICATION, "FOR-371 classification changed")
    require(for371.get("currentResidual") == REQUIRED_RESIDUAL, "FOR-371 residual changed")
    require(for371.get("computedResidual") == REQUIRED_RESIDUAL, "FOR-371 computed residual changed")
    require(for371.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "FOR-371 sample count changed")
    access = for371.get("coverageAccess")
    require(isinstance(access, dict), "FOR-371 coverageAccess missing")
    require(access.get("requiresNewExportPoint") is True, "FOR-371 no longer requires export")
    require(access.get("coverageReconstructedFromRgbaDeltas") is False, "FOR-371 reconstructed coverage")
    samples = for371.get("samples")
    require(isinstance(samples, list) and len(samples) == REQUIRED_SAMPLE_COUNT, "FOR-371 samples changed")
    for index, sample in enumerate(samples, start=1):
        require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "FOR-371 coords changed")
        require(sample.get("sourceCoverage") is None, "FOR-371 unexpectedly exports coverage")
        require(sample.get("effectiveSourceAlpha") is None, "FOR-371 unexpectedly exports alpha")
    return for370, for371


def validate_producer_source() -> dict[str, Any]:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    return {
        "writerCallLine": find_line(CAPTURE_PRODUCER, "writeM60F16EffectiveCoverageExport(residualStats, adapter)"),
        "maskGmLine": find_line(CAPTURE_PRODUCER, "private class BoundedStrokeCapJoinCoverageMaskGM"),
        "transparentBackgroundLine": find_line(CAPTURE_PRODUCER, "setBGColor(0x00000000)"),
        "maskRenderLine": find_line(CAPTURE_PRODUCER, "TestUtils.runGmTest(BoundedStrokeCapJoinCoverageMaskGM())"),
        "coverageReadLine": find_line(CAPTURE_PRODUCER, "coverageMask.getPixel(sample.x, sample.y)"),
        "provenanceLine": find_line(CAPTURE_PRODUCER, "diagnostic transparent GM alpha mask"),
    }


def validate_sample(sample: dict[str, Any], index: int, for370_sample: dict[str, Any]) -> int:
    require(sample.get("index") == index, "sample index changed")
    require((sample.get("x"), sample.get("y")) == EXPECTED_COORDINATES[index - 1], "coordinate changed")
    require(sample.get("x") == for370_sample.get("x") and sample.get("y") == for370_sample.get("y"), "FOR-370 coord mismatch")
    reference = rgba(sample.get("referenceRgba"), "referenceRgba")
    current = rgba(sample.get("currentRgba"), "currentRgba")
    require(sample.get("gpuRgba") == current, "gpu/current RGBA diverged")
    require(sample.get("referenceRgba") == for370_sample.get("referenceRgba"), "reference RGBA diverged from FOR-370")
    require(sample.get("currentRgba") == for370_sample.get("currentRgba"), "current RGBA diverged from FOR-370")
    residual = sample_residual(reference, current)
    require(sample.get("sampleResidual") == residual, "sample residual changed")

    source_coverage_byte = sample.get("sourceCoverageByte")
    alpha_byte = sample.get("effectiveSourceAlphaByte")
    require(isinstance(source_coverage_byte, int), "sourceCoverageByte missing")
    require(isinstance(alpha_byte, int), "effectiveSourceAlphaByte missing")
    require(0 <= source_coverage_byte <= 255, "sourceCoverageByte out of range")
    require(alpha_byte == source_coverage_byte, "effective alpha byte must equal opaque-source coverage")
    require(sample.get("sourceCoverage") == rounded_coverage(source_coverage_byte), "sourceCoverage rounding changed")
    require(sample.get("effectiveSourceAlpha") == rounded_coverage(source_coverage_byte), "effectiveSourceAlpha changed")
    require(sample.get("sourceCoverageStatus") == "exported-from-cpu-diagnostic-mask-alpha", "coverage status changed")
    require(
        sample.get("effectiveSourceAlphaStatus")
        == "opaque-source-paint-alpha-multiplied-by-exported-coverage",
        "effective alpha status changed",
    )
    provenance = sample.get("coverageProvenance")
    require(isinstance(provenance, str), "coverage provenance missing")
    for needle in PROVENANCE_NEEDLES:
        require(needle in provenance, f"coverage provenance missing {needle}")
    require(sample.get("coverageReadSource") == "alpha-channel-from-transparent-cpu-diagnostic-mask", "read source changed")
    require(sample.get("coverageReconstructedFromRgbaDeltas") is False, "sample reconstructs coverage from RGBA")
    require(sample.get("referenceCurrentRgbaUsedForCoverage") is False, "reference/current used for coverage")
    require(sample.get("sampleDeltaRgbaUsedForCoverage") is False, "sample delta used for coverage")
    require(sample.get("candidatePolicyRgba") is None, "candidatePolicyRgba should not be produced")
    require(sample.get("candidatePolicyRgbaStatus") == "not-produced-export-only", "candidate status changed")
    require(sample.get("rendererAppliedCandidate") is False, "candidate was applied to renderer")
    return residual


def validate_artifact(data: dict[str, Any], for370: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sceneId") == SCENE_ID, "scene id changed")
    require(data.get("sourceSceneId") == SOURCE_SCENE_ID, "source scene changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor371Decision") == FOR371_REQUIRED_DECISION, "FOR-371 decision gate changed")
    require(data.get("requiredFor371Classification") == FOR371_REQUIRED_CLASSIFICATION, "FOR-371 classification gate changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sampleCount") == REQUIRED_SAMPLE_COUNT, "sample count changed")
    require(data.get("currentResidual") == REQUIRED_RESIDUAL, "current residual changed")
    require(data.get("candidatePolicyId") == F16_POLICY_ID, "candidate policy id changed")
    require(data.get("candidatePolicyRgbaProduced") is False, "candidate was produced")
    require(data.get("candidatePolicyAppliedToRenderer") is False, "candidate was applied")

    export = data.get("coverageExport")
    require(isinstance(export, dict), "coverageExport missing")
    require(export.get("classification") == CLASSIFICATION, "coverageExport classification changed")
    require(export.get("readyForCandidateProbe") is True, "coverage export not ready")
    require(export.get("sourceCoverageAvailableForAllSamples") is True, "coverage not available for all samples")
    require(export.get("effectiveSourceAlphaByteEqualsCoverageByte") is True, "alpha byte equivalence changed")
    require(export.get("effectiveSourceAlphaEqualsCoverage") is True, "alpha float equivalence changed")
    require(export.get("coverageOwnerRoute") == CPU_OWNER_ROUTE, "coverage owner route changed")
    require(export.get("coveragePlan") == CPU_COVERAGE_PLAN, "coverage plan changed")
    require(export.get("coverageReadSource") == "alpha-channel-from-transparent-cpu-diagnostic-mask", "coverage read source changed")
    require(export.get("coverageReconstructedFromRgbaDeltas") is False, "coverage reconstructed from RGBA")
    require(export.get("referenceCurrentRgbaUsedForCoverage") is False, "reference/current used for coverage")
    require(export.get("sampleDeltaRgbaUsedForCoverage") is False, "sample delta used for coverage")
    provenance = export.get("coverageProvenance")
    require(isinstance(provenance, str), "top-level provenance missing")
    for needle in PROVENANCE_NEEDLES:
        require(needle in provenance, f"top-level provenance missing {needle}")

    samples = data.get("samples")
    require(isinstance(samples, list), "samples missing")
    require(len(samples) == REQUIRED_SAMPLE_COUNT, "sample count changed")
    for370_samples = for370.get("samples")
    require(isinstance(for370_samples, list), "FOR-370 samples missing")
    computed_residual = 0
    for index, sample in enumerate(samples, start=1):
        computed_residual += validate_sample(sample, index, for370_samples[index - 1])
    require(computed_residual == REQUIRED_RESIDUAL, "computed residual changed")
    require(data.get("computedResidual") == REQUIRED_RESIDUAL, "computedResidual field changed")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def report_text(data: dict[str, Any], producer_lines: dict[str, Any]) -> str:
    rows = "\n".join(
        "| {index} | {x} | {y} | `{band}` | `{reference}` | `{current}` | `{paint}` | {coverage_byte} | {coverage:.6f} | {alpha_byte} | {alpha:.6f} |".format(
            index=sample["index"],
            x=sample["x"],
            y=sample["y"],
            band=sample["strokeBand"],
            reference=sample["referenceRgba"],
            current=sample["currentRgba"],
            paint=sample["paintSourceRgba"],
            coverage_byte=sample["sourceCoverageByte"],
            coverage=sample["sourceCoverage"],
            alpha_byte=sample["effectiveSourceAlphaByte"],
            alpha=sample["effectiveSourceAlpha"],
        )
        for sample in data["samples"]
    )
    commands = "\n".join(f"- `{command}`" for command in VALIDATION_COMMANDS)
    producer_path = rel(CAPTURE_PRODUCER)
    return f"""# FOR-372 Export couverture effective M60 F16

Linear: `FOR-372`

Decision: `{data['decision']}`

Classification: `{data['classification']}`

FOR-372 ajoute un export de preuve diagnostique uniquement. Il conserve les
10 coordonnees M60 et le residuel `{data['currentResidual']}`, puis lit la
couverture depuis le canal alpha d'un GM de masque CPU transparent.

## Provenance

- Route proprietaire: `{data['coverageExport']['coverageOwnerRoute']}`
- Plan: `{data['coverageExport']['coveragePlan']}`
- Masque: `{data['coverageExport']['diagnosticMaskScene']}`
- Lecture: `{data['coverageExport']['coverageReadSource']}`
- Appel writer: `{producer_path}:{producer_lines['writerCallLine']}`
- GM masque transparent: `{producer_path}:{producer_lines['maskGmLine']}` et `{producer_path}:{producer_lines['transparentBackgroundLine']}`
- Lecture alpha du masque: `{producer_path}:{producer_lines['coverageReadLine']}`

La couverture n'est pas reconstruite depuis `referenceRgba`, `currentRgba`,
`gpuRgba` ou les deltas RGBA. `candidatePolicyRgba` reste non produit et non
applique au renderer.

## Samples exportes

| # | x | y | bande | reference RGBA | current/gpu RGBA | source paint RGBA | coverage byte | coverage | alpha byte | effective alpha |
|---|---:|---:|---|---|---|---|---:|---:|---:|---:|
{rows}

## Non-objectifs preserves

- Pas de changement renderer/runtime, GPU/WGSL, geometrie ou couverture de production.
- Pas de fallback, Kadre, F16 premul/blend, score, seuil ou promotion modifie.
- Pas de `candidatePolicyRgba` applique au renderer.
- Pas de reconstruction de couverture depuis les deltas RGBA.

## Validations

{commands}
"""


def main() -> int:
    for370, _for371 = validate_prior_artifacts()
    producer_lines = validate_producer_source()
    data = load_json(ARTIFACT)
    validate_artifact(data, for370)
    write_if_changed(REPORT, report_text(data, producer_lines))
    print(f"FOR-372 effective coverage export validated: {rel(ARTIFACT)}")
    print(f"FOR-372 report validated: {rel(REPORT)}")
    print(f"decision={DECISION}")
    print(f"classification={CLASSIFICATION}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
