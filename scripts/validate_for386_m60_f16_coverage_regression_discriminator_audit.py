#!/usr/bin/env python3
"""Validate the FOR-386 M60 F16 coverage regression discriminator audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-386"
ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-coverage-regression-discriminator-audit-for386/"
    "m60-f16-coverage-regression-discriminator-audit-for386.json"
)
FOR385_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-generalized-coverage-metadata-predicate-audit-for385/"
    "m60-f16-generalized-coverage-metadata-predicate-audit-for385.json"
)
FOR384_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-pre-correction-geometry-coverage-metadata-audit-for384/"
    "m60-f16-pre-correction-geometry-coverage-metadata-audit-for384.json"
)
FOR383_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-pre-probe-predicate-audit-for383/"
    "m60-f16-pre-probe-predicate-audit-for383.json"
)
FOR382_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-composition-membership-audit-for382/"
    "m60-f16-coverage-composition-membership-audit-for382.json"
)
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-386-m60-f16-coverage-regression-discriminator-audit.md"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_COVERAGE_REGRESSION_DISCRIMINATOR_AUDIT_RECORDED"
CLASSIFICATION = "discriminator-candidate-too-broad"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-discriminer-les-428-regressions-du-predicate-couverture-apres-for-385"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-385-montre-que-le-predicate-couverture-m60-f16-generalise-reste-trop-large-hors-filtre-for-383"
)
FOR385_DECISION = "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED"
FOR385_CLASSIFICATION = "generalized-predicate-too-broad"
FOR384_DECISION = "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED"
FOR384_CLASSIFICATION = "metadata-candidate-defendable-runtime-proof-still-blocked"
FOR383_DECISION = "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED"
FOR383_CLASSIFICATION = "pre-probe-predicate-too-broad"
FOR382_DECISION = "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED"
FOR382_CLASSIFICATION = (
    "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof"
)
EXPECTED_FULL_SCENE = {
    "uncorrectedSimilarity": 95.91,
    "correctedSimilarity": 87.06,
    "uncorrectedMismatchPixels": 1004,
    "correctedMismatchPixels": 3181,
    "uncorrectedGreaterThanEightPixels": 10,
    "correctedGreaterThanEightPixels": 3164,
}
EXPECTED_SCOPE = {
    "generalizedScopePixels": 3179,
    "inspectedPixels": 436,
    "sourceLocalTruthPixels": 8,
    "regressedTruthPixels": 428,
    "nonRegressedTruthPixels": 8,
}
EXPECTED_TRUTH_SETS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148),
    "for385Selected": (436, 993, 21135, 20142),
    "selectedSourceLocal": (8, 734, 669, -65),
    "selectedRegressed": (428, 259, 20466, 20207),
    "selectedNonRegressed": (8, 734, 669, -65),
}
EXPECTED_CATEGORIES = {
    "source-locale-plausible": 8,
    "coverage-composition-plausible": 3024,
    "mixed": 147,
    "insufficient": 21397,
}
EXPECTED_CANDIDATES = {
    "source-fringe-band-local-window": (
        35,
        8,
        27,
        0.2286,
        1.0,
        768,
        2154,
        "discriminator-candidate-too-broad",
    ),
    "round-cap-high-alpha-lane": (
        124,
        6,
        118,
        0.0484,
        0.75,
        736,
        7315,
        "discriminator-candidate-insufficient",
    ),
    "butt-or-round-low-edge-distance": (
        246,
        8,
        238,
        0.0325,
        1.0,
        894,
        12579,
        "discriminator-candidate-too-broad",
    ),
    "alpha-160-quiet-neighborhood": (
        360,
        7,
        353,
        0.0194,
        0.875,
        876,
        17321,
        "discriminator-candidate-insufficient",
    ),
    "round-cap-local-fringe-window": (
        27,
        7,
        20,
        0.2593,
        0.875,
        710,
        1763,
        "discriminator-candidate-insufficient",
    ),
    "coverage-neighborhood-sparse-edge": (
        382,
        8,
        374,
        0.0209,
        1.0,
        946,
        18133,
        "discriminator-candidate-too-broad",
    ),
}
EXPECTED_BREAKDOWN = {
    "bandCapJoin": {
        "square-bevel|cap=square|join=bevel": 232,
        "round-round|cap=round|join=round": 132,
        "butt-bevel|cap=butt|join=bevel": 64,
    },
    "coverageAlphaLane": {
        "160-191": 382,
        "128-159": 18,
        "96-127": 11,
        "224-254": 9,
        "192-223": 8,
    },
    "bandEdgeDistanceBucket": {
        "17-32": 141,
        "9-16": 115,
        "33+": 80,
        "5-8": 41,
        "2-4": 31,
        "0-1": 20,
    },
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-386 validation failed: {message}")


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


def validate_set(name: str, value: Any, expected: tuple[int, int, int, int]) -> None:
    require(isinstance(value, dict), f"{name} must be object")
    count, before, after, delta = expected
    require(value.get("count") == count, f"{name} count changed")
    require(value.get("beforeResidual") == before, f"{name} before residual changed")
    require(value.get("afterResidual") == after, f"{name} after residual changed")
    require(value.get("deltaVsCurrent") == delta, f"{name} delta changed")
    require(value.get("gainVsCurrent") == -delta, f"{name} gain changed")
    before_channels = channel_dict(value.get("beforeErrorByChannel"), f"{name}.beforeErrorByChannel")
    after_channels = channel_dict(value.get("afterErrorByChannel"), f"{name}.afterErrorByChannel")
    require(sum(before_channels.values()) == before, f"{name} before channel total mismatch")
    require(sum(after_channels.values()) == after, f"{name} after channel total mismatch")


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16CoverageRegressionDiscriminatorAudit(",
        "m60F16CoverageRegressionDiscriminatorAuditJson(",
        "coverageRegressionDiscriminatorCandidates()",
        '"decision": "M60_F16_COVERAGE_REGRESSION_DISCRIMINATOR_AUDIT_RECORDED"',
        SOURCE_MEMORY,
        SOURCE_FINDING,
        '"usesSkiaReferenceForScope": false',
        '"usesProbeOutcomeForScope": false',
        '"usesProbeResidualForScope": false',
        '"usesDeltaVsCurrentForScope": false',
        '"usesFor383PredicateAsPrimaryScope": false',
        '"usesFor384PredicateAsPrimaryScope": false',
        '"usesFor385PredicateAsPrimary": false',
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_prior_artifacts() -> None:
    for385 = load_json(FOR385_ARTIFACT)
    require(for385.get("decision") == FOR385_DECISION, "FOR-385 decision changed")
    require(for385.get("classification") == FOR385_CLASSIFICATION, "FOR-385 classification changed")
    best385 = for385.get("bestMetadataCandidate")
    require(isinstance(best385, dict), "FOR-385 best candidate missing")
    require(best385.get("id") == "partial-coverage-alpha-at-least-96", "FOR-385 best candidate changed")
    require(best385.get("selectedPixels") == 436, "FOR-385 selected count changed")
    require(best385.get("sourceLocalRecovered") == 8, "FOR-385 recovered count changed")
    require(best385.get("regressedPixelsIncluded") == 428, "FOR-385 regressed count changed")

    for384 = load_json(FOR384_ARTIFACT)
    require(for384.get("decision") == FOR384_DECISION, "FOR-384 decision changed")
    require(for384.get("classification") == FOR384_CLASSIFICATION, "FOR-384 classification changed")
    best384 = for384.get("bestMetadataCandidate")
    require(isinstance(best384, dict), "FOR-384 best candidate missing")
    require(best384.get("id") == "coverage-alpha-at-least-96", "FOR-384 best candidate changed")
    require(best384.get("selectedPixels") == 8, "FOR-384 selected count changed")
    require(best384.get("regressedPixelsIncluded") == 0, "FOR-384 regressed count changed")

    for383 = load_json(FOR383_ARTIFACT)
    require(for383.get("decision") == FOR383_DECISION, "FOR-383 decision changed")
    require(for383.get("classification") == FOR383_CLASSIFICATION, "FOR-383 classification changed")
    best383 = for383.get("bestCandidate")
    require(isinstance(best383, dict), "FOR-383 best candidate missing")
    require(best383.get("id") == "partial-alpha-current-error-shape", "FOR-383 best candidate changed")
    require(best383.get("selectedPixels") == 9, "FOR-383 selected count changed")
    require(best383.get("regressedPixelsIncluded") == 1, "FOR-383 regressed count changed")

    for382 = load_json(FOR382_ARTIFACT)
    require(for382.get("decision") == FOR382_DECISION, "FOR-382 decision changed")
    require(for382.get("classification") == FOR382_CLASSIFICATION, "FOR-382 class changed")
    require(for382.get("correctionAppliedByDefault") is False, "FOR-382 correction must remain disabled")


def validate_breakdown(data: dict[str, Any]) -> None:
    breakdown = data.get("regressedMetadataBreakdown")
    require(isinstance(breakdown, dict), "regressedMetadataBreakdown missing")
    require(breakdown.get("regressedPixels") == 428, "regressed breakdown count changed")
    for group_name, expected_items in EXPECTED_BREAKDOWN.items():
        raw_items = breakdown.get(group_name)
        require(isinstance(raw_items, list), f"{group_name} must be list")
        actual = {item.get("id"): item.get("count") for item in raw_items if isinstance(item, dict)}
        for key, expected_count in expected_items.items():
            require(actual.get(key) == expected_count, f"{group_name}.{key} changed")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor385Decision") == FOR385_DECISION, "FOR-385 decision requirement changed")
    require(data.get("requiredFor385Classification") == FOR385_CLASSIFICATION, "FOR-385 class requirement changed")
    require(data.get("requiredFor384Decision") == FOR384_DECISION, "FOR-384 decision requirement changed")
    require(data.get("requiredFor384Classification") == FOR384_CLASSIFICATION, "FOR-384 class requirement changed")
    require(data.get("requiredFor383Decision") == FOR383_DECISION, "FOR-383 decision requirement changed")
    require(data.get("requiredFor383Classification") == FOR383_CLASSIFICATION, "FOR-383 class requirement changed")
    require(data.get("requiredFor382Decision") == FOR382_DECISION, "FOR-382 decision requirement changed")
    require(data.get("requiredFor382Classification") == FOR382_CLASSIFICATION, "FOR-382 class requirement changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionKept") is False, "correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")

    allowed = data.get("allowedClassifications")
    require(isinstance(allowed, list), "allowed classifications missing")
    require(CLASSIFICATION in allowed, "classification not in allowed set")

    scope = data.get("inspectedFor385Selection")
    require(isinstance(scope, dict), "inspectedFor385Selection missing")
    require(scope.get("sourceCandidateId") == "partial-coverage-alpha-at-least-96", "source candidate changed")
    for key, expected in EXPECTED_SCOPE.items():
        require(scope.get(key) == expected, f"scope.{key} changed")
    require(scope.get("sourceScopeFromFor385") is True, "FOR-385 audit input flag missing")
    require(scope.get("usesFor385SelectionAsAuditInput") is True, "FOR-385 audit input missing")
    for key in (
        "candidateSelectionUsesFor385AsPrimaryPredicate",
        "usesSkiaReferenceForScope",
        "usesProbeOutcomeForScope",
        "usesProbeResidualForScope",
        "usesDeltaVsCurrentForScope",
        "usesFor379MembershipAsPrimaryScope",
        "usesFor383PredicateAsPrimaryScope",
        "usesFor384PredicateAsPrimaryScope",
    ):
        require(scope.get(key) is False, f"{key} must be false")
    require(scope.get("probeOutcomeUsedOnlyAsEvaluationTruth") is True, "probe truth guard missing")
    require(scope.get("for382CategoryUsedOnlyAsEvaluationTruth") is True, "FOR-382 truth guard missing")

    signals = data.get("preCorrectionMetadataSignals")
    require(isinstance(signals, dict), "preCorrectionMetadataSignals missing")
    for key in (
        "strokeBandCapJoinAvailable",
        "bandLocalXAvailable",
        "bandEdgeDistanceAvailable",
        "coverageAlphaByteAvailable",
        "transparentSourceAlphaByteAvailable",
        "coverageOrthogonalNeighborhoodAvailable",
    ):
        require(signals.get(key) is True, f"{key} missing")
    for key in (
        "referenceRequiredForCandidateSelection",
        "probeRequiredForCandidateSelection",
        "currentResidualRequiredForCandidateSelection",
        "deltaVsCurrentRequiredForCandidateSelection",
        "for379RequiredForCandidateSelection",
        "for383RequiredForCandidateSelection",
        "for384RequiredForCandidateSelection",
        "for385RequiredForCandidateSelection",
        "rendererRuntimePredicateReady",
    ):
        require(signals.get(key) is False, f"{key} must be false")

    guard = data.get("fullSceneGuard")
    require(isinstance(guard, dict), "fullSceneGuard missing")
    for key, expected in EXPECTED_FULL_SCENE.items():
        require(guard.get(key) == expected, f"fullSceneGuard.{key} changed")
    require(guard.get("refusalsChanged") is False, "refusal stability changed")

    truth = data.get("truthSetsForEvaluationOnly")
    require(isinstance(truth, dict), "truth sets missing")
    for name, expected in EXPECTED_TRUTH_SETS.items():
        validate_set(name, truth.get(name), expected)

    categories = data.get("for382CategoriesForEvaluationOnly")
    require(isinstance(categories, dict), "FOR-382 categories missing")
    for name, expected_count in EXPECTED_CATEGORIES.items():
        require(categories.get(name, {}).get("count") == expected_count, f"{name} category count changed")

    validate_breakdown(data)

    raw_candidates = data.get("discriminatorCandidates")
    require(isinstance(raw_candidates, list), "discriminatorCandidates must be list")
    candidates = {candidate.get("id"): candidate for candidate in raw_candidates if isinstance(candidate, dict)}
    require(set(candidates) == set(EXPECTED_CANDIDATES), "candidate ids changed")
    for candidate_id, expected in EXPECTED_CANDIDATES.items():
        selected, recovered, regressed, precision, recall, before, after, candidate_class = expected
        candidate = candidates[candidate_id]
        for key in (
            "usesSkiaReferenceForSelection",
            "usesProbeOutcomeForSelection",
            "usesProbeResidualForSelection",
            "usesDeltaVsCurrentForSelection",
            "usesFor379MembershipAsPrimary",
            "usesFor383PredicateAsPrimary",
            "usesFor384PredicateAsPrimary",
            "usesFor385PredicateAsPrimary",
            "usesCurrentResidualForSelection",
            "usesCurrentChannelErrorShapeForSelection",
        ):
            require(candidate.get(key) is False, f"{candidate_id} {key} must be false")
        require(
            candidate.get("scope") == "for385-partial-coverage-alpha-at-least-96-selected-pixels",
            f"{candidate_id} scope changed",
        )
        require(candidate.get("selectedPixels") == selected, f"{candidate_id} selected count changed")
        require(candidate.get("sourceLocalRecovered") == recovered, f"{candidate_id} recovered count changed")
        require(candidate.get("regressedPixelsIncluded") == regressed, f"{candidate_id} regressed count changed")
        require(abs(candidate.get("precision") - precision) < 0.00005, f"{candidate_id} precision changed")
        require(abs(candidate.get("recall") - recall) < 0.00005, f"{candidate_id} recall changed")
        require(candidate.get("candidateClass") == candidate_class, f"{candidate_id} class changed")
        impact = candidate.get("sceneImpactIfApplied")
        require(isinstance(impact, dict), f"{candidate_id} impact missing")
        require(impact.get("selectedBeforeResidual") == before, f"{candidate_id} impact before changed")
        require(impact.get("selectedAfterResidual") == after, f"{candidate_id} impact after changed")
        validate_set(
            f"{candidate_id}.diagnosticSelectionResidual",
            candidate.get("diagnosticSelectionResidual"),
            (selected, before, after, after - before),
        )

    best = data.get("bestDiscriminatorCandidate")
    require(isinstance(best, dict), "bestDiscriminatorCandidate missing")
    require(best.get("id") == "source-fringe-band-local-window", "best discriminator changed")
    require(best.get("selectedPixels") == 35, "best selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "best recovered count changed")
    require(best.get("regressedPixelsIncluded") == 27, "best regressed count changed")
    require(best.get("candidateClass") == CLASSIFICATION, "best class changed")
    require(best.get("runtimePredicateReady") is False, "best candidate must not be runtime-ready")
    require(best.get("correctionAppliedByDefault") is False, "best candidate correction must remain disabled")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must remain false")
    return data


def validate_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        "partial-coverage-alpha-at-least-96",
        "436 pixels",
        "8 pixels source-locale",
        "428 pixels regresses",
        "source-fringe-band-local-window",
        "35 | 8 | 27 | 0.2286 | 1.0000 | 768 -> 2154",
        "square-bevel|cap=square|join=bevel",
        "232",
        "diagnostic-only",
        "aucune correction renderer",
        "reste trop large",
    ):
        require(needle in report, f"report missing {needle}")
    require(data["bestDiscriminatorCandidate"]["id"] in report, "report does not mention best candidate")


def main() -> None:
    validate_source()
    validate_prior_artifacts()
    data = validate_artifact()
    validate_report(data)
    print("FOR-386 M60 F16 coverage regression discriminator audit validation passed")


if __name__ == "__main__":
    main()
