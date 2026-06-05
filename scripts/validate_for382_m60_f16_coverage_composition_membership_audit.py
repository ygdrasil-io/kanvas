#!/usr/bin/env python3
"""Validate the FOR-382 M60 F16 coverage/composition membership audit evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-382"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-composition-membership-audit-for382/"
    "m60-f16-coverage-composition-membership-audit-for382.json"
)
FOR381_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-subzone-audit-for381/"
    "m60-f16-source-color-subzone-audit-for381.json"
)
FOR380_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-correction-probe-for380/"
    "m60-f16-source-color-correction-probe-for380.json"
)
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-382-m60-f16-coverage-composition-membership-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED"
CLASSIFICATION = "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof"
FOR381_DECISION = "M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED"
FOR381_CLASSIFICATION = "subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition"
FOR380_DECISION = "M60_F16_SOURCE_COLOR_CORRECTION_PROBE_RECORDED"
FOR380_CLASSIFICATION = "regression-detected"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-appartenance-couverture-composition-apres-for-381"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-381-caracterise-les-sous-zones-m60-f16-et-confirme-que-la-correction-source-couleur-doit-separer-couverture-et-composition"
)
EXPECTED_FULL_SCENE = {
    "uncorrectedSimilarity": 95.91,
    "correctedSimilarity": 87.06,
    "uncorrectedMismatchPixels": 1004,
    "correctedMismatchPixels": 3181,
    "uncorrectedGreaterThanEightPixels": 10,
    "correctedGreaterThanEightPixels": 3164,
}
EXPECTED_FOR381_SETS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148, [0, 255], [0, 255], [0, 172]),
    "improved": (8, 734, 669, -65, [96, 160], [96, 160], [0, 0]),
    "regressed": (3171, 1274, 230487, 229213, [16, 255], [16, 255], [0, 103]),
    "unchanged": (21397, 6, 6, 0, [0, 0], [0, 0], [1, 172]),
    "for379Critical": (10, 856, 816, -40, [64, 160], [64, 160], [0, 0]),
}
EXPECTED_CATEGORIES = {
    "source-locale-plausible": (8, 734, 669, -65, [96, 160], [96, 160], [0, 0]),
    "coverage-composition-plausible": (3024, 847, 223081, 222234, [48, 255], [48, 255], [1, 103]),
    "mixed": (147, 427, 7406, 6979, [16, 255], [16, 255], [0, 62]),
    "insufficient": (21397, 6, 6, 0, [0, 0], [0, 0], [1, 172]),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-382 validation failed: {message}")


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


def bounds(value: Any, label: str) -> None:
    require(value is None or isinstance(value, dict), f"{label} must be null or object")
    if value is None:
        return
    for key in ("left", "top", "right", "bottom"):
        require(isinstance(value.get(key), int), f"{label}.{key} must be int")
    require(value["left"] <= value["right"], f"{label} x bounds inverted")
    require(value["top"] <= value["bottom"], f"{label} y bounds inverted")


def rgba(value: Any, label: str) -> list[int]:
    require(isinstance(value, list) and len(value) == 4, f"{label} must be RGBA")
    out = []
    for channel in value:
        require(isinstance(channel, int), f"{label} channel must be int")
        require(0 <= channel <= 255, f"{label} channel out of range")
        out.append(channel)
    return out


def residual(reference: list[int], value: list[int]) -> int:
    return sum(abs(reference[index] - value[index]) for index in range(4))


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16CoverageCompositionMembershipAudit(",
        "m60F16CoverageCompositionMembershipAuditJson(",
        '"decision": "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED"',
        SOURCE_MEMORY,
        SOURCE_FINDING,
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
    require(data.get("correctionKept") is False, "FOR-380 correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "FOR-380 correction must remain disabled")


def validate_set(name: str, value: Any, expected: tuple[Any, ...]) -> dict[str, Any]:
    require(isinstance(value, dict), f"{name} must be object")
    count, before_total, after_total, delta_total, coverage_range, source_range, distance_range = expected
    require(value.get("count") == count, f"{name} count changed")
    require(value.get("beforeResidual") == before_total, f"{name} before residual changed")
    require(value.get("afterResidual") == after_total, f"{name} after residual changed")
    require(value.get("deltaVsCurrent") == delta_total, f"{name} delta changed")
    require(value.get("gainVsCurrent") == -delta_total, f"{name} gain changed")
    require(value.get("coverageMaskAvailablePixels") == count, f"{name} coverage availability changed")
    require(value.get("coverageAlphaByteRange") == coverage_range, f"{name} coverage alpha range changed")
    require(value.get("transparentSourceAlphaByteRange") == source_range, f"{name} source alpha range changed")
    require(value.get("nearestFor379CriticalManhattanDistanceRange") == distance_range, f"{name} distance range changed")

    before = channel_dict(value.get("beforeErrorByChannel"), f"{name}.beforeErrorByChannel")
    after = channel_dict(value.get("afterErrorByChannel"), f"{name}.afterErrorByChannel")
    delta = channel_dict(value.get("afterMinusBeforeErrorByChannel"), f"{name}.afterMinusBeforeErrorByChannel")
    channel_dict(value.get("probeMinusCurrentRgbaTotals"), f"{name}.probeMinusCurrentRgbaTotals")
    require(sum(before.values()) == before_total, f"{name} before channel total mismatch")
    require(sum(after.values()) == after_total, f"{name} after channel total mismatch")
    for channel in ("r", "g", "b", "a"):
        require(after[channel] - before[channel] == delta[channel], f"{name}.{channel} delta mismatch")

    for bucket_name in ("coverageAlphaBuckets", "transparentSourceAlphaBuckets", "currentResidualBuckets"):
        buckets = value.get(bucket_name)
        require(isinstance(buckets, dict), f"{name}.{bucket_name} must be object")
        require(sum(v for v in buckets.values() if isinstance(v, int)) == count, f"{name}.{bucket_name} count mismatch")

    bounds(value.get("bounds"), f"{name}.bounds")
    bands = value.get("bandDistribution")
    require(isinstance(bands, list), f"{name}.bandDistribution must be list")
    require(sum(b.get("count", 0) for b in bands if isinstance(b, dict)) == count, f"{name} band count mismatch")
    require(
        sum(b.get("beforeResidual", 0) for b in bands if isinstance(b, dict)) == before_total,
        f"{name} band before mismatch",
    )
    require(
        sum(b.get("afterResidual", 0) for b in bands if isinstance(b, dict)) == after_total,
        f"{name} band after mismatch",
    )
    return value


def validate_sample(sample: Any, label: str, expected_category: str) -> None:
    require(isinstance(sample, dict), f"{label} must be object")
    reference = rgba(sample.get("referenceRgba"), f"{label}.referenceRgba")
    current = rgba(sample.get("currentRgba"), f"{label}.currentRgba")
    probe = rgba(sample.get("probeRgba"), f"{label}.probeRgba")
    source = rgba(sample.get("transparentSourceRgba"), f"{label}.transparentSourceRgba")
    require(sample.get("currentResidual") == residual(reference, current), f"{label} current residual mismatch")
    require(sample.get("probeResidual") == residual(reference, probe), f"{label} probe residual mismatch")
    require(sample.get("deltaVsCurrent") == sample["probeResidual"] - sample["currentResidual"], f"{label} delta mismatch")
    require(sample.get("transparentSourceAlphaByte") == source[3], f"{label} source alpha mismatch")
    require(sample.get("membershipCategory") == expected_category, f"{label} bad category")
    require(sample.get("coverageMaskAvailable") is True, f"{label} coverage mask missing")
    require(sample.get("correctionAppliedByDefault") is None, f"{label} must not contain renderer flag")


def validate_artifact() -> None:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory reference changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding reference changed")
    require(data.get("requiredFor381Decision") == FOR381_DECISION, "FOR-381 decision requirement changed")
    require(data.get("requiredFor381Classification") == FOR381_CLASSIFICATION, "FOR-381 class requirement changed")
    require(data.get("requiredFor380Decision") == FOR380_DECISION, "FOR-380 decision requirement changed")
    require(data.get("requiredFor380Classification") == FOR380_CLASSIFICATION, "FOR-380 class requirement changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionKept") is False, "correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")

    guard = data.get("fullSceneGuard")
    require(isinstance(guard, dict), "fullSceneGuard missing")
    for key, expected in EXPECTED_FULL_SCENE.items():
        require(guard.get(key) == expected, f"fullSceneGuard.{key} changed")
    require(guard.get("refusalsChanged") is False, "refusal stability changed")

    signals = data.get("membershipSignals")
    require(isinstance(signals, dict), "membershipSignals missing")
    require(signals.get("coverageMaskAvailable") is True, "coverage mask signal missing")
    require(signals.get("transparentSourceAvailable") is True, "transparent source signal missing")
    require("not a renderer predicate" in signals.get("categoryMethod", ""), "category method overclaims predicate")

    for381 = load_json(FOR381_ARTIFACT)
    require(for381.get("decision") == FOR381_DECISION, "FOR-381 decision changed")
    require(for381.get("classification") == FOR381_CLASSIFICATION, "FOR-381 classification changed")

    sets = data.get("for381Sets")
    require(isinstance(sets, dict), "for381Sets missing")
    parsed_sets = {
        name: validate_set(name, sets.get(name), expected)
        for name, expected in EXPECTED_FOR381_SETS.items()
    }
    for old_name, new_name in (
        ("allAuditedPixels", "allAuditedPixels"),
        ("improved", "improved"),
        ("regressed", "regressed"),
        ("unchanged", "unchanged"),
        ("for379Critical", "for379Critical"),
    ):
        old = for381.get("sets", {}).get(old_name)
        new = parsed_sets[new_name]
        require(isinstance(old, dict), f"FOR-381 set {old_name} missing")
        for key in ("count", "beforeResidual", "afterResidual", "deltaVsCurrent"):
            require(old.get(key) == new.get(key), f"FOR-382 did not preserve FOR-381 {old_name}.{key}")

    require(
        parsed_sets["improved"]["count"] + parsed_sets["regressed"]["count"] + parsed_sets["unchanged"]["count"]
        == parsed_sets["allAuditedPixels"]["count"],
        "FOR-381 partition counts do not sum",
    )

    categories = data.get("categories")
    require(isinstance(categories, dict), "categories missing")
    parsed_categories = {
        name: validate_set(name, categories.get(name), expected)
        for name, expected in EXPECTED_CATEGORIES.items()
    }
    require(
        sum(parsed_categories[name]["count"] for name in EXPECTED_CATEGORIES)
        == parsed_sets["allAuditedPixels"]["count"],
        "category counts do not sum",
    )
    require(
        sum(parsed_categories[name]["beforeResidual"] for name in EXPECTED_CATEGORIES)
        == parsed_sets["allAuditedPixels"]["beforeResidual"],
        "category before residuals do not sum",
    )
    require(
        sum(parsed_categories[name]["afterResidual"] for name in EXPECTED_CATEGORIES)
        == parsed_sets["allAuditedPixels"]["afterResidual"],
        "category after residuals do not sum",
    )

    separation = data.get("separationAnalysis")
    require(isinstance(separation, dict), "separationAnalysis missing")
    require(separation.get("improvedPixels") == 8, "improved separation count changed")
    require(separation.get("regressedPixels") == 3171, "regressed separation count changed")
    require(separation.get("sourceLocalPlausiblePixels") == 8, "source local count changed")
    require(separation.get("coverageCompositionPlausiblePixels") == 3024, "coverage/composition count changed")
    require(separation.get("mixedPixels") == 147, "mixed count changed")
    require(separation.get("insufficientPixels") == 21397, "insufficient count changed")
    require(separation.get("allImprovedPixelsInSourceLocalCategory") is True, "improved pixels no longer isolated")
    require(separation.get("regressedPixelsInSourceLocalCategory") == 0, "regressed pixels entered source-local category")
    require(separation.get("rendererPredicateReady") is False, "renderer predicate must remain blocked")

    samples = data.get("categorySamples")
    require(isinstance(samples, dict), "categorySamples missing")
    for category in EXPECTED_CATEGORIES:
        sample_list = samples.get(category)
        require(isinstance(sample_list, list), f"{category} samples missing")
        if category != "insufficient":
            require(sample_list, f"{category} sample list should not be empty")
        for index, sample in enumerate(sample_list, start=1):
            validate_sample(sample, f"{category} sample {index}", category)

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "nonGoalsPreserved missing")
    for key in (
        "rendererBehaviorChanged",
        "runtimeBehaviorChanged",
        "gpuOrWgslChanged",
        "coverageProductionChanged",
        "fallbackChanged",
        "scoreIncreased",
        "thresholdChanged",
        "promotionChanged",
        "probeEnabledByDefault",
        "correctionPredicateEnabled",
    ):
        require(non_goals.get(key) is False, f"non-goal {key} changed")


def validate_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        "24576",
        "3171",
        "3024",
        "147",
        "21397",
        "renderer predicate",
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
