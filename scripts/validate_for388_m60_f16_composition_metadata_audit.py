#!/usr/bin/env python3
"""Validate the FOR-388 M60 F16 composition metadata audit evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Callable


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-388"
DECISION = "M60_F16_COMPOSITION_METADATA_AUDIT_RECORDED"
CLASSIFICATION = "usable-correction-candidate"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-metadata-composition-source-couverture-apres-for-387"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-387-montre-que-la-metadata-de-frange-m60-f16-actuelle-garde-encore-25-regressions-1"
)
FOR387_DECISION = "M60_F16_RESIDUAL_FRINGE_DISCRIMINATOR_AUDIT_RECORDED"
FOR387_CLASSIFICATION = "fringe-discriminator-too-broad"
SOURCE_CANDIDATE_ID = "local-window-edge-distance-le-17"
FOR387_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-residual-fringe-discriminator-audit-for387/"
    "m60-f16-residual-fringe-discriminator-audit-for387.json"
)
ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-composition-metadata-audit-for388"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-composition-metadata-audit-for388.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-388-m60-f16-composition-metadata-audit.md"
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

EXPECTED_SCOPE = {
    "inspectedPixels": 33,
    "sourceLocalTruthPixels": 8,
    "regressedTruthPixels": 25,
    "sourceCandidateSelectedBeforeResidual": 768,
    "sourceCandidateSelectedAfterResidual": 2044,
}
EXPECTED_CANDIDATES = {
    "alpha-relation-equals-effective-coverage": (33, 8, 25, 0.2424, 1.0, 768, 2044, "still-too-broad"),
    "high-alpha-source-coverage-relation": (30, 7, 23, 0.2333, 0.875, 710, 1931, "insufficient-metadata"),
    "oriented-coverage-source-side": (17, 8, 9, 0.4706, 1.0, 754, 1143, "still-too-broad"),
    "blue-source-contribution": (19, 8, 11, 0.4211, 1.0, 740, 1232, "still-too-broad"),
    "source-color-and-oriented-coverage-lane": (8, 8, 0, 1.0, 1.0, 734, 669, "usable-correction-candidate"),
}
EXPECTED_BEST = "source-color-and-oriented-coverage-lane"


Pixel = dict[str, Any]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-388 validation failed: {message}")


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


def coverage_orientation(pixel: Pixel) -> str:
    neighborhood = pixel["coverageOrthogonalNeighborhood"]
    solid = {key for key in ("north", "south", "west", "east") if neighborhood[key] == 255}
    zero = {key for key in ("north", "south", "west", "east") if neighborhood[key] == 0}
    partial = {
        key
        for key in ("north", "south", "west", "east")
        if 0 < neighborhood[key] < 255
    }
    if solid == {"north", "west"} and zero == {"south", "east"} and not partial:
        return "north-west-solid"
    if solid == {"south", "east"} and zero == {"north", "west"} and not partial:
        return "south-east-solid"
    if solid == {"south", "west"} and zero == {"north", "east"} and not partial:
        return "south-west-solid"
    if solid == {"north", "east"} and zero == {"south", "west"} and not partial:
        return "north-east-solid"
    if solid == {"west"} and zero == {"north", "south", "east"} and not partial:
        return "west-terminal"
    return "other"


def source_color_family(pixel: Pixel) -> str:
    red, green, blue, _alpha = pixel["transparentSourceRgba"]
    if blue > red + 40 and blue > green + 40:
        return "blue-dominant-source"
    if green > red + 30 and green > blue + 30:
        return "green-dominant-source"
    if red > green + 30 and red > blue + 30:
        return "red-dominant-source"
    return "mixed-source"


def coverage_source_alpha_relation(pixel: Pixel) -> str:
    if pixel["coverageAlphaByte"] == pixel["transparentSourceAlphaByte"]:
        return "source-alpha-equals-effective-coverage"
    return "source-alpha-differs-from-effective-coverage"


def source_contribution_independent_of_transparent_coverage(pixel: Pixel) -> str:
    if pixel["transparentSourceAlphaByte"] <= 0:
        return "no-transparent-source-contribution"
    if pixel["transparentSourceAlphaByte"] == pixel["coverageAlphaByte"]:
        return source_color_family(pixel)
    return "source-color-changed-by-coverage-alpha"


def in_for387_best_candidate(pixel: Pixel) -> bool:
    return (
        pixel["bandEdgeDistance"] <= 17
        and pixel["coverageAlphaByte"] >= 96
        and pixel["transparentSourceAlphaByte"] >= 96
    )


def derive_pixel(pixel: Pixel) -> Pixel:
    return {
        "x": pixel["x"],
        "y": pixel["y"],
        "strokeBand": pixel["strokeBand"],
        "cap": pixel["cap"],
        "join": pixel["join"],
        "bandLocalX": pixel["bandLocalX"],
        "bandEdgeDistance": pixel["bandEdgeDistance"],
        "coverageAlphaByte": pixel["coverageAlphaByte"],
        "transparentSourceAlphaByte": pixel["transparentSourceAlphaByte"],
        "coverageSourceAlphaRelation": coverage_source_alpha_relation(pixel),
        "orientedCoverageSide": coverage_orientation(pixel),
        "transparentSourceRgba": pixel["transparentSourceRgba"],
        "transparentSourceColorFamily": source_color_family(pixel),
        "sourceContributionIndependentOfTransparentCoverage": (
            source_contribution_independent_of_transparent_coverage(pixel)
        ),
        "fringeTopology": pixel["fringeTopology"],
        "sourceCoverageRelation": pixel["sourceCoverageRelation"],
        "currentResidual": pixel["currentResidual"],
        "probeResidual": pixel["probeResidual"],
        "for387EvaluationResult": pixel["for387EvaluationResult"],
    }


def candidate_class(source_recovered: int, regressed_included: int, source_truth: int) -> str:
    if source_recovered == source_truth and regressed_included == 0:
        return "usable-correction-candidate"
    if source_recovered == source_truth:
        return "still-too-broad"
    return "insufficient-metadata"


class Candidate:
    def __init__(
        self,
        candidate_id: str,
        description: str,
        signal_family: str,
        selection_method: str,
        select: Callable[[Pixel], bool],
    ) -> None:
        self.id = candidate_id
        self.description = description
        self.signal_family = signal_family
        self.selection_method = selection_method
        self.select = select


def composition_candidates() -> list[Candidate]:
    return [
        Candidate(
            "alpha-relation-equals-effective-coverage",
            "Tests whether source alpha already differs from the effective coverage alpha.",
            "source-alpha-vs-effective-coverage-alpha",
            "coverageAlphaByte == transparentSourceAlphaByte",
            lambda pixel: pixel["coverageSourceAlphaRelation"]
            == "source-alpha-equals-effective-coverage",
        ),
        Candidate(
            "high-alpha-source-coverage-relation",
            "Keeps only the high alpha lane while preserving equal source/coverage alpha.",
            "source-alpha-vs-effective-coverage-alpha",
            "coverageAlphaByte == transparentSourceAlphaByte && coverageAlphaByte >= 160",
            lambda pixel: pixel["coverageSourceAlphaRelation"]
            == "source-alpha-equals-effective-coverage"
            and pixel["coverageAlphaByte"] >= 160,
        ),
        Candidate(
            "oriented-coverage-source-side",
            "Keeps the oriented coverage sides matching the source-local lane direction.",
            "oriented-fringe-side",
            "orientedCoverageSide in {west-terminal,north-west-solid,north-east-solid}",
            lambda pixel: pixel["orientedCoverageSide"]
            in {"west-terminal", "north-west-solid", "north-east-solid"},
        ),
        Candidate(
            "blue-source-contribution",
            "Keeps pixels whose transparent-source diagnostic carries the blue local source.",
            "source-local-contribution-independent-of-transparent-coverage",
            "transparentSourceColorFamily == blue-dominant-source",
            lambda pixel: pixel["transparentSourceColorFamily"] == "blue-dominant-source",
        ),
        Candidate(
            "source-color-and-oriented-coverage-lane",
            "Combines source-local blue contribution with the matching oriented coverage side.",
            "source-color-plus-oriented-fringe-side",
            (
                "(round-round && blue source && oriented side in {west-terminal,north-west-solid}) "
                "|| (butt-bevel && blue source && oriented side == north-east-solid && coverageAlphaByte >= 160)"
            ),
            lambda pixel: (
                pixel["strokeBand"] == "round-round"
                and pixel["transparentSourceColorFamily"] == "blue-dominant-source"
                and pixel["orientedCoverageSide"] in {"west-terminal", "north-west-solid"}
            )
            or (
                pixel["strokeBand"] == "butt-bevel"
                and pixel["transparentSourceColorFamily"] == "blue-dominant-source"
                and pixel["orientedCoverageSide"] == "north-east-solid"
                and pixel["coverageAlphaByte"] >= 160
            ),
        ),
    ]


def residual_stats(pixels: list[Pixel]) -> dict[str, int]:
    before = sum(pixel["currentResidual"] for pixel in pixels)
    after = sum(pixel["probeResidual"] for pixel in pixels)
    return {
        "count": len(pixels),
        "beforeResidual": before,
        "afterResidual": after,
        "deltaVsCurrent": after - before,
        "gainVsCurrent": before - after,
    }


def candidate_stats(candidate: Candidate, pixels: list[Pixel], source_truth: int) -> dict[str, Any]:
    selected = [pixel for pixel in pixels if candidate.select(pixel)]
    source_recovered = sum(
        1 for pixel in selected if pixel["for387EvaluationResult"] == "source-local-useful"
    )
    regressed_included = sum(
        1 for pixel in selected if pixel["for387EvaluationResult"] == "regressed-if-corrected"
    )
    precision = source_recovered / len(selected) if selected else 0.0
    recall = source_recovered / source_truth if source_truth else 0.0
    return {
        "id": candidate.id,
        "description": candidate.description,
        "signalFamily": candidate.signal_family,
        "selectionMethod": candidate.selection_method,
        "scope": "for387-local-window-edge-distance-le-17-selected-pixels",
        "usesSkiaReferenceForSelection": False,
        "usesProbeOutcomeForSelection": False,
        "usesProbeResidualForSelection": False,
        "usesDeltaVsCurrentForSelection": False,
        "usesCurrentResidualForSelection": False,
        "usesFor379MembershipAsPrimary": False,
        "usesFor382CategoryForSelection": False,
        "usesFor383PredicateAsPrimary": False,
        "usesFor384PredicateAsPrimary": False,
        "usesFor385PredicateAsPrimary": False,
        "usesFor386PredicateAsPrimary": False,
        "usesFor387TruthAsSelection": False,
        "selectedPixels": len(selected),
        "sourceLocalRecovered": source_recovered,
        "sourceLocalTruth": source_truth,
        "regressedPixelsIncluded": regressed_included,
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "candidateClass": candidate_class(source_recovered, regressed_included, source_truth),
        "estimatedResidualIfApplied": residual_stats(selected),
        "selectedPixelCoordinates": [
            {"x": pixel["x"], "y": pixel["y"], "result": pixel["for387EvaluationResult"]}
            for pixel in selected
        ],
    }


def load_source_pixels() -> tuple[dict[str, Any], list[Pixel]]:
    for387 = load_json(FOR387_ARTIFACT)
    require(for387.get("decision") == FOR387_DECISION, "FOR-387 decision changed")
    require(for387.get("classification") == FOR387_CLASSIFICATION, "FOR-387 classification changed")
    best = for387.get("bestFringeDiscriminatorCandidate")
    require(isinstance(best, dict), "FOR-387 best candidate missing")
    require(best.get("id") == SOURCE_CANDIDATE_ID, "FOR-387 source candidate changed")
    require(best.get("selectedPixels") == 33, "FOR-387 selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "FOR-387 recovered count changed")
    require(best.get("regressedPixelsIncluded") == 25, "FOR-387 regressed count changed")

    source_pixels = for387.get("sourceLocalPixelsForEvaluationOnly")
    regressed_pixels = for387.get("regressedPixelsForEvaluationOnly")
    require(isinstance(source_pixels, list), "FOR-387 source pixels missing")
    require(isinstance(regressed_pixels, list), "FOR-387 regressed pixels missing")
    scoped = [
        derive_pixel(pixel)
        for pixel in source_pixels + regressed_pixels
        if in_for387_best_candidate(pixel)
    ]
    scoped.sort(key=lambda pixel: (pixel["y"], pixel["x"]))
    return for387, scoped


def build_audit() -> dict[str, Any]:
    for387, pixels = load_source_pixels()
    source_truth = sum(
        1 for pixel in pixels if pixel["for387EvaluationResult"] == "source-local-useful"
    )
    regressed_truth = sum(
        1 for pixel in pixels if pixel["for387EvaluationResult"] == "regressed-if-corrected"
    )
    candidates = [candidate_stats(candidate, pixels, source_truth) for candidate in composition_candidates()]
    best = sorted(
        candidates,
        key=lambda candidate: (
            -candidate["sourceLocalRecovered"],
            candidate["regressedPixelsIncluded"],
            -candidate["precision"],
            candidate["selectedPixels"],
            candidate["id"],
        ),
    )[0]
    classification = candidate_class(
        best["sourceLocalRecovered"],
        best["regressedPixelsIncluded"],
        source_truth,
    )
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-composition-metadata-audit-for388",
        "sourceSceneId": for387.get("sceneId"),
        "sourceArtifact": rel(FOR387_ARTIFACT),
        "adapter": for387.get("adapter"),
        "producer": "scripts/validate_for388_m60_f16_composition_metadata_audit.py",
        "producerInput": "FOR-387 exported diagnostic metadata; no renderer export extension was required",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor387Decision": FOR387_DECISION,
        "requiredFor387Classification": FOR387_CLASSIFICATION,
        "decision": DECISION,
        "classification": classification,
        "allowedClassifications": [
            "usable-correction-candidate",
            "still-too-broad",
            "insufficient-metadata",
        ],
        "auditDoesNotProduceCorrection": True,
        "auditDoesNotApplyRendererChange": True,
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabled": False,
        "rendererExportExtended": False,
        "sourceScope": {
            "sourceCandidateId": SOURCE_CANDIDATE_ID,
            "scopeDescription": "The 33 pixels selected by FOR-387 local-window-edge-distance-le-17.",
            "inspectedPixels": len(pixels),
            "sourceLocalTruthPixels": source_truth,
            "regressedTruthPixels": regressed_truth,
            "sourceCandidateSelectedBeforeResidual": sum(pixel["currentResidual"] for pixel in pixels),
            "sourceCandidateSelectedAfterResidual": sum(pixel["probeResidual"] for pixel in pixels),
            "usesFor387SelectionAsAuditInput": True,
            "usesSkiaReferenceForScope": False,
            "usesProbeOutcomeForScope": False,
            "usesProbeResidualForScope": False,
            "usesDeltaVsCurrentForScope": False,
            "probeOutcomeUsedOnlyAsEvaluationTruth": True,
        },
        "auditedSignals": {
            "sourceAlphaVsEffectiveCoverageAlpha": True,
            "orientedFringeNeighborhood": True,
            "insideOutsideSideApproximation": True,
            "sourceContributionIndependentOfTransparentCoverage": True,
            "transparentSourceRgbaAvailable": True,
            "coverageOrthogonalNeighborhoodAvailable": True,
            "rendererRuntimePredicateReady": False,
        },
        "inspectedPixels": pixels,
        "compositionMetadataCandidates": candidates,
        "bestCompositionMetadataCandidate": best,
        "classificationReason": classification_reason(classification),
        "nextMove": next_move(classification, best),
        "nonGoalsPreserved": {
            "rendererBehaviorChanged": False,
            "runtimeBehaviorChanged": False,
            "gpuOrWgslChanged": False,
            "geometryProductionChanged": False,
            "coverageProductionChanged": False,
            "fallbackChanged": False,
            "scoreChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "correctionEnabled": False,
        },
        "validationCommands": [
            "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
            (
                "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
                ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
            ),
            "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
            "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
            "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
            "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
            "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
            "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
            "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
            "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
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
            "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
            "rtk git diff --check",
        ],
    }


def classification_reason(classification: str) -> str:
    if classification == "usable-correction-candidate":
        return (
            "A source/coverage composition metadata candidate recovers all 8 useful pixels "
            "inside the FOR-387 scope with 0 included regressions. It is only recorded as "
            "a candidate; no correction is enabled."
        )
    if classification == "still-too-broad":
        return (
            "The best composition metadata signal keeps all useful pixels but still includes "
            "regressions in the FOR-387 scope."
        )
    return "The audited metadata cannot recover all useful pixels in the FOR-387 scope."


def next_move(classification: str, best: dict[str, Any]) -> str:
    if classification == "usable-correction-candidate":
        return (
            "Do not enable the correction in FOR-388. A later ticket may evaluate "
            f"`{best['id']}` behind an explicit opt-in guard with full-scene evidence."
        )
    return "Keep correction disabled and export narrower renderer-owned metadata before another predicate attempt."


def report_table(candidates: list[dict[str, Any]]) -> str:
    rows = [
        "| Candidat | Signal | Selectionnes | Source locale retrouves | Regressions incluses | Precision | Rappel | Residuel estime | Classe |",
        "|---|---|---:|---:|---:|---:|---:|---:|---|",
    ]
    for candidate in candidates:
        residual = candidate["estimatedResidualIfApplied"]
        rows.append(
            "| `{id}` | `{signal}` | {selected} | {source} | {regressed} | {precision:.4f} | "
            "{recall:.4f} | {before} -> {after} | `{klass}` |".format(
                id=candidate["id"],
                signal=candidate["signalFamily"],
                selected=candidate["selectedPixels"],
                source=candidate["sourceLocalRecovered"],
                regressed=candidate["regressedPixelsIncluded"],
                precision=candidate["precision"],
                recall=candidate["recall"],
                before=residual["beforeResidual"],
                after=residual["afterResidual"],
                klass=candidate["candidateClass"],
            )
        )
    return "\n".join(rows)


def write_outputs(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    best = data["bestCompositionMetadataCandidate"]
    best_residual = best["estimatedResidualIfApplied"]
    report = f"""# FOR-388 M60 F16 composition metadata audit

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

Source: `{rel(FOR387_ARTIFACT)}`

FOR-388 reste diagnostic-only. Il ne modifie pas le renderer, n'active aucune
correction, ne change pas le scoring, les seuils, WGSL, les fallbacks, ni la
politique de promotion.

## Resultat court

L'audit inspecte les 33 pixels du candidat FOR-387
`local-window-edge-distance-le-17`. Ce perimetre contient 8 pixels
source-locale utiles et 25 regressions restantes.

Le meilleur signal teste est `{best["id"]}` :

- {best["selectedPixels"]} pixels selectionnes ;
- {best["sourceLocalRecovered"]}/8 pixels source-locale retrouves ;
- {best["regressedPixelsIncluded"]} regression incluse ;
- precision {best["precision"]:.4f} ;
- rappel {best["recall"]:.4f} ;
- residuel estime si applique seulement a ces pixels : {best_residual["beforeResidual"]} -> {best_residual["afterResidual"]}.

Ce resultat classe le signal comme `{data["classification"]}`. Il reste
seulement documente comme candidat ; la correction reste desactivee.

## Signaux audites

- Relation source alpha vs coverage alpha effective : tous les pixels du
  perimetre gardent `source-alpha-equals-effective-coverage`, donc ce signal
  seul reste trop large.
- Orientation locale de frange et cote interieur/exterieur approxime par le
  voisinage orthogonal de coverage : le signal reduit les regressions mais ne
  separe pas seul les pixels utiles.
- Contribution source locale independante de la couverture transparente :
  `transparentSourceRgba` permet de distinguer les contributions bleues et
  vertes, mais la contribution bleue seule inclut encore 11 regressions.
- Combinaison source bleue + orientation de coverage : separe les 8 pixels
  utiles avec 0 regression dans ce perimetre.

## Candidats

{report_table(data["compositionMetadataCandidates"])}

## Gardes

Les candidats n'utilisent pas la reference Skia, le resultat du probe, le
residuel du probe, `deltaVsCurrent`, le residuel courant, les categories
FOR-382, ni les predicates FOR-383 a FOR-386 comme selection primaire. Ces
verites restent uniquement des donnees d'evaluation pour mesurer precision,
rappel et regressions incluses.

## Decision de suite

Ne pas activer la correction dans FOR-388. Le candidat
`source-color-and-oriented-coverage-lane` doit etre traite comme une hypothese
mesuree pour un ticket ulterieur avec garde explicite, preuve full-scene, et
verification de stabilite des fallbacks.
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_source() -> None:
    require(CAPTURE_PRODUCER.is_file(), "capture producer missing")
    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "writeM60F16ResidualFringeDiscriminatorAudit(",
        "m60F16ResidualFringeDiscriminatorAuditJson(",
        '"correctionAppliedByDefault": false',
        '"probeEnabledByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture producer missing {needle}")


def validate_artifact() -> dict[str, Any]:
    data = load_json(ARTIFACT)
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor387Decision") == FOR387_DECISION, "FOR-387 decision requirement changed")
    require(
        data.get("requiredFor387Classification") == FOR387_CLASSIFICATION,
        "FOR-387 classification requirement changed",
    )
    require(data.get("auditDoesNotProduceCorrection") is True, "audit must not produce correction")
    require(data.get("auditDoesNotApplyRendererChange") is True, "audit must not apply renderer change")
    require(data.get("correctionAppliedByDefault") is False, "correction must remain disabled")
    require(data.get("correctionPredicateEnabled") is False, "correction predicate must remain disabled")
    require(data.get("rendererExportExtended") is False, "renderer export extension unexpectedly required")

    source_scope = data.get("sourceScope")
    require(isinstance(source_scope, dict), "source scope missing")
    require(source_scope.get("sourceCandidateId") == SOURCE_CANDIDATE_ID, "source candidate changed")
    for key, expected in EXPECTED_SCOPE.items():
        require(source_scope.get(key) == expected, f"sourceScope.{key} changed")
    require(source_scope.get("usesFor387SelectionAsAuditInput") is True, "FOR-387 input flag missing")
    for key in (
        "usesSkiaReferenceForScope",
        "usesProbeOutcomeForScope",
        "usesProbeResidualForScope",
        "usesDeltaVsCurrentForScope",
    ):
        require(source_scope.get(key) is False, f"{key} must be false")
    require(source_scope.get("probeOutcomeUsedOnlyAsEvaluationTruth") is True, "probe truth guard missing")

    signals = data.get("auditedSignals")
    require(isinstance(signals, dict), "audited signals missing")
    for key in (
        "sourceAlphaVsEffectiveCoverageAlpha",
        "orientedFringeNeighborhood",
        "insideOutsideSideApproximation",
        "sourceContributionIndependentOfTransparentCoverage",
        "transparentSourceRgbaAvailable",
        "coverageOrthogonalNeighborhoodAvailable",
    ):
        require(signals.get(key) is True, f"{key} signal missing")
    require(signals.get("rendererRuntimePredicateReady") is False, "runtime predicate must not be ready")

    pixels = data.get("inspectedPixels")
    require(isinstance(pixels, list), "inspected pixels missing")
    require(len(pixels) == EXPECTED_SCOPE["inspectedPixels"], "inspected pixel count changed")
    require(
        sum(1 for pixel in pixels if pixel.get("for387EvaluationResult") == "source-local-useful") == 8,
        "source local inspected count changed",
    )
    require(
        sum(1 for pixel in pixels if pixel.get("for387EvaluationResult") == "regressed-if-corrected") == 25,
        "regressed inspected count changed",
    )
    require(
        all(pixel.get("coverageSourceAlphaRelation") == "source-alpha-equals-effective-coverage" for pixel in pixels),
        "source/coverage alpha relation changed",
    )

    candidates_raw = data.get("compositionMetadataCandidates")
    require(isinstance(candidates_raw, list), "composition candidates missing")
    candidates = {candidate.get("id"): candidate for candidate in candidates_raw if isinstance(candidate, dict)}
    require(set(candidates) == set(EXPECTED_CANDIDATES), "candidate ids changed")
    for candidate_id, expected in EXPECTED_CANDIDATES.items():
        selected, recovered, regressed, precision, recall, before, after, klass = expected
        candidate = candidates[candidate_id]
        for key in (
            "usesSkiaReferenceForSelection",
            "usesProbeOutcomeForSelection",
            "usesProbeResidualForSelection",
            "usesDeltaVsCurrentForSelection",
            "usesCurrentResidualForSelection",
            "usesFor379MembershipAsPrimary",
            "usesFor382CategoryForSelection",
            "usesFor383PredicateAsPrimary",
            "usesFor384PredicateAsPrimary",
            "usesFor385PredicateAsPrimary",
            "usesFor386PredicateAsPrimary",
            "usesFor387TruthAsSelection",
        ):
            require(candidate.get(key) is False, f"{candidate_id} {key} must be false")
        require(candidate.get("selectedPixels") == selected, f"{candidate_id} selected changed")
        require(candidate.get("sourceLocalRecovered") == recovered, f"{candidate_id} recovered changed")
        require(candidate.get("regressedPixelsIncluded") == regressed, f"{candidate_id} regressed changed")
        require(abs(candidate.get("precision") - precision) < 0.00005, f"{candidate_id} precision changed")
        require(abs(candidate.get("recall") - recall) < 0.00005, f"{candidate_id} recall changed")
        require(candidate.get("candidateClass") == klass, f"{candidate_id} class changed")
        residual = candidate.get("estimatedResidualIfApplied")
        require(isinstance(residual, dict), f"{candidate_id} residual missing")
        require(residual.get("beforeResidual") == before, f"{candidate_id} before residual changed")
        require(residual.get("afterResidual") == after, f"{candidate_id} after residual changed")

    best = data.get("bestCompositionMetadataCandidate")
    require(isinstance(best, dict), "best candidate missing")
    require(best.get("id") == EXPECTED_BEST, "best candidate changed")
    require(best.get("selectedPixels") == 8, "best selected count changed")
    require(best.get("sourceLocalRecovered") == 8, "best recovered count changed")
    require(best.get("regressedPixelsIncluded") == 0, "best regressed count changed")
    require(best.get("candidateClass") == CLASSIFICATION, "best class changed")

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
        SOURCE_CANDIDATE_ID,
        "33 pixels",
        "8 pixels",
        "25 regressions",
        EXPECTED_BEST,
        "0 regression",
        "correction reste desactivee",
    ):
        require(needle in report, f"report missing {needle}")
    best = data["bestCompositionMetadataCandidate"]
    require(str(best["selectedPixels"]) in report, "report missing best selected count")
    require(str(best["regressedPixelsIncluded"]) in report, "report missing best regressed count")


def main() -> None:
    validate_source()
    data = build_audit()
    write_outputs(data)
    validated = validate_artifact()
    validate_report(validated)
    print(
        "FOR-388 validation passed: "
        f"{validated['classification']} / best={validated['bestCompositionMetadataCandidate']['id']}"
    )


if __name__ == "__main__":
    main()
