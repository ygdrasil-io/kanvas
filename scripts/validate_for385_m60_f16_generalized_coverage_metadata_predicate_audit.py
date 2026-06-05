#!/usr/bin/env python3
"""Validate the FOR-385 M60 F16 generalized coverage metadata predicate audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-385"
ARTIFACT = (
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
    "2026-06-05-for-385-m60-f16-generalized-coverage-metadata-predicate-audit.md"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED"
CLASSIFICATION = "generalized-predicate-too-broad"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-predicate-moteur-couverture-hors-filtre-for-383-apres-for-384"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-384-separe-les-8-pixels-source-locale-m60-f16-par-metadata-couverture-mais-bloque-encore-la-correction-moteur"
)
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
    "scopePixels": 3179,
    "fullScenePixels": 24576,
    "for383PredicatePixelsInsideScopeForComparisonOnly": 9,
    "sourceLocalTruthPixels": 8,
    "regressedTruthPixels": 3171,
}
EXPECTED_TRUTH_SETS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148),
    "generalizedScope": (3179, 2008, 231156, 229148),
    "improved": (8, 734, 669, -65),
    "regressed": (3171, 1274, 230487, 229213),
    "unchanged": (21397, 6, 6, 0),
    "for379Critical": (10, 856, 816, -40),
}
EXPECTED_CATEGORIES = {
    "source-locale-plausible": 8,
    "coverage-composition-plausible": 3024,
    "mixed": 147,
    "insufficient": 21397,
}
EXPECTED_CANDIDATES = {
    "coverage-alpha-at-least-96": (
        3164,
        8,
        3156,
        0.0025,
        1.0,
        1855,
        230735,
        "generalized-predicate-too-broad",
    ),
    "coverage-and-source-alpha-at-least-96": (
        3164,
        8,
        3156,
        0.0025,
        1.0,
        1855,
        230735,
        "generalized-predicate-too-broad",
    ),
    "partial-coverage-alpha-at-least-96": (
        436,
        8,
        428,
        0.0183,
        1.0,
        993,
        21135,
        "generalized-predicate-too-broad",
    ),
    "coverage-alpha-at-least-160": (
        3134,
        7,
        3127,
        0.0022,
        0.875,
        1766,
        229630,
        "generalized-predicate-insufficient",
    ),
    "round-cap-or-high-coverage": (
        3171,
        8,
        3163,
        0.0025,
        1.0,
        1933,
        230931,
        "generalized-predicate-too-broad",
    ),
    "high-coverage-fringe-neighborhood": (
        512,
        0,
        512,
        0.0,
        0.0,
        203,
        37797,
        "proof-missing",
    ),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-385 validation failed: {message}")


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
        "writeM60F16GeneralizedCoverageMetadataPredicateAudit(",
        "m60F16GeneralizedCoverageMetadataPredicateAuditJson(",
        "generalizedCoverageMetadataCandidates()",
        '"decision": "M60_F16_GENERALIZED_COVERAGE_METADATA_PREDICATE_AUDIT_RECORDED"',
        SOURCE_MEMORY,
        SOURCE_FINDING,
        '"usesSkiaReferenceForScope": false',
        '"usesProbeOutcomeForScope": false',
        '"usesProbeResidualForScope": false',
        '"usesDeltaVsCurrentForScope": false',
        '"usesFor383PredicateAsPrimaryScope": false',
        '"usesFor383PredicateAsPrimary": false',
        '"usesFor384PredicateAsPrimary": false',
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_prior_artifacts() -> None:
    for384 = load_json(FOR384_ARTIFACT)
    require(for384.get("decision") == FOR384_DECISION, "FOR-384 decision changed")
    require(for384.get("classification") == FOR384_CLASSIFICATION, "FOR-384 classification changed")
    best384 = for384.get("bestMetadataCandidate")
    require(isinstance(best384, dict), "FOR-384 best candidate missing")
    require(best384.get("id") == "coverage-alpha-at-least-96", "FOR-384 best candidate changed")
    require(best384.get("selectedPixels") == 8, "FOR-384 selected count changed")
    require(best384.get("sourceLocalRecovered") == 8, "FOR-384 recovered count changed")
    require(best384.get("regressedPixelsIncluded") == 0, "FOR-384 regressed count changed")

    for383 = load_json(FOR383_ARTIFACT)
    require(for383.get("decision") == FOR383_DECISION, "FOR-383 decision changed")
    require(for383.get("classification") == FOR383_CLASSIFICATION, "FOR-383 classification changed")
    best383 = for383.get("bestCandidate")
    require(isinstance(best383, dict), "FOR-383 best candidate missing")
    require(best383.get("id") == "partial-alpha-current-error-shape", "FOR-383 best candidate changed")
    require(best383.get("selectedPixels") == 9, "FOR-383 selected count changed")
    require(best383.get("sourceLocalRecovered") == 8, "FOR-383 recovered count changed")
    require(best383.get("regressedPixelsIncluded") == 1, "FOR-383 regressed count changed")

    for382 = load_json(FOR382_ARTIFACT)
    require(for382.get("decision") == FOR382_DECISION, "FOR-382 decision changed")
    require(for382.get("classification") == FOR382_CLASSIFICATION, "FOR-382 class changed")
    require(for382.get("correctionAppliedByDefault") is False, "FOR-382 correction must remain disabled")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
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

    scope = data.get("generalizedScope")
    require(isinstance(scope, dict), "generalizedScope missing")
    for key, expected in EXPECTED_SCOPE.items():
        require(scope.get(key) == expected, f"generalizedScope.{key} changed")
    for key in (
        "sourceScopeFromFor383",
        "sourceScopeFromFor384",
        "usesSkiaReferenceForScope",
        "usesProbeOutcomeForScope",
        "usesProbeResidualForScope",
        "usesDeltaVsCurrentForScope",
        "usesFor379MembershipAsPrimaryScope",
        "usesFor383PredicateAsPrimaryScope",
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
        "for383RequiredForCandidateSelection",
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

    candidates_raw = data.get("metadataCandidates")
    require(isinstance(candidates_raw, list), "metadataCandidates must be list")
    candidates = {candidate.get("id"): candidate for candidate in candidates_raw if isinstance(candidate, dict)}
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
            "usesCurrentResidualForSelection",
            "usesCurrentChannelErrorShapeForSelection",
        ):
            require(candidate.get(key) is False, f"{candidate_id} {key} must be false")
        require(candidate.get("scope") == "full-scene-nonzero-coverage-source-alpha", f"{candidate_id} scope changed")
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

    best = data.get("bestMetadataCandidate")
    require(isinstance(best, dict), "bestMetadataCandidate missing")
    require(best.get("id") == "partial-coverage-alpha-at-least-96", "best metadata candidate changed")
    require(best.get("selectedPixels") == 436, "best selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "best recovered count changed")
    require(best.get("regressedPixelsIncluded") == 428, "best regressed count changed")
    require(best.get("candidateClass") == CLASSIFICATION, "best class changed")
    require(best.get("runtimePredicateReady") is False, "best candidate must not be runtime-ready")

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
        "436 | 8 | 428 | 0.0183 | 1.0000 | 993 -> 21135",
        "coverage-alpha-at-least-96",
        "3164 | 8 | 3156 | 0.0025 | 1.0000 | 1855 -> 230735",
        "diagnostic-only",
        "aucune correction renderer",
        "reste bloquee",
    ):
        require(needle in report, f"report missing {needle}")
    require(data["bestMetadataCandidate"]["id"] in report, "report does not mention best candidate")


def main() -> None:
    validate_source()
    validate_prior_artifacts()
    data = validate_artifact()
    validate_report(data)
    print("FOR-385 M60 F16 generalized coverage metadata predicate audit validation passed")


if __name__ == "__main__":
    main()
