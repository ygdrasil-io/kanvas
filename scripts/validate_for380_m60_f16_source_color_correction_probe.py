#!/usr/bin/env python3
"""Validate the FOR-380 M60 F16 source/color correction probe evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-380"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-correction-probe-for380/"
    "m60-f16-source-color-correction-probe-for380.json"
)
FOR379_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-effective-source-color-path-for379/"
    "m60-f16-effective-source-color-path-for379.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-380-m60-f16-source-color-correction-probe.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"

DECISION = "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED"
FOR379_DECISION = "M60_F16_EFFECTIVE_SOURCE_COLOR_PATH_RECORDED"
FOR379_CLASSIFICATION = "source-color-path-ready-for-correction"
CLASSIFICATION = "regression-detected"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-correction-experimentale-bornee-chemin-source-couleur-apres-for-379"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-379-isole-le-chemin-couleur-source-m60-f16-comme-pret-pour-correction"
)
CURRENT_RESIDUAL = 856
DIRECT_RESIDUAL = 19
CORRECTED_RESIDUAL = 816
CORRECTED_DELTA_VS_CURRENT = -40
CORRECTED_GAIN_VS_CURRENT = 40
CORRECTED_DELTA_VS_DIRECT = 797
SAMPLE_COUNT = 10
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
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test "
        "--tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
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
        "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for380-pycache python3 -m py_compile "
        "scripts/validate_for380_m60_f16_source_color_correction_probe.py"
    ),
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-380 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def channel_dict(value: Any, label: str) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be an object")
    out: dict[str, int] = {}
    for channel in ("r", "g", "b", "a"):
        raw = value.get(channel)
        require(isinstance(raw, int), f"{label}.{channel} must be int")
        out[channel] = raw
    return out


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def residual(reference: list[int], value: list[int]) -> int:
    return sum(abs(reference[index] - value[index]) for index in range(4))


def validate_source() -> None:
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    renderer = RENDERER.read_text(encoding="utf-8")
    require("private const val GPU_SUPPORT_THRESHOLD = 99.95" in source, "GPU support threshold changed")
    for needle in (
        "writeM60F16SourceColorCorrectionProbe(",
        "withM60F16SourceColorCorrectionProbe(true)",
        '"decision": "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED"',
        '"correctionAppliedByDefault": false',
        '"classification": ${classification.jsonString()}',
    ):
        require(needle in source, f"capture producer missing {needle}")
    for needle in (
        'private const val WEBGPU_M60_F16_SOURCE_COLOR_CORRECTION_PROBE_FLAG: String',
        'System.getProperty(WEBGPU_M60_F16_SOURCE_COLOR_CORRECTION_PROBE_FLAG, "false").toBoolean()',
        "m60F16SourceColorCorrectionProbe",
        "packed[colorFilterBase + 2] = if (d.m60F16SourceColorCorrectionProbe) 0f else targetColorSpaceBlendFlag()",
    ):
        require(needle in renderer, f"renderer guard missing {needle}")


def validate_for379() -> None:
    data = load_json(FOR379_ARTIFACT)
    require(data.get("linear") == "FOR-379", "FOR-379 identity changed")
    require(data.get("decision") == FOR379_DECISION, "FOR-379 decision changed")
    require(data.get("classification") == FOR379_CLASSIFICATION, "FOR-379 classification changed")
    require(data.get("currentResidual") == CURRENT_RESIDUAL, "FOR-379 current residual changed")
    require(
        data.get("directRecomposedOnWhiteTotalResidual") == DIRECT_RESIDUAL,
        "FOR-379 direct recomposed residual changed",
    )
    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == SAMPLE_COUNT, "FOR-379 sample count changed")
    coords = [(sample.get("x"), sample.get("y")) for sample in samples]
    require(coords == EXPECTED_COORDINATES, "FOR-379 coordinates changed")


def validate_artifact() -> None:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("correctionKept") is False, "correction must not be kept")
    require(data.get("correctionAppliedByDefault") is False, "correction must be disabled by default")
    require(data.get("currentResidual") == CURRENT_RESIDUAL, "current residual changed")
    require(data.get("directRecomposedOnWhiteTotalResidual") == DIRECT_RESIDUAL, "direct residual changed")
    require(data.get("correctedResidual") == CORRECTED_RESIDUAL, "corrected residual changed")
    require(data.get("correctedDeltaVsCurrent") == CORRECTED_DELTA_VS_CURRENT, "delta vs current changed")
    require(data.get("correctedGainVsCurrent") == CORRECTED_GAIN_VS_CURRENT, "gain vs current changed")
    require(
        data.get("correctedDeltaVsDirectRecomposedOnWhite") == CORRECTED_DELTA_VS_DIRECT,
        "delta vs direct changed",
    )
    preserved = data.get("preservedFor379")
    require(isinstance(preserved, dict), "preservedFor379 missing")
    require(preserved.get("currentResidual") == CURRENT_RESIDUAL, "preserved current residual changed")
    require(preserved.get("directRecomposedOnWhiteTotalResidual") == DIRECT_RESIDUAL, "preserved direct changed")
    require(preserved.get("sampleCoordinatesPreserved") is True, "coordinates not marked preserved")
    guard = data.get("fullSceneGuard")
    require(isinstance(guard, dict), "fullSceneGuard missing")
    require(guard.get("regressionDetected") is True, "full scene regression must be recorded")
    require(guard.get("uncorrectedSimilarity") == 95.91, "uncorrected similarity changed")
    require(guard.get("correctedSimilarity") == 87.06, "corrected similarity changed")
    require(guard.get("uncorrectedMismatchPixels") == 1004, "uncorrected mismatch changed")
    require(guard.get("correctedMismatchPixels") == 3181, "corrected mismatch changed")
    require(guard.get("refusalsChanged") is False, "refusal stability changed")

    samples = data.get("samples")
    require(isinstance(samples, list) and len(samples) == SAMPLE_COUNT, "sample count changed")
    require([(sample.get("x"), sample.get("y")) for sample in samples] == EXPECTED_COORDINATES, "coordinates changed")
    current_total = 0
    direct_total = 0
    corrected_total = 0
    for index, sample in enumerate(samples, start=1):
        require(sample.get("index") == index, f"sample {index} index changed")
        ref = rgba(sample.get("referenceRgba"), f"sample {index} reference")
        current = rgba(sample.get("currentRgba"), f"sample {index} current")
        direct = rgba(sample.get("directRecomposedOnWhiteRgba"), f"sample {index} direct")
        corrected = rgba(sample.get("correctedRgba"), f"sample {index} corrected")
        cur_res = residual(ref, current)
        direct_res = residual(ref, direct)
        corr_res = residual(ref, corrected)
        require(sample.get("currentResidual") == cur_res, f"sample {index} current residual mismatch")
        require(sample.get("directRecomposedOnWhiteResidual") == direct_res, f"sample {index} direct residual mismatch")
        require(sample.get("correctedResidual") == corr_res, f"sample {index} corrected residual mismatch")
        require(sample.get("correctedDeltaVsCurrent") == corr_res - cur_res, f"sample {index} delta mismatch")
        require(
            sample.get("correctedDeltaVsDirectRecomposedOnWhite") == corr_res - direct_res,
            f"sample {index} direct delta mismatch",
        )
        channel_dict(sample.get("correctedErrorByChannel"), f"sample {index} correctedErrorByChannel")
        current_total += cur_res
        direct_total += direct_res
        corrected_total += corr_res
    require(current_total == CURRENT_RESIDUAL, "summed current residual mismatch")
    require(direct_total == DIRECT_RESIDUAL, "summed direct residual mismatch")
    require(corrected_total == CORRECTED_RESIDUAL, "summed corrected residual mismatch")


def validate_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED",
        "regression-detected",
        "856",
        "816",
        "19",
        "95.91%",
        "87.06%",
        "correctionKept=false",
    ):
        require(needle in text, f"report missing {needle}")


def main() -> None:
    validate_source()
    validate_for379()
    validate_artifact()
    validate_report()
    print(f"{LINEAR_ID} validation passed: {ARTIFACT.relative_to(PROJECT_ROOT)}")


if __name__ == "__main__":
    main()
