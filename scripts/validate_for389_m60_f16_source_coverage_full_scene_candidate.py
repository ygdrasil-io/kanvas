#!/usr/bin/env python3
"""Validate the FOR-389 M60 F16 full-scene source/coverage candidate audit."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-389"
DECISION = "M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED"
CLASSIFICATION = "full-scene-regresses"
CANDIDATE_ID = "source-color-and-oriented-coverage-lane"
GUARD_PROPERTY = "kanvas.webgpu.m60F16SourceCoverageFullSceneCandidate.enabled"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-evaluer-candidat-source-couverture-full-scene-apres-for-388"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-388-prouve-un-candidat-source-couverture-m60-f16-8-utiles-0-regression-sans-activation"
)
FOR388_DECISION = "M60_F16_COMPOSITION_METADATA_AUDIT_RECORDED"
FOR388_CLASSIFICATION = "usable-correction-candidate"
FOR387_DECISION = "M60_F16_RESIDUAL_FRINGE_DISCRIMINATOR_AUDIT_RECORDED"
FOR387_CLASSIFICATION = "fringe-discriminator-too-broad"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-coverage-full-scene-candidate-for389"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-source-coverage-full-scene-candidate-for389.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-389-m60-f16-source-coverage-full-scene-candidate.md"
FOR388_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-composition-metadata-audit-for388/"
    "m60-f16-composition-metadata-audit-for388.json"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

EXPECTED_CANDIDATE = {
    "selectedPixels": 16,
    "improvedPixels": 8,
    "unchangedPixels": 0,
    "regressedPixels": 8,
    "sourceLocalRecovered": 8,
    "sourceLocalTruth": 8,
    "precision": 0.5,
    "recall": 1.0,
}
EXPECTED_SELECTED_RESIDUAL = {
    "count": 16,
    "beforeResidual": 734,
    "afterResidual": 1109,
    "deltaVsCurrent": 375,
    "gainVsCurrent": -375,
}
EXPECTED_FULL_SCENE_IMPACT = {
    "baseUncorrectedFullSceneResidual": 2014,
    "simulatedAfterResidual": 2389,
    "deltaVsCurrent": 375,
    "gainVsCurrent": -375,
    "baseUncorrectedMismatchPixels": 1004,
    "fullProbeMismatchPixelsNotUsedForSelection": 3181,
    "uncorrectedSimilarity": 95.91,
    "fullProbeSimilarityNotUsedForSelection": 87.06,
}


def fail(message: str) -> None:
    raise SystemExit(f"FOR-389 validation failed: {message}")


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


def require_close(actual: Any, expected: float, label: str) -> None:
    require(isinstance(actual, (int, float)), f"{label} must be numeric")
    require(abs(float(actual) - expected) < 0.00005, f"{label} changed: {actual} != {expected}")


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16SourceCoverageFullSceneCandidate(",
        "m60F16SourceCoverageFullSceneCandidateJson(",
        "sourceCoverageFullSceneCandidateSelect(",
        GUARD_PROPERTY,
        '"runtimeHookInstalled": false',
        '"correctionAppliedByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_for388_source() -> None:
    data = load_json(FOR388_ARTIFACT)
    require(data.get("decision") == FOR388_DECISION, "FOR-388 decision changed")
    require(data.get("classification") == FOR388_CLASSIFICATION, "FOR-388 classification changed")
    best = data.get("bestCompositionMetadataCandidate")
    require(isinstance(best, dict), "FOR-388 best candidate missing")
    require(best.get("id") == CANDIDATE_ID, "FOR-388 candidate id changed")
    require(best.get("selectedPixels") == 8, "FOR-388 selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "FOR-388 recovered count changed")
    require(best.get("regressedPixelsIncluded") == 0, "FOR-388 regression count changed")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor388Decision") == FOR388_DECISION, "required FOR-388 decision changed")
    require(
        data.get("requiredFor388Classification") == FOR388_CLASSIFICATION,
        "required FOR-388 classification changed",
    )
    require(data.get("requiredFor387Decision") == FOR387_DECISION, "required FOR-387 decision changed")
    require(
        data.get("requiredFor387Classification") == FOR387_CLASSIFICATION,
        "required FOR-387 classification changed",
    )
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")
    require(data.get("correctionPredicateEnabled") is False, "predicate must remain disabled")
    require(set(data.get("allowedClassifications", [])) == {
        "full-scene-defendable",
        "full-scene-regresses",
        "runtime-metadata-insufficient",
    }, "allowed classifications changed")

    guard = data.get("explicitOptInGuard")
    require(isinstance(guard, dict), "guard missing")
    require(guard.get("guardId") == GUARD_PROPERTY, "guard id changed")
    require(guard.get("enabledByDefault") is False, "guard must be disabled by default")
    require(guard.get("mode") == "diagnostic-simulation-only", "guard mode changed")
    require(guard.get("runtimeHookInstalled") is False, "runtime hook must not be installed")

    candidate = data.get("candidate")
    require(isinstance(candidate, dict), "candidate missing")
    require(candidate.get("id") == CANDIDATE_ID, "candidate id changed")
    for key in (
        "usesSkiaReferenceForSelection",
        "usesProbeOutcomeForSelection",
        "usesProbeResidualForSelection",
        "usesDeltaVsCurrentForSelection",
        "usesCurrentResidualForSelection",
        "usesFor387TruthAsSelection",
        "usesFor388TruthAsSelection",
        "usesFor379MembershipAsPrimary",
        "usesFor382CategoryForSelection",
    ):
        require(candidate.get(key) is False, f"{key} must be false")
    for key, expected in EXPECTED_CANDIDATE.items():
        if isinstance(expected, float):
            require_close(candidate.get(key), expected, f"candidate.{key}")
        else:
            require(candidate.get(key) == expected, f"candidate.{key} changed")

    residual = candidate.get("selectedResidualIfApplied")
    require(isinstance(residual, dict), "selected residual missing")
    for key, expected in EXPECTED_SELECTED_RESIDUAL.items():
        require(residual.get(key) == expected, f"selected residual {key} changed")

    impact = data.get("fullSceneImpactIfAppliedToSelectedPixelsOnly")
    require(isinstance(impact, dict), "full scene impact missing")
    for key, expected in EXPECTED_FULL_SCENE_IMPACT.items():
        if isinstance(expected, float):
            require_close(impact.get(key), expected, f"impact.{key}")
        else:
            require(impact.get(key) == expected, f"impact.{key} changed")

    truth_sets = data.get("truthSetsForEvaluationOnly")
    require(isinstance(truth_sets, dict), "truth sets missing")
    require(truth_sets["allAuditedPixels"]["count"] == 24576, "full scene pixel count changed")
    require(truth_sets["sourceLocalPixels"]["count"] == 8, "source-local truth count changed")
    require(truth_sets["coverageCompositionPixels"]["count"] == 3024, "coverage-composition count changed")
    require(truth_sets["selectedImprovedPixels"]["count"] == 8, "selected improved count changed")
    require(truth_sets["selectedRegressedPixels"]["count"] == 8, "selected regressed count changed")
    require(truth_sets["selectedUnchangedPixels"]["count"] == 0, "selected unchanged count changed")

    metadata = data.get("metadataAvailability")
    require(isinstance(metadata, dict), "metadata availability missing")
    for key in (
        "transparentSourceRgbaAvailable",
        "coverageOrthogonalNeighborhoodAvailable",
        "strokeBandCapJoinAvailable",
        "coverageAlphaByteAvailable",
    ):
        require(metadata.get(key) is True, f"{key} must be true")
    for key in (
        "selectionNeedsReference",
        "selectionNeedsProbe",
        "selectionNeedsResidual",
        "selectionNeedsDeltaVsCurrent",
        "rendererRuntimePredicateReady",
    ):
        require(metadata.get(key) is False, f"{key} must be false")

    full_scene_guard = data.get("fullSceneGuard")
    require(isinstance(full_scene_guard, dict), "full scene guard missing")
    require(full_scene_guard.get("fallbackReasonStable") == "coverage.stroke-cap-join-visual-parity-below-threshold", "fallback reason changed")
    require(full_scene_guard.get("refusalsChanged") is False, "refusals changed")
    require(full_scene_guard.get("normalRouteRemainsRefused") is True, "normal route must remain refused")
    require(full_scene_guard.get("diagnosticOnly") is True, "audit must stay diagnostic-only")

    samples = data.get("selectedPixelSamples")
    require(isinstance(samples, list), "selected samples missing")
    require(len(samples) == 16, "selected sample count changed")
    require(
        sum(1 for sample in samples if sample.get("for389EvaluationResult") == "source-local-useful") == 8,
        "source-local selected samples changed",
    )
    require(
        sum(1 for sample in samples if sample.get("for389EvaluationResult") == "regressed-if-corrected") == 8,
        "regressed selected samples changed",
    )
    require(
        all(sample.get("transparentSourceColorFamily") == "blue-dominant-source" for sample in samples),
        "selected source color family changed",
    )
    require(
        all(sample.get("orientedCoverageSide") in {"west-terminal", "north-west-solid", "north-east-solid"} for sample in samples),
        "selected oriented coverage side changed",
    )

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must remain false")
    return data


def write_report(data: dict[str, Any]) -> None:
    candidate = data["candidate"]
    residual = candidate["selectedResidualIfApplied"]
    impact = data["fullSceneImpactIfAppliedToSelectedPixelsOnly"]
    report = f"""# FOR-389 M60 F16 source/coverage full-scene candidate

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

Source: `{rel(FOR388_ARTIFACT)}`

FOR-389 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas WGSL, les fallbacks, les seuils, le scoring, ni la
politique de promotion.

## Resultat court

FOR-388 avait prouve `{CANDIDATE_ID}` sur 33 pixels locaux : 8 utiles, 0
regression. L'evaluation full-scene selectionne maintenant {candidate["selectedPixels"]}
pixels :

- {candidate["improvedPixels"]} pixels ameliores ;
- {candidate["unchangedPixels"]} pixels inchanges ;
- {candidate["regressedPixels"]} pixels regresses ;
- {candidate["sourceLocalRecovered"]}/{candidate["sourceLocalTruth"]} pixels source-locale retrouves ;
- precision {candidate["precision"]:.4f} ;
- rappel {candidate["recall"]:.4f}.

Le residuel selectionne passe de {residual["beforeResidual"]} a
{residual["afterResidual"]}. Simule sur la scene complete, appliquer le probe
uniquement aux pixels selectionnes ferait passer le residuel de
{impact["baseUncorrectedFullSceneResidual"]} a {impact["simulatedAfterResidual"]}.

La classification est donc `{data["classification"]}`.

## Garde explicite

Le candidat est documente derriere `{GUARD_PROPERTY}`, desactive par defaut, en
mode `diagnostic-simulation-only`. Aucun hook runtime n'est installe.

## Preuve de selection

La selection utilise uniquement :

- `transparentSourceRgba` pour la famille source bleue ;
- `coverageOrthogonalNeighborhood` pour le cote de coverage oriente ;
- `strokeBand`/cap/join et `coverageAlphaByte`.

Elle n'utilise pas la reference Skia, le resultat du probe, le residuel courant,
le residuel probe, `deltaVsCurrent`, ni les verites FOR-387/FOR-388 comme oracle.
Ces donnees servent seulement a mesurer precision, rappel et regressions.

## Risque observe

Les 8 regressions selectionnees sont des pixels deja reference-equivalents avant
probe, mais la recomposition source-couleur les degrade. Le candidat parfait sur
le perimetre FOR-388 n'est donc pas defendable en full-scene sans metadata plus
etroite.
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_report(data: dict[str, Any]) -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    report = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        CANDIDATE_ID,
        GUARD_PROPERTY,
        "16",
        "8 pixels ameliores",
        "8 pixels regresses",
        "734",
        "1109",
        "2014",
        "2389",
        "diagnostic-only",
    ):
        require(needle in report, f"report missing {needle}")


def main() -> None:
    validate_source()
    validate_for388_source()
    data = validate_artifact()
    write_report(data)
    validate_report(data)
    print(
        "FOR-389 validation passed: "
        f"{data['classification']} / selected={data['candidate']['selectedPixels']} "
        f"regressed={data['candidate']['regressedPixels']}"
    )


if __name__ == "__main__":
    main()
