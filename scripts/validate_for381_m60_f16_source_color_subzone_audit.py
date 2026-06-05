#!/usr/bin/env python3
"""Validate the FOR-381 M60 F16 source/color sub-zone audit evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-381"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-subzone-audit-for381/"
    "m60-f16-source-color-subzone-audit-for381.json"
)
FOR380_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-correction-probe-for380/"
    "m60-f16-source-color-correction-probe-for380.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-381-m60-f16-source-color-subzone-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED"
CLASSIFICATION = "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition"
FOR380_DECISION = "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED"
FOR380_CLASSIFICATION = "regression-detected"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-caracterisation-sous-zone-apres-regression-for-380"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-380-refuse-la-correction-source-couleur-m60-f16-au-niveau-draw-entier-car-elle-regresse-la-scene"
)
CURRENT_SAMPLE_RESIDUAL = 856
PROBE_SAMPLE_RESIDUAL = 816
DIRECT_SAMPLE_RESIDUAL = 19
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
EXPECTED_SET_TOTALS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148),
    "improved": (8, 734, 669, -65),
    "regressed": (3171, 1274, 230487, 229213),
    "unchanged": (21397, 6, 6, 0),
    "for379Critical": (10, 856, 816, -40),
}
EXPECTED_FULL_SCENE = {
    "uncorrectedSimilarity": 95.91,
    "correctedSimilarity": 87.06,
    "uncorrectedMismatchPixels": 1004,
    "correctedMismatchPixels": 3181,
    "uncorrectedGreaterThanEightPixels": 10,
    "correctedGreaterThanEightPixels": 3164,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-381 validation failed: {message}")


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


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    out: list[int] = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def channel_dict(value: Any, label: str) -> dict[str, int]:
    require(isinstance(value, dict), f"{label} must be an object")
    out: dict[str, int] = {}
    for channel in ("r", "g", "b", "a"):
        raw = value.get(channel)
        require(isinstance(raw, int), f"{label}.{channel} must be int")
        out[channel] = raw
    return out


def bounds(value: Any, label: str) -> None:
    require(value is None or isinstance(value, dict), f"{label} must be null or object")
    if value is None:
        return
    for key in ("left", "top", "right", "bottom"):
        require(isinstance(value.get(key), int), f"{label}.{key} must be int")
    require(value["left"] <= value["right"], f"{label} x bounds inverted")
    require(value["top"] <= value["bottom"], f"{label} y bounds inverted")


def residual(reference: list[int], value: list[int]) -> int:
    return sum(abs(reference[index] - value[index]) for index in range(4))


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16SourceColorSubzoneAudit(",
        "m60F16SourceColorSubzoneAuditJson(",
        '"decision": "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED"',
        '"auditDoesNotApplyRendererChange": true',
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_for380() -> None:
    data = load_json(FOR380_ARTIFACT)
    require(data.get("linear") == "FOR-380", "FOR-380 identity changed")
    require(data.get("decision") == FOR380_DECISION, "FOR-380 decision changed")
    require(data.get("classification") == FOR380_CLASSIFICATION, "FOR-380 classification changed")
    require(data.get("currentResidual") == CURRENT_SAMPLE_RESIDUAL, "FOR-380 current residual changed")
    require(data.get("correctedResidual") == PROBE_SAMPLE_RESIDUAL, "FOR-380 probe residual changed")
    require(
        data.get("directRecomposedOnWhiteTotalResidual") == DIRECT_SAMPLE_RESIDUAL,
        "FOR-380 direct residual changed",
    )
    require(data.get("correctionKept") is False, "FOR-380 correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "FOR-380 correction must remain disabled")


def validate_set(name: str, value: Any) -> dict[str, Any]:
    require(isinstance(value, dict), f"set {name} must be object")
    expected = EXPECTED_SET_TOTALS[name]
    require(value.get("count") == expected[0], f"{name} count changed")
    require(value.get("beforeResidual") == expected[1], f"{name} before residual changed")
    require(value.get("afterResidual") == expected[2], f"{name} after residual changed")
    require(value.get("deltaVsCurrent") == expected[3], f"{name} delta changed")
    require(value.get("gainVsCurrent") == -expected[3], f"{name} gain changed")
    before = channel_dict(value.get("beforeErrorByChannel"), f"{name}.beforeErrorByChannel")
    after = channel_dict(value.get("afterErrorByChannel"), f"{name}.afterErrorByChannel")
    delta = channel_dict(value.get("afterMinusBeforeErrorByChannel"), f"{name}.afterMinusBeforeErrorByChannel")
    channel_dict(value.get("probeMinusCurrentRgbaTotals"), f"{name}.probeMinusCurrentRgbaTotals")
    require(sum(before.values()) == expected[1], f"{name} before channel total mismatch")
    require(sum(after.values()) == expected[2], f"{name} after channel total mismatch")
    for channel in ("r", "g", "b", "a"):
        require(after[channel] - before[channel] == delta[channel], f"{name}.{channel} delta mismatch")
    bounds(value.get("bounds"), f"{name}.bounds")
    bands = value.get("bandDistribution")
    require(isinstance(bands, list), f"{name}.bandDistribution must be list")
    require(sum(band.get("count", 0) for band in bands if isinstance(band, dict)) == expected[0], f"{name} band counts mismatch")
    require(
        sum(band.get("beforeResidual", 0) for band in bands if isinstance(band, dict)) == expected[1],
        f"{name} band before residual mismatch",
    )
    require(
        sum(band.get("afterResidual", 0) for band in bands if isinstance(band, dict)) == expected[2],
        f"{name} band after residual mismatch",
    )
    for band in bands:
        require(isinstance(band, dict), f"{name} band must be object")
        require(band.get("strokeBand") in {"butt-bevel", "round-round", "square-bevel"}, f"{name} bad band id")
        require(isinstance(band.get("cap"), str), f"{name} band cap missing")
        require(isinstance(band.get("join"), str), f"{name} band join missing")
        channel_dict(band.get("afterMinusBeforeErrorByChannel"), f"{name}.band.afterMinusBeforeErrorByChannel")
        bounds(band.get("bounds"), f"{name}.band.bounds")
    return value


def validate_sample(sample: Any, label: str, *, expect_direct: bool) -> tuple[int, int, int, int]:
    require(isinstance(sample, dict), f"{label} must be object")
    require(isinstance(sample.get("x"), int) and isinstance(sample.get("y"), int), f"{label} coordinates missing")
    reference = rgba(sample.get("referenceRgba"), f"{label}.referenceRgba")
    current = rgba(sample.get("currentRgba"), f"{label}.currentRgba")
    probe = rgba(sample.get("probeRgba"), f"{label}.probeRgba")
    current_residual = residual(reference, current)
    probe_residual = residual(reference, probe)
    require(sample.get("currentResidual") == current_residual, f"{label} current residual mismatch")
    require(sample.get("probeResidual") == probe_residual, f"{label} probe residual mismatch")
    require(sample.get("deltaVsCurrent") == probe_residual - current_residual, f"{label} delta mismatch")
    require(sample.get("gainVsCurrent") == current_residual - probe_residual, f"{label} gain mismatch")
    current_error = channel_dict(sample.get("currentErrorByChannel"), f"{label}.currentErrorByChannel")
    probe_error = channel_dict(sample.get("probeErrorByChannel"), f"{label}.probeErrorByChannel")
    error_delta = channel_dict(sample.get("probeMinusCurrentErrorByChannel"), f"{label}.probeMinusCurrentErrorByChannel")
    rgba_delta = channel_dict(sample.get("probeMinusCurrentRgba"), f"{label}.probeMinusCurrentRgba")
    for index, channel in enumerate(("r", "g", "b", "a")):
        require(current_error[channel] == abs(reference[index] - current[index]), f"{label}.{channel} current error mismatch")
        require(probe_error[channel] == abs(reference[index] - probe[index]), f"{label}.{channel} probe error mismatch")
        require(error_delta[channel] == probe_error[channel] - current_error[channel], f"{label}.{channel} error delta mismatch")
        require(rgba_delta[channel] == probe[index] - current[index], f"{label}.{channel} rgba delta mismatch")
    distance = sample.get("nearestFor379CriticalManhattanDistance")
    require(isinstance(distance, int) and distance >= 0, f"{label} distance missing")
    if expect_direct:
        direct = rgba(sample.get("directRecomposedOnWhiteRgba"), f"{label}.directRecomposedOnWhiteRgba")
        direct_residual = residual(reference, direct)
        require(
            sample.get("directRecomposedOnWhiteResidual") == direct_residual,
            f"{label} direct residual mismatch",
        )
        direct_error = channel_dict(sample.get("directRecomposedErrorByChannel"), f"{label}.directRecomposedErrorByChannel")
        require(sum(direct_error.values()) == direct_residual, f"{label} direct channel total mismatch")
        require(
            sample.get("probeDeltaVsDirectRecomposedOnWhite") == probe_residual - direct_residual,
            f"{label} direct delta mismatch",
        )
        require(sample.get("for379SampleClassification") == "source-color-ready-for-correction", f"{label} bad FOR-379 class")
        return sample["x"], sample["y"], current_residual, direct_residual
    return sample["x"], sample["y"], current_residual, 0


def validate_artifact() -> None:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("requiredFor380Decision") == FOR380_DECISION, "FOR-380 decision requirement changed")
    require(data.get("requiredFor380Classification") == FOR380_CLASSIFICATION, "FOR-380 class requirement changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory reference changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding reference changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionKept") is False, "correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")

    guard = data.get("fullSceneGuard")
    require(isinstance(guard, dict), "fullSceneGuard missing")
    for key, expected in EXPECTED_FULL_SCENE.items():
        require(guard.get(key) == expected, f"fullSceneGuard.{key} changed")
    require(guard.get("refusalsChanged") is False, "refusal stability changed")

    inference = data.get("bandInference")
    require(isinstance(inference, dict), "bandInference missing")
    require(inference.get("coverageMembershipProven") is False, "band inference overclaims coverage")

    sets = data.get("sets")
    require(isinstance(sets, dict), "sets missing")
    parsed_sets = {name: validate_set(name, sets.get(name)) for name in EXPECTED_SET_TOTALS}
    require(
        parsed_sets["improved"]["count"] + parsed_sets["regressed"]["count"] + parsed_sets["unchanged"]["count"]
        == parsed_sets["allAuditedPixels"]["count"],
        "partition counts do not sum to all pixels",
    )
    require(
        parsed_sets["improved"]["beforeResidual"]
        + parsed_sets["regressed"]["beforeResidual"]
        + parsed_sets["unchanged"]["beforeResidual"]
        == parsed_sets["allAuditedPixels"]["beforeResidual"],
        "partition before residuals do not sum",
    )
    require(
        parsed_sets["improved"]["afterResidual"]
        + parsed_sets["regressed"]["afterResidual"]
        + parsed_sets["unchanged"]["afterResidual"]
        == parsed_sets["allAuditedPixels"]["afterResidual"],
        "partition after residuals do not sum",
    )

    critical = data.get("criticalFor379Samples")
    require(isinstance(critical, list) and len(critical) == SAMPLE_COUNT, "critical sample count changed")
    coords = []
    current_total = 0
    direct_total = 0
    probe_total = 0
    for index, sample in enumerate(critical, start=1):
        require(sample.get("index") == index, f"critical sample {index} index changed")
        x, y, current_residual, direct_residual = validate_sample(sample, f"critical sample {index}", expect_direct=True)
        coords.append((x, y))
        current_total += current_residual
        direct_total += direct_residual
        probe_total += sample["probeResidual"]
    require(coords == EXPECTED_COORDINATES, "FOR-379 critical coordinates changed")
    require(current_total == CURRENT_SAMPLE_RESIDUAL, "critical current residual changed")
    require(probe_total == PROBE_SAMPLE_RESIDUAL, "critical probe residual changed")
    require(direct_total == DIRECT_SAMPLE_RESIDUAL, "critical direct residual changed")

    worst = data.get("worstRegressedPixels")
    best = data.get("bestImprovedPixels")
    require(isinstance(worst, list) and len(worst) == 12, "worst regressed sample count changed")
    require(isinstance(best, list) and len(best) == EXPECTED_SET_TOTALS["improved"][0], "best improved sample count changed")
    previous_delta: int | None = None
    for index, sample in enumerate(worst, start=1):
        validate_sample(sample, f"worst regressed {index}", expect_direct=False)
        delta = sample["deltaVsCurrent"]
        require(delta > 0, f"worst regressed {index} is not a regression")
        require(previous_delta is None or delta <= previous_delta, "worst regressed samples not sorted")
        previous_delta = delta
    previous_delta = None
    for index, sample in enumerate(best, start=1):
        validate_sample(sample, f"best improved {index}", expect_direct=False)
        delta = sample["deltaVsCurrent"]
        require(delta < 0, f"best improved {index} is not an improvement")
        require(previous_delta is None or delta >= previous_delta, "best improved samples not sorted")
        previous_delta = delta


def validate_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED",
        CLASSIFICATION,
        "24576",
        "3171",
        "8",
        "21397",
        "229213",
        "95.91% -> 87.06%",
        "correctionKept=false",
    ):
        require(needle in text, f"report missing {needle}")


def main() -> None:
    validate_source()
    validate_for380()
    validate_artifact()
    validate_report()
    print(f"{LINEAR_ID} validation passed: {ARTIFACT.relative_to(PROJECT_ROOT)}")


if __name__ == "__main__":
    main()
