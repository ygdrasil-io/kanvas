#!/usr/bin/env python3
"""Validate the FOR-384 M60 F16 pre-correction geometry/coverage metadata audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-384"
ARTIFACT = (
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
    "2026-06-05-for-384-m60-f16-pre-correction-geometry-coverage-metadata-audit.md"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

DECISION = "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED"
CLASSIFICATION = "metadata-candidate-defendable-runtime-proof-still-blocked"
FOR383_DECISION = "M60_F16_PRE_PROBE_PREDICATE_AUDIT_RECORDED"
FOR383_CLASSIFICATION = "pre-probe-predicate-too-broad"
FOR382_DECISION = "M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED"
FOR382_CLASSIFICATION = (
    "local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof"
)
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-metadata-geometrie-couverture-pre-correction-apres-for-383"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-383-montre-que-le-meilleur-predicate-pre-probe-m60-f16-reste-trop-large-avec-1-pixel-regresse"
)
EXPECTED_FULL_SCENE = {
    "uncorrectedSimilarity": 95.91,
    "correctedSimilarity": 87.06,
    "uncorrectedMismatchPixels": 1004,
    "correctedMismatchPixels": 3181,
    "uncorrectedGreaterThanEightPixels": 10,
    "correctedGreaterThanEightPixels": 3164,
}
EXPECTED_TRUTH_SETS = {
    "allAuditedPixels": (24576, 2014, 231162, 229148),
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
    "coverage-alpha-at-least-96": (8, 8, 0, 1.0, 1.0, 734, 669, "metadata-candidate-defendable"),
    "coverage-and-source-alpha-at-least-96": (8, 8, 0, 1.0, 1.0, 734, 669, "metadata-candidate-defendable"),
    "round-cap-or-high-coverage": (8, 8, 0, 1.0, 1.0, 734, 669, "metadata-candidate-defendable"),
    "band-local-fringe-window": (8, 8, 0, 1.0, 1.0, 734, 669, "metadata-candidate-defendable"),
    "round-cap-only": (7, 7, 0, 1.0, 0.875, 682, 621, "insufficient"),
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-384 validation failed: {message}")


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
        "writeM60F16PreCorrectionGeometryCoverageMetadataAudit(",
        "m60F16PreCorrectionGeometryCoverageMetadataAuditJson(",
        "preCorrectionGeometryCoverageMetadataCandidates()",
        '"decision": "M60_F16_PRE_CORRECTION_GEOMETRY_COVERAGE_METADATA_AUDIT_RECORDED"',
        SOURCE_MEMORY,
        SOURCE_FINDING,
        '"metadataCandidateSelectionUsesSkiaReference": false',
        '"metadataCandidateSelectionUsesProbeOutcome": false',
        '"metadataCandidateSelectionUsesProbeResidual": false',
        '"metadataCandidateSelectionUsesDeltaVsCurrent": false',
        '"metadataCandidateSelectionUsesFor379MembershipAsPrimary": false',
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_prior_artifacts() -> None:
    for383 = load_json(FOR383_ARTIFACT)
    require(for383.get("decision") == FOR383_DECISION, "FOR-383 decision changed")
    require(for383.get("classification") == FOR383_CLASSIFICATION, "FOR-383 classification changed")
    best = for383.get("bestCandidate")
    require(isinstance(best, dict), "FOR-383 best candidate missing")
    require(best.get("id") == "partial-alpha-current-error-shape", "FOR-383 best candidate changed")
    require(best.get("selectedPixels") == 9, "FOR-383 selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "FOR-383 recovered count changed")
    require(best.get("regressedPixelsIncluded") == 1, "FOR-383 regressed count changed")

    for382 = load_json(FOR382_ARTIFACT)
    require(for382.get("decision") == FOR382_DECISION, "FOR-382 decision changed")
    require(for382.get("classification") == FOR382_CLASSIFICATION, "FOR-382 classification changed")
    require(for382.get("correctionAppliedByDefault") is False, "FOR-382 correction must remain disabled")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor383Decision") == FOR383_DECISION, "FOR-383 decision requirement changed")
    require(data.get("requiredFor383Classification") == FOR383_CLASSIFICATION, "FOR-383 class requirement changed")
    require(data.get("requiredFor382Decision") == FOR382_DECISION, "FOR-382 decision requirement changed")
    require(data.get("requiredFor382Classification") == FOR382_CLASSIFICATION, "FOR-382 class requirement changed")
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionKept") is False, "correction must remain refused")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")

    scope = data.get("inspectionScope")
    require(isinstance(scope, dict), "inspectionScope missing")
    require(scope.get("sourcePredicateFromFor383") == "partial-alpha-current-error-shape", "source predicate changed")
    require(scope.get("inspectedPixels") == 9, "inspected pixel count changed")
    require(scope.get("sourceLocalTruthPixels") == 8, "source-local truth count changed")
    require(scope.get("regressedTruthPixels") == 1, "regressed truth count changed")
    require(scope.get("inspectionScopeInheritsFor383ReferenceResidualShape") is True, "FOR-383 inherited scope must be explicit")
    for key in (
        "metadataCandidateSelectionUsesSkiaReference",
        "metadataCandidateSelectionUsesProbeOutcome",
        "metadataCandidateSelectionUsesProbeResidual",
        "metadataCandidateSelectionUsesDeltaVsCurrent",
        "metadataCandidateSelectionUsesFor379MembershipAsPrimary",
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
    require(signals.get("referenceRequiredForCandidateSelection") is False, "reference must not drive metadata candidate")
    require(signals.get("probeRequiredForCandidateSelection") is False, "probe must not drive metadata candidate")
    require(signals.get("rendererRuntimePredicateReady") is False, "runtime predicate must remain blocked")

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

    inspected = data.get("inspectedPixels")
    require(isinstance(inspected, list), "inspectedPixels must be list")
    require(len(inspected) == 9, "inspected pixel list changed")
    regressed = [pixel for pixel in inspected if isinstance(pixel, dict) and pixel.get("deltaVsCurrent") > 0]
    require(len(regressed) == 1, "regressed inspected pixel count changed")
    require(regressed[0].get("x") == 21 and regressed[0].get("y") == 81, "regressed inspected pixel coordinate changed")
    require(regressed[0].get("coverageAlphaByte") == 64, "regressed coverage alpha changed")
    for pixel in inspected:
        require(isinstance(pixel, dict), "inspected pixel must be object")
        require(isinstance(pixel.get("bandLocalX"), int), "bandLocalX missing")
        require(isinstance(pixel.get("bandEdgeDistance"), int), "bandEdgeDistance missing")
        neighborhood = pixel.get("coverageOrthogonalNeighborhood")
        require(isinstance(neighborhood, dict), "coverage neighborhood missing")
        require(neighborhood.get("center") == pixel.get("coverageAlphaByte"), "coverage center mismatch")

    candidates_raw = data.get("metadataCandidates")
    require(isinstance(candidates_raw, list), "metadataCandidates must be list")
    candidates = {candidate.get("id"): candidate for candidate in candidates_raw if isinstance(candidate, dict)}
    require(set(candidates) == set(EXPECTED_CANDIDATES), "candidate ids changed")
    for candidate_id, expected in EXPECTED_CANDIDATES.items():
        selected, recovered, regressed_count, precision, recall, before, after, candidate_class = expected
        candidate = candidates[candidate_id]
        for key in (
            "usesSkiaReferenceForSelection",
            "usesProbeOutcomeForSelection",
            "usesProbeResidualForSelection",
            "usesDeltaVsCurrentForSelection",
            "usesFor379MembershipAsPrimary",
            "usesCurrentResidualForSelection",
            "usesCurrentChannelErrorShapeForSelection",
        ):
            require(candidate.get(key) is False, f"{candidate_id} {key} must be false")
        require(candidate.get("selectedPixels") == selected, f"{candidate_id} selected count changed")
        require(candidate.get("sourceLocalRecovered") == recovered, f"{candidate_id} recovered count changed")
        require(candidate.get("regressedPixelsIncluded") == regressed_count, f"{candidate_id} regressed count changed")
        require(abs(candidate.get("precision") - precision) < 0.00005, f"{candidate_id} precision changed")
        require(abs(candidate.get("recall") - recall) < 0.00005, f"{candidate_id} recall changed")
        require(candidate.get("candidateClass") == candidate_class, f"{candidate_id} class changed")
        validate_set(f"{candidate_id}.diagnosticSelectionResidual", candidate.get("diagnosticSelectionResidual"), (selected, before, after, after - before))

    best = data.get("bestMetadataCandidate")
    require(isinstance(best, dict), "bestMetadataCandidate missing")
    require(best.get("id") == "coverage-alpha-at-least-96", "best metadata candidate changed")
    require(best.get("selectedPixels") == 8, "best selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "best recovered count changed")
    require(best.get("regressedPixelsIncluded") == 0, "best regressed count changed")
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
        "coverage-alpha-at-least-96",
        "8 | 8 | 0 | 1.0000 | 1.0000 | 734 -> 669",
        "diagnostic-only",
        "aucune correction renderer",
        "activee encore",
    ):
        require(needle in report, f"report missing {needle}")
    require(data["bestMetadataCandidate"]["id"] in report, "report does not mention best candidate")


def main() -> None:
    validate_source()
    validate_prior_artifacts()
    data = validate_artifact()
    validate_report(data)
    print("FOR-384 M60 F16 pre-correction geometry/coverage metadata audit validation passed")


if __name__ == "__main__":
    main()
